package com.dogpound.railmap.interior;

import cam72cam.immersiverailroading.entity.EntityRollingStock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Walk-in train interiors: the furniture plan of one car, worked out the same way on both sides from the car's
 * passenger compartment (IR definition: passenger centre / length / width) and the chosen layout.
 * <p>Coordinates are IR's passenger-offset space, already gauge-scaled: x across the car, y up from the car's origin,
 * z along it - exactly what IR feeds its riders, so a seat's offset IS where the rider sits
 * (world = offset.rotateYaw(-yaw) + car position).
 * <p>Every part has boxes to draw, a clickable hit box, and controls (push buttons, lamps, keys, handles, screens,
 * digits) - each control does or shows something real (InteriorServer).
 */
public final class Interior {
    private Interior() {}

    public enum Layout {
        NONE("No interior", "Bare car: IR's own model only"),
        COACH("Coach saloon", "2+2 table bays, luggage racks + stack, reading lights, help call, next-stop screens, CCTV, crew panel, toilet"),
        CAFE("Café / dining car", "Counter + till menu (Pride coins), stocked fridge, coffee machine, kettle, sink, display case, dining bays, stools"),
        SLEEPER("Sleeper car", "Compartments with lower + upper berths you sleep in, berth panels (light / night / call / wake-up), racks, toilet"),
        /** batch 5 #8: travelling post office (mail.MailTpo) - keep it last, the ordinal is saved */
        TPO("Travelling Post Office", "Sorting frames with pigeonholes (lit per town), sorting table, mail-bag racks, late-posting box, sorters' desk");

        public final String label, info;
        Layout(String label, String info) { this.label = label; this.info = info; }
        public static Layout of(int i) { Layout[] v = values(); return i < 0 || i >= v.length ? NONE : v[i]; }
    }

    public enum Kind { SEAT, BERTH, TABLE, RACK, SCREEN, CCTV, LIGHT, PANEL, PASSCOMM, WALL, WC_DOOR, TOILET, SINK, COUNTER, FRIDGE, KETTLE, COFFEE, TILL, PSU, LADDER, DECOR }

    /** control kinds */
    public static final int PUSH = 0, LAMP = 1, KEY = 2, HANDLE = 3, SCREEN = 4, DIGITS = 5;

    public static final class Btn {
        public final String id, label;
        public final int type, color, face;
        public final double[] box;
        /** state key that lights it (lamp / illuminated push) or the text key a screen / digits shows */
        public final String state;
        public int lines = 1;
        Btn(String id, int type, double[] box, int color, String label, String state, int face) {
            this.id = id; this.type = type; this.box = box; this.color = color; this.label = label; this.state = state; this.face = face;
        }
    }

    public static final class Part {
        public final int idx;
        public final Kind kind;
        public final String name;
        /** drawn boxes: x0 y0 z0 x1 y1 z1 colour(rgb) [glow state key in glow list] */
        public final List<double[]> boxes = new ArrayList<>();
        /** box index -> state key that makes it glow (light strips, fridge light) */
        public final Map<Integer, String> glow = new HashMap<>();
        public final List<Btn> btns = new ArrayList<>();
        public double[] hit;
        /** seats / berths: the riding offset; racks / tables / fridge: item spots (x y z per item) */
        public double[] sit;
        public final List<double[]> spots = new ArrayList<>();
        public int face, slots, no;
        Part(int idx, Kind kind, String name) { this.idx = idx; this.kind = kind; this.name = name; }

        public Btn btn(String id) { for (Btn b : btns) if (b.id.equals(id)) return b; return null; }
        public String seatKey() { return sit == null ? null : String.format(Locale.ROOT, "pride:%.3f,%.3f,%.3f", sit[0], sit[1], sit[2]); }
    }

    public static final class Plan {
        public final Layout layout;
        public final List<Part> parts = new ArrayList<>();
        /** the toilet cubicle (x0 y0 z0 x1 y1 z1), or null */
        public double[] wc;
        public double g, floor, cz, hl, hw, ceil;
        public int seats, berths;
        Plan(Layout l) { this.layout = l; }
        public Part part(int i) { return i >= 0 && i < parts.size() ? parts.get(i) : null; }
        public List<Part> of(Kind k) { List<Part> o = new ArrayList<>(); for (Part p : parts) if (p.kind == k) o.add(p); return o; }
        public boolean inWc(double x, double y, double z) {
            return wc != null && x >= wc[0] && x <= wc[3] && z >= wc[2] && z <= wc[5] && y >= wc[1] - 1 && y <= wc[4] + 1;
        }
    }

    private static final Map<String, Plan> CACHE = new HashMap<>();

    /** the car's interior for a layout (cached per definition, gauge and layout); null for NONE / no compartment */
    public static Plan plan(EntityRollingStock s, Layout l) {
        if (l == null || l == Layout.NONE || s.getDefinition() == null) return null;
        String key = s.getDefinitionID() + "|" + s.gauge.value() + "|" + l + "|" + InteriorConfig.seatSitDrop;
        Plan p = CACHE.get(key);
        if (p != null) return p;
        if (CACHE.size() > 256) CACHE.clear();
        double[] d = dims(s);
        p = build(l, d[0], d[1], d[2], d[3], d[4], d[5]);
        CACHE.put(key, p);
        return p;
    }

    /** {gauge scale, floor y (scaled), compartment centre z (scaled), half-length, half-width, ceiling height} - unscaled unless noted */
    public static double[] dims(EntityRollingStock s) {
        double g = s.gauge.scale();
        cam72cam.immersiverailroading.registry.EntityRollingStockDefinition def = s.getDefinition();
        double carHalfLen = def.getLength(s.gauge) / g / 2, carHalfW = def.getWidth(s.gauge) / g / 2, carH = def.getHeight(s.gauge) / g;
        double hl = carHalfLen - 1.0, hw = carHalfW - 0.2;
        try {
            double[] c = def.prideCompartment();                 // the fork's getter (IR Extras interiors)
            if (c[0] > 1) hl = Math.min(c[0], carHalfLen - 0.3);
            if (c[1] > 1) hw = Math.min(c[1] / 2, carHalfW - 0.1);
        } catch (LinkageError e) { /* older fork: whole-car estimate */ }
        double fy = def.passengerCenter == null ? 1.1 : def.passengerCenter.y;
        double cz = def.passengerCenter == null ? 0 : def.passengerCenter.z;
        double ceil = Math.max(1.95, Math.min(2.45, carH - fy - 0.35));
        return new double[]{g, fy * g, cz * g, Math.max(2, hl), Math.max(0.9, hw), ceil};
    }

    /** what a car's definition name says it is (autoByName), NONE when it says nothing */
    public static Layout byName(EntityRollingStock s) {
        String n;
        try { n = (s.getDefinition().name() + " " + s.getDefinitionID()).toLowerCase(Locale.ROOT); } catch (RuntimeException e) { return Layout.NONE; }
        if (n.matches(".*(dining|buffet|cafe|café|restaurant|bistro|bar car|diner).*")) return Layout.CAFE;
        if (n.matches(".*(sleep|couchette|berth|wagon-lit|pullman sleeper).*")) return Layout.SLEEPER;
        if (com.dogpound.railmap.mail.MailTpo.byName(n)) return Layout.TPO;
        if (n.matches(".*(coach|passenger|carriage|saloon|corridor|open second|first class|second class|trailer).*")) return Layout.COACH;
        return Layout.NONE;
    }

