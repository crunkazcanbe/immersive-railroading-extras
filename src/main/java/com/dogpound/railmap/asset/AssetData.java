package com.dogpound.railmap.asset;

import com.dogpound.railmap.depot.DepotClock;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * The infrastructure asset register (her ChatGPT list #2 and friends), saved as irextras_assets.dat:
 * <ul>
 *   <li>assets - stable id (TRK-00012, CB-00003...), kind, where, installed, use so far and its rate, condition, last
 *   inspection, commissioning state + sign-off, history and what it depends on;</li>
 *   <li>defects - from hidden (LATENT) through DISCOVERED, CONFIRMED, REPAIR ORDERED, REPAIRED to VERIFIED, with the
 *   true size, the size the inspection measured and its uncertainty;</li>
 *   <li>certificates - commissioning, secondary injection, rebuild acceptance;</li>
 *   <li>purchase orders - spare, quantity, supplier, lead time, state.</li>
 * </ul>
 * Anything it does not know about is new; old worlds start with an empty register that fills as gear is seen.
 */
public final class AssetData extends WorldSavedData {
    private static final String NAME = "irextras_assets";

    public static final String[] COMM = {"UNCOMMISSIONED", "TESTING", "COMMISSIONED", "FAILED TESTS"};
    public static final int UNCOMM = 0, TESTING = 1, COMMISSIONED = 2, COMM_FAIL = 3;
    public static final String[] STAGES = {"LATENT", "DISCOVERED", "CONFIRMED", "REPAIR ORDERED", "REPAIRED", "VERIFIED"};
    public static final int LATENT = 0, DISCOVERED = 1, CONFIRMED = 2, ORDERED = 3, REPAIRED = 4, VERIFIED = 5;
    public static final String[] SUPPLIERS = {"CATALOGUE", "EXPRESS", "FACTORY", "REMAN"};
    public static final String[] PO_STATES = {"OPEN", "DELIVERED", "CANCELLED"};

    public static final class Asset {
        public String id = "", name = "";
        public AssetKind kind;
        public long pos, installed, inspected, commAt, lastSeen;
        public double use, raw = -1, ratePerDay, rateMark;
        public long rateTick;
        public float cond = 100, measured = -1;
        public int comm = COMMISSIONED;
        public boolean asBuilt = true;
        public String inspectedBy = "", commBy = "";
        public final Set<String> deps = new LinkedHashSet<>();
        public final LinkedList<String> history = new LinkedList<>();
        public final double[] m = new double[4];          // per-kind meters (lamp hours, wet days, starts...)

        public double lifeUsed() { return use / Math.max(1e-6, kind.life / Math.max(0.01, AssetConfig.ageingRate)); }

        /** remaining days at today's rate of use, -1 = unknown */
        public double daysLeft() {
            if (ratePerDay <= 1e-9) return -1;
            return Math.max(0, (kind.life / Math.max(0.01, AssetConfig.ageingRate) - use) / ratePerDay);
        }

        public boolean overdue(long now) { return inspected > 0 && now - inspected > kind.inspectDays * 24000L; }
        public BlockPos at() { return BlockPos.fromLong(pos); }

        public void note(World w, String text) {
            history.addFirst(DepotClock.stamp(w) + " " + text);
            while (history.size() > 30) history.removeLast();
        }
    }

    public static final class Defect {
        public int id, stage;
        public String asset = "", type = "", by = "", method = "", unit = "";
        public long pos, found, changed;
        public double size, seen, unc, crit;
        public final LinkedList<String> trail = new LinkedList<>();

        public void step(World w, int stage, String text) {
            this.stage = stage;
            changed = w.getTotalWorldTime();
            trail.addFirst(DepotClock.stamp(w) + " " + STAGES[stage] + ": " + text);
            while (trail.size() > 12) trail.removeLast();
        }

        public boolean open() { return stage >= DISCOVERED && stage < VERIFIED; }
    }

    public static final class Cert {
        public int id;
        public String scope = "", kind = "", by = "";
        public boolean pass;
        public long at;
        public final List<String> lines = new ArrayList<>();
    }

    public static final class Order {
        public int id, spare, qty, supplier, state, baseline;
        public long ordered, due, stores;
        public double cost;
        public String why = "", by = "";
    }

    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final Map<String, String> where = new HashMap<>();          // kind:pos -> id
    public final List<Defect> defects = new ArrayList<>();
    public final List<Cert> certs = new ArrayList<>();
    public final List<Order> orders = new ArrayList<>();
    public final LinkedList<String> log = new LinkedList<>();
    private final int[] next = new int[AssetKind.values().length];
    public int nextDefect = 1, nextCert = 1, nextOrder = 1;
    /** first save with the register: everything found on the first sweep is as-built (commissioned) */
    public boolean seeded;

    public AssetData() { super(NAME); }
    public AssetData(String n) { super(n); }

    public static AssetData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        AssetData d = (AssetData) s.getOrLoadData(AssetData.class, NAME);
        if (d == null) { d = new AssetData(); s.setData(NAME, d); }
        return d;
    }

    public Collection<Asset> all() { return assets.values(); }
    public Asset byId(String id) { return id == null ? null : assets.get(id.toUpperCase(Locale.ROOT)); }
    public Asset at(AssetKind k, long pos) { String id = where.get(k.name() + ":" + pos); return id == null ? null : assets.get(id); }

    /** register (or find) the asset of this kind at pos; fresh = it was just placed (starts uncommissioned) */
    public Asset register(World w, AssetKind k, long pos, String name, boolean fresh) {
        Asset a = at(k, pos);
        if (a != null) { if (!name.isEmpty() && !name.equals(a.name)) { a.name = name; markDirty(); } return a; }
        a = new Asset();
        a.kind = k;
        a.pos = pos;
        a.name = name;
        a.id = String.format(Locale.ROOT, "%s-%05d", k.prefix, ++next[k.ordinal()]);
        a.installed = w.getTotalWorldTime();
        a.inspected = a.installed;
        a.rateTick = a.installed;
        boolean asBuilt = !fresh || !AssetConfig.commissioning;
        a.asBuilt = asBuilt;
        a.comm = asBuilt ? COMMISSIONED : UNCOMM;
        a.commBy = asBuilt ? "as built (register)" : "";
        a.commAt = asBuilt ? a.installed : 0;
        a.note(w, asBuilt ? "registered (existing, accepted as built)" : "INSTALLED - uncommissioned, acceptance tests due");
        assets.put(a.id, a);
        where.put(k.name() + ":" + pos, a.id);
        markDirty();
        return a;
    }

    public void remove(World w, Asset a, String why) {
        assets.remove(a.id);
        where.remove(a.kind.name() + ":" + a.pos);
        note(w, a.id + " " + a.kind.label + " removed from the register (" + why + ")");
        markDirty();
    }

    public void note(World w, String text) {
        log.addFirst(DepotClock.stamp(w) + " " + text);
        while (log.size() > 80) log.removeLast();
        markDirty();
    }

    public Defect defect(int id) { for (Defect d : defects) if (d.id == id) return d; return null; }

    public Defect addDefect(World w, String asset, long pos, String type, String unit, double size, double crit) {
        Defect d = new Defect();
        d.id = nextDefect++;
        d.asset = asset; d.pos = pos; d.type = type; d.unit = unit; d.size = size; d.crit = crit;
        d.found = w.getTotalWorldTime();
        d.changed = d.found;
        d.trail.add(DepotClock.stamp(w) + " initiated");
        defects.add(d);
        while (defects.size() > 300) { Defect old = null; for (Defect x : defects) if (x.stage == VERIFIED) { old = x; break; } if (old == null) break; defects.remove(old); }
        markDirty();
        return d;
    }

    public List<Defect> defectsOf(String asset) {
        List<Defect> o = new ArrayList<>();
        for (Defect d : defects) if (d.asset.equals(asset)) o.add(d);
        return o;
    }

    public Cert addCert(World w, String scope, String kind, boolean pass, List<String> lines, String by) {
        Cert c = new Cert();
        c.id = nextCert++;
        c.scope = scope; c.kind = kind; c.pass = pass; c.by = by;
        c.at = w.getTotalWorldTime();
        c.lines.addAll(lines);
        certs.add(0, c);
        while (certs.size() > 60) certs.remove(certs.size() - 1);
        markDirty();
        return c;
    }

    public Order order(int id) { for (Order o : orders) if (o.id == id) return o; return null; }

    // ---- save ---------------------------------------------------------------------------------------------------------
    private static NBTTagList strings(Collection<String> l) { NBTTagList t = new NBTTagList(); for (String s : l) t.appendTag(new NBTTagString(s)); return t; }
    private static void strings(NBTTagCompound t, String k, Collection<String> into) { NBTTagList l = t.getTagList(k, Constants.NBT.TAG_STRING); for (int i = 0; i < l.tagCount(); i++) into.add(l.getStringTagAt(i)); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        assets.clear(); where.clear(); defects.clear(); certs.clear(); orders.clear(); log.clear();
        int[] nx = t.getIntArray("next");
        for (int i = 0; i < Math.min(nx.length, next.length); i++) next[i] = nx[i];
        nextDefect = Math.max(1, t.getInteger("nd")); nextCert = Math.max(1, t.getInteger("nc")); nextOrder = Math.max(1, t.getInteger("no"));
        seeded = t.getBoolean("seeded");
        NBTTagList al = t.getTagList("assets", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < al.tagCount(); i++) {
            NBTTagCompound c = al.getCompoundTagAt(i);
            AssetKind k = AssetKind.of(c.getString("k"));
            if (k == null) continue;
            Asset a = new Asset();
            a.kind = k; a.id = c.getString("id"); a.name = c.getString("n"); a.pos = c.getLong("p");
            a.installed = c.getLong("in"); a.inspected = c.getLong("is"); a.commAt = c.getLong("ca"); a.lastSeen = c.getLong("ls");
            a.use = c.getDouble("u"); a.raw = c.hasKey("raw") ? c.getDouble("raw") : -1; a.ratePerDay = c.getDouble("rpd"); a.rateMark = c.getDouble("rm"); a.rateTick = c.getLong("rt");
            a.cond = c.hasKey("cond") ? c.getFloat("cond") : 100; a.measured = c.hasKey("ms") ? c.getFloat("ms") : -1;
            a.comm = c.hasKey("cm") ? c.getInteger("cm") : COMMISSIONED; a.asBuilt = c.getBoolean("ab");
            a.inspectedBy = c.getString("ib"); a.commBy = c.getString("cb");
            strings(c, "dep", a.deps);
            strings(c, "h", a.history);
            int[] mm = c.getIntArray("m");
            for (int q = 0; q < Math.min(mm.length, a.m.length); q++) a.m[q] = mm[q] / 100.0;
            assets.put(a.id, a);
            where.put(k.name() + ":" + a.pos, a.id);
        }
        NBTTagList dl = t.getTagList("defects", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dl.tagCount(); i++) {
            NBTTagCompound c = dl.getCompoundTagAt(i);
            Defect d = new Defect();
            d.id = c.getInteger("id"); d.stage = c.getInteger("st"); d.asset = c.getString("a"); d.type = c.getString("t"); d.by = c.getString("by");
            d.method = c.getString("me"); d.unit = c.getString("un"); d.pos = c.getLong("p"); d.found = c.getLong("f"); d.changed = c.getLong("ch");
            d.size = c.getDouble("sz"); d.seen = c.getDouble("se"); d.unc = c.getDouble("uc"); d.crit = c.getDouble("cr");
            strings(c, "tr", d.trail);
            defects.add(d);
        }
        NBTTagList cl = t.getTagList("certs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cl.tagCount(); i++) {
            NBTTagCompound c = cl.getCompoundTagAt(i);
            Cert x = new Cert();
            x.id = c.getInteger("id"); x.scope = c.getString("s"); x.kind = c.getString("k"); x.by = c.getString("by"); x.pass = c.getBoolean("ok"); x.at = c.getLong("at");
            strings(c, "l", x.lines);
            certs.add(x);
        }
        NBTTagList ol = t.getTagList("orders", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ol.tagCount(); i++) {
            NBTTagCompound c = ol.getCompoundTagAt(i);
            Order o = new Order();
            o.id = c.getInteger("id"); o.spare = c.getInteger("sp"); o.qty = c.getInteger("q"); o.supplier = c.getInteger("su"); o.state = c.getInteger("st"); o.baseline = c.getInteger("bl");
            o.ordered = c.getLong("o"); o.due = c.getLong("d"); o.stores = c.getLong("at"); o.cost = c.getDouble("c"); o.why = c.getString("w"); o.by = c.getString("by");
            orders.add(o);
        }
        strings(t, "log", log);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setIntArray("next", next.clone());
        t.setInteger("nd", nextDefect); t.setInteger("nc", nextCert); t.setInteger("no", nextOrder);
        t.setBoolean("seeded", seeded);
        NBTTagList al = new NBTTagList();
        for (Asset a : assets.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("k", a.kind.name()); c.setString("id", a.id); c.setString("n", a.name); c.setLong("p", a.pos);
            c.setLong("in", a.installed); c.setLong("is", a.inspected); c.setLong("ca", a.commAt); c.setLong("ls", a.lastSeen);
            c.setDouble("u", a.use); c.setDouble("raw", a.raw); c.setDouble("rpd", a.ratePerDay); c.setDouble("rm", a.rateMark); c.setLong("rt", a.rateTick);
            c.setFloat("cond", a.cond); c.setFloat("ms", a.measured); c.setInteger("cm", a.comm); c.setBoolean("ab", a.asBuilt);
            c.setString("ib", a.inspectedBy); c.setString("cb", a.commBy);
            c.setTag("dep", strings(a.deps));
            c.setTag("h", strings(a.history));
            int[] mm = new int[a.m.length];
            for (int q = 0; q < mm.length; q++) mm[q] = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(a.m[q] * 100)));
            c.setIntArray("m", mm);
            al.appendTag(c);
        }
        t.setTag("assets", al);
        NBTTagList dl = new NBTTagList();
        for (Defect d : defects) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", d.id); c.setInteger("st", d.stage); c.setString("a", d.asset); c.setString("t", d.type); c.setString("by", d.by);
            c.setString("me", d.method); c.setString("un", d.unit); c.setLong("p", d.pos); c.setLong("f", d.found); c.setLong("ch", d.changed);
            c.setDouble("sz", d.size); c.setDouble("se", d.seen); c.setDouble("uc", d.unc); c.setDouble("cr", d.crit);
            c.setTag("tr", strings(d.trail));
            dl.appendTag(c);
        }
        t.setTag("defects", dl);
        NBTTagList cl = new NBTTagList();
        for (Cert x : certs) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", x.id); c.setString("s", x.scope); c.setString("k", x.kind); c.setString("by", x.by); c.setBoolean("ok", x.pass); c.setLong("at", x.at);
            c.setTag("l", strings(x.lines));
            cl.appendTag(c);
        }
        t.setTag("certs", cl);
        NBTTagList ol = new NBTTagList();
        for (Order o : orders) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", o.id); c.setInteger("sp", o.spare); c.setInteger("q", o.qty); c.setInteger("su", o.supplier); c.setInteger("st", o.state); c.setInteger("bl", o.baseline);
            c.setLong("o", o.ordered); c.setLong("d", o.due); c.setLong("at", o.stores); c.setDouble("c", o.cost); c.setString("w", o.why); c.setString("by", o.by);
            ol.appendTag(c);
        }
        t.setTag("orders", ol);
        t.setTag("log", strings(log));
        return t;
    }
}
