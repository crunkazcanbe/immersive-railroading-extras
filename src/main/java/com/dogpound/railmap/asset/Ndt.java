package com.dogpound.railmap.asset;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotTicker;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.Insulation;
import com.dogpound.railmap.grid.Thermal;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.track.TrackData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Non-destructive inspection (her 48-next list #27). Five instruments, each with a COVERAGE (how much of the asset the
 * method can see: probe angles, line of sight, sensor positions) and a probability of detection that rises with
 * defect size (POD(a) = coverage × (1 - exp(-(a/a50)²))), and a measurement UNCERTAINTY (what it reads is the true
 * value ± that). What they read is the real state of the game: rail head wear and surface damage from the track model,
 * wheel and bearing wear, transformer hot spots from the thermal model, the insulation's real resistance, a
 * structure's condition. A defect is tracked from the moment it is found: DISCOVERED (first indication), CONFIRMED
 * (found again, sized better), REPAIR ORDERED, REPAIRED (the parts went in), VERIFIED (a re-test finds it gone).
 */
public final class Ndt {
    private Ndt() {}

    public enum Tool {
        ULTRASONIC("ndt_ultrasonic", "Ultrasonic Rail Tester", "0°/37°/70° probes on a walking stick: rail head, web and foot", 0.85, 3.0, 0.15),
        AXLE("ndt_axle", "Ultrasonic Axle Tester", "Bore / end-face probe for axles, plus wheel profile and bearing noise", 0.90, 2.0, 0.10),
        THERMAL("ndt_thermal", "Thermal Imager", "Infra-red camera: hot joints, hot spots, failing drivers, hot bearings", 0.70, 6.0, 0.05),
        INSULATION("ndt_insulation", "Insulation Tester (5 kV)", "Insulation resistance + polarisation index on de-energised plant", 1.00, 8.0, 0.05),
        STRUCTURAL("ndt_structural", "Structural Monitor", "Crack-width gauges, strain and tilt sensors, rebound hammer", 0.60, 0.6, 0.20);
        public final String id, label, tip;
        public final double coverage, a50, unc;
        Tool(String id, String label, String tip, double coverage, double a50, double unc) { this.id = id; this.label = label; this.tip = tip; this.coverage = coverage; this.a50 = a50; this.unc = unc; }
    }

    /** probability of detection of a defect of this size */
    public static double pod(Tool t, double size) { return t.coverage * (1 - Math.exp(-Math.pow(Math.max(0, size) / t.a50, 2))); }

    // ---- block targets ------------------------------------------------------------------------------------------------
    /** an inspection of the asset at (or nearest) pos with this tool: the chat lines */
    public static List<String> inspect(World w, Tool t, BlockPos pos, EntityPlayer who) {
        List<String> out = new ArrayList<>();
        AssetData d = AssetData.get(w);
        AssetData.Asset a = find(w, d, t, pos);
        if (a == null) {
            out.add("§7" + t.label + ": nothing this instrument tests here. It tests " + targets(t));
            return out;
        }
        if (a.kind.tool != t && !(t == Tool.THERMAL && a.kind.electrical()) && !(t == Tool.INSULATION && a.kind.electrical())) {
            out.add("§e" + t.label + " is not the method for a " + a.kind.label.toLowerCase(Locale.ROOT) + " - use the " + a.kind.tool.label);
            return out;
        }
        out.add(String.format(Locale.ROOT, "§b§l%s§r§7 · %s %s %s · coverage %.0f%% · ±%.0f%%", t.label.toUpperCase(Locale.ROOT), a.id, a.kind.label, a.name, t.coverage * 100, t.unc * 100));
        Random r = new Random(w.getTotalWorldTime() * 31 + pos.toLong());
        String refuse = readings(w, t, a, out, r);
        if (refuse != null) { out.add(refuse); return out; }
        findings(w, d, t, a.id, a.pos, a.kind.defect, a.kind.defectUnit, a.kind.crit, out, r, who);
        a.inspected = w.getTotalWorldTime();
        a.inspectedBy = who == null ? "inspector" : who.getName();
        a.note(w, t.label + " test by " + a.inspectedBy + ": " + (out.size() > 2 ? out.get(out.size() - 1).replaceAll("§.", "") : "ok"));
        d.markDirty();
        Recorder.event(w, "NDT: " + a.id + " tested with the " + t.label + " by " + a.inspectedBy);
        com.dogpound.railmap.api.QuestEvents.fire(w, who, pos, "ndt_test", "tool", t.id, "asset", a.id, "kind", a.kind.name());
        return out;
    }

