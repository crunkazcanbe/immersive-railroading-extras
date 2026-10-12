package com.dogpound.railmap.freight6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

/**
 * Grain Silo bin (batch 6 #26): a corrugated steel bin on a hopper bottom. Holds one kind of grain (F6Config.siloItems,
 * 1 item = 50 kg) with its average moisture and the temperature the cable thermometers read. Grain stored wetter than
 * 15 % heats; over 40 °C is a HOT SPOT alarm, over 55 °C it spoils (lost tonnage). AERATION (key, needs mains for the
 * fan) cools it and slowly dries it. The OUTLET GATE (lever, mechanical) lets the loadout draw from it. Level lamps
 * HIGH / LOW, the level gauge and the bin number (the order the plant numbers its bins) show on its panel.
 */
public class TileGrainSilo extends com.dogpound.railmap.depot.DepotTile {
    ItemStack kind = ItemStack.EMPTY;
    long qty;
    float moist = 14f, temp = 15f;
    long inTotal, outTotal, spoiled;

    public TileGrainSilo() { super(new String[]{"hatch"}, new String[]{"Sample hatch (sample bags)"}, new int[]{9}); }

    @Override public String wiredName() { return "Grain silo bin"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return aerating() ? 15 : 1; }
    @Override public String gearState() {
        return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s %s t (%d%%) · %.1f %% moisture · %.0f C%s", Grain.name(kind), Grain.t(qty), pct(), moist, temp, aerating() ? " · AERATING" : "");
    }

    public int capacity() { return Math.max(1000, com.dogpound.railmap.freight6.F6Config.siloItems); }
    public int pct() { return (int) Math.min(100, qty * 100 / capacity()); }
    public long qty() { return qty; }
    public float moist() { return moist; }
    public float temp() { return temp; }
    public ItemStack kind() { return kind; }
    public boolean gateOpen() { return ctl.get("m_gate") == 1; }
    boolean aerating() { return ctl.get("k_aer") == 1 && powered() && mainsOk(); }

    /** how many of s this bin will still take (one kind of grain per bin) */
    public long room(ItemStack s) { return !kind.isEmpty() && !same(s) ? 0 : capacity() - qty; }
    boolean same(ItemStack s) { return !kind.isEmpty() && s.getItem() == kind.getItem() && s.getMetadata() == kind.getMetadata(); }

    /** put n items of s at moisture m in; returns how many went */
    public int add(ItemStack s, int n, float m) {
        int put = (int) Math.min(n, room(s));
        if (put <= 0) return 0;
        if (kind.isEmpty()) { kind = s.copy(); kind.setCount(1); moist = m; }
        else moist = Grain.mix(moist, qty, m, put);
        qty += put; inTotal += put;
        markDirty();
        return put;
    }

    /** draw up to n items out of the bottom (the outlet gate must be open) */
    public ItemStack take(int n) {
        int got = (int) Math.min(n, qty);
        if (got <= 0 || kind.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = kind.copy();
        s.setCount(got);
        qty -= got; outTotal += got;
        if (qty <= 0) { qty = 0; kind = ItemStack.EMPTY; temp = 15f; }
        markDirty();
        return s;
    }

    /** test / command: set the bin's contents */
    public void set(ItemStack s, long n, float m) { kind = n <= 0 ? ItemStack.EMPTY : s.copy(); if (!kind.isEmpty()) kind.setCount(1); qty = Math.max(0, n); moist = m; markDirty(); }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "probe" -> { return qty <= 0 ? "§7The bin is empty" : String.format(Locale.ROOT, "§aBin probe: %s, %.1f %% moisture, %.0f C at the core, %s t", Grain.name(kind), moist, temp, Grain.t(qty)); }
            case "reset" -> { AlarmManager.get(world).clear("grain:hot:" + pos.toLong()); AlarmManager.get(world).clear("grain:full:" + pos.toLong()); return "§7Alarms reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("k_aer")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); return ctl.get(id) == 1 ? (mainsOk() ? "§aAeration fan ON" : "§cAeration selected - but no mains for the fan") : "§7Aeration off"; }
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        // grain respiration: wet grain heats, aeration pulls it back toward ambient and dries it a little
        if (qty > 0) {
            if (F6Config.grainSpoils && moist > Grain.SAFE) temp += (moist - Grain.SAFE) * 0.004f;
            if (aerating()) { temp += (12f - temp) * 0.01f; if (moist > 11f) moist -= 0.0008f; }
            else if (moist <= Grain.SAFE) temp += (15f - temp) * 0.002f;
            temp = Math.min(80f, temp);
            if (temp >= 40f) AlarmManager.get(world).raise(world, "grain:hot:" + pos.toLong(), AlarmManager.Cat.FREIGHT, temp >= 55f ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                    String.format(Locale.ROOT, "Grain bin at %d,%d: HOT SPOT %.0f C (%.1f %% moisture) - aerate or turn the bin", pos.getX(), pos.getZ(), temp, moist), pos, true);
            else if (temp < 35f) AlarmManager.get(world).clear("grain:hot:" + pos.toLong());
            if (temp >= 55f && age % 1200 == 0) {
                long lost = Math.max(1, qty / 100);
                qty -= lost; spoiled += lost;
                if (qty <= 0) { qty = 0; kind = ItemStack.EMPTY; }
                Recorder.event(world, String.format(Locale.ROOT, "Grain bin %d,%d: %s t spoiled (heating)", pos.getX(), pos.getZ(), Grain.t(lost)));
            }
            markDirty();
        }
        if (pct() >= 98) AlarmManager.get(world).raise(world, "grain:full:" + pos.toLong(), AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MINOR,
                "Grain bin at " + pos.getX() + "," + pos.getZ() + ": HIGH LEVEL - bin full", pos, false);
        else AlarmManager.get(world).clear("grain:full:" + pos.toLong());
        int n = Grain.bins(world, pos).indexOf(this);
        put("pwr", powered()); put("mainsl", mains.ok()); put("aerl", aerating()); put("gatel", gateOpen());
        put("high", pct() >= 90); put("low", qty > 0 && pct() <= 10); put("hot", temp >= 40f); put("wet", qty > 0 && moist > Grain.SAFE);
        put("levelg", pct()); put("tempg", Math.round(Math.min(100, temp / 80f * 100))); put("moistg", Math.round(Math.min(100, moist / 25f * 100)));
        text("binno", n < 0 ? "--" : String.valueOf(n + 1));
        text("tons", Grain.t(qty));
        text("line1", qty <= 0 ? "BIN EMPTY" : cut(Grain.name(kind).toUpperCase(Locale.ROOT), 16));
        text("line2", String.format(Locale.ROOT, "%.1f%% H2O %.0fC", moist, temp));
        for (int k = 0; k < 4; k++) put("tc" + k, qty > 0 && temp - k * (temp - 15f) * 0.25f >= 30f);   // four cable thermometers, core to wall: lit = that cable reads 30 C+
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setTag("kind", kind.writeToNBT(new NBTTagCompound())); t.setLong("qty", qty); t.setFloat("moist", moist); t.setFloat("temp", temp);
        t.setLong("in", inTotal); t.setLong("out", outTotal); t.setLong("spoil", spoiled);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        kind = new ItemStack(t.getCompoundTag("kind")); qty = t.getLong("qty"); moist = t.hasKey("moist") ? t.getFloat("moist") : 14f; temp = t.hasKey("temp") ? t.getFloat("temp") : 15f;
        inTotal = t.getLong("in"); outTotal = t.getLong("out"); spoiled = t.getLong("spoil");
    }
}
