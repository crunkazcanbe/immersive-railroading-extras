package com.dogpound.railmap.build6a;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.item.ItemPart;
import com.dogpound.railmap.track.BlockLining;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Tunnel Boring Machine (batch 6a #2): the operator's cabin of a hard-rock TBM, standing on the tunnel invert facing
 * back toward the launch shaft. Each ring it bores one block of the face ahead (5, 7 or 9 m bore) with the cutterhead
 * - time and thrust / torque from the hardness of what is in front of it - sends the spoil back down the conveyor
 * (into whatever inventory touches its launch point, else its own spoil skip), then the segment erector builds a ring
 * of tunnel lining (the mod's Concrete / Brick / Stone Tunnel Lining: walls, haunches, crown, each facing in) from
 * the segment magazine, and the machine shoves itself forward one ring. Steering (left / right, up / down) shifts the
 * line one block every few rings. Disc cutters wear (faster at HIGH thrust, in hard rock); CHANGE CUTTERS re-tips
 * them from Reinforced Steel Plates. It refuses to bore tile entities, track, bedrock and lava, and stops for anyone
 * standing at the face. Power: 4.16-13.8 kV 3φ on a trailing cable back to the launch shaft (the grid cable must
 * touch the block where it was placed); 69 kV burns it out. 12 V for the controls.
 */
public class TileTbm extends DepotTile {
    static final int[] DIA = {7, 5, 9};
    static final String[] LINING = {"concrete", "brick", "stone", ""};
    static final double[] THRUST = {1.0, 0.6, 1.5};

    final B6.Supply mv = new B6.Supply();
    private final Routed items;
    BlockPos feed;
    int phase, progress, need, rings, latDev, vertDev, steerAcc, gradeAcc, lastHard, lastWater;
    float wear, thrustKn, torque, rpm;
    String fault = "", geology = "";
    final List<String> log = new ArrayList<>();
    final List<ItemStack> overflow = new ArrayList<>();

    static final Predicate<ItemStack> PLATE = s -> !s.isEmpty() && s.getItem() instanceof ItemPart && ItemPart.part(s) == ItemPart.Part.PLATE;
    static final Predicate<ItemStack> LINER = s -> !s.isEmpty() && s.getItem() instanceof ItemBlock ib && ib.getBlock() instanceof BlockLining;

    public TileTbm() {
        super(new String[]{"spoil", "segs", "cutters"}, new String[]{"Spoil skip", "Segment magazine", "Cutter store"}, new int[]{27, 27, 9});
        items = new Routed(new net.minecraft.inventory.IInventory[]{store(0), store(1), store(2)}, new boolean[]{true, false, false}, s -> false, LINER, PLATE);
    }

    @Override public String wiredName() { return "Tunnel boring machine"; }
    @Override protected boolean usesMains() { return false; }
    @Override public String gearState() {
        return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · ring %d · %s · cutters %d%% worn · %s", phase == 1 ? "BORING" : phase == 2 ? "RING BUILD" : fault.isEmpty() ? "ready" : fault, rings, mv.what, Math.round(wear), geology);
    }

    EnumFacing dir() { return controlFacing().getOpposite(); }
    int dia() { return DIA[Math.floorMod(ctl.get("s_dia"), DIA.length)]; }
    int half() { return dia() / 2; }
    int clear() { return half() + 2; }
    String lining() { return LINING[Math.floorMod(ctl.get("s_lining"), LINING.length)]; }
    double thrust() { return THRUST[Math.floorMod(ctl.get("s_thrust"), THRUST.length)]; }
    boolean mvOn() { return ctl.get("k_main") == 1 && mv.ok() && ctl.get("m_estop") == 0; }

    /** where the next ring is cut: one block ahead, shifted when the steering says so this ring */
    BlockPos ringBase() {
        EnumFacing d = dir(), l = d.rotateY();
        int s = Math.floorMod(ctl.get("s_steer"), 3), g = Math.floorMod(ctl.get("s_grade"), 3);
        int ls = s != 0 && steerAcc + 1 >= 8 ? (s == 1 ? -1 : 1) : 0;
        int vs = g != 0 && gradeAcc + 1 >= 20 ? (g == 1 ? 1 : -1) : 0;
        return pos.offset(d).offset(l, ls).up(vs);
    }

    /** the ring's cells: {pos, kind} kind 0 = bore out, 1 wall, 2 haunch, 3 crown; lining cells only if lining is on */
    List<Object[]> cells(BlockPos base) {
        EnumFacing l = dir().rotateY();
        int R = half(), H = clear();
        boolean lined = !lining().isEmpty();
        List<Object[]> out = new ArrayList<>();
        for (int li = -(R + 1); li <= R + 1; li++)
            for (int vi = 0; vi <= H; vi++) {
                BlockPos p = base.offset(l, li).up(vi);
                boolean side = Math.abs(li) == R + 1;
                if (!side && vi < H) out.add(new Object[]{p, 0, li});
                else if (!lined) continue;
                else if (side && vi < H) out.add(new Object[]{p, 1, li});
                else if (side) out.add(new Object[]{p, 2, li});
                else out.add(new Object[]{p, 3, li});
            }
        return out;
    }

    /** null = the face can be bored, else why not; sums the hardness into lastHard and names the geology */
    String survey(BlockPos base) {
        if (feed != null && rings >= B6Config.tbmCableM) return "CABLE END - " + B6Config.tbmCableM + " m of trailing cable paid out";
        if (!world.isSideSolid(base.down(), EnumFacing.UP) && !(world.getBlockState(base.down()).getBlock() instanceof BlockLining)) return "VOID UNDER THE INVERT at " + base.down().getX() + "," + base.down().getY() + "," + base.down().getZ();
        double hard = 0;
        int water = 0, n = 0;
        java.util.Map<String, Integer> kinds = new java.util.HashMap<>();
        for (Object[] c : cells(base)) {
            BlockPos p = (BlockPos) c[0];
            if (!world.isBlockLoaded(p)) return "FACE NOT LOADED";
            IBlockState s = world.getBlockState(p);
            Block b = s.getBlock();
            if (world.isAirBlock(p)) continue;
            if (s.getMaterial() == Material.LAVA) return "LAVA AT THE FACE - " + p.getX() + "," + p.getY() + "," + p.getZ();
            if (s.getMaterial() == Material.WATER) { water++; continue; }
            if (b instanceof BlockLining && (int) c[1] > 0) continue;            // already lined (a bore into an old tunnel)
            TileEntity te = world.getTileEntity(p);
            if (te != null) return "OBSTRUCTION: " + b.getLocalizedName() + " at " + p.getX() + "," + p.getY() + "," + p.getZ() + " (will not bore machinery or track)";
            float h = s.getBlockHardness(world, p);
            String id = String.valueOf(b.getRegistryName());
            if (h < 0 || Arrays.asList(B6Config.tbmNeverBore).contains(id)) return "OBSTRUCTION: " + b.getLocalizedName() + " - the cutterhead can't take it";
            hard += Math.max(0.2, h);
            n++;
            kinds.merge(b.getLocalizedName(), 1, Integer::sum);
        }
        AxisAlignedBB face = new AxisAlignedBB(base.offset(dir().rotateY(), -half())).union(new AxisAlignedBB(base.offset(dir().rotateY(), half()).up(clear())));
        if (!world.getEntitiesWithinAABB(EntityPlayer.class, face).isEmpty()) return "PERSON AT THE FACE - stand clear of the cutterhead";
        lastHard = (int) Math.round(hard);
        String main = kinds.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse("open ground");
        geology = n == 0 ? "OPEN GROUND" : main.toUpperCase(Locale.ROOT) + (water > 0 ? " + WATER" : "") + String.format(Locale.ROOT, " %.1f", hard / Math.max(1, n));
        lastWater = water;
        return null;
    }

    /** lining items the ring needs: {walls, haunches, crowns} */
    int[] ringNeeds() { int H = clear(); return lining().isEmpty() ? new int[3] : new int[]{2 * H, 2, 2 * half() + 1}; }

    Predicate<ItemStack> liner(String piece) {
        String want = "tunnel_" + lining() + "_" + piece;
        return s -> LINER.test(s) && ((ItemBlock) s.getItem()).getBlock().getRegistryName() != null && ((ItemBlock) s.getItem()).getBlock().getRegistryName().getPath().equals(want);
    }

    String segShort() {
        if (lining().isEmpty()) return null;
        int[] n = ringNeeds();
        String[] p = {"wall", "haunch", "crown"};
        for (int i = 0; i < 3; i++) { int have = B6.count(store(1), liner(p[i])); if (have < n[i]) return "NO SEGMENTS: needs " + n[i] + " " + lining() + " " + p[i] + "s, has " + have; }
        return null;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "bore" -> { return begin(); }
            case "stop" -> { if (phase != 0) { phase = 0; event("STOPPED by " + who(who)); } return "§eCutterhead stopped"; }
            case "cutchg" -> {
                if (phase != 0) return "§cStop the cutterhead first";
                if (wear < 5) return "§7The disc cutters are as good as new";
                int got = B6.take(store(2), PLATE, 2);
                if (got < 2) { if (got > 0) B6.put(store(2), new ItemStack(com.dogpound.railmap.proxy.CommonProxy.part, got, ItemPart.Part.PLATE.ordinal())); return "§cCutter change needs 2 Reinforced Steel Plates in the cutter store"; }
                event(String.format(Locale.ROOT, "CUTTERS CHANGED at ring %d (were %d%% worn)", rings, Math.round(wear)));
                wear = 0;
                world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 0.9f);
                return "§aDisc cutters re-tipped";
            }
            case "reset" -> { fault = ""; AlarmManager.get(world).clear(akey()); return "§7Fault reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            if (ctl.get(id) == 1) phase = 0;
            return ctl.get(id) == 1 ? "§cE-STOP - cutterhead, thrust rams and erector stopped, MV tripped" : "§aE-stop released";
        }
        if (id.equals("s_dia") && rings > 0 && phase == 0) return null;
        if ((id.equals("s_dia") || id.equals("s_lining")) && phase != 0) return "§cNot while boring";
        return null;
    }

    String begin() {
        if (phase != 0) return "§7Already boring";
        if (!mvOn()) return "§cNo MV: close the main breaker key (" + mv.what + ")";
        if (wear >= 100) return "§cCUTTERS WORN OUT - change the disc cutters";
        String why = survey(ringBase());
        if (why == null) why = segShort();
        if (why == null && feedOut() == null && B6.free(store(0)) < 6) why = "SPOIL FULL - empty the spoil skip or put a conveyor / chest at the launch shaft";
        if (why != null) { fail(why); return "§c" + why; }
        if (lastWater > 0) event("WATER at the face (" + lastWater + " blocks) - grouted as it bores");
        fault = ""; AlarmManager.get(world).clear(akey());
        phase = 1; progress = 0;
        need = (int) Math.max(40, (40 + lastHard * 6) / (thrust() * B6Config.tbmSpeed));
        world.playSound(null, pos, SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.BLOCKS, 1.0f, 0.4f);
        return String.format(Locale.ROOT, "§aBoring ring %d: %s (about %d s)", rings + 1, geology, need / 20);
    }

    void fail(String why) {
        fault = why; phase = 0;
        AlarmManager.Sev sev = why.startsWith("LAVA") || why.startsWith("PERSON") ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR;
        AlarmManager.get(world).raise(world, akey(), AlarmManager.Cat.MACHINE, sev, "TBM ring " + rings + ": " + why, pos, false);
        event("FAULT: " + why);
    }

    String akey() { return "b6tbm:" + (feed == null ? pos : feed).toLong(); }

    void event(String s) {
        log.add(0, s);
        while (log.size() > 6) log.remove(log.size() - 1);
        Recorder.event(world, "TBM ring " + rings + ": " + s);
    }

    /** an inventory touching the launch shaft (the far end of the muck conveyor), or null */
    IItemHandler feedOut() {
        if (feed == null) return null;
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos q = feed.offset(f);
            if (q.equals(pos) || !world.isBlockLoaded(q)) continue;
            TileEntity t = world.getTileEntity(q);
            if (t == null || t == this || t instanceof TileTbm) continue;
            if (t.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite())) return t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f.getOpposite());
        }
        return null;
    }

    void spoil(ItemStack s) {
        IItemHandler out = feedOut();
        if (out != null) s = ItemHandlerHelper.insertItemStacked(out, s, false);
        if (!s.isEmpty()) s = B6.put(store(0), s);
        if (!s.isEmpty()) overflow.add(s);
    }

    @Override
    protected void work() {
        if (feed == null) feed = pos;
        if (age % 20 == 0) {
            long load = phase == 1 ? 1500 : phase == 2 ? 300 : 40;
            if (mv.tick(world, feed, term, wiredName(), ctl.get("k_main") == 1 && powered() ? load : 0, 4000, 14000, 30000, "4.16-13.8 kV 3φ")) put("mv", mv.ok());
            if (phase != 0 && !mvOn()) fail("MV LOST: " + (ctl.get("k_main") == 0 ? "main breaker opened" : mv.what));
            for (ItemStack s : new ArrayList<>(overflow)) { overflow.remove(s); world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s)); }
        }
        boolean cut = phase == 1;
        rpm += ((cut ? 6.5f : 0) - rpm) * 0.05f;
        if (phase == 1) {
            double h = lastHard / Math.max(1.0, cells(ringBase()).size());
            thrustKn = (float) (12000 * thrust() * (0.6 + h / 3));
            torque = (float) (2500 * (0.4 + h / 2.5) * (1 + wear / 200));
            if (++progress >= need) {
                BlockPos base = ringBase();
                String why = survey(base);
                if (why != null) { fail(why); show(); return; }
                excavate(base);
                wear = Math.min(100, wear + (float) (lastHard / 60.0 * thrust() * thrust()));
                phase = 2; progress = 0; need = 60;
                world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0f, 0.5f);
            } else if (progress % 25 == 0) world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.9f, 0.4f);
        } else { thrustKn *= 0.9f; torque *= 0.9f; }
        if (phase == 2 && ++progress >= need) { erectAndShove(); return; }   // may move the machine: nothing after it
        if (age % 10 == 0) show();
    }

    void excavate(BlockPos base) {
        for (Object[] c : cells(base)) {
            BlockPos p = (BlockPos) c[0];
            if (world.isAirBlock(p)) continue;
            IBlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof BlockLining && (int) c[1] > 0) continue;
            if (s.getMaterial() != Material.WATER && s.getMaterial() != Material.LAVA) {
                NonNullList<ItemStack> drops = NonNullList.create();
                s.getBlock().getDrops(drops, world, p, s, 0);
                for (ItemStack d : drops) spoil(d);
            }
            world.setBlockState(p, net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
        }
    }

    /** the erector builds the ring, then the thrust rams shove the machine onto it */
    void erectAndShove() {
        BlockPos base = ringBase();
        int s = Math.floorMod(ctl.get("s_steer"), 3), g = Math.floorMod(ctl.get("s_grade"), 3);
        if (!lining().isEmpty()) {
            String sh = segShort();
            if (sh != null) { fail(sh); show(); return; }
            EnumFacing l = dir().rotateY();
            for (Object[] c : cells(base)) {
                int kind = (int) c[1];
                if (kind == 0) continue;
                BlockPos p = (BlockPos) c[0];
                if (world.getBlockState(p).getBlock() instanceof BlockLining) continue;
                String piece = kind == 1 ? "wall" : kind == 2 ? "haunch" : "crown";
                if (B6.take(store(1), liner(piece), 1) < 1) continue;
                Block b = Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(com.dogpound.railmap.RailMap.MODID, "tunnel_" + lining() + "_" + piece));
                int li = (int) c[2];
                EnumFacing face = kind == 3 ? dir().getOpposite() : li < 0 ? l : l.getOpposite();   // inner face toward the bore
                world.setBlockState(p, b.getDefaultState().withProperty(BlockStationDevice.FACING, face), 3);
            }
        }
        rings++;
        if (s != 0) { if (++steerAcc >= 8) { steerAcc = 0; latDev += s == 1 ? -1 : 1; } } else steerAcc = 0;
        if (g != 0) { if (++gradeAcc >= 20) { gradeAcc = 0; vertDev += g == 1 ? 1 : -1; } } else gradeAcc = 0;
        if (rings % 10 == 0) event(String.format(Locale.ROOT, "RING %d built - %s, cutters %d%%", rings, geology, Math.round(wear)));
        phase = 0;
        boolean cont = ctl.get("k_auto") == 1 && fault.isEmpty();
        show();
        NBTTagCompound tag = B6.lift(world, pos);
        if (tag == null) return;
        tag.setInteger("x", base.getX()); tag.setInteger("y", base.getY()); tag.setInteger("z", base.getZ());
        net.minecraft.world.World w = world;
        w.setBlockState(base, Block.getStateById(tag.getInteger("_state")), 3);
        if (w.getTileEntity(base) instanceof TileTbm n) {
            n.readFromNBT(tag);
            if (cont) n.begin();
            n.markDirty();
        }
    }

    void show() {
        put("pwr", powered()); put("mv", mv.ok()); put("cutl", phase == 1); put("ring", phase == 2);
        put("spoil", feedOut() == null && B6.free(store(0)) < 6); put("seg", segShort() != null); put("wear", wear >= 80);
        put("water", geology.contains("WATER")); put("fault", !fault.isEmpty()); put("obst", fault.startsWith("OBSTRUCTION") || fault.startsWith("PERSON") || fault.startsWith("LAVA"));
        put("cable", fault.startsWith("CABLE")); put("steer", Math.floorMod(ctl.get("s_steer"), 3) != 0 || Math.floorMod(ctl.get("s_grade"), 3) != 0);
        put("autol", ctl.get("k_auto") == 1);
        put("thrustg", Math.round(thrustKn * 100 / 20000)); put("torqueg", Math.round(torque * 100 / 6000)); put("rpmg", Math.round(rpm * 100 / 8));
        put("wearg", Math.round(wear)); put("spoilg", (27 - B6.free(store(0))) * 100 / 27); put("segg", Math.min(100, B6.count(store(1), LINER) * 100 / 128));
        put("mvg", (int) Math.min(100, Math.round(mv.volts * 100 / 13800))); put("progg", phase == 1 ? progress * 100 / Math.max(1, need) : phase == 2 ? 100 : 0);
        text("rings", String.valueOf(rings)); text("chain", String.valueOf(rings));
        String[] l = new String[4];
        switch (Math.floorMod(ctl.get("page"), 4)) {
            case 0 -> {
                l[0] = phase == 1 ? "BORING RING " + (rings + 1) + " " + progress * 100 / Math.max(1, need) + "%" : phase == 2 ? "ERECTING RING " + (rings + 1) : !fault.isEmpty() ? "STOPPED - FAULT" : mvOn() ? "READY TO BORE" : "MV OFF";
                l[1] = cut(fault.isEmpty() ? mv.what.toUpperCase(Locale.ROOT) : fault.toUpperCase(Locale.ROOT), 30);
                l[2] = String.format(Locale.ROOT, "THRUST %d KN TORQUE %d KNM", Math.round(thrustKn), Math.round(torque));
                l[3] = String.format(Locale.ROOT, "HEAD %s RPM CUTTERS %d%%", B6.f1(rpm), Math.round(wear));
            }
            case 1 -> {
                l[0] = "FACE: " + cut(geology, 24);
                l[1] = String.format(Locale.ROOT, "HARDNESS SUM %d", lastHard);
                l[2] = String.format(Locale.ROOT, "BORE %d M CLEAR %d M", dia(), clear());
                l[3] = "LINING " + (lining().isEmpty() ? "OFF - UNLINED" : lining().toUpperCase(Locale.ROOT));
            }
            case 2 -> {
                int[] n = ringNeeds();
                l[0] = String.format(Locale.ROOT, "SEGMENTS %d PER RING", n[0] + n[1] + n[2]);
                l[1] = String.format(Locale.ROOT, "MAGAZINE %d SKIP %d/27", B6.count(store(1), LINER), 27 - B6.free(store(0)));
                l[2] = feedOut() != null ? "CONVEYOR TO SHAFT: RUNNING" : "NO CONVEYOR - SPOIL IN SKIP";
                l[3] = String.format(Locale.ROOT, "CUTTER PLATES %d", B6.count(store(2), PLATE));
            }
            default -> {
                l[0] = String.format(Locale.ROOT, "CHAINAGE %d M  RING %d", rings, rings);
                l[1] = String.format(Locale.ROOT, "DEV H %+d V %+d", latDev, vertDev);
                l[2] = "STEER " + new String[]{"AHEAD", "LEFT", "RIGHT"}[Math.floorMod(ctl.get("s_steer"), 3)] + " " + new String[]{"LEVEL", "UP", "DOWN"}[Math.floorMod(ctl.get("s_grade"), 3)];
                l[3] = String.format(Locale.ROOT, "CABLE %d/%d M", rings, B6Config.tbmCableM);
            }
        }
        for (int i = 0; i < 4; i++) text("line" + (i + 1), l[i]);
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) items : super.getCapability(c, f); }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        mv.write(t, "mv");
        if (feed != null) t.setLong("feed", feed.toLong());
        t.setInteger("phase", phase); t.setInteger("prog", progress); t.setInteger("need", need); t.setInteger("rings", rings);
        t.setInteger("lat", latDev); t.setInteger("vert", vertDev); t.setInteger("sacc", steerAcc); t.setInteger("gacc", gradeAcc); t.setInteger("hard", lastHard);
        t.setFloat("wear", wear); t.setString("fault", fault); t.setString("geo", geology); t.setString("log", String.join("\n", log));
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        mv.read(t, "mv");
        feed = t.hasKey("feed") ? BlockPos.fromLong(t.getLong("feed")) : null;
        phase = t.getInteger("phase"); progress = t.getInteger("prog"); need = t.getInteger("need"); rings = t.getInteger("rings");
        latDev = t.getInteger("lat"); vertDev = t.getInteger("vert"); steerAcc = t.getInteger("sacc"); gradeAcc = t.getInteger("gacc"); lastHard = t.getInteger("hard");
        wear = t.getFloat("wear"); fault = t.getString("fault"); geology = t.getString("geo");
        log.clear();
        for (String s : t.getString("log").split("\n")) if (!s.isEmpty()) log.add(s);
    }

    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(3, 4, 3); }
}
