package com.dogpound.railmap.cctv;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** client -> server CCTV requests (see Cctv.handle) */
public class PacketCctvOp implements IMessage {
    public static final int SNAP = 1, PB_FRAME = 2, RANGE = 3, CONFIG = 4, MONITOR = 5, EXPORT = 6, CONSOLE = 7;
    int op, cam;
    long pos, t;
    String arg = "";

    public PacketCctvOp() {}
    public PacketCctvOp(int op, int cam, long pos, long t, String arg) { this.op = op; this.cam = cam; this.pos = pos; this.t = t; this.arg = arg == null ? "" : arg; }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeByte(op); b.writeInt(cam); b.writeLong(pos); b.writeLong(t);
        new PacketBuffer(b).writeString(arg.length() > 1000 ? arg.substring(0, 1000) : arg);
    }

    @Override
    public void fromBytes(ByteBuf b) { op = b.readByte(); cam = b.readInt(); pos = b.readLong(); t = b.readLong(); arg = new PacketBuffer(b).readString(1100); }

    public static class Handler implements IMessageHandler<PacketCctvOp, IMessage> {
        @Override
        public IMessage onMessage(PacketCctvOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                try { Cctv.handle(p, m); } catch (RuntimeException e) { com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] CCTV request failed: {}", e.toString()); }
            });
            return null;
        }
    }
}
