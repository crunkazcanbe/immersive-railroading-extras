package com.dogpound.railmap.cctv;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;

/**
 * The CCTV control room console (three blocks wide, two tall): a video matrix keyboard and PTZ joystick on the desk,
 * a spot monitor and a 2 x 2 preview wall with live pictures on the bridge, alarm windows, gauges and the alarm list.
 *   keypad:  N CAM = camera N to the spot monitor;  M MON = the spot camera to wall monitor M;  N PRESET / N SET = recall /
 *            store PTZ preset N of the spot camera;  TOUR = cycle the network's cameras on the spot monitor
 *   joystick 8 ways + HOME, SPEED, ZOOM / FOCUS / IRIS knobs, WIPER, IR, REC (manual record + bookmark)
 *   CAMERA / MONITOR / SPLIT / TOUR knobs, ARM key (motion alarms), REC LOCK key (NVR footage lock)
 *   ACK / RESET / SILENCE / LAMP TEST for the CCTV alarms (motion, tamper, video loss, disk, power, NVR, door, network)
 */
public class TileCctvDesk extends CctvTile {
    public int spot, tourIdx;
    public final int[] quad = new int[4];
    private String entry = "";
    private int lastCam;
    private long tourAt, callUpAt;
    private boolean supplied;

    @Override protected int watts() { return 180; }
    @Override public String wiredName() { return "CCTV control desk"; }
    @Override public boolean running() { return term.powered() && supplied; }
    @Override public String gearState() { return running() ? "spot CAM " + spot + (ctl.get("k_arm") == 1 ? ", ARMED" : ", disarmed") : "dark - " + (term.powered() ? mains.what : "no 12 V"); }

    public List<CctvData.Cam> visible() { return Cctv.visible(CctvData.get(world), net()); }

    @Override
    protected void serverTick() {
        Cctv.DESKS.add(this);
        boolean own = !CctvConfig.needSupply || mainsOk();
        if (own != supplied) { supplied = own; controlsChanged(); }
        if (age % 20 != 5) return;
        long now = world.getTotalWorldTime();
        CctvData d = CctvData.get(world);
        List<CctvData.Cam> v = visible();
        // tour
        int per = new int[]{0, 5, 10, 30}[Math.max(0, Math.min(3, ctl.get("s_tour")))];
        if (running() && per > 0 && ctl.get("tour") == 1 && !v.isEmpty() && now - tourAt >= per * 20L && now - callUpAt > 600) {
            tourAt = now;
            tourIdx = (tourIdx + 1) % v.size();
            spot = v.get(tourIdx).no;
        }
        if (spot == 0 && !v.isEmpty()) spot = v.get(0).no;
        // the preview quad: the page of four the spot camera is on
        int at = 0;
        for (int i = 0; i < v.size(); i++) if (v.get(i).no == spot) at = i;
        int page = at / 4 * 4;
        for (int i = 0; i < 4; i++) quad[i] = page + i < v.size() ? v.get(page + i).no : 0;
        // armed state of this network follows the ARM key
        boolean armed = ctl.get("k_arm") == 1;
        if (d.armed(net()) != armed) { if (armed) d.disarmed.remove(net()); else d.disarmed.add(net()); d.changed(); }
        readouts(d, v, now);
        controlsChanged();
    }

