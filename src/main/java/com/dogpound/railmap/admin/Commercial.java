package com.dogpound.railmap.admin;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.ConsoleControl;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.server.WearData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * The commercial side of a shared railway (her ChatGPT list #112-114):
 * <ul>
 *   <li><b>Operating leases</b> - a company (or a player) lets a loco or wagon to another company: a daily rate, a
 *   term, a km allowance and who maintains it. On acceptance a loco moves into the lessee's fleet (it earns for
 *   them); each day the rent is paid (arrears for {@link AdminConfig#arrearsDays} days ends it in default); at the end
 *   it goes back, with excess-km and - when the lessee had the maintenance - dilapidation charges for wear it added.</li>
 *   <li><b>Track access agreements</b> - an infrastructure owner lets an operator run over its lines for a charge per
 *   train-km and per station call, measured from the operator's driverless trains, settled daily, and may grant the
 *   operator's staff signalling / depot / power rights on its territory. An operator's train on another company's
 *   line with no agreement raises UNAUTHORISED RUNNING (and is held when {@link AdminConfig#holdUnauthorised}).</li>
 *   <li><b>Service-level agreements</b> - punctuality, availability and departures-per-day targets on a line, judged
 *   each day from the incident analytics (#77): the operator pays per point of punctuality / per departure short,
 *   the infrastructure owner pays per point of availability lost to blockages.</li>
 * </ul>
 * Money is cents, booked in the companies' ledgers (Lease / Track access / SLA lines on the Company HQ).
 */
public final class Commercial {
    private Commercial() {}

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Boolean> DWELL = new HashMap<>();
    private static final Set<UUID> HELD_UNAUTH = new HashSet<>();

    // ---- helpers ------------------------------------------------------------------------------------------------------
    static boolean finance(Company c, UUID u) { return c != null && c.can(u, r -> r.finance); }

    static void tell(World w, Company c, String msg) {
        if (c == null) return;
        for (Map.Entry<UUID, Company.Member> e : c.members.entrySet()) {
            if (!e.getValue().role.finance && !e.getValue().role.operations) continue;
            EntityPlayerMP p = CompanyData.online(w, e.getKey());
            if (p != null) p.sendMessage(new TextComponentString(msg));
        }
    }

    static long day(World w) { return w.getTotalWorldTime() / CompanyData.DAY; }

    /** the rolling stock a player stands at (riding it, or the nearest within 8 blocks) */
    static EntityRollingStock stockAt(EntityPlayerMP p) {
        EntityRollingStock best = null;
        double bd = 64;
        for (EntityRollingStock s : cam72cam.mod.world.World.get(p.world).getEntities(EntityRollingStock.class)) {
            double d = s.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(p.posX, p.posY, p.posZ));
            if (d < bd && !s.isDead()) { bd = d; best = s; }
        }
        return best;
    }

    public static AdminData.Lease activeLease(World w, UUID stock) {
        for (AdminData.Lease l : AdminData.get(w).leases) if (l.state == AdminData.State.ACTIVE && l.stock.equals(stock)) return l;
        return null;
    }

    // ---- #112 leases ------------------------------------------------------------------------------------------------
    public static String leaseOffer(EntityPlayerMP p, String lessee, long rateCents, int days, int maxKm, boolean maintLessee) {
        World w = p.world;
        CompanyData cd = CompanyData.get(w);
        Company to = cd.byName(lessee);
        if (to == null) return "§cNo company called " + lessee;
        EntityRollingStock s = stockAt(p);
        if (s == null) return "§cStand at (or ride) the loco or wagon you are letting";
        if (activeLease(w, s.getUUID()) != null) return "§cThat vehicle is already on lease";
        AdminData ad = AdminData.get(w);
        AdminData.Lease l = new AdminData.Lease();
        Company from = null;
        for (Company c : cd.all()) if (c.trains.contains(s.getUUID())) from = c;
        if (from != null) {
            if (!finance(from, p.getUniqueID()) && !Access.admin(p)) return "§cOnly " + from.name + "'s owner or a director can let its stock";
            if (from == to) return "§cA company can't lease to itself";
            l.lessorCo = from.id; l.lessorName = from.name;
        } else { l.lessorPlayer = p.getUniqueID(); l.lessorName = p.getName(); }
        l.id = ad.nextId++; l.lesseeCo = to.id; l.stock = s.getUUID(); l.loco = s instanceof Locomotive;
        l.stockName = s.getDefinition() == null ? "vehicle" : s.getDefinition().name();
        l.rate = Math.max(0, rateCents); l.days = Math.max(1, days); l.maxKm = Math.max(0, maxKm); l.maintLessee = maintLessee;
        l.offered = w.getTotalWorldTime();
        ad.leases.add(l);
        ad.markDirty();
        tell(w, to, "§e📄 Lease offer L" + l.id + " from " + l.lessorName + ": " + l.stockName + " for " + l.days + " days at " + Coins.fmt(l.rate) + "/day"
                + (l.maxKm > 0 ? ", " + l.maxKm + " km allowed" : "") + ", maintenance by " + (maintLessee ? "you" : "the lessor") + " - /irextras admin lease accept " + l.id);
        Recorder.event(w, "LEASE: L" + l.id + " offered, " + l.stockName + " " + l.lessorName + " > " + to.name);
        return "§aLease L" + l.id + " offered to " + to.name;
    }

    public static String leaseAccept(EntityPlayerMP p, int id, boolean yes) {
        World w = p.world;
        AdminData ad = AdminData.get(w);
        AdminData.Lease l = ad.lease(id);
        if (l == null || l.state != AdminData.State.OFFERED) return "§cNo open lease offer L" + id;
        CompanyData cd = CompanyData.get(w);
        Company to = cd.byId(l.lesseeCo);
        if (!finance(to, p.getUniqueID()) && !Access.admin(p)) return "§cOnly the lessee's owner or a director can sign";
        if (!yes) { l.state = AdminData.State.REFUSED; ad.markDirty(); return "§eLease L" + id + " refused"; }
        if (activeLease(w, l.stock) != null) return "§cThat vehicle has gone on another lease";
        l.state = AdminData.State.ACTIVE;
        l.startDay = day(w);
        WearData.Record r = WearData.get(w).peek(l.stock);
        l.kmStart = r == null ? 0 : r.km;
        l.wearStart = r == null ? 0 : r.worst();
        if (l.loco) {
            Company from = cd.byId(l.lessorCo);
            if (from != null) from.trains.remove(l.stock);
            to.trains.add(l.stock);
            cd.markDirty();
        }
        ad.markDirty();
        tell(w, cd.byId(l.lessorCo), "§a📄 " + to.name + " signed lease L" + id + " for " + l.stockName);
        Recorder.event(w, "LEASE: L" + id + " signed, " + l.stockName + " to " + to.name + " for " + l.days + " days");
        return "§aLease L" + id + " signed - " + l.stockName + " is yours to run for " + l.days + " days";
    }

    /** end a lease: back to the lessor, with return charges. early = ended before its term (2 days' rent fee when the lessee ends it) */
    public static String leaseEnd(World w, AdminData.Lease l, String why, boolean earlyByLessee) {
        CompanyData cd = CompanyData.get(w);
        Company to = cd.byId(l.lesseeCo), from = cd.byId(l.lessorCo);
        long charge = earlyByLessee ? l.rate * 2 : 0;
        WearData.Record r = WearData.get(w).peek(l.stock);
        double km = r == null ? 0 : r.km - l.kmStart;
        List<String> notes = new ArrayList<>();
        if (earlyByLessee) notes.add("early return fee " + Coins.fmt(l.rate * 2));
        if (l.maxKm > 0 && km > l.maxKm) { long x = Math.round((km - l.maxKm) * Math.max(1, l.rate / 100)); charge += x; notes.add(String.format(Locale.ROOT, "%.0f km over allowance %s", km - l.maxKm, Coins.fmt(x))); }
        if (l.maintLessee && r != null && r.worst() - l.wearStart > 10) { long x = Math.round((r.worst() - l.wearStart - 10) * Math.max(1, l.rate / 20)); charge += x; notes.add(String.format(Locale.ROOT, "wear +%.0f%% dilapidations %s", r.worst() - l.wearStart, Coins.fmt(x))); }
        if (charge > 0 && to != null) {
            long can = Math.min(charge, to.operating);
            if (can > 0 && cd.debit(w, to, Company.Kind.LEASE_OUT, can, "Lease L" + l.id + " return charges", false)) payLessor(w, l, from, can, "Lease L" + l.id + " return charges");
            if (can < charge) { to.reputation = Math.max(0, to.reputation - 5); notes.add("unpaid " + Coins.fmt(charge - can)); }
        }
        if (l.loco && to != null) {
            to.trains.remove(l.stock);
            if (from != null) from.trains.add(l.stock);
            cd.markDirty();
        }
        l.state = why.startsWith("default") ? AdminData.State.DEFAULTED : AdminData.State.ENDED;
        AdminData.get(w).markDirty();
        String msg = "Lease L" + l.id + " (" + l.stockName + ") ended: " + why + (notes.isEmpty() ? "" : " - " + String.join(", ", notes));
        tell(w, to, "§e📄 " + msg);
        tell(w, from, "§e📄 " + msg);
        Recorder.event(w, "LEASE: " + msg);
        return "§e" + msg;
    }

    private static void payLessor(World w, AdminData.Lease l, Company from, long cents, String memo) {
        if (from != null) CompanyData.get(w).credit(w, from, Company.Kind.LEASE_IN, cents, memo);
        else { l.owedPlayer += cents; AdminData.get(w).markDirty(); }
    }

    // ---- #113 track access ----------------------------------------------------------------------------------------
    public static String agreeOffer(EntityPlayerMP p, String operator, long perKm, long perCall, int caps, Collection<String> lines) {
        World w = p.world;
        CompanyData cd = CompanyData.get(w);
        Company own = cd.of(p.getUniqueID()), op = cd.byName(operator);
        if (own == null || !finance(own, p.getUniqueID())) return "§cOnly an infrastructure owner's owner or director can offer track access";
        if (op == null || op == own) return "§cNo other company called " + operator;
        for (String ln : lines) if (!own.lines.contains(ln)) return "§c" + own.name + " doesn't own line " + ln;
        AdminData ad = AdminData.get(w);
        AdminData.Agreement a = new AdminData.Agreement();
        a.id = ad.nextId++; a.ownerCo = own.id; a.operatorCo = op.id; a.perKm = perKm; a.perCall = perCall; a.caps = caps & ~Access.Cap.ADMIN.bit();
        a.lines.addAll(lines);
        ad.agreements.add(a);
        ad.markDirty();
        tell(w, op, "§e📄 Track access offer A" + a.id + " from " + own.name + ": " + (a.lines.isEmpty() ? "all its lines" : String.join(", ", a.lines)) + ", "
                + Coins.fmt(perKm) + "/train-km + " + Coins.fmt(perCall) + "/call, staff rights " + Access.caps(a.caps) + " - /irextras admin agree accept " + a.id);
        return "§aTrack access agreement A" + a.id + " offered to " + op.name;
    }

    public static String agreeAccept(EntityPlayerMP p, int id, boolean yes) {
        World w = p.world;
        AdminData ad = AdminData.get(w);
        AdminData.Agreement a = ad.agreement(id);
        if (a == null || a.state != AdminData.State.OFFERED) return "§cNo open agreement A" + id;
        Company op = CompanyData.get(w).byId(a.operatorCo);
        if (!finance(op, p.getUniqueID()) && !Access.admin(p)) return "§cOnly the operator's owner or a director can sign";
        a.state = yes ? AdminData.State.ACTIVE : AdminData.State.REFUSED;
        ad.markDirty();
        tell(w, CompanyData.get(w).byId(a.ownerCo), "§e📄 " + op.name + (yes ? " signed" : " refused") + " track access agreement A" + id);
        Recorder.event(w, "ACCESS: A" + id + (yes ? " signed" : " refused") + " by " + op.name);
        return yes ? "§aAgreement A" + id + " is in force" : "§eAgreement A" + id + " refused";
    }

    static AdminData.Agreement agreementFor(AdminData ad, Company owner, Company op, String line) {
        for (AdminData.Agreement a : ad.agreements)
            if (a.state == AdminData.State.ACTIVE && a.ownerCo == owner.id && a.operatorCo == op.id && a.covers(line)) return a;
        return null;
    }

    /** every 5 s: driverless trains on other companies' lines - train-km and calls, unauthorised running, lease km */
    static void sample(World w) {
        AdminData ad = AdminData.get(w);
        CompanyData cd = CompanyData.get(w);
        int dim = w.provider.getDimension();
        for (RailwayData.AutoTrain at : RailwayData.get(w).trains().values()) {
            Autopilot.Run r = Autopilot.run(at.loco);
            if (r == null || r.entityId < 0) continue;
            net.minecraft.entity.Entity e = w.getEntityByID(r.entityId);
            if (e == null) continue;
            double[] last = LAST_POS.put(at.loco, new double[]{e.posX, e.posZ});
            double km = last == null ? 0 : Math.min(2.0, Math.hypot(e.posX - last[0], e.posZ - last[1]) / 1000.0);
            Boolean was = DWELL.put(at.loco, r.dwelling);
            boolean call = r.dwelling && !Boolean.TRUE.equals(was);
            Company op = null;
            for (Company c : cd.all()) if (c.trains.contains(at.loco)) op = c;
            Company owner = cd.ownerOfLine(at.line);
            String key = "admin:unauth:" + at.loco;
            if (op == null || owner == null || op == owner) { if (HELD_UNAUTH.remove(at.loco)) ConsoleControl.hold(dim, at.loco, false); continue; }
            AdminData.Agreement a = agreementFor(ad, owner, op, at.line);
            if (a != null) {
                a.km += km;
                if (call) a.calls++;
                ad.markDirty();
                AlarmManager.get(w).clear(key);
                if (HELD_UNAUTH.remove(at.loco)) ConsoleControl.hold(dim, at.loco, false);
            } else if (km > 0 || call) {
                AlarmManager.get(w).raise(w, key, AlarmManager.Cat.AUTO, AlarmManager.Sev.MAJOR,
                        "UNAUTHORISED RUNNING: " + op.name + "'s " + (at.label.isEmpty() ? "train" : at.label) + " on " + owner.name + "'s line " + at.line + " - no access agreement",
                        new BlockPos(e.posX, e.posY, e.posZ), true);
                if (AdminConfig.holdUnauthorised && r.dwelling && HELD_UNAUTH.add(at.loco)) ConsoleControl.hold(dim, at.loco, true);
            }
        }
        if (LAST_POS.size() > 2000) { LAST_POS.clear(); DWELL.clear(); }
        // lessors who are players: pay what they are owed once they are online
        for (AdminData.Lease l : ad.leases) {
            if (l.owedPlayer <= 0 || l.lessorPlayer == null) continue;
            EntityPlayerMP p = CompanyData.online(w, l.lessorPlayer);
            if (p != null && Coins.pay(p, l.owedPlayer, "Lease L" + l.id + " rent")) {
                p.sendMessage(new TextComponentString("§a📄 Lease L" + l.id + " rent paid to you: §6" + Coins.fmt(l.owedPlayer)));
                l.owedPlayer = 0;
                ad.markDirty();
            }
        }
    }

    // ---- daily settlement --------------------------------------------------------------------------------------------
    static void daily(World w) {
        AdminData ad = AdminData.get(w);
        long d = day(w);
        if (ad.settledDay < 0) { ad.settledDay = d; ad.markDirty(); return; }
        if (d == ad.settledDay) return;
        ad.settledDay = d;
        ad.markDirty();
        CompanyData cd = CompanyData.get(w);
        for (AdminData.Lease l : new ArrayList<>(ad.leases)) {
            if (l.state != AdminData.State.ACTIVE) continue;
            Company to = cd.byId(l.lesseeCo);
            if (to == null) { leaseEnd(w, l, "lessee company dissolved", false); continue; }
            if (l.rate > 0 && cd.debit(w, to, Company.Kind.LEASE_OUT, l.rate, "Lease L" + l.id + " rent: " + l.stockName, false)) {
                payLessor(w, l, cd.byId(l.lessorCo), l.rate, "Lease L" + l.id + " rent: " + l.stockName);
                l.paid += l.rate; l.arrears = 0;
                AlarmManager.get(w).clear("admin:lease:" + l.id);
            } else if (l.rate > 0) {
                l.arrears++;
                AlarmManager.get(w).raise(w, "admin:lease:" + l.id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, to.name + " is " + l.arrears + " day(s) behind on lease L" + l.id, null, true);
                if (l.arrears > AdminConfig.arrearsDays) { to.reputation = Math.max(0, to.reputation - 10); leaseEnd(w, l, "default - rent unpaid " + l.arrears + " days", false); continue; }
            }
            if (++l.daysRun >= l.days) leaseEnd(w, l, "term complete", false);
        }
        for (AdminData.Agreement a : ad.agreements) {
            if (a.state != AdminData.State.ACTIVE && a.state != AdminData.State.SUSPENDED) continue;
            Company own = cd.byId(a.ownerCo), op = cd.byId(a.operatorCo);
            if (own == null || op == null) { a.state = AdminData.State.ENDED; continue; }
            a.owed += Math.round(a.km * a.perKm) + (long) a.calls * a.perCall;
            a.km = 0; a.calls = 0;
            if (a.owed <= 0) continue;
            String memo = String.format(Locale.ROOT, "Track access A%d (%s)", a.id, own.name);
            if (cd.debit(w, op, Company.Kind.ACCESS_OUT, a.owed, memo, false)) {
                cd.credit(w, own, Company.Kind.ACCESS_IN, a.owed, "Track access A" + a.id + " (" + op.name + ")");
                a.paid += a.owed; a.owed = 0; a.arrears = 0;
                if (a.state == AdminData.State.SUSPENDED) { a.state = AdminData.State.ACTIVE; tell(w, op, "§aTrack access A" + a.id + " restored - arrears cleared"); }
                AlarmManager.get(w).clear("admin:access:" + a.id);
            } else if (++a.arrears > AdminConfig.arrearsDays && a.state == AdminData.State.ACTIVE) {
                a.state = AdminData.State.SUSPENDED;
                tell(w, op, "§c⛔ Track access A" + a.id + " SUSPENDED by " + own.name + ": " + Coins.fmt(a.owed) + " unpaid");
                Recorder.event(w, "ACCESS: A" + a.id + " suspended for arrears");
            } else AlarmManager.get(w).raise(w, "admin:access:" + a.id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, op.name + " owes " + Coins.fmt(a.owed) + " track access (A" + a.id + ")", null, true);
        }
        slas(w, ad, cd);
    }

    // ---- #114 SLAs -------------------------------------------------------------------------------------------------
    public static String slaOffer(EntityPlayerMP p, String operator, String line, int punct, int avail, int deps, long penaltyCents) {
        World w = p.world;
        CompanyData cd = CompanyData.get(w);
        Company own = cd.of(p.getUniqueID()), op = cd.byName(operator);
        if (own == null || !finance(own, p.getUniqueID())) return "§cOnly an infrastructure owner's owner or director can offer an SLA";
        if (op == null || op == own) return "§cNo other company called " + operator;
        if (!own.lines.contains(line)) return "§c" + own.name + " doesn't own line " + line;
        AdminData ad = AdminData.get(w);
        AdminData.Sla s = new AdminData.Sla();
        s.id = ad.nextId++; s.ownerCo = own.id; s.operatorCo = op.id; s.line = line;
        s.punctPct = Math.max(0, Math.min(100, punct)); s.availPct = Math.max(0, Math.min(100, avail)); s.depsPerDay = Math.max(0, deps); s.penalty = Math.max(0, penaltyCents);
        ad.slas.add(s);
        ad.markDirty();
        tell(w, op, "§e📄 SLA offer S" + s.id + " on " + line + " from " + own.name + ": punctuality " + s.punctPct + "%, availability " + s.availPct + "%, "
                + s.depsPerDay + " departures/day, " + Coins.fmt(s.penalty) + " per point - /irextras admin sla accept " + s.id);
        return "§aSLA S" + s.id + " offered to " + op.name;
    }

    public static String slaAccept(EntityPlayerMP p, int id, boolean yes) {
        World w = p.world;
        AdminData ad = AdminData.get(w);
        AdminData.Sla s = ad.sla(id);
        if (s == null || s.state != AdminData.State.OFFERED) return "§cNo open SLA S" + id;
        Company op = CompanyData.get(w).byId(s.operatorCo);
        if (!finance(op, p.getUniqueID()) && !Access.admin(p)) return "§cOnly the operator's owner or a director can sign";
        s.state = yes ? AdminData.State.ACTIVE : AdminData.State.REFUSED;
        s.lastDay = Analytics.day(w);
        ad.markDirty();
        return yes ? "§aSLA S" + id + " in force from tomorrow" : "§eSLA S" + id + " refused";
    }

    /** one day's measures for a line: [departures, on-time %, availability %] */
    public static double[] lineDay(Map<String, Double> m, String line) {
        double dep = Analytics.v(m, "dep:" + line), on = Analytics.v(m, "ontime:" + line), all = Analytics.v(m, "all:" + line), held = Analytics.v(m, "held:" + line);
        return new double[]{dep, dep == 0 ? 100 : on / dep * 100, all == 0 ? 100 : 100 * (1 - held / all)};
    }

    private static void slas(World w, AdminData ad, CompanyData cd) {
        int today = Analytics.day(w);
        TreeMap<Integer, Map<String, Double>> days = Analytics.get(w).days();
        for (AdminData.Sla s : ad.slas) {
            if (s.state != AdminData.State.ACTIVE) continue;
            Company own = cd.byId(s.ownerCo), op = cd.byId(s.operatorCo);
            if (own == null || op == null) { s.state = AdminData.State.ENDED; continue; }
            for (int d = Math.max(s.lastDay + 1, today - 14); d < today; d++) {
                Map<String, Double> m = days.get(d);
                s.lastDay = d;
                if (m == null) continue;
                double[] k = lineDay(m, s.line);
                double punctShort = k[0] == 0 ? 0 : Math.max(0, s.punctPct - k[1]), availShort = Math.max(0, s.availPct - k[2]);
                double depShort = Math.max(0, s.depsPerDay - k[0]);
                long opPays = Math.round((punctShort + depShort) * s.penalty), ownPays = Math.round(availShort * s.penalty);
                if (opPays > 0 && cd.debit(w, op, Company.Kind.SLA_OUT, opPays, "SLA S" + s.id + " " + s.line + " day " + d, false)) { cd.credit(w, own, Company.Kind.SLA_IN, opPays, "SLA S" + s.id + " from " + op.name); s.operatorPaid += opPays; }
                if (ownPays > 0 && cd.debit(w, own, Company.Kind.SLA_OUT, ownPays, "SLA S" + s.id + " " + s.line + " day " + d, false)) { cd.credit(w, op, Company.Kind.SLA_IN, ownPays, "SLA S" + s.id + " from " + own.name); s.ownerPaid += ownPays; }
                String row = String.format(Locale.ROOT, "D%d punct %.0f%%(%d) avail %.0f%%(%d) deps %.0f(%d) op pays %s, owner pays %s",
                        d, k[1], s.punctPct, k[2], s.availPct, k[0], s.depsPerDay, Coins.fmt(opPays), Coins.fmt(ownPays));
                s.history.add(0, row);
                while (s.history.size() > 14) s.history.remove(s.history.size() - 1);
                if (opPays > 0 || ownPays > 0) {
                    s.breaches++;
                    AlarmManager.get(w).raise(w, "admin:sla:" + s.id, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "SLA S" + s.id + " " + s.line + " missed: " + row, null, false);
                    tell(w, op, "§e📉 SLA S" + s.id + ": " + row);
                    tell(w, own, "§e📉 SLA S" + s.id + ": " + row);
                }
            }
            ad.markDirty();
        }
    }
}
