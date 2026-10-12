package com.dogpound.railmap.station;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.incident.Announcer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * Station Security + Opening Hours Panel (ChatGPT batch 3 #33). OPENS / CLOSES selectors (hours) and a MODE selector
 * (AUTO by the hours / OPEN / CLOSED). While the station is closed:
 * <ul>
 *   <li>the ticket gates let people out but nobody in (TileTicketGate asks PlatformOps.gateRefuse);</li>
 *   <li>no new passengers arrive (PlatformOps.inflow = 0) and the ones still waiting go home;</li>
 *   <li>night mode: AUTO platform lamps go to SECURITY lighting (one in three) or OFF (LAMPS selector);</li>
 *   <li>the PIS shows "station closed - opens at 05:00"; 15 minutes before closing, at closing and at opening the PA says so;</li>
 *   <li>ARMED: a player in the station (RADIUS) who hasn't signed in at this panel raises an INTRUDER alarm and the
 *   klaxon (ACK silences it).</li>
 * </ul>
 * With no 12 V the panel can't tell the time: the station stays as it was (no new closures), the alarm can't arm.
 */
public class TileStationSecurity extends PlatformTile {
    static final int[] RADII = {32, 48, 16, 24};
    private boolean closed, wasClosed, warned, alarmed, silenced;
    private int pisMsg = -1, intruders;
    private long day = -1;
    private final Set<UUID> staff = new HashSet<>();
    private final Map<UUID, String> staffNames = new HashMap<>();
    private String lastIntruder = "";

    @Override protected long load() { return 3; }
    @Override public String wiredName() { return "Station security panel"; }
    @Override public String gearState() { return where() + " · " + (closed ? "CLOSED" : "OPEN") + " · hours " + hours() + (armed() ? " · ARMED" : ""); }

    int openH() { return Math.floorMod(ctl.get("s_open") + 5, 24); }       // selector position 0 = 05
    int closeH() { return Math.floorMod(ctl.get("s_close"), 24); }          // position 0 = 00 (midnight)
    int mode() { return Math.floorMod(ctl.get("s_mode"), 3); }              // AUTO, OPEN, CLOSED
    int lamps() { return Math.floorMod(ctl.get("s_lamps"), 3); }            // SECURITY, OFF, ON
    boolean armed() { return ctl.get("k_arm") == 1 && live(); }
    double radius() { return RADII[Math.floorMod(ctl.get("s_rad"), RADII.length)]; }
    public boolean closed() { return closed; }
    String hours() { return String.format(Locale.ROOT, "%02d:00-%02d:00", openH(), closeH()); }
    public String opensAt() { return String.format(Locale.ROOT, "%02d:00", openH()); }

    /** open at this hour by the timetable? (close == open = always open; spans midnight) */
    static boolean openAt(int h, int open, int close) {
        if (open == close) return true;
        return open < close ? h >= open && h < close : h >= open || h < close;
    }

    boolean lampOff(BlockPos lamp) {
        return switch (lamps()) {
            case 1 -> true;
            case 2 -> false;
            default -> Math.floorMod(lamp.getX() / 4 + lamp.getZ() / 4, 3) != 0;   // security lighting: one in three stays lit
        };
    }

    static int minute(net.minecraft.world.World w) { return (int) (((w.getWorldTime() + 6000) % 1000) * 60 / 1000); }

    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "sign" -> {
                if (who == null) return "§7Staff sign in at the panel's card reader";
                if (staff.remove(who.getUniqueID())) { staffNames.remove(who.getUniqueID()); Recorder.event(world, "Security " + where() + ": " + who.getName() + " signed OUT"); return "§eSigned out"; }
                staff.add(who.getUniqueID()); staffNames.put(who.getUniqueID(), who.getName());
                world.playSound(null, pos, ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1f);
                Recorder.event(world, "Security " + where() + ": " + who.getName() + " signed IN");
                return "§aSigned in as staff - the intruder alarm ignores you";
            }
            case "ack" -> { silenced = true; return "§eIntruder alarm silenced"; }
            case "ann" -> { if (station == Long.MIN_VALUE) return "§7Not at a named station"; say(closed ? "This station is closed. Please make your way to the exit." : "This station closes at " + String.format(Locale.ROOT, "%02d:00", closeH()) + ". Please check the time of your last train."); return "§aAnnounced"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("k_arm")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            world.playSound(null, pos, ModSounds.KEY, SoundCategory.BLOCKS, 0.6f, 1f);
            Recorder.event(world, "Security " + where() + ": intruder alarm " + (ctl.get(id) == 1 ? "ARMED" : "disarmed") + " by " + who(who));
            return ctl.get(id) == 1 ? "§cARMED - intruder detection while closed" : "§aDisarmed";
        }
        return null;
    }

    private void say(String t) { if (station != Long.MIN_VALUE) Announcer.say(world, station, stationName, "SECURITY", t); }

    @Override
    protected void work() {
        resolve();
        if (age % 20 != 11) return;
        int h = PassengerData.hour(world), mi = minute(world);
        long d = (world.getWorldTime() + 6000) / 24000;
        if (d != day) { day = d; warned = false; }
        if (live()) {
            closed = mode() == 2 || mode() == 0 && !openAt(h, openH(), closeH());
            // closing soon: 15 minutes before the hour it shuts
            if (mode() == 0 && !closed && !warned && openH() != closeH() && h == Math.floorMod(closeH() - 1, 24) && mi >= 45) {
                warned = true;
                say("This station will close in 15 minutes. Please check the time of your last train.");
            }
        }
        if (closed != wasClosed) {
            wasClosed = closed;
            Pis pis = Pis.get(world);
            if (closed) {
                say("This station is now closed. Please make your way to the exit. Thank you for travelling with us.");
                if (!stationName.isEmpty()) { Pis.Msg m = pis.post(world, stationName, Pis.Kind.INFO, "Station closed - opens at " + opensAt(), "SECURITY", 0); m.prio = 7; pisMsg = m.id; }
                Recorder.event(world, "Station " + where() + " CLOSED (" + new String[]{"timetable", "", "by hand"}[mode()] + ")");
                QuestEvents.fire(world, null, pos, "station_closed", "station", stationName);
            } else {
                for (Pis.Msg m : pis.msgs) if (m.id == pisMsg) m.expires = world.getTotalWorldTime();
                pis.markDirty();
                pisMsg = -1;
                say("Good morning. This station is now open.");
                Recorder.event(world, "Station " + where() + " OPEN");
            }
            world.playSound(null, pos, ModSounds.GATE, SoundCategory.BLOCKS, 0.7f, 0.8f);
            markDirty();
        }
        // the people still waiting go home
        if (closed && station != Long.MIN_VALUE) {
            PassengerData pd = PassengerData.get(world);
            PassengerData.Stn s = pd.stn(station);
            if (s != null && s.waitingTotal() > 0.5) {
                double k = PlatformConfig.closedLeaveRate, gone = 0;
                for (Map.Entry<Long, Double> e : s.waiting.entrySet()) { gone += e.getValue() * k; e.setValue(e.getValue() * (1 - k)); }
                s.walkedDay += gone;
                pd.markDirty();
            }
        }
        // intruders
        intruders = 0;
        String name = "";
        if (closed && armed() && PlatformConfig.intruderAlarm) {
            BlockPos c = station == Long.MIN_VALUE ? pos : BlockPos.fromLong(station);
            double r = radius();
            for (EntityPlayer p : world.playerEntities) {
                if (p.isCreative() || p.isSpectator() || staff.contains(p.getUniqueID()) || p.getDistanceSq(c) > r * r) continue;
                intruders++;
                name = p.getName();
                if (!p.getName().equals(lastIntruder)) p.sendStatusMessage(new TextComponentString("§cThis station is closed - security has been alerted"), true);
            }
        }
        String key = "intruder:" + pos.toLong();
        if (intruders > 0 && !alarmed) {
            alarmed = true; silenced = false; lastIntruder = name;
            AlarmManager.get(world).raise(world, key, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MAJOR, "INTRUDER in closed station " + where() + ": " + name, pos, true);
            QuestEvents.fire(world, null, pos, "station_intruder", "station", stationName, "who", name);
        } else if (intruders == 0 && alarmed) { alarmed = false; lastIntruder = ""; AlarmManager.get(world).clear(key); }
        if (alarmed && !silenced && age % 100 < 20) world.playSound(null, pos, ModSounds.KLAXON, SoundCategory.BLOCKS, 1.0f, 1f);
        // panel
        put("pwr", live()); put("openl", !closed); put("closedl", closed); put("armedl", armed()); put("intrl", alarmed); put("gatesl", closed);
        put("nightl", closed && lamps() != 2); put("staffl", !staff.isEmpty());
        text("clock", live() ? String.format(Locale.ROOT, "%02d:%02d", h, mi) : "");
        text("status", closed ? "CLOSED - OPENS " + opensAt() : "OPEN" + (mode() == 1 ? " (HELD)" : ""));
        text("hours", hours());
        text("staff", "STAFF " + staff.size());
        text("intr", intruders > 0 ? "INTRUDER " + cut(name.toUpperCase(Locale.ROOT), 9) : "AREA CLEAR");
    }

    String[] row() {
        return new String[]{where(), closed ? "!CLOSED" : "+OPEN", hours(), new String[]{"AUTO", "OPEN", "CLOSED"}[mode()], armed() ? "~ARMED" : ".off",
                intruders > 0 ? "!" + intruders : "0", String.join(",", staffNames.values()), new String[]{"security", "off", "on"}[lamps()]};
    }

    void force(int m) { ctl.set("s_mode", m); controlsChanged(); }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setBoolean("closed", closed); t.setBoolean("was", wasClosed); t.setInteger("pis", pisMsg); t.setBoolean("warned", warned); t.setLong("day", day);
        NBTTagList l = new NBTTagList();
        for (UUID u : staff) l.appendTag(new NBTTagString(u + "|" + staffNames.getOrDefault(u, "")));
        t.setTag("staff", l);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        closed = t.getBoolean("closed"); wasClosed = t.getBoolean("was"); pisMsg = t.hasKey("pis") ? t.getInteger("pis") : -1; warned = t.getBoolean("warned"); day = t.hasKey("day") ? t.getLong("day") : -1;
        staff.clear(); staffNames.clear();
        NBTTagList l = t.getTagList("staff", 8);
        for (int i = 0; i < l.tagCount(); i++) {
            String[] p = l.getStringTagAt(i).split("\\|", 2);
            try { UUID u = UUID.fromString(p[0]); staff.add(u); staffNames.put(u, p.length > 1 ? p[1] : ""); } catch (IllegalArgumentException ignored) { }
        }
    }

    static List<String> selftest() {
        List<String> f = new ArrayList<>();
        if (!openAt(10, 5, 0) || openAt(2, 5, 0) || !openAt(23, 5, 0)) f.add("hours spanning midnight wrong");
        if (!openAt(3, 7, 7) || openAt(20, 6, 18) || !openAt(6, 6, 18)) f.add("day hours wrong");
        return f;
    }
}
