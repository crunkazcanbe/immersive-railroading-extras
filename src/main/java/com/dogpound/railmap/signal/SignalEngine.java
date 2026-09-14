package com.dogpound.railmap.signal;

import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * Automatic Block Signalling, the real rules:
 * <ol>
 *   <li>from each mast, follow the track it faces to the end of the block (an insulated joint
 *       or the next facing signal);</li>
 *   <li>if a train is in that block → <b>Stop</b>;</li>
 *   <li>else look one more block — occupied → <b>Approach</b> (yellow);</li>
 *   <li>else one more — occupied → <b>Advance Approach</b> (flashing yellow);</li>
 *   <li>else <b>Clear</b> (green).</li>
 * </ol>
 * A switch thrown to the diverging route inside the first block turns the aspect into its
 * medium-speed equivalent, which is why two-head masts exist.
 * <p>
 * Runs once a second per dimension and only while the world holds masts, so an unsignalled
 * world costs one map lookup a second.
 */
public final class SignalEngine {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    /** How far a mast will look for the track it governs. */
    private static final int GOVERN_RADIUS = 4;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR) return;
        World world = e.world;
        if (world.getTotalWorldTime() % 20 != 0) return;
        int dim = world.provider.getDimension();
        List<TileSignalMast> masts = SignalRegistry.masts(dim);
        List<TileCrossing> crossings = SignalRegistry.crossings(dim);
        List<TileSignalBridge> bridges = SignalRegistry.bridges(dim);
        if (masts.isEmpty() && crossings.isEmpty() && bridges.isEmpty()) return;

        List<TrainNode> trains = TrainTracker.latest(world);
        try {
            for (TileSignalMast mast : masts) {
                if (mast.isInvalid()) continue;
                if (mast.mode() != TileSignalMast.Mode.AUTO && mast.mode() != TileSignalMast.Mode.CTC) continue;
                update(world, mast, trains);
            }
            for (TileCrossing c : crossings) {
                if (!c.isInvalid()) c.updateFromTrack(trains);
            }
            for (TileSignalBridge b : bridges) {
                if (b.isInvalid() || !b.isController()) continue;
                boolean changed = false;
                for (TileSignalBridge.Head h : b.heads()) {
                    Result r = compute(world, h.rail, b.facing(), trains, 1);
                    if (r.aspect != h.aspect) changed = true;
                    h.aspect = r.aspect;
                    h.clearBlocks = r.clear;
                }
                if (changed) b.sync();
            }
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[RailMap] signal engine pass failed: {}", ex.toString());
        }
    }

    /** What one signal over one piece of track should be showing. */
    public static final class Result {
        public final Aspect aspect;
        public final int clear;
        public final double length;

        Result(Aspect aspect, int clear, double length) {
            this.aspect = aspect;
            this.clear = clear;
            this.length = length;
        }
    }

    /**
     * Walk the track from {@code railPos} away from the signal and turn what is out there into
     * an aspect. {@code heads} decides whether a diverging route can be shown as a medium-speed
     * aspect — a one-head signal has no way to say it.
     */
    public static Result compute(World world, BlockPos railPos, EnumFacing facing, List<TrainNode> trains, int heads) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        Vec3i v = new Vec3i(railPos.getX(), railPos.getY(), railPos.getZ());
        TileRail start = umc.getBlockEntity(v, TileRail.class);
        if (start == null) return new Result(Aspect.DARK, 0, 0);
        Vec3i behind = new Vec3i(v.x - facing.getXOffset(), v.y, v.z - facing.getZOffset());

        TrackFollower follower = new TrackFollower(umc, RailMapConfig.maxBlockLength);
        TrackFollower.Span first = follower.follow(start, behind);
        if (occupied(first, trains)) return new Result(Aspect.STOP, 0, first.length);

        int clear = 1;
        TrackFollower.Span second = follower.continueFrom(first);
        if (second != null && !occupied(second, trains)) {
            clear = 2;
            TrackFollower.Span third = follower.continueFrom(second);
            if (third != null && !occupied(third, trains)) clear = 3;
        }
        Aspect a = Aspect.forClearBlocks(clear, RailMapConfig.threeBlockSignalling);
        if (first.diverging && heads > 1) {
            if (a == Aspect.CLEAR || a == Aspect.ADVANCE_APPROACH) a = Aspect.MEDIUM_CLEAR;
            else if (a == Aspect.APPROACH) a = Aspect.MEDIUM_APPROACH;
        }
        return new Result(a, clear, first.length);
    }

    private void update(World world, TileSignalMast mast, List<TrainNode> trains) {
        Vec3i governed = mast.governedRail();
        if (governed == null) {
            mast.setComputed(Aspect.DARK, 0, 0);
            return;
        }
        Result r = compute(world, new BlockPos(governed.x, governed.y, governed.z),
                mast.facing(), trains, mast.heads());
        if (r.aspect == Aspect.DARK) mast.forgetGovernedRail();
        mast.setComputed(r.aspect, r.clear, r.length);
    }

    /** Any train standing on, or straddling, this span. */
    public static boolean occupied(TrackFollower.Span span, List<TrainNode> trains) {
        if (span == null || trains.isEmpty()) return false;
        for (TrainNode t : trains) {
            if (span.distSqTo(t.x, t.z) <= 2.2 * 2.2) return true;
        }
        return false;
    }

    /**
     * The track piece a mast governs: the nearest {@code TileRail} within
     * {@link #GOVERN_RADIUS}, preferring one in front of the lamps.
     */
    public static BlockPos findGovernedRail(World world, BlockPos mast, EnumFacing facing) {
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -GOVERN_RADIUS; dx <= GOVERN_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -GOVERN_RADIUS; dz <= GOVERN_RADIUS; dz++) {
                    BlockPos p = mast.add(dx, dy, dz);
                    if (!world.isBlockLoaded(p)) continue;
                    Vec3i v = new Vec3i(p.getX(), p.getY(), p.getZ());
                    TileRailBase base = umc.getBlockEntity(v, TileRailBase.class);
                    if (base == null) continue;
                    TileRail rail = base instanceof TileRail tr ? tr : parent(umc, base);
                    if (rail == null || rail.info == null) continue;
                    // Prefer pieces the mast looks at: project the offset onto the facing.
                    double along = dx * facing.getXOffset() + dz * facing.getZOffset();
                    double dist = dx * dx + dy * dy + dz * dz;
                    double score = dist + (along < 0 ? 12 : 0);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new BlockPos(rail.getPos().x, rail.getPos().y, rail.getPos().z);
                    }
                }
            }
        }
        return best;
    }

    private static TileRail parent(cam72cam.mod.world.World umc, TileRailBase base) {
        Vec3i pp = base.getParent();
        if (pp == null || !umc.isBlockLoaded(pp)) return null;
        return base.getParentTile();
    }
}
