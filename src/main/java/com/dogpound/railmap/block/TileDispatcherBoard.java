package com.dogpound.railmap.block;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.*;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.PanelHost;
import com.dogpound.railmap.grid.TileGrid;
import com.dogpound.railmap.network.PacketConsole;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.server.TrainControl;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * The dispatcher board / Control Center console. All the map behaviour lives in {@link TileRailDisplay}; when the block
 * is the Control Center console (requested feature) this is also the console's brain: its desk controls
 * ({@link ConsolePanel}), the big screen's pages, the little readout screens, and its power - grid mains (120 / 208 /
 * 240 V AC) into the back of the desk for the screens and computer, and 12 V DC instrument wire for the desk's buttons
 * and lamps. Anything over 264 V burns it out (screwdriver to repair); DC or too low a voltage just won't run it.
 * The Signal Box Computer shares this tile; none of this runs for it.
 */
public class TileDispatcherBoard extends TileRailDisplay implements PanelHost {
    // ---- console state (synced to clients by PacketConsole) ----
    private final Map<String, Integer> ps = new HashMap<>();
    private final Map<String, String> pt = new HashMap<>();
    private final Map<String, float[]> anim = new HashMap<>();
    private String pressId = "";
    private long pressAt;
    public boolean burnt;
    public int ac, dc;            // 0 none, 1 ok, 2 wrong
    public String acWhat = "", dcWhat = "";
    private UUID selected;
    private int alarmsAcked;
    private long lampTestUntil;
    private GridData.Net acNet;
    private boolean dirty;

    public static final int LINES = 9;

    public boolean isConsole() {
        return world != null && world.getBlockState(pos).getBlock() instanceof BlockControlConsole;
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getPropertyKeys().contains(BlockDispatcherBoard.FACING) ? s.getValue(BlockDispatcherBoard.FACING) : EnumFacing.NORTH;
    }

    public boolean screenOn() { return !burnt && ac == 1; }

    public boolean deskOn() { return !burnt && dc == 1; }

    // ---- PanelHost ----
    @Override
    public int pv(String key) {
        if (key.equals("lamptest")) return lampTestUntil > (world == null ? 0 : world.getTotalWorldTime()) && deskOn() ? 1 : 0;
        if (!deskOn() && !key.equals("ctl") && !key.equals("cbtc") && !key.equals("page") && !key.equals("allStop")) return 0;   // no 12 V: lamps dark
        return ps.getOrDefault(key, 0);
    }

    @Override
    public String ptext(String key) { return deskOn() || key.startsWith("scr") ? pt.getOrDefault(key, "") : ""; }

    public String screenText(String key) { return pt.getOrDefault(key, ""); }

    @Override
    public boolean pressedRecently(String id, long now) { return id.equals(pressId) && now - pressAt < 6; }

    @Override
    public Map<String, float[]> anim() { return anim; }

    // ---- ticking ----
    @Override
    public void update() {
        super.update();
        if (!world.isRemote && !(this instanceof TileSignalBox) && getBlockType() instanceof BlockSignalBox) {   // a signal box from before the lever frame worked: upgrade it
            NBTTagCompound t = writeToNBT(new NBTTagCompound());
            TileSignalBox nb = new TileSignalBox();
            nb.readFromNBT(t);
            world.setTileEntity(pos, nb);
            return;
        }
        if (world.isRemote || !isConsole()) return;
        long now = world.getTotalWorldTime();
        if (now % 100 == 7) fillParts();
        if (now % 10 == 0) {
            power();
            holdManual();
        }
        if (now % 20 == 3) readouts();
        if (now % 40 == 11 && deskOn()) watchBus();
        if (dirty) send();
    }

    // ---- the 12 V control bus: the WIRED page (requested feature) ----

    private final Map<Long, Integer> busLamps = new HashMap<>();

