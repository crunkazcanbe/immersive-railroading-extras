package com.dogpound.railmap.crew;

import com.dogpound.railmap.item.ItemTicket;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** the crew and revenue rules as plain functions (so `/irextras crew selftest` can check them) */
public final class Rules {
    private Rules() {}

    /** is game hour h inside a shift starting at `start` lasting `hours` (wraps midnight); start -1 = no diagram */
    public static boolean inShift(int h, int start, int hours) {
        if (start < 0) return false;
        return Math.floorMod(h - start, 24) < Math.min(24, Math.max(1, hours));
    }

    /** enough rest since the last sign-off (game hours = 1000 ticks) */
    public static boolean rested(long now, long lastOff, int restHours) { return lastOff <= 0 || (now - lastOff) >= restHours * 1000L; }

    /** 0 fresh .. 1 exhausted (can exceed 1): share of max duty worked, plus missed rest, less a cup of tea */
    public static double fatigue(double dutyHours, int dutyMax, double restShortHours, double tea) {
        double f = dutyHours / Math.max(1, dutyMax) + Math.max(0, restShortHours) / 12.0 - Math.max(0, Math.min(1, tea)) * 0.2;
        return Math.max(0, Math.min(1.5, f));
    }

    /** the passenger model's evasion share (PassengerData: 3 % gated, 15 % ungated) scaled for inspections */
    public static double dodgerShare(int gates, double scale) { return Math.min(0.95, (gates > 0 ? 0.03 : 0.15) * scale); }

    /** 0 = a warning, 1 = a penalty fare */
    public static int decide(int mode, boolean warnedBefore, boolean canPay) {
        if (!canPay || mode <= 0) return 0;
        if (mode == 1 && !warnedBefore) return 0;
        return 1;
    }

    /**
     * Is this ticket / pass good for the journey being made now? null = yes, else why not. A ticket must be for a
     * station on the train's line (or this station), and still valid - unpunched, or punched today (the gates punch
     * it on the way in, so a punched ticket from today is a ticket in use). A pass must cover this zone and be in date.
     */
    public static String ticketFor(ItemStack s, long now, long today, long station, Collection<Long> line, int zone) {
        if (s == null || s.isEmpty() || !(s.getItem() instanceof ItemTicket)) return "no ticket";
        if (ItemTicket.isPass(s)) return ItemTicket.passRefused(s, now, zone);
        if (!ItemTicket.valid(s)) return "a blank ticket";
        long from = ItemTicket.from(s), to = ItemTicket.to(s);
        boolean onRoute = from == station || to == station || line != null && (line.contains(from) || line.contains(to));
        if (!onRoute) return "not valid on this route (" + ItemTicket.fromName(s) + " - " + ItemTicket.toName(s) + ")";
        if (ItemTicket.isSpent(s)) {
            long day = s.getTagCompound().getLong("day");
            if (today - day > 0) return "punched on an earlier day";
        }
        return null;
    }

    /** the first stack that is good for the journey, or null; `why` gets the reasons the others were refused */
    public static ItemStack bestTicket(List<ItemStack> inv, long now, long today, long station, Collection<Long> line, int zone, List<String> why) {
        for (ItemStack s : inv) {
            if (!(s.getItem() instanceof ItemTicket)) continue;
            String no = ticketFor(s, now, today, station, line, zone);
            if (no == null) return s;
            if (why != null) why.add(no);
        }
        return null;
    }

    /** self-test: returns the failures (empty = all good) */
    public static List<String> selfTest(net.minecraft.item.Item ticket) {
        List<String> f = new ArrayList<>();
        chk(f, inShift(6, 6, 8) && inShift(13, 6, 8) && !inShift(14, 6, 8), "day shift 06-14");
        chk(f, inShift(23, 22, 8) && inShift(5, 22, 8) && !inShift(6, 22, 8) && !inShift(21, 22, 8), "night shift wraps midnight");
        chk(f, !inShift(10, -1, 8), "no diagram = never auto");
        chk(f, rested(20000, 0, 11) && !rested(20000, 15000, 11) && rested(30000, 15000, 11), "rest rule");
        chk(f, fatigue(0, 12, 0, 0) == 0 && fatigue(12, 12, 0, 0) == 1 && fatigue(6, 12, 6, 0) == 1 && fatigue(6, 12, 0, 1) < 0.5, "fatigue curve");
        chk(f, Math.abs(dodgerShare(2, 1) - 0.03) < 1e-9 && Math.abs(dodgerShare(0, 1) - 0.15) < 1e-9 && dodgerShare(0, 100) <= 0.95, "dodger share follows the pax model");
        chk(f, decide(0, true, true) == 0 && decide(1, false, true) == 0 && decide(1, true, true) == 1 && decide(2, false, true) == 1 && decide(2, true, false) == 0, "penalty: warn first, never charge who can't pay");
        if (ticket != null) {
            List<Long> line = java.util.Arrays.asList(100L, 200L, 300L);
            ItemStack t = ItemTicket.make(ticket, 100, "A", 300, "C", false, 1, 5);
            chk(f, ticketFor(t, 0, 5, 200, line, 1) == null, "ticket on the line is valid");
            chk(f, ticketFor(ItemTicket.make(ticket, 900, "X", 901, "Y", false, 2, 5), 0, 5, 200, line, 1) != null, "ticket for another route refused");
            ItemTicket.punch(t);
            chk(f, ticketFor(t, 0, 5, 200, line, 1) == null, "punched today = in use");
            chk(f, ticketFor(t, 0, 7, 200, line, 1) != null, "punched days ago refused");
            ItemStack pass = ItemTicket.makePass(ticket, 0, 1, 2, 1000, 0, 3, 500, "A");
            chk(f, ticketFor(pass, 10, 5, 200, line, 2) == null && ticketFor(pass, 10, 5, 200, line, 4) != null && ticketFor(pass, 2000, 5, 200, line, 1) != null, "pass zones + expiry");
            chk(f, ticketFor(ItemStack.EMPTY, 0, 5, 200, line, 1) != null, "empty hand refused");
        }
        return f;
    }

    private static void chk(List<String> f, boolean ok, String what) { if (!ok) f.add(what); }
}
