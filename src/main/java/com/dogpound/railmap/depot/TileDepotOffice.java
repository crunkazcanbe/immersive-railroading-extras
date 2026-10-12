package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.server.WearData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Depot Control Office (her ChatGPT list #51-54, #58-62): the shift manager's desk. The LED job board lists the open
 * workshop job cards (NEW EXAM / NEW REPAIR raise them for the selected vehicle; ASSIGN, START, CANCEL work them - a
 * started shop-floor card is done by the depot's fitters, three times faster with its assigned player beside the
 * vehicle, using spares from the Stores racks), the monitor pages through the fleet (condition, remaining life, exam
 * plan, components and their serials, diagnostic downloads, history), the plan selectors set the exam plan of the
 * selected vehicle's type, and the routing buttons send it to the depot, back to service, to the spares pool or onto a
 * line (with each line's target number of trains). AUTO ROUTE, NIGHT STABLING and AUTO RETURN keys hand that to the
 * fleet manager. DOWNLOAD reads the nearest locomotive's event recorder; PRINT gives a printout of the page. The
 * stabling road is set by wrench-picking a rail and clicking the ROAD plate. Drawers, a filing cabinet and a cupboard
 * are real storage; the shelf shows what stands on it.
 */
public class TileDepotOffice extends TileFurniture implements DepotDesk.Linkable {
    public static final int CAP_LATHE = 1, CAP_BOGIE = 2, CAP_BRAKE = 4, CAP_SERVICE = 8, CAP_FUEL = 16, CAP_STORES = 32;
    static final String[] PAGES = {"JOBS", "FLEET", "UNIT", "PLAN", "ALLOC", "COMPS", "DLOAD", "LOG"};
    static final int[][] PLAN_POS = {{25, 50, 100, 200, 0}, {12, 24, 48, 96, 0}, {3, 7, 14, 28, 0}, {3, 5, 10, 20, 0}};

    private long road;
    private int jcur, ucur, lcur, caps;
    private final Map<Integer, String> status = new HashMap<>();
    private String lastMsg = "";

    public TileDepotOffice() {
        super(new String[]{"d1", "d2", "d3", "d4", "d5", "d6", "files", "cup"},
                new String[]{"Desk drawer", "Desk drawer", "Desk drawer", "Desk drawer", "Desk drawer", "Desk drawer", "Job card files", "Manuals cupboard"},
                new int[]{9, 9, 9, 9, 9, 9, 27, 18}, 6);
    }

    // ---- registry --------------------------------------------------------------------------------------------------
    private static final Map<Integer, Set<TileDepotOffice>> LOADED = new HashMap<>();
    public static Collection<TileDepotOffice> loaded(World w) { Set<TileDepotOffice> s = LOADED.get(w.provider.getDimension()); return s == null ? Collections.emptySet() : new ArrayList<>(s); }
    @Override public void onLoad() { if (world != null && !world.isRemote) LOADED.computeIfAbsent(world.provider.getDimension(), k -> new HashSet<>()).add(this); }
    @Override public void invalidate() { super.invalidate(); drop(); }
    @Override public void onChunkUnload() { super.onChunkUnload(); drop(); }
    private void drop() { if (world != null) { Set<TileDepotOffice> s = LOADED.get(world.provider.getDimension()); if (s != null) s.remove(this); } }

    public long road() { return road; }
    public int caps() { return caps; }
    public boolean autoRoute() { return term.powered() && ctl.get("k_auto") == 1; }
    public boolean nightStabling() { return term.powered() && ctl.get("k_night") == 1; }
    public boolean autoReturn() { return term.powered() && ctl.get("k_ret") == 1; }

    public String depotName() {
        StationData sd = StationData.get(world);
        String best = null;
        double bd = 96 * 96;
        for (Map.Entry<Long, String> e : sd.names().entrySet()) {
            if (e.getKey() == road) continue;
            double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (d < bd) { bd = d; best = e.getValue(); }
        }
        return best != null ? best + " Depot" : "Depot " + Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 1000);
    }

    @Override public String wiredName() { return "Depot control office"; }
    @Override public String gearState() { return !term.powered() ? "NO 12 V" : depotName() + " · " + FleetData.get(world).openJobs(null).size() + " open job cards"; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    static double[][] SPOTS = {{20.0, 7.0, 13.4}, {21.8, 7.0, 13.4}, {23.6, 7.0, 13.4}, {25.4, 7.0, 13.4}, {27.2, 7.0, 13.4}, {29.0, 7.0, 13.4}};

    @Override protected boolean mechanical(String id) { return id.startsWith("s_") || id.startsWith("k_"); }

    // ---- the fleet the office looks after ------------------------------------------------------------------------------
    List<UUID> fleet() {
        FleetData f = FleetData.get(world);
        List<UUID> o = new ArrayList<>(f.units().keySet());
        o.removeIf(u -> f.peek(u).name.isEmpty());
        o.sort(Comparator.comparing((UUID u) -> !f.peek(u).loco).thenComparing(u -> f.peek(u).name));
        return o;
    }

    UUID unit() {
        List<UUID> l = fleet();
        if (l.isEmpty()) return null;
        ucur = Math.floorMod(ucur, l.size());
        return l.get(ucur);
    }

    List<FleetData.Job> jobs() {
        List<FleetData.Job> o = new ArrayList<>();
        for (FleetData.Job j : FleetData.get(world).jobs) if (j.state < 3 && (j.depot == 0 || j.depot == pos.toLong())) o.add(j);
        o.sort(Comparator.comparingInt(j -> j.id));
        return o;
    }

    FleetData.Job job() {
        List<FleetData.Job> l = jobs();
        if (l.isEmpty()) return null;
        jcur = Math.floorMod(jcur, l.size());
        return l.get(jcur);
    }

    List<String> lines() { List<String> l = new ArrayList<>(RailwayData.get(world).lines().keySet()); Collections.sort(l); return l; }
    String line() { List<String> l = lines(); if (l.isEmpty()) return ""; lcur = Math.floorMod(lcur, l.size()); return l.get(lcur); }

    EntityRollingStock entity(UUID id) {
        try { return cam72cam.mod.world.World.get(world).getEntity(id, EntityRollingStock.class); } catch (RuntimeException e) { return null; }
    }

    boolean here(UUID id) {
        EntityRollingStock s = entity(id);
        if (s == null) return false;
        Vec3d p = s.getPosition();
        if (pos.distanceSqToCenter(p.x, p.y, p.z) > Jobs.REACH * Jobs.REACH) return false;
        return !(s instanceof EntityMoveableRollingStock m) || m.getCurrentSpeed() == null || Math.abs(m.getCurrentSpeed().metric()) < 1;
    }

    // ---- buttons ---------------------------------------------------------------------------------------------------
    private long testUntil;

    /** LAMP TEST lights every lamp and segment for 3 s */
    @Override public int controlValue(String id) { return term.powered() && ctl.get("lamptest") == 1 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }
    @Override public void update() { super.update(); if (!world.isRemote && testUntil > 0 && world.getTotalWorldTime() > testUntil) { testUntil = 0; ctl.set("lamptest", 0); controlsChanged(); } }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.equals("test")) { testUntil = world.getTotalWorldTime() + 60; ctl.set("lamptest", 1); return "§7Lamp test"; }
        FleetData f = FleetData.get(world);
        UUID u = unit();
        FleetData.Unit fu = u == null ? null : f.unit(u);
        FleetData.Job j = job();
        String r = switch (id) {
            case "prev" -> { jcur--; yield ""; }
            case "next" -> { jcur++; yield ""; }
            case "uprev" -> { ucur--; yield ""; }
            case "unext" -> { ucur++; yield ""; }
            case "lprev" -> { lcur--; yield ""; }
            case "lnext" -> { lcur++; yield ""; }
            case "newexam" -> fu == null ? "§7No vehicle selected" : "§aJob card #" + Jobs.raise(world, u, fu.name, Jobs.EXAM, -1, pos.toLong(), "raised at the office").id + " EXAM for " + fu.name;
            case "newrep" -> newRepair(u, fu);
            case "assign" -> {
                if (j == null) yield "§7No job card selected";
                j.assignee = who == null ? "Fitter" : who.getName();
                if (j.state == 0) j.state = 1;
                f.markDirty();
                yield "§aJob #" + j.id + " assigned to " + j.assignee;
            }
            case "start" -> {
                if (j == null) yield "§7No job card selected";
                if (!Jobs.shopFloor(j.kind)) yield "§e#" + j.id + " " + j.task + " is done at the " + (j.kind == Jobs.WHEELS ? "Wheel Lathe" : j.kind == Jobs.BOGIE ? "Bogie Drop" : j.kind == Jobs.BRAKE_TEST ? "Brake Test Rig" : "Servicing Point");
                if (j.assignee.isEmpty()) j.assignee = "Fitter";
                j.state = 2; j.started = world.getTotalWorldTime(); j.depot = pos.toLong();
                f.markDirty();
                yield "§aJob #" + j.id + " started - " + (here(j.unit) ? "vehicle in the depot" : "§ewaiting for the vehicle to arrive");
            }
            case "cancel" -> {
                if (j == null) yield "§7No job card selected";
                j.state = 4; j.done = world.getTotalWorldTime();
                f.unit(j.unit).note(world, "job card #" + j.id + " " + j.task + " cancelled");
                f.markDirty();
                yield "§eJob #" + j.id + " cancelled";
            }
            case "todepot" -> u == null ? "§7No vehicle" : Fleet.sendToDepot(world, u, this, "sent from the office");
            case "retsvc" -> u == null ? "§7No vehicle" : Fleet.returnToService(world, u, false);
            case "spare" -> u == null ? "§7No vehicle" : Fleet.makeSpare(world, u, this);
            case "alloc" -> u == null || line().isEmpty() ? "§7Select a vehicle and a line" : Fleet.allocate(world, u, line());
            case "stopsvc" -> {
                if (fu == null) yield "§7No vehicle";
                fu.role = fu.role == FleetData.STOPPED ? FleetData.DEPOT : FleetData.STOPPED;
                fu.note(world, fu.role == FleetData.STOPPED ? "STOPPED out of service at the office" : "released from STOPPED");
                f.markDirty();
                yield fu.role == FleetData.STOPPED ? "§c" + fu.name + " STOPPED - out of service" : "§a" + fu.name + " released (depot)";
            }
            case "tgtup", "tgtdn" -> {
                String l = line();
                if (l.isEmpty()) yield "§7No lines";
                int t = Math.max(0, Math.min(12, f.lineTarget.getOrDefault(l, 0) + (id.equals("tgtup") ? 1 : -1)));
                f.lineTarget.put(l, t);
                f.markDirty();
                yield "§e" + l + ": " + (t == 0 ? "not managed" : t + " train" + (t == 1 ? "" : "s") + " wanted");
            }
            case "planset", "planall" -> {
                int[] p = new int[4];
                String[] sel = {"s_km", "s_hrs", "s_days", "s_flt"};
                for (int i = 0; i < 4; i++) p[i] = PLAN_POS[i][Math.floorMod(ctl.get(sel[i]), PLAN_POS[i].length)];
                String key = id.equals("planall") || fu == null ? "*" : fu.def;
                f.plans.put(key, p);
                f.markDirty();
                yield String.format(Locale.ROOT, "§aExam plan for %s: %s km · %s h · %s days · %s faults", key.equals("*") ? "all stock" : key,
                        p[0] == 0 ? "-" : p[0], p[1] == 0 ? "-" : p[1], p[2] == 0 ? "-" : p[2], p[3] == 0 ? "-" : p[3]);
            }
            case "dload" -> {
                Locomotive l = nearestLoco(32);
                if (l == null) yield "§7No locomotive within 32 blocks to download";
                FleetData.Download d = Diagnostics.download(world, l);
                ctl.set("s_page", 6);
                yield "§aDownloaded " + d.unit + " (#" + d.id + ", " + d.lines.size() + " lines)";
            }
            case "print" -> {
                if (who == null) yield "§7The printer is at the desk";
                List<String> lines = page(Math.floorMod(ctl.get("s_page"), PAGES.length), 40);
                if (!who.inventory.addItemStackToInventory(Diagnostics.book(depotName() + " " + PAGES[Math.floorMod(ctl.get("s_page"), PAGES.length)], depotName(), lines)))
                    who.dropItem(Diagnostics.book("Printout", depotName(), lines), false);
                yield "§aPrinted";
            }
            case "road" -> "§7Pick a rail with the Signal Wrench, then click this ROAD plate with it";
            default -> null;
        };
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    private String newRepair(UUID u, FleetData.Unit fu) {
        if (fu == null) return "§7No vehicle selected";
        WearData.Record r = WearData.get(world).peek(u);
        if (r == null || r.worst() < 1) return "§7" + fu.name + " has nothing worn to repair";
        WearData.Part p = r.worstPart();
        int kind = p == WearData.Part.WHEELS ? Jobs.WHEELS : Jobs.REPAIR;
        FleetData.Job j = Jobs.raise(world, u, fu.name, kind, kind == Jobs.REPAIR ? p.ordinal() : -1, pos.toLong(), Math.round(r.wear[p.ordinal()]) + "% worn");
        return "§aJob card #" + j.id + " " + j.task + " for " + fu.name;
    }

    Locomotive nearestLoco(double reach) {
        Locomotive best = null;
        double bd = reach * reach;
        for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
            Vec3d p = l.getPosition();
            double d = pos.distanceSqToCenter(p.x, p.y, p.z);
            if (!l.isDead() && d < bd) { bd = d; best = l; }
        }
        return best;
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (id.startsWith("k_")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            String what = id.equals("k_auto") ? "AUTO ROUTE" : id.equals("k_night") ? "NIGHT STABLING" : "AUTO RETURN";
            return "§e" + what + " " + (ctl.get(id) == 1 ? "ON" : "OFF");
        }
        return null;
    }

    /** the Signal Wrench: a picked rail on the ROAD plate becomes the depot's stabling road (a named stop) */
    public String link(String control, BlockPos pick) {
        if (!control.equals("road")) return null;
        StationData sd = StationData.get(world);
        if (road != 0 && sd.names().containsKey(road) && sd.names().get(road).endsWith("stabling road")) sd.setName(BlockPos.fromLong(road), "");
        road = pick.toLong();
        if (sd.nameAt(pick) == null) sd.setName(pick, depotName() + " stabling road");
        markDirty();
        return "§aStabling road set: trains sent to " + depotName() + " stop at " + pick.getX() + " " + pick.getY() + " " + pick.getZ();
    }

    // ---- every second -------------------------------------------------------------------------------------------------
    @Override
    protected void serverTick() {
        if (world.getTotalWorldTime() % 20 != 7) return;
        if (world.getTotalWorldTime() % 600 == 7) caps = scanCaps();
        if (!term.powered()) return;
        FleetData f = FleetData.get(world);
        for (FleetData.Job j : jobs()) {
            if (j.state != 2 || !Jobs.shopFloor(j.kind)) continue;
            boolean playerAt = false;
            EntityRollingStock s = entity(j.unit);
            if (s != null && !j.assignee.equals("Fitter"))
                for (EntityPlayer p : world.playerEntities) { Vec3d v = s.getPosition(); if (p.getName().equals(j.assignee) && p.getDistanceSq(v.x, v.y, v.z) < 64) playerAt = true; }
            status.put(j.id, Jobs.work(world, f, j, pos, here(j.unit), playerAt));
        }
    }

    private int scanCaps() {
        int c = 0;
        for (TileEntity t : world.loadedTileEntityList) {
            if (t.getPos().distanceSq(pos) > Jobs.REACH * Jobs.REACH) continue;
            if (t instanceof TileWheelLathe) c |= CAP_LATHE;
            else if (t instanceof TileBogieDrop) c |= CAP_BOGIE;
            else if (t instanceof TileBrakeTest) c |= CAP_BRAKE;
            else if (t instanceof TileServicingPoint || t instanceof TileTrainWash) c |= CAP_SERVICE;
            else if (t instanceof TileFuelPoint) c |= CAP_FUEL;
            else if (t instanceof TileDepotStores) c |= CAP_STORES;
        }
        return c;
    }

    // ---- screens -------------------------------------------------------------------------------------------------------
    @Override
    protected boolean refresh() {
        boolean ch = false;
        FleetData f = FleetData.get(world);
        WearData wd = WearData.get(world);
        List<FleetData.Job> js = jobs();
        if (!js.isEmpty()) jcur = Math.floorMod(jcur, js.size());
        int top = js.isEmpty() ? 0 : jcur / 6 * 6;
        for (int i = 0; i < 6; i++) {
            FleetData.Job j = top + i < js.size() ? js.get(top + i) : null;
            ch |= com.dogpound.railmap.block.StationControls.text(ctl, "j" + (i + 1), j == null ? "" : Jobs.line(j));
            ch |= com.dogpound.railmap.block.StationControls.put(ctl, "j" + (i + 1) + "s", j != null && top + i == jcur);
        }
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "jhead", js.size() + " OPEN CARDS  " + (js.isEmpty() ? "" : "PAGE " + (top / 6 + 1) + "/" + ((js.size() + 5) / 6)) + "  " + DepotClock.stamp(world));
        int page = Math.floorMod(ctl.get("s_page"), PAGES.length);
        List<String> p = page(page, 6);
        for (int i = 0; i < 6; i++) ch |= com.dogpound.railmap.block.StationControls.text(ctl, "m" + (i + 1), i < p.size() ? cut(p.get(i), 44) : "");
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "msg", cut(lastMsg, 44));
        // hood lamps + counters
        int due = 0, crit = 0, failed = 0, spares = 0, routed = 0, n = 0, avail = 0;
        for (UUID id : fleet()) {
            FleetData.Unit u = f.peek(id);
            n++;
            if (u.dueAlarm) due++;
            WearData.Record r = wd.peek(id);
            int lv = Condition.worst(r);
            if (lv == Condition.CRITICAL) crit++;
            if (lv == Condition.FAILED || u.failed) failed++;
            if (u.role == FleetData.SPARE) spares++;
            if (u.role == FleetData.DEPOT) routed++;
            if (u.role != FleetData.STOPPED && u.role != FleetData.DEPOT && lv < Condition.CRITICAL) avail++;
        }
        boolean parts = false;
        for (Map.Entry<Integer, String> e : status.entrySet()) if (e.getValue().startsWith("WAITING PARTS")) { FleetData.Job j = f.job(e.getKey()); if (j != null && j.state == 2) parts = true; }
        ch |= put("pwr", true) | put("due", due > 0) | put("crit", crit > 0) | put("fail", failed > 0) | put("parts", parts)
                | put("spare", spares > 0) | put("routed", routed > 0) | put("night", nightStabling());
        ch |= put("nopen", js.size()) | put("nfleet", n) | put("nspare", spares) | put("avail", n == 0 ? 0 : avail * 100 / n);
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "roadt", road == 0 ? "NO ROAD SET" : cut(String.valueOf(StationData.get(world).names().getOrDefault(road, "ROAD")), 22));
        ch |= com.dogpound.railmap.block.StationControls.text(ctl, "capt", capText());
        return ch;
    }

    private boolean put(String id, boolean v) { return com.dogpound.railmap.block.StationControls.put(ctl, id, v); }
    private boolean put(String id, int v) { return com.dogpound.railmap.block.StationControls.put(ctl, id, v); }

    String capText() {
        StringBuilder b = new StringBuilder();
        String[] n = {"LATHE", "BOGIE", "BRAKE", "WASH", "FUEL", "STORES"};
        for (int i = 0; i < n.length; i++) if ((caps & (1 << i)) != 0) b.append(b.length() > 0 ? " " : "").append(n[i]);
        return b.length() == 0 ? "NO DEPOT PLANT" : b.toString();
    }

    /** the monitor / printout for a page */
    List<String> page(int page, int max) {
        FleetData f = FleetData.get(world);
        WearData wd = WearData.get(world);
        List<String> o = new ArrayList<>();
        UUID uid = unit();
        FleetData.Unit u = uid == null ? null : f.peek(uid);
        WearData.Record r = uid == null ? null : wd.peek(uid);
        switch (page) {
            case 0 -> {
                FleetData.Job j = job();
                if (j == null) { o.add("NO OPEN JOB CARDS"); o.add("NEW EXAM / NEW REPAIR raise one for"); o.add("the selected vehicle: " + (u == null ? "-" : u.name)); break; }
                o.add("#" + j.id + " " + j.task + " - " + j.unitName);
                o.add(Jobs.STATES[j.state] + " · " + (j.assignee.isEmpty() ? "UNASSIGNED" : j.assignee) + " · RAISED " + age(j.created));
                StringBuilder sp = new StringBuilder();
                for (ItemSpare.Spare s : ItemSpare.Spare.values()) if (j.spares[s.ordinal()] > 0)
                    sp.append(sp.length() > 0 ? ", " : "").append(j.spares[s.ordinal()]).append("x ").append(s.label).append(" (").append(Stores.count(world, pos, s)).append(" in stores)");
                o.add("PARTS: " + (sp.length() == 0 ? "none" : sp));
                o.add("STATUS: " + status.getOrDefault(j.id, j.state == 2 ? "IN WORK" : Jobs.shopFloor(j.kind) ? "PRESS START" : "AT THE MACHINE"));
                o.add("VEHICLE " + (here(j.unit) ? "IN DEPOT" : "NOT HERE"));
                if (!j.note.isEmpty()) o.add(j.note);
            }
            case 1 -> {
                List<UUID> l = fleet();
                int from = Math.max(0, Math.min(ucur - max / 2, l.size() - max));
                for (int i = from; i < Math.min(l.size(), from + max); i++) {
                    FleetData.Unit x = f.peek(l.get(i));
                    WearData.Record xr = wd.peek(l.get(i));
                    o.add(String.format(Locale.ROOT, "%s%s %s %d%% %s", i == ucur ? ">" : " ", cut(x.name, 16), FleetData.ROLES[x.role], xr == null ? 0 : Math.round(xr.worst()),
                            Condition.NAMES[Condition.worst(xr)]));
                }
                if (l.isEmpty()) o.add("NO VEHICLES SEEN YET");
            }
            case 2 -> {
                if (u == null) { o.add("NO VEHICLE"); break; }
                o.add(u.name + " · " + FleetData.ROLES[u.role] + (u.failed ? " · FAILED" : ""));
                if (r != null) {
                    StringBuilder b = new StringBuilder();
                    for (WearData.Part p : WearData.Part.values())
                        b.append(p.label.substring(0, 3).toUpperCase(Locale.ROOT)).append(' ').append(Math.round(r.wear[p.ordinal()])).append(Condition.NAMES[Condition.level(r.wear[p.ordinal()])].charAt(0)).append(' ');
                    o.add(b.toString().trim());
                }
                double[] fp = Predict.first(u);
                o.add(fp == null ? "RUL: NOT ENOUGH TREND YET" : String.format(Locale.ROOT, "RUL: %s FAILS IN ~%.1f KM", WearData.Part.values()[(int) fp[0]].label.toUpperCase(Locale.ROOT), fp[1]));
                o.add(String.format(Locale.ROOT, "EXAM PLAN %d%% USED%s", Math.round(Predict.used(world, f, u) * 100), u.dueAlarm ? " - DUE" : ""));
                o.add(String.format(Locale.ROOT, "%.1f KM · %.1f H · %d FAULTS", u.km, u.hours, u.faults));
                o.add(u.passenger ? Servicing.state(u).toUpperCase(Locale.ROOT) : "BRAKES " + (u.brakeOk ? "OK" : "TEST FAILED") + (u.brakeWork ? " · TEST DUE" : ""));
            }
            case 3 -> {
                if (u == null) { o.add("NO VEHICLE"); break; }
                int[] p = f.plan(u);
                o.add("EXAM PLAN: " + (f.plans.containsKey(u.def) ? cut(u.def, 24) : "ALL STOCK"));
                o.add(String.format(Locale.ROOT, "KM %s  HOURS %s  DAYS %s  FAULTS %s", p[0] == 0 ? "-" : p[0], p[1] == 0 ? "-" : p[1], p[2] == 0 ? "-" : p[2], p[3] == 0 ? "-" : p[3]));
                o.add(String.format(Locale.ROOT, "SINCE EXAM: %.1f KM  %.1f H", u.km - u.examKm, u.hours - u.examHours));
                o.add(String.format(Locale.ROOT, "  %.1f DAYS  %d FAULTS", (world.getTotalWorldTime() - u.examTick) / 24000.0, u.faults - u.examFaults));
                String due = Predict.due(world, f, u);
                o.add(due.isEmpty() ? "NOT DUE (" + Math.round(Predict.used(world, f, u) * 100) + "% USED)" : "DUE: " + due);
                o.add("SET: SELECTORS THEN PLAN/PLAN ALL");
            }
            case 4 -> {
                String l = line();
                if (l.isEmpty()) { o.add("NO LINES"); break; }
                int run = 0, spares = 0;
                for (RailwayData.AutoTrain a : RailwayData.get(world).trains().values()) {
                    FleetData.Unit x = f.peek(a.loco);
                    if (l.equals(a.line) && (x == null || x.role == FleetData.SERVICE)) run++;
                    if (x != null && x.role == FleetData.SPARE) spares++;
                }
                int t = f.lineTarget.getOrDefault(l, 0);
                PassengerData.LineStat ls = PassengerData.get(world).line(l);
                o.add("LINE " + l);
                o.add("TARGET " + (t == 0 ? "NOT MANAGED" : t) + " · RUNNING " + run);
                o.add("SPARES STANDING BY " + spares);
                o.add(ls == null ? "NO RIDERSHIP YET" : String.format(Locale.ROOT, "RIDERS %d TODAY · LOAD %d%% · SAT %d", Math.round(ls.ridersDay), Math.round(ls.load), Math.round(ls.sat)));
                UUID best = Fleet.bestSpare(world, f, RailwayData.get(world), l, null);
                o.add("BEST SPARE: " + (best == null ? "NONE FIT" : f.unit(best).name));
                o.add("ATTENDED: " + (f.attended.contains(l) ? "YES - DRIVER NEEDED" : "NO"));
            }
            case 5 -> {
                if (u == null) { o.add("NO VEHICLE"); break; }
                for (FleetData.Kind k : FleetData.Kind.values()) {
                    FleetData.Comp c = u.comps[k.ordinal()];
                    if (c == null) c = f.comp(u, k, world.getTotalWorldTime());
                    o.add(String.format(Locale.ROOT, "%s %s %.1fKM R%d%s", cut(k.label.toUpperCase(Locale.ROOT), 12), c.serial, c.life(u.km), c.repairs, k == FleetData.Kind.WHEELS ? " D" + Math.round(c.diameter) : ""));
                }
            }
            case 6 -> {
                FleetData.Download d = null;
                for (FleetData.Download x : f.downloads) if (u != null && x.unit.equals(u.name)) { d = x; break; }
                if (d == null && !f.downloads.isEmpty()) d = f.downloads.get(0);
                if (d == null) { o.add("NO DOWNLOADS - PRESS DOWNLOAD"); o.add("WITH A LOCO WITHIN 32 BLOCKS"); break; }
                o.add("DOWNLOAD #" + d.id + " " + age(d.tick));
                o.addAll(d.lines);
            }
            case 7 -> {
                if (u == null) { o.add("NO VEHICLE"); break; }
                o.add("HISTORY: " + u.name);
                o.addAll(u.log);
            }
            default -> { }
        }
        return o.size() > max ? o.subList(0, max) : o;
    }

    private String age(long tick) { long m = (world.getTotalWorldTime() - tick) * 60 / 1000; return m < 60 ? m + " MIN AGO" : m / 60 + " H AGO"; }

    @Override
    protected void writeExtra(NBTTagCompound t) { t.setLong("road", road); t.setInteger("jcur", jcur); t.setInteger("ucur", ucur); t.setInteger("lcur", lcur); t.setInteger("caps", caps); }

    @Override
    protected void readExtra(NBTTagCompound t) { road = t.getLong("road"); jcur = t.getInteger("jcur"); ucur = t.getInteger("ucur"); lcur = t.getInteger("lcur"); caps = t.getInteger("caps"); }
}
