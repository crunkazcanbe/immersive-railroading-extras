package com.dogpound.railmap.station;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.ctl.CtlConfig;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.incident.IncidentConfig;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.track.TileTunnelControl;
import com.dogpound.railmap.track.TrackHooks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Intelligent Passenger Information (her "next 48" list #30): ONE live service feed that every passenger-facing thing
 * reads - PIS boards, the arrivals boards' message line, the PA (through the Announcer), ticket machines - so they all
 * say the same thing at the same time.
 * <ul>
 *   <li>messages: station (or every station), kind, priority 1..9, text from a template filled from live data, when it
 *       started and when it expires, who made it (AUTO / an operator / an incident);</li>
 *   <li>AUTO (any PIS server in AUTO): delays, cancellations, CONNECTION AT RISK (a late train whose passengers would miss
 *       another line's departure here), evacuations (a tunnel emergency or a fire alarm near the station), all-clear;</li>
 *   <li>operators: platform alterations, security / information messages from the templates, evacuate / all-clear,
 *       clear - from a PIS server's panel, the console or /irextras ctl pis;</li>
 *   <li>priority decides what a sign shows first; messages at {@link CtlConfig#pisAnnouncePriority}+ are also spoken;</li>
 *   <li>a sign that can't reach a powered PIS server (none, no 12 V, or its field RTU's comms are down / stale) shows the
 *       FALLBACK message instead of stale information.</li>
 * </ul>
 * No PIS server placed = no feed, and every board works exactly as before.
 */
public final class Pis extends WorldSavedData {
    private static final String NAME = "irextras_pis";
    public enum Kind {
        EVAC(9), SECURITY(8), CANCEL(6), ALT(5), PLATFORM(5), CONNECT(4), DELAY(3), CLEAR(2), INFO(1);
        public final int prio;
        Kind(int p) { prio = p; }
    }
    public static final String[] INFO_TEMPLATES = {
            "Please keep your belongings with you at all times",
            "Please stand behind the yellow line",
            "Engineering works this weekend - check before you travel",
            "Services are running normally",
            "Please report anything suspicious to staff",
            "Mind the gap between the train and the platform"};
    public static final String T_CONNECT = "Passengers on the {train} for {dest}: it is running {min} min late - your connection with the {line} service at {station} is at risk";

    public static final class Msg {
        public int id;
        public String station = "*", text = "", source = "AUTO", key = "";
        public Kind kind = Kind.INFO;
        public int prio = 1;
        public long created, expires, spokenAt;
        public boolean auto;
    }

    public final List<Msg> msgs = new ArrayList<>();
    private int nextId = 1;
    /** auto messages this pass (keys), so ones whose cause has gone are taken down */
    private final transient Set<String> live = new HashSet<>();

    public Pis() { super(NAME); }
    public Pis(String n) { super(n); }

    public static Pis get(World w) {
        MapStorage st = w.getPerWorldStorage();
        Pis p = (Pis) st.getOrLoadData(Pis.class, NAME);
        if (p == null) { p = new Pis(); st.setData(NAME, p); }
        return p;
    }

    // ---- servers ------------------------------------------------------------------------------------------------------
    public static List<TilePisServer> servers(World w) {
        List<TilePisServer> l = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (t instanceof TilePisServer s && !t.isInvalid()) l.add(s);
        return l;
    }

    /** is there a working server this spot can reach? (the reach goes through the spot's field RTU if one covers it) */
    public static boolean reachable(World w, BlockPos at) {
        boolean any = false;
        for (TilePisServer s : servers(w)) if (s.online() && s.getPos().distanceSq(at) <= (double) s.reach() * s.reach()) { any = true; break; }
        if (!any) return false;
        com.dogpound.railmap.wire.Comms.Rtu r = com.dogpound.railmap.wire.Comms.cover(w, at);
        return r == null || !com.dogpound.railmap.wire.Comms.stale(w, r);
    }

    public static boolean anyServer(World w) { for (TilePisServer s : servers(w)) if (s.online()) return true; return false; }

    // ---- the feed -----------------------------------------------------------------------------------------------------
    public Msg post(World w, String station, Kind k, String text, String source, int minutes) {
        Msg m = new Msg();
        m.id = nextId++;
        m.station = station == null || station.isEmpty() ? "*" : station;
        m.kind = k; m.prio = k.prio; m.text = text; m.source = source;
        m.created = w.getTotalWorldTime();
        m.expires = minutes <= 0 ? 0 : m.created + minutes * 1200L;
        msgs.add(m);
        while (msgs.size() > 120) msgs.remove(0);
        Recorder.event(w, "PIS " + m.station + " [" + k + " p" + m.prio + "] " + text);
        markDirty();
        return m;
    }

    /** an auto message, kept (not re-posted) while its cause holds */
    private void auto(World w, String key, String station, Kind k, String text) {
        live.add(key);
        for (Msg m : msgs) if (m.auto && m.key.equals(key)) { m.text = text; return; }
        Msg m = post(w, station, k, text, "AUTO", 0);
        m.auto = true; m.key = key;
    }

    /** messages for a station (and the all-stations ones), highest priority first, newest first */
    public List<Msg> forStation(World w, String station) {
        long now = w.getTotalWorldTime();
        List<Msg> out = new ArrayList<>();
        for (Msg m : msgs) if ((m.expires == 0 || m.expires > now) && (m.station.equals("*") || m.station.equalsIgnoreCase(station))) out.add(m);
        out.sort((a, b) -> a.prio != b.prio ? b.prio - a.prio : Long.compare(b.created, a.created));
        return out;
    }

    /** the one line an arrivals board / ticket machine shows: "" when there's no feed (old behaviour) */
    public static String headline(World w, BlockPos at, String station) {
        if (!anyServer(w)) return "";
        if (!reachable(w, at)) return CtlConfig.pisFallback;
        List<Msg> l = get(w).forStation(w, station);
        return l.isEmpty() ? "" : (l.get(0).prio >= 8 ? "!! " : "") + l.get(0).text.toUpperCase(Locale.ROOT);
    }

    public static String ticketNote(World w, String station) {
        if (!anyServer(w) || station.isEmpty()) return "";
        List<Msg> l = get(w).forStation(w, station);
        for (Msg m : l) if (m.kind == Kind.CANCEL || m.kind == Kind.EVAC || m.kind == Kind.DELAY || m.kind == Kind.CONNECT || m.kind == Kind.ALT) return " §6⚠ " + m.text;
        return "";
    }

    public void clear(World w, String station, String who) {
        int n = 0;
        for (Msg m : msgs) if (!m.auto && (station == null || m.station.equalsIgnoreCase(station)) && (m.expires == 0 || m.expires > w.getTotalWorldTime())) { m.expires = w.getTotalWorldTime(); n++; }
        Recorder.event(w, "PIS: " + n + " operator message(s) cleared" + (station == null ? "" : " at " + station) + " by " + who);
        markDirty();
    }

    /** every 5 s while a server is in AUTO: rebuild the automatic messages and speak what is new */
    public void tick(World w, boolean autoMode) {
        long now = w.getTotalWorldTime();
        live.clear();
        StationData sd = StationData.get(w);
        RailwayData data = RailwayData.get(w);
        if (autoMode) {
            for (Map.Entry<Long, String> e : sd.names().entrySet()) {
                String name = e.getValue();
                BlockPos sp = BlockPos.fromLong(e.getKey());
                List<Announcer.Due> due;
                try { due = Announcer.due(w, e.getKey(), name); } catch (RuntimeException | LinkageError ex) { continue; }
                for (Announcer.Due d : due) {
                    Map<String, String> v = Announcer.vars(w, name, d, 0);
                    if (d.held && d.lateMin >= IncidentConfig.cancelMinutes) auto(w, "C|" + name + "|" + d.loco, name, Kind.CANCEL, Announcer.fill(IncidentConfig.tCancel, v));
                    else if (d.lateMin >= IncidentConfig.announceDelayMinutes) auto(w, "D|" + name + "|" + d.loco, name, Kind.DELAY, Announcer.fill(IncidentConfig.tDelay, v));
                    if (com.dogpound.railmap.stn.StnConfig.disruptionAlternatives && (d.held || d.lateMin >= IncidentConfig.cancelMinutes)) {   // travel alternatives (batch 3 #35)
                        int ai = 0;
                        try { for (String alt : com.dogpound.railmap.stn.Disruption.alternatives(w, e.getKey(), name, d)) auto(w, "A|" + name + "|" + d.loco + "|" + (ai++), name, Kind.ALT, alt); } catch (RuntimeException | LinkageError ignored) { }
                    }
                    // connection risk: a late train arriving after another line's next departure from here
                    if (d.lateMin > 0 && !d.dwelling) {
                        for (Announcer.Due o : due) {
                            if (o == d || o.line.equals(d.line) || o.line.isEmpty()) continue;
                            int walk = com.dogpound.railmap.stn.Interchange.walkMin(w, name, d.line, o.line);   // walking time between the platforms (batch 3 #28)
                            if (o.etaMin + o.lateMin < d.etaMin + d.lateMin + walk && d.etaMin + d.lateMin + walk - (o.etaMin + o.lateMin) <= 10) {
                                Map<String, String> cv = Announcer.vars(w, name, d, 0);
                                cv.put("line", o.line);
                                auto(w, "X|" + name + "|" + d.loco + "|" + o.line, name, Kind.CONNECT, Announcer.fill(T_CONNECT, cv));
                            }
                        }
                    }
                }
                // evacuation: a tunnel emergency or a fire alarm near the station
                String evac = null;
                for (TileTunnelControl t : TrackHooks.live(w, TileTunnelControl.class))
                    if (t.emergency() && t.getPos().distanceSq(sp) <= 256.0 * 256) { evac = "tunnel emergency " + t.id(); break; }
                if (evac == null)
                    for (AlarmManager.Alarm a : AlarmManager.get(w).all())
                        if (a.cat == AlarmManager.Cat.FIRE && a.active && a.pos != 0 && BlockPos.fromLong(a.pos).distanceSq(sp) <= 128.0 * 128) { evac = "fire alarm"; break; }
                if (evac != null) {
                    Map<String, String> v = new HashMap<>();
                    v.put("station", name); v.put("reason", evac);
                    auto(w, "E|" + name, name, Kind.EVAC, Announcer.fill(IncidentConfig.tEvacuation, v));
                }
            }
        }
        // auto messages whose cause has gone come down (evacuations leave an all-clear for 3 minutes)
        List<Msg> gone = new ArrayList<>();
        for (Msg m : msgs) if (m.auto && (m.expires == 0 || m.expires > now) && !live.contains(m.key)) gone.add(m);
        for (Msg m : gone) {
            m.expires = now;
            if (m.kind == Kind.EVAC) { Map<String, String> v = new HashMap<>(); v.put("station", m.station); post(w, m.station, Kind.CLEAR, Announcer.fill(IncidentConfig.tAllClear, v), "AUTO", 3); }
        }
        msgs.removeIf(m -> m.expires != 0 && now - m.expires > 12000);
        // speak: new messages at the announce priority, evacuations every 2 minutes
        for (Msg m : new ArrayList<>(msgs)) {
            if (m.prio < CtlConfig.pisAnnouncePriority || m.expires != 0 && m.expires <= now) continue;
            boolean repeat = m.kind == Kind.EVAC && now - m.spokenAt >= 2400;
            if (m.spokenAt != 0 && !repeat) continue;
            m.spokenAt = now;
            if (m.station.equals("*")) for (Map.Entry<Long, String> e : sd.names().entrySet()) Announcer.say(w, e.getKey(), e.getValue(), m.kind.name(), m.text);
            else for (Map.Entry<Long, String> e : sd.names().entrySet()) if (e.getValue().equalsIgnoreCase(m.station)) Announcer.say(w, e.getKey(), e.getValue(), m.kind.name(), m.text);
        }
        markDirty();
    }

    // ---- save ---------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        msgs.clear();
        nextId = Math.max(1, t.getInteger("next"));
        NBTTagList l = t.getTagList("m", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Msg m = new Msg();
            m.id = c.getInteger("id"); m.station = c.getString("st"); m.text = c.getString("tx"); m.source = c.getString("src"); m.key = c.getString("key");
            try { m.kind = Kind.valueOf(c.getString("k")); } catch (RuntimeException e) { m.kind = Kind.INFO; }
            m.prio = c.getInteger("p"); m.created = c.getLong("c"); m.expires = c.getLong("x"); m.spokenAt = c.getLong("sp"); m.auto = c.getBoolean("a");
            msgs.add(m);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        t.setInteger("next", nextId);
        NBTTagList l = new NBTTagList();
        for (Msg m : msgs) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", m.id); c.setString("st", m.station); c.setString("tx", m.text); c.setString("src", m.source); c.setString("key", m.key);
            c.setString("k", m.kind.name()); c.setInteger("p", m.prio); c.setLong("c", m.created); c.setLong("x", m.expires); c.setLong("sp", m.spokenAt); c.setBoolean("a", m.auto);
            l.appendTag(c);
        }
        t.setTag("m", l);
        return t;
    }
}
