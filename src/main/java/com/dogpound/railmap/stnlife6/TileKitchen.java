package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.stn.StnFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Pub kitchen (batch 6c #14): a three-block cook line - deep FRYER, char GRILL, OVEN and a four-ring HOB - under an
 * extraction hood, with the pantry and fridge (real storage) and the PASS (a heated shelf that shows the plates waiting
 * on it). Each station's selector picks its dish and START cooks it: the ingredients come out of the pantry / fridge,
 * the station heats (its lamp, a mains load of 30 FE/s on top of the controls), the timer counts down and the dish goes
 * on the pass with a ding. Orders from the bar's menu queue on the ticket rail; with the CHEF key on, the kitchen cooks
 * them by itself as stations come free and ingredients allow, else press START yourself. Frying or grilling without
 * the HOOD fan on fills the kitchen with smoke: a KITCHEN SMOKE fire alarm after 30 s. 12 V + mains (dead: nothing heats).
 */
public class TileKitchen extends StnFurniture {
    static final String[] ST = {"fry", "grill", "oven", "hob"};
    /** per station: the dishes its selector offers (ItemPubFood meta) */
    static final int[][] MENU = {{3, 9}, {5, 10}, {4, 8}, {6, 7}};
    static final Map<Integer, Item[]> NEED = new HashMap<>();
    static final Map<Integer, Integer> SECS = new HashMap<>();
    static {
        need(3, 20, Items.FISH, Items.POTATO);
        need(9, 12, Items.POTATO, Items.POTATO);
        need(5, 15, Items.BEEF, Items.BREAD);
        need(10, 18, Items.BEEF);
        need(4, 30, Items.BEEF, Items.WHEAT, Items.POTATO);
        need(8, 40, Items.CHICKEN, Items.POTATO, Items.CARROT);
        need(6, 20, Items.PORKCHOP, Items.EGG, Items.BREAD);
        need(7, 15, Items.CARROT, Items.POTATO, Items.BOWL);
    }
    static void need(int dish, int secs, Item... in) { NEED.put(dish, in); SECS.put(dish, secs); }

    private final int[] dish = {-1, -1, -1, -1}, left = new int[4];
    private final String[] forWho = {"", "", "", ""};
    private final List<String> orders = new ArrayList<>();     // "dish|customer"
    private int smoke, cooked, noIng;
    private boolean smokeAlarm;

    public TileKitchen() {
        super(new String[]{"pantry", "fridge"}, new String[]{"Pantry", "Fridge"}, new int[]{27, 18}, 6);
    }

    static double[][] SPOTS = {{18.65, 11.4, 13.4}, {20.55, 11.4, 13.4}, {22.45, 11.4, 13.4}, {24.35, 11.4, 13.4}, {26.25, 11.4, 13.4}, {28.15, 11.4, 13.4}};   // the pass, tools/gen_bb_batch6c.py
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override public String wiredName() { return "Pub kitchen"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : active() + " stations cooking · " + orders.size() + " orders · " + cooked + " plates today" + (smokeAlarm ? " · SMOKE" : ""); }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_") || id.startsWith("s_"); }

    int active() { int n = 0; for (int d : dish) if (d >= 0) n++; return n; }
    static int station(int d) { for (int s = 0; s < 4; s++) for (int x : MENU[s]) if (x == d) return s; return -1; }

    /** the bar takes an order: false if the kitchen can't take it */
    boolean order(int d, String who) {
        if (!live() || orders.size() >= 12 || station(d) < 0) return false;
        orders.add(d + "|" + who);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.paper"), SoundCategory.BLOCKS, 0.6f, 1.0f);
        markDirty();
        return true;
    }

    private boolean take(int d, boolean really) {
        List<IInventory> inv = Arrays.asList(storage(0), storage(1));
        Map<Item, Integer> want = new HashMap<>();
        for (Item i : NEED.get(d)) want.merge(i, 1, Integer::sum);
        for (Map.Entry<Item, Integer> e : want.entrySet()) {
            int have = 0;
            for (IInventory v : inv) for (int i = 0; i < v.getSizeInventory(); i++) if (v.getStackInSlot(i).getItem() == e.getKey()) have += v.getStackInSlot(i).getCount();
            if (have < e.getValue()) return false;
        }
        if (!really) return true;
        for (Map.Entry<Item, Integer> e : want.entrySet()) {
            int n = e.getValue();
            for (IInventory v : inv) for (int i = 0; i < v.getSizeInventory() && n > 0; i++) {
                ItemStack s = v.getStackInSlot(i);
                if (s.getItem() != e.getKey()) continue;
                int k = Math.min(n, s.getCount());
                v.decrStackSize(i, k);
                n -= k;
            }
        }
        return true;
    }

    private String start(int s, int d, String who) {
        if (dish[s] >= 0) return "§7The " + ST[s] + " is busy";
        if (!take(d, true)) { noIng = 60; return "§cNot enough in the pantry / fridge for " + ItemPubFood.LABELS[d] + " (" + needs(d) + ")"; }
        dish[s] = d; left[s] = SECS.get(d) * 20; forWho[s] = who;
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.push"), SoundCategory.BLOCKS, 0.6f, 0.8f);
        return "§a" + ItemPubFood.LABELS[d] + " on the " + ST[s] + " - " + SECS.get(d) + " s";
    }

    static String needs(int d) {
        StringBuilder b = new StringBuilder();
        for (Item i : NEED.get(d)) b.append(b.length() == 0 ? "" : ", ").append(new ItemStack(i).getDisplayName());
        return b.toString();
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        for (int s = 0; s < 4; s++) if (id.equals("go_" + ST[s])) {
            int d = MENU[s][Math.floorMod(ctl.get("s_" + ST[s]), MENU[s].length)];
            String cust = "";
            for (Iterator<String> it = orders.iterator(); it.hasNext(); ) {             // cooking a queued dish fills its order
                String[] o = it.next().split("\\|", 2);
                if (Integer.parseInt(o[0]) == d) { cust = o.length > 1 ? o[1] : ""; if (take(d, false)) it.remove(); break; }
            }
            return start(s, d, cust);
        }
        if (id.equals("bump")) { if (orders.isEmpty()) return "§7No tickets"; orders.remove(0); return "§eTop ticket bumped"; }
        if (id.equals("bell")) { world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.8f, 1.8f); return "§7Service! (pass bell)"; }
        return null;
    }

    @Override
    protected void serverTick() {
        if (mains.tick(world, pos, term, wiredName(), term.powered() ? 4 + 30L * active() : 0)) controlsChanged();
        work();
    }

    @Override
    protected void work() {
        if (age % 20 != 5) return;
        if (noIng > 0) noIng -= 20;
        boolean on = live();
        boolean frying = false;
        for (int s = 0; s < 4; s++) {
            if (dish[s] < 0 || !on) continue;
            if (s < 2) frying = true;
            left[s] -= 20;
            if (left[s] > 0) continue;
            ItemStack plate = ItemPubFood.make(dish[s]);
            if (!forWho[s].isEmpty()) plate.setStackDisplayName("§f" + ItemPubFood.LABELS[dish[s]] + " for " + forWho[s]);
            ItemStack rest = insert(shelf, plate);
            if (!rest.isEmpty()) { left[s] = 20; continue; }                      // pass full: keep it warm on the station
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.CASH, SoundCategory.BLOCKS, 0.8f, 1.8f);
            if (!forWho[s].isEmpty()) {
                EntityPlayer p = world.getPlayerEntityByName(forWho[s]);
                if (p != null && p.getDistanceSq(pos) < 48 * 48) p.sendMessage(new net.minecraft.util.text.TextComponentString("§6Kitchen: §fyour " + ItemPubFood.LABELS[dish[s]] + " is ready on the pass"));
            }
            cooked++;
            dish[s] = -1; forWho[s] = "";
            controlsChanged();
        }
        // the chef cooks the ticket rail
        if (on && ctl.get("k_chef") == 1 && !orders.isEmpty()) {
            for (Iterator<String> it = orders.iterator(); it.hasNext(); ) {
                String[] o = it.next().split("\\|", 2);
                int d = Integer.parseInt(o[0]), s = station(d);
                if (s < 0 || dish[s] >= 0 || !take(d, false)) continue;
                it.remove();
                start(s, d, o.length > 1 ? o[1] : "");
            }
        }
        // smoke: frying / grilling with the extraction off
        boolean hood = ctl.get("k_hood") == 1 && on;
        smoke = frying && !hood ? smoke + 1 : Math.max(0, smoke - 3);
        String ak = "kitchensmoke:" + pos.toLong();
        if (smoke >= 30 && !smokeAlarm) { smokeAlarm = true; AlarmManager.get(world).raise(world, ak, AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR, "KITCHEN SMOKE at the pub kitchen " + pos.getX() + "," + pos.getZ() + " - extraction hood off", pos, true); Recorder.event(world, "Pub kitchen: smoke - cooking with the extraction off"); }
        else if (smoke == 0 && smokeAlarm) { smokeAlarm = false; AlarmManager.get(world).clear(ak); }
        if (on && world.rand.nextInt(4) == 0 && frying) ((net.minecraft.world.WorldServer) world).spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, hood ? 1 : 6, 0.4, 0.2, 0.3, 0.01);
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        for (int s = 0; s < 4; s++) {
            int sel = MENU[s][Math.floorMod(ctl.get("s_" + ST[s]), MENU[s].length)];
            ch |= StationControls.put(ctl, "l_" + ST[s], dish[s] >= 0 && live());
            ch |= StationControls.text(ctl, "t_" + ST[s], dish[s] >= 0 ? up(ItemPubFood.LABELS[dish[s]]) + " " + Math.max(0, left[s] / 20) + "S" : "IDLE " + up(ItemPubFood.LABELS[sel]));
        }
        for (int i = 0; i < 4; i++) {
            String t = "";
            if (i < orders.size()) { String[] o = orders.get(i).split("\\|", 2); t = (i + 1) + " " + up(ItemPubFood.LABELS[Integer.parseInt(o[0])]) + (o.length > 1 && !o[1].isEmpty() ? " " + up(o[1]) : ""); }
            ch |= StationControls.text(ctl, "o" + (i + 1), t);
        }
        int plates = 0;
        for (int i = 0; i < shelf.getSizeInventory(); i++) if (!shelf.getStackInSlot(i).isEmpty()) plates++;
        ch |= StationControls.text(ctl, "sign", "KITCHEN " + orders.size() + " TICKETS");
        ch |= StationControls.put(ctl, "l_ready", plates > 0);
        ch |= StationControls.put(ctl, "l_noing", noIng > 0);
        ch |= StationControls.put(ctl, "l_smoke", smokeAlarm);
        ch |= StationControls.put(ctl, "l_hood", ctl.get("k_hood") == 1 && live());
        ch |= StationControls.put(ctl, "pwr", term.powered());
        ch |= StationControls.put(ctl, "g_load", active() * 25);
        return ch;
    }

    static String up(String s) { return s.toUpperCase(Locale.ROOT); }

    @Override
    protected void save(NBTTagCompound t) {
        t.setIntArray("dish", dish); t.setIntArray("left", left); t.setString("for", String.join("\n", forWho));
        t.setString("orders", String.join("\n", orders)); t.setInteger("cooked", cooked); t.setInteger("smoke", smoke); t.setBoolean("sa", smokeAlarm);
    }

    @Override
    protected void load(NBTTagCompound t) {
        int[] a = t.getIntArray("dish"), b = t.getIntArray("left");
        for (int i = 0; i < 4; i++) { dish[i] = i < a.length ? a[i] : -1; left[i] = i < b.length ? b[i] : 0; }
        String[] f = t.getString("for").split("\n", -1);
        for (int i = 0; i < 4; i++) forWho[i] = i < f.length ? f[i] : "";
        orders.clear();
        if (!t.getString("orders").isEmpty()) orders.addAll(Arrays.asList(t.getString("orders").split("\n")));
        cooked = t.getInteger("cooked"); smoke = t.getInteger("smoke"); smokeAlarm = t.getBoolean("sa");
    }

    /** selftest: every menu dish has a recipe on its station */
    static List<String> selfTest() {
        List<String> f = new ArrayList<>();
        for (int s = 0; s < 4; s++) for (int d : MENU[s]) { if (!NEED.containsKey(d)) f.add("no recipe for " + ItemPubFood.NAMES[d]); if (station(d) != s) f.add(ItemPubFood.NAMES[d] + " on the wrong station"); }
        for (int d = 3; d < ItemPubFood.NAMES.length; d++) if (station(d) < 0) f.add(ItemPubFood.NAMES[d] + " can't be cooked anywhere");
        return f;
    }
}
