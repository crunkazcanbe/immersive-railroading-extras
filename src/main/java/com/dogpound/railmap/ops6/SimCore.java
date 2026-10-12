package com.dogpound.railmap.ops6;

import com.dogpound.railmap.sim.EngineSim;

import java.util.*;

/**
 * The training simulator's train and route (batch 6 E #25): one train on a straight virtual line, stepped at 20 Hz.
 * Physics in the same terms as the live sim package: notch response by engine family (EngineSim.Family: a switcher
 * answers at once, turbocharged road / heavy units lag while the boost builds), tractive effort = power / speed capped
 * by the starting effort and by adhesion (dry 0.30, wet 0.20, leaves 0.08 - over it the wheels slip), Davis running
 * resistance, gradient, brake cylinder build-up / release time constants, wheel-slide protection, emergency brake.
 * Safety systems: AWS (a horn at each caution / stop signal's magnet 180 m out, acknowledge in 2.7 s or the brakes go
 * on), DSD vigilance (press every 60 s - 30 s on the night run - while moving, 7 s of warning), TPWS overspeed (15 km/h
 * over the limit) and SPAD. Scoring starts at 100; a SPAD, an overrun of 20 m or a stop short of 50 m fails.
 */
public final class SimCore {
    public static final String[] NAMES = {"STATION STOP", "SIGNALS + AWS", "LOW ADHESION", "HEAVY FREIGHT", "NIGHT RUN"};
    public static final String[] ASPECT = {"RED", "YELLOW", "DOUBLE YELLOW", "GREEN"};
    /** traction for the driver record (Competency.TRACTION index) */
    public static final int[] TRACTION = {0, 1, 0, 0, 1};

    public static final class Sig { public double at; public int aspect; public double clearAfterStop = -1, approachKmh = -1; boolean awsDone, passed; long stoodS; }
    public static final class Lim { public double from, to, kmh; public boolean tsr; Lim(double f, double t, double k, boolean tsr) { from = f; to = t; this.kmh = k; this.tsr = tsr; } }

    public final int scn;
    public final String name;
    public final double massT, powerKw, teMaxKn, len, brakeMax, lineKmh, stopAt, endAt, targetS;
    public final EngineSim.Family family;
    public final List<Sig> sigs = new ArrayList<>();
    public final List<Lim> lims = new ArrayList<>();
    public double leavesFrom = -1, leavesTo = -1, gradFrom = -1, gradTo = -1, gradPermil;
    public boolean wet, night;
    public int vigS = 60;

    // live state
    public double pos, v, notchEff, bc, t;
    public int notch, brake;           // brake 0..7, 8 = EMERGENCY
    public boolean eb, ebLatched, slip, slide, awsHorn, vigWarn, finished, passed, stopped;
    public double awsT, vigT, ebWhy;
    public String ebReason = "", result = "";
    public double score = 100;
    public int spads, awsMiss, vigMiss, slips, slides, tpws;
    public double stopErr = Double.NaN;
    public final Deque<String> events = new ArrayDeque<>();
    private double stillT, overT;

