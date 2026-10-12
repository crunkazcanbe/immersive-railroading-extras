package com.dogpound.railmap;

import com.dogpound.railmap.dynmap.DynmapHook;
import com.dogpound.railmap.network.PacketAction;
import com.dogpound.railmap.network.PacketNetwork;
import com.dogpound.railmap.network.PacketRescan;
import com.dogpound.railmap.network.PacketTrains;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.server.Viewers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * RailMap: a live map of your Immersive Railroading network.
 * <ul>
 *   <li><b>Dispatcher Board</b> — desk console; right-click for the interactive map.</li>
 *   <li><b>Rail Display Panel</b> — wall screen; place several in a rectangle for one big screen.</li>
 *   <li><b>Rail Map</b> item — the same map in your hand, centred on you.</li>
 * </ul>
 * Shows track, switches (click to throw), signals (LandOfSignals), stops, player-named
 * stations with a timetable, live trains with speed/cargo/heading, block occupancy, and
 * mirrors it all onto Dynmap when present.
 */
@Mod(modid = RailMap.MODID, name = "Immersive Railroading Extras", version = "0.5.2",   // keep in step with mod_version in gradle.properties
        acceptableRemoteVersions = "*",
        dependencies = "required-after:universalmodcore;after:immersiverailroading;after:landofsignals;after:dynmap;after:opencomputers")
public class RailMap {
    public static final String MODID = "irextras";
    public static final int GUI_DISPATCHER_BOARD = 0;
    public static final int GUI_SIGNAL_BOX = 1;
    /** Freight Office storage: GUI_OFFICE_STORE + storage index (drawers, filing cabinet, cupboard) */
    public static final int GUI_OFFICE_STORE = 100;
    public static final Logger LOG = LogManager.getLogger("RailMap");

    @Mod.Instance(MODID)
    public static RailMap instance;

    @SidedProxy(clientSide = "com.dogpound.railmap.proxy.ClientProxy", serverSide = "com.dogpound.railmap.proxy.CommonProxy")
    public static CommonProxy proxy;

    /** the factory (formerly the Pride Industry mod): its own registration + renderers, see factory.Factory */
    @SidedProxy(clientSide = "com.dogpound.railmap.factory.proxy.ClientProxy", serverSide = "com.dogpound.railmap.factory.proxy.CommonProxy")
    public static com.dogpound.railmap.factory.proxy.CommonProxy factoryProxy;

