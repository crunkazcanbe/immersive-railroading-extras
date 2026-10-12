package com.dogpound.railmap.station;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.incident.Announcer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Platform Door Safety Controller (ChatGPT batch 3 #32): the platform's length limits and door-release interlock for
 * the Station Door Controllers near it. The platform is the stretch between the two END rails (wrench a rail, click END
 * A / END B), or LENGTH metres centred on the controller along the track. For a train beside it:
 * <ul>
 *   <li>no door is released until the train is stopped;</li>
 *   <li>SDO AUTO (selective door opening): only coaches wholly at the platform open, coaches off the end stay locked;</li>
 *   <li>a train that fits the platform but stands more than {@link PlatformConfig#overrunM} m past an end has OVERRUN:
 *   every door stays locked and a major alarm goes off until it is set back;</li>
 *   <li>SDO OFF releases every coach (strictDoors keeps the ones off the platform shut anyway); LOCKED releases none;</li>
 *   <li>the staff BYPASS key releases every door (logged and alarmed).</li>
 * </ul>
 * Dead (no 12 V) it releases nothing: doors fail locked. Lamps show each coach in (green) or out (red).
 */
public class TileDoorController extends PlatformTile {
    static final int[] LENGTHS = {120, 160, 240, 320, 40, 80};
    static final int CARS = 12;
    private long endA, endB;
    private boolean overrun, alarmed;
    private int in, out, withheld;
    private double trainM, platM;
    private UUID lastTrain;
    private final Set<UUID> inside = new HashSet<>();
    private boolean stoppedNow;

    @Override protected long load() { return 4; }
    @Override public String wiredName() { return "Platform door safety controller"; }
    @Override public String gearState() { return !live() ? "NO 12 V - doors locked" : where() + " · " + modeName() + " · platform " + Math.round(platM) + " m" + (overrun ? " · OVERRUN" : ""); }

    int mode() { return Math.floorMod(ctl.get("s_mode"), 3); }   // 0 SDO AUTO, 1 SDO OFF, 2 LOCKED
    String modeName() { return new String[]{"SDO AUTO", "SDO OFF", "LOCKED"}[mode()]; }
    boolean bypass() { return ctl.get("k_bypass") == 1; }

    /** the platform as a segment in x/z: {ax, az, bx, bz} */
    private double[] segment(double yawDeg) {
        if (endA != 0 && endB != 0) {
            BlockPos a = BlockPos.fromLong(endA), b = BlockPos.fromLong(endB);
            return new double[]{a.getX() + 0.5, a.getZ() + 0.5, b.getX() + 0.5, b.getZ() + 0.5};
        }
        double half = LENGTHS[Math.floorMod(ctl.get("s_len"), LENGTHS.length)] / 2.0, y = Math.toRadians(yawDeg);
        double dx = -Math.sin(y), dz = Math.cos(y), cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        return new double[]{cx - dx * half, cz - dz * half, cx + dx * half, cz + dz * half};
    }

    /** {t from A along the platform, half length, lateral offset, platform length} */
    private double[] place(EntityRollingStock car, double[] seg) {
        double ux = seg[2] - seg[0], uz = seg[3] - seg[1], L = Math.max(1e-3, Math.hypot(ux, uz));
        ux /= L; uz /= L;
        Vec3d p = car.getPosition();
        double rx = p.x - seg[0], rz = p.z - seg[1];
        double half = 8;
        try { half = car.getDefinition().getLength(car.gauge) / 2; } catch (RuntimeException ignored) { }
        return new double[]{rx * ux + rz * uz, half, Math.abs(-rx * uz + rz * ux), L};
    }

    static boolean within(double t, double half, double L, double tol) { return t - half >= -tol && t + half <= L + tol; }

    /** a train that fits but stands past an end */
    static boolean overran(double min, double max, double L, double tol) { return max - min <= L && (min < -tol || max > L + tol); }

    private static boolean stopped(EntityRollingStock s) {
        return !(s instanceof EntityMoveableRollingStock m) || m.getCurrentSpeed() == null || Math.abs(m.getCurrentSpeed().metric()) < 0.5;
    }

    /** PlatformOps.doorsMayOpen: may this car's doors open now? */
    boolean mayOpen(EntityRollingStock car) {
        if (!live()) return false;
        if (bypass()) return true;
        if (mode() == 2 || !stopped(car)) { withheld++; return false; }
        if (overrun) { withheld++; return false; }
        double[] pl = place(car, segment(car.getRotationYaw()));
        if (pl[2] > 8) return true;                                // not on this platform's road
        boolean ok = within(pl[0], pl[1], pl[3], 0.5);
        if (ok || mode() == 1 && !PlatformConfig.strictDoors) return true;
        withheld++;
        return false;
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!control.equals("enda") && !control.equals("endb")) return null;
        if (control.equals("enda")) endA = pick.toLong(); else endB = pick.toLong();
        markDirty();
        controlsChanged();
        return "§aPlatform END " + (control.equals("enda") ? "A" : "B") + " = rail at " + pick.getX() + " " + pick.getY() + " " + pick.getZ();
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "enda", "endb" -> { long e = id.equals("enda") ? endA : endB; return "§7Pick a rail with the Signal Wrench, then click END " + (id.equals("enda") ? "A" : "B") + " with it" + (e == 0 ? "" : " (linked)"); }
            case "clrend" -> { endA = endB = 0; markDirty(); return "§eEnds cleared - the platform is LENGTH metres centred here"; }
            case "reset" -> { overrun = false; if (alarmed) { AlarmManager.get(world).clear("doors:" + pos.toLong()); alarmed = false; } return "§eDoor safety reset"; }
            case "ann" -> {
                if (station == Long.MIN_VALUE) return "§7Not at a named station";
                Announcer.say(world, station, stationName, "DOORS", out > 0 ? "This is a short platform. Doors will open on the front " + in + " coaches only. Passengers in the rear "
                        + out + " coaches please move forward." : "Please stand clear of the doors and let passengers off the train first.");
                return "§aAnnounced";
            }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("k_bypass")) {
            ctl.set(id, bypass() ? 0 : 1);
            world.playSound(null, pos, ModSounds.KEY, SoundCategory.BLOCKS, 0.6f, 1f);
            Recorder.event(world, "Door safety BYPASS " + (bypass() ? "IN" : "out") + " at " + where() + " by " + who(who));
            if (bypass()) AlarmManager.get(world).raise(world, "doorbypass:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "Door safety bypassed at " + where() + " by " + who(who), pos, true);
            else AlarmManager.get(world).clear("doorbypass:" + pos.toLong());
            return bypass() ? "§cDOOR SAFETY BYPASSED - every door releases" : "§aBypass out";
        }
        return null;
    }

    @Override
    protected void work() {
        resolve();
        if (age % 10 != 3) return;
        // the train beside the platform
        List<EntityRollingStock> cars = new ArrayList<>();
        double yaw = 0;
        for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
            Vec3d v = s.getPosition();
            if (s.isDead() || Math.abs(v.y - pos.getY()) > 5 || pos.distanceSqToCenter(v.x, pos.getY() + 0.5, v.z) > 200 * 200) continue;
            double[] pl = place(s, segment(s.getRotationYaw()));
            if (pl[2] > 8 || pl[0] < -60 || pl[0] > pl[3] + 60) continue;
            if (cars.isEmpty()) yaw = s.getRotationYaw();
            cars.add(s);
        }
        // order front-to-back along its consist when possible
        if (!cars.isEmpty() && cars.get(0) instanceof EntityCoupleableRollingStock c) {
            try { List<EntityCoupleableRollingStock> tr = c.getTrain(); List<EntityRollingStock> o = new ArrayList<>(); for (EntityCoupleableRollingStock x : tr) if (cars.contains(x)) o.add(x); if (o.size() == cars.size()) cars = o; } catch (RuntimeException ignored) { }
        }
        double[] seg = segment(yaw);
        platM = Math.hypot(seg[2] - seg[0], seg[3] - seg[1]);
        in = out = 0; trainM = 0;
        double min = 1e9, max = -1e9;
        boolean allStopped = !cars.isEmpty();
        inside.clear();
        for (int i = 0; i < cars.size(); i++) {
            double[] pl = place(cars.get(i), seg);
            trainM += pl[1] * 2;
            min = Math.min(min, pl[0] - pl[1]); max = Math.max(max, pl[0] + pl[1]);
            boolean w = within(pl[0], pl[1], pl[3], 0.5);
            if (w) { in++; inside.add(cars.get(i).getUUID()); } else out++;
            if (i < CARS) { put("ci" + (i + 1), live() && w); put("co" + (i + 1), live() && !w); }
            allStopped &= stopped(cars.get(i));
        }
        for (int i = cars.size(); i < CARS; i++) { put("ci" + (i + 1), false); put("co" + (i + 1), false); }
        stoppedNow = allStopped;
        boolean ov = allStopped && live() && overran(min, max, platM, PlatformConfig.overrunM);
        if (ov && !overrun) {
            overrun = true;
            world.playSound(null, pos, ModSounds.ALARM_HORN, SoundCategory.BLOCKS, 0.8f, 1f);
            AlarmManager.get(world).raise(world, "doors:" + pos.toLong(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR,
                    "Train OVERRAN the platform at " + where() + " - doors locked", pos, true);
            alarmed = true;
        }
        UUID head = cars.isEmpty() ? null : cars.get(0).getUUID();
        if (cars.isEmpty() || !allStopped && overrun) {                      // gone (or being set back): the overrun clears
            if (overrun || alarmed) { overrun = false; AlarmManager.get(world).clear("doors:" + pos.toLong()); alarmed = false; }
        }
        if (head != null && !head.equals(lastTrain) && allStopped) {
            lastTrain = head;
            if (out > 0 && in > 0 && mode() == 0 && live()) {
                Recorder.event(world, "SDO at " + where() + ": " + in + " coaches released, " + out + " kept shut (train " + Math.round(trainM) + " m, platform " + Math.round(platM) + " m)");
                QuestEvents.fire(world, null, pos, "sdo_used", "station", stationName, "in", in, "out", out);
            }
        }
        if (cars.isEmpty()) lastTrain = null;
        boolean released = live() && allStopped && (bypass() || mode() != 2 && !overrun);
        put("pwr", live()); put("stopl", allStopped); put("rel", released); put("sdol", live() && out > 0 && in > 0 && mode() == 0);
        put("over", out > 0); put("ovr", overrun); put("lockl", mode() == 2 || !live()); put("byl", bypass());
        text("plat", "PLAT " + Math.round(platM) + "M" + (endA != 0 && endB != 0 ? " LINKED" : ""));
        text("train", cars.isEmpty() ? "NO TRAIN" : "TRAIN " + Math.round(trainM) + "M " + cars.size() + "C");
        text("inout", "IN " + in + " OUT " + out);
        text("state", !live() ? "" : cars.isEmpty() ? "PLATFORM CLEAR" : !allStopped ? "TRAIN MOVING - LOCKED" : overrun ? "OVERRUN - LOCKED" : mode() == 2 ? "DOORS LOCKED"
                : bypass() ? "BYPASS - ALL RELEASED" : out > 0 && mode() == 0 ? "SDO " + in + "/" + cars.size() + " RELEASED" : "ALL DOORS RELEASED");
    }

    String[] row() {
        return new String[]{where(), modeName() + (bypass() ? " !BYPASS" : ""), Math.round(platM) + " m", trainM <= 0 ? "-" : Math.round(trainM) + " m",
                in + "/" + (in + out), overrun ? "!OVERRUN" : out > 0 ? "~SHORT" : stoppedNow ? "+stopped" : ".", String.valueOf(withheld)};
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setLong("endA", endA); t.setLong("endB", endB); t.setInteger("withheld", withheld); }
    @Override protected void loadExtra(NBTTagCompound t) { endA = t.getLong("endA"); endB = t.getLong("endB"); withheld = t.getInteger("withheld"); }

    static List<String> selftest() {
        List<String> f = new ArrayList<>();
        if (!within(50, 10, 100, 0.5) || within(95, 10, 100, 0.5)) f.add("platform 'within' wrong");
        if (!overran(-10, 80, 100, 3) || overran(-30, 130, 100, 3) || overran(2, 98, 100, 3)) f.add("overrun test wrong");
        return f;
    }
}
