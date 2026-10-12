package com.dogpound.railmap.biz;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.station.StationFacilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Public-service obligations (her "next 48" #38). The county puts loss-making but needed lines out to TENDER: a line
 * whose towns have poor rail access. Companies bid the daily SUBSIDY they would need (a reverse auction - the lowest
 * bid wins after two days). The winning CONTRACT sets service requirements, checked every day from the passenger
 * records: departures a day, punctuality, every station on the line served, a service span (first train by 07:00,
 * last after 21:00) and optionally step-free access at every stop. Met: the day's subsidy is paid (on top of the
 * per-rider PSO support a PSO line already earns). Missed: withheld for that day; with psoClawback on, five missed
 * days in a contract week also claw back that week's payments. PSO operators get priority in capacity auction ties.
 */
public final class Pso implements BizData.Part {
    public static final class Tender {
        public int id;
        public String line = "", why = "";
        public long opened, closes;
        public int minDeps = 8, minPunct = 80;
        public boolean stepFree;
        public final Map<Integer, Long> bids = new LinkedHashMap<>();
        public String state = "OPEN";        // OPEN, AWARDED, VOID
    }

    public static final class Contract {
        public int id, company, minDeps, minPunct, weeks, metDays, missedDays, weekMissed;
        public String line = "";
        public long subsidyDay, from, until, paid, weekPaid;
        public boolean stepFree;
        public String lastCheck = "";
        public String state = "LIVE";        // LIVE, ENDED
    }

    public final List<Tender> tenders = new ArrayList<>();
    public final List<Contract> contracts = new ArrayList<>();
    private int nextId = 1;

    @Override public String key() { return "pso"; }

    public Contract live(String line) { for (Contract c : contracts) if (c.state.equals("LIVE") && c.line.equals(line)) return c; return null; }

    public int priority(World w, Company c, Capacity.Resource r) {
        if (c == null || r == null || r.kind != Capacity.Kind.CORRIDOR) return 0;
        Contract k = live(r.name);
        return k != null && k.company == c.id ? 1 : 0;
    }

    public boolean servesNear(World w, BlockPos at) {
        RailwayData rd = RailwayData.get(w);
        for (Contract c : contracts) {
            if (!c.state.equals("LIVE")) continue;
            RailwayData.Line l = rd.line(c.line);
            if (l != null) for (long s : l.stations) if (BlockPos.fromLong(s).distanceSq(at) < (double) BizConfig.townCatchment * BizConfig.townCatchment) return true;
        }
        return false;
    }

    public boolean companyServes(World w, Company c, BlockPos at) {
        RailwayData rd = RailwayData.get(w);
        for (String n : c.lines) { RailwayData.Line l = rd.line(n); if (l != null) for (long s : l.stations) if (BlockPos.fromLong(s).distanceSq(at) < (double) BizConfig.townCatchment * BizConfig.townCatchment) return true; }
        return false;
    }

    public String bid(World w, Company c, int tender, long subsidy) {
        for (Tender t : tenders) if (t.id == tender && t.state.equals("OPEN")) {
            if (subsidy < 100 || subsidy > 200_000) return "§cBid a daily subsidy between $1 and $2,000";
            t.bids.put(c.id, subsidy);
            return "§aPSO tender T" + t.id + " (" + t.line + "): " + c.mark + " bids " + Coins.fmt(subsidy) + " a day - lowest bid wins on day " + t.closes / BizData.DAY;
        }
        return "§7No open tender T" + tender;
    }

    /** the requirement check for a contract, today; "" = met */
    public String check(World w, Contract k) {
        PassengerData pd = PassengerData.get(w);
        PassengerData.LineStat ls = pd.line(k.line);
        RailwayData.Line l = RailwayData.get(w).line(k.line);
        if (ls == null || l == null) return "line gone";
        StringBuilder b = new StringBuilder();
        if (ls.arrivalsDay < k.minDeps) b.append(ls.arrivalsDay).append('/').append(k.minDeps).append(" departures; ");
        if (ls.punct < k.minPunct) b.append(Math.round(ls.punct)).append("% punctual (").append(k.minPunct).append("% needed); ");
        long now = w.getTotalWorldTime(), dayStart = now - now % BizData.DAY;   // checked in the day's last 5 s
        int unserved = 0, notSf = 0;
        for (long s : l.stations) {
            PassengerData.Stn st = pd.stn(s);
            if (st == null || st.lastArrival < dayStart) unserved++;
            StationFacilities.Audit a = StationFacilities.peek(w, s);
            if (k.stepFree && a != null && a.audited && !a.stepFree) notSf++;
        }
        if (unserved > 0) b.append(unserved).append(" stations not served; ");
        double[] h = new double[24];
        for (long s : l.stations) { PassengerData.Stn st = pd.stn(s); if (st != null) for (int i = 0; i < 24; i++) h[i] += st.hourly[i]; }
        boolean early = false, late = false;
        for (int i = 0; i <= 7; i++) early |= h[i] > 0;
        for (int i = 21; i < 24; i++) late |= h[i] > 0;
        if (ls.ridersDay > 0 && (!early || !late)) b.append("service span short (").append(early ? "" : "no early train").append(!early && !late ? ", " : "").append(late ? "" : "no late train").append("); ");
        if (notSf > 0) b.append(notSf).append(" stops not step-free; ");
        return b.length() == 0 ? "" : b.substring(0, b.length() - 2);
    }

