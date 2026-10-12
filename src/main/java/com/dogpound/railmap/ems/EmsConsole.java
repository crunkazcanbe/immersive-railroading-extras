package com.dogpound.railmap.ems;

import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.incident.IncidentLog;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

import static com.dogpound.railmap.stn.StnConsole.*;

/** the console STN tab's Emergency + EMS runs pages ("ems.*") */
public final class EmsConsole {
    private EmsConsole() {}

    public static void fill(World w, List<String[]> out) {
        page(out, "Emergency", "Fire, ambulance and police stations: status, AUTO turnout, response times");
        cols(out, "Station:0", "Svc:28", "Status:34", "Mode:48", "Today:56", "Last resp:63", "Avg resp:73", "Crew:84");
        for (TileEmergencyStation s : StnUtil.loaded(w, TileEmergencyStation.class)) row(out, String.valueOf(s.getPos().toLong()), s.row());
        btn(out, "DISPATCH", "ems.dispatch", true);
        btn(out, "RECALL", "ems.recall", true);
        btn(out, "AUTO / MANUAL", "ems.k_manual", true);
        btn(out, "RADIO", "ems.radio", true);
        txt(out, EmsConfig.enabled ? "Auto turnout " + (EmsConfig.autoDispatch ? "on" : "off (config)") + " · range " + EmsConfig.maxRange + " blocks · cordon " + (EmsConfig.cordon ? "on" : "off") : "!Emergency services switched off (config)");
        page(out, "EMS runs", "Runs out now and the last ones finished, with their response times");
        cols(out, "Run:0", "Vehicle:6", "From:26", "To:46", "Stage:58", "Response:70", "Work:82");
        EmsData d = EmsData.get(w);
        for (EmsData.Run r : d.active())
            row(out, String.valueOf(r.id), String.valueOf(r.id), r.vehicle, r.stationName, r.incident < 0 ? String.format(Locale.ROOT, "%.0f,%.0f", r.tx, r.tz) : "incident #" + r.incident,
                    "~" + r.stage.name().replace('_', ' '), r.onScene > 0 ? IncidentLog.mins(r.response()) : ".-", String.valueOf(r.work));
        btn(out, "RECALL RUN", "ems.recallrun", true);
        for (String h : d.history) txt(out, h);
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        long k;
        try { k = Long.parseLong(arg); } catch (NumberFormatException e) { return "§7Pick a row first"; }
        if (op.equals("recallrun")) {
            EmsData.Run r = EmsData.get(w).byId((int) k);
            if (r == null || !r.active()) return "§7That run is over";
            Ems.leaveScene(w, r, "recalled from the console by " + p.getName());
            return "§e" + r.vehicle + " recalled";
        }
        BlockPos at = BlockPos.fromLong(k);
        TileEntity t = w.isBlockLoaded(at) ? w.getTileEntity(at) : null;
        if (!(t instanceof TileEmergencyStation s)) return "§7That station isn't loaded";
        String r = IControlled.operate(s, op, null);
        return r == null || r.isEmpty() ? "§7" + s.callSign() + ": done" : r;
    }
}
