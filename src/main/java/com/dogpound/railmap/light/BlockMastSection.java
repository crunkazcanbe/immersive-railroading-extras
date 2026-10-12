package com.dogpound.railmap.light;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** a galvanised lattice-free tubular mast section with its climbing ladder: stack them and put a Yard Floodlight on top */
public class BlockMastSection extends BlockStationDevice {
    public BlockMastSection() {
        super("floodlight_mast", Material.IRON,
                "Floodlight mast section: stack as tall as you like, a Yard Floodlight head goes on top",
                "Galvanised tube with a caged ladder and cable riser");
        setLightOpacity(0);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return rotated(state.getValue(FACING), 4.5, 0, 4.5, 11.5, 16, 13.5);
    }
}
