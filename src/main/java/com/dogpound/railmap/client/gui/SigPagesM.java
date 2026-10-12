package com.dogpound.railmap.client.gui;

import java.util.ArrayList;
import java.util.List;

/** SAFETY tab pages of signalling batch 3 part M (#15 is on the built-in Routes page; #19 Macros). */
final class SigPagesM {
    private SigPagesM() {}

    static List<SigPage> pages() {
        List<SigPage> l = new ArrayList<>();
        l.add(new SigPageMMacros());
        return l;
    }
}
