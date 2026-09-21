package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * One storey of a loadout gantry's leg. Placed automatically in a column under each end of the
 * beam when the gantry goes down, and taken away with it — a block model can't reach across a
 * track on its own, so the structure is built out of real blocks instead.
 */
public class BlockGantryLeg extends BlockStationDevice {

    private static final AxisAlignedBB POST =
            new AxisAlignedBB(4 / 16.0, 0, 4 / 16.0, 12 / 16.0, 1, 12 / 16.0);

    public BlockGantryLeg() {
        super("gantry_leg", Material.IRON,
                "Part of a loadout gantry.",
                "Placed with the gantry — break the gantry to take it down",
                "");
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return POST;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.sendStatusMessage(new TextComponentString("Gantry leg"), true);
        }
        return true;
    }
}
