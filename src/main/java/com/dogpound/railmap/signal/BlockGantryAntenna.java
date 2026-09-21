package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * A lineside radio mast: a slim pole with cross elements and a small dish.
 *
 * Exists because the gantry used to stamp a whole tower by itself, which took the building
 * away from the player. Towers are hand-built from the frame / walkway / handrail now, and
 * this is the thing you put on top of one. Stack several for a taller mast.
 *
 * Purely decorative -- it carries no signalling logic, so it can go anywhere without
 * changing how the railway behaves.
 */
public class BlockGantryAntenna extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    /** Slim, so a mast can sit in the same square as a walkway edge without blocking it. */
    private static final AxisAlignedBB MAST =
            new AxisAlignedBB(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);

    public BlockGantryAntenna() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "gantry_antenna");
        setTranslationKey(RailMap.MODID + ".gantry_antenna");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(2.5f);
        setResistance(8f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7Radio mast for the top of a tower.");
        tip.add("§8Stack them for a taller mast; the dish faces the way you stand.");
        tip.add("§8Decoration only -- it does not change any signalling.");
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
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
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return MAST;
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
