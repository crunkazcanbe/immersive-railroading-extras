package com.dogpound.railmap.travel6;

import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Disruptions;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.economy.PassengerData;
import com.dogpound.railmap.incident.Announcer;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.*;

/**
 * The Pride Rail app's server side (batch 6b #7): every screen is built from the live railway. PLAN = the fewest-changes
 * route between two named stations over the driverless lines (each leg, the stops, where to change, the next train and
 * the fare / your card's cap); LIVE = departures at a station (Announcer.due: line, destination, minutes, late, held);
 * WALLET = your Pride Cards (balance, journey, today's cap, last taps), checked bags, parking tickets and lockers;
 * TRAINS = your company's trains (line, status, next stop, speed, earnings); ALERTS = disruptions, held lines, late
 * trains, and things of yours that need you (low balance, bags waiting, overstays, unpaid parking).
 */
public final class Phone {
    private Phone() {}

    static List<String> names(World w) {
        List<String> o = new ArrayList<>(new TreeSet<>(StationData.get(w).names().values()));
        return o;
    }

    static Long key(World w, String name) {
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        return null;
    }

    public static PacketPhone answer(EntityPlayerMP p, String op, List<String> a) {
        World w = p.world;
        PacketPhone r = new PacketPhone();
        r.op = op;
        r.stations.addAll(names(w));
        Map.Entry<Long, String> near = com.dogpound.railmap.stn.StnData.station(w, p.getPosition(), 400);
        r.here = near == null ? "" : near.getValue();
        String a0 = a.size() > 0 ? a.get(0) : "", a1 = a.size() > 1 ? a.get(1) : "";
        try {
            switch (op) {
                case "plan" -> plan(w, r, a0, a1, p);
                case "dep" -> departures(w, r, a0.isEmpty() ? r.here : a0);
                case "wallet" -> wallet(w, r, p);
                case "trains" -> trains(w, r, p);
                case "alerts" -> alerts(w, r, p);
                default -> home(w, r, p);
            }
        } catch (RuntimeException | LinkageError e) {
            r.title = "Pride Rail";
            r.lines.add("!The app hit a problem: " + e);
        }
        return r;
    }

    // ---- home ------------------------------------------------------------------------------------------------------------
    static void home(World w, PacketPhone r, EntityPlayerMP p) {
        r.title = "Pride Rail";
        r.lines.add("#Good " + (PassengerData.hour(w) < 12 ? "morning" : PassengerData.hour(w) < 18 ? "afternoon" : "evening") + ", " + p.getName());
        r.lines.add(r.here.isEmpty() ? ".No station nearby" : "Nearest station: " + r.here);
        RailwayData rd = RailwayData.get(w);
        r.lines.add(rd.lines().size() + " lines · " + r.stations.size() + " stations · " + rd.trains().size() + " trains running");
        List<TravelData.Card> cs = Cards.of(w, p.getUniqueID());
        if (!cs.isEmpty()) r.lines.add("+Pride Card " + cs.get(0).id + ": " + Coins.fmt(cs.get(0).bal) + (cs.get(0).in() ? " · on a journey from " + cs.get(0).inName : ""));
        else r.lines.add(".No Pride Card yet - get one at a Top-Up Machine");
        int n = alertCount(w, p);
        r.lines.add(n == 0 ? "+No alerts" : "~" + n + " alert(s) - see ALERTS");
        r.lines.add("");
        r.lines.add(".PLAN a journey · LIVE departures · WALLET · TRAINS · ALERTS");
    }

    // ---- journey planner -------------------------------------------------------------------------------------------------
    /** fewest changes from a to b: the lines to ride, in order (null = no route) */
    static List<String> route(RailwayData rd, long a, long b) {
        Map<String, String> prev = new HashMap<>();
        Deque<String> q = new ArrayDeque<>();
        for (RailwayData.Line l : rd.lines().values()) if (l.stations.contains(a)) { prev.put(l.name, ""); q.add(l.name); }
        while (!q.isEmpty()) {
            String cur = q.poll();
            RailwayData.Line L = rd.line(cur);
            if (L.stations.contains(b)) {
                LinkedList<String> path = new LinkedList<>();
                for (String x = cur; !x.isEmpty(); x = prev.get(x)) path.addFirst(x);
                return path;
            }
            for (RailwayData.Line m : rd.lines().values())
                if (!prev.containsKey(m.name) && !Collections.disjoint(L.stations, m.stations)) { prev.put(m.name, cur); q.add(m.name); }
        }
        return null;
    }

