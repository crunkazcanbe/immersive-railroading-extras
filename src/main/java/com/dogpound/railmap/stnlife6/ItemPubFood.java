package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.RailMap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
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

/** station pub food + drink (batch 6c #14): pints from the bar taps, dishes from the kitchen stations */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class ItemPubFood extends ItemFood {
    public static final String[] NAMES = {"pint_ale", "pint_lager", "pint_cider", "fish_chips", "pie_mash", "burger", "breakfast", "soup", "roast", "chips", "steak"};
    public static final String[] LABELS = {"Pint of Ale", "Pint of Lager", "Pint of Cider", "Fish and Chips", "Pie and Mash", "Burger", "Full Breakfast", "Vegetable Soup", "Roast Dinner", "Chips", "Steak"};
    /** food points, saturation x10 */
    static final int[][] STATS = {{2, 2}, {2, 2}, {3, 3}, {9, 9}, {10, 10}, {8, 8}, {10, 9}, {6, 7}, {12, 12}, {4, 4}, {9, 10}};
    public static Item INSTANCE;

    public ItemPubFood() {
        super(2, 0.3f, false);
        setHasSubtypes(true);
        setMaxStackSize(16);
        setRegistryName(new ResourceLocation(RailMap.MODID, "pub_food"));
        setTranslationKey(RailMap.MODID + ".pub_food");
        setCreativeTab(CreativeTabs.FOOD);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> e) { e.getRegistry().register(INSTANCE = new ItemPubFood()); }

    public static ItemStack make(int k) { return new ItemStack(INSTANCE, 1, Math.floorMod(k, NAMES.length)); }
    static int k(ItemStack s) { return Math.floorMod(s.getMetadata(), NAMES.length); }
    public static boolean drink(int k) { return k < 3; }

    @Override public String getTranslationKey(ItemStack s) { return "item." + RailMap.MODID + ".pub_food." + NAMES[k(s)]; }
    @Override public int getHealAmount(ItemStack s) { return STATS[k(s)][0]; }
    @Override public float getSaturationModifier(ItemStack s) { return STATS[k(s)][1] / 10f; }
    @Override public EnumAction getItemUseAction(ItemStack s) { return drink(k(s)) ? EnumAction.DRINK : EnumAction.EAT; }
    @Override public int getMaxItemUseDuration(ItemStack s) { return drink(k(s)) ? 40 : 48; }

    @Override
    protected void onFoodEaten(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) return;
        if (drink(k(s))) p.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 20 * 6, 0, false, false));
        if (k(s) == 6 || k(s) == 8) p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 20 * 8, 0, false, true));
    }

    /** the glass / bowl comes back */
    @Override
    public ItemStack onItemUseFinish(ItemStack s, World w, EntityLivingBase e) {
        int kind = k(s);
        ItemStack left = super.onItemUseFinish(s, w, e);
        ItemStack back = drink(kind) ? new ItemStack(Items.GLASS_BOTTLE) : kind == 7 ? new ItemStack(Items.BOWL) : ItemStack.EMPTY;
        if (back.isEmpty() || !(e instanceof EntityPlayer p) || p.capabilities.isCreativeMode) return left;
        if (left.isEmpty()) return back;
        if (!p.inventory.addItemStackToInventory(back)) p.dropItem(back, false);
        return left;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int i = 0; i < NAMES.length; i++) items.add(new ItemStack(this, 1, i));
    }
}
