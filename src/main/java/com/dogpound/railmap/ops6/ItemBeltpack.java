package com.dogpound.railmap.ops6;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import com.dogpound.railmap.track.Rails;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * RCO Remote Control Beltpack (batch 6 E #21): the yard operator's radio remote for one locomotive. Sneak + right-click
 * near a loco (looking at it) pairs it - the loco is then under remote control; right-click opens the beltpack (its
 * face: direction, speed selector, throttle notches, train + independent brake, horn, bell, sand, vigilance, E-stop).
 * Safety (Rco): dead-man vigilance, tilt switch (operator down / falling / in water), out of radio range, beltpack
 * dropped or the operator gone - every one is a penalty / emergency brake, alarmed and on the event recorder.
 */
public class ItemBeltpack extends Item {
    public ItemBeltpack() {
        setRegistryName(RailMap.MODID, "rco_beltpack");
        setTranslationKey(RailMap.MODID + ".rco_beltpack");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    public static UUID loco(ItemStack s) { NBTTagCompound t = s.getTagCompound(); return t != null && t.hasUniqueId("loco") ? t.getUniqueId("loco") : null; }
    public static String locoName(ItemStack s) { NBTTagCompound t = s.getTagCompound(); return t == null ? "" : t.getString("name"); }
    public static int serial(ItemStack s) {
        NBTTagCompound t = s.getTagCompound();
        if (t == null) { t = new NBTTagCompound(); s.setTagCompound(t); }
        if (!t.hasKey("serial")) t.setInteger("serial", 1000 + (int) (Math.random() * 8999));
        return t.getInteger("serial");
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack s = p.getHeldItem(hand);
        if (p.isSneaking()) {
            if (!w.isRemote) p.sendStatusMessage(new TextComponentString(pair(w, p, s)), true);
            return new ActionResult<>(EnumActionResult.SUCCESS, s);
        }
        if (w.isRemote) com.dogpound.railmap.ops6.client.Ops6Client.openBeltpack();
        return new ActionResult<>(EnumActionResult.SUCCESS, s);
    }

    /** pair with the loco the player is looking at (within 12 blocks), or unpair when looking at nothing */
    static String pair(World w, EntityPlayer p, ItemStack s) {
        Locomotive best = null;
        double bestScore = 0;
        Vec3d eye = p.getPositionEyes(1f), look = p.getLook(1f);
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(w).getEntities(Locomotive.class)) {
                if (l.isDead()) continue;
                cam72cam.mod.math.Vec3d lp = l.getPosition();
                Vec3d d = new Vec3d(lp.x - eye.x, lp.y + 1.5 - eye.y, lp.z - eye.z);
                double dist = d.length();
                if (dist > 12) continue;
                double score = d.normalize().dotProduct(look) - dist / 100;
                if (score > 0.8 && score > bestScore) { bestScore = score; best = l; }
            }
        } catch (LinkageError | RuntimeException e) { return "§cImmersive Railroading not ready"; }
        UUID was = loco(s);
        if (best == null) {
            if (was != null) { Rco.release(w, was, p, "unpaired"); s.getTagCompound().removeTag("loco"); s.getTagCompound().removeTag("locoMost"); s.getTagCompound().removeTag("locoLeast"); return "§eBeltpack unpaired"; }
            return "§7Look at a locomotive within 12 blocks and sneak + right-click to pair";
        }
        String other = Rco.owner(best.getUUID());
        if (other != null && !other.equals(p.getName())) return "§cThat loco is already under " + other + "'s beltpack";
        if (was != null && !was.equals(best.getUUID())) Rco.release(w, was, p, "paired to another loco");
        NBTTagCompound t = s.getTagCompound() == null ? new NBTTagCompound() : s.getTagCompound();
        t.setUniqueId("loco", best.getUUID());
        t.setString("name", Rails.name(best));
        s.setTagCompound(t);
        int sn = serial(s);
        Rco.paired(w, best, (EntityPlayerMP) p, sn);
        return "§aBeltpack " + sn + " paired to " + Rails.name(best) + " - right-click to open it";
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
        tip.add("§7Yard remote control for one locomotive (RCO)");
        tip.add(loco(s) == null ? "§8Not paired: look at a loco, sneak + right-click" : "§aPaired: " + locoName(s) + " §8(#" + serial(s) + ")");
        tip.add("§8Right-click: open the beltpack. Vigilance, tilt and range stops apply.");
    }
}
