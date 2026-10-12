package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The invisible, solid ends of the Freight Office and the other wide desks (BlockWideDesk) (the filing cabinet on one side, the ledger cupboard and shelf on the
 * other): clicks on the drawers / shelf there are handed to the office's controls, breaking one breaks the office.
 */
public class BlockOfficePart extends Block {
    public BlockOfficePart() {
        super(Material.WOOD);
        setRegistryName(RailMap.MODID, "office_part");
        setTranslationKey(RailMap.MODID + ".freight_office");
        setHardness(2f);
    }

    /** the two side blocks of an office at pos facing f (the model's -x and +x neighbours) */
    public static BlockPos[] parts(BlockPos pos, EnumFacing f) {
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        EnumFacing x = EnumFacing.EAST;
        for (int i = 0; i < turns; i++) x = x.rotateY();
        return new BlockPos[]{pos.offset(x), pos.offset(x.getOpposite())};
    }

    /** the office's facing seen from one of its parts (NORTH if orphaned) */
    /** the wide desk this side block belongs to (null if orphaned) - its 12 V terminal is the desk's */
    public static BlockPos desk(IBlockAccess w, BlockPos pos) {
        for (EnumFacing d : EnumFacing.HORIZONTALS) {
            IBlockState s = w.getBlockState(pos.offset(d));
            if (s.getBlock() instanceof BlockWideDesk)
                for (BlockPos p : parts(pos.offset(d), s.getValue(BlockStationDevice.FACING))) if (p.equals(pos)) return pos.offset(d);
        }
        return null;
    }

    private static EnumFacing facing(IBlockAccess w, BlockPos pos) {
        for (EnumFacing d : EnumFacing.HORIZONTALS) {
            IBlockState s = w.getBlockState(pos.offset(d));
            if (s.getBlock() instanceof BlockWideDesk)
                for (BlockPos p : parts(pos.offset(d), s.getValue(BlockStationDevice.FACING))) if (p.equals(pos)) return s.getValue(BlockStationDevice.FACING);
        }
        return EnumFacing.NORTH;
    }

    /** what you point at: only the back wall, so your aim reaches the drawer fronts and the shelf standing in front of it */
    @Override
    public net.minecraft.util.math.AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        return BlockStationDevice.rotated(facing(w, p), 0, 0, 15.0, 16, 16, 16);
    }

    /** what you bump into: the filing cabinet / cupboard depth */
    @Override
    public net.minecraft.util.math.AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        return BlockStationDevice.rotated(facing(w, p), 0, 0, 9.0, 16, 16, 16);
    }

    public static BlockPos master(World world, BlockPos pos) {
        for (EnumFacing d : EnumFacing.HORIZONTALS) {
            BlockPos m = pos.offset(d);
            IBlockState s = world.getBlockState(m);
            if (s.getBlock() instanceof BlockWideDesk)
                for (BlockPos p : parts(m, s.getValue(BlockStationDevice.FACING))) if (p.equals(pos)) return m;
        }
        return null;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, net.minecraft.util.EnumHand hand,
                                    EnumFacing facing, float hx, float hy, float hz) {
        BlockPos m = master(world, pos);
        if (m == null) return false;
        IBlockState ms = world.getBlockState(m);
        return ms.getBlock().onBlockActivated(world, m, ms, player, hand, facing, hx, hy, hz);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        BlockPos m = master(world, pos);
        if (m != null) world.destroyBlock(m, true);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && master(world, pos) == null) world.setBlockToAir(pos);   // orphaned
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        BlockPos m = master(world, pos);
        return m == null ? ItemStack.EMPTY : new ItemStack(world.getBlockState(m).getBlock());
    }

    @Override public int quantityDropped(Random r) { return 0; }
    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.INVISIBLE; }
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w, IBlockState s, BlockPos p, EnumFacing f) { return BlockFaceShape.UNDEFINED; }
}
