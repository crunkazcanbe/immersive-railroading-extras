package com.dogpound.railmap.interior;

import cam72cam.immersiverailroading.entity.EntityRidableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /irextras interior list | info | fit auto|none|coach|cafe|sleeper | parts [kind] | click PART [CONTROL] | sit N | water N | selftest
 * (the car = the passenger car you are in / nearest you).
 */
public final class InteriorCommand {
    private InteriorCommand() {}

    public static final String USAGE = "interior list|info|fit auto|none|coach|cafe|sleeper|parts [kind]|click PART [CTL]|sit N|water L|selftest";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("interior")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        try {
            switch (sub) {
                case "list" -> {
                    InteriorData d = InteriorData.get(w);
                    int n = 0;
                    for (EntityRollingStock st : InteriorServer.stock(w)) {
                        if (!InteriorServer.fittable(st)) continue;
                        InteriorData.Car c = d.peek(st.getUUID());
                        Interior.Layout l = InteriorServer.layoutOf(st, c);
                        if (l == Interior.Layout.NONE && c == null) continue;
                        Interior.Plan p = Interior.plan(st, l);
                        cam72cam.mod.math.Vec3d pos = st.getPosition();
                        say(s, String.format(Locale.ROOT, "§b%s§r [%s%s] %d seats %d berths, power %s, water %.0f L, waste %.0f%%, till %s, owner %s @ %.0f %.0f %.0f",
                                InteriorServer.carName(st), l.label, c == null || c.layout < 0 ? " auto" : "", p == null ? 0 : p.seats, p == null ? 0 : p.berths,
                                InteriorServer.powered(st) ? "§aON§r" : "§cOFF§r", c == null || c.water < 0 ? (double) InteriorConfig.waterTankLitres : c.water, c == null ? 0 : c.waste,
                                com.dogpound.railmap.economy.Coins.fmt(c == null ? 0 : c.till), c == null || c.ownerName.isEmpty() ? "-" : c.ownerName, pos.x, pos.y, pos.z));
                        n++;
                    }
                    say(s, n + " cars with interiors loaded here");
                }
                case "info" -> {
                    EntityRollingStock st = car(w, s);
                    if (st == null) return true;
                    InteriorData.Car c = InteriorData.get(w).peek(st.getUUID());
                    Interior.Layout l = InteriorServer.layoutOf(st, c);
                    double[] dm = Interior.dims(st);
                    say(s, String.format(Locale.ROOT, "%s: %s (by name: %s), gauge x%.2f floor %.2f centre z %.2f half-length %.2f half-width %.2f ceiling %.2f",
                            InteriorServer.carName(st), l.label, Interior.byName(st).label, dm[0], dm[1], dm[2], dm[3], dm[4], dm[5]));
                    Interior.Plan p = Interior.plan(st, l);
                    if (p == null) return true;
                    int boxes = 0, ctl = 0;
                    for (Interior.Part pt : p.parts) { boxes += pt.boxes.size(); ctl += pt.btns.size(); }
                    say(s, String.format(Locale.ROOT, "%d parts, %d boxes, %d controls, %d seats, %d berths, WC %s, CCTV CAM %d", p.parts.size(), boxes, ctl, p.seats, p.berths,
                            p.wc == null ? "none" : "yes", c == null ? 0 : c.cam));
                    if (st instanceof EntityRidableRollingStock r) for (Map.Entry<String, UUID> e : r.prideSeated().entrySet()) {
                        EntityPlayer pl = w.getPlayerEntityByUUID(e.getValue());
                        say(s, "  seated " + e.getKey() + " = " + (pl == null ? e.getValue() : pl.getName()));
                    }
                }
                case "fit" -> {
                    EntityRollingStock st = car(w, s);
                    if (st == null) return true;
                    String k = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "auto";
                    int arg = switch (k) { case "none" -> 0; case "coach" -> 1; case "cafe", "café", "dining" -> 2; case "sleeper" -> 3; default -> -1; };
                    InteriorData.Car c = InteriorData.get(w).car(st.getUUID());
                    say(s, InteriorServer.apply(w, st, c, arg, s instanceof EntityPlayer p ? p : null));
                }
                case "parts" -> {
                    EntityRollingStock st = car(w, s);
                    if (st == null) return true;
                    Interior.Plan p = Interior.plan(st, InteriorServer.layoutOf(st, InteriorData.get(w).peek(st.getUUID())));
                    if (p == null) { say(s, "No interior"); return true; }
                    String f = a.length > 2 ? a[2].toUpperCase(Locale.ROOT) : null;
                    for (Interior.Part pt : p.parts) {
                        if (f != null && !pt.kind.name().startsWith(f)) continue;
                        StringBuilder b = new StringBuilder();
                        for (Interior.Btn bt : pt.btns) b.append(' ').append(bt.id);
                        say(s, " #" + pt.idx + " " + pt.kind + " '" + pt.name + "'" + (pt.slots > 0 ? " slots " + pt.slots : "") + (b.length() > 0 ? " ctl:" + b : ""));
                    }
                }
                case "click" -> {
                    if (!(s instanceof EntityPlayerMP p)) { say(s, "Players only"); return true; }
                    EntityRollingStock st = car(w, s);
                    if (st == null) return true;
                    Interior.Plan plan = Interior.plan(st, InteriorServer.layoutOf(st, InteriorData.get(w).peek(st.getUUID())));
                    if (plan == null) { say(s, "No interior"); return true; }
                    Interior.Part pt = plan.part(Integer.parseInt(a[2]));
                    if (pt == null) { say(s, "No part " + a[2]); return true; }
                    String btn = a.length > 3 ? a[3] : "";
                    if (!btn.isEmpty() && pt.btn(btn) == null) { say(s, "Part #" + pt.idx + " has no control '" + btn + "'"); return true; }
                    InteriorData.Car c = InteriorData.get(w).car(st.getUUID());
                    if (c.water < 0) c.water = InteriorConfig.waterTankLitres;
                    String r = InteriorServer.click(p, st, c, plan, pt, btn);
                    say(s, r == null ? "(no reply)" : r);
                }
                case "sit" -> {
                    if (!(s instanceof EntityPlayerMP p)) { say(s, "Players only"); return true; }
                    EntityRollingStock st = car(w, s);
                    if (st == null) return true;
                    Interior.Plan plan = Interior.plan(st, InteriorServer.layoutOf(st, InteriorData.get(w).peek(st.getUUID())));
                    int no = Integer.parseInt(a[2]);
                    if (plan != null) for (Interior.Part pt : plan.parts)
                        if ((pt.kind == Interior.Kind.SEAT || pt.kind == Interior.Kind.BERTH) && pt.no == no) { say(s, InteriorServer.click(p, st, InteriorData.get(w).car(st.getUUID()), plan, pt, "")); return true; }
                    say(s, "No seat / berth " + no);
                }
                case "water" -> {
                    EntityRollingStock st = car(w, s);
                    if (st == null) return true;
                    InteriorData.Car c = InteriorData.get(w).car(st.getUUID());
                    c.water = Math.max(0, Math.min(InteriorConfig.waterTankLitres, Float.parseFloat(a[2])));
                    InteriorData.get(w).markDirty();
                    say(s, "Water " + c.water + " L");
                }
                case "selftest" -> selftest(s);
                default -> say(s, "/irextras " + USAGE);
            }
        } catch (RuntimeException | LinkageError e) {
            say(s, "§c" + e + "  §7/irextras " + USAGE);
        }
        return true;
    }

    private static EntityRollingStock car(World w, ICommandSender s) {
        net.minecraft.util.math.Vec3d at = s.getPositionVector();
        EntityRollingStock best = null;
        double bd = 64;
        for (EntityRollingStock st : InteriorServer.stock(w)) {
            if (!InteriorServer.fittable(st)) continue;
            double[] lo = Interior.toLocal(st, at.x, at.y, at.z);
            double half = st.getDefinition().getLength(st.gauge) / 2;
            double dz = Math.max(0, Math.abs(lo[2]) - half), d = dz * dz + lo[0] * lo[0];
            if (d < bd) { bd = d; best = st; }
        }
        if (best == null) say(s, "§7No IR passenger car within 8 blocks");
        return best;
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    /** geometry, picking, seat keys and saved data - no world needed */
    static void selftest(ICommandSender s) {
        List<String> fail = new ArrayList<>();
        int pass = 0;
        double[][] cars = {{1, 1.1, 0, 10, 1.35, 2.3}, {1, 1.0, 0.4, 12.5, 1.4, 2.4}, {0.5, 0.6, 0, 9, 1.3, 2.2}, {1, 1.2, 0, 4, 1.0, 2.1}};
        for (double[] d : cars) for (Interior.Layout l : Interior.Layout.values()) {
            if (l == Interior.Layout.NONE) continue;
            String tag = l + " L" + d[3] + " g" + d[0];
            Interior.Plan p = Interior.build(l, d[0], d[1], d[2], d[3], d[4], d[5]);
            Set<String> keys = new HashSet<>();
            boolean ok = true;
            for (Interior.Part pt : p.parts) {
                if (pt.sit != null) {
                    String k = pt.seatKey();
                    if (!keys.add(k)) { fail.add(tag + ": duplicate seat key " + k); ok = false; }
                    // the fork parses the key back into the same offset
                    try {
                        cam72cam.mod.math.Vec3d o = EntityRidableRollingStock.prideSeatOffset(k);
                        if (o == null || Math.abs(o.x - pt.sit[0]) > 1e-3 || Math.abs(o.z - pt.sit[2]) > 1e-3) { fail.add(tag + ": seat key round trip " + k); ok = false; }
                    } catch (LinkageError e) { fail.add("fork without prideSeatOffset (rebuild IR fork)"); ok = false; }
                    double hl = d[3] * d[0], hw = d[4] * d[0];
                    if (Math.abs(pt.sit[0]) > hw + 1e-6 || Math.abs(pt.sit[2] - d[2]) > hl + 1e-6) { fail.add(tag + ": " + pt.name + " outside the compartment"); ok = false; }
                    // a ray from the aisle at standing eye height, aimed at the cushion, hits this part (or a control on it)
                    if (d[0] < 0.75) continue;                           // narrow gauge: a full-size player doesn't fit standing
                    double[] eye = {0, d[1] + 1.62, pt.sit[2]};
                    double[] tgt = {pt.sit[0], pt.sit[1] + InteriorConfig.seatSitDrop + 0.02 * d[0], pt.sit[2]};
                    double[] dir = {tgt[0] - eye[0], tgt[1] - eye[1], tgt[2] - eye[2]};
                    double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
                    for (int i = 0; i < 3; i++) dir[i] /= len;
                    double[] hit = Interior.pick(p, eye, dir, 6);
                    if (hit == null || (int) hit[0] != pt.idx) { fail.add(tag + ": aiming at " + pt.name + " picks " + (hit == null ? "nothing" : p.part((int) hit[0]).name)); ok = false; }
                }
                if (pt.slots > 0 && (pt.slots % 9 != 0 || pt.spots.size() > pt.slots)) { fail.add(tag + ": " + pt.name + " slots " + pt.slots + " spots " + pt.spots.size()); ok = false; }
                for (Interior.Btn b : pt.btns) if (b.box[3] < b.box[0] || b.box[4] < b.box[1] || b.box[5] < b.box[2]) { fail.add(tag + ": inverted control box " + pt.name + "." + b.id); ok = false; }
            }
            if (l == Interior.Layout.SLEEPER && d[3] >= 8 && p.berths == 0) { fail.add(tag + ": no berths"); ok = false; }
            if (l == Interior.Layout.COACH && d[3] >= 8 && p.seats < 8) { fail.add(tag + ": only " + p.seats + " seats"); ok = false; }
            if (l == Interior.Layout.CAFE && (p.of(Interior.Kind.FRIDGE).isEmpty() || p.of(Interior.Kind.TILL).isEmpty() || p.of(Interior.Kind.COFFEE).isEmpty())) { fail.add(tag + ": café without fridge / till / coffee"); ok = false; }
            if (p.wc != null) {
                Interior.Part t = p.of(Interior.Kind.TOILET).get(0);
                double[] h = t.hit;
                if (!p.inWc((h[0] + h[3]) / 2, (h[1] + h[4]) / 2, (h[2] + h[5]) / 2)) { fail.add(tag + ": toilet outside its cubicle"); ok = false; }
                if (p.inWc(0, d[1] * d[0] + 0.5, 0)) { fail.add(tag + ": aisle inside the WC zone"); ok = false; }
            }
            if (ok) pass++;
        }
        // saved data round trip
        InteriorData.Car c = new InteriorData.Car(UUID.randomUUID());
        c.layout = 2; c.water = 123.5f; c.waste = 40; c.till = 1234; c.on.add("read5"); c.on.add("alarm"); c.lightMode = 1; c.cam = 7;
        Interior.Plan cafe = Interior.build(Interior.Layout.CAFE, 1, 1.1, 0, 10, 1.35, 2.3);
        InteriorData.Store fr = c.store(cafe.of(Interior.Kind.FRIDGE).get(0));
        fr.setInventorySlotContents(4, new ItemStack(Items.BREAD, 5));
        NBTTagCompound t = c.write();
        InteriorData.Car back = new InteriorData.Car(c.id);
        back.read(t);
        InteriorData.Store fb = back.store(cafe.of(Interior.Kind.FRIDGE).get(0));
        if (back.layout != 2 || back.water != 123.5f || back.till != 1234 || !back.is("read5") || back.lightMode != 1 || back.cam != 7 || fb.getStackInSlot(4).getCount() != 5) fail.add("saved data round trip");
        else pass++;
        // an old save (no entry) reads as automatic, full tank
        InteriorData.Car fresh = new InteriorData.Car(UUID.randomUUID());
        fresh.read(new NBTTagCompound());
        if (fresh.layout != -1 || fresh.water >= 0) fail.add("empty entry is not 'automatic, tank unset'"); else pass++;
        // prices + brewed drinks
        if (InteriorServer.price(new ItemStack(Items.COOKED_BEEF)) <= InteriorServer.price(new ItemStack(Items.COOKIE))) fail.add("steak should cost more than a cookie"); else pass++;
        ItemStack esp = InteriorServer.drink("Espresso", 0x3B1F0E);
        if (InteriorServer.price(esp) != InteriorConfig.drinkPrice || !esp.getDisplayName().contains("Espresso")) fail.add("drink price / name"); else pass++;
        // sliding a whole stack onto a 9-slot rack: merges, then fills
        InteriorData.Store rack = c.store(cafe.of(Interior.Kind.TABLE).get(0));
        ItemStack rest = InteriorServer.insert(rack, new ItemStack(Items.APPLE, 40));
        rest = InteriorServer.insert(rack, new ItemStack(Items.APPLE, 40));
        if (!rest.isEmpty() || InteriorServer.count(rack) != 80) fail.add("rack insert"); else pass++;
        say(s, (fail.isEmpty() ? "§a" : "§c") + "Interiors self-test: " + pass + " passed, " + fail.size() + " failed");
        for (int i = 0; i < Math.min(12, fail.size()); i++) say(s, " §c- " + fail.get(i));
    }
}
