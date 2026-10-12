package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Locomotive diagnostic downloads (her ChatGPT list #58): the depot data link reads a locomotive's event recorder -
 * operating hours, engine starts, battery / oil / coolant, the live temperatures and derate, coupler overloads, the
 * latched fault codes and its fault / trip log - and saves it with the fleet records for later analysis (Control
 * Center DEPOT > Downloads, /irextras depot downloads), or prints it as a book.
 */
public final class Diagnostics {
    private Diagnostics() {}

    public static FleetData.Download download(World w, Locomotive l) {
        FleetData f = FleetData.get(w);
        FleetData.Unit u = f.unit(l.getUUID());
        SimData.Car c = SimData.get(w).of(l.getUUID());
        WearData.Record r = WearData.get(w).of(l.getUUID());
        List<String> o = new ArrayList<>();
        o.add(String.format(Locale.ROOT, "%s  %s", u.name, DepotClock.stamp(w)));
        o.add(String.format(Locale.ROOT, "ODO %.1f km  HRS %.1f  EXAM %.1f km / %.1f h ago", u.km, u.hours, u.km - u.examKm, u.hours - u.examHours));
        if (l instanceof LocomotiveDiesel d) {
            o.add(String.format(Locale.ROOT, "STARTS %d  BATT %d%%  OIL %d%%  COOLANT %d%%  FUEL %d mB", c.starts, Math.round(c.battery * 100), Math.round(c.oil * 100),
                    Math.round(c.coolant * 100), d.getLiquidAmount()));
            o.add(String.format(Locale.ROOT, "ENGINE %.0f C  RPM %.0f  OIL %.0f kPa  TM %s  DERATE %d%%%s", d.getEngineTemperature(), c.rpm, c.oilKpa,
                    Double.isNaN(c.tmC) ? "-" : Math.round(c.tmC) + " C", Math.round(d.prideDerate * 100), c.derateWhy.isEmpty() ? "" : " (" + c.derateWhy + ")"));
        }
        o.add(String.format(Locale.ROOT, "COUPLERS F %d%% R %d%%  FLAT %d mm  OVERLOAD %s  PEAK %.0f/%.0f kN", Math.round(c.coupler[0] * 100), Math.round(c.coupler[1] * 100),
                Math.round(c.flatMm), c.overload ? "YES" : "no", c.peakT / 1000, c.peakC / 1000));
        StringBuilder wear = new StringBuilder("WEAR");
        for (WearData.Part p : WearData.Part.values())
            wear.append(' ').append(p.label.toUpperCase(Locale.ROOT)).append(' ').append(Math.round(r.wear[p.ordinal()])).append("% ").append(Condition.NAMES[Condition.level(r.wear[p.ordinal()])].charAt(0));
        o.add(wear.toString());
        double[] first = Predict.first(u);
        if (first != null) o.add(String.format(Locale.ROOT, "PREDICTED: %s fails in ~%.1f km", WearData.Part.values()[(int) first[0]].label, first[1]));
        o.add("ACTIVE FAULTS: " + (c.faults.isEmpty() ? "none" : String.join(", ", c.faults)));
        o.add("FAULT / TRIP LOG (" + c.faultLog.size() + "):");
        for (String s : c.faultLog) o.add("  " + s);
        o.add("FAULTS SINCE EXAM: " + (u.faults - u.examFaults) + "  TOTAL: " + u.faults);
        FleetData.Download d = f.addDownload(w, u.name, o);
        u.note(w, "diagnostics downloaded (#" + d.id + ")");
        return d;
    }

    /** a written book of these lines (a printout) */
    public static ItemStack book(String title, String author, List<String> lines) {
        ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", title.length() > 30 ? title.substring(0, 30) : title);
        t.setString("author", author);
        NBTTagList pages = new NBTTagList();
        StringBuilder page = new StringBuilder();
        int n = 0;
        for (String s : lines) {
            if (n >= 12 || page.length() + s.length() > 240) { pages.appendTag(new NBTTagString("{\"text\":" + quote(page.toString()) + "}")); page.setLength(0); n = 0; }
            page.append(s).append('\n');
            n++;
        }
        if (page.length() > 0) pages.appendTag(new NBTTagString("{\"text\":" + quote(page.toString()) + "}"));
        t.setTag("pages", pages);
        b.setTagCompound(t);
        return b;
    }

    private static String quote(String s) { return new com.google.gson.JsonPrimitive(s).toString(); }
}
