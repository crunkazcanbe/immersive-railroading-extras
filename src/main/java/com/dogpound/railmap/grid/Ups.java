package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 12 V Battery Charger / UPS (her ChatGPT list #41: "signal systems, crossing warnings, emergency lighting and control-room
 * communications can have batteries"). AC in at the back charges a real 12 V lead-acid battery (float 13.6 V); the 12 V
 * DC network on its other faces runs from the charger while the mains is there and from the battery when it is not, so
 * every signal, crossing, track circuit, relay and screen on that 12 V wire keeps working through a power cut - for as
 * long as the battery lasts. The load is real: each piece of 12 V gear that draws from it this second counts (~12 W).
 * Flat battery: the low-voltage disconnect drops the output. Batteries age with every cycle; BATTERY TEST proves them.
 */
public final class Ups {
    private Ups() {}

    public static final int[] AH = {24, 65, 100, 200};
    static final double W_PER_DEVICE = 12, W_SELF = 15;
    /** mWh per watt for one tick-second on the Minecraft clock (72x real time, like the day): batteries last game hours */
    static final double HOUR = 1000 * 72 / 3600.0;

    /** 12 V devices drawing from each UPS this second (WireNet.judge reports them) */
    private static final Map<Integer, Map<Long, Integer>> DRAW = new HashMap<>();
    private static final Map<Integer, Map<Long, Integer>> LAST = new HashMap<>();

    /** a 12 V device was powered from this network this second */
    public static void note(World w, GridData.Net net) {
        if (w == null || net == null) return;
        GridData d = GridData.get(w);
        for (long c : net.ins) {
            GridData.Node u = d.nodes().get(c);
            if (u != null && u.kind == GridKind.UPS) { DRAW.computeIfAbsent(w.provider.getDimension(), k -> new HashMap<>()).merge(c, 1, Integer::sum); return; }
        }
    }

    public static int devices(World w, long ups) {
        Map<Long, Integer> m = LAST.get(w.provider.getDimension());
        return m == null ? 0 : m.getOrDefault(ups, 0);
    }

    /** battery capacity in mWh (Ah x 12 V, less what age has taken) */
    public static double capacity(GridData.Node n) { return n.get("ah", 65) * 12.0 * 1000 * health(n); }
    public static double health(GridData.Node n) { return n.st.containsKey("bh") ? n.st("bh") / 1000.0 : 1.0; }
    public static double soc(GridData.Node n) { return Math.max(0, Math.min(1, n.energy / Math.max(1, capacity(n)))); }
    /** battery terminal voltage: float charge 13.6 V on mains; 12.7 (full) .. 11.5 (empty) on battery */
    public static double volts(GridData.Node n) { return n.st("onbat") == 0 && n.st("ac") == 1 ? 13.6 : 11.5 + 1.2 * soc(n); }

    static void tick(World w, GridData d) {
        int dim = w.provider.getDimension();
        Map<Long, Integer> now = DRAW.remove(dim);
        LAST.put(dim, now == null ? new HashMap<>() : now);
        List<GridData.Net> nets = d.nets();
        for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
            GridData.Node n = e.getValue();
            if (n.kind != GridKind.UPS) continue;
            long pos = e.getKey();
            BlockPos bp = BlockPos.fromLong(pos);
            if (!n.st.containsKey("bh")) { n.st.put("bh", 1000); n.energy = (long) (capacity(n) * 0.8); }   // ships 80 % charged
            GridData.Net up = n.upNet >= 0 && n.upNet < nets.size() ? nets.get(n.upNet) : null;
            double inPu = up == null || up.level == null ? 0 : up.puSource;
            boolean acIn = up != null && up.problem.isEmpty() && up.level != null && up.level.ac;
            boolean inRange = acIn && inPu > 0.85 && inPu < 1.12;
            int test = n.st("tst");
            if (test > 0) n.st.put("tst", test - 1);
            boolean onMains = inRange && test == 0 && n.dcOn;
            int devs = devices(w, pos);
            double loadW = n.dcOn && !n.lvd ? devs * W_PER_DEVICE + W_SELF : W_SELF;
            double cap = capacity(n);
            n.st.put("dev", devs);
            n.st.put("lw", (int) Math.round(loadW));
            n.st.put("ac", acIn ? 1 : 0);
            n.st.put("cf", acIn && !inRange ? 1 : n.st("cf") == 2 ? 2 : 0);      // 1 = input out of range, 2 = charger failed (battery test)
            boolean wasBat = n.st("onbat") == 1;
            if (onMains) {
                double room = Math.max(0, cap - n.energy);
                double chgW = Math.min(n.get("chg", 10) * 13.6, room / HOUR);                    // limited by the charger, tapering when full
                if (room < cap * 0.05) chgW = Math.min(chgW, 0.02 * cap / 1000.0);               // float: a trickle near full
                double wantW = (chgW + loadW) / 0.85;
                long fe = (long) Math.ceil(wantW / Elec.W_PER_FE_T * 20);
                long got = fe <= 0 ? 0 : d.draw(up, fe);
                double frac = fe <= 0 ? 1 : (double) got / fe;
                n.energy = (long) Math.min(cap, n.energy + chgW * frac * HOUR);
                n.loadVA = wantW * frac;
                n.st.put("cw", (int) Math.round(chgW * frac));
                n.st.put("onbat", 0);
                if (n.lvd) { n.lvd = false; d.touch(); History.log(w, bp, "UPS", "mains back - output restored, recharging"); }
            } else {
                n.loadVA = 0;
                n.st.put("cw", 0);
                n.st.put("onbat", 1);
                double used = loadW * HOUR;
                n.energy = (long) Math.max(0, n.energy - used);
                if (w.rand.nextDouble() < used / Math.max(1, cap) * 1000 / 500.0) n.st.put("bh", Math.max(300, n.st("bh") - 1));   // ~500 full cycles of life
                if (n.energy <= 0 && !n.lvd) {
                    n.lvd = true; n.st.put("bh", Math.max(300, n.st("bh") - 20));                 // a deep discharge hurts lead-acid
                    d.touch();
                    History.log(w, bp, "UPS", "battery EXHAUSTED - low-voltage disconnect, 12 V output off");
                    GridTicker.alarm(w, pos, String.format(Locale.ROOT, "§c⚡ UPS at %d %d %d battery EXHAUSTED - its 12 V network is DEAD: signals dark, crossings down", bp.getX(), bp.getY(), bp.getZ()));
                }
                if (test == 1) {                                                  // end of a battery test
                    boolean ok = health(n) >= 0.6 && soc(n) > 0.3;
                    n.st.put("tf", ok ? 0 : 1);
                    History.log(w, bp, "TEST", String.format(Locale.ROOT, "battery test %s: health %.0f%%, charge %.0f%%", ok ? "PASSED" : "FAILED", health(n) * 100, soc(n) * 100));
                }
            }
            boolean onBat = n.st("onbat") == 1;
            if (onBat != wasBat && test == 0) {
                if (onBat) History.log(w, bp, "UPS", "mains FAILED - on battery, " + autonomy(n) + " autonomy");
                else History.log(w, bp, "UPS", "mains restored after outage");
            }
            String k = "ups:" + pos;
            if (onBat && test == 0 && n.dcOn) Protection.standing(w, k + ":bat", AlarmManager.Cat.POWER, AlarmManager.Sev.MAJOR,
                    String.format(Locale.ROOT, "UPS at %d %d %d ON BATTERY - mains lost, %d devices on 12 V, %s left", bp.getX(), bp.getY(), bp.getZ(), devs, autonomy(n)), bp);
            else AlarmManager.get(w).clear(k + ":bat");
            if (soc(n) < 0.2 && onBat) Protection.standing(w, k + ":low", AlarmManager.Cat.POWER, AlarmManager.Sev.CRITICAL,
                    String.format(Locale.ROOT, "UPS at %d %d %d BATTERY LOW %.0f%% - %s to 12 V shutdown", bp.getX(), bp.getY(), bp.getZ(), soc(n) * 100, autonomy(n)), bp);
            else AlarmManager.get(w).clear(k + ":low");
            if (health(n) < 0.6) Protection.standing(w, k + ":eol", AlarmManager.Cat.WIRED, AlarmManager.Sev.MINOR,
                    String.format(Locale.ROOT, "UPS at %d %d %d battery END OF LIFE (%.0f%% capacity) - replace it (screwdriver)", bp.getX(), bp.getY(), bp.getZ(), health(n) * 100), bp);
            else AlarmManager.get(w).clear(k + ":eol");
            d.markDirty();
        }
    }

    /** how long the battery lasts at the present load */
    public static String autonomy(GridData.Node n) {
        double w = Math.max(1, n.st("lw"));
        double h = n.energy / 1000.0 / w;                                       // Wh / W
        if (n.st("onbat") == 0) h = capacity(n) * soc(n) / 1000.0 / w;
        int mins = (int) Math.round(h * 60);
        return mins >= 6000 ? "99:59+" : String.format(Locale.ROOT, "%d:%02d h", mins / 60, mins % 60);
    }
}
