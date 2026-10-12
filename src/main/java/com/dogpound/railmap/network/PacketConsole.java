package com.dogpound.railmap.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Server -> nearby players: the Control Center console's desk + screen state (small; the rail network itself syncs with the tile). */
public class PacketConsole implements IMessage {
    private BlockPos pos;
    private NBTTagCompound tag;

    public PacketConsole() {}

    public PacketConsole(BlockPos pos, NBTTagCompound tag) {
        this.pos = pos;
        this.tag = tag;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeTag(buf, tag);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        tag = ByteBufUtils.readTag(buf);
    }

    public static class Handler implements IMessageHandler<PacketConsole, IMessage> {
        @Override
        public IMessage onMessage(PacketConsole msg, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) apply(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(PacketConsole msg) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().world == null || msg.tag == null) return;
                net.minecraft.tileentity.TileEntity te = Minecraft.getMinecraft().world.getTileEntity(msg.pos);
                if (te instanceof com.dogpound.railmap.block.TileDispatcherBoard)
                    ((com.dogpound.railmap.block.TileDispatcherBoard) te).readConsole(msg.tag);
            });
        }
    }
}
