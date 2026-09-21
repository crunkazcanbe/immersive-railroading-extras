package com.dogpound.railmap.signal;

import com.dogpound.railmap.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Builds the real structure of a signal gantry out of real blocks.
 *
 * A renderer can draw a tower of any size but you cannot stand on a drawing: collision is only
 * ever asked of the blocks an entity is actually inside, so geometry drawn five blocks above a
 * single base block is scenery. So the gantry now stamps a 3x3 tower of corner legs per side
 * with an open middle to stand in, and a grating walkway across the top between the two.
 *
 * Two rules keep this safe to run automatically:
 *   - it only ever replaces air, so it can never eat track, a build, or anything she placed;
 *   - taking it down only removes gantry frame and deck blocks, nothing else.
 */
public final class GantryTower {

    /** Height of the walkway above the leg's own block. Clears tall stock underneath. */
    public static final int DECK_UP = 6;

    /** Half-width of the walkway: 2 gives a five-wide deck, three walkable with a rail each side. */
    public static final int DECK_HALF = 2;

    /**
     * Half-width of the tower, matched to the deck on purpose. With a narrower tower the deck
     * overhangs it and the whole gantry reads as a table with the outer edge and the handrail
     * hanging in the air, held up by nothing.
     */
    public static final int TOWER_HALF = 2;

    private GantryTower() {}

    // ---- build ---------------------------------------------------------------------------

    /** The 3x3 corner-leg tower for one side of the gantry. */
    public static void buildTower(World w, BlockPos leg, EnumFacing spanAxis) {
        if (w.isRemote) return;
        Block frame = CommonProxy.gantryFrame;
        if (frame == null) return;
        EnumFacing perp = spanAxis.rotateY();
        // Legs stand at the four corners of a 3x3, leaving all four sides open to walk in
        // through and the middle clear to stand in.
        for (int a = -TOWER_HALF; a <= TOWER_HALF; a += TOWER_HALF * 2) {
            for (int p = -TOWER_HALF; p <= TOWER_HALF; p += TOWER_HALF * 2) {
                BlockPos col = leg.offset(spanAxis, a).offset(perp, p);
                // Up to the walkway and no further: a leg poking out above the deck is what
                // made it look like a row of fence posts standing in the sky.
                for (int y = 0; y <= DECK_UP; y++) {
                    place(w, col.up(y), frame);
                }
            }
        }
    }

    /**
     * Where the access ladder runs, as an offset from the leg: hard against one corner post and
     * one step in from the handrail, so climbing out lands you on the deck and not into a rail.
     */
    private static int ladderAlong() { return -TOWER_HALF; }
    private static int ladderAcross() { return -TOWER_HALF + 1; }

    /** A way up. Vanilla ladders, because a ladder is a solved problem. */
    public static void buildLadder(World w, BlockPos leg, EnumFacing spanAxis) {
        if (w.isRemote) return;
        EnumFacing perp = spanAxis.rotateY();
        BlockPos foot = leg.offset(spanAxis, ladderAlong()).offset(perp, ladderAcross());
        // FACING points away from the block the ladder hangs on, which is the corner post.
        IBlockState rung = Blocks.LADDER.getDefaultState()
                .withProperty(net.minecraft.block.BlockLadder.FACING, perp);
        // From ground level, not one block up: isOnLadder() only ever looks at the block the
        // entity's feet are in, so a ladder starting at knee height is a ladder you walk into.
        for (int y = 0; y <= DECK_UP; y++) {
            BlockPos p = foot.up(y);
            if (!w.isBlockLoaded(p)) continue;
            if (!w.getBlockState(p).getBlock().isAir(w.getBlockState(p), w, p)) continue;
            w.setBlockState(p, rung, 2);
        }
    }

