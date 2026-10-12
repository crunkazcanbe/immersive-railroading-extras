package com.dogpound.railmap.planning;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.Audit;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.program.Rule;
import com.dogpound.railmap.safety.SafetyData;
import com.dogpound.railmap.track.Rails;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Configuration version control (her "next 48" list #40): every change to the railway's CONFIGURATION - lines and
 * timetables (stops, orders, each driverless train's line, mode, dwell, top speed), signalling (CBTC / moving block per
 * line, overlap, sighting, self-test interval), automation (the signal-box program's rules and channel names), power
 * (every grid node's settings: breaker rating, protection pick-up, transformer taps / AVR / fans, feeder mode + reach,
 * rectifier voltage, battery mode, capacitor control), temporary works and the AI dispatchers - is kept as a numbered
 * version with WHO changed it, WHY, and a line-by-line DIFF against the version before.
 * <p>
 * Changes made through IR Extras (the planning desk, the console, commands, PROMOTE from the twin) are committed with
 * the player's name; a watcher also notices changes made any other way (a wrench, a settings screen, a computer) and
 * commits them, naming whoever the automation audit log last saw touch that part of the railway.
 * <p>
 * ROLLBACK puts one part of the configuration back as it was at a version. It first runs a COMPATIBILITY CHECK against
 * the railway as it is now - stops whose rails have gone, trains no longer there, grid nodes removed or rebuilt as a
 * different machine, lines that trains still run that the old version didn't have - and refuses if anything doesn't fit
 * unless forced (then only what fits is restored). Breaker positions, program variables / timers and train positions are
 * operating STATE, not configuration: a rollback never switches power on or off or moves a train. The rollback itself
 * becomes a new version, so it can be undone.
 */
public final class Versions {
    private Versions() {}

    public static final String[] CATS = {"lines", "signalling", "automation", "power", "works", "dispatch"};
    private static final Map<String, Integer> HASH = new HashMap<>();

    // ---- snapshots (flat key = value maps, sorted) ------------------------------------------------------------------
    public static TreeMap<String, String> snapshot(World w, String cat) {
        TreeMap<String, String> m = new TreeMap<>();
        switch (cat) {
            case "lines": {
                RailwayData d = RailwayData.get(w);
                for (RailwayData.Line l : d.lines().values()) {
                    StringBuilder st = new StringBuilder(), or = new StringBuilder();
                    for (int i = 0; i < l.stations.size(); i++) { st.append(i == 0 ? "" : ",").append(l.stations.get(i)); or.append(i == 0 ? "" : ",").append(l.order(i).ordinal()); }
                    m.put("line." + l.name + ".stops", st.toString());
                    m.put("line." + l.name + ".orders", or.toString());
                    m.put("line." + l.name + ".colour", Integer.toHexString(l.color));
                }
                for (RailwayData.AutoTrain a : d.trains().values()) {
                    String k = "train." + a.loco + ".";
                    m.put(k + "label", a.label); m.put(k + "mode", a.mode.name()); m.put(k + "line", a.line);
                    m.put(k + "dwell", String.valueOf(a.dwellSeconds)); m.put(k + "kmh", String.valueOf(a.maxKmh));
                }
                break;
            }
            case "signalling": {
                for (RailwayData.Line l : RailwayData.get(w).lines().values()) m.put("line." + l.name + ".cbtc", String.valueOf(l.movingBlock));
                SafetyData s = SafetyData.get(w);
                m.put("safety.overlap", String.valueOf(s.overlap)); m.put("safety.sightKmh", String.valueOf(s.sightKmh));
                m.put("safety.sightSeconds", String.valueOf(s.sightSeconds)); m.put("safety.selfTestMinutes", String.valueOf(s.selfTestMinutes));
                break;
            }
            case "automation": {
                ProgramData p = ProgramData.get(w);
                for (int i = 0; i < p.rules.size(); i++) {
                    NBTTagCompound r = p.rules.get(i).write();
                    r.removeTag("h"); r.removeTag("nf"); r.removeTag("ne");                        // firing history + counters are state, not configuration
                    m.put(String.format(Locale.ROOT, "rule.%03d", i), r.toString());
                }
                for (int i = 1; i < p.channelName.length; i++) if (p.channelName[i] != null && !p.channelName[i].isEmpty()) m.put(String.format(Locale.ROOT, "channel.%02d.name", i), p.channelName[i]);
                break;
            }
            case "power": {
                for (Map.Entry<Long, GridData.Node> e : GridData.get(w).nodes().entrySet()) {
                    BlockPos p = BlockPos.fromLong(e.getKey());
                    GridData.Node n = e.getValue();
                    String k = "node." + p.getX() + "," + p.getY() + "," + p.getZ() + ".";
                    m.put(k + "kind", n.kind.name());
                    m.put(k + "rating", String.valueOf(n.rating)); m.put(k + "relaySet", String.valueOf(n.relaySet));
                    switch (n.kind.name()) {
                        case "FEEDER": m.put(k + "feeds", n.feeds); m.put(k + "range", String.valueOf(n.range)); m.put(k + "ownFirst", String.valueOf(n.ownFirst)); break;
                        case "TRANSFORMER": m.put(k + "tap", n.avr ? "AVR" : String.valueOf(n.tap)); m.put(k + "avr", String.valueOf(n.avr)); m.put(k + "cool", String.valueOf(n.cool)); break;   // with the AVR in, the tap moves by itself: not a setting
                        case "RECTIFIER": m.put(k + "dcv", String.valueOf(n.dcv)); break;
                        case "BATTERY": m.put(k + "batMode", String.valueOf(n.batMode)); break;
                        case "CAPACITOR": m.put(k + "capAuto", String.valueOf(n.capAuto)); break;
                        default:
                    }
                }
                break;
            }
            case "works":
                for (PlanData.Work k : PlanData.get(w).works) {
                    if (k.phase == PlanData.Phase.CLEARED) continue;
                    String p = "work." + k.id + ".";
                    m.put(p + "kind", k.kind.name()); m.put(p + "name", k.name);
                    m.put(p + "zone", String.format(Locale.ROOT, "%.0f,%.0f,%.0f r%.0f", k.x, k.y, k.z, k.r) + (k.kind == PlanData.Kind.SINGLE_LINE ? String.format(Locale.ROOT, " to %.0f,%.0f", k.x2, k.z2) : ""));
                    m.put(p + "value", k.kind == PlanData.Kind.LENGTH ? k.maxLenM + " m" : k.kmh + " km/h");
                    m.put(p + "window", k.start + "-" + k.end);
                }
                break;
            case "dispatch":
                for (PlanData.Region r : PlanData.get(w).regions) {
                    String p = "region." + r.name + ".";
                    m.put(p + "goal", r.goal.name()); m.put(p + "on", String.valueOf(r.on)); m.put(p + "priority", String.valueOf(r.priority));
                    m.put(p + "maxHold", String.valueOf(r.maxHold)); m.put(p + "lines", r.lines);
                    m.put(p + "area", r.r <= 0 ? "all" : String.format(Locale.ROOT, "%.0f,%.0f r%.0f", r.x, r.z, r.r));
                }
                break;
            default:
        }
        return m;
    }

    static NBTTagCompound pack(Map<String, String> m) { NBTTagCompound t = new NBTTagCompound(); m.forEach(t::setString); return t; }
    static TreeMap<String, String> unpack(NBTTagCompound t) { TreeMap<String, String> m = new TreeMap<>(); for (String k : t.getKeySet()) m.put(k, t.getString(k)); return m; }

    /** the configuration of a part at a version (the newest version of that part at or before it) */
    public static TreeMap<String, String> at(PlanData d, String cat, int version) {
        PlanData.Version best = null;
        for (PlanData.Version v : d.versions) if (v.cat.equals(cat) && v.id <= version && (best == null || v.id > best.id)) best = v;
        return best == null ? null : unpack(best.snap);
    }

    private static TreeMap<String, String> latest(PlanData d, String cat) { return at(d, cat, Integer.MAX_VALUE); }

    public static List<String> diff(Map<String, String> a, Map<String, String> b) {
        List<String> o = new ArrayList<>();
        Set<String> keys = new TreeSet<>(a == null ? Collections.emptySet() : a.keySet());
        keys.addAll(b.keySet());
        for (String k : keys) {
            String x = a == null ? null : a.get(k), y = b.get(k);
            if (Objects.equals(x, y)) continue;
            if (x == null) o.add("+ " + k + " = " + cut(y));
            else if (y == null) o.add("- " + k + " (was " + cut(x) + ")");
            else o.add("~ " + k + ": " + cut(x) + " -> " + cut(y));
        }
        return o;
    }

    private static String cut(String s) { return s.length() > 90 ? s.substring(0, 88) + ".." : s; }

    // ---- commit ----------------------------------------------------------------------------------------------------------
    /** record the part's configuration if it changed; returns the new version or null */
    public static PlanData.Version commit(World w, String cat, String who, String why) {
        PlanData d = PlanData.get(w);
        TreeMap<String, String> now = snapshot(w, cat);
        TreeMap<String, String> last = latest(d, cat);
        List<String> df = diff(last, now);
        HASH.put(w.provider.getDimension() + cat, now.hashCode());
        if (last != null && df.isEmpty()) return null;
        PlanData.Version v = new PlanData.Version();
        v.id = d.nextVersion++;
        v.tick = w.getTotalWorldTime();
        v.who = who; v.cat = cat; v.why = why;
        v.snap = pack(now);
        v.diff.addAll(df.size() > 60 ? df.subList(0, 60) : df);
        if (df.size() > 60) v.diff.add("... and " + (df.size() - 60) + " more");
        d.versions.add(v);
        prune(d);
        d.markDirty();
        if (last != null) Recorder.event(w, "Config v" + v.id + " " + cat + " by " + who + ": " + why + " (" + df.size() + " change" + (df.size() == 1 ? "" : "s") + ")");
        return v;
    }

    /** keep versionsKept, but never drop the only (= base) version of a part */
    private static void prune(PlanData d) {
        while (d.versions.size() > PlanConfig.versionsKept) {
            boolean removed = false;
            for (int i = 0; i < d.versions.size() && !removed; i++) {
                PlanData.Version v = d.versions.get(i);
                for (int j = i + 1; j < d.versions.size(); j++) if (d.versions.get(j).cat.equals(v.cat)) { d.versions.remove(i); removed = true; break; }
            }
            if (!removed) break;
        }
    }

    /** the watcher: commits parts that changed without going through us */
    static void watch(World w, long now) {
        if (PlanConfig.versionWatchSeconds <= 0 || now / 20 % PlanConfig.versionWatchSeconds != 0) return;
        for (String cat : CATS) {
            String hk = w.provider.getDimension() + cat;
            TreeMap<String, String> snap;
            try { snap = snapshot(w, cat); } catch (RuntimeException e) { continue; }
            Integer h = HASH.get(hk);
            if (h != null && h == snap.hashCode()) continue;
            HASH.put(hk, snap.hashCode());
            commit(w, cat, h == null && at(PlanData.get(w), cat, Integer.MAX_VALUE) == null ? "system" : lastActor(w), h == null ? "baseline / changed while unloaded" : "changed outside the planning tools");
        }
    }

    /** whoever the automation audit log saw last (best attribution for a change made elsewhere) */
    private static String lastActor(World w) {
        try {
            List<Audit.Entry> l = Audit.get(w).entries;                       // newest first
            if (!l.isEmpty()) return l.get(0).who + " (audit)";
        } catch (RuntimeException | LinkageError ignored) { }
        return "unattributed";
    }

    // ---- rollback ---------------------------------------------------------------------------------------------------------
    /** what doesn't fit the railway as it is now (empty = compatible) */
    public static List<String> check(World w, String cat, Map<String, String> old) {
        List<String> o = new ArrayList<>();
        switch (cat) {
            case "lines": {
                RailwayData d = RailwayData.get(w);
                for (Map.Entry<String, String> e : old.entrySet()) {
                    String k = e.getKey();
                    if (k.startsWith("line.") && k.endsWith(".stops")) {
                        for (String s : e.getValue().split(",")) {
                            if (s.isEmpty()) continue;
                            BlockPos p = BlockPos.fromLong(Long.parseLong(s));
                            if (w.isBlockLoaded(p) && Rails.pieceAt(w, p) == null) o.add("stop " + p.getX() + "," + p.getZ() + " of " + k.substring(5, k.length() - 6) + ": no track there any more");
                        }
                    } else if (k.startsWith("train.") && k.endsWith(".line")) {
                        String u = k.substring(6, k.length() - 5);
                        try { if (d.train(UUID.fromString(u)) == null) o.add("train " + old.getOrDefault("train." + u + ".label", u.substring(0, 6)) + " is no longer driverless / gone"); } catch (IllegalArgumentException ignored) { }
                    }
                }
                for (RailwayData.AutoTrain a : d.trains().values())
                    if (!a.line.isEmpty() && !old.containsKey("line." + a.line + ".stops")) o.add("train " + (a.label.isEmpty() ? a.loco.toString().substring(0, 6) : a.label) + " runs line " + a.line + ", which that version didn't have");
                break;
            }
            case "signalling":
                for (String k : old.keySet()) if (k.startsWith("line.") && RailwayData.get(w).line(k.substring(5, k.length() - 5)) == null) o.add(k.substring(5, k.length() - 5) + ": line no longer exists");
                break;
            case "automation":
                for (Map.Entry<String, String> e : old.entrySet()) if (e.getKey().startsWith("rule.")) try { JsonToNBT.getTagFromJson(e.getValue()); } catch (NBTException ex) { o.add(e.getKey() + ": unreadable"); }
                break;
            case "power": {
                GridData g = GridData.get(w);
                for (Map.Entry<String, String> e : old.entrySet()) {
                    if (!e.getKey().endsWith(".kind")) continue;
                    String[] c = e.getKey().substring(5, e.getKey().length() - 5).split(",");
                    BlockPos p = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
                    GridData.Node n = g.nodes().get(p.toLong());
                    if (n == null) o.add("grid node " + p.getX() + "," + p.getY() + "," + p.getZ() + " (" + e.getValue() + ") removed");
                    else if (!n.kind.name().equals(e.getValue())) o.add("grid node " + p.getX() + "," + p.getZ() + " is now a " + n.kind.name() + ", was " + e.getValue());
                }
                break;
            }
            default:
        }
        return o;
    }

    public static String rollback(World w, String cat, int version, boolean force, String who) {
        PlanData d = PlanData.get(w);
        TreeMap<String, String> old = at(d, cat, version);
        if (old == null) return "§cNo " + cat + " configuration at or before v" + version;
        List<String> bad = check(w, cat, old);
        if (!bad.isEmpty() && !force) {
            StringBuilder sb = new StringBuilder("§cRollback refused - " + bad.size() + " incompatibilit" + (bad.size() == 1 ? "y" : "ies") + ":");
            for (int i = 0; i < Math.min(6, bad.size()); i++) sb.append("\n§7 - ").append(bad.get(i));
            sb.append("\n§8(add 'force' to restore only what fits)");
            return sb.toString();
        }
        int n = restore(w, cat, old);
        PlanData.Version v = commit(w, cat, who, "ROLLBACK to v" + version + (bad.isEmpty() ? "" : " (forced, " + bad.size() + " part(s) skipped)"));
        Audit.log(w, Audit.Src.PLAYER, who, null, "config:" + cat, "rollback", "current", "v" + version, n + " settings restored");
        return "§a" + cat + " rolled back to v" + version + ": " + n + " settings restored" + (v == null ? " (no change)" : ", now v" + v.id) + (bad.isEmpty() ? "" : " §e(" + bad.size() + " skipped)");
    }

    private static int restore(World w, String cat, Map<String, String> old) {
        int n = 0;
        switch (cat) {
            case "lines": {
                RailwayData d = RailwayData.get(w);
                Set<String> keep = new HashSet<>();
                for (Map.Entry<String, String> e : old.entrySet()) {
                    String k = e.getKey();
                    if (!k.startsWith("line.") || !k.endsWith(".stops")) continue;
                    String name = k.substring(5, k.length() - 6);
                    keep.add(name);
                    List<Long> st = new ArrayList<>();
                    List<Integer> or = new ArrayList<>();
                    for (String s : e.getValue().split(",")) if (!s.isEmpty()) {
                        BlockPos p = BlockPos.fromLong(Long.parseLong(s));
                        if (!w.isBlockLoaded(p) || Rails.pieceAt(w, p) != null) st.add(Long.parseLong(s));
                    }
                    for (String s : old.getOrDefault("line." + name + ".orders", "").split(",")) if (!s.isEmpty()) or.add(Integer.parseInt(s));
                    RailwayData.Line l = d.line(name);
                    boolean mb = l != null && l.movingBlock;
                    RailwayData.Line nl = d.saveLine(name, st, or.size() == st.size() ? or : null);
                    if (nl != null) { nl.movingBlock = mb; try { nl.color = (int) Long.parseLong(old.getOrDefault("line." + name + ".colour", Integer.toHexString(nl.color)), 16); } catch (NumberFormatException ignored) { } }
                    n++;
                }
                for (String name : new ArrayList<>(d.lines().keySet())) {
                    if (keep.contains(name)) continue;
                    boolean used = false;
                    for (RailwayData.AutoTrain a : d.trains().values()) used |= name.equals(a.line);
                    if (!used) { d.deleteLine(name); n++; }
                }
                for (RailwayData.AutoTrain a : d.trains().values()) {
                    String k = "train." + a.loco + ".";
                    if (!old.containsKey(k + "line")) continue;
                    String line = old.get(k + "line");
                    if (line.isEmpty() || d.line(line) != null) a.line = line;
                    try { a.mode = RailwayData.Mode.valueOf(old.get(k + "mode")); } catch (IllegalArgumentException | NullPointerException ignored) { }
                    a.label = old.getOrDefault(k + "label", a.label);
                    a.dwellSeconds = parse(old.get(k + "dwell"), a.dwellSeconds);
                    a.maxKmh = parse(old.get(k + "kmh"), a.maxKmh);
                    n++;
                }
                d.markDirty();
                break;
            }
            case "signalling": {
                RailwayData d = RailwayData.get(w);
                for (Map.Entry<String, String> e : old.entrySet()) {
                    if (!e.getKey().startsWith("line.")) continue;
                    RailwayData.Line l = d.line(e.getKey().substring(5, e.getKey().length() - 5));
                    if (l != null) { l.movingBlock = Boolean.parseBoolean(e.getValue()); n++; }
                }
                d.markDirty();
                SafetyData s = SafetyData.get(w);
                s.overlap = parse(old.get("safety.overlap"), s.overlap); s.sightKmh = parse(old.get("safety.sightKmh"), s.sightKmh);
                s.sightSeconds = parse(old.get("safety.sightSeconds"), s.sightSeconds); s.selfTestMinutes = parse(old.get("safety.selfTestMinutes"), s.selfTestMinutes);
                s.markDirty();
                n += 4;
                break;
            }
            case "automation": {
                ProgramData p = ProgramData.get(w);
                List<Rule> rules = new ArrayList<>();
                for (Map.Entry<String, String> e : old.entrySet())
                    if (e.getKey().startsWith("rule.")) try { rules.add(Rule.read(JsonToNBT.getTagFromJson(e.getValue()))); } catch (NBTException ignored) { }
                p.rules.clear();
                p.rules.addAll(rules);
                for (int i = 1; i < p.channelName.length; i++) p.channelName[i] = old.getOrDefault(String.format(Locale.ROOT, "channel.%02d.name", i), "");
                p.markDirty();
                n = rules.size();
                break;
            }
            case "power": {
                GridData g = GridData.get(w);
                for (Map.Entry<Long, GridData.Node> e : g.nodes().entrySet()) {
                    BlockPos p = BlockPos.fromLong(e.getKey());
                    GridData.Node nd = e.getValue();
                    String k = "node." + p.getX() + "," + p.getY() + "," + p.getZ() + ".";
                    if (!nd.kind.name().equals(old.get(k + "kind"))) continue;
                    nd.rating = parse(old.get(k + "rating"), nd.rating); nd.relaySet = parse(old.get(k + "relaySet"), nd.relaySet);
                    if (old.containsKey(k + "feeds")) nd.feeds = old.get(k + "feeds");
                    nd.range = parse(old.get(k + "range"), nd.range);
                    if (old.containsKey(k + "ownFirst")) nd.ownFirst = Boolean.parseBoolean(old.get(k + "ownFirst"));
                    nd.tap = parse(old.get(k + "tap"), nd.tap);
                    if (old.containsKey(k + "avr")) nd.avr = Boolean.parseBoolean(old.get(k + "avr"));
                    nd.cool = parse(old.get(k + "cool"), nd.cool); nd.dcv = parse(old.get(k + "dcv"), nd.dcv); nd.batMode = parse(old.get(k + "batMode"), nd.batMode);
                    if (old.containsKey(k + "capAuto")) nd.capAuto = Boolean.parseBoolean(old.get(k + "capAuto"));
                    n++;
                }
                g.markDirty();
                break;
            }
            case "works": {
                PlanData d = PlanData.get(w);
                Set<Integer> want = new HashSet<>();
                for (String k : old.keySet()) if (k.startsWith("work.") && k.endsWith(".kind")) want.add(Integer.parseInt(k.split("\\.")[1]));
                for (PlanData.Work k : d.works) if (k.phase != PlanData.Phase.CLEARED && !want.contains(k.id)) { TempWorks.remove(w, k.id, "rollback"); n++; }
                n += 0;   // works the old version had but are gone are re-planned by hand: their crews have already been stood down
                break;
            }
            case "dispatch": {
                PlanData d = PlanData.get(w);
                for (PlanData.Region r : d.regions) {
                    String k = "region." + r.name + ".";
                    if (!old.containsKey(k + "goal")) { r.on = false; n++; continue; }      // a region added since: switched off
                    try { r.goal = PlanData.Goal.valueOf(old.get(k + "goal")); } catch (IllegalArgumentException ignored) { }
                    r.on = Boolean.parseBoolean(old.get(k + "on")); r.priority = parse(old.get(k + "priority"), r.priority); r.maxHold = parse(old.get(k + "maxHold"), r.maxHold);
                    r.lines = old.getOrDefault(k + "lines", r.lines);
                    n++;
                }
                d.markDirty();
                break;
            }
            default:
        }
        return n;
    }

    private static int parse(String s, int dflt) { try { return s == null ? dflt : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return dflt; } }

    public static void clear() { HASH.clear(); }

    /** test hook: Rails used for the stop checks */
    static boolean railAt(World w, BlockPos p) { TileRail r = Rails.pieceAt(w, p); return r != null; }
}
