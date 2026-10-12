package com.dogpound.railmap.incident;

import com.dogpound.railmap.auto.Disruptions;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * What auto/Disruptions asks incident command (her ChatGPT list #68): how long until the recovery crew is on site and
 * how long each car takes. With no Breakdown Crew Depot in the world the old config times stand. With one (powered,
 * its crew in) the nearest free depot turns out: 30 s to turn out plus the road run at 36 km/h; its lockers decide
 * how fast the work goes - full rerailing kit (2+ hydraulic jacks = pistons, 16+ packing timbers = planks / logs,
 * 2+ lifting slings = leads) is quicker, a bare crew slower; a double crew is quicker still.
 */
public final class IncidentHooks {
    private IncidentHooks() {}

    private static final Map<String, long[]> JOB = new HashMap<>();   // "dim:id" -> {depot pos, eta seconds}

    public static int crewSeconds(World w, Disruptions.Incident in, int dflt) {
        long[] j = job(w, in);
        return j == null ? dflt : (int) j[1];
    }

    public static int workSeconds(World w, Disruptions.Incident in, int dflt) {
        if (com.dogpound.railmap.RailMapConfig.recoveryCrewSeconds == 0) return dflt;      // "no crew": players clear wrecks
        long[] j = job(w, in);
        if (j == null) return dflt;
        TileEntity te = w.getTileEntity(BlockPos.fromLong(j[0]));
        if (!(te instanceof TileBreakdownDepot d)) return dflt;
        return Math.max(2, (int) Math.round(dflt * d.workFactor()));
    }

    /** the depot working this incident (assigned the first time anyone asks) */
    static long[] job(World w, Disruptions.Incident in) {
        String k = w.provider.getDimension() + ":" + in.id;
        long[] j = JOB.get(k);
        if (j != null) return j;
        TileBreakdownDepot best = null;
        double bd = Double.MAX_VALUE;
        for (TileEntity te : w.loadedTileEntityList) {
            if (!(te instanceof TileBreakdownDepot d) || te.isInvalid() || !d.ready()) continue;
            double dd = Math.hypot(te.getPos().getX() - in.x, te.getPos().getZ() - in.z);
            if (dd < bd) { bd = dd; best = d; }
        }
        if (best == null) return null;
        int eta = 30 + (int) Math.round(bd / 10.0);
        j = new long[]{best.getPos().toLong(), eta};
        JOB.put(k, j);
        best.turnOut(in.id, in.what, eta, in.pos());
        IncidentLog log = IncidentLog.get(w);
        IncidentLog.Rec r = log.byDisruption(in.id);
        if (r != null) {
            r.crewFrom = best.getPos().toLong();
            r.assigned |= IncidentLog.CREW;
            log.log(w, r, "BREAKDOWN CREW turned out from depot " + best.getPos().getX() + "," + best.getPos().getZ() + ", ETA " + eta + " s, kit: " + best.kitLine());
        }
        return j;
    }

    /** the depot finished (incident gone): crew back */
    static void tidy(World w) {
        int dim = w.provider.getDimension();
        Set<String> live = new HashSet<>();
        for (Disruptions.Incident in : Disruptions.incidents(dim)) if (in.blocking()) live.add(dim + ":" + in.id);
        for (Iterator<Map.Entry<String, long[]>> it = JOB.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, long[]> e = it.next();
            if (!e.getKey().startsWith(dim + ":") || live.contains(e.getKey())) continue;
            TileEntity te = w.getTileEntity(BlockPos.fromLong(e.getValue()[0]));
            if (te instanceof TileBreakdownDepot d) d.standDown("job done");
            it.remove();
        }
    }

    /** a depot asked to turn out by hand (desk CREW / depot TURN OUT) for the nearest blocking wreck */
    static String manual(World w, TileBreakdownDepot d) {
        Disruptions.Incident best = null;
        double bd = Double.MAX_VALUE;
        for (Disruptions.Incident in : Disruptions.incidents(w.provider.getDimension())) {
            if (!in.blocking() || in.kind != Disruptions.Kind.WRECK || JOB.containsKey(w.provider.getDimension() + ":" + in.id)) continue;
            double dd = Math.hypot(d.getPos().getX() - in.x, d.getPos().getZ() - in.z);
            if (dd < bd) { bd = dd; best = in; }
        }
        if (best == null) return "§7No wreck waiting for a crew";
        if (!d.ready()) return "§cThis depot's crew is not available";
        int eta = 30 + (int) Math.round(bd / 10.0);
        JOB.put(w.provider.getDimension() + ":" + best.id, new long[]{d.getPos().toLong(), eta});
        d.turnOut(best.id, best.what, eta, best.pos());
        return "§eCrew turned out to incident #" + best.id + ", ETA " + eta + " s";
    }

    public static void clear() { JOB.clear(); }
}
