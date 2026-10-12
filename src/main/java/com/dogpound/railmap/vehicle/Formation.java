package com.dogpound.railmap.vehicle;

import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A planned train formation (her ChatGPT list item 107): the vehicles in order (each the right way round and in its
 * paint variant), which one is the lead cab, the livery and the MU control system it should run on. Its figures come
 * from Immersive Railroading's own definitions, so they are what the train really is.
 */
public final class Formation {
    public static final class Car {
        public String def = "";
        public boolean flip;
        public String texture = "";
        public Car() {}
        public Car(String def, boolean flip, String texture) { this.def = def; this.flip = flip; this.texture = texture == null ? "" : texture; }
        public EntityRollingStockDefinition definition() { return DefinitionManager.getDefinition(def); }
    }

    public String name = "NEW FORMATION";
    public final List<Car> cars = new ArrayList<>();
    public int lead;
    /** 0 the builder's paint, 1 the operating company's livery */
    public int livery;

    /** what the desk works out */
    public static final class Stats {
        public double lengthM, tonnes, brakedPct, kw, teKn, kwPerT, startGradePct;
        public int locos, cabs, powered, seats;
        public double maxKmh = Double.MAX_VALUE;
        public final List<String> problems = new ArrayList<>(), notes = new ArrayList<>();
    }

    public Stats stats(double maxLengthM, double rulingGradePct) {
        Stats s = new Stats();
        Gauge g = Gauge.standard();
        double kg = 0, braked = 0;
        String sys = null;
        boolean mixed = false;
        for (int i = 0; i < cars.size(); i++) {
            EntityRollingStockDefinition d = cars.get(i).definition();
            if (d == null) { s.problems.add("CAR " + (i + 1) + ": UNKNOWN VEHICLE " + cars.get(i).def); continue; }
            s.lengthM += d.getLength(g);
            kg += d.getWeight(g);
            if (d.hasPressureBrake()) braked += d.getWeight(g);
            s.seats += d.getMaxPassengers();
            s.maxKmh = Math.min(s.maxKmh, d instanceof LocomotiveDefinition ld ? ld.getMaxSpeed(g).metric() : 160);
            if (d instanceof LocomotiveDefinition ld) {
                s.locos++;
                if (ld.isCabCar()) s.cabs++;
                else {
                    s.powered++;
                    s.kw += ld.getWatt(g) / 1000.0;
                    s.teKn += ld.getStartingTractionNewtons(g) / 1000.0;
                    String sy = ld instanceof LocomotiveSteamDefinition ? "NONE" : ld.muliUnitCapable ? "MU" : "NONE";
                    if (sys == null) sys = sy; else if (!sys.equals(sy) || sy.equals("NONE")) mixed = true;
                }
            }
        }
        s.tonnes = kg / 1000.0;
        s.brakedPct = kg <= 0 ? 0 : braked * 100 / kg;
        s.kwPerT = s.tonnes <= 0 ? 0 : s.kw / s.tonnes;
        // the grade it can start on: TE (at 25 % adhesion-limited, IR's own number is the starting TE) against grade + 4 kg/t rolling
        double mass = kg * 9.81;
        s.startGradePct = mass <= 0 ? 0 : Math.max(0, (s.teKn * 1000 / mass - 0.004) * 100);
        if (s.maxKmh == Double.MAX_VALUE) s.maxKmh = 0;
        // checks
        if (cars.isEmpty()) s.problems.add("NO VEHICLES");
        if (s.powered == 0 && !cars.isEmpty()) s.problems.add("NO POWER - NEEDS A LOCOMOTIVE OR POWER CAR");
        EntityRollingStockDefinition ld = lead >= 0 && lead < cars.size() ? cars.get(lead).definition() : null;
        if (!(ld instanceof LocomotiveDefinition)) s.problems.add("LEAD CAB IS NOT A LOCOMOTIVE OR DRIVING TRAILER");
        else if (lead != 0 && lead != cars.size() - 1) s.notes.add("LEAD CAB IS MID-TRAIN - THE DRIVER WILL HAVE NO VIEW");
        if (maxLengthM > 0 && s.lengthM > maxLengthM) s.problems.add(String.format(Locale.ROOT, "TOO LONG %.0f M FOR %.0f M", s.lengthM, maxLengthM));
        if (s.brakedPct < 85 && !cars.isEmpty()) s.problems.add(String.format(Locale.ROOT, "BRAKED MASS ONLY %.0f%% (NEED 85%%)", s.brakedPct));
        if (s.powered > 1 && mixed) s.problems.add("UNITS CAN'T WORK IN MULTIPLE (STEAM / NOT MU FITTED / MIXED)");
        if (s.powered > 0 && s.startGradePct < rulingGradePct) s.problems.add(String.format(Locale.ROOT, "CAN'T START ON %.1f%% - ONLY %.1f%%", rulingGradePct, s.startGradePct));
        if (s.cabs > 0 && ld instanceof LocomotiveDefinition && cars.size() > 1) {
            boolean endCab = cars.get(0).definition() instanceof LocomotiveDefinition && cars.get(cars.size() - 1).definition() instanceof LocomotiveDefinition;
            s.notes.add(endCab ? "PUSH-PULL: DRIVING CABS AT BOTH ENDS" : "DRIVING TRAILER NOT AT AN END");
        }
        if (s.kwPerT > 0 && s.kwPerT < 1.5) s.notes.add(String.format(Locale.ROOT, "SLOW: %.1f KW/T", s.kwPerT));
        return s;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("n", name);
        t.setInteger("lead", lead);
        t.setInteger("liv", livery);
        NBTTagList l = new NBTTagList();
        for (Car c : cars) {
            NBTTagCompound x = new NBTTagCompound();
            x.setString("d", c.def); x.setBoolean("f", c.flip); x.setString("t", c.texture);
            l.appendTag(x);
        }
        t.setTag("cars", l);
        return t;
    }

    public static Formation read(NBTTagCompound t) {
        Formation f = new Formation();
        f.name = t.getString("n");
        f.lead = t.getInteger("lead");
        f.livery = t.getInteger("liv");
        NBTTagList l = t.getTagList("cars", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount() && i < 64; i++) {
            NBTTagCompound x = l.getCompoundTagAt(i);
            f.cars.add(new Car(x.getString("d"), x.getBoolean("f"), x.getString("t")));
        }
        return f;
    }

    public static String shortName(EntityRollingStockDefinition d) {
        if (d == null) return "?";
        String n = d.name();
        return n == null ? d.defID : n;
    }
}
