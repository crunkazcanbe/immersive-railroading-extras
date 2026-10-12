package com.dogpound.railmap.factory.ore;

import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.proxy.CommonProxy;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class FactoryTab extends CreativeTabs {
    public static final FactoryTab INSTANCE = new FactoryTab();

    private FactoryTab() { super("irextras_factory"); }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(CommonProxy.controllers.get(MachineType.JAW_CRUSHER));
    }
}
