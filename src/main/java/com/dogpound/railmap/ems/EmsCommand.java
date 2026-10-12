package com.dogpound.railmap.ems;

import com.dogpound.railmap.incident.IncidentLog;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/** /irextras ems list|runs|send <fire|ambulance|police> [incident#]|recall <run#|all>|selftest */
public final class EmsCommand {
    private EmsCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("ems")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        EmsData d = EmsData.get(w);
        switch (sub) {
            case "list" -> {
                List<TileEmergencyStation> l = StnUtil.loaded(w, TileEmergencyStation.class);
                if (l.isEmpty()) say(s, "§7No Fire / Ambulance / Police Stations loaded");
                for (TileEmergencyStation t : l) say(s, "§e" + String.join(" · ", t.row()).replaceAll("(^|· )[!~+.]", "$1") + " §7at " + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ());
            }
            case "runs" -> {
                for (EmsData.Run r : d.active()) say(s, String.format(Locale.ROOT, "§e#%d %s (%s) %s -> %s at %.0f,%.0f%s", r.id, r.vehicle, r.stationName, r.stage, r.incident < 0 ? "test" : "incident #" + r.incident, r.vx, r.vz,
                        r.onScene > 0 ? " response " + IncidentLog.mins(r.response()) : ""));
                for (int i = 0; i < Math.min(8, d.history.size()); i++) say(s, "§7" + d.history.get(i));
                if (d.active().isEmpty() && d.history.isEmpty()) say(s, "§7No runs yet");
            }
            case "send" -> {
                if (a.length < 3) { say(s, "§c/irextras ems send <fire|ambulance|police> [incident#]"); return true; }
                String kw = a[2].toLowerCase(Locale.ROOT);
                Ems.Kind k = kw.startsWith("f") ? Ems.Kind.FIRE : kw.startsWith("a") ? Ems.Kind.AMBULANCE : kw.startsWith("p") ? Ems.Kind.POLICE : null;
                if (k == null) { say(s, "§cfire, ambulance or police"); return true; }
                IncidentLog.Rec r = a.length > 3 ? IncidentLog.get(w).byId(parse(a[3])) : null;
                BlockPos to = r != null ? r.pos() : s.getPosition();
                TileEmergencyStation t = Ems.nearest(w, k, to, false);
                if (t == null) { say(s, "§cNo " + k.station + " available within " + EmsConfig.maxRange + " blocks (placed, powered, vehicle in?)"); return true; }
                say(s, Ems.dispatch(w, t, r, to, s.getName()));
            }
            case "recall" -> {
                int n = 0;
                for (EmsData.Run r : d.active()) if (a.length < 3 || a[2].equalsIgnoreCase("all") || r.id == parse(a[2])) { Ems.leaveScene(w, r, "recalled by " + s.getName()); n++; }
                say(s, n == 0 ? "§7No such run" : "§e" + n + " run(s) recalled");
            }
            case "selftest" -> {
                List<String> f = Ems.selfTest();
                say(s, f.isEmpty() ? "§aEmergency services self-test passed (dispatch rules, cordon ring, response time, run save/load)" : "§cEMS self-test FAILED: " + String.join("; ", f));
            }
            default -> say(s, "§e/irextras ems §7list · runs · send <fire|ambulance|police> [incident#] (no # = to you, a test run) · recall <run#|all> · selftest");
        }
        return true;
    }

    static int parse(String x) { try { return Integer.parseInt(x.replace("#", "")); } catch (NumberFormatException e) { return -1; } }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