    static String targets(Tool t) {
        return switch (t) {
            case ULTRASONIC -> "rails and point machines (right-click the rail or the machine)";
            case AXLE -> "rolling stock (right-click near a vehicle, within 6 blocks)";
            case THERMAL -> "transformers, breakers, motors, insulators, signals and locomotives";
            case INSULATION -> "transformers, motors, breakers and insulators - de-energised only";
            case STRUCTURAL -> "structure boards (bridges, tunnels, walls), tunnel linings and platform signs";
        };
    }

    /** the asset this tool is aimed at: exact position first, then (rails, linings) the piece or tunnel it belongs to */
    static AssetData.Asset find(World w, AssetData d, Tool t, BlockPos pos) {
        for (AssetKind k : AssetKind.values()) { AssetData.Asset a = d.at(k, pos.toLong()); if (a != null && k != AssetKind.LINING) return a; }
        if (com.dogpound.railmap.scan.SwitchControl.isRail(w, pos)) {
            TileRail piece = Rails.pieceAt(w, pos);
            if (piece != null) {
                BlockPos key = Rails.pos(piece);
                AssetData.Asset a = d.at(AssetKind.TRACK, key.toLong());
                if (a == null && AssetConfig.assetRegister) a = d.register(w, AssetKind.TRACK, key.toLong(), "track " + Rails.xz(key), false);
                return a;
            }
        }
        if (w.getBlockState(pos).getBlock() instanceof com.dogpound.railmap.track.BlockLining) {
            AssetData.Asset best = null;
            double bd = 48 * 48;
            for (AssetData.Asset a : d.all()) if (a.kind == AssetKind.LINING && a.at().distanceSq(pos) < bd) { bd = a.at().distanceSq(pos); best = a; }
            return best;
        }
        return null;
    }

