package com.dogpound.railmap.crew;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Revenue protection (batch 5 #9): what a ticket inspector finds. Passengers the numbers say are aboard / waiting are
 * checked at checksPerMinute; the share without a ticket is the passenger model's own evasion share (gates or not),
 * so the inspectors find what PassengerData lost. A dodger pays a penalty fare (credited to the train / line operator
 * as fares) or is warned. Players are checked against the real ItemTicket / pass rules for the journey they are on;
 * no ticket = a warning or a gentle penalty fare in Pride coins - never anything hostile. Every case is logged (case
 * file, Recorder, CCTV bookmark on the nearest camera, a QuestEvents hook), and a station or line an inspector worked
 * has less fare evasion for a day (PassengerData asks {@link #deterrent}).
 */
public final class Revenue {
    private Revenue() {}

    public static final int CHECKS = 0, CAUGHT = 1, WARNED = 2, CENTS = 3, PLAYERS = 4;
    private static final Map<UUID, Long> PLAYER_SEEN = new HashMap<>();
    private static final Random RND = new Random();

    /** PassengerData's fare-evasion share is multiplied by this at a station (1 = no inspector lately) */
    public static double deterrent(World w, long station) {
        try {
            Long t = CrewData.get(w).patrolled.get(station);
            return t != null && w.getTotalWorldTime() - t < 24000 ? CrewConfig.deterrence : 1.0;
        } catch (RuntimeException e) { return 1.0; }
    }

    static void worked(World w, CrewData c, long station, String line) {
        long now = w.getTotalWorldTime();
        if (station != 0) c.patrolled.put(station, now);
        if (line != null && !line.isEmpty()) {
            c.linePatrolled.put(line, now);
            RailwayData.Line l = RailwayData.get(w).line(line);
            if (l != null) for (long st : l.stations) c.patrolled.put(st, now);
        }
    }

    /**
     * the simulated passengers an inspector checks this half-second (on a train: the people aboard; on a platform:
     * the people waiting). Returns how many had no ticket.
     */
    static int checkNumbers(World w, CrewData c, CrewData.Staff s, CrewTicker.Live L, double pool, int gates, UUID loco, String line, String where, double dt) {
        if (pool < 0.5) return 0;
        L.checkAcc += CrewConfig.checksPerMinute / 60.0 * dt;
        int n = (int) L.checkAcc;
        if (n <= 0) return 0;
        L.checkAcc -= n;
        n = (int) Math.min(n, Math.max(0, pool - L.checked));
        if (n <= 0) return 0;
        L.checked += n;
        double p = Rules.dodgerShare(gates, CrewConfig.dodgerScale);
        int caught = 0;
        for (int i = 0; i < n; i++) if (RND.nextDouble() < p) caught++;
        s.checks += n;
        c.count(CHECKS, n);
        for (int i = 0; i < caught; i++) {
            boolean pays = c.mode() > 0 && RND.nextInt(4) != 0;            // most pay the penalty fare there and then; the rest are warned and buy a ticket
            long cents = pays ? CrewConfig.penaltyCents : 0;
            if (pays && cents > 0) {
                credit(w, loco, line, L.station, where, cents);
                s.fined += cents;
                c.count(CENTS, cents);
            } else { s.warned++; c.count(WARNED, 1); }
            s.caught++;
            c.count(CAUGHT, 1);
            CrewData.Case k = c.open(w, "Passenger", null, where, s.name, pays ? "PAID" : "WARNED", cents, "");
            k.cam = cctv(w, L.at, loco, k.id);
        }
        if (caught > 0) {
            try {
                PassengerData.Stn st = PassengerData.get(w).stn(L.station);
                if (st != null) st.evadersDay = Math.max(0, st.evadersDay - caught);
            } catch (RuntimeException ignored) { }
            QuestEvents.fire(w, null, L.at == null ? null : new BlockPos(L.at[0], L.at[1], L.at[2]), "fare_dodgers_caught", "inspector", s.name, "count", caught, "where", where);
        }
        worked(w, c, L.station, line);
        return caught;
    }

    static void credit(World w, UUID loco, String line, long station, String where, long cents) {
        try {
            CompanyData cd = CompanyData.get(w);
            Company op = loco != null ? cd.ownerOfTrain(loco, line) : cd.ownerOfLine(line);
            if (op == null && station != 0) {
                List<Company> ops = cd.operatorsAt(w, station);
                if (!ops.isEmpty()) op = ops.get(0);
            }
            if (op != null) cd.credit(w, op, Company.Kind.FARES, cents, "Penalty fare (" + where + ")");
        } catch (RuntimeException | LinkageError ignored) { }
    }

    /** bookmark the nearest live CCTV camera's recording (the train's own camera first): "CAM n" or "" */
    static String cctv(World w, double[] at, UUID loco, int caseNo) {
        if (caseNo == 0) return "";
        try {
            com.dogpound.railmap.cctv.CctvData cd = com.dogpound.railmap.cctv.CctvData.get(w);
            com.dogpound.railmap.cctv.CctvData.Cam best = loco == null ? null : cd.byTrain(loco.toString());
            if ((best == null || !best.streams()) && at != null) {
                best = null;
                double bd = 32 * 32;
                for (com.dogpound.railmap.cctv.CctvData.Cam c : cd.cams.values()) {
                    if (c.dim != w.provider.getDimension() || !c.streams()) continue;
                    double d = (c.x - at[0]) * (c.x - at[0]) + (c.y - at[1]) * (c.y - at[1]) + (c.z - at[2]) * (c.z - at[2]);
                    if (d < bd) { bd = d; best = c; }
                }
            }
            if (best == null || !best.streams()) return "";
            cd.mark(best.no, w.getTotalWorldTime(), "FARE CASE #" + caseNo);
            return "CAM " + best.no;
        } catch (RuntimeException | LinkageError e) { return ""; }
    }

    /** cameras streaming within `r` blocks of a spot (the Revenue Protection Office's CCTV link) */
    public static int camerasNear(World w, BlockPos p, double r) {
        int n = 0;
        try {
            for (com.dogpound.railmap.cctv.CctvData.Cam c : com.dogpound.railmap.cctv.CctvData.get(w).cams.values())
                if (c.dim == w.provider.getDimension() && c.streams() && p.distanceSq(c.x, c.y, c.z) <= r * r) n++;
        } catch (RuntimeException | LinkageError ignored) { }
        return n;
    }

    /**
     * check a player's ticket for this journey. station = where they are / the train last stood; line = the train's
     * line ("" on a platform: any line through the station). Returns true when a check happened.
     */
    static boolean checkPlayer(World w, CrewData c, CrewData.Staff s, CrewTicker.Live L, EntityPlayer p, long station, String line, UUID loco) {
        if (!CrewConfig.checkPlayers || p.isSpectator()) return false;
        long now = w.getTotalWorldTime();
        Long last = PLAYER_SEEN.get(p.getUniqueID());
        if (last != null && now - last < 2400) return false;                   // once per journey-ish (2 game hours)
        PLAYER_SEEN.put(p.getUniqueID(), now);
        PLAYER_SEEN.values().removeIf(t -> now - t > 24000);
        Set<Long> stations = new HashSet<>();
        RailwayData rd = RailwayData.get(w);
        if (!line.isEmpty() && rd.line(line) != null) stations.addAll(rd.line(line).stations);
        else for (RailwayData.Line l : rd.lines().values()) if (l.stations.contains(station)) stations.addAll(l.stations);
        int zone = PassengerData.get(w).zoneOf(station);
        List<ItemStack> inv = new ArrayList<>(p.inventory.mainInventory);
        inv.addAll(p.inventory.offHandInventory);
        List<String> why = new ArrayList<>();
        ItemStack ok = Rules.bestTicket(inv, now, w.getWorldTime() / 24000, station, stations, zone, why);
        String where = StationData.get(w).names().getOrDefault(station, "the line") + (line.isEmpty() ? "" : " (" + line + ")");
        String tag = "§b[Ticket inspector " + s.name + "]§r ";
        s.checks++;
        c.count(CHECKS, 1);
        c.count(PLAYERS, 1);
        L.target = p.getUniqueID();
        L.action = CrewTicker.A_CHECK;
        L.actionUntil = now + 60;
        worked(w, c, station, line);
        if (ok != null) {
            ok.getTagCompound().setInteger("checks", ok.getTagCompound().getInteger("checks") + 1);
            p.sendMessage(new TextComponentString(tag + "Tickets please... §a" + ok.getDisplayName() + " - thank you, have a good journey!"));
            QuestEvents.fire(w, p, p.getPosition(), "ticket_checked", "valid", true, "inspector", s.name, "where", where);
            return true;
        }
        String reason = why.isEmpty() ? "no ticket" : why.get(0);
        boolean warnedBefore = c.warnedPlayers.contains(p.getUniqueID());
        long cents = CrewConfig.penaltyCents;
        int verdict = Rules.decide(c.mode(), warnedBefore, cents > 0 && p instanceof EntityPlayerMP mp && (mp.capabilities.isCreativeMode || Coins.balance(mp) >= cents));
        String state;
        if (verdict == 1 && p instanceof EntityPlayerMP mp && Coins.charge(mp, cents, "Penalty fare - " + where)) {
            credit(w, loco, line, station, where, cents);
            s.fined += cents;
            c.count(CENTS, cents);
            state = "PAID";
            p.sendMessage(new TextComponentString(tag + "Tickets please... §e" + reason + ". That's a penalty fare of " + Coins.fmt(cents) + " - paid, here's your receipt. Please buy before you board next time!"));
        } else {
            c.warnedPlayers.add(p.getUniqueID());
            c.count(WARNED, 1);
            s.warned++;
            state = "WARNED";
            p.sendMessage(new TextComponentString(tag + "Tickets please... §e" + reason + ". §fJust a friendly warning this time - please buy a ticket at the machine or a Travel Centre."));
        }
        s.caught++;
        c.count(CAUGHT, 1);
        CrewData.Case k = c.open(w, p.getName(), p.getUniqueID(), where, s.name, state, state.equals("PAID") ? cents : 0, "");
        k.cam = cctv(w, new double[]{p.posX, p.posY, p.posZ}, loco, k.id);
        Recorder.event(w, "Revenue protection: " + p.getName() + " - " + reason + " at " + where + " - " + state + (k.cam.isEmpty() ? "" : " (" + k.cam + " bookmarked)") + " - case #" + k.id);
        QuestEvents.fire(w, p, p.getPosition(), "fare_penalty", "state", state, "cents", state.equals("PAID") ? cents : 0, "inspector", s.name, "where", where, "case", k.id);
        return true;
    }

    /** a fine waived at the Revenue Protection Office: refund the player and the operator gives the fare back */
    public static String waive(World w, CrewData c, CrewData.Case k, String by) {
        if (k == null) return "§7No case selected";
        if (k.state.equals("WAIVED")) return "§7Case #" + k.id + " is already waived";
        if (k.state.equals("PAID") && k.cents > 0 && k.player != null) {
            EntityPlayer p = w.getPlayerEntityByUUID(k.player);
            if (!(p instanceof EntityPlayerMP mp) || !Coins.pay(mp, k.cents, "Penalty fare refund - case #" + k.id)) return "§c" + k.who + " must be online for the refund";
            c.count(CENTS, -k.cents);
            p.sendMessage(new TextComponentString("§b[Revenue protection]§r Your penalty fare (case #" + k.id + ") was waived - " + Coins.fmt(k.cents) + " refunded. Sorry for the trouble!"));
        }
        k.state = "WAIVED";
        c.markDirty();
        Recorder.event(w, "Revenue protection: case #" + k.id + " (" + k.who + ") waived by " + by);
        return "§aCase #" + k.id + " waived";
    }

    public static void forget() { PLAYER_SEEN.clear(); }
}
