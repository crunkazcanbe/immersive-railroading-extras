package com.dogpound.railmap;

import com.dogpound.railmap.proxy.CommonProxy;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The mod's own creative tab.
 *
 * <p>Everything used to go into vanilla's Transportation tab, which is fine in a small pack and
 * useless in a 900-mod one — the blocks registered correctly and still could not be found. A
 * named tab makes the whole mod one click away, and gives search something to filter on.
 */
public final class RailMapTab extends CreativeTabs {
    public static final RailMapTab INSTANCE = new RailMapTab();

    private RailMapTab() {
        super(RailMap.MODID);
    }

    @Override
    public ItemStack createIcon() {
        // The dispatcher board is the mod in one picture. Fall back to the wrench if the
        // block somehow isn't registered yet (tab icons are requested early).
        if (CommonProxy.dispatcherBoard != null) {
            Item i = Item.getItemFromBlock(CommonProxy.dispatcherBoard);
            if (i != Item.getItemFromBlock(net.minecraft.init.Blocks.AIR)) return new ItemStack(i);
        }
        if (CommonProxy.signalWrench != null) return new ItemStack(CommonProxy.signalWrench);
        return new ItemStack(net.minecraft.init.Items.MINECART);
    }

    @Override
    public String getTranslationKey() {
        return RailMap.MODID;
    }
}
