package com.dogpound.railmap.station;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.ctl.CtlConfig;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * PIS platform sign (her "next 48" list #30): a hanging dot-matrix passenger information display that reads the shared
 * feed for the nearest named station (within 128 m). Line 1 the station and clock, lines 2-3 the next trains and/or the
 * highest-priority messages, a scrolling ticker of everything in the feed. EVACUATION takes the whole sign over and
 * flashes. When it can't reach a PIS server (none switched on, out of reach, or its field RTU's comms are stale) it
 * shows the fallback message - never stale information. MODE: FEED / TRAINS / BOTH. 12 V for the sign.
 */
public class TilePisBoard extends SafetyTile {
    private long station = Long.MIN_VALUE;
    private String name = "";
    private boolean wasOnline = true;

    @Override public String wiredName() { return "PIS sign" + (name.isEmpty() ? "" : " " + name); }
    @Override public String gearState() { return !powered() ? "dark - no 12 V" : name.isEmpty() ? "no station within 128 m" : (Pis.reachable(world, pos) ? "online" : "FALLBACK - no PIS server") + " · " + name; }
    @Override public int wiredLamp() { return !powered() ? 3 : Pis.reachable(world, pos) ? 1 : 2; }

    @Override
    protected void serverTick() {
        if (age % 20 != 13) return;
        if (age % 200 == 13 || station == Long.MIN_VALUE) {
            station = Long.MIN_VALUE; name = "";
            double bd = 128 * 128;
            for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) {
                double d = net.minecraft.util.math.BlockPos.fromLong(e.getKey()).distanceSq(pos);
                if (d < bd) { bd = d; station = e.getKey(); name = e.getValue(); }
            }
        }
        boolean online = powered() && Pis.reachable(world, pos);
        if (online != wasOnline && powered()) { Recorder.event(world, "PIS sign " + name + " " + (online ? "back online" : "lost its PIS server - FALLBACK")); wasOnline = online; }
        String clock = Recorder.clock(world, world.getTotalWorldTime() + com.dogpound.railmap.ops6.MasterClocks.offset(world, pos));   // slave clock drift (batch 6 E #24)
        String l1 = (name.isEmpty() ? "NO STATION" : name.toUpperCase(Locale.ROOT)) + "  " + clock, l2 = "", l3 = "", tick;
        boolean evac = false;
        if (!online) { l2 = "INFORMATION UNAVAILABLE"; tick = CtlConfig.pisFallback; }
        else {
            List<Pis.Msg> feed = Pis.get(world).forStation(world, name);
            evac = !feed.isEmpty() && feed.get(0).kind == Pis.Kind.EVAC;
            List<String> trains = new ArrayList<>();
            if (station != Long.MIN_VALUE) for (Announcer.Due d : Announcer.due(world, station, name)) {
                trains.add(String.format(Locale.ROOT, "%s %s %s", d.dest.toUpperCase(Locale.ROOT), d.dwelling ? "ON PLATFORM" : d.held ? "DELAYED" : d.etaMin + d.lateMin <= 0 ? "DUE" : (d.etaMin + d.lateMin) + " MIN", d.lateMin > 0 ? "+" + d.lateMin : ""));
                if (trains.size() >= 2) break;
            }
            int mode = ctl.get("s_mode");
            if (evac) { l1 = "!! EVACUATE " + (name.isEmpty() ? "" : name.toUpperCase(Locale.ROOT)) + " !!"; l2 = "LEAVE BY THE NEAREST EXIT"; l3 = "DO NOT USE THE LIFTS"; }
            else if (mode == 1) { l2 = trains.size() > 0 ? trains.get(0) : "NO TRAINS DUE"; l3 = trains.size() > 1 ? trains.get(1) : ""; }
            else if (mode == 0) { l2 = feed.size() > 0 ? feed.get(0).text.toUpperCase(Locale.ROOT) : "GOOD SERVICE"; l3 = feed.size() > 1 ? feed.get(1).text.toUpperCase(Locale.ROOT) : ""; }
            else { l2 = trains.size() > 0 ? trains.get(0) : "NO TRAINS DUE"; l3 = feed.size() > 0 ? feed.get(0).text.toUpperCase(Locale.ROOT) : trains.size() > 1 ? trains.get(1) : "GOOD SERVICE"; }
            StringBuilder b = new StringBuilder();
            for (Pis.Msg m : feed) b.append(m.text.toUpperCase(Locale.ROOT)).append("   +++   ");
            tick = b.length() == 0 ? "WELCOME TO " + name.toUpperCase(Locale.ROOT) + "   +++   " : b.toString();
        }
        int off = tick.length() <= 36 ? 0 : (age / 20) * 3 % tick.length();
        String doubled = tick + tick;
        text("l1", l1);
        text("l2", l2);
        text("l3", l3);
        text("tick", doubled.substring(off, Math.min(doubled.length(), off + 36)));
        put("online", online);
        put("fallback", powered() && !online);
        put("evac", evac);
        put("pwr", powered());
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (id.equals("s_mode")) { ctl.set("s_mode", (ctl.get("s_mode") + 1) % 3); return "§eSign shows: " + new String[]{"the feed", "the next trains", "trains + feed"}[ctl.get("s_mode")]; }
        return null;
    }

    @Override protected void save(NBTTagCompound t) { }
    @Override protected void load(NBTTagCompound t) { }
}
