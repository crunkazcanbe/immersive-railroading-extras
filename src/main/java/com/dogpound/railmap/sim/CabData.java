package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.auto.DriverAids;
import com.dogpound.railmap.network.PacketCab;
import net.minecraft.world.World;

/** What the train simulation adds to the driver's cab display (PacketCab -> client CabDisplay). */
public final class CabData {
    private CabData() {}

    /** lamp bits (DriverAids owns bits 0-6) */
    public static final int SLIP = 1 << 8, SLIDE = 1 << 9, WSP = 1 << 10, SAND = 1 << 11, SAND_LOW = 1 << 12, TC = 1 << 13,
            FAULT = 1 << 14, OVERLOAD = 1 << 15, PARTED = 1 << 16, ENGINE_RUN = 1 << 17, AESS = 1 << 18, DERATE = 1 << 19,
            AUTO_SAND = 1 << 20, WSP_ON = 1 << 21, HOT = 1 << 22, LOW_BATT = 1 << 23;

    // ---- the planning area (item 109): the protection / autopilot pass leaves what it read ahead here ------------------
    private static final java.util.Map<java.util.UUID, Object[]> PLAN = new java.util.HashMap<>();

    /** called by auto.Protection / Autopilot with the restrictions read ahead (distances along the path) and where the train is */
    public static void plan(java.util.UUID loco, java.util.List<com.dogpound.railmap.auto.Wayside.Limit> limits, double cur, com.dogpound.railmap.auto.TrackPath path,
                            String nextStop, double stopM) {
        java.util.List<float[]> l = new java.util.ArrayList<>();
        if (limits != null) for (com.dogpound.railmap.auto.Wayside.Limit x : limits) {
            double d = x.distance - cur;
            if (d < 0 || d > 4000) continue;
            if (x.stop) l.add(new float[]{(float) d, 0, 1});
            else if (x.kmh < 1e6) l.add(new float[]{(float) d, (float) x.kmh, 0});
        }
        if (nextStop != null && !nextStop.isEmpty() && stopM >= 0 && stopM < 4000) l.add(new float[]{(float) stopM, 0, 2});
        l.sort((a, b) -> Float.compare(a[0], b[0]));
        PLAN.put(loco, new Object[]{l, System.currentTimeMillis(), gradient(path, cur), path, cur});   // path + cur: neutral sections ahead (infra #36)
    }

