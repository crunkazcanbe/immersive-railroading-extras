package com.dogpound.railmap.ride;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collection;

/** Server -> client: every line of the player's dimension (geometry + running state + the live readings the mimic needs). */
public class PacketRide implements IMessage {
    public NBTTagCompound tag = new NBTTagCompound();

    public PacketRide() {}

    public PacketRide(int dim, Collection<RideData.Line> lines) {
        tag.setInteger("dim", dim);
        NBTTagList l = new NBTTagList();
        for (RideData.Line x : lines) {
            NBTTagCompound c = new NBTTagCompound();
            x.write(c);
            c.setFloat("wind", (float) x.wind);
            c.setFloat("kw", (float) x.kw);
            c.setFloat("rpd", (float) x.rpdMm);
            c.setByte("mode", (byte) x.mode);
            c.setFloat("carr", (float) x.carriage);
            NBTTagList cv = new NBTTagList();
            for (double v : x.carV) cv.appendTag(new net.minecraft.nbt.NBTTagDouble(v));
            c.setTag("carV", cv);
            l.appendTag(c);
        }
        tag.setTag("lines", l);
    }

    /** rebuild the lines on the client */
    public static void apply(NBTTagCompound t, java.util.Map<Integer, RideData.Line> into) {
        java.util.Map<Integer, RideData.Line> fresh = new java.util.HashMap<>();
        NBTTagList l = t.getTagList("lines", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            RideData.Line x = into.get(c.getInteger("id"));
            if (x == null) x = new RideData.Line();
            x.read(c);
            x.wind = c.getFloat("wind"); x.kw = c.getFloat("kw"); x.rpdMm = c.getFloat("rpd"); x.mode = c.getByte("mode"); x.carriage = c.getFloat("carr");
            NBTTagList cv = c.getTagList("carV", 6);
            x.carV = new double[cv.tagCount()];
            for (int k = 0; k < x.carV.length; k++) x.carV[k] = cv.getDoubleAt(k);
            fresh.put(x.id, x);
        }
        into.clear();
        into.putAll(fresh);
    }

    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, tag); }
    @Override public void fromBytes(ByteBuf buf) { tag = ByteBufUtils.readTag(buf); if (tag == null) tag = new NBTTagCompound(); }

    public static class Handler implements IMessageHandler<PacketRide, IMessage> {
        @Override
        public IMessage onMessage(PacketRide m, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) client(m);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void client(PacketRide m) { Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.ride.client.RideClient.sync(m.tag)); }
    }
}
