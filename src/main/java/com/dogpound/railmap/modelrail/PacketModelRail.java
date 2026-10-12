package com.dogpound.railmap.modelrail;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** layout editor -> server: set a piece, work points / a signal, place / lift a train, buy stock, starter layout, clear */
public class PacketModelRail implements IMessage {
    private BlockPos pos = BlockPos.ORIGIN;
    private String op = "";
    private int[] args = new int[0];

    public PacketModelRail() {}
    public PacketModelRail(BlockPos pos, String op, int... args) { this.pos = pos; this.op = op; this.args = args; }

    @Override
    public void toBytes(ByteBuf b) {
        PacketBuffer p = new PacketBuffer(b);
        p.writeBlockPos(pos); p.writeString(op); p.writeVarInt(args.length);
        for (int a : args) p.writeVarInt(a);
    }

    @Override
    public void fromBytes(ByteBuf b) {
        PacketBuffer p = new PacketBuffer(b);
        pos = p.readBlockPos(); op = p.readString(16);
        int n = Math.min(8, p.readVarInt());
        args = new int[n];
        for (int i = 0; i < n; i++) args[i] = p.readVarInt();
    }

    public static class Handler implements IMessageHandler<PacketModelRail, IMessage> {
        @Override
        public IMessage onMessage(PacketModelRail m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (!p.world.isBlockLoaded(m.pos) || !(p.world.getTileEntity(m.pos) instanceof TileModelRailway t)) return;
                String r;
                try { r = t.edit(p, m.op, m.args); } catch (RuntimeException e) { r = "§c" + m.op + " failed: " + e; }
                if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true);
            });
            return null;
        }
    }
}
