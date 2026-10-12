package com.dogpound.railmap.client.gui;

import java.util.ArrayList;
import java.util.List;

/** The SAFETY tab's extra pages for signalling batch 3, in chip order (each part lists its own). */
final class SigPages {
    private SigPages() {}

    static List<SigPage> all() {
        List<SigPage> l = new ArrayList<>();
        l.addAll(SigPagesA.pages());
        l.addAll(SigPagesB.pages());
        l.addAll(SigPagesC.pages());
        l.addAll(SigPagesM.pages());
        return l;
    }
}
