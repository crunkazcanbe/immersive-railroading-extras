package com.dogpound.railmap.build6a;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** places a Road-Rail Hi-Rail Truck where you click, facing the way you look */
public class ItemHiRail extends Item {
    public ItemHiRail() {
        setRegistryName(RailMap.MODID, "hirail_truck");
        setTranslationKey(RailMap.MODID + ".hirail_truck");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (w.isRemote) return EnumActionResult.SUCCESS;
        BlockPos at = pos.offset(side);
        EntityHiRail t = new EntityHiRail(w, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.rotationYaw);
        if (!w.getCollisionBoxes(null, t.getEntityBoundingBox()).isEmpty()) return EnumActionResult.FAIL;
        w.spawnEntity(t);
        if (!p.capabilities.isCreativeMode) p.getHeldItem(hand).shrink(1);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7Crew-cab flatbed with hydraulic rail guide wheels: drive it on the road, lower the guide wheels over track");
        t.add("§8Right-click to board; right-click while driving (or sneak + right-click it): cab panel. Sneak + hit: pick it up");
    }
}
