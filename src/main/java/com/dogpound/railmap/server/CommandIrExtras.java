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
        return "/irextras status | " + com.dogpound.railmap.economy.EconCommand.USAGE + " | grid [fill | press x y z control] | sign <text|auto|off> | consist <set> | list [radius] | despawn [radius] | wear [radius] | wear add <part|all> <percent> | wear repair | electric <on|off|auto> | " + com.dogpound.railmap.admin.AdminCommand.USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        if (args.length > 0 && args[0].equalsIgnoreCase("wear")) { wear(server, sender, world, args); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("sig")) { com.dogpound.railmap.safety.SigCommand.run(sender, world, args); return; }   // signalling batch 3 (#11-20, #46, #48, #49)
        if (args.length > 0 && args[0].equalsIgnoreCase("safety")) { com.dogpound.railmap.safety.SafetyCommand.run(sender, world, args); return; }   // signalling safety (#19-28 + fault lab)
        if (args.length > 0 && args[0].equalsIgnoreCase("track")) { com.dogpound.railmap.track.TrackCommand.run(sender, world, args); return; }   // track infrastructure (#13-18)
        if (com.dogpound.railmap.api.ApiCommand.run(sender, world, args)) return;   // railway platform API (#96-105)
        if (args.length > 0 && args[0].equalsIgnoreCase("plan")) { com.dogpound.railmap.planning.PlanCommand.run(sender, world, args); return; }   // operations planning + simulation (next-48 #1 3 4 6 7 8 9 40 42 46 48)
        if (args.length > 0 && args[0].equalsIgnoreCase("incident")) { com.dogpound.railmap.incident.IncidentCommand.run(sender, world, args); return; }   // incident command + control room (#67-80)
        if (com.dogpound.railmap.admin.AdminCommand.command(world, sender, args)) return;   // multiplayer + server admin (#111-120)
        if (com.dogpound.railmap.sim.CabControls.command(world, sender, args)) return;   // working cab controls: /irextras cab status|test|key <action>
        if (com.dogpound.railmap.sim.TrainSim.command(world, sender, args)) return;   // physics / engine / adhesion / knuckle
        if (com.dogpound.railmap.yard.YardCommands.command(world, sender, args)) return;  // yard
        if (com.dogpound.railmap.shop.ShopCommand.command(world, sender, args)) return;   // station shops (#5): /irextras shop list|news|selftest
        if (com.dogpound.railmap.ems.EmsCommand.command(world, sender, args)) return;     // emergency services (#6): /irextras ems list|runs|send|recall|selftest
        if (com.dogpound.railmap.road.RoadCommand.command(world, sender, args)) return;   // batch 5 #3 #4: /irextras road ... | tram ...
        if (com.dogpound.railmap.stn.StnCommand.command(world, sender, args)) return;   // station operations + cargo care (batch 3 #21-35): /irextras stn ...
        if (com.dogpound.railmap.stnlife6.LifeCommand.command(world, sender, args)) return;   // batch 6c #11-15: /irextras stnlife selftest|bus|taxi|hotel|pub|lift
        if (com.dogpound.railmap.steam.SteamCommand.command(world, sender, args)) return;   // batch 5 #2 steam depot: /irextras steam [plant|scale|ash|log|selftest]
        if (com.dogpound.railmap.freight6.F6Command.command(world, sender, args)) return;   // batch 6 #26-30: /irextras grain|tankfarm|warehouse|lockers|holiday [..|selftest]
        if (com.dogpound.railmap.traction.TractionCommand.command(world, sender, args)) return;   // batch 5 #6 battery + hydrogen: /irextras traction ...
        if (com.dogpound.railmap.depot.DepotCommand.command(world, sender, args)) return;  // depots, fleet, crew, stations (her list 8 + 12)
        if (com.dogpound.railmap.asset.AssetCommand.command(world, sender, args)) return;  // assets, inspection, procurement, commissioning, rebuilds (48-next list)
        if (com.dogpound.railmap.infra.InfraCommand.command(world, sender, args)) return;   // batch 3 #36-50: neutral sections, sharing, storage, fault location, relays, flaws, drainage, recording, possessions, drills, advisor
        if (com.dogpound.railmap.ohle.OhleCommand.command(world, sender, args)) return;   // ohle / energy / weather (#31-33, #63-66)
        if (com.dogpound.railmap.web.TrainMapFeed.command(world, sender, args)) return;   // live train map for the website: /irextras trainmap status|build|selftest
        if (com.dogpound.railmap.modelrail.ModelRailCommand.command(world, sender, args)) return;   // model railway table: /irextras modelrail status|selftest|demo
        if (com.dogpound.railmap.freight.GantryCommand.command(world, sender, args)) return;   // batch 5 #1 yard gantry cranes: /irextras gantry list|jobs|plan|auto|press|give|selftest
        if (com.dogpound.railmap.build6a.B6Command.command(world, sender, args)) return;   // batch 6a construction: /irextras build [plan|selftest]
        if (com.dogpound.railmap.mail.MailCommand.command(world, sender, args)) return;   // batch 5 #8 mail trains: /irextras mail list|bags|offices|paper|write|trace|po|selftest
        if (com.dogpound.railmap.drone.DroneCommand.command(world, sender, args)) return;   // IR Extras #2 + #4: /irextras drone ... | wxtrain ...
        if (com.dogpound.railmap.interior.InteriorCommand.command(world, sender, args)) return;   // walk-in train interiors: /irextras interior list|info|fit|parts|click|sit|water|selftest
        if (com.dogpound.railmap.cctv.CctvCommand.command(world, sender, args)) return;   // CCTV: /irextras cctv list|cam N|nvr|events|marks N|snap N|ptz|preset|selftest
        if (com.dogpound.railmap.newrail6.NR6Command.command(world, sender, args)) return;   // batch 6 #16-20: /irextras rack|ferry|motorail|garden|avalanche list|...|selftest, nr6 selftest
        if (com.dogpound.railmap.ride.RideCommand.command(world, sender, args)) return;   // batch 5 #5 ropeways + funiculars: /irextras ride list|line N|start|stop|estop|reset|wind|evac|speed|remove|selftest
        if (com.dogpound.railmap.cine.CineCommand.command(world, sender, args)) return;
        if (com.dogpound.railmap.ops6.Ops6Command.command(world, sender, args)) return;   // batch 6 E #21-25: /irextras rco | crane | lube | masterclock | trainsim [... selftest]   // batch 5 #10 cinematic camera: /irextras cine tripods|desks|selftest
        if (com.dogpound.railmap.ctl.CtlCommand.command(world, sender, args)) return;     // next-48 grid / control / comms (#20-44): /irextras ctl ...
        if (com.dogpound.railmap.os.PrideOs.command(world, sender, args)) return;   // Pride OS desk computers: /irextras os selftest
        if (com.dogpound.railmap.light.LightCommand.command(world, sender, args)) return;
        if (com.dogpound.railmap.cabinet.Cabinet.command(world, sender, args)) return;   // opening cabinets: /irextras cabinet list|specs|x y z status|selftest|door|mcb|fuse|blow|reset   // night lighting: /irextras light status|selftest|lux|flicker|fail|repair|flood
        if (args.length > 1 && (args[0].equalsIgnoreCase("drive") || args[0].equalsIgnoreCase("fill"))) {   // test: nearest loco / tank car
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            net.minecraft.util.math.Vec3d at = sender.getPositionVector();
            cam72cam.immersiverailroading.entity.EntityRollingStock best = null;
            double bd = 64 * 64;
            for (cam72cam.immersiverailroading.entity.EntityRollingStock rs : w.getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                boolean want = args[0].equalsIgnoreCase("drive") ? rs instanceof cam72cam.immersiverailroading.entity.Locomotive
                        : rs instanceof cam72cam.immersiverailroading.entity.FreightTank && !(rs instanceof cam72cam.immersiverailroading.entity.Locomotive);
                double d = rs.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(at.x, at.y, at.z));
                if (want && d < bd) { bd = d; best = rs; }
            }
            if (best == null) { say(sender, "None within 64 blocks."); return; }
            if (args[0].equalsIgnoreCase("fill")) {
                cam72cam.immersiverailroading.entity.FreightTank t = (cam72cam.immersiverailroading.entity.FreightTank) best;
                cam72cam.mod.fluid.Fluid f = cam72cam.mod.fluid.Fluid.getFluid(args[1]);
                if (f == null) { say(sender, "No fluid " + args[1]); return; }
                t.theTank.fill(new cam72cam.mod.fluid.FluidStack(f, t.theTank.getCapacity()), false);
                say(sender, "Filled " + best.getDefinition().name() + " with " + t.theTank.getContents().getAmount() + " mB of " + args[1]);
                return;
            }
            cam72cam.immersiverailroading.entity.Locomotive l = (cam72cam.immersiverailroading.entity.Locomotive) best;
            if (l instanceof cam72cam.immersiverailroading.entity.LocomotiveDiesel) {
                cam72cam.immersiverailroading.entity.LocomotiveDiesel d = (cam72cam.immersiverailroading.entity.LocomotiveDiesel) l;
                for (String fuel : new String[]{"diesel", "oil", "biodiesel", "fuel", "ethanol", "gasoline", "lava"}) {
                    cam72cam.mod.fluid.Fluid f = cam72cam.mod.fluid.Fluid.getFluid(fuel);
                    if (f != null && d.theTank.getContents().getAmount() == 0) d.theTank.fill(new cam72cam.mod.fluid.FluidStack(f, d.theTank.getCapacity()), false);
                }
                d.setTurnedOn(true);
            }
            l.setHandBrake(0);
            l.setIndependentBrake(0);
            l.setTrainBrake(0);
            if (args.length > 2) l.setReverser(Float.parseFloat(args[2]));
            l.setThrottle(Float.parseFloat(args[1]));
            say(sender, String.format(java.util.Locale.ROOT, "%s: throttle %.2f reverser %.2f, %.0f km/h", l.getDefinition().name(), l.getThrottle(), l.getReverser(),
                    l.getCurrentSpeed() == null ? 0 : l.getCurrentSpeed().metric()));
            return;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("stock")) {        // test: single cars in a row (uncoupled) on the track you look at
            if (!(sender instanceof net.minecraft.entity.player.EntityPlayer ep)) { say(sender, "Players only."); return; }
            net.minecraft.util.math.RayTraceResult hit = ep.rayTrace(12, 1f);
            if (hit == null || hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) { say(sender, "Look at the track first."); return; }
            java.util.List<String> ids = java.util.Arrays.asList(args).subList(1, args.length);
            int placed = ConsistPlacer.placeDefs(cam72cam.mod.world.World.get(world), hit.getBlockPos(), ep.rotationYawHead, ids);
            say(sender, "Placed " + placed + " of " + ids.size());
            return;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("track")) {        // test: lay IR track where you look. track <length> [turn degrees] [left|right]
            if (!(sender instanceof net.minecraft.entity.player.EntityPlayer ep)) { say(sender, "Players only."); return; }
            net.minecraft.util.math.RayTraceResult hit = ep.rayTrace(12, 1f);
            if (hit == null || hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) { say(sender, "Look at the ground first."); return; }
            int len = Integer.parseInt(args[1]);
            float deg = args.length > 2 ? Float.parseFloat(args[2]) : 0;
            boolean left = args.length > 3 && args[3].equalsIgnoreCase("left");
            cam72cam.mod.item.ItemStack bp = new cam72cam.mod.item.ItemStack(cam72cam.immersiverailroading.IRItems.ITEM_TRACK_BLUEPRINT, 1);
            cam72cam.immersiverailroading.items.nbt.RailSettings.from(bp).with(b -> {
                b.type = deg > 0 ? cam72cam.immersiverailroading.library.TrackItems.TURN : cam72cam.immersiverailroading.library.TrackItems.STRAIGHT;
                b.length = len;
                if (deg > 0) b.degrees = deg;
                b.direction = left ? cam72cam.immersiverailroading.library.TrackDirection.LEFT : cam72cam.immersiverailroading.library.TrackDirection.RIGHT;
            }).write(bp);
            net.minecraft.item.ItemStack was = ep.getHeldItemMainhand();
            ep.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, bp.internal);
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            cam72cam.mod.entity.Player pl = w.getEntity(ep.getUniqueID(), cam72cam.mod.entity.Player.class);
            net.minecraft.util.math.BlockPos b = hit.getBlockPos();
            cam72cam.immersiverailroading.IRItems.ITEM_TRACK_BLUEPRINT.onClickBlock(pl, w, new cam72cam.mod.math.Vec3i(b.getX(), b.getY(), b.getZ()),
                    cam72cam.mod.entity.Player.Hand.PRIMARY, cam72cam.mod.util.Facing.UP,
                    new cam72cam.mod.math.Vec3d(hit.hitVec.x - b.getX(), hit.hitVec.y - b.getY(), hit.hitVec.z - b.getZ()));
            ep.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, was);
            say(sender, "Laid " + (deg > 0 ? deg + " degree turn" : "straight") + ", length " + len);
            return;
        }
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
        if (args.length > 1 && args[0].equalsIgnoreCase("send")) {         // test: nearest loco driverless to x y z [kmh] | send off
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            net.minecraft.util.math.Vec3d at = sender.getPositionVector();
            cam72cam.immersiverailroading.entity.Locomotive best = null;
            double bd = 64 * 64;
            for (cam72cam.immersiverailroading.entity.Locomotive l : w.getEntities(cam72cam.immersiverailroading.entity.Locomotive.class)) {
                double d = l.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(at.x, at.y, at.z));
                if (d < bd && l.wreck == 0) { bd = d; best = l; }
            }
            if (best == null) { say(sender, "No loco within 64 blocks."); return; }
            com.dogpound.railmap.auto.RailwayData data = com.dogpound.railmap.auto.RailwayData.get(world);
            if (args[1].equalsIgnoreCase("status")) {
                com.dogpound.railmap.auto.Autopilot.Run r = com.dogpound.railmap.auto.Autopilot.run(best.getUUID());
                say(sender, String.format(java.util.Locale.ROOT, "%s: %s | reverser %.1f throttle %.2f brake %.2f yaw %.0f | delay %d s", best.getDefinition().name(),
                        r == null ? "not driverless" : r.status, best.getReverser(), best.getThrottle(), best.getTrainBrakePos(), best.getRotationYaw(),
                        com.dogpound.railmap.auto.Disruptions.delaySeconds(best.getUUID())));
                return;
            }
            if (args[1].equalsIgnoreCase("off")) {
                data.removeTrain(best.getUUID());
                com.dogpound.railmap.auto.Autopilot.release(world, best.getUUID());
                say(sender, best.getDefinition().name() + " back to manual.");
                return;
            }
            if (best instanceof cam72cam.immersiverailroading.entity.LocomotiveDiesel d) d.setTurnedOn(true);
            com.dogpound.railmap.auto.RailwayData.AutoTrain t = data.setTrain(best.getUUID());
            t.mode = com.dogpound.railmap.auto.RailwayData.Mode.SEND;
            t.home = new net.minecraft.util.math.BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3])).toLong();
            if (args.length > 4) t.maxKmh = Integer.parseInt(args[4]);
            data.markDirty();
            say(sender, best.getDefinition().name() + " sent to " + args[1] + " " + args[2] + " " + args[3]);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("incidents")) {    // disruption recovery: list / clear <id> / wreck [1-3] (test)
            int dim = world.provider.getDimension();
            if (args.length > 2 && args[1].equalsIgnoreCase("clear")) {
                say(sender, com.dogpound.railmap.auto.Disruptions.forceClear(world, Integer.parseInt(args[2])) ? "Incident cleared." : "No such open incident.");
                return;
            }
            if (args.length > 1 && args[1].equalsIgnoreCase("wreck")) {
                if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
                cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
                net.minecraft.util.math.Vec3d at = sender.getPositionVector();
                cam72cam.immersiverailroading.entity.EntityMoveableRollingStock best = null;
                double bd = 32 * 32;
                for (cam72cam.immersiverailroading.entity.EntityMoveableRollingStock rs : w.getEntities(cam72cam.immersiverailroading.entity.EntityMoveableRollingStock.class)) {
                    double d = rs.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(at.x, at.y, at.z));
                    if (d < bd) { bd = d; best = rs; }
                }
                if (best == null) { say(sender, "No rolling stock within 32 blocks."); return; }
                int sev = args.length > 2 ? Integer.parseInt(args[2]) : 1;
                cam72cam.immersiverailroading.wreck.Wrecks.forceWreck(best, Math.max(1, Math.min(3, sev)), "test command");
                say(sender, "Wrecked " + best.getDefinition().name() + " (severity " + sev + ")");
                return;
            }
            java.util.List<com.dogpound.railmap.auto.Disruptions.Incident> list = com.dogpound.railmap.auto.Disruptions.incidents(dim);
            if (list.isEmpty()) { say(sender, "No incidents - the railway is running normally."); return; }
            for (com.dogpound.railmap.auto.Disruptions.Incident in : list)
                say(sender, String.format(java.util.Locale.ROOT, "#%d %s %s at %d %d %d: %s, %d train(s) held, %d s", in.id, in.kind, in.what,
                        Math.round(in.x), Math.round(in.y), Math.round(in.z), in.phase.label, in.held.size(), (world.getTotalWorldTime() - in.started) / 20));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("traction")) {     // traction load flow: nearest electric loco; traction brake <0..1>
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            net.minecraft.util.math.Vec3d at = sender.getPositionVector();
            cam72cam.immersiverailroading.entity.LocomotiveDiesel best = null;
            double bd = 96 * 96;
            for (cam72cam.immersiverailroading.entity.LocomotiveDiesel l : w.getEntities(cam72cam.immersiverailroading.entity.LocomotiveDiesel.class)) {
                double d = l.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(at.x, at.y, at.z));
                if (d < bd) { bd = d; best = l; }
            }
            if (best == null) { say(sender, "No diesel / electric loco within 96 blocks."); return; }
            if (args.length > 2 && args[1].equalsIgnoreCase("brake")) {
                best.setThrottle(0);
                best.setTrainBrake(Float.parseFloat(args[2]));
            }
            RailProps.Props pr = RailProps.get(world).of(best.getUUID());
            say(sender, String.format(java.util.Locale.ROOT, "%s: %.0f km/h, throttle %.2f, brake %.2f, dyn %.2f | limit %.2f | %s | draw %d kW, regen %d kW -> %s",
                    best.getDefinition().name(), Math.abs(best.getCurrentSpeed().metric()), best.getThrottle(), best.getTrainBrakePos(), best.getDynamicBrake(),
                    best.prideTractionLimit, Electric.status(world, best), pr.kwDraw, pr.kwRegen, pr.regenTo.isEmpty() ? "-" : pr.regenTo));
            cam72cam.mod.math.Vec3d lp = best.getPosition();
            String why = com.dogpound.railmap.grid.GridTicker.whyDead(world, new net.minecraft.util.math.BlockPos(lp.x, lp.y, lp.z), true);
            if (!why.isEmpty()) say(sender, "Overhead line: " + why);
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
            if (args.length > 6 && args[1].equalsIgnoreCase("set")) {            // set any block's setting (command blocks, tests)
                if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
                net.minecraft.util.math.BlockPos bp;
                try { bp = parseBlockPos(sender, args, 2, false); } catch (net.minecraft.command.CommandException e) { say(sender, "Bad position"); return; }
                if (!(world.getTileEntity(bp) instanceof com.dogpound.railmap.settings.ISettingsHolder h)) { say(sender, "Nothing with settings at " + bp); return; }
                String v = String.join(" ", java.util.Arrays.copyOfRange(args, 6, args.length));
                h.settings().put(args[5], v);
                h.onSettingsChanged(args[5]);
                say(sender, args[5] + " = " + v);
                return;
            }
            if (args.length > 5 && args[1].equalsIgnoreCase("press")) {          // press a machine's panel control (command blocks, tests)
                if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
                net.minecraft.util.math.BlockPos bp;
                try { bp = parseBlockPos(sender, args, 2, false); } catch (net.minecraft.command.CommandException e) { say(sender, "Bad position"); return; }
                if (!(world.getTileEntity(bp) instanceof com.dogpound.railmap.grid.TileGrid t)) { say(sender, "No grid machine at " + bp); return; }
                String r = t.wiredPress(args[5], "command");
                say(sender, r == null || r.isEmpty() ? "ok" : r);
                return;
            }
            if (com.dogpound.railmap.grid.GridCommands.run(world, sender, args)) return;   // protection / automation: study, log, pq, permits, fault...
            if (d.nets().isEmpty()) { say(sender, "No power grid built in this dimension."); return; }
            for (com.dogpound.railmap.grid.GridData.Net n : d.nets())
                say(sender, String.format(java.util.Locale.ROOT, "Network #%d: %d parts, %,d FE, load %d FE/t - %s", n.id, n.nodes.size(), n.energy,
                        n.lastSecond / 20, n.problem.isEmpty() ? "LIVE (" + (n.rectifier ? "DC " : "") + (n.traction ? "AC" : "") + ")" : n.problem));
            return;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("alarm")) {          // raise an alarm (command blocks, tests); "clear" acks + resets all
            if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
            if (args[1].equalsIgnoreCase("clear")) { com.dogpound.railmap.alarm.AlarmManager m = com.dogpound.railmap.alarm.AlarmManager.get(world); m.ack(-1, sender.getName()); m.reset(); say(sender, "All alarms acknowledged and reset"); return; }
            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            com.dogpound.railmap.program.ProgramData.get(world).alarm(world, text, sender.getPosition());
            say(sender, "Alarm raised: " + text);
            return;
        }
        if (com.dogpound.railmap.crew.CrewCommand.command(world, sender, args)) return;   // batch 5 #7 #9: crew NPCs, rosters, ticket inspectors
        if (com.dogpound.railmap.travel6.TravelCommand.command(world, sender, args)) return;   // batch 6b #6-10: Pride Card, phone, left luggage, baggage, park & ride
        if (com.dogpound.railmap.pax.PaxCommand.command(world, sender, args)) return;   // visible passengers: pax selftest | people | graph | clear | add
        if (args.length > 0 && com.dogpound.railmap.economy.EconCommand.handles(args[0])) { com.dogpound.railmap.economy.EconCommand.run(server, sender, world, args); return; }   // railway economy (her list #46-50)
        if (args.length > 0 && args[0].equalsIgnoreCase("biz")) { com.dogpound.railmap.biz.BizCommand.run(sender, world, args); return; }   // world + business layer ("next 48" #11-47)
        if (args.length > 0 && args[0].equalsIgnoreCase("quest")) { com.dogpound.railmap.api.QuestEvents.command(sender, world, args); return; }   // quest hooks: fire a test event / read a state
        if (args.length > 0 && args[0].equalsIgnoreCase("industry")) { com.dogpound.railmap.industry.IndustryCommand.run(sender, world, args); return; }   // her list section 13: plants, orders, cargo rules, supply
        if (args.length > 0 && args[0].equalsIgnoreCase("contracts")) {   // freight contracts: list / post one now / deliver (tests)
            com.dogpound.railmap.freight.ContractData cd = com.dogpound.railmap.freight.ContractData.get(world);
            java.util.Set<Long> ind = com.dogpound.railmap.industry.TileIndustry.LOADED.get(world.provider.getDimension());
            if (args.length > 1 && args[1].equalsIgnoreCase("post")) {
                if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
                com.dogpound.railmap.freight.Contract k = cd.post(world);
                say(sender, k == null ? "Nothing to post: " + (ind == null ? 0 : ind.size()) + " industries loaded, no producer/consumer pair free" : "Posted #" + k.id + ": " + k.qty + " " + k.itemName + " " + k.fromName + " -> " + k.toName);
                return;
            }
            say(sender, (ind == null ? 0 : ind.size()) + " industries loaded · " + cd.all().size() + " contracts:");
            long now = world.getTotalWorldTime();
            for (com.dogpound.railmap.freight.Contract k : cd.board())
                say(sender, " #" + k.id + " " + k.state.label + " · " + k.qty + " " + k.itemName + " · " + k.fromName + " -> " + k.toName + " · loaded " + k.loaded + " delivered " + k.delivered
                        + " · $" + com.dogpound.railmap.freight.Contract.money(k.payNow(now)) + " · " + k.timeLeft(now) + (k.byName.isEmpty() ? "" : " · " + k.byName));
            return;
        }
        if (args.length > 4 && args[0].equalsIgnoreCase("press")) {        // work a machine's modelled control (tests, command blocks)
            if (!sender.canUseCommand(2, getName())) { say(sender, "Needs op."); return; }
            net.minecraft.util.math.BlockPos bp;
            try { bp = parseBlockPos(sender, args, 1, false); } catch (net.minecraft.command.CommandException e) { say(sender, "Bad position"); return; }
            if (!(world.getTileEntity(bp) instanceof com.dogpound.railmap.controls.IControlled m)) { say(sender, "No working controls at " + bp); return; }
            if (args[4].equals("?")) {
                StringBuilder b = new StringBuilder();
                for (com.dogpound.railmap.grid.Panel.C c : m.layout().all.cs) b.append(c.id).append('(').append(c.t.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(m.controlValue(c.id)).append(") ");
                say(sender, b.length() == 0 ? "no controls" : b.toString());
                return;
            }
            say(sender, com.dogpound.railmap.controls.IControlled.operate(m, args[4], null));
            return;
        }
        if (args.length > 3 && args[0].equalsIgnoreCase("wired")) {        // 12 V terminal of one piece of gear, or a console's whole bus
            net.minecraft.util.math.BlockPos bp;
            try { bp = parseBlockPos(sender, args, 1, false); } catch (net.minecraft.command.CommandException e) { say(sender, "Bad position"); return; }
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(bp);
            if (te instanceof com.dogpound.railmap.block.TileDispatcherBoard b) {
                say(sender, "Console: desk " + (b.deskOn() ? "12 V ok" : "DEAD (no 12 V)") + " · bus:");
                for (net.minecraft.util.math.BlockPos p : b.bus())
                    if (world.getTileEntity(p) instanceof com.dogpound.railmap.wire.IWiredMachine m)
                        say(sender, " · " + com.dogpound.railmap.wire.WireNet.name(m.ctlWire()) + " " + m.wiredName() + " lamp " + m.wiredLamp() + (m instanceof com.dogpound.railmap.wire.IWiredGear g ? " - " + g.gearState() : "") + " @ " + p.getX() + " " + p.getY() + " " + p.getZ());
                return;
            }
            com.dogpound.railmap.grid.GridData gd = com.dogpound.railmap.grid.GridData.get(world);
            if (gd.node(bp) != null) {
                com.dogpound.railmap.grid.GridData.Node nd = gd.node(bp);
                com.dogpound.railmap.grid.GridData.Net nn = gd.netOf(bp);
                say(sender, "Grid " + nd.kind + " facing " + nd.facing + " · net " + (nn == null ? "none" : "#" + nn.id + " (" + nn.nodes.size() + " parts, "
                        + (nn.level == null ? "no level" : nn.level.label) + ") " + (nn.problem.isEmpty() ? "LIVE" : nn.problem)));
                for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.HORIZONTALS) {
                    com.dogpound.railmap.grid.GridData.Net fn = gd.netAt(bp, f);
                    say(sender, "  wire on " + f + ": " + (fn == null ? "none" : "#" + fn.id));
                }
                return;
            }
            if (te instanceof com.dogpound.railmap.wire.IWiredGear g) {
                say(sender, g.wiredName() + ": " + g.gearState() + " · " + g.term().status() + "§r · control wire " + (g.ctlWire() < 0 ? "none" : com.dogpound.railmap.wire.WireNet.name(g.ctlWire())));
                return;
            }
            say(sender, "No wired gear or console at " + bp);
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
        if (args.length > 0 && args[0].equalsIgnoreCase("mbdebug")) {      // moving block: what the nearest loco sees
            cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
            net.minecraft.util.math.Vec3d at = sender.getPositionVector();
            cam72cam.immersiverailroading.entity.Locomotive best = null;
            double bd = 64 * 64;
            for (cam72cam.immersiverailroading.entity.Locomotive l : umc.getEntities(cam72cam.immersiverailroading.entity.Locomotive.class)) {
                double d = l.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(at.x, at.y, at.z));
                if (d < bd) { bd = d; best = l; }
            }
            if (best == null) { say(sender, "No loco within 64 blocks."); return; }
            com.dogpound.railmap.auto.MovingBlock.Auth a = com.dogpound.railmap.auto.MovingBlock.last(best.getUUID());
            if (a == null) { say(sender, "No authority worked out for it yet (is it moving / ridden / driverless?)"); return; }
            say(sender, String.format(java.util.Locale.ROOT, "%s: head %.1f eoa %.1f go %.1f gap %.1f permitted %.0f by '%s' pathLen %.1f steps %d",
                    best.getDefinition().name(), a.head, a.eoa, a.distanceToGo, a.gapM, Math.min(999, a.permittedKmh), a.limitedBy, a.path.length, a.path.steps.size()));
            for (com.dogpound.railmap.auto.TrackPath.Step st : a.path.steps) {
                double[] p = st.points;
                say(sender, String.format(java.util.Locale.ROOT, "  step %s to %.1f pts %d first %s last %s", st.pos, st.distance, p == null ? 0 : p.length / 3,
                        p == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f,%.1f", p[0], p[2]),
                        p == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f,%.1f", p[p.length - 3], p[p.length - 1])));
            }
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cbtc")) {         // moving-block signalling per line: cbtc [line|all] [on|off]
            com.dogpound.railmap.auto.RailwayData data = com.dogpound.railmap.auto.RailwayData.get(world);
            if (args.length < 3) {
                StringBuilder sb = new StringBuilder("Moving block (CBTC) lines: ");
                for (com.dogpound.railmap.auto.RailwayData.Line l : data.lines().values())
                    sb.append(l.name).append(l.movingBlock ? " [CBTC]" : " [fixed block]").append("  ");
                say(sender, data.lines().isEmpty() ? "No lines yet." : sb.toString() + " - /irextras cbtc <line|all> <on|off>");
                return;
            }
            boolean on = args[args.length - 1].equalsIgnoreCase("on");
            String name = String.join(" ", java.util.Arrays.asList(args).subList(1, args.length - 1));
            int n = 0;
            for (com.dogpound.railmap.auto.RailwayData.Line l : data.lines().values()) {
                if (name.equalsIgnoreCase("all") || l.name.equalsIgnoreCase(name)) { l.movingBlock = on; n++; }
            }
            data.markDirty();
            say(sender, n == 0 ? "No line called " + name : (on ? "Moving block ON" : "Moving block OFF (back to fixed blocks)") + " for " + n + " line(s).");
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
