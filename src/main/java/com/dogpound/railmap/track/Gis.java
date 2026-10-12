package com.dogpound.railmap.track;

import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * The GIS layer over the surveys (her "next 48" list #26): land boundaries (property to acquire, protected land that may
 * not be crossed, corridors reserved for the railway, flood plains) kept in the world save, and a validator that checks a
 * proposed alignment (a surveyed route, exported to the TrackData register) against the survey and the ground:
 * <ul>
 *   <li>chainage + elevations: formation against the ground every 20 m (the long section);</li>
 *   <li>gradients against the ruling gradient, curve radii against the design speed (full cant + deficiency);</li>
 *   <li>earthworks: cuttings past 12 m want a tunnel, banks past 10 m a viaduct;</li>
 *   <li>drainage: watercourses crossed (culvert / bridge), sags in cuttings (a sump / pumped drain), long flat cuttings
 *       (ponding), flood plains;</li>
 *   <li>utilities in the corridor: grid cables and plant (with their voltage), instrument wiring, existing railway
 *       crossed (grade separation or a level crossing);</li>
 *   <li>boundaries: PROTECTED land is a FAIL, PROPERTY gives the land-take to acquire, RESERVED corridor is fine.</li>
 * </ul>
 * Commands: /irextras ctl gis ...; console: Ctrl tab, GIS page; the surveyor's blueprint book gets a GIS CHECK page.
 */
public final class Gis extends WorldSavedData {
    private static final String NAME = "irextras_gis";
    public enum Kind { PROPERTY, PROTECTED, RESERVED, FLOOD }

