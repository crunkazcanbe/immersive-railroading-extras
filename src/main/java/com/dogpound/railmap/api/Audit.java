package com.dogpound.railmap.api;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Automation audit log (her ChatGPT list #102): who - which computer, script, control panel, logic module, web request
 * or player - issued a command, to what, the state before, the state after and the result. Saved with the world so a
 * server admin can find an accidental or unauthorised change after the fact (console API tab "Audit",
 * /irextras api audit [filter]).
 */
public final class Audit extends WorldSavedData {
    private static final String NAME = "irextras_audit";

    /** where a command came from */
    public enum Src { PLAYER, PANEL, CONSOLE, COMPUTER, PROGRAM, LOGIC, ORDERS, WEB, SCENARIO, API }

    public static final class Entry {
        public long tick, wall;
        public Src src = Src.API;
        public String who = "", target = "", action = "", before = "", after = "", result = "";
        public long pos;

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("t", tick); t.setLong("w", wall); t.setString("s", src.name()); t.setString("who", who); t.setString("tg", target);
            t.setString("a", action); t.setString("b", before); t.setString("af", after); t.setString("r", result); t.setLong("p", pos);
            return t;
        }

        static Entry read(NBTTagCompound t) {
            Entry e = new Entry();
            e.tick = t.getLong("t"); e.wall = t.getLong("w");
            try { e.src = Src.valueOf(t.getString("s")); } catch (IllegalArgumentException ex) { e.src = Src.API; }
            e.who = t.getString("who"); e.target = t.getString("tg"); e.action = t.getString("a"); e.before = t.getString("b");
            e.after = t.getString("af"); e.result = t.getString("r"); e.pos = t.getLong("p");
            return e;
        }

        public boolean refused() { String r = result.toLowerCase(Locale.ROOT); return r.startsWith("refused") || r.contains("denied") || r.contains("could not") || r.startsWith("no "); }

        public String line() {
            return String.format(Locale.ROOT, "%s %-8s %s -> %s %s [%s -> %s] %s", clock(tick), src, who, target, action, before, after, result);
        }
    }

    public final List<Entry> entries = new ArrayList<>();     // newest first
    public int version;

    public Audit() { super(NAME); }
    public Audit(String n) { super(n); }

    public static Audit get(World w) {
        MapStorage s = w.getPerWorldStorage();
        Audit d = (Audit) s.getOrLoadData(Audit.class, NAME);
        if (d == null) { d = new Audit(); s.setData(NAME, d); }
        return d;
    }

    /** write one line. Never throws: auditing must not break the command it records. */
    public static void log(World w, Src src, String who, BlockPos at, String target, String action, String before, String after, String result) {
        if (w == null || w.isRemote) return;
        try {
            Audit a = get(w);
            Entry e = new Entry();
            e.tick = w.getTotalWorldTime(); e.wall = System.currentTimeMillis(); e.src = src;
            e.who = cut(who, 48); e.target = cut(target, 64); e.action = cut(action, 64); e.before = cut(before, 48); e.after = cut(after, 48);
            e.result = cut(result == null ? "ok" : result.replaceAll("§.", ""), 120); e.pos = at == null ? 0 : at.toLong();
            a.entries.add(0, e);
            while (a.entries.size() > ApiConfig.auditKeep) a.entries.remove(a.entries.size() - 1);
            a.version++;
            a.markDirty();
        } catch (RuntimeException ignored) { }
    }

    /** press a control on a machine and log it with the control's value before and after */
    public static String press(World w, Src src, String who, com.dogpound.railmap.controls.IControlled m, String id, java.util.function.Supplier<String> doIt) {
        int before = safeValue(m, id);
        String r = doIt.get();
        net.minecraft.tileentity.TileEntity t = (net.minecraft.tileentity.TileEntity) m;
        String name = m instanceof com.dogpound.railmap.wire.IWiredMachine wm ? wm.wiredName() : t.getBlockType().getLocalizedName();
        log(w, src, who, t.getPos(), name, "press " + id, String.valueOf(before), String.valueOf(safeValue(m, id)), r == null ? "ok" : r);
        return r;
    }

    private static int safeValue(com.dogpound.railmap.controls.IControlled m, String id) {
        try { return m.controlValue(id); } catch (RuntimeException e) { return 0; }
    }

    static String cut(String s, int n) { s = s == null ? "" : s; return s.length() > n ? s.substring(0, n - 1) + "." : s; }

    static String clock(long tick) {
        long d = tick / 24000, t = (tick + 6000) % 24000;
        return String.format(Locale.ROOT, "D%d %02d:%02d", d, t / 1000, (t % 1000) * 60 / 1000);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        entries.clear();
        NBTTagList l = t.getTagList("e", 10);
        for (int i = 0; i < l.tagCount(); i++) entries.add(Entry.read(l.getCompoundTagAt(i)));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Entry e : entries) l.appendTag(e.write());
        t.setTag("e", l);
        return t;
    }
}
