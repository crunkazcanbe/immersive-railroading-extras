package com.dogpound.railmap.safety;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.*;

/**
 * Signalling batch 3, Part B: #14 signal battery cabinets, #16 sighting checker, #17 radio repeaters + coverage map,
 * #18 signal maintenance isolation.
 * The entry points SigBlocks / SigTicker / SigCommand / SigConsole / SigHooks call.
 */
final class SigB {
    private SigB() {}

    /** the blocks (SafetyBlock) this part adds */
    static List<Block> blocks() {
        return Arrays.asList(
                new SafetyBlock("signal_battery", TileSignalBattery::new, new double[]{1, 0, 2, 15, 16, 15},
                        "Signal power backup cabinet: battery string + charger, keeps signals lit through a power cut",
                        "Charger: 230 V AC grid cable touching it. Wrench-link up to 8 pieces of 12 V gear, or cover 16 / 32 m. 12 V = alarms"),
                new SafetyBlock("sighting_checker", TileSightingChecker::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Signal sighting checker: survey camera - sighting, obstruction, banner repeater spot",
                        "Wrench-link a signal, or PREV / NEXT through short ones. SHOW draws the sight line. 12 V"),
                new SafetyBlock("radio_repeater", TileRadioRepeater::new, new double[]{5, 0, 5, 11, 16, 11},
                        "Radio repeater: fills a dead zone, needs a donor base station above -95 dBm",
                        "A cell of its own (48 / 80 / 128 m). Donor level on its screen. 12 V"),
                new SafetyBlock("signal_isolation", TileIsolationBox::new, new double[]{2, 0, 9, 14, 16, 16},
                        "Signal maintenance isolation box: 4 slots, request > isolate > restoration tests > back in use",
                        "Wrench a signal onto a slot. The signaller grants and accepts back (Radio Dispatch Desk ANSWER). 12 V"));
    }

    static void registerTiles() {
        reg(TileSignalBattery.class, "signal_battery");
        reg(TileSightingChecker.class, "sighting_checker");
        reg(TileRadioRepeater.class, "radio_repeater");
        reg(TileIsolationBox.class, "signal_isolation");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }

    /** signals short of their sighting with a raised alarm, per dimension (memory: re-raised after a restart if still short) */
    private static final Map<Integer, Set<Long>> SHORT_ALARMED = new HashMap<>();

    /** once a second / every 5 ticks (server) */
    static void tick(World w) {
        if (w.getTotalWorldTime() % 200 != 11) return;                           // sighting alarms every 10 s
        Set<Long> was = SHORT_ALARMED.computeIfAbsent(w.provider.getDimension(), k -> new HashSet<>());
        Set<Long> now = new HashSet<>();
        AlarmManager am = AlarmManager.get(w);
        if (SigConfig.sightingAlarms)
            for (SignalSighting.Result r : SignalSighting.all(w).values()) {
                double s = SignalSighting.deficit(w, BlockPos.fromLong(r.pos));
                if (s <= 0) continue;
                now.add(r.pos);
                if (!was.contains(r.pos)) am.raise(w, "sight:" + r.pos, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR,
                        "Signal " + r.name + " sighting " + Math.round(s) + " m short (" + r.obstruction + ")" + (r.bannerAt != null ? " - fit a banner at " + Math.round(r.bannerAt[0]) + "," + Math.round(r.bannerAt[2]) : ""), BlockPos.fromLong(r.pos), true);
            }
        for (long p : was) if (!now.contains(p)) am.clear("sight:" + p);
        SHORT_ALARMED.put(w.provider.getDimension(), now);
    }

    static void fast(World w) { }

    // ---- hooks ---------------------------------------------------------------------------------------------------------
    static void holds(World w, Map<Long, String> hold) {
        for (TileIsolationBox b : SigTicker.tiles(w, TileIsolationBox.class))
            for (TileIsolationBox.Slot s : b.slot)
                if (TileIsolationBox.isolates(s)) hold.putIfAbsent(s.signal, "ISOLATED for maintenance by " + s.holder + " (" + TileIsolationBox.STATE[s.state] + ")");
    }

    static String routeRefusal(World w, BlockPos from, BlockPos to) {
        for (TileIsolationBox b : SigTicker.tiles(w, TileIsolationBox.class))
            for (TileIsolationBox.Slot s : b.slot) {
                if (!TileIsolationBox.isolates(s)) continue;
                if (s.signal == from.toLong()) return "SIGNAL ISOLATED: the entrance signal " + Geo.signalName(from) + " is out of use for maintenance (" + s.holder + ")";
                if (s.signal == to.toLong()) return "SIGNAL ISOLATED: the exit signal " + Geo.signalName(to) + " is out of use for maintenance (" + s.holder + ")";
            }
        return null;
    }

