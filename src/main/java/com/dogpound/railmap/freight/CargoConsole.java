package com.dogpound.railmap.freight;

import com.dogpound.railmap.biz.BizData;
import com.dogpound.railmap.biz.Custody;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

import static com.dogpound.railmap.stn.StnConsole.*;

/** STN tab pages for cargo care (batch 3 #21-24): Reefers, Boxes, Washout, Weigh. Ops arrive without the "cargo." prefix. */
public final class CargoConsole {
    private CargoConsole() {}

    public static void fill(World w, List<String[]> out) {
        long now = w.getTotalWorldTime();
        // ---- reefers
        page(out, "Reefers", "Every reefer wagon and plugged reefer container: box temperature, setpoint, cargo quality, power source, genset fuel");
        cols(out, "Unit:0", "Temp:28", "Set:38", "Quality:46", "Power:57", "Fuel L:67", "Excursion:76", "State:88");
        btn(out, "COLDER", "cargo.colder", true); btn(out, "WARMER", "cargo.warmer", true); btn(out, "ACK", "cargo.ack", true);
        for (Reefer.Unit u : Reefer.get(w).units.values()) {
            if (out.size() > 600) break;
            String q = u.food ? (u.quality < CargoConfig.coldChainFindingPct ? "!" : u.quality < 95 ? "~" : "+") + Math.round(u.quality) + "%" : ".empty";
            out.add(new String[]{"ROW", u.key, (u.key.startsWith("box:") ? "BOX " : "") + u.name, (u.temp > u.set + 3 ? "!" : u.temp > u.set + 2 ? "~" : "") + String.format(Locale.ROOT, "%+.1f C", u.temp),
                    String.format(Locale.ROOT, "%+.0f", u.set), q, (u.power.equals("OFF") ? "!" : "") + u.power, u.fuel < 0 ? "-" : String.format(Locale.ROOT, "%.0f", u.fuel),
                    u.excursionS / 60 + " min", u.alarm ? (u.acked ? "~ALARM (ack)" : "!ALARM") : now - u.lastSeen > 200 ? ".not seen" : "+ok"});
        }
        txt(out, "Wagons are reefers when their name says reefer / refrigerated / frigo / chiller. SHORE = plug post, HEP = from a loco in the train.");
        // ---- containers
        Custody cu = BizData.get(w).custody;
        page(out, "Boxes", "Container tracking: ISO 6346 number + check digit, status, seal, last seen where and how, holder, condition");
        cols(out, "Container:0", "Chk:15", "Status:20", "Last:30", "Place:42", "Holder:57", "Seal:70", "Clean:78", "Cold Q:85", "Flag:92");
        btn(out, "TRACE", "cargo.trace", true); btn(out, "CLEAR FLAG", "cargo.clear", true);
        List<Custody.Rec> recs = new ArrayList<>(cu.recs.values());
        recs.sort((a, b) -> Long.compare(b.dwellSince, a.dwellSince));
        int n = 0;
        for (Custody.Rec r : recs) {
            if (n++ >= 80) break;
            boolean k = r.id.startsWith("K");
            out.add(new String[]{"ROW", r.id, r.id, k ? ".cons." : Custody.validId(r.id) ? "+OK" : "!BAD", (r.status.equals("OK") || r.status.equals("CLEARED") ? "" : "!") + r.status,
                    r.lastMode, r.lastPlace, r.lastHolder, r.seal == 0 ? "-" : (r.sealBroken ? "!" : "") + r.seal, (r.clean < 60 ? "!" : "") + Math.round(r.clean) + "%",
                    r.reefer ? (r.coldQ < CargoConfig.coldChainFindingPct ? "!" : "") + Math.round(r.coldQ) + "%" : ".-", r.contam.isEmpty() ? ("HAZ".equals(r.lastLoad) ? "~HAZ last" : "") : "!CONTAM"});
        }
        // ---- washout
        page(out, "Washout", "Washout plants (select one: WASH / STOP) and every wagon's cleanliness, last load and STEAM certificate");
        cols(out, "Plant / wagon:0", "State:30", "Clean:62", "Last load:72", "Certificate:84");
        btn(out, "WASH", "cargo.wash", true); btn(out, "STOP", "cargo.stop", true);
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList))
            if (te instanceof TileWashoutPlant p && !te.isInvalid())
                out.add(new String[]{"ROW", "P" + te.getPos().toLong(), "+PLANT " + te.getPos().getX() + "," + te.getPos().getZ(), p.status(), p.bayCar().isEmpty() ? "" : ".bay: " + p.bayCar(), "", ""});
        Set<UUID> cars = new LinkedHashSet<>(cu.cars.keySet());
        cars.addAll(cu.carLast.keySet());
        n = 0;
        for (UUID c : cars) {
            if (n++ >= 60) break;
            double cl = cu.cars.getOrDefault(c, 100.0);
            String last = cu.carLast.getOrDefault(c, "?");
            out.add(new String[]{"ROW", "", ".wagon " + c.toString().substring(0, 8), "", (cl < 60 ? "!" : "") + Math.round(cl) + "%", (last.equals("HAZ") ? "!" : "") + last,
                    Custody.certValid(cu.carCert.getOrDefault(c, 0L), now) ? "+VALID" : ".none"});
        }
        // ---- weighbridges
        page(out, "Weigh", "Axle-load weighbridge readings, newest first: per-axle loads, imbalance and the verdict");
        cols(out, "Time:0", "Vehicle:9", "Axles:30", "Max t:36", "Limit:43", "Side %:50", "End %:57", "Verdict:64");
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList))
            if (te instanceof TileAxleWeighbridge b && !te.isInvalid())
                for (String l : b.log) {
                    String[] p = l.split("\\|");
                    if (p.length < 8) continue;
                    out.add(new String[]{"ROW", "", p[0], p[1], p[2], p[3], p[4], p[5], p[6], (p[7].equals("OK") ? "+" : "!") + p[7]});
                }
        txt(out, String.format(Locale.ROOT, "Config limits: axle %.1f t, side %d %%, end %d %%. Weighbridges stop trains only with their HOLD key or the harsh config switch.",
                CargoConfig.axleLimitT, CargoConfig.lateralPct, CargoConfig.longitudinalPct));
        // ---- yard gantry cranes (batch 5 #1)
        page(out, "Gantries", "Yard gantry cranes (RMG): state, supply + load, wind, the box on the spreader, jobs; select one for PLAN / AUTO / STOP / E-STOP");
        cols(out, "Crane:0", "State:16", "Supply:30", "kW:50", "Wind:56", "Boxes:63", "Jobs:70", "Spreader:76");
        btn(out, "PLAN", "cargo.rmg.plan", true); btn(out, "AUTO", "cargo.rmg.k_auto", true); btn(out, "STOP", "cargo.rmg.stop", true); btn(out, "E-STOP", "cargo.rmg.m_estop", true); btn(out, "ACK", "cargo.rmg.ack", true);
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList))
            if (te instanceof TileYardGantry g && !te.isInvalid()) {
                String st = g.estop() ? "!E-STOP" : g.storm() ? "!STORM PINS" : !g.fault().isEmpty() ? "!" + g.fault() : g.running() ? (g.busy() ? "~WORKING" : "+READY") : ".off";
                out.add(new String[]{"ROW", "G" + te.getPos().toLong(), g.name() + " " + te.getPos().getX() + "," + te.getPos().getZ(), st, g.gearState().split(" · ")[1], String.format(Locale.ROOT, "%.0f", g.kw()),
                        (g.wind() > GantryConfig.windLimitMs * 0.8 ? "!" : "") + String.format(Locale.ROOT, "%.0f m/s", g.wind()), String.valueOf(g.boxes().size()), String.valueOf(g.jobs.size()),
                        g.carrying.isEmpty() ? "." : com.dogpound.railmap.item.ItemContainer.pretty(g.carrying)});
                for (String[] b : g.boxes()) out.add(new String[]{"ROW", "", ".  " + b[0], b[1], b[2], "", "", "", "", b[3]});
            }
        txt(out, String.format(Locale.ROOT, "Wind limit %.0f m/s (half speed over 80 %%), SWL %.0f t incl. the 10.5 t spreader. Spreader cameras are on the CCTV system.", GantryConfig.windLimitMs, GantryConfig.swlT));
    }

    public static String op(EntityPlayer p, String op, String arg) {
        World w = p.world;
        Reefer r = Reefer.get(w);
        Custody cu = BizData.get(w).custody;
        switch (op) {
            case "colder", "warmer" -> {
                Reefer.Unit u = r.units.get(arg);
                if (u == null) return "§7Select a reefer";
                u.set = Math.max(-30, Math.min(20, u.set + (op.equals("colder") ? -1 : 1)));
                r.markDirty();
                return String.format(Locale.ROOT, "§a%s setpoint %+.0f C", u.name, u.set);
            }
            case "ack" -> {
                Reefer.Unit u = r.units.get(arg);
                if (u == null || !u.alarm) return "§7No alarm on that unit";
                u.acked = true;
                return "§eAcknowledged " + u.name;
            }
            case "trace" -> {
                Custody.Rec c = cu.recs.get(arg);
                if (c == null) return "§7Select a container";
                trace(p, c, w);
                return "";
            }
            case "clear" -> {
                Custody.Rec c = cu.recs.get(arg);
                if (c == null) return "§7Select a container";
                if (c.status.equals("OK")) return "§7Nothing to clear";
                c.status = "CLEARED";
                cu.event(w, c, "CLEAR", null, p.getName(), "flag cleared from the console");
                return "§a" + c.id + " cleared";
            }
            case "wash", "stop" -> {
                if (!arg.startsWith("P")) return "§7Select a washout plant";
                TileEntity te = w.getTileEntity(BlockPos.fromLong(Long.parseLong(arg.substring(1))));
                if (!(te instanceof TileWashoutPlant pl)) return "§7That plant is gone";
                return pl.remote(op);
            }
            default -> {
                if (op.startsWith("rmg.") && arg.startsWith("G")) {                     // yard gantry (batch 5 #1)
                    TileEntity te = w.getTileEntity(BlockPos.fromLong(Long.parseLong(arg.substring(1))));
                    if (!(te instanceof TileYardGantry g)) return "§7That crane is gone";
                    String id = op.substring(4);
                    if (id.equals("plan")) return "§a" + g.plan() + " job(s) planned at " + g.name();
                    return com.dogpound.railmap.controls.IControlled.operate(g, id, p);
                }
                return "§7Unknown cargo op " + op;
            }
        }
    }

    static void trace(net.minecraft.command.ICommandSender s, Custody.Rec c, World w) {
        s.sendMessage(new net.minecraft.util.text.TextComponentString("§e" + c.id + (Custody.validId(c.id) ? " §a(check digit OK)" : c.id.startsWith("K") ? " §7(contract consignment)" : " §c(check digit BAD)")
                + " §7" + c.status + " · owner " + c.owner + " · to " + c.consignee + " · last " + c.lastMode + " @ " + c.lastPlace));
        for (String e : c.events) {
            String[] p = e.split("\\|", 5);
            if (p.length == 5) s.sendMessage(new net.minecraft.util.text.TextComponentString("§8 " + com.dogpound.railmap.alarm.Recorder.clock(w, Long.parseLong(p[0])) + " §7" + p[1] + " §f" + p[2] + " §7" + p[3] + ": " + p[4]));
        }
    }
}
