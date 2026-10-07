package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * Control-room panel instruments (requested feature): switchboard meters with moving needles, a little readout screen
 * and a status lamp. Mount on any wall / panel (click the face you want it on), then link with the Signal Wrench:
 * right-click a grid machine or cable, then the instrument.
 */
public class BlockInstrument extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public enum Inst {
        VOLT("voltmeter", "Voltmeter", "Analogue switchboard voltmeter (V / kV)"),
        AMP("ammeter", "Ammeter", "Analogue ammeter - full scale follows the cable's rating"),
        WATT("wattmeter", "Wattmeter", "Real power P (kW / MW)"),
        VAR("varmeter", "Var Meter", "Reactive power Q (kvar)"),
        PF("pf_meter", "Power Factor Meter", "cos φ: 1.00 = all real power"),
        FREQ("hz_meter", "Frequency Meter", "Hz - sags a little under heavy load"),
        TEMP("thermometer", "Temperature Gauge", "Cable conductor / transformer winding temperature"),
        SCREEN("screen", "Readout Screen", "Little screen: voltage level, volts, amps, kW, PF, status"),
        LAMP("lamp", "Status Lamp", "Green live · red dead · flashing amber tripped");

        public final String id, label, tip;
        Inst(String id, String label, String tip) { this.id = id; this.label = label; this.tip = tip; }
        public boolean analogue() { return this != SCREEN && this != LAMP; }
    }

    public final Inst inst;

    public BlockInstrument(Inst inst) {
        super(Material.IRON);
        this.inst = inst;
        setRegistryName(RailMap.MODID, "grid_inst_" + inst.id);
        setTranslationKey(RailMap.MODID + ".grid_inst_" + inst.id);
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1f);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex(); }

    @Override
    public IBlockState getStateForPlacement(World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, int meta, EntityLivingBase placer, EnumHand hand) {
        // on a wall: face out from it; on a floor / ceiling: face the player
        EnumFacing f = side.getAxis().isHorizontal() ? side : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, f);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, ITooltipFlag flag) {
        tip.add("§7" + inst.tip);
        tip.add("§8Signal Wrench: right-click a grid machine or cable, then this");
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileInstrument(); }

    @Override
    public boolean onBlockActivated(World w, BlockPos pos, IBlockState s, EntityPlayer p, EnumHand hand, EnumFacing f, float hx, float hy, float hz) {
        if (w.isRemote) return true;
        if (w.getTileEntity(pos) instanceof TileInstrument t)
            p.sendStatusMessage(new TextComponentString("§7" + inst.label + " · " + (t.target() == null ? "not linked - use the Signal Wrench" : t.line2 + " · " + t.status)), true);
        return true;
    }

    // a plate on the wall behind it: 3 px deep
    @Override @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) {
        switch (s.getValue(FACING)) {
            case NORTH: return new AxisAlignedBB(0.0625, 0.0625, 0.8125, 0.9375, 0.9375, 1);
            case SOUTH: return new AxisAlignedBB(0.0625, 0.0625, 0, 0.9375, 0.9375, 0.1875);
            case WEST: return new AxisAlignedBB(0.8125, 0.0625, 0.0625, 1, 0.9375, 0.9375);
            default: return new AxisAlignedBB(0, 0.0625, 0.0625, 0.1875, 0.9375, 0.9375);
        }
    }

    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
}
