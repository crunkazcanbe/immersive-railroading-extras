package com.dogpound.railmap.track;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.track.TrackData.Metric;
import com.dogpound.railmap.track.TrackData.Seg;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/** /irextras track ... - inspection and test subcommands for the track infrastructure (her list #13-18). */
public final class TrackCommand {
    private TrackCommand() {}

    public static final String USAGE = "track <here|list|tsr|wear <percent> [radius]|fix [radius]|points|structures|tunnels|crossings|lx|surveys|export|standard [set <std> [radius]|selftest]|diamond|single|buffer|retarder|table [go <road>|selftest]|bridge [open|close|selftest]>";

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    public static void run(ICommandSender s, World w, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "here";
        BlockPos at = s.getPosition();
        TrackData d = TrackData.get(w);
        switch (sub) {
            case "here" -> {
                TileRail r = Rails.pieceAt(w, at.getX() + 0.5, at.getY() - 0.2, at.getZ() + 0.5);
                if (r == null) { say(s, "§7Stand on IR track"); return; }
                BlockPos k = Rails.pos(r);
                Seg g = d.peek(k);
                if (g == null) { say(s, "§aTrack at " + Rails.xz(k) + " is as new (nothing has run over it yet)"); return; }
                say(s, "§b" + Rails.xz(k) + " TQI " + g.tqi() + " band " + g.band() + (g.tsr() > 0 ? " §cTSR " + g.tsr() : "") + " §7" + Math.round(g.tonnes) + " t, " + g.maxKmh + " km/h max");
                TrackData.Standard st = g.standard();
                say(s, String.format(Locale.ROOT, "§7Built to §f%s§7: axle %.1f t, line %d km/h, %s/m · %d overloads, %d overspeeds", st.label, st.axleT, st.lineKmh, com.dogpound.railmap.economy.Coins.fmt(st.costPerM), g.overloads, g.overspeeds));
                say(s, TrackCondition.report(g, true));
            }
            case "list" -> {
                List<Map.Entry<Long, Seg>> l = new ArrayList<>(d.segs.entrySet());
                l.sort(Comparator.comparingInt(e -> e.getValue().tqi()));
                say(s, "§b" + l.size() + " pieces of track on record; worst first:");
                for (int i = 0; i < Math.min(10, l.size()); i++) { Seg g = l.get(i).getValue(); say(s, "§f" + Rails.xz(BlockPos.fromLong(l.get(i).getKey())) + " §7TQI " + g.tqi() + " " + g.worst().label + " " + TrackCondition.fmt(g.get(g.worst())) + (g.tsr() > 0 ? " §cTSR " + g.tsr() : "")); }
            }
            case "tsr" -> {
                int n = 0;
                for (Map.Entry<Long, Seg> e : d.segs.entrySet()) if (e.getValue().tsr() > 0 && n++ < 20) say(s, "§c TSR " + e.getValue().tsr() + " km/h §f" + Rails.xz(BlockPos.fromLong(e.getKey())) + " §7" + TrackHooks.tsrWhy(w, BlockPos.fromLong(e.getKey())));
                for (TileStructureBoard b : TrackHooks.live(w, TileStructureBoard.class)) if (b.limit() > 0) { n++; say(s, "§c TSR " + b.limit() + " km/h §7" + b.limitWhy()); }
                if (n == 0) say(s, "§aNo speed restrictions in force");
            }
            case "wear", "fix" -> {
                double pct = sub.equals("fix") ? -1000 : a.length > 2 ? parse(a[2], 50) : 50;
                int rad = (int) (a.length > (sub.equals("fix") ? 2 : 3) ? parse(a[sub.equals("fix") ? 2 : 3], 16) : 16);
                int n = 0;
                Set<Long> keys = new HashSet<>();
                for (int dx = -rad; dx <= rad; dx += 1) for (int dz = -rad; dz <= rad; dz += 1) for (int dy = -3; dy <= 3; dy++) {
                    TileRail r = Rails.pieceAt(w, at.getX() + dx + 0.5, at.getY() + dy + 0.5, at.getZ() + dz + 0.5);
                    if (r != null) keys.add(Rails.pos(r).toLong());
                }
                for (long k : keys) {
                    Seg g = d.seg(BlockPos.fromLong(k));
                    for (Metric m : Metric.values()) g.set(m, pct <= -1000 ? m.fresh : Math.max(m.fresh * 0.5, g.get(m) + (m.ial - m.fresh) * pct / 100.0));
                    n++;
                }
                d.markDirty();
                say(s, sub.equals("fix") ? "§aRenewed " + n + " pieces of track" : "§eWore " + n + " pieces of track by " + pct + "% of the way to immediate action");
            }
            case "points", "structures", "tunnels", "crossings" -> {
                String tag = switch (sub) { case "points" -> "PM"; case "structures" -> "ST"; case "tunnels" -> "TN"; default -> "LX"; };
                int n = 0;
                for (String[] r : d.assets.values()) if (r.length > 0 && r[0].equals(tag)) { n++; say(s, "§f" + String.join(" §8|§7 ", Arrays.copyOfRange(r, 1, r.length))); }
                if (n == 0) say(s, "§7None registered");
            }
            case "lx" -> { for (TileCrossingPredictor p : TrackHooks.live(w, TileCrossingPredictor.class)) for (String l : p.report()) say(s, l); }
            case "surveys" -> {
                if (d.surveys.isEmpty()) say(s, "§7No surveyed routes yet (Surveyor's instrument)");
                for (Map.Entry<String, net.minecraft.nbt.NBTTagCompound> e : d.surveys.entrySet()) {
                    List<int[]> pts = Survey.points(e.getValue());
                    double[] ew = Survey.earthworks(e.getValue());
                    say(s, String.format(Locale.ROOT, "§b%s§7: %d waypoints, %.0f m, cut %.0f m3, fill %.0f m3", e.getKey(), pts.size(), Survey.length(pts), ew[0], ew[1]));
                }
            }
            case "export" -> {
                String f = Survey.exportTrack(w);
                for (Map.Entry<String, net.minecraft.nbt.NBTTagCompound> e : d.surveys.entrySet()) Survey.export(w, e.getKey(), e.getValue());
                say(s, f == null ? "§cExport failed" : "§aExported track condition to " + f + " and " + d.surveys.size() + " surveyed route(s) to irextras/survey/ (GeoJSON, x/z/y)");
            }
            case "standard", "standards" -> standards(s, w, a, at, d);   // batch-3 #10
            case "diamond", "diamonds" -> {                                // batch-3 #6
                if (a.length > 2 && a[2].equalsIgnoreCase("selftest")) { for (String l : com.dogpound.railmap.auto.Diamonds.selftest()) say(s, l); return; }
                for (String l : com.dogpound.railmap.auto.Diamonds.report(w)) say(s, l);
                for (TilePointMachine m : TrackHooks.live(w, TilePointMachine.class)) if (m.diamond()) say(s, "§b" + m.id() + " §7movable-point diamond at " + Rails.xz(m.switchPos()) + " · " + m.gearState());
            }
            case "single", "gauntlet" -> {                                // batch-3 #7
                if (a.length > 2 && a[2].equalsIgnoreCase("selftest")) { singleSelftest(s); return; }
                List<TileSingleLine> l = TrackHooks.live(w, TileSingleLine.class);
                if (l.isEmpty()) say(s, "§7No single-line section controllers loaded");
                for (TileSingleLine x : l) for (String t : x.report()) say(s, t);
            }
            case "buffer", "buffers" -> {                                  // batch-3 #9
                if (a.length > 2 && a[2].equalsIgnoreCase("selftest")) { bufferSelftest(s); return; }
                List<TileBufferStop> l = TrackHooks.live(w, TileBufferStop.class);
                if (l.isEmpty()) say(s, "§7No buffer stops loaded");
                for (TileBufferStop x : l) for (String t : x.report()) say(s, t);
            }
            case "retarder", "retarders" -> {                              // batch-3 #25
                if (a.length > 2 && a[2].equalsIgnoreCase("selftest")) { for (String t : com.dogpound.railmap.yard.TileRetarder.selftest()) say(s, t); return; }
                List<com.dogpound.railmap.yard.TileRetarder> l = TrackHooks.live(w, com.dogpound.railmap.yard.TileRetarder.class);
                if (l.isEmpty()) say(s, "§7No yard retarders loaded");
                for (com.dogpound.railmap.yard.TileRetarder x : l) for (String t : x.report()) say(s, t);
            }
            case "table", "tables" -> tables(s, w, a, at);              // batch-3 #3
            case "bridge", "bridges" -> bridges(s, w, a, at);            // batch-3 #8
            default -> say(s, "§7/irextras " + USAGE);
        }
    }

