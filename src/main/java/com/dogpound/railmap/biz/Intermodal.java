package com.dogpound.railmap.biz;

import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.economy.Company;
import com.dogpound.railmap.economy.CompanyData;
import com.dogpound.railmap.item.ItemContainer;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Intermodal logistics (her "next 48" #15): the network-wide side of ports, container terminals, trucks and warehouses.
 * <ul>
 *   <li><b>Truck appointments</b>: each gate (a Container Terminal desk or a Port Berth) takes TRUCKS_PER_HOUR trucks an
 *   hour. Book a slot (APPT); a booked truck is let through in its hour (+/- 1); walk-ins only while the hour isn't full
 *   of booked trucks; an unused booking becomes a NO-SHOW.</li>
 *   <li><b>Through freight</b>: an import box landed at a port carries its freight rate to its inland destination; whoever
 *   strips it at a terminal by that station is paid (to their company, if they have one).</li>
 *   <li>Every port and terminal reports its throughput (lifts, gate moves, ship moves, dwell) here for the console.</li>
 * </ul>
 * The berths themselves are TilePortBerth; cargo tracking across the modes is the custody record's history (#17).
 */
public final class Intermodal implements BizData.Part {
    public static final int TRUCKS_PER_HOUR = 2;

    public static final class Appt {
        public long gate;            // the gate desk's position
        public long hour;            // absolute game hour (world ticks / 1000)
        public String who = "";
        public String state = "BOOKED";   // BOOKED, KEPT, NO-SHOW
    }

    public static final class Stat {
        public String name = "";
        public long pos;
        public int shipMoves, gateMoves, railMoves, quay, quayCap, walkIns, kept, noShows;
        public long fees, demurrage;
        public boolean port;
        public long seen;
    }

    public final List<Appt> appts = new ArrayList<>();
    public final Map<Long, Stat> stats = new LinkedHashMap<>();
    public final LinkedList<String> log = new LinkedList<>();
    public long throughFreightPaid;

    @Override public String key() { return "intermodal"; }

    public Stat stat(World w, BlockPos p, boolean port) {
        Stat s = stats.computeIfAbsent(p.toLong(), k -> new Stat());
        s.pos = p.toLong(); s.port = port; s.seen = w.getTotalWorldTime();
        if (s.name.isEmpty()) s.name = Towns.nearestStation(w, p, p.getX() + " " + p.getZ()) + (port ? " port" : " terminal");
        return s;
    }

    public void note(World w, String s) { log.addFirst(com.dogpound.railmap.depot.DepotClock.stamp(w) + " " + s); while (log.size() > 60) log.removeLast(); }

    // ---- truck appointments ---------------------------------------------------------------------------------------------
    public static long hourNow(World w) { return w.getTotalWorldTime() / 1000; }

    public String book(World w, BlockPos gate, String who) {
        long now = hourNow(w);
        for (Appt a : appts) if (a.gate == gate.toLong() && a.who.equals(who) && a.state.equals("BOOKED") && a.hour >= now - 1)
            return "§7You already hold the " + BizData.hhmm(a.hour * 1000) + " slot at this gate";
        for (long h = now; h < now + 24; h++) {
            int n = 0;
            for (Appt a : appts) if (a.gate == gate.toLong() && a.hour == h && !a.state.equals("NO-SHOW")) n++;
            if (n < TRUCKS_PER_HOUR) {
                Appt a = new Appt();
                a.gate = gate.toLong(); a.hour = h; a.who = who;
                appts.add(a);
                return "§aTruck appointment booked: " + BizData.hhmm(h * 1000) + " (arrive within the hour either side)";
            }
        }
        return "§cThe gate is fully booked for the next 24 hours";
    }

    /** null = the truck may pass; else why not (the hour is full of booked trucks and this one has no slot) */
    public String gateRefuse(World w, BlockPos gate, EntityPlayerMP p) {
        if (!BizConfig.enabled) return null;
        long now = hourNow(w);
        int booked = 0;
        for (Appt a : appts) {
            if (a.gate != gate.toLong() || !a.state.equals("BOOKED")) continue;
            if (a.who.equals(p.getName()) && Math.abs(a.hour - now) <= 1) return null;
            if (a.hour == now) booked++;
        }
        return booked >= TRUCKS_PER_HOUR ? "§cGate full: every truck slot this hour is booked - book one (APPT) and come back" : null;
    }

    /** a truck passed the gate: keeps its appointment, or is a walk-in (text for the message) */
    public String gate(World w, BlockPos gate, EntityPlayerMP p, boolean in) {
        if (!BizConfig.enabled) return "";
        long now = hourNow(w);
        Stat s = stats.get(gate.toLong());
        if (s == null) s = stat(w, gate, false);
        s.gateMoves++;
        for (Appt a : appts)
            if (a.gate == gate.toLong() && a.who.equals(p.getName()) && a.state.equals("BOOKED") && Math.abs(a.hour - now) <= 1) {
                a.state = "KEPT"; s.kept++;
                return " (appointment " + BizData.hhmm(a.hour * 1000) + " kept)";
            }
        s.walkIns++;
        return " (walk-in, no appointment)";
    }

    // ---- through freight -------------------------------------------------------------------------------------------------
    public static void setFreight(ItemStack box, long cents) { box.getTagCompound().setLong("frt", cents); }

    /** the box is stripped: if it has reached the station it was booked to, its through freight is paid to whoever strips it */
    public void delivered(World w, ItemStack box, EntityPlayerMP p) {
        if (!BizConfig.enabled || !box.hasTagCompound() || !box.getTagCompound().hasKey("frt")) return;
        long dest = ItemContainer.dest(box);
        Long here = com.dogpound.railmap.station.StationFacilities.stationOf(w, p.getPosition());
        if (here == null || dest == Long.MIN_VALUE || here != dest) return;
        long frt = box.getTagCompound().getLong("frt");
        box.getTagCompound().removeTag("frt");
        Company c = CompanyData.get(w).of(p.getUniqueID());
        if (c != null) CompanyData.get(w).credit(w, c, Company.Kind.FREIGHT, frt, "Through freight " + ItemContainer.pretty(box) + " delivered to " + ItemContainer.destName(box));
        else Coins.pay(p, frt, "Through freight " + ItemContainer.pretty(box));
        throughFreightPaid += frt;
        note(w, ItemContainer.pretty(box) + " delivered inland to " + ItemContainer.destName(box) + " - through freight " + Coins.fmt(frt) + " to " + (c != null ? c.mark : p.getName()));
        BizData.get(w).mastery.count(w, c, p, "boxes", 1);
    }

    /** freight for an import box from the port to a station: by distance */
    public static long freightFor(World w, BlockPos port, long dest) {
        double d = Math.sqrt(BlockPos.fromLong(dest).distanceSq(port));
        return 1500 + Math.round(d * 12);
    }

    /** a random named station inland (not the one at the port) */
    public static long randomDest(World w, BlockPos port) {
        List<Long> st = new ArrayList<>(StationData.get(w).names().keySet());
        st.removeIf(k -> BlockPos.fromLong(k).distanceSq(port) < 64 * 64);
        return st.isEmpty() ? Long.MIN_VALUE : st.get(w.rand.nextInt(st.size()));
    }

    /** ports and terminals employ people: jobs for towns within 256 blocks */
    public double jobsNear(BlockPos at) {
        double j = 0;
        for (Stat s : stats.values()) if (BlockPos.fromLong(s.pos).distanceSq(at) < 256 * 256) j += s.port ? 400 : 120;
        return j;
    }

    @Override
    public void tick(World w, BizData d, long now) {
        long hour = now / 1000;
        for (Appt a : appts) if (a.state.equals("BOOKED") && a.hour < hour - 1) {
            a.state = "NO-SHOW";
            Stat s = stats.get(a.gate);
            if (s != null) s.noShows++;
        }
        appts.removeIf(a -> !a.state.equals("BOOKED") && a.hour < hour - 48);
        stats.values().removeIf(s -> now - s.seen > 7 * BizData.DAY);
    }

    @Override
    public void daily(World w, BizData d, long day) { for (Stat s : stats.values()) { s.walkIns = s.kept = s.noShows = 0; } }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        appts.clear(); stats.clear(); log.clear();
        NBTTagList l = t.getTagList("ap", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Appt a = new Appt();
            a.gate = c.getLong("g"); a.hour = c.getLong("h"); a.who = c.getString("w"); a.state = c.getString("s");
            appts.add(a);
        }
        l = t.getTagList("st", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Stat s = new Stat();
            s.name = c.getString("n"); s.pos = c.getLong("p"); s.port = c.getBoolean("port"); s.seen = c.getLong("seen");
            s.shipMoves = c.getInteger("sm"); s.gateMoves = c.getInteger("gm"); s.railMoves = c.getInteger("rm"); s.quay = c.getInteger("q"); s.quayCap = c.getInteger("qc");
            s.walkIns = c.getInteger("wi"); s.kept = c.getInteger("k"); s.noShows = c.getInteger("ns"); s.fees = c.getLong("f"); s.demurrage = c.getLong("dm");
            stats.put(s.pos, s);
        }
        l = t.getTagList("log", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
        throughFreightPaid = t.getLong("tfp");
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Appt a : appts) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("g", a.gate); c.setLong("h", a.hour); c.setString("w", a.who); c.setString("s", a.state);
            l.appendTag(c);
        }
        t.setTag("ap", l);
        l = new NBTTagList();
        for (Stat s : stats.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("n", s.name); c.setLong("p", s.pos); c.setBoolean("port", s.port); c.setLong("seen", s.seen);
            c.setInteger("sm", s.shipMoves); c.setInteger("gm", s.gateMoves); c.setInteger("rm", s.railMoves); c.setInteger("q", s.quay); c.setInteger("qc", s.quayCap);
            c.setInteger("wi", s.walkIns); c.setInteger("k", s.kept); c.setInteger("ns", s.noShows); c.setLong("f", s.fees); c.setLong("dm", s.demurrage);
            l.appendTag(c);
        }
        t.setTag("st", l);
        l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("log", l);
        t.setLong("tfp", throughFreightPaid);
        return t;
    }

}
