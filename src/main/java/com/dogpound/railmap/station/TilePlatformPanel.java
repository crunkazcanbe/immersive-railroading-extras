package com.dogpound.railmap.station;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Dispatcher;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.depot.DepotConfig;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.depot.FleetData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Platform Allocation Panel (her ChatGPT list #82): up to six platform roads of one station (wrench a rail, click the
 * platform's row to link it). For each: its length, whether it is step-free, which way trains may use it (ANY / UP /
 * DOWN) and the platform whose movements it conflicts with across the station throat, and a CLOSE button. In AUTO, a
 * driverless train heading for any of the station's platforms is given the best one as it approaches: free and long
 * enough for the train, the right direction, not crossing a movement on its conflicting platform, step-free preferred,
 * next to a waiting train on another line (cross-platform interchange), and its own booked platform if that's as
 * good. The train keeps that platform until it has left. MANUAL leaves every train on its booked platform.
 */
public class TilePlatformPanel extends DepotTile {
    public static final int N = 6;
    static final int[] LENGTHS = {40, 80, 120, 160, 240, 320};
    static final String[] DIRS = {"ANY", "UP", "DOWN"};
    final long[] plat = new long[N];
    final int[] len = {2, 2, 2, 2, 2, 2}, dir = new int[N], conf = new int[N];
    final boolean[] stepFree = new boolean[N], closed = new boolean[N];
    /** train -> platform index it has been given, and when */
    final Map<UUID, int[]> alloc = new HashMap<>();
    private int sel, lastSel = -1;

    private static final Map<Integer, Set<TilePlatformPanel>> LOADED = new HashMap<>();
    public static Collection<TilePlatformPanel> loaded(World w) { Set<TilePlatformPanel> s = LOADED.get(w.provider.getDimension()); return s == null ? Collections.emptySet() : new ArrayList<>(s); }
    @Override public void onLoad() { if (world != null && !world.isRemote) LOADED.computeIfAbsent(world.provider.getDimension(), k -> new HashSet<>()).add(this); }
    @Override public void invalidate() { super.invalidate(); drop(); }
    @Override public void onChunkUnload() { super.onChunkUnload(); drop(); }
    private void drop() { if (world != null) { Set<TilePlatformPanel> s = LOADED.get(world.provider.getDimension()); if (s != null) s.remove(this); } }

    @Override public String wiredName() { return "Platform allocation panel"; }
    @Override public String gearState() { int n = 0; for (long p : plat) if (p != 0) n++; return n + " platforms · " + (auto() ? "AUTO" : "MANUAL") + " · " + alloc.size() + " allocated"; }
    boolean auto() { return powered() && ctl.get("k_auto") == 0; }

    int index(long stop) { for (int i = 0; i < N; i++) if (plat[i] == stop) return i; return -1; }

    // ---- the allocator ------------------------------------------------------------------------------------------------
    long allocate(World w, RailwayData.AutoTrain a, long stop) {
        int booked = index(stop);
        if (booked < 0) return stop;
        int[] have = alloc.get(a.loco);
        if (have != null && have[1] == booked) return plat[have[0]];
        if (!auto()) return stop;
        Locomotive l = Autopilot.find(w, a.loco);
        double trainM = 0;
        if (l != null) try { for (EntityCoupleableRollingStock c : l.getTrain()) trainM += c.getDefinition().getLength(c.gauge); } catch (RuntimeException ignored) { }
        int way = a.mode == RailwayData.Mode.SHUTTLE && a.direction < 0 ? 2 : 1;
        int best = -1;
        double bs = -1e9;
        for (int i = 0; i < N; i++) {
            if (plat[i] == 0 || closed[i]) continue;
            if (LENGTHS[len[i]] + 0.5 < trainM) continue;
            if (dir[i] != 0 && dir[i] != way) continue;
            if (taken(w, i, a.loco)) continue;
            double s = 0;
            if (i == booked) s += 12;
            if (stepFree[i]) s += 10;
            if (conf[i] > 0 && moving(w, conf[i] - 1, a.loco)) s -= 30;
            for (int j : new int[]{i - 1, i + 1}) if (j >= 0 && j < N && plat[j] != 0 && interchange(w, j, a)) s += 15;
            s -= (LENGTHS[len[i]] - trainM) / 40.0;           // the shortest platform that fits keeps the long ones free
            if (s > bs) { bs = s; best = i; }
        }
        if (best < 0) return stop;                              // nothing fits: the booked platform, and the signals hold it
        alloc.put(a.loco, new int[]{best, booked, (int) (w.getTotalWorldTime() / 20)});
        if (best != booked) Recorder.event(w, "Platform allocation: " + (a.label.isEmpty() ? FleetData.get(w).unit(a.loco).name : a.label) + " to P" + (best + 1) + " (booked P" + (booked + 1) + ")");
        controlsChanged();
        return plat[best];
    }

