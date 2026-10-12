package com.dogpound.railmap.build6a;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TrackData;
import com.dogpound.railmap.track.TrackData.Metric;
import com.dogpound.railmap.track.TrackData.Seg;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Ballast tamper (batch 6a #3): a Plasser-style 09-3X tamping-lining machine, set down ON the track (its block on top
 * of the rail, facing along the line toward the operator). It works the section either way along the line from it on the real track
 * condition system (track.TrackData, the same top / line / twist / ballast numbers trains wear and the recording car
 * reads): MEASURE records the section's geometry BEFORE, START lowers the tamping heads on each piece in turn - lift
 * the track to the design lift on fresh stone from the ballast hopper, squeeze the tines, line it (TAMP + LINE) and
 * regulate the shoulders (FULL) - then measures AFTER. The screen shows before / after TQI, top, line and twist, and
 * the worst pieces. It will not work with a train in the section. Diesel-hydraulic, battery isolator and engine key
 * like the track layer; 12 V from its battery.
 */
public class TileTamper extends DepotTile {
    static final int[] RANGE = {32, 16, 64, 128};
    static final int[] LIFT = {20, 10, 30, 40};
    static final double[] LIFT_Q = {0.9, 0.7, 1.0, 1.0};
    static final double[] SQUEEZE = {0.8, 0.6, 1.0, 1.2};
    static final double[] SQUEEZE_Q = {0.85, 0.7, 0.95, 1.0};
    static final String[] MODES = {"TAMP", "TAMP+LINE", "FULL"};

    final Diesel diesel = new Diesel();
    int phase, progress, need, index, done;
    long sectionAt;
    final List<BlockPos> pieces = new ArrayList<>();
    /** before / after: {tqi, top, line, twist, tsr pieces} averaged over the section */
    double[] before, after;
    String fault = "", worst = "";
    final List<String> log = new ArrayList<>();
    static final Predicate<ItemStack> GRAVEL = s -> !s.isEmpty() && s.getItem() == Item.getItemFromBlock(Blocks.GRAVEL);
    private final Routed items;

    public TileTamper() {
        super(new String[]{"ballast"}, new String[]{"Ballast hopper"}, new int[]{27});
        items = new Routed(new net.minecraft.inventory.IInventory[]{store(0)}, new boolean[]{false}, GRAVEL);
    }

