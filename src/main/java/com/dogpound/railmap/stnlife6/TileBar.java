package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Station pub bar (batch 6c #14): a three-block bar counter with three working taps, the keg cellar, glass shelf and
 * till (real storage) and a back-bar shelf that shows what stands on it. Pull a tap: a pint is poured into a clean
 * glass (a glass bottle from the glass shelf), the price taken (Pride coins) and the keg gauge drops. ALE is a hand
 * pump (beer engine - works with no power); LAGER and CIDER are gas + python-cooled: they need 12 V and mains. CHANGE
 * KEGS taps a new keg on every empty line from the cellar (ale 6 wheat, lager 5 wheat + 1 sugar, cider 8 apples).
 * The menu screen pages the kitchen's dishes (MENU < >) and ORDER sends the selected one to the nearest pub kitchen
 * (32 blocks) and takes the money. BAR key opens it. 12 V + mains.
 */
public class TileBar extends StnFurniture {
    static final int[] FOOD = {3, 9, 5, 10, 4, 8, 6, 7};
    static final int[] PRICE = {1250, 400, 1050, 1800, 1100, 1500, 950, 600};
    static final String[] TAP = {"ALE", "LAGER", "CIDER"};
    private final int[] keg = new int[3];
    private int sel, pints, meals;
    private long takings;
    private String msg = "";

    public TileBar() { super(new String[]{"cellar", "glasses", "till"}, new String[]{"Keg cellar", "Glass shelf", "Till"}, new int[]{27, 9, 9}, 6); }

    static double[][] SPOTS = {{-11.883, 7.0, 13.4}, {-10.25, 7.0, 13.4}, {-8.617, 7.0, 13.4}, {-6.983, 7.0, 13.4}, {-5.35, 7.0, 13.4}, {-3.717, 7.0, 13.4}};   // tools/gen_bb_batch6c.py
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override public String wiredName() { return "Pub bar"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY (ale hand pump still pulls)" : "kegs " + keg[0] + "/" + keg[1] + "/" + keg[2] + " · " + pints + " pints, " + meals + " meals · " + Coins.fmt(takings); }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_") || id.startsWith("tap"); }

    TileKitchen kitchen() {
        TileKitchen best = null;
        double bd = Double.MAX_VALUE;
        for (TileKitchen k : StnUtil.near(world, TileKitchen.class, pos, 32)) { double d = k.getPos().distanceSq(pos); if (d < bd) { bd = d; best = k; } }
        return best;
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (!id.startsWith("tap")) return null;
        int t = id.charAt(3) - '1';
        if (t < 0 || t > 2) return null;
        if (ctl.get("k_open") != 1) return "§cThe bar is closed";
        if (t > 0 && !live()) return "§c" + TAP[t] + " is a gas + cooler line - no power, no pour (the ale hand pump still works)";
        if (keg[t] <= 0) return "§c" + TAP[t] + " keg is empty - CHANGE KEGS";
        if (who == null) return "§7Pull it in person";
        if (!take(storage(1), Items.GLASS_BOTTLE, 1, true)) return "§cNo clean glasses on the glass shelf (glass bottles)";
        if (who instanceof EntityPlayerMP mp && !Coins.charge(mp, LifeConfig.pintPrice, "Pint of " + TAP[t])) { insert(storage(1), new ItemStack(Items.GLASS_BOTTLE)); return "§cThat's " + Coins.fmt(LifeConfig.pintPrice) + " - you can't pay"; }
        keg[t]--; pints++; takings += LifeConfig.pintPrice;
        ItemStack p = ItemPubFood.make(t);
        if (!who.inventory.addItemStackToInventory(p)) who.dropItem(p, false);
        ctl.set(id, 1 - ctl.get(id));                                       // the handle swings
        msg = "PINT OF " + TAP[t];
        if (keg[t] == 0) Recorder.event(world, "Pub bar: " + TAP[t] + " keg ran dry");
        refresh();
        return "§aPint of " + TAP[t] + " - " + Coins.fmt(LifeConfig.pintPrice) + " (" + keg[t] + " left in the keg)";
    }

    static boolean take(IInventory inv, Item item, int n, boolean really) {
        int have = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i).getItem() == item) have += inv.getStackInSlot(i).getCount();
        if (have < n) return false;
        if (!really) return true;
        for (int i = 0; i < inv.getSizeInventory() && n > 0; i++) {
            if (inv.getStackInSlot(i).getItem() != item) continue;
            int k = Math.min(n, inv.getStackInSlot(i).getCount());
            inv.decrStackSize(i, k);
            n -= k;
        }
        return true;
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "mprev", "mnext" -> { sel = Math.floorMod(sel + (id.equals("mnext") ? 1 : -1), FOOD.length); return "§e" + ItemPubFood.LABELS[FOOD[sel]] + " " + Coins.fmt(PRICE[sel]); }
            case "order" -> {
                if (ctl.get("k_open") != 1) return "§cThe bar is closed";
                if (who == null) return "§7Orders are taken in person";
                TileKitchen k = kitchen();
                if (k == null) return "§cNo kitchen within 32 blocks";
                if (!k.live()) return "§cThe kitchen is closed (no power)";
                int d = FOOD[sel];
                if (who instanceof EntityPlayerMP mp && !Coins.charge(mp, PRICE[sel], ItemPubFood.LABELS[d])) return "§cThat's " + Coins.fmt(PRICE[sel]) + " - you can't pay";
                if (!k.order(d, who.getName())) return "§cThe kitchen can't take another order right now";
                meals++; takings += PRICE[sel];
                msg = "ORDER " + ItemPubFood.LABELS[d].toUpperCase(Locale.ROOT);
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.7f, 1.0f);
                return "§a" + ItemPubFood.LABELS[d] + " ordered - it will be on the kitchen pass";
            }
            case "kegs" -> {
                int n = 0;
                Item[][] need = {{Items.WHEAT}, {Items.WHEAT, Items.SUGAR}, {Items.APPLE}};
                int[][] cnt = {{6}, {5, 1}, {8}};
                for (int t = 0; t < 3; t++) {
                    if (keg[t] > 0) continue;
                    boolean ok = true;
                    for (int i = 0; i < need[t].length; i++) ok &= take(storage(0), need[t][i], cnt[t][i], false);
                    if (!ok) continue;
                    for (int i = 0; i < need[t].length; i++) take(storage(0), need[t][i], cnt[t][i], true);
                    keg[t] = LifeConfig.kegPints; n++;
                }
                return n == 0 ? "§7No empty line with a keg's worth in the cellar (ale 6 wheat, lager 5 wheat + 1 sugar, cider 8 apples)" : "§a" + n + " keg(s) changed";
            }
            default -> { return null; }
        }
    }

    @Override protected void work() {}

    @Override
    protected boolean refresh() {
        boolean ch = false;
        TileKitchen k = world == null ? null : kitchen();
        for (int t = 0; t < 3; t++) {
            ch |= StationControls.put(ctl, "g" + (t + 1), keg[t] * 100 / Math.max(1, LifeConfig.kegPints));
            ch |= StationControls.put(ctl, "l_empty" + (t + 1), keg[t] == 0);
        }
        for (int i = 0; i < 4; i++) {
            int m = Math.floorMod(sel - 1 + i, FOOD.length);
            ch |= StationControls.text(ctl, "m" + (i + 1), (m == sel ? ">" : " ") + ItemPubFood.LABELS[FOOD[m]].toUpperCase(Locale.ROOT) + " " + Coins.fmt(PRICE[m]));
        }
        ch |= StationControls.text(ctl, "sign", "THE STATION ARMS");
        ch |= StationControls.text(ctl, "msg", msg.isEmpty() ? "PINT " + Coins.fmt(LifeConfig.pintPrice) : msg);
        ch |= StationControls.text(ctl, "take", pints + " PINTS " + meals + " MEALS " + Coins.fmt(takings));
        ch |= StationControls.put(ctl, "l_kitchen", k != null && k.live());
        ch |= StationControls.put(ctl, "l_ready", k != null && k.controls().get("l_ready") == 1);
        ch |= StationControls.put(ctl, "l_cool", live());
        ch |= StationControls.put(ctl, "l_open", ctl.get("k_open") == 1);
        ch |= StationControls.put(ctl, "pwr", term.powered());
        return ch;
    }

    @Override protected void save(NBTTagCompound t) { t.setIntArray("keg", keg); t.setInteger("sel", sel); t.setInteger("pints", pints); t.setInteger("meals", meals); t.setLong("take", takings); t.setString("msg", msg); }
    @Override protected void load(NBTTagCompound t) { int[] a = t.getIntArray("keg"); for (int i = 0; i < 3; i++) keg[i] = i < a.length ? a[i] : 0; sel = t.getInteger("sel"); pints = t.getInteger("pints"); meals = t.getInteger("meals"); takings = t.getLong("take"); msg = t.getString("msg"); }
}
