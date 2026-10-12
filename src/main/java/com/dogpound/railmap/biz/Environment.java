package com.dogpound.railmap.biz;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.ohle.OhleData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Environmental impact accounting (her "next 48" #18). Measured, not guessed:
 * <ul>
 *   <li><b>energy</b>: diesel - every 5 s each working diesel's power at the rail (tractive effort x speed) becomes
 *   kWh, litres of fuel (3 kWh at the rail per litre), CO2 (2.68 kg/L), NOx and particulates; electric - the kWh the
 *   pantographs drew and gave back (the OHLE energy ledger, #33), CO2 at the grid's carbon intensity, regenerated kWh
 *   credited;</li>
 *   <li><b>noise</b>: each moving train's sound level at 25 m (speed, traction) spread to every town within 256 blocks
 *   (6 dB per doubled distance, 10 dB per noise barrier), energy-averaged over the day (Leq);</li>
 *   <li><b>land</b> (stations, completed construction projects) and <b>waste</b> (spoil, washings, scrap).</li>
 * </ul>
 * Per company and in total, with the comparison the list asked for: kg CO2 per train-km diesel vs electric, and what
 * regenerative braking saved. MITIGATION investments are bought at the Town Hall Planning Board or by command, cost
 * real company money and change the numbers: noise barriers, a depot solar farm, diesel particulate filters, tree
 * planting, water recycling.
 */
public final class Environment implements BizData.Part {
    public enum Measure {
        NOISE_BARRIER("Noise barrier", 40_000, "-10 dB at the town it shields"),
        SOLAR_DEPOT("Depot solar farm", 90_000, "-15% grid carbon for the company's electric trains"),
        DPF("Diesel particulate filters", 35_000, "-70% particulates, -30% NOx from the company's diesels"),
        TREES("Tree planting", 15_000, "absorbs 120 kg CO2 a day"),
        WATER("Water recycling", 25_000, "-60% waste water from washing");
        public final String label, benefit;
        public final long cost;
        Measure(String label, long cost, String benefit) { this.label = label; this.cost = cost; this.benefit = benefit; }
    }

    public static final class Acct {                    // one company (id 0 = trains nobody owns)
        public double dieselKwh, fuelL, co2Kg, noxG, pmG, elecKwh, regenKwh, dieselKm, elecKm, wasteT;
        public double co2Yesterday;
        public final int[] measures = new int[Measure.values().length];
        public final double[] co2Hist = new double[14];
        void zero() { dieselKwh = fuelL = co2Kg = noxG = pmG = elecKwh = regenKwh = dieselKm = elecKm = wasteT = 0; }
    }

    public final Map<Integer, Acct> accts = new LinkedHashMap<>();
    /** town position -> {sound energy today, Leq yesterday dB} */
    private final Map<Long, double[]> noise = new HashMap<>();
    public double landHa, wasteT;
    public int projectsBuilt;
    private final transient Map<UUID, double[]> lastPos = new HashMap<>();
    private final transient Map<UUID, double[]> lastKwh = new HashMap<>();

    @Override public String key() { return "environment"; }

    public Acct acct(int company) { return accts.computeIfAbsent(company, k -> new Acct()); }

    public double noiseAt(BlockPos town, int barriers) { double[] n = noise.get(town.toLong()); return n == null ? 35 : n[1]; }

    public void waste(World w, Company c, double tonnes, String why) { acct(c == null ? 0 : c.id).wasteT += tonnes; wasteT += tonnes; }

    public void built(double hectares, double spoilT) { projectsBuilt++; landHa += hectares; wasteT += spoilT; }

    public String invest(World w, BizData d, Company c, Measure m, Towns.Town t) {
        if (c == null) return "§cMitigation is paid for by a railway company";
        if (Research.locked(w, c, Research.Tech.GREEN_TECH) && m != Measure.NOISE_BARRIER && m != Measure.TREES) return Research.why(Research.Tech.GREEN_TECH);
        if (m == Measure.NOISE_BARRIER && t == null) return "§cA noise barrier is built for a town - use the town's planning board";
        if (!BizData.pay(w, c, Company.Kind.MITIGATION, m.cost, m.label + (t == null ? "" : " at " + t.name))) return "§c" + c.name + " can't afford " + Coins.fmt(m.cost);
        acct(c.id).measures[m.ordinal()]++;
        if (t != null && m == Measure.NOISE_BARRIER) t.barriers++;
        d.note(w, c.mark + " invested in " + m.label.toLowerCase(Locale.ROOT) + (t == null ? "" : " at " + t.name) + " (" + Coins.fmt(m.cost) + "): " + m.benefit);
        d.mastery.count(w, c, null, "green", 1);
        return "§a" + m.label + " bought for " + Coins.fmt(m.cost) + " - " + m.benefit;
    }

    @Override
    public void tick(World w, BizData d, long now) {
        CompanyData cd = CompanyData.get(w);
        double gridKgPerKwh = BizConfig.gridGramsPerKwh / 1000.0;
        OhleData od = OhleData.get(w);
        List<LocomotiveDiesel> locos;
        try { locos = cam72cam.mod.world.World.get(w).getEntities(LocomotiveDiesel.class); } catch (RuntimeException | LinkageError e) { return; }
        for (LocomotiveDiesel l : locos) {
            if (l.isDead()) continue;
            UUID u = l.getUUID();
            Company c = cd.ownerOfTrain(u, trainLine(w, u));
            Acct a = acct(c == null ? 0 : c.id);
            double v = l.getCurrentSpeed() == null ? 0 : Math.abs(l.getCurrentSpeed().metersPerSecond());
            cam72cam.mod.math.Vec3d p = l.getPosition();
            double[] lp = lastPos.put(u, new double[]{p.x, p.z});
            double km = lp == null ? 0 : Math.min(1.0, Math.hypot(p.x - lp[0], p.z - lp[1]) / 1000.0);
            if (l.prideElectric) {
                a.elecKm += km;
                OhleData.Pan pan = od.pans.get(u);
                if (pan != null) {
                    double[] last = lastKwh.put(u, new double[]{pan.kwhIn, pan.kwhRegen});
                    if (last != null) {
                        double in = Math.max(0, pan.kwhIn - last[0]), back = Math.max(0, pan.kwhRegen - last[1]);
                        a.elecKwh += in; a.regenKwh += back;
                        double solar = 1 - 0.15 * Math.min(4, a.measures[Measure.SOLAR_DEPOT.ordinal()]);
                        a.co2Kg += Math.max(0, in - back) * gridKgPerKwh * solar;
                    }
                }
            } else {
                a.dieselKm += km;
                if (Math.abs(l.getThrottle()) > 0.01f && v > 0.1) {
                    double watts;
                    try { watts = Math.abs(l.getAppliedTractiveEffort(l.getCurrentSpeed())) * v; } catch (RuntimeException e) { watts = 0; }
                    double kwh = Math.min(20, watts * 5 / 3.6e6);
                    double litres = kwh / 3.0;
                    int dpf = Math.min(1, a.measures[Measure.DPF.ordinal()]);
                    a.dieselKwh += kwh; a.fuelL += litres; a.co2Kg += litres * 2.68;
                    a.noxG += kwh * 9 * (dpf > 0 ? 0.7 : 1); a.pmG += kwh * 0.2 * (dpf > 0 ? 0.3 : 1);
                }
            }
            if (v > 2) {                                                         // noise into the towns
                double l25 = 72 + 30 * Math.log10(Math.max(1, v * 3.6) / 50) + (l.prideElectric ? 0 : 4);
                for (Towns.Town t : d.towns.towns.values()) {
                    double dist = Math.max(25, Math.hypot(p.x - t.at().getX(), p.z - t.at().getZ()));
                    if (dist > 256) continue;
                    double at = l25 - 20 * Math.log10(dist / 25) - 10 * Math.min(2, t.barriers);
                    noise.computeIfAbsent(t.pos, k -> new double[]{0, 35})[0] += Math.pow(10, at / 10) * 5;
                }
            }
        }
        if (lastPos.size() > 500) { lastPos.clear(); lastKwh.clear(); }
    }

    private static String trainLine(World w, UUID u) {
        com.dogpound.railmap.auto.RailwayData.AutoTrain a = com.dogpound.railmap.auto.RailwayData.get(w).train(u);
        return a == null ? null : a.line;
    }

    @Override
    public void daily(World w, BizData d, long day) {
        double daySeconds = BizData.DAY / 20.0;
        for (Map.Entry<Long, double[]> e : noise.entrySet()) {
            double[] n = e.getValue();
            n[1] = n[0] <= 0 ? 35 : Math.max(35, 10 * Math.log10(n[0] / daySeconds));
            n[0] = 0;
        }
        noise.keySet().removeIf(k -> !d.towns.towns.containsKey(k));
        for (Acct a : accts.values()) {
            a.co2Kg = Math.max(0, a.co2Kg - 120 * a.measures[Measure.TREES.ordinal()]);
            a.co2Yesterday = a.co2Kg;
            System.arraycopy(a.co2Hist, 0, a.co2Hist, 1, a.co2Hist.length - 1);
            a.co2Hist[0] = a.co2Kg;
            a.zero();
        }
        landHa = com.dogpound.railmap.server.StationData.get(w).names().size() * 0.4 + projectsBuilt * 2.0;
    }

    /** kg CO2 per train-km: diesel, electric (today) */
    public double[] intensity() {
        double dk = 0, dc = 0, ek = 0, ec = 0, regen = 0, ekwh = 0;
        for (Acct a : accts.values()) { dk += a.dieselKm; dc += a.fuelL * 2.68; ek += a.elecKm; ec += Math.max(0, a.elecKwh - a.regenKwh) * BizConfig.gridGramsPerKwh / 1000.0; regen += a.regenKwh; ekwh += a.elecKwh; }
        return new double[]{dk > 0.05 ? dc / dk : -1, ek > 0.05 ? ec / ek : -1, regen * BizConfig.gridGramsPerKwh / 1000.0, ekwh > 0 ? regen / ekwh * 100 : 0};
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        accts.clear(); noise.clear();
        NBTTagCompound ac = t.getCompoundTag("acct");
        for (String k : ac.getKeySet()) {
            NBTTagCompound c = ac.getCompoundTag(k);
            Acct a = new Acct();
            a.dieselKwh = c.getDouble("dk"); a.fuelL = c.getDouble("f"); a.co2Kg = c.getDouble("co2"); a.noxG = c.getDouble("nox"); a.pmG = c.getDouble("pm");
            a.elecKwh = c.getDouble("ek"); a.regenKwh = c.getDouble("rk"); a.dieselKm = c.getDouble("dkm"); a.elecKm = c.getDouble("ekm"); a.wasteT = c.getDouble("w");
            a.co2Yesterday = c.getDouble("y");
            int[] m = c.getIntArray("m");
            System.arraycopy(m, 0, a.measures, 0, Math.min(m.length, a.measures.length));
            int[] h = c.getIntArray("h");
            for (int i = 0; i < Math.min(h.length, a.co2Hist.length); i++) a.co2Hist[i] = h[i];
            try { accts.put(Integer.parseInt(k), a); } catch (NumberFormatException ignored) { }
        }
        NBTTagCompound nz = t.getCompoundTag("noise");
        for (String k : nz.getKeySet()) try { noise.put(Long.parseLong(k), new double[]{0, nz.getDouble(k)}); } catch (NumberFormatException ignored) { }
        landHa = t.getDouble("land"); wasteT = t.getDouble("waste"); projectsBuilt = t.getInteger("built");
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound ac = new NBTTagCompound();
        for (Map.Entry<Integer, Acct> e : accts.entrySet()) {
            Acct a = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setDouble("dk", a.dieselKwh); c.setDouble("f", a.fuelL); c.setDouble("co2", a.co2Kg); c.setDouble("nox", a.noxG); c.setDouble("pm", a.pmG);
            c.setDouble("ek", a.elecKwh); c.setDouble("rk", a.regenKwh); c.setDouble("dkm", a.dieselKm); c.setDouble("ekm", a.elecKm); c.setDouble("w", a.wasteT);
            c.setDouble("y", a.co2Yesterday); c.setIntArray("m", a.measures);
            int[] h = new int[a.co2Hist.length];
            for (int i = 0; i < h.length; i++) h[i] = (int) Math.round(a.co2Hist[i]);
            c.setIntArray("h", h);
            ac.setTag(String.valueOf(e.getKey()), c);
        }
        t.setTag("acct", ac);
        NBTTagCompound nz = new NBTTagCompound();
        for (Map.Entry<Long, double[]> e : noise.entrySet()) nz.setDouble(String.valueOf(e.getKey()), e.getValue()[1]);
        t.setTag("noise", nz);
        t.setDouble("land", landHa); t.setDouble("waste", wasteT); t.setInteger("built", projectsBuilt);
        return t;
    }
}
