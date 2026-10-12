package com.dogpound.railmap.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: a soft key pressed on the driver's display (client.GuiDmi). See sim.CabKeys. */
public class PacketCabKey implements IMessage {
    public String key = "";

    public PacketCabKey() {}
    public PacketCabKey(String key) { this.key = key; }

    @Override public void toBytes(ByteBuf buf) { new PacketBuffer(buf).writeString(key.length() > 32 ? key.substring(0, 32) : key); }
    @Override public void fromBytes(ByteBuf buf) { key = new PacketBuffer(buf).readString(32); }

    public static class Handler implements IMessageHandler<PacketCabKey, IMessage> {
        @Override
        public IMessage onMessage(PacketCabKey msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r = com.dogpound.railmap.sim.CabKeys.press(p.world, p, msg.key);
                if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true);
            });
            return null;
        }
    }
}
