package com.dogpound.railmap.yard;

import com.dogpound.railmap.controls.IControlled;
import net.minecraft.command.ICommandSender;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Locale;

/** /irextras yard [scan | start | stop | route N | release]: the nearest Yard Control Panel (64 blocks), for testing and for computers. */
public final class YardCommands {
    private YardCommands() {}

    public static boolean command(World world, ICommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("yard")) return false;
        TileYardPanel y = nearest(world, sender.getPosition());
        if (y == null) { say(sender, "§7No Yard Control Panel within 64 blocks."); return true; }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        String id = switch (sub) {
            case "scan" -> "scan";
            case "start" -> "start";
            case "stop" -> "stop";
            case "release" -> "relhb";
            case "route" -> args.length > 2 ? "r" + args[2] : null;
            default -> null;
        };
        if (id != null) say(sender, IControlled.operate(y, id, null));
        say(sender, "§bYard panel §7" + TileYardPanel.at(y.getPos()) + ": " + y.gearState());
        say(sender, y.linkStatus());
        for (int i = 0; i < TileYardPanel.TRACKS; i++)
            if (y.clear[i] != null) say(sender, String.format(Locale.ROOT, "§7T%d %-12s %d cars %d t", i + 1, y.rule(i), y.count[i], Math.round(y.tonnes[i])));
        int k = 1;
        for (TileYardPanel.Cut c : y.cuts) {
            say(sender, String.format(Locale.ROOT, "§f cut %d: %d cars %.0f t -> %s %s §8(%s)", k++, c.cars.size(), c.tonnes, c.track < 0 ? "NO TRACK" : "T" + (c.track + 1), c.dest,
                    c.why));
            if (k > 12) break;
        }
        say(sender, "§8/irextras yard scan | start | stop | route <1-8> | release");
        return true;
    }

    static TileYardPanel nearest(World world, BlockPos at) {
        TileYardPanel best = null;
        double bd = 64 * 64;
        for (TileEntity t : world.loadedTileEntityList) {
            if (!(t instanceof TileYardPanel y) || t.isInvalid()) continue;
            double d = t.getPos().distanceSq(at);
            if (d < bd) { bd = d; best = y; }
        }
        return best;
    }

    static void say(ICommandSender s, String m) { if (m != null && !m.isEmpty()) s.sendMessage(new TextComponentString(m)); }
}
