package com.dogpound.railmap.factory.conveyor;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.ore.FactoryTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * Belt Conveyor in three speeds. It runs away from you when placed and works out its own shape from its neighbours:
 * straight, a curve when a belt feeds it from one side only, a ramp up to a belt one block higher, a ramp down to one
 * a block lower. Aimed at a machine's hatch (or any inventory) it runs flush into it. Sneak + empty hand reverses it.
 */
public class BlockConveyor extends Block implements ITileEntityProvider {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyEnum<Shape> SHAPE = PropertyEnum.create("shape", Shape.class);
    private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 6 / 16.0, 1);
    private static final AxisAlignedBB RAMP = new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);

    public enum Shape implements IStringSerializable {
        STRAIGHT, LEFT, RIGHT, UP, DOWN;
        @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public enum Tier {
        BASIC("conveyor", 0.05f, "§7Belt speed 1 m/s"),
        FAST("conveyor_fast", 0.1f, "§cFast belt: 2 m/s"),
        EXPRESS("conveyor_express", 0.15f, "§bExpress belt: 3 m/s");
        public final String id; public final float speed; public final String tip;
        Tier(String id, float speed, String tip) { this.id = id; this.speed = speed; this.tip = tip; }
    }

    public final Tier tier;

    public BlockConveyor(Tier tier) {
        super(Material.IRON);
        this.tier = tier;
        setRegistryName(Factory.MODID, tier.id);
        setTranslationKey(Factory.MODID + "." + tier.id);
        setHardness(2f); setSoundType(SoundType.METAL);
        setCreativeTab(FactoryTab.INSTANCE);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(SHAPE, Shape.STRAIGHT));
    }

    // ---- shape from the neighbours -----------------------------------------------------------------------------

    static boolean isBelt(IBlockAccess w, BlockPos p) { return w.getBlockState(p).getBlock() instanceof BlockConveyor; }
    static EnumFacing facingAt(IBlockAccess w, BlockPos p) { return w.getBlockState(p).getValue(FACING); }

    /**
     * UP / DOWN / STRAIGHT only (no look at its sides, so no recursion). A ramp is always the LOWER of two belts a step
     * apart: UP climbs to a belt one higher in front, DOWN takes the items a belt one higher behind it drops on it.
     */
    static Shape slope(IBlockAccess w, BlockPos p, EnumFacing f) {
        BlockPos front = p.offset(f), back = p.offset(f.getOpposite());
        if (!isBelt(w, front) && isBelt(w, front.up()) && facingAt(w, front.up()) == f) return Shape.UP;
        if (!isBelt(w, p.up()) && isBelt(w, back.up()) && facingAt(w, back.up()) == f && !(isBelt(w, back) && facingAt(w, back) == f)) return Shape.DOWN;
        return Shape.STRAIGHT;
    }

    /** the block the belt at p delivers into */
    public static BlockPos target(IBlockAccess w, BlockPos p, EnumFacing f) {
        BlockPos front = p.offset(f);
        if (slope(w, p, f) == Shape.UP) return front.up();
        if (!isBelt(w, front) && isBelt(w, front.down()) && facingAt(w, front.down()) == f && slope(w, front.down(), f) == Shape.DOWN) return front.down();
        return front;
    }

    /** does a belt at q deliver into p */
    static boolean feeds(IBlockAccess w, BlockPos q, BlockPos p) {
        return isBelt(w, q) && target(w, q, facingAt(w, q)).equals(p);
    }

    public static Shape shapeOf(IBlockAccess w, BlockPos p, EnumFacing f) {
        Shape s = slope(w, p, f);
        if (s != Shape.STRAIGHT) return s;
        BlockPos back = p.offset(f.getOpposite());
        if (feeds(w, back, p) || feeds(w, back.up(), p) || feeds(w, back.down(), p)) return Shape.STRAIGHT;
        boolean l = feeds(w, p.offset(f.rotateYCCW()), p), r = feeds(w, p.offset(f.rotateY()), p);
        return l == r ? Shape.STRAIGHT : l ? Shape.LEFT : Shape.RIGHT;
    }

    @Override @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState s, IBlockAccess w, BlockPos p) {
        return s.withProperty(SHAPE, shapeOf(w, p, s.getValue(FACING)));
    }

    // ---- block plumbing ---------------------------------------------------------------------------------------

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, SHAPE); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(m & 3)); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing f, float hx, float hy, float hz, int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
    }
    @Override @SuppressWarnings("deprecation") public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        return slope(w, p, s.getValue(FACING)) == Shape.STRAIGHT ? BOX : RAMP;
    }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override public TileEntity createNewTileEntity(World w, int meta) { return new TileConveyor(); }

    @Override
    public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer pl, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        ItemStack h = pl.getHeldItem(hand);
        if (h.getItem() instanceof com.dogpound.railmap.wire.ItemWire && w.getTileEntity(p) instanceof TileConveyor c) {   // land a control wire on its terminal
            if (!w.isRemote) pl.sendStatusMessage(new net.minecraft.util.text.TextComponentString(com.dogpound.railmap.wire.WireNet.landWire(c, com.dogpound.railmap.wire.ItemWire.colour(h))), true);
            return true;
        }
        if (h.isEmpty() && !pl.isSneaking() && w.getTileEntity(p) instanceof TileConveyor c) {
            if (!w.isRemote) pl.sendStatusMessage(new net.minecraft.util.text.TextComponentString(c.statusLine()), true);
            return true;
        }
        if (!pl.isSneaking() || !h.isEmpty()) return false;
        if (!w.isRemote) w.setBlockState(p, s.withProperty(FACING, s.getValue(FACING).getOpposite()), 3);
        return true;
    }

    @Override public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add(tier.tip + "§7, 4 items per block");
        t.add("§7Curves, ramps up/down and side-merges on its own");
        t.add("§7Runs flush into machine hatches; redstone stops it");
        t.add("§eNeeds a drive: an Electric Motor (480 V) with its shaft end against a belt runs the whole line");
        t.add("§7Lay Instrument Wire beside it: a terminal pops up - buttons, gauges and computers control the line");
        t.add("§8Sneak + empty hand: reverse");
    }
    @Override public void breakBlock(World w, BlockPos p, IBlockState s) {
        if (w.getTileEntity(p) instanceof TileConveyor c) c.dropAll();
        super.breakBlock(w, p, s);
    }
}
