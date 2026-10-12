package com.dogpound.railmap.freight6.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.freight6.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

/** batch 6 #26-30 client: item models, the live parts (rack pallets, dock door, festive lights, presents), forklifts, holiday trains */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class F6Client {
    private F6Client() {}

    /** decorated cars near the player: entity id -> kit (PacketHoliday) */
    public static final Map<Integer, Integer> KITS = new HashMap<>();
    public static int season;
    public static long kitsAt;
    private static final Map<String, IBakedModel> BAKED = new HashMap<>();

    public static void holiday(PacketHoliday m) { KITS.clear(); KITS.putAll(m.kits); season = m.season; kitsAt = System.currentTimeMillis(); }

    public static IBakedModel baked(String name) {
        return BAKED.computeIfAbsent(name, n -> Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager()
                .getModel(new ModelResourceLocation(RailMap.MODID + ":" + n, "inventory")));
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        for (Block b : F6Registry.BLOCKS)
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        if (F6Registry.kit != null) {
            for (int i = 0; i < ItemHolidayKit.VARIANTS.length; i++)
                ModelLoader.setCustomModelResourceLocation(F6Registry.kit, i, new ModelResourceLocation(RailMap.MODID + ":" + ItemHolidayKit.VARIANTS[i], "inventory"));
            ModelBakery.registerItemVariants(F6Registry.kit, new ResourceLocation(RailMap.MODID, ItemHolidayKit.VARIANTS[0]), new ResourceLocation(RailMap.MODID, ItemHolidayKit.VARIANTS[1]),
                    new ResourceLocation(RailMap.MODID, "forklift"));
        }
        BAKED.clear();
        RenderingRegistry.registerEntityRenderingHandler(EntityForklift.class, F6Render.Forklift::new);
        ClientRegistry.bindTileEntitySpecialRenderer(TileWarehouseRack.class, new F6Render.Rack());
        ClientRegistry.bindTileEntitySpecialRenderer(TileWarehouseDock.class, new F6Render.Dock());
        ClientRegistry.bindTileEntitySpecialRenderer(TileXmasTree.class, new F6Render.Tree());
        ClientRegistry.bindTileEntitySpecialRenderer(TileHolidayLights.class, new F6Render.Festoon());
        ClientRegistry.bindTileEntitySpecialRenderer(TileHalloweenDisplay.class, new F6Render.Pumpkins());
    }
}
