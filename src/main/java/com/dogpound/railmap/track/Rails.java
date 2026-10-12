package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3i;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Small IR lookups the track package shares: which piece of track a point is on, its switch, its geometry, a car's axles. */
public final class Rails {
    private Rails() {}

    /** the IR track piece (TileRail parent) at or just under a point, or null */
    public static TileRail pieceAt(World w, double x, double y, double z) {
        try {
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            for (int dy : new int[]{0, -1, 1}) {
                Vec3i p = new Vec3i(bx, (int) Math.floor(y) + dy, bz);
                if (!umc.isBlockLoaded(p)) continue;
                TileRailBase b = umc.getBlockEntity(p, TileRailBase.class);
                if (b == null) continue;
                TileRail r = b instanceof TileRail tr ? tr : b.getParentTile();
                if (r != null) return r;
            }
        } catch (LinkageError | RuntimeException ignored) { }
        return null;
    }

    public static TileRail pieceAt(World w, BlockPos p) { return pieceAt(w, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5); }

    public static BlockPos pos(TileRail r) { return new BlockPos(r.getPos().x, r.getPos().y, r.getPos().z); }

    /** the switch a rail block belongs to (the switch piece itself or the switch whose diverging leg it is), or null */
    public static TileRail switchOf(World w, BlockPos p) {
        try {
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
            Vec3i v = new Vec3i(p.getX(), p.getY(), p.getZ());
            if (!umc.isBlockLoaded(v)) return null;
            TileRailBase base = umc.getBlockEntity(v, TileRailBase.class);
            if (base == null) return null;
            TileRail sw = base instanceof TileRail tr ? tr : base.getParentTile();
            if (sw == null || sw.info == null || sw.info.settings.type != TrackItems.SWITCH) sw = base.findSwitchParent();
            return sw != null && sw.info != null && sw.info.settings.type == TrackItems.SWITCH ? sw : null;
        } catch (LinkageError | RuntimeException e) { return null; }
    }

    /** curve radius in m for a curve piece (IR's "length" on a curve is its radius), 0 = straight */
    public static int radius(TileRail r) {
        try {
            if (r.info == null) return 0;
            TrackItems t = r.info.settings.type;
            return t == TrackItems.TURN || t == TrackItems.TURN_V2 || t == TrackItems.CUBICPARABOLA ? Math.max(1, r.info.settings.length) : 0;
        } catch (LinkageError | RuntimeException e) { return 0; }
    }

    public static int gaugeMm(TileRail r) {
        try { return r.info == null ? 1435 : (int) Math.round(r.info.settings.gauge.value() * 1000); } catch (LinkageError | RuntimeException e) { return 1435; }
    }

    public static int lengthM(TileRail r) {
        try { return r.info == null ? 10 : Math.max(1, r.info.settings.length); } catch (LinkageError | RuntimeException e) { return 10; }
    }

    /** axle count: bogie vehicles have 4, long locomotives 6, short wagons 2 */
    public static int axles(EntityRollingStock s) {
        double len = 15;
        try { len = s.getDefinition().getLength(s.gauge); } catch (RuntimeException ignored) { }
        if (s instanceof Locomotive) return len > 19 ? 6 : 4;
        return len < 9 ? 2 : 4;
    }

    public static double tonnes(EntityRollingStock s) {
        double rebuilt = 0;
        try { rebuilt = com.dogpound.railmap.asset.Design.massT(s.getUUID()); } catch (LinkageError | RuntimeException ignored) { }   // a rebuild's tanks / batteries / bogies (her 48-next #28)
        try { return Math.max(1, s.getWeight() / 1000.0 + rebuilt); } catch (RuntimeException e) { return 30; }
    }

    public static String name(EntityRollingStock s) {
        if (s.tag != null && !s.tag.isEmpty()) return s.tag;
        return s.getDefinition() == null ? "Train" : s.getDefinition().name();
    }

    public static String xz(BlockPos p) { return p.getX() + "," + p.getZ(); }
}
