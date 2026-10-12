package com.dogpound.railmap.api;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Scriptable train orders (her ChatGPT list #101): reusable order TEMPLATES with parameters (origin, destination,
 * cargo, load percentage, departure window, anything else), assigned to driverless trains. The orders drive the
 * existing autopilot through its own SEND mode only - they say where to go next; the autopilot, signalling,
 * interlocking and protection still do the driving. Steps (separated by ';'):
 * <pre>
 * GOTO &lt;station&gt;          run to that stop and wait there
 * LOAD &lt;percent&gt; [cargo]   wait until every car is that full (and carries cargo whose name contains [cargo])
 * UNLOAD                   wait until the train is empty (or nothing more comes off for a minute)
 * WAIT &lt;seconds&gt;          wait
 * WINDOW hh:mm-hh:mm       wait for the departure window (game clock)
 * WAITCH &lt;n&gt;              wait for signal-box channel n to come on
 * SET &lt;n&gt; on|off          switch channel n
 * EMIT &lt;event&gt;            send a program event
 * REPEAT | END             go round again | finish (the train stays where it is)
 * </pre>
 * $name in a step is replaced by the job's parameter (origin=Harbor dest=Mine load=80 cargo=coal window=06:00-18:00).
 */
public final class TrainOrders extends WorldSavedData {
    private static final String NAME = "irextras_orders";
    public static final String[] KINDS = {"GOTO", "LOAD", "UNLOAD", "WAIT", "WINDOW", "WAITCH", "SET", "EMIT", "REPEAT", "END"};

    public static final class Job {
        public UUID loco;
        public String template = "", name = "", state = "assigned";
        public final Map<String, String> params = new LinkedHashMap<>();
        public int step, laps;
        public long stepSince, lastCargoChange;
        public int lastCargo = -1;
        public boolean issued;

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("loco", loco.toString()); t.setString("tpl", template); t.setString("name", name); t.setString("state", state);
            NBTTagCompound p = new NBTTagCompound();
            for (Map.Entry<String, String> e : params.entrySet()) p.setString(e.getKey(), e.getValue());
            t.setTag("p", p);
            t.setInteger("step", step); t.setInteger("laps", laps); t.setLong("since", stepSince); t.setBoolean("issued", issued);
            return t;
        }

