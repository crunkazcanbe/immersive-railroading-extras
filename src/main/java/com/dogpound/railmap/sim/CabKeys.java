package com.dogpound.railmap.sim;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.DriverAids;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.Locale;

/**
 * The driver's display soft keys (her list item 109): what each key on client.GuiDmi does to the train the player
 * rides. The unit commanded is the consist's MU lead (or the unit ridden when it runs alone).
 */
public final class CabKeys {
    private CabKeys() {}

    /** the locomotive this player drives: the MU lead of the train they ride, else a loco they ride / in their train */
    public static Locomotive driven(World mc, EntityPlayer p) {
        Locomotive any = null;
        for (Locomotive l : cam72cam.mod.world.World.get(mc).getEntities(Locomotive.class)) {
            if (l.isDead()) continue;
            boolean in = false;
            for (cam72cam.mod.entity.Entity e : l.getPassengers()) if (e.internal == p) in = true;
            if (!in) {
                for (EntityCoupleableRollingStock s : l.getTrain(false))
                    for (cam72cam.mod.entity.Entity e : s.getPassengers()) if (e.internal == p) in = true;
                if (!in) continue;
                if (any == null) any = l;
                continue;
            }
            any = l;
            break;
        }
        if (any == null) return null;
        MultiUnit.Group g = MultiUnit.group(any.getUUID());
        if (g != null && !g.lead.equals(any.getUUID()))
            for (Locomotive l : cam72cam.mod.world.World.get(mc).getEntities(Locomotive.class)) if (l.getUUID().equals(g.lead)) return l;
        return any;
    }

    public static String press(World mc, EntityPlayer p, String key) {
        if (key.startsWith("d:")) return CabControls.press(mc, p, key);      // the cab desk (client.render.CabDeskRenderer)
        Locomotive l = driven(mc, p);
        if (l == null) return "§7You are not in a train";
        String k = key.toLowerCase(Locale.ROOT);
        switch (k) {
            case "ack": return DriverAids.ack(mc, p);
            case "start": return MultiUnit.group(l.getUUID()) != null ? MultiUnit.engines(mc, l, true) : startOne(l, true);
            case "stop": return MultiUnit.group(l.getUUID()) != null ? MultiUnit.engines(mc, l, false) : startOne(l, false);
            case "dp": return MultiUnit.dpMode(mc, l);
            case "dp+": return MultiUnit.dpNotch(mc, l, 1);
            case "dp-": return MultiUnit.dpNotch(mc, l, -1);
            case "sand": { SimData.Car c = SimData.get(mc).of(l.getUUID()); c.sandHold = 3; return "§7Sanders on for 3 s"; }
            case "fltreset": {
                int n = 0;
                MultiUnit.Group g = MultiUnit.group(l.getUUID());
                for (LocomotiveDiesel d : cam72cam.mod.world.World.get(mc).getEntities(LocomotiveDiesel.class)) {
                    if (g == null ? !d.getUUID().equals(l.getUUID()) : g.units.stream().noneMatch(u -> u.id.equals(d.getUUID()))) continue;
                    EngineSim.reset(mc, d);
                    n++;
                }
                Recorder.event(mc, EngineSim.label(l) + ": fault reset from the cab by " + p.getName());
                return n == 0 ? "§7No engine units to reset" : "§aFault reset sent to " + n + " unit" + (n > 1 ? "s" : "");
            }
            default:
                if (k.startsWith("cut")) {
                    try { return MultiUnit.cut(mc, l, Integer.parseInt(k.substring(3))); } catch (NumberFormatException e) { return ""; }
                }
                if (k.startsWith("aux:")) {
                    String r = AuxSim.toggle(mc, l, k.substring(4));
                    // the MU train lines carry the headlight / compressor / HVAC commands to every unit
                    MultiUnit.Group g = MultiUnit.group(l.getUUID());
                    String w = k.substring(4);
                    if (g != null && (w.equals("comp") || w.equals("reset"))) {
                        SimData data = SimData.get(mc);
                        SimData.Car lc = data.of(l.getUUID());
                        for (MultiUnit.Unit u : g.units) if (u.role == MultiUnit.MU || u.role == MultiUnit.DP) {
                            SimData.Car c = data.of(u.id);
                            c.compressor = lc.compressor;
                            if (w.equals("reset")) c.convTrip = false;
                        }
                    }
                    return r;
                }
                return "";
        }
    }

    private static String startOne(Locomotive l, boolean on) {
        if (!(l instanceof LocomotiveDiesel d)) return "§7No engine";
        d.setTurnedOn(on);
        return on ? "§aEngine start" : "§eEngine stop";
    }
}
