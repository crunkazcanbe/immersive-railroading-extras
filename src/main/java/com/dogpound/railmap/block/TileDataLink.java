package com.dogpound.railmap.block;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.library.SwitchState;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Dispatcher;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.auto.Protection;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.server.TrainControl;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Optional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Railroad Data Link: an OpenComputers component, {@code component.railroad}. Put it next to
 * a computer (or an OC adapter) and the whole railroad is scriptable from Lua:
 * <pre>
 * local rr = require("component").railroad
 * for _, t in ipairs(rr.getTrains()) do print(t.name, t.speed, t.status) end
 * rr.setSwitch(120, 64, -40, "turn")
 * rr.holdSignal(118, 65, -44, true)
 * rr.sendTrain(t.id, "Harbor")
 * rr.setRoute(118,65,-44, 300,65,-44)
 * rr.ticket("Downtown", "Airport", "Alex")
 * </pre>
 * Every call goes through the same code the board uses, so the interlocking still refuses a
 * conflicting route and protection still brakes a speeding train.
 * <p>
 * The OC interfaces are stripped by {@link Optional} when OpenComputers isn't installed, so the
 * block is still safe to place (it just does nothing).
 */
@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "opencomputers")
public class TileDataLink extends TileEntity implements SimpleComponent {

    @Override
    public String getComponentName() {
        return "railroad";
    }

    // ---- reading ------------------------------------------------------------------------

