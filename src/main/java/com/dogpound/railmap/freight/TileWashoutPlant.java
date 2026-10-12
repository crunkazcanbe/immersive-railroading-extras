package com.dogpound.railmap.freight;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.biz.BizData;
import com.dogpound.railmap.biz.Custody;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.item.ItemContainer;
import com.dogpound.railmap.track.Rails;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.UUID;

/**
 * Wagon + Container Washout Plant (ChatGPT batch 3 #23): the cleaning shed for contaminated stock. Stand a wagon in the
 * bay (within 4 blocks, stopped) and/or put containers in the BOX BAY, pick a PROGRAM and press WASH:
 * <pre>
 *   RINSE      20 s, water only             -> 90 % clean (bulk dust, not chemicals)
 *   DETERGENT  40 s, + 1 slime ball         -> 100 % clean
 *   STEAM      60 s, + detergent + boiler   -> 100 % and a STEAM WASH CERTIFICATE: the only thing that clears a
 *                                              wagon / box whose last load was hazardous for food again
 * </pre>
 * 40 L of water a second from the 8 000 L tank (WATER MAIN open beside water, or a bucket on FILL); all of it ends up in
 * the 6 000 L effluent tank - full, the plant stops until DRAIN pumps it to the sewer (mains). The boiler burns coal from
 * its fuel bunker to make steam (HEAT gauge must reach 100). A certificate (paper) goes into the CERT TRAY for each unit
 * steamed; each wash is billed to the wagon owner's cleaning account. 12 V controls, mains for the pumps; E-STOP.
 */
public class TileWashoutPlant extends CargoTile {
    static final String[] PROGS = {"RINSE", "DETERGENT", "STEAM"};
    static final int[] SECS = {20, 40, 60};
    private static final long COST = 450;
    private int water = 5000, effluent, heat, progress, washed;
    private boolean running, draining;
    private UUID car;
    private String carName = "", lastCert = "";

    public TileWashoutPlant() {
        super(new String[]{"chem", "fuel", "bay", "tray"},
                new String[]{"Detergent hopper (slime balls)", "Boiler fuel bunker (coal / charcoal)", "Container box bay", "Certificate tray"}, new int[]{9, 9, 3, 9});
    }

    @Override public String wiredName() { return "Washout plant"; }
    @Override protected long load() { return running ? (prog() == 2 ? 30 : 18) : draining ? 10 : 1; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : (running ? PROGS[prog()] + " " + progress * 100 / SECS[prog()] + "%" : "idle") + " · water " + water + " L · effluent " + effluent + " L"; }

