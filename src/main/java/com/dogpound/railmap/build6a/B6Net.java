package com.dogpound.railmap.build6a;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.function.Consumer;

/** Hi-rail cab panel -> server: a cab button pressed (entity id + op) */
public class B6Net implements IMessage {
    /** client: opens the cab panel (set by B6Client; a no-op on a dedicated server) */
    public static Consumer<EntityHiRail> openCab = e -> {};

    private int entity;
    private String op = "";

    public B6Net() {}
    public B6Net(int entity, String op) { this.entity = entity; this.op = op; }

    @Override public void toBytes(ByteBuf b) { PacketBuffer p = new PacketBuffer(b); p.writeVarInt(entity); p.writeString(op); }
    @Override public void fromBytes(ByteBuf b) { PacketBuffer p = new PacketBuffer(b); entity = p.readVarInt(); op = p.readString(32); }

    public static class Handler implements IMessageHandler<B6Net, IMessage> {
        @Override
        public IMessage onMessage(B6Net m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                Entity e = p.world.getEntityByID(m.entity);
                if (!(e instanceof EntityHiRail t) || p.getDistanceSq(e) > 64) return;
                String r;
                try { r = t.op(m.op, p); } catch (RuntimeException | LinkageError x) { r = "§c" + m.op + " failed: " + x; }
                if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true);
            });
            return null;
        }
    }
}
