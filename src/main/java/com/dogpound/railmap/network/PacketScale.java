package com.dogpound.railmap.network;

import com.dogpound.railmap.signal.IScalable;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client → server: resize a trackside piece from the Signal Wrench's size slider. */
public class PacketScale implements IMessage {
    private BlockPos pos;
    private float scale;

    public PacketScale() {}

    public PacketScale(BlockPos pos, float scale) {
        this.pos = pos;
        this.scale = scale;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        scale = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeFloat(scale);
    }

    public static class Handler implements IMessageHandler<PacketScale, IMessage> {
        @Override
        public IMessage onMessage(PacketScale msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSq(msg.pos) > 16 * 16 || !player.world.isBlockLoaded(msg.pos)) return;
                TileEntity te = player.world.getTileEntity(msg.pos);
                if (te instanceof IScalable s) s.setScale(msg.scale);
            });
            return null;
        }
    }
}
