package com.dogpound.railmap.stn;

import com.dogpound.railmap.RailMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Control Center's STN tab (ChatGPT batch 3, #21-35): a generic, server-described page set, so each group only
 * writes rows. Row tags (PacketRailState.stn):
 * <pre>
 * PAGE  name  help                 starts a sub-page (its tab button)
 * COLS  "Name:pct" ...             column headers; pct = where the column starts, 0-100 % of the width
 * ROW   key  cell  cell ...        a table row; key = what a button acts on when the row is selected ("" = not selectable)
 *                                   a cell starting '!' is drawn red, '~' amber, '+' green, '.' dim
 * BTN   label  op  needsSel(1/0)   a button over the table; it sends PacketStnOp(op, selected row key)
 * TXT   text                        a free text line under the table
 * </pre>
 * op = "group.action": "cargo." goes to freight.CargoConsole, "plat." to station.PlatformConsole, the rest to
 * StnServices.
 */
public final class StnConsole {
    private StnConsole() {}

    public static void fill(World w, List<String[]> out) {
        try { com.dogpound.railmap.freight.CargoConsole.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("cargo console: {}", e.toString()); }
        try { com.dogpound.railmap.station.PlatformConsole.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("platform console: {}", e.toString()); }
        try { StnServices.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("station services console: {}", e.toString()); }
        try { com.dogpound.railmap.shop.ShopConsole.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("shops console: {}", e.toString()); }
        try { com.dogpound.railmap.ems.EmsConsole.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("emergency console: {}", e.toString()); }
        try { com.dogpound.railmap.road.RoadConsole.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("road console: {}", e.toString()); }
        try { com.dogpound.railmap.mail.MailConsole.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("mail console: {}", e.toString()); }   // batch 5 #8
        try { com.dogpound.railmap.freight6.F6Console.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("freight6 console: {}", e.toString()); }   // batch 6 #26-30: Grain, Tank farm, Warehouse, Lockers, Holidays
        try { com.dogpound.railmap.travel6.TravelConsole.fill(w, out); } catch (RuntimeException | LinkageError e) { RailMap.LOG.debug("travel console: {}", e.toString()); }   // batch 6b #6-10
    }

    /** a console button: the reply for the player (null = nothing) */
    public static String op(EntityPlayer p, String op, String arg) {
        if (op.startsWith("cargo.")) return com.dogpound.railmap.freight.CargoConsole.op(p, op.substring(6), arg);
        if (op.startsWith("plat.")) return com.dogpound.railmap.station.PlatformConsole.op(p, op.substring(5), arg);
        if (op.startsWith("shop.")) return com.dogpound.railmap.shop.ShopConsole.op(p, op.substring(5), arg);
        if (op.startsWith("ems.")) return com.dogpound.railmap.ems.EmsConsole.op(p, op.substring(4), arg);
        if (op.startsWith("road.")) return com.dogpound.railmap.road.RoadConsole.op(p, op.substring(5), arg);
        if (op.startsWith("mail.")) return com.dogpound.railmap.mail.MailConsole.op(p, op.substring(5), arg);   // batch 5 #8
        if (op.startsWith("f6.")) return com.dogpound.railmap.freight6.F6Console.op(p, op.substring(3), arg);   // batch 6 #26-30
        if (op.startsWith("travel6.")) return com.dogpound.railmap.travel6.TravelConsole.op(p, op.substring(8), arg);   // batch 6b #6-10
        return StnServices.op(p, op, arg);
    }

    // ---- row helpers for the providers ----------------------------------------------------------------------------
    public static void page(List<String[]> out, String name, String help) { out.add(new String[]{"PAGE", name, help}); }
    public static void cols(List<String[]> out, String... c) { String[] r = new String[c.length + 1]; r[0] = "COLS"; System.arraycopy(c, 0, r, 1, c.length); out.add(r); }
    public static void row(List<String[]> out, String key, String... c) { String[] r = new String[c.length + 2]; r[0] = "ROW"; r[1] = key == null ? "" : key; System.arraycopy(c, 0, r, 2, c.length); out.add(r); }
    public static void btn(List<String[]> out, String label, String op, boolean needsSel) { out.add(new String[]{"BTN", label, op, needsSel ? "1" : "0"}); }
    public static void txt(List<String[]> out, String t) { out.add(new String[]{"TXT", t}); }
}
