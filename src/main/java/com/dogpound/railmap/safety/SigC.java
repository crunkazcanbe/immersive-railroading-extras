package com.dogpound.railmap.safety;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.auto.TrackPath;
import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.*;

/**
 * Signalling batch 3, Part C: #46 driver competency + route knowledge (Competency, TileDriverAssessor), #48 interactive
 * fault diagnosis (Diagnosis, TileDiagTerminal, the multimeter), #49 control panel builder (TilePanelElement).
 * The entry points SigBlocks / SigTicker / SigCommand / SigConsole / SigHooks call.
 */
final class SigC {
    private SigC() {}

    static final String[] PANEL = {"panel_button", "panel_switch", "panel_lamp", "panel_gauge", "panel_mimic"};

    static List<Block> blocks() {
        List<Block> l = new ArrayList<>();
        l.add(new SafetyBlock("driver_assessor", TileDriverAssessor::new, new double[]{0, 0, 1, 16, 16, 15},
                "Driver competency assessor: theory test, route knowledge, traction pass-out, competence record",
                "IDENT, START TEST, answer A-D. 230 V AC mains + 12 V. Drawers + cabinet are storage"));
        l.add(new SafetyBlock("diagnostic_terminal", TileDiagTerminal::new, new double[]{2, 0, 2, 14, 16, 14},
                "Fault diagnosis terminal: live signalling faults, measure (multimeter), diagnose, repair, verify",
                "TAKE a job or TRAIN (fault lab in TEST MODE). 230 V AC charger + 12 V. Tool case is storage"));
        String[][] tips = {{"Panel push button: works the device it is bound to", "Signal Wrench: pick a device, click it. NEXT: which control / channel"},
                {"Panel turn switch: sets the device / channel / points it is bound to", "Signal Wrench: pick a device, click it. NEXT: which control / channel"},
                {"Panel indication lamps: red = on / occupied / at danger, green = off", "Signal Wrench: pick a device, click it. NEXT: which control"},
                {"Panel gauge: reads the bound device's value, range selector", "Signal Wrench: pick a device, click it. NEXT: which control"},
                {"Panel mimic tile: a piece of miniature track diagram", "Shape selector; bind a track circuit / axle section / switch / signal: red occupied, white route set"}};
        for (int i = 0; i < PANEL.length; i++)
            l.add(new SafetyBlock(PANEL[i], TilePanelElement::new, new double[]{0, 0, 13, 16, 16, 16}, tips[i][0], tips[i][1] + ". 12 V through the panel"));
        return l;
    }

    static void registerTiles() {
        GameRegistry.registerTileEntity(TileDriverAssessor.class, new ResourceLocation(RailMap.MODID, "driver_assessor"));
        GameRegistry.registerTileEntity(TileDiagTerminal.class, new ResourceLocation(RailMap.MODID, "diagnostic_terminal"));
        GameRegistry.registerTileEntity(TilePanelElement.class, new ResourceLocation(RailMap.MODID, "panel_element"));
    }

    static void tick(World w) { Competency.tick(w); }
    static void fast(World w) { }

    // ---- commands ------------------------------------------------------------------------------------------------------
    static String usage() { return "driver [list|show <p>|reinstate <p>|incident <p> <SPAD|TRIP|AWS|OVERSPEED>|line <p> <line>|passout <p> <0-2>] | diag [jobs|sessions] | panel [list|bind x y z tx ty tz|channel x y z n|var x y z name|unbind x y z] | selftest c"; }

    static boolean command(ICommandSender s, World w, String[] a) {
        String sub = a[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "driver": driver(s, w, a); return true;
            case "diag": diag(s, w, a); return true;
            case "panel": panel(s, w, a); return true;
            case "selftest":
                if (a.length > 2 && a[2].equalsIgnoreCase("c")) { selftest(s, w); return true; }
                return false;
            default: return false;
        }
    }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
    private static int i(String v, int d) { try { return Integer.parseInt(v.trim()); } catch (RuntimeException e) { return d; } }

