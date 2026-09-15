package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.scan.NetworkScanner;
import com.dogpound.railmap.server.Viewers;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Anything that shows the map in the world: holds the last scanned {@link RailNetwork}.
 * The server rescans on a throttled interval (only while someone is nearby — an unattended
 * display costs nothing) and on interact; the result is cached here, persisted, and pushed to
 * clients through the vanilla tile update path. While players are near it also keeps them on
 * the live train feed ({@link Viewers}).
 * <p>
 * A multi-block panel wall has one {@link #controller()} that owns the data; the other panels
 * delegate to it.
 */
public abstract class TileRailDisplay extends TileEntity implements ITickable {
    private static final int MIN_RESCAN_GAP = 20;

    private RailNetwork network = RailNetwork.EMPTY;
    /** Last pushed network minus its timestamp; a rescan that changes nothing sends nothing. */
    private NBTTagCompound lastContent;
    private int ticks;
    // Not Long.MIN_VALUE: "now - lastScanTick" overflows to a negative, the debounce never expires and the
    // display never scans at all.
    private long lastScanTick = -MIN_RESCAN_GAP;

    /** The tile that scans and holds the data for this display (itself, unless part of a panel wall). */
    public TileRailDisplay controller() {
        return this;
    }

    public boolean isController() {
        return controller() == this;
    }

    public RailNetwork getNetwork() {
        return controller().network;
    }

    /** Where the map is centred/scanned from. */
    public BlockPos origin() {
        return controller().getPos();
    }

    @Override
    public void update() {
        if (world.isRemote || !isController()) return;
        ticks++;
        if (ticks % 20 == 0 && world.isAnyPlayerWithinRangeAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Viewers.TILE_RANGE)) {
            Viewers.markAround(world, pos);
            if (ticks % RailMapConfig.rescanIntervalTicks < 20) rescan(false);
        }
    }

    public void rescan() {
        rescan(false);
    }

    /** Server only. Debounced so spam-clicking cannot turn the walk into a lag machine; {@code force} skips the debounce (after a switch/station change). */
    public void rescan(boolean force) {
        if (world.isRemote) return;
        long now = world.getTotalWorldTime();
        if (!force && now - lastScanTick < MIN_RESCAN_GAP) return;
        lastScanTick = now;
        long t0 = System.nanoTime();
        RailNetwork fresh;
        try {
            fresh = NetworkScanner.scan(world, pos);
        } catch (Exception e) {
            RailMap.LOG.error("[RailMap] scan failed at {}", pos, e);
            return;
        }
        RailMap.LOG.debug("[RailMap] {}: {} pieces, {} links, {} signals, {} stops in {} ms{}", pos,
                fresh.nodes.size(), fresh.segments.size(), fresh.signals.size(),
                fresh.stops.size(), (System.nanoTime() - t0) / 1_000_000,
                fresh.truncated ? " (budget hit)" : "");
        NBTTagCompound content = fresh.toNBT();
        content.removeTag("time");
        if (content.equals(lastContent)) return; // same track, switches, signals: nothing to tell clients
        lastContent = content;
        network = fresh;
        markDirty();
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3); // -> getUpdatePacket to every tracking client
        if (RailMap.dynmap != null) RailMap.dynmap.network(world, pos, fresh);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote && RailMap.dynmap != null) RailMap.dynmap.remove(world, pos);
    }

    // ---- persistence + sync -------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!network.isEmpty()) tag.setTag("network", network.toNBT());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        network = RailNetwork.fromNBT(tag.getCompoundTag("network"));
        lastContent = null; // force the first post-load scan to push
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
