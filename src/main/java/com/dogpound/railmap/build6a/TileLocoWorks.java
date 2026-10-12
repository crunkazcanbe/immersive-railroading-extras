package com.dogpound.railmap.build6a;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.ItemRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.registry.CarPassengerDefinition;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveDieselDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.item.ItemPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Locomotive Works (batch 6a #4): the works manager's desk of an erecting shop. Multiblock: Erecting Bays placed in a
 * row either side of the desk (up to 2 each side, 4 in all) - one vehicle builds in each bay at once. Pick any rolling
 * stock Immersive Railroading has loaded (category, then PREV / NEXT), ADD it to the build queue (6 deep), START the
 * shop: each free bay takes the next job, draws its parts from the parts stores (Industrial Parts - plates, frames,
 * motors, circuits, gearboxes, heating coils, bearings - and depot spares - wheelsets, brake blocks, coupler knuckles -
 * by weight and type: a 120 t diesel costs far more than a 20 t wagon), and builds it over time; the finished IR
 * rolling-stock item comes out in the despatch bay (hoppers can pull it). PARTS SHORT holds a job until the stores are
 * filled. Shop power 208-480 V 3φ (cranes, welders), 12 V for the desk.
 */
public class TileLocoWorks extends DepotTile {
    static final String[] CATS = {"ALL", "LOCOMOTIVE", "FREIGHT", "PASSENGER"};
    static final double[] GAUGES = {0, 1.435, 1.067, 0.914, 0.632};
    static final int QUEUE = 6;

    /** queued: "defId|gauge"; jobs: per bay {defId|gauge, progress, need} */
    final List<String> queue = new ArrayList<>();
    final String[] job = new String[4];
    final int[] prog = new int[4], need = new int[4];
    String sel = "", fault = "";
    int built;
    boolean running;
    final List<String> log = new ArrayList<>();
    private final Routed items;

    public TileLocoWorks() {
        super(new String[]{"parts", "despatch"}, new String[]{"Parts stores", "Despatch bay"}, new int[]{54, 9});
        items = new Routed(new net.minecraft.inventory.IInventory[]{store(0), store(1)}, new boolean[]{false, true}, s -> !s.isEmpty(), s -> false);
    }

    @Override public String wiredName() { return "Locomotive works"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { int n = 0; for (String j : job) if (j != null) n++; return running ? 40 + 220L * n : 5; }
    @Override public String gearState() {
        return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · %d bays · queue %d · %d built · %s", running ? "RUNNING" : "held", bays(), queue.size(), built, mains.what);
    }

    // ---- the multiblock --------------------------------------------------------------------------------------------
    int bays() {
        EnumFacing along = controlFacing().rotateY();
        int n = 0;
        for (int sgn : new int[]{-1, 1})
            for (int k = 1; k <= 2; k++) {
                BlockPos p = pos.offset(along, sgn * k);
                if (world.getBlockState(p).getBlock() == B6Blocks.bay) n++;
                else break;
            }
        return n;
    }

    // ---- stock catalogue -------------------------------------------------------------------------------------------
    static String cat(EntityRollingStockDefinition d) {
        if (d instanceof LocomotiveDefinition l && !l.isCabCar()) return "LOCOMOTIVE";
        if (d instanceof CarPassengerDefinition || d instanceof LocomotiveDefinition) return "PASSENGER";
        return "FREIGHT";
    }

    List<EntityRollingStockDefinition> catalogue() {
        List<EntityRollingStockDefinition> l = new ArrayList<>();
        try {
            String c = CATS[Math.floorMod(ctl.get("s_cat"), CATS.length)];
            for (EntityRollingStockDefinition d : DefinitionManager.getDefinitions()) if (c.equals("ALL") || cat(d).equals(c)) l.add(d);
            l.sort(Comparator.comparing(EntityRollingStockDefinition::name, String.CASE_INSENSITIVE_ORDER));
        } catch (LinkageError | RuntimeException ignored) { }
        return l;
    }

    static EntityRollingStockDefinition def(String id) { try { return DefinitionManager.getDefinition(id); } catch (LinkageError | RuntimeException e) { return null; } }

    Gauge gaugeFor(EntityRollingStockDefinition d) {
        double g = GAUGES[Math.floorMod(ctl.get("s_gauge"), GAUGES.length)];
        return g == 0 ? d.recommended_gauge : Gauge.from(g);
    }

    // ---- bill of materials -----------------------------------------------------------------------------------------
    static final class Req {
        final String what; final Predicate<ItemStack> ok; final int n;
        Req(String what, Predicate<ItemStack> ok, int n) { this.what = what; this.ok = ok; this.n = n; }
        String what() { return what; } Predicate<ItemStack> ok() { return ok; } int n() { return n; }
    }

    static Predicate<ItemStack> part(ItemPart.Part p) { return s -> !s.isEmpty() && s.getItem() instanceof ItemPart && ItemPart.part(s) == p; }
    static Predicate<ItemStack> spare(ItemSpare.Spare p) { return s -> ItemSpare.is(s, p); }

    /** what one vehicle takes, by its weight, length and kind */
    static List<Req> bill(EntityRollingStockDefinition d, Gauge g) {
        double t = 30, len = 15;
        try { t = Math.max(1, d.getWeight(g) / 1000.0); len = d.getLength(g); } catch (RuntimeException ignored) { }
        boolean loco = d instanceof LocomotiveDefinition l && !l.isCabCar(), steam = d instanceof LocomotiveSteamDefinition, diesel = d instanceof LocomotiveDieselDefinition;
        boolean pax = d instanceof CarPassengerDefinition || d instanceof LocomotiveDefinition ld && ld.isCabCar();
        int wheelsets = len < 9 ? 2 : len > 19 && loco ? 6 : 4;
        List<Req> r = new ArrayList<>();
        r.add(new Req("Reinforced Steel Plate", part(ItemPart.Part.PLATE), (int) Math.ceil(t / 8) + (steam ? (int) Math.ceil(t / 12) : 0)));
        r.add(new Req("Machine Frame", part(ItemPart.Part.FRAME), 1 + (int) (t / 40)));
        r.add(new Req("Wheelset", spare(ItemSpare.Spare.WHEELSET), wheelsets));
        r.add(new Req("Ball Bearing", part(ItemPart.Part.BEARING), wheelsets * 2));
        r.add(new Req("Brake Block Set", spare(ItemSpare.Spare.BRAKE_BLOCKS), 1));
        r.add(new Req("Coupler Knuckle", spare(ItemSpare.Spare.COUPLER), 2));
        r.add(new Req("Control Circuit", part(ItemPart.Part.CIRCUIT), loco ? 3 : pax ? 2 : 1));
        if (diesel) {
            r.add(new Req("Motor Core", part(ItemPart.Part.MOTOR), wheelsets));
            r.add(new Req("Gearbox", part(ItemPart.Part.GEARBOX), wheelsets / 2));
            r.add(new Req("Cooling Fan", part(ItemPart.Part.FAN), 2));
        }
        if (steam) {
            r.add(new Req("Heating Coil", part(ItemPart.Part.HEATING_COIL), 2 + (int) (t / 50)));
            r.add(new Req("Steel Shaft", part(ItemPart.Part.SHAFT), 2));
            r.add(new Req("Spring", part(ItemPart.Part.SPRING), 4));
        }
        if (pax || loco) r.add(new Req("Glass Pane", s -> !s.isEmpty() && s.getItem() == Item.getItemFromBlock(Blocks.GLASS_PANE), pax ? (int) Math.max(4, len / 2) : 4));
        return r;
    }

    static int needTicks(EntityRollingStockDefinition d, Gauge g) {
        double t = 30;
        try { t = Math.max(1, d.getWeight(g) / 1000.0); } catch (RuntimeException ignored) { }
        return (int) Math.max(40, (600 + t * 40) * B6Config.worksTime);
    }

    /** the first part the stores are short of for this vehicle, or null */
    String shortOf(List<Req> bill) {
        for (Req q : bill) { int have = B6.count(store(0), q.ok()); if (have < q.n()) return q.n() - have + " x " + q.what(); }
        return null;
    }

    int availPct(List<Req> bill) {
        int need = 0, have = 0;
        for (Req q : bill) { need += q.n(); have += Math.min(q.n(), B6.count(store(0), q.ok())); }
        return need == 0 ? 100 : have * 100 / need;
    }

    static ItemStack stockItem(EntityRollingStockDefinition d, Gauge g) {
        cam72cam.mod.item.ItemStack st = new cam72cam.mod.item.ItemStack(IRItems.ITEM_ROLLING_STOCK, 1);
        ItemRollingStock.Data data = new ItemRollingStock.Data(st);
        data.def = d;
        data.gauge = g;
        data.write();
        return st.internal;
    }

    // ---- controls ----------------------------------------------------------------------------------------------------
    EntityRollingStockDefinition selected(List<EntityRollingStockDefinition> l) {
        for (EntityRollingStockDefinition d : l) if (d.defID.equals(sel)) return d;
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        List<EntityRollingStockDefinition> l = catalogue();
        switch (id) {
            case "prev", "next" -> {
                if (l.isEmpty()) return "§cNo Immersive Railroading stock in that category";
                EntityRollingStockDefinition cur = selected(l);
                int i = Math.max(0, l.indexOf(cur));
                i = Math.floorMod(i + (id.equals("next") ? 1 : -1), l.size());
                sel = l.get(i).defID;
                return "§b" + l.get(i).name() + " §7(" + (i + 1) + "/" + l.size() + ")";
            }
            case "add" -> {
                EntityRollingStockDefinition d = selected(l);
                if (d == null) return "§cNothing selected";
                if (queue.size() >= QUEUE) return "§cThe build queue is full (" + QUEUE + ")";
                Gauge g = gaugeFor(d);
                queue.add(d.defID + "|" + g.value());
                event("QUEUED " + d.name() + " (" + Math.round(g.value() * 1000) + " mm)");
                return "§aQueued " + d.name() + " - " + queue.size() + " in the queue";
            }
            case "del" -> {
                if (queue.isEmpty()) return "§7The queue is empty";
                String r = queue.remove(queue.size() - 1);
                EntityRollingStockDefinition d = def(r.split("\\|")[0]);
                return "§eRemoved " + (d == null ? r : d.name()) + " from the queue";
            }
            case "start" -> {
                if (ctl.get("k_shop") != 1) return "§cTurn the SHOP key ON first";
                if (bays() == 0) return "§cNo Erecting Bay beside the desk - place one or more in a row either side of it";
                running = true; fault = ""; AlarmManager.get(world).clear(akey());
                event("SHOP STARTED by " + who(who));
                return "§aShop running - " + bays() + " bay(s)";
            }
            case "hold" -> { running = false; event("SHOP HELD by " + who(who)); return "§eShop on hold (jobs keep their progress)"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            if (ctl.get(id) == 1) running = false;
            return ctl.get(id) == 1 ? "§cE-STOP - cranes and welders stopped" : "§aE-stop released";
        }
        if (id.equals("s_cat")) { ctl.set(id, (ctl.get(id) + 1) % CATS.length); sel = ""; return "§eCategory: " + CATS[ctl.get(id)]; }
        return null;
    }

    void event(String s) {
        log.add(0, s);
        while (log.size() > 6) log.remove(log.size() - 1);
        Recorder.event(world, "Locomotive works: " + s);
    }

    String akey() { return "b6works:" + pos.toLong(); }

    @Override
    protected void work() {
        boolean go = running && powered() && mainsOk() && ctl.get("k_shop") == 1 && ctl.get("m_estop") == 0;
        int nb = Math.min(4, bays());
        if (age % 20 == 0 && go) {
            for (int b = 0; b < nb; b++) {
                if (job[b] != null || queue.isEmpty()) continue;
                String q = queue.get(0);
                EntityRollingStockDefinition d = def(q.split("\\|")[0]);
                if (d == null) { queue.remove(0); event("DROPPED unknown stock " + q); continue; }
                Gauge g = Gauge.from(Double.parseDouble(q.split("\\|")[1]));
                List<Req> bill = bill(d, g);
                String sh = shortOf(bill);
                if (sh != null) {
                    if (!fault.startsWith("PARTS SHORT")) {
                        fault = "PARTS SHORT for " + d.name() + ": " + sh;
                        AlarmManager.get(world).raise(world, akey(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.INFO, "LOCO WORKS: " + fault, pos, false);
                        event(fault);
                    }
                    break;
                }
                for (Req r : bill) B6.take(store(0), r.ok(), r.n());
                queue.remove(0);
                job[b] = q; prog[b] = 0; need[b] = needTicks(d, g);
                if (fault.startsWith("PARTS SHORT")) { fault = ""; AlarmManager.get(world).clear(akey()); }
                event("BAY " + (b + 1) + ": started " + d.name());
                world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.5f, 0.7f);
            }
        }
        for (int b = 0; b < 4; b++) {
            if (job[b] == null) continue;
            if (b >= nb) continue;                                              // its bay is gone: the job waits
            if (go && prog[b] < need[b]) prog[b]++;
            if (prog[b] >= need[b]) {
                EntityRollingStockDefinition d = def(job[b].split("\\|")[0]);
                if (d == null) { job[b] = null; continue; }
                Gauge g = Gauge.from(Double.parseDouble(job[b].split("\\|")[1]));
                ItemStack out = stockItem(d, g);
                if (B6.free(store(1)) == 0) { if (age % 200 == 0) Recorder.event(world, "Locomotive works: DESPATCH BAY FULL - " + d.name() + " waiting"); continue; }
                B6.put(store(1), out);
                built++;
                event("BAY " + (b + 1) + ": " + d.name() + " COMPLETE - in the despatch bay");
                job[b] = null;
                world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.6f, 0.8f);
            }
            if (go && prog[b] % 60 == 1) world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.2f, 1.4f);
        }
        if (age % 10 == 0) show(nb);
    }

    void show(int nb) {
        List<EntityRollingStockDefinition> l = catalogue();
        EntityRollingStockDefinition d = selected(l);
        List<Req> bill = d == null ? java.util.Collections.emptyList() : bill(d, gaugeFor(d));
        put("pwr", powered()); put("mainsl", mains.ok()); put("bays", nb > 0); put("short", fault.startsWith("PARTS SHORT"));
        put("ready", B6.free(store(1)) < 9); put("fault", !fault.isEmpty() && !fault.startsWith("PARTS")); put("hold", !running);
        put("runl", running && ctl.get("k_shop") == 1);
        for (int b = 0; b < 4; b++) put("b" + (b + 1), job[b] != null && b < nb);
        int first = -1;
        for (int b = 0; b < 4; b++) if (job[b] != null) { first = b; break; }
        put("progg", first < 0 ? 0 : prog[first] * 100 / Math.max(1, need[first]));
        put("partg", d == null ? 0 : availPct(bill)); put("queueg", queue.size() * 100 / QUEUE);
        put("loadg", (int) Math.min(100, load() * 100 / 920));
        text("sel", d == null ? "NO STOCK" : cut(d.name().toUpperCase(Locale.ROOT), 22));
        text("qn", String.valueOf(queue.size())); text("built", String.valueOf(built));
        String[] t = new String[4];
        switch (Math.floorMod(ctl.get("page"), 4)) {
            case 0 -> {
                if (d == null) { t[0] = "NO STOCK IN " + CATS[Math.floorMod(ctl.get("s_cat"), 4)]; t[1] = t[2] = t[3] = ""; break; }
                Gauge g = gaugeFor(d);
                double tn = 0;
                try { tn = d.getWeight(g) / 1000.0; } catch (RuntimeException ignored) { }
                t[0] = cut(d.name().toUpperCase(Locale.ROOT), 30);
                t[1] = String.format(Locale.ROOT, "%s %dMM %dT", cat(d), Math.round(g.value() * 1000), Math.round(tn));
                t[2] = String.format(Locale.ROOT, "BUILD %d MIN PARTS %d%%", Math.round(needTicks(d, g) / 1200.0), availPct(bill));
                t[3] = String.format(Locale.ROOT, "%d/%d IN CATALOGUE", Math.max(1, l.indexOf(d) + 1), l.size());
            }
            case 1 -> {
                int i = 0;
                for (Req q : bill) {
                    int have = B6.count(store(0), q.ok());
                    if (have >= q.n()) continue;
                    if (i < 4) t[i] = cut(String.format(Locale.ROOT, "%d/%d %s", have, q.n(), q.what().toUpperCase(Locale.ROOT)), 30);
                    i++;
                }
                if (i == 0) t[0] = "ALL PARTS IN STORES";
            }
            case 2 -> {
                for (int i = 0; i < 4; i++) {
                    if (i >= queue.size()) { t[i] = i == 0 ? "QUEUE EMPTY" : ""; continue; }
                    EntityRollingStockDefinition q = def(queue.get(i).split("\\|")[0]);
                    t[i] = cut((i + 1) + " " + (q == null ? "?" : q.name().toUpperCase(Locale.ROOT)), 30);
                }
            }
            default -> {
                for (int b = 0; b < 4; b++) {
                    if (b >= nb) { t[b] = b == 0 ? "NO ERECTING BAY" : ""; continue; }
                    EntityRollingStockDefinition q = job[b] == null ? null : def(job[b].split("\\|")[0]);
                    t[b] = q == null ? "BAY " + (b + 1) + " EMPTY" : cut(String.format(Locale.ROOT, "BAY %d %d%% %s", b + 1, prog[b] * 100 / Math.max(1, need[b]), q.name().toUpperCase(Locale.ROOT)), 30);
                }
            }
        }
        for (int i = 0; i < 4; i++) text("line" + (i + 1), t[i] == null ? "" : t[i]);
    }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) items : super.getCapability(c, f); }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        t.setString("queue", String.join(";", queue));
        for (int b = 0; b < 4; b++) { t.setString("job" + b, job[b] == null ? "" : job[b]); t.setInteger("prog" + b, prog[b]); t.setInteger("need" + b, need[b]); }
        t.setString("sel", sel); t.setString("fault", fault); t.setInteger("built", built); t.setBoolean("run", running);
        t.setString("log", String.join("\n", log));
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        queue.clear();
        for (String s : t.getString("queue").split(";")) if (!s.isEmpty()) queue.add(s);
        for (int b = 0; b < 4; b++) { String j = t.getString("job" + b); job[b] = j.isEmpty() ? null : j; prog[b] = t.getInteger("prog" + b); need[b] = t.getInteger("need" + b); }
        sel = t.getString("sel"); fault = t.getString("fault"); built = t.getInteger("built"); running = t.getBoolean("run");
        log.clear();
        for (String s : t.getString("log").split("\n")) if (!s.isEmpty()) log.add(s);
    }
}
