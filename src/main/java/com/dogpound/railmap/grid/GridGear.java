package com.dogpound.railmap.grid;

import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * The protection + grid-automation machines (her ChatGPT list section 6): Protection IED, Transfer Switch Controller,
 * Power Quality Analyzer, 12 V UPS and Black Start Panel - their settings, their modelled controls (from
 * tools/gen_bb_protection.py), what their lamps / screens show and what their buttons do. The physics is in
 * Protection, Insulation, PowerQuality, Ups and Thermal so it also runs with the chunk unloaded; this is the face.
 */
final class GridGear {
    private GridGear() {}

    // ---- IED settings: one table drives the console, the keypad menu and the engine -------------------------------
    static final class P {
        final String key, label, unit, help;
        final int def, min, max, step;
        final boolean off;                       // 0 = function off, otherwise min..max
        P(String key, String label, int def, int min, int max, int step, boolean off, String unit, String help) {
            this.key = key; this.label = label; this.def = def; this.min = min; this.max = max; this.step = step; this.off = off; this.unit = unit; this.help = help;
        }
        String show(int v) {
            if (off && v == 0) return "OFF";
            switch (key) {
                case "curve": return new String[]{"SI", "VI", "EI", "LTI", "DT"}[Math.max(0, Math.min(4, v))];
                case "tms": return String.format(Locale.ROOT, "%.2f", v / 100.0);
                case "uf": case "of": return String.format(Locale.ROOT, "%.1fHz", v / 10.0);
                case "efon": case "dif": case "h2": case "bf": return v == 1 ? "ON" : "OFF";
                case "dcef": return v == 1 ? "TRIP" : "ALARM";
                case "aux": return v == 1 ? "CT-PWR" : "12V DC";
                default: return v + unit;
            }
        }
        int next(int v, int dir) {
            if (off && v == 0) return dir > 0 ? min : 0;
            int n = v + dir * step;
            if (off && n < min) return 0;
            return Math.max(min, Math.min(max, n));
        }
    }

    static final P[] IED = {
            new P("is", "I> PICKUP", 100, 25, 400, 5, false, "%", "50/51 phase overcurrent pick-up, % of the breaker's rated current (its trip rating in real amps)"),
            new P("curve", "I> CURVE", 0, 0, 4, 1, false, "", "IEC curve: SI standard, VI very, EI extremely, LTI long-time inverse, DT definite time"),
            new P("tms", "I> TMS", 10, 5, 120, 5, false, "", "Time multiplier x0.01 (10 = 0.10). Bigger = slower: grade upstream relays slower than downstream ones"),
            new P("hi", "I>> MULT", 8, 2, 30, 1, true, "x", "50 high-set instantaneous element, multiple of I> (0 = off)"),
            new P("hid", "I>> DELAY", 50, 0, 1000, 10, false, "ms", "50 high-set delay"),
            new P("efon", "IN> ON", 1, 0, 1, 1, false, "", "50N/51N earth-fault element (residual current)"),
            new P("efs", "IN> PICKUP", 20, 5, 100, 5, false, "%", "Earth-fault pick-up, % of I>. A resistance-earthed (NER) network needs this low"),
            new P("efd", "IN> DELAY", 500, 50, 5000, 50, false, "ms", "Earth-fault definite time"),
            new P("dif", "87T ON", 1, 0, 1, 1, false, "", "Transformer differential (needs the IED touching the transformer): trips on faults inside the transformer only"),
            new P("h2", "87T H2 BLK", 1, 0, 1, 1, false, "", "2nd-harmonic restraint: stops 87T tripping on magnetising inrush when the transformer is switched in"),
            new P("uv", "27 U<", 0, 50, 95, 5, true, "%", "Undervoltage trip below this % (0 = off) - drops motors / loads off a dead bus"),
            new P("uvd", "27 DELAY", 3000, 500, 10000, 500, false, "ms", "Undervoltage time delay"),
            new P("ov", "59 U>", 115, 105, 140, 5, true, "%", "Overvoltage trip above this % (0 = off)"),
            new P("ovd", "59 DELAY", 2000, 500, 10000, 500, false, "ms", "Overvoltage time delay"),
            new P("uf", "81U F<", 0, 560, 598, 1, true, "", "Under-frequency load shedding: trips below this (tenths of Hz, 588 = 58.8 Hz; 0 = off)"),
            new P("of", "81O F>", 0, 602, 640, 1, true, "", "Over-frequency trip above this (tenths of Hz; 0 = off)"),
            new P("ufd", "81 DELAY", 1000, 100, 5000, 100, false, "ms", "Frequency element delay"),
            new P("bf", "50BF ON", 1, 0, 1, 1, false, "", "Breaker-failure: if its breaker does not open, trip the one upstream"),
            new P("dcef", "64 ACTION", 0, 0, 1, 1, false, "", "DC earth leakage (first fault on a floating DC system): alarm only, or trip"),
            new P("aux", "AUX SUPPLY", 0, 0, 1, 1, false, "", "0 = 12 V DC station battery (land the 12 V wire: works through a blackout); 1 = CT-powered (only works while load current flows)")
    };

    static P param(String k) { for (P p : IED) if (p.key.equals(k)) return p; return null; }

    static final String[] PRIO = {"Non-essential (shed first)", "Essential (shed last)", "Critical (never shed)"};
    static final String[] BSTAGE = {"Auto (by what it feeds)", "1 Auxiliaries", "2 Transformers", "3 Distribution feeders", "4 Traction", "Never (manual only)"};
    static final String[] NER = {"Solidly earthed", "NER 400 A", "NER 1000 A"};
    static final String[] ROLE = {"Neutral (system) earth", "Maintenance earth (for work)"};
    static final String[] ATS_PREF = {"A (left breaker)", "B (right breaker)"};

