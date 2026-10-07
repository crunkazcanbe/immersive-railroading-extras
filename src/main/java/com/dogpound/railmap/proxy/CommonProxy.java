package com.dogpound.railmap.proxy;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.BlockDispatcherBoard;
import com.dogpound.railmap.block.BlockDisplayPanel;
import com.dogpound.railmap.block.TileDispatcherBoard;
import com.dogpound.railmap.block.TileDisplayPanel;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.item.ItemRailMap;
import com.dogpound.railmap.item.ItemSignalWrench;
import com.dogpound.railmap.signal.BlockCrossing;
import com.dogpound.railmap.signal.BlockLineside;
import com.dogpound.railmap.signal.BlockRelayCase;
import com.dogpound.railmap.signal.BlockSignalBridge;
import com.dogpound.railmap.signal.BlockSignalMast;
import com.dogpound.railmap.signal.BlockTrackCircuit;
import com.dogpound.railmap.signal.SignalStyle;
import com.dogpound.railmap.signal.TileCrossing;
import com.dogpound.railmap.signal.TileRelayCase;
import com.dogpound.railmap.signal.TileSignalBridge;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.List;

/** Server-safe half: block/item/tile registration and the (screen-only) GUI handler. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class CommonProxy implements IGuiHandler {
    public static Block dispatcherBoard;
    public static Block displayPanel;
    public static Block gantryLeg;
    /** The real structure a paired gantry builds: corner legs you climb, walkway you cross. */
    public static Block gantryFrame;
    public static Block gantryDeck;
    public static Block gantryRail;
    public static Block gantryAntenna;
    public static Item railMap;
    public static Item signalWrench;
    public static Item ticket;
    /** Every block this mod registers, in order, so item registration follows automatically. */
    public static net.minecraft.item.Item multimeter;
    public static net.minecraft.item.Item screwdriver, padlock, substationKit;
    public static final java.util.List<Block> ALL_BLOCKS = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        ALL_BLOCKS.clear();
        dispatcherBoard = add(new BlockDispatcherBoard());
        displayPanel = add(new BlockDisplayPanel());
        // Signals: one block per American family, all driven by the same block logic.
        for (SignalStyle style : SignalStyle.values()) add(new BlockSignalMast(style));
        add(new BlockSignalBridge());   // the gantry: two legs that span themselves
        gantryFrame = add(new com.dogpound.railmap.signal.BlockGantryFrame());
        gantryDeck  = add(new com.dogpound.railmap.signal.BlockGantryDeck());
        gantryRail  = add(new com.dogpound.railmap.signal.BlockGantryRail());
        gantryAntenna = add(new com.dogpound.railmap.signal.BlockGantryAntenna());
        // What goes under the rail.
        add(new BlockTrackCircuit(true));    // insulated joint: block boundary
        add(new BlockTrackCircuit(false));   // track circuit: detector + redstone out
        add(new BlockRelayCase());           // where redstone meets the signalling
        // Grade crossings.
        for (TileCrossing.Kind k : TileCrossing.Kind.values()) add(new BlockCrossing(k));
        // Lineside furniture.
        for (BlockLineside.Kind k : BlockLineside.Kind.values()) add(new BlockLineside(k));
        // Overhead line equipment.
        for (com.dogpound.railmap.signal.BlockCatenary.Kind k : com.dogpound.railmap.signal.BlockCatenary.Kind.values())
            add(new com.dogpound.railmap.signal.BlockCatenary(k));
        // Stations and operations.
        add(new com.dogpound.railmap.block.BlockTicketMachine());
        add(new com.dogpound.railmap.block.BlockArrivalsBoard());
        add(new com.dogpound.railmap.block.BlockDefectDetector());
        add(new com.dogpound.railmap.block.BlockMaintenanceDepot());
        add(new com.dogpound.railmap.block.BlockPowerPost(com.dogpound.railmap.block.TilePowerPost.Kind.SUBSTATION));
        add(new com.dogpound.railmap.block.BlockPowerPost(com.dogpound.railmap.block.TilePowerPost.Kind.CHARGER));
        add(new com.dogpound.railmap.block.BlockDataLink());
        // Freight terminal: getting goods on and off the train.
        add(new com.dogpound.railmap.block.BlockFreightTerminal(true));   // loading silo
        add(new com.dogpound.railmap.block.BlockFreightTerminal(false));  // unloading pit
        gantryLeg = add(new com.dogpound.railmap.block.BlockGantryLeg());
        // Passenger side of the station.
        add(new com.dogpound.railmap.block.BlockTicketGate());
        add(new com.dogpound.railmap.block.BlockPaSpeaker());
        add(new com.dogpound.railmap.doors.BlockStationDoors());
        for (com.dogpound.railmap.grid.GridKind k : com.dogpound.railmap.grid.GridKind.values()) add(new com.dogpound.railmap.grid.BlockGrid(k));
        for (com.dogpound.railmap.grid.BlockInstrument.Inst i : com.dogpound.railmap.grid.BlockInstrument.Inst.values()) add(new com.dogpound.railmap.grid.BlockInstrument(i));
        // stations: every piece in four styles + the Station Master's Desk
        for (String style : com.dogpound.railmap.station.BlockStationPiece.STYLES)
            for (com.dogpound.railmap.station.BlockStationPiece.Kind k : com.dogpound.railmap.station.BlockStationPiece.Kind.values())
                add(new com.dogpound.railmap.station.BlockStationPiece(style, k));
        add(new com.dogpound.railmap.station.BlockStationMaster());
        add(new com.dogpound.railmap.program.BlockChannelIO());
        add(new com.dogpound.railmap.block.BlockSignalBox());
        add(new com.dogpound.railmap.program.BlockControlDesk());
        add(new com.dogpound.railmap.program.BlockNXDesk());
        for (com.dogpound.railmap.industry.IndustryKind k : com.dogpound.railmap.industry.IndustryKind.values())
            add(new com.dogpound.railmap.industry.BlockIndustry(k));
        add(new com.dogpound.railmap.doors.BlockPlatformDoor(false));
        add(new com.dogpound.railmap.doors.BlockPlatformDoor(true));
        // Measuring devices in the rail.
        add(new com.dogpound.railmap.block.BlockWayside(com.dogpound.railmap.block.TileWayside.Kind.SCALE));
        add(new com.dogpound.railmap.block.BlockWayside(com.dogpound.railmap.block.TileWayside.Kind.AEI));
        add(new com.dogpound.railmap.signal.BlockRailSign());

        for (Block b : ALL_BLOCKS) e.getRegistry().register(b);

        GameRegistry.registerTileEntity(com.dogpound.railmap.grid.TileInstrument.class, new ResourceLocation(RailMap.MODID, "grid_instrument"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.program.TileNXDesk.class, new ResourceLocation(RailMap.MODID, "nx_desk"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.program.TileControlDesk.class, new ResourceLocation(RailMap.MODID, "control_desk"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.program.TileChannelIO.class, new ResourceLocation(RailMap.MODID, "channel_io"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.industry.TileIndustry.class, new ResourceLocation(RailMap.MODID, "industry"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.station.TileStationPiece.class, new ResourceLocation(RailMap.MODID, "station_piece"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.station.TileStationMaster.class, new ResourceLocation(RailMap.MODID, "station_master"));
        GameRegistry.registerTileEntity(TileDispatcherBoard.class, new ResourceLocation(RailMap.MODID, "dispatcher_board"));
        GameRegistry.registerTileEntity(TileDisplayPanel.class, new ResourceLocation(RailMap.MODID, "display_panel"));
        GameRegistry.registerTileEntity(TileSignalMast.class, new ResourceLocation(RailMap.MODID, "signal_mast"));
        GameRegistry.registerTileEntity(TileSignalBridge.class, new ResourceLocation(RailMap.MODID, "signal_bridge"));
        GameRegistry.registerTileEntity(TileRelayCase.class, new ResourceLocation(RailMap.MODID, "relay_case"));
        GameRegistry.registerTileEntity(TileTrackCircuit.Joint.class, new ResourceLocation(RailMap.MODID, "insulated_joint"));
        GameRegistry.registerTileEntity(TileTrackCircuit.Circuit.class, new ResourceLocation(RailMap.MODID, "track_circuit"));
        GameRegistry.registerTileEntity(TileCrossing.Signal.class, new ResourceLocation(RailMap.MODID, "crossing_signal"));
        GameRegistry.registerTileEntity(TileCrossing.Gate.class, new ResourceLocation(RailMap.MODID, "crossing_gate"));
        GameRegistry.registerTileEntity(TileCrossing.Cantilever.class, new ResourceLocation(RailMap.MODID, "crossing_cantilever"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.signal.TileSpeedSign.class, new ResourceLocation(RailMap.MODID, "speed_sign"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.signal.TileLineside.class, new ResourceLocation(RailMap.MODID, "lineside"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileTicketMachine.class, new ResourceLocation(RailMap.MODID, "ticket_machine"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileArrivalsBoard.class, new ResourceLocation(RailMap.MODID, "arrivals_board"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileDefectDetector.class, new ResourceLocation(RailMap.MODID, "defect_detector"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileFreightTerminal.Loader.class, new ResourceLocation(RailMap.MODID, "loading_silo"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileFreightTerminal.Unloader.class, new ResourceLocation(RailMap.MODID, "unloading_pit"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileTicketGate.class, new ResourceLocation(RailMap.MODID, "ticket_gate"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TilePaSpeaker.class, new ResourceLocation(RailMap.MODID, "pa_speaker"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.grid.TileGrid.class, new ResourceLocation(RailMap.MODID, "grid_machine"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.doors.TileStationDoors.class, new ResourceLocation(RailMap.MODID, "station_doors"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileWayside.Scale.class, new ResourceLocation(RailMap.MODID, "track_scale"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileWayside.Aei.class, new ResourceLocation(RailMap.MODID, "aei_reader"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.signal.TileRailSign.class, new ResourceLocation(RailMap.MODID, "rail_sign"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileDataLink.class, new ResourceLocation(RailMap.MODID, "data_link"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileMaintenanceDepot.class, new ResourceLocation(RailMap.MODID, "maintenance_depot"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TilePowerPost.class, new ResourceLocation(RailMap.MODID, "power_post"));
    }

    private static Block add(Block b) {
        ALL_BLOCKS.add(b);
        return b;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (Block b : ALL_BLOCKS) {
            boolean cable = b instanceof com.dogpound.railmap.grid.BlockGrid && ((com.dogpound.railmap.grid.BlockGrid) b).kind == com.dogpound.railmap.grid.GridKind.CABLE;
            e.getRegistry().register((cable ? new com.dogpound.railmap.grid.ItemPowerCable(b) : new ItemBlock(b)).setRegistryName(b.getRegistryName()));
        }
        railMap = new ItemRailMap();
        e.getRegistry().register(railMap);
        signalWrench = new ItemSignalWrench();
        e.getRegistry().register(signalWrench);
        multimeter = new com.dogpound.railmap.grid.ItemMultimeter();
        e.getRegistry().register(multimeter);
        screwdriver = new com.dogpound.railmap.grid.ItemScrewdriver();
        padlock = new com.dogpound.railmap.grid.ItemPadlock();
        substationKit = new com.dogpound.railmap.grid.ItemSubstationKit();
        e.getRegistry().registerAll(screwdriver, padlock, substationKit);
        ticket = new com.dogpound.railmap.item.ItemTicket();
        e.getRegistry().register(ticket);
    }

    public void preInit() {}

    /** Client-only hooks; no-ops on the dedicated server. */
    public void acceptTrains(int dim, List<TrainNode> trains) {}

    public void acceptHandheldNetwork(RailNetwork net) {}

    public void openTicketMachine(com.dogpound.railmap.network.PacketTicketMenu menu) {}

    public void acceptRailState(com.dogpound.railmap.network.PacketRailState state) {}

    public void openScaleGui(net.minecraft.util.math.BlockPos pos, String what, float scale) {}

    /** Settings Console data arrived (client only) */
    public void settingsData(com.dogpound.railmap.network.PacketSettings msg) {}
    public void openSignTextGui(net.minecraft.util.math.BlockPos pos, String text) {}

    public void openCableSpec(net.minecraft.util.EnumHand hand, com.dogpound.railmap.grid.Elec.Spec spec) {}

    /** The board GUI is a plain GuiScreen: no container, so the server side has nothing to open. */
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
