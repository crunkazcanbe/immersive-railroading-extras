package com.dogpound.railmap.track;

import com.dogpound.railmap.ctl.CtlConfig;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/**
 * Tunnel airflow and smoke between sections (her "next 48" list #29): the tunnel control panel's zone cut into
 * ventilation sections along the jet fans' axis (the tunnel's line), each with its own smoke, CO and temperature, and
 * one longitudinal air velocity for the whole bore:
 * <ul>
 *   <li>velocity: jet-fan thrust (sum of the fans' signed thrust) + the piston effect of moving trains (speed along the
 *       axis x blockage 0.35) + a little natural draught, against wall friction and portal losses (~v²), settling with
 *       a ~10 s time constant;</li>
 *   <li>transport: smoke, CO and heat are carried section to section by the velocity (an upwind scheme) and vent at
 *       the portals; a fire whose velocity is below the critical velocity u_c = 0.61 (g Q / rho cp T)^(1/3) (Kennedy /
 *       Thomas, Q from the fire load) BACK-LAYERS: smoke creeps upstream against the ventilation;</li>
 *   <li>sources: fire (detector fire loads) - smoke and heat in its section; diesel / steam locomotives - CO where they
 *       are; extraction: a fan with open sky within 8 blocks above it (a vent shaft) pulls smoke and heat out of its
 *       section instead of pushing the bore;</li>
 *   <li>what the panel shows: per-section visibility, smoke, CO, temperature, the velocity against u_c, whether
 *       back-layering is happening, and which section is the incident one.</li>
 * </ul>
 */
public final class TunnelAir {
    public final int n;
    public final double[] smoke, co, temp, fire;
    public double u, uc, natural = 0.3;
    public boolean backlayer;
    public int incident = -1;
    private double ax = 1, az = 0, ox, oz, half = 64;
    private double thrust, piston, extract[];
    private double emisTotal;

    public TunnelAir(int sections) {
        n = Math.max(3, sections);
        smoke = new double[n]; co = new double[n]; temp = new double[n]; fire = new double[n]; extract = new double[n];
        java.util.Arrays.fill(temp, 15);
    }

    /** start a step: the axis (the way FORWARD fans blow), the panel position and the zone radius */
    public void begin(EnumFacing axis, BlockPos origin, int zone) {
        ax = axis.getXOffset(); az = axis.getZOffset();
        ox = origin.getX() + 0.5; oz = origin.getZ() + 0.5; half = zone;
        thrust = piston = emisTotal = 0;
        java.util.Arrays.fill(fire, 0);
        java.util.Arrays.fill(extract, 0);
    }

    public double sectionLength() { return 2 * half / n; }

    /** the section a position falls in (clamped to the ends) */
    public int cell(double x, double z) {
        double s = (x - ox) * ax + (z - oz) * az;
        return (int) Math.max(0, Math.min(n - 1, Math.floor((s + half) / (2 * half) * n)));
    }

    public void fan(TileTunnelFan f, boolean shaft) {
        double t = f.thrust() * (f.axis().getXOffset() * ax + f.axis().getZOffset() * az);   // +1 along the panel's axis, -1 against
        if (shaft) extract[cell(f.getPos().getX() + 0.5, f.getPos().getZ() + 0.5)] += Math.abs(f.thrust());
        else thrust += t;
    }

    public void train(double x, double z, double yawDeg, double kmh) {
        double vx = Math.cos(Math.toRadians(yawDeg)), vz = Math.sin(Math.toRadians(yawDeg));
        piston += (vx * ax + vz * az) * kmh / 3.6 * 0.35;
    }

    public void emit(double x, double z, double coRate) { co[cell(x, z)] += coRate; emisTotal += coRate; }
    public void fire(BlockPos p, double load) { if (load > 0) fire[cell(p.getX() + 0.5, p.getZ() + 0.5)] += load; }

