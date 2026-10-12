package com.dogpound.railmap.track;

import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Heavy loads on the real grid (point heaters, tunnel jet fans): a Power Cable or grid machine touching the block at
 * 400 V .. 11 kV feeds it, and what it uses is drawn from that network once a second like any other load.
 */
public final class GridFeed {
    private GridFeed() {}

    /** the last call's result for display */
    public static final class Result { public boolean ok; public String what = ""; public double kw; }

    /** draw kw for one second from a touching grid network; kw <= 0 just checks the supply */
    public static Result draw(World w, BlockPos pos, double kw) {
        Result r = new Result();
        if (!TrackConfig.heatersAndFansNeedGrid) { r.ok = true; r.kw = kw; r.what = "local supply (config)"; return r; }
        GridData d = GridData.get(w);
        r.what = "no grid supply (lay a Power Cable to it, 400 V - 11 kV)";
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos nb = pos.offset(f);
            if (d.node(nb) == null) continue;
            GridData.Net n = d.netAt(nb, f.getOpposite());
            if (n == null) continue;
            if (!n.problem.isEmpty()) { r.what = "grid supply dead: " + n.problem; continue; }
            if (n.level == null || n.level.volts < 380) { r.what = "grid supply too low: " + (n.level == null ? "no voltage" : n.level.label); continue; }
            if (n.level.volts > 11000) { r.what = "grid supply too high: " + n.level.label + " (needs a transformer down to 400 V - 11 kV)"; continue; }
            if (kw <= 0) { r.ok = true; r.what = n.level.label + " available"; return r; }
            long want = Math.max(1, Math.round(kw * 1000 / Elec.W_PER_FE_T * 20));      // FE for one second
            long got = d.draw(n, want);
            r.ok = got >= want * 0.9;
            r.kw = kw * got / (double) want;
            r.what = n.level.label + (r.ok ? String.format(java.util.Locale.ROOT, " · %.1f kW", r.kw) : " · supply can't carry the load");
            return r;
        }
        return r;
    }
}
