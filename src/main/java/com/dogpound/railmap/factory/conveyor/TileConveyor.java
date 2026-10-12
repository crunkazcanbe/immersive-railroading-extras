package com.dogpound.railmap.factory.conveyor;

import com.dogpound.railmap.factory.conveyor.BlockConveyor.Shape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A belt (1 block = 1 m) carrying up to 4 loads along its path - straight, round a curve or up / down a ramp. Items
 * come on at the start from behind (or the feeding side of a curve), merge in at the middle from the sides or from a
 * hopper above, ride along, then go into the next belt / the machine hatch / any inventory it points at, or spill off
 * an open end. Redstone stops the belt itself (the surface stops moving too).
 * <p>
 * Power (requested feature):
 * a line of belts is driven like a real conveyor - an IR Extras Electric Motor (480 V 3-phase, its own starter / VFD)
 * with its shaft end against any belt of the line; every section draws its share (more when loaded). The line has its
 * own controls (START / STOP / SPEED / E-STOP / RESET) for Control Buttons, gauges and the computer: lay Instrument Wire
 * beside a belt and a terminal box pops up on its side and takes that colour.
 */
public class TileConveyor extends TileEntity implements ITickable, com.dogpound.railmap.wire.IWiredMachine {
    public static final int CAPACITY = 4;
    public static final float GAP = 1f / CAPACITY;
    public static final double SURFACE = 5.05 / 16;           // belt top, in blocks

