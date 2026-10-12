package com.dogpound.railmap.travel6;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pride Rail phone app (batch 6b #7). Client to server: an app request (op + up to 3 args: "home", "plan" from to,
 * "dep" station, "wallet", "trains", "alerts", "block" card). Server to client: the screen - its title, its lines
 * (a leading '!' red, '~' amber, '+' green, '.' dim, '#' a heading) and the station list for the pickers.
 */
public class PacketPhone implements IMessage {
    public String op = "";
    public final List<String> args = new ArrayList<>();
    public String title = "";
    public final List<String> lines = new ArrayList<>();
    public final List<String> stations = new ArrayList<>();
    public String here = "";

    public PacketPhone() {}

    public static PacketPhone request(String op, String... a) { PacketPhone p = new PacketPhone(); p.op = op; for (String s : a) p.args.add(s == null ? "" : s); return p; }

    @Override
    public void toBytes(ByteBuf b) {
        ByteBufUtils.writeUTF8String(b, op);
        b.writeByte(args.size());
        for (String s : args) ByteBufUtils.writeUTF8String(b, cut(s, 64));
        ByteBufUtils.writeUTF8String(b, cut(title, 64));
        ByteBufUtils.writeUTF8String(b, cut(here, 64));
        b.writeShort(Math.min(200, lines.size()));
        for (int i = 0; i < Math.min(200, lines.size()); i++) ByteBufUtils.writeUTF8String(b, cut(lines.get(i), 120));
        b.writeShort(Math.min(300, stations.size()));
        for (int i = 0; i < Math.min(300, stations.size()); i++) ByteBufUtils.writeUTF8String(b, cut(stations.get(i), 48));
    }

    @Override
    public void fromBytes(ByteBuf b) {
        op = ByteBufUtils.readUTF8String(b);
        int n = Math.min(4, b.readByte());
        for (int i = 0; i < n; i++) args.add(ByteBufUtils.readUTF8String(b));
        title = ByteBufUtils.readUTF8String(b);
        here = ByteBufUtils.readUTF8String(b);
        n = Math.min(200, b.readShort());
        for (int i = 0; i < n; i++) lines.add(ByteBufUtils.readUTF8String(b));
        n = Math.min(300, b.readShort());
        for (int i = 0; i < n; i++) stations.add(ByteBufUtils.readUTF8String(b));
    }

    static String cut(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, n); }

    public static class Handler implements IMessageHandler<PacketPhone, IMessage> {
        @Override
        public IMessage onMessage(PacketPhone m, MessageContext ctx) {
            if (ctx.side.isServer()) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> {
                    if (!ItemTravel.is(p.getHeldItemMainhand(), ItemTravel.PHONE) && !ItemTravel.is(p.getHeldItemOffhand(), ItemTravel.PHONE)) return;
                    PacketPhone r = Phone.answer(p, m.op, m.args);
                    com.dogpound.railmap.RailMap.NETWORK.sendTo(r, p);
                });
            } else com.dogpound.railmap.travel6.client.GuiPhone.fromServer(m);
            return null;
        }
    }
}