    private static int idx(String[] a, String v, int d) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return d; }

    static void settings(TileGrid t, List<Setting> l) {
        String g = "Protection";
        switch (t.kind()) {
            case IED:
                for (P p : IED) {
                    if (p.key.equals("curve")) l.add(Setting.choice(g, p.key, p.label, p.help, Protection.CURVES[0], Protection.CURVES));
                    else if (p.max == 1 && p.min == 0) l.add(Setting.bool(g, p.key, p.label, p.help, p.def == 1));
                    else l.add(Setting.num(g, p.key, p.label, p.help, p.def, p.off ? 0 : p.min, p.max, p.step, p.unit));
                }
                break;
            case BREAKER:
                l.add(Setting.choice("Automation", "prio", "Load priority", "Load shedding order: non-essential loads are shed first, essential only if that is not enough, critical never", PRIO[0], PRIO));
                l.add(Setting.choice("Automation", "bstage", "Black-start stage", "When the Black Start Panel closes this breaker. Auto works it out from what is downstream", BSTAGE[0], BSTAGE));
                break;
            case EARTHING:
                l.add(Setting.choice("Machine", "role", "Role", "Neutral (system) earth: the network's star point - every network needs one. Maintenance earth: shorts all three phases to earth on an ISOLATED section so people can work on it (a permit needs one). Close a maintenance earth on a LIVE section and you have made a dead short", ROLE[0], ROLE));
                l.add(Setting.choice("Machine", "ner", "Neutral earthing", "Solid: an earth fault is nearly a short circuit (overcurrent sees it). A neutral earthing resistor limits it to 400 / 1000 A: kinder to plant, but only earth-fault (50N) protection sees it", NER[0], NER));
                break;
            case ATS:
                g = "Transfer";
                l.add(Setting.choice(g, "pref", "Preferred source", "Normal supply. The breaker on the controller's LEFT (facing its front) is A, the RIGHT one is B", ATS_PREF[0], ATS_PREF));
                l.add(Setting.num(g, "tdel", "Transfer delay", "How long the preferred source must be lost before transferring (rides through dips)", 3, 1, 60, 1, "s"));
                l.add(Setting.num(g, "rdel", "Retransfer delay", "How long the preferred source must be healthy again before going back", 30, 5, 600, 5, "s"));
                l.add(Setting.num(g, "dead", "Neutral dead time", "Both breakers open between sources (open transition): lets motors' residual voltage decay", 1, 0, 10, 1, "s"));
                l.add(Setting.num(g, "cool", "Generator cool-down", "Standby generator keeps running unloaded this long after retransfer, then stops", 60, 0, 600, 10, "s"));
                l.add(Setting.num(g, "vpu", "Source healthy above", "Voltage a source needs to count as available", 85, 70, 95, 1, "%"));
                l.add(Setting.bool(g, "closed", "Closed transition retransfer", "Make-before-break when both sources are live and in sync (no blink); otherwise open transition", false));
                l.add(Setting.bool(g, "genstart", "Start the standby generator", "Cranks the gensets on source B's side when B is dead and needed (gensets in MAN)", true));
                break;
            case RESTORE:
                g = "Restoration";
                l.add(Setting.num(g, "settle", "Settle time per stage", "Wait after each stage for frequency and voltage to settle", 10, 3, 120, 1, "s"));
                l.add(Setting.num(g, "fmin", "Minimum frequency to switch", "Tenths of Hz: no breaker is closed while the island is below this (595 = 59.5 Hz)", 595, 550, 599, 1, ""));
                l.add(Setting.num(g, "gmax", "Maximum generator loading", "No more load is picked up while the generators are above this", 85, 40, 100, 5, "%"));
                break;
            case PQA:
                g = "Limits";
                l.add(Setting.num(g, "vlo", "Voltage low limit (sag)", "", 90, 50, 99, 1, "%"));
                l.add(Setting.num(g, "vhi", "Voltage high limit (swell)", "", 110, 101, 130, 1, "%"));
                l.add(Setting.num(g, "df", "Frequency deviation limit", "tenths of Hz", 5, 1, 50, 1, ""));
                l.add(Setting.num(g, "vuf", "Voltage unbalance limit", "tenths of % (20 = 2.0 %, the usual planning limit)", 20, 5, 100, 1, ""));
                l.add(Setting.num(g, "thd", "THD / DC ripple limit", "tenths of % (50 = 5.0 %, IEEE 519 at MV)", 50, 10, 200, 5, ""));
                l.add(Setting.num(g, "pf", "Power factor limit", "% (90 = 0.90)", 90, 50, 99, 1, "%"));
                break;
            case UPS:
                g = "Machine";
                l.add(Setting.choice(g, "ahs", "Battery", "12 V lead-acid bank. Capacity / load = autonomy (Minecraft hours)", "65 Ah", "24 Ah", "65 Ah", "100 Ah", "200 Ah"));
                l.add(Setting.num(g, "chg", "Charger current", "Recharge rate (and what it draws from the AC side)", 10, 2, 40, 1, "A"));
                l.add(Setting.choice("Electrical", "primaryLv", "AC input", "The mains voltage on its back face", Elec.Level.AC240.label,
                        Elec.Level.AC120.label, Elec.Level.AC240.label, Elec.Level.AC208.label, Elec.Level.AC480.label));
                break;
            default: break;
        }
    }

    /** copy the console's settings into the grid node so the engine works with this chunk unloaded */
    static void mirror(TileGrid t, GridData.Node n) {
        SettingsStore c = t.settings();
        switch (t.kind()) {
            case IED:
                for (P p : IED) {
                    int v = p.key.equals("curve") ? idx(Protection.CURVES, c.text("curve"), 0) : p.max == 1 && p.min == 0 ? (c.bool(p.key) ? 1 : 0) : c.num(p.key);
                    n.set.put(p.key, v);
                }
                break;
            case BREAKER: n.set.put("prio", 2 - idx(PRIO, c.text("prio"), 0)); n.set.put("bstage", idx(BSTAGE, c.text("bstage"), 0)); break;
            case EARTHING: n.set.put("ner", new int[]{0, 400, 1000}[idx(NER, c.text("ner"), 0)]); n.set.put("maint", idx(ROLE, c.text("role"), 0)); break;
            case PQA: for (String k : new String[]{"vlo", "vhi", "df", "vuf", "thd", "pf"}) n.set.put(k, c.num(k)); break;
            case UPS: n.set.put("ah", Ups.AH[idx(new String[]{"24 Ah", "65 Ah", "100 Ah", "200 Ah"}, c.text("ahs"), 1)]); n.set.put("chg", c.num("chg")); n.kva = 3; break;
            default: break;
        }
    }

    // ---- IED keypad menu: page 0 measurements, 1 fault record, 2.. one setting each ---------------------------------
    static String iedPress(TileGrid t, String id, EntityPlayer p) {
        World w = t.getWorld();
        GridData d = GridData.get(w);
        GridData.Node n = t.nodePublic();
        int page = t.pv("page"), pages = 2 + IED.length;
        boolean edit = t.pv("edit") == 1;
        switch (id) {
            case "mode": {
                boolean test = n.st("test") == 0;
                n.st.put("test", test ? 1 : 0); d.markDirty();
                History.log(w, t.getPos(), "TEST", test ? "switched to TEST - trip outputs blocked by " + who(p) : "back IN SERVICE (" + who(p) + ")");
                if (test) Protection.standing(w, "iedtest:" + t.getPos().toLong(), com.dogpound.railmap.alarm.AlarmManager.Cat.POWER, com.dogpound.railmap.alarm.AlarmManager.Sev.MINOR,
                        "IED " + Protection.at(t.getPos().toLong()) + " in TEST - its breaker is NOT protected", t.getPos());
                else { com.dogpound.railmap.alarm.AlarmManager.get(w).clear("iedtest:" + t.getPos().toLong()); t.put("edit", 0); }
                return test ? "§eTEST mode: trip outputs BLOCKED - settings can be changed (ENTER on a setting)" : "§aIN SERVICE - protecting";
            }
            case "up": case "down": {
                int dir = id.equals("up") ? 1 : -1;
                if (edit && page >= 2) {
                    P q = IED[page - 2];
                    int v = n.get(q.key, q.def);
                    int nv = q.next(v, dir);
                    if (q.key.equals("curve")) t.settings().put("curve", Protection.CURVES[nv]);
                    else if (q.max == 1 && q.min == 0) t.settings().put(q.key, Boolean.toString(nv == 1));
                    else t.settings().put(q.key, Integer.toString(nv));
                    mirror(t, n); d.markDirty();
                    return "§e" + q.label + " = " + q.show(nv);
                }
                t.put("page", ((page - dir) % pages + pages) % pages);
                return "";
            }
            case "enter":
                if (page < 2) { t.put("page", 2); return ""; }
                if (n.st("test") == 0) return "§cSettings are locked IN SERVICE - turn the key to TEST first (that blocks its trips)";
                t.put("edit", edit ? 0 : 1);
                if (edit) History.log(w, t.getPos(), "SET", IED[page - 2].label + " = " + IED[page - 2].show(n.get(IED[page - 2].key, IED[page - 2].def)) + " (" + who(p) + ")");
                return edit ? "§aSaved" : "§eEDIT " + IED[page - 2].label + ": ▲▼ to change, ENTER to save";
            case "esc": t.put("edit", 0); t.put("page", 0); return "";
            case "reset":
                for (String k : new String[]{"ltrip", "l50", "l51", "l50n", "l87", "l27", "l81", "l64", "lbf"}) n.st.remove(k);
                d.markDirty();
                return "§aTargets reset";
            case "trip": case "close": {
                for (long bp : Protection.tripsOf(d, t.getPos().toLong())) {
                    TileGrid b = tile(w, BlockPos.fromLong(bp));
                    if (b == null) continue;
                    if (id.equals("trip")) { if (d.nodes().get(bp).breaker == 0) { b.setBreaker((byte) 1, "opened from the IED by " + who(p)); return "§cBreaker OPEN (control from the IED)"; } }
                    else { String r = b.press("close", null); return r == null || r.equals("ok") ? "§aBreaker CLOSED (control from the IED - breaker key in LOCAL)" : r; }
                }
                return "§7No breaker to " + (id.equals("trip") ? "open" : "close");
            }
            default: return null;
        }
    }

    // ---- what the lamps and screens show -----------------------------------------------------------------------
    static void display(TileGrid t, GridData d, GridData.Node n) {
        World w = t.getWorld();
        switch (t.kind()) {
            case IED: {
                boolean aux = n.st("aux") == 1;
                boolean flash = (w.getTotalWorldTime() / 10) % 2 == 0;
                t.put("lhealthy", aux && n.st("ok") == 1);
                t.put("lpick", aux && n.st("pick") == 1);
                t.put("ltest", aux && n.st("test") == 1);
                for (String k : new String[]{"ltrip", "l50", "l51", "l50n", "l87", "l27", "l81", "l64", "lbf"}) t.put(k, aux && n.st(k) == 1);
                t.put("mode", n.st("test"));
                if (!aux) { t.text("lcd1", ""); t.text("lcd2", ""); t.text("lcd3", ""); break; }
                int page = t.pv("page");
                if (page == 0) {
                    t.text("lcd1", String.format(Locale.ROOT, "IA %s IN %s", Elec.si(n.st("ia"), "A"), n.st("ie") > 0 ? Elec.si(n.st("ie"), "A") : "0A"));
                    t.text("lcd2", String.format(Locale.ROOT, "U %.1f%% F %.2fHz", n.st("v") / 10.0, n.st("hz") / 100.0));
                    t.text("lcd3", Protection.zoneOf(d, t.getPos().toLong()) != Long.MIN_VALUE ? String.format(Locale.ROOT, "Id %.1f%% I> %s", n.st("idf") / 10.0, Elec.si(n.st("is"), "A"))
                            : "I> " + Elec.si(n.st("is"), "A") + (n.st("pick") == 1 ? " PICKUP" : ""));
                } else if (page == 1) {
                    String last = "";
                    for (int i = n.log.size() - 1; i >= 0; i--) if (n.log.get(i).contains("|TRIP|")) { last = n.log.get(i); break; }
                    String[] f = last.split("\\|", 3);
                    t.text("lcd1", "FAULT RECORD");
                    t.text("lcd2", f.length < 3 ? "no trips" : cut(f[2], 22));
                    t.text("lcd3", f.length < 3 ? "" : History.clock(Long.parseLong(f[0])) + " " + Elec.si(n.st("rec"), "A"));
                } else {
                    P q = IED[Math.min(IED.length - 1, page - 2)];
                    boolean edit = t.pv("edit") == 1;
                    t.text("lcd1", String.format(Locale.ROOT, "SET %d/%d %s", page - 1, IED.length, n.st("test") == 1 ? "TEST" : "LOCK"));
                    t.text("lcd2", q.label);
                    t.text("lcd3", (edit && flash ? "> " : "  ") + q.show(n.get(q.key, q.def)) + (q.key.equals("is") ? " " + Elec.si(n.st("is"), "A") : ""));
                }
                break;
            }
            case ATS: {
                boolean t0 = t.pv("lt") > 0;
                t.put("lauto", t0 || t.pv("mode") == 1);
                for (String k : new String[]{"la", "lb", "lca", "lcb", "lgen", "ltimer", "lfault"}) if (t0) t.put(k, 1);
                if (t.pv("lt") > 0) t.put("lt", t.pv("lt") - 1);
                break;
            }
            case RESTORE:
                if (t.pv("lt") > 0) { for (String k : new String[]{"s1", "s2", "s3", "s4", "s5", "ldead", "lhold", "ldone", "lfault"}) t.put(k, 1); t.put("lt", t.pv("lt") - 1); }
                break;
            case PQA: {
                boolean on = t.pv("lpwr") == 1;
                int page = t.pv("page");
                for (String k : new String[]{"av", "af", "au", "ah", "ap"}) t.put(k, on && n.st(k) == 1);
                t.put("lev", on && n.st("nsag") + n.st("nswl") + n.st("nint") > t.pv("evack"));
                t.put("gv", on ? n.st("v") / 10 : 0);
                t.put("gthd", on ? (n.st("dc") == 1 ? n.st("rip") : n.st("thdv")) / 10 : 0);
                if (!on) { for (int i = 1; i <= 4; i++) t.text("l" + i, ""); break; }
                GridData.Net net = PowerQuality.measured(d, t.getPos().toLong());
                String lv = net == null || net.level == null ? "-" : net.level.label;
                double v = n.st("v") / 1000.0;
                switch (page) {
                    case 0:
                        t.text("l1", "V/F  " + lv);
                        t.text("l2", String.format(Locale.ROOT, "U %s %.1f%%", net == null || net.level == null ? "-" : Elec.si(net.level.volts * v, "V"), v * 100));
                        t.text("l3", n.st("dc") == 1 ? "DC - no frequency" : String.format(Locale.ROOT, "F %.2fHz d%+.2f", n.st("hz") / 100.0, net == null || net.level == null ? 0 : n.st("hz") / 100.0 - net.level.hz));
                        t.text("l4", String.format(Locale.ROOT, "P %s", Elec.si(n.st("kw") * 1000.0, "W")));
                        break;
                    case 1:
                        t.text("l1", "UNBALANCE / HARMONICS");
                        t.text("l2", n.st("dc") == 1 ? "VUF n/a (DC)" : String.format(Locale.ROOT, "VUF %.2f%%", n.st("vuf") / 100.0));
                        t.text("l3", n.st("dc") == 1 ? String.format(Locale.ROOT, "RIPPLE %.2f%%", n.st("rip") / 100.0) : String.format(Locale.ROOT, "THDv %.2f%%", n.st("thdv") / 100.0));
                        t.text("l4", n.st("dc") == 1 ? "" : String.format(Locale.ROOT, "THDi %.1f%%", n.st("thdi") / 10.0));
                        break;
                    case 2:
                        t.text("l1", "POWER FACTOR");
                        t.text("l2", String.format(Locale.ROOT, "PF %.2f", n.st("pf") / 100.0));
                        t.text("l3", net == null ? "" : "S " + Elec.si(net.sVA, "VA"));
                        t.text("l4", net == null ? "" : "Q " + Elec.si(net.qVar, "var"));
                        break;
                    default: {
                        t.text("l1", "EVENTS");
                        t.text("l2", String.format(Locale.ROOT, "SAG %d SWL %d", n.st("nsag"), n.st("nswl")));
                        t.text("l3", String.format(Locale.ROOT, "INTERRUPT %d", n.st("nint")));
                        String last = "";
                        for (int i = n.log.size() - 1; i >= 0; i--) if (n.log.get(i).contains("|PQ|")) { last = History.clock(Long.parseLong(n.log.get(i).split("\\|")[0])) + " " + n.log.get(i).split("\\|", 3)[2]; break; }
                        t.text("l4", cut(last, 22));
                    }
                }
                break;
            }
            case UPS: {
                boolean ac = n.st("ac") == 1, bat = n.st("onbat") == 1, out = n.dcOn && !n.lvd;
                boolean lt = t.pv("lt") > 0;
                if (lt) t.put("lt", t.pv("lt") - 1);
                t.put("lac", lt || ac && n.st("cf") == 0);
                t.put("lbat", lt || bat && n.dcOn);
                t.put("llow", lt || Ups.soc(n) < 0.2);
                t.put("lcf", lt || n.st("cf") != 0 || Ups.health(n) < 0.6);
                t.put("ltf", lt || n.st("tf") == 1);
                GridData.Net o = n.net >= 0 && n.net < d.nets().size() ? d.nets().get(n.net) : null;
                t.put("lef", lt || o != null && o.efAmps < 0);
                t.put("lout", lt || out);
                t.put("out", n.dcOn ? 1 : 0);
                int soc = (int) Math.round(Ups.soc(n) * 100);
                t.put("gsoc", soc);
                t.put("soc", soc);
                t.put("gamps", (int) Math.round(n.st("cw") / 13.6));
                t.text("lcd1", (n.st("tst") > 0 ? "BATT TEST " : bat ? "ON BATTERY " : "ON MAINS ") + String.format(Locale.ROOT, "%.1fV", out ? Ups.volts(n) : 0.0));
                t.text("lcd2", String.format(Locale.ROOT, "LOAD %dW %d DEV", n.st("lw"), n.st("dev")));
                t.text("lcd3", n.lvd ? "LVD - OUTPUT OFF" : "AUTONOMY " + Ups.autonomy(n));
                if (bat && n.dcOn && (w.getTotalWorldTime() / 20) % 4 == 0 && t.pv("mute") == 0)
                    w.playSound(null, t.getPos(), com.dogpound.railmap.ModSounds.BEEP_OK, net.minecraft.util.SoundCategory.BLOCKS, 0.4f, 1.0f);
                if (!bat) t.put("mute", 0);
                break;
            }
            default: break;
        }
    }

    // ---- buttons -----------------------------------------------------------------------------------------------
    static String press(TileGrid t, String id, EntityPlayer p) {
        World w = t.getWorld();
        GridData d = GridData.get(w);
        GridData.Node n = t.nodePublic();
        switch (t.kind()) {
            case IED: return iedPress(t, id, p);
            case ATS:
                switch (id) {
                    case "mode": { int m = (t.pv("mode") + 1) % 4; t.put("mode", m); History.log(w, t.getPos(), "ATS", "mode " + new String[]{"OFF", "AUTO", "MAN A", "MAN B"}[m] + " (" + who(p) + ")");
                        return "§7Mode: " + new String[]{"§cOFF (both incomers open)", "§aAUTO", "§eMANUAL - source A", "§eMANUAL - source B"}[m]; }
                    case "test": if (t.pv("mode") != 1) return "§eTEST works in AUTO"; t.put("test", t.pv("test") > 0 ? 0 : 60); return t.pv("test") > 0 ? "§eTEST: simulating loss of the preferred source for 60 s" : "§7Test ended";
                    case "retx": t.put("bypass", 1); return "§eRetransfer / transfer timer bypassed";
                    case "reset": t.put("as", 0); t.text("why", ""); return "§aLockout reset";
                    case "lamptest": t.put("lt", 6); return "§7Lamp test";
                    default: return null;
                }
            case RESTORE:
                switch (id) {
                    case "mode": { int m = (t.pv("mode") + 1) % 3; t.put("mode", m); return "§7Mode: " + new String[]{"§cOFF", "§eSTEP (confirm each stage)", "§aAUTO"}[m]; }
                    case "start":
                        if (t.pv("mode") == 0) return "§cMode is OFF";
                        if (t.pv("stage") > 0 && t.pv("stage") < 6) return "§7Already restoring (stage " + t.pv("stage") + ")";
                        t.put("stage", 1); t.put("hold", 0); t.put("wait", 0); t.put("settle", 0); t.skip().clear(); t.text("msg", "STAGE 1 START GENSETS");
                        History.log(w, t.getPos(), "RESTORE", "black start STARTED by " + who(p));
                        if (p != null) BLACK_START_BY.put(t.getPos().toLong(), p.getUniqueID());   // quest hook: who gets the credit
                        GridTicker.alarm(w, t.getPos().toLong(), "§b⚙ Black start begun at " + Protection.at(t.getPos().toLong()) + " - restoring the grid in stages");
                        return "§aBlack start: stage 1 - starting the black-start gensets";
                    case "next":
                        if (t.pv("ready") != 1) return "§7Stage not finished yet";
                        t.put("ready", 0); t.put("stage", t.pv("stage") + 1); t.put("wait", 0);
                        return "§aConfirmed - stage " + t.pv("stage");
                    case "hold": t.put("hold", 1 - t.pv("hold")); return t.pv("hold") == 1 ? "§eHOLD - restoration paused" : "§aResumed";
                    case "abort":
                        History.log(w, t.getPos(), "RESTORE", "ABORTED at stage " + t.pv("stage") + " by " + who(p));
                        t.put("stage", 0); t.put("ready", 0); t.text("msg", "ABORTED"); return "§cRestoration aborted - breakers stay as they are";
                    case "lamptest": t.put("lt", 6); return "§7Lamp test";
                    default: return null;
                }
            case PQA:
                switch (id) {
                    case "page": t.put("page", (t.pv("page") + 1) % 4); return "";
                    case "reset": n.st.put("nsag", 0); n.st.put("nswl", 0); n.st.put("nint", 0); t.put("evack", 0); d.markDirty(); return "§aEvent counters reset";
                    case "ack": t.put("evack", n.st("nsag") + n.st("nswl") + n.st("nint")); return "§eEvents acknowledged";
                    case "trig": {
                        GridData.Net net = PowerQuality.measured(d, t.getPos().toLong());
                        String snap = net == null || net.level == null ? "no network" : String.format(Locale.ROOT, "SNAPSHOT %s U %.1f%% F %.2f Hz VUF %.2f%% THDv %.2f%% THDi %.1f%% PF %.2f P %s",
                                net.level.label, n.st("v") / 10.0, n.st("hz") / 100.0, net.vuf, net.thdV, net.thdI, net.pf, Elec.si(net.pW, "W"));
                        History.log(w, t.getPos(), "PQ", snap);
                        return "§b" + snap;
                    }
                    default: return null;
                }
            case UPS:
                switch (id) {
                    case "out":
                        n.dcOn = !n.dcOn; d.touch();
                        History.log(w, t.getPos(), "UPS", "12 V output switched " + (n.dcOn ? "ON" : "OFF") + " by " + who(p));
                        return n.dcOn ? "§a12 V DC output ON" : "§c12 V DC output OFF - everything on it goes dark";
                    case "test":
                        if (n.st("ac") == 0) return "§cNo mains - it is already on battery";
                        n.st.put("tst", 10); n.st.put("tf", 0); d.markDirty();
                        return "§eBATTERY TEST: charger off, 10 s on battery under the real load";
                    case "mute": t.put("mute", 1); return "§7Buzzer muted";
                    case "reset": n.st.put("tf", 0); if (n.st("cf") == 2) n.st.put("cf", 0); d.markDirty(); return "§aFault flags reset";
                    case "lamptest": t.put("lt", 6); return "§7Lamp test";
                    default: return null;
                }
            default: return null;
        }
    }

    // ---- once a second (tile loaded): aux supply, the transfer switch and the black-start sequencer --------------
    static void second(TileGrid t) {
        World w = t.getWorld();
        GridData d = GridData.get(w);
        GridData.Node n = t.nodePublic();
        boolean aux12 = com.dogpound.railmap.wire.WireNet.power(w, t.getPos()).state == 1 || com.dogpound.railmap.RailMapConfig.gearNeedsPower == false;
        switch (t.kind()) {
            case IED: if (n.get("aux", 0) == 0) { if (n.st("aux") != (aux12 ? 1 : 0)) { n.st.put("aux", aux12 ? 1 : 0); d.markDirty();
                    History.log(w, t.getPos(), "AUX", aux12 ? "12 V aux supply healthy" : "12 V aux supply LOST - relay dead");
                    if (!aux12) GridTicker.alarm(w, t.getPos().toLong(), "§c⚠ IED at " + Protection.at(t.getPos().toLong()) + " lost its 12 V aux supply - its breaker is UNPROTECTED (feed it from a UPS)"); } }
                break;
            case PQA: t.put("lpwr", aux12); break;
            case ATS: t.put("lpwr", aux12); if (aux12) ats(t, d, n); break;
            case RESTORE: t.put("lpwr", aux12); if (aux12) restore(t, d); break;
            default: break;
        }
    }

    static TileGrid tile(World w, BlockPos p) { return w.isBlockLoaded(p) && w.getTileEntity(p) instanceof TileGrid g ? g : null; }

    static String who(EntityPlayer p) { return p == null ? "remote" : p.getName(); }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

    // ---- ATS ---------------------------------------------------------------------------------------------------
    /** the two incomers: [A = left, B = right] as you face the controller (else any two breakers it touches) */
    static long[] atsBreakers(TileGrid t, GridData d) {
        EnumFacing f = t.controlFacing();
        BlockPos p = t.getPos();
        long a = Long.MIN_VALUE, b = Long.MIN_VALUE;
        GridData.Node l = d.node(p.offset(f.rotateY())), r = d.node(p.offset(f.rotateYCCW()));
        if (l != null && l.kind == GridKind.BREAKER) a = p.offset(f.rotateY()).toLong();
        if (r != null && r.kind == GridKind.BREAKER) b = p.offset(f.rotateYCCW()).toLong();
        if (a == Long.MIN_VALUE || b == Long.MIN_VALUE) {
            List<Long> any = new ArrayList<>();
            for (EnumFacing x : EnumFacing.values()) { GridData.Node q = d.node(p.offset(x)); if (q != null && q.kind == GridKind.BREAKER) any.add(p.offset(x).toLong()); }
            if (any.size() >= 2) { a = any.get(0); b = any.get(1); }
        }
        return new long[]{a, b};
    }

    /** nets around a breaker: its own and its neighbours' (not counting control gear) */
    private static Set<Integer> around(GridData d, long b) {
        Set<Integer> s = new HashSet<>();
        GridData.Node n = d.nodes().get(b);
        if (n != null && n.net >= 0) s.add(n.net);
        BlockPos p = BlockPos.fromLong(b);
        for (EnumFacing f : EnumFacing.values()) { GridData.Node x = d.node(p.offset(f)); if (x != null && !x.kind.control() && x.net >= 0) s.add(x.net); }
        return s;
    }

    private static boolean healthy(GridData.Net n, int vpu) {
        return n != null && n.level != null && n.problem.isEmpty() && n.puSource * 100 >= vpu && n.puSource < 1.12 && (!n.level.ac || Math.abs(n.hzPu - 1) < 0.02);
    }

    /** a breaker the other one of an ATS pair is interlocked with: returns why closing is refused, or "" */
    static String interlock(World w, BlockPos breaker) {
        GridData d = GridData.get(w);
        for (EnumFacing f : EnumFacing.values()) {
            GridData.Node c = d.node(breaker.offset(f));
            if (c == null || c.kind != GridKind.ATS) continue;
            TileGrid at = tile(w, breaker.offset(f));
            if (at == null) continue;
            for (long o : atsBreakers(at, d)) {
                if (o == Long.MIN_VALUE || o == breaker.toLong()) continue;
                GridData.Node on = d.nodes().get(o);
                if (on != null && on.breaker == 0) return "⛔ INTERLOCK: the other incomer (" + Protection.at(o) + ") of this transfer pair is closed - two sources are never paralleled";
            }
        }
        return "";
    }

    private static void ats(TileGrid t, GridData d, GridData.Node n) {
        World w = t.getWorld();
        long[] ab = atsBreakers(t, d);
        if (ab[0] == Long.MIN_VALUE || ab[1] == Long.MIN_VALUE) { t.put("lfault", 1); t.text("status", "NEEDS 2 BREAKERS"); return; }
        int pref = idx(ATS_PREF, t.settings().text("pref"), 0);
        long P = ab[pref], Q = ab[1 - pref];
        GridData.Node pn = d.nodes().get(P), qn = d.nodes().get(Q);
        List<GridData.Net> nets = d.nets();
        Set<Integer> sp = around(d, P), sq = around(d, Q), load = new HashSet<>(sp);
        load.retainAll(sq);
        int L = load.isEmpty() ? -1 : load.iterator().next();
        int vpu = t.settings().num("vpu");
        GridData.Net srcP = side(nets, sp, L, pn), srcQ = side(nets, sq, L, qn);
        boolean testing = t.pv("test") > 0;
        if (testing) t.put("test", t.pv("test") - 1);
        boolean okP = healthy(srcP, vpu) && !testing, okQ = healthy(srcQ, vpu);
        boolean compatible = srcP == null || srcQ == null || srcP.level == null || srcQ.level == null || srcP.level == srcQ.level;
        // lamps + screens
        boolean[] avail = {pref == 0 ? okP : okQ, pref == 0 ? okQ : okP};
        t.put("la", avail[0]); t.put("lb", avail[1]);
        t.put("lca", d.nodes().get(ab[0]).breaker == 0); t.put("lcb", d.nodes().get(ab[1]).breaker == 0);
        t.text("va", "A " + src(pref == 0 ? srcP : srcQ));
        t.text("vb", "B " + src(pref == 0 ? srcQ : srcP));
        t.put("ga", srcVolts(pref == 0 ? srcP : srcQ)); t.put("gb", srcVolts(pref == 0 ? srcQ : srcP));
        int mode = t.pv("mode");
        if (!t.hasPv("mode")) { t.put("mode", 1); mode = 1; }
        int as = t.pv("as"), tmr = t.pv("tmr");
        boolean bypass = t.pv("bypass") == 1;
        t.put("bypass", 0);
        String P_ = pref == 0 ? "A" : "B", Q_ = pref == 0 ? "B" : "A";
        if (!compatible) { as = 6; t.text("why", "INCOMPATIBLE SOURCES " + srcP.level.label + " / " + srcQ.level.label); }
        if (mode == 0) { open(t, P, "ATS OFF"); open(t, Q, "ATS OFF"); t.text("status", "OFF - LOAD DEAD"); t.put("ltimer", 0); t.put("lgen", 0); t.put("lfault", 0); return; }
        if (mode == 2 || mode == 3) {                                      // manual: the selected source, break-before-make
            long want = ab[mode - 2], other = ab[3 - mode];
            if (d.nodes().get(other).breaker == 0) open(t, other, "ATS manual");
            else if (d.nodes().get(want).breaker != 0) close(t, want, "ATS manual");
            t.text("status", "MANUAL " + (mode == 2 ? "A" : "B")); t.put("ltimer", 0); t.put("lfault", 0);
            return;
        }
        // cool-down of a generator it started
        if (t.pv("cd") > 0) { t.put("cd", t.pv("cd") - 1); if (t.pv("cd") == 0 && t.pv("gs") == 1) { gens(t, d, srcQ, false); t.put("gs", 0); } }
        String status;
        switch (as) {
            case 0: {                                                          // on the preferred source
                if (!okP) { as = 1; tmr = t.settings().num("tdel"); log(t, "preferred source " + P_ + " LOST" + (testing ? " (TEST)" : "") + " - transfer timer " + tmr + " s"); status = "SRC " + P_ + " LOST"; break; }
                if (pn.breaker != 0 && qn.breaker != 0) close(t, P, "ATS: preferred source");
                else if (qn.breaker == 0 && pn.breaker != 0) { as = 3; }
                status = "ON " + P_ + " (NORMAL)";
                break;
            }
            case 1: {                                                          // transfer timer running
                if (okP) { as = 0; log(t, "source " + P_ + " came back within the transfer delay - no transfer"); status = "ON " + P_; break; }
                if (!okQ) {
                    if (t.settings().bool("genstart") && t.pv("gs") == 0 && srcQ != null) { gens(t, d, srcQ, true); t.put("gs", 1); log(t, "standby source " + Q_ + " dead - GENERATOR START signal"); }
                    status = "WAIT FOR " + Q_; tmr = Math.max(0, tmr - 1); break;
                }
                if (--tmr <= 0 || bypass) { open(t, P, "ATS transfer"); as = 2; tmr = t.settings().num("dead"); }
                status = "XFER IN " + Math.max(0, tmr) + "s";
                break;
            }
            case 2: {                                                          // neutral: both open
                if (--tmr <= 0) {
                    String r = close(t, Q, "ATS transfer to " + Q_);
                    if (r.startsWith("ok")) { as = 3; log(t, "TRANSFERRED to standby source " + Q_); GridTicker.alarm(w, t.getPos().toLong(), "§e⚙ ATS " + Protection.at(t.getPos().toLong()) + " transferred the load to source " + Q_); }
                    else { as = 6; t.text("why", "CLOSE " + Q_ + " REFUSED: " + r); }
                }
                status = "NEUTRAL";
                break;
            }
            case 3: {                                                          // on standby
                if (okP) { as = 4; tmr = t.settings().num("rdel"); log(t, "source " + P_ + " healthy again - retransfer timer " + tmr + " s"); }
                status = "ON " + Q_ + " (STANDBY)";
                break;
            }
            case 4: {                                                          // retransfer timer
                if (!okP) { as = 3; status = "ON " + Q_; break; }
                if (--tmr <= 0 || bypass) {
                    boolean sync = okQ && srcP != null && srcQ != null && Math.abs(srcP.hzPu - srcQ.hzPu) < 0.002;
                    if (t.settings().bool("closed") && sync) {                // make-before-break, under 100 ms in parallel
                        TileGrid pb = tile(w, BlockPos.fromLong(P));
                        if (pb != null && pb.hasRelayPublic() && pb.lr()) { pb.setBreaker((byte) 0, "ATS closed-transition retransfer"); open(t, Q, "ATS closed-transition retransfer"); as = 0; log(t, "closed-transition RETRANSFER to " + P_ + " (no break)"); t.put("cd", t.settings().num("cool")); }
                        else { open(t, Q, "ATS retransfer"); as = 5; tmr = t.settings().num("dead"); }
                    } else { open(t, Q, "ATS retransfer"); as = 5; tmr = t.settings().num("dead"); }
                }
                status = as == 4 ? "RETX IN " + Math.max(0, tmr) + "s" : "NEUTRAL";
                break;
            }
            case 5: {
                if (--tmr <= 0) {
                    String r = close(t, P, "ATS retransfer to " + P_);
                    if (r.startsWith("ok")) { as = 0; log(t, "RETRANSFERRED to preferred source " + P_); t.put("cd", t.settings().num("cool")); }
                    else { as = 6; t.text("why", "CLOSE " + P_ + " REFUSED: " + r); }
                }
                status = "NEUTRAL";
                break;
            }
            default:
                if (t.pv("as") != 6) log(t, "LOCKOUT: " + t.ptext("why"));
                status = "LOCKOUT";
        }
        // a manual mess: both closed without closed transition - open the standby at once
        if (pn.breaker == 0 && qn.breaker == 0) { open(t, Q, "ATS interlock: both incomers were closed"); log(t, "INTERLOCK: both incomers found closed - opened " + Q_); }
        t.put("as", as); t.put("tmr", Math.max(0, tmr));
        t.put("ltimer", as == 1 || as == 2 || as == 4 || as == 5);
        t.put("lgen", t.pv("gs") == 1);
        t.put("lfault", as == 6);
        t.text("status", status);
        t.text("timer", as == 6 ? cut(t.ptext("why"), 20) : testing ? "TEST " + t.pv("test") + "s" : t.pv("cd") > 0 ? "GEN COOL " + t.pv("cd") + "s" : "");
    }

    private static GridData.Net side(List<GridData.Net> nets, Set<Integer> around, int load, GridData.Node b) {
        if (b.breaker == 0) return load >= 0 && load < nets.size() ? nets.get(load) : null;
        for (int i : around) if (i != load && i >= 0 && i < nets.size()) return nets.get(i);
        return null;
    }

    private static String src(GridData.Net n) {
        if (n == null || n.level == null) return "DEAD";
        if (!n.problem.isEmpty()) return "DEAD";
        return Elec.si(n.level.volts * n.puSource, "V") + (n.level.ac ? String.format(Locale.ROOT, " %.1fHz", n.level.hz * n.hzPu) : "");
    }

    private static int srcVolts(GridData.Net n) { return n == null || n.level == null || !n.problem.isEmpty() ? 0 : (int) Math.round(n.puSource * 100); }

    private static void log(TileGrid t, String s) { History.log(t.getWorld(), t.getPos(), "ATS", s); t.text("last", cut(s, 30)); }

    private static void open(TileGrid t, long b, String why) {
        GridData.Node n = GridData.get(t.getWorld()).nodes().get(b);
        if (n == null || n.breaker != 0) return;
        TileGrid g = tile(t.getWorld(), BlockPos.fromLong(b));
        if (g != null) g.remote("open", why);
    }

    private static String close(TileGrid t, long b, String why) {
        TileGrid g = tile(t.getWorld(), BlockPos.fromLong(b));
        if (g == null) return "breaker chunk not loaded";
        GridData.Node n = GridData.get(t.getWorld()).nodes().get(b);
        if (n != null && n.breaker == 0) return "ok";
        if (n != null && n.breaker == 2) g.remote("reset", why);
        return g.remote("close", why);
    }

    /** start / stop the gensets on a source's side (MAN mode sets; AUTO-mode sets look after themselves) */
    private static void gens(TileGrid t, GridData d, GridData.Net src, boolean start) {
        if (src == null) return;
        for (long p : src.nodes) {
            GridData.Node g = d.nodes().get(p);
            if (g == null || (g.kind != GridKind.DIESEL && g.kind != GridKind.TURBINE)) continue;
            TileGrid gt = tile(t.getWorld(), BlockPos.fromLong(p));
            if (gt == null) continue;
            gt.remote(start ? "start" : "stop", "ATS");
        }
    }

    // ---- black start -------------------------------------------------------------------------------------------
    /** quest hook: Black Start Panel -> the player who pressed START (memory only) */
    static final java.util.Map<Long, java.util.UUID> BLACK_START_BY = new java.util.HashMap<>();
    static final String[] STAGES = {"IDLE", "1 BLACK-START GENSETS", "2 AUXILIARIES", "3 TRANSFORMERS", "4 FEEDERS", "5 TRACTION", "COMPLETE"};

    /** which restoration stage closing this open breaker belongs to (2..5), or 0 = not now (both sides dead / both live) */
    static int classify(GridData d, long b) {
        GridData.Node bn = d.nodes().get(b);
        int over = bn.get("bstage", 0);
        if (over == 5) return 0;
        List<GridData.Net> nets = d.nets();
        GridData.Net live = null, dead = null;
        for (int i : around(d, b)) {
            if (i < 0 || i >= nets.size()) continue;
            GridData.Net n = nets.get(i);
            if (n.problem.isEmpty() && n.level != null) live = n; else dead = n;
        }
        if (live == null || dead == null) return 0;
        if (over >= 1 && over <= 4) return over + 1;
        boolean feeder = false, traction = false, motor = false;
        for (long p : dead.nodes) { GridData.Node x = d.nodes().get(p); if (x == null) continue; if (x.kind == GridKind.FEEDER) feeder = true; if (x.kind == GridKind.MOTOR || x.kind == GridKind.UPS) motor = true; }
        for (long c : dead.outs) { GridData.Node x = d.nodes().get(c); if (x != null && (x.kind == GridKind.RECTIFIER || x.kind == GridKind.TRACTION)) traction = true; }
        if (feeder || traction) return 5;
        if (!dead.outs.isEmpty()) return 3;
        if (motor || live.level.volts < 1000) return 2;
        return 4;
    }

    private static void restore(TileGrid t, GridData d) {
        World w = t.getWorld();
        int stage = t.pv("stage");
        // grid dead? (no live network with a supply)
        boolean anyLive = false;
        double worstLoad = 0, minHz = 99;
        for (GridData.Net n : d.nets()) if (n.problem.isEmpty() && n.level != null && !GridRemote.controlOnly(d, n)) anyLive = true;
        for (GridData.Node g : d.nodes().values())
            if ((g.kind == GridKind.DIESEL || g.kind == GridKind.TURBINE) && g.feeding()) { worstLoad = Math.max(worstLoad, g.genLoad); minHz = Math.min(minHz, g.speed * 60); }
        t.put("ldead", !anyLive);
        t.text("freq", minHz < 99 ? String.format(Locale.ROOT, "%.2f Hz", minHz) : "-");
        t.text("gen", minHz < 99 ? String.format(Locale.ROOT, "GEN %.0f%%", worstLoad * 100) : "GEN -");
        t.put("gload", (int) Math.round(worstLoad * 100));
        for (int i = 1; i <= 5; i++) t.put("s" + i, stage > i || stage == 6 ? 2 : stage == i ? (t.pv("ready") == 1 ? 1 : 3) : 0);   // annunciator windows: steady done, slow flash running, fast = waiting for NEXT
        t.put("lhold", t.pv("hold") == 1);
        t.put("ldone", stage == 6);
        t.text("stage", STAGES[Math.max(0, Math.min(6, stage))]);
        if (stage <= 0 || stage >= 6 || t.pv("hold") == 1 || t.pv("mode") == 0) return;
        if (t.pv("ready") == 1) { t.text("msg", "PRESS NEXT TO CONFIRM"); return; }
        int wait = t.pv("wait");
        if (wait > 0) { t.put("wait", wait - 1); return; }
        double fmin = t.settings().num("fmin") / 10.0, gmax = t.settings().num("gmax") / 100.0;
        if (stage == 1) {
            boolean feeding = false, any = false;
            for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
                GridData.Node g = e.getValue();
                if (g.kind != GridKind.DIESEL && g.kind != GridKind.TURBINE) continue;
                any = true;
                TileGrid gt = tile(w, BlockPos.fromLong(e.getKey()));
                if (gt == null) continue;
                if (g.feeding() && g.speed > fmin / 60) feeding = true;
                else if (!gt.isRunning()) gt.remote("start", "black start");
                else if (!g.gcb) gt.remote("gcbclose", "black start");
            }
            for (GridData.Net n : d.nets()) if (n.problem.isEmpty() && n.level != null && n.utility) feeding = true;   // the utility came back: nothing to black-start
            if (!any && !feeding) { t.put("lfault", 1); t.text("msg", "NO BLACK-START GENSET"); return; }
            t.put("lfault", 0);
            t.text("msg", feeding ? "GENSETS ON THE BUS" : "CRANKING / SYNCHRONISING");
            if (feeding) stageDone(t, stage);
            return;
        }
        if (minHz < fmin && minHz < 99) { t.text("msg", String.format(Locale.ROOT, "WAIT: %.2f Hz < %.1f", minHz, fmin)); return; }
        if (worstLoad > gmax) { t.text("msg", String.format(Locale.ROOT, "WAIT: GEN %.0f%% > %.0f%%", worstLoad * 100, gmax * 100)); return; }
        // close the next breaker of this stage (or an earlier one that has just become possible), one every 2 s
        long best = Long.MIN_VALUE; int bestStage = 99;
        for (Map.Entry<Long, GridData.Node> e : d.nodes().entrySet()) {
            GridData.Node b = e.getValue();
            if (b.kind != GridKind.BREAKER || b.breaker == 0) continue;
            if (t.skip().contains(e.getKey())) continue;
            int c = classify(d, e.getKey());
            if (c >= 2 && c <= stage && c < bestStage) { bestStage = c; best = e.getKey(); }
        }
        if (best == Long.MIN_VALUE) { stageDone(t, stage); return; }
        String r = close(t, best, "black start stage " + bestStage);
        if (r.startsWith("ok")) { History.log(w, t.getPos(), "RESTORE", "stage " + bestStage + ": closed breaker " + Protection.at(best)); t.text("msg", "CLOSED " + Protection.at(best)); }
        else { t.skip().add(best); t.text("msg", cut("SKIP " + Protection.at(best) + ": " + r, 26)); History.log(w, t.getPos(), "RESTORE", "skipped breaker " + Protection.at(best) + ": " + r); }
        t.put("wait", 2);
    }

    private static void stageDone(TileGrid t, int stage) {
        if (t.pv("settle") == 0) { t.put("settle", t.settings().num("settle")); t.text("msg", "STAGE " + stage + " DONE - SETTLING"); return; }
        t.put("settle", t.pv("settle") - 1);
        if (t.pv("settle") > 0) { t.text("msg", "SETTLING " + t.pv("settle") + "s"); return; }
        History.log(t.getWorld(), t.getPos(), "RESTORE", "stage " + STAGES[stage] + " complete");
        if (stage >= 5) {
            t.put("stage", 6); t.text("msg", "GRID RESTORED"); t.skip().clear();
            GridTicker.alarm(t.getWorld(), t.getPos().toLong(), "§a✔ Black start complete - the grid is restored, traction last");
            java.util.UUID by = BLACK_START_BY.remove(t.getPos().toLong());
            if (by != null) com.dogpound.railmap.api.QuestEvents.fireFor(t.getWorld(), java.util.Collections.singleton(by), t.getPos(), "black_start", "stages", 5);
            else com.dogpound.railmap.api.QuestEvents.fire(t.getWorld(), null, t.getPos(), "black_start", "stages", 5);
            return;
        }
        if (t.pv("mode") == 1) { t.put("ready", 1); t.text("msg", "STAGE " + stage + " DONE - PRESS NEXT"); }
        else t.put("stage", stage + 1);
    }

    // ---- readouts for computers, the console and right-clicks ------------------------------------------------------
    static void info(TileGrid t, GridData.Node n, Map<String, Object> m) {
        switch (t.kind()) {
            case IED:
                m.put("inService", n.st("test") == 0); m.put("auxOk", n.st("aux") == 1); m.put("ia", n.st("ia")); m.put("pickupA", n.st("is"));
                m.put("voltsPct", n.st("v") / 10.0); m.put("hz", n.st("hz") / 100.0); m.put("tripped", n.st("ltrip") == 1);
                break;
            case UPS:
                m.put("onBattery", n.st("onbat") == 1); m.put("charge", Math.round(Ups.soc(n) * 100)); m.put("health", Math.round(Ups.health(n) * 100));
                m.put("loadW", n.st("lw")); m.put("devices", n.st("dev")); m.put("autonomy", Ups.autonomy(n)); m.put("outputOn", n.dcOn && !n.lvd);
                break;
            case PQA:
                m.put("voltsPct", n.st("v") / 10.0); m.put("hz", n.st("hz") / 100.0); m.put("vuf", n.st("vuf") / 100.0); m.put("thdV", n.st("thdv") / 100.0);
                m.put("thdI", n.st("thdi") / 10.0); m.put("pf", n.st("pf") / 100.0); m.put("sags", n.st("nsag")); m.put("swells", n.st("nswl")); m.put("interruptions", n.st("nint"));
                break;
            case ATS: m.put("state", t.ptext("status")); m.put("mode", new String[]{"off", "auto", "manual A", "manual B"}[Math.max(0, Math.min(3, t.pv("mode")))]); break;
            case RESTORE: m.put("stage", STAGES[Math.max(0, Math.min(6, t.pv("stage")))]); m.put("message", t.ptext("msg")); break;
            case TRANSFORMER: case TRACTION: case RECTIFIER: case INVERTER: case DCDC:
                m.put("ambientC", Math.round(n.ambient)); m.put("derate", Math.round(n.derate * 100)); m.put("fansFailed", n.fanFail); break;
            case BREAKER: m.put("operations", n.ops); m.put("wear", Math.round(n.wear * 100)); m.put("lastTrip", n.lastTrip); m.put("priority", new String[]{"critical", "essential", "non-essential"}[Math.max(0, Math.min(2, n.get("prio", 2)))]); break;
            default: break;
        }
        if (n.insul < 1 || n.efault != 0) { m.put("insulation", Math.round(n.insul * 100)); m.put("earthFault", n.efault != 0); }
    }

    static String status(TileGrid t, GridData.Node n) {
        switch (t.kind()) {
            case IED: return "§7Protection IED · " + (n.st("aux") == 0 ? "§cDEAD (no 12 V aux)" : n.st("test") == 1 ? "§eTEST - trips blocked" : "§ain service") + "§7 · IA " + n.st("ia") + " A · I> " + n.st("is") + " A · laptop: right-click for a secondary-injection test";
            case UPS: return String.format(Locale.ROOT, "§7UPS · %s · battery %d%% (health %d%%) · %d W to %d devices · %s", n.st("onbat") == 1 ? "§eON BATTERY§7" : "§aon mains§7",
                    Math.round(Ups.soc(n) * 100), Math.round(Ups.health(n) * 100), n.st("lw"), n.st("dev"), Ups.autonomy(n));
            case ATS: return "§7Transfer Switch · " + t.ptext("status") + " · " + t.ptext("va") + " · " + t.ptext("vb");
            case RESTORE: return "§7Black Start Panel · " + t.ptext("stage") + " · " + t.ptext("msg");
            case PQA: return String.format(Locale.ROOT, "§7PQ Analyzer · U %.1f%% · F %.2f Hz · VUF %.2f%% · THDv %.2f%% · PF %.2f", n.st("v") / 10.0, n.st("hz") / 100.0, n.st("vuf") / 100.0, n.st("thdv") / 100.0, n.st("pf") / 100.0);
            default: return null;
        }
    }
}
