package com.dogpound.railmap.stn;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * /irextras stn ... (ChatGPT batch 3, #21-35). Each group has its own words; "stn selftest" runs all three self-tests.
 * Cargo care (freight.CargoCommand): reefer, box, washout, weigh. Platforms (station.PlatformCommand): crowd, zones,
 * doors, security. Station services (StnServices): formation, diagram, interchange, epower, lost, parcels, disrupt.
 */
public final class StnCommand {
    private StnCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("stn")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(java.util.Locale.ROOT) : "help";
        try {
            if (sub.equals("selftest")) {
                say(s, "§e-- cargo care --"); com.dogpound.railmap.freight.CargoCommand.run(w, s, new String[]{"stn", "selftest"});
                say(s, "§e-- platforms --"); com.dogpound.railmap.station.PlatformCommand.run(w, s, new String[]{"stn", "selftest"});
                say(s, "§e-- station services --"); StnServices.command(w, s, new String[]{"stn", "selftest"});
                return true;
            }
            if (com.dogpound.railmap.freight.CargoCommand.run(w, s, a)) return true;
            if (com.dogpound.railmap.station.PlatformCommand.run(w, s, a)) return true;
            if (StnServices.command(w, s, a)) return true;
        } catch (RuntimeException | LinkageError e) {
            say(s, "§c/irextras stn " + sub + " failed: " + e);
            return true;
        }
        say(s, "§e/irextras stn §7selftest · §ereefer|box|washout|weigh §7(cargo) · §ecrowd|zones|doors|security §7(platforms) · §eformation|diagram|interchange|epower|lost|parcels|disrupt §7(services)");
        return true;
    }

    public static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
