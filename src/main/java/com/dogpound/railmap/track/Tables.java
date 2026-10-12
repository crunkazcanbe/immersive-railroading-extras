package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Immersive Railroading's own turntables and transfer tables (traversers), driven by IR Extras panels (her ChatGPT batch
 * 3 #3 turntables + traversers, #8 swing bridges). IR already really moves the table's track and the stock standing on
 * it; the fork's Pride hooks (TileRail.prideDriven / prideTableSpeed / prideSetTableIndex) let a panel own it: the
 * Large Wrench is refused, and the table moves only at the panel motor's speed (0 = stopped, braked or pinned).
 * Here: finding the table, its footprint, slot / road maths, and what stands on it.
 */
public final class Tables {
    private Tables() {}

    /** the IR table (parent TileRail) a picked block belongs to, or null */
    public static TileRail tableAt(World w, BlockPos p) {
        for (int dy : new int[]{0, -1, 1, -2}) {
            TileRail r = Rails.pieceAt(w, p.getX() + 0.5, p.getY() + dy + 0.5, p.getZ() + 0.5);
            if (r != null && r.info != null && r.info.settings.type.isTable()) return r;
        }
        return null;
    }

    /** the table at a remembered parent position, if loaded */
    public static TileRail get(World w, long parent) {
        if (parent == 0) return null;
        BlockPos p = BlockPos.fromLong(parent);
        if (!w.isBlockLoaded(p)) return null;
        try {
            TileRail r = cam72cam.mod.world.World.get(w).getBlockEntity(new Vec3i(p.getX(), p.getY(), p.getZ()), TileRail.class);
            return r != null && r.info != null && r.info.settings.type.isTable() ? r : null;
        } catch (LinkageError | RuntimeException e) { return null; }
    }

    public static boolean turntable(TileRail t) { return t.info.settings.type == TrackItems.TURNTABLE; }

    /** the footprint: {minX, minZ, maxX, maxZ, y} from the table's own blocks (sign-free, works for both kinds) */
    public static double[] footprint(World w, TileRail t) {
        int reach = Math.max(t.info.settings.length, t.info.settings.transfertableEntrySpacing * Math.max(1, t.info.settings.transfertableEntryCount)) + 3;
        Vec3i c = t.getPos();
        double[] f = {c.x, c.z, c.x + 1, c.z + 1, c.y};
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
        for (int dx = -reach; dx <= reach; dx++)
            for (int dz = -reach; dz <= reach; dz++)
                for (int dy = -1; dy <= 1; dy++) {
                    Vec3i v = new Vec3i(c.x + dx, c.y + dy, c.z + dz);
                    if (!umc.isBlockLoaded(v)) continue;
                    TileRailBase b = umc.getBlockEntity(v, TileRailBase.class);
                    if (b == null) continue;
                    TileRail bp = b instanceof TileRail tr ? tr : b.getParentTile();
                    if (bp == null || !bp.getPos().equals(t.getPos())) continue;
                    f[0] = Math.min(f[0], v.x); f[1] = Math.min(f[1], v.z); f[2] = Math.max(f[2], v.x + 1); f[3] = Math.max(f[3], v.z + 1);
                }
        return f;
    }

    /** turntable pit radius in m (IR's "length" on a turntable) */
    public static double radius(TileRail t) { return t.info.settings.length; }

    public static double[] centre(TileRail t, double[] fp) {
        if (turntable(t)) return new double[]{t.getPos().x + 0.5, t.getPos().z + 0.5};
        return new double[]{(fp[0] + fp[2]) / 2, (fp[1] + fp[3]) / 2};
    }

    // ---- slot maths (pure: the self-test checks these) -----------------------------------------------------------------

    /** step between turntable slots, degrees */
    public static double step(int slots) { return 360.0 / Math.max(1, slots); }

    /** the slot whose bridge end points along {@code roadYaw} (IR "wrong yaw" degrees), table placed at facingAngle */
    public static int slotFor(double roadYaw, double facingAngle, int slots) {
        return Math.floorMod((int) Math.round((roadYaw - facingAngle) / step(slots)), slots);
    }

