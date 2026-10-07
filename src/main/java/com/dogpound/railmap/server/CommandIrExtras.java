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
        return "/irextras status | grid [fill] | sign <text|auto|off> | consist <set> | list [radius] | despawn [radius] | wear [radius] | wear add <part|all> <percent> | wear repair | electric <on|off|auto>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        if (args.length > 0 && args[0].equalsIgnoreCase("wear")) { wear(server, sender, world, args); return; }
        if (args.length > 1 && args[0].equalsIgnoreCase("consist")) {      // place a whole coupled train on the track you look at
            if (!(sender instanceof net.minecraft.entity.player.EntityPlayer ep)) { say(sender, "Players only."); return; }
            String id = args[1];
            if (!id.contains("/")) id = "rolling_stock/multiple_unit/" + id + (id.endsWith(".json") ? "" : ".json");
            cam72cam.immersiverailroading.registry.UnitDefinition unit = cam72cam.immersiverailroading.registry.DefinitionManager.getUnit(id);
            if (unit == null) { say(sender, "No such train set: " + id); return; }
            net.minecraft.util.math.RayTraceResult hit = ep.rayTrace(12, 1f);
            if (hit == null || hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) { say(sender, "Look at the track first."); return; }
            net.minecraft.util.math.BlockPos b = hit.getBlockPos();
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            cam72cam.mod.entity.Player pl = w.getEntity(ep.getUniqueID(), cam72cam.mod.entity.Player.class);
            int placed = ConsistPlacer.place(w, b, ep.rotationYawHead, unit);
            say(sender, placed == unit.unitList.size() ? "Placed " + unit.name + " (" + placed + " cars)"
                    : placed == 0 ? "No track there to place " + unit.name : "Placed " + placed + " of " + unit.unitList.size() + " cars of " + unit.name + " (the track ran out)");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("grid")) {         // power grid overview / creative fill
            com.dogpound.railmap.grid.GridData d = com.dogpound.railmap.grid.GridData.get(world);
            if (args.length > 1 && args[1].equalsIgnoreCase("fill")) {
                if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
                for (com.dogpound.railmap.grid.GridData.Node n : d.nodes().values())
                    if (n.kind == com.dogpound.railmap.grid.GridKind.INTAKE || n.kind == com.dogpound.railmap.grid.GridKind.BATTERY) n.energy = n.kind.capacity;
                d.touch();
                say(sender, "All grid intakes + batteries filled.");
                return;
            }
            if (d.nets().isEmpty()) { say(sender, "No power grid built in this dimension."); return; }
            for (com.dogpound.railmap.grid.GridData.Net n : d.nets())
                say(sender, String.format(java.util.Locale.ROOT, "Network #%d: %d parts, %,d FE, load %d FE/t - %s", n.id, n.nodes.size(), n.energy,
                        n.lastSecond / 20, n.problem.isEmpty() ? "LIVE (" + (n.rectifier ? "DC " : "") + (n.traction ? "AC" : "") + ")" : n.problem));
            return;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("sign")) {         // destination board of the nearest train
            cam72cam.immersiverailroading.entity.EntityRollingStock best = null;
            double bd = 24 * 24;
            net.minecraft.util.math.BlockPos at = sender.getPosition();
            for (cam72cam.immersiverailroading.entity.EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                cam72cam.mod.math.Vec3d p = s.getPosition();
                double d = (p.x - at.getX()) * (p.x - at.getX()) + (p.z - at.getZ()) * (p.z - at.getZ());
                if (d < bd) { bd = d; best = s; }
            }
            if (best == null) { say(sender, "No train within 24 blocks."); return; }
            cam72cam.immersiverailroading.entity.EntityRollingStock h = com.dogpound.railmap.signs.SignServer.head(best);
            RailProps.Props pr = RailProps.get(world).of(h.getUUID());
            String rest = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            if (rest.equalsIgnoreCase("auto")) { pr.signMode = 0; say(sender, "Signs: automatic (line + next stop)."); }
            else if (rest.equalsIgnoreCase("off")) { pr.signMode = 2; say(sender, "Signs: off."); }
            else { pr.signMode = 1; pr.signText = rest.length() > 120 ? rest.substring(0, 120) : rest; say(sender, "Signs now read: " + pr.signText); }
            RailProps.get(world).markDirty();
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {         // every piece of rolling stock nearby, with position
            int r = args.length > 1 ? Math.max(1, Math.min(1024, Integer.parseInt(args[1]))) : 64;
            net.minecraft.util.math.BlockPos at = sender.getPosition();
            int n = 0;
            for (cam72cam.immersiverailroading.entity.EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                cam72cam.mod.math.Vec3d p = s.getPosition();
                double d = Math.hypot(p.x - at.getX(), p.z - at.getZ());
                if (d > r) continue;
                n++;
                String name = s.getDefinition() == null ? s.getDefinitionID() : s.getDefinition().name();
                say(sender, String.format(java.util.Locale.ROOT, "%d. %s @ %.2f %.2f %.2f yaw %.0f len %.2f", n, name, p.x, p.y, p.z,
                        s.getRotationYaw(), s.getDefinition() == null ? 0 : s.getDefinition().getLength(s.gauge)));
            }
            if (n == 0) say(sender, "No rolling stock within " + r + " blocks.");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("despawn")) {     // IR stock ignores /kill: remove it properly
            if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
            int r = args.length > 1 ? Math.max(1, Math.min(512, Integer.parseInt(args[1]))) : 32;
            net.minecraft.util.math.BlockPos at = sender.getPosition();
            int n = 0;
            for (cam72cam.immersiverailroading.entity.EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                cam72cam.mod.math.Vec3d p = s.getPosition();
                if (Math.hypot(p.x - at.getX() - .5, p.z - at.getZ() - .5) > r) continue;
                s.kill();
                n++;
            }
            say(sender, "Removed " + n + " piece(s) of rolling stock within " + r + " blocks.");
            return;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("electric")) {
            byte mode = args[1].equalsIgnoreCase("on") ? RailProps.ON : args[1].equalsIgnoreCase("off") ? RailProps.OFF : RailProps.AUTO;
            say(sender, Electric.setNear(world, sender.getPosition(), 16, mode) + " locomotive(s) set to electric " + args[1].toLowerCase());
            return;
        }
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

    /** Maintenance & wear: report what is near you; ops can add or repair wear to test depots and limits. */
    private static void wear(MinecraftServer server, ICommandSender sender, World world, String[] args) {
        net.minecraft.util.math.BlockPos at = sender.getPosition();
        if (args.length >= 2 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("repair"))) {
            if (!sender.canUseCommand(2, "irextras")) { say(sender, "Only operators can change wear"); return; }
            String part = args[1].equalsIgnoreCase("repair") ? "all" : (args.length > 2 ? args[2] : "all");
            double pct = args[1].equalsIgnoreCase("repair") ? -1000 : (args.length > 3 ? Double.parseDouble(args[3]) : 50);
            say(sender, Wear.adjustNear(world, at, 16, part, pct) + " piece(s) of rolling stock changed");
            return;
        }
        int radius = args.length >= 2 ? Integer.parseInt(args[1]) : 64;
        java.util.List<String> lines = Wear.reportNear(world, at, radius);
        say(sender, "Rolling stock condition within " + radius + " blocks: " + (lines.isEmpty() ? "none" : lines.size()));
        for (String l : lines) say(sender, " " + l);
    }

    private static void say(ICommandSender sender, String msg) {
        sender.sendMessage(new TextComponentString(msg));
    }
}