        static Job read(NBTTagCompound t) {
            Job j = new Job();
            j.loco = UUID.fromString(t.getString("loco")); j.template = t.getString("tpl"); j.name = t.getString("name"); j.state = t.getString("state");
            NBTTagCompound p = t.getCompoundTag("p");
            for (String k : p.getKeySet()) j.params.put(k, p.getString(k));
            j.step = t.getInteger("step"); j.laps = t.getInteger("laps"); j.stepSince = t.getLong("since"); j.issued = t.getBoolean("issued");
            return j;
        }
    }

    public final Map<String, String> templates = new TreeMap<>();
    public final Map<UUID, Job> jobs = new LinkedHashMap<>();

    public TrainOrders() { super(NAME); }
    public TrainOrders(String n) { super(n); }

    public static TrainOrders get(World w) {
        MapStorage s = w.getPerWorldStorage();
        TrainOrders d = (TrainOrders) s.getOrLoadData(TrainOrders.class, NAME);
        if (d == null) {
            d = new TrainOrders();
            d.templates.put("freight_shuttle", "GOTO $origin; LOAD $load $cargo; WINDOW $window; GOTO $dest; UNLOAD; REPEAT");
            d.templates.put("one_way", "GOTO $origin; LOAD $load $cargo; GOTO $dest; UNLOAD; END");
            d.templates.put("timed_service", "GOTO $origin; WINDOW $window; GOTO $dest; WAIT 30; REPEAT");
            s.setData(NAME, d);
            d.markDirty();
        }
        return d;
    }

    /** the template split into steps (with the job's parameters filled in) */
    public static List<String[]> steps(String template, Map<String, String> params) {
        List<String[]> out = new ArrayList<>();
        for (String raw : template.split(";")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            if (params != null) for (Map.Entry<String, String> e : params.entrySet()) s = s.replace("$" + e.getKey(), e.getValue());
            s = s.replace("$load", "100").replace("$window", "00:00-24:00").replace("$cargo", "");   // defaults
            out.add(s.trim().split("\\s+", 3));
        }
        return out;
    }

    /** define (or replace) a template; returns null if fine, else what is wrong */
    public String define(World w, String name, String text, String who) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) return "template names are letters, digits, _ and - (max 32)";
        List<String[]> st = steps(text, null);
        if (st.isEmpty()) return "a template needs at least one step";
        for (String[] s : st) if (!Arrays.asList(KINDS).contains(s[0].toUpperCase(Locale.ROOT))) return "unknown step '" + s[0] + "' (" + String.join(" ", KINDS) + ")";
        String was = templates.put(name, text.trim());
        Audit.log(w, Audit.Src.API, who, null, "order template " + name, "define", was == null ? "" : "replaced", text, "ok");
        markDirty();
        return null;
    }

    public String assign(World w, UUID loco, String name, String template, Map<String, String> params, Audit.Src src, String who) {
        if (!templates.containsKey(template)) return "refused: no template '" + template + "' (" + String.join(", ", templates.keySet()) + ")";
        Locomotive l = Autopilot.find(w, loco);
        if (l == null) return "refused: that train is not loaded (orders need an Immersive Railroading locomotive)";
        Job j = new Job();
        j.loco = loco; j.template = template; j.name = name; j.params.putAll(params); j.stepSince = w.getTotalWorldTime();
        Job old = jobs.put(loco, j);
        Audit.log(w, src, who, null, "train " + name, "orders " + template, old == null ? "" : old.template, params.toString(), "ok");
        markDirty();
        return "ok: " + name + " runs '" + template + "' " + params;
    }

    public String cancel(World w, UUID loco, Audit.Src src, String who) {
        Job j = jobs.remove(loco);
        if (j == null) return "refused: that train has no orders";
        Audit.log(w, src, who, null, "train " + j.name, "cancel orders", j.template + " step " + (j.step + 1), "", "ok");
        markDirty();
        return "ok: orders cancelled for " + j.name + " (it stays driverless where it is)";
    }

    // ---- the executor (once a second) ----------------------------------------------------------------------------
    public void tick(World w) {
        if (jobs.isEmpty()) return;
        long now = w.getTotalWorldTime();
        for (Job j : new ArrayList<>(jobs.values())) {
            try {
                for (int guard = 0; guard < 16; guard++) if (!step(w, j, now)) break;
            } catch (RuntimeException | LinkageError e) {
                j.state = "error: " + e;
            }
        }
        markDirty();
    }

    /** carry out the current step; true = it finished at once (go straight on to the next) */
    private boolean step(World w, Job j, long now) {
        String tpl = templates.get(j.template);
        if (tpl == null) { j.state = "template '" + j.template + "' was deleted"; return false; }
        List<String[]> st = steps(tpl, j.params);
        if (j.step >= st.size()) { j.step = 0; j.laps++; }
        String[] s = st.get(j.step);
        String k = s[0].toUpperCase(Locale.ROOT), a1 = s.length > 1 ? s[1] : "", a2 = s.length > 2 ? s[2] : "";
        Locomotive loco = Autopilot.find(w, j.loco);
        if (loco == null) { j.state = "train not loaded - no one is near it"; return false; }
        String where = "step " + (j.step + 1) + "/" + st.size() + " " + String.join(" ", s);
        long waited = now - j.stepSince;
        boolean timeout = ApiConfig.orderStepTimeoutMinutes > 0 && waited > ApiConfig.orderStepTimeoutMinutes * 1200L;
        switch (k) {
            case "GOTO": {
                Long key = station(w, a1 + (a2.isEmpty() ? "" : " " + a2));
                if (key == null) { fail(w, j, "no station named '" + a1 + "'"); return false; }
                RailwayData data = RailwayData.get(w);
                RailwayData.AutoTrain a = data.setTrain(j.loco);
                if (!j.issued || a.mode != RailwayData.Mode.SEND || a.home != key) {
                    a.mode = RailwayData.Mode.SEND; a.home = key; a.calls.clear();
                    if (a.label.isEmpty()) a.label = j.name;
                    data.markDirty();
                    j.issued = true;
                    j.stepSince = now;                                   // the autopilot needs a pass to notice before "idle" means arrived
                    Audit.log(w, Audit.Src.ORDERS, "orders:" + j.template, null, "train " + j.name, "go to " + a1, "", "", "ok");
                }
                Autopilot.Run r = Autopilot.run(j.loco);
                if (r != null && r.idle && a.home == key && now - j.stepSince >= 40) return next(w, j, now, "arrived at " + a1);
                j.state = where + " - " + (r == null ? "waiting for the autopilot" : r.status);
                return false;
            }
            case "LOAD": {
                int want = (int) num(a1, 100), fill = Autopilot.fillPercent(loco);
                boolean cargoOk = a2.isEmpty() || carries(loco, a2);
                if (fill >= want && cargoOk) return next(w, j, now, "loaded " + fill + "%");
                if (timeout) { late(w, j, where + " (" + fill + "% full)"); return next(w, j, now, "load timed out at " + fill + "%"); }
                j.state = where + " - " + fill + "% full" + (cargoOk ? "" : ", no " + a2 + " aboard yet");
                return false;
            }
            case "UNLOAD": {
                int c = Autopilot.cargoUnits(loco);
                if (c != j.lastCargo) { j.lastCargo = c; j.lastCargoChange = now; }
                if (c == 0) return next(w, j, now, "empty");
                if (now - j.lastCargoChange > 1200) return next(w, j, now, "nothing more came off (" + c + " left aboard)");
                if (timeout) { late(w, j, where); return next(w, j, now, "unload timed out"); }
                j.state = where + " - " + c + " aboard";
                return false;
            }
            case "WAIT": {
                long t = Math.round(num(a1, 30) * 20);
                if (waited >= t) return next(w, j, now, "waited");
                j.state = where + " - " + (t - waited) / 20 + " s left";
                return false;
            }
            case "WINDOW": {
                int[] win = window(a1);
                int m = minuteOfDay(w);
                boolean in = win[0] <= win[1] ? m >= win[0] && m < win[1] : m >= win[0] || m < win[1];
                if (in) return next(w, j, now, "departure window open");
                j.state = where + " - waiting for " + a1 + String.format(Locale.ROOT, " (now %02d:%02d)", m / 60, m % 60);
                return false;
            }
            case "WAITCH": {
                if (ProgramData.get(w).ch((int) num(a1, 1))) return next(w, j, now, "channel " + a1 + " on");
                if (timeout) { late(w, j, where); return next(w, j, now, "channel wait timed out"); }
                j.state = where + " - waiting for channel " + a1;
                return false;
            }
            case "SET": ProgramData.get(w).setChannel((int) num(a1, 1), a2.equalsIgnoreCase("on")); return next(w, j, now, "channel " + a1 + " " + a2);
            case "EMIT": ProgramData.post(w, a1); return next(w, j, now, "event " + a1);
            case "REPEAT": j.step = 0; j.laps++; j.issued = false; j.stepSince = now; return true;
            case "END": jobs.remove(j.loco); Audit.log(w, Audit.Src.ORDERS, "orders:" + j.template, null, "train " + j.name, "orders finished", "", "", "ok"); return false;
            default: fail(w, j, "unknown step " + k); return false;
        }
    }

    private boolean next(World w, Job j, long now, String what) {
        j.step++;
        j.issued = false;
        j.stepSince = now;
        j.lastCargo = -1;
        j.state = what;
        return true;
    }

    private void fail(World w, Job j, String why) {
        j.state = "STOPPED: " + why;
        com.dogpound.railmap.alarm.AlarmManager.get(w).raise(w, "orders:" + j.loco, com.dogpound.railmap.alarm.AlarmManager.Cat.AUTO,
                com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR, "Train orders for " + j.name + " stopped: " + why, null, false);
        jobs.remove(j.loco);
    }

    private void late(World w, Job j, String where) {
        com.dogpound.railmap.alarm.AlarmManager.get(w).raise(w, "orders:late:" + j.loco, com.dogpound.railmap.alarm.AlarmManager.Cat.AUTO,
                com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR, j.name + ": order step timed out after " + ApiConfig.orderStepTimeoutMinutes + " min, moving on - " + where, null, false);
    }

    static boolean carries(Locomotive loco, String cargo) {
        Transport.Vehicle v = new Transport.Vehicle();
        for (Entity e : Autopilot.consist(loco)) Transport.cargoOf(e, v);
        String c = cargo.toLowerCase(Locale.ROOT);
        for (String t : v.cargoTypes) if (t.toLowerCase(Locale.ROOT).contains(c)) return true;
        return false;
    }

    static Long station(World w, String name) {
        String n = name.trim();
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) if (e.getValue().equalsIgnoreCase(n)) return e.getKey();
        String first = n.split("\\s+")[0];
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) if (e.getValue().equalsIgnoreCase(first)) return e.getKey();
        return null;
    }

    private static double num(String s, double d) { try { return Double.parseDouble(s.replace("%", "")); } catch (NumberFormatException e) { return d; } }

    static int[] window(String s) {
        String[] p = s.split("-");
        return new int[]{p.length > 0 ? hm(p[0]) : 0, p.length > 1 ? hm(p[1]) : 1440};
    }

    private static int hm(String s) {
        String[] p = s.trim().split(":");
        try { return Math.min(1440, Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0)); } catch (NumberFormatException e) { return 0; }
    }

    static int minuteOfDay(World w) { long t = (w.getWorldTime() + 6000) % 24000; return (int) (t * 1440 / 24000); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        templates.clear();
        NBTTagCompound tp = t.getCompoundTag("tpl");
        for (String k : tp.getKeySet()) templates.put(k, tp.getString(k));
        jobs.clear();
        NBTTagList l = t.getTagList("jobs", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            try { Job j = Job.read(l.getCompoundTagAt(i)); jobs.put(j.loco, j); } catch (IllegalArgumentException ignored) { }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagCompound tp = new NBTTagCompound();
        for (Map.Entry<String, String> e : templates.entrySet()) tp.setString(e.getKey(), e.getValue());
        t.setTag("tpl", tp);
        NBTTagList l = new NBTTagList();
        for (Job j : jobs.values()) l.appendTag(j.write());
        t.setTag("jobs", l);
        return t;
    }
}
