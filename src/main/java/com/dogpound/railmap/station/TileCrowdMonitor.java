package com.dogpound.railmap.station;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.pax.EntityPax;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Platform Crowd Monitor (ChatGPT batch 3 #27): the CCTV people-counting panel for one platform. Every second it takes
 * the people waiting for trains here (PassengerData's numbers, shared between the station's monitors, or the visible
 * passengers on its stretch of platform if there are more) over the platform's area (LENGTH x WIDTH selectors), and
 * gives the density, the Fruin level of service A-F (m2 a person: A 1.2+, B 0.9, C 0.65, D 0.28, E 0.19, F under), the
 * inflow a minute, and the queues at the ticket gates and machines (M/D/1 below capacity, growing above it).
 * <p>What it can do about it, for real: METERING (OFF / AUTO / ON) lets fewer new passengers into the station
 * (PassengerData.generate asks PlatformOps.inflow); the CLOSE ENTRANCE key stops entry and the gates turn people away;
 * the DWELL key stretches a crowded train's stop so everyone gets on; PA asks people to move down the platform.
 * LOS E raises a minor alarm, F a major one.
 */
public class TileCrowdMonitor extends PlatformTile {
    static final int[] LENGTHS = {40, 80, 120, 160, 240, 320};
    static final int[] WIDTHS = {3, 4, 5, 6, 8};
    static final String LOS = "ABCDEF";
    private double inflow, lastWaiting = -1, gateQ, tvmQ, density, peak, people;
    private int los, gates, machines, alarmed = -1, worstToday;
    private long paSaid = -100000;
    private boolean hadMeasure;
    private final Set<UUID> stretched = new HashSet<>();

    @Override protected long load() { return 6; }
    @Override public String wiredName() { return "Platform crowd monitor"; }
    @Override public String gearState() { return !live() ? "NO 12 V" : where() + " · LOS " + LOS.charAt(los) + String.format(Locale.ROOT, " · %.2f p/m2", density); }

    int len() { return LENGTHS[Math.floorMod(ctl.get("s_len") + 2, LENGTHS.length)]; }   // default position = 120 m
    int wid() { return WIDTHS[Math.floorMod(ctl.get("s_wid") + 1, WIDTHS.length)]; }     // default = 4 m
    int meter() { return Math.floorMod(ctl.get("s_meter"), 3); }
    public boolean entranceClosed() { return ctl.get("k_close") == 1; }
    public int los() { return los; }
    public double density() { return density; }

    /** share of new passengers let in */
    public double inflow() {
        if (entranceClosed()) return 0;
        if (!live()) return 1;                                  // dead monitor: no metering
        return switch (meter()) {
            case 2 -> 0.5;
            case 1 -> los >= 4 ? 0.4 : los >= 3 ? 0.75 : 1;
            default -> 1;
        };
    }

    static int fruin(double perPerson) { return perPerson >= 1.2 ? 0 : perPerson >= 0.9 ? 1 : perPerson >= 0.65 ? 2 : perPerson >= 0.28 ? 3 : perPerson >= 0.19 ? 4 : 5; }

    /** M/D/1 mean queue (people) at utilisation rho; above 1 it grows */
    static double md1(double rho) { return rho <= 0 ? 0 : rho >= 0.98 ? 50 : rho * rho / (2 * (1 - rho)); }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "pa" -> { announce(true); return "§aCrowd announcement made"; }
            case "reset" -> { peak = density; worstToday = los; return "§ePeak readings reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("k_close")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            Recorder.event(world, "Crowd control at " + where() + ": entrance " + (entranceClosed() ? "CLOSED" : "reopened") + " by " + who(who));
            world.playSound(null, pos, ModSounds.KEY, SoundCategory.BLOCKS, 0.6f, 1f);
            if (entranceClosed()) QuestEvents.fire(world, who, pos, "crowd_control", "measure", "close", "station", stationName);
            return entranceClosed() ? "§cENTRANCE CLOSED - the gates turn people away" : "§aEntrance open";
        }
        return null;
    }

    private void announce(boolean byHand) {
        if (station == Long.MIN_VALUE) return;
        long now = world.getTotalWorldTime();
        if (!byHand && now - paSaid < 6000) return;
        paSaid = now;
        String t = los >= 5 ? "Due to severe crowding entry to the platforms is being controlled. Please follow the instructions of staff and do not push."
                : "This platform is very busy. Please move right down the platform and use all the doors on the train.";
        Announcer.say(world, station, stationName, "CROWD", t);
    }

    @Override
    protected void work() {
        resolve();
        if (age % 20 != 0) return;
        long now = world.getTotalWorldTime();
        if (age % 100 == 0) count();
        PassengerData pd = PassengerData.get(world);
        PassengerData.Stn s = station == Long.MIN_VALUE ? null : pd.stn(station);
        double waiting = s == null ? 0 : s.waitingTotal();
        if (lastWaiting >= 0) inflow = inflow * 0.9 + Math.max(0, waiting - lastWaiting) * 60 * 0.1;
        lastWaiting = waiting;
        int n = Math.max(1, at(world, TileCrowdMonitor.class, station).size());
        int r = len() / 2;
        int visible = world.getEntitiesWithinAABB(EntityPax.class, new AxisAlignedBB(pos).grow(r, 4, r), e -> e.state() == EntityPax.WAIT || e.state() == EntityPax.SIT || e.state() == EntityPax.TO_SPOT).size();
        people = Math.max(waiting / n, visible);
        double area = len() * wid() * 0.85;                       // furniture, stairs, the yellow line
        density = people / area;
        los = people < 0.5 ? 0 : fruin(area / people);
        peak = Math.max(peak, density);
        worstToday = Math.max(worstToday, los);
        // queues
        double gateCap = gates * (double) PlatformConfig.gatePaxPerMin, tvmCap = machines * (double) PlatformConfig.machinePaxPerMin, tvmIn = inflow * PlatformConfig.machineShare;
        gateQ = queue(gateQ, inflow * inflow(), gateCap);
        tvmQ = queue(tvmQ, tvmIn * inflow(), tvmCap);
        // measures
        boolean measure = entranceClosed() || live() && meter() > 0;
        if (live() && ctl.get("k_dwell") == 1 && los >= 3 && PlatformConfig.crowdDwellMaxSec > 0) {
            for (RailwayData.AutoTrain a : RailwayData.get(world).trains().values()) {
                Autopilot.Run run = Autopilot.run(a.loco);
                if (run == null || !run.dwelling || run.dwellStation != station || stretched.contains(a.loco)) continue;
                int sec = Math.min(PlatformConfig.crowdDwellMaxSec, (los - 2) * 15);
                if (Autopilot.adjustDwell(a.loco, now, sec)) { stretched.add(a.loco); Recorder.event(world, "Crowd dwell +" + sec + " s for " + (a.label.isEmpty() ? "train" : a.label) + " at " + where() + " (LOS " + LOS.charAt(los) + ")"); }
            }
        }
        stretched.removeIf(u -> { Autopilot.Run run = Autopilot.run(u); return run == null || !run.dwelling || run.dwellStation != station; });
        // alarms + PA
        int lvl = !live() || !PlatformConfig.crowdAlarms ? 0 : los >= 5 ? 2 : los >= 4 ? 1 : 0;
        String key = "crowd:" + pos.toLong();
        if (lvl != alarmed) {
            AlarmManager am = AlarmManager.get(world);
            if (lvl == 0) am.clear(key);
            else am.raise(world, key, AlarmManager.Cat.GENERAL, lvl == 2 ? AlarmManager.Sev.MAJOR : AlarmManager.Sev.MINOR,
                    "Platform overcrowding at " + where() + ": LOS " + LOS.charAt(los) + String.format(Locale.ROOT, " %.2f p/m2", density), pos, true);
            if (lvl > 0) world.playSound(null, pos, ModSounds.BUZZER, SoundCategory.BLOCKS, 0.6f, 1f);
            if (lvl == 0 && alarmed > 0 && hadMeasure) QuestEvents.fire(world, null, pos, "crowd_control", "measure", "relieved", "station", stationName);
            alarmed = lvl;
        }
        if (lvl > 0) hadMeasure |= measure; else if (los <= 2) hadMeasure = false;
        if (live() && los >= 4) announce(false);
        // panel
        put("pwr", live());
        for (int i = 0; i < 6; i++) put("los" + Character.toLowerCase(LOS.charAt(i)), los == i);
        put("meterl", live() && meter() > 0 && inflow() < 1); put("closedl", entranceClosed()); put("dwelll", ctl.get("k_dwell") == 1);
        put("alarml", lvl > 0); put("densg", (int) Math.min(100, density * 100 / 6));
        text("losd", String.valueOf(LOS.charAt(los)));
        text("dens", String.format(Locale.ROOT, "%.2f P/M2", density));
        text("waitn", "WAIT " + Math.round(people));
        text("inflow", "IN " + Math.round(inflow) + "/MIN");
        text("gateq", gates == 0 ? "NO GATES" : "GATE Q " + Math.round(gateQ) + " " + Math.round(gateQ / Math.max(1, gateCap) * 60) + "S");
        text("tvmq", machines == 0 ? "NO TVM" : "TVM Q " + Math.round(tvmQ) + " " + Math.round(tvmQ / Math.max(1, tvmCap) * 60) + "S");
        text("area", len() + "M X " + wid() + "M");
        text("line", station == Long.MIN_VALUE ? "NO STATION" : cut(stationName.toUpperCase(Locale.ROOT), 12) + " PEAK " + LOS.charAt(fruin(peak <= 0 ? 99 : 1 / peak)));
    }

    private static double queue(double q, double in, double cap) {
        if (cap <= 0) return 0;
        double rho = in / cap;
        if (rho >= 1) return Math.min(999, q + (in - cap) / 60.0);
        double target = md1(rho);
        return q > target ? Math.max(target, q - (cap - in) / 60.0) : target;
    }

    /** gates and ticket machines serving the station */
    private void count() {
        int g = 0, m = 0;
        BlockPos sp = station == Long.MIN_VALUE ? pos : BlockPos.fromLong(station);
        for (TileEntity t : new ArrayList<>(world.loadedTileEntityList)) {
            if (t.getPos().distanceSq(sp) > 48 * 48) continue;
            if (t instanceof com.dogpound.railmap.block.TileTicketGate) g++;
            else if (t instanceof com.dogpound.railmap.block.TileTicketMachine) m++;
        }
        gates = g; machines = m;
    }

    /** console row */
    String[] row() {
        return new String[]{where(), len() + "x" + wid() + " m", String.valueOf(Math.round(people)), String.format(Locale.ROOT, "%.2f", density),
                (los >= 5 ? "!" : los >= 4 ? "~" : "+") + LOS.charAt(los), String.valueOf(Math.round(inflow)), gates == 0 ? "-" : String.valueOf(Math.round(gateQ)),
                machines == 0 ? "-" : String.valueOf(Math.round(tvmQ)), (entranceClosed() ? "!CLOSED " : "") + new String[]{"", "meter AUTO", "meter ON"}[meter()] + (ctl.get("k_dwell") == 1 ? " dwell" : "")};
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setDouble("inflow", inflow); t.setDouble("peak", peak); t.setInteger("worst", worstToday); }
    @Override protected void loadExtra(NBTTagCompound t) { inflow = t.getDouble("inflow"); peak = t.getDouble("peak"); worstToday = t.getInteger("worst"); }

    /** self-test of the maths (PlatformCommand selftest) */
    static List<String> selftest() {
        List<String> f = new ArrayList<>();
        if (fruin(2.0) != 0 || fruin(1.0) != 1 || fruin(0.7) != 2 || fruin(0.3) != 3 || fruin(0.2) != 4 || fruin(0.1) != 5) f.add("Fruin bands wrong");
        if (Math.abs(md1(0.5) - 0.25) > 1e-9) f.add("M/D/1 queue wrong");
        if (queue(0, 30, 25) <= 0) f.add("over-capacity queue does not grow");
        if (queue(0, 0, 25) != 0) f.add("empty queue not zero");
        return f;
    }
}
