package com.dogpound.railmap.asset;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.depot.Predict;
import com.dogpound.railmap.depot.Telemetry;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.Thermal;
import com.dogpound.railmap.server.WearData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The Control Center's ASSETS tab data (PacketRailState.assets), rows tagged:
 * K summary · A asset · D defect · F fleet health · T thermal · P purchase order · S stock / shortage · C certificate ·
 * L register log. And its buttons (PacketAssetOp) plus the repair flow the command shares.
 */
public final class AssetConsole {
    private AssetConsole() {}

    static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    public static void fill(World w, List<String[]> out) {
        AssetData d = AssetData.get(w);
        long now = w.getTotalWorldTime();
        int uncomm = 0, over = 0, open = 0, pos = 0;
        double cost = 0;
        for (AssetData.Asset a : d.all()) { if (a.comm != AssetData.COMMISSIONED) uncomm++; if (a.overdue(now)) over++; }
        for (AssetData.Defect df : d.defects) if (df.open()) open++;
        for (AssetData.Order o : d.orders) if (o.state == 0) { pos++; cost += o.cost; }
        out.add(new String[]{"K", String.valueOf(d.all().size()), String.valueOf(uncomm), String.valueOf(over), String.valueOf(open), String.valueOf(pos), String.valueOf(Math.round(cost))});
        // assets, worst first (the register can be big: the 220 that matter most)
        List<AssetData.Asset> as = new ArrayList<>(d.all());
        as.sort(Comparator.comparingInt((AssetData.Asset a) -> a.comm == AssetData.COMMISSIONED ? 1 : 0).thenComparingDouble(a -> a.cond));
        Map<String, Integer> openBy = new HashMap<>();
        for (AssetData.Defect df : d.defects) if (df.open()) openBy.merge(df.asset, 1, Integer::sum);
        for (int i = 0; i < Math.min(220, as.size()); i++) {
            AssetData.Asset a = as.get(i);
            BlockPos p = a.at();
            double left = a.daysLeft();
            out.add(new String[]{"A", a.id, a.kind.label, a.name, String.valueOf(Math.round(a.cond)), AssetTicker.BANDS[AssetTicker.band(a.cond)], String.valueOf(Math.round(a.lifeUsed() * 100)),
                    left < 0 ? "-" : left > 999 ? ">999" : f1(left), AssetData.COMM[a.comm], a.overdue(now) ? "1" : "0", f1((now - a.inspected) / 1000.0),
                    String.valueOf(openBy.getOrDefault(a.id, 0)), f1(a.use) + " " + a.kind.unit, p.getX() + "," + p.getY() + "," + p.getZ(), String.join(",", a.deps),
                    i >= 40 || a.history.isEmpty() ? "" : String.join("\n", a.history.subList(0, Math.min(6, a.history.size())))});
        }
        for (AssetData.Defect df : d.defects) {
            if (df.stage == AssetData.LATENT || (df.stage == AssetData.VERIFIED && now - df.changed > 48000)) continue;
            out.add(new String[]{"D", String.valueOf(df.id), df.asset, df.type, AssetData.STAGES[df.stage], df.stage == AssetData.LATENT ? "?" : f1(df.seen) + " ± " + f1(df.unc) + " " + df.unit,
                    f1(df.crit) + " " + df.unit, f1((now - df.found) / 1000.0), df.method + (df.by.isEmpty() ? "" : " / " + df.by), Consequences.spareFor(d, df).label,
                    df.seen >= df.crit ? "1" : "0", String.join("\n", df.trail)});
        }
        // fleet health (#5)
        FleetData f = FleetData.get(w);
        WearData wd = WearData.get(w);
        for (Map.Entry<UUID, FleetData.Unit> e : f.units().entrySet()) {
            FleetData.Unit u = e.getValue();
            if (!u.loco || u.name.isEmpty()) continue;
            double[] fp = Predict.first(u);
            StringBuilder tr = new StringBuilder();
            for (int c = 0; c < 4; c++) {
                double v = Telemetry.last(u, c), s = Telemetry.slope(u, c);
                tr.append(c > 0 ? " | " : "").append(Telemetry.CH[c]).append(' ').append(Double.isNaN(v) ? "-" : String.valueOf(Math.round(v))).append(Double.isNaN(s) ? "" : String.format(Locale.ROOT, " (%+.1f/h)", s));
            }
            WearData.Record r = wd.peek(e.getKey());
            out.add(new String[]{"F", e.getKey().toString(), u.name, String.join(" · ", u.pcodes), fp == null ? "-" : WearData.Part.values()[(int) fp[0]].label + " " + f1(fp[1]) + " km",
                    Telemetry.rulHours(u) < 0 ? "-" : f1(Telemetry.rulHours(u)) + " h", tr.toString(), String.valueOf(u.tele.size()), new String[]{"AS BUILT", "NOT ACCEPTED", "TEST RUN", "ACCEPTED", "FAILED"}[Math.max(0, Math.min(4, u.rebuild))],
                    r == null ? "" : String.valueOf(Math.round(r.worst())), f1(u.hours)});
        }
        // thermal (#22)
        GridData g = GridData.get(w);
        for (Map.Entry<Long, GridData.Node> e : g.nodes().entrySet()) {
            GridData.Node n = e.getValue();
            if (n.kind != GridKind.TRANSFORMER && n.kind != GridKind.TRACTION && n.kind != GridKind.MOTOR) continue;
            BlockPos p = BlockPos.fromLong(e.getKey());
            boolean tx = n.kind != GridKind.MOTOR;
            out.add(new String[]{"T", n.kind.label, p.getX() + "," + p.getY() + "," + p.getZ(), tx ? f1(Thermal.hotSpotC(n)) : "-", tx ? f1(Thermal.topOilC(n)) : "-",
                    tx ? String.format(Locale.ROOT, "%.2f", Thermal.ageing(Thermal.hotSpotC(n))) : "-", tx ? f1(Thermal.agedHours(n)) : "-", String.valueOf(Math.round(Thermal.replica(n) * 100)),
                    tx ? String.valueOf(Math.round(Thermal.fouling(n) * 100)) : "-", tx ? (n.fanFail ? "FAILED" : n.cool == 0 ? "OFF" : n.cool == 2 ? "ON" : "AUTO") : "-", f1(n.ambient), String.valueOf(Math.round(n.insul * 100))});
        }
        // procurement (#16)
        for (int i = 0; i < Math.min(40, d.orders.size()); i++) {
            AssetData.Order o = d.orders.get(i);
            ItemSpare.Spare sp = ItemSpare.Spare.values()[Math.min(ItemSpare.Spare.values().length - 1, o.spare)];
            out.add(new String[]{"P", String.valueOf(o.id), sp.label, String.valueOf(o.qty), AssetData.SUPPLIERS[o.supplier], AssetData.PO_STATES[o.state], Procurement.eta(w, o), String.valueOf(Math.round(o.cost)), o.why, o.by});
        }
        int[] stock = Procurement.stock(w);
        Map<ItemSpare.Spare, Object[]> sh = Procurement.shortages(w);
        for (ItemSpare.Spare sp : ItemSpare.Spare.values()) {
            if (sp == ItemSpare.Spare.BOGIE_BAD) continue;
            Object[] s = sh.get(sp);
            out.add(new String[]{"S", String.valueOf(sp.ordinal()), sp.label, String.valueOf(stock[sp.ordinal()]), String.valueOf(Procurement.onOrder(d, sp.ordinal())), s == null ? "0" : String.valueOf(s[0]),
                    s == null ? "" : (String) s[1], String.valueOf(Procurement.price(sp)), sp.rolling() ? "R" : "I"});
        }
        for (int i = 0; i < Math.min(20, d.certs.size()); i++) {
            AssetData.Cert c = d.certs.get(i);
            out.add(new String[]{"C", String.valueOf(c.id), c.kind, c.scope, c.pass ? "PASS" : "FAIL", c.by, f1((now - c.at) / 1000.0), String.join("\n", c.lines)});
        }
        for (int i = 0; i < Math.min(30, d.log.size()); i++) out.add(new String[]{"L", d.log.get(i)});
        com.dogpound.railmap.infra.InfraConsole.track(w, out);               // batch 3: rail flaws by UIC class (#41), test-train runs, grinders / welders (#42)
    }

