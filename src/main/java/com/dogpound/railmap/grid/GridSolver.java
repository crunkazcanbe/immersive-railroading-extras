package com.dogpound.railmap.grid;

import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The once-a-second electrical solve for the real (levelled) networks (requested feature): loads in watts, the power
 * triangle (P, Q, S, PF), current, and - along the actual path each feeder is supplied by - every cable's current,
 * I²R loss, voltage drop and conductor temperature; voltage passed down through the transformers (ratio × tap, minus
 * their impedance drop). Losses are drawn from the grid as real energy. Legacy networks are left to the old rules.
 */
public final class GridSolver {
    private GridSolver() {}

    static final double AMBIENT = 25;

    /** world changes made by the solve (burnt cables, destroyed breakers) wait until it has finished walking the
     *  networks: removing nodes mid-walk left the tree pointing at nodes that no longer exist (server crash) */
    private static final List<Runnable> AFTER = new ArrayList<>();

    public static void solve(World w, GridData d) {
        try { solve0(w, d); }
        finally { for (Runnable r : AFTER) r.run(); AFTER.clear(); }
    }

    private static void solve0(World w, GridData d) {
        List<GridData.Net> nets = d.nets();
        Map<Long, GridData.Node> nodes = d.nodes();

        // 1. demand of each network in watts, bottom-up through the converters (a few passes settle any chain)
        double[] demand = new double[nets.size()];
        for (int pass = 0; pass < 6; pass++) {
            for (GridData.Net n : nets) {
                double p = 0;
                for (long pos : n.nodes) { GridData.Node nd = nodes.get(pos); if (nd.kind.load()) p += nd.drawLast * Elec.W_PER_FE_T / 20.0; }
                for (long c : n.outs) { GridData.Node cv = nodes.get(c); if (cv != null && cv.net >= 0 && cv.net < nets.size()) p += demand[cv.net] / 0.98; }
                demand[n.id] = p;
            }
        }

        // 2. voltages top-down: networks fed by an intake / generator first, then those fed through converters
        List<GridData.Net> order = new ArrayList<>();
        Set<Integer> done = new HashSet<>();
        for (int pass = 0; pass < 8 && order.size() < nets.size(); pass++) {
            for (GridData.Net n : nets) {
                if (done.contains(n.id)) continue;
                boolean ready = true;
                for (long c : n.ins) { GridData.Node cv = nodes.get(c); if (cv != null && cv.upNet >= 0 && !done.contains(cv.upNet) && cv.upNet != n.id) ready = false; }
                if (ready || pass == 7) { order.add(n); done.add(n.id); }
            }
        }

        for (GridData.Net n : order) {
            if (n.level == null) continue;
            Elec.Level lv = n.level;
            // source voltage: through a converter = upstream voltage at its terminals × ratio × tap - impedance drop
            double pu = 1;
            for (long c : n.ins) {
                GridData.Node cv = nodes.get(c);
                if (cv == null || cv.upNet < 0 || cv.upNet >= nets.size()) continue;
                GridData.Net up = nets.get(cv.upNet);
                Elec.Level rated = Elec.Level.byName(cv.primary, Elec.Level.MV13800);
                double upV = up.level == null ? rated.volts : up.level.volts * nodeAt(up, cv, d);
                double tap = (1 + (cv.tap - 9) * 0.0125) * (1 + cv.octap * 0.025);
                double sThrough = demand[n.id] / Math.max(0.5, n.pf);
                double zDrop = 0.06 * Math.min(2, sThrough / Math.max(1, cv.kva * 1000.0));   // ~6 % impedance at full load
                pu = upV / rated.volts * tap - zDrop;
            }
            n.puSource = pu;

            // 4. prospective short-circuit level: utility 500 MVA, rotating machines Xd'' 15 %, transformers 6 % (rectifier
            //    8 %) in series with what's upstream, inverters / DC-DC current-limited at 1.5x rating
            double sk = 0;
            for (long pos : n.nodes) {
                GridData.Node nd = nodes.get(pos);
                if (!nd.kind.source() || !nd.feeding()) continue;
                sk += nd.kind == GridKind.INTAKE ? 500e6 : nd.kind == GridKind.DIESEL || nd.kind == GridKind.TURBINE ? 2e6 / 0.15 : 0.5e6 * 1.2;
            }
            for (long c : n.ins) {
                GridData.Node cv = nodes.get(c);
                if (cv == null || !cv.passes() || cv.upNet < 0 || cv.upNet >= nets.size()) continue;
                double up = Math.max(1, nets.get(cv.upNet).faultVA), sr = cv.kva * 1000.0;
                sk += cv.kind == GridKind.INVERTER || cv.kind == GridKind.DCDC ? Math.min(up, 1.5 * sr)
                        : 1 / (1 / up + (cv.kind == GridKind.RECTIFIER ? 0.08 : 0.06) / sr);
            }
            n.faultVA = sk;
            // generators: share of the load (island: they carry it all, by rating; with the utility: their governor setting)
            double genVA = 0; boolean utility = false; GridData.Node firstGen = null;
            for (long pos : n.nodes) {
                GridData.Node nd = nodes.get(pos);
                if (nd.kind == GridKind.INTAKE && nd.feeding()) utility = true;
                if ((nd.kind == GridKind.DIESEL || nd.kind == GridKind.TURBINE) && nd.feeding()) { genVA += nd.kva * 1000.0; if (firstGen == null) firstGen = nd; }
            }
            for (long c : n.ins) { GridData.Node cv = nodes.get(c); if (cv != null && cv.passes() && cv.upNet >= 0 && cv.upNet < nets.size() && nets.get(cv.upNet).utility) utility |= nets.get(cv.upNet).problem.isEmpty(); }
            double sNet = Math.sqrt(demand[n.id] * demand[n.id] + n.qVar * n.qVar);
            for (long pos : n.nodes) {
                GridData.Node nd = nodes.get(pos);
                if ((nd.kind == GridKind.DIESEL || nd.kind == GridKind.TURBINE) && nd.feeding()) nd.genLoad = utility ? -1 : sNet / Math.max(1, genVA);
            }
            n.utility = utility;
            n.hzPu = !utility && firstGen != null ? firstGen.speed : 1;
            n.isc = lv.amps(sk);

            // power triangle
            double p = demand[n.id];
            double base = !lv.ac ? 1.0 : 0.85;
            double need = Math.max(1, Math.min(4, Math.ceil(4 * p / Math.max(1, ratingW(n, nodes)))));
            n.pf = !lv.ac ? 1.0 : !n.capacitor ? base : base + (0.98 - base) * Math.min(1, n.steps / need);
            double motorQ = 0;
            for (long pos : n.nodes) { GridData.Node nd = nodes.get(pos); if (nd.kind == GridKind.MOTOR) motorQ += nd.loadVar; }
            n.pW = p;
            n.qVar = Elec.reactive(p, n.pf) + motorQ;
            if (motorQ > 0 && lv.ac) { double s0 = Math.sqrt(p * p + n.qVar * n.qVar); n.pf = s0 <= 0 ? 1 : Math.max(0.05, p / s0); }
            n.sVA = Math.sqrt(p * p + n.qVar * n.qVar);
            n.amps = lv.amps(n.sVA);
            // power electronics have no thermal margin: past 150 % of rating their output trips at once
            for (long c : n.ins) {
                GridData.Node cv = nodes.get(c);
                if (cv == null || !cv.kind.electronic() || !cv.dcOn || n.sVA <= cv.kva * 1500.0) continue;
                cv.dcOn = false;
                d.touch();
                BlockPos bp = BlockPos.fromLong(c);
                String why = String.format(Locale.ROOT, "OVERLOAD %s on a %d kVA unit", Elec.si(n.sVA, "VA"), cv.kva);
                if (w.isBlockLoaded(bp) && w.getTileEntity(bp) instanceof TileGrid t) t.rectifierTripped(why);
                GridTicker.alarm(w, c, "§c⚡ " + cv.kind.label + " tripped at " + bp.getX() + " " + bp.getY() + " " + bp.getZ() + ": " + why);
            }

            // 3. the path each load is fed along: a breadth-first tree from the network's sources / feeding converters
            Map<Long, Long> parent = new HashMap<>();
            ArrayDeque<Long> q = new ArrayDeque<>();
            for (long pos : n.nodes) {
                GridData.Node nd = nodes.get(pos);
                if ((nd.kind.source() && nd.feeding()) || nd.sided() || nd.kind == GridKind.BATTERY) { parent.put(pos, pos); q.add(pos); nd.pu = pu; }
            }
            List<Long> visit = new ArrayList<>();
            Set<Long> inNet = new HashSet<>(n.nodes);
            while (!q.isEmpty()) {
                long cur = q.poll();
                visit.add(cur);
                GridData.Node cn = nodes.get(cur);
                BlockPos bp = BlockPos.fromLong(cur);
                for (EnumFacing f : EnumFacing.values()) {
                    if (cn.sided() && f == cn.back()) continue;
                    long nb = bp.offset(f).toLong();
                    if (!inNet.contains(nb) || parent.containsKey(nb)) continue;
                    GridData.Node nn = nodes.get(nb);
                    if (GridData.opens(cn) && cn.breaker != 0) continue;
                    parent.put(nb, cur);
                    q.add(nb);
                }
            }
            // own demand of each node: feeders draw; nodes touching a converter's primary carry what it passes on
            Map<Long, Double> load = new HashMap<>();
            for (long pos : n.nodes) {
                GridData.Node nd = nodes.get(pos);
                if (nd.kind == GridKind.FEEDER) load.merge(pos, nd.drawLast * Elec.W_PER_FE_T / 20.0, Double::sum);
                if (nd.kind == GridKind.MOTOR && nd.loadVA > 0) load.merge(pos, nd.loadVA * Math.max(0.05, n.pf), Double::sum);   // its VA, scaled so /pf gives it back
            }
            for (long c : n.outs) {
                GridData.Node cv = nodes.get(c);
                if (cv == null || cv.net < 0 || cv.net >= nets.size()) continue;
                long touch = touching(c, inNet, cv);
                if (touch != Long.MIN_VALUE) load.merge(touch, demand[cv.net] / 0.98, Double::sum);
            }
            // accumulate down the tree (children before parents)
            Map<Long, Double> through = new HashMap<>(load);
            for (int i = visit.size() - 1; i >= 0; i--) {
                long cur = visit.get(i);
                Long par = parent.get(cur);
                if (par != null && par != cur) through.merge(par, through.getOrDefault(cur, 0.0), Double::sum);
            }
            // a cable on a network above its insulation class flashes over: a short circuit the nearest breaker must clear
            if (n.problem.startsWith("FLASHOVER") && n.isc > 0)
                for (long cur : visit) {
                    GridData.Node cn = nodes.get(cur);
                    if (cn.kind == GridKind.CABLE && cn.cable().ins.maxVolts < lv.volts) { fault(w, d, n, cur, parent, "insulation flashover"); break; }
                }
            // walk from the sources outward: current, loss, drop and temperature of every node on the path
            double loss = 0;
            for (long cur : visit) {
                GridData.Node cn = nodes.get(cur);
                if (cn == null) continue;
                double s = through.getOrDefault(cur, 0.0) / Math.max(0.05, n.pf);
                cn.amps = lv.amps(s);
                Long par = parent.get(cur);
                GridData.Node pn = par == null ? null : nodes.get(par);
                double upPu = pn == null || par == cur ? pu : pn.pu;
                if (cn.kind == GridKind.CABLE) {
                    Elec.Spec sp = cn.cable();
                    double r = sp.ohmsPerM(cn.temp), x = sp.reactancePerM();
                    loss += Elec.lossW(lv, cn.amps, r);
                    cn.pu = upPu - Elec.dropV(lv, cn.amps, r, x, n.pf) / lv.volts;
                    double frac = cn.amps / Math.max(1, sp.ampacity());
                    double steady = Elec.steadyTemp(AMBIENT, sp.ins, frac);
                    cn.temp += (float) ((steady - cn.temp) * 0.08);                       // ~12 s thermal time constant
                    if (cn.temp > sp.ins.maxTemp + 60) { fault(w, d, n, cur, parent, "cable burnt through"); burn(w, d, cur, cn, lv); }
                } else if (par != null && par != cur) cn.pu = upPu;
            }
            n.lossW = loss;
            n.eff = p <= 0 ? 1 : p / (p + loss);
            // the losses are real energy: take them from the grid
            long lossFe = Math.round(loss / Elec.W_PER_FE_T * 20);
            if (lossFe > 0 && n.problem.isEmpty()) d.draw(n, lossFe);
        }
        for (GridData.Node nd : nodes.values()) if (nd.kind.load()) { nd.drawLast = nd.drawThis; nd.drawThis = 0; }
    }

