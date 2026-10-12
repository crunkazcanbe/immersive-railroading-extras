package com.dogpound.railmap.stn;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Disruptions;

import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.station.Pis;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * Disruption Control Desk (ChatGPT batch 3 #35): the customer-information side of an incident. PREV / NEXT pick a
 * station; the screens show the disrupted services there and the alternatives worked out for their passengers. POST
 * puts the alternatives on every PIS board at the station for 30 minutes (operator messages), SAY announces them now,
 * CLEAR takes this desk's messages down. TICKETS key: ticket acceptance on every line and operator (posted at every
 * affected station while on); BUSES key: rail replacement buses from the forecourt to the stations beyond the
 * blockage. The DISRUPTION lamp flashes while any service is held or cancelled anywhere. 12 V.
 */
public class TileDisruptionDesk extends StnPanel {
    private int stI;
    private final List<Integer> mine = new ArrayList<>();       // PIS message ids this desk posted (CLEAR / keys off)
    private final Map<String, Integer> keyMsgs = new HashMap<>();
    private String sel = "";
    private long selKey;

    @Override public String wiredName() { return "Disruption control desk"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : !mainsOk() ? "NO MAINS" : Disruptions.incidents(world.provider.getDimension()).size() + " incidents · " + sel; }

    private List<Map.Entry<Long, String>> stations() {
        List<Map.Entry<Long, String>> l = new ArrayList<>(StationData.get(world).names().entrySet());
        l.sort(Map.Entry.comparingByValue());
        return l;
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        List<Map.Entry<Long, String>> st = stations();
        switch (id) {
            case "prev", "next" -> {
                if (st.isEmpty()) return "§7No named stations";
                stI = Math.floorMod(stI + (id.equals("next") ? 1 : -1), st.size());
                return "§e" + st.get(stI).getValue();
            }
            case "post" -> {
                if (selKey == 0) return "§7Pick a station";
                int n = 0;
                Pis p = Pis.get(world);
                for (List<String> alts : Disruption.at(world, selKey, sel).values())
                    for (String a : alts) { mine.add(p.post(world, sel, Pis.Kind.ALT, a, who(who), 30).id); n++; }
                markDirty();
                return n == 0 ? "§7No disrupted services at " + sel : "§aPosted " + n + " alternative(s) on the boards at " + sel;
            }
            case "say" -> {
                if (selKey == 0) return "§7Pick a station";
                int n = 0;
                for (List<String> alts : Disruption.at(world, selKey, sel).values()) for (String a : alts) { Announcer.say(world, selKey, sel, "ALT", a); n++; }
                return n == 0 ? "§7Nothing to announce at " + sel : "§aAnnounced " + n + " alternative(s)";
            }
            case "clear" -> { int n = expire(mine); mine.clear(); markDirty(); return "§e" + n + " desk message(s) taken down"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (!id.equals("k_tkt") && !id.equals("k_bus")) return null;
        ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
        boolean on = ctl.get(id) == 1;
        if (!on) { Integer m = keyMsgs.remove(id); if (m != null) expire(Collections.singletonList(m)); }
        Recorder.event(world, (id.equals("k_tkt") ? "Ticket acceptance " : "Rail replacement buses ") + (on ? "ON" : "OFF") + " (disruption desk, " + who(who) + ")");
        markDirty();
        return id.equals("k_tkt") ? (on ? "§aTicket acceptance ON - any line, any operator" : "§eTicket acceptance off") : (on ? "§aRail replacement buses called" : "§eReplacement buses stood down");
    }

    private int expire(Collection<Integer> ids) {
        int n = 0;
        long now = world.getTotalWorldTime();
        for (Pis.Msg m : Pis.get(world).msgs) if (ids.contains(m.id) && (m.expires == 0 || m.expires > now)) { m.expires = now; n++; }
        Pis.get(world).markDirty();
        return n;
    }

    /** keep a key's network-wide message up while the key is on (one message, refreshed) */
    private void keep(String id, String text) {
        Integer have = keyMsgs.get(id);
        long now = world.getTotalWorldTime();
        if (have != null) for (Pis.Msg m : Pis.get(world).msgs) if (m.id == have && (m.expires == 0 || m.expires > now)) { if (!m.text.equals(text)) { m.text = text; Pis.get(world).markDirty(); } return; }
        keyMsgs.put(id, Pis.get(world).post(world, "*", Pis.Kind.ALT, text, "DISRUPTION DESK", 0).id);
        markDirty();
    }

    @Override
    protected void work() {
        if (age % 40 != 11) return;
        List<Map.Entry<Long, String>> st = stations();
        if (st.isEmpty()) { sel = ""; selKey = 0; }
        else { Map.Entry<Long, String> e = st.get(Math.floorMod(stI, st.size())); sel = e.getValue(); selKey = e.getKey(); }
        int dim = world.provider.getDimension();
        int incidents = 0, heldN = 0;
        for (Disruptions.Incident in : Disruptions.incidents(dim)) if (in.blocking()) { incidents++; heldN += in.held.size(); }
        Map<Announcer.Due, List<String>> here = selKey == 0 || !live() ? Collections.emptyMap() : Disruption.at(world, selKey, sel);
        List<String> lines = new ArrayList<>();
        int lateSum = 0;
        for (Map.Entry<Announcer.Due, List<String>> e : here.entrySet()) {
            Announcer.Due d = e.getKey();
            lateSum += d.lateMin;
            lines.add((d.held ? "HELD " : "LATE ") + StnUtil.up(d.train) + " " + d.lateMin + "M " + StnUtil.up(d.reason));
            for (String a : e.getValue()) lines.add("> " + StnUtil.up(a.replaceFirst("^Disruption on the [^:]*: ", "")));
        }
        for (int i = 0; i < 6; i++) text("a" + (i + 1), i < lines.size() ? StnUtil.cut(lines.get(i), 46) : "");
        text("stn", sel.isEmpty() ? "NO STATIONS" : StnUtil.up(sel));
        text("inc", incidents + " INC  " + heldN + " HELD");
        put("late", here.isEmpty() ? 0 : Math.min(60, lateSum / here.size()));
        boolean any = incidents > 0 || !here.isEmpty();
        put("pwr", powered()); put("disl", any && live()); put("tktl", ctl.get("k_tkt") == 1); put("busl", ctl.get("k_bus") == 1);
        put("nalt", lines.size());
        if (live()) {
            Set<String> held = Disruption.heldLines(world);
            if (ctl.get("k_tkt") == 1) keep("k_tkt", "Ticket acceptance: tickets are valid on all lines and all operators by any reasonable route" + (held.isEmpty() ? "" : " while the " + String.join(", ", held) + " line is disrupted"));
            if (ctl.get("k_bus") == 1) keep("k_bus", "Rail replacement buses are running from the station forecourt" + (held.isEmpty() ? "" : " for stations on the " + String.join(", ", held) + " line"));
        }
    }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setInteger("stI", stI);
        t.setIntArray("mine", mine.stream().mapToInt(Integer::intValue).toArray());
        NBTTagCompound k = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : keyMsgs.entrySet()) k.setInteger(e.getKey(), e.getValue());
        t.setTag("keyMsgs", k);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        stI = t.getInteger("stI");
        mine.clear();
        for (int i : t.getIntArray("mine")) mine.add(i);
        keyMsgs.clear();
        NBTTagCompound k = t.getCompoundTag("keyMsgs");
        for (String s : k.getKeySet()) keyMsgs.put(s, k.getInteger(s));
    }
}