    /** a turntable bridge has two ends: the slot (slot or its opposite) that is the shorter turn from cur */
    public static int nearestEnd(int slot, int cur, int slots) {
        int opp = (slot + slots / 2) % slots;
        return ring(cur, slot, slots) <= ring(cur, opp, slots) ? slot : opp;
    }

    /** slots between a and b the short way round */
    public static int ring(int a, int b, int slots) { int d = Math.floorMod(b - a, slots); return Math.min(d, slots - d); }

    /** signed short-way angle a -> b, degrees */
    public static double dAngle(double a, double b) { double d = ((b - a) % 360 + 540) % 360 - 180; return d; }

    /** where IR will put the table for this index: degrees (turntable) or metres (transfer table) */
    public static double target(TileRail t, int index) {
        return turntable(t) ? step(t.prideTableSlots()) * index : (double) index * t.info.settings.transfertableEntrySpacing;
    }

    /** how far the table still has to go (degrees or metres, absolute) */
    public static double remaining(TileRail t) {
        double want = target(t, t.prideTableIndex());
        return turntable(t) ? Math.abs(dAngle(t.info.tablePos, want)) : Math.abs(want - t.info.tablePos);
    }

    /** the road's slot / entry for a picked rail at the end of an exit road (-1 = not a road of this table) */
    public static int roadIndex(TileRail t, BlockPos road) {
        if (turntable(t)) {
            Vec3d d = new Vec3d(road.getX() + 0.5 - (t.getPos().x + 0.5), 0, road.getZ() + 0.5 - (t.getPos().z + 0.5));
            if (d.length() < radius(t) * 0.5) return -1;
            return slotFor(VecUtil.toWrongYaw(d), t.info.placementInfo.facing().getAngle(), t.prideTableSlots());
        }
        // IR's own transfer-table click maths (TileRail.clickOnTransferTable), without moving it
        int halfGauge = (int) Math.floor((t.info.settings.gauge.value() * 1.1 + 0.5) / 2);
        int width = t.info.settings.transfertableEntrySpacing * (t.info.settings.transfertableEntryCount - 1) + halfGauge + 2;
        Vec3i main = new Vec3i(-width / 2, 1, t.info.settings.length / 2).rotate(cam72cam.mod.math.Rotation.from(t.info.placementInfo.facing()));
        Vec3i off = new Vec3i(road.getX(), road.getY(), road.getZ()).subtract(t.getPos().subtract(main))
                .rotate(cam72cam.mod.math.Rotation.from(t.info.placementInfo.facing().getOpposite()));
        int idx = Math.round(Math.abs((float) off.x) / Math.max(1, t.info.settings.transfertableEntrySpacing));
        return Math.max(0, Math.min(t.info.settings.transfertableEntryCount - 1, idx));
    }

    /** the panel owns the table this tick and runs its motor at this fraction of IR's table speed */
    public static void drive(World w, TileRail t, double speed, int index) {
        t.prideDrivenTick = w.getTotalWorldTime();
        t.prideTableSpeed = Math.max(0, speed);
        if (index >= 0) t.prideSetTableIndex(index);
    }

    // ---- what is on the table -------------------------------------------------------------------------------------------

    public static final class Load {
        public final List<EntityCoupleableRollingStock> on = new ArrayList<>();
        public double tonnes, offsetPct;
        public boolean moving, fouling;
        public String foulWhy = "";
    }

