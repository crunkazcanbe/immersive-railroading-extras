package com.dogpound.railmap.drone;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.cctv.CamType;
import com.dogpound.railmap.cctv.CctvData;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.weather.Weather;
import com.dogpound.railmap.weather.WeatherData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Drone plumbing shared by the dock, the entity, the CCTV desk and the console: the live drones on each side (so a
 * CCTV camera number finds its drone), the weather a drone flies in, station no-fly zones, the console's Drones and
 * Drone Findings pages and their buttons, and the desk joystick hooks.
 */
public final class Drones {
    private Drones() {}

    private static final Set<EntityDrone> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    static void track(EntityDrone d) { LIVE.add(d); }

    /** the live drone with this entity id string in this world (server or client), or null */
    public static EntityDrone byUuid(World w, String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        for (EntityDrone d : LIVE) if (d.world == w && !d.isDead && d.getUniqueID().toString().equals(uuid)) return d;
        return null;
    }

    /** CCTV: a drone camera's pose {x, y, z, yaw, pitch}, or null when no drone with that id is here (TrainCams hook) */
    public static double[] camPose(World w, String uuid, float pt) {
        EntityDrone d = byUuid(w, uuid);
        return d == null ? null : d.camPose(pt);
    }

    /** the drone flying as CCTV camera `no`, or null */
    public static EntityDrone byCam(World w, int no) {
        CctvData.Cam c = CctvData.get(w).cam(no);
        return c == null || c.kind() != CamType.DRONE ? null : byUuid(w, c.train);
    }

    public static List<EntityDrone> all(World w) {
        List<EntityDrone> o = new ArrayList<>();
        for (EntityDrone d : LIVE) if (d.world == w && !d.isDead) o.add(d);
        o.sort(Comparator.comparingInt(d -> d.camNo));
        return o;
    }

