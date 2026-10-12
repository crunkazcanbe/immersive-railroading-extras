package com.dogpound.railmap.cctv;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

import java.util.Locale;
import java.util.Map;

/**
 * One CCTV camera (dome, bullet, PTZ, fisheye, night / IR, thermal, platform, doorbell). Needs its 12 V terminal and a
 * real supply: PoE 48 V from the NVR switch its wire run reaches, else 230 V mains (a grid cable touching it). Its
 * picture reaches monitors over the video network (the NVR's wire / radio bus). The server keeps its configuration
 * (name, aim, field of view, privacy masks, motion zones + sensitivity, IR mode, PTZ presets) and its ray-cast frames
 * (recording, motion and tamper detection).
 */
public class TileCamera extends CctvTile {
    public int no;
    public String name = "";
    // aim: fixed cameras = offsets from the mount; PTZ = pan / tilt (motors slew to the target)
    public float yawOff, pitchOff, fovSet;
    public float pan, tilt, zoom = 1, panT, tiltT = -1000, zoomT = 1;
    public int speed = 1, focus, iris, irMode;     // irMode 0 AUTO 1 ON 2 OFF; focus 0 AUTO 1 NEAR 2 MID 3 FAR; iris 0 AUTO..4 F11
    public String masks = "";
    public long zones = (1L << 48) - 1;
    public int sens = 5;
    public boolean motionOn = true;
    public int trigCam, trigPreset;               // motion here -> preset on that PTZ camera
    public final float[][] presets = new float[9][];
    public long wipedAt, recUntil;
    // runtime
    long poeUntil;
    BlockPos nvr;
    public int status;
    public String what = "";
    public boolean ir;
    private int[] prevLuma;
    long nextFrame;
    long nextRec;
    private long onlineSince = -1, lastMotionAlarm, lastServo;
    private boolean tamper;
    private String dirKey = "";
    // client: the head as drawn (eased toward the synced position)
    public float panR, tiltR = -1000, zoomR = 1;

    public CamType type() {
        if (world == null) return CamType.DOME;
        net.minecraft.block.Block b = world.getBlockState(pos).getBlock();
        return b instanceof CctvBlock cb && cb.cam != null ? cb.cam : CamType.DOME;
    }

    @Override protected int watts() { return type().watts + (ir ? 4 : 0); }
    @Override public String wiredName() { return (name.isEmpty() ? type().label + " camera" : name) + (no > 0 ? " (CAM " + no + ")" : ""); }
    @Override public boolean running() { return status == CctvData.ONLINE || status == CctvData.TAMPER; }
    /** 12 V and its supply are there (it may still be off the network) */
    public boolean powerOk() { return running() || status == CctvData.NO_NET; }
    @Override public String gearState() { return CctvData.STATUS[status] + (what.isEmpty() ? "" : " - " + what); }

    @Override
    public void gearInfo(Map<String, Object> m) {
        m.put("camera", no); m.put("type", type().label); m.put("pan", pan); m.put("tilt", tilt); m.put("zoom", zoom); m.put("ir", ir);
        m.put("supply", poeUntil > (world == null ? 0 : world.getTotalWorldTime()) ? "PoE 48 V" : mains.what);
    }

