package com.dogpound.railmap.traction;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.steam.Stock;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Hydrogen Dispenser (batch 5 #6): the 350 bar dispenser with breakaway hose and nozzle, -40 °C pre-cooler, mass meter,
 * gas detectors and the safety zone. CONNECT the nozzle to the hydrogen train standing at it (it locks on), START:
 * hydrogen flows from the Storage Tubes' cascade (LOW, then MEDIUM, then HIGH bank as the train's tank pressure rises)
 * at up to 1.2 kg/s with the PRE-COOL key on (0.4 kg/s without, the tank temperature limit), until the tank is full
 * (350 bar), 85 °C in the tank, or you STOP. The SAFETY ZONE around the train is watched: anyone (or anything) walking
 * into it during a fill sounds the zone alarm and pauses the fill. The hose wears: after 60 connections it starts to
 * leak at the nozzle (gas detector -> alarm at 20 % LEL, station ESD at 40 %) - fit a new hose (a Brake Hose spare in
 * the dispenser's spares drawer) with HOSE. E-STOP = station-wide ESD. Mains for the pre-cooler + 12 V.
 */
public class TileH2Dispenser extends com.dogpound.railmap.depot.DepotTile implements H2Station.Part {
    private UUID unit;
    private boolean filling, esd, zone;
    private float delivered, tankC = 15, lel, total;
    private int connects, bank = -1;

    public TileH2Dispenser() { super(new String[]{"spares"}, new String[]{"Dispenser spares (hoses, nozzle seals)"}, new int[]{9}); }

    @Override public String wiredName() { return "Hydrogen dispenser"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return precool() ? 180 : 4; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · %.1f kg this fill · hose %d/60%s", filling ? "FILLING" : unit != null ? "CONNECTED" : "idle", delivered, connects, esd ? " · ESD" : ""); }

    boolean precool() { return ctl.get("k_cool") == 1 && powered() && mainsOk(); }
    LocomotiveDiesel near() { return Stock.nearest(world, pos, 6, LocomotiveDiesel.class, l -> Traction.kind(l) == Traction.H2); }
    LocomotiveDiesel mine() { LocomotiveDiesel l = near(); return l != null && unit != null && l.getUUID().equals(unit) ? l : null; }

    @Override public void esdTrip(String why) { esd = true; filling = false; controlsChanged(); }
    @Override public boolean esd() { return esd; }
    @Override public float lel() { return lel; }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "nozzle" -> {
                if (unit != null) { if (filling) return "§cSTOP the fill first"; unit = null; snd(0.7f); return "§7Nozzle back in its holster"; }
                LocomotiveDiesel l = near();
                if (l == null) return "§cNo hydrogen train at the dispenser";
                if (!Stock.standing(l)) return "§cThe train must be standing";
                unit = l.getUUID(); connects++; snd(1.3f);
                return "§aNozzle locked on " + Stock.name(l);
            }
            case "start" -> {
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                if (esd) return "§cStation ESD - RESET first";
                LocomotiveDiesel l = mine();
                if (l == null) return "§cCONNECT the nozzle first";
                TileH2Storage st = H2Station.storage(world, pos, 16);
                if (st == null) return "§cNo Storage Tubes within 16 blocks";
                if (Traction.h2Room(l) < 0.1) return "§7Its tanks are full";
                filling = true; delivered = 0; tankC = 15;
                return "§aFilling" + (precool() ? " (pre-cooled)" : " - §eno pre-cool: slow fill");
            }
            case "stop" -> { boolean was = filling; filling = false; return was ? String.format(Locale.ROOT, "§eFill stopped - %.2f kg", delivered) : "§7Not filling"; }
            case "reset" -> {
                if (lel >= 10) return String.format(Locale.ROOT, "§cGas %.0f %% LEL at the dispenser", lel);
                esd = false; H2Station.reset(world, pos);
                return "§aDispenser reset";
            }
            case "hose" -> {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = store(0).getStackInSlot(i);
                    if (ItemSpare.is(s, ItemSpare.Spare.AIR_HOSE)) {
                        s.shrink(1); if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY);
                        connects = 0; lel = 0;
                        return "§aNew dispenser hose fitted";
                    }
                }
                return "§cNo spare hose in the spares drawer (a Brake Hose spare)";
            }
            default -> { return null; }
        }
    }

    void snd(float p) { world.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, p); }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) { if (ctl.get(id) == 0) { ctl.set(id, 1); H2Station.esd(world, pos, "E-STOP at the dispenser"); } else ctl.set(id, 0); return ctl.get(id) == 1 ? "§cESD - station shut down" : "§aE-stop released (RESET each part)"; }
        return null;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        LocomotiveDiesel l = unit == null ? near() : mine();
        if (unit != null && (l == null || !Stock.standing(l))) {
            AlarmManager.get(world).raise(world, "h2pull:" + pos.toLong(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MAJOR, "Hydrogen dispenser at " + pos.getX() + "," + pos.getZ() + ": BREAKAWAY - train moved with the nozzle on", pos, false);
            lel += 30; unit = null; filling = false;
        }
        // the safety zone: around the train being filled
        zone = false;
        if (filling && l != null) {
            cam72cam.mod.math.Vec3d v = l.getPosition();
            List<EntityLivingBase> in = world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(v.x - 2.5, v.y - 1, v.z - 2.5, v.x + 2.5, v.y + 3, v.z + 2.5), e -> e != null && !e.isRiding());
            zone = !in.isEmpty();
            String key = "h2zone:" + pos.toLong();
            if (zone) {
                AlarmManager.get(world).raise(world, key, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "Hydrogen dispenser: SAFETY ZONE ENTERED during a fill (" + in.get(0).getName() + ") - fill paused", pos, true);
                if (age % 40 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 1.0f, 2.0f);
            } else AlarmManager.get(world).clear(key);
        }
        if (filling) {
            TileH2Storage st = H2Station.storage(world, pos, 16);
            TractionData.Unit u = l == null ? null : Traction.unit(l);
            float tankBar = u == null ? 0 : (float) Traction.h2Bar(l, u);
            if (l == null || st == null || esd || !powered() || ctl.get("m_estop") == 1) filling = false;
            else if (!zone) {
                float rate = precool() ? 1.2f : 0.4f;
                float want = (float) Math.min(rate, Traction.h2Room(l));
                bank = st.bankFor(tankBar);
                float got = st.draw(want, tankBar);
                if (got > 0) Traction.addH2(l, got);
                delivered += got; total += got;
                tankC += got * (precool() ? 1.5f : 6f) - (tankC - 15) * 0.03f;
                if (age % 60 == 0) world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.BLOCKS, 0.5f, 1.6f);
                String end = Traction.h2Room(l) < 0.05 ? "tank FULL" : tankC >= 85 ? "tank temperature limit 85 °C" : got < 0.01f ? "storage pressure too low for the cascade" : null;
                if (end != null) {
                    filling = false;
                    Recorder.event(world, String.format(Locale.ROOT, "Hydrogen dispenser: %s %.1f kg - %s", Stock.name(l), delivered, end));
                    world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.5f, 1.0f);
                }
            }
        } else tankC += (15 - tankC) * 0.05f;
        if (connects > 60 && unit != null && world.rand.nextInt(30) == 0) lel += 8;    // worn hose weeps at the nozzle
        lel = Math.max(0, lel - lel * 0.12f);
        if (powered()) H2Station.detector(world, pos, "dispenser", lel);
        TractionData.Unit u = l == null ? null : Traction.unit(l);
        put("pwr", powered()); put("mainsl", mains.ok()); put("nozl", unit != null); put("fill", filling && !zone); put("zonel", zone); put("esdl", esd);
        put("cool", precool()); put("gas", lel >= 5); put("leakl", lel >= 20); put("hosel", connects > 60); put("locol", l != null);
        for (int b = 0; b < 3; b++) put("bk" + (b + 1), filling && bank == b);
        put("tankg", u == null ? 0 : Math.round(Math.max(0, u.h2kg) * 100 / (float) Traction.h2CapKg(l)));
        put("barg", u == null ? 0 : (int) Math.round(Traction.h2Bar(l, u) * 100 / 450));
        put("tempg", Math.round(tankC)); put("lelg", Math.round(lel));
        text("kg", String.format(Locale.ROOT, "%.2f", delivered));
        text("line1", l == null ? "NO H2 TRAIN" : cut(Stock.name(l).toUpperCase(Locale.ROOT), 20));
        text("line2", u == null ? "" : String.format(Locale.ROOT, "TANK %.0f BAR %.0f KG %.0f C", Traction.h2Bar(l, u), Math.max(0, u.h2kg), tankC));
        text("line3", zone ? "ZONE ENTERED - PAUSED" : filling ? (bank < 0 ? "NO BANK" : TileH2Storage.BANK[bank] + " BANK") : esd ? "ESD TRIPPED" : unit != null ? "CONNECTED" : "READY");
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        if (unit != null) { t.setLong("uh", unit.getMostSignificantBits()); t.setLong("ul", unit.getLeastSignificantBits()); }
        t.setBoolean("fill", filling); t.setBoolean("esd", esd); t.setFloat("del", delivered); t.setFloat("tc", tankC); t.setFloat("lel", lel); t.setFloat("tot", total); t.setInteger("con", connects);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        unit = t.hasKey("uh") ? new UUID(t.getLong("uh"), t.getLong("ul")) : null;
        filling = t.getBoolean("fill"); esd = t.getBoolean("esd"); delivered = t.getFloat("del"); tankC = t.hasKey("tc") ? t.getFloat("tc") : 15; lel = t.getFloat("lel"); total = t.getFloat("tot"); connects = t.getInteger("con");
    }
}
