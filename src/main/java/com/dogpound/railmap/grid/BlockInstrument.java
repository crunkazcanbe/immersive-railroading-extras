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
        LAMP("lamp", "Status Lamp", "Green live · red dead · flashing amber tripped"),
        BUTTON("button", "Control Button", "Does what one of the machine's own panel buttons does - wire it to the machine");

        public final String id, label, tip;
        Inst(String id, String label, String tip) { this.id = id; this.label = label; this.tip = tip; }
        public boolean analogue() { return this != SCREEN && this != LAMP && this != BUTTON; }
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
        tip.add("§cNeeds 12 V DC: lay 12 V DC Power Wire to it from a 12 V supply");
        if (inst == Inst.BUTTON) {
            tip.add("§7Right-click it with Instrument Wire, and land the same colour on a machine");
            tip.add("§7Screwdriver: pick which of the machine's buttons it copies (sneak = back)");
            tip.add("§7Mounts in a Gauge Panel hole too - four little buttons on one block");
        } else tip.add("§8Signal Wrench: right-click a grid machine or cable, then this");
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new TileInstrument(); }

    @Override
    public boolean onBlockActivated(World w, BlockPos pos, IBlockState s, EntityPlayer p, EnumHand hand, EnumFacing f, float hx, float hy, float hz) {
        if (w.isRemote) {                                                       // the cap goes in under your finger
            if (inst == Inst.BUTTON && p.getHeldItem(hand).isEmpty() && !p.isSneaking() && w.getTileEntity(pos) instanceof TileInstrument t0) t0.pressedAt = w.getTotalWorldTime();
            return true;
        }
        if (!(w.getTileEntity(pos) instanceof TileInstrument t)) return true;
        net.minecraft.item.ItemStack h = p.getHeldItem(hand);
        if (h.getItem() instanceof ItemScrewdriver && t.burnt) { p.sendStatusMessage(new TextComponentString(t.repair()), true); return true; }
        if (com.dogpound.railmap.wire.ItemWire.isPower(h)) {
            p.sendStatusMessage(new TextComponentString("§7Lay the 12 V power wire on the floor under it (or beside it) - it plugs itself in. Supply: " + com.dogpound.railmap.wire.WireNet.power(w, pos).what), true);
            return true;
        }
        if (h.getItem() instanceof com.dogpound.railmap.wire.ItemWire) {
            t.setWire(h.getMetadata() & 15);
            p.sendStatusMessage(new TextComponentString("§a" + com.dogpound.railmap.wire.WireNet.NAMES[t.wire] + " wire run through the hole in the bottom of the " + inst.label), true);
            return true;
        }
        if (h.isEmpty() && p.isSneaking() && t.wire >= 0) { t.setWire(-1); p.sendStatusMessage(new TextComponentString("§7Wire pulled out of the " + inst.label), true); return true; }
        if (inst == Inst.BUTTON && h.getItem() instanceof ItemScrewdriver) {
            p.sendStatusMessage(new TextComponentString(t.cycle(p.isSneaking() ? -1 : 1)), true);
            return true;
        }
        if (inst == Inst.BUTTON && h.isEmpty() && !p.isSneaking()) {
            w.playSound(null, pos, com.dogpound.railmap.ModSounds.CTL_PUSH, net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1.0f);
            t.pressedAt = w.getTotalWorldTime();
            p.sendStatusMessage(new TextComponentString(t.press(p.getName() + " (wall button)")), true);
            return true;
        }
        if (!h.isEmpty()) return false;
        t.resetPeak();
        String in = t.wire >= 0 ? com.dogpound.railmap.wire.WireNet.NAMES[t.wire] + " wire" : t.target() != null ? "wrench link" : "not connected - wire it or use the Signal Wrench";
        p.sendStatusMessage(new TextComponentString("§7" + inst.label + " · " + in + " · " + t.r.line2 + " · " + t.r.status + (t.r.alarm ? " §c§lALARM" : "")), true);
        return true;
    }

    /** a burnt-out instrument keeps smoking a little */
    @Override
    public void randomDisplayTick(IBlockState s, World w, BlockPos p, java.util.Random rnd) {
        if (!(w.getTileEntity(p) instanceof TileInstrument t) || !t.burnt || rnd.nextInt(3) != 0) return;
        EnumFacing f = s.getValue(FACING);
        double x = p.getX() + 0.5 + f.getXOffset() * -0.25 + (rnd.nextDouble() - 0.5) * 0.5, z = p.getZ() + 0.5 + f.getZOffset() * -0.25 + (rnd.nextDouble() - 0.5) * 0.5;
        w.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL, x, p.getY() + 0.85, z, 0, 0.02, 0);
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

    @Override @SuppressWarnings("deprecation") public boolean canProvidePower(IBlockState s) { return true; }
    @Override @SuppressWarnings("deprecation")
    public int getWeakPower(IBlockState s, IBlockAccess w, BlockPos p, EnumFacing side) { return w.getTileEntity(p) instanceof TileInstrument t && t.alarmOut() ? 15 : 0; }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
}
