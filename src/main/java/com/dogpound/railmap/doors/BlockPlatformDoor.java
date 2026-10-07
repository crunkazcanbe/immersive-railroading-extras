package com.dogpound.railmap.doors;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Platform screen door: a glass sliding door pair (or a fixed glass panel) along the platform edge, two blocks tall
 * with the header and the 'doors opening' lamps. Doors slide apart in four steps when the station door controller
 * opens the train, and only let you through while open.
 */
public class BlockPlatformDoor extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyInteger STAGE = PropertyInteger.create("stage", 0, 3);
    private static final Map<String, Boolean> TARGET = new HashMap<>();
    private final boolean panel;

    public BlockPlatformDoor(boolean panel) {
        super(Material.GLASS);
        this.panel = panel;
        String id = panel ? "psd_panel" : "psd_door";
        setRegistryName(RailMap.MODID, id);
        setTranslationKey(RailMap.MODID + "." + id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(2f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(STAGE, 0));
    }

    /** the controller asks the doors to open / close; they step there over a few ticks */
    public static void drive(World world, BlockPos pos, boolean open) {
        IBlockState st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof BlockPlatformDoor b) || b.panel) return;
        TARGET.put(world.provider.getDimension() + ":" + pos.toLong(), open);
        world.scheduleUpdate(pos, b, 2);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        Boolean open = TARGET.get(world.provider.getDimension() + ":" + pos.toLong());
        if (open == null) return;
        int s = state.getValue(STAGE), want = open ? 3 : 0;
        if (s == want) {
            TARGET.remove(world.provider.getDimension() + ":" + pos.toLong());
            return;
        }
        world.setBlockState(pos, state.withProperty(STAGE, s + (want > s ? 1 : -1)), 3);
        world.scheduleUpdate(pos, this, 3);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, STAGE);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(STAGE, Math.min(3, meta >> 2));
    }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex() | (s.getValue(STAGE) << 2); }

    private static final AxisAlignedBB NS = new AxisAlignedBB(0, 0, 0.4, 1, 1.5, 0.6), EW = new AxisAlignedBB(0.4, 0, 0, 0.6, 1.5, 1);

    @Override @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return state.getValue(FACING).getAxis() == EnumFacing.Axis.Z ? NS : EW;
    }

    @Override @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return !panel && state.getValue(STAGE) >= 2 ? NULL_AABB : getBoundingBox(state, world, pos);
    }

    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
}
