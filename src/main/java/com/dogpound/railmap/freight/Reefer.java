package com.dogpound.railmap.freight;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.biz.Custody;
import com.dogpound.railmap.track.Rails;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Refrigerated freight and the cold chain (ChatGPT batch 3 #21). Every reefer - a wagon whose name says reefer /
 * refrigerated / frigo / chiller, or a shipping container plugged into a Reefer Plug Post - is a unit with a box
 * temperature, a setpoint and a cargo quality. Once a second:
 * <ul>
 *   <li>power: SHORE (a plug post's trackside supply, mains-fed), else HEP (head-end power from a locomotive in its
 *   train), else its own GENSET while it has fuel (it burns {@link CargoConfig#gensetLPerMin} L/min), else OFF;</li>
 *   <li>a powered unit pulls down to its setpoint; an unpowered one warms (or cools) toward the outside air - the
 *   biome, the time of day and rain decide it;</li>
 *   <li>perishables aboard lose quality for every °C over setpoint + 2 °C, and frozen goods that thaw over -5 °C lose it
 *   twice as fast; over setpoint + 3 °C for 5 minutes is a REEFER alarm (alarm manager + recorder + quest event).</li>
 * </ul>
 * A contract's food unloaded from a reefer under {@link CargoConfig#coldChainFindingPct} % is a cargo finding (Custody).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Reefer extends WorldSavedData {
    private static final String NAME = "irextras_reefer";

    public static final class Unit {
        public String key = "", name = "";
        public double temp = 15, set = 3, quality = 100, fuel = -1, ambient = 15;
        public String power = "OFF";
        public boolean food, alarm, acked, pti;
        public long lastSeen, shoreUntil;
        public int hotS, excursionS;
        public BlockPos at = BlockPos.ORIGIN;
    }

    public final Map<String, Unit> units = new LinkedHashMap<>();

    public Reefer() { super(NAME); }
    public Reefer(String n) { super(n); }

    public static Reefer get(World w) {
        MapStorage st = w.getPerWorldStorage();
        Reefer r = (Reefer) st.getOrLoadData(Reefer.class, NAME);
        if (r == null) { r = new Reefer(); st.setData(NAME, r); }
        return r;
    }

    public Unit unit(String key, String name) {
        Unit u = units.computeIfAbsent(key, k -> { Unit x = new Unit(); x.key = k; x.fuel = CargoConfig.gensetTankL * 0.75; return x; });
        if (name != null && !name.isEmpty()) u.name = name;
        markDirty();
        return u;
    }

    public static boolean isReefer(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.contains("reefer") || n.contains("refrigerat") || n.contains("frigo") || n.contains("chiller") || n.contains("insulated van");
    }

    public static String carKey(UUID car) { return "car:" + car; }

    // ---- the physics (pure, the self-test runs it) ------------------------------------------------------------------
    /** the outside air at a spot (°C): biome, night, rain */
    public static double ambient(World w, BlockPos p) {
        Biome b = w.getBiome(p);
        double t = 5 + b.getTemperature(p) * 20;
        long tod = w.getWorldTime() % 24000;
        if (tod > 13000 && tod < 23000) t -= 5;
        if (w.isRainingAt(p.up(2))) t -= 3;
        return t;
    }

    /** `seconds` of a unit: temperature toward the setpoint (powered) or the air; perishables lose quality warm */
    public static void step(Unit u, boolean powered, double seconds) {
        double m = seconds / 60.0;
        if (powered) {
            double d = u.set - u.temp, k = CargoConfig.pullDownPerMin * m;
            u.temp += Math.abs(d) <= k ? d : Math.signum(d) * k;
        } else {
            double d = u.ambient - u.temp, k = CargoConfig.driftPerMin * m;
            u.temp += Math.abs(d) <= k ? d : Math.signum(d) * k;
        }
        if (u.food) {
            double over = u.temp - (u.set + 2);
            double loss = over > 0 ? over * CargoConfig.decayPerDegMin * m : 0;
            if (u.set <= -10 && u.temp > -5) loss *= 2;                     // frozen goods thawing
            u.quality = Math.max(0, u.quality - loss);
            if (over > 0) u.excursionS += (int) Math.round(seconds);
        }
        u.hotS = u.temp > u.set + 3 ? u.hotS + (int) Math.round(seconds) : 0;
    }

    // ---- hooks ------------------------------------------------------------------------------------------------------
    /** a contract loaded food on this car (Custody): a reefer starts its cold chain fresh */
    public static void loaded(World w, UUID car) {
        Reefer r = get(w);
        Unit u = r.units.get(carKey(car));
        if (u == null) return;
        if (!u.food) { u.quality = 100; u.excursionS = 0; }
        u.food = true;
        r.markDirty();
    }

    /** the cargo quality of a reefer car (-1 = not a reefer we know) */
    public static double quality(World w, UUID car) {
        if (!CargoConfig.reefers) return -1;
        Unit u = get(w).units.get(carKey(car));
        return u == null ? -1 : u.quality;
    }

    /** a plug post feeds this car's shore supply for the next 2 s */
    public static void shore(World w, UUID car, long now) { Unit u = get(w).units.get(carKey(car)); if (u != null) u.shoreUntil = now + 40; }

    // ---- the clock --------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !Loader.isModLoaded("immersiverailroading") || !CargoConfig.reefers) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        if (now % 20 != 7) return;
        try { get(w).second(w, now); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.warn("[IR Extras] reefer tick failed: {}", ex.toString()); }
    }

    private void second(World w, long now) {
        cam72cam.mod.world.World uw = cam72cam.mod.world.World.get(w);
        if (uw == null) return;
        for (EntityRollingStock s : uw.getEntities(EntityRollingStock.class)) {
            if (s.isDead() || s instanceof Locomotive) continue;
            String nm = Rails.name(s);
            if (!isReefer(nm) && !isReefer(s.getDefinition() == null ? "" : s.getDefinition().name())) continue;
            Unit u = unit(carKey(s.getUUID()), nm);
            Vec3d p = s.getPosition();
            u.at = new BlockPos(p.x, p.y, p.z);
            u.lastSeen = now;
            u.ambient = ambient(w, u.at);
            boolean food = false;
            if (s instanceof Freight f && f.cargoItems != null) {
                net.minecraftforge.items.IItemHandler h = f.cargoItems.internal;
                for (int i = 0; i < h.getSlots() && !food; i++) { ItemStack st = h.getStackInSlot(i); food = !st.isEmpty() && Custody.isFood(st); }
            }
            if (food && !u.food) { u.quality = 100; u.excursionS = 0; }
            u.food = food;
            boolean hep = false;
            if (s instanceof EntityCoupleableRollingStock c) for (EntityCoupleableRollingStock x : c.getTrain()) if (x instanceof Locomotive) { hep = true; break; }
            if (u.shoreUntil >= now) u.power = "SHORE";
            else if (hep) u.power = "HEP";
            else if (u.fuel > 0.01) { u.power = "GENSET"; u.fuel = Math.max(0, u.fuel - CargoConfig.gensetLPerMin / 60.0); }
            else u.power = "OFF";
            step(u, !u.power.equals("OFF"), 1);
            alarm(w, u, s instanceof EntityMoveableRollingStock);
        }
        // reefer containers away from a plug post: they warm where they stand
        for (Unit u : units.values()) {
            if (!u.key.startsWith("box:") || now - u.lastSeen < 40) continue;
            Custody.Rec r = com.dogpound.railmap.biz.BizData.get(w).custody.recs.get(u.key.substring(4));
            if (r == null || r.stuffed == 0) { u.food = false; continue; }
            u.power = "OFF";
            step(u, false, 1);
            r.coldQ = Math.min(r.coldQ, u.quality); r.tempC = u.temp;
        }
        units.values().removeIf(u -> now - u.lastSeen > 24000L * 7 && !u.alarm);
        markDirty();
    }

    /** raise / clear the REEFER alarm for a unit */
    void alarm(World w, Unit u, boolean car) {
        AlarmManager am = AlarmManager.get(w);
        String k = "reefer:" + u.key;
        if (u.food && u.hotS >= 300 && !u.alarm) {
            u.alarm = true; u.acked = false;
            am.raise(w, k, AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "REEFER %s %.1f C (set %.0f) - %s, quality %.0f%%", u.name, u.temp, u.set, u.power, u.quality), u.at, false);
            Recorder.event(w, String.format(Locale.ROOT, "Reefer alarm: %s at %.1f C, setpoint %.0f C, power %s", u.name, u.temp, u.set, u.power));
            com.dogpound.railmap.api.QuestEvents.fire(w, null, u.at, "reefer_alarm", "unit", u.name, "temp", Math.round(u.temp), "power", u.power);
        } else if (u.alarm && u.temp <= u.set + 2) {
            u.alarm = false;
            am.clear(k);
            Recorder.event(w, "Reefer back in range: " + u.name);
        }
    }

    public static String fmt(Unit u) { return String.format(Locale.ROOT, "%+.1f/%+.0f Q%.0f", u.temp, u.set, u.quality); }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        units.clear();
        NBTTagList l = t.getTagList("u", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Unit u = new Unit();
            u.key = c.getString("k"); u.name = c.getString("n"); u.temp = c.getDouble("t"); u.set = c.getDouble("s"); u.quality = c.getDouble("q");
            u.fuel = c.getDouble("f"); u.ambient = c.getDouble("a"); u.power = c.getString("p"); u.food = c.getBoolean("fd"); u.alarm = c.getBoolean("al");
            u.acked = c.getBoolean("ak"); u.pti = c.getBoolean("pti"); u.lastSeen = c.getLong("ls"); u.hotS = c.getInteger("hs"); u.excursionS = c.getInteger("ex");
            u.at = BlockPos.fromLong(c.getLong("at"));
            units.put(u.key, u);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Unit u : units.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("k", u.key); c.setString("n", u.name); c.setDouble("t", u.temp); c.setDouble("s", u.set); c.setDouble("q", u.quality);
            c.setDouble("f", u.fuel); c.setDouble("a", u.ambient); c.setString("p", u.power); c.setBoolean("fd", u.food); c.setBoolean("al", u.alarm);
            c.setBoolean("ak", u.acked); c.setBoolean("pti", u.pti); c.setLong("ls", u.lastSeen); c.setInteger("hs", u.hotS); c.setInteger("ex", u.excursionS);
            c.setLong("at", u.at.toLong());
            l.appendTag(c);
        }
        t.setTag("u", l);
        return t;
    }
}
