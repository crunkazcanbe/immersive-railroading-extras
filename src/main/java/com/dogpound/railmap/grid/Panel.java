package com.dogpound.railmap.grid;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The real controls on the front of each grid machine (requested feature). One layout per machine kind, in face pixels as you
 * look at the machine: u = 0 at your left … 16 at your right, v = 0 at the bottom … 16 at the top. The panel is drawn
 * live by GridPanelRenderer (lamps light, knobs turn, levers swing, buttons push in) and clicks are matched here.
 * Track Feeder, Feeder Cable and Surge Arrester have no controls in real life, so they get none (the arrester just
 * shows its surge counter, as real ones do).
 */
public final class Panel {
    public enum T {
        PUSH,       // momentary push button (coloured cap in a bezel)
        MUSHROOM,   // emergency stop: red mushroom head on a yellow plate, latches in, click again to twist-release
        SELECTOR,   // rotary selector switch with labelled positions
        KEY,        // key switch (LOCAL / REMOTE)
        LAMP,       // indicator lamp, lit by a state value
        GAUGE,      // analogue meter: white dial, needle
        DIGITS,     // display window
        LEVER,      // big operating handle that swings between positions
        FLAG,       // mechanical ON / OFF indicator window
        SYNCHRO,    // synchroscope: a pointer that turns with the phase difference; 12 o'clock = in phase
        LABEL       // engraved label plate
    }

    /** one control */
    public static final class C {
        public final String id;       // state key it reads / writes, and what a click sends
        public final T t;
        public final double u, v, w, h; // centre u/v (lever: pivot), size
        public final int colour;
        public final String label;
        public final String[] positions; // selector / key positions
        public String fmt = "%d";      // digits: format of the value
        public double scale = 1;       // digits / gauge: value * scale shown
        public int max = 100;          // gauge full scale
        public boolean blink;          // lamp flashes when lit

        C(String id, T t, double u, double v, double w, double h, int colour, String label, String... positions) {
            this.id = id; this.t = t; this.u = u; this.v = v; this.w = w; this.h = h;
            this.colour = colour; this.label = label; this.positions = positions;
        }

        public boolean clickable() { return t == T.PUSH || t == T.MUSHROOM || t == T.SELECTOR || t == T.KEY || t == T.LEVER; }

        public boolean hit(double hu, double hv) {
            double hw = w / 2 + 0.25, hh = h / 2 + 0.25;             // a little slack: these are small targets
            if (t == T.LEVER) return Math.abs(hu - u) <= hw + 1 && hv >= v - h - 1 && hv <= v + h + 1;
            return Math.abs(hu - u) <= hw && Math.abs(hv - v) <= hh;
        }
    }

    /** the cabinet the controls sit on: drawn as a plate standing proud of the machine front (z = face) */
    public final double u0, v0, u1, v1, face;
    public final int plate;
    public final String title;
    public final List<C> cs = new ArrayList<>();

    private Panel(String title, double u0, double v0, double u1, double v1, double face, int plate) {
        this.title = title; this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1; this.face = face; this.plate = plate;
    }

    private C add(C c) { cs.add(c); return c; }
    private C push(String id, double u, double v, int col, String label) { return add(new C(id, T.PUSH, u, v, 1.6, 1.6, col, label)); }
    private C lamp(String id, double u, double v, int col, String label) { return add(new C(id, T.LAMP, u, v, 1.1, 1.1, col, label)); }
    private C sel(String id, double u, double v, String label, String... pos) { return add(new C(id, T.SELECTOR, u, v, 1.9, 1.9, 0x202020, label, pos)); }
    private C key(String id, double u, double v, String label, String... pos) { return add(new C(id, T.KEY, u, v, 1.7, 1.7, 0xC0A040, label, pos)); }
    private C gauge(String id, double u, double v, double r, String label, int max) { C c = add(new C(id, T.GAUGE, u, v, r * 2, r * 2, 0xF2F0E6, label)); c.max = max; return c; }
    private C digits(String id, double u, double v, double w, int col, String fmt, double scale) { C c = add(new C(id, T.DIGITS, u, v, w, 1.4, col, "")); c.fmt = fmt; c.scale = scale; return c; }
    private C label(double u, double v, String text) { return add(new C("", T.LABEL, u, v, 0, 0, 0x101010, text)); }

