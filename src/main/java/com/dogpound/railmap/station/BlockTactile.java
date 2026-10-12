package com.dogpound.railmap.station;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * Tactile paving (her ChatGPT list #81): the raised-profile paving blind and partially-sighted people feel underfoot -
 * BLISTER (rows of domes, at crossings and the top of steps) and the platform-edge OFFSET BLISTER in safety yellow.
 * A full-depth paving slab you walk on; the station audit counts it within 16 blocks of the stop.
 */
public class BlockTactile extends Block {
    private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    private final String tip;

    public BlockTactile(String id, String tip) {
        super(Material.ROCK);
        this.tip = tip;
        setRegistryName(RailMap.MODID, id);
        setTranslationKey(RailMap.MODID + "." + id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.5f);
        setSoundType(SoundType.STONE);
    }

    @Override public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) { t.add("§7" + tip); t.add("§8Counts for the station's accessibility rating"); }
    @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return BOX; }
}
