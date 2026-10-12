package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.TrackPath;
import com.dogpound.railmap.signal.Aspect;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Balise / transponder network (her batch-3 list #12). A balise group sends a telegram to every train that runs over it:
 * a speed limit from a distance ahead, an end of authority (switched balises read their signal through the LEU), and
 * route data. The telegram reaches train protection twice over:
 * <ul>
 *   <li>{@link #restrictions}: the balises on the path ahead go into Wayside.read, so ATP / ATO / the cab display's
 *   planning area brake for them like any lineside restriction;</li>
 *   <li>{@link #fast}: a train running over the balise "reads" it - the cab display shows the telegram, the passage is
 *   logged, and a restrictive telegram sounds the cab alerter that the driver must acknowledge (#13, DriverAids).</li>
 * </ul>
 * A balise without 12 V (or burnt out) is silent: no telegram, an alarm, no braking.
 */
public final class Balises {
    private Balises() {}

    public static final int[] SPEEDS = {-1, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 100, 120, 140, 160, 200};
    public static final int[] OFFSETS = {0, 50, 100, 200, 400};
    public static final int CAUTION_KMH = 40;

    /** one telegram: kmh -1 = no speed restriction */
    static final class Telegram {
        int kmh = -1, offset;
        boolean stop;
        String text = "";

        String show() {
            String s = stop ? "EOA" : kmh < 0 ? "NO LIMIT" : kmh + " KMH";
            if (!stop && offset > 0) s += " +" + offset + "M";
            return text.isEmpty() ? s : s + " " + text;
        }
    }

    /** what a balise programmed with (limit, offset, mode) says, with its signal showing `aspect` (null = no signal) */
    static Telegram build(int kmh, int offset, int mode, Aspect aspect, String text) {
        Telegram t = new Telegram();
        t.offset = offset;
        t.text = text == null ? "" : text;
        t.kmh = kmh;
        if (mode == 1) {                                                         // SWITCHED: the LEU reads the signal
            if (aspect == null || aspect == Aspect.DARK || aspect.isStop()) { t.stop = true; t.kmh = 0; t.offset = 0; }
            else if (aspect.speedKmh < 999) t.kmh = kmh < 0 ? CAUTION_KMH : Math.min(kmh, CAUTION_KMH);
        }
        return t;
    }

    // ---- what trains read ------------------------------------------------------------------------------------------
    private static final class Read { String text; long tick; }
    private static final Map<UUID, Read> LAST = new HashMap<>();
    private static final Map<Integer, LinkedList<String>> LOG = new HashMap<>();

    static String cabNote(UUID loco, long now) {
        Read r = LAST.get(loco);
        return r == null || now - r.tick > 400 ? null : r.text;
    }

    static List<String> log(World w) { return LOG.getOrDefault(w.provider.getDimension(), new LinkedList<>()); }

    private static void log(World w, String line) {
        LinkedList<String> l = LOG.computeIfAbsent(w.provider.getDimension(), k -> new LinkedList<>());
        l.addFirst(Recorder.clock(w, w.getTotalWorldTime()) + " " + line);
        while (l.size() > 60) l.removeLast();
    }

    /** a train stands on the balise's facing test: true when it applies to a train heading hx, hz */
    static boolean applies(TileBalise b, double hx, double hz) {
        int dir = b.dir();
        if (dir == 0) return true;
        net.minecraft.util.EnumFacing f = b.facing();
        double dot = f.getXOffset() * hx + f.getZOffset() * hz;
        return dir == 1 ? dot < -0.3 : dot > 0.3;                                // FACING: the train comes toward its face (like a signal)
    }

    static void tick(World w) {
        for (TileBalise b : SigTicker.tiles(w, TileBalise.class)) b.second();
    }

    static void fast(World w, List<SigA.Pass> passes, long now) {
        List<TileBalise> bs = SigTicker.tiles(w, TileBalise.class);
        if (bs.isEmpty()) return;
        for (TileBalise b : bs) {
            BlockPos p = b.getPos();
            for (SigA.Pass ps : passes) {
                if (!ps.passed(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 3) || !applies(b, ps.hx(), ps.hz())) continue;
                Telegram t = b.telegram();
                String label = SigA.label(ps.loco);
                if (t == null) { log(w, "BG" + b.bg() + " SILENT (no 12 V) - " + label + " read nothing"); b.passed(false); continue; }
                b.passed(true);
                String text = "BG" + b.bg() + ": " + t.show();
                Read r = new Read();
                r.text = text;
                r.tick = now;
                LAST.put(ps.loco.getUUID(), r);
                log(w, label + " read " + text + " at " + Math.round(ps.speed) + " km/h");
                boolean restrictive = t.stop || t.kmh >= 0 && t.kmh < ps.speed;
                List<EntityPlayer> riders = SigA.riders(ps.loco);
                if (restrictive && !riders.isEmpty() && !SigA.driverless(w, ps.loco.getUUID()))
                    com.dogpound.railmap.auto.DriverAids.cabWarn(ps.loco.getUUID(), text, now);
                for (EntityPlayer pl : riders) w.playSound(null, pl.getPosition(), restrictive ? com.dogpound.railmap.ModSounds.BUZZER : com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.PLAYERS, 0.5f, 1.4f);
                if (!riders.isEmpty()) com.dogpound.railmap.api.QuestEvents.fire(w, riders.get(0), p, "balise_passed", "bg", b.bg(), "kmh", t.kmh, "stop", t.stop);
            }
        }
        if (LAST.size() > 200) LAST.entrySet().removeIf(e -> now - e.getValue().tick > 400);
    }

    /** the balises on a path facing the train: {distance, kmh, stop, what, pos, signal=false} */
    static void restrictions(World w, TrackPath path, List<Object[]> out) {
        if (path.steps.isEmpty()) return;
        for (TileBalise b : SigTicker.tiles(w, TileBalise.class)) {
            BlockPos p = b.getPos();
            double d = path.distanceTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 3.5);
            if (d < 0) continue;
            int i = 0;
            while (i < path.steps.size() - 1 && path.steps.get(i).distance < d) i++;
            double[] h = path.headingAt(i);
            if (!applies(b, h[0], h[1])) continue;
            Telegram t = b.telegram();
            if (t == null) continue;
            String what = "Balise BG" + b.bg() + ": " + t.show();
            if (t.stop) {
                double at = d;
                BlockPos sp = b.signalPos();
                if (sp != null) { double sd = path.distanceTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 4.5); if (sd >= d) at = sd; }
                out.add(new Object[]{at, 0.0, true, what, p, false});
            } else if (t.kmh >= 0) out.add(new Object[]{d + t.offset, (double) t.kmh, false, what, p, false});
        }
    }
}
