package com.dogpound.railmap.drone;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.asset.AssetKind;
import com.dogpound.railmap.cctv.CctvData;
import com.dogpound.railmap.ohle.OhleData;
import com.dogpound.railmap.signal.BlockCatenary;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TrackData;
import com.dogpound.railmap.weather.Weather;
import com.dogpound.railmap.weather.WeatherData;
import com.dogpound.railmap.weather.WxTrains;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.Random;

/**
 * What a drone's camera finds (her IR Extras list #2), once a second along its flight. Its payload is a 30x zoom camera
 * plus a thermal core, so it sees what shows from the air - not what only an ultrasonic probe finds:
 * <ul>
 *   <li><b>track</b>: geometry, sleepers, ballast and rail-surface damage past the intervention limit (TrackCondition);</li>
 *   <li><b>assets</b> (asset register): surface defects of rails, points, bridges, tunnels, walls, platforms and
 *   insulators by eye, hot spots of signals, transformers, breakers and lamps by thermal - each with a probability of
 *   detection that grows with the defect's size; found ones move along the asset defect track (DISCOVERED, then
 *   CONFIRMED on a second look); structures it overflies count as examined;</li>
 *   <li><b>OHLE</b>: worn or damaged contact wire, ice on the wire (which calls a de-icing train);</li>
 *   <li><b>the line itself</b>: vegetation in the kinematic envelope (it tells the weather-train planner where leaf fall
 *   is), obstructions on the track, deep snow (calls a plough).</li>
 * </ul>
 * Every finding is a work order (a standing alarm until it is closed on the console) and a bookmark on the drone's CCTV
 * recording.
 */
public final class DroneInspect {
    private DroneInspect() {}

    static void scan(EntityDrone d) {
        World w = d.world;
        // the rail under it (up to 16 blocks down)
        TileRail piece = null;
        int railY = 0;
        for (int dy = 0; dy <= 16 && piece == null; dy++) {
            piece = Rails.pieceAt(w, d.posX, d.posY - dy, d.posZ);
            railY = (int) Math.floor(d.posY - dy);
        }
        if (piece != null) {
            BlockPos key = Rails.pos(piece);
            if (d.seen.add(key.toLong())) track(d, key);
            BlockPos col = new BlockPos(Math.floor(d.posX), railY, Math.floor(d.posZ));
            if (d.seen.add(col.toLong() * 31 + 7)) column(d, col);
            WeatherData.Cell c = Weather.on() ? Weather.cell(w, col) : null;
            if (c != null && d.seen.add(c.key * 17 + 3)) weather(d, c, col);
        }
        assets(d);
    }

    // ---- track condition (visible metrics only: no ultrasonic, no gauge from the air) --------------------------------------
    private static void track(EntityDrone d, BlockPos key) {
        TrackData.Seg s = TrackData.get(d.world).peek(key);
        if (s == null) return;
        TrackData.Metric worst = null;
        int band = 1;
        for (TrackData.Metric m : new TrackData.Metric[]{TrackData.Metric.TOP, TrackData.Metric.LINE, TrackData.Metric.TWIST, TrackData.Metric.SURFACE, TrackData.Metric.SLEEPERS, TrackData.Metric.FOULING}) {
            int b = m.band(s.get(m));
            if (b > band) { band = b; worst = m; }
        }
        if (worst == null) return;
        report(d, "TRACK", "trk:" + key.toLong(), String.format(Locale.ROOT, "%s %s %s at %s (%s)", worst.label, com.dogpound.railmap.track.TrackCondition.fmt(s.get(worst)), worst.unit, Rails.xz(key),
                band >= 3 ? "IMMEDIATE ACTION" : "intervention"), key, band >= 3);
    }

