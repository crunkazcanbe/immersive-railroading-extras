package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.*;

/**
 * The optional railway technology research tree (her "next 48" #32). OFF by default (BizConfig.researchTree): then
 * nothing is locked and the world plays exactly as before. Switched on, a company researches each technology - after
 * its prerequisites - by any mix of four routes, each giving research points:
 * <ul>
 *   <li><b>investment</b>: money into the programme (1 point per $10);</li>
 *   <li><b>experience</b>: what the company actually does, counted daily (train-km, boxes delivered, stages built...);</li>
 *   <li><b>experiments</b>: a proving run - a real condition in the world checked when you press EXPERIMENT (25 points);</li>
 *   <li><b>components</b>: delivering the technology's key parts (held in hand when you give them).</li>
 * </ul>
 * Companies that existed when the tree was switched on keep every technology they already use (grandfathered).
 * Unlocked technologies also give their benefit (cheaper insurance, faster civils, more regen credit...).
 */
public final class Research implements BizData.Part {
    public enum Tech {
        SURVEYING("Surveying", 40, null, "minecraft:compass", "construction surveys run twice as fast"),
        HEAVY_CIVILS("Heavy civil engineering", 120, SURVEYING, "minecraft:piston", "construction crews +25% productive"),
        ELECTRIFICATION("Electrification", 150, HEAVY_CIVILS, "irextras:grid_cable", "electrical fit-out +50% faster"),
        CAPACITY_PLANNING("Capacity planning", 60, null, "minecraft:clock", "may bid in capacity auctions"),
        CONTAINERISATION("Containerisation", 80, null, "irextras:shipping_container", "port export loads"),
        PORT_LOGISTICS("Port logistics", 140, CONTAINERISATION, "minecraft:boat", "STS crane moves +30% faster"),
        GREEN_TECH("Green technology", 100, null, "minecraft:sapling", "solar farms, filters and water recycling"),
        SAFETY_MGMT("Safety management systems", 90, null, "minecraft:book", "insurance premiums -20%, audits +10"),
        CORPORATE_FINANCE("Corporate finance", 110, null, "minecraft:gold_ingot", "may issue bonds and take project loans"),
        CORPORATE_LAW("Corporate law", 130, CORPORATE_FINANCE, "minecraft:writable_book", "mergers, acquisitions and franchises");
        public final String label, part, benefit;
        public final int points;
        public final Tech needs;
        Tech(String label, int points, Tech needs, String part, String benefit) { this.label = label; this.points = points; this.needs = needs; this.part = part; this.benefit = benefit; }
    }

    public static final class Prog {
        public final int[] points = new int[Tech.values().length];
        public final boolean[] done = new boolean[Tech.values().length];
        public boolean grandfathered;
        public int current = -1;
    }

    public final Map<Integer, Prog> progs = new HashMap<>();
    public boolean wasOn;

    @Override public String key() { return "research"; }

    public Prog prog(int company) { return progs.computeIfAbsent(company, k -> new Prog()); }

    /** is this locked for this company? (never, with the tree off or for a grandfathered company) */
    public static boolean locked(World w, Company c, Tech t) {
        if (!BizConfig.researchTree || c == null) return false;
        Prog p = BizData.get(w).research.prog(c.id);
        return !p.grandfathered && !p.done[t.ordinal()];
    }

    public static boolean has(World w, Company c, Tech t) { return c == null ? !BizConfig.researchTree : !locked(w, c, t); }

    public static String why(Tech t) { return "§cNeeds research: " + t.label + " (/irextras biz research)"; }

    private String add(World w, BizData d, Company c, Tech t, int pts, String how) {
        Prog p = prog(c.id);
        if (p.done[t.ordinal()]) return "§7" + t.label + " is already researched";
        if (t.needs != null && !p.done[t.needs.ordinal()] && !p.grandfathered) return "§cResearch " + t.needs.label + " first";
        p.points[t.ordinal()] += pts;
        if (p.points[t.ordinal()] >= t.points) {
            p.done[t.ordinal()] = true;
            d.note(w, c.mark + " completed research: " + t.label + " - " + t.benefit);
            d.mastery.count(w, c, null, "research", 1);
            return "§a🔬 " + t.label + " RESEARCHED - " + t.benefit;
        }
        return "§b" + t.label + ": +" + pts + " (" + how + ") - " + p.points[t.ordinal()] + "/" + t.points;
    }