    // ---- colours ----------------------------------------------------------------------------------------------
    static final int MOQUETTE = 0x2C3E8C, MOQUETTE2 = 0x8C2C5A, FRAME = 0x9AA0A8, TABLE_TOP = 0xC8B48A, WOOD = 0x8A6440, PANEL = 0x3A3F47,
            WALL = 0xE6E2D8, STEEL = 0xC0C6CC, WHITE = 0xF4F4F0, RED = 0xD0302A, GREEN = 0x2FB84A, AMBER = 0xF0A020, BLUE = 0x2F7BE0,
            YELLOW = 0xF2D22E, BLACK = 0x18191C, SCREEN_BG = 0x0A0E14, BLANKET = 0x3A6A9A, PILLOW = 0xF0F0F0, COUNTER = 0x5A2E2E, GLASS = 0x9EC8D8;

    // ---- builder ----------------------------------------------------------------------------------------------
    private static final class B {
        final Plan p;
        final double g, f, cz;
        Part cur;
        B(Plan p) { this.p = p; this.g = p.g; this.f = p.floor; this.cz = p.cz; }

        /** unit (metre, floor-relative) box -> scaled offset box */
        double[] u(double x0, double y0, double z0, double x1, double y1, double z1) {
            return new double[]{Math.min(x0, x1) * g, f + Math.min(y0, y1) * g, cz + Math.min(z0, z1) * g, Math.max(x0, x1) * g, f + Math.max(y0, y1) * g, cz + Math.max(z0, z1) * g};
        }

        Part part(Kind k, String name, int face) {
            cur = new Part(p.parts.size(), k, name);
            cur.face = face;
            p.parts.add(cur);
            return cur;
        }

        B box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
            double[] b = u(x0, y0, z0, x1, y1, z1);
            cur.boxes.add(new double[]{b[0], b[1], b[2], b[3], b[4], b[5], rgb});
            return this;
        }

        B glow(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, String state) {
            box(x0, y0, z0, x1, y1, z1, rgb);
            cur.glow.put(cur.boxes.size() - 1, state);
            return this;
        }

        B hit(double x0, double y0, double z0, double x1, double y1, double z1) { cur.hit = u(x0, y0, z0, x1, y1, z1); return this; }

        /** a control on a panel face: (a, b) = centre on the face plane, s = half size; plane = the face's coordinate */
        Btn ctl(String id, int type, int face, double plane, double a, double v, double sa, double sv, int rgb, String label, String state) {
            double d = type == SCREEN || type == DIGITS ? 0.008 : type == HANDLE ? 0.05 : type == KEY ? 0.03 : 0.022;
            double[] b = switch (face) {
                case 0 -> u(a - sa, v - sv, plane, a + sa, v + sv, plane + d);          // faces +z
                case 1 -> u(a - sa, v - sv, plane - d, a + sa, v + sv, plane);          // faces -z
                case 2 -> u(plane, v - sv, a - sa, plane + d, v + sv, a + sa);          // faces +x
                case 3 -> u(plane - d, v - sv, a - sa, plane, v + sv, a + sa);          // faces -x
                default -> u(a - sa, plane - d, v - sv, a + sa, plane, v + sv);         // 4: faces down (a = x, v = z)
            };
            Btn bt = new Btn(id, type, b, rgb, label, state, face);
            cur.btns.add(bt);
            return bt;
        }

        void sit(double x, double cushionTop, double z) {
            cur.sit = new double[]{x * g, f + cushionTop * g - InteriorConfig.seatSitDrop, cz + z * g};
        }

