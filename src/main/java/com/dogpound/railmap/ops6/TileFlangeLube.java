package com.dogpound.railmap.ops6;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.track.Rails;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * Flange Lubricator (batch 6 E #23): a trackside greaser - 40 kg grease tank, an electric gear pump on 24 V DC or
 * 120 / 240 V AC, a magnetic wheel detector on the rail and gauge-face wiping bars. In AUTO each axle that passes gets a
 * dose (LOW 1 g, MED 2 g, HIGH 4 g) and the wheels carry the grease along the rails (Ops6Config.lubeRange). The film
 * builds with doses, is carried off by traffic and washes off in the rain; it cuts rail side wear on curves (TrackCondition)
 * and wheel flange wear (Wear) and stops the flange squeal. Too much (over 120 %) is OVER-GREASED: slip risk, alarmed.
 * Grease = slime balls (FILL with them in hand: 1 kg each). PRIME runs a 10 g stroke by hand.
 */
public class TileFlangeLube extends InfraTile implements PacketOps6.Screen {
    static final float TANK_KG = 40f;
    static final float[] DOSE_G = {1, 2, 4};
    static final String[] DOSE = {"LOW", "MED", "HIGH"};
    float greaseKg = 0, film;
    int axles, pumpT, detT;
    double usedG;
    long lastCarAt;
    private final Map<UUID, Long> seen = new HashMap<>();
    private final Deque<String> log = new ArrayDeque<>();

    public TileFlangeLube() { super(new Supply(Elec.Level.DC24, Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Flange lubricator " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "dark - no 12 V" : !live() ? "pump dead: " + mains.what : String.format(Locale.ROOT, "%s %s · grease %.1f kg · film %d %% · %d axles", auto() ? "AUTO" : "OFF", DOSE[dose()], greaseKg, Math.round(film * 100), axles); }
    @Override public int wiredLamp() { return !live() ? 3 : greaseKg <= 0 || film > 1.2f ? 2 : 1; }

    boolean auto() { return ctl.get("k_on") == 1; }
    int dose() { return Math.floorMod(ctl.get("s_dose"), 3); }

    void note(String s) { log.addFirst(com.dogpound.railmap.depot.DepotClock.hhmm(world) + " " + s); while (log.size() > 12) log.removeLast(); }

    /** one pump stroke of g grams onto the rail */
    boolean pump(double g) {
        if (!live() || greaseKg <= 0) return false;
        double kg = Math.min(greaseKg, g / 1000);
        greaseKg -= kg;
        usedG += kg * 1000;
        film = (float) Math.min(1.6, film + kg * 1000 / 140.0);
        pumpT = 30;
        return true;
    }

    @Override
    protected void serverTick() {
        if (pumpT > 0) pumpT--;
        if (detT > 0) detT--;
        if (age % 20 != 3) return;
        mainsTick(pumpT > 0 ? 180 : 6);
        long now = world.getTotalWorldTime();
        boolean wet = world.isRainingAt(pos.up(2));
        // the wheel detector: each car that runs past (within 3 blocks, moving) - one dose per axle in AUTO
        try {
            for (EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityMoveableRollingStock.class)) {
                if (s.isDead()) continue;
                cam72cam.mod.math.Vec3d p = s.getPosition();
                double dx = p.x - pos.getX() - 0.5, dz = p.z - pos.getZ() - 0.5;
                if (dx * dx + dz * dz > 9 || Math.abs(p.y - pos.getY()) > 3) continue;
                if (s.getCurrentSpeed() == null || Math.abs(s.getCurrentSpeed().metric()) < 1) continue;
                Long was = seen.put(s.getUUID(), now);
                if (was != null && now - was < 100) continue;
                int n = Rails.axles(s);
                axles += n;
                detT = 10;
                lastCarAt = now;
                film = Math.max(0, film - film * 0.002f * n);                 // the wheels carry some of it away
                if (auto() && live()) {
                    if (greaseKg > 0) pump(n * DOSE_G[dose()]);
                }
            }
        } catch (LinkageError | RuntimeException ignored) { }
        seen.values().removeIf(t -> now - t > 400);
        film = Math.max(0, film * (wet ? 0.997f : 0.9996f));             // rain washes it off in minutes, dry it lasts hours
        Lubricators.post(world, pos, film);
        String k = "lube:" + pos.toLong();
        Alarms6.set(world, k + ":empty", live() && auto() && greaseKg <= 0, AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "FLANGE LUBRICATOR " + pos.getX() + "," + pos.getZ() + " EMPTY - refill with grease", pos);
        if (Alarms6.set(world, k + ":over", film > 1.2f, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR, "OVER-GREASED RAIL at " + pos.getX() + "," + pos.getZ() + " (film " + Math.round(film * 100) + " %) - wheelslip risk, turn the dose down", pos)) note("OVER-GREASED");
        boolean on = powered();
        put("pwr", on); put("sup", mains.ok); put("pumpl", on && pumpT > 0); put("auto", on && auto());
        put("low", on && greaseKg < TANK_KG * 0.15f); put("empty", on && greaseKg <= 0); put("filmok", on && film >= 0.3f && film <= 1.2f);
        put("over", on && film > 1.2f); put("det", on && detT > 0); put("wet", on && wet);
        put("tankg", Math.round(greaseKg / TANK_KG * 100)); put("filmg", Math.round(Math.min(150, film * 100)));
        text("axles", String.valueOf(axles));
        text("used", String.format(Locale.ROOT, "%.0f", usedG));
        text("line", String.format(Locale.ROOT, "FILM %d%% %s %s", Math.round(film * 100), DOSE[dose()], wet ? "RAIN" : "DRY"));
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        switch (id) {
            case "test": return lampTest();
            case "screen": PacketOps6.open(p, this, 0); return "";
            case "fill": return fill(p);
            case "prime":
                if (!powered()) return "§cNo 12 V";
                if (!live()) return "§cPump dead: " + mains.what;
                if (!pump(10)) return "§cThe tank is empty";
                PackSounds.play(world, pos, "machine.pump", 0.6f, 1.3f);
                note("PRIME 10 G by " + who(p));
                return "§aPump primed: 10 g on the rail";
            case "reset": axles = 0; usedG = 0; note("COUNTERS RESET"); return "§7Counters reset";
            default: return null;
        }
    }

    String fill(EntityPlayer p) {
        if (p == null) return "§7Grease is filled by hand";
        ItemStack h = p.getHeldItemMainhand();
        if (h.getItem() != Items.SLIME_BALL) return "§7Hold slime balls (grease) to fill the tank";
        int room = (int) Math.floor(TANK_KG - greaseKg);
        if (room <= 0) return "§eThe tank is full";
        int n = Math.min(room, h.getCount());
        if (!p.isCreative()) h.shrink(n);
        greaseKg += n;
        note("FILLED " + n + " KG");
        Recorder.event(world, "Flange lubricator " + pos.getX() + "," + pos.getZ() + " refilled +" + n + " kg by " + p.getName());
        controlsChanged();
        return String.format(Locale.ROOT, "§aTank %.0f / %.0f kg", greaseKg, TANK_KG);
    }

    @Override
    public net.minecraft.nbt.NBTTagCompound screen(EntityPlayer p, int page) {
        Scr s = new Scr("Flange Lubricator " + pos.getX() + "," + pos.getZ(), live() ? "§aON LINE" : "§c" + (powered() ? "NO SUPPLY" : "NO 12 V"), page, "STATUS", "SETTINGS", "LOG");
        switch (page) {
            case 1 -> {
                s.line("§7Dose per axle: §f" + DOSE[dose()] + " (" + (int) DOSE_G[dose()] + " g)  §7Mode: §f" + (auto() ? "AUTO" : "OFF"));
                s.line("§7Carry range: §f" + Ops6Config.lubeRange + " blocks each way");
                s.line("§7Effect: §frail side wear on curves -" + Math.round(70 * Math.min(1, film)) + " %, flange wear -" + Math.round(50 * Math.min(1, film)) + " %");
                s.line("§7Supply: §f" + mains.what + "  §7(built for " + mains.built() + ")");
                for (int i = 0; i < 3; i++) s.button("dose" + i, "DOSE " + DOSE[i], i == dose() ? Scr.GREEN : Scr.GREY);
                s.button("auto", auto() ? "AUTO -> OFF" : "OFF -> AUTO", auto() ? Scr.GREEN : Scr.AMBER);
            }
            case 2 -> { for (String l : log) s.line("§7" + l); if (log.isEmpty()) s.line("§8(no events yet)"); }
            default -> {
                s.bar("GREASE TANK", greaseKg, TANK_KG, greaseKg < TANK_KG * 0.15f ? 0xC03020 : 0x30A050, String.format(Locale.ROOT, "%.1f / %.0f kg", greaseKg, TANK_KG));
                s.bar("RAIL FILM", Math.min(1.5, film), 1.5, film > 1.2f ? 0xC03020 : film >= 0.3f ? 0x30A050 : 0xC09020, Math.round(film * 100) + " %" + (film > 1.2f ? " OVER-GREASED" : film < 0.3f ? " THIN" : " OK"));
                s.line(String.format(Locale.ROOT, "§7Axles detected: §f%d  §7grease used: §f%.0f g", axles, usedG));
                s.line("§7Last train: §f" + (lastCarAt == 0 ? "none yet" : ((world.getTotalWorldTime() - lastCarAt) / 20) + " s ago"));
                s.line("§7Rail: §f" + (world.isRainingAt(pos.up(2)) ? "WET - film washing off" : "dry"));
                s.button("prime", "PRIME 10 G", Scr.BLUE).button("fill", "FILL (slime balls)", Scr.GREEN).button("reset", "RESET COUNTERS", Scr.GREY);
            }
        }
        return s.build();
    }

    @Override
    public String screenAction(EntityPlayer p, int page, String a) {
        if (a.startsWith("dose")) { ctl.set("s_dose", a.charAt(4) - '0'); controlsChanged(); return "§eDose " + DOSE[dose()]; }
        if (a.equals("auto")) { ctl.set("k_on", auto() ? 0 : 1); controlsChanged(); note(auto() ? "AUTO ON" : "AUTO OFF"); return auto() ? "§aAUTO" : "§eOFF"; }
        return com.dogpound.railmap.controls.IControlled.operate(this, a, p);
    }

    @Override public void invalidate() { super.invalidate(); if (world != null) Lubricators.gone(world, pos); }

    @Override protected void store(NBTTagCompound t) { t.setFloat("kg", greaseKg); t.setFloat("film", film); t.setInteger("axles", axles); t.setDouble("used", usedG); t.setLong("lastCar", lastCarAt); }
    @Override protected void restore(NBTTagCompound t) { greaseKg = t.getFloat("kg"); film = t.getFloat("film"); axles = t.getInteger("axles"); usedG = t.getDouble("used"); lastCarAt = t.getLong("lastCar"); }
}
