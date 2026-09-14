package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/** The Railroad Data Link cabinet: the OpenComputers side of the railroad. */
public class BlockDataLink extends BlockStationDevice {
    public BlockDataLink() {
        super("data_link", Material.IRON,
                "OpenComputers component: component.railroad",
                "Place next to a computer case or an OC adapter",
                "Trains, stations, signals, switches, routes and tickets from Lua");
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileDataLink();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            String s = Loader.isModLoaded("opencomputers")
                    ? "Railroad Data Link · in Lua: local rr = require(\"component\").railroad"
                    : "Railroad Data Link · needs OpenComputers installed";
            player.sendStatusMessage(new TextComponentString(s), true);
        }
        return true;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return true;
    }
}
