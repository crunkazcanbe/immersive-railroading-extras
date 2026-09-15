package com.dogpound.railmap.item;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.signal.TileRelayCase;
import com.dogpound.railmap.signal.TileSignalBridge;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * The linking tool. Right-click a signal to pick it up, then right-click a relay case to wire
 * the two together — the same signal again unlinks it. Right-click nothing to drop the
 * selection.
 * <p>
 * The wrench remembers its pick in its own NBT, so two players can each be wiring a different
 * interlocking without treading on each other.
 */
public class ItemSignalWrench extends Item {
    private static final String TAG_POS = "jz_pick";

    public ItemSignalWrench() {
        setRegistryName(RailMap.MODID, "signal_wrench");
        setTranslationKey(RailMap.MODID + ".signal_wrench");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing facing,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        // onItemUseFirst, not onItemUse: signals, relay cases and ticket machines all have their own
        // right-click, which runs before onItemUse and swallowed every wrench click (it cycled the
        // signal's mode instead of picking it up).
        TileEntity peek = world.getTileEntity(pos);
        // Sneak-right-click anything resizable: open the size slider (client), nothing else happens.
        if (player.isSneaking() && peek instanceof com.dogpound.railmap.signal.IScalable sc) {
            if (world.isRemote) {
                RailMap.proxy.openScaleGui(pos, world.getBlockState(pos).getBlock().getLocalizedName(), sc.scale());
            }
            return EnumActionResult.SUCCESS;
        }
        boolean ours = peek instanceof TileSignalMast || peek instanceof TileSignalBridge || peek instanceof TileRelayCase
                || peek instanceof com.dogpound.railmap.block.TileTicketMachine
                || (peek == null && world.getTileEntity(pos.down()) instanceof com.dogpound.railmap.block.TileTicketMachine);
        if (!ours) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        ItemStack stack = player.getHeldItem(hand);
        TileEntity te = world.getTileEntity(pos);

        if (te instanceof TileSignalMast || te instanceof TileSignalBridge) {
            setPick(stack, pos);
            say(player, "Signal picked up — now right-click a relay case to wire it in");
            return EnumActionResult.SUCCESS;
        }
        if (te instanceof com.dogpound.railmap.block.TileTicketMachine
                || (te == null && world.getTileEntity(pos.down()) instanceof com.dogpound.railmap.block.TileTicketMachine)) {
            com.dogpound.railmap.block.TileTicketMachine machine = te instanceof com.dogpound.railmap.block.TileTicketMachine m
                    ? m : (com.dogpound.railmap.block.TileTicketMachine) world.getTileEntity(pos.down());
            BlockPos pick = getPick(stack);
            say(player, pick == null ? "Pick a platform signal first, then click the ticket machine" : machine.toggleLink(pick));
            return EnumActionResult.SUCCESS;
        }
        if (te instanceof TileRelayCase relay) {
            BlockPos pick = getPick(stack);
            if (pick == null) {
                say(player, relay.statusLine() + " — pick a signal first");
            } else {
                say(player, relay.toggleLink(pick));
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Override
    public net.minecraft.util.ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && getPick(stack) != null) {
            setPick(stack, null);
            say(player, "Selection cleared");
        }
        return new net.minecraft.util.ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, ITooltipFlag flag) {
        tip.add("§7Right-click a signal, then a relay case, to wire them together.");
        tip.add("§8Same pair again unlinks · right-click air to clear");
        tip.add("§8Sneak-right-click a signal, crossing or lineside sign: resize it");
        BlockPos p = getPick(stack);
        if (p != null) {
            tip.add("§dHolding: " + p.getX() + ", " + p.getY() + ", " + p.getZ());
        }
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return getPick(stack) != null;   // glints while it is carrying a selection
    }

    // ---- the pick, in the stack's own tag ------------------------------------------------

    private static void setPick(ItemStack stack, BlockPos pos) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        if (pos == null) { tag.removeTag(TAG_POS); } else { tag.setLong(TAG_POS, pos.toLong()); }
        stack.setTagCompound(tag);
    }

    private static BlockPos getPick(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_POS) ? BlockPos.fromLong(tag.getLong(TAG_POS)) : null;
    }

    private static void say(EntityPlayer player, String msg) {
        player.sendStatusMessage(new TextComponentString(msg), true);
    }
}
