package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.Map;

/**
 * {@code /irextras safety ...} - the signalling-safety systems from chat, for testing and for players without a
 * console nearby. Every sub-command uses the same code paths as the blocks and the console.
 */
public final class SafetyCommand {
    private SafetyCommand() {}

    public static final String USAGE = "safety <status|trains|headcode <train> <code>|integrity <train> confirm|radio <ack id|call|send <train|all> <0-7>|release <train>|log>"
            + "|failsafe [name option]|diag|selftest|history [x y z]|sighting [x y z]|axle|lab <status|inject kind x y z|clear>|set <sightkmh|sightsec|overlap|selftestmin> n>";

    public static void run(ICommandSender s, World w, String[] a) {
        SafetyData d = SafetyData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status": {
                int live = 0, lost = 0, div = 0;
                for (SafetyData.TrainRec r : d.trains.values()) if (!r.gone) { live++; if (r.radio == 2) lost++; if (r.integrity == 2) div++; }
                say(s, "§lSignalling safety§r: " + live + " trains tracked (" + lost + " radio lost, " + div + " divided) · " + SafetyHolds.all(w).size() + " signal(s) held · "
                        + SafetyTicker.evaluators(w).size() + " axle evaluator(s) · " + SafetyTicker.radioMasts(w).size() + " radio mast(s) · lab " + (d.testMode ? "§6TEST MODE§r (" + d.faults.size() + " faults)" : "normal"));
                for (Map.Entry<Long, String> e : SafetyHolds.all(w).entrySet()) say(s, " §c" + Geo.xz(e.getKey()) + "§r held: " + e.getValue());
                return;
            }
            case "trains":
                for (SafetyData.TrainRec r : d.trains.values()) {
                    if (r.gone) continue;
                    say(s, "§e" + r.headcode + "§r RID " + r.radioId + " " + r.names + " · " + r.cars + " veh " + r.axles + " ax " + Math.round(r.lengthM) + " m · " + Math.round(r.x) + "," + Math.round(r.z)
                            + " " + Math.round(Math.abs(r.speed)) + " km/h · berth " + (r.berth.isEmpty() ? "-" : r.berth) + (r.dest.isEmpty() ? "" : " · to " + r.dest) + (r.route.isEmpty() ? "" : " · route " + r.route)
                            + " · radio " + (r.radio == 1 ? r.mast + " " + r.rssi + " dBm" : r.radio == 2 ? "§cLOST§r" : "none") + " · " + (r.integrity == 0 ? "intact" : "§c" + r.integrityWhy + "§r"));
                }
                if (d.trains.isEmpty()) say(s, "No trains tracked yet");
                return;
            case "headcode": {
                if (a.length < 4) { say(s, "/irextras safety headcode <train> <new code>"); return; }
                SafetyData.TrainRec r = d.train(a[2]);
                if (r == null) { say(s, "No train " + a[2]); return; }
                String old = r.headcode;
                r.headcode = a[3].toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
                if (r.headcode.length() > 6) r.headcode = r.headcode.substring(0, 6);
                d.markDirty();
                d.log(w, "TRACKING", r.headcode, "headcode " + old + " re-described as " + r.headcode + " by " + s.getName(), 2, 3);
                say(s, "§a" + old + " is now " + r.headcode);
                return;
            }
            case "integrity": {
                SafetyData.TrainRec r = a.length > 2 ? d.train(a[2]) : null;
                if (r == null) { say(s, "/irextras safety integrity <train> confirm"); return; }
                if (a.length > 3 && a[3].equalsIgnoreCase("confirm")) { ConsistWatch.restore(w, d, r, "confirmed by " + s.getName() + " (command)"); say(s, "§aIntegrity of " + r.headcode + " confirmed"); }
                else say(s, r.headcode + ": " + (r.integrity == 0 ? "intact" : r.integrityWhy));
                return;
            }
            case "radio": radio(s, w, d, a); return;
            case "failsafe": {
                if (a.length >= 4) {
                    FailSafe f = FailSafe.byName(a[2]);
                    if (f == null) { say(s, "No subsystem " + a[2]); return; }
                    int n;
                    try { n = Integer.parseInt(a[3]); } catch (NumberFormatException e) { say(s, "Option is a number 0-" + (f.options.length - 1)); return; }
                    say(s, setFailSafe(w, f, n, s.getName()));
                    return;
                }
                for (FailSafe f : FailSafe.values())
                    say(s, (f.critical ? "§c" : "§7") + f.name() + "§r " + f.label + ": §e" + FailSafe.mode(w, f) + " " + f.options[FailSafe.mode(w, f)] + "§7 (" + f.options.length + " option" + (f.options.length > 1 ? "s" : "") + ")");
                return;
            }
            case "diag": {
                if (d.decisions.isEmpty()) { say(s, "No routes requested since the server started"); return; }
                int n = a.length > 2 ? Math.max(1, Math.min(5, parse(a[2], 1))) : 1;
                for (int i = 0; i < Math.min(n, d.decisions.size()); i++) {
                    SafetyData.Decision dec = d.decisions.get(i);
                    say(s, (dec.granted ? "§aGRANTED " : "§cREFUSED ") + "§r" + dec.route + " §7" + Recorder.clock(w, dec.tick));
                    for (String l : dec.lines) say(s, "  " + (l.startsWith("+") ? "§a" : l.startsWith("-") ? "§c" : "§7") + l);
                }
                return;
            }
            case "selftest": { int[] r = SelfTest.run(w, "command, " + s.getName()); say(s, "§eSelf-test: §a" + r[0] + " pass §c" + r[1] + " fail §6" + r[2] + " intermittent"); return; }
            case "history": {
                BlockPos p = target(s, w, a, 2);
                if (p == null) { say(s, "Look at a piece of gear or give x y z"); return; }
                SafetyData.Gear g = d.gear.get(p.toLong());
                if (g == null) { say(s, "No history for " + p.getX() + "," + p.getY() + "," + p.getZ() + " yet (run a self-test)"); return; }
                say(s, "§l" + g.name + "§r (" + g.kind + ") " + g.pass + " pass / " + g.fail + " fail" + (g.intermittent ? " §6INTERMITTENT" : "") + " · last: " + g.last);
                for (String h : g.history.subList(0, Math.min(12, g.history.size()))) { String[] p2 = h.split("\\|", 2); say(s, " §7" + Recorder.clock(w, Long.parseLong(p2[0])) + "§r " + p2[1]); }
                return;
            }
            case "sighting": {
                BlockPos p = target(s, w, a, 2);
                TileSignalMast m = p != null && w.getTileEntity(p) instanceof TileSignalMast mm ? mm : nearestMast(s, w);
                if (m == null) { say(s, "No signal there / nearby"); return; }
                SignalSighting.Result r = SignalSighting.survey(w, m);
                SignalSighting.store(w, r);
                say(s, "§l" + r.name + "§r sighting " + Math.round(SignalSighting.sightNow(w, r)) + " m (clear-weather " + Math.round(r.sight) + " m) of " + Math.round(SignalSighting.required(w)) + " m needed · "
                        + r.obstruction + (r.banner > 0 ? " · banner " + Math.round(r.banner) + " m" : "") + " · visibility " + Math.round(SignalSighting.visibility(w, m.getPos()) * 100) + "%"
                        + (SignalSighting.deficit(w, m.getPos()) > 0 ? " §c-> " + Math.round(SignalSighting.deficit(w, m.getPos())) + " m short: trains brake to " + Math.round(SignalSighting.CAUTION_KMH) + " km/h early (fit a banner repeater)" : " §a-> OK"));
                return;
            }
            case "axle":
                for (TileAxleEvaluator e : SafetyTicker.evaluators(w))
                    for (int i = 0; i < TileAxleEvaluator.N; i++) if (e.sec[i].state != 4) say(s, e.sectionLine(i) + " · " + e.sec[i].heads.size() + " heads · in " + e.sec[i].inTotal + " out " + e.sec[i].outTotal);
                if (SafetyTicker.evaluators(w).isEmpty()) say(s, "No axle counter evaluators loaded");
                return;
            case "lab": {
                String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "status";
                if (op.equals("clear")) { FaultLab.clearAll(w, "command by " + s.getName()); say(s, "§aAll faults cleared"); return; }
                if (op.equals("inject") && a.length >= 7) {
                    FaultLab.Kind k = FaultLab.Kind.byName(a[3]);
                    if (k == null) { say(s, "Kinds: TC_DROP LAMP POINT_STUCK FALSE_OCC RADIO_CUT AXLE_HEAD POWER_CUT"); return; }
                    say(s, FaultLab.inject(w, k, new BlockPos(parse(a[4], 0), parse(a[5], 0), parse(a[6], 0)), s.getName()));
                    return;
                }
                say(s, "Lab: " + (d.testMode ? "§6TEST MODE§r at " + Geo.xz(d.labPos) + " zone " + d.labRadius + " m" : "normal") + " · " + d.faults.size() + " fault(s)");
                for (SafetyData.Fault f : d.faults) say(s, " " + f.what);
                if (d.reportAt > 0) say(s, " last sequence: " + d.reportPass + " pass, " + d.reportFail + " fail");
                return;
            }
            case "set": {
                if (a.length < 4) { say(s, "set sightkmh|sightsec|overlap|selftestmin <n>"); return; }
                int n = parse(a[3], -1);
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "sightkmh": d.sightKmh = Math.max(10, Math.min(300, n)); break;
                    case "sightsec": d.sightSeconds = Math.max(1, Math.min(20, n)); break;
                    case "overlap": d.overlap = Math.max(0, Math.min(400, n)); break;
                    case "selftestmin": d.selfTestMinutes = Math.max(0, Math.min(240, n)); break;
                    default: say(s, "Unknown setting"); return;
                }
                d.markDirty();
                SignalSighting.clear();
                say(s, "§aSet. Sighting " + d.sightKmh + " km/h x " + d.sightSeconds + " s = " + Math.round(SignalSighting.required(w)) + " m · overlap " + d.overlap + " m · self-test every " + d.selfTestMinutes + " min");
                return;
            }
            default: say(s, "/irextras " + USAGE);
        }
    }

    public static String setFailSafe(World w, FailSafe f, int n, String who) {
        if (n < 0 || n >= f.options.length) return "§c" + f.label + " has options 0-" + (f.options.length - 1);
        SafetyData d = SafetyData.get(w);
        d.failsafe[f.ordinal()] = n;
        d.markDirty();
        Recorder.event(w, "FAIL-SAFE " + f.name() + " set to '" + f.options[n] + "' by " + who);
        return "§a" + f.label + ": " + f.options[n] + (n != 0 ? " §6(not the default safe state)" : "");
    }

    private static void radio(ICommandSender s, World w, SafetyData d, String[] a) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "log";
        EntityPlayer p = s instanceof EntityPlayer ep ? ep : null;
        switch (op) {
            case "ack": {
                SafetyData.Comm c = a.length > 3 ? d.comm(parse(a[3], -1)) : null;
                if (c == null) { say(s, "No such message"); return; }
                SafetyData.TrainRec r = d.trains.get(c.train);
                if (r != null && p != null && !ConsistWatch.riders(w, r).contains(p) && !p.canUseCommand(2, "irextras")) { say(s, "§cOnly the crew of " + r.headcode + " can acknowledge that"); return; }
                say(s, RadioNet.acknowledge(w, c, s.getName(), true));
                return;
            }
            case "call": {
                SafetyData.TrainRec mine = null;
                if (p != null) for (SafetyData.TrainRec r : d.trains.values()) if (!r.gone && ConsistWatch.riders(w, r).contains(p)) mine = r;
                if (mine == null) { say(s, "§cYou're not on a tracked train"); return; }
                if (mine.radio != 1) { say(s, "§cNo radio contact - use a lineside telephone"); return; }
                SafetyData.Comm c = d.log(w, mine.headcode + " (RID " + mine.radioId + ")", "DISPATCH", "RAILWAY EMERGENCY CALL from the driver " + s.getName(), 1, 0);
                c.pos = new BlockPos(mine.x, mine.y, mine.z).toLong();
                say(s, "§6Emergency call made - the signaller's desk is ringing");
                return;
            }
            case "send": {
                if (a.length < 5) { say(s, "radio send <train|all> <0-7>: " + String.join(" / ", RadioNet.MSGS)); return; }
                int m = Math.max(0, Math.min(RadioNet.MSGS.length - 1, parse(a[4], 0)));
                if (a[3].equalsIgnoreCase("all")) {
                    int n = 0;
                    for (SafetyData.TrainRec r : d.trains.values()) if (!r.gone && r.radio == 1 && RadioNet.send(w, "DISPATCH (" + s.getName() + ")", r, m).startsWith("SENT")) n++;
                    say(s, "§eSent to " + n + " train(s): " + RadioNet.MSGS[m]);
                    return;
                }
                SafetyData.TrainRec r = d.train(a[3]);
                say(s, r == null ? "No train " + a[3] : RadioNet.send(w, "DISPATCH (" + s.getName() + ")", r, m));
                return;
            }
            case "release": {
                SafetyData.TrainRec r = a.length > 3 ? d.train(a[3]) : null;
                if (r == null) { say(s, "radio release <train>"); return; }
                RadioNet.release(w, r);
                d.log(w, "DISPATCH (" + s.getName() + ")", r.headcode, "authority by telephone: train control brake / hold released", 2, 3);
                say(s, "§a" + r.headcode + " released");
                return;
            }
            default:
                for (int i = 0; i < Math.min(12, d.comms.size()); i++) {
                    SafetyData.Comm c = d.comms.get(i);
                    say(s, "§7#" + c.id + " " + Recorder.clock(w, c.tick) + "§r " + c.from + " > " + c.to + ": " + c.text + (c.ack == 0 ? " §6(waiting)" : c.ack == 1 ? " §a(ack " + c.ackBy + ")" : c.ack == 2 ? " §c(no contact)" : ""));
                }
        }
    }

    private static BlockPos target(ICommandSender s, World w, String[] a, int at) {
        if (a.length >= at + 3) return new BlockPos(parse(a[at], 0), parse(a[at + 1], 0), parse(a[at + 2], 0));
        if (s instanceof EntityPlayer p) {
            RayTraceResult hit = p.rayTrace(16, 1f);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) return hit.getBlockPos();
        }
        return null;
    }

    private static TileSignalMast nearestMast(ICommandSender s, World w) {
        TileSignalMast best = null;
        double bd = 48 * 48;
        for (TileSignalMast m : SignalRegistry.masts(w.provider.getDimension())) {
            double dd = m.getPos().distanceSq(s.getPosition());
            if (dd < bd) { bd = dd; best = m; }
        }
        return best;
    }

    private static int parse(String v, int def) { try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; } }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
