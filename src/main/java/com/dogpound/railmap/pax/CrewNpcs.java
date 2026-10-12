package com.dogpound.railmap.pax;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.crew.CrewConfig;
import com.dogpound.railmap.crew.CrewData;
import com.dogpound.railmap.crew.CrewTicker;
import com.dogpound.railmap.interior.Interior;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import java.util.*;

/**
 * Draws crew.CrewTicker's crew as people (batch 5 #7 #9): only near players (viewDistance), nearest first, never more
 * than maxNpcs in a dimension, never saved. Each half second every shown person gets their orders: walk the route the
 * ticker is timing, sit in the cab, stand at the rear door dispatching, walk the coach aisle, or walk up to someone on
 * the platform to check their ticket.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class CrewNpcs {
    private CrewNpcs() {}

    private static final Map<Integer, Map<String, EntityCrew>> SHOWN = new HashMap<>();
    private static final Random RND = new Random();

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityCrew.class).id(new ResourceLocation(RailMap.MODID, "railway_crew"), 7761)
                .name(RailMap.MODID + ".railway_crew").tracker(80, 2, false).build());
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (e.getWorld().isRemote) return;
        Map<String, EntityCrew> m = SHOWN.remove(e.getWorld().provider.getDimension());
        if (m != null) for (EntityCrew c : m.values()) c.setDead();
    }

    public static Map<String, EntityCrew> shown(World w) { return SHOWN.getOrDefault(w.provider.getDimension(), Collections.emptyMap()); }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 10 != 6) return;
        try { manage(e.world, e.world.getTotalWorldTime()); }
        catch (RuntimeException | LinkageError ex) { RailMap.LOG.debug("[IR Extras] crew people: {}", ex.toString()); }
    }

    private static void manage(World w, long now) {
        Map<String, EntityCrew> m = SHOWN.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>());
        m.values().removeIf(x -> x.isDead);
        CrewData c = CrewData.get(w);
        if (!CrewConfig.npcs || c.staff.isEmpty()) { for (EntityCrew x : m.values()) x.setDead(); m.clear(); return; }
        double r2 = (double) CrewConfig.viewDistance * CrewConfig.viewDistance;
        List<Object[]> want = new ArrayList<>();
        for (CrewData.Staff s : c.staff.values()) {
            CrewTicker.Live L = CrewTicker.all(w).get(s.name);
            if (L == null || L.phase == CrewTicker.OFF || L.at == null) continue;
            double best = Double.MAX_VALUE;
            for (EntityPlayer p : w.playerEntities) if (!p.isSpectator()) best = Math.min(best, sq(p.posX - L.at[0]) + sq(p.posZ - L.at[2]));
            if (best <= r2) want.add(new Object[]{s, L, best});
        }
        want.sort(Comparator.comparingDouble(o -> (Double) o[2]));
        Set<String> keep = new HashSet<>();
        for (Object[] o : want) {
            if (keep.size() >= CrewConfig.maxNpcs) break;
            CrewData.Staff s = (CrewData.Staff) o[0];
            CrewTicker.Live L = (CrewTicker.Live) o[1];
            EntityCrew e = m.get(s.name);
            if (e == null || e.isDead) {
                if (!w.isBlockLoaded(new BlockPos(L.at[0], L.at[1], L.at[2]))) continue;
                e = new EntityCrew(w);
                e.staff = s.name;
                e.setPosition(L.at[0], L.at[1], L.at[2]);
                e.posY = e.ground(L.at[0], L.at[1] + 0.5, L.at[2]);
                e.setPosition(e.posX, e.posY, e.posZ);
                e.lastOrder = now;
                look(e, s, L);
                w.spawnEntity(e);
                m.put(s.name, e);
            }
            keep.add(s.name);
            look(e, s, L);
            order(w, e, s, L, now);
        }
        for (Iterator<Map.Entry<String, EntityCrew>> it = m.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, EntityCrew> x = it.next();
            if (!keep.contains(x.getKey())) { x.getValue().setDead(); it.remove(); }
        }
    }

    /** uniform, what they carry, name + what they're doing for the tooltip */
    private static void look(EntityCrew e, CrewData.Staff s, CrewTicker.Live L) {
        int acc = s.role == CrewData.DRIVER ? (L.phase == CrewTicker.CAB || L.phase == CrewTicker.BREAK ? EntityPax.ACC_NONE : EntityPax.ACC_BRIEFCASE)
                : s.role == CrewData.GUARD ? EntityPax.ACC_SHOULDER : EntityPax.ACC_NONE;
        int v = EntityPax.makeVariant(s.skin, acc, false, false, s.slim, s.role == CrewData.GUARD ? 3 : 0) | (s.role & 3) << 16;
        if (e.variant() != v) e.setVariant(v);
        e.setDestName(CrewData.ROLE[s.role] + " " + s.name);
        e.setNote(L.doing == null ? "" : L.doing);
    }

    private static void order(World w, EntityCrew e, CrewData.Staff s, CrewTicker.Live L, long now) {
        double dx = e.posX - L.at[0], dz = e.posZ - L.at[2];
        boolean far = dx * dx + dz * dz > 16 * 16;
        int act = L.action;
        switch (L.phase) {
            case CrewTicker.WALK -> {
                if (far) e.setPosition(L.at[0], e.ground(L.at[0], L.at[1] + 0.5, L.at[2]), L.at[2]);
                double[] to = L.to == null ? L.at : L.to;
                e.walkTo(to[0], to[1], to[2], e.rotationYaw);
            }
            case CrewTicker.OFFICE -> {
                if (far) e.setPosition(L.at[0], e.ground(L.at[0], L.at[1] + 0.5, L.at[2]), L.at[2]);
                e.stand(L.at[0], L.at[1], L.at[2], deskYaw(w, CrewTicker.office(CrewData.get(w), s, L.at)), false);
            }
            case CrewTicker.CAB, CrewTicker.BREAK -> {
                EntityRollingStock car = CrewTicker.car(w, L.car);
                if (car == null) { e.keep(); break; }
                double[] d = Interior.dims(car);
                e.pin(L.car, 0.35 * d[0], d[1] - 0.45 * d[0], d[2] + 0.4 * d[0], 0, true, 0);
            }
            case CrewTicker.ONBOARD -> {
                EntityRollingStock car = CrewTicker.car(w, L.car);
                if (car == null) { e.keep(); break; }
                double[] d = Interior.dims(car);
                if (s.role == CrewData.INSPECTOR) {
                    e.pin(L.car, 0, d[1], d[2], 0, false, Math.max(0.5, (d[3] - 0.8) * d[0]));
                } else {                                                               // guard: the end of the rear car away from the loco
                    double end = (car.getDefinition().getLength(car.gauge) / 2 - 1.0);
                    EntityRollingStock loco = CrewTicker.car(w, s.train);
                    double sgn = 1;
                    if (loco != null && loco != car) { double[] lo = Interior.toLocal(car, loco.getPosition().x, loco.getPosition().y, loco.getPosition().z); sgn = lo[2] > 0 ? -1 : 1; }
                    e.pin(L.car, 0, d[1], sgn * end, sgn > 0 ? 180 : 0, false, 0);
                }
            }
            case CrewTicker.PLATFORM -> {
                if (L.to == null) { e.keep(); break; }
                EntityRollingStock loco = CrewTicker.car(w, s.train);
                float yaw = loco == null ? e.rotationYaw : PaxGraph.yaw(loco.getPosition().x - L.to[0], loco.getPosition().z - L.to[2]);
                if (far) e.setPosition(L.to[0], e.ground(L.to[0], L.to[1] + 0.5, L.to[2]), L.to[2]);
                e.stand(L.to[0], L.to[1], L.to[2], yaw, false);
            }
            case CrewTicker.PATROL -> patrol(w, e, s, L, now, far);
            default -> e.keep();
        }
        if (L.target != null && act == CrewTicker.A_CHECK) {                           // face whoever they're checking
            EntityPlayer p = w.getPlayerEntityByUUID(L.target);
            if (p != null) e.look(PaxGraph.yaw(p.posX - e.posX, p.posZ - e.posZ));
        }
        if (act == CrewTicker.A_NONE && now < e.checkUntil) act = CrewTicker.A_CHECK;
        e.setAction(act);
    }

    /** an inspector on a platform: go from person to person checking tickets (the numbers are counted by Revenue) */
    private static void patrol(World w, EntityCrew e, CrewData.Staff s, CrewTicker.Live L, long now, boolean far) {
        if (far) e.setPosition(L.at[0], e.ground(L.at[0], L.at[1] + 0.5, L.at[2]), L.at[2]);
        if (L.target != null) {
            EntityPlayer p = w.getPlayerEntityByUUID(L.target);
            if (p != null) { walkNear(e, p.posX, p.posY, p.posZ); return; }
        }
        Map<Long, PaxWorld.Site> sites = PaxWorld.sites(w);
        PaxWorld.Site site = sites.get(L.station);
        if (e.checking != null && (e.checking.isDead || now - e.checkStart > 240)) e.checking = null;
        if (e.checking == null && site != null && now >= e.checkUntil) {
            List<EntityPax> cand = new ArrayList<>();
            for (EntityPax p : site.people) if (!p.isDead && !p.kid() && (p.state() == EntityPax.WAIT || p.state() == EntityPax.SIT) && sq(p.posX - e.posX) + sq(p.posZ - e.posZ) < 20 * 20) cand.add(p);
            if (!cand.isEmpty()) { e.checking = cand.get(RND.nextInt(cand.size())); e.checkStart = now; }
        }
        if (e.checking != null) {
            EntityPax p = e.checking;
            if (sq(p.posX - e.posX) + sq(p.posZ - e.posZ) > 1.7 * 1.7) { walkNear(e, p.posX, p.posY, p.posZ); return; }
            e.stand(e.posX, e.posY, e.posZ, PaxGraph.yaw(p.posX - e.posX, p.posZ - e.posZ), false);
            e.checkUntil = now + 60;
            p.look(PaxGraph.yaw(e.posX - p.posX, e.posZ - p.posZ));
            int sh = (int) Math.round(com.dogpound.railmap.crew.Rules.dodgerShare(com.dogpound.railmap.economy.PassengerData.get(w).stn(L.station) == null ? 0
                    : com.dogpound.railmap.economy.PassengerData.get(w).stn(L.station).gates, CrewConfig.dodgerScale) * 1000);
            String n = RND.nextInt(1000) < sh ? (CrewConfig.penaltyMode > 0 && RND.nextInt(4) != 0 ? "No ticket - penalty fare paid" : "No ticket - warned, bought one") : "Ticket checked";
            p.setNote(n);
            for (EntityPax k : p.kids) k.setNote(n);
            e.checking = null;
            return;
        }
        if (e.mode == EntityCrew.STAND && RND.nextInt(6) == 0 && site != null && site.g != null && site.g.spots.length > 0) {   // stroll the platform
            int nd = site.g.spots[RND.nextInt(site.g.spots.length)];
            e.walkTo(site.g.cx(nd), site.g.cy(nd), site.g.cz(nd), e.rotationYaw);
        } else e.keep();
    }

    private static void walkNear(EntityCrew e, double x, double y, double z) {
        double dx = x - e.posX, dz = z - e.posZ, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1.6) { e.stand(e.posX, e.posY, e.posZ, PaxGraph.yaw(dx, dz), false); return; }
        e.walkTo(x - dx / d * 1.3, y, z - dz / d * 1.3, PaxGraph.yaw(dx, dz));
    }

    private static float deskYaw(World w, long office) {
        if (office == 0) return 0;
        net.minecraft.tileentity.TileEntity t = w.getTileEntity(BlockPos.fromLong(office));
        EnumFacing f = t instanceof com.dogpound.railmap.controls.IControlled c ? c.controlFacing() : EnumFacing.NORTH;
        return f.getOpposite().getHorizontalAngle();
    }

    static double sq(double v) { return v * v; }

    // ---- station helpers for crew.CrewTicker ------------------------------------------------------------------------------
    /** where a guard stands on the platform beside a train's door nearest `near` (null = no platform graph here) */
    public static double[] platformSpot(World w, long station, UUID loco, double[] near) {
        PaxWorld.Site s = PaxWorld.sites(w).get(station);
        if (s == null || s.g == null) return null;
        s.now = w.getTotalWorldTime();
        List<PaxWorld.Door> ds = s.doors(w, loco);
        PaxWorld.Door best = null; double bd = Double.MAX_VALUE;
        for (PaxWorld.Door d : ds) { double dd = sq(d.x - near[0]) + sq(d.z - near[2]); if (dd < bd) { bd = dd; best = d; } }
        if (best == null) return null;
        return new double[]{s.g.cx(best.node), s.g.cy(best.node), s.g.cz(best.node)};
    }

    /** is this spot on a platform of the station (true when the station has no walk graph to tell) */
    public static boolean onPlatform(World w, long station, double x, double y, double z) {
        PaxWorld.Site s = PaxWorld.sites(w).get(station);
        if (s == null || s.g == null) return true;
        int nd = s.g.nearest(x, y, z, 2.5, false);
        return nd >= 0 && s.g.platform[nd];
    }

    public static void clear(World w) {
        Map<String, EntityCrew> m = SHOWN.get(w.provider.getDimension());
        if (m == null) return;
        for (EntityCrew x : m.values()) x.setDead();
        m.clear();
    }
}
