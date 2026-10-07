package com.dogpound.railmap.signs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: what every Pride Rail car's destination board and screens should say right now. */
public class PacketSigns implements IMessage {
    public static final class Entry {
        public int entityId, color;
        public byte mode;
        public String dest = "", next = "", line = "", title = "";
    }

    public final List<Entry> entries = new ArrayList<>();

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeVarInt(entries.size());
        for (Entry e : entries) {
            b.writeVarInt(e.entityId);
            b.writeByte(e.mode);
            b.writeInt(e.color);
            b.writeString(e.dest);
            b.writeString(e.next);
            b.writeString(e.line);
            b.writeString(e.title);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            Entry e = new Entry();
            e.entityId = b.readVarInt();
            e.mode = b.readByte();
            e.color = b.readInt();
            e.dest = b.readString(128);
            e.next = b.readString(128);
            e.line = b.readString(64);
            e.title = b.readString(96);
            entries.add(e);
        }
    }

    public static class Handler implements IMessageHandler<PacketSigns, IMessage> {
        @Override
        public IMessage onMessage(PacketSigns msg, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.signs.SignRender.receive(msg));
            return null;
        }
    }
}
