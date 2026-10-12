package com.dogpound.railmap.stn;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** small shared helpers for the station services blocks */
public final class StnUtil {
    private StnUtil() {}

    /** print a written book out of the front of a machine (registers, diagrams, parcel waybills) */
    public static void print(World w, BlockPos pos, EnumFacing f, String title, String author, List<String> lines) {
        NBTTagList pages = new NBTTagList();
        StringBuilder p = new StringBuilder();
        int n = 0;
        for (String l : lines) {
            if (n >= 12 || p.length() + l.length() > 240) {
                pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
                p.setLength(0); n = 0;
                if (pages.tagCount() >= 48) break;
            }
            p.append(l).append('\n');
            n++;
        }
        if (p.length() > 0 && pages.tagCount() < 50) pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", title.length() > 30 ? title.substring(0, 30) : title);
        t.setString("author", author);
        t.setTag("pages", pages);
        book.setTagCompound(t);
        EntityItem e = new EntityItem(w, pos.getX() + 0.5 + f.getXOffset() * 0.7, pos.getY() + 1.1, pos.getZ() + 0.5 + f.getZOffset() * 0.7, book);
        e.motionX = f.getXOffset() * 0.05; e.motionZ = f.getZOffset() * 0.05;
        w.spawnEntity(e);
        w.playSound(null, pos, com.dogpound.railmap.ModSounds.PRINTER, SoundCategory.BLOCKS, 0.5f, 1.0f);
    }

    /** every loaded tile of a class within `reach` of a spot */
    public static <T> List<T> near(World w, Class<T> c, BlockPos at, double reach) {
        List<T> o = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (c.isInstance(t) && !t.isInvalid() && t.getPos().distanceSq(at) <= reach * reach) o.add(c.cast(t));
        return o;
    }

    public static <T> List<T> loaded(World w, Class<T> c) {
        List<T> o = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (c.isInstance(t) && !t.isInvalid()) o.add(c.cast(t));
        return o;
    }

    public static String cut(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, Math.max(1, n - 1)) + "."; }

    public static String up(String s) { return s == null ? "" : s.toUpperCase(java.util.Locale.ROOT); }
}
