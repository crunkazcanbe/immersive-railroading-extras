package com.dogpound.railmap.newrail6;

import cam72cam.immersiverailroading.entity.Locomotive;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras rack list|tag|untag|wear|selftest · ferry list|route N|sail N|selftest · motorail list|recover N|selftest ·
 * garden list|selftest · avalanche list|trip|blast|selftest · nr6 selftest (all five). The panels do all of this with
 * their buttons; these are for testing and inspection.
 */
public final class NR6Command {
    private NR6Command() {}

    static final Set<String> SUBS = new HashSet<>(Arrays.asList("rack", "ferry", "motorail", "garden", "avalanche", "nr6"));

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !SUBS.contains(a[0].toLowerCase(Locale.ROOT))) return false;
        String what = a[0].toLowerCase(Locale.ROOT), sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (sub.equals("selftest")) {
            List<String> out = new ArrayList<>();
            int fails = 0;
            for (String k : what.equals("nr6") ? new String[]{"rack", "ferry", "motorail", "garden", "avalanche"} : new String[]{what}) {
                List<String> r = selfTest(k);
                for (String l : r) if (l.startsWith("§cFAIL")) fails++;
                out.addAll(r);
            }
            say(s, fails == 0 ? "§a§l" + what.toUpperCase(Locale.ROOT) + " SELF-TEST PASSED§r§a (" + out.size() + " checks)" : "§c§l" + what.toUpperCase(Locale.ROOT) + " SELF-TEST: " + fails + " FAILED");
            for (String l : out) say(s, l);
            return true;
        }
        NR6Data d = NR6Data.get(w);
        BlockPos at = s.getPosition();
        switch (what) {
            case "rack" -> {
                if (sub.equals("tag") || sub.equals("untag")) {
                    Locomotive l = Rack.nearestLoco(w, at, 16);
                    if (l == null) { say(s, "§cNo locomotive within 16 blocks"); return true; }
                    if (sub.equals("tag")) d.cog.add(l.getUUID()); else d.cog.remove(l.getUUID());
                    d.changed();
                    say(s, "§7" + Rack.name(l) + (sub.equals("tag") ? " is now a cog locomotive" : " is no longer a cog locomotive"));
                    return true;
                }
                if (sub.equals("wear") && a.length > 2) {
                    Locomotive l = Rack.nearestLoco(w, at, 16);
                    if (l == null) { say(s, "§cNo locomotive within 16 blocks"); return true; }
                    d.wear.put(l.getUUID(), Float.parseFloat(a[2])); d.changed();
                    say(s, "§7" + Rack.name(l) + " pinion wear set to " + a[2] + " %");
                    return true;
                }
                say(s, "§b" + d.cog.size() + " cog locomotive(s) tagged · " + Rack.on(w).size() + " locos on rack now · " + NR6Tile.live(w, TileRackPanel.class).size() + " rack panels loaded");
                for (Map.Entry<UUID, Integer> e : Rack.on(w).entrySet()) say(s, "§7  " + e.getKey().toString().substring(0, 8) + " on " + (e.getValue() == 2 ? "entry section" : "rack") + " wear " + Math.round(d.wear(e.getKey())) + " %");
            }
            case "ferry" -> {
                NR6Data.Route r = a.length > 2 ? d.routes.get(parse(a[2])) : null;
                if (sub.equals("sail") && r != null) {                         // test: sail now, ignoring the dock interlocks
                    if (!r.paired() || !r.berths()) { say(s, "§cRoute not paired / berths not found"); return true; }
                    r.phase = NR6Data.DEPARTING; r.phaseAt = w.getTotalWorldTime(); r.doorOpen = false; d.changed();
                    say(s, "§6Route " + r.ch + " casting off (test)");
                    return true;
                }
                if (sub.equals("route") && r != null) {
                    say(s, String.format(Locale.ROOT, "§bRoute %d: %s at %s, %.0f m, s %.1f v %.1f, %d voyages, door %s", r.ch, NR6Data.PHASE[r.phase], r.at == 0 ? "A" : "B", Ferry.length(r), r.s, r.v, r.voyages, r.doorOpen ? "open" : "shut"));
                    for (int i = 0; i < r.deck.size(); i++) say(s, "§7  deck " + (i + 1) + ": " + r.deck.get(i).getString("name") + " (" + r.deck.get(i).getString("def") + ")" + (r.deck.get(i).hasKey("cargo") ? " + cargo" : ""));
                    return true;
                }
                if (d.routes.isEmpty()) say(s, "§7No ferry routes - place two Ferry Docks on the same ROUTE number");
                for (NR6Data.Route x : d.routes.values())
                    say(s, String.format(Locale.ROOT, "§b#%d §7%s · %s · %.0f m · %d cars on deck · %d voyages", x.ch, x.paired() ? "paired" : "waiting for dock " + (x.dockA == 0 ? "A" : "B"),
                            NR6Data.PHASE[x.phase], Ferry.length(x), x.deck.size(), x.voyages));
            }
            case "motorail" -> {
                if (sub.equals("recover") && a.length > 2) {                    // a load whose wagon was scrapped: drive it off at the nearest ramp
                    int n = parse(a[2]);
                    List<UUID> keys = new ArrayList<>(d.loads.keySet());
                    if (n < 1 || n > keys.size()) { say(s, "§cNo load #" + n); return true; }
                    TileMotorailRamp ramp = null;
                    double bd = 64 * 64;
                    for (TileMotorailRamp t : NR6Tile.live(w, TileMotorailRamp.class)) if (t.getPos().distanceSq(at) < bd) { bd = t.getPos().distanceSq(at); ramp = t; }
                    if (ramp == null) { say(s, "§cNo motorail ramp within 64 blocks"); return true; }
                    NR6Data.Load ld = d.loads.get(keys.get(n - 1));
                    BlockPos p = ramp.getPos().offset(ramp.front(), 4);
                    Entity v = Motorail.ivPresent() ? net.minecraft.entity.EntityList.createEntityFromNBT(Motorail.moved(ld.veh, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, ramp.front().getHorizontalAngle()), w) : null;
                    if (v == null || !w.spawnEntity(v)) { say(s, "§cCould not restore it (Immersive Vehicles missing or pack gone)"); return true; }
                    d.loads.remove(keys.get(n - 1)); d.changed();
                    say(s, "§a" + ld.label + " recovered at the ramp");
                    return true;
                }
                say(s, "§b" + d.loads.size() + " vehicle(s) on motorail wagons" + (Motorail.ivPresent() ? "" : " §c(Immersive Vehicles not installed)") + (Motorail.LOST > 0 ? " §7- " + Motorail.LOST + " on wagons not loaded right now" : ""));
                int i = 1;
                for (Map.Entry<UUID, NR6Data.Load> e : d.loads.entrySet()) say(s, "§7  " + (i++) + ". " + e.getValue().label + " on wagon " + e.getKey().toString().substring(0, 8));
            }
            case "garden" -> {
                List<EntityGardenTrain> ts = w.getEntities(EntityGardenTrain.class, x -> true);
                say(s, "§b" + ts.size() + " garden train(s) loaded · " + NR6Tile.live(w, TileGardenStation.class).size() + " stations");
                for (EntityGardenTrain t : ts) say(s, "§7  " + t.line() + " at " + (int) t.posX + "," + (int) t.posZ + " riders " + t.getPassengers().size());
            }
            case "avalanche" -> {
                List<TileAvalancheDetector> ds = NR6Tile.live(w, TileAvalancheDetector.class);
                TileAvalancheDetector near = null;
                double bd = 96 * 96;
                for (TileAvalancheDetector t : ds) if (t.getPos().distanceSq(at) < bd) { bd = t.getPos().distanceSq(at); near = t; }
                if (sub.equals("trip") && near != null) { near.trip("TEST TRIP by command"); say(s, "§6Tripped " + near.wiredName()); return true; }
                if (sub.equals("blast") && near != null) { say(s, near.release("controlled release (command)")); return true; }
                say(s, "§b" + ds.size() + " avalanche detector(s) loaded");
                for (TileAvalancheDetector t : ds) say(s, "§7  " + t.wiredName() + ": " + t.gearState());
            }
            default -> say(s, "§c/irextras " + what + " list|selftest");
        }
        return true;
    }

    static int parse(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; } }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    static void check(List<String> out, String what, boolean ok) { out.add((ok ? "§aPASS §7" : "§cFAIL §7") + what); }

    // ---- self-tests: the maths without a world ------------------------------------------------------------------------
    public static List<String> selfTest(String k) {
        List<String> o = new ArrayList<>();
        switch (k) {
            case "rack" -> {
                check(o, "rack: grade of a 10 degree pitch is 17.6 %", Math.abs(Rack.grade(10) - 17.63) < 0.05);
                check(o, "rack: clean entry at 5 km/h through an entry section (limit 10)", Rack.entry(2, 5, 10) == 0);
                check(o, "rack: hard entry at 18 km/h", Rack.entry(2, 18, 10) == 1);
                check(o, "rack: running on where there is no entry section strikes the teeth", Rack.entry(1, 8, 10) == 2 && Rack.entry(1, 1, 10) == 0);
                check(o, "rack: lubricated pinions wear 4x slower", Math.abs(Rack.wearFor(10, false) / Rack.wearFor(10, true) - 0.5 / 0.12) < 1e-3);
                check(o, "rack: 200 km dry wears a pinion out", Rack.wearFor(200, false) >= 100f);
            }
            case "ferry" -> {
                NR6Data.Route r = new NR6Data.Route();
                r.dockA = 1; r.dockB = 2;
                r.berth[0] = new double[]{0, 62.9, 0}; r.berth[1] = new double[]{0, 62.9, 300};
                r.out[0] = Ferry.yaw(0, 1); r.out[1] = Ferry.yaw(0, -1);
                check(o, "ferry: crossing 300 m", Math.abs(Ferry.length(r) - 300) < 1e-6);
                check(o, "ferry: heading south is yaw 0 (MC)", Math.abs(Ferry.yaw(0, 1)) < 1e-3);
                r.phase = NR6Data.SAILING; r.at = 0;
                double v = 0, t = 0;
                double top = 0;
                r.s = 0;
                while (r.s < 300 - 0.01 && t < 20 * 2000) { v = Ferry.speedFor(v, 300 - r.s); r.s = Math.min(300, r.s + v * 0.05); top = Math.max(top, v); t++; }
                check(o, String.format(Locale.ROOT, "ferry: crossing in %.0f s at up to %.1f m/s, arriving", t / 20, top), r.s >= 300 - 0.01 && top <= NR6Config.ferrySpeed + 1e-6);
                r.s = 150;
                double[] mid = Ferry.pose(r);
                check(o, "ferry: halfway is mid-route", Math.abs(mid[2] - 150) < 1e-6);
                r.s = 299.99;
                double[] end = Ferry.pose(r);
                check(o, "ferry: swings round to lie stern-in at B", Math.abs(((end[3] - r.out[1]) % 360 + 540) % 360 - 180) < 2);
                r.s = 0.01;
                double[] st = Ferry.pose(r);
                check(o, "ferry: leaves A on A's heading (no jump)", Math.abs(((st[3] - r.out[0]) % 360 + 540) % 360 - 180) < 2);
                NBTTagCompound c = new NBTTagCompound();
                r.deck.add(new NBTTagCompound());
                r.deck.get(0).setString("def", "rolling_stock/x.json");
                r.write(c);
                NR6Data.Route b = new NR6Data.Route();
                b.read(c);
                check(o, "ferry: a route with cars on deck saves and loads", b.deck.size() == 1 && b.deck.get(0).getString("def").equals("rolling_stock/x.json") && b.berths() && b.phase == NR6Data.SAILING);
            }
            case "motorail" -> {
                NBTTagCompound v = new NBTTagCompound();
                v.setString("id", "mts:mts_entity"); v.setString("entityid", "EntityVehicleF_Physics");
                v.setDouble("positionx", 1); v.setDouble("positiony", 2); v.setDouble("positionz", 3); v.setDouble("motionx", 4); v.setDouble("anglesy", 10);
                NBTTagCompound m = Motorail.moved(v, 100.5, 64.1, -20.5, 90);
                check(o, "motorail: vehicle moved to the ramp (IV keys + MC Pos)", m.getDouble("positionx") == 100.5 && m.getDouble("positionz") == -20.5 && m.getTagList("Pos", 6).getDoubleAt(1) == 64.1);
                check(o, "motorail: standing still, facing out of the ramp", m.getDouble("motionx") == 0 && m.getDouble("anglesy") == -90);
                check(o, "motorail: the saved original is untouched", v.getDouble("positionx") == 1 && v.getString("entityid").equals("EntityVehicleF_Physics"));
                NR6Data d = new NR6Data();
                NR6Data.Load ld = new NR6Data.Load();
                ld.veh = v; ld.label = "VAN";
                UUID u = UUID.randomUUID();
                d.loads.put(u, ld);
                NR6Data e = new NR6Data();
                e.readFromNBT(d.writeToNBT(new NBTTagCompound()));
                check(o, "motorail: a load saves and loads with its wagon", e.loads.containsKey(u) && e.loads.get(u).label.equals("VAN") && e.loads.get(u).veh.getString("entityid").equals("EntityVehicleF_Physics"));
            }
            case "garden" -> {
                check(o, "garden: straight through wins", Garden.shapeFor(f -> f != EnumFacing.WEST) == Garden.Shape.NS);
                check(o, "garden: two sides at right angles make a curve", Garden.shapeFor(f -> f == EnumFacing.SOUTH || f == EnumFacing.EAST) == Garden.Shape.SE);
                check(o, "garden: one neighbour - straight toward it", Garden.shapeFor(f -> f == EnumFacing.EAST) == Garden.Shape.EW);
                double[] a = Garden.local(EnumFacing.WEST, EnumFacing.NORTH, 0), b = Garden.local(EnumFacing.WEST, EnumFacing.NORTH, 1), m = Garden.local(EnumFacing.WEST, EnumFacing.NORTH, 0.5);
                check(o, "garden: curve starts and ends on the side midpoints", Math.abs(a[0]) < 1e-9 && Math.abs(a[1] - 0.5) < 1e-9 && Math.abs(b[0] - 0.5) < 1e-9 && Math.abs(b[1]) < 1e-9);
                check(o, "garden: curve keeps 0.5 m radius about the corner", Math.abs(Math.hypot(m[0], m[1]) - 0.5) < 1e-9);
                check(o, "garden: quarter circle is pi/4 m", Math.abs(Garden.len(EnumFacing.WEST, EnumFacing.NORTH) - Math.PI / 4) < 1e-12);
                Garden.State s = new Garden.State(BlockPos.ORIGIN, EnumFacing.SOUTH, EnumFacing.NORTH, 0.3);
                double[] p = s.pose();
                check(o, "garden: running north is yaw 180", Math.abs(Math.abs(p[3]) - 180) < 1e-3);
            }
            case "avalanche" -> {
                check(o, "avalanche: no snow, no hazard", Avalanche.hazard(0, 38, -5, false) == 0);
                check(o, "avalanche: deep snow on a 38 deg slope is VERY HIGH", Avalanche.level(Avalanche.hazard(900, 38, -5, false)) == 4);
                check(o, "avalanche: flat ground can't slide", Avalanche.hazard(1500, 10, 2, true) == 0);
                check(o, "avalanche: thaw raises it", Avalanche.hazard(400, 38, 3, false) > Avalanche.hazard(400, 38, -5, false));
                check(o, "avalanche: a 70 deg cliff sloughs off (less than 38 deg)", Avalanche.hazard(600, 70, -5, false) < Avalanche.hazard(600, 38, -5, false));
                check(o, "avalanche: no natural release below CONSIDERABLE", Avalanche.releaseChance(0.5) == 0 && Avalanche.releaseChance(1) > 0);
                check(o, "avalanche: vanilla snow estimate grows with cover", Avalanche.vanillaSnow(100, 80, 640) > Avalanche.vanillaSnow(100, 20, 160));
            }
            default -> { }
        }
        return o;
    }
}