    @Override public String wiredName() { return "Ballast tamper"; }
    @Override public String gearState() {
        return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · %d pieces · TQI %s -> %s · ballast %d · diesel %d%%", phaseName(), pieces.size(),
                before == null ? "-" : Math.round(before[0]), after == null ? "-" : Math.round(after[0]), B6.count(store(0), GRAVEL), diesel.fuelPct());
    }

    String phaseName() { return phase == 1 ? "MEASURING" : phase == 2 ? "TAMPING" : phase == 3 ? "MEASURING AFTER" : fault.isEmpty() ? "ready" : fault; }
    int range() { return RANGE[Math.floorMod(ctl.get("s_range"), RANGE.length)]; }
    int mode() { return Math.floorMod(ctl.get("s_mode"), 3); }
    boolean heads() { return ctl.get("m_heads") == 1; }

    /** the track pieces of the section: the track the machine stands on (it sits one block above the rail), range m
     *  along it either way, ordered */
    List<BlockPos> scan() {
        EnumFacing along = controlFacing();
        Map<Long, BlockPos> found = new LinkedHashMap<>();
        int R = range();
        for (int k = -R; k <= R; k++)
            for (int lat = -1; lat <= 1; lat++)
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = pos.offset(along, k).offset(along.rotateY(), lat).down(dy);
                    if (!world.isBlockLoaded(p)) continue;
                    TileRail r = Rails.pieceAt(world, p);
                    if (r != null) { BlockPos key = Rails.pos(r); found.putIfAbsent(key.toLong(), key); }
                }
        return new ArrayList<>(found.values());
    }

    /** a train (any IR stock) standing or moving in the section */
    String trainIn() {
        if (pieces.isEmpty()) return null;
        int R = range() + 4;
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
                if (s.isDead()) continue;
                cam72cam.mod.math.Vec3d v = s.getPosition();
                if (pos.distanceSq(v.x, v.y, v.z) < R * R) return Rails.name(s);
            }
        } catch (LinkageError | RuntimeException ignored) { }
        return null;
    }

    /** {tqi, top, line, twist, pieces over the intervention limit} over the section */
    double[] measure() {
        TrackData d = TrackData.get(world);
        double[] m = new double[5];
        int n = 0, worstQ = 101;
        worst = "";
        for (BlockPos p : pieces) {
            Seg s = d.seg(p);
            TileRail r = Rails.pieceAt(world, p);
            if (r != null && s.lengthM == 0) { s.lengthM = Rails.lengthM(r); s.radius = Rails.radius(r); s.gaugeMm = Rails.gaugeMm(r); }
            m[0] += s.tqi(); m[1] += s.get(Metric.TOP); m[2] += s.get(Metric.LINE); m[3] += s.get(Metric.TWIST);
            if (s.band() >= 2) m[4]++;
            if (s.tqi() < worstQ) { worstQ = s.tqi(); worst = String.format(Locale.ROOT, "%d,%d TQI %d %s %s", p.getX(), p.getZ(), s.tqi(), s.worst().label.toUpperCase(Locale.ROOT), com.dogpound.railmap.track.TrackCondition.fmt(s.get(s.worst()))); }
            s.seen = s.v.clone();
            s.inspectedAt = world.getTotalWorldTime();
            s.inspectedBy = "Ballast tamper (measuring trolley)";
            n++;
        }
        d.markDirty();
        if (n > 0) for (int i = 0; i < 4; i++) m[i] /= n;
        return m;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "measure" -> {
                if (phase != 0) return "§7Busy";
                if (!diesel.running) return "§cStart the engine first: " + diesel.why;
                pieces.clear(); pieces.addAll(scan());
                if (pieces.isEmpty()) return "§cNo Immersive Railroading track under the machine - set it down on the track, facing along it";
                phase = 1; progress = 0; need = 40 + pieces.size() * 4; after = null;
                return "§aMeasuring " + pieces.size() + " pieces of track, " + range() + " m either way";
            }
            case "start" -> {
                if (phase != 0) return "§7Busy";
                if (!diesel.running) return "§cStart the engine first: " + diesel.why;
                if (!heads()) return "§cLower the tamping heads (HEADS lever DOWN)";
                if (before == null || pieces.isEmpty()) return "§cMEASURE the section first";
                String t = trainIn();
                if (t != null) { fail("TRAIN IN SECTION: " + t + " - get a possession"); return "§cTrain in the section: " + t; }
                fault = ""; AlarmManager.get(world).clear(akey());
                phase = 2; index = 0; progress = 0; need = pieceTicks(0); done = 0;
                event("WORK STARTED: " + MODES[mode()] + ", lift " + LIFT[Math.floorMod(ctl.get("s_lift"), 4)] + " mm, " + pieces.size() + " pieces");
                return "§aTamping " + pieces.size() + " pieces";
            }
            case "stop" -> { if (phase != 0) { event("STOPPED at piece " + (index + 1) + "/" + pieces.size() + " by " + who(who)); phase = 0; } return "§eStopped"; }
            case "reset" -> { fault = ""; AlarmManager.get(world).clear(akey()); return "§7Fault reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            if (ctl.get(id) == 1) phase = 0;
            return ctl.get(id) == 1 ? "§cE-STOP - heads stopped, engine down" : "§aE-stop released";
        }
        if (id.equals("m_heads") && phase == 2) return "§cStop the work before raising the heads";
        if (id.equals("s_range") && phase != 0) return "§cNot while working";
        return null;
    }

    int pieceTicks(int i) {
        int len = 10;
        if (i < pieces.size()) { Seg s = TrackData.get(world).peek(pieces.get(i)); if (s != null && s.lengthM > 0) len = s.lengthM; }
        return (int) Math.max(20, len * (2 + SQUEEZE[Math.floorMod(ctl.get("s_squeeze"), 4)] * 4));
    }

    /** one piece tamped: geometry pulled back toward new, stone used, the work logged on the piece */
    void tamp(BlockPos p) {
        TrackData d = TrackData.get(world);
        Seg s = d.seg(p);
        int li = Math.floorMod(ctl.get("s_lift"), 4);
        int len = Math.max(1, s.lengthM == 0 ? 10 : s.lengthM);
        int stone = (int) Math.ceil(len * LIFT[li] / 20.0 / 4.0);
        int got = B6.take(store(0), GRAVEL, stone);
        double k = B6Config.tampEffect * SQUEEZE_Q[Math.floorMod(ctl.get("s_squeeze"), 4)] * LIFT_Q[li];
        if (got < stone) k *= 0.5 + 0.5 * got / Math.max(1.0, stone);                 // short of stone: a dry tamp holds less
        for (Metric m : new Metric[]{Metric.TOP, Metric.TWIST}) s.set(m, m.fresh + (s.get(m) - m.fresh) * (1 - k));
        if (mode() >= 1) s.set(Metric.LINE, Metric.LINE.fresh + (s.get(Metric.LINE) - Metric.LINE.fresh) * (1 - k * 0.9));
        s.add(Metric.FOULING, 0.3);                                                   // the tines break a little stone
        if (mode() >= 2) { s.set(Metric.FOULING, Math.max(Metric.FOULING.fresh, s.get(Metric.FOULING) - 2.5)); s.set(Metric.TOP, Math.max(Metric.TOP.fresh, s.get(Metric.TOP) - 0.05)); }
        s.workedAt = world.getTotalWorldTime();
        s.lastWork = MODES[mode()] + " (09-3X tamper)";
        d.markDirty();
        if (got < stone && B6.count(store(0), GRAVEL) == 0) Recorder.event(world, "Ballast tamper: hopper empty - tamping without fresh stone");
    }

    void fail(String why) {
        fault = why; phase = 0;
        AlarmManager.get(world).raise(world, akey(), AlarmManager.Cat.MACHINE, why.startsWith("TRAIN") ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR, "BALLAST TAMPER " + pos.getX() + "," + pos.getZ() + ": " + why, pos, false);
        event("FAULT: " + why);
    }

    String akey() { return "b6tamp:" + pos.toLong(); }

    void event(String s) {
        log.add(0, s);
        while (log.size() > 6) log.remove(log.size() - 1);
        Recorder.event(world, "Ballast tamper " + pos.getX() + "," + pos.getZ() + ": " + s);
    }

    @Override
    protected void work() {
        if (age % 20 == 0) {
            diesel.tick(term, ctl.get("k_batt") == 1, ctl.get("k_engine") == 1, ctl.get("m_estop") == 1, phase == 2);
            if (phase != 0 && !diesel.running) fail("ENGINE STOPPED: " + diesel.why);
            if (phase == 2) { String t = trainIn(); if (t != null) fail("TRAIN ENTERED THE SECTION: " + t + " - work stopped"); }
        }
        if (phase == 1 || phase == 3) {
            if (++progress >= need) {
                double[] m = measure();
                if (phase == 1) { before = m; after = null; event(String.format(Locale.ROOT, "BEFORE: TQI %d top %s line %s twist %s, %d over limit", Math.round(m[0]), B6.f1(m[1]), B6.f1(m[2]), B6.f1(m[3]), (int) m[4])); }
                else {
                    after = m;
                    event(String.format(Locale.ROOT, "AFTER: TQI %d -> %d, top %s -> %s mm", Math.round(before[0]), Math.round(m[0]), B6.f1(before[1]), B6.f1(m[1])));
                    AlarmManager.get(world).clear(akey());
                }
                phase = 0;
            }
        } else if (phase == 2) {
            if (++progress >= need) {
                if (index < pieces.size()) { tamp(pieces.get(index)); done++; }
                index++; progress = 0;
                if (index >= pieces.size()) { phase = 3; need = 40 + pieces.size() * 4; world.playSound(null, pos, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.8f, 0.6f); }
                else need = pieceTicks(index);
            } else if (progress % 8 == 0) world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_HIT, SoundCategory.BLOCKS, 0.7f, 0.6f);
        }
        if (age % 10 == 0) show();
    }

    void show() {
        put("pwr", powered()); put("eng", diesel.running); put("batl", diesel.battery < 25); put("fuell", diesel.fuelPct() < 10);
        put("meas", phase == 1 || phase == 3); put("work", phase == 2); put("done", phase == 0 && after != null && fault.isEmpty());
        put("train", age % 40 == 0 ? trainIn() != null : ctl.get("train") == 1); put("ball", B6.count(store(0), GRAVEL) < 16); put("heads", heads());
        put("fault", !fault.isEmpty()); put("tsr", before != null && before[4] > 0);
        put("fuelg", diesel.fuelPct()); put("batg", Math.round(diesel.battery)); put("rpmg", Math.round(diesel.rpm * 100 / 2200));
        put("progg", phase == 2 ? (index * 100 + progress * 100 / Math.max(1, need)) / Math.max(1, pieces.size()) : phase == 1 || phase == 3 ? progress * 100 / Math.max(1, need) : 0);
        put("ballg", Math.min(100, B6.count(store(0), GRAVEL) * 100 / (27 * 64)));
        put("tqibg", before == null ? 0 : (int) Math.round(before[0])); put("tqiag", after == null ? 0 : (int) Math.round(after[0]));
        put("vibg", phase == 2 ? 70 : 0);
        text("tqib", before == null ? "--" : String.valueOf(Math.round(before[0]))); text("tqia", after == null ? "--" : String.valueOf(Math.round(after[0])));
        text("pieces", String.valueOf(pieces.size()));
        String[] l = new String[4];
        switch (Math.floorMod(ctl.get("page"), 4)) {
            case 0 -> {
                l[0] = phase == 2 ? String.format(Locale.ROOT, "TAMPING %d/%d", Math.min(index + 1, pieces.size()), pieces.size()) : phaseName().toUpperCase(Locale.ROOT);
                l[1] = cut((fault.isEmpty() ? diesel.why : fault).toUpperCase(Locale.ROOT), 30);
                l[2] = String.format(Locale.ROOT, "%s LIFT %dMM SQZ %sS", MODES[mode()], LIFT[Math.floorMod(ctl.get("s_lift"), 4)], B6.f1(SQUEEZE[Math.floorMod(ctl.get("s_squeeze"), 4)]));
                l[3] = String.format(Locale.ROOT, "SECTION +-%dM %d PIECES", range(), pieces.size());
            }
            case 1 -> {
                if (before == null) { l[0] = "NOT MEASURED"; l[1] = "PRESS MEASURE"; l[2] = ""; l[3] = ""; break; }
                l[0] = String.format(Locale.ROOT, "TQI    %3d -> %s", Math.round(before[0]), after == null ? "--" : String.valueOf(Math.round(after[0])));
                l[1] = String.format(Locale.ROOT, "TOP    %s -> %s MM", B6.f1(before[1]), after == null ? "--" : B6.f1(after[1]));
                l[2] = String.format(Locale.ROOT, "LINE   %s -> %s MM", B6.f1(before[2]), after == null ? "--" : B6.f1(after[2]));
                l[3] = String.format(Locale.ROOT, "TWIST  %s -> %s", B6.f1(before[3]), after == null ? "--" : B6.f1(after[3]));
            }
            case 2 -> {
                l[0] = "WORST PIECE";
                l[1] = cut(worst.toUpperCase(Locale.ROOT), 30);
                l[2] = before == null ? "" : String.format(Locale.ROOT, "OVER LIMIT BEFORE %d", (int) before[4]);
                l[3] = after == null ? "" : String.format(Locale.ROOT, "OVER LIMIT AFTER %d", (int) after[4]);
            }
            default -> {
                l[0] = String.format(Locale.ROOT, "BALLAST %d  DIESEL %d L", B6.count(store(0), GRAVEL), diesel.tank.getFluidAmount());
                l[1] = String.format(Locale.ROOT, "ENG %d RPM OIL %s BAR", Math.round(diesel.rpm), B6.f1(diesel.oilBar));
                l[2] = String.format(Locale.ROOT, "BATT %d%% COOL %dC", Math.round(diesel.battery), Math.round(diesel.coolC));
                l[3] = log.isEmpty() ? "" : cut(log.get(0).toUpperCase(Locale.ROOT), 30);
            }
        }
        for (int i = 0; i < 4; i++) text("line" + (i + 1), l[i]);
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) {
        if (c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) items;
        if (c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return (T) diesel.tank;
        return super.getCapability(c, f);
    }

    static int[] pack(double[] a) { if (a == null) return new int[0]; int[] r = new int[a.length]; for (int i = 0; i < a.length; i++) r[i] = (int) Math.round(a[i] * 100); return r; }
    static double[] unpack(int[] a) { if (a.length == 0) return null; double[] r = new double[a.length]; for (int i = 0; i < a.length; i++) r[i] = a[i] / 100.0; return r; }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        diesel.write(t);
        t.setInteger("phase", phase); t.setInteger("prog", progress); t.setInteger("need", need); t.setInteger("idx", index); t.setInteger("done", done);
        net.minecraft.nbt.NBTTagList ps = new net.minecraft.nbt.NBTTagList();
        for (BlockPos p : pieces) ps.appendTag(new net.minecraft.nbt.NBTTagLong(p.toLong()));
        t.setTag("pieces", ps);
        t.setIntArray("before", pack(before)); t.setIntArray("after", pack(after));
        t.setString("fault", fault); t.setString("worst", worst); t.setString("log", String.join("\n", log));
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        diesel.read(t);
        phase = t.getInteger("phase"); progress = t.getInteger("prog"); need = t.getInteger("need"); index = t.getInteger("idx"); done = t.getInteger("done");
        pieces.clear();
        net.minecraft.nbt.NBTTagList ps = t.getTagList("pieces", net.minecraftforge.common.util.Constants.NBT.TAG_LONG);
        for (int i = 0; i < ps.tagCount(); i++) pieces.add(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong) ps.get(i)).getLong()));
        before = unpack(t.getIntArray("before")); after = unpack(t.getIntArray("after"));
        fault = t.getString("fault"); worst = t.getString("worst");
        log.clear();
        for (String s : t.getString("log").split("\n")) if (!s.isEmpty()) log.add(s);
    }
}
