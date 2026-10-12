package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.auto.RailwayData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client, to anyone with a board open: the lines, every driverless train with what it
 * is doing, and the dispatcher routes that are set. The board draws lines in their colours,
 * shows "DRIVERLESS" on train cards, and outlines routes.
 */
public class PacketRailState implements IMessage {
    public static final class LineInfo {
        public String name;
        public int color;
        public final List<Long> stations = new ArrayList<>();
        public final List<Integer> orders = new ArrayList<>();
        public boolean movingBlock;
    }

    public static final class TrainInfo {
        public int entityId;
        public String label, line, status, next, money = "";
        public int mode, dwell, maxKmh;
        public long home;
    }

    public static final class RouteInfo {
        public BlockPos start, end;
        public boolean entered;
        public final List<double[]> points = new ArrayList<>();
    }

    public final List<LineInfo> lines = new ArrayList<>();
    public final List<TrainInfo> trains = new ArrayList<>();
    public final List<RouteInfo> routes = new ArrayList<>();

    /** moving block: a train's limit-of-authority envelope (its track from head to end of authority) */
    public static final class Envelope {
        public int color;            // 0 clear, 1 following a train, 2 close to its limit
        public final List<double[]> points = new ArrayList<>();
    }

    public final List<Envelope> envelopes = new ArrayList<>();
    /** freight contracts for the console's FREIGHT tab: id, state, item, qty, loaded, delivered, from, to, pay, time left, by, distance */
    public final List<String[]> contracts = new ArrayList<>();
    /** alarms for the console's ALARMS tab: id, seq (1 new 2 standing 3 cleared 0 history), severity, category, text, count, minutes ago, ack by */
    public final List<String[]> alarms = new ArrayList<>();
    /** railway economy for the COMPANY / MARKET / PASSENGERS tabs: rows tagged co / px / ps / pl / ct (economy.EconSync) */
    public final List<String[]> economy = new ArrayList<>();

    /** protection + grid automation for the console's PROTECT tab (grid.GridConsole): devices, merged log books, last study */
    public final List<String[]> protection = new ArrayList<>();
    public final List<String[]> gridLog = new ArrayList<>();
    public final List<String> study = new ArrayList<>();
    /** signalling safety for the console's SAFETY tab (safety.SafetyConsoleData): rows tagged T C A H S G F D I L R X */
    public final List<String[]> safety = new ArrayList<>();
    /** incident command + control-room tools for the console's OPS tab (incident.IncidentConsoleData) */
    public final List<String[]> incident = new ArrayList<>();
    /** overhead line, energy and railway weather for the console's OLE/WX tab (ohle.OhleConsole) */
    public final List<String[]> env = new ArrayList<>();
    /** track infrastructure for the console's TRACK tab (track.TrackConsole): rows tagged M S A V */
    public final List<String[]> track = new ArrayList<>();
    /** depots, fleet, crew and stations for the console's DEPOT tab (depot.DepotConsole): rows tagged U J L C H D S */
    public final List<String[]> depot = new ArrayList<>();
    /** the railway platform API for the console's API tab (api.ApiConsoleData): rows tagged C V T O J A L X P E (#96-105) */
    public final List<String[]> api = new ArrayList<>();
    /** assets + maintenance for the console's ASSETS tab (asset.AssetConsole): rows tagged K A D F T P S C L (48-next list) */
    public final List<String[]> assets = new ArrayList<>();
    /** operations planning + simulation for the console's PLAN tab (planning.PlanConsole, next-48 #1 3 4 6 7 8 9 40 42 46 48) */
    public final List<String[]> plan = new ArrayList<>();
    /** grid / control / comms for the console's CTRL tab (ctl.CtlConsole, next-48 #20 21 23 25 26 29 30 31 39 41 43 44) */
    public final List<String[]> ctl = new ArrayList<>();
    /** world + business layer for the console's WORLD tab (biz.BizConsole, next-48 #11-15 17 18 32-38 47) */
    public final List<String[]> biz = new ArrayList<>();
    /** station operations + cargo care for the console's STN tab (stn.StnConsole, ChatGPT batch 3 #21-35) */
    public final List<String[]> stn = new ArrayList<>();
    /** CCTV cameras, events, recorders, monitors for the console's CCTV tab (cctv.CctvConsole) */
    public final List<String[]> cctv = new ArrayList<>();

