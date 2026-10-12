package com.dogpound.railmap.vehicle;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: the formation editor saves a formation into a desk's library slot. OpenMsg (server -> client) opens the editor. */
public class PacketFormation implements IMessage {
    public BlockPos pos = BlockPos.ORIGIN;
    public String action = "";
    public int slot;
    public NBTTagCompound tag;

    public PacketFormation() {}
    public PacketFormation(BlockPos pos, String action, int slot, NBTTagCompound tag) { this.pos = pos; this.action = action; this.slot = slot; this.tag = tag; }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeBlockPos(pos); b.writeString(action); b.writeInt(slot); b.writeCompoundTag(tag);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        pos = b.readBlockPos(); action = b.readString(16); slot = b.readInt();
        try { tag = b.readCompoundTag(); } catch (java.io.IOException e) { tag = null; }
    }

    public static class Handler implements IMessageHandler<PacketFormation, IMessage> {
        @Override
        public IMessage onMessage(PacketFormation m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (p.getDistanceSq(m.pos) > 64 || !(p.world.getTileEntity(m.pos) instanceof TileFormationDesk d)) return;
                if (m.action.equals("save") && m.tag != null) p.sendStatusMessage(new TextComponentString(d.save(m.slot, Formation.read(m.tag))), true);
            });
            return null;
        }
    }

    /** server -> client: open the formation editor for the desk at pos */
    public static class OpenMsg implements IMessage {
        public BlockPos pos = BlockPos.ORIGIN;
        public OpenMsg() {}
        public OpenMsg(BlockPos pos) { this.pos = pos; }
        @Override public void toBytes(ByteBuf buf) { buf.writeLong(pos.toLong()); }
        @Override public void fromBytes(ByteBuf buf) { pos = BlockPos.fromLong(buf.readLong()); }
    }

    public static class Open implements IMessageHandler<OpenMsg, IMessage> {
        @Override
        public IMessage onMessage(OpenMsg m, MessageContext ctx) {
            com.dogpound.railmap.client.gui.GuiFormation.open(m.pos);
            return null;
        }
    }
}
