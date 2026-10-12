package com.dogpound.railmap.ops6;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.InfraTile;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.safety.Competency;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.*;

/**
 * Driver Training Simulator (batch 6 E #25): a cab desk with the big route screen (three blocks wide). The instructor
 * side: DESK key, SCENARIO selector (five: station stop, signals + AWS, low adhesion, heavy freight, night run),
 * START / ABORT, the trainee's live speed, brake and score, AWS / DSD / EMERGENCY / PASS / FAIL lamps. SCREEN seats the
 * trainee at the desk: the simulator window (GuiDriverSim) is the windscreen + DMI and its keys are the master
 * controller, brake, AWS acknowledge and DSD. The sim runs while the trainee is at the desk (it pauses if they leave).
 * A finished run goes on the trainee's driver competency record (Competency: a SIM line, and sim hours toward the
 * traction hours at Ops6Config.simHoursCredit up to simHoursCap), the event recorder and the desk's results board.
 * 12 V + 120 / 240 V AC (the sim computers).
 */
public class TileDriverSim extends InfraTile implements PacketOps6.Screen {
    SimCore core;
    UUID trainee;
    String traineeName = "";
    long seenAt, startAt;
    final List<String> results = new ArrayList<>();

    public TileDriverSim() { super(new Supply(Elec.Level.AC120, Elec.Level.AC240)); }

    @Override public String wiredName() { return "Driver training simulator " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !powered() ? "dark - no 12 V" : !live() ? "no supply: " + mains.what : core == null ? "idle · " + SimCore.NAMES[scn()] : (core.finished ? "finished: " + core.result : "RUNNING " + core.name + " · " + traineeName + String.format(Locale.ROOT, " · %.0f km/h · score %.0f", core.v * 3.6, core.score)); }
    @Override public int wiredLamp() { return !live() ? 3 : 1; }

    int scn() { return Math.floorMod(ctl.get("s_scn"), 5); }
    boolean desk() { return ctl.get("k_desk") == 1 && live(); }
    boolean running() { return core != null && !core.finished; }

    void seen(EntityPlayer p) { if (trainee != null && trainee.equals(p.getUniqueID())) seenAt = world.getTotalWorldTime(); }

    @Override
    protected void serverTick() {
        if (age % 20 == 13) mainsTick(running() ? 1800 : desk() ? 400 : 20);
        if (running()) {
            if (!desk()) { abort("POWER / DESK KEY LOST"); return; }
            if (world.getTotalWorldTime() - seenAt < 40) {
                boolean aws = core.awsHorn, vig = core.vigWarn, eb = core.ebLatched;
                core.step(0.05);
                if (core.awsHorn && !aws) PackSounds.play(world, pos, "cab.aws_horn", 0.8f, 1f);
                if (core.vigWarn && !vig) PackSounds.play(world, pos, "buzzer", 0.6f, 1.1f);
                if (core.ebLatched && !eb) PackSounds.play(world, pos, "cab.brake_emergency", 0.9f, 1f);
                if (core.finished) done();
            }
        }
        if (age % 5 == 0) display();
    }

    void display() {
        boolean on = powered(), blink = (world.getTotalWorldTime() / 10) % 2 == 0;
        SimCore c = core;
        put("pwr", on); put("sup", mains.ok); put("deskl", on && desk());
        put("run", on && running() && (world.getTotalWorldTime() - seenAt < 40 || blink));
        put("aws", on && c != null && c.awsHorn && blink); put("dsd", on && c != null && c.vigWarn && blink); put("eb", on && c != null && c.ebLatched);
        put("slip", on && c != null && (c.slip || c.slide)); put("pass", on && c != null && c.finished && c.passed); put("fail", on && c != null && c.finished && !c.passed);
        put("s_pwr", c == null ? 0 : c.notch);                             // the master controller / brake handles follow the sim
        put("s_brk", c == null ? 0 : c.brake);
        put("g_spd", c == null ? 0 : (int) Math.min(100, c.v * 3.6 / 1.6)); put("g_bc", c == null ? 0 : (int) Math.min(100, c.bc * 77));
        text("d_spd", c == null ? "0" : String.format(Locale.ROOT, "%.0f", c.v * 3.6));
        text("d_scn", (scn() + 1) + " " + SimCore.NAMES[scn()]);
        text("d_score", c == null ? "-" : String.valueOf(Math.round(c.score)));
        text("d_who", c == null ? "NO TRAINEE" : traineeName.toUpperCase(Locale.ROOT));
        text("d_state", c == null ? (desk() ? "READY - PRESS START" : "DESK OFF") : c.finished ? (c.passed ? "PASS" : "FAIL") : world.getTotalWorldTime() - seenAt >= 40 ? "PAUSED - SIT AT THE DESK" : String.format(Locale.ROOT, "%.0f M TO STOP", Math.max(0, c.stopAt - c.pos)));
    }

