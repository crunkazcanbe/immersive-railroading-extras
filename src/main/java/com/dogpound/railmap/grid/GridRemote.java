package com.dogpound.railmap.grid;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The whole grid as plain maps / lists for SCADA: the OpenComputers Data Link and the Auto Controller read it here. */
public final class GridRemote {
    private GridRemote() {}

    /** a "network" that is only control gear (an Auto Controller / Grid Control Panel on its own) carries no power: not a fault */
    static boolean controlOnly(GridData d, GridData.Net n) {
        for (long p : n.nodes) { GridData.Node x = d.nodes().get(p); if (x != null && x.kind != GridKind.AUTO && x.kind != GridKind.SCADA && !x.kind.control()) return false; }
        return true;
    }

    public static List<Map<String, Object>> networks(World w) {
        List<Map<String, Object>> l = new ArrayList<>();
        GridData gd = GridData.get(w);
        for (GridData.Net n : gd.nets()) {
            if (controlOnly(gd, n)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id);
            m.put("parts", n.nodes.size());
            m.put("live", n.problem.isEmpty());
            m.put("problem", n.problem);
            if (n.level != null) {
                m.put("level", n.level.label);
                m.put("volts", Math.round(n.level.volts * (n.puSource > 0 ? n.puSource : 1)));
                m.put("ac", n.level.ac);
                m.put("hz", n.level.ac ? Math.round(n.level.hz * n.hzPu * 100) / 100.0 : 0);
            }
            m.put("kw", Math.round(n.pW / 100) / 10.0);
            m.put("kvar", Math.round(n.qVar / 100) / 10.0);
            m.put("kva", Math.round(n.sVA / 100) / 10.0);
            m.put("amps", Math.round(n.amps * 10) / 10.0);
            m.put("pf", Math.round(n.pf * 100) / 100.0);
            m.put("faultMva", Math.round(n.faultVA / 1e5) / 10.0);
            m.put("energy", n.energy);
            m.put("capacity", n.capacity);
            m.put("loadFePerTick", n.lastSecond / 20);
            l.add(m);
        }
        return l;
    }

    /** every machine on the grid (loaded ones in full; ones in unloaded chunks with what the network knows) */
    public static List<Map<String, Object>> devices(World w, String kindFilter) {
        List<Map<String, Object>> l = new ArrayList<>();
        GridData d = GridData.get(w);
        for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
            GridData.Node n = e.getValue();
            if (!n.kind.machine()) continue;
            String k = n.kind.id.replace("grid_", "");
            if (kindFilter != null && !kindFilter.isEmpty() && !kindFilter.equalsIgnoreCase(k)) continue;
            BlockPos p = BlockPos.fromLong(e.getKey());
            l.add(device(w, p, n));
        }
        return l;
    }

    public static Map<String, Object> device(World w, BlockPos p) {
        GridData.Node n = GridData.get(w).node(p);
        return n == null ? null : device(w, p, n);
    }

    private static Map<String, Object> device(World w, BlockPos p, GridData.Node n) {
        TileEntity t = w.isBlockLoaded(p) ? w.getTileEntity(p) : null;
        if (t instanceof TileGrid g) return g.remoteInfo();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", p.getX()); m.put("y", p.getY()); m.put("z", p.getZ());
        m.put("kind", n.kind.id.replace("grid_", "")); m.put("name", n.kind.label);
        m.put("network", n.net); m.put("loaded", false);
        return m;
    }

    /** operate a machine's control like its panel would (breakers need their key in REMOTE) */
    public static String control(World w, BlockPos p, String command, String who) {
        if (!w.isBlockLoaded(p)) return "that machine is in an unloaded chunk";
        TileEntity t = w.getTileEntity(p);
        if (!(t instanceof TileGrid g)) return "no grid machine at " + p.getX() + " " + p.getY() + " " + p.getZ();
        return g.remote(command, who);
    }

    /** everything that needs attention right now */
    public static List<String> alarms(World w) {
        List<String> l = new ArrayList<>();
        GridData d = GridData.get(w);
        for (GridData.Net n : d.nets()) if (!n.problem.isEmpty() && !controlOnly(d, n)) l.add("Network #" + n.id + ": " + n.problem);
        for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
            GridData.Node n = e.getValue();
            BlockPos p = BlockPos.fromLong(e.getKey());
            String at = " at " + p.getX() + " " + p.getY() + " " + p.getZ();
            if (n.kind == GridKind.BREAKER && n.breaker == 2) l.add("Breaker TRIPPED" + at);
            if (w.isBlockLoaded(p) && w.getTileEntity(p) instanceof TileGrid g) {
                Map<String, Object> m = g.remoteInfo();
                Object f = m.get("fault");
                if (f instanceof String s && !s.isEmpty()) l.add(n.kind.label + at + ": " + s);
                if ("overload tripped".equals(m.get("state"))) l.add("Motor overload TRIPPED" + at);
            }
        }
        return l;
    }
}