    public static final class Boundary {
        public String name = "";
        public Kind kind = Kind.PROPERTY;
        public int x1, z1, x2, z2;
        public boolean inside(double x, double z) { return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) + 1 && z >= Math.min(z1, z2) && z <= Math.max(z1, z2) + 1; }
    }

    public final List<Boundary> boundaries = new ArrayList<>();
    /** last validation per route: summary line for the console */
    public final Map<String, String> verdicts = new LinkedHashMap<>();

    public Gis() { super(NAME); }
    public Gis(String n) { super(n); }

    public static Gis get(World w) {
        MapStorage st = w.getPerWorldStorage();
        Gis g = (Gis) st.getOrLoadData(Gis.class, NAME);
        if (g == null) { g = new Gis(); st.setData(NAME, g); }
        return g;
    }

    public static final class Report {
        public final List<String> lines = new ArrayList<>(), longSection = new ArrayList<>();
        public int pass, warn, fail;
        public double landTakeM2;
        void p(String s) { pass++; lines.add("PASS " + s); }
        void w(String s) { warn++; lines.add("WARN " + s); }
        void f(String s) { fail++; lines.add("FAIL " + s); }
        public String verdict() { return (fail > 0 ? "REJECTED" : warn > 0 ? "ACCEPTABLE WITH WORKS" : "APPROVED") + " - " + pass + " pass, " + warn + " warn, " + fail + " fail"; }
    }

    /** validate a surveyed alignment; designKmh 0 = 100, rulingPermille 0 = 25 (1 in 40) */
    public static Report validate(World w, NBTTagCompound s, double designKmh, double rulingPermille) {
        Report r = new Report();
        if (designKmh <= 0) designKmh = 100;
        if (rulingPermille <= 0) rulingPermille = 25;
        List<int[]> pts = Survey.points(s);
        if (pts.size() < 2) { r.f("fewer than two waypoints - nothing to check"); return r; }
        double len = Survey.length(pts);
        double[] ch = Survey.chainages(pts), rad = Survey.radii(pts);
        int[] g = s.getIntArray("gnd");
        if (g.length == 0) { Survey.profile(w, s); g = s.getIntArray("gnd"); }
        // gradients
        int badG = 0;
        for (int i = 1; i < pts.size(); i++) {
            double pm = SurveyMath.gradePermille(pts.get(i)[1] - pts.get(i - 1)[1], ch[i] - ch[i - 1]);
            if (Math.abs(pm) > rulingPermille) { badG++; r.f(String.format(Locale.ROOT, "gradient %s %s at %s-%s is steeper than the ruling 1:%.0f", SurveyMath.oneIn(pm), pm > 0 ? "UP" : "DN", Survey.chain(ch[i - 1]), Survey.chain(ch[i]), 1000 / rulingPermille)); }
        }
        if (badG == 0) r.p("every gradient within the ruling 1:" + Math.round(1000 / rulingPermille));
        // curves
        int badC = 0;
        double needR = designKmh * designKmh * 11.82 / (SurveyMath.CANT_MM + SurveyMath.DEFICIENCY_MM);
        for (int i = 1; i < pts.size() - 1; i++)
            if (rad[i] > 0 && rad[i] < needR) { badC++; r.f(String.format(Locale.ROOT, "curve R%.0f at %s limits trains to %.0f km/h (design %.0f needs R%.0f)", rad[i], Survey.chain(ch[i]), SurveyMath.curveKmh(rad[i]), designKmh, needR)); }
        if (badC == 0) r.p(String.format(Locale.ROOT, "every curve good for %.0f km/h (R >= %.0f m)", designKmh, needR));
        // earthworks + long section + drainage
        double deepest = 0, highest = 0, flatCut = 0;
        int water = 0, sags = 0;
        double prevF = Double.NaN, prevPrevF = Double.NaN;
        for (int i = 0; i < g.length; i++) {
            double c = i * Survey.STEP;
            double[] at = Survey.at(pts, c);
            double d = g[i] - at[2];
            deepest = Math.max(deepest, d);
            highest = Math.max(highest, -d);
            if (i % 10 == 0) r.longSection.add(String.format(Locale.ROOT, "%s  F%.1f  G%d  %s", Survey.chain(c), at[2], g[i], Math.abs(d) < 0.5 ? "ON GRADE" : (d > 0 ? "CUT " : "FILL ") + String.format(Locale.ROOT, "%.1f", Math.abs(d))));
            BlockPos gb = new BlockPos(Math.floor(at[0]), g[i] - 1, Math.floor(at[1]));
            if (w.isBlockLoaded(gb) && w.getBlockState(gb).getMaterial() == Material.WATER) {
                if (water++ == 0 || i % 10 == 0) r.w(String.format(Locale.ROOT, "watercourse crossed at %s (%d,%d) - culvert or bridge, keep the flow", Survey.chain(c), gb.getX(), gb.getZ()));
            }
            if (!Double.isNaN(prevPrevF) && prevF < prevPrevF && prevF < at[2] && d > 0.5 && i % 1 == 0) { sags++; r.w(String.format(Locale.ROOT, "sag in a cutting at %s - water collects: sump + pumped drain", Survey.chain(c - Survey.STEP))); }
            if (d > 0.5) { double pm = i == 0 ? 0 : Math.abs(at[2] - prevF) / Survey.STEP * 1000; flatCut = pm < 2.5 ? flatCut + Survey.STEP : 0; if (flatCut == 50) r.w(String.format(Locale.ROOT, "50 m of near-level cutting ending %s - ponding risk: cess drains at 1:200", Survey.chain(c))); }
            else flatCut = 0;
            prevPrevF = prevF; prevF = at[2];
        }
        if (deepest > 12) r.w(String.format(Locale.ROOT, "deepest cutting %.1f m - a tunnel is cheaper past 12 m", deepest)); else r.p(String.format(Locale.ROOT, "cuttings at most %.1f m", deepest));
        if (highest > 10) r.w(String.format(Locale.ROOT, "highest bank %.1f m - a viaduct past 10 m", highest)); else r.p(String.format(Locale.ROOT, "banks at most %.1f m", highest));
        if (water == 0 && sags == 0) r.p("no watercourses crossed, no sags in cuttings");
        // utilities in the corridor
        double half = Survey.WIDTH / 2 + 2;
        int util = 0;
        GridData gd = GridData.get(w);
        Set<String> seen = new HashSet<>();
        for (Map.Entry<Long, GridData.Node> e : gd.nodes().entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            double[] cd = nearest(pts, p.getX() + 0.5, p.getZ() + 0.5);
            if (cd[1] > half) continue;
            String k = e.getValue().kind.label + "@" + Survey.chain(Math.round(cd[0] / 20) * 20);
            if (!seen.add(k)) continue;
            GridData.Net n = gd.netOf(p);
            util++;
            if (util <= 12) r.w(String.format(Locale.ROOT, "utility conflict at %s: %s (%d,%d,%d)%s - divert or protect", Survey.chain(cd[0]), e.getValue().kind == GridKind.CABLE ? "power cable" : e.getValue().kind.label, p.getX(), p.getY(), p.getZ(), n != null && n.level != null ? " " + n.level.label : ""));
        }
        int wires = 0, rails = 0;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            BlockPos p = t.getPos();
            double[] cd = nearest(pts, p.getX() + 0.5, p.getZ() + 0.5);
            if (cd[1] > half) continue;
            if (t instanceof com.dogpound.railmap.wire.TileWire && wires++ < 3) r.w(String.format(Locale.ROOT, "instrument wiring crossed at %s (%d,%d) - reroute the cable route", Survey.chain(cd[0]), p.getX(), p.getZ()));
        }
        for (int i = 0; i < g.length; i += 4) {
            double[] at = Survey.at(pts, i * Survey.STEP);
            BlockPos q = new BlockPos(Math.floor(at[0]), g[i], Math.floor(at[1]));
            if (Rails.pieceAt(w, q) != null || Rails.pieceAt(w, q.down()) != null) { if (rails++ < 4) r.w(String.format(Locale.ROOT, "crosses existing railway at %s (%d,%d) - grade separation or a level crossing", Survey.chain(i * Survey.STEP), q.getX(), q.getZ())); }
        }
        if (util + wires + rails == 0) r.p("no utilities or railway in the corridor");
        // boundaries
        Gis gis = get(w);
        Map<String, Double> take = new LinkedHashMap<>();
        boolean prot = false;
        for (int i = 0; i * Survey.STEP <= len; i++) {
            double[] at = Survey.at(pts, i * Survey.STEP);
            for (Boundary b : gis.boundaries) {
                if (!b.inside(at[0], at[1])) continue;
                if (b.kind == Kind.PROTECTED) { if (!prot) r.f("crosses PROTECTED land '" + b.name + "' at " + Survey.chain(i * Survey.STEP)); prot = true; }
                else if (b.kind == Kind.PROPERTY) take.merge(b.name, Survey.STEP * (Survey.WIDTH + 6), Double::sum);
                else if (b.kind == Kind.FLOOD) take.merge("~FLOOD " + b.name, Survey.STEP, Double::sum);
            }
        }
        for (Map.Entry<String, Double> e : take.entrySet()) {
            if (e.getKey().startsWith("~FLOOD ")) r.w(String.format(Locale.ROOT, "%.0f m across flood plain '%s' - formation 1 m above flood level, relief culverts", e.getValue(), e.getKey().substring(7)));
            else { r.landTakeM2 += e.getValue(); r.w(String.format(Locale.ROOT, "land take from '%s': %.0f m2 to acquire", e.getKey(), e.getValue())); }
        }
        if (!prot && take.isEmpty()) r.p("inside the railway's own land / reserved corridor");
        return r;
    }

    /** {chainage, offset} of plan point (x, z) against the alignment */
    public static double[] nearest(List<int[]> pts, double x, double z) {
        double best = Double.MAX_VALUE, bestCh = 0, c = 0;
        for (int i = 1; i < pts.size(); i++) {
            double ax = pts.get(i - 1)[0] + 0.5, az = pts.get(i - 1)[2] + 0.5, bx = pts.get(i)[0] + 0.5, bz = pts.get(i)[2] + 0.5;
            double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            double px = ax + dx * t, pz = az + dz * t, d = Math.hypot(x - px, z - pz);
            if (d < best) { best = d; bestCh = c + Math.sqrt(l2) * t; }
            c += Math.sqrt(l2);
        }
        return new double[]{bestCh, best};
    }

    public static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        List<int[]> pts = Arrays.asList(new int[]{0, 64, 0}, new int[]{100, 64, 0}, new int[]{100, 64, 100});
        double[] cd = nearest(pts, 50, 5);
        out.add((Math.abs(cd[0] - 50) < 1 && Math.abs(cd[1] - 5) < 1 ? "PASS" : "FAIL") + String.format(Locale.ROOT, " chainage/offset of (50,5): %.1f / %.1f m", cd[0], cd[1]));
        Boundary b = new Boundary(); b.x1 = 10; b.z1 = -5; b.x2 = 20; b.z2 = 5;
        out.add((b.inside(15, 0) && !b.inside(25, 0) ? "PASS" : "FAIL") + " boundary rectangle test");
        return out;
    }

    // ---- save ---------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        boundaries.clear();
        NBTTagList l = t.getTagList("b", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Boundary b = new Boundary();
            b.name = c.getString("n");
            try { b.kind = Kind.valueOf(c.getString("k")); } catch (RuntimeException e) { b.kind = Kind.PROPERTY; }
            b.x1 = c.getInteger("x1"); b.z1 = c.getInteger("z1"); b.x2 = c.getInteger("x2"); b.z2 = c.getInteger("z2");
            boundaries.add(b);
        }
        verdicts.clear();
        NBTTagCompound v = t.getCompoundTag("v");
        for (String k : v.getKeySet()) verdicts.put(k, v.getString(k));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Boundary b : boundaries) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", b.name); c.setString("k", b.kind.name());
            c.setInteger("x1", b.x1); c.setInteger("z1", b.z1); c.setInteger("x2", b.x2); c.setInteger("z2", b.z2);
            l.appendTag(c);
        }
        t.setTag("b", l);
        NBTTagCompound v = new NBTTagCompound();
        for (Map.Entry<String, String> e : verdicts.entrySet()) v.setString(e.getKey(), e.getValue());
        t.setTag("v", v);
        return t;
    }
}
