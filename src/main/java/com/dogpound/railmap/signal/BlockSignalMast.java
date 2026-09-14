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

/**
 * One American signal mast. Every style shares this block; the tile carries which one, so a
 * single registered block covers colour light, searchlight, PRR position light, B&amp;O colour
 * position, dwarf and semaphore.
 * <p>
 * Right-click: cycle mode (Automatic → Redstone → Fixed). Sneak-right-click: cycle heads.
 * Right-click with the Signal Wrench: change style. The mast also emits redstone — see
 * {@link TileSignalMast}.
 */
public class BlockSignalMast extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB MAST = new AxisAlignedBB(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);
    private static final AxisAlignedBB DWARF = new AxisAlignedBB(4 / 16.0, 0, 4 / 16.0, 12 / 16.0, 8 / 16.0, 12 / 16.0);

    private final SignalStyle defaultStyle;

    public BlockSignalMast(SignalStyle style) {
        super(Material.IRON);
        this.defaultStyle = style;
        setRegistryName(RailMap.MODID, "signal_" + style.id);
        setTranslationKey(RailMap.MODID + ".signal_" + style.id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(2.5f);
        setLightLevel(0.5f);   // the lamps glow
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public SignalStyle style() {
        return defaultStyle;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileSignalMast mast)) return true;
        if (player.isSneaking()) {
            mast.configure(mast.style(), mast.heads() % mast.style().maxHeads + 1);
        } else if (mast.mode() == TileSignalMast.Mode.MANUAL) {
            mast.cycleManualAspect();
        } else {
            mast.cycleMode();
        }
        player.sendStatusMessage(new TextComponentString(mast.statusLine()), true);
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7" + defaultStyle.label);
        tip.add("§8Right-click: mode · Sneak: heads");
        tip.add("§8Powers the block below: 15 clear, 7 approach, 0 stop");
        tip.add("§8Power it to hold the signal at Stop");
    }

    // ---- tile ---------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        TileSignalMast t = new TileSignalMast();
        t.configure(defaultStyle, defaultStyle == SignalStyle.DWARF ? 1 : 1);
        return t;
    }

    // ---- redstone -----------------------------------------------------------------------

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileSignalMast mast ? mast.redstoneOutput() : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        // Weak only: the mast lights a wire beside it without powering solid blocks through.
        return 0;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileSignalMast mast ? mast.redstoneOutput() : 0;
    }

    // ---- placement / shape ---------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    /** The signal faces the train it talks to, so it points back at the player placing it. */
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
        return defaultStyle.isDwarf() ? DWARF : MAST;
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

    /** Placing or breaking track nearby can change which piece the mast governs. */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileSignalMast mast) mast.forgetGovernedRail();
    }
}
