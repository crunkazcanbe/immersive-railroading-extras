package com.dogpound.railmap.incident;

import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.block.IFurniture;
import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * One block for each piece of emergency-response / control-room gear (incident/): faces the player who places it,
 * its tile does the work. Furniture ones drop what is in them when broken; the exit sign lights the area.
 */
public class BlockIncident extends BlockStationDevice {
    private final Supplier<TileEntity> tile;
    private final double[] box;

    public BlockIncident(String id, Supplier<TileEntity> tile, double[] box, String... tips) {
        super(id, Material.IRON, tips);
        this.tile = tile;
        this.box = box;
        setHardness(2.5f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return tile.get(); }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return box == null ? FULL_BLOCK_AABB : rotated(state.getValue(FACING), box[0], box[1], box[2], box[3], Math.min(16, box[4]), box[5]);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity t = world.getTileEntity(pos);
        return t instanceof TileEvacSign s ? s.light() : 0;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileFurniture f && placer instanceof EntityPlayer p) f.placedBy(p);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof IFurniture o) o.dropAll();
        super.breakBlock(world, pos, state);
    }
}
