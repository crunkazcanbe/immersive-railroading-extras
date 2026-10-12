package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.industry.IndustryKind;
import com.dogpound.railmap.industry.TileIndustry;
import com.dogpound.railmap.server.StationData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Living population + regional development (her "next 48" #13). A town is a Town Hall Planning Board (or a Town Market
 * the railway serves). Once a day each town grows or shrinks toward what it can support:
 * <ul>
 *   <li><b>transport access</b>: the stations within its catchment, how many of its people they carry and how good the
 *   service is there (satisfaction, waits);</li>
 *   <li><b>jobs</b>: running industries within reach, against its working-age population;</li>
 *   <li><b>goods</b>: whether its markets are being supplied by train;</li>
 *   <li><b>environment</b>: railway noise over 55 dB at the town and its noise barriers (#18);</li>
 *   <li>a public-service line calling there (#38).</li>
 * </ul>
 * It never shrinks below its founding size. Population feeds back: Town Market population is the town's, and each
 * station's catchment takes a share of every hall town within reach (split between the stations by distance), which is
 * what generates its passenger demand. Tiers HAMLET .. METROPOLIS; the planning board shows the 14-day history.
 */
public final class Towns implements BizData.Part {
    public static final String[] TIERS = {"HAMLET", "VILLAGE", "TOWN", "CITY", "METROPOLIS"};

    public static final class Town {
        public int id;
        public String name = "";
        public long pos;
        public boolean hall;                 // a planning board (else a town market)
        public double pop, founded;
        public double access, jobs, goods = 50, noiseDb, growthPct;
        public int barriers, stations;
        public double jobsAvail;
        public final double[] hist = new double[14];
        public String why = "";
        public String tier() { return TIERS[pop < 300 ? 0 : pop < 1000 ? 1 : pop < 5000 ? 2 : pop < 20000 ? 3 : 4]; }
        public BlockPos at() { return BlockPos.fromLong(pos); }
    }

    public final Map<Long, Town> towns = new LinkedHashMap<>();
    private int nextId = 1;

    @Override public String key() { return "towns"; }

    public Town at(long pos) { return towns.get(pos); }
    public Town byId(int id) { for (Town t : towns.values()) if (t.id == id) return t; return null; }

    public Town found(World w, BlockPos p, String name, boolean hall, double pop) {
        Town t = towns.get(p.toLong());
        if (t != null) return t;
        t = new Town();
        t.id = nextId++; t.pos = p.toLong(); t.hall = hall; t.pop = t.founded = Math.max(50, pop);
        t.name = name == null || name.isEmpty() ? nearestStation(w, p, "Town " + t.id) : name;
        Arrays.fill(t.hist, t.pop);
        towns.put(t.pos, t);
        Recorder.event(w, "Town founded: " + t.name + " (" + Math.round(t.pop) + " people)");
        return t;
    }

    public void remove(long pos) { towns.remove(pos); }

    static String nearestStation(World w, BlockPos p, String dflt) {
        String best = dflt;
        double bd = 256 * 256;
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) { double d = BlockPos.fromLong(e.getKey()).distanceSq(p); if (d < bd) { bd = d; best = e.getValue(); } }
        return best;
    }

    /** Town Market population: the town's, once it is one (else what the market says) */
    public long marketPop(World w, BlockPos p, long base) {
        Town t = towns.get(p.toLong());
        if (t == null) { if (!BizConfig.townGrowth) return base; t = found(w, p, "", false, base); }
        return Math.round(t.pop);
    }

    /** the share of the hall towns' people living in this station's catchment (split by distance between stations) */
    public double catchment(World w, BlockPos station) {
        double r = BizConfig.townCatchment, sum = 0;
        for (Town t : towns.values()) {
            if (!t.hall) continue;
            double d = Math.sqrt(t.at().distanceSq(station));
            if (d >= r) continue;
            double mine = 1 / Math.max(16, d), all = 0;
            for (long k : StationData.get(w).names().keySet()) { double dd = Math.sqrt(t.at().distanceSq(BlockPos.fromLong(k))); if (dd < r) all += 1 / Math.max(16, dd); }
            sum += t.pop * 0.6 * (all <= 0 ? 1 : mine / all) * (1 - d / (r * 1.25));   // 60 % of people live near enough to use a station at all
        }
        return sum;
    }

    @Override
    public void daily(World w, BizData d, long day) {
        Set<Long> loaded = TileIndustry.LOADED.get(w.provider.getDimension());
        List<TileIndustry> inds = new ArrayList<>();
        if (loaded != null) for (long l : loaded) { BlockPos p = BlockPos.fromLong(l); if (w.isBlockLoaded(p) && w.getTileEntity(p) instanceof TileIndustry ti) inds.add(ti); }
        PassengerData pd = PassengerData.get(w);
        for (Town t : towns.values()) {
            BlockPos at = t.at();
            // transport access
            double rides = 0, sat = 0, n = 0;
            for (PassengerData.Stn s : pd.stations()) {
                double dd = Math.sqrt(BlockPos.fromLong(s.key).distanceSq(at));
                if (dd >= BizConfig.townCatchment) continue;
                n++; rides += s.boardedYesterday; sat += s.sat;
            }
            t.stations = (int) n;
            t.access = n == 0 ? 0 : Math.min(100, 30 + 40 * Math.min(1, rides / Math.max(1, t.pop * 0.05)) + 30 * sat / n / 100);
            // jobs
            double jobs = 0;
            boolean supplied = false, anyMarket = false;
            for (TileIndustry ti : inds) {
                double dd = Math.sqrt(ti.getPos().distanceSq(at));
                if (ti.kind() == IndustryKind.TOWN_MARKET) { if (dd < 96) { anyMarket = true; supplied |= ti.endpointState().equals("RUNNING"); } continue; }
                if (dd < 256 && ti.endpointState().equals("RUNNING")) jobs += ti.kind().makesFE > 0 ? 300 : 150;
            }
            jobs += d.intermodal.jobsNear(at);
            t.jobsAvail = jobs;
            t.jobs = Math.min(150, jobs / Math.max(1, t.pop * 0.45) * 100);
            t.goods = anyMarket ? supplied ? Math.min(100, t.goods + 20) : Math.max(0, t.goods - 15) : Math.max(30, t.goods - 2);
            t.noiseDb = d.env.noiseAt(at, t.barriers);
            boolean pso = d.pso.servesNear(w, at);
            // growth toward what it can support
            double g = 0.02 * (t.access - 50) / 50 + 0.015 * (Math.min(t.jobs, 150) / 100 - 0.8) + 0.01 * (t.goods - 50) / 50 + (pso ? 0.004 : 0);
            String why = (t.access < 40 ? "poor rail access; " : "") + (t.jobs < 60 ? "few jobs; " : "") + (t.goods < 40 ? "short of goods; " : "");
            if (BizConfig.noiseAffectsTowns && t.noiseDb > 55) { g -= (t.noiseDb - 55) * 0.001; why += "railway noise " + Math.round(t.noiseDb) + " dB; "; }
            g = Math.max(-0.03, Math.min(0.03, g));
            if (!BizConfig.townGrowth) g = 0;
            t.growthPct = g * 100;
            t.pop = Math.max(t.founded, t.pop * (1 + g));
            t.why = why.isEmpty() ? (g > 0 ? "growing: good service, work and goods" : "steady") : why.substring(0, why.length() - 2);
            System.arraycopy(t.hist, 0, t.hist, 1, t.hist.length - 1);
            t.hist[0] = t.pop;
            String tier = t.tier();
            if (!tier.equals(TIERS[t.hist[1] < 300 ? 0 : t.hist[1] < 1000 ? 1 : t.hist[1] < 5000 ? 2 : t.hist[1] < 20000 ? 3 : 4]))
                d.note(w, t.name + " is now a " + tier.toLowerCase(Locale.ROOT) + " (" + Math.round(t.pop) + " people)");
        }
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound tag) {
        towns.clear();
        NBTTagList l = tag.getTagList("t", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Town t = new Town();
            t.id = c.getInteger("id"); t.name = c.getString("n"); t.pos = c.getLong("p"); t.hall = c.getBoolean("h");
            t.pop = c.getDouble("pop"); t.founded = c.getDouble("f"); t.access = c.getDouble("a"); t.jobs = c.getDouble("j"); t.goods = c.getDouble("g");
            t.noiseDb = c.getDouble("nz"); t.growthPct = c.getDouble("gr"); t.barriers = c.getInteger("b"); t.stations = c.getInteger("s"); t.why = c.getString("why");
            t.jobsAvail = c.getDouble("ja");
            int[] h = c.getIntArray("hist");
            for (int k = 0; k < t.hist.length; k++) t.hist[k] = k < h.length ? h[k] : t.pop;
            towns.put(t.pos, t);
        }
        nextId = Math.max(1, tag.getInteger("next"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Town t : towns.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", t.id); c.setString("n", t.name); c.setLong("p", t.pos); c.setBoolean("h", t.hall);
            c.setDouble("pop", t.pop); c.setDouble("f", t.founded); c.setDouble("a", t.access); c.setDouble("j", t.jobs); c.setDouble("g", t.goods);
            c.setDouble("nz", t.noiseDb); c.setDouble("gr", t.growthPct); c.setInteger("b", t.barriers); c.setInteger("s", t.stations); c.setString("why", t.why);
            c.setDouble("ja", t.jobsAvail);
            int[] h = new int[t.hist.length];
            for (int k = 0; k < h.length; k++) h[k] = (int) Math.round(t.hist[k]);
            c.setIntArray("hist", h);
            l.appendTag(c);
        }
        tag.setTag("t", l);
        tag.setInteger("next", nextId);
        return tag;
    }
}
