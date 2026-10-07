package com.dogpound.railmap.grid;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * The railway power grid of one dimension, kept in the world save so it keeps working while chunks are unloaded:
 * every grid block's position + kind, breaker states, stored energy (intakes + batteries), and the networks
 * worked out from them (a breaker joins its two sides only while closed).
 */
public class GridData extends WorldSavedData {
    private static final String NAME = "irextras_grid";

    public static final class Node {
        public GridKind kind;
        public byte breaker;          // 0 closed, 1 open, 2 tripped
        public long energy;           // intake / battery
        public int net = -1;
        public int rating = 2000;     // breaker: FE per tick before it trips
        public String feeds = "Auto"; // feeder: Auto / Beam / Wire / Third rail
        public int range = 64;        // feeder reach
        public boolean ownFirst = true; // feeder: spend its own store (fed by any mod's cables) before the grid
        public float heat;            // transformer winding temperature rise, 0..120
        public boolean hot;           // transformer tripped on overtemperature until it cools below 40
        // ---- real controls (Panel, Requested) - defaults = as-built, so old saves behave as before
        public int tap = 9;           // transformer on-load tap changer 1..17 (9 = nominal, 1.25 % per step)
        public boolean avr;           // transformer automatic voltage regulator (moves the tap by itself)
        public int cool = 1;          // transformer fans: 0 off, 1 auto, 2 on
        public int dcv = 750;         // rectifier DC system voltage selector
        public float speed;           // generator: shaft speed per unit of rated (1.0 = 60 Hz)
        public double genLoad;        // generator: its share of the load / its rating (last solve)
        public double loadVar;        // motor: reactive power it draws right now (magnetising / inrush), var
        public double loadVA;         // motor: apparent power it draws right now (inrush included), VA
        public int octap;             // transformer off-circuit tap link, -2..+2 (2.5 % each), set with a screwdriver, dead only
        public int kA = 25;           // breaker: breaking capacity, kA (a fault bigger than this destroys it)
        public boolean dcOn = true;   // rectifier DC circuit breaker
        public boolean mainOn = true; // intake incomer main switch
        public boolean earthOn = true;// earthing switch handle
        public boolean batOn = true;  // battery inverter started
        public int batMode = 1;       // battery: 0 charge only, 1 auto, 2 discharge only
        public boolean gcb = true;    // generator circuit breaker
        public int steps;             // capacitor bank steps switched in (0..4)
        public boolean capAuto = true;// capacitor bank PF controller in AUTO
        public int relaySet = 100;    // protection relay pick-up, % of the breaker rating
        public int surges;            // surge arrester counter
        // ---- real electricity (requested feature) ------------------------------------------------------------------
        public boolean frontOk;       // built before 10-07: its panel face still connects (so old grids keep working)
        public byte facing = -1;      // horizontal index of the machine's front (set by its tile); back = primary side
        public boolean realSides;     // converters: back = primary, other faces = secondary (false = legacy pass-through)
        public String level = "";     // intake / generator output level, converter SECONDARY level (Elec.Level name)
        public String primary = "";   // converter rated PRIMARY level
        public int kva = 5000;        // converter rating, kVA
        public String spec = "";      // cable: Elec.Spec key ("" = legacy 95 mm² Cu MV)
        public float temp = 20;       // cable conductor temperature °C
        public long drawThis, drawLast;   // feeder: FE drawn through it this / last second
        public int upNet = -1;        // converter: the network on its primary side
        public double amps, pu = 1;   // last solve: current through it, its voltage per unit
        /** a converter whose back face is its primary side */
        public boolean sided() { return realSides && facing >= 0 && kind.converter(); }
        /** a converter passing power right now (power electronics have an output breaker) */
        public boolean passes() { return !kind.electronic() || dcOn; }
        /** the control-panel face, which takes no cable (null = none / unknown / an old build) */
        public EnumFacing panelFace() { return frontOk || facing < 0 || kind == GridKind.CABLE || Panel.of(kind) == null ? null : EnumFacing.byHorizontalIndex(facing); }
        public EnumFacing back() { return EnumFacing.byHorizontalIndex(facing).getOpposite(); }
        public Elec.Level lvl(Elec.Level dflt) { return Elec.Level.byName(level, dflt); }
        public Elec.Spec cable() { return spec.isEmpty() ? Elec.Spec.LEGACY : Elec.Spec.parse(spec); }
        /** does this node put energy into its network right now? (a main switch / generator breaker can cut it off) */
        public boolean feeding() { return kind == GridKind.INTAKE ? mainOn : kind.generator() ? gcb : false; }
    }

