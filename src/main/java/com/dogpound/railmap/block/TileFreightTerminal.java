package com.dogpound.railmap.block;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.List;

/**
 * A freight terminal: the machine that actually gets goods on and off a train.
 * <p>
 * Two jobs, same box. A <b>loading silo</b> takes from the stockpile beside it and fills the
 * freight car standing under it; an <b>unloading pit</b> empties the car into the stockpile.
 * Real flood loaders work on a moving train, so this does too — a car creeping past under
 * {@link #MAX_LOAD_KMH} keeps loading, a car highballing through does not.
 * <p>
 * It reports what it's doing on a comparator: the signal is the served car's fill level, 0–15,
 * so "stop the train when the hopper is full" is just a comparator and a redstone line.
 */
public class TileFreightTerminal extends TileEntity implements ITickable, com.dogpound.railmap.signal.IScalable {

    /** how far from the block a car can be and still be served, in blocks */
    private static final double RANGE = 3.5D;
    /** flood loaders work at a creep; past this the car is moving too fast to fill */
    private static final double MAX_LOAD_KMH = 15.0D;
    /** items moved per working cycle */
    private static final int RATE = 4;
    /** ticks between cycles */
    private static final int PERIOD = 10;

    private final boolean loader;
    /** dial the whole structure to match the gauge she runs — small stock wants a small gantry */
    private float scale = 1f;
    private int timer;
    private int fillPct = -1;
    private String servedCar = "";
    private int movedLastCycle;

    public TileFreightTerminal() {
        this(true);
    }

    public TileFreightTerminal(boolean loader) {
        this.loader = loader;
    }

    public static class Loader extends TileFreightTerminal {
        public Loader() {
            super(true);
        }
    }

    public static class Unloader extends TileFreightTerminal {
        public Unloader() {
            super(false);
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (++timer < PERIOD) {
            return;
        }
        timer = 0;

        Car car = findCar();
        if (car == null) {
            fillPct = -1;
            servedCar = "";
            movedLastCycle = 0;
            return;
        }
        servedCar = car.name;
        fillPct = car.fillPct;

        IItemHandler yard = adjacentInventory();
        movedLastCycle = (car.inv == null || yard == null) ? 0
                : loader ? move(yard, car.inv) : move(car.inv, yard);
    }

    /** Whatever is sitting under the terminal right now, whichever mod's rolling stock it is. */
    private static final class Car {
        final IItemHandler inv;
        final String name;
        final int fillPct;

        Car(IItemHandler inv, String name, int fillPct) {
            this.inv = inv;
            this.name = name;
            this.fillPct = fillPct;
        }
    }

    /** Moves up to {@link #RATE} items from one inventory to the other. Returns how many moved. */
    private static int move(IItemHandler from, IItemHandler to) {
        int budget = RATE;
        for (int slot = 0; slot < from.getSlots() && budget > 0; slot++) {
            ItemStack peek = from.extractItem(slot, budget, true);
            if (peek.isEmpty()) {
                continue;
            }
            ItemStack left = ItemHandlerHelper.insertItem(to, peek.copy(), true);
            int fits = peek.getCount() - left.getCount();
            if (fits <= 0) {
                continue;
            }
            ItemStack taken = from.extractItem(slot, fits, false);
            if (taken.isEmpty()) {
                continue;
            }
            ItemHandlerHelper.insertItem(to, taken, false);
            budget -= taken.getCount();
        }
        return RATE - budget;
    }

