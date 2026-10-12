package com.dogpound.railmap.ohle;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.signal.BlockCatenary;
import com.dogpound.railmap.weather.Weather;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.*;

/**
 * The overhead line as an electrical and mechanical system (her ChatGPT list #31 + #32).
 * <p>
 * <b>Sections (#31).</b> Contact wire joined end to end is one electrical section. A Section Insulator or a Neutral
 * Section in the run splits it; a Motorised Isolator beside an insulator bridges it while CLOSED. A Track Feeder within
 * 4 blocks of the wire (below / beside it) feeds that section through its own OHLE feeder circuit breaker. A fault
 * (wire torn down, a flashover, an earthed section energised, a flooded third rail...) trips only the breakers feeding
 * the section it is in; after 3 s the breaker recloses once - first opening any isolator in AUTO round the faulted
 * section, so the healthy sections come back and the faulted one stays dead (fault isolation and restoration). Still
 * there: LOCKOUT until someone resets it. Lines with no insulators and no feeder near the wire keep the old radius
 * feeding, so old worlds run as before.
 * <p>
 * <b>Pantographs (#32).</b> Each electric loco's pantograph is raised (6 s, air) under wire, its main breaker (VCB)
 * closes once it sees line volts, opens through neutral sections (APC) and on a dead section, and the pantograph drops
 * on an earthed section or at a LOWER PAN magnet. While collecting, the contact is judged once a second from the wire's
 * height above the rail, its stagger off the pantograph centre, the mean contact force (static + aerodynamic,
 * EN 50367: F = 70 N + 0.00097 v²), the wire tension (balance weights, or a fixed termination that sags in heat), the
 * wave speed of the wire against the train speed, wire wear, carbon strip wear and ice on the wire. Poor contact
 * costs power and arcs: sparks, wear on the wire and the carbon, and - when the same loco or the same wire spot keeps
 * arcing - a maintenance alarm saying why.
 */
public final class Ohle {
    private Ohle() {}

    // ------------------------------------------------------------------------------------------------ blocks
    private static Item wireItem;
    private static Block wireBlock;

    public static Block wireBlock() {
        if (wireBlock == null) wireBlock = Block.getBlockFromName("irextras:catenary_wire");
        return wireBlock;
    }

    public static Item wireItem() {
        if (wireItem == null) wireItem = Item.getItemFromBlock(wireBlock());
        return wireItem;
    }

    static BlockCatenary.Kind kindAt(World w, BlockPos p) {
        if (!w.isBlockLoaded(p)) return null;
        Block b = w.getBlockState(p).getBlock();
        return b instanceof BlockCatenary c ? c.kind() : null;
    }

    // ------------------------------------------------------------------------------------------------ sections
    public static final class Section {
        public final Set<Long> wires = new HashSet<>();
        public final Set<Long> insulators = new LinkedHashSet<>(), neutrals = new LinkedHashSet<>();
        public final List<Long> feeders = new ArrayList<>();
        public final List<TileTensioner> tensioners = new ArrayList<>();
        public boolean sectioned, earthed, faulted, energised;
        public String why = "";
        public long anchor = Long.MAX_VALUE;

        /** no insulators, no feeders on the wire: the old radius feeding decides */
        public boolean legacy() { return !RailMapConfig.ohleSectioning || (!sectioned && feeders.isEmpty()); }

        public String name() {
            BlockPos p = BlockPos.fromLong(anchor);
            return "OLE " + p.getX() + "/" + p.getZ();
        }

        public String state() {
            return faulted ? "FAULT" : earthed ? "EARTHED" : legacy() ? "radius-fed" : energised ? "LIVE" : "DEAD";
        }

        /** wire tension, N: balance weights hold it; a fixed termination sags in heat and tightens in cold */
        public double tension(World w) {
            for (TileTensioner t : tensioners) if (t.holding()) return t.tensionN();
            double air = Weather.airC(w, BlockPos.fromLong(anchor));
            return Math.max(2000, Math.min(24000, 12000 - 218 * (air - 10)));
        }
    }

    private static final Map<Integer, Map<Long, Section>> CACHE = new HashMap<>();
    private static final Map<Integer, Long> CACHE_AT = new HashMap<>();
    private static int version;

    /** topology changed (an isolator moved, an earth went on, wire placed / removed): rebuild the sections */
    public static void dirty() { version++; }

    private static Map<Long, Section> cache(World w) {
        int dim = w.provider.getDimension();
        long key = (w.getTotalWorldTime() / 20) * 100_000L + version;
        if (!Objects.equals(CACHE_AT.get(dim), key)) { CACHE.put(dim, new HashMap<>()); CACHE_AT.put(dim, key); }
        return CACHE.get(dim);
    }

