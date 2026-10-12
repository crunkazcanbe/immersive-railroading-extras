package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.entity.Tender;
import cam72cam.mod.math.Vec3d;
import net.minecraft.init.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Predicate;

/** finding the rolling stock standing at a servicing machine, and putting coal / water into it */
public final class Stock {
    private Stock() {}

    public static <T extends EntityRollingStock> T nearest(World w, BlockPos p, double reach, Class<T> c, Predicate<T> ok) {
        T best = null;
        double bd = reach * reach;
        try {
            for (T s : cam72cam.mod.world.World.get(w).getEntities(c)) {
                if (s.isDead() || ok != null && !ok.test(s)) continue;
                Vec3d v = s.getPosition();
                double d = p.distanceSqToCenter(v.x, v.y, v.z);
                if (d < bd) { bd = d; best = s; }
            }
        } catch (LinkageError | RuntimeException ignored) { }
        return best;
    }

    /** a tender or a steam (tank) locomotive: what a water column / coal chute serves */
    public static boolean steamStock(EntityRollingStock s) { return s instanceof Tender || s instanceof LocomotiveSteam; }

    /** km/h, 0 when unknown */
    public static double kmh(EntityRollingStock s) {
        try { return s instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0; }
        catch (RuntimeException e) { return 0; }
    }

    public static boolean standing(EntityRollingStock s) { return kmh(s) < 0.5; }

    /** coal items the tender / loco bunker will still take */
    public static int coalRoom(FreightTank t) {
        int room = 0;
        for (int i = 2; i < t.cargoItems.getSlotCount(); i++) {
            cam72cam.mod.item.ItemStack s = t.cargoItems.get(i);
            if (s.isEmpty()) room += 64;
            else if (s.internal.getItem() == Items.COAL && s.internal.getMetadata() == 0) room += Math.max(0, t.cargoItems.getLimit(i) - s.getCount());
        }
        return room;
    }

    /** put up to n coal into the bunker (slots 2+); returns how many went in */
    public static int addCoal(FreightTank t, int n) {
        int put = 0;
        for (int i = 2; i < t.cargoItems.getSlotCount() && put < n; i++) {
            cam72cam.mod.item.ItemStack in = new cam72cam.mod.item.ItemStack(new net.minecraft.item.ItemStack(Items.COAL, n - put, 0));
            cam72cam.mod.item.ItemStack rest = t.cargoItems.insert(i, in, false);
            put += (n - put) - rest.internal.getCount();
        }
        return put;
    }

    /** litres of water aboard / capacity */
    public static int water(FreightTank t) { return t.getLiquidAmount(); }
    public static int capacity(FreightTank t) { return t.getTankCapacity().MilliBuckets(); }

    /** the tender coupled to a steam loco (or null) */
    public static Tender tender(Locomotive l) {
        try {
            for (cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType c : cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType.values())
                if (l.getCoupled(c) instanceof Tender t) return t;
        } catch (RuntimeException ignored) { }
        return null;
    }

    public static String name(EntityRollingStock s) { return com.dogpound.railmap.depot.DepotTicker.name(s); }
}
