package com.dogpound.railmap.freight6;

import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

import static com.dogpound.railmap.stn.StnConsole.*;

/**
 * The Control Center's STN tab pages for batch 6 freight + fun: Grain, Tank farm, Warehouse, Lockers, Holidays.
 * Ops arrive without "f6.".
 */
public final class F6Console {
    private F6Console() {}

    static String at(BlockPos p) { return p.getX() + "," + p.getZ(); }

    public static void fill(World w, List<String[]> out) {
        // ---- grain
        page(out, "Grain", "Grain elevators: every bin's grain, tonnage, moisture and temperature; legs, dryers and loadouts");
        cols(out, "Bin / machine:0", "Grain:22", "Tonnes:40", "Fill:50", "Moisture:58", "Temp:68", "State:77");
        btn(out, "AERATE", "f6.aerate", true);
        for (TileGrainSilo b : StnUtil.loaded(w, TileGrainSilo.class))
            out.add(new String[]{"ROW", "S" + b.getPos().toLong(), "Bin " + at(b.getPos()), b.qty() <= 0 ? ".empty" : Grain.name(b.kind()), Grain.t(b.qty()), b.pct() + "%",
                    (b.moist() > Grain.SAFE ? "~" : "") + String.format(Locale.ROOT, "%.1f %%", b.moist()), (b.temp() >= 40 ? "!" : "") + String.format(Locale.ROOT, "%.0f C", b.temp()),
                    !b.powered() ? "!NO 12 V" : b.temp() >= 55 ? "!SPOILING" : b.temp() >= 40 ? "!HOT SPOT" : b.controls().get("k_aer") == 1 ? "+AERATING" : "ok"});
        for (TileGrainLeg l : StnUtil.loaded(w, TileGrainLeg.class)) out.add(new String[]{"ROW", "", "Leg " + at(l.getPos()), "", "", "", "", "", l.running() ? "+RUNNING" : l.gearState()});
        for (TileGrainDryer d : StnUtil.loaded(w, TileGrainDryer.class)) out.add(new String[]{"ROW", "", "Dryer " + at(d.getPos()), d.qty() > 0 ? "garner" : ".empty", Grain.t(d.qty()), "", String.format(Locale.ROOT, "%.1f %%", d.moist()), "", d.burning() ? "~FIRING" : "off"});
        for (TileGrainLoadout l : StnUtil.loaded(w, TileGrainLoadout.class)) out.add(new String[]{"ROW", "", "Loadout " + at(l.getPos()), "", "", "", "", "", l.gearState()});
        txt(out, "Grain over 15 % moisture heats in the bin - dry it (Grain Dryer) or aerate it. 1 item = 50 kg.");

        // ---- tank farm
        page(out, "Tank farm", "Oil storage tanks, loading racks and the fire and gas panels: levels, transfers, gas % LEL, fire, deluge, ESD");
        cols(out, "Tank / rack:0", "Product:22", "Litres:42", "Level:55", "Valves:63", "State:76");
        btn(out, "ESD", "f6.esd", false);
        btn(out, "RESET", "f6.esdreset", false);
        for (TileOilTank t : StnUtil.loaded(w, TileOilTank.class))
            out.add(new String[]{"ROW", "", "Tank " + at(t.getPos()), t.product(), String.valueOf(t.tank().getFluidAmount()), (t.highHigh() ? "!" : "") + t.pct() + "%",
                    (t.inletOpen() ? "IN " : ".in ") + (t.outletOpen() ? "OUT" : ".out"), t.fire() ? "!FIRE" : t.highHigh() ? "!HIGH-HIGH" : "ok"});
        for (TileOilRack r : StnUtil.loaded(w, TileOilRack.class))
            out.add(new String[]{"ROW", "", "Rack " + at(r.getPos()), "", "", "", "", (r.vapour() >= 10 ? "!" : r.running() ? "~" : "") + r.gearState()});
        for (TileFireGas g : StnUtil.loaded(w, TileFireGas.class))
            out.add(new String[]{"ROW", "", "F+G panel " + at(g.getPos()), "", "", "", "", (g.fireZones() > 0 || g.esdActive() ? "!" : g.lel() >= 10 ? "~" : "+") + g.gearState()});

        // ---- warehouse
        page(out, "Warehouse", "Rail-served warehouses: the stock in the racks (inventory), dock doors and the forklift jobs");
        cols(out, "Item / dock:0", "Qty:40", "Slots:50", "Where:60");
        btn(out, "STOP JOBS", "f6.whstop", false);
        for (TileWarehouseOffice o : StnUtil.loaded(w, TileWarehouseOffice.class)) {
            out.add(new String[]{"ROW", "", "+Office " + at(o.getPos()), "", "", o.gearState()});
            for (Map.Entry<String, int[]> e : Warehouse.stock(w, o.getPos()).entrySet()) out.add(new String[]{"ROW", "", "  " + e.getKey(), String.valueOf(e.getValue()[0]), String.valueOf(e.getValue()[1]), "racks near " + at(o.getPos())});
            List<TileWarehouseDock> ds = Warehouse.docks(w, o.getPos());
            for (int i = 0; i < ds.size(); i++) out.add(new String[]{"ROW", "", "  Dock " + (i + 1), "", "", (ds.get(i).secured() ? "+" : ".") + ds.get(i).gearState()});
            for (int i = 0; i < o.workers().size(); i++) { TileWarehouseOffice.Worker k = o.workers().get(i); out.add(new String[]{"ROW", "", "  Forklift " + (i + 1), k.fork.isEmpty() ? "" : String.valueOf(k.fork.getCount()), "", TileWarehouseOffice.PHASE[k.phase]}); }
        }

        // ---- lockers
        page(out, "Lockers", "Station parcel lockers: who has a parcel waiting where, for how long");
        cols(out, "Lockers:0", "Box:26", "For:33", "From:50", "Waiting:78");
        long now = w.getTotalWorldTime();
        for (TileParcelLockers l : StnUtil.loaded(w, TileParcelLockers.class)) {
            out.add(new String[]{"ROW", "", "+" + (l.station().isEmpty() ? "Lockers " + at(l.getPos()) : l.station()), l.used() + "/" + TileParcelLockers.N, "", "", l.powered() ? "" : "!NO 12 V"});
            for (int i = 0; i < TileParcelLockers.N; i++) {
                TileParcelLockers.Box b = l.box(i);
                if (b == null) continue;
                long days = (now - b.since) / 24000;
                out.add(new String[]{"ROW", "", "", String.valueOf(i + 1), b.name, b.from, (days >= F6Config.lockerHoldDays ? "!UNCOLLECTED " : "") + days + " d"});
            }
        }
        txt(out, "Codes go to the addressee by chat (/irextras lockers codes). Sorting offices use the lockers for anyone without a RealmCoin mailbox.");

        // ---- holidays
        page(out, "Holidays", "Holiday trains and festive stations: today's season, dressed cars, lighting controllers and displays");
        cols(out, "What:0", "Where:30", "State:48");
        txt(out, Holiday.status(w));
        HolidayData d = HolidayData.get(w);
        try {
            for (cam72cam.immersiverailroading.entity.EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                int k = d.kit(s.getUUID());
                if (k == 0) continue;
                cam72cam.mod.math.Vec3d v = s.getPosition();
                out.add(new String[]{"ROW", "", (k == HolidayData.XMAS ? "+Christmas train " : "~Ghost train ") + com.dogpound.railmap.steam.Stock.name(s), Math.round(v.x) + "," + Math.round(v.z), com.dogpound.railmap.steam.Stock.standing(s) ? "standing" : "running"});
            }
        } catch (RuntimeException | LinkageError ignored) { }
        for (TileHolidayController c : StnUtil.loaded(w, TileHolidayController.class)) out.add(new String[]{"ROW", "", "Lighting controller", at(c.getPos()), c.gearState()});
        for (TileFestive f : StnUtil.loaded(w, TileFestive.class)) out.add(new String[]{"ROW", "", f.wiredName(), at(f.getPos()), (f.controls().get("lit") == 1 ? "+" : ".") + f.gearState()});
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        switch (op) {
            case "aerate" -> {
                if (!arg.startsWith("S")) return "§7Select a bin";
                BlockPos bp = BlockPos.fromLong(Long.parseLong(arg.substring(1)));
                if (!(w.getTileEntity(bp) instanceof TileGrainSilo b)) return "§7Gone";
                b.controls().set("k_aer", b.controls().get("k_aer") == 1 ? 0 : 1);
                b.controlsChanged();
                return "§aBin " + at(bp) + ": aeration " + (b.controls().get("k_aer") == 1 ? "ON" : "off");
            }
            case "esd" -> {
                int n = 0;
                for (TileFireGas g : StnUtil.loaded(w, TileFireGas.class)) { com.dogpound.railmap.controls.IControlled.operate(g, "esd", p); n++; }
                return n == 0 ? "§7No fire and gas panel loaded" : "§cESD on " + n + " tank farm(s)";
            }
            case "esdreset" -> {
                StringBuilder r = new StringBuilder();
                for (TileFireGas g : StnUtil.loaded(w, TileFireGas.class)) r.append(com.dogpound.railmap.controls.IControlled.operate(g, "reset", p)).append(" ");
                return r.length() == 0 ? "§7No fire and gas panel loaded" : r.toString().trim();
            }
            case "whstop" -> {
                int n = 0;
                for (TileWarehouseOffice o : StnUtil.loaded(w, TileWarehouseOffice.class)) if (o.job() != 0) { com.dogpound.railmap.controls.IControlled.operate(o, "stop", p); n++; }
                return "§e" + n + " warehouse job(s) stopped";
            }
            default -> { return null; }
        }
    }
}
