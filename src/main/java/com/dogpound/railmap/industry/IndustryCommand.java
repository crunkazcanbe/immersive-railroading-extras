package com.dogpound.railmap.industry;

import com.dogpound.railmap.freight.CargoRules;
import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.freight.ContractData;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.Locale;

/**
 * /irextras industry [order | cargo <car name> | supply x y z | finish x y z] - the industry link (her list section 13):
 * every plant the railway serves with what it has ready and what it is short of, the factory load ledger, an automatic
 * order posted now, the wagon cargo rules for the item in hand, what supply a factory at a spot would see.
 */
public final class IndustryCommand {
    private IndustryCommand() {}

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    public static void run(ICommandSender sender, World w, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "order": {
                if (!sender.canUseCommand(2, "irextras")) { say(sender, "Needs op."); return; }
                Contract k = ContractData.get(w).orders(w);
                say(sender, k == null ? "§7No order: nobody is short of anything a loaded plant makes (or it is already on order)" : "§aOrder #" + k.id + ": " + k.qty + " " + k.itemName + " " + k.fromName + " -> " + k.toName + " · $" + Contract.money(k.pay));
                return;
            }
            case "cargo": {
                if (!(sender.getCommandSenderEntity() instanceof EntityPlayer p)) { say(sender, "Hold the item"); return; }
                String car = a.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : "Boxcar";
                ItemStack h = p.getHeldItemMainhand();
                if (h.isEmpty()) { say(sender, "Hold the item to test"); return; }
                String no = CargoRules.refuse(car, h);
                say(sender, "§b" + car + "§7 is a §f" + CargoRules.car(car) + "§7 car; " + h.getDisplayName() + " is §f" + CargoRules.cargo(h) + (CargoRules.dense(h) ? " (dense)" : "") + "§7: "
                        + (no == null ? "§aloads, up to " + CargoRules.limitPct(car, h) + "% full" : "§crefused - " + no));
                return;
            }
            case "supply": case "finish": {
                if (a.length < 5) { say(sender, "/irextras industry " + sub + " <x> <y> <z>"); return; }
                BlockPos bp;
                try { bp = new BlockPos(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])); } catch (NumberFormatException e) { say(sender, "Bad position"); return; }
                if (sub.equals("finish")) {
                    if (!sender.canUseCommand(2, "irextras")) { say(sender, "Needs op."); return; }
                    say(sender, w.getTileEntity(bp) instanceof TileConstructionSite s ? s.finishNow() : "No construction site office there");
                    return;
                }
                IndustryLink.Supply s = IndustryLink.supply(w, bp);
                say(sender, !s.live ? "§cNo live supply touching " + bp + ": " + s.why
                        : String.format(Locale.ROOT, "§bNetwork #%d %s§7: %.1f%% volts, %s, THD %.2f%%, unbalance %.2f%%%s", s.net, s.level, s.pu * 100,
                        s.ac ? String.format(Locale.ROOT, "%.2f Hz", s.hz) : "DC", s.thdV, s.vuf, s.shed ? " §c· INDUSTRY SHED" : ""));
                return;
            }
            default: break;
        }
        say(sender, "§bPlants the railway serves:");
        for (TileEntity te : IndustryLink.all(w)) {
            IndustryLink.Endpoint e = (IndustryLink.Endpoint) te;
            StringBuilder mk = new StringBuilder(), wn = new StringBuilder();
            for (ItemStack s : e.makes()) if (!s.isEmpty()) mk.append(' ').append(s.getCount()).append(' ').append(s.getDisplayName()).append(',');
            for (FluidStack f : e.fluidsMade()) if (f != null) mk.append(' ').append(f.amount).append(" L ").append(f.getLocalizedName()).append(',');
            for (ItemStack s : e.wants()) if (!s.isEmpty()) wn.append(' ').append(s.getCount()).append(' ').append(s.getDisplayName()).append(',');
            for (FluidStack f : e.fluidsWanted()) if (f != null) wn.append(' ').append(f.amount).append(" L ").append(f.getLocalizedName()).append(',');
            BlockPos p = te.getPos();
            say(sender, " §f" + e.endpointName() + " §7[" + e.endpointState() + "] " + p.getX() + " " + p.getY() + " " + p.getZ()
                    + (mk.length() > 0 ? " · ready:" + mk : "") + (wn.length() > 0 ? " · §eshort:" + wn : ""));
        }
        say(sender, String.format(Locale.ROOT, "§bFactory load: %.0f kW", IndustryLink.industryKw(w)));
        for (IndustryLink.Load l : IndustryLink.loads(w))
            say(sender, String.format(Locale.ROOT, " §f%s §7%s · %.0f kW · %s", l.name, l.state, l.kw, l.supply));
    }
}
