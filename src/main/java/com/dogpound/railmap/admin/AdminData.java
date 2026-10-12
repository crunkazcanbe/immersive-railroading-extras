package com.dogpound.railmap.admin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * The railway's administrative records, per dimension (like the companies): territory claims and named control areas
 * (#111, #115), delegated grants (#111), leases (#112), track access agreements (#113), service-level agreements
 * (#114), admin safe-stopped trains (#117) and the chosen simulation profile (#118).
 */
public final class AdminData extends WorldSavedData {
    private static final String NAME = "irextras_admin";

    /** a rectangle of railway: a company's territory (company > 0) or a named control area (company 0) */
    public static final class Rect {
        public String name = "";
        public int company, x0, z0, x1, z1;
        public boolean has(double x, double z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
        NBTTagCompound w() { NBTTagCompound t = new NBTTagCompound(); t.setString("n", name); t.setInteger("c", company); t.setIntArray("r", new int[]{x0, z0, x1, z1}); return t; }
        static Rect r(NBTTagCompound t) { Rect r = new Rect(); r.name = t.getString("n"); r.company = t.getInteger("c"); int[] a = t.getIntArray("r"); if (a.length == 4) { r.x0 = a[0]; r.z0 = a[1]; r.x1 = a[2]; r.z1 = a[3]; } return r; }
    }

    public enum State { OFFERED, ACTIVE, SUSPENDED, ENDED, DEFAULTED, REFUSED }

    /** #112: a loco or wagon let by a company (or a player) to another company */
    public static final class Lease {
        public int id, lessorCo, lesseeCo, days, maxKm;
        public UUID lessorPlayer, stock;
        public String lessorName = "", stockName = "";
        public boolean loco, maintLessee;
        public long rate;                  // cents per in-game day
        public State state = State.OFFERED;
        public long offered, startDay;
        public double kmStart;
        public float wearStart;
        public int arrears, daysRun;
        public long paid, owedPlayer;
    }

    /** #113: an infrastructure owner lets an operator run over its lines (and use its stations / signals) for fees */
    public static final class Agreement {
        public int id, ownerCo, operatorCo, caps;
        public final Set<String> lines = new LinkedHashSet<>();   // empty = all the owner's lines
        public long perKm, perCall;        // cents per train-km, per station call
        public State state = State.OFFERED;
        public double km;                  // since the last settlement
        public int calls, arrears;
        public long owed, paid;
        public boolean covers(String line) { return lines.isEmpty() || lines.contains(line); }
    }

    /** #114: performance targets on one line, two-way: the owner pays for lost availability, the operator for lost punctuality / capacity */
    public static final class Sla {
        public int id, ownerCo, operatorCo, punctPct, availPct, depsPerDay;
        public String line = "";
        public long penalty;               // cents per percentage point (or per missing departure)
        public State state = State.OFFERED;
        public long ownerPaid, operatorPaid;
        public int lastDay = -1, breaches;
        public final List<String> history = new ArrayList<>();
    }

    public final List<Rect> claims = new ArrayList<>(), areas = new ArrayList<>();
    /** per player: [granted caps, denied caps] inside their own company's territory (delegation) */
    public final Map<UUID, int[]> grants = new HashMap<>();
    public final List<Lease> leases = new ArrayList<>();
    public final List<Agreement> agreements = new ArrayList<>();
    public final List<Sla> slas = new ArrayList<>();
    /** admin safe-stopped trains -> why */
    public final Map<UUID, String> stopped = new LinkedHashMap<>();
    public String profile = "";
    public int nextId = 1;
    public long settledDay = -1;

    public AdminData() { super(NAME); }
    public AdminData(String n) { super(n); }

    public static AdminData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        AdminData d = (AdminData) s.getOrLoadData(AdminData.class, NAME);
        if (d == null) { d = new AdminData(); s.setData(NAME, d); }
        return d;
    }

    public Lease lease(int id) { for (Lease l : leases) if (l.id == id) return l; return null; }
    public Agreement agreement(int id) { for (Agreement a : agreements) if (a.id == id) return a; return null; }
    public Sla sla(int id) { for (Sla s : slas) if (s.id == id) return s; return null; }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        claims.clear(); areas.clear(); grants.clear(); leases.clear(); agreements.clear(); slas.clear(); stopped.clear();
        NBTTagList l = t.getTagList("claims", 10);
        for (int i = 0; i < l.tagCount(); i++) claims.add(Rect.r(l.getCompoundTagAt(i)));
        l = t.getTagList("areas", 10);
        for (int i = 0; i < l.tagCount(); i++) areas.add(Rect.r(l.getCompoundTagAt(i)));
        NBTTagCompound g = t.getCompoundTag("grants");
        for (String k : g.getKeySet()) try { int[] a = g.getIntArray(k); if (a.length == 2) grants.put(UUID.fromString(k), a); } catch (IllegalArgumentException ignored) { }
        l = t.getTagList("leases", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound x = l.getCompoundTagAt(i);
            Lease e = new Lease();
            e.id = x.getInteger("id"); e.lessorCo = x.getInteger("lc"); e.lesseeCo = x.getInteger("ec"); e.days = x.getInteger("d"); e.maxKm = x.getInteger("mk");
            if (x.hasUniqueId("lp")) e.lessorPlayer = x.getUniqueId("lp");
            if (x.hasUniqueId("st")) e.stock = x.getUniqueId("st");
            e.lessorName = x.getString("ln"); e.stockName = x.getString("sn"); e.loco = x.getBoolean("lo"); e.maintLessee = x.getBoolean("ml");
            e.rate = x.getLong("r"); e.state = st(x.getString("s")); e.offered = x.getLong("of"); e.startDay = x.getLong("sd");
            e.kmStart = x.getDouble("km"); e.wearStart = x.getFloat("ws"); e.arrears = x.getInteger("ar"); e.daysRun = x.getInteger("dr");
            e.paid = x.getLong("pd"); e.owedPlayer = x.getLong("op");
            if (e.stock != null) leases.add(e);
        }
        l = t.getTagList("agree", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound x = l.getCompoundTagAt(i);
            Agreement a = new Agreement();
            a.id = x.getInteger("id"); a.ownerCo = x.getInteger("oc"); a.operatorCo = x.getInteger("pc"); a.caps = x.getInteger("cp");
            NBTTagList ls = x.getTagList("lines", 8);
            for (int k = 0; k < ls.tagCount(); k++) a.lines.add(ls.getStringTagAt(k));
            a.perKm = x.getLong("pk"); a.perCall = x.getLong("pc2"); a.state = st(x.getString("s")); a.km = x.getDouble("km");
            a.calls = x.getInteger("ca"); a.arrears = x.getInteger("ar"); a.owed = x.getLong("ow"); a.paid = x.getLong("pd");
            agreements.add(a);
        }
        l = t.getTagList("slas", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound x = l.getCompoundTagAt(i);
            Sla s = new Sla();
            s.id = x.getInteger("id"); s.ownerCo = x.getInteger("oc"); s.operatorCo = x.getInteger("pc"); s.punctPct = x.getInteger("pp");
            s.availPct = x.getInteger("ap"); s.depsPerDay = x.getInteger("dd"); s.line = x.getString("l"); s.penalty = x.getLong("pen");
            s.state = st(x.getString("s")); s.ownerPaid = x.getLong("op"); s.operatorPaid = x.getLong("pp2"); s.lastDay = x.hasKey("ld") ? x.getInteger("ld") : -1;
            s.breaches = x.getInteger("br");
            NBTTagList h = x.getTagList("h", 8);
            for (int k = 0; k < h.tagCount(); k++) s.history.add(h.getStringTagAt(k));
            slas.add(s);
        }
        NBTTagCompound sp = t.getCompoundTag("stopped");
        for (String k : sp.getKeySet()) try { stopped.put(UUID.fromString(k), sp.getString(k)); } catch (IllegalArgumentException ignored) { }
        profile = t.getString("profile");
        nextId = Math.max(1, t.getInteger("next"));
        settledDay = t.hasKey("settled") ? t.getLong("settled") : -1;
    }

    private static State st(String s) { try { return State.valueOf(s); } catch (IllegalArgumentException e) { return State.ENDED; } }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Rect r : claims) l.appendTag(r.w());
        t.setTag("claims", l);
        l = new NBTTagList();
        for (Rect r : areas) l.appendTag(r.w());
        t.setTag("areas", l);
        NBTTagCompound g = new NBTTagCompound();
        for (Map.Entry<UUID, int[]> e : grants.entrySet()) g.setIntArray(e.getKey().toString(), e.getValue());
        t.setTag("grants", g);
        l = new NBTTagList();
        for (Lease e : leases) {
            NBTTagCompound x = new NBTTagCompound();
            x.setInteger("id", e.id); x.setInteger("lc", e.lessorCo); x.setInteger("ec", e.lesseeCo); x.setInteger("d", e.days); x.setInteger("mk", e.maxKm);
            if (e.lessorPlayer != null) x.setUniqueId("lp", e.lessorPlayer);
            x.setUniqueId("st", e.stock);
            x.setString("ln", e.lessorName); x.setString("sn", e.stockName); x.setBoolean("lo", e.loco); x.setBoolean("ml", e.maintLessee);
            x.setLong("r", e.rate); x.setString("s", e.state.name()); x.setLong("of", e.offered); x.setLong("sd", e.startDay);
            x.setDouble("km", e.kmStart); x.setFloat("ws", e.wearStart); x.setInteger("ar", e.arrears); x.setInteger("dr", e.daysRun);
            x.setLong("pd", e.paid); x.setLong("op", e.owedPlayer);
            l.appendTag(x);
        }
        t.setTag("leases", l);
        l = new NBTTagList();
        for (Agreement a : agreements) {
            NBTTagCompound x = new NBTTagCompound();
            x.setInteger("id", a.id); x.setInteger("oc", a.ownerCo); x.setInteger("pc", a.operatorCo); x.setInteger("cp", a.caps);
            NBTTagList ls = new NBTTagList();
            for (String s : a.lines) ls.appendTag(new NBTTagString(s));
            x.setTag("lines", ls);
            x.setLong("pk", a.perKm); x.setLong("pc2", a.perCall); x.setString("s", a.state.name()); x.setDouble("km", a.km);
            x.setInteger("ca", a.calls); x.setInteger("ar", a.arrears); x.setLong("ow", a.owed); x.setLong("pd", a.paid);
            l.appendTag(x);
        }
        t.setTag("agree", l);
        l = new NBTTagList();
        for (Sla s : slas) {
            NBTTagCompound x = new NBTTagCompound();
            x.setInteger("id", s.id); x.setInteger("oc", s.ownerCo); x.setInteger("pc", s.operatorCo); x.setInteger("pp", s.punctPct);
            x.setInteger("ap", s.availPct); x.setInteger("dd", s.depsPerDay); x.setString("l", s.line); x.setLong("pen", s.penalty);
            x.setString("s", s.state.name()); x.setLong("op", s.ownerPaid); x.setLong("pp2", s.operatorPaid); x.setInteger("ld", s.lastDay); x.setInteger("br", s.breaches);
            NBTTagList h = new NBTTagList();
            for (String k : s.history) h.appendTag(new NBTTagString(k));
            x.setTag("h", h);
            l.appendTag(x);
        }
        t.setTag("slas", l);
        NBTTagCompound sp = new NBTTagCompound();
        for (Map.Entry<UUID, String> e : stopped.entrySet()) sp.setString(e.getKey().toString(), e.getValue());
        t.setTag("stopped", sp);
        t.setString("profile", profile);
        t.setInteger("next", nextId);
        t.setLong("settled", settledDay);
        return t;
    }
}
