package com.dogpound.railmap.ops6;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** builds one device screen page for the client GUI (GuiOps6Screen): tabs, text lines, bar graphs, buttons */
public final class Scr {
    private final NBTTagCompound t = new NBTTagCompound();
    private final NBTTagList tabs = new NBTTagList(), lines = new NBTTagList(), bars = new NBTTagList(), buttons = new NBTTagList();

    public Scr(String title, String right, int page, String... tabNames) {
        t.setString("title", title);
        t.setString("right", right == null ? "" : right);
        t.setInteger("page", page);
        for (String s : tabNames) tabs.appendTag(new NBTTagString(s));
    }

    public Scr line(String s) { lines.appendTag(new NBTTagString(s)); return this; }
    /** a bar: label, value / max, colour (rgb), shown text */
    public Scr bar(String label, double v, double max, int rgb, String text) { bars.appendTag(new NBTTagString(label + "|" + v + "|" + max + "|" + rgb + "|" + text)); return this; }
    /** a button: action id, label, colour (rgb) */
    public Scr button(String id, String label, int rgb) { buttons.appendTag(new NBTTagString(id + "|" + label + "|" + rgb)); return this; }

    public NBTTagCompound build() {
        t.setTag("tabs", tabs); t.setTag("lines", lines); t.setTag("bars", bars); t.setTag("buttons", buttons);
        return t;
    }

    public static final int GREEN = 0x1F8A3A, RED = 0xB02020, AMBER = 0xC08010, BLUE = 0x2050B0, GREY = 0x505860;
}
