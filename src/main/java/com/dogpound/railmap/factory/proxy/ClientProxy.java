package com.dogpound.railmap.factory.proxy;

import com.dogpound.railmap.factory.client.MachineRenderer;
import com.dogpound.railmap.factory.client.OreColors;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.factory.ore.ItemOreStage;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();
        OBJLoader.INSTANCE.addDomain(com.dogpound.railmap.factory.Factory.MODID);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MachineRenderer.Models.class);
    }

    @Override
    public void init() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileMachine.class, new MachineRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.factory.conveyor.TileConveyor.class, new com.dogpound.railmap.factory.client.ConveyorRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.factory.block.TilePort.class, new com.dogpound.railmap.factory.client.PortRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.factory.block.TilePipe.class, new com.dogpound.railmap.factory.client.PipeRenderer());
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler(OreColors::color, crushed, washed, concentrate);
        com.dogpound.railmap.factory.fluid.ModFluids.oreColor = OreColors::colorOf;
    }

    @SubscribeEvent
    public void models(ModelRegistryEvent e) {
        for (Block b : BLOCKS) {
            Item it = Item.getItemFromBlock(b);
            ModelLoader.setCustomModelResourceLocation(it, 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        }
        for (com.dogpound.railmap.factory.ore.ItemMaterial.M m : com.dogpound.railmap.factory.ore.ItemMaterial.M.values())
            ModelLoader.setCustomModelResourceLocation(CommonProxy.material, m.ordinal(), new ModelResourceLocation(com.dogpound.railmap.factory.Factory.MODID + ":material_" + m.id, "inventory"));
        ModelLoader.setCustomModelResourceLocation(CommonProxy.crate, 0, new ModelResourceLocation(com.dogpound.railmap.factory.Factory.MODID + ":crate", "inventory"));
        for (ItemOreStage st : ItemOreStage.ALL) {
            ModelResourceLocation loc = new ModelResourceLocation(st.getRegistryName(), "inventory");
            net.minecraft.client.renderer.block.model.ModelBakery.registerItemVariants(st, loc);
            ModelLoader.setCustomMeshDefinition(st, s -> loc);
        }
    }
}
