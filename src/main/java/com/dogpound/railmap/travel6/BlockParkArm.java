package com.dogpound.railmap.travel6;

import com.dogpound.railmap.block.BlockStationDevice;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The far half of a lowered car park barrier boom (batch 6b #10): invisible (the barrier's model draws the boom), a thin
 * bar at boom height that stops cars and people, placed and lifted by its TileParkBarrier. Orphaned, it removes itself.
 */
public class BlockParkArm extends BlockStationDevice {
    public BlockParkArm() {
        super("park_arm", Material.IRON);
        setCreativeTab(null);
        setBlockUnbreakable();
        setResistance(20f);
    }

    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.INVISIBLE; }
    @Override public Item getItemDropped(IBlockState s, Random r, int fortune) { return net.minecraft.init.Items.AIR; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return rotated(s.getValue(FACING), 0, 9, 6.5, 16, 12, 9.5); }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return rotated(s.getValue(FACING), 0, 8, 6, 16, 15, 10); }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState s, World w, BlockPos p, Block b, BlockPos from) {
        if (!w.isRemote && !(w.getTileEntity(p.offset(s.getValue(FACING).rotateY())) instanceof TileParkBarrier)) w.setBlockToAir(p);
    }
}
