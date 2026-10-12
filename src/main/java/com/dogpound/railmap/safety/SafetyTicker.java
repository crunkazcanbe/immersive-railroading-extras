package com.dogpound.railmap.safety;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.signal.TileTrackCircuit;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Drives the signalling-safety systems (her ChatGPT list section 4) on the server: train tracking + integrity every
 * 10 ticks; once a second the gear lists, holds, radio, sighting surveys, the fault lab and the self-test schedule.
 * Costs nothing in a world without trains or safety gear.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class SafetyTicker {
    private SafetyTicker() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");

    private static final class Lists {
        final List<TileAxleEvaluator> evaluators = new ArrayList<>();
        final List<TileRadioMast> radioMasts = new ArrayList<>();
        final List<TileEmergencyPlunger> plungers = new ArrayList<>();
        final List<TilePossessionSwitch> possessions = new ArrayList<>();
        final List<TileBannerRepeater> banners = new ArrayList<>();
        final List<TileTrackCircuit> circuits = new ArrayList<>();
        final List<TileFaultLab> labs = new ArrayList<>();
        int gear;
    }

    private static final Map<Integer, Lists> LISTS = new HashMap<>();

    public static void clear() {
        LISTS.clear();
        AxleWatch.clear();
        ConsistWatch.clear();
        SafetyHolds.clear();
        SignalSighting.clear();
    }

    private static Lists lists(World w) { return LISTS.getOrDefault(w.provider.getDimension(), new Lists()); }
    public static List<TileAxleEvaluator> evaluators(World w) { return lists(w).evaluators; }
    public static List<TileRadioMast> radioMasts(World w) { return lists(w).radioMasts; }
    public static List<TileEmergencyPlunger> plungers(World w) { return lists(w).plungers; }
    public static List<TilePossessionSwitch> possessions(World w) { return lists(w).possessions; }
    public static List<TileBannerRepeater> banners(World w) { return lists(w).banners; }
    public static List<TileTrackCircuit> circuits(World w) { return lists(w).circuits; }
    public static List<TileFaultLab> labs(World w) { return lists(w).labs; }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient()) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        try {
            if (IR && now % 10 == 3) ConsistWatch.tick(w);
            if (now % 20 != 9) return;
            scan(w);
            SafetyHolds.tick(w);
            if (IR) { RadioNet.tick(w); SignalSighting.tick(w); }
            SafetyData d = SafetyData.get(w);
            // the fault lab's faults live only while a lab console in test mode exists
            if (!d.faults.isEmpty() || d.testMode) {
                boolean labHere = false;
                for (TileFaultLab l : labs(w)) if (l.getPos().toLong() == d.labPos) labHere = true;
                BlockPos lp = BlockPos.fromLong(d.labPos);
                if (!labHere && (d.labPos == 0 || w.isBlockLoaded(lp) && !(w.getTileEntity(lp) instanceof TileFaultLab))) {
                    FaultLab.clearAll(w, "lab console gone");
                    d.testMode = false;
                    d.markDirty();
                }
                for (SafetyData.Fault f : d.faults) if (f.kind == FaultLab.Kind.POWER_CUT) { FaultLab.cutPower(w, true); break; }
            }
            if (lists(w).gear > 0 && d.selfTestMinutes > 0 && now - d.lastSelfTest >= d.selfTestMinutes * 1200L) SelfTest.run(w, "scheduled");
        } catch (LinkageError | RuntimeException ex) {
            RailMap.LOG.warn("[irextras] safety systems pass failed: {}", ex.toString());
        }
    }

    private static void scan(World w) {
        Lists l = new Lists();
        for (TileEntity t : w.loadedTileEntityList) {
            if (t.isInvalid()) continue;
            if (t instanceof SafetyTile) l.gear++;
            if (t instanceof TileAxleEvaluator e) l.evaluators.add(e);
            else if (t instanceof TileRadioMast m) l.radioMasts.add(m);
            else if (t instanceof TileEmergencyPlunger p) l.plungers.add(p);
            else if (t instanceof TilePossessionSwitch p) l.possessions.add(p);
            else if (t instanceof TileBannerRepeater b) l.banners.add(b);
            else if (t instanceof TileFaultLab f) l.labs.add(f);
            else if (t instanceof TileTrackCircuit c && !c.isJoint()) l.circuits.add(c);
        }
        LISTS.put(w.provider.getDimension(), l);
    }

    /** a safety block was broken */
    public static void removed(World w, BlockPos p) {
        SafetyData d = SafetyData.get(w);
        if (d.labPos == p.toLong()) {
            FaultLab.clearAll(w, "lab console removed");
            d.testMode = false;
            d.labPos = 0;
            d.markDirty();
        }
        if (w.getTileEntity(p) instanceof TileEmergencyPlunger || w.getTileEntity(p) instanceof TileCallPoint || w.getTileEntity(p) instanceof TileAxleEvaluator) {
            com.dogpound.railmap.alarm.AlarmManager am = com.dogpound.railmap.alarm.AlarmManager.get(w);
            am.clear("esp:" + p.toLong());
            for (int i = 0; i < TileAxleEvaluator.N; i++) am.clear("axle:" + p.toLong() + ":" + i);
        }
    }
}
