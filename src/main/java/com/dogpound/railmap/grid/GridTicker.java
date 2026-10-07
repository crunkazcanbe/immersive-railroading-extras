package com.dogpound.railmap.grid;

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
                    boolean fans = nd.cool == 2 || (nd.cool == 1 && nd.heat > 40);
                    double cooling = nd.cool == 0 ? 0.6 : fans ? 2.4 : 1.2;
                    double over = n.volt > 1.08 ? (n.volt - 1.08) * 60 : 0;                // over-voltage over-fluxes the core
                    nd.heat = (float) Math.max(0, Math.min(120, nd.heat + (load > rating ? (load / rating - 1) * 12 : -cooling) + over));
                    if (!nd.hot && nd.heat >= 100) { nd.hot = true; d.touch(); alarm(e.world, p, "§c⚠ Transformer OVERHEATED and tripped (load " + (int) load + " FE/t, rating " + (int) rating + ")"); }
                    if (nd.hot && nd.heat < 40) { nd.hot = false; d.touch(); alarm(e.world, p, "§a✔ Transformer cooled down - back in service"); }
                }
            }
            // lightning on a network WITH an arrester: the arrester takes it and its surge counter clicks over
            if (storm && n.arrester && e.world.rand.nextInt(90) == 0)
                for (long p : n.nodes) { GridData.Node nd = d.nodes().get(p); if (nd.kind == GridKind.ARRESTER) { nd.surges++; d.markDirty(); } }
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
                if (e.world.isBlockLoaded(bp) && e.world.getTileEntity(bp) instanceof TileGrid t) t.checkOverload(n);
                else if (nd.breaker == 0 && n.lastSecond > (long) TileGrid.relayRating(d, bp, nd.rating) * 20) { nd.breaker = 2; d.touch(); }
            }
        }
        d.balance();
    }

    /**
     * Power for a train at {@code at}: a live Track Feeder of the right kind within its section length.
     * ac = overhead wire, otherwise DC (monorail beam / maglev guideway / third rail). Returns FE actually supplied.
     */
    public static long supply(World w, BlockPos at, boolean ac, boolean beam, long fe) {
        GridData d = GridData.get(w);
        if (d.nodes().isEmpty()) return -1;                       // no grid built here: caller falls back to substations
        for (java.util.Map.Entry<Long, GridData.Node> en : d.nodes().entrySet()) {
            GridData.Node n = en.getValue();
            if (n.kind != GridKind.FEEDER) continue;
            BlockPos p = BlockPos.fromLong(en.getKey());
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ(), dy = p.getY() - at.getY();
            if (dx * dx + dz * dz > (double) n.range * n.range || Math.abs(dy) > 24) continue;
            switch (n.feeds) {
                case "Wire": if (!ac) continue; break;
                case "Beam": if (ac || !beam) continue; break;
                case "Third rail": if (ac || beam) continue; break;
                default: break;
            }
            // power wired straight into the feeder from any mod's cables, then the railway grid (or the other way round)
            GridData.Net net = d.netOf(p);
            boolean gridOk = net != null && GridData.why(net, ac).isEmpty();
            long got = 0;
            if (n.ownFirst) got += takeOwn(d, n, fe);
            if (gridOk && !ac && net.level == null && net.rectifier) {
                int nominal = beam ? 1500 : 750;                          // monorail / maglev beam 1500 V, third rail 750 V
                if (net.dcv > nominal * 1.3) { overvoltage(w, d, net, nominal); gridOk = false; }
            }
            if (gridOk && !ac && net.level != null && net.level.volts > (beam ? 1500 : 750) * 1.3) { overvoltage(w, d, net, beam ? 1500 : 750); gridOk = false; }
            if (got < fe && gridOk) {
                double v = net.level != null ? realVoltage(net, n, beam) : voltage(net, ac, beam);
                long drew = d.draw(net, fe - got);
                n.drawThis += drew;
                got += Math.round(drew * quality(net, ac) * v);
            }
            if (!n.ownFirst && got < fe) got += takeOwn(d, n, fe - got);
            // voltage drop along the line: the far end of a long section gets less
            double reach = Math.sqrt(dx * dx + dz * dz) / Math.max(1, n.range);
            got = Math.round(got * (1 - 0.4 * reach * reach));
            if (got > 0 || gridOk) return got;
        }
        return 0;
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