    // ---- the repair flow ---------------------------------------------------------------------------------------------------
    public static String orderRepair(World w, int id, String who) {
        AssetData d = AssetData.get(w);
        AssetData.Defect df = d.defect(id);
        if (df == null) return "No defect #" + id;
        if (df.stage == AssetData.LATENT) return "Defect #" + id + " has not been found yet";
        if (df.stage >= AssetData.ORDERED) return "Defect #" + id + " is already " + AssetData.STAGES[df.stage];
        df.step(w, AssetData.ORDERED, "repair ordered by " + who + (df.stage == AssetData.DISCOVERED ? " (unconfirmed)" : ""));
        d.note(w, "defect #" + id + " REPAIR ORDERED by " + who);
        d.markDirty();
        return "Defect #" + id + " repair ORDERED - it needs 1 x " + Consequences.spareFor(d, df).label;
    }

    /** the gang does the repair: takes its spare from the loaded stores nearest the asset */
    public static String repair(World w, int id, String who) {
        AssetData d = AssetData.get(w);
        AssetData.Defect df = d.defect(id);
        if (df == null) return "No defect #" + id;
        if (df.stage == AssetData.DISCOVERED || df.stage == AssetData.CONFIRMED) orderRepair(w, id, who);
        if (df.stage != AssetData.ORDERED) return "Defect #" + id + " is " + AssetData.STAGES[df.stage];
        ItemSpare.Spare sp = Consequences.spareFor(d, df);
        BlockPos near = Consequences.posOf(df);
        if (!Procurement.takeNear(w, near, sp, 1)) return "WAITING PARTS: no " + sp.label + " in any loaded depot stores - " + (Procurement.onOrder(d, sp.ordinal()) > 0 ? "one is on order" : "raise a purchase order");
        AssetData.Asset a = d.byId(df.asset);
        String done = a == null ? "wheelset changed" : Consequences.repair(w, a, df);
        if (a == null && df.asset.startsWith("VEH:")) {
            try { UUID u = UUID.fromString(df.asset.substring(4)); WearData.Record r = WearData.get(w).peek(u); if (r != null) { r.wear[WearData.Part.WHEELS.ordinal()] = 0; WearData.get(w).markDirty(); } } catch (IllegalArgumentException ignored) { }
        }
        df.size = 0;
        df.step(w, AssetData.REPAIRED, done + " (" + who + ") - re-test to verify");
        if (a != null && sp != ItemSpare.Spare.STRUCTURE_KIT && AssetConfig.cores) Reman.toStores(w, near == null ? BlockPos.ORIGIN : near, ItemSpare.core(sp, Math.max(5, a.cond * 0.6f), a.id));
        AlarmManager.get(w).clear("defect:" + id);
        d.note(w, "defect #" + id + " REPAIRED: " + done);
        Recorder.event(w, "ASSET: defect #" + id + " repaired - " + done);
        d.markDirty();
        return "Defect #" + id + " REPAIRED: " + done + " - re-test it with the " + (a == null ? Ndt.Tool.AXLE.label : a.kind.tool.label) + " to verify";
    }

