package com.dogpound.railmap.admin;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.depot.DepotConfig;
import com.dogpound.railmap.incident.IncidentConfig;
import com.dogpound.railmap.sim.SimConfig;
import com.dogpound.railmap.sim.SimData;
import com.dogpound.railmap.track.TrackConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Simulation profiles (her ChatGPT list #118): one switch that sets every realism setting the other systems added -
 * wear, physics detail, failures, weather, electrification, permits, driver aids, economy, passengers, automation.
 * CASUAL = forgiving, REALISTIC = the shipped defaults, HARDCORE = everything harsh on. Applying writes the config
 * files (they stay editable one by one afterwards = CUSTOM) and resets each world's physics choice to the profile's.
 */
public final class Profiles {
    private Profiles() {}

    public static final String[] NAMES = {"CASUAL", "REALISTIC", "HARDCORE"};

    /** class, field, casual, realistic, hardcore, what */
    private static final Object[][] T = {
            {RailMapConfig.class, "wearEnabled", true, true, true, "rolling-stock wear"},
            {RailMapConfig.class, "wearRate", 0.3, 1.0, 2.0, "wear rate"},
            {RailMapConfig.class, "wearEffects", false, true, true, "worn parts limit trains"},
            {RailMapConfig.class, "powerNeedsEnergy", false, true, true, "substations need FE"},
            {RailMapConfig.class, "stormOutages", false, true, true, "storm trips"},
            {RailMapConfig.class, "ohleContactDetail", false, true, true, "pantograph / wire mechanics"},
            {RailMapConfig.class, "ohleDewirement", false, false, true, "dewirements"},
            {RailMapConfig.class, "ohleWireBreaks", false, false, true, "worn wire snaps"},
            {RailMapConfig.class, "weatherRestrictions", false, true, true, "weather speed restrictions"},
            {RailMapConfig.class, "railBuckling", false, false, true, "rail buckling"},
            {RailMapConfig.class, "frozenPoints", false, true, true, "frozen points"},
            {RailMapConfig.class, "gearNeedsPower", false, true, true, "gear needs 12 V"},
            {RailMapConfig.class, "permitToWork", false, true, true, "HV permit to work"},
            {RailMapConfig.class, "recoveryCrewSeconds", 20, 60, 0, "auto recovery crew (0 = players clear wrecks)"},
            {RailMapConfig.class, "passengerDemand", 1.0, 1.0, 1.5, "passenger demand"},
            {RailMapConfig.class, "contractPayScale", 1.5, 1.0, 0.8, "contract pay"},
            {RailMapConfig.class, "energyDispatch", true, true, true, "energy-aware dispatch"},
            {SimConfig.class, "consistPhysics", "light", "light", "full", "consist physics"},
            {SimConfig.class, "couplerStrength", 2.0, 1.0, 1.0, "coupler strength"},
            {SimConfig.class, "engineManagement", false, true, true, "diesel engine management"},
            {SimConfig.class, "tractionMotorHeating", false, true, true, "traction motor heating"},
            {SimConfig.class, "railConditions", false, true, true, "leaves / frost / dew"},
            {SimConfig.class, "wheelFlats", false, true, true, "wheel flats"},
            {SimConfig.class, "vigilanceSeconds", 0, 60, 45, "vigilance device (s, 0 off)"},
            {SimConfig.class, "awsAckSeconds", 0.0, 2.7, 2.7, "AWS (s, 0 off)"},
            {DepotConfig.class, "conditionEffects", false, true, true, "condition restrictions"},
            {DepotConfig.class, "failedHoldsTrain", false, false, true, "failed in service holds trains"},
            {DepotConfig.class, "examAlarms", false, true, true, "exam due alarms"},
            {DepotConfig.class, "mainsRequired", false, true, true, "depot machines need mains"},
            {DepotConfig.class, "brakeTestAfterBrakeWork", false, true, true, "brake test after brake work"},
            {DepotConfig.class, "servicing", false, true, true, "cleaning / water / toilets / catering"},
            {DepotConfig.class, "crewRequired", false, true, true, "crew rules on attended lines"},
            {DepotConfig.class, "facilityCosts", false, true, true, "station facility costs"},
            {TrackConfig.class, "trackWearRate", 0.3, 1.0, 2.0, "track wear rate"},
            {TrackConfig.class, "trackSpeedRestrictions", false, true, true, "automatic TSRs"},
            {TrackConfig.class, "pointMotorFailPer10k", 0, 8, 20, "point motor failures /10k"},
            {TrackConfig.class, "heatersAndFansNeedGrid", false, true, true, "tunnel fans need grid"},
            {TrackConfig.class, "structuresStopTrains", false, true, true, "structure limits stop trains"},
            {TrackConfig.class, "structureDecay", false, true, true, "structure decay"},
            {TrackConfig.class, "crossingStalls", false, true, true, "road traffic stalls"},
            {TrackConfig.class, "barrierFailPer1000", 0, 3, 6, "barrier failures /1000"},
            {IncidentConfig.class, "autoProtectAdjacent", false, false, true, "protect adjacent lines"},
            {IncidentConfig.class, "towEnforce", false, true, true, "tow speed enforced"},
            {AdminConfig.class, "holdUnauthorised", false, false, true, "hold unauthorised running"},
    };

    public static int index(String n) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(n)) return i;
        return -1;
    }

    private static Field field(Object[] r) throws NoSuchFieldException { return ((Class<?>) r[0]).getField((String) r[1]); }

    private static Object cast(Field f, Object v) {
        Class<?> t = f.getType();
        if (t == double.class) return ((Number) v).doubleValue();
        if (t == int.class) return ((Number) v).intValue();
        return v;
    }

    /** "setting: now -> profile" for every setting the profile would change */
    public static List<String> diff(int p) {
        List<String> o = new ArrayList<>();
        for (Object[] r : T) {
            try {
                Field f = field(r);
                Object want = cast(f, r[2 + p]), now = f.get(null);
                if (!Objects.equals(want, now)) o.add(r[5] + ": " + now + " -> " + want);
            } catch (ReflectiveOperationException e) { o.add(r[1] + ": (missing)"); }
        }
        return o;
    }

    /** the profile a config matches, or CUSTOM */
    public static String current() {
        for (int p = 0; p < NAMES.length; p++) if (diff(p).isEmpty()) return NAMES[p];
        return "CUSTOM";
    }

    public static List<String> apply(MinecraftServer s, int p, String who) {
        List<String> ch = diff(p);
        for (Object[] r : T) {
            try { Field f = field(r); f.set(null, cast(f, r[2 + p])); } catch (ReflectiveOperationException e) { RailMap.LOG.warn("[IR Extras] profile: {}", e.toString()); }
        }
        ConfigManager.sync(RailMap.MODID, Config.Type.INSTANCE);                    // writes every irextras config file
        if (s != null) for (WorldServer w : s.worlds) {
            SimData d = SimData.get(w);
            if (d.physicsMode != -1) { d.physicsMode = -1; d.markDirty(); }          // the profile's physics, not an old per-world choice
            AdminData ad = AdminData.get(w);
            ad.profile = NAMES[p];
            ad.markDirty();
            com.dogpound.railmap.alarm.Recorder.event(w, "ADMIN: " + who + " applied the " + NAMES[p] + " simulation profile (" + ch.size() + " settings changed)");
        }
        return ch;
    }

    /** for a test: every row resolves to a real config field of the right type */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        for (Object[] r : T)
            for (int p = 0; p < 3; p++) {
                try { Field f = field(r); f.get(null); Object v = cast(f, r[2 + p]); if (!f.getType().isPrimitive() && !f.getType().isInstance(v)) bad.add(r[1] + " type"); if (f.getType().isPrimitive() && v.getClass() == String.class) bad.add(r[1] + " type"); }
                catch (ReflectiveOperationException e) { bad.add(r[1] + " missing"); }
            }
        return bad;
    }
}
