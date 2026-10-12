package com.dogpound.railmap.safety;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.api.QuestEvents;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.depot.Diagnostics;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Fault Diagnosis Terminal (her ChatGPT batch-3 #48): a maintainer's rugged laptop and signalling test set on a stand.
 * The screen lists the live faults on 12 V signalling gear (Diagnosis.jobs); PREV / NEXT pick one, TAKE opens a job
 * for whoever pressed. TRAIN has the Fault Lab (in TEST MODE) inject a hidden fault on gear in its zone instead. Then
 * the procedure: 1 MEASURE with the multimeter at the gear (and at a 12 V source for a supply / wiring fault) - every
 * reading is real and lands here; 2 DIAGNOSE with the cause buttons; 3 REPAIR in the world (or FIT PART at the gear
 * for a lab fault); 4 VERIFY runs a self-test and the job closes with its score and time. The laptop runs off its
 * 230 V AC charger (mains) and the 12 V test set; the tool case and the drawer are real storage.
 */
public class TileDiagTerminal extends DepotTile {
    private int jcur;
    private Diagnosis.Job job;
    private UUID tech;
    private String techName = "";
    /** 0 none, 1 measure, 2 diagnose, 3 repair, 4 verify, 5 done */
    private int step;
    private int score, wrong;
    private long started;
    private final Set<String> got = new HashSet<>();
    private final List<String> log = new ArrayList<>();
    private final List<String> done = new ArrayList<>();
    private double lastV;
    private String msg = "";

    public TileDiagTerminal() {
        super(new String[]{"case", "drw"}, new String[]{"Tool case", "Stand drawer"}, new int[]{9, 9});
    }

    @Override public String wiredName() { return "Fault diagnosis terminal"; }
    @Override public String gearState() { return !mains.ok() ? "NO MAINS - " + mains.what : job == null ? "no job open" : "job " + job.name + " step " + STEP[step] + " score " + score; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return 4; }

    static final String[] STEP = {"-", "MEASURE", "DIAGNOSE", "REPAIR", "VERIFY", "DONE"};

    private boolean on() { return powered() && mains.ok(); }

    @Override public int controlValue(String id) {
        if (id.equals("mains")) return powered() && mains.ok() ? 1 : 0;
        if (!id.startsWith("s_") && !id.equals("case") && !id.equals("drw") && !mains.ok()) return 0;
        return super.controlValue(id);
    }

    @Override public String ptext(String key) { return on() ? super.ptext(key) : ""; }

    public Diagnosis.Job job() { return job; }
    public int step() { return step; }
    public int score() { return score; }
    public String techName() { return techName; }
    public long started() { return started; }
    public List<String> log() { return log; }

    /** a multimeter reading by `p` (Diagnosis.record): counts for this terminal's open job if p is its technician */
    void measured(EntityPlayer p, BlockPos at, double volts, boolean source) {
        if (job == null || step != 1 || tech == null || !tech.equals(p.getUniqueID())) return;
        lastV = volts;
        BlockPos jp = BlockPos.fromLong(job.pos);
        String what;
        if (!source && (at.equals(jp) || job.cause == Diagnosis.Cause.POINTS && FaultLab.switchOf(world, at).equals(jp))) { got.add("gear"); what = "at the faulty gear"; }
        else if (source && at.distanceSq(jp) <= 64 * 64) { got.add("source"); what = "at the 12 V source"; }
        else { score = Math.max(0, score - 5); what = "somewhere that doesn't help (-5)"; }
        note(String.format(Locale.ROOT, "MEASURED %.2f V %s", Math.max(0, volts), what));
        if (got.containsAll(Diagnosis.needs(job.cause))) { step = 2; note("measurements complete - DIAGNOSE"); }
        controlsChanged();
    }

    private void note(String s) {
        log.add(0, Recorder.clock(world, world.getTotalWorldTime()) + " " + s);
        while (log.size() > 20) log.remove(log.size() - 1);
        msg = s;
    }

    private List<Diagnosis.Job> jobs() { return Diagnosis.jobs(world, pos); }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (!mains.ok()) return "§cThe laptop is flat - no mains to its charger (" + mains.what + ")";
        String r;
        switch (id) {
            case "prev": jcur--; return "";
            case "next": jcur++; return "";
            case "take": {
                if (job != null) return "§cA job is open (" + job.name + ") - finish or ABORT it";
                List<Diagnosis.Job> l = jobs();
                if (l.isEmpty()) return "§aNo faults on the signalling gear";
                start(l.get(Math.floorMod(jcur, l.size())), who);
                r = "§eJob opened: " + Diagnosis.symptom(job) + " - MEASURE with the multimeter";
                break;
            }
            case "train": {
                if (job != null) return "§cA job is open - finish or ABORT it";
                String t = train(who);
                if (t != null) return t;
                r = "§6TRAINING fault injected - find it: " + Diagnosis.symptom(job);
                break;
            }
            case "abort":
                if (job == null) return "§7No job open";
                note("job abandoned");
                if (job.hidden && job.lab != null) FaultLab.clear(world, job.lab, BlockPos.fromLong(job.pos));
                job = null; step = 0;
                return "§eJob abandoned";
            case "repair": {
                if (job == null || step != 3) return "§7FIT PART is for after the diagnosis";
                if (job.lab == null) return "§7This one is repaired in the world: " + howTo(job.cause);
                if (who != null && who.getDistanceSq(BlockPos.fromLong(job.pos)) > 8 * 8) return "§cGo to the gear (within 8 m) to fit the part - the terminal is a radio link";
                r = Diagnosis.fitPart(world, job, who);
                note("part fitted");
                break;
            }
            case "verify": {
                if (job == null || step < 3) return "§7Nothing to verify yet";
                SelfTest.run(world, "diagnosis verify, " + (who == null ? "console" : who.getName()));
                SafetyData.Gear g = SafetyData.get(world).gear.get(job.pos);
                if (!Diagnosis.fixed(world, job) || g != null && g.state == 2) {
                    score = Math.max(0, score - 10);
                    note("VERIFY FAILED - still faulty (-10)");
                    return "§cStill faulty - " + howTo(job.cause);
                }
                finish(who);
                r = "§aVERIFIED - job closed, score " + score;
                break;
            }
            case "print": {
                if (who == null) return "§7Collect the report at the terminal";
                List<String> l = new ArrayList<>();
                l.add("FAULT DIAGNOSIS REPORT");
                if (job != null) { l.add("OPEN: " + job.name + " " + Geo.xz(job.pos)); l.add("Step " + STEP[step] + " score " + score); }
                l.addAll(log);
                l.add("--- closed jobs ---");
                l.addAll(done);
                who.inventory.addItemStackToInventory(Diagnostics.book("Diagnosis report", "Fault terminal", l));
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.6f, 1f);
                r = "§aReport printed";
                break;
            }
            default:
                if (id.startsWith("d_")) { r = diagnose(id.substring(2)); break; }
                return null;
        }
        msg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    static final String[] CAUSE_IDS = {"sup", "wir", "brn", "lmp", "det", "rad", "pts"};

    private String diagnose(String c) {
        if (job == null) return "§7No job open";
        if (step == 1) return "§cMeasure first: " + String.join(" + ", Diagnosis.needs(job.cause)) + " (multimeter) - you can't sign a diagnosis you haven't proved";
        if (step != 2) return "§7Already diagnosed";
        int i = Arrays.asList(CAUSE_IDS).indexOf(c);
        if (i < 0) return null;
        Diagnosis.Cause said = Diagnosis.Cause.values()[i];
        if (said != job.cause) {
            wrong++;
            score = Math.max(0, score - 20);
            note("diagnosed " + said.label + " - WRONG (-20)");
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_ERROR, SoundCategory.BLOCKS, 0.6f, 1f);
            return "§cNot the cause - look at your readings again (-20)";
        }
        step = 3;
        note("diagnosed " + said.label + " - correct");
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1f);
        return "§aCorrect: " + said.label + " - REPAIR: " + howTo(said);
    }

    static String howTo(Diagnosis.Cause c) {
        switch (c) {
            case BURNT: return "screwdriver on the gear (new board), then VERIFY";
            case SUPPLY: return "restore the 12 V supply (charger / DC-DC converter / battery), then VERIFY";
            case WIRING: return "lay the 12 V power pair to the gear, then VERIFY";
            default: return "FIT PART at the gear, then VERIFY";
        }
    }

    private void start(Diagnosis.Job j, EntityPlayer who) {
        job = j;
        tech = who == null ? null : who.getUniqueID();
        techName = who == null ? "console" : who.getName();
        step = 1; score = 100; wrong = 0; got.clear(); lastV = 0;
        started = world.getTotalWorldTime();
        log.clear();
        note("job " + j.name + " " + Geo.xz(j.pos) + " taken by " + techName + (j.hidden ? " (training)" : " - " + j.source));
    }

    /** a hidden training fault from the Fault Lab, on gear in its test zone */
    private String train(EntityPlayer who) {
        SafetyData d = SafetyData.get(world);
        if (!d.testMode || d.labPos == 0) return "§cTraining needs a Fault Lab console in TEST MODE (it injects the fault)";
        BlockPos lab = BlockPos.fromLong(d.labPos);
        List<Object[]> cand = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(world.loadedTileEntityList)) {
            if (t.isInvalid() || t.getPos().distanceSq(lab) > (double) d.labRadius * d.labRadius) continue;
            if (t instanceof TileSignalMast) cand.add(new Object[]{t, FaultLab.Kind.LAMP});
            else if (t instanceof TileTrackCircuit tc && !tc.isJoint()) cand.add(new Object[]{t, FaultLab.Kind.TC_DROP});
            else if (t instanceof TileRadioMast) cand.add(new Object[]{t, FaultLab.Kind.RADIO_CUT});
            else if (t instanceof TileAxleHead) cand.add(new Object[]{t, FaultLab.Kind.AXLE_HEAD});
        }
        if (cand.isEmpty()) return "§cNo signals, track circuits, radio masts or axle heads in the lab's test zone";
        Object[] c = cand.get(world.rand.nextInt(cand.size()));
        TileEntity t = (TileEntity) c[0];
        FaultLab.Kind k = (FaultLab.Kind) c[1];
        String r = FaultLab.inject(world, k, t.getPos(), "training (" + (who == null ? "console" : who.getName()) + ")");
        if (!r.startsWith("§6")) return r;
        Diagnosis.Job j = Diagnosis.job(world, t.getPos().toLong(), Diagnosis.ofLab(k), "training");
        j.lab = k;
        j.hidden = true;
        start(j, who);
        return null;
    }

    private void finish(EntityPlayer who) {
        long secs = (world.getTotalWorldTime() - started) / 20;
        step = 5;
        String line = Recorder.clock(world, world.getTotalWorldTime()) + " " + job.name + " " + Geo.xz(job.pos) + ": " + job.cause.label + " · score " + score + " · " + secs + " s · " + techName + (job.hidden ? " (training)" : "");
        done.add(0, line);
        while (done.size() > 20) done.remove(done.size() - 1);
        note("VERIFIED - closed in " + secs + " s, score " + score);
        Recorder.event(world, "FAULT DIAGNOSED " + line);
        SafetyData.get(world).history(world, BlockPos.fromLong(job.pos), null, null, "diagnosed + repaired: " + job.cause.label + " by " + techName + " (score " + score + ")");
        QuestEvents.fire(world, who, BlockPos.fromLong(job.pos), "fault_diagnosed", "cause", job.cause.name(), "score", score, "seconds", secs, "training", job.hidden);
        job = null;
    }

    @Override
    protected void work() {
        if (age % 10 != 6) return;
        if (job != null && step == 3 && job.lab == null && Diagnosis.fixed(world, job)) { step = 4; note("repair seen - VERIFY"); }
        if (job != null && step == 3 && job.lab != null && Diagnosis.fixed(world, job)) { step = 4; }
        put("pwr", powered());
        put("charge", mains.ok());
        for (int i = 1; i <= 4; i++) put("st" + i, job != null && step == i);
        put("done", job == null && !done.isEmpty());
        put("err", wrong > 0 && job != null);
        put("meter", (int) Math.round(Math.max(0, lastV) * 10));
        put("score", job == null ? 0 : score);
        List<Diagnosis.Job> l = jobs();
        if (job == null) {
            text("hdr", l.isEmpty() ? "NO FAULTS ON SIGNALLING GEAR" : "FAULTS " + l.size() + "  JOB " + (Math.floorMod(jcur, l.size()) + 1));
            for (int i = 0; i < 5; i++) {
                if (l.isEmpty()) { text("l" + (i + 1), ""); continue; }
                int at = Math.floorMod(jcur, l.size()) + i;
                Diagnosis.Job j = at < l.size() ? l.get(at) : null;
                text("l" + (i + 1), j == null ? "" : (i == 0 ? "> " : "  ") + cut(j.name, 18) + " " + Geo.xz(j.pos) + " " + cut(j.source, 12));
            }
            text("l6", l.isEmpty() ? "" : cut(Diagnosis.symptom(l.get(Math.floorMod(jcur, l.size()))), 40));
            text("time", "");
        } else {
            text("hdr", (job.hidden ? "TRAINING " : "JOB ") + cut(job.name, 20) + " " + Geo.xz(job.pos));
            text("l1", cut(Diagnosis.symptom(job), 40));
            text("l2", "STEP " + step + " " + STEP[step] + "   TECH " + cut(techName, 12));
            text("l3", step == 1 ? "MEASURE: " + String.join(" + ", Diagnosis.needs(job.cause)).toUpperCase(Locale.ROOT) + " - GOT " + got.size()
                    : step == 2 ? "PRESS THE CAUSE" : step >= 3 ? cut(howTo(job.cause), 40) : "");
            text("l4", log.size() > 0 ? cut(log.get(0), 40) : "");
            text("l5", log.size() > 1 ? cut(log.get(1), 40) : "");
            text("l6", log.size() > 2 ? cut(log.get(2), 40) : "");
            text("time", ((world.getTotalWorldTime() - started) / 20) + " S");
        }
        text("msg", cut(msg, 40));
        text("v", String.format(Locale.ROOT, "%.2f V", Math.max(0, lastV)));
    }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setInteger("jc", jcur);
        if (job != null) {
            NBTTagCompound j = new NBTTagCompound();
            j.setLong("p", job.pos); j.setString("n", job.name); j.setString("k", job.kind); j.setString("s", job.source);
            j.setString("c", job.cause.name()); if (job.lab != null) j.setString("l", job.lab.name()); j.setBoolean("h", job.hidden);
            t.setTag("job", j);
        }
        if (tech != null) t.setUniqueId("tech", tech);
        t.setString("techN", techName);
        t.setInteger("step", step); t.setInteger("score", score); t.setInteger("wrong", wrong); t.setLong("st", started);
        t.setString("got", String.join(",", got));
        NBTTagList a = new NBTTagList(), b = new NBTTagList();
        for (String s : log) a.appendTag(new NBTTagString(s));
        for (String s : done) b.appendTag(new NBTTagString(s));
        t.setTag("log", a); t.setTag("done", b);
        t.setString("msg", msg);
        t.setDouble("lv", lastV);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        jcur = t.getInteger("jc");
        job = null;
        if (t.hasKey("job")) {
            NBTTagCompound j = t.getCompoundTag("job");
            try {
                Diagnosis.Job x = new Diagnosis.Job();
                x.pos = j.getLong("p"); x.name = j.getString("n"); x.kind = j.getString("k"); x.source = j.getString("s");
                x.cause = Diagnosis.Cause.valueOf(j.getString("c")); x.lab = j.hasKey("l") ? FaultLab.Kind.byName(j.getString("l")) : null; x.hidden = j.getBoolean("h");
                job = x;
            } catch (RuntimeException ignored) { }
        }
        tech = t.hasUniqueId("tech") ? t.getUniqueId("tech") : null;
        techName = t.getString("techN");
        step = t.getInteger("step"); score = t.getInteger("score"); wrong = t.getInteger("wrong"); started = t.getLong("st");
        got.clear();
        for (String s : t.getString("got").split(",")) if (!s.isEmpty()) got.add(s);
        log.clear(); done.clear();
        NBTTagList a = t.getTagList("log", 8), b = t.getTagList("done", 8);
        for (int i = 0; i < a.tagCount(); i++) log.add(a.getStringTagAt(i));
        for (int i = 0; i < b.tagCount(); i++) done.add(b.getStringTagAt(i));
        msg = t.getString("msg");
        lastV = t.getDouble("lv");
    }
}