    private void readouts(CctvData d, List<CctvData.Cam> v, long now) {
        int on = 0, rec = 0;
        for (CctvData.Cam c : v) { if (c.streams()) on++; if (c.rec) rec++; }
        put("g_online", v.isEmpty() ? 0 : on * 100 / v.size());
        put("g_rec", v.isEmpty() ? 0 : rec * 100 / v.size());
        int disk = 0;
        for (TileNvr n : Cctv.nvrs(world, net())) disk = Math.max(disk, n.diskPct());
        put("g_disk", disk);
        text("d_cams", on + "/" + v.size() + " CAMS ONLINE");
        int motion = 0;
        for (String[] e : d.events) if (e[2].equals("MOTION") && now - Long.parseLong(e[0]) < 24000 && onNet(d, e)) motion++;
        text("d_motion", "MOTION " + motion + " TODAY " + (ctl.get("k_arm") == 1 ? "ARMED" : "DISARMED"));
        int k = 0;
        for (String[] e : d.events) {
            if (k >= 4) break;
            if (!onNet(d, e)) continue;
            text("d_alarm" + (++k), clock(Long.parseLong(e[0])) + " " + e[3]);
        }
        for (; k < 4; ) text("d_alarm" + (++k), "");
        text("d_clock", "DAY " + (world.getWorldTime() / 24000 + 1) + " " + clock(world.getWorldTime()));
        CctvData.Cam s = d.cam(spot);
        text("d_spot", s == null ? "NO CAMERA" : "CAM " + s.no + " " + s.name);
        put("led_spot_live", s != null && s.streams());
        put("led_spot_rec", s != null && s.rec);
        text("d_quad", "QUAD CAMS " + (quad[0] == 0 ? "-" : quad[0] + "-" + max(quad)));
        text("entry", entry.isEmpty() ? "READY" : entry);
        put("led_pwr", running());
        // alarm windows
        AlarmManager am = AlarmManager.get(world);
        String[][] tiles = {{"a_motion", "cctv:motion:"}, {"a_tamper", "cctv:tamper:"}, {"a_loss", "cctv:loss:"}, {"a_door", "cctv:door:"}};
        for (String[] t : tiles) {
            int seq = 0;
            for (AlarmManager.Alarm a : am.all()) if (a.key.startsWith(t[1]) && camOnNet(d, a.key)) seq = best(seq, a.seq(now));
            put(t[0], seq);
        }
        int dk = 0;
        for (AlarmManager.Alarm a : am.all()) if (a.key.startsWith("cctv:disk:")) dk = best(dk, a.seq(now));
        put("a_disk", dk);
        int pw = 0, nvr = 0;
        for (CctvData.Cam c : v) if (c.status == CctvData.OFF || c.status == CctvData.NO_SUPPLY) pw = 2;
        if (Cctv.nvrs(world, net()).isEmpty() || Cctv.nvrs(world, net()).stream().anyMatch(n -> !n.running())) nvr = 1;
        put("a_power", pw);
        put("a_nvr", nvr);
        put("a_net", net() == 0 && CctvConfig.requireNetwork ? 1 : 0);
    }

    /** the more urgent of two annunciator states (new > cleared-unreset > acknowledged > off) */
    private static int best(int a, int b) { return rank(b) > rank(a) ? b : a; }
    private static int rank(int s) { return s == 1 ? 3 : s == 3 ? 2 : s == 2 ? 1 : 0; }
    private static int max(int[] a) { int m = 0; for (int x : a) m = Math.max(m, x); return m; }

    private boolean onNet(CctvData d, String[] e) {
        CctvData.Cam c = d.cam(Integer.parseInt(e[1]));
        return c == null || Cctv.canSee(c, net());
    }

    private boolean camOnNet(CctvData d, String key) {
        try { CctvData.Cam c = d.cam(Integer.parseInt(key.substring(key.lastIndexOf(':') + 1))); return c == null || Cctv.canSee(c, net()); }
        catch (NumberFormatException e) { return false; }
    }

    static String clock(long t) {
        long day = (t + 6000) % 24000;
        return String.format(Locale.ROOT, "%02d:%02d", day / 1000, day % 1000 * 60 / 1000);
    }

    /** a motion / doorbell call-up: the spot monitor jumps to that camera (armed desks only for motion) */
    void callUp(int cam, String why) {
        if (!running()) return;
        if (why.equals("MOTION") && ctl.get("k_arm") != 1) return;
        spot = cam;
        callUpAt = world.getTotalWorldTime();
        world.playSound(null, pos, why.equals("DOOR") ? ModSounds.PA_CHIME : ModSounds.BUZZER, SoundCategory.BLOCKS, 0.6f, 1f);
        controlsChanged();
    }

