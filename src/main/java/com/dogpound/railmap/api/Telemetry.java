package com.dogpound.railmap.api;

import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.Reading;
import com.dogpound.railmap.wire.IWiredMachine;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

/**
 * Universal machine telemetry (her ChatGPT list #97): one standard reading for anything with a block entity - IR
 * Extras gear and grid machines, Pride Industry plants, signals, and any other mod's machine that exposes Forge
 * inventories, tanks or energy. Seeded by IWiredMachine.wiredInfo (what the Data Link already reads). Every record has:
 * <pre>
 * schema 1: kind, name, mod, x, y, z, source
 *   state, mode, running, fault, lamp (1 ok / 2 stopped / 3 fault), powered12v
 *   kw (drawn, +) / kwOut (produced), tempC, wearPct
 *   items, itemSlots, fluids [{fluid, mb, cap}], energy, energyCap
 *   commands [control ids], ranges {control: [positions]}, data {everything the machine itself reports}
 * </pre>
 * Other mods add or override readings with {@link #register(Source)} (or RailApi.registerTelemetry).
 */
public final class Telemetry {
    private Telemetry() {}

    public static final int SCHEMA = 1;

    /** a mod's own reader: fill (or add to) the record for a block entity it understands */
    public interface Source {
        String id();
        /** return true if it handled this block entity (later sources and the generic reader still run) */
        boolean read(TileEntity te, Map<String, Object> out);
    }

    private static final List<Source> SOURCES = new ArrayList<>();

    public static synchronized void register(Source s) {
        SOURCES.removeIf(x -> x.id().equals(s.id()));
        SOURCES.add(s);
    }

    public static synchronized List<Source> sources() { return new ArrayList<>(SOURCES); }

    /** the standard record for whatever stands at p, or null (nothing there / unloaded / nothing to report) */
    public static Map<String, Object> read(World w, BlockPos p) {
        if (w == null || p == null || !w.isBlockLoaded(p)) return null;
        TileEntity te = w.getTileEntity(p);
        return te == null ? null : read(te);
    }

    public static Map<String, Object> read(TileEntity te) {
        Map<String, Object> m = new LinkedHashMap<>();
        BlockPos p = te.getPos();
        String rn = te.getBlockType() == null || te.getBlockType().getRegistryName() == null ? "?" : te.getBlockType().getRegistryName().toString();
        m.put("schema", SCHEMA);
        m.put("kind", rn.substring(rn.indexOf(':') + 1));
        m.put("mod", rn.contains(":") ? rn.substring(0, rn.indexOf(':')) : "?");
        m.put("name", te.getBlockType() == null ? rn : te.getBlockType().getLocalizedName());
        m.put("x", p.getX()); m.put("y", p.getY()); m.put("z", p.getZ());
        boolean any = false;
        String src = "generic";
        if (te instanceof IWiredMachine wm) {
            try { wired(te.getWorld(), wm, m); any = true; src = "wired"; } catch (RuntimeException | LinkageError e) { m.put("error", e.toString()); }
        }
        for (Source s : sources()) {
            try { if (s.read(te, m)) { any = true; src = s.id(); } } catch (RuntimeException | LinkageError e) { m.put("error", s.id() + ": " + e); }
        }
        any |= caps(te, m);
        m.put("source", src);
        return any ? m : null;
    }

    private static void wired(World w, IWiredMachine wm, Map<String, Object> m) {
        Map<String, Object> d = wm.wiredInfo();
        m.put("name", wm.wiredName());
        m.put("data", d);
        int lamp = wm.wiredLamp();
        m.put("lamp", lamp);
        m.put("running", d.containsKey("running") ? d.get("running") : lamp == 1);
        m.put("state", d.containsKey("state") ? String.valueOf(d.get("state")) : lamp == 1 ? "running" : lamp == 2 ? "stopped" : "fault");
        Object f = first(d, "fault", "problem", "trip");
        m.put("fault", f == null || Boolean.FALSE.equals(f) ? (lamp == 3 ? "fault" : "") : String.valueOf(f));
        if (d.containsKey("powered")) m.put("powered12v", d.get("powered"));
        if (d.containsKey("mode")) m.put("mode", String.valueOf(d.get("mode")));
        Object kw = first(d, "kw", "kW", "powerKw", "load");
        if (kw instanceof Number n) m.put("kw", n.doubleValue());
        Object out = first(d, "kwOut", "output", "gen");
        if (out instanceof Number n) m.put("kwOut", n.doubleValue());
        Object t = first(d, "tempC", "temp", "temperature");
        if (t instanceof Number n) m.put("tempC", n.doubleValue());
        Object wear = first(d, "wearPct", "wear", "condition");
        if (wear instanceof Number n) m.put("wearPct", n.doubleValue());
        // a reading through the same instruments the gauges use, when the machine offers one
        try {
            Reading r = new Reading();
            wm.wiredRead(r, com.dogpound.railmap.grid.BlockInstrument.Inst.WATT);
            if (!m.containsKey("kw") && "kW".equalsIgnoreCase(r.unit)) m.put("kw", r.value);
        } catch (RuntimeException ignored) { }
        List<String> cmds = new ArrayList<>();
        Map<String, Object> ranges = new LinkedHashMap<>();
        Panel pn = wm.wiredPanel();
        if (pn != null) for (Panel.C c : Reading.buttons(pn)) {
            cmds.add(c.id);
            if (c.positions != null && c.positions.length > 0) ranges.put(c.id, Arrays.asList(c.positions));
        }
        m.put("commands", cmds);
        m.put("ranges", ranges);
    }