    public static final class Net {
        public int id;
        public final List<Long> nodes = new ArrayList<>();
        public boolean intake, transformer, rectifier, traction;
        public boolean capacitor, earthed, arrester, hot;
        public int transformers;      // power + traction transformers: each carries RATING FE/t
        public double volt = 1.0;     // network voltage, per unit (tap changers, load drop, capacitor over-compensation)
        public int dcv = 750;         // DC system voltage the rectifier is set to
        public int steps;             // capacitor steps switched in across the network
        // ---- real electricity
        public Elec.Level level;      // nominal voltage level (null = legacy network, no levels)
        public boolean legacy;        // has a legacy pass-through converter: old rules apply
        public final List<Long> ins = new ArrayList<>();   // converters feeding this network (their secondary side is here)
        public final List<Long> outs = new ArrayList<>();  // converters this network feeds (their primary side touches here)
        public double pW, qVar, sVA, amps, pf = 1, lossW, eff = 1, puSource = 1;   // last solve
        public double faultVA, isc;
        public boolean utility;       // tied to the utility (intake) somewhere upstream: frequency held at 1.0
        public double hzPu = 1;       // frequency per unit: 1 on the utility, the generators' speed on an island   // prospective short-circuit power (VA) and current (A) at this network's bus
        public long energy, capacity;
        public long drawThisSecond, lastSecond, total;
        public String problem = "";
    }

    private final Map<Long, Node> nodes = new HashMap<>();
    private final List<Net> nets = new ArrayList<>();
    private boolean dirty = true;

    public GridData() { super(NAME); }
    public GridData(String n) { super(n); }

    public static GridData get(World world) {
        MapStorage st = world.getPerWorldStorage();
        GridData d = (GridData) st.getOrLoadData(GridData.class, NAME);
        if (d == null) { d = new GridData(); st.setData(NAME, d); }
        return d;
    }

    public Map<Long, Node> nodes() { return nodes; }
    public List<Net> nets() { rebuild(); return nets; }
    public Node node(BlockPos p) { return nodes.get(p.toLong()); }

    public void add(BlockPos p, GridKind k) {
        Node n = nodes.computeIfAbsent(p.toLong(), x -> new Node());
        n.kind = k;
        dirty = true;
        markDirty();
    }

    public void remove(BlockPos p) {
        if (nodes.remove(p.toLong()) != null) { dirty = true; markDirty(); }
    }

    public void touch() { dirty = true; markDirty(); }

    public Net netOf(BlockPos p) {
        rebuild();
        Node n = nodes.get(p.toLong());
        return n == null || n.net < 0 || n.net >= nets.size() ? null : nets.get(n.net);
    }

