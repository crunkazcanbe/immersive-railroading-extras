package com.dogpound.railmap.grid;

import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Insulation and earth faults (her ChatGPT list #35). Insulation ages with heat (it halves its life every 8 °C over its
 * rating, like real XLPE / paper-oil), soaks up water when the plant is flooded, and once it is weakened rain gets in.
 * Below 25 % it breaks down to earth. What that does depends on the system:
 *   AC, solidly earthed:   a big earth-fault current, close to a short circuit - overcurrent protection sees it.
 *   AC, resistance earthed (Earthing Switch with a 400 A NER): only 400 A flows - only 50N/51N earth-fault protection
 *                          sees it; with none the fault stands, the arc keeps burning the cable until it burns through.
 *   DC (traction, 12 V):   the system floats: the FIRST fault only leaks (an earth-leakage / insulation monitor alarms),
 *                          a SECOND fault on the other pole is a short circuit through earth.
 * Earth current never passes a transformer (delta windings block it), so upstream relays only see its phase share.
 */
public final class Insulation {
    private Insulation() {}

    /** insulation resistance shown by a megger test */
    public static String megohms(GridData.Node n) {
        if (n.efault != 0) return "0.00 MΩ - BROKEN DOWN";
        double r = Math.pow(10, 4 * n.insul - 1);
        return r >= 1000 ? ">1000 MΩ" : r >= 1 ? String.format(Locale.ROOT, "%.0f MΩ", r) : String.format(Locale.ROOT, "%.2f MΩ", r);
    }

    /** megger pass mark: 1 MΩ per kV + 1 MΩ */
    public static boolean passes(GridData.Node n, double volts) { return n.efault == 0 && Math.pow(10, 4 * n.insul - 1) >= volts / 1000 + 1; }

    /** NER rating of the network's earthing (0 = solidly earthed) */
    static int ner(GridData d, GridData.Net net) {
        for (long p : net.nodes) { GridData.Node n = d.nodes().get(p); if (n != null && n.kind == GridKind.EARTHING && n.earthOn) return n.get("ner", 0); }
        return 0;
    }

    /** once a second, after the solve: age every node, start faults, make faults do what they do */
    static void tick(World w, GridData d) {
        boolean ageNow = (w.getTotalWorldTime() / 20) % 10 == 0;               // ageing is slow: work it out every 10 s (x10)
        for (GridData.Net net : d.nets()) {
            net.efAmps = 0;
            boolean live = net.problem.isEmpty() && net.level != null;
            Set<Integer> poles = new HashSet<>();
            long faultAt = Long.MIN_VALUE;
            for (long p : net.nodes) {
                GridData.Node n = d.nodes().get(p);
                if (n == null || n.kind.control()) continue;
                if (live && ageNow) age(w, d, p, n, net);
                if (n.efault != 0) { poles.add((int) n.efault); if (faultAt == Long.MIN_VALUE) faultAt = p; }
            }
            if (live) {                                                            // maintenance earths closed on a live section: a dead short
                long me = Long.MIN_VALUE;
                for (long p : net.nodes) { GridData.Node n = d.nodes().get(p); if (n != null && n.kind == GridKind.EARTHING && n.earthOn && n.get("maint", 0) == 1) { me = p; break; } }
                if (me != Long.MIN_VALUE && net.isc > 0) {
                    BlockPos mp = BlockPos.fromLong(me);
                    if (!Protection.clear(w, d, net, me, net.isc, false, "LIVE SECTION EARTHED - maintenance earth at " + mp.getX() + " " + mp.getY() + " " + mp.getZ() + " closed on live plant"))
                        Protection.standing(w, "ef:" + net.id + ":" + me, com.dogpound.railmap.alarm.AlarmManager.Cat.FAULT, com.dogpound.railmap.alarm.AlarmManager.Sev.CRITICAL,
                                "MAINTENANCE EARTH CLOSED ON LIVE PLANT at " + mp.getX() + " " + mp.getY() + " " + mp.getZ() + " and nothing tripped", mp);
                    net.efAmps = net.isc;
                    continue;
                }
            }
            if (!live || faultAt == Long.MIN_VALUE) continue;
            BlockPos fp = BlockPos.fromLong(faultAt);
            String where = d.nodes().get(faultAt).kind.label.toLowerCase(Locale.ROOT) + " at " + fp.getX() + " " + fp.getY() + " " + fp.getZ();
            if (net.level.ac) {
                int ner = ner(d, net);
                double ief = ner > 0 ? Math.min(ner, net.isc) : 0.9 * net.isc;
                if (Protection.clear(w, d, net, faultAt, ief, true, "earth fault (" + where + ")")) continue;
                net.efAmps = ief;                                                 // nothing saw it: it keeps arcing
                Protection.standing(w, "ef:" + net.id + ":" + faultAt, com.dogpound.railmap.alarm.AlarmManager.Cat.FAULT,
                        com.dogpound.railmap.alarm.AlarmManager.Sev.CRITICAL, String.format(Locale.ROOT, "STANDING EARTH FAULT %s on %s: %.0f A to earth and NO protection sees it - fit an IED with 50N", where, net.level.label, ief), fp);
            } else if (poles.size() >= 2) {
                Protection.clear(w, d, net, faultAt, net.isc, false, "double earth fault, + and - poles to earth (" + where + ")");
            } else {
                net.efAmps = -1;                                                   // first DC fault: leakage only
                Protection.standing(w, "dcef:" + net.id, com.dogpound.railmap.alarm.AlarmManager.Cat.FAULT, com.dogpound.railmap.alarm.AlarmManager.Sev.MAJOR,
                        String.format(Locale.ROOT, "DC EARTH FAULT (%s pole) %s on %s - first fault, still running: find it before a second one shorts the system", poles.contains(1) ? "+" : "-", where, net.level.label), fp);
            }
        }
        sweep(w, d);
    }

