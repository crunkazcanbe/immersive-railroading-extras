package com.dogpound.railmap.incident;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Announcement Control Unit (her ChatGPT list #76): the station's public-information console with its gooseneck
 * microphone. It belongs to the nearest named station. The LED shows the trains due here (live: ETA, held, late).
 * TEMPLATE picks DELAY / PLATFORM / CANCEL / CONNECT / NEXT / EMERG / EVAC / CLEAR, TRAIN < > the train it is about,
 * PLATFORM the platform for an alteration; PREVIEW puts the filled-in text on the LED, ANNOUNCE speaks it on every
 * PA speaker of the station and shows it as text to the people there. With the AUTO key it announces by itself from
 * live data: delays and their reason, cancellations, connections, the next arrival. 12 V.
 */
public class TileAnnounceUnit extends SafetyTile {
    private long station = Long.MIN_VALUE;
    private String name = "";
    private int count;
    private long onAir;

    @Override public String wiredName() { return "Announcement control unit"; }
    @Override public String gearState() { return name.isEmpty() ? "no station within reach" : name + " · " + (ctl.get("k_auto") == 1 ? "AUTO" : "MANUAL") + " · " + count + " announcements"; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("station", name); m.put("count", count); }

    public long station() { return station; }
    public String stationName() { return name; }

    private void resolve() {
        StationData sd = StationData.get(world);
        long best = Long.MIN_VALUE;
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach;
        for (Map.Entry<Long, String> e : sd.names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (d < bd) { bd = d; best = e.getKey(); }
        }
        station = best;
        name = best == Long.MIN_VALUE ? "" : sd.names().get(best);
    }

    private String compose() {
        int k = Math.floorMod(ctl.get("s_tpl"), Announcer.KINDS.length);
        List<Announcer.Due> due = Announcer.due(world, station, name);
        Announcer.Due d = due.isEmpty() ? null : due.get(Math.floorMod(ctl.get("tsel"), due.size()));
        if (d == null && k <= 4) return null;
        Map<String, String> v = Announcer.vars(world, name, d, Math.floorMod(ctl.get("s_plat"), 8) + 1);
        v.put("place", name.isEmpty() ? pos.getX() + "," + pos.getZ() : name);
        return Announcer.fill(Announcer.template(k), v);
    }

    @Override
    protected void serverTick() {
        if (age % 100 == 1 || station == Long.MIN_VALUE && age % 20 == 1) resolve();
        if (!powered()) return;
        if (ctl.get("k_auto") == 1 && age % 100 == 7 && !name.isEmpty()) Announcer.auto(world, station, name);
        if (age % 20 != 3) return;
        List<Announcer.Due> due = Announcer.due(world, station, name);
        Announcer.Due d = due.isEmpty() ? null : due.get(Math.floorMod(ctl.get("tsel"), due.size()));
        text("stn", name.isEmpty() ? "NO STATION" : name.toUpperCase(Locale.ROOT));
        text("train", d == null ? "NO TRAINS DUE" : (d.train + " TO " + d.dest + (d.dwelling ? " - HERE" : d.held ? " - HELD" : " - " + d.etaMin + " MIN") + (d.lateMin > 0 ? " +" + d.lateMin : "")).toUpperCase(Locale.ROOT));
        if (world.getTotalWorldTime() - onAir > 400) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(2, due.size()); i++) b.append(i == 0 ? "" : "  ").append(due.get(i).train).append(' ').append(due.get(i).dwelling ? "HERE" : due.get(i).held ? "HELD" : due.get(i).etaMin + "M");
            text("l1", b.length() == 0 ? "NO TRAINS DUE" : b.toString().toUpperCase(Locale.ROOT));
            text("l2", Announcer.KINDS[Math.floorMod(ctl.get("s_tpl"), Announcer.KINDS.length)] + " READY");
        }
        boolean late = false;
        for (Announcer.Due x : due) late |= x.held || x.lateMin >= IncidentConfig.announceDelayMinutes;
        put("delay", late);
        put("onair", world.getTotalWorldTime() - onAir < 100);
        put("auto", ctl.get("k_auto") == 1);
        put("power", true);
        put("cnt", count);
        put("due", due.size());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("k_") || id.startsWith("s_")) return null;
        if (!powered()) return "§cThe announcement unit has no 12 V";
        switch (id) {
            case "tprev": ctl.set("tsel", ctl.get("tsel") - 1); return "";
            case "tnext": ctl.set("tsel", ctl.get("tsel") + 1); return "";
            case "preview": {
                String s = compose();
                if (s == null) return "§7No train due to announce about";
                text("l1", s.length() > 46 ? s.substring(0, 46) : s);
                text("l2", s.length() > 46 ? s.substring(46, Math.min(s.length(), 92)) : "");
                onAir = world.getTotalWorldTime() - 300;
                return "§7" + s;
            }
            case "announce": {
                String s = compose();
                if (s == null) return "§7No train due to announce about";
                int k = Math.floorMod(ctl.get("s_tpl"), Announcer.KINDS.length);
                int n = Announcer.say(world, station, name, Announcer.KINDS[k], s);
                if (k == 2) Analytics.count(world, "cancel", 1);
                count++;
                onAir = world.getTotalWorldTime();
                text("l1", s.length() > 46 ? s.substring(0, 46) : s);
                text("l2", s.length() > 46 ? s.substring(46, Math.min(s.length(), 92)) : "");
                return n == 0 ? "§7Shown as text - no powered PA speakers at " + (name.isEmpty() ? "this station" : name) : "§eAnnounced on " + n + " speaker(s)";
            }
            case "test": return lampTest();
            default: return null;
        }
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("cnt", count); }
    @Override protected void load(NBTTagCompound t) { count = t.getInteger("cnt"); }
}
