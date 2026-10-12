package com.dogpound.railmap.asset;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.depot.Stores;
import com.dogpound.railmap.depot.TileDepotStores;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Spare-parts supply (her 48-next list #16). The depot stores' real bins are the inventory. Purchase orders carry a
 * spare, a quantity, a supplier and its lead time:
 * <pre>
 *   CATALOGUE  2 days, list price          EXPRESS  half a day, 2.5x the price
 *   FACTORY    an in-house works order: closed when the factory's output reaches the stores (freight #91)
 *   REMAN      an exchange unit from an outside rebuilder: 1 day, 60 % of the price
 * </pre>
 * Shortages: job cards waiting for parts and asset repairs waiting for parts; with autoProcure the stores order what
 * they are short of by themselves (and keep two of each infrastructure spare). Substitutions: a job short of a part may
 * use an equivalent set that is in stock (a bogie built up from wheelsets, bearings and brake blocks...).
 */
public final class Procurement {
    private Procurement() {}

    /** list price per spare, in coins (Spare order) */
    static final int[] PRICE = {60, 900, 140, 2600, 1800, 420, 160, 40, 30, 4200, 0, 1100, 1500, 380, 120, 650, 1700, 900, 300};
    public static final long[] LEAD = {48000, 12000, 0, 24000};
    static final double[] COST_K = {1.0, 2.5, 0.45, 0.6};

    public static int price(ItemSpare.Spare s) { return s.ordinal() < PRICE.length ? PRICE[s.ordinal()] : 500; }

    /** substitutes: {short spare, {sub spare, qty}...} */
    static final int[][][] SUBS = {
            {{ItemSpare.Spare.BOGIE.ordinal()}, {ItemSpare.Spare.WHEELSET.ordinal(), 2}, {ItemSpare.Spare.AXLE_BEARING.ordinal(), 4}, {ItemSpare.Spare.BRAKE_BLOCKS.ordinal(), 1}},
            {{ItemSpare.Spare.TRACTION_MOTOR.ordinal()}, {ItemSpare.Spare.MOTOR_KIT.ordinal(), 1}, {ItemSpare.Spare.AXLE_BEARING.ordinal(), 1}},
            {{ItemSpare.Spare.POINT_MOTOR.ordinal()}, {ItemSpare.Spare.MOTOR_KIT.ordinal(), 1}, {ItemSpare.Spare.CONTROL_CARD.ordinal(), 1}},
            {{ItemSpare.Spare.SIGNAL_MODULE.ordinal()}, {ItemSpare.Spare.CONTROL_CARD.ordinal(), 2}},
    };

    // ---- orders ----------------------------------------------------------------------------------------------------------
    public static AssetData.Order raise(World w, ItemSpare.Spare sp, int qty, int supplier, BlockPos stores, String why, String by) {
        AssetData d = AssetData.get(w);
        AssetData.Order o = new AssetData.Order();
        o.id = d.nextOrder++;
        o.spare = sp.ordinal(); o.qty = Math.max(1, qty); o.supplier = Math.max(0, Math.min(3, supplier));
        o.ordered = w.getTotalWorldTime();
        o.due = o.supplier == 2 ? 0 : o.ordered + Math.round(LEAD[o.supplier] * AssetConfig.leadTimeScale);
        o.stores = stores == null ? 0 : stores.toLong();
        o.cost = price(sp) * o.qty * COST_K[o.supplier];
        o.why = why; o.by = by;
        if (o.supplier == 2) { TileDepotStores st = storesAt(w, o.stores); o.baseline = st == null ? 0 : st.count(sp); }
        d.orders.add(0, o);
        while (d.orders.size() > 120) { AssetData.Order old = null; for (int i = d.orders.size() - 1; i >= 0; i--) if (d.orders.get(i).state != 0) { old = d.orders.get(i); break; } if (old == null) break; d.orders.remove(old); }
        d.note(w, String.format(Locale.ROOT, "PO #%d %d x %s from %s (%s) - %s", o.id, o.qty, sp.label, AssetData.SUPPLIERS[o.supplier], eta(w, o), why));
        Recorder.event(w, "PROCUREMENT: PO #" + o.id + " " + o.qty + " x " + sp.label + " (" + AssetData.SUPPLIERS[o.supplier] + ")");
        d.markDirty();
        return o;
    }

    public static String eta(World w, AssetData.Order o) {
        if (o.state == 1) return "delivered";
        if (o.state == 2) return "cancelled";
        if (o.supplier == 2) return "when the factory delivers";
        long left = o.due - w.getTotalWorldTime();
        return left <= 0 ? "due now" : String.format(Locale.ROOT, "%.1f h", left / 1000.0);
    }

    static TileDepotStores storesAt(World w, long p) {
        if (p != 0 && w.isBlockLoaded(BlockPos.fromLong(p)) && w.getTileEntity(BlockPos.fromLong(p)) instanceof TileDepotStores s) return s;
        return null;
    }

    static List<TileDepotStores> allStores(World w) {
        List<TileDepotStores> o = new ArrayList<>();
        for (TileEntity t : w.loadedTileEntityList) if (t instanceof TileDepotStores s && !t.isInvalid()) o.add(s);
        return o;
    }

    /** once a second: deliveries; every 5 s: what the stores are short of */
    static void tick(World w, long now) {
        AssetData d = AssetData.get(w);
        for (AssetData.Order o : d.orders) {
            if (o.state != 0) continue;
            ItemSpare.Spare sp = ItemSpare.Spare.values()[Math.min(ItemSpare.Spare.values().length - 1, o.spare)];
            TileDepotStores st = storesAt(w, o.stores);
            if (o.supplier == 2) {                                             // works order: the factory's freight has to bring them
                if (st != null && st.count(sp) >= o.baseline + o.qty) deliver(w, d, o, sp, null);
                continue;
            }
            if (now < o.due) continue;
            if (st == null) { List<TileDepotStores> all = allStores(w); if (all.isEmpty()) continue; st = all.get(0); o.stores = st.getPos().toLong(); }
            deliver(w, d, o, sp, st);
        }
        if (now % 100 == 13 && AssetConfig.autoProcure) auto(w, d);
    }

    private static void deliver(World w, AssetData d, AssetData.Order o, ItemSpare.Spare sp, TileDepotStores st) {
        if (st != null) {
            int left = o.qty;
            for (int k = 0; k < 6 && left > 0; k++) {
                while (left > 0) {
                    ItemStack s = sp == ItemSpare.Spare.BOGIE ? ItemSpare.bogie(true, FleetData.get(w).serial(FleetData.Kind.BOGIE_A), 0)
                            : ItemSpare.stack(sp, Math.min(left, sp == ItemSpare.Spare.WHEELSET || sp == ItemSpare.Spare.TRACTION_MOTOR ? 1 : 16));
                    if (o.supplier == 3) { if (!s.hasTagCompound()) s.setTagCompound(new net.minecraft.nbt.NBTTagCompound()); s.getTagCompound().setString("grade", "A"); }
                    int n = s.getCount();
                    if (!Stores.put(st.storage(k), s)) break;
                    left -= n;
                }
            }
            if (left > 0) { o.qty = left; return; }                            // bins full: the rest waits on the dock
            st.controlsChanged();
        }
        o.state = 1;
        d.note(w, "PO #" + o.id + " DELIVERED: " + o.qty + " x " + sp.label + (st == null ? " (factory works order complete)" : " to the stores at " + st.getPos().getX() + "," + st.getPos().getZ()));
        Recorder.event(w, "PROCUREMENT: PO #" + o.id + " delivered (" + o.qty + " x " + sp.label + ")");
        AlarmManager.get(w).clear("short:" + o.spare);
        d.markDirty();
    }

    /** open orders for a spare (any stores) */
    static int onOrder(AssetData d, int spare) { int n = 0; for (AssetData.Order o : d.orders) if (o.state == 0 && o.spare == spare) n += o.qty; return n; }

    /** shortages right now: spare -> {qty short, why} */
    public static Map<ItemSpare.Spare, Object[]> shortages(World w) {
        Map<ItemSpare.Spare, Object[]> out = new LinkedHashMap<>();
        FleetData f = FleetData.get(w);
        for (FleetData.Job j : f.jobs) {
            if (j.state != 2 || j.depot == 0) continue;
            BlockPos office = BlockPos.fromLong(j.depot);
            for (ItemSpare.Spare sp : ItemSpare.Spare.values()) {
                int need = j.spares[sp.ordinal()];
                if (need <= 0) continue;
                int have = Stores.count(w, office, sp);
                if (have < need) add(out, sp, need - have, "job #" + j.id + " " + j.unitName);
            }
        }
        AssetData d = AssetData.get(w);
        int[] have = stock(w);
        for (AssetData.Defect df : d.defects) {
            if (df.stage != AssetData.ORDERED) continue;
            ItemSpare.Spare sp = Consequences.spareFor(d, df);
            if (have[sp.ordinal()] <= 0) add(out, sp, 1, "defect #" + df.id);
        }
        return out;
    }

    private static void add(Map<ItemSpare.Spare, Object[]> out, ItemSpare.Spare sp, int n, String why) {
        Object[] e = out.computeIfAbsent(sp, k -> new Object[]{0, ""});
        e[0] = (Integer) e[0] + n;
        e[1] = ((String) e[1]).isEmpty() ? why : e[1] + ", " + why;
    }

    /** every serviceable spare in every loaded stores */
    public static int[] stock(World w) {
        int[] n = new int[ItemSpare.Spare.values().length];
        for (TileDepotStores s : allStores(w)) for (ItemSpare.Spare sp : ItemSpare.Spare.values()) n[sp.ordinal()] += s.count(sp);
        return n;
    }

    /** the stores raise their own orders: shortages first, then two of each infrastructure spare */
    static void auto(World w, AssetData d) {
        List<TileDepotStores> all = allStores(w);
        if (all.isEmpty()) return;
        BlockPos home = all.get(0).getPos();
        AlarmManager am = AlarmManager.get(w);
        for (Map.Entry<ItemSpare.Spare, Object[]> e : shortages(w).entrySet()) {
            ItemSpare.Spare sp = e.getKey();
            int need = (Integer) e.getValue()[0];
            if (onOrder(d, sp.ordinal()) >= need) continue;
            raise(w, sp, need - onOrder(d, sp.ordinal()), 0, home, "SHORTAGE: " + e.getValue()[1], "stores (auto)");
            AssetTicker.standing(w, "short:" + sp.ordinal(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "SHORTAGE: " + need + " x " + sp.label + " for " + e.getValue()[1] + " - on order", home);
        }
        int[] have = stock(w);
        for (ItemSpare.Spare sp : ItemSpare.Spare.values()) {
            if (sp.rolling()) continue;
            int target = 2;
            if (have[sp.ordinal()] + onOrder(d, sp.ordinal()) < target) raise(w, sp, target - have[sp.ordinal()] - onOrder(d, sp.ordinal()), 0, home, "stock under the reorder level (" + target + ")", "stores (auto)");
        }
    }

    // ---- substitutions ----------------------------------------------------------------------------------------------------
    /** a job card short of parts: use substitutes in stock where the rules allow; returns what was swapped ("" none) */
    public static String substitute(World w, BlockPos at, int[] need) {
        if (!AssetConfig.substitutions) return "";
        StringBuilder b = new StringBuilder();
        for (int[][] rule : SUBS) {
            int sp = rule[0][0];
            if (sp >= need.length || need[sp] <= 0) continue;
            ItemSpare.Spare s = ItemSpare.Spare.values()[sp];
            int have = Stores.count(w, at, s);
            if (have >= need[sp]) continue;
            int missing = need[sp] - have;
            boolean ok = true;
            for (int i = 1; i < rule.length; i++) {
                if (rule[i][1] <= 0) { ok = false; break; }
                ItemSpare.Spare sub = ItemSpare.Spare.values()[rule[i][0]];
                if (Stores.count(w, at, sub) < need[rule[i][0]] + rule[i][1] * missing) { ok = false; break; }
            }
            if (!ok) continue;
            need[sp] -= missing;
            StringBuilder subs = new StringBuilder();
            for (int i = 1; i < rule.length; i++) {
                need[rule[i][0]] += rule[i][1] * missing;
                subs.append(subs.length() > 0 ? " + " : "").append(rule[i][1] * missing).append(" x ").append(ItemSpare.Spare.values()[rule[i][0]].label);
            }
            b.append(b.length() > 0 ? "; " : "").append(missing).append(" x ").append(s.label).append(" -> ").append(subs);
        }
        return b.toString();
    }

    /** take spares from the loaded stores nearest a place (an asset repair gang collects them); true if all were there */
    public static boolean takeNear(World w, BlockPos near, ItemSpare.Spare sp, int n) {
        List<TileDepotStores> all = allStores(w);
        int have = 0;
        for (TileDepotStores s : all) have += s.count(sp);
        if (have < n) return false;
        if (near != null) all.sort(Comparator.comparingDouble(s -> s.getPos().distanceSq(near)));
        int left = n;
        for (TileDepotStores s : all) { if (left <= 0) break; left -= s.take(sp, left); }
        return left <= 0;
    }
}
