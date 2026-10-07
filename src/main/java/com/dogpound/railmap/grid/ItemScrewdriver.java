package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * Electrician's screwdriver (requested feature): right-click a grid machine to open its settings (the covers come off);
 * sneak + right-click a transformer to move its off-circuit tap link (±2.5 % a step) - only safe DEAD, like the real
 * thing: try it live and you get zapped.
 */
public class ItemScrewdriver extends Item {
    static final DamageSource ZAP = new DamageSource("irextras.zap").setDamageBypassesArmor();

    public ItemScrewdriver() {
        setRegistryName(RailMap.MODID, "screwdriver");
        setTranslationKey(RailMap.MODID + ".screwdriver");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Right-click a grid machine: open its settings");
        tip.add("§7Sneak + right-click a transformer: off-circuit tap link (de-energise it first!)");
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer p, World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, EnumHand hand) {
        if (!(w.getTileEntity(pos) instanceof TileGrid t)) return EnumActionResult.PASS;
        GridKind k = t.kind();
        if (!p.isSneaking()) {
            if (w.isRemote) RailMap.NETWORK.sendToServer(com.dogpound.railmap.network.PacketSettings.request(pos));
            return EnumActionResult.SUCCESS;
        }
        if (w.isRemote) return EnumActionResult.SUCCESS;
        if (k != GridKind.TRANSFORMER && k != GridKind.TRACTION) {
            p.sendStatusMessage(new TextComponentString("§7Nothing to adjust with a screwdriver here - right-click (not sneaking) for its settings"), true);
            return EnumActionResult.SUCCESS;
        }
        GridData d = GridData.get(w);
        GridData.Node n = d.node(pos);
        GridData.Net net = d.netOf(pos);
        boolean live = net != null && net.problem.isEmpty();
        if (!live && n.upNet >= 0 && n.upNet < d.nets().size()) live = d.nets().get(n.upNet).problem.isEmpty();
        if (live) {
            p.attackEntityFrom(ZAP, 6f);
            p.sendMessage(new TextComponentString("§c⚡ ZAP! It's LIVE. Off-circuit taps are only moved dead: open its breakers, earth it, then try again"));
            return EnumActionResult.SUCCESS;
        }
        n.octap = n.octap >= 2 ? -2 : n.octap + 1;
        d.touch();
        p.sendStatusMessage(new TextComponentString(String.format(Locale.ROOT, "§eOff-circuit tap link: position %d (%+.1f%% output)", n.octap + 3, n.octap * 2.5)), true);
        return EnumActionResult.SUCCESS;
    }
}
