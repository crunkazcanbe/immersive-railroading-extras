package com.dogpound.railmap.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The grey box beside the track — and the place you wire redstone into.
 * <p>
 * Link signals to it with the {@link com.dogpound.railmap.item.ItemSignalWrench Signal Wrench}:
 * right-click a signal, then right-click this box. One box can hold as many signals as you
 * like, which is the whole point — a junction's worth of heads on one lever.
 * <p>
 * What the redstone does depends on the box's mode, cycled by right-clicking it bare-handed:
 * <ul>
 *   <li><b>Hold</b> (default) — power the box and every linked signal goes to Stop and stays
 *       there. Cut the power and they go back to reading the track. A dispatcher's lever.</li>
 *   <li><b>Drive</b> — the linked signals stop watching the track and show what the input
 *       strength says: 0 Stop, 1-5 Restricting, 6-10 Approach, 11+ Clear.</li>
 *   <li><b>Report</b> — the box ignores its input and instead <em>emits</em> the state of what
 *       is linked: 15 when every linked signal is clear, 0 when any of them is at Stop. Feed
 *       that into your own contraption.</li>
 * </ul>
 * In every mode the box also emits the report value on its comparator output, so you can read
 * the railroad without giving up the control modes.
 */
public class TileRelayCase extends TileEntity implements ITickable {
    public enum Mode {
        HOLD("Hold at Stop when powered"),
        DRIVE("Aspect follows input strength"),
        REPORT("Output only — reports linked signals");

        public final String label;

        Mode(String label) { this.label = label; }

        public Mode next() {
            Mode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    /** How far a signal may be from its box. Generous: interlockings are spread out. */
    public static final int LINK_RANGE = 64;

    private final List<BlockPos> linked = new ArrayList<>();
    private Mode mode = Mode.HOLD;
    private int lastPower = -1;
    private int ticks;

    public Mode mode() { return mode; }
    public List<BlockPos> linked() { return linked; }
    public int linkCount() { return linked.size(); }

    public void cycleMode() {
        mode = mode.next();
        lastPower = -1;         // re-apply under the new rules on the next tick
        markDirty();
        sync();
    }

    /** Returns a line for the player: linked, already linked (so unlinked), or out of range. */
    public String toggleLink(BlockPos target) {
        if (target.distanceSq(pos) > (double) LINK_RANGE * LINK_RANGE) {
            return "That signal is more than " + LINK_RANGE + " blocks away";
        }
        for (int i = 0; i < linked.size(); i++) {
            if (linked.get(i).equals(target)) {
                linked.remove(i);
                markDirty();
                sync();
                applyNow();
                return "Unlinked — " + linked.size() + " signal(s) left on this box";
            }
        }
        if (!isSignal(target)) { return "That is not a signal"; }
        linked.add(target);
        markDirty();
        sync();
        applyNow();
        return "Linked — this box now controls " + linked.size() + " signal(s)";
    }

    private boolean isSignal(BlockPos p) {
        if (!world.isBlockLoaded(p)) return false;
        TileEntity te = world.getTileEntity(p);
        return te instanceof TileSignalMast || te instanceof TileSignalBridge;
    }

    @Override
    public void update() {
        if (world.isRemote || ++ticks % 4 != 0) return;
        int power = world.getRedstonePowerFromNeighbors(pos);
        if (power != lastPower) {
            lastPower = power;
            apply(power);
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
    }

    private void applyNow() {
        if (world != null && !world.isRemote) {
            lastPower = -1;
        }
    }

    /** Push the box's decision out to every linked signal, dropping any that have gone. */
    private void apply(int power) {
        boolean dropped = false;
        for (int i = linked.size() - 1; i >= 0; i--) {
            BlockPos p = linked.get(i);
            if (!world.isBlockLoaded(p)) continue;      // not loaded is not the same as gone
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileSignalMast mast) {
                switch (mode) {
                    case HOLD   -> mast.setRelayControl(power > 0, null);
                    case DRIVE  -> mast.setRelayControl(false, aspectFor(power));
                    case REPORT -> mast.setRelayControl(false, null);
                }
            } else if (te == null) {
                linked.remove(i);
                dropped = true;
            }
        }
        if (dropped) { markDirty(); sync(); }
    }

    static Aspect aspectFor(int power) {
        if (power <= 0) return Aspect.STOP;
        if (power <= 5) return Aspect.RESTRICTING;
        if (power <= 10) return Aspect.APPROACH;
        return Aspect.CLEAR;
    }

    /** What the box reports: 15 all clear, 0 if anything linked is at Stop, else the worst. */
    public int redstoneOutput() {
        if (linked.isEmpty()) return 0;
        int worst = 15;
        for (BlockPos p : linked) {
            if (!world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            int v = 15;
            if (te instanceof TileSignalMast mast) v = mast.redstoneOutput();
            else if (te instanceof TileSignalBridge b) v = b.redstoneOutput();
            worst = Math.min(worst, v);
        }
        return worst;
    }

    public String statusLine() {
        return "Relay case — " + mode.label + " · " + linked.size() + " signal(s) linked"
                + (linked.isEmpty() ? " · link one with the Signal Wrench" : "");
    }

    private void sync() {
        if (world == null || world.isRemote) return;
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    /** Release everything this box was holding, so breaking it never strands a signal at red. */
    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            for (BlockPos p : linked) {
                if (!world.isBlockLoaded(p)) continue;
                TileEntity te = world.getTileEntity(p);
                if (te instanceof TileSignalMast mast) mast.setRelayControl(false, null);
            }
        }
        super.invalidate();
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        long[] arr = new long[linked.size()];
        for (int i = 0; i < linked.size(); i++) arr[i] = linked.get(i).toLong();
        // NBT has no long[] in 1.12, so store as pairs of ints.
        int[] packed = new int[arr.length * 2];
        for (int i = 0; i < arr.length; i++) {
            packed[i * 2] = (int) (arr[i] >> 32);
            packed[i * 2 + 1] = (int) arr[i];
        }
        t.setIntArray("links", packed);
        t.setByte("mode", (byte) mode.ordinal());
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        linked.clear();
        int[] packed = t.getIntArray("links");
        for (int i = 0; i + 1 < packed.length; i += 2) {
            long v = ((long) packed[i] << 32) | (packed[i + 1] & 0xffffffffL);
            linked.add(BlockPos.fromLong(v));
        }
        Mode[] all = Mode.values();
        int m = t.getByte("mode");
        mode = m >= 0 && m < all.length ? all[m] : Mode.HOLD;
        lastPower = -1;
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
}
