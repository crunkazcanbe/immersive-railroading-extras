package com.dogpound.railmap.cine.client;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Saved camera paths: config/irextras_campaths.txt, one per line "slot|dim|secs|x,y,z,yaw,pitch,fov;..." (slots 1-9). */
public final class CinePathStore {
    private CinePathStore() {}

    static File file() { return new File(Minecraft.getMinecraft().gameDir, "config/irextras_campaths.txt"); }

    static Map<Integer, String> read() {
        Map<Integer, String> m = new TreeMap<>();
        try {
            File f = file();
            if (f.exists()) for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int i = l.indexOf('|');
                if (i > 0) try { m.put(Integer.parseInt(l.substring(0, i)), l.substring(i + 1)); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        return m;
    }

    public static String save(int slot) {
        if (CineCam.WAYPOINTS.size() < 2) return "NEED 2+ WAYPOINTS";
        Map<Integer, String> m = read();
        StringBuilder b = new StringBuilder();
        b.append(Minecraft.getMinecraft().world.provider.getDimension()).append('|').append(CineCam.pathSecs).append('|');
        for (double[] k : CineCam.WAYPOINTS) b.append(String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.2f,%.2f,%.1f;", k[0], k[1], k[2], k[3], k[4], k[5]));
        m.put(slot, b.toString());
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, String> e : m.entrySet()) lines.add(e.getKey() + "|" + e.getValue());
        try {
            File f = file();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
            return "PATH SAVED IN SLOT " + slot;
        } catch (Exception e) {
            return "COULD NOT SAVE: " + e.getMessage();
        }
    }

    public static String load(int slot) {
        String s = read().get(slot);
        if (s == null) return "SLOT " + slot + " EMPTY";
        String[] p = s.split("\\|");
        if (p.length < 3) return "SLOT " + slot + " DAMAGED";
        if (Integer.parseInt(p[0]) != Minecraft.getMinecraft().world.provider.getDimension()) return "SLOT " + slot + " IS IN ANOTHER DIMENSION";
        CineCam.WAYPOINTS.clear();
        CineCam.pathSecs = Double.parseDouble(p[1]);
        for (String k : p[2].split(";")) {
            if (k.isEmpty()) continue;
            String[] v = k.split(",");
            double[] d = new double[6];
            for (int i = 0; i < 6 && i < v.length; i++) d[i] = Double.parseDouble(v[i]);
            CineCam.WAYPOINTS.add(d);
        }
        return "SLOT " + slot + ": " + CineCam.WAYPOINTS.size() + " WAYPOINTS";
    }

    public static Set<Integer> slots() { return read().keySet(); }
}
