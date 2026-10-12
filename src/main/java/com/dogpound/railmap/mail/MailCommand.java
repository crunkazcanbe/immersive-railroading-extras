package com.dogpound.railmap.mail;

import com.dogpound.railmap.interior.Interior;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/** /irextras mail [list|bags|offices|paper|write <to> <text..>|trace <id>|po|selftest] (batch 5 #8) */
public final class MailCommand {
    private MailCommand() {}

    static final String USAGE = "mail [list|bags|offices|paper [n]|write <to,town> <text..>|trace <id>|po|selftest]";

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("mail")) return false;
        try { run(w, s, a); } catch (RuntimeException e) { say(s, "§c/irextras " + USAGE + " (" + e + ")"); }
        return true;
    }

    private static void run(World w, ICommandSender s, String[] a) {
        MailData d = MailData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                int n = 0;
                for (MailData.Mail m : d.mail.values()) if (m.live()) { n++; say(s, "§e#" + m.id + " §7" + m.what() + " " + m.fromName + " > §f" + m.to + " §7(" + m.destName + ") §f" + MailData.STATE[m.state] + (m.loose ? " §bTPO" : "") + (m.state == MailData.ON_TRAIN ? " §7on " + m.trainName : "")); }
                say(s, "§7" + n + " in the system · posted " + d.postedTotal + " · delivered " + d.deliveredTotal + " (robot " + d.robotTotal + ") · robots " + (Mail.Robots.available() ? "§aON" : "§coff"));
            }
            case "bags" -> {
                for (MailData.Bag b : d.bags.values()) if (b.state != 3) say(s, "§eBag " + b.seal + " §7" + b.fromName + " > " + b.toName + " §f" + MailData.BSTATE[b.state] + (b.state == 1 ? " §7on " + b.trainName : "") + " §7(" + b.items.size() + ")");
                say(s, "§7" + d.bags.size() + " bags on record");
            }
            case "offices" -> {
                for (TileSortingOffice o : StnUtil.loaded(w, TileSortingOffice.class))
                    say(s, "§e" + o.station() + " §7@ " + o.getPos().getX() + "," + o.getPos().getY() + "," + o.getPos().getZ() + " · " + o.gearState());
            }
            case "paper" -> {
                if (!(s instanceof EntityPlayer p)) { say(s, "§cPlayers only"); return; }
                int n = a.length > 2 ? Math.max(1, Math.min(16, Integer.parseInt(a[2]))) : 4;
                ItemStack st = new ItemStack(MailRegistry.mail, n, ItemMail.PAPER);
                if (!p.inventory.addItemStackToInventory(st)) p.dropItem(st, false);
                say(s, "§a" + n + " sheets of writing paper - right-click one to write");
            }
            case "write" -> {
                if (!(s instanceof EntityPlayer p) || a.length < 4) { say(s, "§c/irextras mail write <to[,town]> <message...>  (use _ for spaces in the address)"); return; }
                ItemStack l = ItemMail.write(p.getName(), a[2].replace('_', ' '), Collections.singletonList(String.join(" ", Arrays.copyOfRange(a, 3, a.length))));
                if (!p.inventory.addItemStackToInventory(l)) p.dropItem(l, false);
                say(s, "§aLetter to " + ItemMail.to(l) + " written - post it at a Sorting Office");
            }
            case "trace" -> {
                MailData.Mail m = d.mail.get(Integer.parseInt(a[2].replace("#", "")));
                if (m == null) say(s, "§cNo mail #" + a[2]); else MailConsole.trace(s, w, m);
            }
            case "po" -> {
                for (Map.Entry<UUID, Long> e : d.poBox.entrySet())
                    say(s, "§e" + d.names.getOrDefault(e.getKey(), e.getKey().toString().substring(0, 8)) + " §7PO box at " + com.dogpound.railmap.server.StationData.get(w).names().getOrDefault(e.getValue(), "?"));
                say(s, "§7" + d.poBox.size() + " PO boxes. RealmCoin mailboxes (when installed) win over PO boxes.");
            }
            case "selftest" -> selftest(s);
            default -> say(s, "§7/irextras " + USAGE);
        }
    }

    // ---- self-test: addressing, postage, records, items, the TPO interior ----------------------------------------------------
    static void selftest(ICommandSender s) {
        List<String> fail = new ArrayList<>();
        int ok = 0;
        // addresses
        if (!Mail.addressee("Alex, Riverside").equals("Alex") || !Mail.townPart("Alex, Riverside").equals("Riverside") || !Mail.townPart("Alex").isEmpty()) fail.add("address parsing"); else ok++;
        // postage
        if (MailRegistry.mail != null) {
            ItemStack l = ItemMail.write("A", "B, C", Collections.singletonList("hi"));
            ItemStack p = ItemMail.parcel("A", "B", Arrays.asList(new ItemStack(net.minecraft.init.Items.APPLE, 10), new ItemStack(net.minecraft.init.Items.BREAD, 5)));
            if (Mail.postage(l) != MailConfig.letterFee) fail.add("letter postage"); else ok++;
            long want = MailConfig.parcelBase + (long) MailConfig.parcelPer100g * ((250 + 15 * 120 + 99) / 100);
            if (Mail.postage(p) != want) fail.add("parcel postage " + Mail.postage(p) + " != " + want); else ok++;
            if (ItemMail.contents(p).size() != 2 || ItemMail.contents(p).get(0).getCount() != 10) fail.add("parcel contents"); else ok++;
            if (!ItemMail.to(l).equals("B, C") || !ItemMail.from(l).equals("A") || ItemMail.body(l).size() != 1) fail.add("letter fields"); else ok++;
            if (!ItemMail.isMail(l) || !ItemMail.isMail(p) || ItemMail.isMail(new ItemStack(MailRegistry.mail, 1, ItemMail.PAPER))) fail.add("isMail"); else ok++;
        } else fail.add("mail item not registered");
        // records survive a save
        MailData d = new MailData("selftest");
        MailData.Mail m = d.newMail(100);
        m.to = "Alex, Riverside"; m.destKey = 42; m.destName = "Riverside"; m.state = MailData.ON_TRAIN; m.loose = true; m.train = UUID.randomUUID(); m.toPlayer = UUID.randomUUID();
        if (MailRegistry.mail != null) m.stack = new ItemStack(MailRegistry.mail, 1, ItemMail.LETTER);
        d.track(m, 120, "test");
        MailData.Bag b = d.newBag(130, 1, "Here", 42, "Riverside");
        b.items.add(m.id); b.state = 1; b.train = m.train;
        d.poBox.put(m.toPlayer, 42L);
        MailData e = new MailData("selftest2");
        e.readFromNBT(d.writeToNBT(new NBTTagCompound()));
        MailData.Mail m2 = e.mail.get(m.id);
        MailData.Bag b2 = e.bags.get(b.id);
        if (m2 == null || !m2.to.equals(m.to) || m2.destKey != 42 || !m2.loose || !m.train.equals(m2.train) || m2.log.size() != 1 || m2.stack.isEmpty() != m.stack.isEmpty()) fail.add("mail record save/load"); else ok++;
        if (b2 == null || b2.items.size() != 1 || b2.seal != b.seal || !m.train.equals(b2.train) || e.poBox.get(m.toPlayer) != 42L) fail.add("bag / PO box save/load"); else ok++;
        if (e.nextMail != d.nextMail || e.nextSeal != d.nextSeal) fail.add("counters"); else ok++;
        // the TPO interior: frames with pigeonholes, the late box, the sorters' desk
        try {
            Interior.Plan pl = com.dogpound.railmap.interior.Interior.build(Interior.Layout.TPO, 1, 1.1, 0, 10, 1.35, 2.3);
            boolean late = false, desk = false, frame = false;
            for (Interior.Part pt : pl.parts) {
                late |= pt.name.equals("Late posting box") && pt.slots > 0;
                desk |= pt.kind == Interior.Kind.PANEL && pt.btn("tposort") != null && pt.btn("tposcr") != null;
                frame |= pt.name.startsWith("Sorting frame") && pt.glow.containsValue("tpoh0");
            }
            if (!late || !desk || !frame) fail.add("TPO interior (late " + late + ", desk " + desk + ", frame " + frame + ")"); else ok++;
        } catch (RuntimeException | LinkageError ex) { fail.add("TPO interior: " + ex); }
        if (!MailTpo.byName("royal mail tpo van") || MailTpo.byName("open second coach")) fail.add("TPO by name"); else ok++;
        say(s, (fail.isEmpty() ? "§aMAIL SELF-TEST PASSED " : "§cMAIL SELF-TEST FAILED ") + ok + " ok" + (fail.isEmpty() ? "" : ": " + String.join(", ", fail))
                + " §7· robots " + (Mail.Robots.available() ? "available (RealmCoin)" : "not available (pickup only)"));
    }
}
