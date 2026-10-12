package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The railway fault-injection laboratory's faults (her ChatGPT list: "a particularly good addition"). Faults exist only
 * while a Fault Lab console is in TEST MODE, only on gear inside its test zone, and all of them are cleared the moment
 * test mode ends or the console is broken - so an experiment can't leak into a live railway. Each fault acts through
 * the real code path, not a fake indication:
 * <ul>
 *   <li>TC_DROP - the track circuit's relay drops: it reads OCCUPIED (TileTrackCircuit);</li>
 *   <li>LAMP - a signal's lamp fails: the head goes dark, its aspect is STOP, lamp proving holds the one in rear;</li>
 *   <li>POINT_STUCK - points jam mid-stroke: they won't move and lose detection (Interlocking + RouteCheck);</li>
 *   <li>FALSE_OCC - a phantom vehicle on the rail: every detector, signal and crossing sees a train there;</li>
 *   <li>RADIO_CUT - a radio mast goes off air: trains it covered lose their link (RadioNet);</li>
 *   <li>AXLE_HEAD - an axle counter head fails: its sections go DISTURBED;</li>
 *   <li>POWER_CUT - the 12 V is cut to every piece of gear in the zone (backup-power / fail-safe test).</li>
 * </ul>
 */
public final class FaultLab {
    private FaultLab() {}

    public enum Kind {
        TC_DROP("Drop track circuit"), LAMP("Signal lamp failure"), POINT_STUCK("Stuck points"), FALSE_OCC("False occupancy"),
        RADIO_CUT("Radio link cut"), AXLE_HEAD("Axle head failure"), POWER_CUT("12 V supply cut (zone)"),
        // ---- power commissioning test bench (her 48-next #19): faults in the protection chain of a grid IED / breaker
        CT_ERROR("CT ratio error (sensor)"), VT_FAIL("VT fuse failure (sensor)"), TRIP_WIRING("Trip circuit open (wiring)"),
        BREAKER_STUCK("Breaker mechanism stuck"), RELAY_SETTING("Wrong relay setting");
        public final String label;
        Kind(String label) { this.label = label; }
        public static Kind byName(String s) { for (Kind k : values()) if (k.name().equalsIgnoreCase(s) || k.name().replace("_", "").equalsIgnoreCase(s)) return k; return null; }
    }

    public static boolean any(World w) { return !w.isRemote && !SafetyData.get(w).faults.isEmpty(); }

    /** the bench faults in now, by position (grid protection reads these without a world: grid.Bench) */
    private static final Map<Long, EnumSet<Kind>> BENCH = new HashMap<>();
    public static boolean bench(Kind k, long pos) { EnumSet<Kind> s = BENCH.get(pos); return s != null && s.contains(k); }
    public static boolean benchAny() { return !BENCH.isEmpty(); }
    public static Set<Long> benchAt(Kind k) { Set<Long> o = new HashSet<>(); for (Map.Entry<Long, EnumSet<Kind>> e : BENCH.entrySet()) if (e.getValue().contains(k)) o.add(e.getKey()); return o; }
    static boolean benchKind(Kind k) { return k.ordinal() >= Kind.CT_ERROR.ordinal(); }

    /** rebuild the bench map from the saved faults (after a load; inject / clear keep it current) */
    public static void syncBench(World w) {
        BENCH.clear();
        for (SafetyData.Fault f : SafetyData.get(w).faults) if (benchKind(f.kind)) BENCH.computeIfAbsent(f.pos, x -> EnumSet.noneOf(Kind.class)).add(f.kind);
    }

    /** a fault of this kind at (or, for points and phantoms, right next to) pos */
    public static boolean has(World w, Kind k, BlockPos p) {
        if (w == null || w.isRemote) return false;
        List<SafetyData.Fault> fs = SafetyData.get(w).faults;
        if (fs.isEmpty()) return false;
        long key = p.toLong();
        for (SafetyData.Fault f : fs) {
            if (f.kind != k) continue;
            if (f.pos == key) return true;
            if (k == Kind.POINT_STUCK) { BlockPos q = BlockPos.fromLong(f.pos); if (Math.abs(q.getX() - p.getX()) <= 1 && Math.abs(q.getY() - p.getY()) <= 1 && Math.abs(q.getZ() - p.getZ()) <= 1) return true; }
        }
        return false;
    }

    /** the switch at pos (or the switch the picked rail belongs to) is jammed by the lab */
    public static boolean stuck(World w, BlockPos p) { return has(w, Kind.POINT_STUCK, p) || has(w, Kind.POINT_STUCK, switchOf(w, p)); }

    /** the trains a detector sees, with the lab's phantom vehicles added */
    public static List<TrainNode> withPhantoms(World w, List<TrainNode> trains) {
        if (w == null || w.isRemote) return trains;
        List<SafetyData.Fault> fs = SafetyData.get(w).faults;
        if (fs.isEmpty()) return trains;
        List<TrainNode> out = null;
        int i = 0;
        for (SafetyData.Fault f : fs) {
            if (f.kind != Kind.FALSE_OCC) continue;
            if (out == null) out = new ArrayList<>(trains);
            BlockPos p = BlockPos.fromLong(f.pos);
            out.add(new TrainNode(-1000 - i++, p.getX() + 0.5f, p.getY(), p.getZ() + 0.5f, 0, 0, TrainNode.Kind.OTHER, "PHANTOM (fault lab)", "", -1, 0, 1, true, 0, 0, 0, ""));
        }
        return out == null ? trains : out;
    }

