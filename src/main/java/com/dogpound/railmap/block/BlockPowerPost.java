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

/** Substation / Charging Station block (railway list §10, §11). */
public class BlockPowerPost extends BlockStationDevice {
    private final TilePowerPost.Kind kind;

    public BlockPowerPost(TilePowerPost.Kind kind) {
        super(kind == TilePowerPost.Kind.SUBSTATION ? "substation" : "charging_station", Material.IRON,
                kind == TilePowerPost.Kind.SUBSTATION ? "Energises contact wire and third rail nearby." : "Charges parked electric and battery trains beside it.",
                kind == TilePowerPost.Kind.SUBSTATION ? "Electric locos under live wire run without fuel" : "Accepts Forge Energy (FE) from any generator",
                kind == TilePowerPost.Kind.SUBSTATION ? "Right-click: breaker · redstone = section off" : "Right-click: status");
        this.kind = kind;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TilePowerPost(kind);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TilePowerPost p) {
            if (kind == TilePowerPost.Kind.SUBSTATION && !player.isSneaking()) player.sendStatusMessage(new TextComponentString(p.click()), true);
            player.sendMessage(new TextComponentString(p.status()));
        }
        return true;
    }
}
