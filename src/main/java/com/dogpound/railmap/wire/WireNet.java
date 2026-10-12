package com.dogpound.railmap.wire;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.*;

/**
 * Instrument wiring (requested feature). Each of the 16 wire colours is its own circuit: a Grid Tap puts a
 * point of the power grid on a colour, Instrument Wire / Bundled Cable carry it, a Gauge Panel hole reads it, and a
 * Radio Transmitter / Receiver pair carries every colour across the world on a channel.
 */
public final class WireNet {
    private WireNet() {}

    public static final String[] NAMES = {"White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray",
            "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};
    public static final int[] RGB = {0xF0F0F0, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

    /** the 12 V DC power pair: a 17th "colour" laid in the same runs (red + black), feeds the instruments, panels and radios */
    public static final int POWER = 16;
    public static final int POWER_RGB = 0xC81E1E;
    public static String name(int c) { return c == POWER ? "12 V DC" : c >= 0 && c < 16 ? NAMES[c] : "?"; }
    public static int rgb(int c) { return c == POWER ? POWER_RGB : c >= 0 && c < 16 ? RGB[c] : 0x808080; }

    /** what a powered device (meter, screen, button, gauge panel, radio) gets from the 12 V power wire touching it */
    public static final class Supply {
        public final int state;            // 0 none / dead, 1 12 V DC ok, 2 WRONG voltage (burns the electronics)
        public final String what;
        Supply(int state, String what) { this.state = state; this.what = what; }
    }
    private static final Supply NONE = new Supply(0, "no 12 V DC supply");

    /**
     * Follow the 12 V power wire out of the device at `dev` to the first grid block it reaches (a DC-DC converter set to
     * 12 V, a battery, a cable...) and judge that network: 12 V DC and live = powered; dead = nothing; any other level
     * (24 V, 48 V, mains AC...) = over-voltage.
     */
    public static Supply power(World w, BlockPos dev) {
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        seen.add(dev.toLong());
        q.add(dev);
        Set<Long> body = new HashSet<>();                                       // the device itself, plus a wide desk's side blocks:
        body.add(dev.toLong());                                                 // a 12 V pair landed on either end feeds the desk
        net.minecraft.block.state.IBlockState ds = w.getBlockState(dev);
        if (ds.getBlock() instanceof com.dogpound.railmap.block.BlockWideDesk)
            for (BlockPos part : com.dogpound.railmap.block.BlockOfficePart.parts(dev, ds.getValue(com.dogpound.railmap.block.BlockStationDevice.FACING)))
                if (w.getBlockState(part).getBlock() instanceof com.dogpound.railmap.block.BlockOfficePart) { body.add(part.toLong()); seen.add(part.toLong()); q.add(part); }
        int budget = 3000;
        Supply dead = NONE;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos p = q.poll();
            boolean isDev = body.contains(p.toLong());
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos n = p.offset(f);
                if (!w.isBlockLoaded(n) || seen.contains(n.toLong())) continue;
                if (!throughChase(w, p, w.getTileEntity(p), n, f)) continue;     // no hole this way (another way in may have one)
                seen.add(n.toLong());
                TileEntity t = w.getTileEntity(n);
                if (t instanceof TileWire tw && tw.has(POWER) || t instanceof TileWireChase) { q.add(n); continue; }   // the 12 V pair runs in chase walls too
                if (isDev || !(t instanceof com.dogpound.railmap.grid.TileGrid g)) continue;
                if (g.kind() == com.dogpound.railmap.grid.GridKind.AUTO || g.kind() == com.dogpound.railmap.grid.GridKind.SCADA) continue;   // control gear carries no power
                Supply s = judge(w, n, f.getOpposite());
                if (s.state != 0) return s;                                     // a live supply: 12 V (good) or the wrong voltage (burns)
                dead = s;                                                       // dead one: keep looking along the wire
            }
        }
        return dead;
    }

    /** the wrong voltage just hit a 12 V device: a crack, a fizz and a puff of smoke; it stays dead until repaired */
    public static void burnOut(World w, BlockPos p, String what, String volts) {
        w.playSound(null, p, com.dogpound.railmap.ModSounds.ARC_BUZZ, net.minecraft.util.SoundCategory.BLOCKS, 0.8f, 1.0f);
        w.playSound(null, p, com.dogpound.railmap.ModSounds.FUSE_BLOW, net.minecraft.util.SoundCategory.BLOCKS, 0.25f, 1.0f);
        if (w instanceof net.minecraft.world.WorldServer ws) {
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.02);
            ws.spawnParticle(net.minecraft.util.EnumParticleTypes.LAVA, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
        }
        com.dogpound.railmap.RailMap.LOG.info("[IR Extras] {} at {} burnt out: fed {} instead of 12 V DC", what, p, volts);
    }

