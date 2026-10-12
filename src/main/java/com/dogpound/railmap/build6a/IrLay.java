package com.dogpound.railmap.build6a;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackDirection;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.track.BuilderBase;
import cam72cam.immersiverailroading.track.IIterableTrack;
import cam72cam.immersiverailroading.track.TrackBase;
import cam72cam.immersiverailroading.track.VecYPR;
import cam72cam.immersiverailroading.util.PlacementInfo;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * Lays one panel of Immersive Railroading track from code, the way IR's own Track Blueprint does (RailInfo + its
 * builder), without a player: a straight or a curved panel of the given length starting at a point and heading, with
 * the real IR material costs (rails, sleepers, bed) and where the next panel starts (the railhead).
 */
public final class IrLay {
    private IrLay() {}

    public static final class Plan {
        public BuilderBase builder;
        public int rails, ties, bed;
        /** the next panel's start: its block, the point inside that block (x, z 0..1) and its heading (IR yaw) */
        public BlockPos next;
        public double nextFx, nextFz;
        public float nextYaw;
        public String err;
        public double gaugeM;
    }

    /**
     * @param curve 0 = curve LEFT, 1 = straight, 2 = curve RIGHT
     * @param radius curve radius in m (IR's TURN length)
     */
    public static Plan plan(World w, BlockPos at, double fx, double fz, float yaw, double gaugeM, int length, int curve, int radius, boolean ballast) {
        Plan p = new Plan();
        try {
            cam72cam.mod.world.World world = cam72cam.mod.world.World.get(w);
            Gauge g = Gauge.from(gaugeM);
            p.gaugeM = g.value();
            boolean turn = curve != 1;
            float degrees = turn ? (float) Math.toDegrees((double) length / Math.max(8, radius)) : 0;
            cam72cam.mod.item.ItemStack bp = new cam72cam.mod.item.ItemStack(IRItems.ITEM_TRACK_BLUEPRINT, 1);
            RailSettings s = RailSettings.from(bp).with(b -> {
                b.gauge = g;
                b.type = turn ? TrackItems.TURN : TrackItems.STRAIGHT;
                b.pickType = b.type;
                b.length = turn ? Math.max(8, radius) : Math.max(2, length);
                b.degrees = turn ? Math.max(1f, degrees) : 90;
                b.direction = curve == 0 ? TrackDirection.LEFT : TrackDirection.RIGHT;
                b.railBed = ballast ? new cam72cam.mod.item.ItemStack(new net.minecraft.item.ItemStack(Blocks.GRAVEL)) : cam72cam.mod.item.ItemStack.EMPTY;
                b.isPreview = false;
            });
            PlacementInfo pi = new PlacementInfo(new Vec3d(fx, 0, fz), curve == 0 ? TrackDirection.LEFT : TrackDirection.RIGHT, ((yaw % 360) + 360) % 360, null);
            RailInfo info = new RailInfo(s, pi, pi, SwitchState.NONE, SwitchState.NONE, 0);
            Vec3i v = new Vec3i(at.getX(), at.getY(), at.getZ());
            BuilderBase b = info.getBuilder(world, v);
            if (b == null) { p.err = "IR has no builder for that panel"; return p; }
            p.builder = b;
            p.rails = b.costRails();
            p.ties = b.costTies();
            p.bed = ballast ? b.costBed() : 0;
            if (!(b instanceof IIterableTrack it)) { p.err = "panel has no path"; return p; }
            List<VecYPR> path = it.getPath(0.25);
            if (path.size() < 2) { p.err = "panel too short"; return p; }
            VecYPR end = path.get(path.size() - 1), pre = path.get(path.size() - 2);
            double dx = end.x - pre.x, dz = end.z - pre.z;
            float endYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            double ex = at.getX() + fx + end.x + Math.sin(Math.toRadians(endYaw)), ez = at.getZ() + fz + end.z + Math.cos(Math.toRadians(endYaw));
            p.next = new BlockPos(Math.floor(ex), at.getY(), Math.floor(ez));
            p.nextFx = ex - Math.floor(ex);
            p.nextFz = ez - Math.floor(ez);
            p.nextYaw = ((endYaw % 360) + 360) % 360;
        } catch (LinkageError | RuntimeException e) {
            p.err = "IR refused the panel: " + e.getClass().getSimpleName();
        }
        return p;
    }

    /**
     * Would every track block go down? The machine standing at `self` is in the way of the first block, so that one is
     * only checked for solid ground under it. Returns null when it can, else what is in the way.
     */
    public static String blocked(World w, Plan p, BlockPos self) {
        try {
            for (TrackBase t : p.builder.getTracksForBuild()) {
                Vec3i q = t.getPos();
                boolean me = q.x == self.getX() && q.y == self.getY() && q.z == self.getZ();
                if (me) { if (!t.isDownSolid(true)) return "NO FORMATION under the railhead"; continue; }
                if (!t.isDownSolid(true)) return String.format(Locale.ROOT, "NO FORMATION at %d,%d,%d - build the embankment first", q.x, q.y - 1, q.z);
                if (!t.canPlaceTrack()) return String.format(Locale.ROOT, "OBSTRUCTION at %d,%d,%d (%s)", q.x, q.y, q.z, w.getBlockState(new BlockPos(q.x, q.y, q.z)).getBlock().getLocalizedName());
            }
        } catch (LinkageError | RuntimeException e) { return "IR check failed: " + e.getClass().getSimpleName(); }
        return null;
    }

    public static boolean build(Plan p) {
        try { p.builder.build(); return true; } catch (LinkageError | RuntimeException e) { return false; }
    }
}
