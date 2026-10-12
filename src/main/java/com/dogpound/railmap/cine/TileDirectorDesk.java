package com.dogpound.railmap.cine;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.ride.RideNet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * The director desk (three blocks wide, batch 5 #10): a vision mixer for train coverage. Preview bus (CAM 1-8 trackside
 * tripods, TRACKSIDE AUTO, CHASE, SIDE TRACK, CAB VIEW, HELI ORBIT, FLYBY) with green tally lamps, program tally in red,
 * CUT, the shot list (ADD the preview with the DURATION knob, DEL, CLR, NEXT, RUN key plays it in a loop), TRAIN < >
 * picks the subject, ON AIR key + timecode, letterbox + zoom knobs, WATCH puts your own screen on the program output
 * (spectator-style, your player stays where it is). The preview and program monitors show the real pictures live (CCTV
 * render-to-texture). 12 V + 120 / 240 V AC.
 */
public class TileDirectorDesk extends InfraTile {
    public static final Set<TileDirectorDesk> ALL = Collections.newSetFromMap(new WeakHashMap<>());
    public static final int[] DURATIONS = {3, 5, 8, 12, 20};
    public static final int[] FOVS = {30, 45, 60, 75};
    /** the bus buttons, left to right */
    public static final String[] BUS = {"b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b_auto", "b_chase", "b_side", "b_cab", "b_orbit", "b_fly"};
    public static final int[] BUS_SRC = {1, 2, 3, 4, 5, 6, 7, 8, CineMath.AUTO, CineMath.CHASE, CineMath.SIDE, CineMath.CAB, CineMath.ORBIT, CineMath.FLYBY};

    public int program = CineMath.AUTO, preview = CineMath.CHASE;
    public String target = "", targetName = "";
    public final List<int[]> shots = new ArrayList<>();
    public int listIdx, listT, autoCam;
    private long autoSince, airTicks;

    public TileDirectorDesk() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Director desk " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return mains.status().replaceAll("§.", "") + " · PGM " + CineMath.name(program) + " · " + (targetName.isEmpty() ? "no train" : targetName) + (ctl.get("k_air") == 1 ? " · ON AIR" : ""); }

    /** the tripod number a source is on now (0 = a virtual camera) */
    public int resolved(int src) { return src >= 1 && src <= 8 ? src : src == CineMath.AUTO ? autoCam : 0; }

    public int letterbox() { return Math.max(0, Math.min(3, ctl.get("s_lb"))); }
    public int fov() { return FOVS[Math.max(0, Math.min(FOVS.length - 1, ctl.get("s_fov")))]; }

