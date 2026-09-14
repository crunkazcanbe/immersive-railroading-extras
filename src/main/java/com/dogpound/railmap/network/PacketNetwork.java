package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.graph.RailNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server → client: a full network snapshot for the handheld map (tiles use the vanilla tile-update path). */
public class PacketNetwork implements IMessage {
    private NBTTagCompound tag = new NBTTagCompound();

    public PacketNetwork() {}

    public PacketNetwork(RailNetwork net) {
        tag = net.toNBT();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tag = ByteBufUtils.readTag(new PacketBuffer(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketNetwork, IMessage> {
        @Override
        public IMessage onMessage(PacketNetwork msg, MessageContext ctx) {
            RailMap.proxy.acceptHandheldNetwork(RailNetwork.fromNBT(msg.tag));
            return null;
        }
    }
}
