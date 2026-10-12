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

    /** HV work: live = ZAP; dead but no permit = refused. True when the job may go ahead */
    static boolean work(EntityPlayer p, World w, BlockPos pos) {
        if (Permits.designated(w, pos) && Permits.liveNow(w, pos)) {
            p.attackEntityFrom(ZAP, 8f);
            p.setFire(2);
            w.playSound(null, pos, com.dogpound.railmap.ModSounds.BREAKER, net.minecraft.util.SoundCategory.BLOCKS, 1f, 0.5f);
            History.log(w, pos, "INCIDENT", p.getName() + " was zapped working on it LIVE");
            p.sendMessage(new TextComponentString("§c§l⚡ ZAP! §r§cThat is LIVE high-voltage plant. Isolate it, earth it, prove it dead, lock it off and get a permit"));
            return false;
        }
        String why = Permits.mayWork(p, pos);
        if (!why.isEmpty()) { p.sendStatusMessage(new TextComponentString(why), true); return false; }
        return true;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer p, World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, EnumHand hand) {
        if (!(w.getTileEntity(pos) instanceof TileGrid t)) return EnumActionResult.PASS;
        if (t.isBurnt()) {                                                      // burnt out: the screwdriver fixes it
            if (!w.isRemote && work(p, w, pos)) p.sendStatusMessage(new TextComponentString(t.repair()), true);
            return EnumActionResult.SUCCESS;
        }
        GridKind k = t.kind();
        if (!p.isSneaking()) {
            if (w.isRemote) RailMap.NETWORK.sendToServer(com.dogpound.railmap.network.PacketSettings.request(pos));
            return EnumActionResult.SUCCESS;
        }
        if (w.isRemote) return EnumActionResult.SUCCESS;
        GridData d = GridData.get(w);
        GridData.Node n = d.node(pos);
        if ((k == GridKind.TRANSFORMER || k == GridKind.TRACTION) && n.fanFail) {       // LV auxiliary: no permit needed
            n.fanFail = false; n.fanHours = 0; d.markDirty();
            History.log(w, pos, "MAINT", "cooling fan motor replaced by " + p.getName() + " - ONAF rating back");
            p.sendStatusMessage(new TextComponentString("§aNew cooling fan motor fitted - ONAF rating restored"), true);
            return EnumActionResult.SUCCESS;
        }
        if (k == GridKind.UPS && Ups.health(n) < 0.8) {                          // 12 V: no permit needed
            n.st.put("bh", 1000); n.energy = (long) (Ups.capacity(n) * 0.5); n.st.put("tf", 0); d.markDirty();
            History.log(w, pos, "MAINT", "battery bank replaced by " + p.getName());
            p.sendStatusMessage(new TextComponentString("§aNew 12 V battery bank fitted (50 % charged) - it will finish charging on mains"), true);
            return EnumActionResult.SUCCESS;
        }
        if (k == GridKind.BREAKER) {                                            // overhaul: new contacts, mechanism serviced
            if (n.breaker == 0) { p.sendStatusMessage(new TextComponentString("§cIt's CLOSED - open it (and isolate it) before overhauling"), true); return EnumActionResult.SUCCESS; }
            if (!work(p, w, pos)) return EnumActionResult.SUCCESS;
            History.log(w, pos, "MAINT", String.format(Locale.ROOT, "OVERHAULED by %s: contacts renewed (were %.0f%% worn after %,d operations)", p.getName(), n.wear * 100, n.ops));
            n.wear = 0; Insulation.renew(n); d.markDirty();
            com.dogpound.railmap.alarm.AlarmManager.get(w).clear("wear:" + pos.toLong());
            p.sendStatusMessage(new TextComponentString("§aBreaker overhauled - new contacts, mechanism greased, insulation tested"), true);
            return EnumActionResult.SUCCESS;
        }
        if (k != GridKind.TRANSFORMER && k != GridKind.TRACTION) {
            if (n != null && (n.insul < 1 || n.efault != 0)) {                   // re-insulate / rewind
                if (!work(p, w, pos)) return EnumActionResult.SUCCESS;
                History.log(w, pos, "MAINT", "insulation renewed by " + p.getName() + " (was " + Insulation.megohms(n) + ")");
                Insulation.renew(n); d.touch();
                p.sendStatusMessage(new TextComponentString("§aInsulation renewed - megger it before you re-energise"), true);
                return EnumActionResult.SUCCESS;
            }
            p.sendStatusMessage(new TextComponentString("§7Nothing to adjust with a screwdriver here - right-click (not sneaking) for its settings"), true);
            return EnumActionResult.SUCCESS;
        }
        GridData.Net net = d.netOf(pos);
        boolean live = net != null && net.problem.isEmpty();
        if (!live && n.upNet >= 0 && n.upNet < d.nets().size()) live = d.nets().get(n.upNet).problem.isEmpty();
        if (live) {
            p.attackEntityFrom(ZAP, 6f);
            p.sendMessage(new TextComponentString("§c⚡ ZAP! It's LIVE. Off-circuit taps are only moved dead: open its breakers, earth it, then try again"));
            return EnumActionResult.SUCCESS;
        }
        { String why = Permits.mayWork(p, pos); if (!why.isEmpty()) { p.sendStatusMessage(new TextComponentString(why), true); return EnumActionResult.SUCCESS; } }
        n.octap = n.octap >= 2 ? -2 : n.octap + 1;
        if (n.efault != 0 || n.insul < 1) Insulation.renew(n);                   // the tank is open anyway: windings dried and re-insulated
        History.log(w, pos, "TAP", "off-circuit tap moved to position " + (n.octap + 3) + " by " + p.getName());
        d.touch();
        p.sendStatusMessage(new TextComponentString(String.format(Locale.ROOT, "§eOff-circuit tap link: position %d (%+.1f%% output)", n.octap + 3, n.octap * 2.5)), true);
        return EnumActionResult.SUCCESS;
    }
}
