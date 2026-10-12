package com.dogpound.railmap.stn;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console STN tab -> server: a button (see StnConsole.op) */
public class PacketStnOp implements IMessage {
    private String op = "", arg = "";

    public PacketStnOp() {}
    public PacketStnOp(String op, String arg) { this.op = op == null ? "" : op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { PacketBuffer p = new PacketBuffer(b); p.writeString(op); p.writeString(arg); }
    @Override public void fromBytes(ByteBuf b) { PacketBuffer p = new PacketBuffer(b); op = p.readString(64); arg = p.readString(256); }

    public static class Handler implements IMessageHandler<PacketStnOp, IMessage> {
        @Override
        public IMessage onMessage(PacketStnOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (com.dogpound.railmap.admin.Access.denyCap(p, com.dogpound.railmap.admin.Access.Cap.DEPOT)) return;
                String r;
                try { r = StnConsole.op(p, m.op, m.arg); } catch (RuntimeException | LinkageError e) { r = "§c" + m.op + " failed: " + e; }
                if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true);
            });
            return null;
        }
    }
}
