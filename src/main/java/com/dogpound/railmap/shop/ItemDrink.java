package com.dogpound.railmap.shop;

import com.dogpound.railmap.RailMap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** a takeaway drink from the station coffee kiosk: you drink it (a little food, a short caffeine Speed boost) */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class ItemDrink extends ItemFood {
    public static final String[] NAMES = {"espresso", "americano", "latte", "cappuccino", "mocha"};
    public static final String[] LABELS = {"Espresso", "Americano", "Latte", "Cappuccino", "Mocha"};
    /** food points, Speed seconds */
    static final int[][] STATS = {{1, 90}, {2, 60}, {4, 45}, {3, 45}, {5, 40}};
    public static Item INSTANCE;

    public ItemDrink() {
        super(2, 0.3f, false);
        setAlwaysEdible();
        setHasSubtypes(true);
        setMaxStackSize(8);
        setRegistryName(new ResourceLocation(RailMap.MODID, "coffee"));
        setTranslationKey(RailMap.MODID + ".coffee");
        setCreativeTab(CreativeTabs.FOOD);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> e) { e.getRegistry().register(INSTANCE = new ItemDrink()); }

    public static ItemStack make(int kind, int shots, String size) {
        ItemStack s = new ItemStack(INSTANCE, 1, Math.floorMod(kind, NAMES.length));
        s.setStackDisplayName("§f" + size + " " + LABELS[s.getMetadata()] + (shots > 1 ? " (double shot)" : ""));
        return s;
    }

    @Override public String getTranslationKey(ItemStack s) { return "item." + RailMap.MODID + ".coffee." + NAMES[Math.floorMod(s.getMetadata(), NAMES.length)]; }
    @Override public int getHealAmount(ItemStack s) { return STATS[Math.floorMod(s.getMetadata(), NAMES.length)][0]; }
    @Override public EnumAction getItemUseAction(ItemStack s) { return EnumAction.DRINK; }
    @Override public int getMaxItemUseDuration(ItemStack s) { return 40; }

    @Override
    protected void onFoodEaten(ItemStack s, World w, EntityPlayer p) {
        if (!w.isRemote) p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 20 * STATS[Math.floorMod(s.getMetadata(), NAMES.length)][1], 0, false, true));
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int i = 0; i < NAMES.length; i++) items.add(new ItemStack(this, 1, i));
    }
}
