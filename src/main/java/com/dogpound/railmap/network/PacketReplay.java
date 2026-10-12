package com.dogpound.railmap.network;

import com.dogpound.railmap.alarm.Recorder;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/** The console's REPLAY tab: client asks (empty), server answers with the recorder's buffer (samples + events). */
public class PacketReplay implements IMessage {
    public long now;
    public boolean recording;
    public final List<long[]> sampleTicks = new ArrayList<>();      // [tick]
    public final List<float[][]> sampleTrains = new ArrayList<>();  // per sample: trains {x, z, kmh, id}
    public final List<String[]> sampleNames = new ArrayList<>();
    public final List<Object[]> events = new ArrayList<>();         // {tick (Long), text}
    private boolean reply;

    public PacketReplay() {}

    public static PacketReplay request() { return new PacketReplay(); }

    static PacketReplay of(net.minecraft.world.World w) {
        PacketReplay p = new PacketReplay();
        p.reply = true;
        p.now = w.getTotalWorldTime();
        p.recording = Recorder.recording(w);
        List<Recorder.Sample> s = Recorder.samples(w);
        int step = Math.max(1, s.size() / 300);                     // keep the packet small
        for (int i = 0; i < s.size(); i += step) {
            Recorder.Sample x = s.get(i);
            p.sampleTicks.add(new long[]{x.tick});
            p.sampleTrains.add(x.trains.toArray(new float[0][]));
            p.sampleNames.add(x.names.toArray(new String[0]));
        }
        List<Recorder.Event> ev = Recorder.events(w);
        for (int i = Math.max(0, ev.size() - 150); i < ev.size(); i++) p.events.add(new Object[]{ev.get(i).tick, ev.get(i).text});
        return p;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeBoolean(reply);
        if (!reply) return;
        b.writeLong(now); b.writeBoolean(recording);
        b.writeVarInt(sampleTicks.size());
        for (int i = 0; i < sampleTicks.size(); i++) {
            b.writeLong(sampleTicks.get(i)[0]);
            float[][] t = sampleTrains.get(i);
            b.writeVarInt(t.length);
            for (int k = 0; k < t.length; k++) { for (float v : t[k]) b.writeFloat(v); b.writeString(sampleNames.get(i)[k]); }
        }
        b.writeVarInt(events.size());
        for (Object[] e : events) { b.writeLong((Long) e[0]); String s = (String) e[1]; b.writeString(s.length() > 120 ? s.substring(0, 120) : s); }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        reply = b.readBoolean();
        if (!reply) return;
        now = b.readLong(); recording = b.readBoolean();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            sampleTicks.add(new long[]{b.readLong()});
            int m = b.readVarInt();
            float[][] t = new float[m][4];
            String[] names = new String[m];
            for (int k = 0; k < m; k++) { for (int j = 0; j < 4; j++) t[k][j] = b.readFloat(); names[k] = b.readString(64); }
            sampleTrains.add(t); sampleNames.add(names);
        }
        int ne = b.readVarInt();
        for (int i = 0; i < ne; i++) events.add(new Object[]{b.readLong(), b.readString(160)});
    }

    /** client: the last answer */
    @SideOnly(Side.CLIENT)
    public static PacketReplay last;

    public static class Handler implements IMessageHandler<PacketReplay, IMessage> {
        @Override
        public IMessage onMessage(PacketReplay msg, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> com.dogpound.railmap.RailMap.NETWORK.sendTo(of(p.world), p));
            } else client(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void client(PacketReplay msg) { Minecraft.getMinecraft().addScheduledTask(() -> last = msg); }
    }
}
