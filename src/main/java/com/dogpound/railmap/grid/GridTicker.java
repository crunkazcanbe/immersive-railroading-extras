package com.dogpound.railmap.grid;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Once a second per dimension: settle the load figures, overload protection, battery charging. */
public class GridTicker {
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || e.world.getTotalWorldTime() % 20 != 11) return;
        GridData d = GridData.get(e.world);
        if (d.nodes().isEmpty()) return;
        boolean storm = e.world.isThundering();
        // a network that went dead for lack of energy comes back once its sources have some again (generators run up,
        // an intake gets fed): the "No energy" verdict is only made on a rebuild, so ask for one
        for (GridData.Net n : d.nets()) {
            if (!n.problem.startsWith("No energy")) continue;
            boolean refilled = false;
            for (long p : n.nodes) { GridData.Node nd = d.nodes().get(p); if (nd.energy > 0 && ((nd.kind.source() && nd.feeding()) || (nd.kind == GridKind.BATTERY && nd.batOn))) refilled = true; }
            if (refilled) { d.touch(); break; }
        }
        GridSolver.solve(e.world, d);                                  // real electricity: P/Q/S, currents, drops, cable heat
        for (java.util.Map.Entry<Long, GridData.Node> en : d.nodes().entrySet())   // ambient, fans, derating (#42)
            if (en.getValue().kind == GridKind.TRANSFORMER || en.getValue().kind == GridKind.TRACTION || en.getValue().kind.electronic()) Thermal.tick(e.world, d, en.getKey(), en.getValue());
        Thermal.models(e.world, d);                                    // hot spot, loss of life, 49 replicas (her 48-next #22)
        try {
            Protection.tick(e.world, d);                               // IED timed functions: 51, 27/59, 81, 64, 87T inrush (#34, #40)
            Insulation.tick(e.world, d);                               // insulation ageing, earth faults AC / DC (#35)
            PowerQuality.tick(e.world, d);                             // unbalance, harmonics, PF, frequency (#39)
            Ups.tick(e.world, d);                                      // 12 V battery chargers / UPS (#41)
        } finally { Protection.flush(); }
        for (GridData.Net n : d.nets()) {
            double load = n.drawThisSecond / 20.0, rating = (double) GridData.RATING * Math.max(1, n.transformers);
            // capacitor bank: AUTO switches in the steps the load needs (4 steps cover a full rating)
            int need = (int) Math.min(4, Math.ceil(4 * load / rating));
            int steps = 0;
            for (long p : n.nodes) {
                GridData.Node nd = d.nodes().get(p);
                if (nd.kind != GridKind.CAPACITOR) continue;
                if (nd.capAuto && nd.steps != need) { nd.steps = need; d.markDirty(); }
                steps += nd.steps;
            }
            n.steps = steps;
            // network voltage: the tap changers set it, load pulls it down, over-compensation pushes it up
            if (n.level != null) n.volt = n.puSource;                   // real networks: the solver's figure
            else if (n.transformers > 0) {
                double taps = 0;
                int tx = 0;
                for (long p : n.nodes) {
                    GridData.Node nd = d.nodes().get(p);
                    if (nd.kind == GridKind.TRANSFORMER || nd.kind == GridKind.TRACTION) { taps += 1 + (nd.tap - 9) * 0.0125; tx++; }
                }
                n.volt = taps / Math.max(1, tx) - 0.08 * Math.min(1.5, load / rating) + 0.02 * Math.max(0, steps - need - 1);
            }
            if (n.transformers > 0 || n.level != null) {
                boolean avrTurn = e.world.getTotalWorldTime() % 60 == 11;               // a real AVR waits before each tap step
                for (long p : n.nodes) {
                    GridData.Node nd = d.nodes().get(p);
                    if ((nd.kind != GridKind.TRANSFORMER && nd.kind != GridKind.TRACTION) || !nd.avr || !avrTurn) continue;
                    if (n.volt < 0.985 && nd.tap < 17) { nd.tap++; d.markDirty(); }
                    else if (n.volt > 1.015 && nd.tap > 1) { nd.tap--; d.markDirty(); }
                }
            } else if (n.level == null) n.volt = 1.0;
            // transformer thermal model: overload heats the windings, light load lets them cool; the fans set how fast
            if (n.transformers > 0) {
                for (long p : n.nodes) {
                    GridData.Node nd = d.nodes().get(p);
                    if (nd.kind != GridKind.TRANSFORMER && nd.kind != GridKind.TRACTION) continue;
                    boolean fans = !nd.fanFail && (nd.cool == 2 || (nd.cool == 1 && nd.heat > 40));
                    double cooling = nd.cool == 0 ? 0.6 : fans ? 2.4 : 1.2;
                    double over = n.volt > 1.08 ? (n.volt - 1.08) * 60 : 0;                // over-voltage over-fluxes the core
                    double rated = rating * Math.max(0.3, nd.derate);                         // ambient + cooling derating (#42)
                    nd.heat = (float) Math.max(0, Math.min(120, nd.heat + (load > rated ? (load / rated - 1) * 12 : -cooling) + over));
                    if (!nd.hot && nd.heat >= 100) {
                        nd.hot = true; d.touch(); alarm(e.world, p, "§c⚠ Transformer OVERHEATED and tripped (load " + (int) load + " FE/t, rating " + (int) rating + (nd.derate < 0.999 ? String.format(java.util.Locale.ROOT, ", derated to %.0f%%: %s", nd.derate * 100, Thermal.describe(nd)) : "") + ")");
                        History.log(e.world, p, "TEMP", String.format(java.util.Locale.ROOT, "OVERTEMPERATURE trip: winding rise %.0f °C at %.0f °C ambient, load %d FE/t of %d (%s)", nd.heat, nd.ambient, (int) load, (int) rating, Thermal.describe(nd)));
                        long now = e.world.getTotalWorldTime();
                        nd.hotTrips = now - nd.lastHotTrip < 12000 ? nd.hotTrips + 1 : 1;
                        nd.lastHotTrip = now;
                        if (nd.hotTrips >= 3) TileGrid.burnOut(e.world, net.minecraft.util.math.BlockPos.fromLong(p), "windings melted - overheated 3 times in 10 minutes and the oil caught fire");
                    }
                    if (nd.hot && nd.heat < 40) { nd.hot = false; d.touch(); alarm(e.world, p, "§a✔ Transformer cooled down - back in service"); History.log(e.world, p, "TEMP", "cooled to " + Math.round(nd.heat) + " °C rise - back in service"); }
                }
            }
            // lightning on a network WITH an arrester: the arrester takes it and its surge counter clicks over (the intake and
            // feeder have their own arrester counters too)
            if (storm && n.arrester && e.world.rand.nextInt(90) == 0)
                for (long p : n.nodes) { GridData.Node nd = d.nodes().get(p); if (nd.kind == GridKind.ARRESTER || nd.kind == GridKind.INTAKE || nd.kind == GridKind.FEEDER) { nd.surges++; d.markDirty(); } }
            // lightning: a live network with no surge arrester flashes over in a thunderstorm
            if (storm && !n.arrester && n.problem.isEmpty() && e.world.rand.nextInt(90) == 0) {
                for (long p : n.nodes) {
                    GridData.Node nd = d.nodes().get(p);
                    if (nd.kind != GridKind.BREAKER || nd.breaker != 0) continue;
                    BlockPos bp = BlockPos.fromLong(p);
                    if (e.world.isBlockLoaded(bp) && e.world.getTileEntity(bp) instanceof TileGrid t) t.setBreaker((byte) 2, "TRIPPED: lightning flashover (no surge arrester)");
                    else { nd.breaker = 2; d.touch(); }
                }
                alarm(e.world, n.nodes.get(0), "§e⚡ Lightning flashover! A network with no Surge Arrester tripped");
            }
            n.lastSecond = n.drawThisSecond;
            n.drawThisSecond = 0;
            if (n.lastSecond <= 0) continue;
            for (long p : n.nodes) {
                GridData.Node nd = d.nodes().get(p);
                if (nd.kind != GridKind.BREAKER) continue;
                BlockPos bp = BlockPos.fromLong(p);
                if (n.level != null && TileGrid.hasIed(d, bp)) continue;           // an IED times its own overcurrent in real amps (real networks)
                if (e.world.isBlockLoaded(bp) && e.world.getTileEntity(bp) instanceof TileGrid t) t.checkOverload(n);
                else if (nd.breaker == 0 && n.lastSecond > (long) TileGrid.relayRating(d, bp, nd.rating) * 20) { nd.breaker = 2; nd.lastTrip = "overload " + n.lastSecond / 20 + " FE/t"; History.log(e.world, bp, "TRIP", nd.lastTrip); d.touch(); }
            }
        }
        d.balance();
    }

    /** does this feeder serve this kind of line? */
    private static boolean serves(GridData.Node n, boolean ac, boolean beam) {
        switch (n.feeds) {
            case "Wire": return ac;
            case "Beam": return !ac && beam;
            case "Third rail": return !ac && !beam;
            default: return true;
        }
    }

    /** regenerative braking this second: per network, {energy offered by braking trains, energy other trains took} */
    private static final Map<GridData.Net, long[]> REGEN = new IdentityHashMap<>();

    /** start of the traction second: forget last second's regen offers */
    public static void regenStart() { REGEN.clear(); }

    /**
     * A train braking electrically at {@code at} offers {@code fe} back to the line. Returns the network that takes it,
     * or null (no live feeder here: the energy goes into the train's braking resistors).
     */
    public static GridData.Net offerRegen(World w, BlockPos at, boolean ac, boolean beam, long fe) { return offerRegen(w, at, ac, beam, fe, null); }

    /** only = the feeders whose OHLE section the train is in (ohle.Ohle sectioning, #31); null = every feeder in range */
    public static GridData.Net offerRegen(World w, BlockPos at, boolean ac, boolean beam, long fe, java.util.Set<Long> only) {
        GridData d = GridData.get(w);
        for (Map.Entry<Long, GridData.Node> en : d.nodes().entrySet()) {
            GridData.Node n = en.getValue();
            if (n.kind != GridKind.FEEDER || !serves(n, ac, beam)) continue;
            if (only != null && !only.contains(en.getKey())) continue;
            BlockPos p = BlockPos.fromLong(en.getKey());
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ(), dy = p.getY() - at.getY();
            if (only == null && (dx * dx + dz * dz > (double) n.range * n.range || Math.abs(dy) > 24)) continue;
            GridData.Net net = d.netOf(p);
            if (net == null || !GridData.why(net, ac).isEmpty()) continue;
            REGEN.computeIfAbsent(net, k -> new long[2])[0] += fe;
            return net;
        }
        return null;
    }

    /** where one network's regen went this second, in FE: other trains, batteries, back up the line (AC), resistors */
    public static final class RegenResult {
        public long offered, trains, battery, exported, rejected, ess;

        public double accepted() { return offered <= 0 ? 0 : (double) (offered - rejected) / offered; }

        /** the biggest taker, for the cab readout */
        public String mainSink() {
            long m = Math.max(Math.max(Math.max(trains, battery), Math.max(exported, rejected)), ess);
            if (m <= 0) return "";
            return m == trains ? "trains" : m == ess ? "wayside storage" : m == battery ? "battery" : m == exported ? "grid" : "resistors";
        }
    }

    /**
     * End of the traction second: what other trains did not take charges the network's batteries; on an AC line the
     * rest flows back through the traction transformer to the supply; a DC rectifier cannot pass power back, so on DC
     * the rest is burnt in the trains' braking resistors.
     */
    public static Map<GridData.Net, RegenResult> settleRegen(World w) {
        GridData d = GridData.get(w);
        Map<GridData.Net, RegenResult> out = new IdentityHashMap<>();
        for (Map.Entry<GridData.Net, long[]> en : REGEN.entrySet()) {
            GridData.Net net = en.getKey();
            RegenResult r = new RegenResult();
            r.offered = en.getValue()[0];
            r.trains = Math.min(r.offered, en.getValue()[1]);
            long left = r.offered - r.trains;
            r.ess = com.dogpound.railmap.infra.Ess.absorb(w, net, left);      // infra #38: wayside storage beside the line soaks it up first
            left -= r.ess;
            for (long p : net.nodes) {
                if (left <= 0) break;
                GridData.Node b = d.nodes().get(p);
                if (b == null || b.kind != GridKind.BATTERY || !b.batOn || b.batMode == 2) continue;
                long put = Math.min(left, GridKind.BATTERY.capacity - b.energy);
                if (put <= 0) continue;
                b.energy += put;
                r.battery += put;
                left -= put;
            }
            boolean ac = net.level != null ? net.level.ac : net.traction;
            if (ac) {
                r.exported = left;
                // fed back up the line: it offsets this second's load on the feeders (never below zero)
                for (long p : net.nodes) {
                    GridData.Node f = d.nodes().get(p);
                    if (left <= 0) break;
                    if (f == null || f.kind != GridKind.FEEDER) continue;
                    long cut = Math.min(left, f.drawThis);
                    f.drawThis -= cut;
                    left -= cut;
                }
                net.regenOut += r.exported;
            } else {
                r.rejected = left;
            }
            if (r.battery > 0) d.markDirty();
            out.put(net, r);
        }
        return out;
    }

    /**
     * Power for a train at {@code at}: every live Track Feeder of the right kind whose section covers it (a train between
     * two substations draws from both), regen from braking trains on the same network first, then the feeder's own
     * store and the grid. ac = overhead wire, otherwise DC (monorail beam / maglev guideway / third rail).
     * Returns the FE that reaches the train (after power quality, line voltage and the drop along the section), or -1 when
     * this world has no grid at all.
     */
    public static long supply(World w, BlockPos at, boolean ac, boolean beam, long fe) { return supply(w, at, ac, beam, fe, null); }

    /** only = the feeders whose OHLE section the train is in (ohle.Ohle sectioning, #31: they feed it end to end, past
     *  their radius); null = every feeder in range, as before */
    public static long supply(World w, BlockPos at, boolean ac, boolean beam, long fe, java.util.Set<Long> only) {
        GridData d = GridData.get(w);
        if (d.nodes().isEmpty()) return -1;                       // no grid built here: caller falls back to substations
        if (only != null && only.isEmpty()) return 0;             // a sectioned line with no feeder on its section
        long total = 0;
        java.util.List<Map.Entry<Long, GridData.Node>> cands = new java.util.ArrayList<>();
        for (Map.Entry<Long, GridData.Node> en : d.nodes().entrySet()) {
            GridData.Node n = en.getValue();
            if (n.kind != GridKind.FEEDER || !serves(n, ac, beam)) continue;
            if (only != null && !only.contains(en.getKey())) continue;
            BlockPos p = BlockPos.fromLong(en.getKey());
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ(), dy = p.getY() - at.getY();
            if (only == null && (dx * dx + dz * dz > (double) n.range * n.range || Math.abs(dy) > 24)) continue;
            cands.add(en);
        }
        // infra #37 parallel feeding: each feeder first gives its share (by impedance + voltage), a second pass makes up the rest
        double[] share = com.dogpound.railmap.infra.LoadSharing.shares(w, d, cands, at, ac);
        for (int pass = 0; pass < (share == null ? 1 : 2); pass++)
        for (int ci = 0; ci < cands.size(); ci++) {
            if (total >= fe) break;
            Map.Entry<Long, GridData.Node> en = cands.get(ci);
            GridData.Node n = en.getValue();
            BlockPos p = BlockPos.fromLong(en.getKey());
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ();
            // the far end of a long section gets less: voltage drop along the line
            double reach = Math.min(1, Math.sqrt(dx * dx + dz * dz) / Math.max(1, n.range));
            double atTrain = 1 - 0.4 * reach * reach;
            long want = share != null && pass == 0 ? Math.min(fe - total, (long) Math.ceil(fe * share[ci])) : fe - total;
            if (want <= 0) continue;
            long need = (long) Math.ceil(want / atTrain);                   // what must leave this feeder
            GridData.Net net = d.netOf(p);
            boolean gridOk = net != null && GridData.why(net, ac).isEmpty();
            if (gridOk && !ac && net.level == null && net.rectifier) {
                int nominal = beam ? 1500 : 750;                          // monorail / maglev beam 1500 V, third rail 750 V
                if (net.dcv > nominal * 1.3) { overvoltage(w, d, net, nominal); gridOk = false; }
            }
            if (gridOk && !ac && net.level != null && net.level.volts > (beam ? 1500 : 750) * 1.3) { overvoltage(w, d, net, beam ? 1500 : 750); gridOk = false; }
            long got = 0;
            // regen from trains braking on this network: clean energy straight off the line
            long[] pool = gridOk ? REGEN.get(net) : null;
            if (pool != null) {
                long take = Math.min(pool[0] - pool[1], need);
                if (take > 0) { pool[1] += take; got += take; }
            }
            // infra #38: a wayside energy store on this network discharges into the line (peak lopping, or the supply is dead)
            if (got < need && net != null) got += com.dogpound.railmap.infra.Ess.give(w, net, en.getKey(), n, need - got, gridOk);
            // power wired straight into the feeder from any mod's cables, then the railway grid (or the other way round)
            if (n.ownFirst && got < need) got += takeOwn(d, n, need - got);
            if (got < need && gridOk) {
                double v = net.level != null ? realVoltage(net, n, beam) : voltage(net, ac, beam);
                double q = quality(net, ac) * v;
                long drew = d.draw(net, (long) Math.ceil((need - got) / Math.max(0.05, q)));
                n.drawThis += drew;
                got += Math.round(drew * q);
            }
            if (!n.ownFirst && got < need) got += takeOwn(d, n, need - got);
            long delivered = Math.round(Math.min(got, need) * atTrain);
            total += delivered;
            if (delivered > 0) com.dogpound.railmap.infra.LoadSharing.record(w, en.getKey(), delivered, share == null ? 1 : share[ci]);
        }
        return Math.min(total, fe);
    }

    /** no capacitor bank: DC from a bare rectifier is rough (trains get ~70%), AC loses ~10% to poor power factor;
     *  a bank with too few steps switched in only fixes part of it */
    static double quality(GridData.Net net, boolean ac) {
        double base = ac ? 0.9 : 0.7;
        if (net == null || !net.capacitor) return net == null ? 1.0 : base;
        double need = Math.max(1, Math.min(4, Math.ceil(4 * (net.lastSecond / 20.0) / ((double) GridData.RATING * Math.max(1, net.transformers)))));
        return base + (1 - base) * Math.min(1, net.steps / need);
    }

    /** what the line voltage does to a train: tap position / load drop on AC, the rectifier's DC setting vs the line */
    static double voltage(GridData.Net net, boolean ac, boolean beam) {
        double v = Math.max(0.5, Math.min(1.1, net.volt));
        if (!ac && net.rectifier) v *= Math.min(1.0, net.dcv / (beam ? 1500.0 : 750.0));
        return v;
    }

    /** real network: the voltage at this feeder (per unit, after every cable's drop) and the line's own rating */
    static double realVoltage(GridData.Net net, GridData.Node feeder, boolean beam) {
        double v = Math.max(0.3, Math.min(1.1, feeder.pu));
        if (!net.level.ac) v *= Math.min(1.0, net.level.volts / (beam ? 1500.0 : 750.0));
        return v;
    }

    /** DC set far above what the line is built for: the rectifier's DC breaker trips on overvoltage */
    static void overvoltage(World w, GridData d, GridData.Net net, int nominal) {
        for (long p : net.nodes) {
            GridData.Node nd = d.nodes().get(p);
            if (nd.kind != GridKind.RECTIFIER || !nd.dcOn) continue;
            nd.dcOn = false;
            d.touch();
            BlockPos bp = BlockPos.fromLong(p);
            if (w.isBlockLoaded(bp) && w.getTileEntity(bp) instanceof TileGrid t) t.rectifierTripped("OVERVOLTAGE: " + nd.dcv + " V on a " + nominal + " V line");
            alarm(w, p, "§c⚡ Rectifier tripped: " + nd.dcv + " V DC on a line built for " + nominal + " V - turn DC VOLTS down, RESET, CLOSE");
        }
    }

    static void alarm(net.minecraft.world.World w, long at, String msg) {
        BlockPos p = BlockPos.fromLong(at);
        com.dogpound.railmap.alarm.AlarmManager.fromText(w, msg, p);
        for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities)
            if (pl.getDistanceSq(p) < 96 * 96) pl.sendMessage(new net.minecraft.util.text.TextComponentString(msg));
    }

    private static long takeOwn(GridData d, GridData.Node n, long fe) {
        long t = Math.min(n.energy, fe);
        if (t > 0) { n.energy -= t; d.markDirty(); }
        return t;
    }

    /** why a train here has no grid power (for the cab / Control Center) */
    public static String whyDead(World w, BlockPos at, boolean ac) {
        GridData d = GridData.get(w);
        String best = "No Track Feeder covers this section";
        for (java.util.Map.Entry<Long, GridData.Node> en : d.nodes().entrySet()) {
            if (en.getValue().kind != GridKind.FEEDER) continue;
            BlockPos p = BlockPos.fromLong(en.getKey());
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ();
            if (dx * dx + dz * dz > (double) en.getValue().range * en.getValue().range) continue;
            if (en.getValue().energy > 0) return "";             // wired straight from another mod's cables
            String why = GridData.why(d.netOf(p), ac);
            if (why.isEmpty()) return "";
            best = why;
        }
        return best;
    }
}
