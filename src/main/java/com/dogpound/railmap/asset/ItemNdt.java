package com.dogpound.railmap.asset;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;

/**
 * The inspection instruments (her 48-next list #27), one item with five kinds: ultrasonic rail tester, ultrasonic axle
 * tester, thermal imager, insulation tester and structural monitor. Right-click the asset (a rail, a machine, a
 * structure board, a lining block); the axle tester and the imager also work on the vehicle nearest you when you
 * right-click the air. A test takes a few seconds (a cooldown), like the real thing.
 */
public class ItemNdt extends Item {
    public ItemNdt() {
        setRegistryName(RailMap.MODID, "ndt_tool");
        setTranslationKey(RailMap.MODID + ".ndt_tool");
        setHasSubtypes(true);
        setMaxStackSize(1);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    public static Ndt.Tool of(ItemStack s) { Ndt.Tool[] t = Ndt.Tool.values(); return t[Math.min(t.length - 1, Math.max(0, s.getMetadata()))]; }

    @Override public String getItemStackDisplayName(ItemStack s) { return of(s).label; }
    @Override public void getSubItems(CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> l) { if (isInCreativeTab(tab)) for (Ndt.Tool t : Ndt.Tool.values()) l.add(new ItemStack(this, 1, t.ordinal())); }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        Ndt.Tool t = of(s);
        tip.add("§7" + t.tip);
        tip.add(String.format(java.util.Locale.ROOT, "§8Coverage %.0f%% · 50%% detection at %.1f · uncertainty ±%.0f%%", t.coverage * 100, t.a50, t.unc * 100));
        tip.add("§7Tests: " + Ndt.targets(t));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer p, World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, EnumHand hand) {
        ItemStack st = p.getHeldItem(hand);
        Ndt.Tool t = of(st);
        if (t == Ndt.Tool.AXLE) return EnumActionResult.PASS;                  // vehicles: onItemRightClick
        if (w.isRemote) return EnumActionResult.SUCCESS;
        if (p.getCooldownTracker().hasCooldown(this)) return EnumActionResult.SUCCESS;
        List<String> out;
        try { out = Ndt.inspect(w, t, pos, p); } catch (LinkageError | RuntimeException e) { out = java.util.Collections.singletonList("§c" + t.label + ": " + e); }
        if (out.size() == 1 && out.get(0).startsWith("§7" + t.label + ": nothing") && t == Ndt.Tool.THERMAL) {
            EntityRollingStock s = Ndt.nearVehicle(w, p, 6);
            if (s != null) out = Ndt.inspectVehicle(w, t, s, p);
        }
        for (String l : out) p.sendMessage(new TextComponentString(l));
        done(p, w, pos);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        ItemStack st = p.getHeldItem(hand);
        Ndt.Tool t = of(st);
        if (t != Ndt.Tool.AXLE && t != Ndt.Tool.THERMAL) return new ActionResult<>(EnumActionResult.PASS, st);
        if (w.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, st);
        if (p.getCooldownTracker().hasCooldown(this)) return new ActionResult<>(EnumActionResult.SUCCESS, st);
        EntityRollingStock s = Ndt.nearVehicle(w, p, 6);
        if (s == null) { p.sendStatusMessage(new TextComponentString("§7No vehicle within 6 blocks to test"), true); return new ActionResult<>(EnumActionResult.SUCCESS, st); }
        try { for (String l : Ndt.inspectVehicle(w, t, s, p)) p.sendMessage(new TextComponentString(l)); }
        catch (LinkageError | RuntimeException e) { p.sendMessage(new TextComponentString("§c" + t.label + ": " + e)); }
        done(p, w, p.getPosition());
        return new ActionResult<>(EnumActionResult.SUCCESS, st);
    }

    private void done(EntityPlayer p, World w, BlockPos pos) {
        p.getCooldownTracker().setCooldown(this, 60);                           // a test takes ~3 s
        w.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.PLAYERS, 0.3f, 1.0f);
    }
}
