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
public class TileTicketGate extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder, com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- working controls (requested feature): reader lamps green / red on 12 V, staff key holds the paddles open
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { gearChanged(); }
    /** staff override key turned to OPEN (mechanical: works with no power) */
    public boolean heldOpen() { return ctl.get("override") == 1; }
    @Override public int controlValue(String id) {
        if (id.startsWith("go")) return term.powered() && isOpen() ? 1 : 0;
        if (id.startsWith("stop")) return term.powered() && !isOpen() ? 1 : 0;
        return ctl.get(id);
    }
    @Override public String controlPress(String id, net.minecraft.entity.player.EntityPlayer who) {
        if (!id.equals("override")) return null;
        ctl.set(id, heldOpen() ? 0 : 1);
        syncState();
        return heldOpen() ? "§eStaff key OPEN - the gate is held open, no tickets checked" : "§eStaff key AUTO - tickets checked again";
    }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.controls.IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }

    // ---- 12 V terminal (requested feature): needs 12 V DC instrument power; with none it is paddles standing open
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { return "Ticket gate"; }
    @Override public String gearState() { return term.powered() ? passed + " through · " + refused + " turned away" : "NO 12 V - paddles standing open"; }

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
        com.dogpound.railmap.wire.IWiredGear.settings(l, st, this);
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
        return openFor > 0 || heldOpen();
    }

    @Override
    public void update() {
        if (world != null && !world.isRemote) { if (term.tick(world, pos, wiredName())) gearChanged(); if (!term.powered()) return; }
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
        if (!term.powered()) { passed++; openFor = 40; syncState(); return "The gate is dead (no 12 V) - its paddles stand open, walk through"; }
        if (heldOpen()) { passed++; return "The gate is held open by staff - walk through"; }
        if (station == Long.MIN_VALUE) {
            return "This gate isn't at a station yet — name a station nearby.";
        }
        if (com.dogpound.railmap.travel6.Cards.isCard(held)) {                  // Pride Card: tap in / out (batch 6b #6)
            String acc0 = cfg.text("accept");
            int mode = acc0.equals("Leaving from here") ? com.dogpound.railmap.travel6.Cards.ENTRY : acc0.equals("Arriving here") ? com.dogpound.railmap.travel6.Cards.EXIT : com.dogpound.railmap.travel6.Cards.AUTO;
            com.dogpound.railmap.travel6.Cards.Result cr = com.dogpound.railmap.travel6.Cards.tap(world, held, station, stationName, mode, "gate " + pos.getX() + "," + pos.getZ());
            if (!cr.ok) { refused++; return cr.msg + "."; }
            passed++;
            open();
            return cr.msg;
        }
        if (ItemTicket.isPass(held)) {                                          // travel passes check the fare zone (her list #48)
            int zone = com.dogpound.railmap.economy.PassengerData.get(world).zoneOf(station);
            String no = ItemTicket.passRefused(held, world.getTotalWorldTime(), zone);
            if (no != null) { refused++; return no + "."; }
            ItemTicket.ride(held);
            passed++;
            open();
            return "Pass accepted - " + stationName + ", zone " + zone + ". Please proceed.";
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
        if (!to) { String shut = com.dogpound.railmap.station.PlatformOps.gateRefuse(world, station); if (shut != null) { refused++; return shut; } }   // closed station / crowd control: exit only (batch 3 #27 #33)
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
        term.write(t);
        cfg.write(t);
        ctl.write(t);
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
        term.read(t);
        cfg.read(t);
        ctl.read(t);
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
