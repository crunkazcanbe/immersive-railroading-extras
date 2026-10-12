package com.dogpound.railmap.cctv;

import com.dogpound.railmap.alarm.AlarmManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * The Control Center's CCTV tab, as rows (PacketRailState.cctv): PAGE / COLS / ROW (key = camera no) / BTN / TXT like the
 * other generic pages; the client page adds a live (or snapshot) preview of the selected camera and thumbnails.
 */
public final class CctvConsole {
    private CctvConsole() {}

    public static void fill(World w, List<String[]> o) {
        CctvData d = CctvData.get(w);
        long now = w.getTotalWorldTime();
        int on = 0;
        for (CctvData.Cam c : d.cams.values()) if (c.streams()) on++;
        o.add(new String[]{"PAGE", "Cameras", "every CCTV camera: status, network, recording, motion - click one for its picture"});
        o.add(new String[]{"COLS", "CAM:0", "NAME:7", "TYPE:30", "STATUS:44", "NET:60", "REC:72", "MOTION:79", "WHERE:88"});
        for (CctvData.Cam c : d.cams.values()) {
            String st = c.streams() ? (c.status == CctvData.TAMPER ? "!TAMPER" : "+ONLINE") : "!" + CctvData.STATUS[c.status];
            String where = c.kind() == CamType.DRONE ? "airborne " + (int) c.x + "," + (int) c.z + " " + c.what : c.train.isEmpty() ? (c.pos == 0 ? "" : net.minecraft.util.math.BlockPos.fromLong(c.pos).toString().replace("BlockPos", "")) : "on a train";
            o.add(new String[]{"ROW", String.valueOf(c.no), String.valueOf(c.no), c.name, c.kind().label, st, c.kind() == CamType.TRAIN || c.kind() == CamType.DRONE ? "LTE" : c.net == 0 ? ".none" : Long.toHexString(c.net & 0xFFFFFF).toUpperCase(Locale.ROOT),
                    c.rec ? "~REC" : ".-", c.lastMotion > 0 && now - c.lastMotion < 1200 ? "~" + (now - c.lastMotion) / 20 + "s ago" : ".-", where});
        }
        o.add(new String[]{"BTN", "MARK", "mark", "1"});
        o.add(new String[]{"BTN", "ACK CAM", "ack", "1"});
        o.add(new String[]{"BTN", "ARM ALL", "arm", "0"});
        o.add(new String[]{"BTN", "DISARM ALL", "disarm", "0"});
        o.add(new String[]{"TXT", on + " of " + d.cams.size() + " cameras online. " + (d.disarmed.isEmpty() ? "All networks armed." : d.disarmed.size() + " network(s) disarmed.")});
        o.add(new String[]{"TXT", "Cameras need 12 V + PoE / mains and a wire run to an NVR. Configure with the CCTV Configurator; drive them from a CCTV desk."});

        o.add(new String[]{"PAGE", "Events", "motion, tamper, video loss and doorbell events (newest first)"});
        o.add(new String[]{"COLS", "TIME:0", "CAM:14", "KIND:24", "EVENT:38"});
        int i = 0;
        for (String[] e : d.events) {
            if (i++ > 60) break;
            long t = Long.parseLong(e[0]);
            o.add(new String[]{"ROW", e[1], (now - t) / 20 / 60 + " min ago", e[1], e[2].equals("MOTION") ? "~MOTION" : e[2].equals("DOOR") ? "+DOOR" : "!" + e[2], e[3]});
        }
        o.add(new String[]{"BTN", "ACK CAM", "ack", "1"});

        o.add(new String[]{"PAGE", "Recorders", "NVRs: cameras, drives, recording mode, disk use, PoE"});
        o.add(new String[]{"COLS", "NVR:0", "STATE:30", "CAMS:62", "DISK:72", "TB:84"});
        for (TileNvr n : Cctv.NVRS) {
            if (n.getWorld() != w || n.isInvalid()) continue;
            o.add(new String[]{"ROW", "", n.getPos().toString().replace("BlockPos", ""), n.running() ? "+" + n.gearState() : "!" + n.gearState(), String.valueOf(n.cameras().size()), n.diskPct() + "%",
                    String.valueOf(n.capacity() / Math.max(1, CctvConfig.framesPerTB))});
        }
        o.add(new String[]{"PAGE", "Monitors", "wall + platform monitors and desks"});
        o.add(new String[]{"COLS", "MON:0", "SHOWS:12", "STATE:40", "WHERE:72"});
        for (TileEntity t : w.loadedTileEntityList) {
            if (t instanceof TileMonitor m && m.isMaster())
                o.add(new String[]{"ROW", "", String.valueOf(m.no), (m.mode == 1 ? "PLAYBACK CAM " : "CAM ") + m.ch[0] + (m.split > 1 ? " +" + (m.split - 1) : ""), m.running() ? "+on" : "!" + m.gearState(), t.getPos().toString().replace("BlockPos", "")});
            if (t instanceof TileCctvDesk k)
                o.add(new String[]{"ROW", String.valueOf(k.spot), "DESK", "SPOT CAM " + k.spot, k.running() ? "+" + k.gearState() : "!" + k.gearState(), t.getPos().toString().replace("BlockPos", "")});
        }
        try { com.dogpound.railmap.drone.Drones.console(w, o); } catch (RuntimeException | LinkageError e) { com.dogpound.railmap.RailMap.LOG.debug("[IR Extras] drone console rows failed: {}", e.toString()); }   // IR Extras #2: drones, findings, flights
    }

    /** a console button: "action|selected camera" */
    public static String op(World w, String arg, EntityPlayer p) {
        String[] a = arg.split("\\|", -1);
        CctvData d = CctvData.get(w);
        int cam;
        try { cam = a.length > 1 && !a[1].isEmpty() ? Integer.parseInt(a[1]) : 0; } catch (NumberFormatException e) { cam = 0; }
        switch (a[0]) {
            case "mark":
                if (d.cam(cam) == null) return "§cPick a camera";
                d.mark(cam, w.getTotalWorldTime(), "CONSOLE MARK " + p.getName());
                return "§aBookmark on CAM " + cam;
            case "ack": {
                AlarmManager am = AlarmManager.get(w);
                for (AlarmManager.Alarm x : am.all()) if (x.key.startsWith("cctv:") && x.key.endsWith(":" + cam) && !x.acked) am.ack(x.id, p.getName());
                return "§eCAM " + cam + " alarms acknowledged";
            }
            case "arm": d.disarmed.clear(); d.changed(); return "§cAll CCTV networks armed";
            case "disarm":
                for (CctvData.Cam c : d.cams.values()) d.disarmed.add(c.net);
                d.changed();
                return "§aAll CCTV networks disarmed";
            default: { String r = com.dogpound.railmap.drone.Drones.op(w, a, p); return r == null ? "§7?" : r; }   // drone buttons
        }
    }
}