    /** the measured values; a refusal (live plant for the megger) stops the test */
    private static String readings(World w, Tool t, AssetData.Asset a, List<String> out, Random r) {
        GridData g = GridData.get(w);
        GridData.Node n = g.nodes().get(a.pos);
        switch (t) {
            case ULTRASONIC -> {
                BlockPos key = a.kind == AssetKind.POINTS ? switchKey(w, a) : a.at();
                TrackData.Seg s = key == null ? null : TrackData.get(w).peek(key);
                if (s == null) { out.add("§7No running record on this rail yet: probes coupled, clean signal, nothing to compare"); break; }
                out.add(String.format(Locale.ROOT, "§7Head loss %.1f mm (±%.1f) · surface damage (RCF / corrugation) %.0f%% · %.2f MGT carried · %s",
                        jit(s.get(TrackData.Metric.RAIL), t, r), s.get(TrackData.Metric.RAIL) * t.unc + 0.1, jit(s.get(TrackData.Metric.SURFACE), t, r), s.tonnes / 1e6,
                        s.radius > 0 ? "curve R " + s.radius + " m" : "straight"));
            }
            case THERMAL -> {
                if (n != null) {
                    double amb = n.ambient;
                    if (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION)
                        out.add(String.format(Locale.ROOT, "§7Tank top %.0f °C · radiators Δ%.0f K · ambient %.0f °C · est. hot spot %.0f °C · fans %s · radiator fouling %.0f%%",
                                jitC(Thermal.topOilC(n), r), Math.max(0, n.st("toil") / 10.0 * 0.6), amb, jitC(Thermal.hotSpotC(n), r),
                                n.fanFail ? "FAILED (cold)" : n.cool == 0 ? "off" : "running", Thermal.fouling(n) * 100));
                    else if (n.kind == GridKind.MOTOR)
                        out.add(String.format(Locale.ROOT, "§7Frame %.0f °C · bearings %.0f °C · 49 replica %.0f%%", jitC(amb + 60 * Thermal.replica(n), r), jitC(amb + 25 * Thermal.replica(n) + 5, r), Thermal.replica(n) * 100));
                    else if (n.kind == GridKind.BREAKER)
                        out.add(String.format(Locale.ROOT, "§7Terminals %.0f °C · contacts %.0f%% worn · %d operations", jitC(amb + 8 + 30 * n.wear * Math.min(1, n.amps / 400.0), r), n.wear * 100, n.ops));
                    else out.add(String.format(Locale.ROOT, "§7Surface %.0f °C (ambient %.0f °C)", jitC(amb + 3, r), amb));
                } else if (a.kind == AssetKind.SIGNAL || a.kind == AssetKind.LUMINAIRE) {
                    double amb = Thermal.ambient(w, a.at());
                    out.add(String.format(Locale.ROOT, "§7Lamp driver %.0f °C · %.0f lamp hours", jitC(amb + 12 + 20 * Math.min(1, a.lifeUsed()), r), a.m[0]));
                } else out.add("§7No heat signature worth recording");
            }
            case INSULATION -> {
                if (n == null) { out.add("§7No insulation to test on this asset"); break; }
                if (com.dogpound.railmap.grid.Permits.liveNow(w, a.at()))
                    return "§c§lREFUSED§r§c - the plant is LIVE. Isolate it, earth it and prove it dead before an insulation test";
                double mo = Math.pow(10, 4 * n.insul - 1) * (1 + (r.nextDouble() - 0.5) * 2 * t.unc);
                double pi = n.efault != 0 ? 1.0 : 1.0 + 3.0 * n.insul * n.insul;
                out.add(String.format(Locale.ROOT, "§7IR(1 min) %s · PI %.1f %s · %s", n.efault != 0 ? "0.00 MΩ BROKEN DOWN" : mo >= 1000 ? ">1000 MΩ" : String.format(Locale.ROOT, "%.1f MΩ", mo),
                        pi, pi >= 2 ? "(good)" : pi >= 1.5 ? "(questionable)" : "(POOR - wet / contaminated)", Insulation.megohms(n)));
            }
            case STRUCTURAL -> {
                TileEntity te = w.isBlockLoaded(a.at()) ? w.getTileEntity(a.at()) : null;
                Map<String, Object> info = new HashMap<>();
                if (te instanceof com.dogpound.railmap.wire.IWiredGear gear) gear.gearInfo(info);
                if (info.containsKey("condition"))
                    out.add(String.format(Locale.ROOT, "§7Condition index %d%% · %s overloads recorded · axle rating %s t · rebound hammer %.0f MPa", jitI(((Number) info.get("condition")).doubleValue(), t, r),
                            info.getOrDefault("overloads", 0), info.getOrDefault("axleRatingT", "?"), 25 + 30 * a.cond / 100.0));
                else out.add(String.format(Locale.ROOT, "§7Condition %d%% (estimated) · %.0f %s of service", jitI(a.cond, t, r), a.use, a.kind.unit));
            }
            default -> { }
        }
        return null;
    }

    private static BlockPos switchKey(World w, AssetData.Asset a) {
        TileEntity te = w.isBlockLoaded(a.at()) ? w.getTileEntity(a.at()) : null;
        if (te instanceof com.dogpound.railmap.track.TilePointMachine pm && pm.switchPos() != null) {
            TileRail r = Rails.pieceAt(w, pm.switchPos());
            return r == null ? pm.switchPos() : Rails.pos(r);
        }
        return null;
    }

