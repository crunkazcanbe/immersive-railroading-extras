package com.dogpound.railmap.interior;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: a click on an interior part / control, a fitting-kit choice, or a café order. */
public class PacketInterior implements IMessage {
    public static final int CLICK = 0, FIT = 1, BUY = 2;
    public int op, entity, part, btn, arg;

    public PacketInterior() {}
    public PacketInterior(int op, int entity, int part, int btn, int arg) { this.op = op; this.entity = entity; this.part = part; this.btn = btn; this.arg = arg; }

    @Override public void toBytes(ByteBuf buf) { PacketBuffer b = new PacketBuffer(buf); b.writeByte(op); b.writeVarInt(entity); b.writeVarInt(part); b.writeVarInt(btn + 1); b.writeVarInt(arg + 4); }
    @Override public void fromBytes(ByteBuf buf) { PacketBuffer b = new PacketBuffer(buf); op = b.readByte(); entity = b.readVarInt(); part = b.readVarInt(); btn = b.readVarInt() - 1; arg = b.readVarInt() - 4; }

    public static class Handler implements IMessageHandler<PacketInterior, IMessage> {
        @Override
        public IMessage onMessage(PacketInterior m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> InteriorServer.handle(p, m));
            return null;
        }
    }
}
