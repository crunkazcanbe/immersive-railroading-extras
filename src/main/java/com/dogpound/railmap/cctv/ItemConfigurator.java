package com.dogpound.railmap.cctv;

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

/** The CCTV configurator (an IPC test monitor): right-click a camera to set it up - name, aim with a live preview, field of
 *  view, privacy masks, motion zones + sensitivity, IR mode, focus, iris, motion -> PTZ preset. */
public class ItemConfigurator extends Item {
    public ItemConfigurator() {
        setRegistryName(RailMap.MODID, "cctv_configurator");
        setTranslationKey(RailMap.MODID + ".cctv_configurator");
        setMaxStackSize(1);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(pos) instanceof TileCamera)) return EnumActionResult.PASS;
        if (w.isRemote) com.dogpound.railmap.cctv.client.CctvClient.openCamera(pos);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Right-click a CCTV camera: name, aim (live preview), FOV, privacy masks, motion zones, IR");
    }
}
