package com.dogpound.railmap.factory.machine;

import com.dogpound.railmap.factory.block.BlockCasing;
import com.dogpound.railmap.factory.block.BlockController;
import com.dogpound.railmap.factory.block.BlockPort;
import com.dogpound.railmap.factory.block.TilePort;
import com.dogpound.railmap.factory.ore.ItemOreStage;
import com.dogpound.railmap.factory.ore.OreRegistry;
import com.dogpound.railmap.factory.proxy.CommonProxy;
import com.dogpound.railmap.grid.PanelHost;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.util.*;

/**
 * One plant (any MachineType): its structure, inventories, water, power, the process it runs and its panel state.
 * Power comes in at the Drive Coupling as Forge Energy (an IR Extras Electric Motor normally drives it), so the plant
 * is a real load on the grid: a stalled or starved machine shows it on its panel.
 */
public class TileMachine extends TileEntity implements ITickable, PanelHost, com.dogpound.railmap.wire.IWiredMachine, com.dogpound.railmap.industry.IndustryLink.Endpoint {
    private MachineType type;
    private boolean formed;
    private final List<BlockPos> members = new ArrayList<>();
    private final Set<Long> memberSet = new HashSet<>();

    private final ItemStackHandler input = new ItemStackHandler(27) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return accepts(s); }
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };
    private final ItemStackHandler output = new ItemStackHandler(27) { @Override protected void onContentsChanged(int slot) { markDirty(); } };
    private final FluidTank water = new FluidTank(16000) {
        @Override public boolean canFillFluidType(FluidStack f) { return f != null && f.getFluid() == FluidRegistry.WATER; }
    };
    /** real fluids: the smelter's molten metal, the washer's slurry, the flotation cells' froth */
    private final FluidTank metal = new FluidTank(4000);
    private final FluidTank slurry = new FluidTank(4000);
    /** new plants: fluid feed (refinery feedstock, tank-car supply) and fluid product (creosote, plant oil, diesel, tank-car drain) */
    private final FluidTank fin = new FluidTank(16000), fout = new FluidTank(16000), fout2 = new FluidTank(16000);   // fout2: the Electrolysis Plant's oxygen
    private int cycle, ptimer, dumped;
    private float angle, chute;
    private boolean blocked;
    private String metalOre = "";
    private int castProg;
    private boolean pouring;

    private Buffer energy;

    /** the drive coupling's store: accepts power from outside, the machine draws it down itself */
    public static class Buffer extends EnergyStorage {
        Buffer(int cap) { super(cap, cap, 0); }
        Buffer(int cap, int stored) { super(cap, cap, 0, stored); }
        void use(int fe) { energy = Math.max(0, energy - fe); }
    }

    // process state
    private boolean running, estop, estopFault;
    private String fault = "";
    private int progress, made, madeWindow, jamClear;
    private float temp = 25;          // smelter °C
    private long madeAt;              // tick of the last per-minute roll-over
    private int rate;                 // items per minute (last minute)
    private float load;               // fraction of rated power drawn in the last tick
    private boolean carHere, chuteOpen, carFull;
    private String carName = "";
    private int carPct;

    // assembly line: one work piece per station (recipe index, -1 = empty) and its progress in ticks
    private final int[] stRecipe = {-1, -1, -1, -1, -1};
    private final int[] stProg = new int[5];

    // panel (synced)
    private final Map<String, Integer> ps = new HashMap<>();
    private final Map<String, String> pt = new HashMap<>();
    private boolean panelDirty;
    private String pressId = "";
    private long pressAt = -100;
    private final Map<String, float[]> anim = new HashMap<>();
    /** client: animation phase of the moving parts (flywheel angle, drum, jaw...) */
    public float phase, phaseSpeed;

    public TileMachine() {}
    public TileMachine(MachineType t) { setType(t); }

    private void setType(MachineType t) {
        type = t; energy = new Buffer(t.buffer);
        water.setCapacity(t == MachineType.WATER_TOWER ? 64_000 : t == MachineType.BOREHOLE_PUMP ? 8_000 : 16_000);
    }

    // ---- water works: borehole pump + water tower --------------------------------------------------------------
    private boolean surveyed;
    private double yieldMax, aquifer, aquiferCap, pumpAcc, flowAvg, outAvg, inAvg;
    private int depth;
    private String strata = "";
    private boolean autoOn, inletOpen, outletOpen, overflow, silenced;
    private int overflowT, lampTest, outThisSec, inThisSec;
    public boolean waterWorks() { return type == MachineType.BOREHOLE_PUMP || type == MachineType.WATER_TOWER; }

    /** what the Water Inlet takes: the tower only through its open inlet valve (and it notices when it overflows) */
    public net.minecraftforge.fluids.capability.IFluidHandler fluidIn() {
        if (type() == MachineType.REFINERY || type() == MachineType.TANK_LOADER || type() == MachineType.ARC_FURNACE || type() == MachineType.ELECTROLYSER || type() == MachineType.TIE_PLANT) return new net.minecraftforge.fluids.capability.IFluidHandler() {
            @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties() { return fin.getTankProperties(); }
            @Override public int fill(FluidStack r, boolean doFill) {
                if (r == null || type == MachineType.REFINERY && !feedstock(r)) return 0;
                if (type == MachineType.ARC_FURNACE && !isOxygen(r)) return 0;              // the oxygen lance
                if (type == MachineType.ELECTROLYSER && r.getFluid() != FluidRegistry.WATER) return 0;
                if (type == MachineType.TIE_PLANT && !r.getFluid().getName().toLowerCase(java.util.Locale.ROOT).contains("creosote")) return 0;   // the working tank takes creosote only
                return fin.fill(r, doFill);
            }
            @Override public FluidStack drain(FluidStack r, boolean doDrain) { return null; }
            @Override public FluidStack drain(int max, boolean doDrain) { return null; }
        };
        if (type() != MachineType.WATER_TOWER) return water;
        return new net.minecraftforge.fluids.capability.IFluidHandler() {
            @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties() { return water.getTankProperties(); }
            @Override public int fill(FluidStack r, boolean doFill) {
                if (!inletOpen || r == null || r.getFluid() != FluidRegistry.WATER) return 0;
                int n = water.fill(r, doFill);
                if (n < r.amount && water.getFluidAmount() >= water.getCapacity()) { overflow = true; overflowT = 40; }
                if (doFill) inThisSec += n;
                return n;
            }
            @Override public FluidStack drain(FluidStack r, boolean doDrain) { return null; }
            @Override public FluidStack drain(int max, boolean doDrain) { return null; }
        };
    }

    /** water a pump / tower pushes out of its Fluid Output Hatch this go (every 5 ticks) */
    public boolean makesFluid() { return type == MachineType.COKE_OVEN || type == MachineType.OIL_PRESS || type == MachineType.REFINERY || type == MachineType.TANK_UNLOADER || type == MachineType.ELECTROLYSER; }
    private FluidTank offered;   // which gas the hatch is pushing this go (the electrolyser has two)
    private FluidTank outTank() { return type == MachineType.ELECTROLYSER && fout2.getFluidAmount() > fout.getFluidAmount() ? fout2 : fout; }
    /** what the fluid hatch pushes out this go */
    public FluidStack offer() {
        if (makesFluid()) { offered = outTank(); return offered.getFluid() == null ? null : new FluidStack(offered.getFluid(), Math.min(1000, offered.getFluidAmount())); }
        int n = outBudget();
        return n <= 0 ? null : new FluidStack(FluidRegistry.WATER, n);
    }
    public void took(int mb) { if (makesFluid()) (offered != null ? offered : fout).drainInternal(mb, true); else tookOut(mb); }

    public int outBudget() {
        if (type == MachineType.BOREHOLE_PUMP) return Math.min(water.getFluidAmount(), 2_000);
        if (type == MachineType.WATER_TOWER) {
            if (!outletOpen) return 0;
            double lvl = water.getFluidAmount() / (double) water.getCapacity();
            return Math.min(water.getFluidAmount(), (int) ((20 + 180 * lvl) * 5));   // gravity: the head pushes it
        }
        return 0;
    }
    public void tookOut(int mb) { if (mb > 0) { water.drainInternal(mb, true); outThisSec += mb; } }

    /** comparator signal from the controller */
    public int comparator() {
        if (!formed || !waterWorks()) return 0;
        int a = water.getFluidAmount();
        return a <= 0 ? 0 : 1 + a * 14 / water.getCapacity();
    }

    private static double permeability(IBlockState s) {
        net.minecraft.block.material.Material m = s.getMaterial();
        net.minecraft.block.Block b = s.getBlock();
        if (m == net.minecraft.block.material.Material.WATER)
            return s.getPropertyKeys().contains(net.minecraft.block.BlockLiquid.LEVEL) && s.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0 ? 3.0 : 1.5;
        if (m == net.minecraft.block.material.Material.AIR || m == net.minecraft.block.material.Material.LAVA) return 0;
        if (m == net.minecraft.block.material.Material.SAND) return b == net.minecraft.init.Blocks.GRAVEL ? 0.8 : 0.7;
        if (m == net.minecraft.block.material.Material.CLAY) return 0.05;
        if (m == net.minecraft.block.material.Material.GROUND || m == net.minecraft.block.material.Material.GRASS) return 0.3;
        if (m == net.minecraft.block.material.Material.ROCK) return b == net.minecraft.init.Blocks.SANDSTONE ? 0.5 : 0.25;
        return 0.15;
    }
    private static String stratum(IBlockState s) {
        net.minecraft.block.material.Material m = s.getMaterial();
        if (m == net.minecraft.block.material.Material.WATER) return "§bWATER-BEARING";
        if (m == net.minecraft.block.material.Material.AIR) return "§8open void / cave";
        if (m == net.minecraft.block.material.Material.LAVA) return "§cmagma (cased off)";
        if (m == net.minecraft.block.material.Material.SAND) return "§egravel & sand aquifer";
        if (m == net.minecraft.block.material.Material.CLAY) return "§7clay (tight)";
        if (m == net.minecraft.block.material.Material.GROUND || m == net.minecraft.block.material.Material.GRASS) return "§6soil";
        if (m == net.minecraft.block.material.Material.ROCK) return "§7rock";
        return "§7mixed fill";
    }

    /** log the borehole: every layer from the wellhead down to bedrock, and what the well can give */
    private void survey() {
        BlockPos head = Structure.at(type, pos, facing(), 1, 0, 1);
        double sum = 0; depth = 0;
        StringBuilder log = new StringBuilder();
        String seg = null; int segTop = 0;
        int sea = world.getSeaLevel();
        for (int y = head.getY() - 1; y > 0; y--) {
            IBlockState s = world.getBlockState(new BlockPos(head.getX(), y, head.getZ()));
            if (s.getBlock() == net.minecraft.init.Blocks.BEDROCK) break;
            depth++;
            sum += permeability(s) * (y < sea ? 1.0 : 0.35);
            String st = stratum(s);
            if (!st.equals(seg)) {
                if (seg != null) log.append(String.format("§7%d-%d m: %s\n", segTop, depth - 1, seg));
                seg = st; segTop = depth - 1;
            }
        }
        if (seg != null) log.append(String.format("§7%d-%d m: %s\n", segTop, depth, seg));
        yieldMax = Math.min(500, 2 + sum);
        aquiferCap = yieldMax * 6000;
        if (!surveyed && !restoreAquifer) aquifer = aquiferCap;
        restoreAquifer = false;
        aquifer = Math.min(aquifer, aquiferCap);
        strata = log.toString();
        surveyed = true;
    }

    private double recharge() {
        float rain = world.getBiome(pos).getRainfall();
        return yieldMax * 0.5 * (0.5 + rain) * (world.isRainingAt(pos.up(8)) || world.isRaining() ? 1.6 : 1.0);
    }

    private void pump() {
        if (!surveyed) survey();
        int hoa = pv("hoa");
        double fill = water.getFluidAmount() / (double) water.getCapacity();
        boolean tripped = !fault.isEmpty() && !fault.startsWith("no power");
        if (hoa == 1 || estop || estopFault || tripped) running = false;
        else if (hoa == 2) {
            if (fill < 0.3) autoOn = true; else if (fill > 0.9) autoOn = false;
            running = autoOn;
        }
        double drawn = 0;
        if (running) {
            double speed = new double[]{0.5, 0.75, 1.0}[pv("speed")];
            double depthFactor = Math.max(0.3, Math.min(2.0, depth / 48.0));
            if (draw(Math.pow(speed, 3) * depthFactor)) {
                if (aquifer <= 0) {
                    running = false; autoOn = false;
                    fault = "DRY RUN - the well is drawn down: let it recover, then RESET";
                    clunk(0.5f);
                } else {
                    double q = yieldMax * speed * (0.35 + 0.65 * aquifer / Math.max(1, aquiferCap));
                    double space = water.getCapacity() - water.getFluidAmount();
                    drawn = Math.min(q, Math.min(space, aquifer));
                    put("lhigh", q > space + 0.01);                                    // deadheaded: the relief valve lifts
                    aquifer -= drawn;
                    pumpAcc += drawn;
                    int whole = (int) pumpAcc;
                    if (whole > 0) { water.fillInternal(new FluidStack(FluidRegistry.WATER, whole), true); pumpAcc -= whole; made += whole; }
                }
            }
        } else put("lhigh", false);
        aquifer = Math.min(aquiferCap, aquifer + recharge());
        flowAvg = flowAvg * 0.95 + drawn * 0.05;
    }

    private void tower() {
        boolean powered = draw(1.0);
        double lvl = water.getFluidAmount() / (double) water.getCapacity();
        if (estop || estopFault) { inletOpen = false; outletOpen = false; }               // fail-safe: both valves shut
        else if (powered && pv("mode") == 1) {                                             // AUTO fill control
            if (lvl >= 0.95 && inletOpen) { inletOpen = false; clunk(1.3f); }
            else if (lvl <= 0.80 && !inletOpen) { inletOpen = true; clunk(1.0f); }
        }
        if (overflowT > 0 && --overflowT == 0) overflow = false;
        boolean alarm = (lvl < 0.15 || overflow) && !(estop || estopFault);
        if (!alarm) silenced = false;
        if (alarm && !silenced && world.getTotalWorldTime() % 40 == 0)
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 1.0f, 1.0f);
        put("lalarm", alarm && !silenced);
        if (lampTest > 0) lampTest--;
        running = inletOpen || outletOpen;
    }

    @Override public void onLoad() { if (world != null && !world.isRemote && formed && production()) com.dogpound.railmap.industry.IndustryLink.register(this); }
    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) { com.dogpound.railmap.industry.IndustryLink.forget(this); com.dogpound.railmap.alarm.AlarmManager.get(world).clear("pq:" + pos.toLong()); } }

    public MachineType type() {
        if (type == null && world != null && world.getBlockState(pos).getBlock() instanceof BlockController b) setType(b.type);
        return type;
    }
    public boolean formed() { return formed; }
    public boolean isMember(BlockPos p) { return memberSet.contains(p.toLong()); }
    public IItemHandler inputView() { return input; }
    public IItemHandler outputView() { return output; }
    public FluidTank water() { return water; }
    /** what pipes may pull at a Fluid Output Hatch */
    public net.minecraftforge.fluids.capability.IFluidHandler fluidOut() {
        if (type() == MachineType.ELECTROLYSER) return new net.minecraftforge.fluids.capability.IFluidHandler() {   // hydrogen and oxygen
            @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties() {
                return new net.minecraftforge.fluids.capability.IFluidTankProperties[]{fout.getTankProperties()[0], fout2.getTankProperties()[0]};
            }
            @Override public int fill(FluidStack r, boolean doFill) { return 0; }
            @Override public FluidStack drain(FluidStack r, boolean doDrain) {
                if (r == null) return null;
                FluidStack a = fout.drainInternal(r, doDrain);
                return a != null ? a : fout2.drainInternal(r, doDrain);
            }
            @Override public FluidStack drain(int max, boolean doDrain) { return outTank().drainInternal(max, doDrain); }
        };
        if (makesFluid()) return new net.minecraftforge.fluids.capability.IFluidHandler() {
            @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties() { return fout.getTankProperties(); }
            @Override public int fill(FluidStack r, boolean doFill) { return 0; }
            @Override public FluidStack drain(FluidStack r, boolean doDrain) { return fout.drainInternal(r, doDrain); }
            @Override public FluidStack drain(int max, boolean doDrain) { return fout.drainInternal(max, doDrain); }
        };
        if (waterWorks()) return new net.minecraftforge.fluids.capability.IFluidHandler() {
            @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties() { return water.getTankProperties(); }
            @Override public int fill(FluidStack r, boolean doFill) { return 0; }
            @Override public FluidStack drain(FluidStack r, boolean doDrain) { return r == null ? null : drain(r.amount, doDrain); }
            @Override public FluidStack drain(int max, boolean doDrain) {
                int n = Math.min(max, outBudget());
                if (n <= 0) return null;
                FluidStack d = water.drainInternal(n, doDrain);
                if (doDrain && d != null) outThisSec += d.amount;
                return d;
            }
        };
        return new net.minecraftforge.fluids.capability.IFluidHandler() {
            @Override public net.minecraftforge.fluids.capability.IFluidTankProperties[] getTankProperties() { return metal.getTankProperties(); }
            @Override public int fill(FluidStack r, boolean doFill) { return 0; }
            @Override public FluidStack drain(FluidStack r, boolean doDrain) { FluidStack d = metal.drainInternal(r, doDrain); if (doDrain && metal.getFluidAmount() == 0) metalOre = ""; return d; }
            @Override public FluidStack drain(int max, boolean doDrain) { FluidStack d = metal.drainInternal(max, doDrain); if (doDrain && metal.getFluidAmount() == 0) metalOre = ""; return d; }
        };
    }
    public Buffer energy() { if (energy == null) setType(type()); return energy; }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockController ? s.getValue(BlockController.FACING) : EnumFacing.NORTH;
    }

    // ---- what each plant accepts -------------------------------------------------------------------------------
    @Override
    public boolean accepts(ItemStack s) {
        if (s.isEmpty() || type == null) return false;
        switch (type) {
            case JAW_CRUSHER: return OreRegistry.oreOf(s) != null || isMat(s, com.dogpound.railmap.factory.ore.ItemMaterial.M.SLAG) || isWornSteel(s);
            case WASHING_PLANT: return s.getItem() == CommonProxy.crushed;
            case FLOTATION: return s.getItem() == CommonProxy.washed;
            case SMELTER: return s.getItem() instanceof ItemOreStage;
            case BULK_LOADER: return true;
            case ASSEMBLY_LINE: return true;
            case OVERHEAD_LOADER: case CRATE_PACKER: return true;
            case COKE_OVEN: return s.getItem() == net.minecraft.init.Items.COAL && s.getMetadata() == 0 || hasOre(s, "coal") || isMat(s, com.dogpound.railmap.factory.ore.ItemMaterial.M.SAWDUST);
            case TIE_PLANT: return hasOre(s, "plankWood");
            case BLAST_FURNACE: return hasOre(s, "ingotIron") || hasOre(s, "fuelCoke");
            case ROLLING_MILL: return hasOre(s, "ingotSteel") || !plateFor(s).isEmpty();
            case WIRE_DRAWER: return hasOre(s, "ingotCopper") || hasOre(s, "ingotGold") || hasOre(s, "ingotIron");
            case OIL_PRESS: return isSeed(s);
            case SAWMILL: return hasOre(s, "logWood");
            case WINDMILL: return isGrain(s);
            case ARC_FURNACE: return isIronCharge(s) || hasOre(s, "fuelCoke") || isElectrode(s);
            case WELDING_CELL: return hasOre(s, "ingotSteel") || hasOre(s, "plateSteel");
            default: return false;
        }
    }

    // ---- commissioning -----------------------------------------------------------------------------------------
    public String tryForm() {
        MachineType t = type();
        if (formed) return "§7" + t.label + " is already commissioned (sneak + screwdriver to decommission)";
        String why = Structure.check(world, t, pos, facing());
        if (!why.isEmpty()) return "§c" + t.label + ": " + why;
        members.clear(); memberSet.clear();
        IndustryData d = IndustryData.get(world);
        for (BlockPos p : Structure.members(t, pos, facing())) {
            members.add(p.toImmutable()); memberSet.add(p.toLong());
            d.set(p, pos);
            IBlockState s = world.getBlockState(p);
            world.setBlockState(p, s.withProperty(BlockCasing.FORMED, true), 2);
            if (world.getTileEntity(p) instanceof TilePort tp) tp.setController(pos);
        }
        formed = true;
        world.setBlockState(pos, world.getBlockState(pos).withProperty(BlockCasing.FORMED, true), 3);
        if (production()) com.dogpound.railmap.industry.IndustryLink.register(this);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.construction"), SoundCategory.BLOCKS, 0.8f, 1.0f);
        markDirty(); sync();
        return "§a⚙ " + t.label + " commissioned (" + members.size() + " parts). " + (t == MachineType.WINDMILL ? "Set the sails and press RELEASE - the wind does the rest." : t == MachineType.ARC_FURNACE || t == MachineType.ELECTROLYSER || t == MachineType.WELDING_CELL ? "Connect its Power Inlet to an IR Extras grid at " + rated(t).label + " (not more!) and press START." : "Couple an Electric Motor to the Drive Coupling and press START on its panel.");
    }

    public String unform(String why) {
        if (!formed) return "§7Not commissioned";
        formed = false; running = false;
        com.dogpound.railmap.industry.IndustryLink.forget(this);
        IndustryData d = IndustryData.get(world);
        for (BlockPos p : members) {
            d.clear(p);
            IBlockState s = world.getBlockState(p);
            if (s.getPropertyKeys().contains(BlockCasing.FORMED)) world.setBlockState(p, s.withProperty(BlockCasing.FORMED, false), 2);
            if (world.getTileEntity(p) instanceof TilePort tp) tp.setController(null);
        }
        members.clear(); memberSet.clear();
        IBlockState me = world.getBlockState(pos);
        if (me.getBlock() instanceof BlockController) world.setBlockState(pos, me.withProperty(BlockCasing.FORMED, false), 3);
        markDirty(); sync();
        return "§e" + type().label + " decommissioned: " + why;
    }

    // ---- panel ------------------------------------------------------------------------------------------------
    @Override public int pv(String k) { Integer v = ps.get(k); return v == null ? 0 : v; }
    @Override public String ptext(String k) { String v = pt.get(k); return v == null ? "" : v; }
    @Override public boolean pressedRecently(String id, long now) { return id.equals(pressId) && now - pressAt < 6; }
    @Override public Map<String, float[]> anim() { return anim; }
    private void put(String k, int v) { Integer o = ps.put(k, v); if (o == null || o != v) panelDirty = true; }
    private void put(String k, boolean v) { put(k, v ? 1 : 0); }
    private void text(String k, String v) { if (!v.equals(pt.put(k, v))) panelDirty = true; }
    private void click(float p) { world.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_PUSH, SoundCategory.BLOCKS, 0.4f, p); }
    private void clunk(float p) { world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.8f, 0.85f + 0.15f * p); }

    public String press(String id, EntityPlayer p) {
        if (burnt && !id.equals("door")) return "§4🔥 BURNT OUT (" + burntWhy + ") - fix it with a screwdriver on the controller";
        pressId = id; pressAt = world.getTotalWorldTime(); panelDirty = true;
        String r = operate(id);
        panelTick(); sync();
        return r;
    }

    private String operate(String id) {
        MachineType t = type();
        if (id.equals("door")) {                                                // the cabinet door
            put("door", 1 - pv("door"));
            world.playSound(null, pos, pv("door") == 1 ? com.dogpound.railmap.sound.PackSounds.ev("machine.door_open") : com.dogpound.railmap.sound.PackSounds.ev("machine.door_close"), SoundCategory.BLOCKS, 0.8f, 1.0f);
            return pv("door") == 1 ? "§7Control cabinet open - right-click a blank spot inside to shut it" : "§7Control cabinet shut";
        }
        switch (id) {
            case "start":
                if (estop || estopFault) return "§cE-STOP - twist it out and RESET first";
                if (t == MachineType.BOREHOLE_PUMP && pv("hoa") == 1) return "§7Selector is at OFF - turn it to HAND (or AUTO)";
                if (t == MachineType.BOREHOLE_PUMP && pv("hoa") == 2) return "§7In AUTO the tank level starts and stops the pump";
                if (!fault.isEmpty() && !fault.startsWith("no ")) return "§cFault: " + fault + " - fix it and press RESET";
                if (running) return "§7Already running";
                running = true; clunk(1.1f);
                return "§a▶ " + t.label + " started";
            case "stop":
                if (!running) return "§7Already stopped";
                running = false; click(0.8f);
                return "§c■ " + t.label + " stopped";
            case "reset":
                if (estop) return "§cE-STOP still pushed in";
                if (fault.startsWith("DRY RUN") && aquifer < aquiferCap * 0.2)
                    return String.format("§cThe well has only recovered to %.0f%% - wait for 20%%", 100 * aquifer / Math.max(1, aquiferCap));
                if (fault.startsWith("PQ TRIP") && !pqBreach().isEmpty()) return "§cThe supply is still out of limits (" + pqBreach() + ") - wait for it to settle";
                if (fault.startsWith("PQ TRIP")) com.dogpound.railmap.alarm.AlarmManager.get(world).clear("pq:" + pos.toLong());
                estopFault = false; fault = ""; click(0.9f);
                return "§aReset";
            case "estop":
                estop = !estop;
                if (estop) { estopFault = true; running = false; clunk(0.6f); return "§c§lEMERGENCY STOP"; }
                click(1f); return "§7E-STOP released - press RESET";
            case "clear":
                if (!"JAM".equals(fault)) return "§7No jam to clear";
                jamClear = 60; clunk(0.7f);
                return "§eReversing the jaw to clear the jam...";
            case "speed": put("speed", (pv("speed") + 1) % 3); click(1.2f); return "§7Pump speed " + new int[]{50, 75, 100}[pv("speed")] + "% (power goes with speed cubed)";
            case "hoa": {
                put("hoa", (pv("hoa") + 1) % 3); click(1.0f);
                if (pv("hoa") == 1) running = false;
                return "§7Selector: " + new String[]{"HAND - START/STOP buttons", "OFF", "AUTO - tank level controls the pump"}[pv("hoa")];
            }
            case "survey": {
                survey(); click(1.4f);
                return String.format("§b⛏ Borehole survey: %d m to bedrock§r\n%s§bYield up to %.0f mB/s · aquifer %.0f%% · recharge %.1f mB/s (rainfall %.1f)",
                        depth, strata, yieldMax * 20, 100 * aquifer / Math.max(1, aquiferCap), recharge() * 20, world.getBiome(pos).getRainfall());
            }
            case "inlet": case "outlet": {
                if (estop || estopFault) return "§cE-STOP holds both valves shut - release it and RESET";
                if (energy().getEnergyStored() < 40) return "§cNo power for the valve actuators";
                boolean open;
                if (id.equals("inlet")) { if (pv("mode") == 1) return "§7Inlet is under AUTO fill control - switch FILL to MAN"; open = inletOpen = !inletOpen; }
                else open = outletOpen = !outletOpen;
                clunk(open ? 1.1f : 0.8f);
                return "§b" + (id.equals("inlet") ? "Inlet" : "Outlet") + " valve " + (open ? "OPENING" : "CLOSING");
            }
            case "silence": silenced = true; click(0.7f); return "§7Alarm silenced";
            case "test": lampTest = 40; click(1.5f); return "§7Lamp test";
            case "tap": put("tap", (pv("tap") + 1) % 3); clunk(1.0f); return "§7Furnace transformer tap " + new String[]{"LOW (50%)", "MEDIUM (75%)", "HIGH (100%) - fastest melt, biggest draw"}[pv("tap")];
            case "lance": put("lance", 1 - pv("lance")); click(1.1f); return pv("lance") == 1 ? "§bOxygen lance ON - pipe oxygen into the inlet (from an Electrolysis Plant): melts 35% faster" : "§7Oxygen lance OFF";
            case "current": put("current", (pv("current") + 1) % 3); clunk(1.0f); return "§7Cell current " + new String[]{"LOW", "MEDIUM", "HIGH"}[pv("current")];
            case "program": put("program", (pv("program") + 1) % 3); click(1.2f); return "§7Weld program: " + WELD_NAMES[pv("program")];
            case "sails": put("sails", (pv("sails") + 1) % 3); clunk(0.9f);
                return "§7Sails " + new String[]{"FULL - every cloth spread", "REEFED - half the cloth (safe in strong wind)", "FURLED - bare frames, nothing turns"}[pv("sails")];
            case "feed": put("feed", (pv("feed") + 1) % 3); click(1.2f); return "§7Feed rate " + new String[]{"LOW", "MEDIUM", "HIGH (may jam)"}[pv("feed")];
            case "valve": put("valve", 1 - pv("valve")); click(1.1f); return pv("valve") == 1 ? "§bWater valve OPEN" : "§7Water valve SHUT";
            case "air": put("air", 1 - pv("air")); click(1.1f); return "§7Air blower " + (pv("air") == 1 ? "HIGH" : "LOW");
            case "setpoint": put("setpoint", (pv("setpoint") + 1) % 3); click(1.1f); return "§7Furnace power " + new int[]{50, 75, 100}[pv("setpoint")] + "%";
            case "mode": put("mode", 1 - pv("mode")); click(1.1f); return "§7Mode " + (pv("mode") == 1 ? "AUTO" : "MANUAL");
            case "prev": case "next": {
                int n = AssemblyRecipes.ALL.size();
                put("recipe", ((pv("recipe") + (id.equals("next") ? 1 : -1)) % n + n) % n);
                click(1.3f);
                AssemblyRecipes.Recipe r = AssemblyRecipes.ALL.get(pv("recipe"));
                StringBuilder sb = new StringBuilder("§eProduct: " + r.label + " §7(");
                for (AssemblyRecipes.Part pa : r.parts) sb.append(pa.label()).append(", ");
                return sb.substring(0, sb.length() - 2) + " -> " + r.outCount + "x " + r.output + ")";
            }
            default: {                                                                  // any other selector on the panel just turns
                for (com.dogpound.railmap.grid.Panel.C c : t.panel().cs)
                    if (c.id.equals(id) && c.t == com.dogpound.railmap.grid.Panel.T.SELECTOR && c.positions != null && c.positions.length > 0) {
                        put(id, (pv(id) + 1) % c.positions.length); click(1.15f);
                        return "§7" + c.label + ": " + c.positions[pv(id)];
                    }
                return "";
            }
        }
    }

    public String status() {
        MachineType t = type();
        return "§7" + t.label + " · " + (running ? "§arunning" : "§cstopped") + (t == MachineType.WINDMILL ? String.format(java.util.Locale.ROOT, "§7 · wind %.1f m/s · sails %s", millWind(), new String[]{"FULL", "REEFED", "FURLED"}[pv("sails")]) : electrical() ? "§7 · supply " + (supplyOk ? "§a" : "§c") + (supplyWhat.isEmpty() ? "?" : supplyWhat) + "§7 · store " + energy().getEnergyStored() * 100 / Math.max(1, energy().getMaxEnergyStored()) + "%" + (type == MachineType.ARC_FURNACE ? " · electrode " + electrodeLife + " heats" : "") : "§7 · power " + energy().getEnergyStored() * 100 / Math.max(1, energy().getMaxEnergyStored()) + "% stored")
                + (fault.isEmpty() ? "" : " · §e" + fault) + (type == MachineType.SMELTER ? String.format(" · %.0f °C", temp) : "")
                + (waterWorks() ? String.format(" · water %d L", water.getFluidAmount()) : "")
                + (fin.getFluidAmount() > 0 ? " · in " + fin.getFluidAmount() + " mB " + fin.getFluid().getLocalizedName() : "")
                + (fout.getFluidAmount() > 0 ? " · out " + fout.getFluidAmount() + " mB " + fout.getFluid().getLocalizedName() : "")
                + (type == MachineType.COKE_OVEN || type == MachineType.BLAST_FURNACE || type == MachineType.REFINERY ? String.format(" · %.0f °C", temp) : "")
                + " · made " + made
                + (type == MachineType.BOREHOLE_PUMP ? String.format(" · %.0f mB/s · well %.0f%% · %d m", flowAvg * 20, 100 * aquifer / Math.max(1, aquiferCap), depth) : "");
    }

    // ---- running ----------------------------------------------------------------------------------------------
    @Override
    public void update() {
        if (world == null || type() == null) return;
        if (world.isRemote) { phase += phaseSpeed; return; }
        if (!formed) return;
        if (world.getTotalWorldTime() % 20 == (pos.hashCode() & 15)) overdriveTick();
        if (burnt) { running = false; if (!"BURNT OUT".equals(fault)) fault = "BURNT OUT"; }
        load = 0;
        if (estop || estopFault) running = false;
        if (jamClear > 0 && --jamClear == 0) { fault = ""; clunk(1.2f); }
        if (electrical() && world.getTotalWorldTime() % 5 == 0) powerInlet();
        switch (type) {
            case JAW_CRUSHER: crusher(); break;
            case WASHING_PLANT: washer(); break;
            case FLOTATION: flotation(); break;
            case SMELTER: smelter(); break;
            case ASSEMBLY_LINE: assembly(); break;
            case BULK_LOADER: loader(); break;
            case UNLOADER: unloader(); break;
            case BOREHOLE_PUMP: pump(); break;
            case WATER_TOWER: tower(); break;
            case OVERHEAD_LOADER: overheadLoader(); break;
            case CAR_DUMPER: carDumper(); break;
            case TANK_LOADER: case TANK_UNLOADER: tankStation(); break;
            case COKE_OVEN: cokeOven(); break;
            case BLAST_FURNACE: blastFurnace(); break;
            case ROLLING_MILL: rollingMill(); break;
            case WIRE_DRAWER: wireDrawer(); break;
            case OIL_PRESS: oilPress(); break;
            case REFINERY: refinery(); break;
            case SAWMILL: sawmill(); break;
            case CRATE_PACKER: cratePacker(); break;
            case WINDMILL: windmill(); break;
            case ARC_FURNACE: arcFurnace(); break;
            case ELECTROLYSER: electrolyser(); break;
            case WELDING_CELL: weldingCell(); break;
            case TIE_PLANT: tiePlant(); break;
        }
        if (now0() % 20 == (pos.hashCode() & 15)) gridTick();
        if (waterWorks() && world.getTotalWorldTime() % 20 == 0) {
            outAvg = outThisSec; inAvg = inThisSec; outThisSec = 0; inThisSec = 0;
        }
        long now = world.getTotalWorldTime();
        if (now - madeAt >= 1200) { rate = madeWindow; madeWindow = 0; madeAt = now; }
        if (now % 10 == (pos.hashCode() & 7)) { panelTick(); sync(); }
    }

    /** draw this tick's power; false = starved (the machine waits) */
    private boolean draw(double fraction) {
        if (shed) { fault = "LOAD SHED - the grid dropped industry to keep the trains running"; return false; }
        if (!planHold.isEmpty()) { fault = planHold; return false; }                // next-48 #23: the production planner paused it
        if (fault.startsWith("PLANNER")) fault = "";
        if (fault.startsWith("LOAD SHED")) fault = "";
        if (fault.startsWith("PQ TRIP")) return false;
        int need = (int) Math.ceil(type.power * fraction);
        if (energy().getEnergyStored() < need) { fault = "no power - is the motor running?"; return false; }
        energy.use(need);
        load = (float) fraction;
        if (fault.startsWith("no power")) fault = "";
        return true;
    }

    private boolean emit(ItemStack s) {
        if (!ItemHandlerHelper.insertItemStacked(output, s, true).isEmpty()) { fault = "no room - empty the output hatch"; return false; }
        ItemHandlerHelper.insertItemStacked(output, s, false);
        if (fault.startsWith("no room")) fault = "";
        made += s.getCount(); madeWindow += s.getCount();
        if (com.dogpound.railmap.api.QuestEvents.active())                                  // quest hook: the plant made something
            com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "factory_output", "item", s.getItem().getRegistryName() + "@" + s.getMetadata(), "count", s.getCount(), "machine", String.valueOf(type()));
        return true;
    }

    private int firstInput() {
        for (int i = 0; i < input.getSlots(); i++) if (!input.getStackInSlot(i).isEmpty()) return i;
        return -1;
    }

    private void crusher() {
        if (!running || !fault.isEmpty() && !fault.startsWith("no ")) return;
        int feed = pv("feed");
        int slot = firstInput();
        if (slot < 0) { progress = 0; return; }
        double frac = feed == 0 ? 0.6 : feed == 1 ? 1.0 : 1.4;
        if (!draw(frac)) return;
        int need = feed == 0 ? 80 : feed == 1 ? 60 : 40;
        if (++progress < need) return;
        progress = 0;
        if (feed == 2 && world.rand.nextInt(33) == 0 || feed == 1 && world.rand.nextInt(200) == 0) {
            fault = "JAM"; clunk(0.5f);
            return;
        }
        ItemStack feedIn = input.getStackInSlot(slot);
        if (isMat(feedIn, com.dogpound.railmap.factory.ore.ItemMaterial.M.SLAG)) {         // her list #92: slag crushed into railway ballast
            if (emit(new ItemStack(net.minecraft.init.Blocks.GRAVEL, 2))) input.extractItem(slot, 1, false);
            return;
        }
        if (isWornSteel(feedIn)) {                                                      // worn bogies / wheelsets broken up for the arc furnace
            if (emit(mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.SCRAP, scrapFrom(feedIn)))) input.extractItem(slot, 1, false);
            return;
        }
        String ore = OreRegistry.oreOf(feedIn);
        if (ore == null) { input.extractItem(slot, 1, false); return; }
        if (emit(new ItemStack(CommonProxy.crushed, 2, OreRegistry.meta(ore)))) input.extractItem(slot, 1, false);
    }

    private void washer() {
        if (slurry.getFluidAmount() > 0 && world.getTotalWorldTime() % 4 == 0) slurry.drainInternal(10, true);   // settles out of the sump
        if (!running || estopFault) return;
        int slot = firstInput();
        if (slot < 0) { progress = 0; return; }
        if (pv("valve") == 0) { fault = "no water - open the WATER valve"; return; }
        if (water.getFluidAmount() < 250) { fault = "no water at the inlet"; return; }
        if (fault.startsWith("no water")) fault = "";
        if (!draw(1.0)) return;
        if (++progress < 80) return;
        progress = 0;
        ItemStack in = input.getStackInSlot(slot);
        if (emit(new ItemStack(CommonProxy.washed, 2, in.getMetadata()))) {
            input.extractItem(slot, 1, false); water.drainInternal(250, true);
            String ore = OreRegistry.name(in.getMetadata());
            if (ore != null) { slurry.setFluid(com.dogpound.railmap.factory.fluid.ModFluids.tagged(com.dogpound.railmap.factory.fluid.ModFluids.SLURRY, ore, Math.min(4000, slurry.getFluidAmount() + 250))); metalOre = ore; }
        }
    }

    private void flotation() {
        if (!running || estopFault) return;
        int slot = firstInput();
        if (slot < 0 || input.getStackInSlot(slot).getCount() < 2) { progress = 0; return; }
        if (water.getFluidAmount() < 500) { fault = "no water at the inlet"; return; }
        if (fault.startsWith("no water")) fault = "";
        boolean high = pv("air") == 1;
        if (!draw(high ? 1.0 : 0.6)) return;
        if (++progress < (high ? 100 : 140)) return;
        progress = 0;
        ItemStack in = input.getStackInSlot(slot);
        String fo = OreRegistry.name(in.getMetadata());
        if (fo != null) metalOre = fo;
        if (emit(new ItemStack(CommonProxy.concentrate, 3, in.getMetadata()))) { input.extractItem(slot, 2, false); water.drainInternal(500, true); }
    }

    private void smelter() {
        double sp = new double[]{0.5, 0.75, 1.0}[pv("setpoint")];
        double target = running ? new double[]{1250, 1480, 1560}[pv("setpoint")] : 25;
        if (running && !draw(sp)) target = 25;
        temp += (float) ((target - temp) * (target > temp ? 0.004 * sp * 2 : 0.003));
        boolean molten = temp >= 1450;
        // casting: the melt pours into the ingot moulds (MOULD), 144 mB an ingot; PIPE holds it for the fluid hatch
        pouring = false;
        if (molten && pv("outlet") == 0 && metal.getFluidAmount() >= 144 && !metalOre.isEmpty()) {
            pouring = true;
            if (++castProg >= 30) {
                castProg = 0;
                ItemStack ingot = OreRegistry.ingot(metalOre, 1);
                if (!ingot.isEmpty() && emit(ingot)) { metal.drainInternal(144, true); if (metal.getFluidAmount() == 0) metalOre = ""; }
            }
        }
        if (!running || !molten) { progress = 0; return; }
        int slot = firstInput();
        if (slot < 0) { progress = 0; return; }
        ItemStack in = input.getStackInSlot(slot);
        boolean conc = in.getItem() == CommonProxy.concentrate;
        if (conc && in.getCount() < 3) { progress = 0; return; }
        String ore = OreRegistry.name(in.getMetadata());
        if (ore == null) { input.extractItem(slot, 1, false); return; }
        if (!metalOre.isEmpty() && !metalOre.equals(ore)) { fault = "waiting: the crucible still holds molten " + metalOre; return; }
        int mb = conc ? 576 : 144;                       // 3 concentrate = 4 ingots of metal
        if (metal.getFluidAmount() + mb > metal.getCapacity()) return;
        int need = (int) ((conc ? 100 : 50) * (1 + (1560 - temp) / 110.0));
        if (++progress < need) return;
        progress = 0;
        FluidStack melt = com.dogpound.railmap.factory.fluid.ModFluids.molten(ore, mb);
        if (metal.getFluid() != null && !metal.getFluid().isFluidEqual(melt)) { fault = "the crucible holds a different metal"; return; }
        metal.fillInternal(melt, true);
        metalOre = ore;
        input.extractItem(slot, conc ? 3 : 1, false);
        if (fault.startsWith("waiting") || fault.startsWith("the crucible")) fault = "";
    }

    // ---- assembly line ----------------------------------------------------------------------------------------
    private void assembly() {
        boolean busy = false;
        for (int r : stRecipe) busy |= r >= 0;
        if (!running || estopFault) return;
        if (!draw(busy ? 1.0 : 0.15)) return;
        // the last station finishes first, then every piece moves up one station (a real indexing line)
        for (int i = 4; i >= 0; i--) {
            if (stRecipe[i] < 0) continue;
            AssemblyRecipes.Recipe r = AssemblyRecipes.ALL.get(stRecipe[i]);
            int step = Math.max(10, r.ticks / 5);
            if (stProg[i] < step) { stProg[i]++; continue; }
            if (i == 4) {
                ItemStack out = r.result();
                if (out.isEmpty() || emit(out)) { stRecipe[4] = -1; stProg[4] = 0; }
            } else if (stRecipe[i + 1] < 0) {
                stRecipe[i + 1] = stRecipe[i]; stProg[i + 1] = 0; stRecipe[i] = -1; stProg[i] = 0;
            }
        }
        // load a new piece at the press when a full set of parts is in the bin
        if (stRecipe[0] < 0) {
            int ri = pv("recipe") % AssemblyRecipes.ALL.size();
            if (takeParts(AssemblyRecipes.ALL.get(ri))) { stRecipe[0] = ri; stProg[0] = 0; fault = fault.startsWith("waiting") ? "" : fault; }
        }
    }

    /** take one full set of the recipe's parts from the input bin (all or nothing) */
    private boolean takeParts(AssemblyRecipes.Recipe r) {
        for (AssemblyRecipes.Part pa : r.parts) {
            int have = 0;
            for (int i = 0; i < input.getSlots(); i++) if (pa.matches(input.getStackInSlot(i))) have += input.getStackInSlot(i).getCount();
            if (have < pa.count) return false;
        }
        for (AssemblyRecipes.Part pa : r.parts) {
            int need = pa.count;
            for (int i = 0; i < input.getSlots() && need > 0; i++) if (pa.matches(input.getStackInSlot(i))) need -= input.extractItem(i, need, false).getCount();
        }
        return true;
    }

        // ---- trains ----------------------------------------------------------------------------------------------
    private void loader() {
        Freight.Car car = Freight.find(world, trackBox());
        carHere = car != null; carName = car == null ? "" : car.name; carPct = car == null ? 0 : car.fillPct;
        carFull = car != null && car.fillPct >= 100;
        boolean auto = pv("mode") == 1;
        boolean want = carHere && !carFull && firstInput() >= 0 && (running || auto) && !estopFault;
        chuteOpen = want;
        if (!want) { if (running && carFull && !auto) { running = false; clunk(1.0f); } return; }
        if (!draw(1.0)) { chuteOpen = false; return; }
        if (world.getTotalWorldTime() % 10 != 0) return;
        int moved = bill(true, car, Freight.move(input, car.inv, 64, car));
        made += moved; madeWindow += moved;
        if (moved == 0 && !Freight.refused.isEmpty()) fault = "WRONG CAR: " + Freight.refused;
        else if (fault.startsWith("WRONG CAR")) fault = "";
    }

    private void unloader() {
        Freight.Car car = Freight.find(world, trackBox());
        carHere = car != null; carName = car == null ? "" : car.name; carPct = car == null ? 0 : car.fillPct;
        boolean empty = car == null || Freight.count(car.inv) == 0;
        boolean auto = pv("mode") == 1;
        boolean want = carHere && !empty && (running || auto) && !estopFault;
        chuteOpen = want;
        put("lempty", carHere && empty);
        if (!want) { if (running && carHere && empty && !auto) { running = false; clunk(1.0f); } return; }
        if (!draw(1.0)) { chuteOpen = false; return; }
        if (world.getTotalWorldTime() % 10 != 0) return;
        int moved = bill(false, car, Freight.move(car.inv, output, 64, null));
        made += moved; madeWindow += moved;
    }

    /** where freight cars stand to be served: the track clearance, stretched along the track */
    private AxisAlignedBB trackBox() {
        List<BlockPos> t = Structure.clearance(type, pos, facing());
        if (t.isEmpty()) return new AxisAlignedBB(pos);
        AxisAlignedBB b = new AxisAlignedBB(t.get(0));
        for (BlockPos p : t) b = b.union(new AxisAlignedBB(p));
        EnumFacing along = facing().getOpposite();
        return b.expand(along.getXOffset() * 3, 2, along.getZOffset() * 3).expand(-along.getXOffset() * 3, 0, -along.getZOffset() * 3);
    }

    // ---- panel readings ----------------------------------------------------------------------------------------
    private void panelTick() {
        if (type == null) return;
        put("lrun", running && formed);
        put("lfault", !fault.isEmpty() && !fault.startsWith("no power") || estopFault);
        put("lpower", energy().getEnergyStored() > 0);
        put("load", (int) (load * 100));
        put("rate", rate > 0 ? rate : madeWindow * 1200 / (int) Math.max(1, world.getTotalWorldTime() - madeAt));
        put("estop", estop);
        text("ores", String.valueOf(made));
        put("ljam", "JAM".equals(fault));
        put("water", water.getFluidAmount() * 100 / water.getCapacity());
        put("llow", water.getFluidAmount() < 1000 && running);
        put("froth", running && firstInput() >= 0 ? Math.min(100, 40 + progress / 2) : 0);
        put("temp", Math.round(temp));
        put("tempg", Math.round(temp / 100));
        put("lheat", running && temp < 1450);
        put("lready", temp >= 1450);
        int stored = 0, cap = 0;
        IItemHandler bin = type == MachineType.UNLOADER ? output : input;
        for (int i = 0; i < bin.getSlots(); i++) { stored += bin.getStackInSlot(i).getCount(); cap += 64; }
        put("bin", cap == 0 ? 0 : stored * 100 / cap);
        put("lcar", carHere); put("lgate", chuteOpen); put("lfull", carFull);
        put("car", carPct); text("carname", carName.length() > 10 ? carName.substring(0, 10) : carName);
        put("anim", running && fault.isEmpty() ? 1 : 0);
        put("metal", metal.getFluidAmount());
        put("pour", pouring);
        put("slurry", slurry.getFluidAmount() * 100 / slurry.getCapacity());
        text("fluid", metal.getFluid() != null ? metal.getFluid().getFluid().getName() : "");
        text("fore", metalOre);
        if (type == MachineType.BOREHOLE_PUMP) {
            put("flow", (int) Math.round(flowAvg * 20));
            put("depth", depth);
            int wl = (int) (100 * aquifer / Math.max(1, aquiferCap));
            put("well", wl);
            put("press", pv("lhigh") == 1 ? 10 : (int) Math.round(water.getFluidAmount() * 8.0 / water.getCapacity()));
            put("llow", wl < 25);
            put("ldry", fault.startsWith("DRY RUN"));
            put("anim", running && flowAvg > 0.05 ? 1 : 0);
            put("tank", water.getFluidAmount() * 100 / water.getCapacity());
        }
        if (type == MachineType.WATER_TOWER) {
            int lvl = water.getFluidAmount() * 100 / water.getCapacity();
            boolean t = lampTest > 0;
            put("stored", water.getFluidAmount());
            put("outflow", (int) outAvg);
            put("level", lvl);
            put("head", (int) Math.round(3 + 4.0 * lvl / 100));
            put("inlet", inletOpen); put("outlet", outletOpen);
            put("lhi", t || lvl >= 90); put("llo", t || lvl < 15); put("lover", t || overflow);
            put("lfill", t || inAvg > 0);
            put("lpower", t || energy().getEnergyStored() > 0);
            if (t) { put("lalarm", true); put("lfault", true); }
            put("anim", 0);
        }
        switch (type) {
            case OVERHEAD_LOADER:
                put("chuteg", (int) (chute / 16)); put("chute", (int) chute); put("lgate", chuteOpen); put("lblock", blocked); put("lcar", carHere);
                put("lstop", carHere && !carFull); put("ladv", carHere && carFull);
                put("anim", chuteOpen ? 1 : 0); break;
            case CAR_DUMPER:
                put("angle", Math.round(angle)); put("angled", Math.round(angle)); text("ores", String.valueOf(dumped));
                put("lclamp", cycle >= 1 && cycle <= 5); put("lrot", cycle == 2 || cycle == 4); put("ldump", cycle == 3); put("lcar", carHere);
                put("lempty", carHere && cycle == 0 && carPct == 0); put("lrun", cycle > 0); put("anim", cycle == 2 || cycle == 4 ? 1 : 0); break;
            case TANK_LOADER: case TANK_UNLOADER: {
                FluidTank t = type == MachineType.TANK_LOADER ? fin : fout;
                put("tankg", t.getFluidAmount() * 100 / t.getCapacity()); put("flowg", (int) Math.min(100, rate)); put("lcar", carHere);
                put("larm", chuteOpen); put("lflow", chuteOpen && load > 0); put("ldone", carFull); put("lwrong", "WRONG".equals(carName.length() > 0 ? fault : ""));
                put("arm", chuteOpen ? 1 : 0); put("anim", chuteOpen ? 1 : 0); break;
            }
            case COKE_OVEN: case BLAST_FURNACE: case REFINERY:
                put("prog", progressPct()); put("lwait", running && !hasFeed()); put("lfull", fault.startsWith("no room") || fault.contains("full"));
                put("lready", temp >= hotEnough()); put("lheat", running && temp < hotEnough()); put("anim", running && temp >= hotEnough() && hasFeed() ? 1 : 0);
                put("tempg", Math.round(temp / 100)); put("temp", Math.round(temp)); break;
            case ARC_FURNACE:
                put("temp", Math.round(temp)); put("tempg", Math.round(temp / 100)); put("prog", progressPct()); put("mw", (int) Math.round(load * type.power * 2));
                put("larc", running && load > 0); put("lhot", temp >= 1550); put("lelec", running && electrodeLife <= 0 && slotWith(TileMachine::isElectrode) < 0);
                put("lwait", running && !(slotWith(TileMachine::isIronCharge) >= 0 && slotWith(x -> hasOre(x, "fuelCoke")) >= 0)); put("lvolt", !supplyOk);
                put("anim", running && load > 0 ? 1 : 0); break;
            case ELECTROLYSER:
                put("h2", fout.getFluidAmount() / 100); put("o2", fout2.getFluidAmount() / 100); put("amps", running && load > 0 ? 3 + 3 * pv("current") : 0);
                put("waterg", fin.getFluidAmount() * 100 / fin.getCapacity()); put("lrun", running && load > 0); put("lwait", running && fin.getFluidAmount() < 100);
                put("lfull", fault.contains("full")); put("lac", supplyAc); put("lvolt", !supplyOk); put("anim", running && load > 0 ? 1 : 0); break;
            case WELDING_CELL:
                text("prog_txt", WELD_NAMES[pv("program")]); put("rate", rate); put("prog", progressPct()); put("amps", running && load > 0 ? 18 + 6 * pv("program") : 0);
                put("lrun", running && load > 0); put("lwait", running && !weldReady()); put("lfull", fault.startsWith("no room")); put("lgate", true); put("lvolt", !supplyOk);
                put("anim", running && load > 0 ? 1 : 0); break;
            case WINDMILL: {
                double v = millWind();
                put("wind", (int) Math.round(v)); put("windg", (int) Math.round(Math.min(30, v)));
                put("prog", progressPct()); put("lwait", running && !hasFeed()); put("lfull", fault.startsWith("no room"));
                put("lrun", millTurning()); put("lcalm", running && v < 3); put("lstorm", v > 20);
                put("anim", millTurning() ? 1 : 0); break;
            }
            case TIE_PLANT: {
                int st = tieStage();
                text("stagetxt", new String[]{"IDLE", "VACUUM", "FLOOD", "PRESSURE", "DRAIN"}[st]);
                put("bar", st == 3 ? 12 : st == 2 ? 2 : 0); put("creo", fin.getFluidAmount() * 100 / fin.getCapacity());
                put("lvac", st == 1); put("lflood", st == 2); put("lpress", st == 3); put("ldrain", st == 4); put("ldoor", st > 0);
                put("lwait", running && tieN == 0 && countOre("plankWood") < 2); put("lcreo", running && fin.getFluidAmount() < tieCreo());
                put("arm", st > 0 ? 1 : 0); put("anim", st > 0 ? 1 : 0); break;
            }
            case ROLLING_MILL: case WIRE_DRAWER: case OIL_PRESS: case SAWMILL: case CRATE_PACKER:
                put("prog", progressPct()); put("lwait", running && !hasFeed()); put("lfull", fault.startsWith("no room") || fault.contains("full"));
                put("ljam", "JAM".equals(fault)); put("anim", running && hasFeed() && fault.isEmpty() ? 1 : 0); break;
            default: break;
        }
        if (type == MachineType.ASSEMBLY_LINE) {
            int ri = pv("recipe") % AssemblyRecipes.ALL.size();
            text("product", AssemblyRecipes.ALL.get(ri).label);
            boolean any = false;
            for (int i = 0; i < 5; i++) { put("s" + i, stRecipe[i] >= 0); put("st" + i, stRecipe[i] + 1); any |= stRecipe[i] >= 0; }
            put("lwait", running && stRecipe[0] < 0);
            put("anim", running && any ? 1 : 0);
        }
    }

    // ---- burn-out (requested feature): an Electric Motor far too big for the plant
    // wrecks the gearbox and fries the controls. A warning first, then it burns. ----------------------------------------
    public boolean burnt;
    private String burntWhy = "";
    private float overdrive;            // stress from an oversized drive, 0..60
    private static final double OVERSIZE = 5;   // a motor more than 5x the plant's rating

    /** the Electric Motor coupled to the drive coupling (its back against it), or null */
    private com.dogpound.railmap.grid.TileGrid coupledMotor() {
        for (BlockPos m : members) {
            if (!(world.getBlockState(m).getBlock() instanceof com.dogpound.railmap.factory.block.BlockPort bp) || bp.kind != com.dogpound.railmap.factory.block.BlockPort.Kind.DRIVE) continue;
            for (EnumFacing f : EnumFacing.HORIZONTALS) {
                BlockPos q = m.offset(f);
                if (!(world.getTileEntity(q) instanceof com.dogpound.railmap.grid.TileGrid g) || g.kind() != com.dogpound.railmap.grid.GridKind.MOTOR) continue;
                IBlockState gs = world.getBlockState(q);
                if (gs.getPropertyKeys().contains(com.dogpound.railmap.grid.BlockGrid.FACING) && q.offset(gs.getValue(com.dogpound.railmap.grid.BlockGrid.FACING).getOpposite()).equals(m)) return g;
            }
        }
        return null;
    }

    private void overdriveTick() {
        if (burnt || !formed || type() == null) return;
        com.dogpound.railmap.grid.TileGrid g = coupledMotor();
        double plantKw = type().power * com.dogpound.railmap.grid.Elec.W_PER_FE_T / 1000.0;
        double motorKw = 0;
        if (g != null && g.remoteInfo().get("kw") instanceof Number n) motorKw = n.doubleValue();
        boolean turning = g != null && "running".equals(g.remoteInfo().get("state"));
        double ratio = plantKw > 0 ? motorKw / plantKw : 0;
        if (turning && running && ratio > OVERSIZE) {
            float before = overdrive;
            overdrive += (float) (ratio / OVERSIZE);
            if (before < 30 && overdrive >= 30) tell(String.format(java.util.Locale.ROOT, "§6⚠ %s: the gearbox is screaming - its %.0f kW motor is %.0fx too big (rated %.0f kW). Stop it or fit a smaller motor!", type().label, motorKw, ratio, plantKw));
            if (overdrive >= 60) burnOut(String.format(java.util.Locale.ROOT, "gearbox wrecked, controls fried - driven by a %.0f kW motor, %.0fx its rating", motorKw, ratio));
        } else overdrive = Math.max(0, overdrive - 2);
    }

    private void tell(String msg) {
        for (net.minecraft.entity.player.EntityPlayer pl : world.playerEntities) if (pl.getDistanceSq(pos) < 48 * 48) pl.sendMessage(new net.minecraft.util.text.TextComponentString(msg));
    }

    public void burnOut(String why) {
        if (burnt) return;
        burnt = true; burntWhy = why; running = false; fault = "BURNT OUT"; overdrive = 0;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.explosion"), SoundCategory.BLOCKS, 2.0f, 1.0f);
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 1.0f, 0.8f);
        if (world instanceof net.minecraft.world.WorldServer ws) {
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 50, 1.0, 0.8, 1.0, 0.03);
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.9, 0.6, 0.9, 0.02);
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.8, 0.4, 0.8, 0.0);
        }
        tell("§4🔥 " + type().label + " BURNT OUT: " + why + " - fix it with a screwdriver on its controller");
        panelDirty = true; markDirty(); sync();
    }

    /** a screwdriver on the burnt plant's controller */
    public String repair() {
        if (!burnt) return null;
        burnt = false; burntWhy = ""; fault = ""; overdrive = 0;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.air_tool"), SoundCategory.BLOCKS, 0.8f, 1.0f);
        panelDirty = true; markDirty(); sync();
        return "§aRepaired the " + type().label + " - new gearbox and control boards. Fit a motor its size before you start it again!";
    }

    // ---- control terminal on the controller (IR Extras Instrument Wire): gauges, screens, Control Buttons, the Data Link ----
    private int ctlWire = -1;
    @Override public int ctlWire() { return ctlWire; }
    @Override public void setCtlWire(int c) {
        ctlWire = c; markDirty(); panelDirty = true; sync();
        if (world != null) world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }
    @Override public String wiredName() { return type() == null ? "Factory plant" : type().label; }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return type() == null ? null : type().panel(); }
    @Override public String wiredPress(String id, String who) { return formed ? press(id, null) : "§cThe " + wiredName() + " isn't built yet"; }
    @Override public int wiredState(String id) { return pv(id); }
    @Override public int wiredLamp() { return burnt || estop || estopFault || (!fault.isEmpty() && !fault.startsWith("no ")) ? 3 : running ? 1 : 2; }

    /** what a gauge on its wire shows: the drive's volts / amps / kW (a 480 V motor on a VFD), the VFD's Hz, its temperature, a status screen */
    @Override
    public void wiredRead(com.dogpound.railmap.grid.Reading r, com.dogpound.railmap.grid.BlockInstrument.Inst k) {
        MachineType t = type();
        boolean powered = energy().getEnergyStored() > 0;
        double ratedW = (t == null ? 100 : t.power) * com.dogpound.railmap.grid.Elec.W_PER_FE_T;
        double w = running && powered ? ratedW * load : 0, volts = electrical() ? supplyVolts : powered ? 480 : 0, amps = volts > 0 ? w / (Math.sqrt(3) * volts * 0.86) : 0;
        r.lamp = wiredLamp();
        r.status = !formed ? "NOT BUILT" : r.lamp == 3 ? "FAULT" : running ? "LIVE" : "STOPPED";
        r.unit = ""; r.value = 0; r.full = 1;
        switch (k) {
            case VOLT: r.value = volts; r.full = 600; r.unit = "V"; break;
            case AMP: r.value = amps; r.full = Math.max(1, ratedW / (Math.sqrt(3) * 480 * 0.86) * 1.25); r.unit = "A"; break;
            case WATT: r.value = w; r.full = Math.max(1, ratedW * 1.25); r.unit = ratedW * 1.25 >= 1e6 ? "MW" : "kW"; break;
            case VAR: r.value = w * 0.59; r.full = Math.max(1, ratedW * 0.75); r.unit = "kvar"; break;
            case PF: r.value = running && powered ? 0.86 : 0; r.full = 1; r.unit = "PF"; break;
            case FREQ: r.value = running && powered ? (pv("speed") == 1 ? 60 : 45) * (0.5 + 0.5 * Math.min(1, load)) + 0 : 0; r.full = 66; r.unit = "Hz"; break;
            case TEMP: { int hot = hotEnough(); r.value = temp; r.full = hot > 0 ? hot * 1.4 : 120; r.unit = "°C"; break; }
            default: break;
        }
        r.line1 = wiredName() + (running ? "" : " - STOPPED");
        r.line2 = String.format(java.util.Locale.ROOT, "%s  %s", com.dogpound.railmap.grid.Elec.si(volts, "V"), com.dogpound.railmap.grid.Elec.si(amps, "A"));
        String extra = hotEnough() > 0 ? String.format(java.util.Locale.ROOT, "%.0f °C", temp)
                : fout.getFluidAmount() > 0 ? fout.getFluidAmount() + " mB out" : "made " + made;
        r.line3 = String.format(java.util.Locale.ROOT, "%s  %s", com.dogpound.railmap.grid.Elec.si(w, "W"), extra);
        if (!fault.isEmpty()) r.status = fault.toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public java.util.Map<String, Object> wiredInfo() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("x", pos.getX()); m.put("y", pos.getY()); m.put("z", pos.getZ());
        m.put("kind", type() == null ? "" : type().name().toLowerCase(java.util.Locale.ROOT)); m.put("name", wiredName());
        m.put("formed", formed); m.put("running", running); m.put("state", running ? "running" : "stopped");
        m.put("fault", fault); m.put("estop", estop || estopFault);
        m.put("progress", progressPct()); m.put("made", made); m.put("perMinute", rate);
        m.put("power", energy().getEnergyStored() * 100 / Math.max(1, energy().getMaxEnergyStored()));
        if (hotEnough() > 0) m.put("temp", Math.round(temp));
        if (fin.getFluidAmount() > 0) m.put("fluidIn", fin.getFluidAmount() + " mB " + fin.getFluid().getLocalizedName());
        if (fout.getFluidAmount() > 0) m.put("fluidOut", fout.getFluidAmount() + " mB " + fout.getFluid().getLocalizedName());
        java.util.List<String> cs = new java.util.ArrayList<>();
        for (com.dogpound.railmap.grid.Panel.C c : com.dogpound.railmap.grid.Reading.buttons(wiredPanel())) cs.add(c.id);
        m.put("controls", cs);
        return m;
    }

    private void sync() {
        if (!panelDirty || world == null || world.isRemote) return;
        panelDirty = false;
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 2);
    }

    // ---- saving ------------------------------------------------------------------------------------------------
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        writeShared(t);
        t.setTag("in", input.serializeNBT()); t.setTag("out", output.serializeNBT());
        t.setTag("water", water.writeToNBT(new NBTTagCompound()));
        t.setTag("metal", metal.writeToNBT(new NBTTagCompound())); t.setTag("slurry", slurry.writeToNBT(new NBTTagCompound())); t.setString("metalOre", metalOre);
        t.setInteger("energy", energy().getEnergyStored());
        NBTTagList m = new NBTTagList();
        for (BlockPos p : members) m.appendTag(new NBTTagLong(p.toLong()));
        t.setTag("members", m);
        t.setInteger("progress", progress);
        t.setIntArray("stRecipe", stRecipe); t.setIntArray("stProg", stProg);
        t.setTag("fin", fin.writeToNBT(new NBTTagCompound())); t.setTag("fout", fout.writeToNBT(new NBTTagCompound())); t.setTag("fout2", fout2.writeToNBT(new NBTTagCompound())); t.setInteger("electrode", electrodeLife);
        t.setInteger("tieN", tieN);
        t.setInteger("cycle", cycle); t.setInteger("ptimer", ptimer); t.setFloat("angle", angle); t.setFloat("chute", chute); t.setInteger("dumped", dumped);
        if (waterWorks()) {
            t.setBoolean("surveyed", surveyed); t.setDouble("aquifer", aquifer); t.setBoolean("autoOn", autoOn);
            t.setBoolean("inletOpen", inletOpen); t.setBoolean("outletOpen", outletOpen);
        }
        return t;
    }

    /** what the client needs to draw the panel and the moving parts */
    private void writeShared(NBTTagCompound t) {
        if (type() != null) t.setString("type", type.name());
        t.setBoolean("formed", formed); t.setBoolean("running", running); t.setBoolean("estop", estop); t.setBoolean("estopFault", estopFault);
        t.setString("fault", fault); t.setFloat("temp", temp); t.setInteger("made", made); t.setInteger("ctlWire", ctlWire);
        t.setBoolean("burnt", burnt); t.setString("burntWhy", burntWhy);
        NBTTagCompound pc = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : ps.entrySet()) pc.setInteger(e.getKey(), e.getValue());
        t.setTag("ps", pc);
        NBTTagCompound tc = new NBTTagCompound();
        for (Map.Entry<String, String> e : pt.entrySet()) tc.setString(e.getKey(), e.getValue());
        t.setTag("pt", tc);
        t.setString("pressId", pressId); t.setLong("pressAt", pressAt);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        try { if (t.hasKey("type")) setType(MachineType.valueOf(t.getString("type"))); } catch (IllegalArgumentException ignored) { }
        formed = t.getBoolean("formed"); running = t.getBoolean("running"); estop = t.getBoolean("estop"); estopFault = t.getBoolean("estopFault");
        fault = t.getString("fault"); temp = t.hasKey("temp") ? t.getFloat("temp") : 25; made = t.getInteger("made");
        ctlWire = t.hasKey("ctlWire") ? t.getInteger("ctlWire") : -1;
        burnt = t.getBoolean("burnt"); burntWhy = t.getString("burntWhy");
        NBTTagCompound pc = t.getCompoundTag("ps");
        for (String k : pc.getKeySet()) ps.put(k, pc.getInteger(k));
        NBTTagCompound tc = t.getCompoundTag("pt");
        for (String k : tc.getKeySet()) pt.put(k, tc.getString(k));
        pressId = t.getString("pressId"); pressAt = t.getLong("pressAt");
        if (t.hasKey("in")) input.deserializeNBT(t.getCompoundTag("in"));
        if (t.hasKey("out")) output.deserializeNBT(t.getCompoundTag("out"));
        if (t.hasKey("water")) water.readFromNBT(t.getCompoundTag("water"));
        if (t.hasKey("metal")) metal.readFromNBT(t.getCompoundTag("metal"));
        if (t.hasKey("slurry")) slurry.readFromNBT(t.getCompoundTag("slurry"));
        metalOre = t.getString("metalOre");
        if (t.hasKey("energy") && type != null) { energy = new Buffer(type.buffer, t.getInteger("energy")); }
        if (t.hasKey("members")) {
            members.clear(); memberSet.clear();
            NBTTagList m = t.getTagList("members", 4);
            for (int i = 0; i < m.tagCount(); i++) { long l = ((NBTTagLong) m.get(i)).getLong(); members.add(BlockPos.fromLong(l)); memberSet.add(l); }
        }
        progress = t.getInteger("progress");
        int[] a1 = t.getIntArray("stRecipe"), a2 = t.getIntArray("stProg");
        if (a1.length == 5) System.arraycopy(a1, 0, stRecipe, 0, 5);
        if (a2.length == 5) System.arraycopy(a2, 0, stProg, 0, 5);
        if (t.hasKey("fin")) fin.readFromNBT(t.getCompoundTag("fin"));
        if (t.hasKey("fout")) fout.readFromNBT(t.getCompoundTag("fout"));
        if (t.hasKey("fout2")) fout2.readFromNBT(t.getCompoundTag("fout2"));
        electrodeLife = t.getInteger("electrode");
        tieN = t.getInteger("tieN");
        cycle = t.getInteger("cycle"); ptimer = t.getInteger("ptimer"); angle = t.getFloat("angle"); chute = t.getFloat("chute"); dumped = t.getInteger("dumped");
        surveyed = false;                                     // re-surveyed on the first tick; keeps the aquifer level
        aquifer = t.getDouble("aquifer"); autoOn = t.getBoolean("autoOn");
        inletOpen = t.getBoolean("inletOpen"); outletOpen = t.getBoolean("outletOpen");
        if (t.getBoolean("surveyed")) restoreAquifer = true;
    }
    private boolean restoreAquifer;

    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound t = super.getUpdateTag(); writeShared(t); return t; }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { NBTTagCompound t = new NBTTagCompound(); writeShared(t); return new SPacketUpdateTileEntity(pos, 0, t); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) {
        NBTTagCompound t = p.getNbtCompound();
        try { if (t.hasKey("type")) setType(MachineType.valueOf(t.getString("type"))); } catch (IllegalArgumentException ignored) { }
        formed = t.getBoolean("formed"); running = t.getBoolean("running"); temp = t.getFloat("temp"); burnt = t.getBoolean("burnt");
        NBTTagCompound pc = t.getCompoundTag("ps");
        for (String k : pc.getKeySet()) ps.put(k, pc.getInteger(k));
        NBTTagCompound tc = t.getCompoundTag("pt");
        for (String k : tc.getKeySet()) pt.put(k, tc.getString(k));
        pressId = t.getString("pressId"); pressAt = t.getLong("pressAt");
        phaseSpeed = pv("anim") == 1 ? 1f : 0f;
    }

    @Override public boolean shouldRefresh(net.minecraft.world.World w, BlockPos p, IBlockState o, IBlockState n) { return o.getBlock() != n.getBlock(); }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (type() == null) return super.getRenderBoundingBox();
        int r = Math.max(type.width(), Math.max(type.height(), type.depth())) + 1;
        return new AxisAlignedBB(pos).grow(r);
    }

    @Override public double getMaxRenderDistanceSquared() { return 96 * 96; }

    // =============================================================================================================
    // the new plants (requested feature)
    // =============================================================================================================
    private static boolean hasOre(ItemStack s, String ore) {
        if (s.isEmpty() || !net.minecraftforge.oredict.OreDictionary.doesOreNameExist(ore)) return false;
        for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(s)) if (net.minecraftforge.oredict.OreDictionary.getOreName(id).equals(ore)) return true;
        return false;
    }
    private static String oreSuffix(ItemStack s, String prefix) {
        for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(s)) { String n = net.minecraftforge.oredict.OreDictionary.getOreName(id); if (n.startsWith(prefix) && n.length() > prefix.length()) return n.substring(prefix.length()); }
        return null;
    }
    private static ItemStack firstOre(String name, int n) {
        for (ItemStack o : net.minecraftforge.oredict.OreDictionary.getOres(name)) { ItemStack c = o.copy(); if (c.getMetadata() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) c.setItemDamage(0); c.setCount(n); return c; }
        return ItemStack.EMPTY;
    }
    private static ItemStack part(int meta, int n) {
        net.minecraft.item.Item it = com.dogpound.railmap.proxy.CommonProxy.part;
        return it == null ? ItemStack.EMPTY : new ItemStack(it, n, meta);
    }
    /** the plate any mod makes from this ingot (steel -> IR Extras Reinforced Steel Plate) */
    private static ItemStack plateFor(ItemStack ingot) {
        if (hasOre(ingot, "ingotSteel")) return part(0, 1);
        String m = oreSuffix(ingot, "ingot");
        return m == null ? ItemStack.EMPTY : firstOre("plate" + m, 1);
    }
    private static boolean isSeed(ItemStack s) {
        net.minecraft.item.Item i = s.getItem();
        return i == net.minecraft.init.Items.WHEAT_SEEDS || i == net.minecraft.init.Items.PUMPKIN_SEEDS || i == net.minecraft.init.Items.MELON_SEEDS
                || i == net.minecraft.init.Items.BEETROOT_SEEDS || hasOre(s, "seed") || hasOre(s, "listAllseed");
    }
    private static boolean feedstock(FluidStack f) {
        if (f == null) return false;
        String n = f.getFluid().getName().toLowerCase(java.util.Locale.ROOT);
        return (n.contains("oil") || n.contains("crude") || n.contains("creosote")) && !n.contains("diesel");
    }
    private int slotWith(java.util.function.Predicate<ItemStack> p) {
        for (int i = 0; i < input.getSlots(); i++) if (!input.getStackInSlot(i).isEmpty() && p.test(input.getStackInSlot(i))) return i;
        return -1;
    }
    private int recipeTicks() {
        boolean fast = pv("speed") == 1;
        switch (type) {
            case COKE_OVEN: return 300; case BLAST_FURNACE: return pv("blast") == 1 ? 110 : 170; case REFINERY: return pv("reflux") == 1 ? 260 : 180;
            case ROLLING_MILL: return (fast ? 60 : 90) + (pv("gap") == 0 ? 15 : 0); case WIRE_DRAWER: return (fast ? 50 : 80) + (pv("die") == 0 ? 10 : 0);
            case ARC_FURNACE: return (int) (new int[]{360, 260, 190}[pv("tap")] * (pv("lance") == 1 && isOxygen(fin.getFluid()) && fin.getFluidAmount() >= 50 ? 0.65 : 1));
            case ELECTROLYSER: return new int[]{60, 40, 25}[pv("current")];
            case WELDING_CELL: return new int[]{80, 120, 60}[pv("program")];
            case TIE_PLANT: return pv("process") == 0 ? 600 : 420;
            case WINDMILL: { double v = Math.min(12, millWind()) * (pv("sails") == 1 ? 0.55 : 1); return v < 3 ? 400 : (int) (40 * 12 / v); }
            case OIL_PRESS: return new int[]{60, 40, 25}[pv("feed")]; case SAWMILL: return new int[]{90, 60, 40}[pv("feed")]; case CRATE_PACKER: return fast ? 30 : 50;
            default: return 100;
        }
    }
    private int progressPct() { return Math.min(100, progress * 100 / Math.max(1, recipeTicks())); }
    private int hotEnough() { return type == MachineType.COKE_OVEN ? 1000 : type == MachineType.BLAST_FURNACE ? 1450 : type == MachineType.REFINERY ? 350 : 0; }
    private boolean hasFeed() {
        switch (type) {
            case BLAST_FURNACE: return slotWith(x -> hasOre(x, "ingotIron")) >= 0 && slotWith(x -> hasOre(x, "fuelCoke")) >= 0;
            case REFINERY: return fin.getFluidAmount() >= 500;
            case CRATE_PACKER: return pv("mode") == 1 ? slotWith(x -> x.getItem() == CommonProxy.crate) >= 0 : slotWith(x -> x.getItem() != CommonProxy.crate && x.getCount() >= x.getMaxStackSize()) >= 0;
            default: return firstInput() >= 0;
        }
    }

    /** a furnace that has to come up to heat first (coke oven, blast furnace, refinery column) */
    private boolean heat(double max) {
        double sp = new double[]{0.5, 0.75, 1.0}[pv("setpoint")];
        double target = running ? max * (0.8 + 0.2 * sp) : 25;
        if (running && !draw(sp * (temp < hotEnough() ? 1.0 : 0.6))) target = 25;
        temp += (float) ((target - temp) * (target > temp ? 0.003 * sp * (type == MachineType.BLAST_FURNACE && pv("blast") == 1 ? 1.6 : 1) : 0.002));
        return running && temp >= hotEnough();
    }

    private void cokeOven() {
        if (!heat(1250) || estopFault) { progress = 0; return; }
        int slot = firstInput();
        if (slot < 0) { progress = 0; return; }
        if (pv("doors") == 1) { fault = "oven doors open - shut them"; return; }
        if (fault.startsWith("oven doors")) fault = "";
        if (++progress < recipeTicks()) return;
        progress = 0;
        boolean wood = isMat(input.getStackInSlot(slot), com.dogpound.railmap.factory.ore.ItemMaterial.M.SAWDUST);   // her list #92: sawdust carbonised to charcoal + wood tar
        if (wood && input.getStackInSlot(slot).getCount() < 4) return;
        if (emit(wood ? new ItemStack(net.minecraft.init.Items.COAL, 1, 1) : mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.COKE, 1))) {
            input.extractItem(slot, wood ? 4 : 1, false);
            Fluid cr = com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.CREOSOTE, "creosote");
            if (fout.fill(new FluidStack(cr, 250), true) < 250) fault = "creosote tank full - pipe it away";
            else if (fault.startsWith("creosote")) fault = "";
        }
    }

    private void blastFurnace() {
        if (!heat(1650) || estopFault) { progress = 0; return; }
        int fe = slotWith(x -> hasOre(x, "ingotIron")), co = slotWith(x -> hasOre(x, "fuelCoke"));
        if (fe < 0 || co < 0) { progress = 0; return; }
        if (++progress < recipeTicks()) return;
        progress = 0;
        if (emit(com.dogpound.railmap.factory.ore.ItemMaterial.make(com.dogpound.railmap.factory.ore.ItemMaterial.M.STEEL, 1))) {
            input.extractItem(fe, 1, false); input.extractItem(co, 1, false);
            if (world.rand.nextInt(4) == 0) emit(com.dogpound.railmap.factory.ore.ItemMaterial.make(com.dogpound.railmap.factory.ore.ItemMaterial.M.SLAG, 1));
        }
    }

    private void refinery() {
        if (!heat(420) || estopFault) { progress = 0; return; }
        if (fin.getFluidAmount() < 500) { progress = 0; return; }
        if (++progress < recipeTicks()) return;
        progress = 0;
        Fluid d = com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.DIESEL, "diesel", "biodiesel");
        int yield = pv("reflux") == 1 ? 380 : 300;
        if (fout.fill(new FluidStack(d, yield), false) < yield) { fault = "diesel tank full - pipe it away"; return; }
        if (fault.startsWith("diesel tank")) fault = "";
        fout.fill(new FluidStack(d, yield), true); fin.drainInternal(500, true);
        made += yield; madeWindow += 1;
    }

    /** the simple motor-driven plants: one input item -> products, after a cycle */
    private void simplePlant(java.util.function.Function<ItemStack, ItemStack[]> products, int takeEach, double power) {
        if (!running || estopFault) { progress = 0; return; }
        int slot = firstInput();
        if (slot < 0) { progress = 0; return; }
        if (!draw(power * (pv("speed") == 1 ? 1.3 : 0.8))) return;
        if (++progress < recipeTicks()) return;
        progress = 0;
        ItemStack in = input.getStackInSlot(slot);
        ItemStack[] out = products.apply(in);
        if (out == null || out.length == 0) { input.extractItem(slot, 1, false); return; }
        for (ItemStack o : out) if (!o.isEmpty() && !ItemHandlerHelper.insertItemStacked(output, o, true).isEmpty()) { fault = "no room - empty the output hatch"; return; }
        if (fault.startsWith("no room")) fault = "";
        for (ItemStack o : out) if (!o.isEmpty()) emit(o);
        input.extractItem(slot, takeEach, false);
    }

    private void rollingMill() {
        if (pv("gap") == 2) {                                                           // the RAIL pass: steel bloom -> flat-bottom rail (irRail)
            simplePlant(in -> new ItemStack[]{hasOre(in, "ingotSteel") ? mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.RAIL, 1) : ItemStack.EMPTY}, 1, 1.3);
            return;
        }
        simplePlant(in -> new ItemStack[]{plateFor(in)}, 1, 1.0);
    }

    private void wireDrawer() {
        simplePlant(in -> {
            net.minecraft.item.Item wire = com.dogpound.railmap.proxy.CommonProxy.instrumentWire;
            if (hasOre(in, "ingotCopper")) return new ItemStack[]{part(13, pv("die") == 1 ? 3 : 4)};
            if (hasOre(in, "ingotGold")) return new ItemStack[]{part(13, 2)};
            return new ItemStack[]{wire == null ? ItemStack.EMPTY : new ItemStack(wire, pv("die") == 1 ? 6 : 8, 0)};
        }, 1, 1.0);
    }

    private void oilPress() {
        if (!running || estopFault) { progress = 0; return; }
        int slot = firstInput();
        if (slot < 0) { progress = 0; return; }
        Fluid oil = com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.PLANT_OIL, "plantoil", "plant_oil", "seed_oil");
        if (fout.fill(new FluidStack(oil, 60), false) < 60) { fault = "oil tank full - pipe it away"; return; }
        if (fault.startsWith("oil tank")) fault = "";
        if (!draw(new double[]{0.6, 0.9, 1.2}[pv("feed")])) return;
        if (++progress < recipeTicks()) return;
        progress = 0;
        fout.fill(new FluidStack(oil, 60), true); input.extractItem(slot, 1, false); made++; madeWindow++;
    }

    // ---- electrical plants: the Power Inlet (requested feature) -----------------------------------------------------------------------------------------
    /** the voltage each electrical plant is built for */
    private static com.dogpound.railmap.grid.Elec.Level rated(MachineType t) {
        return t == MachineType.ARC_FURNACE ? com.dogpound.railmap.grid.Elec.Level.MV13800 : t == MachineType.ELECTROLYSER ? com.dogpound.railmap.grid.Elec.Level.DC750 : com.dogpound.railmap.grid.Elec.Level.AC480;
    }
    public boolean electrical() { return type == MachineType.ARC_FURNACE || type == MachineType.ELECTROLYSER || type == MachineType.WELDING_CELL; }
    private boolean supplyOk = true, supplyAc;
    private String supplyWhat = "";
    private double supplyVolts;
    private int overStress, electrodeLife;
    private static final String[] WELD_NAMES = {"PLATE", "FRAME", "SHAFT"};

    /** every 5 ticks: read the grid network touching the Power Inlet; draw from it at the right voltage, or protect / burn / blow up */
    private void powerInlet() {
        if (burnt) { overStress = 0; supplyOk = false; supplyWhat = "burnt out - screwdriver to repair"; return; }   // dead: draws nothing, can't blow twice
        com.dogpound.railmap.grid.Elec.Level want = rated(type);
        com.dogpound.railmap.grid.GridData d = com.dogpound.railmap.grid.GridData.get(world);
        com.dogpound.railmap.grid.GridData.Net net = null;
        com.dogpound.railmap.grid.GridData.Node node = null;
        for (BlockPos m : members) {
            if (!(world.getBlockState(m).getBlock() instanceof com.dogpound.railmap.factory.block.BlockPort bp) || bp.kind != com.dogpound.railmap.factory.block.BlockPort.Kind.POWER) continue;
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos q = m.offset(f);
                if (!(world.getTileEntity(q) instanceof com.dogpound.railmap.grid.TileGrid)) continue;
                net = d.netAt(q, f.getOpposite()); node = d.node(q);
                if (net != null) break;
            }
            if (net != null) break;
        }
        supplyAc = false;
        if (net == null || !net.problem.isEmpty() || net.level == null) {
            supplyOk = false; supplyVolts = 0;
            supplyWhat = net == null ? "no grid at the Power Inlet" : !net.problem.isEmpty() ? "supply dead: " + net.problem : "supply has no voltage";
            overStress = 0; return;
        }
        com.dogpound.railmap.grid.Elec.Level lv = net.level;
        supplyVolts = lv.volts;
        if (shed) { supplyOk = false; supplyWhat = "LOAD SHED by the grid"; return; }
        if (lv.ac != want.ac) {
            supplyOk = false; supplyAc = lv.ac;
            supplyWhat = (want.ac ? "needs AC, fed " : "needs DC, fed ") + lv.label + (want.ac ? "" : " - the cells just fizz");
            return;
        }
        if (lv.volts > want.volts * 1.15) {                                   // too much: a warning, then it goes
            supplyOk = false; supplyWhat = "OVERVOLTAGE: fed " + lv.label + ", built for " + want.label;
            overStress++;
            if (overStress == 20) tell("§6⚠ " + type.label + " is fed " + lv.label + " - it's built for " + want.label + "! Open the breaker NOW");
            if (overStress >= 40) {
                overStress = 0;
                if (type == MachineType.WELDING_CELL) burnOut("welding transformer burnt up - fed " + lv.label + ", built for " + want.label);
                else explode(type == MachineType.ARC_FURNACE ? "the furnace transformer EXPLODED - fed " + lv.label + ", built for " + want.label
                        : "the hydrogen IGNITED - the cells were fed " + lv.label + ", built for " + want.label);
            }
            return;
        }
        overStress = Math.max(0, overStress - 2);
        if (lv.volts < want.volts * 0.8) { supplyOk = false; supplyWhat = "UNDERVOLTAGE: " + lv.label + " (needs " + want.label + ") - locked out"; return; }
        supplyOk = true; supplyWhat = lv.label;
        int room = energy().getMaxEnergyStored() - energy().getEnergyStored();
        long wantFe = Math.min(room, (long) type.power * 6);
        if (wantFe <= 0) return;
        long got = d.draw(net, wantFe);
        energy().receiveEnergy((int) got, false);
        if (node != null) node.drawThis += got;
    }

    /** a real blow-up: fire and a blast that hurts whoever's near, but leaves the blocks standing */
    public void explode(String why) {
        burnOut(why);
        world.newExplosion(null, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 3.5f, true, false);
    }

    // ---- Electric Arc Furnace ------------------------------------------------------------------------------------------
    private static boolean isIronCharge(ItemStack s) {
        if (s.isEmpty()) return false;
        for (String o : new String[]{"ingotIron", "oreIron", "dustIron", "scrapIron", "scrapSteel", "ingotScrap", "nuggetIron"}) if (hasOre(s, o)) return true;
        return s.getItem() == net.minecraft.init.Items.IRON_INGOT || s.getItem() == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.IRON_ORE);
    }
    private static boolean isElectrode(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof com.dogpound.railmap.factory.ore.ItemMaterial && s.getMetadata() == com.dogpound.railmap.factory.ore.ItemMaterial.M.ELECTRODE.ordinal();
    }
    private static boolean isOxygen(FluidStack f) { return f != null && (f.getFluid() == com.dogpound.railmap.factory.fluid.ModFluids.OXYGEN || "oxygen".equals(f.getFluid().getName())); }

    private void arcFurnace() {
        double tap = new double[]{0.5, 0.75, 1.0}[pv("tap")];
        boolean arcing = running && !estopFault && !burnt && supplyOk && (electrodeLife > 0 || slotWith(TileMachine::isElectrode) >= 0);
        if (arcing && electrodeLife <= 0) { int e = slotWith(TileMachine::isElectrode); input.extractItem(e, 1, false); electrodeLife = 16; clunk(0.8f); }
        if (arcing && !draw(tap)) arcing = false;
        double target = arcing ? 1800 * (0.85 + 0.15 * tap) : 25;
        temp += (float) ((target - temp) * (target > temp ? 0.006 * tap : 0.002));
        if (running && !supplyOk) fault = supplyWhat;
        else if (fault.startsWith("OVERVOLT") || fault.startsWith("UNDERVOLT") || fault.startsWith("no grid") || fault.startsWith("needs") || fault.startsWith("supply")) fault = "";
        if (!arcing || temp < 1550) { if (!arcing) progress = 0; return; }
        int fe = slotWith(TileMachine::isIronCharge), co = slotWith(x -> hasOre(x, "fuelCoke"));
        if (fe < 0 || co < 0) { progress = 0; return; }
        if (++progress < recipeTicks()) return;
        progress = 0;
        int n = 0;                                                            // a heat: up to 4 iron, 1 coke -> that much steel + slag
        for (int k = 0; k < 4; k++) { int i = slotWith(TileMachine::isIronCharge); if (i < 0) break; ItemStack one = input.getStackInSlot(i); if (hasOre(one, "nuggetIron") && one.getCount() < 9) break; input.extractItem(i, hasOre(one, "nuggetIron") ? 9 : 1, false); n++; }
        if (n == 0) return;
        input.extractItem(co, 1, false);
        if (pv("lance") == 1 && isOxygen(fin.getFluid()) && fin.getFluidAmount() >= 50) fin.drainInternal(50, true);
        emit(com.dogpound.railmap.factory.ore.ItemMaterial.make(com.dogpound.railmap.factory.ore.ItemMaterial.M.STEEL, n));
        emit(com.dogpound.railmap.factory.ore.ItemMaterial.make(com.dogpound.railmap.factory.ore.ItemMaterial.M.SLAG, 1));
        electrodeLife--; made += n; madeWindow += n;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.ARC_FLASH, net.minecraft.util.SoundCategory.BLOCKS, 0.25f, 1.0f);
    }

    // ---- Electrolysis Plant ---------------------------------------------------------------------------------------------
    private void electrolyser() {
        if (running && !supplyOk) fault = supplyWhat;
        else if (fault.startsWith("OVERVOLT") || fault.startsWith("UNDERVOLT") || fault.startsWith("no grid") || fault.startsWith("needs") || fault.startsWith("supply")) fault = "";
        if (!running || estopFault || burnt || !supplyOk) { progress = 0; return; }
        if (fin.getFluidAmount() < 100) { progress = 0; return; }
        if (fout.getCapacity() - fout.getFluidAmount() < 200 || fout2.getCapacity() - fout2.getFluidAmount() < 100) { fault = "gas tanks full - pipe the hydrogen / oxygen away"; return; }
        if (fault.startsWith("gas tanks")) fault = "";
        if (!draw(new double[]{0.4, 0.7, 1.0}[pv("current")])) return;
        if (++progress < recipeTicks()) return;
        progress = 0;
        fin.drainInternal(100, true);                                          // 2 H2O -> 2 H2 + O2
        fout.fill(new FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.HYDROGEN, 200), true);
        fout2.fill(new FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.OXYGEN, 100), true);
        made += 200; madeWindow++;
    }

    // ---- Robotic Welding Cell -------------------------------------------------------------------------------------------
    private int countOre(String ore) { int n = 0; for (int i = 0; i < input.getSlots(); i++) if (hasOre(input.getStackInSlot(i), ore)) n += input.getStackInSlot(i).getCount(); return n; }
    private void takeOre(String ore, int n) { for (int i = 0; i < input.getSlots() && n > 0; i++) if (hasOre(input.getStackInSlot(i), ore)) n -= input.extractItem(i, n, false).getCount(); }
    private boolean weldReady() {
        switch (pv("program")) {
            case 0: return countOre("plateSteel") >= 2 && countOre("ingotSteel") >= 1 || countOre("ingotSteel") >= 3;
            case 1: return countOre("ingotSteel") >= 4;
            default: return countOre("ingotSteel") >= 2;
        }
    }
    private void weldingCell() {
        if (running && !supplyOk) fault = supplyWhat;
        else if (fault.startsWith("OVERVOLT") || fault.startsWith("UNDERVOLT") || fault.startsWith("no grid") || fault.startsWith("needs") || fault.startsWith("supply")) fault = "";
        if (!running || estopFault || burnt || !supplyOk || !weldReady()) { progress = 0; return; }
        if (!draw(1.0)) return;
        if (world.rand.nextInt(6) == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.servo"), net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1.0f);
        if (++progress < recipeTicks()) return;
        progress = 0;
        int prog = pv("program");
        ItemStack out = prog == 0 ? part(0, 1) : prog == 1 ? part(1, 1) : part(10, 1);   // reinforced plate, machine frame, steel shaft
        if (!emit(out)) { fault = "no room - empty the output hatch"; return; }
        if (fault.startsWith("no room")) fault = "";
        if (prog == 0) { if (countOre("plateSteel") >= 2) { takeOre("plateSteel", 2); takeOre("ingotSteel", 1); } else takeOre("ingotSteel", 3); }
        else takeOre("ingotSteel", prog == 1 ? 4 : 2);
        made++; madeWindow++;
    }

    // ---- grain windmill ----------------------------------------------------------------------------------------------
    private int stormStress;

    private static boolean isGrain(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.getItem() == net.minecraft.init.Items.WHEAT) return true;
        for (String o : new String[]{"cropWheat", "cropBarley", "cropRye", "cropOats", "cropCorn", "cropRice", "cropMillet", "cropSorghum", "grainWheat"}) if (hasOre(s, o)) return true;
        return false;
    }

    /** the wind at the sails (the cap is ~5 blocks up, a mill is built ~1:4 so call it 22 m, more on a hill) */
    private double millWind() { return world == null ? 0 : com.dogpound.railmap.grid.Wind.at(world, 22 + Math.max(0, pos.getY() - 64) * 1.5); }

    private boolean millTurning() { return formed && running && !estop && !estopFault && !burnt && pv("sails") != 2 && millWind() >= 3; }

    /** the stones grind as fast as the wind turns the sails; full sails in a gale get torn off */
    private void windmill() {
        if (!running || estopFault || burnt) { progress = 0; return; }
        double v = millWind();
        if (v > 20 && pv("sails") == 0) {
            if (++stormStress == 200) tell("§6⚠ Grain Windmill: the sails are cracking in a " + Math.round(v) + " m/s gale - REEF or FURL them, or BRAKE!");
            if (stormStress >= 400) { stormStress = 0; burnOut("the storm tore the sails off - reef or furl them in high wind"); return; }
        } else stormStress = Math.max(0, stormStress - 2);
        if (!millTurning()) { if (fault.startsWith("no room")) return; progress = 0; return; }
        int slot = firstInput();
        if (slot < 0) { progress = 0; return; }
        if (++progress < recipeTicks()) return;
        progress = 0;
        ItemStack flour = com.dogpound.railmap.factory.ore.ItemMaterial.make(com.dogpound.railmap.factory.ore.ItemMaterial.M.FLOUR, 1);
        if (!emit(flour)) { fault = "no room - empty the flour bin"; return; }
        if (fault.startsWith("no room")) fault = "";
        input.extractItem(slot, 1, false); made++; madeWindow++;
    }

    private void sawmill() {
        simplePlant(in -> {
            ItemStack planks = ItemStack.EMPTY;
            net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
                @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) { return false; } }, 1, 1);
            grid.setInventorySlotContents(0, in.copy().splitStack(1));
            net.minecraft.item.crafting.IRecipe r = net.minecraft.item.crafting.CraftingManager.findMatchingRecipe(grid, world);
            if (r != null) { planks = r.getCraftingResult(grid).copy(); planks.setCount(planks.getCount() * 3 / 2); }
            if (planks.isEmpty()) planks = new ItemStack(net.minecraft.init.Blocks.PLANKS, 6);
            return new ItemStack[]{planks, com.dogpound.railmap.factory.ore.ItemMaterial.make(com.dogpound.railmap.factory.ore.ItemMaterial.M.SAWDUST, 2)};
        }, 1, 1.0);
    }

    private void cratePacker() {
        if (!running || estopFault) { progress = 0; return; }
        boolean unpack = pv("mode") == 1;
        int slot = unpack ? slotWith(x -> x.getItem() == CommonProxy.crate) : slotWith(x -> x.getItem() != CommonProxy.crate && x.getCount() >= x.getMaxStackSize());
        if (slot < 0) { progress = 0; return; }
        if (!draw(0.8)) return;
        if (++progress < recipeTicks()) return;
        progress = 0;
        ItemStack in = input.getStackInSlot(slot);
        ItemStack out = unpack ? com.dogpound.railmap.factory.ore.ItemCrate.contents(in) : com.dogpound.railmap.factory.ore.ItemCrate.pack(in.copy());
        if (out.isEmpty()) { input.extractItem(slot, 1, false); return; }
        if (emit(out)) input.extractItem(slot, unpack ? 1 : in.getMaxStackSize(), false);
    }

    // ---- trains -------------------------------------------------------------------------------------------------
    private void overheadLoader() {
        BlockPos head = Structure.at(type, pos, facing(), 1, 0, 1);
        int dist = 0;
        for (int d = 1; d <= 13; d++) { if (!world.isAirBlock(head.down(d))) { dist = d; break; } }
        blocked = dist == 1;
        Freight.Car car = blocked || dist == 0 ? null : Freight.find(world, new AxisAlignedBB(head.getX() - 1.5, head.getY() - dist, head.getZ() - 1.5, head.getX() + 2.5, head.getY(), head.getZ() + 2.5));
        int target = new int[]{50, 75, 100}[pv("fill")];
        carHere = car != null; carName = car == null ? "" : car.name; carPct = car == null ? 0 : car.fillPct;
        carFull = car != null && car.fillPct >= target;
        boolean auto = pv("mode") == 1;
        boolean want = carHere && !carFull && firstInput() >= 0 && (running || auto) && !estopFault && !blocked;
        float chuteTarget = want || carHere && !carFull && (running || auto) ? Math.max(0, (dist - 3) * 16f) : 0;
        chute += Math.max(-1.5f, Math.min(1.5f, chuteTarget - chute));                 // the telescoping chute runs down / up
        chuteOpen = want && Math.abs(chute - chuteTarget) < 0.5f;
        if (blocked) fault = "chute blocked - cut a hole in the floor under the chute";
        else if (fault.startsWith("chute blocked")) fault = "";
        if (!chuteOpen) { if (running && carFull && !auto) { running = false; clunk(1.0f); } return; }
        if (!draw(1.0)) { chuteOpen = false; return; }
        if (world.getTotalWorldTime() % 10 != 0) return;
        int moved = bill(true, car, Freight.move(input, car.inv, 16, car));
        made += moved; madeWindow += moved;
        if (moved == 0 && !Freight.refused.isEmpty()) fault = "WRONG CAR: " + Freight.refused;
        else if (fault.startsWith("WRONG CAR")) fault = "";
    }

    private void carDumper() {
        Freight.Car car = Freight.find(world, trackBox());
        carHere = car != null; carName = car == null ? "" : car.name; carPct = car == null ? 0 : car.fillPct;
        boolean auto = pv("mode") == 1;
        if (estopFault) return;
        switch (cycle) {
            case 0:
                if (carHere && carPct > 0 && (running || auto)) { cycle = 1; ptimer = 20; clunk(0.8f); }
                return;
            case 1: if (--ptimer <= 0) cycle = 2; return;                                                   // clamps close
            case 2: if (!draw(1.0)) return; angle = Math.min(155, angle + 1.4f); if (angle >= 155) { cycle = 3; ptimer = 0; } return;   // roll over
            case 3: {                                                                                       // empty it into the hopper
                if (car == null) { cycle = 4; return; }
                if (world.getTotalWorldTime() % 4 != 0) return;
                int moved = bill(false, car, Freight.move(car.inv, output, 64, null));
                dumped += moved; made += moved; madeWindow += moved;
                if (Freight.count(car.inv) == 0) cycle = 4;
                else if (moved == 0) fault = "hopper full - empty the output hatch";
                if (moved > 0 && fault.startsWith("hopper full")) fault = "";
                return;
            }
            case 4: if (!draw(0.7)) return; angle = Math.max(0, angle - 1.6f); if (angle <= 0) { cycle = 5; ptimer = 20; } return;   // roll back
            case 5: if (--ptimer <= 0) { cycle = 0; clunk(1.1f); if (!auto) running = false; } return;          // release the clamps
            default: cycle = 0;
        }
    }

    /** a tank car (or diesel locomotive) standing at the station: its fluid tank */
    private net.minecraftforge.fluids.FluidTank tankCar() {
        tankCarId = null;
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("immersiverailroading")) return null;
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
        AxisAlignedBB box = trackBox();
        for (cam72cam.immersiverailroading.entity.EntityRollingStock st : w.getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
            if (st.isDead() || !(st instanceof cam72cam.immersiverailroading.entity.FreightTank ft) || ft.theTank == null) continue;
            cam72cam.mod.math.Vec3d p = st.getPosition();
            if (!box.contains(new net.minecraft.util.math.Vec3d(p.x, p.y + 0.5, p.z))) continue;
            carName = st.getDefinition() == null ? st.getDefinitionID() : st.getDefinition().name();
            tankCarId = st.getUUID();
            return ft.theTank.internal;
        }
        return null;
    }

    private void tankStation() {
        net.minecraftforge.fluids.FluidTank car = tankCar();
        boolean load = type == MachineType.TANK_LOADER;
        carHere = car != null;
        if (car == null) { carName = ""; carPct = 0; chuteOpen = false; carFull = false; return; }
        carPct = car.getFluidAmount() * 100 / Math.max(1, car.getCapacity());
        int target = load ? new int[]{50, 90, 98}[pv("fill")] : new int[]{50, 10, 0}[pv("fill")];
        carFull = load ? carPct >= target : carPct <= target;
        boolean auto = pv("mode") == 1;
        chuteOpen = (running || auto) && !estopFault && !carFull;
        if (!chuteOpen) { if (running && carFull && !auto) { running = false; clunk(1.0f); } return; }
        if (!draw(1.0)) { chuteOpen = false; return; }
        int rateMb = 100;
        if (load) {
            FluidStack give = fin.drainInternal(rateMb, false);
            if (give == null) { fault = "no supply - fill the fluid inlet"; return; }
            int n = car.fill(give, false);
            if (n <= 0) { fault = "WRONG"; return; }
            FluidStack went = fin.drainInternal(n, true);
            car.fill(went, true);
            billFluid(true, went);
            rate = n * 20 / 10;
        } else {
            FluidStack take = car.drain(rateMb, false);
            if (take == null) { carFull = true; return; }
            int n = fout.fill(take, false);
            if (n <= 0) { fault = "receiving tank full - pipe it away"; return; }
            FluidStack came = car.drain(n, true);
            fout.fill(came, true);
            billFluid(false, came);
            rate = n * 20 / 10;
        }
        if (fault.equals("WRONG") || fault.startsWith("no supply") || fault.startsWith("receiving")) fault = "";
        made += rateMb; madeWindow += 1;
    }

    // =============================================================================================================
    // her ChatGPT list section 13: Pride Industry and IR Extras together
    // =============================================================================================================
    private long now0() { return world.getTotalWorldTime(); }
    private static boolean isMat(ItemStack s, com.dogpound.railmap.factory.ore.ItemMaterial.M m) {
        return !s.isEmpty() && s.getItem() instanceof com.dogpound.railmap.factory.ore.ItemMaterial && s.getMetadata() == m.ordinal();
    }
    /** IR Extras worn-out running gear: defective bogies (meta 10) and wheelsets (meta 1) go to the scrap line (#92) */
    private static boolean isWornSteel(ItemStack s) {
        return !s.isEmpty() && s.getItem() == com.dogpound.railmap.proxy.CommonProxy.spare && (s.getMetadata() == 10 || s.getMetadata() == 1);
    }
    private static int scrapFrom(ItemStack s) { return s.getMetadata() == 10 ? 8 : 4; }
    private static ItemStack mat(com.dogpound.railmap.factory.ore.ItemMaterial.M m, int n) { return com.dogpound.railmap.factory.ore.ItemMaterial.make(m, n); }

    // ---- Sleeper Treatment Plant (#86, chain A): a Bethell full-cell creosoting charge -------------------------------
    private int tieN;                                       // sleepers in the cylinder (0 = empty)
    private int tieCreo() { return (pv("charge") == 1 ? 8 : 4) * (pv("process") == 0 ? 250 : 120); }
    /** 0 idle, 1 vacuum, 2 flood, 3 pressure, 4 drain */
    private int tieStage() {
        if (tieN == 0) return 0;
        double f = progress / (double) Math.max(1, recipeTicks());
        return f < 0.2 ? 1 : f < 0.35 ? 2 : f < 0.8 ? 3 : 4;
    }

    private void tiePlant() {
        if (!running || estopFault || burnt) { if (tieN == 0) progress = 0; return; }   // a charge stays sealed in the cylinder when stopped
        if (tieN == 0) {
            int want = pv("charge") == 1 ? 8 : 4;
            if (countOre("plankWood") < want * 2 || fin.getFluidAmount() < tieCreo()) { progress = 0; return; }
            if (!ItemHandlerHelper.insertItemStacked(output, mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.SLEEPER, want), true).isEmpty()) { fault = "no room - empty the output hatch"; return; }
            if (fault.startsWith("no room")) fault = "";
            takeOre("plankWood", want * 2);
            tieN = want; progress = 0; clunk(0.7f);                              // charge run in, door locked
        }
        int st = tieStage();
        if (!draw(st == 3 ? 1.0 : st == 1 ? 0.7 : 0.35)) return;
        int floodAt = recipeTicks() / 5;
        if (progress == floodAt) {                                               // flood the cylinder from the working tank
            if (fin.getFluidAmount() < tieCreo()) { fault = "no creosote - the working tank ran dry mid-charge"; return; }
            if (fault.startsWith("no creosote")) fault = "";
            fin.drainInternal(tieCreo(), true);
        }
        if (++progress < recipeTicks()) return;
        if (!emit(mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.SLEEPER, tieN))) { progress--; return; }
        if (pv("process") == 1 && fin.getFluid() != null) fin.fillInternal(new FluidStack(fin.getFluid(), tieN * 40), true);   // empty-cell: the kickback recovers some
        tieN = 0; progress = 0; clunk(1.2f);
    }

    // ---- next-48 #23: the plant-wide production planner (factory.Planner) ------------------------------------------
    /** non-empty = the planner holds it ("PLANNER: ..."); transient, so a restart never leaves a plant stuck */
    public String planHold = "";
    public boolean planRunning() { return running; }
    public int planRate() { return rate; }
    public String planFault() { return fault; }
    public int planGet(String key) { return pv(key); }
    /** the planner switches a recipe / pass (rolling mill gap, welding program, assembly recipe) */
    public void planSet(String key, int v) { if (pv(key) != v) { put(key, v); progress = 0; markDirty(); } }

    // ---- the grid: load shedding (#88), power-quality trips (#94), the factory load ledger -------------------------
    private boolean shed;
    private com.dogpound.railmap.industry.IndustryLink.Supply lastSupply;

    /** voltage band %, frequency band Hz, THD %, unbalance %: what sensitive plant rides through (null = not sensitive) */
    private double[] pqLimits() {
        switch (type) {
            case WELDING_CELL: return new double[]{10, 0.5, 8, 2};             // inverter welders and robot drives
            case ELECTROLYSER: return new double[]{10, 1, 15, 3};             // the rectifier's DC bus
            case ASSEMBLY_LINE: return new double[]{10, 1, 8, 3};             // servo drives on every station
            default: return null;
        }
    }

    private String pqBreach() {
        double[] l = pqLimits();
        if (l == null || lastSupply == null || !com.dogpound.railmap.RailMapConfig.industryPowerQuality) return "";
        return lastSupply.breaks(l[0], l[1], l[2], l[3]);
    }

    private void gridTick() {
        if (!formed || type == null) return;
        if (production()) com.dogpound.railmap.industry.IndustryLink.register(this);
        com.dogpound.railmap.industry.IndustryLink.Supply s = null;
        if (electrical()) {
            for (BlockPos m : members)
                if (world.getBlockState(m).getBlock() instanceof com.dogpound.railmap.factory.block.BlockPort bp && bp.kind == com.dogpound.railmap.factory.block.BlockPort.Kind.POWER) {
                    s = com.dogpound.railmap.industry.IndustryLink.supply(world, m);
                    if (s.net >= 0) break;
                }
        } else if (type.power > 0) {
            com.dogpound.railmap.grid.TileGrid g = coupledMotor();
            if (g != null) s = com.dogpound.railmap.industry.IndustryLink.supplyOf(world, g.getPos());
        }
        lastSupply = s;
        shed = s != null && s.live && s.shed;
        String br = pqBreach();
        if (!br.isEmpty() && running && !fault.startsWith("PQ TRIP")) {
            running = false; fault = "PQ TRIP: " + br; clunk(0.6f);
            com.dogpound.railmap.alarm.AlarmManager.get(world).raise(world, "pq:" + pos.toLong(), com.dogpound.railmap.alarm.AlarmManager.Cat.MACHINE, com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR,
                    type.label + " tripped on supply quality: " + br + " - RESET on its panel once the supply settles", pos, true);
            tell("§6⚡ " + type.label + " tripped: " + br + " on its supply");
        }
        double kw = running ? type.power * com.dogpound.railmap.grid.Elec.W_PER_FE_T * load / 1000.0 : 0;
        com.dogpound.railmap.industry.IndustryLink.reportLoad(world, pos, type.label, kw, s, endpointState());
    }

    // ---- the railway sees the plant as an endpoint: contracts, automatic orders, the market ------------------------
    /** the plants that make or use goods (the loaders, pumps and the tower only move them) */
    public boolean production() {
        switch (type()) {
            case BULK_LOADER: case UNLOADER: case OVERHEAD_LOADER: case CAR_DUMPER: case TANK_LOADER: case TANK_UNLOADER: case BOREHOLE_PUMP: case WATER_TOWER: case CRATE_PACKER: return false;
            default: return true;
        }
    }

    @Override public String endpointName() { return type().label; }

    /** what the plant turns out with its current settings (so contracts can be posted before the first batch) */
    private ItemStack product() {
        switch (type) {
            case BLAST_FURNACE: case ARC_FURNACE: return mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.STEEL, 1);
            case COKE_OVEN: return mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.COKE, 1);
            case ROLLING_MILL: return pv("gap") == 2 ? mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.RAIL, 1) : part(0, 1);
            case WIRE_DRAWER: return part(13, 1);
            case SAWMILL: return new ItemStack(net.minecraft.init.Blocks.PLANKS, 1);
            case WINDMILL: return mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.FLOUR, 1);
            case TIE_PLANT: return mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.SLEEPER, 1);
            case WELDING_CELL: { int p = pv("program"); return p == 0 ? part(0, 1) : p == 1 ? part(1, 1) : part(10, 1); }
            case ASSEMBLY_LINE: return AssemblyRecipes.ALL.get(pv("recipe") % AssemblyRecipes.ALL.size()).result();
            default: return ItemStack.EMPTY;
        }
    }

    @Override
    public List<ItemStack> makes() {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack s = output.getStackInSlot(i);
            if (s.isEmpty()) continue;
            boolean merged = false;
            for (ItemStack o : l) if (o.isItemEqual(s)) { o.grow(s.getCount()); merged = true; }
            if (!merged) l.add(s.copy());
        }
        ItemStack p = product();
        boolean have = false;
        for (ItemStack o : l) have |= !p.isEmpty() && o.isItemEqual(p);
        if (!p.isEmpty() && !have) l.add(p);
        return l;
    }

    /** {ore name to count by (null = exact item), the stack to order} for each feed */
    private List<Object[]> feeds() {
        List<Object[]> f = new ArrayList<>();
        ItemStack steel = mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.STEEL, 1), coke = mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.COKE, 1);
        switch (type) {
            case JAW_CRUSHER: f.add(new Object[]{"oreIron", new ItemStack(net.minecraft.init.Blocks.IRON_ORE)}); break;
            case COKE_OVEN: f.add(new Object[]{null, new ItemStack(net.minecraft.init.Items.COAL)}); break;
            case BLAST_FURNACE: f.add(new Object[]{"ingotIron", new ItemStack(net.minecraft.init.Items.IRON_INGOT)}); f.add(new Object[]{"fuelCoke", coke}); break;
            case ROLLING_MILL: case WELDING_CELL: f.add(new Object[]{"ingotSteel", steel}); break;
            case WIRE_DRAWER: { ItemStack cu = firstOre("ingotCopper", 1); f.add(cu.isEmpty() ? new Object[]{"ingotIron", new ItemStack(net.minecraft.init.Items.IRON_INGOT)} : new Object[]{"ingotCopper", cu}); break; }
            case SAWMILL: f.add(new Object[]{"logWood", new ItemStack(net.minecraft.init.Blocks.LOG)}); break;
            case OIL_PRESS: f.add(new Object[]{null, new ItemStack(net.minecraft.init.Items.WHEAT_SEEDS)}); break;
            case WINDMILL: f.add(new Object[]{null, new ItemStack(net.minecraft.init.Items.WHEAT)}); break;
            case ARC_FURNACE: f.add(new Object[]{"scrapSteel", mat(com.dogpound.railmap.factory.ore.ItemMaterial.M.SCRAP, 1)}); f.add(new Object[]{"fuelCoke", coke}); break;
            case TIE_PLANT: f.add(new Object[]{"plankWood", new ItemStack(net.minecraft.init.Blocks.PLANKS)}); break;
            case ASSEMBLY_LINE:
                for (AssemblyRecipes.Part pa : AssemblyRecipes.ALL.get(pv("recipe") % AssemblyRecipes.ALL.size()).parts) {
                    ItemStack w = pa.ore != null ? firstOre(pa.ore, 1) : ItemStack.EMPTY;
                    if (w.isEmpty() && pa.fallback != null) { net.minecraft.item.Item it = net.minecraft.item.Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(pa.fallback.split("@")[0])); if (it != null) w = new ItemStack(it, 1, pa.fallback.contains("@") ? Integer.parseInt(pa.fallback.split("@")[1]) : 0); }
                    if (!w.isEmpty()) f.add(new Object[]{pa.ore, w});
                }
                break;
            default: break;
        }
        return f;
    }

    /** a running plant whose input bin holds under 16 of a feed orders a stack of it (#89) */
    @Override
    public List<ItemStack> wants() {
        List<ItemStack> l = new ArrayList<>();
        if (!formed || !running || burnt || !production()) return l;
        for (Object[] fd : feeds()) {
            ItemStack w = (ItemStack) fd[1];
            int have = 0;
            for (int i = 0; i < input.getSlots(); i++) {
                ItemStack s = input.getStackInSlot(i);
                if (fd[0] != null ? hasOre(s, (String) fd[0]) : s.isItemEqual(w)) have += s.getCount();
            }
            if (have < 16) { ItemStack o = w.copy(); o.setCount(64 - have); l.add(o); }
        }
        return l;
    }

    @Override
    public List<FluidStack> fluidsMade() {
        List<FluidStack> l = new ArrayList<>();
        if (fout.getFluid() != null && fout.getFluidAmount() > 0) l.add(fout.getFluid().copy());
        else if (type == MachineType.REFINERY) l.add(new FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.DIESEL, "diesel", "biodiesel"), 1));
        else if (type == MachineType.COKE_OVEN) l.add(new FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.CREOSOTE, "creosote"), 1));
        else if (type == MachineType.OIL_PRESS) l.add(new FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.PLANT_OIL, "plantoil", "plant_oil", "seed_oil"), 1));
        return l;
    }

    @Override
    public List<FluidStack> fluidsWanted() {
        if (!formed || !running || burnt) return java.util.Collections.emptyList();
        if (type == MachineType.TIE_PLANT && fin.getFluidAmount() < tieCreo() * 2)
            return java.util.Collections.singletonList(new FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.CREOSOTE, "creosote"), fin.getCapacity() - fin.getFluidAmount()));
        if (type == MachineType.REFINERY && fin.getFluidAmount() < 2000)
            return java.util.Collections.singletonList(new FluidStack(com.dogpound.railmap.factory.fluid.ModFluids.prefer(com.dogpound.railmap.factory.fluid.ModFluids.PLANT_OIL, "plantoil", "plant_oil", "seed_oil"), fin.getCapacity() - fin.getFluidAmount()));
        return java.util.Collections.emptyList();
    }

    /** #93: when the batch in the machine comes off (contracts are timed to it) */
    @Override
    public long readyAt() {
        if (!running || firstOutput() >= 0 || progress <= 0) return -1;
        return now0() + Math.max(0, recipeTicks() - progress);
    }
    private int firstOutput() { for (int i = 0; i < output.getSlots(); i++) if (!output.getStackInSlot(i).isEmpty()) return i; return -1; }

    @Override
    public String endpointState() {
        if (burnt) return "BURNT OUT";
        if (!formed) return "NOT BUILT";
        if (!fault.isEmpty()) return fault.length() > 22 ? fault.substring(0, 22).toUpperCase(java.util.Locale.ROOT) : fault.toUpperCase(java.util.Locale.ROOT);
        return running ? "RUNNING" : "STOPPED";
    }

    // ---- freight contracts: what this loader put on / took off which car (#89, #87) -----------------------------------
    private java.util.UUID tankCarId;
    private int bill(boolean loading, Freight.Car car, int moved) {
        if (moved > 0 && car != null && car.id != null && !Freight.lastMoved.isEmpty())
            com.dogpound.railmap.freight.ContractData.get(world).onMoved(world, loading, pos, car.id, new ArrayList<>(Freight.lastMoved));
        return moved;
    }
    private void billFluid(boolean loading, FluidStack moved) {
        if (tankCarId != null && moved != null && moved.amount > 0) com.dogpound.railmap.freight.ContractData.get(world).onFluid(world, loading, pos, tankCarId, moved);
    }
}