    /** track standards: list | set <standard> [radius] (test: rebuild the track around you) | selftest */
    private static void standards(ICommandSender s, World w, String[] a, BlockPos at, TrackData d) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        if (op.equals("set") && a.length > 3) {
            TrackData.Standard st = null;
            for (TrackData.Standard x : TrackData.Standard.values()) if (x.name().replace("_", "").equalsIgnoreCase(a[3].replace("_", "").replace("-", "")) || x.tag.equalsIgnoreCase(a[3])) st = x;
            if (st == null) { say(s, "§cStandards: ballasted, slab, heavy_haul, metro, high_speed"); return; }
            int rad = a.length > 4 ? (int) parse(a[4], 16) : 16;
            Set<Long> keys = new HashSet<>();
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) for (int dy = -3; dy <= 3; dy++) {
                TileRail r = Rails.pieceAt(w, at.getX() + dx + 0.5, at.getY() + dy + 0.5, at.getZ() + dz + 0.5);
                if (r != null) keys.add(Rails.pos(r).toLong());
            }
            for (long k : keys) { Seg g = d.seg(BlockPos.fromLong(k)); g.std = st.ordinal(); if (g.lengthM == 0) { TileRail r = Rails.pieceAt(w, BlockPos.fromLong(k)); if (r != null) g.lengthM = Rails.lengthM(r); } }
            d.markDirty();
            say(s, "§a" + keys.size() + " pieces of track set to " + st.label);
            return;
        }
        if (op.equals("selftest")) {
            int pass = 0, fail = 0;
            java.util.function.BiConsumer<Boolean, String> t = (ok, what) -> say(s, (ok ? "§aPASS " : "§cFAIL ") + what);
            boolean[] r = {
                    TrackData.Standard.of(0) == TrackData.Standard.BALLASTED && TrackData.Standard.of(99) == TrackData.Standard.HIGH_SPEED,
                    TrackData.Standard.SLAB.wear[3] == 0 && TrackData.Standard.SLAB.wear[0] < TrackData.Standard.BALLASTED.wear[0],
                    TrackData.Standard.HEAVY_HAUL.axleT > TrackData.Standard.BALLASTED.axleT && TrackData.Standard.HIGH_SPEED.lineKmh > TrackData.Standard.BALLASTED.lineKmh,
                    Math.abs(Math.pow(30 / TrackData.Standard.BALLASTED.axleT, 4) - 2.0736) < 1e-3,
                    new TrackData.Seg().std == 0};
            String[] what = {"standard lookup clamps", "slab track: no ballast fouling, far less geometry wear", "heavy haul carries more, high speed runs faster",
                    "fourth-power overload factor (30 t on a 25 t standard = 2.07x)", "old / new pieces default to ballasted"};
            for (int i = 0; i < r.length; i++) { t.accept(r[i], what[i]); if (r[i]) pass++; else fail++; }
            say(s, (fail == 0 ? "§a" : "§c") + "Track standards self-test: " + pass + " pass, " + fail + " fail");
            return;
        }
        for (String[] r : TrackConsole.standards(d)) say(s, "§b" + r[1] + "§7: " + r[2] + " pieces, " + r[3] + " m · axle " + r[4] + " t · " + r[5] + " km/h · wear x" + r[6] + " · " + r[7] + "/m · " + r[8] + " overloads · " + r[9] + " overspeeds · TQI " + r[10]);
        say(s, "§8Lay with the Track Maintenance Kit (LAY <standard> jobs). Line speeds as restrictions: config trackStandardLimits = " + TrackConfig.trackStandardLimits);
    }

    private static void check(ICommandSender s, boolean ok, String what, int[] n) { say(s, (ok ? "§aPASS " : "§cFAIL ") + what); n[ok ? 0 : 1]++; }

    /** batch-3 #9: the buffer stop's energy + braking-curve maths */
    private static void bufferSelftest(ICommandSender s) {
        int[] n = new int[2];
        check(s, Math.abs(TileBufferStop.energyKJ(100, 10) - 385.8) < 1, "100 t at 10 km/h = 386 kJ", n);
        double atStop = TileBufferStop.allowedKmh(1200, 100, 0), far = TileBufferStop.allowedKmh(1200, 100, 100);
        check(s, Math.abs(TileBufferStop.energyKJ(100, atStop) - 1200) < 2, "allowed impact speed uses exactly the capacity left", n);
        check(s, far > atStop, "the allowed speed rises with distance (braking curve)", n);
        check(s, TileBufferStop.allowedKmh(300, 400, 0) < TileBufferStop.allowedKmh(300, 40, 0), "heavier trains must arrive slower", n);
        check(s, TileBufferStop.allowedKmh(0, 100, 0) == 0, "a wrecked stop allows nothing at contact", n);
        say(s, (n[1] == 0 ? "§a" : "§c") + "Buffer stop self-test: " + n[0] + " pass, " + n[1] + " fail");
    }

    /** batch-3 #7: the section's direction names */
    private static void singleSelftest(ICommandSender s) {
        int[] n = new int[2];
        check(s, TileSingleLine.dirName(TileSingleLine.AB).equals("A>B") && TileSingleLine.dirName(TileSingleLine.BA).equals("B>A") && TileSingleLine.dirName(TileSingleLine.NONE).equals("NONE"), "direction names", n);
        check(s, TileSingleLine.MODES.length == 4 && TileSingleLine.MODES[3].equals("CLOSED"), "modes AUTO / FIXED A>B / FIXED B>A / CLOSED", n);
        say(s, "§7(live checks: place a controller, link END A / END B + entry signals, run trains both ways; /irextras track single shows the state)");
        say(s, (n[1] == 0 ? "§a" : "§c") + "Single-line self-test: " + n[0] + " pass, " + n[1] + " fail");
    }

    private static double parse(String v, double def) { try { return Double.parseDouble(v); } catch (NumberFormatException e) { return def; } }

    // ---- batch 3 #3 turntables + traversers, #8 movable bridges ---------------------------------------------------------

    private static <T extends net.minecraft.tileentity.TileEntity> T nearest(World w, Class<T> c, BlockPos at) {
        T best = null;
        double bd = 64 * 64;
        for (T t : TrackHooks.live(w, c)) { double q = t.getPos().distanceSq(at); if (q < bd) { bd = q; best = t; } }
        return best;
    }

    private static void tables(ICommandSender s, World w, String[] a, BlockPos at) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        if (op.equals("selftest")) { for (String l : Tables.selftest()) say(s, l); return; }
        if (op.equals("go")) {
            TileTurntableControl t = nearest(w, TileTurntableControl.class, at);
            if (t == null) { say(s, "§7No turntable panel within 64 blocks"); return; }
            int road = a.length > 3 ? (int) parse(a[3], 1) : 1;
            String r = t.request(road - 1, s.getName(), true);
            say(s, r == null ? "§e" + t.id() + ": turning to road " + road : "§c" + r);
            return;
        }
        int n = 0;
        for (TileTurntableControl t : TrackHooks.live(w, TileTurntableControl.class)) { n++; say(s, "§b" + t.id() + " §7" + Rails.xz(t.getPos()) + " · " + t.gearState()); }
        if (n == 0) say(s, "§7No turntable / traverser panels loaded");
    }

    private static void bridges(ICommandSender s, World w, String[] a, BlockPos at) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        if (op.equals("selftest")) { for (String l : TileMovableBridge.selftest()) say(s, l); for (String l : Tables.selftest()) say(s, l); return; }
        if (op.equals("open") || op.equals("close")) {
            TileMovableBridge b = nearest(w, TileMovableBridge.class, at);
            if (b == null) { say(s, "§7No movable bridge desk within 64 blocks"); return; }
            String r = b.request(op.equals("open"), s.getName(), true);
            say(s, r == null ? "§e" + b.id() + ": " + op.toUpperCase(Locale.ROOT) + " sequence started" : "§c" + r);
            return;
        }
        int n = 0;
        for (TileMovableBridge b : TrackHooks.live(w, TileMovableBridge.class)) { n++; say(s, "§b" + b.id() + " §7" + Rails.xz(b.getPos()) + " · " + b.gearState()); }
        if (n == 0) say(s, "§7No movable bridge desks loaded");
    }
}
