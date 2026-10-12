package com.dogpound.railmap.factory.block;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.ore.FactoryTab;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.List;

/** Pride Pipe, Gate Valve and Flow Meter: steel water mains that join onto any mod's pipes, tanks and machines. */
public class BlockPipe extends Block implements ITileEntityProvider {
    public enum Kind {
        PIPE("pipe", "§7Carries any fluid, 250 mB/t; joins onto any mod's pipes and tanks"),
        VALVE("gate_valve", "§7Right-click the handwheel to open / shut; a redstone signal shuts it"),
        METER("flow_meter", "§7Shows the flow through it; comparator reads the flow");
        public final String id, tip;
        Kind(String id, String tip) { this.id = id; this.tip = tip; }
    }

    public static final PropertyBool[] CONN = new PropertyBool[6];
    public static final PropertyBool OPEN = PropertyBool.create("open");
    static { for (EnumFacing f : EnumFacing.values()) CONN[f.ordinal()] = PropertyBool.create(f.getName()); }

    public final Kind kind;

    public BlockPipe(Kind kind) {
        super(Material.IRON);
        this.kind = kind;
        setRegistryName(Factory.MODID, kind.id);
        setTranslationKey(Factory.MODID + "." + kind.id);
        setHardness(2.5f); setSoundType(SoundType.METAL);
        setCreativeTab(FactoryTab.INSTANCE);
        IBlockState d = blockState.getBaseState().withProperty(OPEN, true);
        for (PropertyBool p : CONN) d = d.withProperty(p, false);
        setDefaultState(d);
    }

    @Override protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, CONN[0], CONN[1], CONN[2], CONN[3], CONN[4], CONN[5], OPEN);
    }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState(); }
    @Override public int getMetaFromState(IBlockState s) { return 0; }

    static boolean connects(IBlockAccess w, BlockPos p, EnumFacing f) {
        TileEntity t = w.getTileEntity(p.offset(f));
        return t != null && (t instanceof TilePipe || t.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, f.getOpposite()));
    }

    @Override @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState s, IBlockAccess w, BlockPos p) {
        for (EnumFacing f : EnumFacing.values()) s = s.withProperty(CONN[f.ordinal()], connects(w, p, f));
        return s.withProperty(OPEN, !(w.getTileEntity(p) instanceof TilePipe t) || t.open);
    }

    @Override @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        double lo = kind == Kind.PIPE ? 5 / 16.0 : kind == Kind.VALVE ? 4 / 16.0 : 3 / 16.0, hi = 1 - lo;
        AxisAlignedBB b = new AxisAlignedBB(lo, lo, lo, hi, kind == Kind.VALVE ? 1.0 : hi, hi);
        for (EnumFacing f : EnumFacing.values()) {
            if (!connects(w, p, f)) continue;
            b = b.union(new AxisAlignedBB(
                    f == EnumFacing.WEST ? 0 : 5 / 16.0, f == EnumFacing.DOWN ? 0 : 5 / 16.0, f == EnumFacing.NORTH ? 0 : 5 / 16.0,
                    f == EnumFacing.EAST ? 1 : 11 / 16.0, f == EnumFacing.UP ? 1 : 11 / 16.0, f == EnumFacing.SOUTH ? 1 : 11 / 16.0));
        }
        return b;
    }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override public TileEntity createNewTileEntity(World w, int meta) { return new TilePipe(); }

    @Override
    public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer pl, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(p) instanceof TilePipe t)) return false;
        if (kind == Kind.PIPE && !pl.getHeldItem(hand).isEmpty()) return false;
        if (w.isRemote) return true;
        String fl = t.tank.getFluid() == null ? "empty" : t.tank.getFluidAmount() + " mB " + t.tank.getFluid().getLocalizedName();
        switch (kind) {
            case VALVE:
                t.open = !t.open; t.sync();
                w.playSound(null, p, com.dogpound.railmap.sound.PackSounds.ev("machine.clunk"), SoundCategory.BLOCKS, 0.7f, t.open ? 1.05f : 0.9f);
                pl.sendStatusMessage(new TextComponentString(t.open ? "§bGate valve OPEN" + (w.isBlockPowered(p) ? " §c(but its actuator is held shut by redstone)" : "") : "§7Gate valve SHUT"), true);
                break;
            case METER:
                pl.sendStatusMessage(new TextComponentString(String.format("§bFlow %d mB/t (%d L/s) · total %,d L · %s", t.flow, t.flow * 20, t.total, fl)), true);
                break;
            default:
                pl.sendStatusMessage(new TextComponentString("§7Pipe: " + fl), true);
        }
        return true;
    }

    @Override @SuppressWarnings("deprecation") public boolean hasComparatorInputOverride(IBlockState s) { return kind == Kind.METER; }
    @Override @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(IBlockState s, World w, BlockPos p) {
        return w.getTileEntity(p) instanceof TilePipe t ? Math.min(15, (t.flow * 15 + TilePipe.RATE - 1) / TilePipe.RATE) : 0;
    }

    @Override public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) { t.add(kind.tip); }
}
