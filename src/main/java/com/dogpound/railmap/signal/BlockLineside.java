package com.dogpound.railmap.signal;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * The small stuff along the right-of-way. No logic, no tile entity — these are the details
 * that make a line read as a real railroad instead of two rails in a field.
 */
public class BlockLineside extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public enum Kind {
        WHISTLE_POST("whistle_post", "Whistle Post", "The W board: sound the horn for the crossing ahead.", 0.45f, 1.0f),
        MILEPOST("milepost", "Milepost", "Distance along the line.", 0.35f, 0.75f),
        SPEED_SIGN("speed_sign", "Speed Limit Sign", "Maximum authorised speed from here.", 0.5f, 1.0f),
        SWITCH_STAND("switch_stand", "Switch Stand", "Target and lamp beside the points.", 0.5f, 0.7f),
        DERAIL("derail", "Derail", "Yellow wedge that puts a runaway on the ground.", 1.0f, 0.3f),
        BUMPER("bumper", "Bumper Post", "End of track. Stop here.", 1.0f, 0.7f),
        CROSSBUCK("crossbuck", "Crossbuck", "RAILROAD CROSSING — the plain, unlit sign.", 0.4f, 1.0f);

        public final String id;
        public final String label;
        public final String tip;
        /** Collision footprint, blocks wide. */
        public final float width;
        /** Height in blocks. */
        public final float height;

        Kind(String id, String label, String tip, float width, float height) {
            this.id = id;
            this.label = label;
            this.tip = tip;
            this.width = width;
            this.height = height;
        }
    }

    private final Kind kind;
    private final AxisAlignedBB box;

    public BlockLineside(Kind kind) {
        super(Material.WOOD);
        this.kind = kind;
        double half = kind.width / 2;
        this.box = new AxisAlignedBB(0.5 - half, 0, 0.5 - half, 0.5 + half, kind.height, 0.5 + half);
        setRegistryName(RailMap.MODID, kind.id);
        setTranslationKey(RailMap.MODID + "." + kind.id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.2f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
        tip.add("§7" + kind.tip);
        if (kind == Kind.SPEED_SIGN) {
            tip.add("§8Right-click: raise the limit · Sneak: lower it");
            tip.add("§8Driverless trains obey it; train protection enforces it");
        }
    }

    // ---- the speed sign carries a number; everything else is scenery -----------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return kind == Kind.SPEED_SIGN;
    }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(World world, IBlockState state) {
        return kind == Kind.SPEED_SIGN ? new TileSpeedSign() : null;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, net.minecraft.entity.player.EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (kind != Kind.SPEED_SIGN) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileSpeedSign sign) {
            sign.step(player.isSneaking());
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(
                    "Speed limit " + sign.mph() + " mph"), true);
        }
        return true;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return box;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
