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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * One leg of a signal bridge. Place a leg each side of the track, both facing the oncoming
 * train, and they build the span between them by themselves.
 */
public class BlockSignalBridge extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB LEG = new AxisAlignedBB(4 / 16.0, 0, 4 / 16.0, 12 / 16.0, 1, 12 / 16.0);

    public BlockSignalBridge() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "signal_bridge");
        setTranslationKey(RailMap.MODID + ".signal_bridge");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(3f);
        setLightLevel(0.4f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileSignalBridge b) {
            b.markDirtyLayout();
            player.sendStatusMessage(new TextComponentString(b.statusLine()), true);
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7The gantry trains ride under.");
        tip.add("§8Place one each side of the track, both facing the train.");
        tip.add("§8They span themselves and hang a head over every track below.");
        tip.add("§8Outputs redstone for the best aspect on the bridge.");
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileSignalBridge();
    }

    // ---- redstone -----------------------------------------------------------------------

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileSignalBridge b ? b.redstoneOutput() : 0;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileSignalBridge b ? b.redstoneOutput() : 0;
    }

    // ---- placement / shape ---------------------------------------------------------------

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

    /** A new leg (or a lost one) changes the span, so tell every leg in reach to re-measure. */
    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        poke(world, pos);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // Take the real tower and walkway down with the leg, or breaking a gantry would leave
        // a staircase of orphaned steel standing in the air.
        GantryTower.clear(world, pos, state.getValue(FACING).rotateYCCW());
        super.breakBlock(world, pos, state);
        poke(world, pos);
    }

    private static void poke(World world, BlockPos pos) {
        int r = TileSignalBridge.MAX_SPAN;
        for (EnumFacing d : EnumFacing.HORIZONTALS) {
            for (int i = 1; i <= r; i++) {
                BlockPos p = pos.offset(d, i);
                if (!world.isBlockLoaded(p)) break;
                TileEntity te = world.getTileEntity(p);
                if (te instanceof TileSignalBridge b) b.markDirtyLayout();
            }
        }
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return LEG;
    }

    /** The renderer draws the legs and the span, so the block model itself stays hidden. */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
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

    /** The leg is a lattice you can climb, like a ladder, to reach the walkway across the top. */
    @Override
    public boolean isLadder(net.minecraft.block.state.IBlockState state, net.minecraft.world.IBlockAccess world,
                            net.minecraft.util.math.BlockPos pos, net.minecraft.entity.EntityLivingBase entity) {
        return true;
    }
}
