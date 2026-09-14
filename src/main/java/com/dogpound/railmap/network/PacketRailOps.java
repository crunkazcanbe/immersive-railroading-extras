package com.dogpound.railmap.network;

import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Interlocking;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.TileRailDisplay;
import com.dogpound.railmap.item.ItemRailMap;
import com.dogpound.railmap.server.StationData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client → server: the dispatcher running the railroad from the board.
 * <ul>
 *   <li>{@link #AUTO_SET} — make a locomotive driverless (line, shuttle, on call) or change its settings;</li>
 *   <li>{@link #AUTO_OFF} — hand it back to people;</li>
 *   <li>{@link #SEND} — send a train to one station;</li>
 *   <li>{@link #LINE_SAVE} / {@link #LINE_DELETE} — edit lines;</li>
 *   <li>{@link #ROUTE_SET} / {@link #ROUTE_CANCEL} — CTC entrance-exit routes between two signals.</li>
 * </ul>
 */
public class PacketRailOps implements IMessage {
    public static final byte AUTO_SET = 0, AUTO_OFF = 1, SEND = 2, LINE_SAVE = 3, LINE_DELETE = 4,
            ROUTE_SET = 5, ROUTE_CANCEL = 6;

    private byte op;
    private BlockPos board;
    private int entityId;
    private long a, b;
    private int mode, dwell, maxKmh;
    private String text = "";
    private final List<Long> list = new ArrayList<>();

    public PacketRailOps() {}

    private PacketRailOps(byte op, BlockPos board) {
        this.op = op;
        this.board = board;
    }

    public static PacketRailOps autopilot(BlockPos board, int entityId, RailwayData.Mode mode, String line,
                                          long home, int dwell, int maxKmh) {
        PacketRailOps p = new PacketRailOps(AUTO_SET, board);
        p.entityId = entityId;
        p.mode = mode.ordinal();
        p.text = line == null ? "" : line;
        p.a = home;
        p.dwell = dwell;
        p.maxKmh = maxKmh;
        return p;
    }

    public static PacketRailOps autopilotOff(BlockPos board, int entityId) {
        PacketRailOps p = new PacketRailOps(AUTO_OFF, board);
        p.entityId = entityId;
        return p;
    }

    public static PacketRailOps send(BlockPos board, int entityId, long station) {
        PacketRailOps p = new PacketRailOps(SEND, board);
        p.entityId = entityId;
        p.a = station;
        return p;
    }

    public static PacketRailOps saveLine(BlockPos board, String name, List<Long> stations) {
        PacketRailOps p = new PacketRailOps(LINE_SAVE, board);
        p.text = name;
        p.list.addAll(stations);
        return p;
    }

    public static PacketRailOps deleteLine(BlockPos board, String name) {
        PacketRailOps p = new PacketRailOps(LINE_DELETE, board);
        p.text = name;
        return p;
    }

    public static PacketRailOps route(BlockPos board, BlockPos start, BlockPos end) {
        PacketRailOps p = new PacketRailOps(ROUTE_SET, board);
        p.a = start.toLong();
        p.b = end.toLong();
        return p;
    }

    public static PacketRailOps cancelRoute(BlockPos board, BlockPos start) {
        PacketRailOps p = new PacketRailOps(ROUTE_CANCEL, board);
        p.a = start.toLong();
        return p;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer p = new PacketBuffer(buf);
        p.writeByte(op);
        p.writeBoolean(board != null);
        if (board != null) p.writeLong(board.toLong());
        p.writeVarInt(entityId);
        p.writeLong(a);
        p.writeLong(b);
        p.writeVarInt(mode);
        p.writeVarInt(dwell);
        p.writeVarInt(maxKmh);
        p.writeString(text.length() > 40 ? text.substring(0, 40) : text);
        p.writeVarInt(Math.min(list.size(), 64));
        for (int i = 0; i < Math.min(list.size(), 64); i++) p.writeLong(list.get(i));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer p = new PacketBuffer(buf);
        op = p.readByte();
        board = p.readBoolean() ? BlockPos.fromLong(p.readLong()) : null;
        entityId = p.readVarInt();
        a = p.readLong();
        b = p.readLong();
        mode = p.readVarInt();
        dwell = p.readVarInt();
        maxKmh = p.readVarInt();
        text = p.readString(40);
        int n = Math.min(64, p.readVarInt());
        for (int i = 0; i < n; i++) list.add(p.readLong());
    }

    public static class Handler implements IMessageHandler<PacketRailOps, IMessage> {
        @Override
        public IMessage onMessage(PacketRailOps msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(msg, player));
            return null;
        }

        private static void handle(PacketRailOps msg, EntityPlayerMP player) {
            World world = player.world;
            // Only a player at a board (or holding a Rail Map) may run the railroad.
            boolean atBoard = msg.board != null && player.getDistanceSq(msg.board) <= 64 * 64
                    && world.isBlockLoaded(msg.board) && world.getTileEntity(msg.board) instanceof TileRailDisplay;
            boolean handheld = player.getHeldItemMainhand().getItem() instanceof ItemRailMap
                    || player.getHeldItemOffhand().getItem() instanceof ItemRailMap;
            if (!atBoard && !handheld) return;

            RailwayData data = RailwayData.get(world);
            String reply;
            switch (msg.op) {
                case AUTO_SET -> {
                    Locomotive loco = locoOf(world, msg.entityId);
                    if (loco == null) { reply = "That train is not loaded"; break; }
                    RailwayData.AutoTrain t = data.setTrain(loco.getUUID());
                    RailwayData.Mode m = RailwayData.Mode.byOrdinal(msg.mode);
                    if (m != t.mode || !msg.text.equals(t.line)) { t.index = 0; t.calls.clear(); }
                    t.mode = m;
                    t.line = msg.text;
                    if (msg.a != 0) t.home = msg.a;
                    if (t.home == 0) t.home = nearestStation(world, loco);
                    t.dwellSeconds = Math.max(5, Math.min(600, msg.dwell));
                    t.maxKmh = Math.max(5, Math.min(250, msg.maxKmh <= 0 ? RailMapConfig.autopilotDefaultKmh : msg.maxKmh));
                    if (t.label.isEmpty()) t.label = Autopilot.label(t, loco);
                    data.markDirty();
                    reply = t.label + " is now driverless · " + m.label
                            + (m == RailwayData.Mode.LINE || m == RailwayData.Mode.SHUTTLE ? " · " + (t.line.isEmpty() ? "no line picked" : t.line) : "");
                }
                case AUTO_OFF -> {
                    Locomotive loco = locoOf(world, msg.entityId);
                    if (loco == null) { reply = "That train is not loaded"; break; }
                    Autopilot.release(world, loco.getUUID());
                    data.removeTrain(loco.getUUID());
                    reply = "Driverless off — brakes set, the train is yours";
                }
                case SEND -> {
                    Locomotive loco = locoOf(world, msg.entityId);
                    if (loco == null) { reply = "That train is not loaded"; break; }
                    String name = StationData.get(world).names().get(msg.a);
                    if (name == null) { reply = "Pick a named station"; break; }
                    RailwayData.AutoTrain t = data.setTrain(loco.getUUID());
                    t.mode = RailwayData.Mode.SEND;
                    t.home = msg.a;
                    t.calls.clear();
                    if (t.label.isEmpty()) t.label = Autopilot.label(t, loco);
                    data.markDirty();
                    reply = t.label + " sent to " + name;
                }
                case LINE_SAVE -> {
                    String name = msg.text.trim();
                    if (name.isEmpty()) { reply = "Give the line a name"; break; }
                    if (msg.list.size() < 2) { reply = "A line needs at least two stations"; break; }
                    data.saveLine(name, msg.list);
                    reply = "Line saved: " + name + " · " + msg.list.size() + " stations";
                }
                case LINE_DELETE -> {
                    data.deleteLine(msg.text);
                    reply = "Line deleted: " + msg.text;
                }
                case ROUTE_SET -> reply = Interlocking.setRoute(world, BlockPos.fromLong(msg.a), BlockPos.fromLong(msg.b));
                case ROUTE_CANCEL -> reply = Interlocking.cancelRoute(world, BlockPos.fromLong(msg.a));
                default -> reply = "";
            }
            if (!reply.isEmpty()) player.sendStatusMessage(new TextComponentString(reply), true);
            PacketRailState.sendTo(world, player);
        }

        private static Locomotive locoOf(World world, int id) {
            EntityRollingStock s = cam72cam.mod.world.World.get(world).getEntity(id, EntityRollingStock.class);
            if (s instanceof Locomotive l) return l;
            if (s instanceof EntityCoupleableRollingStock c) {
                for (EntityCoupleableRollingStock x : c.getTrain()) if (x instanceof Locomotive l) return l;
            }
            return null;
        }

        private static long nearestStation(World world, Locomotive loco) {
            long best = 0;
            double bd = Double.MAX_VALUE;
            for (Long k : StationData.get(world).names().keySet()) {
                BlockPos p = BlockPos.fromLong(k);
                double dx = p.getX() - loco.getPosition().x, dz = p.getZ() - loco.getPosition().z;
                double d = dx * dx + dz * dz;
                if (d < bd) { bd = d; best = k; }
            }
            return best;
        }
    }
}
