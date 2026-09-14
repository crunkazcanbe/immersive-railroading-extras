package com.dogpound.railmap.item;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.network.PacketRescan;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * Pocket map. Right-click: the client asks the server for a scan centred on the player
 * ({@link PacketRescan} with no position); the GUI opens when the network arrives.
 */
public class ItemRailMap extends Item {
    public ItemRailMap() {
        setRegistryName(RailMap.MODID, "rail_map");
        setTranslationKey(RailMap.MODID + ".rail_map");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (world.isRemote) {
            RailMap.NETWORK.sendToServer(new PacketRescan(null));
            player.swingArm(hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
