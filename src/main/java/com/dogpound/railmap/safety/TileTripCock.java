package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Train-stop trip cock (her batch-3 list #11): the trackside arm beside the rail at a signal. Air pressure (worked by a
 * 12 V solenoid valve) holds the arm DOWN while the signal shows a proceed aspect; at danger the valve vents and a
 * spring throws the arm UP, where it strikes the trip cock on a passing train and vents its brake pipe (TripCocks).
 * <ul>
 *   <li>SIGNAL mode: arm up whenever the wrench-linked signal shows STOP or is dark (or nothing is linked);</li>
 *   <li>SCTS mode (speed-controlled train stop): the arm stands up permanently as a speed trap - it drops only for a
 *   train approaching under the set speed (15 / 25 / 40 km/h), e.g. on the approach to a buffer stop;</li>
 *   <li>no 12 V or no air: the spring raises the arm (fail-safe); ISOLATED (key) = arm clamped down, standing alarm;</li>
 *   <li>TEST raises the arm for 5 s; the counter shows how many trains it has tripped.</li>
 * </ul>
 */
public class TileTripCock extends SafetyTile {
    private long signal;                         // linked signal (0 = none)
    private int trips;
    private long testUntil, lowerUntil;          // SCTS: lowered for a slow train until
    private boolean wasUp;

    @Override public String wiredName() { return "Train stop " + pos.getX() + "," + pos.getZ(); }

    public boolean isolated() { return ctl.get("k_iso") == 1; }
    public int trips() { return trips; }
    public int scts() { return new int[]{15, 25, 40}[Math.min(2, Math.max(0, ctl.get("s_kmh")))]; }
    public String modeName() { return ctl.get("s_mode") == 1 ? "SCTS " + scts() + " km/h" : "SIGNAL"; }

    public TileSignalMast mast() {
        if (signal == 0 || world == null) return null;
        BlockPos p = BlockPos.fromLong(signal);
        return world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileSignalMast m ? m : null;
    }

    public String signalName() { TileSignalMast m = mast(); return m == null ? (signal == 0 ? "-" : "lost") : SignalSighting.name(m); }

    /** the SCTS trap trips a train going faster than its setting */
    static boolean sctsTrips(double kmh, double set) { return kmh > set; }

    /** is the arm up (in the tripping position) right now */
    public boolean armUp() {
        if (isolated()) return false;
        if (world != null && world.getTotalWorldTime() < testUntil) return true;
        if (!powered()) return true;                                             // no air valve power: the spring raises it
        if (ctl.get("s_mode") == 1) return world == null || world.getTotalWorldTime() >= lowerUntil;
        TileSignalMast m = mast();
        if (m == null) return true;
        Aspect a = m.shownAspect();
        return a == Aspect.DARK || a.isStop();
    }

    /** SCTS: a train approaching under the set speed - drop the arm for it */
    void lowerFor(long ticks) { lowerUntil = Math.max(lowerUntil, world.getTotalWorldTime() + ticks); }

    void tripped(String what) {
        trips++;
        markDirty();
        SafetyData.get(world).history(world, pos, wiredName(), "train stop", "TRIPPED " + what);
    }

    @Override
    public String gearState() {
        if (isolated()) return "ISOLATED - arm clamped down";
        if (!powered()) return "NO 12 V - arm up on the spring";
        return "arm " + (armUp() ? "UP" : "down") + " · " + modeName() + (ctl.get("s_mode") == 0 ? " · signal " + signalName() : "");
    }

    @Override public int wiredLamp() { return isolated() || !powered() ? 3 : armUp() ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("armUp", armUp()); m.put("trips", trips); m.put("isolated", isolated()); m.put("mode", modeName()); m.put("signal", signalName()); }

    @Override
    protected void serverTick() {
        if (age % 5 != 0) return;
        boolean up = armUp();
        if (up != wasUp) {
            wasUp = up;
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.RELAY, SoundCategory.BLOCKS, 0.6f, up ? 0.8f : 1.2f);
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.lever"), SoundCategory.BLOCKS, 0.5f, up ? 0.7f : 1.0f);
        }
        put("arm", up);
        put("up", up);
        put("down", !up);
        put("air", powered() && !isolated());
        put("isol", isolated());
        put("pwr", powered());
        put("lnk", mast() != null);
        put("nolnk", ctl.get("s_mode") == 0 && mast() == null);
        put("trips", trips);
        text("sig", ctl.get("s_mode") == 1 ? "TRAP " + scts() + " KMH" : "SIG " + signalName().toUpperCase());
        String key = "trainstop:" + pos.toLong();
        AlarmManager am = AlarmManager.get(world);
        if (age % 20 == 0) {
            if (isolated()) am.raise(world, key, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MINOR, "Train stop at " + pos.getX() + "," + pos.getZ() + " ISOLATED - trains are not protected", pos, true);
            else am.clear(key);
        }
    }

    @Override
    protected boolean keepTextUnpowered() { return true; }

    /** the arm is mechanical: it shows its position with or without 12 V */
    @Override
    public int controlValue(String id) {
        if (id.equals("arm")) return armUp() ? 1 : 0;
        return super.controlValue(id);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test":
                if (isolated()) return "§cIsolated - put the key back to NORMAL to test";
                testUntil = world.getTotalWorldTime() + 100;
                SafetyData.get(world).history(world, pos, wiredName(), "train stop", "arm test by " + who(who));
                return "§eTest: arm raised for 5 s";
            case "lamptest": return lampTest();
            case "arm": return "§7The arm is worked by the air motor - " + (armUp() ? "UP" : "down");
            case "k_iso": {
                int v = ctl.get("k_iso") == 0 ? 1 : 0;
                ctl.set("k_iso", v);
                SafetyData.get(world).history(world, pos, wiredName(), "train stop", (v == 1 ? "ISOLATED" : "restored to service") + " by " + who(who));
                com.dogpound.railmap.alarm.Recorder.event(world, "TRAIN STOP " + pos.getX() + "," + pos.getZ() + (v == 1 ? " isolated" : " back in service") + " by " + who(who));
                return v == 1 ? "§6Train stop ISOLATED - arm clamped down" : "§aTrain stop back in service";
            }
            case "s_mode": ctl.set("s_mode", 1 - ctl.get("s_mode")); return "§eMode: " + modeName();
            case "s_kmh": ctl.set("s_kmh", (ctl.get("s_kmh") + 1) % 3); return "§eSpeed trap " + scts() + " km/h";
            default: return "§7" + gearState();
        }
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!(world.getTileEntity(pick) instanceof TileSignalMast m)) return "§cPick the signal this train stop protects";
        signal = pick.toLong();
        markDirty();
        return "§aTrain stop linked to signal " + SignalSighting.name(m);
    }

    @Override protected void save(NBTTagCompound t) { t.setLong("sig", signal); t.setInteger("trips", trips); }
    @Override protected void load(NBTTagCompound t) { signal = t.getLong("sig"); trips = t.getInteger("trips"); }
}
