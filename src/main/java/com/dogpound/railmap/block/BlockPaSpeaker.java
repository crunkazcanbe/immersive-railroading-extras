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

/** The horn on the platform canopy. */
public class BlockPaSpeaker extends BlockStationDevice {

    public BlockPaSpeaker() {
        super("pa_speaker", Material.IRON,
                "Reads out arrivals and departures on the platform.",
                "Place at a named station · everyone within earshot hears it",
                "Right-click: repeat the last announcement");
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TilePaSpeaker();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TilePaSpeaker s) {
            player.sendStatusMessage(new TextComponentString(s.statusLine()), true);
        }
        return true;
    }
}
