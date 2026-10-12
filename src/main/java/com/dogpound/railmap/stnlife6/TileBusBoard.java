package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.road.EntityRoadVehicle;
import com.dogpound.railmap.road.RoadConfig;
import com.dogpound.railmap.stn.StnData;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Bus station departure board + timetable controller (batch 6c #11). It owns routes 1-4: a route is every bus bay
 * within LifeConfig.busReach whose ROUTE wheel says so, in STOP order. With a route's SERVICE key on it sends a bus
 * from the first bay every HEADWAY minutes (2/4/6/10/15, real time); the bus (road traffic, EntityServiceVehicle) drives
 * bay to bay, dwells, and terminates at the last bay. Out of anyone's sight no bus is spawned (counted as run
 * unobserved). The board shows the next departures (page 1), the selected route's stops (2) and its counts (3); each
 * bay's countdown is fed from here. SEND puts an extra bus out on the selected route now, RECALL brings them all in,
 * ANNOUNCE reads the next departures over the station PA. A route that can't run (fewer than 2 bays, a bay with no road
 * next to it) is a ROUTE FAULT alarm; a bus more than 3 minutes late is a recorder event. 12 V + mains.
 */
public class TileBusBoard extends LifeTile {
    static final int[] HEADWAY = {2, 4, 6, 10, 15};
    private final long[] next = new long[4];
    private final int[] sent = new int[4], lost = new int[4], unseen = new int[4], boarded = new int[4], onTime = new int[4], late = new int[4];
    private final Map<Integer, List<UUID>> live = new HashMap<>();
    private final Map<Integer, List<TileBusBay>> bays = new HashMap<>();
    private final boolean[] fault = new boolean[4];
    private int page;

