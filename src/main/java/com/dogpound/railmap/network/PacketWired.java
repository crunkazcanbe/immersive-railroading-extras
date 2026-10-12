package com.dogpound.railmap.network;

import com.dogpound.railmap.block.TileDispatcherBoard;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * The console's WIRED page. Client -> server: "send me the bus" (op 0) or "press this control on that machine" (op 1);
 * server -> client: the bus list (op 2).
 */
public class PacketWired implements IMessage {
    private byte op;
    private BlockPos board, target = BlockPos.ORIGIN;
    private String id = "";
    private NBTTagCompound tag = new NBTTagCompound();

    public PacketWired() {}

    public static PacketWired request(BlockPos board) { PacketWired p = new PacketWired(); p.op = 0; p.board = board; return p; }
    public static PacketWired press(BlockPos board, BlockPos target, String id) { PacketWired p = request(board); p.op = 1; p.target = target; p.id = id; return p; }
    static PacketWired list(BlockPos board, NBTTagCompound tag) { PacketWired p = request(board); p.op = 2; p.tag = tag; return p; }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(op);
        buf.writeLong(board.toLong());
        buf.writeLong(target.toLong());
        ByteBufUtils.writeUTF8String(buf, id);
        ByteBufUtils.writeTag(buf, tag);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        op = buf.readByte();
        board = BlockPos.fromLong(buf.readLong());
        target = BlockPos.fromLong(buf.readLong());
        id = ByteBufUtils.readUTF8String(buf);
        tag = ByteBufUtils.readTag(buf);
    }

    /** client: the latest bus list per board */
    @SideOnly(Side.CLIENT)
    public static final Map<BlockPos, NBTTagCompound> LISTS = new HashMap<>();

    public static class Handler implements IMessageHandler<PacketWired, IMessage> {
        @Override
        public IMessage onMessage(PacketWired msg, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) { client(msg); return null; }
            EntityPlayerMP pl = ctx.getServerHandler().player;
            pl.getServerWorld().addScheduledTask(() -> {
                if (pl.getDistanceSq(msg.board) > 16 * 16 || !pl.world.isBlockLoaded(msg.board)) return;
                if (!(pl.world.getTileEntity(msg.board) instanceof TileDispatcherBoard b)) return;
                if (msg.op == 1 && com.dogpound.railmap.admin.Access.denyRemote(pl, msg.target)) return;   // #111 / #115
                if (msg.op == 1) {
                    String r = b.wiredPress(msg.target, msg.id, pl);
                    com.dogpound.railmap.api.Audit.log(pl.world, com.dogpound.railmap.api.Audit.Src.CONSOLE, pl.getName(), msg.target,
                            pl.world.getTileEntity(msg.target) instanceof com.dogpound.railmap.wire.IWiredMachine wm ? wm.wiredName() : "machine", "press " + msg.id, "", "", r);   // audit log (#102)
                    pl.sendStatusMessage(new TextComponentString(r), true);
                }
                com.dogpound.railmap.RailMap.NETWORK.sendTo(list(msg.board, b.wiredList()), pl);
            });
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void client(PacketWired msg) {
            if (msg.op == 2) Minecraft.getMinecraft().addScheduledTask(() -> LISTS.put(msg.board, msg.tag));
        }
    }
}