    private static void driver(ICommandSender s, World w, String[] a) {
        Competency c = Competency.get(w);
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        Competency.Rec r = a.length > 3 ? c.find(a[3]) : s instanceof EntityPlayer p ? c.rec(p) : null;
        switch (op) {
            case "list":
                if (c.recs.isEmpty()) { say(s, "No competence records yet (drive a train, or IDENT at a Driver Competency Assessor)"); return; }
                for (Competency.Rec x : c.recs.values())
                    say(s, String.format(Locale.ROOT, "§e%s§r %s · %.1f h · %d known · theory %d%% · SPAD %d TRIP %d AWS %d OVSP %d%s", x.name, Competency.gradeOf(w, x), x.total(),
                            Competency.knownCount(w, x), x.theoryBest, x.spads, x.trips, x.aws, x.overspeed, QuestEvents.rankOf(x.id).isEmpty() ? "" : " · " + QuestEvents.rankOf(x.id)));
                return;
            case "show":
                if (r == null) { say(s, "No record for " + (a.length > 3 ? a[3] : "you")); return; }
                say(s, "§l" + r.name + "§r " + Competency.gradeOf(w, r) + String.format(Locale.ROOT, " · D %.1f E %.1f S %.1f h · known %d · theory best %d%% (%d tests)", r.hours[0], r.hours[1], r.hours[2], Competency.knownCount(w, r), r.theoryBest, r.tests));
                say(s, "§7" + Competency.nextStep(w, r) + " · routes " + (r.lines.isEmpty() ? "-" : String.join(", ", r.lines)));
                for (String x : r.incidents.subList(0, Math.min(8, r.incidents.size()))) { String[] p = x.split("\\|", 3); say(s, " §c" + p[1] + "§r " + Recorder.clock(w, Long.parseLong(p[0])) + " " + (p.length > 2 ? p[2] : "")); }
                return;
            case "reinstate":
                if (r == null) { say(s, "No such driver"); return; }
                r.suspended = false; r.suspendWhy = ""; c.markDirty();
                AlarmManager.get(w).clear("suspend:" + r.id);
                Recorder.event(w, "COMPETENCE: " + r.name + " reinstated by " + s.getName());
                say(s, "§a" + r.name + " reinstated");
                return;
            case "incident": {
                if (r == null || a.length < 5) { say(s, "driver incident <player> <SPAD|TRIP|AWS|OVERSPEED>"); return; }
                EntityPlayer p = w.getPlayerEntityByUUID(r.id);
                if (p == null) { say(s, "That driver is not online"); return; }
                Competency.incident(w, Collections.singletonList(p), a[4], "logged by " + s.getName());
                say(s, "§eIncident logged");
                return;
            }
            case "line":
                if (r == null || a.length < 5) { say(s, "driver line <player> <line>"); return; }
                say(s, Competency.qualifyLine(w, r, a[4], null));
                return;
            case "passout":
                if (r == null || a.length < 5) { say(s, "driver passout <player> <0 diesel|1 electric|2 steam>"); return; }
                say(s, Competency.passOut(w, r, Math.max(0, Math.min(2, i(a[4], 0)))));
                return;
            default: say(s, "/irextras sig " + usage());
        }
    }

