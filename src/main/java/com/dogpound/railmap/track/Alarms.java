package com.dogpound.railmap.track;

import com.dogpound.railmap.alarm.AlarmManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Edge-triggered standing alarms: raised once when a condition appears (text kept current), cleared when it goes. */
final class Alarms {
    private Alarms() {}

    static void state(World w, String key, boolean on, AlarmManager.Cat cat, AlarmManager.Sev sev, String text, BlockPos at) {
        AlarmManager am = AlarmManager.get(w);
        if (!on) { am.clear(key); return; }
        for (AlarmManager.Alarm a : am.all()) if (a.key.equals(key) && a.active) { a.text = text; return; }   // already standing
        am.raise(w, key, cat, sev, text, at, true);
    }
}
