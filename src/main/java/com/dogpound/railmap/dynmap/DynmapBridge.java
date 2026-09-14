package com.dogpound.railmap.dynmap;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.graph.Chains;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.SignalNode;
import com.dogpound.railmap.graph.StopNode;
import com.dogpound.railmap.graph.TrainNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Mirrors every display's network onto Dynmap as three toggleable layers: track chains
 * (coloured by kind), signals (flag icons by aspect) and live trains (refreshed every 2 s
 * while anything is watching). Marker ids are prefixed by the display's position so two
 * boards covering the same track just overwrite each other's copy.
 * <p>
 * Only constructed when the dynmap mod is present; all Dynmap classes stay inside this file.
 */
public final class DynmapBridge extends DynmapCommonAPIListener implements DynmapHook {
    private MarkerAPI markers;
    private MarkerSet trackSet, signalSet, trainSet;
    private final Set<String> trainIds = new HashSet<>();

    public DynmapBridge() {
        DynmapCommonAPIListener.register(this);
    }

    @Override
    public void apiEnabled(DynmapCommonAPI api) {
        markers = api.getMarkerAPI();
        if (markers == null) {
            RailMap.LOG.warn("[RailMap] Dynmap MarkerAPI not available.");
            return;
        }
        trackSet = getOrCreate("railmap.tracks", "Rail Tracks");
        signalSet = getOrCreate("railmap.signals", "Rail Signals");
        trainSet = getOrCreate("railmap.trains", "Trains");
        RailMap.LOG.info("[RailMap] Dynmap hooked: track/signal/train layers ready.");
    }

    @Override
    public void apiDisabled(DynmapCommonAPI api) {
        markers = null;
        trackSet = signalSet = trainSet = null;
    }

    private MarkerSet getOrCreate(String id, String label) {
        MarkerSet set = markers.getMarkerSet(id);
        if (set == null) set = markers.createMarkerSet(id, label, null, false);
        else set.setMarkerSetLabel(label);
        return set;
    }

    @Override
    public boolean wantsTrains() {
        return trainSet != null;
    }

    /** Dynmap's Forge world naming: level name for the overworld, DIM<n> otherwise. */
    private static String worldName(World w) {
        int dim = w.provider.getDimension();
        return dim == 0 ? w.getWorldInfo().getWorldName() : "DIM" + dim;
    }

    private static String prefix(BlockPos origin) {
        return "rm_" + origin.getX() + "_" + origin.getY() + "_" + origin.getZ() + "_";
    }

    @Override
    public void remove(World world, BlockPos origin) {
        if (trackSet == null) return;
        String pre = prefix(origin);
        for (PolyLineMarker m : new ArrayList<>(trackSet.getPolyLineMarkers())) if (m.getMarkerID().startsWith(pre)) m.deleteMarker();
        for (Marker m : new ArrayList<>(signalSet.getMarkers())) if (m.getMarkerID().startsWith(pre)) m.deleteMarker();
    }

    @Override
    public void network(World world, BlockPos origin, RailNetwork net) {
        if (trackSet == null) return;
        try {
            remove(world, origin);
            String w = worldName(world);
            String pre = prefix(origin);
            int i = 0;
            for (float[] chain : Chains.build(net)) {
                int n = chain.length / 3;
                if (n < 2) continue;
                double[] xs = new double[n], ys = new double[n], zs = new double[n];
                for (int k = 0; k < n; k++) {
                    xs[k] = chain[k * 3];
                    ys[k] = chain[k * 3 + 1];
                    zs[k] = chain[k * 3 + 2];
                }
                PolyLineMarker m = trackSet.createPolyLineMarker(pre + "t" + i++, "Track", false, w, xs, ys, zs, false);
                if (m != null) m.setLineStyle(3, 0.9, 0xC8C8C8);
            }
            for (SignalNode s : net.signals) {
                String icon = switch (s.aspect) {
                    case RED -> "redflag";
                    case YELLOW -> "yellowflag";
                    case GREEN -> "greenflag";
                    default -> "pinkflag";
                };
                MarkerIcon ic = markers.getMarkerIcon(icon);
                signalSet.createMarker(pre + "s" + i++, "Signal: " + s.aspect + (s.rawState.isEmpty() ? "" : " (" + s.rawState + ")"),
                        w, s.pos.getX() + 0.5, s.pos.getY(), s.pos.getZ() + 0.5, ic, false);
            }
            for (StopNode s : net.stops) {
                MarkerIcon ic = markers.getMarkerIcon(s.kind == StopNode.Kind.STATION ? "building" : "sign");
                String label = s.named ? s.name : s.name.toLowerCase(Locale.ROOT).replace('_', ' ');
                signalSet.createMarker(pre + "p" + i++, label, w, s.pos.getX() + 0.5, s.pos.getY(), s.pos.getZ() + 0.5, ic, false);
            }
        } catch (RuntimeException e) {
            RailMap.LOG.warn("[RailMap] Dynmap update failed: {}", e.toString());
        }
    }

    @Override
    public void trains(World world, List<TrainNode> trains) {
        if (trainSet == null) return;
        try {
            String w = worldName(world);
            Set<String> seen = new HashSet<>();
            MarkerIcon ic = markers.getMarkerIcon("minecart");
            for (TrainNode t : trains) {
                if (!t.lead) continue; // one marker per train, not per car
                String id = "tr_" + t.id;
                seen.add(id);
                String label = t.displayName() + (t.consist > 1 ? " (" + t.consist + " cars)" : "")
                        + " " + Math.round(Math.abs(t.speedKmh)) + " km/h"
                        + (t.heading.isEmpty() ? "" : " → " + t.heading);
                Marker m = trainSet.findMarker(id);
                if (m == null) {
                    trainSet.createMarker(id, label, w, t.x, t.y, t.z, ic, false);
                } else {
                    m.setLocation(w, t.x, t.y, t.z);
                    m.setLabel(label);
                }
            }
            for (String old : new ArrayList<>(trainIds)) {
                if (!seen.contains(old)) {
                    Marker m = trainSet.findMarker(old);
                    if (m != null) m.deleteMarker();
                }
            }
            trainIds.clear();
            trainIds.addAll(seen);
        } catch (RuntimeException e) {
            RailMap.LOG.warn("[RailMap] Dynmap train update failed: {}", e.toString());
        }
    }

    @Override
    public void shutdown() {
        if (trackSet != null) { trackSet.deleteMarkerSet(); trackSet = null; }
        if (signalSet != null) { signalSet.deleteMarkerSet(); signalSet = null; }
        if (trainSet != null) { trainSet.deleteMarkerSet(); trainSet = null; }
    }
}