    /** one second */
    public void step() {
        double target = thrust * 1.4 / Math.max(1, half / 64.0) + piston + natural;
        // portal + friction losses: the fans can't push it past ~ +-12 m/s
        target = Math.signum(target) * Math.min(12, Math.abs(target));
        u += (target - u) * 0.1;
        // fire: heat release, critical velocity
        double q = 0;
        incident = -1;
        double big = 0;
        for (int i = 0; i < n; i++) { q += fire[i]; if (fire[i] > big) { big = fire[i]; incident = i; } }
        double qMw = q * 5;                                                    // a detector's fire load ~ 5 MW per unit
        uc = qMw <= 0 ? 0 : 0.61 * Math.cbrt(9.81 * qMw * 1e6 / (1.2 * 1005 * 290 * 40));   // 40 m2 bore
        backlayer = incident >= 0 && Math.abs(u) < uc;
        double len = sectionLength();
        double frac = Math.min(0.9, Math.abs(u) / Math.max(1, len));            // share of a section carried on per second
        double[] s2 = new double[n], c2 = new double[n], t2 = new double[n];
        int dir = u >= 0 ? 1 : -1;
        for (int i = 0; i < n; i++) {
            double ts = temp[i] - 15;
            // keep what stays, pass on what moves (the end sections vent out of the portal)
            s2[i] += smoke[i] * (1 - frac); c2[i] += co[i] * (1 - frac); t2[i] += ts * (1 - frac);
            int j = i + dir;
            if (j >= 0 && j < n) { s2[j] += smoke[i] * frac; c2[j] += co[i] * frac; t2[j] += ts * frac; }
        }
        // back-layering: the smoke layer under the roof runs upstream of the fire against slow ventilation
        if (backlayer) {
            double b = (1 - Math.abs(u) / Math.max(0.01, uc)) * 0.3;
            for (int k = 1; k <= 3; k++) {
                int up = incident - dir * k;
                if (up < 0 || up >= n) break;
                s2[up] += s2[incident] * b / k;
                t2[up] += t2[incident] * b / k * 0.5;
            }
        }
        for (int i = 0; i < n; i++) {
            s2[i] += fire[i] * 0.8 * 8.0 / n;                                  // smoke production into the fire's section
            t2[i] += fire[i] * 25;                                             // heat
            double ex = Math.min(0.9, extract[i] * 0.35);                     // shaft extraction
            s2[i] *= (1 - ex) * 0.985; c2[i] *= (1 - ex * 0.8) * 0.99; t2[i] *= (1 - ex * 0.5) * 0.97;
            smoke[i] = Math.max(0, Math.min(40, s2[i]));
            co[i] = Math.max(0, Math.min(2000, c2[i]));
            temp[i] = 15 + Math.max(0, Math.min(900, t2[i]));
        }
    }

    public double maxSmoke() { double m = 0; for (double v : smoke) m = Math.max(m, v); return m; }
    public double maxCo() { double m = 0; for (double v : co) m = Math.max(m, v); return m; }
    public double maxTemp() { double m = 15; for (double v : temp) m = Math.max(m, v); return m; }
    public static int visibility(double smoke) { return smoke <= 0.05 ? 999 : (int) Math.min(999, 150 / smoke); }

    /** the section profile as text for the console: "s,co,temp;..." */
    public String profile() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(i == 0 ? "" : ";").append(String.format(Locale.ROOT, "%.1f,%.0f,%.0f", smoke[i], co[i], temp[i]));
        return b.toString();
    }

    public static boolean enabled() { return CtlConfig.tunnelAirSections; }

    /** self-test: fans at speed clear a fire's smoke downstream; no ventilation = back-layering */
    public static java.util.List<String> selfTest() {
        java.util.List<String> out = new java.util.ArrayList<>();
        TunnelAir a = new TunnelAir(8);
        for (int s = 0; s < 60; s++) { a.begin(EnumFacing.EAST, BlockPos.ORIGIN, 64); a.fire(new BlockPos(0, 0, 0), 2); a.natural = 0; a.step(); }
        out.add((a.backlayer ? "PASS" : "FAIL") + String.format(Locale.ROOT, " still air at a fire back-layers (u %.2f < uc %.2f)", Math.abs(a.u), a.uc));
        TunnelAir b = new TunnelAir(8);
        for (int s = 0; s < 60; s++) { b.begin(EnumFacing.EAST, BlockPos.ORIGIN, 64); b.fire(new BlockPos(0, 0, 0), 2); b.thrust = 4; b.step(); }
        int fc = b.cell(0.5, 0.5);
        out.add((b.smoke[Math.max(0, fc - 2)] < b.smoke[Math.min(b.n - 1, fc + 1)] && !b.backlayer ? "PASS" : "FAIL") + String.format(Locale.ROOT, " fans at %.1f m/s keep the upstream sections clear (%.2f vs %.2f %%/m downstream)", b.u, b.smoke[Math.max(0, fc - 2)], b.smoke[Math.min(b.n - 1, fc + 1)]));
        return out;
    }
}
