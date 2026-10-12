package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
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
 * Wire Chase Wall (requested feature): a wall block with a cable chase inside. Every Instrument Wire colour and the 12 V DC pair run through it and
 * on to the chase blocks beside it; gauges, screens, buttons and gauge panels mounted on it take their wire straight in
 * at the back, and floor wire laid up to it goes in through a grommet. Looks like stone bricks, or any block you
 * right-click it with.
 */
public class BlockWireChase extends Block {
    public static final PropertyBool CAMO = PropertyBool.create("camo");

    public BlockWireChase() {
        super(Material.ROCK);
        setRegistryName(RailMap.MODID, "wire_chase");
        setTranslationKey(RailMap.MODID + ".wire_chase");
        setCreativeTab(RailMapTab.INSTANCE);
        setHardness(1.5f); setResistance(10f); setSoundType(SoundType.STONE);
        setDefaultState(blockState.getBaseState().withProperty(CAMO, false));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, CAMO); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(CAMO, (m & 1) == 1); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(CAMO) ? 1 : 0; }
    @Override public boolean hasTileEntity(IBlockState s) { return true; }
    @Override public TileEntity createTileEntity(World w, IBlockState s) { return new TileWireChase(); }
    /** disguised: the renderer draws the chosen block instead */
    @Override @SuppressWarnings("deprecation") public EnumBlockRenderType getRenderType(IBlockState s) { return s.getValue(CAMO) ? EnumBlockRenderType.INVISIBLE : EnumBlockRenderType.MODEL; }
    @Override @SuppressWarnings("deprecation") public boolean isOpaqueCube(IBlockState s) { return !s.getValue(CAMO); }

    @Override
    public boolean onBlockActivated(World w, BlockPos p, IBlockState s, EntityPlayer pl, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (!(w.getTileEntity(p) instanceof TileWireChase t)) return false;
        ItemStack h = pl.getHeldItem(hand);
        if (h.getItem() instanceof ItemWire) {                                // drill (or, sneaking, plug) a hole in the face you clicked
            if (w.isRemote) return true;
            boolean drill = !pl.isSneaking();
            if (t.hole(side) == drill) { pl.sendStatusMessage(new TextComponentString(drill ? "§7There's already a hole in this face - lay the wire up to it" : "§7No hole here to plug"), true); return true; }
            t.setHole(side, drill);
            w.playSound(null, p, drill ? SoundEvents.BLOCK_STONE_HIT : SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.8f, drill ? 1.6f : 1.0f);
            pl.sendStatusMessage(new TextComponentString(drill ? "§aDrilled a little hole through the " + side.getName() + " face - lay wire up to it and it runs inside the wall"
                    : "§7Plugged the hole in the " + side.getName() + " face"), true);
            return true;
        }
        if (h.getItem() instanceof ItemBlock ib && !(ib.getBlock() instanceof BlockWireChase) && !pl.isSneaking()) {
            if (w.isRemote) return true;
            t.camo = String.valueOf(ib.getBlock().getRegistryName()); t.camoMeta = ib.getMetadata(h.getMetadata());
            w.setBlockState(p, s.withProperty(CAMO, true), 3);
            t.sync();
            w.playSound(null, p, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
            pl.sendStatusMessage(new TextComponentString("§aWire chase now looks like " + h.getDisplayName() + " §7(sneak + empty hand to undo)"), true);
            return true;
        }
        if (h.isEmpty() && pl.isSneaking() && s.getValue(CAMO)) {
            if (w.isRemote) return true;
            t.camo = ""; w.setBlockState(p, s.withProperty(CAMO, false), 3); t.sync();
            pl.sendStatusMessage(new TextComponentString("§7Wire chase back to plain bricks"), true);
            return true;
        }
        if (h.isEmpty()) {
            if (w.isRemote) return true;
            pl.sendStatusMessage(new TextComponentString("§7Wire Chase Wall: every wire colour and the 12 V pair run hidden inside - mount gauges on it, lay wire up to it"), true);
            return true;
        }
        return false;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7A wall with a cable chase inside: all wire colours + 12 V run hidden through it");
        t.add("§7Gauges, screens, buttons and gauge panels on it are wired in at the back");
        t.add("§7Right-click a face with Instrument Wire to drill a little hole (sneak to plug it): wire goes in there");
        t.add("§7Right-click with any block to make it look like that block");
    }
}