    // ---- pose --------------------------------------------------------------------------------------------------------
    /** {x, y, z, yaw, pitch, fov}; render = the client's eased head */
    public double[] pose(boolean render) {
        CamType t = type();
        EnumFacing f = facing();
        float p = render ? panR : pan, ti = render ? tiltR : tilt, z = render ? zoomR : zoom;
        if (ti < -500) ti = t.pitch;
        double yaw = f.getHorizontalAngle(), pitch;
        double lx, ly, lz;                                     // lens in model px, north-facing, before the mount turn
        if (t == CamType.PTZ) {
            yaw += p;
            pitch = ti;
            double a = Math.toRadians(ti), b = Math.toRadians(p), r = 4;
            double vy = -r * Math.sin(a), vz = -r * Math.cos(a);   // tilt the lens about the tilt axis
            double vx = Math.sin(b) * (-vz);                        // pan: (0, vy, vz) -> (sin b * |vz|, vy, cos b * vz)
            double vz2 = vz * Math.cos(b);
            lx = CamType.PIV_X + vx; ly = CamType.PIV_Y + vy; lz = CamType.PIV_Z + vz2;
        } else {
            yaw += yawOff;
            pitch = t.pitch + pitchOff;
            lx = t.eye[0]; ly = t.eye[1]; lz = t.eye[2];
        }
        pitch = Math.max(-60, Math.min(90, pitch));
        // the mount turn (as ControlsRenderer: -90 deg per quarter about the block centre)
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        double ang = Math.toRadians(-90 * turns), dx = lx - 8, dz = lz - 8;
        double rx = dx * Math.cos(ang) + dz * Math.sin(ang), rz = -dx * Math.sin(ang) + dz * Math.cos(ang);
        double[] fw = Snap.basis((float) yaw, (float) pitch)[0];
        double x = pos.getX() + (rx + 8) / 16 + fw[0] * 0.15, y = pos.getY() + ly / 16 + fw[1] * 0.15, zz = pos.getZ() + (rz + 8) / 16 + fw[2] * 0.15;
        float fov = (fovSet > 0 ? fovSet : t.fov) / Math.max(1, z);
        return new double[]{x, y, zz, yaw, pitch, fov};
    }

