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
    public static Item waybill;
    public static Item container;
    /** Every block this mod registers, in order, so item registration follows automatically. */
    public static net.minecraft.item.Item multimeter;
    public static net.minecraft.item.Item screwdriver, padlock, substationKit, instrumentWire, part;
    public static net.minecraft.item.Item spare;   // depot spares (depot.ItemSpare)
    public static net.minecraft.item.Item workPermit, lotoKey, studyLaptop;   // protection + permits (grid/)
    public static net.minecraft.item.Item[] trackItems = new net.minecraft.item.Item[0];   // track/ tools
    public static Block instrumentWireBlock;
    public static Block consolePart;
    public static Block officePart;
    public static final java.util.List<Block> ALL_BLOCKS = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        ALL_BLOCKS.clear();
        dispatcherBoard = add(new com.dogpound.railmap.block.BlockControlConsole());   // the Control Center console (was the flat board)
        consolePart = add(new com.dogpound.railmap.block.BlockConsolePart());
        officePart = add(new com.dogpound.railmap.block.BlockOfficePart());
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
        // instrument wiring: wires, bundled cable, taps, radios, gauge panels
        instrumentWireBlock = add(new com.dogpound.railmap.wire.BlockWire(false));
        add(new com.dogpound.railmap.wire.BlockWire(true));
        add(new com.dogpound.railmap.wire.BlockTap());
        add(new com.dogpound.railmap.wire.BlockRadio(false));
        add(new com.dogpound.railmap.wire.BlockRadio(true));
        add(new com.dogpound.railmap.wire.BlockGaugePanel());
        add(new com.dogpound.railmap.grid.BlockWindTower());
        add(new com.dogpound.railmap.wire.BlockWireChase());
        // stations: every piece in four styles + the Station Master's Desk
        for (String style : com.dogpound.railmap.station.BlockStationPiece.STYLES)
            for (com.dogpound.railmap.station.BlockStationPiece.Kind k : com.dogpound.railmap.station.BlockStationPiece.Kind.values())
                add(new com.dogpound.railmap.station.BlockStationPiece(style, k));
        add(new com.dogpound.railmap.station.BlockStationMaster());
        add(new com.dogpound.railmap.program.BlockChannelIO());
        add(new com.dogpound.railmap.block.BlockSignalBox());
        add(new com.dogpound.railmap.block.BlockFreightOffice());
        for (com.dogpound.railmap.block.BlockEconomyDesk.Kind k : com.dogpound.railmap.block.BlockEconomyDesk.Kind.values()) add(new com.dogpound.railmap.block.BlockEconomyDesk(k));   // her list #46-50
        add(new com.dogpound.railmap.alarm.BlockAnnunciator());
        add(new com.dogpound.railmap.grid.BlockPermitBoard());
        add(new com.dogpound.railmap.alarm.BlockEventRecorder());
        add(new com.dogpound.railmap.yard.BlockYardPanel());        // train sim: hump yard control panel
        add(new com.dogpound.railmap.yard.BlockLocoDiag());         // train sim: locomotive diagnostic panel
        add(new com.dogpound.railmap.vehicle.BlockFormationDesk()); // her list 107 + 110: formation & livery desk
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
        for (Block b : com.dogpound.railmap.safety.SafetyBlocks.create()) add(b);   // signalling safety (#19-28 + fault lab)
        for (Block b : com.dogpound.railmap.safety.SigBlocks.create()) add(b);      // signalling batch 3 (#11-20, #46, #48, #49)
        for (Block b : com.dogpound.railmap.depot.DepotBlocks.create()) add(b);          // depots + rolling stock (her list section 8, #83, #84)
        for (Block b : com.dogpound.railmap.steam.SteamBlocks.create()) add(b);          // batch 5 #2: steam servicing depot
        for (Block b : com.dogpound.railmap.traction.TractionBlocks.create()) add(b);    // batch 5 #6: charging post + hydrogen station
        for (Block b : com.dogpound.railmap.crew.CrewBlocks.create()) add(b);            // train crew NPCs + revenue protection (batch 5 #7 #9)
        for (Block b : com.dogpound.railmap.industry.IndustryBlocks.create()) add(b);    // industry integration: construction projects (#95)
        for (Block b : com.dogpound.railmap.station.StationFxBlocks.create()) add(b);    // station access, platforms, facilities (#81, #82, #85)
        for (Block b : com.dogpound.railmap.track.TrackBlocks.create()) add(b);     // track infrastructure (#13-18 + tunnel building)
        for (Block b : com.dogpound.railmap.incident.IncidentBlocks.create()) add(b);
        for (Block b : com.dogpound.railmap.planning.PlanBlocks.create()) add(b);   // operations planning desk (next-48 #1 3 6 7 40 42 46 48)
        add(com.dogpound.railmap.admin.Admin.block());                              // railway administration console (#111-120)   // emergency response + control room (#67-70, #72-80)
        for (Block b : com.dogpound.railmap.ohle.OhleBlocks.create()) add(b);
        for (Block b : com.dogpound.railmap.shop.ShopBlocks.create()) add(b);       // station shops: newsstand, coffee kiosk, ticket office (#5)
        for (Block b : com.dogpound.railmap.ems.EmsBlocks.create()) add(b);         // emergency services: fire / ambulance / police stations, cordon (#6)
        for (Block b : com.dogpound.railmap.road.RoadBlocks.create()) add(b);       // batch 5 #3 road traffic + #4 trams: signals, controller, loops, tram stop, validator, edge, pole, tram road
        for (Block b : com.dogpound.railmap.stn.StnBlocks.create()) add(b);         // station operations + cargo care (ChatGPT batch 3 #21-35)       // overhead line #31-32 + weather #63-66
        for (Block b : com.dogpound.railmap.stnlife6.LifeBlocks.create()) add(b);   // batch 6c #11-15: bus station, taxi rank, hotel, pub, boarding lift + gap filler
        for (Block b : com.dogpound.railmap.logic.LogicBlocks.create()) add(b);     // railway logic modules (#99)
        for (Block b : com.dogpound.railmap.asset.AssetBlocks.create()) add(b);     // assets + maintenance: commissioning, reman, loco works (48-next list)
        for (Block b : com.dogpound.railmap.build6a.B6Blocks.create()) add(b);      // batch 6a: track layer, TBM, tamper, loco works + bays
        for (Block b : com.dogpound.railmap.ctl.CtlBlocks.create()) add(b);         // next-48: grid, control + comms (#20 #21 #23 #25 #30 #43)
        for (Block b : com.dogpound.railmap.light.LightBlocks.create()) add(b);     // night lighting: luminaires, floodlight masts, photocell, contactor panel
        for (Block b : com.dogpound.railmap.travel6.TravelBlocks.create()) add(b);  // batch 6b #6-10: Pride Card, left luggage, baggage, park & ride

        for (Block b : ALL_BLOCKS) e.getRegistry().register(b);

        GameRegistry.registerTileEntity(com.dogpound.railmap.grid.TileInstrument.class, new ResourceLocation(RailMap.MODID, "grid_instrument"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.wire.TileWire.class, new ResourceLocation(RailMap.MODID, "instrument_wire"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.wire.TileTap.class, new ResourceLocation(RailMap.MODID, "grid_tap"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.wire.TileRadio.class, new ResourceLocation(RailMap.MODID, "radio"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.wire.TileGaugePanel.class, new ResourceLocation(RailMap.MODID, "gauge_panel"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.wire.TileWireChase.class, new ResourceLocation(RailMap.MODID, "wire_chase"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.program.TileNXDesk.class, new ResourceLocation(RailMap.MODID, "nx_desk"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.program.TileControlDesk.class, new ResourceLocation(RailMap.MODID, "control_desk"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.program.TileChannelIO.class, new ResourceLocation(RailMap.MODID, "channel_io"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileFreightOffice.class, new ResourceLocation(RailMap.MODID, "freight_office"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileCompanyHQ.class, new ResourceLocation(RailMap.MODID, "company_hq"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileExchange.class, new ResourceLocation(RailMap.MODID, "commodity_exchange"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileTravelCentre.class, new ResourceLocation(RailMap.MODID, "travel_centre"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileContainerTerminal.class, new ResourceLocation(RailMap.MODID, "container_terminal"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.biz.TilePortBerth.class, new ResourceLocation(RailMap.MODID, "port_berth"));            // world + business (#15)
        GameRegistry.registerTileEntity(com.dogpound.railmap.biz.TileStockBoard.class, new ResourceLocation(RailMap.MODID, "stock_exchange"));        // #36
        GameRegistry.registerTileEntity(com.dogpound.railmap.biz.TileRegulatorOffice.class, new ResourceLocation(RailMap.MODID, "regulator_office")); // #34 + #12
        GameRegistry.registerTileEntity(com.dogpound.railmap.biz.TileInsuranceDesk.class, new ResourceLocation(RailMap.MODID, "insurance_desk"));     // #33
        GameRegistry.registerTileEntity(com.dogpound.railmap.biz.TilePlanningBoard.class, new ResourceLocation(RailMap.MODID, "planning_board"));     // #13 #18 #38
        GameRegistry.registerTileEntity(com.dogpound.railmap.alarm.TileAnnunciator.class, new ResourceLocation(RailMap.MODID, "annunciator"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.grid.TilePermitBoard.class, new ResourceLocation(RailMap.MODID, "permit_board"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.alarm.TileEventRecorder.class, new ResourceLocation(RailMap.MODID, "event_recorder"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.yard.TileYardPanel.class, new ResourceLocation(RailMap.MODID, "yard_panel"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.yard.TileLocoDiag.class, new ResourceLocation(RailMap.MODID, "loco_diag"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.vehicle.TileFormationDesk.class, new ResourceLocation(RailMap.MODID, "formation_desk"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.industry.TileIndustry.class, new ResourceLocation(RailMap.MODID, "industry"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.station.TileStationPiece.class, new ResourceLocation(RailMap.MODID, "station_piece"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.station.TileStationMaster.class, new ResourceLocation(RailMap.MODID, "station_master"));
        GameRegistry.registerTileEntity(TileDispatcherBoard.class, new ResourceLocation(RailMap.MODID, "dispatcher_board"));
        GameRegistry.registerTileEntity(com.dogpound.railmap.block.TileSignalBox.class, new ResourceLocation(RailMap.MODID, "signal_box_frame"));
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
        com.dogpound.railmap.safety.SafetyBlocks.registerTiles();
        com.dogpound.railmap.safety.SigBlocks.registerTiles();                     // signalling batch 3
        com.dogpound.railmap.depot.DepotBlocks.registerTiles();
        com.dogpound.railmap.steam.SteamBlocks.registerTiles();                    // batch 5 #2
        com.dogpound.railmap.traction.TractionBlocks.registerTiles();              // batch 5 #6
        com.dogpound.railmap.crew.CrewBlocks.registerTiles();                      // batch 5 #7 #9
        com.dogpound.railmap.industry.IndustryBlocks.registerTiles();
        com.dogpound.railmap.track.TrackBlocks.registerTiles();
        com.dogpound.railmap.incident.IncidentBlocks.registerTiles();
        com.dogpound.railmap.planning.PlanBlocks.registerTiles();
        com.dogpound.railmap.admin.Admin.registerTiles();
        com.dogpound.railmap.ohle.OhleBlocks.registerTiles();
        com.dogpound.railmap.shop.ShopBlocks.registerTiles();                       // station shops (#5)
        com.dogpound.railmap.ems.EmsBlocks.registerTiles();                         // emergency services (#6)
        com.dogpound.railmap.road.RoadBlocks.registerTiles();                       // batch 5 #3 road traffic + #4 trams
        com.dogpound.railmap.stn.StnBlocks.registerTiles();                         // ChatGPT batch 3 #21-35
        com.dogpound.railmap.stnlife6.LifeBlocks.registerTiles();                  // batch 6c #11-15 station life
        com.dogpound.railmap.logic.LogicBlocks.registerTiles();                    // railway logic modules (#99)
        com.dogpound.railmap.asset.AssetBlocks.registerTiles();                    // assets + maintenance (48-next list)
        com.dogpound.railmap.build6a.B6Blocks.registerTiles();                     // batch 6a construction machines
        com.dogpound.railmap.ctl.CtlBlocks.registerTiles();                        // next-48: grid, control + comms
        com.dogpound.railmap.light.LightBlocks.registerTiles();                    // night lighting
        com.dogpound.railmap.travel6.TravelBlocks.registerTiles();                 // batch 6b #6-10
    }

    private static Block add(Block b) {
        ALL_BLOCKS.add(b);
        return b;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (Block b : ALL_BLOCKS) {
            if (b == instrumentWireBlock || b == consolePart || b == officePart) continue;                                   // placed by the Instrument Wire item
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
        workPermit = new com.dogpound.railmap.grid.ItemPermit();
        lotoKey = new com.dogpound.railmap.grid.ItemLotoKey();
        studyLaptop = new com.dogpound.railmap.grid.ItemStudyLaptop();
        e.getRegistry().registerAll(workPermit, lotoKey, studyLaptop);
        instrumentWire = new com.dogpound.railmap.wire.ItemWire();
        e.getRegistry().register(instrumentWire);
        part = new com.dogpound.railmap.item.ItemPart();
        e.getRegistry().register(part);
        ticket = new com.dogpound.railmap.item.ItemTicket();
        e.getRegistry().register(ticket);
        waybill = new com.dogpound.railmap.item.ItemWaybill();
        e.getRegistry().register(waybill);
        container = new com.dogpound.railmap.item.ItemContainer();
        e.getRegistry().register(container);
        spare = new com.dogpound.railmap.depot.ItemSpare();   // depot spares (her list #53)
        e.getRegistry().register(spare);
        trackItems = com.dogpound.railmap.track.TrackBlocks.items();          // track trolley, maintenance kit, surveyor (#13, #18)
        e.getRegistry().registerAll(trackItems);
        e.getRegistry().registerAll(com.dogpound.railmap.asset.AssetBlocks.items());   // inspection instruments (48-next #27)
    }

    public void preInit() {}

    /** Client-only hooks; no-ops on the dedicated server. */
    public void acceptTrains(int dim, List<TrainNode> trains) {}

    public void acceptHandheldNetwork(RailNetwork net) {}

    public void openTicketMachine(com.dogpound.railmap.network.PacketTicketMenu menu) {}

    public void acceptRailState(com.dogpound.railmap.network.PacketRailState state) {}

    public void acceptCab(com.dogpound.railmap.network.PacketCab p) {}

    public void openScaleGui(net.minecraft.util.math.BlockPos pos, String what, float scale) {}

    /** Settings Console data arrived (client only) */
    public void settingsData(com.dogpound.railmap.network.PacketSettings msg) {}
    public void openSignTextGui(net.minecraft.util.math.BlockPos pos, String text) {}

    public void openCableSpec(net.minecraft.util.EnumHand hand, com.dogpound.railmap.grid.Elec.Spec spec) {}

    /** The board GUI is a plain GuiScreen: no container, so the server side has nothing to open. */
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id >= RailMap.GUI_OFFICE_STORE && world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z)) instanceof com.dogpound.railmap.block.IFurniture o) {
            net.minecraft.inventory.IInventory inv = o.storage(id - RailMap.GUI_OFFICE_STORE);
            return inv == null ? null : new net.minecraft.inventory.ContainerChest(player.inventory, inv, player);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