    /** where to change from line x to line y: their first shared station after `from` on x */
    static long changeAt(RailwayData rd, String x, String y, long from) {
        RailwayData.Line X = rd.line(x), Y = rd.line(y);
        long best = Long.MIN_VALUE;
        int bd = Integer.MAX_VALUE, i0 = X.stations.indexOf(from);
        for (int i = 0; i < X.stations.size(); i++) {
            long s = X.stations.get(i);
            if (s != from && Y.stations.contains(s) && Math.abs(i - i0) < bd) { bd = Math.abs(i - i0); best = s; }
        }
        return best;
    }

    static void plan(World w, PacketPhone r, String from, String to, EntityPlayerMP p) {
        r.title = "Journey planner";
        Long a = key(w, from.isEmpty() ? r.here : from), b = key(w, to);
        if (a == null || b == null) { r.lines.add(".Pick FROM and TO, then GO"); return; }
        if (a.equals(b)) { r.lines.add("~You are already there"); return; }
        StationData sd = StationData.get(w);
        RailwayData rd = RailwayData.get(w);
        List<String> legs = route(rd, a, b);
        r.lines.add("#" + sd.names().get(a) + "  >  " + sd.names().get(b));
        if (legs == null) { r.lines.add("!No route - no line (or chain of lines) joins these stations"); return; }
        long at = a;
        int totalStops = 0;
        for (int i = 0; i < legs.size(); i++) {
            String ln = legs.get(i);
            RailwayData.Line L = rd.line(ln);
            long next = i + 1 < legs.size() ? changeAt(rd, ln, legs.get(i + 1), at) : b;
            int stops = Math.abs(L.stations.indexOf(next) - L.stations.indexOf(at));
            totalStops += stops;
            Announcer.Due due = null;
            try { for (Announcer.Due d : Announcer.due(w, at, sd.names().getOrDefault(at, ""))) if (ln.equals(d.line)) { due = d; break; } } catch (RuntimeException | LinkageError ignored) { }
            r.lines.add((i == 0 ? "+Ride " : "+Change, ride ") + ln + " from " + Autopilot.nameOf(sd, at));
            r.lines.add("   " + stops + " stop" + (stops == 1 ? "" : "s") + " to " + Autopilot.nameOf(sd, next)
                    + (due == null ? " · .no train due" : due.dwelling ? " · train in the platform now" : " · next in " + (due.etaMin + due.lateMin) + " min" + (due.lateMin > 0 ? " (" + due.lateMin + " late)" : "")));
            if (i + 1 < legs.size()) {
                int walk = com.dogpound.railmap.stn.Interchange.walkMin(w, Autopilot.nameOf(sd, next), ln, legs.get(i + 1));
                r.lines.add(".   change at " + Autopilot.nameOf(sd, next) + " - " + walk + " min walk");
            }
            at = next;
        }
        PassengerData pd = PassengerData.get(w);
        long fare = pd.fare(w, a, b);
        r.lines.add("");
        r.lines.add("Fare " + Coins.fmt(fare) + " (zones " + pd.zoneOf(a) + " > " + pd.zoneOf(b) + (PassengerData.peak(PassengerData.hour(w)) ? ", peak" : PassengerData.night(PassengerData.hour(w)) ? ", night" : "") + ") · " + legs.size() + " train" + (legs.size() == 1 ? "" : "s") + ", " + totalStops + " stops");
        for (TravelData.Card c : Cards.of(w, p.getUniqueID())) {
            long room = Math.max(0, Cards.cap(w, c) - (c.day == Cards.day(w) ? c.spentDay : 0));
            r.lines.add("+Card " + c.id + ": pays " + Coins.fmt(Math.min(fare, room)) + (room < fare ? " (daily cap)" : "") + " · balance " + Coins.fmt(c.bal));
            break;
        }
        try { for (String h : com.dogpound.railmap.stn.Disruption.heldLines(w)) if (legs.contains(h)) r.lines.add("!" + h + " is disrupted - trains held"); } catch (RuntimeException | LinkageError ignored) { }
    }

