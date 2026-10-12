package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.auto.RailwayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Radio Dispatch Desk (her ChatGPT list #26): the signaller's GSM-R console. Step through the trains on the radio
 * (headcode, radio id, signal strength) and the standard instructions, then CALL one train, BROADCAST to every train
 * on a line, or call ALL trains. Drivers acknowledge in chat; driverless trains acknowledge and obey. The amber lamp
 * shows instructions still waiting for an acknowledgement, the red one a call that couldn't reach its train. Incoming
 * emergency calls (lineside telephones, drivers) flash and ring until ANSWERed. The red mushroom is the railway
 * emergency all-stop: every train on the radio is stopped until it is pulled out again (which sends PROCEED to all).
 * CONFIRM INTEGRITY records that the selected train has been examined and is complete. Every call, answer and
 * acknowledgement goes in the communications log (the four lines on the screen, the console's SAFETY page and the
 * event recorder).
 */
public class TileRadioDesk extends SafetyTile {
    private String sel = "";
    private int msg, line;
    private long txUntil;
    private final Set<String> stopped = new HashSet<>();

    @Override public String wiredName() { return "Radio dispatch desk"; }

    @Override public String gearState() {
        SafetyData d = SafetyData.get(world);
        int[] n = counts(d);
        return !powered() ? "dead - no 12 V" : n[0] + " instruction(s) unacknowledged · " + n[1] + " call(s) waiting" + (ctl.get("m_emerg") == 1 ? " · EMERGENCY ALL-STOP" : "");
    }

    @Override public int wiredLamp() { return !powered() ? 3 : counts(SafetyData.get(world))[1] > 0 || ctl.get("m_emerg") == 1 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { int[] n = counts(SafetyData.get(world)); m.put("unacked", n[0]); m.put("calls", n[1]); m.put("selected", sel); }

    private static int[] counts(SafetyData d) {
        int un = 0, calls = 0;
        for (SafetyData.Comm c : d.comms) {
            if (c.ack == 0 && c.kind == 0) un++;
            if (c.ack == 0 && c.kind == 1) calls++;
        }
        return new int[]{un, calls};
    }

    private List<SafetyData.TrainRec> trains() {
        List<SafetyData.TrainRec> l = new ArrayList<>();
        for (SafetyData.TrainRec r : SafetyData.get(world).trains.values()) if (!r.gone) l.add(r);
        l.sort(Comparator.comparing(r -> r.headcode));
        return l;
    }

    private List<String> lines() {
        List<String> l = new ArrayList<>(RailwayData.get(world).lines().keySet());
        Collections.sort(l);
        l.add(0, "UNASSIGNED");
        return l;
    }

    @Override
    protected void serverTick() {
        if (age % 10 != 0) return;
        SafetyData d = SafetyData.get(world);
        boolean on = powered();
        List<SafetyData.TrainRec> ts = trains();
        SafetyData.TrainRec r = d.trains.get(sel);
        if ((r == null || r.gone) && !ts.isEmpty()) { r = ts.get(0); sel = r.id; }
        text("train", r == null ? "NO TRAINS" : r.headcode + " RID" + r.radioId + " " + (r.radio == 1 ? r.rssi + "DBM" : r.radio == 2 ? "LOST" : "NO RADIO"));
        text("msg", RadioNet.MSGS[msg]);
        List<String> ls = lines();
        text("line", ls.get(Math.min(line, ls.size() - 1)));
        int[] n = counts(d);
        put("npend", n[0]);
        put("ncall", n[1]);
        put("pending", n[0] > 0);
        put("incoming", n[1] > 0);
        boolean failed = false;
        for (SafetyData.Comm c : d.comms) if (world.getTotalWorldTime() - c.tick < 600 && c.ack == 2) { failed = true; break; }
        put("fail", failed);
        put("tx", world.getTotalWorldTime() < txUntil);
        put("pwr", on);
        put("emerg", ctl.get("m_emerg") == 1);
        for (int i = 0; i < 4; i++) {
            SafetyData.Comm c = i < d.comms.size() ? d.comms.get(i) : null;
            text("log" + (i + 1), c == null ? "" : com.dogpound.railmap.alarm.Recorder.clock(world, c.tick) + " " + c.from + ">" + c.to + " " + c.text
                    + (c.ack == 0 ? " ?" : c.ack == 1 ? " OK" : c.ack == 2 ? " FAIL" : ""));
        }
        if (on && n[1] > 0 && age % 40 == 0) world.playSound(null, pos, com.dogpound.railmap.ModSounds.PHONE_RING, SoundCategory.BLOCKS, 0.9f, 1.0f);
        if (ctl.get("m_emerg") == 1 && on)                                    // the all-stop stays in force for trains that join
            for (SafetyData.TrainRec t : ts) if (t.radio == 1 && (stopped.add(t.id) || !t.braked && !t.held)) RadioNet.stop(world, t, "railway emergency all-stop");
    }

    @Override protected boolean keepTextUnpowered() { return false; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        if (!powered()) return "§cThe desk is dead - no 12 V";
        SafetyData d = SafetyData.get(world);
        List<SafetyData.TrainRec> ts = trains();
        String me = "DISPATCH" + (who == null ? "" : " (" + who.getName() + ")");
        switch (id) {
            case "tprev": case "tnext": {
                if (ts.isEmpty()) return "§7No trains";
                int i = 0;
                for (int k = 0; k < ts.size(); k++) if (ts.get(k).id.equals(sel)) i = k;
                i = Math.floorMod(i + (id.equals("tnext") ? 1 : -1), ts.size());
                sel = ts.get(i).id;
                return "§e" + ts.get(i).label();
            }
            case "mprev": msg = Math.floorMod(msg - 1, RadioNet.MSGS.length); return "§e" + RadioNet.MSGS[msg];
            case "mnext": msg = (msg + 1) % RadioNet.MSGS.length; return "§e" + RadioNet.MSGS[msg];
            case "lnext": line = (line + 1) % lines().size(); return "§eLine: " + lines().get(line);
            case "call": {
                SafetyData.TrainRec r = d.trains.get(sel);
                if (r == null || r.gone) return "§7No train selected";
                txUntil = world.getTotalWorldTime() + 30;
                return "§e" + RadioNet.send(world, me, r, msg);
            }
            case "bcast": {
                String ln = lines().get(Math.min(line, lines().size() - 1));
                int sent = 0, fail = 0;
                for (SafetyData.TrainRec r : ts) {
                    if (!(ln.equals("UNASSIGNED") ? r.line.isEmpty() : ln.equals(r.line))) continue;
                    if (RadioNet.send(world, me + " to line " + ln, r, msg).startsWith("SENT")) sent++; else fail++;
                }
                txUntil = world.getTotalWorldTime() + 30;
                return "§eBroadcast to line " + ln + ": " + sent + " reached" + (fail > 0 ? ", §c" + fail + " no contact" : "");
            }
            case "all": {
                int sent = 0;
                for (SafetyData.TrainRec r : ts) if (r.radio == 1 && RadioNet.send(world, me + " ALL CALL", r, msg).startsWith("SENT")) sent++;
                txUntil = world.getTotalWorldTime() + 30;
                return "§eAll-call: " + sent + " train(s) reached";
            }
            case "answer": {
                SafetyData.Comm oldest = null;
                for (SafetyData.Comm c : d.comms) if (c.kind == 1 && c.ack == 0) oldest = c;
                if (oldest == null) return "§7No call waiting";
                oldest.ack = 1; oldest.ackBy = me; oldest.ackTick = world.getTotalWorldTime();
                AlarmManager.get(world).clear("call:" + oldest.id);
                d.log(world, me, oldest.from, "emergency call answered", 2, 3);
                for (SafetyData.Incident in : d.incidents) if (in.closed == 0 && in.pos == oldest.pos) in.steps |= 4;
                return "§aAnswered: " + oldest.from + " - " + oldest.text;
            }
            case "confirm": {
                SafetyData.TrainRec r = d.trains.get(sel);
                if (r == null) return "§7No train selected";
                if (r.integrity == 0) return "§7" + r.headcode + " integrity is already confirmed";
                ConsistWatch.restore(world, d, r, "confirmed by " + me + " after examination");
                return "§aIntegrity of " + r.headcode + " confirmed";
            }
            case "m_emerg": {
                boolean on = ctl.get("m_emerg") == 0;
                ctl.set("m_emerg", on ? 1 : 0);
                if (on) {
                    stopped.clear();
                    d.log(world, me, "ALL TRAINS", "RAILWAY EMERGENCY ALL-STOP", 0, 3);
                    AlarmManager.get(world).raise(world, "radio:allstop", AlarmManager.Cat.ALLSTOP, AlarmManager.Sev.CRITICAL, "Radio emergency all-stop by " + me, pos, true);
                    for (SafetyData.TrainRec r : ts) if (r.radio == 1) { stopped.add(r.id); RadioNet.stop(world, r, "railway emergency all-stop"); }
                    return "§cEMERGENCY ALL-STOP sent to " + stopped.size() + " train(s)";
                }
                for (SafetyData.TrainRec r : ts) if (stopped.contains(r.id)) { RadioNet.release(world, r); RadioNet.send(world, me, r, RadioNet.PROCEED); }
                stopped.clear();
                AlarmManager.get(world).clear("radio:allstop");
                return "§aAll-stop cancelled - PROCEED sent";
            }
            default: return "";
        }
    }

    @Override
    protected void save(NBTTagCompound t) {
        t.setString("sel", sel); t.setInteger("msg", msg); t.setInteger("line", line);
        t.setString("stopped", String.join(",", stopped));
    }

    @Override
    protected void load(NBTTagCompound t) {
        sel = t.getString("sel"); msg = t.getInteger("msg") % RadioNet.MSGS.length; line = t.getInteger("line");
        stopped.clear();
        for (String s : t.getString("stopped").split(",")) if (!s.isEmpty()) stopped.add(s);
    }
}
