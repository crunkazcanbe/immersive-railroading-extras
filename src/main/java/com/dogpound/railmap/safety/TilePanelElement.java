package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.library.SwitchState;
import com.dogpound.railmap.admin.Approvals;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.scan.SwitchControl;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Physical control panel builder (her ChatGPT batch-3 #49): one tile of a panel the player builds by placing elements
 * side by side - push button, turn switch, indication lamp pair, gauge, miniature track mimic. Each is bound with the
 * Signal Wrench (pick a device, then click the element): any IControlled machine (NEXT picks which of its controls),
 * a signal, a track circuit, a switch rail, an axle counter evaluator (NEXT picks the section), or - with nothing
 * picked - an automation channel 1-64 (NEXT steps the channel), or a program variable (/irextras sig panel var).
 * Buttons and switches work the real thing (approvals and route locking respected), lamps / gauges / mimics read it.
 * The elements are 12 V gear: one 12 V feed powers the whole panel through the elements touching each other.
 */
public class TilePanelElement extends SafetyTile {
    public enum Bind { NONE, DEVICE, SIGNAL, TC, SWITCH, AXLE, CHANNEL, VAR }

    private Bind bind = Bind.NONE;
    private long target;
    private String tctl = "", var = "";
    private int sec, chan;
    private boolean held;
    private long pulseUntil;

    public static final String[] SHAPES = {"ST", "CL", "CR", "PL", "PR", "X", "SG", "BF"};
    public static final String[] SHAPE_NAMES = {"straight", "curve left", "curve right", "points left", "points right", "crossing", "signal", "buffer stop"};

    String kind() {
        String n = getBlockType() == null || getBlockType().getRegistryName() == null ? "" : getBlockType().getRegistryName().getPath();
        return n.startsWith("panel_") ? n.substring(6) : n;
    }

    // ---- power: 12 V itself, or through the panel ------------------------------------------------------------------
    @Override public boolean powered() { return term.powered() || ctl.get("chain") == 1; }

    /** fed: some element of the connected panel has its 12 V (BFS over touching elements) */
    private boolean panelFed() {
        // ponytail: BFS per element per second, O(n^2) on huge panels; cache per panel if panels grow past a few hundred
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(pos);
        seen.add(pos.toLong());
        while (!q.isEmpty() && seen.size() < 512) {
            BlockPos p = q.poll();
            TileEntity t = world.isBlockLoaded(p) ? world.getTileEntity(p) : null;
            if (!(t instanceof TilePanelElement e)) continue;
            if (e.term.powered()) return true;
            for (EnumFacing f : EnumFacing.values()) if (seen.add(p.offset(f).toLong())) q.add(p.offset(f));
        }
        return false;
    }

    @Override public String wiredName() { return "Panel " + kind() + (bind == Bind.NONE ? "" : " (" + label() + ")"); }
    @Override public String gearState() { return bind == Bind.NONE ? "not bound" : label() + " = " + value(); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("bind", bind.name()); m.put("target", label()); m.put("value", value()); }

    // ---- binding -------------------------------------------------------------------------------------------------------
    public String label() {
        BlockPos t = BlockPos.fromLong(target);
        switch (bind) {
            case DEVICE: return "DEV " + t.getX() + "," + t.getZ() + " " + tctl;
            case SIGNAL: return "SIG " + t.getX() + "," + t.getZ();
            case TC: return "TC " + t.getX() + "," + t.getZ();
            case SWITCH: return "PTS " + t.getX() + "," + t.getZ();
            case AXLE: return "AXLE " + t.getX() + "," + t.getZ() + " S" + (sec + 1);
            case CHANNEL: return "CH " + chan + (ProgramData.get(world).channelName[chan].isEmpty() ? "" : " " + ProgramData.get(world).channelName[chan]);
            case VAR: return "VAR " + var;
            default: return "UNBOUND";
        }
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (pick.equals(pos)) { bind = Bind.NONE; controlsChanged(); return "§7Unbound"; }
        return bindTo(pick);
    }

    /** bind to whatever is at `pick` (also /irextras sig panel bind) */
    String bindTo(BlockPos pick) {
        if (pick.distanceSq(pos) > (double) SigConfig.panelBindRange * SigConfig.panelBindRange) return "§cToo far - panel elements reach " + SigConfig.panelBindRange + " m (config panelBindRange)";
        TileEntity t = world.getTileEntity(pick);
        target = pick.toLong();
        sec = 0;
        if (t instanceof TileSignalMast) bind = Bind.SIGNAL;
        else if (t instanceof TileTrackCircuit) bind = Bind.TC;
        else if (t instanceof TileAxleEvaluator && !kind().equals("button")) bind = Bind.AXLE;
        else if (t instanceof TilePanelElement) return "§cBind to the device, not to another panel element";
        else if (t instanceof IControlled c) {
            List<String> ids = candidates(c);
            if (ids.isEmpty()) return "§cThat device has no controls this " + kind() + " can use";
            bind = Bind.DEVICE;
            tctl = ids.get(0);
        } else if (SwitchControl.isRail(world, pick)) { bind = Bind.SWITCH; target = FaultLab.switchOf(world, pick).toLong(); }
        else { target = 0; return "§cNothing to bind there (device, signal, track circuit, axle evaluator or switch)"; }
        controlsChanged();
        return "§aBound: " + label() + (bind == Bind.DEVICE ? " §7(NEXT steps through its controls)" : bind == Bind.AXLE ? " §7(NEXT steps the section)" : "");
    }

    void bindChannel(int ch) { bind = Bind.CHANNEL; chan = Math.max(1, Math.min(ProgramData.CHANNELS, ch)); target = 0; controlsChanged(); }
    void bindVar(String v) { bind = Bind.VAR; var = v; target = 0; controlsChanged(); }
    void unbind() { if (held) relay(false); bind = Bind.NONE; target = 0; controlsChanged(); }

    /** the target's controls this element can drive (buttons / switches) or read (lamps, gauges, mimics) */
    List<String> candidates(IControlled c) {
        List<String> out = new ArrayList<>();
        boolean drive = kind().equals("button") || kind().equals("switch");
        for (Panel.C k : c.layout().all.cs) {
            if (k.id.isEmpty() || k.t == Panel.T.ZONE || k.t == Panel.T.LABEL) continue;
            if (drive != k.clickable()) continue;
            out.add(k.id);
        }
        return out;
    }

    private IControlled device() {
        BlockPos t = BlockPos.fromLong(target);
        return world.isBlockLoaded(t) && world.getTileEntity(t) instanceof IControlled c ? c : null;
    }

    // ---- reading -------------------------------------------------------------------------------------------------------
    /** the bound thing's raw value now */
    int value() {
        BlockPos t = BlockPos.fromLong(target);
        TileEntity te = bind == Bind.CHANNEL || bind == Bind.VAR || bind == Bind.NONE || !world.isBlockLoaded(t) ? null : world.getTileEntity(t);
        switch (bind) {
            case DEVICE: return te instanceof IControlled c ? c.controlValue(tctl) : 0;
            case SIGNAL: if (te instanceof TileSignalMast m) { Aspect a = m.shownAspect(); return a.isStop() || a == Aspect.DARK ? 0 : a.speedKmh >= 999 ? 2 : 1; } return 0;
            case TC: return te instanceof TileTrackCircuit c && c.occupied() ? 1 : 0;
            case SWITCH: return Interlocking.stateAt(world, t) == SwitchState.TURN ? 1 : 0;
            case AXLE: return te instanceof TileAxleEvaluator e ? (e.sec[Math.min(TileAxleEvaluator.N - 1, sec)].state == 0 ? 0 : 1) : 0;
            case CHANNEL: return ProgramData.get(world).ch(chan) ? 1 : 0;
            case VAR: return (int) Math.round(ProgramData.get(world).var(var));
            default: return 0;
        }
    }

    /** red lamp lit: at danger / occupied / reversed / on */
    boolean active() {
        if (bind == Bind.NONE) return false;
        if (bind == Bind.SIGNAL) return value() == 0;
        return value() != 0;
    }

    boolean occupied() {
        BlockPos t = BlockPos.fromLong(target);
        switch (bind) {
            case TC: case AXLE: case DEVICE: case CHANNEL: case VAR: return value() != 0;
            case SWITCH: case SIGNAL: {
                for (TrainNode n : TrainTracker.latest(world)) if (Geo.d2(n.x, n.z, t.getX() + 0.5, t.getZ() + 0.5) <= (bind == Bind.SWITCH ? 16 : 36)) return true;
                return false;
            }
            default: return false;
        }
    }

    boolean routed() {
        if (bind == Bind.NONE || target == 0) return false;
        BlockPos t = BlockPos.fromLong(target);
        for (Interlocking.Route r : Interlocking.routes(world)) {
            if (bind == Bind.SIGNAL) { if (r.start.equals(t) || r.end.equals(t)) return true; continue; }
            if (Geo.onPath(r.path, t.getX() + 0.5, t.getY(), t.getZ() + 0.5, 3)) return true;
        }
        return false;
    }

    // ---- operating -----------------------------------------------------------------------------------------------------
    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "bind": return "§7" + (bind == Bind.NONE ? "Not bound - Signal Wrench: pick a device / signal / track circuit / switch, then click here (NEXT alone binds a channel)" : "Bound to " + label() + " = " + value());
            case "sel": return next();
            case "btn": return powered() ? push(who) : "§cPanel dark - no 12 V";
            case "s_sw": {
                int v = (ctl.get("s_sw") + 1) % 2;
                ctl.set("s_sw", v);
                return powered() ? turn(v, who) : "§7Switch turned (panel dark - no 12 V)";
            }
            case "s_rng": ctl.set("s_rng", (ctl.get("s_rng") + 1) % 4); return "§eRange " + RANGE[ctl.get("s_rng")];
            case "s_shape": ctl.set("s_shape", (ctl.get("s_shape") + 1) % SHAPES.length); return "§eMimic: " + SHAPE_NAMES[ctl.get("s_shape")];
            default: return "§7" + gearState();
        }
    }

    static final int[] RANGE = {10, 100, 1000, 10000};

    private String next() {
        switch (bind) {
            case NONE: bindChannel(1); return "§eBound to channel 1 (NEXT steps it)";
            case CHANNEL: bindChannel(chan % ProgramData.CHANNELS + 1); return "§eChannel " + chan;
            case AXLE: sec = (sec + 1) % TileAxleEvaluator.N; controlsChanged(); return "§eSection " + (sec + 1);
            case DEVICE: {
                IControlled c = device();
                if (c == null) return "§cDevice not loaded";
                List<String> ids = candidates(c);
                if (ids.isEmpty()) return "§cNo controls";
                tctl = ids.get((ids.indexOf(tctl) + 1) % ids.size());
                Panel.C k = c.layout().control(tctl);
                controlsChanged();
                return "§eNow " + tctl + (k == null || k.label.isEmpty() ? "" : " (" + k.label.replace("|", " ") + ")");
            }
            default: return "§7" + label() + " has nothing to step";
        }
    }

    private String push(EntityPlayer who) {
        BlockPos t = BlockPos.fromLong(target);
        switch (bind) {
            case DEVICE: case AXLE: {
                IControlled c = device();
                if (c == null) return "§cDevice not loaded";
                String no = Approvals.blocked(world, "operate", t, who);
                if (no != null) return no;
                return IControlled.operate(c, bind == Bind.AXLE ? "pr" + (sec + 1) : tctl, who);
            }
            case SIGNAL: held = !held; relay(held); return held ? "§cSignal held at STOP from the panel" : "§aSignal hold released";
            case SWITCH: return throwPoints(value() == 0, who);
            case CHANNEL: ProgramData.get(world).setChannel(chan, true); pulseUntil = world.getTotalWorldTime() + 20; return "§eChannel " + chan + " pulsed";
            case VAR: { ProgramData d = ProgramData.get(world); d.setVar(var, d.var(var) + 1); return "§eVariable " + var + " = " + Math.round(d.var(var)); }
            default: return "§7Not bound";
        }
    }

    private String turn(int v, EntityPlayer who) {
        BlockPos t = BlockPos.fromLong(target);
        switch (bind) {
            case DEVICE: {
                IControlled c = device();
                if (c == null) return "§cDevice not loaded";
                String no = Approvals.blocked(world, "operate", t, who);
                if (no != null) return no;
                Panel.C k = c.layout().control(tctl);
                if (k == null) return "§cNo control " + tctl;
                if (k.t == Panel.T.PUSH) return v == 1 ? IControlled.operate(c, tctl, who) : "§7Back to normal";
                String r = "";
                for (int i = 0; i < Math.max(2, k.positions.length) && c.controlValue(tctl) != v; i++) r = IControlled.operate(c, tctl, who);
                return r;
            }
            case SIGNAL: held = v == 1; relay(held); return held ? "§cSignal held at STOP" : "§aSignal released";
            case SWITCH: return throwPoints(v == 1, who);
            case CHANNEL: ProgramData.get(world).setChannel(chan, v == 1); return "§eChannel " + chan + (v == 1 ? " ON" : " OFF");
            case VAR: ProgramData.get(world).setVar(var, v); return "§eVariable " + var + " = " + v;
            default: return "§7Not bound";
        }
    }

    private String throwPoints(boolean reverse, EntityPlayer who) {
        BlockPos t = BlockPos.fromLong(target);
        if (Interlocking.isLocked(world, t)) return "§cPoints locked by a route / train - can't throw them";
        String no = Approvals.blocked(world, "operate", t, who);
        if (no != null) return no;
        return SwitchControl.set(world, t, reverse) ? "§ePoints " + (reverse ? "REVERSE" : "NORMAL") : "§cPoints didn't move (failed / jammed / not a switch)";
    }

    private void relay(boolean hold) {
        BlockPos t = BlockPos.fromLong(target);
        if (world.isBlockLoaded(t) && world.getTileEntity(t) instanceof TileSignalMast m) m.setRelayControl(hold, null);
    }

    // ---- tick ------------------------------------------------------------------------------------------------------------
    @Override
    protected void serverTick() {
        if (pulseUntil > 0 && world.getTotalWorldTime() >= pulseUntil) { pulseUntil = 0; if (bind == Bind.CHANNEL) ProgramData.get(world).setChannel(chan, false); }
        if (age % 20 == 13) {
            put("chain", !term.powered() && panelFed());
            if (held && bind == Bind.SIGNAL) relay(true);                              // keep the hold on (the signal may reload)
            if (bind == Bind.CHANNEL && kind().equals("switch") && powered() && ProgramData.get(world).ch(chan) != (ctl.get("s_sw") == 1))
                ProgramData.get(world).setChannel(chan, ctl.get("s_sw") == 1);         // a turned switch holds its channel
        }
        if (age % 5 != 1) return;
        put("pwr", powered());
        put("bound", bind != Bind.NONE);
        String k = kind();
        text("tag", label());
        if (k.equals("lamp")) { boolean a = active(); put("on", a); put("off", bind != Bind.NONE && !a); }
        if (k.equals("button") || k.equals("switch")) put("ind", active());
        if (k.equals("gauge")) {
            int v = value();
            put("g", (int) Math.round(Math.max(0, v) * 100.0 / RANGE[Math.min(3, ctl.get("s_rng"))]));
            text("val", bind == Bind.NONE ? "----" : String.valueOf(v));
        }
        if (k.equals("mimic")) mimic();
    }

    /** the mimic's glyphs: each spoke a coloured line of the shape (red occupied, white route set, dim grey idle) */
    private void mimic() {
        int shape = ctl.get("s_shape");
        boolean occ = occupied(), route = !occ && routed();
        String c = bind == Bind.NONE ? "§8" : occ ? "§c" : route ? "§f" : "§7";
        String dim = "§8";
        Set<String> on = new HashSet<>();
        switch (SHAPES[shape]) {
            case "ST": on.addAll(Arrays.asList("W", "E")); break;
            case "CL": on.addAll(Arrays.asList("W", "NE")); break;
            case "CR": on.addAll(Arrays.asList("W", "SE")); break;
            case "PL": on.addAll(Arrays.asList("W", "E", "NE")); break;
            case "PR": on.addAll(Arrays.asList("W", "E", "SE")); break;
            case "X": on.addAll(Arrays.asList("W", "E", "NE", "SW")); break;
            case "SG": on.addAll(Arrays.asList("W", "E")); break;
            default: on.add("W"); break;
        }
        boolean points = SHAPES[shape].startsWith("P") && bind == Bind.SWITCH;
        boolean rev = points && value() == 1;
        for (String s : new String[]{"W", "E", "NE", "SE", "SW", "NW"}) {
            String col = !on.contains(s) ? null : points && (s.equals("E") && rev || !s.equals("E") && !s.equals("W") && !rev) ? dim : c;
            if (s.equals("W") || s.equals("E")) text("m" + s, col == null ? "" : col + "=======");
            else for (int i = 1; i <= 3; i++) text("m" + s + i, col == null ? "" : col + "■");
        }
        String sig = "";
        if (SHAPES[shape].equals("SG")) {
            int v = bind == Bind.SIGNAL ? value() : -1;
            sig = v < 0 ? "§8●" : v == 0 ? "§c●" : v == 1 ? "§e●" : "§a●";
        }
        text("msig", sig);
        for (int i = 1; i <= 3; i++) text("mb" + i, SHAPES[shape].equals("BF") ? c + "■" : "");
    }

    // ---- save --------------------------------------------------------------------------------------------------------------
    @Override
    protected void save(NBTTagCompound t) {
        t.setString("bind", bind.name()); t.setLong("tgt", target); t.setString("tctl", tctl); t.setString("var", var);
        t.setInteger("sec", sec); t.setInteger("chan", chan); t.setBoolean("held", held);
    }

    @Override
    protected void load(NBTTagCompound t) {
        try { bind = Bind.valueOf(t.getString("bind")); } catch (RuntimeException e) { bind = Bind.NONE; }
        target = t.getLong("tgt"); tctl = t.getString("tctl"); var = t.getString("var");
        sec = t.getInteger("sec"); chan = t.getInteger("chan"); held = t.getBoolean("held");
    }
}
