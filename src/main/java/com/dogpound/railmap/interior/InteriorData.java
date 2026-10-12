package com.dogpound.railmap.interior;

import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interior state per rolling stock (keyed by the stock's UUID, shared by every dimension): the fitted layout and its
 * owner, real storage (fridge, racks, tables, display case), the car's fresh-water and waste tanks, galley appliances,
 * switch / lamp states, the till. Cars without an entry simply have no fitted interior (old saves untouched).
 */
public final class InteriorData extends WorldSavedData {
    private static final String NAME = "irextras_interiors";

    /** a part's storage; usable while the player is within 8 blocks of the car */
    public static final class Store extends InventoryBasic {
        public final Car car;
        Store(Car car, String name, int size) { super(name, true, size); this.car = car; }
        @Override public boolean isUsableByPlayer(EntityPlayer p) { return car.pos == null || p.getDistanceSq(car.pos[0], car.pos[1], car.pos[2]) < 12 * 12; }
    }

    public static final class Car {
        public final UUID id;
        /** fitted layout ordinal; -1 = automatic (by the car's name) */
        public int layout = -1;
        public UUID owner;
        public String ownerName = "";
        public final Map<Integer, Store> stores = new HashMap<>();
        public float water = -1, waste, kettleT = 20, kettleL, coffeeT = 20;
        public boolean kettleOn, coffeeOn, cctvOff;
        public int lightMode;            // 0 AUTO, 1 ON, 2 OFF
        public long till, sold;
        /** latched switches / calls: "read12", "call5", "alarm", "bl3", "nl3", "bc3", "wk3", "vent9", "wccord" */
        public final Set<String> on = new HashSet<>();
        public UUID wcLock;
        public String wcLockName = "";
        public int cam;
        /** last known position (for inventory reach) - not saved */
        transient double[] pos;
        transient InteriorData data;

        Car(UUID id) { this.id = id; }

        public Store store(Interior.Part p) {
            if (p == null || p.slots <= 0) return null;
            Store s = stores.get(p.idx);
            if (s == null || s.getSizeInventory() != p.slots) {
                Store n = new Store(this, p.name, p.slots);
                if (s != null) for (int i = 0; i < Math.min(s.getSizeInventory(), n.getSizeInventory()); i++) n.setInventorySlotContents(i, s.getStackInSlot(i));
                n.addInventoryChangeListener(inv -> { if (data != null) data.markDirty(); });
                stores.put(p.idx, s = n);
            }
            return s;
        }

        public boolean toggle(String k) { boolean v = !on.remove(k); if (v) on.add(k); if (data != null) data.markDirty(); return v; }
        public boolean is(String k) { return on.contains(k); }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("layout", layout);
            if (owner != null) t.setUniqueId("owner", owner);
            t.setString("ownerN", ownerName);
            NBTTagCompound inv = new NBTTagCompound();
            for (Map.Entry<Integer, Store> e : stores.entrySet()) {
                NBTTagCompound s = TileFurniture.save(e.getValue());
                s.setInteger("_n", e.getValue().getSizeInventory());
                inv.setTag(String.valueOf(e.getKey()), s);
            }
            t.setTag("inv", inv);
            t.setFloat("water", water); t.setFloat("waste", waste); t.setFloat("kT", kettleT); t.setFloat("kL", kettleL); t.setFloat("cT", coffeeT);
            t.setBoolean("kOn", kettleOn); t.setBoolean("cOn", coffeeOn); t.setBoolean("cctvOff", cctvOff);
            t.setInteger("lm", lightMode); t.setLong("till", till); t.setLong("sold", sold); t.setInteger("cam", cam);
            t.setString("on", String.join(",", on));
            if (wcLock != null) t.setUniqueId("wc", wcLock);
            t.setString("wcN", wcLockName);
            return t;
        }

        void read(NBTTagCompound t) {
            layout = t.hasKey("layout") ? t.getInteger("layout") : -1;
            owner = t.hasUniqueId("owner") ? t.getUniqueId("owner") : null;
            ownerName = t.getString("ownerN");
            NBTTagCompound inv = t.getCompoundTag("inv");
            for (String k : inv.getKeySet()) {
                NBTTagCompound s = inv.getCompoundTag(k);
                Store st = new Store(this, "Storage", Math.max(1, s.getInteger("_n")));
                TileFurniture.load(st, s);
                st.addInventoryChangeListener(x -> { if (data != null) data.markDirty(); });
                stores.put(Integer.parseInt(k), st);
            }
            water = t.hasKey("water") ? t.getFloat("water") : -1; waste = t.getFloat("waste");
            kettleT = t.hasKey("kT") ? t.getFloat("kT") : 20; kettleL = t.getFloat("kL"); coffeeT = t.hasKey("cT") ? t.getFloat("cT") : 20;
            kettleOn = t.getBoolean("kOn"); coffeeOn = t.getBoolean("cOn"); cctvOff = t.getBoolean("cctvOff");
            lightMode = t.getInteger("lm"); till = t.getLong("till"); sold = t.getLong("sold"); cam = t.getInteger("cam");
            on.clear();
            for (String s : t.getString("on").split(",")) if (!s.isEmpty()) on.add(s);
            wcLock = t.hasUniqueId("wc") ? t.getUniqueId("wc") : null;
            wcLockName = t.getString("wcN");
        }
    }

    public final Map<UUID, Car> cars = new HashMap<>();

    public InteriorData() { super(NAME); }
    public InteriorData(String n) { super(n); }

    public static InteriorData get(World w) {
        World w0 = w.getMinecraftServer() == null ? w : w.getMinecraftServer().getWorld(0);
        MapStorage st = w0.getMapStorage();
        InteriorData d = st == null ? null : (InteriorData) st.getOrLoadData(InteriorData.class, NAME);
        if (d == null) { d = new InteriorData(); if (st != null) st.setData(NAME, d); }
        return d;
    }

    public Car peek(UUID id) { return cars.get(id); }

    public Car car(UUID id) {
        Car c = cars.get(id);
        if (c == null) { c = new Car(id); c.data = this; cars.put(id, c); markDirty(); }
        return c;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        cars.clear();
        NBTTagCompound c = t.getCompoundTag("cars");
        for (String k : c.getKeySet()) {
            try {
                Car car = new Car(UUID.fromString(k));
                car.data = this;
                car.read(c.getCompoundTag(k));
                cars.put(car.id, car);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagCompound c = new NBTTagCompound();
        for (Car car : cars.values()) c.setTag(car.id.toString(), car.write());
        t.setTag("cars", c);
        return t;
    }
}
