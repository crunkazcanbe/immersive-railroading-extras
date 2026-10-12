package com.dogpound.railmap.cine.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.network.PacketReplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-flying the past (batch 5 #10 "replays"): the Event Recorder's rewind buffer (alarm.Recorder - every train's place
 * and speed for the last 15 minutes, while a powered recorder runs) is fetched with the console's PacketReplay and played
 * back as ghost trains the camera can chase, orbit, fly by or watch from the tripods. Heights come from the train itself
 * if it is still loaded, else the ground under it.
 */
public final class CineReplay {
    private CineReplay() {}

    public static boolean loaded;
    public static long t0, t1;
    public static double t;
    public static float speed = 1;
    public static boolean paused;
    /** the ghost the camera follows (its recorder id) */
    public static int follow = -1;
    private static PacketReplay data;
    private static long asked;

    public static void request() {
        asked = System.currentTimeMillis();
        PacketReplay.last = null;
        RailMap.NETWORK.sendToServer(PacketReplay.request());
    }

    /** called every client tick while replay mode is on */
    public static void tick() {
        if (PacketReplay.last != null && PacketReplay.last != data) {
            data = PacketReplay.last;
            loaded = !data.sampleTicks.isEmpty();
            if (loaded) { t0 = data.sampleTicks.get(0)[0]; t1 = data.sampleTicks.get(data.sampleTicks.size() - 1)[0]; t = t0; }
            if (follow < 0) { List<Integer> ids = ids(); if (!ids.isEmpty()) follow = ids.get(0); }
        }
    }

    public static boolean waiting() { return !loaded && System.currentTimeMillis() - asked < 5000; }

    public static String status() {
        if (!loaded) return waiting() ? "LOADING RECORDER..." : "NO RECORDER DATA (needs a powered Event Recorder)";
        return String.format(java.util.Locale.ROOT, "REPLAY %s / %s  x%.0f%s  %d trains", clock(t - t0), clock(t1 - t0), speed, paused ? " PAUSED" : "", ids().size());
    }

    static String clock(double ticks) { long s = (long) (ticks / 20); return String.format(java.util.Locale.ROOT, "%02d:%02d", s / 60, s % 60); }

    /** advance by real seconds */
    public static void advance(double secs) {
        if (!loaded || paused) return;
        t = Math.max(t0, Math.min(t1, t + secs * 20 * speed));
        if (t >= t1) paused = true;
    }

    public static void seek(double secs) { if (loaded) t = Math.max(t0, Math.min(t1, t + secs * 20)); }

    /** every recorder train id in the buffer */
    public static List<Integer> ids() {
        List<Integer> o = new ArrayList<>();
        if (data == null) return o;
        for (float[][] s : data.sampleTrains) for (float[] tr : s) if (!o.contains((int) tr[3])) o.add((int) tr[3]);
        return o;
    }

    public static void cycle(int dir) {
        List<Integer> ids = ids();
        if (ids.isEmpty()) return;
        int at = Math.max(0, ids.indexOf(follow));
        follow = ids.get(Math.floorMod(at + dir, ids.size()));
    }

    /** every ghost now: id -> {x, y, z, heading, speed m/s, half length} */
    public static Map<Integer, double[]> ghosts() {
        Map<Integer, double[]> o = new LinkedHashMap<>();
        if (!loaded || data == null) return o;
        int n = data.sampleTicks.size();
        int i = 0;
        while (i + 1 < n && data.sampleTicks.get(i + 1)[0] <= t) i++;
        int j = Math.min(n - 1, i + 1);
        long ta = data.sampleTicks.get(i)[0], tb = data.sampleTicks.get(j)[0];
        double f = tb == ta ? 0 : (t - ta) / (tb - ta);
        Minecraft mc = Minecraft.getMinecraft();
        for (float[] a : data.sampleTrains.get(i)) {
            float[] b = null;
            for (float[] q : data.sampleTrains.get(j)) if ((int) q[3] == (int) a[3]) b = q;
            if (b == null) b = a;
            double x = a[0] + (b[0] - a[0]) * f, z = a[1] + (b[1] - a[1]) * f, kmh = a[2] + (b[2] - a[2]) * f;
            double dx = b[0] - a[0], dz = b[1] - a[1];
            double heading = Math.abs(dx) + Math.abs(dz) < 1e-3 ? prevHeading((int) a[3]) : Math.toDegrees(Math.atan2(-dx, dz));
            HEAD.put((int) a[3], heading);
            Entity live = mc.world.getEntityByID((int) a[3]);
            double y = live != null ? live.posY : mc.world.getHeight(new BlockPos(x, 0, z)).getY();
            o.put((int) a[3], new double[]{x, y, z, heading, kmh / 3.6, 9});
        }
        return o;
    }

    private static final Map<Integer, Double> HEAD = new java.util.HashMap<>();
    static double prevHeading(int id) { return HEAD.getOrDefault(id, 0.0); }

    public static String name(int id) {
        if (data == null) return "";
        for (int i = 0; i < data.sampleTrains.size(); i++) {
            float[][] s = data.sampleTrains.get(i);
            for (int k = 0; k < s.length; k++) if ((int) s[k][3] == id) return data.sampleNames.get(i)[k];
        }
        return "";
    }

    public static double[] subject() { return follow < 0 ? null : ghosts().get(follow); }

    /** the ghost trains: translucent long boxes with their names */
    public static void render(double cx, double cy, double cz) {
        Minecraft mc = Minecraft.getMinecraft();
        for (Map.Entry<Integer, double[]> en : ghosts().entrySet()) {
            double[] g = en.getValue();
            double x = g[0] - cx, y = g[1] - cy, z = g[2] - cz;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate((float) (180 - g[3]), 0, 1, 0);
            com.dogpound.railmap.client.render.Prims.begin();
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            int col = en.getKey() == follow ? 0x60e0ff : 0xa0c8ff;
            com.dogpound.railmap.client.render.Prims.box(-1.5, 0.2, -g[5], 1.5, 4.0, g[5], col, 0.35f);
            com.dogpound.railmap.client.render.Prims.box(-1.55, 3.9, -g[5], 1.55, 4.1, g[5], 0xffffff, 0.5f);
            GlStateManager.depthMask(true);
            com.dogpound.railmap.client.render.Prims.end();
            GlStateManager.popMatrix();
            String label = name(en.getKey()) + String.format(java.util.Locale.ROOT, " %.0f km/h", g[4] * 3.6);
            EntityRenderer.drawNameplate(mc.fontRenderer, label, (float) x, (float) y + 5f, (float) z, 0, mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX, false, false);
        }
        GlStateManager.enableLighting();
    }
}
