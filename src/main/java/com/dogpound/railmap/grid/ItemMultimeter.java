package com.dogpound.railmap.grid;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * Clamp meter / multimeter (requested feature): right-click any grid machine or cable for a full reading of that point:
 * level, V, A, P, Q, S, PF, Hz, temperature, losses, and the conductor if it's a cable.
 */
public class ItemMultimeter extends Item {
    public ItemMultimeter() {
        setRegistryName(RailMap.MODID, "multimeter");
        setTranslationKey(RailMap.MODID + ".multimeter");
        setCreativeTab(RailMapTab.INSTANCE);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§7Right-click a grid machine or cable: V, A, kW, kvar, kVA, PF, Hz, °C, insulation (megger)");
        tip.add("§7On dead plant it records a PROVEN DEAD test (permits to work)");
        tip.add("§7Sneak + right-click: the equipment's maintenance history");
        tip.add("§7Right-click 12 V signalling gear or points: terminal volts, board / lamp current, relay coil, insulation");
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer p, World w, BlockPos pos, EnumFacing side, float hx, float hy, float hz, EnumHand hand) {
        if (!(w.getBlockState(pos).getBlock() instanceof BlockGrid)) {                // batch-3 #48: 12 V signalling gear and point detection
            if (w.isRemote) return w.getTileEntity(pos) instanceof com.dogpound.railmap.wire.IWiredGear ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
            return !p.isSneaking() && com.dogpound.railmap.safety.Diagnosis.meter(p, w, pos) ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
        }
        if (w.isRemote) return EnumActionResult.SUCCESS;
        GridData d = GridData.get(w);
        GridData.Node n = d.node(pos);
        GridData.Net net = d.netOf(pos);
        if (n == null || net == null) { say(p, "§7No reading - not part of a network"); return EnumActionResult.SUCCESS; }
        if (p.isSneaking()) {                                                   // the log book (#43)
            say(p, "§b§l📒 History §r§7· " + n.kind.label + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            say(p, "§7" + History.counters(n));
            if (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION || n.kind.electronic()) say(p, "§7" + Thermal.describe(n));
            List<String> h = History.recent(n, 12);
            if (h.isEmpty()) say(p, "§8nothing recorded yet");
            for (String e : h) say(p, "§f" + e);
            return EnumActionResult.SUCCESS;
        }
        Elec.Level lv = net.level;
        boolean live = net.problem.isEmpty();
        double nominal = lv != null ? lv.volts : (net.traction ? 25_000 : 11_000);
        double pu = lv != null ? (n.pu > 0 ? n.pu : net.puSource) : net.volt;
        double v = live ? nominal * pu : 0;
        double amps = n.kind == GridKind.CABLE || n.sided() ? n.amps : net.amps;
        boolean dead = !live || v < 1;
        com.dogpound.railmap.safety.Diagnosis.gridMeasured(p, w, pos, v, lv == Elec.Level.DC12);   // batch-3 #48: a 12 V source reading for a diagnosis job
        if (dead && !Permits.liveNow(w, pos)) {                                 // a proven-dead test, for the permit to work (#44)
            n.deadTestAt = w.getTotalWorldTime(); n.deadTestBy = p.getName(); d.markDirty();
            History.log(w, pos, "TEST", "proven DEAD with the multimeter by " + p.getName());
        }
        say(p, "§b§l⚡ Multimeter §r§7· " + n.kind.label + " · network " + net.id + (live ? " §aLIVE" : " §cDEAD: " + net.problem)
                + (dead && !Permits.liveNow(w, pos) ? " §a· ✔ PROVEN DEAD (test recorded)" : ""));
        say(p, String.format(Locale.ROOT, "§7Level §f%s§7 · §f%s§7 (%.1f%%) · §f%s§7 · %s",
                lv == null ? "legacy (no levels)" : lv.label, Elec.si(v, "V"), pu * 100, Elec.si(amps, "A"),
                lv == null || !lv.ac ? "DC" : String.format(Locale.ROOT, "%d Hz %dφ", lv.hz, lv.phases)));
        say(p, String.format(Locale.ROOT, "§7P §f%s§7 · Q §f%s§7 · S §f%s§7 · PF §f%.2f§7 · losses §f%s§7 · efficiency §f%.1f%%",
                Elec.si(net.pW, "W"), Elec.si(net.qVar, "var"), Elec.si(net.sVA, "VA"), net.pf, Elec.si(net.lossW, "W"), net.eff * 100));
        if (lv != null && net.isc > 0)
            say(p, String.format(Locale.ROOT, "§7Prospective fault current §f%s§7 (%s fault level)%s", Elec.si(net.isc, "A"), Elec.si(net.faultVA, "VA"),
                    n.kind == GridKind.BREAKER ? (net.isc > n.kA * 1000.0 ? " §c· breaker rated only " + n.kA + " kA - it will NOT survive a fault!" : " §a· breaker " + n.kA + " kA OK") : ""));
        if (n.kind == GridKind.CABLE) {
            Elec.Spec sp = n.cable();
            say(p, String.format(Locale.ROOT, "§7Cable §f%s§7 · %.0f / %.0f A (%.0f%%) · conductor §f%.0f °C§7 of %d °C max",
                    sp.label(), n.amps, sp.ampacity(), n.amps / Math.max(1, sp.ampacity()) * 100, n.temp, sp.ins.maxTemp));
        } else if (n.sided())
            say(p, String.format(Locale.ROOT, "§7Converter §f%s → %s§7 · %d kVA · tap %d", Elec.Level.byName(n.primary, Elec.Level.MV13800).label,
                    Elec.Level.byName(n.level, Elec.Level.AC480).label, n.kva, n.tap));
        if (lv != null) say(p, String.format(Locale.ROOT, "§7Insulation (megger) §f%s§7 %s", Insulation.megohms(n), Insulation.passes(n, lv.volts) ? "§apass" : "§cFAIL")
                + (n.efault != 0 ? " §4· EARTH FAULT HERE" : "") + (net.efAmps > 0 ? String.format(Locale.ROOT, " §c· %.0f A flowing to earth on this network", net.efAmps) : net.efAmps < 0 ? " §e· DC earth leakage on this network" : ""));
        if (n.kind == GridKind.TRANSFORMER || n.kind == GridKind.TRACTION || n.kind.electronic()) say(p, "§7Thermal: " + Thermal.describe(n));
        if (lv != null && lv.ac && (net.vuf > 0.05 || net.thdV > 0.05)) say(p, String.format(Locale.ROOT, "§7Power quality: VUF §f%.2f%%§7 · THDv §f%.2f%%§7 · THDi §f%.1f%%§7 %s", net.vuf, net.thdV, net.thdI, net.resonance));
        return EnumActionResult.SUCCESS;
    }

    private static void say(EntityPlayer p, String s) { p.sendMessage(new TextComponentString(s)); }
}
