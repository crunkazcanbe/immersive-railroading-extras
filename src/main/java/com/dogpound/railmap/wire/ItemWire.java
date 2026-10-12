package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** Instrument Wire in 16 colours: lay it on the floor, lay more colours alongside, run it into a Gauge Panel hole. */
public class ItemWire extends Item {
    public ItemWire() {
        setRegistryName(RailMap.MODID, "instrument_wire");
        setTranslationKey(RailMap.MODID + ".instrument_wire");
        setHasSubtypes(true);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    /** the circuit this stack lays: 0-15 a signal colour, 16 the 12 V DC power pair */
    public static int colour(ItemStack s) { return s.getMetadata() == WireNet.POWER ? WireNet.POWER : s.getMetadata() & 15; }
    public static boolean isPower(ItemStack s) { return s.getItem() instanceof ItemWire && s.getMetadata() == WireNet.POWER; }

    @Override public String getItemStackDisplayName(ItemStack s) { return isPower(s) ? "12 V DC Power Wire" : WireNet.NAMES[s.getMetadata() & 15] + " Instrument Wire"; }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> l) {
        if (isInCreativeTab(tab)) for (int c = 0; c <= WireNet.POWER; c++) l.add(new ItemStack(this, 1, c));
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        ItemStack s = p.getHeldItem(hand);
        Block wire = com.dogpound.railmap.proxy.CommonProxy.instrumentWireBlock;
        BlockPos at = w.getBlockState(pos).getBlock().isReplaceable(w, pos) ? pos : pos.offset(side);
        if (!w.getBlockState(at).getBlock().isReplaceable(w, at) || !w.isSideSolid(at.down(), EnumFacing.UP) || !p.canPlayerEdit(at, side, s)) return EnumActionResult.FAIL;
        if (w.isRemote) return EnumActionResult.SUCCESS;
        w.setBlockState(at, wire.getDefaultState(), 3);
        if (w.getTileEntity(at) instanceof TileWire t) t.add(colour(s));
        w.playSound(null, at, SoundEvents.BLOCK_CLOTH_PLACE, SoundCategory.BLOCKS, 0.6f, 1.4f);
        if (!p.capabilities.isCreativeMode) s.shrink(1);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        if (isPower(s)) {
            t.add("§c12 V DC power pair (red + black) for meters, screens, buttons, gauge panels and radios");
            t.add("§7Lay it from a 12 V DC supply (a DC-DC converter set to 12 V, a battery...) to touch them");
            t.add("§cThe wrong voltage burns their electronics out - fix them with a screwdriver");
            return;
        }
        t.add("§7Thin signal wire - each colour is its own circuit");
        t.add("§7Right-click a wire run to lay this colour alongside");
        t.add("§7Right-click a Gauge Panel hole / Grid Tap to wire it to this colour");
    }
}
