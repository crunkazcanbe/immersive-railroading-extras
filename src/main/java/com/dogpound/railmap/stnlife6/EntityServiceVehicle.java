package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.road.EntityRoadVehicle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A timetabled bus or a taxi (batch 6c #11 #12): a road-traffic vehicle (road.EntityRoadVehicle - same A* road driving,
 * signals, crossings, queues and lamps) that drives a list of stops instead of wandering. A bus stops at each bay for
 * LifeConfig.busDwell seconds with its doors open (right-click to board: the fare is taken), then drives on, and at
 * the last bay sets everyone down and runs back to the depot. A taxi drives to its rank and waits; get in and it takes
 * you to the destination booked at the rank with the meter running, takes the fare and returns to the rank. Long trips
 * are cut into ~100 block legs on the road. Like all road traffic it is never saved: the board / rank sends another.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class EntityServiceVehicle extends EntityRoadVehicle {
    public static final int BUS = 0, TAXI = 1;
    public int role, route;
    public BlockPos home;                          // the departure board / taxi rank that sent it
    public final List<BlockPos> stops = new ArrayList<>();
    public final List<String> names = new ArrayList<>();
    public int stopIdx, dwell, waitLeft;
    public boolean done, hired, returning;
    public double meter;                           // blocks driven on hire
    public long fare, tariff100 = 100, dispatched;
    public String destName = "";
    private double lx, lz;

    public EntityServiceVehicle(World w) { super(w); }
    public EntityServiceVehicle(World w, Kind k, int colour, BlockPos home, int role) { super(w, k, colour, null); this.home = home; this.role = role; }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> e) {
        e.getRegistry().register(EntityEntryBuilder.create().entity(EntityServiceVehicle.class).id(new ResourceLocation(RailMap.MODID, "service_vehicle"), 7821)
                .name(RailMap.MODID + ".service_vehicle").tracker(128, 2, true).build());
    }

    public BlockPos target() { return stopIdx < stops.size() ? stops.get(stopIdx) : null; }
    public String stopName() { return stopIdx < names.size() ? names.get(stopIdx) : ""; }
    public boolean atStop() { return dwell > 0; }

    @Override protected boolean ambient() { return false; }
    @Override protected boolean hold() { return dwell > 0 || (role == TAXI && !hired && !returning && waitLeft > 0 && stopIdx >= stops.size()); }

    @Override
    protected BlockPos nextDest() {
        BlockPos t = target();
        return t == null ? null : Roads.leg(world, getPosition(), t);
    }

    @Override
    public void onUpdate() {
        if (!world.isRemote && !isDead) route();
        super.onUpdate();
    }

    private void route() {
        if (role == TAXI && hired && ticksExisted % 20 == 0) {
            meter += Math.sqrt((posX - lx) * (posX - lx) + (posZ - lz) * (posZ - lz));
            fare = (LifeConfig.taxiFlag + (long) (meter * LifeConfig.taxiPerBlock)) * tariff100 / 100;
            for (Entity p : getPassengers()) if (p instanceof EntityPlayer pl)
                pl.sendStatusMessage(new TextComponentString(String.format(Locale.ROOT, "§eTAXI METER §f%s §7- to %s, %.0f blocks", Coins.fmt(fare), destName, target() == null ? 0 : Math.sqrt(getDistanceSq(target())))), true);
        }
        if (ticksExisted % 20 == 0) { lx = posX; lz = posZ; }
        if (dwell > 0) {
            if (--dwell == 0) {
                if (role == BUS) world.playSound(null, getPosition(), com.dogpound.railmap.ModSounds.DOOR_CLOSE, SoundCategory.NEUTRAL, 0.8f, 0.9f);
                stopIdx++;
                if (stopIdx >= stops.size()) finish();
                else reroute();
            }
            return;
        }
        if (role == TAXI && !hired && stopIdx >= stops.size()) {             // waiting at the rank for a fare
            if (--waitLeft <= 0) setDead();
            return;
        }
        BlockPos t = target();
        if (t == null) return;
        if (getDistanceSq(t) < 16) arrive();                                 // pull up (hold() stops it dead)
    }

    private void arrive() {
        if (role == BUS) {
            dwell = LifeConfig.busDwell * 20;
            if (home != null && world.getTileEntity(home) instanceof TileBusBoard b) dwell += b.arrived(this);
            world.playSound(null, getPosition(), com.dogpound.railmap.ModSounds.DOOR_OPEN, SoundCategory.NEUTRAL, 0.8f, 0.9f);
            boolean last = stopIdx == stops.size() - 1;
            for (Entity p : getPassengers()) if (p instanceof EntityPlayer pl)
                pl.sendMessage(new TextComponentString("§6Route " + route + ": §f" + stopName() + (last ? " §c- this bus terminates here, all change please" : " §7- sneak to get off")));
            if (last) removePassengers();
            return;
        }
        if (hired) {                                                          // taxi: at the fare's destination
            for (Entity p : new ArrayList<>(getPassengers())) {
                p.dismountRidingEntity();
                if (p instanceof EntityPlayerMP pl) {
                    boolean paid = Coins.charge(pl, fare, "Taxi to " + destName);
                    pl.sendMessage(new TextComponentString(paid ? "§aTaxi: " + destName + " - fare " + Coins.fmt(fare) + " paid. Thank you!" : "§cTaxi: " + destName + " - fare " + Coins.fmt(fare) + " NOT PAID (no funds)"));
                    TileTaxiRank r = rank();
                    if (r != null) r.paid(pl.getName(), fare, paid, destName, meter);
                }
            }
            hired = false; returning = true;
            stops.clear(); names.clear(); stopIdx = 0;
            if (home != null) { BlockPos h = Roads.near(world, home, 6); if (h != null) { stops.add(h); names.add("rank"); } }
            reroute();
            return;
        }
        stopIdx++;                                                             // taxi: back at / arrived at the rank
        returning = false;
        waitLeft = LifeConfig.taxiWait * 20;
    }

    private void finish() {
        done = true;
        removePassengers();
        if (role == BUS && home != null && world.getTileEntity(home) instanceof TileBusBoard b) b.terminated(this);
        setDead();
    }

    TileTaxiRank rank() { return home != null && world.getTileEntity(home) instanceof TileTaxiRank r ? r : null; }

    /** a taxi waiting at its rank: in you get, off to the booked destination */
    public boolean hire(EntityPlayer p, BlockPos dest, String name, long tariff) {
        if (role != TAXI || hired || dest == null || !getPassengers().isEmpty()) return false;
        stops.clear(); names.clear(); stopIdx = 0;
        stops.add(dest); names.add(name);
        destName = name; tariff100 = tariff; hired = true; meter = 0; fare = LifeConfig.taxiFlag * tariff / 100;
        lx = posX; lz = posZ;
        p.startRiding(this, true);
        reroute();
        return true;
    }

    @Override
    public boolean processInitialInteract(EntityPlayer p, EnumHand hand) {
        if (world.isRemote) return true;
        if (isPassenger(p)) return true;
        if (role == BUS) {
            if (dwell <= 0) { p.sendStatusMessage(new TextComponentString("§7Route " + route + " bus - board it at a bay when the doors are open"), true); return true; }
            if (stopIdx >= stops.size() - 1) { p.sendStatusMessage(new TextComponentString("§cNot in service - this bus terminates here"), true); return true; }
            if (getPassengers().size() >= 6) { p.sendStatusMessage(new TextComponentString("§cBus full"), true); return true; }
            if (p instanceof EntityPlayerMP mp && !Coins.charge(mp, LifeConfig.busFare, "Bus route " + route)) { p.sendStatusMessage(new TextComponentString("§cThe driver wants " + Coins.fmt(LifeConfig.busFare) + " - you can't pay"), true); return true; }
            p.startRiding(this, true);
            p.sendMessage(new TextComponentString("§6Route " + route + " §7- fare " + Coins.fmt(LifeConfig.busFare) + ". Next: §f" + (stopIdx + 1 < names.size() ? names.get(stopIdx + 1) : "")));
            TileBusBoard b = home != null && world.getTileEntity(home) instanceof TileBusBoard bb ? bb : null;
            if (b != null) b.boarded(route);
            return true;
        }
        TileTaxiRank r = rank();
        if (hired || returning) { p.sendStatusMessage(new TextComponentString("§cTaxi - ON HIRE"), true); return true; }
        if (r == null) { p.sendStatusMessage(new TextComponentString("§7Taxi - book it at the rank"), true); return true; }
        p.sendStatusMessage(new TextComponentString(r.board(p, this)), true);
        return true;
    }

    @Override public double getMountedYOffset() { return 0.45; }
    @Override public boolean canFitPassenger(Entity e) { return getPassengers().size() < (kind() == Kind.BUS ? 6 : 1); }

    @Override
    public void updatePassenger(Entity e) {
        if (!isPassenger(e)) return;
        int i = getPassengers().indexOf(e);
        double back = kind() == Kind.BUS ? -3.0 + i * 1.1 : -0.4;   // role isn't synced; the kind is
        double s = Math.sin(Math.toRadians(rotationYaw)), c = Math.cos(Math.toRadians(rotationYaw));
        e.setPosition(posX + s * back, posY + getMountedYOffset() + e.getYOffset(), posZ - c * back);
    }
}
