package com.dogpound.railmap.stn;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.planning.SplitJoin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * Formation Planner (ChatGPT batch 3 #26): demand-led train lengths per line. PREV / NEXT pick the line; twelve dials
 * show its demand profile through the day (two hours each, as % of a present train's seats); the screens show the
 * load now, the present formation and the recommended cars NOW / PEAK / OFF-PEAK at the TARGET LOAD factor, capped by
 * MAX CARS (- / +, the line's shortest platform). OVER / UNDER / OK lamps judge the trains running now. APPLY splits
 * the surplus cars off over-formed trains standing at the line's ends (they are secured, stabled where they stand);
 * the AUTO key does that by itself (config formationAutoSplit). PRINT gives the day's formation plan. 12 V + mains.
 */
public class TileFormationPlanner extends StnPanel {
    private int lineI;
    private final Map<String, Integer> maxCars = new HashMap<>();
    private final Map<UUID, Long> splitDone = new HashMap<>();
    private String line = "";
    private int recNow, recPeak, recOff, splits;

    @Override public String wiredName() { return "Formation planner"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : !mainsOk() ? "NO MAINS" : line.isEmpty() ? "no lines" : line + " · now " + recNow + " cars, peak " + recPeak; }

    int lf() { return new int[]{70, 85, 100, 120}[Math.floorMod(ctl.get("s_lf"), 4)]; }
    int max(String l) { return maxCars.getOrDefault(l, 8); }

    private List<String> lines() { List<String> l = new ArrayList<>(RailwayData.get(world).lines().keySet()); Collections.sort(l); return l; }

    @Override
    protected String act(String id, EntityPlayer who) {
        List<String> ls = lines();
        switch (id) {
            case "prev", "next" -> { if (ls.isEmpty()) return "§7No lines"; lineI = Math.floorMod(lineI + (id.equals("next") ? 1 : -1), ls.size()); return "§e" + ls.get(lineI); }
            case "maxm", "maxp" -> { if (line.isEmpty()) return "§7No line"; int m = Math.max(1, Math.min(24, max(line) + (id.equals("maxp") ? 1 : -1))); maxCars.put(line, m); markDirty(); return "§eMax " + m + " cars on " + line; }
            case "apply" -> { int n = splitSurplus(who(who)); return n == 0 ? "§7No over-formed train standing at an end of " + line : "§aSplit surplus cars off " + n + " train(s)"; }
            case "print" -> {
                if (line.isEmpty()) return "§7No line";
                FormationDemand.LineNow n = FormationDemand.now(world, line);
                double[] p = StnData.get(world).demand(line);
                List<String> out = new ArrayList<>();
                out.add("FORMATION PLAN " + line);
                out.add("target load " + lf() + "%, max " + max(line) + " cars, " + Math.round(n.perCar) + " seats/car");
                for (int h = 0; h < 24; h++) out.add(String.format(Locale.ROOT, "%02d:00 %4.0f pax  %d cars", h, p[h], FormationDemand.recommend(p[h], n.perCar, lf(), max(line))));
                StnUtil.print(world, pos, controlFacing(), "Formation plan " + line, "Formation planner", out);
                return "§aPrinted the formation plan for " + line;
            }
            default -> { return null; }
        }
    }

    /** split surplus rear cars off over-formed trains of this line standing at one of its ends */
    private int splitSurplus(String who) {
        RailwayData rd = RailwayData.get(world);
        RailwayData.Line l = rd.line(line);
        if (l == null || l.stations.isEmpty()) return 0;
        long a0 = l.stations.get(0), a1 = l.stations.get(l.stations.size() - 1);
        PassengerData pd = PassengerData.get(world);
        int n = 0;
        long now = world.getTotalWorldTime();
        for (RailwayData.AutoTrain a : new ArrayList<>(rd.trains().values())) {
            if (!line.equals(a.line)) continue;
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r == null || !r.dwelling || r.dwellStation != a0 && r.dwellStation != a1) continue;
            Long done = splitDone.get(a.loco);
            if (done != null && now - done < 6000) continue;
            int surplus = Math.max(0, pd.cars(a.loco) - 1) - recNow;
            if (surplus < 1) continue;
            Locomotive lead = Autopilot.find(world, a.loco);
            if (lead == null) continue;
            String r2 = SplitJoin.split(world, lead, surplus, "", who);
            splitDone.put(a.loco, now);
            Recorder.event(world, "Formation planner (" + line + "): " + r2.replaceAll("§.", ""));
            if (r2.startsWith("§a")) { n++; splits++; com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "formation_split", "line", line, "cars", surplus); }
        }
        return n;
    }

    @Override
    protected void work() {
        if (age % 40 != 13) return;
        List<String> ls = lines();
        line = ls.isEmpty() ? "" : ls.get(Math.floorMod(lineI, ls.size()));
        put("pwr", powered());
        if (line.isEmpty()) { text("line", "NO LINES"); return; }
        FormationDemand.LineNow n = FormationDemand.now(world, line);
        double[] p = StnData.get(world).demand(line);
        int h = PassengerData.hour(world), m = max(line);
        double peak = 0, off = 0;
        for (int i = 0; i < 24; i++) { if (PassengerData.peak(i)) peak = Math.max(peak, p[i]); else if (!PassengerData.night(i)) off = Math.max(off, p[i]); }
        recNow = FormationDemand.recommend(Math.max(p[h], Math.max(n.aboardMax, n.waitingPerTrain)), n.perCar, lf(), m);
        recPeak = FormationDemand.recommend(peak, n.perCar, lf(), m);
        recOff = FormationDemand.recommend(off, n.perCar, lf(), m);
        double cap = Math.max(1, n.seats);
        for (int i = 0; i < 12; i++) put("h" + i, (int) Math.min(150, Math.max(p[i * 2], p[i * 2 + 1]) * 100 / cap));
        boolean over = n.trains > 0 && n.cars > recNow, under = n.trains > 0 && n.cars < recNow;
        text("line", StnUtil.up(StnUtil.cut(line, 16)) + "  " + n.trains + " TRAINS");
        text("now", String.format(Locale.ROOT, "LOAD %d%% %d PAX", Math.round(n.loadPct), Math.round(n.aboardMax)));
        text("form", "CARS " + n.cars + "  SEATS " + n.seats);
        text("rec", "NOW " + recNow + " PEAK " + recPeak + " OFF " + recOff);
        text("max", "MAX " + m + " LF " + lf() + "%");
        text("adv", n.trains == 0 ? "NO TRAINS ON LINE" : over ? "DETACH " + (n.cars - recNow) + " CARS" : under ? "ATTACH " + (recNow - n.cars) + " CARS" : "FORMATION OK");
        put("over", over); put("under", under); put("okl", n.trains > 0 && !over && !under); put("autol", ctl.get("k_auto") == 1 && StnConfig.formationAutoSplit);
        put("nsplit", splits);
        if (ctl.get("k_auto") == 1 && StnConfig.formationAutoSplit && live() && over) splitSurplus("formation planner AUTO");
        splitDone.values().removeIf(t -> world.getTotalWorldTime() - t > 24000);
    }

    public String summary() { return line + "|" + recNow + "|" + recPeak + "|" + recOff + "|" + splits; }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setInteger("lineI", lineI); t.setInteger("splits", splits);
        NBTTagCompound m = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : maxCars.entrySet()) m.setInteger(e.getKey(), e.getValue());
        t.setTag("max", m);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        lineI = t.getInteger("lineI"); splits = t.getInteger("splits");
        maxCars.clear();
        NBTTagCompound m = t.getCompoundTag("max");
        for (String k : m.getKeySet()) maxCars.put(k, m.getInteger(k));
    }
}
