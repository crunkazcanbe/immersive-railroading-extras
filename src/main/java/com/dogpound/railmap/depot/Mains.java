package com.dogpound.railmap.depot;

import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.wire.Terminal;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Mains power for the heavy depot and station machines (wheel lathe, bogie drop, wash plant, lift, escalator, lamps,
 * heaters): an IR Extras grid cable or machine touching the block, 120-480 V AC. Each second the running machine draws
 * its load in FE from that network (intakes, generators and batteries really give it up). 4.16 kV or more into a
 * 480 V motor burns its electronics out (the 12 V terminal's board - a screwdriver fixes it); DC or 12-48 V is just
 * the wrong supply. The 12 V terminal still powers the controls and screens.
 */
public final class Mains {
    public int state;                   // 0 none, 1 ok
    public String what = "no mains";
    public long fe;                     // drawn in the last second
    /** its cabinet's main MCB is off or fuse out (cabinet.Cabinet; memory only) */
    public boolean isolated;
    private int age = -1;

    /** once a second: look for a supply and draw `load` FE from it (0 = idle, just look). Returns true when state changed. */
    public boolean tick(World w, BlockPos p, Terminal term, String name, long load) {
        if (age < 0) age = (int) (p.toLong() & 15);
        if (age++ % 20 != 0) return false;
        int was = state;
        fe = 0;
        if (isolated) { state = 0; what = "isolated at its cabinet main MCB / fuse"; return was != state; }
        if (!DepotConfig.mainsRequired) { state = 1; what = "mains not required (config)"; return was != state; }
        state = 0;
        what = "no mains - lay a grid cable to it (120-480 V AC)";
        GridData d = GridData.get(w);
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos nb = p.offset(f);
            if (d.node(nb) == null) continue;
            GridData.Net n = d.netAt(nb, f.getOpposite());
            if (n == null) continue;
            if (!n.problem.isEmpty()) { what = "mains dead: " + n.problem; continue; }
            Elec.Level L = n.level;
            if (L != null && L.volts > 690) {
                if (term != null && !term.burnt) { term.burnt = true; WireNet.burnOut(w, p, name, L.label); }
                what = "BURNT OUT - fed " + L.label;
                return true;
            }
            if (L != null && (!L.ac || L.volts < 100)) { what = "wrong supply: " + L.label + " (needs 120-480 V AC)"; continue; }
            if (load > 0) {
                long got = d.draw(n, load);
                if (got < load) { what = "supply can't carry the load (" + got + "/" + load + " FE)"; continue; }
                fe = got;
            }
            state = 1;
            what = L == null ? "mains" : L.label;
            break;
        }
        if (state == 0) {                                                       // station emergency power (batch 3 #30): battery / standby generator
            String em = null;
            try { em = com.dogpound.railmap.stn.EmergencyPower.feed(w, p, name, load); } catch (RuntimeException | LinkageError ignored) { }
            if (em != null) { state = 1; what = em; fe = load; }
        }
        return was != state;
    }

    public boolean ok() { return state == 1; }

    public void write(NBTTagCompound t) { t.setByte("mains", (byte) state); t.setString("mainsW", what); }
    public void read(NBTTagCompound t) { state = t.getByte("mains"); what = t.getString("mainsW"); }
}
