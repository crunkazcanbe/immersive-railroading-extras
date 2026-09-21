package com.dogpound.railmap.signal;

import cam72cam.mod.math.Vec3i;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A signal bridge — the gantry trains ride under.
 * <p>
 * Place one post each side of the track, both facing the oncoming train. They look across for
 * each other and, when they match up, build the span between them on their own: no wand, no
 * second item, no placing the middle. The post on the viewer's left owns the structure.
 * <p>
 * The span then finds every track running underneath it and hangs a working head over each
 * one. Each head is its own signal: it walks its own track, sees its own block, and shows its
 * own aspect, exactly like a mast standing beside that rail would.
 */
public class TileSignalBridge extends TileEntity implements ITickable, IScalable {
    /** Widest span the two posts will bridge. */
    public static final int MAX_SPAN = 24;
    /** How far under the span we look for track. */
    private static final int DROP = 6;

    /** One head hanging over one track. */
    public static final class Head {
        /** Blocks from the controller post, along the span. */
        public final double along;
        public final BlockPos rail;
        public Aspect aspect = Aspect.DARK;
        public int clearBlocks;

        Head(double along, BlockPos rail) {
            this.along = along;
            this.rail = rail;
        }
    }

    private BlockPos partner;
    private boolean controller;
    private int span;                 // blocks between the two posts, 0 = not paired
    private long rescanAt = Long.MIN_VALUE;
    private final List<Head> heads = new ArrayList<>();
    /** visual height/size, dialled with the wrench so the gantry clears any IR train */
    private float scale = 1f;

    @Override public float scale() { return scale; }

    @Override
    public void setScale(float sc) {
        scale = IScalable.clamp(sc);
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }
    private int ticks;