    // factory fluids come in buckets; must be switched on statically, before pre-init
    static { net.minecraftforge.fluids.FluidRegistry.enableUniversalBucket(); }

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    /**
     * Null unless the dynmap mod is loaded. Deliberately typed as the {@link DynmapHook}
     * interface, NOT as the implementation: Forge's ProxyInjector enumerates every field on
     * this class, which loads each field's type — and naming DynmapBridge here crashed the
     * game with NoClassDefFoundError on any install without Dynmap.
     */
    public static DynmapHook dynmap;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NETWORK.registerMessage(PacketRescan.Handler.class, PacketRescan.class, 0, Side.SERVER);
        NETWORK.registerMessage(PacketTrains.Handler.class, PacketTrains.class, 1, Side.CLIENT);
        NETWORK.registerMessage(PacketAction.Handler.class, PacketAction.class, 2, Side.SERVER);
        NETWORK.registerMessage(PacketNetwork.Handler.class, PacketNetwork.class, 3, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketTicketMenu.Handler.class, com.dogpound.railmap.network.PacketTicketMenu.class, 4, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketTicketBuy.Handler.class, com.dogpound.railmap.network.PacketTicketBuy.class, 5, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketRailOps.Handler.class, com.dogpound.railmap.network.PacketRailOps.class, 6, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketRailState.Handler.class, com.dogpound.railmap.network.PacketRailState.class, 7, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketScale.Handler.class, com.dogpound.railmap.network.PacketScale.class, 8, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketSignText.Handler.class, com.dogpound.railmap.network.PacketSignText.class, 9, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketSettings.Handler.class, com.dogpound.railmap.network.PacketSettings.class, 10, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketSettings.Handler.class, com.dogpound.railmap.network.PacketSettings.class, 10, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.signs.PacketSigns.Handler.class, com.dogpound.railmap.signs.PacketSigns.class, 11, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.program.PacketProgram.Handler.class, com.dogpound.railmap.program.PacketProgram.class, 12, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.program.PacketProgram.Handler.class, com.dogpound.railmap.program.PacketProgram.class, 12, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.grid.PacketCableSpec.Handler.class, com.dogpound.railmap.grid.PacketCableSpec.class, 13, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketCab.Handler.class, com.dogpound.railmap.network.PacketCab.class, 14, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketConsole.Handler.class, com.dogpound.railmap.network.PacketConsole.class, 15, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketWired.Handler.class, com.dogpound.railmap.network.PacketWired.class, 16, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketWired.Handler.class, com.dogpound.railmap.network.PacketWired.class, 16, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketAlarmOp.Handler.class, com.dogpound.railmap.network.PacketAlarmOp.class, 17, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketReplay.Handler.class, com.dogpound.railmap.network.PacketReplay.class, 18, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketReplay.Handler.class, com.dogpound.railmap.network.PacketReplay.class, 18, Side.CLIENT);
        // train simulation (consist physics / engines / driver aids / yard): the cab acknowledge key. id 40, clear of 19+ used by others
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketCabAck.Handler.class, com.dogpound.railmap.network.PacketCabAck.class, 40, Side.SERVER);
        // her list 106-110: driver's display soft keys, vehicle appearance, Formation & Livery Desk
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketCabKey.Handler.class, com.dogpound.railmap.network.PacketCabKey.class, 90, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.network.PacketAppearance.Handler.class, com.dogpound.railmap.network.PacketAppearance.class, 91, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.vehicle.PacketFormation.Handler.class, com.dogpound.railmap.vehicle.PacketFormation.class, 92, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.vehicle.PacketFormation.Open.class, com.dogpound.railmap.vehicle.PacketFormation.OpenMsg.class, 93, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.safety.PacketSafetyOp.Handler.class, com.dogpound.railmap.safety.PacketSafetyOp.class, 52, Side.SERVER);   // console SAFETY tab
        NETWORK.registerMessage(com.dogpound.railmap.depot.PacketDepotOp.Handler.class, com.dogpound.railmap.depot.PacketDepotOp.class, 64, Side.SERVER);   // console DEPOT tab
        NETWORK.registerMessage(com.dogpound.railmap.asset.PacketAssetOp.Handler.class, com.dogpound.railmap.asset.PacketAssetOp.class, 117, Side.SERVER);   // console ASSETS tab (48-next list)
        NETWORK.registerMessage(com.dogpound.railmap.ohle.PacketOhleOp.Handler.class, com.dogpound.railmap.ohle.PacketOhleOp.class, 71, Side.SERVER);   // console OLE / WEATHER tab (ohle/, weather/)
        NETWORK.registerMessage(com.dogpound.railmap.incident.PacketIncidentOp.Handler.class, com.dogpound.railmap.incident.PacketIncidentOp.class, 61, Side.SERVER);   // console OPS tab (incident/)
        NETWORK.registerMessage(com.dogpound.railmap.api.PacketApiOp.Handler.class, com.dogpound.railmap.api.PacketApiOp.class, 80, Side.SERVER);   // console API tab (api/, #96-105)
        NETWORK.registerMessage(com.dogpound.railmap.biz.PacketBizOp.Handler.class, com.dogpound.railmap.biz.PacketBizOp.class, 113, Side.SERVER);   // console WORLD tab (biz/, next-48 #11-47 world + business)
        NETWORK.registerMessage(com.dogpound.railmap.ctl.PacketCtlOp.Handler.class, com.dogpound.railmap.ctl.PacketCtlOp.class, 125, Side.SERVER);   // console CTRL tab (ctl/, next-48 #20-44 grid / control / comms)
        NETWORK.registerMessage(com.dogpound.railmap.planning.PacketPlanOp.Handler.class, com.dogpound.railmap.planning.PacketPlanOp.class, 47, Side.SERVER);
        NETWORK.registerMessage(com.dogpound.railmap.os.PacketOs.Handler.class, com.dogpound.railmap.os.PacketOs.class, 211, Side.SERVER);   // Pride OS desk computers (os/)
        NETWORK.registerMessage(com.dogpound.railmap.os.PacketOs.Handler.class, com.dogpound.railmap.os.PacketOs.class, 211, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.interior.PacketInterior.Handler.class, com.dogpound.railmap.interior.PacketInterior.class, 247, Side.SERVER);   // walk-in train interiors (interior/)
        NETWORK.registerMessage(com.dogpound.railmap.interior.PacketInteriorState.Handler.class, com.dogpound.railmap.interior.PacketInteriorState.class, 248, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.cctv.PacketCctvOp.Handler.class, com.dogpound.railmap.cctv.PacketCctvOp.class, 231, Side.SERVER);      // CCTV (cctv/)
        NETWORK.registerMessage(com.dogpound.railmap.cctv.PacketCctvFrame.Handler.class, com.dogpound.railmap.cctv.PacketCctvFrame.class, 232, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.cctv.PacketCctvDir.Handler.class, com.dogpound.railmap.cctv.PacketCctvDir.class, 233, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.modelrail.PacketModelRail.Handler.class, com.dogpound.railmap.modelrail.PacketModelRail.class, 197, Side.SERVER);   // model railway layout editor (modelrail/)
        NETWORK.registerMessage(com.dogpound.railmap.mail.PacketMail.Handler.class, com.dogpound.railmap.mail.PacketMail.class, 183, Side.SERVER);   // letter writing (mail/, batch 5 #8)
        NETWORK.registerMessage(com.dogpound.railmap.travel6.PacketPhone.Handler.class, com.dogpound.railmap.travel6.PacketPhone.class, 157, Side.SERVER);   // Pride Rail phone app (travel6/, batch 6b #7)
        NETWORK.registerMessage(com.dogpound.railmap.travel6.PacketPhone.Handler.class, com.dogpound.railmap.travel6.PacketPhone.class, 157, Side.CLIENT);
        NETWORK.registerMessage(com.dogpound.railmap.stn.PacketStnOp.Handler.class, com.dogpound.railmap.stn.PacketStnOp.class, 167, Side.SERVER);   // console STN tab (stn/, ChatGPT batch 3 #21-35)   // console PLAN tab (planning/, next-48 #1 3 4 6 7 8 9 40 42 46 48)
        NETWORK.registerMessage(com.dogpound.railmap.build6a.B6Net.Handler.class, com.dogpound.railmap.build6a.B6Net.class, 236, Side.SERVER);   // batch 6a hi-rail truck cab panel
        NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
        MinecraftForge.EVENT_BUS.register(new TrainTracker());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.server.Wear());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.track.TrackCondition());   // #13 track quality + TSRs
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.server.Electric());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.doors.TrainDoors());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.signs.SignServer());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.grid.GridTicker());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.program.ProgramTicker());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.signal.SignalEngine());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.auto.Autopilot());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.auto.Protection());
        proxy.preInit();
        com.dogpound.railmap.factory.Factory.preInit(event.getModConfigurationDirectory());   // factory (ex Pride Industry)
    }

    /**
     * Wipe last session's state BEFORE the worlds load. This used to run in serverStarting, which
     * fires after the integrated server has loaded the worlds -- so every signal mast, crossing,
     * bridge, speed sign and joint that registered itself while loading was thrown away again,
     * and none of them ever reacted to a train.
     */
    @Mod.EventHandler
    public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent event) {
        if (net.minecraftforge.fml.common.Loader.isModLoaded("opencomputers")) com.dogpound.railmap.block.OCDisk.register();
        com.dogpound.railmap.factory.Factory.init();
    }

    @Mod.EventHandler
    public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent event) {
        com.dogpound.railmap.factory.Factory.postInit();   // ore dictionary, every mod's ores, factory self-test
    }

    @Mod.EventHandler
    public void serverAboutToStart(net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent event) {
        Viewers.clear();
        TrainTracker.clear();
        com.dogpound.railmap.signal.SignalRegistry.clear();
        com.dogpound.railmap.auto.Autopilot.clear();
        com.dogpound.railmap.auto.Protection.clear();
        com.dogpound.railmap.auto.Interlocking.clear();
        com.dogpound.railmap.auto.ArrivalEvents.clear();
        com.dogpound.railmap.safety.SafetyTicker.clear();
        com.dogpound.railmap.safety.SigTicker.clear();
        com.dogpound.railmap.depot.DepotTicker.clear();          // depots (her list section 8)
        com.dogpound.railmap.asset.AssetTicker.clear();          // asset register (48-next list)
        com.dogpound.railmap.station.TileStationLift.clear();
        com.dogpound.railmap.admin.Backups.applyStaged(event.getServer());   // #119: a staged railway restore goes in before the worlds load
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.dogpound.railmap.server.CommandIrExtras());
        event.registerServerCommand(new com.dogpound.railmap.factory.machine.CommandIndustry());   // /irfactory (alias /prideindustry)
        com.dogpound.railmap.admin.Admin.serverStarting(event.getServer());   // #117/#120: time + pause the tick handlers
        if (Loader.isModLoaded("dynmap") && dynmap == null) {
            try {
                // Reflection, so the verifier never has to resolve DynmapBridge (and through
                // it org.dynmap.*) while loading this class — only when we actually call this.
                dynmap = (DynmapHook) Class.forName("com.dogpound.railmap.dynmap.DynmapBridge")
                        .getConstructor().newInstance();
            } catch (LinkageError | ReflectiveOperationException e) {
                LOG.warn("[RailMap] Dynmap present but its API didn't link: {}", e.toString());
            }
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (dynmap != null) dynmap.shutdown();
        Viewers.clear();
        TrainTracker.clear();
    }
}
