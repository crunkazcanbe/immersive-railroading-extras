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
public class TileTicketGate extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int passed, refused;

    @Override public String settingsTitle() { return "Fare Gate"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }

    public boolean creativeWalksThrough() { return cfg.bool("creative"); }
    public boolean beeps() { return cfg.bool("sound"); }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String g = "Gate", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.num(g, "openSec", "Stays open", "", 3, 1, 30, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.choice(g, "accept", "Accept tickets", "", "To or from here", "To or from here", "Leaving from here", "Arriving here", "Any valid ticket"));
        l.add(com.dogpound.railmap.settings.Setting.bool(g, "punch", "Punch the ticket", "Mark it used at the gate (it can't be used again)", false));
        l.add(com.dogpound.railmap.settings.Setting.bool(g, "creative", "Creative players walk through", "Open for creative-mode players without a ticket", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(g, "sound", "Beep", "", true));
        l.add(com.dogpound.railmap.settings.Setting.num(g, "level", "Redstone while open", "", 15, 1, 15, 1, ""));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Station", stationName.isEmpty() ? "none in range" : stationName));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Let through", String.valueOf(passed)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Turned away", String.valueOf(refused)));
        return l;
    }


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
            refused++;
            return "Present a ticket for " + stationName + ".";
        }
        if (ItemTicket.isSpent(held)) {
            refused++;
            return "That ticket is spent.";
        }
        String acc = cfg.text("accept");
        boolean from = ItemTicket.from(held) == station, to = ItemTicket.to(held) == station;
        boolean mine = switch (acc) {
            case "Leaving from here" -> from;
            case "Arriving here" -> to;
            case "Any valid ticket" -> true;
            default -> from || to;
        };
        if (!mine) {
            refused++;
            return "Not valid here — that ticket is " + ItemTicket.fromName(held)
                    + " to " + ItemTicket.toName(held) + ".";
        }
        if (cfg.bool("punch")) ItemTicket.punch(held);
        passed++;
        open();
        return "Ticket accepted — " + stationName + ". Please proceed.";
    }

    public void open() {
        boolean was = isOpen();
        openFor = Math.max(20, cfg.num("openSec") * 20);
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
        return isOpen() ? Math.max(1, cfg.num("level")) : 0;
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
        t.setInteger("passed", passed);
        t.setInteger("refused", refused);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        station = t.hasKey("station") ? t.getLong("station") : Long.MIN_VALUE;
        stationName = t.getString("stationName");
        openFor = t.getInteger("open");
        passed = t.getInteger("passed");
        refused = t.getInteger("refused");
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
}
