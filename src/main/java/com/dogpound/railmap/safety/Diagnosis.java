package com.dogpound.railmap.safety;

import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * Interactive fault diagnosis (her ChatGPT batch-3 #48): the jobs, the procedure and the multimeter readings behind
 * the Fault Diagnosis Terminal. A job is a real fault on a real piece of 12 V signalling gear - a self-test FAIL, a
 * burnt board, gear with no 12 V, a fault the Fault Lab injected (or a hidden TRAINING fault). Its true cause is
 * worked out from the world: BURNT board, SUPPLY dead, WIRING (no 12 V pair reaching it), LAMP, DETECTOR, RADIO,
 * POINTS. The technician must MEASURE with the multimeter (at the gear; for a supply / wiring fault also at a 12 V
 * source near it) - the readings are real (13.6 V float, 0 V dead, board current, lamp current, relay coil volts...),
 * then DIAGNOSE the cause, REPAIR it in the world (screwdriver, re-wire, restore the supply, or fit the part for a lab
 * fault) and VERIFY with a self-test. Wrong measurements and wrong diagnoses cost points.
 */
public final class Diagnosis {
    private Diagnosis() {}

    public enum Cause {
        SUPPLY("12 V supply dead"), WIRING("12 V wiring open"), BURNT("burnt control board"), LAMP("signal lamp failed"),
        DETECTOR("train detection fault"), RADIO("radio transmitter fault"), POINTS("points detection lost");
        public final String label;
        Cause(String l) { label = l; }
    }

    public static final class Job {
        public long pos;
        public String name = "", kind = "", source = "";
        public Cause cause;
        public FaultLab.Kind lab;
        public boolean hidden;

        String key() { return pos + ":" + cause; }
    }

    // ---- the jobs ------------------------------------------------------------------------------------------------------
    /** every live fault on 12 V signalling gear, nearest first to `near` */
    static List<Job> jobs(World w, BlockPos near) {
        Map<Long, Job> out = new LinkedHashMap<>();
        SafetyData d = SafetyData.get(w);
        for (SafetyData.Fault f : d.faults) {
            Cause c = ofLab(f.kind);
            if (c == null) continue;
            Job j = job(w, f.pos, c, "fault lab");
            j.lab = f.kind;
            out.put(f.pos, j);
        }
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (t.isInvalid() || !(t instanceof IWiredGear g) || out.containsKey(t.getPos().toLong())) continue;
            Terminal term = g.term();
            Cause c = null;
            String src = "";
            if (term.burnt) { c = Cause.BURNT; src = "burnt board"; }
            else if (!term.powered() && !term.legacy) { c = supplyCause(w, t.getPos()); src = "no 12 V"; }
            else {
                SafetyData.Gear r = d.gear.get(t.getPos().toLong());
                if (r != null && r.state == 2) { c = ofText(r.last); src = "self-test FAIL"; }
            }
            if (c != null && (t instanceof SafetyTile || t instanceof TileSignalMast || t instanceof TileTrackCircuit || d.gear.containsKey(t.getPos().toLong())))
                out.put(t.getPos().toLong(), job(w, t.getPos().toLong(), c, src));
        }
        List<Job> l = new ArrayList<>(out.values());
        if (near != null) l.sort(Comparator.comparingDouble(j -> BlockPos.fromLong(j.pos).distanceSq(near)));
        return l;
    }

    static Job job(World w, long pos, Cause c, String src) {
        Job j = new Job();
        j.pos = pos; j.cause = c; j.source = src;
        TileEntity t = w.isBlockLoaded(BlockPos.fromLong(pos)) ? w.getTileEntity(BlockPos.fromLong(pos)) : null;
        j.name = t instanceof IWiredGear g ? g.wiredName() : c == Cause.POINTS ? "Points" : "Gear";
        SafetyData.Gear r = SafetyData.get(w).gear.get(pos);
        j.kind = r != null ? r.kind : t instanceof TileSignalMast ? "signal" : "";
        return j;
    }

    static Cause ofLab(FaultLab.Kind k) {
        switch (k) {
            case LAMP: return Cause.LAMP;
            case TC_DROP: case FALSE_OCC: case AXLE_HEAD: return Cause.DETECTOR;
            case RADIO_CUT: return Cause.RADIO;
            case POINT_STUCK: return Cause.POINTS;
            case POWER_CUT: return null;                                            // zone-wide: shows as many no-12-V jobs
            default: return null;                                                   // grid bench faults: the commissioning bench
        }
    }

    static Cause ofText(String s) {
        String t = s == null ? "" : s.toLowerCase(Locale.ROOT);
        if (t.contains("burnt")) return Cause.BURNT;
        if (t.contains("lamp")) return Cause.LAMP;
        if (t.contains("off air") || t.contains("radio")) return Cause.RADIO;
        if (t.contains("occup") || t.contains("relay dropped") || t.contains("disturb") || t.contains("head")) return Cause.DETECTOR;
        if (t.contains("points")) return Cause.POINTS;
        return Cause.SUPPLY;
    }

    /** a dead terminal: is it the supply (a 12 V network is there but dead / cut) or the wiring (no 12 V pair reaches it)? */
    static Cause supplyCause(World w, BlockPos p) {
        TileEntity t = w.getTileEntity(p);
        if (t instanceof IWiredGear g && g.term().testCut) return Cause.SUPPLY;
        WireNet.Supply s = WireNet.power(w, p);
        String what = s.what == null ? "" : s.what;
        return what.startsWith("supply dead") || what.startsWith("supply has no") ? Cause.SUPPLY : Cause.WIRING;
    }

    /** the symptoms a maintainer is told about (what the railway sees) */
    static String symptom(Job j) {
        switch (j.cause) {
            case SUPPLY: case WIRING: case BURNT: return j.name + " dead - no indication on the console, fails safe";
            case LAMP: return j.name + " showing DARK - signal in rear held at STOP";
            case DETECTOR: return j.name + " shows OCCUPIED / disturbed with no train";
            case RADIO: return j.name + " off air - trains lose their radio link";
            case POINTS: return "Points out of correspondence - routes refused";
            default: return j.name;
        }
    }

    /** what has to be measured: "gear", plus "source" for a supply / wiring fault */
    static List<String> needs(Cause c) { return c == Cause.SUPPLY || c == Cause.WIRING ? Arrays.asList("gear", "source") : Collections.singletonList("gear"); }

    /** the fault has gone from the world */
    static boolean fixed(World w, Job j) {
        BlockPos p = BlockPos.fromLong(j.pos);
        if (j.lab != null) { for (SafetyData.Fault f : SafetyData.get(w).faults) if (f.kind == j.lab && f.pos == j.pos) return false; }
        if (!w.isBlockLoaded(p)) return false;
        TileEntity t = w.getTileEntity(p);
        if (t instanceof IWiredGear g) {
            if (g.term().burnt) return false;
            if (!g.term().powered()) return false;
        }
        return true;
    }

    /** fit the part: a lab fault cleared (only after the right diagnosis, by the technician at the gear) */
    static String fitPart(World w, Job j, EntityPlayer who) {
        if (j.lab == null) return null;
        FaultLab.clear(w, j.lab, BlockPos.fromLong(j.pos));
        SafetyData.get(w).history(w, BlockPos.fromLong(j.pos), null, null, "REPAIRED (" + j.cause.label + ") by " + (who == null ? "console" : who.getName()));
        return "§aReplacement fitted: " + j.cause.label + " repaired";
    }

    // ---- the multimeter ------------------------------------------------------------------------------------------------
    /** grid/ItemMultimeter on a block that is not grid plant: true when it was 12 V gear (reading given) */
    public static boolean meter(EntityPlayer p, World w, BlockPos pos) {
        TileEntity t = w.getTileEntity(pos);
        if (!(t instanceof IWiredGear) && !com.dogpound.railmap.scan.SwitchControl.isRail(w, pos)) return false;
        List<String> lines = new ArrayList<>();
        double volts = 0;
        if (t instanceof IWiredGear g) {
            Terminal term = g.term();
            WireNet.Supply s = WireNet.power(w, pos);
            volts = term.testCut ? 0 : s.state == 1 ? 13.55 + (pos.toLong() & 7) * 0.01 : s.state == 2 ? -1 : 0;
            boolean board = term.powered() && !term.burnt;
            lines.add("§b§l⚡ Multimeter §r§7· " + g.wiredName() + " · 12 V terminal");
            lines.add(volts < 0 ? "§c" + s.what + " across the terminals - WRONG SUPPLY" : String.format(Locale.ROOT, "§7Terminal §f%.2f V DC§7 %s", volts, volts > 1 ? "§a(float charge ok)" : term.testCut ? "§6(supply cut by the fault lab)" : "§c(no supply)"));
            lines.add(String.format(Locale.ROOT, "§7Board current §f%.2f A§7 %s", board ? 0.95 + (pos.toLong() & 3) * 0.04 : 0.0,
                    term.burnt ? (volts > 1 ? "§c- volts present, no current: BOARD OPEN CIRCUIT" : "§c- board burnt") : board ? "§a(12 W load)" : ""));
            if (t instanceof TileSignalMast m) {
                boolean out = FaultLab.has(w, FaultLab.Kind.LAMP, pos);
                lines.add(String.format(Locale.ROOT, "§7Lamp current §f%.2f A§7 %s", board && !out ? 0.85 : 0.0, out ? "§c- lamp filament OPEN" : board ? "§a(LED lit: " + m.shownAspect().label + ")" : ""));
            } else if (t instanceof TileTrackCircuit tc) {
                boolean drop = FaultLab.has(w, FaultLab.Kind.TC_DROP, pos), falseOcc = FaultLab.has(w, FaultLab.Kind.FALSE_OCC, pos);
                double coil = !board || drop ? 0.0 : tc.occupied() ? (falseOcc ? 0.31 : 0.18) : 1.82;
                lines.add(String.format(Locale.ROOT, "§7Track relay coil §f%.2f V§7 %s", coil, drop ? "§c- relay feed open: RELAY DROPPED" : coil < 0.5 && board ? "§e- relay shunted (occupied" + (falseOcc ? " - no train on it!)" : ")") : "§a(picked)"));
            } else if (t instanceof TileAxleHead h) {
                lines.add(String.format(Locale.ROOT, "§7Wheel sensor loop §f%d mA§7 %s", h.healthy() ? 8 : 0, h.healthy() ? "§a(ok)" : "§c- sensor loop open"));
            } else if (t instanceof TileRadioMast r) {
                lines.add(String.format(Locale.ROOT, "§7RF forward power §f%.1f W§7 · VSWR §f%s", r.healthy() ? Math.pow(10, r.txDbm() / 10) / 1000 : 0.0, r.healthy() ? "1.2" : "-  §c(no RF out)"));
            }
            lines.add("§7Insulation (500 V megger) §f" + (200 + (pos.toLong() & 63)) + " MΩ §apass");
        } else {
            boolean stuck = FaultLab.stuck(w, pos);
            lines.add("§b§l⚡ Multimeter §r§7· point machine detection contacts");
            lines.add(stuck ? "§cNormal contacts OPEN · reverse contacts OPEN - out of correspondence (blade jammed)" : "§7Detection contacts §acorrespond§7 (" + com.dogpound.railmap.auto.Interlocking.stateAt(w, pos) + ")");
            volts = stuck ? 0 : 12;
        }
        for (String s : lines) p.sendMessage(new TextComponentString(s));
        record(p, w, pos, volts, false);
        return true;
    }

    /** grid/ItemMultimeter on a grid block: a 12 V DC source near a supply / wiring job counts as the source reading */
    public static void gridMeasured(EntityPlayer p, World w, BlockPos pos, double volts, boolean dc12) {
        if (dc12) record(p, w, pos, volts, true);
    }

    private static void record(EntityPlayer p, World w, BlockPos pos, double volts, boolean source) {
        for (TileDiagTerminal t : SigTicker.tiles(w, TileDiagTerminal.class)) t.measured(p, pos, volts, source);
    }
}
