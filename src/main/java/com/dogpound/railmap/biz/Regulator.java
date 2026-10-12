package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.incident.IncidentLog;
import com.dogpound.railmap.station.StationFacilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.*;

/**
 * The railway regulator (her "next 48" #34). Server owners can switch it off (BizConfig.regulator). Every
 * auditEveryDays it AUDITS each operating company against nine checks taken from the railway's own records -
 * maintenance compliance, brake tests, punctuality, overcrowding, step-free access at its stations, the maintenance
 * budget, insurance for passenger operators, open cargo investigations it is liable for, and incidents left without a
 * final report. Every failed check is a CORRECTIVE ACTION with a deadline (3 days). The regulator re-checks daily and
 * closes it once the check passes; the company may ACCEPT it (logged) and APPEAL once (+2 days). Past its deadline
 * it costs reputation, and - only with regulatorFines on - a daily fine.
 */
public final class Regulator implements BizData.Part {
    public static final String[] CHECKS = {"MAINTENANCE", "BRAKE TESTS", "PUNCTUALITY", "OVERCROWDING", "STEP-FREE", "MAINT BUDGET", "INSURANCE", "CARGO CASES", "INCIDENT REPORTS"};
    public static final String[] ACTIONS = {"bring every unit within its exam plan", "re-test and pass every brake test", "lift line punctuality to 80%",
            "add capacity: under 50 left behind a day", "restore lifts / declare level access at its stations", "fund the maintenance account",
            "take out at least third-party insurance", "settle the cargo investigations it is liable for", "file final reports on its incidents"};

    public static final class Audit {
        public int company, score;
        public long when;
        public final boolean[] pass = new boolean[CHECKS.length];
        public final String[] detail = new String[CHECKS.length];
    }

    public static final class Ca {
        public int id, company, check;
        public long raised, deadline;
        public String state = "OPEN";        // OPEN, ACCEPTED, CLOSED, OVERDUE
        public boolean appealed;
        public long fined;
    }

    public final Map<Integer, Audit> last = new LinkedHashMap<>();
    public final List<Ca> cas = new ArrayList<>();
    private int nextId = 1;

    @Override public String key() { return "regulator"; }

    public int lastScore(int company) { Audit a = last.get(company); return a == null ? -1 : a.score; }

