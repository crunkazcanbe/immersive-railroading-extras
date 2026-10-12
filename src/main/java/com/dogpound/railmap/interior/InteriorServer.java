package com.dogpound.railmap.interior;

import cam72cam.immersiverailroading.entity.CarPassenger;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRidableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.sound.PackSounds;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server side of walk-in interiors: works out which cars have one, runs their appliances (kettle, espresso
 * boiler, fresh-water and waste tanks), the toilet lock, berths and sleeping, passenger alarms / help calls, the café
 * till (Pride coins), the saloon CCTV camera and the next-stop screens (autopilot + PIS), and sends nearby players
 * what each interior shows (only when it changed).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class InteriorServer {
    private InteriorServer() {}

    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    private static final Map<UUID, Map<Integer, Integer>> SENT = new HashMap<>();
    /** berth sleepers: player -> world tick they fell asleep */
    static final Map<UUID, Long> ASLEEP = new HashMap<>();
    private static final Map<UUID, String> ANNOUNCED = new HashMap<>();
    private static final Map<UUID, Long> PAID = new HashMap<>();
    private static final Map<String, Long> WAKE_DAY = new HashMap<>();

    // ---- which cars, which layout -------------------------------------------------------------------------------
    public static boolean fittable(EntityRollingStock s) { return s instanceof CarPassenger && s.getDefinition() != null; }

    public static Interior.Layout layoutOf(EntityRollingStock s, InteriorData.Car c) {
        if (!fittable(s)) return Interior.Layout.NONE;
        if (c != null && c.layout >= 0) return Interior.Layout.of(c.layout);
        return InteriorConfig.autoByName ? Interior.byName(s) : Interior.Layout.NONE;
    }

    public static boolean powered(EntityRollingStock s) {
        try { return s.hasElectricalPower(); } catch (RuntimeException e) { return false; }
    }

    static double speed(EntityRollingStock s) {
        return s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null ? Math.abs(m.getCurrentSpeed().metric()) : 0;
    }

    static boolean lit(World w, EntityRollingStock s, InteriorData.Car c) {
        if (!powered(s)) return false;
        int mode = c == null ? 0 : c.lightMode;
        if (mode == 1) return true;
        if (mode == 2) return false;
        cam72cam.mod.math.Vec3d p = s.getPosition();
        BlockPos b = new BlockPos(p.x, p.y + 2, p.z);
        return !w.isDaytime() || (w.isBlockLoaded(b) && w.getLightFor(EnumSkyBlock.SKY, b) < 10);
    }

    static List<EntityRollingStock> stock(World w) {
        try { return cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class); } catch (RuntimeException e) { return new ArrayList<>(); }
    }

    static EntityRollingStock byEntityId(World w, int id) {
        for (EntityRollingStock s : stock(w)) if (s.internal.getEntityId() == id) return s;
        return null;
    }

    public static EntityRollingStock byUuid(World w, UUID id) {
        try { return cam72cam.mod.world.World.get(w).getEntity(id, EntityRollingStock.class); } catch (RuntimeException e) { return null; }
    }

    static String carName(EntityRollingStock s) {
        try { return s.getDefinition().name(); } catch (RuntimeException e) { return "car"; }
    }

    static double ambient(World w, EntityRollingStock s) {
        cam72cam.mod.math.Vec3d p = s.getPosition();
        BlockPos b = new BlockPos(p.x, p.y, p.z);
        float t = w.getBiome(b).getTemperature(b);
        return Math.round(t * 25 - 3 - (w.isDaytime() ? 0 : 4));
    }

    // ---- ticking --------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || !IR) return;
        World w = e.world;
        long now = w.getTotalWorldTime();
        boolean work = now % 5 == 2, sync = now % InteriorConfig.syncTicks == 1 && !w.playerEntities.isEmpty(), sleep = now % 20 == 7;
        if (!work && !sync && !sleep) return;
        try {
            tick(w, now, work, sync, sleep);
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.debug("[IR Extras] interiors: {}", ex.toString());
        }
    }

    private static void tick(World w, long now, boolean work, boolean sync, boolean sleep) {
        InteriorData data = InteriorData.get(w);
        Map<Integer, PacketInteriorState> built = new HashMap<>();
        Map<Integer, double[]> where = new HashMap<>();
        Set<UUID> inBerth = new HashSet<>();
        for (EntityRollingStock s : stock(w)) {
            if (s.isDead() || !fittable(s)) continue;
            InteriorData.Car c = data.peek(s.getUUID());
            Interior.Layout l = layoutOf(s, c);
            if (l == Interior.Layout.NONE) continue;
            Interior.Plan p = Interior.plan(s, l);
            if (p == null) continue;
            cam72cam.mod.math.Vec3d pos = s.getPosition();
            if (c != null) { c.pos = new double[]{pos.x, pos.y, pos.z}; if (c.water < 0) c.water = InteriorConfig.waterTankLitres; }
            if (work && c != null) appliances(w, s, c, 0.25f);
            if (sleep) {
                if (c != null) wcCheck(w, s, c, p);
                berths(w, s, c, p, now, inBerth);
                arrival(w, s);
            }
            if (sync && anyNear(w, pos)) {
                built.put(s.internal.getEntityId(), state(w, s, c, p, l, now));
                where.put(s.internal.getEntityId(), new double[]{pos.x, pos.y, pos.z});
            }
        }
        if (sleep) sleepers(w, now, inBerth);
        if (sync) send(w, now, built, where);
    }

    private static boolean anyNear(World w, cam72cam.mod.math.Vec3d p) {
        double r = InteriorConfig.renderRange + 16;
        for (EntityPlayer pl : w.playerEntities) if (pl.getDistanceSq(p.x, p.y, p.z) < r * r) return true;
        return false;
    }

    // ---- appliances: kettle, espresso boiler, fridge ------------------------------------------------------------
    private static void appliances(World w, EntityRollingStock s, InteriorData.Car c, float dt) {
        boolean pw = powered(s);
        if (c.kettleOn && pw && c.kettleL > 0.1f) {
            c.kettleT = Math.min(100, c.kettleT + 4.5f * dt);
            if (c.kettleT >= 100) {
                c.kettleOn = false;
                sound(w, s, ModSounds.RELAY, 0.6f, 1.4f);
                sound(w, s, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.25f, 1.6f);
            }
        } else {
            if (c.kettleOn && (!pw || c.kettleL <= 0.1f)) c.kettleOn = false;       // no power / boiled dry: the thermostat cuts out
            c.kettleT = Math.max(20, c.kettleT - 0.06f * dt);
        }
        if (c.coffeeOn && pw && c.water >= 0.05f) {
            float t0 = c.coffeeT;
            c.coffeeT = Math.min(121, c.coffeeT + 3f * dt);
            if (t0 < 95 && c.coffeeT >= 95) sound(w, s, ModSounds.RELAY, 0.5f, 1.1f);
            if (c.coffeeT >= 121 && w.rand.nextInt(20) == 0) sound(w, s, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.15f, 1.8f);   // the pressurestat vents
        } else c.coffeeT = Math.max(20, c.coffeeT - 0.12f * dt);
        if (c.data != null) c.data.markDirty();
    }

    static void sound(World w, EntityRollingStock s, SoundEvent ev, float vol, float pitch) {
        cam72cam.mod.math.Vec3d p = s.getPosition();
        w.playSound(null, p.x, p.y + 1.5, p.z, ev, SoundCategory.NEUTRAL, vol, pitch);
    }

    static void pack(World w, EntityRollingStock s, String id, float vol) {
        cam72cam.mod.math.Vec3d p = s.getPosition();
        PackSounds.play(w, p.x, p.y + 1.5, p.z, id, vol, PackSounds.vary(w));
    }

    /** the toilet lock lets go when whoever locked it isn't in the cubicle any more */
    private static void wcCheck(World w, EntityRollingStock s, InteriorData.Car c, Interior.Plan p) {
        if (c.wcLock == null) return;
        EntityPlayer pl = w.getPlayerEntityByUUID(c.wcLock);
        if (pl != null) {
            double[] lo = Interior.toLocal(s, pl.posX, pl.posY, pl.posZ);
            if (p.inWc(lo[0], lo[1], lo[2])) return;
        }
        c.wcLock = null; c.wcLockName = "";
        pack(w, s, "signal.points_lock", 0.4f);
        c.data.markDirty();
    }

    /** next stop: an in-car chime + announcement when the train gets within 600 m of it */
    private static void arrival(World w, EntityRollingStock s) {
        EntityRollingStock h = com.dogpound.railmap.signs.SignServer.head(s);
        Autopilot.Run r = Autopilot.run(h.getUUID());
        if (r == null || r.nextStopName == null || r.nextStopName.isEmpty()) return;
        if (r.metresToStop < 0 || r.metresToStop > 600) return;
        if (r.nextStopName.equals(ANNOUNCED.put(s.getUUID(), r.nextStopName))) return;
        pack(w, s, "station.chime_two_tone", 0.7f);
        for (EntityPlayer pl : riders(w, s)) pl.sendStatusMessage(new TextComponentString("§b[" + carName(s) + "] §fWe are now approaching §e" + r.nextStopName + "§f."), false);
    }

    static List<EntityPlayer> riders(World w, EntityRollingStock s) {
        List<EntityPlayer> out = new ArrayList<>();
        cam72cam.mod.math.Vec3d p = s.getPosition();
        double half = s.getDefinition().getLength(s.gauge) / 2 + 0.5, hw = s.getDefinition().getWidth(s.gauge) / 2 + 0.5;
        for (EntityPlayer pl : w.playerEntities) {
            double[] lo = Interior.toLocal(s, pl.posX, pl.posY, pl.posZ);
            if (Math.abs(lo[2]) <= half && Math.abs(lo[0]) <= hw && lo[1] > -1 && lo[1] < 5) out.add(pl);
        }
        return out;
    }

    // ---- berths + sleeping ------------------------------------------------------------------------------------
    static boolean night(World w) { long t = w.getWorldTime() % 24000; return t >= 12541 && t <= 23458 || w.isThundering(); }

    private static Map<String, UUID> seated(EntityRollingStock s) {
        try { return s instanceof EntityRidableRollingStock r ? r.prideSeated() : new HashMap<>(); } catch (LinkageError e) { return new HashMap<>(); }
    }

    private static void berths(World w, EntityRollingStock s, InteriorData.Car c, Interior.Plan p, long now, Set<UUID> inBerth) {
        if (p.berths == 0) return;
        Map<String, UUID> seated = seated(s);
        long tod = w.getWorldTime() % 24000, day = w.getWorldTime() / 24000;
        for (Interior.Part b : p.of(Interior.Kind.BERTH)) {
            UUID who = seated.get(b.seatKey());
            if (who == null) continue;
            inBerth.add(who);
            if (night(w)) ASLEEP.putIfAbsent(who, now);
            // wake-up call at 06:00 (tick 0 of the day)
            if (c != null && c.is("wk" + b.no) && tod < 400) {
                String k = s.getUUID() + "|" + b.no;
                Long last = WAKE_DAY.get(k);
                if (last == null || last != day) {
                    WAKE_DAY.put(k, day);
                    EntityPlayer pl = w.getPlayerEntityByUUID(who);
                    if (pl instanceof EntityPlayerMP mp) {
                        PackSounds.play(w, pl.posX, pl.posY, pl.posZ, "station.chime_pling", 0.8f, 1.2f);
                        mp.sendStatusMessage(new TextComponentString("§eGood morning! §7Your 06:00 wake-up call, berth " + b.no + "."), false);
                    }
                }
            }
        }
    }

    /** skip the night when everyone is asleep (bed or berth), otherwise berths just restore you */
    private static void sleepers(World w, long now, Set<UUID> inBerth) {
        for (EntityPlayer pl : w.playerEntities) if (!inBerth.contains(pl.getUniqueID()) || !night(w)) ASLEEP.remove(pl.getUniqueID());
        if (w.getMinecraftServer() != null) ASLEEP.keySet().removeIf(u -> w.getMinecraftServer().getPlayerList().getPlayerByUUID(u) == null);
        boolean restore = now % 100 < 20;
        for (UUID u : inBerth) {
            EntityPlayer pl = w.getPlayerEntityByUUID(u);
            if (pl != null && restore && InteriorConfig.berthHeal > 0 && pl.getHealth() < pl.getMaxHealth()) pl.heal(InteriorConfig.berthHeal);
        }
        if (!InteriorConfig.berthSkipsNight || ASLEEP.isEmpty() || !night(w) || !w.getGameRules().getBoolean("doDaylightCycle")) return;
        long need = InteriorConfig.berthSleepSeconds * 20L;
        int awake = 0;
        for (EntityPlayer pl : w.playerEntities) {
            if (pl.isSpectator()) continue;
            Long since = ASLEEP.get(pl.getUniqueID());
            if (pl.isPlayerFullyAsleep() || since != null && now - since >= need) continue;
            awake++;
        }
        if (awake > 0) return;
        long t = w.getWorldTime() + 24000L;
        w.setWorldTime(t - t % 24000L);
        if (w.getGameRules().getBoolean("doWeatherCycle")) w.provider.resetRainAndThunder();
        for (EntityPlayer pl : w.playerEntities) if (ASLEEP.containsKey(pl.getUniqueID())) pl.sendStatusMessage(new TextComponentString("§7You sleep soundly to the rhythm of the rails... §eit's morning."), false);
        ASLEEP.clear();
        Recorder.event(w, "Night passed - everyone asleep (beds / sleeper berths)");
    }

    // ---- what the interior shows ------------------------------------------------------------------------------
    static PacketInteriorState state(World w, EntityRollingStock s, InteriorData.Car c, Interior.Plan p, Interior.Layout l, long now) {
        PacketInteriorState m = new PacketInteriorState();
        m.entity = s.internal.getEntityId();
        m.layout = l.ordinal();
        m.powered = powered(s);
        m.lit = lit(w, s, c);
        Map<String, Integer> v = m.vals;
        Map<String, String> t = m.txt;
        float water = c == null || c.water < 0 ? InteriorConfig.waterTankLitres : c.water;
        float waste = c == null ? 0 : c.waste;
        if (m.powered) v.put("pwr", 1);
        if (m.lit) v.put("lit", 1);
        int mode = c == null ? 0 : c.lightMode;
        v.put("litmode", mode);
        boolean calls = false;
        if (c != null) for (String k : c.on) { v.put(k, 1); if (k.startsWith("call") || k.startsWith("bc")) calls = true; }
        if (calls) v.put("calls", 1);
        if (c != null && (c.is("alarm") || c.is("wccord"))) v.put("alarm", 1);
        if (c != null && c.wcLock != null) v.put("wcocc", 1);
        if (water < InteriorConfig.waterTankLitres * 0.15f) v.put("waterlow", 1);
        if (waste >= 95) v.put("wastefull", 1);
        boolean cam = c != null && c.cam > 0 && !c.cctvOff;
        if (cam) v.put("cctv", 1);
        if (cam && m.powered) v.put("cctvrec", 1);
        if (!m.powered || waste >= 100) v.put("wcout", 1);
        if (c != null) {
            if (c.kettleOn) v.put("kon", 1);
            if (c.coffeeOn) v.put("con", 1);
            if (c.coffeeOn && c.coffeeT >= 95) v.put("cready", 1);
            if (c.coffeeOn && c.coffeeT < 95 && m.powered) v.put("cheat", 1);
            Long paid = PAID.get(s.getUUID());
            if (paid != null && now - paid < 40) v.put("paid", 1);
        }
        if (water < 1) v.put("cnowater", 1);
        Map<String, UUID> seated = seated(s);
        // text
        EntityRollingStock h = com.dogpound.railmap.signs.SignServer.head(s);
        Autopilot.Run r = Autopilot.run(h.getUUID());
        String next = r == null || r.nextStopName == null ? "" : r.nextStopName;
        String clock = clock(w);
        StringBuilder pis = new StringBuilder();
        if (next.isEmpty()) pis.append(carName(s)).append('\n').append("Welcome aboard").append('\n').append(clock);
        else {
            boolean near = r.metresToStop >= 0 && r.metresToStop < 600;
            pis.append(near ? "NOW APPROACHING" : "NEXT STOP").append('\n').append(next).append('\n');
            String head = "";
            try { cam72cam.mod.math.Vec3d p0 = s.getPosition(); head = com.dogpound.railmap.station.Pis.headline(w, new BlockPos(p0.x, p0.y, p0.z), next); } catch (RuntimeException | LinkageError ignored) { }
            pis.append(head.isEmpty() ? clock + (r.metresToStop >= 0 ? String.format(Locale.ROOT, "  %.1f km", r.metresToStop / 1000) : "") + String.format(Locale.ROOT, "  %d km/h", Math.round(speed(s))) : head);
        }
        t.put("pis", pis.toString());
        int occ = 0;
        for (Interior.Part pt : p.parts) if (pt.kind == Interior.Kind.SEAT || pt.kind == Interior.Kind.BERTH) {
            UUID who = seated.get(pt.seatKey());
            if (who == null) { if (pt.kind == Interior.Kind.SEAT) t.put("seat" + pt.no, pt.no + " FREE"); continue; }
            occ++;
            if (pt.kind == Interior.Kind.SEAT) {
                v.put("seat" + pt.no, 1);
                EntityPlayer pl = w.getPlayerEntityByUUID(who);
                t.put("seat" + pt.no, pt.no + " " + (pl == null ? "TAKEN" : pl.getName()));
            }
        }
        t.put("crew", String.format(Locale.ROOT, "%s %s\nPWR %s  LIGHTS %s\nSEATED %d/%d  %s\n%s", l == Interior.Layout.SLEEPER ? "SLEEPER" : l == Interior.Layout.CAFE ? "CAFE" : "COACH",
                clock, m.powered ? "ON" : "OFF", new String[]{"AUTO", "ON", "OFF"}[mode], occ, p.seats + p.berths, c != null && c.wcLock != null ? "WC OCC" : "WC FREE",
                c != null && c.is("alarm") ? "!! PASSENGER ALARM" : calls ? "! HELP CALL" : next.isEmpty() ? "NO SERVICE" : "NEXT " + next));
        t.put("water", String.format(Locale.ROOT, "H2O %3.0f L", water));
        t.put("waste", String.format(Locale.ROOT, "WST %3.0f%%", waste));
        t.put("wcsign", !m.powered || waste >= 100 ? "OUT OF ORDER" : c != null && c.wcLock != null ? "OCCUPIED" : "VACANT");
        double amb = ambient(w, s);
        for (Interior.Part pt : p.of(Interior.Kind.PSU)) {
            boolean vent = c != null && c.is("vent" + pt.idx);
            double tc = m.powered ? (vent ? (21 + amb) / 2 : 21) : amb;
            t.put("bayt" + pt.idx, String.format(Locale.ROOT, "%.0f C", tc));
        }
        for (Interior.Part pt : p.of(Interior.Kind.BERTH)) t.put("bclk" + pt.no, c != null && c.is("wk" + pt.no) ? clock + " W06" : clock);
        for (Interior.Part pt : p.of(Interior.Kind.WALL)) if (pt.name.startsWith("Compartment ")) t.put("comp" + pt.name.substring(12), "C" + pt.name.substring(12));
        if (l == Interior.Layout.TPO) { try { com.dogpound.railmap.mail.MailTpo.state(w, s, v, t); } catch (RuntimeException | LinkageError ignored) { } }   // batch 5 #8 travelling post office
        if (l == Interior.Layout.CAFE) {
            t.put("ktemp", c == null ? "20 C" : String.format(Locale.ROOT, "%.0f C", c.kettleT));
            t.put("ctemp", c == null || !c.coffeeOn ? "OFF" : String.format(Locale.ROOT, "%.0fC %.1fb", c.coffeeT, Math.max(0, (c.coffeeT - 100) / 18.0)));
            t.put("ftemp", m.powered ? "4 C" : String.format(Locale.ROOT, "%.0f C", Math.max(4, amb)));
            t.put("till", c == null ? Coins.fmt(0) : Coins.fmt(c.till));
            Interior.Part fridge = p.of(Interior.Kind.FRIDGE).isEmpty() ? null : p.of(Interior.Kind.FRIDGE).get(0);
            StringBuilder board = new StringBuilder("MENU  (" + clock + ")");
            int lines = 0;
            if (c != null && fridge != null) {
                IInventory inv = c.store(fridge);
                Map<String, Long> seen = new java.util.LinkedHashMap<>();
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack st = inv.getStackInSlot(i);
                    if (st.isEmpty()) continue;
                    long price = price(st);
                    m.menu.add(new Object[]{i, st.copy(), price});
                    seen.merge(st.getDisplayName(), price, (a, b) -> a);
                }
                for (Map.Entry<String, Long> e : seen.entrySet()) { if (lines++ >= 5) break; board.append('\n').append(e.getKey()).append("  ").append(Coins.fmt(e.getValue())); }
            }
            if (lines == 0) board.append("\nSOLD OUT - fridge empty");
            t.put("board", board.toString());
            t.put("tillscr", m.powered ? (m.menu.isEmpty() ? "CLOSED\nno stock\n" : "TAP TO ORDER\n" + m.menu.size() + " lines\n" + (Coins.available() ? "PRIDE COINS" : "NO BANK")) : "NO POWER\n\n");
        }
        // what stands on racks / tables / the counter / in the fridge
        if (c != null) for (Interior.Part pt : p.parts) {
            if (pt.slots <= 0 || pt.spots.isEmpty()) continue;
            InteriorData.Store st = c.stores.get(pt.idx);
            if (st == null) continue;
            List<ItemStack> show = new ArrayList<>();
            for (int i = 0; i < st.getSizeInventory() && show.size() < pt.spots.size(); i++) {
                ItemStack it = st.getStackInSlot(i);
                if (it.isEmpty()) continue;
                ItemStack one = it.copy();
                one.setCount(1);
                show.add(one);
            }
            if (!show.isEmpty()) m.items.put(pt.idx, show);
        }
        return m;
    }

    static String clock(World w) {
        long t = (w.getWorldTime() + 6000) % 24000;
        return String.format(Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    private static void send(World w, long now, Map<Integer, PacketInteriorState> built, Map<Integer, double[]> where) {
        if (built.isEmpty()) return;
        Map<Integer, Integer> hash = new HashMap<>();
        for (Map.Entry<Integer, PacketInteriorState> e : built.entrySet()) {
            io.netty.buffer.ByteBuf b = Unpooled.buffer();
            e.getValue().toBytes(b);
            byte[] a = new byte[b.readableBytes()];
            b.readBytes(a);
            hash.put(e.getKey(), Arrays.hashCode(a));
        }
        double r = InteriorConfig.renderRange + 16;
        boolean refresh = now % 100 < InteriorConfig.syncTicks;
        for (EntityPlayer pl : w.playerEntities) {
            if (!(pl instanceof EntityPlayerMP mp)) continue;
            Map<Integer, Integer> sent = SENT.computeIfAbsent(pl.getUniqueID(), k -> new HashMap<>());
            for (Map.Entry<Integer, PacketInteriorState> e : built.entrySet()) {
                double[] q = where.get(e.getKey());
                if (pl.getDistanceSq(q[0], q[1], q[2]) > r * r) { sent.remove(e.getKey()); continue; }
                Integer hs = hash.get(e.getKey());
                if (!refresh && hs.equals(sent.get(e.getKey()))) continue;
                sent.put(e.getKey(), hs);
                RailMap.NETWORK.sendTo(e.getValue(), mp);
            }
        }
        if (now % 1200 == 1) SENT.keySet().removeIf(u -> w.getMinecraftServer() != null && w.getMinecraftServer().getPlayerList().getPlayerByUUID(u) == null);
    }

    // ---- prices + drinks --------------------------------------------------------------------------------------
    public static long price(ItemStack st) {
        if (st.hasTagCompound() && st.getTagCompound().getBoolean("irxDrink")) return InteriorConfig.drinkPrice;
        if (st.getItem() instanceof ItemFood f) return InteriorConfig.basePrice + (long) f.getHealAmount(st) * InteriorConfig.foodPerShank;
        if (st.getItem() == Items.POTIONITEM || st.getItem() == Items.MILK_BUCKET) return InteriorConfig.basePrice + 100;
        return InteriorConfig.basePrice;
    }

    static ItemStack drink(String name, int colour, PotionEffect... fx) {
        ItemStack s = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER);
        PotionUtils.appendEffects(s, Arrays.asList(fx));
        NBTTagCompound t = s.getTagCompound();
        t.setInteger("CustomPotionColor", colour);
        t.setBoolean("irxDrink", true);
        s.setStackDisplayName("§f" + name);
        return s;
    }

    // ---- clicks -----------------------------------------------------------------------------------------------
    static void say(EntityPlayer p, String s) { p.sendStatusMessage(new TextComponentString(s), true); }

    static boolean crew(EntityPlayer p, InteriorData.Car c) {
        return p.capabilities.isCreativeMode || (c != null && p.getUniqueID().equals(c.owner)) || p.canUseCommand(2, "") || c != null && c.owner == null;
    }

    static void handle(EntityPlayerMP p, PacketInterior m) {
        if (!IR) return;
        World w = p.world;
        EntityRollingStock s = byEntityId(w, m.entity);
        if (s == null || s.isDead()) return;
        if (m.op == PacketInterior.FIT) { fit(p, s, m.arg); return; }
        cam72cam.mod.math.Vec3d pos = s.getPosition();
        double reach = s.getDefinition().getLength(s.gauge) / 2 + 8;
        if (p.getDistanceSq(pos.x, pos.y, pos.z) > reach * reach) return;
        InteriorData data = InteriorData.get(w);
        Interior.Layout l = layoutOf(s, data.peek(s.getUUID()));
        Interior.Plan plan = Interior.plan(s, l);
        if (plan == null) return;
        InteriorData.Car c = data.car(s.getUUID());
        c.pos = new double[]{pos.x, pos.y, pos.z};
        if (c.water < 0) c.water = InteriorConfig.waterTankLitres;
        if (m.op == PacketInterior.BUY) { buy(p, s, c, plan, m.arg); return; }
        Interior.Part part = plan.part(m.part);
        if (part == null) return;
        // the part must be within arm's reach (the client ray-cast it; check it really is close)
        double[] hb = part.hit != null ? part.hit : part.boxes.isEmpty() ? null : part.boxes.get(0);
        if (hb != null) {
            double[] cw = Interior.toWorld(s, (hb[0] + hb[3]) / 2, (hb[1] + hb[4]) / 2, (hb[2] + hb[5]) / 2);
            if (p.getDistanceSq(cw[0], cw[1], cw[2]) > 7.5 * 7.5) return;
        }
        String btn = m.btn >= 0 && m.btn < part.btns.size() ? part.btns.get(m.btn).id : "";
        String r = click(p, s, c, plan, part, btn);
        if (r != null && !r.isEmpty()) say(p, r);
        data.markDirty();
    }

    static String click(EntityPlayerMP p, EntityRollingStock s, InteriorData.Car c, Interior.Plan plan, Interior.Part part, String btn) {
        World w = p.world;
        boolean pw = powered(s);
        String car = carName(s);
        ItemStack held = p.getHeldItem(EnumHand.MAIN_HAND);
        switch (part.kind) {
            case SEAT -> { return sit(p, s, part); }
            case BERTH -> {
                if (btn.isEmpty()) return sit(p, s, part);
                int no = part.no;
                switch (btn) {
                    case "blight", "night" -> {
                        if (!pw) return "§cNo car power";
                        boolean on = c.toggle((btn.equals("blight") ? "bl" : "nl") + no);
                        sound(w, s, ModSounds.BUTTON, 0.3f, 1.4f);
                        return (btn.equals("blight") ? "§eReading light " : "§9Night light ") + (on ? "on" : "off");
                    }
                    case "call" -> {
                        boolean on = c.toggle("bc" + no);
                        if (on && InteriorConfig.alarms) AlarmManager.get(w).raise(w, "int:bc:" + s.getUUID(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "Sleeper attendant call: berth " + no + ", " + car, at(s), true);
                        pack(w, s, "station.chime_dingdong", 0.4f);
                        notifyOwner(w, c, on ? "§eAttendant call: berth " + no + " in " + car : null);
                        return on ? "§eAttendant called" : "§7Call cancelled";
                    }
                    case "wake" -> { boolean on = c.toggle("wk" + no); sound(w, s, ModSounds.KEY, 0.4f, 1.2f); return on ? "§aWake-up call set for 06:00" : "§7Wake-up call off"; }
                    default -> { return null; }
                }
            }
            case RACK, TABLE, COUNTER -> {
                if (part.kind == Interior.Kind.COUNTER && !btn.isEmpty()) {
                    if (btn.equals("bell")) { pack(w, s, "station.chime_pling", 0.8f); notifyOwner(w, c, "§eService bell rung in the " + car + " café"); return "§7Ding! The crew have been called"; }
                }
                if (part.kind == Interior.Kind.COUNTER && !crew(p, c)) return "§7Display case: staff only - order at the till";
                return rack(p, s, c, part, held);
            }
            case FRIDGE -> {
                InteriorData.Store inv = c.store(part);
                if (!held.isEmpty()) {
                    String name = held.getDisplayName();
                    ItemStack rest = insert(inv, held.copy());
                    int moved = held.getCount() - rest.getCount();
                    if (moved == 0) return "§cFridge full";
                    held.shrink(moved);
                    pack(w, s, "machine.door_close", 0.4f);
                    return "§aStocked " + moved + "x " + name + " into the fridge (" + count(inv) + " items) - it's on the menu now";
                }
                if (!crew(p, c)) return "§7Staff only - buy at the till (or hand stock in: click with an item)";
                pack(w, s, "machine.door_open", 0.4f);
                p.displayGUIChest(inv);
                return null;
            }
            case TILL -> {
                if (btn.equals("cashup")) {
                    if (c.owner == null || !c.owner.equals(p.getUniqueID()) && !p.canUseCommand(2, "")) return "§cOnly the car's owner can cash up";
                    if (c.till <= 0) return "§7Till empty";
                    EntityPlayerMP owner = (EntityPlayerMP) w.getPlayerEntityByUUID(c.owner);
                    if (owner == null) owner = p;
                    if (!Coins.pay(owner, c.till, "Café takings: " + car)) return "§cNo Pride Realms bank to pay into";
                    String msg = "§aCashed up " + Coins.fmt(c.till) + " (" + c.sold + " sales) to " + owner.getName();
                    Recorder.event(w, "Café " + car + " cashed up " + Coins.fmt(c.till));
                    c.till = 0; c.sold = 0;
                    pack(w, s, "ui.cash", 0.8f);
                    return msg;
                }
                return "§7Tap the till screen to see the menu";        // the client opens the menu itself
            }
            case KETTLE -> {
                if (c.kettleOn) { c.kettleOn = false; sound(w, s, ModSounds.RELAY, 0.5f, 1.3f); return "§7Kettle off (" + Math.round(c.kettleT) + " C)"; }
                if (!pw) return "§cNo car power - the kettle won't heat";
                if (c.kettleL < 1.9f) {
                    float want = 2f - c.kettleL;
                    if (!use(c, want)) return "§cFresh-water tank empty - refill it at a sink with a water bucket";
                    c.kettleT = (c.kettleT * c.kettleL + 12 * want) / 2f;
                    c.kettleL = 2f;
                    pack(w, s, "station.tap", 0.5f);
                }
                c.kettleOn = true;
                sound(w, s, ModSounds.RELAY, 0.5f, 1.1f);
                return "§aKettle on: " + Math.round(c.kettleT) + " C -> boiling";
            }
            case COFFEE -> { return coffee(p, s, c, plan, btn, pw); }
            case SINK -> { return sink(p, s, c, plan, btn, held, pw); }
            case TOILET -> {
                if (wcBlocked(p, s, c, plan)) return "§cOccupied - the door is locked";
                if (btn.equals("cord")) {
                    c.on.add("wccord");
                    if (InteriorConfig.alarms) AlarmManager.get(w).raise(w, "int:wc:" + s.getUUID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR, "TOILET EMERGENCY CORD pulled - " + car, at(s), true);
                    sound(w, s, ModSounds.BUZZER, 0.7f, 1f);
                    return "§cEmergency cord pulled - the crew have been alerted";
                }
                if (!pw) return "§cNo power - the vacuum toilet is out of order";
                if (c.waste >= 100) return "§cOut of order: waste tank full (crew: WASTE DISCHARGE at the crew panel, train stopped)";
                if (!use(c, 0.6f)) return "§cNo water to flush";
                c.waste = Math.min(100, c.waste + 1.5f);
                pack(w, s, "station.flush", 0.9f);
                sound(w, s, SoundEvents.ENTITY_GENERIC_SPLASH, 0.3f, 1.4f);
                return "§7*whoosh* Flushed (waste " + Math.round(c.waste) + "%)";
            }
            case WC_DOOR -> {
                if (btn.equals("lockin")) {
                    double[] lo = Interior.toLocal(s, p.posX, p.posY, p.posZ);
                    if (!plan.inWc(lo[0], lo[1], lo[2])) return "§7The lock is on the inside - step into the toilet first";
                    if (c.wcLock != null && !c.wcLock.equals(p.getUniqueID())) return "§cAlready locked";
                    pack(w, s, "signal.points_lock", 0.5f);
                    if (c.wcLock != null) { c.wcLock = null; c.wcLockName = ""; return "§aUnlocked - sign shows VACANT"; }
                    c.wcLock = p.getUniqueID(); c.wcLockName = p.getName();
                    return "§aLocked - sign shows OCCUPIED";
                }
                if (c.wcLock != null && !c.wcLock.equals(p.getUniqueID())) {
                    sound(w, s, SoundEvents.BLOCK_WOOD_BUTTON_CLICK_ON, 0.9f, 0.6f);
                    EntityPlayer in = w.getPlayerEntityByUUID(c.wcLock);
                    if (in != null) say(in, "§7*knock knock* - someone is waiting for the toilet");
                    return "§cOCCUPIED - you knock";
                }
                return "§aVACANT - walk in, lock the door with the red button inside";
            }
            case PASSCOMM -> {
                c.on.add("alarm");
                if (InteriorConfig.alarms) AlarmManager.get(w).raise(w, "int:pa:" + s.getUUID(), AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MAJOR,
                        "PASSENGER ALARM pulled by " + p.getName() + " - " + car + String.format(Locale.ROOT, " at %.0f km/h", speed(s)), at(s), true);
                sound(w, s, ModSounds.ALARM, 0.8f, 1.2f);
                return "§cPassenger alarm activated - the driver and control have been told";
            }
            case PSU -> {
                if (btn.startsWith("read")) {
                    if (!pw) return "§cNo car power";
                    boolean on = c.toggle(btn);
                    sound(w, s, ModSounds.BUTTON, 0.3f, 1.4f);
                    return on ? "§eReading light on" : "§7Reading light off";
                }
                if (btn.equals("call")) {
                    boolean on = c.toggle("call" + part.idx);
                    if (on && InteriorConfig.alarms) AlarmManager.get(w).raise(w, "int:call:" + s.getUUID() + ":" + part.idx, AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "Passenger help call: " + car + " (" + p.getName() + ")", at(s), true);
                    else AlarmManager.get(w).clear("int:call:" + s.getUUID() + ":" + part.idx);
                    pack(w, s, "station.chime_dingdong", 0.5f);
                    notifyOwner(w, c, on ? "§ePassenger help call in " + car + " from " + p.getName() : null);
                    return on ? "§eHelp call sent - the crew lamp is lit" : "§7Help call cancelled";
                }
                if (btn.equals("vent")) { boolean on = c.toggle("vent" + part.idx); sound(w, s, ModSounds.SELECTOR, 0.3f, 1.2f); return on ? "§7Air vent open" : "§7Air vent shut"; }
                return null;
            }
            case PANEL -> { return crewPanel(p, s, c, plan, btn, pw); }
            case SCREEN, CCTV, LIGHT, WALL, LADDER, DECOR -> {
                if (btn.equals("plate")) return null;
                return part.kind == Interior.Kind.CCTV ? (c.cam > 0 ? "§7Saloon camera CAM " + c.cam + (c.cctvOff ? " (switched off)" : pw ? " - recording" : " - no power") : "§7Saloon camera: not on the CCTV system (fit the car with the kit)") : null;
            }
            default -> { return null; }
        }
    }

    static BlockPos at(EntityRollingStock s) { cam72cam.mod.math.Vec3d p = s.getPosition(); return new BlockPos(p.x, p.y, p.z); }

    static void notifyOwner(World w, InteriorData.Car c, String text) {
        if (text == null || c.owner == null) return;
        EntityPlayer o = w.getPlayerEntityByUUID(c.owner);
        if (o != null) o.sendStatusMessage(new TextComponentString(text), false);
    }

    static boolean use(InteriorData.Car c, float litres) {
        if (!InteriorConfig.waterUsed) return true;
        if (c.water < litres) return false;
        c.water -= litres;
        return true;
    }

    static boolean wcBlocked(EntityPlayer p, EntityRollingStock s, InteriorData.Car c, Interior.Plan plan) {
        return c.wcLock != null && !c.wcLock.equals(p.getUniqueID());
    }

    private static String sit(EntityPlayerMP p, EntityRollingStock s, Interior.Part part) {
        if (!(s instanceof EntityRidableRollingStock r)) return null;
        String key = part.seatKey();
        try {
            Map<String, UUID> seated = r.prideSeated();
            UUID who = seated.get(key);
            if (who != null && !who.equals(p.getUniqueID())) {
                EntityPlayer o = p.world.getPlayerEntityByUUID(who);
                return "§c" + part.name + " is taken" + (o == null ? "" : " by " + o.getName());
            }
            cam72cam.mod.entity.Player pl = cam72cam.mod.world.World.get(p.world).getEntity(p.getUniqueID(), cam72cam.mod.entity.Player.class);
            if (pl == null) return null;
            if (who != null) { r.prideSeat(p.getUniqueID(), null); ASLEEP.remove(p.getUniqueID()); return "§7You get up"; }
            if (!r.isPassenger(pl)) {
                if (!r.canFitPassenger(pl)) return "§cThe car is full (or you may not board it)";
                r.addPassenger(pl);
            }
            r.prideSeat(p.getUniqueID(), key);
            if (part.kind == Interior.Kind.BERTH) {
                return night(p.world) ? "§bYou settle into the berth and drift off... (click again to get up)" : "§7You lie down in the berth - it restores you; at night you sleep (click again to get up)";
            }
            return "§7You sit down in " + part.name.toLowerCase(Locale.ROOT) + " (click again or sneak to stand)";
        } catch (LinkageError e) {
            return "§cThis needs the updated Immersive Railroading fork (Pride seat hooks)";
        }
    }

    private static String rack(EntityPlayerMP p, EntityRollingStock s, InteriorData.Car c, Interior.Part part, ItemStack held) {
        InteriorData.Store inv = c.store(part);
        if (inv == null) return null;
        if (p.isSneaking() && held.isEmpty()) { p.displayGUIChest(inv); return null; }
        if (!held.isEmpty()) {
            ItemStack rest = insert(inv, held.copy());
            int moved = held.getCount() - rest.getCount();
            if (moved == 0) return "§c" + part.name + " is full";
            held.shrink(moved);
            sound(p.world, s, SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM, 0.5f, 1f);
            return "§7Put on the " + part.name.toLowerCase(Locale.ROOT) + " (" + count(inv) + " items; sneak-click for the full view)";
        }
        for (int i = inv.getSizeInventory() - 1; i >= 0; i--) {
            ItemStack it = inv.getStackInSlot(i);
            if (it.isEmpty()) continue;
            inv.setInventorySlotContents(i, ItemStack.EMPTY);
            if (!p.addItemStackToInventory(it)) p.dropItem(it, false);
            sound(p.world, s, SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM, 0.5f, 1f);
            return "§7Took it from the " + part.name.toLowerCase(Locale.ROOT);
        }
        return "§7The " + part.name.toLowerCase(Locale.ROOT) + " is empty - click with an item to put it there";
    }

    static ItemStack insert(IInventory inv, ItemStack st) {
        for (int pass = 0; pass < 2 && !st.isEmpty(); pass++) {
            for (int i = 0; i < inv.getSizeInventory() && !st.isEmpty(); i++) {
                ItemStack in = inv.getStackInSlot(i);
                if (pass == 0 && !in.isEmpty() && ItemStack.areItemsEqual(in, st) && ItemStack.areItemStackTagsEqual(in, st)) {
                    int n = Math.min(st.getCount(), Math.min(in.getMaxStackSize(), inv.getInventoryStackLimit()) - in.getCount());
                    if (n > 0) { in.grow(n); st.shrink(n); inv.markDirty(); }
                } else if (pass == 1 && in.isEmpty()) {
                    inv.setInventorySlotContents(i, st.copy());
                    st = ItemStack.EMPTY;
                }
            }
        }
        return st;
    }

    static int count(IInventory inv) { int n = 0; for (int i = 0; i < inv.getSizeInventory(); i++) n += inv.getStackInSlot(i).getCount(); return n; }

    private static String sink(EntityPlayerMP p, EntityRollingStock s, InteriorData.Car c, Interior.Plan plan, String btn, ItemStack held, boolean pw) {
        World w = p.world;
        double[] lo = Interior.toLocal(s, p.posX, p.posY, p.posZ);
        if (plan.inWc(lo[0], lo[1], lo[2]) && wcBlocked(p, s, c, plan)) return "§cOccupied";
        if (btn.equals("soap")) { sound(w, s, SoundEvents.BLOCK_SLIME_PLACE, 0.4f, 1.6f); return "§7A squirt of soap"; }
        if (btn.equals("dryer")) {
            if (!pw) return "§cNo power";
            sound(w, s, ModSounds.FAN_LOOP, 0.5f, 1.3f);
            return "§7*whirr* Hands dried";
        }
        if (held.getItem() == Items.WATER_BUCKET) {
            float room = InteriorConfig.waterTankLitres - c.water;
            if (room < 1) return "§7Fresh-water tank already full (" + Math.round(c.water) + " L)";
            c.water = Math.min(InteriorConfig.waterTankLitres, c.water + 10);
            if (!p.capabilities.isCreativeMode) p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            pack(w, s, "station.tap", 0.6f);
            return "§aTank refilled +10 L (" + Math.round(c.water) + " / " + InteriorConfig.waterTankLitres + " L)";
        }
        if (held.getItem() == Items.GLASS_BOTTLE) {
            if (!use(c, 0.25f)) return "§cNo water - tank empty";
            held.shrink(1);
            ItemStack wb = PotionUtils.addPotionToItemStack(new ItemStack(Items.POTIONITEM), PotionTypes.WATER);
            if (!p.addItemStackToInventory(wb)) p.dropItem(wb, false);
            pack(w, s, "station.tap", 0.6f);
            return "§7Filled a water bottle";
        }
        if (held.getItem() == Items.BUCKET) {
            if (!use(c, 10)) return "§cNot enough water in the tank (" + Math.round(c.water) + " L)";
            held.shrink(1);
            ItemStack wb = new ItemStack(Items.WATER_BUCKET);
            if (!p.addItemStackToInventory(wb)) p.dropItem(wb, false);
            pack(w, s, "station.tap", 0.7f);
            return "§7Filled a bucket (" + Math.round(c.water) + " L left)";
        }
        if (!use(c, 0.2f)) return "§cNo water - the tank is empty";
        pack(w, s, "station.tap", 0.6f);
        p.extinguish();
        return "§7You wash your hands (" + Math.round(c.water) + " L left)";
    }

    // ---- espresso machine ---------------------------------------------------------------------------------------
    private static String coffee(EntityPlayerMP p, EntityRollingStock s, InteriorData.Car c, Interior.Plan plan, String btn, boolean pw) {
        World w = p.world;
        if (btn.equals("cpower") || btn.isEmpty()) {
            if (btn.isEmpty()) return "§7Espresso machine: " + (c.coffeeOn ? Math.round(c.coffeeT) + " C" + (c.coffeeT >= 95 ? " READY" : " heating") : "off") + " - press the drink buttons";
            c.coffeeOn = !c.coffeeOn;
            sound(w, s, ModSounds.KEY, 0.5f, 1f);
            if (c.coffeeOn && !pw) return "§cSwitched on, but there's no car power";
            return c.coffeeOn ? "§aMachine on - boiler heating" : "§7Machine off";
        }
        if (!pw) return "§cNo car power";
        if (!c.coffeeOn) return "§cTurn the machine on first (POWER key)";
        boolean kettleTea = btn.equals("tea");
        if (!kettleTea && c.coffeeT < 95) return "§eStill heating: " + Math.round(c.coffeeT) + " C (ready at 95 C)";
        if (kettleTea && (c.kettleT < 90 || c.kettleL < 0.25f)) return "§eTea needs the kettle boiled (" + Math.round(c.kettleT) + " C, " + String.format(Locale.ROOT, "%.1f", c.kettleL) + " L)";
        if (!kettleTea && !use(c, 0.1f)) return "§cNo water in the tank";
        Interior.Part fridge = plan.of(Interior.Kind.FRIDGE).isEmpty() ? null : plan.of(Interior.Kind.FRIDGE).get(0);
        IInventory stock = fridge == null ? null : c.store(fridge);
        Need n = new Need(stock, p);
        ItemStack cup;
        switch (btn) {
            case "espresso" -> {
                if (!n.has(Items.DYE, EnumDyeColor.BROWN.getDyeDamage()) || !n.has(Items.GLASS_BOTTLE, -1)) return "§cEspresso needs cocoa beans + a glass bottle (fridge or your bag)";
                n.take(Items.DYE, EnumDyeColor.BROWN.getDyeDamage()); n.take(Items.GLASS_BOTTLE, -1);
                cup = drink("Espresso", 0x3B1F0E, new PotionEffect(MobEffects.HASTE, 2400, 0), new PotionEffect(MobEffects.SPEED, 1200, 0));
            }
            case "latte" -> {
                if (!n.has(Items.DYE, EnumDyeColor.BROWN.getDyeDamage()) || !n.has(Items.MILK_BUCKET, -1) || !n.has(Items.GLASS_BOTTLE, -1)) return "§cLatte needs cocoa beans + a milk bucket + a glass bottle";
                n.take(Items.DYE, EnumDyeColor.BROWN.getDyeDamage()); n.take(Items.MILK_BUCKET, -1); n.take(Items.GLASS_BOTTLE, -1); n.give(new ItemStack(Items.BUCKET));
                cup = drink("Latte", 0xC8A070, new PotionEffect(MobEffects.SPEED, 1800, 0), new PotionEffect(MobEffects.REGENERATION, 100, 0));
            }
            case "choc" -> {
                if (!n.has(Items.DYE, EnumDyeColor.BROWN.getDyeDamage()) || !n.has(Items.MILK_BUCKET, -1) || !n.has(Items.SUGAR, -1) || !n.has(Items.GLASS_BOTTLE, -1)) return "§cHot chocolate needs cocoa + milk + sugar + a glass bottle";
                n.take(Items.DYE, EnumDyeColor.BROWN.getDyeDamage()); n.take(Items.MILK_BUCKET, -1); n.take(Items.SUGAR, -1); n.take(Items.GLASS_BOTTLE, -1); n.give(new ItemStack(Items.BUCKET));
                cup = drink("Hot Chocolate", 0x5A3010, new PotionEffect(MobEffects.REGENERATION, 200, 0), new PotionEffect(MobEffects.ABSORPTION, 1200, 0));
            }
            case "tea" -> {
                if (!n.has(Items.GLASS_BOTTLE, -1)) return "§cTea needs a glass bottle";
                n.take(Items.GLASS_BOTTLE, -1);
                boolean sweet = n.has(Items.SUGAR, -1);
                if (sweet) n.take(Items.SUGAR, -1);
                c.kettleL -= 0.25f;
                cup = sweet ? drink("Sweet Tea", 0xB06A2A, new PotionEffect(MobEffects.REGENERATION, 160, 0), new PotionEffect(MobEffects.SPEED, 600, 0))
                        : drink("Tea", 0xA0602A, new PotionEffect(MobEffects.REGENERATION, 160, 0));
            }
            case "water" -> {
                if (!n.has(Items.GLASS_BOTTLE, -1)) return "§cHot water needs a glass bottle";
                n.take(Items.GLASS_BOTTLE, -1);
                cup = drink("Hot Water", 0xC0D8F0, new PotionEffect(MobEffects.RESISTANCE, 200, 0));
            }
            default -> { return null; }
        }
        c.coffeeT = Math.max(90, c.coffeeT - (kettleTea ? 0 : 6));
        sound(w, s, SoundEvents.BLOCK_BREWING_STAND_BREW, 0.6f, 1.2f);
        sound(w, s, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.2f, 1.5f);
        String name = cup.getDisplayName().substring(2);
        if (n.fromFridge && stock != null && insert(stock, cup).isEmpty()) return "§a" + name + " made - into the fridge for sale (" + Coins.fmt(price(cup)) + ")";
        if (!p.addItemStackToInventory(cup)) p.dropItem(cup, false);
        return "§a" + name + " made - enjoy!";
    }

    /** ingredients: from the fridge first (staff making stock), else the player's own bag (a passenger making their own) */
    private static final class Need {
        final IInventory fridge; final EntityPlayer p;
        boolean fromFridge;
        final List<ItemStack> out = new ArrayList<>();
        Need(IInventory fridge, EntityPlayer p) { this.fridge = fridge; this.p = p; }
        private int find(IInventory inv, Item it, int meta) {
            if (inv == null) return -1;
            for (int i = 0; i < inv.getSizeInventory(); i++) { ItemStack s = inv.getStackInSlot(i); if (s.getItem() == it && (meta < 0 || s.getMetadata() == meta) && !s.hasDisplayName()) return i; }
            return -1;
        }
        boolean has(Item it, int meta) { return find(fridge, it, meta) >= 0 || find(p.inventory, it, meta) >= 0; }
        void take(Item it, int meta) {
            int i = find(fridge, it, meta);
            if (i >= 0) { fridge.decrStackSize(i, 1); fromFridge = true; return; }
            i = find(p.inventory, it, meta);
            if (i >= 0) p.inventory.decrStackSize(i, 1);
        }
        void give(ItemStack s) {
            if (fromFridge && fridge != null && insert(fridge, s).isEmpty()) return;
            if (!p.addItemStackToInventory(s)) p.dropItem(s, false);
        }
    }

    // ---- crew panel -------------------------------------------------------------------------------------------
    private static String crewPanel(EntityPlayerMP p, EntityRollingStock s, InteriorData.Car c, Interior.Plan plan, String btn, boolean pw) {
        World w = p.world;
        if (btn.isEmpty() || btn.equals("status")) return "§7Crew panel: " + (pw ? "car power ON" : "§cno car power") + "§7 · water " + Math.round(c.water) + " L · waste " + Math.round(c.waste) + "%";
        if (!crew(p, c)) return "§cCrew only (the car's owner) - passengers: use the help call or the alarm handle";
        String car = carName(s);
        switch (btn) {
            case "lights" -> { c.lightMode = (c.lightMode + 1) % 3; sound(w, s, ModSounds.SELECTOR, 0.5f, 1f); return "§7Saloon lights: " + new String[]{"AUTO (dark / tunnels)", "ON", "OFF"}[c.lightMode]; }
            case "pa" -> {
                pack(w, s, "station.chime_two_tone", 0.8f);
                EntityRollingStock h = com.dogpound.railmap.signs.SignServer.head(s);
                Autopilot.Run r = Autopilot.run(h.getUUID());
                String next = r == null || r.nextStopName == null || r.nextStopName.isEmpty() ? "" : " Our next stop is " + r.nextStopName + ".";
                for (EntityPlayer pl : riders(w, s)) pl.sendStatusMessage(new TextComponentString("§b[PA " + car + "] §fLadies and gentlemen, welcome aboard." + next), false);
                return null;
            }
            case "callrst" -> {
                c.on.removeIf(k -> k.startsWith("call") || k.startsWith("bc"));
                for (Interior.Part pt : plan.of(Interior.Kind.PSU)) AlarmManager.get(w).clear("int:call:" + s.getUUID() + ":" + pt.idx);
                AlarmManager.get(w).clear("int:bc:" + s.getUUID());
                sound(w, s, ModSounds.ACK_BEEP, 0.5f, 1f);
                return "§aHelp calls answered + reset";
            }
            case "alarmrst" -> {
                c.on.remove("alarm"); c.on.remove("wccord");
                AlarmManager.get(w).clear("int:pa:" + s.getUUID()); AlarmManager.get(w).clear("int:wc:" + s.getUUID());
                Recorder.event(w, "Passenger alarm reset by " + p.getName() + " - " + car);
                sound(w, s, ModSounds.ACK_BEEP, 0.5f, 0.8f);
                return "§aPassenger alarm reset";
            }
            case "discharge" -> {
                if (speed(s) > 1) return "§cOnly with the train stopped (controlled emptying)";
                c.waste = 0;
                sound(w, s, SoundEvents.ENTITY_GENERIC_SPLASH, 0.5f, 0.6f);
                return "§aWaste tank emptied";
            }
            case "doorl", "doorr", "doorc" -> {
                if (!btn.equals("doorc") && speed(s) > 1) return "§cDoors are interlocked while moving";
                if (!pw) return "§cNo car power for the door motors";
                if (btn.equals("doorc")) { com.dogpound.railmap.doors.TrainDoors.move(w, s, "DL", false, 2.5f); com.dogpound.railmap.doors.TrainDoors.move(w, s, "DR", false, 2.5f); return "§7Doors closing"; }
                com.dogpound.railmap.doors.TrainDoors.move(w, s, btn.equals("doorl") ? "DL" : "DR", true, 2.5f);
                return "§7Doors opening (" + (btn.equals("doorl") ? "left" : "right") + ") - works on cars with sliding-door groups";
            }
            case "cctvkey" -> {
                c.cctvOff = !c.cctvOff;
                sound(w, s, ModSounds.KEY, 0.5f, 1f);
                return c.cam > 0 ? (c.cctvOff ? "§7Saloon CCTV off" : "§aSaloon CCTV on (CAM " + c.cam + ")") : "§7No saloon camera on the CCTV system (fit the car with the kit)";
            }
            default -> { return plan.layout == Interior.Layout.TPO ? com.dogpound.railmap.mail.MailTpo.panel(p, s, c, btn) : null; }   // batch 5 #8 TPO sorters' desk
        }
    }

    // ---- café sale ----------------------------------------------------------------------------------------------
    private static void buy(EntityPlayerMP p, EntityRollingStock s, InteriorData.Car c, Interior.Plan plan, int slot) {
        World w = p.world;
        if (plan.layout != Interior.Layout.CAFE) return;
        if (!powered(s)) { say(p, "§cThe till has no power"); return; }
        Interior.Part fridge = plan.of(Interior.Kind.FRIDGE).isEmpty() ? null : plan.of(Interior.Kind.FRIDGE).get(0);
        if (fridge == null) return;
        InteriorData.Store inv = c.store(fridge);
        if (slot < 0 || slot >= inv.getSizeInventory() || inv.getStackInSlot(slot).isEmpty()) { say(p, "§cSold out"); return; }
        ItemStack st = inv.getStackInSlot(slot);
        long price = price(st);
        boolean own = p.getUniqueID().equals(c.owner);
        if (!own && !Coins.charge(p, price, "Café: " + st.getDisplayName())) {
            say(p, Coins.available() ? "§cCard declined: " + Coins.fmt(price) + " needed (balance " + Coins.fmt(Math.max(0, Coins.balance(p))) + ")" : "§cThe till needs the Pride Realms bank");
            sound(w, s, ModSounds.BEEP_ERROR, 0.5f, 1f);
            return;
        }
        ItemStack one = inv.decrStackSize(slot, 1);
        if (!p.addItemStackToInventory(one)) p.dropItem(one, false);
        if (!own && !p.capabilities.isCreativeMode) { c.till += price; c.sold++; }
        PAID.put(s.getUUID(), w.getTotalWorldTime());
        pack(w, s, "ui.cash", 0.7f);
        say(p, "§aBought " + one.getDisplayName() + (own ? " (your own stock - free)" : " for " + Coins.fmt(price)));
        c.data.markDirty();
    }

    // ---- fitting ----------------------------------------------------------------------------------------------
    /** arg: -1 automatic (by name), 0 none, 1.. a layout */
    static void fit(EntityPlayerMP p, EntityRollingStock s, int arg) {
        World w = p.world;
        if (!fittable(s)) { say(p, "§cOnly passenger cars can be fitted out"); return; }
        cam72cam.mod.math.Vec3d pos = s.getPosition();
        if (p.getDistanceSq(pos.x, pos.y, pos.z) > Math.pow(s.getDefinition().getLength(s.gauge) / 2 + 8, 2)) return;
        ItemStack kit = p.getHeldItem(EnumHand.MAIN_HAND);
        if (!(kit.getItem() instanceof ItemFittingKit) && !p.capabilities.isCreativeMode) { say(p, "§cHold the Interior Fitting Kit"); return; }
        InteriorData data = InteriorData.get(w);
        InteriorData.Car c = data.car(s.getUUID());
        if (c.owner != null && !c.owner.equals(p.getUniqueID()) && !p.canUseCommand(2, "") && !p.capabilities.isCreativeMode) { say(p, "§cThis car was fitted by " + c.ownerName + " - only they can refit it"); return; }
        Interior.Layout before = layoutOf(s, c);
        String r = apply(w, s, c, arg, p);
        Interior.Layout after = layoutOf(s, c);
        if (after != Interior.Layout.NONE && after != before && !p.capabilities.isCreativeMode) kit.shrink(1);
        say(p, r);
    }

    /** change a car's layout: storage contents go to the player (or drop), the saloon camera follows */
    public static String apply(World w, EntityRollingStock s, InteriorData.Car c, int arg, EntityPlayer p) {
        Interior.Layout before = layoutOf(s, c);
        c.layout = Math.max(-1, Math.min(Interior.Layout.values().length - 1, arg));
        Interior.Layout after = layoutOf(s, c);
        if (before != after) {
            for (InteriorData.Store st : c.stores.values()) for (int i = 0; i < st.getSizeInventory(); i++) {
                ItemStack it = st.removeStackFromSlot(i);
                if (it.isEmpty()) continue;
                if (p != null) { if (!p.addItemStackToInventory(it)) p.dropItem(it, false); }
                else { cam72cam.mod.math.Vec3d q = s.getPosition(); net.minecraft.inventory.InventoryHelper.spawnItemStack(w, q.x, q.y + 1, q.z, it); }
            }
            c.stores.clear();
            c.on.clear();
            c.wcLock = null;
            if (s instanceof EntityRidableRollingStock r) {
                try { for (Map.Entry<String, UUID> e : new HashMap<>(r.prideSeated()).entrySet()) if (e.getKey().startsWith("pride:")) r.prideSeat(e.getValue(), null); } catch (LinkageError ignored) { }
            }
        }
        if (p != null) { c.owner = p.getUniqueID(); c.ownerName = p.getName(); }
        if (c.water < 0) c.water = InteriorConfig.waterTankLitres;
        camera(w, s, c, after != Interior.Layout.NONE && c.layout > 0);
        c.data.markDirty();
        pack(w, s, "machine.steam_hammer", 0.3f);
        Interior.Plan plan = Interior.plan(s, after);
        return after == Interior.Layout.NONE ? "§7Interior removed: " + carName(s) + " is a bare car again"
                : "§aFitted " + carName(s) + " as " + after.label + (c.layout < 0 ? " (automatic, by its name)" : "") + ": " + (plan == null ? 0 : plan.seats) + " seats, " + (plan == null ? 0 : plan.berths) + " berths"
                + (c.cam > 0 ? ", saloon CCTV CAM " + c.cam : "");
    }

    /** register / remove the saloon camera on the CCTV system (streams over the train's LTE modem like the forward cameras) */
    static void camera(World w, EntityRollingStock s, InteriorData.Car c, boolean want) {
        try {
            com.dogpound.railmap.cctv.CctvData d = com.dogpound.railmap.cctv.CctvData.get(w);
            String key = s.getUUID() + "#in";
            com.dogpound.railmap.cctv.CctvData.Cam cam = d.byTrain(key);
            if (!want) { if (cam != null) { d.cams.remove(cam.no); d.changed(); } c.cam = 0; return; }
            if (cam == null) {
                cam = new com.dogpound.railmap.cctv.CctvData.Cam();
                cam.no = d.allocCam();
                cam.type = com.dogpound.railmap.cctv.CamType.TRAIN.ordinal();
                cam.train = key;
                cam.dim = w.provider.getDimension();
                String n = ("SALOON " + carName(s)).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ./:+-]", "");
                cam.name = n.length() > 24 ? n.substring(0, 24) : n;
                cam.fov = 75;
                cam.status = com.dogpound.railmap.cctv.CctvData.ONLINE;
                d.cams.put(cam.no, cam);
                d.changed();
            }
            c.cam = cam.no;
        } catch (RuntimeException | LinkageError e) {
            RailMap.LOG.debug("[IR Extras] interior camera: {}", e.toString());
        }
    }

    /** set by the client: the layout a car was last reported with (0 = none) */
    public static java.util.function.Function<EntityRollingStock, Integer> CLIENT_LAYOUT;

    /** the saloon camera's eye for the CCTV system: {x, y, z, yaw, pitch}, null when off / unpowered / unloaded */
    public static double[] camPose(World w, String uuid, float pt) {
        try {
            EntityRollingStock s = byUuid(w, UUID.fromString(uuid));
            if (s == null || !fittable(s) || !powered(s)) return null;
            Interior.Plan p;
            if (w.isRemote) {                                   // live view on a client: the layout it was sent
                p = CLIENT_LAYOUT == null ? null : Interior.plan(s, Interior.Layout.of(CLIENT_LAYOUT.apply(s)));
            } else {
                InteriorData.Car c = InteriorData.get(w).peek(s.getUUID());
                if (c == null || c.cctvOff) return null;
                p = Interior.plan(s, layoutOf(s, c));
            }
            if (p == null || p.of(Interior.Kind.CCTV).isEmpty()) return null;
            double[] h = p.of(Interior.Kind.CCTV).get(0).hit;
            double[] e = Interior.toWorld(s, 0, h[1] - 0.05, h[2] - 0.1);
            double[] a = Interior.toWorld(s, 0, h[1] - 0.05, h[2] - 1.1);
            double dx = a[0] - e[0], dz = a[2] - e[2];
            return new double[]{e[0], e[1], e[2], Math.toDegrees(Math.atan2(-dx, dz)), 18};
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }
}
