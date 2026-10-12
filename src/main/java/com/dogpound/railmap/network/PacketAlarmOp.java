package com.dogpound.railmap.network;

import com.dogpound.railmap.alarm.AlarmManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Console ALARMS tab -> server: 0 ack one, 1 ack all, 2 silence, 3 reset, 4 shelve one for an hour */
public class PacketAlarmOp implements IMessage {
    private int op, id;

    public PacketAlarmOp() {}
    public PacketAlarmOp(int op, int id) { this.op = op; this.id = id; }

    @Override public void toBytes(ByteBuf b) { b.writeByte(op); b.writeInt(id); }
    @Override public void fromBytes(ByteBuf b) { op = b.readByte(); id = b.readInt(); }

    public static class Handler implements IMessageHandler<PacketAlarmOp, IMessage> {
        @Override
        public IMessage onMessage(PacketAlarmOp m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                AlarmManager a = AlarmManager.get(p.world);
                switch (m.op) {
                    case 0 -> a.ack(m.id, p.getName());
                    case 1 -> a.ack(-1, p.getName());
                    case 2 -> a.silence();
                    case 3 -> a.reset();
                    case 4 -> a.shelve(m.id, p.world.getTotalWorldTime() + 1000);
                    default -> { }
                }
            });
            return null;
        }
    }
}
