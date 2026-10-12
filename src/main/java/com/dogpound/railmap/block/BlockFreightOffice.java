package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** The Freight Office: the yard clerk's desk under the contract board (see TileFreightOffice) */
public class BlockFreightOffice extends BlockWideDesk {
    public BlockFreightOffice() {
        super("freight_office", Material.WOOD, null,
                "Freight contracts: industries post what they need moved; accept a job here, print the waybill, haul it, collect your pay",
                "Needs 12 V DC for its screens, printer and buttons");
        setHardness(2.0f);
    }

    /** the filing cabinet end and the cupboard / shelf end are solid: fill them with invisible parts where there is room */
    @Override
    public void onBlockPlacedBy(World world, net.minecraft.util.math.BlockPos pos, IBlockState state, net.minecraft.entity.EntityLivingBase placer, net.minecraft.item.ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) return;
        for (net.minecraft.util.math.BlockPos p : BlockOfficePart.parts(pos, state.getValue(FACING)))
            if (world.getBlockState(p).getBlock().isReplaceable(world, p)) world.setBlockState(p, com.dogpound.railmap.proxy.CommonProxy.officePart.getDefaultState(), 3);
    }

    @Override
    public void breakBlock(World world, net.minecraft.util.math.BlockPos pos, IBlockState state) {
        if (world.getTileEntity(pos) instanceof TileFreightOffice o) o.dropAll();
        super.breakBlock(world, pos, state);
        for (net.minecraft.util.math.BlockPos p : BlockOfficePart.parts(pos, state.getValue(FACING)))
            if (world.getBlockState(p).getBlock() instanceof BlockOfficePart) world.setBlockToAir(p);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileFreightOffice(); }
}
