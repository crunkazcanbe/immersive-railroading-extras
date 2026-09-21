package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
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
 * The handrail along the edge of a gantry walkway.
 *
 * Deliberately NOT the same block as the lattice leg. The leg is a ladder so the tower can be
 * climbed, and a climbable handrail is a stile: you walk into it, climb it, and step off the
 * far side into thin air. This one is a plain barrier -- nothing climbs it.
 */
public class BlockGantryRail extends Block {

    /**
     * Which way the rail runs. Without this every rail rendered on the same axis, so the ones
     * across the two ends sat crossways to their own edge and the whole handrail looked like a
     * row of spikes instead of a rail.
     */
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    private static final AxisAlignedBB RAIL =
            new AxisAlignedBB(0, 0, 0, 1, 1, 1);

    /**
     * One and a half blocks tall, which is the whole trick -- and exactly what vanilla fences
     * do. Auto-Jump is on by default, so a rail only one block tall is not a barrier at all:
     * the player hops it without even pressing jump and lands off the far side of the walkway.
     * A box this tall cannot be cleared, so the rail actually keeps you on the deck.
     */
    private static final AxisAlignedBB RAIL_COLLISION =
            new AxisAlignedBB(0, 0, 0, 1, 1.5, 1);

    public BlockGantryRail() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "gantry_rail");
        setTranslationKey(RailMap.MODID + ".gantry_rail");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(3f);
        setResistance(10f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7Handrail for a gantry walkway.");
        tip.add("§8Keeps you on the walkway. You cannot climb it.");
        tip.add("§8Placed and taken away with the gantry.");
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return RAIL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return RAIL_COLLISION;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
