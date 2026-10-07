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
 * the "serve cars slower than" setting keeps loading, a car highballing through does not.
 * <p>
 * It reports what it's doing on a comparator: the signal is the served car's fill level, 0–15,
 * so "stop the train when the hopper is full" is just a comparator and a redstone line.
 */
public class TileFreightTerminal extends TileEntity implements ITickable, com.dogpound.railmap.signal.IScalable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private long totalMoved;
    private int carsServed;
    private String lastCar = "";

    private double range() { return cfg.num("reach") + 0.5D; }
    private double maxKmh() { return cfg.num("maxKmh"); }

    @Override public String settingsTitle() { return loader ? "Loading Silo" : "Unloading Pit"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { markDirty(); }

    @Override
    public List<com.dogpound.railmap.settings.Setting> settingDefs() {
        List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String op = "Operation", fl = "Filter", sk = "Rolling stock", rs = "Redstone", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.bool(op, "on", "Working", "Switch the terminal on or off", true));
        l.add(com.dogpound.railmap.settings.Setting.num(op, "rate", "Items per cycle", "How many items move each working cycle", 4, 1, 64, 1, "items"));
        l.add(com.dogpound.railmap.settings.Setting.num(op, "period", "Cycle time", "Ticks between cycles (20 ticks = 1 second)", 10, 1, 40, 1, "ticks"));
        l.add(com.dogpound.railmap.settings.Setting.num(op, "reach", "Reach", "How far from the block a car can stand", 3, 1, 8, 1, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num(op, "maxKmh", "Serve cars slower than", "Flood loading works at a creep", 15, 0, 80, 1, "km/h"));
        if (loader) l.add(com.dogpound.railmap.settings.Setting.num(op, "stopAt", "Stop filling at", "Leave the car partly empty", 100, 5, 100, 5, "%"));
        else l.add(com.dogpound.railmap.settings.Setting.num(op, "keep", "Leave in each car", "Unload all but this many items", 0, 0, 1728, 16, "items"));
        l.add(com.dogpound.railmap.settings.Setting.num(op, "keepYard", loader ? "Keep in the stockpile" : "Stop when the stockpile has", loader ? "Never take the stockpile below this" : "Stop unloading once the stockpile holds this many", 0, 0, 100000, 64, "items"));
        l.add(com.dogpound.railmap.settings.Setting.choice(fl, "filter", "Filter", "Which items it handles", "Everything", "Everything", "Only these", "All except these"));
        l.add(com.dogpound.railmap.settings.Setting.text(fl, "list", "Item list", "Comma separated: minecraft:coal, modid:*, ore:ingotIron", "", 300));
        l.add(com.dogpound.railmap.settings.Setting.bool(sk, "ir", "Immersive Railroading freight", "", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(sk, "other", "Other mods' wagons and minecarts", "Traincraft, chest minecarts, any wagon with an inventory", true));
        l.add(com.dogpound.railmap.settings.Setting.choice(rs, "rsMode", "Redstone control", "", "Always", "Always", "Only when powered", "Only when unpowered"));
        l.add(com.dogpound.railmap.settings.Setting.choice(rs, "rsOut", "Redstone output", "What the block emits", "Fill level", "Fill level", "Car present", "Moving items", "Car full", "Car empty"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Car", servedCar.isEmpty() ? "none in range" : servedCar));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Car fill", fillPct < 0 ? "-" : fillPct + "%"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Rate now", movedLastCycle * 20 / Math.max(1, cfg.num("period")) + " items/s"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Items moved (total)", String.valueOf(totalMoved)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Cars served", String.valueOf(carsServed)));
        return l;
    }

    /** Filter: entries like minecraft:coal, modid:*, ore:ingotIron */
    private boolean allowed(ItemStack st) {
        String mode = cfg.text("filter");
        if ("Everything".equals(mode) || st.isEmpty()) return true;
        boolean hit = false;
        String id = st.getItem().getRegistryName() == null ? "" : st.getItem().getRegistryName().toString();
        for (String raw : cfg.text("list").split(",")) {
            String e = raw.trim();
            if (e.isEmpty()) continue;
            if (e.startsWith("ore:")) {
                for (int o : net.minecraftforge.oredict.OreDictionary.getOreIDs(st))
                    if (net.minecraftforge.oredict.OreDictionary.getOreName(o).equals(e.substring(4))) hit = true;
            } else if (e.endsWith(":*")) { if (id.startsWith(e.substring(0, e.length() - 1))) hit = true; }
            else if (e.equals(id)) hit = true;
            if (hit) break;
        }
        return "Only these".equals(mode) == hit;
    }

    private static int count(IItemHandler h) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) n += h.getStackInSlot(i).getCount();
        return n;
    }

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
        if (++timer < Math.max(1, cfg.num("period"))) {
            return;
        }
        timer = 0;
        int oldOut = redstoneOutput();
        boolean powered = world.isBlockPowered(pos);
        String rsMode = cfg.text("rsMode");
        boolean run = cfg.bool("on") && !("Only when powered".equals(rsMode) && !powered) && !("Only when unpowered".equals(rsMode) && powered);

        Car car = findCar();
        if (car == null) {
            fillPct = -1;
            servedCar = "";
            movedLastCycle = 0;
            return;
        }
        if (!car.name.equals(lastCar)) { lastCar = car.name; carsServed++; }
        servedCar = car.name;
        fillPct = car.fillPct;

        IItemHandler yard = adjacentInventory();
        int budget = Math.max(1, cfg.num("rate"));
        if (!run || car.inv == null || yard == null) budget = 0;
        else if (loader) {
            if (fillPct >= cfg.num("stopAt")) budget = 0;
            budget = Math.min(budget, Math.max(0, count(yard) - cfg.num("keepYard")));
        } else {
            budget = Math.min(budget, Math.max(0, count(car.inv) - cfg.num("keep")));
            if (cfg.num("keepYard") > 0) budget = Math.min(budget, Math.max(0, cfg.num("keepYard") - count(yard)));
        }
        movedLastCycle = budget <= 0 ? 0 : loader ? move(yard, car.inv, budget) : move(car.inv, yard, budget);
        totalMoved += movedLastCycle;
        if (oldOut != redstoneOutput()) world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
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

    /** Moves up to {@code rate} allowed items from one inventory to the other. Returns how many moved. */
    private int move(IItemHandler from, IItemHandler to, int rate) {
        int budget = rate;
        for (int slot = 0; slot < from.getSlots() && budget > 0; slot++) {
            ItemStack peek = from.extractItem(slot, budget, true);
            if (peek.isEmpty() || !allowed(peek)) {
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
        return rate - budget;
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
        Car ir = cfg.bool("ir") ? findImmersiveRailroadingCar() : null;
        return ir != null ? ir : cfg.bool("other") ? findGenericCar() : null;
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
            if (dist > range() || Math.abs(dy) > range()) {
                continue;
            }
            if (stock instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null
                    && Math.abs(m.getCurrentSpeed().metric()) > maxKmh()) {
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
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(range(), range(), range());
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
            if (kmh > maxKmh()) {
                continue;
            }
            double dx = e.posX - cx, dz = e.posZ - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range() || dist >= bestDist) {
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
        return switch (cfg.text("rsOut")) {
            case "Car present" -> servedCar.isEmpty() ? 0 : 15;
            case "Moving items" -> movedLastCycle > 0 ? 15 : 0;
            case "Car full" -> fillPct >= 100 ? 15 : 0;
            case "Car empty" -> !servedCar.isEmpty() && fillPct == 0 ? 15 : 0;
            default -> fillPct <= 0 ? 0 : Math.max(1, Math.min(15, fillPct * 15 / 100));
        };
    }

    public String statusLine() {
        String job = loader ? "Loading silo" : "Unloading pit";
        if (servedCar.isEmpty()) {
            return job + " — no car in range (park a freight car within " + cfg.num("reach") + " blocks)";
        }
        String rate = movedLastCycle > 0 ? movedLastCycle * 20 / Math.max(1, cfg.num("period")) + "/s" : "idle";
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
        tag.setLong("moved", totalMoved);
        tag.setInteger("cars", carsServed);
        cfg.write(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fillPct = tag.hasKey("fill") ? tag.getInteger("fill") : -1;
        scale = tag.hasKey("scale") ? com.dogpound.railmap.signal.IScalable.clamp(tag.getFloat("scale")) : 1f;
        totalMoved = tag.getLong("moved");
        carsServed = tag.getInteger("cars");
        cfg.read(tag);
    }
}
