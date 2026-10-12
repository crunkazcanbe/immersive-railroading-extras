package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.depot.Crew;
import com.dogpound.railmap.depot.DepotClock;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileSpeedSign;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Driver competency and route qualification (her ChatGPT batch-3 #46). Extends the Crew Booking-On Desk's drivers
 * (depot.FleetData: traction quals + route knowledge per line) with a real competence record per player:
 * <ul>
 *   <li>traction hours (game hours, DepotClock) for diesel / electric / steam, counted while they ride a moving train
 *   that is not driverless;</li>
 *   <li>route knowledge signal by signal: a signal is KNOWN after {@link SigConfig#routeKnowledgePasses} passes as
 *   driver (the train's describer berth moving off that signal), lapsing after {@link SigConfig#routeKnowledgeLapseDays}
 *   game days unused when that is set;</li>
 *   <li>incidents: SPAD, TRIP (trip cock), AWS (warning not acknowledged), OVERSPEED (protection / supervisor);</li>
 *   <li>a theory assessment (Driver Competency Assessor: rule book + live questions about this railway);</li>
 *   <li>a grade: TRAINEE -> QUALIFIED (theory 80 %, 4 h driving, 5 signals known) -> SENIOR (40 h, 25 known, no
 *   incident in the last 20 h driven) -> INSTRUCTOR (120 h, 60 known, theory 95 %).</li>
 * </ul>
 * Pass-outs and route qualifications are written onto the matching FleetData driver, so the crew desk and attended
 * lines honour them. Enforcement ({@link SigConfig#competencyEnforce}, default off): 40 km/h past signals the driver
 * doesn't know, and a SPAD / trip suspends the driver until reassessed (theory passed again).
 */
public final class Competency extends WorldSavedData {
    private static final String NAME = "irextras_competency";
    public static final String[] GRADE = {"TRAINEE", "QUALIFIED", "SENIOR", "INSTRUCTOR"};
    public static final String[] TRACTION = {"DIESEL", "ELECTRIC", "STEAM"};

    public static final class Rec {
        public UUID id;
        public String name = "";
        /** game hours driven per traction (DIESEL, ELECTRIC, STEAM) */
        public final double[] hours = new double[3];
        /** signal -> {passes, last game day passed} */
        public final Map<Long, int[]> known = new LinkedHashMap<>();
        public int theoryBest, theoryLast, tests;
        public long theoryAt;
        /** newest first: "tick|KIND|text" */
        public final List<String> incidents = new ArrayList<>();
        public int spads, trips, aws, overspeed;
        public double hoursAtIncident = -1;
        public boolean suspended;
        public String suspendWhy = "";
        public final Set<String> lines = new LinkedHashSet<>();
        public int passedOut;          // traction bits passed out at the assessor (Crew.DIESEL / ELECTRIC / STEAM)

        public double total() { return hours[0] + hours[1] + hours[2]; }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setUniqueId("id", id); t.setString("n", name);
            for (int i = 0; i < 3; i++) t.setDouble("h" + i, hours[i]);
            NBTTagCompound k = new NBTTagCompound();
            for (Map.Entry<Long, int[]> e : known.entrySet()) k.setIntArray(String.valueOf(e.getKey()), e.getValue());
            t.setTag("k", k);
            t.setInteger("tb", theoryBest); t.setInteger("tl", theoryLast); t.setInteger("tn", tests); t.setLong("ta", theoryAt);
            NBTTagList in = new NBTTagList();
            for (String s : incidents) in.appendTag(new NBTTagString(s));
            t.setTag("inc", in);
            t.setInteger("sp", spads); t.setInteger("tr", trips); t.setInteger("aw", aws); t.setInteger("ov", overspeed);
            t.setDouble("hi", hoursAtIncident); t.setBoolean("sus", suspended); t.setString("sw", suspendWhy);
            NBTTagList ls = new NBTTagList();
            for (String s : lines) ls.appendTag(new NBTTagString(s));
            t.setTag("ln", ls);
            t.setInteger("po", passedOut);
            return t;
        }

        static Rec read(NBTTagCompound t) {
            Rec r = new Rec();
            r.id = t.getUniqueId("id"); r.name = t.getString("n");
            for (int i = 0; i < 3; i++) r.hours[i] = t.getDouble("h" + i);
            NBTTagCompound k = t.getCompoundTag("k");
            for (String s : k.getKeySet()) try { int[] v = k.getIntArray(s); if (v.length == 2) r.known.put(Long.parseLong(s), v); } catch (NumberFormatException ignored) { }
            r.theoryBest = t.getInteger("tb"); r.theoryLast = t.getInteger("tl"); r.tests = t.getInteger("tn"); r.theoryAt = t.getLong("ta");
            NBTTagList in = t.getTagList("inc", 8);
            for (int i = 0; i < in.tagCount(); i++) r.incidents.add(in.getStringTagAt(i));
            r.spads = t.getInteger("sp"); r.trips = t.getInteger("tr"); r.aws = t.getInteger("aw"); r.overspeed = t.getInteger("ov");
            r.hoursAtIncident = t.hasKey("hi") ? t.getDouble("hi") : -1; r.suspended = t.getBoolean("sus"); r.suspendWhy = t.getString("sw");
            NBTTagList ls = t.getTagList("ln", 8);
            for (int i = 0; i < ls.tagCount(); i++) r.lines.add(ls.getStringTagAt(i));
            r.passedOut = t.getInteger("po");
            return r;
        }
    }

    public final Map<UUID, Rec> recs = new LinkedHashMap<>();

    public Competency() { super(NAME); }
    public Competency(String n) { super(n); }

    public static Competency get(World w) {
        MapStorage s = w.getPerWorldStorage();
        Competency d = (Competency) s.getOrLoadData(Competency.class, NAME);
        if (d == null) { d = new Competency(); s.setData(NAME, d); }
        return d;
    }

    public Rec rec(EntityPlayer p) {
        Rec r = recs.computeIfAbsent(p.getUniqueID(), k -> { Rec n = new Rec(); n.id = k; return n; });
        r.name = p.getName();
        return r;
    }

    public Rec find(String nameOrId) {
        for (Rec r : recs.values()) if (r.name.equalsIgnoreCase(nameOrId) || r.id.toString().equals(nameOrId)) return r;
        return null;
    }

    // ---- knowledge + grade ------------------------------------------------------------------------------------------
    static long day(World w) { return w.getTotalWorldTime() / 24000L; }

    /** does this driver know this signal (enough passes, not lapsed)? */
    public static boolean knows(World w, Rec r, long signal) {
        int[] k = r.known.get(signal);
        if (k == null || k[0] < SigConfig.routeKnowledgePasses) return false;
        return SigConfig.routeKnowledgeLapseDays <= 0 || day(w) - k[1] <= SigConfig.routeKnowledgeLapseDays;
    }

    public static int knownCount(World w, Rec r) {
        int n = 0;
        for (long s : r.known.keySet()) if (knows(w, r, s)) n++;
        return n;
    }

    /** the grade earned from the record (0 TRAINEE .. 3 INSTRUCTOR) */
    public static int grade(Rec r, int known) {
        if (r.suspended) return 0;
        double h = r.total();
        boolean clean20 = r.hoursAtIncident < 0 || h - r.hoursAtIncident >= 20;
        if (r.theoryBest >= 95 && h >= 120 && known >= 60 && clean20) return 3;
        if (r.theoryBest >= 80 && h >= 40 && known >= 25 && clean20) return 2;
        if (r.theoryBest >= 80 && h >= 4 && known >= 5) return 1;
        return 0;
    }

    public static String gradeOf(World w, Rec r) { return r.suspended ? "SUSPENDED" : GRADE[grade(r, knownCount(w, r))]; }

    /** what is still missing for the next grade, one line */
    public static String nextStep(World w, Rec r) {
        if (r.suspended) return "suspended (" + r.suspendWhy + ") - pass the theory test again";
        int k = knownCount(w, r), g = grade(r, k);
        double h = r.total();
        List<String> need = new ArrayList<>();
        int[] th = {80, 80, 95}, kn = {5, 25, 60};
        double[] hr = {4, 40, 120};
        if (g >= 3) return "top grade";
        if (r.theoryBest < th[g]) need.add("theory " + th[g] + "%");
        if (h < hr[g]) need.add(String.format(Locale.ROOT, "%.1f more h driving", hr[g] - h));
        if (k < kn[g]) need.add((kn[g] - k) + " more signals known");
        if (g >= 1 && !(r.hoursAtIncident < 0 || h - r.hoursAtIncident >= 20)) need.add(String.format(Locale.ROOT, "%.1f h clean driving", 20 - (h - r.hoursAtIncident)));
        return "for " + GRADE[g + 1] + ": " + (need.isEmpty() ? "-" : String.join(", ", need));
    }

    // ---- tracking (once a second) -----------------------------------------------------------------------------------
    /** last berth seen per tracked train */
    private static final Map<String, Long> BERTH = new HashMap<>();
    private static final Map<UUID, Integer> GRADE_SEEN = new HashMap<>();

    static void tick(World w) {
        SafetyData sd = SafetyData.get(w);
        Competency c = get(w);
        double hourPerSecond = 20.0 / DepotClock.TICKS_PER_HOUR;
        boolean dirty = false;
        Set<String> live = new HashSet<>();
        for (SafetyData.TrainRec t : sd.trains.values()) {
            if (t.gone) continue;
            live.add(t.id);
            Long was = BERTH.put(t.id, t.berthPos);
            if (t.driverless) continue;
            List<EntityPlayer> riders = ConsistWatch.riders(w, t);
            if (riders.isEmpty()) continue;
            int kind = 0;
            Locomotive l = ConsistWatch.loco(w, t);
            if (l != null) kind = Integer.numberOfTrailingZeros(Crew.traction(w, l));
            for (EntityPlayer p : riders) {
                Rec r = c.rec(p);
                if (Math.abs(t.speed) > 2) { r.hours[Math.min(2, kind)] += hourPerSecond; dirty = true; }
                if (was != null && was != 0 && was != t.berthPos) {                   // moved off the berth = passed that signal
                    int[] k = r.known.computeIfAbsent(was, x -> new int[2]);
                    k[0]++;
                    k[1] = (int) day(w);
                    dirty = true;
                    if (k[0] == SigConfig.routeKnowledgePasses) {
                        String sig = Geo.xz(was);
                        Recorder.event(w, "ROUTE KNOWLEDGE: " + r.name + " now knows the signal at " + sig);
                        QuestEvents.fire(w, p, BlockPos.fromLong(was), "route_learned", "signal", sig, "known", knownCount(w, r));
                    }
                }
                int g = grade(r, knownCount(w, r));
                Integer gs = GRADE_SEEN.put(r.id, g);
                if (gs != null && g > gs) {
                    Recorder.event(w, "COMPETENCE: " + r.name + " is now " + GRADE[g]);
                    QuestEvents.fire(w, p, p.getPosition(), "driver_qualified", "grade", GRADE[g], "hours", Math.round(r.total()));
                }
            }
        }
        BERTH.keySet().retainAll(live);
        if (dirty) c.markDirty();
    }

    /** an incident against the drivers (SigC.incident) */
    static void incident(World w, List<EntityPlayer> drivers, String kind, String text) {
        if (drivers == null || drivers.isEmpty()) return;
        Competency c = get(w);
        String k = kind == null ? "OTHER" : kind.toUpperCase(Locale.ROOT);
        for (EntityPlayer p : drivers) {
            Rec r = c.rec(p);
            r.incidents.add(0, w.getTotalWorldTime() + "|" + k + "|" + (text == null ? "" : text));
            while (r.incidents.size() > 30) r.incidents.remove(r.incidents.size() - 1);
            switch (k) {
                case "SPAD": r.spads++; break;
                case "TRIP": r.trips++; break;
                case "AWS": r.aws++; break;
                default: r.overspeed++; break;
            }
            r.hoursAtIncident = r.total();
            if (SigConfig.competencyEnforce && (k.equals("SPAD") || k.equals("TRIP")) && !r.suspended) {
                r.suspended = true;
                r.suspendWhy = k + " " + Recorder.clock(w, w.getTotalWorldTime());
                AlarmManager.get(w).raise(w, "suspend:" + r.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR,
                        "Driver " + r.name + " SUSPENDED after a " + k + " - reassessment needed (Driver Competency Assessor)", p.getPosition(), true);
                p.sendMessage(new net.minecraft.util.text.TextComponentString("§c[COMPETENCE] You are suspended from driving after a " + k + " - pass the theory test at a Driver Competency Assessor"));
            }
            Recorder.event(w, "DRIVER INCIDENT " + k + ": " + r.name + (text == null || text.isEmpty() ? "" : " - " + text));
        }
        c.markDirty();
    }

    // ---- enforcement: limits along a path (SigHooks.restrictions) --------------------------------------------------
    static void restrictions(World w, TrackPath path, List<Object[]> out) {
        if (!SigConfig.competencyEnforce || path.steps.isEmpty()) return;
        Vec3i s0 = path.steps.get(0).pos;
        SafetyData.TrainRec train = null;
        for (SafetyData.TrainRec t : SafetyData.get(w).trains.values())
            if (!t.gone && !t.driverless && Geo.d2(t.x, t.z, s0.x + 0.5, s0.z + 0.5) < 30 * 30) { train = t; break; }
        if (train == null) return;
        List<EntityPlayer> riders = ConsistWatch.riders(w, train);
        if (riders.isEmpty()) return;
        Competency c = get(w);
        List<Rec> ds = new ArrayList<>();
        for (EntityPlayer p : riders) ds.add(c.rec(p));
        for (Rec r : ds) if (r.suspended) { out.add(new Object[]{0.0, 25.0, false, "Driver " + r.name + " suspended - 25 km/h", null, false}); return; }
        for (TileSignalMast m : SignalRegistry.masts(w.provider.getDimension())) {
            Vec3i g = m.governedRail();
            if (g == null) continue;
            int i = path.indexOf(g);
            if (i < 0) continue;
            boolean known = false;
            for (Rec r : ds) if (knows(w, r, m.getPos().toLong())) { known = true; break; }
            if (known) continue;
            double d = i == 0 ? 0 : path.steps.get(i - 1).distance;
            out.add(new Object[]{Math.max(0, d - 150), 40.0, false, "Route not known: " + SignalSighting.name(m), m.getPos(), false});
        }
    }

    // ---- route qualification for a line ----------------------------------------------------------------------------
    /** the signals along a line: within 200 m of the straight leg between consecutive stations */
    static List<Long> lineSignals(World w, RailwayData.Line line) {
        List<Long> out = new ArrayList<>();
        if (line == null || line.stations.size() < 2) return out;
        for (TileSignalMast m : SignalRegistry.masts(w.provider.getDimension())) {
            double px = m.getPos().getX() + 0.5, pz = m.getPos().getZ() + 0.5;
            for (int i = 0; i + 1 < line.stations.size(); i++) {
                BlockPos a = BlockPos.fromLong(line.stations.get(i)), b = BlockPos.fromLong(line.stations.get(i + 1));
                if (segD2(px, pz, a.getX(), a.getZ(), b.getX(), b.getZ()) <= 200 * 200) { out.add(m.getPos().toLong()); break; }
            }
        }
        return out;
    }

    static double segD2(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 <= 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / l2));
        return Geo.d2(px, pz, ax + t * dx, az + t * dz);
    }

    /** grant route knowledge of a line (60 % of its signals known); writes it onto the FleetData driver */
    static String qualifyLine(World w, Rec r, String lineName, EntityPlayer who) {
        RailwayData.Line line = RailwayData.get(w).lines().get(lineName);
        if (line == null) return "§cNo line " + lineName;
        List<Long> sigs = lineSignals(w, line);
        if (sigs.isEmpty()) return "§cNo signals found along line " + lineName + " (needs stations and signals)";
        int k = 0;
        for (long s : sigs) if (knows(w, r, s)) k++;
        int need = (int) Math.ceil(sigs.size() * 0.6);
        if (k < need) return "§cRoute knowledge of " + lineName + ": " + k + " of " + sigs.size() + " signals known, " + need + " needed - drive the line";
        r.lines.add(lineName);
        FleetData.Driver d = fleetDriver(w, r, true);
        if (d != null) { d.routes.add(lineName); FleetData.get(w).crewNote(w, r.name + " passed route knowledge of " + lineName + " (assessor)"); FleetData.get(w).markDirty(); }
        get(w).markDirty();
        QuestEvents.fire(w, who, null, "route_learned", "line", lineName, "known", k);
        return "§aRoute qualified: " + lineName + " (" + k + "/" + sigs.size() + " signals known)";
    }

    static final double[] PASSOUT_HOURS = {2, 2, 3};

    /** pass a driver out on a traction once they have the hours on it; writes FleetData quals */
    static String passOut(World w, Rec r, int kind) {
        if (r.suspended) return "§cSuspended - reassess first";
        if (r.theoryBest < 80) return "§cTheory test (80 %) first";
        if (r.hours[kind] < PASSOUT_HOURS[kind]) return String.format(Locale.ROOT, "§c%s: %.1f of %.0f h driven", TRACTION[kind], r.hours[kind], PASSOUT_HOURS[kind]);
        r.passedOut |= 1 << kind;
        FleetData.Driver d = fleetDriver(w, r, true);
        if (d != null) { d.quals |= 1 << kind; FleetData.get(w).crewNote(w, r.name + " passed out on " + TRACTION[kind] + " (assessor)"); FleetData.get(w).markDirty(); }
        get(w).markDirty();
        return "§aPassed out on " + TRACTION[kind];
    }

    /** the crew-desk driver for this player (made when asked to) */
    static FleetData.Driver fleetDriver(World w, Rec r, boolean make) {
        FleetData f = FleetData.get(w);
        for (FleetData.Driver d : f.drivers.values()) if (r.id.equals(d.player)) return d;
        if (!make) return null;
        FleetData.Driver d = new FleetData.Driver();
        d.name = r.name;
        d.player = r.id;
        d.quals = 0;
        f.drivers.put(d.name, d);
        f.crewNote(w, d.name + " joined the roster (competency assessor)");
        return d;
    }

    // ---- the theory test ----------------------------------------------------------------------------------------------
    public static final class Q {
        public final String text;
        public final String[] ans;
        public final int correct;
        Q(String text, String[] ans, int correct) { this.text = text; this.ans = ans; this.correct = correct; }
    }

    private static final String[][] BANK = {
            {"A signal is dark (no lamps lit). You must:", "Treat it as STOP", "Proceed at caution", "Proceed at line speed", "Sound the horn and pass"},
            {"The AWS horn sounds on the approach. You must:", "Acknowledge and be ready to stop", "Ignore it if the signal is green", "Stop at once", "Call the depot"},
            {"You passed a signal at danger. First you:", "Stop and contact the signaller", "Reverse back behind it", "Carry on to the next station", "Reset the AWS"},
            {"A trip cock has tripped your train. You:", "Stop, reset the trip valve, call the signaller", "Release the brakes and carry on", "Reverse at once", "Isolate the brakes"},
            {"The vigilance buzzer sounds. You:", "Acknowledge within the time", "Wait for the penalty brake", "Switch off the desk", "Open the doors"},
            {"Radio link lost inside radio territory. You:", "Obey the fallback and stop if braked", "Drive on faster", "Turn off the radio", "Ignore it"},
            {"A balise telegram gives a lower limit ahead. You:", "Acknowledge and brake to the limit", "Keep line speed", "Stop dead", "Reverse"},
            {"A yellow (APPROACH) aspect means:", "Next signal may be at STOP", "Line clear two blocks", "Stop here", "Shunt only"},
            {"Before passing a signal at STOP you need:", "The signaller's authority", "A second driver", "The guard's whistle", "Nothing at all"},
            {"Train integrity lost (divided train). You:", "Stop and protect the line", "Couple up later", "Keep going", "Blow the horn"},
            {"An emergency stop plunger was operated ahead. You:", "Stop - all signals in the zone are at STOP", "Proceed at caution", "Ignore - only for staff", "Speed up"},
            {"A possession (line blocked) covers your route. You:", "Do not enter it", "Enter at 40 km/h", "Enter after sounding the horn", "Reverse into it"},
            {"Route knowledge of a line means:", "You know every signal along it", "You own a map", "You rode it once", "You know the stations"},
            {"A speed sign shows the limit for:", "The line from that sign onward", "Only that block", "Freight only", "Nothing - advisory"},
            {"Signal sighting too short means:", "Brake early to caution where it comes into view", "Drive faster", "Sighting does not matter", "Stop at every signal"},
            {"A red flashing lamp on the cab desk ALERT means:", "A penalty brake is applied", "The kettle is on", "The doors are open", "Radio OK"},
    };

    /** 10 questions: 4 live from this railway where it has the things to ask about, the rest from the rule book */
    public static List<Q> paper(World w, long seed) {
        Random rnd = new Random(seed);
        List<Q> live = new ArrayList<>();
        List<TileSignalMast> masts = new ArrayList<>(SignalRegistry.masts(w.provider.getDimension()));
        masts.removeIf(m -> m.isInvalid() || m.aspect() == Aspect.DARK);
        if (!masts.isEmpty()) {
            TileSignalMast m = masts.get(rnd.nextInt(masts.size()));
            Aspect a = m.aspect();
            String right = a.isStop() ? "Stop at it" : a.speedKmh >= 999 ? "Proceed at line speed" : a.speedKmh <= 30 ? "Proceed at restricted speed, ready to stop" : "Proceed, ready to stop at the next signal";
            live.add(q(rnd, "Signal " + SignalSighting.name(m) + " now shows " + a.label.toUpperCase(Locale.ROOT) + ". You:", right,
                    without(right, "Stop at it", "Proceed at line speed", "Proceed, ready to stop at the next signal", "Proceed at restricted speed, ready to stop")));
        }
        List<TileSpeedSign> signs = new ArrayList<>(SignalRegistry.speedSigns(w.provider.getDimension()));
        signs.removeIf(TileSpeedSign::isInvalid);
        if (!signs.isEmpty()) {
            TileSpeedSign s = signs.get(rnd.nextInt(signs.size()));
            int k = (int) Math.round(s.kmh());
            String right = k + " km/h";
            live.add(q(rnd, "The speed sign at " + s.getPos().getX() + "," + s.getPos().getZ() + " allows:", right,
                    new String[]{(k + 20) + " km/h", Math.max(10, k - 20) + " km/h", (k * 2) + " km/h"}));
        }
        FailSafe f = FailSafe.RADIO_LOSS;
        String fr = f.options[FailSafe.mode(w, f)];
        live.add(q(rnd, "On this railway, when a train's radio link is lost:", fr, without(fr, f.options[0], f.options[1], f.options[2], "Nothing happens")));
        int req = (int) Math.round(SignalSighting.required(w));
        live.add(q(rnd, "Here a signal must be in view for at least:", req + " m", new String[]{(req / 2) + " m", (req * 2) + " m", (req + 100) + " m"}));
        List<Q> out = new ArrayList<>(live);
        List<String[]> bank = new ArrayList<>(Arrays.asList(BANK));
        Collections.shuffle(bank, rnd);
        for (String[] b : bank) { if (out.size() >= 10) break; out.add(q(rnd, b[0], b[1], new String[]{b[2], b[3], b[4]})); }
        Collections.shuffle(out, rnd);
        return out;
    }

    private static String[] without(String right, String... all) {
        List<String> l = new ArrayList<>();
        for (String s : all) if (!s.equals(right) && !l.contains(s)) l.add(s);
        while (l.size() < 3) l.add("None of these");
        return l.subList(0, 3).toArray(new String[0]);
    }

    private static Q q(Random rnd, String text, String right, String[] wrong) {
        List<String> a = new ArrayList<>(Arrays.asList(wrong).subList(0, 3));
        int at = rnd.nextInt(4);
        a.add(at, right);
        return new Q(text, a.toArray(new String[0]), at);
    }

    /** a test finished with this score (%) */
    static String finish(World w, Rec r, int score, EntityPlayer who) {
        r.tests++;
        r.theoryLast = score;
        r.theoryAt = w.getTotalWorldTime();
        r.theoryBest = Math.max(r.theoryBest, score);
        boolean pass = score >= 80;
        String extra = "";
        if (pass && r.suspended) {
            r.suspended = false;
            r.suspendWhy = "";
            AlarmManager.get(w).clear("suspend:" + r.id);
            extra = " - suspension lifted";
            Recorder.event(w, "COMPETENCE: " + r.name + " reassessed (" + score + "%) - suspension lifted");
        }
        get(w).markDirty();
        Recorder.event(w, "THEORY TEST: " + r.name + " " + score + "% " + (pass ? "PASS" : "FAIL"));
        if (pass) QuestEvents.fire(w, who, who == null ? null : who.getPosition(), "theory_passed", "score", score, "tests", r.tests);
        return (pass ? "§aPASS " : "§cFAIL ") + score + "%" + extra;
    }

    // ---- save ---------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        recs.clear();
        NBTTagList l = t.getTagList("r", 10);
        for (int i = 0; i < l.tagCount(); i++) { Rec r = Rec.read(l.getCompoundTagAt(i)); if (r.id != null) recs.put(r.id, r); }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Rec r : recs.values()) l.appendTag(r.write());
        t.setTag("r", l);
        return t;
    }
}