    private static Supply judge(World w, BlockPos grid, EnumFacing touched) {
        com.dogpound.railmap.grid.GridData.Net net = com.dogpound.railmap.grid.GridData.get(w).netAt(grid, touched);
        if (net == null || !net.problem.isEmpty() || net.level == null) return new Supply(0, net == null ? "power wire not on a network" : !net.problem.isEmpty() ? "supply dead: " + net.problem : "supply has no voltage");
        if (net.level == com.dogpound.railmap.grid.Elec.Level.DC12) { com.dogpound.railmap.grid.Ups.note(w, net); return new Supply(1, "12 V DC"); }   // a UPS counts the gear it carries
        return new Supply(2, net.level.label);
    }

    // ---- radio: transmitters by channel, per world ---------------------------------------------------------------
    private static final Map<World, Map<Integer, Set<Long>>> TX = new WeakHashMap<>();

    static void txAdd(World w, int ch, BlockPos p) { TX.computeIfAbsent(w, k -> new HashMap<>()).computeIfAbsent(ch, k -> new HashSet<>()).add(p.toLong()); }
    static void txRemove(World w, int ch, BlockPos p) { Map<Integer, Set<Long>> m = TX.get(w); if (m != null && m.get(ch) != null) m.get(ch).remove(p.toLong()); }
    public static Set<Long> transmitters(World w, int ch) {
        Map<Integer, Set<Long>> m = TX.get(w);
        return m == null || m.get(ch) == null ? Collections.emptySet() : m.get(ch);
    }

    /** where colour c lies across a wire run (px from the centre line): lanes side by side, bundled cable = centre */
    public static double laneOffset(TileWire w, int c) {
        if (w.bundled() || !w.has(c)) return 0;
        int idx = Integer.bitCount(w.mask() & ((1 << c) - 1)), n = w.count();
        return (idx - (n - 1) / 2.0) * 1.15;
    }

    /** does the thing at p plug colour c in (a wire carrying it, a tap on it, a panel hole wired to it, a radio) */
    public static boolean carries(IBlockAccess w, BlockPos p, int c) {
        TileEntity t = w.getTileEntity(p);
        if (t instanceof TileWire tw) return tw.has(c);
        if (t instanceof TileTap tp) return tp.channel == c;
        if (t instanceof TileGaugePanel gp) return gp.uses(c);
        if (t instanceof com.dogpound.railmap.grid.TileInstrument ti) return ti.wire == c;
        if (t instanceof IWiredMachine m) return m.ctlWire() == c;
        return t instanceof TileRadio || t instanceof TileWireChase || t instanceof com.dogpound.railmap.block.TileDataLink;   // a chase carries every colour
    }

    /** for drawing: does the wire block at p run a strand toward f */
    public static boolean joins(IBlockAccess w, BlockPos p, EnumFacing f, TileWire me) {
        TileEntity t = w.getTileEntity(p.offset(f));
        if (t instanceof TileWireChase ch) return me.count() > 0 && ch.hole(f.getOpposite());   // only into a drilled hole
        if (t instanceof TileWire o) return (me.mask() & o.mask()) != 0;
        if (t instanceof TileTap tp) return me.has(tp.channel);
        if (t instanceof TileGaugePanel gp) { for (int c = 0; c < 16; c++) if (me.has(c) && gp.uses(c)) return true; return false; }
        if (t instanceof com.dogpound.railmap.grid.TileInstrument ti) return me.has(ti.wire) || me.has(POWER);
        if (t instanceof TileGaugePanel && me.has(POWER)) return true;
        if (t instanceof com.dogpound.railmap.grid.TileGrid && me.has(POWER)) return true;   // the 12 V supply
        if (t instanceof IWiredGear && me.has(POWER)) return true;                            // its 12 V feed
        if (me.has(POWER) && w.getBlockState(p.offset(f)).getBlock() instanceof com.dogpound.railmap.block.BlockOfficePart) {   // a wide desk's end
            BlockPos d = com.dogpound.railmap.block.BlockOfficePart.desk(w, p.offset(f));
            if (d != null && w.getTileEntity(d) instanceof IWiredGear) return true;
        }
        if (t instanceof IWiredMachine m) return me.has(m.ctlWire());
        return t instanceof TileRadio || t instanceof TileWireChase || t instanceof com.dogpound.railmap.block.TileDataLink;
    }