    // ---- live departures -------------------------------------------------------------------------------------------------
    static void departures(World w, PacketPhone r, String name) {
        Long k = key(w, name);
        r.title = "Live: " + (k == null ? "-" : name);
        if (k == null) { r.lines.add(".Pick a station"); return; }
        List<Announcer.Due> due = Announcer.due(w, k, name);
        r.lines.add("#Line            To                 Due");
        if (due.isEmpty()) r.lines.add(".No trains due");
        for (Announcer.Due d : due) {
            String when = d.dwelling ? "NOW" : d.held ? "HELD" : (d.etaMin + d.lateMin) + " min";
            String col = d.held ? "!" : d.dwelling ? "+" : d.lateMin > 0 ? "~" : "";
            r.lines.add(col + String.format(Locale.ROOT, "%-16s %-18s %s", StnUtil.cut(d.line.isEmpty() ? d.train : d.line, 16), StnUtil.cut(d.dest, 18), when)
                    + (d.lateMin > 0 ? " (" + d.lateMin + " late)" : "") + (d.held && !d.reason.isEmpty() ? " - " + d.reason : ""));
        }
        PassengerData.Stn s = PassengerData.get(w).stn(k);
        if (s != null) { r.lines.add(""); r.lines.add(String.format(Locale.ROOT, ".Zone %d · %.0f waiting · crowding %.0f%%", PassengerData.get(w).zoneOf(k), waiting(s), 100 - s.crowd)); }
        r.lines.add(".Lines: " + String.join(", ", com.dogpound.railmap.stn.Interchange.lines(w, k)));
    }

    static double waiting(PassengerData.Stn s) { double n = 0; for (double v : s.waiting.values()) n += v; return n; }

    // ---- wallet ----------------------------------------------------------------------------------------------------------
    static void wallet(World w, PacketPhone r, EntityPlayerMP p) {
        r.title = "Wallet";
        List<TravelData.Card> cs = Cards.of(w, p.getUniqueID());
        if (cs.isEmpty()) r.lines.add(".No Pride Cards - get one at a Top-Up Machine");
        for (TravelData.Card c : cs) {
            r.lines.add("#Pride Card " + c.id + (c.blocked ? "  BLOCKED" : ""));
            r.lines.add((c.bal < TravelConfig.minTapIn ? "!" : "+") + "Balance " + Coins.fmt(c.bal) + (c.in() ? " · on a journey from " + c.inName : ""));
            boolean today = c.day == Cards.day(w);
            r.lines.add("Today " + Coins.fmt(today ? c.spentDay : 0) + (today && c.zlo > 0 ? " of cap " + Coins.fmt(Cards.cap(w, c)) + " (zones " + c.zlo + "-" + c.zhi + ", " + c.tripsDay + " trips)" : " · not capped yet"));
            for (int i = 0; i < Math.min(6, c.hist.size()); i++) r.lines.add(".  " + c.hist.get(i));
        }
        TravelData d = TravelData.get(w);
        List<TravelData.Bag> bags = new ArrayList<>();
        for (TravelData.Bag b : d.bags.values()) if (p.getUniqueID().equals(b.owner) && b.state != TravelData.COLLECTED) bags.add(b);
        if (!bags.isEmpty()) r.lines.add("#Checked bags");
        for (TravelData.Bag b : bags)
            r.lines.add((b.state == TravelData.RECLAIM ? "+" : "") + "Tag " + b.tag + " " + b.fromName + " > " + b.toName + " · " + TravelData.BSTATE[b.state] + (b.state == TravelData.ON_TRAIN ? " (" + b.trainName + ")" : "") + " · " + b.weightKg() + " kg");
        boolean head = false;
        for (TravelData.Park k : d.parks.values()) {
            if (!p.getUniqueID().equals(k.who) || !k.live()) continue;
            if (!head) { r.lines.add("#Parking"); head = true; }
            r.lines.add((Parking.paidUp(w, k) ? "+" : "~") + "Ticket #" + k.id + " " + k.garage + " · " + Math.max(1, (w.getTotalWorldTime() - k.in + 999) / 1000) + " h · " + (Parking.paidUp(w, k) ? "PAID" : "due " + Coins.fmt(Parking.due(w, k)) + (Parking.railSince(w, k) ? " (park & ride)" : "")));
        }
        head = false;
        for (TileLeftLuggage t : StnUtil.loaded(w, TileLeftLuggage.class))
            for (int i = 0; i < TileLeftLuggage.N; i++) if (t.rentedTo(i, p.getUniqueID())) {
                if (!head) { r.lines.add("#Left luggage"); head = true; }
                long o = t.owed(i);
                r.lines.add((o > 0 ? "~" : "+") + "Locker " + (i + 1) + " at " + t.where() + (o > 0 ? " · overstay " + Coins.fmt(o) : ""));
            }
    }

