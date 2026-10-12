package com.dogpound.railmap.newrail6;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Batch 6 #16-20 state in one dimension: cog-tagged locomotives and their pinion wear (rack), the ferry routes with the
 * cars stowed on each ferry's deck, and the road vehicles carried on motorail cars.
 */
public final class NR6Data extends WorldSavedData {
    private static final String NAME = "irextras_newrail6";

    // ---- rack -------------------------------------------------------------------------------------------------------
    /** locomotives fitted with a rack pinion (tagged at a rack panel) */
    public final Set<UUID> cog = new HashSet<>();
    /** pinion wear % per cog locomotive */
    public final Map<UUID, Float> wear = new HashMap<>();

    // ---- ferries ----------------------------------------------------------------------------------------------------
    public static final int BERTHED = 0, DEPARTING = 1, SAILING = 2, ARRIVING = 3;
    public static final String[] PHASE = {"BERTHED", "CASTING OFF", "AT SEA", "BERTHING"};

    public static final class Route {
        public int ch;
        public long dockA, dockB;
        /** berthed at / heading for: 0 = A, 1 = B */
        public int at;
        public int phase = BERTHED;
        /** metres sailed from the departure berth, speed m/s */
        public double s, v;
        public long phaseAt;
        public int voyages;
        public boolean doorOpen;
        public final List<NBTTagCompound> deck = new ArrayList<>();
        /** berths (where the ship lies, on the water) and their outward headings: the docks write them every second */
        public double[][] berth = new double[2][];
        public float[] out = new float[2];
        public transient long[] seen = new long[2];
        /** a dock's stern door / span state (memory only; the dock says it every second) */
        public transient boolean[] seated = new boolean[2];

        public boolean berths() { return berth[0] != null && berth[1] != null; }

        public long dock(int k) { return k == 0 ? dockA : dockB; }
        public boolean paired() { return dockA != 0 && dockB != 0; }

        public void write(NBTTagCompound t) {
            t.setInteger("ch", ch); t.setLong("a", dockA); t.setLong("b", dockB); t.setInteger("at", at); t.setInteger("ph", phase);
            t.setDouble("s", s); t.setDouble("v", v); t.setLong("pa", phaseAt); t.setInteger("voy", voyages); t.setBoolean("door", doorOpen);
            NBTTagList l = new NBTTagList();
            for (NBTTagCompound c : deck) l.appendTag(c.copy());
            t.setTag("deck", l);
            for (int k = 0; k < 2; k++) if (berth[k] != null) { t.setDouble("bx" + k, berth[k][0]); t.setDouble("by" + k, berth[k][1]); t.setDouble("bz" + k, berth[k][2]); t.setFloat("out" + k, out[k]); }
        }

        public void read(NBTTagCompound t) {
            ch = t.getInteger("ch"); dockA = t.getLong("a"); dockB = t.getLong("b"); at = t.getInteger("at"); phase = t.getInteger("ph");
            s = t.getDouble("s"); v = t.getDouble("v"); phaseAt = t.getLong("pa"); voyages = t.getInteger("voy"); doorOpen = t.getBoolean("door");
            deck.clear();
            NBTTagList l = t.getTagList("deck", 10);
            for (int i = 0; i < l.tagCount(); i++) deck.add(l.getCompoundTagAt(i));
            for (int k = 0; k < 2; k++) if (t.hasKey("bx" + k)) { berth[k] = new double[]{t.getDouble("bx" + k), t.getDouble("by" + k), t.getDouble("bz" + k)}; out[k] = t.getFloat("out" + k); }
        }
    }

    public final Map<Integer, Route> routes = new TreeMap<>();

    // ---- motorail ---------------------------------------------------------------------------------------------------
    public static final class Load {
        /** the vehicle entity as saved (with its "id"), and what to call it */
        public NBTTagCompound veh = new NBTTagCompound();
        public String label = "";
        public long at, ramp;

        void write(NBTTagCompound t) { t.setTag("veh", veh); t.setString("label", label); t.setLong("at", at); t.setLong("ramp", ramp); }
        void read(NBTTagCompound t) { veh = t.getCompoundTag("veh"); label = t.getString("label"); at = t.getLong("at"); ramp = t.getLong("ramp"); }
    }

    /** IR car UUID -> the vehicle chained down on it */
    public final Map<UUID, Load> loads = new LinkedHashMap<>();

    public int version;

    public NR6Data() { super(NAME); }
    public NR6Data(String n) { super(n); }

    public static NR6Data get(World w) {
        MapStorage st = w.getPerWorldStorage();
        NR6Data d = (NR6Data) st.getOrLoadData(NR6Data.class, NAME);
        if (d == null) { d = new NR6Data(); st.setData(NAME, d); }
        return d;
    }

    public void changed() { version++; markDirty(); }

    public Route route(int ch) { return routes.computeIfAbsent(ch, k -> { Route r = new Route(); r.ch = k; return r; }); }

    public float wear(UUID u) { return wear.getOrDefault(u, 0f); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        cog.clear(); wear.clear(); routes.clear(); loads.clear();
        NBTTagList c = t.getTagList("cog", 10);
        for (int i = 0; i < c.tagCount(); i++) {
            NBTTagCompound x = c.getCompoundTagAt(i);
            UUID u = x.getUniqueId("u");
            if (u == null) continue;
            if (x.getBoolean("tag")) cog.add(u);
            wear.put(u, x.getFloat("wear"));
        }
        NBTTagList r = t.getTagList("routes", 10);
        for (int i = 0; i < r.tagCount(); i++) { Route x = new Route(); x.read(r.getCompoundTagAt(i)); routes.put(x.ch, x); }
        NBTTagList l = t.getTagList("loads", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound x = l.getCompoundTagAt(i);
            UUID u = x.getUniqueId("car");
            if (u == null) continue;
            Load ld = new Load();
            ld.read(x);
            loads.put(u, ld);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList c = new NBTTagList();
        Set<UUID> all = new HashSet<>(cog);
        all.addAll(wear.keySet());
        for (UUID u : all) {
            NBTTagCompound x = new NBTTagCompound();
            x.setUniqueId("u", u);
            x.setBoolean("tag", cog.contains(u));
            x.setFloat("wear", wear(u));
            c.appendTag(x);
        }
        t.setTag("cog", c);
        NBTTagList r = new NBTTagList();
        for (Route x : routes.values()) { NBTTagCompound y = new NBTTagCompound(); x.write(y); r.appendTag(y); }
        t.setTag("routes", r);
        NBTTagList l = new NBTTagList();
        for (Map.Entry<UUID, Load> e : loads.entrySet()) {
            NBTTagCompound x = new NBTTagCompound();
            x.setUniqueId("car", e.getKey());
            e.getValue().write(x);
            l.appendTag(x);
        }
        t.setTag("loads", l);
        return t;
    }
}