    public static TileBusBoard boardFor(World w, BlockPos at) {
        TileBusBoard best = null;
        double bd = Double.MAX_VALUE;
        for (TileBusBoard b : StnUtil.near(w, TileBusBoard.class, at, LifeConfig.busReach)) {
            double d = b.getPos().distanceSq(at);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    int sel() { return ctl.get("s_sel"); }
    boolean service(int r) { return ctl.get("k_svc" + (r + 1)) == 1; }
    int headwayTicks(int r) { return HEADWAY[Math.floorMod(ctl.get("s_hw" + (r + 1)), HEADWAY.length)] * 1200; }
    static double bpt() { return RoadConfig.speed * EntityRoadVehicle.Kind.BUS.speedK; }

    @Override public String wiredName() { return "Bus departure board"; }
    @Override public String gearState() {
        if (!live()) return "NO SUPPLY";
        StringBuilder b = new StringBuilder();
        for (int r = 0; r < 4; r++) b.append("R").append(r + 1).append(service(r) ? fault[r] ? " FAULT" : " " + buses(r) + " out" : " off").append(r < 3 ? " · " : "");
        return b.toString();
    }
    @Override protected long load() { return 6; }

    int buses(int r) { return live.getOrDefault(r, Collections.emptyList()).size(); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("k_") || id.startsWith("s_")) return null;
        String d = dead();
        if (d != null) return d;
        int r = sel();
        switch (id) {
            case "pg" -> { page = (page + 1) % 3; refresh(); return "§7Page " + (page + 1) + " / 3"; }
            case "send" -> { String why = dispatch(r, true); refresh(); return why == null ? "§aExtra bus sent on route " + (r + 1) : "§c" + why; }
            case "recall" -> {
                int n = 0;
                for (EntityServiceVehicle v : buses(world, r)) { v.done = true; v.removePassengers(); v.setDead(); n++; }
                live.remove(r);
                Recorder.event(world, "Bus route " + (r + 1) + ": " + n + " bus(es) recalled to the depot by " + who(who));
                return "§e" + n + " route " + (r + 1) + " bus(es) recalled";
            }
            case "ann" -> {
                Map.Entry<Long, String> st = StnData.station(world, pos, 128);
                StringBuilder t = new StringBuilder("Bus departures: ");
                for (int k = 0; k < 4; k++) if (service(k) && !fault[k]) t.append("route ").append(k + 1).append(" to ").append(dest(k)).append(" in ").append(Math.max(1, (next[k] - world.getTotalWorldTime()) / 1200)).append(" minutes. ");
                if (st != null) com.dogpound.railmap.incident.Announcer.say(world, st.getKey(), st.getValue(), "CONNECT", t.toString());
                return "§a" + t;
            }
            default -> { return null; }
        }
    }

    // ---- the route engine -------------------------------------------------------------------------------------------
    List<EntityServiceVehicle> buses(World w, int r) {
        List<EntityServiceVehicle> o = new ArrayList<>();
        List<UUID> ids = live.getOrDefault(r, Collections.emptyList());
        if (ids.isEmpty()) return o;
        for (Entity e : w.loadedEntityList) if (e instanceof EntityServiceVehicle v && !v.isDead && ids.contains(v.getUniqueID())) o.add(v);
        return o;
    }

    String dest(int r) { List<TileBusBay> b = bays.getOrDefault(r, Collections.emptyList()); return b.isEmpty() ? "-" : b.get(b.size() - 1).stopName(); }

    private void scan() {
        bays.clear();
        for (TileBusBay b : StnUtil.near(world, TileBusBay.class, pos, LifeConfig.busReach)) {
            if (!b.inUse() || boardFor(world, b.getPos()) != this) continue;
            bays.computeIfAbsent(b.route() - 1, k -> new ArrayList<>()).add(b);
        }
        for (List<TileBusBay> l : bays.values()) l.sort(Comparator.comparingInt(TileBusBay::seq).thenComparing(b -> b.getPos().toLong()));
    }

    /** what stops route r cannot run, or null */
    String why(int r) {
        List<TileBusBay> b = bays.getOrDefault(r, Collections.emptyList());
        if (b.size() < 2) return "needs 2 bays in service (has " + b.size() + ")";
        for (TileBusBay x : b) if (x.road() == null) return "bay " + x.letter() + " at " + x.getPos().getX() + "," + x.getPos().getZ() + " has no road next to it";
        return null;
    }

    /** put a bus out on route r now; null = done, else why not */
    String dispatch(int r, boolean extra) {
        String w = why(r);
        if (w != null) return "Route " + (r + 1) + " can't run: " + w;
        List<TileBusBay> b = bays.get(r);
        BlockPos first = b.get(0).road();
        boolean seen = false;
        for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(first) < (double) RoadConfig.despawn * RoadConfig.despawn) seen = true;
        sent[r]++;
        if (!seen || !world.isBlockLoaded(first)) { unseen[r]++; return extra ? "nobody near the first bay to see it - ran unobserved" : null; }
        EntityServiceVehicle v = new EntityServiceVehicle(world, EntityRoadVehicle.Kind.BUS, r, pos, EntityServiceVehicle.BUS);
        v.route = r + 1;
        v.dispatched = world.getTotalWorldTime();
        for (TileBusBay x : b) { v.stops.add(x.road()); v.names.add(x.stopName()); }
        BlockPos second = b.get(1).road();
        v.setLocationAndAngles(first.getX() + 0.5, first.getY(), first.getZ() + 0.5, (float) Math.toDegrees(Math.atan2(-(second.getX() - first.getX()), second.getZ() - first.getZ())), 0);
        if (!world.spawnEntity(v)) return "the bus wouldn't fit on the road";
        live.computeIfAbsent(r, k -> new ArrayList<>()).add(v.getUniqueID());
        return null;
    }

    /** a bus pulled up at a bay: extra dwell ticks (a passenger REQUESTed it), punctuality */
    int arrived(EntityServiceVehicle v) {
        int r = v.route - 1;
        if (r < 0 || r > 3) return 0;
        List<TileBusBay> b = bays.getOrDefault(r, Collections.emptyList());
        long due = v.dispatched + travel(r, 0, v.stopIdx) * 20L;
        long lateS = (world.getTotalWorldTime() - due) / 20;
        if (lateS > 60) late[r]++; else onTime[r]++;
        if (lateS > 180) Recorder.event(world, "Bus route " + v.route + " " + lateS / 60 + " min late at " + v.stopName());
        if (v.stopIdx < b.size() && b.get(v.stopIdx).requested) return 200;
        return 0;
    }

    void terminated(EntityServiceVehicle v) { List<UUID> l = live.get(v.route - 1); if (l != null) l.remove(v.getUniqueID()); }

    void boarded(int route) { if (route >= 1 && route <= 4) boarded[route - 1]++; }

