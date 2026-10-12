package com.dogpound.railmap.item;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.freight.Contract;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.List;

/**
 * A freight waybill, printed by the Freight Office when a contract is accepted: what goes where, how much, by when,
 * and what it pays. Hold it to see the job; the cars it bills and the delivery are tracked by the railway itself.
 */
public class ItemWaybill extends Item {
    public ItemWaybill() {
        setRegistryName(RailMap.MODID, "waybill");
        setTranslationKey(RailMap.MODID + ".waybill");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    public static ItemStack make(Item item, Contract k, long now) {
        ItemStack s = new ItemStack(item);
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", k.id);
        t.setString("goods", k.qty + " " + k.itemName);
        t.setString("from", k.fromName);
        t.setString("to", k.toName);
        t.setString("pay", Contract.money(k.pay));
        t.setLong("deadline", k.deadline);
        t.setString("by", k.byName);
        t.setInteger("dist", k.distance);
        t.setLong("printed", now);
        s.setTagCompound(t);
        return s;
    }

    public static int id(ItemStack s) { return s.hasTagCompound() ? s.getTagCompound().getInteger("id") : 0; }

    @Override
    public String getItemStackDisplayName(ItemStack s) {
        return s.hasTagCompound() ? "Waybill #" + id(s) + " - " + s.getTagCompound().getString("goods") : "Blank Waybill";
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        if (!s.hasTagCompound()) { tip.add("§7Printed by a Freight Office when you accept a contract"); return; }
        NBTTagCompound t = s.getTagCompound();
        tip.add("§7From §f" + t.getString("from"));
        tip.add("§7To   §f" + t.getString("to") + " §8(" + t.getInteger("dist") + " blocks)");
        tip.add("§7Pays §6" + t.getString("pay") + " coins§7 on time, less when late");
        if (w != null) {
            long left = t.getLong("deadline") - w.getTotalWorldTime();
            tip.add(left >= 0 ? "§7Due in §f" + (left / 1000) + " h" : "§cLATE by " + (-left / 1000) + " h");
        }
        tip.add("§7Hauler: §f" + t.getString("by"));
        tip.add("§8Load it at the origin's loading silo (the cars get billed), unload it at the destination's pit");
    }
}
