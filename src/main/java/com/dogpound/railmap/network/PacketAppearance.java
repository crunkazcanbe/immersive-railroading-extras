package com.dogpound.railmap.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> client every 2 s: the look of the vehicles near the player (dirt, weathering, damage, livery). See sim.Appearance. */
public class PacketAppearance implements IMessage {
    public final List<Integer> ids = new ArrayList<>(), rgb = new ArrayList<>();

    public PacketAppearance() {}

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(ids.size());
        for (int i = 0; i < ids.size(); i++) { buf.writeInt(ids.get(i)); buf.writeMedium(rgb.get(i)); }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readUnsignedShort();
        for (int i = 0; i < n; i++) { ids.add(buf.readInt()); rgb.add(buf.readUnsignedMedium()); }
    }

    public static class Handler implements IMessageHandler<PacketAppearance, IMessage> {
        @Override
        public IMessage onMessage(PacketAppearance msg, MessageContext ctx) {
            com.dogpound.railmap.client.StockTint.receive(msg);
            return null;
        }
    }
}
