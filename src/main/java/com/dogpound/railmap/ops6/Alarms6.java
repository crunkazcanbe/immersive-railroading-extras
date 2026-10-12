package com.dogpound.railmap.ops6;

import com.dogpound.railmap.alarm.AlarmManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** standing alarms raised once on the way in and cleared on the way out (raise() every second would re-count them) */
final class Alarms6 {
    private Alarms6() {}

    static boolean active(World w, String key) {
        for (AlarmManager.Alarm a : AlarmManager.get(w).all()) if (a.key.equals(key) && a.active) return true;
        return false;
    }

    /** returns true when it just came in */
    static boolean set(World w, String key, boolean on, AlarmManager.Cat cat, AlarmManager.Sev sev, String text, BlockPos at) {
        boolean was = active(w, key);
        if (on && !was) { AlarmManager.get(w).raise(w, key, cat, sev, text, at, true); return true; }
        if (!on && was) AlarmManager.get(w).clear(key);
        return false;
    }

    /** a momentary alarm (a trip, a penalty brake): active once, waits for ACK */
    static void trip(World w, String key, AlarmManager.Cat cat, AlarmManager.Sev sev, String text, BlockPos at) { AlarmManager.get(w).raise(w, key, cat, sev, text, at, false); }
}
