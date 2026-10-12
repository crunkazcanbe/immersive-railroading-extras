package com.dogpound.railmap.travel6;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.Map;

/**
 * Pride Card reader (batch 6b #6): a validator post for unbarriered stations and interchanges. Tap a Pride Card on the
 * yellow pad: green GO + the fare / balance on the screen, or red STOP + why. The MODE key (mechanical, staff) makes
 * it AUTO (in or out), ENTRY or EXIT only. Screen: station + zone, the last tap, taps today. TEST lights every lamp.
 * 12 V: dark (OUT OF ORDER) without it - and an unpowered reader can't charge anyone.
 */
public class TileCardReader extends SafetyTile {
    private int taps, refused;
    private long flashUntil, tapDay = -1;
    private boolean lastOk, lastOut;
    private String l1 = "", l2 = "";

    @Override public String wiredName() { return "Pride Card reader " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "NO 12 V - out of order" : taps + " taps today · " + refused + " refused"; }

    int mode() { return Math.floorMod(ctl.get("k_mode"), 3); }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return powered() ? lampTest() : "§cNo 12 V";
            case "k_mode": return null;                                    // the staff key turns (default)
            case "reader": case "pad": break;
            default: return "§7" + gearState();
        }
        if (who == null) return "§7Tap a card on the pad";
        if (!powered()) return "§cOUT OF ORDER - the reader is dark (no 12 V)";
        ItemStack held = Cards.isCard(who.getHeldItemMainhand()) ? who.getHeldItemMainhand() : who.getHeldItemOffhand();
        Map.Entry<Long, String> st = Cards.stationAt(world, pos);
        Cards.Result r;
        if (!Cards.isCard(held)) r = new Cards.Result(false, false, "Touch a Pride Card on the yellow pad", 0);
        else if (st == null) r = new Cards.Result(false, false, "This reader isn't at a station - name a station nearby", 0);
        else r = Cards.tap(world, held, st.getKey(), st.getValue(), mode(), "reader " + pos.getX() + "," + pos.getZ());
        long day = Cards.day(world);
        if (day != tapDay) { tapDay = day; taps = 0; refused = 0; }
        lastOk = r.ok; lastOut = r.out;
        if (r.ok) taps++; else refused++;
        flashUntil = world.getTotalWorldTime() + 50;
        world.playSound(null, pos, PackSounds.ev(r.ok ? "ui.beep_ok" : "ui.beep_error"), SoundCategory.BLOCKS, 0.7f, r.out ? 0.85f : 1.1f);
        l1 = r.ok ? (r.out ? "TOUCHED OUT" : "TOUCHED IN") : "SEEK ASSISTANCE";
        l2 = r.ok ? (r.charged > 0 ? Coins.fmt(r.charged) + " BAL " : "BAL ") + balOf(held) : cut(r.msg.toUpperCase(Locale.ROOT), 22);
        markDirty();
        return (r.ok ? "§a" : "§c") + r.msg;
    }

    private String balOf(ItemStack s) {
        TravelData.Card c = TravelData.get(world).cards.get(ItemTravel.cardId(s));
        return c == null ? "?" : Coins.fmt(c.bal);
    }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }

    @Override
    protected void serverTick() {
        if (age % 4 != 0) return;
        boolean f = world.getTotalWorldTime() < flashUntil;
        put("go", f && lastOk);
        put("stop", f && !lastOk);
        put("inl", f && lastOk && !lastOut);
        put("outl", f && lastOk && lastOut);
        put("pwr", powered());
        put("ntaps", taps);
        String[] m = {"AUTO", "ENTRY", "EXIT"};
        put("k_mode", mode());
        if (age % 20 == 0) {
            Map.Entry<Long, String> st = Cards.stationAt(world, pos);
            text("stn", st == null ? "NO STATION" : cut(st.getValue().toUpperCase(Locale.ROOT), 14) + " Z" + com.dogpound.railmap.economy.PassengerData.get(world).zoneOf(st.getKey()));
            text("modet", m[mode()]);
        }
        text("l1", f ? l1 : mode() == Cards.EXIT ? "TOUCH OUT" : mode() == Cards.ENTRY ? "TOUCH IN" : "TOUCH CARD");
        text("l2", f ? l2 : "PRIDE CARD");
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("taps", taps); t.setInteger("ref", refused); t.setLong("tday", tapDay); }
    @Override protected void load(NBTTagCompound t) { taps = t.getInteger("taps"); refused = t.getInteger("ref"); tapDay = t.hasKey("tday") ? t.getLong("tday") : -1; }
}
