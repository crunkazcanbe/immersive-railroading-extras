package com.dogpound.railmap.ohle;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.signal.BlockCatenary;
import com.dogpound.railmap.weather.Crews;
import com.dogpound.railmap.weather.Weather;
import com.dogpound.railmap.weather.WeatherData;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras ohle | energy | weather - inspection and test commands for the overhead line (#31-32), energy dispatch
 * (#33) and railway weather (#63-66).
 */
public final class OhleCommand {
    private OhleCommand() {}

    public static final String USAGE = "ohle [sections|survey [r]|fault [earth|short]|clear|feeder close|open|pan up|down|auto|manual|force <N>|service|wear <pct>] | energy [mark <label>|compare|peakshift on|off] | weather [heatwave|coldsnap [min]|calm|set snow|ice|water|condition <v>|risk <0-3|auto>|orders|restrictions]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0) return false;
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "ohle": ohle(w, s, a); return true;
                case "energy": energy(w, s, a); return true;
                case "weather": weather(w, s, a); return true;
                default: return false;
            }
        } catch (NumberFormatException e) {
            say(s, "§cBad number. /irextras " + USAGE);
            return true;
        }
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    // ------------------------------------------------------------------------------------------------ ohle
    private static void ohle(World w, ICommandSender s, String[] a) {
        BlockPos me = s.getPosition();
        OhleData d = OhleData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "": case "sections": {
                List<Ohle.Section> l = Ohle.known(w);
                say(s, "§b== OHLE: " + l.size() + " sections, " + d.faults.size() + " faults, sectioning " + (RailMapConfig.ohleSectioning ? "on" : "OFF") + ", contact detail " + (RailMapConfig.ohleContactDetail ? "on" : "OFF"));
                for (Ohle.Section x : l) {
                    StringBuilder f = new StringBuilder();
                    for (long fd : x.feeders) f.append(" F").append(Ohle.xz(BlockPos.fromLong(fd))).append(cbName(d, fd));
                    say(s, (x.energised ? "§c" : x.earthed ? "§e" : "§7") + x.name() + " " + x.state() + " · " + x.wires.size() + " m · " + x.insulators.size() + " SI " + x.neutrals.size() + " NS · "
                            + String.format(Locale.ROOT, "%.1f kN", x.tension(w) / 1000) + (x.tensioners.isEmpty() ? " (fixed)" : "") + f + (x.why.isEmpty() ? "" : " · " + x.why));
                }
                for (OhleData.Fault f : d.faults) say(s, "§c FAULT " + f.kind + " at " + Ohle.xyz(BlockPos.fromLong(f.pos)) + ": " + f.why + (f.persistent ? " (standing)" : ""));
                for (int i = 0; i < Math.min(6, d.log.size()); i++) say(s, "§8 " + d.log.get(i));
                return;
            }
            case "survey": survey(w, s, me, a.length > 2 ? Integer.parseInt(a[2]) : 32); return;
            case "fault": {
                BlockPos wire = nearestLine(w, me, 12);
                if (wire == null) { say(s, "§cNo contact wire within 12 blocks"); return; }
                boolean shortc = a.length > 2 && a[2].equalsIgnoreCase("short");
                if (!shortc) d.damaged.add(wire.toLong());
                Ohle.fault(w, wire, shortc ? "SHORT" : "EARTH", shortc ? "test flashover (command)" : "test: wire down (command) - renew it with Contact Wire", !shortc);
                say(s, "§eInjected " + (shortc ? "a transient SHORT" : "a standing EARTH fault (wire down)") + " at " + Ohle.xyz(wire));
                return;
            }
            case "clear": {
                for (OhleData.Fault f : d.faults) com.dogpound.railmap.alarm.AlarmManager.get(w).clear("ohlef:" + f.pos);
                d.faults.clear(); d.damaged.clear();
                for (OhleData.Cb cb : d.cbs.values()) { cb.state = 0; cb.shots = 0; }
                d.markDirty(); Ohle.dirty();
                say(s, "§aAll OHLE faults cleared, torn wire restored, every feeder breaker closed");
                return;
            }
            case "feeder": {
                long best = 0;
                double bd = Double.MAX_VALUE;
                for (Map.Entry<Long, GridData.Node> en : GridData.get(w).nodes().entrySet())
                    if (en.getValue().kind == GridKind.FEEDER && BlockPos.fromLong(en.getKey()).distanceSq(me) < bd) { bd = BlockPos.fromLong(en.getKey()).distanceSq(me); best = en.getKey(); }
                if (best == 0) { say(s, "§cNo Track Feeder in this world"); return; }
                if (a.length < 3) { say(s, "§7Feeder " + Ohle.xz(BlockPos.fromLong(best)) + " breaker" + cbName(d, best)); return; }
                say(s, Ohle.feederOp(w, best, a[2].equalsIgnoreCase("close"), s.getName() + " (command)"));
                return;
            }
            case "wear": {
                BlockPos wire = nearestLine(w, me, 12);
                if (wire == null || a.length < 3) { say(s, "§c/irextras ohle wear <percent> near contact wire"); return; }
                d.wear.put(wire.toLong(), Float.parseFloat(a[2]) / 100f);
                d.markDirty();
                say(s, "§eWire at " + Ohle.xyz(wire) + " set to " + a[2] + "% worn");
                return;
            }
            case "pan": pan(w, s, me, d, a); return;
            default: say(s, "§c/irextras " + USAGE);
        }
    }

    private static String cbName(OhleData d, long f) {
        OhleData.Cb cb = d.cbs.get(f);
        int st = cb == null ? 0 : cb.state;
        return "[" + new String[]{"CLOSED", "TRIPPED", "LOCKOUT", "OPEN"}[st] + (cb != null && cb.trips > 0 ? " " + cb.trips + " trips" : "") + "]";
    }

    static BlockPos nearestLine(World w, BlockPos me, int r) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -4; dy <= 10; dy++) {
                    BlockPos q = me.add(dx, dy, dz);
                    if (Ohle.kindAt(w, q) == BlockCatenary.Kind.WIRE && q.distanceSq(me) < bd) { bd = q.distanceSq(me); best = q; }
                }
        return best;
    }

    /** height and alignment check: every wire block within r, against the rail under it */
    private static void survey(World w, ICommandSender s, BlockPos me, int r) {
        r = Math.min(64, Math.max(4, r));
        OhleData d = OhleData.get(w);
        int n = 0, low = 0, high = 0, off = 0, noRail = 0, worn = 0, down = 0, steps = 0;
        List<String> bad = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -8; dy <= 12; dy++) {
                    BlockPos q = me.add(dx, dy, dz);
                    if (Ohle.kindAt(w, q) != BlockCatenary.Kind.WIRE) continue;
                    n++;
                    float wr = d.wear.getOrDefault(q.toLong(), 0f);
                    if (wr >= 0.7f) { worn++; bad.add(Ohle.xyz(q) + " worn " + Math.round(wr * 100) + "%"); }
                    if (d.damaged.contains(q.toLong())) { down++; bad.add(Ohle.xyz(q) + " WIRE DOWN"); }
                    BlockPos rail = null;
                    int lat = 0;
                    search:
                    for (int k = 2; k <= 9; k++)
                        for (int l = 0; l <= 1; l++)
                            for (int[] o : l == 0 ? new int[][]{{0, 0}} : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                BlockPos b = q.add(o[0], -k, o[1]);
                                if (w.isBlockLoaded(b) && w.getTileEntity(b) != null && com.dogpound.railmap.scan.SwitchControl.isRail(w, b)) { rail = b; lat = l; break search; }
                            }
                    if (rail == null) { noRail++; continue; }
                    double h = q.getY() + 0.125 - (rail.getY() + 0.1);
                    if (h < 4.7) { low++; bad.add(Ohle.xyz(q) + String.format(Locale.ROOT, " LOW %.2f m", h)); }
                    if (h > 6.2) { high++; bad.add(Ohle.xyz(q) + String.format(Locale.ROOT, " HIGH %.2f m%s", h, h > RailMapConfig.ohlePanReachM ? " - out of pantograph reach" : "")); }
                    if (lat > 0) { off++; bad.add(Ohle.xyz(q) + " not over its rail (1 m stagger)"); }
                    for (BlockPos nb : new BlockPos[]{q.north(), q.east()})
                        if (Ohle.kindAt(w, nb.up()) == BlockCatenary.Kind.WIRE || Ohle.kindAt(w, nb.down()) == BlockCatenary.Kind.WIRE) steps++;
                }
        say(s, "§b== OHLE survey, " + r + " blocks: " + n + " wire blocks · " + low + " low (<4.7 m) · " + high + " high (>6.2 m) · " + off + " off-centre · " + steps + " height steps · " + worn + " worn · " + down + " down · " + noRail + " with no track under");
        for (int i = 0; i < Math.min(10, bad.size()); i++) say(s, "§e " + bad.get(i));
        if (bad.size() > 10) say(s, "§7 ...and " + (bad.size() - 10) + " more");
    }

    private static void pan(World w, ICommandSender s, BlockPos me, OhleData d, String[] a) {
        LocomotiveDiesel best = null;
        double bd = 24 * 24;
        for (LocomotiveDiesel l : cam72cam.mod.world.World.get(w).getEntities(LocomotiveDiesel.class)) {
            double dd = l.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(me.getX(), me.getY(), me.getZ()));
            if (!l.isDead() && dd < bd) { bd = dd; best = l; }
        }
        if (best == null) { say(s, "§cNo locomotive within 24 blocks"); return; }
        OhleData.Pan p = d.pan(best.getUUID());
        String name = best.getDefinition() == null ? "loco" : best.getDefinition().name();
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "status";
        switch (op) {
            case "up": p.manual = true; p.wantUp = true; say(s, "§e" + name + ": pantograph MANUAL, raising"); break;
            case "down": p.manual = true; p.wantUp = false; say(s, "§e" + name + ": pantograph MANUAL, lowered"); break;
            case "auto": p.manual = false; p.downZone = false; say(s, "§a" + name + ": pantograph AUTO (raises under wire, APC through neutral sections, obeys magnets)"); break;
            case "manual": p.manual = true; p.wantUp = p.up; say(s, "§e" + name + ": pantograph MANUAL - APC isolated, you raise / lower it"); break;
            case "force":
                if (a.length < 4) { say(s, "§c/irextras ohle pan force <N>"); return; }
                p.forceN = Math.max(20, Math.min(300, Integer.parseInt(a[3])));
                say(s, "§e" + name + ": static contact force " + p.forceN + " N" + (p.forceN < 55 ? " (too light: poor contact)" : p.forceN > 140 ? " (too heavy: wire wear)" : ""));
                break;
            case "service":
                p.carbon = 1f; p.broken = false; p.alerted = false;
                com.dogpound.railmap.alarm.AlarmManager.get(w).clear("pan:" + best.getUUID());
                say(s, "§a" + name + ": pantograph serviced - new carbon strips, head checked, force " + p.forceN + " N");
                break;
            default: {
                int recent = 0;
                for (long t : p.arcs) if (t > 0 && w.getTotalWorldTime() - t < 2400) recent++;
                say(s, "§b" + name + ": pantograph " + (p.broken ? "§cBROKEN" : p.up ? "UP" : "down") + "§b · VCB " + (p.vcb ? "closed" : "open") + " · " + (p.manual ? "MANUAL" : "AUTO") + (p.downZone ? " (LOWER PAN zone)" : "")
                        + " · force " + p.forceN + " N · carbon " + Math.round(p.carbon * 100) + "% · " + recent + " arcs / 2 min" + (p.note.isEmpty() ? "" : " · " + p.note));
                say(s, String.format(Locale.ROOT, "§7  ledger: %.1f kWh drawn, %.1f kWh regenerated, %.1f kWh in resistors, %.0f coins, %.0f%% at peak", p.kwhIn, p.kwhRegen, p.kwhRes, p.cost, p.kwhIn > 0 ? p.kwhPeak / p.kwhIn * 100 : 0));
            }
        }
        d.markDirty();
    }

    // ------------------------------------------------------------------------------------------------ energy
    private static void energy(World w, ICommandSender s, String[] a) {
        OhleData d = OhleData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        int h = EnergyDispatch.hour(w);
        switch (sub) {
            case "mark": {
                OhleData.Period cur = d.period();
                cur.end = w.getTotalWorldTime();
                OhleData.Period p = new OhleData.Period();
                p.label = a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : String.valueOf((char) ('A' + d.periods.size() % 26));
                p.start = w.getTotalWorldTime();
                d.periods.add(p);
                while (d.periods.size() > 12) d.periods.remove(0);
                d.markDirty();
                say(s, "§aPeriod \"" + cur.label + "\" closed; now measuring \"" + p.label + "\". Change the timetable, then /irextras energy compare");
                return;
            }
            case "compare": {
                say(s, "§b== Energy by operating period (kWh / train-hour normalises for length)");
                for (OhleData.Period p : d.periods) {
                    double hrs = Math.max(1, ((p.end == 0 ? w.getTotalWorldTime() : p.end) - p.start)) / 72000.0;
                    say(s, String.format(Locale.ROOT, "§e%s§7: %.0f kWh in (%.1f kWh/h), regen %.0f kWh (%.0f%%), resistors %.0f kWh, cost %.0f, peak %.0f%% · %d departures, %d held (avg %.0f s)",
                            p.label, p.kwhIn, p.kwhIn / hrs, p.kwhRegen, p.kwhIn > 0 ? p.kwhRegen / p.kwhIn * 100 : 0, p.kwhRes, p.cost, p.kwhIn > 0 ? p.kwhPeak / p.kwhIn * 100 : 0,
                            p.departures, p.held, p.held > 0 ? p.heldSeconds / (double) p.held : 0));
                }
                return;
            }
            case "peakshift":
                d.peakShift = a.length > 2 ? a[2].equalsIgnoreCase("on") : !d.peakShift;
                d.markDirty();
                say(s, "§ePeak shift " + (d.peakShift ? "ON: FULL LOAD freights wait out the peak tariff (max 1 game hour)" : "OFF"));
                return;
            default: {
                OhleData.Period p = d.period();
                say(s, "§b== Energy: " + String.format(Locale.ROOT, "%02d:00", h) + " " + EnergyDispatch.BANDS[EnergyDispatch.band(h)] + " tariff " + EnergyDispatch.tariff(w) + "/MWh · dispatch " + (RailMapConfig.energyDispatch ? "on" : "OFF") + " · peak shift " + (d.peakShift ? "on" : "off"));
                say(s, String.format(Locale.ROOT, "§7Period \"%s\": %.1f kWh in, %.1f regen, %.1f resistors, %.0f coins, %d departures (%d held)", p.label, p.kwhIn, p.kwhRegen, p.kwhRes, p.cost, p.departures, p.held));
                List<OhleData.Pan> l = new ArrayList<>(d.pans.values());
                l.sort((x, y) -> Double.compare(y.kwhIn, x.kwhIn));
                for (int i = 0; i < Math.min(8, l.size()); i++) {
                    OhleData.Pan x = l.get(i);
                    say(s, String.format(Locale.ROOT, "§7 %s: %.1f kWh, regen %.1f, resistors %.1f, %.0f coins", x.name.isEmpty() ? "loco" : x.name, x.kwhIn, x.kwhRegen, x.kwhRes, x.cost));
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ weather
    private static void weather(World w, ICommandSender s, String[] a) {
        WeatherData d = WeatherData.get(w);
        BlockPos me = s.getPosition();
        WeatherData.Cell c = Weather.cell(w, me);
        long now = w.getTotalWorldTime();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "heatwave": case "coldsnap": {
                long until = now + 1200L * (a.length > 2 ? Integer.parseInt(a[2]) : 20);
                if (sub.equals("heatwave")) d.heatwaveUntil = until; else d.coldSnapUntil = until;
                d.log(w, sub.toUpperCase(Locale.ROOT) + " for " + (until - now) / 1200 + " min (" + s.getName() + ")");
                say(s, "§e" + sub + " for " + (until - now) / 1200 + " minutes");
                return;
            }
            case "calm": d.heatwaveUntil = 0; d.coldSnapUntil = 0; d.markDirty(); say(s, "§aNo heatwave / cold snap"); return;
            case "set": {
                if (a.length < 4) { say(s, "§c/irextras weather set snow|ice|water|condition <value>"); return; }
                double v = Double.parseDouble(a[3]);
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "snow" -> c.snow = v;
                    case "ice" -> c.ice = v;
                    case "water" -> c.water = v;
                    case "condition" -> c.condition = Math.max(0.1, Math.min(1, v / 100));
                    default -> { say(s, "§csnow | ice | water | condition"); return; }
                }
                d.markDirty();
                say(s, "§e" + Weather.name(c) + ": " + a[2] + " = " + a[3]);
                return;
            }
            case "risk":
                c.floodRisk = a.length > 2 && !a[2].equalsIgnoreCase("auto") ? Math.max(0, Math.min(3, Integer.parseInt(a[2]))) : -1;
                d.markDirty();
                say(s, "§eFlood risk of " + Weather.name(c) + ": " + (c.floodRisk < 0 ? "automatic" : c.floodRisk));
                return;
            case "orders": {
                say(s, "§b== Snow work orders (" + RailMapConfig.snowCrews + " gangs)");
                for (WeatherData.Cell x : Crews.orders(d))
                    say(s, String.format(Locale.ROOT, "§7 %s: %.0f mm, %.1f trains/h, priority %.0f%s", Weather.name(x), x.snow, x.traffic, Crews.priority(x), x.crew >= 0 ? " §a- gang " + (x.crew + 1) + " working" : ""));
                return;
            }
            case "restrictions": {
                say(s, "§b== Weather restrictions");
                for (WeatherData.Cell x : d.cells.values()) {
                    Object[] r = Weather.restriction(w, x, now);
                    if ((double) r[0] >= 0) say(s, "§e " + Weather.name(x) + ": " + ((double) r[0] <= 0 ? "CLOSED" : Math.round((double) r[0]) + " km/h") + " - " + r[1]);
                }
                return;
            }
            default: {
                Object[] r = Weather.restriction(w, c, now);
                say(s, "§b== " + Weather.name(c) + (Weather.weather2() ? " (Weather2)" : "") + (now < d.heatwaveUntil ? " §cHEATWAVE" : "") + (now < d.coldSnapUntil ? " §bCOLD SNAP" : ""));
                say(s, String.format(Locale.ROOT, "§7air %.1f °C · rail %.1f °C (SFT %.0f, condition %.0f%%) · wind %.0f m/s · %s · visibility %.0f m %s",
                        c.air, c.rail, c.sft, c.condition * 100, c.wind, c.precip == 0 ? "dry" : (c.snowing ? "snow " : "rain ") + c.precip, c.vis, c.visWhy));
                say(s, String.format(Locale.ROOT, "§7snow %.0f mm · ice on wire %.1f mm · water %.0f mm (%s) · flood risk %s · %.1f trains/h%s",
                        c.snow, c.ice, c.water, c.water > 150 ? "§cover the rail§7" : "below the rail head", c.floodRisk < 0 ? "auto" : String.valueOf(c.floodRisk), c.traffic, c.tunnel ? " · in a tunnel" : ""));
                say(s, (double) r[0] < 0 ? "§aNo weather restriction" : "§e" + ((double) r[0] <= 0 ? "LINE CLOSED" : Math.round((double) r[0]) + " km/h") + " - " + r[1]);
                for (int i = 0; i < Math.min(5, d.log.size()); i++) say(s, "§8 " + d.log.get(i));
            }
        }
    }
}