    /** standing earth-fault alarms clear when the faulted part is de-energised or replaced */
    static void sweep(World w, GridData d) {
        com.dogpound.railmap.alarm.AlarmManager am = com.dogpound.railmap.alarm.AlarmManager.get(w);
        java.util.List<String> gone = new java.util.ArrayList<>();
        for (com.dogpound.railmap.alarm.AlarmManager.Alarm a : am.all()) {
            if (!a.active || !(a.key.startsWith("ef:") || a.key.startsWith("dcef:"))) continue;
            boolean still = false;
            for (GridData.Net net : d.nets()) if ((a.key.equals("dcef:" + net.id) && net.efAmps < 0) || (a.key.startsWith("ef:" + net.id + ":") && net.efAmps > 0)) still = true;
            if (!still) gone.add(a.key);
        }
        for (String k : gone) am.clear(k);
    }

    private static void age(World w, GridData d, long pos, GridData.Node n, GridData.Net net) {
        if (n.efault != 0) return;
        BlockPos p = BlockPos.fromLong(pos);
        double rate = 0;
        if (n.kind == GridKind.CABLE) {
            double over = n.temp - n.cable().ins.maxTemp;
            rate += 2e-7 * Math.pow(2, over / 8);                                   // thermal ageing (Montsinger: life halves every 8 °C)
        } else if (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION) {
            rate += n.heat > 80 ? 1e-5 * Math.pow(2, (n.heat - 80) / 6) : 0;       // paper insulation cooks above 80 °C rise
        }
        if (w.isBlockLoaded(p)) {
            boolean flooded = false;
            for (EnumFacing f : EnumFacing.values()) if (w.getBlockState(p.offset(f)).getMaterial() == Material.WATER) { flooded = true; break; }
            if (flooded) rate += 1e-4;                                              // under water: about 2 h to break down
            else if (n.insul < 0.8 && w.isRainingAt(p.up())) rate += 2e-5;         // rain gets into weakened insulation
        }
        if (rate <= 0) return;
        float before = n.insul;
        n.insul = (float) Math.max(0, n.insul - rate * 10);
        d.markDirty();
        if (before >= 0.5f && n.insul < 0.5f) {
            History.log(w, p, "INSUL", "insulation down to 50% (" + megohms(n) + ") - plan a replacement");
            GridTicker.alarm(w, pos, String.format(Locale.ROOT, "§e⚠ Insulation deteriorating: %s at %d %d %d down to %s", n.kind.label, p.getX(), p.getY(), p.getZ(), megohms(n)));
        }
        if (n.insul < 0.25f) {
            n.efault = (byte) (net.level != null && !net.level.ac && w.rand.nextBoolean() ? 2 : 1);
            History.log(w, p, "FAULT", "insulation BROKE DOWN to earth" + (net.level != null && !net.level.ac ? (n.efault == 1 ? " (+ pole)" : " (- pole)") : ""));
            d.touch();
        }
    }

    /** a repaired / overhauled machine gets new insulation */
    public static void renew(GridData.Node n) { n.insul = 1; n.efault = 0; }
}
