package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras biz ... - the world + business layer (her "next 48" #11-15, #17, #18, #32-38, #47): status, self-test and a
 * command for everything the desks and boards do (so it can all be tested and scripted).
 */
public final class BizCommand {
    private BizCommand() {}

    public static final String USAGE = "biz [status|selftest|towns|journeys|slots|slot|cargo|cases|env|invest|research|insure|claims|audit|ca|deal|deals|stock|bond|trophies|pso|scenario|site]";

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
    private static int i(String[] a, int k, int d) { try { return k < a.length ? Integer.parseInt(a[k]) : d; } catch (NumberFormatException e) { return d; } }
    private static long l(String[] a, int k, long d) { try { return k < a.length ? Math.round(Double.parseDouble(a[k].replace("$", "")) * (a[k].contains(".") || a[k].startsWith("$") ? 100 : 1)) : d; } catch (NumberFormatException e) { return d; } }
    private static boolean op(ICommandSender s) { return s.canUseCommand(2, "irextras"); }

    private static Company mine(ICommandSender s) {
        return s.getCommandSenderEntity() instanceof EntityPlayer p ? CompanyData.get(p.world).of(p.getUniqueID()) : null;
    }

    private static Company director(ICommandSender s) {
        Company c = mine(s);
        if (c == null) { say(s, "§cYou're not in a railway company (found or join one at a Company HQ)"); return null; }
        if (!(s.getCommandSenderEntity() instanceof EntityPlayer p) || !c.can(p.getUniqueID(), r -> r.finance)) { say(s, "§cOnly " + c.name + "'s owner or a director can do that"); return null; }
        return c;
    }

    public static void run(ICommandSender s, World w, String[] a) {
        if (!BizConfig.enabled) { say(s, "§7The world + business layer is switched off (irextras_biz.cfg)"); return; }
        BizData d = BizData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        CompanyData cd = CompanyData.get(w);
        switch (sub) {
            case "status": {
                say(s, "§b== World + business layer");
                say(s, "§7 towns " + d.towns.towns.size() + " · capacity slots " + d.capacity.allocs.size() + " (" + d.capacity.bids.stream().filter(b -> b.state.equals("OPEN")).count() + " sealed bids)"
                        + " · custody records " + d.custody.recs.size() + " (" + d.custody.cases.stream().filter(c -> c.state.equals("OPEN")).count() + " open cases)");
                say(s, "§7 connections made " + (d.journeys.connectionPct() < 0 ? "-" : String.format(Locale.ROOT, "%.0f%%", d.journeys.connectionPct())) + " · abandoned today " + Math.round(d.journeys.abandonedDay)
                        + " · listings " + d.securities.listings.size() + " · bonds " + d.securities.bonds.size() + " · PSO contracts " + d.pso.contracts.size() + " / tenders " + d.pso.tenders.size());
                say(s, "§7 research tree " + (BizConfig.researchTree ? "ON" : "off") + " · regulator " + (BizConfig.regulator ? "on" : "OFF") + (BizConfig.regulatorFines ? " (fines)" : "")
                        + " · reject bad cargo " + (BizConfig.rejectBadCargo ? "ON" : "off") + " · enforce slots " + (BizConfig.enforceSlots ? "ON" : "off")
                        + (d.scenario.applied.isEmpty() ? "" : " · scenario \"" + d.scenario.applied + "\""));
                for (int k = 0; k < Math.min(6, d.log.size()); k++) say(s, "§8 " + d.log.get(k));
                return;
            }
            case "selftest": for (String r : SelfTest.run(w)) say(s, r); return;
            case "towns": {
                if (a.length > 2 && a[2].equalsIgnoreCase("found")) {
                    if (!op(s)) { say(s, "Needs op."); return; }
                    BlockPos p = s.getPosition();
                    Towns.Town t = d.towns.found(w, p, a.length > 3 ? a[3] : "", true, i(a, 4, 500));
                    say(s, "§aTown " + t.name + " founded at " + p.getX() + " " + p.getZ() + " (" + Math.round(t.pop) + " people)");
                    return;
                }
                if (d.towns.towns.isEmpty()) { say(s, "§7No towns yet - place a Town Hall Planning Board, or a Town Market the railway serves"); return; }
                for (Towns.Town t : d.towns.towns.values())
                    say(s, String.format(Locale.ROOT, "§e%s§7 %s %d people (%+.2f%%/day) · access %.0f · jobs %.0f%% · goods %.0f · noise %.0f dB · %s", t.name, t.tier().toLowerCase(Locale.ROOT),
                            Math.round(t.pop), t.growthPct, t.access, t.jobs, t.goods, t.noiseDb, t.why));
                return;
            }
            case "journeys": {
                say(s, String.format(Locale.ROOT, "§b== Journeys§7: %.0f arrived today, %.0f abandoned (%.0f all time), connections made %s, avg sampled trip %s",
                        d.journeys.completedDay, d.journeys.abandonedDay, d.journeys.abandonedTotal, d.journeys.connectionPct() < 0 ? "-" : String.format(Locale.ROOT, "%.0f%%", d.journeys.connectionPct()),
                        d.journeys.avgJourneyMin() < 0 ? "-" : String.format(Locale.ROOT, "%.0f min", d.journeys.avgJourneyMin())));
                for (Map.Entry<Long, Journeys.Ix> e : d.journeys.interchanges().entrySet()) {
                    Journeys.Ix x = e.getValue();
                    if (x.transfers < 0.5 && x.madeY < 0.5) continue;
                    say(s, String.format(Locale.ROOT, "§7 %s: %.0f changing, made %.0f, missed %.0f, abandoned %.0f, rerouted %.0f (step-free lost %.0f), avg wait %.1f min",
                            StationData.get(w).names().getOrDefault(e.getKey(), "?"), x.transfers, x.made, x.missed, x.abandoned, x.rerouted, x.stepFreeLost, x.waited > 0 ? x.waitSum / x.waited * 60 / 1000 : 0));
                }
                for (int k = 0; k < Math.min(3, d.journeys.trips.size()); k++) {
                    Journeys.Trip t = d.journeys.trips.get(k);
                    say(s, "§e Trip J" + t.id + " " + t.state + "§7: " + String.join(" > ", t.legs));
                }
                return;
            }
            case "slots": {
                for (Capacity.Alloc x : d.capacity.allocs) { Company c = cd.byId(x.company); say(s, "§a slot #" + x.id + " " + (c == null ? "?" : c.mark) + " " + x.res + " " + Capacity.WINDOWS[x.window] + " x" + x.paths + " @ " + Coins.fmt(x.cents) + (x.season < 0 ? " recurring" : " " + Capacity.SEASONS[x.season]) + " · used " + x.used + ", unauthorised seen " + x.unauthorisedSeen); }
                for (Capacity.Bid b : d.capacity.bids) { Company c = cd.byId(b.company); say(s, "§7 bid #" + b.id + " " + (c == null ? "?" : c.mark) + " " + b.res + " " + Capacity.WINDOWS[b.window] + " x" + b.paths + " @ " + Coins.fmt(b.cents) + " " + b.state); }
                for (int k = 0; k < Math.min(5, d.capacity.decisions.size()); k++) say(s, "§8 " + d.capacity.decisions.get(k));
                if (d.capacity.allocs.isEmpty() && d.capacity.bids.isEmpty()) say(s, "§7No slots or bids. /irextras biz slot bid C:<line> <window 0-5> <paths> <$/path/day> [season 0-3]");
                return;
            }
            case "slot": {
                String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                if (act.equals("close")) { if (!op(s)) { say(s, "Needs op."); return; } say(s, "§b" + d.capacity.close(w, d) + " bids decided"); return; }
                if (act.equals("paths")) {
                    if (!op(s) || a.length < 5) { say(s, "Needs op: slot paths <res> <n> [reserve $]"); return; }
                    Capacity.Resource r = d.capacity.resource(w, a[3]);
                    if (r == null) { say(s, "§cNo such resource " + a[3]); return; }
                    r.paths = Math.max(1, i(a, 4, r.paths)); if (a.length > 5) r.reserve = l(a, 5, r.reserve);
                    say(s, "§a" + r.name + ": " + r.paths + " paths per window, reserve " + Coins.fmt(r.reserve)); return;
                }
                Company c = director(s);
                if (c == null) return;
                if (act.equals("withdraw")) { say(s, d.capacity.withdraw(c, i(a, 3, -1))); return; }
                if (act.equals("bid") && a.length >= 7) { say(s, d.capacity.bid(w, c, a[3], i(a, 4, -1), i(a, 5, 1), l(a, 6, 0), i(a, 7, -1))); return; }
                say(s, "/irextras biz slot bid <C:line|T:station|S:siding> <window 0-5> <paths> <cents|$x.yy> [season 0-3] · withdraw <id> · close · paths <res> <n>");
                return;
            }
            case "cargo": {
                if (a.length < 3) { say(s, "/irextras biz cargo <container number or K<contract>>"); return; }
                String id = a[2].replace(" ", "").toUpperCase(Locale.ROOT);
                Custody.Rec r = null;
                for (Custody.Rec x : d.custody.recs.values()) if (x.id.replace(" ", "").equalsIgnoreCase(id)) r = x;
                if (r == null) { say(s, "§7No custody record for " + id); return; }
                say(s, String.format(Locale.ROOT, "§b%s§7 %s · owner %s → %s · seal %s · clean %.0f%% · moisture %.1f%% · %.0f C%s%s", r.id, r.status, r.owner, r.consignee,
                        r.seal == 0 ? "none" : r.seal + (r.sealBroken ? " BROKEN" : ""), r.clean, r.moisture, r.tempC, r.food ? " · FOOD" : "", r.haz ? " · DANGEROUS" : ""));
                for (String e : r.events) { String[] p = e.split("\\|", 5); if (p.length == 5) say(s, "§7  " + BizData.hhmm(Long.parseLong(p[0])) + " D" + Long.parseLong(p[0]) / BizData.DAY + " §e" + p[1] + "§7 " + p[2] + " · " + p[3] + " · " + p[4]); }
                for (String f : d.custody.inspect(w, r)) say(s, "§c  finding: " + f);
                return;
            }
            case "cases": {
                if (d.custody.cases.isEmpty()) { say(s, "§7No cargo investigations"); return; }
                for (Custody.Case c : d.custody.cases) say(s, "§e #" + c.id + " " + c.rec + " " + c.state + "§7 - " + c.findings + " · liable: " + c.liable + " · value " + Coins.fmt(c.value));
                return;
            }
            case "washcar": {
                if (a.length < 3) { say(s, "/irextras biz washcar <car uuid prefix>"); return; }
                for (UUID u : new ArrayList<>(d.custody.cars.keySet())) if (u.toString().startsWith(a[2])) { say(s, d.custody.washCar(u)); return; }
                say(s, "§7No dirty car " + a[2]); return;
            }
            case "env": {
                double[] in = d.env.intensity();
                say(s, String.format(Locale.ROOT, "§b== Environment§7: diesel %s kg CO2/train-km, electric %s kg/train-km, regenerative braking saved %.0f kg today (%.0f%% of traction energy back) · land %.1f ha · waste %.0f t",
                        in[0] < 0 ? "-" : String.format(Locale.ROOT, "%.2f", in[0]), in[1] < 0 ? "-" : String.format(Locale.ROOT, "%.2f", in[1]), in[2], in[3], d.env.landHa, d.env.wasteT));
                for (Map.Entry<Integer, Environment.Acct> e : d.env.accts.entrySet()) {
                    Environment.Acct x = e.getValue();
                    Company c = cd.byId(e.getKey());
                    StringBuilder m = new StringBuilder();
                    for (Environment.Measure ms : Environment.Measure.values()) if (x.measures[ms.ordinal()] > 0) m.append(' ').append(ms.label.toLowerCase(Locale.ROOT)).append(" x").append(x.measures[ms.ordinal()]);
                    say(s, String.format(Locale.ROOT, "§7 %s: diesel %.1f kWh (%.1f L), electric %.1f kWh (regen %.1f), CO2 %.1f kg (yesterday %.1f), NOx %.0f g, PM %.1f g, %.2f+%.2f km%s",
                            c == null ? "unowned trains" : c.mark, x.dieselKwh, x.fuelL, x.elecKwh, x.regenKwh, x.co2Kg, x.co2Yesterday, x.noxG, x.pmG, x.dieselKm, x.elecKm, m.length() == 0 ? "" : " ·" + m));
                }
                for (Towns.Town t : d.towns.towns.values()) say(s, String.format(Locale.ROOT, "§7 noise at %s: %.0f dB Leq (%d barriers)", t.name, t.noiseDb, t.barriers));
                return;
            }
            case "invest": {
                Company c = director(s);
                if (c == null) return;
                Environment.Measure m;
                try { m = Environment.Measure.valueOf(a.length > 2 ? a[2].toUpperCase(Locale.ROOT) : ""); } catch (IllegalArgumentException e) { say(s, "/irextras biz invest <" + Arrays.toString(Environment.Measure.values()) + ">"); return; }
                Towns.Town near = null;
                double bd = Double.MAX_VALUE;
                for (Towns.Town t : d.towns.towns.values()) { double dd = t.at().distanceSq(s.getPosition()); if (dd < bd) { bd = dd; near = t; } }
                say(s, d.env.invest(w, d, c, m, near));
                return;
            }
            case "research": {
                Company c = mine(s);
                if (a.length < 3 || c == null) {
                    say(s, "§b== Research tree " + (BizConfig.researchTree ? "§aON" : "§7OFF (everything unlocked)"));
                    Research.Prog p = c == null ? null : d.research.prog(c.id);
                    for (Research.Tech t : Research.Tech.values())
                        say(s, "§7 " + t.name() + (p != null && (p.done[t.ordinal()] || p.grandfathered) ? " §aDONE" : p != null ? " §e" + p.points[t.ordinal()] + "/" + t.points : "") + "§7 - " + t.label
                                + (t.needs == null ? "" : " (after " + t.needs.label + ")") + ": " + t.benefit + " · part " + t.part);
                    say(s, "§7/irextras biz research <TECH> invest <$> | experiment | give | focus");
                    return;
                }
                Research.Tech t;
                try { t = Research.Tech.valueOf(a[2].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { say(s, "§cNo technology " + a[2]); return; }
                String act = a.length > 3 ? a[3].toLowerCase(Locale.ROOT) : "";
                if (act.equals("invest")) { Company dc = director(s); if (dc != null) say(s, d.research.invest(w, d, dc, t, l(a, 4, 10_000))); }
                else if (act.equals("experiment")) say(s, d.research.experiment(w, d, c, t));
                else if (act.equals("give") && s.getCommandSenderEntity() instanceof EntityPlayer p) say(s, d.research.component(w, d, c, t, p));
                else if (act.equals("focus")) { d.research.prog(c.id).current = t.ordinal(); say(s, "§a" + c.mark + "'s experience now goes into " + t.label); }
                else say(s, "invest <$> | experiment | give | focus");
                return;
            }
            case "insure": {
                Company c = director(s);
                if (c == null) return;
                Insurance.Cover cv;
                try { cv = Insurance.Cover.valueOf(a.length > 2 ? a[2].toUpperCase(Locale.ROOT) : "COMPREHENSIVE"); } catch (IllegalArgumentException e) { say(s, "/irextras biz insure <NONE|THIRD_PARTY|COMPREHENSIVE> [excess 0-3]"); return; }
                say(s, d.insurance.take(w, d, c, cv, i(a, 3, 1)));
                return;
            }
            case "claims": {
                for (Insurance.Claim k : d.insurance.claims) { Company c = cd.byId(k.company); say(s, "§e C" + k.id + " " + (c == null ? "?" : c.mark) + " " + k.state + "§7 " + k.what + " · value " + Coins.fmt(k.value) + " · paid " + Coins.fmt(k.paid) + " · evidence " + k.evidence + (k.notes.isEmpty() ? "" : " · " + k.notes)); }
                if (d.insurance.claims.isEmpty()) say(s, "§7No claims");
                if (a.length > 3 && a[2].equalsIgnoreCase("settle") && op(s)) for (Insurance.Claim k : d.insurance.claims) if (k.id == i(a, 3, -1)) say(s, d.insurance.settle(w, d, k));
                return;
            }
            case "audit": {
                Company c = a.length > 2 ? cd.byName(a[2]) : mine(s);
                if (c == null) { say(s, "§c/irextras biz audit <company>"); return; }
                if (!BizConfig.regulator) { say(s, "§7The regulator is switched off"); return; }
                Regulator.Audit au = d.regulator.audit(w, d, c);
                say(s, "§bAudit " + c.name + ": " + au.score + "/100");
                for (int k = 0; k < Regulator.CHECKS.length; k++) say(s, (au.pass[k] ? "§a PASS " : "§c FAIL ") + Regulator.CHECKS[k] + "§7 - " + au.detail[k]);
                for (Regulator.Ca x : d.regulator.cas) if (x.company == c.id && !x.state.equals("CLOSED")) say(s, "§e CA" + x.id + " " + x.state + "§7: " + Regulator.ACTIONS[x.check] + " by day " + x.deadline / BizData.DAY);
                return;
            }
            case "ca": {
                Company c = director(s);
                if (c == null) return;
                say(s, a.length > 3 && a[2].equalsIgnoreCase("appeal") ? d.regulator.appeal(w, c, i(a, 3, -1)) : d.regulator.accept(w, c, i(a, 3, -1)));
                return;
            }
            case "deal": {
                Company c = director(s);
                if (c == null) return;
                String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                if (act.equals("accept")) { say(s, d.deals.accept(w, d, c, i(a, 3, -1))); return; }
                if (act.equals("reject")) { say(s, d.deals.reject(w, c, i(a, 3, -1))); return; }
                if (act.equals("offer") && a.length >= 5) {
                    Company t = cd.byName(a[3]);
                    if (t == null) { say(s, "§cNo company " + a[3]); return; }
                    Deals.Kind k;
                    switch (a[4].toLowerCase(Locale.ROOT)) {
                        case "merger": k = Deals.Kind.MERGER; break;
                        case "line": k = Deals.Kind.ACQUIRE_LINE; break;
                        case "train": k = Deals.Kind.ACQUIRE_TRAIN; break;
                        case "station": k = Deals.Kind.ACQUIRE_STATION; break;
                        case "franchise": k = Deals.Kind.FRANCHISE; break;
                        default: say(s, "§cmerger | line | train | station | franchise"); return;
                    }
                    say(s, d.deals.offer(w, c, t, k, a.length > 5 ? a[5] : "", l(a, 6, 0), i(a, 7, 10), i(a, 8, 2)));
                    return;
                }
                say(s, "/irextras biz deal offer <mark> merger | line <name> <$> | train <uuid prefix> <$> | station <name> <$> | franchise <line> 0 <fee%> <weeks> · accept|reject <id>");
                return;
            }
            case "deals": {
                if (d.deals.deals.isEmpty()) { say(s, "§7No deals"); return; }
                for (Deals.Deal x : d.deals.deals) { Company f = cd.byId(x.from), t = cd.byId(x.to); say(s, "§e D" + x.id + " " + x.state + "§7 " + (f == null ? "?" : f.mark) + " -> " + (t == null ? "?" : t.mark) + " " + Deals.describe(w, x) + (x.result.isEmpty() ? "" : " · " + x.result)); }
                return;
            }
            case "stock": {
                String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                EntityPlayerMP p = s.getCommandSenderEntity() instanceof EntityPlayerMP x ? x : null;
                if (act.isEmpty()) {
                    say(s, "§b== Railway Stock Exchange§7 index " + d.securities.index(w));
                    for (Securities.Listing x : d.securities.listings.values()) { Company c = cd.byId(x.company); if (c != null) say(s, String.format(Locale.ROOT, "§e %s§7 last %s (%+.1f%%) fair %s bid %s ask %s vol %d", c.mark, Coins.fmt(x.last), (x.last - x.prev) * 100.0 / Math.max(1, x.prev), Coins.fmt(Securities.fair(c)), Coins.fmt(d.securities.bestBid(c.id)), Coins.fmt(d.securities.bestAsk(c.id)), x.volumeDay)); }
                    for (Securities.Order o : d.securities.orders) { Company c = cd.byId(o.company); say(s, "§7 #" + o.id + " " + (o.buy ? "BUY " : "SELL ") + o.qty + " " + (c == null ? "?" : c.mark) + " @ " + Coins.fmt(o.price) + " (" + o.whoName + ")"); }
                    return;
                }
                if (p == null) { say(s, "Players only"); return; }
                if (act.equals("list")) { Company c = director(s); if (c != null) say(s, d.securities.list(w, c, p, i(a, 3, 100), l(a, 4, 0))); return; }
                if (act.equals("cancel")) { for (Securities.Order o : d.securities.orders) if (o.id == i(a, 3, -1)) { say(s, d.securities.cancel(w, cd.byId(o.company), p, o.id)); return; } say(s, "§7No order"); return; }
                if ((act.equals("buy") || act.equals("sell")) && a.length >= 6) { Company c = cd.byName(a[3]); if (c == null) { say(s, "§cNo company " + a[3]); return; } say(s, d.securities.order(w, c, p, act.equals("buy"), i(a, 4, 1), l(a, 5, 1))); return; }
                say(s, "/irextras biz stock [list <qty/1000> [$] | buy|sell <mark> <qty> <$> | cancel <id>]");
                return;
            }
            case "bond": {
                String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                if (act.equals("issue")) { Company c = director(s); if (c != null) say(s, d.securities.issueBond(w, c, i(a, 3, 50), i(a, 4, 8), i(a, 5, 150))); return; }
                if (act.equals("buy") && s.getCommandSenderEntity() instanceof EntityPlayerMP p) { for (Securities.Bond b : d.securities.bonds) if (b.id == i(a, 3, -1)) { say(s, d.securities.buyBond(w, p, b, i(a, 4, 1))); return; } say(s, "§7No bond"); return; }
                for (Securities.Bond b : d.securities.bonds) { Company c = cd.byId(b.company); say(s, "§e B" + b.id + " " + (c == null ? "?" : c.mark) + " " + b.state + "§7 " + b.sold + "/" + b.units + " x $100, " + b.couponBp / 100.0 + "%/wk, " + b.weeks + " wk"); }
                say(s, "§7/irextras biz bond issue <units> <weeks> <coupon bp/wk> · buy <id> <units>");
                return;
            }
            case "trophies": {
                for (Company c : cd.all()) say(s, "§6🏆 " + c.mark + " level " + d.mastery.level(c.id) + " (" + d.mastery.xp(c.id) + " xp): §e" + String.join(", ", d.mastery.trophies.getOrDefault(c.id, new LinkedHashSet<>())));
                for (String[] aw : Mastery.AWARDS) say(s, "§7 " + aw[0] + " - " + aw[3]);
                return;
            }
            case "pso": {
                if (a.length > 3 && a[2].equalsIgnoreCase("bid")) { Company c = director(s); if (c != null) say(s, d.pso.bid(w, c, i(a, 3, -1), l(a, 4, 5_000))); return; }
                for (Pso.Tender t : d.pso.tenders) say(s, "§e T" + t.id + " " + t.state + "§7 " + t.line + " - " + t.why + " · needs " + t.minDeps + " deps, " + t.minPunct + "% punctual" + (t.stepFree ? ", step-free" : "") + " · " + t.bids.size() + " bids, closes day " + t.closes / BizData.DAY);
                for (Pso.Contract k : d.pso.contracts) { Company c = cd.byId(k.company); say(s, "§a P" + k.id + " " + k.state + "§7 " + k.line + " " + (c == null ? "?" : c.mark) + " " + Coins.fmt(k.subsidyDay) + "/day · met " + k.metDays + ", missed " + k.missedDays + " · " + k.lastCheck); }
                if (d.pso.tenders.isEmpty() && d.pso.contracts.isEmpty()) say(s, "§7No tenders yet - the county tenders lines that serve towns with poor rail access (daily)");
                return;
            }
            case "scenario": {
                String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                if (act.equals("generate")) {
                    if (!op(s)) { say(s, "Needs op."); return; }
                    String name = a.length > 3 ? a[3] : "region";
                    ScenarioGen.Spec sp = ScenarioGen.generate(name, a.length > 4 ? l(a, 4, w.rand.nextLong()) : w.rand.nextLong(), a.length > 5 ? a[5].toLowerCase(Locale.ROOT) : "normal");
                    try { say(s, "§aScenario written: " + ScenarioGen.write(sp).getPath() + " - " + sp.towns.size() + " towns, " + sp.companies.size() + " rivals, " + sp.goals.size() + " goals (applies only to a brand-new world)"); }
                    catch (java.io.IOException e) { say(s, "§cCouldn't write: " + e); }
                    return;
                }
                if (act.equals("apply")) {
                    if (!op(s)) { say(s, "Needs op."); return; }
                    ScenarioGen.Spec sp = ScenarioGen.read(a.length > 3 ? a[3] : "");
                    if (sp == null) { say(s, "§cNo scenario file " + (a.length > 3 ? a[3] : "")); return; }
                    say(s, d.scenario.apply(w, d, sp, s.getCommandSenderEntity() instanceof EntityPlayer p ? p : null));
                    return;
                }
                say(s, d.scenario.applied.isEmpty() ? "§7No scenario in this world. " + (d.scenario.refuse(w) == null ? "It is brand new - /irextras biz scenario apply <name>" : d.scenario.refuse(w)) : "§bScenario \"" + d.scenario.applied + "\"");
                for (ScenarioGen.GoalSpec g : d.scenario.goals) say(s, "§7 " + (d.scenario.value(w, d, g) >= g.target ? "§a✔ " : "§e· ") + g.text + " - " + d.scenario.value(w, d, g) + "/" + g.target + " (by day " + g.byDay + ")");
                say(s, "§7/irextras biz scenario generate <name> [seed] [easy|normal|hard] · apply <name>");
                return;
            }
            case "site": {
                for (net.minecraft.tileentity.TileEntity te : new ArrayList<>(w.loadedTileEntityList))
                    if (te instanceof com.dogpound.railmap.industry.TileConstructionSite cs) { String[] r = cs.pmRow(); say(s, "§e" + r[1] + "§7 " + r[2] + " " + r[3] + "% · crews " + r[4] + " · " + r[5] + " · budget " + r[6] + " spent " + r[7] + (r[8].isEmpty() ? "" : " · §cHELD " + r[8]) + "§7 · delays " + r[9] + " h · " + r[10] + " @ " + r[11]); }
                return;
            }
            default: say(s, "/irextras " + USAGE);
        }
    }
}
