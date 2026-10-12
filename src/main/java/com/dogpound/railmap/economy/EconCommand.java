package com.dogpound.railmap.economy;

import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras company | market | pax | zone | fares | container - the railway economy from chat (her ChatGPT list #10,
 * #46-50). Everything here can also be done at the Company HQ, Commodity Exchange and Travel Centre desks, except the
 * names, marks, colours and the server's fare scheme.
 */
public final class EconCommand {
    private EconCommand() {}

    public static final String USAGE = "company [list|info|create <name>|name <name>|mark <ABC>|colour <hex>|hire <player>|fire <player>|role <player> <role>|wage <player> <$>|line <line>|train|station <name>|pso <line>|cut <%>|share <player> <n>|report|dissolve] | market [<item>|set <item> <cents>] | pax [lines|<station>] | zone <station> <1-9> | fares [zones|distance|flat|base <c>|step <c>|peak <%>] | container [give]";

    public static boolean handles(String a) { return a.equalsIgnoreCase("company") || a.equalsIgnoreCase("market") || a.equalsIgnoreCase("pax") || a.equalsIgnoreCase("zone") || a.equalsIgnoreCase("fares") || a.equalsIgnoreCase("container"); }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
    private static String rest(String[] a, int from) { return String.join(" ", Arrays.copyOfRange(a, Math.min(from, a.length), a.length)); }

    public static void run(MinecraftServer server, ICommandSender s, World w, String[] a) {
        boolean op = s.canUseCommand(2, "irextras") || s instanceof EntityPlayer pl && Perms.has(pl, Perms.ADMIN);
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "company" -> company(server, s, w, a, op);
            case "market" -> market(s, w, a, op);
            case "pax" -> pax(s, w, a);
            case "zone" -> {
                if (a.length < 3) { say(s, "/irextras zone <station> <1-9>"); return; }
                Long k = station(w, rest(a, 1).replaceAll("\\s+\\d+$", ""));
                if (k == null) { say(s, "No station called that"); return; }
                int z;
                try { z = Integer.parseInt(a[a.length - 1]); } catch (NumberFormatException e) { say(s, "Zone must be 1-9"); return; }
                PassengerData.get(w).setZone(k, z);
                say(s, "§a" + StationData.get(w).nameAt(net.minecraft.util.math.BlockPos.fromLong(k)) + " is now fare zone " + PassengerData.get(w).zoneOf(k)
                        + " §7(a Station Master's Desk there keeps its own setting)");
            }
            case "fares" -> fares(s, w, a, op);
            case "container" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("give") && op && s instanceof EntityPlayer p) {
                    Company c = CompanyData.get(w).of(p.getUniqueID());
                    net.minecraft.item.ItemStack b = com.dogpound.railmap.item.ItemContainer.make(com.dogpound.railmap.proxy.CommonProxy.container,
                            c != null ? c.mark : p.getName(), w.rand.nextInt(1_000_000), p.getUniqueID(), p.getName());
                    p.inventory.addItemStackToInventory(b);
                    say(s, "§aGave you " + com.dogpound.railmap.item.ItemContainer.pretty(b));
                } else say(s, "Containers are gated in at a Container Terminal (empty hand on GATE IN hires a new one). Ops: /irextras container give");
            }
            default -> say(s, USAGE);
        }
    }

    // ---- companies ---------------------------------------------------------------------------------------------------
    private static void company(MinecraftServer server, ICommandSender s, World w, String[] a, boolean op) {
        CompanyData cd = CompanyData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        UUID me = s instanceof EntityPlayer p ? p.getUniqueID() : null;
        Company mine = cd.of(me);
        if (sub.equals("list")) {
            if (cd.all().isEmpty()) { say(s, "No railway companies yet - place a Company HQ and press FOUND, or /irextras company create <name>"); return; }
            for (Company c : cd.all())
                say(s, String.format(Locale.ROOT, "§6%s §7(%s) · %d staff · %s operating · %s maint · rep %d · %d lines %d trains %d stations",
                        c.name, c.mark, c.members.size(), Coins.fmt(c.operating), Coins.fmt(c.maintenance), c.reputation, c.lines.size(), c.trains.size(), c.stations.size()));
            return;
        }
        if (sub.equals("info") || sub.equals("report")) {
            Company c = a.length > 2 ? cd.byName(rest(a, 2)) : mine;
            if (c == null) { say(s, "No such company (and you don't work for one)"); return; }
            for (String l : CompanyData.report(c, sub.equals("report") && c.monthNo > 0)) say(s, l);
            for (Map.Entry<UUID, Company.Member> e : c.members.entrySet())
                say(s, "§7  " + e.getValue().name + " - " + e.getValue().role.label + ", " + Coins.fmt(e.getValue().wage) + "/day, " + c.shares.getOrDefault(e.getKey(), 0) / 10.0 + "% shares");
            say(s, "§7  Lines: " + (c.lines.isEmpty() ? "none" : String.join(", ", c.lines)) + (c.pso.isEmpty() ? "" : " · PSO: " + String.join(", ", c.pso)));
            return;
        }
        if (sub.equals("create")) {
            if (!(s instanceof EntityPlayer p)) { say(s, "Players only"); return; }
            if (mine != null) { say(s, "You already work for " + mine.name); return; }
            if (!Perms.has(p, Perms.FOUND)) { say(s, Perms.no("found a railway company")); return; }
            if (a.length < 3) { say(s, "/irextras company create <name>"); return; }
            Company c = cd.found(w, p, rest(a, 2));
            say(s, "§a" + c.name + " (" + c.mark + ") founded - you own 100%");
            return;
        }
        Company c = mine;
        if (c == null) { say(s, "You don't work for a railway company"); return; }
        Company.Member m = c.member(me);
        boolean boss = op || m != null && m.role.staff, ops = op || m != null && m.role.operations, fin = op || m != null && m.role.finance;
        switch (sub) {
            case "name" -> { if (!boss) { say(s, "Directors only"); return; } c.name = rest(a, 2); say(s, "§aRenamed to " + c.name); }
            case "mark" -> { if (!boss) { say(s, "Directors only"); return; } c.mark = rest(a, 2).toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", ""); if (c.mark.length() > 4) c.mark = c.mark.substring(0, 4); say(s, "§aReporting mark " + c.mark); }
            case "colour", "color" -> {
                if (!boss) { say(s, "Directors only"); return; }
                try { c.colour = Integer.parseInt(rest(a, 2).replace("#", ""), 16); say(s, "§aLivery colour set"); } catch (NumberFormatException e) { say(s, "Colour as hex, e.g. #8E24AA"); }
            }
            case "hire", "fire", "role", "wage", "share" -> {
                if (a.length < 3) { say(s, "/irextras company " + sub + " <player> ..."); return; }
                EntityPlayerMP t = server.getPlayerList().getPlayerByUsername(a[2]);
                UUID u = t != null ? t.getUniqueID() : null;
                if (u == null) for (Map.Entry<UUID, Company.Member> e : c.members.entrySet()) if (e.getValue().name.equalsIgnoreCase(a[2])) u = e.getKey();
                if (u == null) { say(s, "No player " + a[2]); return; }
                Company.Member x = c.member(u);
                switch (sub) {
                    case "hire" -> {
                        if (!boss) { say(s, "Directors only"); return; }
                        if (cd.of(u) != null) { say(s, "They already work for " + cd.of(u).name); return; }
                        x = new Company.Member(); x.name = a[2]; x.wage = 1_000;
                        if (a.length > 3) try { x.role = Company.Role.valueOf(a[3].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { }
                        if (x.role == Company.Role.OWNER) x.role = Company.Role.DIRECTOR;
                        c.members.put(u, x); c.applicants.remove(u);
                        say(s, "§a" + x.name + " hired as " + x.role.label);
                    }
                    case "fire" -> {
                        if (!boss || x == null || x.role == Company.Role.OWNER) { say(s, "Can't"); return; }
                        c.members.remove(u);
                        Integer sh = c.shares.remove(u);
                        if (sh != null) c.shares.merge(c.owner, sh, Integer::sum);
                        say(s, "§e" + x.name + " has left");
                    }
                    case "role" -> {
                        if (!boss || x == null || x.role == Company.Role.OWNER || a.length < 4) { say(s, "/irextras company role <player> DIRECTOR|DISPATCHER|DRIVER|CLERK"); return; }
                        try { x.role = Company.Role.valueOf(a[3].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { say(s, "Roles: DIRECTOR DISPATCHER DRIVER CLERK"); return; }
                        if (x.role == Company.Role.OWNER) x.role = Company.Role.DIRECTOR;
                        say(s, "§a" + x.name + " is now " + x.role.label);
                    }
                    case "wage" -> {
                        if (!boss || x == null || a.length < 4) { say(s, "/irextras company wage <player> <dollars a day>"); return; }
                        try { x.wage = Math.max(0, Math.round(Double.parseDouble(a[3]) * 100)); } catch (NumberFormatException e) { say(s, "Dollars, e.g. 12.50"); return; }
                        say(s, "§a" + x.name + " earns " + Coins.fmt(x.wage) + " a day");
                    }
                    default -> {
                        if (x == null || a.length < 4) { say(s, "/irextras company share <member> <shares of 1000>"); return; }
                        int n;
                        try { n = Integer.parseInt(a[3]); } catch (NumberFormatException e) { say(s, "Shares are counted out of 1000"); return; }
                        int have = c.shares.getOrDefault(me, 0);
                        if (n <= 0 || n > have) { say(s, "You hold " + have + " shares"); return; }
                        c.shares.put(me, have - n);
                        c.shares.merge(u, n, Integer::sum);
                        c.shares.values().removeIf(v -> v <= 0);
                        say(s, "§aTransferred " + n + " shares (" + n / 10.0 + "%) to " + x.name);
                    }
                }
            }
            case "line", "pso" -> {
                String l = rest(a, 2);
                if (RailwayData.get(w).line(l) == null) { say(s, "No line called " + l); return; }
                if (sub.equals("pso")) {
                    if (!fin || !c.lines.contains(l)) { say(s, "Directors only, on your own lines"); return; }
                    if (!c.pso.remove(l)) c.pso.add(l);
                    say(s, c.pso.contains(l) ? "§aPSO contract signed for " + l : "§ePSO ended for " + l);
                } else {
                    if (!ops) { say(s, "Dispatchers and directors only"); return; }
                    Company o = cd.ownerOfLine(l);
                    if (o != null && o != c) { say(s, "Run by " + o.name); return; }
                    if (!c.lines.remove(l)) c.lines.add(l); else c.pso.remove(l);
                    say(s, c.lines.contains(l) ? "§a" + c.name + " now runs " + l : "§eReleased " + l);
                }
            }
            case "train" -> {
                if (!ops || !(s instanceof EntityPlayer p)) { say(s, "Dispatchers and directors only"); return; }
                RailwayData.AutoTrain best = null;
                double bd = 32 * 32;
                for (RailwayData.AutoTrain t : RailwayData.get(w).trains().values()) {
                    cam72cam.immersiverailroading.entity.Locomotive l = com.dogpound.railmap.auto.Autopilot.find(w, t.loco);
                    if (l == null) continue;
                    double d = l.getPosition().distanceToSquared(new cam72cam.mod.math.Vec3d(p.posX, p.posY, p.posZ));
                    if (d < bd) { bd = d; best = t; }
                }
                if (best == null) { say(s, "No driverless train within 32 blocks"); return; }
                for (Company o : cd.all()) if (o != c && o.trains.contains(best.loco)) { say(s, "That train belongs to " + o.name); return; }
                if (!c.trains.remove(best.loco)) c.trains.add(best.loco);
                say(s, c.trains.contains(best.loco) ? "§aTrain added to the fleet" : "§eTrain released");
            }
            case "station" -> {
                if (!ops) { say(s, "Dispatchers and directors only"); return; }
                Long k = station(w, rest(a, 2));
                if (k == null) { say(s, "No station called that"); return; }
                for (Company o : cd.all()) if (o != c && o.stations.contains(k)) { say(s, "Owned by " + o.name); return; }
                if (!c.stations.remove(k)) c.stations.add(k);
                say(s, c.stations.contains(k) ? "§aStation taken over" : "§eStation released");
            }
            case "cut" -> {
                if (!fin) { say(s, "Directors only"); return; }
                try { c.contractSharePct = Math.max(0, Math.min(100, Integer.parseInt(a[2]))); } catch (RuntimeException e) { say(s, "/irextras company cut <0-100>"); return; }
                say(s, "§aThe company keeps " + c.contractSharePct + "% of contracts its staff deliver");
            }
            case "dissolve" -> {
                if (!op && (m == null || m.role != Company.Role.OWNER)) { say(s, "Only the owner dissolves the company"); return; }
                long left = c.operating + c.maintenance - c.loan;
                if (left > 0 && s instanceof EntityPlayerMP p) Coins.pay(p, left, "Company wound up");
                cd.dissolve(w, c);
                say(s, "§e" + c.name + " dissolved; what was left after the loan went to the owner");
            }
            default -> say(s, USAGE);
        }
        cd.markDirty();
    }

    private static Long station(World w, String name) {
        for (Map.Entry<Long, String> e : StationData.get(w).names().entrySet()) if (e.getValue().equalsIgnoreCase(name.trim())) return e.getKey();
        return null;
    }

    // ---- market / passengers / fares -----------------------------------------------------------------------------------
    private static void market(ICommandSender s, World w, String[] a, boolean op) {
        MarketData md = MarketData.get(w);
        if (a.length > 3 && a[1].equalsIgnoreCase("set") && op) {
            MarketData.Price p = md.get(a[2]);
            if (p == null) { say(s, "No commodity " + a[2] + " (modid:item:meta)"); return; }
            try { p.price = Math.max(1, Long.parseLong(a[3])); md.markDirty(); say(s, "§a" + p.name() + " set to " + Coins.fmt(p.price)); } catch (NumberFormatException e) { say(s, "Price in cents"); }
            return;
        }
        String find = a.length > 1 ? rest(a, 1).toLowerCase(Locale.ROOT) : "";
        say(s, "§6Commodity market §7(index " + md.index() + ")");
        for (MarketData.Price p : md.all()) {
            if (!find.isEmpty() && !p.name().toLowerCase(Locale.ROOT).contains(find) && !p.key.contains(find)) continue;
            int c = p.change24();
            say(s, String.format(Locale.ROOT, "§f %s §e%s §7(%s%.1f%%) bid %s ask %s · supply %.0f demand %.0f%s", p.name(), Coins.fmt(p.price), c >= 0 ? "+" : "", c / 10.0,
                    Coins.fmt(p.bid()), Coins.fmt(p.ask()), p.supply, p.demand, find.isEmpty() ? "" : " · 24h " + com.dogpound.railmap.block.TileExchange.spark(p)));
        }
    }

    private static void pax(ICommandSender s, World w, String[] a) {
        PassengerData pd = PassengerData.get(w);
        if (a.length > 1 && a[1].equalsIgnoreCase("lines")) {
            for (PassengerData.LineStat l : pd.lines())
                say(s, String.format(Locale.ROOT, "§b%s §7riders %.0f (yesterday %.0f) · %.0f%% satisfied · on time %.0f%% · load %.0f%% · fares %s · refunds %s",
                        l.name, l.ridersDay, l.ridersYesterday, l.sat, l.punct, l.load, Coins.fmt(l.revenueDay), Coins.fmt(l.refundsDay)));
            return;
        }
        String find = a.length > 1 ? rest(a, 1).toLowerCase(Locale.ROOT) : "";
        for (PassengerData.Stn x : pd.stations()) {
            if (!find.isEmpty() && !x.name.toLowerCase(Locale.ROOT).contains(find)) continue;
            say(s, String.format(Locale.ROOT, "§b%s §7zone %d · catchment %d · %.0f waiting · %.0f boarded · §f%.0f%% %s §7(on time %.0f, space %.0f, waits %.0f, facilities %.0f, clean %.0f)",
                    x.name, pd.zoneOf(x.key), x.pop, x.waitingTotal(), x.boardedDay, x.sat, x.rating(), x.punct, x.crowd, x.wait, x.fac, x.clean));
            if (!find.isEmpty()) {
                List<Map.Entry<Long, Double>> d = new ArrayList<>(x.waiting.entrySet());
                d.sort((p, q) -> Double.compare(q.getValue(), p.getValue()));
                for (int i = 0; i < Math.min(5, d.size()); i++)
                    say(s, String.format(Locale.ROOT, "§7   %.0f waiting for %s", d.get(i).getValue(), StationData.get(w).names().getOrDefault(d.get(i).getKey(), "?")));
                for (int h = 0; h < 24; h += 6)
                    say(s, String.format(Locale.ROOT, "§7   %02d-%02dh boarded: %.0f %.0f %.0f %.0f %.0f %.0f", h, h + 5, x.hourly[h], x.hourly[h + 1], x.hourly[h + 2], x.hourly[h + 3], x.hourly[h + 4], x.hourly[h + 5]));
            }
        }
        if (pd.stations().isEmpty()) say(s, "No stations yet");
    }

    private static void fares(ICommandSender s, World w, String[] a, boolean op) {
        PassengerData pd = PassengerData.get(w);
        if (a.length > 1 && op) {
            try {
                switch (a[1].toLowerCase(Locale.ROOT)) {
                    case "zones", "distance", "flat" -> pd.scheme = a[1].toUpperCase(Locale.ROOT);
                    case "base" -> pd.zoneBase = Long.parseLong(a[2]);
                    case "step" -> pd.zoneStep = Long.parseLong(a[2]);
                    case "flatfare" -> pd.flat = Long.parseLong(a[2]);
                    case "perblock" -> pd.perBlock = Long.parseLong(a[2]);
                    case "peak" -> pd.peakPct = Integer.parseInt(a[2]);
                    default -> { }
                }
                pd.markDirty();
            } catch (RuntimeException e) { say(s, "/irextras fares zones|distance|flat | base <cents> | step <cents> | flatfare <cents> | perblock <cents> | peak <%>"); return; }
        }
        say(s, String.format(Locale.ROOT, "§6Fares: %s §7· zone base %s + %s per extra zone · flat %s · %s a block · peak +%d%% (07-09, 17-19) · night -20%% · child 50%%, senior 40%%",
                pd.scheme, Coins.fmt(pd.zoneBase), Coins.fmt(pd.zoneStep), Coins.fmt(pd.flat), Coins.fmt(pd.perBlock), pd.peakPct));
        say(s, "§7Passes: day = 2.5 singles, 7-day = 5 days, 28-day = 16 days · sold at Travel Centres, checked by fare gates");
    }
}
