package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TileRailDisplay;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.scan.NetworkScanner;
import com.dogpound.railmap.server.Viewers;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server: "Rescan". For a tile the server checks reach so a stray packet can't scan
 * remote boards; for the handheld map ({@code pos == null}) it scans around the player and
 * answers with {@link PacketNetwork}.
 */
public class PacketRescan implements IMessage {
    private BlockPos pos;

    public PacketRescan() {}

    /** {@code null} = handheld map. */
    public PacketRescan(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = buf.readBoolean() ? BlockPos.fromLong(buf.readLong()) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
    }

    public static class Handler implements IMessageHandler<PacketRescan, IMessage> {
        @Override
        public IMessage onMessage(PacketRescan msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null) {
                    handheld(player);
                    return;
                }
                if (player.getDistanceSq(msg.pos) > 64 * 64 || !player.world.isBlockLoaded(msg.pos)) return;
                TileEntity te = player.world.getTileEntity(msg.pos);
                if (te instanceof TileRailDisplay d) d.controller().rescan();
            });
            return null;
        }

        static void handheld(EntityPlayerMP player) {
            Viewers.mark(player);
            RailNetwork net;
            try {
                net = NetworkScanner.scan(player.world, player.getPosition());
            } catch (Exception e) {
                RailMap.LOG.error("[RailMap] handheld scan failed for {}", player.getName(), e);
                return;
            }
            RailMap.NETWORK.sendTo(new PacketNetwork(net), player);
        }
    }
}
