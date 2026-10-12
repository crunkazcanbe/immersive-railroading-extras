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
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/** An invisible, solid filler of the Control Center console: clicks and breaking go to the console it belongs to. */
public class BlockConsolePart extends Block {
    public BlockConsolePart() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "console_part");
        setTranslationKey(RailMap.MODID + ".dispatcher_board");
        setHardness(2f);
    }

    /** the console this part belongs to, or null */
    public static BlockPos master(World world, BlockPos pos) {
        for (int dy = -1; dy <= 0; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos m = pos.add(dx, dy, dz);
                    IBlockState s = world.getBlockState(m);
                    if (s.getBlock() instanceof BlockControlConsole
                            && BlockControlConsole.parts(m, s.getValue(BlockDispatcherBoard.FACING)).contains(pos)) return m;
                }
        return null;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        BlockPos m = master(world, pos);
        if (m == null) return false;
        IBlockState ms = world.getBlockState(m);
        return ms.getBlock().onBlockActivated(world, m, ms, player, hand, facing, hitX, hitY, hitZ);
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
