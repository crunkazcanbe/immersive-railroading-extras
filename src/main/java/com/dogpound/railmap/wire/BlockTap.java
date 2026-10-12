package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.Reading;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** Grid Tap: clip it onto any grid machine or cable, right-click it with a wire colour to pick its circuit. */
public class BlockTap extends Block {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public BlockTap() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "grid_tap");
        setTranslationKey(RailMap.MODID + ".grid_tap");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(0.8f); setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(m & 7)); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing side, float hx, float hy, float hz, int meta, EntityLivingBase pl, EnumHand hand) {
        return getDefaultState().withProperty(FACING, side);
    }
    @Override public boolean hasTileEntity(IBlockState s) { return true; }
    @Override public TileEntity createTileEntity(World w, IBlockState s) { return new TileTap(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.INVISIBLE; }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }

    @Override @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        double a = 4 / 16.0, b = 12 / 16.0, d = 4 / 16.0;
        switch (s.getValue(FACING)) {
            case UP: return new AxisAlignedBB(a, 0, a, b, d, b);
            case DOWN: return new AxisAlignedBB(a, 1 - d, a, b, 1, b);
            case NORTH: return new AxisAlignedBB(a, a, 1 - d, b, b, 1);
            case SOUTH: return new AxisAlignedBB(a, a, 0, b, b, d);
            case WEST: return new AxisAlignedBB(1 - d, a, a, 1, b, b);
            default: return new AxisAlignedBB(0, a, a, d, b, b);
        }
    }

    @Override
    public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer pl, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(p) instanceof TileTap t)) return false;
        if (w.isRemote) return true;
        ItemStack h = pl.getHeldItem(hand);
        if (h.getItem() instanceof ItemWire) {
            t.setChannel(h.getMetadata());
            pl.sendStatusMessage(new TextComponentString("§aTap now on the " + WireNet.NAMES[t.channel] + " circuit"), true);
            return true;
        }
        Reading r = new Reading();
        r.read(w, t.target(), BlockInstrument.Inst.SCREEN);
        pl.sendStatusMessage(new TextComponentString("§7Grid Tap · " + WireNet.NAMES[t.channel] + " circuit · " + r.line1 + " · " + r.line2 + " · " + r.status), true);
        return true;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7Clip onto any grid machine or cable: puts its volts, amps, kW... on a wire");
        t.add("§7Right-click it with Instrument Wire to choose the colour (circuit)");
    }
}