    // ---- my trains -------------------------------------------------------------------------------------------------------
    static void trains(World w, PacketPhone r, EntityPlayerMP p) {
        r.title = "My trains";
        Company c = CompanyData.get(w).of(p.getUniqueID());
        if (c == null) { r.lines.add(".You are not in a railway company - found or join one at a Company HQ"); return; }
        r.lines.add("#" + c.name + " · " + c.trains.size() + " trains · reputation " + c.reputation + " · " + Coins.fmt(c.operating));
        RailwayData rd = RailwayData.get(w);
        for (UUID u : c.trains) {
            RailwayData.AutoTrain a = rd.trains().get(u);
            if (a == null) { r.lines.add(".(a train not in driverless service)"); continue; }
            Autopilot.Run run = Autopilot.run(u);
            boolean held = Disruptions.isHeld(w.provider.getDimension(), u);
            int late = Disruptions.delaySeconds(u) / 60;
            String name = a.label.isEmpty() ? "train " + u.toString().substring(0, 4) : a.label;
            r.lines.add((held ? "!" : late > 0 ? "~" : "+") + name + " · " + (a.line.isEmpty() ? a.mode.name() : a.line));
            r.lines.add("   " + (run == null ? "not running" : run.dwelling ? "at " + Autopilot.nameOf(StationData.get(w), run.dwellStation) : run.status + (run.nextStopName.isEmpty() ? "" : " > " + run.nextStopName))
                    + (run == null ? "" : String.format(Locale.ROOT, " · %.0f km/h", run.speedKmh)) + (late > 0 ? " · " + late + " min late" : "") + " · earned " + Coins.fmt(a.earned));
        }
        if (c.trains.isEmpty()) r.lines.add(".No trains yet - add them at the Company HQ");
    }

    // ---- alerts ----------------------------------------------------------------------------------------------------------
    static List<String> alertLines(World w, EntityPlayerMP p) {
        List<String> o = new ArrayList<>();
        int dim = w.provider.getDimension();
        for (Disruptions.Incident in : Disruptions.incidents(dim)) o.add("!" + Disruptions.why(in));
        try { for (String l : com.dogpound.railmap.stn.Disruption.heldLines(w)) o.add("!" + l + ": trains held"); } catch (RuntimeException | LinkageError ignored) { }
        RailwayData rd = RailwayData.get(w);
        for (RailwayData.AutoTrain a : rd.trains().values()) {
            int late = Disruptions.delaySeconds(a.loco) / 60;
            if (late >= 3) o.add("~" + (a.label.isEmpty() ? "A train" : a.label) + (a.line.isEmpty() ? "" : " (" + a.line + ")") + " running " + late + " min late");
        }
        for (TravelData.Card c : Cards.of(w, p.getUniqueID())) {
            if (c.blocked) o.add("~Card " + c.id + " is blocked (reported lost)");
            else if (c.bal < TravelConfig.minTapIn) o.add("~Card " + c.id + " balance " + Coins.fmt(c.bal) + " - top up to travel");
            if (c.in() && w.getTotalWorldTime() - c.inAt > 2000) o.add("~Card " + c.id + " still tapped in at " + c.inName + " - tap out or it costs the maximum fare");
        }
        TravelData d = TravelData.get(w);
        for (TravelData.Bag b : d.bags.values()) if (p.getUniqueID().equals(b.owner) && b.state == TravelData.RECLAIM) o.add("+Bag " + b.tag + " is waiting for you at " + b.toName);
        for (TravelData.Park k : d.parks.values()) if (p.getUniqueID().equals(k.who) && k.live() && w.getTotalWorldTime() - k.in > 12000) o.add("~Parked " + (w.getTotalWorldTime() - k.in) / 1000 + " h at " + k.garage);
        for (TileLeftLuggage t : StnUtil.loaded(w, TileLeftLuggage.class))
            for (int i = 0; i < TileLeftLuggage.N; i++) if (t.rentedTo(i, p.getUniqueID()) && t.owed(i) > 0) o.add("~Locker " + (i + 1) + " at " + t.where() + " overstayed - " + Coins.fmt(t.owed(i)));
        return o;
    }

    static int alertCount(World w, EntityPlayerMP p) { return alertLines(w, p).size(); }

    static void alerts(World w, PacketPhone r, EntityPlayerMP p) {
        r.title = "Alerts";
        List<String> a = alertLines(w, p);
        if (a.isEmpty()) r.lines.add("+All good - no disruptions, nothing needs you");
        r.lines.addAll(a);
    }
}
