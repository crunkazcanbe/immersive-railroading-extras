package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Overhead line equipment. Three pieces build a whole electrified railway:
 * a <b>mast</b> beside the track, a <b>portal</b> beam to span several tracks, and the
 * <b>contact wire</b> that runs between them.
 * <p>
 * The wire finds its own neighbours the way the signal wire does — place a run of it and it
 * joins up end to end, so a line can be wired without placing a single corner piece by hand.
 */
public class BlockCatenary extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    /** wire only: which way it continues */
    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool WEST = PropertyBool.create("west");

    public enum Kind {
        MAST("catenary_mast", "Catenary Mast", 0.45f, 1.0f),
        PORTAL("catenary_portal", "Catenary Portal", 1.0f, 1.0f),
        WIRE("catenary_wire", "Contact Wire", 1.0f, 1.0f);

        public final String id;
        public final String label;
        public final float width, height;

        Kind(String id, String label, float width, float height) {
            this.id = id;
            this.label = label;
            this.width = width;
            this.height = height;
        }
    }

    private final Kind kind;
    private final AxisAlignedBB box;

    /**
     * Block's own constructor calls createBlockState() before our fields exist, so the kind has
     * to be parked somewhere static for that one call to read. Standard 1.12 dance.
     */
    private static Kind building;

    private static Material prep(Kind kind) {
        building = kind;
        return Material.IRON;
    }

    public BlockCatenary(Kind kind) {
        super(prep(kind));
        this.kind = kind;
        double half = kind.width / 2;
        this.box = new AxisAlignedBB(0.5 - half, 0, 0.5 - half, 0.5 + half, kind.height, 0.5 + half);
        setRegistryName(RailMap.MODID, kind.id);
        setTranslationKey(RailMap.MODID + "." + kind.id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.6f);
        IBlockState st = blockState.getBaseState();
        if (kind == Kind.WIRE) {
            st = st.withProperty(NORTH, false).withProperty(SOUTH, false)
                   .withProperty(EAST, false).withProperty(WEST, false);
        } else {
            st = st.withProperty(FACING, EnumFacing.NORTH);
        }
        setDefaultState(st);
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return building == Kind.WIRE
                ? new BlockStateContainer(this, NORTH, SOUTH, EAST, WEST)
                : new BlockStateContainer(this, FACING);
    }

    /** Wire joins to any other catenary piece next to it, so a run wires itself up. */
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (kind != Kind.WIRE) {
            return state;
        }
        return state.withProperty(NORTH, joins(world, pos.north()))
                .withProperty(SOUTH, joins(world, pos.south()))
                .withProperty(EAST, joins(world, pos.east()))
                .withProperty(WEST, joins(world, pos.west()));
    }

    private static boolean joins(IBlockAccess world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockCatenary;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer,
                                            net.minecraft.util.EnumHand hand) {
        return kind == Kind.WIRE ? getDefaultState()
                : getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return kind == Kind.WIRE ? getDefaultState()
                : getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return kind == Kind.WIRE ? 0 : state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return box;
    }

    /** Wire hangs overhead — nothing about it should stop a train or a player. */
    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return kind == Kind.WIRE ? NULL_AABB : box;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT;
    }
}
