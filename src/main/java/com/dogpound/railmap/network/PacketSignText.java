package com.dogpound.railmap.network;

import com.dogpound.railmap.signal.TileRailSign;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client → server: set the text on a custom rail sign. */
public class PacketSignText implements IMessage {
    private BlockPos pos;
    private String text;

    public PacketSignText() {}

    public PacketSignText(BlockPos pos, String text) {
        this.pos = pos;
        this.text = text;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        text = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, text == null ? "" : text);
    }

    public static class Handler implements IMessageHandler<PacketSignText, IMessage> {
        @Override
        public IMessage onMessage(PacketSignText msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSq(msg.pos) > 20 * 20 || !player.world.isBlockLoaded(msg.pos)) return;
                TileEntity te = player.world.getTileEntity(msg.pos);
                // cap the text so nobody stuffs a novel into an NBT sign
                String t = msg.text == null ? "" : msg.text;
                if (t.length() > 60) t = t.substring(0, 60);
                if (te instanceof TileRailSign s) s.setCustomText(t);
            });
            return null;
        }
    }
}
