package com.dogpound.railmap.incident;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Scheduled reports (her ChatGPT list #79): at the end of every in-game day a daily report - finance, freight,
 * passenger service, maintenance, power and incidents - and every seventh day a weekly one. They are kept in the
 * world (console REPORTS page; the Incident Command Desk and the Maintenance Terminal print them as books) and, with
 * exportReports, written as JSON to <world>/data/irextras_reports/ for websites and spreadsheets.
 */
public final class Reports {
    private Reports() {}

    static void dayEnded(World w, Analytics a, int day) {
        Map<String, Double> m = a.days.getOrDefault(day, Collections.emptyMap());
        add(w, a, "D", "DAILY REPORT - DAY " + (day + 1), m, day);
        if ((day + 1) % 7 == 0) {
            List<Map<String, Double>> wk = new ArrayList<>();
            for (int d = day - 6; d <= day; d++) if (a.days.containsKey(d)) wk.add(a.days.get(d));
            add(w, a, "W", "WEEKLY REPORT - DAYS " + (day - 5) + "-" + (day + 1), Analytics.merge(wk), day);
        }
    }

    /** a report for today so far (console GENERATE) */
    public static String[] now(World w) {
        Analytics a = Analytics.get(w);
        int d = Analytics.day(w);
        return add(w, a, "N", "REPORT SO FAR - DAY " + (d + 1), a.today(w), d);
    }

    private static String[] add(World w, Analytics a, String kind, String title, Map<String, Double> m, int day) {
        List<String> l = new ArrayList<>();
        l.add(kind + "|" + title);
        long span = kind.equals("W") ? 7 * 24000L : 24000L, now = w.getTotalWorldTime();
        l.add("== FINANCE ==");
        l.add(String.format(Locale.ROOT, "Earned by driverless trains: $%.0f", Analytics.v(m, "earned")));
        l.add("== FREIGHT ==");
        l.add(String.format(Locale.ROOT, "Loads delivered: %.0f", Analytics.v(m, "freight")));
        l.add("== PASSENGER SERVICE ==");
        double dep = Analytics.v(m, "dep");
        l.add(String.format(Locale.ROOT, "Departures %.0f, on time %s, average delay %s, worst %.0f s", dep,
                dep == 0 ? "-" : String.format(Locale.ROOT, "%.1f %%", Analytics.v(m, "ontime") / dep * 100),
                dep == 0 ? "-" : String.format(Locale.ROOT, "%.0f s", Analytics.v(m, "delay") / dep), Analytics.v(m, "maxDelay")));
        l.add(String.format(Locale.ROOT, "Failed journeys %.0f, cancellations announced %.0f", Analytics.v(m, "failed"), Analytics.v(m, "cancel")));
        l.add("== MAINTENANCE ==");
        int n = 0, worn = 0, sum = 0;
        for (TrainNode t : TrainTracker.latest(w)) if (t.wear >= 0) { n++; sum += t.wear; if (t.wear >= 70) worn++; }
        l.add(n == 0 ? "Fleet wear: no data" : String.format(Locale.ROOT, "Fleet wear: average %d %%, %d vehicle(s) at 70 %% or more", sum / n, worn));
        int open = 0, done = 0;
        for (IncidentLog.WorkOrder o : IncidentLog.get(w).orders) { if (o.done == 0) open++; else if (now - o.done < span) done++; }
        l.add("Work orders: " + open + " open, " + done + " completed");
        l.add(String.format(Locale.ROOT, "Breakdowns %.0f", Analytics.v(m, "breakdowns")));
        l.add("== POWER ==");
        l.add(String.format(Locale.ROOT, "Traction energy %.1f kWh, regenerated %.1f kWh (%.0f %%), distance %.1f km", Analytics.v(m, "kwh"), Analytics.v(m, "regen"),
                Analytics.v(m, "kwh") > 0 ? Analytics.v(m, "regen") / Analytics.v(m, "kwh") * 100 : 0, Analytics.v(m, "km")));
        int trips = 0;
        for (AlarmManager.Alarm al : AlarmManager.get(w).all()) if (al.cat == AlarmManager.Cat.POWER && now - al.first < span) trips++;
        l.add("Power trips logged: " + trips);
        l.add("== INCIDENTS ==");
        l.add(String.format(Locale.ROOT, "Opened %.0f, closed %.0f; derailments %.0f, fires %.0f", Analytics.v(m, "incidents"), Analytics.v(m, "closed"), Analytics.v(m, "wrecks"), Analytics.v(m, "fires")));
        l.add("Average response " + (Analytics.v(m, "respN") == 0 ? "-" : Math.round(Analytics.v(m, "resp") / Analytics.v(m, "respN")) + " s")
                + ", average reopening " + (Analytics.v(m, "reopenN") == 0 ? "-" : Math.round(Analytics.v(m, "reopen") / Analytics.v(m, "reopenN")) + " s"));
        for (IncidentLog.Rec r : IncidentLog.get(w).all()) if (now - r.opened < span)
            l.add("#" + r.id + " " + r.type.label + " L" + r.level + " " + r.stage.name() + ": " + r.what);
        String[] rep = l.toArray(new String[0]);
        if (!kind.equals("N")) {
            a.reports.add(0, rep);
            int dl = 0, wl = 0;
            for (Iterator<String[]> it = a.reports.iterator(); it.hasNext(); ) {
                String k = it.next()[0].substring(0, 1);
                if (k.equals("D") && ++dl > 14 || k.equals("W") && ++wl > 8) it.remove();
            }
            a.markDirty();
            if (IncidentConfig.exportReports) export(w, rep, (kind.equals("D") ? "day-" : "week-") + (day + 1));
            for (EntityPlayer p : w.playerEntities) p.sendMessage(new TextComponentString("§b📋 " + title + " is ready (Control Center, Ops > Reports)"));
        }
        return rep;
    }

    /** the data interface: one JSON file per report */
    private static void export(World w, String[] rep, String name) {
        try {
            File dir = new File(w.getSaveHandler().getWorldDirectory(), "data/irextras_reports");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            StringBuilder b = new StringBuilder("{\n  \"title\": ").append(q(rep[0].substring(2))).append(",\n  \"sections\": {");
            String sec = null;
            boolean firstSec = true, firstLine = true;
            for (int i = 1; i < rep.length; i++) {
                String s = rep[i];
                if (s.startsWith("== ")) {
                    if (sec != null) b.append("]");
                    sec = s.replace("=", "").trim();
                    b.append(firstSec ? "\n    " : ",\n    ").append(q(sec)).append(": [");
                    firstSec = false; firstLine = true;
                } else if (sec != null) { b.append(firstLine ? "" : ", ").append(q(s)); firstLine = false; }
            }
            if (sec != null) b.append("]");
            b.append("\n  }\n}\n");
            try (FileWriter f = new FileWriter(new File(dir, name + ".json"))) { f.write(b.toString()); }
        } catch (IOException | RuntimeException ignored) { }
    }

    private static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
