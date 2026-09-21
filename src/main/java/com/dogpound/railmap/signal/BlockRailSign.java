package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The universal railway sign. One block, every sign face — cycle them with the Signal Wrench, and
 * the CUSTOM face opens a text box so you can write your own. Drawn entirely by a TESR, so the
 * chunk model is just an invisible marker; the post + board + text are rendered live.
 */
public class BlockRailSign extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    private static final AxisAlignedBB POST =
            new AxisAlignedBB(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);

    public BlockRailSign() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "rail_sign");
        setTranslationKey(RailMap.MODID + ".rail_sign");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.2f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
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
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRailSign();
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return POST;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /**
     * Bare-handed right-click on the CUSTOM sign opens the text editor too (so you don't need the
     * wrench just to retype it). Any other face tells you what it is.
     */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!(world.getTileEntity(pos) instanceof TileRailSign sign)) {
            return false;
        }
        if (!player.getHeldItem(hand).isEmpty()) {
            return false;   // let the wrench (or whatever) handle it
        }
        if (world.isRemote && sign.isCustom()) {
            RailMap.proxy.openSignTextGui(pos, sign.customText());
        }
        return true;
    }
}