    /** seconds from stop a to stop b on route r (driving + dwells) */
    int travel(int r, int a, int b) {
        List<TileBusBay> l = bays.getOrDefault(r, Collections.emptyList());
        double d = 0;
        for (int i = a; i < b && i + 1 < l.size(); i++) d += Roads.run(l.get(i).getPos(), l.get(i + 1).getPos());
        return Roads.secs(d, bpt()) + Math.max(0, b - a) * LifeConfig.busDwell;
    }

    /** seconds until the next bus at stop k of route r, and whether it's standing there: {secs, at} */
    long[] eta(int r, int k) {
        long best = Long.MAX_VALUE, at = 0, nowT = world.getTotalWorldTime();
        for (EntityServiceVehicle v : buses(world, r)) {
            if (v.stopIdx > k) continue;
            if (v.stopIdx == k && v.atStop()) { at = 1; best = 0; break; }
            BlockPos t = v.target();
            double first = t == null ? 0 : Math.sqrt(v.getDistanceSq(t)) * 1.2;
            long s = Roads.secs(first, bpt()) + travel(r, v.stopIdx, k);
            best = Math.min(best, s);
        }
        if (service(r)) best = Math.min(best, Math.max(0, (next[r] - nowT) / 20) + travel(r, 0, k));
        return new long[]{best, at};
    }

    @Override
    protected void work() {
        if (age % 20 != 11) return;
        long now = world.getTotalWorldTime();
        if (!live()) return;
        scan();
        for (int r = 0; r < 4; r++) {
            // buses gone: terminated, recalled, or lost (despawned / no road path)
            List<UUID> ids = live.getOrDefault(r, new ArrayList<>());
            List<EntityServiceVehicle> here = buses(world, r);
            if (here.size() < ids.size()) {
                int gone = ids.size() - here.size();
                Set<UUID> keep = new HashSet<>();
                for (EntityServiceVehicle v : here) keep.add(v.getUniqueID());
                ids.retainAll(keep);
                lost[r] += gone;
                Recorder.event(world, "Bus route " + (r + 1) + ": " + gone + " bus(es) lost on the road (out of sight / no road path)");
            }
            String w = service(r) ? why(r) : null;
            String ak = "busroute:" + pos.toLong() + ":" + r;
            if (w != null && !fault[r]) { fault[r] = true; AlarmManager.get(world).raise(world, ak, AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR, "BUS ROUTE " + (r + 1) + " FAULT: " + w, pos, true); }
            else if (w == null && fault[r]) { fault[r] = false; AlarmManager.get(world).clear(ak); }
            if (!service(r)) { next[r] = 0; continue; }
            if (next[r] == 0 || next[r] > now + headwayTicks(r)) next[r] = now + 200;
            if (now >= next[r]) {
                next[r] += headwayTicks(r);
                if (next[r] < now) next[r] = now + headwayTicks(r);
                if (w == null) { String x = dispatch(r, false); if (x != null) Recorder.event(world, "Bus route " + (r + 1) + ": departure failed - " + x); }
            }
        }
        // the bays' countdowns
        for (Map.Entry<Integer, List<TileBusBay>> e : bays.entrySet()) {
            int r = e.getKey();
            List<TileBusBay> l = e.getValue();
            for (int k = 0; k < l.size(); k++) {
                TileBusBay b = l.get(k);
                if (k == l.size() - 1) { b.show("R" + (r + 1) + " TERMINUS", "BUSES TERMINATE", false, eta(r, k)[1] == 1); continue; }
                long[] t = eta(r, k);
                boolean on = service(r) && !fault[r];
                b.show(on ? "R" + (r + 1) + " " + StnUtil.up(StnUtil.cut(dest(r), 12)) : null, on ? (t[1] == 1 ? "AT BAY - BOARD NOW" : t[0] == Long.MAX_VALUE ? "-" : t[0] < 60 ? "DUE" : t[0] / 60 + " MIN") : fault[r] ? "ROUTE FAULT" : "", on && t[0] < 60, t[1] == 1);
            }
        }
        refresh();
    }

