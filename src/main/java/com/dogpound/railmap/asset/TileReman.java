package com.dogpound.railmap.asset;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotClock;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.depot.Diagnostics;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.depot.Stores;
import com.dogpound.railmap.grid.Thermal;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Remanufacturing Bench (her 48-next list #24): a parts-washer tank, a hydraulic strip press, an inspection table with a
 * crack detector and a grading screen. Cores (used components) go in the CORES bin; START runs the cycle on the first
 * one: WASH (heats the tank to the WASH selector's setpoint - hotter cleans better, so the inspection measures more
 * precisely, but takes longer and draws more mains), STRIP (the press takes it apart), INSPECT (its condition is
 * measured) and GRADE: grade A comes out as a remanufactured spare, B too if its overhaul kit is in the cores bin and KITS
 * is ON (else it is stripped for parts), C gives the sub-parts that pass plus metals, SCRAP goes to the scrap bin.
 * AUTO keeps going while there are cores. Needs mains for the tank heater and the press, 12 V for the controls.
 */
public class TileReman extends DepotTile {
    private static final int[] WASH_C = {40, 60, 80};
    private int phase, t;                 // 0 idle, 1 wash, 2 strip, 3 inspect
    private double washC = 20, pressKn, measured;
    private ItemStack work = ItemStack.EMPTY;
    private final LinkedList<String> log = new LinkedList<>();
    private int done, na, nb, nc, ns;

    public TileReman() {
        super(new String[]{"bin_in", "bin_out", "bin_scrap"}, new String[]{"Cores in (used parts + overhaul kits)", "Remanufactured + reusable parts", "Metals + scrap"}, new int[]{27, 27, 18});
    }

    @Override public String wiredName() { return "Remanufacturing bench"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return phase == 1 ? 40 : phase == 2 ? 30 : 2; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : new String[]{"idle", "washing", "stripping", "inspecting"}[phase] + (work.isEmpty() ? "" : " · " + Reman.describe(work)) + " · " + done + " done"; }
    @Override public void gearInfo(Map<String, Object> m) { super.gearInfo(m); m.put("phase", phase); m.put("done", done); m.put("gradeA", na); m.put("gradeB", nb); m.put("gradeC", nc); m.put("scrap", ns); }

    private int washSet() { return WASH_C[Math.floorMod(ctl.get("s_wash"), 3)]; }
    private boolean auto() { return ctl.get("s_mode") == 1; }
    private boolean kits() { return ctl.get("s_kit") == 0; }

    private int firstCore() {
        IInventory in = store(0);
        for (int i = 0; i < in.getSizeInventory(); i++) if (ItemSpare.isCore(in.getStackInSlot(i)) || ItemSpare.is(in.getStackInSlot(i), ItemSpare.Spare.BOGIE_BAD)) return i;
        return -1;
    }

    private int cores() {
        int n = 0;
        IInventory in = store(0);
        for (int i = 0; i < in.getSizeInventory(); i++) { ItemStack s = in.getStackInSlot(i); if (ItemSpare.isCore(s) || ItemSpare.is(s, ItemSpare.Spare.BOGIE_BAD)) n += s.getCount(); }
        return n;
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { if (ctl.get("m_estop") == 0 && phase > 0) abort("E-STOP"); return null; }
        return null;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> { return start(); }
            case "abort" -> { if (phase == 0) return "§7Nothing running"; abort("ABORT by " + who(who)); return "§6Cycle aborted - the core is back in the bin"; }
            case "print" -> { print(who); return "§7Printing the remanufacturing log"; }
            case "count" -> {
                StringBuilder b = new StringBuilder("Cores waiting: ");
                IInventory in = store(0);
                for (int i = 0; i < in.getSizeInventory(); i++) { ItemStack s = in.getStackInSlot(i); if (ItemSpare.isCore(s)) b.append(Reman.describe(s)).append("; "); }
                return "§7" + b;
            }
            default -> { return null; }
        }
    }

    private String start() {
        if (phase != 0) return "§7A cycle is already running";
        if (ctl.get("m_estop") == 1) return "§cE-STOP is in - pull it out first";
        if (!mainsOk()) return "§cNo mains - " + mains.what;
        int slot = firstCore();
        if (slot < 0) return "§7No cores in the CORES bin (used parts come back from depot repairs)";
        ItemStack s = store(0).getStackInSlot(slot);
        work = s.splitStack(1);
        if (s.isEmpty()) store(0).setInventorySlotContents(slot, ItemStack.EMPTY);
        store(0).markDirty();
        phase = 1; t = 0;
        washC = Thermal.ambient(world, pos);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.jack"), SoundCategory.BLOCKS, 0.6f, 1.1f);
        return "§aCycle started: " + label(work);
    }

    private static String label(ItemStack s) { return ItemSpare.isCore(s) ? Reman.describe(s) : ItemSpare.of(s).label; }
    private static float condOf(ItemStack s) { return ItemSpare.isCore(s) ? s.getTagCompound().getFloat("core") : 35; }

    private void abort(String why) {
        if (!work.isEmpty() && !Stores.put(store(0), work)) world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, work));
        work = ItemStack.EMPTY;
        phase = 0; pressKn = 0;
        Recorder.event(world, "REMAN: cycle aborted (" + why + ")");
    }

    @Override
    protected void work() {
        if (phase == 0) {
            washC += (Thermal.ambient(world, pos) - washC) * 0.01;
            pressKn = Math.max(0, pressKn - 5);
            if (auto() && age % 40 == 0 && powered() && ctl.get("m_estop") == 0 && firstCore() >= 0 && mainsOk()) start();
        } else if (!powered() || !mainsOk()) {
            if (age % 20 == 0) text("line1", !powered() ? "NO 12 V" : "MAINS LOST - HOLD");
        } else {
            t++;
            switch (phase) {
                case 1 -> {                                                   // wash: heat to the setpoint, hold 3 s
                    washC = Math.min(washSet(), washC + 0.25);
                    if (washC >= washSet() - 0.5 && t > 60) { phase = 2; t = 0; world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.7f, 1.0f); }
                }
                case 2 -> {                                                   // strip: the press ramps up, the part comes apart
                    pressKn = Math.min(160, pressKn + 2);
                    if (t % 20 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.steam_hammer"), SoundCategory.BLOCKS, 0.5f, 0.9f + world.rand.nextFloat() * 0.2f);
                    if (t > 80) { phase = 3; t = 0; pressKn = 0; }
                }
                case 3 -> {                                                   // inspect: crack detector + measurement
                    double unc = new double[]{12, 8, 5}[Math.floorMod(ctl.get("s_wash"), 3)];
                    if (t == 1) measured = Math.max(0, Math.min(100, condOf(work) + world.rand.nextGaussian() * unc / 2));
                    if (t > 60) finish(unc);
                }
                default -> phase = 0;
            }
        }
        if (age % 5 == 0) screens();
    }

    private void finish(double unc) {
        ItemSpare.Spare sp = ItemSpare.of(work);
        int g = Reman.grade(measured);
        List<ItemStack> out = new ArrayList<>();
        String result;
        ItemSpare.Spare kit = Reman.kit(sp);
        if (g == 0) { out.add(Reman.remanned(world, sp, 0)); result = "GRADE A - remanufactured"; na++; }
        else if (g == 1 && kits() && kit != null && Stores.count(store(0), kit) > 0) {
            Stores.take(store(0), kit, 1);
            out.add(Reman.remanned(world, sp, 1));
            result = "GRADE B - rebuilt with " + kit.label; nb++;
        } else {
            out.addAll(Reman.strip(world, sp, g));
            result = g == 1 ? "GRADE B - stripped for parts (no kit)" : g == 2 ? "GRADE C - parts + metals" : "SCRAP";
            if (g == 1) nb++; else if (g == 2) nc++; else ns++;
        }
        for (ItemStack s : out) {
            boolean metal = !(s.getItem() instanceof ItemSpare) && !(s.getItem() instanceof com.dogpound.railmap.item.ItemPart);
            if (!Stores.put(store(metal ? 2 : 1), s) && !Stores.put(store(metal ? 1 : 2), s))
                world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, s));
        }
        String line = String.format(Locale.ROOT, "%s %s: measured %.0f%% ±%.0f -> %s", DepotClock.stamp(world), label(work), measured, unc, result);
        log.addFirst(line);
        while (log.size() > 24) log.removeLast();
        AssetData.get(world).note(world, "REMAN " + line);
        Recorder.event(world, "REMAN: " + label(work) + " -> " + result);
        work = ItemStack.EMPTY;
        phase = 0; t = 0;
        done++;
        world.playSound(null, pos, g <= 1 ? com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok") : com.dogpound.railmap.sound.PackSounds.ev("ui.beep_error"), SoundCategory.BLOCKS, 0.5f, 1.0f);
    }

    private void screens() {
        put("pwr", true);
        put("mains", mainsOk());
        put("run", phase > 0);
        put("lwash", phase == 1);
        put("lstrip", phase == 2);
        put("linsp", phase == 3);
        int lastG = log.isEmpty() ? -1 : log.getFirst().contains("GRADE A") ? 0 : log.getFirst().contains("GRADE B") ? 1 : log.getFirst().contains("GRADE C") ? 2 : 3;
        put("ga", lastG == 0); put("gb", lastG == 1); put("gc", lastG == 2); put("gs", lastG == 3);
        boolean kitIn = false;
        for (ItemSpare.Spare k : new ItemSpare.Spare[]{ItemSpare.Spare.MOTOR_KIT, ItemSpare.Spare.FILTER_SET, ItemSpare.Spare.AXLE_BEARING, ItemSpare.Spare.AIR_HOSE}) if (Stores.count(store(0), k) > 0) kitIn = true;
        put("kit", kitIn);
        put("full", firstFree(store(1)) < 0);
        put("wtemp", (int) Math.round(washC));
        put("press", (int) Math.round(pressKn));
        put("prog", phase == 0 ? 0 : (int) Math.min(100, ((phase - 1) * 33 + Math.min(33, t * 33 / (phase == 2 ? 80 : 60)))));
        put("nin", cores()); put("ndone", done); put("na", na); put("nb", nb); put("nc", nc); put("ns", ns);
        text("line1", ctl.get("m_estop") == 1 ? "E-STOP IN" : phase == 0 ? (firstCore() < 0 ? "READY - NO CORES" : auto() ? "AUTO - NEXT CORE" : "READY - PRESS START")
                : new String[]{"", "WASHING " + Math.round(washC) + "/" + washSet() + "C", "STRIPPING " + Math.round(pressKn) + " KN", "INSPECTING"}[phase]);
        text("line2", work.isEmpty() ? "" : cut(label(work).toUpperCase(Locale.ROOT), 28));
        text("line3", phase == 3 && t > 20 ? String.format(Locale.ROOT, "MEASURED %.0f%% GRADE %s", measured, Reman.GRADES[Reman.grade(measured)]) : log.isEmpty() ? "" : cut(log.getFirst().substring(Math.min(log.getFirst().length(), 12)).toUpperCase(Locale.ROOT), 28));
    }

    private static int firstFree(IInventory inv) { for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i).isEmpty()) return i; return -1; }

    private void print(EntityPlayer who) {
        List<String> l = new ArrayList<>();
        l.add("REMANUFACTURING LOG");
        l.add(DepotClock.stamp(world) + " bench " + pos.getX() + "," + pos.getZ());
        l.add(String.format(Locale.ROOT, "%d done: %d A, %d B, %d C, %d scrap", done, na, nb, nc, ns));
        l.addAll(log);
        ItemStack book = Diagnostics.book("Reman log " + DepotClock.stamp(world), "Reman bench", l);
        EnumFacing f = controlFacing();
        world.spawnEntity(new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 1.1, pos.getZ() + 0.5 + f.getZOffset() * 0.7, book));
    }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setInteger("phase", phase); t.setInteger("t", this.t); t.setDouble("washC", washC); t.setDouble("meas", measured);
        if (!work.isEmpty()) t.setTag("work", work.writeToNBT(new NBTTagCompound()));
        NBTTagList l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("log", l);
        t.setIntArray("n", new int[]{done, na, nb, nc, ns});
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        phase = t.getInteger("phase"); this.t = t.getInteger("t"); washC = t.hasKey("washC") ? t.getDouble("washC") : 20; measured = t.getDouble("meas");
        work = t.hasKey("work") ? new ItemStack(t.getCompoundTag("work")) : ItemStack.EMPTY;
        log.clear();
        NBTTagList l = t.getTagList("log", Constants.NBT.TAG_STRING);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
        int[] n = t.getIntArray("n");
        if (n.length == 5) { done = n[0]; na = n[1]; nb = n[2]; nc = n[3]; ns = n[4]; }
    }

    @Override
    public void dropAll() {
        super.dropAll();
        if (!work.isEmpty()) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, work);
        work = ItemStack.EMPTY;
    }
}
