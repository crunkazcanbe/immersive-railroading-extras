package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * A three-block-wide piece of office furniture (Freight Office, Company HQ, Commodity Exchange, Travel Centre, Container
 * Terminal desk): the model reaches a block to each side, where invisible solid parts (BlockOfficePart) fill in so you
 * can't walk through it and your clicks on the side drawers / shelves reach it.
 */
public abstract class BlockWideDesk extends BlockStationDevice {
    private final Supplier<TileEntity> tile;

    protected BlockWideDesk(String id, Material m, Supplier<TileEntity> tile, String... tips) {
        super(id, m, tips);
        this.tile = tile;
        setHardness(2.0f);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) return;
        for (BlockPos p : BlockOfficePart.parts(pos, state.getValue(FACING)))
            if (world.getBlockState(p).getBlock().isReplaceable(world, p)) world.setBlockState(p, com.dogpound.railmap.proxy.CommonProxy.officePart.getDefaultState(), 3);
        if (world.getTileEntity(pos) instanceof TileFurniture f && placer instanceof net.minecraft.entity.player.EntityPlayer p) f.placedBy(p);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (world.getTileEntity(pos) instanceof IFurniture o) o.dropAll();
        super.breakBlock(world, pos, state);
        for (BlockPos p : BlockOfficePart.parts(pos, state.getValue(FACING)))
            if (world.getBlockState(p).getBlock() instanceof BlockOfficePart) world.setBlockToAir(p);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return tile != null; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return tile == null ? null : tile.get(); }
}
