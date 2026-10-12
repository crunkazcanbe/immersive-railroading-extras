package com.dogpound.railmap.crew;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Crew NPCs and revenue protection (batch 5 #7 #9), saved per world as irextras_crew. Drivers stay what they always
 * were - FleetData.Driver (quals, routes, duty, breaks; the Crew Booking-On Desk and the Driver Competency Assessor
 * write those) - a Staff record of the same name adds their uniform, shift diagram and fatigue. Guards and ticket
 * inspectors live only here. A world without this file has no crew NPCs: nothing changes until someone is hired.
 */
public final class CrewData extends WorldSavedData {
    private static final String NAME = "irextras_crew";
    public static final int DRIVER = 1, GUARD = 2, INSPECTOR = 3;
    public static final String[] ROLE = {"-", "DRIVER", "GUARD", "INSPECTOR"};

    public static final class Staff {
        public String name = "";
        public int role = DRIVER, skin;
        public boolean slim;
        /** shift diagram: start hour (game clock) and length; start -1 = no diagram (signed on / off by hand) */
        public int start = -1, hours = 8;
        /** guard's train; drivers use FleetData.Driver.train */
        public UUID train;
        /** inspector's beat (a line; "" = any line) or a guard's line */
        public String line = "";
        public long office;                 // the Crew Office they sign on at (0 = the nearest)
        public boolean onDuty;              // guards + inspectors (drivers: FleetData)
        public long dutyStart, lastOff, breakUntil;
        public int checks, caught, warned, mistakes;
        public long fined;                  // cents collected in penalty fares (lifetime)
        public double tea;                  // a fresh cuppa at the office: fatigue relief (0-1, wears off)

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("n", name); t.setInteger("r", role); t.setInteger("sk", skin); t.setBoolean("sl", slim);
            t.setInteger("st", start); t.setInteger("h", hours); if (train != null) t.setUniqueId("t", train); t.setString("l", line);
            t.setLong("of", office); t.setBoolean("on", onDuty); t.setLong("ds", dutyStart); t.setLong("lo", lastOff); t.setLong("bu", breakUntil);
            t.setInteger("c", checks); t.setInteger("ca", caught); t.setInteger("w", warned); t.setInteger("m", mistakes); t.setLong("f", fined); t.setDouble("tea", tea);
            return t;
        }

        static Staff read(NBTTagCompound t) {
            Staff s = new Staff();
            s.name = t.getString("n"); s.role = Math.max(1, Math.min(3, t.getInteger("r"))); s.skin = t.getInteger("sk"); s.slim = t.getBoolean("sl");
            s.start = t.hasKey("st") ? t.getInteger("st") : -1; s.hours = Math.max(1, t.getInteger("h")); s.train = t.hasUniqueId("t") ? t.getUniqueId("t") : null; s.line = t.getString("l");
            s.office = t.getLong("of"); s.onDuty = t.getBoolean("on"); s.dutyStart = t.getLong("ds"); s.lastOff = t.getLong("lo"); s.breakUntil = t.getLong("bu");
            s.checks = t.getInteger("c"); s.caught = t.getInteger("ca"); s.warned = t.getInteger("w"); s.mistakes = t.getInteger("m"); s.fined = t.getLong("f"); s.tea = t.getDouble("tea");
            return s;
        }
    }

    /** one revenue protection case: a penalty fare or a warning */
    public static final class Case {
        public int id;
        public long tick;
        public String who = "", where = "", inspector = "", state = "", cam = "";
        public UUID player;
        public long cents;

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("i", id); t.setLong("t", tick); t.setString("w", who); t.setString("at", where); t.setString("in", inspector); t.setString("s", state); t.setString("cam", cam);
            if (player != null) t.setUniqueId("p", player);
            t.setLong("c", cents);
            return t;
        }

        static Case read(NBTTagCompound t) {
            Case c = new Case();
            c.id = t.getInteger("i"); c.tick = t.getLong("t"); c.who = t.getString("w"); c.where = t.getString("at"); c.inspector = t.getString("in"); c.state = t.getString("s"); c.cam = t.getString("cam");
            c.player = t.hasUniqueId("p") ? t.getUniqueId("p") : null; c.cents = t.getLong("c");
            return c;
        }
    }

    public final Map<String, Staff> staff = new LinkedHashMap<>();
    public final Set<Long> offices = new LinkedHashSet<>(), rpOffices = new LinkedHashSet<>();
    public final LinkedList<Case> cases = new LinkedList<>();
    /** players already given their one friendly warning (penaltyMode 1) */
    public final Set<UUID> warnedPlayers = new HashSet<>();
    /** station / line -> last tick an inspector worked it (deterrence) */
    public final Map<Long, Long> patrolled = new HashMap<>();
    public final Map<String, Long> linePatrolled = new HashMap<>();
    public boolean autoRoster = true;
    /** -1 = config penaltyMode, else the Revenue Protection Office's FINES key */
    public int penaltyMode = -1;
    public int nextCase = 1;
    public long day = -1;
    /** today / yesterday: checks, caught, warnings, penalty cents, players checked */
    public final long[] today = new long[5], yesterday = new long[5], total = new long[5];

    public CrewData() { super(NAME); }
    public CrewData(String n) { super(n); }

    public static CrewData get(World w) {
        MapStorage st = w.getPerWorldStorage();
        CrewData d = (CrewData) st.getOrLoadData(CrewData.class, NAME);
        if (d == null) { d = new CrewData(); st.setData(NAME, d); }
        return d;
    }

    public int mode() { return penaltyMode >= 0 ? penaltyMode : CrewConfig.penaltyMode; }

    public Staff byRole(int role, UUID train) {
        for (Staff s : staff.values()) if (s.role == role && train != null && train.equals(s.train)) return s;
        return null;
    }

    public Case open(World w, String who, UUID player, String where, String inspector, String state, long cents, String cam) {
        Case c = new Case();
        c.id = nextCase++; c.tick = w.getTotalWorldTime(); c.who = who; c.player = player; c.where = where; c.inspector = inspector; c.state = state; c.cents = cents; c.cam = cam;
        cases.addFirst(c);
        while (cases.size() > 80) cases.removeLast();
        markDirty();
        return c;
    }

    public Case caseNo(int id) { for (Case c : cases) if (c.id == id) return c; return null; }

    public void count(int k, long n) { today[k] += n; total[k] += n; markDirty(); }

    /** roll the day counters over */
    void rollDay(long d) {
        if (d == day) return;
        if (day >= 0) System.arraycopy(today, 0, yesterday, 0, 5);
        Arrays.fill(today, 0);
        day = d;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        staff.clear(); offices.clear(); rpOffices.clear(); cases.clear(); warnedPlayers.clear(); patrolled.clear(); linePatrolled.clear();
        NBTTagList l = t.getTagList("staff", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) { Staff s = Staff.read(l.getCompoundTagAt(i)); if (!s.name.isEmpty()) staff.put(s.name, s); }
        longs(t, "off", offices);
        longs(t, "rp", rpOffices);
        NBTTagList cl = t.getTagList("cases", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cl.tagCount(); i++) cases.add(Case.read(cl.getCompoundTagAt(i)));
        NBTTagList wl = t.getTagList("warned", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < wl.tagCount(); i++) warnedPlayers.add(wl.getCompoundTagAt(i).getUniqueId("p"));
        NBTTagCompound pt = t.getCompoundTag("pat");
        for (String k : pt.getKeySet()) try { patrolled.put(Long.parseLong(k), pt.getLong(k)); } catch (NumberFormatException ignored) { }
        NBTTagCompound lp = t.getCompoundTag("lpat");
        for (String k : lp.getKeySet()) linePatrolled.put(k, lp.getLong(k));
        autoRoster = !t.hasKey("auto") || t.getBoolean("auto");
        penaltyMode = t.hasKey("pm") ? t.getInteger("pm") : -1;
        nextCase = Math.max(1, t.getInteger("nc"));
        day = t.hasKey("day") ? t.getLong("day") : -1;
        for (int i = 0; i < 5; i++) { today[i] = t.getLong("td" + i); yesterday[i] = t.getLong("yd" + i); total[i] = t.getLong("tt" + i); }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Staff s : staff.values()) l.appendTag(s.write());
        t.setTag("staff", l);
        t.setTag("off", longs(offices));
        t.setTag("rp", longs(rpOffices));
        NBTTagList cl = new NBTTagList();
        for (Case c : cases) cl.appendTag(c.write());
        t.setTag("cases", cl);
        NBTTagList wl = new NBTTagList();
        for (UUID u : warnedPlayers) { NBTTagCompound c = new NBTTagCompound(); c.setUniqueId("p", u); wl.appendTag(c); }
        t.setTag("warned", wl);
        NBTTagCompound pt = new NBTTagCompound();
        for (Map.Entry<Long, Long> e : patrolled.entrySet()) pt.setLong(String.valueOf(e.getKey()), e.getValue());
        t.setTag("pat", pt);
        NBTTagCompound lp = new NBTTagCompound();
        for (Map.Entry<String, Long> e : linePatrolled.entrySet()) lp.setLong(e.getKey(), e.getValue());
        t.setTag("lpat", lp);
        t.setBoolean("auto", autoRoster);
        t.setInteger("pm", penaltyMode);
        t.setInteger("nc", nextCase);
        t.setLong("day", day);
        for (int i = 0; i < 5; i++) { t.setLong("td" + i, today[i]); t.setLong("yd" + i, yesterday[i]); t.setLong("tt" + i, total[i]); }
        return t;
    }

    private static void longs(NBTTagCompound t, String k, Set<Long> into) {
        NBTTagList l = t.getTagList(k, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) into.add(l.getCompoundTagAt(i).getLong("p"));
    }

    private static NBTTagList longs(Set<Long> from) {
        NBTTagList l = new NBTTagList();
        for (long p : from) { NBTTagCompound c = new NBTTagCompound(); c.setLong("p", p); l.appendTag(c); }
        return l;
    }
}
