package com.dogpound.railmap.shop;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Coffee Kiosk (station shop #5): a two-group espresso machine, a milk fridge and a menu board. The owner fills the bean
 * hopper (cocoa beans stand in for coffee beans), the milk fridge (milk buckets: eight steamed portions each, the empty
 * bucket stays in the fridge) and the cup store (paper: four cups a sheet). The BOILER key heats the boiler on mains
 * (30 FE a second heating, 6 holding) - the pressure and temperature gauges climb, READY lights at 0.9 bar. Order with
 * the drink buttons (SIZE and SHOTS selectors set it), pay, and it is made in front of you: the grinder grinds a bean
 * into four shots when it needs them, the group pulls the shot, the steam wand steams the milk, and the drink is handed
 * over. Every drink dirties the group: after ShopConfig.drinksBetweenCleans the CLEAN lamp flashes, and past half as
 * many again it refuses until CLEAN runs a backflush. Station passengers buy coffee too (ShopConfig.paxPerCoffee).
 */
public class TileCoffeeKiosk extends ShopTile {
    static double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};   // tools/gen_bb_shops.py
    static final long[] BASE = {150, 220, 300, 300, 340};            // cents, regular size, single shot
    static final String[] SIZES = {"SMALL", "REGULAR", "LARGE"};
    static final double[] SIZE_X = {0.8, 1.0, 1.3};
    static final int[] MILK = {0, 0, 2, 1, 1};
    static final Predicate<ItemStack> BEAN = s -> s.getItem() == Items.DYE && s.getMetadata() == 3;
    static final Predicate<ItemStack> MILKB = s -> s.getItem() == Items.MILK_BUCKET;
    static final Predicate<ItemStack> PAPER = s -> s.getItem() == Items.PAPER;
    static final Predicate<ItemStack> SUGAR = s -> s.getItem() == Items.SUGAR;

    private double temp = 20;
    private int shots, milk, cups, dirty, brewT = -1, brewKind, brewShots, cleanT;
    private String brewSize = "";
    private UUID orderBy;

    public TileCoffeeKiosk() {
        super(new String[]{"beans", "fridge", "cups"}, new String[]{"Bean hopper (cocoa beans)", "Milk fridge (milk buckets)", "Cups + sugar (paper = 4 cups)"}, new int[]{9, 9, 9}, 6);
    }

    @Override public String wiredName() { return "Coffee kiosk"; }
    @Override public String facilityKind() { return "Coffee kiosk"; }
    @Override public int facilityPoints() { return 8; }
    @Override public long dailyCost() { return 80; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean ownerOnly(int k) { return true; }
    @Override protected int paxEvery() { return ShopConfig.paxPerCoffee; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }
    @Override protected long load() { return boilerOn() ? temp < 118 ? 30 : 6 : 4; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : (open() ? "OPEN" : "closed") + String.format(Locale.ROOT, " · boiler %.1f bar %.0f C · %d sold", bar(), temp, soldToday); }
    @Override public String stockNote() { return beans() + " beans/" + shots + " shots, milk " + (milk + 8 * milkBuckets()) + ", cups " + (cups + 4 * count(storage(2), PAPER)); }

    boolean boilerOn() { return ctl.get("k_boiler") == 1 && live(); }
    double bar() { return Math.max(0, Math.min(1.4, (temp - 100) / 20 * 1.2)); }
    boolean ready() { return bar() >= 0.9; }
    int beans() { return count(storage(0), BEAN); }
    int milkBuckets() { return count(storage(1), MILKB); }
    int size() { return Math.floorMod(ctl.get("s_size"), 3); }
    int nShots() { return ctl.get("s_shots") == 1 ? 2 : 1; }
    long price(int kind) { return Math.round(BASE[kind] * SIZE_X[size()] + (nShots() - 1) * 60) / 10 * 10; }
    int limit() { return ShopConfig.drinksBetweenCleans * 3 / 2; }

    // ---- ingredients ------------------------------------------------------------------------------------------------
    private boolean haveShots(int n) { while (shots < n) { if (!take(storage(0), BEAN, 1)) return false; shots += 4; grindSound(); } return true; }
    private boolean haveMilk(int n) {
        while (milk < n) {
            if (!take(storage(1), MILKB, 1)) return false;
            milk += 8;
            com.dogpound.railmap.stn.StnFurniture.insert(storage(1), new ItemStack(Items.BUCKET));
        }
        return true;
    }
    private boolean haveCup() { if (cups > 0) return true; if (!take(storage(2), PAPER, 1)) return false; cups += 4; return true; }
    private void grindSound() { world.playSound(null, pos, com.dogpound.railmap.ems.EmsSounds.COFFEE_GRIND, SoundCategory.BLOCKS, 0.6f, 1.0f); }

    /** why a drink can't be made now (null = it can, ingredients reserved) */
    private String make(int kind, int nShots) {
        if (!ready()) return boilerOn() ? "§eThe boiler is still heating (" + String.format(Locale.ROOT, "%.1f bar", bar()) + ")" : "§cThe boiler is off (BOILER key)";
        if (cleanT > 0) return "§eBackflush running";
        if (dirty >= limit()) return "§cThe machine needs cleaning - press CLEAN";
        if (brewT >= 0) return "§eAlready making one - wait for it";
        if (!haveCup()) return "§cOut of cups (paper in the cup store)";
        if (!haveShots(nShots)) return "§cOut of beans (cocoa beans in the hopper)";
        if (!haveMilk(MILK[kind])) return "§cOut of milk (milk buckets in the fridge)";
        cups--; shots -= nShots; milk -= MILK[kind]; dirty++;
        return null;
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        int kind = -1;
        for (int i = 0; i < ItemDrink.NAMES.length; i++) if (id.equals(ItemDrink.NAMES[i])) kind = i;
        if (kind >= 0) {
            if (who == null) return "§7Ordered in person";
            if (!open()) return "§cThe kiosk is closed";
            long p = price(kind);
            String why = charge(who, 0, "");
            if (why != null) return why;
            why = make(kind, nShots());
            if (why != null) { last = "CANNOT MAKE"; return why; }
            why = charge(who, p, ItemDrink.LABELS[kind]);
            if (why != null) { cups++; shots += nShots(); milk += MILK[kind]; dirty--; return why; }
            sold(p);
            brewT = 12 + (MILK[kind] > 0 ? 5 : 0); brewKind = kind; brewShots = nShots(); brewSize = SIZES[size()]; orderBy = who.getUniqueID();
            last = "ORDER " + ItemDrink.LABELS[kind].toUpperCase(Locale.ROOT);
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.6f, 1.0f);
            return "§a" + brewSize.toLowerCase(Locale.ROOT) + " " + ItemDrink.LABELS[kind] + " - " + Coins.fmt(p) + ", coming right up";
        }
        switch (id) {
            case "grind":
                if (!take(storage(0), BEAN, 1)) return "§cThe hopper is empty";
                shots += 4; grindSound();
                return "§7Ground - " + shots + " shots in the doser";
            case "steam":
                if (!ready()) return "§cNo steam pressure";
                world.playSound(null, pos, com.dogpound.railmap.ems.EmsSounds.COFFEE_STEAM, SoundCategory.BLOCKS, 0.6f, 1.0f);
                return "§7Pssshhh - steam wand purged";
            case "clean":
                if (brewT >= 0) return "§eFinish the drink first";
                cleanT = 10; last = "BACKFLUSH";
                return "§eBackflush cleaning cycle - 10 s";
            case "sugar":
                if (who == null) return null;
                if (!take(storage(2), SUGAR, 1)) return "§7No sugar left";
                give(who, new ItemStack(Items.SUGAR));
                return "§7A sugar for you";
            case "bell":
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.3f);
                return open() ? "§7Ding! - what can I get you?" : "§7Ding! - the kiosk is closed";
            default: return null;
        }
    }

    @Override
    protected boolean paxSale() {
        if (!ready() || dirty >= limit()) return false;
        int kind = world.rand.nextInt(ItemDrink.NAMES.length);
        if (make(kind, 1) != null) return false;
        sold(BASE[kind]);
        return true;
    }

    @Override
    protected void tick() {
        double target = boilerOn() ? 121 : 20, rate = (121 - 20) / (double) Math.max(5, ShopConfig.boilerWarmSeconds);
        if (temp < target) temp = Math.min(target, temp + rate); else temp = Math.max(target, temp - rate * 0.25);
        if (cleanT > 0 && --cleanT == 0) { dirty = 0; last = "CLEAN"; world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f); }
        if (brewT > 0) {
            if (brewT % 4 == 0) world.playSound(null, pos, (MILK[brewKind] > 0 && brewT < 8 ? com.dogpound.railmap.ems.EmsSounds.COFFEE_STEAM : com.dogpound.railmap.ems.EmsSounds.COFFEE_POUR), SoundCategory.BLOCKS, 0.5f, 1.0f);
            brewT--;
        } else if (brewT == 0) {
            brewT = -1;
            ItemStack d = ItemDrink.make(brewKind, brewShots, brewSize.charAt(0) + brewSize.substring(1).toLowerCase(Locale.ROOT));
            EntityPlayer p = orderBy == null ? null : world.getPlayerEntityByUUID(orderBy);
            if (p != null && p.getDistanceSq(pos) < 100) give(p, d);
            else {
                EnumFacing f = controlFacing();
                world.spawnEntity(new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.8, pos.getY() + 1.0, pos.getZ() + 0.5 + f.getZOffset() * 0.8, d));
            }
            last = "READY " + ItemDrink.LABELS[brewKind].toUpperCase(Locale.ROOT);
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.2f);
        }
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        int bns = beans(), mk = milk + 8 * milkBuckets(), cp = cups + 4 * count(storage(2), PAPER);
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "openl", open());
        ch |= StationControls.put(ctl, "heat", boilerOn() && temp < 118);
        ch |= StationControls.put(ctl, "ready", ready());
        ch |= StationControls.put(ctl, "brew", brewT >= 0);
        ch |= StationControls.put(ctl, "cleanl", dirty >= ShopConfig.drinksBetweenCleans || cleanT > 0);
        ch |= StationControls.put(ctl, "beanlow", bns * 4 + shots < 8);
        ch |= StationControls.put(ctl, "milklow", mk < 8);
        ch |= StationControls.put(ctl, "cuplow", cp < 4);
        ch |= StationControls.put(ctl, "gpress", (int) Math.round(bar() / 1.5 * 100));
        ch |= StationControls.put(ctl, "gtemp", (int) Math.round(temp / 140 * 100));
        for (int i = 0; i < ItemDrink.NAMES.length; i++) ch |= StationControls.text(ctl, "m" + (i + 1), String.format(Locale.ROOT, "%-11s %s", ItemDrink.LABELS[i].toUpperCase(Locale.ROOT), Coins.fmt(price(i))));
        ch |= StationControls.text(ctl, "status", cleanT > 0 ? "BACKFLUSH " + cleanT + "S" : brewT >= 0 ? "MAKING " + ItemDrink.LABELS[brewKind].toUpperCase(Locale.ROOT) + " " + brewT + "S" : !boilerOn() ? "BOILER OFF" : ready() ? "READY" : "HEATING");
        ch |= StationControls.text(ctl, "press", String.format(Locale.ROOT, "%.1f BAR %.0fC", bar(), temp));
        ch |= StationControls.text(ctl, "stock", "BEANS " + bns + " MILK " + mk + " CUPS " + cp);
        ch |= StationControls.text(ctl, "order", SIZES[size()] + (nShots() > 1 ? " DOUBLE" : " SINGLE"));
        ch |= StationControls.text(ctl, "sold", "SOLD " + soldToday + " " + Coins.fmt(takingsToday));
        ch |= StationControls.text(ctl, "dirty", "CLEAN IN " + Math.max(0, ShopConfig.drinksBetweenCleans - dirty));
        ch |= StationControls.text(ctl, "last", last);
        return ch;
    }

    @Override
    protected void saveShop(NBTTagCompound t) {
        t.setDouble("temp", temp); t.setInteger("shots", shots); t.setInteger("milk", milk); t.setInteger("cups", cups); t.setInteger("dirty", dirty);
        t.setInteger("brewT", brewT); t.setInteger("brewK", brewKind); t.setInteger("brewS", brewShots); t.setString("brewZ", brewSize); t.setInteger("cleanT", cleanT);
        if (orderBy != null) t.setUniqueId("by", orderBy);
    }

    @Override
    protected void loadShop(NBTTagCompound t) {
        temp = t.hasKey("temp") ? t.getDouble("temp") : 20; shots = t.getInteger("shots"); milk = t.getInteger("milk"); cups = t.getInteger("cups"); dirty = t.getInteger("dirty");
        brewT = t.hasKey("brewT") ? t.getInteger("brewT") : -1; brewKind = t.getInteger("brewK"); brewShots = t.getInteger("brewS"); brewSize = t.getString("brewZ"); cleanT = t.getInteger("cleanT");
        orderBy = t.hasUniqueId("by") ? t.getUniqueId("by") : null;
    }
}
