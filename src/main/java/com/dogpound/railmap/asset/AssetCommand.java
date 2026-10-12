package com.dogpound.railmap.asset;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.depot.Telemetry;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.Thermal;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras asset ... - the asset register, inspection, procurement, remanufacturing, thermal model, commissioning and
 * rebuilds (her 48-next list #2 #5 #10 #16 #19 #22 #24 #27 #28 #45):
 * <pre>
 *   asset                         summary                       asset list [kind]       worst assets (of a kind)
 *   asset info &lt;id&gt;               one asset in full             asset near              assets within 16 blocks
 *   asset defects                 open defects                  asset order|repair &lt;defect&gt;
 *   asset stock | pos             spares stock / purchase orders
 *   asset po &lt;spare&gt; &lt;qty&gt; [catalogue|express|factory|reman]   asset cancel|expedite &lt;po&gt;
 *   asset thermal                 transformer hot spots, loss of life, motor replicas
 *   asset fleet                   telemetry trends, P-codes, remaining life
 *   asset certs                   certificates                  asset bench &lt;x y z&gt;   secondary injection of an IED
 *   asset tool                    the five inspection instruments    asset core &lt;spare&gt; &lt;cond%&gt;   a core (test)
 *   asset age &lt;id&gt; &lt;life%&gt; | asset seed &lt;id&gt; [size] | asset comm &lt;id&gt; new|done   (tests)
 *   asset selftest                checks the maths: POD, ageing, thermal curves, grading, substitutions
 * </pre>
 */
public final class AssetCommand {
    private AssetCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("asset") && !a[0].equalsIgnoreCase("assets")) return false;
        try { run(w, s, a); } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    private static void run(World w, ICommandSender s, String[] a) {
        AssetData d = AssetData.get(w);
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        long now = w.getTotalWorldTime();
        switch (sub) {
            case "" -> {
                List<String[]> rows = new ArrayList<>();
                AssetConsole.fill(w, rows);
                String[] k = rows.get(0);
                say(s, "§6Assets: §f" + k[1] + " registered · " + k[2] + " uncommissioned · " + k[3] + " inspections overdue · " + k[4] + " open defects · " + k[5] + " open POs ($" + k[6] + ")");
                Map<AssetKind, Integer> n = new EnumMap<>(AssetKind.class);
                for (AssetData.Asset x : d.all()) n.merge(x.kind, 1, Integer::sum);
                StringBuilder b = new StringBuilder();
                for (Map.Entry<AssetKind, Integer> e : n.entrySet()) b.append(e.getKey().prefix).append(' ').append(e.getValue()).append("  ");
                say(s, "§7" + b);
                say(s, "§8asset list|info|near|defects|order|repair|stock|pos|po|cancel|expedite|thermal|fleet|certs|bench|tool|core|age|seed|comm|selftest");
            }
            case "list" -> {
                AssetKind kind = a.length > 2 ? AssetKind.of(a[2]) : null;
                List<AssetData.Asset> l = new ArrayList<>(d.all());
                l.removeIf(x -> kind != null && x.kind != kind);
                l.sort(Comparator.comparingDouble(x -> x.cond));
                for (int i = 0; i < Math.min(20, l.size()); i++) say(s, line(l.get(i), now));
                if (l.isEmpty()) say(s, "§7Nothing registered" + (kind == null ? "" : " of that kind"));
            }
            case "info" -> {
                AssetData.Asset x = d.byId(arg(a, 2));
                if (x == null) { say(s, "§cNo asset " + arg(a, 2)); return; }
                say(s, line(x, now));
                say(s, String.format(Locale.ROOT, "§7Installed %.1f days ago · %.2f %s used (%.0f%% of a %.0f life) · %.2f/day · %s left · inspected %.1f h ago by %s · %s",
                        (now - x.installed) / 24000.0, x.use, x.kind.unit, x.lifeUsed() * 100, x.kind.life / Math.max(0.01, AssetConfig.ageingRate), x.ratePerDay,
                        x.daysLeft() < 0 ? "?" : String.format(Locale.ROOT, "%.0f days", x.daysLeft()), (now - x.inspected) / 1000.0, x.inspectedBy.isEmpty() ? "-" : x.inspectedBy,
                        AssetData.COMM[x.comm] + (x.commBy.isEmpty() ? "" : " (" + x.commBy + ")")));
                if (!x.deps.isEmpty()) say(s, "§7Depends on: " + String.join(", ", x.deps));
                for (AssetData.Defect df : d.defectsOf(x.id)) say(s, defect(df));
                for (String h : x.history) say(s, "§8" + h);
            }
            case "near" -> {
                BlockPos p = s.getPosition();
                for (AssetData.Asset x : d.all()) if (x.at().distanceSq(p) < 16 * 16) say(s, line(x, now));
            }
            case "defects" -> { int n = 0; for (AssetData.Defect df : d.defects) if (df.open()) { say(s, defect(df)); n++; } if (n == 0) say(s, "§aNo open defects (latent ones are still hidden - inspect!)"); }
            case "order" -> say(s, "§e" + AssetConsole.orderRepair(w, Integer.parseInt(arg(a, 2)), s.getName()));
            case "repair" -> say(s, "§e" + AssetConsole.repair(w, Integer.parseInt(arg(a, 2)), s.getName()));
            case "stock" -> {
                int[] st = Procurement.stock(w);
                Map<ItemSpare.Spare, Object[]> sh = Procurement.shortages(w);
                for (ItemSpare.Spare sp : ItemSpare.Spare.values()) {
                    if (sp == ItemSpare.Spare.BOGIE_BAD) continue;
                    Object[] x = sh.get(sp);
                    say(s, String.format(Locale.ROOT, "§7%-34s stock %3d · on order %3d%s", sp.label, st[sp.ordinal()], Procurement.onOrder(d, sp.ordinal()), x == null ? "" : " §c· SHORT " + x[0] + " (" + x[1] + ")"));
                }
            }
            case "pos" -> { for (AssetData.Order o : d.orders) say(s, po(w, o)); if (d.orders.isEmpty()) say(s, "§7No purchase orders"); }
            case "po" -> {
                ItemSpare.Spare sp = spare(arg(a, 2));
                if (sp == null) { say(s, "§cSpare? " + Arrays.toString(ItemSpare.Spare.values())); return; }
                int qty = a.length > 3 ? Integer.parseInt(a[3]) : 1;
                int sup = a.length > 4 ? Math.max(0, Arrays.asList(AssetData.SUPPLIERS).indexOf(a[4].toUpperCase(Locale.ROOT))) : 0;
                say(s, "§a" + AssetConsole.op(w, 3, sp.ordinal() + ":" + qty + ":" + sup, s.getName()));
            }
            case "cancel" -> say(s, "§e" + AssetConsole.op(w, 4, arg(a, 2), s.getName()));
            case "expedite" -> say(s, "§e" + AssetConsole.op(w, 5, arg(a, 2), s.getName()));
            case "thermal" -> {
                GridData g = GridData.get(w);
                int n = 0;
                for (Map.Entry<Long, GridData.Node> e : g.nodes().entrySet()) {
                    GridData.Node x = e.getValue();
                    if (x.kind != GridKind.TRANSFORMER && x.kind != GridKind.TRACTION && x.kind != GridKind.MOTOR) continue;
                    BlockPos p = BlockPos.fromLong(e.getKey());
                    say(s, "§7" + x.kind.label + " " + p.getX() + " " + p.getY() + " " + p.getZ() + ": " + Thermal.model(x));
                    n++;
                }
                if (n == 0) say(s, "§7No transformers or motors on the grid");
                say(s, String.format(Locale.ROOT, "§8IEC 60255-149 trip time at 1.2x / 1.5x / 2x from cold, τ 25 min: %.1f / %.1f / %.1f min", Thermal.tripMinutes(1.2, 0, 25, 1.05), Thermal.tripMinutes(1.5, 0, 25, 1.05), Thermal.tripMinutes(2, 0, 25, 1.05)));
            }
            case "fleet" -> {
                for (FleetData.Unit u : FleetData.get(w).units().values()) {
                    if (!u.loco || u.name.isEmpty()) continue;
                    StringBuilder b = new StringBuilder();
                    for (int c = 0; c < 6; c++) { double v = Telemetry.last(u, c), sl = Telemetry.slope(u, c); b.append(Telemetry.CH[c]).append(' ').append(Double.isNaN(v) ? "-" : String.valueOf(Math.round(v))).append(Double.isNaN(sl) ? "" : String.format(Locale.ROOT, "(%+.1f/h)", sl)).append("  "); }
                    say(s, "§b" + u.name + "§7 · " + u.tele.size() + " samples · RUL " + (Telemetry.rulHours(u) < 0 ? "-" : String.format(Locale.ROOT, "%.1f h", Telemetry.rulHours(u))) + " · " + b);
                    for (String c : u.pcodes) say(s, "  §e" + c);
                }
            }
            case "certs" -> { for (AssetData.Cert c : d.certs) { say(s, (c.pass ? "§a" : "§c") + "#" + c.id + " " + c.kind + " " + c.scope + " (" + c.by + ")"); for (String l : c.lines) say(s, "§8  " + l); } }
            case "bench" -> {
                BlockPos p = new BlockPos(Integer.parseInt(arg(a, 2)), Integer.parseInt(arg(a, 3)), Integer.parseInt(arg(a, 4)));
                Object[] r = com.dogpound.railmap.grid.Bench.test(w, p);
                @SuppressWarnings("unchecked") List<String> l = (List<String>) r[1];
                for (String x : l) say(s, (x.startsWith("FAIL") ? "§c" : x.startsWith("PASS") ? "§a" : "§7") + x);
                d.addCert(w, "IED " + p.getX() + "," + p.getZ(), "SECONDARY INJECTION", (Boolean) r[0], l, s.getName());
            }
            case "tool" -> {
                if (!(s instanceof EntityPlayer pl)) return;
                for (Ndt.Tool t : Ndt.Tool.values()) pl.inventory.addItemStackToInventory(new net.minecraft.item.ItemStack(AssetBlocks.ndt, 1, t.ordinal()));
                say(s, "§aThe five inspection instruments");
            }
            case "core" -> {
                if (!(s instanceof EntityPlayer pl)) return;
                ItemSpare.Spare sp = spare(arg(a, 2));
                float c = a.length > 3 ? Float.parseFloat(a[3]) : 40;
                pl.inventory.addItemStackToInventory(ItemSpare.core(sp == null ? ItemSpare.Spare.TRACTION_MOTOR : sp, c, "test"));
                say(s, "§aA core for the Remanufacturing Bench");
            }
            case "age" -> {
                AssetData.Asset x = d.byId(arg(a, 2));
                if (x == null) { say(s, "§cNo asset " + arg(a, 2)); return; }
                x.use += x.kind.life / Math.max(0.01, AssetConfig.ageingRate) * Double.parseDouble(arg(a, 3)) / 100.0;
                AssetTicker.condition(w, d, x, now);
                say(s, "§e" + x.id + " now " + Math.round(x.lifeUsed() * 100) + "% of its life used, condition " + Math.round(x.cond) + "%");
            }
            case "seed" -> {
                AssetData.Asset x = d.byId(arg(a, 2));
                if (x == null) { say(s, "§cNo asset " + arg(a, 2)); return; }
                double size = a.length > 3 ? Double.parseDouble(a[3]) : x.kind.crit * 0.5;
                AssetData.Defect df = d.addDefect(w, x.id, x.pos, x.kind.defect, x.kind.defectUnit, size, x.kind.crit);
                say(s, "§eLatent defect #" + df.id + " (" + size + " " + x.kind.defectUnit + ") seeded in " + x.id + " - find it with the " + x.kind.tool.label);
            }
            case "comm" -> {
                AssetData.Asset x = d.byId(arg(a, 2));
                if (x == null) { say(s, "§cNo asset " + arg(a, 2)); return; }
                boolean done = arg(a, 3).equalsIgnoreCase("done");
                x.comm = done ? AssetData.COMMISSIONED : AssetData.UNCOMM;
                x.commBy = done ? s.getName() + " (command)" : "";
                x.note(w, done ? "commissioned by command" : "set UNCOMMISSIONED by command");
                d.markDirty();
                say(s, "§e" + x.id + " " + AssetData.COMM[x.comm]);
            }
            case "selftest" -> selftest(w, s);
            default -> say(s, "§7asset list|info|near|defects|order|repair|stock|pos|po|cancel|expedite|thermal|fleet|certs|bench|tool|core|age|seed|comm|selftest");
        }
    }

    static String arg(String[] a, int i) { return a.length > i ? a[i] : ""; }

    static ItemSpare.Spare spare(String s) {
        for (ItemSpare.Spare sp : ItemSpare.Spare.values()) if (sp.name().equalsIgnoreCase(s) || sp.id.equalsIgnoreCase(s)) return sp;
        return null;
    }

    static String line(AssetData.Asset x, long now) {
        int b = AssetTicker.band(x.cond);
        String c = b == 0 ? "§a" : b == 1 ? "§e" : b == 2 ? "§6" : "§c";
        return String.format(Locale.ROOT, "%s%s§7 %s %s · %s%d%%§7 %s · life %d%% · %s%s", c, x.id, x.kind.label, x.name, c, Math.round(x.cond), AssetTicker.BANDS[b], Math.round(x.lifeUsed() * 100),
                AssetData.COMM[x.comm], x.overdue(now) ? " §6· INSPECTION OVERDUE" : "");
    }

    static String defect(AssetData.Defect df) {
        return String.format(Locale.ROOT, "%s#%d %s§7 %s on %s · %s · crit %.0f %s · %s", df.seen >= df.crit ? "§c" : "§e", df.id, AssetData.STAGES[df.stage], df.type, df.asset,
                df.stage == AssetData.LATENT ? "size unknown" : String.format(Locale.ROOT, "%.1f ± %.1f %s", df.seen, df.unc, df.unit), df.crit, df.unit, df.method);
    }

    static String po(World w, AssetData.Order o) {
        ItemSpare.Spare sp = ItemSpare.Spare.values()[Math.min(ItemSpare.Spare.values().length - 1, o.spare)];
        return String.format(Locale.ROOT, "%sPO #%d§7 %d x %s · %s · %s · %s · $%.0f · %s", o.state == 0 ? "§e" : o.state == 1 ? "§a" : "§8", o.id, o.qty, sp.label, AssetData.SUPPLIERS[o.supplier],
                AssetData.PO_STATES[o.state], Procurement.eta(w, o), o.cost, o.why);
    }

    /** the maths behind these systems, checked: PASS / FAIL lines */
    static void selftest(World w, ICommandSender s) {
        int pass = 0, fail = 0;
        List<String> out = new ArrayList<>();
        // POD rises with defect size and never beats the coverage
        boolean ok = true;
        for (Ndt.Tool t : Ndt.Tool.values()) {
            double prev = -1;
            for (double a = 0; a <= t.a50 * 5; a += t.a50 / 4) { double p = Ndt.pod(t, a); if (p < prev - 1e-12 || p > t.coverage + 1e-12) ok = false; prev = p; }
        }
        out.add((ok ? "PASS" : "FAIL") + " POD monotonic in size, capped at coverage"); if (ok) pass++; else fail++;
        // IEC 60076-7 ageing: 1.0 at 98 °C, doubles every 6 K
        ok = Math.abs(Thermal.ageing(98) - 1) < 1e-9 && Math.abs(Thermal.ageing(104) - 2) < 1e-9 && Math.abs(Thermal.ageing(92) - 0.5) < 1e-9;
        out.add((ok ? "PASS" : "FAIL") + " relative ageing V(98)=1, V(104)=2, V(92)=0.5"); if (ok) pass++; else fail++;
        // IEC 60255-149: no trip at or under k·IB, faster for more current, preload trips sooner
        double t12 = Thermal.tripMinutes(1.2, 0, 25, 1.05), t20 = Thermal.tripMinutes(2.0, 0, 25, 1.05), t12h = Thermal.tripMinutes(1.2, 0.9, 25, 1.05);
        ok = Thermal.tripMinutes(1.0, 0, 25, 1.05) < 0 && t20 < t12 && t12h < t12 && t12 > 0;
        out.add(String.format(Locale.ROOT, "%s thermal overload curve (1.2x %.1f min, 2x %.1f min, 1.2x hot %.1f min)", ok ? "PASS" : "FAIL", t12, t20, t12h)); if (ok) pass++; else fail++;
        // remanufacturing grades
        ok = Reman.grade(80) == 0 && Reman.grade(55) == 0 && Reman.grade(54.9) == 1 && Reman.grade(30) == 1 && Reman.grade(20) == 2 && Reman.grade(5) == 3;
        out.add((ok ? "PASS" : "FAIL") + " remanufacturing grade bands A 55+ / B 30-55 / C 10-30 / SCRAP"); if (ok) pass++; else fail++;
        // condition curve: new = 100, end of life well under 20
        AssetData.Asset x = new AssetData.Asset();
        x.kind = AssetKind.TRACK;
        double life = x.kind.life / Math.max(0.01, AssetConfig.ageingRate);
        x.use = 0; float c0 = cond(w, x); x.use = life * 0.5; float c5 = cond(w, x); x.use = life; float c10 = cond(w, x);
        ok = c0 > 99 && c5 < c0 && c10 < 20 && c10 < c5;
        out.add(String.format(Locale.ROOT, "%s life curve: new %.0f%%, half life %.0f%%, end %.0f%%", ok ? "PASS" : "FAIL", c0, c5, c10)); if (ok) pass++; else fail++;
        // design sheet: as built = 100 % power, the heavy freight gear caps speed
        FleetData.Unit u = new FleetData.Unit();
        u.rebuild = 1; u.baseKmh = 120; u.design[1] = 1; u.cap = Float.MAX_VALUE;
        Design.apply(u);
        ok = Math.abs(Design.speedKmh(u) - 78) < 0.5 && (!AssetConfig.rebuildAcceptance || u.cap == 25);
        out.add(String.format(Locale.ROOT, "%s design: 4.93 gear on a 120 km/h loco = %.0f km/h, unaccepted cap %.0f", ok ? "PASS" : "FAIL", Design.speedKmh(u), u.cap)); if (ok) pass++; else fail++;
        // substitution arithmetic never leaves a negative need
        int[] need = new int[ItemSpare.Spare.values().length];
        need[ItemSpare.Spare.BOGIE.ordinal()] = 1;
        Procurement.substitute(w, s.getPosition(), need);
        ok = true; for (int n : need) if (n < 0) ok = false;
        out.add((ok ? "PASS" : "FAIL") + " substitution leaves no negative quantities"); if (ok) pass++; else fail++;
        // the register: ids stable and unique
        Set<String> ids = new HashSet<>();
        ok = true; for (AssetData.Asset y : AssetData.get(w).all()) if (!ids.add(y.id)) ok = false;
        out.add((ok ? "PASS" : "FAIL") + " asset ids unique (" + ids.size() + ")"); if (ok) pass++; else fail++;
        for (String l : out) say(s, (l.startsWith("PASS") ? "§a" : "§c") + l);
        say(s, "§b" + pass + " pass, " + fail + " fail");
    }

    private static float cond(World w, AssetData.Asset x) {
        AssetData scratch = new AssetData("scratch");
        x.id = "TEST"; x.cond = 100; x.measured = -1;
        AssetTicker.condition(w, scratch, x, 0);
        return x.cond;
    }
}
