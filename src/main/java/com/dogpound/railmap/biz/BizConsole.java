package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.*;

/**
 * The Control Center's WORLD tab rows (PacketRailState.biz), each tagged by its first field, and the tab's buttons
 * (PacketBizOp -> op). Tags: pm site, sr/sl/sb/sd capacity, tw town, jx/jt journeys, ix/ia/il intermodal, cu/cc cargo,
 * en/eg environment, rs research, ip/ic insurance, ra/rc regulator, dd deals, sx/so/sn/st stocks, mt/mf mastery,
 * pt/pc PSO, sc scenario, lg the layer's log, S summary.
 */
public final class BizConsole {
    private BizConsole() {}

    private static String f0(double v) { return String.format(Locale.ROOT, "%.0f", v); }
    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    public static void fill(World w, List<String[]> out) {
        if (!BizConfig.enabled) return;
        BizData d = BizData.get(w);
        CompanyData cd = CompanyData.get(w);
        Map<Long, String> names = StationData.get(w).names();
        out.add(new String[]{"S", String.valueOf(BizConfig.researchTree), String.valueOf(BizConfig.regulator), String.valueOf(BizConfig.regulatorFines), String.valueOf(BizConfig.rejectBadCargo),
                String.valueOf(BizConfig.enforceSlots), String.valueOf(d.securities.index(w)), d.journeys.connectionPct() < 0 ? "-" : f0(d.journeys.connectionPct()), d.scenario.applied});
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList)) if (te instanceof com.dogpound.railmap.industry.TileConstructionSite cs && !te.isInvalid()) out.add(cs.pmRow());
        // #12 capacity
        for (Capacity.Resource r : d.capacity.resources.values()) out.add(new String[]{"sr", r.key(), r.name, r.kind.name(), String.valueOf(r.paths), Coins.fmt(r.reserve)});
        int season = Capacity.season(w);
        for (Capacity.Alloc a : d.capacity.allocs) { Company c = cd.byId(a.company); out.add(new String[]{"sl", String.valueOf(a.id), c == null ? "?" : c.mark, a.res, Capacity.WINDOWS[a.window], String.valueOf(a.paths), Coins.fmt(a.cents), a.season < 0 ? "RECURRING" : Capacity.SEASONS[a.season], String.valueOf(a.used), String.valueOf(a.unauthorisedSeen), a.season < 0 || a.season == season ? "ACTIVE" : "OFF-SEASON"}); }
        for (Capacity.Bid b : d.capacity.bids) { Company c = cd.byId(b.company); out.add(new String[]{"sb", String.valueOf(b.id), c == null ? "?" : c.mark, b.res, Capacity.WINDOWS[b.window], String.valueOf(b.paths), Coins.fmt(b.cents), b.season < 0 ? "RECURRING" : Capacity.SEASONS[b.season], b.state}); }
        for (int i = 0; i < Math.min(12, d.capacity.decisions.size()); i++) out.add(new String[]{"sd", d.capacity.decisions.get(i)});
        // #13 towns
        for (Towns.Town t : d.towns.towns.values())
            out.add(new String[]{"tw", t.name, t.tier(), f0(t.pop), String.format(Locale.ROOT, "%+.2f", t.growthPct), f0(t.access), f0(t.jobs), f0(t.goods), f0(t.noiseDb), String.valueOf(t.barriers), String.valueOf(t.stations), t.why, BizDesk.spark(t.hist), t.hall ? "HALL" : "MARKET"});
        // #14 journeys
        for (Map.Entry<Long, Journeys.Ix> e : d.journeys.interchanges().entrySet()) {
            Journeys.Ix x = e.getValue();
            if (x.transfers < 0.5 && x.madeY + x.missedY < 0.5) continue;
            out.add(new String[]{"jx", names.getOrDefault(e.getKey(), "?"), f0(x.transfers), f0(x.made), f0(x.missed), f0(x.abandoned), f0(x.rerouted), f0(x.stepFreeLost), x.waited > 0 ? f1(x.waitSum / x.waited * 60 / 1000) : "-", f0(x.madeY), f0(x.missedY), f0(x.abandonedY)});
        }
        out.add(new String[]{"js", f0(d.journeys.completedDay), f0(d.journeys.abandonedDay), f0(d.journeys.abandonedTotal), d.journeys.avgJourneyMin() < 0 ? "-" : f0(d.journeys.avgJourneyMin()), String.valueOf((int) d.journeys.journeys)});
        for (int i = 0; i < Math.min(12, d.journeys.trips.size()); i++) { Journeys.Trip t = d.journeys.trips.get(i); out.add(new String[]{"jt", "J" + t.id, t.state, names.getOrDefault(t.from, "?"), names.getOrDefault(t.to, "?"), String.join(" > ", t.legs)}); }
        // #15 intermodal
        for (Intermodal.Stat s : d.intermodal.stats.values())
            out.add(new String[]{"ix", s.name, s.port ? "PORT" : "TERMINAL", s.quay + "/" + s.quayCap, String.valueOf(s.shipMoves), String.valueOf(s.gateMoves), String.valueOf(s.railMoves), String.valueOf(s.kept), String.valueOf(s.walkIns), String.valueOf(s.noShows), Coins.fmt(s.fees), Coins.fmt(s.demurrage)});
        long hourNow = Intermodal.hourNow(w);
        for (Intermodal.Appt a : d.intermodal.appts) if (a.hour >= hourNow - 2) out.add(new String[]{"ia", BizData.hhmm(a.hour * 1000), a.who, a.state, d.intermodal.stats.containsKey(a.gate) ? d.intermodal.stats.get(a.gate).name : "?"});
        for (int i = 0; i < Math.min(10, d.intermodal.log.size()); i++) out.add(new String[]{"il", d.intermodal.log.get(i)});
        out.add(new String[]{"if", Coins.fmt(d.intermodal.throughFreightPaid)});
        // #17 cargo
        List<Custody.Rec> recs = new ArrayList<>(d.custody.recs.values());
        Collections.reverse(recs);
        for (int i = 0; i < Math.min(30, recs.size()); i++) {
            Custody.Rec r = recs.get(i);
            String last = r.events.isEmpty() ? "" : r.events.get(r.events.size() - 1);
            String[] lp = last.split("\\|", 5);
            out.add(new String[]{"cu", r.id, r.status, r.owner, r.consignee, r.seal == 0 ? "-" : r.seal + (r.sealBroken ? " BROKEN" : ""), f0(r.clean), f1(r.moisture), f0(r.tempC),
                    (r.food ? "FOOD " : "") + (r.haz ? "DG " : "") + (r.grain ? "GRAIN " : "") + (r.dirty ? "BULK" : ""), lp.length == 5 ? lp[1] + " " + lp[2] + " " + lp[4] : "", String.valueOf(r.events.size())});
        }
        for (Custody.Case c : d.custody.cases) out.add(new String[]{"cc", String.valueOf(c.id), c.rec, c.state, c.findings, c.liable, Coins.fmt(c.value)});
        // #18 environment
        double[] in = d.env.intensity();
        out.add(new String[]{"eg", in[0] < 0 ? "-" : String.format(Locale.ROOT, "%.2f", in[0]), in[1] < 0 ? "-" : String.format(Locale.ROOT, "%.2f", in[1]), f0(in[2]), f0(in[3]), f1(d.env.landHa), f0(d.env.wasteT), String.valueOf(BizConfig.gridGramsPerKwh)});
        for (Map.Entry<Integer, Environment.Acct> e : d.env.accts.entrySet()) {
            Environment.Acct a = e.getValue();
            Company c = cd.byId(e.getKey());
            StringBuilder m = new StringBuilder();
            for (Environment.Measure ms : Environment.Measure.values()) if (a.measures[ms.ordinal()] > 0) m.append(m.length() > 0 ? ", " : "").append(ms.label).append(" x").append(a.measures[ms.ordinal()]);
            out.add(new String[]{"en", c == null ? "(unowned)" : c.mark, f1(a.dieselKwh), f1(a.fuelL), f1(a.elecKwh), f1(a.regenKwh), f1(a.co2Kg), f1(a.co2Yesterday), f0(a.noxG), f1(a.pmG), f1(a.dieselKm + a.elecKm), m.toString()});
        }
        // #32 research
        for (Company c : cd.all()) {
            Research.Prog p = d.research.prog(c.id);
            StringBuilder b = new StringBuilder();
            for (Research.Tech t : Research.Tech.values()) b.append(b.length() > 0 ? "," : "").append(p.done[t.ordinal()] || p.grandfathered || !BizConfig.researchTree ? "D" : String.valueOf(p.points[t.ordinal()]));
            out.add(new String[]{"rs", c.mark, b.toString(), p.current < 0 ? "-" : Research.Tech.values()[p.current].name(), String.valueOf(p.grandfathered)});
        }
        // #33 insurance
        for (Map.Entry<Integer, Insurance.Policy> e : d.insurance.policies.entrySet()) {
            Company c = cd.byId(e.getKey());
            Insurance.Policy p = e.getValue();
            if (c != null) out.add(new String[]{"ip", c.mark, p.cover.name().replace('_', ' '), Coins.fmt(Insurance.EXCESS[p.excess]), Coins.fmt(p.premiumDay), f0(p.compliance), p.overdue + "/" + p.units, String.valueOf(p.brakeFails), Coins.fmt(p.paidTotal), Coins.fmt(p.claimsPaid), String.valueOf(p.evidenceDocs)});
        }
        for (int i = d.insurance.claims.size() - 1, n = 0; i >= 0 && n < 30; i--, n++) {
            Insurance.Claim k = d.insurance.claims.get(i);
            Company c = cd.byId(k.company);
            out.add(new String[]{"ic", String.valueOf(k.id), c == null ? "?" : c.mark, k.state, k.what, Coins.fmt(k.value), Coins.fmt(k.liability), Coins.fmt(k.paid), String.valueOf(k.evidence), k.notes});
        }
        // #34 regulator
        for (Regulator.Audit a : d.regulator.last.values()) {
            Company c = cd.byId(a.company);
            StringBuilder fail = new StringBuilder();
            for (int i = 0; i < Regulator.CHECKS.length; i++) if (!a.pass[i]) fail.append(fail.length() > 0 ? ", " : "").append(Regulator.CHECKS[i]).append(" (").append(a.detail[i]).append(')');
            if (c != null) out.add(new String[]{"ra", c.mark, String.valueOf(a.score), "D" + a.when / BizData.DAY + " " + BizData.hhmm(a.when), fail.toString(), String.valueOf(c.id)});
        }
        for (Regulator.Ca x : d.regulator.cas) {
            if (x.state.equals("CLOSED") && w.getTotalWorldTime() - x.deadline > 3 * BizData.DAY) continue;
            Company c = cd.byId(x.company);
            out.add(new String[]{"rc", String.valueOf(x.id), c == null ? "?" : c.mark, Regulator.CHECKS[x.check], Regulator.ACTIONS[x.check], x.state, "D" + x.deadline / BizData.DAY + " " + BizData.hhmm(x.deadline), String.valueOf(x.appealed), Coins.fmt(x.fined)});
        }
        // #35 deals
        for (Deals.Deal x : d.deals.deals) { Company f = cd.byId(x.from), t = cd.byId(x.to); out.add(new String[]{"dd", String.valueOf(x.id), f == null ? "?" : f.mark, t == null ? "?" : t.mark, x.kind.name(), Deals.describe(w, x), x.state, x.result}); }
        // #36 stocks
        for (Securities.Listing l : d.securities.listings.values()) {
            Company c = cd.byId(l.company);
            if (c != null) out.add(new String[]{"sx", c.mark, c.name, Coins.fmt(l.last), String.format(Locale.ROOT, "%+.1f", (l.last - l.prev) * 100.0 / Math.max(1, l.prev)), Coins.fmt(Securities.fair(c)),
                    Coins.fmt(d.securities.bestBid(c.id)), Coins.fmt(d.securities.bestAsk(c.id)), String.valueOf(l.volumeDay), BizDesk.spark(Arrays.stream(l.hist).asDoubleStream().toArray())});
        }
        for (Securities.Order o : d.securities.orders) { Company c = cd.byId(o.company); out.add(new String[]{"so", String.valueOf(o.id), c == null ? "?" : c.mark, o.buy ? "BUY" : "SELL", String.valueOf(o.qty), Coins.fmt(o.price), o.whoName}); }
        for (Securities.Bond b : d.securities.bonds) { Company c = cd.byId(b.company); out.add(new String[]{"sn", String.valueOf(b.id), c == null ? "?" : c.mark, b.state, b.sold + "/" + b.units, String.format(Locale.ROOT, "%.2f", b.couponBp / 100.0), String.valueOf(b.weeks), b.matures > 0 ? "D" + b.matures / BizData.DAY : "-"}); }
        for (int i = 0; i < Math.min(12, d.securities.tape.size()); i++) out.add(new String[]{"st", d.securities.tape.get(i)});
        // #37 mastery
        for (Company c : cd.all()) out.add(new String[]{"mt", c.mark, String.valueOf(d.mastery.level(c.id)), String.valueOf(d.mastery.xp(c.id)), String.join(", ", d.mastery.trophies.getOrDefault(c.id, new LinkedHashSet<>())), String.valueOf(c.colour)});
        for (int i = 0; i < Math.min(12, d.mastery.feed.size()); i++) out.add(new String[]{"mf", d.mastery.feed.get(i)});
        // #38 PSO
        for (Pso.Tender t : d.pso.tenders) {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<Integer, Long> e : t.bids.entrySet()) { Company c = cd.byId(e.getKey()); b.append(b.length() > 0 ? ", " : "").append(c == null ? "?" : c.mark).append(' ').append(Coins.fmt(e.getValue())); }
            out.add(new String[]{"pt", String.valueOf(t.id), t.line, t.state, t.why, t.minDeps + " deps, " + t.minPunct + "% punct" + (t.stepFree ? ", step-free" : ""), b.toString(), "D" + t.closes / BizData.DAY});
        }
        for (Pso.Contract k : d.pso.contracts) { Company c = cd.byId(k.company); out.add(new String[]{"pc", String.valueOf(k.id), k.line, c == null ? "?" : c.mark, Coins.fmt(k.subsidyDay), k.state, k.metDays + "/" + k.missedDays, k.lastCheck, Coins.fmt(k.paid), "D" + k.until / BizData.DAY}); }
        // #47 scenario
        for (ScenarioGen.GoalSpec g : d.scenario.goals) out.add(new String[]{"sc", g.text, String.valueOf(d.scenario.value(w, d, g)), String.valueOf(g.target), String.valueOf(g.byDay)});
        for (int i = 0; i < Math.min(15, d.log.size()); i++) out.add(new String[]{"lg", d.log.get(i)});
    }

    /** a WORLD-tab button (PacketBizOp); null = no message */
    public static String op(EntityPlayerMP p, int op, String arg) {
        World w = p.world;
        BizData d = BizData.get(w);
        CompanyData cd = CompanyData.get(w);
        Company mine = cd.of(p.getUniqueID());
        boolean director = mine != null && mine.can(p.getUniqueID(), r -> r.finance), admin = p.canUseCommand(2, "irextras");
        String[] a = arg.split(",");
        int id;
        try { id = Integer.parseInt(a[0].trim()); } catch (NumberFormatException e) { id = -1; }
        switch (op) {
            case 1: return admin ? d.capacity.close(w, d) + " capacity bids decided" : "Only a server operator closes an auction early";
            case 2: { Company c = cd.byId(id); return c == null ? null : BizConfig.regulator ? "Audit " + c.mark + ": " + d.regulator.audit(w, d, c).score + "/100" : "The regulator is switched off"; }
            case 3: { if (!admin) return "Only a server operator settles a claim early"; for (Insurance.Claim k : d.insurance.claims) if (k.id == id) return d.insurance.settle(w, d, k); return null; }
            case 4: return director ? d.regulator.accept(w, mine, id) : "Directors only";
            case 5: return director ? d.regulator.appeal(w, mine, id) : "Directors only";
            case 6: return director ? d.deals.accept(w, d, mine, id) : "Directors only";
            case 7: return director ? d.deals.reject(w, mine, id) : "Directors only";
            case 8: return director ? d.capacity.withdraw(mine, id) : "Directors only";
            case 9: { if (mine == null) return "Not in a company"; try { return d.research.experiment(w, d, mine, Research.Tech.valueOf(arg)); } catch (IllegalArgumentException e) { return null; } }
            case 10: { if (mine == null) return "Not in a company"; try { d.research.prog(mine.id).current = Research.Tech.valueOf(arg).ordinal(); return mine.mark + " research focus: " + Research.Tech.valueOf(arg).label; } catch (IllegalArgumentException e) { return null; } }
            case 11: { if (!director) return "Directors only"; try { return d.insurance.take(w, d, mine, Insurance.Cover.valueOf(a[0]), a.length > 1 ? Integer.parseInt(a[1]) : 1); } catch (RuntimeException e) { return null; } }
            case 12: { if (!director || a.length < 2) return "Directors only"; try { return d.pso.bid(w, mine, id, Long.parseLong(a[1])); } catch (NumberFormatException e) { return null; } }
            case 13: { if (!director || a.length < 5) return "Directors only"; try { return d.capacity.bid(w, mine, a[0], Integer.parseInt(a[1]), Integer.parseInt(a[2]), Long.parseLong(a[3]), Integer.parseInt(a[4])); } catch (NumberFormatException e) { return null; } }
            case 14: { if (!director) return "Directors only"; try { return d.env.invest(w, d, mine, Environment.Measure.valueOf(arg), null); } catch (IllegalArgumentException e) { return null; } }
            case 15: { for (Securities.Order o : d.securities.orders) if (o.id == id) return d.securities.cancel(w, cd.byId(o.company), p, id); return null; }
            case 16: { for (Securities.Bond b : d.securities.bonds) if (b.id == id) return d.securities.buyBond(w, p, b, 1); return null; }
            default: return null;
        }
    }
}