    String start(EntityPlayer p) {
        if (!powered()) return "§cNo 12 V";
        if (!desk()) return "§cTurn the DESK key on (and the supply: " + mains.what + ")";
        if (running()) return "§cA run is in progress - ABORT it first";
        core = new SimCore(scn());
        trainee = p == null ? null : p.getUniqueID();
        traineeName = p == null ? "console" : p.getName();
        seenAt = world.getTotalWorldTime();
        startAt = world.getTotalWorldTime();
        Recorder.event(world, "Training simulator: " + traineeName + " started scenario " + (scn() + 1) + " " + core.name);
        PackSounds.play(world, pos, "ui.beep_ok", 0.7f, 1f);
        return "§aScenario " + (scn() + 1) + " " + core.name + " - SCREEN to sit at the desk and drive";
    }

    void abort(String why) {
        if (core == null) return;
        core.finished = true;
        core.passed = false;
        core.result = core.name + " ABORTED (" + why + ")";
        Recorder.event(world, "Training simulator: " + traineeName + " " + core.result);
        results.add(0, com.dogpound.railmap.depot.DepotClock.stamp(world) + " " + traineeName + " " + core.result);
        while (results.size() > 12) results.remove(results.size() - 1);
    }

    /** a run finished: the record, the recorder, the results board */
    void done() {
        String line = com.dogpound.railmap.depot.DepotClock.stamp(world) + " " + traineeName + " " + core.result;
        results.add(0, line);
        while (results.size() > 12) results.remove(results.size() - 1);
        Recorder.event(world, "Training simulator: " + traineeName + " " + core.result);
        PackSounds.play(world, pos, core.passed ? "ui.beep_ok" : "ui.beep_error", 0.9f, 1f);
        EntityPlayer p = trainee == null ? null : world.getPlayerEntityByUUID(trainee);
        try {
            Competency c = Competency.get(world);
            Competency.Rec r = p != null ? c.rec(p) : trainee == null ? null : c.recs.get(trainee);
            if (r != null) {
                r.incidents.add(0, world.getTotalWorldTime() + "|SIM|" + core.result + String.format(Locale.ROOT, " SPAD %d AWS %d DSD %d SLIP %d", core.spads, core.awsMiss, core.vigMiss, core.slips));
                while (r.incidents.size() > 30) r.incidents.remove(r.incidents.size() - 1);
                double hrs = core.t * 20 / com.dogpound.railmap.depot.DepotClock.TICKS_PER_HOUR;   // sim seconds -> game hours (a game hour is 1000 ticks)
                String k = "irextras_simH" + SimCore.TRACTION[core.scn];
                NBTTagCompound pd = p == null ? null : p.getEntityData();
                double had = pd == null ? 0 : pd.getDouble(k);
                double credit = Math.max(0, Math.min(Ops6Config.simHoursCap - had, hrs * Ops6Config.simHoursCredit));
                if (credit > 0 && pd != null && core.passed) { r.hours[SimCore.TRACTION[core.scn]] += credit; pd.setDouble(k, had + credit); }
                c.markDirty();
                if (p != null) p.sendMessage(new net.minecraft.util.text.TextComponentString((core.passed ? "§a" : "§c") + "[SIM] " + core.result
                        + (credit > 0 && core.passed ? String.format(Locale.ROOT, " §7- %.2f h %s credited to your driver record", credit, Competency.TRACTION[SimCore.TRACTION[core.scn]]) : "")));
            }
        } catch (LinkageError | RuntimeException ignored) { }
        controlsChanged();
    }

