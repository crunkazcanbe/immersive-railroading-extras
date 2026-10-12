package com.dogpound.railmap.block;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.freight.ContractData;
import com.dogpound.railmap.industry.TileIndustry;
import com.dogpound.railmap.item.ItemContainer;
import com.dogpound.railmap.proxy.CommonProxy;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.*;

/**
 * The Container Terminal (her ChatGPT list #47): a rail-mounted gantry crane over a container yard, worked from the
 * operator's desk. Behind the desk lie the truck APRON (where boxes are gated in and out, and stuffed from / stripped
 * into the warehouse - any inventory touching the desk), the RAIL lane (lay a track parallel to the desk, 4-5 blocks
 * behind it: the IR freight car standing there), and two stack rows (ROW A, ROW B) three high. The crane really
 * moves: trolley and hoist travel at crane speeds, the spreader has to be down on a box to LOCK its twistlocks, and a
 * box can only be set down where there is room. MOVE BOX runs the whole lift from FROM to TO by itself.
 * Every lift costs the box's owner a handling fee; boxes in the stack pay storage by the day and demurrage after two
 * days (with an alarm); fees go to the terminal owner's company. Goods in a box count for freight contracts when the
 * box goes on or comes off a car here.
 */
public class TileContainerTerminal extends TileFurniture {
    public static final String[] STORE_IDS = {"locker"};
    public static final String[] STORE_NAMES = {"Operator's Locker"};
    public static final int[] STORE_SIZES = {9};
    public static final String[] POS_NAMES = {"APRON", "RAIL", "ROW A", "ROW B"};
    /** where each position lies behind the desk (blocks from the block centre) */
    public static final double[] POS_D = {2.5, 4.5, 7.0, 9.0};
    public static final double BOX_H = 2.6, DECK = 1.1, TRAVEL = 9.2, T_MIN = 1.5, T_MAX = 10.0;
    public static final int TIERS = 3;
    private static final double SPEED_T = 0.1, SPEED_H = 0.06;
    private static final long LIFT_FEE = 250, STORE_FEE = 100, DEMURRAGE = 500, NEW_BOX = 5_000;

    // ---- the yard and the crane (synced to clients for the renderer) --------------------------------------------------
    public ItemStack apron = ItemStack.EMPTY, carrying = ItemStack.EMPTY;
    public final ItemStack[][] rows = {new ItemStack[TIERS], new ItemStack[TIERS]};
    public double trolley = 3.0, hoist = TRAVEL;
    public boolean locked;
    public boolean carHere;
    /** client: what is drawn, easing toward trolley / hoist */
    public double drawT = 3.0, drawH = TRAVEL;
    private final Deque<double[]> queue = new ArrayDeque<>();     // {op, value}: 'M' move, 'H' hoist, 'P' lower to pick, 'D' lower to drop, 'L' lock, 'U' unlock
    private String fault = "";
    private long feesToday, feesDay;
    private int syncIn;

    public TileContainerTerminal() {
        super(STORE_IDS, STORE_NAMES, STORE_SIZES, 0);
        for (ItemStack[] r : rows) Arrays.fill(r, ItemStack.EMPTY);
    }

