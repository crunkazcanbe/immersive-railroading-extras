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
public class TileCrossing extends TileEntity implements ITickable, IScalable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int seen, activations, prewarn, pulse;
    /** Drawn size, 1 = normal; set with the Signal Wrench (sneak-right-click). */
    private float scale = 1f;

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float s) {
        scale = IScalable.clamp(s);
        markDirty();
        sync();
    }

    private static final int[] APPROACH = { 16, 32, 48, 64, 96, 128 };

    public enum Kind { SIGNAL, GATE, CANTILEVER }

    private final Kind kind;
    private int approachIndex = 2;
    private boolean active;
    private int holdTicks;
    /** 0 = arm fully up, gateTicks() = fully down. Server-authoritative, smoothed on the client. */
    private int gate;
    private int ticks;

    protected TileCrossing(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() { return kind; }
    public boolean active() { return active; }
    public int approach() { return cfg.num("approach"); }

    private int gateTicks() { return Math.max(10, cfg.num("gateSec") * 20); }

    /** 0..1, how far the arm has come down. */
    public float gateProgress(float partial) {
        float g = gate, gt = gateTicks();
        if (active && prewarn <= 0 && gate < gt) g += partial;
        else if (!active && gate > 0) g -= partial;
        return Math.max(0, Math.min(1, g / gt));
    }

    /** Lights alternate left/right while active (flash period from the Settings Console). */
    public boolean lampLeftOn() {
        int p = Math.max(4, cfg.num("flash"));
        return active && (world.getTotalWorldTime() % p) < p / 2;
    }

    public boolean lampRightOn() {
        int p = Math.max(4, cfg.num("flash"));
        return active && (cfg.bool("together") ? (world.getTotalWorldTime() % p) < p / 2 : (world.getTotalWorldTime() % p) >= p / 2);
    }

    public void cycleApproach() {
        int cur = approach(), next = APPROACH[0];
        for (int a : APPROACH) if (a > cur) { next = a; break; }
        cfg.set("approach", Integer.toString(next));
        markDirty();
        sync();
    }

    // ---- Settings Console ------------------------------------------------------------------
    @Override public String settingsTitle() { return kind == Kind.GATE ? "Crossing Gate" : kind == Kind.CANTILEVER ? "Crossing Cantilever" : "Crossing Signal"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { sync(); }

    @Override
    public List<com.dogpound.railmap.settings.Setting> settingDefs() {
        List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String d = "Detection", t = "Timing", b = "Lights & bell", o = "Redstone", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.num(d, "approach", "Wake distance", "Trains closer than this start the crossing (right-click the block cycles presets)", 48, 8, 256, 4, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num(d, "island", "Island", "Inside this distance any train counts, whichever way it faces", 20, 4, 64, 2, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num(d, "height", "Height window", "Ignore trains more than this far above or below (bridges, tunnels)", 8, 2, 64, 1, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.bool(d, "direction", "Only trains heading this way", "Off = any train in range, even one going away", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(d, "stopped", "Stopped trains count", "A train standing in range keeps it down (e.g. at a station next to the road)", true));
        l.add(com.dogpound.railmap.settings.Setting.num(d, "minKmh", "Ignore slower than", "Trains crawling slower than this don't wake it (0 = all)", 0, 0, 60, 1, "km/h"));
        l.add(com.dogpound.railmap.settings.Setting.num(t, "prewarn", "Warning before gates fall", "Lights and bell start this long before the arms move", 2, 0, 15, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.num(t, "gateSec", "Arm travel time", "Seconds for the arm to come down or go up", 3, 1, 10, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.num(t, "hold", "Hold after train", "Stays down this long after the last train leaves", 2, 0, 30, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.bool(t, "armsBlock", "Lowered arms block traffic", "Down arms are solid - mobs and players can't drive through", true));
        l.add(com.dogpound.railmap.settings.Setting.num(b, "flash", "Flash period", "Ticks for one left-right flash cycle", 20, 4, 60, 2, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.bool(b, "together", "Both lamps flash together", "Off = alternate left/right like American crossings", false));
        l.add(com.dogpound.railmap.settings.Setting.bool(b, "bell", "Bell", "Ring while active", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(b, "gateBell", "Bell on gate-only crossings too", "", false));
        l.add(com.dogpound.railmap.settings.Setting.choice(b, "bellSound", "Bell sound", "", "Bell", "Bell", "Chime", "Pling", "Xylophone", "Harp"));
        l.add(com.dogpound.railmap.settings.Setting.num(b, "bellEvery", "Ring every", "Ticks between bell strikes", 20, 4, 60, 2, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.num(b, "bellVol", "Bell volume", "", 80, 0, 300, 10, "%"));
        l.add(com.dogpound.railmap.settings.Setting.num(b, "bellPitch", "Bell pitch", "", 190, 50, 200, 5, "%"));
        l.add(com.dogpound.railmap.settings.Setting.bool(o, "rsIn", "Redstone input forces it on", "Power the block to lower the crossing yourself", true));
        l.add(com.dogpound.railmap.settings.Setting.choice(o, "rsOut", "Redstone output", "What the block emits", "While active", "While active", "Arms fully down", "Pulse on start", "Inverted", "Off"));
        l.add(com.dogpound.railmap.settings.Setting.num(o, "rsLevel", "Output strength", "", 15, 1, 15, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "State", active ? (gate >= gateTicks() ? "DOWN" : prewarn > 0 ? "WARNING" : "LOWERING") : gate > 0 ? "RAISING" : "clear"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Arm position", Math.round(100f * gate / gateTicks()) + "%"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Trains in range", String.valueOf(seen)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Times activated", String.valueOf(activations)));
        return l;
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockCrossing ? s.getValue(BlockCrossing.FACING) : EnumFacing.NORTH;
    }

    /** Called once a second by {@link SignalEngine} with every train in the dimension. */
    public void updateFromTrack(List<TrainNode> trains) {
        double r = approach(), island = cfg.num("island"), hgt = cfg.num("height"), minKmh = cfg.num("minKmh");
        boolean near = false, dir = cfg.bool("direction"), stopped = cfg.bool("stopped");
        seen = 0;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        for (TrainNode t : trains) {
            double dx = t.x - cx, dy = t.y - cy, dz = t.z - cz;
            if (Math.abs(dy) > hgt) continue;
            double d2 = dx * dx + dz * dz;
            if (d2 > r * r) continue;
            seen++;
            if (Math.abs(t.speedKmh) < minKmh && t.moving()) continue;
            if (!t.moving() && !stopped) continue;
            // Approaching, or close enough that direction no longer matters (on the island).
            if (d2 < island * island || !t.moving() || !dir) { near = true; break; }
            double len = Math.sqrt(d2);
            double hx = Math.cos(Math.toRadians(t.yaw)), hz = Math.sin(Math.toRadians(t.yaw));
            if ((-dx * hx - dz * hz) / len > 0.2) { near = true; break; }   // heading toward us
        }
        setActive(near || cfg.bool("rsIn") && world.getRedstonePowerFromNeighbors(pos) > 0);
    }

    private void setActive(boolean want) {
        if (want) {
            holdTicks = Math.max(1, cfg.num("hold") * 20);
            if (!active) {
                active = true;
                activations++;
                prewarn = cfg.num("prewarn") * 20;
                pulse = 10;
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
        int gt = gateTicks();
        if (prewarn > 0) prewarn--;
        if (world.isRemote) {
            // Client only animates; the server tells it whether the crossing is active.
            if (active && prewarn <= 0 && gate < gt) gate++;
            else if (!active && gate > 0) gate--;
            return;
        }
        ticks++;
        if (pulse > 0 && --pulse == 0) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        if (holdTicks > 0 && --holdTicks == 0) setActive(false);
        boolean wasDown = gate >= gt;
        if (active && prewarn <= 0 && gate < gt) gate++;
        else if (!active && gate > 0) gate--;
        if (wasDown != gate >= gt) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        // The bell, while the lights are flashing.
        if (active && cfg.bool("bell") && (kind != Kind.GATE || cfg.bool("gateBell")) && ticks % Math.max(4, cfg.num("bellEvery")) == 0) {
            net.minecraft.util.SoundEvent bell = switch (cfg.text("bellSound")) {
                case "Chime" -> SoundEvents.BLOCK_NOTE_CHIME;
                case "Pling" -> SoundEvents.BLOCK_NOTE_PLING;
                case "Xylophone" -> SoundEvents.BLOCK_NOTE_XYLOPHONE;
                case "Harp" -> SoundEvents.BLOCK_NOTE_HARP;
                default -> SoundEvents.BLOCK_NOTE_BELL;
            };
            world.playSound(null, pos, bell, SoundCategory.BLOCKS, cfg.num("bellVol") / 100f, cfg.num("bellPitch") / 100f);
        }
    }

    public int redstoneOutput() {
        int lv = Math.max(1, cfg.num("rsLevel"));
        return switch (cfg.text("rsOut")) {
            case "Arms fully down" -> gate >= gateTicks() ? lv : 0;
            case "Pulse on start" -> pulse > 0 ? lv : 0;
            case "Inverted" -> active ? 0 : lv;
            case "Off" -> 0;
            default -> active ? lv : 0;
        };
    }

    /** Gate arms are solid while down: nothing drives through a closed crossing. */
    public boolean armBlocks() {
        return kind == Kind.GATE && cfg.bool("armsBlock") && gate > gateTicks() / 2;
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
        if (scale != 1f) t.setFloat("scale", scale);
        t.setBoolean("act", active);
        t.setByte("appr", (byte) approachIndex);
        t.setShort("gate", (short) gate);
        t.setShort("pw", (short) prewarn);
        t.setInteger("acts", activations);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        scale = t.hasKey("scale") ? IScalable.clamp(t.getFloat("scale")) : 1f;
        active = t.getBoolean("act");
        approachIndex = Math.max(0, Math.min(APPROACH.length - 1, t.getByte("appr")));
        gate = t.getShort("gate");
        prewarn = t.getShort("pw");
        activations = t.getInteger("acts");
        cfg.read(t);
        if (!t.getCompoundTag("cfg").hasKey("approach") && t.hasKey("appr")) cfg.set("approach", Integer.toString(APPROACH[approachIndex]));
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
        return new net.minecraft.util.math.AxisAlignedBB(pos).grow(6 * scale, 3 * scale, 6 * scale);
    }

    public static class Signal extends TileCrossing { public Signal() { super(Kind.SIGNAL); } }
    public static class Gate extends TileCrossing { public Gate() { super(Kind.GATE); } }
    public static class Cantilever extends TileCrossing { public Cantilever() { super(Kind.CANTILEVER); } }
}