    /** flood-fill over saved positions only (no chunk access): neighbours in 6 directions; open breakers don't pass */
    void rebuild() {
        if (!dirty) return;
        dirty = false;
        Map<Integer, long[]> keep = new HashMap<>();
        for (Net n : nets) for (long p : n.nodes) keep.putIfAbsent(n.id, new long[]{n.total});
        nets.clear();
        for (Node n : nodes.values()) n.net = -1;
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            if (e.getValue().net >= 0) continue;
            Net net = new Net();
            net.id = nets.size();
            ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(e.getKey());
            e.getValue().net = net.id;
            while (!q.isEmpty()) {
                long cur = q.poll();
                Node cn = nodes.get(cur);
                net.nodes.add(cur);
                switch (cn.kind) {
                    case INTAKE: case DIESEL: case TURBINE: case SOLAR: case WIND:
                        if (!cn.feeding()) break;                       // main switch / generator breaker open
                        net.intake = true; net.energy += cn.energy; net.capacity += cn.kind.capacity; break;
                    case BATTERY: if (cn.batOn) { net.energy += cn.energy; net.capacity += GridKind.BATTERY.capacity; } break;
                    case TRANSFORMER: net.transformer = true; net.transformers++; if (cn.hot) net.hot = true; break;
                    case RECTIFIER: if (cn.dcOn) { net.rectifier = true; net.dcv = cn.dcv; } break;
                    case TRACTION: net.traction = true; net.transformers++; if (cn.hot) net.hot = true; break;
                    case CAPACITOR: net.capacitor = true; net.steps += cn.steps; break;
                    case EARTHING: if (cn.earthOn) net.earthed = true; break;
                    case ARRESTER: net.arrester = true; break;
                    default: break;
                }
                if (cn.kind.machine() && !cn.sided() && cn.kind.converter()) net.legacy = true;
                if (cn.sided()) net.ins.add(cur);                               // its secondary side is this network
                if (opens(cn) && cn.breaker != 0 && cur != e.getKey()) continue;   // open: stop here
                BlockPos bp = BlockPos.fromLong(cur);
                for (EnumFacing f : EnumFacing.values()) {
                    if (cn.sided() && f == cn.back()) continue;                 // a converter's primary side is another network
                    if (f == cn.panelFace()) continue;                          // nothing connects through a control panel
                    long nb = bp.offset(f).toLong();
                    Node nn = nodes.get(nb);
                    if (nn == null) continue;
                    if (f.getOpposite() == nn.panelFace()) continue;
                    if (nn.sided() && f.getOpposite() == nn.back()) {            // we touch that converter's PRIMARY side
                        if (!net.outs.contains(nb)) net.outs.add(nb);
                        continue;
                    }
                    if (nn.net >= 0) continue;
                    if (opens(cn) && cn.breaker != 0) continue;
                    if (opens(nn) && nn.breaker != 0) { nn.net = net.id; net.nodes.add(nb); continue; }
                    nn.net = net.id;
                    q.add(nb);
                }
            }
            nets.add(net);
        }
        for (Net net : nets) for (long c : net.outs) { Node cv = nodes.get(c); if (cv != null) cv.upNet = net.id; }
        for (Net net : nets) levelOf(net);
        for (Net net : nets) net.energy += upstreamEnergy(net, new HashSet<>());
        for (Net net : nets) problemOf(net);
    }

    /** the nominal level of a real network: its sources' / feeding converters' level; a clash is a problem */
    private void levelOf(Net net) {
        if (net.legacy) return;
        for (long p : net.nodes) {
            Node n = nodes.get(p);
            Elec.Level l = null;
            if (n.kind == GridKind.INTAKE && n.mainOn) l = n.lvl(Elec.Level.MV13800);
            else if (n.kind.generator() && n.gcb) l = n.lvl(n.kind == GridKind.SOLAR || n.kind == GridKind.WIND ? Elec.Level.AC480 : Elec.Level.AC480);
            else if (n.sided() && n.passes()) l = n.lvl(n.kind == GridKind.RECTIFIER ? Elec.Level.DC750 : n.kind == GridKind.DCDC ? Elec.Level.DC48 : n.kind == GridKind.TRACTION ? Elec.Level.AC25000 : Elec.Level.AC480);
            if (l == null) continue;
            if (net.level != null && net.level != l) { net.problem = "VOLTAGE CLASH: " + net.level.label + " tied to " + l.label; return; }
            net.level = l;
        }
    }

    /** energy a network can get from upstream through the converters feeding it */
    private long upstreamEnergy(Net net, Set<Integer> seen) {
        if (!seen.add(net.id)) return 0;
        long e = 0;
        for (long c : net.ins) {
            Node cv = nodes.get(c);
            if (cv == null || cv.upNet < 0 || cv.upNet >= nets.size() || !cv.passes()) continue;
            Net up = nets.get(cv.upNet);
            long own = 0;
            for (long p : up.nodes) { Node n = nodes.get(p); if ((n.kind.source() && n.feeding()) || (n.kind == GridKind.BATTERY && n.batOn && n.batMode != 0)) own += n.energy; }
            e += own + upstreamEnergy(up, seen);
        }
        return e;
    }

    private void problemOf(Net net) {
        if (!net.problem.isEmpty()) return;
        if (net.legacy || net.ins.isEmpty() && net.level == null) {                  // the original rules
            if (!net.intake) net.problem = "No Grid Intake or generator connected";
            else if (!net.transformer) net.problem = "No Power Transformer between the intake and the line";
            else if (!net.earthed) net.problem = "Not earthed - fit an Earthing Switch (protection can't clear faults)";
            else if (net.hot) net.problem = "Transformer OVERHEATED - tripped until it cools (add transformers for this load)";
            else if (net.energy <= 0) net.problem = "No energy - feed RF / FE into the intake";
            return;
        }
        if (net.level == null) { net.problem = "No supply: no intake, generator or transformer feeds this network"; return; }
        for (long p : net.nodes) {                                                  // cables rated below the voltage flash over
            Node n = nodes.get(p);
            if (n.kind == GridKind.CABLE && n.cable().ins.maxVolts < net.level.volts) {
                net.problem = "FLASHOVER: " + n.cable().ins.label + " cable on " + net.level.label; return;
            }
        }
        for (long c : net.ins) {                                                    // converters fed far over their rated primary
            Node cv = nodes.get(c);
            if (cv.upNet < 0 || cv.upNet >= nets.size()) { net.problem = "Converter's primary side (back) isn't connected"; return; }
            Net up = nets.get(cv.upNet);
            Elec.Level rated = Elec.Level.byName(cv.primary, Elec.Level.MV13800);
            if (up.level != null && up.level.volts > rated.volts * 1.15) { net.problem = "FLASHOVER: " + rated.label + " primary fed " + up.level.label; return; }
            if (up.level != null) {
                boolean dcIn = cv.kind == GridKind.INVERTER || cv.kind == GridKind.DCDC;
                if (!up.level.ac && !dcIn && cv.kind != GridKind.RECTIFIER) { net.problem = "Transformer fed DC - transformers need AC"; return; }
                if (up.level.ac && dcIn) { net.problem = "FAULT: " + cv.kind.label + " fed AC - its input stage is DC only (put a rectifier first)"; return; }
                if (!up.level.ac && cv.kind == GridKind.RECTIFIER) { net.problem = "Rectifier fed DC - it needs AC in (use a DC-DC converter)"; return; }
                if (cv.kind.electronic() && up.level.volts < rated.volts * 0.8) { net.problem = "UNDERVOLTAGE: " + rated.label + " input fed " + up.level.label + " - converter locked out"; return; }
            }
            if (!up.problem.isEmpty() && !net.intake) { net.problem = "Upstream: " + up.problem; return; }
        }
        if (!net.earthed) net.problem = "Not earthed - fit an Earthing Switch (protection can't clear faults)";
        else if (net.hot) net.problem = "Transformer OVERHEATED - tripped until it cools (or fit a bigger one)";
        else if (net.energy <= 0) net.problem = "No energy - nothing upstream has any stored";
    }

    /** breakers and disconnectors both break the flood-fill when open */
    static boolean opens(Node n) { return n.kind == GridKind.BREAKER || n.kind == GridKind.ISOLATOR; }

    /** FE/t one transformer carries before it starts to heat */
    public static final int RATING = 4000;

    /** can this network drive a line of the given type? (DC: beam / guideway / third rail, AC: overhead wire) */
    public static String why(Net n, boolean ac) {
        if (n == null) return "Not connected";
        if (!n.problem.isEmpty()) return n.problem;
        if (n.level != null) {
            if (ac && !(n.level.ac && n.level.phases == 1 && n.level.volts >= 15_000)) return "Overhead wire needs 25 kV AC (" + n.level.label + " here)";
            if (!ac && n.level.ac) return "Beam / third rail needs DC from a rectifier (" + n.level.label + " here)";
            return "";
        }
        if (ac && !n.traction) return "Overhead wire needs a Traction Transformer";
        if (!ac && !n.rectifier) return "Beam / guideway / third rail needs a Traction Rectifier (DC)";
        return "";
    }

    /** take FE from the network: intakes first, then batteries. Returns what was supplied. */
    public long draw(Net n, long fe) {
        long got = 0;
        for (int pass = 0; pass < 2; pass++) {                  // sources (intakes + generators) first, then batteries
            for (long p : n.nodes) {
                if (got >= fe) break;
                Node nd = nodes.get(p);
                if ((pass == 0 ? !(nd.kind.source() && nd.feeding()) : nd.kind != GridKind.BATTERY || !nd.batOn || nd.batMode == 0) || nd.energy <= 0) continue;
                long t = Math.min(nd.energy, fe - got);
                nd.energy -= t;
                got += t;
            }
        }
        if (got < fe) got += drawUp(n, fe - got, new HashSet<>());
        n.energy -= got;
        n.drawThisSecond += got;
        n.total += got;
        markDirty();
        return got;
    }

    /** take FE from the networks upstream of n through its feeding converters (each loses ~2%) */
    private long drawUp(Net n, long fe, Set<Integer> seen) {
        if (!seen.add(n.id)) return 0;
        long got = 0;
        for (long c : n.ins) {
            if (got >= fe) break;
            Node cv = nodes.get(c);
            if (cv == null || cv.upNet < 0 || cv.upNet >= nets.size() || !cv.passes()) continue;
            Net up = nets.get(cv.upNet);
            if (!up.problem.isEmpty() && !up.intake) continue;
            double eff = 0.98;
            long want = (long) Math.ceil((fe - got) / eff), pulled = 0;
            for (int pass = 0; pass < 2 && pulled < want; pass++) {
                for (long p : up.nodes) {
                    if (pulled >= want) break;
                    Node nd = nodes.get(p);
                    if ((pass == 0 ? !(nd.kind.source() && nd.feeding()) : nd.kind != GridKind.BATTERY || !nd.batOn || nd.batMode == 0) || nd.energy <= 0) continue;
                    long t = Math.min(nd.energy, want - pulled);
                    nd.energy -= t; pulled += t;
                }
            }
            if (pulled < want) pulled += drawUp(up, want - pulled, seen);
            up.drawThisSecond += pulled;
            up.energy -= Math.min(up.energy, pulled);
            got += (long) Math.floor(pulled * eff);
        }
        return got;
    }

    /** move intake surplus into batteries a little every second */
    public void balance() {
        rebuild();
        for (Net n : nets) {
            long room = 0;
            List<Node> bats = new ArrayList<>(), ins = new ArrayList<>();
            for (long p : n.nodes) {
                Node nd = nodes.get(p);
                if (nd.kind == GridKind.BATTERY && nd.batOn && nd.batMode != 2) { bats.add(nd); room += GridKind.BATTERY.capacity - nd.energy; }
                if (nd.kind.source() && nd.feeding()) ins.add(nd);
            }
            if (bats.isEmpty() || room <= 0) continue;
            long move = Math.min(room, 40_000);
            for (Node in : ins) {
                long t = Math.min(move, in.energy / 2);
                if (t <= 0) continue;
                in.energy -= t;
                for (Node b : bats) {
                    long put = Math.min(t, GridKind.BATTERY.capacity - b.energy);
                    b.energy += put;
                    t -= put;
                    if (t <= 0) break;
                }
                in.energy += t;
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        nodes.clear();
        NBTTagList l = nbt.getTagList("nodes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            Node n = new Node();
            try { n.kind = GridKind.valueOf(t.getString("kind")); } catch (IllegalArgumentException e) { continue; }
            n.breaker = t.getByte("breaker");
            n.energy = t.getLong("energy");
            n.rating = t.hasKey("rating") ? t.getInteger("rating") : 2000;
            n.feeds = t.hasKey("feeds") ? t.getString("feeds") : "Auto";
            n.range = t.hasKey("range") ? t.getInteger("range") : 64;
            n.heat = t.getFloat("heat");
            n.hot = t.getBoolean("hot");
            if (t.hasKey("ctl")) {
                NBTTagCompound c = t.getCompoundTag("ctl");
                n.tap = c.getInteger("tap"); n.avr = c.getBoolean("avr"); n.cool = c.getInteger("cool"); n.dcv = c.getInteger("dcv"); if (c.hasKey("kA")) n.kA = c.getInteger("kA"); n.octap = c.getInteger("octap"); n.speed = c.getFloat("speed");
                n.dcOn = c.getBoolean("dcOn"); n.mainOn = c.getBoolean("mainOn"); n.earthOn = c.getBoolean("earthOn"); n.batOn = c.getBoolean("batOn");
                n.batMode = c.getInteger("batMode"); n.gcb = c.getBoolean("gcb"); n.steps = c.getInteger("steps"); n.capAuto = c.getBoolean("capAuto");
                n.relaySet = c.getInteger("relaySet"); n.surges = c.getInteger("surges");
                n.facing = c.hasKey("facing") ? c.getByte("facing") : -1; n.frontOk = !c.hasKey("v2") || c.getBoolean("frontOk"); n.realSides = c.getBoolean("realSides");
                n.level = c.getString("level"); n.primary = c.getString("primary"); n.kva = c.hasKey("kva") ? c.getInteger("kva") : 5000;
                n.spec = c.getString("spec"); n.temp = c.hasKey("temp") ? c.getFloat("temp") : 20;
            }
            nodes.put(t.getLong("pos"), n);
        }
        dirty = true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("pos", e.getKey());
            t.setString("kind", e.getValue().kind.name());
            t.setByte("breaker", e.getValue().breaker);
            t.setLong("energy", e.getValue().energy);
            t.setInteger("rating", e.getValue().rating);
            t.setString("feeds", e.getValue().feeds);
            t.setInteger("range", e.getValue().range);
            if (e.getValue().heat > 0) t.setFloat("heat", e.getValue().heat);
            if (e.getValue().hot) t.setBoolean("hot", true);
            Node n = e.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("tap", n.tap); c.setBoolean("avr", n.avr); c.setInteger("cool", n.cool); c.setInteger("dcv", n.dcv); c.setInteger("kA", n.kA); c.setInteger("octap", n.octap); c.setFloat("speed", n.speed);
            c.setBoolean("dcOn", n.dcOn); c.setBoolean("mainOn", n.mainOn); c.setBoolean("earthOn", n.earthOn); c.setBoolean("batOn", n.batOn);
            c.setInteger("batMode", n.batMode); c.setBoolean("gcb", n.gcb); c.setInteger("steps", n.steps); c.setBoolean("capAuto", n.capAuto);
            c.setInteger("relaySet", n.relaySet); c.setInteger("surges", n.surges);
            c.setByte("facing", n.facing); c.setBoolean("v2", true); c.setBoolean("frontOk", n.frontOk); c.setBoolean("realSides", n.realSides); c.setString("level", n.level); c.setString("primary", n.primary);
            c.setInteger("kva", n.kva); c.setString("spec", n.spec); c.setFloat("temp", n.temp);
            t.setTag("ctl", c);
            l.appendTag(t);
        }
        nbt.setTag("nodes", l);
        return nbt;
    }
}