    static void radio(World w, SafetyData.TrainRec r) {
        if (r.radio != 0) Coverage.get(w).sample(w, r.x, r.z, r.radio, r.rssi);
    }

    // ---- command -------------------------------------------------------------------------------------------------------
    static String usage() { return "battery [test x y z]|sighting [all|show x y z|kmh x y z <n>]|radio <survey|deadzones|clear>|isolate|selftest b"; }

    static boolean command(ICommandSender s, World w, String[] a) {
        String sub = a[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "battery": {
                if (a.length >= 6 && a[2].equalsIgnoreCase("test")) {
                    BlockPos p = new BlockPos(n(a[3]), n(a[4]), n(a[5]));
                    say(s, w.getTileEntity(p) instanceof TileSignalBattery b ? b.startTest(s.getName()) : "§cNo battery cabinet there");
                    return true;
                }
                List<TileSignalBattery> l = SigTicker.tiles(w, TileSignalBattery.class);
                if (l.isEmpty()) say(s, "No signal battery cabinets loaded");
                for (TileSignalBattery b : l) say(s, "§e" + b.where() + "§r " + b.gearState());
                return true;
            }
            case "sighting": {
                String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "all";
                if (op.equals("kmh") && a.length >= 7) {
                    BlockPos p = new BlockPos(n(a[3]), n(a[4]), n(a[5]));
                    if (!(w.getTileEntity(p) instanceof TileSignalMast)) { say(s, "§cNo signal there"); return true; }
                    SafetyData d = SafetyData.get(w);
                    int k = n(a[6]);
                    if (k <= 0) d.sightKmhAt.remove(p.toLong()); else d.sightKmhAt.put(p.toLong(), Math.max(10, Math.min(300, k)));
                    d.markDirty();
                    say(s, "§aLine speed at " + p.getX() + "," + p.getZ() + ": " + SignalSighting.kmhAt(w, p) + " km/h - needs " + Math.round(SignalSighting.requiredAt(w, p)) + " m");
                    return true;
                }
                if (op.equals("show")) {
                    BlockPos p = a.length >= 6 ? new BlockPos(n(a[3]), n(a[4]), n(a[5])) : look(s);
                    if (p == null || !(w.getTileEntity(p) instanceof TileSignalMast m)) { say(s, "§cLook at a signal or give x y z"); return true; }
                    SignalSighting.Result r = SignalSighting.survey(w, m);
                    SignalSighting.store(w, r);
                    say(s, "§l" + r.name + "§r " + Math.round(SignalSighting.sightNow(w, r)) + " / " + Math.round(SignalSighting.requiredAt(w, p)) + " m · " + r.obstruction
                            + (r.obstructionPos != 0 ? " §c(block at " + Geo.xz(r.obstructionPos) + ")" : "")
                            + (r.bannerAt != null ? " §a· banner spot " + Math.round(r.bannerAt[0]) + " " + Math.round(r.bannerAt[1]) + " " + Math.round(r.bannerAt[2]) + " (" + Math.round(r.bannerAt[3]) + " m out)" : ""));
                    return true;
                }
                List<SignalSighting.Result> l = new ArrayList<>(SignalSighting.all(w).values());
                l.sort(Comparator.comparingDouble(r -> -SignalSighting.deficit(w, BlockPos.fromLong(r.pos))));
                int shown = 0;
                for (SignalSighting.Result r : l) {
                    double d = SignalSighting.deficit(w, BlockPos.fromLong(r.pos));
                    if (shown++ >= 15) break;
                    say(s, (d > 0 ? "§c" : "§a") + r.name + "§r " + Math.round(SignalSighting.sightNow(w, r)) + "/" + Math.round(SignalSighting.requiredAt(w, BlockPos.fromLong(r.pos))) + " m"
                            + (d > 0 ? " SHORT " + Math.round(d) + " m · " + r.obstruction + (r.bannerAt != null ? " · banner at " + Math.round(r.bannerAt[0]) + "," + Math.round(r.bannerAt[2]) : " · no banner spot") : ""));
                }
                if (l.isEmpty()) say(s, "No signals surveyed yet (about every 30 s)");
                return true;
            }
            case "radio": {
                String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "deadzones";
                Coverage c = Coverage.get(w);
                if (op.equals("survey")) { say(s, "§eDrive-test survey: " + c.survey(w) + " points predicted, " + c.deadZones().size() + " dead zone cell(s)"); return true; }
                if (op.equals("clear")) { c.cells.clear(); c.markDirty(); say(s, "§eCoverage map cleared"); return true; }
                List<Coverage.Cell> dz = c.deadZones();
                say(s, "§l" + c.cells.size() + "§r cells mapped, §c" + dz.size() + "§r dead");
                int sz = Coverage.size();
                for (int i = 0; i < Math.min(12, dz.size()); i++) {
                    Coverage.Cell x = dz.get(i);
                    say(s, " §c" + x.cx * sz + "," + x.cz * sz + "§r " + (x.n > 0 ? "measured mean " + x.mean() + " worst " + x.worst + " dBm, " + x.losses + " loss(es) in " + x.n : "predicted " + x.pred + " dBm"));
                }
                return true;
            }
            case "isolate": {
                int n = 0;
                for (TileIsolationBox b : SigTicker.tiles(w, TileIsolationBox.class))
                    for (int i = 0; i < TileIsolationBox.N; i++) {
                        TileIsolationBox.Slot x = b.slot[i];
                        if (x.signal == 0) continue;
                        n++;
                        say(s, "§e" + b.getPos().getX() + "," + b.getPos().getZ() + " slot " + (i + 1) + "§r signal " + Geo.xz(x.signal) + ": " + TileIsolationBox.STATE[x.state] + (x.holder.isEmpty() ? "" : " (" + x.holder + ")") + (x.why.isEmpty() ? "" : " §c" + x.why));
                    }
                if (n == 0) say(s, "No isolation box slots in use");
                return true;
            }
            case "selftest":
                if (a.length > 2 && !a[2].equalsIgnoreCase("b")) return false;
                for (String l : selfTest()) say(s, l);
                return a.length > 2;                                              // "selftest" alone: let the other parts run theirs too
            default: return false;
        }
    }