    // ---- the column of air over the rail: vegetation, obstructions, the contact wire ---------------------------------------
    private static void column(EntityDrone d, BlockPos rail) {
        World w = d.world;
        int veg = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 1; dy <= 5; dy++) {
                    BlockPos q = rail.add(dx, dy, dz);
                    if (!w.isBlockLoaded(q)) continue;
                    Material m = w.getBlockState(q).getMaterial();
                    if (m == Material.LEAVES || m == Material.VINE || m == Material.PLANTS || m == Material.CACTUS) veg++;
                }
        if (veg >= 3) {
            WxTrains.leafSeen(w, rail);
            report(d, "VEGETATION", "veg:" + (rail.toLong() >> 3), veg + " blocks of vegetation in the train's envelope at " + Rails.xz(rail) + " - cut back; leaf fall on the rail head", rail, veg >= 9);
        }
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos q = rail.up(dy);
            if (!w.isBlockLoaded(q)) continue;
            IBlockState st = w.getBlockState(q);
            Block b = st.getBlock();
            Material m = st.getMaterial();
            if (!m.blocksMovement() || b instanceof BlockCatenary || m == Material.LEAVES || com.dogpound.railmap.scan.SwitchControl.isRail(w, q)) continue;
            if (b.getRegistryName() != null && b.getRegistryName().getNamespace().equals("immersiverailroading")) continue;
            report(d, "OBSTRUCTION", "obs:" + q.toLong(), "OBSTRUCTION ON THE LINE: " + b.getLocalizedName() + " at " + q.getX() + "," + q.getY() + "," + q.getZ() + " - protect the line", q, true);
            break;
        }
        OhleData od = OhleData.get(w);
        for (int dy = 2; dy <= 8; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos q = rail.add(dx, dy, dz);
                    if (!w.isBlockLoaded(q) || !(w.getBlockState(q).getBlock() instanceof BlockCatenary c) || c.kind() != BlockCatenary.Kind.WIRE) continue;
                    float wear = od.wear.getOrDefault(q.toLong(), 0f);
                    if (od.damaged.contains(q.toLong())) report(d, "OHLE", "ohd:" + q.toLong(), "DAMAGED CONTACT WIRE at " + q.getX() + "," + q.getY() + "," + q.getZ() + " - isolate and repair", q, true);
                    else if (wear > 0.3f) report(d, "OHLE", "ohw:" + q.toLong(), String.format(Locale.ROOT, "contact wire worn %.0f%% at %d,%d - renew", wear * 100, q.getX(), q.getZ()), q, wear > 0.6f);
                    double ice = Weather.iceMm(w, q);
                    if (ice > 1) {
                        report(d, "OHLE ICE", "ice:" + (q.toLong() >> 6), String.format(Locale.ROOT, "%.1f mm of ice on the contact wire near %d,%d - pantographs will arc: de-icing train called", ice, q.getX(), q.getZ()), q, ice > 4);
                        WxTrains.request(w, WxTrains.DEICE, q, "drone " + d.droneName() + " saw " + Math.round(ice) + " mm ice on the wire");
                    }
                    return;
                }
    }

    private static void weather(EntityDrone d, WeatherData.Cell c, BlockPos at) {
        if (c.snow >= 100) {
            report(d, "SNOW", "snow:" + c.key, Math.round(c.snow) + " mm of snow on the track in " + Weather.name(c) + " - plough called", at, c.snow >= 300);
            WxTrains.request(d.world, WxTrains.PLOUGH, at, "drone " + d.droneName() + " saw " + Math.round(c.snow) + " mm of snow");
        }
    }

    // ---- assets in view ------------------------------------------------------------------------------------------------------
    private static void assets(EntityDrone d) {
        World w = d.world;
        AssetData ad = AssetData.get(w);
        double r = d.mission == 2 ? 16 : 9;
        long now = w.getTotalWorldTime();
        Random rnd = new Random(now * 13 + d.getEntityId());
        for (AssetData.Asset a : ad.all()) {
            BlockPos p = a.at();
            double dx = p.getX() + 0.5 - d.posX, dz = p.getZ() + 0.5 - d.posZ;
            if (dx * dx + dz * dz > r * r || Math.abs(p.getY() - d.posY) > 40) continue;
            if (!d.seen.add(((long) a.id.hashCode() << 20) ^ 0x5EE5L)) continue;
            double[] pod = pod(a.kind);
            if (pod[0] <= 0) continue;
            String how = pod[2] > 0 ? "drone thermal" : "drone visual";
            for (AssetData.Defect df : ad.defectsOf(a.id)) {
                if (df.stage >= AssetData.REPAIRED) continue;
                double pd = pod[0] * (1 - Math.exp(-Math.pow(Math.max(0, df.size) / (pod[1] * df.crit), 2)));
                if (rnd.nextDouble() > pd) continue;
                double seen = Math.max(0, df.size * (1 + rnd.nextGaussian() * 0.25));
                if (df.stage == AssetData.LATENT) {
                    df.seen = seen; df.unc = df.size * 0.25; df.by = "drone " + d.droneName(); df.method = how;
                    df.step(w, AssetData.DISCOVERED, String.format(Locale.ROOT, "%s from the air: %.1f %s", how, seen, df.unit));
                    ad.note(w, "defect #" + df.id + " DISCOVERED on " + a.id + " by drone " + d.droneName());
                } else if (df.stage == AssetData.DISCOVERED) {
                    df.seen = (df.seen + seen) / 2;
                    df.step(w, AssetData.CONFIRMED, String.format(Locale.ROOT, "second look by drone %s: %.1f %s", d.droneName(), df.seen, df.unit));
                }
                report(d, "ASSET", "def:" + df.id, String.format(Locale.ROOT, "%s %s %s: %s %.1f %s (critical %.0f) - %s", a.id, a.kind.label, a.name, df.type, df.seen, df.unit, df.crit, AssetData.STAGES[df.stage]),
                        p, df.seen >= df.crit * 0.7);
            }
            if (a.cond < 40) report(d, "ASSET", "cond:" + a.id, a.id + " " + a.kind.label + " " + a.name + ": condition " + Math.round(a.cond) + "% (" + com.dogpound.railmap.asset.AssetTicker.BANDS[com.dogpound.railmap.asset.AssetTicker.band(a.cond)] + ")", p, a.cond < 20);
            if (a.kind == AssetKind.BRIDGE || a.kind == AssetKind.TUNNEL || a.kind == AssetKind.STRUCTURE || a.kind == AssetKind.LINING) {
                a.inspected = now;                                                   // a drone exam of a structure counts
                a.inspectedBy = "drone " + d.droneName();
                a.note(w, "examined from the air by drone " + d.droneName());
            }
            ad.markDirty();
        }
    }

    /** {coverage, a50 as a fraction of the critical size, thermal 1/0} of the drone's payload for a kind of asset */
    static double[] pod(AssetKind k) {
        return switch (k) {
            case TRACK, POINTS -> new double[]{0.45, 0.35, 0};
            case BRIDGE, TUNNEL, STRUCTURE, LINING, PLATFORM -> new double[]{0.70, 0.30, 0};
            case INSULATOR -> new double[]{0.60, 0.35, 0};
            case SIGNAL, TRANSFORMER, BREAKER, LUMINAIRE -> new double[]{0.50, 0.30, 1};
            default -> new double[]{0, 1, 0};
        };
    }

    /** a finding: the work order (standing alarm), the CCTV bookmark, the log */
    static void report(EntityDrone d, String kind, String key, String text, BlockPos at, boolean major) {
        World w = d.world;
        DroneData dd = DroneData.get(w);
        DroneData.Finding f = dd.find(w, d.flight, kind, key, text, at, major);
        if (f == null) return;
        AlarmManager.get(w).raise(w, "dronewo:" + f.id, AlarmManager.Cat.MACHINE, major ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                "DRONE work order #" + f.id + " (" + d.droneName() + "): " + text, at, true);
        CctvData.get(w).mark(d.camNo, w.getTotalWorldTime(), "DRONE FINDING #" + f.id + " " + kind);
        dd.log(w, d.droneName() + " found #" + f.id + " " + kind + ": " + text);
        if (major) Recorder.event(w, "DRONE " + d.droneName() + " finding #" + f.id + ": " + text);
        d.note = "FOUND #" + f.id + " " + kind;
    }
}
