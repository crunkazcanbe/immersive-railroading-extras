package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.server.Wear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/** Once a second: repair parked stock within reach (see {@link Wear#serviceNear}). Options: Settings Console. */
public class TileMaintenanceDepot extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int working, serviced;
    private boolean wasWorking;

    @Override
    public void update() {
        if (world == null || world.isRemote || world.getTotalWorldTime() % 20 != 0) return;
        boolean powered = world.isBlockPowered(pos);
        String rs = cfg.text("rsMode");
        if (!cfg.bool("on") || "Only when powered".equals(rs) && !powered || "Only when unpowered".equals(rs) && powered) { setWorking(0); return; }
        int mask = 0;
        String[] keys = {"wheels", "brakes", "engine", "bearings", "electrics"};
        for (int i = 0; i < keys.length; i++) if (cfg.bool(keys[i])) mask |= 1 << i;
        int n = Wear.serviceNear(world, pos, cfg.num("reach"), cfg.num("speed") / 10.0, cfg.num("parked"), mask, cfg.bool("announce"));
        setWorking(Math.max(0, n));
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
        if (world != null) for (String line : Wear.reportNear(world, pos, cfg.num("reach")))
            l.add(com.dogpound.railmap.settings.Setting.info(st, "Unit", net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(line)));
        return l;
    }

    @Override
    public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound t) {
        super.writeToNBT(t);
        t.setInteger("serviced", serviced);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(net.minecraft.nbt.NBTTagCompound t) {
        super.readFromNBT(t);
        serviced = t.getInteger("serviced");
        cfg.read(t);
    }
}
