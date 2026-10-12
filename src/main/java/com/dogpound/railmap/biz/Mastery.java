package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Achievements and operational mastery (her "next 48" #37). Cosmetic only: nothing here changes how a railway runs.
 * The layer counts what each company really does (boxes delivered, construction stages, deals, green investments,
 * research, punctual days, connections made...). Reaching a mark awards a TROPHY: it shows on the console's dashboard
 * and the Stock Exchange board, and every member online gets an engraved trophy plaque to stand on a shelf (the Company
 * HQ's trophy shelf shows it). Mastery LEVEL grows with experience points from everything counted.
 */
public final class Mastery implements BizData.Part {
    /** name, counter, threshold, what it is for */
    public static final String[][] AWARDS = {
            {"FIRST BOX", "boxes", "1", "delivered a container inland"}, {"PORT MASTER", "boxes", "25", "25 containers delivered"},
            {"BUILDER", "stages", "7", "a whole construction project, survey to opening"}, {"CIVIL ENGINEER", "stages", "28", "four projects built"},
            {"DEALMAKER", "deals", "1", "completed a merger, acquisition or franchise"}, {"GREEN LINE", "green", "3", "three environmental investments"},
            {"SCIENTIST", "research", "3", "three technologies researched"}, {"PUNCTUAL", "punctual", "3", "three days with every line 95% punctual"},
            {"CONNECTOR", "connect", "1", "a day with 90%+ connections made (50+ changes)"}, {"TOWN BUILDER", "city", "1", "a town on its lines grew into a city"},
            {"PUBLIC SERVANT", "pso", "7", "seven days meeting a public-service contract"}, {"CLEAN AUDIT", "audit", "1", "a 100/100 regulator audit"}};

    public final Map<Integer, Map<String, Integer>> counts = new HashMap<>();
    public final Map<Integer, LinkedHashSet<String>> trophies = new HashMap<>();
    public final LinkedList<String> feed = new LinkedList<>();

    @Override public String key() { return "mastery"; }

    public int get(int company, String k) { Map<String, Integer> m = counts.get(company); return m == null ? 0 : m.getOrDefault(k, 0); }

    public int xp(int company) { int s = 0; Map<String, Integer> m = counts.get(company); if (m != null) for (int v : m.values()) s += v * 10; return s + trophies.getOrDefault(company, new LinkedHashSet<>()).size() * 100; }
    public int level(int company) { return 1 + (int) Math.sqrt(xp(company) / 50.0); }

    /** count something a company (or a player's company) did */
    public void count(World w, Company c, EntityPlayer p, String k, int n) {
        if (c == null && p != null) c = CompanyData.get(w).of(p.getUniqueID());
        if (c == null) return;
        counts.computeIfAbsent(c.id, x -> new HashMap<>()).merge(k, n, Integer::sum);
        check(w, c);
    }

    /** a one-off award by name (null = nothing) */
    public void trophy(World w, Company c, String name) {
        if (c == null || name == null) return;
        counts.computeIfAbsent(c.id, x -> new HashMap<>()).merge("audit", name.equals("CLEAN AUDIT") ? 1 : 0, Integer::sum);
        check(w, c);
    }

    private void check(World w, Company c) {
        LinkedHashSet<String> have = trophies.computeIfAbsent(c.id, x -> new LinkedHashSet<>());
        for (String[] a : AWARDS) {
            if (have.contains(a[0]) || get(c.id, a[1]) < Integer.parseInt(a[2])) continue;
            have.add(a[0]);
            feed.addFirst(com.dogpound.railmap.depot.DepotClock.stamp(w) + " " + c.mark + " won " + a[0] + " - " + a[3]);
            while (feed.size() > 40) feed.removeLast();
            Recorder.event(w, "🏆 " + c.name + " won the " + a[0] + " trophy - " + a[3]);
            com.dogpound.railmap.api.QuestEvents.fireFor(w, c.members.keySet(), null, "trophy", "name", a[0]);
            for (UUID u : c.members.keySet()) {
                EntityPlayerMP p = CompanyData.online(w, u);
                if (p == null) continue;
                p.sendMessage(new TextComponentString("§6🏆 " + c.name + " won §e" + a[0] + "§6 - " + a[3]));
                ItemStack t = new ItemStack(Items.SIGN);                       // an engraved plaque: cosmetic, worth no more than a sign
                t.setStackDisplayName("§6🏆 " + a[0]);
                NBTTagCompound disp = t.getOrCreateSubCompound("display");
                NBTTagList lore = new NBTTagList();
                lore.appendTag(new NBTTagString("§7" + c.name + " - " + a[3]));
                lore.appendTag(new NBTTagString("§8" + com.dogpound.railmap.depot.DepotClock.stamp(w)));
                disp.setTag("Lore", lore);
                if (!p.inventory.addItemStackToInventory(t)) p.dropItem(t, false);
            }
        }
    }

    @Override
    public void dayEnd(World w, BizData d, long day) {
        CompanyData cd = CompanyData.get(w);
        PassengerData pd = PassengerData.get(w);
        for (Company c : cd.all()) {
            boolean all = !c.lines.isEmpty();
            for (String l : c.lines) { PassengerData.LineStat s = pd.line(l); if (s == null || s.punct < 95 || s.arrivalsDay == 0) all = false; }
            if (all) count(w, c, null, "punctual", 1);
            for (Towns.Town t : d.towns.towns.values())
                if (t.pop >= 20000 && t.hist.length > 1 && t.hist[1] < 20000 && d.pso.companyServes(w, c, t.at())) count(w, c, null, "city", 1);
        }
        double made = 0, missed = 0;
        for (Journeys.Ix x : d.journeys.interchanges().values()) { made += x.made; missed += x.missed; }
        if (made + missed >= 50 && made / (made + missed) >= 0.9) for (Company c : cd.all()) if (!c.lines.isEmpty()) count(w, c, null, "connect", 1);
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        counts.clear(); trophies.clear(); feed.clear();
        NBTTagCompound c = t.getCompoundTag("c");
        for (String k : c.getKeySet()) {
            Map<String, Integer> m = new HashMap<>();
            NBTTagCompound x = c.getCompoundTag(k);
            for (String n : x.getKeySet()) m.put(n, x.getInteger(n));
            try { counts.put(Integer.parseInt(k), m); } catch (NumberFormatException ignored) { }
        }
        NBTTagCompound tr = t.getCompoundTag("t");
        for (String k : tr.getKeySet()) {
            LinkedHashSet<String> s = new LinkedHashSet<>();
            NBTTagList l = tr.getTagList(k, 8);
            for (int i = 0; i < l.tagCount(); i++) s.add(l.getStringTagAt(i));
            try { trophies.put(Integer.parseInt(k), s); } catch (NumberFormatException ignored) { }
        }
        NBTTagList f = t.getTagList("feed", 8);
        for (int i = 0; i < f.tagCount(); i++) feed.add(f.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound c = new NBTTagCompound();
        for (Map.Entry<Integer, Map<String, Integer>> e : counts.entrySet()) {
            NBTTagCompound x = new NBTTagCompound();
            for (Map.Entry<String, Integer> n : e.getValue().entrySet()) x.setInteger(n.getKey(), n.getValue());
            c.setTag(String.valueOf(e.getKey()), x);
        }
        t.setTag("c", c);
        NBTTagCompound tr = new NBTTagCompound();
        for (Map.Entry<Integer, LinkedHashSet<String>> e : trophies.entrySet()) {
            NBTTagList l = new NBTTagList();
            for (String s : e.getValue()) l.appendTag(new NBTTagString(s));
            tr.setTag(String.valueOf(e.getKey()), l);
        }
        t.setTag("t", tr);
        NBTTagList f = new NBTTagList();
        for (String s : feed) f.appendTag(new NBTTagString(s));
        t.setTag("feed", f);
        return t;
    }
}
