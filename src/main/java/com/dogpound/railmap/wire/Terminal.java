package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMapConfig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The 12 V control terminal every piece of railway gear carries (requested feature). Lay the 12 V DC power pair beside
 * it and it powers up; land a signal colour on it (right-click with Instrument Wire) and the Control Center console,
 * gauges, Control Buttons and the Data Link on that colour see it and work its controls. With no 12 V it fails safe
 * (signals dark = stop, crossings down, track circuits occupied, screens dark); the wrong voltage burns its electronics
 * out until a screwdriver puts a new board in. Gear placed before this existed keeps working unpowered ("legacy") until
 * the first time it gets 12 V, so nothing in an old world goes dark by itself.
 */
public final class Terminal {
    public int ctl = -1;
    public boolean burnt;
    public boolean legacy;
    /** 0 dead, 1 12 V DC ok */
    public int supply;
    public String what = "no 12 V DC supply";
    private int age = -1;

    /** server tick: re-checks the 12 V supply once a second; returns true when powered() changed (sync the client) */
    public boolean tick(World w, BlockPos p, String name) {
        if (w.isRemote) return false;
        if (age < 0) age = (int) (p.toLong() & 15);                             // stagger the wire walks
        if (age++ % 20 != 0) return false;
        boolean was = lastPowered == null ? powered() : lastPowered;
        if (backup > 0) backup--;
        if (burnt) { supply = 0; what = "BURNT OUT - screwdriver to fix"; }
        else {
            WireNet.Supply s = WireNet.power(w, p);
            if (s.state == 2) { burnt = true; supply = 0; what = "BURNT OUT - fed " + s.what; WireNet.burnOut(w, p, name, s.what); }
            else {
                int had = supply;
                supply = s.state; what = s.what; if (supply == 1) legacy = false;
                if (had != 1 && supply == 1 && com.dogpound.railmap.api.QuestEvents.active())   // quest hook: this gear just got its 12 V
                    com.dogpound.railmap.api.QuestEvents.fire(w, null, p, "gear_powered", "block", String.valueOf(w.getBlockState(p).getBlock().getRegistryName()), "name", name);
            }
        }
        lastPowered = powered();
        return was != lastPowered;
    }

    /** batch-3 #14: seconds of battery feed left from a Signal Battery Cabinet (memory only; the cabinet refreshes it each second it feeds) */
    public int backup;
    private Boolean lastPowered;

    /** running on a battery cabinet's feed right now */
    public boolean onBackup() { return backup > 0 && supply != 1 && !burnt && !testCut; }

    /** the fault lab's 12 V supply cut (memory only, never saved: a restart can't leave gear dead) */
    public boolean testCut;

    /** its cabinet's 12 V control MCB is off or fuse out (cabinet.Cabinet sets it; memory only, re-applied each half second) */
    public boolean isolated;

    public boolean powered() { return !isolated && !testCut && !burnt && (supply == 1 || backup > 0 || legacy || !RailMapConfig.gearNeedsPower); }

    /** one line for chat, settings and the console */
    public String status() {
        if (testCut) return "§6SUPPLY CUT by the fault lab (test mode)";
        if (isolated) return "§cISOLATED§7 - 12 V control MCB off or fuse out in its cabinet";
        if (burnt) return "§cBURNT OUT§7 - screwdriver to fix";
        if (supply == 1) return "§a12 V DC ok";
        if (backup > 0) return "§e12 V from a battery cabinet§7 - mains supply lost (" + what + ")";
        if (legacy || !RailMapConfig.gearNeedsPower) return "§7runs without 12 V (placed before wiring / config) - " + what;
        return "§cNO 12 V§7 - " + what;
    }

    public String repair(String name) {
        if (!burnt) return null;
        burnt = false; age = 0;
        return "§aRepaired - new electronics in the " + name;
    }

    public void write(NBTTagCompound t) {
        NBTTagCompound c = new NBTTagCompound();
        c.setInteger("ctl", ctl); c.setBoolean("burnt", burnt); c.setBoolean("legacy", legacy); c.setByte("sup", (byte) supply);
        t.setTag("term", c);
    }

    public void read(NBTTagCompound t) {
        if (!t.hasKey("term")) { legacy = true; return; }                       // saved before the terminal existed
        NBTTagCompound c = t.getCompoundTag("term");
        ctl = c.getInteger("ctl"); burnt = c.getBoolean("burnt"); legacy = c.getBoolean("legacy"); supply = c.getByte("sup");
    }
}
