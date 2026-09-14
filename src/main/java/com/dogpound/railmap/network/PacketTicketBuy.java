package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TileTicketMachine;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client → server: "print me a ticket to this station". The server re-checks everything. */
public class PacketTicketBuy implements IMessage {
    private BlockPos machine = BlockPos.ORIGIN;
    private long dest;
    private boolean round;

    public PacketTicketBuy() {}

    public PacketTicketBuy(BlockPos machine, long dest, boolean round) {
        this.machine = machine;
        this.dest = dest;
        this.round = round;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(machine.toLong());
        buf.writeLong(dest);
        buf.writeBoolean(round);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        machine = BlockPos.fromLong(buf.readLong());
        dest = buf.readLong();
        round = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<PacketTicketBuy, IMessage> {
        @Override
        public IMessage onMessage(PacketTicketBuy msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSq(msg.machine) > 8 * 8 || !player.world.isBlockLoaded(msg.machine)) return;
                if (!(player.world.getTileEntity(msg.machine) instanceof TileTicketMachine m)) return;
                String result = m.buy(player, msg.dest, msg.round);
                PacketTicketMenu menu = PacketTicketMenu.build(m, player);
                menu.message = result;
                RailMap.NETWORK.sendTo(menu, player);
            });
            return null;
        }
    }
}
