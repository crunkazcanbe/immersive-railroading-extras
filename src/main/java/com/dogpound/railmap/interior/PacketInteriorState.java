package com.dogpound.railmap.interior;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server -> client: what one fitted car's interior shows right now (lamps, screens, items on racks, the café menu). */
public class PacketInteriorState implements IMessage {
    public int entity, layout;
    public boolean powered, lit;
    public final Map<String, Integer> vals = new HashMap<>();
    public final Map<String, String> txt = new HashMap<>();
    /** part index -> what is on it (display) */
    public final Map<Integer, List<ItemStack>> items = new LinkedHashMap<>();
    /** café menu: fridge slot, item, price (cents) */
    public final List<Object[]> menu = new ArrayList<>();
    public transient long at;

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeVarInt(entity); b.writeByte(layout); b.writeBoolean(powered); b.writeBoolean(lit);
        b.writeVarInt(vals.size());
        for (Map.Entry<String, Integer> e : vals.entrySet()) { b.writeString(e.getKey()); b.writeVarInt(e.getValue()); }
        b.writeVarInt(txt.size());
        for (Map.Entry<String, String> e : txt.entrySet()) { b.writeString(e.getKey()); b.writeString(cut(e.getValue())); }
        b.writeVarInt(items.size());
        for (Map.Entry<Integer, List<ItemStack>> e : items.entrySet()) {
            b.writeVarInt(e.getKey()); b.writeVarInt(e.getValue().size());
            for (ItemStack s : e.getValue()) b.writeItemStack(s);
        }
        b.writeVarInt(menu.size());
        for (Object[] m : menu) { b.writeVarInt((Integer) m[0]); b.writeItemStack((ItemStack) m[1]); b.writeVarLong((Long) m[2]); }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        try {
            entity = b.readVarInt(); layout = b.readByte(); powered = b.readBoolean(); lit = b.readBoolean();
            for (int i = b.readVarInt(); i > 0; i--) vals.put(b.readString(64), b.readVarInt());
            for (int i = b.readVarInt(); i > 0; i--) txt.put(b.readString(64), b.readString(512));
            for (int i = b.readVarInt(); i > 0; i--) {
                int k = b.readVarInt();
                List<ItemStack> l = new ArrayList<>();
                for (int j = b.readVarInt(); j > 0; j--) l.add(b.readItemStack());
                items.put(k, l);
            }
            for (int i = b.readVarInt(); i > 0; i--) menu.add(new Object[]{b.readVarInt(), b.readItemStack(), b.readVarLong()});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String cut(String s) { return s == null ? "" : s.length() > 500 ? s.substring(0, 500) : s; }

    public static class Handler implements IMessageHandler<PacketInteriorState, IMessage> {
        @Override
        public IMessage onMessage(PacketInteriorState m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.railmap.interior.client.InteriorClient.receive(m));
            return null;
        }
    }
}
