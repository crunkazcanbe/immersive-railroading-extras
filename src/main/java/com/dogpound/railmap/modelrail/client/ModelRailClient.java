package com.dogpound.railmap.modelrail.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.modelrail.ItemMiniStock;
import com.dogpound.railmap.modelrail.ModelRailRegistry;
import com.dogpound.railmap.modelrail.TileModelRailway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** client side of the model railway: item models, the table renderer, the layout editor */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class ModelRailClient {
    private ModelRailClient() {}

    public static void open(BlockPos desk) { Minecraft.getMinecraft().displayGuiScreen(new GuiModelRail(desk)); }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        for (net.minecraft.block.Block b : new net.minecraft.block.Block[]{ModelRailRegistry.desk, ModelRailRegistry.table})
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        for (int i = 0; i < ItemMiniStock.IDS.length; i++)
            ModelLoader.setCustomModelResourceLocation(ModelRailRegistry.stock, i, new ModelResourceLocation(RailMap.MODID + ":mini_" + ItemMiniStock.IDS[i], "inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(TileModelRailway.class, new ModelRailRenderer());
    }
}
