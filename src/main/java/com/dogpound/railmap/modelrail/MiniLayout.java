package com.dogpound.railmap.modelrail;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * The model railway itself (her ChatGPT list #8): a grid of track pieces on the table top (4 cells to a block, up to
 * 16 x 16 on a 4 x 4 table) and the mini trains running on it. Pure logic - no world - so the server ticks it, the
 * renderer extrapolates a copy between syncs, and the self-test drives it headless.
 * <p>
 * Edges: 0 = north (back of the table, -gz), 1 = east (+gx), 2 = south (the operator's side, +gz), 3 = west.
 * A train's loco faces its {@code to} edge; positive speed runs toward it, the cars trail behind. Points: the toe is
 * edge {@code rot}, the straight road runs to rot+2, the turnout to rot+1 (or rot+3 when mirrored); trailing through
 * them springs them over. Signals stop trains that enter from edge {@code rot} while red. Over 2.5 cells/s on a curve
 * derails; two trains touching shorts the track (the tile trips the power and raises the alarm).
 */
public final class MiniLayout {
    public static final int N = 16;
    public static final int EMPTY = 0, STRAIGHT = 1, CURVE = 2, POINTS = 3, CROSSING = 4, STATION = 5, TUNNEL = 6, SIGNAL = 7, BUFFER = 8, TREE = 9, HOUSE = 10;
    public static final String[] NAMES = {"Empty", "Straight", "Curve", "Points", "Crossing", "Station", "Tunnel", "Signal", "Buffer stop", "Trees", "House"};
    public static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    public static final double CAR = 0.55, CURVE_LIMIT = 2.5, STEP = 0.45;
    public static final int MAX_TRAINS = 2, MAX_CARS = 6;

    public final byte[] cell = new byte[N * N];
    public final boolean[] rev = new boolean[N * N];
    public final boolean[] green = new boolean[N * N];
    public int w = 8, d = 8;
    public final List<Train> trains = new ArrayList<>();

    /** a deep copy (the renderer runs one ahead between syncs) */
    public MiniLayout copy() {
        MiniLayout m = new MiniLayout();
        System.arraycopy(cell, 0, m.cell, 0, cell.length);
        System.arraycopy(rev, 0, m.rev, 0, rev.length);
        System.arraycopy(green, 0, m.green, 0, green.length);
        m.w = w; m.d = d;
        for (Train t : trains) m.trains.add(t.copy());
        return m;
    }

    // ---- pieces ----------------------------------------------------------------------------------------------------------
    public int type(int c) { return c < 0 ? EMPTY : cell[c] & 15; }
    public int rot(int c) { return (cell[c] >> 4) & 3; }
    public boolean mirror(int c) { return (cell[c] & 64) != 0; }
    public static int idx(int gx, int gz) { return gz * N + gx; }
    public boolean inside(int gx, int gz) { return gx >= 0 && gz >= 0 && gx < w && gz < d; }

    public void set(int gx, int gz, int type, int rot, boolean mirror) {
        if (gx < 0 || gz < 0 || gx >= N || gz >= N || type < 0 || type >= NAMES.length) return;
        int c = idx(gx, gz);
        cell[c] = (byte) (type | (rot & 3) << 4 | (mirror ? 64 : 0));
        rev[c] = false;
        green[c] = false;
    }

    public static boolean isTrack(int type) { return type >= STRAIGHT && type <= BUFFER; }

    /** the edge a train entering this cell by `e` leaves by; -1 = no road */
    public int route(int c, int e) {
        int t = type(c), r = rot(c);
        switch (t) {
            case STRAIGHT: case STATION: case TUNNEL: case SIGNAL:
                return (e & 1) == (r & 1) ? (e + 2) & 3 : -1;
            case CROSSING: return (e + 2) & 3;
            case CURVE: return e == r ? (r + 1) & 3 : e == ((r + 1) & 3) ? r : -1;
            case BUFFER: return e == r ? (r + 2) & 3 : e == ((r + 2) & 3) ? r : -1;
            case POINTS: {
                int div = (r + (mirror(c) ? 3 : 1)) & 3, straight = (r + 2) & 3;
                if (e == r) return rev[c] ? div : straight;
                if (e == straight || e == div) return r;
                return -1;
            }
            default: return -1;
        }
    }

    /** a curved road through this cell (curve, or points set to the turnout) */
    public static boolean curved(int from, int to) { return to != ((from + 2) & 3); }

    public static double length(int from, int to) { return curved(from, to) ? Math.PI / 4 : 1.0; }

    /** the cell next door across edge e, or -1 off the table */
    public int next(int c, int e) {
        int gx = c % N + DX[e], gz = c / N + DZ[e];
        return inside(gx, gz) ? idx(gx, gz) : -1;
    }

    // ---- where a train is ------------------------------------------------------------------------------------------------
    public static final class Loc {
        public int c, from, to;
        public double t;
        public boolean blocked;
        public double done;
        public Loc(int c, int from, int to, double t) { this.c = c; this.from = from; this.to = to; this.t = t; }
        public Loc copy() { return new Loc(c, from, to, t); }
    }

    /** the lowest / highest t a train may reach in this cell going this way (buffer stops; a red signal for the head) */
    private double[] limits(Loc s, boolean obeySignals) {
        double lo = 0, hi = 1;
        int t = type(s.c), r = rot(s.c);
        if (t == BUFFER) {
            if (s.to == ((r + 2) & 3)) hi = 0.5;
            if (s.from == ((r + 2) & 3)) lo = 0.5;
        }
        if (obeySignals && t == SIGNAL && !green[s.c] && s.from == r && s.t <= 0.45 + 1e-9) hi = Math.min(hi, 0.45);
        return new double[]{lo, hi};
    }

    /** move along the road by dist cells (negative = backward); stops at the end of the road or a red signal (head only) */
    public Loc walk(Loc s0, double dist, boolean obeySignals) {
        Loc s = s0.copy();
        double rem = Math.abs(dist);
        boolean fwd = dist >= 0;
        for (int guard = 0; guard < 64 && rem > 1e-9; guard++) {
            double len = length(s.from, s.to);
            double[] lim = limits(s, obeySignals && fwd);
            if (fwd) {
                double need = (lim[1] - s.t) * len;
                if (rem <= need) { s.t += rem / len; s.done += rem; return s; }
                s.done += Math.max(0, need); rem -= Math.max(0, need); s.t = lim[1];
                if (lim[1] < 1) { s.blocked = true; return s; }
                int nc = next(s.c, s.to), entry = (s.to + 2) & 3, nto = nc < 0 ? -1 : route(nc, entry);
                if (nto < 0) { s.blocked = true; return s; }
                s.c = nc; s.from = entry; s.to = nto; s.t = 0;
            } else {
                double need = (s.t - lim[0]) * len;
                if (rem <= need) { s.t -= rem / len; s.done += rem; return s; }
                s.done += Math.max(0, need); rem -= Math.max(0, need); s.t = lim[0];
                if (lim[0] > 0) { s.blocked = true; return s; }
                int nc = next(s.c, s.from), entry = (s.from + 2) & 3, nfrom = nc < 0 ? -1 : route(nc, entry);
                if (nfrom < 0) { s.blocked = true; return s; }
                s.c = nc; s.to = entry; s.from = nfrom; s.t = 1;
            }
        }
        return s;
    }

    /** grid position (cells, fractional) and heading (radians, grid frame: 0 = +gx, pi/2 = +gz) of a location */
    public static double[] pose(Loc s) {
        double[] a = mid(s.from), b = mid(s.to);
        double u, v, h;
        if (!curved(s.from, s.to)) {
            u = a[0] + (b[0] - a[0]) * s.t; v = a[1] + (b[1] - a[1]) * s.t;
            h = Math.atan2(b[1] - a[1], b[0] - a[0]);
        } else {
            double[] k = corner(s.from, s.to);
            double a0 = Math.atan2(a[1] - k[1], a[0] - k[0]), a1 = Math.atan2(b[1] - k[1], b[0] - k[0]);
            double da = a1 - a0;
            while (da > Math.PI) da -= 2 * Math.PI;
            while (da < -Math.PI) da += 2 * Math.PI;
            double ang = a0 + da * s.t;
            u = k[0] + 0.5 * Math.cos(ang); v = k[1] + 0.5 * Math.sin(ang);
            h = ang + (da > 0 ? Math.PI / 2 : -Math.PI / 2);
        }
        return new double[]{s.c % N + u, s.c / N + v, h};
    }

    static double[] mid(int e) {
        switch (e) {
            case 0: return new double[]{0.5, 0};
            case 1: return new double[]{1, 0.5};
            case 2: return new double[]{0.5, 1};
            default: return new double[]{0, 0.5};
        }
    }

    static double[] corner(int a, int b) {
        boolean n = a == 0 || b == 0, e = a == 1 || b == 1;
        return new double[]{e ? 1 : 0, n ? 0 : 1};
    }

    // ---- trains --------------------------------------------------------------------------------------------------------
    public static final class Train {
        public int id;
        public String name = "";
        public Loc loco;
        public double v, dwell;
        public int[] cars = new int[0];     // ItemMiniStock metas: [0] the loco
        public boolean derailed;
        public int lastStation = -1;

        public double length() { return Math.max(0, cars.length - 1) * CAR; }

        public Train copy() {
            Train t = new Train();
            t.id = id; t.name = name; t.loco = loco.copy(); t.v = v; t.dwell = dwell; t.cars = cars.clone(); t.derailed = derailed; t.lastStation = lastStation;
            return t;
        }

        NBTTagCompound write() {
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("id", id); n.setString("name", name);
            n.setInteger("c", loco.c); n.setByte("f", (byte) loco.from); n.setByte("to", (byte) loco.to); n.setDouble("t", loco.t);
            n.setDouble("v", v); n.setDouble("dw", dwell); n.setIntArray("cars", cars); n.setBoolean("der", derailed); n.setInteger("ls", lastStation);
            return n;
        }

        static Train read(NBTTagCompound n) {
            Train t = new Train();
            t.id = n.getInteger("id"); t.name = n.getString("name");
            t.loco = new Loc(Math.floorMod(n.getInteger("c"), N * N), n.getByte("f") & 3, n.getByte("to") & 3, Math.max(0, Math.min(1, n.getDouble("t"))));
            t.v = n.getDouble("v"); t.dwell = n.getDouble("dw"); t.cars = n.getIntArray("cars"); t.derailed = n.getBoolean("der"); t.lastStation = n.getInteger("ls");
            return t;
        }
    }

    public Train train(int id) { for (Train t : trains) if (t.id == id) return t; return null; }

    /** the location of car i (0 = loco) */
    public Loc car(Train t, int i) { return i == 0 ? t.loco : walk(t.loco, -i * CAR, false); }

    /** put a train on the road through cell (gx,gz); its cars must fit behind it. null = it fits, else why not */
    public String place(Train t, int gx, int gz) {
        if (!inside(gx, gz)) return "that square is off the table";
        int c = idx(gx, gz);
        if (!isTrack(type(c))) return "there is no track on that square";
        for (int e = 0; e < 4; e++) {
            int to = route(c, e);
            if (to < 0) continue;
            Loc l = new Loc(c, e, to, type(c) == BUFFER ? 0.25 : 0.5);
            if (type(c) == BUFFER && l.to != ((rot(c) + 2) & 3)) continue;
            Loc tail = walk(l, -t.length(), false);
            if (tail.blocked) continue;
            t.loco = l;
            t.v = 0;
            t.derailed = false;
            for (Train o : trains) if (o.id != t.id && touching(t, o)) return "another train is in the way";
            return null;
        }
        return "the train does not fit there (not enough track behind it)";
    }

    /** any car of a within 0.45 cells of any car of b */
    public boolean touching(Train a, Train b) {
        for (int i = 0; i < Math.max(1, a.cars.length); i++) {
            double[] p = pose(car(a, i));
            for (int j = 0; j < Math.max(1, b.cars.length); j++) {
                double[] q = pose(car(b, j));
                if ((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) < 0.45 * 0.45) return true;
            }
        }
        return false;
    }

    /** a train is somewhere within `range` cells of this location (the auto signalling's track circuit) */
    private boolean occupiedAhead(Loc from, double range) {
        for (double s = 0; s <= range; s += 0.25) {
            Loc l = walk(from, s, false);
            double[] p = pose(l);
            for (Train t : trains)
                for (int i = 0; i < Math.max(1, t.cars.length); i++) {
                    double[] q = pose(car(t, i));
                    if ((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) < 0.5 * 0.5) return true;
                }
            if (l.blocked) break;
        }
        return false;
    }

    // ---- one tick of the railway ---------------------------------------------------------------------------------------------
    /** what the control panel asks of the layout this tick */
    public static final class Inputs {
        public boolean power;               // track power (12 V + mains + key + no E-stop / short)
        public final double[] target = new double[MAX_TRAINS + 1];   // per train id: cells/s, signed (direction switch)
        public boolean inertia, stationStops, autoSignals;
    }

    /** what happened (for the panel lamps, the alarm and the recorder) */
    public static final class Events {
        public String collision, derail, buffer;
        public int stationArrival = -1;
        public double amps;
    }

    public Events tick(Inputs in, double dt) {
        Events ev = new Events();
        boolean[][] was = new boolean[trains.size()][trains.size()];
        for (int i = 0; i < trains.size(); i++) for (int j = i + 1; j < trains.size(); j++) was[i][j] = touching(trains.get(i), trains.get(j));
        if (in.autoSignals)
            for (int c = 0; c < N * N; c++)
                if (type(c) == SIGNAL) {
                    int r = rot(c);
                    green[c] = !occupiedAhead(new Loc(c, r, (r + 2) & 3, 0.5), 4);
                }
        for (Train t : trains) {
            if (t.derailed || t.loco == null) { t.v = 0; continue; }
            double want = in.power ? in.target[Math.min(MAX_TRAINS, Math.max(0, t.id))] : 0;
            if (t.dwell > 0) { t.dwell -= dt; want = 0; }
            double acc = !in.power ? 6 : in.inertia ? 0.8 : 6;
            t.v += Math.max(-acc * dt, Math.min(acc * dt, want - t.v));
            if (Math.abs(t.v) < 1e-3) t.v = 0;
            if (in.power) ev.amps += 0.08 + Math.abs(t.v) * 0.12 * Math.max(1, t.cars.length);
            if (t.v == 0) continue;
            double dist = t.v * dt;
            if (dist > 0) {
                Loc before = t.loco;
                Loc n = walk(t.loco, dist, true);
                if (in.stationStops && n.c != t.lastStation && type(n.c) == STATION && n.t >= 0.5 && !(before.c == n.c && before.t >= 0.5)) {
                    n.t = 0.5; n.blocked = false;                                   // stop with the loco at the platform's middle
                    t.lastStation = n.c; t.dwell = 4; t.v = 0; ev.stationArrival = t.id;
                }
                if (n.blocked) { if (type(n.c) == BUFFER) ev.buffer = t.name; t.v = 0; }
                t.loco = n;
            } else {
                Loc tail = walk(car(t, Math.max(0, t.cars.length - 1)), dist, false);
                double moved = tail.done;
                t.loco = walk(t.loco, -moved, false);
                if (tail.blocked) { if (type(tail.c) == BUFFER) ev.buffer = t.name; t.v = 0; }
            }
            springPoints(t);
            if (t.lastStation >= 0 && t.loco.c != t.lastStation && type(t.loco.c) != STATION) t.lastStation = -1;
            if (Math.abs(t.v) > CURVE_LIMIT) {
                for (int i = 0; i < Math.max(1, t.cars.length); i++) {
                    Loc l = car(t, i);
                    if (curved(l.from, l.to)) {
                        t.derailed = true; t.v = 0;
                        ev.derail = t.name + " came off on the " + (type(l.c) == POINTS ? "points" : "curve") + " at " + (char) ('A' + l.c % N) + (l.c / N + 1);
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < trains.size(); i++)
            for (int j = i + 1; j < trains.size(); j++)
                if (!was[i][j] && touching(trains.get(i), trains.get(j))) {   // newly in contact: a collision (already touching = they may part)
                    ev.collision = trains.get(i).name + " ran into " + trains.get(j).name;
                    trains.get(i).v = 0; trains.get(j).v = 0;
                }
        return ev;
    }

    /** trailing through points springs them to the road the train came from */
    private void springPoints(Train t) {
        for (int i = 0; i < Math.max(1, t.cars.length); i++) {
            Loc l = car(t, i);
            if (type(l.c) != POINTS) continue;
            int r = rot(l.c), div = (r + (mirror(l.c) ? 3 : 1)) & 3;
            if (l.to == r || l.from == r) {
                int other = l.to == r ? l.from : l.to;
                rev[l.c] = other == div;
            }
        }
    }

    /** cells where something on the layout sits (points / signals numbering is row-major) */
    public List<Integer> numbered(int type) {
        List<Integer> l = new ArrayList<>();
        for (int gz = 0; gz < d; gz++) for (int gx = 0; gx < w; gx++) if (type(idx(gx, gz)) == type) l.add(idx(gx, gz));
        return l;
    }

    public int count(int type) { return numbered(type).size(); }

    // ---- save ------------------------------------------------------------------------------------------------------------
    public void write(NBTTagCompound t) {
        t.setByteArray("cells", cell.clone());
        byte[] r = new byte[N * N], g = new byte[N * N];
        for (int i = 0; i < N * N; i++) { r[i] = (byte) (rev[i] ? 1 : 0); g[i] = (byte) (green[i] ? 1 : 0); }
        t.setByteArray("rev", r); t.setByteArray("grn", g);
        t.setInteger("w", w); t.setInteger("d", d);
        NBTTagList l = new NBTTagList();
        for (Train tr : trains) l.appendTag(tr.write());
        t.setTag("trains", l);
    }

    public void read(NBTTagCompound t) {
        byte[] c = t.getByteArray("cells"), r = t.getByteArray("rev"), g = t.getByteArray("grn");
        for (int i = 0; i < N * N; i++) {
            cell[i] = i < c.length ? c[i] : 0;
            rev[i] = i < r.length && r[i] != 0;
            green[i] = i < g.length && g[i] != 0;
        }
        if (t.hasKey("w")) { w = Math.max(1, Math.min(N, t.getInteger("w"))); d = Math.max(1, Math.min(N, t.getInteger("d"))); }
        trains.clear();
        NBTTagList l = t.getTagList("trains", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount() && i < MAX_TRAINS; i++) trains.add(Train.read(l.getCompoundTagAt(i)));
    }

    // ---- the starter layout --------------------------------------------------------------------------------------------------
    /** an oval with a station, a tunnel, a signal, a siding with points and a buffer stop, trees and houses */
    public void demo() {
        for (int i = 0; i < N * N; i++) { cell[i] = 0; rev[i] = false; green[i] = false; }
        trains.clear();
        int x0 = 1, z0 = 1, x1 = w - 2, z1 = d - 2;
        if (x1 - x0 < 3 || z1 - z0 < 3) return;
        for (int x = x0 + 1; x < x1; x++) { set(x, z0, STRAIGHT, 1, false); set(x, z1, STRAIGHT, 1, false); }
        for (int z = z0 + 1; z < z1; z++) { set(x0, z, STRAIGHT, 0, false); set(x1, z, STRAIGHT, 0, false); }
        set(x0, z0, CURVE, 1, false); set(x1, z0, CURVE, 2, false); set(x1, z1, CURVE, 3, false); set(x0, z1, CURVE, 0, false);
        set((x0 + x1) / 2, z1, STATION, 1, false);
        set(x1, (z0 + z1) / 2, TUNNEL, 0, false);
        set((x0 + x1) / 2, z0, SIGNAL, 3, false);
        green[idx((x0 + x1) / 2, z0)] = true;
        if (z1 - z0 >= 5) {
            set(x1 - 1, z1, POINTS, 1, true);                // toe east, turnout north into the siding
            set(x1 - 1, z1 - 1, STRAIGHT, 0, false);
            set(x1 - 1, z1 - 2, BUFFER, 2, false);
        }
        int[][] trees = {{x0 + 2, z0 + 2}, {x0 + 2, z1 - 2}, {x0 - 1 >= 0 ? x0 - 1 : x0 + 3, z0}, {w - 1, z0 + 1}};
        for (int[] p : trees) if (inside(p[0], p[1]) && type(idx(p[0], p[1])) == EMPTY) set(p[0], p[1], TREE, 0, false);
        if (inside(x0 + 3, z0 + 2) && type(idx(x0 + 3, z0 + 2)) == EMPTY) set(x0 + 3, z0 + 2, HOUSE, 2, false);
        if (inside((x0 + x1) / 2, z1 + 1)) set((x0 + x1) / 2, z1 + 1, HOUSE, 0, false);
    }
}
