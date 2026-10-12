package com.dogpound.railmap.factory.ore;

import com.dogpound.railmap.factory.Factory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.oredict.OreDictionary;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Every ore from every mod: anything registered as {@code ore<Name>} that also has an {@code ingot<Name>} gets crushed,
 * washed and concentrate forms. Each ore keeps the same item damage value forever (saved in
 * config/prideindustry-ores.txt), so worlds stay valid when mods are added or removed.
 */
public final class OreRegistry {
    private OreRegistry() {}

    public static File configDir;
    private static final List<String> NAMES = new ArrayList<>();           // index = item damage
    private static final Map<String, Integer> INDEX = new HashMap<>();

    public static int count() { return NAMES.size(); }
    public static String name(int meta) { return meta >= 0 && meta < NAMES.size() ? NAMES.get(meta) : null; }
    public static int meta(String name) { Integer i = INDEX.get(name); return i == null ? -1 : i; }

    /** "Copper" -> "Copper", "NetherQuartz" -> "Nether Quartz" */
    public static String pretty(String n) { return n == null ? "?" : n.replaceAll("([a-z])([A-Z])", "$1 $2"); }

    public static void discover() {
        File f = new File(configDir, "prideindustry-ores.txt");
        try {
            if (f.isFile()) for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String n = line.trim();
                if (!n.isEmpty() && !n.startsWith("#")) add(n);
            }
        } catch (Exception e) { Factory.LOG.warn("Couldn't read {}: {}", f, e.toString()); }
        int before = NAMES.size();
        List<String> found = new ArrayList<>();
        for (String od : OreDictionary.getOreNames()) {
            if (!od.startsWith("ore") || od.length() < 4 || !Character.isUpperCase(od.charAt(3))) continue;
            String n = od.substring(3);
            if (OreDictionary.getOres(od, false).isEmpty() || OreDictionary.getOres("ingot" + n, false).isEmpty()) continue;
            found.add(n);
        }
        Collections.sort(found);
        for (String n : found) add(n);
        if (NAMES.size() != before || !f.isFile()) {
            StringBuilder sb = new StringBuilder("# Pride Industry ore list: line number (from 0, comments skipped) = item damage. Only ever append.\n");
            for (String n : NAMES) sb.append(n).append('\n');
            try { Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8)); }
            catch (Exception e) { Factory.LOG.warn("Couldn't write {}: {}", f, e.toString()); }
        }
    }

    private static void add(String n) {
        if (INDEX.containsKey(n)) return;
        INDEX.put(n, NAMES.size());
        NAMES.add(n);
    }

    /** the ore dictionary's first ingot for this ore (or EMPTY) */
    public static ItemStack ingot(String name, int count) {
        List<ItemStack> l = OreDictionary.getOres("ingot" + name, false);
        if (l.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = l.get(0).copy();
        if (s.getMetadata() == OreDictionary.WILDCARD_VALUE) s.setItemDamage(0);
        s.setCount(count);
        return s;
    }

    /** which registered ore an item stack is (by its ore dictionary names), or null */
    public static String oreOf(ItemStack s) {
        if (s.isEmpty()) return null;
        for (int id : OreDictionary.getOreIDs(s)) {
            String od = OreDictionary.getOreName(id);
            if (od.startsWith("ore") && INDEX.containsKey(od.substring(3))) return od.substring(3);
        }
        return null;
    }

    /** crushed / washed ore still smelt in any furnace, 1 ingot each (the 2x and 4x tiers) */
    public static void addSmelting() {
        for (int m = 0; m < NAMES.size(); m++) {
            ItemStack ing = ingot(NAMES.get(m), 1);
            if (ing.isEmpty()) continue;
            for (ItemOreStage st : ItemOreStage.ALL)
                FurnaceRecipes.instance().addSmeltingRecipe(new ItemStack(st, 1, m), ing, 0.5f);
        }
    }
}