    /** run the nine checks now */
    public Audit audit(World w, BizData d, Company c) {
        Audit a = new Audit();
        a.company = c.id; a.when = w.getTotalWorldTime();
        Insurance.Policy pol = d.insurance.policy(c.id);
        d.insurance.survey(w, c, pol);
        PassengerData pd = PassengerData.get(w);
        set(a, 0, pol.compliance >= 90, String.format(Locale.ROOT, "%.0f%% compliant, %d of %d units overdue", pol.compliance, pol.overdue, pol.units));
        set(a, 1, pol.brakeFails == 0, pol.brakeFails + " failed brake tests outstanding");
        double pu = 0, left = 0; int ln = 0; boolean riders = false;
        for (String l : c.lines) { PassengerData.LineStat s = pd.line(l); if (s != null) { pu += s.punct; left += s.leftDay; ln++; riders |= s.ridersYesterday > 0 || s.ridersDay > 0; } }
        set(a, 2, ln == 0 || pu / ln >= 80, ln == 0 ? "no lines" : String.format(Locale.ROOT, "%.0f%% punctual", pu / ln));
        set(a, 3, left < 50, Math.round(left) + " passengers left behind today");
        int bad = 0;
        for (long st : c.stations) { StationFacilities.Audit fa = StationFacilities.peek(w, st); if (fa != null && fa.audited && !fa.stepFree) bad++; }
        set(a, 4, bad == 0, bad + " stations without step-free access");
        set(a, 5, c.maintenance > 0 || c.trains.isEmpty() && c.stations.isEmpty(), "maintenance account " + Coins.fmt(c.maintenance));
        set(a, 6, !riders || pol.cover != Insurance.Cover.NONE, riders ? "carries passengers, cover: " + pol.cover.name().replace('_', ' ') : "no passengers");
        int cases = 0;
        for (Custody.Case k : d.custody.cases) if (k.state.equals("OPEN")) for (Company.Member m : c.members.values()) if (m.name.equals(k.liable)) cases++;
        set(a, 7, cases == 0, cases + " open cargo investigations");
        int noReport = 0;
        CompanyData cd = CompanyData.get(w);
        for (IncidentLog.Rec r : IncidentLog.get(w).all()) {
            if (r.report.isEmpty() && r.opened > 0 && w.getTotalWorldTime() - r.opened > BizData.DAY)
                for (String s : r.stock) try { if (cd.ownerOfTrain(UUID.fromString(s), null) == c) { noReport++; break; } } catch (IllegalArgumentException ignored) { }
        }
        set(a, 8, noReport == 0, noReport + " incidents without a final report");
        int passed = 0;
        for (boolean b : a.pass) if (b) passed++;
        a.score = passed * 100 / CHECKS.length;
        if (Research.has(w, c, Research.Tech.SAFETY_MGMT) && BizConfig.researchTree) a.score = Math.min(100, a.score + 10);
        last.put(c.id, a);
        for (int i = 0; i < CHECKS.length; i++) {
            if (a.pass[i]) continue;
            boolean open = false;
            for (Ca x : cas) if (x.company == c.id && x.check == i && !x.state.equals("CLOSED")) open = true;
            if (open) continue;
            Ca x = new Ca();
            x.id = nextId++; x.company = c.id; x.check = i; x.raised = a.when; x.deadline = a.when + 3 * BizData.DAY;
            cas.add(x);
        }
        while (cas.size() > 200) cas.remove(0);
        d.note(w, "Audit of " + c.mark + ": " + a.score + "/100" + (passed < CHECKS.length ? " - " + (CHECKS.length - passed) + " corrective actions" : " - clean"));
        Recorder.event(w, "Regulator audit " + c.name + ": " + a.score + "/100");
        d.mastery.trophy(w, c, a.score == 100 ? "CLEAN AUDIT" : null);
        return a;
    }

    private static void set(Audit a, int i, boolean ok, String d) { a.pass[i] = ok; a.detail[i] = d; }

    public Ca ca(int id) { for (Ca x : cas) if (x.id == id) return x; return null; }

    public String accept(World w, Company c, int id) {
        Ca x = ca(id);
        if (x == null || x.company != c.id) return "§7No corrective action CA" + id + " for " + c.mark;
        if (!x.state.equals("OPEN")) return "§7CA" + id + " is " + x.state;
        x.state = "ACCEPTED";
        return "§aCA" + id + " accepted: " + ACTIONS[x.check] + " by day " + (x.deadline / BizData.DAY);
    }

    public String appeal(World w, Company c, int id) {
        Ca x = ca(id);
        if (x == null || x.company != c.id) return "§7No corrective action CA" + id + " for " + c.mark;
        if (x.appealed) return "§cCA" + id + " has already been appealed once";
        if (x.state.equals("CLOSED")) return "§7CA" + id + " is closed";
        x.appealed = true; x.deadline += 2 * BizData.DAY;
        if (x.state.equals("OVERDUE")) x.state = "OPEN";
        return "§eAppeal granted: CA" + id + " deadline extended two days";
    }