    private static void diag(ICommandSender s, World w, String[] a) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "jobs";
        if (op.equals("sessions")) {
            for (TileDiagTerminal t : SigTicker.tiles(w, TileDiagTerminal.class))
                say(s, "§e" + Geo.xz(t.getPos().toLong()) + "§r " + (t.job() == null ? "no job" : t.job().name + " " + Geo.xz(t.job().pos) + " · " + TileDiagTerminal.STEP[t.step()] + " · score " + t.score() + " · " + t.techName()));
            if (SigTicker.tiles(w, TileDiagTerminal.class).isEmpty()) say(s, "No Fault Diagnosis Terminals loaded");
            return;
        }
        List<Diagnosis.Job> l = Diagnosis.jobs(w, s.getPosition());
        if (l.isEmpty()) { say(s, "§aNo faults on 12 V signalling gear"); return; }
        for (Diagnosis.Job j : l.subList(0, Math.min(15, l.size()))) say(s, "§e" + j.name + "§r " + Geo.xz(j.pos) + " · " + j.source + " · " + Diagnosis.symptom(j));
    }

    private static void panel(ICommandSender s, World w, String[] a) {
        String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        if (op.equals("list")) {
            List<TilePanelElement> l = SigTicker.tiles(w, TilePanelElement.class);
            for (TilePanelElement e : l) say(s, "§e" + e.kind() + "§r " + Geo.xz(e.getPos().toLong()) + " · " + e.label() + " = " + e.value() + (e.powered() ? "" : " §c(no 12 V)"));
            if (l.isEmpty()) say(s, "No panel elements loaded");
            return;
        }
        if (a.length < 6) { say(s, "/irextras sig panel bind|channel|var|unbind x y z ..."); return; }
        BlockPos p = new BlockPos(i(a[3], 0), i(a[4], 0), i(a[5], 0));
        if (!(w.getTileEntity(p) instanceof TilePanelElement e)) { say(s, "No panel element at " + p.getX() + " " + p.getY() + " " + p.getZ()); return; }
        switch (op) {
            case "bind": if (a.length < 9) { say(s, "panel bind x y z tx ty tz"); return; } say(s, e.bindTo(new BlockPos(i(a[6], 0), i(a[7], 0), i(a[8], 0)))); return;
            case "channel": if (a.length < 7) { say(s, "panel channel x y z <1-64>"); return; } e.bindChannel(i(a[6], 1)); say(s, "§aBound: " + e.label()); return;
            case "var": if (a.length < 7) { say(s, "panel var x y z <name>"); return; } e.bindVar(a[6]); say(s, "§aBound: " + e.label()); return;
            case "unbind": e.unbind(); say(s, "§7Unbound"); return;
            default: say(s, "/irextras sig " + usage());
        }
    }

    /** checks of the part-C logic that need no world gear */
    private static void selftest(ICommandSender s, World w) {
        List<String> out = new ArrayList<>();
        int[] pf = new int[2];
        Competency.Rec r = new Competency.Rec();
        check(out, pf, "new driver is TRAINEE", Competency.grade(r, 0) == 0);
        r.theoryBest = 85; r.hours[0] = 5;
        check(out, pf, "80% + 5 h + 5 known = QUALIFIED", Competency.grade(r, 5) == 1);
        check(out, pf, "4 known is not enough", Competency.grade(r, 4) == 0);
        r.hours[0] = 45; r.hoursAtIncident = 40;
        check(out, pf, "SENIOR needs 20 clean hours", Competency.grade(r, 30) == 1);
        r.hoursAtIncident = 10;
        check(out, pf, "SENIOR after 20 clean hours", Competency.grade(r, 30) == 2);
        r.suspended = true;
        check(out, pf, "suspended = TRAINEE", Competency.grade(r, 99) == 0);
        List<Competency.Q> q = Competency.paper(w, 42);
        boolean ok = q.size() == 10;
        for (Competency.Q x : q) ok &= x.ans.length == 4 && x.correct >= 0 && x.correct < 4 && x.ans[x.correct] != null;
        check(out, pf, "paper has 10 questions with 4 answers and a right one", ok);
        check(out, pf, "the same seed sets the same paper", Competency.paper(w, 42).get(0).text.equals(q.get(0).text));
        check(out, pf, "segment distance", Math.abs(Competency.segD2(5, 5, 0, 0, 10, 0) - 25) < 1e-6);
        check(out, pf, "lab LAMP -> LAMP", Diagnosis.ofLab(FaultLab.Kind.LAMP) == Diagnosis.Cause.LAMP);
        check(out, pf, "lab TC_DROP -> DETECTOR", Diagnosis.ofLab(FaultLab.Kind.TC_DROP) == Diagnosis.Cause.DETECTOR);
        check(out, pf, "self-test text 'burnt out' -> BURNT", Diagnosis.ofText("burnt out") == Diagnosis.Cause.BURNT);
        check(out, pf, "self-test text 'off air' -> RADIO", Diagnosis.ofText("off air - x") == Diagnosis.Cause.RADIO);
        check(out, pf, "supply faults need gear + source readings", Diagnosis.needs(Diagnosis.Cause.SUPPLY).size() == 2 && Diagnosis.needs(Diagnosis.Cause.LAMP).size() == 1);
        String[] wr = TileDriverAssessor.wrap("one two three four five six seven", 9, 3);
        check(out, pf, "screen wrap", wr[0].equals("one two") && wr[1].equals("three") && wr[2].equals("four five"));
        check(out, pf, "mimic shapes", TilePanelElement.SHAPES.length == TilePanelElement.SHAPE_NAMES.length);
        for (TilePanelElement e : SigTicker.tiles(w, TilePanelElement.class)) {
            check(out, pf, "element " + Geo.xz(e.getPos().toLong()) + " " + e.label() + " reads", e.value() >= Integer.MIN_VALUE);
            break;
        }
        for (String l : out) say(s, l);
        say(s, "§lPart C self-test: §a" + pf[0] + " PASS §c" + pf[1] + " FAIL");
    }

    private static void check(List<String> out, int[] pf, String what, boolean ok) {
        out.add((ok ? "§aPASS " : "§cFAIL ") + "§r" + what);
        pf[ok ? 0 : 1]++;
    }

    // ---- console ----------------------------------------------------------------------------------------------------------
    static void fill(World w, List<String[]> out) {
        Competency c = Competency.get(w);
        int n = 0;
        for (Competency.Rec r : c.recs.values()) {
            if (n++ > 40) break;
            out.add(new String[]{"c.d", r.name, Competency.gradeOf(w, r), QuestEvents.rankOf(r.id), String.format(Locale.ROOT, "%.1f", r.total()),
                    String.format(Locale.ROOT, "%.1f/%.1f/%.1f", r.hours[0], r.hours[1], r.hours[2]), String.valueOf(Competency.knownCount(w, r)),
                    String.valueOf(r.theoryBest), r.spads + "/" + r.trips + "/" + r.aws + "/" + r.overspeed, r.suspended ? "1" : "0", r.id.toString(),
                    String.join(" ", r.lines), Competency.nextStep(w, r)});
            for (String x : r.incidents.subList(0, Math.min(4, r.incidents.size()))) {
                String[] p = x.split("\\|", 3);
                out.add(new String[]{"c.i", Recorder.clock(w, Long.parseLong(p[0])), r.name, p[1], p.length > 2 ? p[2] : ""});
            }
        }
        List<Diagnosis.Job> jobs = Diagnosis.jobs(w, null);
        for (Diagnosis.Job j : jobs.subList(0, Math.min(40, jobs.size())))
            out.add(new String[]{"c.j", j.name, Geo.xz(j.pos), j.source, Diagnosis.symptom(j)});
        for (TileDiagTerminal t : SigTicker.tiles(w, TileDiagTerminal.class)) {
            Diagnosis.Job j = t.job();
            out.add(new String[]{"c.s", Geo.xz(t.getPos().toLong()), j == null ? "" : j.name + " " + Geo.xz(j.pos), TileDiagTerminal.STEP[t.step()], j == null ? "" : String.valueOf(t.score()),
                    t.techName(), j == null ? "" : String.valueOf((w.getTotalWorldTime() - t.started()) / 20), String.join("\n", t.log().subList(0, Math.min(5, t.log().size()))), String.valueOf(t.getPos().toLong())});
        }
        n = 0;
        for (TilePanelElement e : SigTicker.tiles(w, TilePanelElement.class)) {
            if (n++ > 80) break;
            out.add(new String[]{"c.p", e.kind(), Geo.xz(e.getPos().toLong()), e.label(), String.valueOf(e.value()), e.powered() ? "1" : "0", String.valueOf(e.getPos().toLong())});
        }
    }

    /** 60 reinstate a driver (uuid), 61 abort a diagnosis terminal's job (pos), 62 unbind a panel element (pos) */
    static String op(World w, int op, String arg, String who) {
        switch (op) {
            case 60: {
                Competency c = Competency.get(w);
                Competency.Rec r = c.find(arg);
                if (r == null) return null;
                r.suspended = false; r.suspendWhy = ""; c.markDirty();
                AlarmManager.get(w).clear("suspend:" + r.id);
                Recorder.event(w, "COMPETENCE: " + r.name + " reinstated from the console by " + who);
                return "Reinstated " + r.name;
            }
            case 61: case 62: {
                BlockPos p;
                try { p = BlockPos.fromLong(Long.parseLong(arg)); } catch (NumberFormatException e) { return null; }
                TileEntity t = w.isBlockLoaded(p) ? w.getTileEntity(p) : null;
                if (op == 61 && t instanceof TileDiagTerminal d) return net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(d.wiredPress("abort", who));
                if (op == 62 && t instanceof TilePanelElement e) { e.unbind(); return "Unbound panel element"; }
                return null;
            }
            default: return null;
        }
    }

    // ---- hooks -----------------------------------------------------------------------------------------------------------
    /** hook (SigHooks): route-knowledge restrictions along a path {distance, kmh, stop, what, BlockPos, signal} */
    static void restrictions(World w, TrackPath path, List<Object[]> out) { Competency.restrictions(w, path, out); }

    /**
     * A driver incident for the competency records (#46): kind SPAD, TRIP (trip cock), AWS (AWS / cab-signal warning not
     * acknowledged), OVERSPEED (protection / supervisor intervention). Called by part A (DriverAids, trip cocks).
     */
    static void incident(World w, List<EntityPlayer> drivers, String kind, String text) { Competency.incident(w, drivers, kind, text); }
}
