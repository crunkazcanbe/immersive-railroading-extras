package com.dogpound.railmap.cabinet;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/** Cartridge fuse for the cabinets' fuse carriers (meta 0 good, 1 blown - what comes out after a fault). */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class ItemFuse extends Item {
    public static ItemFuse INSTANCE;

    public ItemFuse() {
        setRegistryName(RailMap.MODID, "cartridge_fuse");
        setTranslationKey(RailMap.MODID + ".cartridge_fuse");
        setCreativeTab(RailMapTab.INSTANCE);
        setHasSubtypes(true);
        setMaxStackSize(16);
    }

    public static ItemStack stack(boolean blown) { return new ItemStack(INSTANCE, 1, blown ? 1 : 0); }

    @Override public String getTranslationKey(ItemStack s) { return super.getTranslationKey(s) + (s.getMetadata() == 1 ? "_blown" : ""); }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) items.add(new ItemStack(this, 1, 0));
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        if (s.getMetadata() == 1) tip.add("§cBlown - the element is gone. Find the fault, then fit a new one");
        else tip.add("§7Right-click an empty fuse carrier in an open machine cabinet to fit it");
        tip.add("§7Right-click a fitted fuse to pull it out (isolates that circuit)");
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> e) {
        INSTANCE = new ItemFuse();
        e.getRegistry().register(INSTANCE);
    }

    @Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
    public static final class Client {
        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public static void models(ModelRegistryEvent e) {
            ModelLoader.setCustomModelResourceLocation(INSTANCE, 0, new ModelResourceLocation(RailMap.MODID + ":cartridge_fuse", "inventory"));
            ModelLoader.setCustomModelResourceLocation(INSTANCE, 1, new ModelResourceLocation(RailMap.MODID + ":cartridge_fuse_blown", "inventory"));
        }
    }
}