    /**
     * The point of the grid feeding colour c into the circuit that `start` (a panel, a radio...) is plugged into,
     * following wires of that colour, bundled cables and radio links. Null = nothing on that colour.
     */
    public static BlockPos source(World w, BlockPos start, int c) {
        if (c < 0 || c > 15) return null;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        seen.add(start.toLong());
        expand(w, start, c, q, seen);
        int budget = 6000;
        while (!q.isEmpty() && budget-- > 0) {
            BlockPos p = q.poll();
            TileEntity t = w.getTileEntity(p);
            if (t instanceof TileTap tp) { if (tp.channel == c) return tp.target(); continue; }
            if (t instanceof IWiredMachine m) { if (m.ctlWire() == c) return p; continue; }   // wire landed on a machine's control terminal
            if (t instanceof TileRadio r && !r.working()) continue;               // a radio with no 12 V (or burnt out) is dead
            if (t instanceof TileRadio r && r.receiver()) {                     // over the air to every transmitter on the channel
                for (long tx : transmitters(w, r.channel)) {
                    BlockPos tp2 = BlockPos.fromLong(tx);
                    if (w.isBlockLoaded(tp2) && seen.add(tx)) q.add(tp2);
                }
            }
            if (t instanceof TileWire || t instanceof TileRadio || t instanceof TileWireChase) expand(w, p, c, q, seen);
        }
        return null;
    }

    /** a chase wall only lets wire in and out through its holes (chase-to-chase inside the wall, and gauges on it, always) */
    private static boolean throughChase(World w, BlockPos p, TileEntity me, BlockPos n, EnumFacing f) {
        if (me instanceof TileWireChase mc && !mc.opensTo(f)) return false;
        return !(w.getTileEntity(n) instanceof TileWireChase nc) || nc.opensTo(f.getOpposite());
    }

    /** right-click a machine with a colour of Instrument Wire: land it on (or, same colour again, take it off) its control terminal */
    public static String landWire(IWiredMachine m, int c) {
        if (c == POWER) return "§7That's 12 V power wire - it feeds meters, screens, buttons and radios. Land a signal colour here";
        if (m.ctlWire() == c) { m.setCtlWire(-1); return "§7" + NAMES[c] + " wire taken off the " + m.wiredName() + "'s control terminal"; }
        m.setCtlWire(c);
        return "§a" + NAMES[c] + " wire landed on the " + m.wiredName() + "'s control terminal §7- lay it to gauges, screens, Control Buttons or a Data Link";
    }

    /** every machine wired to the thing at `start` (a Data Link...), by colour: follows every colour out of it */
    public static java.util.Map<Integer, BlockPos> wiredMachines(World w, BlockPos start) {
        java.util.Map<Integer, BlockPos> m = new java.util.TreeMap<>();
        for (int c = 0; c < 16; c++) {
            BlockPos s = source(w, start, c);
            if (s != null && w.getTileEntity(s) instanceof IWiredMachine) m.put(c, s);
        }
        return m;
    }

    /**
     * The control bus: every machine and piece of gear whose terminal is on any colour run reaching `start` (the
     * console), several per colour like a real multi-drop bus. Walks through wires, chases and radios; stops at taps.
     */
    public static List<BlockPos> bus(World w, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<Long> found = new HashSet<>();
        for (int c = 0; c < 16; c++) {
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            Set<Long> seen = new HashSet<>();
            seen.add(start.toLong());
            expand(w, start, c, q, seen);
            int budget = 6000;
            while (!q.isEmpty() && budget-- > 0) {
                BlockPos p = q.poll();
                TileEntity t = w.getTileEntity(p);
                if (t instanceof IWiredMachine m) { if (m.ctlWire() == c && found.add(p.toLong())) out.add(p); continue; }
                if (t instanceof TileTap) continue;
                if (t instanceof TileRadio r && !r.working()) continue;
                if (t instanceof TileRadio r && r.receiver()) {
                    for (long tx : transmitters(w, r.channel)) {
                        BlockPos tp2 = BlockPos.fromLong(tx);
                        if (w.isBlockLoaded(tp2) && seen.add(tx)) q.add(tp2);
                    }
                }
                if (t instanceof TileWire || t instanceof TileRadio || t instanceof TileWireChase) expand(w, p, c, q, seen);
            }
        }
        return out;
    }

    private static void expand(World w, BlockPos p, int c, ArrayDeque<BlockPos> q, Set<Long> seen) {
        TileEntity me = w.getTileEntity(p);
        if (me instanceof TileWire tw && !tw.has(c)) return;
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos n = p.offset(f);
            if (!w.isBlockLoaded(n) || seen.contains(n.toLong())) continue;
            if (!throughChase(w, p, me, n, f)) continue;
            if (carries(w, n, c)) { seen.add(n.toLong()); q.add(n); }
        }
    }
}
