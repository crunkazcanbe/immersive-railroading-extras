package com.dogpound.railmap.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

/**
 * Universal transport adapter API (her ChatGPT list #96): Immersive Railroading trains, Traincraft trains and PrideCarts
 * minecart trains all reach the railway logic (program conditions, logic modules, telemetry, the export, the console's
 * API tab, train orders) through one interface. Each mod is one {@link Adapter}; a missing mod only switches its adapter
 * off. Other mods add theirs with {@link #register(Adapter)} (or RailApi.registerTransport).
 */
public final class Transport {
    private Transport() {}

    /** one train (or single vehicle) as every adapter reports it */
    public static final class Vehicle {
        public String id = "", adapter = "", name = "", kind = "train", destination = "", line = "", status = "";
        public UUID uuid;
        public int entityId = -1, cars = 1, cargo, fillPct = -1;
        public double x, y, z, speedKmh;
        public boolean driverless;
        /** registry names of what is aboard (items and fluids), for cargo-type checks */
        public final Set<String> cargoTypes = new TreeSet<>();

        public Map<String, Object> map() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("adapter", adapter); m.put("name", name); m.put("kind", kind);
            m.put("x", Math.round(x * 10) / 10.0); m.put("y", Math.round(y * 10) / 10.0); m.put("z", Math.round(z * 10) / 10.0);
            m.put("speedKmh", Math.round(speedKmh * 10) / 10.0); m.put("cars", cars); m.put("cargo", cargo); m.put("fillPct", fillPct);
            m.put("destination", destination); m.put("line", line); m.put("driverless", driverless); m.put("status", status);
            m.put("cargoTypes", new ArrayList<>(cargoTypes));
            if (uuid != null) m.put("uuid", uuid.toString());
            return m;
        }

