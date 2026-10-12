package com.dogpound.railmap.factory.machine;

import com.dogpound.railmap.factory.Factory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;

/** Which machine every formed casing / port belongs to (per dimension), so breaking any part un-forms its machine. */
public class IndustryData extends WorldSavedData {
    private static final String NAME = Factory.OLD_MODID + "_owners";   // old save name kept so existing worlds load
    private final Map<Long, Long> owner = new HashMap<>();

    public IndustryData(String name) { super(name); }

    public static IndustryData get(World w) {
        MapStorage st = w.getPerWorldStorage();
        IndustryData d = (IndustryData) st.getOrLoadData(IndustryData.class, NAME);
        if (d == null) { d = new IndustryData(NAME); st.setData(NAME, d); }
        return d;
    }

    public BlockPos owner(BlockPos p) { Long o = owner.get(p.toLong()); return o == null ? null : BlockPos.fromLong(o); }
    public void set(BlockPos p, BlockPos controller) { owner.put(p.toLong(), controller.toLong()); markDirty(); }
    public void clear(BlockPos p) { if (owner.remove(p.toLong()) != null) markDirty(); }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        owner.clear();
        NBTTagList k = t.getTagList("k", 4), v = t.getTagList("v", 4);
        for (int i = 0; i < Math.min(k.tagCount(), v.tagCount()); i++)
            owner.put(((NBTTagLong) k.get(i)).getLong(), ((NBTTagLong) v.get(i)).getLong());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList k = new NBTTagList(), v = new NBTTagList();
        for (Map.Entry<Long, Long> e : owner.entrySet()) { k.appendTag(new NBTTagLong(e.getKey())); v.appendTag(new NBTTagLong(e.getValue())); }
        t.setTag("k", k); t.setTag("v", v);
        return t;
    }
}
