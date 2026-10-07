package com.dogpound.railmap.industry;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** An industry building: one block, a 3x3 model around it. Put a Loading Silo / Unloading Pit beside it. */
public class BlockIndustry extends BlockStationDevice {
    public final IndustryKind kind;

    public BlockIndustry(IndustryKind kind) {
        super("industry_" + kind.id, Material.IRON, kind.tip,
                "Put a Loading Silo or Unloading Pit next to it; trains carry its goods",
                "Sneak-right-click with the Signal Wrench: production settings + stockpile");
        this.kind = kind;
        setHardness(3f);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileIndustry(kind); }

    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TileIndustry t ? t.comparator() : 0;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing f,
                                    float hx, float hy, float hz) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileIndustry t)
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(t.statusLine()), true);
        return true;
    }
}
