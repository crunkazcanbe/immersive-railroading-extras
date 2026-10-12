package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.Freight;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.freight.CargoRules;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.Constants;

import java.util.*;
import java.util.function.Predicate;

/**
 * Warehouse Office (batch 6 #28): the warehouse management desk - WMS terminal with its paged screen, label printer,
 * radio base for the forklift drivers, the shift board and the pick-list tray. Pick the DOCK (1-4) and press UNLOAD
 * (the car at that dock into the racks) or LOAD (the racks into the car: what you put in the PICK LIST tray, or
 * anything if it's empty); STOP ends the job. WORKERS (1-3) sets how many forklift drivers are on shift; AUTO unloads
 * any secured dock with a loaded car. The drivers are real: each forklift drives to the dock, takes a pallet
 * (F6Config.palletItems of one item) out of the car, drives it to the nearest rack with room and puts it away - and
 * back. The goods ride on the forks (they are in the office's books, so nothing is lost if the chunk unloads). The
 * screen's pages (PAGE): job / stock / docks / workers. 12 V for the WMS.
 */
public class TileWarehouseOffice extends com.dogpound.railmap.depot.DepotTile {
    static final String[] PHASE = {"IDLE", "TO DOCK", "PICKING", "TO RACK", "PUTTING AWAY", "TO RACK", "PICKING", "TO DOCK", "LOADING", "HOME"};
    /** a forklift driver: where it is, where it goes, what is on its forks */
    public static final class Worker {
        public double x, y, z, tx, ty, tz;
        public int phase, timer;
        public BlockPos rack;
        public ItemStack fork = ItemStack.EMPTY;
        public float yaw;
        int entity = -1;

