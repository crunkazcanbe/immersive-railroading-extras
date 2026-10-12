package com.dogpound.railmap.industry;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.TileGrid;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The link between the railway and any mod's industry (her ChatGPT list section 13: "let railway operations depend on
 * what Pride Industry produces"). IR Extras never refers to another mod here: a factory (Pride Industry's plants, our own
 * industries, depot stores, the fuel point) implements {@link Endpoint} and registers itself while loaded; freight
 * contracts, automatic orders, the commodity market and the console then treat every endpoint the same way. Ports and
 * hatches of a multiblock implement {@link Part} so a loader working off a hatch bills the plant it belongs to.
 *
 * It also tells a factory what its supply looks like (voltage, frequency, harmonics, unbalance) and whether the grid is
 * shedding industrial load right now (#88: factories and traction compete for the same generation - when the supply sags
 * or the frequency falls, industry is dropped first so the trains keep running), and keeps a ledger of what each factory
 * draws for the console (#88, #94).
 */
public final class IndustryLink {
    private IndustryLink() {}

    /** a producer and / or consumer the railway serves */
    public interface Endpoint {
        /** what the plant is called on waybills and the console */
        String endpointName();
        /** the kinds of goods it produces; each stack's count is how many are ready to ship now, at least 1 (an ItemStack of 0 is empty) */
        List<ItemStack> makes();
        /** would its stockpile take this? */
        boolean accepts(ItemStack s);
        /** what it is short of: each stack's count is how many it wants delivered (empty = it is stocked) */
        List<ItemStack> wants();
        /** fluids it produces (amount = ready now, mB = litres) */
        default List<FluidStack> fluidsMade() { return Collections.emptyList(); }
        /** fluids it is short of (amount = litres wanted) */
        default List<FluidStack> fluidsWanted() { return Collections.emptyList(); }
        /** world tick the next production batch is ready (contracts are timed to it), or -1 = ready now / unknown */
        default long readyAt() { return -1; }
        /** a short state for the console ("RUNNING", "NO FEED", "SHED"...) */
        default String endpointState() { return ""; }
    }

    /** a hatch / port of a multiblock: the endpoint it belongs to (null when not part of a formed plant) */
    public interface Part {
        BlockPos endpoint();
    }

    // ---- every loaded endpoint, per dimension -------------------------------------------------------------------------
    private static final Map<Integer, Set<Long>> LOADED = new ConcurrentHashMap<>();

    /** call from the tile's onLoad / update (cheap: a set add) */
    public static void register(TileEntity t) {
        if (t.getWorld() == null || t.getWorld().isRemote || !(t instanceof Endpoint)) return;
        LOADED.computeIfAbsent(t.getWorld().provider.getDimension(), d -> ConcurrentHashMap.newKeySet()).add(t.getPos().toLong());
    }

    public static void forget(TileEntity t) {
        if (t.getWorld() == null || t.getWorld().isRemote) return;
        Set<Long> s = LOADED.get(t.getWorld().provider.getDimension());
        if (s != null) s.remove(t.getPos().toLong());
    }

    /** every loaded endpoint in this world (stale entries are dropped as they are found) */
    public static List<TileEntity> all(World w) {
        List<TileEntity> out = new ArrayList<>();
        Set<Long> s = LOADED.get(w.provider.getDimension());
        if (s == null) return out;
        for (Iterator<Long> it = s.iterator(); it.hasNext(); ) {
            BlockPos p = BlockPos.fromLong(it.next());
            if (!w.isBlockLoaded(p)) continue;
            TileEntity t = w.getTileEntity(p);
            if (t instanceof Endpoint && !t.isInvalid()) out.add(t);
            else it.remove();
        }
        return out;
    }

    public static Endpoint at(World w, BlockPos p) {
        return w.isBlockLoaded(p) && w.getTileEntity(p) instanceof Endpoint e ? e : null;
    }

    /** the endpoint a stockpile block belongs to: a plant's hatch resolves to its controller; anything else is itself */
    public static BlockPos resolve(World w, BlockPos yard) {
        if (yard == null || !w.isBlockLoaded(yard)) return yard;
        TileEntity t = w.getTileEntity(yard);
        if (t instanceof Part p && p.endpoint() != null) return p.endpoint();
        return yard;
    }

    /** a loader / unloader at `yard` serves the industry at `endpoint`: its own hatch, or a siding within 24 blocks */
    public static boolean serves(World w, BlockPos endpoint, BlockPos yard) {
        if (endpoint == null || yard == null) return false;
        if (endpoint.equals(yard) || endpoint.equals(resolve(w, yard))) return true;
        return endpoint.distanceSq(yard) <= 24 * 24;
    }

    // ---- the supply a factory sees ------------------------------------------------------------------------------------
    /** what the network feeding a factory looks like right now */
    public static final class Supply {
        public boolean live;            // a healthy network with a voltage level
        public boolean shed;            // the grid is shedding industrial load: don't draw
        public boolean ac;
        public double volts, pu = 1, hz, hzNominal, thdV, vuf;
        public int net = -1;
        public String level = "", why = "";

        /** first power-quality limit this supply breaks for a sensitive load, or "" (limits: voltage band %, frequency band Hz, THD %, unbalance %) */
        public String breaks(double vBand, double hzBand, double thdMax, double vufMax) {
            if (!live) return "";
            if (Math.abs(pu - 1) * 100 > vBand) return String.format(Locale.ROOT, pu < 1 ? "UNDERVOLTAGE %.0f%%" : "OVERVOLTAGE %.0f%%", pu * 100);
            if (ac && Math.abs(hz - hzNominal) > hzBand) return String.format(Locale.ROOT, "FREQUENCY %.2f Hz", hz);
            if (ac && thdV > thdMax) return String.format(Locale.ROOT, "HARMONICS THD %.1f%%", thdV);
            if (ac && vuf > vufMax) return String.format(Locale.ROOT, "UNBALANCE %.1f%%", vuf);
            return "";
        }
    }

    /** load-shedding state per network id (hysteresis: on fast, off only after 10 s of a healthy supply) */
    private static final Map<String, long[]> SHED = new ConcurrentHashMap<>();

    /** the grid network touching the block at `at` (any side), as a factory sees it */
    public static Supply supply(World w, BlockPos at) {
        Supply s = new Supply();
        GridData d = GridData.get(w);
        GridData.Net n = null;
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos q = at.offset(f);
            if (!w.isBlockLoaded(q) || !(w.getTileEntity(q) instanceof TileGrid)) continue;
            n = d.netAt(q, f.getOpposite());
            if (n != null) break;
        }
        if (n == null) { s.why = "no grid connection"; return s; }
        return of(w, n, s);
    }

    /** the supply on the network a grid block (a motor, a feeder) sits on */
    public static Supply supplyOf(World w, BlockPos gridBlock) {
        GridData.Net n = GridData.get(w).netOf(gridBlock);
        Supply s = new Supply();
        if (n == null) { s.why = "no grid connection"; return s; }
        return of(w, n, s);
    }

    private static Supply of(World w, GridData.Net n, Supply s) {
        s.net = n.id;
        if (!n.problem.isEmpty() || n.level == null) { s.why = n.problem.isEmpty() ? "no voltage level" : n.problem; return s; }
        Elec.Level lv = n.level;
        s.live = true; s.ac = lv.ac; s.level = lv.label;
        s.pu = n.puSource; s.volts = lv.volts * n.puSource;
        s.hzNominal = lv.ac ? lv.hz : 0; s.hz = lv.ac ? lv.hz * n.hzPu : 0;
        s.thdV = n.thdV; s.vuf = n.vuf;
        s.shed = shedding(w, n);
        return s;
    }

    /** under-voltage / under-frequency load shedding: industry goes first so traction keeps the generation it has */
    private static boolean shedding(World w, GridData.Net n) {
        if (!RailMapConfig.industryLoadShedding) return false;
        String key = w.provider.getDimension() + ":" + n.id;
        long now = w.getTotalWorldTime();
        long[] st = SHED.computeIfAbsent(key, k -> new long[]{0, 0});     // {shedding 0/1, tick the supply last looked bad}
        boolean bad = n.puSource < 0.92 || n.level.ac && n.hzPu < 0.985;
        if (bad) {
            st[1] = now;
            if (st[0] == 0) {
                st[0] = 1;
                String why = n.puSource < 0.92 ? String.format(Locale.ROOT, "voltage %.0f%%", n.puSource * 100) : String.format(Locale.ROOT, "frequency %.2f Hz", n.level.hz * n.hzPu);
                AlarmManager.get(w).raise(w, "indshed:" + key, AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR,
                        "Industrial load shed on network #" + n.id + " (" + n.level.label + ", " + why + ") - factories stopped so traction keeps its supply", null, true);
            }
        } else if (st[0] == 1 && now - st[1] > 200) {
            st[0] = 0;
            AlarmManager.get(w).clear("indshed:" + key);
            Recorder.event(w, "Grid: industrial load restored on network #" + n.id);
        }
        return st[0] == 1;
    }

    // ---- the factory load ledger (console) ----------------------------------------------------------------------------
    /** one factory's draw: name, state, kW, network id, supply text, last tick reported */
    public static final class Load {
        public String name = "", state = "", supply = "";
        public double kw;
        public int net = -1;
        public long tick;
        public BlockPos pos = BlockPos.ORIGIN;
    }

    private static final Map<Integer, Map<Long, Load>> LOADS = new ConcurrentHashMap<>();

    /** a factory reports what it draws (call about once a second) */
    public static void reportLoad(World w, BlockPos pos, String name, double kw, Supply s, String state) {
        if (w.isRemote) return;
        Load l = LOADS.computeIfAbsent(w.provider.getDimension(), d -> new ConcurrentHashMap<>()).computeIfAbsent(pos.toLong(), k -> new Load());
        l.name = name; l.kw = kw; l.state = state; l.tick = w.getTotalWorldTime(); l.pos = pos;
        l.net = s == null ? -1 : s.net;
        l.supply = s == null ? "" : !s.live ? s.why : s.shed ? "SHED - " + s.level : String.format(Locale.ROOT, "%s %.0f%%%s", s.level, s.pu * 100, s.ac ? String.format(Locale.ROOT, " %.2fHz", s.hz) : "");
    }

    /** every factory that reported in the last 10 s */
    public static List<Load> loads(World w) {
        List<Load> out = new ArrayList<>();
        Map<Long, Load> m = LOADS.get(w.provider.getDimension());
        if (m == null) return out;
        long now = w.getTotalWorldTime();
        m.values().removeIf(l -> now - l.tick > 600);
        for (Load l : m.values()) if (now - l.tick <= 200) out.add(l);
        out.sort((a, b) -> Double.compare(b.kw, a.kw));
        return out;
    }

    /** total industrial draw, kW */
    public static double industryKw(World w) { double s = 0; for (Load l : loads(w)) s += l.kw; return s; }
}