    public PacketRailState() {}

    public static void sendTo(World world, EntityPlayerMP player) {
        RailMap.NETWORK.sendTo(build(world), player);
    }

    public static PacketRailState build(World world) {
        PacketRailState p = new PacketRailState();
        RailwayData data = RailwayData.get(world);
        for (RailwayData.Line l : data.lines().values()) {
            LineInfo i = new LineInfo();
            i.name = l.name;
            i.color = l.color;
            i.stations.addAll(l.stations);
            for (int k = 0; k < l.stations.size(); k++) i.orders.add(l.order(k).ordinal());
            i.movingBlock = l.movingBlock;
            p.lines.add(i);
        }
        for (RailwayData.AutoTrain a : data.trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            TrainInfo t = new TrainInfo();
            t.entityId = r == null ? -1 : r.entityId;
            t.label = a.label;
            t.line = a.line;
            t.mode = a.mode.ordinal();
            t.dwell = a.dwellSeconds;
            t.maxKmh = a.maxKmh;
            t.home = a.home;
            t.status = r == null ? "Starting" : r.status;
            t.next = r == null ? "" : r.nextStopName;
            t.money = a.earned == 0 && a.trips == 0 ? "" : "$" + a.earned + " earned · " + a.delivered + " delivered · " + a.trips + " laps"
                    + (a.trips > 0 ? " · last lap $" + a.lastLap : "");
            p.trains.add(t);
        }
        for (Interlocking.Route route : Interlocking.routes(world)) {
            RouteInfo ri = new RouteInfo();
            ri.start = route.start;
            ri.end = route.end;
            ri.entered = route.entered;
            for (com.dogpound.railmap.auto.TrackPath.Step s : route.path.steps) {
                if (s.points != null) ri.points.add(s.points);
            }
            p.routes.add(ri);
        }
        long now = world.getTotalWorldTime();
        for (java.util.Map.Entry<java.util.UUID, com.dogpound.railmap.auto.MovingBlock.Auth> en : com.dogpound.railmap.auto.MovingBlock.all()) {
            com.dogpound.railmap.auto.MovingBlock.Auth a = en.getValue();
            if (now - a.tick > 40) continue;
            Envelope ev = new Envelope();
            ev.color = a.limitedBy.isEmpty() ? 0 : a.distanceToGo < 50 ? 2 : 1;
            ev.points.addAll(com.dogpound.railmap.auto.MovingBlock.envelope(a));
            if (!ev.points.isEmpty()) p.envelopes.add(ev);
        }
        for (com.dogpound.railmap.freight.Contract k : com.dogpound.railmap.freight.ContractData.get(world).board()) {
            if (p.contracts.size() >= 40) break;
            p.contracts.add(new String[]{String.valueOf(k.id), k.state.name(), k.itemName, String.valueOf(k.qty), String.valueOf(k.loaded), String.valueOf(k.delivered),
                    k.fromName, k.toName, com.dogpound.railmap.freight.Contract.money(k.state == com.dogpound.railmap.freight.Contract.State.DONE ? k.paid : k.payNow(now)),
                    k.state == com.dogpound.railmap.freight.Contract.State.DONE ? "-" : k.timeLeft(now), k.byName, String.valueOf(k.distance)});
        }
        com.dogpound.railmap.alarm.AlarmManager am = com.dogpound.railmap.alarm.AlarmManager.get(world);
        for (com.dogpound.railmap.alarm.AlarmManager.Alarm a : am.all()) {
            if (p.alarms.size() >= 60) break;
            p.alarms.add(new String[]{String.valueOf(a.id), String.valueOf(a.seq(now)), a.sev.name(), a.cat.tile.replace("|", " "), a.text,
                    String.valueOf(a.count), String.valueOf((now - a.last) / 1200), a.ackBy});
        }
        if (com.dogpound.railmap.RailMapConfig.railwayEconomy) p.economy.addAll(com.dogpound.railmap.economy.EconSync.rows(world));
        p.protection.addAll(com.dogpound.railmap.grid.GridConsole.devices(world));   // PROTECT tab (grid/)
        p.gridLog.addAll(com.dogpound.railmap.grid.GridConsole.log(world));
        p.study.addAll(com.dogpound.railmap.grid.GridConsole.study(world));
        com.dogpound.railmap.safety.SafetyConsoleData.fill(world, p.safety);   // SAFETY tab (safety/)
        try { com.dogpound.railmap.incident.IncidentConsoleData.fill(world, p.incident); } catch (RuntimeException | LinkageError e) { p.incident.clear(); }   // OPS tab (incident/)
        try { com.dogpound.railmap.ohle.OhleConsole.fill(world, p.env); } catch (RuntimeException | LinkageError e) { p.env.clear(); }   // OLE/WX tab (ohle/, weather/)
        try { com.dogpound.railmap.track.TrackConsole.fill(world, p.track); } catch (RuntimeException | LinkageError e) { p.track.clear(); }   // TRACK tab (track/)
        try { com.dogpound.railmap.depot.DepotConsole.fill(world, p.depot); } catch (RuntimeException | LinkageError e) { p.depot.clear(); }   // DEPOT tab (depot/, station/)
        try { com.dogpound.railmap.api.ApiConsoleData.fill(world, p.api); } catch (RuntimeException | LinkageError e) { p.api.clear(); }   // API tab (api/, logic/)
        try { com.dogpound.railmap.asset.AssetConsole.fill(world, p.assets); } catch (RuntimeException | LinkageError e) { p.assets.clear(); }   // ASSETS tab (asset/)
        try { com.dogpound.railmap.planning.PlanConsole.fill(world, p.plan); } catch (RuntimeException | LinkageError e) { p.plan.clear(); }   // PLAN tab (planning/)
        try { com.dogpound.railmap.ctl.CtlConsole.fill(world, p.ctl); } catch (RuntimeException | LinkageError e) { p.ctl.clear(); }   // CTRL tab (ctl/)
        try { com.dogpound.railmap.biz.BizConsole.fill(world, p.biz); } catch (RuntimeException | LinkageError e) { p.biz.clear(); }   // WORLD tab (biz/)
        try { com.dogpound.railmap.stn.StnConsole.fill(world, p.stn); } catch (RuntimeException | LinkageError e) { p.stn.clear(); }   // STN tab (stn/, batch 3)
        try { com.dogpound.railmap.cctv.CctvConsole.fill(world, p.cctv); } catch (RuntimeException | LinkageError e) { p.cctv.clear(); }   // CCTV tab (cctv/)
        return p;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeVarInt(lines.size());
        for (LineInfo l : lines) {
            b.writeString(l.name);
            b.writeInt(l.color);
            b.writeVarInt(l.stations.size());
            for (long s : l.stations) b.writeLong(s);
            for (int k = 0; k < l.stations.size(); k++) b.writeVarInt(k < l.orders.size() ? l.orders.get(k) : 0);
            b.writeBoolean(l.movingBlock);
        }
        b.writeVarInt(trains.size());
        for (TrainInfo t : trains) {
            b.writeVarInt(t.entityId + 1);
            b.writeString(t.label);
            b.writeString(t.line);
            b.writeString(t.status.length() > 120 ? t.status.substring(0, 120) : t.status);
            b.writeString(t.next);
            b.writeVarInt(t.mode);
            b.writeVarInt(t.dwell);
            b.writeVarInt(t.maxKmh);
            b.writeLong(t.home);
            b.writeString(t.money);
        }
        b.writeVarInt(routes.size());
        for (RouteInfo r : routes) {
            b.writeLong(r.start.toLong());
            b.writeLong(r.end.toLong());
            b.writeBoolean(r.entered);
            b.writeVarInt(r.points.size());
            for (double[] pts : r.points) {
                b.writeVarInt(pts.length / 3);
                for (int i = 0; i + 2 < pts.length; i += 3) {
                    b.writeFloat((float) pts[i]);
                    b.writeFloat((float) pts[i + 2]);
                }
            }
        }
        b.writeVarInt(envelopes.size());
        for (Envelope ev : envelopes) {
            b.writeVarInt(ev.color);
            b.writeVarInt(ev.points.size());
            for (double[] pts : ev.points) {
                b.writeVarInt(pts.length / 3);
                for (int i = 0; i + 2 < pts.length; i += 3) {
                    b.writeFloat((float) pts[i]);
                    b.writeFloat((float) pts[i + 2]);
                }
            }
        }
        b.writeVarInt(contracts.size());
        for (String[] c : contracts) { b.writeVarInt(c.length); for (String s : c) b.writeString(s); }
        b.writeVarInt(alarms.size());
        for (String[] c : alarms) { b.writeVarInt(c.length); for (String s : c) b.writeString(s.length() > 200 ? s.substring(0, 200) : s); }
        b.writeVarInt(economy.size());                                         // economy rows (after alarms)
        for (String[] c : economy) { b.writeVarInt(c.length); for (String s : c) b.writeString(s.length() > 250 ? s.substring(0, 250) : s); }
        b.writeVarInt(protection.size());                                      // PROTECT tab (after economy)
        for (String[] c : protection) { b.writeVarInt(c.length); for (String s : c) b.writeString(s.length() > 250 ? s.substring(0, 250) : s); }
        b.writeVarInt(gridLog.size());
        for (String[] c : gridLog) { b.writeVarInt(c.length); for (String s : c) b.writeString(s.length() > 250 ? s.substring(0, 250) : s); }
        b.writeVarInt(study.size());
        for (String s : study) b.writeString(s.length() > 400 ? s.substring(0, 400) : s);
        b.writeVarInt(safety.size());                                          // SAFETY tab (after study)
        for (String[] c : safety) { b.writeVarInt(c.length); for (String s : c) b.writeString(s.length() > 1000 ? s.substring(0, 1000) : s); }
        b.writeVarInt(incident.size());                                        // OPS tab (after safety)
        for (String[] c : incident) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 400 ? s.substring(0, 400) : s); }
        b.writeVarInt(env.size());                                             // OLE/WX tab (after incident)
        for (String[] c : env) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 400 ? s.substring(0, 400) : s); }
        b.writeVarInt(track.size());                                           // TRACK tab (after env)
        for (String[] c : track) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 400 ? s.substring(0, 400) : s); }
        b.writeVarInt(depot.size());                                           // DEPOT tab (after track)
        for (String[] c : depot) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 3000 ? s.substring(0, 3000) : s); }
        b.writeVarInt(api.size());                                             // API tab (after depot)
        for (String[] c : api) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 400 ? s.substring(0, 400) : s); }
        b.writeVarInt(assets.size());                                          // ASSETS tab (after api)
        for (String[] c : assets) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 1500 ? s.substring(0, 1500) : s); }
        b.writeVarInt(plan.size());                                            // PLAN tab (after api)
        for (String[] c : plan) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 1000 ? s.substring(0, 1000) : s); }
        b.writeVarInt(ctl.size());                                             // CTRL tab (after plan)
        for (String[] c : ctl) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 1500 ? s.substring(0, 1500) : s); }
        b.writeVarInt(biz.size());                                             // WORLD tab (after ctl)
        for (String[] c : biz) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 1000 ? s.substring(0, 1000) : s); }
        b.writeVarInt(stn.size());                                             // STN tab (after biz)
        for (String[] c : stn) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 600 ? s.substring(0, 600) : s); }
        b.writeVarInt(cctv.size());                                            // CCTV tab (after stn)
        for (String[] c : cctv) { b.writeVarInt(c.length); for (String s : c) b.writeString(s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s); }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        int nl = b.readVarInt();
        for (int i = 0; i < nl; i++) {
            LineInfo l = new LineInfo();
            l.name = b.readString(512);
            l.color = b.readInt();
            int ns = b.readVarInt();
            for (int k = 0; k < ns; k++) l.stations.add(b.readLong());
            for (int k = 0; k < ns; k++) l.orders.add(b.readVarInt());
            l.movingBlock = b.readBoolean();
            lines.add(l);
        }
        int nt = b.readVarInt();
        for (int i = 0; i < nt; i++) {
            TrainInfo t = new TrainInfo();
            t.entityId = b.readVarInt() - 1;
            t.label = b.readString(512);
            t.line = b.readString(512);
            t.status = b.readString(512);
            t.next = b.readString(512);
            t.mode = b.readVarInt();
            t.dwell = b.readVarInt();
            t.maxKmh = b.readVarInt();
            t.home = b.readLong();
            t.money = b.readString(512);
            trains.add(t);
        }
        int nr = b.readVarInt();
        for (int i = 0; i < nr; i++) {
            RouteInfo r = new RouteInfo();
            r.start = BlockPos.fromLong(b.readLong());
            r.end = BlockPos.fromLong(b.readLong());
            r.entered = b.readBoolean();
            int np = b.readVarInt();
            for (int k = 0; k < np; k++) {
                int n = b.readVarInt();
                double[] pts = new double[n * 3];
                for (int j = 0; j < n; j++) {
                    pts[j * 3] = b.readFloat();
                    pts[j * 3 + 2] = b.readFloat();
                }
                r.points.add(pts);
            }
            routes.add(r);
        }
        if (b.isReadable()) {
            int ne = b.readVarInt();
            for (int i = 0; i < ne; i++) {
                Envelope ev = new Envelope();
                ev.color = b.readVarInt();
                int np = b.readVarInt();
                for (int k = 0; k < np; k++) {
                    int n = b.readVarInt();
                    double[] pts = new double[n * 3];
                    for (int j = 0; j < n; j++) {
                        pts[j * 3] = b.readFloat();
                        pts[j * 3 + 2] = b.readFloat();
                    }
                    ev.points.add(pts);
                }
                envelopes.add(ev);
            }
        }
        if (b.isReadable()) {
            int nc = b.readVarInt();
            for (int i = 0; i < nc; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(256); contracts.add(c); }
            if (b.isReadable()) {
                int na = b.readVarInt();
                for (int i = 0; i < na; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(256); alarms.add(c); }
            }
            if (b.isReadable()) {                                                 // economy rows
                int ne = b.readVarInt();
                for (int i = 0; i < ne; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(256); economy.add(c); }
            }
        }
        if (b.isReadable()) {                                                     // PROTECT tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(256); protection.add(c); }
            if (b.isReadable()) { n = b.readVarInt(); for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(256); gridLog.add(c); } }
            if (b.isReadable()) { n = b.readVarInt(); for (int i = 0; i < n; i++) study.add(b.readString(512)); }
        }
        if (b.isReadable()) {                                                     // SAFETY tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1024); safety.add(c); }
        }
        if (b.isReadable()) {                                                     // OPS tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1024); incident.add(c); }
        }
        if (b.isReadable()) {                                                     // OLE/WX tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1024); env.add(c); }
        }
        if (b.isReadable()) {                                                     // TRACK tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1024); track.add(c); }
        }
        if (b.isReadable()) {                                                     // DEPOT tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(3000); depot.add(c); }
        }
        if (b.isReadable()) {                                                     // API tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1024); api.add(c); }
        }
        if (b.isReadable()) {                                                     // ASSETS tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1500); assets.add(c); }
        }
        if (b.isReadable()) {                                                     // PLAN tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1024); plan.add(c); }
        }
        if (b.isReadable()) {                                                     // CTRL tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1500); ctl.add(c); }
        }
        if (b.isReadable()) {                                                     // WORLD tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(1024); biz.add(c); }
        }
        if (b.isReadable()) {                                                     // STN tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(700); stn.add(c); }
        }
        if (b.isReadable()) {                                                     // CCTV tab
            int n = b.readVarInt();
            for (int i = 0; i < n; i++) { String[] c = new String[b.readVarInt()]; for (int j = 0; j < c.length; j++) c[j] = b.readString(400); cctv.add(c); }
        }
    }

    public static class Handler implements IMessageHandler<PacketRailState, IMessage> {
        @Override
        public IMessage onMessage(PacketRailState msg, MessageContext ctx) {
            RailMap.proxy.acceptRailState(msg);
            return null;
        }
    }
}
