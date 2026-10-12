package com.dogpound.railmap.stn;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Station services' saved state (irextras_stn.dat): service diagrams per unit (#31), the line demand profile the
 * formation planner works from (#26), parcels in the network (#34) and the lost-property register (#34). A world
 * without this file starts empty - nothing changes until a block is placed.
 */
public final class StnData extends WorldSavedData {
    private static final String NAME = "irextras_stn";

    // ---- #31 service diagrams -----------------------------------------------------------------------------------------
    public static final class Diagram {
        public String code = "";
        public String unit = "";
        /** planned hours (-1 = not set), and whether they are enforced (sent to depot outside them) */
        public int start = -1, end = -1;
        public boolean enforce;
        /** today's workings: "tick|station|ARR/DEP|late seconds|line" */
        public final List<String> work = new ArrayList<>();
        public long day = -1;
        public int trips, calls, lateCalls;
        public long firstDep, lastArr;
        public double lateSum;
        /** yesterday, for the board */
        public int callsY, lateY;
        public boolean offDiagram;
    }

    public final Map<UUID, Diagram> diagrams = new LinkedHashMap<>();
    private int nextDiagram = 1;

    public Diagram diagram(UUID unit, String name, String line) {
        Diagram d = diagrams.get(unit);
        if (d == null) {
            d = new Diagram();
            d.code = String.format(Locale.ROOT, "%d%s%02d", 1 + (nextDiagram - 1) / 99, line == null || line.isEmpty() ? "X" : line.substring(0, 1).toUpperCase(Locale.ROOT), (nextDiagram - 1) % 99 + 1);
            nextDiagram++;
            diagrams.put(unit, d);
            markDirty();
        }
        if (name != null && !name.isEmpty()) d.unit = name;
        return d;
    }

    // ---- #26 demand profile: line -> 24 hourly peak loads (passengers per train, smoothed) ---------------------------
    public final Map<String, double[]> demand = new HashMap<>();

    public double[] demand(String line) { return demand.computeIfAbsent(line, k -> new double[24]); }

    // ---- #34 parcels --------------------------------------------------------------------------------------------------
    public static final String[] PSTATE = {"WAITING", "ON TRAIN", "ARRIVED", "COLLECTED"};

    public static final class Parcel {
        public int id;
        public long from, to, at;           // station keys; at = where it is now (WAITING / ARRIVED)
        public String fromName = "", toName = "", sender = "", what = "";
        public int state;                   // PSTATE
        public UUID train;                  // ON TRAIN
        public String trainName = "";
        public long created, moved;
        public ItemStack stack = ItemStack.EMPTY;   // ON TRAIN: the goods travel in the record
        public final List<String> log = new ArrayList<>();
    }

    public final List<Parcel> parcels = new ArrayList<>();
    private int nextParcel = 1;

    public Parcel newParcel(World w, long from, String fromName, long to, String toName, String sender, ItemStack s) {
        Parcel p = new Parcel();
        p.id = nextParcel++;
        p.from = from; p.to = to; p.at = from; p.fromName = fromName; p.toName = toName; p.sender = sender;
        p.what = s.getCount() + " x " + s.getDisplayName();
        p.created = p.moved = w.getTotalWorldTime();
        p.log.add(w.getTotalWorldTime() + "|consigned at " + fromName + " by " + sender + " for " + toName);
        parcels.add(p);
        while (parcels.size() > 400) {
            boolean removed = false;
            for (Iterator<Parcel> it = parcels.iterator(); it.hasNext(); ) if (it.next().state == 3) { it.remove(); removed = true; break; }
            if (!removed) break;
        }
        markDirty();
        return p;
    }

    public Parcel parcel(int id) { for (Parcel p : parcels) if (p.id == id) return p; return null; }

    // ---- #34 lost property register ------------------------------------------------------------------------------------
    /** "tick|station|item|owner|state" newest first */
    public final List<String> lost = new ArrayList<>();
    private int nextLost = 1;

    public int lostEntry(World w, String station, String item, String owner) {
        int id = nextLost++;
        lost.add(0, id + "|" + w.getTotalWorldTime() + "|" + station + "|" + item + "|" + owner + "|HELD");
        while (lost.size() > 200) lost.remove(lost.size() - 1);
        markDirty();
        return id;
    }

    public void lostState(int id, String state) {
        for (int i = 0; i < lost.size(); i++) {
            String[] p = lost.get(i).split("\\|", -1);
            if (p.length >= 6 && p[0].equals(String.valueOf(id))) { p[5] = state; lost.set(i, String.join("|", p)); markDirty(); return; }
        }
    }

    // ---- plumbing -------------------------------------------------------------------------------------------------------
    public StnData() { super(NAME); }
    public StnData(String n) { super(n); }

    public static StnData get(World w) {
        MapStorage st = w.getPerWorldStorage();
        StnData d = (StnData) st.getOrLoadData(StnData.class, NAME);
        if (d == null) { d = new StnData(); st.setData(NAME, d); }
        return d;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        diagrams.clear(); demand.clear(); parcels.clear(); lost.clear();
        nextDiagram = Math.max(1, t.getInteger("nd"));
        nextParcel = Math.max(1, t.getInteger("np"));
        nextLost = Math.max(1, t.getInteger("nl"));
        NBTTagCompound dg = t.getCompoundTag("diag");
        for (String k : dg.getKeySet()) {
            NBTTagCompound c = dg.getCompoundTag(k);
            Diagram d = new Diagram();
            d.code = c.getString("c"); d.unit = c.getString("u"); d.start = c.hasKey("s") ? c.getInteger("s") : -1; d.end = c.hasKey("e") ? c.getInteger("e") : -1;
            d.enforce = c.getBoolean("en"); d.day = c.getLong("d"); d.trips = c.getInteger("tr"); d.calls = c.getInteger("ca"); d.lateCalls = c.getInteger("lc");
            d.firstDep = c.getLong("fd"); d.lastArr = c.getLong("la"); d.lateSum = c.getDouble("ls"); d.callsY = c.getInteger("cy"); d.lateY = c.getInteger("ly"); d.offDiagram = c.getBoolean("off");
            NBTTagList l = c.getTagList("w", 8);
            for (int i = 0; i < l.tagCount(); i++) d.work.add(l.getStringTagAt(i));
            try { diagrams.put(UUID.fromString(k), d); } catch (IllegalArgumentException ignored) { }
        }
        NBTTagCompound dm = t.getCompoundTag("dem");
        for (String k : dm.getKeySet()) {
            NBTTagList l = dm.getTagList(k, 6);
            double[] a = new double[24];
            for (int i = 0; i < Math.min(24, l.tagCount()); i++) a[i] = l.getDoubleAt(i);
            demand.put(k, a);
        }
        NBTTagList pl = t.getTagList("parcels", 10);
        for (int i = 0; i < pl.tagCount(); i++) {
            NBTTagCompound c = pl.getCompoundTagAt(i);
            Parcel p = new Parcel();
            p.id = c.getInteger("id"); p.from = c.getLong("f"); p.to = c.getLong("t"); p.at = c.getLong("at");
            p.fromName = c.getString("fn"); p.toName = c.getString("tn"); p.sender = c.getString("snd"); p.what = c.getString("wh");
            p.state = c.getInteger("st"); p.train = c.hasUniqueId("tr") ? c.getUniqueId("tr") : null; p.trainName = c.getString("trn");
            p.created = c.getLong("cr"); p.moved = c.getLong("mv");
            p.stack = c.hasKey("item") ? new ItemStack(c.getCompoundTag("item")) : ItemStack.EMPTY;
            NBTTagList l = c.getTagList("log", 8);
            for (int k = 0; k < l.tagCount(); k++) p.log.add(l.getStringTagAt(k));
            parcels.add(p);
        }
        NBTTagList ll = t.getTagList("lost", 8);
        for (int i = 0; i < ll.tagCount(); i++) lost.add(ll.getStringTagAt(i));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("nd", nextDiagram); t.setInteger("np", nextParcel); t.setInteger("nl", nextLost);
        NBTTagCompound dg = new NBTTagCompound();
        for (Map.Entry<UUID, Diagram> e : diagrams.entrySet()) {
            Diagram d = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setString("c", d.code); c.setString("u", d.unit); c.setInteger("s", d.start); c.setInteger("e", d.end); c.setBoolean("en", d.enforce);
            c.setLong("d", d.day); c.setInteger("tr", d.trips); c.setInteger("ca", d.calls); c.setInteger("lc", d.lateCalls);
            c.setLong("fd", d.firstDep); c.setLong("la", d.lastArr); c.setDouble("ls", d.lateSum); c.setInteger("cy", d.callsY); c.setInteger("ly", d.lateY); c.setBoolean("off", d.offDiagram);
            NBTTagList l = new NBTTagList();
            for (String s : d.work) l.appendTag(new NBTTagString(s));
            c.setTag("w", l);
            dg.setTag(e.getKey().toString(), c);
        }
        t.setTag("diag", dg);
        NBTTagCompound dm = new NBTTagCompound();
        for (Map.Entry<String, double[]> e : demand.entrySet()) {
            NBTTagList l = new NBTTagList();
            for (double v : e.getValue()) l.appendTag(new net.minecraft.nbt.NBTTagDouble(v));
            dm.setTag(e.getKey(), l);
        }
        t.setTag("dem", dm);
        NBTTagList pl = new NBTTagList();
        for (Parcel p : parcels) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", p.id); c.setLong("f", p.from); c.setLong("t", p.to); c.setLong("at", p.at);
            c.setString("fn", p.fromName); c.setString("tn", p.toName); c.setString("snd", p.sender); c.setString("wh", p.what);
            c.setInteger("st", p.state); if (p.train != null) c.setUniqueId("tr", p.train); c.setString("trn", p.trainName);
            c.setLong("cr", p.created); c.setLong("mv", p.moved);
            if (!p.stack.isEmpty()) c.setTag("item", p.stack.writeToNBT(new NBTTagCompound()));
            NBTTagList l = new NBTTagList();
            for (String s : p.log) l.appendTag(new NBTTagString(s));
            c.setTag("log", l);
            pl.appendTag(c);
        }
        t.setTag("parcels", pl);
        NBTTagList ll = new NBTTagList();
        for (String s : lost) ll.appendTag(new NBTTagString(s));
        t.setTag("lost", ll);
        return t;
    }

    /** the named station nearest a spot within `reach` blocks: {key, name} or null */
    public static Map.Entry<Long, String> station(World w, BlockPos at, double reach) {
        Map.Entry<Long, String> best = null;
        double bd = reach * reach;
        for (Map.Entry<Long, String> e : com.dogpound.railmap.server.StationData.get(w).names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(at);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }
}