    /** The walkway between the two legs. Only the controlling leg lays it, so it is laid once. */
    public static void buildDeck(World w, BlockPos leg, EnumFacing spanAxis, int span) {
        if (w.isRemote || span <= 0) return;
        Block deck = CommonProxy.gantryDeck;
        if (deck == null) return;
        EnumFacing perp = spanAxis.rotateY();
        BlockPos base = leg.up(DECK_UP);
        // The far leg's ladder sits at the same offset from ITS own post, not a mirrored one.
        final int farLadderAlong = span + ladderAlong();
        for (int d = -TOWER_HALF; d <= span + TOWER_HALF; d++) {
            // Five wide: three to walk down plus an edge each side to carry the handrail, so
            // there is room to walk round the head of the gantry instead of a tightrope.
            for (int p = -DECK_HALF; p <= DECK_HALF; p++) {
                // Hatch: leave the square above each ladder open or there is no way out at the top.
                boolean hatch = p == ladderAcross() && (d == ladderAlong() || d == farLadderAlong);
                if (hatch) continue;
                place(w, base.offset(spanAxis, d).offset(perp, p), deck);
            }
            // Handrail along both edges. It is gantry frame rather than vanilla bars so that
            // taking the gantry down removes it again and never touches anything of hers.
            Block rail = CommonProxy.gantryRail;
            if (rail != null) {
                IBlockState along = rail.getDefaultState()
                        .withProperty(BlockGantryRail.FACING, spanAxis);
                placeState(w, base.up(1).offset(spanAxis, d).offset(perp, -DECK_HALF), along);
                placeState(w, base.up(1).offset(spanAxis, d).offset(perp, DECK_HALF), along);
                // Rail the two short ends as well. Railing only the long sides still leaves two
                // open ends to walk straight off, which is exactly what happened.
                if (d == -TOWER_HALF || d == span + TOWER_HALF) {
                    for (int p = -DECK_HALF + 1; p <= DECK_HALF - 1; p++) {
                        if (p == ladderAcross() && (d == ladderAlong() || d == farLadderAlong)) continue;
                        placeState(w, base.up(1).offset(spanAxis, d).offset(perp, p),
                                rail.getDefaultState().withProperty(BlockGantryRail.FACING, perp));
                    }
                }
            }
        }
    }

    // ---- take down -----------------------------------------------------------------------

    /**
     * Remove this leg's tower and the walkway running off it. Only our own blocks go, and the
     * walkway is followed outwards until it runs out rather than assumed to be a fixed length,
     * so it still clears correctly if the span changed.
     */
    public static void clear(World w, BlockPos leg, EnumFacing spanAxis) {
        if (w.isRemote) return;
        EnumFacing perp = spanAxis.rotateY();

        for (int a = -TOWER_HALF; a <= TOWER_HALF; a += TOWER_HALF * 2) {
            for (int p = -TOWER_HALF; p <= TOWER_HALF; p += TOWER_HALF * 2) {
                BlockPos col = leg.offset(spanAxis, a).offset(perp, p);
                for (int y = 0; y <= DECK_UP + 1; y++) remove(w, col.up(y));
            }
        }

        // The access ladder, by exact position only -- never a sweep for ladders in general.
        BlockPos foot = leg.offset(spanAxis, ladderAlong()).offset(perp, ladderAcross());
        for (int y = 0; y <= DECK_UP; y++) {
            BlockPos lp = foot.up(y);
            if (w.isBlockLoaded(lp) && w.getBlockState(lp).getBlock() == Blocks.LADDER) {
                w.setBlockState(lp, Blocks.AIR.getDefaultState(), 2);
            }
        }

        BlockPos base = leg.up(DECK_UP);
        for (EnumFacing dir : new EnumFacing[] { spanAxis, spanAxis.getOpposite() }) {
            for (int d = 0; d <= TileSignalBridge.MAX_SPAN + 2; d++) {
                boolean any = false;
                for (int p = -DECK_HALF; p <= DECK_HALF; p++) {
                    if (remove(w, base.offset(dir, d).offset(perp, p))) any = true;
                    if (remove(w, base.up(1).offset(dir, d).offset(perp, p))) any = true;
                }
                // Stop at the first empty rank so we never walk off into someone else's build.
                if (!any && d > 0) break;
            }
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private static void placeState(World w, BlockPos p, IBlockState st) {
        if (!w.isBlockLoaded(p)) return;
        if (!w.getBlockState(p).getBlock().isAir(w.getBlockState(p), w, p)) return;
        w.setBlockState(p, st, 2);
    }

    private static void place(World w, BlockPos p, Block b) {
        if (!w.isBlockLoaded(p)) return;
        if (!w.getBlockState(p).getBlock().isAir(w.getBlockState(p), w, p)) return;
        w.setBlockState(p, b.getDefaultState(), 2);
    }

    private static boolean remove(World w, BlockPos p) {
        if (!w.isBlockLoaded(p)) return false;
        IBlockState s = w.getBlockState(p);
        Block b = s.getBlock();
        if (b != CommonProxy.gantryFrame && b != CommonProxy.gantryDeck && b != CommonProxy.gantryRail) return false;
        w.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
        return true;
    }
}
