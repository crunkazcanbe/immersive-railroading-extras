package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The two ends of a freight operation: the silo that fills cars and the pit that empties them.
 * Both sit beside the track with a chest, barrel or hopper against them as the stockpile.
 */
public class BlockFreightTerminal extends BlockStationDevice {

    private final boolean loader;

    public BlockFreightTerminal(boolean loader) {
        super(loader ? "loading_silo" : "unloading_pit", Material.IRON,
                loader ? "Overhead loadout: straddles the track and fills the car underneath."
                        : "Bottom-dump pit: empties the car standing over it.",
                loader ? "Place ONE BLOCK ABOVE the rail · chest or hopper against a leg"
                        : "Place UNDER the rail · chest or hopper against it",
                "Works on a creeping train · comparator reads the car's fill level");
        this.loader = loader;
    }

    /** True while a gantry is tearing its own legs down, so the legs don't recurse back. */
    private static boolean demolishing = false;

    /**
     * A loadout gantry straddles the track, so placing the head end builds the two leg columns
     * beside it down to whatever it is standing on.
     */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                net.minecraft.entity.EntityLivingBase placer,
                                net.minecraft.item.ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !loader) {
            return;
        }
        EnumFacing f = state.getValue(FACING);
        for (EnumFacing side : new EnumFacing[]{f.rotateY(), f.rotateYCCW()}) {
            BlockPos leg = pos.offset(side);
            for (int drop = 0; drop < 8; drop++) {
                if (!world.isAirBlock(leg) && !world.getBlockState(leg).getBlock().isReplaceable(world, leg)) {
                    break;
                }
                world.setBlockState(leg, com.dogpound.railmap.proxy.CommonProxy.gantryLeg.getDefaultState(), 3);
                leg = leg.down();
            }
        }
    }

    /** Taking the gantry down takes its legs with it. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (loader && !demolishing) {
            demolishing = true;
            EnumFacing f = state.getValue(FACING);
            for (EnumFacing side : new EnumFacing[]{f.rotateY(), f.rotateYCCW()}) {
                BlockPos leg = pos.offset(side);
                for (int drop = 0; drop < 8; drop++) {
                    if (world.getBlockState(leg).getBlock() != com.dogpound.railmap.proxy.CommonProxy.gantryLeg) {
                        break;
                    }
                    world.setBlockToAir(leg);
                    leg = leg.down();
                }
            }
            demolishing = false;
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    protected net.minecraft.block.state.BlockStateContainer createBlockState() {
        return new net.minecraft.block.state.BlockStateContainer(this, FACING, SCALED);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return state.withProperty(SCALED, te instanceof TileFreightTerminal t && t.scale() != 1f);
    }

    public static final net.minecraft.block.properties.PropertyBool SCALED =
            net.minecraft.block.properties.PropertyBool.create("scaled");

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return loader ? new TileFreightTerminal.Loader() : new TileFreightTerminal.Unloader();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileFreightTerminal t) {
            player.sendStatusMessage(new TextComponentString(t.statusLine()), true);
        }
        return true;
    }

    /** The gantry hangs over the track, so nothing about it is solid — trains run under it
     *  and you can walk between its legs. */
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.util.math.AxisAlignedBB getCollisionBoundingBox(IBlockState state,
            IBlockAccess world, BlockPos pos) {
        return loader ? NULL_AABB : FULL_BLOCK_AABB;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TileFreightTerminal t ? t.redstoneOutput() : 0;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileFreightTerminal t ? t.redstoneOutput() : 0;
    }
}
