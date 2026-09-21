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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** Crossbuck with flashing lights, the gate-arm mechanism, and the cantilever over wide roads. */
public class BlockCrossing extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB POST = new AxisAlignedBB(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);

    private final TileCrossing.Kind kind;

    public BlockCrossing(TileCrossing.Kind kind) {
        super(Material.IRON);
        this.kind = kind;
        String id = switch (kind) {
            case SIGNAL -> "crossing_signal";
            case GATE -> "crossing_gate";
            case CANTILEVER -> "crossing_cantilever";
        };
        setRegistryName(RailMap.MODID, id);
        setTranslationKey(RailMap.MODID + "." + id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(2.5f);
        setLightLevel(0.35f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public TileCrossing.Kind kind() {
        return kind;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileCrossing c) {
            c.cycleApproach();
            player.sendStatusMessage(new TextComponentString(
                    "Crossing wakes at " + c.approach() + " blocks" + (c.active() ? " · ACTIVE now" : "")), true);
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7Wakes on an approaching train, holds, then clears.");
        tip.add("§8Right-click to set the approach distance.");
        tip.add("§8Power it to force it down · outputs 15 while active");
    }

    // ---- tile ---------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return switch (kind) {
            case SIGNAL -> new TileCrossing.Signal();
            case GATE -> new TileCrossing.Gate();
            case CANTILEVER -> new TileCrossing.Cantilever();
        };
    }

    // ---- redstone -----------------------------------------------------------------------

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileCrossing c ? c.redstoneOutput() : 0;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileCrossing c ? c.redstoneOutput() : 0;
    }

    // ---- shape --------------------------------------------------------------------------

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
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return POST;
    }

    /**
     * Gates get a big selection box that runs the length of the arm, so a right-click on the
     * arm itself — not just the post — cycles the approach distance. Signals and cantilevers
     * keep the tight post box.
     */
    @Override
    public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World world, BlockPos pos) {
        if (kind != TileCrossing.Kind.GATE) return super.getSelectedBoundingBox(state, world, pos);
        return POST.union(armBox(state.getValue(FACING))).offset(pos);
    }

    /** The arm's reach as an AABB in block-local space; direction matches CrossingRenderer. */
    private static AxisAlignedBB armBox(EnumFacing facing) {
        double reach = 4.6, w = 0.30, yLo = 0.95, yHi = 1.6, c = 0.5;
        int i = facing.getHorizontalIndex();
        double dx = i == 0 ? 1 : i == 2 ? -1 : 0;   // local +X after the renderer's -index*90 Y-spin
        double dz = i == 1 ? 1 : i == 3 ? -1 : 0;
        double ex = dx * reach, ez = dz * reach;
        return new AxisAlignedBB(
                Math.min(c, c + ex) - (dx == 0 ? w : 0), yLo, Math.min(c, c + ez) - (dz == 0 ? w : 0),
                Math.max(c, c + ex) + (dx == 0 ? w : 0), yHi, Math.max(c, c + ez) + (dz == 0 ? w : 0));
    }

    /** The TESR draws the whole signal, so the block model itself must not render too. */
    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.INVISIBLE;
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
