package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.depot.Predict;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.incident.IncidentLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.*;

/**
 * Insurance, liability and claims (her "next 48" #33), driven by the railway's own records:
 * <ul>
 *   <li>a company takes a POLICY (third-party or comprehensive, with an excess); the daily premium is priced from its
 *   fleet, its MAINTENANCE COMPLIANCE (units past their exam plan, failed brake tests - the depot's records), its
 *   claims in the last four weeks, its last audit score (#34) and Safety Management research (#32);</li>
 *   <li>every closed INCIDENT (wreck, failed train, fire, hazmat, evacuation) involving its vehicles becomes a claim:
 *   own damage by severity and vehicles, third-party LIABILITY per passenger affected;</li>
 *   <li>the loss ADJUSTER scores the EVIDENCE: each vehicle's exam up to date, brake certificate, open faults, the train
 *   recorder download, the incident's final report, and the documents filed in the Insurance Desk's evidence drawer.
 *   Score 80+ pays in full, 50+ pays 70 %, below that 40 %; a vehicle overdue for its exam AND with a failed brake test
 *   is NEGLIGENCE - the own-damage claim is denied (liability is still paid to the public);</li>
 *   <li>cargo claims from cargo investigations (#17) are paid by the liable party's insurer to the box owner.</li>
 * </ul>
 * Without a policy nothing is claimed - and nothing extra is charged (an old world plays as before).
 */
public final class Insurance implements BizData.Part {
    public enum Cover { NONE, THIRD_PARTY, COMPREHENSIVE }
    public static final long[] EXCESS = {0, 5_000, 20_000, 50_000};

    public static final class Policy {
        public Cover cover = Cover.NONE;
        public int excess = 1;
        public long premiumDay, paidTotal, claimsPaid;
        public double compliance = 100;
        public int overdue, brakeFails, units;
        public int evidenceDocs;              // documents in the insurance desk's evidence drawer (updated by the desk)
    }

    public static final class Claim {
        public int id, company, incident = -1;
        public String what = "", state = "FILED", notes = "";   // FILED, PAID, PART, DENIED
        public long filed, value, liability, paid;
        public int evidence;
        public boolean cargo;
    }

    public final Map<Integer, Policy> policies = new LinkedHashMap<>();
    public final List<Claim> claims = new ArrayList<>();
    private final Set<Integer> seenIncidents = new HashSet<>();
    private int nextId = 1;
    private boolean primed;                  // incidents from before insurance existed are never claimed

    @Override public String key() { return "insurance"; }

    public Policy policy(int company) { return policies.computeIfAbsent(company, k -> new Policy()); }

    /** maintenance compliance from the depot's records (units of this company) */
    public void survey(World w, Company c, Policy p) {
        FleetData f = FleetData.get(w);
        int n = 0, over = 0, brake = 0;
        for (UUID u : c.trains) {
            FleetData.Unit x = f.peek(u);
            if (x == null) continue;
            n++;
            if (!Predict.due(w, f, x).isEmpty()) over++;
            if (!x.brakeOk) brake++;
        }
        p.units = n; p.overdue = over; p.brakeFails = brake;
        p.compliance = n == 0 ? 100 : Math.max(0, 100 - over * 100.0 / n - brake * 150.0 / n);
    }

    public long quote(World w, BizData d, Company c, Cover cover, int excess) {
        if (cover == Cover.NONE) return 0;
        Policy p = policy(c.id);
        survey(w, c, p);
        int cars = 0;
        for (UUID t : c.trains) cars += com.dogpound.railmap.economy.PassengerData.get(w).cars(t);
        double base = 150 + c.trains.size() * 120 + cars * 25 + c.stations.size() * 40;
        if (cover == Cover.COMPREHENSIVE) base *= 2.2;
        double risk = 1 + (100 - p.compliance) / 100 * 1.5;
        int recent = 0;
        for (Claim k : claims) if (k.company == c.id && w.getTotalWorldTime() - k.filed < 28 * BizData.DAY && !k.cargo) recent++;
        risk *= 1 + recent * 0.15;
        int score = d.regulator.lastScore(c.id);
        if (score >= 0) risk *= 1.2 - score / 250.0;
        if (Research.has(w, c, Research.Tech.SAFETY_MGMT) && BizConfig.researchTree) risk *= 0.8;
        risk *= new double[]{1.25, 1.0, 0.85, 0.7}[Math.max(0, Math.min(3, excess))];
        return Math.max(50, Math.round(base * risk));
    }

