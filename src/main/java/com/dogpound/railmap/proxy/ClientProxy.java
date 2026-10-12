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
        com.dogpound.railmap.pax.client.PaxRender.register();   // visible passengers: renderer + look tooltip
        com.dogpound.railmap.ems.client.EmsClient.init();        // emergency vehicles + station shop shelves (#5, #6)
        com.dogpound.railmap.road.client.RoadClient.init();      // batch 5 #3: road vehicles renderer
        com.dogpound.railmap.build6a.client.B6Client.init();     // batch 6a: hi-rail truck renderer + cab panel
        com.dogpound.railmap.drone.client.DroneClient.init();   // IR Extras #2 + #4: drone renderer, weather depot desk shelf
        MinecraftForge.EVENT_BUS.register(com.dogpound.railmap.client.CabDisplay.class);
        MinecraftForge.EVENT_BUS.register(com.dogpound.railmap.client.GridSounds.class);
        com.dogpound.railmap.client.CabDisplay.initKeys();
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.signs.SignRender());
        ClientRegistry.bindTileEntitySpecialRenderer(TileDisplayPanel.class, new DisplayPanelRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileFreightOffice.class, new com.dogpound.railmap.client.render.FreightOfficeRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.grid.TilePermitBoard.class, new com.dogpound.railmap.client.render.PermitBoardRenderer());
        for (Class<? extends com.dogpound.railmap.block.TileFurniture> c : java.util.Arrays.asList(com.dogpound.railmap.block.TileCompanyHQ.class, com.dogpound.railmap.block.TileExchange.class, com.dogpound.railmap.block.TileTravelCentre.class))
            ClientRegistry.bindTileEntitySpecialRenderer(c, new com.dogpound.railmap.client.render.ShelfRenderer());
        for (Class<? extends com.dogpound.railmap.block.TileFurniture> c : java.util.Arrays.asList(com.dogpound.railmap.depot.TileDepotOffice.class, com.dogpound.railmap.depot.TileToolCrib.class,
                com.dogpound.railmap.depot.TileCrewDesk.class, com.dogpound.railmap.station.TileKiosk.class))   // depot + station furniture shelves
            ClientRegistry.bindTileEntitySpecialRenderer(c, new com.dogpound.railmap.client.render.ShelfRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.incident.TileIncidentDesk.class, new com.dogpound.railmap.client.render.ShelfRenderer());   // incident desk shelf
        for (Class<? extends com.dogpound.railmap.block.TileFurniture> c : java.util.Arrays.asList(com.dogpound.railmap.crew.TileCrewOffice.class, com.dogpound.railmap.crew.TileRevenueOffice.class))   // crew office notice ledge + RP office ledger shelf (batch 5 #7 #9)
            ClientRegistry.bindTileEntitySpecialRenderer(c, new com.dogpound.railmap.client.render.ShelfRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.planning.TilePlanningDesk.class, new com.dogpound.railmap.client.render.ShelfRenderer());   // planning desk shelf
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.asset.TileLocoWorkshop.class, new com.dogpound.railmap.client.render.ShelfRenderer());   // loco works shelf (48-next #28)
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.vehicle.TileFormationDesk.class, new com.dogpound.railmap.client.render.ShelfRenderer());   // formation desk shelf
        for (Class<? extends com.dogpound.railmap.block.TileFurniture> c : java.util.Arrays.asList(com.dogpound.railmap.stn.TileLostProperty.class, com.dogpound.railmap.stn.TileParcelsOffice.class))   // lost property + parcels shelves (batch 3 #34)
            ClientRegistry.bindTileEntitySpecialRenderer(c, new com.dogpound.railmap.client.render.ShelfRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileContainerTerminal.class, new com.dogpound.railmap.client.render.ContainerCraneRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.track.TileMovableBridge.class, new com.dogpound.railmap.client.render.BasculeRenderer());   // batch 3 #8 bascule leaf
        for (Class<? extends com.dogpound.railmap.block.TileFurniture> c : java.util.Arrays.asList(com.dogpound.railmap.biz.TilePortBerth.class, com.dogpound.railmap.biz.TileStockBoard.class,
                com.dogpound.railmap.biz.TileRegulatorOffice.class, com.dogpound.railmap.biz.TileInsuranceDesk.class, com.dogpound.railmap.biz.TilePlanningBoard.class))   // world + business furniture shelves
            ClientRegistry.bindTileEntitySpecialRenderer(c, new com.dogpound.railmap.client.render.ShelfRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.block.TileDispatcherBoard.class, new com.dogpound.railmap.client.render.ConsoleRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.program.TileNXDesk.class, new com.dogpound.railmap.client.render.NXDeskRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.program.TileControlDesk.class, new com.dogpound.railmap.client.render.ControlDeskRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.station.TileStationPiece.class, new com.dogpound.railmap.client.render.StationPieceRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.grid.TileGrid.class, new com.dogpound.railmap.client.render.FeederCableRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.grid.TileInstrument.class, new com.dogpound.railmap.client.render.InstrumentRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.wire.TileWire.class, new com.dogpound.railmap.client.render.WireRenderers.Wire());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.wire.TileTap.class, new com.dogpound.railmap.client.render.WireRenderers.Tap());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.wire.TileRadio.class, new com.dogpound.railmap.client.render.WireRenderers.Radio());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.wire.TileGaugePanel.class, new com.dogpound.railmap.client.render.WireRenderers.Panel());
        ClientRegistry.bindTileEntitySpecialRenderer(com.dogpound.railmap.wire.TileWireChase.class, new com.dogpound.railmap.client.render.WireRenderers.Chase());
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
    public void itemColors(net.minecraftforge.client.event.ColorHandlerEvent.Item e) {
        e.getItemColors().registerItemColorHandler((s, tint) -> tint == 0 ? com.dogpound.railmap.wire.WireNet.rgb(com.dogpound.railmap.wire.ItemWire.colour(s)) : -1,
                com.dogpound.railmap.proxy.CommonProxy.instrumentWire);
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent e) {
        for (net.minecraft.block.Block b : ALL_BLOCKS) {
            if (b == com.dogpound.railmap.proxy.CommonProxy.instrumentWireBlock) continue;
            if (b == com.dogpound.railmap.proxy.CommonProxy.consolePart || b == com.dogpound.railmap.proxy.CommonProxy.officePart) continue;
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
        for (com.dogpound.railmap.asset.Ndt.Tool t : com.dogpound.railmap.asset.Ndt.Tool.values())   // inspection instruments (48-next #27)
            ModelLoader.setCustomModelResourceLocation(com.dogpound.railmap.asset.AssetBlocks.ndt, t.ordinal(), new ModelResourceLocation("irextras:" + t.id, "inventory"));
        for (com.dogpound.railmap.depot.ItemSpare.Spare sp : com.dogpound.railmap.depot.ItemSpare.Spare.values())   // depot spares
            ModelLoader.setCustomModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.spare, sp.ordinal(), new ModelResourceLocation("irextras:spare_" + sp.id, "inventory"));
        for (com.dogpound.railmap.item.ItemPart.Part pt : com.dogpound.railmap.item.ItemPart.Part.values())
            ModelLoader.setCustomModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.part, pt.ordinal(), new ModelResourceLocation("irextras:part_" + pt.id, "inventory"));
        for (int c = 0; c <= com.dogpound.railmap.wire.WireNet.POWER; c++)
            ModelLoader.setCustomModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.instrumentWire, c, new ModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.instrumentWire.getRegistryName(), "inventory"));
        for (net.minecraft.item.Item it : new net.minecraft.item.Item[]{com.dogpound.railmap.proxy.CommonProxy.workPermit, com.dogpound.railmap.proxy.CommonProxy.lotoKey, com.dogpound.railmap.proxy.CommonProxy.studyLaptop})
            ModelLoader.setCustomModelResourceLocation(it, 0, new ModelResourceLocation(it.getRegistryName(), "inventory"));
        for (net.minecraft.item.Item it : new net.minecraft.item.Item[]{com.dogpound.railmap.proxy.CommonProxy.screwdriver, com.dogpound.railmap.proxy.CommonProxy.padlock, com.dogpound.railmap.proxy.CommonProxy.substationKit})
            ModelLoader.setCustomModelResourceLocation(it, 0, new ModelResourceLocation(it.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(railMap, 0,
                new ModelResourceLocation(railMap.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(signalWrench, 0,
                new ModelResourceLocation(signalWrench.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(ticket, 0,
                new ModelResourceLocation(ticket.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.waybill, 0, new ModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.waybill.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.container, 0, new ModelResourceLocation(com.dogpound.railmap.proxy.CommonProxy.container.getRegistryName(), "inventory"));
        for (net.minecraft.item.Item it : com.dogpound.railmap.proxy.CommonProxy.trackItems)   // track/ tools
            ModelLoader.setCustomModelResourceLocation(it, 0, new ModelResourceLocation(it.getRegistryName(), "inventory"));
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
    public void acceptCab(com.dogpound.railmap.network.PacketCab p) {
        Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.client.CabDisplay.accept(p));
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
        if (id >= RailMap.GUI_OFFICE_STORE) {
            int k = id - RailMap.GUI_OFFICE_STORE;
            if (!(world.getTileEntity(new BlockPos(x, y, z)) instanceof com.dogpound.railmap.block.IFurniture o)) return null;
            return new net.minecraft.client.gui.inventory.GuiChest(player.inventory, new net.minecraft.inventory.InventoryBasic(o.storeName(k), true, o.storeSize(k)));
        }
        if (id != RailMap.GUI_DISPATCHER_BOARD && id != RailMap.GUI_SIGNAL_BOX) return null;
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        return te instanceof TileRailDisplay d ? new GuiDispatcherBoard(d, id == RailMap.GUI_SIGNAL_BOX) : null;
    }
}
