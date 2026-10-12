package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/** a hotel room key card (batch 6c #13), cut at reception for one room lock + its serial; dead after check-out time */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public class ItemRoomKey extends Item {
    public static Item INSTANCE;

    public ItemRoomKey() {
        setMaxStackSize(1);
        setRegistryName(new ResourceLocation(RailMap.MODID, "room_key"));
        setTranslationKey(RailMap.MODID + ".room_key");
        setCreativeTab(RailMapTab.INSTANCE);
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> e) { e.getRegistry().register(INSTANCE = new ItemRoomKey()); }

    public static ItemStack cut(BlockPos lock, int room, int serial, long until, String guest) {
        ItemStack s = new ItemStack(INSTANCE);
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("lock", lock.toLong()); t.setInteger("room", room); t.setInteger("serial", serial); t.setLong("until", until); t.setString("guest", guest);
        s.setTagCompound(t);
        s.setStackDisplayName("§fRoom " + room + " key card");
        return s;
    }

    /** does this stack open that lock now? */
    public static boolean opens(ItemStack s, BlockPos lock, int serial, long now) {
        if (s.getItem() != INSTANCE || !s.hasTagCompound()) return false;
        NBTTagCompound t = s.getTagCompound();
        return t.getLong("lock") == lock.toLong() && t.getInteger("serial") == serial && now < t.getLong("until");
    }

    public static boolean holds(net.minecraft.entity.player.EntityPlayer p, BlockPos lock, int serial, long now) {
        for (ItemStack s : p.inventory.mainInventory) if (opens(s, lock, serial, now)) return true;
        return opens(p.getHeldItemOffhand(), lock, serial, now);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        if (!s.hasTagCompound()) { tip.add("§7Blank key card - cut at a hotel reception"); return; }
        NBTTagCompound t = s.getTagCompound();
        tip.add("§7Room " + t.getInteger("room") + " · guest " + t.getString("guest"));
        if (w != null) {
            long left = t.getLong("until") - w.getTotalWorldTime();
            tip.add(left > 0 ? "§aValid " + (left / 24000) + " d " + (left % 24000) / 1000 + " h more" : "§cExpired - check-out time passed");
        }
    }
}
