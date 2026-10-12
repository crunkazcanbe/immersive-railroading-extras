package com.dogpound.railmap.newrail6;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.util.*;

/**
 * Motorail, batch 6 #18: Immersive Vehicles cars, vans and trucks driven up an end-loading ramp onto a train's flat /
 * car-carrier wagons. A loaded vehicle is saved exactly as IV saves it (its whole state: fuel, parts, paint, inventory)
 * and chained to that wagon; at the destination ramp it is driven off again where the ramp's roadway is. The wagon shows
 * it under a tarpaulin while it travels. Without Immersive Vehicles the ramps still work as ramps, they just have
 * nothing to load (the screen says so).
 */
public final class Motorail {
    private Motorail() {}

    static final String IV_ENTITY = "mcinterface1122.BuilderEntityExisting";

    public static boolean ivPresent() { return Loader.isModLoaded("mts"); }

    /** an IV vehicle (not a seat, a gun or a placed decor entity) */
    static boolean vehicle(Entity e) {
        if (e == null || e.isDead || !e.getClass().getName().equals(IV_ENTITY)) return false;
        NBTTagCompound t = new NBTTagCompound();
        try { e.writeToNBT(t); } catch (RuntimeException ex) { return false; }
        return t.getString("entityid").contains("Vehicle");
    }

    static String label(NBTTagCompound t, Entity e) {
        String n = t.getString("systemName");
        if (n.isEmpty()) n = t.getString("subName");
        if (n.isEmpty() && e != null) n = e.getName();
        n = n.replace('_', ' ');
        return n.length() > 18 ? n.substring(0, 18) : n;
    }

    /** IV vehicles on the roadway in front of a ramp */
    static List<Entity> onRoad(World w, BlockPos ramp, net.minecraft.util.EnumFacing front) {
        BlockPos c = ramp.offset(front, 4);
        AxisAlignedBB box = new AxisAlignedBB(c).grow(5, 3, 5);
        List<Entity> out = new ArrayList<>();
        for (Entity e : w.getEntitiesWithinAABB(Entity.class, box)) if (vehicle(e)) out.add(e);
        out.sort(Comparator.comparingDouble(e -> e.getDistanceSq(c)));
        return out;
    }

