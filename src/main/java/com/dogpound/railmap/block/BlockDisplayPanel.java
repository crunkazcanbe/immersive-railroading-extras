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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Wall-mounted screen tile, 3/16 thick, screen facing {@link #FACING}. Place it on a wall and
 * it hangs there; place more in a rectangle and they become one big screen.
 */
public class BlockDisplayPanel extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB AABB_N = new AxisAlignedBB(0, 0, 13 / 16.0, 1, 1, 1);
    private static final AxisAlignedBB AABB_S = new AxisAlignedBB(0, 0, 0, 1, 1, 3 / 16.0);
    private static final AxisAlignedBB AABB_E = new AxisAlignedBB(0, 0, 0, 3 / 16.0, 1, 1);
    private static final AxisAlignedBB AABB_W = new AxisAlignedBB(13 / 16.0, 0, 0, 1, 1, 1);

    public BlockDisplayPanel() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "display_panel");
        setTranslationKey(RailMap.MODID + ".display_panel");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.5f);
        setLightLevel(0.4f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileDisplayPanel p) {
            TileRailDisplay c = p.controller();
            if (!world.isRemote) {
                c.rescan();
            } else {
                // Client-only screen: see BlockDispatcherBoard -- a server-side openGui is dropped.
                BlockPos cp = c.getPos();
                player.openGui(RailMap.instance, RailMap.GUI_DISPATCHER_BOARD, world, cp.getX(), cp.getY(), cp.getZ());
            }
        }
        return true;
    }

    // ---- tile ---------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileDisplayPanel();
    }

    /** A neighbour changed: every panel in reach re-measures its wall on next use. */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileDisplayPanel p) p.markRectDirty();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        dirtyWall(world, pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        dirtyWall(world, pos);
    }

    private static void dirtyWall(World world, BlockPos pos) {
        int r = TileDisplayPanel.MAX_SIZE;
        for (BlockPos p : BlockPos.getAllInBoxMutable(pos.add(-r, -r, -r), pos.add(r, r, r))) {
            if (!world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileDisplayPanel d) d.markRectDirty();
        }
    }

    // ---- facing + shape -----------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    /** Clicked a wall side: screen faces out from that wall. Floor/ceiling: face the player. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        EnumFacing f = facing.getAxis().isHorizontal() ? facing : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, f);
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
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH: return AABB_S;
            case EAST: return AABB_E;
            case WEST: return AABB_W;
            default: return AABB_N;
        }
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
        return face == state.getValue(FACING).getOpposite() ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
