package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

/**
 * Instrument Wire (thin, up to 16 colours side by side in one block, each its own circuit) and Bundled Cable (all 16
 * colours in one sheath). Laid on the floor; joins up with wires sharing a colour, Grid Taps, Gauge Panels and radios.
 */
public class BlockWire extends Block {
    public final boolean bundled;
    private static final AxisAlignedBB THIN = new AxisAlignedBB(0, 0, 0, 1, 1 / 16.0, 1), FAT = new AxisAlignedBB(0.25, 0, 0.25, 0.75, 3 / 16.0, 0.75);

    public BlockWire(boolean bundled) {
        super(Material.CIRCUITS);
        this.bundled = bundled;
        String id = bundled ? "bundled_cable" : "instrument_wire";
        setRegistryName(RailMap.MODID, id);
        setTranslationKey(RailMap.MODID + "." + id);
        if (bundled) setCreativeTab(RailMapTab.INSTANCE);
        setHardness(0.1f); setSoundType(SoundType.CLOTH);
    }

    @Override public boolean hasTileEntity(IBlockState s) { return true; }
    @Override public TileEntity createTileEntity(World w, IBlockState s) { return new TileWire(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState s) { return EnumBlockRenderType.INVISIBLE; }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public boolean isFullCube(IBlockState s) { return false; }
    @Override @SuppressWarnings("deprecation") public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return bundled ? FAT : THIN; }
    @Override @SuppressWarnings("deprecation") public AxisAlignedBB getCollisionBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return NULL_AABB; }
    @Override public boolean canPlaceBlockAt(World w, BlockPos p) { return super.canPlaceBlockAt(w, p) && w.isSideSolid(p.down(), EnumFacing.UP); }

    @Override @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState s, World w, BlockPos p, Block b, BlockPos from) {
        if (!w.isRemote && !w.isSideSolid(p.down(), EnumFacing.UP)) { dropWires(w, p); w.setBlockToAir(p); }
        else if (w.getTileEntity(p) instanceof TileWire t) t.sync();
    }

    @Override
    public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer pl, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(p) instanceof TileWire t)) return false;
        ItemStack h = pl.getHeldItem(hand);
        if (!bundled && h.getItem() instanceof ItemWire) {                   // lay another colour alongside
            if (w.isRemote) return true;
            int c = ItemWire.colour(h);
            if (t.add(c)) {
                if (!pl.capabilities.isCreativeMode) h.shrink(1);
                pl.sendStatusMessage(new TextComponentString("§7" + WireNet.name(c) + " wire laid alongside (" + t.count() + " in this run)"), true);
            } else pl.sendStatusMessage(new TextComponentString("§7There's already a " + WireNet.name(c) + " wire here"), true);
            return true;
        }
        if (!bundled && h.isEmpty() && pl.isSneaking()) {                     // pull the last one out
            if (w.isRemote) return true;
            int c = t.removeLast();
            if (c >= 0) spawn(w, p, new ItemStack(com.dogpound.railmap.proxy.CommonProxy.instrumentWire, 1, c));
            if (t.rawMask() == 0) w.setBlockToAir(p);
            return true;
        }
        if (h.isEmpty()) {
            if (w.isRemote) return true;
            StringBuilder sb = new StringBuilder(bundled ? "§7Bundled Cable: all 16 colours" : "§7Wires here:");
            if (!bundled) for (int c = 0; c <= WireNet.POWER; c++) if (t.has(c)) sb.append(' ').append(WireNet.name(c));
            pl.sendStatusMessage(new TextComponentString(sb.toString()), true);
            return true;
        }
        return false;
    }

    private void dropWires(World w, BlockPos p) {
        if (bundled || !(w.getTileEntity(p) instanceof TileWire t)) return;
        for (int c = 0; c <= WireNet.POWER; c++) if ((t.rawMask() >> c & 1) != 0) spawn(w, p, new ItemStack(com.dogpound.railmap.proxy.CommonProxy.instrumentWire, 1, c));
    }

    private static void spawn(World w, BlockPos p, ItemStack s) { w.spawnEntity(new EntityItem(w, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, s)); }

    @Override
    public void breakBlock(World w, BlockPos p, IBlockState s) {
        if (!w.isRemote) dropWires(w, p);
        super.breakBlock(w, p, s);
    }
    @Override public int quantityDropped(Random r) { return bundled ? 1 : 0; }

    @Override
    public ItemStack getPickBlock(IBlockState s, RayTraceResult t, World w, BlockPos p, EntityPlayer pl) {
        if (bundled) return new ItemStack(this);
        int c = w.getTileEntity(p) instanceof TileWire tw ? Integer.numberOfTrailingZeros(Math.max(1, tw.rawMask())) : 0;
        return new ItemStack(com.dogpound.railmap.proxy.CommonProxy.instrumentWire, 1, c);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7All 16 instrument circuits in one sheath");
        t.add("§7Joins any colour of Instrument Wire, Taps, Gauge Panels, radios");
    }
}