    /** every machine and piece of gear whose control wire reaches any block of the desk */
    public List<BlockPos> bus() {
        LinkedHashSet<BlockPos> all = new LinkedHashSet<>(WireNet.bus(world, pos));
        if (isConsole()) for (BlockPos p : BlockControlConsole.parts(pos, facing())) all.addAll(WireNet.bus(world, p));
        all.remove(pos);
        return new ArrayList<>(all);
    }

    /** raise an alarm when something on the bus goes to fault (burnt, tripped, lost its 12 V), and when it comes back */
    private void watchBus() {
        Set<Long> live = new HashSet<>();
        for (BlockPos p : bus()) {
            if (!(world.getTileEntity(p) instanceof com.dogpound.railmap.wire.IWiredMachine m)) continue;
            live.add(p.toLong());
            int lamp = m.wiredLamp();
            Integer was = busLamps.put(p.toLong(), lamp);
            if (was == null || was == lamp) continue;
            if (lamp == 3) ProgramData.get(world).alarm(world, "WIRED: " + m.wiredName() + " - FAULT" + (m instanceof com.dogpound.railmap.wire.IWiredGear g ? " (" + g.gearState() + ")" : ""), p);
            else if (was == 3) ProgramData.get(world).alarm(world, "WIRED: " + m.wiredName() + " - back to normal", p);
        }
        busLamps.keySet().retainAll(live);
    }

    /** the WIRED page: one entry per machine with its lamp, state, readings and its real controls */
    public NBTTagCompound wiredList() {
        NBTTagCompound out = new NBTTagCompound();
        out.setBoolean("dead", !deskOn());
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        if (deskOn()) for (BlockPos p : bus()) {
            if (!(world.getTileEntity(p) instanceof com.dogpound.railmap.wire.IWiredMachine m)) continue;
            NBTTagCompound e = new NBTTagCompound();
            e.setLong("p", p.toLong());
            e.setString("n", m.wiredName());
            e.setInteger("c", m.ctlWire());
            e.setInteger("l", m.wiredLamp());
            Map<String, Object> info;
            try { info = m.wiredInfo(); } catch (RuntimeException ex) { info = Collections.emptyMap(); }
            e.setString("s", m instanceof com.dogpound.railmap.wire.IWiredGear g ? g.gearState() : String.valueOf(info.getOrDefault("state", info.getOrDefault("status", ""))));
            net.minecraft.nbt.NBTTagList il = new net.minecraft.nbt.NBTTagList();
            int n = 0;
            for (Map.Entry<String, Object> ie : info.entrySet()) {
                if (ie.getKey().equals("name") || ie.getKey().equals("state")) continue;   // already the row's title and state
                if (n++ >= 14) break;
                Object v = ie.getValue();
                String vs = v instanceof Double || v instanceof Float ? String.format(Locale.ROOT, "%.2f", ((Number) v).doubleValue()) : String.valueOf(v);
                il.appendTag(new net.minecraft.nbt.NBTTagString(ie.getKey() + ": " + (vs.length() > 40 ? vs.substring(0, 40) + "…" : vs)));
            }
            e.setTag("i", il);
            Panel pn = m.wiredPanel();
            net.minecraft.nbt.NBTTagList cl = new net.minecraft.nbt.NBTTagList();
            if (pn != null) for (Panel.C c : pn.cs) {
                if (c.t == Panel.T.LABEL || c.id.isEmpty()) continue;
                NBTTagCompound ct = new NBTTagCompound();
                ct.setString("id", c.id);
                ct.setInteger("t", c.t.ordinal());
                ct.setString("lb", c.label == null ? c.id : c.label);
                ct.setInteger("col", c.colour);
                ct.setInteger("st", m.wiredState(c.id));
                if (c.t == Panel.T.DIGITS) ct.setString("fmt", c.fmt);
                ct.setDouble("sc", c.scale);
                ct.setInteger("max", c.max);
                if (c.positions != null && c.positions.length > 0) ct.setString("pos", String.join("|", c.positions));
                cl.appendTag(ct);
            }
            e.setTag("k", cl);
            list.appendTag(com.dogpound.railmap.wire.Comms.view(world, p, e));   // next-48 #25: through the field RTU's comms (STALE when it can't get through)
        }
        out.setTag("list", list);
        return out;
    }

