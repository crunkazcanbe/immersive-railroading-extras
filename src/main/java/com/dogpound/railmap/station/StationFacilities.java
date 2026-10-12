package com.dogpound.railmap.station;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.depot.DepotConfig;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Station accessibility and facilities (her ChatGPT list #81 + #85). Once a minute every named station is audited:
 * the facilities within 48 blocks (lifts, escalators, toilets, help points, vending, kiosks, lamps, heaters - working
 * or not) and the tactile paving within 16 blocks of the stop. Accessibility rating (0-100, grade A-E): step-free
 * access 40 (every lift working, or LEVEL ACCESS declared on the Station Facilities Panel), tactile paving 20 (12
 * blocks for the lot), an accessible toilet 15, a help point 15, platform-gap warnings 10. Passengers who need
 * step-free access (config, 12 %) won't travel to or from an audited station that isn't step-free. Once a day each
 * station's operators pay its facilities' running costs and get its concession / vending income.
 */
public final class StationFacilities {
    private StationFacilities() {}

    public static final class Audit {
        public long station;
        public String name = "";
        public boolean stepFree, declaredLevel, audited;
        public int rating, lifts, liftsOk, tactile, toilets, toiletsOk, help, helpOk, gap, total, working, points;
        public long cost, income, costYesterday, incomeYesterday;
        public final List<String> rows = new ArrayList<>();   // "kind|working|cost"
        public String grade() { return rating >= 85 ? "A" : rating >= 70 ? "B" : rating >= 50 ? "C" : rating >= 30 ? "D" : "E"; }
    }

    private static final Map<Integer, Map<Long, Audit>> AUDITS = new HashMap<>();
    private static final Map<Integer, Long> LAST_DAY = new HashMap<>();

    public static Map<Long, Audit> audits(World w) { return AUDITS.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()); }
    public static Audit peek(World w, long station) { return audits(w).get(station); }

    public static void tick(World w, long now) {
        if (now % 1200 == 600) for (long k : new ArrayList<>(StationData.get(w).names().keySet())) if (w.isBlockLoaded(BlockPos.fromLong(k))) audit(w, k);
        long day = now / 24000;
        Long last = LAST_DAY.get(w.provider.getDimension());
        if (last == null) { LAST_DAY.put(w.provider.getDimension(), day); return; }
        if (day != last) { LAST_DAY.put(w.provider.getDimension(), day); daily(w); }
    }

    /** the station a facility at p belongs to (the nearest named stop within 48 blocks), or null */
    public static Long stationOf(World w, BlockPos p) {
        Long best = null;
        double bd = 48 * 48;
        for (long k : StationData.get(w).names().keySet()) { double d = BlockPos.fromLong(k).distanceSq(p); if (d < bd) { bd = d; best = k; } }
        return best;
    }

    public static Audit audit(World w, long key) {
        Audit a = audits(w).computeIfAbsent(key, k -> new Audit());
        a.station = key;
        a.name = StationData.get(w).names().getOrDefault(key, "?");
        BlockPos at = BlockPos.fromLong(key);
        a.lifts = a.liftsOk = a.toilets = a.toiletsOk = a.help = a.helpOk = a.gap = a.total = a.working = a.points = 0;
        a.cost = 0;
        a.rows.clear();
        a.declaredLevel = false;
        boolean panel = false, anyStepFree = false;
        for (TileEntity t : w.loadedTileEntityList) {
            if (t.isInvalid() || t.getPos().distanceSq(at) > 48 * 48) continue;
            if (t instanceof TileFacilitiesPanel fp) { panel = true; a.declaredLevel |= fp.levelAccess(); }
            if (!(t instanceof IStationFacility f)) continue;
            Long mine = stationOf(w, t.getPos());
            if (mine == null || mine != key) continue;
            boolean ok = f.facilityWorking();
            a.total++;
            if (ok) { a.working++; a.points += f.facilityPoints(); }
            a.cost += f.dailyCost();
            a.rows.add(f.facilityKind() + "|" + (ok ? "OK" : "OUT OF SERVICE") + "|" + f.dailyCost());
            if (t instanceof TileStationLift) { a.lifts++; if (ok) a.liftsOk++; }
            if (t instanceof TileStationToilet) { a.toilets++; if (ok) a.toiletsOk++; }
            if (t instanceof TileHelpPoint hp) { a.help++; if (ok) { a.helpOk++; if (hp.gapWarning()) a.gap++; } }
            if (ok && f.stepFree()) anyStepFree = true;
        }
        a.tactile = tactile(w, at);
        a.audited = panel || a.lifts > 0;
        a.stepFree = a.lifts > 0 ? a.liftsOk == a.lifts && anyStepFree : a.declaredLevel;
        a.rating = (a.stepFree ? 40 : 0) + Math.min(20, a.tactile * 20 / 12) + (a.toiletsOk > 0 ? 15 : 0) + (a.helpOk > 0 ? 15 : 0) + (a.gap > 0 ? 10 : 0);
        AlarmManager am = AlarmManager.get(w);
        if (a.lifts > a.liftsOk) am.raise(w, "lift:" + key, AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, a.name + ": " + (a.lifts - a.liftsOk) + " lift(s) out of service - no step-free access", at, true);
        else am.clear("lift:" + key);
        return a;
    }

    private static int tactile(World w, BlockPos at) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(at.add(-16, -4, -16), at.add(16, 4, 16)))
            if (w.isBlockLoaded(p) && w.getBlockState(p).getBlock() instanceof BlockTactile) n++;
        return n;
    }

    /** demand weight for a trip between two stations: people who need step-free access won't use a station that isn't */
    public static double accessWeight(World w, long from, long to) {
        Audit a = peek(w, from), b = peek(w, to);
        boolean bad = a != null && a.audited && !a.stepFree || b != null && b.audited && !b.stepFree;
        return bad ? 1 - DepotConfig.stepFreeShare : 1;
    }

    /** satisfaction points of a facility (PassengerData counts these with the boards and machines) */
    public static int points(TileEntity t) { return t instanceof IStationFacility f && f.facilityWorking() ? f.facilityPoints() : 0; }

    /** once a day: each station's operators pay its facilities' running costs and get their income */
    private static void daily(World w) {
        Map<Long, Audit> map = audits(w);
        Map<Long, Long> income = new HashMap<>();
        for (TileEntity t : w.loadedTileEntityList) {
            if (!(t instanceof IStationFacility f)) continue;
            Long k = stationOf(w, t.getPos());
            long in = f.takeIncome();
            if (k != null) income.merge(k, in, Long::sum);
        }
        for (Long k : StationData.get(w).names().keySet()) {
            Audit a = map.containsKey(k) ? audit(w, k) : null;
            if (a == null) continue;
            a.costYesterday = a.cost;
            a.incomeYesterday = income.getOrDefault(k, 0L);
            if (!DepotConfig.facilityCosts || !com.dogpound.railmap.RailMapConfig.railwayEconomy) continue;
            CompanyData cd = CompanyData.get(w);
            List<Company> ops = cd.operatorsAt(w, k);
            if (ops.isEmpty()) continue;
            if (a.incomeYesterday > 0) cd.creditShared(w, ops, Company.Kind.TRADING, a.incomeYesterday, "Concessions + vending at " + a.name);
            long each = a.cost / ops.size();
            for (Company c : ops) if (each > 0 && !cd.debit(w, c, Company.Kind.MAINT, each, "Station facilities at " + a.name, true)) c.reputation = Math.max(0, c.reputation - 1);
        }
    }
}
