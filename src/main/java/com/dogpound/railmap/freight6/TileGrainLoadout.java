package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.Freight;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.freight.CargoRules;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;

/**
 * Grain Loadout (batch 6 #26): the batch-weigh loadout over the track - surge bin, weigh hopper on load cells and the
 * telescopic loading spout. Pick the bin (FROM BIN 1-6, its OUTLET GATE must be open) and how full to load the car
 * (1/4 1/2 3/4 FULL), lower the SPOUT, START: it fills the weigh hopper (one 10 t batch = 200 items), weighs it, then
 * drops it into the hopper car standing under it, batch after batch until the target, the car's limit or the bin runs
 * out. Wagon rules apply (a box car or flat car refuses grain the way CargoRules says). Mains + 12 V.
 */
public class TileGrainLoadout extends com.dogpound.railmap.depot.DepotTile {
    static final int BATCH = 200;
    static final String[] QTY = {"1/4", "1/2", "3/4", "FULL"};
    private boolean run;
    private int phase, batch, loaded, target, total, batches;    // phase 0 idle 1 filling 2 weighing 3 discharging
    private String car = "", why = "";
    private ItemStack batchKind = ItemStack.EMPTY;

    public TileGrainLoadout() { super(new String[]{"cab"}, new String[]{"Loadout cabin locker"}, new int[]{9}); }

    @Override public String wiredName() { return "Grain loadout"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return run ? 120 : 3; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : run ? String.format(Locale.ROOT, "LOADING %s %s/%s t", car, Grain.t(loaded), Grain.t(target)) : "idle" + (why.isEmpty() ? "" : " · " + why); }

    int bin() { return Math.floorMod(ctl.get("s_bin"), Grain.MAX_BINS); }
    boolean spoutDown() { return ctl.get("m_spout") == 1; }

