package com.dogpound.railmap.station;

import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Station Facilities Panel (her ChatGPT list #81 + #85): the station's accessibility and facilities audit on one
 * board. The rating dial and grade, lamps for step-free access, lifts, accessible toilet, help point and gap warning,
 * the tactile paving count, every facility and whether it is in service, and yesterday's running cost and income.
 * AUDIT re-checks now; the LEVEL ACCESS key declares a station with no steps between street and platform (then it is
 * step-free without a lift). PRINT gives the audit as a book. 12 V.
 */
public class TileFacilitiesPanel extends DepotTile {
    private int row;

    @Override public String wiredName() { return "Station facilities panel"; }
    @Override public String gearState() { StationFacilities.Audit a = audit(); return a == null ? "no station" : a.name + " access " + a.grade() + " (" + a.rating + ")"; }
    public boolean levelAccess() { return ctl.get("k_level") == 1; }

    StationFacilities.Audit audit() {
        Long st = StationFacilities.stationOf(world, pos);
        if (st == null) return null;
        StationFacilities.Audit a = StationFacilities.audit(world, (long) st);
        return a;
    }

    List<String> lines(StationFacilities.Audit a) {
        List<String> o = new ArrayList<>();
        if (a == null) { o.add("NOT AT A NAMED STATION (48 BLOCKS)"); return o; }
        o.add(String.format(Locale.ROOT, "%s ACCESS %s %d/100", a.name.toUpperCase(Locale.ROOT), a.grade(), a.rating));
        o.add(String.format(Locale.ROOT, "STEP-FREE %s LIFTS %d/%d TACTILE %d", a.stepFree ? "YES" : "NO", a.liftsOk, a.lifts, a.tactile));
        o.add(String.format(Locale.ROOT, "TOILETS %d/%d HELP %d/%d GAP %d", a.toiletsOk, a.toilets, a.helpOk, a.help, a.gap));
        o.add(String.format(Locale.ROOT, "FACILITIES %d/%d IN SERVICE, %d PTS", a.working, a.total, a.points));
        o.add("COST/DAY " + Coins.fmt(a.cost) + " YDAY IN " + Coins.fmt(a.incomeYesterday));
        for (String r : a.rows) { String[] p = r.split("\\|"); o.add(String.format(Locale.ROOT, "%s - %s %s", p[0].toUpperCase(Locale.ROOT), p[1], Coins.fmt(Long.parseLong(p[2])))); }
        return o;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "audit" -> { Long st = StationFacilities.stationOf(world, pos); if (st == null) return "§cNot at a named station"; StationFacilities.audit(world, (long) st); return "§aAudit done"; }
            case "prev" -> { row = Math.max(0, row - 1); return ""; }
            case "next" -> { row++; return ""; }
            case "print" -> {
                if (who == null) return "§7Collect it at the panel";
                StationFacilities.Audit a = audit();
                who.inventory.addItemStackToInventory(com.dogpound.railmap.depot.Diagnostics.book("Station audit", "Facilities panel", lines(a)));
                return "§aAudit printed";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        StationFacilities.Audit a = audit();
        List<String> l = lines(a);
        row = Math.max(0, Math.min(row, Math.max(0, l.size() - 5)));
        for (int i = 0; i < 5; i++) text("m" + (i + 1), i == 0 ? (l.isEmpty() ? "" : l.get(0)) : row + i < l.size() ? cut(l.get(row + i), 40) : "");
        put("pwr", powered());
        put("rating", a == null ? 0 : a.rating);
        text("grade", a == null ? "-" : a.grade());
        put("stepf", a != null && a.stepFree); put("liftoos", a != null && a.lifts > a.liftsOk);
        put("wc", a != null && a.toiletsOk > 0); put("help", a != null && a.helpOk > 0); put("gap", a != null && a.gap > 0);
        put("tact", a == null ? 0 : a.tactile);
        text("cost", a == null ? "" : Coins.fmt(a.cost));
        text("inc", a == null ? "" : Coins.fmt(a.incomeYesterday));
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setInteger("row", row); }
    @Override protected void loadExtra(NBTTagCompound t) { row = t.getInteger("row"); }
}