    /** the section this wire block belongs to (null if it isn't wire) */
    public static Section section(World w, BlockPos wire) {
        Map<Long, Section> c = cache(w);
        Section s = c.get(wire.toLong());
        if (s != null) return s;
        s = build(w, wire, false);
        if (s != null) for (long p : s.wires) c.put(p, s);
        return s;
    }

    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};

    /** flood-fill the wire; elementary = every insulator breaks it (isolators ignored) */
    static Section build(World w, BlockPos start, boolean elementary) {
        if (kindAt(w, start) != BlockCatenary.Kind.WIRE) return null;
        Section s = new Section();
        Deque<Long> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start.toLong());
        seen.add(start.toLong());
        while (!q.isEmpty() && s.wires.size() < 8000) {
            long lp = q.poll();
            BlockPos p = BlockPos.fromLong(lp);
            BlockCatenary.Kind k = kindAt(w, p);
            if (k == BlockCatenary.Kind.WIRE) { s.wires.add(lp); if (lp < s.anchor) s.anchor = lp; }
            for (int[] d : STEPS) {
                BlockPos n = p.add(d[0], d[1], d[2]);
                long ln = n.toLong();
                if (seen.contains(ln)) continue;
                BlockCatenary.Kind nk = kindAt(w, n);
                if (nk == null || !nk.line()) continue;
                seen.add(ln);
                if (nk == BlockCatenary.Kind.WIRE) q.add(ln);
                else if (nk == BlockCatenary.Kind.SECTION_INSULATOR) {
                    s.insulators.add(ln);
                    s.sectioned = true;
                    if (!elementary && bridged(w, n)) q.add(ln);      // a closed isolator carries the current round it
                } else if (nk == BlockCatenary.Kind.NEUTRAL_SECTION) {
                    s.neutrals.add(ln);
                    s.sectioned = true;
                }
            }
        }
        if (elementary) return s;
        OhleData d = OhleData.get(w);
        GridData g = GridData.get(w);
        for (Map.Entry<Long, GridData.Node> en : g.nodes().entrySet()) {
            GridData.Node n = en.getValue();
            if (n.kind != GridKind.FEEDER || !(n.feeds.equals("Wire") || n.feeds.equals("Auto"))) continue;
            if (touches(s, BlockPos.fromLong(en.getKey()), 4, -1, 10)) s.feeders.add(en.getKey());
        }
        for (TileTensioner t : TileTensioner.all(w)) if (touches(s, t.getPos(), 4, -2, 10)) s.tensioners.add(t);
        for (TileIsolator i : TileIsolator.all(w)) if (i.earthed() && s.wires.contains(i.earthPos())) { s.earthed = true; s.why = "earthed at isolator " + xz(i.getPos()); }
        for (OhleData.Fault f : d.faults) if (f.persistent && s.wires.contains(f.pos)) { s.faulted = true; s.why = f.kind + ": " + f.why; }
        for (long p : d.damaged) if (s.wires.contains(p)) { s.faulted = true; if (s.why.isEmpty()) s.why = "wire down at " + xz(BlockPos.fromLong(p)); }
        for (long f : s.feeders) if (d.cbClosed(f) && feederLive(w, g, f)) s.energised = true;
        if (!s.energised && !s.legacy() && s.why.isEmpty()) s.why = s.feeders.isEmpty() ? "no Track Feeder on this section" : "feeder breaker open / no supply";
        if (s.earthed || s.faulted) s.energised = false;
        return s;
    }

    private static boolean touches(Section s, BlockPos f, int r, int dy0, int dy1) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = dy0; dy <= dy1; dy++)
                    if (s.wires.contains(f.add(dx, dy, dz).toLong())) return true;
        return false;
    }

    static boolean feederLive(World w, GridData g, long f) {
        GridData.Node n = g.nodes().get(f);
        if (n == null || n.kind != GridKind.FEEDER) return false;
        return n.energy > 0 || GridData.why(g.netOf(BlockPos.fromLong(f)), true).isEmpty();
    }

    /** a closed isolator is linked to this insulator */
    static boolean bridged(World w, BlockPos ins) {
        for (TileIsolator i : TileIsolator.all(w)) if (i.link() == ins.toLong() && i.closed()) return true;
        return false;
    }

    /** the sections on either side of a boundary block (or the section of a wire) */
    public static List<Section> around(World w, BlockPos p) {
        List<Section> out = new ArrayList<>();
        if (kindAt(w, p) == BlockCatenary.Kind.WIRE) { Section s = section(w, p); if (s != null) out.add(s); return out; }
        for (int[] d : STEPS) {
            BlockPos n = p.add(d[0], d[1], d[2]);
            if (kindAt(w, n) != BlockCatenary.Kind.WIRE) continue;
            Section s = section(w, n);
            if (s != null && !out.contains(s)) out.add(s);
        }
        return out;
    }

    /** every section anyone can see: those with a feeder, an isolator or a tensioner on them */
    public static List<Section> known(World w) {
        List<Section> out = new ArrayList<>();
        List<BlockPos> seeds = new ArrayList<>();
        for (Map.Entry<Long, GridData.Node> en : GridData.get(w).nodes().entrySet())
            if (en.getValue().kind == GridKind.FEEDER) seeds.add(BlockPos.fromLong(en.getKey()));
        for (TileIsolator i : TileIsolator.all(w)) if (i.link() != 0) seeds.add(BlockPos.fromLong(i.link()));
        for (TileTensioner t : TileTensioner.all(w)) seeds.add(t.getPos());
        for (BlockPos s : seeds)
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++)
                    for (int dy = -1; dy <= 10; dy++) {
                        BlockPos p = s.add(dx, dy, dz);
                        if (kindAt(w, p) != BlockCatenary.Kind.WIRE) continue;
                        Section x = section(w, p);
                        if (x != null && !out.contains(x)) out.add(x);
                    }
        out.sort(Comparator.comparingLong(x -> x.anchor));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ faults + protection
    /** record a fault on the line at pos */
    public static void fault(World w, BlockPos pos, String kind, String why, boolean persistent) {
        OhleData d = OhleData.get(w);
        for (OhleData.Fault f : d.faults) if (f.pos == pos.toLong() && f.kind.equals(kind)) return;
        OhleData.Fault f = new OhleData.Fault();
        f.pos = pos.toLong(); f.kind = kind; f.why = why; f.persistent = persistent; f.since = w.getTotalWorldTime();
        d.faults.add(f);
        d.log(w, "FAULT " + kind + " at " + xyz(pos) + ": " + why);
        Recorder.event(w, "OHLE " + kind + " FAULT at " + xyz(pos) + ": " + why);
        if (persistent) AlarmManager.get(w).raise(w, "ohlef:" + pos.toLong(), AlarmManager.Cat.FAULT, AlarmManager.Sev.MAJOR,
                "OHLE " + kind + " fault at " + xyz(pos) + ": " + why, pos, true);
        dirty();
    }

    /** once a second (from the traction tick): protection on every fault, auto-reclose, earthed-section checks */
    public static void tick(World w) {
        if (!RailMapConfig.ohleSectioning) return;
        OhleData d = OhleData.get(w);
        long now = w.getTotalWorldTime();
        GridData g = GridData.get(w);
        // an earthed section someone made live: a bolted fault on the earth
        for (TileIsolator i : TileIsolator.all(w)) {
            if (!i.earthed() || i.earthPos() == 0) continue;
            Section s = section(w, BlockPos.fromLong(i.earthPos()));
            if (s == null) continue;
            for (long f : s.feeders) if (d.cbClosed(f) && feederLive(w, g, f)) { fault(w, BlockPos.fromLong(i.earthPos()), "EARTH", "earthed section energised (earth switch at " + xz(i.getPos()) + " is ON)", false); break; }
        }
        // protection: every breaker feeding a faulted section trips
        for (Iterator<OhleData.Fault> it = d.faults.iterator(); it.hasNext(); ) {
            OhleData.Fault f = it.next();
            BlockPos p = BlockPos.fromLong(f.pos);
            if (!w.isBlockLoaded(p)) continue;
            boolean live = false;
            for (Section s : around(w, p))
                for (long fd : s.feeders) {
                    if (!d.cbClosed(fd) || !feederLive(w, g, fd)) continue;
                    live = true;
                    trip(w, d, fd, f, s);
                }
            if (!f.persistent) { it.remove(); dirty(); }            // the arc goes out once nothing feeds it
            else if (kindAt(w, p) == null) { it.remove(); AlarmManager.get(w).clear("ohlef:" + f.pos); dirty(); }
            if (live) d.markDirty();
        }
        // auto-reclose after isolating the faulted section
        for (Map.Entry<Long, OhleData.Cb> en : d.cbs.entrySet()) {
            OhleData.Cb cb = en.getValue();
            if (cb.state == 0 && cb.shots > 0 && now - cb.closedAt > 1200) { cb.shots = 0; d.markDirty(); }
            if (cb.state != 1 || now < cb.recloseAt) continue;
            int opened = isolateFaults(w, d);
            boolean moving = false;
            for (TileIsolator i : TileIsolator.all(w)) moving |= i.moving();
            if (opened > 0 || moving) {                              // the motors take 4 s: reclose once they are open
                cb.recloseAt = now + 20;
                if (opened > 0) d.log(w, "feeder " + xz(BlockPos.fromLong(en.getKey())) + ": " + opened + " AUTO isolator(s) opening round the fault, reclose after");
                continue;
            }
            cb.state = 0;
            cb.shots++;
            cb.closedAt = now;
            String msg = "feeder " + xz(BlockPos.fromLong(en.getKey())) + " AUTO-RECLOSE" + (opened > 0 ? " after opening " + opened + " isolator(s) round the fault" : "");
            d.log(w, msg);
            Recorder.event(w, "OHLE " + msg);
            AlarmManager.get(w).clear("ohlecb:" + en.getKey());
            dirty();
        }
    }

    private static void trip(World w, OhleData d, long feeder, OhleData.Fault f, Section s) {
        OhleData.Cb cb = d.cb(feeder);
        long now = w.getTotalWorldTime();
        boolean lockout = !RailMapConfig.ohleAutoReclose || cb.shots > 0 && now - cb.closedAt < 600;
        cb.state = lockout ? 2 : 1;
        cb.recloseAt = now + 60;
        cb.trips++;
        cb.lastTrip = now;
        cb.last = f.kind + " " + f.why;
        try { com.dogpound.railmap.infra.FaultLocator.onTrip(w, feeder, f, s); } catch (RuntimeException e) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] fault locator: {}", e.toString()); }   // #39 distance to fault
        BlockPos fp = BlockPos.fromLong(feeder);
        String msg = "OHLE feeder " + xz(fp) + " TRIPPED (" + f.kind + " fault at " + xyz(BlockPos.fromLong(f.pos)) + ", section " + s.name() + ")"
                + (lockout ? " - LOCKOUT, reset at the Feeding Diagram / console" : " - auto-reclose in 3 s");
        d.log(w, msg);
        Recorder.event(w, msg);
        AlarmManager.get(w).raise(w, "ohlecb:" + feeder, AlarmManager.Cat.POWER, lockout ? AlarmManager.Sev.CRITICAL : AlarmManager.Sev.MAJOR, msg, fp, true);
        for (EntityPlayer pl : w.playerEntities) if (pl.getDistanceSq(fp) < 128 * 128) pl.sendMessage(new TextComponentString("§c⚡ " + msg));
        dirty();
    }

    /** open AUTO isolators round each persistently faulted elementary section; returns how many moved */
    static int isolateFaults(World w, OhleData d) {
        int n = 0;
        for (OhleData.Fault f : d.faults) {
            if (!f.persistent) continue;
            BlockPos p = BlockPos.fromLong(f.pos);
            Section e = build(w, p, true);
            if (e == null) continue;
            for (long ins : e.insulators)
                for (TileIsolator i : TileIsolator.all(w))
                    if (i.link() == ins && i.closed() && i.auto() && i.autoOpen("fault isolation: " + f.kind + " at " + xyz(p))) n++;
        }
        for (long p : d.damaged) {
            Section e = build(w, BlockPos.fromLong(p), true);
            if (e == null) continue;
            for (long ins : e.insulators)
                for (TileIsolator i : TileIsolator.all(w))
                    if (i.link() == ins && i.closed() && i.auto() && i.autoOpen("fault isolation: wire down at " + xyz(BlockPos.fromLong(p)))) n++;
        }
        if (n > 0) dirty();
        return n;
    }

    /** console / diagram / command: close (reset) or open a feeder's OHLE breaker */
    public static String feederOp(World w, long feeder, boolean close, String who) {
        OhleData d = OhleData.get(w);
        OhleData.Cb cb = d.cb(feeder);
        String where = xz(BlockPos.fromLong(feeder));
        if (!close) {
            cb.state = 3;
            d.log(w, "feeder " + where + " OPENED by " + who);
            dirty();
            return "§eFeeder " + where + " breaker OPEN";
        }
        cb.state = 0;
        cb.shots = 1;                                   // closing onto a standing fault locks it straight back out
        cb.closedAt = w.getTotalWorldTime();
        AlarmManager.get(w).clear("ohlecb:" + feeder);
        d.log(w, "feeder " + where + " CLOSED by " + who);
        dirty();
        return "§aFeeder " + where + " breaker CLOSED";
    }

    // ------------------------------------------------------------------------------------------------ the pantograph
    public static final class Contact {
        /** a contact line block within the pantograph's reach above the loco */
        public boolean underWire;
        /** pantograph up, VCB closed, line live: the loco can draw */
        public boolean collecting;
        /** feeders whose breakers feed this section (null = old radius feeding) */
        public Set<Long> feeders;
        /** share of traction lost to poor contact this second */
        public float loss;
        public String note = "";
        public Section section;
    }

    private static final double MASS_PER_M = 0.95;      // kg/m, 107 mm² copper-silver contact wire

    /**
     * Once a second for every electric loco under / near overhead line: the pantograph sequence, which section it
     * collects from and how well. {@code drawing}: it wants current (motoring or braking electrically).
     */
    public static Contact contact(World w, LocomotiveDiesel loco, BlockPos at, boolean drawing, float throttle, long now) {
        Contact c = new Contact();
        OhleData d = OhleData.get(w);
        OhleData.Pan p = d.pan(loco.getUUID());
        if (p.name.isEmpty() && loco.getDefinition() != null) p.name = loco.getDefinition().name();
        Vec3d pos = loco.getPosition();
        double v = loco.getCurrentSpeed() == null ? 0 : Math.abs(loco.getCurrentSpeed().metersPerSecond());
        // the line overhead: the block nearest the pantograph centre within its reach
        BlockPos best = null;
        double bestOff = 9;
        BlockCatenary.Kind bestKind = null;
        boolean neutralNear = false;
        int maxDy = (int) Math.floor(RailMapConfig.ohlePanReachM + 0.5);
        for (int dy = 2; dy <= Math.max(7, maxDy); dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos q = at.add(dx, dy, dz);
                    BlockCatenary.Kind k = kindAt(w, q);
                    if (k == null || !k.line()) continue;
                    double ox = q.getX() + 0.5 - pos.x, oz = q.getZ() + 0.5 - pos.z, off = Math.sqrt(ox * ox + oz * oz);
                    if (k == BlockCatenary.Kind.NEUTRAL_SECTION && off < 3.2) neutralNear = true;
                    if (off < bestOff) { bestOff = off; best = q; bestKind = k; }
                }
        c.underWire = best != null && bestOff <= 2.2;
        if (c.underWire) p.lastWire = now;
        boolean detail = RailMapConfig.ohleContactDetail;
        // magnets beside the track: LOWER / RAISE pantograph, COAST (open the VCB) - first pass only
        boolean nearAny = false;
        for (TileApcMagnet m : TileApcMagnet.all(w)) {
            BlockPos mp = m.getPos();
            double dx = mp.getX() + 0.5 - pos.x, dz = mp.getZ() + 0.5 - pos.z, d2 = dx * dx + dz * dz;
            if (Math.abs(mp.getY() - pos.y) > 4) continue;
            if (d2 < 8 * 8) nearAny = true;
            if (d2 > 3.5 * 3.5 || p.lastMagnet == mp.toLong()) continue;
            p.lastMagnet = mp.toLong();
            String said = m.passed(loco, p, now);
            if (!said.isEmpty()) tell(loco, said);
        }
        if (!nearAny) p.lastMagnet = 0;
        // ---- pantograph sequence
        if (!detail) { p.up = c.underWire; p.vcb = c.underWire; }
        else {
            boolean want = p.manual ? p.wantUp : !p.downZone && now - p.lastWire < 60;     // AUTO: up under wire, down 3 s after it ends
            if (p.broken) want = false;
            if (want && !p.up) {
                if (p.raiseAt == 0) { p.raiseAt = now; w.playSound(null, at, com.dogpound.railmap.sound.PackSounds.ev("train.pantograph_up"), SoundCategory.NEUTRAL, 0.8f, 1.0f); }
                if (now < p.raiseAt) c.note = "pantograph held down (section was earthed)";
                else if (now - p.raiseAt >= 120) { p.up = true; p.raiseAt = 0; }
                else c.note = "pantograph raising " + (now - p.raiseAt) / 20 + "/6 s";
            } else if (!want && p.up) {
                p.up = false; p.vcb = false; p.raiseAt = 0;
                w.playSound(null, at, com.dogpound.railmap.sound.PackSounds.ev("train.pantograph_down"), SoundCategory.NEUTRAL, 0.8f, 1.0f);
            } else if (!want) p.raiseAt = 0;
        }
        if (!c.underWire) {
            p.vcb = false;
            if (p.up && detail && c.note.isEmpty()) c.note = "pantograph up - no wire overhead";
            p.lastH = Double.NaN;
            return c;
        }
        // ---- which section
        Section s = null;
        boolean overInsulator = bestKind == BlockCatenary.Kind.SECTION_INSULATOR;
        if (bestKind == BlockCatenary.Kind.WIRE) s = section(w, best);
        else if (bestKind != null) {
            List<Section> sides = around(w, best);
            Section live = null, earthed = null;
            for (Section x : sides) { if (x.energised || x.legacy() && !x.faulted && !x.earthed) live = x; if (x.earthed) earthed = x; }
            if (overInsulator && p.up && live != null && earthed != null && !live.legacy())
                fault(w, best, "SHORT", "pantograph of " + p.name + " bridged a live and an earthed section at the insulator", false);
            s = overInsulator ? live : null;
        }
        c.section = s;
        boolean volts = s != null && (s.legacy() ? !s.faulted && !s.earthed : s.energised);
        if (s != null && !s.legacy()) { c.feeders = new HashSet<>(); for (long f : s.feeders) if (d.cbClosed(f)) c.feeders.add(f); }
        // protection: an earthed section drops an AUTO pantograph; a dead one opens the VCB on no-volt
        if (s != null && s.earthed && p.up && !p.manual) { p.up = false; p.vcb = false; p.raiseAt = now + 200; c.note = "SECTION EARTHED - pantograph lowered"; }
        // ---- the main breaker: APC through neutral sections, no-volt release, 1 s to close once volts are seen
        boolean apc = neutralNear && com.dogpound.railmap.infra.NeutralSections.apc(p.manual);      // #36: on-board APC opens it in MANUAL too (InfraConfig.cabAutoVcb)
        if (bestKind == BlockCatenary.Kind.NEUTRAL_SECTION) com.dogpound.railmap.infra.NeutralSections.passing(w, best, loco.getUUID(), p.up && p.vcb && throttle > 0.05f, now);
        else com.dogpound.railmap.infra.NeutralSections.clear(loco.getUUID());
        if (bestKind == BlockCatenary.Kind.NEUTRAL_SECTION && p.up && p.vcb && throttle > 0.05f) {
            fault(w, best, "SHORT", p.name + " drew power across the neutral section (phase-to-phase flashover)", false);
            arc(w, best, 20);
        }
        if (!p.up || !volts || apc) {
            if (p.vcb && !volts && p.up) c.note = s == null ? "NEUTRAL SECTION - coasting" : "NO LINE VOLTS - " + s.name() + " " + s.state() + (s.why.isEmpty() ? "" : ": " + s.why);
            p.vcb = false;
            p.vcbAt = 0;
            if (apc && c.note.isEmpty()) c.note = "NEUTRAL SECTION - APC opened the VCB, coasting";
            else if (!volts && p.up && c.note.isEmpty()) c.note = s == null ? "NEUTRAL SECTION - no volts" : "NO LINE VOLTS - " + s.name() + " " + s.state() + (s.why.isEmpty() ? "" : ": " + s.why);
            if (!p.up && c.note.isEmpty() && detail) c.note = p.broken ? "PANTOGRAPH BROKEN - service it" : p.downZone ? "pantograph down (LOWER PAN zone)" : "pantograph down";
            return c;
        }
        if (!p.vcb) {
            if (p.vcbAt == 0) p.vcbAt = now;
            if (now - p.vcbAt < 20 && detail) { c.note = "VCB closing"; return c; }
            p.vcb = true;
        }
        c.collecting = true;
        if (!detail || best == null) return c;
        // ---- the contact itself
        double h = best.getY() + 0.125 - pos.y;
        double fm = p.forceN + 0.00097 * Math.pow(v * 3.6, 2);
        double tension = s == null ? 12000 : s.tension(w);
        double wave = Math.sqrt(tension / MASS_PER_M);
        float wear = d.wear.getOrDefault(best.toLong(), 0f);
        double ice = Weather.iceMm(w, best);
        double[] q = {
                Math.max(0, (bestOff - 0.45) / 0.55),                                  // stagger: off the pantograph's working width
                h < 4.7 ? (4.7 - h) / 0.8 : h > 6.2 ? (h - 6.2) / Math.max(0.3, RailMapConfig.ohlePanReachM - 6.2) : 0,   // height
                Math.max(0, v / (0.7 * wave) - 1) * 2,                                 // wave speed (tension too low for the speed)
                p.forceN < 55 ? (55 - p.forceN) / 55.0 : 0,                             // too little contact force
                Math.max(0, (wear - 0.6) / 0.4) * 0.6,                                  // worn wire
                p.carbon < 0.15f ? 0.5 : 0,                                             // carbon strip worn out
                Math.min(0.8, ice * 0.25),                                              // ice on the wire
                Double.isNaN(p.lastH) || v < 1 ? 0 : Math.max(0, Math.abs(h - p.lastH) / Math.max(1, v) / grade(v * 3.6) - 1) * 0.3,   // height change too steep for the speed
                d.damaged.contains(best.toLong()) ? 1 : 0};
        String[] whyQ = {"stagger " + fmt(bestOff) + " m off centre", "wire height " + fmt(h) + " m", "speed near the wire's wave speed (" + Math.round(wave * 3.6) + " km/h, tension " + Math.round(tension / 100) / 10.0 + " kN)",
                "contact force only " + p.forceN + " N", "wire worn " + Math.round(wear * 100) + "%", "carbon strip worn out", "ice on the wire " + fmt(ice) + " mm", "steep wire gradient", "damaged wire"};
        double good = 1;
        int worst = 0;
        for (int i = 0; i < q.length; i++) { q[i] = Math.min(1, q[i]); good *= 1 - q[i]; if (q[i] > q[worst]) worst = i; }
        double bad = 1 - good;
        p.lastH = h;
        // wear: every wire block the pantograph ran under since last second
        double base = 1.0 / Math.max(500, RailMapConfig.ohleWirePasses) * Math.pow(fm / 90.0, 1.5) * (p.forceN > 140 ? Math.pow(p.forceN / 90.0, 2) : 1);
        long last = Double.isNaN(p.lastX) ? 0 : new BlockPos(p.lastX, best.getY(), p.lastZ).toLong();
        int steps = (int) Math.min(60, Math.ceil(Math.sqrt(sq(pos.x - p.lastX) + sq(pos.z - p.lastZ))));
        if (!Double.isNaN(p.lastX) && steps > 0) {
            for (int i = 1; i <= steps; i++) {
                double fx = p.lastX + (pos.x - p.lastX) * i / steps, fz = p.lastZ + (pos.z - p.lastZ) * i / steps;
                BlockPos b = new BlockPos(fx, best.getY(), fz);
                if (b.toLong() == last || kindAt(w, b) != BlockCatenary.Kind.WIRE) continue;
                last = b.toLong();
                wearAdd(w, d, b, (float) (base * (1 + bad * 3)));
            }
            p.carbon = Math.max(0, p.carbon - (float) (steps / 5_000_000.0 * (fm / 90.0)));
        }
        p.lastX = pos.x;
        p.lastZ = pos.z;
        if (ice > 0) Weather.scrapeIce(w, best, 0.05);
        // poor contact: power dips, arcs when current is flowing
        c.loss = (float) Math.min(0.6, bad * 0.5);
        if (bad > 0.05 && drawing && w.rand.nextDouble() < bad) {
            arc(w, best, (int) (4 + bad * 12));
            wearAdd(w, d, best, (float) (base * 30));
            p.carbon = Math.max(0, p.carbon - 0.002f);
            p.arcs[p.arcN++ % p.arcs.length] = now;
            spot(w, d, best, loco.getUUID(), whyQ[worst], now);
            c.note = "ARCING - " + whyQ[worst];
            int recent = 0;
            for (long t : p.arcs) if (t > 0 && now - t < 2400) recent++;
            if (recent >= 5 && !p.alerted) {
                p.alerted = true;
                AlarmManager.get(w).raise(w, "pan:" + loco.getUUID(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR,
                        p.name + ": poor current collection - " + recent + " arcs in 2 min (" + whyQ[worst] + "), carbon strip " + Math.round(p.carbon * 100) + "%, force " + p.forceN + " N - inspect the pantograph",
                        at, true);
                d.log(w, p.name + ": repeated poor contact - " + whyQ[worst]);
            }
        } else if (bad > 0.1) c.note = "poor contact - " + whyQ[worst];
        if (p.alerted) {
            long newest = 0;
            for (long t : p.arcs) newest = Math.max(newest, t);
            if (now - newest > 6000) { p.alerted = false; AlarmManager.get(w).clear("pan:" + loco.getUUID()); }
        }
        // dewirement (harsh, off by default): the pantograph runs off the wire at speed, or hits torn wire
        if (RailMapConfig.ohleDewirement && v > 8 && (bestOff > 0.9 || d.damaged.contains(best.toLong()) || h < 4.2) && w.rand.nextDouble() < 0.35) dewire(w, d, p, loco, best, pos);
        d.markDirty();
        return c;
    }

    /** largest wire gradient a pantograph follows at this speed (EN 50119 style) */
    static double grade(double kmh) {
        return kmh <= 50 ? 1 / 40.0 : kmh <= 60 ? 1 / 83.0 : kmh <= 100 ? 1 / 167.0 : kmh <= 120 ? 1 / 250.0 : kmh <= 160 ? 1 / 300.0 : 1 / 500.0;
    }

    private static void dewire(World w, OhleData d, OhleData.Pan p, LocomotiveDiesel loco, BlockPos wire, Vec3d pos) {
        p.broken = true; p.up = false; p.vcb = false;
        int n = 0;
        for (int i = -3; i <= 3; i++)
            for (BlockPos b : new BlockPos[]{wire.add(i, 0, 0), wire.add(0, 0, i)})
                if (kindAt(w, b) == BlockCatenary.Kind.WIRE && d.damaged.add(b.toLong())) n++;
        arc(w, wire, 40);
        w.playSound(null, wire, com.dogpound.railmap.ModSounds.ARC_FLASH, SoundCategory.BLOCKS, 0.8f, 1.0f);
        fault(w, wire, "EARTH", "DEWIREMENT - " + p.name + " tore down " + n + " m of contact wire", true);
        tell(loco, "§c⚡ DEWIREMENT - the pantograph tore the wire down. Pantograph broken: /irextras ohle pan service");
    }

    private static void wearAdd(World w, OhleData d, BlockPos b, float add) {
        float nw = d.wear.getOrDefault(b.toLong(), 0f) + add;
        d.wear.put(b.toLong(), nw);
        if (nw >= 0.7f && nw - add < 0.7f)
            AlarmManager.get(w).raise(w, "ohlew:" + b.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "Contact wire worn " + Math.round(nw * 100) + "% at " + xyz(b) + " - renew it (dead + earthed section, right-click with Contact Wire)", b, true);
        if (nw >= 1f && nw - add < 1f && RailMapConfig.ohleWireBreaks) {
            d.damaged.add(b.toLong());
            fault(w, b, "EARTH", "contact wire worn through and SNAPPED", true);
        }
    }

    /** the same wire spot arcing under several trains: a hard spot / misalignment to inspect */
    private static void spot(World w, OhleData d, BlockPos b, UUID loco, String why, long now) {
        long[] s = d.spots.computeIfAbsent(b.toLong(), k -> new long[4]);
        if (now - s[1] > 12000) { s[0] = 0; s[1] = now; s[2] = 0; s[3] = 0; }
        s[0]++;
        s[2] |= 1L << (Math.abs(loco.hashCode()) % 63);
        if (s[0] >= 3 && Long.bitCount(s[2]) >= 2 && s[3] == 0 && (s[3] = 1) == 1)
            AlarmManager.get(w).raise(w, "ohles:" + b.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "OHLE defect at " + xyz(b) + ": " + s[0] + " arcs from " + Long.bitCount(s[2]) + " trains in 10 min - " + why, b, false);
    }

    /** sparks and the crack of an arc at the contact point */
    static void arc(World w, BlockPos b, int n) {
        if (w instanceof WorldServer ws) {
            ws.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, b.getX() + 0.5, b.getY() + 0.1, b.getZ() + 0.5, n, 0.15, 0.05, 0.15, 0.08);
            ws.spawnParticle(EnumParticleTypes.CRIT_MAGIC, b.getX() + 0.5, b.getY() + 0.1, b.getZ() + 0.5, n / 2 + 1, 0.1, 0.05, 0.1, 0.2);
        }
        w.playSound(null, b, com.dogpound.railmap.ModSounds.ARC_BUZZ, SoundCategory.BLOCKS, 0.5f, 1.0f);
    }

    static void tell(LocomotiveDiesel loco, String msg) {
        try {
            for (cam72cam.immersiverailroading.entity.EntityRollingStock car : loco.getTrain())
                for (cam72cam.mod.entity.Entity e : car.getPassengers())
                    if (e.internal instanceof net.minecraft.entity.player.EntityPlayerMP mp) mp.sendStatusMessage(new TextComponentString(msg), true);
        } catch (RuntimeException | LinkageError ignored) { }
    }

    // ------------------------------------------------------------------------------------------------ repair
    /** right-click a wire with Contact Wire: renew it (worn / torn down). Live = refused, and it bites */
    public static String renew(World w, BlockPos pos, EntityPlayer pl, ItemStack held) {
        OhleData d = OhleData.get(w);
        float wr = d.wear.getOrDefault(pos.toLong(), 0f);
        boolean down = d.damaged.contains(pos.toLong());
        for (Section s : around(w, pos)) {
            if (s.energised || s.legacy() && !s.faulted && !s.earthed && liveByRadius(w, pos)) {
                if (!pl.capabilities.isCreativeMode) pl.attackEntityFrom(DamageSource.LIGHTNING_BOLT, 8f);
                arc(w, pos, 30);
                d.log(w, pl.getName() + " touched LIVE contact wire at " + xyz(pos));
                return "§c⚡ That wire is LIVE (" + s.name() + "). Open its feeder / isolator and earth it first";
            }
        }
        if (wr < 0.05f && !down) return "§7Contact wire at " + xyz(pos) + ": " + Math.round(wr * 100) + "% worn - fine";
        boolean earthed = false;
        for (Section s : around(w, pos)) earthed |= s.earthed;
        if (!pl.capabilities.isCreativeMode) held.shrink(1);
        d.wear.remove(pos.toLong());
        d.damaged.remove(pos.toLong());
        if (down) {
            d.faults.removeIf(f -> f.pos == pos.toLong());
            AlarmManager.get(w).clear("ohlef:" + pos.toLong());
            for (OhleData.Fault f : new ArrayList<>(d.faults)) if (f.persistent && f.kind.equals("EARTH") && f.why.startsWith("DEWIREMENT")) {
                boolean any = false;
                for (long p : d.damaged) if (BlockPos.fromLong(p).distanceSq(BlockPos.fromLong(f.pos)) < 64) any = true;
                if (!any) { d.faults.remove(f); AlarmManager.get(w).clear("ohlef:" + f.pos); }
            }
        }
        AlarmManager.get(w).clear("ohlew:" + pos.toLong());
        d.log(w, pl.getName() + " renewed the contact wire at " + xyz(pos) + (earthed ? "" : " (section dead but NOT earthed - permit rule broken)"));
        Recorder.event(w, "OHLE wire renewed at " + xyz(pos) + " by " + pl.getName());
        d.markDirty();
        dirty();
        return (earthed ? "§a" : "§e") + "Contact wire renewed" + (down ? " - torn wire replaced" : " (was " + Math.round(wr * 100) + "% worn)") + (earthed ? "" : " - next time earth the section first");
    }

    /** worn 5 % or more, or torn down (client: never - the server decides) */
    public static boolean needsRenewal(World w, BlockPos pos) {
        if (w.isRemote) return false;
        OhleData d = OhleData.get(w);
        return d.damaged.contains(pos.toLong()) || d.wear.getOrDefault(pos.toLong(), 0f) >= 0.05f;
    }

    private static boolean liveByRadius(World w, BlockPos pos) {
        return com.dogpound.railmap.grid.GridTicker.whyDead(w, pos, true).isEmpty() && !GridData.get(w).nodes().isEmpty();
    }

    public static void removed(World w, BlockPos pos) {
        OhleData d = OhleData.get(w);
        if (d.wear.remove(pos.toLong()) != null | d.damaged.remove(pos.toLong())) d.markDirty();
        dirty();
    }

    // ------------------------------------------------------------------------------------------------ helpers
    static double sq(double a) { return a * a; }
    static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    public static String xz(BlockPos p) { return p.getX() + "," + p.getZ(); }
    public static String xyz(BlockPos p) { return p.getX() + "," + p.getY() + "," + p.getZ(); }
}
