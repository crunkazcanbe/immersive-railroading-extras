package com.dogpound.railmap.crew;

import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * Control Center > Depot > Crew rows (DepotConsole appends these to PacketRailState.depot):
 * N {name, role, phase, doing, diagram, fatigue %, train / beat} and R {text} - revenue protection totals + cases.
 */
public final class CrewConsole {
    private CrewConsole() {}

    public static void fill(World w, List<String[]> out) {
        CrewData c = CrewData.get(w);
        FleetData f = FleetData.get(w);
        long now = w.getTotalWorldTime();
        int n = 0;
        for (CrewData.Staff s : c.staff.values()) {
            if (n++ >= 40) break;
            CrewTicker.Live L = CrewTicker.all(w).get(s.name);
            String on = L == null ? "OFF" : CrewTicker.PHASE[L.phase];
            String where = s.role == CrewData.INSPECTOR ? (s.line.isEmpty() ? "any line" : s.line) + " · " + s.caught + "/" + s.checks
                    : s.role == CrewData.DRIVER ? (f.drivers.get(s.name) == null || f.drivers.get(s.name).train == null ? "spare" : f.unit(f.drivers.get(s.name).train).name)
                    : s.train == null ? "spare" : f.unit(s.train).name;
            out.add(new String[]{"N", s.name, CrewData.ROLE[s.role], on, L == null ? "" : L.doing, TileCrewOffice.shift(s), String.valueOf(Math.round(CrewTicker.fatigue(w, s) * 100)), where});
        }
        if (c.today[Revenue.CHECKS] + c.total[Revenue.CHECKS] == 0 && c.cases.isEmpty()) return;
        String[] mode = {"warnings only", "warn first, then penalty fare", "penalty fare"};
        out.add(new String[]{"R", "Revenue protection (" + mode[Math.max(0, Math.min(2, c.mode()))] + ", " + Coins.fmt(CrewConfig.penaltyCents) + ")"});
        out.add(new String[]{"R", String.format(Locale.ROOT, "Today %d checked (%d players) · %d without a ticket · %d warned · %s penalty fares · yesterday %d / %d / %s",
                c.today[0], c.today[4], c.today[1], c.today[2], Coins.fmt(c.today[3]), c.yesterday[0], c.yesterday[1], Coins.fmt(c.yesterday[3]))});
        int k = 0;
        for (CrewData.Case x : c.cases) {
            if (k++ >= 6) break;
            out.add(new String[]{"R", "#" + x.id + " " + x.who + " · " + x.where + " · " + x.state + (x.cents > 0 ? " " + Coins.fmt(x.cents) : "") + " · " + x.inspector + (x.cam.isEmpty() ? "" : " · " + x.cam)
                    + " · " + (now - x.tick) * 60 / 1000 + " min ago"});
        }
    }
}
