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
 * Protection engineer's laptop (her ChatGPT list #36: "a tool that displays which relay or breaker should trip first for
 * a simulated fault"). Right-click any grid part: a short-circuit study there - every breaker between the fault and the
 * sources, the current each sees, how fast each relay is, who trips first, the grading margins, under-rated breakers
 * and what backs up what. Sneak: an earth-fault study. On an IED: a secondary-injection test of its settings. The last
 * study also shows on the Control Center's PROTECT tab. Nothing is switched - it is a simulation of the live grid.
 */
public class ItemStudyLaptop extends Item {
    public ItemStudyLaptop() {
        setRegistryName(RailMap.MODID, "protection_laptop");
        setTranslationKey(RailMap.MODID + ".protection_laptop");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Right-click a grid part: short-circuit protection study");
        tip.add("§7Sneak + right-click: earth-fault study");
        tip.add("§7Right-click an IED: secondary-injection test");
        tip.add("§8Who trips first, grading margins, breaker ratings - nothing is switched");
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer p, World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, EnumHand hand) {
        if (!(w.getBlockState(pos).getBlock() instanceof BlockGrid)) return EnumActionResult.PASS;
        if (w.isRemote) return EnumActionResult.SUCCESS;
        GridData.Node n = GridData.get(w).node(pos);
        List<String> out = n != null && n.kind == GridKind.IED && !p.isSneaking() ? Protection.inject(w, pos) : Protection.study(w, pos, p.isSneaking());
        for (String l : out) p.sendMessage(new TextComponentString(l));
        return EnumActionResult.SUCCESS;
    }
}
