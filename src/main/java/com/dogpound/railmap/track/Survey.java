package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A surveyed alignment (her list #18): waypoints at formation level, the ground profile under it sampled every 2 m from
 * the real terrain, and everything worked out from them - chainage, gradients, curve radii, proposed speed limits,
 * cut and fill volumes, track materials - plus the exports: the TrackData register (console), a JSON + GeoJSON file per
 * route for the web map / a planning dashboard, survey pegs set out on the ground, and a construction blueprint book.
 */
public final class Survey {
    private Survey() {}

    public static final double STEP = 2.0, WIDTH = 6.5;

    public static List<int[]> points(NBTTagCompound s) {
        List<int[]> out = new ArrayList<>();
        NBTTagList l = s.getTagList("pts", Constants.NBT.TAG_INT_ARRAY);
        for (int i = 0; i < l.tagCount(); i++) { int[] p = l.getIntArrayAt(i); if (p.length == 3) out.add(p); }
        return out;
    }

    public static void setPoints(NBTTagCompound s, List<int[]> pts) {
        NBTTagList l = new NBTTagList();
        for (int[] p : pts) l.appendTag(new NBTTagIntArray(p));
        s.setTag("pts", l);
    }

    /** formation level along the alignment at chainage ch (linear between waypoints) and plan position: {x, z, y} */
    public static double[] at(List<int[]> pts, double ch) {
        double c = 0;
        for (int i = 1; i < pts.size(); i++) {
            int[] a = pts.get(i - 1), b = pts.get(i);
            double l = Math.hypot(b[0] - a[0], b[2] - a[2]);
            if (ch <= c + l || i == pts.size() - 1) {
                double t = l == 0 ? 0 : Math.max(0, Math.min(1, (ch - c) / l));
                return new double[]{a[0] + 0.5 + (b[0] - a[0]) * t, a[2] + 0.5 + (b[2] - a[2]) * t, a[1] + (b[1] - a[1]) * t};
            }
            c += l;
        }
        int[] p = pts.get(0);
        return new double[]{p[0] + 0.5, p[2] + 0.5, p[1]};
    }

    public static double length(List<int[]> pts) {
        double c = 0;
        for (int i = 1; i < pts.size(); i++) c += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[2] - pts.get(i - 1)[2]);
        return c;
    }

    /** chainage of each waypoint */
    public static double[] chainages(List<int[]> pts) {
        double[] ch = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) ch[i] = ch[i - 1] + Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[2] - pts.get(i - 1)[2]);
        return ch;
    }

    /** radius at each interior waypoint, 0 = straight / an end */
    public static double[] radii(List<int[]> pts) {
        double[] r = new double[pts.size()];
        for (int i = 1; i < pts.size() - 1; i++) { int[] a = pts.get(i - 1), b = pts.get(i), c = pts.get(i + 1); r[i] = SurveyMath.radius(a[0], a[2], b[0], b[2], c[0], c[2]); }
        return r;
    }

    /** ground surface height at a column, scanning around formation level y (water counts as surface) */
    static int ground(World w, int x, int z, int y) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int yy = Math.min(255, y + 40); yy >= Math.max(1, y - 40); yy--) {
            m.setPos(x, yy, z);
            IBlockState s = w.getBlockState(m);
            Material mt = s.getMaterial();
            if (mt == Material.LEAVES || mt == Material.WOOD || mt == Material.PLANTS || mt == Material.VINE || mt == Material.SNOW) continue;
            if (mt.isLiquid() || mt.blocksMovement() && Rails.pieceAt(w, m.toImmutable()) == null) return yy + 1;
        }
        return y - 40;
    }

    /** re-sample the ground profile (every STEP m) and save it beside the points */
    public static void profile(World w, NBTTagCompound s) {
        List<int[]> pts = points(s);
        if (pts.size() < 2) { s.setIntArray("gnd", new int[0]); return; }
        double len = length(pts);
        int n = (int) Math.min(1500, Math.floor(len / STEP) + 1);
        int[] g = new int[n];
        for (int i = 0; i < n; i++) {
            double[] p = at(pts, i * STEP);
            g[i] = ground(w, (int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.round(p[2]));
        }
        s.setIntArray("gnd", g);
    }

    /** earthworks {cut m3, fill m3, deepest cut, highest fill} from the stored profile */
    public static double[] earthworks(NBTTagCompound s) {
        List<int[]> pts = points(s);
        int[] g = s.getIntArray("gnd");
        double cut = 0, fill = 0, dc = 0, df = 0;
        for (int i = 0; i < g.length; i++) {
            double f = at(pts, i * STEP)[2];
            double d = g[i] - f;                                       // + = ground above formation = cut
            double a = SurveyMath.earthArea(d, WIDTH);
            if (d > 0) { cut += a * STEP; dc = Math.max(dc, d); } else { fill += a * STEP; df = Math.max(df, -d); }
        }
        return new double[]{cut, fill, dc, df};
    }

    /** one text line per waypoint section: grade, radius, proposed speed */
    public static List<String> sections(NBTTagCompound s) {
        List<int[]> pts = points(s);
        double[] ch = chainages(pts), r = radii(pts);
        List<String> out = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) {
            double run = ch[i] - ch[i - 1], pm = SurveyMath.gradePermille(pts.get(i)[1] - pts.get(i - 1)[1], run);
            double rr = Math.max(r[i - 1], 0), rr2 = r[i];
            double tight = rr > 0 && rr2 > 0 ? Math.min(rr, rr2) : Math.max(rr, rr2);
            out.add(String.format(Locale.ROOT, "%s-%s  %s  %s  %s", chain(ch[i - 1]), chain(ch[i]), SurveyMath.oneIn(pm) + (pm > 0 ? " UP" : pm < 0 ? " DN" : ""), tight > 0 ? "R" + Math.round(tight) : "STR", speedText(speed(pm, tight))));
        }
        return out;
    }

    /** proposed line speed for a section: the lower of the curve and gradient limits (0 = unrestricted) */
    public static double speed(double permille, double radius) {
        double v = radius > 0 ? SurveyMath.curveKmh(radius) : 0, g = SurveyMath.gradeKmh(permille);
        if (v > 0 && g > 0) return Math.min(v, g);
        return Math.max(v, g);
    }

    public static String speedText(double v) { return v <= 0 ? "LINE" : (int) (Math.floor(v / 5) * 5) + "KMH"; }
    public static String chain(double m) { return String.format(Locale.ROOT, "%d+%03d", (int) (m / 1000), (int) Math.round(m) % 1000); }

    /** the design train (nearest IR consist): {name, longest car m} or null */
    public static Object[] designTrain(World w, BlockPos near) {
        try {
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(w);
            EntityRollingStock best = null;
            double bd = 64 * 64;
            for (EntityRollingStock s : umc.getEntities(EntityRollingStock.class)) {
                double d = near.distanceSq(s.getPosition().x, s.getPosition().y, s.getPosition().z);
                if (d < bd) { bd = d; best = s; }
            }
            if (best == null) return null;
            double longest = 0;
            List<EntityRollingStock> cars = new ArrayList<>();
            if (best instanceof EntityCoupleableRollingStock c) cars.addAll(c.getTrain()); else cars.add(best);
            for (EntityRollingStock s : cars) longest = Math.max(longest, s.getDefinition().getLength(s.gauge));
            return new Object[]{Rails.name(best) + (cars.size() > 1 ? " +" + (cars.size() - 1) : ""), longest};
        } catch (LinkageError | RuntimeException e) { return null; }
    }

    /** curve check lines for the design train */
    public static List<String> curveCheck(NBTTagCompound s, Object[] train) {
        List<String> out = new ArrayList<>();
        if (train == null) { out.add("No train near to check (park one within 64 blocks)"); return out; }
        double len = (double) train[1], bog = len * 0.7;
        List<int[]> pts = points(s);
        double[] r = radii(pts), ch = chainages(pts);
        boolean any = false;
        for (int i = 1; i < pts.size() - 1; i++) {
            if (r[i] <= 0) continue;
            any = true;
            double[] t = SurveyMath.throwsM(len, bog, r[i]);
            boolean ok = t[0] <= 0.30 && t[1] <= 0.45 && r[i] >= len * 2.5;
            out.add(String.format(Locale.ROOT, "%s R%d: centre %.2f end %.2f m %s", chain(ch[i]), Math.round(r[i]), t[0], t[1], ok ? "OK" : "TOO TIGHT"));
        }
        if (!any) out.add("No curves - straight route");
        return out;
    }

    /** register in TrackData (console) and write route JSON + GeoJSON for the web map. Returns the file name or null. */
    public static String export(World w, String name, NBTTagCompound s) {
        TrackData d = TrackData.get(w);
        NBTTagCompound copy = s.copy();
        copy.setLong("at", w.getTotalWorldTime());
        d.surveys.put(name, copy);
        d.version++;
        d.markDirty();
        try {
            File root = DimensionManager.getCurrentSaveRootDirectory();
            if (root == null) return null;
            File dir = new File(root, "irextras/survey");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            List<int[]> pts = points(s);
            double[] ch = chainages(pts), r = radii(pts), ew = earthworks(s);
            StringBuilder j = new StringBuilder();
            j.append("{\"type\":\"FeatureCollection\",\"name\":\"").append(esc(name)).append("\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"name\":\"").append(esc(name))
                    .append("\",\"lengthM\":").append(Math.round(length(pts))).append(",\"cutM3\":").append(Math.round(ew[0])).append(",\"fillM3\":").append(Math.round(ew[1]))
                    .append(",\"dimension\":").append(w.provider.getDimension()).append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
            for (int i = 0; i < pts.size(); i++) j.append(i == 0 ? "" : ",").append('[').append(pts.get(i)[0]).append(',').append(pts.get(i)[2]).append(',').append(pts.get(i)[1]).append(']');
            j.append("]}}");
            for (int i = 0; i < pts.size(); i++)
                j.append(",{\"type\":\"Feature\",\"properties\":{\"waypoint\":").append(i).append(",\"chainage\":").append(Math.round(ch[i])).append(",\"level\":").append(pts.get(i)[1])
                        .append(",\"radius\":").append(Math.round(r[i])).append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[").append(pts.get(i)[0]).append(',').append(pts.get(i)[2]).append("]}}");
            j.append("]}");
            String file = name.replaceAll("[^A-Za-z0-9_-]", "_") + ".geojson";
            Files.write(new File(dir, file).toPath(), j.toString().getBytes(StandardCharsets.UTF_8));
            return "irextras/survey/" + file;
        } catch (Exception e) {
            RailMap.LOG.warn("[IR Extras] survey export failed: {}", e.toString());
            return null;
        }
    }

    /** every piece of track the network knows, with its condition, as GeoJSON points (console / web map) */
    public static String exportTrack(World w) {
        try {
            File root = DimensionManager.getCurrentSaveRootDirectory();
            if (root == null) return null;
            File dir = new File(root, "irextras/survey");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            StringBuilder j = new StringBuilder("{\"type\":\"FeatureCollection\",\"name\":\"track condition\",\"features\":[");
            boolean first = true;
            for (java.util.Map.Entry<Long, TrackData.Seg> e : TrackData.get(w).segs.entrySet()) {
                BlockPos p = BlockPos.fromLong(e.getKey());
                TrackData.Seg s = e.getValue();
                j.append(first ? "" : ",").append("{\"type\":\"Feature\",\"properties\":{\"tqi\":").append(s.tqi()).append(",\"tsr\":").append(s.tsr()).append(",\"band\":").append(s.band())
                        .append(",\"radius\":").append(s.radius).append(",\"tonnes\":").append(Math.round(s.tonnes)).append(",\"worst\":\"").append(s.worst().label)
                        .append("\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[").append(p.getX()).append(',').append(p.getZ()).append(',').append(p.getY()).append("]}}");
                first = false;
            }
            j.append("]}");
            Files.write(new File(dir, "track_condition_dim" + w.provider.getDimension() + ".geojson").toPath(), j.toString().getBytes(StandardCharsets.UTF_8));
            return "irextras/survey/track_condition_dim" + w.provider.getDimension() + ".geojson";
        } catch (Exception e) { return null; }
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
