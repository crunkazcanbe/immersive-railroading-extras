package com.dogpound.railmap.newrail6;

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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/** Garden Train Set (batch 6 #19): right-click Garden Track to put a train on it, heading the way you face. */
public class ItemGardenTrain extends Item {
    public ItemGardenTrain() {
        setRegistryName(RailMap.MODID, "garden_train_set");
        setTranslationKey(RailMap.MODID + ".garden_train_set");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(4);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (Garden.at(w, pos) == null) {
            if (!w.isRemote) p.sendStatusMessage(new TextComponentString("§7Put it on Garden Track"), true);
            return EnumActionResult.FAIL;
        }
        if (w.isRemote) return EnumActionResult.SUCCESS;
        int n = w.getEntitiesWithinAABB(EntityGardenTrain.class, new net.minecraft.util.math.AxisAlignedBB(pos).grow(256, 64, 256)).size() + 1;
        EntityGardenTrain t = EntityGardenTrain.place(w, pos, p.getHorizontalFacing(), n);
        if (t == null) return EnumActionResult.FAIL;
        w.spawnEntity(t);
        if (!p.capabilities.isCreativeMode) p.getHeldItem(hand).shrink(1);
        p.sendStatusMessage(new TextComponentString("§a" + t.name() + " on the line - right-click to drive, a Garden Station dispatches it"), true);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§70-4-0 tank engine, driving truck and two sit-astride cars (6 riders)");
        tip.add("§8Right-click Garden Track. Sneak + hit the empty train to pick it up");
    }
}