    public SimCore(int scn) {
        this.scn = Math.max(0, Math.min(4, scn));
        name = NAMES[this.scn];
        switch (this.scn) {
            case 1 -> { massT = 300; powerKw = 3000; teMaxKn = 200; len = 120; brakeMax = 0.9; lineKmh = 90; stopAt = 4000; endAt = 4100; targetS = 230; family = EngineSim.Family.ROAD;
                sig(800, 3); sig(1600, 2); sig(2400, 1); Sig r = sig(3200, 0); r.clearAfterStop = 12; }
            case 2 -> { massT = 160; powerKw = 1200; teMaxKn = 120; len = 46; brakeMax = 0.9; lineKmh = 100; stopAt = 2400; endAt = 2500; targetS = 160; family = EngineSim.Family.ROAD;
                leavesFrom = 1200; leavesTo = 2600; wet = true; sig(2650, 3); }
            case 3 -> { massT = 1600; powerKw = 3200; teMaxKn = 400; len = 520; brakeMax = 0.45; lineKmh = 75; stopAt = 3800; endAt = 4000; targetS = 330; family = EngineSim.Family.HEAVY;
                gradFrom = 1000; gradTo = 3000; gradPermil = -8; lims.add(new Lim(2200, 2600, 30, true)); Sig r = sig(3850, 0); r.clearAfterStop = 1e9; }
            case 4 -> { massT = 420; powerKw = 4000; teMaxKn = 250; len = 160; brakeMax = 0.9; lineKmh = 120; stopAt = 4200; endAt = 4300; targetS = 220; family = EngineSim.Family.ROAD; night = true; vigS = 30;
                lims.add(new Lim(1500, 1900, 60, true)); sig(2600, 1); Sig r = sig(3000, 0); r.approachKmh = 30; }
            default -> { massT = 160; powerKw = 1200; teMaxKn = 120; len = 46; brakeMax = 0.9; lineKmh = 100; stopAt = 2500; endAt = 2600; targetS = 150; family = EngineSim.Family.ROAD; }
        }
        lims.add(0, new Lim(0, 1e9, lineKmh, false));
        note("SCENARIO " + (this.scn + 1) + " " + name + " - " + Math.round(stopAt) + " M TO THE STOP");
    }

    private Sig sig(double at, int aspect) { Sig s = new Sig(); s.at = at; s.aspect = aspect; sigs.add(s); return s; }

    void note(String s) { events.addFirst(String.format(Locale.ROOT, "%3.0fs %s", t, s)); while (events.size() > 8) events.removeLast(); }

    void penalty(double pts, String why) { score -= pts; note(String.format(Locale.ROOT, "-%.0f %s", pts, why)); }

    /** the speed limit the whole train is under at its head position */
    public double limitAt(double head) {
        double lim = lineKmh, tail = head - len;
        for (Lim l : lims) if (l.from < head && l.to > tail) lim = Math.min(lim, l.kmh);
        return lim;
    }

    public double mu(double at) {
        if (at >= leavesFrom && at <= leavesTo) return 0.08;
        return wet ? 0.20 : 0.30;
    }

    public double grade(double at) { return at >= gradFrom && at <= gradTo ? gradPermil / 1000.0 : 0; }

    /** notch response time (s): no turbo = quick, turbo lag while the boost builds */
    double tau() { return family.maxBoostKpa <= 0 ? 0.6 : 1.0 + family.maxBoostKpa / 70.0; }

