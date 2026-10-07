package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings Console traffic. Client → server: REQUEST (send me this block's settings), SET (key = value), RESET.
 * Server → client: DATA (title, every setting with its value, the block's scale or -1).
 */
public class PacketSettings implements IMessage {
    public static final byte REQUEST = 0, SET = 1, RESET = 2, DATA = 3;

    public byte op;
    public BlockPos pos = BlockPos.ORIGIN;
    public String key = "", value = "", title = "";
    public float scale = -1;
    public final List<Setting> settings = new ArrayList<>();

    public PacketSettings() {}

    public static PacketSettings request(BlockPos pos) { PacketSettings p = new PacketSettings(); p.op = REQUEST; p.pos = pos; return p; }

    public static PacketSettings set(BlockPos pos, String key, String value) {
        PacketSettings p = new PacketSettings(); p.op = SET; p.pos = pos; p.key = key; p.value = value; return p;
    }

    public static PacketSettings reset(BlockPos pos) { PacketSettings p = new PacketSettings(); p.op = RESET; p.pos = pos; return p; }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeByte(op); b.writeLong(pos.toLong()); b.writeString(key); b.writeString(value.length() > 400 ? value.substring(0, 400) : value);
        b.writeString(title); b.writeFloat(scale);
        b.writeVarInt(settings.size());
        for (Setting s : settings) s.write(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        op = b.readByte(); pos = BlockPos.fromLong(b.readLong()); key = b.readString(64); value = b.readString(512);
        title = b.readString(128); scale = b.readFloat();
        int n = Math.min(512, b.readVarInt());
        for (int i = 0; i < n; i++) settings.add(Setting.read(buf));
    }

    /** the block's settings, as sent to a player */
    public static PacketSettings data(TileEntity te) {
        PacketSettings p = new PacketSettings();
        p.op = DATA;
        p.pos = te.getPos();
        if (te instanceof ISettingsHolder h) {
            p.title = h.settingsTitle();
            p.settings.addAll(h.settings().withValues());
        }
        if (te instanceof com.dogpound.railmap.signal.IScalable sc) p.scale = sc.scale();
        return p;
    }

    public static class Handler implements IMessageHandler<PacketSettings, IMessage> {
        @Override
        public IMessage onMessage(PacketSettings msg, MessageContext ctx) {
            if (ctx.side.isClient()) {
                RailMap.proxy.settingsData(msg);
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSq(msg.pos) > 24 * 24 || !player.world.isBlockLoaded(msg.pos)) return;
                TileEntity te = player.world.getTileEntity(msg.pos);
                if (!(te instanceof ISettingsHolder h)) return;
                if (msg.op == SET && h.settings().set(msg.key, msg.value)) {
                    te.markDirty();
                    h.onSettingsChanged(msg.key);
                    net.minecraft.block.state.IBlockState st = player.world.getBlockState(msg.pos);
                    player.world.notifyBlockUpdate(msg.pos, st, st, 3);
                } else if (msg.op == RESET) {
                    h.settings().reset();
                    te.markDirty();
                    h.onSettingsChanged("");
                }
                RailMap.NETWORK.sendTo(data(te), player);
            });
            return null;
        }
    }
}
