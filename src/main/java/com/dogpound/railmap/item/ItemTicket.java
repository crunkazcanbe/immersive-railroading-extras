package com.dogpound.railmap.item;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.List;

/**
 * A paper train ticket, printed by a ticket machine. It carries its trip — from, to, round trip
 * or one way — and a serial number. Put it into the ticket machine at the station it is from
 * to call a train; the machine punches it. A round-trip ticket turns around after the first
 * trip and can be used once more from the destination.
 */
public class ItemTicket extends Item {
    private static final String FROM = "from", TO = "to", FROM_N = "fromName", TO_N = "toName",
            SERIAL = "serial", ROUND = "round", USED = "used", DAY = "day";

    public ItemTicket() {
        setRegistryName(RailMap.MODID, "ticket");
        setTranslationKey(RailMap.MODID + ".ticket");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
        // The model swaps to the punched ticket once it has been used.
        addPropertyOverride(new ResourceLocation(RailMap.MODID, "punched"),
                (stack, world, entity) -> isSpent(stack) ? 1f : 0f);
    }

    public static ItemStack make(Item item, long from, String fromName, long to, String toName, boolean round, int serial, long day) {
        ItemStack s = new ItemStack(item);
        NBTTagCompound t = new NBTTagCompound();
        t.setLong(FROM, from);
        t.setLong(TO, to);
        t.setString(FROM_N, fromName);
        t.setString(TO_N, toName);
        t.setBoolean(ROUND, round);
        t.setInteger(SERIAL, serial);
        t.setLong(DAY, day);
        s.setTagCompound(t);
        return s;
    }

    public static boolean valid(ItemStack s) {
        return s.getItem() instanceof ItemTicket && s.hasTagCompound() && s.getTagCompound().hasKey(FROM);
    }

    public static long from(ItemStack s) { return s.getTagCompound().getLong(FROM); }
    public static long to(ItemStack s) { return s.getTagCompound().getLong(TO); }
    public static String fromName(ItemStack s) { return s.getTagCompound().getString(FROM_N); }
    public static String toName(ItemStack s) { return s.getTagCompound().getString(TO_N); }

    /** Uses so far: 0 fresh, 1 = out-leg ridden, 2 = both legs of a round trip ridden. */
    public static int used(ItemStack s) {
        return s.hasTagCompound() ? s.getTagCompound().getInteger(USED) : 0;
    }

    public static boolean isSpent(ItemStack s) {
        if (!s.hasTagCompound()) return false;
        int u = used(s);
        return s.getTagCompound().getBoolean(ROUND) ? u >= 2 : u >= 1;
    }

    /** Punch it. A round trip swaps ends after the first use so it's good for the way back. */
    public static void punch(ItemStack s) {
        NBTTagCompound t = s.getTagCompound();
        int u = t.getInteger(USED) + 1;
        t.setInteger(USED, u);
        if (t.getBoolean(ROUND) && u == 1) {
            long f = t.getLong(FROM);
            String fn = t.getString(FROM_N);
            t.setLong(FROM, t.getLong(TO));
            t.setString(FROM_N, t.getString(TO_N));
            t.setLong(TO, f);
            t.setString(TO_N, fn);
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (!valid(stack)) return "Blank Ticket";
        return (isSpent(stack) ? "Used Ticket: " : "Ticket: ") + fromName(stack) + " → " + toName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tip, ITooltipFlag flag) {
        if (!valid(stack)) {
            tip.add("§7Buy one from a ticket machine at a station.");
            return;
        }
        NBTTagCompound t = stack.getTagCompound();
        boolean round = t.getBoolean(ROUND);
        tip.add("§f" + fromName(stack) + " §7→ §f" + toName(stack));
        tip.add("§7" + (round ? "Round trip" : "One way") + " · No. " + t.getInteger(SERIAL) + " · issued day " + t.getLong(DAY));
        if (isSpent(stack)) tip.add("§8Punched — no longer valid");
        else if (round && used(stack) == 1) tip.add("§aReturn half: use it at " + fromName(stack));
        else tip.add("§aPut it into the ticket machine at " + fromName(stack) + " to call a train");
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return valid(stack) && !isSpent(stack) && stack.getTagCompound().getBoolean(ROUND);
    }
}
