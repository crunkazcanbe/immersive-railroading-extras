package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.Freight;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.Locale;

/**
 * Grain Receiving Pit (batch 6 #26): grated hopper between the rails with the pit conveyor, dust collector and the
 * truck/car intake scale. A hopper car standing over it is emptied when you open its GATE (START): grain only, at twice
 * the leg's rate, into the pit (27 slots - open the grate to see it; hoppers / pipes can tip into it too). The moisture
 * of what comes in is sampled (PROBE) from the field it came from - wet weather, wet grain. DUST key runs the dust
 * collector (running without it is a dust-explosion alarm). Mains + 12 V.
 */
public class TileGrainPit extends com.dogpound.railmap.depot.DepotTile {
    float moist = 14f;
    private boolean gate;
    private int carIn, total;
    private String car = "";

    public TileGrainPit() { super(new String[]{"pit"}, new String[]{"Receiving pit (grain)"}, new int[]{27}); }

    @Override public String wiredName() { return "Grain receiving pit"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return gate ? 60 : ctl.get("k_dust") == 1 ? 20 : 2; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "pit %d items · %.1f %% · %s", count(), moist, gate ? "UNLOADING " + car : "gate shut"); }

    public InventoryBasic pit() { return store(0); }
    public int count() { int n = 0; for (int i = 0; i < pit().getSizeInventory(); i++) n += pit().getStackInSlot(i).getCount(); return n; }
    public boolean dustOn() { return ctl.get("k_dust") == 1 && powered() && mainsOk(); }

    /** the leg draws from the pit: one kind of grain at a time */
    public ItemStack draw(int n) {
        ItemStack got = ItemStack.EMPTY;
        for (int i = 0; i < pit().getSizeInventory() && got.getCount() < n; i++) {
            ItemStack s = pit().getStackInSlot(i);
            if (s.isEmpty() || !Grain.isGrain(s) || !got.isEmpty() && !(s.getItem() == got.getItem() && s.getMetadata() == got.getMetadata())) continue;
            int k = Math.min(n - got.getCount(), s.getCount());
            if (got.isEmpty()) { got = s.copy(); got.setCount(k); } else got.grow(k);
            s.shrink(k);
            if (s.isEmpty()) pit().setInventorySlotContents(i, ItemStack.EMPTY);
        }
        if (!got.isEmpty()) markDirty();
        return got;
    }

    /** put grain back (the leg could not deliver) */
    public void giveBack(ItemStack s) { ItemStack r = Cars.put(new InvWrapper(pit()), s); if (!r.isEmpty()) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, r); }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the pit conveyor (" + mains.what + ")";
                Freight f = Cars.goods(world, pos, 6);
                if (f == null) return "§cNo wagon standing over the pit";
                gate = true; carIn = 0; car = Cars.name(f);
                moist = Grain.mix(moist, count(), Grain.fieldMoisture(world, pos), 1);
                return "§aCar gate open - unloading " + car;
            }
            case "stop" -> { gate = false; return String.format(Locale.ROOT, "§eGate shut - %s t received", Grain.t(carIn)); }
            case "probe" -> {
                float m = Grain.fieldMoisture(world, pos);
                text("probe", String.format(Locale.ROOT, "%.1f", m));
                return String.format(Locale.ROOT, "§aProbe sample: %.1f %% moisture%s", m, m > Grain.SAFE ? " §e- route it through the DRYER" : " - dry enough to bin");
            }
            case "reset" -> { AlarmManager.get(world).clear("grain:dust:" + pos.toLong()); return "§7Alarms reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) gate = false; return ctl.get(id) == 1 ? "§cE-STOP - pit conveyor stopped" : "§aE-stop released"; }
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        Freight f = Cars.goods(world, pos, 6);
        if (gate) {
            if (!powered() || !mainsOk() || f == null || ctl.get("m_estop") == 1) gate = false;
            else {
                int before = count();
                ItemStack s = Cars.take(Cars.inv(f), Math.min(F6Config.legRate * 2, Cars.room(new InvWrapper(pit()), new ItemStack(net.minecraft.init.Items.WHEAT))), Grain::isGrain);
                if (!s.isEmpty()) {
                    ItemStack rest = Cars.put(new InvWrapper(pit()), s);
                    if (!rest.isEmpty()) Cars.put(Cars.inv(f), rest);
                    int got = s.getCount() - rest.getCount();
                    moist = Grain.mix(moist, before, Grain.fieldMoisture(world, pos), got);
                    carIn += got; total += got;
                    world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_GRAVEL_FALL, net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.3f);
                } else {
                    gate = false;
                    Recorder.event(world, String.format(Locale.ROOT, "Grain pit %d,%d: %s unloaded, %s t", pos.getX(), pos.getZ(), car, Grain.t(carIn)));
                    world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1f);
                }
            }
        }
        boolean legRunning = false;
        for (TileGrainLeg l : com.dogpound.railmap.stn.StnUtil.near(world, TileGrainLeg.class, pos, 12)) legRunning |= l.running();
        boolean dusty = (gate || legRunning) && !dustOn();
        if (dusty && powered()) AlarmManager.get(world).raise(world, "grain:dust:" + pos.toLong(), AlarmManager.Cat.FIRE, AlarmManager.Sev.MAJOR,
                "Grain pit at " + pos.getX() + "," + pos.getZ() + ": DUST COLLECTOR OFF while handling grain - explosion risk", pos, false);
        else if (!dusty) AlarmManager.get(world).clear("grain:dust:" + pos.toLong());
        put("pwr", powered()); put("mainsl", mains.ok()); put("gatel", gate); put("carl", f != null); put("dustl", dustOn()); put("dusty", dusty);
        put("pitg", count() * 100 / (27 * 64)); put("wetl", count() > 0 && moist > Grain.SAFE);
        put("carg", f == null ? 0 : f.getPercentCargoFull());
        text("tons", Grain.t(carIn));
        text("total", Grain.t(total));
        text("line1", f == null ? "NO CAR OVER PIT" : cut(Cars.name(f).toUpperCase(Locale.ROOT), 18));
        text("line2", String.format(Locale.ROOT, "PIT %d  %.1f%% H2O", count(), moist));
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) new InvWrapper(pit()) : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setFloat("moist", moist); t.setBoolean("gate", gate); t.setInteger("carIn", carIn); t.setInteger("total", total); t.setString("car", car); }
    @Override protected void loadExtra(NBTTagCompound t) { moist = t.hasKey("moist") ? t.getFloat("moist") : 14f; gate = t.getBoolean("gate"); carIn = t.getInteger("carIn"); total = t.getInteger("total"); car = t.getString("car"); }
}
