package com.dogpound.railmap.grid;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * /irextras grid ... - inspection and test commands for protection and grid automation (her ChatGPT list section 6):
 *   study x y z [earth]      protection study of a fault there (who trips first, margins, ratings)
 *   inject x y z            secondary-injection test of an IED
 *   log x y z               maintenance history of one part
 *   pq x y z                power quality of the network there
 *   permits                 the live permits to work
 *   fault x y z earth|short (op) put a real fault there - earth = insulation breakdown, short = short circuit now
 *   insul x y z <percent>   (op) set a part's insulation condition (ageing tests)
 *   ied x y z <key> <value> (op) set an IED setting directly (tests)
 */
public final class GridCommands {
    private GridCommands() {}

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    private static BlockPos at(ICommandSender s, String[] a, int i) {
        try { return CommandBase.parseBlockPos(s, a, i, false); } catch (Exception e) { return null; }
    }

    /** true = handled */
    public static boolean run(World w, ICommandSender s, String[] a) {
        if (a.length < 2) return false;
        String c = a[1].toLowerCase(Locale.ROOT);
        GridData d = GridData.get(w);
        boolean op = s.canUseCommand(2, "irextras");
        switch (c) {
            case "help":
                say(s, "§b/irextras grid§7 study|inject|log|pq <x y z> · permits · (op) fault <x y z> earth|short · insul <x y z> <%> · ied <x y z> <key> <value> · set · press · fill");
                return true;
            case "permits": {
                List<Permits.Permit> l = Permits.get(w).live();
                say(s, l.isEmpty() ? "§7No permits to work are live" : "§b" + l.size() + " permit(s) live:");
                for (Permits.Permit p : l) say(s, String.format(Locale.ROOT, "§7#%d %s · %s · issued %s · %d isolation points, %d earths, %d parts in the section",
                        p.id, p.holder, p.what, History.clock(p.issued), p.points.size(), p.earths.size(), p.area.size()));
                return true;
            }
            case "study": case "inject": case "log": case "pq": case "fault": case "insul": case "ied": {
                if (a.length < 5) { say(s, "§cUsage: /irextras grid " + c + " <x> <y> <z> ..."); return true; }
                BlockPos p = at(s, a, 2);
                GridData.Node n = p == null ? null : d.node(p);
                if (n == null) { say(s, "§cNo grid part at that position"); return true; }
                switch (c) {
                    case "study": for (String l : Protection.study(w, p, a.length > 5 && a[5].equalsIgnoreCase("earth"))) say(s, l); return true;
                    case "inject": for (String l : Protection.inject(w, p)) say(s, l); return true;
                    case "log":
                        say(s, "§b" + n.kind.label + " " + p.getX() + " " + p.getY() + " " + p.getZ() + "§7 · " + History.counters(n));
                        for (String l : History.recent(n, 30)) say(s, "§f" + l);
                        return true;
                    case "pq": {
                        GridData.Net net = n.kind.control() ? PowerQuality.measured(d, p.toLong()) : d.netOf(p);
                        if (net == null || net.level == null) { say(s, "§7No real network there"); return true; }
                        say(s, String.format(Locale.ROOT, "§bNetwork #%d %s§7 · U %.1f%% · F %.2f Hz · VUF %.2f%% · THDv %.2f%% · THDi %.1f%% · ripple %.2f%% · PF %.2f · S %s · fault level %s %s",
                                net.id, net.level.label, net.puSource * 100, net.level.ac ? net.level.hz * net.hzPu : 0, net.vuf, net.thdV, net.thdI, net.ripple, net.pf, Elec.si(net.sVA, "VA"), Elec.si(net.faultVA, "VA"), net.resonance));
                        return true;
                    }
                    default: break;
                }
                if (!op) { say(s, "§cNeeds op"); return true; }
                if (c.equals("fault")) {
                    boolean earth = a.length <= 5 || !a[5].equalsIgnoreCase("short");
                    GridData.Net net = d.netOf(p);
                    if (earth) { n.insul = 0.2f; n.efault = (byte) (net != null && net.level != null && !net.level.ac && w.rand.nextBoolean() ? 2 : 1); d.touch(); History.log(w, p, "FAULT", "earth fault put here by command"); say(s, "§eEarth fault at " + n.kind.label + " - it acts next second"); }
                    else if (net == null || net.level == null || !net.problem.isEmpty()) say(s, "§7That point is dead - a short there draws nothing");
                    else { boolean t = Protection.clear(w, d, net, p.toLong(), net.isc, false, "short circuit (command)"); Protection.flush(); say(s, t ? "§cShort circuit cleared - see the alarm / log" : "§4Nothing cleared it!"); }
                    return true;
                }
                if (c.equals("insul")) {
                    if (a.length < 6) { say(s, "§cUsage: insul <x y z> <percent>"); return true; }
                    try { n.insul = Math.max(0, Math.min(1, Integer.parseInt(a[5]) / 100f)); if (n.insul >= 0.25f) n.efault = 0; d.touch(); say(s, "§7Insulation " + Math.round(n.insul * 100) + "% (" + Insulation.megohms(n) + ")"); }
                    catch (NumberFormatException e) { say(s, "§cNot a number"); }
                    return true;
                }
                if (a.length < 7) { say(s, "§cUsage: ied <x y z> <key> <value>  (keys: is curve tms hi hid efon efs efd dif h2 uv uvd ov ovd uf of ufd bf dcef aux test)"); return true; }
                try {
                    int v = Integer.parseInt(a[6]);
                    if (a[5].equals("test")) n.st.put("test", v);
                    else if (w.isBlockLoaded(p) && w.getTileEntity(p) instanceof TileGrid t) {
                        GridGear.P q = GridGear.param(a[5]);
                        if (q == null) { say(s, "§cNo setting " + a[5]); return true; }
                        if (q.key.equals("curve")) t.settings().put("curve", Protection.CURVES[Math.max(0, Math.min(4, v))]);
                        else if (q.max == 1 && q.min == 0) t.settings().put(q.key, Boolean.toString(v == 1));
                        else t.settings().put(q.key, Integer.toString(v));
                        GridGear.mirror(t, n);
                    }
                    d.markDirty();
                    say(s, "§7IED " + a[5] + " = " + a[6]);
                } catch (NumberFormatException e) { say(s, "§cNot a number"); }
                return true;
            }
            default: return false;
        }
    }
}
