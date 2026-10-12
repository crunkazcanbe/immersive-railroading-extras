package com.dogpound.railmap.freight;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.biz.BizData;
import com.dogpound.railmap.biz.Custody;
import com.dogpound.railmap.item.ItemContainer;
import com.dogpound.railmap.track.Rails;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.Locale;
import java.util.UUID;

/**
 * Terminal Reefer Plug Post (ChatGPT batch 3 #21): a yard pillar with four 400 V reefer sockets and a trackside shore
 * supply. Containers in the socket RACK (one per socket) are plugged in: with the socket breaker ON, 12 V for the
 * controller and mains for the compressors, each holds its box at the setpoint and the box's cold-chain record is
 * kept up to date (Custody). CAR SUPPLY ON feeds the reefer wagon standing within 8 blocks (SHORE power - its genset
 * rests). APPLY sets the SETPOINT selector on every unit; PTI runs a pre-trip inspection of the wagon; FUEL tops its
 * genset up from the fuel bin (coal 40 L, charcoal 30 L, coal block 360 L); ACK silences the alarm buzzer.
 */
public class TileReeferPlug extends CargoTile {
    static final double[] SETPOINTS = {-25, -18, -5, 0, 3, 5, 8, 13};
    private UUID car;
    private String carName = "";
    private int active;
    private long buzzAt;

    public TileReeferPlug() { super(new String[]{"rack", "fuelbin"}, new String[]{"Reefer socket rack (1 container per socket)", "Genset fuel bin"}, new int[]{4, 9}); }

    @Override public String wiredName() { return "Reefer plug post"; }
    @Override protected long load() { return active * 4L + (car != null ? 6 : 0); }
    @Override public String gearState() { return !powered() ? "NO 12 V" : active + " socket(s) live" + (car != null ? " · car " + carName : "") + (mainsOk() ? "" : " · " + mains.what); }

    double setpoint() { return SETPOINTS[Math.floorMod(ctl.get("s_set"), SETPOINTS.length)]; }

    @Override
    protected String act(String id, EntityPlayer who) {
        Reefer r = Reefer.get(world);
        switch (id) {
            case "apply" -> {
                int n = 0;
                for (int i = 0; i < 4; i++) { ItemStack b = store(0).getStackInSlot(i); if (ItemContainer.is(b)) { r.unit("box:" + ItemContainer.id(b), ItemContainer.pretty(b)).set = setpoint(); n++; } }
                if (car != null) { Reefer.Unit u = r.units.get(Reefer.carKey(car)); if (u != null) { u.set = setpoint(); n++; } }
                return n == 0 ? "§7Nothing plugged in" : String.format(Locale.ROOT, "§aSetpoint %+.0f C sent to %d unit(s)", setpoint(), n);
            }
            case "pti" -> {
                Reefer.Unit u = car == null ? null : r.units.get(Reefer.carKey(car));
                if (u == null) return "§7No reefer wagon on the car supply";
                StringBuilder f = new StringBuilder();
                if (Math.abs(u.temp - u.set) > 2) f.append(String.format(Locale.ROOT, " box %.1f C not at setpoint %.0f;", u.temp, u.set));
                if (u.fuel < CargoConfig.gensetTankL * 0.25) f.append(String.format(Locale.ROOT, " genset fuel low (%.0f L);", u.fuel));
                if (u.food && u.quality < 95) f.append(String.format(Locale.ROOT, " cargo already at %.0f%%;", u.quality));
                if (u.alarm) f.append(" alarm standing;");
                u.pti = f.length() == 0;
                Recorder.event(world, "Reefer PTI " + u.name + ": " + (u.pti ? "PASS" : "FAIL -" + f));
                world.playSound(null, pos, u.pti ? ModSounds.BEEP_OK : ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.7f, 1f);
                return u.pti ? "§aPTI PASS - " + u.name + " fit to load" : "§cPTI FAIL:" + f;
            }
            case "fuel" -> {
                Reefer.Unit u = car == null ? null : r.units.get(Reefer.carKey(car));
                if (u == null) return "§7No reefer wagon on the car supply";
                double before = u.fuel;
                for (int i = 0; i < store(1).getSizeInventory() && u.fuel < CargoConfig.gensetTankL - 30; i++) {
                    ItemStack s = store(1).getStackInSlot(i);
                    double l = litres(s);
                    while (!s.isEmpty() && l > 0 && u.fuel + l <= CargoConfig.gensetTankL + 1) { u.fuel = Math.min(CargoConfig.gensetTankL, u.fuel + l); s.shrink(1); }
                    if (s.isEmpty()) store(1).setInventorySlotContents(i, ItemStack.EMPTY);
                }
                r.markDirty();
                if (u.fuel <= before) return "§7Fuel bin empty (coal, charcoal, coal blocks) or the tank is full";
                com.dogpound.railmap.sound.PackSounds.play(world, pos, "machine.fuel_pump", 0.7f, 1f);
                return String.format(Locale.ROOT, "§aGenset fuelled: %.0f L (+%.0f)", u.fuel, u.fuel - before);
            }
            case "ack" -> {
                int n = 0;
                for (Reefer.Unit u : r.units.values()) if (u.alarm && !u.acked && u.at.distanceSq(pos) < 32 * 32) { u.acked = true; n++; }
                return n == 0 ? "§7No alarms to acknowledge" : "§eAcknowledged " + n + " reefer alarm(s)";
            }
            default -> { return null; }
        }
    }

