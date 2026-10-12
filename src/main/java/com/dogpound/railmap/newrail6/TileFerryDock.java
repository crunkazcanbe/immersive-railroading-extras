package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.grid.Elec;
import com.dogpound.railmap.infra.Supply;
import com.dogpound.railmap.sound.PackSounds;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Train ferry dock (batch 6 #17): the link span's control cabin at the water's edge, beside the dock track. Hydraulic
 * power pack (480 V / 208 V 3-phase) lifts and lowers the link span onto the ferry's stern; 12 V for the controls.
 * ENABLE key, ROUTE number (two docks on the same route pair up: the first is A, the second B), LOAD wagons / all,
 * RAISE / LOWER span, STERN DOOR, LOAD, UNLOAD, DEPART, HORN, ACK, LAMP TEST, E-STOP (span hydraulics stop, a departure
 * is called off). Interlocked: the span lowers only onto a berthed ferry, the door opens only onto a seated span, cars
 * move only over a seated span with the door open, the ferry sails only with the span up and the door shut. Until the
 * span is seated with the door open the dock track ends in the water: signals within 20 blocks are held at danger and
 * driverless trains are stopped short.
 */
public class TileFerryDock extends NR6Tile {
    static final String[] PAGES = {"OVERVIEW", "DECK", "VOYAGE", "EVENTS"};
    /** span: 0 = raised clear, 100 = seated on the ferry */
    double span;
    int moving;
    double bar;
    private int chSeen = -1;
    private boolean held;

    public TileFerryDock() { super(new Supply(Elec.Level.AC480, Elec.Level.AC208)); }

    @Override public String wiredName() { return "Ferry dock " + pos.getX() + "," + pos.getZ(); }

    int ch() { return 1 + Math.max(0, Math.min(7, ctl.get("s_route"))); }

    NR6Data.Route route() { return NR6Data.get(world).route(ch()); }

    /** this dock's end of route r: 0 A, 1 B, -1 not on it */
    int end(NR6Data.Route r) { return r.dockA == pos.toLong() ? 0 : r.dockB == pos.toLong() ? 1 : -1; }

    boolean ferryIn(NR6Data.Route r) { int k = end(r); return k >= 0 && r.phase == NR6Data.BERTHED && r.at == k; }

    boolean seated(NR6Data.Route r) { return span >= 99.9 && ferryIn(r); }

    @Override
    public String gearState() {
        NR6Data.Route r = route();
        int k = end(r);
        return mains.status().replaceAll("§.", "") + " · route " + ch() + (k < 0 ? " (not paired)" : " dock " + (k == 0 ? "A" : "B")) + " · ferry " + ferryState(r) + " · span " + f0(span) + "%";
    }

    String ferryState(NR6Data.Route r) {
        int k = end(r);
        if (!r.paired()) return "WAITING FOR THE OTHER DOCK";
        if (r.phase == NR6Data.SAILING) return String.format(Locale.ROOT, "AT SEA %.0f%%", r.s / Math.max(1, Ferry.length(r)) * 100);
        int here = r.phase == NR6Data.ARRIVING ? 1 - r.at : r.at;
        return NR6Data.PHASE[r.phase] + (here == k ? " HERE" : " AT OTHER DOCK");
    }

    @Override public int wiredLamp() { return !powered() ? 3 : !mains.ok ? 2 : 1; }

    @Override
    protected void serverTick() {
        NR6Data d = NR6Data.get(world);
        if (chSeen != ch()) {                                                   // the route knob moved: leave the old route
            if (chSeen > 0) { NR6Data.Route o = d.route(chSeen); if (o.dockA == pos.toLong()) o.dockA = 0; if (o.dockB == pos.toLong()) o.dockB = 0; d.changed(); }
            chSeen = ch();
        }
        NR6Data.Route r = route();
        if (age % 20 == 4) {
            mainsTick(moving != 0 ? 22000 : 800);
            claim(d, r);
            if (age % 100 == 4) berth(r);
        }
        int k = end(r);
        if (k >= 0) { r.seen[k] = world.getTotalWorldTime(); r.seated[k] = seated(r); }
        boolean estop = ctl.get("m_estop") == 1;
        // span hydraulics
        if (moving != 0) {
            if (estop || !live()) { moving = 0; event(estop ? "SPAN STOPPED - E-STOP" : "SPAN STOPPED - " + mains.what); }
            else {
                span = Math.max(0, Math.min(100, span + moving * 0.5));
                bar = 160 + 20 * Math.sin(age * 0.3);
                if (age % 30 == 0) PackSounds.play(world, pos, "machine.pump", 0.7f, 0.9f);
                if (span <= 0 || span >= 100) { event(span >= 100 ? (ferryIn(r) ? "SPAN SEATED ON FERRY" : "SPAN DOWN") : "SPAN RAISED"); moving = 0; }
            }
        } else bar = Math.max(live() ? 40 : 0, bar - 4);
        if (span > 0 && k >= 0 && !ferryIn(r) && r.phase != NR6Data.BERTHED) span = 0;      // (old saves: no ferry under it)
        if (estop && k >= 0 && r.phase == NR6Data.DEPARTING && r.at == k) { r.phase = NR6Data.BERTHED; d.changed(); event("DEPARTURE CALLED OFF - E-STOP"); }
        if (age % 5 != 0) return;
        boolean on = powered();
        boolean in = ferryIn(r), seat = seated(r);
        held = !(seat && r.doorOpen);
        put("l_pwr", on);
        put("l_main", on && mains.ok);
        put("l_up", on && span <= 0.1);
        put("l_down", on && span >= 99.9);
        put("l_seat", on && seat);
        put("l_in", on && in);
        put("l_door", on && in && r.doorOpen);
        put("l_sail", on && r.paired() && r.phase != NR6Data.BERTHED);
        put("l_pump", on && moving != 0);
        put("l_flt", on && (!mains.ok || estop));
        put("l_hold", on && held);
        put("g_span", (int) Math.round(span));
        put("g_bar", (int) Math.min(100, bar / 2));
        put("g_deck", (int) Math.min(100, r.deck.size() * 100.0 / NR6Config.ferryCapacity));
        switch (page(PAGES.length)) {
            case 0 -> screen("ROUTE " + ch() + (k < 0 ? " NOT PAIRED" : " DOCK " + (k == 0 ? "A" : "B")),
                    "FERRY " + ferryState(r),
                    "SPAN " + f0(span) + "% " + (seat ? "SEATED" : span <= 0.1 ? "RAISED" : moving != 0 ? "MOVING" : "PART"),
                    "STERN DOOR " + (in ? r.doorOpen ? "OPEN" : "SHUT" : "-"),
                    "DECK " + r.deck.size() + "/" + NR6Config.ferryCapacity + " CARS",
                    held ? "DOCK SIGNALS HELD" : "DOCK SIGNALS FREE");
            case 1 -> {
                String[] o = new String[6];
                o[0] = "ON THE DECK " + r.deck.size();
                for (int i = 1; i < 6; i++) o[i] = i - 1 < r.deck.size() ? i + " " + r.deck.get(i - 1).getString("name") : (i == 1 ? "EMPTY" : "");
                if (r.deck.size() > 5) o[5] = "+" + (r.deck.size() - 4) + " MORE";
                screen(o);
            }
            case 2 -> {
                double L = Ferry.length(r);
                int other = k < 0 ? -1 : 1 - k;
                long od = other < 0 ? 0 : r.dock(other);
                screen("CROSSING " + f0(L) + " M",
                        r.phase == NR6Data.SAILING ? String.format(Locale.ROOT, "SPEED %.1f M/S %.0f KN", r.v, r.v * 1.944) : "SPEED 0",
                        r.phase == NR6Data.SAILING ? "ETA " + Math.round((L - r.s) / Math.max(0.5, r.v)) + " S" : "ETA -",
                        "VOYAGES " + r.voyages,
                        od == 0 ? "OTHER DOCK -" : "OTHER DOCK " + BlockPos.fromLong(od).getX() + "," + BlockPos.fromLong(od).getZ(),
                        r.berth[Math.max(0, k)] == null ? "BERTH NOT FOUND - WATER?" : "BERTH OK");
            }
            default -> screen(logLines("EVENTS", 6));
        }
    }

    /** join the route as end A or B */
    private void claim(NR6Data d, NR6Data.Route r) {
        long me = pos.toLong();
        for (int k = 0; k < 2; k++) {                                         // a dock gone from the route frees its end
            long o = r.dock(k);
            if (o == 0 || o == me) continue;
            BlockPos q = BlockPos.fromLong(o);
            if (world.isBlockLoaded(q) && !(world.getTileEntity(q) instanceof TileFerryDock t && t.ch() == r.ch)) { if (k == 0) r.dockA = 0; else r.dockB = 0; d.changed(); }
        }
        if (end(r) >= 0) return;
        if (r.dockA == 0) { r.dockA = me; d.changed(); event("JOINED ROUTE " + r.ch + " AS DOCK A"); }
        else if (r.dockB == 0) { r.dockB = me; d.changed(); event("JOINED ROUTE " + r.ch + " AS DOCK B"); }
    }

    /** find the berth: the nearest open water within 12 blocks, the ship lying 10 m out from it */
    private void berth(NR6Data.Route r) {
        int k = end(r);
        if (k < 0) return;
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int dy = -4; dy <= 1; dy++) {
                    BlockPos q = pos.add(dx, dy, dz);
                    if (!world.isBlockLoaded(q) || world.getBlockState(q).getMaterial() != Material.WATER || world.getBlockState(q.up()).getMaterial() == Material.WATER) continue;
                    double dd = dx * dx + dz * dz;
                    if (dd < bd && dd >= 4) { bd = dd; best = q; }
                }
        if (best == null) { if (r.berth[k] != null) { r.berth[k] = null; NR6Data.get(world).changed(); } return; }
        double dx = best.getX() - pos.getX(), dz = best.getZ() - pos.getZ(), n = Math.max(1e-6, Math.hypot(dx, dz));
        double[] b = {best.getX() + 0.5 + dx / n * 10, best.getY() + 0.9, best.getZ() + 0.5 + dz / n * 10};
        float out = Ferry.yaw(dx, dz);
        if (r.berth[k] == null || Math.abs(r.berth[k][0] - b[0]) > 0.5 || Math.abs(r.berth[k][2] - b[2]) > 0.5) {
            if (r.phase == NR6Data.SAILING) return;                           // never move a berth under a ship at sea
            r.berth[k] = b; r.out[k] = out; NR6Data.get(world).changed();
        }
    }

    @Override
    public void holds(Map<Long, String> hold, List<Object[]> zones) {
        if (!held) return;
        String why = "ferry dock " + pos.getX() + "," + pos.getZ() + ": link span not seated on a ferry";
        zones.add(new Object[]{pos.getX() + 0.5, pos.getZ() + 0.5, 20.0, why});
    }

    @Override
    public String controlPress(String id, EntityPlayer p) {
        if (id.equals("test")) return lampTest();
        if (id.startsWith("s_") || id.startsWith("k_") || id.startsWith("m_")) return null;
        if (!powered()) return "§cCabin dark - no 12 V";
        NR6Data d = NR6Data.get(world);
        NR6Data.Route r = route();
        int k = end(r);
        if (id.equals("ack")) { clearAlarm("ferry:" + pos.toLong()); return "§7Acknowledged"; }
        if (id.equals("horn")) {
            double[] b = k >= 0 ? r.berth[k] : null;
            if (b != null && ferryIn(r)) PackSounds.play(world, b[0], b[1], b[2], "station.ship_horn", 4f, 0.8f);
            else PackSounds.play(world, pos, "station.ship_horn", 2f, 1f);
            return "§7Horn";
        }
        if (ctl.get("k_en") != 1) return "§cTurn the ENABLE key first";
        if (ctl.get("m_estop") == 1) return "§cE-STOP is in - pull it out first";
        if (k < 0) return "§cThis dock is not on route " + ch() + " - two docks on one route number pair up";
        switch (id) {
            case "raise" -> {
                if (!live()) return "§cPower pack has no mains: " + mains.what;
                if (r.doorOpen && ferryIn(r)) return "§cShut the stern door first";
                if (span <= 0) return "§7Span already raised";
                moving = -1; event("SPAN RAISING"); return "§eRaising the link span";
            }
            case "lower" -> {
                if (!live()) return "§cPower pack has no mains: " + mains.what;
                if (!ferryIn(r)) return "§cNo ferry at the berth - the span would go into the water";
                if (span >= 100) return "§7Span already seated";
                moving = 1; event("SPAN LOWERING"); return "§eLowering the link span onto the ferry";
            }
            case "door" -> {
                if (!ferryIn(r)) return "§cNo ferry at the berth";
                if (!r.doorOpen && !seated(r)) return "§cSeat the span first - the door comes down onto it";
                r.doorOpen = !r.doorOpen; d.changed();
                event(r.doorOpen ? "STERN DOOR OPEN" : "STERN DOOR SHUT");
                return r.doorOpen ? "§eStern door open" : "§aStern door shut";
            }
            case "load", "unload" -> {
                if (!seated(r) || !r.doorOpen) return "§cSpan must be seated on the ferry and the stern door open";
                String msg = id.equals("load") ? Ferry.load(world, this, r, ctl.get("s_what") == 0) : Ferry.unload(world, this, r);
                event(msg.replaceAll("§.", ""));
                return msg;
            }
            case "depart" -> {
                if (!r.paired()) return "§cNo other dock on route " + r.ch;
                if (!ferryIn(r)) return "§cThe ferry is not berthed here";
                if (r.doorOpen) return "§cShut the stern door";
                if (span > 0) return "§cRaise the link span clear";
                if (!r.berths()) return "§cA berth has no water to lie in (place the dock at the water's edge)";
                if (Ferry.length(r) < 32) return "§cThe docks are too close for a crossing (under 32 m)";
                BlockPos bl = Ferry.blocked(world, r);
                if (bl != null) {
                    alarm("ferry:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "Ferry route " + r.ch + " not navigable: land / obstruction at " + bl.getX() + "," + bl.getZ());
                    return "§cRoute not navigable - land or an obstruction at " + bl.getX() + "," + bl.getZ();
                }
                r.phase = NR6Data.DEPARTING; r.phaseAt = world.getTotalWorldTime(); d.changed();
                double[] b = r.berth[k];
                PackSounds.play(world, b[0], b[1], b[2], "station.ship_horn", 4f, 0.8f);
                event("FERRY DEPARTING WITH " + r.deck.size() + " CARS");
                return "§aFerry casting off - " + r.deck.size() + " cars aboard";
            }
            default -> { return null; }
        }
    }

    @Override protected void storeMore(NBTTagCompound t) { t.setDouble("span", span); t.setInteger("ch", chSeen); }
    @Override protected void restoreMore(NBTTagCompound t) { span = t.getDouble("span"); chSeen = t.hasKey("ch") ? t.getInteger("ch") : -1; }
}
