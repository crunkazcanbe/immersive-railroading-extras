package com.dogpound.railmap.road;

import com.dogpound.railmap.ohle.Reg;
import com.dogpound.railmap.stn.StnPanel;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Traffic signal controller cabinet (batch 5 #3 / #4): the roadside cabinet that runs one junction's signals - the
 * traffic heads (phase groups A-D), the pedestrian crossing units (P) and the tram signals (T) within 48 blocks that
 * have this as their nearest controller. Stages: one group green at a time with amber, all-red and (UK) red+amber
 * between; a pedestrian stage when someone pushed the button; a tram stage when a detection loop calls it.
 * <p>
 * MODE selector: AUTO = vehicle-actuated (a green rests until another group has demand, is held up to MAX GRN while
 * vehicles keep arriving, and a tram call cuts it short after MIN GRN - tram priority), FIXED = fixed-time cycle,
 * MANUAL = a stage changes only on ADV (the POLICE key forces this too), FLASH = flashing amber, OFF = dark. TIMING
 * selector + / - set the six timings; ALL RED holds every signal at red; PED / TRAM test calls; LOG prints the
 * statistics; TEST = lamp test. The timing screen shows the stage, time left, timings and counters.
 * 12 V control + mains (230 V class, the lamps' load). No supply = every signal it runs goes dark.
 */
public class TileTrafficController extends StnPanel {
    static final Reg<TileTrafficController> REG = new Reg<>();
    public static final int RANGE = 48;
    public static final String GROUPS = "ABCDPT";
    public static final int P = 4, T = 5;

    final Phasing ph = new Phasing();
    private long tramCalledAt = -1, tramWaitLast = -1, tramWaitSum;
    private int tramServed, pedServed;
    private final long[] extendUntil = new long[6];

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    public static List<TileTrafficController> all(World w) { return new ArrayList<>(REG.all(w)); }

    /** the controller nearest to a field unit, within RANGE */
    public static TileTrafficController nearest(World w, BlockPos p) {
        TileTrafficController best = null;
        double bd = (double) RANGE * RANGE;
        for (TileTrafficController c : REG.all(w)) {
            double d = c.getPos().distanceSq(p);
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    @Override public String wiredName() { return "Traffic signal controller " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !live() ? "NO SUPPLY - signals dark" : modeName() + " · " + ph.stageName() + " · " + ph.cycles + " cycles"; }
    @Override protected long load() { return 2 + 2L * ph.presentCount(); }      // the lamps it lights

    public int mode() { return ctl.get("k_police") == 1 ? Phasing.MANUAL : Math.floorMod(ctl.get("s_mode"), 5); }
    String modeName() { return ctl.get("k_police") == 1 ? "POLICE MANUAL" : Phasing.MODES[mode()]; }

    /** what a group's signals show now (Phasing aspect codes) */
    public int aspect(int g) { return live() ? ph.aspect(g) : Phasing.DARK; }
    public boolean demand(int g) { return ph.demand[g]; }

    // ---- calls from the field ---------------------------------------------------------------------------------------
    public void pedCall() { if (live() && !ph.demand[P]) { ph.demand[P] = true; markDirty(); } }
    public void vehCall(int g) { if (g >= 0 && g < 4 && live() && ph.present[g] && !ph.demand[g] && !(ph.stage == g && ph.phase == Phasing.GREEN_PHASE)) { ph.demand[g] = true; markDirty(); } }
    public void extend(int g) { if (g >= 0 && g < 6) extendUntil[g] = world.getTotalWorldTime() + 50; }
    public void tramCall() {
        if (!live()) return;
        if (!ph.demand[T] && !(ph.stage == T && ph.phase == Phasing.GREEN_PHASE)) { ph.demand[T] = true; tramCalledAt = world.getTotalWorldTime(); markDirty(); }
    }
    public void tramCancel() { if (ph.stage == T && ph.phase == Phasing.GREEN_PHASE) ph.cancel = true; }

    // ---- the cabinet's buttons -------------------------------------------------------------------------------------
    @Override
    protected String act(String id, EntityPlayer who) {
        switch (id) {
            case "adv":
                if (mode() != Phasing.MANUAL) return "§7ADV only works in MANUAL (mode selector, or the POLICE key)";
                ph.advReq = true;
                return "§eAdvance to the next stage requested";
            case "allred":
                ph.holdAllRed = !ph.holdAllRed;
                return ph.holdAllRed ? "§cALL RED - every signal held at red" : "§aAll red released";
            case "pedtest": pedCall(); return "§ePedestrian demand inserted";
            case "tramtest": tramCall(); return "§eTram priority call inserted";
            case "tup": case "tdn": {
                int k = Math.floorMod(ctl.get("s_tsel"), 6);
                ph.tim[k] = Math.max(Phasing.TMIN[k], Math.min(Phasing.TMAX[k], ph.tim[k] + (id.equals("tup") ? 1 : -1)));
                if (ph.tim[1] < ph.tim[0]) ph.tim[1] = ph.tim[0];
                markDirty();
                return "§e" + Phasing.TNAMES[k] + " = " + ph.tim[k] + " s";
            }
            case "log": {
                List<String> out = new ArrayList<>();
                out.add("TRAFFIC SIGNALS " + pos.getX() + "," + pos.getZ());
                out.add("Mode " + modeName() + ", " + ph.cycles + " cycles");
                for (int g = 0; g < 6; g++) if (ph.present[g] || ph.served[g] > 0) out.add("Group " + GROUPS.charAt(g) + ": " + ph.served[g] + " greens" + (ph.present[g] ? "" : " (no signals)"));
                out.add("Pedestrian stages " + pedServed + ", tram priority " + tramServed + (tramServed > 0 ? ", avg tram wait " + (tramWaitSum / tramServed / 20) + " s" : ""));
                for (int k = 0; k < 6; k++) out.add(Phasing.TNAMES[k] + " " + ph.tim[k] + " s");
                StnUtil.print(world, pos, controlFacing(), "Signal log", "TS " + pos.getX() + "," + pos.getZ(), out);
                return "§aSignal log printed";
            }
            default: return null;
        }
    }

    @Override
    protected void work() {
        if (age % 20 == 3) scanField();
        long now = world.getTotalWorldTime();
        ph.mode = mode();
        for (int g = 0; g < 6; g++) ph.extend[g] = now < extendUntil[g];
        if (live()) {
            int was = ph.stage, wasPh = ph.phase;
            ph.tick();
            if (ph.stage != was && ph.phase != Phasing.ALLRED) {
                if (ph.stage == T) { tramServed++; if (tramCalledAt >= 0) { tramWaitLast = now - tramCalledAt; tramWaitSum += tramWaitLast; tramCalledAt = -1; } }
                if (ph.stage == P) pedServed++;
                markDirty();
            }
            if (wasPh != ph.phase && ph.phase == Phasing.ENDING && ph.stage == P)
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("relay_click"), net.minecraft.util.SoundCategory.BLOCKS, 0.4f, 1.2f);
        }
        if (age % 4 == 1) refresh();
    }

    /** which groups have signals bound to this controller */
    private void scanField() {
        boolean[] p = new boolean[6];
        for (TileTrafficSignal s : TileTrafficSignal.REG.all(world)) if (s.controller() == this) p[s.group()] = true;
        for (TilePedUnit s : TilePedUnit.REG.all(world)) if (s.controller() == this) p[P] = true;
        for (TileTramSignal s : TileTramSignal.REG.all(world)) if (s.controller() == this) p[T] = true;
        System.arraycopy(p, 0, ph.present, 0, 6);
    }

    private void refresh() {
        boolean t = ctl.get("lamptest") == 1;
        for (int g = 0; g < 6; g++) {
            char c = GROUPS.charAt(g);
            int a = ph.aspect(g);
            put("g" + c, t || a == Phasing.GREEN || a == Phasing.WALK_FLASH && g == P);
            put("d" + c, t || ph.demand[g]);
        }
        put("flt", t || powered() && !mainsOk());
        put("allr", t || ph.holdAllRed);
        put("pol", t || ctl.get("k_police") == 1);
        text("stage", !live() ? "NO SUPPLY - SIGNALS DARK" : ph.stageName());
        text("tleft", modeName() + "  T " + ph.t / 20 + " S" + (ph.phase == Phasing.GREEN_PHASE && ph.stage >= 0 && ph.stage < 4 ? "  MAX " + ph.tim[1] : ""));
        text("tim1", "MIN G " + ph.tim[0] + "  MAX G " + ph.tim[1] + "  AMB " + ph.tim[2]);
        text("tim2", "ALL RED " + ph.tim[3] + "  PED " + ph.tim[4] + "  TRAM " + ph.tim[5]);
        int k = Math.floorMod(ctl.get("s_tsel"), 6);
        text("tim3", "SET " + Phasing.TNAMES[k] + " = " + ph.tim[k] + " S");
        text("cnt", "CYCLES " + ph.cycles + "  PED " + pedServed + "  TRAM " + tramServed);
        text("tram", ph.stage == T && ph.phase == Phasing.GREEN_PHASE ? "TRAM PRIORITY ACTIVE" : ph.demand[T] ? "TRAM CALLED " + (tramCalledAt < 0 ? 0 : (world.getTotalWorldTime() - tramCalledAt) / 20) + " S"
                : "TRAM IDLE" + (tramWaitLast >= 0 ? "  LAST WAIT " + tramWaitLast / 20 + " S" : ""));
    }

    public String[] row() {
        return new String[]{pos.getX() + "," + pos.getZ(), !live() ? "!NO SUPPLY" : ph.holdAllRed ? "!ALL RED" : modeName(), live() ? ph.stageName() : "-",
                String.valueOf(ph.t / 20), demands(), ph.demand[T] ? "~CALLED" : ph.stage == T ? "+ACTIVE" : ".idle", String.valueOf(ph.cycles), presentGroups()};
    }

    String demands() { StringBuilder b = new StringBuilder(); for (int g = 0; g < 6; g++) if (ph.demand[g]) b.append(GROUPS.charAt(g)); return b.length() == 0 ? "-" : b.toString(); }
    String presentGroups() { StringBuilder b = new StringBuilder(); for (int g = 0; g < 6; g++) if (ph.present[g]) b.append(GROUPS.charAt(g)); return b.length() == 0 ? "none" : b.toString(); }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setTag("ph", ph.write());
        t.setLong("tc", tramCalledAt); t.setLong("tw", tramWaitLast); t.setLong("tws", tramWaitSum); t.setInteger("ts", tramServed); t.setInteger("ps", pedServed);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        ph.read(t.getCompoundTag("ph"));
        tramCalledAt = t.hasKey("tc") ? t.getLong("tc") : -1; tramWaitLast = t.hasKey("tw") ? t.getLong("tw") : -1; tramWaitSum = t.getLong("tws");
        tramServed = t.getInteger("ts"); pedServed = t.getInteger("ps");
    }

    /**
     * The stage logic alone (no world), so the self-test can drive it. Groups 0-3 = vehicle phase groups A-D, 4 =
     * pedestrians, 5 = trams. One stage (group) green at a time.
     */
    public static final class Phasing {
        public static final int DARK = 0, RED = 1, RED_AMBER = 2, GREEN = 3, AMBER = 4, FLASH_AMBER = 5, WALK_FLASH = 6;
        public static final int AUTO = 0, FIXED = 1, MANUAL = 2, FLASH = 3, OFF = 4;
        static final String[] MODES = {"AUTO", "FIXED", "MANUAL", "FLASH", "OFF"};
        public static final int ENDING = 1, ALLRED = 2, REDAMBER = 3;
        static final String[] TNAMES = {"MIN GRN", "MAX GRN", "AMBER", "ALL RED", "PED", "TRAM"};
        static final int[] TMIN = {4, 6, 3, 1, 4, 4}, TMAX = {30, 120, 6, 8, 30, 40};
        static final int PED_CLEAR = 5, RA = 2;
        public static final int GREEN_PHASE = 0;

        /** min green, max green, amber, all red, pedestrian walk, tram stage (seconds) */
        public final int[] tim = {7, 30, 3, 2, 7, 8};
        public final boolean[] present = new boolean[6], demand = new boolean[6], extend = new boolean[6];
        public final int[] served = new int[6];
        public int stage = -1, phase = ALLRED, t, mode = AUTO, lastVeh = -1, cycles;
        public boolean holdAllRed, advReq, cancel;

        int presentCount() { int n = 0; for (boolean b : present) if (b) n++; return n; }

        public void tick() {
            if (mode == FLASH || mode == OFF) { stage = -1; phase = ALLRED; t = 0; return; }
            t++;
            switch (phase) {
                case GREEN_PHASE -> { if (holdAllRed || shouldEnd()) { phase = ENDING; t = 0; cancel = false; } }
                case ENDING -> { if (t >= (stage == P ? PED_CLEAR : stage == T ? 2 : tim[2]) * 20) { phase = ALLRED; t = 0; } }
                case ALLRED -> {
                    if (holdAllRed || t < tim[3] * 20) return;
                    int n = choose();
                    if (n < 0) return;
                    if (n < 4) { if (lastVeh >= 0 && n <= lastVeh) cycles++; lastVeh = n; }
                    stage = n; demand[n] = false; served[n]++; t = 0;
                    phase = n < 4 && RoadConfig.redAmber ? REDAMBER : GREEN_PHASE;
                }
                case REDAMBER -> { if (t >= RA * 20) { phase = GREEN_PHASE; t = 0; } }
                default -> phase = ALLRED;
            }
        }

        private boolean shouldEnd() {
            int g = stage;
            if (g < 0) return true;
            if (g == P) return t >= tim[4] * 20;
            if (g == T) return t >= tim[5] * 20 || cancel && t >= 60;
            if (!present[g]) return true;
            boolean other = false;
            for (int k = 0; k < 6; k++) if (k != g && demand[k]) other = true;
            switch (mode) {
                case MANUAL: if (advReq && t >= tim[0] * 20) { advReq = false; return true; } return false;
                case FIXED: return t >= tim[1] * 20 && (other || presentVehiclesExcept(g) > 0);
                default:
                    if (t < tim[0] * 20 || !other) return false;
                    if (demand[T]) return true;                                  // tram priority: cut the green short
                    return !(extend[g] && t < tim[1] * 20);
            }
        }

        private int presentVehiclesExcept(int g) { int n = 0; for (int k = 0; k < 4; k++) if (k != g && present[k]) n++; return n; }

        private int choose() {
            if (demand[T]) return T;
            if (demand[P]) return P;
            int from = lastVeh < 0 ? 0 : lastVeh + 1;
            for (int i = 0; i < 4; i++) {
                int g = (from + i) % 4;
                if (!present[g]) continue;
                if (mode == FIXED || mode == MANUAL || demand[g]) return g;
            }
            if (lastVeh >= 0 && present[lastVeh]) return lastVeh;                 // no demand anywhere: back to rest on the last road
            for (int g = 0; g < 4; g++) if (present[g]) return g;
            return -1;
        }

        public int aspect(int g) {
            if (mode == OFF) return DARK;
            if (mode == FLASH) return g < 4 ? FLASH_AMBER : DARK;
            if (g != stage) return RED;
            return switch (phase) {
                case GREEN_PHASE -> GREEN;
                case ENDING -> g >= 4 ? WALK_FLASH : AMBER;
                case REDAMBER -> RED_AMBER;
                default -> RED;
            };
        }

        public String stageName() {
            if (mode == OFF) return "SIGNALS OFF";
            if (mode == FLASH) return "FLASHING AMBER";
            if (holdAllRed && phase == ALLRED) return "ALL RED HOLD";
            if (stage < 0) return "ALL RED";
            String g = stage == P ? "PEDESTRIANS" : stage == T ? "TRAM" : "STAGE " + GROUPS.charAt(stage);
            return switch (phase) {
                case GREEN_PHASE -> g + (stage == P ? " WALK" : " GREEN");
                case ENDING -> g + (stage == P ? " CLEARING" : stage == T ? " PREPARE" : " AMBER");
                case REDAMBER -> g + " RED+AMBER";
                default -> "ALL RED";
            };
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setIntArray("T", this.tim.clone());
            int d = 0;
            for (int k = 0; k < 6; k++) if (demand[k]) d |= 1 << k;
            t.setInteger("dem", d); t.setInteger("st", stage); t.setInteger("ph", phase); t.setInteger("t", this.t); t.setInteger("lv", lastVeh); t.setInteger("cy", cycles);
            t.setBoolean("ar", holdAllRed); t.setIntArray("sv", served.clone());
            return t;
        }

        void read(NBTTagCompound t) {
            if (t.hasKey("T")) { int[] a = t.getIntArray("T"); for (int k = 0; k < Math.min(6, a.length); k++) this.tim[k] = Math.max(TMIN[k], Math.min(TMAX[k], a[k])); }
            int d = t.getInteger("dem");
            for (int k = 0; k < 6; k++) demand[k] = (d & 1 << k) != 0;
            stage = t.hasKey("st") ? t.getInteger("st") : -1; phase = t.hasKey("ph") ? t.getInteger("ph") : ALLRED; this.t = t.getInteger("t");
            lastVeh = t.hasKey("lv") ? t.getInteger("lv") : -1; cycles = t.getInteger("cy"); holdAllRed = t.getBoolean("ar");
            int[] s = t.getIntArray("sv");
            for (int k = 0; k < Math.min(6, s.length); k++) served[k] = s[k];
        }
    }

    /** self-test of the stage logic: [] = pass */
    static List<String> selfTest() {
        List<String> f = new ArrayList<>();
        Phasing p = new Phasing();
        p.present[0] = p.present[1] = true;
        run(p, 200);
        if (p.stage != 0 || p.phase != Phasing.GREEN_PHASE) f.add("first stage should be A green, is " + p.stageName());
        run(p, 2000);
        if (p.stage != 0) f.add("AUTO must rest on A with no other demand");
        p.demand[1] = true;
        run(p, 20 * (p.tim[2] + p.tim[3] + Phasing.RA) + 40);
        if (p.stage != 1 || p.aspect(1) != Phasing.GREEN || p.aspect(0) != Phasing.RED) f.add("B demand should give B green, A red: " + p.stageName());
        p.extend[1] = true; p.demand[0] = true;
        run(p, 20 * p.tim[0] + 20);
        if (p.stage != 1) f.add("an extended green must be held past min green");
        p.demand[T] = true;
        run(p, 5);
        if (p.phase != Phasing.ENDING || p.aspect(1) != Phasing.AMBER) f.add("a tram call must cut an extended green after min green: " + p.stageName());
        run(p, 20 * (p.tim[2] + p.tim[3]) + 10);
        if (p.stage != T || p.aspect(T) != Phasing.GREEN || p.aspect(0) != Phasing.RED || p.aspect(1) != Phasing.RED) f.add("tram stage expected, " + p.stageName());
        p.demand[P] = true;
        run(p, 20 * (p.tim[5] + 2 + p.tim[3]) + 10);
        if (p.stage != P || p.aspect(P) != Phasing.GREEN) f.add("pedestrian stage expected after the tram, " + p.stageName());
        p.mode = Phasing.FLASH;
        run(p, 1);
        if (p.aspect(0) != Phasing.FLASH_AMBER || p.aspect(P) != Phasing.DARK) f.add("FLASH mode aspects wrong");
        p.mode = Phasing.MANUAL; p.extend[1] = false;
        run(p, 400);
        int st = p.stage;
        run(p, 2000);
        if (p.stage != st) f.add("MANUAL must hold its stage until ADV");
        p.advReq = true;
        run(p, 20 * (p.tim[2] + p.tim[3] + Phasing.RA + 2) + 40);
        if (p.stage == st && st >= 0) f.add("ADV must move MANUAL on");
        if (!f.isEmpty()) f.add(0, "phasing:");
        return f;
    }

    private static void run(Phasing p, int ticks) { for (int i = 0; i < ticks; i++) p.tick(); }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1, 2, 1); }
}
