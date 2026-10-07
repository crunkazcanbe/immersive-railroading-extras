package com.dogpound.railmap.program;

import com.dogpound.railmap.RailMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Signal Box program traffic. Client → server: REQUEST, SAVE_RULE, DELETE_RULE, TOGGLE_RULE,
 * MOVE_RULE, CHANNEL, CHANNEL_NAME, CLEAR_ALARMS. Server → client: SYNC (full ProgramData NBT).
 */
public class PacketProgram implements IMessage {
    public static final byte REQUEST = 0, SYNC = 1, SAVE_RULE = 2, DELETE_RULE = 3, TOGGLE_RULE = 4,
            MOVE_RULE = 5, CHANNEL = 6, CHANNEL_NAME = 7, CLEAR_ALARMS = 8;

    public byte op;
    public NBTTagCompound data = new NBTTagCompound();

    public PacketProgram() {}

    public static PacketProgram request(BlockPos box) {
        PacketProgram p = new PacketProgram();
        p.op = REQUEST;
        p.data.setLong("box", box.toLong());
        return p;
    }

    public static PacketProgram op(byte op, NBTTagCompound data, BlockPos box) {
        PacketProgram p = new PacketProgram();
        p.op = op;
        p.data = data;
        p.data.setLong("box", box.toLong());
        return p;
    }

    public static PacketProgram sync(ProgramData d) {
        PacketProgram p = new PacketProgram();
        p.op = SYNC;
        p.data = d.writeToNBT(new NBTTagCompound());
        return p;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(op);
        ByteBufUtils.writeTag(buf, data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        op = buf.readByte();
        data = ByteBufUtils.readTag(buf);
    }

    public static ProgramData client = new ProgramData();
    public static int clientVersion;

    public static class Handler implements IMessageHandler<PacketProgram, IMessage> {
        @Override
        public IMessage onMessage(PacketProgram msg, MessageContext ctx) {
            if (ctx.side.isClient()) {
                if (msg.op == SYNC) {
                    ProgramData d = new ProgramData();
                    d.readFromNBT(msg.data);
                    client = d;
                    clientVersion++;
                }
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ProgramData d = ProgramData.get(player.world);
                if (msg.op == REQUEST) {
                    RailMap.NETWORK.sendTo(sync(d), player);
                    return;
                }
                BlockPos box = BlockPos.fromLong(msg.data.getLong("box"));
                if (!player.capabilities.isCreativeMode && player.getDistanceSq(box) > 64 * 64) return;
                switch (msg.op) {
                    case SAVE_RULE: {
                        Rule r = Rule.read(msg.data);
                        if (r.id == 0) {
                            r.id = d.nextId++;
                            d.rules.add(r);
                        } else {
                            for (int i = 0; i < d.rules.size(); i++) {
                                if (d.rules.get(i).id == r.id) {
                                    d.rules.set(i, r);
                                    break;
                                }
                            }
                        }
                        d.changed();
                        break;
                    }
                    case DELETE_RULE: {
                        int id = msg.data.getInteger("id");
                        d.rules.removeIf(r -> r.id == id);
                        d.changed();
                        break;
                    }
                    case TOGGLE_RULE: {
                        int id = msg.data.getInteger("id");
                        Rule r = d.rule(id);
                        if (r != null) {
                            r.enabled = !r.enabled;
                            d.changed();
                        }
                        break;
                    }
                    case MOVE_RULE: {
                        int id = msg.data.getInteger("id");
                        int dir = msg.data.getInteger("dir");
                        int i = -1;
                        for (int k = 0; k < d.rules.size(); k++) if (d.rules.get(k).id == id) { i = k; break; }
                        if (i >= 0) {
                            int j = i + dir;
                            if (j >= 0 && j < d.rules.size()) {
                                java.util.Collections.swap(d.rules, i, j);
                                d.changed();
                            }
                        }
                        break;
                    }
                    case CHANNEL: {
                        int n = msg.data.getInteger("n");
                        boolean v = msg.data.getBoolean("v");
                        d.setChannel(n, v);
                        break;
                    }
                    case CHANNEL_NAME: {
                        int n = msg.data.getInteger("n");
                        String name = msg.data.getString("name");
                        if (n >= 1 && n <= ProgramData.CHANNELS) {
                            if (name.length() > 24) name = name.substring(0, 24);
                            d.channelName[n] = name;
                            d.changed();
                        }
                        break;
                    }
                    case CLEAR_ALARMS: {
                        d.alarms.clear();
                        d.changed();
                        break;
                    }
                    default:
                        return;
                }
                RailMap.NETWORK.sendTo(sync(d), player);
            });
            return null;
        }
    }
}
