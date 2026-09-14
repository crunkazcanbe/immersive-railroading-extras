package com.dogpound.railmap.proxy;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TileDisplayPanel;
import com.dogpound.railmap.block.TileRailDisplay;
import com.dogpound.railmap.client.ClientTrains;
import com.dogpound.railmap.client.HandheldMap;
import com.dogpound.railmap.client.gui.GuiDispatcherBoard;
import com.dogpound.railmap.client.render.DisplayPanelRenderer;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.TrainNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * Client half. Deliberately NOT an {@code @EventBusSubscriber}: class-target registration
 * also picks up the inherited static handlers from {@link CommonProxy} and would register
 * the block twice. Registering the instance only sees instance methods.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(this);
        ClientRegistry.bindTileEntitySpecialRenderer(TileDisplayPanel.class, new DisplayPanelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileSignalMast.class,
                new com.dogpound.railmap.client.render.SignalMastRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileSignalBridge.class,
                new com.dogpound.railmap.client.render.SignalBridgeRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileCrossing.Signal.class,
                new com.dogpound.railmap.client.render.CrossingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileCrossing.Gate.class,
                new com.dogpound.railmap.client.render.CrossingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileCrossing.Cantilever.class,
                new com.dogpound.railmap.client.render.CrossingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileArrivalsBoard.class,
                new com.dogpound.railmap.client.render.ArrivalsBoardRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileSpeedSign.class,
                new com.dogpound.railmap.client.render.SpeedSignRenderer());
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent e) {
        for (net.minecraft.block.Block b : ALL_BLOCKS) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0,
                    new ModelResourceLocation(b.getRegistryName(), "inventory"));
        }
        ModelLoader.setCustomModelResourceLocation(railMap, 0,
                new ModelResourceLocation(railMap.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(signalWrench, 0,
                new ModelResourceLocation(signalWrench.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ticket, 0,
                new ModelResourceLocation(ticket.getRegistryName(), "inventory"));
    }

    @Override
    public void acceptTrains(int dim, List<TrainNode> trains) {
        Minecraft.getMinecraft().addScheduledTask(() -> ClientTrains.accept(dim, trains));
    }

    @Override
    public void openTicketMachine(com.dogpound.railmap.network.PacketTicketMenu menu) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof com.dogpound.railmap.client.gui.GuiTicketMachine g) g.update(menu);
            else mc.displayGuiScreen(new com.dogpound.railmap.client.gui.GuiTicketMachine(menu));
        });
    }

    @Override
    public void acceptRailState(com.dogpound.railmap.network.PacketRailState state) {
        Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.client.ClientRailway.accept(state));
    }

    @Override
    public void acceptHandheldNetwork(RailNetwork net) {
        Minecraft.getMinecraft().addScheduledTask(() -> HandheldMap.accept(net));
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != RailMap.GUI_DISPATCHER_BOARD) return null;
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        return te instanceof TileRailDisplay d ? new GuiDispatcherBoard(d) : null;
    }
}
