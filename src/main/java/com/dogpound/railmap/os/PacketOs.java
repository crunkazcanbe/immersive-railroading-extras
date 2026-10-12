package com.dogpound.railmap.os;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Pride OS traffic, both ways (one id). Server to client: OPEN (the desk's saved state + railway status, or no power),
 * STATUS (fresh railway status), LOCKED (PIN wanted). Client to server: SAVE (one section: notes / draws / hs / set),
 * UNLOCK (a PIN try), STATUS (refresh), BOOK (print note n), MAP (print drawing n).
 */
public class PacketOs implements IMessage {
    public static final byte OPEN = 0, STATUS = 1, LOCKED = 2, SAVE = 3, UNLOCK = 4, BOOK = 5, MAP = 6, NOPOWER = 7;

    public byte op;
    public BlockPos pos = BlockPos.ORIGIN;
    public String key = "";
    public NBTTagCompound data = new NBTTagCompound();

    public PacketOs() {}

    public PacketOs(byte op, BlockPos pos, String key, NBTTagCompound data) {
        this.op = op; this.pos = pos; this.key = key == null ? "" : key; this.data = data == null ? new NBTTagCompound() : data;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(op);
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, key);
        ByteBufUtils.writeTag(buf, data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        op = buf.readByte();
        pos = BlockPos.fromLong(buf.readLong());
        key = ByteBufUtils.readUTF8String(buf);
        data = ByteBufUtils.readTag(buf);
        if (data == null) data = new NBTTagCompound();
    }

    public static class Handler implements IMessageHandler<PacketOs, IMessage> {
        @Override
        public IMessage onMessage(PacketOs m, MessageContext ctx) {
            if (ctx.side.isServer()) {
                net.minecraft.entity.player.EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> PrideOs.fromClient(p, m));
            } else com.dogpound.railmap.client.gui.GuiPrideOs.fromServer(m);
            return null;
        }
    }
}
