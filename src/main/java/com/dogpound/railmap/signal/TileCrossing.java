package com.dogpound.railmap.signal;

import com.dogpound.railmap.graph.TrainNode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * A grade crossing: crossbuck with alternating flashing lights, an optional gate arm, and the
 * bell. It wakes when a train comes within the approach distance, holds while the train is on
 * the crossing, and clears once it has passed — the same island-and-approach behaviour a real
 * crossing predictor gives you.
 * <p>
 * Redstone works both ways: power it to force the lights and gates down (a maintainer's test
 * switch, or your own detection), and it emits 15 while it is active so you can drive extra
 * lights, sounds or a second gate from it.
 */
public class TileCrossing extends TileEntity implements ITickable {
    /** Seconds the gate takes to fall or rise. */
    private static final int GATE_TICKS = 60;
    /** Held down this long after the last train leaves, so it doesn't flicker. */
    private static final int HOLD_TICKS = 40;
    private static final int[] APPROACH = { 16, 32, 48, 64, 96, 128 };

    public enum Kind { SIGNAL, GATE, CANTILEVER }

    private final Kind kind;
    private int approachIndex = 2;
    private boolean active;
    private int holdTicks;
    /** 0 = arm fully up, GATE_TICKS = fully down. Server-authoritative, smoothed on the client. */
    private int gate;
    private int ticks;

    protected TileCrossing(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() { return kind; }
    public boolean active() { return active; }
    public int approach() { return APPROACH[approachIndex]; }

    /** 0..1, how far the arm has come down. */
    public float gateProgress(float partial) {
        float g = gate;
        if (active && gate < GATE_TICKS) g += partial;
        else if (!active && gate > 0) g -= partial;
        return Math.max(0, Math.min(1, g / GATE_TICKS));
    }

    /** Lights alternate left/right twice a second while active. */
    public boolean lampLeftOn() {
        return active && (world.getTotalWorldTime() % 20) < 10;
    }

    public boolean lampRightOn() {
        return active && (world.getTotalWorldTime() % 20) >= 10;
    }

    public void cycleApproach() {
        approachIndex = (approachIndex + 1) % APPROACH.length;
        markDirty();
        sync();
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockCrossing ? s.getValue(BlockCrossing.FACING) : EnumFacing.NORTH;
    }

    /** Called once a second by {@link SignalEngine} with every train in the dimension. */
    public void updateFromTrack(List<TrainNode> trains) {
        double r = approach();
        boolean near = false;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        for (TrainNode t : trains) {
            double dx = t.x - cx, dy = t.y - cy, dz = t.z - cz;
            if (Math.abs(dy) > 8) continue;
            double d2 = dx * dx + dz * dz;
            if (d2 > r * r) continue;
            // Approaching, or close enough that direction no longer matters (on the island).
            if (d2 < 20 * 20 || !t.moving()) { near = true; break; }
            double len = Math.sqrt(d2);
            double hx = Math.cos(Math.toRadians(t.yaw)), hz = Math.sin(Math.toRadians(t.yaw));
            if ((-dx * hx - dz * hz) / len > 0.2) { near = true; break; }   // heading toward us
        }
        setActive(near || world.getRedstonePowerFromNeighbors(pos) > 0);
    }

    private void setActive(boolean want) {
        if (want) {
            holdTicks = HOLD_TICKS;
            if (!active) {
                active = true;
                markDirty();
                sync();
                world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
            }
        } else if (active && holdTicks <= 0) {
            active = false;
            markDirty();
            sync();
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
    }

    @Override
    public void update() {
        if (world.isRemote) {
            // Client only animates; the server tells it whether the crossing is active.
            if (active && gate < GATE_TICKS) gate++;
            else if (!active && gate > 0) gate--;
            return;
        }
        ticks++;
        if (holdTicks > 0 && --holdTicks == 0) setActive(false);
        if (active && gate < GATE_TICKS) gate++;
        else if (!active && gate > 0) gate--;
        // The bell: once a second while the lights are flashing.
        if (active && kind != Kind.GATE && ticks % 20 == 0) {
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BELL, SoundCategory.BLOCKS, 0.8f, 1.9f);
        }
    }

    public int redstoneOutput() {
        return active ? 15 : 0;
    }

    /** Gate arms are solid while down: nothing drives through a closed crossing. */
    public boolean armBlocks() {
        return kind == Kind.GATE && gate > GATE_TICKS / 2;
    }

    private void sync() {
        if (world == null || world.isRemote) return;
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    // ---- lifecycle -----------------------------------------------------------------------

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) SignalRegistry.addCrossing(world.provider.getDimension(), this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) SignalRegistry.removeCrossing(world.provider.getDimension(), this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null && !world.isRemote) SignalRegistry.removeCrossing(world.provider.getDimension(), this);
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setBoolean("act", active);
        t.setByte("appr", (byte) approachIndex);
        t.setShort("gate", (short) gate);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        active = t.getBoolean("act");
        approachIndex = Math.max(0, Math.min(APPROACH.length - 1, t.getByte("appr")));
        gate = t.getShort("gate");
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

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos).grow(6, 3, 6);
    }

    public static class Signal extends TileCrossing { public Signal() { super(Kind.SIGNAL); } }
    public static class Gate extends TileCrossing { public Gate() { super(Kind.GATE); } }
    public static class Cantilever extends TileCrossing { public Cantilever() { super(Kind.CANTILEVER); } }
}
