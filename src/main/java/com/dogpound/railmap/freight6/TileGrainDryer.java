package com.dogpound.railmap.freight6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Locale;

/**
 * Grain Dryer (batch 6 #26): a mixed-flow tower dryer - wet garner on top, the column with its plenum, a coal-fired
 * burner and fan, the discharge meter rolls and cooling section at the foot. The leg fills its garner (DRYER on the
 * distributor, up to 2 000 items). BURNER key + START light it: it burns coal / charcoal from its fuel hopper
 * (1 coal = 400 item-percent of drying) and brings the grain down to the TARGET moisture (12-15 %); dried grain is
 * metered out to the bin set on DISCHARGE TO (BIN 1-6) while a leg is running. Firing an empty dryer overheats it
 * (OVERTEMP trip). Mains (fan) + 12 V.
 */
public class TileGrainDryer extends com.dogpound.railmap.depot.DepotTile {
    static final int GARNER = 2000;
    static final float[] TARGET = {12f, 13f, 14f, 15f};
    ItemStack kind = ItemStack.EMPTY;
    long qty;
    float moist = 14f, plenum = 15f, fuelLeft;
    private boolean burn, trip;
    private long dried;

    public TileGrainDryer() { super(new String[]{"fuel"}, new String[]{"Burner fuel hopper (coal / charcoal)"}, new int[]{9}); }

