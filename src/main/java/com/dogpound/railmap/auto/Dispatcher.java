package com.dogpound.railmap.auto;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * Who goes where. The autopilot drives; this decides the next stop and matches passengers
 * (tickets put into a machine) to trains.
 * <p>
 * A ticket from A to B is served by, in order of preference:
 * <ol>
 *   <li>a train already running a line or shuttle that calls at both A and B — it will be
 *       there anyway, so the passenger just waits for it;</li>
 *   <li>the nearest free <b>on-call</b> train, which is sent to A, then B, then home.</li>
 * </ol>
 * With no train able to take it the ticket waits, and is re-offered every second.
 */
public final class Dispatcher {
    private Dispatcher() {}

    /** The stop a train is heading for right now, or null if it has nowhere to go. */
    public static Long nextStop(RailwayData data, RailwayData.AutoTrain a) {
        if (!a.calls.isEmpty()) return a.calls.get(0);
        switch (a.mode) {
            case LINE, SHUTTLE -> {
                RailwayData.Line line = data.line(a.line);
                if (line == null || line.stations.isEmpty()) return null;
                if (a.index < 0 || a.index >= line.stations.size()) a.index = 0;
                return line.stations.get(a.index);
            }
            default -> {
                return a.home == 0 ? null : a.home;
            }
        }
    }

    /** After leaving {@code station}: move on to the next stop. */
    public static void advance(RailwayData data, RailwayData.AutoTrain a, long station) {
        if (!a.calls.isEmpty() && a.calls.get(0) == station) {
            a.calls.remove(0);
            return;
        }
        RailwayData.Line line = data.line(a.line);
        int n = line == null ? 0 : line.stations.size();
        switch (a.mode) {
            case LINE -> { if (n > 0) a.index = (a.index + 1) % n; }
            case SHUTTLE -> {
                if (n <= 1) { a.index = 0; return; }
                int next = a.index + a.direction;
                if (next >= n) { a.direction = -1; next = n - 2; }
                if (next < 0) { a.direction = 1; next = 1; }
                a.index = next;
            }
            default -> { /* on-call and sent trains stay put until something else is asked of them */ }
        }
    }

    // ---- passengers ----------------------------------------------------------------------

    /** A ticket went into a machine at {@code from}. Returns what the machine should say. */
    public static String ticketInserted(World world, long from, long to, String passenger) {
        RailwayData data = RailwayData.get(world);
        RailwayData.Call call = data.addCall(from, to, passenger, world.getTotalWorldTime());
        RailwayData.AutoTrain t = assign(world, data, call);
        StationData st = StationData.get(world);
        String dest = Autopilot.nameOf(st, to);
        if (t == null) {
            return "Ticket accepted to " + dest + ". No train is free yet — one will be sent as soon as it is.";
        }
        Locomotive loco = Autopilot.find(world, t.loco);
        String name = loco == null ? "Your train" : Autopilot.label(t, loco);
        return "Ticket accepted to " + dest + ". " + name + " is on its way.";
    }

    /** Once a second: offer waiting tickets to trains again, forget tickets for vanished trains. */
    static void tick(World world, RailwayData data) {
        for (RailwayData.Call c : data.calls()) {
            if (c.train != null && data.train(c.train) == null) {
                c.train = null;
                c.state = RailwayData.CallState.WAITING;
            }
            if (c.state == RailwayData.CallState.WAITING) assign(world, data, c);
        }
    }

    private static RailwayData.AutoTrain assign(World world, RailwayData data, RailwayData.Call call) {
        // 1. A line that serves both ends.
        for (RailwayData.AutoTrain t : data.trains().values()) {
            if (t.mode != RailwayData.Mode.LINE && t.mode != RailwayData.Mode.SHUTTLE) continue;
            RailwayData.Line line = data.line(t.line);
            if (line != null && line.stations.contains(call.from) && line.stations.contains(call.to)) {
                call.train = t.loco;
                call.state = RailwayData.CallState.ASSIGNED;
                data.markDirty();
                return t;
            }
        }
        // 2. The nearest on-call train that isn't already carrying someone.
        RailwayData.AutoTrain best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos from = BlockPos.fromLong(call.from);
        for (RailwayData.AutoTrain t : data.trains().values()) {
            if (t.mode != RailwayData.Mode.ON_CALL || !t.calls.isEmpty()) continue;
            Locomotive loco = Autopilot.find(world, t.loco);
            if (loco == null) continue;
            double dx = loco.getPosition().x - from.getX(), dz = loco.getPosition().z - from.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = t; }
        }
        if (best == null) return null;
        best.calls.add(call.from);
        best.calls.add(call.to);
        call.train = best.loco;
        call.state = RailwayData.CallState.ASSIGNED;
        data.markDirty();
        return best;
    }

    /** A driverless train has stopped at a station: board or set down its passengers. */
    static void arrived(World world, RailwayData data, RailwayData.AutoTrain a, long station, String train, String here) {
        List<RailwayData.Call> calls = data.calls();
        for (int i = calls.size() - 1; i >= 0; i--) {
            RailwayData.Call c = calls.get(i);
            if (!a.loco.equals(c.train)) continue;
            if (c.state == RailwayData.CallState.ASSIGNED && c.from == station) {
                c.state = RailwayData.CallState.BOARDING;
                tell(world, c.passenger, "[TRAIN] " + train + " is at " + here + " — all aboard!");
            } else if (c.state == RailwayData.CallState.RIDING && c.to == station) {
                tell(world, c.passenger, "[TRAIN] Arrived at " + here + ". Thanks for riding!");
                data.removeCall(c);
            }
        }
        data.markDirty();
    }

    static void departed(World world, RailwayData data, RailwayData.AutoTrain a, long station) {
        for (RailwayData.Call c : data.calls()) {
            if (a.loco.equals(c.train) && c.state == RailwayData.CallState.BOARDING && c.from == station) {
                c.state = RailwayData.CallState.RIDING;
            }
        }
        data.markDirty();
    }

    /** True while a driverless train is standing at this station taking on passengers. */
    public static boolean boardingAt(World world, long station) {
        for (RailwayData.AutoTrain a : RailwayData.get(world).trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r != null && r.dwelling && r.dwellStation == station) return true;
        }
        return false;
    }

    private static void tell(World world, String name, String msg) {
        if (world.getMinecraftServer() == null) return;
        EntityPlayerMP p = world.getMinecraftServer().getPlayerList().getPlayerByUsername(name);
        if (p != null) p.sendMessage(new TextComponentString(msg));
    }
}