    // ---- CCTV ------------------------------------------------------------------------------------------------------------
    /** the flying drone becomes CCTV camera `no` (keeps the dock's number when it is free) */
    static int registerCam(World w, EntityDrone d, int want) {
        CctvData cd = CctvData.get(w);
        int no = want > 0 && (cd.cam(want) == null || cd.cam(want).kind() == CamType.DRONE) ? want : cd.allocCam();
        CctvData.Cam c = new CctvData.Cam();
        c.no = no;
        c.type = CamType.DRONE.ordinal();
        c.train = d.getUniqueID().toString();
        c.dim = w.provider.getDimension();
        c.name = ("DRONE " + d.droneName()).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ./:+-]", "");
        c.fov = CamType.DRONE.fov;
        c.status = CctvData.ONLINE;
        c.x = d.posX; c.y = d.posY; c.z = d.posZ;
        cd.cams.put(no, c);
        cd.changed();
        return no;
    }

    static void unregisterCam(World w, EntityDrone d) {
        CctvData cd = CctvData.get(w);
        CctvData.Cam c = cd.cam(d.camNo);
        if (c != null && c.train.equals(d.getUniqueID().toString())) { cd.cams.remove(d.camNo); cd.changed(); }
    }

    // ---- weather ---------------------------------------------------------------------------------------------------------
    /** {wind m/s, direction radians}: railway weather (Weather2 when installed), else the world's rain */
    static double[] wind(World w, BlockPos p) {
        WeatherData.Cell c = cell(w, p);
        double ms = c != null ? c.wind : w.isThundering() ? 18 : w.isRaining() ? 9 : 3;
        double dir = (w.getTotalWorldTime() / 12000.0 + (p.getX() >> 8) * 0.37 + (p.getZ() >> 8) * 0.11) % (Math.PI * 2);
        return new double[]{ms, dir};
    }

    /** the railway weather cell over p if the weather model already knows it (a drone never seeds a cell at its height) */
    static WeatherData.Cell cell(World w, BlockPos p) {
        if (!Weather.on()) return null;
        WeatherData.Cell c = WeatherData.get(w).cells.get(WeatherData.key(p.getX() >> 6, p.getZ() >> 6));
        return c == null || c.lastTick == 0 ? null : c;
    }

    /** why a drone may not fly here now, or null */
    public static String grounded(World w, BlockPos p) {
        if (!DroneConfig.drones) return "drones are switched off (config)";
        double wind = wind(w, p)[0];
        if (wind > DroneConfig.maxWindMs) return String.format(Locale.ROOT, "WIND %.0f m/s (limit %.0f)", wind, DroneConfig.maxWindMs);
        if (!DroneConfig.rainGrounds) return null;
        if (w.isThundering() && w.isRaining()) return "THUNDERSTORM";
        WeatherData.Cell c = cell(w, p);
        if (c != null) {
            if (c.snowing && c.precip > 0) return "SNOW";
            if (c.precip >= 2) return "HEAVY RAIN";
        } else if (w.isRainingAt(p.up(3)) && w.getBiome(p).getTemperature(p) < 0.15f) return "SNOW";
        return null;
    }

    /** the height a drone at (x, y, z) must keep over a crowd at a station, NaN = no restriction */
    static double noFlyFloor(World w, double x, double y, double z) {
        int r = DroneConfig.noFlyRadius;
        if (r <= 0) return Double.NaN;
        boolean station = false;
        for (Long k : StationData.get(w).names().keySet()) {
            BlockPos s = BlockPos.fromLong(k);
            if ((s.getX() - x) * (s.getX() - x) + (s.getZ() - z) * (s.getZ() - z) < (double) r * r) { station = true; break; }
        }
        if (!station) return Double.NaN;
        double floor = Double.NaN;
        for (EntityPlayer p : w.playerEntities) {
            if (p.isSpectator()) continue;
            double dx = p.posX - x, dz = p.posZ - z;
            if (dx * dx + dz * dz < 12 * 12 && p.posY <= y + 2) floor = Double.isNaN(floor) ? p.posY + DroneConfig.noFlyClearance : Math.max(floor, p.posY + DroneConfig.noFlyClearance);
        }
        return floor;
    }

    public static String statusLine(EntityDrone d) {
        return String.format(Locale.ROOT, "§bDRONE %s§7 · %s · §%s%.0f%%§7 · %.0f m up · %.1f m/s · wind %.0f m/s · %.0f W%s",
                d.droneName(), EntityDrone.MODES[d.mode()], d.battery() < 25 ? "c" : d.battery() < 50 ? "e" : "a", d.battery(), d.posY - d.dock.getY(),
                Math.sqrt(d.motionX * d.motionX + d.motionZ * d.motionZ) * 20, d.windMs, d.loadW, d.note.isEmpty() ? "" : " · §e" + d.note);
    }

    // ---- the CCTV desk joystick (TileCctvDesk hooks): null = the spot camera isn't a drone ---------------------------------
    public static String deskStick(World w, int cam, float dp, float dt) {
        EntityDrone d = byCam(w, cam);
        if (d == null) return null;
        double step = Math.max(Math.abs(dp), Math.abs(dt)) / 2.0;
        return d.nudge(dt < 0 ? step : dt > 0 ? -step : 0, 0, 0, dp);
    }

    public static String deskHome(World w, int cam) {
        EntityDrone d = byCam(w, cam);
        if (d == null) return null;
        d.rth("HOME pressed at the CCTV desk");
        return "§e" + d.droneName() + " returning home";
    }

    /** the desk's ZOOM knob flies a drone up / down a height band; position 0..5 */
    public static String deskZoom(World w, int cam, int pos) {
        EntityDrone d = byCam(w, cam);
        if (d == null) return null;
        double[] heights = {4, 8, 15, 30, 50, 80};
        double want = d.dock.getY() + heights[Math.max(0, Math.min(5, pos))];
        String r = d.nudge(0, 0, want - d.posY, 0);
        d.gimbal(pos <= 1 ? 30 : pos <= 3 ? 55 : 80);
        return r + " §7(height band " + Math.round(heights[Math.max(0, Math.min(5, pos))]) + " m)";
    }

    // ---- console (CCTV tab pages; rows like CctvConsole's) ---------------------------------------------------------------
    public static void console(World w, List<String[]> o) {
        o.add(new String[]{"PAGE", "Drones", "inspection drones: docks, flights, battery, wind - click one for its live picture"});
        o.add(new String[]{"COLS", "CAM:0", "DRONE:7", "STATE:22", "BAT:40", "ALT:48", "WIND:55", "MISSION:63", "FOUND:78", "DOCK:86"});
        DroneData dd = DroneData.get(w);
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(t instanceof TileDroneDock k) || t.isInvalid()) continue;
            EntityDrone d = k.drone();
            DroneData.Flight f = d == null ? null : dd.flight(d.flight);
            String state = d == null ? (k.flying() ? "~OUT OF RANGE" : k.ready() == null ? "+READY" : "." + k.ready()) : (d.mode() == EntityDrone.RTH || d.mode() == EntityDrone.LANDED_OUT ? "!" : "+") + EntityDrone.MODES[d.mode()];
            o.add(new String[]{"ROW", String.valueOf(d == null ? k.camNo : d.camNo), d == null ? "-" : String.valueOf(d.camNo), k.serial(), state,
                    d == null ? k.bayLine() : String.format(Locale.ROOT, "%s%.0f%%", d.battery() < 25 ? "!" : "", d.battery()),
                    d == null ? "-" : Math.round(d.posY - d.dock.getY()) + " m", d == null ? "-" : Math.round(d.windMs) + " m/s",
                    DroneData.MISSIONS[Math.max(0, Math.min(3, d == null ? k.missionSel() : d.mission))] + (d == null ? "" : String.format(Locale.ROOT, " %.0f m", d.metres)),
                    f == null ? "-" : (f.findings > 0 ? "~" : "") + f.findings, t.getPos().getX() + "," + t.getPos().getZ()});
        }
        o.add(new String[]{"BTN", "LAUNCH", "dlaunch", "1"});
        o.add(new String[]{"BTN", "RETURN HOME", "drth", "1"});
        o.add(new String[]{"BTN", "HOLD", "dhold", "1"});
        o.add(new String[]{"BTN", "RESUME", "dresume", "1"});
        o.add(new String[]{"TXT", "Docks need 12 V + a 120 / 240 V supply to charge; the drone streams as a CCTV camera over LTE. Fly it from the CCTV desk joystick (HOME = return, ZOOM = height) or a handheld controller."});
        for (int i = 0; i < Math.min(8, dd.log.size()); i++) o.add(new String[]{"TXT", dd.log.get(i)});

        o.add(new String[]{"PAGE", "Drone findings", "defects the drones found - each is a work order until closed"});
        o.add(new String[]{"COLS", "ID:0", "AGE:6", "KIND:16", "STATE:30", "FINDING:40"});
        long now = w.getTotalWorldTime();
        int n = 0;
        for (DroneData.Finding f : dd.findings) {
            if (n++ > 80) break;
            o.add(new String[]{"ROW", "F" + f.id, String.valueOf(f.id), (now - f.at) / 1200 + " min", f.kind, f.state == 0 ? (f.major ? "!OPEN" : "~OPEN") : f.state == 1 ? ".ACKED" : "+CLOSED", f.text});
        }
        o.add(new String[]{"BTN", "ACK", "fack", "1"});
        o.add(new String[]{"BTN", "CLOSE", "fclose", "1"});
        o.add(new String[]{"BTN", "ACK ALL", "fackall", "0"});
        o.add(new String[]{"PAGE", "Flights", "every drone flight: mission, distance, lowest battery, findings, how it ended"});
        o.add(new String[]{"COLS", "#:0", "DRONE:6", "MISSION:18", "M:32", "MIN BAT:40", "FOUND:50", "RESULT:58"});
        for (DroneData.Flight f : dd.flights)
            o.add(new String[]{"ROW", "", String.valueOf(f.id), f.drone, DroneData.MISSIONS[Math.max(0, Math.min(3, f.mission))], String.valueOf(Math.round(f.metres)),
                    Math.round(f.minBattery) + "%", String.valueOf(f.findings), f.end == 0 ? "~IN FLIGHT" : f.result.startsWith("landed") ? "+" + f.result : "!" + f.result});
    }

    /** a console button: "action|selected row key" (camera no, or F<finding id>); null = not ours */
    public static String op(World w, String[] a, EntityPlayer p) {
        String key = a.length > 1 ? a[1] : "";
        switch (a[0]) {
            case "dlaunch": case "drth": case "dhold": case "dresume": {
                int cam;
                try { cam = Integer.parseInt(key); } catch (NumberFormatException e) { return "§cPick a drone"; }
                for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
                    if (t instanceof TileDroneDock k && (k.camNo == cam || k.drone() != null && k.drone().camNo == cam)) return k.remote(a[0].substring(1), p);
                return "§cNo dock for CAM " + cam;
            }
            case "fack": case "fclose": {
                DroneData d = DroneData.get(w);
                DroneData.Finding f = key.startsWith("F") ? d.finding(parse(key.substring(1))) : null;
                if (f == null) return "§cPick a finding";
                f.state = a[0].equals("fack") ? Math.max(f.state, 1) : 2;
                if (f.state == 2) AlarmManager.get(w).clear("dronewo:" + f.id);
                d.markDirty();
                return f.state == 2 ? "§aFinding " + f.id + " closed (work order done)" : "§eFinding " + f.id + " acknowledged";
            }
            case "fackall": {
                DroneData d = DroneData.get(w);
                int k = 0;
                for (DroneData.Finding f : d.findings) if (f.state == 0) { f.state = 1; k++; }
                d.markDirty();
                return "§e" + k + " drone finding(s) acknowledged";
            }
            default: return null;
        }
    }

    static int parse(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; } }
}