    @Override public String wiredName() { return "Grain dryer"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return burn ? 180 : 2; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "garner %s t %.1f %% · plenum %.0f C · %s", Grain.t(qty), moist, plenum, trip ? "OVERTEMP TRIP" : burn ? "FIRING" : "off"); }

    public long qty() { return qty; }
    public float moist() { return moist; }
    public boolean burning() { return burn; }
    float target() { return TARGET[Math.floorMod(ctl.get("s_target"), TARGET.length)]; }

    public int fill(ItemStack s, int n, float m) {
        if (!kind.isEmpty() && !(s.getItem() == kind.getItem() && s.getMetadata() == kind.getMetadata())) return 0;
        int put = (int) Math.min(n, GARNER - qty);
        if (put <= 0) return 0;
        if (kind.isEmpty()) { kind = s.copy(); kind.setCount(1); moist = m; } else moist = Grain.mix(moist, qty, m, put);
        qty += put;
        markDirty();
        return put;
    }

    int fuelItems() { int n = 0; for (int i = 0; i < store(0).getSizeInventory(); i++) { ItemStack s = store(0).getStackInSlot(i); if (s.getItem() == Items.COAL) n += s.getCount(); } return n; }

    boolean burnCoal() {
        for (int i = 0; i < store(0).getSizeInventory(); i++) {
            ItemStack s = store(0).getStackInSlot(i);
            if (s.getItem() != Items.COAL) continue;
            s.shrink(1);
            if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY);
            fuelLeft += 400f;
            return true;
        }
        return false;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (ctl.get("k_burner") == 0) return "§cBURNER key is OFF";
                if (trip) return "§cOVERTEMP trip - RESET first";
                if (!mainsOk()) return "§cNo mains for the dryer fan (" + mains.what + ")";
                if (fuelItems() == 0 && fuelLeft <= 0) return "§cThe fuel hopper is empty (coal / charcoal)";
                burn = true;
                world.playSound(null, pos, net.minecraft.init.SoundEvents.ITEM_FIRECHARGE_USE, net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 0.7f);
                return "§aBurner lit - drying to " + target() + " %";
            }
            case "stop" -> { burn = false; return "§eBurner off"; }
            case "reset" -> { if (plenum > 95) return "§cStill too hot - wait for the plenum to cool"; trip = false; AlarmManager.get(world).clear("grain:dryer:" + pos.toLong()); return "§7Overtemp reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) burn = false; return ctl.get(id) == 1 ? "§cE-STOP - burner out, fan off" : "§aE-stop released"; }
            case "k_burner" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 0) burn = false; return ctl.get(id) == 1 ? "§eBurner key ON" : "§7Burner key OFF"; }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        if (burn && (!powered() || !mainsOk() || ctl.get("m_estop") == 1 || ctl.get("k_burner") == 0)) burn = false;
        if (burn) {
            if (fuelLeft <= 0 && !burnCoal()) { burn = false; Recorder.event(world, "Grain dryer: burner out of fuel"); }
            else {
                plenum += ((qty > 0 ? 80f : 130f) - plenum) * 0.08f;
                if (qty > 0 && moist > target()) {
                    float pts = Math.min(40f / qty, moist - target());
                    moist -= pts; fuelLeft -= pts * qty;
                } else fuelLeft -= 2f;                                              // idling burner still burns
                if (plenum > 105) {
                    burn = false; trip = true;
                    AlarmManager.get(world).raise(world, "grain:dryer:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR,
                            "Grain dryer at " + pos.getX() + "," + pos.getZ() + ": PLENUM OVERTEMP - burner tripped (fired with no grain in the column)", pos, true);
                    world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("alarm.annunciator"), net.minecraft.util.SoundCategory.BLOCKS, 1f, 1f);
                }
            }
        } else plenum += (15f - plenum) * 0.05f;
        // discharge dry grain to the bin while a leg runs (the dryer's discharge conveyor feeds the leg's second boot)
        boolean legRun = false;
        for (TileGrainLeg l : com.dogpound.railmap.stn.StnUtil.near(world, TileGrainLeg.class, pos, 16)) legRun |= l.running();
        int binNo = Math.floorMod(ctl.get("s_bin"), Grain.MAX_BINS);
        boolean disch = false;
        if (qty > 0 && moist <= target() + 0.01f && legRun && powered()) {
            TileGrainSilo b = Grain.bin(world, pos, binNo);
            if (b != null) {
                int n = (int) Math.min(qty, Math.max(1, F6Config.legRate / 2));
                int put = b.add(kind, n, moist);
                qty -= put; dried += put; disch = put > 0;
                if (qty <= 0) { qty = 0; kind = ItemStack.EMPTY; }
            }
        }
        List<TileGrainSilo> bins = Grain.bins(world, pos);
        put("pwr", powered()); put("mainsl", mains.ok()); put("burnl", burn); put("flame", burn && age % 40 < 30); put("trip", trip);
        put("dryl", qty > 0 && moist <= target()); put("wetl", qty > 0 && moist > target()); put("discl", disch); put("fuell", fuelItems() < 4);
        put("garnerg", (int) (qty * 100 / GARNER)); put("plenumg", Math.round(Math.min(100, plenum / 130f * 100))); put("moistg", Math.round(Math.min(100, moist / 25f * 100)));
        put("fuelg", Math.min(100, fuelItems() * 100 / (9 * 64)));
        text("moist", String.format(Locale.ROOT, "%.1f", moist));
        text("plen", String.format(Locale.ROOT, "%.0f", plenum));
        text("line1", qty <= 0 ? "GARNER EMPTY" : cut(Grain.name(kind).toUpperCase(Locale.ROOT), 14) + " " + Grain.t(qty) + "T");
        text("line2", "TARGET " + target() + "% > BIN " + (binNo + 1) + (binNo < bins.size() ? "" : " NONE"));
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setTag("kind", kind.writeToNBT(new NBTTagCompound())); t.setLong("qty", qty); t.setFloat("moist", moist); t.setFloat("plen", plenum); t.setFloat("fuel", fuelLeft);
        t.setBoolean("burn", burn); t.setBoolean("trip", trip); t.setLong("dried", dried);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        kind = new ItemStack(t.getCompoundTag("kind")); qty = t.getLong("qty"); moist = t.hasKey("moist") ? t.getFloat("moist") : 14f; plenum = t.hasKey("plen") ? t.getFloat("plen") : 15f; fuelLeft = t.getFloat("fuel");
        burn = t.getBoolean("burn"); trip = t.getBoolean("trip"); dried = t.getLong("dried");
    }
}
