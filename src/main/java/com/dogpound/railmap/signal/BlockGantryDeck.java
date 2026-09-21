package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * The walkway across the top of a gantry: a steel grating plate you actually stand on.
 *
 * It is a thin plate at the top of its block so the surface sits level with the deck height the
 * renderer draws the span at, instead of a metre of solid steel over the track.
 */
public class BlockGantryDeck extends Block {

    private static final AxisAlignedBB PLATE =
            new AxisAlignedBB(0, 12 / 16.0, 0, 1, 1, 1);

    public BlockGantryDeck() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "gantry_deck");
        setTranslationKey(RailMap.MODID + ".gantry_deck");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(3f);
        setResistance(10f);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7Walkway grating for a signal gantry.");
        tip.add("§8Walk across the top of the gantry on it.");
        tip.add("§8Placed and taken away with the gantry.");
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return PLATE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /** Solid enough on top to stand on and to hold a rail, open on the sides. */
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.UP ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
