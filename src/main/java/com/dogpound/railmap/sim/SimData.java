package com.dogpound.railmap.sim;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Everything the train simulation keeps per car / locomotive between sessions (irextras_trainsim.dat): coupler
 * condition, wheel flats, the diesel's oil, coolant, battery, hours and fault log, and the driver's settings for
 * sanders, traction control and wheel-slide protection. Plus the world's chosen physics mode.
 * Unknown cars start as new (full couplers, full fluids, charged battery), so old worlds load clean.
 */
public final class SimData extends WorldSavedData {
    private static final String NAME = "irextras_trainsim";

    /** the world's consist physics mode: -1 = the config default, 0 off, 1 light, 2 full */
    public int physicsMode = -1;

    public static final class Car {
        /** coupler condition 0..1: [0] front, [1] rear; 0 = broken knuckle */
        public final float[] coupler = {1f, 1f};
        /** wheel flat length, mm (a flat over 60 mm takes the car out of service) */
        public float flatMm;
        /** sanders switch to sand by themselves on slip / slide / bad rail */
        public boolean autoSand = true;
        /** traction control (slip control) fitted and on */
        public boolean tractionControl = true;
        /** wheel-slide protection on (fitted to locomotives and passenger stock) */
        public boolean wsp = true;
        /** the depot service time we last saw (WearData) - a newer one means it was in the shop */
        public long serviceSeen;

        // ---- locomotives
        public float oil = 1f, coolant = 1f, battery = 1f;
        public double hours;
        public int starts;
        public boolean aess = true;
        /** engine run / isolate switch: isolated = the engine runs but gives no traction (trailing unit) */
        public boolean isolated;
        /** latched fault codes (cleared by FAULT RESET once the condition is gone) */
        public final Set<String> faults = new LinkedHashSet<>();
        /** the last 12 faults, "hh:mm CODE text" */
        public final LinkedList<String> faultLog = new LinkedList<>();

        // ---- multiple-unit / distributed power (her list item 106, sim.MultiUnit)
        /** MU control system ("" = from the vehicle: AAR-27 diesel, UIC-13 electric, ANY cab car, NONE steam) */
        public String muSystem = "";
        /** MU jumper unplugged / DP radio cut out: this unit takes no orders from a lead cab */
        public boolean muCut;
        /** as a DP remote: 0 SYNC (follows the lead), 1 INDEPENDENT (its own notch, behind the fence), 2 IDLE */
        public int dpMode;
        public float dpNotch;
        /** the formation's lead cab (Formation & Livery Desk APPLY): leads an unmanned consist */
        public boolean muLead;

        // ---- auxiliary systems (item 108, sim.AuxSim): the driver's switches
        /** headlights 0 OFF, 1 DIM, 2 BRIGHT */
        public int headlight = 2;
        public boolean cabLights, hvac = true, wipers, batteryOn = true, layoverHeater, fuelHeater = true;
        /** compressor governor 0 OFF, 1 AUTO */
        public int compressor = 1;
        public int cabSet = 21;

        // ---- appearance (item 110, sim.Appearance)
        /** body damage 0..100 (dents, scorching, missing panels; restored at a Formation & Livery Desk) */
        public float body;
        /** livery: 0 the builder's paint, 1 the operating company's colours */
        public int livery;
        /** the depot km reading at the last repaint (weathering grows from here) */
        public double paintKm = -1;

        // ---- transient (live simulation state)
        public transient boolean init;
        public transient int phase;            // EngineSim.OFF ...
        public transient double phaseTime;
        public transient double rpm, boostKpa, oilKpa, tmC = Double.NaN, fuelLph, idleSec;
        public transient double lastFuel = -1;
        public transient boolean aessStopped, fanLow, fanHigh, shutters;
        public transient double sandHold, slideSec, flatThump;
        public transient float derate = 1f;
        public transient String derateWhy = "";
        public transient boolean slip, slide, wspActive, sanding, overload;
        public transient float railAdhesion = 1f;
        public transient String railWhy = "";
        public transient double peakT, peakC, buffSustain;
        public transient long adhAt = -1;
        public transient net.minecraft.util.math.BlockPos adhPos;
        public transient float[] adhCache;
        // aux
        public transient double cabC = Double.NaN, compC = Double.NaN, auxKw, auxCap, batKw;
        public transient boolean compRun, compTrip, convTrip, auxOk, rainNoWipers;
        public transient String auxSrc = "";
        public transient int lastWreck = -1;

        public void fault(String code, String text, String clock) {
            if (faults.add(code + " " + text)) {
                faultLog.addFirst(clock + " " + code + " " + text);
                while (faultLog.size() > 12) faultLog.removeLast();
            }
        }