    /** the gradient profile along the route ahead, from the rail heights of its pieces: {from m, per mille} in 100 m bits */
    static float[] gradient(com.dogpound.railmap.auto.TrackPath path, double cur) {
        if (path == null || path.steps.isEmpty()) return new float[0];
        java.util.List<Float> out = new java.util.ArrayList<>();
        double from = cur, prevY = height(path, cur);
        while (from + 100 <= Math.min(path.length, cur + 2000) && out.size() < 32) {
            double y = height(path, from + 100);
            out.add((float) (from - cur));
            out.add((float) ((y - prevY) / 100 * 1000));
            prevY = y;
            from += 100;
        }
        float[] r = new float[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static double height(com.dogpound.railmap.auto.TrackPath path, double d) {
        for (com.dogpound.railmap.auto.TrackPath.Step s : path.steps) if (s.distance >= d) return s.pos.y;
        return path.steps.get(path.steps.size() - 1).pos.y;
    }

    @SuppressWarnings("unchecked")
    private static void driverDisplay(World mc, Locomotive loco, SimData.Car c, PacketCab p) {
        p.throttle = loco.getThrottle();
        p.brake = loco.getTrainBrakePos();
        p.reverser = loco.getReverser();
        p.mrBar = loco.getMainAirReservoir() * 10;
        p.bcBar = loco.getBrakePressure() * 5;
        p.unit = EngineSim.label(loco);
        if (c != null) {
            p.aux = AuxSim.line(loco, c);
            p.auxBits = AuxSim.bits(c, loco);
            p.batV = (float) AuxSim.volts(c);
            p.cabC = Double.isNaN(c.cabC) ? -999 : (float) c.cabC;
            p.auxKw = (float) c.auxKw;
            p.auxCap = (float) c.auxCap;
        }
        // the consist: MU lines, and every unit's faults (a fault anywhere lights the lead's FAULT lamp)
        StringBuilder mu = new StringBuilder(MultiUnit.summary(loco.getUUID()));
        for (String s : MultiUnit.lines(mc, loco)) mu.append('\n').append(s);
        p.mu = mu.toString();
        StringBuilder f = new StringBuilder();
        SimData data = SimData.get(mc);
        MultiUnit.Group g = MultiUnit.group(loco.getUUID());
        java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
        if (g == null) ids.add(loco.getUUID()); else for (MultiUnit.Unit u : g.units) ids.add(u.id);
        int n = 0, i = 0;
        for (java.util.UUID id : ids) {
            i++;
            SimData.Car x = data.peek(id);
            if (x == null) continue;
            for (String s : x.faults) { n++; if (f.length() < 800) f.append(f.length() > 0 ? "\n" : "").append(ids.size() > 1 ? "U" + i + " " : "").append(s); }
        }
        p.faultCount = n;
        p.faults = f.toString();
        Object[] pl = PLAN.get(loco.getUUID());
        if (pl != null && System.currentTimeMillis() - (Long) pl[1] < 3000) {
            java.util.List<float[]> l = (java.util.List<float[]>) pl[0];
            if (pl.length > 4) {                                           // infra #36: neutral sections on the planning area + warning
                l = new java.util.ArrayList<>(l);
                String ns = com.dogpound.railmap.infra.NeutralSections.plan(mc, loco.getUUID(), (com.dogpound.railmap.auto.TrackPath) pl[3], (Double) pl[4], l);
                l.sort((a, b) -> Float.compare(a[0], b[0]));
                if (ns != null && p.note.isEmpty()) p.note = ns;
            }
            int k = Math.min(12, l.size());
            p.planD = new float[k]; p.planKmh = new float[k]; p.planKind = new byte[k];
            for (int j = 0; j < k; j++) { p.planD[j] = l.get(j)[0]; p.planKmh[j] = l.get(j)[1]; p.planKind[j] = (byte) l.get(j)[2]; }
        }
        if (pl != null && System.currentTimeMillis() - (Long) pl[1] < 3000) p.grade = (float[]) pl[2];
    }

    public static void fill(Locomotive loco, PacketCab p) {
        try {
            World mc = loco.getWorld().internal;
            long now = mc.getTotalWorldTime();
            int b = DriverAids.bits(loco.getUUID(), now);
            p.advice = DriverAids.advice(loco.getUUID());
            p.adviceKmh = (float) DriverAids.adviceKmh(loco.getUUID());
            SimData.Car c = SimData.get(mc).peek(loco.getUUID());
            if (c != null) {
                if (c.slip) b |= SLIP;
                if (c.slide) b |= SLIDE;
                if (c.wspActive) b |= WSP;
                if (c.sanding) b |= SAND;
                if (c.autoSand) b |= AUTO_SAND;
                if (c.wsp) b |= WSP_ON;
                if (c.tractionControl) b |= TC;
                float sand = loco.prideSandLevel();
                if (sand >= 0 && sand < 0.15f) b |= SAND_LOW;
                if (!c.faults.isEmpty()) b |= FAULT;
                if (c.derate < 1f) b |= DERATE;
                if (c.battery < 0.3f) b |= LOW_BATT;
                p.adh = AdhesionSim.line(loco, c);
                if (loco instanceof LocomotiveDiesel d && SimConfig.engineManagement) {
                    if (c.phase == EngineSim.RUN) b |= ENGINE_RUN;
                    if (c.aessStopped) b |= AESS;
                    if (d.getEngineTemperature() > 100) b |= HOT;
                    p.eng1 = EngineSim.line1(d, c);
                    p.eng2 = EngineSim.line2(d, c);
                }
                if (!c.faults.isEmpty()) p.fault = c.faults.iterator().next() + (c.faults.size() > 1 ? " (+" + (c.faults.size() - 1) + ")" : "");
            }
            driverDisplay(mc, loco, c, p);
            try { com.dogpound.railmap.steam.SteamTicker.cab(loco, p); com.dogpound.railmap.traction.Traction.cab(loco, p); } catch (LinkageError | RuntimeException ignored) { }   // batch 5 #2 boiler + #6 battery / hydrogen lines
            ConsistSim.Summary s = DriverAids.summary(loco.getUUID());
            if (s == null) s = ConsistSim.summary(mc, loco);
            if (SimData.get(mc).mode() > 0) {
                p.phys = ConsistSim.line(s);
                p.drawbar = (float) s.maxDraftN;
                p.buff = (float) s.maxBuffN;
                p.coupler = s.worstCoupler;
                double k = ConsistSim.scale(loco) * SimConfig.couplerStrength;
                if (s.maxDraftN > ConsistSim.DRAFT_WORK * k) b |= OVERLOAD;
            }
            if (ConsistSim.holding(loco.getUUID(), now)) b |= PARTED;
            if (p.faultCount > 0) b |= FAULT;
            p.aids = b;
        } catch (LinkageError | RuntimeException ignored) {
        }
    }
}
