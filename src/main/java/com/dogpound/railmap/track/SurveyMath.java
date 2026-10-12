package com.dogpound.railmap.track;

/**
 * Railway alignment arithmetic for the Surveyor's instrument (her list #18), kept free of Minecraft so it can be
 * checked on its own: {@code java SurveyMath.java} runs {@link #main}.
 */
public final class SurveyMath {
    private SurveyMath() {}

    /** applied cant + allowed cant deficiency, mm (UK practice: 150 + 110) */
    public static final double CANT_MM = 150, DEFICIENCY_MM = 110;

    /** horizontal radius (m) of the circle through three plan points; 0 = straight (collinear) */
    public static double radius(double ax, double az, double bx, double bz, double cx, double cz) {
        double a = Math.hypot(bx - cx, bz - cz), b = Math.hypot(ax - cx, az - cz), c = Math.hypot(ax - bx, az - bz);
        double area2 = Math.abs((bx - ax) * (cz - az) - (cx - ax) * (bz - az));    // twice the triangle area
        if (area2 < 1e-6 * Math.max(1, a * b)) return 0;
        return a * b * c / (2 * area2);
    }

    /** the fastest a train may take a curve of radius r (m) with full cant and allowed deficiency, km/h (0 r = no limit) */
    public static double curveKmh(double r) {
        if (r <= 0) return 0;
        return Math.sqrt(r * (CANT_MM + DEFICIENCY_MM) / 11.82);
    }

    /** gradient per mille */
    public static double gradePermille(double rise, double run) { return run <= 0 ? 0 : 1000 * rise / run; }

    /** "1 in N" text for a gradient */
    public static String oneIn(double permille) { return Math.abs(permille) < 0.5 ? "LEVEL" : "1:" + Math.round(1000 / Math.abs(permille)); }

    /** a speed for a gradient: steep grades need care (1:40 and steeper) */
    public static double gradeKmh(double permille) {
        double g = Math.abs(permille);
        return g > 40 ? 40 : g > 25 ? 80 : 0;
    }

    /** cross-section area (m²) of a cut (depth d > 0) or fill (d < 0) with formation width w and side slopes */
    public static double earthArea(double d, double w) {
        if (d > 0) return d * (w + d * 1.0);        // cuttings stand at 1:1
        double h = -d;
        return h * (w + h * 1.5);                   // embankments at 1:1.5
    }

    /** throw of a vehicle on a curve: {centre throw, end throw} in m for length L and bogie centres s */
    public static double[] throwsM(double length, double bogies, double r) {
        if (r <= 0) return new double[]{0, 0};
        return new double[]{bogies * bogies / (8 * r), (length * length - bogies * bogies) / (8 * r)};
    }

    public static void main(String[] a) {
        double r = radius(0, 0, 100, 0, 200, 0);
        if (r != 0) throw new AssertionError("collinear points must be straight, got " + r);
        r = radius(-500, 0, 0, 500, 500, 0);                  // three points on a 500 m circle
        if (Math.abs(r - 500) > 1e-6) throw new AssertionError("expected R500, got " + r);
        double v = curveKmh(1000);                            // R1000 with 260 mm -> ~148 km/h
        if (Math.abs(v - 148.3) > 0.5) throw new AssertionError("curve speed " + v);
        if (!oneIn(gradePermille(1, 100)).equals("1:100")) throw new AssertionError(oneIn(gradePermille(1, 100)));
        if (Math.abs(earthArea(2, 6) - 16) > 1e-9) throw new AssertionError("cut area");
        if (Math.abs(earthArea(-2, 6) - 18) > 1e-9) throw new AssertionError("fill area");
        double[] t = throwsM(20, 14, 200);
        if (Math.abs(t[0] - 0.1225) > 1e-9 || Math.abs(t[1] - 0.1275) > 1e-9) throw new AssertionError("throws");
        System.out.println("SurveyMath ok");
    }
}
