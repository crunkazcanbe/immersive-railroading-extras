package com.dogpound.railmap.cctv;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.List;

/** A 3.5" surveillance hard drive for the NVR's bays: 1, 2, 4 or 8 TB (meta 0-3). */
public class ItemDrive extends Item {
    public static final int[] TB = {1, 2, 4, 8};
    public static final String[] IDS = {"1tb", "2tb", "4tb", "8tb"};

    public ItemDrive() {
        setRegistryName(RailMap.MODID, "cctv_drive");
        setTranslationKey(RailMap.MODID + ".cctv_drive");
        setHasSubtypes(true);
        setMaxStackSize(1);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    public static int tb(ItemStack s) { return s.getItem() instanceof ItemDrive ? TB[Math.max(0, Math.min(3, s.getMetadata()))] : 0; }

    @Override public String getItemStackDisplayName(ItemStack s) { return "CCTV Hard Drive " + tb(s) + " TB"; }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> l) { if (isInCreativeTab(tab)) for (int i = 0; i < 4; i++) l.add(new ItemStack(this, 1, i)); }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Surveillance-grade 7200 rpm, 24/7 rated");
        tip.add("§8Fit it in an NVR drive bay: " + tb(s) * CctvConfig.framesPerTB + " recorded frames (" + tb(s) * CctvConfig.framesPerTB * CctvConfig.recordSeconds / 60 + " camera-minutes)");
        tip.add("§8Changing drives rebuilds the array (recordings are wiped)");
    }
}
