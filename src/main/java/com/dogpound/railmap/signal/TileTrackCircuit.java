package com.dogpound.railmap.signal;

import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The block that goes under the track.
 * <p>
 * Two jobs, chosen by which block holds it:
 * <ul>
 *   <li><b>Insulated Joint</b> — marks a block boundary. Signalling blocks start and end here,
 *       exactly like the insulated rail joints on a real railroad.</li>
 *   <li><b>Track Circuit</b> — a detector: outputs full redstone while a train is over it, and
 *       for an adjustable distance either side, so you can wire crossings, yard lights or your
 *       own interlocking without touching the automatic signalling at all.</li>
 * </ul>
 * Both are silent and invisible from above once the rail is laid over them.
 */
public class TileTrackCircuit extends TileEntity implements ITickable {
    /** Detection radius in blocks; right-click cycles it. */
    private static final int[] RANGES = { 2, 4, 8, 16, 32, 64 };

    private final boolean joint;
    private int rangeIndex = 1;
    private boolean occupied;
    private int ticks;

    /** Forge needs a no-arg constructor per registered tile type; see the two subclasses. */
    protected TileTrackCircuit(boolean joint) {
        this.joint = joint;
    }

    public boolean isJoint() {
        return joint;
    }

    public int range() {
        return RANGES[rangeIndex];
    }

    public boolean occupied() {
        return occupied;
    }

    public void cycleRange() {
        rangeIndex = (rangeIndex + 1) % RANGES.length;
        markDirty();
        sync();
    }

    @Override
    public void update() {
        if (world.isRemote || ++ticks % 5 != 0) return;
        List<TrainNode> trains = TrainTracker.latest(world);
        double r = range();
        boolean now = false;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        for (TrainNode t : trains) {
            double dx = t.x - cx, dy = t.y - cy, dz = t.z - cz;
            if (Math.abs(dy) > 4) continue;
            if (dx * dx + dz * dz <= r * r) { now = true; break; }
        }
        if (now != occupied) {
            occupied = now;
            markDirty();
            sync();
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
    }

    public int redstoneOutput() {
        return occupied ? 15 : 0;
    }

    private void sync() {
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    // ---- lifecycle: joints register so the track follower can see them --------------------

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote && joint) {
            SignalRegistry.addJoint(world.provider.getDimension(), pos);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote && joint) {
            SignalRegistry.removeJoint(world.provider.getDimension(), pos);
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null && !world.isRemote && joint) {
            SignalRegistry.removeJoint(world.provider.getDimension(), pos);
        }
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setByte("range", (byte) rangeIndex);
        t.setBoolean("occ", occupied);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        rangeIndex = Math.max(0, Math.min(RANGES.length - 1, t.getByte("range")));
        occupied = t.getBoolean("occ");
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

    /** Registered tile type for the joint variant. */
    public static class Joint extends TileTrackCircuit {
        public Joint() { super(true); }
    }

    /** Registered tile type for the detector variant. */
    public static class Circuit extends TileTrackCircuit {
        public Circuit() { super(false); }
    }
}
