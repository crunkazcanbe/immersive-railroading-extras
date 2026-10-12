package com.dogpound.railmap.stn;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;

import com.dogpound.railmap.incident.Announcer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * Interchange Board (ChatGPT batch 3 #28): a station's connection management panel. FROM / TO pick a pair of lines
 * calling here, WALK - / + set how many minutes it takes to walk between their platforms, the STEP-FREE key routes the
 * pair through the lift (+2 min). The six rows show the next connections live, each with a MAKE / TIGHT / MISS lamp.
 * HOLD (OFF / 2 / 5 MIN) holds a dwelling train for a late connection; ANNOUNCE tells arriving passengers where to
 * change and how long to allow. 12 V.
 */
public class TileInterchangeBoard extends StnPanel {
    /** "A>B" -> {walk minutes, step-free} */
    private final Map<String, int[]> pairs = new HashMap<>();
    private int fromI, toI = 1;
    private final Map<String, Long> held = new HashMap<>();
    private final Map<UUID, Long> announced = new HashMap<>();
    private String station = "";
    private long stationKey;
    private int holds;

    @Override public String wiredName() { return "Interchange board"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : !mainsOk() ? "NO MAINS" : station.isEmpty() ? "no station near" : station + " · " + lines().size() + " lines · " + holds + " holds today"; }

    public String stationName() { return station; }

    List<String> lines() { return stationKey == 0 ? Collections.emptyList() : Interchange.lines(world, stationKey); }

    private static String key(String a, String b) { return a + ">" + b; }

    /** walking minutes a -> b (the reverse pair if only that one was set) */
    public int walk(String a, String b) {
        int[] p = pairs.get(key(a, b));
        if (p == null) p = pairs.get(key(b, a));
        if (p == null) return StnConfig.defaultWalkMinutes;
        return p[0] + (p[1] == 1 ? 2 : 0);
    }

    private int[] sel() {
        List<String> l = lines();
        if (l.size() < 2) return null;
        String a = l.get(Math.floorMod(fromI, l.size())), b = l.get(Math.floorMod(toI, l.size()));
        if (a.equals(b)) return null;
        return pairs.computeIfAbsent(key(a, b), k -> new int[]{StnConfig.defaultWalkMinutes, 0});
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        List<String> l = lines();
        switch (id) {
            case "from" -> { fromI++; if (l.size() > 1 && Math.floorMod(fromI, l.size()) == Math.floorMod(toI, l.size())) fromI++; return l.size() < 2 ? "§7Fewer than two lines call here" : "§eFrom the " + l.get(Math.floorMod(fromI, l.size())) + " line"; }
            case "to" -> { toI++; if (l.size() > 1 && Math.floorMod(fromI, l.size()) == Math.floorMod(toI, l.size())) toI++; return l.size() < 2 ? "§7Fewer than two lines call here" : "§eTo the " + l.get(Math.floorMod(toI, l.size())) + " line"; }
            case "wminus", "wplus" -> {
                int[] p = sel();
                if (p == null) return "§7Pick two different lines first (FROM / TO)";
                p[0] = Math.max(0, Math.min(30, p[0] + (id.equals("wplus") ? 1 : -1)));
                markDirty();
                return "§eWalking time " + p[0] + " min" + (p[1] == 1 ? " + 2 min by the lift" : "");
            }
            case "annnow" -> { int n = announce(true); return n == 0 ? "§7No train standing here with connections to announce" : "§aAnnounced connections for " + n + " train(s)"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("k_sf")) {
            int[] p = sel();
            if (p == null) return "§7Pick two different lines first (FROM / TO)";
            p[1] = p[1] == 1 ? 0 : 1;
            ctl.set(id, p[1]);
            markDirty();
            return p[1] == 1 ? "§eStep-free route via the lift (+2 min)" : "§eStairs route";
        }
        return null;
    }

    @Override
    protected void work() {
        if (age % 40 != 7) return;
        Map.Entry<Long, String> st = StnData.station(world, pos, 96);
        station = st == null ? "" : st.getValue();
        stationKey = st == null ? 0 : st.getKey();
        List<String> l = lines();
        String a = l.size() < 2 ? "-" : l.get(Math.floorMod(fromI, l.size())), b = l.size() < 2 ? "-" : l.get(Math.floorMod(toI, l.size()));
        int[] p = l.size() < 2 || a.equals(b) ? null : pairs.get(key(a, b));
        ctl.set("k_sf", p != null && p[1] == 1 ? 1 : 0);
        text("stn", station.isEmpty() ? "NO STATION" : StnUtil.up(station));
        text("pair", StnUtil.cut(StnUtil.up(a), 10) + " > " + StnUtil.cut(StnUtil.up(b), 10));
        text("walk", "WALK " + (l.size() < 2 ? "-" : walk(a, b)) + " MIN" + (p != null && p[1] == 1 ? " LIFT" : ""));
        List<Interchange.Conn> cs = live() && stationKey != 0 ? Interchange.connections(world, stationKey, station) : Collections.emptyList();
        boolean risk = false, anyHeld = false;
        long now = world.getTotalWorldTime();
        int holdMin = new int[]{0, 2, 5}[Math.floorMod(ctl.get("s_hold"), 3)];
        for (int i = 0; i < 6; i++) {
            Interchange.Conn c = i < cs.size() ? cs.get(i) : null;
            text("c" + (i + 1), c == null ? "" : String.format(Locale.ROOT, "%s %dM > %s %dM W%d %s", StnUtil.cut(StnUtil.up(c.arr.line), 6), Math.round(c.arrMin),
                    StnUtil.cut(StnUtil.up(c.dep.line), 6), Math.round(c.depMin), c.walk, c.status()));
            put("c" + (i + 1) + "g", c != null && c.status().equals("MAKE"));
            put("c" + (i + 1) + "a", c != null && c.status().equals("TIGHT"));
            put("c" + (i + 1) + "r", c != null && c.status().equals("MISS"));
            if (c != null && !c.status().equals("MAKE")) risk = true;
        }
        // hold for connections: a dwelling train the arriving one would just miss
        if (live() && holdMin > 0)
            for (Interchange.Conn c : cs) {
                if (!c.dep.dwelling || c.margin >= 0 || -c.margin > holdMin) continue;
                String k = c.dep.loco + "|" + c.arr.loco;
                if (held.containsKey(k)) { anyHeld = true; continue; }
                int sec = (int) Math.min(StnConfig.maxConnectionHoldSeconds, Math.ceil(-c.margin * 60) + 20);
                if (sec <= 0 || !Autopilot.adjustDwell(c.dep.loco, now, sec)) continue;
                held.put(k, now);
                holds++;
                anyHeld = true;
                Recorder.event(world, "Connection held at " + station + ": " + c.dep.train + " (" + c.dep.line + ") waits " + sec + " s for " + c.arr.train + " (" + c.arr.line + ", walk " + c.walk + " min)");
                Announcer.say(world, stationKey, station, "CONNECT", "The " + c.dep.line + " service is being held for passengers from the " + c.arr.line + " service");
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "connection_held", "station", station, "seconds", sec);
            }
        held.values().removeIf(t -> now - t > 12000);
        if (ctl.get("k_ann") == 1 && live()) announce(false);
        put("pwr", powered()); put("riskl", risk); put("heldl", anyHeld); put("annl", ctl.get("k_ann") == 1);
        put("nlines", l.size());
        text("holds", holds + " HELD");
    }

    /** "change here for ..." for each train standing here (once per stop unless forced); returns trains announced */
    private int announce(boolean force) {
        if (stationKey == 0) return 0;
        int n = 0;
        long now = world.getTotalWorldTime();
        for (RailwayData.AutoTrain a : RailwayData.get(world).trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r == null || !r.dwelling || r.dwellStation != stationKey || a.line.isEmpty()) continue;
            Long last = announced.get(a.loco);
            if (!force && last != null && now - last < 2400) continue;
            List<String> parts = new ArrayList<>();
            for (String other : lines()) if (!other.equals(a.line)) {
                int[] p = pairs.get(key(a.line, other));
                parts.add("the " + other + " line, allow " + walk(a.line, other) + " minutes" + (p != null && p[1] == 1 ? " using the lift" : ""));
            }
            if (parts.isEmpty()) continue;
            announced.put(a.loco, now);
            Announcer.say(world, stationKey, station, "CONNECT", "This is " + station + ". Change here for " + String.join(", and for ", parts) + ".");
            n++;
        }
        announced.values().removeIf(t -> now - t > 24000);
        return n;
    }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        NBTTagCompound p = new NBTTagCompound();
        for (Map.Entry<String, int[]> e : pairs.entrySet()) p.setIntArray(e.getKey(), e.getValue());
        t.setTag("pairs", p);
        t.setInteger("fromI", fromI); t.setInteger("toI", toI); t.setInteger("holds", holds);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        pairs.clear();
        NBTTagCompound p = t.getCompoundTag("pairs");
        for (String k : p.getKeySet()) { int[] v = p.getIntArray(k); if (v.length >= 2) pairs.put(k, v); }
        fromI = t.getInteger("fromI"); toI = t.hasKey("toI") ? t.getInteger("toI") : 1; holds = t.getInteger("holds");
    }

    // ---- console / command ----------------------------------------------------------------------------------------------
    public Map<String, int[]> pairs() { return pairs; }
}