    /** the voltage (per unit) at the node of `up` that touches converter cv's primary side */
    private static double nodeAt(GridData.Net up, GridData.Node cv, GridData d) {
        for (long p : up.nodes) {
            GridData.Node n = d.nodes().get(p);
            if (n == null) continue;
            BlockPos bp = BlockPos.fromLong(p);
            for (EnumFacing f : EnumFacing.values()) {
                GridData.Node nb = d.nodes().get(bp.offset(f).toLong());
                if (nb == cv) return n.pu > 0 ? n.pu : up.puSource;
            }
        }
        return up.puSource;
    }

    private static long touching(long conv, Set<Long> inNet, GridData.Node cv) {
        if (cv.facing < 0) return Long.MIN_VALUE;
        long nb = BlockPos.fromLong(conv).offset(cv.back()).toLong();
        return inNet.contains(nb) ? nb : Long.MIN_VALUE;
    }

    private static double ratingW(GridData.Net n, Map<Long, GridData.Node> nodes) {
        double kva = 0;
        for (long c : n.ins) { GridData.Node cv = nodes.get(c); if (cv != null) kva += cv.kva; }
        return kva > 0 ? kva * 1000 : (double) GridData.RATING * Elec.W_PER_FE_T * Math.max(1, n.transformers);
    }

