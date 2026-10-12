package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.signal.TileTrackCircuit;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Radio coverage and dead-zone map (her ChatGPT batch-3 #17), saved with the world. The railway is cut into square
 * cells (SigConfig.coverageCell m). Every second each train in radio territory reports what it hears (RadioNet ->
 * SigHooks.radio): best / worst / mean level, how many reports, how many of them were LOST links. A drive-test survey
 * ({@code /irextras sig radio survey}, the Radio map page's SURVEY button) predicts the level along every surveyed
 * signal approach and at every track circuit from every healthy mast and repeater, so dead zones show up before a train
 * ever loses its link there. A cell in territory below the -95 dBm threshold (measured or predicted) is a dead zone;
 * each new one raises one minor alarm.
 */
public final class Coverage extends WorldSavedData {
    private static final String NAME = "irextras_radiocov";
    public static final int NONE = -999;

    public static final class Cell {
        public int cx, cz, best = NONE, worst = 0, n, losses, pred = NONE;
        public long sum, last;
        public boolean alarmed;

        public int mean() { return n == 0 ? NONE : (int) Math.round((double) sum / n); }
        /** the level to judge the cell by: measured mean, else predicted */
        public int level() { return n > 0 ? mean() : pred; }
        public boolean dead() { return losses > 0 || n > 0 && worst < RadioNet.THRESHOLD || n == 0 && pred != NONE && pred < RadioNet.THRESHOLD; }
    }

    public final Map<Long, Cell> cells = new HashMap<>();

    public Coverage() { super(NAME); }
    public Coverage(String n) { super(n); }

    public static Coverage get(World w) {
        MapStorage s = w.getPerWorldStorage();
        Coverage d = (Coverage) s.getOrLoadData(Coverage.class, NAME);
        if (d == null) { d = new Coverage(); s.setData(NAME, d); }
        return d;
    }

    static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }
    static int size() { return SigConfig.coverageCell; }

    Cell cell(double x, double z) {
        int s = size(), cx = Math.floorDiv((int) Math.floor(x), s), cz = Math.floorDiv((int) Math.floor(z), s);
        return cells.computeIfAbsent(key(cx, cz), k -> { Cell c = new Cell(); c.cx = cx; c.cz = cz; return c; });
    }

    /** one train report: radio 1 linked (rssi heard), 2 LOST in territory; out of territory is not mapped */
    void sample(World w, double x, double z, int radio, int rssi) {
        if (radio == 0) return;
        Cell c = cell(x, z);
        c.n++;
        int v = radio == 2 && rssi < -500 ? -130 : rssi;                         // no mast at all in service: as bad as it gets
        c.sum += v;
        c.best = c.best == NONE ? v : Math.max(c.best, v);
        c.worst = c.n == 1 ? v : Math.min(c.worst, v);
        if (radio == 2) c.losses++;
        c.last = w.getTotalWorldTime();
        if (cells.size() > 20000) prune();
        alarm(w, c);
        markDirty();
    }

    private void alarm(World w, Cell c) {
        if (c.alarmed || !c.dead()) return;
        c.alarmed = true;
        int s = size();
        AlarmManager.get(w).raise(w, "radiodz:" + key(c.cx, c.cz), AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR,
                "Radio dead zone at " + c.cx * s + "," + c.cz * s + " (" + (c.n > 0 ? c.losses + " link loss(es), worst " + c.worst + " dBm" : "predicted " + c.pred + " dBm") + ") - fit a repeater",
                new BlockPos(c.cx * s + s / 2, 64, c.cz * s + s / 2), false);
    }

    private void prune() {
        List<Cell> l = new ArrayList<>(cells.values());
        l.sort(Comparator.comparingLong(c -> c.last));
        for (int i = 0; i < l.size() / 4; i++) cells.remove(key(l.get(i).cx, l.get(i).cz));
    }

    /** the best level from every healthy cell (mast or repeater) at a point; NONE when no cell covers it (out of territory) */
    static int predict(World w, double x, double y, double z) {
        double best = NONE;
        boolean territory = false;
        for (TileRadioMast m : SafetyTicker.radioMasts(w)) {
            double dx = x - m.getPos().getX(), dz = z - m.getPos().getZ();
            if (dx * dx + dz * dz > (double) m.radius() * m.radius()) continue;
            territory = true;
            if (m.healthy()) best = Math.max(best, RadioNet.rssi(w, m, x, y, z));
        }
        return !territory ? NONE : best == NONE ? -130 : (int) Math.round(best);
    }

    /** drive-test survey: predicted levels along every surveyed signal approach and at every track circuit; returns points tested */
    int survey(World w) {
        for (Cell c : cells.values()) c.pred = NONE;
        int pts = 0;
        for (SignalSighting.Result r : SignalSighting.all(w).values()) {
            double lastD = -99;
            for (double[] p : r.approach) {
                if (p[3] - lastD < 8) continue;
                lastD = p[3];
                pts += predictAt(w, p[0], p[1], p[2]);
            }
        }
        for (TileTrackCircuit tc : SafetyTicker.circuits(w)) pts += predictAt(w, tc.getPos().getX() + 0.5, tc.getPos().getY() + 1, tc.getPos().getZ() + 0.5);
        for (Cell c : cells.values()) alarm(w, c);
        markDirty();
        return pts;
    }

    private int predictAt(World w, double x, double y, double z) {
        int v = predict(w, x, y, z);
        if (v == NONE) return 0;
        Cell c = cell(x, z);
        c.pred = c.pred == NONE ? v : Math.min(c.pred, v);                      // a cell is as good as its worst point
        return 1;
    }

    List<Cell> deadZones() {
        List<Cell> l = new ArrayList<>();
        for (Cell c : cells.values()) if (c.dead()) l.add(c);
        l.sort(Comparator.comparingInt(Cell::level));
        return l;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        cells.clear();
        NBTTagList l = t.getTagList("c", 11);
        for (int i = 0; i < l.tagCount(); i++) {
            int[] a = l.getIntArrayAt(i);
            if (a.length < 9) continue;
            Cell c = new Cell();
            c.cx = a[0]; c.cz = a[1]; c.best = a[2]; c.worst = a[3]; c.n = a[4]; c.losses = a[5]; c.pred = a[6]; c.sum = a[7]; c.alarmed = a[8] != 0;
            if (a.length > 9) c.last = a[9] * 20L;
            cells.put(key(c.cx, c.cz), c);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Cell c : cells.values())
            l.appendTag(new net.minecraft.nbt.NBTTagIntArray(new int[]{c.cx, c.cz, c.best, c.worst, c.n, c.losses, c.pred, (int) Math.max(Integer.MIN_VALUE, c.sum), c.alarmed ? 1 : 0, (int) (c.last / 20)}));
        t.setTag("c", l);
        return t;
    }

    static List<long[]> mastMarks(World w) {
        List<long[]> out = new ArrayList<>();
        for (TileRadioMast m : SafetyTicker.radioMasts(w)) out.add(new long[]{m.getPos().getX(), m.getPos().getZ(), m.radius(), m.healthy() ? 1 : 0, m instanceof TileRadioRepeater ? 1 : 0});
        return out;
    }

}
