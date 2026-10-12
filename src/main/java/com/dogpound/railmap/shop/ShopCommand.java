package com.dogpound.railmap.shop;

import com.dogpound.railmap.incident.Analytics;
import com.dogpound.railmap.station.IShop;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** /irextras shop list|news [new|give]|selftest */
public final class ShopCommand {
    private ShopCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("shop")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "list" -> {
                List<ShopTile> l = StnUtil.loaded(w, ShopTile.class);
                if (l.isEmpty()) say(s, "§7No newsstands, coffee kiosks or ticket offices loaded");
                for (ShopTile t : l) say(s, "§e" + String.join(" · ", t.row()).replaceAll("(^|· )[!~+.]", "$1"));
            }
            case "news" -> {
                NewsData d = NewsData.get(w);
                String what = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "show";
                if (what.equals("new") || d.day < 0) News.publish(w, d, Analytics.day(w));
                if (what.equals("give") && s instanceof EntityPlayer p) {
                    if (!p.capabilities.isCreativeMode && !s.canUseCommand(2, "")) { say(s, "§cBuy it at a Newsstand"); return true; }
                    if (!p.inventory.addItemStackToInventory(News.copy(w))) p.dropItem(News.copy(w), false);
                }
                say(s, "§b" + News.TITLE + " No. " + d.number + " (day " + d.day + ")");
                for (String h : d.heads) say(s, "§e* " + h);
            }
            case "selftest" -> {
                List<String> f = new ArrayList<>(News.selfTest());
                f.addAll(TileTicketOffice.selfTest());
                Random r = new Random(1);
                int n = 0;
                for (int i = 0; i < 1000; i++) n += IShop.sales(60, 30, r);
                if (n != 2000) f.add("60 riders at 1 in 30 should sell 2 a time, sold " + n / 1000.0);
                if (IShop.sales(100, 0, r) != 0) f.add("every 0 = no sales");
                say(s, f.isEmpty() ? "§aStation shops self-test passed (newspaper writing + headlines, written book, fares, passenger sales)" : "§cShops self-test FAILED: " + String.join("; ", f));
            }
            default -> say(s, "§e/irextras shop §7list · news [new|give] · selftest");
        }
        return true;
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
