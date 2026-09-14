package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.graph.TrainNode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Server → client, twice a second while watching: every rolling stock in the dimension. */
public class PacketTrains implements IMessage {
    private int dim;
    private List<TrainNode> trains = new ArrayList<>();

    public PacketTrains() {}

    public PacketTrains(int dim, List<TrainNode> trains) {
        this.dim = dim;
        this.trains = trains;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        dim = b.readVarInt();
        int n = b.readVarInt();
        trains = new ArrayList<>(n);
        for (int i = 0; i < n && i < 4096; i++) trains.add(TrainNode.read(b));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeVarInt(dim);
        b.writeVarInt(trains.size());
        for (TrainNode t : trains) t.write(b);
    }

    public static class Handler implements IMessageHandler<PacketTrains, IMessage> {
        @Override
        public IMessage onMessage(PacketTrains msg, MessageContext ctx) {
            RailMap.proxy.acceptTrains(msg.dim, msg.trains); // proxy keeps client classes off the server
            return null;
        }
    }
}
