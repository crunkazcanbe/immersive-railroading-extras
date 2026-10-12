package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.RailProps;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Network performance analytics (her ChatGPT list #77): on-time departures, punctuality, average and worst delay,
 * route utilisation per line, failed journeys, energy drawn and regenerated, breakdowns and other incidents, freight
 * delivered, money earned, distance run - counted per in-game day, 14 days kept.
 * Punctuality needs a timetable to be late against: each stop-to-stop run is booked at its best observed time plus a
 * 15 % recovery allowance (how real timetables are padded), so a run is late by however much it took longer than
 * that, delays held for incidents included. Also stores the scenario snapshot (#80) and the reports (#79).
 */
public final class Analytics extends WorldSavedData {
    private static final String NAME = "irextras_ops";

    /** day number -> counters */
    final TreeMap<Integer, Map<String, Double>> days = new TreeMap<>();
    /** booked stop-to-stop run "from>to" -> ticks (best seen x 1.15) */
    final Map<String, Long> booked = new HashMap<>();
    /** reports (#79): newest first, each = lines; kind "D" daily / "W" weekly / "N" on demand */
    final List<String[]> reports = new ArrayList<>();
    int lastDay = -1;
    /** scenario snapshot (#80) */
    public NBTTagCompound snapshot = new NBTTagCompound();
    /** historical trends (next-48 #42): per world-clock hour (1000 ticks, a week kept), per week (52 kept) and per season
     *  (24 days, 8 kept) - the same counters as the days, so any period can be compared with any other */
    final TreeMap<Long, Map<String, Double>> hours = new TreeMap<>();
    final TreeMap<Integer, Map<String, Double>> weeks = new TreeMap<>(), seasons = new TreeMap<>();
    private final Map<String, Double> seen = new HashMap<>();
    private int seenDay = -1, maxAlarm;
    public static final int SEASON_DAYS = 24;

    public Analytics() { super(NAME); }
    public Analytics(String n) { super(n); }

    public static Analytics get(World w) {
        MapStorage s = w.getPerWorldStorage();
        Analytics d = (Analytics) s.getOrLoadData(Analytics.class, NAME);
        if (d == null) { d = new Analytics(); s.setData(NAME, d); }
        return d;
    }

    public static int day(World w) { return (int) (w.getWorldTime() / 24000L); }

    public Map<String, Double> today(World w) { return days.computeIfAbsent(day(w), k -> new HashMap<>()); }

    public static void count(World w, String key, double v) {
        Analytics a = get(w);
        a.today(w).merge(key, v, Double::sum);
        a.markDirty();
    }

    // ---- live counting (once a second) ------------------------------------------------------------------------
    private static final class Dep { boolean dwelling; long station = Long.MIN_VALUE, lastStation = Long.MIN_VALUE, lastDepart; int delivered = -1; long earned = -1; long heldSince; boolean failed; }
    private static final Map<UUID, Dep> DEP = new HashMap<>();

    static void tick(World w, long now) {
        Analytics a = get(w);
        int d = day(w);
        if (a.lastDay >= 0 && d != a.lastDay) { Reports.dayEnded(w, a, a.lastDay); a.rollUp(a.lastDay); }
        a.lastDay = d;
        Map<String, Double> t = a.today(w);
        t.merge("sec", 1.0, Double::sum);
        RailwayData data = RailwayData.get(w);
        for (RailwayData.AutoTrain at : data.trains().values()) {
            Autopilot.Run run = Autopilot.run(at.loco);
            Dep dp = DEP.computeIfAbsent(at.loco, k -> new Dep());
            String line = at.line == null || at.line.isEmpty() ? "(no line)" : at.line;
            t.merge("all:" + line, 1.0, Double::sum);
            if (run != null && Math.abs(run.speedKmh) > 1) t.merge("run:" + line, 1.0, Double::sum);
            if (run != null) {
                if (run.dwelling && !dp.dwelling) dp.station = run.dwellStation;
                if (!run.dwelling && dp.dwelling && dp.station != Long.MIN_VALUE) {
                    double late = departed(a, t, dp, now);
                    t.merge("dep:" + line, 1.0, Double::sum);                       // per line, for SLAs (admin/Commercial, #114)
                    if (late <= IncidentConfig.onTimeSeconds) t.merge("ontime:" + line, 1.0, Double::sum);
                }
                dp.dwelling = run.dwelling;
            }
            if (dp.delivered >= 0 && at.delivered > dp.delivered) t.merge("freight", (double) (at.delivered - dp.delivered), Double::sum);
            if (dp.earned >= 0 && at.earned > dp.earned) t.merge("earned", (double) (at.earned - dp.earned), Double::sum);
            dp.delivered = at.delivered; dp.earned = at.earned;
            // failed journeys: held by an incident 5 minutes or more counts once
            if (Disruptions.isHeld(w.provider.getDimension(), at.loco)) {
                if (dp.heldSince == 0) dp.heldSince = now;
                t.merge("held:" + line, 1.0, Double::sum);                          // availability per line (#114)
                if (!dp.failed && now - dp.heldSince > 6000) { dp.failed = true; t.merge("failed", 1.0, Double::sum); }
            } else { dp.heldSince = 0; dp.failed = false; }
        }
        DEP.keySet().removeIf(u -> !data.trains().containsKey(u));
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
            RailProps.Props pr = RailProps.get(w).peek(l.getUUID());
            if (pr != null) { t.merge("kwh", pr.kwDraw / 3600.0, Double::sum); t.merge("regen", pr.kwRegen / 3600.0, Double::sum); }
            t.merge("km", Math.abs(l.getCurrentSpeed().metric()) / 3600.0, Double::sum);
        }
        while (a.days.size() > 14) a.days.pollFirstEntry();
        a.trend(w, t, d);
        a.markDirty();
    }

    /** #42: what changed in today's counters this second goes into the current hour; new alarms count as faults */
    private void trend(World w, Map<String, Double> t, int d) {
        try {
            for (com.dogpound.railmap.alarm.AlarmManager.Alarm al : com.dogpound.railmap.alarm.AlarmManager.get(w).all())
                if (al.id > maxAlarm) { if (maxAlarm > 0 && al.sev != com.dogpound.railmap.alarm.AlarmManager.Sev.INFO) t.merge("faults", 1.0, Double::sum); maxAlarm = al.id; }
        } catch (RuntimeException ignored) { }
        if (d != seenDay) { boolean first = seenDay == -1; seen.clear(); seenDay = d; if (first) { seen.putAll(t); return; } }   // after a load: don't pour the whole day into this hour
        Map<String, Double> h = hours.computeIfAbsent(w.getWorldTime() / 1000L, k -> new HashMap<>());
        for (Map.Entry<String, Double> e : t.entrySet()) {
            double was = seen.getOrDefault(e.getKey(), 0.0), now = e.getValue();
            if (e.getKey().equals("maxDelay")) { if (now > was) h.put("maxDelay", Math.max(v(h, "maxDelay"), now)); }
            else if (now > was) h.merge(e.getKey(), now - was, Double::sum);
            seen.put(e.getKey(), now);
        }
        while (hours.size() > 24 * 7) hours.pollFirstEntry();
    }

    /** #42: a finished day goes into its week and its season */
    private void rollUp(int day) {
        Map<String, Double> m = days.get(day);
        if (m == null) return;
        weeks.put(day / 7, merge(Arrays.asList(weeks.getOrDefault(day / 7, new HashMap<>()), m)));
        seasons.put(day / SEASON_DAYS, merge(Arrays.asList(seasons.getOrDefault(day / SEASON_DAYS, new HashMap<>()), m)));
        while (weeks.size() > 52) weeks.pollFirstEntry();
        while (seasons.size() > 8) seasons.pollFirstEntry();
    }

    public TreeMap<Long, Map<String, Double>> hours() { return hours; }
    public TreeMap<Integer, Map<String, Double>> weeks() { return weeks; }
    public TreeMap<Integer, Map<String, Double>> seasons() { return seasons; }
    public Map<String, Long> booked() { return booked; }

    private static double departed(Analytics a, Map<String, Double> t, Dep dp, long now) {
        t.merge("dep", 1.0, Double::sum);
        long delay = 0;
        if (dp.lastStation != Long.MIN_VALUE && dp.lastStation != dp.station) {
            String seg = dp.lastStation + ">" + dp.station;
            long actual = now - dp.lastDepart;
            Long b = a.booked.get(seg);
            long best = b == null ? Long.MAX_VALUE : Math.round(b / 1.15);
            if (actual > 100 && actual < best) a.booked.put(seg, Math.round(actual * 1.15));
            if (b != null) delay = Math.max(0, actual - b);
        }
        double s = delay / 20.0;
        t.merge("delay", s, Double::sum);
        t.merge("maxDelay", 0.0, Double::sum);
        if (s > t.get("maxDelay")) t.put("maxDelay", s);
        if (s <= IncidentConfig.onTimeSeconds) t.merge("ontime", 1.0, Double::sum);
        dp.lastStation = dp.station;
        dp.lastDepart = now;
        return s;
    }

    // ---- reading ---------------------------------------------------------------------------------------------------
    public static double v(Map<String, Double> m, String k) { Double d = m.get(k); return d == null ? 0 : d; }

    /** the headline KPIs for one day (or a merged span) */
    public static List<String[]> kpis(Map<String, Double> m) {
        List<String[]> o = new ArrayList<>();
        double dep = v(m, "dep"), on = v(m, "ontime");
        o.add(new String[]{"Departures", String.format(Locale.ROOT, "%.0f", dep)});
        o.add(new String[]{"On time (<=" + IncidentConfig.onTimeSeconds + " s)", dep == 0 ? "-" : String.format(Locale.ROOT, "%.1f %%", on / dep * 100)});
        o.add(new String[]{"Average delay", dep == 0 ? "-" : String.format(Locale.ROOT, "%.0f s", v(m, "delay") / dep)});
        o.add(new String[]{"Worst delay", String.format(Locale.ROOT, "%.0f s", v(m, "maxDelay"))});
        o.add(new String[]{"Failed journeys", String.format(Locale.ROOT, "%.0f", v(m, "failed"))});
        o.add(new String[]{"Breakdowns", String.format(Locale.ROOT, "%.0f", v(m, "breakdowns"))});
        o.add(new String[]{"Derailments", String.format(Locale.ROOT, "%.0f", v(m, "wrecks"))});
        o.add(new String[]{"Fires", String.format(Locale.ROOT, "%.0f", v(m, "fires"))});
        o.add(new String[]{"Incidents (closed)", String.format(Locale.ROOT, "%.0f (%.0f)", v(m, "incidents"), v(m, "closed"))});
        o.add(new String[]{"Avg response", v(m, "respN") == 0 ? "-" : String.format(Locale.ROOT, "%.0f s", v(m, "resp") / v(m, "respN"))});
        o.add(new String[]{"Avg reopening", v(m, "reopenN") == 0 ? "-" : String.format(Locale.ROOT, "%.0f s", v(m, "reopen") / v(m, "reopenN"))});
        o.add(new String[]{"Energy drawn", String.format(Locale.ROOT, "%.1f kWh", v(m, "kwh"))});
        o.add(new String[]{"Regenerated", String.format(Locale.ROOT, "%.1f kWh", v(m, "regen"))});
        o.add(new String[]{"Distance run", String.format(Locale.ROOT, "%.1f km", v(m, "km"))});
        o.add(new String[]{"Freight delivered", String.format(Locale.ROOT, "%.0f", v(m, "freight"))});
        o.add(new String[]{"Earned", String.format(Locale.ROOT, "$%.0f", v(m, "earned"))});
        o.add(new String[]{"Cancellations announced", String.format(Locale.ROOT, "%.0f", v(m, "cancel"))});
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (!e.getKey().startsWith("all:")) continue;
            String line = e.getKey().substring(4);
            o.add(new String[]{"Line " + line + " utilisation", String.format(Locale.ROOT, "%.0f %% moving, %.1f trains", v(m, "run:" + line) / Math.max(1, e.getValue()) * 100,
                    e.getValue() / Math.max(1, Math.min(86400, secondsOf(m))))});
        }
        return o;
    }

    private static double secondsOf(Map<String, Double> m) { return Math.max(1, v(m, "sec")); }

    public static Map<String, Double> merge(Collection<Map<String, Double>> ds) {
        Map<String, Double> o = new HashMap<>();
        for (Map<String, Double> d : ds)
            for (Map.Entry<String, Double> e : d.entrySet()) {
                if (e.getKey().equals("maxDelay")) o.put("maxDelay", Math.max(v(o, "maxDelay"), e.getValue()));
                else o.merge(e.getKey(), e.getValue(), Double::sum);
            }
        return o;
    }

    public TreeMap<Integer, Map<String, Double>> days() { return days; }
    public List<String[]> reports() { return reports; }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        days.clear();
        NBTTagCompound d = t.getCompoundTag("days");
        for (String k : d.getKeySet()) {
            Map<String, Double> m = new HashMap<>();
            NBTTagCompound c = d.getCompoundTag(k);
            for (String kk : c.getKeySet()) m.put(kk, c.getDouble(kk));
            try { days.put(Integer.parseInt(k), m); } catch (NumberFormatException ignored) { }
        }
        booked.clear();
        NBTTagCompound b = t.getCompoundTag("booked");
        for (String k : b.getKeySet()) booked.put(k, b.getLong(k));
        reports.clear();
        NBTTagList r = t.getTagList("reports", 10);
        for (int i = 0; i < r.tagCount(); i++) {
            List<String> l = IncidentLog.list(r.getCompoundTagAt(i), "l");
            reports.add(l.toArray(new String[0]));
        }
        lastDay = t.hasKey("lastDay") ? t.getInteger("lastDay") : -1;
        snapshot = t.getCompoundTag("snap");
        hours.clear(); weeks.clear(); seasons.clear();
        NBTTagCompound hh = t.getCompoundTag("hours");
        for (String k : hh.getKeySet()) try { hours.put(Long.parseLong(k), readMap(hh.getCompoundTag(k))); } catch (NumberFormatException ignored) { }
        NBTTagCompound ww = t.getCompoundTag("weeks");
        for (String k : ww.getKeySet()) try { weeks.put(Integer.parseInt(k), readMap(ww.getCompoundTag(k))); } catch (NumberFormatException ignored) { }
        NBTTagCompound ss = t.getCompoundTag("seasons");
        for (String k : ss.getKeySet()) try { seasons.put(Integer.parseInt(k), readMap(ss.getCompoundTag(k))); } catch (NumberFormatException ignored) { }
        maxAlarm = t.getInteger("maxAlarm");
    }

    private static Map<String, Double> readMap(NBTTagCompound c) { Map<String, Double> m = new HashMap<>(); for (String k : c.getKeySet()) m.put(k, c.getDouble(k)); return m; }
    private static NBTTagCompound writeMap(Map<String, Double> m) { NBTTagCompound c = new NBTTagCompound(); for (Map.Entry<String, Double> e : m.entrySet()) c.setDouble(e.getKey(), e.getValue()); return c; }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagCompound d = new NBTTagCompound();
        for (Map.Entry<Integer, Map<String, Double>> e : days.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            for (Map.Entry<String, Double> kv : e.getValue().entrySet()) c.setDouble(kv.getKey(), kv.getValue());
            d.setTag(String.valueOf(e.getKey()), c);
        }
        t.setTag("days", d);
        NBTTagCompound b = new NBTTagCompound();
        for (Map.Entry<String, Long> e : booked.entrySet()) b.setLong(e.getKey(), e.getValue());
        t.setTag("booked", b);
        NBTTagList r = new NBTTagList();
        for (String[] rep : reports) { NBTTagCompound c = new NBTTagCompound(); c.setTag("l", IncidentLog.strings(Arrays.asList(rep))); r.appendTag(c); }
        t.setTag("reports", r);
        t.setInteger("lastDay", lastDay);
        t.setTag("snap", snapshot);
        NBTTagCompound hh = new NBTTagCompound(), ww = new NBTTagCompound(), ss = new NBTTagCompound();
        for (Map.Entry<Long, Map<String, Double>> e : hours.entrySet()) hh.setTag(String.valueOf(e.getKey()), writeMap(e.getValue()));
        for (Map.Entry<Integer, Map<String, Double>> e : weeks.entrySet()) ww.setTag(String.valueOf(e.getKey()), writeMap(e.getValue()));
        for (Map.Entry<Integer, Map<String, Double>> e : seasons.entrySet()) ss.setTag(String.valueOf(e.getKey()), writeMap(e.getValue()));
        t.setTag("hours", hh); t.setTag("weeks", ww); t.setTag("seasons", ss);
        t.setInteger("maxAlarm", maxAlarm);
        return t;
    }
}