    static double litres(ItemStack s) {
        if (s.isEmpty()) return 0;
        Item i = s.getItem();
        if (i == Items.COAL) return s.getMetadata() == 1 ? 30 : 40;
        if (i == Item.getItemFromBlock(Blocks.COAL_BLOCK)) return 360;
        return 0;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        Reefer r = Reefer.get(world);
        long now = world.getTotalWorldTime();
        double amb = Reefer.ambient(world, pos);
        boolean live = live(), alarm = false;
        active = 0;
        Custody cu = BizData.get(world).custody;
        for (int i = 0; i < 4; i++) {
            ItemStack b = store(0).getStackInSlot(i);
            String n = String.valueOf(i + 1);
            boolean box = ItemContainer.is(b), on = box && ctl.get("k_s" + n) == 1 && live;
            if (!box) { put("on" + n, false); put("al" + n, false); text("t" + n, "- EMPTY -"); continue; }
            Reefer.Unit u = r.unit("box:" + ItemContainer.id(b), ItemContainer.pretty(b));
            u.lastSeen = now; u.ambient = amb; u.at = pos;
            Custody.Rec rec = cu.box(b);
            boolean food = false;
            for (ItemStack g : ItemContainer.contents(b)) food |= Custody.isFood(g);
            if (food && !u.food) { u.quality = Math.min(100, rec.coldQ); u.temp = rec.tempC; }
            u.food = food;
            u.power = on ? "SHORE" : "OFF";
            if (on) active++;
            Reefer.step(u, on, 1);
            r.alarm(world, u, false);
            if (food) { rec.reefer = true; rec.coldQ = Math.min(rec.coldQ, u.quality); rec.tempC = u.temp; }
            alarm |= u.alarm && !u.acked;
            put("on" + n, on); put("al" + n, u.alarm);
            text("t" + n, String.format(Locale.ROOT, "%s %+.1f/%+.0f %s", ItemContainer.id(b).substring(0, 4), u.temp, u.set, food ? "Q" + Math.round(u.quality) : "MT"));
        }
        // the wagon on the trackside supply
        car = null; carName = "";
        if (ctl.get("k_car") == 1 && live) {
            double best = 64;
            for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
                if (s.isDead() || s instanceof Locomotive || !Reefer.isReefer(Rails.name(s))) continue;
                Vec3d p = s.getPosition();
                double d = pos.distanceSqToCenter(p.x, p.y, p.z);
                double kmh = s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
                if (d < best && kmh < 1) { best = d; car = s.getUUID(); carName = Rails.name(s); }
            }
            if (car != null) Reefer.shore(world, car, now);
        }
        Reefer.Unit cu2 = car == null ? null : r.units.get(Reefer.carKey(car));
        put("carl", cu2 != null); put("caral", cu2 != null && cu2.alarm); put("ptil", cu2 != null && cu2.pti);
        alarm |= cu2 != null && cu2.alarm && !cu2.acked;
        text("car", cu2 == null ? (ctl.get("k_car") == 1 ? "NO WAGON" : "CAR SUPPLY OFF") : cut(carName, 10) + " " + Reefer.fmt(cu2) + String.format(Locale.ROOT, " %.0fL", cu2.fuel));
        text("line1", !powered() ? "" : !mainsOk() ? "NO MAINS" : alarm ? "REEFER ALARM" : active + " SOCKETS LIVE");
        text("setp", String.format(Locale.ROOT, "SET %+.0f C", setpoint()));
        put("pwr", powered()); put("mainsl", mainsOk());
        put("load", (int) load());
        if (alarm && now - buzzAt > 100) { buzzAt = now; world.playSound(null, pos, ModSounds.BUZZER, SoundCategory.BLOCKS, 0.5f, 1.1f); }
    }

    @Override protected void saveExtra(NBTTagCompound t) { }
    @Override protected void loadExtra(NBTTagCompound t) { }
}
