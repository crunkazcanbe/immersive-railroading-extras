package com.dogpound.railmap.grid;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client → server: the conductor chosen in the cable picker, written onto the Power Cable stack in that hand. */
public class PacketCableSpec implements IMessage {
    private boolean offhand;
    private String spec = "";

    public PacketCableSpec() {}
    public PacketCableSpec(EnumHand hand, Elec.Spec s) { offhand = hand == EnumHand.OFF_HAND; spec = s.key(); }

    @Override public void fromBytes(ByteBuf b) { offhand = b.readBoolean(); spec = ByteBufUtils.readUTF8String(b); }
    @Override public void toBytes(ByteBuf b) { b.writeBoolean(offhand); ByteBufUtils.writeUTF8String(b, spec); }

    public static class Handler implements IMessageHandler<PacketCableSpec, IMessage> {
        @Override
        public IMessage onMessage(PacketCableSpec m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                ItemStack s = p.getHeldItem(m.offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND);
                if (!(s.getItem() instanceof ItemPowerCable)) return;
                Elec.Spec sp = Elec.Spec.parse(m.spec);                     // parse() falls back to LEGACY on junk
                ItemPowerCable.with(s, sp);
            });
            return null;
        }
    }
}
