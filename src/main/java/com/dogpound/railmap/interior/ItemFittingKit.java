package com.dogpound.railmap.interior;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;

/**
 * Interior Fitting Kit: right-click inside or next to an Immersive Railroading passenger car to pick its interior
 * (coach / café / sleeper / automatic by name / none). One kit per fit-out; the fitter owns the car's café till and
 * crew panel. The click is caught client-side by InteriorClient (riding players can't use items on IR cars otherwise).
 */
public class ItemFittingKit extends Item {
    public ItemFittingKit() {
        setRegistryName(RailMap.MODID, "interior_kit");
        setTranslationKey(RailMap.MODID + ".interior_kit");
        setMaxStackSize(16);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Use in / beside an IR passenger car: choose Coach, Café or Sleeper");
        tip.add("§8Seats you sit in, berths you sleep in, café till (Pride coins), fridge, kettle,");
        tip.add("§8espresso machine, toilet, luggage racks, next-stop screens, saloon CCTV, crew panel");
    }
}
