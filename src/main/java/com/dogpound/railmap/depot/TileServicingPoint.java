package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;

import java.util.Locale;

/**
 * Carriage Servicing Point (her ChatGPT list #83): the layover services for passenger stock standing within 12 blocks -
 * fresh WATER from the water main (water beside the bollard), the CET pump that empties the toilet retention tanks
 * into its own holding tank (DISCHARGE it to the sewer when full), the interior CLEAN gang, and CATERING restocked
 * from the food in its store (each food item is 10 % of a vehicle's trolley). ALL does the lot; with AUTO on it does
 * whatever a vehicle that stands here needs, so trains are serviced during their layovers. The cleaning and catering
 * are billed to the operator.
 */
public class TileServicingPoint extends DepotTile {
    private static final int REACH = 12;
    private int cet, servicedToday;
    private long day;
    private String doing = "";

    public TileServicingPoint() { super(new String[]{"stock"}, new String[]{"Catering stock (food)"}, new int[]{27}); }

    @Override public String wiredName() { return "Carriage servicing point"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return doing.isEmpty() ? 1 : 12; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : (doing.isEmpty() ? "idle" : doing) + " · CET tank " + cet + "% · " + servicedToday + " serviced today"; }

    private boolean waterMain() {
        for (EnumFacing f : EnumFacing.values()) if (world.getBlockState(pos.offset(f)).getMaterial() == Material.WATER) return true;
        return false;
    }

    private int food() { int n = 0; for (int i = 0; i < store(0).getSizeInventory(); i++) if (store(0).getStackInSlot(i).getItem() instanceof ItemFood) n += store(0).getStackInSlot(i).getCount(); return n; }

    private boolean takeFood() {
        for (int i = 0; i < store(0).getSizeInventory(); i++) {
            ItemStack s = store(0).getStackInSlot(i);
            if (s.getItem() instanceof ItemFood) { s.shrink(1); if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY); return true; }
        }
        return false;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "water", "cetp", "clean", "cater" -> { ctl.set("on_" + id, ctl.get("on_" + id) == 1 ? 0 : 1); return "§e" + id.toUpperCase(Locale.ROOT) + (ctl.get("on_" + id) == 1 ? " on" : " off"); }
            case "all" -> { for (String s : new String[]{"water", "cetp", "clean", "cater"}) ctl.set("on_" + s, 1); return "§aFull service started"; }
            case "stop" -> { for (String s : new String[]{"water", "cetp", "clean", "cater"}) ctl.set("on_" + s, 0); return "§eServices stopped"; }
            case "discharge" -> {
                if (!mainsOk()) return "§cNo mains for the discharge pump";
                int was = cet; cet = 0;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.flush"), SoundCategory.BLOCKS, 0.8f, 0.8f);
                return "§aHolding tank discharged to the sewer (" + was + "%)";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        long d = world.getTotalWorldTime() / 24000;
        if (d != day) { day = d; servicedToday = 0; }
        boolean auto = ctl.get("s_auto") == 1;
        doing = "";
        if (powered() && mainsOk()) {
            FleetData f = FleetData.get(world);
            for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
                Vec3d p = s.getPosition();
                if (s.isDead() || pos.distanceSqToCenter(p.x, p.y, p.z) > REACH * REACH) continue;
                if (s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null && Math.abs(m.getCurrentSpeed().metric()) > 0.5) continue;
                FleetData.Unit u = f.unit(s.getUUID());
                if (!u.passenger) continue;
                boolean any = false;
                if ((ctl.get("on_water") == 1 || auto && u.water < 90) && u.water < 100 && waterMain()) { u.water = Math.min(100, u.water + 5); any = true; doing = "WATERING"; }
                if ((ctl.get("on_cetp") == 1 || auto && u.waste > 10) && u.waste > 0 && cet < 100) { float t = Math.min(u.waste, 5); u.waste -= t; cet = Math.min(100, cet + Math.max(1, Math.round(t / 4))); any = true; doing = "CET PUMPING"; }
                if ((ctl.get("on_clean") == 1 || auto && u.interior < 90) && u.interior < 100) { u.interior = Math.min(100, u.interior + 3); any = true; doing = "CLEANING"; if (age % 400 == 0) bill(u, 150, "Interior clean"); }
                if ((ctl.get("on_cater") == 1 || auto && u.catering < 70) && u.catering <= 90 && takeFood()) { u.catering = Math.min(100, u.catering + 10); any = true; doing = "CATERING"; }
                if (any && Servicing.need(u) <= 10) {
                    servicedToday++;
                    u.note(world, "serviced: " + Servicing.state(u));
                    Jobs.machineDone(world, s.getUUID(), Jobs.SERVICE, "servicing point");
                    Recorder.event(world, "Servicing point: " + u.name + " serviced");
                }
                if (any) { ctl.set("cur", Servicing.need(u)); text("unit", cut(u.name.toUpperCase(Locale.ROOT), 18)); f.markDirty(); }
            }
        }
        put("pwr", powered()); put("mainsl", mains.ok()); put("main", waterMain());
        for (String s : new String[]{"water", "cetp", "clean", "cater"}) put("l_" + s, ctl.get("on_" + s) == 1 || auto && doing.startsWith(s.equals("cetp") ? "CET" : s.toUpperCase(Locale.ROOT).substring(0, 4)));
        put("cetg", cet); put("full", cet >= 100); put("foodg", Math.min(100, food() * 100 / 64)); put("needg", ctl.get("cur"));
        put("ntoday", servicedToday);
        text("line1", doing.isEmpty() ? (auto ? "AUTO - WAITING" : "IDLE") : doing);
        if (doing.isEmpty()) text("unit", "");
    }

    private void bill(FleetData.Unit u, long cents, String what) {
        if (!com.dogpound.railmap.RailMapConfig.railwayEconomy) return;
        CompanyData cd = CompanyData.get(world);
        for (Company c : cd.all()) if (c.name != null && u.retLine != null && cd.ownerOfLine(u.retLine) == c) { cd.debit(world, c, Company.Kind.CLEANING, cents, what + ": " + u.name, false); return; }
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setInteger("cet", cet); t.setInteger("today", servicedToday); t.setLong("day", day); }
    @Override protected void loadExtra(NBTTagCompound t) { cet = t.getInteger("cet"); servicedToday = t.getInteger("today"); day = t.getLong("day"); }
}
