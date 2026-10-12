package com.dogpound.railmap.freight6;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.HashMap;
import java.util.Map;

/** server -> client: the season and which IR cars near the player wear which decoration kit (entity id -> kit) */
public class PacketHoliday implements IMessage {
    public int season;
    public final Map<Integer, Integer> kits = new HashMap<>();

    public PacketHoliday() {}

    @Override
    public void toBytes(ByteBuf b) {
        b.writeByte(season);
        b.writeShort(kits.size());
        for (Map.Entry<Integer, Integer> e : kits.entrySet()) { b.writeInt(e.getKey()); b.writeByte(e.getValue()); }
    }

    @Override
    public void fromBytes(ByteBuf b) {
        season = b.readByte();
        for (int i = b.readShort(); i > 0; i--) kits.put(b.readInt(), (int) b.readByte());
    }

    public static class Handler implements IMessageHandler<PacketHoliday, IMessage> {
        @Override
        public IMessage onMessage(PacketHoliday m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.freight6.client.F6Client.holiday(m));
            return null;
        }
    }
}
