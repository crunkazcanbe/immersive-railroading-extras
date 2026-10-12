package com.dogpound.railmap.newrail6;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;
import java.util.UUID;

/** Batch 6 #16-20's own channel (no shared packet ids): server -> client, which wagons carry a motorail vehicle. */
public final class NR6Net {
    private NR6Net() {}

    public static SimpleNetworkWrapper NET;

    static void init() {
        if (NET != null) return;
        NET = NetworkRegistry.INSTANCE.newSimpleChannel("irextras_nr6");
        NET.registerMessage(Loads.Handler.class, Loads.class, 0, Side.CLIENT);
    }

    public static class Loads implements IMessage {
        public NBTTagCompound tag = new NBTTagCompound();

        public Loads() {}

        public Loads(Map<UUID, NR6Data.Load> loads) {
            NBTTagList l = new NBTTagList();
            for (Map.Entry<UUID, NR6Data.Load> e : loads.entrySet()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setUniqueId("u", e.getKey());
                c.setString("n", e.getValue().label);
                l.appendTag(c);
            }
            tag.setTag("l", l);
        }

        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, tag); }
        @Override public void fromBytes(ByteBuf buf) { tag = ByteBufUtils.readTag(buf); if (tag == null) tag = new NBTTagCompound(); }

        public static class Handler implements IMessageHandler<Loads, IMessage> {
            @Override
            public IMessage onMessage(Loads m, MessageContext ctx) {
                if (ctx.side == Side.CLIENT) client(m);
                return null;
            }

            @SideOnly(Side.CLIENT)
            private static void client(Loads m) { Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.newrail6.client.NR6Client.loads(m.tag)); }
        }
    }
}
