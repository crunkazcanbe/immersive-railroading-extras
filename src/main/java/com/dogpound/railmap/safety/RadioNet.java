package com.dogpound.railmap.safety;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.ConsoleControl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.World;

import java.util.*;

/**
 * Radio-based train control (her ChatGPT list #21) and the radio side of dispatch (#26), GSM-R style:
 * <ul>
 *   <li>every train has a radio id (with its headcode) and registers on the strongest healthy Radio Mast in range.
 *   Signal strength is worked out for real: transmitter power, free-space path loss at 900 MHz over the distance,
 *   30 dB lost when terrain blocks the line between mast and cab, and the cell edge (the mast's planned radius);</li>
 *   <li>while linked, the train sends a position report every 5 s (berth, speed) - the describer and tracking use them;</li>
 *   <li>inside radio territory with no link (mast unpowered, burnt, cut by the lab, terrain) the link is LOST. After
 *   a 10 s grace the configured fallback runs (fail-safe RADIO_LOSS): brake the train to a stand (driverless trains
 *   are held; a driver's throttle is cut and the brakes go on), or hold the signal ahead at STOP, or carry on by the
 *   lineside signals with the driver warned. Driving out of territory is a clean sign-off, not a failure;</li>
 *   <li>end-of-train: the rear vehicle's telemetry also has to reach a mast. Inside territory, a rear end that hasn't
 *   been heard for 30 s makes the train's integrity UNCONFIRMED (#20);</li>
 *   <li>dispatcher instructions (the Radio Dispatch Desk) go to one train, a line or everyone; drivers acknowledge
 *   with the [ACKNOWLEDGE] link in chat, driverless trains acknowledge automatically and obey stop / proceed.</li>
 * </ul>
 */
public final class RadioNet {
    private RadioNet() {}

    public static final String[] MSGS = {"CONTACT THE SIGNALLER", "STOP AT THE NEXT SIGNAL", "PROCEED - AUTHORITY RESUMED", "REDUCE SPEED - PROCEED AT CAUTION",
            "PASS SIGNAL AT DANGER - AUTHORISED", "EMERGENCY STOP", "INTEGRITY CHECK - CONFIRM REAR OF TRAIN", "LINE BLOCKED - DO NOT PROCEED"};
    public static final int CONTACT = 0, STOP_NEXT = 1, PROCEED = 2, CAUTION = 3, PASS_DANGER = 4, EMERGENCY = 5, INTEGRITY = 6, LINE_BLOCKED = 7;
    public static final double THRESHOLD = -95;

