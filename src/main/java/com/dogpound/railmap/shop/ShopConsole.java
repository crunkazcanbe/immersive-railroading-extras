package com.dogpound.railmap.shop;

import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

import static com.dogpound.railmap.stn.StnConsole.*;

/** the console STN tab's Shops + Newspaper pages, and their buttons ("shop.*") */
public final class ShopConsole {
    private ShopConsole() {}

    public static void fill(World w, List<String[]> out) {
        page(out, "Shops", "Station shops: newsstands, coffee kiosks, ticket office counters (takings go to the station's operators daily)");
        cols(out, "Station:0", "Shop:18", "State:32", "Sold:44", "Takings:51", "Stock:62");
        for (ShopTile t : StnUtil.loaded(w, ShopTile.class)) row(out, String.valueOf(t.getPos().toLong()), t.row());
        btn(out, "OPEN / CLOSE", "shop.k_open", true);
        btn(out, "PRINT 5", "shop.print5", true);
        btn(out, "BOILER", "shop.k_boiler", true);
        NewsData d = NewsData.get(w);
        txt(out, "Newspapers sold (all stands): " + d.sold + " · today's edition No. " + d.number + " (day " + d.day + ")");
        page(out, "Newspaper", "Today's edition of " + News.TITLE + ", written from the server's real events");
        cols(out, "Line:0");
        for (String l : d.lines) row(out, "", l.startsWith("#") ? "+" + l.substring(1) : l);
        btn(out, "NEW EDITION NOW", "shop.news", false);
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        if (op.equals("news")) { News.publish(w, NewsData.get(w), com.dogpound.railmap.incident.Analytics.day(w)); return "§aNew edition No. " + NewsData.get(w).number + " written"; }
        BlockPos at;
        try { at = BlockPos.fromLong(Long.parseLong(arg)); } catch (NumberFormatException e) { return "§7Pick a shop first"; }
        TileEntity t = w.isBlockLoaded(at) ? w.getTileEntity(at) : null;
        if (!(t instanceof ShopTile s)) return "§7That shop isn't loaded";
        if (op.equals("print5") && !(s instanceof TileNewsstand)) return "§7Only newsstands print";
        if (op.equals("k_boiler") && !(s instanceof TileCoffeeKiosk)) return "§7Only coffee kiosks have a boiler";
        String r = IControlled.operate(s, op, null);
        return r == null || r.isEmpty() ? "§7" + s.facilityKind() + ": done" : r;
    }

}
