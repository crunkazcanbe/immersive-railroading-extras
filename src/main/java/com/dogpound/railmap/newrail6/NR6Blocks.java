package com.dogpound.railmap.newrail6;

import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.function.Supplier;

/** Batch 6 #16-20 blocks: rack rail + entry, avalanche gallery, garden track (no tiles) and the panels (SafetyBlock). */
public final class NR6Blocks {
    private NR6Blocks() {}

    /** a panel / machine: faces the player, its tile does the work */
    public static class Part extends SafetyBlock {
        public Part(String id, Supplier<TileEntity> tile, double[] box, String... tips) { super(id, tile, box, tips); }

        @Override
        public void breakBlock(World w, BlockPos pos, IBlockState state) {
            if (!w.isRemote && w.getTileEntity(pos) instanceof TileFerryDock d) {
                NR6Data data = NR6Data.get(w);
                for (NR6Data.Route r : data.routes.values()) {
                    if (r.dockA == pos.toLong()) r.dockA = 0;
                    if (r.dockB == pos.toLong()) r.dockB = 0;
                }
                data.changed();
            }
            super.breakBlock(w, pos, state);
        }
    }

    /** a plain piece laid along the track (facing = along it): rack lengths and the avalanche gallery */
    public static class Piece extends BlockStationDevice {
        private final AxisAlignedBB box;
        public Piece(String id, Material m, AxisAlignedBB box, String... tips) { super(id, m, tips); this.box = box; setHardness(3f); }
        @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return box; }
    }

    /** ride-on garden railway track: straight or curved by itself (Garden.shapeFor) */
    public static class GardenTrack extends BlockStationDevice {
        public static final PropertyEnum<Garden.Shape> SHAPE = PropertyEnum.create("shape", Garden.Shape.class);
        static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 3 / 16.0, 1);

        public GardenTrack() {
            super("garden_track", Material.IRON,
                    "Garden railway track: 7 1/4 inch gauge aluminium rail on recycled-plastic sleepers",
                    "Lays itself straight or curved to the track beside it - keep parallel lines a block apart. Put a Garden Train Set on it");
            setHardness(0.8f);
            setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(SHAPE, Garden.Shape.NS));
        }

        @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, SHAPE); }

        @Override
        public IBlockState getActualState(IBlockState s, IBlockAccess w, BlockPos p) {
            Garden.Shape sh = Garden.at(w, p);
            return s.withProperty(SHAPE, sh == null ? Garden.Shape.NS : sh);
        }

        @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return BOX; }
        @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return NULL_AABB; }
        @Override public boolean isPassable(IBlockAccess w, BlockPos p) { return true; }
    }
}