    private static Object first(Map<String, Object> d, String... keys) {
        for (String k : keys) if (d.containsKey(k)) return d.get(k);
        return null;
    }

    /** Forge capabilities: inventory, tanks, energy (works for every mod's machines) */
    private static boolean caps(TileEntity te, Map<String, Object> m) {
        boolean any = false;
        try {
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
                IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (h != null) {
                    int n = 0;
                    for (int i = 0; i < h.getSlots(); i++) n += h.getStackInSlot(i).getCount();
                    m.put("items", n); m.put("itemSlots", h.getSlots());
                    any = true;
                }
            }
            if (te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
                IFluidHandler f = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
                if (f != null) {
                    List<Object> l = new ArrayList<>();
                    for (IFluidTankProperties t : f.getTankProperties()) {
                        Map<String, Object> x = new LinkedHashMap<>();
                        x.put("fluid", t.getContents() == null ? "" : t.getContents().getFluid().getName());
                        x.put("mb", t.getContents() == null ? 0 : t.getContents().amount);
                        x.put("cap", t.getCapacity());
                        l.add(x);
                    }
                    m.put("fluids", l);
                    any = true;
                }
            }
            if (te.hasCapability(CapabilityEnergy.ENERGY, null)) {
                IEnergyStorage e = te.getCapability(CapabilityEnergy.ENERGY, null);
                if (e != null) { m.put("energy", e.getEnergyStored()); m.put("energyCap", e.getMaxEnergyStored()); any = true; }
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return any;
    }

    /**
     * one number, for expressions and logic modules: "x,y,z.key" (key may go into data: "x,y,z.data.progress"; "items",
     * "fluid0" = mB in the first tank, "lamp", "running" 1/0, "fault" 1/0). Unknown / unloaded = 0.
     */
    public static double value(World w, String spec) {
        int dot = spec.indexOf('.');
        if (dot < 0) throw new IllegalArgumentException("telemetry needs x,y,z.key");
        String[] c = spec.substring(0, dot).split(",");
        if (c.length != 3) throw new IllegalArgumentException("telemetry needs x,y,z.key");
        BlockPos p;
        try { p = new BlockPos(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim())); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("bad telemetry position " + spec.substring(0, dot)); }
        Map<String, Object> m = read(w, p);
        if (m == null) return 0;
        String key = spec.substring(dot + 1);
        if (key.matches("fluid\\d+")) {
            Object l = m.get("fluids");
            int i = Integer.parseInt(key.substring(5));
            if (l instanceof List<?> ls && i < ls.size() && ls.get(i) instanceof Map<?, ?> f && f.get("mb") instanceof Number n) return n.doubleValue();
            return 0;
        }
        Object v = key.startsWith("data.") && m.get("data") instanceof Map<?, ?> d ? d.get(key.substring(5)) : m.get(key);
        return num(v);
    }

    public static double num(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            if (s.isEmpty()) return 0;
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 1; }   // a non-empty text (a fault) = 1
        }
        return 0;
    }

    /** every loaded machine that reports something (IR Extras gear, grid, Pride Industry, registered sources) */
    public static List<Map<String, Object>> all(World w, int max) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Source> extra = sources();
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList)) {
            if (out.size() >= max) break;
            if (te.isInvalid()) continue;
            boolean known = te instanceof IWiredMachine;
            if (!known) for (Source s : extra) { Map<String, Object> probe = new HashMap<>(); try { if (s.read(te, probe)) { known = true; break; } } catch (RuntimeException ignored) { } }
            if (!known) continue;
            Map<String, Object> m = read(te);
            if (m != null) out.add(m);
        }
        return out;
    }
}