    public void transfer(int from, int to, String line) { for (Contract c : contracts) if (c.company == from && c.line.equals(line)) c.company = to; }
    public void transferAll(int from, int to) { for (Contract c : contracts) if (c.company == from) c.company = to; for (Tender t : tenders) { Long b = t.bids.remove(from); if (b != null) t.bids.putIfAbsent(to, b); } }

    /** the last 5 s of the day, before the passenger day rolls over: was the service run? */
    @Override
    public void dayEnd(World w, BizData d, long day) {
        CompanyData cd = CompanyData.get(w);
        long now = w.getTotalWorldTime();
        for (Contract k : contracts) {
            if (!k.state.equals("LIVE")) continue;
            Company c = cd.byId(k.company);
            if (c == null || now > k.until) { k.state = "ENDED"; if (c != null) c.pso.remove(k.line); d.note(w, "PSO contract P" + k.id + " " + k.line + " ended"); continue; }
            String miss = check(w, k);
            k.lastCheck = miss.isEmpty() ? "MET" : miss;
            if (miss.isEmpty()) {
                k.metDays++;
                cd.credit(w, c, Company.Kind.SUBSIDY, k.subsidyDay, "PSO contract P" + k.id + " " + k.line + ": requirements met");
                k.paid += k.subsidyDay; k.weekPaid += k.subsidyDay;
                d.mastery.count(w, c, null, "pso", 1);
            } else {
                k.missedDays++; k.weekMissed++;
                d.note(w, "PSO P" + k.id + " " + k.line + " subsidy withheld: " + miss);
            }
            if ((day % 7) == 0) {
                if (BizConfig.psoClawback && k.weekMissed >= 5 && k.weekPaid > 0) {
                    BizData.pay(w, c, Company.Kind.FINES, k.weekPaid, "PSO clawback P" + k.id + " (" + k.weekMissed + " days missed)");
                    d.note(w, "PSO P" + k.id + " clawback " + Coins.fmt(k.weekPaid));
                }
                k.weekMissed = 0; k.weekPaid = 0;
            }
        }
    }

