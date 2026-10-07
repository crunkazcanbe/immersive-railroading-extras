package com.dogpound.railmap.grid;

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

/**
 * Lockout / tagout padlock (requested feature): hang it on a breaker, disconnector, earthing switch, generator, motor,
 * converter or intake in its SAFE state (open / stopped / earthed) and nothing can switch it back - no button, no
 * redstone, no auto-reclose - until the person who hung it takes it off (sneak + right-click with an empty hand).
 */
public class ItemPadlock extends Item {
    public ItemPadlock() {
        setRegistryName(RailMap.MODID, "lockout_padlock");
        setTranslationKey(RailMap.MODID + ".lockout_padlock");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(16);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Right-click equipment in its safe state to lock it out");
        tip.add("§7Only you can remove it: sneak + right-click it empty-handed");
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer p, World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, EnumHand hand) {
        if (!(w.getTileEntity(pos) instanceof TileGrid t)) return EnumActionResult.PASS;
        if (w.isRemote) return EnumActionResult.SUCCESS;
        if (!t.loto().isEmpty()) {
            p.sendStatusMessage(new TextComponentString("§c🔒 Already locked out by " + t.loto()), true);
            return EnumActionResult.SUCCESS;
        }
        String why = t.lockable();
        if (!why.isEmpty()) {
            p.sendStatusMessage(new TextComponentString("§eCan't lock it like this: " + why), true);
            return EnumActionResult.SUCCESS;
        }
        t.setLoto(p.getName());
        if (!p.capabilities.isCreativeMode) p.getHeldItem(hand).shrink(1);
        p.sendMessage(new TextComponentString("§a🔒 Locked out and tagged: DANGER - DO NOT OPERATE (" + p.getName() + "). Safe to work on."));
        return EnumActionResult.SUCCESS;
    }

    /** sneak + empty hand on a locked device: its owner takes the padlock back */
    static boolean remove(TileGrid t, EntityPlayer p) {
        if (t.loto().isEmpty()) return false;
        if (!t.loto().equals(p.getName()) && !p.capabilities.isCreativeMode) {
            p.sendStatusMessage(new TextComponentString("§c🔒 That's " + t.loto() + "'s padlock - only they can remove it"), true);
            return true;
        }
        t.setLoto("");
        if (!p.capabilities.isCreativeMode) p.addItemStackToInventory(new ItemStack(com.dogpound.railmap.proxy.CommonProxy.padlock));
        p.sendStatusMessage(new TextComponentString("§a🔓 Padlock removed - the equipment can be operated again"), true);
        return true;
    }
}
