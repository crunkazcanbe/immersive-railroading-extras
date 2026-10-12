package com.dogpound.railmap.safety;

import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.depot.Diagnostics;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Driver Competency Assessor (her ChatGPT batch-3 #46): a simulator desk with the assessor's screen. IDENT signs in
 * whoever presses (their competence record: grade, career rank, hours per traction, signals known, incidents, what
 * the next grade needs). START TEST sets a 10-question paper (rule book + live questions about this railway's real
 * signals, signs and fail-safe modes); A-D answer, the right / wrong lamps show the result, 80 % passes (and lifts a
 * suspension). ROUTE QUAL grants the selected line to the driver on the crew desk when they know 60 % of its signals;
 * PASS OUT passes them out on the selected traction once they have the hours. PRINT gives the certificate as a book.
 * The PC runs from 230 V AC mains (a grid cable to the desk) as well as the 12 V desk supply: no mains = dark.
 * Two desk drawers and the document cabinet are real storage.
 */
public class TileDriverAssessor extends DepotTile {
    private UUID cand;
    private String candName = "";
    private List<Competency.Q> paper;
    private int qi, score, lastAns = -1;
    private long testStart;
    private int lcur;
    private String msg = "";

    public TileDriverAssessor() {
        super(new String[]{"dr1", "dr2", "cab"}, new String[]{"Desk drawer", "Desk drawer", "Document cabinet"}, new int[]{9, 9, 18});
    }

    @Override public String wiredName() { return "Driver competency assessor"; }
    @Override public String gearState() { return !mains.ok() ? "NO MAINS - " + mains.what : paper != null ? "testing " + candName + " Q" + (qi + 1) + "/10" : cand == null ? "idle" : candName + " signed in"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return paper != null ? 8 : 5; }

    private boolean on() { return powered() && mains.ok(); }

    @Override public int controlValue(String id) {
        if (id.equals("mains")) return powered() && mains.ok() ? 1 : 0;
        if (!id.startsWith("k_") && !id.startsWith("s_") && !id.startsWith("dr") && !id.equals("cab") && !mains.ok()) return 0;
        return super.controlValue(id);
    }

    @Override public String ptext(String key) { return on() ? super.ptext(key) : ""; }

    private String line() {
        List<String> l = new ArrayList<>(RailwayData.get(world).lines().keySet());
        if (l.isEmpty()) return "";
        Collections.sort(l);
        lcur = Math.floorMod(lcur, l.size());
        return l.get(lcur);
    }

    private Competency.Rec rec() { return cand == null ? null : Competency.get(world).recs.get(cand); }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("s_trac")) { ctl.set("s_trac", (ctl.get("s_trac") + 1) % 3); return "§eTraction: " + Competency.TRACTION[ctl.get("s_trac")]; }
        return null;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (!mains.ok()) return "§cThe assessor PC is dark - no mains (" + mains.what + ")";
        Competency c = Competency.get(world);
        String r;
        switch (id) {
            case "ident":
                if (who == null) return "§7Sign in at the desk";
                if (paper != null && !who.getUniqueID().equals(cand)) return "§cA test is running for " + candName;
                cand = who.getUniqueID(); candName = who.getName();
                c.rec(who);
                c.markDirty();
                r = "§aSigned in: " + candName + " (" + Competency.gradeOf(world, rec()) + ")";
                break;
            case "start":
                if (cand == null) return "§cIDENT first";
                if (who != null && !who.getUniqueID().equals(cand)) return "§cThe candidate starts the test (" + candName + ")";
                paper = Competency.paper(world, world.getTotalWorldTime() ^ cand.getLeastSignificantBits());
                qi = 0; score = 0; lastAns = -1; testStart = world.getTotalWorldTime();
                r = "§eTest started - 10 questions, 80 % to pass";
                break;
            case "a": case "b": case "c": case "d": {
                if (paper == null) return "§7No test running - START TEST";
                if (who != null && !who.getUniqueID().equals(cand)) return "§cOnly " + candName + " answers";
                int a = id.charAt(0) - 'a';
                Competency.Q q = paper.get(qi);
                boolean right = a == q.correct;
                if (right) score++;
                lastAns = right ? 1 : 0;
                world.playSound(null, pos, right ? com.dogpound.railmap.ModSounds.BEEP_OK : com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.6f, 1f);
                r = right ? "§aCorrect" : "§cWrong - the answer was " + (char) ('A' + q.correct) + ": " + q.ans[q.correct];
                qi++;
                if (qi >= paper.size()) {
                    int pct = score * 100 / paper.size();
                    r = r + " · " + Competency.finish(world, rec(), pct, who);
                    paper = null;
                }
                break;
            }
            case "abort":
                if (paper == null) return "§7No test running";
                paper = null;
                r = "§eTest abandoned (not recorded)";
                break;
            case "lprev": lcur--; r = "§7Line: " + line(); break;
            case "lnext": lcur++; r = "§7Line: " + line(); break;
            case "routeq":
                if (rec() == null) return "§cIDENT first";
                if (line().isEmpty()) return "§cNo lines on this railway yet";
                r = Competency.qualifyLine(world, rec(), line(), who);
                break;
            case "passout":
                if (rec() == null) return "§cIDENT first";
                r = Competency.passOut(world, rec(), ctl.get("s_trac"));
                break;
            case "print": {
                if (who == null) return "§7Collect the certificate at the desk";
                Competency.Rec x = rec();
                if (x == null) return "§cIDENT first";
                who.inventory.addItemStackToInventory(Diagnostics.book("Competence " + x.name, "Assessor", certificate(x)));
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                r = "§aCertificate printed";
                break;
            }
            default: return null;
        }
        msg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    private List<String> certificate(Competency.Rec x) {
        List<String> l = new ArrayList<>();
        l.add("DRIVER COMPETENCE");
        l.add(x.name);
        l.add("Grade: " + Competency.gradeOf(world, x));
        String rank = QuestEvents.rankOf(x.id);
        if (!rank.isEmpty()) l.add("Career rank: " + rank);
        l.add(String.format(Locale.ROOT, "Hours: D %.1f E %.1f S %.1f", x.hours[0], x.hours[1], x.hours[2]));
        l.add("Signals known: " + Competency.knownCount(world, x));
        l.add("Theory best " + x.theoryBest + "% (" + x.tests + " tests)");
        StringBuilder po = new StringBuilder();
        for (int i = 0; i < 3; i++) if ((x.passedOut & 1 << i) != 0) po.append(po.length() > 0 ? ", " : "").append(Competency.TRACTION[i]);
        l.add("Passed out: " + (po.length() == 0 ? "-" : po));
        l.add("Routes: " + (x.lines.isEmpty() ? "-" : String.join(", ", x.lines)));
        l.add("Incidents: SPAD " + x.spads + " TRIP " + x.trips + " AWS " + x.aws + " OVSP " + x.overspeed);
        for (String s : x.incidents.subList(0, Math.min(6, x.incidents.size()))) { String[] p = s.split("\\|", 3); l.add(com.dogpound.railmap.alarm.Recorder.clock(world, Long.parseLong(p[0])) + " " + p[1] + " " + (p.length > 2 ? p[2] : "")); }
        l.add(Competency.nextStep(world, x));
        return l;
    }

    @Override
    protected void work() {
        if (age % 10 != 4) return;
        Competency.Rec x = rec();
        boolean testing = paper != null;
        put("pwr", powered());
        put("testing", testing);
        put("pass", x != null && !testing && x.theoryLast >= 80 && x.tests > 0);
        put("fail", x != null && !testing && x.theoryLast < 80 && x.tests > 0);
        put("susp", x != null && x.suspended);
        put("right", lastAns == 1);
        put("wrong", lastAns == 0);
        put("score", testing ? score : x == null ? 0 : x.theoryBest);
        put("known", x == null ? 0 : Competency.knownCount(world, x));
        put("hrs", x == null ? 0 : (int) Math.round(x.total()));
        String rank = x == null ? "" : QuestEvents.rankOf(x.id);
        text("who", x == null ? "PRESS IDENT TO SIGN IN" : (x.name + " - " + Competency.gradeOf(world, x) + (rank.isEmpty() ? "" : " - " + rank)).toUpperCase(Locale.ROOT));
        if (testing) {
            Competency.Q q = paper.get(qi);
            String[] w = wrap(q.text, 34, 3);
            text("q1", w[0]); text("q2", w[1]); text("q3", w[2]);
            for (int i = 0; i < 4; i++) text("a" + (char) ('A' + i), (char) ('A' + i) + " " + cut(q.ans[i], 30));
            text("prog", "Q " + (qi + 1) + "/" + paper.size() + "   SCORE " + score + "   " + (world.getTotalWorldTime() - testStart) / 20 + " S");
        } else {
            text("q1", x == null ? "" : String.format(Locale.ROOT, "HOURS D %.1f E %.1f S %.1f", x.hours[0], x.hours[1], x.hours[2]));
            text("q2", x == null ? "" : "KNOWN " + Competency.knownCount(world, x) + " SIGNALS - THEORY " + x.theoryBest + "%");
            text("q3", x == null ? "" : "SPAD " + x.spads + " TRIP " + x.trips + " AWS " + x.aws + " OVSP " + x.overspeed);
            String[] n = wrap(x == null ? "" : Competency.nextStep(world, x), 32, 2);
            text("aA", n[0]); text("aB", n[1]);
            text("aC", x == null || x.lines.isEmpty() ? "" : "ROUTES " + cut(String.join(" ", x.lines), 26));
            text("aD", "");
            text("prog", x == null ? "" : "TESTS " + x.tests + "   LAST " + x.theoryLast + "%");
        }
        text("line", line().isEmpty() ? "NO LINES" : cut(line(), 14));
        text("msg", cut(msg, 40));
    }

    static String[] wrap(String s, int width, int lines) {
        String[] out = new String[lines];
        Arrays.fill(out, "");
        int li = 0;
        StringBuilder b = new StringBuilder();
        for (String word : s.split(" ")) {
            if (b.length() + word.length() + 1 > width && b.length() > 0) { if (li < lines) out[li++] = b.toString(); b.setLength(0); }
            if (b.length() > 0) b.append(' ');
            b.append(word);
        }
        if (b.length() > 0 && li < lines) out[li] = b.toString();
        return out;
    }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        if (cand != null) t.setUniqueId("cand", cand);
        t.setString("candN", candName);
        t.setInteger("lc", lcur);
        t.setString("msg", msg);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        cand = t.hasUniqueId("cand") ? t.getUniqueId("cand") : null;
        candName = t.getString("candN");
        lcur = t.getInteger("lc");
        msg = t.getString("msg");
    }
}
