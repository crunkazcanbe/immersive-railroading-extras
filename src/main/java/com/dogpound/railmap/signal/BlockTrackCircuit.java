package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
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

/** The flat plate that goes on the ground beside the rail: insulated joint (boundary) or track circuit (detector). */
public class BlockTrackCircuit extends Block {
    private static final AxisAlignedBB PLATE = new AxisAlignedBB(0, 0, 0, 1, 2 / 16.0, 1);

    private final boolean joint;

    public BlockTrackCircuit(boolean joint) {
        super(Material.IRON);
        this.joint = joint;
        String id = joint ? "insulated_joint" : "track_circuit";
        setRegistryName(RailMap.MODID, id);
        setTranslationKey(RailMap.MODID + "." + id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.5f);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileTrackCircuit c) {
            if (joint) {
                player.sendStatusMessage(new TextComponentString(
                        "Insulated joint — signal blocks start and end here"), true);
            } else {
                c.cycleRange();
                player.sendStatusMessage(new TextComponentString(
                        "Track circuit range: " + c.range() + " blocks"), true);
            }
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        if (joint) {
            tip.add("§7Place on the ground 2-3 blocks from the track centre (outside the train) to split the line into signal blocks.");
            tip.add("§8Signals look ahead as far as the next joint.");
        } else {
            tip.add("§7Detects trains and outputs redstone while one is near.");
            tip.add("§8Right-click to set the range (2-64 blocks).");
        }
    }

    // ---- tile ---------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return joint ? new TileTrackCircuit.Joint() : new TileTrackCircuit.Circuit();
    }

    // ---- redstone -----------------------------------------------------------------------

    @Override
    public boolean canProvidePower(IBlockState state) {
        return !joint;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (joint) return 0;
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileTrackCircuit c ? c.redstoneOutput() : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (joint) return 0;
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileTrackCircuit c && side == EnumFacing.UP ? 0 : getWeakPower(state, world, pos, side);
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return !joint;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileTrackCircuit c ? c.redstoneOutput() : 0;
    }

    // ---- shape --------------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return PLATE;
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
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
