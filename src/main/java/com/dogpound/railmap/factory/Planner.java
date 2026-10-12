package com.dogpound.railmap.factory;

import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.freight.ContractData;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.industry.IndustryLink;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.*;

/**
 * Plant-wide production planning (her "next 48" list #23): one plan across the whole ore -> steel -> rail chain.
 * <ol>
 *   <li>DEMAND: the planner's own targets (rails and steel per hour) plus every freight contract for those goods that
 *       has to depart (quantity still to load / hours to its deadline), less what is already stockpiled in the plants'
 *       output hatches;</li>
 *   <li>the demand runs UP the chain through the stage ratios (2 steel per rail length, 2 concentrate + 1 coke per
 *       steel, washed per concentrate, crushed per washed);</li>
 *   <li>CAPACITY per stage from its plants (measured output while running, the rated rate otherwise): utilisation and the
 *       BOTTLENECK stage;</li>
 *   <li>POWER BIDS: each plant bids for power by how badly the plan needs its stage (utilisation, freight due in the next
 *       hour); per grid network the industrial headroom (rating less the non-industrial load) is cleared in bid order -
 *       plants that lose are paused (AUTO), and the clearing price is what the last accepted bid offered;</li>
 *   <li>NON-CRITICAL plants whose stockpile already covers two hours of demand while their stage is under-used are
 *       paused; the rolling mill is switched to its RAIL pass when rails are wanted.</li>
 * </ol>
 */
public final class Planner {
    private Planner() {}

    public enum Stage {
        CRUSH("Crushing", MachineType.JAW_CRUSHER), WASH("Washing", MachineType.WASHING_PLANT), FLOAT("Flotation", MachineType.FLOTATION),
        COKE("Coke", MachineType.COKE_OVEN), STEEL("Steelmaking", MachineType.BLAST_FURNACE, MachineType.ARC_FURNACE, MachineType.SMELTER),
        ROLL("Rolling (rail)", MachineType.ROLLING_MILL), SLEEPER("Sleepers", MachineType.TIE_PLANT, MachineType.SAWMILL),
        ASSEMBLY("Fabrication", MachineType.WELDING_CELL, MachineType.ASSEMBLY_LINE, MachineType.WIRE_DRAWER);
        public final String label;
        public final MachineType[] types;
        Stage(String label, MachineType... t) { this.label = label; types = t; }
        public static Stage of(MachineType t) { for (Stage s : values()) for (MachineType x : s.types) if (x == t) return s; return null; }
    }

    /** rated output per plant, items per hour (when it has no measured rate yet) */
    static final double RATED = 60;

    public static final class PlantRow {
        public TileMachine m;
        public Stage stage;
        public double bid, kw;
        public int net = -1;
        public String verdict = "", hold = "";
        public int stock;
    }

    public static final class Plan {
        public final EnumMap<Stage, Double> demand = new EnumMap<>(Stage.class), capacity = new EnumMap<>(Stage.class), stock = new EnumMap<>(Stage.class);
        public final EnumMap<Stage, Integer> plants = new EnumMap<>(Stage.class);
        public final List<PlantRow> rows = new ArrayList<>();
        public final List<String> freight = new ArrayList<>(), power = new ArrayList<>();
        public final Set<Stage> late = EnumSet.noneOf(Stage.class);
        public Stage bottleneck;
        public double bottleneckUtil, clearing;
        public int held, atRisk;
        public double util(Stage s) { double c = capacity.getOrDefault(s, 0.0); return c <= 0 ? (demand.getOrDefault(s, 0.0) > 0 ? 9.99 : 0) : demand.getOrDefault(s, 0.0) / c; }
    }

    /** which stage's product a contract wants (by item name), or null */
    static Stage product(String itemName) {
        String n = itemName.toLowerCase(Locale.ROOT);
        if (n.contains("rail")) return Stage.ROLL;
        if (n.contains("steel") || n.contains("ingot")) return Stage.STEEL;
        if (n.contains("sleeper") || n.contains("tie")) return Stage.SLEEPER;
        if (n.contains("coke")) return Stage.COKE;
        if (n.contains("concentrate")) return Stage.FLOAT;
        return null;
    }