    public String take(World w, BizData d, Company c, Cover cover, int excess) {
        Policy p = policy(c.id);
        p.cover = cover; p.excess = Math.max(0, Math.min(3, excess));
        p.premiumDay = quote(w, d, c, cover, p.excess);
        d.note(w, c.mark + (cover == Cover.NONE ? " cancelled its insurance" : " insured: " + cover.name().replace('_', ' ').toLowerCase(Locale.ROOT) + ", excess " + Coins.fmt(EXCESS[p.excess]) + ", premium " + Coins.fmt(p.premiumDay) + "/day"));
        return cover == Cover.NONE ? "§eInsurance cancelled - " + c.name + " now carries its own risks" : "§aPolicy: " + cover.name().replace('_', ' ') + ", excess " + Coins.fmt(EXCESS[p.excess]) + " - premium " + Coins.fmt(p.premiumDay) + " a day";
    }

    /** the evidence for an incident claim, 0-100, with the adjuster's notes */
    public int evidence(World w, Company c, IncidentLog.Rec r, StringBuilder notes, boolean[] negligent) {
        FleetData f = FleetData.get(w);
        int score = 30, units = 0;
        for (String s : r.stock) {
            UUID u;
            try { u = UUID.fromString(s); } catch (IllegalArgumentException e) { continue; }
            FleetData.Unit x = f.peek(u);
            if (x == null) { notes.append("no maintenance record for ").append(s, 0, 8).append("; "); continue; }
            units++;
            String due = Predict.due(w, f, x);
            if (due.isEmpty()) score += 10; else notes.append(x.name).append(" OVERDUE (").append(due).append("); ");
            if (x.brakeOk && x.brakeCert > 0) score += 5; else if (!x.brakeOk) notes.append(x.name).append(" failed its last brake test; ");
            if (x.faults - x.examFaults > 0) { score -= 5; notes.append(x.name).append(" had open faults; "); }
            if (!due.isEmpty() && !x.brakeOk) negligent[0] = true;
        }
        if (!r.blackbox.isEmpty()) { score += 20; notes.append("recorder downloaded; "); } else notes.append("no recorder download; ");
        if (!r.report.isEmpty()) { score += 15; notes.append("final report filed; "); } else notes.append("no final report; ");
        int docs = Math.min(4, policy(c.id).evidenceDocs);
        score += docs * 5;
        if (docs > 0) notes.append(docs).append(" documents in the evidence file; ");
        if (units == 0) score -= 10;
        return Math.max(0, Math.min(100, score));
    }

    /** an incident closed: a claim for every insured company whose vehicles were in it */
    void incidentClaims(World w, BizData d) {
        CompanyData cd = CompanyData.get(w);
        if (!primed) { for (IncidentLog.Rec r : IncidentLog.get(w).all()) if (!r.open()) seenIncidents.add(r.id); primed = true; return; }
        for (IncidentLog.Rec r : IncidentLog.get(w).all()) {
            if (r.open() || seenIncidents.contains(r.id)) continue;
            seenIncidents.add(r.id);
            Set<Company> involved = new LinkedHashSet<>();
            for (String s : r.stock) try { Company c = cd.ownerOfTrain(UUID.fromString(s), null); if (c != null) involved.add(c); } catch (IllegalArgumentException ignored) { }
            for (Company c : involved) {
                if (policy(c.id).cover == Cover.NONE) continue;              // uninsured: nothing is claimed (and nothing charged)
                Claim k = new Claim();
                k.id = nextId++; k.company = c.id; k.incident = r.id; k.filed = w.getTotalWorldTime();
                k.what = r.type.label + " #" + r.id + " (level " + r.level + ")";
                k.value = r.level * 50_000L + r.stock.size() * 30_000L;
                k.liability = r.paxAffected * 500L;
                claims.add(k);
                d.note(w, "Claim C" + k.id + " filed for " + c.mark + ": " + k.what + " - damage " + Coins.fmt(k.value) + ", liability " + Coins.fmt(k.liability));
            }
        }
        while (claims.size() > 150) claims.remove(0);
        if (seenIncidents.size() > 2000) seenIncidents.clear();
    }

