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
@Mod(modid = RailMap.MODID, name = "Immersive Railroading Extras", version = "0.5.0",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:universalmodcore;after:immersiverailroading;after:landofsignals;after:dynmap;after:opencomputers")
public class RailMap {
    public static final String MODID = "irextras";
    public static final int GUI_DISPATCHER_BOARD = 0;
    public static final Logger LOG = LogManager.getLogger("RailMap");

    @Mod.Instance(MODID)
    public static RailMap instance;

    @SidedProxy(clientSide = "com.dogpound.railmap.proxy.ClientProxy", serverSide = "com.dogpound.railmap.proxy.CommonProxy")
    public static CommonProxy proxy;

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
        NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
        MinecraftForge.EVENT_BUS.register(new TrainTracker());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.signal.SignalEngine());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.auto.Autopilot());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.railmap.auto.Protection());
        proxy.preInit();
    }

    /**
     * Wipe last session's state BEFORE the worlds load. This used to run in serverStarting, which
     * fires after the integrated server has loaded the worlds -- so every signal mast, crossing,
     * bridge, speed sign and joint that registered itself while loading was thrown away again,
     * and none of them ever reacted to a train.
     */
    @Mod.EventHandler
    public void serverAboutToStart(net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent event) {
        Viewers.clear();
        TrainTracker.clear();
        com.dogpound.railmap.signal.SignalRegistry.clear();
        com.dogpound.railmap.auto.Autopilot.clear();
        com.dogpound.railmap.auto.Protection.clear();
        com.dogpound.railmap.auto.Interlocking.clear();
        com.dogpound.railmap.auto.ArrivalEvents.clear();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.dogpound.railmap.server.CommandIrExtras());
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