    public boolean isController() { return controller && span > 0; }
    public boolean isPaired()     { return span > 0; }
    public int span()             { return span; }
    public List<Head> heads()     { return heads; }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockSignalBridge ? s.getValue(BlockSignalBridge.FACING) : EnumFacing.NORTH;
    }

    /** The direction the span runs: across the track, i.e. the viewer's right. */
    public EnumFacing spanAxis() {
        return facing().rotateYCCW();
    }

    /** Span the real structure was last built for; -1 until it has been built at all. */
    private int builtSpan = -1;

    public void markDirtyLayout() {
        rescanAt = Long.MIN_VALUE;
    }

    @Override
    public void update() {
        if (world.isRemote || ++ticks % 20 != 0) return;
        long now = world.getTotalWorldTime();
        if (rescanAt == Long.MIN_VALUE || now >= rescanAt) {
            rescanAt = now + 100;   // re-measure every 5s; cheap and self-healing
            relink();
        }
    }

    /** Find the post opposite, decide who owns the span, and hang a head over each track. */
    private void relink() {
        EnumFacing f = facing();
        EnumFacing right = spanAxis();
        BlockPos found = null;
        int dist = 0;
        for (int i = 1; i <= MAX_SPAN; i++) {
            BlockPos p = pos.offset(right, i);
            if (!world.isBlockLoaded(p)) break;
            IBlockState s = world.getBlockState(p);
            if (!(s.getBlock() instanceof BlockSignalBridge)) continue;
            // The far post must look the same way, or it belongs to a different gantry.
            if (s.getValue(BlockSignalBridge.FACING) != f) continue;
            found = p;
            dist = i;
            break;
        }
        boolean wasPaired = span > 0;
        if (found == null) {
            // Maybe we are the far post and the owner is behind us.
            BlockPos owner = null;
            for (int i = 1; i <= MAX_SPAN; i++) {
                BlockPos p = pos.offset(right.getOpposite(), i);
                if (!world.isBlockLoaded(p)) break;
                IBlockState s = world.getBlockState(p);
                if (s.getBlock() instanceof BlockSignalBridge && s.getValue(BlockSignalBridge.FACING) == f) {
                    owner = p;
                    dist = i;
                    break;
                }
            }
            partner = owner;
            controller = false;
            span = owner == null ? 0 : dist;
            heads.clear();
        } else {
            partner = found;
            controller = true;
            span = dist;
            findHeads();
        }
        if (wasPaired != (span > 0) || controller) sync();
        updateStructure();
    }

    /**
     * Put up (or take down) the real tower and walkway. A drawn gantry is scenery -- you cannot
     * stand on a renderer -- so a paired gantry builds itself out of real blocks. Rebuilt only
     * when the span actually changes, since relink() runs every few seconds.
     */
    private void updateStructure() {
        // Deliberately builds NOTHING any more.
        //
        // An earlier version stamped a 5x5 tower and a walkway automatically. She did not
        // want that: it did not fit the mod, and it took the building away from the player.
        // The frame / walkway / handrail / antenna are ordinary placeable blocks instead, so
        // a tower is something you make yourself and decorate how you like.
        //
        // Teardown is still wired up in BlockSignalBridge.breakBlock so that any tower left
        // over from that older version still comes down cleanly when the gantry is broken.
        if (world == null || world.isRemote) return;
        builtSpan = span;
    }

    /**
     * Every track crossing under the span gets a head. We step along the span a block at a
     * time, look down for an IR rail, and keep the first hit of each run — two neighbouring
     * columns of the same track must not become two signals.
     */
    private void findHeads() {
        heads.clear();
        EnumFacing right = spanAxis();
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        BlockPos lastRail = null;
        for (int i = 1; i < span; i++) {
            BlockPos col = pos.offset(right, i);
            BlockPos rail = null;
            for (int dy = 0; dy <= DROP && rail == null; dy++) {
                BlockPos p = col.down(dy);
                if (!world.isBlockLoaded(p)) break;
                Vec3i v = new Vec3i(p.getX(), p.getY(), p.getZ());
                cam72cam.immersiverailroading.tile.TileRailBase base =
                        umc.getBlockEntity(v, cam72cam.immersiverailroading.tile.TileRailBase.class);
                if (base == null) continue;
                cam72cam.immersiverailroading.tile.TileRail tr =
                        base instanceof cam72cam.immersiverailroading.tile.TileRail t ? t : parent(umc, base);
                if (tr != null && tr.info != null) {
                    rail = new BlockPos(tr.getPos().x, tr.getPos().y, tr.getPos().z);
                }
            }
            if (rail == null) { lastRail = null; continue; }
            if (rail.equals(lastRail)) continue;    // same piece as the column before: one head only
            lastRail = rail;
            heads.add(new Head(i, rail));
            if (heads.size() >= 8) break;
        }
    }

    private static cam72cam.immersiverailroading.tile.TileRail parent(
            cam72cam.mod.world.World umc, cam72cam.immersiverailroading.tile.TileRailBase base) {
        Vec3i pp = base.getParent();
        if (pp == null || !umc.isBlockLoaded(pp)) return null;
        return base.getParentTile();
    }

    /** The strongest (most permissive) aspect on the gantry, for the redstone output. */
    public Aspect bestAspect() {
        Aspect best = null;
        for (Head h : heads) {
            if (best == null || h.aspect.ordinal() < best.ordinal()) best = h.aspect;
        }
        return best == null ? Aspect.DARK : best;
    }

    public int redstoneOutput() {
        switch (bestAspect()) {
            case CLEAR: return 15;
            case ADVANCE_APPROACH: return 12;
            case APPROACH_MEDIUM:
            case MEDIUM_CLEAR: return 10;
            case APPROACH:
            case MEDIUM_APPROACH: return 7;
            case RESTRICTING: return 3;
            default: return 0;
        }
    }

    public void sync() {
        if (world == null || world.isRemote) return;
        markDirty();
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
    }

    // ---- lifecycle -----------------------------------------------------------------------

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) SignalRegistry.addBridge(world.provider.getDimension(), this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) SignalRegistry.removeBridge(world.provider.getDimension(), this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null && !world.isRemote) SignalRegistry.removeBridge(world.provider.getDimension(), this);
    }

    // ---- persistence: the aspects ride along so the client can draw the lamps --------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setInteger("span", span);
        t.setBoolean("ctrl", controller);
        int[] along = new int[heads.size()];
        byte[] asp = new byte[heads.size()];
        for (int i = 0; i < heads.size(); i++) {
            along[i] = (int) Math.round(heads.get(i).along);
            asp[i] = (byte) heads.get(i).aspect.ordinal();
        }
        t.setIntArray("h_at", along);
        t.setByteArray("h_as", asp);
        if (scale != 1f) t.setFloat("scale", scale);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        span = t.getInteger("span");
        controller = t.getBoolean("ctrl");
        scale = t.hasKey("scale") ? IScalable.clamp(t.getFloat("scale")) : 1f;
        int[] along = t.getIntArray("h_at");
        byte[] asp = t.getByteArray("h_as");
        heads.clear();
        Aspect[] all = Aspect.values();
        for (int i = 0; i < along.length; i++) {
            Head h = new Head(along[i], pos);
            if (i < asp.length) {
                int a = asp[i] & 0xff;
                h.aspect = a < all.length ? all[a] : Aspect.DARK;
            }
            heads.add(h);
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    /** The controller draws the whole span, so its render box has to cover it. */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        double h = 8.0 * Math.max(1f, scale);   // a scaled-up gantry rises higher, so grow with it
        if (world == null || !isController()) return new AxisAlignedBB(pos).grow(1, h, 1);
        BlockPos far = pos.offset(spanAxis(), Math.max(1, span));
        return new AxisAlignedBB(pos).union(new AxisAlignedBB(far)).grow(2, h, 2);
    }

    public String statusLine() {
        if (!isPaired()) {
            return "Signal bridge — place a matching post across the track (up to " + MAX_SPAN + " blocks), same facing";
        }
        if (!controller) return "Signal bridge — far post, span " + span + " blocks";
        return "Signal bridge — span " + span + " blocks · " + heads.size()
                + (heads.size() == 1 ? " track" : " tracks") + " underneath · showing " + bestAspect().label;
    }
}