    private static double jit(double v, Tool t, Random r) { return Math.max(0, v * (1 + (r.nextDouble() - 0.5) * 2 * t.unc)); }
    private static double jitC(double v, Random r) { return v + (r.nextDouble() - 0.5) * 4 + v * 0.02 * (r.nextDouble() - 0.5); }
    private static int jitI(double v, Tool t, Random r) { return (int) Math.round(Math.max(0, Math.min(100, v * (1 + (r.nextDouble() - 0.5) * t.unc)))); }

    /** look for the asset's defects: each one may or may not be seen; seen ones move along their track */
    static void findings(World w, AssetData d, Tool t, String assetId, long pos, String type, String unit, double crit, List<String> out, Random r, EntityPlayer who) {
        String by = who == null ? "inspector" : who.getName();
        int found = 0, missed = 0;
        for (AssetData.Defect df : d.defectsOf(assetId)) {
            if (df.stage == AssetData.VERIFIED) continue;
            if (df.stage == AssetData.REPAIRED) {                           // the re-test after a repair
                df.step(w, AssetData.VERIFIED, t.label + " re-test by " + by + ": no indication - repair verified");
                out.add("§a✔ Defect #" + df.id + " (" + df.type + ") - re-test clean: REPAIR VERIFIED, closed");
                com.dogpound.railmap.api.QuestEvents.fire(w, who, null, "defect_verified", "tool", t.id, "found_by", df.method == null ? "" : df.method, "defect", df.type);
                continue;
            }
            double p = pod(t, df.size);
            if (r.nextDouble() > p) { missed++; continue; }
            double seen = Math.max(0, df.size * (1 + r.nextGaussian() * t.unc));
            double unc = Math.max(0.05, df.size * t.unc);
            found++;
            if (df.stage == AssetData.LATENT) {
                df.seen = seen; df.unc = unc; df.by = by; df.method = t.label;
                df.step(w, AssetData.DISCOVERED, String.format(Locale.ROOT, "%s by %s: %.1f ± %.1f %s", t.label, by, seen, unc, unit));
                out.add(String.format(Locale.ROOT, "§6⚠ NEW INDICATION #%d: %s %.1f ± %.1f %s (critical %.0f %s) - re-test to confirm", df.id, df.type, seen, unc, unit, crit, unit));
                d.note(w, "defect #" + df.id + " DISCOVERED on " + assetId + ": " + df.type);
                com.dogpound.railmap.api.QuestEvents.fire(w, who, null, "defect_found", "tool", t.id, "stage", "DISCOVERED", "defect", df.type);
            } else if (df.stage == AssetData.DISCOVERED) {
                df.seen = (df.seen + seen) / 2; df.unc = unc * 0.7;
                df.step(w, AssetData.CONFIRMED, String.format(Locale.ROOT, "re-test by %s: %.1f ± %.1f %s", by, df.seen, df.unc, unit));
                com.dogpound.railmap.api.QuestEvents.fire(w, who, null, "defect_found", "tool", t.id, "stage", "CONFIRMED", "defect", df.type);
                out.add(String.format(Locale.ROOT, "§c✖ DEFECT CONFIRMED #%d: %s %.1f ± %.1f %s - order the repair (console ASSETS > Defects, or /irextras asset repair %d)", df.id, df.type, df.seen, df.unc, unit, df.id));
            } else {
                df.seen = seen; df.unc = unc;
                out.add(String.format(Locale.ROOT, "§e#%d %s now %.1f ± %.1f %s (%s)", df.id, df.type, seen, unc, unit, AssetData.STAGES[df.stage]));
            }
        }
        out.add(found == 0 ? String.format(Locale.ROOT, "§aNo reportable indications §8(POD for a %.1f %s flaw: %.0f%% - a small one may still hide)", crit / 4, unit, pod(t, crit / 4) * 100)
                : "§7" + found + " indication(s) recorded");
        d.markDirty();
    }

