package com.dogpound.railmap.block;

import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.Reading;
import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.TileSignalMast;
import com.dogpound.railmap.signal.TileTrackCircuit;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * The signal box's mechanical lever frame, working (requested feature). Twelve levers in the real colours: yellow distants, red home / starter / shunt signals, black
 * points, blue facing point locks, white spare. Link a lever to its signals / points with the Signal Wrench (pick the
 * signal or the switch track, then right-click the lever). The frame is interlocked like a real tappet frame:
 * <ul>
 *   <li>points can't be thrown while their facing point lock is in;</li>
 *   <li>a home or starter can't be pulled until the points it reads over are locked; a distant only once its home and
 *       starter are off;</li>
 *   <li>back-locking: a lever another pulled lever depends on can't be put back until that one goes back first.</li>
 * </ul>
 * The levers are mechanical (no power needed). The block instruments, bells and diagram lamps run on the 12 V supply.
 * Block instruments: link this box to another with the wrench (pick that box, then right-click an instrument dial here):
 * your commutator sets the needle on their dial and your bell plunger rings their bell.
 */
public class TileSignalBox extends TileDispatcherBoard implements IControlled, IWiredGear {
    public static final String[] KIND = {"", "distant", "home", "starter", "facing point lock", "points", "spare", "points",
            "facing point lock", "starter", "home", "shunt", "distant"};
    /** levers that must be pulled (reverse) before this one can be pulled */
    private static final int[][] NEEDS = new int[13][];
    /** points lever -> its facing point lock lever */
    private static final Map<Integer, Integer> FPL = new HashMap<>();
    static {
        for (int i = 0; i < 13; i++) NEEDS[i] = new int[0];
        NEEDS[2] = new int[]{4}; NEEDS[3] = new int[]{4};
        NEEDS[9] = new int[]{8}; NEEDS[10] = new int[]{8};
        NEEDS[1] = new int[]{2, 3}; NEEDS[12] = new int[]{9, 10};
        FPL.put(5, 4); FPL.put(7, 8);
    }

    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    /** lever -> what it works (signals or switch tracks) */
    private final Map<Integer, List<BlockPos>> links = new HashMap<>();
    /** diagram track-circuit lamp 1..3 -> its track circuit */
    private final Map<Integer, BlockPos> tcs = new HashMap<>();
    /** block instrument a/b/c -> the other box and its instrument letter */
    private final Map<Character, BlockPos> partner = new HashMap<>();
    private final Map<Character, Character> partnerLetter = new HashMap<>();

