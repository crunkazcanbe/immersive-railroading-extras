package com.dogpound.railmap.wire;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.grid.ItemScrewdriver;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Wiring up railway gear without touching each block's own right-click: Instrument Wire lands a colour on its control
 * terminal (12 V power wire: says what supply it sees), a screwdriver puts new electronics in burnt-out gear.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class GearEvents {
    private GearEvents() {}

    @SubscribeEvent
    public static void click(PlayerInteractEvent.RightClickBlock e) {
        TileEntity t = e.getWorld().getTileEntity(e.getPos());
        if (t instanceof com.dogpound.railmap.controls.IControlled m && !e.getEntityPlayer().isSneaking()
                && !(e.getItemStack().getItem() instanceof ItemWire) && !(e.getItemStack().getItem() instanceof ItemScrewdriver)) {
            com.dogpound.railmap.grid.Panel.C c = com.dogpound.railmap.controls.IControlled.aimed(m, e.getEntityPlayer());
            if (c != null && m instanceof com.dogpound.railmap.block.TileSignalBox box && e.getItemStack().getItem() instanceof com.dogpound.railmap.item.ItemSignalWrench) {
                e.setCanceled(true);
                e.setCancellationResult(EnumActionResult.SUCCESS);
                if (!e.getWorld().isRemote) {
                    net.minecraft.util.math.BlockPos pick = com.dogpound.railmap.item.ItemSignalWrench.getPick(e.getItemStack());
                    e.getEntityPlayer().sendStatusMessage(new TextComponentString(pick == null ? "§7Pick a signal, switch track, track circuit or signal box with the wrench first" : box.link(c.id, pick)), true);
                }
                return;
            }
            if (c != null && !(e.getItemStack().getItem() instanceof com.dogpound.railmap.item.ItemSignalWrench)) {                                                     // a real control on the model: work it
                e.setCanceled(true);
                e.setCancellationResult(EnumActionResult.SUCCESS);
                if (!e.getWorld().isRemote) {
                    String r = com.dogpound.railmap.controls.IControlled.operate(m, c.id, e.getEntityPlayer());
                    if (r != null && !r.isEmpty()) e.getEntityPlayer().sendStatusMessage(new TextComponentString(r), true);
                }
                return;
            }
        }
        if (!(t instanceof IWiredGear g)) return;
        ItemStack h = e.getItemStack();
        String say;
        if (h.getItem() instanceof ItemWire) {
            int c = ItemWire.colour(h);
            say = c == WireNet.POWER ? "§7" + g.wiredName() + " 12 V supply: " + g.term().status() + " §7- lay the 12 V power wire on the floor beside it, it plugs itself in"
                    : WireNet.landWire(g, c);
        } else if (h.getItem() instanceof ItemScrewdriver && g.term().burnt) {
            say = g.term().repair(g.wiredName());
            g.gearChanged();
        } else return;
        e.setCanceled(true);
        e.setCancellationResult(EnumActionResult.SUCCESS);
        if (!e.getWorld().isRemote) e.getEntityPlayer().sendStatusMessage(new TextComponentString(say), true);
    }
}
