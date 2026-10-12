package com.dogpound.railmap.block;

import com.dogpound.railmap.grid.Panel;

/**
 * The control desk of the Control Center console (requested feature). Face pixels across the
 * whole 3-block desk: u 0..48 from the operator's left, v 0..12 from the front edge up the slope.
 *
 *  TRAINS  - pick a train, its name + speed/status screens, moving/held/protection lamps, HOLD / RELEASE / HORN / SEND
 *            HOME, and an emergency-stop mushroom for that train
 *  DISPLAY - the big screen's page selector (MAP / TRAINS / POWER / ALARMS), zoom + / - / fit, rescan, the clock,
 *            and the CONTROL key: nothing on the desk operates with the key at OFF
 *  SYSTEM  - supply voltage / load / alarm-count screens, AC / 12 V / CBTC / ROUTES / ALARM / HEALTHY lamps, the CBTC
 *            key (moving block on every line), ACK, LAMP TEST, CANCEL ROUTES and the latching ALL STOP mushroom
 */
public final class ConsolePanel {
    private ConsolePanel() {}

    public static final double FACE = -0.35;
    public static final Panel DESK = build();

    private static Panel build() {
        Panel p = new Panel("CONTROL CENTER", 0, 0, 48, 12, FACE, 0xC5C7C4);

        // ---- TRAINS (left third)
        p.label(8, 11.35, "TRAINS");
        p.digits("trName", 8, 9.9, 13, Panel.AMBER, "%s", 1);
        p.digits("trInfo", 8, 8.3, 13, Panel.GREEN, "%s", 1);
        p.push("trPrev", 2.2, 6.0, Panel.WHITE, "<");
        p.push("trNext", 4.8, 6.0, Panel.WHITE, ">");
        p.lamp("trMove", 8.2, 6.2, Panel.GREEN, "MOVING");
        p.lamp("trHeld", 11.0, 6.2, Panel.AMBER, "HELD");
        p.lamp("trProt", 13.8, 6.2, Panel.RED, "ATP");
        p.push("trHold", 2.2, 2.6, Panel.AMBER, "HOLD");
        p.push("trGo", 5.3, 2.6, Panel.GREEN, "GO");
        p.push("trHorn", 8.4, 2.6, Panel.BLUE, "HORN");
        p.push("trHome", 11.0, 2.6, Panel.WHITE, "HOME");
        p.add(new Panel.C("trStop", Panel.T.MUSHROOM, 14.0, 2.9, 1.8, 1.8, Panel.RED, "STOP"));

        // ---- DISPLAY (middle third)
        p.label(24, 11.35, "DISPLAY");
        p.digits("clock", 24, 9.9, 7, Panel.GREEN, "%s", 1);
        p.sel("page", 19.5, 5.4, "SCREEN", "MAP", "TRN", "PWR", "ALM");
        p.push("zoomIn", 24.5, 6.3, Panel.WHITE, "ZOOM+");
        p.push("zoomOut", 27.5, 6.3, Panel.WHITE, "ZOOM-");
        p.push("fit", 30.0, 6.3, Panel.WHITE, "FIT");
        p.push("rescan", 24.5, 2.6, Panel.WHITE, "SCAN");
        p.lamp("lpScan", 27.5, 2.8, Panel.BLUE, "LIVE");
        p.key("ctl", 30.3, 2.1, "CONTROL", "OFF", "ON");

        // ---- SYSTEM (right third)
        p.label(40, 11.35, "SYSTEM");
        p.digits("supply", 35.6, 9.9, 4.8, Panel.GREEN, "%s", 1);
        p.digits("load", 40.6, 9.9, 4.2, Panel.AMBER, "%s", 1);
        p.digits("alarms", 45.2, 9.9, 3.6, Panel.RED, "%s", 1);
        p.lamp("lpAC", 34.4, 7.4, Panel.GREEN, "AC");
        p.lamp("lp12", 36.6, 7.4, Panel.GREEN, "12V");
        p.lamp("lpCbtc", 38.8, 7.4, Panel.BLUE, "CBTC");
        p.lamp("lpRoute", 41.0, 7.4, Panel.WHITE, "ROUTE");
        p.lamp("lpAlarm", 43.4, 7.4, Panel.RED, "ALARM").blink = true;
        p.lamp("lpOk", 45.8, 7.4, Panel.GREEN, "OK");
        p.key("cbtc", 34.8, 3.4, "CBTC", "OFF", "ON");
        p.push("ack", 38.6, 4.2, Panel.AMBER, "ACK");
        p.push("ltest", 41.2, 4.2, Panel.WHITE, "TEST");
        p.push("cancel", 38.6, 1.4, Panel.RED, "ROUTES");
        p.add(new Panel.C("allStop", Panel.T.MUSHROOM, 45.0, 3.0, 2.6, 2.6, Panel.RED, "ALL STOP"));
        return p;
    }
}
