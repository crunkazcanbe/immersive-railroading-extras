package com.dogpound.railmap.freight6;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** the real-calendar holiday seasons (batch 6 #30): Christmas 1 Dec - 6 Jan, Halloween 15 Oct - 2 Nov */
public final class Season {
    private Season() {}

    public static final int NONE = 0, CHRISTMAS = 1, HALLOWEEN = 2;
    public static final String[] NAMES = {"NONE", "CHRISTMAS", "HALLOWEEN"};

    public static int of(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if (m == 12 || m == 1 && day <= 6) return CHRISTMAS;
        if (m == 10 && day >= 15 || m == 11 && day <= 2) return HALLOWEEN;
        return NONE;
    }

    /** today's season, after the config (holidays off = NONE, forceSeason overrides the date) */
    public static int now() {
        if (!F6Config.holidays) return NONE;
        switch (F6Config.forceSeason == null ? "auto" : F6Config.forceSeason.trim().toLowerCase(Locale.ROOT)) {
            case "christmas": return CHRISTMAS;
            case "halloween": return HALLOWEEN;
            case "none": return NONE;
            default: return of(LocalDate.now());
        }
    }

    /** days until the next 25 Dec / 31 Oct */
    public static long daysTo(LocalDate d, int season) {
        LocalDate t = season == HALLOWEEN ? LocalDate.of(d.getYear(), 10, 31) : LocalDate.of(d.getYear(), 12, 25);
        if (t.isBefore(d)) t = t.plusYears(1);
        return ChronoUnit.DAYS.between(d, t);
    }
}
