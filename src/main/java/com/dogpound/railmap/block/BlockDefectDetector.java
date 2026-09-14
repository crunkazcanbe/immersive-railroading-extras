package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** The detector's trackside bungalow. Place it right beside the rail. */
public class BlockDefectDetector extends BlockStationDevice {
    public BlockDefectDetector() {
        super("defect_detector", Material.IRON,
                "Announces every passing train on the radio.",
                "Place beside the track · rename in an anvil",
                "Sneak-right-click: track number · redstone pulse on each report");
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileDefectDetector();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (stack.hasDisplayName() && world.getTileEntity(pos) instanceof TileDefectDetector d) d.setName(stack.getDisplayName());
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileDefectDetector d) {
            if (player.isSneaking()) d.stepTrack();
            player.sendStatusMessage(new TextComponentString(d.statusLine()), true);
        }
        return true;
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileDefectDetector d ? d.redstoneOutput() : 0;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return rotated(state.getValue(FACING), 2, 0, 3, 14, 16, 13);
    }
}