    /** the stopped wagon nearest the ramp (not a locomotive) with or without a load */
    static EntityRollingStock wagon(World mc, BlockPos ramp, boolean loaded) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(mc);
        NR6Data d = NR6Data.get(mc);
        EntityRollingStock best = null;
        double bd = 16 * 16;
        for (EntityRollingStock s : w.getEntities(EntityRollingStock.class)) {
            if (s.isDead() || s instanceof Locomotive || d.loads.containsKey(s.getUUID()) != loaded) continue;
            Vec3d p = s.getPosition();
            double q = ramp.distanceSqToCenter(p.x, p.y, p.z);
            if (q < bd) { bd = q; best = s; }
        }
        return best;
    }

    /** someone is sitting in the vehicle (IV seats are entities of its own interface package) */
    static boolean occupied(World w, Entity veh) {
        for (EntityPlayer p : w.playerEntities) {
            Entity r = p.getRidingEntity();
            if (r != null && r.getClass().getName().startsWith("mcinterface") && p.getDistanceSq(veh) < 8 * 8) return true;
        }
        return !veh.getPassengers().isEmpty();
    }

    /** the vehicle's saved tag moved to a new spot, standing still (IV keeps its own position keys) */
    public static NBTTagCompound moved(NBTTagCompound in, double x, double y, double z, float yaw) {
        NBTTagCompound t = in.copy();
        t.setDouble("positionx", x); t.setDouble("positiony", y); t.setDouble("positionz", z);
        t.setDouble("motionx", 0); t.setDouble("motiony", 0); t.setDouble("motionz", 0);
        if (t.hasKey("anglesy")) t.setDouble("anglesy", -yaw);
        NBTTagList pos = new NBTTagList();
        pos.appendTag(new NBTTagDouble(x)); pos.appendTag(new NBTTagDouble(y)); pos.appendTag(new NBTTagDouble(z));
        t.setTag("Pos", pos);
        NBTTagList mo = new NBTTagList();
        for (int i = 0; i < 3; i++) mo.appendTag(new NBTTagDouble(0));
        t.setTag("Motion", mo);
        return t;
    }

    static String load(World w, TileMotorailRamp ramp) {
        if (!ivPresent()) return "§7Immersive Vehicles is not installed - nothing to load";
        List<Entity> vs = onRoad(w, ramp.getPos(), ramp.front());
        if (vs.isEmpty()) return "§7No vehicle on the ramp roadway (drive it up to the ramp)";
        Entity v = vs.get(0);
        if (occupied(w, v)) return "§cEveryone out of the vehicle first";
        EntityRollingStock car = wagon(w, ramp.getPos(), false);
        if (car == null) return "§cNo empty wagon at the ramp (within 16 blocks, not a locomotive)";
        if (car instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && Math.abs(m.getCurrentSpeed().metric()) > 0.3) return "§cThe wagon is moving";
        NBTTagCompound t = new NBTTagCompound();
        if (!v.writeToNBTOptional(t)) return "§cThat vehicle can't be loaded";
        NR6Data.Load ld = new NR6Data.Load();
        ld.veh = t;
        ld.label = label(t, v);
        ld.at = w.getTotalWorldTime();
        ld.ramp = ramp.getPos().toLong();
        v.setDead();
        NR6Data d = NR6Data.get(w);
        d.loads.put(car.getUUID(), ld);
        d.changed();
        return "§a" + ld.label + " driven on and chained down on " + Ferry.label(car);
    }

    static String unload(World w, TileMotorailRamp ramp) {
        EntityRollingStock car = wagon(w, ramp.getPos(), true);
        if (car == null) return "§7No loaded wagon at the ramp (within 16 blocks)";
        if (car instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && Math.abs(m.getCurrentSpeed().metric()) > 0.3) return "§cThe wagon is moving";
        NR6Data d = NR6Data.get(w);
        NR6Data.Load ld = d.loads.get(car.getUUID());
        if (!ivPresent()) return "§cImmersive Vehicles is not installed - " + ld.label + " stays chained on the wagon";
        BlockPos at = ramp.getPos().offset(ramp.front(), 4);
        for (Entity e : onRoad(w, ramp.getPos(), ramp.front())) if (e.getDistanceSq(at) < 9) return "§cThe roadway is blocked by another vehicle";
        float yaw = ramp.front().getHorizontalAngle();
        NBTTagCompound t = moved(ld.veh, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, yaw);
        Entity v;
        try { v = EntityList.createEntityFromNBT(t, w); } catch (RuntimeException ex) { RailMap.LOG.warn("[IR Extras] motorail unload failed: {}", ex.toString()); v = null; }
        if (v == null) return "§cThe vehicle would not come off (its pack may be gone) - it stays on the wagon";
        v.setLocationAndAngles(at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, yaw, 0);
        if (!w.spawnEntity(v)) return "§cThe vehicle would not come off - it stays on the wagon";
        d.loads.remove(car.getUUID());
        d.changed();
        return "§a" + ld.label + " driven off at the ramp";
    }

    /** wagons that left the world (scrapped / unloaded chunk is fine): a load whose wagon was destroyed falls off at the last ramp */
    static void orphan(World mc, long now) {
        if (now % 1200 != 77) return;
        NR6Data d = NR6Data.get(mc);
        if (d.loads.isEmpty()) return;
        Set<UUID> alive = new HashSet<>();
        for (EntityRollingStock s : cam72cam.mod.world.World.get(mc).getEntities(EntityRollingStock.class)) alive.add(s.getUUID());
        // nothing is dropped automatically: a load whose wagon is in an unloaded chunk must survive. Only report.
        int lost = 0;
        for (UUID u : d.loads.keySet()) if (!alive.contains(u)) lost++;
        LOST = lost;
    }

    /** loads whose wagon is not loaded right now (in unloaded chunks or scrapped) */
    public static int LOST;
}
