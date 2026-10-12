package com.dogpound.railmap.grid;

import java.util.Map;

/**
 * Anything that carries a real control panel (requested feature). Grid machines implement it; Pride Industry's multiblock controllers do too.
 */
public interface PanelHost {
    /** a control's position / reading (lamps 0/1, selectors 0..n, gauges, digits) */
    int pv(String key);
    /** a display's text */
    String ptext(String key);
    /** was this push button pressed within the last few ticks (it is drawn pushed in) */
    boolean pressedRecently(String id, long now);
    /** client-side easing state for needles and levers */
    Map<String, float[]> anim();
}