    /** audits run in the day's last 5 s, while the day's punctuality and crowding are still on the books */
    @Override
    public void dayEnd(World w, BizData d, long day) {
        if (!BizConfig.regulator) return;
        CompanyData cd = CompanyData.get(w);
        for (Company c : cd.all()) {
            if (c.trains.isEmpty() && c.stations.isEmpty() && c.lines.isEmpty()) continue;
            Audit a = last.get(c.id);
            boolean due = a == null || w.getTotalWorldTime() - a.when >= BizConfig.auditEveryDays * BizData.DAY;
            Audit now = due ? audit(w, d, c) : null;
            boolean openCa = false;
            for (Ca x : cas) if (x.company == c.id && !x.state.equals("CLOSED")) openCa = true;
            if (!due && openCa) now = audit(w, d, c);                               // re-check open actions daily
            if (now == null) continue;
            for (Ca x : cas) {
                if (x.company != c.id || x.state.equals("CLOSED")) continue;
                if (now.pass[x.check]) { x.state = "CLOSED"; d.note(w, "CA" + x.id + " " + c.mark + " closed: " + CHECKS[x.check] + " now passes"); AlarmManager.get(w).clear("reg:" + x.id); continue; }
                if (w.getTotalWorldTime() > x.deadline) {
                    if (!x.state.equals("OVERDUE")) c.reputation = Math.max(0, c.reputation - 5);   // once, when it goes overdue
                    x.state = "OVERDUE";
                    AlarmManager.get(w).raise(w, "reg:" + x.id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "Regulator: " + c.name + " CA" + x.id + " OVERDUE - " + ACTIONS[x.check], null, true);
                    if (BizConfig.regulatorFines && BizData.pay(w, c, Company.Kind.FINES, 10_000, "Regulator fine, CA" + x.id + " overdue")) x.fined += 10_000;
                }
            }
        }
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        last.clear(); cas.clear();
        NBTTagCompound l = t.getCompoundTag("last");
        for (String k : l.getKeySet()) {
            NBTTagCompound c = l.getCompoundTag(k);
            Audit a = new Audit();
            a.company = c.getInteger("c"); a.score = c.getInteger("s"); a.when = c.getLong("w");
            byte[] p = c.getByteArray("p");
            NBTTagList dt = c.getTagList("d", 8);
            for (int i = 0; i < CHECKS.length; i++) { a.pass[i] = i < p.length && p[i] != 0; a.detail[i] = i < dt.tagCount() ? dt.getStringTagAt(i) : ""; }
            last.put(a.company, a);
        }
        NBTTagList cl = t.getTagList("ca", 10);
        for (int i = 0; i < cl.tagCount(); i++) {
            NBTTagCompound c = cl.getCompoundTagAt(i);
            Ca x = new Ca();
            x.id = c.getInteger("id"); x.company = c.getInteger("c"); x.check = Math.max(0, Math.min(CHECKS.length - 1, c.getInteger("k")));
            x.raised = c.getLong("r"); x.deadline = c.getLong("d"); x.state = c.getString("s"); x.appealed = c.getBoolean("a"); x.fined = c.getLong("f");
            cas.add(x);
        }
        nextId = Math.max(1, t.getInteger("next"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagCompound l = new NBTTagCompound();
        for (Audit a : last.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("c", a.company); c.setInteger("s", a.score); c.setLong("w", a.when);
            byte[] p = new byte[CHECKS.length];
            NBTTagList dt = new NBTTagList();
            for (int i = 0; i < CHECKS.length; i++) { p[i] = (byte) (a.pass[i] ? 1 : 0); dt.appendTag(new net.minecraft.nbt.NBTTagString(a.detail[i] == null ? "" : a.detail[i])); }
            c.setByteArray("p", p); c.setTag("d", dt);
            l.setTag(String.valueOf(a.company), c);
        }
        t.setTag("last", l);
        NBTTagList cl = new NBTTagList();
        for (Ca x : cas) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", x.id); c.setInteger("c", x.company); c.setInteger("k", x.check); c.setLong("r", x.raised); c.setLong("d", x.deadline);
            c.setString("s", x.state); c.setBoolean("a", x.appealed); c.setLong("f", x.fined);
            cl.appendTag(c);
        }
        t.setTag("ca", cl);
        t.setInteger("next", nextId);
        return t;
    }
}
