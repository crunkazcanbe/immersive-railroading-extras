package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.entity.Tender;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.network.PacketCab;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * The steam locomotive's boiler and fire, once a second (batch 5 #2):
 * <ul>
 *   <li><b>scale</b>: every litre turned to steam leaves its hardness behind on the tubes and firebox plates. Scale
 *   insulates: each mm costs about 8 % more coal for the same steam (the fire's burn time is shortened, so the loco
 *   really burns more). At {@code washoutDueMm} the boiler is DUE A WASHOUT; at {@code tubeLeakMm} the overheated tube
 *   ends LEAK and the boiler loses water until it is washed out and the tubes re-expanded (Boiler Washout Plant).</li>
 *   <li><b>ash</b>: coal leaves about 10 % ash in the ash pan. A full pan chokes the fire's air (15 % more coal and
 *   falling steam) until the fire is cleaned and the pan dropped at an Ash Pit.</li>
 * </ul>
 * Faults show on the cab display and the alarm list like the diesel engine's.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class SteamTicker {
    private SteamTicker() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    /** mm of scale per litre per ppm: 0.5 mm per hour at 4.5 L/s on 250 ppm */
    static final double K_SCALE = 0.5 / (3600 * 4.5 * 250);
    /** kg of ash per burning firebox slot per second (5 kg per coal item, ~80 s each) */
    static final double ASH_PER_SLOT_S = 0.0625;
    private static final Map<UUID, Float> CARRY = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !IR || e.world.getTotalWorldTime() % 20 != 11) return;
        try { boilers(e.world); } catch (LinkageError | RuntimeException ex) { RailMap.LOG.debug("[IR Extras] steam tick failed: {}", ex.toString()); }
    }

    /** the fuel penalty of this boiler: 0..0.5 */
    public static float loss(SteamData.Boiler b) {
        float l = SteamConfig.boilerScale ? Math.min(0.35f, b.scaleMm * 0.08f) : 0;
        if (SteamConfig.ashPan && b.ashKg >= SteamConfig.ashPanKg) l += 0.15f;
        return Math.min(0.5f, l);
    }

    /** the hardness of the water this loco is boiling (its tender's, or its own tanks') */
    static float hardness(SteamData d, LocomotiveSteam l) {
        Tender t = Stock.tender(l);
        SteamData.Boiler tb = t == null ? null : d.peek(t.getUUID());
        if (tb != null && tb.ppm >= 0) return tb.ppm;
        SteamData.Boiler b = d.peek(l.getUUID());
        return b == null ? SteamConfig.defaultPpm : b.hardness();
    }

    static void boilers(World w) {
        SteamData d = SteamData.get(w);
        long now = w.getTotalWorldTime();
        boolean dirty = false;
        for (LocomotiveSteam l : cam72cam.mod.world.World.get(w).getEntities(LocomotiveSteam.class)) {
            if (l.isDead() || l.getDefinition() == null || !l.isBuilt()) continue;
            SteamData.Boiler b = d.of(l.getUUID());
            b.name = Stock.name(l);
            float bar = l.getBoilerPressureBar();
            int burning = 0;
            for (Integer v : l.getBurnTime().values()) if (v != null && v > 0) burning++;
            double gs = Math.max(0.1, l.gauge.scale());
            if (bar > 0.1f) {
                b.steamH += 1 / 3600.0;
                double litres = (0.5 + 8 * Math.abs(l.getThrottle() * l.getReverser())) * gs;
                if (SteamConfig.boilerScale) b.scaleMm += (float) (litres * hardness(d, l) * K_SCALE * SteamConfig.scaleRate);
                dirty = true;
            }
            if (SteamConfig.ashPan && burning > 0) { b.ashKg = Math.min(SteamConfig.ashPanKg * 1.25f, b.ashKg + (float) (burning * ASH_PER_SLOT_S * gs)); dirty = true; }
            // the penalty: the fire burns through its coal faster for the same heat
            float loss = loss(b);
            if (loss > 0 && burning > 0) {
                float extra = 20f * loss / (1 - loss) + CARRY.getOrDefault(l.getUUID(), 0f);
                int take = (int) extra;
                CARRY.put(l.getUUID(), extra - take);
                if (take > 0) for (Map.Entry<Integer, Integer> en : l.getBurnTime().entrySet()) if (en.getValue() != null && en.getValue() > 0) en.setValue(Math.max(0, en.getValue() - take));
            }
            // tube leaks: the boiler loses water through the leaking tube ends
            if (SteamConfig.tubeLeakMm > 0 && b.scaleMm >= SteamConfig.tubeLeakMm && !b.leak) {
                b.leak = true;
                d.note(w, b.name + ": TUBES LEAKING at " + String.format(Locale.ROOT, "%.1f", b.scaleMm) + " mm scale");
            }
            if (b.leak && bar > 0.1f && l.theTank.getContents() != null && l.theTank.getContents().getAmount() > 0)
                l.theTank.drain(new cam72cam.mod.fluid.FluidStack(l.theTank.getContents().getFluid(), (int) Math.round(30 * gs)), false);
            faults(w, l, b, now);
        }
        if (dirty && now % 600 < 20) d.markDirty();
    }

    private static void faults(World w, LocomotiveSteam l, SteamData.Boiler b, long now) {
        SimData.Car c = SimData.get(w).of(l.getUUID());
        set(w, l, c, now, "B101", SteamConfig.boilerScale && b.scaleMm >= SteamConfig.washoutDueMm, String.format(Locale.ROOT, "BOILER SCALE %.1f MM - WASHOUT DUE", b.scaleMm), AlarmManager.Sev.MINOR);
        set(w, l, c, now, "B102", b.leak, "BOILER TUBES LEAKING - WASHOUT + RE-EXPAND", AlarmManager.Sev.MAJOR);
        set(w, l, c, now, "B201", SteamConfig.ashPan && b.ashKg >= SteamConfig.ashPanKg, "ASH PAN FULL - FIRE CHOKED, DROP IT AT AN ASH PIT", AlarmManager.Sev.MINOR);
    }

    private static void set(World w, LocomotiveSteam l, SimData.Car c, long now, String code, boolean on, String text, AlarmManager.Sev sev) {
        String key = "steam:" + l.getUUID() + ":" + code;
        if (on && !c.hasFault(code)) {
            c.fault(code, text, Recorder.clock(w, now));
            SimData.get(w).markDirty();
            Vec3d p = l.getPosition();
            AlarmManager.get(w).raise(w, key, AlarmManager.Cat.MACHINE, sev, Stock.name(l) + " " + code + " " + text, new BlockPos(p.x, p.y, p.z), false);
        } else if (!on && c.hasFault(code)) {
            c.faults.removeIf(f -> f.startsWith(code + " "));
            SimData.get(w).markDirty();
            AlarmManager.get(w).clear(key);
        }
    }

    /** a washout: the plant took `fraction` of the scale off, the tubes were re-expanded */
    public static void washed(World w, LocomotiveSteam l, float fraction, String who) {
        SteamData d = SteamData.get(w);
        SteamData.Boiler b = d.of(l.getUUID());
        float before = b.scaleMm;
        b.scaleMm = Math.max(0, b.scaleMm * (1 - fraction));
        b.leak = false;
        b.steamH = 0;
        b.washedAt = w.getTotalWorldTime();
        b.washouts++;
        d.note(w, String.format(Locale.ROOT, "%s WASHED OUT by %s: scale %.2f -> %.2f mm", Stock.name(l), who, before, b.scaleMm));
        Recorder.event(w, String.format(Locale.ROOT, "Boiler washout: %s, scale %.2f -> %.2f mm", Stock.name(l), before, b.scaleMm));
        faults(w, l, b, w.getTotalWorldTime());
        d.markDirty();
    }

    // ---- the cab display: boiler lines for steam locos ------------------------------------------------------------------
    public static void cab(cam72cam.immersiverailroading.entity.Locomotive loco, PacketCab p) {
        if (!(loco instanceof LocomotiveSteam l)) return;
        World w = loco.getWorld().internal;
        SteamData.Boiler b = SteamData.get(w).peek(l.getUUID());
        int burning = 0, slots = Math.max(0, l.cargoItems.getSlotCount() - 2);
        for (Integer v : l.getBurnTime().values()) if (v != null && v > 0) burning++;
        p.eng1 = String.format(Locale.ROOT, "BOILER %.1f bar %d °C · water %d%% · fire %d/%d%s", l.getBoilerPressureBar(), Math.round(l.getBoilerTemperature()),
                l.getPercentLiquidFull(), burning, slots, b != null && b.leak ? " · TUBES LEAKING" : "");
        if (b == null) { p.eng2 = "boiler clean · ash pan empty"; return; }
        p.eng2 = String.format(Locale.ROOT, "scale %.2f mm (+%d%% coal) · %d ppm · ash %d%% · %.1f h since washout", b.scaleMm, Math.round(loss(b) * 100),
                Math.round(hardness(SteamData.get(w), l)), Math.round(b.ashKg * 100 / SteamConfig.ashPanKg), b.steamH);
    }

}