    /** the trainee's controls from the simulator window */
    String input(EntityPlayer p, String c) {
        if (c.equals("start")) return start(p);
        if (c.equals("abort")) { if (!running()) return "§7Nothing running"; abort("ABORTED BY " + p.getName()); return "§eAborted"; }
        if (!running()) return null;
        if (trainee != null && !trainee.equals(p.getUniqueID())) return "§c" + traineeName + " is at the desk";
        if (c.equals("horn")) { PackSounds.play(world, pos, "cab.horn", 0.8f, 1f); return null; }
        String r = core.input(c);
        if (c.startsWith("p") || c.startsWith("b") || c.equals("eb")) PackSounds.play(world, pos, "cab.notch", 0.4f, 1f);
        return r == null ? null : "§e" + r;
    }

    /** the simulator window's data */
    NBTTagCompound simState() {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("live", desk());
        t.setInteger("scn", scn());
        t.setString("who", traineeName);
        if (core == null) return t;
        SimCore c = core;
        t.setBoolean("has", true);
        t.setString("name", c.name);
        t.setDouble("kmh", c.v * 3.6); t.setDouble("pos", c.pos); t.setDouble("t", c.t); t.setDouble("score", c.score);
        t.setDouble("lim", c.limitAt(c.pos)); t.setDouble("stop", c.stopAt - c.pos); t.setDouble("target", c.targetS);
        t.setInteger("notch", c.notch); t.setDouble("notchEff", c.notchEff); t.setInteger("brake", c.brake); t.setDouble("bc", c.bc);
        t.setBoolean("aws", c.awsHorn); t.setBoolean("vig", c.vigWarn); t.setBoolean("eb", c.ebLatched); t.setString("ebWhy", c.ebReason);
        t.setBoolean("slip", c.slip); t.setBoolean("slide", c.slide); t.setBoolean("fin", c.finished); t.setBoolean("pass", c.passed); t.setString("result", c.result);
        t.setDouble("mu", c.mu(c.pos)); t.setDouble("grade", c.grade(c.pos) * 1000); t.setBoolean("night", c.night); t.setBoolean("wet", c.wet);
        t.setBoolean("paused", world.getTotalWorldTime() - seenAt >= 40);
        NBTTagList a = new NBTTagList();
        for (String s : c.ahead()) a.appendTag(new NBTTagString(s));
        t.setTag("ahead", a);
        NBTTagList ev = new NBTTagList();
        for (String s : c.events) ev.appendTag(new NBTTagString(s));
        t.setTag("ev", ev);
        return t;
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        switch (id) {
            case "test": return lampTest();
            case "start": return start(p);
            case "abort": if (!running()) return "§7Nothing running"; abort("ABORTED AT THE DESK"); return "§eRun aborted";
            case "screen":
                if (!(p instanceof EntityPlayerMP mp)) return "§7Sit at the desk to drive";
                if (running() && trainee != null && !trainee.equals(p.getUniqueID())) { PacketOps6.open(p, this, 0); return "§7" + traineeName + " is driving - instructor view"; }
                if (!running() && desk()) { String r = start(p); if (!running()) return r; }
                if (!running()) { PacketOps6.open(p, this, 0); return ""; }
                seenAt = world.getTotalWorldTime();
                Ops6Net.NET.sendTo(PacketOps6.of("opensim").pos(pos), mp);
                return "§7You sit at the simulator desk";
            case "s_scn": if (running()) return "§cNot during a run"; return null;
            case "s_pwr": case "s_brk": case "p_aws": case "p_vig": case "p_horn": case "p_eb": {     // the cab's own handles and buttons
                if (!running()) return "§7No run - START first";
                if (!powered()) return "§cNo 12 V";
                String c = id.equals("s_pwr") ? (core.notch >= 8 ? "p0" : "pu") : id.equals("s_brk") ? (core.brake >= 8 ? "b0" : "bu") : id.substring(2);
                if (c.equals("p0")) { core.notch = 0; c = "pd"; }
                if (c.equals("b0")) { core.brake = 0; c = "bd"; }
                String r = input(p, c);
                display();
                return r != null ? r : id.startsWith("s_") ? "§e" + (id.equals("s_pwr") ? "Power notch " + core.notch : "Brake " + (core.brake >= 8 ? "EMERGENCY" : core.brake == 0 ? "RELEASE" : "step " + core.brake)) : "§7" + id.substring(2).toUpperCase(Locale.ROOT);
            }
            default: return null;
        }
    }