        void spot(double x, double y, double z) { double[] b = u(x, y, z, x, y, z); cur.spots.add(new double[]{b[0], b[1], b[2]}); }
    }

    public static Plan build(Layout l, double g, double floor, double cz, double hl, double hw, double ceil) {
        Plan p = new Plan(l);
        p.g = g; p.floor = floor; p.cz = cz; p.hl = hl; p.hw = hw; p.ceil = ceil;
        B b = new B(p);
        double w0 = -hl + 0.15, w1 = hl - 0.15;
        boolean wc = w1 - w0 >= 9 && hw >= 1.0;
        // front end (+z): crew panel, passenger alarm, next-stop screen, CCTV
        double front = w1 - 0.9;
        ends(b, w0, w1, front, hw, ceil, l);
        double rear = w0 + 0.3;
        if (wc) { toilet(b, w0, hw, ceil); luggageStack(b, w0, hw); rear = w0 + 2.1; }
        lights(b, rear, front, ceil);
        switch (l) {
            case COACH -> bays(b, rear, front, hw, ceil, true);
            case CAFE -> {
                double counterLen = Math.min(5.2, (front - rear) * 0.55);
                cafe(b, front - counterLen, front, hw, ceil);
                bays(b, rear, front - counterLen - 0.2, hw, ceil, false);
            }
            case SLEEPER -> sleeper(b, rear, front, hw, ceil);
            case TPO -> tpo(b, rear, front, hw, ceil);
            default -> {}
        }
        return p;
    }

    // ---- shared ends ------------------------------------------------------------------------------------------
    private static void ends(B b, double w0, double w1, double front, double hw, double ceil, Layout l) {
        // partition at the saloon's front end with the doorway in the middle
        b.part(Kind.WALL, "Saloon end partition", 1);
        b.box(-hw, 0, front, -0.45, ceil, front + 0.06, WALL).box(0.45, 0, front, hw, ceil, front + 0.06, WALL).box(-0.45, 2.0, front, 0.45, ceil, front + 0.06, WALL);
        // next-stop screen over the doorway (faces the saloon, -z)
        b.part(Kind.SCREEN, "Next-stop screen (front)", 1);
        b.box(-0.55, 1.72, front - 0.05, 0.55, 1.99, front, BLACK);
        b.ctl("pis", SCREEN, 1, front - 0.05, 0, 1.855, 0.52, 0.12, SCREEN_BG, "Next stop / PIS", "pis").lines = 3;
        b.hit(-0.55, 1.72, front - 0.07, 0.55, 1.99, front);
        // crew (guard's) panel on the right of the doorway
        b.part(Kind.PANEL, "Crew control panel", 1);
        double px0 = 0.5, px1 = Math.min(hw - 0.05, 1.25);
        b.box(px0, 0.95, front - 0.04, px1, 1.68, front, PANEL);
        b.hit(px0, 0.95, front - 0.06, px1, 1.68, front);
        double pf = front - 0.04, pc = (px0 + px1) / 2;
        b.ctl("status", SCREEN, 1, pf, pc, 1.56, (px1 - px0) / 2 - 0.03, 0.08, SCREEN_BG, "Car status", "crew").lines = 4;
        String[] lamps = {"POWER", "LIGHTS", "CALL", "ALARM", "WC", "WATER", "WASTE", "CCTV"};
        String[] keys = {"pwr", "lit", "calls", "alarm", "wcocc", "waterlow", "wastefull", "cctv"};
        int[] cols = {GREEN, WHITE, AMBER, RED, AMBER, AMBER, RED, GREEN};
        double step = (px1 - px0 - 0.08) / (lamps.length - 1);
        for (int i = 0; i < lamps.length; i++) b.ctl("l_" + keys[i], LAMP, 1, pf, px0 + 0.04 + i * step, 1.43, 0.016, 0.016, cols[i], lamps[i] + " lamp", keys[i]);
        String[] btns = {"lights", "pa", "callrst", "alarmrst", "discharge", "doorl", "doorr", "doorc"};
        String[] bl = {"LIGHTS AUTO/ON/OFF", "PA CHIME", "CALL RESET", "ALARM RESET", "WASTE DISCHARGE", "DOORS OPEN LEFT", "DOORS OPEN RIGHT", "DOORS CLOSE"};
        int[] bc = {WHITE, BLUE, AMBER, RED, YELLOW, GREEN, GREEN, RED};
        for (int i = 0; i < btns.length; i++) b.ctl(btns[i], i == 0 ? KEY : PUSH, 1, pf, px0 + 0.04 + (i % 4) * ((px1 - px0 - 0.08) / 3), i < 4 ? 1.30 : 1.17, 0.025, 0.025, bc[i], bl[i], i == 0 ? "litmode" : null);
        b.ctl("cctvkey", KEY, 1, pf, px0 + 0.06, 1.04, 0.025, 0.025, BLACK, "CCTV key ON/OFF", "cctv");
        b.ctl("water", DIGITS, 1, pf, px0 + 0.30, 1.04, 0.12, 0.03, SCREEN_BG, "Fresh water (litres)", "water");
        b.ctl("waste", DIGITS, 1, pf, px1 - 0.18, 1.04, 0.12, 0.03, SCREEN_BG, "Waste tank", "waste");
        // passenger alarm (passcomm) on the left of the doorway
        b.part(Kind.PASSCOMM, "Passenger alarm", 1);
        double ax = -Math.min(hw - 0.15, 0.8);
        b.box(ax - 0.12, 1.45, front - 0.02, ax + 0.12, 1.7, front, RED).box(ax - 0.11, 1.62, front - 0.025, ax + 0.11, 1.68, front - 0.02, WHITE);
        b.ctl("pull", HANDLE, 1, front - 0.02, ax, 1.52, 0.09, 0.03, RED, "PASSENGER ALARM - pull in emergency", "alarm");
        b.ctl("l_alarm", LAMP, 1, front - 0.02, ax, 1.60, 0.012, 0.012, RED, "Alarm lamp", "alarm");
        b.hit(ax - 0.12, 1.45, front - 0.08, ax + 0.12, 1.7, front);
        // CCTV dome on the ceiling, looking down the saloon
        b.part(Kind.CCTV, "Saloon CCTV camera", 1);
        b.box(-0.09, ceil - 0.02, front - 0.4, 0.09, ceil, front - 0.22, WHITE).box(-0.07, ceil - 0.1, front - 0.38, 0.07, ceil - 0.02, front - 0.24, 0x30343A);
        b.glow(-0.012, ceil - 0.03, front - 0.245, 0.012, ceil - 0.015, front - 0.235, RED, "cctvrec");
        b.hit(-0.09, ceil - 0.1, front - 0.4, 0.09, ceil, front - 0.22);
    }

    private static void lights(B b, double rear, double front, double ceil) {
        b.part(Kind.LIGHT, "Saloon lighting", 0);
        b.glow(-0.55, ceil - 0.03, rear, -0.4, ceil, front, WHITE, "lit");
        b.glow(0.4, ceil - 0.03, rear, 0.55, ceil, front, WHITE, "lit");
        b.box(-0.06, ceil - 0.015, rear, 0.06, ceil, front, FRAME);
        // a second, rear next-stop screen facing forward
        b.part(Kind.SCREEN, "Next-stop screen (rear)", 0);
        b.box(-0.55, 1.72, rear - 0.05, 0.55, 1.99, rear, BLACK);
        b.ctl("pis", SCREEN, 0, rear, 0, 1.855, 0.52, 0.12, SCREEN_BG, "Next stop / PIS", "pis").lines = 3;
        b.hit(-0.55, 1.72, rear - 0.05, 0.55, 1.99, rear + 0.02);
    }

    // ---- toilet cubicle at the rear right ---------------------------------------------------------------------
    private static void toilet(B b, double w0, double hw, double ceil) {
        double x0 = 0.42, z1 = w0 + 1.9;
        b.p.wc = b.u(x0, 0, w0, hw, ceil, z1);
        b.part(Kind.WALL, "Toilet cubicle", 3);
        b.box(x0, 0, w0, x0 + 0.06, ceil, w0 + 0.15, WALL).box(x0, 0, w0 + 1.05, x0 + 0.06, ceil, z1, WALL).box(x0, 2.0, w0 + 0.15, x0 + 0.06, ceil, w0 + 1.05, WALL)
                .box(x0, 0, z1 - 0.06, hw, ceil, z1, WALL);
        // the door (always shown shut; the lock is what counts) with OCCUPIED / VACANT sign
        b.part(Kind.WC_DOOR, "Toilet door", 3);
        b.box(x0 + 0.01, 0.02, w0 + 0.15, x0 + 0.05, 1.98, w0 + 1.05, 0xB8BEC6);
        b.ctl("sign", SCREEN, 3, x0, w0 + 0.6, 1.62, 0.3, 0.07, SCREEN_BG, "Toilet OCCUPIED / VACANT", "wcsign");
        b.ctl("help", PUSH, 3, x0, w0 + 0.95, 1.1, 0.03, 0.03, YELLOW, "Toilet door OPEN / HELP (knocks)", null);
        b.ctl("lockin", PUSH, 2, x0 + 0.05, w0 + 0.95, 1.12, 0.03, 0.03, RED, "LOCK / UNLOCK (from inside)", "wcocc");
        b.hit(x0 - 0.02, 0, w0 + 0.15, x0 + 0.07, 1.98, w0 + 1.05);
        // the toilet itself (vacuum WC) against the back wall
        b.part(Kind.TOILET, "Vacuum toilet", 2);
        double tx = hw - 0.3, tz = w0 + 0.45;
        b.box(tx - 0.2, 0, tz - 0.22, tx + 0.2, 0.42, tz + 0.22, WHITE).box(tx - 0.19, 0.42, tz - 0.21, tx + 0.19, 0.45, tz + 0.21, 0xDDDDDD)
                .box(tx + 0.18, 0.45, tz - 0.21, tx + 0.22, 0.85, tz + 0.21, 0xDDDDDD).box(tx + 0.22, 0.9, tz - 0.15, hw, 1.2, tz + 0.15, STEEL);
        b.ctl("flush", PUSH, 3, tx + 0.22, tz, 1.05, 0.05, 0.05, BLUE, "FLUSH", null);
        b.ctl("l_out", LAMP, 3, tx + 0.22, tz + 0.1, 1.15, 0.012, 0.012, RED, "OUT OF ORDER lamp", "wcout");
        b.ctl("cord", HANDLE, 3, hw - 0.02, w0 + 0.9, 0.35, 0.02, 0.25, RED, "Emergency cord - pull for help", "wccord");
        b.hit(tx - 0.2, 0, tz - 0.22, hw, 1.2, tz + 0.22);
        // wash basin, soap, dryer, mirror
        b.part(Kind.SINK, "Toilet wash basin", 3);
        double sz = w0 + 1.45;
        b.box(hw - 0.42, 0.78, sz - 0.22, hw, 0.86, sz + 0.22, WHITE).box(hw - 0.4, 0, sz - 0.15, hw - 0.1, 0.78, sz + 0.15, STEEL)
                .box(hw - 0.06, 0.86, sz - 0.03, hw - 0.02, 1.0, sz + 0.03, STEEL).box(hw - 0.02, 1.2, sz - 0.25, hw, 1.7, sz + 0.25, GLASS)
                .box(hw - 0.12, 1.0, sz + 0.27, hw, 1.25, sz + 0.42, WHITE);
        b.ctl("tap", PUSH, 3, hw - 0.02, sz, 0.94, 0.03, 0.03, STEEL, "TAP (bottle / bucket fills)", null);
        b.ctl("soap", PUSH, 3, hw - 0.02, sz - 0.14, 0.98, 0.025, 0.025, 0xF0B0D0, "SOAP", null);
        b.ctl("dryer", PUSH, 3, hw - 0.12, sz + 0.345, 1.04, 0.03, 0.03, BLACK, "HAND DRYER", null);
        b.hit(hw - 0.42, 0, sz - 0.25, hw, 1.7, sz + 0.42);
    }

    /** big luggage stack opposite the toilet (3 shelves, 9 slots) */
    private static void luggageStack(B b, double w0, double hw) {
        b.part(Kind.RACK, "Luggage stack", 2);
        double x0 = -hw, x1 = -0.45, z0 = w0 + 0.2, z1 = w0 + 1.6;
        b.box(x0, 0, z0, x1, 0.04, z1, FRAME).box(x0, 0.6, z0, x1, 0.64, z1, FRAME).box(x0, 1.2, z0, x1, 1.24, z1, FRAME).box(x0, 1.8, z0, x1, 1.84, z1, FRAME)
                .box(x0, 0, z0, x1, 1.84, z0 + 0.04, FRAME).box(x0, 0, z1 - 0.04, x1, 1.84, z1, FRAME).box(x0, 0, z0, x0 + 0.03, 1.84, z1, FRAME);
        b.hit(x0, 0, z0, x1, 1.84, z1);
        b.cur.slots = 9;
        for (int sh = 0; sh < 3; sh++) for (int i = 0; i < 3; i++) b.spot((x0 + x1) / 2, 0.06 + sh * 0.6, z0 + 0.25 + i * (z1 - z0 - 0.5) / 2);
    }

    // ---- 2+2 (or 1+1) table bays -------------------------------------------------------------------------------
    private static void bays(B b, double rear, double front, double hw, double ceil, boolean racks) {
        double ae = 0.36;                                       // aisle half-width
        int cols = hw >= 1.22 ? 2 : 1;
        double sw = Math.min(0.5, (hw - ae - 0.04) / cols);
        int n = (int) Math.floor((front - rear) / 1.9);
        int seatNo = b.p.seats;
        for (int bay = 0; bay < n; bay++) {
            double wa = rear + bay * 1.9 + ((front - rear) - n * 1.9) / 2;
            for (int side = -1; side <= 1; side += 2) {
                double xin = side * ae, xout = side * (ae + cols * sw);
                // seats: row A faces +z (back at wa), row B faces -z (back at wa + 1.9)
                for (int row = 0; row < 2; row++) {
                    for (int c = 0; c < cols; c++) {
                        double xc = side * (ae + sw * (c + 0.5));
                        double za = row == 0 ? wa + 0.12 : wa + 1.78;      // backrest
                        double dir = row == 0 ? 1 : -1;
                        seat(b, ++seatNo, xc, sw, za, dir, (bay + c) % 2 == 0 ? MOQUETTE : MOQUETTE2);
                    }
                }
                // table between the rows, with sockets and an item tray on top
                b.part(Kind.TABLE, "Table", side > 0 ? 3 : 2);
                double tz0 = wa + 0.72, tz1 = wa + 1.18;
                b.box(xin + side * 0.05, 0.7, tz0, xout, 0.74, tz1, TABLE_TOP).box(xin + side * 0.1, 0.0, (tz0 + tz1) / 2 - 0.04, xin + side * 0.16, 0.7, (tz0 + tz1) / 2 + 0.04, FRAME);
                b.hit(xin + side * 0.05, 0.6, tz0, xout, 0.76, tz1);
                b.cur.slots = 9;
                for (int i = 0; i < 4; i++) b.spot(xin + side * (0.2 + (i % 2) * (Math.abs(xout - xin) - 0.35)), 0.745, tz0 + 0.12 + (i / 2) * 0.22);
                double sx = xout - side * 0.02;
                b.ctl("sock", LAMP, side > 0 ? 3 : 2, sx, tz0 + 0.12, 0.66, 0.03, 0.025, GREEN, "230 V socket (lit = car power)", "pwr");
                b.ctl("usb", LAMP, side > 0 ? 3 : 2, sx, tz0 + 0.24, 0.66, 0.018, 0.012, BLUE, "USB charger (lit = car power)", "pwr");
                // overhead rack + passenger service unit (reading lights per seat, help call)
                if (racks) {
                    b.part(Kind.RACK, "Luggage rack", side > 0 ? 3 : 2);
                    double rx0 = side * (hw - 0.48), rx1 = side * hw;
                    b.box(rx0, 1.86, wa + 0.05, rx1, 1.89, wa + 1.85, FRAME).box(rx0, 1.89, wa + 0.05, rx0 + side * 0.02, 1.95, wa + 1.85, STEEL);
                    b.hit(rx0, 1.84, wa + 0.05, rx1, 1.97, wa + 1.85);
                    b.cur.slots = 9;
                    for (int i = 0; i < 4; i++) b.spot((rx0 + rx1) / 2, 1.895, wa + 0.3 + i * 0.42);
                }
                b.part(Kind.PSU, "Passenger service unit", 4);
                double ux0 = side * (hw - 0.48), ux1 = side * (hw - 0.05);
                b.box(ux0, 1.78, wa + 0.1, ux1, 1.84, wa + 1.8, PANEL);
                b.hit(ux0, 1.76, wa + 0.1, ux1, 1.84, wa + 1.8);
                for (int row = 0; row < 2; row++) for (int c = 0; c < cols; c++) {
                    int sn = seatNo - (cols * 2) + row * cols + c + 1;
                    double zz = (row == 0 ? wa + 0.4 : wa + 1.5) + (c == 0 ? -0.08 : 0.08), xx = side * (hw - 0.14);
                    b.ctl("read" + sn, PUSH, 4, 1.78, xx, zz, 0.03, 0.03, WHITE, "Reading light seat " + sn, "read" + sn);
                }
                b.ctl("call", PUSH, 4, 1.78, side * (hw - 0.3), wa + 1.05, 0.035, 0.035, AMBER, "HELP / call the crew", "call" + b.cur.idx);
                b.ctl("l_call", LAMP, 4, 1.78, side * (hw - 0.3), wa + 1.15, 0.015, 0.015, AMBER, "Help call lamp", "call" + b.cur.idx);
                b.ctl("vent", KEY, 4, 1.78, side * (hw - 0.3), wa + 0.8, 0.04, 0.04, 0x6A7078, "Air vent OPEN/SHUT (fresh air, cools the bay)", "vent" + b.cur.idx);
                b.ctl("bayt", DIGITS, 4, 1.78, side * (hw - 0.3), wa + 0.95, 0.06, 0.02, SCREEN_BG, "Bay temperature", "bayt" + b.cur.idx);
            }
        }
        b.p.seats = seatNo;
    }

    private static void seat(B b, int no, double xc, double sw, double zBack, double dir, int cloth) {
        Part s = b.part(Kind.SEAT, "Seat " + no, dir > 0 ? 0 : 1);
        s.no = no;
        double hx = sw / 2 - 0.02;
        double c0 = zBack + dir * 0.06, c1 = zBack + dir * 0.52;
        b.box(xc - hx, 0.0, c0 + dir * 0.06, xc + hx, 0.38, c1 - dir * 0.06, FRAME)              // pedestal
                .box(xc - hx, 0.38, c0, xc + hx, 0.47, c1, cloth)                            // cushion
                .box(xc - hx, 0.47, zBack - dir * 0.04, xc + hx, 1.18, zBack + dir * 0.06, cloth)   // backrest
                .box(xc - hx + 0.03, 1.1, zBack - dir * 0.045, xc + hx - 0.03, 1.2, zBack + dir * 0.065, WHITE)   // antimacassar
                .box(xc + hx - 0.035, 0.47, c0, xc + hx, 0.6, c1 - dir * 0.08, FRAME);            // armrest
        b.hit(xc - hx, 0.3, Math.min(zBack - dir * 0.04, c1), xc + hx, 0.56, Math.max(zBack - dir * 0.04, c1));   // the cushion: a window seat stays clickable over the aisle seat
        // seat reservation display on the back of the seat (seen by the row behind), number + FREE / TAKEN
        b.ctl("res", DIGITS, dir > 0 ? 1 : 0, zBack - dir * 0.04, xc, 1.05, hx - 0.04, 0.035, SCREEN_BG, "Seat " + no + " reservation display", "seat" + no);
        b.sit(xc, 0.47, (c0 + c1) / 2 + dir * 0.03);
    }

    // ---- café counter -----------------------------------------------------------------------------------------
    private static void cafe(B b, double z0, double z1, double hw, double ceil) {
        double cx0 = 0.38, cx1 = 0.78, bx0 = hw - 0.55;
        z1 -= 0.1;
        // front counter (customers in the aisle, -x side)
        b.part(Kind.COUNTER, "Café counter display case", 3);
        b.box(cx0, 0, z0 + 0.3, cx1, 0.95, z1 - 1.5, COUNTER).box(cx0 - 0.03, 0.95, z0 + 0.25, cx1 + 0.02, 0.99, z1 - 1.45, WOOD)
                .box(cx0 + 0.02, 0.99, z0 + 0.4, cx1 - 0.05, 1.3, z0 + 1.6, GLASS).box(cx0 + 0.02, 1.3, z0 + 0.4, cx1 - 0.05, 1.32, z0 + 1.6, STEEL);
        b.hit(cx0 - 0.03, 0.9, z0 + 0.4, cx1, 1.32, z0 + 1.6);
        b.cur.slots = 9;
        for (int i = 0; i < 6; i++) b.spot(cx0 + 0.1 + (i % 2) * 0.2, 1.0, z0 + 0.55 + (i / 2) * 0.4);
        b.ctl("bell", PUSH, 3, cx0, z0 + 1.85, 0.85, 0.04, 0.04, YELLOW, "Service bell (rings the crew)", null);
        // till: menu screen, card reader, keys
        b.part(Kind.TILL, "Till + menu (Pride coins)", 3);
        double tz = z0 + 2.3;
        b.box(cx0 + 0.05, 0.99, tz - 0.25, cx0 + 0.35, 1.05, tz + 0.25, BLACK).box(cx0 + 0.15, 1.05, tz - 0.22, cx0 + 0.2, 1.38, tz + 0.22, PANEL);
        b.ctl("menu", SCREEN, 3, cx0 + 0.15, tz, 1.25, 0.2, 0.1, SCREEN_BG, "MENU - tap to order", "tillscr").lines = 3;
        b.ctl("card", PUSH, 3, cx0 + 0.05, tz - 0.17, 1.08, 0.04, 0.03, 0x404850, "Card reader (opens the menu)", "paid");
        b.ctl("cashup", KEY, 3, cx0 + 0.05, tz + 0.17, 1.08, 0.03, 0.03, AMBER, "CASH UP key (owner) - pays the takings out", null);
        b.ctl("till", DIGITS, 3, cx0 + 0.05, tz, 1.03, 0.09, 0.02, SCREEN_BG, "Takings", "till");
        b.hit(cx0, 0.95, tz - 0.27, cx0 + 0.36, 1.4, tz + 0.27);
        // menu board over the counter
        b.part(Kind.SCREEN, "Menu board", 3);
        b.box(cx0 + 0.03, 1.7, z0 + 0.4, cx0 + 0.08, 2.15, z0 + 2.6, BLACK);
        b.ctl("board", SCREEN, 3, cx0 + 0.03, z0 + 1.5, 1.925, 1.05, 0.21, SCREEN_BG, "Menu board", "board").lines = 6;
        b.hit(cx0 + 0.0, 1.7, z0 + 0.4, cx0 + 0.08, 2.15, z0 + 2.6);
        // back counter: sink, kettle, coffee machine
        b.part(Kind.WALL, "Back counter", 3);
        b.box(bx0, 0, z0 + 0.2, hw, 0.9, z1 - 1.3, 0xC9C2B6).box(bx0 - 0.02, 0.9, z0 + 0.15, hw, 0.94, z1 - 1.25, STEEL)
                .box(hw - 0.05, 1.3, z0 + 0.2, hw, 1.9, z1 - 1.3, 0xD8D0C2);
        b.part(Kind.SINK, "Galley sink", 3);
        double sz = z0 + 0.6;
        b.box(bx0 + 0.05, 0.86, sz - 0.25, hw - 0.05, 0.94, sz + 0.25, STEEL).box(hw - 0.08, 0.94, sz - 0.02, hw - 0.04, 1.14, sz + 0.02, STEEL);
        b.ctl("tap", PUSH, 3, hw - 0.04, sz, 1.1, 0.03, 0.03, STEEL, "TAP (bottle / bucket fills; water bucket refills the tank)", null);
        b.hit(bx0, 0.85, sz - 0.27, hw, 1.15, sz + 0.27);
        b.part(Kind.KETTLE, "Kettle (2 L)", 3);
        double kz = z0 + 1.2;
        b.box(bx0 + 0.1, 0.94, kz - 0.1, bx0 + 0.3, 0.96, kz + 0.1, BLACK).box(bx0 + 0.12, 0.96, kz - 0.08, bx0 + 0.28, 1.2, kz + 0.08, STEEL)
                .box(bx0 + 0.28, 1.0, kz - 0.02, bx0 + 0.32, 1.17, kz + 0.02, BLACK);
        b.glow(bx0 + 0.1, 0.96, kz - 0.085, bx0 + 0.12, 0.99, kz - 0.07, BLUE, "kon");
        b.ctl("kettle", PUSH, 3, bx0 + 0.1, kz + 0.04, 0.98, 0.015, 0.012, WHITE, "Kettle ON / OFF (fills from the tank)", "kon");
        b.ctl("ktemp", DIGITS, 3, bx0 + 0.1, kz, 1.12, 0.06, 0.02, SCREEN_BG, "Kettle water temperature", "ktemp");
        b.hit(bx0 + 0.08, 0.93, kz - 0.12, bx0 + 0.33, 1.22, kz + 0.12);
        b.part(Kind.COFFEE, "Espresso machine", 3);
        double mz = z0 + 2.0;
        b.box(hw - 0.5, 0.94, mz - 0.35, hw - 0.02, 1.45, mz + 0.35, 0x8A1E1E).box(hw - 0.52, 1.45, mz - 0.37, hw, 1.5, mz + 0.37, STEEL)
                .box(hw - 0.58, 1.08, mz - 0.25, hw - 0.5, 1.12, mz - 0.1, BLACK).box(hw - 0.58, 1.08, mz + 0.1, hw - 0.5, 1.12, mz + 0.25, BLACK)
                .box(hw - 0.56, 0.94, mz - 0.3, hw - 0.5, 0.96, mz + 0.3, 0x30343A);
        double mf = hw - 0.5;
        b.ctl("cpower", KEY, 3, mf, mz - 0.27, 1.38, 0.03, 0.03, BLACK, "Machine POWER (boiler heats)", "con");
        b.ctl("l_cready", LAMP, 3, mf, mz - 0.17, 1.4, 0.014, 0.014, GREEN, "READY lamp (boiler at pressure)", "cready");
        b.ctl("l_cheat", LAMP, 3, mf, mz - 0.11, 1.4, 0.014, 0.014, AMBER, "HEATING lamp", "cheat");
        b.ctl("l_cwater", LAMP, 3, mf, mz - 0.05, 1.4, 0.014, 0.014, RED, "NO WATER lamp", "cnowater");
        b.ctl("ctemp", DIGITS, 3, mf, mz + 0.14, 1.39, 0.13, 0.03, SCREEN_BG, "Boiler temperature / pressure", "ctemp");
        String[] drinks = {"espresso", "latte", "tea", "choc", "water"};
        String[] dl = {"ESPRESSO (cocoa beans + bottle)", "LATTE (cocoa + milk bucket + bottle)", "TEA (kettle water + bottle [+ sugar])", "HOT CHOCOLATE (cocoa + milk + sugar + bottle)", "HOT WATER (bottle)"};
        int[] dc = {0x6A3A1A, 0xC8A070, 0xB0702A, 0x4A2A10, BLUE};
        for (int i = 0; i < drinks.length; i++) b.ctl(drinks[i], PUSH, 3, mf, mz - 0.28 + i * 0.14, 1.27, 0.035, 0.03, dc[i], dl[i], null);
        b.hit(hw - 0.6, 0.93, mz - 0.37, hw, 1.5, mz + 0.37);
        // fridge (stock) at the front end of the galley: glass door, items visible
        b.part(Kind.FRIDGE, "Stock fridge", 3);
        double fz0 = z1 - 1.2, fz1 = z1 - 0.4, fx0 = hw - 0.62;
        b.box(fx0, 0, fz0, hw, 1.9, fz1, WHITE).box(fx0 - 0.02, 0.15, fz0 + 0.05, fx0, 1.8, fz1 - 0.05, GLASS).box(fx0 - 0.05, 0.9, fz1 - 0.12, fx0 - 0.02, 1.3, fz1 - 0.08, STEEL);
        b.glow(fx0 + 0.02, 1.82, fz0 + 0.05, fx0 + 0.05, 1.86, fz1 - 0.05, WHITE, "pwr");
        b.ctl("ftemp", DIGITS, 3, fx0, (fz0 + fz1) / 2, 1.86, 0.1, 0.025, SCREEN_BG, "Fridge temperature", "ftemp");
        b.hit(fx0 - 0.06, 0, fz0, hw, 1.9, fz1);
        b.cur.slots = 27;
        for (int i = 0; i < 9; i++) b.spot(fx0 + 0.15, 0.25 + (i / 3) * 0.5, fz0 + 0.18 + (i % 3) * 0.22);
        // bar ledge + stools on the other side of the aisle
        b.part(Kind.TABLE, "Bar ledge", 2);
        double lx0 = -hw, lx1 = -hw + 0.3;
        b.box(lx0, 1.0, z0 + 0.3, lx1, 1.04, z1 - 0.3, WOOD).box(lx0, 0, z0 + 0.3, lx0 + 0.04, 1.0, z1 - 0.3, WOOD);
        b.hit(lx0, 0.95, z0 + 0.3, lx1, 1.06, z1 - 0.3);
        b.cur.slots = 9;
        for (int i = 0; i < 4; i++) b.spot(lx0 + 0.15, 1.045, z0 + 0.6 + i * (z1 - z0 - 1.2) / 3);
        int n = (int) ((z1 - z0 - 0.6) / 0.75);
        for (int i = 0; i < n; i++) {
            int no = ++b.p.seats;
            Part s = b.part(Kind.SEAT, "Stool " + no, 3);
            s.no = no;
            double zc = z0 + 0.7 + i * 0.75, xc = -hw + 0.55;
            b.box(xc - 0.03, 0, zc - 0.03, xc + 0.03, 0.72, zc + 0.03, STEEL).box(xc - 0.17, 0.72, zc - 0.17, xc + 0.17, 0.8, zc + 0.17, 0xA02828)
                    .box(xc - 0.15, 0.3, zc - 0.15, xc + 0.15, 0.32, zc + 0.15, STEEL);
            b.hit(xc - 0.17, 0.3, zc - 0.17, xc + 0.17, 0.82, zc + 0.17);
            b.sit(xc, 0.8, zc);
        }
    }

    // ---- sleeper compartments ---------------------------------------------------------------------------------
    // ---- travelling post office (batch 5 #8, mail.MailTpo) -----------------------------------------------------------
    private static final int SACK = 0xB8AC8C, RED_BOX = 0xC0242A;

    private static void tpo(B b, double rear, double front, double hw, double ceil) {
        // +x side: pigeonhole sorting frames over the sorting table, the length of the car
        int frames = Math.max(1, Math.min(4, (int) ((front - rear - 1.6) / 1.6)));
        double fx0 = hw - 0.42, fx1 = hw;
        for (int f = 0; f < frames; f++) {
            double z0 = rear + 0.2 + f * 1.6, z1 = z0 + 1.5;
            b.part(Kind.RACK, "Sorting frame " + (f + 1), 3);
            b.box(fx0, 0.95, z0, fx1, 2.05, z0 + 0.03, WOOD).box(fx0, 0.95, z1 - 0.03, fx1, 2.05, z1, WOOD).box(fx0, 2.02, z0, fx1, 2.06, z1, WOOD).box(fx1 - 0.02, 0.95, z0, fx1, 2.05, z1, 0x6A4A30);
            for (int r = 0; r <= 4; r++) b.box(fx0, 0.95 + r * 0.265, z0, fx1, 0.965 + r * 0.265, z1, WOOD);
            for (int c = 1; c < 6; c++) b.box(fx0, 0.95, z0 + c * 0.25 - 0.008, fx1, 2.02, z0 + c * 0.25 + 0.008, WOOD);
            for (int r = 0; r < 4; r++)
                for (int c = 0; c < 6; c++) {
                    double y = 0.965 + (3 - r) * 0.265, zc = z0 + c * 0.25;
                    b.glow(fx0 + 0.1, y, zc + 0.04, fx1 - 0.05, y + 0.035, zc + 0.21, 0xF2EEE2, "tpoh" + (f * 24 + r * 6 + c));
                    b.box(fx0 + 0.005, y - 0.012, zc + 0.05, fx0 + 0.01, y + 0.012, zc + 0.2, 0xF4F0E0);       // hole label card
                }
            b.ctl("lab", DIGITS, 3, fx0, (z0 + z1) / 2, 2.12, 0.34, 0.035, SCREEN_BG, "Frame " + (f + 1) + " towns", "tpof" + f);
            b.hit(fx0, 0.95, z0, fx1, 2.06, z1);
            b.cur.slots = 9;
            for (int i = 0; i < 9; i++) b.spot(fx0 + 0.2, 0.97 + (i / 3) * 0.265, z0 + 0.2 + (i % 3) * 0.55);
        }
        double tz1 = rear + 0.2 + frames * 1.6;
        b.part(Kind.TABLE, "Sorting table (loose letters)", 3);
        b.box(hw - 0.75, 0.86, rear + 0.2, hw - 0.42, 0.9, tz1, TABLE_TOP).box(hw - 0.74, 0.0, rear + 0.3, hw - 0.7, 0.86, rear + 0.34, FRAME).box(hw - 0.74, 0.0, tz1 - 0.34, hw - 0.7, 0.86, tz1 - 0.3, FRAME);
        for (int k = 0; k < 6; k++) b.glow(hw - 0.7 + (k % 2) * 0.12, 0.9 + (k / 2) * 0.012, rear + 0.5 + k * 0.17, hw - 0.5 + (k % 2) * 0.12, 0.91 + (k / 2) * 0.012, rear + 0.64 + k * 0.17, 0xF2EEE2, "tpotray");
        b.hit(hw - 0.75, 0.8, rear + 0.2, hw - 0.42, 0.95, tz1);
        b.cur.slots = 9;
        for (int i = 0; i < 4; i++) b.spot(hw - 0.6, 0.905, rear + 0.5 + i * (tz1 - rear - 0.8) / 3);
        // -x side: mail-bag racks with the bags on board hanging from them
        double rz1 = Math.min(front - 1.4, rear + 0.2 + 8 * 0.5);
        b.part(Kind.RACK, "Mail bag rack", 2);
        b.box(-hw, 1.72, rear + 0.2, -hw + 0.5, 1.76, rz1, STEEL).box(-hw, 0.02, rear + 0.2, -hw + 0.5, 0.06, rz1, STEEL);
        for (double z = rear + 0.2; z <= rz1 + 0.01; z += (rz1 - rear - 0.2) / 2) b.box(-hw + 0.46, 0.0, z - 0.02, -hw + 0.5, 1.76, z + 0.02, STEEL);
        for (int k = 0; k < 8; k++) {
            double zc = rear + 0.45 + k * 0.5;
            if (zc > rz1 - 0.2) break;
            b.box(-hw + 0.22, 1.6, zc - 0.015, -hw + 0.26, 1.72, zc + 0.015, STEEL);                       // hook
            b.glow(-hw + 0.06, 0.95, zc - 0.2, -hw + 0.42, 1.55, zc + 0.2, SACK, "tpob" + k);                 // the bag
            b.glow(-hw + 0.43, 1.35, zc - 0.06, -hw + 0.44, 1.47, zc + 0.06, RED, "tpob" + k);                // its label
        }
        b.hit(-hw, 0.0, rear + 0.2, -hw + 0.5, 1.76, rz1);
        b.cur.slots = 9;
        for (int i = 0; i < 9; i++) b.spot(-hw + 0.25, 0.07, rear + 0.35 + i * (rz1 - rear - 0.4) / 8);
        // late-posting box by the door end: letters dropped in are franked LATE and sorted on the train
        b.part(Kind.RACK, "Late posting box", 2);
        double lz = front - 1.15;
        b.box(-hw, 0.9, lz - 0.2, -hw + 0.22, 1.35, lz + 0.2, RED_BOX).box(-hw + 0.22, 1.24, lz - 0.12, -hw + 0.225, 1.27, lz + 0.12, BLACK)
                .box(-hw + 0.22, 1.05, lz - 0.14, -hw + 0.226, 1.16, lz + 0.14, 0xF0D040);
        b.hit(-hw, 0.9, lz - 0.2, -hw + 0.24, 1.35, lz + 0.2);
        b.cur.slots = 9;
        b.spot(-hw + 0.11, 1.36, lz);
        // the sorters' desk panel
        b.part(Kind.PANEL, "TPO sorters' desk", 2);
        double pz0 = front - 0.85, pz1 = front - 0.25, px = -hw + 0.02;
        b.box(-hw, 0.95, pz0, px, 1.65, pz1, PANEL);
        b.hit(-hw, 0.95, pz0, px + 0.04, 1.65, pz1);
        b.ctl("tposcr", SCREEN, 2, px, (pz0 + pz1) / 2, 1.5, (pz1 - pz0) / 2 - 0.03, 0.11, SCREEN_BG, "TPO status: letters loose / sorted / bags / next drop", "tposcr").lines = 4;
        b.ctl("tposort", KEY, 2, px, pz0 + 0.1, 1.2, 0.035, 0.035, GREEN, "SORTING key: sorters on duty (letters sorted en route)", "tposort");
        b.ctl("tpoprint", PUSH, 2, px, pz0 + 0.3, 1.2, 0.03, 0.03, BLUE, "PRINT the TPO manifest", null);
        b.ctl("l_tpoon", LAMP, 2, px, pz0 + 0.45, 1.2, 0.016, 0.016, GREEN, "In service lamp (driverless train)", "tpoon");
        b.ctl("l_tposort", LAMP, 2, px, pz0 + 0.52, 1.2, 0.016, 0.016, AMBER, "Sorting lamp", "tposort");
        b.ctl("l_tpotray", LAMP, 2, px, pz0 + 0.45, 1.08, 0.016, 0.016, WHITE, "Loose letters waiting lamp", "tpotray");
    }

    private static void sleeper(B b, double rear, double front, double hw, double ceil) {
        double cor = -hw + 0.8;          // corridor on the -x side
        int n = (int) Math.floor((front - rear) / 2.05);
        double off = ((front - rear) - n * 2.05) / 2;
        for (int c = 0; c < n; c++) {
            double z0 = rear + off + c * 2.05, z1 = z0 + 2.05;
            b.part(Kind.WALL, "Compartment " + (c + 1), 3);
            b.box(cor, 0, z0, hw, ceil, z0 + 0.05, WALL).box(cor, 0, z1 - 0.05, hw, ceil, z1, WALL)
                    .box(cor, 0, z0, cor + 0.05, ceil, z0 + 0.3, WALL).box(cor, 0, z1 - 0.95, cor + 0.05, ceil, z1, WALL).box(cor, 2.0, z0 + 0.3, cor + 0.05, ceil, z1 - 0.95, WALL);
            // compartment number plate on the corridor side
            b.ctl("plate", DIGITS, 3, cor, z1 - 0.7, 1.75, 0.12, 0.05, SCREEN_BG, "Compartment " + (c + 1), "comp" + (c + 1));
            for (int lvl = 0; lvl < 2; lvl++) {
                int no = ++b.p.berths;
                Part p = b.part(Kind.BERTH, (lvl == 0 ? "Lower" : "Upper") + " berth " + no, 3);
                p.no = no;
                double y = lvl == 0 ? 0.45 : 1.38, bx0 = hw - 0.78;
                b.box(bx0, y - 0.08, z0 + 0.08, hw, y - 0.02, z1 - 0.08, WOOD)                   // berth frame
                        .box(bx0 + 0.02, y - 0.02, z0 + 0.1, hw - 0.02, y + 0.1, z1 - 0.1, WHITE) // mattress
                        .box(bx0 + 0.02, y + 0.1, z0 + 0.1, hw - 0.02, y + 0.14, z1 - 0.5, BLANKET)  // blanket
                        .box(bx0 + 0.15, y + 0.1, z1 - 0.45, hw - 0.15, y + 0.2, z1 - 0.12, PILLOW);   // pillow
                if (lvl == 0) b.box(bx0 + 0.3, 0, z0 + 0.3, bx0 + 0.36, y - 0.08, z0 + 0.36, FRAME).box(bx0 + 0.3, 0, z1 - 0.36, bx0 + 0.36, y - 0.08, z1 - 0.3, FRAME);
                else b.box(bx0 - 0.02, y + 0.14, z0 + 0.1, bx0 + 0.02, y + 0.4, z1 - 0.1, FRAME);          // safety rail
                b.hit(bx0, y - 0.08, z0 + 0.08, hw, y + 0.22, z1 - 0.08);
                b.sit((bx0 + hw) / 2, y + 0.12, (z0 + z1) / 2);
                // berth panel on the end wall by the pillow: reading light, night light, call, wake-up, lamps
                double pz = z1 - 0.05, py = y + 0.45;
                b.ctl("blight", PUSH, 1, pz, hw - 0.1, py, 0.025, 0.025, WHITE, "Berth reading light", "bl" + no);
                b.ctl("night", PUSH, 1, pz, hw - 0.2, py, 0.025, 0.025, BLUE, "Night light (blue)", "nl" + no);
                b.ctl("call", PUSH, 1, pz, hw - 0.3, py, 0.025, 0.025, AMBER, "Call the attendant", "bc" + no);
                b.ctl("wake", KEY, 1, pz, hw - 0.42, py, 0.03, 0.03, BLACK, "Wake-up call at 06:00 ON/OFF", "wk" + no);
                b.ctl("bclock", DIGITS, 1, pz, hw - 0.6, py, 0.08, 0.025, SCREEN_BG, "Berth clock / wake-up time", "bclk" + no);
                b.glow(hw - 0.14, py + 0.12, pz - 0.04, hw - 0.06, py + 0.16, pz, WHITE, "bl" + no);
                b.glow(hw - 0.24, py + 0.12, pz - 0.04, hw - 0.16, py + 0.16, pz, BLUE, "nl" + no);
            }
            // ladder, wash basin, rack over the door
            b.part(Kind.LADDER, "Ladder", 3);
            double lz = z0 + 0.35, lx = hw - 0.82;
            b.box(lx - 0.02, 0, lz - 0.2, lx, 1.4, lz - 0.17, STEEL).box(lx - 0.02, 0, lz + 0.17, lx, 1.4, lz + 0.2, STEEL);
            for (int r = 1; r < 5; r++) b.box(lx - 0.02, r * 0.28, lz - 0.17, lx, r * 0.28 + 0.03, lz + 0.17, STEEL);
            b.part(Kind.SINK, "Compartment wash basin", 0);
            double sx = (cor + hw - 0.8) / 2;
            b.box(sx - 0.2, 0.75, z0 + 0.05, sx + 0.2, 0.83, z0 + 0.4, WHITE).box(sx - 0.02, 0.83, z0 + 0.05, sx + 0.02, 0.98, z0 + 0.1, STEEL)
                    .box(sx - 0.2, 1.1, z0 + 0.05, sx + 0.2, 1.6, z0 + 0.07, GLASS);
            b.ctl("tap", PUSH, 0, z0 + 0.1, sx, 0.93, 0.025, 0.025, STEEL, "TAP (bottle / bucket fills)", null);
            b.hit(sx - 0.2, 0.7, z0 + 0.05, sx + 0.2, 1.0, z0 + 0.4);
            b.part(Kind.RACK, "Compartment rack", 2);
            b.box(cor + 0.05, 2.0, z0 + 0.3, cor + 0.6, 2.03, z1 - 0.95, FRAME);
            b.hit(cor + 0.05, 1.95, z0 + 0.3, cor + 0.6, 2.1, z1 - 0.95);
            b.cur.slots = 9;
            for (int i = 0; i < 3; i++) b.spot(cor + 0.32, 2.035, z0 + 0.45 + i * 0.25);
        }
    }

    /** the part + control a ray (offset space, from o along d) hits first within maxT, or null: {part, btn or -1, t} */
    public static double[] pick(Plan p, double[] o, double[] d, double maxT) {
        double best = maxT;
        int bp = -1, bb = -1;
        for (Part pt : p.parts) {
            for (int i = 0; i < pt.btns.size(); i++) {
                Btn b = pt.btns.get(i);
                if (b.type == LAMP || (b.type == SCREEN && pt.kind != Kind.TILL && pt.kind != Kind.WC_DOOR) || (b.type == DIGITS && pt.kind != Kind.TILL)) continue;
                double t = ray(b.box, o, d, 0.012);
                if (t >= 0 && t < best) { best = t; bp = pt.idx; bb = i; }
            }
        }
        for (Part pt : p.parts) {
            if (pt.hit == null) continue;
            double t = ray(pt.hit, o, d, 0);
            if (t >= 0 && t < best - 0.03) { best = t; bp = pt.idx; bb = -1; }
        }
        return bp < 0 ? null : new double[]{bp, bb, best};
    }

    /** slab test; grow pads small controls so they are easy to hit */
    static double ray(double[] b, double[] o, double[] d, double grow) {
        double t0 = 0, t1 = Double.MAX_VALUE;
        for (int a = 0; a < 3; a++) {
            double lo = b[a] - grow, hi = b[a + 3] + grow;
            if (Math.abs(d[a]) < 1e-9) { if (o[a] < lo || o[a] > hi) return -1; continue; }
            double ta = (lo - o[a]) / d[a], tb = (hi - o[a]) / d[a];
            if (ta > tb) { double x = ta; ta = tb; tb = x; }
            t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
            if (t0 > t1) return -1;
        }
        return t0;
    }

    /** world point -> offset space of a car (pitch ignored, like IR's riders) */
    public static double[] toLocal(EntityRollingStock s, double x, double y, double z) {
        cam72cam.mod.math.Vec3d p = s.getPosition();
        cam72cam.mod.math.Vec3d r = new cam72cam.mod.math.Vec3d(x - p.x, y - p.y, z - p.z).rotateYaw(s.getRotationYaw());
        return new double[]{r.x, r.y, r.z};
    }

    public static double[] dirLocal(EntityRollingStock s, double x, double y, double z) {
        cam72cam.mod.math.Vec3d r = new cam72cam.mod.math.Vec3d(x, y, z).rotateYaw(s.getRotationYaw());
        return new double[]{r.x, r.y, r.z};
    }

    public static double[] toWorld(EntityRollingStock s, double x, double y, double z) {
        cam72cam.mod.math.Vec3d p = s.getPosition();
        cam72cam.mod.math.Vec3d r = new cam72cam.mod.math.Vec3d(x, y, z).rotateYaw(-s.getRotationYaw());
        return new double[]{r.x + p.x, r.y + p.y, r.z + p.z};
    }
}
