package com.dogpound.railmap.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console API tab -> server: a button (see ApiConsoleData.op); the answer comes back with the next rail state */
public class PacketApiOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketApiOp() {}
    public PacketApiOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(96); }

    public static class Handler implements IMessageHandler<PacketApiOp, IMessage> {
        @Override
        public IMessage onMessage(PacketApiOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r;
                try { r = ApiConsoleData.op(p.world, m.op, m.arg, p.getName()); } catch (RuntimeException e) { r = "§c" + e.getMessage(); }
                if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r.startsWith("§") ? r : "§e" + r), true);
                com.dogpound.railmap.network.PacketRailState.sendTo(p.world, p);
            });
            return null;
        }
    }
}
