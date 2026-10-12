package com.dogpound.railmap.light;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The light a yard floodlight throws on the ground: an invisible, walk-through, replaceable "air" block that only gives
 * block light (LEVEL 1-15). Floodlights place and remove them in batches (LightHelpers); one left behind with no lit
 * floodlight owning it removes itself on a random tick, so they can never pile up.
 */
public class BlockLightHelper extends Block {
    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 0, 15);

    public BlockLightHelper() {
        super(Material.AIR);
        setRegistryName(RailMap.MODID, "light_helper");
        setTranslationKey(RailMap.MODID + ".light_helper");
        setDefaultState(blockState.getBaseState().withProperty(LEVEL, 15));
        setTickRandomly(true);
        setHardness(0f);
        setLightOpacity(0);
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, LEVEL); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(LEVEL, meta & 15); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(LEVEL); }

    @Override public int getLightValue(IBlockState s, IBlockAccess w, BlockPos p) { return s.getValue(LEVEL); }
    @SuppressWarnings("deprecation") @Override public int getLightValue(IBlockState s) { return s.getValue(LEVEL); }
    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.INVISIBLE; }
    @Override public boolean isAir(IBlockState s, IBlockAccess w, BlockPos p) { return true; }
    @Override public boolean isReplaceable(IBlockAccess w, BlockPos p) { return true; }
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public boolean canCollideCheck(IBlockState s, boolean hitIfLiquid) { return false; }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return NULL_AABB; }
    @Override public Item getItemDropped(IBlockState s, Random r, int fortune) { return net.minecraft.init.Items.AIR; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w, IBlockState s, BlockPos p, EnumFacing f) { return BlockFaceShape.UNDEFINED; }
    @Override public boolean canSpawnInBlock() { return true; }

    @Override
    public void randomTick(World w, BlockPos p, IBlockState s, Random r) {
        if (!w.isRemote) LightHelpers.orphanCheck(w, p);
    }
}
