package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.server.TrainTracker;
import com.dogpound.railmap.signal.SignalRegistry;
import com.dogpound.railmap.signal.TileSignalMast;
import cam72cam.mod.math.Vec3i;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Emergency Stop Plunger (her ChatGPT list #23): the big red mushroom on a lineside post. Hit it and every signal
 * reading into its zone goes to danger, trains on the radio inside the zone are stopped, the signaller's desk gets an
 * emergency call and a critical alarm goes up. That opens an incident, and the protection procedure is tracked on the
 * plunger's lamps and the console:
 * <ol>
 *   <li>signals protecting the zone at danger (checked from the real aspects);</li>
 *   <li>every train in the zone at a stand (checked from the real speeds);</li>
 *   <li>the signaller has answered the emergency call (Radio Dispatch Desk ANSWER, or the console);</li>
 *   <li>the site is protected by a possession (a Track Blocking Switch in BLOCK covering the zone) - optional.</li>
 * </ol>
 * It only resets with the key (turn to RESET, then pull the mushroom) once steps 2 and 3 are done. With no 12 V the
 * plunger circuit is broken, which (fail-safe ESP_WIRING) counts as operated.
 */
public class TileEmergencyPlunger extends SafetyTile {
    private final Set<String> stopped = new HashSet<>();
    private String by = "";

    public int zone() { return new int[]{32, 64, 128}[Math.min(2, Math.max(0, ctl.get("s_zone")))]; }

    /** the plunger is in (pushed), or its circuit is dead and that counts as operated */
    public boolean operated() { return ctl.get("m_esp") == 1 || !powered() && FailSafe.safe(world, FailSafe.ESP_WIRING); }

    @Override public String wiredName() { return "Emergency stop plunger"; }

    @Override public String gearState() {
        if (ctl.get("m_esp") == 1) return "OPERATED by " + by + " - all stop within " + zone() + " m";
        if (!powered()) return FailSafe.safe(world, FailSafe.ESP_WIRING) ? "NO 12 V - treated as OPERATED (fail-safe)" : "NO 12 V - alarm only";
        return "normal · zone " + zone() + " m";
    }

    @Override public int wiredLamp() { return operated() ? 3 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("operated", operated()); m.put("zone", zone()); }

    @Override
    protected void onPowerChange(boolean on) {
        if (!on && ctl.get("m_esp") == 0) {
            boolean safe = FailSafe.safe(world, FailSafe.ESP_WIRING);
            AlarmManager.get(world).raise(world, "esp:" + pos.toLong(), AlarmManager.Cat.WIRED, safe ? AlarmManager.Sev.CRITICAL : AlarmManager.Sev.MAJOR,
                    "Emergency plunger at " + pos.getX() + "," + pos.getZ() + " lost 12 V" + (safe ? " - treated as OPERATED" : ""), pos, true);
        } else if (on && ctl.get("m_esp") == 0) AlarmManager.get(world).clear("esp:" + pos.toLong());
    }

    @Override
    protected void serverTick() {
        if (age % 20 != 7) return;
        SafetyData d = SafetyData.get(world);
        SafetyData.Incident in = d.openIncident(pos.toLong());
        if (in != null) {
            boolean sigs = true;
            double r2 = (double) zone() * zone();
            for (TileSignalMast m : SignalRegistry.masts(world.provider.getDimension())) {
                Vec3i g = m.governedRail();
                if (g != null && Geo.d2(g.x + 0.5, g.z + 0.5, pos.getX() + 0.5, pos.getZ() + 0.5) <= r2 && !m.aspect().isStop()) { sigs = false; break; }
            }
            boolean stand = true;
            for (TrainNode t : TrainTracker.latest(world)) if (t.moving() && Geo.d2(t.x, t.z, pos.getX() + 0.5, pos.getZ() + 0.5) <= r2) { stand = false; break; }
            boolean protectedSite = false;
            for (TilePossessionSwitch p : SafetyTicker.possessions(world))
                if (p.granted() && p.getPos().distanceSq(pos) <= Math.pow(zone() + p.zone(), 2)) { protectedSite = true; break; }
            int s = (in.steps & 4) | (sigs ? 1 : 0) | (stand ? 2 : 0) | (protectedSite ? 8 : 0);
            if (s != in.steps) { in.steps = s; d.markDirty(); }
            // keep stopping radio trains that enter the zone while it stands
            for (SafetyData.TrainRec r : d.trains.values())
                if (!r.gone && r.radio == 1 && Geo.d2(r.x, r.z, pos.getX() + 0.5, pos.getZ() + 0.5) <= r2 && stopped.add(r.id)) RadioNet.stop(world, r, "emergency stop plunger operated");
        }
        boolean op = operated();
        put("op", op);
        put("pwr", powered());
        put("st1", in != null && (in.steps & 1) != 0);
        put("st2", in != null && (in.steps & 2) != 0);
        put("st3", in != null && (in.steps & 4) != 0);
        put("st4", in != null && (in.steps & 8) != 0);
        text("zone", "ZONE " + zone() + "M");
        if (ctl.get("m_esp") == 1 && powered() && age % 40 == 7) world.playSound(null, pos, com.dogpound.railmap.ModSounds.KLAXON, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        switch (id) {
            case "test": return lampTest();
            case "s_zone":
                if (ctl.get("m_esp") == 1) return "§cCan't change the zone while the plunger is operated";
                ctl.set("s_zone", (ctl.get("s_zone") + 1) % 3);
                return "§eProtection zone " + zone() + " m";
            case "k_reset": return null;
            case "m_esp": return ctl.get("m_esp") == 0 ? operate(who) : reset(who);
            default: return "§7" + gearState();
        }
    }

    private String operate(EntityPlayer who) {
        SafetyData d = SafetyData.get(world);
        ctl.set("m_esp", 1);
        by = who(who);
        SafetyData.Incident in = new SafetyData.Incident();
        in.id = d.nextIncident++;
        in.pos = pos.toLong();
        in.opened = world.getTotalWorldTime();
        in.by = by;
        in.where = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        d.incidents.add(0, in);
        while (d.incidents.size() > 30) d.incidents.remove(d.incidents.size() - 1);
        SafetyData.Comm c = d.log(world, "ESP " + pos.getX() + "," + pos.getZ(), "DISPATCH", "EMERGENCY STOP PLUNGER OPERATED by " + by, 1, 0);
        c.pos = pos.toLong();
        AlarmManager.get(world).raise(world, "esp:" + pos.toLong(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.CRITICAL,
                "EMERGENCY STOP plunger operated at " + in.where + " by " + by + " - all stop within " + zone() + " m", pos, true);
        Recorder.event(world, "INCIDENT #" + in.id + " opened: emergency plunger at " + in.where + " by " + by);
        d.history(world, pos, wiredName(), "emergency plunger", "OPERATED by " + by + " (incident #" + in.id + ")");
        stopped.clear();
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_ESTOP, SoundCategory.BLOCKS, 0.5f, 1.0f);
        return "§c§lEMERGENCY STOP - signals to danger, trains stopped, signaller called (incident #" + in.id + ")";
    }

    private String reset(EntityPlayer who) {
        if (ctl.get("k_reset") != 1) return "§cThe plunger is latched - turn the reset key to RESET first";
        SafetyData d = SafetyData.get(world);
        SafetyData.Incident in = d.openIncident(pos.toLong());
        if (in != null) {
            if ((in.steps & 2) == 0) return "§cReset refused - a train in the zone is still moving";
            if ((in.steps & 4) == 0) return "§cReset refused - the signaller hasn't answered the emergency call yet (Radio Dispatch Desk: ANSWER)";
            in.closed = world.getTotalWorldTime();
            Recorder.event(world, "INCIDENT #" + in.id + " closed after " + (in.closed - in.opened) / 20 + " s, reset by " + who(who));
        }
        ctl.set("m_esp", 0);
        ctl.set("k_reset", 0);
        for (String id : stopped) { SafetyData.TrainRec r = d.trains.get(id); if (r != null) RadioNet.release(world, r); }
        stopped.clear();
        AlarmManager.get(world).clear("esp:" + pos.toLong());
        d.history(world, pos, wiredName(), "emergency plunger", "reset by " + who(who));
        d.markDirty();
        return "§aPlunger reset - the line is given back to the signals";
    }

    @Override protected void save(NBTTagCompound t) { t.setString("by", by); t.setString("stopped", String.join(",", stopped)); }
    @Override protected void load(NBTTagCompound t) { by = t.getString("by"); stopped.clear(); for (String s : t.getString("stopped").split(",")) if (!s.isEmpty()) stopped.add(s); }
}
