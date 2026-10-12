package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Park & ride (batch 6b #10): what counts as a vehicle, how many are parked in a garage, tickets and fees. Vehicles are
 * Immersive Vehicles (Transport Simulator) entities and IR Extras road traffic (EntityRoadVehicle); one is parked when
 * it has moved less than half a block in the last 5 s. Fee = parkPerHour per started game hour; a driver whose Pride
 * Card made a rail journey since they parked pays parkRidePct of it (park & ride).
 */
public final class Parking {
    private Parking() {}

    /** last seen position + tick per vehicle entity (weak: unloaded vehicles drop out) */
    private static final Map<Entity, double[]> SEEN = new WeakHashMap<>();

    public static boolean isVehicle(Entity e) {
        if (e instanceof com.dogpound.railmap.road.EntityRoadVehicle) return true;
        String n = e.getClass().getName();
        // ponytail: IV is matched by class name (soft dependency); its render / seat helper entities are skipped by name
        return n.startsWith("minecrafttransportsimulator") && !n.contains("Render") && !n.contains("Seat") && !n.contains("Particle");
    }

    /** vehicles standing within `r` blocks of `c` (parked: < 0.5 block moved over the last 5 s) */
    public static int parked(World w, BlockPos c, int r) {
        long now = w.getTotalWorldTime();
        int n = 0;
        for (Entity e : w.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(c).grow(r, 4, r), Parking::isVehicle)) {
            double[] s = SEEN.get(e);
            if (s == null || now - (long) s[3] > 400) SEEN.put(e, s = new double[]{e.posX, e.posY, e.posZ, now, 0});
            else if (now - (long) s[3] >= 100) {                              // a 5 s sample: did it move?
                double dx = e.posX - s[0], dy = e.posY - s[1], dz = e.posZ - s[2];
                s[4] = dx * dx + dy * dy + dz * dz < 0.25 ? 1 : 0;
                s[0] = e.posX; s[1] = e.posY; s[2] = e.posZ; s[3] = now;
            }
            if (s[4] == 1) n++;
        }
        return n;
    }

    /** the player behind the wheel of whatever they ride (or on foot) */
    public static EntityPlayer driver(Entity e) {
        if (e instanceof EntityPlayer p) return p;
        for (Entity x : e.getPassengers()) if (x instanceof EntityPlayer p) return p;
        return null;
    }

    public static long fee(World w, TravelData.Park p) {
        long now = w.getTotalWorldTime();
        long hours = Math.max(1, (now - p.in + 999) / 1000);
        return hours * TravelConfig.parkPerHour;
    }

    /** did one of this driver's Pride Cards make (or start) a rail journey since they parked */
    public static boolean railSince(World w, TravelData.Park p) {
        if (p.who == null) return false;
        for (TravelData.Card c : Cards.of(w, p.who)) if (c.lastTripEnd >= p.in || c.in() && c.inAt >= p.in) return true;
        return false;
    }

    /** what this ticket costs now (park & ride rate applied) */
    public static long due(World w, TravelData.Park p) {
        long f = fee(w, p);
        return railSince(w, p) ? f * TravelConfig.parkRidePct / 100 : f;
    }

    public static boolean paidUp(World w, TravelData.Park p) {
        return p.paidAt >= 0 && w.getTotalWorldTime() - p.paidAt <= TravelConfig.exitGraceSeconds * 20L;
    }

    /** pay a ticket: from a Pride Card (card != null) or the bank; null = paid, else why not */
    public static String pay(World w, EntityPlayer who, TravelData.Park p, TravelData.Card card, String where) {
        long due = due(w, p);
        boolean rail = railSince(w, p);
        if (card != null) {
            if (card.blocked) return "That card is blocked";
            if (card.bal < due) return "Card balance " + Coins.fmt(card.bal) + " - parking is " + Coins.fmt(due);
            card.bal -= due;
            card.note(Recorder.clock(w, w.getTotalWorldTime()) + " PARKING " + where + " -" + Coins.fmt(due) + (rail ? " (P+R)" : ""));
            p.card = card.id;
        } else if (!(who instanceof net.minecraft.entity.player.EntityPlayerMP mp) || !Coins.charge(mp, due, "Parking at " + where)) return "Parking is " + Coins.fmt(due) + " - your bank can't cover it";
        p.paid += due; p.paidAt = w.getTotalWorldTime(); p.rail = rail;
        TravelData d = TravelData.get(w);
        d.parking += due;
        d.markDirty();
        return null;
    }

    /** find the session a ticket stands for */
    public static TravelData.Park session(World w, ItemStack s) { return TravelData.get(w).parks.get(ItemTravel.parkId(s)); }

    public static ItemStack ticketIn(EntityPlayer p) {
        for (ItemStack s : new ItemStack[]{p.getHeldItemMainhand(), p.getHeldItemOffhand()}) if (ItemTravel.parkId(s) != 0) return s;
        for (ItemStack s : p.inventory.mainInventory) if (ItemTravel.parkId(s) != 0) return s;
        return ItemStack.EMPTY;
    }
}