    /** proves the pure logic: battery maths, coverage cells, the isolation procedure */
    static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        int[] pf = new int[2];
        BiCheck c = (name, ok) -> { pf[ok ? 0 : 1]++; out.add((ok ? "§aPASS " : "§cFAIL ") + "§r" + name); };
        double cap = TileSignalBattery.capacity(65, 1);
        c.check("65 Ah = 780 Wh", Math.abs(cap - 780_000) < 1);
        c.check("float 13.6 V on charge", TileSignalBattery.volts(true, 0.3) == 13.6);
        c.check("12.7 V full / 11.5 V empty on battery", Math.abs(TileSignalBattery.volts(false, 1) - 12.7) < 1e-9 && Math.abs(TileSignalBattery.volts(false, 0) - 11.5) < 1e-9);
        double rt = TileSignalBattery.runtime(cap, 8 + 12 * 4);                   // 4 signals + the cabinet
        c.check("runtime 780 Wh at 56 W = " + Math.round(rt) + " s (~" + Math.round(rt * 72 / 3600) + " game h)", Math.abs(rt * 72 / 3600 - 780.0 / 56) < 0.01);
        c.check("deep discharge wears more than shallow, flat most", TileSignalBattery.wear(0.1, false) > TileSignalBattery.wear(0.8, false) && TileSignalBattery.wear(0, true) > TileSignalBattery.wear(0.1, false));
        long k = Coverage.key(-3, 7);
        c.check("cell key round trip", (int) (k >> 32) == -3 && (int) k == 7);
        Coverage.Cell cell = new Coverage.Cell();
        cell.n = 2; cell.sum = -170; cell.worst = -100; cell.losses = 0;
        c.check("cell mean -85, worst -100 below threshold = dead", cell.mean() == -85 && cell.dead());
        Coverage.Cell good = new Coverage.Cell();
        good.n = 3; good.sum = -240; good.worst = -85;
        c.check("cell all above -95 = alive", !good.dead());
        Coverage.Cell pred = new Coverage.Cell();
        pred.pred = -110;
        c.check("predicted -110 dBm cell = dead", pred.dead() && pred.level() == -110);
        int s = TileIsolationBox.NORMAL;
        s = TileIsolationBox.next(s, "key_isol", true);
        c.check("key refused before a request", s == -1);
        s = TileIsolationBox.NORMAL;
        int[] seq = {TileIsolationBox.next(s, "req", true)};
        c.check("request -> REQUESTED", seq[0] == TileIsolationBox.REQUESTED);
        s = TileIsolationBox.next(seq[0], "answered", true);
        c.check("answered -> GRANTED", s == TileIsolationBox.GRANTED);
        s = TileIsolationBox.next(s, "key_isol", true);
        c.check("key -> ISOLATED", s == TileIsolationBox.ISOLATED);
        c.check("key back refused while isolated", TileIsolationBox.next(s, "key_normal", true) == -1);
        s = TileIsolationBox.next(s, "restore", true);
        c.check("restore -> TESTING", s == TileIsolationBox.TESTING);
        c.check("failed test -> back to ISOLATED", TileIsolationBox.next(s, "test_fail", true) == TileIsolationBox.ISOLATED);
        s = TileIsolationBox.next(s, "tests_ok", true);
        c.check("tests ok -> AWAIT ACCEPT", s == TileIsolationBox.AWAIT_ACK);
        s = TileIsolationBox.next(s, "accepted", true);
        c.check("accepted -> READY", s == TileIsolationBox.READY);
        c.check("key back -> NORMAL", TileIsolationBox.next(s, "key_normal", true) == TileIsolationBox.NORMAL);
        c.check("no signaller: request -> GRANTED, tests -> READY", TileIsolationBox.next(0, "req", false) == TileIsolationBox.GRANTED && TileIsolationBox.next(TileIsolationBox.TESTING, "tests_ok", false) == TileIsolationBox.READY);
        out.add(0, "§lPart B self-test: " + pf[0] + " pass, " + pf[1] + " fail");
        return out;
    }

    private interface BiCheck { void check(String name, boolean ok); }

    // ---- console -------------------------------------------------------------------------------------------------------
    static void fill(World w, List<String[]> out) {
        int n = 0;
        for (TileSignalBattery b : SigTicker.tiles(w, TileSignalBattery.class)) if (n++ < 40) out.add(b.row());
        n = 0;
        for (TileIsolationBox b : SigTicker.tiles(w, TileIsolationBox.class))
            for (int i = 0; i < TileIsolationBox.N; i++) {
                TileIsolationBox.Slot s = b.slot[i];
                if (s.signal == 0 || n++ > 60) continue;
                out.add(new String[]{"b.iso", b.getPos().getX() + "," + b.getPos().getZ(), String.valueOf(i + 1), Geo.xz(s.signal), String.valueOf(s.state), TileIsolationBox.STATE[s.state], s.holder,
                        s.state >= TileIsolationBox.ISOLATED ? String.valueOf((w.getTotalWorldTime() - s.since) / 1200) : "", s.state == TileIsolationBox.TESTING ? TileIsolationBox.TEST[Math.min(3, s.step)] : "", s.why});
            }
        // radio map: cells near the masts / the worst first, capped for the packet
        Coverage c = Coverage.get(w);
        out.add(new String[]{"b.covh", String.valueOf(Coverage.size()), String.valueOf(c.cells.size()), String.valueOf(c.deadZones().size())});
        List<Coverage.Cell> cells = new ArrayList<>(c.cells.values());
        cells.sort(Comparator.comparingLong((Coverage.Cell x) -> -x.last));
        StringBuilder sb = new StringBuilder();
        int rows = 0;
        for (int i = 0; i < Math.min(1500, cells.size()); i++) {
            Coverage.Cell x = cells.get(i);
            sb.append(x.cx).append(',').append(x.cz).append(',').append(x.level()).append(',').append(x.dead() ? 1 : 0).append(';');
            if (sb.length() > 900) { out.add(new String[]{"b.cov", sb.toString()}); sb.setLength(0); if (++rows > 40) break; }
        }
        if (sb.length() > 0) out.add(new String[]{"b.cov", sb.toString()});
        for (long[] m : Coverage.mastMarks(w)) out.add(new String[]{"b.mast", String.valueOf(m[0]), String.valueOf(m[1]), String.valueOf(m[2]), String.valueOf(m[3]), String.valueOf(m[4])});
        List<Coverage.Cell> dz = c.deadZones();
        int sz = Coverage.size();
        for (int i = 0; i < Math.min(10, dz.size()); i++) {
            Coverage.Cell x = dz.get(i);
            out.add(new String[]{"b.dz", x.cx * sz + "," + x.cz * sz, x.n > 0 ? "measured mean " + x.mean() + ", worst " + x.worst + " dBm, " + x.losses + " loss(es) in " + x.n + " reports" : "predicted " + x.pred + " dBm"});
        }
    }

    /** ops 40-59: 40 battery test (arg = pos), 41 radio survey, 42 clear the coverage map */
    static String op(World w, int op, String arg, String who) {
        switch (op) {
            case 40: {
                try { BlockPos p = BlockPos.fromLong(Long.parseLong(arg)); return w.getTileEntity(p) instanceof TileSignalBattery b ? b.startTest(who) : "No cabinet"; }
                catch (NumberFormatException e) { return null; }
            }
            case 41: { Coverage c = Coverage.get(w); return "Survey: " + c.survey(w) + " points, " + c.deadZones().size() + " dead cell(s)"; }
            case 42: { Coverage c = Coverage.get(w); c.cells.clear(); c.markDirty(); return "Coverage map cleared"; }
            default: return null;
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------
    private static int n(String v) { try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return 0; } }
    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
    private static BlockPos look(ICommandSender s) {
        if (!(s instanceof EntityPlayer p)) return null;
        RayTraceResult hit = p.rayTrace(32, 1f);
        return hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit.getBlockPos() : null;
    }
}