    @Override
    public NBTTagCompound screen(EntityPlayer p, int page) {
        Scr s = new Scr("Driver Training Simulator", live() ? (desk() ? "§aDESK ON" : "§eDESK OFF") : "§cNO POWER", page, "INSTRUCTOR", "RESULTS", "SCENARIOS");
        switch (page) {
            case 1 -> { for (String r : results) s.line((r.contains(" PASS ") ? "§a" : "§c") + r); if (results.isEmpty()) s.line("§8(no runs yet)"); }
            case 2 -> {
                String[] d = {"Passenger DMU 160 t: run 2.5 km and stop on the platform mark, 100 km/h line",
                        "300 t electric: green, double yellow, yellow, red - AWS at each, wait for the signaller, stop at 4 km",
                        "DMU on wet leaves: 0.08 adhesion from 1.2 km, stop at 2.4 km inside the leaf fall - brake early",
                        "1600 t freight: 8 per mille down, 30 km/h TSR, stop at the red signal at 3.8 km",
                        "Night electric 120 km/h: TSR 60, approach-controlled red, DSD every 30 s, stop at 4.2 km"};
                for (int i = 0; i < 5; i++) { s.line((i == scn() ? "§b> " : "§f") + (i + 1) + " " + SimCore.NAMES[i]); s.line("§7   " + d[i]); }
                for (int i = 0; i < 5; i++) s.button("scn" + i, (i + 1) + " " + SimCore.NAMES[i], i == scn() ? Scr.GREEN : Scr.GREY);
            }
            default -> {
                if (core == null) s.line("§7No run yet. Pick a scenario, DESK key on, START, then SCREEN to sit and drive.");
                else {
                    SimCore c = core;
                    s.line("§f" + c.name + " §7trainee §f" + traineeName + "   " + (c.finished ? (c.passed ? "§aPASS" : "§cFAIL") : world.getTotalWorldTime() - seenAt >= 40 ? "§ePAUSED" : "§aRUNNING"));
                    s.line(String.format(Locale.ROOT, "§7Speed §f%.0f km/h §7limit §f%.0f   §7notch §f%d §7brake §f%s   §7to the stop §f%.0f m   §7time §f%.0f s", c.v * 3.6, c.limitAt(c.pos), c.notch, c.brake >= 8 ? "EMERG" : String.valueOf(c.brake), c.stopAt - c.pos, c.t));
                    s.line(String.format(Locale.ROOT, "§7SPAD §f%d §7AWS missed §f%d §7DSD missed §f%d §7slips §f%d §7slides §f%d §7TPWS §f%d", c.spads, c.awsMiss, c.vigMiss, c.slips, c.slides, c.tpws));
                    s.bar("SCORE", c.score, 100, c.score >= 75 ? 0x30A050 : 0xC03020, Math.round(c.score) + " / 100 (pass 75)");
                    for (String e : c.events) s.line("§7" + e);
                }
                s.button("start", "START", Scr.GREEN).button("abort", "ABORT", Scr.RED);
            }
        }
        return s.build();
    }

    @Override
    public String screenAction(EntityPlayer p, int page, String a) {
        if (a.startsWith("scn")) { if (running()) return "§cNot during a run"; ctl.set("s_scn", a.charAt(3) - '0'); controlsChanged(); return "§eScenario " + (scn() + 1) + " " + SimCore.NAMES[scn()]; }
        return com.dogpound.railmap.controls.IControlled.operate(this, a, p);
    }

    @Override protected void store(NBTTagCompound t) { NBTTagList l = new NBTTagList(); for (String r : results) l.appendTag(new NBTTagString(r)); t.setTag("results", l); }
    @Override protected void restore(NBTTagCompound t) { results.clear(); NBTTagList l = t.getTagList("results", 8); for (int i = 0; i < l.tagCount(); i++) results.add(l.getStringTagAt(i)); }
}
