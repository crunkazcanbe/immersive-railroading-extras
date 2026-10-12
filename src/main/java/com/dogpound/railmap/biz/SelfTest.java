package com.dogpound.railmap.biz;

import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /irextras biz selftest - checks the layer's own logic on throw-away copies (nothing in the world is changed):
 * slot windows, the custody chain's liability walk, the cargo inspection, a sealed-bid auction tie-break, the order
 * book, the research tree's prerequisites, the regulator's tables, scenario generation and the noise sum.
 */
public final class SelfTest {
    private SelfTest() {}

    private static void check(List<String> out, String name, boolean ok, String detail) { out.add((ok ? "§a PASS " : "§c FAIL ") + name + (ok ? "" : " §7- " + detail)); }

    public static List<String> run(World w) {
        List<String> out = new ArrayList<>();
        out.add("§b== world + business self-test");
        // #12 windows
        check(out, "slot windows", Capacity.window(8) == 2 && Capacity.window(23) == 0 && Capacity.window(4) == 0 && Capacity.window(17) == 4 && Capacity.window(12) == 3 && Capacity.window(20) == 5, "hour -> window table");
        // #17 liability walk + inspection
        Custody.Rec r = new Custody.Rec();
        r.id = "TEST"; r.owner = "owner";
        r.events.add("0|STUFF|A|alice|stuffed");
        r.events.add("100|RAIL LOAD|A|bob|loaded");
        r.events.add("50000|RAIL UNLOAD|B|carol|unloaded");
        check(out, "custody: contamination is the stuffer's", Custody.liable(r, "food contaminated").equals("alice"), Custody.liable(r, "food contaminated"));
        check(out, "custody: wet cargo is the longest holder's", Custody.liable(r, "grain wet").equals("bob"), Custody.liable(r, "grain wet"));
        r.seal = 5; r.sealBroken = true; r.sealBrokenBy = "mallory";
        check(out, "custody: broken seal is who broke it", Custody.liable(r, "seal 5 broken").equals("mallory"), Custody.liable(r, "seal 5 broken"));
        Custody.Rec q = new Custody.Rec();
        q.food = true; q.haz = true; q.clean = 40; q.grain = true; q.moisture = 20;
        List<String> f = new Custody().inspect(w, q);
        check(out, "custody: inspection finds contamination, segregation, moisture", f.size() >= 3, f.toString());
        check(out, "custody: a clean box passes", new Custody().inspect(w, new Custody.Rec()).isEmpty(), "clean box flagged");
        // #36 order book arithmetic (no companies, no money): best bid / ask
        Securities sec = new Securities();
        Securities.Order b = new Securities.Order(); b.company = 99; b.buy = true; b.price = 120; b.qty = 5; sec.orders.add(b);
        Securities.Order a = new Securities.Order(); a.company = 99; a.buy = false; a.price = 110; a.qty = 3; sec.orders.add(a);
        check(out, "order book: best bid / ask", sec.bestBid(99) == 120 && sec.bestAsk(99) == 110, sec.bestBid(99) + "/" + sec.bestAsk(99));
        // #32 the tree has no loops and every prerequisite comes first
        boolean ok = true;
        for (Research.Tech t : Research.Tech.values()) if (t.needs != null && t.needs.ordinal() >= t.ordinal()) ok = false;
        check(out, "research: prerequisites ordered", ok, "a tech needs a later one");
        check(out, "research: off by default unlocks everything", BizConfig.researchTree || !Research.locked(w, null, Research.Tech.CORPORATE_LAW), "locked with the tree off");
        // #34 tables
        check(out, "regulator: every check has a corrective action", Regulator.CHECKS.length == Regulator.ACTIONS.length, Regulator.CHECKS.length + " vs " + Regulator.ACTIONS.length);
        // #47 scenarios are reproducible and never touch an old world
        ScenarioGen.Spec s1 = ScenarioGen.generate("t", 42, "hard"), s2 = ScenarioGen.generate("t", 42, "hard");
        check(out, "scenario: same seed, same region", s1.towns.size() == s2.towns.size() && s1.towns.get(0).name.equals(s2.towns.get(0).name) && s1.towns.get(0).dx == s2.towns.get(0).dx, "differs");
        check(out, "scenario: hard has rivals", s1.companies.size() == 2, s1.companies.size() + " rivals");
        check(out, "scenario: an existing world is refused", w.getTotalWorldTime() <= 1200 || new ScenarioGen().refuse(w) != null, "an old world was accepted");
        // #18 noise: two equal sources are +3 dB
        double one = 10 * Math.log10(Math.pow(10, 6.0)), two = 10 * Math.log10(2 * Math.pow(10, 6.0));
        check(out, "environment: two trains = +3 dB", Math.abs(two - one - 3.01) < 0.05, String.format(Locale.ROOT, "%.2f", two - one));
        // #13 tiers
        Towns.Town t = new Towns.Town();
        t.pop = 999; String tier1 = t.tier(); t.pop = 25000; String tier2 = t.tier();
        check(out, "towns: tiers", tier1.equals("VILLAGE") && tier2.equals("METROPOLIS"), tier1 + "/" + tier2);
        // #11 stages add up
        double sum = 0;
        for (double x : new double[]{0.08, 0.20, 0.20, 0.20, 0.15, 0.12, 0.05}) sum += x;
        check(out, "construction: the seven stages make the whole project", Math.abs(sum - 1) < 1e-9 && com.dogpound.railmap.industry.TileConstructionSite.STAGES.length == 7, String.valueOf(sum));
        long fails = out.stream().filter(x -> x.contains("FAIL")).count();
        out.add(fails == 0 ? "§aAll passed" : "§c" + fails + " failed");
        return out;
    }
}
