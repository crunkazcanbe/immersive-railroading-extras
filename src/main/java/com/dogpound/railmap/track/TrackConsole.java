package com.dogpound.railmap.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The console's TRACK tab data (PacketRailState.track): rows tagged
 * M summary · S track piece (overlay + defect list) · A asset (point machine PM, structure ST, tunnel TN, crossing LX) ·
 * V surveyed route.
 */
public final class TrackConsole {
    private TrackConsole() {}

    /** batch-3 #10: one "TS" row per construction standard in use: {TS, standard, pieces, metres, axle t, line km/h, wear x, $/m, overloads, overspeeds, TQI, TSRs} */
    public static List<String[]> standards(TrackData d) {
        int n = TrackData.Standard.values().length;
        int[] pieces = new int[n], len = new int[n], ovl = new int[n], ovs = new int[n], tsr = new int[n];
        long[] tqi = new long[n];
        for (TrackData.Seg s : d.segs.values()) {
            int i = s.standard().ordinal();
            pieces[i]++; len[i] += s.lengthM; ovl[i] += s.overloads; ovs[i] += s.overspeeds; tqi[i] += s.tqi(); if (s.tsr() > 0) tsr[i]++;
        }
        List<String[]> out = new ArrayList<>();
        for (TrackData.Standard st : TrackData.Standard.values()) {
            int i = st.ordinal();
            if (pieces[i] == 0 && i != 0) continue;
            out.add(new String[]{"TS", st.label, String.valueOf(pieces[i]), String.valueOf(len[i]), String.format(Locale.ROOT, "%.1f", st.axleT), String.valueOf(st.lineKmh),
                    String.format(Locale.ROOT, "%.2f", st.wearAvg()), com.dogpound.railmap.economy.Coins.fmt(st.costPerM), String.valueOf(ovl[i]), String.valueOf(ovs[i]),
                    pieces[i] == 0 ? "-" : String.valueOf(tqi[i] / pieces[i]), String.valueOf(tsr[i])});
        }
        return out;
    }

    public static void fill(World w, List<String[]> out) {
        TrackData d = TrackData.get(w);
        long now = w.getTotalWorldTime();
        int[] bands = new int[4];
        int tsrs = 0;
        long tqiSum = 0;
        List<Map.Entry<Long, TrackData.Seg>> all = new ArrayList<>(d.segs.entrySet());
        for (Map.Entry<Long, TrackData.Seg> e : all) { TrackData.Seg s = e.getValue(); bands[s.band()]++; if (s.tsr() > 0) tsrs++; tqiSum += s.tqi(); }
        out.add(new String[]{"M", String.valueOf(all.size()), String.valueOf(bands[0]), String.valueOf(bands[1]), String.valueOf(bands[2]), String.valueOf(bands[3]), String.valueOf(tsrs),
                String.valueOf(all.isEmpty() ? 100 : tqiSum / all.size())});
        all.sort(Comparator.comparingInt(e -> e.getValue().tqi()));
        for (int i = 0; i < Math.min(600, all.size()); i++) {
            BlockPos p = BlockPos.fromLong(all.get(i).getKey());
            TrackData.Seg s = all.get(i).getValue();
            TrackData.Metric m = s.worst();
            out.add(new String[]{"S", String.valueOf(p.getX()), String.valueOf(p.getZ()), String.valueOf(s.tqi()), String.valueOf(s.band()), String.valueOf(s.tsr()), m.label,
                    TrackCondition.fmt(s.get(m)) + " " + m.unit, String.valueOf(Math.round(s.tonnes)), String.valueOf(s.maxKmh),
                    s.inspectedAt == 0 ? "never" : ((now - s.inspectedAt) / 1200) + " min", s.lastWork.isEmpty() ? "-" : s.lastWork, s.radius > 0 ? "R" + s.radius : "STR", String.valueOf(p.getY())});
        }
        for (String[] a : d.assets.values()) { String[] r = new String[a.length + 1]; r[0] = "A"; System.arraycopy(a, 0, r, 1, a.length); out.add(r); }
        for (String[] r : standards(d)) { String[] x = new String[r.length + 1]; x[0] = "A"; System.arraycopy(r, 0, x, 1, r.length); out.add(x); }   // batch-3 #10
        for (Map.Entry<String, NBTTagCompound> e : d.surveys.entrySet()) {
            NBTTagCompound s = e.getValue();
            List<int[]> pts = Survey.points(s);
            double[] ew = Survey.earthworks(s), r = Survey.radii(pts), ch = Survey.chainages(pts);
            double maxG = 0, minR = 0;
            for (int i = 1; i < pts.size(); i++) maxG = Math.max(maxG, Math.abs(SurveyMath.gradePermille(pts.get(i)[1] - pts.get(i - 1)[1], ch[i] - ch[i - 1])));
            for (double v : r) if (v > 0 && (minR == 0 || v < minR)) minR = v;
            out.add(new String[]{"V", e.getKey(), String.valueOf(pts.size()), String.valueOf(Math.round(Survey.length(pts))), String.valueOf(Math.round(ew[0])), String.valueOf(Math.round(ew[1])),
                    SurveyMath.oneIn(maxG), minR > 0 ? "R" + Math.round(minR) : "-", pts.isEmpty() ? "-" : pts.get(0)[0] + "," + pts.get(0)[2], ((now - s.getLong("at")) / 1200) + " min"});
        }
    }
}
