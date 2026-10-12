package com.dogpound.railmap.safety;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console SAFETY tab -> server: a button (see SafetyConsoleData.op) */
public class PacketSafetyOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketSafetyOp() {}
    public PacketSafetyOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(64); }

    public static class Handler implements IMessageHandler<PacketSafetyOp, IMessage> {
        @Override
        public IMessage onMessage(PacketSafetyOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r = SafetyConsoleData.op(p.world, m.op, m.arg, p.getName());
                if (r != null) p.sendStatusMessage(new TextComponentString("§e" + r), true);
            });
            return null;
        }
    }
}
