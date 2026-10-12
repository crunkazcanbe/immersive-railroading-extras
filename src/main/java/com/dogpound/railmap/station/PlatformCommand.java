package com.dogpound.railmap.station;

import com.dogpound.railmap.stn.StnCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /irextras stn crowd|zones|doors|security (+ selftest): the platform gear (ChatGPT batch 3 #27 #29 #32 #33) */
public final class PlatformCommand {
    private PlatformCommand() {}

    public static boolean run(World w, ICommandSender s, String[] a) {
        if (a.length < 2) return false;
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "selftest" -> {
                List<String> f = new ArrayList<>();
                f.addAll(TileCrowdMonitor.selftest());
                f.addAll(TileZoneMarker.selftest());
                f.addAll(TileDoorController.selftest());
                f.addAll(TileStationSecurity.selftest());
                f.add(PlatformOps.inflow(w, Long.MIN_VALUE) == 1 ? null : "inflow not 1 with nothing placed");
                f.removeIf(x -> x == null);
                StnCommand.say(s, f.isEmpty() ? "§aPlatform self-test passed (Fruin bands, M/D/1 queues, short platforms, SDO + overrun, opening hours)" : "§cPlatform self-test FAILED: " + String.join("; ", f));
                return true;
            }
            case "crowd" -> {
                List<TileCrowdMonitor> l = PlatformTile.loaded(w, TileCrowdMonitor.class);
                if (l.isEmpty()) StnCommand.say(s, "§7No crowd monitors loaded");
                for (TileCrowdMonitor m : l) StnCommand.say(s, "§e" + String.join(" · ", m.row()).replaceAll("(^|· )[!~+.]", "$1"));
                return true;
            }
            case "zones" -> {
                List<TileZoneMarker> l = PlatformTile.loaded(w, TileZoneMarker.class);
                if (l.isEmpty()) StnCommand.say(s, "§7No zone markers loaded");
                for (TileZoneMarker m : l) StnCommand.say(s, "§e" + String.join(" ", m.row()));
                if (a.length > 2 && a[2].equalsIgnoreCase("announce"))
                    for (TileZoneMarker m : l) { TileZoneMarker.Pred p = TileZoneMarker.predictNext(w, m.station(), m.stationName(), true); if (p != null) StnCommand.say(s, "§a" + p.text); }
                return true;
            }
            case "doors" -> {
                List<TileDoorController> l = PlatformTile.loaded(w, TileDoorController.class);
                if (l.isEmpty()) StnCommand.say(s, "§7No door safety controllers loaded");
                for (TileDoorController c : l) StnCommand.say(s, "§e" + String.join(" · ", c.row()));
                return true;
            }
            case "security" -> {
                List<TileStationSecurity> l = PlatformTile.loaded(w, TileStationSecurity.class);
                if (l.isEmpty()) StnCommand.say(s, "§7No security panels loaded");
                for (TileStationSecurity t : l) {
                    if (a.length > 2) { int m = a[2].equalsIgnoreCase("open") ? 1 : a[2].equalsIgnoreCase("close") ? 2 : 0; t.force(m); }
                    StnCommand.say(s, "§e" + String.join(" · ", t.row()));
                }
                return true;
            }
            default -> { return false; }
        }
    }
}
