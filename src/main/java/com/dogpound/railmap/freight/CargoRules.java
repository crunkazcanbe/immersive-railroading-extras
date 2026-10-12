package com.dogpound.railmap.freight;

import com.dogpound.railmap.RailMapConfig;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Locale;

/**
 * Which wagon carries what (her ChatGPT list #90). The car's type comes from its name (every IR / Traincraft pack names
 * its stock "... Hopper", "... Boxcar", "... Flatcar"), the cargo's class from what the item is:
 *   covered hopper  bulk + grain              open hopper / gondola  bulk, long loads, scrap
 *   box car / van   packaged goods, grain, crates and containers
 *   flat / well car long loads (rails, sleepers, logs, plate), containers, crates, machinery
 *   tank car        fluids only (its tank decides which)
 * Dense cargo (ore, metal, stone) "weighs out" before it "cubes out": a car takes it only to its axle-load limit.
 * A car whose type can't be told from its name takes anything, so odd packs keep working.
 */
public final class CargoRules {
    private CargoRules() {}

    public enum Car { HOPPER, OPEN, BOX, FLAT, TANK, ANY }
    public enum Cargo { BULK, GRAIN, LONG, PACKAGED, CONTAINER }

    public static Car car(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("tank")) return Car.TANK;
        if (n.contains("hopper") || n.contains("grain") || n.contains("cement")) return Car.HOPPER;
        if (n.contains("gondola") || n.contains("open") || n.contains("coal") || n.contains("ore") || n.contains("mineral") || n.contains("ballast") || n.contains("tipper")) return Car.OPEN;
        if (n.contains("box") || n.contains("van") || n.contains("reefer") || n.contains("covered") || n.contains("wagon lit") || n.contains("parcel")) return Car.BOX;
        if (n.contains("flat") || n.contains("well") || n.contains("stake") || n.contains("bulkhead") || n.contains("spine") || n.contains("container") || n.contains("log") || n.contains("coil") || n.contains("rail carrier")) return Car.FLAT;
        return Car.ANY;
    }

    private static boolean ore(ItemStack s, String... prefixes) {
        for (int id : OreDictionary.getOreIDs(s)) {
            String o = OreDictionary.getOreName(id);
            for (String p : prefixes) if (o.startsWith(p)) return true;
        }
        return false;
    }

    public static Cargo cargo(ItemStack s) {
        String id = s.getItem().getRegistryName() == null ? "" : s.getItem().getRegistryName().toString();
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (id.equals("irextras:container") || path.contains("crate") || path.contains("container")) return Cargo.CONTAINER;
        if (ore(s, "crop", "seed", "grain", "dustWheat", "foodFlour") || path.equals("wheat") || path.contains("seeds") || path.equals("sugar") || path.contains("flour")) return Cargo.GRAIN;
        if (ore(s, "irRail", "irTie", "logWood", "plate", "stickSteel", "rod") || path.contains("rail") || path.contains("sleeper") || path.contains("pipe") || path.contains("beam") || path.contains("girder")) return Cargo.LONG;
        if (ore(s, "ore", "crushed", "dust", "gem", "coal", "fuelCoke", "sand", "gravel", "cobblestone", "stone", "clay") || path.contains("crushed") || path.contains("washed") || path.contains("concentrate")
                || path.equals("coal") || path.equals("gravel") || path.equals("sand") || path.equals("cobblestone") || path.equals("dirt") || path.equals("clay_ball") || path.contains("slag") || path.contains("scrap")) return Cargo.BULK;
        if (s.getItem() instanceof ItemBlock ib) {
            Material m = ib.getBlock().getDefaultState().getMaterial();
            if (m == Material.SAND || m == Material.GROUND || m == Material.CLAY) return Cargo.BULK;
        }
        return Cargo.PACKAGED;
    }

    /** dense goods: an axle-load limit, not the car's volume, decides how full it gets */
    public static boolean dense(ItemStack s) {
        if (ore(s, "ore", "ingot", "crushed", "nugget", "plate")) return true;
        if (s.getItem() instanceof ItemBlock ib && ib.getBlock().getDefaultState().getMaterial() == Material.ROCK) return true;
        String id = s.getItem().getRegistryName() == null ? "" : s.getItem().getRegistryName().getPath();
        return id.contains("ingot") || id.contains("ore") || id.contains("scrap") || id.contains("slag");
    }

    /** null = this car may take it; otherwise why not ("box cars don't take bulk ore") */
    public static String refuse(String carName, ItemStack s) {
        if (!RailMapConfig.cargoRules || s.isEmpty()) return null;
        Car c = car(carName);
        Cargo g = cargo(s);
        switch (c) {
            case ANY: return null;
            case TANK: return "tank cars carry fluids only";
            case HOPPER: return g == Cargo.BULK || g == Cargo.GRAIN ? null : "a hopper takes bulk and grain, not " + g.name().toLowerCase(Locale.ROOT) + " goods";
            case OPEN: return g == Cargo.BULK || g == Cargo.LONG ? null : "an open wagon takes bulk and long loads, not " + g.name().toLowerCase(Locale.ROOT) + " goods";
            case BOX: return g == Cargo.PACKAGED || g == Cargo.GRAIN || g == Cargo.CONTAINER ? null : "a box car takes packaged goods, grain and crates, not " + g.name().toLowerCase(Locale.ROOT) + " cargo";
            default: return g == Cargo.LONG || g == Cargo.CONTAINER || g == Cargo.PACKAGED ? null : "a flat car takes long loads, crates and machinery, not loose " + g.name().toLowerCase(Locale.ROOT);
        }
    }

    /** the fill % this car stops at for this cargo (100 = by volume) */
    public static int limitPct(String carName, ItemStack s) {
        if (!RailMapConfig.cargoRules || s.isEmpty() || !dense(s)) return 100;
        Car c = car(carName);
        return c == Car.FLAT ? 100 : c == Car.ANY ? 100 : 60;     // ore cars "weigh out" at ~60 % of their volume
    }

    // ---- axle loads + load distribution (ChatGPT batch 3 #24) -------------------------------------------------------
    /** what a vehicle's axles carry: per axle (t, front to rear), side-to-side and bogie-to-bogie imbalance (0..1) */
    public static final class Load {
        public double[] axleT = new double[0];
        public double totalT, cargoT, lateral, longitudinal, maxAxleT;
        public boolean left;                                   // the heavy side is the left
        public boolean front;                                  // the heavy end is the front
    }

    /** the route axle limit (t) */
    public static double axleLimitT() { return CargoConfig.axleLimitT; }

    /**
     * {front-left, front-right, rear-left, rear-right} share of the cargo from where it sits in the car's slots: rows of
     * `width` slots run front to rear, columns left to right; the middle row / column is shared half and half.
     * w[i] = weight in slot i. All zero = evenly spread.
     */
    public static double[] distribution(int width, double[] w) {
        double[] q = new double[4];
        int n = w.length, cols = Math.max(1, width), rows = Math.max(1, (n + cols - 1) / cols);
        double tot = 0;
        for (int i = 0; i < n; i++) {
            if (w[i] <= 0) continue;
            int r = i / cols, c = i % cols;
            double fr = share(r, rows), lf = share(c, cols);          // front share, left share
            q[0] += w[i] * fr * lf; q[1] += w[i] * fr * (1 - lf); q[2] += w[i] * (1 - fr) * lf; q[3] += w[i] * (1 - fr) * (1 - lf);
            tot += w[i];
        }
        if (tot <= 0) return new double[]{0.25, 0.25, 0.25, 0.25};
        for (int k = 0; k < 4; k++) q[k] /= tot;
        return q;
    }

    /** how much of index i (of n) is in the first half: 1, 0, or 0.5 for the middle of an odd count */
    private static double share(int i, int n) { double mid = (n - 1) / 2.0; return i < mid ? 1 : i > mid ? 0 : 0.5; }

    /** the loads: tare spread evenly, cargo by its distribution; axles split evenly between the two bogies */
    public static Load load(double tareKg, double cargoKg, double[] dist, int axles) {
        Load l = new Load();
        axles = Math.max(2, axles - axles % 2);
        double f = tareKg / 2 + cargoKg * (dist[0] + dist[1]), r = tareKg / 2 + cargoKg * (dist[2] + dist[3]);
        double left = tareKg / 2 + cargoKg * (dist[0] + dist[2]), right = tareKg / 2 + cargoKg * (dist[1] + dist[3]);
        l.axleT = new double[axles];
        for (int i = 0; i < axles; i++) l.axleT[i] = (i < axles / 2 ? f : r) / (axles / 2) / 1000.0;
        l.totalT = (tareKg + cargoKg) / 1000.0;
        l.cargoT = cargoKg / 1000.0;
        l.lateral = left + right <= 0 ? 0 : Math.abs(left - right) / (left + right);
        l.longitudinal = f + r <= 0 ? 0 : Math.abs(f - r) / (f + r);
        l.left = left > right; l.front = f > r;
        for (double a : l.axleT) l.maxAxleT = Math.max(l.maxAxleT, a);
        return l;
    }

    /** a real IR vehicle's load: tare from its definition, cargo = what it weighs over that, spread as its slots hold it */
    public static Load load(cam72cam.immersiverailroading.entity.EntityRollingStock s) {
        double total = 30000, tare = 20000;
        try { total = s.getWeight(); } catch (RuntimeException | LinkageError ignored) { }
        try { tare = Math.min(total, s.getDefinition().getWeight(s.gauge)); } catch (RuntimeException | LinkageError ignored) { tare = total; }
        double[] w = new double[0];
        int width = 1;
        if (s instanceof cam72cam.immersiverailroading.entity.Freight f && f.cargoItems != null) {
            try {
                net.minecraftforge.items.IItemHandler h = f.cargoItems.internal;
                w = new double[h.getSlots()];
                width = Math.max(1, f.getInventoryWidth());
                for (int i = 0; i < w.length; i++) { ItemStack st = h.getStackInSlot(i); if (!st.isEmpty()) w[i] = st.getCount() * (dense(st) ? 4 : 1); }
            } catch (RuntimeException | LinkageError ignored) { w = new double[0]; }
        }
        return load(tare, Math.max(0, total - tare), distribution(width, w), com.dogpound.railmap.track.Rails.axles(s));
    }
}
