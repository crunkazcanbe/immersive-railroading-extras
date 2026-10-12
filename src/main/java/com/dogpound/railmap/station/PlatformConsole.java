package com.dogpound.railmap.station;

import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.stn.StnConsole;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** STN tab pages for the platform gear (ChatGPT batch 3 #27 #29 #32 #33); row key = the block's position */
public final class PlatformConsole {
    private PlatformConsole() {}

    public static void fill(World w, List<String[]> out) {
        StnConsole.page(out, "Crowds", "Platform crowd monitors: density, Fruin level of service, queues, crowd measures (#27)");
        StnConsole.cols(out, "Station:0", "Area:16", "People:27", "p/m2:35", "LOS:42", "In/min:48", "Gate Q:56", "TVM Q:64", "Measures:72");
        for (TileCrowdMonitor m : PlatformTile.loaded(w, TileCrowdMonitor.class)) StnConsole.row(out, String.valueOf(m.getPos().toLong()), m.row());
        StnConsole.btn(out, "METER", "plat.crowd.s_meter", true);
        StnConsole.btn(out, "ENTRANCE", "plat.crowd.k_close", true);
        StnConsole.btn(out, "DWELL", "plat.crowd.k_dwell", true);
        StnConsole.btn(out, "PA", "plat.crowd.pa", true);
        StnConsole.txt(out, "LOS (m2 a person): A 1.2+  B 0.9  C 0.65  D 0.28  E 0.19  F less. E = minor alarm, F = major.");

        StnConsole.page(out, "Zones", "Platform zone markers and the next train's predicted stopping zones (#29)");
        StnConsole.cols(out, "Station:0", "Zone:25", "Access:33", "Stop:45", "Now:58");
        for (TileZoneMarker m : PlatformTile.loaded(w, TileZoneMarker.class)) StnConsole.row(out, String.valueOf(m.getPos().toLong()), m.row());
        StnConsole.btn(out, "ANNOUNCE", "plat.zones.ann", true);
        for (TileZoneMarker m : PlatformTile.loaded(w, TileZoneMarker.class)) {
            TileZoneMarker.Pred p = TileZoneMarker.pred(m.station());
            if (p != null && TileZoneMarker.plan(w, m.station()).get(0) == m) StnConsole.txt(out, m.stationName() + ": " + p.text);
        }

        StnConsole.page(out, "Doors", "Platform door safety: platform length, selective door opening, overruns (#32)");
        StnConsole.cols(out, "Station:0", "Mode:20", "Platform:40", "Train:50", "In:60", "State:68", "Withheld:84");
        for (TileDoorController c : PlatformTile.loaded(w, TileDoorController.class)) StnConsole.row(out, String.valueOf(c.getPos().toLong()), c.row());
        StnConsole.btn(out, "MODE", "plat.doors.s_mode", true);
        StnConsole.btn(out, "RESET", "plat.doors.reset", true);
        StnConsole.btn(out, "ANNOUNCE", "plat.doors.ann", true);

        StnConsole.page(out, "Security", "Station opening hours, night mode, gates and the intruder alarm (#33)");
        StnConsole.cols(out, "Station:0", "Now:18", "Hours:28", "Mode:42", "Alarm:52", "Intruders:62", "Staff in:72", "Lamps:90");
        for (TileStationSecurity s : PlatformTile.loaded(w, TileStationSecurity.class)) StnConsole.row(out, String.valueOf(s.getPos().toLong()), s.row());
        StnConsole.btn(out, "AUTO", "plat.sec.auto", true);
        StnConsole.btn(out, "OPEN", "plat.sec.open", true);
        StnConsole.btn(out, "CLOSE", "plat.sec.close", true);
        StnConsole.btn(out, "ACK", "plat.sec.ack", true);
    }

    /** op = "crowd.<control>", "zones.ann", "doors.<control>", "sec.auto|open|close|ack"; arg = the block's position */
    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        TileEntity t;
        try { t = w.getTileEntity(BlockPos.fromLong(Long.parseLong(arg))); } catch (NumberFormatException e) { return "§7Select a row first"; }
        if (!(t instanceof PlatformTile pt)) return "§cThat block is gone";
        String ctl = op.substring(op.indexOf('.') + 1);
        if (pt instanceof TileStationSecurity s) {
            switch (ctl) {
                case "auto" -> { s.force(0); return "§eSecurity: AUTO (timetable)"; }
                case "open" -> { s.force(1); return "§aStation held OPEN"; }
                case "close" -> { s.force(2); return "§cStation CLOSED"; }
                default -> { }
            }
        }
        return IControlled.operate(pt, ctl, p);
    }
}
