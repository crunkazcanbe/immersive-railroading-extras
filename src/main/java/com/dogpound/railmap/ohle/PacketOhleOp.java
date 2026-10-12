package com.dogpound.railmap.ohle;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console OLE / WEATHER tab -> server: a button (see OhleConsole.op) */
public class PacketOhleOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketOhleOp() {}
    public PacketOhleOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(64); }

    public static class Handler implements IMessageHandler<PacketOhleOp, IMessage> {
        @Override
        public IMessage onMessage(PacketOhleOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (com.dogpound.railmap.admin.Access.denyCap(p, com.dogpound.railmap.admin.Access.Cap.POWER)) return;   // #111
                String r;
                try { r = OhleConsole.op(p.world, m.op, m.arg, p.getName()); } catch (NumberFormatException e) { r = null; }
                if (r != null) p.sendStatusMessage(new TextComponentString("§e" + r.replaceAll("§.", "")), true);
            });
            return null;
        }
    }
}