    @Callback(doc = "function():table -- every loaded rolling stock: id, name, x, y, z, speed (km/h), kind, lead, consist, driverless, status, nextStop, protected")
    @Optional.Method(modid = "opencomputers")
    public Object[] getTrains(Context c, Arguments a) {
        RailwayData data = RailwayData.get(world);
        Map<Integer, RailwayData.AutoTrain> auto = new HashMap<>();
        for (RailwayData.AutoTrain t : data.trains().values()) {
            Autopilot.Run r = Autopilot.run(t.loco);
            if (r != null && r.entityId >= 0) auto.put(r.entityId, t);
        }
        List<Object> out = new ArrayList<>();
        for (TrainNode t : TrainTracker.latest(world)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.id);
            m.put("name", t.tag == null || t.tag.isEmpty() ? t.name : t.tag);
            m.put("x", t.x); m.put("y", t.y); m.put("z", t.z);
            m.put("speed", t.speedKmh);
            m.put("kind", t.kind.name().toLowerCase());
            m.put("lead", t.lead);
            m.put("consist", t.consist);
            m.put("throttle", t.throttle);
            m.put("brake", t.brake);
            RailwayData.AutoTrain at = auto.get(t.id);
            m.put("driverless", at != null);
            if (at != null) {
                Autopilot.Run r = Autopilot.run(at.loco);
                m.put("mode", at.mode.name().toLowerCase());
                m.put("line", at.line);
                m.put("status", r == null ? "" : r.status);
                m.put("nextStop", r == null ? "" : r.nextStopName);
            }
            out.add(m);
        }
        return new Object[]{ out };
    }

    @Callback(doc = "function():table -- named stations: name, x, y, z, waiting passengers")
    @Optional.Method(modid = "opencomputers")
    public Object[] getStations(Context c, Arguments a) {
        RailwayData data = RailwayData.get(world);
        List<Object> out = new ArrayList<>();
        for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            Map<String, Object> m = new HashMap<>();
            m.put("name", e.getValue());
            m.put("x", p.getX()); m.put("y", p.getY()); m.put("z", p.getZ());
            m.put("waiting", data.waitingAt(e.getKey()));
            out.add(m);
        }
        return new Object[]{ out };
    }

    @Callback(doc = "function():table -- lines: name, stations {names}")
    @Optional.Method(modid = "opencomputers")
    public Object[] getLines(Context c, Arguments a) {
        StationData st = StationData.get(world);
        List<Object> out = new ArrayList<>();
        for (RailwayData.Line l : RailwayData.get(world).lines().values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", l.name);
            List<String> names = new ArrayList<>();
            for (long k : l.stations) names.add(Autopilot.nameOf(st, k));
            m.put("stations", names);
            out.add(m);
        }
        return new Object[]{ out };
    }

    @Callback(doc = "function():table -- signal masts: x, y, z, aspect, style, mode, clearBlocks, routeSet")
    @Optional.Method(modid = "opencomputers")
    public Object[] getSignals(Context c, Arguments a) {
        List<Object> out = new ArrayList<>();
        for (TileSignalMast m : SignalRegistry.masts(world.provider.getDimension())) {
            Map<String, Object> t = new HashMap<>();
            t.put("x", m.getPos().getX()); t.put("y", m.getPos().getY()); t.put("z", m.getPos().getZ());
            t.put("aspect", m.aspect().label);
            t.put("style", m.style().label);
            t.put("mode", m.mode().name().toLowerCase());
            t.put("clearBlocks", m.clearBlocks());
            t.put("routeSet", m.routeSet());
            out.add(t);
        }
        return new Object[]{ out };
    }

    // ---- control ------------------------------------------------------------------------

    @Callback(doc = "function(x,y,z, state:string):boolean,string -- throw a switch: straight, turn or auto")
    @Optional.Method(modid = "opencomputers")
    public Object[] setSwitch(Context c, Arguments a) {
        BlockPos p = new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2));
        String s = a.checkString(3).toLowerCase();
        SwitchState want = s.startsWith("t") ? SwitchState.TURN : s.startsWith("s") ? SwitchState.STRAIGHT : SwitchState.NONE;
        if (Interlocking.isLocked(world, p)) return new Object[]{ false, "locked by a route or a driverless train" };
        if (want == SwitchState.NONE) {
            boolean ok = Interlocking.setAuto(world, p);
            return new Object[]{ ok, ok ? "switch on auto (redstone)" : "no switch there" };
        }
        boolean ok = Interlocking.claim(world, new cam72cam.mod.math.Vec3i(p.getX(), p.getY(), p.getZ()),
                want, "oc@" + pos.toLong());
        Interlocking.release(world, new cam72cam.mod.math.Vec3i(p.getX(), p.getY(), p.getZ()), "oc@" + pos.toLong());
        return new Object[]{ ok, ok ? "switch set" : "occupied or locked" };
    }

    @Callback(doc = "function(x,y,z, hold:boolean):boolean -- hold a signal at Stop (true) or release it")
    @Optional.Method(modid = "opencomputers")
    public Object[] holdSignal(Context c, Arguments a) {
        BlockPos p = new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2));
        if (!(world.getTileEntity(p) instanceof TileSignalMast m)) return new Object[]{ false };
        m.setRelayControl(a.checkBoolean(3), null);
        return new Object[]{ true };
    }

    @Callback(doc = "function(x1,y1,z1, x2,y2,z2):string -- CTC route from one signal to another")
    @Optional.Method(modid = "opencomputers")
    public Object[] setRoute(Context c, Arguments a) {
        return new Object[]{ Interlocking.setRoute(world,
                new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2)),
                new BlockPos(a.checkInteger(3), a.checkInteger(4), a.checkInteger(5))) };
    }

    @Callback(doc = "function(x,y,z):string -- cancel the route starting at this signal")
    @Optional.Method(modid = "opencomputers")
    public Object[] cancelRoute(Context c, Arguments a) {
        return new Object[]{ Interlocking.cancelRoute(world, new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2))) };
    }

    @Callback(doc = "function(id, command:string):string -- throttle_up, throttle_down, brake_up, brake_down, forward, neutral, reverse, horn, bell, emergency_stop")
    @Optional.Method(modid = "opencomputers")
    public Object[] trainCommand(Context c, Arguments a) {
        String cmd = a.checkString(1).toUpperCase().replace(' ', '_');
        TrainControl.Cmd k;
        switch (cmd) {
            case "FORWARD" -> k = TrainControl.Cmd.REVERSER_FORWARD;
            case "NEUTRAL" -> k = TrainControl.Cmd.REVERSER_NEUTRAL;
            case "REVERSE" -> k = TrainControl.Cmd.REVERSER_REVERSE;
            default -> {
                try {
                    k = TrainControl.Cmd.valueOf(cmd);
                } catch (IllegalArgumentException e) {
                    return new Object[]{ "unknown command" };
                }
            }
        }
        return new Object[]{ TrainControl.apply(world, null, a.checkInteger(0), k) };
    }

    @Callback(doc = "function(id, station:string):string -- make the train driverless and send it to a station")
    @Optional.Method(modid = "opencomputers")
    public Object[] sendTrain(Context c, Arguments a) {
        Locomotive loco = loco(a.checkInteger(0));
        if (loco == null) return new Object[]{ "train not loaded" };
        Long key = station(a.checkString(1));
        if (key == null) return new Object[]{ "no station by that name" };
        RailwayData data = RailwayData.get(world);
        RailwayData.AutoTrain t = data.setTrain(loco.getUUID());
        t.mode = RailwayData.Mode.SEND;
        t.home = key;
        t.calls.clear();
        if (t.label.isEmpty()) t.label = Autopilot.label(t, loco);
        data.markDirty();
        return new Object[]{ t.label + " sent to " + a.checkString(1) };
    }

    @Callback(doc = "function(id, mode:string [, line:string, maxKmh, dwellSeconds]):string -- driverless: line, shuttle, oncall, off")
    @Optional.Method(modid = "opencomputers")
    public Object[] setAutopilot(Context c, Arguments a) {
        Locomotive loco = loco(a.checkInteger(0));
        if (loco == null) return new Object[]{ "train not loaded" };
        RailwayData data = RailwayData.get(world);
        String mode = a.checkString(1).toLowerCase();
        if (mode.equals("off")) {
            Autopilot.release(world, loco.getUUID());
            data.removeTrain(loco.getUUID());
            return new Object[]{ "driverless off" };
        }
        RailwayData.AutoTrain t = data.setTrain(loco.getUUID());
        t.mode = mode.startsWith("s") ? RailwayData.Mode.SHUTTLE : mode.startsWith("o") ? RailwayData.Mode.ON_CALL : RailwayData.Mode.LINE;
        t.line = optString(a, 2, t.line);
        t.maxKmh = Math.max(5, Math.min(250, optInt(a, 3, t.maxKmh)));
        t.dwellSeconds = Math.max(5, Math.min(600, optInt(a, 4, t.dwellSeconds)));
        t.index = 0;
        if (t.label.isEmpty()) t.label = Autopilot.label(t, loco);
        data.markDirty();
        return new Object[]{ t.label + ": " + t.mode.label };
    }

    @Callback(doc = "function(from:string, to:string, passenger:string):string -- book a ride as if a ticket went into a machine")
    @Optional.Method(modid = "opencomputers")
    public Object[] ticket(Context c, Arguments a) {
        Long from = station(a.checkString(0)), to = station(a.checkString(1));
        if (from == null || to == null) return new Object[]{ "no station by that name" };
        return new Object[]{ Dispatcher.ticketInserted(world, from, to, optString(a, 2, "computer")) };
    }

    @Callback(doc = "function(id):boolean -- is train protection braking this train right now")
    @Optional.Method(modid = "opencomputers")
    public Object[] isProtected(Context c, Arguments a) {
        Locomotive loco = loco(a.checkInteger(0));
        return new Object[]{ loco != null && Protection.penalised(loco.getUUID()) };
    }

    // ---- helpers ------------------------------------------------------------------------

    @Optional.Method(modid = "opencomputers")
    private static String optString(Arguments a, int i, String def) {
        return a.count() > i && a.isString(i) ? a.checkString(i) : def;
    }

    @Optional.Method(modid = "opencomputers")
    private static int optInt(Arguments a, int i, int def) {
        return a.count() > i && a.isInteger(i) ? a.checkInteger(i) : def;
    }

    private Locomotive loco(int id) {
        EntityRollingStock s = cam72cam.mod.world.World.get(world).getEntity(id, EntityRollingStock.class);
        if (s instanceof Locomotive l) return l;
        if (s instanceof EntityCoupleableRollingStock cs) {
            for (EntityCoupleableRollingStock x : cs.getTrain()) if (x instanceof Locomotive l) return l;
        }
        return null;
    }

    private Long station(String name) {
        for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) {
            if (e.getValue().equalsIgnoreCase(name.trim())) return e.getKey();
        }
        return null;
    }
}
