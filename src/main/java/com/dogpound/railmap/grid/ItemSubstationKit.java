package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * Unit substation kit (requested feature): drops a complete, correctly built 13.8 kV -> 480 V substation in front of you,
 * laid out like a real one along the direction you face:
 *
 *   in -> [MV cable] -> [MV breaker] -> [transformer] -+
 *          + earthing    + relay on top                    +-> [LV cable] -> [LV breaker] -> [LV cable] -> [LV cable] -> out
 *          + arrester                                          + earthing     + relay on top   + energy meter
 *
 * Panels face the aisle on your right. Feed the first cable from a 13.8 kV supply, take 480 V from the last one.
 */
public class ItemSubstationKit extends Item {
    public ItemSubstationKit() {
        setRegistryName(RailMap.MODID, "substation_kit");
        setTranslationKey(RailMap.MODID + ".substation_kit");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(16);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Right-click the ground: builds a 13.8 kV → 480 V unit substation ahead of you");
        tip.add("§7(6 long × 4 wide: breakers, relays, transformer, earthing, arrester, meter)");
    }

    /** {along, across (+ = right), up, kind, facing: 0 = panel to the right aisle, 1 = forward, 2 = left}.
     *  The transformer faces forward, so its primary (back) meets the MV breaker and its 480 V leaves from its right side;
     *  protection relays sit on top of their breakers; no cable ever enters a control panel. */
    private static final Object[][] LAYOUT = {
            {0, 0, 0, GridKind.CABLE, 0}, {0, 1, 0, GridKind.EARTHING, 0}, {0, -1, 0, GridKind.ARRESTER, 2},
            {1, 0, 0, GridKind.BREAKER, 0}, {1, 0, 1, GridKind.RELAY, 0},
            {2, 0, 0, GridKind.TRANSFORMER, 1},
            {2, 1, 0, GridKind.CABLE, 0}, {2, 2, 0, GridKind.EARTHING, 0},
            {3, 1, 0, GridKind.BREAKER, 0}, {3, 1, 1, GridKind.RELAY, 0},
            {4, 1, 0, GridKind.CABLE, 0}, {4, 2, 0, GridKind.METER, 0},
            {5, 1, 0, GridKind.CABLE, 0},
    };

    private static BlockPos at(BlockPos base, EnumFacing fwd, EnumFacing right, Object[] e) {
        return base.offset(fwd, (Integer) e[0]).offset(right, (Integer) e[1]).up((Integer) e[2]);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (w.isRemote) return EnumActionResult.SUCCESS;
        EnumFacing fwd = p.getHorizontalFacing(), right = fwd.rotateY();
        BlockPos base = w.getBlockState(pos).getBlock().isReplaceable(w, pos) ? pos : pos.offset(side);
        for (Object[] e : LAYOUT) {
            BlockPos at = at(base, fwd, right, e);
            if (!w.getBlockState(at).getBlock().isReplaceable(w, at)) {
                p.sendStatusMessage(new TextComponentString("§cNo room: something is in the way at " + at.getX() + " " + at.getY() + " " + at.getZ() + " (needs 6 long × 4 wide × 2 high clear ahead)"), true);
                return EnumActionResult.FAIL;
            }
        }
        for (Object[] e : LAYOUT) {
            GridKind k = (GridKind) e[3];
            Block b = Block.REGISTRY.getObject(new ResourceLocation(RailMap.MODID, k.id));
            if (!(b instanceof BlockGrid)) continue;
            BlockPos at = at(base, fwd, right, e);
            IBlockState st = b.getDefaultState();
            int face = (Integer) e[4];
            if (k != GridKind.CABLE) st = st.withProperty(BlockGrid.FACING, face == 1 ? fwd : face == 2 ? right.getOpposite() : right);
            w.setBlockState(at, st, 3);
        }
        if (!p.capabilities.isCreativeMode) p.getHeldItem(hand).shrink(1);
        p.sendMessage(new TextComponentString("§a⚡ Unit substation built: 13.8 kV in at the first cable, 480 V out at the last. Breakers have relays, both sides are earthed, the MV side has a surge arrester."));
        return EnumActionResult.SUCCESS;
    }
}
