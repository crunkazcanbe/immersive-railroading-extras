package com.dogpound.railmap.settings;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;

/**
 * One option on a block's Settings Console (requested feature). Blocks list these; the console draws them page by page and sends changes back as text.
 */
public final class Setting {
    public enum Type { BOOL, NUM, CHOICE, TEXT, INFO }

    public final String page, key, label, help;
    public final Type type;
    public final int min, max, step;
    public final String unit, def;
    public final String[] choices;
    /** filled in when sent to the client */
    public String value = "";

    private Setting(String page, String key, String label, String help, Type type, String def, int min, int max, int step, String unit, String[] choices) {
        this.page = page; this.key = key; this.label = label; this.help = help; this.type = type; this.def = def;
        this.min = min; this.max = max; this.step = step; this.unit = unit == null ? "" : unit; this.choices = choices == null ? new String[0] : choices;
    }

    public static Setting bool(String page, String key, String label, String help, boolean def) {
        return new Setting(page, key, label, help, Type.BOOL, Boolean.toString(def), 0, 1, 1, "", null);
    }

    public static Setting num(String page, String key, String label, String help, int def, int min, int max, int step, String unit) {
        return new Setting(page, key, label, help, Type.NUM, Integer.toString(def), min, max, step, unit, null);
    }

    public static Setting choice(String page, String key, String label, String help, String def, String... choices) {
        return new Setting(page, key, label, help, Type.CHOICE, def, 0, choices.length - 1, 1, "", choices);
    }

    public static Setting text(String page, String key, String label, String help, String def, int maxLen) {
        return new Setting(page, key, label, help, Type.TEXT, def, 0, maxLen, 1, "", null);
    }

    /** a read-only line (live readouts); value = the text */
    public static Setting info(String page, String label, String value) {
        Setting s = new Setting(page, "", label, "", Type.INFO, value, 0, 0, 0, "", null);
        s.value = value;
        return s;
    }

    /** clamp / validate a value coming from a client */
    public String sanitize(String v) {
        if (v == null) return def;
        switch (type) {
            case BOOL: return Boolean.toString(Boolean.parseBoolean(v));
            case NUM:
                try { return Integer.toString(Math.max(min, Math.min(max, Integer.parseInt(v.trim())))); } catch (NumberFormatException e) { return def; }
            case CHOICE:
                for (String c : choices) if (c.equals(v)) return v;
                return def;
            case TEXT: return v.length() > max ? v.substring(0, max) : v;
            default: return def;
        }
    }

    public void write(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeString(page); b.writeString(key); b.writeString(label); b.writeString(help);
        b.writeByte(type.ordinal()); b.writeString(def); b.writeVarInt(min); b.writeVarInt(max); b.writeVarInt(step); b.writeString(unit);
        b.writeVarInt(choices.length);
        for (String c : choices) b.writeString(c);
        b.writeString(value.length() > 400 ? value.substring(0, 400) : value);
    }

    public static Setting read(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        String page = b.readString(64), key = b.readString(64), label = b.readString(128), help = b.readString(512);
        Type t = Type.values()[b.readByte()];
        String def = b.readString(512);
        int min = b.readVarInt(), max = b.readVarInt(), step = b.readVarInt();
        String unit = b.readString(32);
        String[] ch = new String[b.readVarInt()];
        for (int i = 0; i < ch.length; i++) ch[i] = b.readString(64);
        Setting s = new Setting(page, key, label, help, t, def, min, max, step, unit, ch);
        s.value = b.readString(512);
        return s;
    }
}