    /** The stockpile: the first neighbouring block that exposes an item handler. */
    private IItemHandler adjacentInventory() {
        for (EnumFacing dir : EnumFacing.values()) {
            TileEntity te = world.getTileEntity(pos.offset(dir));
            if (te == null) {
                continue;
            }
            EnumFacing side = dir.getOpposite();
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)) {
                return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            }
        }
        return null;
    }

    /**
     * Finds the closest piece of rolling stock in range that is stopped or still creeping.
     * <p>
     * Immersive Railroading freight is handled directly, because its cargo hold hangs off the
     * entity rather than off a capability. Everything else — Traincraft freight cars, chest
     * minecarts, any other mod's wagon — is picked up generically as long as it exposes an
     * inventory, so the terminal is not tied to one train mod or one size of stock.
     */
    private Car findCar() {
        Car ir = findImmersiveRailroadingCar();
        return ir != null ? ir : findGenericCar();
    }

    private Car findImmersiveRailroadingCar() {
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
        if (w == null) {
            return null;
        }
        List<EntityRollingStock> all = w.getEntities(EntityRollingStock.class);
        Freight best = null;
        double bestDist = Double.MAX_VALUE;
        double cx = pos.getX() + 0.5D, cy = pos.getY() + 0.5D, cz = pos.getZ() + 0.5D;
        for (EntityRollingStock stock : all) {
            if (stock.isDead() || !(stock instanceof Freight f)) {
                continue;
            }
            cam72cam.mod.math.Vec3d p = stock.getPosition();
            double dx = p.x - cx, dy = p.y - cy, dz = p.z - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > RANGE || Math.abs(dy) > RANGE) {
                continue;
            }
            if (stock instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null
                    && Math.abs(m.getCurrentSpeed().metric()) > MAX_LOAD_KMH) {
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = f;
            }
        }
        if (best == null) {
            return null;
        }
        int fill = best instanceof FreightTank ft ? ft.getPercentLiquidFull() : best.getPercentCargoFull();
        IItemHandler inv = best.cargoItems == null ? null : best.cargoItems.internal;
        return new Car(inv, nameOf(best), fill);
    }

    private Car findGenericCar() {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(RANGE, RANGE, RANGE);
        List<Entity> ents = world.getEntitiesWithinAABB(Entity.class, box);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        double cx = pos.getX() + 0.5D, cz = pos.getZ() + 0.5D;
        for (Entity e : ents) {
            if (e.isDead || e instanceof net.minecraft.entity.player.EntityPlayer) {
                continue;
            }
            if (inventoryOf(e) == null) {
                continue;
            }
            // km/h from the entity's own motion — works for any mod's wagon
            double kmh = Math.sqrt(e.motionX * e.motionX + e.motionZ * e.motionZ) * 20.0D * 3.6D;
            if (kmh > MAX_LOAD_KMH) {
                continue;
            }
            double dx = e.posX - cx, dz = e.posZ - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > RANGE || dist >= bestDist) {
                continue;
            }
            bestDist = dist;
            best = e;
        }
        if (best == null) {
            return null;
        }
        IItemHandler inv = inventoryOf(best);
        return new Car(inv, best.getName(), fillOf(inv));
    }

    /** An item handler for any entity that carries cargo, however it exposes it. */
    private static IItemHandler inventoryOf(Entity e) {
        if (e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            return e.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        }
        if (e instanceof IInventory inv && inv.getSizeInventory() > 0) {
            return new InvWrapper(inv);
        }
        return null;
    }

    /** Percentage full by item count, for stock that doesn't report its own load. */
    private static int fillOf(IItemHandler inv) {
        if (inv == null || inv.getSlots() == 0) {
            return 0;
        }
        int used = 0, cap = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            int limit = Math.max(1, inv.getSlotLimit(i));
            cap += limit;
            used += st.getCount();
        }
        return cap == 0 ? 0 : (int) Math.round(used * 100.0D / cap);
    }

    private static String nameOf(EntityRollingStock stock) {
        EntityRollingStockDefinition def = stock.getDefinition();
        return def == null ? stock.getDefinitionID() : def.name();
    }

    /** Comparator output: the served car's fill level, 0-15. Nothing in range reads 0. */
    public int redstoneOutput() {
        return fillPct <= 0 ? 0 : Math.max(1, Math.min(15, fillPct * 15 / 100));
    }

    public String statusLine() {
        String job = loader ? "Loading silo" : "Unloading pit";
        if (servedCar.isEmpty()) {
            return job + " — no car in range (park a freight car within " + (int) RANGE + " blocks)";
        }
        String rate = movedLastCycle > 0 ? movedLastCycle * (20 / PERIOD) + "/s" : "idle";
        return job + " — " + servedCar + "  " + fillPct + "% full  " + rate;
    }

    public boolean isLoader() {
        return loader;
    }

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float s) {
        scale = com.dogpound.railmap.signal.IScalable.clamp(s);
        markDirty();
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
                             net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        // the gantry reaches a block either side and two up, more when scaled up
        double r = 2.0D * Math.max(1f, scale);
        return new net.minecraft.util.math.AxisAlignedBB(pos).grow(r, r, r);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("fill", fillPct);
        if (scale != 1f) tag.setFloat("scale", scale);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fillPct = tag.hasKey("fill") ? tag.getInteger("fill") : -1;
        scale = tag.hasKey("scale") ? com.dogpound.railmap.signal.IScalable.clamp(tag.getFloat("scale")) : 1f;
    }
}
