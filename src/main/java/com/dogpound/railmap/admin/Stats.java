package com.dogpound.railmap.admin;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.incident.IncidentLog;
import com.dogpound.railmap.server.WearData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Persistent railway statistics (her ChatGPT list #116). The incident analytics (#77) keep 14 days of detail; this
 * keeps the long view without holding every event: each finished day is rolled into a compact daily row (120 days
 * kept), a 30-day month row (kept for ever) and all-time totals. Traffic (departures, train-km, freight), punctuality,
 * revenue (every company's income), passengers, energy, incidents, alarms and maintenance work orders.
 */
public final class Stats extends WorldSavedData {
    private static final String NAME = "irextras_stats";
    /** the counters kept long-term (per-line departures "dep:LINE" are kept too) */
    public static final String[] CORE = {"dep", "ontime", "delay", "failed", "breakdowns", "wrecks", "fires", "incidents", "kwh", "regen", "km", "freight",
            "earned", "cancel", "revenue", "riders", "workorders", "alarms", "serviced"};
    public static final int KEEP_DAYS = 120;

    final TreeMap<Integer, Map<String, Double>> daily = new TreeMap<>(), monthly = new TreeMap<>();
    final Map<String, Double> total = new HashMap<>();
    /** this mod's own counters for days still in progress */
    final TreeMap<Integer, Map<String, Double>> extra = new TreeMap<>();
    int archived = -1;
    long lastSample = -1;
    private double ridersSeen = -1;

    public Stats() { super(NAME); }
    public Stats(String n) { super(n); }

    public static Stats get(World w) {
        MapStorage s = w.getPerWorldStorage();
        Stats d = (Stats) s.getOrLoadData(Stats.class, NAME);
        if (d == null) { d = new Stats(); s.setData(NAME, d); }
        return d;
    }

    /** once a minute: count what the analytics don't, and archive finished days */
    void sample(World w) {
        long now = w.getTotalWorldTime();
        long from = lastSample < 0 ? now : lastSample;
        lastSample = now;
        Map<String, Double> x = extra.computeIfAbsent(Analytics.day(w), k -> new HashMap<>());
        double rev = 0;
        for (Company c : CompanyData.get(w).all())
            for (Company.Entry e : c.ledger)
                if (e.tick > from && e.tick <= now && e.kind != null && e.kind.income && e.kind != Company.Kind.DEPOSIT && e.kind != Company.Kind.LOAN) rev += e.amount / 100.0;
        if (rev > 0) x.merge("revenue", rev, Double::sum);
        double riders = 0;
        for (PassengerData.LineStat l : PassengerData.get(w).lines()) riders += l.ridersDay;
        if (ridersSeen >= 0) x.merge("riders", riders >= ridersSeen ? riders - ridersSeen : riders, Double::sum);   // a drop = the passenger day rolled over
        ridersSeen = riders;
        int wo = 0;
        for (IncidentLog.WorkOrder o : IncidentLog.get(w).orders) if (o.done > from && o.done <= now) wo++;
        if (wo > 0) x.merge("workorders", (double) wo, Double::sum);
        int al = 0;
        for (AlarmManager.Alarm a : AlarmManager.get(w).all()) if (a.first > from && a.first <= now) al++;
        if (al > 0) x.merge("alarms", (double) al, Double::sum);
        int sv = 0;
        for (WearData.Record r : WearData.get(w).all().values()) if (r.servicedAt > from && r.servicedAt <= now) sv++;
        if (sv > 0) x.merge("serviced", (double) sv, Double::sum);
        archive(w);
        markDirty();
    }

    private void archive(World w) {
        int today = Analytics.day(w);
        for (Map.Entry<Integer, Map<String, Double>> e : Analytics.get(w).days().entrySet()) {
            int d = e.getKey();
            if (d >= today || d <= archived) continue;
            Map<String, Double> row = new HashMap<>();
            for (Map.Entry<String, Double> k : e.getValue().entrySet())
                if (Arrays.asList(CORE).contains(k.getKey()) || k.getKey().startsWith("dep:")) row.put(k.getKey(), k.getValue());
            Map<String, Double> x = extra.remove(d);
            if (x != null) row.putAll(x);
            put(d, row);
            archived = d;
        }
        extra.headMap(today - 14).clear();
    }

    private void put(int d, Map<String, Double> row) {
        daily.put(d, row);
        while (daily.size() > KEEP_DAYS) daily.pollFirstEntry();
        Map<String, Double> m = monthly.computeIfAbsent(d / 30, k -> new HashMap<>());
        for (Map.Entry<String, Double> k : row.entrySet()) {
            if (k.getKey().equals("maxDelay")) continue;
            m.merge(k.getKey(), k.getValue(), Double::sum);
            total.merge(k.getKey(), k.getValue(), Double::sum);
        }
        total.merge("days", 1.0, Double::sum);
        m.merge("days", 1.0, Double::sum);
    }

    // ---- reading ---------------------------------------------------------------------------------------------------
    /** the last n archived days merged (n <= 0: all time) */
    public Map<String, Double> span(int n) {
        if (n <= 0) return total;
        Map<String, Double> o = new HashMap<>();
        int k = 0;
        for (Map<String, Double> r : daily.descendingMap().values()) {
            if (k++ >= n) break;
            for (Map.Entry<String, Double> e : r.entrySet()) o.merge(e.getKey(), e.getValue(), Double::sum);
            o.merge("days", 1.0, Double::sum);
        }
        return o;
    }

    public static List<String> lines(Map<String, Double> m, String title) {
        List<String> o = new ArrayList<>();
        double dep = Analytics.v(m, "dep"), days = Math.max(1, Analytics.v(m, "days"));
        o.add(title + String.format(Locale.ROOT, " (%.0f days)", Analytics.v(m, "days")));
        o.add(String.format(Locale.ROOT, "Departures %.0f (%.1f/day), on time %s, avg delay %s", dep, dep / days,
                dep == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", Analytics.v(m, "ontime") / dep * 100), dep == 0 ? "-" : String.format(Locale.ROOT, "%.0f s", Analytics.v(m, "delay") / dep)));
        o.add(String.format(Locale.ROOT, "Train-km %.1f, freight %.0f, passengers %.0f", Analytics.v(m, "km"), Analytics.v(m, "freight"), Analytics.v(m, "riders")));
        o.add(String.format(Locale.ROOT, "Revenue $%.0f (company income), train earnings $%.0f", Analytics.v(m, "revenue"), Analytics.v(m, "earned")));
        o.add(String.format(Locale.ROOT, "Energy %.1f kWh drawn, %.1f kWh regenerated", Analytics.v(m, "kwh"), Analytics.v(m, "regen")));
        o.add(String.format(Locale.ROOT, "Incidents %.0f, failed journeys %.0f, breakdowns %.0f, derailments %.0f, fires %.0f", Analytics.v(m, "incidents"), Analytics.v(m, "failed"),
                Analytics.v(m, "breakdowns"), Analytics.v(m, "wrecks"), Analytics.v(m, "fires")));
        o.add(String.format(Locale.ROOT, "Alarms %.0f, work orders done %.0f, units serviced %.0f", Analytics.v(m, "alarms"), Analytics.v(m, "workorders"), Analytics.v(m, "serviced")));
        List<Map.Entry<String, Double>> ls = new ArrayList<>();
        for (Map.Entry<String, Double> e : m.entrySet()) if (e.getKey().startsWith("dep:")) ls.add(e);
        ls.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(5, ls.size()); i++) o.add(String.format(Locale.ROOT, "  Line %s: %.0f departures", ls.get(i).getKey().substring(4), ls.get(i).getValue()));
        return o;
    }

    /** every daily row and month as CSV, for spreadsheets / the website */
    public File export(World w) throws IOException {
        File dir = new File(w.getSaveHandler().getWorldDirectory(), "data/irextras_reports");
        dir.mkdirs();
        File f = new File(dir, "railway-stats-dim" + w.provider.getDimension() + ".csv");
        StringBuilder b = new StringBuilder("period,").append(String.join(",", CORE)).append('\n');
        for (Map.Entry<Integer, Map<String, Double>> e : daily.entrySet()) row(b, "day " + e.getKey(), e.getValue());
        for (Map.Entry<Integer, Map<String, Double>> e : monthly.entrySet()) row(b, "month " + e.getKey(), e.getValue());
        row(b, "all time", total);
        Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static void row(StringBuilder b, String p, Map<String, Double> m) {
        b.append(p);
        for (String k : CORE) b.append(',').append(String.format(Locale.ROOT, "%.2f", Analytics.v(m, k)));
        b.append('\n');
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    private static NBTTagCompound wm(Map<String, Double> m) { NBTTagCompound t = new NBTTagCompound(); for (Map.Entry<String, Double> e : m.entrySet()) t.setDouble(e.getKey(), e.getValue()); return t; }
    private static Map<String, Double> rm(NBTTagCompound t) { Map<String, Double> m = new HashMap<>(); for (String k : t.getKeySet()) m.put(k, t.getDouble(k)); return m; }
    private static NBTTagCompound wt(TreeMap<Integer, Map<String, Double>> m) { NBTTagCompound t = new NBTTagCompound(); for (Map.Entry<Integer, Map<String, Double>> e : m.entrySet()) t.setTag(String.valueOf(e.getKey()), wm(e.getValue())); return t; }
    private static void rt(NBTTagCompound t, TreeMap<Integer, Map<String, Double>> m) { m.clear(); for (String k : t.getKeySet()) try { m.put(Integer.parseInt(k), rm(t.getCompoundTag(k))); } catch (NumberFormatException ignored) { } }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        rt(t.getCompoundTag("daily"), daily);
        rt(t.getCompoundTag("monthly"), monthly);
        rt(t.getCompoundTag("extra"), extra);
        total.clear();
        total.putAll(rm(t.getCompoundTag("total")));
        archived = t.hasKey("archived") ? t.getInteger("archived") : -1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setTag("daily", wt(daily));
        t.setTag("monthly", wt(monthly));
        t.setTag("extra", wt(extra));
        t.setTag("total", wm(total));
        t.setInteger("archived", archived);
        return t;
    }
}