    private void refresh() {
        long now = world.getTotalWorldTime();
        text("title", page == 0 ? "BUS DEPARTURES" : page == 1 ? "ROUTE " + (sel() + 1) + " STOPS" : "ROUTE " + (sel() + 1) + " TODAY");
        text("clock", com.dogpound.railmap.alarm.Recorder.clock(world, now));
        List<String> rows = new ArrayList<>();
        if (page == 0) {
            for (int r = 0; r < 4; r++) {
                List<TileBusBay> l = bays.getOrDefault(r, Collections.emptyList());
                String bay = l.isEmpty() ? "-" : l.get(0).letter();
                String st = !service(r) ? "NOT IN SERVICE" : fault[r] ? "ROUTE FAULT" : Math.max(0, (next[r] - now) / 1200) + " MIN";
                rows.add(String.format(Locale.ROOT, "%d %-12s %s %s", r + 1, StnUtil.up(StnUtil.cut(dest(r), 12)), bay, st));
            }
            rows.add("BUSES OUT " + (buses(0) + buses(1) + buses(2) + buses(3)));
        } else if (page == 1) {
            List<TileBusBay> l = bays.getOrDefault(sel(), Collections.emptyList());
            for (int k = 0; k < Math.min(6, l.size()); k++) rows.add((k + 1) + " " + StnUtil.up(StnUtil.cut(l.get(k).stopName(), 20)) + " +" + travel(sel(), 0, k) / 60 + "M");
            if (l.isEmpty()) rows.add("NO BAYS ON THIS ROUTE");
        } else {
            int r = sel();
            rows.add("SENT " + sent[r] + " LOST " + lost[r]);
            rows.add("UNOBSERVED " + unseen[r]);
            rows.add("BOARDED " + boarded[r]);
            rows.add("ON TIME " + onTime[r] + " LATE " + late[r]);
            rows.add("HEADWAY " + headwayTicks(r) / 1200 + " MIN");
            String w = why(r);
            rows.add(w == null ? "ROUTE OK" : StnUtil.up(StnUtil.cut(w, 22)));
        }
        for (int i = 0; i < 6; i++) text("r" + (i + 1), i < rows.size() ? rows.get(i) : "");
        int out = 0, ot = 0, lt = 0;
        for (int r = 0; r < 4; r++) {
            put("l_run" + (r + 1), service(r) && buses(r) > 0);
            put("l_flt" + (r + 1), fault[r] ? 1 : 0);
            out += buses(r); ot += onTime[r]; lt += late[r];
        }
        put("g_buses", Math.min(8, out));
        put("g_punct", ot + lt == 0 ? 100 : ot * 100 / (ot + lt));
        text("status", String.format(Locale.ROOT, "SEL R%d  PAGE %d", sel() + 1, page + 1));
    }

    @Override
    protected void write(NBTTagCompound t) {
        t.setInteger("page", page);
        for (int r = 0; r < 4; r++) {
            t.setLong("n" + r, next[r]); t.setInteger("s" + r, sent[r]); t.setInteger("l" + r, lost[r]); t.setInteger("u" + r, unseen[r]);
            t.setInteger("b" + r, boarded[r]); t.setInteger("o" + r, onTime[r]); t.setInteger("t" + r, late[r]); t.setBoolean("f" + r, fault[r]);
        }
    }

    @Override
    protected void read(NBTTagCompound t) {
        page = t.getInteger("page");
        for (int r = 0; r < 4; r++) {
            next[r] = t.getLong("n" + r); sent[r] = t.getInteger("s" + r); lost[r] = t.getInteger("l" + r); unseen[r] = t.getInteger("u" + r);
            boarded[r] = t.getInteger("b" + r); onTime[r] = t.getInteger("o" + r); late[r] = t.getInteger("t" + r); fault[r] = t.getBoolean("f" + r);
        }
    }

    /** /irextras stnlife selftest: [] = pass */
    static List<String> selfTest() {
        List<String> f = new ArrayList<>();
        if (Roads.secs(0, 0.44) != 0) f.add("zero distance must take 0 s");
        if (Roads.secs(88, 0.44) != 10) f.add("88 blocks at 0.44 b/t must be 10 s, got " + Roads.secs(88, 0.44));
        if (Math.abs(Roads.run(new BlockPos(0, 0, 0), new BlockPos(30, 0, 40)) - 65) > 0.01) f.add("road run estimate 50 x 1.3");
        if (HEADWAY.length != 5 || HEADWAY[0] != 2) f.add("headway table");
        return f;
    }
}