    private boolean running() { return term.powered() && ctl.get("power") == 0 && ctl.get("estop") == 0; }
    @Override protected boolean mechanical(String id) { return id.equals("power") || id.equals("estop"); }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (id.equals("estop")) {
            ctl.set("estop", ctl.get("estop") == 0 ? 1 : 0);
            if (ctl.get("estop") == 1) queue.clear();
            return ctl.get("estop") == 1 ? "§cEMERGENCY STOP - crane halted" : "§aE-stop released";
        }
        return null;
    }

    // ---- geometry ------------------------------------------------------------------------------------------------------
    private EnumFacing back() { return controlFacing().getOpposite(); }
    private EnumFacing right() { return controlFacing().rotateYCCW(); }

    /** the position the trolley is over (within 0.3 block), or -1 */
    private int posAt(double d) { for (int i = 0; i < POS_D.length; i++) if (Math.abs(POS_D[i] - d) < 0.3) return i; return -1; }

    private int tiers(int row) { int n = 0; for (ItemStack s : rows[row]) if (!s.isEmpty()) n++; return n; }

    /** height of the top of whatever stands at position p (0 = bare ground) */
    private double top(int p) {
        return switch (p) {
            case 0 -> apron.isEmpty() ? 0 : BOX_H;
            case 1 -> carHere ? DECK + (carBoxes() > 0 ? BOX_H : 0) : 0;
            default -> tiers(p - 2) * BOX_H;
        };
    }

    private boolean hasBox(int p) { return p == 0 ? !apron.isEmpty() : p == 1 ? carBoxes() > 0 : tiers(p - 2) > 0; }

    // ---- the car on the rail lane ------------------------------------------------------------------------------------
    private UUID carId;
    private IItemHandler carInv;

    private void findCar() {
        carHere = false; carId = null; carInv = null;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        EnumFacing b = back(), r = right();
        try {
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            double bestD = Double.MAX_VALUE;
            if (w != null)
                for (EntityRollingStock s : w.getEntities(EntityRollingStock.class)) {
                    if (s.isDead() || !(s instanceof Freight f) || f.cargoItems == null) continue;
                    cam72cam.mod.math.Vec3d p = s.getPosition();
                    double dx = p.x - cx, dz = p.z - cz;
                    double along = dx * b.getXOffset() + dz * b.getZOffset(), side = dx * r.getXOffset() + dz * r.getZOffset();
                    if (Math.abs(along - POS_D[1]) > 2 || Math.abs(side) > 5 || Math.abs(p.y - pos.getY()) > 3) continue;
                    if (s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null && Math.abs(m.getCurrentSpeed().metric()) > 2) continue;
                    double d = Math.abs(side);
                    if (d < bestD) { bestD = d; carHere = true; carId = s.getUUID(); carInv = f.cargoItems.internal; }
                }
        } catch (LinkageError | RuntimeException ignored) { }
        if (carHere) return;
        BlockPos lane = pos.offset(b, 4);                                       // any other mod's wagon / minecart with an inventory
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(lane).grow(4, 2, 4))) {
            if (e instanceof EntityPlayer || e.isDead) continue;
            IItemHandler h = e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ? e.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
                    : e instanceof IInventory inv && inv.getSizeInventory() > 0 ? new InvWrapper(inv) : null;
            if (h == null) continue;
            carHere = true; carId = e.getUniqueID(); carInv = h;
            return;
        }
    }

    private int carBoxes() {
        if (carInv == null) return 0;
        int n = 0;
        for (int i = 0; i < carInv.getSlots(); i++) if (ItemContainer.is(carInv.getStackInSlot(i))) n++;
        return n;
    }

    // ---- the warehouse: any inventory touching the desk ----------------------------------------------------------------
    private IItemHandler warehouse() {
        for (EnumFacing d : EnumFacing.values()) {
            TileEntity te = world.getTileEntity(pos.offset(d));
            if (te != null && !(te instanceof TileFurniture) && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, d.getOpposite()))
                return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, d.getOpposite());
        }
        return null;
    }

    /** the industry this terminal serves (freight contracts count boxes on / off cars here as that industry's yard) */
    private BlockPos yard() {
        Set<Long> l = TileIndustry.LOADED.get(world.provider.getDimension());
        BlockPos best = null;
        double bd = 48 * 48;
        if (l != null) for (long k : l) { BlockPos p = BlockPos.fromLong(k); double d = p.distanceSq(pos); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    // ---- the crane ---------------------------------------------------------------------------------------------------
    @Override
    public void update() {
        if (world.isRemote) {                                                    // ease the drawn crane toward the real one
            drawT += Math.max(-SPEED_T * 1.5, Math.min(SPEED_T * 1.5, trolley - drawT));
            drawH += Math.max(-SPEED_H * 1.5, Math.min(SPEED_H * 1.5, hoist - drawH));
            return;
        }
        super.update();
    }

    @Override
    protected void serverTick() {
        if (age % 20 == 0) findCar();
        if (age % 1200 == 7) bill();
        if (queue.isEmpty() || !running()) return;
        double[] st = queue.peek();
        char op = (char) st[0];
        boolean done;
        switch (op) {
            case 'M' -> { trolley = toward(trolley, st[1], SPEED_T); done = trolley == st[1]; }
            case 'H', 'P', 'D' -> {
                if (op != 'H' && st[1] < 0) {
                    int p = posAt(trolley);
                    if (p < 0) { fail("The spreader isn't over a container slot"); return; }
                    st[1] = op == 'P' ? top(p) : top(p) + BOX_H;
                    if (op == 'P' && !hasBox(p)) { fail("Nothing to pick up at " + POS_NAMES[p]); return; }
                }
                hoist = toward(hoist, st[1], SPEED_H);
                done = hoist == st[1];
            }
            case 'L' -> { String r = lock(); if (r != null) { fail(r); return; } done = true; }
            default -> { String r = unlock(); if (r != null) { fail(r); return; } done = true; }
        }
        if (done) queue.poll();
        if (done || --syncIn <= 0) { syncIn = 4; controlsChanged(); }
    }

    private static double toward(double v, double t, double s) { return Math.abs(t - v) <= s ? t : v + Math.signum(t - v) * s; }

    private void fail(String why) {
        queue.clear();
        fault = why;
        ctl.set("fault", 1);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.8f, 1.0f);
        controlsChanged();
    }

    private void travel(int p) { queue.add(new double[]{'H', TRAVEL}); queue.add(new double[]{'M', POS_D[p]}); }
    private void pick() { queue.add(new double[]{'P', -1}); queue.add(new double[]{'L', 0}); queue.add(new double[]{'H', TRAVEL}); }
    private void drop() { queue.add(new double[]{'D', -1}); queue.add(new double[]{'U', 0}); queue.add(new double[]{'H', TRAVEL}); }

    private String lock() {
        int p = posAt(trolley);
        if (p < 0) return "The spreader isn't over a container slot";
        if (!carrying.isEmpty()) return "The spreader already holds a box";
        if (!hasBox(p) || Math.abs(hoist - top(p)) > 0.15) return "The spreader isn't down on a container";
        ItemStack box = ItemStack.EMPTY;
        switch (p) {
            case 0 -> { box = apron; apron = ItemStack.EMPTY; }
            case 1 -> {
                for (int i = 0; i < carInv.getSlots() && box.isEmpty(); i++)
                    if (ItemContainer.is(carInv.getStackInSlot(i))) box = carInv.extractItem(i, 1, false);
                if (box.isEmpty()) return "The car's box won't come free";
                BlockPos y = yard();
                if (y != null && carId != null) ContractData.get(world).onMoved(world, false, y, carId, Collections.singletonList(box.copy()));
                com.dogpound.railmap.biz.BizData.get(world).custody.event(world, box, "RAIL UNLOAD", pos, placerName, "lifted off car " + carId.toString().substring(0, 8) + " at the terminal");   // #15/#17 tracking
                ItemContainer.setSince(box, world.getTotalWorldTime());
                ItemContainer.setBilled(box, world.getTotalWorldTime());
            }
            default -> {
                ItemStack[] r = rows[p - 2];
                for (int i = TIERS - 1; i >= 0; i--) if (!r[i].isEmpty()) { box = r[i]; r[i] = ItemStack.EMPTY; break; }
            }
        }
        carrying = box;
        locked = true;
        charge(box, LIFT_FEE, "lift at " + POS_NAMES[p]);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.9f, 0.9f);
        return null;
    }

    private String unlock() {
        int p = posAt(trolley);
        if (p < 0) return "The spreader isn't over a container slot";
        if (carrying.isEmpty()) { locked = false; return null; }
        if (Math.abs(hoist - (top(p) + BOX_H)) > 0.15) return "The box isn't set down - lower it first";
        switch (p) {
            case 0 -> { if (!apron.isEmpty()) return "The apron is taken"; apron = carrying; }
            case 1 -> {
                if (!carHere || carInv == null) return "No car on the rail lane";
                ItemStack left = ItemHandlerHelper.insertItem(carInv, carrying.copy(), false);
                if (!left.isEmpty()) return "The car has no room for the box";
                BlockPos y = yard();
                if (y != null && carId != null) ContractData.get(world).onMoved(world, true, y, carId, Collections.singletonList(carrying.copy()));
                com.dogpound.railmap.biz.BizData.get(world).custody.event(world, carrying, "RAIL LOAD", pos, placerName, "set on car " + (carId == null ? "?" : carId.toString().substring(0, 8)) + (ItemContainer.destName(carrying).isEmpty() ? "" : " for " + ItemContainer.destName(carrying)));
            }
            default -> {
                ItemStack[] r = rows[p - 2];
                int t = tiers(p - 2);
                if (t >= TIERS) return POS_NAMES[p] + " is stacked " + TIERS + " high";
                r[t] = carrying;
                if (ItemContainer.since(carrying) == 0) ItemContainer.setSince(carrying, world.getTotalWorldTime());
            }
        }
        carrying = ItemStack.EMPTY;
        locked = false;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.9f, 1.05f);
        return null;
    }

    // ---- fees ----------------------------------------------------------------------------------------------------------
    private Company operator() { return placer == null ? null : CompanyData.get(world).of(placer); }

    /** the owner pays now if they are online, else it goes on the box's account (collected at gate-out) */
    private void charge(ItemStack box, long cents, String why) {
        EntityPlayerMP o = CompanyData.online(world, ItemContainer.owner(box));
        if (o != null && Coins.charge(o, cents, "Container " + ItemContainer.pretty(box) + " " + why)) collected(cents, "Box " + ItemContainer.pretty(box) + ": " + why);
        else ItemContainer.addFees(box, cents);
    }

    private void collected(long cents, String memo) {
        long day = world.getTotalWorldTime() / CompanyData.DAY;
        if (day != feesDay) { feesDay = day; feesToday = 0; }
        feesToday += cents;
        Company c = operator();
        if (c != null) CompanyData.get(world).credit(world, c, Company.Kind.HANDLING, cents, memo);
    }

    /** storage by the day; demurrage and an alarm after two days in the stack */
    private void bill() {
        long now = world.getTotalWorldTime();
        com.dogpound.railmap.biz.Custody cu = com.dogpound.railmap.biz.BizData.get(world).custody;   // #17: boxes in the open yard sweat / warm up
        cu.exposure(world, apron, pos, 1200);
        com.dogpound.railmap.biz.Intermodal.Stat st = com.dogpound.railmap.biz.BizData.get(world).intermodal.stat(world, pos, false);   // #15 throughput for the console
        st.quay = boxes().size(); st.quayCap = 1 + 2 * TIERS;
        for (ItemStack[] r : rows) for (ItemStack b : r) cu.exposure(world, b, pos, 1200);
        for (ItemStack[] r : rows)
            for (ItemStack b : r) {
                if (b.isEmpty()) continue;
                long since = ItemContainer.since(b), billed = Math.max(since, ItemContainer.lastBilled(b));
                long days = (now - billed) / CompanyData.DAY;
                if (days < 1) continue;
                boolean over = now - since > 2 * CompanyData.DAY;
                ItemContainer.addFees(b, days * (STORE_FEE + (over ? DEMURRAGE : 0)));
                ItemContainer.setBilled(b, billed + days * CompanyData.DAY);
                if (over) AlarmManager.get(world).raise(world, "box:" + ItemContainer.id(b), AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MINOR,
                        "Container " + ItemContainer.pretty(b) + " overdue in the terminal (" + (now - since) / CompanyData.DAY + " days) - demurrage", pos, false);
            }
        controlsChanged();
    }

    private boolean overdue() {
        long now = world.getTotalWorldTime();
        for (ItemStack[] r : rows) for (ItemStack b : r) if (!b.isEmpty() && ItemContainer.since(b) > 0 && now - ItemContainer.since(b) > 2 * CompanyData.DAY) return true;
        return false;
    }

    // ---- the desk -------------------------------------------------------------------------------------------------------
    private int sel(String id) { return Math.floorMod(ctl.get(id), POS_NAMES.length); }

    @Override
    protected String press(String id, EntityPlayer who) {
        ctl.set("fault", 0);
        fault = "";
        switch (id) {
            case "from": case "to": ctl.set(id, (sel(id) + 1) % POS_NAMES.length); return "§7" + id.toUpperCase(Locale.ROOT) + ": " + POS_NAMES[sel(id)];
            default: break;
        }
        if (!com.dogpound.railmap.economy.Perms.has(who, com.dogpound.railmap.economy.Perms.CRANE)) return com.dogpound.railmap.economy.Perms.no("operate the terminal");
        boolean crane = "auto go pick drop tl tr hu hd lock unlock".contains(id);
        if (crane && !running()) return ctl.get("estop") == 1 ? "§cE-stop is in" : "§cThe crane is switched OFF (POWER key)";
        if (crane && !queue.isEmpty() && !id.equals("auto")) return "§7The crane is busy - wait for it to finish";
        int p = posAt(trolley);
        switch (id) {
            case "auto": {
                int f = sel("from"), t = sel("to");
                if (f == t) return "§7FROM and TO are the same place";
                if (!queue.isEmpty()) return "§7The crane is busy";
                if (!carrying.isEmpty()) return "§7Set down the box it holds first";
                travel(f); pick(); travel(t); drop();
                return "§eMoving a box: " + POS_NAMES[f] + " -> " + POS_NAMES[t];
            }
            case "go": travel(sel("from")); return "§7Trolley to " + POS_NAMES[sel("from")];
            case "pick": if (p < 0) return "§7Drive the trolley over a slot first"; pick(); return "§7Lifting at " + POS_NAMES[p];
            case "drop": if (p < 0) return "§7Drive the trolley over a slot first"; drop(); return "§7Setting down at " + POS_NAMES[p];
            case "tl": queue.add(new double[]{'M', Math.max(T_MIN, trolley - 1)}); return "";
            case "tr": queue.add(new double[]{'M', Math.min(T_MAX, trolley + 1)}); return "";
            case "hu": queue.add(new double[]{'H', Math.min(TRAVEL, hoist + 1)}); return "";
            case "hd": {
                double floor = p < 0 ? (carrying.isEmpty() ? 0 : BOX_H) : top(p) + (carrying.isEmpty() ? 0 : BOX_H);
                if (p < 0) floor = Math.max(floor, maxTopUnder() + (carrying.isEmpty() ? 0 : BOX_H));
                queue.add(new double[]{'H', Math.max(floor, hoist - 1)});
                return hoist - 1 < floor ? "§7Down on the stack" : "";
            }
            case "lock": { String r = lock(); controlsChanged(); return r == null ? "§aTwistlocks LOCKED - " + ItemContainer.pretty(carrying) : "§c" + r; }
            case "unlock": { String r = unlock(); controlsChanged(); return r == null ? "§aTwistlocks released" : "§c" + r; }
            default: break;
        }
        if (!(who instanceof EntityPlayerMP pl)) return "§7That is done at the gate, in person";
        switch (id) {
            case "gatein": {
                if (!apron.isEmpty()) return "§7The apron is taken - lift that box away first";
                String full = com.dogpound.railmap.biz.BizData.get(world).intermodal.gateRefuse(world, pos, pl);   // #15 truck appointments
                if (full != null) return full;
                ItemStack h = pl.getHeldItemMainhand();
                if (ItemContainer.is(h)) {
                    apron = h.splitStack(1);
                    if (!apron.hasTagCompound() || !apron.getTagCompound().hasKey("cid")) apron = numbered(pl);
                } else {
                    if (!h.isEmpty()) return "§7Hold a container to gate it in (or nothing, to hire a new one)";
                    if (!Coins.charge(pl, NEW_BOX, "New container")) return "§cA new container costs " + Coins.fmt(NEW_BOX);
                    apron = numbered(pl);
                    collected(NEW_BOX, "Container hire to " + pl.getName());
                }
                ItemContainer.setSince(apron, world.getTotalWorldTime());
                ItemContainer.setBilled(apron, world.getTotalWorldTime());
                String appt = com.dogpound.railmap.biz.BizData.get(world).intermodal.gate(world, pos, pl, true);   // #15 truck appointments
                com.dogpound.railmap.biz.BizData.get(world).custody.event(world, apron, "TRUCK", pos, pl.getName(), "gated in by truck" + appt);
                return "§aGated in: " + ItemContainer.pretty(apron) + appt;
            }
            case "gateout": {
                if (apron.isEmpty()) return "§7No box on the apron";
                String full = com.dogpound.railmap.biz.BizData.get(world).intermodal.gateRefuse(world, pos, pl);   // #15 truck appointments
                if (full != null) return full;
                long due = ItemContainer.fees(apron);
                if (due > 0) {
                    if (!Coins.charge(pl, due, "Terminal fees, " + ItemContainer.pretty(apron))) return "§c" + Coins.fmt(due) + " in fees are due on that box";
                    collected(due, "Fees on " + ItemContainer.pretty(apron) + " at gate-out");
                    ItemContainer.addFees(apron, -due);
                }
                ItemContainer.setSince(apron, 0);
                String appt = com.dogpound.railmap.biz.BizData.get(world).intermodal.gate(world, pos, pl, false);   // #15 truck appointments
                com.dogpound.railmap.biz.BizData.get(world).custody.event(world, apron, "TRUCK", pos, pl.getName(), "gated out by truck" + appt);
                if (!pl.inventory.addItemStackToInventory(apron)) pl.dropItem(apron, false);
                String n = ItemContainer.pretty(apron);
                apron = ItemStack.EMPTY;
                return "§aGated out: " + n + (due > 0 ? " (fees " + Coins.fmt(due) + " paid)" : "");
            }
            case "stuff": case "strip": {
                if (apron.isEmpty()) return "§7Stuffing and stripping happen on the apron - bring a box there";
                IItemHandler wh = warehouse();
                if (wh == null) return "§7No warehouse: put a chest (or any inventory) against the desk";
                List<ItemStack> in = ItemContainer.contents(apron);
                int moved = 0;
                if (id.equals("stuff")) {
                    List<ItemStack> added = new ArrayList<>();
                    for (int i = 0; i < wh.getSlots() && in.size() < ItemContainer.SLOTS; i++) {
                        ItemStack x = wh.extractItem(i, 64, false);
                        if (!x.isEmpty()) { in.add(x); added.add(x); moved += x.getCount(); }
                    }
                    ItemContainer.setContents(apron, in);
                    com.dogpound.railmap.biz.BizData.get(world).custody.stuffed(world, apron, added, pos, pl.getName());   // #17 seal / contamination / segregation
                } else {
                    long value = 0;
                    for (ItemStack x : in) { com.dogpound.railmap.economy.MarketData.Price q = com.dogpound.railmap.economy.MarketData.get(world).of(x); if (q != null) value += q.price * x.getCount(); }
                    String refused = com.dogpound.railmap.biz.BizData.get(world).custody.delivery(world, apron, pos, pl.getName(), value);   // #17 the consignee inspects
                    if (refused != null) return "§cREJECTED by the consignee: " + refused + " - the goods stay in the box (investigation opened)";
                    com.dogpound.railmap.biz.BizData.get(world).intermodal.delivered(world, apron, pl);   // #15 through-freight paid on delivery
                    List<ItemStack> keep = new ArrayList<>();
                    for (ItemStack x : in) { ItemStack left = ItemHandlerHelper.insertItem(wh, x, false); moved += x.getCount() - left.getCount(); if (!left.isEmpty()) keep.add(left); }
                    in = keep;
                    com.dogpound.railmap.biz.BizData.get(world).custody.stripped(world, apron, pos, pl.getName());
                }
                ItemContainer.setContents(apron, in);
                charge(apron, moved / 64 * 20 + 50, id.equals("stuff") ? "stuffing" : "stripping");
                return "§a" + (id.equals("stuff") ? "Stuffed " : "Stripped ") + moved + " items · box holds " + ItemContainer.manifest(apron);
            }
            case "appt": return com.dogpound.railmap.biz.BizData.get(world).intermodal.book(world, pos, pl.getName());   // #15 truck appointment
            case "wash": {                                                       // #17 wash the box on the apron
                if (apron.isEmpty()) return "§7Bring the box to the apron to wash it";
                if (ItemContainer.count(apron) > 0) return "§7Strip the box before washing it";
                charge(apron, 300, "washing");
                com.dogpound.railmap.biz.BizData.get(world).custody.wash(world, apron, pos, pl.getName());
                return "§aBox " + ItemContainer.pretty(apron) + " washed and inspected clean";
            }
            case "dest": {
                if (apron.isEmpty()) return "§7Seal a box on the apron";
                List<Map.Entry<Long, String>> st = new ArrayList<>(StationData.get(world).names().entrySet());
                if (st.isEmpty()) return "§7No named stations to send it to";
                st.sort(Map.Entry.comparingByValue());
                int i = 0;
                for (int k = 0; k < st.size(); k++) if (st.get(k).getKey() == ItemContainer.dest(apron)) i = k + 1;
                Map.Entry<Long, String> d = st.get(i % st.size());
                ItemContainer.setDest(apron, d.getKey(), d.getValue());
                int sealNo = com.dogpound.railmap.biz.BizData.get(world).custody.seal(world, apron, pos, pl.getName());   // #17 numbered seal
                for (Contract k : ContractData.get(world).all())          // which waybill the goods travel under
                    if (k.state == Contract.State.ACCEPTED && pl.getUniqueID().equals(k.by))
                        for (ItemStack x : ItemContainer.contents(apron)) if (k.matches(x)) { ItemContainer.setContract(apron, k.id); break; }
                return "§aSealed (seal " + sealNo + ") for " + d.getValue() + (ItemContainer.contract(apron) > 0 ? " under contract #" + ItemContainer.contract(apron) : "");
            }
            default: return null;
        }
    }

    private double maxTopUnder() { double m = 0; for (int i = 0; i < POS_D.length; i++) if (Math.abs(POS_D[i] - trolley) < 1.3) m = Math.max(m, top(i)); return m; }

    private ItemStack numbered(EntityPlayerMP pl) {
        Company c = CompanyData.get(world).of(pl.getUniqueID());
        String code = c != null ? c.mark : pl.getName().replaceAll("[^A-Za-z]", "");
        return ItemContainer.make(CommonProxy.container, code, world.rand.nextInt(1_000_000), pl.getUniqueID(), pl.getName());
    }

    // ---- screens and lamps --------------------------------------------------------------------------------------------
    @Override
    protected boolean refresh() {
        boolean ch = false;
        int p = posAt(trolley);
        ItemStack focus = !carrying.isEmpty() ? carrying : apron;
        ch |= StationControls.text(ctl, "box", focus.isEmpty() ? "NO BOX" : ItemContainer.pretty(focus));
        ch |= StationControls.text(ctl, "cnt", focus.isEmpty() ? "" : cut(ItemContainer.manifest(focus).toUpperCase(Locale.ROOT), 24));
        ch |= StationControls.text(ctl, "wt", focus.isEmpty() ? "" : String.format(Locale.ROOT, "%.1f T", ItemContainer.weightKg(focus) / 1000.0));
        ch |= StationControls.text(ctl, "dst", focus.isEmpty() || ItemContainer.destName(focus).isEmpty() ? "" : "TO " + cut(ItemContainer.destName(focus).toUpperCase(Locale.ROOT), 14));
        ch |= StationControls.text(ctl, "pos", (p < 0 ? String.format(Locale.ROOT, "%.1f M", trolley) : POS_NAMES[p]) + String.format(Locale.ROOT, " H%.1f", hoist));
        ch |= StationControls.text(ctl, "stat", !fault.isEmpty() ? cut(fault.toUpperCase(Locale.ROOT), 30) : !queue.isEmpty() ? "WORKING" : running() ? "READY - " + POS_NAMES[sel("from")] + " > " + POS_NAMES[sel("to")] : "STOPPED");
        ch |= StationControls.text(ctl, "fees", Coins.fmt(feesToday));
        ch |= StationControls.text(ctl, "fromto", POS_NAMES[sel("from")] + " > " + POS_NAMES[sel("to")]);
        ch |= StationControls.text(ctl, "m1", "APRON  " + (apron.isEmpty() ? "-" : ItemContainer.pretty(apron) + (ItemContainer.fees(apron) > 0 ? " DUE " + Coins.fmt(ItemContainer.fees(apron)) : "")));
        ch |= StationControls.text(ctl, "m2", "RAIL   " + (carHere ? carBoxes() + " BOX(ES) ON THE CAR" : "NO CAR"));
        ch |= StationControls.text(ctl, "m3", "ROW A  " + stack(0));
        ch |= StationControls.text(ctl, "m4", "ROW B  " + stack(1));
        ch |= StationControls.put(ctl, "pw", term.powered());
        ch |= StationControls.put(ctl, "on", running());
        ch |= StationControls.put(ctl, "locked", locked);
        ch |= StationControls.put(ctl, "moving", !queue.isEmpty());
        ch |= StationControls.put(ctl, "car", carHere);
        ch |= StationControls.put(ctl, "wh", warehouse() != null);
        ch |= StationControls.put(ctl, "overdue", overdue());
        ch |= StationControls.put(ctl, "load", carrying.isEmpty() ? 0 : (int) (ItemContainer.weightKg(carrying) / 1000));
        return ch;
    }

    private String stack(int r) {
        StringBuilder b = new StringBuilder();
        for (ItemStack s : rows[r]) if (!s.isEmpty()) b.append(b.length() == 0 ? "" : " / ").append(ItemContainer.id(s).substring(0, Math.min(4, ItemContainer.id(s).length()))).append(ItemContainer.id(s).length() > 10 ? ItemContainer.id(s).substring(7, 10) : "");
        return b.length() == 0 ? "EMPTY" : b.toString();
    }

    @Override
    public void dropAll() {
        super.dropAll();
        List<ItemStack> all = new ArrayList<>(Arrays.asList(apron, carrying));
        for (ItemStack[] r : rows) all.addAll(Arrays.asList(r));
        for (ItemStack s : all) if (!s.isEmpty()) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, s);
    }

    /** every box in the yard, for the console */
    public List<String[]> boxes() {
        List<String[]> out = new ArrayList<>();
        long now = world.getTotalWorldTime();
        java.util.function.BiConsumer<ItemStack, String> add = (s, where) -> { if (!s.isEmpty()) out.add(new String[]{ItemContainer.pretty(s), where, ItemContainer.destName(s), ItemContainer.manifest(s),
                ItemContainer.since(s) > 0 ? String.format(Locale.ROOT, "%.1f", (now - ItemContainer.since(s)) / (double) CompanyData.DAY) : "-", Coins.fmt(ItemContainer.fees(s))}); };
        add.accept(apron, "APRON");
        add.accept(carrying, "ON THE CRANE");
        for (int r = 0; r < 2; r++) for (int t = 0; t < TIERS; t++) add.accept(rows[r][t], POS_NAMES[r + 2] + " tier " + (t + 1));
        return out;
    }

    // ---- console + save ------------------------------------------------------------------------------------------------
    @Override public String wiredName() { return "Container Terminal"; }
    @Override public String gearState() { return (running() ? "crane ON" : "crane OFF") + " · " + boxes().size() + " boxes · " + (queue.isEmpty() ? "idle" : "working"); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return INFINITE_EXTENT_AABB; }

    @Override
    protected void writeExtra(NBTTagCompound t) {
        t.setTag("apron", apron.writeToNBT(new NBTTagCompound()));
        t.setTag("carry", carrying.writeToNBT(new NBTTagCompound()));
        NBTTagList l = new NBTTagList();
        for (ItemStack[] r : rows) for (ItemStack s : r) l.appendTag(s.writeToNBT(new NBTTagCompound()));
        t.setTag("rows", l);
        t.setDouble("trolley", trolley); t.setDouble("hoist", hoist); t.setBoolean("locked", locked); t.setBoolean("car", carHere);
        t.setString("fault", fault); t.setLong("fees", feesToday); t.setLong("feesDay", feesDay);
        NBTTagList q = new NBTTagList();
        for (double[] s : queue) { NBTTagCompound x = new NBTTagCompound(); x.setDouble("o", s[0]); x.setDouble("v", s[1]); q.appendTag(x); }
        t.setTag("queue", q);
    }

    @Override
    protected void readExtra(NBTTagCompound t) {
        apron = new ItemStack(t.getCompoundTag("apron"));
        carrying = new ItemStack(t.getCompoundTag("carry"));
        NBTTagList l = t.getTagList("rows", 10);
        for (int i = 0; i < 2 * TIERS; i++) rows[i / TIERS][i % TIERS] = i < l.tagCount() ? new ItemStack(l.getCompoundTagAt(i)) : ItemStack.EMPTY;
        if (t.hasKey("trolley")) { trolley = t.getDouble("trolley"); hoist = t.getDouble("hoist"); }
        locked = t.getBoolean("locked"); carHere = t.getBoolean("car");
        fault = t.getString("fault"); feesToday = t.getLong("fees"); feesDay = t.getLong("feesDay");
        queue.clear();
        NBTTagList q = t.getTagList("queue", 10);
        for (int i = 0; i < q.tagCount(); i++) queue.add(new double[]{q.getCompoundTagAt(i).getDouble("o"), q.getCompoundTagAt(i).getDouble("v")});
        if (world == null || !world.isRemote) return;
        if (Math.abs(drawT - trolley) > 4) drawT = trolley;
        if (Math.abs(drawH - hoist) > 4) drawH = hoist;
    }
}