    /** settle a claim: adjuster, payout, liability */
    public String settle(World w, BizData d, Claim k) {
        if (!k.state.equals("FILED")) return "§7Claim C" + k.id + " is already " + k.state;
        CompanyData cd = CompanyData.get(w);
        Company c = cd.byId(k.company);
        if (c == null) { k.state = "DENIED"; k.notes = "company dissolved"; return "§7Company gone"; }
        Policy p = policy(c.id);
        IncidentLog.Rec r = null;
        for (IncidentLog.Rec x : IncidentLog.get(w).all()) if (x.id == k.incident) r = x;
        StringBuilder notes = new StringBuilder();
        boolean[] neg = {false};
        k.evidence = r == null ? 40 : evidence(w, c, r, notes, neg);
        // third-party liability: passengers are always compensated
        if (k.liability > 0) {
            boolean covered = p.cover != Cover.NONE;
            if (!covered) BizData.pay(w, c, Company.Kind.LIABILITY, k.liability, "Passenger compensation, " + k.what);
            notes.append(covered ? "liability " + Coins.fmt(k.liability) + " paid by the insurer; " : "uninsured - " + c.mark + " paid liability " + Coins.fmt(k.liability) + "; ");
        }
        long pay = 0;
        if (p.cover == Cover.COMPREHENSIVE) {
            if (neg[0]) { k.state = "DENIED"; notes.append("NEGLIGENCE: a vehicle overdue for its exam with a failed brake test - own damage denied"); }
            else {
                double f = k.evidence >= 80 ? 1.0 : k.evidence >= 50 ? 0.7 : 0.4;
                pay = Math.max(0, Math.round(k.value * f) - EXCESS[p.excess]);
                k.state = f >= 1 ? "PAID" : "PART";
                notes.append("evidence ").append(k.evidence).append("/100 -> ").append(Math.round(f * 100)).append("% less excess ").append(Coins.fmt(EXCESS[p.excess]));
            }
        } else { k.state = p.cover == Cover.NONE ? "DENIED" : "PART"; notes.append(p.cover == Cover.NONE ? "no policy" : "third-party cover only - own damage not covered"); }
        if (pay > 0) { cd.credit(w, c, Company.Kind.CLAIMS, pay, "Insurance claim C" + k.id + ": " + k.what); p.claimsPaid += pay; }
        k.paid = pay;
        k.notes = notes.toString();
        Recorder.event(w, "Insurance claim C" + k.id + " " + k.state + " for " + c.mark + ": " + Coins.fmt(pay) + " (evidence " + k.evidence + ")");
        return "§bClaim C" + k.id + " " + k.state + " - " + Coins.fmt(pay) + " · " + k.notes;
    }

    /** a cargo investigation closed: the liable party's insurer pays the box owner */
    void cargoClaim(World w, BizData d, Custody.Case cs) {
        CompanyData cd = CompanyData.get(w);
        Custody.Rec r = d.custody.recs.get(cs.rec);
        Company owner = null, liable = null;
        for (Company c : cd.all()) {
            for (Company.Member m : c.members.values()) { if (r != null && m.name.equals(r.owner)) owner = c; if (m.name.equals(cs.liable)) liable = c; }
            if (r != null && c.name.equals(r.owner)) owner = c;
        }
        if (owner == null || cs.value <= 0) return;
        Claim k = new Claim();
        k.id = nextId++; k.company = owner.id; k.cargo = true; k.filed = w.getTotalWorldTime(); k.what = "Cargo " + cs.rec + ": " + cs.findings; k.value = cs.value;
        claims.add(k);
        long pay = cs.value / 2;
        if (liable != null && liable != owner) {
            Policy lp = policy(liable.id);
            if (lp.cover == Cover.NONE) { if (!BizData.pay(w, liable, Company.Kind.LIABILITY, pay, "Cargo damages to " + owner.mark + ", " + cs.rec)) pay = 0; k.notes = liable.mark + " (uninsured) liable"; }
            else k.notes = liable.mark + "'s insurer liable";
        } else k.notes = liable == owner ? "own fault - no third party to claim from" : "liable party not a company: " + cs.liable;
        if (liable == owner || liable == null) pay = policy(owner.id).cover == Cover.COMPREHENSIVE ? pay / 2 : 0;
        k.paid = pay; k.state = pay > 0 ? "PAID" : "DENIED";
        if (pay > 0) cd.credit(w, owner, Company.Kind.CLAIMS, pay, "Cargo claim C" + k.id + " " + cs.rec);
    }

