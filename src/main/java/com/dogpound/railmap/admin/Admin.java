package com.dogpound.railmap.admin;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.incident.BlockIncident;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Multiplayer + server administration (her ChatGPT list #111-120): the clock, start-up and the console block. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Admin {
    private Admin() {}

    public static Block block() {
        return new BlockIncident("admin_console", TileAdminConsole::new, new double[]{0, 0, 1, 16, 16, 16},
                "Railway Administration Console: inspect / safe-stop trains, pause subsystems, desk locks, backups, statistics, simulation profile",
                "Admins only (irextras.rail.admin). ADMIN KEY arms restore / profile / force release. 12 V");
    }

    public static void registerTiles() { GameRegistry.registerTileEntity(TileAdminConsole.class, new ResourceLocation(RailMap.MODID, "admin_console")); }

    /** FMLServerStarting: time the tick handlers */
    public static void serverStarting(MinecraftServer s) { Sched.install(s); }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        try {
            if (now % 20 == 5) { Sched.expire(w); AdminOps.holdStopped(w); }
            if (now % 100 == 41) { Commercial.sample(w); Commercial.daily(w); }
            if (now % 1200 == 97) Stats.get(w).sample(w);
            MinecraftServer s = w.getMinecraftServer();
            if (w.provider.getDimension() == 0 && s != null && AdminConfig.snapshotMinutes > 0 && s.getTickCounter() > 0 && s.getTickCounter() % (AdminConfig.snapshotMinutes * 1200) == 0)
                Backups.snapshot(s, "auto");
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.warn("[IR Extras] admin tick failed: {}", ex.toString());
        }
    }

    @SubscribeEvent
    public static void load(WorldEvent.Load e) {
        if (!e.getWorld().isRemote && e.getWorld().provider.getDimension() == 0) Backups.worldLoaded(e.getWorld());
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (!e.getWorld().isRemote && e.getWorld().provider.getDimension() == 0) Backups.worldUnloaded(e.getWorld());
    }
}
