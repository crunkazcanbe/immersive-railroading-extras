package com.dogpound.railmap.signal;

/** A trackside piece whose size can be dialled in with the Signal Wrench (sneak-right-click). */
public interface IScalable {
    float MIN = 0.5f, MAX = 3.0f;

    float scale();

    /** Server side: clamps, saves and syncs to clients. */
    void setScale(float scale);

    static float clamp(float s) {
        return Float.isNaN(s) ? 1f : Math.max(MIN, Math.min(MAX, s));
    }
}
