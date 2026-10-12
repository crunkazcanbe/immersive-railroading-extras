package com.dogpound.railmap.asset;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console ASSETS tab -> server: a button (see AssetConsole.op) */
public class PacketAssetOp implements IMessage {
    private int op;
    private String arg = "";

    public PacketAssetOp() {}
    public PacketAssetOp(int op, String arg) { this.op = op; this.arg = arg == null ? "" : arg; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); new PacketBuffer(b).writeString(arg); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); arg = new PacketBuffer(b).readString(128); }

    public static class Handler implements IMessageHandler<PacketAssetOp, IMessage> {
        @Override
        public IMessage onMessage(PacketAssetOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                if (com.dogpound.railmap.admin.Access.denyCap(p, com.dogpound.railmap.admin.Access.Cap.DEPOT)) return;
                String r = AssetConsole.op(p.world, m.op, m.arg, p.getName());
                if (r != null) p.sendStatusMessage(new TextComponentString("§e" + r), true);
            });
            return null;
        }
    }
}
