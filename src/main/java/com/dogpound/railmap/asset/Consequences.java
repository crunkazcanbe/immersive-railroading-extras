package com.dogpound.railmap.asset;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.track.TrackData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * What a defect does when it fails (only with defectConsequences on), and what its repair puts right - always through
 * the systems that already own that piece of gear: the track model's rail metrics (so a broken rail gets its TSR from
 * the existing restriction logic), the breaker's contact wear (so the existing breaker-failure chance applies), the
 * insulation the grid already ages, the OHLE fault list.
 */
public final class Consequences {
    private Consequences() {}

    static void fail(World w, AssetData.Asset a, AssetData.Defect df) {
        GridData g = GridData.get(w);
        GridData.Node n = g.nodes().get(a.pos);
        String what;
        switch (a.kind) {
            case TRACK, POINTS -> {
                TrackData.Seg s = TrackData.get(w).peek(a.kind == AssetKind.TRACK ? a.at() : a.at());
                if (s != null) { s.set(TrackData.Metric.RAIL, TrackData.Metric.RAIL.ial * 1.05); TrackData.get(w).markDirty(); }
                what = "BROKEN RAIL - immediate-action limit, the track's own TSR applies";
            }
            case BREAKER -> { if (n != null) { n.wear = Math.max(n.wear, 1.6f); g.markDirty(); } what = "main contacts welded / mechanism binding - it may fail to open"; }
            case TRANSFORMER, MOTOR -> { if (n != null) { n.insul = Math.min(n.insul, 0.3f); g.markDirty(); } what = "insulation close to breakdown"; }
            case INSULATOR -> {
                if (n != null) { n.insul = Math.min(n.insul, 0.3f); g.markDirty(); }
                else try { com.dogpound.railmap.ohle.Ohle.fault(w, a.at(), "FLASHOVER", "insulator " + a.id + " tracked over", false); } catch (LinkageError | RuntimeException ignored) { }
                what = "flashover across the insulator";
            }
            default -> what = "failed - " + a.kind.label.toLowerCase(Locale.ROOT) + " needs emergency repair";
        }
        a.note(w, "FAILED: " + df.type + " - " + what);
        Recorder.event(w, "ASSET FAILURE: " + a.id + " " + a.name + " - " + what);
    }

    /** the repair went in: put the gear right through its own system; returns what was done */
    static String repair(World w, AssetData.Asset a, AssetData.Defect df) {
        GridData g = GridData.get(w);
        GridData.Node n = g.nodes().get(a.pos);
        String done;
        switch (a.kind) {
            case TRACK, POINTS -> {
                TrackData.Seg s = TrackData.get(w).peek(a.at());
                if (s != null) {
                    s.set(TrackData.Metric.RAIL, TrackData.Metric.RAIL.fresh);
                    s.set(TrackData.Metric.SURFACE, TrackData.Metric.SURFACE.fresh);
                    TrackData.get(w).markDirty();
                }
                a.use *= 0.25;
                done = a.kind == AssetKind.TRACK ? "rail renewed (new 18 m length, welded in)" : "drive motor, clutch and detection renewed";
            }
            case BREAKER -> { if (n != null) { n.wear = 0; g.markDirty(); } a.use = 0; done = "contacts and mechanism overhauled"; }
            case TRANSFORMER -> {
                if (n != null) { n.st.put("foul", 0); n.fanFail = false; n.insul = Math.max(n.insul, 0.8f); g.markDirty(); }
                a.use *= 0.6;
                done = "bushing, tap-changer contacts and gaskets renewed, radiators cleaned, fans checked";
            }
            case MOTOR -> { if (n != null) { com.dogpound.railmap.grid.Insulation.renew(n); g.markDirty(); } a.use *= 0.2; done = "rewound, new bearings"; }
            case INSULATOR -> { if (n != null) { n.insul = 1; g.markDirty(); } a.use = 0; a.m[0] = 0; done = "insulator replaced"; }
            case SIGNAL -> { a.use *= 0.1; a.m[0] *= 0.1; done = "LED module and driver card replaced"; }
            case LUMINAIRE -> { a.use *= 0.05; a.m[0] *= 0.05; done = "LED module, driver and lamp holder replaced"; }
            default -> { a.use *= 0.5; done = "structural repair (resin injection, rebar, mortar / weld)"; }
        }
        a.raw = -1;                                                          // counters re-based after the renewal
        a.note(w, "REPAIRED defect #" + df.id + ": " + done);
        return done;
    }

    /** the spare a defect's repair needs */
    static ItemSpare.Spare spareFor(AssetData d, AssetData.Defect df) {
        if (df.asset.startsWith("VEH:")) return ItemSpare.Spare.WHEELSET;
        AssetData.Asset a = d.byId(df.asset);
        return a == null ? ItemSpare.Spare.STRUCTURE_KIT : a.kind.spare;
    }

    static BlockPos posOf(AssetData.Defect df) { return df.pos == Long.MIN_VALUE ? null : BlockPos.fromLong(df.pos); }
}
