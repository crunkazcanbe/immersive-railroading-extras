package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Map;

/**
 * The station public-address speaker. When a train calls at the station this speaker belongs to,
 * it plays the two-tone chime and reads the announcement to everyone on the platform.
 */
public class TilePaSpeaker extends TileEntity implements ITickable {

    /** how far the announcement carries, in blocks */
    private static final double EARSHOT = 24.0D;

    private long station = Long.MIN_VALUE;
    private String stationName = "";
    private int resolveTimer;
    private String lastCall = "";

    public long station() {
        return station;
    }

    public String stationName() {
        return stationName;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (--resolveTimer <= 0) {
            resolveTimer = 100;
            resolveStation();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            com.dogpound.railmap.auto.ArrivalEvents.addSpeaker(world.provider.getDimension(), this);
        }
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            com.dogpound.railmap.auto.ArrivalEvents.removeSpeaker(world.provider.getDimension(), this);
        }
        super.invalidate();
    }

    private void resolveStation() {
        StationData stations = StationData.get(world);
        long best = Long.MIN_VALUE;
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach;
        for (Map.Entry<Long, String> e : stations.names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (d < bd) {
                bd = d;
                best = e.getKey();
            }
        }
        if (best != station) {
            station = best;
            stationName = best == Long.MIN_VALUE ? "" : stations.names().get(best);
            markDirty();
        }
    }

    /** Called when a train pulls in at this speaker's station. */
    public void announceArrival(String train) {
        say("The train now arriving at " + stationName + " is the " + train + ".");
    }

    public void announceDeparture(String train) {
        say("The " + train + " is ready to depart " + stationName + ". Mind the doors.");
    }

    private void say(String text) {
        if (world == null || world.isRemote) {
            return;
        }
        lastCall = text;
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.BLOCKS, 1.0F, 1.2F);
        AxisAlignedBB earshot = new AxisAlignedBB(pos).grow(EARSHOT, EARSHOT / 2, EARSHOT);
        List<EntityPlayer> near = world.getEntitiesWithinAABB(EntityPlayer.class, earshot);
        for (EntityPlayer p : near) {
            p.sendMessage(new TextComponentString(TextFormatting.AQUA + "[PA] " + TextFormatting.WHITE + text));
        }
    }

    public String statusLine() {
        if (station == Long.MIN_VALUE) {
            return "PA speaker — no station in range";
        }
        return "PA speaker · " + stationName + (lastCall.isEmpty() ? "" : " · last: " + lastCall);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setLong("station", station);
        t.setString("stationName", stationName);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        station = t.hasKey("station") ? t.getLong("station") : Long.MIN_VALUE;
        stationName = t.getString("stationName");
    }
}
