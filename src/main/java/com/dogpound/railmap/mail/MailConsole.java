package com.dogpound.railmap.mail;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.*;

import static com.dogpound.railmap.stn.StnConsole.*;

/** STN tab pages for mail by train (batch 5 #8): Mail (bags + letters live), Post offices. Ops arrive without "mail.". */
public final class MailConsole {
    private MailConsole() {}

    public static void fill(World w, List<String[]> out) {
        MailData d = MailData.get(w);
        long now = w.getTotalWorldTime();
        page(out, "Mail", "Mail bags and letters, live: where every bag is (waiting / on which train / arrived), and every letter's state");
        cols(out, "Bag / item:0", "From:14", "To:30", "State:46", "Train / where:58", "Items:76", "Age:84");
        btn(out, "TRACE", "mail.trace", true);
        btn(out, "RETURN", "mail.return", true);
        for (MailData.Bag b : d.bags.values()) {
            if (b.state == 3) continue;
            out.add(new String[]{"ROW", "B" + b.id, "+BAG " + b.seal, b.fromName, b.toName, (b.state == 1 ? "~" : b.state == 2 ? "+" : "") + MailData.BSTATE[b.state],
                    b.state == 1 ? b.trainName : ".", String.valueOf(b.items.size()), (now - b.moved) / 1000 + " h"});
        }
        int n = 0;
        List<MailData.Mail> ms = new ArrayList<>(d.mail.values());
        Collections.reverse(ms);
        for (MailData.Mail m : ms) {
            if (n++ >= 120) break;
            String st = MailData.STATE[m.state] + (m.loose ? " (TPO)" : "");
            out.add(new String[]{"ROW", "M" + m.id, "#" + m.id + " " + m.what(), m.fromName, m.to + " (" + m.destName + ")", (m.state == MailData.DELIVERED || m.state == MailData.COLLECTED ? "+" : m.state == MailData.ON_TRAIN ? "~" : m.state == MailData.PICKUP ? "." : "") + st,
                    m.state == MailData.ON_TRAIN ? m.trainName : "", m.kind == 1 ? ItemMail.contents(m.stack).size() + " stk" : "", (now - m.posted) / 1000 + " h"});
        }
        txt(out, String.format(Locale.ROOT, "Posted %d · delivered %d (by robot %d) · to pickup %d · robots %s · letter %s, parcel %s + %s/100 g",
                d.postedTotal, d.deliveredTotal, d.robotTotal, d.pickupTotal, Mail.Robots.available() ? "ON (RealmCoin)" : "off (no RealmCoin) - pickup only",
                com.dogpound.railmap.economy.Coins.fmt(MailConfig.letterFee), com.dogpound.railmap.economy.Coins.fmt(MailConfig.parcelBase), com.dogpound.railmap.economy.Coins.fmt(MailConfig.parcelPer100g)));
        for (int i = 0; i < Math.min(6, d.log.size()); i++) txt(out, d.log.get(i));

        page(out, "Post offices", "Every Sorting Office: open / auto, letters in its tray and pigeonholes, bags waiting and arrived, pickup shelf");
        cols(out, "Office:0", "Open:24", "Auto:31", "Tray:38", "Sorted:45", "Bags out:53", "Bags in:62", "Supply:71");
        btn(out, "SORT", "mail.sort", true); btn(out, "BAG", "mail.bag", true); btn(out, "OPEN BAGS", "mail.open", true);
        for (TileSortingOffice o : StnUtil.loaded(w, TileSortingOffice.class))
            out.add(new String[]{"ROW", "O" + o.getPos().toLong(), (o.station().isEmpty() ? "?" : o.station()) + " " + o.getPos().getX() + "," + o.getPos().getZ(), o.open() ? "+YES" : "!no",
                    o.controls().get("k_auto") == 1 ? "+AUTO" : ".hand", String.valueOf(o.tray), String.valueOf(TileSortingOffice.sum(o.holes)), String.valueOf(o.bagsOut), String.valueOf(o.bagsIn), o.live() ? "+ok" : "!NO SUPPLY"});
        txt(out, "PO boxes registered: " + d.poBox.size() + ". A TPO train sorts loose letters en route (fit a car with the Interior Fitting Kit: Travelling Post Office).");
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        MailData d = MailData.get(w);
        switch (op) {
            case "trace" -> {
                if (arg.startsWith("M")) {
                    MailData.Mail m = d.mail.get(Integer.parseInt(arg.substring(1)));
                    if (m == null) return "§7Gone";
                    trace(p, w, m);
                    return "";
                }
                if (arg.startsWith("B")) {
                    MailData.Bag b = d.bags.get(Integer.parseInt(arg.substring(1)));
                    if (b == null) return "§7Gone";
                    say(p, "§eBag " + b.seal + " §7" + b.fromName + " > " + b.toName + " · " + MailData.BSTATE[b.state] + (b.state == 1 ? " on " + b.trainName : ""));
                    for (int id : b.items) { MailData.Mail m = d.mail.get(id); if (m != null) say(p, "§7 #" + m.id + " " + m.what() + " to " + m.to + " - " + MailData.STATE[m.state]); }
                    return "";
                }
                return "§7Select a bag or a letter";
            }
            case "return" -> {
                if (!arg.startsWith("M")) return "§7Select a letter";
                MailData.Mail m = d.mail.get(Integer.parseInt(arg.substring(1)));
                if (m == null || m.state != MailData.PICKUP) return "§7Only letters waiting on a pickup shelf can be returned to sender";
                m.to = m.from + ", " + m.fromName;
                Mail.resolve(w, m, m.fromKey, m.fromName);
                for (TileSortingOffice o : StnUtil.loaded(w, TileSortingOffice.class))
                    if (o.getPos().toLong() == m.office) {
                        for (int i = 0; i < o.pickup().getSizeInventory(); i++) if (ItemMail.mailId(o.pickup().getStackInSlot(i)) == m.id) { m.stack = o.pickup().getStackInSlot(i).copy(); o.pickup().setInventorySlotContents(i, net.minecraft.item.ItemStack.EMPTY); }
                    }
                if (m.stack.isEmpty()) return "§cThat letter is not on its shelf any more";
                ItemMail.tag(m.stack).setString("to", m.to);
                m.state = MailData.POSTED;
                d.track(m, w.getTotalWorldTime(), "RETURN TO SENDER (" + p.getName() + ") - now for " + m.destName);
                return "§aReturned to sender: " + m.to;
            }
            case "sort", "bag", "open" -> {
                if (!arg.startsWith("O")) return "§7Select a post office";
                net.minecraft.tileentity.TileEntity te = w.getTileEntity(net.minecraft.util.math.BlockPos.fromLong(Long.parseLong(arg.substring(1))));
                if (!(te instanceof TileSortingOffice o)) return "§7That office is gone";
                return com.dogpound.railmap.controls.IControlled.operate(o, op, null);
            }
            default -> { return "§7Unknown mail op " + op; }
        }
    }

    static void trace(net.minecraft.command.ICommandSender s, World w, MailData.Mail m) {
        say(s, "§e#" + m.id + " " + m.what() + " §7to §f" + m.to + " §7(" + m.destName + ") from " + m.from + " at " + m.fromName + " · §f" + MailData.STATE[m.state] + (m.loose ? " loose on the TPO" : ""));
        for (String e : m.log) {
            String[] q = e.split("\\|", 2);
            if (q.length == 2) say(s, "§8 " + Recorder.clock(w, Long.parseLong(q[0])) + " §7" + q[1]);
        }
    }

    static void say(net.minecraft.command.ICommandSender s, String t) { s.sendMessage(new net.minecraft.util.text.TextComponentString(t)); }
}
