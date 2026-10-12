package com.dogpound.railmap.planning;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console PLAN tab -> server: a button (see PlanConsole.op); the answer comes back with the next rail state */
public class PacketPlanOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketPlanOp() {}
    public PacketPlanOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg.length() > 250 ? arg.substring(0, 250) : arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(256); }

    public static class Handler implements IMessageHandler<PacketPlanOp, IMessage> {
        @Override
        public IMessage onMessage(PacketPlanOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r = PlanConsole.op(p.world, m.op, m.arg, p);
                if (r != null && !r.isEmpty()) for (String line : r.split("\n")) p.sendMessage(new TextComponentString(line.startsWith("§") ? line : "§e" + line));
                com.dogpound.railmap.network.PacketRailState.sendTo(p.world, p);
            });
            return null;
        }
    }
}