    @Override
    public void daily(World w, BizData d, long day) {
        CompanyData cd = CompanyData.get(w);
        long now = w.getTotalWorldTime();
        // tenders close: lowest bid wins
        for (Tender t : tenders) {
            if (!t.state.equals("OPEN") || now < t.closes) continue;
            Map.Entry<Integer, Long> best = null;
            for (Map.Entry<Integer, Long> e : t.bids.entrySet()) if (cd.byId(e.getKey()) != null && (best == null || e.getValue() < best.getValue())) best = e;
            if (best == null) { t.state = "VOID"; continue; }
            t.state = "AWARDED";
            Contract k = new Contract();
            k.id = t.id; k.company = best.getKey(); k.line = t.line; k.subsidyDay = best.getValue(); k.minDeps = t.minDeps; k.minPunct = t.minPunct; k.stepFree = t.stepFree;
            k.weeks = 4; k.from = now; k.until = now + 28 * BizData.DAY;
            contracts.add(k);
            Company c = cd.byId(k.company);
            c.pso.add(k.line);
            d.note(w, "PSO tender T" + t.id + " " + t.line + " awarded to " + c.mark + " at " + Coins.fmt(k.subsidyDay) + "/day for 4 weeks");
        }
        tenders.removeIf(t -> !t.state.equals("OPEN") && now - t.closes > 7 * BizData.DAY);
        contracts.removeIf(k -> k.state.equals("ENDED") && now - k.until > 14 * BizData.DAY);
        // the county posts new tenders for lines serving under-served towns
        int open = 0;
        for (Tender t : tenders) if (t.state.equals("OPEN")) open++;
        RailwayData rd = RailwayData.get(w);
        for (RailwayData.Line l : rd.lines().values()) {
            if (open >= 3) break;
            if (live(l.name) != null) continue;
            boolean pending = false;
            for (Tender t : tenders) if (t.line.equals(l.name) && t.state.equals("OPEN")) pending = true;
            if (pending) continue;
            Towns.Town poor = null;
            for (Towns.Town tw : d.towns.towns.values()) {
                if (tw.access >= 45 || tw.pop < 150) continue;
                for (long s : l.stations) if (BlockPos.fromLong(s).distanceSq(tw.at()) < (double) BizConfig.townCatchment * BizConfig.townCatchment) { poor = tw; break; }
                if (poor != null) break;
            }
            if (poor == null) continue;
            Tender t = new Tender();
            t.id = nextId++; t.line = l.name; t.opened = now; t.closes = now + 2 * BizData.DAY; t.why = poor.name + " has poor rail access (" + Math.round(poor.access) + ")";
            t.minDeps = Math.max(4, l.stations.size() * 2); t.minPunct = 80; t.stepFree = poor.pop > 2000;
            tenders.add(t);
            open++;
            d.note(w, "County tender T" + t.id + ": public service on " + l.name + " - " + t.why);
        }
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        tenders.clear(); contracts.clear();
        NBTTagList l = t.getTagList("t", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Tender x = new Tender();
            x.id = c.getInteger("id"); x.line = c.getString("l"); x.why = c.getString("why"); x.opened = c.getLong("o"); x.closes = c.getLong("c");
            x.minDeps = c.getInteger("md"); x.minPunct = c.getInteger("mp"); x.stepFree = c.getBoolean("sf"); x.state = c.getString("s");
            NBTTagCompound b = c.getCompoundTag("b");
            for (String k : b.getKeySet()) try { x.bids.put(Integer.parseInt(k), b.getLong(k)); } catch (NumberFormatException ignored) { }
            tenders.add(x);
        }
        l = t.getTagList("k", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Contract k = new Contract();
            k.id = c.getInteger("id"); k.company = c.getInteger("c"); k.minDeps = c.getInteger("md"); k.minPunct = c.getInteger("mp"); k.weeks = c.getInteger("wk");
            k.metDays = c.getInteger("met"); k.missedDays = c.getInteger("mis"); k.weekMissed = c.getInteger("wm"); k.line = c.getString("l");
            k.subsidyDay = c.getLong("sd"); k.from = c.getLong("f"); k.until = c.getLong("u"); k.paid = c.getLong("p"); k.weekPaid = c.getLong("wp");
            k.stepFree = c.getBoolean("sf"); k.lastCheck = c.getString("lc"); k.state = c.getString("s");
            contracts.add(k);
        }
        nextId = Math.max(1, t.getInteger("next"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Tender x : tenders) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", x.id); c.setString("l", x.line); c.setString("why", x.why); c.setLong("o", x.opened); c.setLong("c", x.closes);
            c.setInteger("md", x.minDeps); c.setInteger("mp", x.minPunct); c.setBoolean("sf", x.stepFree); c.setString("s", x.state);
            NBTTagCompound b = new NBTTagCompound();
            for (Map.Entry<Integer, Long> e : x.bids.entrySet()) b.setLong(String.valueOf(e.getKey()), e.getValue());
            c.setTag("b", b);
            l.appendTag(c);
        }
        t.setTag("t", l);
        l = new NBTTagList();
        for (Contract k : contracts) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", k.id); c.setInteger("c", k.company); c.setInteger("md", k.minDeps); c.setInteger("mp", k.minPunct); c.setInteger("wk", k.weeks);
            c.setInteger("met", k.metDays); c.setInteger("mis", k.missedDays); c.setInteger("wm", k.weekMissed); c.setString("l", k.line);
            c.setLong("sd", k.subsidyDay); c.setLong("f", k.from); c.setLong("u", k.until); c.setLong("p", k.paid); c.setLong("wp", k.weekPaid);
            c.setBoolean("sf", k.stepFree); c.setString("lc", k.lastCheck); c.setString("s", k.state);
            l.appendTag(c);
        }
        t.setTag("k", l);
        t.setInteger("next", nextId);
        return t;
    }
}
