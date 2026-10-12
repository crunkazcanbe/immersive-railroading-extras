package com.dogpound.railmap.travel6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.economy.Coins;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The passenger's pocket (batch 6b): damage 0 = Pride Card (contactless stored-value card; blank until a Top-Up
 * Machine registers it), 1 = Pride Rail phone (right-click: the app - journey planner, live departures, wallet,
 * my trains, alerts; travel6.client.GuiPhone), 2 = baggage claim tag, 3 = parking ticket, 4 = suitcase (a checked
 * bag; once collected at a reclaim carousel, right-click to unpack it).
 */
public class ItemTravel extends Item {
    public static final String[] VARIANTS = {"pride_card", "rail_phone", "bag_tag", "park_ticket", "suitcase"};
    public static final int CARD = 0, PHONE = 1, TAG = 2, PARK = 3, SUITCASE = 4;

    public ItemTravel() {
        setRegistryName(RailMap.MODID, "travel");
        setTranslationKey(RailMap.MODID + ".travel");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    public static boolean is(ItemStack s, int kind) { return !s.isEmpty() && s.getItem() instanceof ItemTravel && s.getMetadata() == kind; }

    static NBTTagCompound tag(ItemStack s) { if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound()); return s.getTagCompound(); }
    static int intOf(ItemStack s, String k) { return s.hasTagCompound() ? s.getTagCompound().getInteger(k) : 0; }

