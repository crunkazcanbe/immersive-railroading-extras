package com.dogpound.railmap.block;

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
 * Shared shape for the station hardware: a horizontal-facing, non-cube block with a Blockbench
 * model, that turns to face the player who places it. Subclasses add the tile and behaviour.
 */
public abstract class BlockStationDevice extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    private final String[] tips;

    protected BlockStationDevice(String id, Material material, String... tips) {
        super(material);
        this.tips = tips;
        setRegistryName(RailMap.MODID, id);
        setTranslationKey(RailMap.MODID + "." + id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(2.5f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        for (int i = 0; i < tips.length; i++) tip.add((i == 0 ? "§7" : "§8") + tips[i]);
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

    /** Box in north-facing model space (x, y, z in 0..16), rotated to the block's facing. */
    protected static AxisAlignedBB rotated(EnumFacing f, double x0, double y0, double z0, double x1, double y1, double z1) {
        double a0 = x0 / 16, b0 = z0 / 16, a1 = x1 / 16, b1 = z1 / 16;
        return switch (f) {
            case SOUTH -> new AxisAlignedBB(1 - a1, y0 / 16, 1 - b1, 1 - a0, y1 / 16, 1 - b0);
            case WEST -> new AxisAlignedBB(b0, y0 / 16, 1 - a1, b1, y1 / 16, 1 - a0);
            case EAST -> new AxisAlignedBB(1 - b1, y0 / 16, a0, 1 - b0, y1 / 16, a1);
            default -> new AxisAlignedBB(a0, y0 / 16, b0, a1, y1 / 16, b1);
        };
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