    public String invest(World w, BizData d, Company c, Tech t, long cents) {
        if (!BizData.pay(w, c, Company.Kind.RESEARCH, cents, "Research programme: " + t.label)) return "§c" + c.name + " can't fund " + Coins.fmt(cents);
        return add(w, d, c, t, (int) (cents / 1000), "investment " + Coins.fmt(cents));
    }

    public String component(World w, BizData d, Company c, Tech t, EntityPlayer p) {
        ItemStack h = p.getHeldItemMainhand();
        if (h.isEmpty() || h.getItem().getRegistryName() == null || !h.getItem().getRegistryName().toString().equals(t.part)) return "§7" + t.label + " takes " + t.part + " (hold some)";
        int n = h.getCount();
        h.setCount(0);
        return add(w, d, c, t, n * 3, n + " components");
    }

    /** a proving experiment: a real condition the company has to have achieved */
    public String experiment(World w, BizData d, Company c, Tech t) {
        Environment.Acct a = d.env.acct(c.id);
        boolean ok; String need;
        switch (t) {
            case SURVEYING: case HEAVY_CIVILS: case ELECTRIFICATION: ok = d.mastery.get(c.id, "stages") >= 1 + t.ordinal(); need = "complete construction stages"; break;
            case CAPACITY_PLANNING: ok = !d.capacity.allocs.isEmpty(); need = "any company holding a capacity slot (watch the market)"; break;
            case CONTAINERISATION: case PORT_LOGISTICS: ok = d.mastery.get(c.id, "boxes") >= (t == Tech.CONTAINERISATION ? 1 : 5); need = "deliver boxes inland"; break;
            case GREEN_TECH: ok = a.regenKwh >= 5; need = "an electric train regenerating 5 kWh today"; break;
            case SAFETY_MGMT: ok = d.regulator.lastScore(c.id) >= 70; need = "an audit score of 70+"; break;
            default: ok = c.reputation >= 60; need = "reputation 60+"; break;
        }
        if (!ok) return "§eExperiment for " + t.label + " inconclusive - needs " + need;
        return add(w, d, c, t, 25, "experiment");
    }

    @Override
    public void daily(World w, BizData d, long day) {
        if (!BizConfig.researchTree) { wasOn = false; return; }
        CompanyData cd = CompanyData.get(w);
        if (!wasOn) {                                                            // switched on: existing companies keep what they use
            for (Company c : cd.all()) prog(c.id).grandfathered = true;
            wasOn = true;
            d.note(w, "Research tree switched on - " + cd.all().size() + " existing companies keep every technology");
        }
        for (Company c : cd.all()) {                                             // experience
            Prog p = prog(c.id);
            if (p.current < 0 || p.done[p.current]) continue;
            Environment.Acct a = d.env.acct(c.id);
            int pts = (int) Math.min(20, (a.dieselKm + a.elecKm) * 2 + c.trains.size());
            if (pts > 0) add(w, d, c, Tech.values()[p.current], pts, "experience");
        }
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        progs.clear();
        NBTTagCompound c = t.getCompoundTag("p");
        for (String k : c.getKeySet()) {
            NBTTagCompound x = c.getCompoundTag(k);
            Prog p = new Prog();
            int[] pts = x.getIntArray("pts");
            System.arraycopy(pts, 0, p.points, 0, Math.min(pts.length, p.points.length));
            byte[] dn = x.getByteArray("done");
            for (int i = 0; i < Math.min(dn.length, p.done.length); i++) p.done[i] = dn[i] != 0;
            p.grandfathered = x.getBoolean("gf"); p.current = x.hasKey("cur") ? x.getInteger("cur") : -1;
            try { progs.put(Integer.parseInt(k), p); } catch (NumberFormatException ignored) { }
        }
        wasOn = t.getBoolean("on");
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound c = new NBTTagCompound();
        for (Map.Entry<Integer, Prog> e : progs.entrySet()) {
            Prog p = e.getValue();
            NBTTagCompound x = new NBTTagCompound();
            x.setIntArray("pts", p.points);
            byte[] dn = new byte[p.done.length];
            for (int i = 0; i < dn.length; i++) dn[i] = (byte) (p.done[i] ? 1 : 0);
            x.setByteArray("done", dn);
            x.setBoolean("gf", p.grandfathered); x.setInteger("cur", p.current);
            c.setTag(String.valueOf(e.getKey()), x);
        }
        t.setTag("p", c);
        t.setBoolean("on", wasOn);
        return t;
    }
}
