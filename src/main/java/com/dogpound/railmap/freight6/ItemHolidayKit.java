package com.dogpound.railmap.freight6;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * Train decoration kits (batch 6 #30): 0 Christmas lights (strings of coloured bulbs along the roof and ends, garland,
 * wreaths), 1 Halloween ghost train (orange / purple bulbs, jack-o'-lanterns, cobwebs and ghosts that circle it at
 * night). Use one in or beside an IR car to dress it; sneak-use takes the kit back off.
 */
public class ItemHolidayKit extends Item {
    public static final String[] VARIANTS = {"holiday_kit_xmas", "holiday_kit_ghost"};

    public ItemHolidayKit() {
        setRegistryName(RailMap.MODID, "holiday_kit");
        setTranslationKey(RailMap.MODID + ".holiday_kit");
        setHasSubtypes(true);
        setMaxStackSize(16);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    @Override public String getTranslationKey(ItemStack s) { return "item." + RailMap.MODID + "." + VARIANTS[Math.floorMod(s.getMetadata(), 2)]; }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) for (int i = 0; i < 2; i++) items.add(new ItemStack(this, 1, i));
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add(s.getMetadata() == 0 ? "§7Christmas lights for a train: bulbs along the roof and ends, garland, wreaths" : "§7Ghost train: orange + purple bulbs, jack-o'-lanterns, cobwebs, ghosts at night");
        tip.add("§8Use in / beside an Immersive Railroading car. Sneak-use takes a kit off");
        tip.add("§8In season (" + (s.getMetadata() == 0 ? "1 Dec - 6 Jan" : "15 Oct - 2 Nov") + ") it brings " + (s.getMetadata() == 0 ? "presents to station trees" : "treats to pumpkin displays"));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (w.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, s);
        String r = Holiday.apply(w, p, s, null);
        p.sendStatusMessage(new TextComponentString(r), true);
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    /** the car the player is in or beside (its axis within reach) */
    static EntityRollingStock aimed(World w, EntityPlayer p) {
        EntityRollingStock best = null;
        double bd = Double.MAX_VALUE;
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(w).getEntities(EntityRollingStock.class)) {
                double[] lo = com.dogpound.railmap.interior.Interior.toLocal(s, p.posX, p.posY + 1, p.posZ);
                double hl = s.getDefinition().getLength(s.gauge) / 2 + 1.5, hw = s.getDefinition().getWidth(s.gauge) / 2 + 2.5;
                if (Math.abs(lo[2]) > hl || Math.abs(lo[0]) > hw || lo[1] < -3 || lo[1] > 6) continue;
                double d = lo[0] * lo[0] + lo[2] * lo[2] * 0.1;
                if (d < bd) { bd = d; best = s; }
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return best;
    }
}
