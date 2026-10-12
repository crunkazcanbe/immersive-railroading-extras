package com.dogpound.railmap.freight6;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Station Christmas Tree (batch 6 #30): a tall spruce in a planter tub, with baubles, tinsel, a star and a 12 V LED
 * string the Festive Lighting Controller runs (STEADY / CHASE / TWINKLE / FADE). PRESENTS under the tree is real
 * storage (27): each game day in the Christmas season a Christmas train standing within 20 blocks leaves a present
 * (cookies, a cake, fireworks, snowballs, a music disc now and then) - they show as wrapped boxes under the tree.
 */
public class TileXmasTree extends TileFestive {
    public TileXmasTree() { super(new String[]{"presents"}, new String[]{"Presents under the tree"}, new int[]{27}); }

    @Override public String wiredName() { return "Station Christmas tree"; }
    @Override protected int ownSeason() { return Season.CHRISTMAS; }

    /** a Christmas train leaves a present (once a game day) */
    public boolean present(String train) {
        java.util.Random r = world.rand;
        ItemStack gift = r.nextInt(12) == 0 ? new ItemStack(Items.RECORD_CAT) : rnd(r, cookies(8), new ItemStack(Items.CAKE), new ItemStack(Items.FIREWORKS, 3), new ItemStack(Items.SNOWBALL, 16), new ItemStack(Items.PUMPKIN_PIE, 2));
        if (!gift(0, gift)) return false;
        note(train + " left a present under the station Christmas tree at " + pos.getX() + "," + pos.getZ());
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.chime_bell"), net.minecraft.util.SoundCategory.BLOCKS, 1f, 1.4f);
        return true;
    }

    public int presents() { int n = 0; for (int i = 0; i < store(0).getSizeInventory(); i++) if (!store(0).getStackInSlot(i).isEmpty()) n++; return n; }

    @Override protected void tickMore() { put("presl", presents() > 0); }
}
