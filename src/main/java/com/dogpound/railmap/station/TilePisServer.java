package com.dogpound.railmap.station;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.incident.IncidentConfig;
import com.dogpound.railmap.safety.SafetyTile;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * PIS Server rack (her "next 48" list #30): the head-end of the passenger information system. It owns the live feed
 * ({@link Pis}) that every PIS board, arrivals board, ticket machine and PA reads.
 * <ul>
 *   <li>MODE: OFF / AUTO (it writes delays, cancellations, connection risks and evacuations from live data) / MANUAL
 *       (operator messages only); REACH 256 / 512 / 1024 / 2048 m - signs further than that are out of its network;</li>
 *   <li>STN &lt; &gt; picks the station the operator buttons work on (ALL = every station); TPL &lt; &gt; the message template;</li>
 *   <li>PUBLISH puts the template up as information, SECURITY as a priority-8 security message, PLATFORM ALTERATION
 *       moves the next train due to the PLATFORM selector's number; the red EVACUATE mushroom evacuates the station
 *       (and speaks it); ALL CLEAR ends it; CLEAR takes the operator messages down;</li>
 *   <li>screen: the station, the template, the top four messages in the feed, counts.</li>
 * </ul>
 * 12 V for the rack. Without a working server every sign shows the fallback message.
 */
public class TilePisServer extends SafetyTile implements com.dogpound.railmap.block.IFurniture {
    private final com.dogpound.railmap.ctl.Drawers drawers = new com.dogpound.railmap.ctl.Drawers(this, new String[]{"dr1"}, new String[]{"Spare boards drawer"}, new int[]{18});
    @Override public net.minecraft.inventory.IInventory storage(int k) { return drawers.storage(k); }
    @Override public String storeName(int k) { return drawers.name(k); }
    @Override public int storeSize(int k) { return drawers.size(k); }
    @Override public void dropAll() { drawers.dropAll(this); }

    public static final int[] REACH = {256, 512, 1024, 2048};
    private int stn = -1, tpl;
    private String evacKey = "";

    @Override public String wiredName() { return "PIS server " + Math.floorMod(pos.getX() * 5 + pos.getZ() * 3, 100); }
    public boolean online() { return powered() && ctl.get("s_mode") != 0; }
    public boolean autoMode() { return online() && ctl.get("s_mode") == 1; }
    public int reach() { return REACH[Math.min(3, ctl.get("s_reach"))]; }

    private List<String> stations() { List<String> l = new ArrayList<>(new TreeSet<>(StationData.get(world).names().values())); l.removeIf(String::isEmpty); return l; }
    private String station() { List<String> l = stations(); return stn < 0 || stn >= l.size() ? "*" : l.get(stn); }

    @Override public String gearState() { return !powered() ? "OFF - no 12 V (signs on fallback)" : !online() ? "mode OFF (signs on fallback)" : (autoMode() ? "AUTO" : "MANUAL") + " · " + Pis.get(world).forStation(world, "*").size() + " all-station message(s)"; }
    @Override public int wiredLamp() { return !online() ? 3 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("online", online()); m.put("auto", autoMode()); m.put("messages", Pis.get(world).msgs.size()); m.put("station", station()); }

    @Override
    protected void serverTick() {
        if (age % 20 != 11) return;
        Pis p = Pis.get(world);
        // the lowest-placed online server runs the feed (several racks = redundancy)
        boolean leader = online();
        boolean anyAuto = false;
        for (TilePisServer s : Pis.servers(world)) {
            if (!s.online()) continue;
            anyAuto |= s.autoMode();
            if (s != this && s.getPos().toLong() < pos.toLong()) leader = false;
        }
        if (leader && age % 100 == 11) p.tick(world, anyAuto);
        String st = station();
        List<Pis.Msg> feed = p.forStation(world, st.equals("*") ? "~none~" : st);
        int autoN = 0;
        for (Pis.Msg m : feed) if (m.auto) autoN++;
        put("pwr", powered());
        put("run", online());
        put("auto", autoMode());
        put("lead", leader);
        put("evac", !feed.isEmpty() && feed.get(0).kind == Pis.Kind.EVAC);
        put("msgs", !feed.isEmpty());
        text("stn", "STN " + (st.equals("*") ? "ALL STATIONS" : st.toUpperCase(Locale.ROOT)));
        text("tpl", "TPL " + (tpl + 1) + " " + Pis.INFO_TEMPLATES[tpl].toUpperCase(Locale.ROOT));
        for (int i = 0; i < 4; i++) text("l" + (i + 1), i < feed.size() ? "P" + feed.get(i).prio + " " + feed.get(i).kind + " " + feed.get(i).text.toUpperCase(Locale.ROOT) : "");
        text("cnt", "MSG " + feed.size() + " AUTO " + autoN + (leader ? " MASTER" : " STANDBY"));
        text("status", !powered() ? "" : !online() ? "SERVER OFF - SIGNS ON FALLBACK" : autoMode() ? "AUTO FEED RUNNING" : "MANUAL - OPERATOR ONLY");
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        { String dr = drawers.open(id, who, this); if (dr != null) return dr; }
        switch (id) {
            case "test": return lampTest();
            case "stn<": case "stn>": { int n = stations().size(); stn += id.endsWith(">") ? 1 : -1; if (stn >= n) stn = -1; if (stn < -1) stn = n - 1; return "§eStation: " + (station().equals("*") ? "ALL" : station()); }
            case "tpl<": case "tpl>": { int n = Pis.INFO_TEMPLATES.length; tpl = ((tpl + (id.endsWith(">") ? 1 : -1)) % n + n) % n; return "§eTemplate: " + Pis.INFO_TEMPLATES[tpl]; }
            default: break;
        }
        if (!online()) return "§cThe PIS server is " + (powered() ? "switched OFF" : "dead - no 12 V");
        Pis p = Pis.get(world);
        String st = station();
        String who2 = "operator " + who(who);
        switch (id) {
            case "pub": p.post(world, st, Pis.Kind.INFO, Pis.INFO_TEMPLATES[tpl], who2, 60); return "§aPublished to " + (st.equals("*") ? "every station" : st);
            case "sec": p.post(world, st, Pis.Kind.SECURITY, "Security announcement: " + Pis.INFO_TEMPLATES[tpl], who2, 30); return "§6Security message published";
            case "plat": {
                if (st.equals("*")) return "§cPick one station (STN) for a platform alteration";
                int plat = ctl.get("s_plat") + 1;
                Long sid = null;
                for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) if (e.getValue().equals(st)) sid = e.getKey();
                List<Announcer.Due> due = sid == null ? Collections.emptyList() : Announcer.due(world, sid, st);
                if (due.isEmpty()) return "§7No train due at " + st;
                Map<String, String> v = Announcer.vars(world, st, due.get(0), plat);
                p.post(world, st, Pis.Kind.PLATFORM, Announcer.fill(IncidentConfig.tPlatform, v), who2, 20);
                return "§ePlatform alteration: " + due.get(0).train + " now platform " + plat;
            }
            case "m_evac": {
                ctl.set("m_evac", 1 - ctl.get("m_evac"));
                if (ctl.get("m_evac") == 1) {
                    Map<String, String> v = new HashMap<>(); v.put("station", st.equals("*") ? "every station" : st);
                    p.post(world, st, Pis.Kind.EVAC, Announcer.fill(IncidentConfig.tEvacuation, v), who2, 0);
                    Recorder.event(world, "PIS: EVACUATION of " + st + " by " + who(who));
                    return "§c§lEVACUATION published + announced at " + (st.equals("*") ? "every station" : st);
                }
                return "§7Mushroom released - press ALL CLEAR to end the evacuation";
            }
            case "allclr": {
                if (ctl.get("m_evac") == 1) return "§cRelease the EVACUATE mushroom first";
                long now = world.getTotalWorldTime();
                for (Pis.Msg m : p.msgs) if (m.kind == Pis.Kind.EVAC && !m.auto && (st.equals("*") || m.station.equalsIgnoreCase(st)) && (m.expires == 0 || m.expires > now)) m.expires = now;
                Map<String, String> v = new HashMap<>(); v.put("station", st);
                p.post(world, st, Pis.Kind.CLEAR, Announcer.fill(IncidentConfig.tAllClear, v), who2, 3);
                return "§aAll clear published";
            }
            case "clr": p.clear(world, st.equals("*") ? null : st, who(who)); return "§7Operator messages cleared";
            default: return null;
        }
    }

    @Override protected void save(NBTTagCompound t) { drawers.write(t); t.setInteger("stn", stn); t.setInteger("tpl", tpl); }
    @Override protected void load(NBTTagCompound t) { drawers.read(t); stn = t.hasKey("stn") ? t.getInteger("stn") : -1; tpl = Math.floorMod(t.getInteger("tpl"), Pis.INFO_TEMPLATES.length); }
}
