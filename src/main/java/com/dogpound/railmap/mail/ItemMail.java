package com.dogpound.railmap.mail;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Mail (batch 5 #8). Damage values: 0 = writing paper + envelope (right-click to write a letter: To and the message),
 * 1 = a written letter (right-click reads it; post it at a Sorting Office), 2 = a parcel (items wrapped at the Sorting
 * Office's parcel tray; the addressee right-clicks to unwrap it), 3 = a sealed mail bag (only ever seen on the Sorting
 * Office's bag shelves - the letters travel in their records). The letter screen is mail.client.MailClient.
 */
public class ItemMail extends Item {
    public static final String[] VARIANTS = {"mail_paper", "mail_letter", "mail_parcel", "mail_bag"};
    public static final int PAPER = 0, LETTER = 1, PARCEL = 2, BAG = 3;

    public ItemMail() {
        setRegistryName(RailMap.MODID, "mail");
        setTranslationKey(RailMap.MODID + ".mail");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    public static boolean is(ItemStack s, int kind) { return !s.isEmpty() && s.getItem() instanceof ItemMail && s.getMetadata() == kind; }
    public static boolean isMail(ItemStack s) { return is(s, LETTER) || is(s, PARCEL); }

    @Override public int getItemStackLimit(ItemStack s) { return s.getMetadata() == PAPER ? 16 : 1; }
    @Override public String getTranslationKey(ItemStack s) { return "item." + RailMap.MODID + "." + VARIANTS[Math.min(3, Math.max(0, s.getMetadata()))]; }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (isInCreativeTab(tab)) items.add(new ItemStack(this, 1, PAPER));
    }

    // ---- the letter's fields ---------------------------------------------------------------------------------------------
    static NBTTagCompound tag(ItemStack s) { if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound()); return s.getTagCompound(); }
    public static String to(ItemStack s) { return s.hasTagCompound() ? s.getTagCompound().getString("to") : ""; }
    public static String from(ItemStack s) { return s.hasTagCompound() ? s.getTagCompound().getString("from") : ""; }
    public static String postmark(ItemStack s) { return s.hasTagCompound() ? s.getTagCompound().getString("pm") : ""; }
    public static int mailId(ItemStack s) { return s.hasTagCompound() ? s.getTagCompound().getInteger("mid") : 0; }

    public static List<String> body(ItemStack s) {
        List<String> o = new ArrayList<>();
        if (!s.hasTagCompound()) return o;
        NBTTagList l = s.getTagCompound().getTagList("body", 8);
        for (int i = 0; i < l.tagCount(); i++) o.add(l.getStringTagAt(i));
        return o;
    }

    /** writing paper -> a written letter (server, from PacketMail / the command) */
    public static ItemStack write(String from, String to, List<String> lines) {
        ItemStack l = new ItemStack(MailRegistry.mail, 1, LETTER);
        NBTTagCompound t = tag(l);
        t.setString("from", cut(from, 32));
        t.setString("to", cut(to.trim(), 48));
        NBTTagList b = new NBTTagList();
        for (int i = 0; i < Math.min(8, lines.size()); i++) b.appendTag(new NBTTagString(cut(lines.get(i), 60)));
        t.setTag("body", b);
        return l;
    }

    public static ItemStack parcel(String from, String to, List<ItemStack> contents) {
        ItemStack p = new ItemStack(MailRegistry.mail, 1, PARCEL);
        NBTTagCompound t = tag(p);
        t.setString("from", cut(from, 32));
        t.setString("to", cut(to.trim(), 48));
        NBTTagList l = new NBTTagList();
        for (ItemStack c : contents) if (!c.isEmpty()) l.appendTag(c.writeToNBT(new NBTTagCompound()));
        t.setTag("items", l);
        return p;
    }

    public static List<ItemStack> contents(ItemStack p) {
        List<ItemStack> o = new ArrayList<>();
        if (!p.hasTagCompound()) return o;
        NBTTagList l = p.getTagCompound().getTagList("items", 10);
        for (int i = 0; i < l.tagCount(); i++) { ItemStack c = new ItemStack(l.getCompoundTagAt(i)); if (!c.isEmpty()) o.add(c); }
        return o;
    }

    public static int weightG(ItemStack s) {
        if (is(s, PARCEL)) { int n = 0; for (ItemStack c : contents(s)) n += c.getCount(); return 250 + n * 120; }
        return 20 + body(s).size() * 2;
    }

    public static ItemStack bag(MailData.Bag b) {
        ItemStack s = new ItemStack(MailRegistry.mail, 1, BAG);
        NBTTagCompound t = tag(s);
        t.setInteger("bag", b.id); t.setInteger("seal", b.seal); t.setString("to", b.toName); t.setString("from", b.fromName);
        return s;
    }

    public static int bagId(ItemStack s) { return is(s, BAG) && s.hasTagCompound() ? s.getTagCompound().getInteger("bag") : 0; }

    static String cut(String s, int n) { s = s == null ? "" : s.replaceAll("[§\\p{Cntrl}]", ""); return s.length() <= n ? s : s.substring(0, n); }

    // ---- use -------------------------------------------------------------------------------------------------------------
    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (w.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, s);   // the screens open in MailClient (client event)
        if (s.getMetadata() == PARCEL) {
            String to = to(s);
            boolean mine = to.isEmpty() || Mail.addressee(to).equalsIgnoreCase(p.getName()) || from(s).equalsIgnoreCase(p.getName());
            if (!postmark(s).isEmpty() && !mine && !p.capabilities.isCreativeMode) {
                p.sendStatusMessage(new TextComponentString("§7This parcel is addressed to " + to + " - only they can open it"), true);
                return new ActionResult<>(EnumActionResult.FAIL, s);
            }
            for (ItemStack c : contents(s)) if (!p.inventory.addItemStackToInventory(c)) p.dropItem(c, false);
            p.sendStatusMessage(new TextComponentString("§aParcel unwrapped" + (from(s).isEmpty() ? "" : " - from " + from(s))), true);
            return new ActionResult<>(EnumActionResult.SUCCESS, ItemStack.EMPTY);
        }
        if (s.getMetadata() == BAG) p.sendStatusMessage(new TextComponentString("§7Sealed mail bag " + (s.hasTagCompound() ? "#" + s.getTagCompound().getInteger("seal") + " for " + to(s) : "") + " - leave it on the Sorting Office shelf"), true);
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        switch (s.getMetadata()) {
            case PAPER -> { tip.add("§7Right-click to write a letter: who it's to, and your message"); tip.add("§8Address: a player (\"Alex\"), a player at a town (\"Alex, Riverside\") or a town's post office"); }
            case LETTER, PARCEL -> {
                tip.add("§7To: §f" + (to(s).isEmpty() ? "?" : to(s)) + (from(s).isEmpty() ? "" : " §7from §f" + from(s)));
                if (!postmark(s).isEmpty()) tip.add("§8Postmark: " + postmark(s));
                else tip.add("§8Post it at a Sorting Office (the POSTING BOX), then FRANK");
                if (s.getMetadata() == PARCEL) tip.add("§8" + contents(s).size() + " stack(s) inside, " + weightG(s) + " g · right-click to unwrap");
                else tip.add("§8Right-click to read");
            }
            default -> { if (s.hasTagCompound()) tip.add("§7Seal " + s.getTagCompound().getInteger("seal") + " · " + s.getTagCompound().getString("from") + " > " + to(s)); }
        }
    }
}