        public boolean hasFault(String code) {
            for (String f : faults) if (f.startsWith(code + " ")) return true;
            return false;
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setFloat("cf", coupler[0]); t.setFloat("cr", coupler[1]);
            t.setFloat("flat", flatMm);
            t.setBoolean("as", autoSand); t.setBoolean("tc", tractionControl); t.setBoolean("wsp", wsp);
            t.setLong("svc", serviceSeen);
            t.setFloat("oil", oil); t.setFloat("cool", coolant); t.setFloat("bat", battery);
            t.setDouble("hours", hours); t.setInteger("starts", starts);
            t.setBoolean("aess", aess); t.setBoolean("iso", isolated);
            t.setString("mus", muSystem); t.setBoolean("muc", muCut); t.setInteger("dpm", dpMode); t.setFloat("dpn", dpNotch); t.setBoolean("mul", muLead);
            t.setInteger("hl", headlight); t.setBoolean("cabl", cabLights); t.setBoolean("hvac", hvac); t.setBoolean("wip", wipers);
            t.setBoolean("batOn", batteryOn); t.setBoolean("lay", layoverHeater); t.setBoolean("fuh", fuelHeater);
            t.setInteger("comp", compressor); t.setInteger("cset", cabSet);
            t.setFloat("body", body); t.setInteger("liv", livery); t.setDouble("pkm", paintKm);
            NBTTagList f = new NBTTagList();
            for (String s : faults) f.appendTag(new NBTTagString(s));
            t.setTag("faults", f);
            NBTTagList l = new NBTTagList();
            for (String s : faultLog) l.appendTag(new NBTTagString(s));
            t.setTag("log", l);
            return t;
        }

        static Car read(NBTTagCompound t) {
            Car c = new Car();
            c.coupler[0] = t.hasKey("cf") ? t.getFloat("cf") : 1f;
            c.coupler[1] = t.hasKey("cr") ? t.getFloat("cr") : 1f;
            c.flatMm = t.getFloat("flat");
            c.autoSand = !t.hasKey("as") || t.getBoolean("as");
            c.tractionControl = !t.hasKey("tc") || t.getBoolean("tc");
            c.wsp = !t.hasKey("wsp") || t.getBoolean("wsp");
            c.serviceSeen = t.getLong("svc");
            c.oil = t.hasKey("oil") ? t.getFloat("oil") : 1f;
            c.coolant = t.hasKey("cool") ? t.getFloat("cool") : 1f;
            c.battery = t.hasKey("bat") ? t.getFloat("bat") : 1f;
            c.hours = t.getDouble("hours");
            c.starts = t.getInteger("starts");
            c.aess = !t.hasKey("aess") || t.getBoolean("aess");
            c.isolated = t.getBoolean("iso");
            c.muSystem = t.getString("mus"); c.muCut = t.getBoolean("muc"); c.dpMode = t.getInteger("dpm"); c.dpNotch = t.getFloat("dpn"); c.muLead = t.getBoolean("mul");
            if (t.hasKey("hl")) {
                c.headlight = t.getInteger("hl"); c.cabLights = t.getBoolean("cabl"); c.hvac = t.getBoolean("hvac"); c.wipers = t.getBoolean("wip");
                c.batteryOn = t.getBoolean("batOn"); c.layoverHeater = t.getBoolean("lay"); c.fuelHeater = t.getBoolean("fuh");
                c.compressor = t.getInteger("comp"); c.cabSet = t.getInteger("cset");
            }
            c.body = t.getFloat("body"); c.livery = t.getInteger("liv"); c.paintKm = t.hasKey("pkm") ? t.getDouble("pkm") : -1;
            NBTTagList f = t.getTagList("faults", Constants.NBT.TAG_STRING);
            for (int i = 0; i < f.tagCount(); i++) c.faults.add(f.getStringTagAt(i));
            NBTTagList l = t.getTagList("log", Constants.NBT.TAG_STRING);
            for (int i = 0; i < l.tagCount(); i++) c.faultLog.add(l.getStringTagAt(i));
            return c;
        }
    }

    private final Map<UUID, Car> cars = new HashMap<>();

    public SimData() { super(NAME); }
    public SimData(String n) { super(n); }

    public static SimData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        SimData d = (SimData) s.getOrLoadData(SimData.class, NAME);
        if (d == null) { d = new SimData(); s.setData(NAME, d); }
        return d;
    }

    public Car of(UUID id) { return cars.computeIfAbsent(id, k -> new Car()); }
    public Car peek(UUID id) { return cars.get(id); }

    /** 0 off, 1 light, 2 full */
    public int mode() { return physicsMode >= 0 ? physicsMode : SimConfig.modeOf(SimConfig.consistPhysics); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        cars.clear();
        physicsMode = t.hasKey("mode") ? t.getInteger("mode") : -1;
        NBTTagList l = t.getTagList("cars", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            cars.put(new UUID(c.getLong("hi"), c.getLong("lo")), Car.read(c));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("mode", physicsMode);
        NBTTagList l = new NBTTagList();
        for (Map.Entry<UUID, Car> e : cars.entrySet()) {
            NBTTagCompound c = e.getValue().write();
            c.setLong("hi", e.getKey().getMostSignificantBits());
            c.setLong("lo", e.getKey().getLeastSignificantBits());
            l.appendTag(c);
        }
        t.setTag("cars", l);
        return t;
    }
}
