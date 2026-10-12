package com.dogpound.railmap.item;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.*;

/**
 * A 20 ft shipping container (her ChatGPT list #47): a real box of goods (27 stacks) with an ISO 6346 number (owner code,
 * serial, check digit), a destination, the freight contract it travels under, who owns it and the terminal fees it has
 * run up. It rides an IR freight car as cargo; Container Terminals lift it between trucks / the warehouse, their stack
 * and the train, and stuff or strip it.
 */
public class ItemContainer extends Item {
    public static final int SLOTS = 27;
    public static final long TARE_KG = 2200;
    public static final int[] LIVERIES = {0xB03A2E, 0x1F5FA8, 0x2E8B57, 0xD9A21B, 0x6C3483, 0xE67E22, 0x17A589, 0x7B7D7D};

    public ItemContainer() {
        setRegistryName(RailMap.MODID, "shipping_container");
        setTranslationKey(RailMap.MODID + ".shipping_container");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    // ---- ISO 6346 ---------------------------------------------------------------------------------------------------
    private static int letter(char c) {
        int v = 10 + (c - 'A');
        return v + (v - 1) / 10;                // skip 11, 22, 33 (A=10, B=12 ... K=21, L=23 ... U=32, V=34 ... Z=38)
    }

    /** the check digit for "ABCU123456" */
    public static int check(String code) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = code.charAt(i);
            int v = Character.isDigit(c) ? c - '0' : letter(c);
            sum += v << i;
        }
        return sum % 11 % 10;
    }

    /** a new empty container: owner code (3 letters), serial */
    public static ItemStack make(Item item, String owner3, int serial, UUID owner, String ownerName) {
        String o = (owner3 + "XXX").substring(0, 3).toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "X");
        String code = o + "U" + String.format(Locale.ROOT, "%06d", Math.abs(serial) % 1_000_000);
        ItemStack s = new ItemStack(item);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("cid", code + check(code));
        if (owner != null) t.setUniqueId("owner", owner);
        t.setString("ownerN", ownerName == null ? "" : ownerName);
        t.setInteger("col", LIVERIES[Math.floorMod(o.hashCode(), LIVERIES.length)]);
        t.setTag("inv", new NBTTagList());
        s.setTagCompound(t);
        return s;
    }

    public static boolean is(ItemStack s) { return !s.isEmpty() && s.getItem() instanceof ItemContainer; }
    private static NBTTagCompound tag(ItemStack s) { if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound()); return s.getTagCompound(); }

    public static String id(ItemStack s) { String c = tag(s).getString("cid"); return c.isEmpty() ? "UNNUMBERED" : c; }
    /** "PRDU 123456 7" */
    public static String pretty(ItemStack s) { String c = id(s); return c.length() == 11 ? c.substring(0, 4) + " " + c.substring(4, 10) + " " + c.substring(10) : c; }
    public static int colour(ItemStack s) { return tag(s).hasKey("col") ? tag(s).getInteger("col") : LIVERIES[0]; }
    public static UUID owner(ItemStack s) { return tag(s).hasUniqueId("owner") ? tag(s).getUniqueId("owner") : null; }
    public static String ownerName(ItemStack s) { return tag(s).getString("ownerN"); }
    public static long dest(ItemStack s) { return tag(s).hasKey("dest") ? tag(s).getLong("dest") : Long.MIN_VALUE; }
    public static String destName(ItemStack s) { return tag(s).getString("destN"); }
    public static void setDest(ItemStack s, long key, String name) { tag(s).setLong("dest", key); tag(s).setString("destN", name); }
    public static int contract(ItemStack s) { return tag(s).getInteger("contract"); }
    public static void setContract(ItemStack s, int id) { tag(s).setInteger("contract", id); }
    public static long fees(ItemStack s) { return tag(s).getLong("fees"); }
    public static void addFees(ItemStack s, long c) { tag(s).setLong("fees", Math.max(0, fees(s) + c)); }
    public static long since(ItemStack s) { return tag(s).getLong("since"); }
    public static void setSince(ItemStack s, long t) { tag(s).setLong("since", t); }
    public static long lastBilled(ItemStack s) { return tag(s).getLong("billed"); }
    public static void setBilled(ItemStack s, long t) { tag(s).setLong("billed", t); }

    public static List<ItemStack> contents(ItemStack s) {
        List<ItemStack> out = new ArrayList<>();
        NBTTagList l = tag(s).getTagList("inv", 10);
        for (int i = 0; i < l.tagCount(); i++) { ItemStack x = new ItemStack(l.getCompoundTagAt(i)); if (!x.isEmpty()) out.add(x); }
        return out;
    }

    public static void setContents(ItemStack s, List<ItemStack> items) {
        NBTTagList l = new NBTTagList();
        for (ItemStack x : items) if (!x.isEmpty() && l.tagCount() < SLOTS) l.appendTag(x.writeToNBT(new NBTTagCompound()));
        tag(s).setTag("inv", l);
    }

    public static int count(ItemStack s) { int n = 0; for (ItemStack x : contents(s)) n += x.getCount(); return n; }
    public static long weightKg(ItemStack s) { return TARE_KG + count(s) * 15L; }

    /** goods moved, with containers opened up: what freight contracts count */
    public static List<ItemStack> unpack(List<ItemStack> moved) {
        boolean any = false;
        for (ItemStack s : moved) if (is(s)) { any = true; break; }
        if (!any) return moved;
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : moved) if (is(s)) out.addAll(contents(s)); else out.add(s);
        return out;
    }

    /** a one-line manifest: "24 Coal, 64 Iron Ingot" */
    public static String manifest(ItemStack s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (ItemStack x : contents(s)) m.merge(x.getDisplayName(), x.getCount(), Integer::sum);
        if (m.isEmpty()) return "empty";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) { if (b.length() > 0) b.append(", "); b.append(e.getValue()).append(' ').append(e.getKey()); }
        return b.toString();
    }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        return s.hasTagCompound() && s.getTagCompound().hasKey("cid") ? "Container " + pretty(s) : "Shipping Container (unnumbered)";
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        if (!s.hasTagCompound() || !s.getTagCompound().hasKey("cid")) { tip.add("§7Gate it in at a Container Terminal to get it numbered and filled"); return; }
        tip.add("§7Contents: §f" + manifest(s));
        tip.add(String.format(Locale.ROOT, "§7Gross §f%.1f t §8(tare %.1f t)", weightKg(s) / 1000.0, TARE_KG / 1000.0));
        if (!destName(s).isEmpty()) tip.add("§7Destination: §f" + destName(s));
        if (contract(s) > 0) tip.add("§7Waybill: §fcontract #" + contract(s));
        if (!ownerName(s).isEmpty()) tip.add("§7Owner: §f" + ownerName(s));
        if (fees(s) > 0) tip.add("§6Terminal fees due: " + com.dogpound.railmap.economy.Coins.fmt(fees(s)));
        tip.add("§8Rides a freight car as cargo; Container Terminals lift, stack, stuff and strip it");
    }

    @Override public boolean hasEffect(ItemStack s) { return false; }
}