    @Override
    public void daily(World w, BizData d, long day) {
        incidentClaims(w, d);
        for (Claim k : claims) if (k.state.equals("FILED") && w.getTotalWorldTime() - k.filed >= BizData.DAY) settle(w, d, k);
        if (!BizConfig.insurance) return;
        CompanyData cd = CompanyData.get(w);
        for (Map.Entry<Integer, Policy> e : policies.entrySet()) {
            Company c = cd.byId(e.getKey());
            Policy p = e.getValue();
            if (c == null || p.cover == Cover.NONE) continue;
            p.premiumDay = quote(w, d, c, p.cover, p.excess);
            if (BizData.pay(w, c, Company.Kind.PREMIUM, p.premiumDay, "Insurance premium (" + p.cover.name().replace('_', ' ').toLowerCase(Locale.ROOT) + ")")) p.paidTotal += p.premiumDay;
            else { p.cover = Cover.NONE; d.note(w, c.mark + "'s insurance LAPSED - premium unpaid"); }
        }
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        policies.clear(); claims.clear(); seenIncidents.clear();
        NBTTagCompound ps = t.getCompoundTag("pol");
        for (String k : ps.getKeySet()) {
            NBTTagCompound c = ps.getCompoundTag(k);
            Policy p = new Policy();
            try { p.cover = Cover.valueOf(c.getString("cv")); } catch (IllegalArgumentException e) { p.cover = Cover.NONE; }
            p.excess = c.getInteger("ex"); p.premiumDay = c.getLong("pd"); p.paidTotal = c.getLong("pt"); p.claimsPaid = c.getLong("cp");
            p.compliance = c.getDouble("co"); p.overdue = c.getInteger("ov"); p.brakeFails = c.getInteger("bf"); p.units = c.getInteger("u"); p.evidenceDocs = c.getInteger("doc");
            try { policies.put(Integer.parseInt(k), p); } catch (NumberFormatException ignored) { }
        }
        NBTTagList l = t.getTagList("cl", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Claim k = new Claim();
            k.id = c.getInteger("id"); k.company = c.getInteger("c"); k.incident = c.getInteger("inc"); k.what = c.getString("w"); k.state = c.getString("s"); k.notes = c.getString("n");
            k.filed = c.getLong("f"); k.value = c.getLong("v"); k.liability = c.getLong("l"); k.paid = c.getLong("p"); k.evidence = c.getInteger("e"); k.cargo = c.getBoolean("cg");
            claims.add(k);
        }
        for (int i : t.getIntArray("seen")) seenIncidents.add(i);
        nextId = Math.max(1, t.getInteger("next"));
        primed = t.getBoolean("primed");
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound ps = new NBTTagCompound();
        for (Map.Entry<Integer, Policy> e : policies.entrySet()) {
            Policy p = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setString("cv", p.cover.name()); c.setInteger("ex", p.excess); c.setLong("pd", p.premiumDay); c.setLong("pt", p.paidTotal); c.setLong("cp", p.claimsPaid);
            c.setDouble("co", p.compliance); c.setInteger("ov", p.overdue); c.setInteger("bf", p.brakeFails); c.setInteger("u", p.units); c.setInteger("doc", p.evidenceDocs);
            ps.setTag(String.valueOf(e.getKey()), c);
        }
        t.setTag("pol", ps);
        NBTTagList l = new NBTTagList();
        for (Claim k : claims) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", k.id); c.setInteger("c", k.company); c.setInteger("inc", k.incident); c.setString("w", k.what); c.setString("s", k.state); c.setString("n", k.notes);
            c.setLong("f", k.filed); c.setLong("v", k.value); c.setLong("l", k.liability); c.setLong("p", k.paid); c.setInteger("e", k.evidence); c.setBoolean("cg", k.cargo);
            l.appendTag(c);
        }
        t.setTag("cl", l);
        int[] seen = new int[seenIncidents.size()];
        int i = 0;
        for (int s : seenIncidents) seen[i++] = s;
        t.setIntArray("seen", seen);
        t.setInteger("next", nextId);
        t.setBoolean("primed", primed);
        return t;
    }
}
