package com.dogpound.railmap.asset;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.Thermal;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.track.TrackData;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * The asset register's clock (her 48-next list #2). Every 10 s: find the gear (loaded point machines, signals,
 * structure boards and platform signs, every grid transformer / breaker / motor / arrester, the OHLE section
 * insulators, every piece of track with a running record), count how much it was used since last time from the real
 * counters, age it, work out its condition, grow hidden defects, and raise the inspection / commissioning / defect
 * alarms. Once a second: purchase-order deliveries and rebuild acceptance runs. Gear placed by a player after the
 * register first ran starts UNCOMMISSIONED; everything else is accepted as built.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class AssetTicker {
    private AssetTicker() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    /** positions placed by players since the register started, waiting for their first sweep (this session) */
    private static final Set<Long> PLACED = new HashSet<>();

    @SubscribeEvent
    public static void placed(BlockEvent.PlaceEvent e) {
        if (e.getWorld().isRemote || !AssetConfig.commissioning) return;
        PLACED.add(e.getPos().toLong());
        if (PLACED.size() > 4000) PLACED.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !AssetConfig.assetRegister) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        try {
            if (now % 20 == 13) {
                Procurement.tick(w, now);
                Design.tick(w, now);
            }
            if (now % 200 == 113) sweep(w, now);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] asset tick failed: {}", ex.toString());
        }
    }

    // ---- the sweep ------------------------------------------------------------------------------------------------------
    static void sweep(World w, long now) {
        com.dogpound.railmap.safety.FaultLab.syncBench(w);                  // bench faults survive a reload (grid.Bench reads them)
        AssetData d = AssetData.get(w);
        boolean first = !d.seeded;
        Set<String> seen = new HashSet<>();
        // loaded tile gear
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (t.isInvalid()) continue;
            long p = t.getPos().toLong();
            boolean fresh = !first && PLACED.remove(p);
            if (t instanceof com.dogpound.railmap.track.TilePointMachine pm) {
                AssetData.Asset a = d.register(w, AssetKind.POINTS, p, pm.id(), fresh);
                seen.add(a.id);
                Map<String, Object> m = info(pm);
                use(w, a, num(m.get("throws")), 1, now);
                measured(a, pm.wiredLamp() == 3 && pm.powered() ? 40 : -1);
            } else if (t instanceof com.dogpound.railmap.signal.TileSignalMast sm) {
                AssetData.Asset a = d.register(w, AssetKind.SIGNAL, p, "signal " + com.dogpound.railmap.safety.SignalSighting.name(sm), fresh);
                seen.add(a.id);
                if (sm.term().powered()) a.m[0] += 10.0 / 50.0;                    // 10 s = 0.2 game hours lit
                use(w, a, a.m[0], 1, now);
                measured(a, sm.shownAspect() == com.dogpound.railmap.signal.Aspect.DARK && sm.term().powered() ? 10 : -1);
            } else if (t instanceof com.dogpound.railmap.track.TileStructureBoard sb) {
                Map<String, Object> m = info(sb);
                String kind = String.valueOf(m.getOrDefault("kind", "BRIDGE"));
                AssetKind k = kind.equals("BRIDGE") || kind.equals("VIADUCT") ? AssetKind.BRIDGE : kind.equals("TUNNEL") ? AssetKind.TUNNEL : AssetKind.STRUCTURE;
                AssetData.Asset a = d.register(w, k, p, sb.wiredName(), fresh);
                seen.add(a.id);
                use(w, a, num(m.get("crossings")) * 4 / 1000.0, 1 + num(m.get("overloads")) * 0.02, now);   // k axles; overloads fatigue it faster
                measured(a, (float) num(m.getOrDefault("condition", 100)));
                if (k == AssetKind.TUNNEL) lining(w, d, a, p, fresh, seen, now);
                else { AssetData.Asset l = d.at(AssetKind.LINING, p); if (l != null) d.remove(w, l, "no longer a tunnel"); }
            } else if (t instanceof com.dogpound.railmap.light.ILamp lamp) {          // night lighting: luminaires age by their lamp hours
                AssetData.Asset a = d.register(w, AssetKind.LUMINAIRE, p, lamp.lampName(), fresh);
                seen.add(a.id);
                if (lamp.lampOn()) a.m[0] += 10.0 / 50.0;                           // 10 s = 0.2 game hours lit
                use(w, a, a.m[0], 1, now);
                measured(a, -1);
            } else if (t instanceof com.dogpound.railmap.station.TileStationPiece sp && sp.kind().equals("platform_sign")) {
                AssetData.Asset a = d.register(w, AssetKind.PLATFORM, p, "platform " + sp.cfg().num("number") + " " + sp.station(), fresh);
                seen.add(a.id);
                boolean train = !sp.train().isEmpty();
                if (train && a.m[1] == 0) a.m[0]++;                               // a call: the platform loads (footfall, impact)
                a.m[1] = train ? 1 : 0;
                a.m[2] += 10.0 / 1200.0;                                          // days exposed
                use(w, a, a.m[0] + a.m[2] * 40, 1, now);
            }
        }
        // the grid (works with the chunks unloaded)
        GridData g = GridData.get(w);
        for (Map.Entry<Long, GridData.Node> en : g.nodes().entrySet()) {
            GridData.Node n = en.getValue();
            AssetKind k = n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION ? AssetKind.TRANSFORMER : n.kind == GridKind.BREAKER ? AssetKind.BREAKER
                    : n.kind == GridKind.MOTOR ? AssetKind.MOTOR : n.kind == GridKind.ARRESTER ? AssetKind.INSULATOR : null;
            if (k == null) continue;
            long p = en.getKey();
            boolean fresh = !first && PLACED.remove(p);
            BlockPos bp = BlockPos.fromLong(p);
            AssetData.Asset a = d.register(w, k, p, n.kind.label + " " + bp.getX() + "," + bp.getZ(), fresh);
            seen.add(a.id);
            switch (k) {
                case TRANSFORMER -> { use(w, a, Thermal.agedHours(n), 1, now); measured(a, n.insul * 100); }
                case BREAKER -> { use(w, a, n.ops + n.wear * a.kind.life, 1, now); measured(a, Math.max(0, 100 - n.wear * 100)); }
                case MOTOR -> {
                    boolean run = n.loadVA > 0;
                    if (run) a.m[0] += 10.0 / 50.0;                                // run hours
                    if (run && a.m[1] == 0) a.m[2]++;                              // starts
                    a.m[1] = run ? 1 : 0;
                    use(w, a, a.m[0] + a.m[2] * 0.5 + Thermal.replica(n) * 0, 1, now);
                    measured(a, n.insul * 100);
                }
                case INSULATOR -> { wet(w, a, bp); use(w, a, a.m[0] + n.surges * 2, 1, now); measured(a, n.insul * 100); }
                default -> { }
            }
        }
        // OHLE section insulators
        try {
            for (com.dogpound.railmap.ohle.Ohle.Section s : com.dogpound.railmap.ohle.Ohle.known(w))
                for (long ins : s.insulators) {
                    boolean fresh = !first && PLACED.remove(ins);
                    BlockPos bp = BlockPos.fromLong(ins);
                    AssetData.Asset a = d.register(w, AssetKind.INSULATOR, ins, "OLE section insulator " + bp.getX() + "," + bp.getZ(), fresh);
                    seen.add(a.id);
                    wet(w, a, bp);
                    use(w, a, a.m[0], 1, now);
                }
        } catch (LinkageError | RuntimeException ignored) { }
        // track pieces with a running record
        if (IR) {
            TrackData td = TrackData.get(w);
            for (Map.Entry<Long, TrackData.Seg> en : td.segs.entrySet()) {
                TrackData.Seg s = en.getValue();
                if (s.lengthM == 0 && s.tonnes <= 0) continue;
                BlockPos bp = BlockPos.fromLong(en.getKey());
                AssetData.Asset a = d.register(w, AssetKind.TRACK, en.getKey(), "track " + bp.getX() + "," + bp.getZ() + (s.radius > 0 ? " R" + s.radius : ""), false);
                seen.add(a.id);
                double curve = s.radius > 0 ? 1 + 400.0 / s.radius : 1, fast = s.maxKmh > 120 ? 1.3 : 1;
                use(w, a, s.tonnes / 1e6, curve * fast, now);
                measured(a, s.tqi());
                if (s.inspectedAt > a.inspected) { a.inspected = s.inspectedAt; a.inspectedBy = s.inspectedBy; }   // a trolley / recording-car run counts
            }
        }
        // gone: loaded, but the gear is not there any more
        for (AssetData.Asset a : new ArrayList<>(d.all())) {
            if (seen.contains(a.id)) { a.lastSeen = now; continue; }
            boolean gone;
            switch (a.kind) {
                case TRANSFORMER, BREAKER, MOTOR -> gone = g.nodes().get(a.pos) == null;
                case TRACK -> gone = TrackData.get(w).peek(a.at()) == null;
                case INSULATOR -> gone = g.nodes().get(a.pos) == null && w.isBlockLoaded(a.at()) && !(w.getBlockState(a.at()).getBlock() instanceof com.dogpound.railmap.signal.BlockCatenary);
                case LINING -> gone = d.at(AssetKind.TUNNEL, a.pos) == null;
                default -> gone = w.isBlockLoaded(a.at()) && w.getTileEntity(a.at()) == null;
            }
            if (gone) d.remove(w, a, "gear removed");
        }
        // vehicles: worn wheels / bearings start axle cracks (the axle tester finds them)
        if (IR && AssetConfig.latentDefects) vehicles(w, d);
        for (AssetData.Asset a : d.all()) condition(w, d, a, now);
        defects(w, d, now);
        alarms(w, d, now);
        d.seeded = true;
        d.markDirty();
    }

    private static Map<String, Object> info(IWiredGear g) { Map<String, Object> m = new HashMap<>(); try { g.gearInfo(m); } catch (RuntimeException ignored) { } return m; }
    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }

    /** wet days: an exposed insulator / lining in the rain (or flooded) */
    private static void wet(World w, AssetData.Asset a, BlockPos bp) {
        if (!w.isBlockLoaded(bp)) return;
        if (w.isRainingAt(bp.up()) || w.isRaining() && w.canSeeSky(bp.up())) a.m[0] += 10.0 / 1200.0 * 3;
        else a.m[0] += 10.0 / 1200.0 * 0.3;                                    // dry days pollute a little too
    }

    /** a tunnel's lining: water through it (rain above, water next to it) and frost (cold biome) */
    private static void lining(World w, AssetData d, AssetData.Asset tunnel, long p, boolean fresh, Set<String> seen, long now) {
        AssetData.Asset l = d.register(w, AssetKind.LINING, p, "lining of " + tunnel.name, fresh);
        seen.add(l.id);
        l.deps.add(tunnel.id);
        BlockPos bp = BlockPos.fromLong(p);
        boolean wet = false, cold = w.getBiome(bp).getTemperature(bp) < 0.2f;
        for (BlockPos q : BlockPos.getAllInBoxMutable(bp.add(-3, -1, -3), bp.add(3, 3, 3)))
            if (w.getBlockState(q).getMaterial() == net.minecraft.block.material.Material.WATER) { wet = true; break; }
        if (w.isRaining() && w.canSeeSky(bp.up(8))) wet = true;
        l.m[0] += 10.0 / 1200.0 * ((wet ? 4 : 0.5) + (cold ? 2 : 0));
        use(w, l, l.m[0], 1, now);
    }

    /** advance an asset's use from a raw cumulative counter (k = how hard each unit of it is: curves, fast running) */
    static void use(World w, AssetData.Asset a, double raw, double k, long now) {
        if (a.raw < 0 || raw < a.raw) { a.raw = raw; return; }             // first look / counter was reset (a renewal)
        double du = (raw - a.raw) * k;
        a.raw = raw;
        if (!AssetConfig.ageing || du <= 0) return;
        a.use += du;
        a.rateMark += du;
        long dt = now - a.rateTick;
        if (dt >= 24000 / 4) {                                               // the rate of use, smoothed over quarter days
            double perDay = a.rateMark / (dt / 24000.0);
            a.ratePerDay = a.ratePerDay <= 0 ? perDay : a.ratePerDay * 0.7 + perDay * 0.3;
            a.rateMark = 0;
            a.rateTick = now;
        }
    }

    private static void measured(AssetData.Asset a, float v) { a.measured = v; }

    /** condition: the life curve, what the asset's own system measures, and its open defects */
    static void condition(World w, AssetData d, AssetData.Asset a, long now) {
        double life = Math.max(0, Math.min(1.5, a.lifeUsed()));
        double c = 100 * (1 - 0.85 * Math.pow(Math.min(1, life), 1.6)) - (life > 1 ? (life - 1) * 60 : 0);
        if (a.measured >= 0) c = Math.min(c, a.measured);
        for (AssetData.Defect df : d.defectsOf(a.id)) if (df.stage < AssetData.REPAIRED) c -= 30 * Math.min(1.2, df.size / Math.max(1e-6, df.crit));
        float nc = (float) Math.max(0, Math.min(100, c));
        if (Math.abs(nc - a.cond) > 0.05f) {
            int band = band(a.cond), nb = band(nc);
            a.cond = nc;
            if (nb > band) a.note(w, "condition now " + Math.round(nc) + "% (" + BANDS[nb] + ")");
        }
    }

    public static final String[] BANDS = {"GOOD", "FAIR", "POOR", "VERY POOR", "END OF LIFE"};
    public static int band(float c) { return c >= 80 ? 0 : c >= 60 ? 1 : c >= 40 ? 2 : c >= 20 ? 3 : 4; }

    /** hidden defects start in worn assets and grow with use */
    static void defects(World w, AssetData d, long now) {
        if (!AssetConfig.latentDefects) return;
        Random r = w.rand;
        for (AssetData.Asset a : d.all()) {
            boolean has = false;
            for (AssetData.Defect df : d.defectsOf(a.id)) if (df.stage < AssetData.REPAIRED) { has = true; break; }
            double p = Math.pow(Math.max(0, 1 - a.cond / 100.0), 2) * 0.015 * AssetConfig.ageingRate;
            if (!has && a.cond < 85 && r.nextDouble() < p) {
                com.dogpound.railmap.infra.RailFlaws.Flaw fl = com.dogpound.railmap.infra.RailFlaws.pick(w, a, r);   // infra #41: a real UIC rail defect class
                double crit = fl == null ? a.kind.crit : fl.crit;
                d.addDefect(w, a.id, a.pos, fl == null ? a.kind.defect : fl.type(), a.kind.defectUnit, crit * (0.05 + r.nextDouble() * 0.1), crit);
            }
        }
        AlarmManager am = AlarmManager.get(w);
        for (AssetData.Defect df : d.defects) {
            if (df.stage >= AssetData.REPAIRED) continue;
            AssetData.Asset a = d.byId(df.asset);
            double wear = a == null ? 0.004 : Math.max(0.001, a.ratePerDay / Math.max(1e-6, a.kind.life) * 0.5);
            com.dogpound.railmap.infra.RailFlaws.Flaw fl = com.dogpound.railmap.infra.RailFlaws.of(df.type);
            df.size += df.crit * wear * (0.5 + r.nextDouble()) * AssetConfig.ageingRate * (fl == null ? 1 : fl.growth);   // ~half its life to grow to critical at today's rate
            if (df.size >= df.crit && !df.trail.isEmpty() && !df.trail.getFirst().contains("CRITICAL")) {
                df.trail.addFirst(com.dogpound.railmap.depot.DepotClock.stamp(w) + " grew past CRITICAL (" + String.format(Locale.ROOT, "%.1f %s", df.size, df.unit) + ")");
                String where = a == null ? df.asset : a.id + " " + a.name;
                if (df.stage == AssetData.LATENT) {                            // nobody found it in time: it shows itself
                    df.seen = df.size; df.unc = df.size * 0.3; df.by = "train crew report"; df.method = "in service";
                    df.step(w, AssetData.DISCOVERED, "reported in service - it was never found by inspection");
                }
                standing(w, "defect:" + df.id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MAJOR, "DEFECT CRITICAL #" + df.id + ": " + df.type + " on " + where
                        + (AssetConfig.defectConsequences ? " - it has failed" : " - repair it now"), df.pos == Long.MIN_VALUE ? null : BlockPos.fromLong(df.pos));
                Recorder.event(w, "ASSET: defect #" + df.id + " critical on " + where);
                if (AssetConfig.defectConsequences && a != null) Consequences.fail(w, a, df);
            }
        }
    }

    /** rolling stock: worn wheels or bearings may start an axle fatigue crack */
    private static void vehicles(World w, AssetData d) {
        try {
            WearData wd = WearData.get(w);
            for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                if (s.isDead()) continue;
                WearData.Record r = wd.peek(s.getUUID());
                if (r == null) continue;
                float worst = Math.max(r.wear[WearData.Part.WHEELS.ordinal()], r.wear[WearData.Part.BEARINGS.ordinal()]);
                if (worst < 40) continue;
                String id = "VEH:" + s.getUUID();
                boolean has = false;
                for (AssetData.Defect df : d.defectsOf(id)) if (df.stage < AssetData.REPAIRED) { has = true; break; }
                if (!has && w.rand.nextDouble() < (worst - 40) / 60.0 * 0.01 * AssetConfig.ageingRate)
                    d.addDefect(w, id, Long.MIN_VALUE, "axle fatigue crack (" + com.dogpound.railmap.depot.DepotTicker.name(s) + ")", "mm", 0.4 + w.rand.nextDouble() * 0.6, 6);
            }
        } catch (LinkageError | RuntimeException ignored) { }
    }

    /** one aggregate alarm per kind: overdue inspections; one for uncommissioned gear */
    static void alarms(World w, AssetData d, long now) {
        AlarmManager am = AlarmManager.get(w);
        int[] over = new int[AssetKind.values().length];
        int uncomm = 0;
        String firstUn = "";
        for (AssetData.Asset a : d.all()) {
            if (a.overdue(now)) over[a.kind.ordinal()]++;
            if (a.comm != AssetData.COMMISSIONED) { uncomm++; if (firstUn.isEmpty()) firstUn = a.id + " " + a.name; }
        }
        for (AssetKind k : AssetKind.values()) {
            String key = "inspdue:" + k.name();
            if (AssetConfig.inspectionAlarms && over[k.ordinal()] > 0)
                standing(w, key, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "INSPECTION OVERDUE: " + over[k.ordinal()] + " " + k.label.toLowerCase(Locale.ROOT) + " asset(s) past their "
                        + k.inspectDays + "-day inspection - use the " + k.tool.label, null);
            else am.clear(key);
        }
        if (AssetConfig.commissioning && uncomm > 0)
            standing(w, "uncommissioned", AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, uncomm + " asset(s) UNCOMMISSIONED (e.g. " + firstUn + ") - run their acceptance tests at a Commissioning Test Set", null);
        else am.clear("uncommissioned");
    }

    /** a standing alarm raised once; while it stays active only its text follows the facts (no re-raise, no new count) */
    public static void standing(World w, String key, AlarmManager.Cat cat, AlarmManager.Sev sev, String text, BlockPos at) {
        AlarmManager m = AlarmManager.get(w);
        for (AlarmManager.Alarm a : m.all()) if (a.key.equals(key) && a.active) { if (!a.text.equals(text)) { a.text = text; m.touch(); } return; }
        m.raise(w, key, cat, sev, text, at, true);
    }

    /** server stopping */
    public static void clear() { PLACED.clear(); }
}
