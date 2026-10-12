package com.dogpound.railmap.factory.ore;

import com.dogpound.railmap.factory.Factory;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.List;

/**
 * A Shipping Crate (Crate Packer): one full stack of one item, nailed shut. Crates stack 16 high, so a freight car of
 * crates carries 16x what it would loose - unpack them at the other end.
 */
public class ItemCrate extends Item {
    public ItemCrate() {
        setRegistryName(Factory.MODID, "crate");
        setTranslationKey(Factory.MODID + ".crate");
        setMaxStackSize(16);
        setCreativeTab(FactoryTab.INSTANCE);
    }

    public static ItemStack contents(ItemStack crate) {
        NBTTagCompound t = crate.getTagCompound();
        return t == null || !t.hasKey("load") ? ItemStack.EMPTY : new ItemStack(t.getCompoundTag("load"));
    }

    public static ItemStack pack(ItemStack load) {
        ItemStack c = new ItemStack(com.dogpound.railmap.factory.proxy.CommonProxy.crate);
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("load", load.writeToNBT(new NBTTagCompound()));
        c.setTagCompound(t);
        return c;
    }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        ItemStack in = contents(s);
        return in.isEmpty() ? "Shipping Crate (empty)" : "Crate of " + in.getCount() + " " + in.getDisplayName();
    }

    @Override public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) { t.add("§7Packed by the Crate Packer; unpack it there too"); }
}
