package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSpeedSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * A trackside defect detector — the box that talks on the radio after every train.
 * <p>
 * It watches the track beside it. From the moment the head end passes until the last car has
 * cleared it measures the train: how many cars went by, how fast, and whether anything was
 * wrong. Then it keys up and reads the report to everyone in radio range, word for word the way
 * the real ones do:
 * <blockquote>"DogPound detector, milepost 12.4, track 1. No defects. Train speed 42.
 * Total axles 36. Temperature 71 degrees. Detector out."</blockquote>
 * Rename it in an anvil to name the detector. Right-click steps the track number.
 * <p>
 * Defects are only reported for things the detector can actually measure: going faster than
 * the posted speed limit nearby is "excessive speed", and a train that stops on the detector is
 * "stopped on detector". It never invents a hot box.
 * <p>
 * Redstone: a pulse while it reports, strength 15 on a defect, 8 on a clean train.
 */
public class TileDefectDetector extends TileEntity implements ITickable {
    private static final double RANGE = 4.0;
    private static final int CLEAR_TICKS = 30;

    private String name = "";
    private int track = 1;
    private int ticks;

    // A pass in progress
    private boolean passing;
    private final Set<Integer> seen = new HashSet<>();
    /** Each train's position at the previous look, to catch trains that pass between samples. */
    private final Map<Integer, double[]> lastSeen = new HashMap<>();
    private double maxKmh;
    private int axles;
    private int clearFor;
    private int stoppedFor;
    private int output;
    private int outputTicks;

    public void setName(String n) {
        name = n == null ? "" : n.trim();
        markDirty();
    }

    public String name() {
        return name.isEmpty() ? "Railroad" : name;
    }

    public int track() {
        return track;
    }

    public void stepTrack() {
        track = track % 4 + 1;
        markDirty();
    }

    public int redstoneOutput() {
        return output;
    }

    /** Distance from the world origin, in miles, to one decimal — every detector has a milepost. */
    public String milepost() {
        double miles = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ()) / 1609.344;
        return String.format("%.1f", miles);
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        if (outputTicks > 0 && --outputTicks == 0) {
            output = 0;
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
        if (++ticks % 10 != 0) return;   // same rate the train tracker refreshes, so every look is a fresh position

        List<TrainNode> trains = TrainTracker.latest(world);
        boolean present = false;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        Map<Integer, double[]> now = new HashMap<>();
        for (TrainNode t : trains) {
            if (Math.abs(t.y - pos.getY()) > 4) continue;
            now.put(t.id, new double[]{t.x, t.z});
            // Distance to the stretch the train covered since the last look, not just where it is
            // now: positions only refresh twice a second, so a train doing 45 km/h moves ~6 blocks
            // between samples and jumped clean over a point check.
            double[] was = lastSeen.getOrDefault(t.id, new double[]{t.x, t.z});
            if (distToSegment(cx, cz, was[0], was[1], t.x, t.z) > RANGE) continue;
            present = true;
            if (seen.add(t.id)) axles += axlesOf(t);
            maxKmh = Math.max(maxKmh, Math.abs(t.speedKmh));
            if (Math.abs(t.speedKmh) < 0.5) stoppedFor += 10;
        }
        lastSeen.clear();
        lastSeen.putAll(now);
        if (present) {
            passing = true;
            clearFor = 0;
        } else if (passing) {
            clearFor += 10;
            if (clearFor >= CLEAR_TICKS) report();
        }
    }

    private static double distToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double k = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len2));
        double dx = px - (ax + k * vx), dz = pz - (az + k * vz);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int axlesOf(TrainNode t) {
        return switch (t.kind) {
            case HANDCAR -> 2;
            case LOCO_STEAM -> 8;
            case LOCO_DIESEL -> 6;
            default -> 4;
        };
    }

    private void report() {
        passing = false;
        int speedMph = (int) Math.round(maxKmh / 1.609344);
        String defect = null;
        TileSpeedSign sign = nearestSign();
        if (sign != null && maxKmh > sign.kmh() + 5) defect = "excessive speed";
        if (stoppedFor >= 100) defect = "stopped on detector";
        int temp = temperatureF();

        StringBuilder sb = new StringBuilder();
        sb.append(name()).append(" detector, milepost ").append(milepost()).append(", track ").append(track).append(". ");
        if (defect == null) sb.append("No defects. ");
        else sb.append("Defect, ").append(defect).append(". Repeat, ").append(defect).append(". ");
        sb.append("Train speed ").append(speedMph).append(". ");
        sb.append("Total axles ").append(axles).append(". ");
        sb.append("Temperature ").append(temp).append(" degrees. Detector out.");

        TextComponentString msg = new TextComponentString("[RADIO] " + sb);
        msg.getStyle().setColor(defect == null ? TextFormatting.AQUA : TextFormatting.GOLD);
        double r = RailMapConfig.detectorRadioRange;
        for (EntityPlayer p : world.playerEntities) {
            if (p.getDistanceSq(pos) <= r * r) p.sendMessage(msg);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_PLING, SoundCategory.BLOCKS, 1.0f, 1.5f);

        output = defect == null ? 8 : 15;
        outputTicks = 40;
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);

        seen.clear();
        axles = 0;
        maxKmh = 0;
        stoppedFor = 0;
        markDirty();
    }

    private TileSpeedSign nearestSign() {
        TileSpeedSign best = null;
        double bd = 96 * 96;
        for (TileSpeedSign s : SignalRegistry.speedSigns(world.provider.getDimension())) {
            double d = s.getPos().distanceSq(pos);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    /** Biome temperature as a believable Fahrenheit reading. */
    private int temperatureF() {
        float t = world.getBiome(pos).getTemperature(pos);
        boolean night = !world.isDaytime();
        int f = Math.round(20 + t * 65 - (night ? 12 : 0));
        return Math.max(-10, Math.min(115, f));
    }

    public String statusLine() {
        return name() + " detector · milepost " + milepost() + " · track " + track
                + " · rename it in an anvil · sneak-right-click: track number";
    }

    // ---- persistence ---------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setString("name", name);
        t.setByte("track", (byte) track);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        name = t.getString("name");
        track = Math.max(1, Math.min(4, t.getByte("track")));
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
