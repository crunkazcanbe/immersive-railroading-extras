package com.dogpound.railmap.infra;

import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.TileGrid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * A machine's real mains supply from the IR Extras grid (her rule 2026-10-11: "its real supply at the right voltage
 * from the grid ... no supply means it doesn't run"), the way the factory's Power Inlet does it: a cable, breaker or
 * transformer of a grid network TOUCHING the machine feeds it. The machine is built for a range of levels; a level it is
 * not built for is refused (wrong kind: AC for DC) or locks it out (undervoltage), and overvoltage burns it out after a
 * warning - the screwdriver repairs it (its 12 V terminal goes BURNT too, so the usual screwdriver click fixes both).
 * What it draws is a real load on that network (GridData.draw, and the touching node's drawThis).
 */
public final class Supply {
    private final Elec.Level[] built;
    public boolean ok, burnt;
    /** its cabinet's main MCB is off or fuse out (cabinet.Cabinet; memory only) */
    public boolean isolated;
    /** wired to a network at a level it is built for (even if that network is dead right now) */
    public boolean connected;
    public String what = "no grid supply touching it";
    public Elec.Level level;
    public double share, kw;
    private int over;
    /** last second's network (not saved) */
    public transient GridData.Net net;
    public transient BlockPos at;

    public Supply(Elec.Level... built) { this.built = built; }

    public String built() {
        StringBuilder b = new StringBuilder();
        for (Elec.Level l : built) b.append(b.length() == 0 ? "" : " / ").append(l.label);
        return b.toString();
    }

    private double maxVolts() { double m = 0; for (Elec.Level l : built) m = Math.max(m, l.volts); return m; }

    private boolean fits(Elec.Level lv) { for (Elec.Level l : built) if (l == lv) return true; return false; }

    /**
     * Once a second: find the network touching pos, judge its level, draw {@code watts} for one second.
     * Returns the share delivered (0..1). {@code burn} is told when overvoltage destroys the machine.
     */
    public double tick(World w, BlockPos pos, double watts, Runnable burn, java.util.function.Consumer<String> warn) {
        net = null; at = null; share = 0; kw = 0;
        if (isolated) { ok = false; connected = false; what = "isolated at its cabinet main MCB / fuse"; return 0; }
        if (burnt) { ok = false; connected = false; what = "BURNT OUT - screwdriver to repair"; return 0; }
        GridData d = GridData.get(w);
        GridData.Node node = null;
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos q = pos.offset(f);
            if (!w.isBlockLoaded(q) || !(w.getTileEntity(q) instanceof TileGrid)) continue;
            GridData.Net n = d.netAt(q, f.getOpposite());
            if (n == null) continue;
            net = n; node = d.node(q); at = q;
            break;
        }
        if (net == null) { ok = false; connected = false; level = null; what = "no grid supply touching it (needs " + built() + ")"; over = 0; return 0; }
        level = net.level;
        connected = false;
        if (level == null) { ok = false; what = "legacy network has no voltage level - give its cables a spec"; return 0; }
        if (level.volts > maxVolts() * 1.15) {                               // too much: a warning, then it goes
            ok = false;
            what = "OVERVOLTAGE: fed " + level.label + ", built for " + built();
            over++;
            if (over == 15 && warn != null) warn.accept("§6⚠ fed " + level.label + " - built for " + built() + "! Open the breaker NOW");
            if (over >= 30) { over = 0; burnt = true; what = "BURNT OUT - fed " + level.label; if (burn != null) burn.run(); }
            return 0;
        }
        over = Math.max(0, over - 2);
        if (!fits(level)) {
            ok = false;
            boolean kindOk = false;
            for (Elec.Level l : built) kindOk |= l.ac == level.ac;
            what = !kindOk ? (level.ac ? "needs DC, fed " : "needs AC, fed ") + level.label : "WRONG VOLTAGE: " + level.label + " (needs " + built() + ") - locked out";
            return 0;
        }
        connected = true;                                                    // wired to a network it is built for
        if (!net.problem.isEmpty()) { ok = false; what = "supply dead: " + net.problem; return 0; }
        ok = true;
        what = level.label;
        if (watts <= 0) { share = 1; return 1; }
        long fe = Math.max(1, Math.round(watts / 100));                       // 1 FE / s = 100 W
        long got = d.draw(net, fe);
        if (node != null) node.drawThis += got;
        share = Math.min(1, got / (double) fe);
        kw = watts * share / 1000;
        if (share < 0.5) { ok = false; what = "supply too weak: " + Math.round(share * 100) + "% of " + Math.round(watts / 1000) + " kW"; }
        return share;
    }

    public String status() { return (ok ? "§a" : burnt ? "§c" : "§e") + what + (ok && kw > 0 ? String.format(Locale.ROOT, " §7(%.1f kW)", kw) : ""); }

    public void write(NBTTagCompound t) { t.setBoolean("supBurnt", burnt); }
    public void read(NBTTagCompound t) { burnt = t.getBoolean("supBurnt"); }
}
