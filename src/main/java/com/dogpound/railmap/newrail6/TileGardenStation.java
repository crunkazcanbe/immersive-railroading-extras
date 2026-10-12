package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Garden railway station (batch 6 #19): a little platform with the station master's pedestal and the starter signal.
 * The train stops at mid-platform on the track beside it (within 2 blocks) while the starter shows red. MANUAL: the
 * DISPATCH button clears the starter (bell rings, away it goes); AUTO: it clears itself after the DWELL time. HOLD keeps
 * it red; LINE SPEED is what a driverless train runs at once dispatched; E-STOP stops every garden train within 64 blocks.
 * 12 V for the controls and signal, 120 / 240 V AC for the platform lights and the bell. Pages: OVERVIEW / TRAINS / TIMES
 * / EVENTS (lap times are timed from this station).
 */
public class TileGardenStation extends NR6Tile {
    public static final int[] DWELL = {10, 20, 30, 60};
    public static final double[] LINE = {1, 1.5, 2, 3};
    static final String[] PAGES = {"OVERVIEW", "TRAINS", "TIMES", "EVENTS"};

    /** the train standing here (entity id), when it stopped, green until */
    private int standing = -1;
    private long standSince, greenUntil;
    private final Map<Integer, Long> lastArrive = new HashMap<>();
    private double lastLap, bestLap;
    private int departures;

    public TileGardenStation() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Garden station " + pos.getX() + "," + pos.getZ(); }

    boolean auto() { return ctl.get("s_mode") == 1; }
    int dwell() { return DWELL[Math.max(0, Math.min(DWELL.length - 1, ctl.get("s_dwell")))]; }
    double lineSpeed() { return LINE[Math.max(0, Math.min(LINE.length - 1, ctl.get("s_vline")))]; }

    /** the starter at red for this train (no 12 V = red, fail-safe) */
    boolean red(EntityGardenTrain t) {
        if (!powered() || ctl.get("m_estop") == 1 || ctl.get("x_hold") == 1) return true;
        return world.getTotalWorldTime() >= greenUntil;
    }

    void arrived(EntityGardenTrain t) {
        standing = t.getEntityId();
        standSince = world.getTotalWorldTime();
        Long last = lastArrive.put(t.getEntityId(), standSince);
        if (last != null) {
            lastLap = (standSince - last) / 20.0;
            if (bestLap == 0 || lastLap < bestLap) bestLap = lastLap;
            event(String.format(Locale.ROOT, "%s IN  LAP %.1f S", t.name(), lastLap));
        } else event(t.name() + " IN");
    }

    void departed(EntityGardenTrain t) {
        if (standing == t.getEntityId()) standing = -1;
        t.lineSpeed = lineSpeed();
        departures++;
        event(t.name() + " AWAY");
    }

    void dispatch() {
        greenUntil = world.getTotalWorldTime() + 60;
        if (mains.ok) PackSounds.play(world, pos, "cab.bell", 1f, 1.6f);
    }

    static boolean estopNear(World w, BlockPos p) {
        for (TileGardenStation s : live(w, TileGardenStation.class)) if (s.ctl.get("m_estop") == 1 && s.pos.distanceSq(p) < 64 * 64) return true;
        return false;
    }

    List<EntityGardenTrain> trains() {
        return world.getEntitiesWithinAABB(EntityGardenTrain.class, new net.minecraft.util.math.AxisAlignedBB(pos).grow(64, 16, 64));
    }

    @Override
    public String gearState() { return (powered() ? auto() ? "AUTO" : "MANUAL" : "dark - no 12 V") + " · lights " + mains.what + (standing >= 0 ? " · train in" : ""); }

    @Override
    protected void serverTick() {
        if (age % 20 == 11) mainsTick(world.isDaytime() ? 60 : 450);
        long now = world.getTotalWorldTime();
        if (standing >= 0 && !(world.getEntityByID(standing) instanceof EntityGardenTrain)) standing = -1;
        if (auto() && standing >= 0 && powered() && ctl.get("x_hold") != 1 && now - standSince >= dwell() * 20L && now >= greenUntil && now - standSince < dwell() * 20L + 5) dispatch();
        if (age % 5 != 0) return;
        boolean on = powered();
        boolean green = on && now < greenUntil && ctl.get("x_hold") != 1 && ctl.get("m_estop") != 1;
        put("l_pwr", on);
        put("l_in", on && standing >= 0);
        put("sig_g", green);
        put("sig_r", on && !green);
        put("l_hold", on && ctl.get("x_hold") == 1);
        put("l_estop", on && ctl.get("m_estop") == 1);
        put("l_lights", on && mains.ok);
        put("l_auto", on && auto());
        List<EntityGardenTrain> ts = age % 20 == 0 ? trains() : null;
        if (ts != null) { trainCount = ts.size(); lines.clear(); for (EntityGardenTrain t : ts) lines.add(t.line()); fastest = 0; for (EntityGardenTrain t : ts) fastest = Math.max(fastest, t.speed()); }
        put("g_spd", (int) Math.min(100, fastest / NR6Config.gardenTopSpeed * 100));
        put("g_dwell", standing >= 0 && auto() ? (int) Math.min(100, (now - standSince) / (dwell() * 20.0) * 100) : 0);
        switch (page(PAGES.length)) {
            case 0 -> screen("GARDEN RAILWAY " + (auto() ? "AUTO" : "MANUAL"),
                    standing >= 0 ? "TRAIN IN PLATFORM" + (auto() ? " DEP " + Math.max(0, dwell() - (now - standSince) / 20) + " S" : "") : "PLATFORM CLEAR",
                    "STARTER " + (green ? "GREEN" : "RED") + (ctl.get("x_hold") == 1 ? " HOLD" : ""),
                    String.format(Locale.ROOT, "LINE SPEED %.1f M/S", lineSpeed()),
                    "TRAINS ON LINE " + trainCount,
                    "LIGHTS " + (mains.ok ? "ON" : "NO MAINS"));
            case 1 -> {
                String[] o = new String[6];
                o[0] = "TRAINS " + trainCount;
                for (int i = 1; i < 6; i++) o[i] = i - 1 < lines.size() ? lines.get(i - 1) : (i == 1 ? "NONE - USE A TRAIN SET" : "");
                screen(o);
            }
            case 2 -> screen("LAP TIMES",
                    lastLap == 0 ? "LAST LAP -" : String.format(Locale.ROOT, "LAST LAP %.1f S", lastLap),
                    bestLap == 0 ? "BEST LAP -" : String.format(Locale.ROOT, "BEST LAP %.1f S", bestLap),
                    "DEPARTURES " + departures,
                    "DWELL " + dwell() + " S",
                    "");
            default -> screen(logLines("EVENTS", 6));
        }
    }

    private int trainCount;
    private double fastest;
    private final List<String> lines = new ArrayList<>();

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.startsWith("s_") || id.startsWith("k_") || id.startsWith("m_")) return null;
        if (!powered()) return "§cStation pedestal dark - no 12 V";
        switch (id) {
            case "dispatch" -> {
                if (ctl.get("x_hold") == 1) return "§cHOLD is on";
                if (ctl.get("m_estop") == 1) return "§cE-STOP is in";
                dispatch();
                event("DISPATCHED");
                return "§aRight away! Starter off";
            }
            case "hold" -> {
                ctl.set("x_hold", ctl.get("x_hold") == 1 ? 0 : 1);
                event(ctl.get("x_hold") == 1 ? "HOLD ON" : "HOLD OFF");
                return ctl.get("x_hold") == 1 ? "§6Trains held at the platform" : "§aHold released";
            }
            case "bell" -> { PackSounds.play(world, pos, "cab.bell", 1f, 1.6f); return "§7Ding ding"; }
            case "reset" -> { lastLap = bestLap = 0; departures = 0; lastArrive.clear(); return "§7Lap times reset"; }
            default -> { return null; }
        }
    }
}