    // ---- controls -------------------------------------------------------------------------------------------------
    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); }

    private boolean rev(int n) { return ctl.get("lever" + n) == 1; }

    @Override
    public int controlValue(String id) {
        if (id.startsWith("lever")) return ctl.get(id);
        if (!term.powered()) return 0;                                         // lamps, dials: dark with no 12 V
        if (id.startsWith("sig")) {
            int n = Integer.parseInt(id.substring(3));
            if (!rev(n)) return 0;
            for (BlockPos p : links.getOrDefault(n, Collections.emptyList()))
                if (world.getTileEntity(p) instanceof TileSignalMast m) return m.aspect().isStop() ? 0 : 1;
            return 1;
        }
        if (id.startsWith("tc")) {
            BlockPos p = tcs.get(Integer.parseInt(id.substring(2)));
            return p != null && world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileTrackCircuit t && t.occupied() ? 1 : 0;
        }
        if (id.startsWith("ind")) {                                             // the needle: what the far box's commutator says
            char l = id.charAt(3);
            BlockPos p = partner.get(l);
            if (p == null || !world.isBlockLoaded(p) || !(world.getTileEntity(p) instanceof TileSignalBox o)) return 0;
            return o.ctl.get("com" + partnerLetter.get(l));
        }
        return ctl.get(id);
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("lever")) { int n = Integer.parseInt(id.substring(5)); String r = pull(n); questLever(n, r, who); return r; }
        if (id.startsWith("bell")) {
            if (!term.powered()) return "§cNo 12 V - the block bell circuit is dead";
            char l = id.charAt(4);
            BlockPos p = partner.get(l);
            if (p == null || !(world.getTileEntity(p) instanceof TileSignalBox o)) return "§7Block " + Character.toUpperCase(l) + " isn't linked to another box (wrench: pick that box, then click this dial)";
            o.ring();
            return "§eBell rung to the box at " + p.getX() + ", " + p.getY() + ", " + p.getZ();
        }
        if (id.startsWith("com")) {
            int v = (ctl.get(id) + 1) % 3;
            ctl.set(id, v);
            if (term.powered()) {                                              // 3-1 is line clear / 2 train entering / 2-1 train out of section
                BlockPos far = partner.get(id.charAt(3));
                String code = v == 1 ? "signal.bellcode.is_line_clear" : v == 2 ? "signal.bellcode.train_entering" : "signal.bellcode.train_out";
                world.playSound(null, far != null ? far : pos, com.dogpound.railmap.sound.PackSounds.ev(code), SoundCategory.BLOCKS, 1.0f, 1.0f);
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("signal.block_needle"), SoundCategory.BLOCKS, 0.7f, 1.0f);
            }
            return "§eBlock " + Character.toUpperCase(id.charAt(3)) + ": " + new String[]{"LINE BLOCKED", "LINE CLEAR", "TRAIN ON LINE"}[v];
        }
        return null;
    }

    /** quest hook: levers the frame refused since the last route was set (memory only) */
    private transient int refused;

    /** a distant coming off = a whole route set through the locking (FPL, home, starter, distant) */
    private void questLever(int n, String r, EntityPlayer who) {
        if (r == null || !com.dogpound.railmap.api.QuestEvents.active()) return;
        if (r.startsWith("§c")) { refused++; return; }
        if (!KIND[n].equals("distant") || !rev(n)) return;
        int linked = 0;
        for (int need : NEEDS[n]) linked += links.getOrDefault(need, Collections.emptyList()).size();
        linked += links.getOrDefault(n, Collections.emptyList()).size();
        com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "route_set", "lever", n, "kind", KIND[n], "refused", refused, "linked", linked);
        refused = 0;
    }

    private void ring() {
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("signal.block_bell"), SoundCategory.BLOCKS, 1.0f, 1.0f);
        ctl.pressId = "ringing";
        ctl.pressAt = world.getTotalWorldTime();
    }

    /** a lever pulled or put back, if the locking lets it move */
    private String pull(int n) {
        if (n < 1 || n > 12) return "§7No such lever";
        boolean to = !rev(n);
        String kind = KIND[n];
        if (to) {
            for (int need : NEEDS[n]) if (!rev(need)) return "§cLever " + n + " is locked - pull lever " + need + " (" + KIND[need] + ") first";
        } else {
            for (int m = 1; m <= 12; m++)
                if (rev(m)) for (int need : NEEDS[m]) if (need == n) return "§cLever " + n + " is back-locked by lever " + m + " (" + KIND[m] + ") - put " + m + " back first";
        }
        Integer lock = FPL.get(n);
        if (lock != null && rev(lock)) return "§cPoints lever " + n + " is locked by facing point lock " + lock + " - put " + lock + " back first";
        ctl.set("lever" + n, to ? 1 : 0);
        apply(n);
        String did = switch (kind) {
            case "points" -> to ? "points reversed" : "points normal";
            case "facing point lock" -> to ? "points locked" : "points unlocked";
            case "distant" -> to ? "distant off (clear)" : "distant on (caution)";
            case "spare" -> "spare lever";
            default -> to ? kind + " signal off" : kind + " signal on (danger)";
        };
        int linked = links.getOrDefault(n, Collections.emptyList()).size();
        return "§eLever " + n + " " + (to ? "pulled" : "put back") + " - " + did + (linked == 0 && !kind.equals("facing point lock") && !kind.equals("spare")
                ? " §7(not linked: Signal Wrench - pick a signal / switch, then right-click this lever)" : "");
    }

    /** make the outside world match lever n */
    private void apply(int n) {
        boolean r = rev(n);
        String kind = KIND[n];
        for (BlockPos p : links.getOrDefault(n, Collections.emptyList())) {
            if (!world.isBlockLoaded(p)) continue;
            if (world.getTileEntity(p) instanceof TileSignalMast m) {
                if (kind.equals("distant")) m.setRelayControl(false, r ? null : Aspect.APPROACH);
                else m.setRelayControl(!r, null);
            } else if (kind.equals("points")) {
                com.dogpound.railmap.scan.SwitchControl.set(world, p, r);
            }
        }
    }

    /** Signal Wrench: link the picked signal / switch / track circuit / signal box to a lever, lamp or instrument */
    public String link(String id, BlockPos pick) {
        if (id.startsWith("lever")) {
            int n = Integer.parseInt(id.substring(5));
            List<BlockPos> l = links.computeIfAbsent(n, k -> new ArrayList<>());
            if (l.remove(pick)) { controlsChanged(); return "§7Unlinked from lever " + n; }
            boolean signal = world.getTileEntity(pick) instanceof TileSignalMast;
            if (KIND[n].equals("points") && signal) return "§cLever " + n + " is a points lever - pick the switch track, not a signal";
            if (!KIND[n].equals("points") && !signal) return "§cLever " + n + " (" + KIND[n] + ") works signals - pick a signal";
            l.add(pick);
            apply(n);
            controlsChanged();
            return "§aLinked to lever " + n + " (" + KIND[n] + ")";
        }
        if (id.startsWith("tc") && world.getTileEntity(pick) instanceof TileTrackCircuit) {
            tcs.put(Integer.parseInt(id.substring(2)), pick);
            controlsChanged();
            return "§aTrack circuit shown on the diagram";
        }
        if ((id.startsWith("ind") || id.startsWith("com") || id.startsWith("bell")) && world.getTileEntity(pick) instanceof TileSignalBox o && !pick.equals(pos)) {
            char me = id.charAt(id.startsWith("bell") ? 4 : 3);
            char them = me;                                                     // same letter on both boxes
            partner.put(me, pick); partnerLetter.put(me, them);
            o.partner.put(them, pos); o.partnerLetter.put(them, me);
            controlsChanged(); o.controlsChanged();
            return "§aBlock " + Character.toUpperCase(me) + " wired to the box at " + pick.getX() + ", " + pick.getY() + ", " + pick.getZ();
        }
        return "§7The wrench can't link that here";
    }

    @Override
    public void update() {
        super.update();
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        if (world.getTotalWorldTime() % 40 == 17) for (int n = 1; n <= 12; n++) if (links.containsKey(n)) apply(n);   // keep the outside world right
        if (world.getTotalWorldTime() % 20 == 5) controlsChanged();             // lamps / dials follow the railway
    }

    // ---- 12 V terminal + console --------------------------------------------------------------------------------------
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return "Signal box lever frame"; }
    @Override public String gearState() {
        StringBuilder b = new StringBuilder();
        for (int n = 1; n <= 12; n++) if (rev(n)) b.append(b.length() == 0 ? "pulled: " : ", ").append(n);
        return b.length() == 0 ? "all levers normal" : b.toString();
    }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public int wiredLamp() { return term.burnt ? 3 : 1; }
    @Override public void wiredRead(Reading r, BlockInstrument.Inst k) { IWiredGear.super.wiredRead(r, k); }
    @Override public Map<String, Object> wiredInfo() { return IWiredGear.super.wiredInfo(); }
    @Override public int ctlWire() { return term.ctl; }
    @Override public void setCtlWire(int c) { term.ctl = c; controlsChanged(); }

    // PanelHost: the frame's controls, not the console desk
    @Override public int pv(String key) { return controlValue(key); }
    @Override public String ptext(String key) { return ctl.text(key); }
    @Override public boolean pressedRecently(String id, long now) { return id.equals(ctl.pressId) && now - ctl.pressAt < 6; }
    @Override public Map<String, float[]> anim() { return ctl.anim; }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        ctl.write(t);
        term.write(t);
        NBTTagCompound l = new NBTTagCompound();
        for (Map.Entry<Integer, List<BlockPos>> e : links.entrySet()) {
            long[] a = new long[e.getValue().size()];
            for (int i = 0; i < a.length; i++) a[i] = e.getValue().get(i).toLong();
            NBTTagList nl = new NBTTagList();
            for (long v : a) nl.appendTag(new net.minecraft.nbt.NBTTagLong(v));
            l.setTag("l" + e.getKey(), nl);
        }
        for (Map.Entry<Integer, BlockPos> e : tcs.entrySet()) l.setLong("tc" + e.getKey(), e.getValue().toLong());
        for (Map.Entry<Character, BlockPos> e : partner.entrySet()) { l.setLong("p" + e.getKey(), e.getValue().toLong()); l.setString("q" + e.getKey(), String.valueOf(partnerLetter.get(e.getKey()))); }
        t.setTag("frame", l);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        ctl.read(t);
        term.read(t);
        if (!t.hasKey("term")) term.legacy = false;                             // the frame never ran without its terminal
        links.clear(); tcs.clear(); partner.clear(); partnerLetter.clear();
        NBTTagCompound l = t.getCompoundTag("frame");
        for (String k : l.getKeySet()) {
            if (k.startsWith("l")) {
                List<BlockPos> ps = new ArrayList<>();
                NBTTagList nl = l.getTagList(k, 4);
                for (int i = 0; i < nl.tagCount(); i++) ps.add(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong) nl.get(i)).getLong()));
                links.put(Integer.parseInt(k.substring(1)), ps);
            } else if (k.startsWith("tc")) tcs.put(Integer.parseInt(k.substring(2)), BlockPos.fromLong(l.getLong(k)));
            else if (k.startsWith("p")) { char c = k.charAt(1); partner.put(c, BlockPos.fromLong(l.getLong(k))); partnerLetter.put(c, l.getString("q" + c).charAt(0)); }
        }
    }
}
