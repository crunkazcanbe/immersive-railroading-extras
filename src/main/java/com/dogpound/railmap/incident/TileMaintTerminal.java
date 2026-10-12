package com.dogpound.railmap.incident;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.TileGrid;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.IWiredMachine;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Remote Maintenance Terminal (her ChatGPT list #75): the technician's desk terminal on the telemetry bus. It lists
 * every machine and piece of gear that has a control wire landed (the same bus the console's WIRED page reads) -
 * FILTER all / faults / no 12 V - nearest first. For the selected one it shows its state, supply and readings;
 * ACK acknowledges its alarms, TEST runs its lamp test, CTL steps through its own panel controls and OPERATE works
 * the chosen one remotely - EXCEPT safety-critical operations (signalling safety gear, grid switchgear, keys, resets,
 * earthing, releases...), which the terminal refuses with LOCAL ACTION REQUIRED and offers a work order instead.
 * W/O raises a work order for it (printed; it closes itself once the fault clears, see IncidentTicker); PRINT prints
 * the open work orders. Printer paper tray and work-order files are real storage.
 */
public class TileMaintTerminal extends TileFurniture {
    public static final String[] STORE_IDS = {"paper", "files"};
    public static final String[] STORE_NAMES = {"Printer Paper Tray", "Work Order Files"};
    public static final int[] STORE_SIZES = {9, 18};
    private static final String[] FILTERS = {"ALL", "FAULTS", "NO 12V"};
    private static final Set<String> CRITICAL_IDS = new HashSet<>(Arrays.asList("reset", "release", "earth", "close", "direct", "esp", "block", "rel", "isolate", "trip", "discharge"));

    private final List<BlockPos> list = new ArrayList<>();

    public TileMaintTerminal() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 1); }

    @Override protected boolean mechanical(String id) { return id.startsWith("s_"); }

    private void scan() {
        list.clear();
        int f = Math.floorMod(ctl.get("s_filter"), 3);
        List<TileEntity> ts = new ArrayList<>();
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof IWiredMachine m) || te == this || te.isInvalid() || m.ctlWire() < 0) continue;
            if (te.getPos().distanceSq(pos) > 2048 * 2048) continue;
            if (f == 1 && m.wiredLamp() == 1) continue;
            if (f == 2 && !(te instanceof IWiredGear g && !g.term().powered())) continue;
            ts.add(te);
        }
        ts.sort(Comparator.comparingDouble(t -> t.getPos().distanceSq(pos)));
        for (TileEntity t : ts) if (list.size() < 200) list.add(t.getPos());
    }

    private IWiredMachine sel() {
        if (list.isEmpty()) return null;
        BlockPos p = list.get(Math.floorMod(ctl.get("sel"), list.size()));
        TileEntity te = world.getTileEntity(p);
        return te instanceof IWiredMachine m ? m : null;
    }

    private List<Panel.C> controlsOf(IWiredMachine m) {
        List<Panel.C> o = new ArrayList<>();
        Panel p = m.wiredPanel();
        if (p != null) for (Panel.C c : p.cs) if (c.t == Panel.T.PUSH || c.t == Panel.T.SELECTOR || c.t == Panel.T.KEY || c.t == Panel.T.MUSHROOM || c.t == Panel.T.LEVER || c.t == Panel.T.PULL) o.add(c);
        return o;
    }

    /** why a remote operation must be done at the equipment instead (null = may be done remotely) */
    static String localOnly(IWiredMachine m, Panel.C c) {
        if (m instanceof SafetyTile) return "signalling safety equipment";
        if (m instanceof TileGrid) return "electrical switchgear (permit to work, local switching)";
        if (m instanceof com.dogpound.railmap.block.TileSignalBox) return "the interlocking";
        if (c.t == Panel.T.KEY || c.t == Panel.T.MUSHROOM || c.t == Panel.T.PULL) return "a key / emergency / lever control";
        String id = c.id.toLowerCase(Locale.ROOT);
        for (String k : CRITICAL_IDS) if (id.contains(k)) return "a safety-critical operation (" + c.id + ")";
        return null;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        String name = who == null ? "console" : who.getName();
        IWiredMachine m = sel();
        BlockPos at = m == null ? null : ((TileEntity) m).getPos();
        switch (id) {
            case "prev": ctl.set("sel", ctl.get("sel") - 1); ctl.set("ci", 0); return "";
            case "next": ctl.set("sel", ctl.get("sel") + 1); ctl.set("ci", 0); return "";
            case "cprev": ctl.set("ci", ctl.get("ci") - 1); return "";
            case "cnext": ctl.set("ci", ctl.get("ci") + 1); return "";
            case "ack": {
                if (m == null) return "§7Nothing selected";
                AlarmManager am = AlarmManager.get(world);
                int n = 0;
                for (AlarmManager.Alarm a : am.all()) if (!a.acked && a.pos == at.toLong()) { am.ack(a.id, name + " (remote)"); n++; }
                return "§e" + n + " alarm(s) acknowledged remotely";
            }
            case "test": {
                if (m == null) return "§7Nothing selected";
                for (String t : new String[]{"test", "lamptest", "ltest"}) {
                    Panel p = m.wiredPanel();
                    if (p != null) for (Panel.C c : p.cs) if (c.id.equals(t)) return "§eRemote: " + m.wiredPress(t, name + " (remote)");
                }
                return "§7" + m.wiredName() + " has no remote test";
            }
            case "operate": {
                if (m == null) return "§7Nothing selected";
                List<Panel.C> cs = controlsOf(m);
                if (cs.isEmpty()) return "§7" + m.wiredName() + " has no controls to work";
                Panel.C c = cs.get(Math.floorMod(ctl.get("ci"), cs.size()));
                String why = localOnly(m, c);
                if (why != null) { ctl.set("localflag", 1); return "§cLOCAL ACTION REQUIRED: " + why + " - raise a work order (W/O)"; }
                return "§eRemote " + c.label.replace("|", " ") + ": " + m.wiredPress(c.id, name + " (remote)");
            }
            case "wo": {
                if (m == null) return "§7Nothing selected";
                String fault = m instanceof IWiredGear g ? g.gearState() : m.wiredLamp() == 1 ? "inspection" : "fault";
                boolean local = false;
                for (Panel.C c : controlsOf(m)) local |= localOnly(m, c) != null;
                IncidentLog.WorkOrder o = IncidentLog.get(world).workOrder(world, at, m.wiredName(), fault, name, local);
                TileIncidentDesk.printBook(world, pos, controlFacing(), "Work Order " + o.id, "Maintenance Terminal", Arrays.asList(
                        "WORK ORDER " + o.id, "Equipment: " + m.wiredName(), "At " + at.getX() + "," + at.getY() + "," + at.getZ(),
                        "Bus: " + WireNet.name(m.ctlWire()), "Fault: " + fault, local ? "LOCAL ACTION REQUIRED - isolate and work on site" : "May be reset remotely after repair",
                        "Raised by " + name, "Closes itself when the fault clears"));
                return "§eWork order " + o.id + " raised and printed";
            }
            case "print": {
                List<String> l = new ArrayList<>();
                l.add("OPEN WORK ORDERS");
                for (IncidentLog.WorkOrder o : IncidentLog.get(world).orders) {
                    BlockPos p = BlockPos.fromLong(o.pos);
                    l.add((o.done == 0 ? "OPEN " : "DONE ") + o.id + " " + o.gear + " @" + p.getX() + "," + p.getZ() + ": " + o.fault + (o.done == 0 ? "" : " (" + o.closedWhy + ")"));
                    if (l.size() > 60) break;
                }
                TileIncidentDesk.printBook(world, pos, controlFacing(), "Work Orders", "Maintenance Terminal", l);
                return "§7Printing work orders...";
            }
            default: return null;
        }
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        if (age % 40 == 3 || list.isEmpty()) scan();
        IWiredMachine m = sel();
        int n = list.size();
        String[] t = new String[6];
        if (m == null) { t[0] = n == 0 ? "NO GEAR ON THE BUS" : "SELECT"; for (int i = 1; i < 6; i++) t[i] = ""; }
        else {
            BlockPos p = ((TileEntity) m).getPos();
            t[0] = (Math.floorMod(ctl.get("sel"), n) + 1) + "/" + n + " " + m.wiredName();
            t[1] = "AT " + p.getX() + "," + p.getY() + "," + p.getZ() + " BUS " + WireNet.name(m.ctlWire());
            t[2] = m instanceof IWiredGear g ? "STATE " + g.gearState() : "LAMP " + new String[]{"", "RUNNING", "STOPPED", "FAULT"}[Math.max(0, Math.min(3, m.wiredLamp()))];
            t[3] = m instanceof IWiredGear g ? "SUPPLY " + g.term().status() : "";
            List<Panel.C> cs = controlsOf(m);
            Panel.C c = cs.isEmpty() ? null : cs.get(Math.floorMod(ctl.get("ci"), cs.size()));
            t[4] = c == null ? "NO REMOTE CONTROLS" : "CTL " + c.label.replace("|", " ") + (localOnly(m, c) == null ? " - REMOTE OK" : " - LOCAL ONLY");
            int wo = 0;
            for (IncidentLog.WorkOrder o : IncidentLog.get(world).orders) if (o.done == 0) wo++;
            Map<String, Object> info = m.wiredInfo();
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Object> e : info.entrySet()) if (!e.getKey().equals("name") && !e.getKey().equals("state") && !e.getKey().equals("supply") && b.length() < 40) b.append(e.getKey()).append(' ').append(e.getValue()).append(' ');
            t[5] = "W/O OPEN " + wo + " " + b;
            ch |= put("local", c != null && localOnly(m, c) != null);
            ch |= put("fault", m.wiredLamp() != 1);
        }
        for (int i = 0; i < 6; i++) ch |= txt("t" + (i + 1), cut(t[i], 46).toUpperCase(Locale.ROOT));
        ch |= put("power", 1);
        ch |= put("link", n > 0);
        ch |= put("count", n);
        ch |= txt("filt", FILTERS[Math.floorMod(ctl.get("s_filter"), 3)]);
        return ch;
    }

    private boolean put(String id, int v) { return StationControls.put(ctl, id, v); }
    private boolean put(String id, boolean v) { return StationControls.put(ctl, id, v); }
    private boolean txt(String id, String t) { return StationControls.text(ctl, id, t); }

    @Override public String wiredName() { return "Remote maintenance terminal"; }
    @Override public String gearState() { return term.powered() ? list.size() + " items on the bus" : "NO 12 V - terminal dark"; }
    @Override protected void writeExtra(NBTTagCompound t) { }
    @Override protected void readExtra(NBTTagCompound t) { }
}
