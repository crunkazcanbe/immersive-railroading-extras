package com.dogpound.railmap.logic;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.api.Audit;
import com.dogpound.railmap.api.Expr;
import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.Reading;
import com.dogpound.railmap.program.ProgramData;
import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.IWiredMachine;
import com.dogpound.railmap.wire.Terminal;
import com.dogpound.railmap.wire.WireNet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Railway logic modules (her ChatGPT list #99): DIN-rail logic relays that run a station, a freight yard or a loading
 * process without OpenComputers. Seven kinds share this block entity - GATE, TIMER, COUNTER, COMPARE, LATCH / EDGE,
 * MATH and SEQUENCER (a state machine). Every module has three inputs A, B, C and one output Q:
 * <ul>
 *   <li>an input reads a signal-box CHANNEL, an Instrument WIRE colour (the machine landed on it: 1 = running; a Grid
 *   Tap: its volts), REDSTONE (A = left side, B = right side, C = underneath, as you face the panel), a program
 *   VARIABLE or an EXPRESSION (the program's language, incl. telemetry: {@code @12,64,-3.kw});</li>
 *   <li>Q drives a channel, the back face's redstone and / or a program variable (and posts logic:&lt;tag&gt;:on/off).</li>
 * </ul>
 * The front panel works: FUNCTION selector, ▲ / ▼ preset (STEP x1 / x10 / x100), RESET, the FORCE key (AUTO / ON / OFF
 * - a forced module raises a standing alarm until it goes back to AUTO), LEDs for 12 V, A, B, C, Q and ERR, and two LCD
 * lines. Runs on 12 V DC: dead (or burnt out) = every output off, the fail-safe state. On the console's WIRED page when
 * its control wire is landed; listed on the API tab's Logic page.
 */
public class TileLogic extends TileEntity implements ITickable, IControlled, IWiredGear, ISettingsHolder {
    public enum Kind {
        GATE("logic_gate", "Logic Gate Module", new String[]{"AND", "OR", "XOR", "NAND", "NOR", "XNOR", "NOT A", "BUF A"}),
        TIMER("logic_timer", "Timer Relay", new String[]{"ON DELAY", "OFF DELAY", "PULSE", "FLASHER"}),
        COUNTER("logic_counter", "Counter Module", new String[]{"COUNT UP", "COUNT DOWN", "UP / DOWN"}),
        COMPARE("logic_compare", "Comparator Module", new String[]{"A > B", "A >= B", "A = B", "A <= B", "A < B", "A <> B", "WINDOW"}),
        LATCH("logic_latch", "Latch + Edge Module", new String[]{"SR SET DOM", "RS RESET DOM", "TOGGLE", "RISING EDGE", "FALLING EDGE", "ANY EDGE"}),
        MATH("logic_math", "Arithmetic Module", new String[]{"A + B", "A - B", "A x B", "A / B", "MIN", "MAX", "AVERAGE", "DIFFERENCE"}),
        SEQUENCER("logic_sequencer", "Sequencer (state machine)", new String[]{"STEP ON A", "TIMED STEPS"});

        public final String id, label;
        public final String[] fns;
        Kind(String id, String label, String[] fns) { this.id = id; this.label = label; this.fns = fns; }
        public static Kind byId(String id) { for (Kind k : values()) if (k.id.equals(id)) return k; return GATE; }
    }

    public static final String[] SOURCES = {"None", "Channel", "Wire colour", "Redstone", "Variable", "Expression"};
    private static final String[] IN = {"A", "B", "C"};
    private static final Map<World, Set<TileLogic>> LOADED = new WeakHashMap<>();

    private Kind kind;
    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    private final SettingsStore cfg = new SettingsStore(this);
    // live state (saved: a real relay keeps its counter / latch in EEPROM)
    private boolean q, set, running;
    private int count, state = 1, outRs, age;
    private long elapsed;                       // ticks
    private final boolean[] last = new boolean[3];
    private final double[] in = new double[3];
    private String err = "";
    private int lastCh = -1;
    private boolean lastForcedAlarm;

    public TileLogic() {}
    public TileLogic(Kind k) {
        kind = k;
        ctl.set("preset", k == Kind.TIMER || k == Kind.LATCH || k == Kind.SEQUENCER ? 50 : 10);   // 5.0 s / a count or setpoint of 10
    }

    public Kind kind() {
        if (kind == null) kind = getBlockType() != null && getBlockType().getRegistryName() != null ? Kind.byId(getBlockType().getRegistryName().getPath()) : Kind.GATE;
        return kind;
    }

    public static int count(World w) { Set<TileLogic> s = w == null ? null : LOADED.get(w); return s == null ? 0 : s.size(); }

    public static List<TileLogic> loaded(World w) {
        Set<TileLogic> s = LOADED.get(w);
        List<TileLogic> out = new ArrayList<>();
        if (s != null) for (TileLogic t : s) if (!t.isInvalid()) out.add(t);
        return out;
    }

    @Override public void onLoad() { if (world != null && !world.isRemote) LOADED.computeIfAbsent(world, k -> Collections.newSetFromMap(new WeakHashMap<>())).add(this); }
    @Override public void invalidate() { super.invalidate(); release(); }
    @Override public void onChunkUnload() { super.onChunkUnload(); if (world != null) { Set<TileLogic> s = LOADED.get(world); if (s != null) s.remove(this); } }

    /** taken away: its channel and alarm must not stay stuck on */
    private void release() {
        if (world == null || world.isRemote) return;
        Set<TileLogic> s = LOADED.get(world);
        if (s != null) s.remove(this);
        if (q && cfg.num("outCh") > 0) ProgramData.get(world).setChannel(cfg.num("outCh"), false);
        AlarmManager.get(world).clear("logic:forced:" + pos.toLong());
    }

    public String tag() { String t = cfg.text("tag").trim(); return t.isEmpty() ? "L" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ() : t; }
    public boolean q() { return q; }
    public String fnName() { String[] f = kind().fns; return f[Math.floorMod(ctl.get("fn"), f.length)]; }
    public String display() { return ctl.text("d1") + " | " + ctl.text("d2"); }

    // ---- the scan (every 2 ticks) -------------------------------------------------------------------------------------
    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        if (++age % 2 != 0) return;
        boolean was = q;
        double value;
        if (!term.powered()) {
            q = false;
            value = 0;
        } else {
            err = "";
            for (int i = 0; i < 3; i++) in[i] = read(i);
            value = scan();
            int force = ctl.get("force");
            if (force == 1) q = true; else if (force == 2) q = false;
            forcedAlarm(force != 0);
        }
        outputs(was, value);
        if (age % 10 == 0 || was != q) panel(value);
    }

    private boolean used(int i) { return !cfg.text("src" + IN[i]).equals("None") && !cfg.text("src" + IN[i]).isEmpty(); }
    private boolean dig(int i) { return in[i] > 0.5 || cfg.text("src" + IN[i]).equals("Redstone") && in[i] > 0 || cfg.text("src" + IN[i]).equals("Variable") && in[i] != 0 || cfg.text("src" + IN[i]).equals("Expression") && in[i] != 0; }
    private boolean rise(int i) { return dig(i) && !last[i]; }
    private boolean fall(int i) { return !dig(i) && last[i]; }

    private double read(int i) {
        String src = cfg.text("src" + IN[i]);
        try {
            switch (src) {
                case "Channel": return ProgramData.get(world).ch(cfg.num("ch" + IN[i])) ? 1 : 0;
                case "Redstone": {
                    EnumFacing f = side(i);
                    return world.getRedstonePower(pos.offset(f), f);
                }
                case "Wire colour": {
                    int c = colour(cfg.text("wire" + IN[i]));
                    BlockPos s = WireNet.source(world, pos, c);
                    if (s == null) return 0;
                    TileEntity t = world.getTileEntity(s);
                    if (t instanceof IWiredMachine m) return m.wiredLamp() == 1 ? 1 : 0;
                    Reading r = new Reading();
                    r.read(world, s, BlockInstrument.Inst.VOLT);
                    return r.value;
                }
                case "Variable": return ProgramData.get(world).var(cfg.text("arg" + IN[i]).trim());
                case "Expression": return Expr.sum(world, cfg.text("arg" + IN[i]));
                default: return 0;
            }
        } catch (RuntimeException e) {
            err = IN[i] + ": " + (e.getMessage() == null ? e.toString() : e.getMessage());
            return 0;
        }
    }

    /** A = left, B = right (as you face the panel), C = underneath */
    private EnumFacing side(int i) {
        EnumFacing f = facing();
        return i == 0 ? f.rotateY() : i == 1 ? f.rotateYCCW() : EnumFacing.DOWN;
    }

    private EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getPropertyKeys().contains(BlockStationDevice.FACING) ? s.getValue(BlockStationDevice.FACING) : EnumFacing.NORTH;
    }

    private static int colour(String name) { for (int c = 0; c < 16; c++) if (WireNet.NAMES[c].equals(name)) return c; return 0; }

    private int preset() { return ctl.get("preset"); }

    /** run the module's function; returns the analogue value it shows / writes (count, elapsed, result, state...) */
    private double scan() {
        int fn = Math.floorMod(ctl.get("fn"), kind().fns.length);
        boolean a = dig(0), b = dig(1), c = dig(2);
        boolean ra = rise(0), rb = rise(1), rc = rise(2), fa = fall(0);
        double value;
        switch (kind()) {
            case GATE: {
                int n = (used(0) && a ? 1 : 0) + (used(1) && b ? 1 : 0) + (used(2) && c ? 1 : 0), of = (used(0) ? 1 : 0) + (used(1) ? 1 : 0) + (used(2) ? 1 : 0);
                boolean and = of > 0 && n == of, or = n > 0, xor = n % 2 == 1;
                q = switch (fn) { case 0 -> and; case 1 -> or; case 2 -> xor; case 3 -> !and; case 4 -> !or; case 5 -> !xor; case 6 -> !a; default -> a; };
                value = n;
                break;
            }
            case TIMER: {
                long pt = Math.max(1, preset()) * 2L;                         // preset in tenths of a second, 2 ticks each
                if (c) { elapsed = 0; q = false; running = false; value = 0; break; }
                switch (fn) {
                    case 0 -> { if (a) { elapsed = Math.min(pt, elapsed + 2); q = elapsed >= pt; } else { elapsed = 0; q = false; } }
                    case 1 -> { if (a) { q = true; elapsed = 0; } else if (q) { elapsed += 2; if (elapsed >= pt) q = false; } }
                    case 2 -> { if (ra && !running) { running = true; elapsed = 0; } if (running) { q = true; elapsed += 2; if (elapsed >= pt) { running = false; q = false; } } }
                    default -> { if (a || !used(0)) { elapsed += 2; if (elapsed >= pt) { elapsed = 0; q = !q; } } else { elapsed = 0; q = false; } }
                }
                value = elapsed / 20.0;
                break;
            }
            case COUNTER: {
                int p = preset();
                if (rc) count = fn == 1 ? p : 0;
                else {
                    if (ra) count += fn == 1 ? -1 : 1;
                    if (rb && fn == 2) count--;
                }
                count = Math.max(-99999, Math.min(99999, count));
                q = fn == 1 ? count <= 0 : count >= p;
                value = count;
                break;
            }
            case COMPARE: {
                double x = in[0], y = used(1) ? in[1] : preset(), h = Math.max(0, cfg.num("hyst"));
                q = switch (fn) {
                    case 0 -> q ? x > y - h : x > y;
                    case 1 -> q ? x >= y - h : x >= y;
                    case 2 -> Math.abs(x - y) <= Math.max(h, 1e-6);
                    case 3 -> q ? x <= y + h : x <= y;
                    case 4 -> q ? x < y + h : x < y;
                    case 5 -> Math.abs(x - y) > Math.max(h, 1e-6);
                    default -> { double lo = used(1) ? in[1] : preset() - h, hi = used(2) ? in[2] : preset() + h; yield x >= lo && x <= hi; }
                };
                value = x;
                break;
            }
            case LATCH: {
                long pt = Math.max(1, preset()) * 2L;
                switch (fn) {
                    case 0 -> { if (a) set = true; else if (b) set = false; q = set; }
                    case 1 -> { if (b) set = false; else if (a) set = true; q = set; }
                    case 2 -> { if (ra) set = !set; if (b) set = false; q = set; }
                    default -> {
                        boolean edge = fn == 3 ? ra : fn == 4 ? fa : ra || fa;
                        if (edge) { running = true; elapsed = 0; }
                        if (running) { elapsed += 2; if (elapsed >= pt) running = false; }
                        q = running;
                    }
                }
                value = q ? 1 : 0;
                break;
            }
            case MATH: {
                double x = in[0], y = in[1];
                value = switch (fn) { case 0 -> x + y; case 1 -> x - y; case 2 -> x * y; case 3 -> y == 0 ? 0 : x / y; case 4 -> Math.min(x, y); case 5 -> Math.max(x, y); case 6 -> (x + y) / 2; default -> Math.abs(x - y); };
                q = value > preset();
                break;
            }
            default: {                                                         // SEQUENCER
                int n = Math.max(2, Math.min(8, cfg.num("states")));
                if (rc) { state = 1; elapsed = 0; }
                else if (fn == 0) { if (ra) state = state % n + 1; if (rb) state = state <= 1 ? n : state - 1; }
                else {
                    if (rb) { state = state <= 1 ? n : state - 1; elapsed = 0; }
                    if (a || !used(0)) { elapsed += 2; if (elapsed >= Math.max(1, preset()) * 2L) { elapsed = 0; state = state % n + 1; } }
                }
                state = Math.max(1, Math.min(n, state));
                q = state == n;
                value = state;
            }
        }
        for (int i = 0; i < 3; i++) last[i] = dig(i);
        if (cfg.bool("invert") && kind() != Kind.SEQUENCER) q = !q;
        return value;
    }

    private void forcedAlarm(boolean forced) {
        if (forced == lastForcedAlarm) return;
        lastForcedAlarm = forced;
        if (forced) AlarmManager.get(world).raise(world, "logic:forced:" + pos.toLong(), AlarmManager.Cat.AUTO, AlarmManager.Sev.MINOR,
                kind().label + " " + tag() + " FORCED " + (ctl.get("force") == 1 ? "ON" : "OFF") + " - key back to AUTO when done", pos, true);
        else AlarmManager.get(world).clear("logic:forced:" + pos.toLong());
    }

    private void outputs(boolean was, double value) {
        ProgramData d = ProgramData.get(world);
        int ch = cfg.num("outCh");
        if (kind() == Kind.SEQUENCER && ch > 0) {                             // one-hot: channel ch + state - 1
            int n = Math.max(2, Math.min(8, cfg.num("states")));
            for (int i = 0; i < n; i++) d.setChannel(ch + i, term.powered() && i + 1 == state);
        } else if (ch > 0) {
            if (lastCh > 0 && lastCh != ch) d.setChannel(lastCh, false);      // re-pointed: let go of the old one
            boolean before = d.ch(ch);
            d.setChannel(ch, q);
            if (before != q) Audit.log(world, Audit.Src.LOGIC, tag(), pos, "channel " + ch, kind().label + " Q", before ? "on" : "off", q ? "on" : "off", "ok");
        }
        lastCh = ch;
        String var = cfg.text("outVar").trim();
        if (!var.isEmpty() && var.matches("[A-Za-z_][A-Za-z0-9_.]*") && term.powered()) {
            double v = kind() == Kind.GATE || kind() == Kind.COMPARE || kind() == Kind.LATCH ? (q ? 1 : 0) : value;
            d.setVar(var, v);
        }
        int rs = !cfg.bool("outRs") ? 0 : kind() == Kind.SEQUENCER ? (term.powered() ? state : 0) : q ? 15 : 0;
        if (rs != outRs) { outRs = rs; markDirty(); world.notifyNeighborsOfStateChange(pos, getBlockType(), false); }
        if (was != q) { ProgramData.post(world, "logic:" + tag() + ":" + (q ? "on" : "off")); markDirty(); }
    }

    public int redstoneOut(EnumFacing side) { return side == facing() ? outRs : 0; }   // out of the back face

    private void panel(double value) {
        boolean pw = term.powered();
        boolean ch = StationControls.put(ctl, "pwr", pw) | StationControls.put(ctl, "la", pw && dig(0)) | StationControls.put(ctl, "lb", pw && dig(1))
                | StationControls.put(ctl, "lc", pw && dig(2)) | StationControls.put(ctl, "lq", pw && q) | StationControls.put(ctl, "err", pw && !err.isEmpty());
        if (kind() == Kind.SEQUENCER) {
            int n = Math.max(2, Math.min(8, cfg.num("states")));
            for (int i = 1; i <= 8; i++) ch |= StationControls.put(ctl, "s" + i, pw && i == state && i <= n);
        }
        if (kind() == Kind.TIMER) ch |= StationControls.put(ctl, "elapsed", pw ? (int) Math.min(100, elapsed * 100 / Math.max(2L, preset() * 2L)) : 0);
        String l1 = !pw ? "" : !err.isEmpty() ? "ERR " + err : switch (kind()) {
            case GATE -> fnName() + "  Q=" + (q ? 1 : 0);
            case TIMER -> String.format(Locale.ROOT, "%.1f / %.1f S", value, preset() / 10.0);
            case COUNTER -> "COUNT " + count + " / " + preset();
            case COMPARE -> "A " + Expr.fmt(round(in[0])) + "  B " + (used(1) ? Expr.fmt(round(in[1])) : preset());
            case LATCH -> fnName() + "  Q=" + (q ? 1 : 0);
            case MATH -> "= " + Expr.fmt(round(value));
            default -> "STATE " + state + " / " + Math.max(2, Math.min(8, cfg.num("states")));
        };
        String l2 = !pw ? "" : fnName() + "  P " + preset() + (ctl.get("force") == 0 ? "" : ctl.get("force") == 1 ? "  FORCED ON" : "  FORCED OFF");
        ch |= StationControls.text(ctl, "d1", l1.toUpperCase(Locale.ROOT)) | StationControls.text(ctl, "d2", l2.toUpperCase(Locale.ROOT));
        if (ch) controlsChanged();
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }

    // ---- the front panel ---------------------------------------------------------------------------------------------
    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }
    @Override public int controlValue(String id) { return term.powered() || id.equals("fn") || id.equals("force") || id.equals("step") ? ctl.get(id) : 0; }
    @Override public String ptext(String key) { return term.powered() ? ctl.text(key) : ""; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        String by = who == null ? "remote" : who.getName();
        switch (id) {
            case "fn": {
                if (!term.powered()) return "§cNo 12 V - the module is dead";
                String before = fnName();
                ctl.set("fn", (ctl.get("fn") + 1) % kind().fns.length);
                Audit.log(world, who == null ? Audit.Src.CONSOLE : Audit.Src.PLAYER, by, pos, kind().label + " " + tag(), "function", before, fnName(), "ok");
                return "§eFunction: " + fnName();
            }
            case "step": ctl.set("step", (ctl.get("step") + 1) % 3); return "§7Step x" + (int) Math.pow(10, ctl.get("step"));
            case "up": case "down": {
                if (!term.powered()) return "§cNo 12 V - the module is dead";
                int st = (int) Math.pow(10, ctl.get("step")), p = preset();
                ctl.set("preset", Math.max(kind() == Kind.MATH || kind() == Kind.COMPARE ? -99999 : 0, Math.min(99999, p + (id.equals("up") ? st : -st))));
                return "§ePreset " + preset() + (kind() == Kind.TIMER || kind() == Kind.LATCH || kind() == Kind.SEQUENCER ? " (" + preset() / 10.0 + " s)" : "");
            }
            case "reset": {
                if (!term.powered()) return "§cNo 12 V - the module is dead";
                count = kind() == Kind.COUNTER && ctl.get("fn") == 1 ? preset() : 0; elapsed = 0; set = false; running = false; state = 1;
                if (kind() != Kind.GATE && kind() != Kind.COMPARE && kind() != Kind.MATH) q = false;
                Audit.log(world, who == null ? Audit.Src.CONSOLE : Audit.Src.PLAYER, by, pos, kind().label + " " + tag(), "reset", "", "", "ok");
                return "§eReset";
            }
            case "force": {
                int was = ctl.get("force");
                ctl.set("force", (was + 1) % 3);
                String[] k = {"AUTO", "FORCE ON", "FORCE OFF"};
                Audit.log(world, who == null ? Audit.Src.CONSOLE : Audit.Src.PLAYER, by, pos, kind().label + " " + tag(), "force key", k[was], k[ctl.get("force")], "ok");
                return "§6Key: " + k[ctl.get("force")];
            }
            default: return "§7" + id;
        }
    }

    // ---- 12 V + console ----------------------------------------------------------------------------------------------
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return kind().label + " " + tag(); }
    @Override public String gearState() { return !term.powered() ? "NO 12 V - outputs off" : fnName() + ", Q " + (q ? "ON" : "off") + (err.isEmpty() ? "" : ", ERR " + err); }
    @Override public int wiredLamp() { return term.burnt || !term.powered() || !err.isEmpty() ? 3 : q ? 1 : 2; }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override
    public void gearInfo(Map<String, Object> m) {
        m.put("kind", kind().id); m.put("function", fnName()); m.put("q", q); m.put("preset", preset());
        m.put("a", in[0]); m.put("b", in[1]); m.put("c", in[2]); m.put("count", count); m.put("state", state); m.put("elapsed", elapsed / 20.0);
        m.put("forced", ctl.get("force")); m.put("error", err); m.put("tag", tag());
    }

    // ---- Settings Console (sneak-right-click with the Signal Wrench) -------------------------------------------------
    @Override public String settingsTitle() { return kind().label; }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        String[] where = {"left side", "right side", "underneath"};
        for (int i = 0; i < 3; i++) {
            String p = "Input " + IN[i];
            l.add(Setting.choice(p, "src" + IN[i], "Reads", "Redstone comes in on the " + where[i] + " (as you face the panel)", i == 0 ? "Channel" : "None", SOURCES));
            l.add(Setting.num(p, "ch" + IN[i], "Channel", "When it reads a channel", i + 1, 1, 64, 1, ""));
            l.add(Setting.choice(p, "wire" + IN[i], "Wire colour", "Instrument Wire into the module: a machine's control wire (1 = running) or a Grid Tap (volts)", WireNet.NAMES[i], WireNet.NAMES));
            l.add(Setting.text(p, "arg" + IN[i], "Variable / expression", "e.g. waiting_pax, or @120,64,-40.kw / ch5 + ch6", "", 96));
        }
        l.add(Setting.num("Output", "outCh", "Q drives channel", kind() == Kind.SEQUENCER ? "first of the channels: state n turns on channel + n - 1 (0 = none)" : "0 = none", 0, 0, 64, 1, ""));
        l.add(Setting.bool("Output", "outRs", "Redstone out of the back", kind() == Kind.SEQUENCER ? "strength = the state number" : "15 when Q is on", true));
        l.add(Setting.text("Output", "outVar", "Write program variable", "Q (1/0), or the count / seconds / result / state", "", 32));
        l.add(Setting.bool("Output", "invert", "Invert Q", "", false));
        l.add(Setting.text("Output", "tag", "Tag", "its name in events (logic:<tag>:on), the audit log and the console", "", 24));
        if (kind() == Kind.SEQUENCER) l.add(Setting.num("Function", "states", "States", "", 4, 2, 8, 1, ""));
        if (kind() == Kind.COMPARE) l.add(Setting.num("Function", "hyst", "Hysteresis / window half-width", "stops chatter around the setpoint", 0, 0, 10000, 1, ""));
        l.add(Setting.num("Function", "presetSet", "Preset", kind() == Kind.TIMER || kind() == Kind.LATCH || kind() == Kind.SEQUENCER ? "tenths of a second (also ▲ ▼ on the panel)" : "also ▲ ▼ on the panel", preset(), -99999, 99999, 1, ""));
        l.add(Setting.info("Status", "Function", fnName() + " (FUNCTION selector on the panel)"));
        if (world != null && !world.isRemote) {
            l.add(Setting.info("Status", "Inputs", "A " + Expr.fmt(round(in[0])) + "  B " + Expr.fmt(round(in[1])) + "  C " + Expr.fmt(round(in[2])) + (err.isEmpty() ? "" : "  ERR " + err)));
            l.add(Setting.info("Status", "Q", q ? "ON" : "off"));
            IWiredGear.settings(l, "Status", this);
        }
        return l;
    }

    @Override public SettingsStore settings() { return cfg; }

    @Override
    public void onSettingsChanged(String key) {
        if (key.equals("presetSet")) ctl.set("preset", cfg.num("presetSet"));
        Audit.log(world, Audit.Src.PLAYER, "settings console", pos, kind().label + " " + tag(), "setting " + key, "", cfg.get(key), "ok");
        controlsChanged();
    }

    // ---- save --------------------------------------------------------------------------------------------------------
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        ctl.write(t); term.write(t); cfg.write(t);
        t.setBoolean("q", q); t.setBoolean("set", set); t.setBoolean("run", running);
        t.setInteger("count", count); t.setInteger("state", state); t.setLong("el", elapsed); t.setInteger("rs", outRs);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        ctl.read(t); term.read(t); cfg.read(t);
        if (!t.hasKey("term")) term.legacy = false;                              // new block: needs its 12 V from the start
        q = t.getBoolean("q"); set = t.getBoolean("set"); running = t.getBoolean("run");
        count = t.getInteger("count"); state = Math.max(1, t.getInteger("state")); elapsed = t.getLong("el"); outRs = t.getInteger("rs");
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager n, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
