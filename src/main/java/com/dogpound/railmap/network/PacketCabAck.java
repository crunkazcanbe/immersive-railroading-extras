package com.dogpound.railmap.network;

import com.dogpound.railmap.auto.DriverAids;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: the driver pressed the cab acknowledge key (vigilance, AWS, SPAD / penalty reset). See auto.DriverAids. */
public class PacketCabAck implements IMessage {
    public PacketCabAck() {}

    @Override public void toBytes(ByteBuf buf) { buf.writeByte(1); }
    @Override public void fromBytes(ByteBuf buf) { buf.readByte(); }

    public static class Handler implements IMessageHandler<PacketCabAck, IMessage> {
        @Override
        public IMessage onMessage(PacketCabAck msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r = DriverAids.ack(p.world, p);
                if (r != null) p.sendStatusMessage(new TextComponentString(r), true);
            });
            return null;
        }
    }
}
