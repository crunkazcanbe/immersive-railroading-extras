package com.dogpound.railmap.ride;

import com.dogpound.railmap.RailMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;

/** Runs every line each tick (RideSim), keeps their cars spawned near players, syncs the lines to clients, guards the doors. */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class RideTicker {
    private RideTicker() {}

    /** "dim:line:idx" -> the car entity seen last tick */
    private static final Map<String, EntityRideCar> CARS = new HashMap<>();
    private static final Map<Integer, Integer> SENT = new HashMap<>();

    static void seen(EntityRideCar c) { CARS.put(key(c.world, c.line(), c.idx()), c); }

    static String key(World w, int line, int idx) { return w.provider.getDimension() + ":" + line + ":" + idx; }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        World w = e.world;
        RideData d = RideData.get(w);
        int dim = w.provider.getDimension();
        if (d.lines.isEmpty()) {                                              // the last line went: tell the clients once
            if (SENT.getOrDefault(dim, -1) != d.version) { SENT.put(dim, d.version); RideNet.NET.sendToDimension(new PacketRide(dim, d.lines.values()), dim); }
            return;
        }
        long now = w.getTotalWorldTime();
        boolean moving = false;
        for (RideData.Line l : d.lines.values()) {
            try { RideSim.tick(w, l, now); } catch (RuntimeException ex) { RailMap.LOG.warn("[IR Extras] ropeway line {} tick failed: {}", l.id, ex.toString()); }
            moving |= Math.abs(l.v) > 0.001 || l.carV.length > 0 && anyMoving(l);
            if (now % 20 == 11) spawn(w, l);
        }
        if (now % 200 == 0) d.markDirty();
        if (now % (moving ? 10 : 40) == 0 || SENT.getOrDefault(dim, -1) != d.version) {
            SENT.put(dim, d.version);
            PacketRide p = new PacketRide(dim, d.lines.values());
            RideNet.NET.sendToDimension(p, dim);
        }
    }

    static boolean anyMoving(RideData.Line l) { for (double v : l.carV) if (v > 0.001) return true; return false; }

    static void spawn(World w, RideData.Line l) {
        if (l.geom() == null) return;
        for (int i = 0; i < l.cars(); i++) {
            EntityRideCar c = CARS.get(key(w, l.id, i));
            if (c != null && !c.isDead && c.world == w && w.getTotalWorldTime() - c.seenAt < 40) continue;
            if (c != null) c.setDead();
            double[] p = l.carPose(i);
            if (p == null) continue;
            BlockPos at = new BlockPos(p[0], p[1], p[2]);
            if (!w.isBlockLoaded(at)) continue;
            EntityPlayer near = w.getClosestPlayer(p[0], p[1], p[2], RideConfig.spawnRange, false);
            if (near == null) continue;
            EntityRideCar n = new EntityRideCar(w, l.id, i, l.kind);
            n.setPosition(p[0], p[1], p[2]);
            n.rotationYaw = (float) p[3];
            n.seenAt = w.getTotalWorldTime();
            if (w.spawnEntity(n)) seen(n);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP p && RideNet.NET != null) RideNet.NET.sendTo(new PacketRide(p.world.provider.getDimension(), RideData.get(p.world).lines.values()), p);
    }

    @SubscribeEvent
    public static void changedDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.player instanceof EntityPlayerMP p && RideNet.NET != null) RideNet.NET.sendTo(new PacketRide(p.world.provider.getDimension(), RideData.get(p.world).lines.values()), p);
    }

    /** doors locked between stations: sneaking doesn't get you out over the valley */
    @SubscribeEvent
    public static void mount(EntityMountEvent e) {
        if (e.isMounting() || !(e.getEntityBeingMounted() instanceof EntityRideCar c) || e.getWorldObj().isRemote) return;
        if (!RideConfig.doorsLocked || c.isDead || c.ejecting || c.doorsOpen()) return;
        Entity who = e.getEntityMounting();
        if (who instanceof EntityPlayer p && p.capabilities.isCreativeMode) return;
        e.setCanceled(true);
        if (who instanceof EntityPlayer p) p.sendStatusMessage(new TextComponentString("§eDoors locked - wait for the station"), true);
    }

    /** forget a removed line's cars */
    static void removed(World w, RideData.Line l) {
        for (int i = 0; i < 64; i++) {
            EntityRideCar c = CARS.remove(key(w, l.id, i));
            if (c != null) { c.eject(); c.setDead(); }
        }
    }
}