    /** infra #41: an ultrasonic TEST TRAIN's probes over one track piece (no player): the lines, null when it isn't a track asset */
    public static List<String> testTrainPass(World w, BlockPos rail, String by) {
        AssetData d = AssetData.get(w);
        AssetData.Asset a = find(w, d, Tool.ULTRASONIC, rail);
        if (a == null || a.kind != AssetKind.TRACK) return null;
        List<String> out = new ArrayList<>();
        Random r = new Random(w.getTotalWorldTime() * 31 + rail.toLong());
        findings(w, d, Tool.ULTRASONIC, a.id, a.pos, a.kind.defect, a.kind.defectUnit, a.kind.crit, out, r, null);
        a.inspected = w.getTotalWorldTime();
        a.inspectedBy = by;
        d.markDirty();
        return out;
    }

    // ---- rolling stock: the axle tester and the thermal imager on a vehicle ---------------------------------------------
    public static List<String> inspectVehicle(World w, Tool t, EntityRollingStock s, EntityPlayer who) {
        List<String> out = new ArrayList<>();
        UUID id = s.getUUID();
        String name = DepotTicker.name(s);
        WearData.Record wr = WearData.get(w).peek(id);
        SimData.Car c = SimData.get(w).peek(id);
        FleetData.Unit u = FleetData.get(w).peek(id);
        Random r = new Random(w.getTotalWorldTime() * 17 + id.getLeastSignificantBits());
        out.add(String.format(Locale.ROOT, "§b§l%s§r§7 · %s · coverage %.0f%% · ±%.0f%%", t.label.toUpperCase(Locale.ROOT), name, t.coverage * 100, t.unc * 100));
        if (t == Tool.AXLE) {
            float wh = wr == null ? 0 : wr.wear[WearData.Part.WHEELS.ordinal()], br = wr == null ? 0 : wr.wear[WearData.Part.BEARINGS.ordinal()];
            FleetData.Comp ws = u == null ? null : u.comps[FleetData.Kind.WHEELS.ordinal()];
            out.add(String.format(Locale.ROOT, "§7Wheel tread wear %.0f%% · flat %d mm · diameter %s · bearing noise %s (%.0f%% worn) · %.1f km",
                    jit(wh, t, r), c == null ? 0 : Math.round(c.flatMm), ws == null || ws.diameter <= 0 ? "-" : Math.round(ws.diameter) + " mm",
                    br > 80 ? "HIGH" : br > 50 ? "elevated" : "normal", jit(br, t, r), wr == null ? 0 : wr.km));
            findings(w, AssetData.get(w), t, "VEH:" + id, Long.MIN_VALUE, "axle fatigue crack", "mm", 6, out, r, who);
        } else {
            double amb = Thermal.ambient(w, new BlockPos(s.getPosition().x, s.getPosition().y, s.getPosition().z));
            float br = wr == null ? 0 : wr.wear[WearData.Part.BEARINGS.ordinal()];
            out.add(String.format(Locale.ROOT, "§7Axle boxes %.0f °C (hottest) · brakes %s%s", jitC(amb + 10 + br * 0.6, r), c != null && c.flatMm > 10 ? "one wheel skidded hot" : "even",
                    s instanceof LocomotiveDiesel d ? String.format(Locale.ROOT, " · engine %.0f °C · traction motors %s", d.getEngineTemperature(), c == null || Double.isNaN(c.tmC) ? "-" : Math.round(c.tmC) + " °C") : ""));
            if (br > 80) out.add("§c✖ HOT BOX: an axle bearing runs far hotter than the rest - stop and examine");
        }
        if (u != null) u.note(w, t.label + " test by " + (who == null ? "inspector" : who.getName()));
        Recorder.event(w, "NDT: " + name + " tested with the " + t.label);
        return out;
    }

    /** the nearest vehicle within reach of a player */
    public static EntityRollingStock nearVehicle(World w, EntityPlayer p, double reach) {
        EntityRollingStock best = null;
        double bd = reach * reach;
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                Vec3d v = s.getPosition();
                double dd = p.getDistanceSq(v.x, v.y, v.z);
                if (!s.isDead() && dd < bd) { bd = dd; best = s; }
            }
        } catch (LinkageError | RuntimeException ignored) { }
        return best;
    }
}
