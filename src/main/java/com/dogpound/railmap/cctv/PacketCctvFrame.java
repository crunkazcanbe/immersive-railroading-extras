package com.dogpound.railmap.cctv;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** server -> client: a CCTV picture (kind 0 snapshot, 1 playback frame) or a camera's timeline (kind 2: lo, hi, bookmarks) */
public class PacketCctvFrame implements IMessage {
    public int kind, cam, w, h;
    public long t, lo, hi;
    public String info = "";
    public short[] px = new short[0];

    public PacketCctvFrame() {}

    PacketCctvFrame(int kind, int cam, Snap.Frame f, long lo, long hi, String info) {
        this.kind = kind; this.cam = cam; this.lo = lo; this.hi = hi; this.info = info == null ? "" : info;
        if (f != null) { t = f.t; w = f.w; h = f.h; px = f.px; }
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeByte(kind); b.writeInt(cam); b.writeLong(t); b.writeLong(lo); b.writeLong(hi);
        new PacketBuffer(b).writeString(info.length() > 4000 ? info.substring(0, 4000) : info);
        b.writeShort(w); b.writeShort(h);
        for (int i = 0; i < w * h; i++) b.writeShort(px[i]);
    }

    @Override
    public void fromBytes(ByteBuf b) {
        kind = b.readByte(); cam = b.readInt(); t = b.readLong(); lo = b.readLong(); hi = b.readLong();
        info = new PacketBuffer(b).readString(4100);
        w = b.readShort(); h = b.readShort();
        if (w < 0 || h < 0 || w * h > 512 * 512) { w = h = 0; return; }
        px = new short[w * h];
        for (int i = 0; i < px.length; i++) px[i] = b.readShort();
    }

    public static class Handler implements IMessageHandler<PacketCctvFrame, IMessage> {
        @Override
        public IMessage onMessage(PacketCctvFrame m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.cctv.client.CctvClient.frame(m));
            return null;
        }
    }
}