        public boolean near(BlockPos p, double r) {
            double dx = x - p.getX() - 0.5, dz = z - p.getZ() - 0.5;
            return dx * dx + dz * dz <= r * r && Math.abs(y - p.getY()) <= 8;
        }
    }

    public interface Adapter {
        /** short id used in vehicle ids ("ir", "tc", "pridecarts") */
        String id();
        /** the mod it needs (null = none) */
        String modId();
        String label();
        /** every loaded train it can see in this world */
        List<Vehicle> vehicles(World w);
        /** commands it understands, for the diagnostics page */
        default List<String> commands() { return Collections.emptyList(); }
        /** carry out a command on one of its vehicles; returns what happened */
        default String command(World w, Vehicle v, String cmd, String arg) { return "refused: " + label() + " takes no commands"; }
    }

    private static final List<Adapter> ADAPTERS = new ArrayList<>();
    /** last error per adapter (diagnostics); a failing adapter is skipped, never fatal */
    static final Map<String, String> ERRORS = new HashMap<>();
    static final Map<String, Integer> SEEN = new HashMap<>();

    static {
        register(new IrAdapter());
        register(new PrideCartsAdapter());
        if (Loader.isModLoaded("tc")) {
            try { register((Adapter) Class.forName("com.dogpound.railmap.api.TraincraftAdapter").getConstructor().newInstance()); }
            catch (ReflectiveOperationException | LinkageError e) { ERRORS.put("tc", "did not link: " + e); }
        }
    }

    public static synchronized void register(Adapter a) {
        ADAPTERS.removeIf(x -> x.id().equals(a.id()));
        ADAPTERS.add(a);
    }

    public static synchronized List<Adapter> adapters() { return new ArrayList<>(ADAPTERS); }

    public static boolean active(Adapter a) { return a.modId() == null || Loader.isModLoaded(a.modId()); }

    /** every vehicle from every active adapter */
    public static List<Vehicle> vehicles(World w) {
        List<Vehicle> out = new ArrayList<>();
        for (Adapter a : adapters()) {
            if (!active(a)) continue;
            try {
                List<Vehicle> l = a.vehicles(w);
                out.addAll(l);
                SEEN.put(a.id(), l.size());
                ERRORS.remove(a.id());
            } catch (RuntimeException | LinkageError e) {
                ERRORS.put(a.id(), e.toString());
            }
        }
        return out;
    }

    public static Vehicle find(World w, String idOrName) {
        for (Vehicle v : vehicles(w)) if (v.id.equalsIgnoreCase(idOrName) || v.name.equalsIgnoreCase(idOrName) || v.uuid != null && v.uuid.toString().equals(idOrName)) return v;
        return null;
    }

    public static String command(World w, String vehicle, String cmd, String arg, Audit.Src src, String who) {
        Vehicle v = find(w, vehicle);
        if (v == null) return "refused: no loaded vehicle '" + vehicle + "'";
        Adapter a = null;
        for (Adapter x : adapters()) if (x.id().equals(v.adapter)) a = x;
        if (a == null || !active(a)) return "refused: adapter " + v.adapter + " is not active";
        String before = v.speedKmh < 1 ? "stopped" : Math.round(v.speedKmh) + " km/h";
        String r;
        try { r = a.command(w, v, cmd.toLowerCase(Locale.ROOT), arg == null ? "" : arg); }
        catch (RuntimeException | LinkageError e) { r = "refused: " + e; }
        Audit.log(w, src, who, new BlockPos(v.x, v.y, v.z), v.adapter + " " + v.name, cmd + (arg == null || arg.isEmpty() ? "" : " " + arg), before, v.destination, r);
        return r;
    }

    /** any vehicle (any adapter) within r blocks of p - what the program's TRAIN NEAR condition asks */
    public static boolean near(World w, BlockPos p, double r, boolean stopped) {
        for (Vehicle v : vehicles(w)) if (v.near(p, r) && (!stopped || v.speedKmh < 2)) return true;
        return false;
    }

    static void cargoOf(Entity e, Vehicle v) {
        if (e == null) return;
        if (e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            IItemHandler h = e.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (h != null) for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty() || s.getItem().getRegistryName() == null) continue;
                v.cargo += s.getCount();
                v.cargoTypes.add(s.getItem().getRegistryName().toString());
            }
        }
        if (e.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
            IFluidHandler f = e.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
            if (f != null) for (IFluidTankProperties t : f.getTankProperties()) if (t.getContents() != null && t.getContents().amount > 0) {
                v.cargo += t.getContents().amount / 1000;
                v.cargoTypes.add("fluid:" + t.getContents().getFluid().getName());
            }
        }
    }

    // ---- Immersive Railroading -------------------------------------------------------------------------------------
    static final class IrAdapter implements Adapter {
        @Override public String id() { return "ir"; }
        @Override public String modId() { return "immersiverailroading"; }
        @Override public String label() { return "Immersive Railroading"; }
        @Override public List<String> commands() { return Arrays.asList("send <station>", "stop", "throttle_up", "throttle_down", "brake_up", "brake_down", "forward", "neutral", "reverse", "horn", "bell", "emergency_stop"); }

        @Override
        public List<Vehicle> vehicles(World w) {
            List<Vehicle> out = new ArrayList<>();
            cam72cam.mod.world.World u = cam72cam.mod.world.World.get(w);
            if (u == null) return out;
            com.dogpound.railmap.auto.RailwayData data = com.dogpound.railmap.auto.RailwayData.get(w);
            Set<UUID> done = new HashSet<>();
            for (cam72cam.immersiverailroading.entity.EntityRollingStock s : u.getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                if (done.contains(s.getUUID())) continue;
                List<cam72cam.immersiverailroading.entity.EntityRollingStock> cars = new ArrayList<>();
                if (s instanceof cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock c) cars.addAll(c.getTrain()); else cars.add(s);
                cam72cam.immersiverailroading.entity.EntityRollingStock head = s;
                for (cam72cam.immersiverailroading.entity.EntityRollingStock c : cars) { done.add(c.getUUID()); if (c instanceof cam72cam.immersiverailroading.entity.Locomotive && !(head instanceof cam72cam.immersiverailroading.entity.Locomotive)) head = c; }
                Vehicle v = new Vehicle();
                v.adapter = "ir"; v.entityId = head.getId(); v.id = "ir:" + head.getId(); v.uuid = head.getUUID(); v.cars = cars.size();
                v.kind = head instanceof cam72cam.immersiverailroading.entity.Locomotive ? "train" : "cars";
                v.name = head.tag != null && !head.tag.isEmpty() ? head.tag : head.getDefinition() == null ? "Train" : head.getDefinition().name();
                cam72cam.mod.math.Vec3d p = head.getPosition();
                v.x = p.x; v.y = p.y; v.z = p.z;
                if (head instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && m.getCurrentSpeed() != null) v.speedKmh = Math.abs(m.getCurrentSpeed().metric());
                for (cam72cam.immersiverailroading.entity.EntityRollingStock c : cars) if (c != head || !(c instanceof cam72cam.immersiverailroading.entity.Locomotive)) cargoOf(c.internal, v);
                if (head instanceof cam72cam.immersiverailroading.entity.Locomotive l) v.fillPct = com.dogpound.railmap.auto.Autopilot.fillPercent(l);
                com.dogpound.railmap.auto.RailwayData.AutoTrain a = data.train(head.getUUID());
                if (a != null) {
                    v.driverless = true; v.line = a.line == null ? "" : a.line;
                    if (!a.label.isEmpty()) v.name = a.label;
                    com.dogpound.railmap.auto.Autopilot.Run r = com.dogpound.railmap.auto.Autopilot.run(a.loco);
                    if (r != null) { v.status = r.status; v.destination = r.nextStopName; }
                }
                out.add(v);
            }
            return out;
        }

        @Override
        public String command(World w, Vehicle v, String cmd, String arg) {
            if (cmd.equals("send")) {
                Long key = null;
                for (Map.Entry<Long, String> e : com.dogpound.railmap.server.StationData.get(w).names().entrySet()) if (e.getValue().equalsIgnoreCase(arg.trim())) key = e.getKey();
                if (key == null) return "refused: no station '" + arg + "'";
                if (v.kind.equals("cars")) return "refused: no locomotive in that consist";
                com.dogpound.railmap.auto.RailwayData data = com.dogpound.railmap.auto.RailwayData.get(w);
                com.dogpound.railmap.auto.RailwayData.AutoTrain t = data.setTrain(v.uuid);
                t.mode = com.dogpound.railmap.auto.RailwayData.Mode.SEND; t.home = key; t.calls.clear();
                if (t.label.isEmpty()) t.label = v.name;
                data.markDirty();
                return "ok: " + v.name + " sent to " + arg;
            }
            String c = cmd.toUpperCase(Locale.ROOT);
            if (c.equals("STOP")) c = "EMERGENCY_STOP";
            if (c.equals("FORWARD") || c.equals("NEUTRAL") || c.equals("REVERSE")) c = "REVERSER_" + c;
            com.dogpound.railmap.server.TrainControl.Cmd k;
            try { k = com.dogpound.railmap.server.TrainControl.Cmd.valueOf(c); } catch (IllegalArgumentException e) { return "refused: unknown command " + cmd; }
            String r = com.dogpound.railmap.server.TrainControl.apply(w, null, v.entityId, k);
            return r == null ? "ok" : r;
        }
    }

    // ---- PrideCarts (minecart trains: TrainCarts-style signs, names, destinations) ---------------------------------
    static final class PrideCartsAdapter implements Adapter {
        private java.lang.reflect.Method of, key, stop;
        private java.lang.reflect.Field carts;
        private boolean tried;

        @Override public String id() { return "pridecarts"; }
        @Override public String modId() { return "pridecarts"; }
        @Override public String label() { return "PrideCarts minecart trains"; }
        @Override public List<String> commands() { return Arrays.asList("destination <name>", "stop", "name <text>"); }

        private void link() {
            if (tried) return;
            tried = true;
            try {
                Class<?> t = Class.forName("com.dogpound.pridecarts.Train");
                of = t.getMethod("of", EntityMinecart.class); key = t.getMethod("key"); stop = t.getMethod("stop"); carts = t.getField("carts");
            } catch (ReflectiveOperationException | LinkageError e) {
                ERRORS.put("pridecarts", "Train API not found, carts listed one by one: " + e);
            }
        }

        private static boolean foreign(Entity e) {
            String n = e.getClass().getName();
            return n.startsWith("train.") || n.startsWith("cam72cam.");                // Traincraft / IR report themselves
        }

        @Override
        public List<Vehicle> vehicles(World w) {
            link();
            List<Vehicle> out = new ArrayList<>();
            Set<Integer> done = new HashSet<>();
            for (Entity e : new ArrayList<>(w.loadedEntityList)) {
                if (!(e instanceof EntityMinecart c) || e.isDead || foreign(e) || done.contains(e.getEntityId())) continue;
                List<EntityMinecart> train = Collections.singletonList(c);
                if (of != null) {
                    try {
                        Object t = of.invoke(null, c);
                        @SuppressWarnings("unchecked") List<EntityMinecart> l = (List<EntityMinecart>) carts.get(t);
                        train = l;
                    } catch (ReflectiveOperationException | RuntimeException ex) { ERRORS.put("pridecarts", ex.toString()); }
                }
                for (EntityMinecart x : train) done.add(x.getEntityId());
                EntityMinecart head = train.get(0);
                net.minecraft.nbt.NBTTagCompound tag = head.getEntityData().getCompoundTag("PrideCarts");
                Vehicle v = new Vehicle();
                v.adapter = "pridecarts"; v.entityId = head.getEntityId(); v.id = "pridecarts:" + head.getEntityId(); v.uuid = head.getUniqueID();
                v.cars = train.size(); v.kind = train.size() > 1 ? "train" : "cart";
                v.name = tag.getString("name").isEmpty() ? head.getName() : tag.getString("name");
                v.destination = tag.getString("destination");
                v.line = tag.getString("route");
                v.x = head.posX; v.y = head.posY; v.z = head.posZ;
                v.speedKmh = Math.sqrt(head.motionX * head.motionX + head.motionZ * head.motionZ) * 20 * 3.6;
                for (EntityMinecart x : train) cargoOf(x, v);
                v.status = tag.hasKey("hold") ? "held at a sign" : v.speedKmh < 0.5 ? "stopped" : "rolling";
                out.add(v);
            }
            return out;
        }

        @Override
        public String command(World w, Vehicle v, String cmd, String arg) {
            Entity e = w.getEntityByID(v.entityId);
            if (!(e instanceof EntityMinecart c)) return "refused: cart unloaded";
            List<EntityMinecart> train = Collections.singletonList(c);
            Object t = null;
            link();
            if (of != null) try { t = of.invoke(null, c); @SuppressWarnings("unchecked") List<EntityMinecart> l = (List<EntityMinecart>) carts.get(t); train = l; } catch (ReflectiveOperationException ignored) { }
            switch (cmd) {
                case "destination": case "name":
                    for (EntityMinecart x : train) {
                        net.minecraft.nbt.NBTTagCompound root = x.getEntityData();
                        if (!root.hasKey("PrideCarts")) root.setTag("PrideCarts", new net.minecraft.nbt.NBTTagCompound());
                        if (arg.isEmpty()) root.getCompoundTag("PrideCarts").removeTag(cmd); else root.getCompoundTag("PrideCarts").setString(cmd, arg);
                    }
                    return "ok: " + cmd + " = " + (arg.isEmpty() ? "(none)" : arg);
                case "stop":
                    if (t != null && stop != null) { try { stop.invoke(t); return "ok: stopped"; } catch (ReflectiveOperationException ex) { return "refused: " + ex; } }
                    for (EntityMinecart x : train) { x.motionX = 0; x.motionZ = 0; }
                    return "ok: stopped";
                default: return "refused: unknown command " + cmd;
            }
        }
    }
}
