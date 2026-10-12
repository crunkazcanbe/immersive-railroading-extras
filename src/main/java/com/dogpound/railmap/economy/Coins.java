package com.dogpound.railmap.economy;

import com.dogpound.railmap.RailMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Pride coins (Pride Realms' bank), by reflection so IR Extras runs without it. Money is in CENTS everywhere.
 * pay = new money into the player's bank (like any reward); charge = taken out of the player's own account (burned).
 * Without Pride Realms nothing is paid, and only creative players can buy (free).
 */
public final class Coins {
    private Coins() {}

    private static boolean tried;
    private static Method reward, balance, ledgerGet, burn;

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> api = Class.forName("com.dogpound.realmcoin.api.PrideApi");
            reward = api.getMethod("reward", EntityPlayerMP.class, long.class, String.class);
            balance = api.getMethod("balance", EntityPlayerMP.class);
            Class<?> ledger = Class.forName("com.dogpound.realmcoin.bank.Ledger");
            ledgerGet = ledger.getMethod("get", World.class);
            burn = ledger.getMethod("burn", String.class, long.class, String.class, String.class);
        } catch (Throwable t) {
            RailMap.LOG.info("[IR Extras] Pride Realms not installed - the railway economy can't move Pride coins");
        }
    }

    public static boolean available() { init(); return reward != null; }

    /** the player's bank balance in cents, or -1 without Pride Realms */
    public static long balance(EntityPlayerMP p) {
        init();
        if (balance == null) return -1;
        try { return (long) balance.invoke(null, p); } catch (Throwable t) { return -1; }
    }

    /** pay the player (into their bank) */
    public static boolean pay(EntityPlayerMP p, long cents, String why) {
        init();
        if (cents <= 0) return true;
        if (reward == null) return false;
        try { reward.invoke(null, p, cents, why); return true; }
        catch (Throwable t) { RailMap.LOG.warn("[IR Extras] payment failed: {}", t.toString()); return false; }
    }

    /** take money from the player's own account; false = can't afford it (or no bank). Creative players ride free. */
    public static boolean charge(EntityPlayerMP p, long cents, String why) {
        if (cents <= 0 || p.capabilities.isCreativeMode) return true;
        init();
        if (burn == null || balance(p) < cents) return false;
        try {
            World w0 = p.getServer() == null ? p.world : p.getServer().getWorld(0);
            Object ledger = ledgerGet.invoke(null, w0);
            Object r = burn.invoke(ledger, "p:" + p.getUniqueID(), cents, "railway", why);
            return "OK".equals(String.valueOf(r));
        } catch (Throwable t) {
            RailMap.LOG.warn("[IR Extras] charge failed: {}", t.toString());
            return false;
        }
    }

    public static String fmt(long cents) {
        return (cents < 0 ? "-$" : "$") + String.format(Locale.ROOT, "%,d.%02d", Math.abs(cents) / 100, Math.abs(cents) % 100);
    }
}
