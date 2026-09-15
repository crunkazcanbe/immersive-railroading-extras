package com.dogpound.railmap.server;

import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileCrossing;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * {@code /irextras status} -- what the railroad systems can see right now: trains the tracker is
 * reporting, and how many signals, crossings, bridges, speed signs and joints are registered.
 * For checking a layout (and for bug reports) without guessing from the lights.
 */
public class CommandIrExtras extends CommandBase {
    @Override
    public String getName() {
        return "irextras";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/irextras status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        int dim = world.provider.getDimension();
        java.util.List<TrainNode> trains = TrainTracker.latest(world);
        say(sender, "IR Extras - dimension " + dim + ": " + trains.size() + " train(s) tracked, "
                + SignalRegistry.masts(dim).size() + " signal(s), " + SignalRegistry.crossings(dim).size() + " crossing(s), "
                + SignalRegistry.bridges(dim).size() + " bridge(s), " + SignalRegistry.speedSigns(dim).size() + " speed sign(s), "
                + SignalRegistry.joints(dim) + " joint(s)");
        int umc = 0, rails = 0, withInfo = 0;
        for (net.minecraft.tileentity.TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof cam72cam.mod.block.tile.TileEntity u)) continue;
            umc++;
            cam72cam.mod.block.BlockEntity be = u.instance();
            if (be instanceof cam72cam.immersiverailroading.tile.TileRail r) {
                rails++;
                if (r.info != null) withInfo++;
            }
        }
        com.dogpound.railmap.graph.RailNetwork net = com.dogpound.railmap.scan.NetworkScanner.scan(world, sender.getPosition());
        say(sender, " scan from you: " + umc + " UMC tiles loaded, " + rails + " IR rails (" + withInfo + " with info) -> "
                + net.nodes.size() + " track pieces, " + net.signals.size() + " signals, " + net.stops.size() + " stops");
        for (TrainNode t : trains) {
            say(sender, String.format(" train %s at %.0f %.0f %.0f, %.0f km/h", t.name, t.x, t.y, t.z, Math.abs(t.speedKmh)));
        }
        for (TileSignalMast m : SignalRegistry.masts(dim)) {
            say(sender, " signal at " + m.getPos().getX() + " " + m.getPos().getY() + " " + m.getPos().getZ() + ": " + m.aspect() + " (" + m.mode() + ")");
        }
        for (TileCrossing c : SignalRegistry.crossings(dim)) {
            say(sender, " crossing at " + c.getPos().getX() + " " + c.getPos().getY() + " " + c.getPos().getZ() + (c.active() ? ": ACTIVE" : ": idle"));
        }
    }

    private static void say(ICommandSender sender, String msg) {
        sender.sendMessage(new TextComponentString(msg));
    }
}
