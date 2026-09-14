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
    }

    public static final class TrainInfo {
        public int entityId;
        public String label, line, status, next;
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
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        int nl = b.readVarInt();
        for (int i = 0; i < nl; i++) {
            LineInfo l = new LineInfo();
            l.name = b.readString(64);
            l.color = b.readInt();
            int ns = b.readVarInt();
            for (int k = 0; k < ns; k++) l.stations.add(b.readLong());
            lines.add(l);
        }
        int nt = b.readVarInt();
        for (int i = 0; i < nt; i++) {
            TrainInfo t = new TrainInfo();
            t.entityId = b.readVarInt() - 1;
            t.label = b.readString(64);
            t.line = b.readString(64);
            t.status = b.readString(128);
            t.next = b.readString(64);
            t.mode = b.readVarInt();
            t.dwell = b.readVarInt();
            t.maxKmh = b.readVarInt();
            t.home = b.readLong();
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
    }

    public static class Handler implements IMessageHandler<PacketRailState, IMessage> {
        @Override
        public IMessage onMessage(PacketRailState msg, MessageContext ctx) {
            RailMap.proxy.acceptRailState(msg);
            return null;
        }
    }
}