    /** the switch a picked rail block belongs to (or the pos itself) */
    static BlockPos switchOf(World w, BlockPos p) {
        try {
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
            TileRailBase base = umc.getBlockEntity(new Vec3i(p.getX(), p.getY(), p.getZ()), TileRailBase.class);
            if (base == null) return p;
            TileRail sw = base instanceof TileRail tr ? tr : base.getParentTile();
            if (sw == null || sw.info == null || sw.info.settings.type != TrackItems.SWITCH) sw = base.findSwitchParent();
            if (sw == null) return p;
            return new BlockPos(sw.getPos().x, sw.getPos().y, sw.getPos().z);
        } catch (LinkageError | RuntimeException e) { return p; }
    }

    /** what a fault of this kind can be put on */
    static String check(World w, Kind k, BlockPos p) {
        TileEntity t = w.getTileEntity(p);
        switch (k) {
            case TC_DROP: return t instanceof TileTrackCircuit tc && !tc.isJoint() ? null : "a TRACK CIRCUIT (not a joint)";
            case LAMP: return t instanceof TileSignalMast ? null : "a SIGNAL";
            case POINT_STUCK: { BlockPos s = switchOf(w, p); return com.dogpound.railmap.auto.Interlocking.stateAt(w, s) != cam72cam.immersiverailroading.library.SwitchState.NONE ? null : "a SWITCH (points)"; }
            case FALSE_OCC: return com.dogpound.railmap.scan.SwitchControl.isRail(w, p) ? null : "a RAIL";
            case RADIO_CUT: return t instanceof TileRadioMast ? null : "a RADIO MAST";
            case AXLE_HEAD: return t instanceof TileAxleHead ? null : "an AXLE COUNTER HEAD";
            case POWER_CUT: return null;
            case CT_ERROR: case VT_FAIL: case RELAY_SETTING: return gridKind(w, p) == com.dogpound.railmap.grid.GridKind.IED ? null : "a PROTECTION IED (grid)";
            case TRIP_WIRING: return gridKind(w, p) == com.dogpound.railmap.grid.GridKind.IED || gridKind(w, p) == com.dogpound.railmap.grid.GridKind.BREAKER ? null : "a PROTECTION IED or CIRCUIT BREAKER";
            case BREAKER_STUCK: return gridKind(w, p) == com.dogpound.railmap.grid.GridKind.BREAKER ? null : "a CIRCUIT BREAKER";
            default: return "?";
        }
    }

    private static com.dogpound.railmap.grid.GridKind gridKind(World w, BlockPos p) {
        com.dogpound.railmap.grid.GridData.Node n = com.dogpound.railmap.grid.GridData.get(w).node(p);
        return n == null ? null : n.kind;
    }

    /** put a fault in. Only from a lab in test mode, only inside its zone. Returns the message for the player. */
    public static String inject(World w, Kind k, BlockPos p, String who) {
        SafetyData d = SafetyData.get(w);
        if (!d.testMode || d.labPos == 0) return "§cFault injection refused - a Fault Lab console must be in TEST MODE first";
        BlockPos lab = BlockPos.fromLong(d.labPos);
        if (k != Kind.POWER_CUT && lab.distanceSq(p) > (double) d.labRadius * d.labRadius) return "§cRefused - that is outside the lab's test zone (" + d.labRadius + " blocks around the console)";
        String need = check(w, k, p);
        if (need != null) return "§c" + k.label + " needs " + need + " as its target";
        if (k == Kind.POINT_STUCK) p = switchOf(w, p);
        for (SafetyData.Fault f : d.faults) if (f.kind == k && f.pos == p.toLong()) return "§7That fault is already in";
        SafetyData.Fault f = new SafetyData.Fault();
        f.kind = k; f.pos = p.toLong(); f.since = w.getTotalWorldTime(); f.what = k.label + " @ " + p.getX() + "," + p.getY() + "," + p.getZ();
        d.faults.add(f);
        if (benchKind(k)) syncBench(w);
        if (k == Kind.POWER_CUT) cutPower(w, true);
        d.history(w, p, null, null, "LAB: " + k.label + " injected by " + who);
        Recorder.event(w, "FAULT LAB: injected " + f.what + " (" + who + ")");
        d.markDirty();
        return "§6Injected: " + f.what;
    }

    public static void clearAll(World w, String why) {
        SafetyData d = SafetyData.get(w);
        if (d.faults.isEmpty()) return;
        boolean power = false;
        for (SafetyData.Fault f : d.faults) {
            if (f.kind == Kind.POWER_CUT) power = true;
            d.history(w, BlockPos.fromLong(f.pos), null, null, "LAB: " + f.kind.label + " cleared (" + why + ")");
        }
        d.faults.clear();
        BENCH.clear();
        if (power) cutPower(w, false);
        Recorder.event(w, "FAULT LAB: all faults cleared (" + why + ")");
        d.markDirty();
    }

    public static void clear(World w, Kind k, BlockPos p) {
        SafetyData d = SafetyData.get(w);
        d.faults.removeIf(f -> f.kind == k && (p == null || f.pos == p.toLong()));
        if (benchKind(k)) syncBench(w);
        if (k == Kind.POWER_CUT) cutPower(w, false);
        d.markDirty();
    }

    /** the 12 V cut: every terminal of gear inside the test zone (memory only - a restart never leaves gear dead) */
    static void cutPower(World w, boolean cut) {
        SafetyData d = SafetyData.get(w);
        BlockPos lab = BlockPos.fromLong(d.labPos);
        double r2 = (double) d.labRadius * d.labRadius;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(t instanceof IWiredGear g) || t instanceof TileFaultLab) continue;
            if (cut && t.getPos().distanceSq(lab) > r2) continue;
            if (g.term().testCut != cut) { g.term().testCut = cut; g.gearChanged(); }
        }
    }
}
