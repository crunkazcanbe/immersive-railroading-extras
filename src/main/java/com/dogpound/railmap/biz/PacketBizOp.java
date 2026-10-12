package com.dogpound.railmap.biz;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console WORLD tab -> server: a button (see BizConsole.op) */
public class PacketBizOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketBizOp() {}
    public PacketBizOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(128); }

    public static class Handler implements IMessageHandler<PacketBizOp, IMessage> {
        @Override
        public IMessage onMessage(PacketBizOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (!BizConfig.enabled) return;
                String r = BizConsole.op(p, m.op, m.arg);
                if (r != null) p.sendStatusMessage(new TextComponentString("§e" + r.replaceAll("§.", "")), true);
            });
            return null;
        }
    }
}
