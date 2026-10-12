package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Train Wash Plant (her ChatGPT list #83): a drive-through gantry with pre-wet arch, four rotating brushes and a rinse
 * arch. With the plant RUNNING, a vehicle that rolls through under it at walking pace (1-8 km/h; 1-4 km/h for the
 * UNDERFRAME program) is washed - its exterior back to 100 % (80 % on plain water, without detergent in the dosing
 * tank). Water comes from the 10 000 L tank, topped up through the WATER MAIN valve from water beside the plant (or a
 * bucket on FILL); RECYCLE reuses 70 % of it. The signal on the gantry shows the driver GO / TOO FAST / STOP. Each
 * vehicle washed is billed to its operator's cleaning account.
 */
public class TileTrainWash extends DepotTile {
    private static final int COST = 300;     // cents per vehicle
    private int water = 6000, washedToday, washedTotal;
    private long day;
    private final Map<UUID, Long> washed = new HashMap<>();
    private boolean tooFast, busy;

    public TileTrainWash() { super(new String[]{"chem"}, new String[]{"Detergent dosing tank (slime balls)"}, new int[]{9}); }

    @Override public String wiredName() { return "Train wash plant"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return running() ? (busy ? 45 : 10) : 1; }
    boolean running() { return ctl.get("run") == 1 && ctl.get("m_estop") == 0 && powered() && mainsOk(); }
    @Override public String gearState() { return !powered() ? "NO 12 V" : (running() ? "RUNNING" : "stopped") + " · water " + water + " L · " + washedToday + " washed today"; }

    int prog() { return Math.floorMod(ctl.get("s_prog"), 3); }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "start" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (!mainsOk()) return "§cNo mains for the brushes (" + mains.what + ")";
                if (water < 200) return "§cNot enough water in the tank";
                ctl.set("run", 1);
                return "§aWash plant running - bring the train through at walking pace";
            }
            case "stop" -> { ctl.set("run", 0); return "§eWash plant stopped"; }
            case "fill" -> {
                if (who == null || who.getHeldItemMainhand().getItem() != Items.WATER_BUCKET) return "§7Hold a water bucket (or open the WATER MAIN beside water)";
                water = Math.min(10000, water + 1000);
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                return "§aTank " + water + " L";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); if (ctl.get(id) == 1) ctl.set("run", 0); return ctl.get(id) == 1 ? "§cE-STOP - brushes stopped" : "§aE-stop released"; }
        return null;
    }

    private boolean waterBeside() {
        for (EnumFacing f : EnumFacing.values()) if (world.getBlockState(pos.offset(f)).getMaterial() == Material.WATER) return true;
        return false;
    }

    private int chem() { int n = 0; for (int i = 0; i < store(0).getSizeInventory(); i++) if (store(0).getStackInSlot(i).getItem() == Items.SLIME_BALL) n += store(0).getStackInSlot(i).getCount(); return n; }

    private void useChem() {
        for (int i = 0; i < store(0).getSizeInventory(); i++) {
            ItemStack s = store(0).getStackInSlot(i);
            if (s.getItem() == Items.SLIME_BALL) { s.shrink(1); if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY); return; }
        }
    }

    @Override
    protected void work() {
        if (age % 10 != 0) return;
        long d = world.getTotalWorldTime() / 24000;
        if (d != day) { day = d; washedToday = 0; }
        if (ctl.get("s_main") == 1 && water < 10000 && waterBeside() && age % 20 == 0) water = Math.min(10000, water + 200);
        busy = false; tooFast = false;
        if (running()) {
            double max = prog() == 1 ? 4 : 8;
            for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
                Vec3d p = s.getPosition();
                if (s.isDead() || Math.abs(p.y - pos.getY()) > 4 || pos.distanceSqToCenter(p.x, pos.getY() + 0.5, p.z) > 9) continue;
                double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
                if (kmh > max) { tooFast = true; continue; }
                busy = true;
                if (kmh < 1 && ctl.get("s_prog") != 2) continue;        // has to move through (ROOF program: may stand)
                FleetData f = FleetData.get(world);
                FleetData.Unit u = f.unit(s.getUUID());
                int use = ctl.get("s_recycle") == 1 ? 30 : 100;
                if (water < use) { ctl.set("run", 0); break; }
                water -= use;
                boolean det = chem() > 0;
                float target = det ? 100 : 80;
                u.exterior = Math.min(target, u.exterior + 35);
                Long last = washed.get(s.getUUID());
                if (u.exterior >= target - 0.1 && (last == null || world.getTotalWorldTime() - last > 2400)) {
                    washed.put(s.getUUID(), world.getTotalWorldTime());
                    washedToday++; washedTotal++;
                    if (det) useChem();
                    u.note(world, "washed" + (det ? "" : " (water only)"));
                    Jobs.machineDone(world, s.getUUID(), Jobs.SERVICE, "train wash");
                    bill(s.getUUID(), u.name);
                }
                f.markDirty();
            }
            // brushes + spray: client LoopSounds plays machine.wash while the "busy" lamp is lit
        }
        put("pwr", powered()); put("mainsl", mains.ok()); put("runl", running()); put("busy", busy);
        put("go", running() && !tooFast); put("stopl", !running() || tooFast); put("fast", tooFast);
        put("loww", water < 1000); put("nochem", chem() == 0);
        put("waterg", water / 100); put("chemg", Math.min(100, chem() * 100 / 64));
        put("ntoday", washedToday);
        text("line1", !running() ? "STOPPED" : tooFast ? "TOO FAST - SLOW" : busy ? "WASHING" : "RUNNING - COME IN");
        text("line2", new String[]{"EXTERIOR", "+UNDERFRAME", "+ROOF"}[prog()] + (ctl.get("s_recycle") == 1 ? " RECYCLE" : ""));
    }

    private void bill(UUID id, String name) {
        if (!com.dogpound.railmap.RailMapConfig.railwayEconomy) return;
        CompanyData cd = CompanyData.get(world);
        Company c = cd.ownerOfTrain(id, "");
        if (c != null) cd.debit(world, c, Company.Kind.CLEANING, COST, "Train wash: " + name, false);
        Recorder.event(world, "Train wash: " + name);
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setInteger("water", water); t.setInteger("today", washedToday); t.setInteger("total", washedTotal); t.setLong("day", day); }
    @Override protected void loadExtra(NBTTagCompound t) { water = t.hasKey("water") ? t.getInteger("water") : 6000; washedToday = t.getInteger("today"); washedTotal = t.getInteger("total"); day = t.getLong("day"); }
}