    @Override
    protected void serverTick() {
        ALL.add(this);
        if (age % 20 == 6) mainsTick(450);
        if (age % 10 != 0) return;
        boolean on = live();
        long now = world.getTotalWorldTime();
        // the subject
        double[] tp = CineTrains.pose(world, target, 1f);
        if (tp == null && age % 40 == 0) {
            List<Locomotive> near = CineTrains.near(world, pos.getX(), pos.getZ(), 256);
            if (!near.isEmpty()) { target = near.get(0).getUUID().toString(); targetName = CineTrains.name(near.get(0)); tp = CineTrains.pose(world, target, 1f); controlsChanged(); }
        }
        // trackside auto-cut
        int was = autoCam;
        if (tp != null) {
            List<double[]> cams = new ArrayList<>();
            for (TileCamTripod t : TileCamTripod.ALL) {
                if (t.getWorld() != world || t.isInvalid() || !t.powered()) continue;
                Vec3d l = t.lens();
                if (l.squareDistanceTo(tp[0], tp[1], tp[2]) > CineConfig.tripodRange * CineConfig.tripodRange || !t.sees(tp[0], tp[1] + 1.5, tp[2])) continue;
                cams.add(new double[]{l.x, l.y, l.z, t.number()});
            }
            autoCam = CineMath.autoCut(cams, tp[0], tp[1], tp[2], autoCam, (now - autoSince) / 20.0, CineConfig.tripodRange, CineConfig.minShotSeconds);
        } else autoCam = 0;
        if (autoCam != was) { autoSince = now; if (program == CineMath.AUTO) controlsChanged(); }
        // the shot list
        if (on && ctl.get("k_run") == 1 && !shots.isEmpty()) {
            if (listIdx >= shots.size()) listIdx = 0;
            int[] s = shots.get(listIdx);
            if (program != s[0]) { program = s[0]; controlsChanged(); }
            listT += 10;
            if (listT >= s[1] * 20) { listT = 0; listIdx = (listIdx + 1) % shots.size(); program = shots.get(listIdx)[0]; controlsChanged(); }
        }
        if (on && ctl.get("k_air") == 1) airTicks += 10;
        // panel
        for (int i = 0; i < BUS.length; i++) {
            put("pg" + (i + 1), on && program == BUS_SRC[i]);
            put("pv" + (i + 1), on && preview == BUS_SRC[i]);
        }
        put("l_air", on && ctl.get("k_air") == 1);
        put("l_run", on && ctl.get("k_run") == 1);
        put("l_pwr", powered());
        put("l_sup", mains.ok);
        if (!on) { for (String k : new String[]{"d_pgm", "d_pvw", "d_trn", "d_tc", "d_auto", "d_s1", "d_s2", "d_s3", "d_s4", "d_s5", "d_s6"}) text(k, ""); return; }
        text("d_pgm", "PGM " + CineMath.name(program) + (resolved(program) > 0 && program == CineMath.AUTO ? " > CAM " + autoCam : ""));
        text("d_pvw", "PVW " + CineMath.name(preview));
        text("d_trn", tp == null ? "NO TRAIN IN RANGE" : (targetName.isEmpty() ? "TRAIN" : targetName.toUpperCase(Locale.ROOT)) + String.format(Locale.ROOT, " %.0f KM/H", tp[4] * 3.6));
        long s = airTicks / 20;
        text("d_tc", String.format(Locale.ROOT, "%s %02d:%02d:%02d:%02d", ctl.get("k_air") == 1 ? "ON AIR" : "TC", s / 3600, s / 60 % 60, s % 60, (airTicks % 20) * 25 / 20));
        text("d_auto", autoCam == 0 ? "AUTO: NO TRIPOD SEES IT" : "AUTO > CAM " + autoCam);
        for (int i = 0; i < 6; i++) {
            String line = "";
            if (i < shots.size()) { int[] sh = shots.get(i); line = (ctl.get("k_run") == 1 && i == listIdx ? ">" : " ") + (i + 1) + " " + CineMath.name(sh[0]) + " " + sh[1] + "S"; }
            else if (i == 0) line = "SHOT LIST EMPTY";
            text("d_s" + (i + 1), line);
        }
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.startsWith("s_") || id.startsWith("k_")) return null;
        if (!live()) return "§cThe desk is dark: " + (powered() ? mains.what : "no 12 V");
        for (int i = 0; i < BUS.length; i++) if (BUS[i].equals(id)) { preview = BUS_SRC[i]; controlsChanged(); return "§aPreview: " + CineMath.name(preview); }
        switch (id) {
            case "cut": {
                int t = program; program = preview; preview = t;
                controlsChanged();
                if (ctl.get("k_air") == 1) Recorder.event(world, "Director desk cut to " + CineMath.name(program));
                return "§cCUT §7- program " + CineMath.name(program);
            }
            case "add":
                if (shots.size() >= 12) return "§7Shot list full (12)";
                shots.add(new int[]{preview, DURATIONS[Math.max(0, Math.min(DURATIONS.length - 1, ctl.get("s_dur")))]});
                controlsChanged();
                return "§aShot " + shots.size() + ": " + CineMath.name(preview);
            case "del": if (!shots.isEmpty()) shots.remove(shots.size() - 1); listIdx = 0; controlsChanged(); return "§7Last shot removed";
            case "clr": shots.clear(); listIdx = 0; controlsChanged(); return "§7Shot list cleared";
            case "next":
                if (shots.isEmpty()) return "§7No shot list";
                listIdx = (listIdx + 1) % shots.size(); listT = 0; program = shots.get(listIdx)[0]; controlsChanged();
                return "§eNext shot: " + CineMath.name(program);
            case "trn_p": case "trn_n": {
                List<Locomotive> near = CineTrains.near(world, pos.getX(), pos.getZ(), 256);
                if (near.isEmpty()) return "§7No locomotive within 256 blocks";
                int at = -1;
                for (int i = 0; i < near.size(); i++) if (near.get(i).getUUID().toString().equals(target)) at = i;
                at = Math.floorMod(at + (id.equals("trn_n") ? 1 : -1), near.size());
                target = near.get(at).getUUID().toString();
                targetName = CineTrains.name(near.get(at));
                controlsChanged();
                return "§bSubject: " + targetName;
            }
            case "watch":
                if (p instanceof EntityPlayerMP mp) RideNet.NET.sendTo(new PacketCine(PacketCine.WATCH, pos.toLong()), mp);
                return "§bWatching the program output §7- F6 to leave";
            default: return null;
        }
    }

    @Override
    protected void store(NBTTagCompound t) {
        t.setInteger("pgm", program); t.setInteger("pvw", preview);
        t.setString("tgt", target); t.setString("tgtN", targetName);
        int[] a = new int[shots.size() * 2];
        for (int i = 0; i < shots.size(); i++) { a[i * 2] = shots.get(i)[0]; a[i * 2 + 1] = shots.get(i)[1]; }
        t.setIntArray("shots", a);
        t.setInteger("li", listIdx); t.setInteger("auto", autoCam); t.setLong("air", airTicks);
    }

    @Override
    protected void restore(NBTTagCompound t) {
        if (t.hasKey("pgm")) { program = t.getInteger("pgm"); preview = t.getInteger("pvw"); }
        target = t.getString("tgt"); targetName = t.getString("tgtN");
        shots.clear();
        int[] a = t.getIntArray("shots");
        for (int i = 0; i + 1 < a.length; i += 2) shots.add(new int[]{a[i], a[i + 1]});
        listIdx = t.getInteger("li"); autoCam = t.getInteger("auto"); airTicks = t.getLong("air");
    }
}