    /** one step of dt seconds */
    public void step(double dt) {
        if (finished) return;
        t += dt;
        double kmh = v * 3.6, m = massT * 1000, g = 9.81;
        // safety systems first: they can demand the emergency brake
        Sig next = null;
        for (Sig s : sigs) if (!s.passed && s.at > pos) { next = s; break; }
        for (Sig s : sigs) {
            if (!s.awsDone && pos >= s.at - 180) {
                s.awsDone = true;
                if (s.aspect < 3) { awsHorn = true; awsT = 0; note("AWS WARNING - " + ASPECT[s.aspect]); } else note("AWS BELL - CLEAR");
            }
            if (!s.passed && pos >= s.at) {
                s.passed = true;
                if (s.aspect == 0) { spads++; penalty(100, "SPAD - SIGNAL PASSED AT DANGER"); emergency("SPAD"); }
            }
            if (!s.passed && s.aspect == 0) {                                 // the signaller clears it
                if (s.clearAfterStop >= 0 && v < 0.05 && s.at - pos < 400) { s.stoodS += 1; if (s.stoodS > s.clearAfterStop / dt) { s.aspect = 1; note("SIGNAL CLEARED TO YELLOW"); } }
                if (s.approachKmh > 0 && s.at - pos < 300 && kmh < s.approachKmh) { s.aspect = 1; note("APPROACH CONTROL - SIGNAL STEPS UP"); }
            }
        }
        if (awsHorn) { awsT += dt; if (awsT > 2.7) { awsHorn = false; awsMiss++; penalty(15, "AWS NOT ACKNOWLEDGED"); emergency("AWS"); } }
        if (v > 0.1) vigT += dt; else vigT = 0;
        if (vigT > vigS) { vigWarn = true; if (vigT > vigS + 7) { vigT = 0; vigWarn = false; vigMiss++; penalty(10, "VIGILANCE (DSD) MISSED"); emergency("DSD"); } }
        double lim = limitAt(pos);
        if (kmh > lim + 15 && !ebLatched) { tpws++; penalty(10, "TPWS OVERSPEED " + Math.round(kmh) + "/" + Math.round(lim)); emergency("TPWS"); }
        if (kmh > lim + 3) { overT += dt; double rate = kmh > lim + 10 ? 3 : 1; score -= rate * dt; if (overT >= 5) { overT = 0; note(String.format(Locale.ROOT, "OVERSPEED %.0f/%.0f", kmh, lim)); } }
        // power
        notchEff += (notch - notchEff) * Math.min(1, dt / tau());
        double te = 0;
        if (notchEff > 0.05 && !ebLatched && brake == 0) {
            te = Math.min(teMaxKn * 1000, powerKw * 1000 * 0.82 / Math.max(1.5, v)) * notchEff / 8.0;
            double adh = mu(pos) * Math.min(m, 120_000) * g;
            boolean was = slip;
            slip = te > adh;
            if (slip) { te = adh * 0.55; if (!was) { slips++; penalty(2, "WHEELSLIP - EASE THE POWER"); } }
        } else slip = false;
        // brakes
        double target = ebLatched || brake >= 8 ? 1.3 : brake / 7.0;
        double bTau = target > bc ? 2.5 : 4.0;
        bc += (target - bc) * Math.min(1, dt / bTau);
        double bf = bc * brakeMax * m;
        double adhB = mu(pos) * m * g * 0.9;
        boolean wasSlide = slide;
        slide = bf > adhB && v > 0.5;
        if (slide) { bf = adhB * 0.8; if (!wasSlide) { slides++; penalty(2, "WHEEL SLIDE (WSP)"); } }
        // resistance + gradient
        double davis = massT * (10 + 0.25 * v) + 6 * v * v;                   // Davis: N per tonne + air drag
        double gradF = m * g * grade(pos);
        double net = te - davis - gradF;
        double a = (net - (v > 0.01 ? bf : Math.min(bf, Math.max(0, net)))) / (m * 1.06);
        v += a * dt;
        if (v < 0) v = 0;
        pos += v * dt;
        // standing still
        if (v < 0.05) {
            stillT += dt;
            if (ebLatched && stillT > 1 && brake >= 8) { ebLatched = false; note("EMERGENCY RESET"); }
            if (stillT > 2 && Double.isNaN(stopErr) && Math.abs(pos - stopAt) < 150 && pos > 100) stationStop();
        } else stillT = 0;
        if (pos > stopAt + 20 && Double.isNaN(stopErr)) { stopErr = pos - stopAt; penalty(50, "OVERRAN THE STOP BY " + Math.round(stopErr) + " M"); finish(false, "OVERRUN"); }
        if (pos >= endAt && !finished) finish(score >= 75 && spads == 0, "END OF ROUTE");
    }

    void emergency(String why) { if (!ebLatched) { ebLatched = true; ebReason = why; note("EMERGENCY BRAKE - " + why); } }

    void stationStop() {
        stopErr = pos - stopAt;
        double e = Math.abs(stopErr);
        if (e > 50) { penalty(40, "STOPPED " + Math.round(e) + " M SHORT"); finish(false, "STOP SHORT"); return; }
        if (e > 1) penalty(Math.min(30, (e - 1) * (e > 5 ? 2 : 1)), String.format(Locale.ROOT, "STOP %+.1f M FROM THE MARK", stopErr));
        else note("STOP ON THE MARK");
        if (t > targetS) penalty(Math.min(20, (t - targetS) / 10), "LATE " + Math.round(t - targetS) + " S");
        finish(score >= 75 && spads == 0, "STATION STOP");
    }

    void finish(boolean pass, String why) {
        if (finished) return;
        finished = true;
        passed = pass && spads == 0;
        score = Math.max(0, Math.min(100, score));
        result = String.format(Locale.ROOT, "%s %s %d/100 (%s)", name, passed ? "PASS" : "FAIL", Math.round(score), why);
        note(passed ? "PASSED" : "FAILED");
    }

