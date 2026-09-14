package com.dogpound.railmap.signal;

/**
 * The signal hardware families RailMap ships. They all run the same block logic — only the
 * way an {@link Aspect} is displayed differs, which is what {@link #render} names.
 */
public enum SignalStyle {
    /** US&S / GRS style colour-light head: three lamps per head, hooded, on a mast. */
    COLOR_LIGHT("color_light", "Color Light Signal", Render.LAMPS, 3, 4.0f),
    /** One lamp per head that changes colour behind a lens. 1930s–70s American standard. */
    SEARCHLIGHT("searchlight", "Searchlight Signal", Render.SEARCHLIGHT, 2, 4.0f),
    /** Pennsylvania Railroad: rows of amber lamps; the ROW's angle is the aspect. */
    POSITION_LIGHT("position_light", "Position Light Signal (PRR)", Render.POSITION, 2, 4.0f),
    /** Baltimore &amp; Ohio: colour-position — coloured lamp pairs at angles, plus an orbital. */
    COLOR_POSITION("color_position", "Color Position Light (B&O)", Render.COLOR_POSITION, 2, 4.0f),
    /** Ground-level signal for yards, sidings and interlocking exits. */
    DWARF("dwarf", "Dwarf Signal", Render.LAMPS, 1, 0f),
    /** Upper-quadrant semaphore: the blade angle is the aspect, with a coloured spectacle lens. */
    SEMAPHORE("semaphore", "Semaphore Signal", Render.SEMAPHORE, 2, 4.0f);

    /** How the client draws an aspect for this family. */
    public enum Render { LAMPS, SEARCHLIGHT, POSITION, COLOR_POSITION, SEMAPHORE }

    public final String id;
    public final String label;
    public final Render render;
    /** Most heads this style supports on one mast. */
    public final int maxHeads;
    /** Default mast height in blocks (0 = ground-mounted). */
    public final float mastHeight;

    SignalStyle(String id, String label, Render render, int maxHeads, float mastHeight) {
        this.id = id;
        this.label = label;
        this.render = render;
        this.maxHeads = maxHeads;
        this.mastHeight = mastHeight;
    }

    public boolean isDwarf() {
        return this == DWARF;
    }

    public static SignalStyle byId(String id) {
        for (SignalStyle s : values()) if (s.id.equals(id)) return s;
        return COLOR_LIGHT;
    }

    public static SignalStyle byOrdinal(int i) {
        SignalStyle[] v = values();
        return i >= 0 && i < v.length ? v[i] : COLOR_LIGHT;
    }
}
