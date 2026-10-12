package com.dogpound.railmap.biz;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.server.StationData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.FileWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generated railway scenarios (her "next 48" #47). GENERATE writes a scenario file - config/irextras_scenarios/
 * <name>.json - from a seed and a difficulty: a region of towns (names, sizes, places around spawn), rival railway
 * companies with their starting capital, the county's first public-service tenders and the goals to reach. You can
 * read and edit the file. APPLY sets a world up from it, and only a BRAND-NEW world: under a minute old, with no
 * stations, lines or companies, and never set up before. An existing world is refused - it is never modified.
 * Setting scenarioOnNewWorld applies it automatically to each new world. Progress toward the goals is checked daily.
 */
public final class ScenarioGen implements BizData.Part {
    public static final class Spec {
        public String name = "", difficulty = "normal";
        public long seed;
        public List<TownSpec> towns = new ArrayList<>();
        public List<CoSpec> companies = new ArrayList<>();
        public List<GoalSpec> goals = new ArrayList<>();
        public int tenders = 2;
    }
    public static final class TownSpec { public String name = ""; public int dx, dz, pop; }
    public static final class CoSpec { public String name = ""; public long cash; }
    public static final class GoalSpec { public String kind = "", text = ""; public int target, byDay; }

    public String applied = "";
    public long appliedAt;
    public final List<GoalSpec> goals = new ArrayList<>();
    public final Map<String, Integer> progress = new LinkedHashMap<>();

    @Override public String key() { return "scenario"; }

    private static final String[] PRE = {"Ash", "Bram", "Cole", "Dun", "Elm", "Fen", "Glen", "Hart", "Ivy", "Kirk", "Lyn", "Mar", "Nor", "Oak", "Pen", "Rye", "Stan", "Thorn", "Wex", "York"};
    private static final String[] SUF = {"ford", "ton", "bury", "wick", "field", "ham", "mouth", "bridge", "stead", "port", "dale", "worth"};

    public static File dir() { File d = new File(Loader.instance().getConfigDir(), "irextras_scenarios"); d.mkdirs(); return d; }

    /** a scenario from a seed: towns on a loose ring around spawn, rivals, goals by difficulty */
    public static Spec generate(String name, long seed, String difficulty) {
        Random r = new Random(seed);
        Spec s = new Spec();
        s.name = name; s.seed = seed; s.difficulty = difficulty;
        int n = 4 + r.nextInt(4), hard = difficulty.equals("hard") ? 2 : difficulty.equals("easy") ? 0 : 1;
        Set<String> used = new HashSet<>();
        for (int i = 0; i < n; i++) {
            TownSpec t = new TownSpec();
            do { t.name = PRE[r.nextInt(PRE.length)] + SUF[r.nextInt(SUF.length)]; } while (!used.add(t.name));
            double ang = Math.PI * 2 * i / n + r.nextDouble() * 0.5, dist = 250 + r.nextInt(500);
            t.dx = (int) (Math.cos(ang) * dist); t.dz = (int) (Math.sin(ang) * dist);
            t.pop = i == 0 ? 3000 + r.nextInt(4000) : 200 + r.nextInt(1800);
            s.towns.add(t);
        }
        for (int i = 0; i < hard; i++) { CoSpec c = new CoSpec(); c.name = s.towns.get(1 + i).name + " & District Railway"; c.cash = 500_000L + r.nextInt(500_000); s.companies.add(c); }
        s.goals.add(goal("CONNECT", "Give " + (2 + hard) + " towns good rail access", 2 + hard, 20));
        s.goals.add(goal("POPULATION", "Grow the region to " + (8000 + hard * 4000) + " people", 8000 + hard * 4000, 40));
        s.goals.add(goal("BOXES", "Deliver " + (5 + hard * 5) + " containers inland", 5 + hard * 5, 30));
        s.goals.add(goal("PUNCTUAL", "Run 3 fully punctual days", 3, 30));
        s.tenders = 1 + hard;
        return s;
    }

    private static GoalSpec goal(String k, String t, int target, int by) { GoalSpec g = new GoalSpec(); g.kind = k; g.text = t; g.target = target; g.byDay = by; return g; }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static File write(Spec s) throws java.io.IOException {
        File f = new File(dir(), s.name.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
        try (FileWriter out = new FileWriter(f)) { GSON.toJson(s, out); }
        return f;
    }

    public static Spec read(String name) {
        File f = new File(dir(), name.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
        if (!f.isFile()) return null;
        try (Reader in = new java.io.InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8)) { return GSON.fromJson(in, Spec.class); }
        catch (Exception e) { RailMap.LOG.warn("[IR Extras] scenario {} unreadable: {}", name, e.toString()); return null; }
    }

    /** why this world can't take a scenario (null = it is brand new and may) */
    public String refuse(World w) {
        if (!applied.isEmpty()) return "This world was already set up from scenario \"" + applied + "\"";
        if (w.getTotalWorldTime() > 1200) return "This is an existing world (" + w.getTotalWorldTime() / 20 + " s old) - scenarios only set up brand-new worlds and never modify existing ones";
        if (!StationData.get(w).names().isEmpty() || !RailwayData.get(w).lines().isEmpty() || !CompanyData.get(w).all().isEmpty()) return "This world already has a railway - it is never modified";
        return null;
    }

    public String apply(World w, BizData d, Spec s, EntityPlayer owner) {
        String no = refuse(w);
        if (no != null) return "§c" + no;
        BlockPos spawn = w.getSpawnPoint();
        for (TownSpec t : s.towns) {
            BlockPos at = w.getTopSolidOrLiquidBlock(spawn.add(t.dx, 0, t.dz));
            d.towns.found(w, at, t.name, true, t.pop);
        }
        if (owner != null) for (CoSpec c : s.companies) {
            Company co = CompanyData.get(w).found(w, owner, c.name);              // a rival, held in trust by whoever set the world up: sell it or merge it (#35)
            co.operating = c.cash;
        }
        goals.clear(); goals.addAll(s.goals);
        applied = s.name; appliedAt = w.getTotalWorldTime();
        d.note(w, "World set up from scenario \"" + s.name + "\" (seed " + s.seed + ", " + s.difficulty + "): " + s.towns.size() + " towns, " + s.companies.size() + " rivals, " + s.goals.size() + " goals");
        return "§aScenario \"" + s.name + "\" applied: " + s.towns.size() + " towns, " + s.companies.size() + " rival companies, " + s.goals.size() + " goals - see the console (World > Scenario)";
    }

    /** the goal's current value */
    public int value(World w, BizData d, GoalSpec g) {
        switch (g.kind) {
            case "CONNECT": { int n = 0; for (Towns.Town t : d.towns.towns.values()) if (t.access >= 50) n++; return n; }
            case "POPULATION": { double p = 0; for (Towns.Town t : d.towns.towns.values()) p += t.pop; return (int) p; }
            case "BOXES": { int n = 0; for (Map<String, Integer> m : d.mastery.counts.values()) n += m.getOrDefault("boxes", 0); return n; }
            case "PUNCTUAL": { int n = 0; for (Map<String, Integer> m : d.mastery.counts.values()) n = Math.max(n, m.getOrDefault("punctual", 0)); return n; }
            default: return 0;
        }
    }

    @Override
    public void daily(World w, BizData d, long day) {
        if (applied.isEmpty()) return;
        for (GoalSpec g : goals) {
            int v = value(w, d, g), was = progress.getOrDefault(g.kind, 0);
            progress.put(g.kind, v);
            if (v >= g.target && was < g.target) d.note(w, "🎯 Scenario goal reached: " + g.text);
        }
    }

    /** a brand-new world with scenarioOnNewWorld set: set it up once the first player is in */
    public void autoApply(World w, BizData d, EntityPlayer p, String name) {
        if (name == null || name.isEmpty() || refuse(w) != null) return;
        Spec s = read(name);
        if (s == null) { s = generate(name, w.getSeed(), "normal"); try { write(s); } catch (java.io.IOException ignored) { } }
        String r = apply(w, d, s, p);
        p.sendMessage(new net.minecraft.util.text.TextComponentString(r));
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        applied = t.getString("name"); appliedAt = t.getLong("at");
        goals.clear(); progress.clear();
        NBTTagList l = t.getTagList("g", 10);
        for (int i = 0; i < l.tagCount(); i++) { NBTTagCompound c = l.getCompoundTagAt(i); goals.add(goal(c.getString("k"), c.getString("t"), c.getInteger("n"), c.getInteger("d"))); }
        NBTTagCompound p = t.getCompoundTag("p");
        for (String k : p.getKeySet()) progress.put(k, p.getInteger(k));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("name", applied); t.setLong("at", appliedAt);
        NBTTagList l = new NBTTagList();
        for (GoalSpec g : goals) { NBTTagCompound c = new NBTTagCompound(); c.setString("k", g.kind); c.setString("t", g.text); c.setInteger("n", g.target); c.setInteger("d", g.byDay); l.appendTag(c); }
        t.setTag("g", l);
        NBTTagCompound p = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : progress.entrySet()) p.setInteger(e.getKey(), e.getValue());
        t.setTag("p", p);
        return t;
    }
}