    /** received level at a point from one mast (dBm) */
    public static double rssi(World w, TileRadioMast m, double x, double y, double z) {
        BlockPos p = m.getPos();
        double dx = x - p.getX() - 0.5, dy = y - p.getY(), dz = z - p.getZ() - 0.5;
        double d = Math.max(4, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double fspl = 20 * Math.log10(d / 1000.0) + 20 * Math.log10(900) + 32.44;
        double r = m.txDbm() - fspl;
        RayTraceResult hit = w.rayTraceBlocks(new Vec3d(p.getX() + 0.5, p.getY() + 1.9, p.getZ() + 0.5), new Vec3d(x, y + 3, z), false, true, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && !hit.getBlockPos().equals(p) && !hit.getBlockPos().equals(p.up())) r -= 30;
        if (d > m.radius()) r -= (d - m.radius()) * 0.5;
        return r;
    }

    private static Object[] best(World w, List<TileRadioMast> masts, double x, double y, double z) {
        TileRadioMast best = null;
        double br = -999;
        boolean territory = false;
        for (TileRadioMast m : masts) {
            double dx = x - m.getPos().getX(), dz = z - m.getPos().getZ();
            if (dx * dx + dz * dz > (double) m.radius() * m.radius()) continue;
            territory = true;
            if (!m.healthy()) continue;
            double r = rssi(w, m, x, y, z);
            if (r > br) { br = r; best = m; }
        }
        return new Object[]{br >= THRESHOLD ? best : null, br, territory};
    }

    /** once a second */
    public static void tick(World w) {
        SafetyData d = SafetyData.get(w);
        List<TileRadioMast> masts = SafetyTicker.radioMasts(w);
        long now = w.getTotalWorldTime();
        int dim = w.provider.getDimension();
        for (TileRadioMast m : masts) m.linked = 0;
        for (SafetyData.TrainRec r : d.trains.values()) {
            if (r.gone) { release(w, r); continue; }
            if (masts.isEmpty()) {
                if (r.radio != 0) { r.radio = 0; release(w, r); AlarmManager.get(w).clear("radio:" + r.id); }
                if (r.integrity == 1) ConsistWatch.restore(w, d, r, "no radio territory - end-of-train monitoring not fitted");
                continue;
            }
            Object[] b = best(w, masts, r.x, r.y, r.z);
            TileRadioMast m = (TileRadioMast) b[0];
            boolean territory = (Boolean) b[2];
            int was = r.radio;
            if (m != null) {
                m.linked++;
                r.radio = 1;
                r.rssi = (int) Math.round((Double) b[1]);
                r.mast = m.cell();
                if (now - r.lastReport >= 100) r.lastReport = now;           // position report
                if (was == 0) d.log(w, r.headcode + " (RID " + r.radioId + ")", "RADIO", "registered on cell " + r.mast + " at " + r.rssi + " dBm", 2, 3);
                if (was == 2) {
                    d.log(w, r.headcode, "RADIO", "radio link RESTORED on cell " + r.mast + " after " + (now - r.radioLostAt) / 20 + " s", 2, 3);
                    AlarmManager.get(w).clear("radio:" + r.id);
                    release(w, r);
                    ConsistWatch.tellRiders(w, r, "§a[RADIO] Link restored on cell " + r.mast + " - you may release the brakes and proceed on signals");
                }
            } else if (territory) {
                r.rssi = (int) Math.round((Double) b[1]);
                if (was != 2) {
                    r.radio = 2;
                    r.radioLostAt = now;
                    d.log(w, r.headcode, "RADIO", "radio link LOST near " + Math.round(r.x) + "," + Math.round(r.z) + " (" + (r.rssi < -500 ? "no mast in service" : r.rssi + " dBm") + ")", 2, 3);
                    AlarmManager.get(w).raise(w, "radio:" + r.id, AlarmManager.Cat.SIGNAL, AlarmManager.Sev.MAJOR,
                            "Radio link lost: " + r.headcode + " (RID " + r.radioId + ") near " + Math.round(r.x) + "," + Math.round(r.z), new BlockPos(r.x, r.y, r.z), true);
                    ConsistWatch.tellRiders(w, r, "§c[RADIO] NO RADIO CONTACT with the signaller - " + FailSafe.RADIO_LOSS.options[FailSafe.mode(w, FailSafe.RADIO_LOSS)]);
                }
                if (now - r.radioLostAt >= 200 && FailSafe.mode(w, FailSafe.RADIO_LOSS) == 0) stop(w, r, "radio link lost");
            } else {
                if (was != 0) {
                    d.log(w, r.headcode, "RADIO", "signed off - left radio territory", 2, 3);
                    AlarmManager.get(w).clear("radio:" + r.id);
                    release(w, r);
                }
                r.radio = 0;
            }
            SigHooks.radio(w, r);                                                       // batch-3 #17 coverage / dead-zone map
            // end-of-train telemetry
            if (r.integrity != 2) {
                boolean rearOk = r.cars <= 1 || !territory || best(w, masts, r.rx, r.y, r.rz)[0] != null;
                if (rearOk) { r.integritySince = now; if (r.integrity == 1) ConsistWatch.restore(w, d, r, "end-of-train telemetry heard again"); }
                else if (r.integrity == 0 && now - r.integritySince > 600) {
                    r.integrity = 1;
                    r.integrityWhy = "rear end not confirmed - no end-of-train radio contact";
                    d.log(w, "EOT", r.headcode, r.integrityWhy, 2, 3);
                    AlarmManager.get(w).raise(w, "integrity:" + r.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MINOR, r.headcode + ": " + r.integrityWhy, new BlockPos(r.x, r.y, r.z), true);
                }
            }
            if (r.braked) brakeNow(w, r);
        }
        // driverless trains acknowledge their instructions a few seconds later
        for (SafetyData.Comm c : d.comms) {
            if (c.ack != 0 || c.kind != 0 || now - c.tick < 60) continue;
            SafetyData.TrainRec r = d.trains.get(c.train);
            if (r != null && r.driverless) acknowledge(w, c, "auto (driverless)", false);
        }
        // emergency calls nobody answers
        for (SafetyData.Comm c : d.comms) {
            if (c.kind == 1 && c.ack == 0 && now - c.tick > 600 && now - c.tick <= 620)
                AlarmManager.get(w).raise(w, "call:" + c.id, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "Emergency call unanswered for 30 s: " + c.from + " - " + c.text, BlockPos.fromLong(c.pos), true);
        }
    }

    /** bring a train to a stand: driverless = held, driven = throttle off + brakes on (kept on until released) */
    public static void stop(World w, SafetyData.TrainRec r, String why) {
        if (r.lead == null) return;
        int dim = w.provider.getDimension();
        if (r.driverless) {
            if (!ConsoleControl.heldByName(dim, r.lead)) { ConsoleControl.hold(dim, r.lead, true); r.held = true; }
        } else if (!r.braked) {
            r.braked = true;
            ConsistWatch.tellRiders(w, r, "§c[RADIO] " + why.toUpperCase(Locale.ROOT) + " - brakes applied by train control");
        }
        brakeNow(w, r);
    }

    private static void brakeNow(World w, SafetyData.TrainRec r) {
        if (r.driverless) return;
        Locomotive l = ConsistWatch.loco(w, r);
        if (l == null) return;
        l.setThrottle(0);
        l.setTrainBrake(1);
    }

    /** undo exactly what train control did to this train */
    public static void release(World w, SafetyData.TrainRec r) {
        if (r.held && r.lead != null) ConsoleControl.hold(w.provider.getDimension(), r.lead, false);
        r.held = false;
        r.braked = false;
    }

    /** a dispatcher instruction to one train; returns what the desk shows */
    public static String send(World w, String from, SafetyData.TrainRec r, int msg) {
        SafetyData d = SafetyData.get(w);
        if (r.radio != 1) {
            SafetyData.Comm c = d.log(w, from, r.headcode, MSGS[msg], 0, 2);
            c.train = r.id; c.msg = msg;
            return "NO CONTACT " + r.headcode;
        }
        SafetyData.Comm c = d.log(w, from, r.headcode + " (RID " + r.radioId + ")", MSGS[msg], 0, 0);
        c.train = r.id;
        c.msg = msg;
        if (msg == EMERGENCY) stop(w, r, "emergency stop ordered by " + from);
        for (EntityPlayer p : ConsistWatch.riders(w, r)) {
            TextComponentString t = new TextComponentString("§e[RADIO] " + from + " to " + r.headcode + ": §f" + MSGS[msg] + " ");
            TextComponentString ack = new TextComponentString("§a[ACKNOWLEDGE]");
            ack.setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/irextras safety radio ack " + c.id)));
            t.appendSibling(ack);
            p.sendMessage(t);
            w.playSound(null, p.getPosition(), com.dogpound.railmap.ModSounds.BEEP_OK, net.minecraft.util.SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        return "SENT " + r.headcode;
    }

    /** the driver (or a driverless train) acknowledged: log it and carry the instruction out */
    public static String acknowledge(World w, SafetyData.Comm c, String by, boolean driver) {
        SafetyData d = SafetyData.get(w);
        if (c.ack != 0) return "§7Already acknowledged";
        c.ack = 1;
        c.ackBy = by;
        c.ackTick = w.getTotalWorldTime();
        d.markDirty();
        SafetyData.TrainRec r = d.trains.get(c.train);
        String did = "";
        if (r != null) {
            int dim = w.provider.getDimension();
            switch (c.msg) {
                case STOP_NEXT: case LINE_BLOCKED: case EMERGENCY:
                    if (r.driverless && r.lead != null && !ConsoleControl.heldByName(dim, r.lead)) { ConsoleControl.hold(dim, r.lead, true); r.held = true; did = " - train held"; }
                    break;
                case PROCEED:
                    release(w, r);
                    did = " - holds released";
                    break;
                case INTEGRITY:
                    if (driver || r.integrity == 1) { ConsistWatch.restore(w, d, r, "rear of train confirmed by " + by); did = " - integrity confirmed"; }
                    break;
                default: break;
            }
        }
        com.dogpound.railmap.alarm.Recorder.event(w, "RADIO ack #" + c.id + " " + c.to + " by " + by + did);
        return "§aAcknowledged: " + c.text + did;
    }
}
