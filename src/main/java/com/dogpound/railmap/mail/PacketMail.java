package com.dogpound.railmap.mail;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** letter screen -> server: write the writing paper in that hand into a letter (batch 5 #8) */
public class PacketMail implements IMessage {
    private boolean off;
    private String to = "";
    private final List<String> lines = new ArrayList<>();

    public PacketMail() {}
    public PacketMail(EnumHand hand, String to, List<String> lines) { this.off = hand == EnumHand.OFF_HAND; this.to = to; this.lines.addAll(lines); }

    @Override
    public void toBytes(ByteBuf b) {
        PacketBuffer p = new PacketBuffer(b);
        p.writeBoolean(off); p.writeString(to);
        p.writeByte(Math.min(8, lines.size()));
        for (int i = 0; i < Math.min(8, lines.size()); i++) p.writeString(lines.get(i));
    }

    @Override
    public void fromBytes(ByteBuf b) {
        PacketBuffer p = new PacketBuffer(b);
        off = p.readBoolean(); to = p.readString(64);
        int n = Math.min(8, p.readByte());
        for (int i = 0; i < n; i++) lines.add(p.readString(80));
    }

    public static class Handler implements IMessageHandler<PacketMail, IMessage> {
        @Override
        public IMessage onMessage(PacketMail m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                EnumHand h = m.off ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
                ItemStack held = p.getHeldItem(h);
                if (!ItemMail.is(held, ItemMail.PAPER)) return;
                if (m.to.trim().isEmpty()) { p.sendStatusMessage(new TextComponentString("§cA letter needs an address (To)"), true); return; }
                ItemStack letter = ItemMail.write(p.getName(), m.to, m.lines);
                held.shrink(1);
                if (held.isEmpty()) p.setHeldItem(h, letter);
                else if (!p.inventory.addItemStackToInventory(letter)) p.dropItem(letter, false);
                p.world.playSound(null, p.getPosition(), net.minecraft.init.SoundEvents.BLOCK_CLOTH_PLACE, SoundCategory.PLAYERS, 0.8f, 1f);
                p.sendStatusMessage(new TextComponentString("§aLetter to " + m.to.trim() + " sealed - post it at a Sorting Office"), true);
            });
            return null;
        }
    }
}
