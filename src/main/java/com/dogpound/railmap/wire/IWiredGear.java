package com.dogpound.railmap.wire;

import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.Reading;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Railway gear with a {@link Terminal}: signals, crossings, track circuits, station and lineside equipment. Most of it
 * has no buttons of its own in real life (a signal head, a detector), so the defaults are monitoring only; gear that
 * really has a control panel (relay case, crossing controller, ticket gate...) overrides wiredPanel / wiredPress.
 */
public interface IWiredGear extends IWiredMachine {
    Terminal term();
    /** save + send to the client after the terminal changed */
    void gearChanged();
    /** what it is doing now, one short line ("CLEAR", "gates down", "occupied") */
    String gearState();
    /** extra readings for computers and the console */
    default void gearInfo(Map<String, Object> m) {}

    /** the two wiring lines every gear's settings screen shows */
    static void settings(java.util.List<com.dogpound.railmap.settings.Setting> l, String cat, IWiredGear g) {
        l.add(com.dogpound.railmap.settings.Setting.info(cat, "12 V supply", g.term().status()));
        l.add(com.dogpound.railmap.settings.Setting.info(cat, "Control wire", g.ctlWire() < 0 ? "none landed (right-click with Instrument Wire)" : WireNet.name(g.ctlWire()) + " - on the console's WIRED page"));
    }

    @Override default int ctlWire() { return term().ctl; }
    @Override default void setCtlWire(int c) { term().ctl = c; gearChanged(); }
    @Override default Panel wiredPanel() { return null; }
    @Override default String wiredPress(String id, String who) { return "§7The " + wiredName() + " has no remote controls - it is monitored only"; }
    @Override default int wiredState(String id) { return 0; }
    @Override default int wiredLamp() { return term().burnt || !term().powered() ? 3 : 1; }   // lost its 12 V = a fault the console alarms on

    @Override
    default void wiredRead(Reading r, BlockInstrument.Inst k) {
        Terminal t = term();
        if (!t.powered()) { r.unpowered(t.burnt ? 2 : 0, t.what); r.line1 = wiredName(); return; }
        r.value = 1; r.full = 1; r.unit = ""; r.lamp = 1;
        r.status = gearState(); r.line1 = wiredName(); r.line2 = gearState(); r.line3 = "12 V ok";
    }

    @Override
    default Map<String, Object> wiredInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", wiredName());
        m.put("powered", term().powered());
        m.put("burnt", term().burnt);
        m.put("supply", term().what);
        m.put("state", gearState());
        gearInfo(m);
        return m;
    }
}
