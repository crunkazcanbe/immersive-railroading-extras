package com.dogpound.railmap.wire;

import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.Reading;

import java.util.Map;

/**
 * A machine with a control terminal (requested feature). Right-click the machine with
 * Instrument Wire to land that colour on its terminal; then gauges, screens, Control Buttons and the Data Link on that
 * colour read and operate it exactly like its own panel. IR Extras grid machines and Pride Industry plants both have one.
 */
public interface IWiredMachine {
    /** colour landed on the control terminal, -1 = none */
    int ctlWire();
    void setCtlWire(int c);

    String wiredName();
    /** its own control panel (the buttons a Control Button can copy); null = none */
    Panel wiredPanel();
    /** press one of its panel controls, exactly as its own button would; returns what the panel says */
    String wiredPress(String id, String who);
    /** where a selector / switch stands (its panel value), for the Control Button's indicator */
    int wiredState(String id);
    /** 1 running / live (green), 2 stopped / dead (red), 3 fault / tripped (flashing amber) */
    int wiredLamp();
    /** fill a gauge / screen reading for this kind of instrument */
    void wiredRead(Reading r, BlockInstrument.Inst k);
    /** everything a computer can read about it */
    Map<String, Object> wiredInfo();
}
