package com.dogpound.railmap.ctl;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console CTRL tab -> server: a button (see CtlConsole.op) */
public class PacketCtlOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketCtlOp() {}
    public PacketCtlOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg.length() > 1400 ? arg.substring(0, 1400) : arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(1500); }

    public static class Handler implements IMessageHandler<PacketCtlOp, IMessage> {
        @Override
        public IMessage onMessage(PacketCtlOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r;
                try { r = CtlConsole.op(p.world, m.op, m.arg, p); } catch (RuntimeException e) { r = "§cRefused: " + e.getMessage(); }
                if (r != null) p.sendStatusMessage(new TextComponentString(r), true);
            });
            return null;
        }
    }
}