    // ---- driver inputs ---------------------------------------------------------------------------------------------
    public String input(String c) {
        switch (c) {
            case "pu" -> { if (brake > 0) return "Release the brake first"; notch = Math.min(8, notch + 1); }
            case "pd" -> notch = Math.max(0, notch - 1);
            case "bu" -> { notch = 0; brake = Math.min(8, brake + 1); }
            case "bd" -> brake = Math.max(0, brake - 1);
            case "eb" -> { notch = 0; brake = 8; }
            case "aws" -> { if (awsHorn) { awsHorn = false; note("AWS ACKNOWLEDGED"); } }
            case "vig" -> { vigT = 0; vigWarn = false; }
            default -> { }
        }
        if (!c.equals("aws")) { vigT = Math.min(vigT, 0); vigWarn = false; }     // any control movement resets the DSD
        return null;
    }

    /** what is ahead within 1500 m, for the windscreen: "S|dist|aspect", "L|dist|kmh|tsr", "P|dist", "F|dist|leaves" */
    public List<String> ahead() {
        List<String> l = new ArrayList<>();
        for (Sig s : sigs) if (s.at >= pos - 20 && s.at - pos < 1500) l.add("S|" + Math.round(s.at - pos) + "|" + s.aspect);
        for (Lim x : lims) if (x.tsr && x.from >= pos - 20 && x.from - pos < 1500) l.add("L|" + Math.round(x.from - pos) + "|" + Math.round(x.kmh) + "|1");
        if (stopAt - pos < 1500 && stopAt - pos > -200) l.add("P|" + Math.round(stopAt - pos));
        if (leavesFrom >= 0 && leavesTo > pos && leavesFrom - pos < 1500) l.add("F|" + Math.round(Math.max(0, leavesFrom - pos)));
        if (gradFrom >= 0 && gradTo > pos && gradFrom - pos < 1500) l.add("G|" + Math.round(Math.max(0, gradFrom - pos)) + "|" + Math.round(gradPermil));
        return l;
    }

    /** a quick self-check of the physics: returns the failures */
    public static List<String> selftest() {
        List<String> bad = new ArrayList<>();
        SimCore c = new SimCore(0);
        c.notch = 8;
        for (int i = 0; i < 20 * 60; i++) { c.step(0.05); if (c.vigWarn) c.input("vig"); }
        if (c.v * 3.6 < 60) bad.add(String.format(Locale.ROOT, "160 t / 1.2 MW reached only %.0f km/h in 60 s", c.v * 3.6));
        double v0 = c.v;
        c.notch = 0; c.brake = 7;
        double p0 = c.pos;
        for (int i = 0; i < 20 * 120 && c.v > 0; i++) c.step(0.05);
        double stop = c.pos - p0, ideal = v0 * v0 / (2 * 0.9);
        if (c.v > 0 || stop < ideal || stop > ideal * 2.2) bad.add(String.format(Locale.ROOT, "full service from %.0f km/h stopped in %.0f m (ideal %.0f)", v0 * 3.6, stop, ideal));
        SimCore h = new SimCore(3);
        h.pos = 1200; h.v = 60 / 3.6;
        for (int i = 0; i < 20 * 20; i++) h.step(0.05);
        if (h.v * 3.6 < 60) bad.add("1600 t coasting down 8 per mille did not gain speed");
        SimCore s = new SimCore(1);
        s.pos = 3150; s.v = 40 / 3.6; s.notch = 3; s.awsHorn = false;
        for (Sig x : s.sigs) { x.awsDone = true; if (x.at < 3150) x.passed = true; }
        for (int i = 0; i < 20 * 10; i++) s.step(0.05);
        if (s.spads != 1 || !s.ebLatched) bad.add("passing the red signal was not a SPAD with the emergency brake");
        SimCore a = new SimCore(2);
        a.pos = 1300; a.v = 50 / 3.6; a.brake = 7; a.bc = 1;
        a.step(0.05);
        if (!a.slide) bad.add("full service on leaves did not slide (WSP)");
        return bad;
    }
}