    /** a console button: returns what to tell the player */
    public static String op(World w, int op, String arg, String who) {
        AssetData d = AssetData.get(w);
        try {
            return switch (op) {
                case 1 -> orderRepair(w, Integer.parseInt(arg), who);
                case 2 -> repair(w, Integer.parseInt(arg), who);
                case 3 -> {
                    String[] p = arg.split(":");
                    ItemSpare.Spare sp = ItemSpare.Spare.values()[Integer.parseInt(p[0])];
                    int qty = p.length > 1 ? Integer.parseInt(p[1]) : 1, sup = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                    List<com.dogpound.railmap.depot.TileDepotStores> st = Procurement.allStores(w);
                    AssetData.Order o = Procurement.raise(w, sp, qty, sup, st.isEmpty() ? null : st.get(0).getPos(), "ordered from the control center", who);
                    yield "PO #" + o.id + ": " + qty + " x " + sp.label + " from " + AssetData.SUPPLIERS[sup] + " (" + Procurement.eta(w, o) + ")";
                }
                case 4 -> {
                    AssetData.Order o = d.order(Integer.parseInt(arg));
                    if (o == null || o.state != 0) yield "No open PO #" + arg;
                    o.state = 2; d.note(w, "PO #" + o.id + " CANCELLED by " + who);
                    yield "PO #" + o.id + " cancelled";
                }
                case 5 -> {
                    AssetData.Order o = d.order(Integer.parseInt(arg));
                    if (o == null || o.state != 0 || o.supplier == 1) yield "Can't expedite PO #" + arg;
                    long fast = w.getTotalWorldTime() + Math.round(Procurement.LEAD[1] * AssetConfig.leadTimeScale);
                    o.due = o.supplier == 2 ? fast : Math.min(o.due, fast);
                    o.cost *= 2.5 / Procurement.COST_K[o.supplier];
                    o.supplier = 1;
                    d.note(w, "PO #" + o.id + " EXPEDITED by " + who);
                    yield "PO #" + o.id + " expedited (express, " + Procurement.eta(w, o) + ")";
                }
                default -> null;
            };
        } catch (RuntimeException e) { return "Bad request: " + e.getMessage(); }
    }
}