    public static Plan plan(World w, double railPerH, double steelPerH, boolean freight) {
        Plan p = new Plan();
        long now = w.getTotalWorldTime();
        for (Stage s : Stage.values()) { p.demand.put(s, 0.0); p.capacity.put(s, 0.0); p.stock.put(s, 0.0); p.plants.put(s, 0); }
        // plants, stock, capacity
        Map<Long, IndustryLink.Load> loads = new HashMap<>();
        for (IndustryLink.Load l : IndustryLink.loads(w)) loads.put(l.pos.toLong(), l);
        for (TileEntity t : IndustryLink.all(w)) {
            if (!(t instanceof TileMachine m) || !m.formed()) continue;
            Stage s = Stage.of(m.type());
            if (s == null) continue;
            PlantRow r = new PlantRow();
            r.m = m; r.stage = s;
            for (ItemStack st : m.makes()) r.stock += st.getCount();
            IndustryLink.Load l = loads.get(m.getPos().toLong());
            r.kw = l == null ? m.type().power * com.dogpound.railmap.grid.Elec.W_PER_FE_T / 1000.0 : Math.max(l.kw, m.type().power * com.dogpound.railmap.grid.Elec.W_PER_FE_T / 1000.0 * 0.5);
            r.net = l == null ? -1 : l.net;
            p.rows.add(r);
            p.plants.merge(s, 1, Integer::sum);
            p.stock.merge(s, (double) r.stock, Double::sum);
            double rate = m.planRunning() && m.planRate() > 0 ? m.planRate() * 60.0 : RATED;
            p.capacity.merge(s, rate, Double::sum);
        }
        // demand: targets + freight departures
        p.demand.merge(Stage.ROLL, railPerH, Double::sum);
        p.demand.merge(Stage.STEEL, steelPerH, Double::sum);
        if (freight) {
            for (Contract k : ContractData.get(w).board()) {
                if (k.state != Contract.State.OPEN && k.state != Contract.State.ACCEPTED) continue;
                Stage s = product(k.itemName);
                if (s == null) continue;
                int left = Math.max(0, k.qty - k.loaded);
                double hours = Math.max(0.05, (k.deadline - now) / 72000.0);        // hours of play (72000 ticks), the unit of every rate here
                double need = left / hours;
                p.demand.merge(s, need, Double::sum);
                double ready = p.stock.getOrDefault(s, 0.0);
                boolean risk = ready < left && p.capacity.getOrDefault(s, 0.0) * hours < left - ready;
                if (risk) { p.atRisk++; p.late.add(s); }
                p.freight.add(String.format(Locale.ROOT, "#%d %s x%d to %s - %s left - stock %.0f - %s", k.id, k.itemName, left, k.toName, k.timeLeft(now), ready, risk ? "AT RISK" : ready >= left ? "READY TO DEPART" : "ON PLAN"));
            }
        }
        // stockpile above an hour's demand counts against it
        for (Stage s : Stage.values()) { double st = p.stock.get(s), d = p.demand.get(s); if (d > 0) p.demand.put(s, Math.max(0, d - Math.max(0, st - d) * 0.5)); }
        // up the chain
        p.demand.merge(Stage.STEEL, p.demand.get(Stage.ROLL) * 2, Double::sum);
        p.demand.merge(Stage.STEEL, p.demand.get(Stage.ASSEMBLY) * 0.5, Double::sum);
        p.demand.merge(Stage.COKE, p.demand.get(Stage.STEEL), Double::sum);
        p.demand.merge(Stage.FLOAT, p.demand.get(Stage.STEEL) * 2, Double::sum);
        p.demand.merge(Stage.WASH, p.demand.get(Stage.FLOAT), Double::sum);
        p.demand.merge(Stage.CRUSH, p.demand.get(Stage.WASH) / 2, Double::sum);
        for (Stage s : Stage.values()) { double u = p.util(s); if (p.plants.get(s) > 0 && u > p.bottleneckUtil) { p.bottleneckUtil = u; p.bottleneck = s; } }
        // bids: the plan's need for each stage
        for (PlantRow r : p.rows) {
            double u = Math.min(2, p.util(r.stage));
            boolean due = p.late.contains(r.stage);
            r.bid = Math.round((20 + 80 * u + (due ? 100 : 0) + (r.stage == p.bottleneck ? 40 : 0)) * 10) / 10.0;
            double st = r.stock, d = p.demand.get(r.stage);
            if (u < 0.3 && st > 0 && st >= 2 * Math.max(1, d)) r.hold = "PLANNER: stock covers 2 h - paused (non-critical)";
        }
        // power: per network, clear the industrial headroom in bid order
        GridData gd = GridData.get(w);
        Map<Integer, List<PlantRow>> byNet = new TreeMap<>();
        for (PlantRow r : p.rows) if (r.net >= 0 && r.hold.isEmpty()) byNet.computeIfAbsent(r.net, k -> new ArrayList<>()).add(r);
        double lowest = Double.MAX_VALUE;
        for (Map.Entry<Integer, List<PlantRow>> e : byNet.entrySet()) {
            GridData.Net n = e.getKey() < gd.nets().size() ? gd.nets().get(e.getKey()) : null;
            if (n == null || n.level == null) continue;
            double cap = 0;
            for (long c : n.ins) { GridData.Node cv = gd.nodes().get(c); if (cv != null && cv.passes()) cap += cv.kva * 1000.0 * 0.9; }
            for (long q : n.nodes) { GridData.Node nd = gd.nodes().get(q); if (nd != null && nd.kind.source() && nd.feeding() && nd.kind != GridKind.INTAKE) cap += nd.kva * 1000.0 * 0.9; else if (nd != null && nd.kind == GridKind.INTAKE && nd.feeding()) cap += 1e9; }
            double industry = 0;
            for (PlantRow r : e.getValue()) industry += r.m.planRunning() ? r.kw * 1000 : 0;
            double other = Math.max(0, n.sVA - industry);
            double head = cap - other;
            List<PlantRow> l = new ArrayList<>(e.getValue());
            l.sort((a, b) -> Double.compare(b.bid, a.bid));
            double used = 0, clear = 0;
            int lost = 0;
            for (PlantRow r : l) {
                if (used + r.kw * 1000 <= head) { used += r.kw * 1000; clear = r.bid; r.verdict = "WON"; }
                else { lost++; r.verdict = "LOST"; r.hold = String.format(Locale.ROOT, "PLANNER: lost its power bid ($%.0f/MWh) on network #%d", r.bid, n.id); }
            }
            if (clear > 0) lowest = Math.min(lowest, clear);
            p.power.add(String.format(Locale.ROOT, "NET #%d %s: headroom %.0f kW, industry %.0f kW bid, clearing $%.0f/MWh%s", n.id, n.level.label, Math.max(0, head) / 1000, used / 1000, clear, lost > 0 ? ", " + lost + " plant(s) LOST" : ""));
        }
        p.clearing = lowest == Double.MAX_VALUE ? 0 : lowest;
        for (PlantRow r : p.rows) if (!r.hold.isEmpty()) p.held++;
        return p;
    }
}
