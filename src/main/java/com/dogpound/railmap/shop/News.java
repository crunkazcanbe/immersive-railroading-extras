package com.dogpound.railmap.shop;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.incident.IncidentLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * The real daily newspaper ("The Pride Rail Times"): each Minecraft morning a new edition is written from what really
 * happened on the server - the network's punctuality (Analytics), the most punctual unit (service diagrams) and the
 * busiest train, the day's incidents and emergency-service responses, freight delivered, the stock exchange's movers
 * (biz Securities), the weather and the forecast (the world's own rain / thunder clocks), stations opened since the last
 * edition, and players' achievements (the quest events). Newsstands print it as a written book and sell it; its
 * headlines go up on the stand's front board.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class News {
    private News() {}

    public static final String TITLE = "The Pride Rail Times";
    public static final String TAG = "irxNews";
    private static boolean listening;

    /** everything one edition is written from (gathered from the world, or made up by the self-test) */
    public static final class In {
        public int day, number;
        public double dep, ontime, delay, worst, km, freight, earned, wrecks, breakdowns, fires, incidents;
        public String bestUnit = "", busiest = "";
        public int bestCalls, bestLate, busiestTrips;
        public final List<String[]> incs = new ArrayList<>();      // {type, what, level, stage, response}
        public final List<String> ems = new ArrayList<>();
        public final List<Object[]> market = new ArrayList<>();    // {mark, last cents, prev cents}
        public long index;
        public final List<String> tape = new ArrayList<>();
        public boolean rain, thunder;
        public int hoursToChange = -1;
        public final List<String> newStations = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();       // "who|what"
    }

    // ---- ticking ------------------------------------------------------------------------------------------------------
    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime() % 200 != 37) return;
        if (!listening) { listening = true; com.dogpound.railmap.api.QuestEvents.listen(News::onQuest); }
        if (!ShopConfig.dailyPaper) return;
        World w = e.world;
        if (w.provider.getDimension() != 0 && com.dogpound.railmap.server.StationData.get(w).names().isEmpty()) return;
        NewsData d = NewsData.get(w);
        int day = Analytics.day(w);
        if (d.day == day) return;
        try { publish(w, d, day); } catch (RuntimeException | LinkageError ex) { RailMap.LOG.warn("[IR Extras] newspaper failed: {}", ex.toString()); d.day = day; }
    }

    /** a quest event: the notable ones make the paper */
    static void onQuest(EntityPlayerMP p, Map<String, Object> m) {
        String ev = String.valueOf(m.get("event"));
        String what = switch (ev) {
            case "trophy" -> "won the " + m.get("name") + " trophy";
            case "driver_qualified" -> "qualified as a " + String.valueOf(m.get("grade")).toLowerCase(Locale.ROOT) + " driver";
            case "theory_passed" -> "passed the driver theory test (" + m.get("score") + ")";
            case "black_start" -> "restored the grid with a black start";
            case "auction_won" -> "won a capacity auction";
            case "freight_delivered" -> "delivered a freight contract" + (m.get("item") == null ? "" : " of " + m.get("item"));
            case "fault_diagnosed" -> "diagnosed a " + m.get("cause") + " fault";
            case "commissioned" -> "commissioned a new " + m.get("kind");
            case "chaos_finished" -> "came through a chaos drill";
            default -> null;
        };
        if (what == null || p == null || p.world == null) return;
        try { NewsData.get(p.world).note(p.getName(), what); } catch (RuntimeException ignored) { }
    }

    // ---- publishing ---------------------------------------------------------------------------------------------------
    public static void publish(World w, NewsData d, int day) {
        In in = gather(w, d, day);
        d.lines.clear(); d.heads.clear();
        compose(in, d.lines, d.heads);
        d.day = day;
        d.number = in.number;
        d.notes.clear();
        d.markDirty();
        Recorder.event(w, "NEWSPAPER: " + TITLE + " No. " + in.number + " out - " + (d.heads.isEmpty() ? "" : d.heads.get(0)));
    }

    static In gather(World w, NewsData d, int day) {
        In in = new In();
        in.day = day;
        in.number = d.number + 1;
        // punctuality + traffic: yesterday's full day if there was one, else today so far
        Analytics an = Analytics.get(w);
        Map<String, Double> m = an.days().getOrDefault(day - 1, an.days().getOrDefault(day, Collections.emptyMap()));
        in.dep = Analytics.v(m, "dep"); in.ontime = Analytics.v(m, "ontime"); in.delay = in.dep == 0 ? 0 : Analytics.v(m, "delay") / in.dep;
        in.worst = Analytics.v(m, "maxDelay"); in.km = Analytics.v(m, "km"); in.freight = Analytics.v(m, "freight"); in.earned = Analytics.v(m, "earned");
        in.wrecks = Analytics.v(m, "wrecks"); in.breakdowns = Analytics.v(m, "breakdowns"); in.fires = Analytics.v(m, "fires"); in.incidents = Analytics.v(m, "incidents");
        try {
            for (Map.Entry<UUID, com.dogpound.railmap.stn.StnData.Diagram> e : com.dogpound.railmap.stn.Diagrams.list(w)) {
                com.dogpound.railmap.stn.StnData.Diagram g = e.getValue();
                if (g.callsY < 4) continue;
                double r = (g.callsY - g.lateY) / (double) g.callsY, best = in.bestCalls == 0 ? -1 : (in.bestCalls - in.bestLate) / (double) in.bestCalls;
                if (r > best || r == best && g.callsY > in.bestCalls) { in.bestUnit = g.unit.isEmpty() ? g.code : g.unit; in.bestCalls = g.callsY; in.bestLate = g.lateY; }
            }
        } catch (RuntimeException | LinkageError ignored) { }
        Map<String, Integer> now = new HashMap<>();
        for (Map.Entry<UUID, com.dogpound.railmap.auto.RailwayData.AutoTrain> e : com.dogpound.railmap.auto.RailwayData.get(w).trains().entrySet()) {
            com.dogpound.railmap.auto.RailwayData.AutoTrain a = e.getValue();
            String k = e.getKey().toString();
            now.put(k, a.trips);
            int n = a.trips - d.trips.getOrDefault(k, a.trips);
            if (n > in.busiestTrips) { in.busiestTrips = n; in.busiest = (a.label.isEmpty() ? "Train " + k.substring(0, 4) : a.label) + (a.line.isEmpty() ? "" : " (" + a.line + ")"); }
        }
        d.trips.clear(); d.trips.putAll(now);
        // incidents in the last day
        long t = w.getTotalWorldTime();
        for (IncidentLog.Rec r : IncidentLog.get(w).all()) {
            if (t - r.opened > 24000) continue;
            in.incs.add(new String[]{r.type.label, r.what, String.valueOf(r.level), r.stage.name(), r.onScene > 0 ? IncidentLog.mins(r.onScene - r.opened) : "-"});
        }
        try { for (String s : com.dogpound.railmap.ems.EmsData.get(w).history) if (in.ems.size() < 8) in.ems.add(s); } catch (RuntimeException | LinkageError ignored) { }
        // markets
        try {
            com.dogpound.railmap.biz.Securities sec = com.dogpound.railmap.biz.BizData.get(w).securities;
            com.dogpound.railmap.economy.CompanyData cd = com.dogpound.railmap.economy.CompanyData.get(w);
            for (com.dogpound.railmap.biz.Securities.Listing l : sec.listings.values()) {
                com.dogpound.railmap.economy.Company c = cd.byId(l.company);
                in.market.add(new Object[]{c == null ? "#" + l.company : c.mark.isEmpty() ? c.name : c.mark, l.last, l.prev});
            }
            in.index = sec.index(w);
            for (int i = 0; i < Math.min(3, sec.tape.size()); i++) in.tape.add(sec.tape.get(i));
        } catch (RuntimeException | LinkageError ignored) { }
        // weather
        in.rain = w.isRaining(); in.thunder = w.isThundering();
        int rt = w.getWorldInfo().getRainTime();
        in.hoursToChange = rt > 0 ? Math.max(1, rt / 1000) : -1;
        // stations
        for (String s : com.dogpound.railmap.server.StationData.get(w).names().values()) if (d.stations.add(s) && d.number > 0) in.newStations.add(s);
        d.markDirty();
        in.notes.addAll(d.notes);
        return in;
    }

    /** write the paper: text lines (a "#" line starts a section / page) and the front-board headlines */
    public static void compose(In in, List<String> lines, List<String> heads) {
        // ---- headlines, most newsworthy first
        for (String[] r : in.incs) if (r[0].equals(IncidentLog.Type.WRECK.label) || Integer.parseInt(r[2]) >= 2) heads.add(up(r[0] + ": " + r[1]));
        for (String s : in.newStations) heads.add(up("New station opens: " + s));
        if (in.dep >= 1) heads.add(String.format(Locale.ROOT, "%.0f%% OF TRAINS ON TIME", in.ontime / in.dep * 100));
        Object[] mover = null;
        double mv = 0;
        for (Object[] x : in.market) {
            long last = (Long) x[1], prev = (Long) x[2];
            if (prev <= 0) continue;
            double pct = (last - prev) * 100.0 / prev;
            if (Math.abs(pct) > Math.abs(mv)) { mv = pct; mover = x; }
        }
        if (mover != null && Math.abs(mv) >= 0.5) heads.add(String.format(Locale.ROOT, "%s SHARES %+.0f%%", up(String.valueOf(mover[0])), mv));
        if (in.thunder) heads.add("THUNDERSTORMS OVER THE NETWORK");
        else if (!in.rain && in.hoursToChange > 0 && in.hoursToChange <= 12) heads.add("RAIN EXPECTED WITHIN " + in.hoursToChange + " H");
        if (in.freight >= 1) heads.add(String.format(Locale.ROOT, "%.0f FREIGHT LOADS DELIVERED", in.freight));
        if (!in.notes.isEmpty()) heads.add(up(in.notes.get(0).replace('|', ' ')));
        if (in.busiestTrips > 0) heads.add(up(in.busiest + ": " + in.busiestTrips + " trips"));
        if (heads.isEmpty()) heads.add("A QUIET DAY ON THE RAILWAY");
        while (heads.size() > 8) heads.remove(heads.size() - 1);
        // ---- the paper
        lines.add("#" + TITLE.toUpperCase(Locale.ROOT));
        lines.add("No. " + in.number + " - Day " + in.day);
        lines.add("");
        for (int i = 0; i < Math.min(4, heads.size()); i++) lines.add("* " + heads.get(i));
        lines.add("#PUNCTUALITY");
        if (in.dep < 1) lines.add("No timetabled departures were recorded.");
        else {
            lines.add(String.format(Locale.ROOT, "%.0f departures, %.1f%% on time.", in.dep, in.ontime / in.dep * 100));
            lines.add(String.format(Locale.ROOT, "Average delay %.0f s, worst %.0f s.", in.delay, in.worst));
        }
        if (in.bestCalls > 0) lines.add(String.format(Locale.ROOT, "Most punctual unit: %s, %d of %d calls on time.", in.bestUnit, in.bestCalls - in.bestLate, in.bestCalls));
        if (in.busiestTrips > 0) lines.add("Busiest train: " + in.busiest + ", " + in.busiestTrips + " trips.");
        if (in.km > 0) lines.add(String.format(Locale.ROOT, "Trains ran %.1f km.", in.km));
        lines.add("#INCIDENTS");
        if (in.incs.isEmpty()) lines.add("No incidents. A clean day.");
        for (String[] r : in.incs) lines.add(r[0] + " (level " + r[2] + "): " + r[1] + " - " + r[3].toLowerCase(Locale.ROOT) + (r[4].equals("-") ? "" : ", on scene in " + r[4]));
        if (!in.ems.isEmpty()) { lines.add(""); lines.add("Emergency services:"); for (String s : in.ems) lines.add("- " + s); }
        lines.add("#FREIGHT + BUSINESS");
        lines.add(String.format(Locale.ROOT, "%.0f freight loads delivered.", in.freight));
        if (in.earned != 0) lines.add(String.format(Locale.ROOT, "Railways earned $%.0f.", in.earned));
        lines.add("#STOCK EXCHANGE");
        if (in.market.isEmpty()) lines.add("No companies are listed yet.");
        else {
            lines.add("Index " + com.dogpound.railmap.economy.Coins.fmt(in.index));
            for (Object[] x : in.market) {
                long last = (Long) x[1], prev = (Long) x[2];
                lines.add(x[0] + " " + com.dogpound.railmap.economy.Coins.fmt(last) + (prev > 0 ? String.format(Locale.ROOT, " (%+.1f%%)", (last - prev) * 100.0 / prev) : ""));
            }
            for (String s : in.tape) lines.add("> " + s);
        }
        lines.add("#WEATHER");
        lines.add(in.thunder ? "Thunderstorms now. Expect slippery rail and lightning on the overhead line." : in.rain ? "Rain now. Leaf-fall and wet rail: allow longer braking." : "Dry now.");
        if (in.hoursToChange > 0) lines.add((in.rain ? "Clearing" : "Rain") + " expected in about " + in.hoursToChange + " h.");
        if (!in.newStations.isEmpty()) {
            lines.add("#NEW STATIONS");
            for (String s : in.newStations) lines.add(s + " is open for passengers.");
        }
        lines.add("#ACHIEVEMENTS");
        if (in.notes.isEmpty()) lines.add("No new achievements today.");
        for (String n : in.notes) { String[] p = n.split("\\|", 2); lines.add(p[0] + " " + (p.length > 1 ? p[1] : "")); }
    }

    static String up(String s) { return s.toUpperCase(Locale.ROOT); }

    /** today's paper as a written book (null = no edition yet) */
    public static ItemStack copy(World w) {
        NewsData d = NewsData.get(w);
        if (d.day < 0) publish(w, d, Analytics.day(w));
        return book(d.lines, d.number, d.day);
    }

    public static ItemStack book(List<String> lines, int number, int day) {
        NBTTagList pages = new NBTTagList();
        StringBuilder p = new StringBuilder();
        int n = 0;
        for (String l : lines) {
            boolean section = l.startsWith("#");
            String s = section ? "§l" + l.substring(1) + "§r" : l;
            if (p.length() > 0 && (section || n >= 12 || p.length() + s.length() > 230)) {
                pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
                p.setLength(0); n = 0;
                if (pages.tagCount() >= 48) break;
            }
            p.append(s).append('\n');
            n += 1 + s.length() / 19;
        }
        if (p.length() > 0) pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
        ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", TITLE);
        t.setString("author", "No. " + number + ", day " + day);
        t.setTag("pages", pages);
        t.setInteger(TAG, day);
        b.setTagCompound(t);
        return b;
    }

    /** which day's paper a stack is (-1 = not one of ours) */
    public static int dayOf(ItemStack s) {
        return !s.isEmpty() && s.getItem() == Items.WRITTEN_BOOK && s.hasTagCompound() && s.getTagCompound().hasKey(TAG) ? s.getTagCompound().getInteger(TAG) : -1;
    }

    // ---- self-test --------------------------------------------------------------------------------------------------
    public static List<String> selfTest() {
        List<String> f = new ArrayList<>();
        In in = new In();
        in.day = 12; in.number = 3; in.dep = 40; in.ontime = 36; in.delay = 20; in.worst = 140; in.freight = 7;
        in.incs.add(new String[]{IncidentLog.Type.WRECK.label, "derailment at Kings Cross", "2", "CLOSED", "1:20"});
        in.market.add(new Object[]{"PRR", 1200L, 1000L});
        in.newStations.add("Rainbow Park");
        in.notes.add("Alex|won the Golden Spike trophy");
        in.thunder = true;
        List<String> l = new ArrayList<>(), h = new ArrayList<>();
        compose(in, l, h);
        if (h.isEmpty() || !h.get(0).contains("DERAILMENT")) f.add("a derailment should lead: " + h);
        if (!h.contains("NEW STATION OPENS: RAINBOW PARK")) f.add("new station headline missing");
        if (!h.contains("90% OF TRAINS ON TIME")) f.add("punctuality headline wrong: " + h);
        if (!h.contains("PRR SHARES +20%")) f.add("market mover headline wrong: " + h);
        if (l.stream().noneMatch(s -> s.contains("Alex won the Golden Spike trophy"))) f.add("achievement missing");
        ItemStack b = book(l, 3, 12);
        if (dayOf(b) != 12) f.add("book tag wrong");
        if (b.getTagCompound().getTagList("pages", 8).tagCount() < 6) f.add("expected a page per section, got " + b.getTagCompound().getTagList("pages", 8).tagCount());
        In quiet = new In();
        List<String> h2 = new ArrayList<>();
        compose(quiet, new ArrayList<>(), h2);
        if (!h2.equals(Collections.singletonList("A QUIET DAY ON THE RAILWAY"))) f.add("quiet day headline wrong: " + h2);
        return f;
    }
}
