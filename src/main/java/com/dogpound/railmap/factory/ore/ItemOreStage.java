package com.dogpound.railmap.factory.ore;

import com.dogpound.railmap.factory.Factory;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * One processing stage of every ore (damage value = the ore, see {@link OreRegistry}): Crushed (jaw crusher, 2x),
 * Washed (washing plant, 4x), Concentrate (flotation, 6x; the induction smelter turns 3 into 4 ingots = 8x).
 * Coloured from each ore's own ingot texture on the client.
 */
public class ItemOreStage extends Item {
    public static final List<ItemOreStage> ALL = new ArrayList<>();

    public final String stage, label, tip;

    public ItemOreStage(String stage, String label, String tip) {
        this.stage = stage; this.label = label; this.tip = tip;
        setRegistryName(Factory.MODID, stage + "_ore");
        setTranslationKey(Factory.MODID + "." + stage + "_ore");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(FactoryTab.INSTANCE);
        ALL.add(this);
    }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        return label.replace("%s", OreRegistry.pretty(OreRegistry.name(s.getMetadata())));
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7" + tip);
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int m = 0; m < OreRegistry.count(); m++) items.add(new ItemStack(this, 1, m));
    }
}