    /** a control on the WIRED page pressed: only for gear really on this desk's bus, and only with the desk powered */
    public String wiredPress(BlockPos target, String id, EntityPlayer who) {
        if (!deskOn()) return "§cThe desk is dead - it needs 12 V DC instrument power";
        if (!bus().contains(target) || !(world.getTileEntity(target) instanceof com.dogpound.railmap.wire.IWiredMachine m)) return "§cThat isn't wired to this console any more";
        String comms = com.dogpound.railmap.wire.Comms.command(world, target);   // next-48 #25: no comms path = not sent
        if (comms != null) return comms;
        return m.wiredPress(id, who.getName());
    }

    /** a Dispatcher Board placed before it became the console: grow the rest of the console if there's room */
    private void fillParts() {
        EnumFacing f = facing();
        List<BlockPos> parts = BlockControlConsole.parts(pos, f);
        boolean missing = false, room = true;
        for (BlockPos p : parts) {
            if (world.getBlockState(p).getBlock() == com.dogpound.railmap.proxy.CommonProxy.consolePart) continue;
            missing = true;
            if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) room = false;
        }
        if (!missing || !room) return;
        for (BlockPos p : parts)
            if (world.getBlockState(p).getBlock() != com.dogpound.railmap.proxy.CommonProxy.consolePart)
                world.setBlockState(p, com.dogpound.railmap.proxy.CommonProxy.consolePart.getDefaultState(), 3);
    }

    private void power() {
        int acB = ac, dcB = dc;
        boolean burntB = burnt;
        EnumFacing f = facing(), back = f.getOpposite();
        if (burnt) {
            ac = 2; dc = 2;
        } else {
            // mains: a grid machine / cable behind any of the desk's three bottom blocks
            ac = 0;
            acWhat = "no mains supply (feed 120/240 V AC into the back of the desk)";
            acNet = null;
            GridData d = GridData.get(world);
            for (BlockPos p : new BlockPos[]{pos, pos.offset(f.rotateY()), pos.offset(f.rotateYCCW())}) {
                BlockPos nb = p.offset(back);
                if (d.node(nb) == null) continue;                                // any grid machine or cable (cables have no tile)
                GridData.Net n = d.netAt(nb, back.getOpposite());
                if (n == null) continue;
                if (!n.problem.isEmpty()) { acWhat = "supply dead: " + n.problem; continue; }
                Elec.Level L = n.level;
                if (L != null && L.volts > 264) {
                    burn(L.label + " into a 240 V desk");
                    return;
                }
                if (L != null && (!L.ac || L.volts < 100)) { acWhat = "wrong supply: " + L.label + " (needs 120/240 V AC)"; continue; }
                long got = d.draw(n, 1);
                if (got <= 0) { acWhat = "supply can't carry the load"; continue; }
                ac = 1;
                acNet = n;
                acWhat = L == null ? "mains" : L.label;
                break;
            }
            // instruments: 12 V DC on the instrument wire landed on the console
            WireNet.Supply sup = WireNet.power(world, pos);
            if (sup.state == 2) {
                burn(sup.what + " on the 12 V desk wiring");
                return;
            }
            dc = sup.state;
            dcWhat = sup.state == 1 ? "12 V DC" : "no 12 V instrument supply (run the 12 V pair to the desk)";
        }
        if (ac != acB || dc != dcB || burnt != burntB) dirty = true;
    }

    private void burn(String why) {
        burnt = true;
        ac = 2;
        dc = 2;
        acWhat = "BURNT OUT: " + why;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.ARC_BUZZ, net.minecraft.util.SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.FUSE_BLOW, net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1.0f);
        if (world instanceof net.minecraft.world.WorldServer) {
            net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) world;
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.9, 0.5, 0.4, 0.03);
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 8, 0.8, 0.3, 0.3, 0.0);
        }
        RailMap.LOG.info("[IR Extras] Control Center console at {} burnt out: {}", pos, why);
        ProgramData.get(world).alarm(world, "Control Center console burnt out: " + why, pos);
        markDirty();
        dirty = true;
    }

    public String repair() {
        if (!burnt) return null;
        burnt = false;
        ac = 0;
        dc = 0;
        markDirty();
        dirty = true;
        return "§aRepaired - new power supply and desk boards in";
    }

    /** trains a player drives that the console holds: keep their brakes on */
    private void holdManual() {
        int dim = world.provider.getDimension();
        RailwayData data = RailwayData.get(world);
        for (Locomotive l : locos()) {
            if (data.train(l.getUUID()) != null) continue;          // driverless: Autopilot obeys the hold itself
            if (ConsoleControl.held(dim, l.getUUID())) {
                l.setThrottle(0);
                l.setTrainBrake(1);
            }
        }
    }

    // ---- the trains it can see ----
    private List<Locomotive> locos() {
        List<Locomotive> out = new ArrayList<>();
        try {
            out.addAll(cam72cam.mod.world.World.get(world).getEntities(Locomotive.class));
        } catch (LinkageError | RuntimeException e) {
            return out;
        }
        out.removeIf(l -> l.isDead());
        out.sort(Comparator.comparing(this::name, String.CASE_INSENSITIVE_ORDER).thenComparing(l -> l.getUUID()));
        return out;
    }

    private String name(Locomotive l) {
        RailwayData.AutoTrain a = RailwayData.get(world).train(l.getUUID());
        if (a != null && a.label != null && !a.label.isEmpty()) return a.label;
        return l.getDefinition().name();
    }

    private Locomotive selectedLoco(List<Locomotive> ls) {
        for (Locomotive l : ls) if (l.getUUID().equals(selected)) return l;
        if (!ls.isEmpty()) {
            selected = ls.get(0).getUUID();
            return ls.get(0);
        }
        return null;
    }

    // ---- pressing the desk ----
    public void press(String id, EntityPlayer player) {
        long now = world.getTotalWorldTime();
        String msg = operate(id, player);
        pressId = id;
        pressAt = now;
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_PUSH, net.minecraft.util.SoundCategory.BLOCKS, 0.4f, 1.0f);
        if (msg != null && !msg.isEmpty() && player != null) player.sendStatusMessage(new TextComponentString(msg), true);
        readouts();
        markDirty();
        send();
    }

    private String operate(String id, EntityPlayer player) {
        if (burnt) return "§cThe console is burnt out - repair it with the electrician's screwdriver";
        if (!deskOn()) return "§cThe desk is dead - it needs 12 V DC instrument power";
        int dim = world.provider.getDimension();
        if (id.equals("ctl")) {
            put("ctl", ps.getOrDefault("ctl", 0) == 1 ? 0 : 1);
            return ps.get("ctl") == 1 ? "§aControl key ON - the desk is live" : "§eControl key OFF - the desk is locked";
        }
        if (id.equals("ltest")) {
            lampTestUntil = world.getTotalWorldTime() + 60;
            return "Lamp test";
        }
        if (ps.getOrDefault("ctl", 0) != 1) return "§eTurn the CONTROL key to ON first";
        List<Locomotive> ls = locos();
        Locomotive sel = selectedLoco(ls);
        switch (id) {
            case "trPrev":
            case "trNext": {
                if (ls.isEmpty()) return "No trains";
                int i = sel == null ? 0 : ls.indexOf(sel);
                i = Math.floorMod(i + (id.equals("trNext") ? 1 : -1), ls.size());
                selected = ls.get(i).getUUID();
                return "Selected " + name(ls.get(i));
            }
            case "trHold":
                if (sel == null) return "No train selected";
                ConsoleControl.hold(dim, sel.getUUID(), true);
                return "§eHolding " + name(sel);
            case "trGo":
                if (sel == null) return "No train selected";
                ConsoleControl.hold(dim, sel.getUUID(), false);
                if (RailwayData.get(world).train(sel.getUUID()) == null) sel.setTrainBrake(0);
                return "§aReleased " + name(sel);
            case "trHorn":
                if (sel == null) return "No train selected";
                return TrainControl.apply(world, null, sel.internal.getEntityId(), TrainControl.Cmd.HORN);
            case "trHome": {
                if (sel == null) return "No train selected";
                RailwayData data = RailwayData.get(world);
                RailwayData.AutoTrain a = data.train(sel.getUUID());
                if (a == null || a.home == 0) return "§e" + name(sel) + " has no home station (it isn't driverless)";
                a.mode = RailwayData.Mode.SEND;
                a.calls.clear();
                data.markDirty();
                ConsoleControl.hold(dim, sel.getUUID(), false);
                return "Sending " + name(sel) + " home";
            }
            case "trStop":
                if (sel == null) return "No train selected";
                ConsoleControl.hold(dim, sel.getUUID(), true);
                TrainControl.apply(world, null, sel.internal.getEntityId(), TrainControl.Cmd.EMERGENCY_STOP);
                return "§cEMERGENCY STOP: " + name(sel);
            case "allStop": {
                boolean on = !ConsoleControl.allStop(dim);
                ConsoleControl.allStop(dim, on);
                put("allStop", on ? 1 : 0);
                if (on) {
                    for (Locomotive l : ls) TrainControl.apply(world, null, l.internal.getEntityId(), TrainControl.Cmd.EMERGENCY_STOP);
                    ProgramData.get(world).alarm(world, "ALL STOP pressed at the control center", pos);
                    for (EntityPlayer p : world.playerEntities)
                        if (p.getDistanceSq(pos) < 300 * 300) p.sendMessage(new TextComponentString("§4⛔ ALL STOP - every train stopped by the control center"));
                    return "§4ALL STOP - every train stopping";
                }
                for (Locomotive l : ls) if (RailwayData.get(world).train(l.getUUID()) == null && !ConsoleControl.heldByName(dim, l.getUUID())) l.setTrainBrake(0);
                return "§aALL STOP released (twist to reset)";
            }
            case "cbtc": {
                boolean on = ps.getOrDefault("cbtc", 0) != 1;
                RailwayData data = RailwayData.get(world);
                for (RailwayData.Line l : data.lines().values()) l.movingBlock = on;
                data.markDirty();
                put("cbtc", on ? 1 : 0);
                return on ? "§bMoving block (CBTC) ON for every line" : "Fixed-block signalling on every line";
            }
            case "ack":
                alarmsAcked = ProgramData.get(world).alarms.size();
                return "Alarms acknowledged";
            case "cancel": {
                int n = 0;
                for (Interlocking.Route r : new ArrayList<>(Interlocking.routes(world))) { Interlocking.cancelRoute(world, r.start); n++; }
                return n == 0 ? "No routes set" : "Cancelled " + n + " route(s)";
            }
            case "page":
                put("page", (ps.getOrDefault("page", 0) + 1) % 4);
                return "Screen: " + new String[]{"MAP", "TRAINS", "POWER", "ALARMS"}[ps.get("page")];
            case "zoomIn":
                put("zoom", Math.min(6, ps.getOrDefault("zoom", 0) + 1));
                return "Zoom in";
            case "zoomOut":
                put("zoom", Math.max(-4, ps.getOrDefault("zoom", 0) - 1));
                return "Zoom out";
            case "fit":
                put("zoom", 0);
                return "Whole network";
            case "rescan":
                rescan(true);
                return "Rescanning the railway";
            default:
                return "";
        }
    }

    private void put(String k, int v) { ps.put(k, v); }

    // ---- readouts: little screens, lamps, the big screen's text pages ----
    private void readouts() {
        int dim = world.provider.getDimension();
        long t = (world.getWorldTime() + 6000) % 24000;
        pt.put("clock", String.format(Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000));
        pt.put("supply", burnt ? "BURNT" : ac == 1 ? (acNet != null && acNet.level != null ? Math.round(acNet.level.volts * acNet.volt) + "V" : "MAINS") : "NO AC");
        pt.put("load", ac == 1 ? (acNet != null ? String.format(Locale.ROOT, "%.0fkW", acNet.lastSecond * 2.0 / 20) : "--") : "--");
        List<String> alarms = ProgramData.get(world).alarms;
        int unacked = Math.max(0, alarms.size() - alarmsAcked);
        pt.put("alarms", String.valueOf(alarms.size()));
        put("lpAC", ac == 1 ? 1 : 0);
        put("lp12", dc == 1 ? 1 : 0);
        boolean anyCbtc = false;
        for (RailwayData.Line l : RailwayData.get(world).lines().values()) anyCbtc |= l.movingBlock;
        put("lpCbtc", anyCbtc ? 1 : 0);
        put("cbtc", anyCbtc ? 1 : 0);
        put("lpRoute", Interlocking.routes(world).isEmpty() ? 0 : 1);
        put("lpAlarm", unacked > 0 ? 1 : 0);
        put("lpOk", ac == 1 && dc == 1 && unacked == 0 ? 1 : 0);
        put("allStop", ConsoleControl.allStop(dim) ? 1 : 0);
        put("lpScan", getNetwork().isEmpty() ? 0 : 1);

        List<Locomotive> ls = locos();
        Locomotive sel = selectedLoco(ls);
        if (sel != null) {
            double kmh = Math.abs(sel.getCurrentSpeed().metric());
            Autopilot.Run r = Autopilot.run(sel.getUUID());
            boolean held = ConsoleControl.held(dim, sel.getUUID());
            pt.put("trName", (ls.indexOf(sel) + 1) + "/" + ls.size() + " " + name(sel));
            pt.put("trInfo", String.format(Locale.ROOT, "%d km/h %s", Math.round(kmh), held ? "HELD" : r != null ? "AUTO" : "MANUAL"));
            put("trMove", kmh > 1 ? 1 : 0);
            put("trHeld", held ? 1 : 0);
            put("trProt", Protection.penalised(sel.getUUID()) ? 1 : 0);
            put("trStop", held && ConsoleControl.heldByName(dim, sel.getUUID()) ? 0 : 0);
        } else {
            pt.put("trName", "NO TRAINS");
            pt.put("trInfo", "");
            put("trMove", 0);
            put("trHeld", 0);
            put("trProt", 0);
        }

        // the big screen's text pages
        int page = ps.getOrDefault("page", 0);
        List<String> lines = new ArrayList<>();
        String title;
        if (page == 1) {
            title = "TRAINS  (" + ls.size() + ")";
            for (Locomotive l : ls) {
                Autopilot.Run r = Autopilot.run(l.getUUID());
                MovingBlock.Auth mb = MovingBlock.last(l.getUUID());
                String st = ConsoleControl.held(dim, l.getUUID()) ? "HELD" : r != null ? r.status : Protection.penalised(l.getUUID()) ? "protection braking" : "driven by hand";
                String gap = mb != null && mb.gapM >= 0 ? String.format(Locale.ROOT, " | %dm to train ahead", Math.round(mb.gapM)) : "";
                lines.add(String.format(Locale.ROOT, "%s%-14s %3d km/h  %s%s", l.getUUID().equals(selected) ? ">" : " ",
                        cut(name(l), 14), Math.round(Math.abs(l.getCurrentSpeed().metric())), st, gap));
            }
        } else if (page == 2) {
            title = "POWER";
            lines.add("Console mains: " + acWhat);
            lines.add("Desk 12 V: " + dcWhat);
            // traction load flow: what the trains draw from the line and give back braking
            long drawKw = 0, regenKw = 0;
            List<String> per = new ArrayList<>();
            com.dogpound.railmap.server.RailProps rp = com.dogpound.railmap.server.RailProps.get(world);
            for (Locomotive l : ls) {
                com.dogpound.railmap.server.RailProps.Props pr = rp.peek(l.getUUID());
                String st = com.dogpound.railmap.server.Electric.status(world, l);
                if (pr == null || st.isEmpty()) continue;
                drawKw += pr.kwDraw;
                if (!pr.regenTo.isEmpty() && !pr.regenTo.equals("resistors")) regenKw += pr.kwRegen;
                per.add(" " + cut(name(l), 12) + ": " + st.replaceAll("\u00a7.", ""));
            }
            if (!per.isEmpty()) {
                lines.add(String.format(Locale.ROOT, "Traction: %s drawn, %s regenerated", kw(drawKw), kw(regenKw)));
                for (String x : per) if (lines.size() < LINES - 2) lines.add(x);
            }
            for (GridData.Net n : GridData.get(world).nets()) {
                if (lines.size() >= LINES) break;
                lines.add(String.format(Locale.ROOT, "%s  %.0f kW  %.2f pu  %s", n.level == null ? "network" : n.level.label,
                        n.lastSecond * 2.0 / 20, n.volt, n.problem.isEmpty() ? "LIVE" : n.problem));
            }
        } else if (page == 3) {
            title = "ALARMS  (" + unacked + " new)";
            for (int i = alarms.size() - 1; i >= 0 && lines.size() < LINES; i--)
                lines.add((i >= alarmsAcked ? "! " : "  ") + alarms.get(i));
            if (alarms.isEmpty()) lines.add("No alarms");
        } else {
            title = "NETWORK MAP";
        }
        pt.put("scrTitle", title);
        for (int i = 0; i < LINES; i++) pt.put("scr" + i, i < lines.size() ? lines.get(i) : "");
        dirty = true;
    }

    private static String kw(long kw) { return kw >= 1000 ? String.format(Locale.ROOT, "%.1f MW", kw / 1000.0) : kw + " kW"; }

    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }

    // ---- sync + save ----
    private void send() {
        dirty = false;
        if (world == null || world.isRemote) return;
        PacketConsole p = new PacketConsole(pos, writeConsole(new NBTTagCompound()));
        for (EntityPlayer pl : world.playerEntities)
            if (pl instanceof EntityPlayerMP && pl.getDistanceSq(pos) < 64 * 64) RailMap.NETWORK.sendTo(p, (EntityPlayerMP) pl);
    }

    public NBTTagCompound writeConsole(NBTTagCompound t) {
        NBTTagCompound s = new NBTTagCompound(), x = new NBTTagCompound();
        ps.forEach(s::setInteger);
        pt.forEach(x::setString);
        t.setTag("cps", s);
        t.setTag("cpt", x);
        t.setString("cpress", pressId);
        t.setLong("cpressAt", pressAt);
        t.setBoolean("cburnt", burnt);
        t.setInteger("cac", ac);
        t.setInteger("cdc", dc);
        t.setLong("cltest", lampTestUntil);
        return t;
    }

    public void readConsole(NBTTagCompound t) {
        if (!t.hasKey("cps")) return;
        ps.clear();
        pt.clear();
        NBTTagCompound s = t.getCompoundTag("cps"), x = t.getCompoundTag("cpt");
        for (String k : s.getKeySet()) ps.put(k, s.getInteger(k));
        for (String k : x.getKeySet()) pt.put(k, x.getString(k));
        pressId = t.getString("cpress");
        pressAt = t.getLong("cpressAt");
        burnt = t.getBoolean("cburnt");
        ac = t.getInteger("cac");
        dc = t.getInteger("cdc");
        lampTestUntil = t.getLong("cltest");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeConsole(tag);
        if (selected != null) tag.setUniqueId("csel", selected);
        tag.setInteger("cack", alarmsAcked);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readConsole(tag);
        if (tag.hasUniqueId("csel")) selected = tag.getUniqueId("csel");
        alarmsAcked = tag.getInteger("cack");
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos.add(-2, 0, -2), pos.add(3, 3, 3));
    }

    /** the desk panel: which control is a player pointing at (null = none / the screen) */
    public Panel.C hit(EntityPlayer player) {
        return ConsoleGeometry.hit(this, player);
    }
}
