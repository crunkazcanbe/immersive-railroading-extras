package com.dogpound.railmap.incident;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.MovingBlock;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.server.TrainTracker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Digital twin (her ChatGPT list #78): a live model of the railway for the control room - per line the trains on it,
 * how many are moving or held and the worst delay; every traction feeder section and whether it is live (and why
 * not); the points each driverless train's route still needs and whether they already lie right; vehicles due
 * maintenance and open work orders; and PREDICTED CONFLICTS: two trains whose planned routes reach the same piece
 * of track within 30 s of each other in the next two minutes (or a train standing on another's route), with when
 * and where. The map tab already draws the positions, occupancy and switch states; this is the model behind it.
 */
public final class Twin {
    private Twin() {}

    public static void fill(World w, List<String[]> out) {
        RailwayData data = RailwayData.get(w);
        int dim = w.provider.getDimension();
        long now = w.getTotalWorldTime();
        // lines
        Map<String, int[]> lines = new TreeMap<>();
        for (RailwayData.AutoTrain a : data.trains().values()) {
            int[] v = lines.computeIfAbsent(a.line == null || a.line.isEmpty() ? "(no line)" : a.line, k -> new int[4]);
            Autopilot.Run r = Autopilot.run(a.loco);
            v[0]++;
            if (r != null && Math.abs(r.speedKmh) > 1) v[1]++;
            if (Disruptions.isHeld(dim, a.loco)) v[2]++;
            v[3] = Math.max(v[3], Disruptions.delaySeconds(a.loco));
        }
        for (Map.Entry<String, int[]> e : lines.entrySet()) {
            RailwayData.Line l = data.lines().get(e.getKey());
            out.add(new String[]{"WL", e.getKey(), String.valueOf(e.getValue()[0]), String.valueOf(e.getValue()[1]), String.valueOf(e.getValue()[2]),
                    String.valueOf(e.getValue()[3] / 60), l == null ? "-" : String.valueOf(l.stations.size()), l != null && l.movingBlock ? "CBTC" : "signals"});
        }
        // electrical sections
        GridData gd = GridData.get(w);
        int nf = 0;
        for (Map.Entry<Long, GridData.Node> e : gd.nodes().entrySet()) {
            if (e.getValue().kind != GridKind.FEEDER || nf++ > 40) continue;
            BlockPos p = BlockPos.fromLong(e.getKey());
            String why = e.getValue().energy > 0 ? "" : GridData.why(gd.netOf(p), true);
            out.add(new String[]{"WF", p.getX() + "," + p.getZ(), e.getValue().feeds, String.valueOf(e.getValue().range), why.isEmpty() ? "LIVE" : "DEAD", why});
        }
        // maintenance
        int worn = 0;
        for (TrainNode t : TrainTracker.latest(w)) if (t.wear >= 70 && worn++ < 20) out.add(new String[]{"WM", t.displayName(), String.valueOf(t.wear), Math.round(t.x) + "," + Math.round(t.z)});
        int wo = 0;
        for (IncidentLog.WorkOrder o : IncidentLog.get(w).orders) if (o.done == 0) wo++;
        out.add(new String[]{"WW", String.valueOf(wo)});
        // routes, points and conflicts
        List<Map.Entry<UUID, MovingBlock.Auth>> auths = new ArrayList<>();
        for (Map.Entry<UUID, MovingBlock.Auth> e : MovingBlock.all()) if (now - e.getValue().tick <= 60 && e.getValue().path != null) auths.add(e);
        int sw = 0;
        for (Map.Entry<UUID, MovingBlock.Auth> e : auths) {
            MovingBlock.Auth a = e.getValue();
            String name = name(data, e.getKey());
            for (Map.Entry<Vec3i, SwitchState> s : a.path.switches.entrySet()) {
                if (sw++ > 40) break;
                int idx = a.path.indexOf(s.getKey());
                if (idx >= 0 && a.path.steps.get(idx).distance < a.head) continue;            // already behind it
                SwitchState cur = state(w, s.getKey());
                out.add(new String[]{"WS", s.getKey().x + "," + s.getKey().z, name, s.getValue().name(), cur == null ? "?" : cur.name(), cur == s.getValue() ? "1" : "0",
                        idx < 0 ? "" : String.valueOf(Math.round(a.path.steps.get(idx).distance - a.head))});
            }
        }
        int nc = 0;
        for (int i = 0; i < auths.size() && nc < 20; i++)
            for (int j = i + 1; j < auths.size() && nc < 20; j++) {
                String[] c = conflict(w, data, auths.get(i), auths.get(j));
                if (c != null) { out.add(c); nc++; }
            }
        out.add(new String[]{"WC", String.valueOf(nc)});
    }

    private static String name(RailwayData data, UUID u) {
        RailwayData.AutoTrain a = data.trains().get(u);
        return a != null && !a.label.isEmpty() ? a.label : u.toString().substring(0, 6);
    }

    /** the first piece of track both routes reach within 30 s of each other, inside 120 s ahead; or one train in the other's way */
    private static String[] conflict(World w, RailwayData data, Map.Entry<UUID, MovingBlock.Auth> ea, Map.Entry<UUID, MovingBlock.Auth> eb) {
        MovingBlock.Auth a = ea.getValue(), b = eb.getValue();
        Autopilot.Run ra = Autopilot.run(ea.getKey()), rb = Autopilot.run(eb.getKey());
        double va = Math.max(1, Math.abs(ra == null ? 0 : ra.speedKmh) / 3.6), vb = Math.max(1, Math.abs(rb == null ? 0 : rb.speedKmh) / 3.6);
        Map<Vec3i, Double> tb = new HashMap<>();
        for (TrackPath.Step s : b.path.steps) {
            double d = s.distance - b.head;
            if (d < -20) continue;
            double t = Math.max(0, d) / vb;
            if (t > 120) break;
            tb.putIfAbsent(s.pos, t);
        }
        for (TrackPath.Step s : a.path.steps) {
            double d = s.distance - a.head;
            if (d < 0) continue;
            double t = d / va;
            if (t > 120) break;
            Double t2 = tb.get(s.pos);
            if (t2 == null) continue;
            boolean standing = t2 == 0 && vb <= 1.01;
            if (Math.abs(t - t2) <= 30 || standing)
                return new String[]{"WX", name(data, ea.getKey()), name(data, eb.getKey()), s.pos.x + "," + s.pos.z, String.valueOf(Math.round(Math.min(t, t2))),
                        standing ? "standing in its path" : "both due within " + Math.round(Math.abs(t - t2)) + " s"};
        }
        return null;
    }

    private static SwitchState state(World mc, Vec3i p) {
        try {
            TileRailBase base = cam72cam.mod.world.World.get(mc).getBlockEntity(p, TileRailBase.class);
            if (base == null) return null;
            TileRail sw = base instanceof TileRail tr ? tr : base.getParentTile();
            if (sw == null || sw.info == null || sw.info.settings.type != TrackItems.SWITCH) sw = base.findSwitchParent();
            return sw == null ? null : SwitchUtil.getSwitchState(sw);
        } catch (RuntimeException e) { return null; }
    }
}