    // ---- server ------------------------------------------------------------------------------------------------------
    @Override
    protected void serverTick() {
        long now = world.getTotalWorldTime();
        CctvData d = CctvData.get(world);
        if (no <= 0) { no = d.allocCam(); if (name.isEmpty()) name = type().label + " " + no; controlsChanged(); }
        Cctv.CAMS.add(this);
        CamType t = type();
        if (tiltT < -500) { tiltT = t == CamType.PTZ ? 20 : 0; tilt = tiltT; }
        if (t == CamType.PTZ) slew(now);
        boolean poe = poeUntil > now;
        boolean supply = poe || !CctvConfig.needSupply || mainsOk();
        if (age % 20 != 7) return;
        int was = status;
        if (!term.powered()) { status = CctvData.OFF; what = term.what; }
        else if (!supply) { status = CctvData.NO_SUPPLY; what = "no PoE and " + mains.what; }
        else if (CctvConfig.requireNetwork && net() == 0) { status = CctvData.NO_NET; what = "no NVR on its wire run - land a wire colour and run it to an NVR"; }
        else { status = tamper ? CctvData.TAMPER : CctvData.ONLINE; what = poe ? "PoE 48 V" : mains.what; }
        // IR: the cut filter drops out in the dark (thermal never needs it)
        boolean dark = Math.max(world.getLightFor(EnumSkyBlock.SKY, pos) - world.getSkylightSubtracted(), world.getLightFor(EnumSkyBlock.BLOCK, pos)) < 7;
        boolean wasIr = ir;
        ir = t != CamType.THERMAL && running() && (irMode == 1 || irMode == 0 && dark);
        if (ir != wasIr) put("ir", ir);
        if (running() && onlineSince < 0) {
            onlineSince = now;
            AlarmManager.get(world).clear("cctv:loss:" + no);
            if (com.dogpound.railmap.api.QuestEvents.active())
                com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "cctv_camera_online", "camera", no, "type", t.name());
        }
        if (!running() && onlineSince >= 0) {
            if (now - onlineSince > 100) videoLoss(CctvData.STATUS[status] + ": " + what);
            onlineSince = -1;
        }
        lamps(now);
        if (was != status) controlsChanged();
        writeDir(d, now);
    }

    void videoLoss(String why) {
        if (world == null || world.isRemote || no <= 0) return;
        AlarmManager.get(world).raise(world, "cctv:loss:" + no, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MAJOR, "CCTV VIDEO LOSS CAM " + no + " " + name + " - " + why, pos, true);
        CctvData.get(world).event(world.getTotalWorldTime(), no, "LOSS", "VIDEO LOSS CAM " + no);
        com.dogpound.railmap.alarm.Recorder.event(world, "CCTV video loss: camera " + no + " (" + name + ") " + why);
    }

    private void lamps(long now) {
        put("led_pwr", term.powered());
        put("led_net", running());
        CctvData.Cam c = CctvData.get(world).cam(no);
        put("led_rec", c != null && (c.rec || now - c.lastMotion < 100));
        put("led_cal", running() && now % 3600 < 40);
        put("ring", term.powered());
    }

    private void writeDir(CctvData d, long now) {
        CctvData.Cam c = d.cam(no);
        if (c == null) { c = new CctvData.Cam(); c.no = no; d.cams.put(no, c); }
        double[] p = pose(false);
        c.type = type().ordinal(); c.dim = world.provider.getDimension(); c.pos = pos.toLong(); c.train = "";
        c.x = p[0]; c.y = p[1]; c.z = p[2]; c.yaw = (float) p[3]; c.pitch = (float) p[4]; c.fov = (float) p[5];
        c.name = name; c.status = status; c.what = what; c.net = CctvConfig.requireNetwork ? net() : 0; c.ir = ir; c.masks = masks;
        c.gain = new float[]{1f, 1.6f, 1f, 0.6f, 0.3f}[Math.max(0, Math.min(4, iris))];
        boolean rain = world.isRainingAt(new BlockPos(p[0], p[1] + 0.6, p[2])) && (type() != CamType.PTZ || now - wipedAt > 1200);
        c.fx = (rain ? 1 : 0) | (focus == 1 || focus == 3 ? 2 : 0);
        String k = c.type + "|" + c.name + "|" + c.status + "|" + c.net + "|" + c.ir + "|" + c.masks + "|" + Math.round(c.yaw) + "|" + Math.round(c.pitch) + "|" + Math.round(c.fov * 4) + "|" + c.gain + "|" + c.fx + "|" + c.rec + "|" + c.dim;
        if (!k.equals(dirKey)) { dirKey = k; d.changed(); }
    }

    /** PTZ motors: pan / tilt / zoom slew toward their targets at a real motor speed */
    private void slew(long now) {
        float rate = new float[]{1.5f, 3f, 6f}[Math.max(0, Math.min(2, speed))];
        if (!term.powered() || !powerOk()) return;                                    // motors need the camera's supply
        float dp = wrap(panT - pan), dt = tiltT - tilt, dz = zoomT - zoom;
        boolean moving = Math.abs(dp) > 0.01f || Math.abs(dt) > 0.01f || Math.abs(dz) > 0.01f;
        if (!moving) return;
        pan = wrap(pan + Math.max(-rate, Math.min(rate, dp)));
        tilt += Math.max(-rate, Math.min(rate, dt));
        zoom += Math.max(-0.6f, Math.min(0.6f, dz));
        if (now - lastServo > 8) { lastServo = now; PackSounds.play(world, pos, "machine.servo", 0.35f, 1.3f); }
        if (now % 5 == 0 || Math.abs(dp) <= rate && Math.abs(dt) <= rate && Math.abs(dz) <= 0.6f) controlsChanged();
    }

    static float wrap(float a) { a %= 360; if (a > 180) a -= 360; if (a < -180) a += 360; return a; }

    /** a PTZ move command (desk joystick / presets): relative pan, tilt, zoom target, speed */
    public String ptz(float dPan, float dTilt, float zoomTo, int spd) {
        if (type() != CamType.PTZ) {
            if (type() == CamType.FISHEYE) { yawOff = wrap(yawOff + dPan); controlsChanged(); return "§7Fisheye: digital pan " + Math.round(yawOff) + "°"; }
            return "§7CAM " + no + " is a fixed camera - no pan / tilt motors";
        }
        if (!powerOk()) return "§cCAM " + no + " has no power - the motors won't move";
        speed = spd;
        panT = wrap(panT + dPan);
        tiltT = Math.max(-5, Math.min(90, tiltT + dTilt));
        if (zoomTo > 0) zoomT = Math.max(1, Math.min(30, zoomTo));
        controlsChanged();
        return null;
    }

    public String preset(int n, boolean store) {
        if (n < 0 || n >= presets.length) return "§cPresets 0-8 (0 = home)";
        if (store) { presets[n] = new float[]{panT, tiltT, zoomT}; controlsChanged(); return "§aCAM " + no + " preset " + n + " stored"; }
        float[] p = n == 0 && presets[0] == null ? new float[]{0, 20, 1} : presets[n];
        if (p == null) return "§cCAM " + no + " has no preset " + n;
        if (type() != CamType.PTZ) return "§7CAM " + no + " is fixed";
        panT = p[0]; tiltT = p[1]; zoomT = p[2];
        controlsChanged();
        return "§aCAM " + no + " -> preset " + n;
    }

    /** a server ray-cast frame (Cctv's budget calls this): motion, tamper, recording */
    void capture(long now) {
        nextFrame = now + CctvConfig.analyticsTicks;
        if (!running()) { prevLuma = null; return; }
        double[] p = pose(false);
        Snap.Frame f = Snap.cast(world, p[0], p[1], p[2], (float) p[3], (float) p[4], (float) p[5], pos, Snap.parseMasks(masks));
        Cctv.LATEST.put(no, f);
        boolean cov = f.near > f.px.length * 0.8;
        if (cov != tamper) {
            tamper = cov;
            if (cov) {
                AlarmManager.get(world).raise(world, "cctv:tamper:" + no, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MAJOR, "CCTV TAMPER CAM " + no + " " + name + " - view obstructed", pos, true);
                CctvData.get(world).event(now, no, "TAMPER", "TAMPER CAM " + no);
                com.dogpound.railmap.alarm.Recorder.event(world, "CCTV camera " + no + " covered / obstructed");
                if (com.dogpound.railmap.api.QuestEvents.active()) com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "cctv_tamper", "camera", no);
            } else AlarmManager.get(world).clear("cctv:tamper:" + no);
        }
        motion(f, now);
        Cctv.record(this, f, now);
    }

    private void motion(Snap.Frame f, long now) {
        int[] l = new int[f.px.length];
        for (int i = 0; i < l.length; i++) l[i] = Snap.luma(f.px[i]);
        int[] prev = prevLuma;
        prevLuma = l;
        if (prev == null || prev.length != l.length || !motionOn || tamper) return;
        int thr = 60 - 5 * sens;
        double need = 0.26 - 0.02 * sens;
        int[] hit = new int[48], tot = new int[48];
        for (int j = 0; j < f.h; j++)
            for (int i = 0; i < f.w; i++) {
                int cell = (j * 6 / f.h) * 8 + i * 8 / f.w;
                if ((zones >> cell & 1) == 0) continue;
                int k = j * f.w + i;
                tot[cell]++;
                if (Math.abs(l[k] - prev[k]) > thr) hit[cell]++;
            }
        int cells = 0;
        for (int c = 0; c < 48; c++) if (tot[c] > 0 && hit[c] > tot[c] * need) cells++;
        if (cells == 0) return;
        CctvData d = CctvData.get(world);
        CctvData.Cam c = d.cam(no);
        if (c != null) { c.lastMotion = now; c.motion++; }
        if (now - lastMotionAlarm < 200) return;
        lastMotionAlarm = now;
        d.mark(no, now, "MOTION");
        d.event(now, no, "MOTION", "MOTION CAM " + no + " " + name);
        com.dogpound.railmap.alarm.Recorder.event(world, "CCTV motion: camera " + no + " (" + name + "), " + cells + " zone cells");
        if (CctvConfig.motionAlarms && d.armed(net()))
            AlarmManager.get(world).raise(world, "cctv:motion:" + no, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "CCTV MOTION CAM " + no + " " + name, pos, false);
        if (com.dogpound.railmap.api.QuestEvents.active()) com.dogpound.railmap.api.QuestEvents.fire(world, null, pos, "cctv_motion", "camera", no, "cells", cells);
        if (trigCam > 0) { TileCamera t = Cctv.camTile(world, trigCam); if (t != null) t.preset(trigPreset, false); }
        Cctv.callUp(world, net(), no, "MOTION");
    }

    // ---- controls ------------------------------------------------------------------------------------------------------
    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("bell")) return doorbell(who);
        return "§7" + wiredName() + ": " + gearState();
    }

    private String doorbell(EntityPlayer who) {
        if (!term.powered()) return "§7The doorbell is dead - no 12 V";
        long now = world.getTotalWorldTime();
        world.playSound(null, pos, ModSounds.DOOR_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
        CctvData d = CctvData.get(world);
        d.mark(no, now, "DOORBELL");
        d.event(now, no, "DOOR", "DOORBELL CAM " + no + " " + name + (who == null ? "" : " - " + who.getName()));
        AlarmManager.get(world).raise(world, "cctv:door:" + no, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "DOORBELL CAM " + no + " " + name, pos, false);
        if (com.dogpound.railmap.api.QuestEvents.active()) com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "cctv_doorbell", "camera", no);
        Cctv.callUp(world, net(), no, "DOOR");
        return "§bDing-dong - the CCTV desk has you on screen";
    }

    /** the configurator / GUI: "key=value;..." (server checks every value) */
    public String configure(String arg, EntityPlayer who) {
        for (String kv : arg.split(";")) {
            int e = kv.indexOf('=');
            if (e < 0) continue;
            String k = kv.substring(0, e), v = kv.substring(e + 1);
            try {
                switch (k) {
                    case "name" -> name = v.replaceAll("[^A-Za-z0-9 ./:+-]", "").trim().toUpperCase(Locale.ROOT);
                    case "yaw" -> yawOff = clamp(Float.parseFloat(v), -60, 60);
                    case "pitch" -> pitchOff = clamp(Float.parseFloat(v), -40, 60);
                    case "fov" -> fovSet = Float.parseFloat(v) <= 0 ? 0 : clamp(Float.parseFloat(v), 10, 150);
                    case "masks" -> masks = Snap.parseMasks(v).length == 0 ? "" : join(Snap.parseMasks(v));
                    case "zones" -> zones = Long.parseLong(v) & ((1L << 48) - 1);
                    case "sens" -> sens = (int) clamp(Integer.parseInt(v), 1, 10);
                    case "motion" -> motionOn = v.equals("1");
                    case "ir" -> irMode = (int) clamp(Integer.parseInt(v), 0, 2);
                    case "trig" -> { String[] a = v.split(","); trigCam = Integer.parseInt(a[0]); trigPreset = a.length > 1 ? Integer.parseInt(a[1]) : 1; }
                    case "focus" -> focus = (int) clamp(Integer.parseInt(v), 0, 3);
                    case "iris" -> iris = (int) clamp(Integer.parseInt(v), 0, 4);
                    case "ptz" -> { String[] a = v.split(","); ptz(Float.parseFloat(a[0]), Float.parseFloat(a[1]), 0, 1); }
                    case "zoomto" -> ptz(0, 0, Float.parseFloat(v), 1);
                    default -> { }
                }
            } catch (RuntimeException ignored) { }
        }
        if (name.length() > 24) name = name.substring(0, 24);
        controlsChanged();
        if (world != null) writeDir(CctvData.get(world), world.getTotalWorldTime());
        return "§aCAM " + no + " configured: " + name;
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    static String join(int[][] m) {
        StringBuilder b = new StringBuilder();
        for (int[] r : m) b.append(b.length() == 0 ? "" : ";").append(r[0]).append(',').append(r[1]).append(',').append(r[2]).append(',').append(r[3]);
        return b.toString();
    }

    // ---- save ---------------------------------------------------------------------------------------------------------
    @Override
    protected void saveMore(NBTTagCompound t) {
        t.setInteger("no", no); t.setString("name", name);
        t.setFloat("yawOff", yawOff); t.setFloat("pitchOff", pitchOff); t.setFloat("fovSet", fovSet);
        t.setFloat("pan", pan); t.setFloat("tilt", tilt); t.setFloat("zoom", zoom); t.setFloat("panT", panT); t.setFloat("tiltT", tiltT); t.setFloat("zoomT", zoomT);
        t.setInteger("speed", speed); t.setInteger("focus", focus); t.setInteger("iris", iris); t.setInteger("irMode", irMode);
        t.setString("masks", masks); t.setLong("zones", zones); t.setInteger("sens", sens); t.setBoolean("motionOn", motionOn);
        t.setInteger("trigCam", trigCam); t.setInteger("trigPre", trigPreset); t.setLong("wiped", wipedAt); t.setLong("recUntil", recUntil);
        t.setInteger("status", status); t.setString("what", what); t.setBoolean("irOn", ir);
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < presets.length; i++) if (presets[i] != null) {
            NBTTagCompound p = new NBTTagCompound(); p.setInteger("n", i); p.setFloat("p", presets[i][0]); p.setFloat("t", presets[i][1]); p.setFloat("z", presets[i][2]); l.appendTag(p);
        }
        t.setTag("presets", l);
    }

    @Override
    protected void loadMore(NBTTagCompound t) {
        no = t.getInteger("no"); name = t.getString("name");
        yawOff = t.getFloat("yawOff"); pitchOff = t.getFloat("pitchOff"); fovSet = t.getFloat("fovSet");
        pan = t.getFloat("pan"); tilt = t.getFloat("tilt"); zoom = Math.max(1, t.getFloat("zoom")); panT = t.getFloat("panT");
        tiltT = t.hasKey("tiltT") ? t.getFloat("tiltT") : -1000; zoomT = Math.max(1, t.getFloat("zoomT"));
        speed = t.getInteger("speed"); focus = t.getInteger("focus"); iris = t.getInteger("iris"); irMode = t.getInteger("irMode");
        masks = t.getString("masks"); zones = t.hasKey("zones") ? t.getLong("zones") : (1L << 48) - 1; sens = t.hasKey("sens") ? t.getInteger("sens") : 5;
        motionOn = !t.hasKey("motionOn") || t.getBoolean("motionOn");
        trigCam = t.getInteger("trigCam"); trigPreset = t.getInteger("trigPre"); wipedAt = t.getLong("wiped"); recUntil = t.getLong("recUntil");
        status = t.getInteger("status"); what = t.getString("what"); ir = t.getBoolean("irOn");
        java.util.Arrays.fill(presets, null);
        NBTTagList l = t.getTagList("presets", 10);
        for (int i = 0; i < l.tagCount(); i++) { NBTTagCompound p = l.getCompoundTagAt(i); int n = p.getInteger("n"); if (n >= 0 && n < presets.length) presets[n] = new float[]{p.getFloat("p"), p.getFloat("t"), p.getFloat("z")}; }
        if (tiltR < -500) { panR = pan; tiltR = tilt; zoomR = zoom; }
    }

    @Override
    public void invalidate() { super.invalidate(); Cctv.CAMS.remove(this); }

    @Override
    public void onChunkUnload() { super.onChunkUnload(); Cctv.CAMS.remove(this); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1); }
}