    public static Load load(World w, TileRail t, double[] fp) {
        Load l = new Load();
        double[] c = centre(t, fp);
        double r = turntable(t) ? radius(t) : Math.max(fp[2] - fp[0], fp[3] - fp[1]) / 2;
        double mx = 0, mz = 0;
        try {
            for (EntityCoupleableRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityCoupleableRollingStock.class)) {
                if (s.isDead()) continue;
                Vec3d p = s.getPosition();
                if (Math.abs(p.y - fp[4]) > 4) continue;
                double half = 6;
                try { half = s.getDefinition().getLength(s.gauge) / 2; } catch (RuntimeException ignored) { }
                boolean inside, near;
                if (turntable(t)) {
                    double d = Math.hypot(p.x - c[0], p.z - c[1]);
                    inside = d <= r;
                    near = d <= r + half;
                    if (inside && d + half > r + 0.4) { l.fouling = true; l.foulWhy = Rails.name(s) + " overhangs the pit edge"; }
                } else {
                    inside = p.x >= fp[0] && p.x <= fp[2] && p.z >= fp[1] && p.z <= fp[3];
                    near = p.x >= fp[0] - half && p.x <= fp[2] + half && p.z >= fp[1] - half && p.z <= fp[3] + half;
                }
                if (!inside && near && !turntable(t)) {
                    // a traverser's deck runs the length of the table: a vehicle half on it is straddling the pit
                    double ox = Math.max(fp[0] - p.x, p.x - fp[2]), oz = Math.max(fp[1] - p.z, p.z - fp[3]);
                    if (Math.max(ox, oz) < half - 0.4) { l.fouling = true; l.foulWhy = Rails.name(s) + " straddles the traverser pit"; }
                }
                if (!inside && near && turntable(t)) { l.fouling = true; l.foulWhy = Rails.name(s) + " is hanging over the pit"; }
                if (!inside) continue;
                double tn = Rails.tonnes(s);
                l.on.add(s);
                l.tonnes += tn;
                mx += (p.x - c[0]) * tn;
                mz += (p.z - c[1]) * tn;
                if (Math.abs(s.getCurrentSpeed().metric()) > 0.5) l.moving = true;
                for (EntityCoupleableRollingStock.CouplerType ct : EntityCoupleableRollingStock.CouplerType.values()) {
                    EntityCoupleableRollingStock o = s.getCoupled(ct);
                    if (o != null) {
                        Vec3d q = o.getPosition();
                        boolean oIn = turntable(t) ? Math.hypot(q.x - c[0], q.z - c[1]) <= r : q.x >= fp[0] && q.x <= fp[2] && q.z >= fp[1] && q.z <= fp[3];
                        if (!oIn) { l.fouling = true; l.foulWhy = Rails.name(s) + " is still coupled to " + Rails.name(o) + " off the table"; }
                    }
                }
            }
        } catch (LinkageError | RuntimeException ignored) { }
        if (l.tonnes > 0) l.offsetPct = Math.min(100, Math.hypot(mx / l.tonnes, mz / l.tonnes) * 100 / Math.max(1, r));
        return l;
    }

    /** the self-test of the pure maths: lines of PASS / FAIL */
    public static List<String> selftest() {
        List<String> o = new ArrayList<>();
        check(o, "slotFor 0 deg, 16 slots = 0", slotFor(0, 0, 16) == 0);
        check(o, "slotFor 90 deg, 16 slots = 4", slotFor(90, 0, 16) == 4);
        check(o, "slotFor -22.5 deg wraps to 15", slotFor(-22.5, 0, 16) == 15);
        check(o, "slotFor with table facing 180: road 180 = slot 0", slotFor(180, 180, 16) == 0);
        check(o, "nearestEnd picks the opposite end when shorter", nearestEnd(12, 3, 16) == 4);
        check(o, "nearestEnd keeps the slot when shorter", nearestEnd(5, 3, 16) == 5);
        check(o, "ring distance wraps", ring(15, 1, 16) == 2);
        check(o, "dAngle 350 -> 10 = +20", Math.abs(dAngle(350, 10) - 20) < 1e-9);
        check(o, "dAngle 10 -> 350 = -20", Math.abs(dAngle(10, 350) + 20) < 1e-9);
        return o;
    }

    static void check(List<String> o, String what, boolean ok) { o.add((ok ? "§aPASS " : "§cFAIL ") + what); }
}
