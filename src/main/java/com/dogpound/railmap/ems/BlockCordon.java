package com.dogpound.railmap.ems;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

/**
 * Police cordon barrier: a crowd-control barrier with POLICE LINE tape, put up by a police car on scene (Ems.cordon) on
 * empty ground only, and always taken away again when the car leaves. You can't walk through it; it drops nothing.
 */
public class BlockCordon extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB SEL = new AxisAlignedBB(0, 0, 0.3, 1, 1, 0.7), SEL_X = new AxisAlignedBB(0.3, 0, 0, 0.7, 1, 1);
    private static final AxisAlignedBB COL = new AxisAlignedBB(0, 0, 0.3, 1, 1.5, 0.7), COL_X = new AxisAlignedBB(0.3, 0, 0, 0.7, 1.5, 1);

    public BlockCordon() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "police_cordon");
        setTranslationKey(RailMap.MODID + ".police_cordon");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.0f);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(m & 3)); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing f, float hx, float hy, float hz, int meta, net.minecraft.entity.EntityLivingBase by) { return getDefaultState().withProperty(FACING, by.getHorizontalFacing()); }
    private static boolean alongX(IBlockState s) { return s.getValue(FACING).getAxis() == EnumFacing.Axis.Z; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return alongX(s) ? SEL : SEL_X; }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return alongX(s) ? COL : COL_X; }
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public Item getItemDropped(IBlockState s, Random r, int fortune) { return net.minecraft.init.Items.AIR; }
    @Override public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) { t.add("§7Police cordon barrier - put up and taken away by police cars at incidents"); }
}
