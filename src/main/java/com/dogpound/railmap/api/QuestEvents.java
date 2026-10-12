package com.dogpound.railmap.api;

import com.dogpound.railmap.RailMap;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Quest hooks (her Railway Career quest line): IR Extras fires NAMED EVENTS with data when real railway work gets done,
 * and answers STATE questions, so a quest mod can follow a player's career without either mod depending on the other.
 * PrideQuests finds this class by reflection and calls {@link #listen}, {@link #rankProvider}; it reads
 * {@link #state} by reflection too. Nothing listening = every fire() returns at once.
 * <p>
 * Events (data keys after the name):
 * <pre>
 * track_inspected   tqi band tsr mode(walk|spot)    Track Geometry Trolley measured a piece
 * track_work        job tqi tsr                       Track Maintenance Kit job done
 * gear_powered      block name                        a 12 V terminal got its supply (0 -> 12 V DC)
 * commissioned      asset kind                        Commissioning Test Set signed an asset off
 * ndt_test          tool asset                        an NDT instrument tested an asset
 * defect_found      tool stage(DISCOVERED|CONFIRMED)  an NDT instrument found / confirmed a defect
 * defect_verified   tool                              the re-test after a repair came back clean (defect closed)
 * alarm_ack         count                             ACKNOWLEDGE on an annunciator with new alarms in
 * alarm_reset       count                             RESET on an annunciator with cleared alarms waiting
 * route_set         lever kind refused linked         a distant lever came off: a whole route set through the frame
 * station_stop      station dist auto speed           a train came to a stand at a named station (driver = riders)
 * freight_delivered id qty item ontime               a freight contract was completed
 * factory_output    item count machine                a factory machine made something
 * black_start       stages                            a Black Start Panel restored the grid
 * chaos_finished    scenario pass fail skipped        a chaos-engineering scenario ended
 * auction_won       bid paths window resource         a capacity auction bid WON (company members)
 * trophy            name                              a company won a mastery trophy (company members)
 * route_learned     signal known | line known         a driver now knows a signal (route knowledge) / was route-qualified on a line
 * driver_qualified  grade hours                       a driver reached a competence grade (QUALIFIED / SENIOR / INSTRUCTOR)
 * theory_passed     score tests                       a driver passed the theory test at a Driver Competency Assessor
 * fault_diagnosed   cause score seconds training      a Fault Diagnosis Terminal job was measured, diagnosed, repaired and verified
 * train_tripped     speed mode driverless             a train-stop trip cock tripped a train (its crew)
 * balise_passed     bg kmh stop                       a driven train read a balise telegram
 * macro_run         macro routes mode                 a route macro set all its routes (batch-3 #19)
 * </pre>
 * State keys: otp, departures, dispatcher_decisions, dispatcher_regions, trophies, trophy:NAME, mastery_level,
 * company, director, stations.
 */
public final class QuestEvents {
    private QuestEvents() {}

    /** players this close to an event with no named player get it (the crew on the spot) */
    public static final int NEAR = 48;

    private static final List<BiConsumer<EntityPlayerMP, Map<String, Object>>> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Function<UUID, String> rank;
    private static boolean warned;

    /** a quest mod listens: (player, data) - data has "event", "dim", "x","y","z" (when known) and the event's keys */
    public static void listen(BiConsumer<EntityPlayerMP, Map<String, Object>> l) { if (l != null) LISTENERS.add(l); }

    /** a quest mod tells us each player's rank title (shown on the console's crew / operator list) */
    public static void rankProvider(Function<UUID, String> f) { rank = f; }

    public static boolean active() { return !LISTENERS.isEmpty(); }

    /** the player's career title ("" = none / no quest mod) */
    public static String rankOf(UUID id) {
        Function<UUID, String> f = rank;
        if (f == null || id == null) return "";
        try { String s = f.apply(id); return s == null ? "" : s; } catch (RuntimeException e) { return ""; }
    }

    /** fire for one player, else the players near `at`, else everyone in the world. kv = key, value, key, value ... */
    public static void fire(World w, EntityPlayer who, BlockPos at, String event, Object... kv) {
        if (LISTENERS.isEmpty() || w == null || w.isRemote) return;
        List<EntityPlayerMP> to = new ArrayList<>();
        if (who instanceof EntityPlayerMP mp) to.add(mp);
        else for (EntityPlayer p : w.playerEntities)
            if (p instanceof EntityPlayerMP mp && (at == null || p.getDistanceSq(at) <= (double) NEAR * NEAR)) to.add(mp);
        send(w, to, at, event, kv);
    }

    /** fire for these players (a company's members), the ones online */
    public static void fireFor(World w, Collection<UUID> ids, BlockPos at, String event, Object... kv) {
        if (LISTENERS.isEmpty() || w == null || w.isRemote || ids == null) return;
        List<EntityPlayerMP> to = new ArrayList<>();
        for (UUID u : ids) {
            EntityPlayerMP p = w.getMinecraftServer() == null ? null : w.getMinecraftServer().getPlayerList().getPlayerByUUID(u);
            if (p != null) to.add(p);
        }
        send(w, to, at, event, kv);
    }

    private static void send(World w, List<EntityPlayerMP> to, BlockPos at, String event, Object... kv) {
        if (to.isEmpty()) return;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("event", event);
        d.put("dim", w.provider.getDimension());
        if (at != null) { d.put("x", at.getX()); d.put("y", at.getY()); d.put("z", at.getZ()); }
        for (int i = 0; i + 1 < kv.length; i += 2) d.put(String.valueOf(kv[i]), kv[i + 1]);
        Map<String, Object> ro = Collections.unmodifiableMap(d);
        for (EntityPlayerMP p : to)
            for (BiConsumer<EntityPlayerMP, Map<String, Object>> l : LISTENERS) {
                try { l.accept(p, ro); }
                catch (Throwable t) { if (!warned) { warned = true; RailMap.LOG.warn("[IR Extras] quest listener failed on {}: {}", event, t.toString()); } }
            }
    }

    // ---- state (read by the quest mod once a second for quests that are open) -----------------------------------
    /** a number about the player's railway right now; 0 when unknown */
    public static long state(EntityPlayerMP p, String key) {
        if (p == null || key == null) return 0;
        World w = p.world;
        try {
            switch (key) {
                case "otp": {                                       // today's on-time departures %, once 10 trains have left
                    Map<String, Double> t = com.dogpound.railmap.incident.Analytics.get(w).today(w);
                    double dep = com.dogpound.railmap.incident.Analytics.v(t, "dep");
                    return dep < 10 ? 0 : Math.round(com.dogpound.railmap.incident.Analytics.v(t, "ontime") / dep * 100);
                }
                case "departures": return Math.round(com.dogpound.railmap.incident.Analytics.v(com.dogpound.railmap.incident.Analytics.get(w).today(w), "dep"));
                case "dispatcher_decisions": {
                    long n = 0;
                    for (com.dogpound.railmap.planning.PlanData.Region r : com.dogpound.railmap.planning.PlanData.get(w).regions) if (r.on) n += r.decisions;
                    return n;
                }
                case "dispatcher_regions": {
                    long n = 0;
                    for (com.dogpound.railmap.planning.PlanData.Region r : com.dogpound.railmap.planning.PlanData.get(w).regions) if (r.on) n++;
                    return n;
                }
                case "stations": return com.dogpound.railmap.server.StationData.get(w).names().size();
                default:
            }
            com.dogpound.railmap.economy.Company c = com.dogpound.railmap.economy.CompanyData.get(w).of(p.getUniqueID());
            if (key.equals("company")) return c == null ? 0 : 1;
            if (c == null) return 0;
            com.dogpound.railmap.biz.Mastery m = com.dogpound.railmap.biz.BizData.get(w).mastery;
            if (key.equals("director")) return p.getUniqueID().equals(c.owner) ? 1 : 0;
            if (key.equals("mastery_level")) return m.level(c.id);
            Set<String> have = m.trophies.getOrDefault(c.id, new LinkedHashSet<>());
            if (key.equals("trophies")) return have.size();
            if (key.startsWith("trophy:")) return have.contains(key.substring(7).toUpperCase(Locale.ROOT).replace('_', ' ')) ? 1 : 0;
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
        return 0;
    }

    // ---- /irextras quest ... (tests: fire an event at yourself, read a state) ------------------------------------
    public static void command(ICommandSender s, World w, String[] a) {
        if (a.length < 2 || a[1].equalsIgnoreCase("help")) {
            say(s, "§b/irextras quest fire <event> [key=value ...]§7 - fire a quest event at you (tests)");
            say(s, "§b/irextras quest state <key>§7 - read a quest state value (otp, trophies, director ...)");
            say(s, "§7Listeners: " + LISTENERS.size() + (LISTENERS.isEmpty() ? " (PrideQuests not installed?)" : ""));
            return;
        }
        if (!(s.getCommandSenderEntity() instanceof EntityPlayerMP p)) { say(s, "§cPlayers only"); return; }
        if (a[1].equalsIgnoreCase("state") && a.length > 2) { say(s, "§b" + a[2] + " = §f" + state(p, a[2])); return; }
        if (a[1].equalsIgnoreCase("fire") && a.length > 2) {
            List<Object> kv = new ArrayList<>();
            for (int i = 3; i < a.length; i++) {
                String[] x = a[i].split("=", 2);
                if (x.length < 2) continue;
                kv.add(x[0]);
                Object v = x[1];
                try { v = Double.parseDouble(x[1]); } catch (NumberFormatException ignored) {}
                kv.add(v);
            }
            fire(w, p, p.getPosition(), a[2], kv.toArray());
            say(s, "§aFired §f" + a[2] + "§a " + kv + " to " + LISTENERS.size() + " listener(s)");
            return;
        }
        say(s, "§c/irextras quest fire <event> [k=v ...] | state <key>");
    }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
