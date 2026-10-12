package com.dogpound.railmap.grid;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.ctl.CtlConfig;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Microgrid Controller (her "next 48" list #21): lets a depot, station or factory ride through a grid failure on its
 * own generation and storage and come back safely. Place it touching the LOCAL plant; with the Signal Wrench pick the
 * intertie breaker (the point of interconnection to the utility, its key in REMOTE) and click the POI window, and pick
 * breakers of non-essential loads and click the SHED A / B / C windows (A goes first).
 * <ul>
 *   <li>GRID-TIED: watches the utility. On loss (dead, under/over voltage, frequency out) it ISLANDS: opens the intertie
 *       (anti-islanding the other way round), sheds A at once, starts every local genset and turns the batteries to
 *       DISCHARGE;</li>
 *   <li>ISLANDED: secondary frequency control (trims the gensets' governors back to nominal after their droop), load
 *       shedding in stages on under-frequency or generator overload and restoring in reverse once there is margin,
 *       voltage band alarms;</li>
 *   <li>RESYNC (AUTO, after the utility has been back {@link CtlConfig#microgridReturnSeconds}): a sync-check relay -
 *       slip &lt; 0.1 Hz, voltage within 5 %, phase within 10 degrees on the synchroscope - closes the intertie, then
 *       loads come back one stage at a time and the batteries go back to AUTO;</li>
 *   <li>controlled intertie: an import limit (250 / 500 / 1000 / 2000 kW) that sheds and discharges batteries to stay
 *       under it; ISLAND mode islands deliberately (planned: gensets run up and parallel first, then the intertie opens
 *       with no break); the red INTERTIE TRIP mushroom opens it and locks out.</li>
 * </ul>
 */
public class TileMicrogrid extends SafetyTile implements com.dogpound.railmap.block.IFurniture {
    private final com.dogpound.railmap.ctl.Drawers drawers = new com.dogpound.railmap.ctl.Drawers(this, new String[]{"dr1", "dr2"}, new String[]{"Switching log drawer", "Spares cupboard"}, new int[]{9, 18});
    @Override public net.minecraft.inventory.IInventory storage(int k) { return drawers.storage(k); }
    @Override public String storeName(int k) { return drawers.name(k); }
    @Override public int storeSize(int k) { return drawers.size(k); }
    @Override public void dropAll() { drawers.dropAll(this); }

    public static final String[] MODES = {"OFF", "TIED", "AUTO", "ISLAND"};
    public static final int[] LIMIT = {0, 250, 500, 1000, 2000};
    public enum St { TIED, ISLANDED, RESYNC, BLACKOUT, IDLE }

    private long poi;
    private final List<List<Long>> tiers = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private St state = St.IDLE;
    private int shed;                         // tiers shed now 0..3
    private int utilOk, margin, overLimit, underFor;
    private double phase, slip, poiKw, hz = 1, volt = 1;
    private boolean lockout;
    private String why = "";
    private final List<String> log = new ArrayList<>();

    public String id() { return "MG" + Math.floorMod(pos.getX() * 11 + pos.getZ() * 29, 100); }
    @Override public String wiredName() { return "Microgrid controller " + id(); }
    int mode() { return Math.min(3, ctl.get("s_mode")); }
    public St state() { return state; }
    public List<String> log() { return log; }

    @Override public String gearState() { return !powered() ? "dead - no 12 V" : state + (shed > 0 ? " · shed " + "ABC".substring(0, shed) : "") + String.format(Locale.ROOT, " · %.2f Hz · %.1f%% V · POI %.0f kW", hz * nominalHz(), volt * 100, poiKw) + (why.isEmpty() ? "" : " · " + why); }
    @Override public int wiredLamp() { return !powered() || state == St.BLACKOUT ? 3 : state == St.TIED || state == St.IDLE ? 1 : 2; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("state", state.name()); m.put("mode", MODES[mode()]); m.put("shedStages", shed); m.put("hz", Math.round(hz * nominalHz() * 100) / 100.0); m.put("poiKw", Math.round(poiKw)); m.put("phase", Math.round(phase)); m.put("intertie", poi == 0 ? "not linked" : xyz(poi)); }

    private static String xyz(long p) { BlockPos b = BlockPos.fromLong(p); return b.getX() + "," + b.getY() + "," + b.getZ(); }
    private double nominalHz() { GridData.Net n = local(); return n != null && n.level != null && n.level.ac ? n.level.hz : 60; }
    private GridData.Net local() { return PowerQuality.measured(GridData.get(world), pos.toLong()); }

    /** the network on the far side of the open intertie (the utility side), or null */
    private GridData.Net utilitySide(GridData d, GridData.Net local) {
        if (poi == 0) return null;
        BlockPos b = BlockPos.fromLong(poi);
        for (EnumFacing f : EnumFacing.values()) {
            GridData.Net n = d.netOf(b.offset(f));
            if (n != null && (local == null || n.id != local.id)) return n;
        }
        return null;
    }

    private boolean healthy(GridData.Net n) {
        return n != null && n.level != null && n.problem.isEmpty() && n.utility && n.puSource > 0.9 && n.puSource < 1.1 && (!n.level.ac || Math.abs(n.hzPu - 1) < 0.01);
    }

    private GridData.Node poiNode(GridData d) { return poi == 0 ? null : d.nodes().get(poi); }
    private boolean poiClosed(GridData d) { GridData.Node n = poiNode(d); return n != null && n.breaker == 0; }

    private String remote(long at, String cmd) {
        BlockPos b = BlockPos.fromLong(at);
        if (!world.isBlockLoaded(b) || !(world.getTileEntity(b) instanceof TileGrid g)) return "not loaded";
        return g.remote(cmd, "microgrid " + id());
    }

    private void note(String s) {
        log.add(0, com.dogpound.railmap.alarm.Recorder.clock(world, world.getTotalWorldTime()) + " " + s);
        while (log.size() > 24) log.remove(log.size() - 1);
        Recorder.event(world, "MICROGRID " + id() + ": " + s);
    }

    @Override
    protected void serverTick() {
        if (age % 20 != 9) return;
        GridData d = GridData.get(world);
        GridData.Net loc = local();
        hz = loc != null && loc.level != null && loc.level.ac && loc.problem.isEmpty() ? loc.hzPu : 0;
        volt = loc != null && loc.problem.isEmpty() ? loc.puSource : 0;
        GridData.Node pn = poiNode(d);
        poiKw = pn == null || loc == null || loc.level == null ? 0 : pn.amps * loc.level.volts * (loc.level.phases == 3 ? Math.sqrt(3) : 1) * loc.pf / 1000.0 * (poiClosed(d) ? 1 : 0);
        if (!powered() || mode() == 0) { state = St.IDLE; live(d, loc); return; }
        boolean closed = poiClosed(d);
        GridData.Net util = closed ? loc : utilitySide(d, loc);
        boolean uOk = healthy(util);
        utilOk = uOk ? utilOk + 1 : 0;
        if (loc != null && !loc.problem.isEmpty() && !closed) { if (state != St.BLACKOUT) { state = St.BLACKOUT; note("BLACKOUT - local network dead: " + loc.problem); alarm("BLACKOUT on the microgrid: " + loc.problem, AlarmManager.Sev.CRITICAL); } }
        switch (state) {
            case IDLE: state = closed ? St.TIED : St.ISLANDED; break;
            case TIED: tied(d, loc, uOk); break;
            case ISLANDED: islanded(d, loc, uOk); break;
            case RESYNC: resync(d, loc, util, uOk); break;
            case BLACKOUT:
                if (loc != null && loc.problem.isEmpty()) { state = closed ? St.TIED : St.ISLANDED; note("bus live again"); AlarmManager.get(world).clear("mg:" + pos.toLong()); }
                else startSources(d, loc);
                break;
        }
        live(d, loc);
    }

    private void tied(GridData d, GridData.Net loc, boolean uOk) {
        if (!poiClosed(d)) { state = St.ISLANDED; note("intertie found open - islanded"); return; }
        if (!uOk) { island(d, loc, "utility lost (" + (loc == null ? "no network" : !loc.problem.isEmpty() ? loc.problem : !loc.utility ? "no utility source" : String.format(Locale.ROOT, "%.0f%% V, %.2f Hz", loc.puSource * 100, loc.hzPu * nominalHz())) + ")"); return; }
        if (mode() == 3) {                                                     // planned islanding: parallel the sets, then open
            startSources(d, loc);
            if (gensOn(d, loc) > 0 || batteries(d, loc) > 0) island(d, loc, "planned islanding (ISLAND mode)");
            return;
        }
        // controlled intertie: import limit
        int lim = LIMIT[Math.min(4, ctl.get("s_lim"))];
        if (lim > 0 && poiKw > lim) {
            if (++overLimit >= 5 && shed < 2) { shedNext(d, "import " + Math.round(poiKw) + " kW over the " + lim + " kW intertie limit"); overLimit = 0; }
            setBatteries(d, loc, 2);
        } else if (lim > 0 && poiKw < lim * 0.7 && shed > 0) { if (++margin >= 30) { restoreNext(d, "import back under the limit"); margin = 0; } }
        else { overLimit = 0; margin = 0; if (shed > 0 && lim == 0 && ++margin >= 10) { restoreNext(d, "tied to a healthy utility"); margin = 0; } }
    }

    private void island(GridData d, GridData.Net loc, String reason) {
        String r = remote(poi, "open");
        if (!"ok".equals(r) && poiClosed(d)) { why = "CAN'T OPEN INTERTIE: " + r; alarm("Microgrid " + id() + " can't island - intertie refused: " + r, AlarmManager.Sev.MAJOR); return; }
        state = St.ISLANDED;
        why = "";
        note("ISLANDED - " + reason);
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BUZZER, SoundCategory.BLOCKS, 0.7f, 1.0f);
        if (!reason.startsWith("planned") && shed < 1) shedNext(d, "islanding (non-essential load off first)");
        startSources(d, loc);
        setBatteries(d, loc, 2);
        alarm("Microgrid " + id() + " ISLANDED: " + reason, AlarmManager.Sev.MINOR);
        utilOk = 0;
    }

    private void islanded(GridData d, GridData.Net loc, boolean uOk) {
        if (poiClosed(d)) { state = St.TIED; note("intertie closed from elsewhere - tied"); return; }
        if (lockout) { why = "LOCKED OUT - RESET"; return; }
        startSources(d, loc);
        if (loc == null || loc.level == null) return;
        // secondary frequency control: trim the governors back to nominal
        if (loc.level.ac && hz > 0) {
            if (hz < 0.995) trimGens(d, loc, "up");
            else if (hz > 1.005) trimGens(d, loc, "down");
        }
        // load balance: shed on under-frequency or overload, restore with margin
        double gl = genLoad(d, loc);
        boolean bad = loc.level.ac && hz > 0 && hz < 0.97 || gl > 1.0;
        underFor = bad ? underFor + 1 : 0;
        if (underFor >= 2 && shed < 3) { shedNext(d, String.format(Locale.ROOT, "%s (%.2f Hz, generation %.0f%% loaded)", hz < 0.97 ? "UNDER-FREQUENCY" : "GENERATION OVERLOAD", hz * nominalHz(), gl * 100)); underFor = 0; }
        boolean room = gl < 0.6 && (!loc.level.ac || Math.abs(hz - 1) < 0.01);
        margin = room && shed > 0 ? margin + 1 : 0;
        if (margin >= 20) { restoreNext(d, String.format(Locale.ROOT, "margin: generation %.0f%% loaded", gl * 100)); margin = 0; }
        if (volt > 0 && (volt < 0.95 || volt > 1.05)) Protection.standing(world, "mg:" + pos.toLong() + ":v", AlarmManager.Cat.POWER, AlarmManager.Sev.MINOR, String.format(Locale.ROOT, "Microgrid %s islanded voltage %.1f%% out of the 95-105%% band", id(), volt * 100), pos);
        else AlarmManager.get(world).clear("mg:" + pos.toLong() + ":v");
        if (mode() == 2 && utilOk >= CtlConfig.microgridReturnSeconds) { state = St.RESYNC; phase = 150; note("utility back for " + utilOk + " s - resynchronising"); }
        why = mode() == 2 ? (uOk ? "utility back " + utilOk + "/" + CtlConfig.microgridReturnSeconds + " s" : "waiting for the utility") : mode() == 3 ? "ISLAND mode - stays islanded" : "TIED mode: resync by hand (SYNC)";
    }

    private void resync(GridData d, GridData.Net loc, GridData.Net util, boolean uOk) {
        if (!uOk) { state = St.ISLANDED; note("utility failed again during resync"); return; }
        if (poiClosed(d)) { state = St.TIED; return; }
        boolean gens = gensOn(d, loc) > 0;
        double fGrid = util != null ? util.hzPu : 1;
        slip = (hz > 0 ? hz : fGrid) - fGrid;
        // the synchroniser: island a hair fast (+0.03 Hz) so the phase walks round to 12 o'clock
        if (gens) { if (slip < 0.0003) trimGens(d, loc, "up"); else if (slip > 0.0015) trimGens(d, loc, "down"); phase += slip * 360 * nominalHz(); }
        else phase *= 0.5;                                                     // grid-forming inverter slews its own phase
        phase = ((phase % 360) + 540) % 360 - 180;
        double dv = util == null || loc == null ? 1 : Math.abs(util.puSource - loc.puSource);
        boolean ok = Math.abs(slip) < 0.0017 && dv < 0.05 && Math.abs(phase) <= 10;
        why = String.format(Locale.ROOT, "SYNC slip %+.3f Hz dV %.1f%% angle %+.0f°", slip * nominalHz(), dv * 100, phase);
        if (ok) {
            String r = remote(poi, "close");
            if ("ok".equals(r)) {
                state = St.TIED; why = "";
                note(String.format(Locale.ROOT, "RESYNCHRONISED: intertie closed at %+.0f° slip %+.3f Hz", phase, slip * nominalHz()));
                world.playSound(null, pos, com.dogpound.railmap.ModSounds.CONTACTOR, SoundCategory.BLOCKS, 0.8f, 1.0f);
                setBatteries(d, loc, 1);
                AlarmManager.get(world).clear("mg:" + pos.toLong());
            } else why = "SYNC OK but intertie refused: " + r;
        }
    }

    // ---- actions on the plant ----------------------------------------------------------------------------------------
    private List<Long> gens(GridData d, GridData.Net loc) {
        List<Long> l = new ArrayList<>();
        if (loc != null) for (long p : loc.nodes) { GridData.Node n = d.nodes().get(p); if (n != null && (n.kind == GridKind.DIESEL || n.kind == GridKind.TURBINE)) l.add(p); }
        return l;
    }
    private int gensOn(GridData d, GridData.Net loc) { int k = 0; for (long p : gens(d, loc)) if (d.nodes().get(p).gcb && d.nodes().get(p).speed > 0.9f) k++; return k; }
    private int batteries(GridData d, GridData.Net loc) { int k = 0; if (loc != null) for (long p : loc.nodes) { GridData.Node n = d.nodes().get(p); if (n != null && n.kind == GridKind.BATTERY && n.batOn && n.energy > 0) k++; } return k; }
    private double genLoad(GridData d, GridData.Net loc) { double m = 0; for (long p : gens(d, loc)) { GridData.Node n = d.nodes().get(p); if (n.gcb) m = Math.max(m, n.genLoad); } return m; }

    private void startSources(GridData d, GridData.Net loc) {
        for (long p : gens(d, loc)) { GridData.Node n = d.nodes().get(p); if (n.speed < 0.5f) remote(p, "start"); }
        if (loc != null) for (long p : loc.nodes) { GridData.Node n = d.nodes().get(p); if (n != null && n.kind == GridKind.BATTERY && !n.batOn) remote(p, "start"); }
    }

    private void trimGens(GridData d, GridData.Net loc, String dir) { for (long p : gens(d, loc)) if (d.nodes().get(p).gcb) remote(p, dir); }

    /** battery mode: 0 charge, 1 auto, 2 discharge (the BESS panel's MODE selector steps round) */
    private void setBatteries(GridData d, GridData.Net loc, int want) {
        if (loc == null) return;
        for (long p : loc.nodes) {
            GridData.Node n = d.nodes().get(p);
            if (n == null || n.kind != GridKind.BATTERY) continue;
            for (int i = 0; i < 3 && n.batMode != want; i++) if (!remote(p, "mode").startsWith("ok")) break;
        }
    }

    private void shedNext(GridData d, String reason) {
        if (shed >= 3) return;
        List<Long> t = tiers.get(shed);
        int n = 0;
        for (long b : t) if ("ok".equals(remote(b, "open"))) n++;
        note("SHED " + "ABC".charAt(shed) + " (" + n + "/" + t.size() + " breakers): " + reason);
        shed++;
    }

    private void restoreNext(GridData d, String reason) {
        if (shed <= 0) return;
        shed--;
        List<Long> t = tiers.get(shed);
        int n = 0;
        for (long b : t) if ("ok".equals(remote(b, "close"))) n++;
        note("RESTORED " + "ABC".charAt(shed) + " (" + n + "/" + t.size() + "): " + reason);
    }

    private void alarm(String text, AlarmManager.Sev sev) { AlarmManager.get(world).raise(world, "mg:" + pos.toLong(), AlarmManager.Cat.POWER, sev, text, pos, true); }

    private void live(GridData d, GridData.Net loc) {
        boolean closed = poiClosed(d);
        GridData.Net util = closed ? loc : utilitySide(d, loc);
        put("tied", state == St.TIED);
        put("lisl", state == St.ISLANDED);
        put("resync", state == St.RESYNC);
        put("blackout", state == St.BLACKOUT);
        put("util", healthy(util));
        put("shedA", shed >= 1); put("shedB", shed >= 2); put("shedC", shed >= 3);
        put("lgen", gensOn(d, loc) > 0);
        put("lbat", batteries(d, loc) > 0);
        put("lock", lockout);
        put("pwr", powered());
        put("poi", closed);
        put("sync", state == St.RESYNC || !closed && util != null ? (int) Math.round(phase) : 0);
        put("g_hz", (int) Math.max(0, Math.min(100, Math.round((hz - 0.9) * 500))));
        put("g_load", (int) Math.min(150, Math.round(genLoad(d, loc) * 100)));
        int soc = 0, nb = 0;
        if (loc != null) for (long p : loc.nodes) { GridData.Node n = d.nodes().get(p); if (n != null && n.kind == GridKind.BATTERY) { soc += (int) (n.energy * 100 / Math.max(1, GridKind.BATTERY.capacity)); nb++; } }
        put("g_soc", nb == 0 ? 0 : soc / nb);
        text("st", state + " · " + MODES[mode()] + (lockout ? " · LOCKOUT" : ""));
        text("hz", hz <= 0 ? "--.-- HZ" : String.format(Locale.ROOT, "%.2f HZ", hz * nominalHz()));
        text("kv", loc == null || loc.level == null ? "NO BUS" : String.format(Locale.ROOT, "%s %.1f%%", loc.level.label.toUpperCase(Locale.ROOT), volt * 100));
        text("poikw", poi == 0 ? "POI NOT LINKED" : String.format(Locale.ROOT, "POI %s %.0f KW%s", closed ? "CLOSED" : "OPEN", poiKw, LIMIT[Math.min(4, ctl.get("s_lim"))] > 0 ? " LIM " + LIMIT[Math.min(4, ctl.get("s_lim"))] : ""));
        text("gen", String.format(Locale.ROOT, "GEN %d/%d %.0f%% BESS %d", gensOn(d, loc), gens(d, loc).size(), genLoad(d, loc) * 100, batteries(d, loc)));
        text("shed", "SHED A" + tiers.get(0).size() + " B" + tiers.get(1).size() + " C" + tiers.get(2).size() + " OUT " + (shed == 0 ? "NONE" : "ABC".substring(0, shed)));
        text("why", why.isEmpty() ? (log.isEmpty() ? "" : log.get(0)) : why);
    }

    @Override
    public String link(String control, net.minecraft.util.math.BlockPos pick, EntityPlayer who) {
        GridData.Node n = GridData.get(world).node(pick);
        if (n == null || n.kind != GridKind.BREAKER) return "§cPick a grid CIRCUIT BREAKER with the wrench";
        long p = pick.toLong();
        switch (control) {
            case "poi": case "tied": poi = p; markDirty(); return "§aIntertie (point of interconnection): breaker at " + xyz(p) + " - put its key in REMOTE";
            case "shedA": case "shedB": case "shedC": {
                int t = control.charAt(4) - 'A';
                for (List<Long> l : tiers) l.remove(p);
                tiers.get(t).add(p);
                markDirty();
                return "§aBreaker " + xyz(p) + " is now SHED " + control.charAt(4) + " (" + tiers.get(t).size() + " in that stage) - key in REMOTE";
            }
            default: return "§7Click the POI window or a SHED A / B / C window";
        }
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        { String dr = drawers.open(id, who, this); if (dr != null) return dr; }
        GridData d = GridData.get(world);
        switch (id) {
            case "test": return lampTest();
            case "s_mode": ctl.set("s_mode", (ctl.get("s_mode") + 1) % 4); return "§eMode " + MODES[mode()] + new String[]{" - controller off, nothing switched", " - stays tied, islands only if the utility fails, resync by hand", " - islands and resynchronises by itself", " - planned islanding: runs the site on its own sources"}[mode()];
            case "m_trip":
                ctl.set("m_trip", 1 - ctl.get("m_trip"));
                if (ctl.get("m_trip") == 1) { remote(poi, "open"); lockout = true; state = St.ISLANDED; note("INTERTIE TRIP pushed by " + who(who) + " - LOCKOUT"); return "§cINTERTIE TRIPPED + locked out"; }
                return "§7Mushroom released - RESET the lockout";
            default: break;
        }
        if (!powered()) return "§cController dead - no 12 V";
        switch (id) {
            case "reset":
                if (ctl.get("m_trip") == 1) return "§cRelease the INTERTIE TRIP mushroom first";
                lockout = false; why = ""; AlarmManager.get(world).clear("mg:" + pos.toLong());
                return "§aLockout reset";
            case "dosync":
                if (poiClosed(d)) return "§7Already tied";
                if (lockout) return "§cLOCKED OUT";
                state = St.RESYNC; phase = 150; note("manual resync started by " + who(who));
                return "§eSynchroniser running - watch the synchroscope";
            case "island": {
                if (!poiClosed(d)) return "§7Already islanded";
                island(d, local(), "manual islanding by " + who(who));
                return "§6Islanded";
            }
            case "shed": shedNext(d, "manual, " + who(who)); return "§6Shed stage " + shed;
            case "restore": restoreNext(d, "manual, " + who(who)); return "§aRestored - " + shed + " stage(s) still shed";
            default: return null;
        }
    }

    @Override
    protected void save(NBTTagCompound t) {
        drawers.write(t);
        t.setLong("poi", poi);
        for (int i = 0; i < 3; i++) { NBTTagList l = new NBTTagList(); for (long p : tiers.get(i)) l.appendTag(new NBTTagLong(p)); t.setTag("tier" + i, l); }
        t.setString("state", state.name()); t.setInteger("shed", shed); t.setBoolean("lock", lockout);
    }

    @Override
    protected void load(NBTTagCompound t) {
        drawers.read(t);
        poi = t.getLong("poi");
        for (int i = 0; i < 3; i++) { tiers.get(i).clear(); NBTTagList l = t.getTagList("tier" + i, 4); for (int k = 0; k < l.tagCount(); k++) tiers.get(i).add(((NBTTagLong) l.get(k)).getLong()); }
        try { state = St.valueOf(t.getString("state")); } catch (RuntimeException e) { state = St.IDLE; }
        shed = t.getInteger("shed"); lockout = t.getBoolean("lock");
    }
}
