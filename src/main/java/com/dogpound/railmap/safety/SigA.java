package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.*;

/**
 * Signalling batch 3, Part A: #11 trip cocks, #12 balises, #13 cab signal acknowledgement (auto.DriverAids), #20
 * independent safety supervisor. The entry points SigBlocks / SigTicker / SigCommand / SigConsole / SigHooks call, and
 * the shared bit both trackside devices need: which locomotives went past a point since the last look.
 */
final class SigA {
    private SigA() {}

    static List<Block> blocks() {
        return Arrays.asList(
                new SafetyBlock("train_stop", TileTripCock::new, new double[]{2, 0, 2, 14, 12, 14},
                        "Train-stop trip cock: the arm rises when its signal is at danger and trips the brakes of a train that passes it",
                        "Wrench-link its signal. Isolation key, TEST, SCTS speed-trap mode. Air motor on 12 V; no 12 V = arm up"),
                new SafetyBlock("balise", TileBalise::new, new double[]{0, 0, 0, 16, 10, 16},
                        "Balise group + LEU: sends a speed limit / end of authority / route data to every train that passes",
                        "PROGRAM key, then speed, offset, mode, direction. SWITCHED mode reads a wrench-linked signal. 12 V"),
                new SafetyBlock("safety_supervisor", TileSafetySupervisor::new, new double[]{1, 0, 1, 15, 16, 15},
                        "Independent safety supervisor (2oo2): checks every driverless train against its own authority envelope",
                        "MONITOR logs and alarms; ENFORCE brakes and holds the train until RESET. Coverage 256 / 1024 m / all. 12 V"));
    }

