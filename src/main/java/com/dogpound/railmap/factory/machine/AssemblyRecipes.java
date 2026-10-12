package com.dogpound.railmap.factory.machine;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Assembly Line builds (PRODUCT selector on its panel). Ingredients are ore-dictionary names first (any mod's
 * copper, rubber, iron...), with a plain item id as the fallback. A full set is taken from the input bin, the work
 * piece travels through the five stations (press, weld, rivet, paint, inspect) and comes out as the product.
 */
public final class AssemblyRecipes {
    private AssemblyRecipes() {}

    public static final class Part {
        final String ore; final String fallback; final int count;
        Part(String ore, String fallback, int count) { this.ore = ore; this.fallback = fallback; this.count = count; }
        public boolean matches(ItemStack s) {
            if (s.isEmpty()) return false;
            if (ore != null) for (int id : OreDictionary.getOreIDs(s)) if (OreDictionary.getOreName(id).equals(ore)) return true;
            if (fallback == null || s.getItem().getRegistryName() == null) return false;
            String[] f = fallback.split("@");                                // "modid:item@meta" pins the metadata
            return s.getItem().getRegistryName().toString().equals(f[0]) && (f.length == 1 || s.getMetadata() == Integer.parseInt(f[1]));
        }
        public String label() { return count + "x " + (ore != null ? ore : fallback); }
    }

    public static final class Recipe {
        public final String id, label, output; public final int outCount, ticks; public final List<Part> parts = new ArrayList<>();
        Recipe(String id, String label, String output, int outCount, int ticks) { this.id = id; this.label = label; this.output = output; this.outCount = outCount; this.ticks = ticks; }
        Recipe part(String ore, String fallback, int n) { parts.add(new Part(ore, fallback, n)); return this; }
        public ItemStack result() {
            String[] o = output.split("@");
            Item it = Item.REGISTRY.getObject(new ResourceLocation(o[0]));
            return it == null ? ItemStack.EMPTY : new ItemStack(it, outCount, o.length > 1 ? Integer.parseInt(o[1]) : 0);
        }
    }

