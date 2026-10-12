package com.dogpound.railmap.modelrail;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.stn.StnPanel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * The Model Railway Control Desk (her ChatGPT list #8). It drives the layout on the 2x2 .. 4x4 table built behind it:
 * two cab controllers (speed knob 0-8, direction switch, STOP), points P1-P6 and signals S1-S4 with their repeater
 * lamps, the power key and a latched E-stop, MODE (MANUAL / AUTO signalling / MIRROR of the real railway), station
 * stops, momentum and layout lights; track volts / current meters, four screens, a fast clock, and lamps for 12 V,
 * mains, track power, SHORT, DERAIL, BUFFER, STATION and MIRROR. Two trains touching shorts the track: the power trips,
 * an alarm stands until RESET. Too fast on a curve derails (RE-RAIL puts it back). The stock box (a real chest) holds
 * the mini rolling stock; the layout editor (right-click the table or the desk) places trains from it.
 * 12 V control terminal + mains (the 16 V AC track transformer): no 12 V = dead panel; no mains = no track power.
 */
public class TileModelRailway extends StnPanel {
    public final MiniLayout lay = new MiniLayout();
    public int tw, td, tl;              // table: blocks wide, deep, and how far the left edge is left of the desk
    public boolean tableOk;
    public boolean shorted;
    public String note = "";
    /** mirror mode: real track (packed grid*64 x<<16 | z, -1 = new line) + trains (x<<16|z, colour) */
    public int[] mirChains = new int[0], mirTrains = new int[0];
    private long noteAt;
    /** client: world time of the last sync, for the renderer's extrapolation */
    public long syncTime;

    public TileModelRailway() {
        super(new String[]{"box", "drawer"}, new String[]{"Rolling stock box", "Scenery drawer"}, new int[]{18, 9});
    }

    @Override public String wiredName() { return "Model railway desk"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : !mainsOk() ? "NO MAINS" : !tableOk ? "NO TABLE" : shorted ? "SHORT" : trackPower() ? "TRACK LIVE" : "TRACK OFF"; }
    /** the track transformer: 16 V AC at 4 A max, about 64 W under load */
    @Override protected long load() { return trackPower() ? 4 + lay.trains.size() * 2 : 2; }

    public boolean trackPower() { return live() && tableOk && !shorted && ctl.get("k_main") == 1 && ctl.get("m_estop") == 0; }

    // ---- the table --------------------------------------------------------------------------------------------------------
    public EnumFacing back() { return controlFacing().getOpposite(); }
    public EnumFacing right() { return back().rotateY(); }

    /** the table block at column i (0 = left as the operator sees it), row j (0 = next to the desk) */
    public BlockPos tableBlock(int i, int j) { return pos.offset(back(), 1 + j).offset(right(), i - tl); }

    public static boolean isTable(net.minecraft.world.IBlockAccess w, BlockPos p) { return w.getBlockState(p).getBlock() == ModelRailRegistry.table; }

    /** find the 2x2 .. 4x4 rectangle of table sections behind the desk */
    void detectTable() {
        BlockPos s = pos.offset(back());
        int l = 0, r = 0;
        while (l < 3 && isTable(world, s.offset(right().getOpposite(), l + 1))) l++;
        while (r < 3 && isTable(world, s.offset(right(), r + 1))) r++;
        int ntl = isTable(world, s) ? Math.min(l, 3) : 0, ntw = isTable(world, s) ? Math.min(4, ntl + r + 1) : 0;
        int ntd = 0;
        outer:
        for (int j = 0; j < 4 && ntw > 0; j++) {
            for (int i = 0; i < ntw; i++) if (!isTable(world, s.offset(back(), j).offset(right(), i - ntl))) break outer;
            ntd = j + 1;
        }
        boolean ok = ntw >= 2 && ntd >= 2;
        if (ok != tableOk || ok && (ntw != tw || ntd != td || ntl != tl)) {
            tableOk = ok;
            if (ok) {                                   // a missing section only pauses the layout; a new size re-fits it
                tw = ntw; td = ntd; tl = ntl;
                lay.w = tw * 4;
                lay.d = td * 4;
                for (MiniLayout.Train t : new ArrayList<>(lay.trains))
                    for (int i = 0; i < Math.max(1, t.cars.length); i++)
                        if (!lay.inside(lay.car(t, i).c % MiniLayout.N, lay.car(t, i).c / MiniLayout.N)) { giveBack(t, null); break; }
            }
            controlsChanged();
        }
    }

    /** is this world block one of my table's sections */
    public boolean owns(BlockPos p) {
        if (!tableOk) return false;
        for (int j = 0; j < td; j++) for (int i = 0; i < tw; i++) if (tableBlock(i, j).equals(p)) return true;
        return false;
    }

    /** the desk that owns a table section, or null (either side) */
    public static TileModelRailway owner(net.minecraft.world.World w, BlockPos p) {
        for (BlockPos q : BlockPos.getAllInBox(p.add(-5, 0, -5), p.add(5, 0, 5)))
            if (w.getTileEntity(q) instanceof TileModelRailway t && t.owns(p)) return t;
        return null;
    }

    // ---- what the panel asks for ----------------------------------------------------------------------------------------------
    public MiniLayout.Inputs inputs() {
        MiniLayout.Inputs in = new MiniLayout.Inputs();
        in.power = trackPower();
        in.target[1] = ctl.get("s_spdA") * MiniLayout.STEP * (ctl.get("s_dirA") == 1 ? -1 : 1);
        in.target[2] = ctl.get("s_spdB") * MiniLayout.STEP * (ctl.get("s_dirB") == 1 ? -1 : 1);
        in.inertia = ctl.get("s_inert") == 1;
        in.stationStops = ctl.get("s_stn") == 1;
        in.autoSignals = ctl.get("s_mode") == 1;
        return in;
    }

    /** client: the layout run on from the last sync to now (smooth trains between the 0.5 s syncs) */
    public MiniLayout predicted(float partialTicks) {
        MiniLayout m = lay.copy();
        if (world == null || !tableOk || mirror()) return m;
        double secs = Math.min(1.0, Math.max(0, (world.getTotalWorldTime() - syncTime + partialTicks) / 20.0));
        MiniLayout.Inputs in = inputs();
        for (double t = 0; t + 1e-6 < secs; t += 0.05) m.tick(in, Math.min(0.05, secs - t));
        return m;
    }

    public boolean mirror() { return ctl.get("s_mode") == 2; }
    public boolean night() { return ctl.get("s_lite") == 1; }

    // ---- controls ------------------------------------------------------------------------------------------------------------
    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("s_spdA") || id.equals("s_spdB")) { ctl.set(id, (ctl.get(id) + 1) % 9); return "§eCab " + id.charAt(5) + " speed " + ctl.get(id); }
        if (id.equals("m_estop") && ctl.get(id) == 0) {
            ctl.set(id, 1);
            Recorder.event(world, "Model railway E-STOP pressed by " + who(who));
            return "§cE-STOP: track power off";
        }
        if (id.equals("s_mode")) {
            ctl.set(id, (ctl.get(id) + 1) % 3);
            if (ctl.get(id) == 2) { mirAt = -1_000_000; trainsAt = -1_000_000; }
            return "§eMode: " + new String[]{"MANUAL signals", "AUTO signalling (track circuits)", "MIRROR - the real railway in miniature"}[ctl.get(id)];
        }
        return null;
    }

    @Override
    protected String act(String id, EntityPlayer who) {
        if (id.equals("a_stop") || id.equals("b_stop")) {
            String k = id.equals("a_stop") ? "s_spdA" : "s_spdB";
            ctl.set(k, 0);
            MiniLayout.Train t = lay.train(id.equals("a_stop") ? 1 : 2);
            if (t != null) t.v = 0;
            return "§eCab " + (id.charAt(0) == 'a' ? "A" : "B") + " stopped";
        }
        if (id.equals("reset")) {
            if (!shorted) return "§7Nothing to reset";
            for (int i = 0; i < lay.trains.size(); i++) for (int j = i + 1; j < lay.trains.size(); j++)
                if (lay.touching(lay.trains.get(i), lay.trains.get(j))) return "§cStill shorted: lift or pull the trains apart first (editor: LIFT)";
            shorted = false;
            AlarmManager.get(world).clear(alarmKey());
            Recorder.event(world, "Model railway short reset by " + who(who));
            return "§aShort reset - track power back";
        }
        if (id.equals("rerail")) {
            int n = 0;
            for (MiniLayout.Train t : lay.trains) if (t.derailed) { t.derailed = false; t.v = 0; n++; }
            ctl.set("s_spdA", 0); ctl.set("s_spdB", 0);
            return n == 0 ? "§7Nothing is off the rails" : "§aRe-railed " + n + " train(s) - knobs back to 0";
        }
        if (id.length() == 2 && (id.charAt(0) == 'p' || id.charAt(0) == 'g') && Character.isDigit(id.charAt(1))) {
            boolean pts = id.charAt(0) == 'p';
            int n = id.charAt(1) - '1';
            List<Integer> l = lay.numbered(pts ? MiniLayout.POINTS : MiniLayout.SIGNAL);
            if (n >= l.size()) return "§7" + (pts ? "P" : "S") + (n + 1) + " isn't on the layout";
            int c = l.get(n);
            if (pts) {
                for (MiniLayout.Train t : lay.trains)
                    for (int i = 0; i < Math.max(1, t.cars.length); i++)
                        if (lay.car(t, i).c == c) return "§cP" + (n + 1) + " locked: a train is standing on them";
                lay.rev[c] = !lay.rev[c];
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.selector"), net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1.6f);
                return "§eP" + (n + 1) + (lay.rev[c] ? " reverse (turnout)" : " normal (straight)");
            }
            if (ctl.get("s_mode") == 1) return "§7Signals are automatic in AUTO mode - turn MODE to MANUAL";
            lay.green[c] = !lay.green[c];
            return "§eS" + (n + 1) + (lay.green[c] ? " clear" : " danger");
        }
        return null;
    }

    /** a train off the table: its vehicles back into the stock box (to the player / onto the floor when it is full) */
    void giveBack(MiniLayout.Train t, EntityPlayer p) {
        lay.trains.remove(t);
        for (int m : t.cars) {
            ItemStack s = new ItemStack(ModelRailRegistry.stock, 1, m);
            ItemStack left = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(new net.minecraftforge.items.wrapper.InvWrapper(store(0)), s, false);
            if (left.isEmpty()) continue;
            if (p != null) net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, left);
            else net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, left);
        }
    }

    @Override
    public void dropAll() {
        for (MiniLayout.Train t : new ArrayList<>(lay.trains))
            for (int m : t.cars) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, new ItemStack(ModelRailRegistry.stock, 1, m));
        lay.trains.clear();
        super.dropAll();
    }

    private String alarmKey() { return "modelrail:" + pos.toLong(); }

    // ---- the tick -------------------------------------------------------------------------------------------------------------
    private long mirAt = -1_000_000, trainsAt = -1_000_000;

    @Override
    protected void work() {
        if (age % 40 == 1) detectTable();
        boolean moving = false;
        if (tableOk && !mirror()) {
            MiniLayout.Events ev = lay.tick(inputs(), 0.05);
            for (MiniLayout.Train t : lay.trains) if (t.v != 0) moving = true;
            if (ev.collision != null) {
                shorted = true;
                AlarmManager.get(world).raise(world, alarmKey(), AlarmManager.Cat.FAULT, AlarmManager.Sev.MINOR,
                        "Model railway: " + ev.collision + " - track short, power tripped (press RESET)", pos, true);
                Recorder.event(world, "Model railway collision: " + ev.collision);
                say("SHORT: " + ev.collision);
            }
            if (ev.derail != null) { Recorder.event(world, "Model railway derailment: " + ev.derail); say("DERAILED: " + ev.derail); }
            if (ev.stationArrival > 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ctl.push"), net.minecraft.util.SoundCategory.BLOCKS, 0.3f, 2.0f);
            if (age % 5 == 0) readouts(ev);
        } else if (age % 20 == 0) readouts(new MiniLayout.Events());
        if (mirror() && tableOk && live()) mirror(world.getTotalWorldTime());
        if (moving && age % 10 == 0) controlsChanged();
    }

    private void say(String s) { note = s.toUpperCase(Locale.ROOT); noteAt = world.getTotalWorldTime(); }

    private void readouts(MiniLayout.Events ev) {
        boolean tp = trackPower();
        put("trk", tp);
        put("shortl", shorted);
        boolean der = false, stn = false, buf = false;
        for (MiniLayout.Train t : lay.trains) {
            der |= t.derailed;
            stn |= t.dwell > 0;
            if (t.v == 0 && lay.type(t.loco.c) == MiniLayout.BUFFER) buf = true;
        }
        put("derl", der); put("stnl", stn); put("bufl", buf || ev.buffer != null);
        put("mirl", mirror() && tableOk && live());
        int va = ctl.get("s_spdA"), vb = ctl.get("s_spdB");
        put("volts", tp ? Math.round(Math.max(va, vb) * 1.5f + 2) : 0);
        put("amps", tp ? (int) Math.min(1000, Math.round(ev.amps * 250)) : 0);
        List<Integer> pts = lay.numbered(MiniLayout.POINTS), sig = lay.numbered(MiniLayout.SIGNAL);
        for (int i = 0; i < 6; i++) put("pl" + (i + 1), i < pts.size() && lay.rev[pts.get(i)]);
        for (int i = 0; i < 4; i++) put("gl" + (i + 1), i < sig.size() && lay.green[sig.get(i)]);
        text("scr", !tableOk ? "NO TABLE: 2X2-4X4 BEHIND DESK" : world.getTotalWorldTime() - noteAt < 200 && !note.isEmpty() ? cut(note, 30)
                : (tw + "X" + td + " TABLE " + lay.w + "X" + lay.d + " " + lay.trains.size() + " TRAINS" + (shorted ? " SHORT" : "")));
        text("scrA", cab(1));
        text("scrB", cab(2));
        long t = (world.getWorldTime() * 6 + 6000) % 24000;            // a 6x fast clock, the model railway's own time
        text("clk", String.format(Locale.ROOT, "FAST CLOCK %02d:%02d %s", t / 1000, (t % 1000) * 60 / 1000, mirror() ? "MIRROR" : ctl.get("s_mode") == 1 ? "AUTO" : "MANUAL"));
        text("spdA", speed(1));
        text("spdB", speed(2));
    }

    private String cab(int id) {
        MiniLayout.Train t = lay.train(id);
        if (t == null) return (id == 1 ? "A" : "B") + ": NO TRAIN";
        return (id == 1 ? "A: " : "B: ") + cut(t.name.toUpperCase(Locale.ROOT), 16) + " " + t.cars.length + "V" + (t.derailed ? " OFF" : t.dwell > 0 ? " STN" : "");
    }

    private String speed(int id) {
        MiniLayout.Train t = lay.train(id);
        return t == null ? "--" : Math.round(Math.abs(t.v) * 60) + " KM/H " + (t.v < 0 ? "R" : "F");
    }

    // ---- mirror: the real railway in miniature ----------------------------------------------------------------------------------
    private double[] mirBounds;

    private void mirror(long now) {
        if (now - mirAt >= 200) {
            mirAt = now;
            List<float[]> chains = new ArrayList<>();
            for (com.dogpound.railmap.graph.RailNetwork n : com.dogpound.railmap.web.TrainMapFeed.networks(world)) chains.addAll(com.dogpound.railmap.graph.Chains.build(n));
            double nx = Double.MAX_VALUE, nz = Double.MAX_VALUE, xx = -Double.MAX_VALUE, xz = -Double.MAX_VALUE;
            int pts = 0;
            for (float[] c : chains) { pts += c.length / 3; for (int i = 0; i + 2 < c.length; i += 3) { nx = Math.min(nx, c[i]); xx = Math.max(xx, c[i]); nz = Math.min(nz, c[i + 2]); xz = Math.max(xz, c[i + 2]); } }
            if (chains.isEmpty()) { mirChains = new int[0]; mirBounds = null; }
            else {
                mirBounds = new double[]{nx, nz, xx, xz};
                int stride = Math.max(1, pts / 1500);                   // ponytail: plain decimation, ~1500 points max per table
                List<Integer> out = new ArrayList<>();
                for (float[] c : chains) {
                    out.add(-1);
                    for (int i = 0; i + 2 < c.length; i += 3 * stride) out.add(pack(c[i], c[i + 2]));
                    out.add(pack(c[c.length - 3], c[c.length - 1]));
                }
                mirChains = out.stream().mapToInt(Integer::intValue).toArray();
            }
            controlsChanged();
        }
        if (now - trainsAt >= 20 && mirBounds != null) {
            trainsAt = now;
            Map<String, Integer> col = new HashMap<>();
            for (com.dogpound.railmap.auto.RailwayData.Line ln : com.dogpound.railmap.auto.RailwayData.get(world).lines().values()) col.put(ln.name, ln.color);
            List<Integer> out = new ArrayList<>();
            for (com.dogpound.railmap.api.Transport.Vehicle v : com.dogpound.railmap.api.Transport.vehicles(world)) {
                if (v.x < mirBounds[0] - 32 || v.x > mirBounds[2] + 32 || v.z < mirBounds[1] - 32 || v.z > mirBounds[3] + 32) continue;
                out.add(pack(v.x, v.z));
                out.add(col.getOrDefault(v.line, v.speedKmh > 1 ? 0xFF7AC6 : 0xC0C0C0));
                if (out.size() >= 128) break;
            }
            mirTrains = out.stream().mapToInt(Integer::intValue).toArray();
            controlsChanged();
        }
    }

    /** world x,z -> table grid units * 64, fitted with a margin, packed x << 16 | z */
    private int pack(double x, double z) {
        double[] b = mirBounds;
        double gw = lay.w - 0.6, gd = lay.d - 0.6;
        double s = Math.min(gw / Math.max(8, b[2] - b[0]), gd / Math.max(8, b[3] - b[1]));
        double ox = 0.3 + (gw - (b[2] - b[0]) * s) / 2, oz = 0.3 + (gd - (b[3] - b[1]) * s) / 2;
        int gx = (int) Math.round(((x - b[0]) * s + ox) * 64), gz = (int) Math.round(((z - b[1]) * s + oz) * 64);
        return (Math.max(0, Math.min(65535, gx)) << 16) | Math.max(0, Math.min(65535, gz));
    }

    // ---- editor operations (PacketModelRail) ---------------------------------------------------------------------------------------
    public String edit(EntityPlayerMP p, String op, int[] a) {
        if (p.getDistanceSq(pos) > 12 * 12) return "§cToo far from the model railway";
        if (!tableOk) return "§cBuild the table first: 2x2 to 4x4 Model Railway Table sections behind the desk";
        switch (op) {
            case "set": {
                if (a.length < 5 || !lay.inside(a[0], a[1])) return null;
                int c = MiniLayout.idx(a[0], a[1]);
                for (MiniLayout.Train t : lay.trains) for (int i = 0; i < Math.max(1, t.cars.length); i++)
                    if (lay.car(t, i).c == c) return "§cA train is standing there - lift it first";
                lay.set(a[0], a[1], a[2], a[3], a[4] != 0);
                if (a[2] == MiniLayout.SIGNAL) lay.green[c] = true;
                controlsChanged();
                return null;
            }
            case "op": {
                if (a.length < 2 || !lay.inside(a[0], a[1])) return null;
                if (!live()) return "§cThe point motors and signals need the desk powered (12 V + mains)";
                int c = MiniLayout.idx(a[0], a[1]), t = lay.type(c);
                if (t == MiniLayout.POINTS) {
                    int n = lay.numbered(MiniLayout.POINTS).indexOf(c);
                    if (n >= 0 && n < 6) return act("p" + (n + 1), p);
                    for (MiniLayout.Train tr : lay.trains) for (int i = 0; i < Math.max(1, tr.cars.length); i++) if (lay.car(tr, i).c == c) return "§cLocked: a train is on the points";
                    lay.rev[c] = !lay.rev[c];
                } else if (t == MiniLayout.SIGNAL) {
                    if (ctl.get("s_mode") == 1) return "§7Signals are automatic in AUTO mode";
                    lay.green[c] = !lay.green[c];
                }
                controlsChanged();
                return null;
            }
            case "place": {
                if (a.length < 3 || a[0] < 1 || a[0] > 2) return null;
                if (lay.train(a[0]) != null) return "§cCab " + (a[0] == 1 ? "A" : "B") + " already has a train - LIFT it first";
                InventoryBasic box = store(0);
                List<Integer> metas = new ArrayList<>(), slots = new ArrayList<>();
                for (int i = 0; i < box.getSizeInventory() && metas.size() < MiniLayout.MAX_CARS; i++) {
                    ItemStack s = box.getStackInSlot(i);
                    if (s.isEmpty() || !(s.getItem() instanceof ItemMiniStock)) continue;
                    int m = ItemMiniStock.meta(s);
                    if (metas.isEmpty() && !ItemMiniStock.loco(m)) continue;          // a train starts with its loco
                    if (!metas.isEmpty() && ItemMiniStock.loco(m)) break;            // the next loco starts the next train
                    for (int k = 0; k < s.getCount() && metas.size() < MiniLayout.MAX_CARS; k++) { metas.add(m); slots.add(i); }
                }
                if (metas.isEmpty()) return "§cPut a mini loco (and wagons after it) in the stock box first";
                MiniLayout.Train t = new MiniLayout.Train();
                t.id = a[0];
                t.cars = metas.stream().mapToInt(Integer::intValue).toArray();
                t.name = ItemMiniStock.NAMES[t.cars[0]].replace("Mini ", "") + (a[0] == 1 ? " A" : " B");
                String why = lay.place(t, a[1], a[2]);
                if (why != null) return "§c" + why;
                for (int slot : slots) box.decrStackSize(slot, 1);
                lay.trains.add(t);
                Recorder.event(world, "Model railway: " + p.getName() + " placed " + t.name + " (" + t.cars.length + " vehicles)");
                controlsChanged();
                return "§aPlaced " + t.name + " on cab " + (a[0] == 1 ? "A" : "B");
            }
            case "lift": {
                if (a.length < 1) return null;
                MiniLayout.Train t = lay.train(a[0]);
                if (t == null) return "§7No train on that cab";
                giveBack(t, p);
                controlsChanged();
                return "§eLifted " + t.name + " back into the stock box";
            }
            case "buy": {
                if (a.length < 1 || a[0] < 0 || a[0] >= ItemMiniStock.IDS.length) return null;
                long price = ItemMiniStock.PRICE[a[0]];
                if (!com.dogpound.railmap.economy.Coins.charge(p, price, "Model railway shop: " + ItemMiniStock.NAMES[a[0]]))
                    return com.dogpound.railmap.economy.Coins.available() ? "§cYou can't afford " + com.dogpound.railmap.economy.Coins.fmt(price) : "§cNo bank on this server - craft it instead (or creative)";
                ItemStack s = new ItemStack(ModelRailRegistry.stock, 1, a[0]);
                ItemStack left = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(new net.minecraftforge.items.wrapper.InvWrapper(store(0)), s, false);
                if (!left.isEmpty()) net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, left);
                return "§aBought " + ItemMiniStock.NAMES[a[0]] + (p.capabilities.isCreativeMode ? "" : " for " + com.dogpound.railmap.economy.Coins.fmt(price));
            }
            case "demo": {
                if (!lay.trains.isEmpty()) return "§cLift the trains first";
                lay.demo();
                controlsChanged();
                return "§aStarter layout laid";
            }
            case "clear": {
                if (!lay.trains.isEmpty()) return "§cLift the trains first";
                for (int i = 0; i < lay.cell.length; i++) { lay.cell[i] = 0; lay.rev[i] = false; lay.green[i] = false; }
                controlsChanged();
                return "§eLayout cleared";
            }
            default: return null;
        }
    }

    // ---- save + sync ---------------------------------------------------------------------------------------------------------------
    @Override
    protected void saveExtra(NBTTagCompound t) {
        NBTTagCompound l = new NBTTagCompound();
        lay.write(l);
        t.setTag("layout", l);
        t.setInteger("tw", tw); t.setInteger("td", td); t.setInteger("tl", tl); t.setBoolean("tok", tableOk);
        t.setBoolean("short", shorted);
        t.setString("note", note);
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        if (t.hasKey("layout")) lay.read(t.getCompoundTag("layout"));
        tw = t.getInteger("tw"); td = t.getInteger("td"); tl = t.getInteger("tl"); tableOk = t.getBoolean("tok");
        shorted = t.getBoolean("short");
        note = t.getString("note");
        if (t.hasKey("mirC")) { mirChains = t.getIntArray("mirC"); mirTrains = t.getIntArray("mirT"); }
        if (world != null && world.isRemote) syncTime = world.getTotalWorldTime();
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound t = super.getUpdateTag();
        if (mirror()) { t.setIntArray("mirC", mirChains); t.setIntArray("mirT", mirTrains); }
        return t;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(6, 3, 6); }

    @Override
    public double getMaxRenderDistanceSquared() { return 96 * 96; }
}
