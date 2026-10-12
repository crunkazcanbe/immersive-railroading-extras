package com.dogpound.railmap.travel6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.stn.StnFurniture;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Left-luggage lockers (batch 6b #8): a bank of twelve steel lockers - eight small (9 slots), four large (27) - with
 * the control unit in the middle: screen, 12-key PIN pad, HOURS selector, RENT, PAY, locker < > and a printer. Click a
 * free locker's door, pick the hours, RENT (paid from your bank), then key a 4-digit PIN and ENT: the door unlocks for
 * 30 s to load it, then locks. To collect: click the door (or select it), key the PIN, ENT - anything owed for an
 * overstay is charged first. A locker found empty after its door shuts is free again. Wrong PINs: after pinTries the
 * locker locks out and raises a tamper alarm; uncollected luggage a day past its time raises an alarm too. The
 * STAFF key (mechanical, works with no power) opens any door and CLEAR frees the selected locker. No power = every
 * locker stays locked (fail secure). 12 V control + mains (the electronic locks).
 */
public class TileLeftLuggage extends StnFurniture {
    static final int N = 12;
    static final String[] HOURS_POS = {"1H", "4H", "12H", "24H"};
    static final int[] HOURS = {1, 4, 12, 24};
    private final UUID[] by = new UUID[N];
    private final String[] byName = new String[N];
    private final int[] pin = new int[N], tries = new int[N];
    private final long[] until = new long[N], lockout = new long[N], openUntil = new long[N], paid = new long[N];
    private final UUID[] openFor = new UUID[N];
    private final boolean[] alarmed = new boolean[N];
    private int sel = -1;
    private String entry = "", lastMsg = "";
    private long testUntil;

    public TileLeftLuggage() {
        super(ids(), names(), sizes(), 1);
        Arrays.fill(pin, -1);
        Arrays.fill(byName, "");
    }

    static String[] ids() { String[] s = new String[N]; for (int i = 0; i < N; i++) s[i] = "l" + (i + 1); return s; }
    static String[] names() { String[] s = new String[N]; for (int i = 0; i < N; i++) s[i] = "Locker " + (i + 1); return s; }
    static int[] sizes() { int[] s = new int[N]; for (int i = 0; i < N; i++) s[i] = large(i) ? 27 : 9; return s; }
    /** the bottom row (lockers 9-12) is the large one */
    static boolean large(int k) { return k >= 8; }

    @Override public String wiredName() { return "Left luggage lockers"; }
    @Override public String gearState() { return !live() ? "NO SUPPLY - lockers locked" : rented() + "/" + N + " rented"; }
    @Override protected boolean mechanical(String id) { return id.startsWith("k_"); }
    boolean staff() { return ctl.get("k_staff") == 1; }

    int rented() { int n = 0; for (int i = 0; i < N; i++) if (by[i] != null) n++; return n; }
    int hours() { return HOURS[Math.floorMod(ctl.get("s_hours"), HOURS.length)]; }
    long rate(int k) { return TravelConfig.lockerPerHour * (large(k) ? 2L : 1L); }
    long owed(int k) {
        long now = world.getTotalWorldTime();
        if (by[k] == null || now <= until[k]) return 0;
        return ((now - until[k]) / 1000 + 1) * TravelConfig.lockerOverstayPerHour;
    }
    boolean empty(int k) { IInventory inv = storage(k); for (int i = 0; i < inv.getSizeInventory(); i++) if (!inv.getStackInSlot(i).isEmpty()) return false; return true; }
    String where() { Map.Entry<Long, String> st = Cards.stationAt(world, pos); return st == null ? "left luggage " + pos.getX() + "," + pos.getZ() : st.getValue() + " left luggage"; }

    /** a click on a locker door: opens it only for staff or in its open window; else it selects the locker */
    @Override
    protected String refuse(int k, EntityPlayer who) {
        if (staff()) { Recorder.event(world, "Left luggage locker " + (k + 1) + " opened with the STAFF key by " + who.getName() + " at " + where()); return null; }
        long now = world.getTotalWorldTime();
        if (openUntil[k] > now && who.getUniqueID().equals(openFor[k]) && live()) return null;
        sel = k; entry = "";
        controlsChanged();
        if (!live()) return "§cThe lockers have no power - they stay locked (staff key opens them)";
        if (by[k] == null) return "§7Locker " + (k + 1) + " is free (" + (large(k) ? "large" : "small") + ", " + Coins.fmt(rate(k)) + "/h) - pick HOURS and press RENT";
        if (lockout[k] > now) return "§cLocker " + (k + 1) + " is locked out - wait " + (lockout[k] - now) / 20 + " s";
        return "§7Locker " + (k + 1) + " is locked - key your PIN and press ENT";
    }

    @Override public int controlValue(String id) { return live() && testUntil > 0 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        if (!id.equals("k_staff")) return null;
        ctl.set(id, staff() ? 0 : 1);
        Recorder.event(world, "Left luggage STAFF key " + (staff() ? "ON" : "OFF") + " at " + where() + " (" + who(who) + ")");
        return staff() ? "§eSTAFF key ON - every locker door opens; CLEAR frees the selected locker" : "§eSTAFF key off";
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        String r = act0(id, who);
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    private String act0(String id, EntityPlayer who) {
        long now = world.getTotalWorldTime();
        if (id.length() == 2 && id.charAt(0) == 'n' && Character.isDigit(id.charAt(1))) {
            if (entry.length() < 4) entry += id.charAt(1);
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.25f, 1.6f);
            return "";
        }
        switch (id) {
            case "test": testUntil = now + 60; return "§7Lamp test";
            case "prev": sel = sel < 0 ? N - 1 : Math.floorMod(sel - 1, N); entry = ""; return "";
            case "next": sel = Math.floorMod(sel + 1, N); entry = ""; return "";
            case "clr": entry = ""; return "§7PIN cleared";
            case "rent": return rent(who, now);
            case "ent": return enter(who, now);
            case "sclr": {
                if (!staff()) return "§7CLEAR needs the STAFF key";
                if (sel < 0) return "§7Select a locker";
                int k = sel;
                Recorder.event(world, "Left luggage locker " + (k + 1) + " cleared by staff at " + where() + (by[k] == null ? "" : " (was " + byName[k] + ")"));
                free(k);
                return "§eLocker " + (k + 1) + " cleared - empty it by hand";
            }
            case "print": {
                List<String> out = new ArrayList<>();
                out.add("LEFT LUGGAGE - " + where());
                for (int k = 0; k < N; k++)
                    out.add("Locker " + (k + 1) + (large(k) ? " L " : " S ") + (by[k] == null ? "free" : byName[k] + " until " + Recorder.clock(world, until[k]) + (owed(k) > 0 ? " OVERSTAY " + Coins.fmt(owed(k)) : "")));
                StnUtil.print(world, pos, controlFacing(), "Left luggage", where(), out);
                return "§aLocker register printed";
            }
            default: return null;
        }
    }

    private String rent(EntityPlayer who, long now) {
        if (!(who instanceof EntityPlayerMP p)) return "§7Rent at the lockers";
        if (sel < 0) return "§7Click a free locker door first";
        int k = sel;
        if (by[k] != null) return "§cLocker " + (k + 1) + " is taken";
        long cost = rate(k) * hours();
        if (!Coins.charge(p, cost, "Left luggage locker " + (k + 1))) return "§c" + hours() + " h costs " + Coins.fmt(cost) + " - your bank can't cover it";
        by[k] = who.getUniqueID(); byName[k] = who.getName(); pin[k] = -1; tries[k] = 0; paid[k] = cost;
        until[k] = now + hours() * 1000L;
        entry = "";
        TravelData d = TravelData.get(world);
        d.rent += cost;
        d.note(Recorder.clock(world, now) + " locker " + (k + 1) + " rented by " + who.getName() + " at " + where() + " for " + hours() + " h");
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.6f, 1f);
        return "§aLocker " + (k + 1) + " rented for " + hours() + " h (" + Coins.fmt(cost) + ") - now key a 4-digit PIN and press ENT";
    }

    private String enter(EntityPlayer who, long now) {
        if (sel < 0) return "§7Select a locker first";
        int k = sel;
        if (by[k] == null) return "§7Locker " + (k + 1) + " is free - RENT it first";
        if (entry.length() != 4) return "§7A PIN is 4 digits";
        int e = Integer.parseInt(entry);
        entry = "";
        if (pin[k] < 0) {
            if (who == null || !who.getUniqueID().equals(by[k])) return "§cOnly whoever rented locker " + (k + 1) + " sets its PIN";
            pin[k] = hash(k, e);
            open(k, who, now);
            Recorder.event(world, "Left luggage locker " + (k + 1) + " PIN set by " + who.getName() + " at " + where());
            return "§aPIN set - locker " + (k + 1) + " is open: put your luggage in, it locks in 30 s";
        }
        if (lockout[k] > now) return "§cLocker " + (k + 1) + " is locked out for " + (lockout[k] - now) / 20 + " s";
        if (hash(k, e) != pin[k]) {
            tries[k]++;
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_error"), SoundCategory.BLOCKS, 0.7f, 1f);
            if (tries[k] >= TravelConfig.pinTries) {
                tries[k] = 0;
                lockout[k] = now + TravelConfig.lockoutSeconds * 20L;
                AlarmManager.get(world).raise(world, "travel6.lock.tamper." + pos.toLong() + "." + k, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR,
                        "Left luggage locker " + (k + 1) + " at " + where() + ": " + TravelConfig.pinTries + " wrong PINs" + (who == null ? "" : " (" + who.getName() + ")"), pos, false);
                return "§cWrong PIN - locker " + (k + 1) + " LOCKED OUT, staff alerted";
            }
            return "§cWrong PIN (" + (TravelConfig.pinTries - tries[k]) + " tries left)";
        }
        long o = owed(k);
        if (o > 0) {
            if (!(who instanceof EntityPlayerMP p) || !Coins.charge(p, o, "Left luggage overstay, locker " + (k + 1))) return "§cOverstay " + Coins.fmt(o) + " to pay - your bank can't cover it";
            TravelData.get(world).rent += o;
            TravelData.get(world).markDirty();
            paid[k] += o;
        }
        tries[k] = 0;
        open(k, who, now);
        return "§aLocker " + (k + 1) + " open" + (o > 0 ? " (overstay " + Coins.fmt(o) + " paid)" : "") + " - take your luggage; an empty locker is freed when it shuts";
    }

    /** the PIN is kept scrambled (the tile's NBT also goes to clients). ponytail: 10^4 PINs, a scramble not a secret */
    private int hash(int k, int p) { return Math.floorMod((p + 7919 * (k + 1)) * 40503 ^ (int) pos.toLong(), 1 << 30); }

    private void open(int k, EntityPlayer who, long now) {
        openUntil[k] = now + 600;
        openFor[k] = who == null ? by[k] : who.getUniqueID();
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.6f, 1.4f);
    }

    private void free(int k) {
        by[k] = null; byName[k] = ""; pin[k] = -1; tries[k] = 0; until[k] = 0; openUntil[k] = 0; openFor[k] = null; paid[k] = 0; lockout[k] = 0;
        if (alarmed[k]) { AlarmManager.get(world).clear("travel6.lock.over." + pos.toLong() + "." + k); alarmed[k] = false; }
        markDirty();
    }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (testUntil > 0 && now > testUntil) { testUntil = 0; controlsChanged(); }
        if (age % 20 != 7) return;
        for (int k = 0; k < N; k++) {
            if (openUntil[k] > 0 && now > openUntil[k]) {
                openUntil[k] = 0; openFor[k] = null;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.4f, 1.0f);
                if (by[k] != null && pin[k] >= 0 && empty(k)) {
                    TravelData.get(world).note(Recorder.clock(world, now) + " locker " + (k + 1) + " at " + where() + " collected by " + byName[k]);
                    free(k);
                }
                controlsChanged();
            }
            if (by[k] != null && pin[k] < 0 && now > until[k]) free(k);   // never got a PIN: the rental lapses
            boolean over = by[k] != null && now > until[k] + 24000;
            if (over && !alarmed[k]) {
                alarmed[k] = true;
                AlarmManager.get(world).raise(world, "travel6.lock.over." + pos.toLong() + "." + k, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR,
                        "Left luggage locker " + (k + 1) + " at " + where() + " uncollected a day past its time (" + byName[k] + ")", pos, true);
            }
        }
    }

    @Override
    protected boolean refresh() {
        long now = world.getTotalWorldTime();
        String[] l = new String[6];
        Arrays.fill(l, "");
        if (sel < 0) {
            l[0] = "LEFT LUGGAGE";
            l[1] = (N - rented()) + " OF " + N + " LOCKERS FREE";
            l[2] = "SMALL " + Coins.fmt(TravelConfig.lockerPerHour) + "/H LARGE " + Coins.fmt(TravelConfig.lockerPerHour * 2L) + "/H";
            l[3] = "CLICK A LOCKER DOOR";
        } else {
            int k = sel;
            l[0] = "LOCKER " + (k + 1) + (large(k) ? " LARGE" : " SMALL");
            if (by[k] == null) { l[1] = "FREE - " + Coins.fmt(rate(k)) + " PER HOUR"; l[2] = "HOURS " + hours() + " = " + Coins.fmt(rate(k) * hours()); l[3] = "PRESS RENT"; }
            else {
                l[1] = StnUtil.up(StnUtil.cut(byName[k], 20));
                l[2] = pin[k] < 0 ? "KEY A NEW PIN + ENT" : now > until[k] ? "OVERSTAY " + Coins.fmt(owed(k)) : "PAID UNTIL " + Recorder.clock(world, until[k]) + " (" + (until[k] - now) / 1000 + "H)";
                l[3] = lockout[k] > now ? "LOCKED OUT " + (lockout[k] - now) / 20 + "S" : openUntil[k] > now ? "DOOR OPEN " + (openUntil[k] - now) / 20 + "S" : "LOCKED - KEY PIN + ENT";
            }
            l[4] = "PAID " + Coins.fmt(paid[k]);
        }
        l[5] = staff() ? "STAFF KEY ON" : "";
        boolean ch = false;
        for (int i = 0; i < 6; i++) ch |= StationControls.text(ctl, "s" + (i + 1), l[i]);
        ch |= StationControls.text(ctl, "pinv", "****____".substring(4 - entry.length(), 8 - entry.length()));
        ch |= StationControls.text(ctl, "selv", sel < 0 ? "--" : String.format(Locale.ROOT, "%02d", sel + 1));
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 34));
        ch |= StationControls.put(ctl, "nfree", N - rented()) | StationControls.put(ctl, "pwr", term.powered()) | StationControls.put(ctl, "stfl", staff());
        boolean anyAlarm = false;
        for (int k = 0; k < N; k++) {
            boolean open = staff() || openUntil[k] > now;
            ch |= StationControls.put(ctl, "oc" + (k + 1), by[k] != null && !open);
            ch |= StationControls.put(ctl, "fr" + (k + 1), by[k] == null);
            ch |= StationControls.put(ctl, "op" + (k + 1), open);
            anyAlarm |= alarmed[k] || lockout[k] > now;
        }
        ch |= StationControls.put(ctl, "alml", anyAlarm);
        return ch;
    }

    @Override
    protected void save(NBTTagCompound t) {
        NBTTagCompound l = new NBTTagCompound();
        for (int k = 0; k < N; k++) {
            if (by[k] == null) continue;
            NBTTagCompound x = new NBTTagCompound();
            x.setUniqueId("by", by[k]); x.setString("n", byName[k]); x.setInteger("pin", pin[k]); x.setInteger("tr", tries[k]);
            x.setLong("u", until[k]); x.setLong("lo", lockout[k]); x.setLong("p", paid[k]); x.setBoolean("al", alarmed[k]);
            l.setTag(String.valueOf(k), x);
        }
        t.setTag("lockers", l);
        t.setInteger("sel", sel);
    }

    @Override
    protected void load(NBTTagCompound t) {
        NBTTagCompound l = t.getCompoundTag("lockers");
        for (int k = 0; k < N; k++) {
            by[k] = null; byName[k] = ""; pin[k] = -1;
            if (!l.hasKey(String.valueOf(k))) continue;
            NBTTagCompound x = l.getCompoundTag(String.valueOf(k));
            by[k] = x.getUniqueId("by"); byName[k] = x.getString("n"); pin[k] = x.getInteger("pin"); tries[k] = x.getInteger("tr");
            until[k] = x.getLong("u"); lockout[k] = x.getLong("lo"); paid[k] = x.getLong("p"); alarmed[k] = x.getBoolean("al");
        }
        sel = t.hasKey("sel") ? t.getInteger("sel") : -1;
    }

    /** selftest hooks */
    int pinOf(int k) { return pin[k]; }
    boolean rentedTo(int k, UUID u) { return u.equals(by[k]); }
}
