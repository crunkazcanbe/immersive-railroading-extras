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
public class TileDataLink extends TileEntity implements SimpleComponent, com.dogpound.railmap.settings.ISettingsHolder, net.minecraft.util.ITickable,
        com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- 12 V terminal + working front panel (requested feature): the link answers computers only on 12 V; MODE cycles
    // FULL (everything its Settings Console allows) / READ ONLY (no switches, signals, routes, driving...) / OFF; RESET
    // clears the counters and the map cache. LEDs: PWR 12 V, RX a call came in, TX it changed the railway, LINK a
    // computer talked in the last 30 s (also the port LEDs), ERR a call was refused; the LCD shows mode and counters.
    private static final String[] MODES = {"FULL", "READ ONLY", "OFF"};
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    private long lastRx = -1000, lastTx = -1000, lastErr = -1000;
    private int calls;
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { return "Railroad Data Link"; }
    @Override public String gearState() { return term.powered() ? MODES[ctl.get("mode")] + " · " + calls + " calls · " + refused + " refused" : "NO 12 V - link down"; }
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { gearChanged(); }
    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.controls.IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }

    private static boolean reads(String what) { return what.equals("read") || what.equals("gridRead"); }

    /** a computer's call came in: may it go through? (12 V, MODE, then the Settings Console) */
    private boolean ok(String what) {
        if (world == null) return false;
        long now = world.getTotalWorldTime();
        lastRx = now;
        calls++;
        int mode = ctl.get("mode");
        if (!term.powered() || mode == 2 || mode == 1 && !reads(what) || !cfg.bool(what)) return false;
        if (!reads(what)) lastTx = now;
        return true;
    }

    @Override
    public String controlPress(String id, net.minecraft.entity.player.EntityPlayer who) {
        if (!id.equals("reset") && !id.equals("mode")) return null;
        if (!term.powered()) return "§cNo 12 V - the Data Link is dead";
        if (id.equals("mode")) { ctl.set("mode", (ctl.get("mode") + 1) % 3); return "§eData Link mode: " + MODES[ctl.get("mode")]; }
        refused = 0; calls = 0; lastErr = -1000; mapCache = null;
        return "§eData Link reset - counters and map cache cleared";
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (term.tick(world, pos, wiredName())) gearChanged();
        long now = world.getTotalWorldTime();
        if (now % 5 != 0) return;
        boolean pw = term.powered(), link = pw && net.minecraftforge.fml.common.Loader.isModLoaded("opencomputers") && now - lastRx < 600;
        boolean ch = StationControls.put(ctl, "pwr", pw) | StationControls.put(ctl, "rx", pw && now - lastRx < 10)
                | StationControls.put(ctl, "tx", pw && now - lastTx < 10) | StationControls.put(ctl, "tip", pw && now - lastTx < 10)
                | StationControls.put(ctl, "link", link) | StationControls.put(ctl, "err", pw && now - lastErr < 100);
        for (int i = 1; i <= 5; i++) ch |= StationControls.put(ctl, "port" + i, link);
        ch |= StationControls.text(ctl, "lcd1", pw ? "MODE " + MODES[ctl.get("mode")] : "")
                | StationControls.text(ctl, "lcd2", pw ? "CALLS " + calls + "  ERR " + refused : "");
        if (ch) controlsChanged();
    }

    @Override public net.minecraft.nbt.NBTTagCompound getUpdateTag() { return writeToNBT(new net.minecraft.nbt.NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }


    @Override
    public String getComponentName() {
        return "railroad";
    }

    // ---- reading ------------------------------------------------------------------------

    @Callback(doc = "function():table -- every loaded rolling stock: id, name, x, y, z, speed (km/h), kind, lead, consist, driverless, status, nextStop, protected")
    @Optional.Method(modid = "opencomputers")
    public Object[] getTrains(Context c, Arguments a) {
        if (!ok("read")) return denied("read");
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
        if (!ok("read")) return denied("read");
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
        if (!ok("read")) return denied("read");
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
        if (!ok("read")) return denied("read");
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
        if (!ok("switches")) return denied("switches");
        BlockPos p = new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2));
        String s = a.checkString(3).toLowerCase();
        SwitchState want = s.startsWith("t") ? SwitchState.TURN : s.startsWith("s") ? SwitchState.STRAIGHT : SwitchState.NONE;
        if (Interlocking.isLocked(world, p)) return new Object[]{ false, "locked by a route or a driverless train" };
        if (want == SwitchState.NONE) {
            boolean ok = Interlocking.setAuto(world, p);
            return new Object[]{ ok, ok ? "switch on auto (redstone)" : "no switch there" };
        }
        SwitchState was = Interlocking.stateAt(world, p);
        boolean ok = Interlocking.claim(world, new cam72cam.mod.math.Vec3i(p.getX(), p.getY(), p.getZ()),
                want, "oc@" + pos.toLong());
        Interlocking.release(world, new cam72cam.mod.math.Vec3i(p.getX(), p.getY(), p.getZ()), "oc@" + pos.toLong());
        audit(p, "switch", "set " + s, String.valueOf(was), String.valueOf(Interlocking.stateAt(world, p)), ok ? "ok" : "refused: occupied or locked");   // #102
        return new Object[]{ ok, ok ? "switch set" : "occupied or locked" };
    }

    @Callback(doc = "function(x,y,z, hold:boolean):boolean -- hold a signal at Stop (true) or release it")
    @Optional.Method(modid = "opencomputers")
    public Object[] holdSignal(Context c, Arguments a) {
        if (!ok("signals")) return denied("signals");
        BlockPos p = new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2));
        if (!(world.getTileEntity(p) instanceof TileSignalMast m)) return new Object[]{ false };
        String was = String.valueOf(m.aspect());
        m.setRelayControl(a.checkBoolean(3), null);
        audit(p, m.wiredName(), a.checkBoolean(3) ? "hold at stop" : "release", was, String.valueOf(m.aspect()), "ok");
        return new Object[]{ true };
    }

    @Callback(doc = "function(x1,y1,z1, x2,y2,z2):string -- CTC route from one signal to another")
    @Optional.Method(modid = "opencomputers")
    public Object[] setRoute(Context c, Arguments a) {
        if (!ok("routes")) return denied("routes");
        String r = Interlocking.setRoute(world,
                new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2)),
                new BlockPos(a.checkInteger(3), a.checkInteger(4), a.checkInteger(5)));
        audit(new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2)), "route", "set to " + a.checkInteger(3) + "," + a.checkInteger(4) + "," + a.checkInteger(5), "", "", r);
        return new Object[]{ r };
    }

    @Callback(doc = "function(x,y,z):string -- cancel the route starting at this signal")
    @Optional.Method(modid = "opencomputers")
    public Object[] cancelRoute(Context c, Arguments a) {
        if (!ok("routes")) return denied("routes");
        Object r = Interlocking.cancelRoute(world, new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2)));
        audit(new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2)), "route", "cancel", "", "", String.valueOf(r));
        return new Object[]{ r };
    }

    @Callback(doc = "function(id, command:string):string -- throttle_up, throttle_down, brake_up, brake_down, forward, neutral, reverse, horn, bell, emergency_stop")
    @Optional.Method(modid = "opencomputers")
    public Object[] trainCommand(Context c, Arguments a) {
        if (!ok("drive")) return denied("drive");
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
        String r = TrainControl.apply(world, null, a.checkInteger(0), k);
        audit(null, "train " + a.checkInteger(0), k.name(), "", "", r);
        return new Object[]{ r };
    }

    @Callback(doc = "function(id, station:string):string -- make the train driverless and send it to a station")
    @Optional.Method(modid = "opencomputers")
    public Object[] sendTrain(Context c, Arguments a) {
        if (!ok("dispatch")) return denied("dispatch");
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
        audit(null, "train " + t.label, "send to " + a.checkString(1), "", "SEND", "ok");
        return new Object[]{ t.label + " sent to " + a.checkString(1) };
    }

    @Callback(doc = "function(id, mode:string [, line:string, maxKmh, dwellSeconds]):string -- driverless: line, shuttle, oncall, off")
    @Optional.Method(modid = "opencomputers")
    public Object[] setAutopilot(Context c, Arguments a) {
        if (!ok("dispatch")) return denied("dispatch");
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
        t.maxKmh = Math.max(5, Math.min(cfg.num("maxKmh"), optInt(a, 3, t.maxKmh)));
        t.dwellSeconds = Math.max(5, Math.min(600, optInt(a, 4, t.dwellSeconds)));
        t.index = 0;
        if (t.label.isEmpty()) t.label = Autopilot.label(t, loco);
        data.markDirty();
        return new Object[]{ t.label + ": " + t.mode.label };
    }

    @Callback(doc = "function(from:string, to:string, passenger:string):string -- book a ride as if a ticket went into a machine")
    @Optional.Method(modid = "opencomputers")
    public Object[] ticket(Context c, Arguments a) {
        if (!ok("tickets")) return denied("tickets");
        Long from = station(a.checkString(0)), to = station(a.checkString(1));
        if (from == null || to == null) return new Object[]{ "no station by that name" };
        return new Object[]{ Dispatcher.ticketInserted(world, from, to, optString(a, 2, "computer")) };
    }

    @Callback(doc = "function(id):boolean -- is train protection braking this train right now")
    @Optional.Method(modid = "opencomputers")
    public Object[] isProtected(Context c, Arguments a) {
        if (!ok("read")) return denied("read");
        Locomotive loco = loco(a.checkInteger(0));
        return new Object[]{ loco != null && Protection.penalised(loco.getUUID()) };
    }

    // ---- the versioned railway API (her ChatGPT list #96-105): rr.api("hello"), rr.api("vehicles"), rr.api("telemetry", x, y, z) ...
    @Callback(doc = "function(method:string, ...):any -- the versioned IR Extras API: rr.api(\"hello\") lists the level, capabilities and methods; rr.api(\"methods\") documents them")
    @Optional.Method(modid = "opencomputers")
    public Object[] api(Context c, Arguments a) {
        String name = a.checkString(0);
        com.dogpound.railmap.api.RailApi.Method m = com.dogpound.railmap.api.RailApi.method(name);
        String deny = apiDenied(m != null && m.control);
        if (deny != null) return new Object[]{ null, deny };
        Object[] rest = new Object[Math.max(0, a.count() - 1)];
        for (int i = 0; i < rest.length; i++) {
            int k = i + 1;
            rest[i] = a.isBoolean(k) ? (Object) a.checkBoolean(k) : a.isDouble(k) || a.isInteger(k) ? (Object) a.checkDouble(k) : a.isString(k) ? a.checkString(k) : null;   // tables: pass orders params as "k=v k=v"
        }
        return new Object[]{ com.dogpound.railmap.api.RailApi.call(world, name, rest, com.dogpound.railmap.api.Audit.Src.COMPUTER, who()) };
    }

    /** may a computer use the API right now (12 V, MODE, the "api" setting)? null = yes (also used by the ComputerCraft bridge) */
    public String apiDenied(boolean control) {
        if (!ok(control ? "api" : "read")) { Object[] d = denied(control ? "api" : "read"); return String.valueOf(d[1]); }
        if (control && !cfg.bool("api")) return "'api' control calls are switched off in this Data Link's Settings Console";
        return null;
    }

    private String who() { return "computer@" + pos.getX() + "," + pos.getY() + "," + pos.getZ(); }

    /** audit log (#102): every change a computer makes through the Data Link */
    private void audit(BlockPos at, String target, String action, String before, String after, String result) {
        com.dogpound.railmap.api.Audit.log(world, com.dogpound.railmap.api.Audit.Src.COMPUTER, who(), at, target, action, before, after, result);
    }

    // ---- the power grid (IR Extras electrics) -------------------------------------------------

    @Callback(doc = "function():table -- every power network: id, level, volts, hz, kw, kvar, kva, amps, pf, live, problem, faultMva, energy")
    @Optional.Method(modid = "opencomputers")
    public Object[] getGrid(Context c, Arguments a) {
        if (!ok("gridRead")) return denied("gridRead");
        return new Object[]{ com.dogpound.railmap.grid.GridRemote.networks(world) };
    }

    @Callback(doc = "function([kind:string]):table -- every grid machine (breaker, diesel, motor, meter...): x, y, z, kind, state, volts, amps, controls...")
    @Optional.Method(modid = "opencomputers")
    public Object[] getDevices(Context c, Arguments a) {
        if (!ok("gridRead")) return denied("gridRead");
        return new Object[]{ com.dogpound.railmap.grid.GridRemote.devices(world, optString(a, 0, "")) };
    }

    @Callback(doc = "function(x,y,z):table -- one grid machine's readings and the controls it has")
    @Optional.Method(modid = "opencomputers")
    public Object[] getDevice(Context c, Arguments a) {
        if (!ok("gridRead")) return denied("gridRead");
        return new Object[]{ com.dogpound.railmap.grid.GridRemote.device(world, new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2))) };
    }

    @Callback(doc = "function(x,y,z, control:string):boolean,string -- press a control: breakers open/close/reset (key in REMOTE), gensets start/stop/gcbclose/gcbopen, motors start/stop (selector AUTO)...")
    @Optional.Method(modid = "opencomputers")
    public Object[] control(Context c, Arguments a) {
        if (!ok("gridControl")) return denied("gridControl");
        String r = com.dogpound.railmap.grid.GridRemote.control(world, new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2)), a.checkString(3), "computer");
        audit(new BlockPos(a.checkInteger(0), a.checkInteger(1), a.checkInteger(2)), "grid machine", a.checkString(3), "", "", r);
        return new Object[]{ r.startsWith("ok"), r };
    }

    @Callback(doc = "function():table -- every machine wired into this Data Link (Instrument Wire from its control terminal to the link): wire colour, x, y, z, name, state, readings, controls")
    @Optional.Method(modid = "opencomputers")
    public Object[] getWired(Context c, Arguments a) {
        if (!ok("gridRead")) return denied("gridRead");
        List<Object> l = new ArrayList<>();
        for (Map.Entry<Integer, BlockPos> e : com.dogpound.railmap.wire.WireNet.wiredMachines(world, pos).entrySet()) {
            if (!(world.getTileEntity(e.getValue()) instanceof com.dogpound.railmap.wire.IWiredMachine m)) continue;
            Map<String, Object> info = new java.util.LinkedHashMap<>(m.wiredInfo());
            info.put("wire", com.dogpound.railmap.wire.WireNet.NAMES[e.getKey()]);
            info.put("colour", e.getKey());
            info.put("name", m.wiredName());
            info.put("lamp", m.wiredLamp());
            List<String> cs = new ArrayList<>();
            for (com.dogpound.railmap.grid.Panel.C pc : com.dogpound.railmap.grid.Reading.buttons(m.wiredPanel())) cs.add(pc.id);
            info.put("controls", cs);
            l.add(info);
        }
        return new Object[]{ l };
    }

    @Callback(doc = "function(colour:number|string, control:string):boolean,string -- press a control on the machine wired in on that colour, exactly like its own panel button")
    @Optional.Method(modid = "opencomputers")
    public Object[] pressWired(Context c, Arguments a) {
        if (!ok("gridControl")) return denied("gridControl");
        int col = -1;
        if (a.isInteger(0)) col = a.checkInteger(0);
        else { String n = a.checkString(0); for (int i = 0; i < 16; i++) if (com.dogpound.railmap.wire.WireNet.NAMES[i].equalsIgnoreCase(n)) col = i; }
        if (col < 0 || col > 15) return new Object[]{ false, "no such wire colour" };
        BlockPos src = com.dogpound.railmap.wire.WireNet.source(world, pos, col);
        if (!(src != null && world.getTileEntity(src) instanceof com.dogpound.railmap.wire.IWiredMachine m)) return new Object[]{ false, "nothing wired in on " + com.dogpound.railmap.wire.WireNet.NAMES[col] };
        String r = m.wiredPress(a.checkString(1), "computer");
        audit(src, m.wiredName(), "press " + a.checkString(1) + " (wire " + com.dogpound.railmap.wire.WireNet.NAMES[col] + ")", "", "", r);
        boolean refused = r != null && (r.startsWith("§c") || r.startsWith("§e") || r.startsWith("§6"));
        return new Object[]{ !refused, r == null ? "ok" : r.replaceAll("§.", "") };
    }

    @Callback(doc = "function():table -- everything that needs attention: dead networks, tripped breakers, faulted gensets / motors")
    @Optional.Method(modid = "opencomputers")
    public Object[] getAlarms(Context c, Arguments a) {
        if (!ok("gridRead")) return denied("gridRead");
        return new Object[]{ com.dogpound.railmap.grid.GridRemote.alarms(world) };
    }

    @Callback(doc = "function(channel:number):table -- every wire colour a Radio Transmitter on this channel carries: colour, x, y, z of the tapped point, volts, amps, kw, pf, status")
    @Optional.Method(modid = "opencomputers")
    public Object[] getRadio(Context c, Arguments a) {
        if (!ok("gridRead")) return denied("gridRead");
        int ch = a.checkInteger(0);
        List<Object> l = new ArrayList<>();
        for (long tx : com.dogpound.railmap.wire.WireNet.transmitters(world, ch)) {
            BlockPos tp = BlockPos.fromLong(tx);
            for (int col = 0; col < 16; col++) {
                BlockPos src = com.dogpound.railmap.wire.WireNet.source(world, tp, col);
                if (src == null) continue;
                com.dogpound.railmap.grid.Reading r = new com.dogpound.railmap.grid.Reading();
                Map<String, Object> m = new HashMap<>();
                m.put("colour", com.dogpound.railmap.wire.WireNet.NAMES[col].toLowerCase(java.util.Locale.ROOT));
                m.put("x", src.getX()); m.put("y", src.getY()); m.put("z", src.getZ());
                r.read(world, src, com.dogpound.railmap.grid.BlockInstrument.Inst.VOLT); m.put("volts", Math.round(r.value)); m.put("status", r.status);
                r.read(world, src, com.dogpound.railmap.grid.BlockInstrument.Inst.AMP); m.put("amps", Math.round(r.value * 10) / 10.0);
                r.read(world, src, com.dogpound.railmap.grid.BlockInstrument.Inst.WATT); m.put("kw", Math.round(r.value / 100) / 10.0);
                r.read(world, src, com.dogpound.railmap.grid.BlockInstrument.Inst.PF); m.put("pf", Math.round(r.value * 100) / 100.0);
                l.add(m);
            }
        }
        return new Object[]{ l };
    }

    // ---- the track map (what the wall screens draw) ------------------------------------------
    private Map<String, Object> mapCache;
    private long mapAt = -1000;

    @Callback(doc = "function():table -- the track map around this Data Link: tracks {kind, points {x,z,...}, switch}, signals {x,y,z,aspect}, stops {name,x,z}, bounds")
    @Optional.Method(modid = "opencomputers")
    public Object[] getMap(Context c, Arguments a) {
        if (!ok("read")) return denied("read");
        long now = world.getTotalWorldTime();
        if (mapCache == null || now - mapAt > 100) { mapCache = buildMap(); mapAt = now; }
        return new Object[]{ mapCache };
    }

    private Map<String, Object> buildMap() {
        com.dogpound.railmap.graph.RailNetwork net = com.dogpound.railmap.scan.NetworkScanner.scan(world, pos);
        Map<String, Object> m = new HashMap<>();
        List<Object> tracks = new ArrayList<>();
        int i = 0;
        for (com.dogpound.railmap.graph.RailNode n : net.nodes) {
            if (++i > 3000) break;
            Map<String, Object> t = new HashMap<>();
            t.put("kind", n.kind.name().toLowerCase(java.util.Locale.ROOT));
            List<Object> pts = new ArrayList<>();
            for (int k = 0; k + 2 < n.points.length; k += 3) { pts.add(Math.round(n.points[k] * 10) / 10.0); pts.add(Math.round(n.points[k + 2] * 10) / 10.0); }
            t.put("points", pts);
            if (n.kind == com.dogpound.railmap.graph.RailNode.Kind.SWITCH)
                t.put("switch", n.switchState == com.dogpound.railmap.graph.RailNode.SWITCH_TURN ? "turn" : "straight");
            t.put("x", n.pos.getX()); t.put("y", n.pos.getY()); t.put("z", n.pos.getZ());
            tracks.add(t);
        }
        List<Object> sig = new ArrayList<>();
        for (com.dogpound.railmap.graph.SignalNode s : net.signals) {
            Map<String, Object> t = new HashMap<>();
            t.put("x", s.pos.getX()); t.put("y", s.pos.getY()); t.put("z", s.pos.getZ());
            t.put("aspect", s.aspect.name().toLowerCase(java.util.Locale.ROOT));
            sig.add(t);
        }
        List<Object> stops = new ArrayList<>();
        for (com.dogpound.railmap.graph.StopNode s : net.stops) {
            Map<String, Object> t = new HashMap<>();
            t.put("name", s.name); t.put("x", s.pos.getX()); t.put("z", s.pos.getZ());
            stops.add(t);
        }
        m.put("tracks", tracks); m.put("signals", sig); m.put("stops", stops); m.put("truncated", net.truncated || i > 3000);
        Map<String, Object> b = new HashMap<>();
        b.put("minX", net.minX); b.put("minZ", net.minZ); b.put("maxX", net.maxX); b.put("maxZ", net.maxZ);
        m.put("bounds", b);
        return m;
    }

    // ---- Settings Console (sneak-right-click with the Signal Wrench) ----
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int refused;

    private Object[] denied(String what) {
        refused++;
        lastErr = world.getTotalWorldTime();
        int mode = ctl.get("mode");
        if (!term.powered()) return new Object[]{ null, "the Data Link has no 12 V power" };
        if (mode == 2) return new Object[]{ null, "the Data Link is switched OFF (MODE button on its front)" };
        if (mode == 1 && !reads(what)) return new Object[]{ null, "the Data Link is in READ ONLY mode (MODE button on its front)" };
        if (cfg.bool("logRefused")) com.dogpound.railmap.RailMap.LOG.info("[IR Extras] Data Link at {} refused a '{}' call (off in its Settings Console)", pos, what);
        return new Object[]{ null, "'" + what + "' is switched off in this Data Link's Settings Console" };
    }

    @Override public String settingsTitle() { return "Railroad Data Link"; }

    @Override
    public List<com.dogpound.railmap.settings.Setting> settingDefs() {
        List<com.dogpound.railmap.settings.Setting> l = new ArrayList<>();
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "read", "Read the railroad", "getTrains, getStations, getLines, getSignals, isProtected", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "switches", "Throw switches", "setSwitch (the interlocking still refuses locked ones)", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "signals", "Hold signals", "holdSignal", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "routes", "Set and cancel routes", "setRoute, cancelRoute", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "drive", "Drive trains", "trainCommand: throttle, brakes, reverser, horn", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "dispatch", "Send driverless trains", "sendTrain, setAutopilot", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "tickets", "Book tickets", "ticket", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "gridRead", "Read the power grid", "getGrid, getDevices, getDevice, getAlarms", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "gridControl", "Operate the power grid", "control: breakers (key in REMOTE), gensets, motors (AUTO), converters, batteries", true));
        l.add(com.dogpound.railmap.settings.Setting.bool("Computers may", "api", "Use the API's control calls", "api(...): setChannel, setVar, press, vehicleCommand, orders, scenario (reads follow 'Read the railroad')", true));
        l.add(com.dogpound.railmap.settings.Setting.num("Limits", "maxKmh", "Top speed a computer may set", "Cap for setAutopilot", 250, 5, 250, 5, "km/h"));
        l.add(com.dogpound.railmap.settings.Setting.bool("Limits", "logRefused", "Log refused calls", "Write a line to the game log when a script hits a switched-off call", false));
        l.add(com.dogpound.railmap.settings.Setting.info("Status", "Component name", "railroad  (local rr = require(\"component\").railroad)"));
        l.add(com.dogpound.railmap.settings.Setting.info("Status", "OpenComputers", net.minecraftforge.fml.common.Loader.isModLoaded("opencomputers") ? "installed" : "not installed: the block does nothing"));
        l.add(com.dogpound.railmap.settings.Setting.info("Status", "Refused calls", String.valueOf(refused)));
        com.dogpound.railmap.wire.IWiredGear.settings(l, "Status", this);
        return l;
    }

    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }

    @Override public void onSettingsChanged(String key) { markDirty(); }

    @Override
    public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound t) {
        super.writeToNBT(t);
        cfg.write(t);
        term.write(t);
        ctl.write(t);
        t.setInteger("refused", refused);
        t.setInteger("calls", calls);
        return t;
    }

    @Override
    public void readFromNBT(net.minecraft.nbt.NBTTagCompound t) {
        super.readFromNBT(t);
        cfg.read(t);
        term.read(t);
        ctl.read(t);
        refused = t.getInteger("refused");
        calls = t.getInteger("calls");
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
