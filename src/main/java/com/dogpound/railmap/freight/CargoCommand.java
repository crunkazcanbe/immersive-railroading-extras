package com.dogpound.railmap.freight;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.biz.BizData;
import com.dogpound.railmap.biz.Custody;
import com.dogpound.railmap.track.Rails;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras stn reefer [set <unit> <C>] · box [<number>] · washout · weigh [near] · selftest  (cargo care, batch 3 #21-24)
 */
public final class CargoCommand {
    private CargoCommand() {}

    public static boolean run(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "selftest" -> { selftest(s); return true; }
            case "reefer" -> {
                Reefer r = Reefer.get(w);
                if (a.length > 4 && a[2].equalsIgnoreCase("set")) {
                    for (Reefer.Unit u : r.units.values())
                        if (u.key.contains(a[3]) || u.name.equalsIgnoreCase(a[3])) {
                            try { u.set = Math.max(-30, Math.min(20, Double.parseDouble(a[4]))); } catch (NumberFormatException e) { say(s, "§cNot a temperature: " + a[4]); return true; }
                            r.markDirty();
                            say(s, String.format(Locale.ROOT, "§a%s setpoint %+.0f C", u.name, u.set));
                            return true;
                        }
                    say(s, "§cNo reefer matching " + a[3]);
                    return true;
                }
                if (r.units.isEmpty()) say(s, "§7No reefers seen yet (wagons named reefer / refrigerated / frigo / chiller, or containers on a plug post)");
                for (Reefer.Unit u : r.units.values())
                    say(s, String.format(Locale.ROOT, "§e%s §7%s %+.1f C set %+.0f · quality %.0f%% · %s · fuel %.0f L%s", u.key.substring(0, Math.min(12, u.key.length())), u.name, u.temp, u.set,
                            u.quality, u.power, u.fuel, u.alarm ? " §cALARM" : ""));
                return true;
            }
            case "box" -> {
                Custody cu = BizData.get(w).custody;
                if (a.length > 2) {
                    Custody.Rec c = cu.where(String.join("", Arrays.copyOfRange(a, 2, a.length)));
                    if (c == null) say(s, "§cNo single container matches " + a[2]);
                    else CargoConsole.trace(s, c, w);
                    return true;
                }
                int n = 0;
                for (Custody.Rec c : cu.recs.values())
                    if (!c.status.equals("OK") && !c.status.equals("CLEARED") || !c.contam.isEmpty()) { n++; say(s, "§e" + c.id + " §7" + c.status + " " + c.lastMode + " @ " + c.lastPlace + (c.contam.isEmpty() ? "" : " §c" + c.contam)); }
                say(s, "§7" + cu.recs.size() + " tracked, " + n + " flagged. /irextras stn box <number> for a trace");
                return true;
            }
            case "washout" -> {
                Custody cu = BizData.get(w).custody;
                long now = w.getTotalWorldTime();
                Set<UUID> cars = new LinkedHashSet<>(cu.cars.keySet());
                cars.addAll(cu.carLast.keySet());
                for (UUID c : cars)
                    say(s, String.format(Locale.ROOT, "§e%s §7clean %.0f%% · last load %s · cert %s", c.toString().substring(0, 8), cu.cars.getOrDefault(c, 100.0),
                            cu.carLast.getOrDefault(c, "?"), Custody.certValid(cu.carCert.getOrDefault(c, 0L), now) ? "§aVALID" : "none"));
                if (cars.isEmpty()) say(s, "§7No wagons on record yet");
                return true;
            }
            case "weigh" -> {
                if (a.length > 2 && a[2].equalsIgnoreCase("near")) {
                    EntityRollingStock best = null;
                    double bd = 32 * 32;
                    for (EntityRollingStock rs : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                        double d = rs.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(s.getPositionVector().x, s.getPositionVector().y, s.getPositionVector().z));
                        if (d < bd) { bd = d; best = rs; }
                    }
                    if (best == null) { say(s, "§7No vehicle within 32 blocks"); return true; }
                    CargoRules.Load l = CargoRules.load(best);
                    StringBuilder ax = new StringBuilder();
                    for (double x : l.axleT) ax.append(String.format(Locale.ROOT, "%.1f ", x));
                    say(s, String.format(Locale.ROOT, "§e%s §7%.1f t (cargo %.1f t), axles %s· side %.0f%% %s · end %.0f%% %s · limit %.1f t", Rails.name(best), l.totalT, l.cargoT, ax,
                            l.lateral * 100, l.left ? "L" : "R", l.longitudinal * 100, l.front ? "F" : "R", CargoRules.axleLimitT()));
                    return true;
                }
                int n = 0;
                for (net.minecraft.tileentity.TileEntity te : new ArrayList<>(w.loadedTileEntityList))
                    if (te instanceof TileAxleWeighbridge b) for (String l : b.log) { if (n++ < 20) say(s, "§7" + l.replace("|", " · ")); }
                if (n == 0) say(s, "§7No weighings yet. /irextras stn weigh near = weigh the nearest vehicle now");
                return true;
            }
            default -> { return false; }
        }
    }

    // ---- self-test ---------------------------------------------------------------------------------------------------
    static void selftest(ICommandSender s) {
        List<String> fail = new ArrayList<>();
        int n = 0;
        // #22: ISO 6346 check digit (CSQU 305438 3 is the standard's own example)
        n++; if (!Custody.validId("CSQU3054383")) fail.add("check digit: CSQU3054383 should be valid");
        n++; if (Custody.validId("CSQU3054384")) fail.add("check digit: CSQU3054384 should be invalid");
        n++; if (Custody.validId("CSQX3054383")) fail.add("category letter X accepted");
        // #24: distribution + axle loads
        double[] d = CargoRules.distribution(2, new double[]{10, 0, 0, 0});
        n++; if (Math.abs(d[0] - 1) > 1e-9) fail.add("distribution: front-left slot not front-left");
        CargoRules.Load l = CargoRules.load(20000, 40000, new double[]{0.5, 0, 0.5, 0}, 4);
        n++; if (Math.abs(l.lateral - 40000.0 / 60000) > 1e-6 || !l.left) fail.add("lateral imbalance " + l.lateral);
        n++; if (Math.abs(l.axleT[0] - 15) > 1e-6 || l.longitudinal > 1e-9) fail.add("axle loads " + Arrays.toString(l.axleT));
        CargoRules.Load f = CargoRules.load(20000, 40000, new double[]{0.5, 0.5, 0, 0}, 4);
        n++; if (Math.abs(f.maxAxleT - 25) > 1e-6 || !f.front) fail.add("front-loaded max axle " + f.maxAxleT);
        // #21: reefer physics
        Reefer.Unit u = new Reefer.Unit();
        u.temp = 3; u.set = 3; u.ambient = 30; u.food = true;
        for (int i = 0; i < 600; i++) Reefer.step(u, false, 1);
        n++; if (u.temp <= 3 + 1 || u.quality >= 100) fail.add(String.format(Locale.ROOT, "unpowered reefer did not warm / decay (%.1f C, %.1f%%)", u.temp, u.quality));
        double q = u.quality;
        for (int i = 0; i < 1200; i++) Reefer.step(u, true, 1);
        n++; if (Math.abs(u.temp - 3) > 0.01) fail.add("powered reefer did not pull down: " + u.temp);
        n++; if (u.quality < q - 5) fail.add("quality kept falling after pull-down");
        n++; if (!Reefer.isReefer("Mechanical Reefer 57ft") || Reefer.isReefer("Covered Hopper")) fail.add("reefer names");
        // #23: contamination rule
        n++; if (CargoConfig.contamination && Custody.contamRule("HAZ", 0, 1000).isEmpty()) fail.add("food after HAZ without a certificate passed");
        n++; if (!Custody.contamRule("HAZ", 1000, 1500).isEmpty()) fail.add("valid STEAM certificate not accepted");
        n++; if (!Custody.contamRule("BULK", 0, 1000).isEmpty()) fail.add("bulk last load should not need a certificate");
        say(s, fail.isEmpty() ? "§aCargo care self-test: " + n + "/" + n + " passed" : "§cCargo care self-test: " + (n - fail.size()) + "/" + n + " passed");
        for (String x : fail) say(s, "§c - " + x);
    }

    private static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
