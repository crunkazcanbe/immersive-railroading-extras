package com.dogpound.railmap.modelrail;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.List;

/** Mini rolling stock for the model railway table: craft or buy them, put them in the desk's stock box, place them from the GUI. */
public class ItemMiniStock extends Item {
    public static final String[] IDS = {"steam", "diesel", "electric", "coach", "goods", "tank", "brake"};
    public static final String[] NAMES = {"Mini Steam Loco", "Mini Diesel Loco", "Mini Electric Loco", "Mini Passenger Coach", "Mini Goods Van", "Mini Tank Wagon", "Mini Brake Van"};
    /** price in cents when bought from the desk */
    public static final long[] PRICE = {4000, 3500, 4500, 1200, 800, 900, 700};

    public ItemMiniStock() {
        setRegistryName(RailMap.MODID, "mini_stock");
        setTranslationKey(RailMap.MODID + ".mini_stock");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    public static boolean loco(int meta) { return meta >= 0 && meta <= 2; }
    public static int meta(ItemStack s) { return Math.max(0, Math.min(IDS.length - 1, s.getMetadata())); }

    @Override public String getItemStackDisplayName(ItemStack s) { return NAMES[meta(s)]; }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int i = 0; i < IDS.length; i++) items.add(new ItemStack(this, 1, i));
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
        tip.add("§7Model railway rolling stock (" + (loco(meta(s)) ? "locomotive" : "wagon") + ")");
        tip.add("§8Put it in the Model Railway Control Desk's stock box, then PLACE A / B in the layout editor");
    }
}
