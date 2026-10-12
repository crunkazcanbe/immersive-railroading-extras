package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Locale;

/**
 * The train simulation's clock (items 7, 8, 12 of her ChatGPT list): consist physics, engine management and adhesion,
 * four times a second on the server. Plus the /irextras commands for it and the console's one-line status.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class TrainSim {
    private TrainSim() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 5 != 3) return;
        SimData data = SimData.get(w);
        try {
            ConsistSim.tick(w, data, now);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] consist physics pass failed: {}", ex.toString());
        }
        try {
            MultiUnit.tick(w, data, now, 0.25);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] multiple-unit pass failed: {}", ex.toString());
        }
        try {
            AuxSim.preStart(w, data, now);
            EngineSim.tick(w, data, now, 0.25);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] engine management pass failed: {}", ex.toString());
        }
        try {
            AuxSim.tick(w, data, now, 0.25);
            Appearance.tick(w, data, now);
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[IR Extras] auxiliary / appearance pass failed: {}", ex.toString());
        }
        if (now % 10 == 3) {
            try {
                AdhesionSim.tick(w, data, now, 0.5);
            } catch (LinkageError | RuntimeException ex) {
                RailMap.LOG.warn("[IR Extras] adhesion pass failed: {}", ex.toString());
            }
        }
    }

    /** the console / board's status addition for a locomotive ("" when nothing to say) */
    public static String status(World mc, EntityMoveableRollingStock s) {
        if (!IR) return "";
        try {
            StringBuilder b = new StringBuilder();
            if (s instanceof LocomotiveDiesel d) b.append(EngineSim.status(mc, d));
            SimData.Car c = SimData.get(mc).peek(s.getUUID());
            if (c != null) {
                if (c.slip) b.append(b.length() > 0 ? " · " : "").append("§eSLIP§r");
                if (c.slide) b.append(b.length() > 0 ? " · " : "").append("§eSLIDE§r");
                if (c.flatMm >= 30) b.append(b.length() > 0 ? " · " : "").append("flat ").append(Math.round(c.flatMm)).append(" mm");
                if (Math.min(c.coupler[0], c.coupler[1]) <= 0) b.append(b.length() > 0 ? " · " : "").append("§cbroken knuckle§r");
                if (c.compTrip) b.append(b.length() > 0 ? " · " : "").append("§ecompressor trip§r");
                if (c.convTrip) b.append(b.length() > 0 ? " · " : "").append("§eaux converter trip§r");
                if (!c.batteryOn) b.append(b.length() > 0 ? " · " : "").append("§7battery off§r");
                if (c.body > 0) b.append(b.length() > 0 ? " · " : "").append("body ").append(Math.round(c.body)).append("% damaged");
            }
            String mu = MultiUnit.summary(s.getUUID());
            if (!mu.isEmpty() && !MultiUnit.isFollower(s.getUUID())) b.append(b.length() > 0 ? " · " : "").append(mu);
            {
            }
            return b.toString();
        } catch (LinkageError | RuntimeException ex) {
            return "";
        }
    }

    // ---- /irextras commands -------------------------------------------------------------------------------------------

    /** handles physics / engine / adhesion / knuckle / aids; false = not one of ours */
    public static boolean command(World world, net.minecraft.command.ICommandSender sender, String[] args) {
        if (args.length == 0 || !IR) return false;
        String a = args[0].toLowerCase(Locale.ROOT);
        switch (a) {
            case "physics": {
                SimData data = SimData.get(world);
                if (args.length > 1) {
                    if (args[1].equalsIgnoreCase("default")) data.physicsMode = -1;
                    else data.physicsMode = SimConfig.modeOf(args[1]);
                    data.markDirty();
                }
                String[] names = {"OFF", "LIGHT", "FULL"};
                say(sender, "§bConsist physics for this world: §f" + names[data.mode()] + (data.physicsMode < 0 ? " §7(config default)" : "")
                        + "§7 - off | light (measure + warn) | full (couplers wear and break, buff derailments) | default");
                Locomotive l = nearestLoco(world, sender);
                if (l != null) say(sender, "§7" + ConsistSim.line(ConsistSim.summary(world, l)));
                return true;
            }
            case "engine": {
                Locomotive l = nearestLoco(world, sender);
                if (!(l instanceof LocomotiveDiesel d)) { say(sender, "§7No diesel locomotive within 16 blocks."); return true; }
                SimData data = SimData.get(world);
                SimData.Car c = data.of(d.getUUID());
                String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                switch (sub) {
                    case "reset": say(sender, EngineSim.reset(world, d)); break;
                    case "aess": c.aess = args.length > 2 ? args[2].equalsIgnoreCase("on") : !c.aess; data.markDirty(); say(sender, "§7AESS " + (c.aess ? "§aON" : "§cOFF")); break;
                    case "isolate": c.isolated = !c.isolated; data.markDirty(); say(sender, c.isolated ? "§eEngine ISOLATED (no traction)" : "§aEngine on RUN"); break;
                    case "service": EngineSim.service(c); data.markDirty(); say(sender, "§aOil, coolant, battery topped up"); break;
                    default:
                        say(sender, "§b" + EngineSim.label(d) + " §7(" + EngineSim.family(d).label + ", " + Math.round(c.hours * 10) / 10.0 + " h, " + c.starts + " starts)");
                        say(sender, "§f" + EngineSim.line1(d, c));
                        say(sender, "§f" + EngineSim.line2(d, c));
                        say(sender, String.format(Locale.ROOT, "§7oil %d%% · coolant %d%% · battery %d%% · AESS %s%s", Math.round(c.oil * 100), Math.round(c.coolant * 100),
                                Math.round(c.battery * 100), c.aess ? "on" : "off", c.isolated ? " · ISOLATED" : ""));
                        for (String f : c.faults) say(sender, "§c" + f);
                        say(sender, "§8/irextras engine reset | aess [on|off] | isolate | service");
                }
                return true;
            }
            case "adhesion": {
                Locomotive l = nearestLoco(world, sender);
                if (l == null) { say(sender, "§7No locomotive within 16 blocks."); return true; }
                SimData data = SimData.get(world);
                SimData.Car c = data.of(l.getUUID());
                if (args.length > 2) {
                    boolean on = args[2].equalsIgnoreCase("on");
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "sand": c.autoSand = on; break;
                        case "tc": case "traction": c.tractionControl = on; break;
                        case "wsp": c.wsp = on; break;
                        default: break;
                    }
                    data.markDirty();
                }
                say(sender, "§b" + EngineSim.label(l) + "§7: " + AdhesionSim.line(l, c));
                say(sender, String.format(Locale.ROOT, "§7auto sand %s · traction control %s · WSP %s%s%s", on(c.autoSand), on(c.tractionControl), on(c.wsp),
                        c.slip ? " · §eSLIPPING" : "", c.slide ? " · §eSLIDING" : ""));
                say(sender, "§8/irextras adhesion sand|tc|wsp on|off");
                return true;
            }
            case "mu": MultiUnit.command(world, sender, args); return true;
            case "aux": AuxSim.command(world, sender, args); return true;
            case "look": {
                Locomotive l = nearestLoco(world, sender);
                if (l == null) { say(sender, "§7No locomotive within 16 blocks."); return true; }
                SimData data = SimData.get(world);
                SimData.Car c = data.of(l.getUUID());
                if (args.length > 2 && args[1].equalsIgnoreCase("damage")) { c.body = Math.max(0, Math.min(100, Float.parseFloat(args[2]))); data.markDirty(); }
                if (args.length > 1 && args[1].equalsIgnoreCase("livery")) { c.livery = 1 - c.livery; data.markDirty(); }
                say(sender, "§b" + EngineSim.label(l) + "§7: " + Appearance.line(world, l));
                say(sender, "§8/irextras look damage <0-100> | livery   (test helpers; real repairs + repaints at a Formation & Livery Desk)");
                return true;
            }
            case "knuckle": {
                if (!(sender instanceof EntityPlayer p)) { say(sender, "Players only."); return true; }
                if (!p.capabilities.isCreativeMode) {
                    int iron = 0;
                    for (net.minecraft.item.ItemStack s : p.inventory.mainInventory) if (s.getItem() == net.minecraft.init.Items.IRON_INGOT) iron += s.getCount();
                    if (iron < 2) { say(sender, "§cA new knuckle takes 2 iron ingots."); return true; }
                }
                int n = ConsistSim.replaceKnuckles(world, p.getPosition(), 6);
                if (n > 0 && !p.capabilities.isCreativeMode) p.inventory.clearMatchingItems(net.minecraft.init.Items.IRON_INGOT, -1, 2, null);
                say(sender, n == 0 ? "§7Every coupler within 6 blocks is fine." : "§aReplaced " + n + " knuckle" + (n > 1 ? "s" : "") + " - couple up again");
                return true;
            }
            default:
                return false;
        }
    }

    static String on(boolean b) { return b ? "§aon§7" : "§coff§7"; }

    static void say(net.minecraft.command.ICommandSender s, String m) { s.sendMessage(new net.minecraft.util.text.TextComponentString(m)); }

    static Locomotive nearestLoco(World world, net.minecraft.command.ICommandSender sender) {
        BlockPos at = sender.getPosition();
        Locomotive best = null;
        double bd = 16 * 16;
        for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
            Vec3d p = l.getPosition();
            double d = at.distanceSqToCenter(p.x, p.y, p.z);
            if (!l.isDead() && d < bd) { bd = d; best = l; }
        }
        return best;
    }
}
