package com.dogpound.railmap.freight6;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Halloween Platform Display (batch 6 #30): a hay-bale stack of carved jack-o'-lanterns (their candles are the 12 V
 * LEDs the controller flickers), a scarecrow, cobwebs on a lamp post, a crooked TRICK OR TREAT sign and the treat bowl
 * (real storage, 9). On a Halloween night a ghost train standing within 20 blocks fills the bowl (once a game day).
 */
public class TileHalloweenDisplay extends TileFestive {
    public TileHalloweenDisplay() { super(new String[]{"bowl"}, new String[]{"Treat bowl"}, new int[]{9}); }

    @Override public String wiredName() { return "Halloween display"; }
    @Override protected int ownSeason() { return Season.HALLOWEEN; }

    public boolean treat(String train) {
        java.util.Random r = world.rand;
        if (!gift(0, rnd(r, cookies(6), new ItemStack(Items.PUMPKIN_PIE, 2), new ItemStack(Items.SUGAR, 8), new ItemStack(Items.APPLE, 4)), rnd(r, cookies(2), new ItemStack(Items.MELON, 4)))) return false;
        note("The ghost train " + train + " left treats in the pumpkin display's bowl at " + pos.getX() + "," + pos.getZ());
        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_GHAST_AMBIENT, net.minecraft.util.SoundCategory.BLOCKS, 0.4f, 0.7f);
        return true;
    }

    @Override protected void tickMore() {
        boolean any = false;
        for (int i = 0; i < store(0).getSizeInventory(); i++) any |= !store(0).getStackInSlot(i).isEmpty();
        put("bowll", any);
    }
}
