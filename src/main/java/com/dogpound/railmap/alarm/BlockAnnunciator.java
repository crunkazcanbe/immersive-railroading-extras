package com.dogpound.railmap.alarm;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** The Alarm Annunciator cabinet (see TileAnnunciator) */
public class BlockAnnunciator extends BlockStationDevice {
    public BlockAnnunciator() {
        super("annunciator", Material.IRON,
                "Control-room alarm annunciator: 12 backlit windows, horn and strobe, ACK / SILENCE / RESET / LAMP TEST",
                "Shows every IR Extras alarm (grid, signals, wrecks, freight...). Needs 12 V DC");
        setHardness(3f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileAnnunciator(); }
}