    /** a short circuit at `at`: the nearest closed breaker between it and the source trips (selective protection).
     *  If the fault current is more than that breaker can break, it can't clear it - it fails violently. */
    private static void fault(World w, GridData d, GridData.Net n, long at, Map<Long, Long> parent, String what) {
        Map<Long, GridData.Node> nodes = d.nodes();
        Long cur = at;
        for (int guard = 0; cur != null && guard < 100_000; guard++) {
            GridData.Node nd = nodes.get(cur);
            if (nd != null && nd.kind == GridKind.BREAKER && nd.breaker == 0) {
                BlockPos bp = BlockPos.fromLong(cur);
                if (n.isc > nd.kA * 1000.0) {
                    GridTicker.alarm(w, cur, String.format(Locale.ROOT, "§4💥 BREAKER FAILED at %d %d %d: %s fault current, it is only rated %d kA - fit a 40 / 63 kA breaker",
                            bp.getX(), bp.getY(), bp.getZ(), Elec.si(n.isc, "A"), nd.kA));
                    nd.breaker = 2;                                                    // stops conducting now; the block goes after the solve
                    AFTER.add(() -> {
                        if (w.isBlockLoaded(bp)) {
                            w.setBlockToAir(bp);
                            w.newExplosion(null, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2.0f, true, false);   // arc flash: fire, no crater
                        }
                        d.remove(bp);
                    });
                } else {
                    GridTicker.alarm(w, cur, String.format(Locale.ROOT, "§c⚡ Short circuit (%s): breaker at %d %d %d cleared %s", what, bp.getX(), bp.getY(), bp.getZ(), Elec.si(n.isc, "A")));
                    if (w.isBlockLoaded(bp) && w.getTileEntity(bp) instanceof TileGrid t) t.setBreaker((byte) 2, "tripped: short circuit " + Elec.si(n.isc, "A"));
                    else { nd.breaker = 2; d.touch(); }
                }
                return;
            }
            Long par = parent.get(cur);
            if (par == null || par.equals(cur)) return;
            cur = par;
        }
    }

    /** a cable far past its insulation temperature burns out: the block goes, a fire starts, the network splits */
    private static void burn(World w, GridData d, long pos, GridData.Node cn, Elec.Level lv) {
        BlockPos bp = BlockPos.fromLong(pos);
        GridTicker.alarm(w, pos, String.format(Locale.ROOT, "§c🔥 Cable BURNT OUT at %d %d %d: %s carrying %.0f A (rated %.0f A) on %s",
                bp.getX(), bp.getY(), bp.getZ(), cn.cable().label(), cn.amps, cn.cable().ampacity(), lv.label));
        AFTER.add(() -> {
            if (w.isBlockLoaded(bp)) {
                w.setBlockToAir(bp);
                if (w.isAirBlock(bp)) w.setBlockState(bp, Blocks.FIRE.getDefaultState());
            }
            d.remove(bp);
        });
    }
}
