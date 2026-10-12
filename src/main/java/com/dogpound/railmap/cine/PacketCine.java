package com.dogpound.railmap.cine;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Server -> client: a director desk's WATCH button - put this player's screen on that desk's program output. */
public class PacketCine implements IMessage {
    public static final int WATCH = 1;
    public int op;
    public long pos;

    public PacketCine() {}
    public PacketCine(int op, long pos) { this.op = op; this.pos = pos; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); b.writeLong(pos); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); pos = b.readLong(); }

    public static class Handler implements IMessageHandler<PacketCine, IMessage> {
        @Override
        public IMessage onMessage(PacketCine m, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) client(m);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void client(PacketCine m) {
            Minecraft.getMinecraft().addScheduledTask(() -> { if (m.op == WATCH) com.dogpound.railmap.cine.client.CineCam.watchDesk(net.minecraft.util.math.BlockPos.fromLong(m.pos)); });
        }
    }
}
