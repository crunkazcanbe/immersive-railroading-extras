package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.server.Wear;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;

/** Maintenance Depot (railway list §13): parked trains beside it get their wheels, brakes, engine, bearings and electrics fixed. */
public class BlockMaintenanceDepot extends BlockStationDevice {
    public BlockMaintenanceDepot() {
        super("maintenance_depot", Material.IRON,
                "Repairs worn rolling stock parked beside it.",
                "Stop a train within a few blocks and wait",
                "Right-click: condition report of nearby stock");
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileMaintenanceDepot();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        List<String> lines = Wear.reportNear(world, pos, RailMapConfig.depotReach);
        player.sendMessage(new TextComponentString(TextFormatting.LIGHT_PURPLE + "🔧 Maintenance Depot" + TextFormatting.GRAY
                + (lines.isEmpty() ? " · no rolling stock within " + RailMapConfig.depotReach + " blocks" : " · " + lines.size() + " in the yard")));
        for (String l : lines) player.sendMessage(new TextComponentString(l));
        return true;
    }

    @Override
    public boolean canProvidePower(IBlockState state) { return true; }

    @Override
    public int getWeakPower(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos, net.minecraft.util.EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileMaintenanceDepot d ? d.redstoneOutput() : 0;
    }
}
