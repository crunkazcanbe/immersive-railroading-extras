package com.dogpound.railmap.cctv;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** server -> client: the CCTV camera directory (who is where, how it looks, its status), sent when it changes */
public class PacketCctvDir implements IMessage {
    public final List<CctvData.Cam> cams = new ArrayList<>();

    public PacketCctvDir() {}
    PacketCctvDir(CctvData d) { cams.addAll(d.cams.values()); }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        int n = Math.min(cams.size(), 2000);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            CctvData.Cam c = cams.get(i);
            b.writeVarInt(c.no); b.writeVarInt(c.type); b.writeInt(c.dim); b.writeVarInt(c.status);
            b.writeString(c.name); b.writeString(c.train); b.writeString(c.what.length() > 120 ? c.what.substring(0, 120) : c.what); b.writeString(c.masks);
            b.writeLong(c.pos); b.writeLong(c.net); b.writeDouble(c.x); b.writeDouble(c.y); b.writeDouble(c.z);
            b.writeFloat(c.yaw); b.writeFloat(c.pitch); b.writeFloat(c.fov); b.writeBoolean(c.ir); b.writeFloat(c.gain); b.writeVarInt(c.fx); b.writeBoolean(c.rec);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            CctvData.Cam c = new CctvData.Cam();
            c.no = b.readVarInt(); c.type = b.readVarInt(); c.dim = b.readInt(); c.status = b.readVarInt();
            c.name = b.readString(64); c.train = b.readString(64); c.what = b.readString(200); c.masks = b.readString(200);
            c.pos = b.readLong(); c.net = b.readLong(); c.x = b.readDouble(); c.y = b.readDouble(); c.z = b.readDouble();
            c.yaw = b.readFloat(); c.pitch = b.readFloat(); c.fov = b.readFloat(); c.ir = b.readBoolean(); c.gain = b.readFloat(); c.fx = b.readVarInt(); c.rec = b.readBoolean();
            cams.add(c);
        }
    }

    public static class Handler implements IMessageHandler<PacketCctvDir, IMessage> {
        @Override
        public IMessage onMessage(PacketCctvDir m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.cctv.client.CctvClient.dir(m));
            return null;
        }
    }
}