    static void registerTiles() {
        reg(TileTripCock.class, "train_stop");
        reg(TileBalise.class, "balise");
        reg(TileSafetySupervisor.class, "safety_supervisor");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(com.dogpound.railmap.RailMap.MODID, id));
    }

    // ---- locomotives passing points ---------------------------------------------------------------------------------

    /** one locomotive's move since the last look (every 5 ticks) */
    static final class Pass {
        final Locomotive loco;
        final double x0, y0, z0, x, y, z, speed;
        Pass(Locomotive l, double[] last, double x, double y, double z, double speed) {
            loco = l; this.x = x; this.y = y; this.z = z; this.speed = speed;
            x0 = last == null ? x : last[0]; y0 = last == null ? y : last[1]; z0 = last == null ? z : last[2];
        }
        /** the move went past (within r horizontally, 3 m vertically) the point px, py, pz */
        boolean passed(double px, double py, double pz, double r) {
            if (Math.abs(y - py) > 3 && Math.abs(y0 - py) > 3) return false;
            if (x == x0 && z == z0) return false;                                // standing: nothing passed
            return segD2(x0, z0, x, z, px, pz) <= r * r;
        }
        /** heading of the move */
        double hx() { double l = Math.hypot(x - x0, z - z0); return l < 1e-6 ? 0 : (x - x0) / l; }
        double hz() { double l = Math.hypot(x - x0, z - z0); return l < 1e-6 ? 0 : (z - z0) / l; }
    }

    private static final Map<Integer, Map<UUID, double[]>> LAST = new HashMap<>();

    /** squared distance from p to the segment a-b (the swept test: fast trains cover 10 m between looks) */
    static double segD2(double ax, double az, double bx, double bz, double px, double pz) {
        double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / l2));
        double cx = ax + t * dx - px, cz = az + t * dz - pz;
        return cx * cx + cz * cz;
    }

    static List<EntityPlayer> riders(Locomotive l) {
        List<EntityPlayer> out = new ArrayList<>();
        List<EntityCoupleableRollingStock> cars = new ArrayList<>();
        try { cars.addAll(l.getTrain(false)); } catch (RuntimeException ignored) { }
        if (cars.isEmpty()) cars.add(l);
        for (EntityCoupleableRollingStock s : cars) for (cam72cam.mod.entity.Entity e : s.getPassengers()) if (e.internal instanceof EntityPlayer p && !out.contains(p)) out.add(p);
        return out;
    }

    static String label(Locomotive l) {
        if (l.tag != null && !l.tag.isEmpty()) return l.tag;
        return l.getDefinition() == null ? "Train" : l.getDefinition().name();
    }

    static boolean driverless(World w, UUID loco) { return com.dogpound.railmap.auto.RailwayData.get(w).train(loco) != null; }

    static Locomotive loco(World w, UUID id) {
        for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) if (!l.isDead() && l.getUUID().equals(id)) return l;
        return null;
    }

    // ---- ticks ------------------------------------------------------------------------------------------------------

    static void tick(World w) {
        TripCocks.tick(w);
        Balises.tick(w);
        Supervisor.tick(w);
    }

    static void fast(World w) {
        boolean need = !SigTicker.tiles(w, TileTripCock.class).isEmpty() || !SigTicker.tiles(w, TileBalise.class).isEmpty() || TripCocks.any();
        long now = w.getTotalWorldTime();
        if (need) {
            int dim = w.provider.getDimension();
            Map<UUID, double[]> last = LAST.computeIfAbsent(dim, k -> new HashMap<>());
            Map<UUID, double[]> seen = new HashMap<>();
            List<Pass> passes = new ArrayList<>();
            for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
                if (l.isDead()) continue;
                cam72cam.mod.math.Vec3d p = l.getPosition();
                passes.add(new Pass(l, last.get(l.getUUID()), p.x, p.y, p.z, Math.abs(l.getCurrentSpeed().metric())));
                seen.put(l.getUUID(), new double[]{p.x, p.y, p.z});
            }
            LAST.put(dim, seen);
            TripCocks.fast(w, passes, now);
            Balises.fast(w, passes, now);
        }
        if (now % 10 == 2) Supervisor.fast(w, now);
    }

    // ---- hooks ------------------------------------------------------------------------------------------------------

    static void restrictions(World w, TrackPath path, List<Object[]> out) { Balises.restrictions(w, path, out); }

    static String cabNote(UUID loco, long now) { return Balises.cabNote(loco, now); }

    // ---- command ----------------------------------------------------------------------------------------------------

    static String usage() { return "trip [list|reset <train|all>|test x y z]|balise [list|text x y z <text>|log]|cab|supervisor [status|reset]|selftest a"; }

    static boolean command(ICommandSender s, World w, String[] a) {
        String sub = a[1].toLowerCase(Locale.ROOT);
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "trip":
                if (op.equals("reset")) { say(s, TripCocks.commandReset(w, a.length > 3 ? a[3] : "all", s.getName())); return true; }
                if (op.equals("test") && a.length >= 6) {
                    BlockPos p = new BlockPos(num(a[3]), num(a[4]), num(a[5]));
                    say(s, w.getTileEntity(p) instanceof TileTripCock t ? t.controlPress("test", null) : "§cNo train stop at " + p);
                    return true;
                }
                for (TileTripCock t : SigTicker.tiles(w, TileTripCock.class)) say(s, "§e" + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ() + "§r " + t.gearState() + " · " + t.trips() + " trip(s)");
                if (SigTicker.tiles(w, TileTripCock.class).isEmpty()) say(s, "No train stops loaded");
                for (String l : TripCocks.trippedLines(w)) say(s, "§c" + l);
                return true;
            case "balise":
                if (op.equals("text") && a.length >= 7) {
                    BlockPos p = new BlockPos(num(a[3]), num(a[4]), num(a[5]));
                    if (!(w.getTileEntity(p) instanceof TileBalise b)) { say(s, "§cNo balise at " + p); return true; }
                    say(s, b.setText(String.join(" ", Arrays.copyOfRange(a, 6, a.length)), s.getName()));
                    return true;
                }
                if (op.equals("log")) { for (String l : Balises.log(w)) say(s, l); return true; }
                for (TileBalise b : SigTicker.tiles(w, TileBalise.class)) say(s, "§eBG" + b.bg() + "§r " + b.getPos().getX() + "," + b.getPos().getZ() + " · " + b.gearState() + " · " + b.passes() + " passage(s)");
                if (SigTicker.tiles(w, TileBalise.class).isEmpty()) say(s, "No balises loaded");
                return true;
            case "cab": {
                if (!(s instanceof EntityPlayer p)) { say(s, "Ride a locomotive"); return true; }
                for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class))
                    if (!l.isDead() && riders(l).contains(p)) { say(s, label(l) + ": " + com.dogpound.railmap.auto.DriverAids.cabStatus(l.getUUID(), w.getTotalWorldTime()) + " · last telegram: " + Optional.ofNullable(Balises.cabNote(l.getUUID(), w.getTotalWorldTime())).orElse("-")); return true; }
                say(s, "You are not on a locomotive");
                return true;
            }
            case "supervisor":
                if (op.equals("reset")) { say(s, Supervisor.reset(w, null, s.getName())); return true; }
                for (String l : Supervisor.status(w)) say(s, l);
                return true;
            case "selftest":
                if (!op.equals("a")) return false;
                for (String l : selfTest()) say(s, l);
                return true;
            default: return false;
        }
    }

    /** exercises part A's logic without any train: the swept test, the braking envelope, telegrams, the SCTS trap */
    static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        int[] pf = new int[2];
        check(out, pf, "swept test: 44 m/s train jumping past the arm is caught", segD2(0, -5, 0, 6, 1.5, 0) <= 9);
        check(out, pf, "swept test: train on the next track (4 m away) is not", segD2(0, -5, 0, 6, 4, 0) > 9);
        check(out, pf, "standing train passes nothing", !new Pass(null, new double[]{1, 64, 1}, 1, 64, 1, 0).passed(1, 64, 1, 3));
        check(out, pf, "envelope: 0 m to a stop = 0 km/h", com.dogpound.railmap.auto.Wayside.allowed(0, 0, 0.8) < 0.01);
        check(out, pf, "envelope: 200 m at 0.8 m/s2 to a stop = 64.4 km/h", Math.abs(com.dogpound.railmap.auto.Wayside.allowed(200, 0, 0.8) - 64.4) < 0.5);
        check(out, pf, "channel B (more conservative) never permits more than A", Supervisor.envelope(300, 40, 0.8 * Supervisor.B_FACTOR) <= Supervisor.envelope(300, 40, 0.8));
        Balises.Telegram t = Balises.build(60, 100, 0, null, "LINE A");
        check(out, pf, "FIXED telegram: 60 km/h from 100 m", t.kmh == 60 && t.offset == 100 && !t.stop);
        t = Balises.build(80, 0, 1, com.dogpound.railmap.signal.Aspect.STOP, "");
        check(out, pf, "SWITCHED at STOP = end of authority", t.stop);
        t = Balises.build(80, 0, 1, com.dogpound.railmap.signal.Aspect.APPROACH, "");
        check(out, pf, "SWITCHED at caution = 40 km/h", t.kmh == 40 && !t.stop);
        t = Balises.build(-1, 0, 1, com.dogpound.railmap.signal.Aspect.CLEAR, "");
        check(out, pf, "SWITCHED at clear, no limit programmed = no restriction", t.kmh < 0 && !t.stop);
        check(out, pf, "SCTS trap: 30 km/h past a 25 km/h trap trips", TileTripCock.sctsTrips(30, 25));
        check(out, pf, "SCTS trap: 20 km/h past a 25 km/h trap does not", !TileTripCock.sctsTrips(20, 25));
        out.add(0, (pf[1] == 0 ? "§a" : "§c") + "Part A self-test: " + pf[0] + " PASS, " + pf[1] + " FAIL");
        return out;
    }

    private static void check(List<String> out, int[] pf, String what, boolean ok) { pf[ok ? 0 : 1]++; out.add((ok ? "§a PASS " : "§c FAIL ") + "§r" + what); }

    // ---- console ----------------------------------------------------------------------------------------------------

    static void fill(World w, List<String[]> out) {
        long now = w.getTotalWorldTime();
        for (TileTripCock t : SigTicker.tiles(w, TileTripCock.class))
            out.add(new String[]{"a.tc", t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ(), t.signalName(), t.armUp() ? "UP" : "DOWN", t.modeName(), t.gearState(), String.valueOf(t.trips()), t.isolated() ? "1" : "0"});
        for (TileBalise b : SigTicker.tiles(w, TileBalise.class))
            out.add(new String[]{"a.bg", "BG" + b.bg(), b.getPos().getX() + "," + b.getPos().getZ(), b.modeName(), b.telegramText(), String.valueOf(b.passes()), b.powered() ? "1" : "0"});
        int n = 0;
        for (String l : Balises.log(w)) if (n++ < 30) out.add(new String[]{"a.ps", l});
        for (String[] r : TripCocks.trippedRows(w)) out.add(r);
        Supervisor.fill(w, out, now);
    }

    /** 20 supervisor reset (arg loco uuid or "" all), 21 trip valve reset (arg loco uuid or "" all) */
    static String op(World w, int op, String arg, String who) {
        switch (op) {
            case 20: return Supervisor.reset(w, arg.isEmpty() ? null : arg, who);
            case 21: return TripCocks.commandReset(w, arg.isEmpty() ? "all" : arg, who);
            default: return null;
        }
    }

    static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; } }

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
