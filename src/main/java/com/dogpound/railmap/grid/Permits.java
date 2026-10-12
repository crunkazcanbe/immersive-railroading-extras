package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMapConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Electrical switching permits (her ChatGPT list #44). Before anyone works on high-voltage plant (1 kV and up) the
 * real procedure has to be done, in order:
 *   1 ISOLATE   every way power could get in is open (breakers / disconnectors) - and nothing in the section is a source
 *   2 EARTH     an Earthing Switch is closed on the section
 *   3 PROVE DEAD  the multimeter has tested it dead since the permit form was made out
 *   4 LOCK OFF  every isolation point and the earth carry a lockout padlock, and every padlock's key hangs in the key
 *               safe of the Permit Board that issues the permit (so nobody can unlock while people are working)
 * Then the board ISSUES the permit to the person in charge. With it in their inventory they may repair, overhaul,
 * re-set or move taps on anything inside the isolated section. While it is live the padlocks cannot be removed, the
 * keys cannot leave the safe, the earth cannot be taken off and the boundary breakers cannot be closed. Hand the
 * permit back at the board (CANCEL) to undo it. Work on live HV plant zaps.
 */
public final class Permits extends WorldSavedData {
    private static final String NAME = "irextras_permits";

    public static final class Permit {
        public int id;
        public long equip, board;
        public String holder = "", what = "";
        public long issued;
        public boolean live = true;
        public final Set<Long> area = new HashSet<>(), points = new HashSet<>(), earths = new HashSet<>();
    }

    private final List<Permit> permits = new ArrayList<>();
    private int nextId = 1;

    public Permits() { super(NAME); }
    public Permits(String n) { super(n); }

    public static Permits get(World w) {
        MapStorage st = w.getPerWorldStorage();
        Permits p = (Permits) st.getOrLoadData(Permits.class, NAME);
        if (p == null) { p = new Permits(); st.setData(NAME, p); }
        return p;
    }

    public List<Permit> all() { return permits; }
    public Permit byId(int id) { for (Permit p : permits) if (p.id == id) return p; return null; }
    public List<Permit> live() { List<Permit> l = new ArrayList<>(); for (Permit p : permits) if (p.live) l.add(p); return l; }

    Permit add(World w, long equip, long board, String holder, String what, Check c) {
        Permit p = new Permit();
        p.id = nextId++;
        p.equip = equip; p.board = board; p.holder = holder; p.what = what; p.issued = w.getTotalWorldTime();
        p.area.addAll(c.area); p.points.addAll(c.points); p.earths.addAll(c.earths);
        permits.add(p);
        while (permits.size() > 100) { int k = -1; for (int i = 0; i < permits.size(); i++) if (!permits.get(i).live) { k = i; break; } if (k < 0) break; permits.remove(k); }
        markDirty();
        return p;
    }

    void cancel(Permit p) { p.live = false; markDirty(); }

    // ---- the checks ------------------------------------------------------------------------------------------------
    /** is this designated HV equipment (1 kV and up)? */
    public static boolean designated(World w, BlockPos p) {
        if (!RailMapConfig.permitToWork) return false;
        GridData d = GridData.get(w);
        GridData.Node n = d.node(p);
        if (n == null || n.kind.control() || n.kind == GridKind.UPS) return false;
        GridData.Net net = d.netOf(p);
        double v = net != null && net.level != null ? net.level.volts : 0;
        if (n.sided()) v = Math.max(v, Elec.Level.byName(n.primary, Elec.Level.MV13800).volts);
        if (n.kind == GridKind.INTAKE || n.kind.generator()) v = Math.max(v, n.lvl(Elec.Level.AC480).volts);
        return v >= 1000;
    }

    public static final class Check {
        public boolean isolated, earthed, dead, locked;
        public final List<String> lines = new ArrayList<>();
        public final Set<Long> area = new HashSet<>(), points = new HashSet<>(), earths = new HashSet<>();
        public boolean ok() { return isolated && earthed && dead && locked; }
    }

    /** the networks the work area covers: the equipment's own, and a converter's primary side too */
    static List<GridData.Net> section(GridData d, BlockPos eq) {
        List<GridData.Net> l = new ArrayList<>();
        GridData.Net n = d.netOf(eq);
        if (n != null) l.add(n);
        GridData.Node x = d.node(eq);
        if (x != null && x.sided() && x.upNet >= 0 && x.upNet < d.nets().size() && !l.contains(d.nets().get(x.upNet))) l.add(d.nets().get(x.upNet));
        return l;
    }

    private static boolean liveNet(GridData.Net n) { return n != null && n.level != null && n.problem.isEmpty(); }

    /** walk the procedure for work on `eq`; bindAt = when the form was made out (the dead test must come after) */
    public static Check check(World w, BlockPos eq, long bindAt) {
        GridData d = GridData.get(w);
        Check c = new Check();
        List<GridData.Net> sec = section(d, eq);
        if (sec.isEmpty()) { c.lines.add("§c✖ not part of a network"); return c; }
        List<String> feeds = new ArrayList<>();
        boolean earthed = true, dead = false;
        for (GridData.Net n : sec) {
            for (long p : n.nodes) {
                GridData.Node x = d.nodes().get(p);
                if (x == null) continue;
                c.area.add(p);
                if ((x.kind.source() && x.feeding()) || (x.kind == GridKind.BATTERY && x.batOn)) feeds.add(x.kind.label + " " + Protection.at(p) + " is ON");
                if (GridData.opens(x) && x.breaker != 0) c.points.add(p);
                BlockPos bp = BlockPos.fromLong(p);
                for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.values()) {   // open switches on the boundary belong to the next network over
                    GridData.Node y = d.node(bp.offset(f));
                    if (y != null && GridData.opens(y) && y.breaker != 0) c.points.add(bp.offset(f).toLong());
                }
                if (x.kind == GridKind.EARTHING && x.earthOn && x.get("maint", 0) == 1) c.earths.add(p);
                if (x.deadTestAt >= bindAt && bindAt >= 0) dead = true;
            }
            for (long cv : n.ins) {
                GridData.Node x = d.nodes().get(cv);
                if (x == null || !x.passes() || x.upNet < 0 || x.upNet >= d.nets().size()) continue;
                if (sec.contains(d.nets().get(x.upNet))) continue;
                if (liveNet(d.nets().get(x.upNet))) feeds.add(x.kind.label + " " + Protection.at(cv) + " back-feeds it (switch its output off / isolate its primary)");
            }
            if (!n.earthed) earthed = false;
        }
        c.area.removeAll(c.points);                                            // the isolation points themselves have live on one side
        c.isolated = feeds.isEmpty() && !c.points.isEmpty();
        c.lines.add(c.isolated ? "§a✔ 1 ISOLATED§7 at " + c.points.size() + " point(s)" : "§c✖ 1 NOT ISOLATED§7: " + (feeds.isEmpty() ? "no open breaker / disconnector bounds the section" : feeds.get(0)));
        c.earthed = !c.earths.isEmpty();
        c.lines.add(c.earthed ? "§a✔ 2 EARTHED§7 (" + c.earths.size() + " maintenance earth)" : "§c✖ 2 NOT EARTHED§7: close a MAINTENANCE Earthing Switch (Role setting) on the section - after proving it dead");
        c.dead = dead && c.isolated;
        c.lines.add(c.dead ? "§a✔ 3 PROVEN DEAD§7 with the multimeter" : "§c✖ 3 NOT PROVEN DEAD§7: test it with the multimeter (after isolating)");
        String unlocked = "";
        for (long p : c.points) if (!padlocked(w, p)) { unlocked = Protection.at(p); break; }
        if (unlocked.isEmpty()) for (long p : c.earths) if (!padlocked(w, p)) { unlocked = Protection.at(p) + " (earth)"; break; }
        c.locked = unlocked.isEmpty() && !c.points.isEmpty();
        c.lines.add(c.locked ? "§a✔ 4 LOCKED OFF§7 - padlocks on every point (keys go in the Permit Board's key safe)" : "§c✖ 4 NOT LOCKED OFF§7: no padlock on " + (unlocked.isEmpty() ? "the isolation points" : unlocked));
        return c;
    }

    static boolean padlocked(World w, long p) {
        BlockPos b = BlockPos.fromLong(p);
        return w.isBlockLoaded(b) && w.getTileEntity(b) instanceof TileGrid t && !t.loto().isEmpty();
    }

    // ---- what a live permit forbids / allows -----------------------------------------------------------------------
    /** may this player work on this equipment now? "" = yes, else why not (shown to them) */
    public static String mayWork(EntityPlayer p, BlockPos eq) {
        World w = p.world;
        if (!designated(w, eq)) return "";
        Permits ps = get(w);
        for (ItemStack s : p.inventory.mainInventory) {
            Permit pm = ItemPermit.permit(ps, s);
            if (pm != null && pm.live && pm.area.contains(eq.toLong())) return "";
        }
        if (p.capabilities.isCreativeMode && p.isSneaking()) return "";
        return "§c⛔ PERMIT TO WORK needed: this is high-voltage plant. Isolate, earth, prove dead, lock off, then get a permit issued at a Permit Board";
    }

    /** is the equipment live right now (working on it would zap you)? */
    public static boolean liveNow(World w, BlockPos eq) {
        GridData d = GridData.get(w);
        for (GridData.Net n : section(d, eq)) if (liveNet(n)) return true;
        return false;
    }

    private Permit holding(long pos, boolean earth) {
        for (Permit p : permits) if (p.live && (earth ? p.earths.contains(pos) : p.points.contains(pos))) return p;
        return null;
    }

    public static String closeBlocked(World w, BlockPos b) {
        Permit p = get(w).holding(b.toLong(), false);
        return p == null ? "" : "⛔ PERMIT #" + p.id + " (" + p.holder + ") is live on this section - cancel it at the Permit Board before closing";
    }

    public static String earthBlocked(World w, BlockPos b) {
        Permit p = get(w).holding(b.toLong(), true);
        return p == null ? "" : "⛔ PERMIT #" + p.id + " (" + p.holder + ") depends on this earth - cancel it at the Permit Board first";
    }

    /** a padlock that protects people under a live permit cannot come off */
    public static String lockHeld(World w, BlockPos b) {
        Permits ps = get(w);
        Permit p = ps.holding(b.toLong(), false);
        if (p == null) p = ps.holding(b.toLong(), true);
        return p == null ? "" : "⛔ This padlock protects PERMIT #" + p.id + " (" + p.holder + ") - its key is locked in the Permit Board's key safe until the permit is cancelled";
    }

    /** a key hanging in a safe that a live permit locked */
    public Permit keyHeld(long devicePos) {
        for (Permit p : permits) if (p.live && (p.points.contains(devicePos) || p.earths.contains(devicePos))) return p;
        return null;
    }

    // ---- save ------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        permits.clear();
        nextId = Math.max(1, t.getInteger("next"));
        NBTTagList l = t.getTagList("p", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Permit p = new Permit();
            p.id = c.getInteger("id"); p.equip = c.getLong("eq"); p.board = c.getLong("board"); p.holder = c.getString("by"); p.what = c.getString("what");
            p.issued = c.getLong("at"); p.live = c.getBoolean("live");
            for (long x : longs(c, "area")) p.area.add(x);
            for (long x : longs(c, "pts")) p.points.add(x);
            for (long x : longs(c, "ea")) p.earths.add(x);
            permits.add(p);
        }
    }

    private static long[] longs(NBTTagCompound c, String k) {
        NBTTagList l = c.getTagList(k, Constants.NBT.TAG_COMPOUND);
        long[] r = new long[l.tagCount()];
        for (int i = 0; i < r.length; i++) r[i] = l.getCompoundTagAt(i).getLong("v");
        return r;
    }

    private static NBTTagList list(Set<Long> s) {
        NBTTagList l = new NBTTagList();
        for (long x : s) { NBTTagCompound c = new NBTTagCompound(); c.setLong("v", x); l.appendTag(c); }
        return l;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("next", nextId);
        NBTTagList l = new NBTTagList();
        for (Permit p : permits) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", p.id); c.setLong("eq", p.equip); c.setLong("board", p.board); c.setString("by", p.holder); c.setString("what", p.what);
            c.setLong("at", p.issued); c.setBoolean("live", p.live);
            c.setTag("area", list(p.area)); c.setTag("pts", list(p.points)); c.setTag("ea", list(p.earths));
            l.appendTag(c);
        }
        t.setTag("p", l);
        return t;
    }
}