    /** what rides on the belt: stack + position along it (0 = start, 1 = end) */
    public static final class Load {
        public ItemStack stack; public float pos, prev;
        Load(ItemStack s, float p) { stack = s; pos = p; prev = p; }
    }
    public final List<Load> loads = new ArrayList<>();
    private boolean dirty;
    public Shape shape = Shape.STRAIGHT;
    /** belt surface travel (blocks) for the renderer; stands still while the belt is stopped */
    public float travel, travelPrev;
    public boolean stopped;

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockConveyor ? s.getValue(BlockConveyor.FACING) : EnumFacing.NORTH;
    }

    public float speed() {
        float base = world != null && world.getBlockState(pos).getBlock() instanceof BlockConveyor b ? b.tier.speed : 0.05f;
        return base * SPEED_MUL[Math.max(0, Math.min(2, spd))];
    }

    // ---- drive power + line control -------------------------------------------------------------------------------
    private static final float[] SPEED_MUL = {0.6f, 1f, 1.4f};
    private static final String[] SPEED_NAME = {"SLOW", "NORMAL", "FAST"};
    private static final double BUF_MAX = 400;
    /** FE from an Electric Motor whose shaft is on this belt (the driven belt pays for the whole line) */
    public double buf;
    private long drivePos;              // the driven belt feeding this one
    private long driveAt = -1000;        // when its drive last reached us
    private int lineSize = 1;
    /** the line's controls (the same on every belt of it; a command spreads down the whole line at once) */
    public boolean run = true, estop;
    public int spd = 1;
    private long seq;
    private static long SEQ = System.currentTimeMillis() * 1000;
    /** synced: the belt surface is moving */
    public boolean moving;
    private double drawnW;
    private String fault = "";
    private int ctlWire = -1;

    /** watts this section takes running: belt + gearbox, more per load, more fast */
    private double sectionW() {
        double base = world.getBlockState(pos).getBlock() instanceof BlockConveyor b ? (b.tier == BlockConveyor.Tier.EXPRESS ? 2200 : b.tier == BlockConveyor.Tier.FAST ? 1500 : 750) : 750;
        return base * SPEED_MUL[spd] + 250 * loads.size();
    }

    private final net.minecraftforge.energy.IEnergyStorage drive = new net.minecraftforge.energy.IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) { int got = (int) Math.max(0, Math.min(max, BUF_MAX - buf)); if (!sim && got > 0) buf += got; return got; }
        @Override public int extractEnergy(int max, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return (int) buf; }
        @Override public int getMaxEnergyStored() { return (int) BUF_MAX; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    /** every belt joined to this one (any side, up and down ramps too) */
    public static List<TileConveyor> line(net.minecraft.world.World w, BlockPos start, int max) {
        List<TileConveyor> out = new ArrayList<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        q.add(start); seen.add(start.toLong());
        while (!q.isEmpty() && out.size() < max) {
            BlockPos p = q.poll();
            if (!(w.getTileEntity(p) instanceof TileConveyor c)) continue;
            out.add(c);
            for (EnumFacing f : EnumFacing.values()) { BlockPos n = p.offset(f); if (seen.add(n.toLong()) && w.isBlockLoaded(n) && w.getTileEntity(n) instanceof TileConveyor) q.add(n); }
        }
        return out;
    }

    private void lineSet(java.util.function.Consumer<TileConveyor> change) {
        long sq = ++SEQ;
        for (TileConveyor c : line(world, pos, 512)) { change.accept(c); c.seq = sq; c.markDirty(); c.dirty = true; }
    }

    private TileConveyor driveSource() {
        if (world.getTotalWorldTime() - driveAt > 45) return null;
        return world.getTileEntity(BlockPos.fromLong(drivePos)) instanceof TileConveyor c && c.buf > 0 ? c : null;
    }

    /** server, every 10 ticks: the driven belt tells the line; newer commands spread to belts added later; the wire terminal */
    private void lineTick() {
        long now = world.getTotalWorldTime();
        if (buf >= 1 && now % 20 < 10) {
            List<TileConveyor> l = line(world, pos, 512);
            for (TileConveyor c : l) { c.drivePos = pos.toLong(); c.driveAt = now; c.lineSize = l.size(); }
        }
        for (EnumFacing f : EnumFacing.values())
            if (world.getTileEntity(pos.offset(f)) instanceof TileConveyor c && c.seq > seq) { seq = c.seq; run = c.run; spd = c.spd; estop = c.estop; dirty = true; }
        // the terminal box: a signal wire laid beside the belt lands on it by itself; take the wire away and it lets go
        int found = -1;
        for (EnumFacing f : EnumFacing.HORIZONTALS)
            if (world.getTileEntity(pos.offset(f)) instanceof com.dogpound.railmap.wire.TileWire tw)
                for (int c = 0; c < 16; c++) if (tw.has(c) && (found < 0 || c == ctlWire)) found = c;
        if (ctlWire >= 0 && found != ctlWire) {
            boolean still = false;
            for (EnumFacing f : EnumFacing.HORIZONTALS) still |= world.getTileEntity(pos.offset(f)) instanceof com.dogpound.railmap.wire.TileWire tw && tw.has(ctlWire);
            if (!still) setCtlWire(found);
        } else if (ctlWire < 0 && found >= 0) setCtlWire(found);
    }

    /** server, every tick: run the belt if the line is started, nothing stops it and the drive can pay for this section */
    private void powerTick() {
        boolean was = moving;
        boolean want = run && !estop && !world.isBlockPowered(pos);
        TileConveyor src = driveSource();
        double need = sectionW() / com.dogpound.railmap.grid.Elec.W_PER_FE_T;
        moving = want && src != null && src.buf >= need;
        if (moving) { src.buf -= need; drawnW = sectionW(); } else drawnW = 0;
        fault = estop ? "E-STOP" : !want || moving ? "" : src == null ? "no drive - couple a running Electric Motor to the line" : "drive overloaded";
        if (moving != was) dirty = true;
    }

    /** where on the belt (0..1 along it) an item coming in through `face` lands, or -1 if it can't come in that way */
    public float entry(EnumFacing face) {
        EnumFacing f = facing();
        if (face == null) return 0;
        if (face == f) return -1;                                      // the delivery end
        if (face == f.getOpposite() || face == EnumFacing.DOWN) return 0;
        if (shape == Shape.LEFT && face == f.rotateYCCW()) return 0;
        if (shape == Shape.RIGHT && face == f.rotateY()) return 0;
        return 0.5f;                                                   // side merge / hopper above
    }

    public boolean roomAt(float at) {
        if (loads.size() >= CAPACITY || at < 0) return false;
        for (Load l : loads) if (Math.abs(l.pos - at) < GAP) return false;
        return true;
    }

    public ItemStack accept(ItemStack s, EnumFacing face, boolean simulate) {
        float at = entry(face);
        if (s.isEmpty() || !roomAt(at)) return s;
        if (!simulate) { loads.add(new Load(s.copy(), at)); dirty = true; markDirty(); }
        return ItemStack.EMPTY;
    }

    private final Map<EnumFacing, IItemHandler> handlers = new EnumMap<>(EnumFacing.class);
    private IItemHandler handler(EnumFacing side) {
        return handlers.computeIfAbsent(side, sd -> new IItemHandler() {
            @Override public int getSlots() { return 1; }
            @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return accept(stack, sd, simulate); }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return 64; }
        });
    }

    @Override public boolean hasCapability(Capability<?> cap, EnumFacing side) {
        return (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && side != null && side != facing())
                || (cap == net.minecraftforge.energy.CapabilityEnergy.ENERGY && side != null) || super.hasCapability(cap, side);
    }
    @Override @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, EnumFacing side) {
        if (cap == net.minecraftforge.energy.CapabilityEnergy.ENERGY && side != null) return (T) drive;   // an Electric Motor's shaft drives the line here
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && side != null && side != facing()) return (T) handler(side);
        return super.getCapability(cap, side);
    }

    @Override
    public void update() {
        if (world.getTotalWorldTime() % 10 == (pos.hashCode() & 7) || travel == 0) shape = BlockConveyor.shapeOf(world, pos, facing());
        if (!world.isRemote) {
            if (world.getTotalWorldTime() % 10 == (pos.hashCode() & 7)) lineTick();
            powerTick();
        }
        stopped = !moving;
        for (Load l : loads) l.prev = l.pos;
        travelPrev = travel;
        if (!stopped) { travel += speed(); if (travel > 64) { travel -= 64; travelPrev -= 64; } }
        if (world.isRemote) {                                    // the client runs the same motion between syncs
            if (!stopped) advance();
            return;
        }
        if (world.getTotalWorldTime() % 4 == 0) pickUp();
        if (!stopped) advance();
        if (!loads.isEmpty()) {                                  // hand over at the end
            Load head = loads.get(0);
            for (Load l : loads) if (l.pos > head.pos) head = l;
            if (head.pos >= 1f) {
                ItemStack left = handOver(head.stack);
                if (left.isEmpty()) loads.remove(head); else head.stack = left;
                dirty = true; markDirty();
            }
        }
        if (dirty) { dirty = false; IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 2); }
    }

    /** items dropped / thrown onto the belt land where they touch it */
    private void pickUp() {
        AxisAlignedBB box = shape == Shape.UP || shape == Shape.DOWN ? new AxisAlignedBB(pos).expand(0, 0.6, 0) : new AxisAlignedBB(pos).contract(0, 0.5, 0);
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, box)) {
            if (e.isDead || e.getItem().isEmpty()) continue;
            EnumFacing f = facing();
            float at = (float) Math.max(0, Math.min(0.9, (e.posX - pos.getX() - 0.5) * f.getXOffset() + (e.posZ - pos.getZ() - 0.5) * f.getZOffset() + 0.5));
            if (!roomAt(at)) continue;
            loads.add(new Load(e.getItem().copy(), at));
            e.setDead();
            dirty = true; markDirty();
            return;
        }
    }

    /** move every load forward, keeping a gap behind the one in front and stopping at the end */
    private void advance() {
        loads.sort((a, b) -> Float.compare(b.pos, a.pos));
        float limit = 1f, v = speed() * (shape == Shape.LEFT || shape == Shape.RIGHT ? 1.27f : shape == Shape.STRAIGHT ? 1f : 0.71f);
        for (Load l : loads) {
            l.pos = Math.min(limit, l.pos + v);
            limit = l.pos - GAP;
        }
    }

    private ItemStack handOver(ItemStack s) {
        EnumFacing f = facing();
        BlockPos next = BlockConveyor.target(world, pos, f);
        TileEntity t = world.getTileEntity(next);
        if (t instanceof TileConveyor c) {
            if (c.facing() == f.getOpposite() && next.getY() == pos.getY()) return s;   // belts running into each other
            return c.accept(s, f.getOpposite(), false);
        }
        if (t != null && t.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite())) {
            IItemHandler h = t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite());
            return h == null ? s : ItemHandlerHelper.insertItemStacked(h, s, false);
        }
        if (world.isAirBlock(next)) {                            // off the end it goes
            EntityItem e = new EntityItem(world, next.getX() + 0.5, pos.getY() + 0.4, next.getZ() + 0.5, s);
            e.motionX = f.getXOffset() * speed() * 2.4; e.motionY = 0; e.motionZ = f.getZOffset() * speed() * 2.4;
            world.spawnEntity(e);
            return ItemStack.EMPTY;
        }
        return s;
    }

    /** a point on the belt surface (block-relative) at `p` along it: straight, round the curve or up / down the ramp */
    public static double[] point(Shape shape, EnumFacing f, double p) {
        double fx = f.getXOffset(), fz = f.getZOffset();
        switch (shape) {
            case LEFT: case RIGHT: {
                EnumFacing in = shape == Shape.LEFT ? f.rotateYCCW() : f.rotateY();      // the side the items come from
                double ix = in.getXOffset(), iz = in.getZOffset();
                double cx = 0.5 + 0.5 * (ix + fx), cz = 0.5 + 0.5 * (iz + fz);            // the corner the curve goes round
                double a = p * Math.PI / 2;
                return new double[]{cx - fx * 0.5 * Math.cos(a) - ix * 0.5 * Math.sin(a), SURFACE, cz - fz * 0.5 * Math.cos(a) - iz * 0.5 * Math.sin(a)};
            }
            case UP: return new double[]{0.5 + fx * (p - 0.5), SURFACE + p, 0.5 + fz * (p - 0.5)};
            case DOWN: return new double[]{0.5 + fx * (p - 0.5), SURFACE + 1 - p, 0.5 + fz * (p - 0.5)};
            default: return new double[]{0.5 + fx * (p - 0.5), SURFACE, 0.5 + fz * (p - 0.5)};
        }
    }

    public void dropAll() {
        for (Load l : loads) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, l.stack);
        loads.clear();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        NBTTagList list = new NBTTagList();
        for (Load l : loads) { NBTTagCompound c = l.stack.writeToNBT(new NBTTagCompound()); c.setFloat("p", l.pos); list.appendTag(c); }
        t.setTag("loads", list);
        t.setDouble("buf", buf); t.setBoolean("run", run); t.setBoolean("estop", estop); t.setInteger("spd", spd); t.setLong("seq", seq);
        t.setBoolean("moving", moving); t.setInteger("ctlWire", ctlWire); t.setInteger("lineSize", lineSize); t.setString("fault", fault);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        loads.clear();
        NBTTagList list = t.getTagList("loads", 10);
        for (int i = 0; i < list.tagCount(); i++) { NBTTagCompound c = list.getCompoundTagAt(i); loads.add(new Load(new ItemStack(c), c.getFloat("p"))); }
        buf = t.getDouble("buf"); run = !t.hasKey("run") || t.getBoolean("run"); estop = t.getBoolean("estop"); spd = t.hasKey("spd") ? t.getInteger("spd") : 1; seq = t.getLong("seq");
        moving = t.getBoolean("moving"); ctlWire = t.hasKey("ctlWire") ? t.getInteger("ctlWire") : -1; lineSize = Math.max(1, t.getInteger("lineSize")); fault = t.getString("fault");
    }

    // ---- control terminal (IR Extras): gauges, screens, Control Buttons and the computer on its wire ---------------
    private static final com.dogpound.railmap.grid.Panel PANEL = new com.dogpound.railmap.grid.Panel("CONVEYOR", 2.5, 1.5, 13.5, 14.5, -1.0, 0x4A4F54);
    static {
        PANEL.push("start", 3.8, 5.0, com.dogpound.railmap.grid.Panel.GREEN, "START");
        PANEL.push("stop", 6.0, 5.0, com.dogpound.railmap.grid.Panel.RED, "STOP");
        PANEL.sel("speed", 9.2, 8.8, "SPEED", SPEED_NAME);
        PANEL.push("reset", 8.4, 2.9, com.dogpound.railmap.grid.Panel.BLUE, "RESET");
        PANEL.add(new com.dogpound.railmap.grid.Panel.C("estop", com.dogpound.railmap.grid.Panel.T.MUSHROOM, 11.6, 4.0, 2.2, 2.2, com.dogpound.railmap.grid.Panel.RED, "E-STOP"));
    }

    @Override public int ctlWire() { return ctlWire; }
    @Override public void setCtlWire(int c) {
        ctlWire = c; markDirty(); dirty = true;
        if (world != null) world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1), pos.add(1, 1, 1));
    }
    @Override public String wiredName() { return "Conveyor Line (" + lineSize + (lineSize == 1 ? " belt)" : " belts)"); }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return PANEL; }

    @Override
    public String wiredPress(String id, String who) {
        switch (id) {
            case "start":
                if (estop) return "§cE-STOP is in - twist it out (press E-STOP again) and RESET first";
                lineSet(c -> c.run = true);
                return driveSource() == null ? "§eLine STARTED - but no drive: couple a running Electric Motor to a belt" : "§aConveyor line STARTED";
            case "stop": lineSet(c -> c.run = false); return "§cConveyor line STOPPED";
            case "speed": { int n = (spd + 1) % 3; lineSet(c -> c.spd = n); return "§7Belt speed " + SPEED_NAME[n]; }
            case "estop":
                if (!estop) { lineSet(c -> { c.estop = true; c.run = false; }); return "§c§lE-STOP - whole line stopped"; }
                lineSet(c -> c.estop = false); return "§7E-STOP released - press START";
            case "reset": fault = ""; return estop ? "§cE-STOP still in" : "§7Reset";
            default: return "§cNo control '" + id + "' on a conveyor line";
        }
    }
    @Override public int wiredState(String id) { return id.equals("speed") ? spd : id.equals("estop") ? (estop ? 1 : 0) : run ? 1 : 0; }
    @Override public int wiredLamp() { return estop || !fault.isEmpty() ? 3 : moving ? 1 : 2; }

    @Override
    public void wiredRead(com.dogpound.railmap.grid.Reading r, com.dogpound.railmap.grid.BlockInstrument.Inst k) {
        boolean driven = driveSource() != null;
        double w = moving ? sectionW() * lineSize : 0, volts = driven ? 480 : 0, amps = volts > 0 ? w / (Math.sqrt(3) * 480 * 0.82) : 0;
        double rated = 2200 * 1.4 * Math.max(1, lineSize);
        r.lamp = wiredLamp();
        r.status = estop ? "E-STOP" : !fault.isEmpty() ? "FAULT" : moving ? "LIVE" : "STOPPED";
        r.unit = ""; r.value = 0; r.full = 1;
        switch (k) {
            case VOLT: r.value = volts; r.full = 600; r.unit = "V"; break;
            case AMP: r.value = amps; r.full = Math.max(1, rated / (Math.sqrt(3) * 480 * 0.82)); r.unit = "A"; break;
            case WATT: r.value = w; r.full = rated; r.unit = "kW"; break;
            case VAR: r.value = w * 0.7; r.full = rated * 0.75; r.unit = "kvar"; break;
            case PF: r.value = moving ? 0.82 : 0; r.full = 1; r.unit = "PF"; break;
            case FREQ: r.value = moving ? 60 * SPEED_MUL[spd] : 0; r.full = 90; r.unit = "Hz"; break;
            case TEMP: r.value = 25 + (moving ? 20 + 10 * spd : 0); r.full = 120; r.unit = "°C"; break;
            default: break;
        }
        r.line1 = wiredName();
        r.line2 = String.format(java.util.Locale.ROOT, "%s  %s", com.dogpound.railmap.grid.Elec.si(volts, "V"), com.dogpound.railmap.grid.Elec.si(amps, "A"));
        r.line3 = moving ? String.format(java.util.Locale.ROOT, "%.1f m/s  %s", speed() * 20, SPEED_NAME[spd]) : fault.isEmpty() ? "STOPPED" : fault.toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public java.util.Map<String, Object> wiredInfo() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("x", pos.getX()); m.put("y", pos.getY()); m.put("z", pos.getZ());
        m.put("kind", "conveyor"); m.put("name", wiredName());
        m.put("running", run); m.put("moving", moving); m.put("state", estop ? "E-STOP" : moving ? "running" : "stopped");
        m.put("speed", SPEED_NAME[spd]); m.put("mps", Math.round(speed() * 200) / 10.0); m.put("belts", lineSize);
        m.put("items", loads.size()); m.put("kw", Math.round(drawnW * lineSize / 100) / 10.0); m.put("fault", fault);
        m.put("drive", driveSource() != null);
        return m;
    }

    /** right-click with an empty hand: the line's state */
    public String statusLine() {
        return "§7" + wiredName() + " · " + (estop ? "§cE-STOP" : moving ? "§arunning " + SPEED_NAME[spd] : run ? "§estarted, not moving" : "§cstopped")
                + "§7 · drive: " + (driveSource() != null ? "§aElectric Motor" : "§cnone") + "§7" + (fault.isEmpty() ? "" : " · §c" + fault)
                + "§7 · control wire: " + (ctlWire >= 0 ? com.dogpound.railmap.wire.WireNet.NAMES[ctlWire] : "none");
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(0, 1, 0); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager n, SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