    public static final List<Recipe> ALL = new ArrayList<>();
    static {
        ALL.add(new Recipe("casing", "CASING", "irextras:machine_casing", 8, 200)
                .part("ingotIron", "minecraft:iron_ingot", 6).part("dustRedstone", "minecraft:redstone", 1));
        ALL.add(new Recipe("motor", "MOTOR", "irextras:grid_motor", 1, 400)
                .part("ingotCopper", null, 4).part("ingotIron", "minecraft:iron_ingot", 4).part("dustRedstone", "minecraft:redstone", 2));
        ALL.add(new Recipe("coupling", "COUPLING", "irextras:drive_coupling", 2, 240)
                .part(null, "irextras:machine_casing", 2).part("ingotIron", "minecraft:iron_ingot", 2).part("dustRedstone", "minecraft:redstone", 1));
        ALL.add(new Recipe("conveyor", "BELTS", "irextras:conveyor", 12, 200)
                .part("itemRubber", "minecraft:leather", 3).part("ingotIron", "minecraft:iron_ingot", 2));
        ALL.add(new Recipe("cable", "CABLE", "irextras:grid_cable", 16, 160)
                .part("ingotCopper", null, 3).part("itemRubber", "minecraft:slime_ball", 1));
        ALL.add(new Recipe("rail", "RAILS", "minecraft:rail", 48, 160)
                .part("ingotIron", "minecraft:iron_ingot", 6).part("stickWood", "minecraft:stick", 1));
        ALL.add(new Recipe("cart", "CARTS", "minecraft:minecart", 2, 200)
                .part("ingotIron", "minecraft:iron_ingot", 8));
        ALL.add(new Recipe("hatch", "HATCHES", "irextras:item_input_hatch", 2, 200)
                .part(null, "irextras:machine_casing", 2).part(null, "minecraft:hopper", 1));
        // ---- her list section 13: the railway's own parts (chain A), the electrification gear (chain B), depot spares (#91) ----
        ALL.add(new Recipe("clips", "CLIPS", "irextras:material@8", 16, 160).part("ingotSteel", "irextras:material@1", 2));
        ALL.add(new Recipe("points", "POINTS", "irextras:point_machine", 1, 400)
                .part("ingotSteel", "irextras:material@1", 4).part(null, "irextras:material@8", 4).part(null, "irextras:part@3", 1).part(null, "irextras:part@2", 1));
        ALL.add(new Recipe("signal", "SIGNAL", "irextras:signal_color_light", 1, 320)
                .part("ingotSteel", "irextras:material@1", 2).part("blockGlass", "minecraft:glass", 3).part(null, "irextras:part@2", 1).part("dustRedstone", "minecraft:redstone", 2));
        ALL.add(new Recipe("mast", "OLE MAST", "irextras:catenary_mast", 2, 240).part("ingotSteel", "irextras:material@1", 3).part(null, "irextras:material@8", 2));
        ALL.add(new Recipe("contact", "OLE WIRE", "irextras:catenary_wire", 8, 200).part("ingotCopper", null, 3).part(null, "irextras:part@15", 1));
        ALL.add(new Recipe("core", "TX CORE", "irextras:part@14", 1, 240).part("ingotSteel", "irextras:material@1", 4));
        ALL.add(new Recipe("insulator", "INSULATOR", "irextras:part@15", 4, 200).part(null, "minecraft:clay_ball", 4).part("dyeWhite", "minecraft:quartz", 1));
        ALL.add(new Recipe("transformer", "TRANSFORMER", "irextras:grid_transformer", 1, 600)
                .part(null, "irextras:part@13", 2).part(null, "irextras:part@14", 1).part(null, "irextras:part@15", 2).part(null, "irextras:part@0", 2).part(null, "irextras:part@7", 1).part(null, "irextras:part@1", 1));
        ALL.add(new Recipe("breaker", "BREAKER", "irextras:grid_breaker", 1, 400)
                .part(null, "irextras:part@18", 2).part(null, "irextras:part@11", 2).part(null, "irextras:part@0", 2).part(null, "irextras:part@2", 1));
        ALL.add(new Recipe("brakes", "BRAKE BLKS", "irextras:spare@0", 4, 200).part("ingotIron", "minecraft:iron_ingot", 2).part(null, "minecraft:clay_ball", 2));
        ALL.add(new Recipe("wheelset", "WHEELSET", "irextras:spare@1", 1, 480).part("ingotSteel", "irextras:material@1", 8).part(null, "irextras:part@10", 1));
        ALL.add(new Recipe("bearing", "BEARINGS", "irextras:spare@2", 2, 240).part(null, "irextras:part@9", 2).part(null, "irextras:part@12", 1));
        ALL.add(new Recipe("tmotor", "TRAC MOTOR", "irextras:spare@3", 1, 600).part(null, "irextras:part@3", 2).part(null, "irextras:part@13", 2).part("ingotSteel", "irextras:material@1", 4));
        ALL.add(new Recipe("powerkit", "POWER KIT", "irextras:spare@4", 1, 480).part("ingotSteel", "irextras:material@1", 4).part(null, "irextras:part@12", 2).part(null, "irextras:part@9", 1));
        ALL.add(new Recipe("card", "CTRL CARD", "irextras:spare@5", 1, 320).part(null, "irextras:part@2", 1).part("dustRedstone", "minecraft:redstone", 2));
        ALL.add(new Recipe("coupler", "COUPLER", "irextras:spare@6", 1, 240).part("ingotSteel", "irextras:material@1", 3));
        ALL.add(new Recipe("hose", "BRAKE HOSE", "irextras:spare@7", 2, 160).part("itemRubber", "minecraft:slime_ball", 2).part("ingotIron", "minecraft:iron_ingot", 1));
        ALL.add(new Recipe("filters", "FILTERS", "irextras:spare@8", 2, 160).part(null, "minecraft:paper", 2).part("ingotIron", "minecraft:iron_ingot", 1));
        // infrastructure spares (her 48-next #16: factory-made parts feed the depot stores + purchase orders)
        ALL.add(new Recipe("rail", "RAIL 18M", "irextras:spare@11", 1, 360).part("ingotSteel", "irextras:material@1", 6));
        ALL.add(new Recipe("ptmotor", "POINT MOTOR", "irextras:spare@12", 1, 400).part(null, "irextras:part@3", 1).part(null, "irextras:part@18", 1).part(null, "irextras:part@10", 1));
        ALL.add(new Recipe("sigled", "SIGNAL LED", "irextras:spare@13", 2, 240).part(null, "irextras:part@2", 1).part("dustGlowstone", "minecraft:glowstone_dust", 2));
        ALL.add(new Recipe("insul", "INSULATOR", "irextras:spare@14", 2, 200).part(null, "irextras:part@15", 2));
        ALL.add(new Recipe("cbkit", "BREAKER KIT", "irextras:spare@15", 1, 320).part(null, "irextras:part@11", 2).part(null, "irextras:part@18", 1));
        ALL.add(new Recipe("bushing", "TX BUSHING", "irextras:spare@16", 1, 360).part(null, "irextras:part@15", 1).part(null, "irextras:part@13", 1).part(null, "irextras:part@12", 1));
        ALL.add(new Recipe("motorkit", "MOTOR KIT", "irextras:spare@17", 1, 320).part(null, "irextras:part@9", 2).part(null, "irextras:part@13", 1));
        ALL.add(new Recipe("structkit", "STRUCT KIT", "irextras:spare@18", 2, 200).part("ingotIron", "minecraft:iron_ingot", 2).part(null, "minecraft:sand", 4));
    }
}
