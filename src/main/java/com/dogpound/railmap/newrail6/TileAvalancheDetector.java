package com.dogpound.railmap.newrail6;

import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3i;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import com.dogpound.railmap.weather.Weather;
import net.minecraft.block.BlockSnow;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Avalanche / landslide detector (batch 6 #20): a lineside mast with an ultrasonic snow-depth sensor, avalanche radar and a
 * geophone, a slide-detection fence and the control cabinet. It watches the line within its RANGE: snowpack on the slope
 * above, slope angle, air temperature -> the avalanche hazard (five-step scale); an avalanche or landslide reaching the
 * line (snow / rock / earth on a rail, falling blocks in the zone) TRIPS it. ARMED, it holds every signal in its zone at
 * danger (and stops driverless trains short) when tripped, when something lies on the line, on HOLD, at or above its
 * HOLD AT hazard level - and with no 12 V (fail-safe). LINE CLEAR (after the snow / rock has been cleared off the rails)
 * resets a trip. BLAST is a controlled release (a gas exploder on the slope) while the line is held: it brings the snow
 * down on purpose and leaves the slope safe for two days. 12 V for the cabinet and signals, 120 / 240 V AC for the
 * sensor heaters (without them the sensors ice up in the cold and the cabinet reports SENSOR ICED).
 */
public class TileAvalancheDetector extends NR6Tile {
    public static final int[] RANGE = {16, 24, 32, 48};
    public static final double[] HOLD_AT = {0.4, 0.6, 0.8};
    static final String[] PAGES = {"OVERVIEW", "SNOW", "SENSORS", "EVENTS"};

    private final List<BlockPos> rails = new ArrayList<>(), scanRails = new ArrayList<>();
    private int sliceX = -999, scanR, covered, obstructed;
    private double snowMm, slopeDeg, airC, hazard;
    private boolean tripped, iced, loading;
    private String tripWhy = "";
    private long relievedUntil;
    private int releases;

    public TileAvalancheDetector() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Avalanche detector " + pos.getX() + "," + pos.getZ(); }

    boolean armed() { return ctl.get("k_arm") == 1; }
    int range() { return RANGE[Math.max(0, Math.min(RANGE.length - 1, ctl.get("s_r")))]; }
    double holdAt() { return HOLD_AT[Math.max(0, Math.min(HOLD_AT.length - 1, ctl.get("s_thr")))]; }
    boolean manualHold() { return ctl.get("x_hold") == 1; }

    /** why the line is held (null = it isn't) */
    String holdWhy() {
        if (!armed()) return null;
        if (!powered()) return "avalanche detector has no 12 V (fail-safe)";
        if (tripped) return tripWhy;
        if (obstructed > 0) return obstructed + " m of line obstructed (snow / rock on the rails)";
        if (manualHold()) return "line held at the avalanche detector";
        if (hazard >= holdAt()) return "avalanche hazard " + Avalanche.LEVEL[Avalanche.level(hazard)];
        return null;
    }

    @Override
    public String gearState() {
        String h = holdWhy();
        return (powered() ? armed() ? "ARMED" : "disarmed" : "dark - no 12 V") + " · hazard " + Avalanche.LEVEL[Avalanche.level(hazard)] + (h == null ? "" : " · HOLDING: " + h);
    }

    @Override public int wiredLamp() { return !powered() ? 3 : tripped || obstructed > 0 ? 2 : 1; }

    @Override
    protected void serverTick() {
        if (age % 20 == 2) { mainsTick(world.isRaining() || airC < 2 ? 600 : 80); scanSlice(); }
        if (age % 20 == 12) checkObstruction();
        if (age % 200 == 50) assess();
        if (age % 10 == 6) landslides();
        if (age % 5 != 0) return;
        boolean on = powered();
        int lv = Avalanche.level(hazard);
        String hw = holdWhy();
        put("l_pwr", on);
        put("l_heat", on && mains.ok);
        put("l_low", on && lv <= 1);
        put("l_mod", on && lv == 2);
        put("l_high", on && lv >= 3);
        put("l_trip", on && tripped);
        put("l_obs", on && obstructed > 0);
        put("l_hold", on && hw != null);
        put("l_clear", on && armed() && hw == null);
        put("l_beacon", on && (tripped || lv >= 3));
        put("l_ice", on && iced);
        put("g_snow", (int) Math.min(100, snowMm / 1500 * 100));
        put("g_haz", (int) Math.round(hazard * 100));
        put("g_temp", (int) Math.max(0, Math.min(100, (airC + 20) / 40 * 100)));
        switch (page(PAGES.length)) {
            case 0 -> screen("AVALANCHE " + (armed() ? "ARMED" : "DISARMED") + "  R " + range(),
                    "HAZARD " + Avalanche.LEVEL[lv] + " " + Math.round(hazard * 100) + "%",
                    tripped ? "TRIPPED: " + tripWhy : "NO TRIP",
                    "OBSTRUCTED " + obstructed + " M  COVERED " + covered + "/" + rails.size(),
                    hw == null ? "LINE CLEAR" : "LINE HELD",
                    "HOLD AT " + Avalanche.LEVEL[Avalanche.level(holdAt())]);
            case 1 -> screen("SNOWPACK",
                    String.format(Locale.ROOT, "DEPTH %.0f MM", snowMm),
                    String.format(Locale.ROOT, "SLOPE %.0f DEG", slopeDeg),
                    String.format(Locale.ROOT, "AIR %.1f C %s", airC, airC > 0 ? "THAW" : "FROZEN"),
                    loading ? "LOADING - SNOW FALLING" : "NO NEW SNOW",
                    world.getTotalWorldTime() < relievedUntil ? "SLOPE RELIEVED BY BLAST" : "RELEASES " + releases);
            case 2 -> screen("SENSORS",
                    "SNOW DEPTH " + (iced ? "ICED" : on ? "OK" : "DEAD"),
                    "RADAR " + (on ? "OK" : "DEAD") + "  GEOPHONE " + (on ? "OK" : "DEAD"),
                    "SLIDE FENCE " + (tripped ? "BROKEN" : "INTACT"),
                    "HEATERS " + (mains.ok ? "ON" : mains.what),
                    "RAILS WATCHED " + rails.size());
            default -> screen(logLines("EVENTS", 6));
        }
    }

    /** IR rails in the zone, a few rows a second */
    private void scanSlice() {
        int r = range();
        if (sliceX == -999 || sliceX > scanR || scanR != r) {
            if (sliceX != -999 && scanR == r) { rails.clear(); rails.addAll(scanRails); covered = 0; for (BlockPos p : rails) if (Avalanche.covered(world, p)) covered++; }
            scanRails.clear(); scanR = r; sliceX = -r;
        }
        cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
        for (int n = 0; n < 8 && sliceX <= scanR; n++, sliceX++)
            for (int dz = -scanR; dz <= scanR; dz++)
                for (int dy = -6; dy <= 3; dy++) {
                    BlockPos q = pos.add(sliceX, dy, dz);
                    if (!world.isBlockLoaded(q)) continue;
                    if (w.getBlockEntity(new Vec3i(q.getX(), q.getY(), q.getZ()), TileRailBase.class) != null && scanRails.size() < 400) scanRails.add(q);
                }
    }

    private void checkObstruction() {
        if (rails.isEmpty()) { obstructed = 0; return; }
        int n = 0;
        for (BlockPos p : rails) if (Avalanche.obstructed(world, p)) n++;
        if (n > 0 && obstructed == 0 && armed() && powered()) {
            event("LINE OBSTRUCTED " + n + " M");
            alarm("aval:" + pos.toLong() + ":obs", AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "Line obstructed by snow / rock at " + wiredName() + " (" + n + " m) - signals held");
        }
        if (n == 0) clearAlarm("aval:" + pos.toLong() + ":obs");
        obstructed = n;
    }

    /** the slope and snowpack, every 10 s */
    private void assess() {
        int r = range();
        int cols = 0, snowy = 0, layers = 0;
        double best = 0;
        int baseY = rails.isEmpty() ? pos.getY() : rails.get(0).getY();
        for (int dx = -r; dx <= r; dx += 3)
            for (int dz = -r; dz <= r; dz += 3) {
                BlockPos c = pos.add(dx, 0, dz);
                if (!world.isBlockLoaded(c)) continue;
                BlockPos top = world.getHeight(c);
                cols++;
                net.minecraft.block.state.IBlockState s = world.getBlockState(top);
                if (s.getBlock() == Blocks.SNOW_LAYER) { snowy++; layers += s.getValue(BlockSnow.LAYERS); }
                else if (world.getBlockState(top.down()).getBlock() == Blocks.SNOW || s.getBlock() == Blocks.SNOW) { snowy++; layers += 8; }
                double run = Math.hypot(dx, dz), rise = top.getY() - baseY;
                if (run > 2 && rise > 0) best = Math.max(best, Math.toDegrees(Math.atan2(rise, run)));
            }
        slopeDeg = best;
        airC = Weather.airC(world, pos);
        snowMm = Math.max(Weather.snowMm(world, pos), Avalanche.vanillaSnow(cols, snowy, layers));
        loading = world.isRaining() && airC < 1;
        iced = !mains.ok && airC < -2;
        double h = Avalanche.hazard(snowMm, slopeDeg, airC, loading);
        if (world.getTotalWorldTime() < relievedUntil) h *= 0.2;
        int before = Avalanche.level(hazard);
        hazard = h;
        int after = Avalanche.level(h);
        if (after != before) event("HAZARD " + Avalanche.LEVEL[after]);
        if (after >= 3 && powered()) alarm("aval:" + pos.toLong() + ":haz", AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "Avalanche hazard " + Avalanche.LEVEL[after] + " at " + wiredName());
        else if (after < 3) clearAlarm("aval:" + pos.toLong() + ":haz");
        if (NR6Config.naturalAvalanches && world.rand.nextDouble() < Avalanche.releaseChance(h)) release("natural avalanche");
    }

    /** falling blocks (sand, gravel, anything loose) in the zone: a landslide */
    private void landslides() {
        int r = range();
        List<EntityFallingBlock> f = world.getEntitiesWithinAABB(EntityFallingBlock.class, new AxisAlignedBB(pos).grow(r, 24, r));
        if (f.size() >= 3) trip("LANDSLIDE - " + f.size() + " falling blocks in the zone");
    }

    void trip(String why) {
        if (tripped) return;
        if (!powered() || iced && world.rand.nextBoolean()) { event("SLIDE NOT DETECTED - " + (powered() ? "SENSOR ICED" : "NO 12 V")); return; }
        tripped = true;
        tripWhy = why;
        event("TRIPPED: " + why);
        PackSounds.play(world, pos, "alarm.klaxon", 1.5f, 1f);
        alarm("aval:" + pos.toLong() + ":trip", AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "AVALANCHE DETECTOR TRIPPED at " + pos.getX() + "," + pos.getZ() + ": " + why + (armed() ? " - signals held" : " - NOT ARMED, line not protected!"));
    }

    /** the snow comes down: deposits on the line where no gallery covers it */
    String release(String kind) {
        double snowF = Math.max(0, Math.min(1, (snowMm - 60) / 600));
        releases++;
        relievedUntil = world.getTotalWorldTime() + 48000;
        hazard *= 0.2;
        PackSounds.play(world, pos, "machine.explosion", 2.5f, 0.4f);
        if (snowF <= 0.05) { event(kind.toUpperCase(Locale.ROOT) + " - LITTLE SNOW CAME DOWN"); return "§7Little snow came down - the slope was nearly bare"; }
        List<BlockPos> open = new ArrayList<>();
        for (BlockPos p : rails) if (!Avalanche.covered(world, p)) open.add(p);
        if (open.isEmpty()) { event(kind.toUpperCase(Locale.ROOT) + " OVER THE GALLERIES - LINE PROTECTED"); return "§aThe avalanche ran over the galleries - the line is protected"; }
        Collections.shuffle(open, world.rand);
        int n = Math.min(open.size(), 2 + (int) Math.round(snowF * 10));
        for (int i = 0; i < n; i++) {
            BlockPos q = open.get(i).up();
            if (world.isAirBlock(q)) world.setBlockState(q, Blocks.SNOW.getDefaultState(), 3);
            for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.HORIZONTALS) {
                BlockPos s = q.offset(f);
                if (world.isAirBlock(s) && world.getBlockState(s.down()).isTopSolid()) world.setBlockState(s, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, 2 + world.rand.nextInt(4)), 3);
            }
        }
        trip(kind + " - snow on " + n + " m of line");
        return "§6" + kind + ": snow came down on " + n + " m of unprotected line";
    }

    @Override
    public void holds(Map<Long, String> hold, List<Object[]> zones) {
        String why = holdWhy();
        if (why == null) { Disruptions.release(world, "aval:" + pos.toLong(), "avalanche detector line clear"); return; }
        zones.add(new Object[]{pos.getX() + 0.5, pos.getZ() + 0.5, (double) range(), why});
        Disruptions.hold(world, "aval:" + pos.toLong(), why, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, range());
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.startsWith("s_") || id.startsWith("k_") || id.startsWith("m_")) return null;
        if (!powered()) return "§cCabinet dark - no 12 V";
        switch (id) {
            case "clear" -> {
                if (!tripped) return "§7Not tripped";
                if (obstructed > 0) return "§cThe line is still obstructed (" + obstructed + " m) - clear the rails first";
                tripped = false; tripWhy = "";
                clearAlarm("aval:" + pos.toLong() + ":trip");
                event("LINE CLEAR GIVEN by " + who(p));
                return "§aLine clear - detector reset";
            }
            case "hold" -> {
                ctl.set("x_hold", manualHold() ? 0 : 1);
                event(manualHold() ? "LINE HELD BY HAND" : "HAND HOLD OFF");
                return manualHold() ? "§6Line held" : "§aHand hold released";
            }
            case "blast" -> {
                if (!armed()) return "§cArm the detector first (key)";
                if (holdWhy() == null) return "§cHold the line first - nobody may be under the slope";
                return release("controlled release");
            }
            case "trip" -> { trip("TEST TRIP by " + who(p)); return "§6Test trip - signals in the zone go to danger"; }
            case "ack" -> {
                for (AlarmManager.Alarm a : AlarmManager.get(world).all()) if (a.key.startsWith("aval:" + pos.toLong()) && !a.acked) AlarmManager.get(world).ack(a.id, who(p));
                return "§7Acknowledged";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void storeMore(NBTTagCompound t) {
        t.setBoolean("tripped", tripped); t.setString("why", tripWhy); t.setLong("relieved", relievedUntil); t.setInteger("rel", releases);
        t.setFloat("haz", (float) hazard); t.setFloat("snow", (float) snowMm); t.setFloat("slope", (float) slopeDeg); t.setFloat("air", (float) airC);
    }

    @Override
    protected void restoreMore(NBTTagCompound t) {
        tripped = t.getBoolean("tripped"); tripWhy = t.getString("why"); relievedUntil = t.getLong("relieved"); releases = t.getInteger("rel");
        hazard = t.getFloat("haz"); snowMm = t.getFloat("snow"); slopeDeg = t.getFloat("slope"); airC = t.getFloat("air");
    }
}
