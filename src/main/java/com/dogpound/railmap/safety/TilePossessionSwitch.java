package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;

/**
 * Track Blocking Switch (her ChatGPT list #23 - "local track-blocking switches"): the lineside key switch a track
 * worker uses to take a possession of the line. Turn the key to BLOCK: the signals reading into its zone go to danger
 * at once and the interlocking refuses any route through it. The possession is only GRANTED (red LINE BLOCKED lamp
 * steady) once no train is left in the zone - until then the amber lamp flashes. Turn the key back to NORMAL to give
 * the line back. Holder and minutes held show on the plate; every take / give-back goes in the logs. The key is
 * mechanical: a possession holds even if the 12 V fails (only the lamps go out).
 */
public class TilePossessionSwitch extends SafetyTile {
    private String holder = "";
    private long since;
    private boolean granted;

    public int zone() { return new int[]{32, 64, 128}[Math.min(2, Math.max(0, ctl.get("s_zone")))]; }

    /** key in BLOCK: signals held, routes refused (requested or granted) */
    public boolean blocking() { return ctl.get("k_poss") == 1; }
    public boolean granted() { return blocking() && granted; }
    public String holder() { return holder; }

    @Override public String wiredName() { return "Track blocking switch"; }
    @Override public String gearState() { return !blocking() ? "NORMAL - line open" : granted ? "LINE BLOCKED - possession by " + holder : "BLOCK requested by " + holder + " - waiting for trains to clear"; }
    @Override public int wiredLamp() { return !powered() ? 3 : blocking() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("blocked", blocking()); m.put("granted", granted()); m.put("holder", holder); m.put("zone", zone()); }

    private boolean trainInZone() {
        double r2 = (double) zone() * zone();
        for (TrainNode t : FaultLab.withPhantoms(world, TrainTracker.latest(world)))
            if (Geo.d2(t.x, t.z, pos.getX() + 0.5, pos.getZ() + 0.5) <= r2 && Math.abs(t.y - pos.getY()) < 12) return true;
        return false;
    }

    @Override
    protected void serverTick() {
        if (age % 20 != 11) return;
        boolean train = trainInZone();
        if (blocking() && !granted && !train) {
            granted = true;
            log("possession GRANTED to " + holder + " - line blocked within " + zone() + " m");
        }
        put("blocked", granted());
        put("req", blocking() && !granted);
        put("clear", !train);
        put("train", train);
        put("pwr", powered());
        put("mins", blocking() ? (int) ((world.getTotalWorldTime() - since) / 1200) : 0);
        text("holder", blocking() ? holder : "LINE OPEN");
        text("zone", "ZONE " + zone() + "M");
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_zone":
                if (blocking()) return "§cGive the line back before changing the zone";
                ctl.set("s_zone", (ctl.get("s_zone") + 1) % 3);
                return "§eZone " + zone() + " m";
            case "k_poss":
                if (!blocking()) {
                    ctl.set("k_poss", 1);
                    holder = who(who);
                    since = world.getTotalWorldTime();
                    granted = false;
                    log("BLOCK requested by " + holder + " (signals into the zone to danger)");
                    return "§6Line block requested - signals to danger; granted once no train is in the zone";
                }
                ctl.set("k_poss", 0);
                log("line given back by " + who(who) + " after " + (world.getTotalWorldTime() - since) / 1200 + " min");
                granted = false;
                return "§aLine given back - NORMAL";
            default: return "§7" + gearState();
        }
    }

    private void log(String s) {
        SafetyData d = SafetyData.get(world);
        d.log(world, "POSSESSION " + pos.getX() + "," + pos.getZ(), "DISPATCH", s, 2, 3);
        d.history(world, pos, wiredName(), "possession switch", s);
        Recorder.event(world, "POSSESSION " + pos.getX() + "," + pos.getZ() + ": " + s);
    }

    @Override protected boolean keepTextUnpowered() { return false; }
    @Override protected void save(NBTTagCompound t) { t.setString("holder", holder); t.setLong("since", since); t.setBoolean("granted", granted); }
    @Override protected void load(NBTTagCompound t) { holder = t.getString("holder"); since = t.getLong("since"); granted = t.getBoolean("granted"); }
}
