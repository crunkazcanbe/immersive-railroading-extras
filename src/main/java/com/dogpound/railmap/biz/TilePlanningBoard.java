package com.dogpound.railmap.biz;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * The Town Hall Planning Board (her "next 48" #13, #18, #38). Placing it founds a town here (its town hall); the board
 * follows any town on the map with TOWN < >. It shows the town's population, tier and growth, the 14-day history, its
 * rail access, jobs, goods and the railway noise there, why it is growing or shrinking, the stations whose catchment
 * it fills (and the share each gets), and the public-service tenders and contracts of the lines serving it. Environment:
 * the network's CO2 today, diesel vs electric kg per train-km and what regenerative braking saved. Investments: a
 * company director buys a NOISE BARRIER for this town, or TREES / SOLAR / FILTERS / WATER recycling for the company.
 * PSO: pick the subsidy with PRICE and BID on the open tender for a line here. RENAME takes the name off a held name
 * tag. The plan chest and the planning-applications tray are storage; the planning table shows models placed on it.
 */
public class TilePlanningBoard extends BizDesk {
    public static final String[] STORE_IDS = {"plans", "apps"};
    public static final String[] STORE_NAMES = {"Plan Chest", "Planning Applications Tray"};
    public static final int[] STORE_SIZES = {27, 9};
    private static final double[][] SPOTS = {{4.0, 9.08, 11.6}, {6.6, 9.08, 11.6}, {9.4, 9.08, 11.6}, {12.0, 9.08, 11.6}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    private int town = -1;
    private long bidPx = 5_000;

    public TilePlanningBoard() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, 4); }

    @Override
    public void placedBy(EntityPlayer p) {
        super.placedBy(p);
        if (world != null && !world.isRemote) { Towns.Town t = biz().towns.found(world, pos, "", true, 500); town = t.id; }
    }

    @Override
    public void dropAll() {
        super.dropAll();
        if (world != null && !world.isRemote) biz().towns.remove(pos.toLong());   // the town hall is gone: the town record goes too
    }

    private Towns.Town town() {
        Towns tw = biz().towns;
        Towns.Town t = town >= 0 ? tw.byId(town) : null;
        if (t == null) t = tw.at(pos.toLong());
        if (t == null && !tw.towns.isEmpty()) t = tw.towns.values().iterator().next();
        return t;
    }

    private List<RailwayData.Line> linesHere(Towns.Town t) {
        List<RailwayData.Line> out = new ArrayList<>();
        if (t == null) return out;
        double r2 = (double) BizConfig.townCatchment * BizConfig.townCatchment;
        for (RailwayData.Line l : RailwayData.get(world).lines().values())
            for (long s : l.stations) if (BlockPos.fromLong(s).distanceSq(t.at()) < r2) { out.add(l); break; }
        return out;
    }

    private Pso.Tender tender(Towns.Town t) {
        for (RailwayData.Line l : linesHere(t)) for (Pso.Tender x : biz().pso.tenders) if (x.state.equals("OPEN") && x.line.equals(l.name)) return x;
        return null;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        BizData d = biz();
        Towns.Town t = town();
        switch (id) {
            case "tprev": case "tnext": {
                List<Towns.Town> all = new ArrayList<>(d.towns.towns.values());
                if (all.isEmpty()) return "§7No towns yet";
                int i = Math.max(0, all.indexOf(t));
                town = all.get(Math.floorMod(i + (id.equals("tnext") ? 1 : -1), all.size())).id;
                return "§7Town: §f" + town().name;
            }
            case "pxup": case "pxdn": bidPx = Math.max(100, bidPx + (id.equals("pxup") ? 500 : -500)); return "§7PSO bid: " + Coins.fmt(bidPx) + " a day";
            case "report": {
                if (who == null || t == null) return "";
                who.sendMessage(new TextComponentString("§6🏛 " + t.name + " §7- " + t.tier().toLowerCase(Locale.ROOT) + ", " + Math.round(t.pop) + " people (" + String.format(Locale.ROOT, "%+.2f%%", t.growthPct) + " a day)"));
                who.sendMessage(new TextComponentString(String.format(Locale.ROOT, "§7 access %.0f · jobs %.0f%% (%.0f posts) · goods %.0f · noise %.0f dB (%d barriers) · %d stations", t.access, t.jobs, t.jobsAvail, t.goods, t.noiseDb, t.barriers, t.stations)));
                who.sendMessage(new TextComponentString("§7 " + t.why));
                return "";
            }
            default: break;
        }
        if (!(who instanceof EntityPlayerMP p)) return null;
        switch (id) {
            case "rename": {
                ItemStack h = p.getHeldItemMainhand();
                if (t == null) return "";
                if (h.isEmpty() || !h.hasDisplayName()) return "§7Hold a named item (a name tag) to rename the town";
                String old = t.name;
                t.name = h.getDisplayName();
                return "§a" + old + " is now " + t.name;
            }
            case "barrier": case "trees": case "solar": case "dpf": case "water": {
                Company m = mine(p, true);
                if (m == null) return "§cEnvironmental works are paid for by a company's owner or director";
                Environment.Measure ms = id.equals("barrier") ? Environment.Measure.NOISE_BARRIER : id.equals("trees") ? Environment.Measure.TREES
                        : id.equals("solar") ? Environment.Measure.SOLAR_DEPOT : id.equals("dpf") ? Environment.Measure.DPF : Environment.Measure.WATER;
                return d.env.invest(world, d, m, ms, ms == Environment.Measure.NOISE_BARRIER ? t : null);
            }
            case "bid": {
                Company m = mine(p, true);
                if (m == null) return "§cA company's owner or director bids for public service";
                Pso.Tender x = tender(t);
                if (x == null) return "§7No open public-service tender for the lines serving " + (t == null ? "this town" : t.name);
                return d.pso.bid(world, m, x.id, bidPx);
            }
            default: return null;
        }
    }

    @Override
    protected void screens() {
        BizData d = biz();
        Towns.Town t = town();
        t("town", t == null ? "NO TOWN" : cut(t.name.toUpperCase(Locale.ROOT), 18) + " - " + t.tier());
        t("pop", t == null ? "" : Math.round(t.pop) + " PEOPLE " + String.format(Locale.ROOT, "%+.2f%%/DAY", t.growthPct));
        t("hist", t == null ? "" : "14D " + spark(t.hist));
        t("acc", t == null ? "" : String.format(Locale.ROOT, "ACCESS %.0f  %d STATIONS", t.access, t.stations));
        t("jobs", t == null ? "" : String.format(Locale.ROOT, "JOBS %.0f%%  %.0f POSTS", t.jobs, t.jobsAvail));
        t("goods", t == null ? "" : String.format(Locale.ROOT, "GOODS %.0f", t.goods));
        t("noise", t == null ? "" : String.format(Locale.ROOT, "NOISE %.0f DB  %d BARRIERS", t.noiseDb, t.barriers));
        t("why", t == null ? "" : cut(t.why.toUpperCase(Locale.ROOT), 36));
        // the catchment: stations near the town, and their share of its people
        List<String> st = new ArrayList<>();
        if (t != null) {
            PassengerData pd = PassengerData.get(world);
            for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) {
                double dd = Math.sqrt(BlockPos.fromLong(e.getKey()).distanceSq(t.at()));
                if (dd >= BizConfig.townCatchment) continue;
                PassengerData.Stn s = pd.stn(e.getKey());
                st.add(cut(e.getValue().toUpperCase(Locale.ROOT), 14) + String.format(Locale.ROOT, " %.0fM %s", dd, s == null ? "" : "POP " + s.pop + " SAT " + Math.round(s.sat)));
            }
        }
        for (int i = 0; i < 4; i++) t("st" + (i + 1), i < st.size() ? st.get(i) : i == 0 ? "NO STATION IN REACH" : "");
        List<String> ps = new ArrayList<>();
        for (RailwayData.Line l : linesHere(t)) {
            Pso.Contract k = d.pso.live(l.name);
            if (k != null) ps.add("P" + k.id + " " + cut(l.name.toUpperCase(Locale.ROOT), 10) + " " + Coins.fmt(k.subsidyDay) + " " + (k.lastCheck.equals("MET") ? "MET" : k.lastCheck.isEmpty() ? "NEW" : "MISSED"));
            for (Pso.Tender x : d.pso.tenders) if (x.state.equals("OPEN") && x.line.equals(l.name)) ps.add("T" + x.id + " TENDER " + cut(l.name.toUpperCase(Locale.ROOT), 10) + " " + x.bids.size() + " BIDS");
        }
        for (int i = 0; i < 3; i++) t("pso" + (i + 1), i < ps.size() ? ps.get(i) : i == 0 ? "NO PUBLIC SERVICE HERE" : "");
        t("bidpx", "BID " + Coins.fmt(bidPx) + "/DAY");
        double co2 = 0, y = 0;
        for (Environment.Acct a : d.env.accts.values()) { co2 += a.co2Kg; y += a.co2Yesterday; }
        double[] in = d.env.intensity();
        t("env1", String.format(Locale.ROOT, "CO2 %.0f KG TODAY  %.0f YDAY", co2, y));
        t("env2", (in[0] < 0 ? "DSL -" : String.format(Locale.ROOT, "DSL %.1f", in[0])) + (in[1] < 0 ? "  ELEC -" : String.format(Locale.ROOT, "  ELEC %.1f", in[1])) + String.format(Locale.ROOT, " KG/KM REGEN -%.0fKG", in[2]));
        p("pw", term.powered());
        p("grow", t != null && t.growthPct > 0.05);
        p("shrink", t != null && t.growthPct < -0.05);
        p("noisy", t != null && t.noiseDb > 55);
        p("tend", tender(t) != null);
        p("psoon", t != null && d.pso.servesNear(world, t.at()));
        v("accg", t == null ? 0 : (int) Math.round(t.access));
        v("noiseg", t == null ? 0 : (int) Math.round(t.noiseDb));
    }

    @Override public String wiredName() { return "Town Hall Planning Board"; }
    @Override public String gearState() { Towns.Town t = town(); return t == null ? "no town" : t.name + " · " + Math.round(t.pop) + " people · " + t.tier().toLowerCase(Locale.ROOT); }
    @Override protected void save(NBTTagCompound t) { t.setInteger("town", town); t.setLong("px", bidPx); }
    @Override protected void load(NBTTagCompound t) { town = t.hasKey("town") ? t.getInteger("town") : -1; bidPx = t.hasKey("px") ? t.getLong("px") : 5_000; }
}