    /** someone else is standing at it, or it has been given to another train */
    boolean taken(World w, int i, UUID me) {
        for (Map.Entry<UUID, int[]> e : alloc.entrySet()) if (e.getValue()[0] == i && !e.getKey().equals(me)) return true;
        BlockPos p = BlockPos.fromLong(plat[i]);
        for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
            Vec3d v = s.getPosition();
            if (s.isDead() || p.distanceSqToCenter(v.x, v.y, v.z) > 64) continue;
            if (s instanceof Locomotive l && l.getUUID().equals(me)) continue;
            boolean mine = false;
            if (s instanceof EntityCoupleableRollingStock c) { Locomotive l = Autopilot.find(w, me); if (l != null) try { mine = l.getTrain().contains(c); } catch (RuntimeException ignored) { } }
            if (!mine) return true;
        }
        return false;
    }

    /** a train (not this one) is moving on or near this platform's road */
    boolean moving(World w, int i, UUID me) {
        if (plat[i] == 0) return false;
        BlockPos p = BlockPos.fromLong(plat[i]);
        for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityMoveableRollingStock.class)) {
            Vec3d v = s.getPosition();
            if (s.getUUID().equals(me) || p.distanceSqToCenter(v.x, v.y, v.z) > 24 * 24) continue;
            if (s.getCurrentSpeed() != null && Math.abs(s.getCurrentSpeed().metric()) > 2) return true;
        }
        return false;
    }

    /** a train of another line stands at this platform now: a cross-platform change */
    boolean interchange(World w, int j, RailwayData.AutoTrain me) {
        for (RailwayData.AutoTrain o : RailwayData.get(w).trains().values()) {
            if (o == me || o.line.equals(me.line)) continue;
            Autopilot.Run r = Autopilot.run(o.loco);
            int[] al = alloc.get(o.loco);
            if (r != null && r.dwelling && (r.dwellStation == plat[j] || al != null && al[0] == j)) return true;
        }
        return false;
    }

    // ---- controls --------------------------------------------------------------------------------------------------
    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (control.length() != 2 || control.charAt(0) != 'z') return null;
        int i = control.charAt(1) - '1';
        if (i < 0 || i >= N) return null;
        plat[i] = pick.toLong();
        sel = i;
        controlsChanged();
        String n = com.dogpound.railmap.server.StationData.get(world).nameAt(pick);
        return "§aP" + (i + 1) + " = the rail at " + pick.getX() + " " + pick.getY() + " " + pick.getZ() + (n == null ? " §7(tip: name one platform as the station; lines stop there and get allocated across)" : " (" + n + ")");
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.length() == 2 && id.charAt(0) == 'z') { sel = id.charAt(1) - '1'; return "§7P" + (sel + 1) + " selected" + (plat[sel] == 0 ? " - wrench a rail and click here to link it" : ""); }
        if (id.length() == 2 && id.charAt(0) == 'c') {
            int i = id.charAt(1) - '1';
            closed[i] = !closed[i];
            Recorder.event(world, "Platform P" + (i + 1) + (closed[i] ? " CLOSED" : " reopened"));
            return closed[i] ? "§cP" + (i + 1) + " CLOSED" : "§aP" + (i + 1) + " open";
        }
        if (id.equals("stepf")) { stepFree[sel] = !stepFree[sel]; return "§eP" + (sel + 1) + (stepFree[sel] ? " step-free" : " has steps"); }
        if (id.equals("clear")) { alloc.clear(); return "§eAllocations cleared"; }
        return null;
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "s_len" -> { len[sel] = (len[sel] + 1) % LENGTHS.length; return "§eP" + (sel + 1) + " length " + LENGTHS[len[sel]] + " m"; }
            case "s_dir" -> { dir[sel] = (dir[sel] + 1) % DIRS.length; return "§eP" + (sel + 1) + " used " + DIRS[dir[sel]]; }
            case "s_conf" -> { do conf[sel] = (conf[sel] + 1) % (N + 1); while (conf[sel] == sel + 1); return "§eP" + (sel + 1) + " conflicts with " + (conf[sel] == 0 ? "nothing" : "P" + conf[sel]); }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        // let go of trains that have left (or no longer head here)
        RailwayData rd = RailwayData.get(world);
        alloc.entrySet().removeIf(e -> {
            RailwayData.AutoTrain a = rd.train(e.getKey());
            if (a == null) return true;
            Autopilot.Run r = Autopilot.run(a.loco);
            Long next = Dispatcher.nextStop(rd, a);
            boolean stillHere = r != null && r.dwelling && index(r.dwellStation) >= 0;
            return !stillHere && (next == null || index(next) < 0);
        });
        ctl.set("s_len", len[sel]); ctl.set("s_dir", dir[sel]); ctl.set("s_conf", conf[sel]);
        for (int i = 0; i < N; i++) {
            String who = "";
            for (Map.Entry<UUID, int[]> e : alloc.entrySet()) if (e.getValue()[0] == i) { RailwayData.AutoTrain a = rd.train(e.getKey()); who = a == null ? "" : a.label.isEmpty() ? FleetData.get(world).unit(a.loco).name : a.label; }
            boolean occ = plat[i] != 0 && taken(world, i, new UUID(0, 0)) && who.isEmpty();
            text("p" + (i + 1), plat[i] == 0 ? "P" + (i + 1) + " NOT LINKED" : String.format(Locale.ROOT, "P%d %3dM %s %-4s %s", i + 1, LENGTHS[len[i]], stepFree[i] ? "SF" : "  ", DIRS[dir[i]],
                    closed[i] ? "CLOSED" : !who.isEmpty() ? "> " + cut(who.toUpperCase(Locale.ROOT), 14) : occ ? "OCCUPIED" : "FREE"));
            put("p" + (i + 1) + "a", !who.isEmpty()); put("p" + (i + 1) + "o", occ); put("p" + (i + 1) + "c", closed[i]); put("p" + (i + 1) + "s", i == sel);
        }
        put("pwr", powered()); put("autol", auto());
        text("selt", "P" + (sel + 1) + " " + LENGTHS[len[sel]] + "M " + DIRS[dir[sel]] + (conf[sel] == 0 ? "" : " X P" + conf[sel]) + (stepFree[sel] ? " SF" : ""));
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        NBTTagCompound pl = new NBTTagCompound(); for (int i = 0; i < N; i++) pl.setLong("p" + i, plat[i]); t.setTag("plat", pl);
        t.setIntArray("len", len); t.setIntArray("dir", dir); t.setIntArray("conf", conf);
        int sf = 0, cl = 0;
        for (int i = 0; i < N; i++) { if (stepFree[i]) sf |= 1 << i; if (closed[i]) cl |= 1 << i; }
        t.setInteger("sf", sf); t.setInteger("cl", cl); t.setInteger("sel", sel);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        NBTTagCompound pl = t.getCompoundTag("plat");
        for (int i = 0; i < N; i++) plat[i] = pl.getLong("p" + i);
        int[] a = t.getIntArray("len"), b = t.getIntArray("dir"), c = t.getIntArray("conf");
        for (int i = 0; i < N; i++) { if (i < a.length) len[i] = a[i]; if (i < b.length) dir[i] = b[i]; if (i < c.length) conf[i] = c[i]; }
        int sf = t.getInteger("sf"), cl = t.getInteger("cl");
        for (int i = 0; i < N; i++) { stepFree[i] = (sf >> i & 1) == 1; closed[i] = (cl >> i & 1) == 1; }
        sel = t.getInteger("sel");
    }
}