    int prog() { return Math.floorMod(ctl.get("s_prog"), 3); }
    boolean ok() { return powered() && mainsOk() && ctl.get("m_estop") == 0; }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "wash" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the pumps (" + mains.what + ")";
                if (running) return "§7Already washing";
                if (car == null && boxes() == 0) return "§7Nothing in the bay - stop a wagon here or put containers in the box bay";
                if (water < 40 * SECS[prog()] / 2) return "§cNot enough water in the tank";
                if (effluent > 5000) return "§cEffluent tank full - DRAIN first";
                if (prog() > 0 && chem() == 0) return "§cDetergent hopper empty (slime balls)";
                if (prog() == 2 && heat < 100) return "§cBoiler not up to steam yet (" + heat + "%) - fuel in the bunker";
                running = true; progress = 0;
                world.playSound(null, pos, ModSounds.CONTACTOR, SoundCategory.BLOCKS, 0.7f, 1f);
                return "§a" + PROGS[prog()] + " started" + (car != null ? " on " + carName : "") + (boxes() > 0 ? " + " + boxes() + " box(es)" : "");
            }
            case "stop" -> { running = false; draining = false; return "§eStopped"; }
            case "drain" -> {
                if (!mainsOk()) return "§cNo mains for the effluent pump";
                draining = !draining;
                return draining ? "§aEffluent pump running to the sewer" : "§eEffluent pump stopped";
            }
            case "fill" -> {
                if (who == null || who.getHeldItemMainhand().getItem() != Items.WATER_BUCKET) return "§7Hold a water bucket (or open the WATER MAIN beside water)";
                water = Math.min(8000, water + 1000);
                who.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                return "§aTank " + water + " L";
            }
            case "cert" -> {
                if (lastCert.isEmpty()) return "§7No certificate issued yet";
                certificate(lastCert);
                return "§aDuplicate certificate printed into the tray";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) { running = false; draining = false; } return ctl.get(id) == 1 ? "§cE-STOP - pumps stopped" : "§aE-stop released"; }
        return null;
    }

    private int boxes() { int n = 0; for (int i = 0; i < 3; i++) if (ItemContainer.is(store(2).getStackInSlot(i))) n++; return n; }
    private int count(int st, java.util.function.Predicate<ItemStack> p) { int n = 0; for (int i = 0; i < store(st).getSizeInventory(); i++) { ItemStack s = store(st).getStackInSlot(i); if (p.test(s)) n += s.getCount(); } return n; }
    private int chem() { return count(0, s -> s.getItem() == Items.SLIME_BALL); }
    private boolean take(int st, java.util.function.Predicate<ItemStack> p) {
        for (int i = 0; i < store(st).getSizeInventory(); i++) { ItemStack s = store(st).getStackInSlot(i); if (!s.isEmpty() && p.test(s)) { s.shrink(1); if (s.isEmpty()) store(st).setInventorySlotContents(i, ItemStack.EMPTY); return true; } }
        return false;
    }

    private boolean waterBeside() { for (EnumFacing f : EnumFacing.HORIZONTALS) if (world.getBlockState(pos.offset(f)).getMaterial() == Material.WATER) return true; return false; }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        Custody cu = BizData.get(world).custody;
        // the wagon in the bay
        car = null; carName = "";
        double best = 16;
        for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
            if (s.isDead() || s instanceof Locomotive) continue;
            Vec3d p = s.getPosition();
            double d = pos.distanceSqToCenter(p.x, p.y, p.z);
            double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
            if (d < best && kmh < 0.5) { best = d; car = s.getUUID(); carName = Rails.name(s); }
        }
        if (ctl.get("s_main") == 1 && waterBeside() && water < 8000) water = Math.min(8000, water + 300);
        if (draining && mainsOk()) { effluent = Math.max(0, effluent - 300); if (effluent == 0) draining = false; }
        boolean steam = prog() == 2;
        if (powered() && heat < 100 && (steam || running) && age % 200 == 0 && take(1, s -> s.getItem() == Items.COAL)) heat = Math.min(100, heat + 35);
        else if (age % 100 == 0 && heat > 0 && !running) heat = Math.max(0, heat - 2);
        boolean fault = false;
        if (running) {
            if (!ok()) { running = false; fault = true; }
            else if (water < 40 || effluent >= 6000) { running = false; fault = true; Recorder.event(world, "Washout plant stopped: " + (water < 40 ? "out of water" : "effluent tank full")); }
            else if (car == null && boxes() == 0) { running = false; }
            else {
                water -= 40; effluent += 40;
                if (steam) heat = Math.max(0, heat - 1);
                if (++progress >= SECS[prog()]) finish(cu);
            }
        }
        if (effluent >= 6000) AlarmManager.get(world).raise(world, "washout:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "Washout plant effluent tank FULL - drain it", pos, false);
        else AlarmManager.get(world).clear("washout:" + pos.toLong());
        String last = car == null ? "" : cu.carLast.getOrDefault(car, "?");
        long cert = car == null ? 0 : cu.carCert.getOrDefault(car, 0L);
        put("pwr", powered()); put("mainsl", mainsOk()); put("run", running); put("carl", car != null); put("boxl", boxes() > 0);
        put("effl", effluent >= 5400); put("loww", water < 1500); put("nochem", chem() == 0); put("nofuel", count(1, s -> s.getItem() == Items.COAL) == 0);
        put("haz", "HAZ".equals(last)); put("fault", fault); put("done", !running && progress >= SECS[prog()] && progress > 0); put("drainl", draining);
        put("water", water * 100 / 8000); put("efflg", effluent * 100 / 6000); put("prog", running ? progress * 100 / SECS[prog()] : 0); put("heat", heat);
        text("line1", !powered() ? "" : ctl.get("m_estop") == 1 ? "E-STOP" : running ? PROGS[prog()] + " " + progress + "/" + SECS[prog()] + "S" : !mainsOk() ? "NO MAINS" : "READY - " + PROGS[prog()]);
        text("line2", car == null ? "NO WAGON IN BAY" : cut(carName, 12) + " " + Math.round(cu.cars.getOrDefault(car, 100.0)) + "% " + last);
        text("line3", car == null ? "" : Custody.certValid(cert, world.getTotalWorldTime()) ? "CERT VALID" : "NO CERT");
        put("nwash", washed);
    }

    private void finish(Custody cu) {
        running = false;
        int p = prog();
        boolean steam = p == 2;
        if (p > 0) take(0, s -> s.getItem() == Items.SLIME_BALL);
        double clean = p == 0 ? 90 : 100;
        String who = placerName();
        if (car != null) {
            boolean wasHaz = "HAZ".equals(cu.carLast.get(car));
            cu.washCar(world, car, clean, steam);
            washed++;
            bill(car, carName);
            Recorder.event(world, "Washout: " + carName + " " + PROGS[p] + (wasHaz && !steam ? " - still NOT fit for food (hazardous last load needs STEAM)" : ""));
            if (steam) certificate(lastCert = carName + " (" + car.toString().substring(0, 8) + ")");
            com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "car_washed", "car", carName, "program", PROGS[p]);
        }
        for (int i = 0; i < 3; i++) {
            ItemStack b = store(2).getStackInSlot(i);
            if (!ItemContainer.is(b)) continue;
            cu.wash(world, b, pos, who, clean, steam);
            washed++;
            if (steam) certificate(lastCert = ItemContainer.pretty(b));
        }
        world.playSound(null, pos, ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.8f, 1f);
        markDirty();
    }

    private String placerName() { return "washout plant"; }

    /** a paper wash certificate into the tray */
    private void certificate(String unit) {
        ItemStack c = new ItemStack(Items.PAPER);
        c.setStackDisplayName("§fSteam Wash Certificate: " + unit);
        NBTTagCompound d = c.getOrCreateSubCompound("display");
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("§7Program STEAM SANITISE - fit for food"));
        lore.appendTag(new NBTTagString("§7Issued " + Recorder.clock(world, world.getTotalWorldTime()) + " at " + pos.getX() + " " + pos.getZ()));
        lore.appendTag(new NBTTagString("§7Valid " + CargoConfig.certDays + " day(s)"));
        d.setTag("Lore", lore);
        for (int i = 0; i < store(3).getSizeInventory(); i++) if (store(3).getStackInSlot(i).isEmpty()) { store(3).setInventorySlotContents(i, c); world.playSound(null, pos, ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f); return; }
    }

    private void bill(UUID id, String name) {
        if (!com.dogpound.railmap.RailMapConfig.railwayEconomy) return;
        CompanyData cd = CompanyData.get(world);
        Company c = cd.ownerOfTrain(id, "");
        if (c != null) cd.debit(world, c, Company.Kind.CLEANING, COST * (prog() + 1), "Washout " + PROGS[prog()] + ": " + name, false);
    }

    public String status() { return gearState(); }
    public boolean running() { return running; }
    public String bayCar() { return carName; }

    /** console: WASH / STOP */
    public String remote(String what) { return com.dogpound.railmap.controls.IControlled.operate(this, what, null); }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setInteger("water", water); t.setInteger("effl", effluent); t.setInteger("heat", heat); t.setInteger("prog", progress); t.setInteger("washed", washed);
        t.setBoolean("running", running); t.setBoolean("drain", draining); t.setString("cert", lastCert);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        water = t.hasKey("water") ? t.getInteger("water") : 5000; effluent = t.getInteger("effl"); heat = t.getInteger("heat"); progress = t.getInteger("prog"); washed = t.getInteger("washed");
        running = t.getBoolean("running"); draining = t.getBoolean("drain"); lastCert = t.getString("cert");
    }
}
