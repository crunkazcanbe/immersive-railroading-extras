package com.dogpound.railmap.station;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.incident.Announcer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Platform Zone Marker (ChatGPT batch 3 #29): the lettered A-H sign hung along a platform, one per coach-length zone.
 * The markers of a station together are its zone plan: their spacing is the zone length, their count x spacing the
 * platform length, the STOP key marks the end the trains stop at, the ACCESS key an accessible boarding point (ramp
 * and staff). As a train comes within two minutes, the station hears where it will stop - "formed of 8 coaches, stopping
 * at zones A to F, accessible boarding at zone C" - and, if it is longer than the platform, which rear coaches won't be
 * at the platform. Each marker lights COACH HERE for its zone (predicted on approach, measured once it stands) and
 * shows the coach number beside it.
 */
public class TileZoneMarker extends PlatformTile {
    static final String LETTERS = "ABCDEFGH";

    /** a station's predicted stop: which markers the train covers, its coaches, how many are off the platform */
    static final class Pred {
        final Set<Long> covered = new HashSet<>();
        int cars, offRear;
        double trainM, platM;
        String text = "", train = "";
        long at;
    }
    private static final Map<Long, Pred> PRED = new HashMap<>();
    private static final Map<String, Long> SAID = new HashMap<>();

    private int coachHere;
    private String lastSaid = "";

    @Override protected long load() { return 1; }
    @Override public String wiredName() { return "Platform zone marker"; }
    @Override public String gearState() { return where() + " zone " + letter() + (access() ? " (accessible boarding)" : "") + (stop() ? " (stop end)" : ""); }
    @Override protected boolean keepTextUnpowered() { return true; }     // the painted letter is always there

    public char letter() { return LETTERS.charAt(Math.floorMod(ctl.get("s_zone"), LETTERS.length())); }
    public boolean access() { return ctl.get("k_acc") == 1; }
    public boolean stop() { return ctl.get("k_stop") == 1; }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "ann" -> {
                Pred p = predictNext(world, station, stationName, true);
                return p == null ? "§7No train due at " + where() : "§aAnnounced: " + p.text;
            }
            default -> { return null; }
        }
    }

    /** the station's markers, sorted by letter */
    static List<TileZoneMarker> plan(World w, long station) {
        List<TileZoneMarker> l = at(w, TileZoneMarker.class, station);
        l.sort(Comparator.comparingInt(m -> m.letter()));
        return l;
    }

    /** zone length (m): the mean spacing of consecutive markers, 20 m with one marker */
    static double spacing(List<TileZoneMarker> l) {
        if (l.size() < 2) return 20;
        double s = 0;
        for (int i = 1; i < l.size(); i++) s += Math.sqrt(l.get(i).getPos().distanceSq(l.get(i - 1).getPos()));
        return Math.max(4, s / (l.size() - 1));
    }

    /** the zone plan's stopping end: the STOP marker, else the one nearest the station's stop rail */
    static TileZoneMarker stopEnd(List<TileZoneMarker> l, long station) {
        for (TileZoneMarker m : l) if (m.stop()) return m;
        BlockPos sp = BlockPos.fromLong(station);
        return l.stream().min(Comparator.comparingDouble(m -> m.getPos().distanceSq(sp))).orElse(null);
    }

    /** the zones a train of these coach lengths covers from the stopping end, and how many rear coaches are off the end */
    static Pred cover(List<TileZoneMarker> l, long station, double[] coaches) {
        Pred p = new Pred();
        p.cars = coaches.length;
        for (double c : coaches) p.trainM += c;
        double sp = spacing(l);
        p.platM = l.size() * sp;
        TileZoneMarker end = stopEnd(l, station);
        if (end == null) return p;
        List<TileZoneMarker> byDist = new ArrayList<>(l);
        byDist.sort(Comparator.comparingDouble(m -> m.getPos().distanceSq(end.getPos())));
        int zones = (int) Math.min(l.size(), Math.ceil(p.trainM / sp - 0.05));
        for (int i = 0; i < zones; i++) p.covered.add(byDist.get(i).getPos().toLong());
        double run = 0;
        for (double c : coaches) { if (run + c / 2 > p.platM) p.offRear++; run += c; }
        return p;
    }

    /** words: "zones A to F" */
    static String zoneWords(List<TileZoneMarker> l, Set<Long> covered) {
        List<Character> c = new ArrayList<>();
        for (TileZoneMarker m : l) if (covered.contains(m.getPos().toLong())) c.add(m.letter());
        if (c.isEmpty()) return "";
        Collections.sort(c);
        return c.size() == 1 ? "zone " + c.get(0) : "zones " + c.get(0) + " to " + c.get(c.size() - 1);
    }

    static double[] coaches(Locomotive l) {
        List<Double> o = new ArrayList<>();
        try { for (EntityCoupleableRollingStock c : l.getTrain()) o.add(c.getDefinition().getLength(c.gauge)); } catch (RuntimeException ignored) { }
        double[] a = new double[o.size()];
        for (int i = 0; i < a.length; i++) a[i] = o.get(i);
        return a;
    }

    /** predict + announce the next train due here (force = say it even if said before) */
    static Pred predictNext(World w, long station, String name, boolean force) {
        if (station == Long.MIN_VALUE) return null;
        List<TileZoneMarker> l = plan(w, station);
        if (l.isEmpty()) return null;
        List<Announcer.Due> due;
        try { due = Announcer.due(w, station, name); } catch (RuntimeException | LinkageError e) { return null; }
        for (Announcer.Due d : due) {
            if (d.dwelling || d.etaMin > 2) continue;
            Locomotive loco = Autopilot.find(w, d.loco);
            if (loco == null) continue;
            Pred p = cover(l, station, coaches(loco));
            p.at = w.getTotalWorldTime();
            p.train = d.train;
            StringBuilder t = new StringBuilder("The train for ").append(d.dest).append(" is formed of ").append(p.cars).append(p.cars == 1 ? " coach" : " coaches")
                    .append(" and will stop at ").append(zoneWords(l, p.covered)).append('.');
            List<Character> acc = new ArrayList<>();
            for (TileZoneMarker m : l) if (m.access() && p.covered.contains(m.getPos().toLong())) acc.add(m.letter());
            if (!acc.isEmpty()) t.append(" Accessible boarding is at zone ").append(acc.get(0)).append('.');
            if (p.offRear > 0) t.append(" This is a short platform. The rear ").append(p.offRear == 1 ? "coach" : p.offRear + " coaches")
                    .append(" will not be at the platform - please move to the front ").append(p.cars - p.offRear).append(" coaches.");
            p.text = t.toString();
            PRED.put(station, p);
            String key = station + "|" + d.loco;
            Long last = SAID.get(key);
            if (force || last == null || w.getTotalWorldTime() - last > 6000) {
                SAID.put(key, w.getTotalWorldTime());
                if (SAID.size() > 500) SAID.clear();
                Announcer.say(w, station, name, "ZONES", p.text);
                QuestEvents.fire(w, null, BlockPos.fromLong(station), "zones_announced", "station", name, "cars", p.cars, "short", p.offRear);
            }
            return p;
        }
        return null;
    }

    @Override
    protected void work() {
        resolve();
        if (age % 20 != 7) return;
        List<TileZoneMarker> l = plan(world, station);
        boolean leader = !l.isEmpty() && l.get(0) == this;
        if (leader && live()) predictNext(world, station, stationName, false);
        Pred p = PRED.get(station);
        if (p != null && world.getTotalWorldTime() - p.at > 3600) { PRED.remove(station); p = null; }
        // a coach standing beside this marker
        coachHere = 0;
        int cars = 0;
        for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
            Vec3d v = s.getPosition();
            if (s.isDead() || Math.abs(v.y - pos.getY()) > 5) continue;
            double half = 8;
            try { half = s.getDefinition().getLength(s.gauge) / 2; } catch (RuntimeException ignored) { }
            if (pos.distanceSqToCenter(v.x, pos.getY() + 0.5, v.z) > (half + 2) * (half + 2)) continue;
            if (s instanceof EntityCoupleableRollingStock c) try { List<EntityCoupleableRollingStock> tr = c.getTrain(); coachHere = tr.indexOf(c) + 1; cars = tr.size(); } catch (RuntimeException e) { coachHere = 1; }
            else coachHere = 1;
            break;
        }
        boolean predicted = p != null && p.covered.contains(pos.toLong());
        put("pwr", live()); put("here", coachHere > 0 || predicted); put("accl", access()); put("stopl", stop());
        put("short", p != null && p.offRear > 0 && predicted);
        text("zone", String.valueOf(letter()));
        text("info", !live() ? "" : coachHere > 0 ? "COACH " + coachHere + (cars > 0 ? "/" + cars : "") : predicted ? p.cars + " CARS DUE" : p != null ? "NO COACH" : access() ? "ACCESS POINT" : "ZONE " + letter());
        if (leader && p != null && !p.text.equals(lastSaid)) { lastSaid = p.text; world.playSound(null, pos, ModSounds.PA_CHIME, SoundCategory.BLOCKS, 0.3f, 1f); }
    }

    @Override protected void saveExtra(NBTTagCompound t) {}
    @Override protected void loadExtra(NBTTagCompound t) {}

    /** console row */
    String[] row() { return new String[]{where(), String.valueOf(letter()), access() ? "+ACCESS" : "", stop() ? "STOP END" : "", coachHere > 0 ? "coach " + coachHere : "-"}; }

    static Pred pred(long station) { return PRED.get(station); }

    static List<String> selftest() {
        List<String> f = new ArrayList<>();
        // a 3-zone plan, 20 m zones (no world: test the arithmetic on coach runs)
        double plat = 3 * 20.0, run = 0;
        int off = 0;
        for (double c : new double[]{20, 20, 20, 20, 20}) { if (run + c / 2 > plat) off++; run += c; }
        if (off != 2) f.add("short-platform count wrong (" + off + ")");
        return f;
    }
}
