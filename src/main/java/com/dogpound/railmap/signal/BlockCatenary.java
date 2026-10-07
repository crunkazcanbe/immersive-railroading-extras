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
    /** mast: another mast above / below (cap and base plate only at the ends of a column) */
    public static final PropertyBool UP = PropertyBool.create("up");
    public static final PropertyBool DOWN = PropertyBool.create("down");
    /** wire: a cantilever arm comes in from that side (draws the steady arm + registration) */
    public static final PropertyBool ARM_N = PropertyBool.create("arm_n");
    public static final PropertyBool ARM_S = PropertyBool.create("arm_s");
    public static final PropertyBool ARM_E = PropertyBool.create("arm_e");
    public static final PropertyBool ARM_W = PropertyBool.create("arm_w");
    /** wire: a portal girder overhead (draws drop hangers) / cantilever: a mast at its back (draws the hinge) */
    public static final PropertyBool HUNG = PropertyBool.create("hung");

    public enum Kind {
        MAST("catenary_mast", "Catenary Mast", 0.5f, 1.0f),
        PORTAL("catenary_portal", "Portal Girder", 1.0f, 1.0f),
        WIRE("catenary_wire", "Contact Wire", 1.0f, 1.0f),
        CANTILEVER("catenary_cantilever", "Cantilever Arm", 1.0f, 1.0f);

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
                   .withProperty(EAST, false).withProperty(WEST, false)
                   .withProperty(ARM_N, false).withProperty(ARM_S, false).withProperty(ARM_E, false)
                   .withProperty(ARM_W, false).withProperty(HUNG, false);
        } else {
            st = st.withProperty(FACING, EnumFacing.NORTH);
            if (kind == Kind.MAST) st = st.withProperty(UP, false).withProperty(DOWN, false);
            if (kind == Kind.CANTILEVER) st = st.withProperty(HUNG, false);
        }
        setDefaultState(st);
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        switch (building) {
            case WIRE: return new BlockStateContainer(this, NORTH, SOUTH, EAST, WEST, ARM_N, ARM_S, ARM_E, ARM_W, HUNG);
            case MAST: return new BlockStateContainer(this, FACING, UP, DOWN);
            case CANTILEVER: return new BlockStateContainer(this, FACING, HUNG);
            default: return new BlockStateContainer(this, FACING);
        }
    }

    /**
     * Real overhead line: masts stack into a column (base plate at the bottom, cap on top), cantilever arms chain
     * out from a mast over the track, and the contact wire runs on to the next wire block, picking up the steady arm
     * of any cantilever beside it and drop hangers from a portal girder above it.
     */
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        switch (kind) {
            case WIRE:
                return state.withProperty(NORTH, is(world, pos.north(), Kind.WIRE))
                        .withProperty(SOUTH, is(world, pos.south(), Kind.WIRE))
                        .withProperty(EAST, is(world, pos.east(), Kind.WIRE))
                        .withProperty(WEST, is(world, pos.west(), Kind.WIRE))
                        .withProperty(ARM_N, is(world, pos.north(), Kind.CANTILEVER))
                        .withProperty(ARM_S, is(world, pos.south(), Kind.CANTILEVER))
                        .withProperty(ARM_E, is(world, pos.east(), Kind.CANTILEVER))
                        .withProperty(ARM_W, is(world, pos.west(), Kind.CANTILEVER))
                        .withProperty(HUNG, is(world, pos.up(), Kind.PORTAL));
            case MAST:
                return state.withProperty(UP, is(world, pos.up(), Kind.MAST))
                        .withProperty(DOWN, is(world, pos.down(), Kind.MAST));
            case CANTILEVER: {
                BlockPos back = pos.offset(state.getValue(FACING).getOpposite());
                return state.withProperty(HUNG, is(world, back, Kind.MAST) || is(world, back.up(), Kind.MAST));
            }
            default:
                return state;
        }
    }

    private static boolean is(IBlockAccess world, BlockPos pos, Kind k) {
        net.minecraft.block.Block b = world.getBlockState(pos).getBlock();
        return b instanceof BlockCatenary && ((BlockCatenary) b).kind == k;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer,
                                            net.minecraft.util.EnumHand hand) {
        if (kind == Kind.WIRE) return getDefaultState();
        // mast + cantilever face the way you look (towards the track); a girder lies across your view
        return getDefaultState().withProperty(FACING, kind == Kind.PORTAL ? placer.getHorizontalFacing().rotateY()
                : placer.getHorizontalFacing());
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
        return kind == Kind.WIRE || kind == Kind.CANTILEVER || kind == Kind.PORTAL ? NULL_AABB : box;
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
