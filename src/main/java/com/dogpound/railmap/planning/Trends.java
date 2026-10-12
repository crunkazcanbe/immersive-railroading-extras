package com.dogpound.railmap.planning;

import com.dogpound.railmap.incident.Analytics;
import net.minecraft.world.World;

import java.util.*;

/**
 * Historical analytics + trend comparison (her "next 48" list #42), from the analytics' hourly, daily, weekly and
 * seasonal counters: punctuality, average / worst delay, faults (new alarms), energy drawn and regenerated, throughput
 * (departures, freight, km) and utilisation (share of train-time spent moving), each as a table per period, the
 * hour-of-day profile over the last week (rush hours, the worst hour), and PERIOD vs PERIOD comparisons - today vs
 * yesterday, this week vs last, this season vs last - with the change in each.
 */
public final class Trends {
    private Trends() {}

    public static final String[] SEASONS = {"Spring", "Summer", "Autumn", "Winter"};
    public static final String[] HEAD = {"Departures", "Punctual %", "Avg delay s", "Worst s", "Faults", "kWh", "Regen kWh", "Freight", "km", "Moving %"};

    static double v(Map<String, Double> m, String k) { return Analytics.v(m, k); }

    /** the ten figures of one period */
    public static double[] figures(Map<String, Double> m) {
        double dep = v(m, "dep"), all = 0, run = 0;
        for (Map.Entry<String, Double> e : m.entrySet()) { if (e.getKey().startsWith("all:")) all += e.getValue(); if (e.getKey().startsWith("run:")) run += e.getValue(); }
        return new double[]{dep, dep <= 0 ? Double.NaN : v(m, "ontime") * 100 / dep, dep <= 0 ? Double.NaN : v(m, "delay") / dep, v(m, "maxDelay"), v(m, "faults") + v(m, "breakdowns"),
                v(m, "kwh"), v(m, "regen"), v(m, "freight"), v(m, "km"), all <= 0 ? Double.NaN : run * 100 / all};
    }

    static String f(double d) { return Double.isNaN(d) ? "-" : Math.abs(d) >= 100 ? String.format(Locale.ROOT, "%.0f", d) : String.format(Locale.ROOT, "%.1f", d); }

    private static String[] row(String tag, String label, Map<String, Double> m) {
        double[] x = figures(m);
        String[] r = new String[2 + x.length];
        r[0] = tag; r[1] = label;
        for (int i = 0; i < x.length; i++) r[2 + i] = f(x[i]);
        return r;
    }

    public static void fill(World w, List<String[]> out) {
        Analytics an = Analytics.get(w);
        long hourNow = w.getWorldTime() / 1000L;
        // hours: the last 24
        for (Map.Entry<Long, Map<String, Double>> e : an.hours().descendingMap().entrySet()) {
            if (hourNow - e.getKey() >= 24) break;
            long h = e.getKey();
            out.add(row("HH", "Day " + (h / 24 + 1) + " " + String.format(Locale.ROOT, "%02d:00", (h + 6) % 24), e.getValue()));
        }
        // hour-of-day profile over the week
        Map<Integer, List<Map<String, Double>>> prof = new TreeMap<>();
        for (Map.Entry<Long, Map<String, Double>> e : an.hours().entrySet()) prof.computeIfAbsent((int) ((e.getKey() + 6) % 24), k -> new ArrayList<>()).add(e.getValue());
        double worst = -1;
        int worstH = -1;
        for (Map.Entry<Integer, List<Map<String, Double>>> e : prof.entrySet()) {
            Map<String, Double> m = Analytics.merge(e.getValue());
            String[] r = row("HP", String.format(Locale.ROOT, "%02d:00 (x%d)", e.getKey(), e.getValue().size()), m);
            out.add(r);
            double avg = figures(m)[2];
            if (!Double.isNaN(avg) && avg > worst) { worst = avg; worstH = e.getKey(); }
        }
        // days, weeks, seasons
        for (Map.Entry<Integer, Map<String, Double>> e : an.days().descendingMap().entrySet()) out.add(row("HD", "Day " + (e.getKey() + 1), e.getValue()));
        for (Map.Entry<Integer, Map<String, Double>> e : an.weeks().descendingMap().entrySet()) out.add(row("HW", "Week " + (e.getKey() + 1), e.getValue()));
        for (Map.Entry<Integer, Map<String, Double>> e : an.seasons().descendingMap().entrySet())
            out.add(row("HS", SEASONS[e.getKey() % 4] + " yr " + (e.getKey() / 4 + 1), e.getValue()));
        // comparisons
        int day = Analytics.day(w);
        compare(out, "Today vs yesterday", an.days().get(day), an.days().get(day - 1));
        compare(out, "This week vs last", weekSoFar(an, day), an.weeks().get(day / 7 - 1));
        Map<String, Double> season = new HashMap<>(an.seasons().getOrDefault(day / Analytics.SEASON_DAYS, new HashMap<>()));
        if (an.days().get(day) != null) season = Analytics.merge(Arrays.asList(season, an.days().get(day)));
        compare(out, "This season vs last", season, an.seasons().get(day / Analytics.SEASON_DAYS - 1));
        Map<String, Double> lastH = an.hours().get(hourNow), prevH = an.hours().get(hourNow - 1);
        compare(out, "This hour vs the one before", lastH, prevH);
        out.add(new String[]{"HN", worstH < 0 ? "-" : String.format(Locale.ROOT, "%02d:00", worstH), worst < 0 ? "-" : f(worst), SEASONS[(day / Analytics.SEASON_DAYS) % 4],
                String.valueOf(an.hours().size()), String.valueOf(an.weeks().size()), String.valueOf(an.seasons().size())});
    }

    private static Map<String, Double> weekSoFar(Analytics an, int day) {
        List<Map<String, Double>> l = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Double>> e : an.days().entrySet()) if (e.getKey() / 7 == day / 7) l.add(e.getValue());
        return l.isEmpty() ? null : Analytics.merge(l);
    }

    private static void compare(List<String[]> out, String what, Map<String, Double> a, Map<String, Double> b) {
        if (a == null || b == null) { out.add(new String[]{"HC", what, "not enough history yet"}); return; }
        double[] x = figures(a), y = figures(b);
        for (int i = 0; i < HEAD.length; i++) {
            double ch = Double.isNaN(x[i]) || Double.isNaN(y[i]) ? Double.NaN : y[i] == 0 ? (x[i] == 0 ? 0 : Double.NaN) : (x[i] - y[i]) / Math.abs(y[i]) * 100;
            boolean lowerBetter = i == 2 || i == 3 || i == 4 || i == 5;
            String good = Double.isNaN(ch) || Math.abs(ch) < 1 ? "0" : (ch < 0) == lowerBetter ? "+" : "-";
            out.add(new String[]{"HC", what, HEAD[i], f(x[i]), f(y[i]), Double.isNaN(ch) ? "-" : String.format(Locale.ROOT, "%+.0f %%", ch), good});
        }
    }
}
