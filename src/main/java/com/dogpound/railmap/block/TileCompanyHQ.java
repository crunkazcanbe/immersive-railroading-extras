package com.dogpound.railmap.block;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * The Company HQ (her ChatGPT list #46): the boardroom of a railway company. The big ledger board on the wall shows one
 * of five books - ACCOUNTS (the ledger), STAFF (roles, wages, wages owed), FLEET (every line, driverless train and
 * station, and who owns it), SHARES (the share register) and REPORT (this month against last) - and the desk works the
 * company for real: deposits and withdrawals in Pride coins, moving money into the maintenance budget, paying wages and
 * dividends, taking out and repaying a loan, hiring applicants, setting roles and wages, claiming lines / trains /
 * stations, public-service contracts. The safe is for company members only; the BOOKS key locks withdrawals.
 */
public class TileCompanyHQ extends TileFurniture {
    public static final String[] STORE_IDS = {"dl1", "dl2", "dl3", "dr1", "dr2", "dr3", "safe", "bk1", "bk2"};
    public static final String[] STORE_NAMES = {"Desk Drawer (left, bottom)", "Desk Drawer (left, middle)", "Desk Drawer (left, top)",
            "Desk Drawer (right, bottom)", "Desk Drawer (right, middle)", "Desk Drawer (right, top)", "Company Safe", "Records Cupboard (left)", "Records Cupboard (right)"};
    public static final int[] STORE_SIZES = {9, 9, 9, 9, 9, 9, 27, 18, 18};
    public static final String[] PAGES = {"ACCOUNTS", "STAFF", "FLEET", "SHARES", "REPORT"};
    private static final double[][] SPOTS = {{19.933, 7.0, 13.4}, {21.6, 7.0, 13.4}, {23.267, 7.0, 13.4}, {24.933, 7.0, 13.4}, {26.6, 7.0, 13.4}, {28.267, 7.0, 13.4}};
    @Override public double[][] shelfSpots() { return SPOTS; }
    public static final int SHELF = 6;
    private static final long DEP_SMALL = 1_000, DEP_BIG = 10_000, STEP = 10_000, LOAN_STEP = 100_000, WAGE_STEP = 500;

    private int companyId;
    private int row;

    public TileCompanyHQ() { super(STORE_IDS, STORE_NAMES, STORE_SIZES, SHELF); }

    public Company company() { return companyId == 0 || world == null ? null : CompanyData.get(world).byId(companyId); }
    private int page() { return Math.floorMod(ctl.get("page"), PAGES.length); }
    private boolean booksOpen() { return ctl.get("books") == 0; }

    @Override protected boolean mechanical(String id) { return id.equals("books"); }

    @Override
    protected String refuse(int k, EntityPlayer who) {
        if (!STORE_IDS[k].equals("safe")) return null;
        Company c = company();
        if (c == null || c.members.containsKey(who.getUniqueID())) return null;
        return "§cThe safe's combination is known only to " + c.name + " staff";
    }

    @Override
    protected String pressMechanical(String id, EntityPlayer who) {
        Company c = company();
        if (c != null && who != null && !c.can(who.getUniqueID(), r -> r.finance)) return "§cOnly the owner or a director holds the BOOKS key";
        return null;
    }

    // ---- the books on the board ------------------------------------------------------------------------------------
    /** {left text, right text, detail 1, detail 2, detail 3, target} */
    private List<String[]> rows(Company c) {
        List<String[]> out = new ArrayList<>();
        if (c == null) return out;
        CompanyData cd = CompanyData.get(world);
        PassengerData pd = PassengerData.get(world);
        RailwayData rd = RailwayData.get(world);
        switch (page()) {
            case 0 -> {
                for (int i = c.ledger.size() - 1; i >= 0 && out.size() < 60; i--) {
                    Company.Entry e = c.ledger.get(i);
                    long d = e.tick / CompanyData.DAY;
                    out.add(new String[]{"D" + d + " " + e.memo, (e.amount >= 0 ? "+" : "") + Coins.fmt(e.amount), e.memo, e.kind.label + (e.maint ? " (maintenance acct)" : " (operating acct)"),
                            "Day " + d + " · " + Coins.fmt(e.amount), ""});
                }
            }
            case 1 -> {
                for (Map.Entry<UUID, Company.Member> e : c.members.entrySet()) {
                    Company.Member m = e.getValue();
                    out.add(new String[]{m.name + " (" + m.role.label + ")", Coins.fmt(m.wage) + "/d", m.name + " - " + m.role.label,
                            "Wage " + Coins.fmt(m.wage) + " a day · owed " + Coins.fmt(m.owed), "Paid so far " + Coins.fmt(m.paid), "m:" + e.getKey()});
                }
                for (UUID u : c.applicants) {
                    EntityPlayerMP p = CompanyData.online(world, u);
                    out.add(new String[]{"APPLICANT " + (p == null ? u.toString().substring(0, 8) : p.getName()), "HIRE?", "Wants to join " + c.name, "Press HIRE to take them on as a clerk", "", "a:" + u});
                }
            }
            case 2 -> {
                for (RailwayData.Line l : rd.lines().values()) {
                    Company o = cd.ownerOfLine(l.name);
                    PassengerData.LineStat ls = pd.line(l.name);
                    out.add(new String[]{"LINE " + l.name + (c.pso.contains(l.name) ? " PSO" : ""), o == null ? "unowned" : o == c ? "OURS" : o.mark,
                            "Line " + l.name + " - " + l.stations.size() + " stations" + (o == null ? ", unowned" : ", run by " + o.name),
                            ls == null ? "no passengers yet" : String.format(Locale.ROOT, "%d riders today · %d yesterday · %.0f%% satisfied", Math.round(ls.ridersDay), Math.round(ls.ridersYesterday), ls.sat),
                            ls == null ? "" : String.format(Locale.ROOT, "on time %.0f%% · load %.0f%% · fares %s", ls.punct, ls.load, Coins.fmt(ls.revenueDay)), "l:" + l.name});
                }
                for (RailwayData.AutoTrain a : rd.trains().values()) {
                    Company o = cd.ownerOfTrain(a.loco, a.line);
                    String n = a.label.isEmpty() ? "Train " + a.loco.toString().substring(0, 4) : a.label;
                    out.add(new String[]{"TRAIN " + n, o == null ? "unowned" : o == c ? "OURS" : o.mark, n + (a.line.isEmpty() ? "" : " on " + a.line),
                            "Earned $" + a.earned + " · " + a.delivered + " delivered · " + a.trips + " laps",
                            String.format(Locale.ROOT, "%d aboard of %d seats · %d cars", Math.round(pd.aboard(a.loco)), pd.seats(a.loco), pd.cars(a.loco)), "t:" + a.loco});
                }
                for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) {
                    boolean ours = c.stations.contains(e.getKey());
                    PassengerData.Stn s = pd.stn(e.getKey());
                    out.add(new String[]{"STATION " + e.getValue(), ours ? "OURS" : "-", e.getValue() + " (zone " + pd.zoneOf(e.getKey()) + ")",
                            s == null ? "" : String.format(Locale.ROOT, "%.0f%% satisfied · %d waiting · %d boarded today", s.sat, Math.round(s.waitingTotal()), Math.round(s.boardedDay)),
                            s == null ? "" : "catchment " + s.pop + " · facilities " + Math.round(s.fac) + "%", "s:" + e.getKey()});
                }
            }
            case 3 -> {
                int cars = 0;
                for (UUID t : c.trains) cars += pd.cars(t);
                long sp = c.sharePrice(cars);
                for (Map.Entry<UUID, Integer> e : c.shares.entrySet()) {
                    Company.Member m = c.member(e.getKey());
                    String n = m == null ? e.getKey().toString().substring(0, 8) : m.name;
                    out.add(new String[]{n, String.format(Locale.ROOT, "%.1f%%", e.getValue() / 10.0), n + " holds " + e.getValue() + " of 1000 shares",
                            "Worth " + Coins.fmt(sp * e.getValue()) + " at " + Coins.fmt(sp) + " a share", "Book value " + Coins.fmt(c.assets(cars) - c.loan), ""});
                }
            }
            default -> {
                for (Company.Kind k : Company.Kind.values()) {
                    long now = c.month[k.ordinal()], last = c.lastMonth[k.ordinal()];
                    if (now == 0 && last == 0) continue;
                    out.add(new String[]{(k.income ? "+ " : "- ") + k.label, Coins.fmt(now), k.label + (k.income ? " (income)" : " (cost)"),
                            "This month " + Coins.fmt(now), "Last month " + Coins.fmt(last), ""});
                }
                long pl = c.profit(c.month);
                out.add(new String[]{"= RESULT", Coins.fmt(pl), "Operating result this month", "Last month " + Coins.fmt(c.profit(c.lastMonth)), "Month " + (c.monthNo + 1), ""});
            }
        }
        return out;
    }

    @Override
    protected boolean refresh() {
        boolean ch = false;
        Company c = company();
        List<String[]> b = rows(c);
        row = Math.max(0, Math.min(row, Math.max(0, b.size() - 1)));
        int top = Math.max(0, Math.min(row - 2, b.size() - 6));
        for (int r = 1; r <= 6; r++) {
            int i = top + r - 1;
            String[] x = i < b.size() ? b.get(i) : null;
            ch |= StationControls.text(ctl, "r" + r + "a", x == null ? "" : (i == row ? "> " : "  ") + cut(x[0], 30));
            ch |= StationControls.text(ctl, "r" + r + "b", x == null ? "" : cut(x[1], 12));
            ch |= StationControls.put(ctl, "r" + r + "s", x != null && i == row);
        }
        String[] s = b.isEmpty() ? null : b.get(row);
        ch |= StationControls.text(ctl, "title", c == null ? "NO COMPANY - PRESS FOUND" : cut(c.name.toUpperCase(Locale.ROOT), 26) + " (" + c.mark + ")");
        ch |= StationControls.text(ctl, "pg", PAGES[page()] + (b.isEmpty() ? "" : "  " + (row + 1) + "/" + b.size()));
        ch |= StationControls.text(ctl, "m1", c == null ? "NO COMPANY HERE YET" : s == null ? PAGES[page()] + ": NOTHING YET" : cut(s[2], 34));
        ch |= StationControls.text(ctl, "m2", c == null ? "FOUND starts one, you own it" : s == null ? "" : cut(s[3], 34));
        ch |= StationControls.text(ctl, "m3", c == null ? "JOIN asks to work for one" : s == null ? "" : cut(s[4], 34));
        ch |= StationControls.text(ctl, "m4", c == null ? "" : c.applicants.isEmpty() ? "STAFF " + c.members.size() + " · WAGES " + Coins.fmt(c.dailyWages()) + "/D" : c.applicants.size() + " APPLICANT(S) WAITING");
        int cars = 0;
        if (c != null) for (UUID t : c.trains) cars += PassengerData.get(world).cars(t);
        long pl = c == null ? 0 : c.profit(c.month);
        ch |= StationControls.text(ctl, "oper", c == null ? "" : Coins.fmt(c.operating));
        ch |= StationControls.text(ctl, "maint", c == null ? "" : Coins.fmt(c.maintenance));
        ch |= StationControls.text(ctl, "pl", c == null ? "" : Coins.fmt(pl));
        ch |= StationControls.text(ctl, "share", c == null ? "" : "SHARE " + Coins.fmt(c.sharePrice(cars)) + " · REP " + c.reputation + "/100");
        ch |= StationControls.text(ctl, "loanamt", c == null ? "" : c.loan == 0 ? "NO LOAN · " + c.lines.size() + " LINES " + c.trains.size() + " TRAINS" : "LOAN " + Coins.fmt(c.loan) + " AT " + c.loanRatePct + "%/WK");
        ch |= StationControls.put(ctl, "power", term.powered());
        ch |= StationControls.put(ctl, "lowfunds", c != null && c.operating < Math.max(10_000, c.dailyWages() * 2));
        ch |= StationControls.put(ctl, "loanlamp", c != null && c.loan > 0);
        ch |= StationControls.put(ctl, "owed", c != null && c.owedWages() > 0);
        ch |= StationControls.put(ctl, "profit", c != null && pl > 0);
        ch |= StationControls.put(ctl, "loss", c != null && pl < 0);
        ch |= StationControls.put(ctl, "apply", c != null && !c.applicants.isEmpty());
        ch |= StationControls.put(ctl, "repgauge", c == null ? 0 : c.reputation);
        return ch;
    }

    // ---- the desk ----------------------------------------------------------------------------------------------------
    @Override
    protected String press(String id, EntityPlayer who) {
        CompanyData cd = CompanyData.get(world);
        Company c = company();
        switch (id) {
            case "page": ctl.set("page", (page() + 1) % PAGES.length); row = 0; return "§7Board: " + PAGES[page()];
            case "prev": row = Math.max(0, row - 1); return "";
            case "next": row++; return "";
            case "phone":
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
                if (who != null && c != null) for (String s : CompanyData.report(c, false)) who.sendMessage(new TextComponentString(s));
                return c == null ? "§7Nobody answers - this office has no company" : "§7The accounts clerk reads you the books";
            case "found": return found(cd, c, who);
            case "join": return join(c, who);
            default: break;
        }
        if (c == null) return "§7This office has no company yet - press FOUND";
        if (!(who instanceof EntityPlayerMP p)) return "§7The board meets in person";
        if (!com.dogpound.railmap.economy.Perms.has(p, com.dogpound.railmap.economy.Perms.MANAGE)) return com.dogpound.railmap.economy.Perms.no("work a company's books");
        UUID me = p.getUniqueID();
        Company.Member m = c.member(me);
        if (m == null) return "§cYou don't work for " + c.name + " - press JOIN to apply";
        String[] sel = selected(c);
        switch (id) {
            case "dep10": case "dep100": {
                long amt = id.equals("dep10") ? DEP_SMALL : DEP_BIG;
                if (!Coins.charge(p, amt, "Deposit into " + c.name)) return "§cYour bank can't cover " + Coins.fmt(amt);
                issueShares(c, me, amt);
                cd.credit(world, c, Company.Kind.DEPOSIT, amt, "Deposit by " + p.getName());
                return "§a" + Coins.fmt(amt) + " paid into " + c.name + " - your stake is now " + String.format(Locale.ROOT, "%.1f%%", c.shares.getOrDefault(me, 0) / 10.0);
            }
            case "wd100": {
                if (!m.role.finance) return "§cOnly the owner or a director can withdraw";
                if (!booksOpen()) return "§cThe BOOKS key is turned to LOCKED";
                if (!cd.debit(world, c, Company.Kind.WITHDRAW, STEP, "Withdrawn by " + p.getName(), false)) return "§cThe operating account holds only " + Coins.fmt(c.operating);
                if (!Coins.pay(p, STEP, c.name + " withdrawal")) { c.operating += STEP; return "§cThe bank isn't there (Pride Realms not installed)"; }
                return "§e" + Coins.fmt(STEP) + " withdrawn to your bank";
            }
            case "tomaint": {
                if (!m.role.finance) return "§cOnly the owner or a director moves budgets";
                long amt = Math.min(STEP, c.operating);
                if (amt <= 0) return "§cNothing in the operating account";
                c.operating -= amt; c.maintenance += amt;
                Company.Entry e = new Company.Entry();
                e.tick = world.getTotalWorldTime(); e.kind = Company.Kind.MAINT; e.amount = amt; e.memo = "Budget moved to maintenance by " + p.getName(); e.maint = true;
                c.ledger.add(e);
                cd.markDirty();
                return "§a" + Coins.fmt(amt) + " moved into the maintenance budget (now " + Coins.fmt(c.maintenance) + ")";
            }
            case "wages": {
                if (!m.role.finance) return "§cOnly the owner or a director runs payroll";
                int paid = 0;
                for (Map.Entry<UUID, Company.Member> e : c.members.entrySet()) {
                    Company.Member x = e.getValue();
                    if (x.owed <= 0) continue;
                    EntityPlayerMP xp = CompanyData.online(world, e.getKey());
                    if (xp != null && Coins.pay(xp, x.owed, c.name + " wages")) { x.paid += x.owed; x.owed = 0; paid++; }
                }
                cd.markDirty();
                return paid == 0 ? "§7No wages owed to anyone online (" + Coins.fmt(c.owedWages()) + " owed in total)" : "§aPaid " + paid + " people their wages";
            }
            case "dividend": {
                if (!m.role.finance) return "§cOnly the owner or a director declares a dividend";
                long reserve = c.dailyWages() * 3 + 50_000;
                long pot = (c.operating - reserve) / 2;
                if (pot < 1_000) return "§cNo dividend: the company must keep " + Coins.fmt(reserve) + " in reserve";
                cd.debit(world, c, Company.Kind.DIVIDEND, pot, "Dividend declared by " + p.getName(), false);
                for (Map.Entry<UUID, Integer> e : c.shares.entrySet()) {
                    long cut = pot * e.getValue() / 1000;
                    Company.Member x = c.member(e.getKey());
                    if (x != null) x.owed += cut;                                // paid with the wages, when they are online
                }
                return "§aDividend of " + Coins.fmt(pot) + " declared - shareholders are paid with their wages";
            }
            case "loan": {
                if (!m.role.finance) return "§cOnly the owner or a director borrows";
                int cars = 0;
                for (UUID t : c.trains) cars += PassengerData.get(world).cars(t);
                long limit = Math.max(200_000, c.assets(cars) * 30 / 100);
                if (c.loan + LOAN_STEP > limit) return "§cThe bank won't lend past " + Coins.fmt(limit) + " (30% of assets)";
                c.loan += LOAN_STEP;
                cd.credit(world, c, Company.Kind.LOAN, LOAN_STEP, "Loan drawn by " + p.getName() + " at " + c.loanRatePct + "% a week");
                return "§e" + Coins.fmt(LOAN_STEP) + " borrowed - loan now " + Coins.fmt(c.loan);
            }
            case "repay": {
                if (!m.role.finance) return "§cOnly the owner or a director repays loans";
                long amt = Math.min(LOAN_STEP, c.loan);
                if (amt <= 0) return "§7No loan to repay";
                if (!cd.debit(world, c, Company.Kind.REPAY, amt, "Loan repayment", false)) return "§cNot enough in the operating account";
                c.loan -= amt;
                return "§a" + Coins.fmt(amt) + " repaid - loan now " + Coins.fmt(c.loan);
            }
            case "hire": {
                if (!m.role.staff) return "§cOnly the owner or a director hires";
                if (c.applicants.isEmpty()) return "§7Nobody has applied";
                UUID u = sel != null && sel[5].startsWith("a:") ? UUID.fromString(sel[5].substring(2)) : c.applicants.get(0);
                c.applicants.remove(u);
                if (cd.of(u) != null) return "§7They already work for " + cd.of(u).name;
                EntityPlayerMP np = CompanyData.online(world, u);
                Company.Member nm = new Company.Member();
                nm.name = np == null ? u.toString().substring(0, 8) : np.getName();
                nm.role = Company.Role.CLERK; nm.wage = 1_000;
                c.members.put(u, nm);
                cd.markDirty();
                if (np != null) np.sendMessage(new TextComponentString("§a💼 You're hired! Welcome to " + c.name + " (clerk, " + Coins.fmt(nm.wage) + " a day)"));
                return "§a" + nm.name + " hired as a clerk";
            }
            case "role": case "wageup": case "wagedn": case "fire": {
                if (!m.role.staff) return "§cOnly the owner or a director manages staff";
                if (sel == null || !sel[5].startsWith("m:")) return "§7Pick someone on the STAFF board first";
                UUID u = UUID.fromString(sel[5].substring(2));
                Company.Member x = c.member(u);
                if (x == null) return "§7They have left";
                if (x.role == Company.Role.OWNER && !id.startsWith("wage")) return "§cThe owner can't be re-roled or fired";
                switch (id) {
                    case "role" -> {
                        Company.Role[] rs = {Company.Role.DIRECTOR, Company.Role.DISPATCHER, Company.Role.DRIVER, Company.Role.CLERK};
                        int i = Arrays.asList(rs).indexOf(x.role);
                        x.role = rs[(i + 1) % rs.length];
                    }
                    case "wageup" -> x.wage = Math.min(20_000, x.wage + WAGE_STEP);
                    case "wagedn" -> x.wage = Math.max(0, x.wage - WAGE_STEP);
                    default -> {
                        c.members.remove(u);
                        Integer sh = c.shares.remove(u);
                        if (sh != null) c.shares.merge(c.owner, sh, Integer::sum);   // the owner buys out their stake
                        cd.markDirty();
                        return "§e" + x.name + " has left " + c.name + (x.owed > 0 ? " (still owed " + Coins.fmt(x.owed) + ")" : "");
                    }
                }
                cd.markDirty();
                return "§7" + x.name + ": " + x.role.label + ", " + Coins.fmt(x.wage) + " a day";
            }
            case "claim": {
                if (!m.role.operations) return "§cOnly the owner, directors and dispatchers assign the fleet";
                if (sel == null || sel[5].length() < 3 || "lts".indexOf(sel[5].charAt(0)) < 0) return "§7Pick a line, train or station on the FLEET board";
                String t = sel[5].substring(2);
                switch (sel[5].charAt(0)) {
                    case 'l' -> {
                        Company o = cd.ownerOfLine(t);
                        if (o != null && o != c) return "§cThat line is run by " + o.name;
                        if (!c.lines.remove(t)) c.lines.add(t); else c.pso.remove(t);
                        cd.markDirty();
                        return c.lines.contains(t) ? "§aLine " + t + " is now run by " + c.name : "§eLine " + t + " released";
                    }
                    case 't' -> {
                        UUID u = UUID.fromString(t);
                        for (Company o : cd.all()) if (o != c && o.trains.contains(u)) return "§cThat train belongs to " + o.name;
                        if (!c.trains.remove(u)) c.trains.add(u);
                        cd.markDirty();
                        return c.trains.contains(u) ? "§aTrain added to " + c.name + "'s fleet" : "§eTrain released";
                    }
                    case 's' -> {
                        long k = Long.parseLong(t);
                        for (Company o : cd.all()) if (o != c && o.stations.contains(k)) return "§cThat station belongs to " + o.name;
                        if (!c.stations.remove(k)) c.stations.add(k);
                        cd.markDirty();
                        return c.stations.contains(k) ? "§aStation taken over by " + c.name + " (upkeep " + Coins.fmt(CompanyData.MAINT_STATION) + " a day)" : "§eStation released";
                    }
                    default -> { return "§7Pick a line, train or station on the FLEET board"; }
                }
            }
            case "pso": {
                if (!m.role.finance) return "§cOnly the owner or a director signs public-service contracts";
                if (sel == null || !sel[5].startsWith("l:")) return "§7Pick one of your lines on the FLEET board";
                String l = sel[5].substring(2);
                if (!c.lines.contains(l)) return "§cOnly lines " + c.name + " runs can take a PSO contract";
                if (!c.pso.remove(l)) c.pso.add(l);
                cd.markDirty();
                return c.pso.contains(l) ? "§aPublic-service contract signed for " + l + ": the county pays per rider, each day" : "§ePSO contract for " + l + " ended";
            }
            case "report":
                for (String s : CompanyData.report(c, c.monthNo > 0)) p.sendMessage(new TextComponentString(s));
                return "";
            default: return null;
        }
    }

    private String[] selected(Company c) { List<String[]> b = rows(c); return b.isEmpty() ? null : b.get(Math.min(row, b.size() - 1)); }

    private String found(CompanyData cd, Company c, EntityPlayer who) {
        if (!(who instanceof EntityPlayerMP p)) return "§7A company is founded in person";
        if (!com.dogpound.railmap.economy.Perms.has(p, com.dogpound.railmap.economy.Perms.FOUND)) return com.dogpound.railmap.economy.Perms.no("found a railway company");
        if (c != null) return "§7This office already belongs to " + c.name;
        Company mine = cd.of(p.getUniqueID());
        if (mine != null) { companyId = mine.id; markDirty(); return "§aThis office is now " + mine.name + "'s headquarters"; }
        Company n = cd.found(world, p, p.getName() + "'s Railway");
        companyId = n.id;
        markDirty();
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.stamp"), SoundCategory.BLOCKS, 0.8f, 1.0f);
        return "§a§l" + n.name + " (" + n.mark + ") founded! §r§7Rename it: /irextras company name <new name>";
    }

    private String join(Company c, EntityPlayer who) {
        if (who == null) return "§7Apply in person";
        if (c == null) return "§7No company here to join";
        if (c.members.containsKey(who.getUniqueID())) return "§7You already work here";
        if (c.applicants.contains(who.getUniqueID())) return "§7Your application is on the director's desk";
        c.applicants.add(who.getUniqueID());
        CompanyData.get(world).markDirty();
        return "§aApplication sent to " + c.name + " - a director presses HIRE";
    }

    /** a deposit buys equity: everyone's stake is revalued at book value, the depositor's grows by what they paid in */
    private void issueShares(Company c, UUID who, long amt) {
        int cars = 0;
        for (UUID t : c.trains) cars += PassengerData.get(world).cars(t);
        double value = Math.max(1, c.assets(cars) - c.loan);
        Map<UUID, Double> stake = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> e : c.shares.entrySet()) stake.put(e.getKey(), value * e.getValue() / 1000.0);
        stake.merge(who, (double) amt, Double::sum);
        double total = value + amt;
        c.shares.clear();
        int given = 0;
        for (Map.Entry<UUID, Double> e : stake.entrySet()) { int n = (int) Math.floor(e.getValue() / total * 1000); c.shares.put(e.getKey(), n); given += n; }
        if (c.owner != null) c.shares.merge(c.owner, 1000 - given, Integer::sum);   // rounding goes to the owner
    }

    // ---- console --------------------------------------------------------------------------------------------------
    @Override public String wiredName() { return "Company HQ"; }
    @Override public String gearState() { Company c = company(); return c == null ? "no company" : c.name + " · " + Coins.fmt(c.operating) + " · rep " + c.reputation; }

    @Override protected void writeExtra(NBTTagCompound t) { t.setInteger("company", companyId); t.setInteger("row", row); }
    @Override protected void readExtra(NBTTagCompound t) { companyId = t.getInteger("company"); row = t.getInteger("row"); }
}
