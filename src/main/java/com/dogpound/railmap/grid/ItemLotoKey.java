package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The key of one lockout padlock, stamped with the device it locks (her ChatGPT list #44). Whoever holds the key can
 * take that padlock off; hang it in a Permit Board's key safe to issue a permit - while the permit is live the safe
 * keeps it.
 */
public class ItemLotoKey extends Item {
    public ItemLotoKey() {
        setRegistryName(RailMap.MODID, "lockout_key");
        setTranslationKey(RailMap.MODID + ".lockout_key");
        setMaxStackSize(1);
    }

    public static ItemStack forDevice(BlockPos p, String owner, String what) {
        ItemStack s = new ItemStack(com.dogpound.railmap.proxy.CommonProxy.lotoKey);
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("dev", p.toLong()); t.setString("owner", owner); t.setString("what", what);
        s.setTagCompound(t);
        return s;
    }

    public static long device(ItemStack s) { return s.getItem() instanceof ItemLotoKey && s.hasTagCompound() ? s.getTagCompound().getLong("dev") : Long.MIN_VALUE; }

    /** right-click the padlocked device with its key: the padlock comes off (unless a live permit holds it) */
    @Override
    public net.minecraft.util.EnumActionResult onItemUseFirst(net.minecraft.entity.player.EntityPlayer p, World w, BlockPos pos, net.minecraft.util.EnumFacing side, float hx, float hy, float hz, net.minecraft.util.EnumHand hand) {
        if (!(w.getTileEntity(pos) instanceof TileGrid t)) return net.minecraft.util.EnumActionResult.PASS;
        if (w.isRemote) return net.minecraft.util.EnumActionResult.SUCCESS;
        ItemStack s = p.getHeldItem(hand);
        if (device(s) != pos.toLong() || t.loto().isEmpty()) {
            p.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§7This key doesn't fit a padlock here"), true);
            return net.minecraft.util.EnumActionResult.SUCCESS;
        }
        String held = Permits.lockHeld(w, pos);
        if (!held.isEmpty()) { p.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§c" + held), true); return net.minecraft.util.EnumActionResult.SUCCESS; }
        History.log(w, pos, "LOTO", "padlock (" + t.loto() + ") removed with its key by " + p.getName());
        t.setLoto("");
        s.shrink(1);
        if (!p.capabilities.isCreativeMode) p.addItemStackToInventory(new ItemStack(com.dogpound.railmap.proxy.CommonProxy.padlock));
        p.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§a🔓 Unlocked with its key - padlock back in your pocket"), true);
        return net.minecraft.util.EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        if (!s.hasTagCompound()) return;
        BlockPos p = BlockPos.fromLong(s.getTagCompound().getLong("dev"));
        tip.add("§7Padlock on §f" + s.getTagCompound().getString("what") + " §7at " + p.getX() + " " + p.getY() + " " + p.getZ());
        tip.add("§7Owner: " + s.getTagCompound().getString("owner"));
        tip.add("§8Sneak + right-click the padlock holding this key to remove it · hang it in a Permit Board key safe");
    }
}
