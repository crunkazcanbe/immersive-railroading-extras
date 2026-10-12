package com.dogpound.railmap.factory.machine;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.List;

/**
 * The freight car standing at a loading / unloading station: an Immersive Railroading freight car (its cargo hold),
 * or any mod's wagon or cart with an inventory. Moving cars (over ~1 km/h) are never served.
 */
final class Freight {
    private Freight() {}

    static final class Car {
        final IItemHandler inv;
        final String name;
        final int fillPct;
        java.util.UUID id;                  // IR stock id: freight contracts bill this car
        Car(IItemHandler inv, String name, int fillPct) { this.inv = inv; this.name = name; this.fillPct = fillPct; }
    }

    static Car find(World w, AxisAlignedBB box) {
        if (Loader.isModLoaded("immersiverailroading")) {
            Car c = ir(w, box);
            if (c != null) return c;
        }
        for (Entity e : w.getEntitiesWithinAABB(Entity.class, box)) {
            if (e.isDead || e instanceof EntityPlayer) continue;
            IItemHandler inv = inventoryOf(e);
            if (inv == null) continue;
            double kmh = Math.sqrt(e.motionX * e.motionX + e.motionZ * e.motionZ) * 20 * 3.6;
            if (kmh > 1) continue;
            return new Car(inv, e.getName(), fill(inv));
        }
        return null;
    }

    private static Car ir(World world, AxisAlignedBB box) {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
        if (w == null) return null;
        for (EntityRollingStock stock : w.getEntities(EntityRollingStock.class)) {
            if (stock.isDead() || !(stock instanceof cam72cam.immersiverailroading.entity.Freight f)) continue;
            cam72cam.mod.math.Vec3d p = stock.getPosition();
            if (!box.contains(new net.minecraft.util.math.Vec3d(p.x, p.y + 0.5, p.z))) continue;
            if (stock instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null && Math.abs(m.getCurrentSpeed().metric()) > 1) continue;
            IItemHandler inv = f.cargoItems == null ? null : f.cargoItems.internal;
            if (inv == null) continue;
            EntityRollingStockDefinition def = stock.getDefinition();
            Car c = new Car(inv, def == null ? stock.getDefinitionID() : def.name(), f.getPercentCargoFull());
            c.id = stock.getUUID();
            return c;
        }
        return null;
    }

    private static IItemHandler inventoryOf(Entity e) {
        if (e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) return e.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (e instanceof IInventory inv && inv.getSizeInventory() > 0) return new InvWrapper(inv);
        return null;
    }

    static int fill(IItemHandler inv) {
        int used = 0, cap = 0;
        for (int i = 0; i < inv.getSlots(); i++) { used += inv.getStackInSlot(i).getCount(); cap += Math.max(1, inv.getSlotLimit(i)); }
        return cap == 0 ? 0 : used * 100 / cap;
    }

    static int count(IItemHandler inv) {
        int n = 0;
        for (int i = 0; i < inv.getSlots(); i++) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    /** what the last move() carried, stack by stack (freight contracts bill it) */
    static final List<ItemStack> lastMoved = new java.util.ArrayList<>();
    /** why the last item was refused by the wagon cargo rules ("" = none) */
    static String refused = "";

    /** move up to max items from one inventory to another; returns how many moved. Loading a car (`into` given), the
     *  IR Extras wagon cargo rules (her list #90) decide what this wagon takes and how full it may get. */
    static int move(IItemHandler from, IItemHandler to, int max, Car into) {
        int moved = 0;
        lastMoved.clear();
        for (int i = 0; i < from.getSlots() && moved < max; i++) {
            ItemStack take = from.extractItem(i, max - moved, true);
            if (take.isEmpty()) continue;
            if (into != null) {
                String no = com.dogpound.railmap.freight.CargoRules.refuse(into.name, take);
                if (no == null && fill(to) >= com.dogpound.railmap.freight.CargoRules.limitPct(into.name, take)) no = "axle-load limit for " + take.getDisplayName();
                if (no != null) { refused = no; continue; }
                refused = "";
            }
            ItemStack left = ItemHandlerHelper.insertItemStacked(to, take, false);
            int n = take.getCount() - left.getCount();
            if (n > 0) { ItemStack got = from.extractItem(i, n, false); lastMoved.add(got.copy()); moved += n; }
        }
        return moved;
    }
}
