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
public class TileTrackCircuit extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int trainsSeen, holdLeft, pulseLeft;
    private boolean raw;

    @Override public String settingsTitle() { return joint ? "Insulated Joint" : "Track Circuit"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { markDirty(); sync(); }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        cfg.put("range", Integer.toString(range()));
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String d = "Detection", o = "Output", st = "Status";
        if (joint) {
            l.add(com.dogpound.railmap.settings.Setting.info(st, "What this does", "Marks a signalling block boundary (no options needed)"));
            return l;
        }
        l.add(com.dogpound.railmap.settings.Setting.num(d, "range", "Detection radius", "Right-click also steps this", 4, 1, 128, 1, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num(d, "height", "Height window", "", 4, 1, 32, 1, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.choice(d, "which", "Trains that count", "", "Any", "Any", "Locomotives only", "Moving only", "Stopped only"));
        l.add(com.dogpound.railmap.settings.Setting.num(d, "minKmh", "Faster than", "Only trains above this speed (0 = all)", 0, 0, 200, 5, "km/h"));
        l.add(com.dogpound.railmap.settings.Setting.num(d, "check", "Check every", "", 5, 1, 40, 1, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.choice(o, "mode", "Output", "", "While occupied", "While occupied", "Inverted (on when clear)", "Pulse when a train enters", "Pulse when it leaves"));
        l.add(com.dogpound.railmap.settings.Setting.num(o, "level", "Strength", "", 15, 1, 15, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.num(o, "hold", "Stay on after the train", "Keep the output on this long after it clears", 0, 0, 30, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.num(o, "pulse", "Pulse length", "", 10, 2, 100, 2, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Occupied", occupied ? "YES" : "no"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Trains detected", String.valueOf(trainsSeen)));
        return l;
    }
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
        String v = cfg == null ? "" : cfg.text("range");
        try { if (!v.isEmpty()) return Integer.parseInt(v); } catch (NumberFormatException ignored) { }
        return RANGES[rangeIndex];
    }

    public boolean occupied() {
        return occupied;
    }

    public void cycleRange() {
        int cur = range(), next = RANGES[0];
        for (int r : RANGES) if (r > cur) { next = r; break; }
        for (int i = 0; i < RANGES.length; i++) if (RANGES[i] == next) rangeIndex = i;
        cfg.put("range", Integer.toString(next));
        markDirty();
        sync();
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        if (pulseLeft > 0 && --pulseLeft == 0) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        int every = Math.max(1, cfg.num("check"));
        if (++ticks % every != 0) return;
        List<TrainNode> trains = TrainTracker.latest(world);
        double r = range(), h = cfg.num("height"), minKmh = cfg.num("minKmh");
        String which = cfg.text("which");
        boolean now = false;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        for (TrainNode t : trains) {
            double dx = t.x - cx, dy = t.y - cy, dz = t.z - cz;
            if (Math.abs(dy) > h) continue;
            if ("Locomotives only".equals(which) && !t.kind.isLoco()) continue;
            if ("Moving only".equals(which) && !t.moving()) continue;
            if ("Stopped only".equals(which) && t.moving()) continue;
            if (Math.abs(t.speedKmh) < minKmh) continue;
            if (dx * dx + dz * dz <= r * r) { now = true; break; }
        }
        if (now) holdLeft = cfg.num("hold") * 20;
        else if (holdLeft > 0) { holdLeft -= every; if (holdLeft > 0) now = true; }
        if (now != raw) {
            raw = now;
            if (now) trainsSeen++;
            String mode = cfg.text("mode");
            if (now && mode.startsWith("Pulse when a train enters") || !now && mode.startsWith("Pulse when it leaves")) pulseLeft = Math.max(2, cfg.num("pulse"));
        }
        if (now != occupied) {
            occupied = now;
            markDirty();
            sync();
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
    }

    public int redstoneOutput() {
        int lv = Math.max(1, cfg.num("level"));
        return switch (cfg.text("mode")) {
            case "Inverted (on when clear)" -> occupied ? 0 : lv;
            case "Pulse when a train enters", "Pulse when it leaves" -> pulseLeft > 0 ? lv : 0;
            default -> occupied ? lv : 0;
        };
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
        t.setInteger("seen", trainsSeen);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        rangeIndex = Math.max(0, Math.min(RANGES.length - 1, t.getByte("range")));
        occupied = t.getBoolean("occ");
        trainsSeen = t.getInteger("seen");
        cfg.read(t);
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
