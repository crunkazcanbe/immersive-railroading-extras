package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.grid.BlockInstrument;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * Gauge Panel (requested feature).
 */
public class BlockGaugePanel extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final String[] HOLE = {"top-left", "top-right", "bottom-left", "bottom-right"};

    public BlockGaugePanel() {
        super(Material.IRON);
        setRegistryName(RailMap.MODID, "gauge_panel");
        setTranslationKey(RailMap.MODID + ".gauge_panel");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.5f); setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(m & 3)); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing side, float hx, float hy, float hz, int meta, EntityLivingBase pl, EnumHand hand) {
        return getDefaultState().withProperty(FACING, pl.getHorizontalFacing().getOpposite());
    }
    @Override public boolean hasTileEntity(IBlockState s) { return true; }
    @Override public TileEntity createTileEntity(World w, IBlockState s) { return new TileGaugePanel(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.INVISIBLE; }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }

    /** which hole (0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right) a click on the front lands in; -1 = not the front */
    static int hole(EnumFacing front, EnumFacing side, float hx, float hy, float hz) {
        if (side != front) return -1;
        double u = front == EnumFacing.NORTH ? 1 - hx : front == EnumFacing.SOUTH ? hx : front == EnumFacing.EAST ? 1 - hz : hz;
        return (hy >= 0.5 ? 0 : 2) + (u < 0.5 ? 0 : 1);
    }

    @Override
    public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer pl, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(p) instanceof TileGaugePanel t)) return false;
        ItemStack h = pl.getHeldItem(hand);
        int i = hole(s.getValue(FACING), side, hx, hy, hz);
        boolean button = i >= 0 && t.inst[i] == BlockInstrument.Inst.BUTTON.ordinal();
        if (w.isRemote) { if (button && h.isEmpty() && !pl.isSneaking()) t.pressedAt[i] = w.getTotalWorldTime(); return true; }
        String where = i >= 0 ? HOLE[i] + " hole" : "";
        if (t.burnt && h.getItem() instanceof com.dogpound.railmap.grid.ItemScrewdriver) { say(pl, t.repair()); return true; }
        if (ItemWire.isPower(h)) { say(pl, "§7Lay the 12 V power wire on the floor beside the panel - it plugs itself in. Supply: " + WireNet.power(w, p).what); return true; }
        if (button && h.getItem() instanceof com.dogpound.railmap.grid.ItemScrewdriver) { say(pl, t.cycle(i, pl.isSneaking() ? -1 : 1)); return true; }
        if (button && h.isEmpty() && !pl.isSneaking()) {
            w.playSound(null, p, com.dogpound.railmap.ModSounds.CTL_PUSH, SoundCategory.BLOCKS, 0.5f, 1.0f);
            t.pressedAt[i] = w.getTotalWorldTime();
            say(pl, t.press(i, pl.getName() + " (panel button)"));
            return true;
        }
        if (h.getItem() instanceof ItemWire) {
            if (i < 0) { say(pl, "§7Right-click one of the four holes on the front with the wire"); return true; }
            t.wire[i] = h.getMetadata() & 15; t.sync();
            w.playSound(null, p, SoundEvents.BLOCK_CLOTH_PLACE, SoundCategory.BLOCKS, 0.6f, 1.5f);
            say(pl, "§a" + WireNet.NAMES[t.wire[i]] + " wire run through the " + where + " - it comes in at the back; run it to a machine with that colour landed on it (or a Grid Tap)");
            return true;
        }
        if (h.getItem() instanceof ItemBlock ib && ib.getBlock() instanceof BlockInstrument bi) {
            if (i < 0) { say(pl, "§7Mount it on one of the four holes on the front"); return true; }
            if (t.inst[i] >= 0) { say(pl, "§eThe " + where + " already has a " + BlockInstrument.Inst.values()[t.inst[i]].label + " - sneak + empty hand takes it off"); return true; }
            t.inst[i] = bi.inst.ordinal(); t.sync();
            if (!pl.capabilities.isCreativeMode) h.shrink(1);
            w.playSound(null, p, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.7f, 1.3f);
            say(pl, "§a" + bi.inst.label + " mounted over the " + where + (t.wire[i] < 0 ? " §e(no wire in that hole yet)" : ""));
            return true;
        }
        if (h.getItem() instanceof ItemBlock ib && !pl.isSneaking() && !(ib.getBlock() instanceof BlockGaugePanel)) {
            t.camo = String.valueOf(ib.getBlock().getRegistryName()); t.camoMeta = ib.getMetadata(h.getMetadata()); t.sync();
            w.playSound(null, p, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
            say(pl, "§aPanel now looks like " + h.getDisplayName() + " §7(sneak + empty hand on its side to undo)");
            return true;
        }
        if (h.isEmpty() && pl.isSneaking()) {
            if (i >= 0 && t.inst[i] >= 0) {
                Block b = Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(RailMap.MODID, "grid_inst_" + BlockInstrument.Inst.values()[t.inst[i]].id));
                w.spawnEntity(new EntityItem(w, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, new ItemStack(b)));
                t.inst[i] = -1; t.sync(); say(pl, "§7Instrument taken off the " + where);
            } else if (i >= 0 && t.wire[i] >= 0) { t.wire[i] = -1; t.sync(); say(pl, "§7Wire pulled out of the " + where); }
            else if (!t.camo.isEmpty()) { t.camo = ""; t.sync(); say(pl, "§7Camouflage removed"); }
            return true;
        }
        if (h.isEmpty()) {
            if (i >= 0) {
                String what = t.inst[i] < 0 ? "empty" : BlockInstrument.Inst.values()[t.inst[i]].label + " · " + t.rd[i].line2 + " · " + t.rd[i].status;
                say(pl, "§7" + where + ": " + (t.wire[i] < 0 ? "no wire" : WireNet.NAMES[t.wire[i]] + " wire") + " · " + what);
            } else say(pl, "§7Gauge Panel: four holes on the front - wire them (Instrument Wire), mount gauges / screens, right-click with any block to disguise it");
            return true;
        }
        return false;
    }

    @Override
    public void randomDisplayTick(IBlockState s, World w, BlockPos p, java.util.Random rnd) {
        if (!(w.getTileEntity(p) instanceof TileGaugePanel t) || !t.burnt || rnd.nextInt(3) != 0) return;
        w.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_NORMAL, p.getX() + 0.2 + rnd.nextDouble() * 0.6, p.getY() + 1.0, p.getZ() + 0.2 + rnd.nextDouble() * 0.6, 0, 0.02, 0);
    }

    private static void say(EntityPlayer p, String s) { p.sendStatusMessage(new TextComponentString(s), true); }

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState s) {
        if (!w.isRemote && w.getTileEntity(p) instanceof TileGaugePanel t)
            for (int k : t.inst) if (k >= 0) {
                Block b = Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(RailMap.MODID, "grid_inst_" + BlockInstrument.Inst.values()[k].id));
                w.spawnEntity(new EntityItem(w, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, new ItemStack(b)));
            }
        super.breakBlock(w, p, s);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7Four holes: right-click one with Instrument Wire to run that colour in,");
        t.add("§7then with a meter / screen / lamp to mount it over the hole");
        t.add("§7Right-click with any block to make it look like that block");
        t.add("§cNeeds 12 V DC: lay 12 V DC Power Wire beside it from a 12 V supply");
    }
}