    int capacityOf(Freight f) {
        int n = 0;
        net.minecraftforge.items.IItemHandler h = Cars.inv(f);
        if (h != null) for (int i = 0; i < h.getSlots(); i++) n += Math.min(64, h.getSlotLimit(i));
        return n;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!spoutDown()) return "§cLower the SPOUT onto the car first";
                if (!mainsOk()) return "§cNo mains (" + mains.what + ")";
                TileGrainSilo b = Grain.bin(world, pos, bin());
                if (b == null) return "§cBIN " + (bin() + 1) + " does not exist (Grain Silos within 24 blocks)";
                if (!b.gateOpen()) return "§cBIN " + (bin() + 1) + "'s OUTLET GATE is shut";
                if (b.qty() <= 0) return "§cBIN " + (bin() + 1) + " is empty";
                Freight f = Cars.goods(world, pos, 6);
                if (f == null) return "§cNo wagon standing under the spout";
                String no = CargoRules.refuse(Cars.type(f), b.kind());
                if (no != null) return "§c" + Cars.name(f) + ": " + no;
                int cap = capacityOf(f), q = Math.floorMod(ctl.get("s_qty"), 4);
                int have = cap * f.getPercentCargoFull() / 100;
                target = Math.max(0, cap * (q + 1) / 4 - have);
                if (target <= 0) return "§7The car is already that full";
                run = true; phase = 1; batch = 0; loaded = 0; car = Cars.name(f); why = "";
                return "§aLoading " + car + " - " + QTY[q] + " (" + Grain.t(target) + " t) from BIN " + (bin() + 1);
            }
            case "stop" -> { run = false; returnBatch(); phase = 0; return String.format(Locale.ROOT, "§eStopped - %s t loaded", Grain.t(loaded)); }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) { run = false; phase = 0; } return ctl.get(id) == 1 ? "§cE-STOP - gates shut" : "§aE-stop released"; }
            case "m_spout" -> { if (run) return "§cStop loading before raising the spout"; ctl.set(id, ctl.get(id) == 1 ? 0 : 1); return ctl.get(id) == 1 ? "§eSpout lowered" : "§7Spout raised clear"; }
            default -> { return null; }
        }
    }

    /** a stopped batch goes back to its bin */
    void returnBatch() {
        if (batch <= 0) return;
        TileGrainSilo b = Grain.bin(world, pos, bin());
        if (b != null && !batchKind.isEmpty()) batch -= b.add(batchKind, batch, b.moist());
        if (batch > 0 && !batchKind.isEmpty()) { ItemStack d = batchKind.copy(); d.setCount(batch); net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, d); }
        batch = 0;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        Freight f = Cars.goods(world, pos, 6);
        TileGrainSilo b = Grain.bin(world, pos, bin());
        if (run && (!powered() || !mainsOk() || f == null || !spoutDown() || ctl.get("m_estop") == 1)) { run = false; why = f == null ? "car moved" : "interlock"; returnBatch(); phase = 0; }
        if (run) {
            switch (phase) {
                case 1 -> {
                    int want = Math.min(BATCH, target - loaded) - batch;
                    if (b == null || !b.gateOpen() || b.qty() <= 0) { if (batch > 0) phase = 2; else { run = false; why = "bin empty / gate shut"; } }
                    else { ItemStack s = b.take(Math.min(want, 60)); if (!s.isEmpty()) { batchKind = s.copy(); batchKind.setCount(1); } batch += s.getCount(); if (batch >= Math.min(BATCH, target - loaded)) phase = 2; }
                }
                case 2 -> phase = 3;                                                     // a second on the load cells
                case 3 -> {
                    ItemStack s = batchKind.copy();
                    if (s.isEmpty() || batch <= 0) { phase = 1; break; }
                    s.setCount(Math.min(batch, 64));
                    ItemStack rest = Cars.put(Cars.inv(f), s);
                    int put = s.getCount() - rest.getCount();
                    batch -= put; loaded += put; total += put;
                    if (put > 0) world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_GRAVEL_FALL, net.minecraft.util.SoundCategory.BLOCKS, 0.7f, 1.4f);
                    if (rest.getCount() > 0) { run = false; why = "car full"; returnBatch(); }
                    if (batch <= 0) {
                        batches++;
                        if (loaded >= target) { run = false; why = "target reached"; }
                        else phase = 1;
                    }
                    if (!run) {
                        phase = 0;
                        Recorder.event(world, String.format(Locale.ROOT, "Grain loadout %d,%d: %s loaded %s t (%s)", pos.getX(), pos.getZ(), car, Grain.t(loaded), why));
                        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1f);
                    }
                }
                default -> phase = 1;
            }
        }
        put("pwr", powered()); put("mainsl", mains.ok()); put("runl", run); put("spoutl", spoutDown()); put("carl", f != null);
        put("filll", phase == 1); put("weighl", phase == 2); put("discl", phase == 3); put("gatel", b != null && b.gateOpen());
        put("hopperg", batch * 100 / BATCH); put("carg", f == null ? 0 : f.getPercentCargoFull()); put("bing", b == null ? 0 : b.pct());
        text("loaded", Grain.t(loaded));
        text("target", Grain.t(target));
        text("scale", Grain.t(batch));
        text("line1", f == null ? "NO CAR UNDER SPOUT" : cut(Cars.name(f).toUpperCase(Locale.ROOT), 18));
        text("line2", "BIN " + (bin() + 1) + " " + (b == null ? "NONE" : Grain.t(b.qty()) + "T " + (b.gateOpen() ? "GATE OPEN" : "GATE SHUT")));
        text("line3", run ? new String[]{"", "FILLING HOPPER", "WEIGHING", "DISCHARGING"}[phase] : why.isEmpty() ? "READY" : why.toUpperCase(Locale.ROOT));
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setBoolean("run", run); t.setInteger("phase", phase); t.setInteger("batch", batch); t.setInteger("loaded", loaded); t.setInteger("target", target);
        t.setInteger("total", total); t.setInteger("batches", batches); t.setString("car", car); t.setString("why", why);
        t.setTag("bk", batchKind.writeToNBT(new NBTTagCompound()));
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        run = t.getBoolean("run"); phase = t.getInteger("phase"); batch = t.getInteger("batch"); loaded = t.getInteger("loaded"); target = t.getInteger("target");
        total = t.getInteger("total"); batches = t.getInteger("batches"); car = t.getString("car"); why = t.getString("why");
        batchKind = new ItemStack(t.getCompoundTag("bk"));
    }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }
}
