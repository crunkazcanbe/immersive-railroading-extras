package com.dogpound.railmap.weather;

import com.dogpound.railmap.RailMapConfig;
import net.minecraft.world.World;

import java.util.*;

/**
 * Snow-clearing gangs (her ChatGPT list #63: "snow-clearing crews receive work orders based on track priority"). Every
 * cell with 100 mm of snow or more is a work order, and so is a used cell with 30 mm in frost (frozen points); its priority is the traffic over it (trains an hour) first, then
 * the depth. snowCrews gangs take the top orders, dig 12 mm per 5 s each (half that in a blizzard) and move on once a
 * cell is down to 20 mm.
 */
public final class Crews {
    private Crews() {}

    public static double priority(WeatherData.Cell c) { return c.traffic * 10 + c.snow / 50; }

    public static List<WeatherData.Cell> orders(WeatherData d) {
        List<WeatherData.Cell> l = new ArrayList<>();
        for (WeatherData.Cell c : d.cells.values()) if (c.snow >= 100 || c.snow >= 30 && c.air <= 0 && c.traffic > 0.5 || c.crew >= 0) l.add(c);
        l.sort((a, b) -> Double.compare(priority(b), priority(a)));
        return l;
    }

    static void tick(World w, WeatherData d, long now) {
        int n = RailMapConfig.snowCrews;
        boolean[] busy = new boolean[Math.max(0, n)];
        for (WeatherData.Cell c : d.cells.values()) {
            if (c.crew < 0) continue;
            if (c.crew >= n || c.snow <= 20) {
                if (c.crew < n) d.log(w, "snow gang " + (c.crew + 1) + " finished " + Weather.name(c));
                c.crew = -1;
                continue;
            }
            busy[c.crew] = true;
            c.snow = Math.max(0, c.snow - (c.snowing && c.precip == 3 ? 6 : 12));
        }
        for (WeatherData.Cell c : orders(d)) {
            if (c.crew >= 0 || c.snow < 30) continue;
            int free = -1;
            for (int i = 0; i < busy.length; i++) if (!busy[i]) { free = i; break; }
            if (free < 0) break;
            busy[free] = true;
            c.crew = free;
            d.log(w, "snow gang " + (free + 1) + " sent to " + Weather.name(c) + " (" + Math.round(c.snow) + " mm, " + Math.round(c.traffic) + " trains/h)");
        }
        d.markDirty();
    }
}