    // ---- controls ----------------------------------------------------------------------------------------------------
    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("k_")) {                                                // keys turn with no power
            int v = (ctl.get(id) + 1) % 2;
            ctl.set(id, v);
            if (id.equals("k_lock")) for (TileNvr n : Cctv.nvrs(world, net())) { n.controls().set("k_lock", v); n.controlsChanged(); }
            return id.equals("k_arm") ? (v == 1 ? "§cCCTV ARMED - motion raises alarms" : "§aCCTV disarmed") : (v == 1 ? "§eNVR footage LOCKED" : "§7NVR footage unlocked");
        }
        if (!running()) return "§7The desk is dark - " + (term.powered() ? mains.what : "no 12 V");
        CctvData d = CctvData.get(world);
        List<CctvData.Cam> v = visible();
        TileCamera cam = Cctv.camTile(world, spot);
        int speed = Math.max(0, Math.min(2, ctl.get("s_speed")));
        float step = new float[]{3, 8, 20}[speed];
        if (id.length() == 2 && id.charAt(0) == 'n' && Character.isDigit(id.charAt(1))) {
            if (entry.length() < 4) entry += id.charAt(1);
            PackSounds.play(world, pos, "ui.keypad", 0.5f, 1f);
            return "§7" + entry;
        }
        String r;
        switch (id) {
            case "clr": entry = ""; return "§7Cleared";
            case "cam": case "ent": {
                int n = num();
                CctvData.Cam c = d.cam(n);
                if (c == null || !Cctv.canSee(c, net())) return "§cNo camera " + n + " on this network";
                spot = n; lastCam = n; callUpAt = world.getTotalWorldTime();
                return "§aCAM " + n + " " + c.name + " on the spot monitor";
            }
            case "mon": {
                int m = num();
                TileMonitor mon = Cctv.monitor(world, m);
                if (mon == null) return "§cNo monitor " + m + " (monitor numbers show on their bezel)";
                if (mon.net() != net() && CctvConfig.requireNetwork) return "§cMonitor " + m + " is on another network";
                mon.op("split", "1");
                mon.op("cam", "0," + spot);
                return "§aCAM " + spot + " -> MON " + m;
            }
            case "pre": case "set": {
                if (cam == null) return "§cThe spot camera isn't here";
                return cam.preset(num(), id.equals("set"));
            }
            case "tour": ctl.set("tour", ctl.get("tour") == 1 ? 0 : 1); if (ctl.get("s_tour") == 0) ctl.set("s_tour", 2); return ctl.get("tour") == 1 ? "§aTour running" : "§7Tour stopped";
            case "j_up": r = ptz(cam, 0, -step); break;
            case "j_dn": r = ptz(cam, 0, step); break;
            case "j_l": r = ptz(cam, -step, 0); break;
            case "j_r": r = ptz(cam, step, 0); break;
            case "j_ul": r = ptz(cam, -step, -step); break;
            case "j_ur": r = ptz(cam, step, -step); break;
            case "j_dl": r = ptz(cam, -step, step); break;
            case "j_dr": r = ptz(cam, step, step); break;
            case "j_home": { String dr = cam == null ? com.dogpound.railmap.drone.Drones.deskHome(world, spot) : null; r = dr != null ? dr : cam == null ? "§cNo camera" : cam.preset(0, false); break; }   // a drone: return home
            case "s_speed": ctl.set(id, (ctl.get(id) + 1) % 3); return "§7Joystick speed " + new String[]{"SLOW", "MED", "FAST"}[ctl.get(id)];
            case "s_zoom": {
                ctl.set(id, (ctl.get(id) + 1) % 6);
                float z = new float[]{1, 2, 4, 8, 16, 30}[ctl.get(id)];
                if (cam == null) { String dr = com.dogpound.railmap.drone.Drones.deskZoom(world, spot, ctl.get(id)); return dr != null ? dr : "§cNo camera"; }   // a drone: height band
                if (cam.type() != CamType.PTZ) return "§7CAM " + spot + " has a fixed lens";
                String q = cam.ptz(0, 0, z, speed);
                return q != null ? q : "§aZoom " + (int) z + "x";
            }
            case "s_focus": ctl.set(id, (ctl.get(id) + 1) % 4); if (cam != null) cam.configure("focus=" + ctl.get(id), who); return "§7Focus " + new String[]{"AUTO", "NEAR", "MID", "FAR"}[ctl.get(id)];
            case "s_iris": ctl.set(id, (ctl.get(id) + 1) % 5); if (cam != null) cam.configure("iris=" + ctl.get(id), who); return "§7Iris " + new String[]{"AUTO", "F1.4", "F2.8", "F5.6", "F11"}[ctl.get(id)];
            case "wiper": if (cam == null) return "§cNo camera"; cam.wipedAt = world.getTotalWorldTime(); PackSounds.play(world, cam.getPos(), "machine.servo", 0.5f, 0.7f); return "§7Wiper";
            case "irbtn": if (cam == null) return "§cNo camera"; cam.configure("ir=" + (cam.irMode + 1) % 3, who); return "§7IR " + new String[]{"AUTO", "ON", "OFF"}[cam.irMode];
            case "rec": {
                if (cam == null) return "§cNo camera";
                cam.recUntil = world.getTotalWorldTime() + 1200;
                d.mark(spot, world.getTotalWorldTime(), "OPERATOR MARK" + (who == null ? "" : " " + who.getName()));
                return "§cRecording CAM " + spot + " for 60 s + bookmark";
            }
            case "s_cam": {
                ctl.set(id, (ctl.get(id) + 1) % 12);
                int i = ctl.get(id);
                if (i >= v.size()) return "§7Camera knob " + (i + 1) + ": nothing there (" + v.size() + " cameras)";
                spot = v.get(i).no; callUpAt = world.getTotalWorldTime();
                return "§aCAM " + spot + " " + v.get(i).name;
            }
            case "s_mon": ctl.set(id, (ctl.get(id) + 1) % 8); return "§7Monitor " + (ctl.get(id) + 1) + " selected (SPLIT knob / M MON)";
            case "s_split": {
                ctl.set(id, (ctl.get(id) + 1) % 3);
                int s = new int[]{1, 4, 9}[ctl.get(id)];
                TileMonitor mon = Cctv.monitor(world, ctl.get("s_mon") + 1);
                if (mon == null) return "§7Split " + s + " - no monitor " + (ctl.get("s_mon") + 1);
                mon.op("split", String.valueOf(s));
                for (int i = 0; i < s; i++) mon.op("cam", i + "," + (i < v.size() ? v.get(i).no : 0));
                return "§aMON " + (ctl.get("s_mon") + 1) + " split " + s;
            }
            case "s_tour": ctl.set(id, (ctl.get(id) + 1) % 4); return "§7Tour dwell " + new String[]{"OFF", "5 s", "10 s", "30 s"}[ctl.get(id)];
            case "ack": case "reset": case "silence": {
                AlarmManager am = AlarmManager.get(world);
                if (id.equals("silence")) { am.silence(); return "§7Horn silenced"; }
                for (AlarmManager.Alarm a : am.all()) if (a.key.startsWith("cctv:") && !a.acked && (a.key.startsWith("cctv:disk:") || camOnNet(d, a.key))) {
                    if (id.equals("ack") || !a.active) am.ack(a.id, who == null ? "console" : who.getName());
                }
                world.playSound(null, pos, ModSounds.ACK_BEEP, SoundCategory.BLOCKS, 0.5f, 1f);
                return id.equals("ack") ? "§eCCTV alarms acknowledged" : "§aCleared CCTV alarms reset";
            }
            case "test": return lampTest();
            default: return null;
        }
        return r == null ? "§7PTZ CAM " + spot : r;
    }

    private String ptz(TileCamera cam, float dp, float dt) {
        if (cam == null) { String dr = com.dogpound.railmap.drone.Drones.deskStick(world, spot, dp, dt); if (dr != null) return dr; }   // a drone on the spot monitor: the stick flies it
        return cam == null ? "§cThe spot camera isn't here" : cam.ptz(dp, dt, 0, Math.max(0, Math.min(2, ctl.get("s_speed"))));
    }

    private int num() {
        int n;
        try { n = entry.isEmpty() ? lastCam : Integer.parseInt(entry); } catch (NumberFormatException e) { n = 0; }
        entry = "";
        return n;
    }

    @Override
    protected void saveMore(NBTTagCompound t) {
        t.setInteger("spot", spot); t.setIntArray("quad", quad); t.setString("entry", entry); t.setBoolean("sup", supplied); t.setInteger("tourIdx", tourIdx);
    }

    @Override
    protected void loadMore(NBTTagCompound t) {
        spot = t.getInteger("spot");
        int[] q = t.getIntArray("quad");
        System.arraycopy(q, 0, quad, 0, Math.min(4, q.length));
        entry = t.getString("entry"); supplied = t.getBoolean("sup"); tourIdx = t.getInteger("tourIdx");
    }

    @Override public void invalidate() { super.invalidate(); Cctv.DESKS.remove(this); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 2, 2); }

    public BlockPos at() { return pos; }
}
