package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.server.Wear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/**
 * Once a second: repair parked stock within reach (see {@link Wear#serviceNear}). Options: Settings Console.
 * Its controls work (requested feature): START / STOP open and close the workshop; the compressor keeps the air receiver
 * between 8 and 10 bar on 12 V and the repair tools use air (no repairs under 5 bar, the gauge shows it); the column
 * jack raises / lowers on its buttons (E-stop halts it) and a unit on a fully raised jack is repaired twice as fast.
 */
public class TileMaintenanceDepot extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder, com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- 12 V terminal (requested feature): needs 12 V DC instrument power; with none it is no repairs
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { return "Maintenance depot"; }
    @Override public String gearState() { return term.powered() ? (cfg.bool("on") ? "repairing · " + serviced + " s so far" : "workshop closed") + " · air " + air() / 10.0 + " bar" : "NO 12 V - no repairs"; }

    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int working, serviced;
    private boolean wasWorking;

    // ---- working controls ----------------------------------------------------------------------------------------
    /** air in tenths of a bar, jack height %, jack moving (-1 / 0 / +1), compressor running: kept in the control state */
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { gearChanged(); }
    private int air() { return ctl.vals.containsKey("air") ? ctl.get("air") : 80; }

    @Override
    public String controlPress(String id, net.minecraft.entity.player.EntityPlayer who) {
        switch (id) {
            case "start": if (!term.powered()) return "§cNo 12 V - the depot controls are dead"; cfg.set("on", "true"); return "§aWorkshop open - repairing parked stock";
            case "stop": cfg.set("on", "false"); setWorking(0); return "§eWorkshop closed - repairs stopped";
            case "jackup":
                if (ctl.get("jackstop") == 1) return "§cJack E-stop is in - twist it out first";
                if (!term.powered()) return "§cNo 12 V - the jack pump won't run";
                ctl.set("jdir", 1); return "§eJack raising";
            case "jackdown":
                if (ctl.get("jackstop") == 1) return "§cJack E-stop is in - twist it out first";
                ctl.set("jdir", -1); return "§eJack lowering";
            case "jackstop":
                ctl.set("jdir", 0);
                ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
                return ctl.get(id) == 1 ? "§cJack EMERGENCY STOP" : "§aJack E-stop released";
            default: return null;
        }
    }

    /** every half second: jack, compressor, lamps and the status display */
    private void runControls() {
        com.dogpound.railmap.controls.ControlState c = ctl;
        boolean pw = term.powered(), ch = false;
        int jack = c.get("jack"), dir = c.get("jdir"), air = air();
        if (dir > 0 && !pw) dir = 0;
        jack = Math.max(0, Math.min(100, jack + dir * 5));
        if (jack == 0 || jack == 100) dir = 0;
        boolean comp = pw && (c.get("comp") == 1 ? air < 100 : air < 80);   // cut in at 8 bar, out at 10
        if (comp) air = Math.min(100, air + 3);
        ch |= StationControls.put(c, "jack", jack) | StationControls.put(c, "jdir", dir) | StationControls.put(c, "comp", comp)
                | StationControls.put(c, "air", air);
        boolean on = cfg.bool("on");
        ch |= StationControls.put(c, "stock", pw && working > 0) | StationControls.put(c, "ready", pw && on && air >= 50)
                | StationControls.put(c, "pitlamp", pw && on) | StationControls.put(c, "beacon", pw && dir != 0);
        ch |= StationControls.text(c, "line1", !pw ? "" : !on ? "CLOSED" : air < 50 ? "LOW AIR" : working > 0 ? "REPAIR " + working : "READY")
                | StationControls.text(c, "line2", pw ? String.format(java.util.Locale.ROOT, "AIR %.1f", air / 10.0) : "")
                | StationControls.text(c, "line3", pw ? "JACK " + jack + "%" : "");
        if (ch) controlsChanged();
    }

    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.controls.IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (term.tick(world, pos, wiredName())) gearChanged();
        if (world.getTotalWorldTime() % 10 == 0) runControls();
        if (!term.powered() || world.getTotalWorldTime() % 20 != 0) return;
        boolean powered = world.isBlockPowered(pos);
        String rs = cfg.text("rsMode");
        if (!cfg.bool("on") || air() < 50 || "Only when powered".equals(rs) && !powered || "Only when unpowered".equals(rs) && powered) { setWorking(0); return; }
        int mask = 0;
        String[] keys = {"wheels", "brakes", "engine", "bearings", "electrics"};
        for (int i = 0; i < keys.length; i++) if (cfg.bool(keys[i])) mask |= 1 << i;
        double speed = cfg.num("speed") / 10.0 * (ctl.get("jack") == 100 ? 2 : 1);
        int n = Wear.serviceNear(world, pos, cfg.num("reach"), speed, cfg.num("parked"), mask, cfg.bool("announce"));
        setWorking(Math.max(0, n));
        if (n > 0) { ctl.set("air", Math.max(0, air() - 2 * n)); controlsChanged(); }   // the air tools use the receiver down
    }

    private void setWorking(int n) {
        if (n > 0) serviced++;
        working = n;
        if (wasWorking != n > 0) { wasWorking = n > 0; world.notifyNeighborsOfStateChange(pos, getBlockType(), false); }
        markDirty();
    }

    /** redstone: 15 while repairing something (Settings Console > Redstone) */
    public int redstoneOutput() { return cfg.bool("rsOut") && working > 0 ? 15 : 0; }

    @Override public String settingsTitle() { return "Maintenance Depot"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String w = "Workshop", p = "Parts", r = "Redstone", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.bool(w, "on", "Open", "Repair parked stock", true));
        l.add(com.dogpound.railmap.settings.Setting.num(w, "reach", "Reach", "Stock within this many blocks gets serviced", RailMapConfig.depotReach, 2, 64, 1, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num(w, "speed", "Repair speed", "Wear removed per second, per part (tenths of a percent)", (int) Math.round(RailMapConfig.depotRepairPerSecond * 10), 1, 500, 5, "x0.1%/s"));
        l.add(com.dogpound.railmap.settings.Setting.num(w, "parked", "Counts as parked below", "Stock moving faster than this is left alone", 1, 0, 30, 1, "km/h"));
        l.add(com.dogpound.railmap.settings.Setting.bool(w, "announce", "Announce 'fully serviced'", "", true));
        String[][] parts = {{"wheels", "Wheels"}, {"brakes", "Brakes"}, {"engine", "Engine"}, {"bearings", "Bearings"}, {"electrics", "Electrics"}};
        for (String[] pp : parts) l.add(com.dogpound.railmap.settings.Setting.bool(p, pp[0], pp[1], "Repair " + pp[1].toLowerCase(), true));
        l.add(com.dogpound.railmap.settings.Setting.choice(r, "rsMode", "Redstone control", "", "Always", "Always", "Only when powered", "Only when unpowered"));
        l.add(com.dogpound.railmap.settings.Setting.bool(r, "rsOut", "Output 15 while repairing", "", true));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Working on", working + " unit" + (working == 1 ? "" : "s")));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Seconds spent repairing", String.valueOf(serviced)));
        com.dogpound.railmap.wire.IWiredGear.settings(l, st, this);
        if (world != null) for (String line : Wear.reportNear(world, pos, cfg.num("reach")))
            l.add(com.dogpound.railmap.settings.Setting.info(st, "Unit", net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(line)));
        return l;
    }

    @Override
    public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound t) {
        super.writeToNBT(t);
        t.setInteger("serviced", serviced);
        term.write(t);
        cfg.write(t);
        ctl.write(t);
        t.setInteger("working", working);
        return t;
    }

    @Override
    public void readFromNBT(net.minecraft.nbt.NBTTagCompound t) {
        super.readFromNBT(t);
        serviced = t.getInteger("serviced");
        term.read(t);
        cfg.read(t);
        ctl.read(t);
        working = t.getInteger("working");
    }

    @Override public net.minecraft.nbt.NBTTagCompound getUpdateTag() { return writeToNBT(new net.minecraft.nbt.NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }
}
