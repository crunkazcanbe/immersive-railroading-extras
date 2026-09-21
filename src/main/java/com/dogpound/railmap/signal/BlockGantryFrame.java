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
 * A storey of a gantry's corner leg. The old gantry was drawn entirely by a renderer, so it was
 * a picture of a tower -- nothing to stand on and nothing to walk round. Four of these make a
 * real 3x3 tower with an open middle you can stand inside, and because the lattice is a ladder
 * you can climb a corner to the walkway on top.
 *
 * The post is deliberately narrow: the tower has to be walk-through, so a full cube here would
 * fence the middle off and defeat the point.
 */
public class BlockGantryFrame extends Block {

    private static final AxisAlignedBB POST =
            new AxisAlignedBB(3 / 16.0, 0, 3 / 16.0, 13 / 16.0, 1, 13 / 16.0);

    public BlockGantryFrame() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "gantry_frame");
        setTranslationKey(RailMap.MODID + ".gantry_frame");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(3f);
        setResistance(10f);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7Corner leg of a signal gantry.");
        tip.add("§8Climb it like a ladder to reach the walkway.");
        tip.add("§8Placed and taken away with the gantry.");
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return POST;
    }



    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /**
     * Reported solid so the access ladder can attach and stay attached. The legs sit on the
     * corners of a five-wide tower, so all four sides are still three blocks of open air to
     * walk in through -- nothing is fenced off by this.
     */
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.SOLID;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
