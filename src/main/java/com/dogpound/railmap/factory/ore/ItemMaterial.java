package com.dogpound.railmap.factory.ore;

import com.dogpound.railmap.factory.Factory;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.List;

/** What the new plants make: coke (Coke Oven), steel (Blast Furnace), sawdust (Sawmill), slag. */
public class ItemMaterial extends Item {
    public enum M {
        COKE("coke", "Coke", "From the Coke Oven: burns hot and long, the Blast Furnace runs on it", 3200),
        STEEL("steel_ingot", "Steel Ingot", "From the Blast Furnace: iron and coke - roll it into plates", 0),
        SAWDUST("sawdust", "Sawdust", "From the Sawmill: a little fuel", 100),
        SLAG("slag", "Slag", "What the Blast Furnace leaves behind", 0),
        FLOUR("flour", "Flour", "From the Grain Windmill: bake it in a furnace for bread", 0),
        ELECTRODE("graphite_electrode", "Graphite Electrode", "The Electric Arc Furnace burns these away arcing: one lasts 16 heats", 0),
        // her list section 13: the railway is built from what the mills make (append only - metadata = ordinal)
        RAIL("rail_section", "Steel Rail Section", "From the Rolling Mill on its RAIL pass: flat-bottom rail. Immersive Railroading track takes it (irRail)", 0),
        SLEEPER("treated_sleeper", "Creosoted Sleeper", "From the Sleeper Treatment Plant: pressure-creosoted timber. Immersive Railroading track takes it (irTie)", 0),
        CLIPS("rail_clips", "Rail Clips + Baseplates", "From the Assembly Line: what holds rail to sleeper - points, signals and catenary parts need them", 0),
        SCRAP("steel_scrap", "Steel Scrap", "From the Jaw Crusher breaking up worn bogies and wheelsets: charge it into the Electric Arc Furnace", 0);
        public final String id, label, tip; public final int burn;
        M(String id, String label, String tip, int burn) { this.id = id; this.label = label; this.tip = tip; this.burn = burn; }
    }

    public ItemMaterial() {
        setRegistryName(Factory.MODID, "material");
        setTranslationKey(Factory.MODID + ".material");
        setHasSubtypes(true);
        setCreativeTab(FactoryTab.INSTANCE);
    }

    public static M of(ItemStack s) { M[] m = M.values(); return m[Math.min(m.length - 1, Math.max(0, s.getMetadata()))]; }
    public static ItemStack make(M m, int n) { return new ItemStack(com.dogpound.railmap.factory.proxy.CommonProxy.material, n, m.ordinal()); }
    @Override public String getItemStackDisplayName(ItemStack s) { return of(s).label; }
    @Override public int getItemBurnTime(ItemStack s) { return of(s).burn; }
    @Override public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> l) { if (isInCreativeTab(tab)) for (M m : M.values()) l.add(new ItemStack(this, 1, m.ordinal())); }
    @Override public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) { t.add("§7" + of(s).tip); }
}