    public static final int GREEN = 0x1FA34A, RED = 0xD02020, AMBER = 0xF0A020, WHITE = 0xF0F0F0, BLUE = 0x2050D0, BLACK = 0x1A1A1A, YELLOW = 0xF0D020;
    private static final int GREY_7035 = 0xC5C7C4, GREY_DARK = 0x4A4F54, CREAM = 0xE4DCC4, GENSET = 0x2F3A33;

    private static final Map<GridKind, Panel> PANELS = new EnumMap<>(GridKind.class);

    public static Panel of(GridKind k) { return PANELS.get(k); }

    /** the control at face pixel (u, v), or null */
    public static C hit(GridKind k, double u, double v) {
        Panel p = PANELS.get(k);
        if (p == null) return null;
        for (C c : p.cs) if (c.clickable() && c.hit(u, v)) return c;
        return null;
    }

    static {
        // ---- Power / Traction Transformer: the tap-changer control kiosk on the tank (RAL 7035 cubicle)
        for (GridKind k : new GridKind[]{GridKind.TRANSFORMER, GridKind.TRACTION}) {
            Panel p = new Panel(k == GridKind.TRACTION ? "TRACTION TX - OLTC" : "TRANSFORMER - OLTC", 3, 1.5, 13, 14.5, -1.0, GREY_7035);
            p.label(8, 13.6, p.title);
            p.digits("tap", 5.6, 12.1, 3.6, 0xFF5020, "TAP %d", 1);
            p.digits("volts", 10.4, 12.1, 4.2, 0x40FF60, "%.1fkV", 0.1);
            p.push("raise", 5.0, 9.8, BLACK, "RAISE");
            p.push("lower", 5.0, 7.6, BLACK, "LOWER");
            p.sel("avr", 8.0, 8.7, "AVR", "MAN", "AUTO");
            p.sel("cool", 11.0, 8.7, "FANS", "OFF", "AUTO", "ON");
            p.gauge("temp", 5.6, 4.4, 1.7, "OIL °C", 120);
            p.lamp("fanrun", 9.0, 5.6, GREEN, "FANS RUN");
            p.lamp("alarm", 11.0, 5.6, RED, "ALARM").blink = true;
            p.lamp("trip", 9.0, 3.6, AMBER, "O/TEMP");
            p.push("reset", 11.0, 3.6, BLUE, "RESET");
            PANELS.put(k, p);
        }
        // ---- Traction Rectifier: rectifier control panel (DC system voltage, DC circuit breaker)
        {
            Panel p = new Panel("TRACTION RECTIFIER", 2.5, 1.5, 13.5, 14.5, -1.0, GREY_7035);
            p.label(8, 13.6, p.title);
            p.digits("dcv", 5.8, 12.1, 4.2, 0x40FF60, "%dV DC", 1);
            p.digits("dca", 10.6, 12.1, 4.2, 0x40FF60, "%d%% LD", 1);
            p.sel("vsel", 5.4, 8.8, "DC VOLTS", "600", "750", "1500", "3000");
            p.push("dcclose", 9.3, 9.8, GREEN, "CLOSE");
            p.push("dcopen", 11.4, 9.8, RED, "OPEN");
            p.lamp("dcon", 9.3, 7.6, RED, "DC ON");
            p.lamp("dcoff", 11.4, 7.6, GREEN, "DC OFF");
            p.gauge("load", 5.4, 4.3, 1.6, "DC A", 100);
            p.lamp("fault", 9.3, 4.9, AMBER, "FAULT").blink = true;
            p.lamp("fans", 11.4, 4.9, GREEN, "COOLING");
            p.push("reset", 10.3, 2.8, BLUE, "RESET");
            PANELS.put(GridKind.RECTIFIER, p);
        }
        // ---- Inverter: a central inverter's door - LCD, RUN / STOP selector, status LEDs, e-stop (no other buttons in real life)
        {
            Panel p = new Panel("INVERTER", 2.5, 1.5, 13.5, 14.5, -1.0, GREY_7035);
            p.label(8, 13.6, "INVERTER  DC → AC");
            p.digits("vin", 5.8, 12.1, 4.2, 0x40FF60, "%dV DC", 1);
            p.digits("vout", 10.6, 12.1, 4.2, 0x40FF60, "%dV AC", 1);
            p.digits("kw", 8.0, 10.4, 5.0, 0x40FF60, "%d kW", 1);
            p.sel("run", 4.6, 7.6, "RUN", "STOP", "RUN");
            p.lamp("ldc", 7.6, 8.0, GREEN, "DC IN");
            p.lamp("lrun", 9.6, 8.0, GREEN, "AC OUT");
            p.lamp("lfault", 11.6, 8.0, RED, "FAULT").blink = true;
            p.gauge("load", 5.4, 3.9, 1.6, "LOAD %", 150);
            p.push("reset", 8.4, 4.2, BLUE, "RESET");
            p.add(new C("estop", T.MUSHROOM, 11.2, 3.9, 2.4, 2.4, RED, "E-STOP"));
            PANELS.put(GridKind.INVERTER, p);
        }
        // ---- Electric Motor: the local starter / control station on the front of the motor
        {
            Panel p = new Panel("MOTOR", 2.5, 1.5, 13.5, 14.5, -1.0, GREY_DARK);
            p.label(8, 13.6, "MOTOR STARTER");
            p.digits("out", 8.0, 12.1, 5.0, 0x40FF60, "%s", 1);
            p.gauge("amps", 5.2, 9.0, 1.6, "A % FLC", 150);
            p.sel("hoa", 10.6, 9.2, "MODE", "HAND", "OFF", "AUTO");
            p.push("start", 4.6, 5.8, GREEN, "START");
            p.push("stop", 7.0, 5.8, RED, "STOP");
            p.push("reset", 9.4, 5.8, BLUE, "RESET");
            p.lamp("lrun", 4.6, 3.4, GREEN, "RUN");
            p.lamp("lstart", 7.0, 3.4, AMBER, "START").blink = true;
            p.lamp("ltrip", 9.4, 3.4, RED, "O/L TRIP");
            p.add(new C("estop", T.MUSHROOM, 12.0, 4.6, 2.0, 2.0, RED, "E-STOP"));
            PANELS.put(GridKind.MOTOR, p);
        }
        // ---- DC-DC Converter: a rack converter's front - two meters, ENABLE switch, two LEDs and a reset
        {
            Panel p = new Panel("DC-DC", 3.0, 2.0, 13.0, 14.0, -1.0, GREY_DARK);
            p.label(8, 13.2, "DC-DC CONVERTER");
            p.digits("vin", 5.8, 11.6, 4.0, 0xFFB040, "IN %dV", 1);
            p.digits("vout", 10.4, 11.6, 4.0, 0x40FF60, "OUT %dV", 1);
            p.sel("run", 5.4, 7.8, "ENABLE", "OFF", "ON");
            p.lamp("lrun", 9.0, 8.4, GREEN, "OUTPUT");
            p.lamp("lfault", 11.2, 8.4, RED, "FAULT").blink = true;
            p.gauge("load", 6.0, 4.0, 1.5, "LOAD %", 150);
            p.push("reset", 10.2, 4.6, BLUE, "RESET");
            PANELS.put(GridKind.DCDC, p);
        }
        // ---- Circuit Breaker: switchgear front (recessed cubicle face at model z = 4): the big operating handle
        {
            Panel p = new Panel("FEEDER CB", 2.2, 1.2, 13.8, 15.4, 3.6, CREAM);
            p.label(8, 14.8, "FEEDER CIRCUIT BREAKER");
            p.lamp("lclosed", 4.5, 13.3, RED, "CLOSED");
            p.lamp("lopen", 8.0, 13.3, GREEN, "OPEN");
            p.lamp("ltrip", 11.5, 13.3, AMBER, "TRIPPED").blink = true;
            p.add(new C("handle", T.LEVER, 8.0, 8.6, 1.4, 4.2, 0xC02020, "ON / OFF", "ON", "OFF"));
            p.add(new C("flag", T.FLAG, 8.0, 3.6, 2.6, 1.3, 0, ""));
            p.push("close", 11.6, 10.6, GREEN, "CLOSE");
            p.push("open", 11.6, 8.4, RED, "OPEN");
            p.push("reset", 11.6, 6.2, BLUE, "RESET");
            p.key("lr", 4.4, 10.4, "CONTROL", "LOCAL", "REMOTE");
            p.lamp("spring", 4.4, 7.6, WHITE, "SPRING");
            p.gauge("amps", 4.4, 4.2, 1.6, "AMPS", 100);
            p.lamp("lockout", 11.6, 3.8, AMBER, "LOCKOUT");
            PANELS.put(GridKind.BREAKER, p);
        }
        // ---- Disconnector: operating mechanism box on the support with a swing handle + padlock hasp
        {
            Panel p = new Panel("DISCONNECTOR", 4.5, 1.0, 11.5, 8.8, 5.0, GREY_DARK);
            p.add(new C("handle", T.LEVER, 8.0, 4.8, 1.2, 3.2, 0xF0D020, "OPERATE", "CLOSED", "OPEN"));
            p.add(new C("lock", T.PUSH, 10.4, 2.2, 1.4, 1.4, 0xB08A30, "PADLOCK"));
            p.lamp("closed", 5.8, 7.6, RED, "");
            p.lamp("open", 10.2, 7.6, GREEN, "");
            PANELS.put(GridKind.ISOLATOR, p);
        }
        // ---- Earthing Switch: the neutral earth switch's operating handle
        {
            Panel p = new Panel("EARTH", 4, 1.5, 12, 11, -1.0, 0x2A6E2A);
            p.label(8, 10.3, "NEUTRAL EARTH");
            p.add(new C("handle", T.LEVER, 8.0, 5.8, 1.3, 3.4, 0x1A9A3A, "EARTH", "ON", "OFF"));
            p.lamp("earthed", 5.6, 2.6, GREEN, "EARTHED");
            p.lamp("unearthed", 10.4, 2.6, RED, "OFF").blink = true;
            PANELS.put(GridKind.EARTHING, p);
        }
        // ---- Grid Intake: the incomer's main switch on the LV cubicle
        {
            Panel p = new Panel("INCOMER", 3, 1.5, 13, 14, -1.0, GREY_7035);
            p.label(8, 13.2, "GRID INCOMER");
            p.digits("import", 8, 11.6, 7, 0x40FF60, "IN %d FE/t", 1);
            p.sel("main", 5.5, 8.0, "MAIN SWITCH", "OFF", "ON");
            p.lamp("supply", 9.5, 9.0, WHITE, "SUPPLY");
            p.lamp("on", 11.5, 9.0, RED, "MAIN ON");
            p.gauge("stored", 10.5, 4.6, 1.8, "STORE %", 100);
            PANELS.put(GridKind.INTAKE, p);
        }
        // ---- Energy Meter: the two real buttons (scroll display, sealed demand reset) under the glass
        {
            Panel p = new Panel("METER", 4.2, 2.0, 11.8, 13.6, 3.0, 0x2A2A2A);
            p.digits("page", 8, 11.6, 6.6, 0x50FF70, "%s", 1);
            p.label(8, 10.2, "kWh METER");
            p.push("display", 6.2, 7.6, BLACK, "DISPLAY");
            p.push("demand", 9.8, 7.6, 0xA02020, "DEMAND RST");
            p.lamp("pulse", 8.0, 4.4, RED, "IMP");
            PANELS.put(GridKind.METER, p);
        }
        // ---- Battery Storage: the inverter / BMS panel on the container door
        {
            Panel p = new Panel("BESS", 2.5, 1.5, 13.5, 14.5, -1.0, GREY_7035);
            p.label(8, 13.6, "BATTERY INVERTER");
            p.digits("soc", 8, 12.1, 6, 0x40FF60, "SOC %d%%", 1);
            p.push("start", 4.6, 9.6, GREEN, "START");
            p.push("stop", 6.8, 9.6, RED, "STOP");
            p.sel("mode", 10.6, 9.3, "MODE", "CHARGE", "AUTO", "DISCH");
            p.lamp("run", 4.6, 7.2, GREEN, "RUN");
            p.lamp("chg", 6.8, 7.2, BLUE, "CHG");
            p.lamp("dis", 9.0, 7.2, AMBER, "DISCH");
            p.lamp("fault", 11.2, 7.2, RED, "FAULT").blink = true;
            p.gauge("socg", 5.4, 4.0, 1.7, "SOC", 100);
            p.add(new C("estop", T.MUSHROOM, 10.4, 3.9, 2.4, 2.4, RED, "E-STOP"));
            PANELS.put(GridKind.BATTERY, p);
        }
        // ---- Grid Control Panel (SCADA desk): the desk's alarm handling buttons
        {
            Panel p = new Panel("SCADA", 3.5, 1.2, 12.5, 7.4, -1.0, 0x30343A);
            p.digits("count", 8, 6.4, 7.4, 0x50FF70, "%s", 1);
            p.lamp("alarm", 5.0, 4.0, RED, "ALARM").blink = true;
            p.lamp("healthy", 7.0, 4.0, GREEN, "ALL LIVE");
            p.push("ack", 9.4, 3.6, AMBER, "ACK");
            p.push("lamptest", 11.4, 3.6, WHITE, "LAMP TEST");
            PANELS.put(GridKind.SCADA, p);
        }
        // ---- Diesel Generator / Steam Turbine: the genset controller (AMF style), replaces the old 4-button plate
        for (GridKind k : new GridKind[]{GridKind.DIESEL, GridKind.TURBINE}) {
            Panel p = new Panel(k == GridKind.DIESEL ? "GENSET CONTROLLER" : "TURBINE CONTROL", 1.6, 3.6, 14.4, 15.6, -1.0, GENSET);
            p.label(8, 15.0, p.title);
            p.digits("out", 4.9, 13.6, 4.0, 0x40FF60, "%d FE/t", 1);
            p.digits("hz", 8.9, 13.6, 3.0, 0xFFB040, "%.1fHz", 0.1);
            p.gauge("rpm", 11.9, 13.2, 1.5, "RPM %", 120);
            p.sel("mode", 3.6, 10.6, "MODE", "OFF", "MAN", "AUTO");
            p.push("start", 6.4, 11.0, GREEN, "START");
            p.push("stop", 8.6, 11.0, RED, "STOP");
            p.push("up", 10.8, 11.0, BLACK, "GOV ▲");
            p.push("down", 12.8, 11.0, BLACK, "GOV ▼");
            p.push("gcbclose", 6.4, 8.4, GREEN, "GCB ON");
            p.push("gcbopen", 8.6, 8.4, RED, "GCB OFF");
            p.push("reset", 10.8, 8.4, BLUE, "RESET");
            p.add(new C("sync", T.SYNCHRO, 13.0, 8.4, 2.2, 2.2, 0xF2F0E6, "SYNC"));
            p.lamp("lrun", 3.2, 7.4, GREEN, "RUN");
            p.lamp("lgcb", 4.8, 7.4, RED, "GCB");
            p.lamp("lfault", 3.2, 5.6, AMBER, "FAULT").blink = true;
            p.lamp("llow", 4.8, 5.6, AMBER, k == GridKind.DIESEL ? "LOW FUEL" : "LOW STM");
            p.gauge("fuel", 8.0, 5.4, 1.5, k == GridKind.DIESEL ? "FUEL" : "STEAM", 100);
            p.add(new C("estop", T.MUSHROOM, 12.2, 5.6, 2.4, 2.4, RED, "E-STOP"));
            PANELS.put(k, p);
        }
        // ---- Solar Array: the string inverter's rotary DC isolator + its display button
        {
            Panel p = new Panel("INVERTER", 9.5, 1.2, 15.2, 6.4, -0.6, 0xE6E6E6);
            p.digits("out", 12.35, 5.4, 5.0, 0x40FF60, "%dFE/t", 1);
            p.sel("dc", 11.0, 3.0, "DC ISOL", "OFF", "ON");
            p.lamp("grid", 13.6, 3.6, GREEN, "GRID");
            p.lamp("fault", 13.6, 2.0, RED, "FAULT");
            PANELS.put(GridKind.SOLAR, p);
        }
        // ---- Wind Turbine: the tower-base controller cabinet
        {
            Panel p = new Panel("WTG", 4.5, 1.2, 11.5, 9.0, -1.0, 0xDADADA);
            p.label(8, 8.4, "TURBINE CTRL");
            p.push("start", 6.0, 6.6, GREEN, "START");
            p.push("stop", 8.0, 6.6, RED, "STOP");
            p.push("reset", 10.0, 6.6, BLUE, "RESET");
            p.lamp("run", 6.0, 4.6, GREEN, "RUN");
            p.lamp("fault", 8.0, 4.6, AMBER, "FEATHER");
            p.add(new C("estop", T.MUSHROOM, 9.6, 3.0, 2.0, 2.0, RED, "E-STOP"));
            p.digits("out", 6.2, 2.6, 3.0, 0x40FF60, "%d", 1);
            PANELS.put(GridKind.WIND, p);
        }
        // ---- Capacitor Bank: power-factor controller
        {
            Panel p = new Panel("PFC", 4, 1.5, 12, 10.5, -1.0, GREY_7035);
            p.label(8, 9.8, "PF CONTROLLER");
            p.digits("pf", 8, 8.4, 6, 0x40FF60, "PF %.2f", 0.01);
            p.sel("auto", 5.6, 5.8, "MODE", "MAN", "AUTO");
            p.push("stepin", 8.6, 6.4, GREEN, "STEP+");
            p.push("stepout", 10.6, 6.4, RED, "STEP-");
            for (int i = 0; i < 4; i++) p.lamp("s" + (i + 1), 6.2 + i * 1.6, 3.0, GREEN, i == 0 ? "STEPS" : "");
            PANELS.put(GridKind.CAPACITOR, p);
        }
        // ---- Protection Relay: relay front - LEDs, RESET, TEST, pick-up setting
        {
            Panel p = new Panel("RELAY", 3.4, 2.0, 12.6, 14.0, 4.6, 0x24272B);
            p.label(8, 13.2, "OVERCURRENT 50/51");
            p.lamp("healthy", 5.0, 11.4, GREEN, "HEALTHY");
            p.lamp("pickup", 8.0, 11.4, AMBER, "PICKUP").blink = true;
            p.lamp("trip", 11.0, 11.4, RED, "TRIP");
            p.digits("set", 8.0, 9.2, 6.4, 0xFF8040, "I> %d%%", 1);
            p.sel("pickupset", 6.0, 6.2, "SETTING", "50", "80", "100", "120");
            p.push("test", 9.2, 6.8, AMBER, "TEST");
            p.push("reset", 11.2, 6.8, BLUE, "RESET");
            p.gauge("load", 10.2, 3.8, 1.4, "% I>", 150);
            PANELS.put(GridKind.RELAY, p);
        }
        // ---- Surge Arrester: real ones have no buttons, just a surge counter
        {
            Panel p = new Panel("COUNTER", 6.0, 1.0, 10.0, 4.6, -0.6, 0x3C3C3C);
            p.digits("surges", 8.0, 2.8, 3.4, 0xF0F0F0, "%04d", 1);
            PANELS.put(GridKind.ARRESTER, p);
        }
    }
}