        boolean move(double step) {
            double dx = tx - x, dz = tz - z, d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.05) yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            y += (ty - y) * 0.3;
            if (d <= step) { x = tx; z = tz; return true; }
            x += dx / d * step; z += dz / d * step;
            return false;
        }
        void go(double gx, double gy, double gz) { tx = gx; ty = gy; tz = gz; }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setDouble("x", x); t.setDouble("y", y); t.setDouble("z", z); t.setDouble("tx", tx); t.setDouble("ty", ty); t.setDouble("tz", tz);
            t.setInteger("ph", phase); t.setInteger("tm", timer); t.setFloat("yaw", yaw);
            if (rack != null) t.setLong("rack", rack.toLong());
            t.setTag("fork", fork.writeToNBT(new NBTTagCompound()));
            return t;
        }
        static Worker read(NBTTagCompound t) {
            Worker w = new Worker();
            w.x = t.getDouble("x"); w.y = t.getDouble("y"); w.z = t.getDouble("z"); w.tx = t.getDouble("tx"); w.ty = t.getDouble("ty"); w.tz = t.getDouble("tz");
            w.phase = t.getInteger("ph"); w.timer = t.getInteger("tm"); w.yaw = t.getFloat("yaw");
            if (t.hasKey("rack")) w.rack = BlockPos.fromLong(t.getLong("rack"));
            w.fork = new ItemStack(t.getCompoundTag("fork"));
            return w;
        }
    }

    final List<Worker> workers = new ArrayList<>();
    private int job;               // 0 none, 1 unload, 2 load
    private int jobDock, page, moved, total, trips;
    private String why = "";

    public TileWarehouseOffice() { super(new String[]{"picklist", "labels"}, new String[]{"Pick list tray (samples of what to load)", "Label printer stock"}, new int[]{9, 9}); }

    @Override public String wiredName() { return "Warehouse office"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : (job == 0 ? "no job" : (job == 1 ? "UNLOADING dock " : "LOADING dock ") + (jobDock + 1)) + " · " + workers.size() + " forklifts · " + total + " items moved"; }

    public int job() { return job; }
    public int jobDock() { return jobDock; }
    public List<Worker> workers() { return workers; }
    int wanted() { return 1 + Math.floorMod(ctl.get("s_workers"), 3); }
    int dockSel() { return Math.floorMod(ctl.get("s_dock"), Warehouse.MAX_DOCKS); }
    TileWarehouseDock dock(int n) { List<TileWarehouseDock> l = Warehouse.docks(world, pos); return n < l.size() ? l.get(n) : null; }

    Predicate<ItemStack> pickFilter() {
        List<ItemStack> want = new ArrayList<>();
        for (int i = 0; i < store(0).getSizeInventory(); i++) if (!store(0).getStackInSlot(i).isEmpty()) want.add(store(0).getStackInSlot(i));
        if (want.isEmpty()) return s -> true;
        return s -> { for (ItemStack w : want) if (ItemStack.areItemsEqual(w, s)) return true; return false; };
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "unload", "load" -> {
                TileWarehouseDock d = dock(dockSel());
                if (d == null) return "§cDOCK " + (dockSel() + 1) + " does not exist (Warehouse Dock Doors within 32 blocks)";
                if (Warehouse.racks(world, pos).isEmpty()) return "§cNo pallet racks within 24 blocks";
                if (!d.secured()) return "§cDOCK " + (dockSel() + 1) + " is not SECURED (door open, leveller down, derail on, car standing)";
                job = id.equals("unload") ? 1 : 2; jobDock = dockSel(); moved = 0; why = "";
                AlarmManager.get(world).clear("wh:" + pos.toLong());
                return "§a" + (job == 1 ? "Unload" : "Load") + " job issued: DOCK " + (jobDock + 1) + " - " + Cars.name(d.car());
            }
            case "stop" -> { job = 0; why = "stopped"; return "§eJob stopped - drivers finish their pallet and go home"; }
            case "page" -> { page = (page + 1) % 4; return "§7Screen page " + (page + 1) + "/4"; }
            case "print" -> {
                List<String> lines = new ArrayList<>();
                for (Map.Entry<String, int[]> e : Warehouse.stock(world, pos).entrySet()) lines.add(e.getKey() + ": " + e.getValue()[0]);
                if (lines.isEmpty()) lines.add("(racks empty)");
                com.dogpound.railmap.stn.StnUtil.print(world, pos, controlFacing(), "Stock list", "WMS", lines);
                return "§aStock list printed";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        double step = F6Config.forkliftSpeed / 20.0;
        if (!powered()) step *= 0;                                  // no WMS, no radio: drivers stand
        // ---- shift: the drivers start at the office door
        while (workers.size() < wanted()) { Worker w = new Worker(); w.x = w.tx = pos.getX() + 0.5; w.y = w.ty = pos.getY(); w.z = w.tz = pos.getZ() + 0.5; workers.add(w); }
        if (workers.size() > wanted()) for (Iterator<Worker> it = workers.iterator(); it.hasNext() && workers.size() > wanted(); ) { Worker w = it.next(); if (w.fork.isEmpty() && (w.phase == 0 || w.phase == 9)) { kill(w); it.remove(); } }
        if (job == 0 && ctl.get("k_auto") == 1 && powered() && age % 40 == 0) {
            List<TileWarehouseDock> ds = Warehouse.docks(world, pos);
            for (int i = 0; i < ds.size(); i++) if (ds.get(i).secured() && ds.get(i).car().getPercentCargoFull() > 0) { job = 1; jobDock = i; moved = 0; why = ""; break; }
        }
        TileWarehouseDock d = job == 0 ? null : dock(jobDock);
        if (job != 0 && (d == null || !d.secured())) { job = 0; why = d == null ? "dock gone" : "dock no longer secured"; }
        for (Worker w : workers) if (step > 0) drive(w, d, step);
        if (world.getTotalWorldTime() % 20 == 3) spawnViews();
        if (age % 20 != 0) return;
        List<TileWarehouseRack> racks = Warehouse.racks(world, pos);
        List<TileWarehouseDock> docks = Warehouse.docks(world, pos);
        int stock = 0, slots = 0;
        for (TileWarehouseRack r : racks) { stock += r.count(); slots += r.used(); }
        put("pwr", powered()); put("jobl", job != 0); put("unl", job == 1); put("ldl", job == 2); put("autol", ctl.get("k_auto") == 1);
        put("fulll", !racks.isEmpty() && slots >= racks.size() * 81); put("w1", workers.size() >= 1 && workers.get(0).phase != 0); put("w2", workers.size() >= 2 && workers.get(1).phase != 0); put("w3", workers.size() >= 3 && workers.get(2).phase != 0);
        for (int i = 0; i < Warehouse.MAX_DOCKS; i++) put("d" + i, i < docks.size() ? docks.get(i).secured() ? 1 : 0 : 0);
        put("rackg", racks.isEmpty() ? 0 : slots * 100 / (racks.size() * 81));
        text("moved", String.valueOf(moved));
        switch (page) {
            case 1 -> {
                List<Map.Entry<String, int[]>> top = new ArrayList<>(Warehouse.stock(world, pos).entrySet());
                top.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
                for (int i = 0; i < 3; i++) text("line" + (i + 1), i < top.size() ? cut(top.get(i).getKey().toUpperCase(Locale.ROOT), 13) + " " + top.get(i).getValue()[0] : i == 0 ? "RACKS EMPTY" : "");
            }
            case 2 -> {
                for (int i = 0; i < 3; i++) {
                    TileWarehouseDock x = i < docks.size() ? docks.get(i) : null;
                    text("line" + (i + 1), x == null ? (i == 0 ? "NO DOCKS" : "") : "D" + (i + 1) + " " + (x.secured() ? "SECURED " : "OPEN " + Math.round(x.door()) + "% ") + (x.car() == null ? "" : x.car().getPercentCargoFull() + "%"));
                }
            }
            case 3 -> {
                for (int i = 0; i < 3; i++) text("line" + (i + 1), i < workers.size() ? "FLT" + (i + 1) + " " + PHASE[workers.get(i).phase] + (workers.get(i).fork.isEmpty() ? "" : " " + workers.get(i).fork.getCount()) : "");
            }
            default -> {
                text("line1", job == 0 ? "NO JOB" + (why.isEmpty() ? "" : " " + cut(why.toUpperCase(Locale.ROOT), 12)) : (job == 1 ? "UNLOAD D" : "LOAD D") + (jobDock + 1) + " " + moved);
                text("line2", "STOCK " + stock + " IN " + slots + " SL");
                text("line3", racks.size() + " RACKS " + docks.size() + " DOCKS " + workers.size() + " FLT");
            }
        }
        text("pageno", (page + 1) + "/4");
    }

    /** one driver, one tick */
    void drive(Worker w, TileWarehouseDock d, double step) {
        BlockPos home = pos;
        switch (w.phase) {
            case 0 -> {                                                        // at home: take work (looks once a second)
                if (job != 0 && d != null && --w.timer <= 0) { w.timer = 20; if (job == 1) w.phase = 1; else if (pickRack(w)) w.phase = 5; }
            }
            case 1, 7 -> { if (d == null) { w.phase = 9; break; } w.go(d.getPos().getX() + 0.5, d.getPos().getY(), d.getPos().getZ() + 0.5); if (w.move(step)) { w.phase = w.phase == 1 ? 2 : 8; w.timer = 30; } }
            case 2 -> {                                                        // pick a pallet out of the car
                if (--w.timer > 0) break;
                Freight car = d == null ? null : d.car();
                ItemStack s = car == null ? ItemStack.EMPTY : Cars.take(Cars.inv(car), F6Config.palletItems, x -> true);
                if (s.isEmpty()) { if (job == 1) { finish(car == null ? "car gone" : "car empty"); } w.phase = 9; break; }
                w.fork = s;
                world.playSound(null, d.getPos(), com.dogpound.railmap.sound.PackSounds.ev("ctl.lever"), net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 0.5f);
                TileWarehouseRack r = rackFor(s);
                if (r == null) { Cars.put(Cars.inv(car), w.fork); w.fork = ItemStack.EMPTY; finish("racks full"); full(); w.phase = 9; break; }
                w.rack = r.getPos(); w.phase = 3;
            }
            case 3, 5 -> {
                if (w.rack == null) { w.phase = w.phase == 3 ? 4 : 0; break; }
                BlockPos r = w.rack;
                w.go(r.getX() + 0.5, r.getY(), r.getZ() + 0.5);
                if (w.move(step)) { w.phase = w.phase == 3 ? 4 : 6; w.timer = 30; }
            }
            case 4 -> {                                                        // put away
                if (--w.timer > 0) break;
                TileWarehouseRack r = w.rack != null && world.getTileEntity(w.rack) instanceof TileWarehouseRack x ? x : rackFor(w.fork);
                int had = w.fork.getCount();
                if (r != null) w.fork = r.putAway(w.fork);
                moved += had - w.fork.getCount(); total += had - w.fork.getCount();
                if (!w.fork.isEmpty()) { TileWarehouseRack r2 = rackFor(w.fork); if (r2 != null && r2 != r) { w.rack = r2.getPos(); w.phase = 3; break; } }
                if (!w.fork.isEmpty()) { dropFork(w); full(); finish("racks full"); }
                trips++;
                w.phase = job == 1 ? 1 : 9;
            }
            case 6 -> {                                                        // pick from the rack for loading
                if (--w.timer > 0) break;
                TileWarehouseRack r = w.rack != null && world.getTileEntity(w.rack) instanceof TileWarehouseRack x ? x : null;
                Freight car = d == null ? null : d.car();
                String type = car == null ? "" : Cars.type(car);
                ItemStack s = r == null ? ItemStack.EMPTY : r.pick(F6Config.palletItems, x -> pickFilter().test(x) && CargoRules.refuse(type, x) == null);
                if (s.isEmpty()) { w.phase = pickRack(w) ? 5 : 9; if (w.phase == 9 && job == 2) finish("nothing left to pick"); break; }
                w.fork = s; w.phase = 7;
            }
            case 8 -> {                                                        // load into the car
                if (--w.timer > 0) break;
                Freight car = d == null ? null : d.car();
                ItemStack rest = car == null ? w.fork : Cars.put(Cars.inv(car), w.fork);
                moved += w.fork.getCount() - rest.getCount(); total += w.fork.getCount() - rest.getCount(); trips++;
                w.fork = rest;
                if (!w.fork.isEmpty()) {                                       // the car is full: the pallet goes back
                    TileWarehouseRack back = rackFor(w.fork);
                    if (back != null) { w.rack = back.getPos(); w.phase = 3; } else { dropFork(w); w.phase = 9; }
                    finish("car full");
                    break;
                }
                w.phase = job == 2 ? (pickRack(w) ? 5 : 9) : 9;
            }
            case 9 -> {                                                        // back to the office (and back to work if there is a job)
                if (job != 0 && w.fork.isEmpty() && d != null) { w.phase = 0; break; }
                w.go(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                if (w.move(step)) {
                    if (w.fork.isEmpty()) w.phase = 0;
                    else { TileWarehouseRack r = rackFor(w.fork); if (r != null) { w.rack = r.getPos(); w.phase = 3; } else { dropFork(w); w.phase = 0; } }   // never home with a load
                }
            }
            default -> w.phase = 0;
        }
    }

    void target(Worker w, BlockPos p) { if (p != null) w.go(p.getX() + 0.5, p.getY(), p.getZ() + 0.5); }

    boolean pickRack(Worker w) {
        TileWarehouseDock d = dock(jobDock);
        String type = d == null || d.car() == null ? "" : Cars.type(d.car());
        Predicate<ItemStack> ok = x -> pickFilter().test(x) && CargoRules.refuse(type, x) == null;
        TileWarehouseRack best = null;
        double bd = Double.MAX_VALUE;
        for (TileWarehouseRack r : Warehouse.racks(world, pos)) {
            if (!r.has(ok)) continue;
            double dd = r.getPos().distanceSq(w.x, w.y, w.z);
            if (dd < bd) { bd = dd; best = r; }
        }
        w.rack = best == null ? null : best.getPos();
        return best != null;
    }

    TileWarehouseRack rackFor(ItemStack s) {
        TileWarehouseRack best = null;
        double bd = Double.MAX_VALUE;
        for (TileWarehouseRack r : Warehouse.racks(world, pos)) {
            if (r.room(s) <= 0) continue;
            double dd = r.getPos().distanceSq(pos);
            if (dd < bd) { bd = dd; best = r; }
        }
        return best;
    }

    void finish(String w) {
        if (job == 0) return;
        Recorder.event(world, String.format(Locale.ROOT, "Warehouse %d,%d: %s job at dock %d finished - %d items (%s)", pos.getX(), pos.getZ(), job == 1 ? "unload" : "load", jobDock + 1, moved, w));
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1f);
        job = 0; why = w;
    }

    void full() { AlarmManager.get(world).raise(world, "wh:" + pos.toLong(), AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MINOR, "Warehouse at " + pos.getX() + "," + pos.getZ() + ": RACKS FULL - nowhere to put away", pos, false); }

    void dropFork(Worker w) {
        if (w.fork.isEmpty()) return;
        net.minecraft.inventory.InventoryHelper.spawnItemStack(world, w.x, w.y + 0.5, w.z, w.fork);
        w.fork = ItemStack.EMPTY;
    }

    // ---- the forklifts you see (views of the workers, never saved) ---------------------------------------------------
    void spawnViews() {
        if (world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 80, false) == null) { for (Worker w : workers) kill(w); return; }
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            if (w.entity >= 0 && world.getEntityByID(w.entity) instanceof EntityForklift f && !f.isDead) continue;
            EntityForklift f = new EntityForklift(world, pos, i);
            f.setLocationAndAngles(w.x, w.y, w.z, w.yaw, 0);
            if (world.spawnEntity(f)) w.entity = f.getEntityId();
        }
    }

    void kill(Worker w) { if (w.entity >= 0 && world.getEntityByID(w.entity) instanceof EntityForklift f) f.setDead(); w.entity = -1; }

    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) for (Worker w : workers) kill(w); }
    @Override public void onChunkUnload() { super.onChunkUnload(); for (Worker w : workers) kill(w); }
    /** broken: what is on the forks drops with the trays */
    @Override public void dropAll() { super.dropAll(); for (Worker w : workers) dropFork(w); }

    public Vec3d workerPos(int i) { return i < workers.size() ? new Vec3d(workers.get(i).x, workers.get(i).y, workers.get(i).z) : null; }

    @Override protected void saveExtra(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Worker w : workers) l.appendTag(w.write());
        t.setTag("workers", l);
        t.setInteger("job", job); t.setInteger("dock", jobDock); t.setInteger("page", page); t.setInteger("moved", moved); t.setInteger("total", total); t.setInteger("trips", trips); t.setString("why", why);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        List<Worker> was = new ArrayList<>(workers);
        workers.clear();
        NBTTagList l = t.getTagList("workers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) { Worker w = Worker.read(l.getCompoundTagAt(i)); if (i < was.size()) w.entity = was.get(i).entity; workers.add(w); }
        job = t.getInteger("job"); jobDock = t.getInteger("dock"); page = t.getInteger("page"); moved = t.getInteger("moved"); total = t.getInteger("total"); trips = t.getInteger("trips"); why = t.getString("why");
    }
}
