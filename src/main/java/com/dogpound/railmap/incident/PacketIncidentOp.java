package com.dogpound.railmap.incident;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console OPS tab -> server: a button (see IncidentConsoleData.op); the answer comes back with the next rail state */
public class PacketIncidentOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketIncidentOp() {}
    public PacketIncidentOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(96); }

    public static class Handler implements IMessageHandler<PacketIncidentOp, IMessage> {
        @Override
        public IMessage onMessage(PacketIncidentOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r = IncidentConsoleData.op(p.world, m.op, m.arg, p.getName());
                if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r.startsWith("§") ? r : "§e" + r), true);
                com.dogpound.railmap.network.PacketRailState.sendTo(p.world, p);
            });
            return null;
        }
    }
}
