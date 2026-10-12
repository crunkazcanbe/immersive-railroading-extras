package com.dogpound.railmap.freight6;

import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The festive station decorations (batch 6 #30): their 12 V LED strings are fed from the nearest Festive Lighting
 * Controller (within 48 blocks) - lit when it says so, in its pattern, and only for their own season. A local switch
 * (k_on) can turn one display off. The renderer reads lit / pat / season / show.
 */
public abstract class TileFestive extends com.dogpound.railmap.depot.DepotTile {
    protected long lastGift = -1;
    private boolean resync;

    @Override public void markDirty() { super.markDirty(); resync = true; }

    protected TileFestive(String[] ids, String[] names, int[] sizes) { super(ids, names, sizes); }

    /** the season this display belongs to (Season.NONE = any) */
    protected abstract int ownSeason();

    @Override public String gearState() { return !powered() ? "NO 12 V" : ctl.get("lit") == 1 ? "LIT" : "dark (" + why() + ")"; }

    String why() {
        TileHolidayController c = TileHolidayController.near(world, pos);
        if (c == null) return "no Festive Lighting Controller within 48 blocks";
        if (!c.lightsOn()) return "controller off / daylight / out of season";
        if (ownSeason() != Season.NONE && c.season() != ownSeason()) return "not its season";
        return ctl.get("k_on") == 1 ? "local switch off" : "";
    }

    @Override protected String press(String id, EntityPlayer who) { return null; }

    @Override
    protected void work() {
        if (age % 10 != 0) return;
        if (resync) { resync = false; net.minecraft.block.state.IBlockState bs = world.getBlockState(pos); world.notifyBlockUpdate(pos, bs, bs, 3); }
        TileHolidayController c = TileHolidayController.near(world, pos);
        boolean lit = powered() && c != null && c.lightsOn() && (ownSeason() == Season.NONE || c.season() == ownSeason()) && ctl.get("k_on") == 0;
        put("lit", lit); put("pat", c == null ? 0 : c.pattern()); put("show", c != null && c.show()); put("season", c == null ? Season.now() : c.season());
        put("pwr", powered());
        tickMore();
    }

    protected void tickMore() {}

    /** once a game day: put a gift in storage k; false if already today or no room */
    protected boolean gift(int k, ItemStack... items) {
        long day = world.getTotalWorldTime() / 24000;
        if (day == lastGift) return false;
        for (ItemStack s : items) { ItemStack r = Cars.put(new net.minecraftforge.items.wrapper.InvWrapper(store(k)), s); if (!r.isEmpty()) return false; }
        lastGift = day;
        markDirty();
        controlsChanged();
        return true;
    }

    static ItemStack rnd(java.util.Random r, ItemStack... options) { return options[r.nextInt(options.length)].copy(); }
    static ItemStack cookies(int n) { return new ItemStack(Items.COOKIE, n); }

    protected void note(String what) { Recorder.event(world, what); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setLong("gift", lastGift); }
    @Override protected void loadExtra(NBTTagCompound t) { lastGift = t.hasKey("gift") ? t.getLong("gift") : -1; }

    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }
}
