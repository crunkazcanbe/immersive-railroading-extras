package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.TileTicketMachine;
import com.dogpound.railmap.server.StationData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server → client: everything the ticket machine's touch screen shows — where you are, every
 * destination with its fares, what you can pay with, and who is waiting.
 */
public class PacketTicketMenu implements IMessage {
    public static final class Dest {
        public long key;
        public String name;
        public int oneWay, round;
        public double distance;
    }

    public BlockPos machine = BlockPos.ORIGIN;
    public String station = "";
    public String fareItem = "";
    public int wallet;
    public int waiting;
    public int trains;
    public String message = "";
    public final List<Dest> dests = new ArrayList<>();

    public PacketTicketMenu() {}

    public static PacketTicketMenu build(TileTicketMachine m, EntityPlayerMP player) {
        PacketTicketMenu p = new PacketTicketMenu();
        p.machine = m.getPos();
        p.station = m.stationName();
        Item fare = TileTicketMachine.fareItem();
        p.fareItem = fare == null ? "" : new ItemStack(fare).getDisplayName();
        if (fare != null) {
            for (ItemStack s : player.inventory.mainInventory) if (!s.isEmpty() && s.getItem() == fare) p.wallet += s.getCount();
        }
        if (m.station() != Long.MIN_VALUE) {
            BlockPos here = BlockPos.fromLong(m.station());
            for (Map.Entry<Long, String> e : StationData.get(m.getWorld()).names().entrySet()) {
                if (e.getKey() == m.station()) continue;
                Dest d = new Dest();
                d.key = e.getKey();
                d.name = e.getValue();
                d.oneWay = m.fare(e.getKey(), false);
                d.round = m.fare(e.getKey(), true);
                d.distance = Math.sqrt(BlockPos.fromLong(e.getKey()).distanceSq(here));
                p.dests.add(d);
            }
            p.dests.sort((a, b) -> Double.compare(a.distance, b.distance));
            RailwayData data = RailwayData.get(m.getWorld());
            p.waiting = data.waitingAt(m.station());
            p.trains = data.trains().size();
        }
        return p;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeLong(machine.toLong());
        b.writeString(station);
        b.writeString(fareItem);
        b.writeVarInt(wallet);
        b.writeVarInt(waiting);
        b.writeVarInt(trains);
        b.writeString(message);
        b.writeVarInt(Math.min(dests.size(), 200));
        for (int i = 0; i < Math.min(dests.size(), 200); i++) {
            Dest d = dests.get(i);
            b.writeLong(d.key);
            b.writeString(d.name);
            b.writeVarInt(d.oneWay);
            b.writeVarInt(d.round);
            b.writeDouble(d.distance);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        machine = BlockPos.fromLong(b.readLong());
        station = b.readString(64);
        fareItem = b.readString(64);
        wallet = b.readVarInt();
        waiting = b.readVarInt();
        trains = b.readVarInt();
        message = b.readString(256);
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            Dest d = new Dest();
            d.key = b.readLong();
            d.name = b.readString(64);
            d.oneWay = b.readVarInt();
            d.round = b.readVarInt();
            d.distance = b.readDouble();
            dests.add(d);
        }
    }

    public static class Handler implements IMessageHandler<PacketTicketMenu, IMessage> {
        @Override
        public IMessage onMessage(PacketTicketMenu msg, MessageContext ctx) {
            RailMap.proxy.openTicketMachine(msg);
            return null;
        }
    }
}
