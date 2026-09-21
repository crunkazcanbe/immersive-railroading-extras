package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.item.ItemTicket;
import com.dogpound.railmap.server.StationData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * A fare gate. Closed, it is a waist-high barrier across the concourse; shown a ticket that is
 * valid for this station it swings open for a few seconds, chirps, and passes redstone on while
 * it is open — so it can drive a door, a counter, or a light.
 * <p>
 * It accepts a ticket travelling either way: outbound from this station, or arriving at it. A
 * spent ticket is refused, which is what makes a round trip worth the extra fare.
 */
public class TileTicketGate extends TileEntity implements ITickable {

    /** how long the gate stays open after a valid ticket, in ticks */
    private static final int OPEN_TICKS = 60;

    private long station = Long.MIN_VALUE;
    private String stationName = "";
    private int openFor;
    private int resolveTimer;

    public long station() {
        return station;
    }

    public String stationName() {
        return stationName;
    }

    public boolean isOpen() {
        return openFor > 0;
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
        if (openFor > 0 && --openFor == 0) {
            syncState();
        }
    }

    /** The nearest named station within reach — the one this gate guards. */
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

    /**
     * Tries a ticket against this gate.
     *
     * @return the message to show the passenger
     */
    public String present(ItemStack held) {
        if (station == Long.MIN_VALUE) {
            return "This gate isn't at a station yet — name a station nearby.";
        }
        if (!ItemTicket.valid(held)) {
            return "Present a ticket for " + stationName + ".";
        }
        if (ItemTicket.isSpent(held)) {
            return "That ticket is spent.";
        }
        boolean mine = ItemTicket.from(held) == station || ItemTicket.to(held) == station;
        if (!mine) {
            return "Not valid here — that ticket is " + ItemTicket.fromName(held)
                    + " to " + ItemTicket.toName(held) + ".";
        }
        open();
        return "Ticket accepted — " + stationName + ". Please proceed.";
    }

    public void open() {
        boolean was = isOpen();
        openFor = OPEN_TICKS;
        if (!was) {
            syncState();
        }
    }

    private void syncState() {
        markDirty();
        IBlockState st = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, st, st, 3);
        world.notifyNeighborsOfStateChange(pos, st.getBlock(), false);
    }

    public int redstoneOutput() {
        return isOpen() ? 15 : 0;
    }

    public String statusLine() {
        if (station == Long.MIN_VALUE) {
            return "Fare gate — no station in range";
        }
        return "Fare gate · " + stationName + " · " + (isOpen() ? "OPEN" : "closed");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setLong("station", station);
        t.setString("stationName", stationName);
        t.setInteger("open", openFor);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        station = t.hasKey("station") ? t.getLong("station") : Long.MIN_VALUE;
        stationName = t.getString("stationName");
        openFor = t.getInteger("open");
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
}
