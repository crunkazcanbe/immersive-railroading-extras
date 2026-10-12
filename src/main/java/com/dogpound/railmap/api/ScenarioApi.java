package com.dogpound.railmap.api;

import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.incident.Scenario;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.safety.FaultLab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Scenario and simulation API (her ChatGPT list #105) for questing mods, server events and scenario packs. It does not
 * duplicate the scenario lab the incident work built (incident/Scenario: save the railway, try a fault / dwell / speed /
 * train-count change, run both forward and compare) - it exposes it, and adds what a quest or event needs on top:
 * <ul>
 *   <li>OBJECTIVES - an expression (the program's language: "deps >= 40 && ontime >= 90") with a deadline; REACH = met
 *   the moment it is true, HOLD = failed the moment it is false, met at the deadline. Results post program events
 *   (objective:&lt;name&gt;:met / :failed), go in the recorder and the audit log.</li>
 *   <li>CONTROLLED FAULTS - through the Fault Lab only, so the lab's TEST MODE and zone rules still apply.</li>
 *   <li>DEMAND - a station's passenger demand scaled for N minutes, then put back (also after a restart).</li>
 *   <li>EVALUATE - today's KPIs (departures, punctuality, delay, freight, energy...) as one table.</li>
 * </ul>
 */
public final class ScenarioApi extends WorldSavedData {
    private static final String NAME = "irextras_scenario_api";

    public static final class Objective {
        public String name = "", expr = "", state = "RUNNING", who = "";
        public boolean hold;
        public long start, deadline, ended;

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("n", name); t.setString("e", expr); t.setString("s", state); t.setString("who", who); t.setBoolean("hold", hold);
            t.setLong("start", start); t.setLong("dl", deadline); t.setLong("end", ended);
            return t;
        }

        static Objective read(NBTTagCompound t) {
            Objective o = new Objective();
            o.name = t.getString("n"); o.expr = t.getString("e"); o.state = t.getString("s"); o.who = t.getString("who"); o.hold = t.getBoolean("hold");
            o.start = t.getLong("start"); o.deadline = t.getLong("dl"); o.ended = t.getLong("end");
            return o;
        }
    }

    public final List<Objective> objectives = new ArrayList<>();
    /** station key -> {original population, restore at tick} */
    public final Map<Long, long[]> demand = new LinkedHashMap<>();

    public ScenarioApi() { super(NAME); }
    public ScenarioApi(String n) { super(n); }

    public static ScenarioApi get(World w) {
        MapStorage s = w.getPerWorldStorage();
        ScenarioApi d = (ScenarioApi) s.getOrLoadData(ScenarioApi.class, NAME);
        if (d == null) { d = new ScenarioApi(); s.setData(NAME, d); }
        return d;
    }

    // ---- the scenario lab (incident/Scenario), exposed as-is -------------------------------------------------------
    public static String labSave(World w) { return Scenario.save(w); }
    public static String labSet(World w, String what, int delta) { String r = Scenario.set(w, what, delta); return r == null ? "refused: fault | faultmin | dwell | speed | line | trains | reset" : r; }
    public static String labRun(World w) { return Scenario.run(w); }
    public static String labDescribe(World w) { return Scenario.describe(w); }

    /** the last lab run's comparison rows: what, as-is, with changes, difference */
    public static List<Map<String, Object>> labResult(World w) {
        List<Map<String, Object>> out = new ArrayList<>();
        NBTTagList l = Analytics.get(w).snapshot.getTagList("res", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("what", c.getString("w")); m.put("asIs", c.getString("a")); m.put("withChanges", c.getString("b")); m.put("difference", c.getString("d"));
            out.add(m);
        }
        return out;
    }

    // ---- objectives ------------------------------------------------------------------------------------------------
    public String objective(World w, String name, String expr, double minutes, boolean hold, Audit.Src src, String who) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) return "refused: objective names are letters, digits, _ and - (max 32)";
        String[] err = new String[1];
        Expr.test(w, expr, err);
        if (err[0] != null) return "refused: bad expression: " + err[0];
        objectives.removeIf(o -> o.name.equalsIgnoreCase(name));
        Objective o = new Objective();
        o.name = name; o.expr = expr; o.hold = hold; o.who = who;
        o.start = w.getTotalWorldTime();
        o.deadline = minutes <= 0 ? 0 : o.start + Math.round(minutes * 1200);
        objectives.add(o);
        Audit.log(w, src, who, null, "objective " + name, hold ? "hold" : "reach", "", expr + (minutes > 0 ? " within " + minutes + " min" : ""), "ok");
        markDirty();
        return "ok: objective '" + name + "' " + (hold ? "HOLD " : "REACH ") + expr + (minutes > 0 ? " within " + minutes + " min" : "");
    }

    public String cancel(World w, String name, Audit.Src src, String who) {
        boolean r = objectives.removeIf(o -> o.name.equalsIgnoreCase(name));
        if (r) { Audit.log(w, src, who, null, "objective " + name, "cancel", "", "", "ok"); markDirty(); }
        return r ? "ok: objective '" + name + "' removed" : "refused: no objective '" + name + "'";
    }

    // ---- controlled faults through the Fault Lab -------------------------------------------------------------------
    public static String inject(World w, String kind, BlockPos p, Audit.Src src, String who) {
        FaultLab.Kind k = FaultLab.Kind.byName(kind);
        if (k == null) return "refused: fault kinds are " + Arrays.toString(FaultLab.Kind.values());
        String r = FaultLab.inject(w, k, p, "scenario " + who).replaceAll("§.", "");
        Audit.log(w, src, who, p, "fault lab", "inject " + k.name(), "", "", r);
        return r;
    }

    public static String clearFaults(World w, Audit.Src src, String who) {
        FaultLab.clearAll(w, "scenario " + who);
        Audit.log(w, src, who, null, "fault lab", "clear all faults", "", "", "ok");
        return "ok: every lab fault cleared";
    }

    // ---- passenger demand ------------------------------------------------------------------------------------------
    public String demand(World w, String station, int percent, double minutes, Audit.Src src, String who) {
        if (!ApiConfig.scenarioDemand) return "refused: scenarioDemand is off in irextras_api.cfg";
        Long key = TrainOrders.station(w, station);
        if (key == null) return "refused: no station named '" + station + "'";
        PassengerData pd = PassengerData.get(w);
        PassengerData.Stn s = pd.stn(key);
        if (s == null) return "refused: " + station + " has no passenger records yet (no trains have served it)";
        long[] was = demand.get(key);
        int base = was == null ? s.pop : (int) was[0];
        int pct = Math.max(0, Math.min(1000, percent));
        s.pop = Math.max(0, base * pct / 100);
        demand.put(key, new long[]{base, minutes <= 0 ? Long.MAX_VALUE : w.getTotalWorldTime() + Math.round(minutes * 1200)});
        pd.markDirty();
        markDirty();
        Audit.log(w, src, who, BlockPos.fromLong(key), "demand at " + station, pct + "%", String.valueOf(base), String.valueOf(s.pop), "ok");
        return "ok: demand at " + station + " " + pct + "% (" + base + " -> " + s.pop + ")" + (minutes > 0 ? " for " + minutes + " min" : " until reset");
    }

    public String demandReset(World w, Audit.Src src, String who) {
        int n = demand.size();
        for (Long k : new ArrayList<>(demand.keySet())) restore(w, k);
        if (n > 0) Audit.log(w, src, who, null, "demand", "reset", n + " changed", "normal", "ok");
        return "ok: demand back to normal at " + n + " station(s)";
    }

    private void restore(World w, long key) {
        long[] v = demand.remove(key);
        if (v == null) return;
        PassengerData pd = PassengerData.get(w);
        PassengerData.Stn s = pd.stn(key);
        if (s != null) { s.pop = (int) v[0]; pd.markDirty(); }
        markDirty();
    }

    // ---- evaluation ------------------------------------------------------------------------------------------------
    public static List<String[]> evaluate(World w) { return Analytics.kpis(Analytics.get(w).today(w)); }

    // ---- once a second ---------------------------------------------------------------------------------------------
    public void tick(World w) {
        long now = w.getTotalWorldTime();
        for (Long k : new ArrayList<>(demand.keySet())) if (demand.get(k)[1] <= now) restore(w, k);
        for (Objective o : objectives) {
            if (!o.state.equals("RUNNING")) continue;
            String[] err = new String[1];
            boolean v = Expr.test(w, o.expr, err);
            if (err[0] != null) { end(w, o, "FAILED", "expression error: " + err[0]); continue; }
            boolean late = o.deadline > 0 && now >= o.deadline;
            if (o.hold) { if (!v) end(w, o, "FAILED", "stopped being true"); else if (late) end(w, o, "MET", "held to the deadline"); }
            else { if (v) end(w, o, "MET", "reached"); else if (late) end(w, o, "FAILED", "deadline passed"); }
        }
        objectives.removeIf(o -> !o.state.equals("RUNNING") && now - o.ended > 72000);   // finished ones stay an hour for the console
    }

    private void end(World w, Objective o, String state, String why) {
        o.state = state;
        o.ended = w.getTotalWorldTime();
        ProgramData.post(w, "objective:" + o.name + ":" + state.toLowerCase(Locale.ROOT));
        com.dogpound.railmap.alarm.Recorder.event(w, "OBJECTIVE " + o.name + " " + state + " (" + why + ")");
        Audit.log(w, Audit.Src.SCENARIO, "objective", null, "objective " + o.name, state.toLowerCase(Locale.ROOT), "RUNNING", state, why);
        for (EntityPlayer p : w.playerEntities) p.sendMessage(new TextComponentString((state.equals("MET") ? "§a§l★ OBJECTIVE MET§r§a " : "§c§l✖ OBJECTIVE FAILED§r§c ") + o.name + "§7 - " + why));
        markDirty();
    }

    public List<Map<String, Object>> objectiveList(World w) {
        List<Map<String, Object>> out = new ArrayList<>();
        long now = w.getTotalWorldTime();
        for (Objective o : objectives) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", o.name); m.put("expr", o.expr); m.put("mode", o.hold ? "hold" : "reach"); m.put("state", o.state);
            m.put("secondsLeft", o.deadline == 0 ? -1 : Math.max(0, (o.deadline - now) / 20));
            out.add(m);
        }
        return out;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        objectives.clear();
        NBTTagList l = t.getTagList("obj", 10);
        for (int i = 0; i < l.tagCount(); i++) objectives.add(Objective.read(l.getCompoundTagAt(i)));
        demand.clear();
        NBTTagList d = t.getTagList("dem", 10);
        for (int i = 0; i < d.tagCount(); i++) { NBTTagCompound c = d.getCompoundTagAt(i); demand.put(c.getLong("k"), new long[]{c.getLong("base"), c.getLong("until")}); }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Objective o : objectives) l.appendTag(o.write());
        t.setTag("obj", l);
        NBTTagList d = new NBTTagList();
        for (Map.Entry<Long, long[]> e : demand.entrySet()) { NBTTagCompound c = new NBTTagCompound(); c.setLong("k", e.getKey()); c.setLong("base", e.getValue()[0]); c.setLong("until", e.getValue()[1]); d.appendTag(c); }
        t.setTag("dem", d);
        return t;
    }
}
