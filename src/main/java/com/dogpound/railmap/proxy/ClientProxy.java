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
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.signs.SignRender());
        ClientRegistry.bindTileEntitySpecialRenderer(TileDisplayPanel.class, new DisplayPanelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.program.TileNXDesk.class, new com.dogpound.railmap.client.render.NXDeskRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.program.TileControlDesk.class, new com.dogpound.railmap.client.render.ControlDeskRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.station.TileStationPiece.class, new com.dogpound.railmap.client.render.StationPieceRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.grid.TileGrid.class, new com.dogpound.railmap.client.render.FeederCableRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.grid.TileInstrument.class, new com.dogpound.railmap.client.render.InstrumentRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileRailSign.class, new com.dogpound.railmap.client.render.RailSignRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileWayside.Scale.class,
                new com.dogpound.railmap.client.render.WaysideRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileWayside.Aei.class,
                new com.dogpound.railmap.client.render.WaysideRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileFreightTerminal.Loader.class,
                new com.dogpound.railmap.client.render.FreightTerminalRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileFreightTerminal.Unloader.class,
                new com.dogpound.railmap.client.render.FreightTerminalRenderer());
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
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.signal.TileLineside.class,
                new com.dogpound.railmap.client.render.LinesideRenderer());
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent e) {
        for (net.minecraft.block.Block b : ALL_BLOCKS) {
            if (b instanceof com.dogpound.railmap.grid.BlockGrid && ((com.dogpound.railmap.grid.BlockGrid) b).kind == com.dogpound.railmap.grid.GridKind.CABLE) continue;   // icon per conductor below
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0,
                    new ModelResourceLocation(b.getRegistryName(), "inventory"));
        }
        // Power Cable: the inventory icon follows the conductor on the stack (look x thickness)
        for (net.minecraft.block.Block b : com.dogpound.railmap.proxy.CommonProxy.ALL_BLOCKS) {
            if (!(b instanceof com.dogpound.railmap.grid.BlockGrid) || ((com.dogpound.railmap.grid.BlockGrid) b).kind != com.dogpound.railmap.grid.GridKind.CABLE) continue;
            Item it = Item.getItemFromBlock(b);
            java.util.List<net.minecraft.client.renderer.block.model.ModelResourceLocation> all = new java.util.ArrayList<>();
            for (com.dogpound.railmap.grid.BlockGrid.Look l : com.dogpound.railmap.grid.BlockGrid.Look.values())
                for (com.dogpound.railmap.grid.BlockGrid.Gauge g : com.dogpound.railmap.grid.BlockGrid.Gauge.values())
                    all.add(new net.minecraft.client.renderer.block.model.ModelResourceLocation(com.dogpound.railmap.RailMap.MODID + ":cable_item_" + l.getName() + "_" + g.getName(), "inventory"));
            net.minecraft.client.renderer.block.model.ModelBakery.registerItemVariants(it, all.toArray(new net.minecraft.util.ResourceLocation[0]));
            ModelLoader.setCustomMeshDefinition(it, st -> {
                com.dogpound.railmap.grid.Elec.Spec sp = com.dogpound.railmap.grid.ItemPowerCable.spec(st);
                return new net.minecraft.client.renderer.block.model.ModelResourceLocation(com.dogpound.railmap.RailMap.MODID + ":cable_item_"
                        + com.dogpound.railmap.grid.BlockGrid.Look.of(sp).getName() + "_" + com.dogpound.railmap.grid.BlockGrid.Gauge.of(sp).getName(), "inventory");
            });
        }
        ModelLoader.setCustomModelResourceLocation(multimeter, 0, new ModelResourceLocation(multimeter.getRegistryName(), "inventory"));
        for (net.minecraft.item.Item it : new net.minecraft.item.Item[]{com.dogpound.railmap.proxy.CommonProxy.screwdriver, com.dogpound.railmap.proxy.CommonProxy.padlock, com.dogpound.railmap.proxy.CommonProxy.substationKit})
            ModelLoader.setCustomModelResourceLocation(it, 0, new ModelResourceLocation(it.getRegistryName(), "inventory"));
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
    public void settingsData(com.dogpound.railmap.network.PacketSettings msg) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof com.dogpound.railmap.client.gui.GuiBlockSettings g && g.pos.equals(msg.pos)) g.accept(msg);
            else if (mc.currentScreen == null) mc.displayGuiScreen(new com.dogpound.railmap.client.gui.GuiBlockSettings(msg));
        });
    }

    @Override
    public void openScaleGui(net.minecraft.util.math.BlockPos pos, String what, float scale) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                new com.dogpound.railmap.client.gui.GuiScale(pos, what, scale));
    }

    @Override
    public void openCableSpec(net.minecraft.util.EnumHand hand, com.dogpound.railmap.grid.Elec.Spec spec) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new com.dogpound.railmap.client.gui.GuiCableSpec(hand, spec));
    }

    @Override
    public void openSignTextGui(net.minecraft.util.math.BlockPos pos, String text) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                new com.dogpound.railmap.client.gui.GuiSignText(pos, text));
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
        if (id != RailMap.GUI_DISPATCHER_BOARD && id != RailMap.GUI_SIGNAL_BOX) return null;
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        return te instanceof TileRailDisplay d ? new GuiDispatcherBoard(d, id == RailMap.GUI_SIGNAL_BOX) : null;
    }
}
