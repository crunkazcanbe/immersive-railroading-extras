package com.dogpound.railmap.cctv.client;

/** CCTV pictures for the Control Center's CCTV tab (the console lives in another package) */
public final class ConsolePictures {
    private ConsolePictures() {}

    public static void draw(CctvClient.Pic p, int x, int y, int w, int h) { CctvGui.picture(p, x, y, w, h, false); }

    public static void thumb(int cam, int x, int y, int w, int h) { CctvGui.picture(CctvClient.thumb(cam), x, y, w, h, false); }
}