    @Override public int getItemStackLimit(ItemStack s) { return s.getMetadata() == CARD && cardId(s) == 0 ? 16 : 1; }
    @Override public String getTranslationKey(ItemStack s) { return "item." + RailMap.MODID + "." + VARIANTS[Math.min(VARIANTS.length - 1, Math.max(0, s.getMetadata()))]; }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        items.add(new ItemStack(this, 1, CARD));
        items.add(new ItemStack(this, 1, PHONE));
    }

    // ---- Pride Card ------------------------------------------------------------------------------------------------------
    public static int cardId(ItemStack s) { return is(s, CARD) ? intOf(s, "card") : 0; }

    public static ItemStack card(TravelData.Card c) {
        ItemStack s = new ItemStack(TravelRegistry.item, 1, CARD);
        tag(s).setInteger("card", c.id);
        sync(s, c);
        return s;
    }

    /** refresh what the card's chip shows (its tooltip) from the record */
    public static void sync(ItemStack s, TravelData.Card c) {
        if (c == null || s.isEmpty()) return;
        NBTTagCompound t = tag(s);
        t.setLong("bal", c.bal); t.setString("own", c.ownerName); t.setString("in", c.in() ? c.inName : ""); t.setBoolean("bl", c.blocked);
    }

    // ---- claim tags, parking tickets, suitcases --------------------------------------------------------------------------
    public static int bagId(ItemStack s) { return (is(s, TAG) || is(s, SUITCASE)) ? intOf(s, "bag") : 0; }
    public static int parkId(ItemStack s) { return is(s, PARK) ? intOf(s, "park") : 0; }

    public static ItemStack claimTag(TravelData.Bag b) {
        ItemStack s = new ItemStack(TravelRegistry.item, 1, TAG);
        NBTTagCompound t = tag(s);
        t.setInteger("bag", b.id); t.setInteger("tag", b.tag); t.setString("to", b.toName); t.setString("from", b.fromName);
        return s;
    }

    public static ItemStack suitcase(TravelData.Bag b) {
        ItemStack s = new ItemStack(TravelRegistry.item, 1, SUITCASE);
        NBTTagCompound t = tag(s);
        t.setInteger("bag", b.id); t.setInteger("tag", b.tag); t.setString("to", b.toName); t.setString("from", b.fromName);
        return s;
    }

    /** a collected bag: the contents go into the suitcase itself */
    public static ItemStack packed(TravelData.Bag b) {
        ItemStack s = suitcase(b);
        NBTTagList l = new NBTTagList();
        for (ItemStack c : b.items) l.appendTag(c.writeToNBT(new NBTTagCompound()));
        tag(s).setTag("items", l);
        tag(s).setInteger("bag", 0);
        return s;
    }

    public static ItemStack parkTicket(TravelData.Park p, String garage) {
        ItemStack s = new ItemStack(TravelRegistry.item, 1, PARK);
        NBTTagCompound t = tag(s);
        t.setInteger("park", p.id); t.setLong("in", p.in); t.setString("g", garage);
        return s;
    }

    // ---- use -------------------------------------------------------------------------------------------------------------
    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (w.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, s);   // the phone opens in TravelClient
        if (s.getMetadata() == SUITCASE) {
            if (!s.hasTagCompound() || !s.getTagCompound().hasKey("items")) {
                p.sendStatusMessage(new TextComponentString("§7This bag is still in the baggage system - collect it at the reclaim carousel with its claim tag"), true);
                return new ActionResult<>(EnumActionResult.FAIL, s);
            }
            NBTTagList l = s.getTagCompound().getTagList("items", 10);
            for (int i = 0; i < l.tagCount(); i++) { ItemStack c = new ItemStack(l.getCompoundTagAt(i)); if (!c.isEmpty() && !p.inventory.addItemStackToInventory(c)) p.dropItem(c, false); }
            w.playSound(null, p.getPosition(), net.minecraft.init.SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.PLAYERS, 0.7f, 1.3f);
            p.sendStatusMessage(new TextComponentString("§aBag unpacked"), true);
            return new ActionResult<>(EnumActionResult.SUCCESS, ItemStack.EMPTY);
        }
        if (s.getMetadata() == CARD && cardId(s) != 0) {
            TravelData.Card c = TravelData.get(w).cards.get(cardId(s));
            if (c != null) { sync(s, c); p.sendStatusMessage(new TextComponentString("§bPride Card " + c.id + " §7balance §f" + Coins.fmt(c.bal) + (c.in() ? " §7· on a journey from §f" + c.inName : "") + (c.blocked ? " §c· BLOCKED" : "")), true); }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        NBTTagCompound t = s.getTagCompound();
        switch (s.getMetadata()) {
            case CARD -> {
                if (cardId(s) == 0) { tip.add("§7Blank card - register it at a Top-Up Machine (deposit " + Coins.fmt(TravelConfig.cardDeposit) + ")"); return; }
                tip.add("§bNo. " + cardId(s) + " §7· " + t.getString("own"));
                tip.add("§7Balance §f" + Coins.fmt(t.getLong("bal")) + (t.getBoolean("bl") ? " §c· BLOCKED" : ""));
                if (!t.getString("in").isEmpty()) tip.add("§eOn a journey from " + t.getString("in") + " - tap out at the end");
                tip.add("§8Tap in and out at fare gates and card readers · daily fare cap · top up at a Top-Up Machine");
            }
            case PHONE -> { tip.add("§7Right-click: the Pride Rail app"); tip.add("§8Journey planner · live departures · wallet · my trains · alerts"); }
            case TAG -> { if (t != null) { tip.add("§fClaim tag " + t.getInteger("tag")); tip.add("§7" + t.getString("from") + " > " + t.getString("to")); } tip.add("§8Show it at the reclaim carousel to get your bag back"); }
            case PARK -> { if (t != null) tip.add("§fParking ticket #" + t.getInteger("park") + " §7· " + t.getString("g")); tip.add("§8Pay at the pay station, then show it at the exit barrier"); }
            case SUITCASE -> {
                if (t != null && t.hasKey("items")) tip.add("§7" + t.getTagList("items", 10).tagCount() + " stack(s) inside · right-click to unpack");
                else if (t != null) tip.add("§7Tag " + t.getInteger("tag") + " · " + t.getString("from") + " > " + t.getString("to"));
            }
            default -> { }
        }
    }

    /** contents of a packed suitcase (selftest) */
    public static List<ItemStack> contents(ItemStack s) {
        List<ItemStack> o = new ArrayList<>();
        if (!s.hasTagCompound()) return o;
        NBTTagList l = s.getTagCompound().getTagList("items", 10);
        for (int i = 0; i < l.tagCount(); i++) { ItemStack c = new ItemStack(l.getCompoundTagAt(i)); if (!c.isEmpty()) o.add(c); }
        return o;
    }
}
