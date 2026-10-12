package com.dogpound.railmap.yard;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.freight.ContractData;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * The yard's classification: which class track a car goes to. A car's facts come from the railway itself - its freight
 * contract (destination, commodity, hauler), what it is carrying, and its condition (wear, wheel flats, knuckles). Rules
 * are matched specific-first: "bad" tracks take bad-order cars before anything else, then destination / item: / owner: /
 * empty / loaded rules in track order, and "*" tracks last.
 */
final class Classifier {
    static final class Result {
        int track = -1;
        String dest = "", why = "";
    }

    private final World world;
    private final TileYardPanel panel;
    private final ContractData contracts;

    Classifier(World world, TileYardPanel panel) {
        this.world = world;
        this.panel = panel;
        this.contracts = ContractData.get(world);
    }

    Result classify(EntityRollingStock s) {
        Result r = new Result();
        Contract k = null;
        for (Contract c : contracts.all()) if (c.state == Contract.State.ACCEPTED && c.cars.containsKey(s.getUUID())) { k = c; break; }
        String cargo = cargo(s);
        boolean loaded = !cargo.isEmpty();
        boolean bad = badOrder(s);
        String dest = k != null ? k.toName : loaded ? "loaded" : "empty";
        r.dest = bad ? "BAD ORDER" : dest;
        r.why = (k != null ? "waybill #" + k.id + " to " + k.toName : "no waybill") + (loaded ? ", " + cargo : ", empty") + (bad ? ", bad order" : "");
        // 1. bad order
        if (bad) for (int i = 0; i < TileYardPanel.TRACKS; i++) if (has(i, "bad") || has(i, "badorder")) { r.track = i; return r; }
        // 2. specific rules
        String wb = k != null ? k.toName : "";
        for (int i = 0; i < TileYardPanel.TRACKS; i++) {
            if (panel.clear[i] == null) continue;
            if (YardPlanner.reservedAgainst(world, panel, i, wb, cargo)) continue;     // a departure road reserved for other traffic (next-48 #4)
            for (String tok : tokens(i)) {
                if (tok.equals("*") || tok.equals("bad") || tok.equals("badorder")) continue;
                boolean hit;
                if (tok.startsWith("item:")) hit = loaded && cargo.toLowerCase(Locale.ROOT).contains(tok.substring(5).trim());
                else if (tok.startsWith("owner:")) hit = k != null && k.byName.toLowerCase(Locale.ROOT).contains(tok.substring(6).trim());
                else if (tok.equals("empty")) hit = !loaded;
                else if (tok.equals("loaded")) hit = loaded;
                else hit = k != null && k.toName.toLowerCase(Locale.ROOT).contains(tok);
                if (hit) { r.track = i; return r; }
            }
        }
        // 3. everything else
        for (int i = 0; i < TileYardPanel.TRACKS; i++) if (panel.clear[i] != null && has(i, "*") && !YardPlanner.reservedAgainst(world, panel, i, wb, cargo)) { r.track = i; return r; }
        return r;
    }

    private boolean has(int i, String tok) {
        if (panel.clear[i] == null) return false;
        for (String t : tokens(i)) if (t.equals(tok)) return true;
        return false;
    }

    private String[] tokens(int i) {
        String rule = panel.settings().text("t" + (i + 1)).toLowerCase(Locale.ROOT);
        String[] p = rule.split("[,;]");
        for (int j = 0; j < p.length; j++) p[j] = p[j].trim();
        return p;
    }

    private boolean badOrder(EntityRollingStock s) {
        WearData.Record w = WearData.get(world).peek(s.getUUID());
        if (w != null && w.worst() >= 80) return true;
        SimData.Car c = SimData.get(world).peek(s.getUUID());
        return c != null && (c.flatMm >= 60 || Math.min(c.coupler[0], c.coupler[1]) <= 0.15f);
    }

    /** what the car carries ("" = empty): the biggest stack, or the tank's fluid */
    static String cargo(EntityRollingStock s) {
        if (s instanceof FreightTank t && t.getLiquidAmount() > 0 && t.getLiquid() != null) return t.getLiquid().ident;
        if (s instanceof Freight f) {
            String best = "";
            int n = 0;
            for (int i = 0; i < f.cargoItems.getSlotCount(); i++) {
                cam72cam.mod.item.ItemStack st = f.cargoItems.get(i);
                if (!st.isEmpty() && st.getCount() > n) { n = st.getCount(); best = st.getDisplayName(); }
            }
            return best;
        }
        return "";
    }
}
