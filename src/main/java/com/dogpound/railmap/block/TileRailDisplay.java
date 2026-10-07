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
public abstract class TileRailDisplay extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private static final int MIN_RESCAN_GAP = 20;

    private RailNetwork network = RailNetwork.EMPTY;
    /** Last pushed network minus its timestamp; a rescan that changes nothing sends nothing. */
    private NBTTagCompound lastContent;
    private int ticks;
    // Not Long.MIN_VALUE: "now - lastScanTick" overflows to a negative, the debounce never expires and the
    // display never scans at all.
    private long lastScanTick = -MIN_RESCAN_GAP;

    // ---- Settings Console (sneak-right-click with the Signal Wrench); a panel wall uses its controller's options
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);

    @Override public String settingsTitle() { return "Map Screen"; }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        l.add(com.dogpound.railmap.settings.Setting.choice("Map", "labels", "Station and signal names", "Auto shows names once the screen is 2 panels or bigger", "Auto", "Auto", "Always", "Never"));
        l.add(com.dogpound.railmap.settings.Setting.bool("Map", "grid", "Grid lines", "A faint grid behind the map", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Map", "trains", "Show trains", "Live train positions (needs the train feed)", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Map", "chrome", "Compass and scale bar", "North arrow + distance bar in the corner", true));
        l.add(com.dogpound.railmap.settings.Setting.num("Map", "margin", "Margin round the track", "Empty space kept round the network, in blocks", 6, 0, 64, 2, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.choice("Status strip", "strip", "Status strip at the bottom", "Train count + the latest arrival", "Auto", "Auto", "Always", "Never"));
        l.add(com.dogpound.railmap.settings.Setting.bool("Status strip", "stripLast", "Latest arrival / departure", "Right side of the strip", true));
        l.add(com.dogpound.railmap.settings.Setting.num("Screen", "px", "Resolution (pixels per block)", "Higher = sharper text, more work for the graphics card", 64, 32, 128, 16, "px"));
        l.add(com.dogpound.railmap.settings.Setting.num("Screen", "scanEvery", "Rescan the track every", "How often the map re-reads the railway while someone is near", 10, 2, 120, 2, "s"));
        TileRailDisplay c = this == controller() || world == null ? this : controller();
        RailNetwork n = c.network;
        l.add(com.dogpound.railmap.settings.Setting.info("Screen", "Track pieces", String.valueOf(n.nodes.size())));
        l.add(com.dogpound.railmap.settings.Setting.info("Screen", "Signals / stops", n.signals.size() + " / " + n.stops.size()));
        return l;
    }

    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return world != null && !isController() ? controller().cfg : cfg; }

    @Override
    public void onSettingsChanged(String key) {
        TileRailDisplay c = controller();                 // settings() already wrote into the controller's store
        c.markDirty();
        if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(c.getPos()); world.notifyBlockUpdate(c.getPos(), s, s, 3); }
    }

    /** the options the renderer uses (always the controller's) */
    public com.dogpound.railmap.settings.SettingsStore displaySettings() { return controller().cfg; }

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
            int every = Math.max(40, cfg.num("scanEvery") * 20);
            if (ticks % every < 20) rescan(false);
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
        cfg.write(tag);
        if (!network.isEmpty()) tag.setTag("network", network.toNBT());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        cfg.read(tag);
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
