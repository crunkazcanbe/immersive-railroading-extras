package com.dogpound.railmap.ops6;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Batch 6 E's one packet, both ways, an NBT tag with an "op":
 * client -> server: poll / act (a device screen page or button), rco / rcopoll (beltpack), sim / simpoll (training desk);
 * server -> client: open / screen (device screens), rcostate, opensim / simstate, clocks (slave clock offsets).
 */
public class PacketOps6 implements IMessage {
    public NBTTagCompound tag = new NBTTagCompound();

    public PacketOps6() {}
    public PacketOps6(NBTTagCompound t) { tag = t; }

    public static PacketOps6 of(String op) { NBTTagCompound t = new NBTTagCompound(); t.setString("op", op); return new PacketOps6(t); }
    public PacketOps6 pos(BlockPos p) { tag.setLong("pos", p.toLong()); return this; }
    public PacketOps6 str(String k, String v) { tag.setString(k, v); return this; }
    public PacketOps6 num(String k, int v) { tag.setInteger(k, v); return this; }
    public PacketOps6 data(NBTTagCompound d) { tag.setTag("d", d); return this; }

    @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, tag); }
    @Override public void fromBytes(ByteBuf buf) { tag = ByteBufUtils.readTag(buf); if (tag == null) tag = new NBTTagCompound(); }

    public static class Handler implements IMessageHandler<PacketOps6, IMessage> {
        @Override
        public IMessage onMessage(PacketOps6 m, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) client(m);
            else { EntityPlayerMP p = ctx.getServerHandler().player; p.getServerWorld().addScheduledTask(() -> server(p, m.tag)); }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void client(PacketOps6 m) { net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.ops6.client.Ops6Client.receive(m.tag)); }
    }

    /** a device whose screen is a GUI with pages (crane LMI, master clock, lubricator, sim instructor) */
    public interface Screen {
        /** the page to show: title, right, tabs, lines, bars, buttons */
        NBTTagCompound screen(EntityPlayer p, int page);
        /** a screen button: the message to show (null = none) */
        String screenAction(EntityPlayer p, int page, String action);
    }

    static void server(EntityPlayerMP p, NBTTagCompound t) {
        String op = t.getString("op");
        BlockPos pos = BlockPos.fromLong(t.getLong("pos"));
        switch (op) {
            case "poll", "act" -> {
                if (p.getDistanceSq(pos) > 16 * 16 || !p.world.isBlockLoaded(pos)) return;
                TileEntity te = p.world.getTileEntity(pos);
                if (!(te instanceof Screen s)) return;
                int page = t.getInteger("page");
                if (op.equals("act")) {
                    String r = s.screenAction(p, page, t.getString("a"));
                    if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true);
                }
                Ops6Net.NET.sendTo(of("screen").pos(pos).data(s.screen(p, page)), p);
            }
            case "rco", "rcopoll" -> {
                if (op.equals("rco")) Rco.command(p, t.getString("c"), t.getInteger("v"));
                Ops6Net.NET.sendTo(of("rcostate").data(Rco.state(p)), p);
            }
            case "sim", "simpoll" -> {
                if (p.getDistanceSq(pos) > 16 * 16 || !(p.world.getTileEntity(pos) instanceof TileDriverSim d)) return;
                if (op.equals("sim")) { String r = d.input(p, t.getString("c")); if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true); }
                d.seen(p);
                Ops6Net.NET.sendTo(of("simstate").pos(pos).data(d.simState()), p);
            }
            default -> { }
        }
    }

    /** server: open a device screen for this player */
    public static void open(EntityPlayer p, TileEntity te, int page) {
        if (p instanceof EntityPlayerMP mp && te instanceof Screen s) Ops6Net.NET.sendTo(of("open").pos(te.getPos()).data(s.screen(p, page)), mp);
    }
}
