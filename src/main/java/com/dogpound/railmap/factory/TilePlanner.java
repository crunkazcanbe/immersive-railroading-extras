package com.dogpound.railmap.factory;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.ctl.CtlConfig;
import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.machine.TileMachine;
import com.dogpound.railmap.safety.SafetyTile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

/**
 * Production Planning Console (her "next 48" list #23): the plant-wide planner's desk.
 * <ul>
 *   <li>MODE: OFF / ADVISE (works out the plan, shows it, touches nothing) / AUTO (pauses plants that lose their power
 *       bid or are overstocked, switches the rolling mill to its rail pass when rails are wanted);</li>
 *   <li>RAIL and STEEL targets per hour, FREIGHT ON/OFF (plan for the freight contracts that must depart), VIEW pages the
 *       screen: STAGES (demand / capacity / utilisation per stage, the bottleneck), POWER (headroom and clearing price
 *       per network), FREIGHT (each departure: ready / on plan / AT RISK), PLANTS (every plant's bid and verdict);</li>
 *   <li>a lamp per stage (red = bottleneck), HELD, FREIGHT AT RISK; REPLAN, RELEASE (lift every hold), PRINT (the plan
 *       as a book).</li>
 * </ul>
 * Plants it pauses show "PLANNER: ..." on their own panel. Switching it OFF or breaking it releases them at once.
 */
public class TilePlanner extends SafetyTile implements com.dogpound.railmap.block.IFurniture {
    private final com.dogpound.railmap.ctl.Drawers drawers = new com.dogpound.railmap.ctl.Drawers(this, new String[]{"dr1", "dr2", "cab"}, new String[]{"Plans drawer", "Production records", "Reports cabinet"}, new int[]{9, 18, 27});
    @Override public net.minecraft.inventory.IInventory storage(int k) { return drawers.storage(k); }
    @Override public String storeName(int k) { return drawers.name(k); }
    @Override public int storeSize(int k) { return drawers.size(k); }
    @Override public void dropAll() { drawers.dropAll(this); }

    static final int[] RAIL = {0, 16, 32, 64, 128}, STEEL = {0, 16, 32, 64};
    private final Set<Long> holding = new HashSet<>();
    private Planner.Plan last;

    @Override public String wiredName() { return "Production planner"; }
    int mode() { return Math.min(2, ctl.get("s_mode")); }
    public Planner.Plan plan() { return last; }

    @Override public String gearState() { return !powered() ? "dark - no 12 V" : mode() == 0 ? "OFF" : (mode() == 1 ? "ADVISE" : "AUTO") + (last == null ? "" : String.format(Locale.ROOT, " · bottleneck %s %.0f%% · %d held · %d departures at risk", last.bottleneck == null ? "-" : last.bottleneck.label, last.bottleneckUtil * 100, last.held, last.atRisk)); }
    @Override public int wiredLamp() { return !powered() ? 3 : last != null && last.atRisk > 0 ? 2 : 1; }
    @Override public void gearInfo(Map<String, Object> m) { m.put("mode", new String[]{"OFF", "ADVISE", "AUTO"}[mode()]); if (last != null) { m.put("bottleneck", last.bottleneck == null ? "-" : last.bottleneck.label); m.put("held", last.held); m.put("atRisk", last.atRisk); m.put("clearingPerMWh", last.clearing); } }

    @Override
    protected void serverTick() {
        if (age % 100 != 17) { if (age % 20 == 17) live(); return; }
        if (!powered() || mode() == 0) { release("planner " + (powered() ? "OFF" : "lost 12 V")); last = null; live(); return; }
        replan();
        live();
    }

    private void replan() {
        last = Planner.plan(world, RAIL[Math.min(4, ctl.get("s_rail"))], STEEL[Math.min(3, ctl.get("s_steel"))], ctl.get("s_frt") == 0);
        Set<Long> now = new HashSet<>();
        if (mode() == 2 && CtlConfig.plannerMayPause) {
            for (Planner.PlantRow r : last.rows) {
                long p = r.m.getPos().toLong();
                if (!r.hold.isEmpty()) {
                    if (!r.hold.equals(r.m.planHold)) { r.m.planHold = r.hold; Recorder.event(world, "PLANNER: " + r.m.type().label + " at " + r.m.getPos().getX() + "," + r.m.getPos().getZ() + " - " + r.hold); }
                    now.add(p);
                } else if (holding.contains(p)) r.m.planHold = "";
                if (r.m.type() == MachineType.ROLLING_MILL && last.demand.get(Planner.Stage.ROLL) > 0 && r.m.planGet("gap") != 2) { r.m.planSet("gap", 2); Recorder.event(world, "PLANNER: rolling mill at " + r.m.getPos().getX() + "," + r.m.getPos().getZ() + " switched to the RAIL pass"); }
            }
        } else for (Planner.PlantRow r : last.rows) if (holding.contains(r.m.getPos().toLong())) r.m.planHold = "";
        for (long p : holding) if (!now.contains(p) && world.isBlockLoaded(BlockPos.fromLong(p)) && world.getTileEntity(BlockPos.fromLong(p)) instanceof TileMachine m) m.planHold = "";
        holding.clear();
        holding.addAll(now);
        String key = "plan:" + pos.toLong();
        if (last.atRisk > 0) AlarmManager.get(world).raise(world, key, AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MINOR, "Production plan: " + last.atRisk + " freight departure(s) AT RISK - bottleneck " + (last.bottleneck == null ? "-" : last.bottleneck.label), pos, true);
        else AlarmManager.get(world).clear(key);
    }

    private void release(String why) {
        if (holding.isEmpty()) return;
        for (long p : holding) if (world.isBlockLoaded(BlockPos.fromLong(p)) && world.getTileEntity(BlockPos.fromLong(p)) instanceof TileMachine m) m.planHold = "";
        Recorder.event(world, "PLANNER: " + holding.size() + " plant(s) released (" + why + ")");
        holding.clear();
    }

    private void live() {
        Planner.Plan p = last;
        int i = 0;
        for (Planner.Stage s : Planner.Stage.values()) {
            put("st" + i, p != null && p.plants.get(s) > 0);
            put("bn" + i, p != null && s == p.bottleneck && p.bottleneckUtil > 0.9);
            i++;
        }
        put("pwr", powered());
        put("auto", powered() && mode() == 2);
        put("held", p != null && p.held > 0);
        put("risk", p != null && p.atRisk > 0);
        put("g_util", p == null ? 0 : (int) Math.min(200, Math.round(p.bottleneckUtil * 100)));
        put("g_bid", p == null ? 0 : (int) Math.min(300, Math.round(p.clearing)));
        String[] views = {"STAGES", "POWER", "FREIGHT", "PLANTS"};
        int v = Math.min(3, ctl.get("s_view"));
        text("hd", (mode() == 0 ? "PLANNER OFF" : (mode() == 1 ? "ADVISE" : "AUTO")) + " " + views[v] + (p == null ? "" : String.format(Locale.ROOT, " BN %s %.0f%%", p.bottleneck == null ? "-" : p.bottleneck.name(), p.bottleneckUtil * 100)));
        List<String> lines = new ArrayList<>();
        if (p != null) {
            switch (v) {
                case 0: for (Planner.Stage s : Planner.Stage.values()) if (p.plants.get(s) > 0 || p.demand.get(s) > 0) lines.add(String.format(Locale.ROOT, "%-5s N%d D%.0f C%.0f %3.0f%% S%.0f", s.name(), p.plants.get(s), p.demand.get(s), p.capacity.get(s), Math.min(999, p.util(s) * 100), p.stock.get(s))); break;
                case 1: lines.addAll(p.power); break;
                case 2: lines.addAll(p.freight); break;
                default: for (Planner.PlantRow r : p.rows) lines.add(String.format(Locale.ROOT, "%s $%.0f %s %s", r.m.type().label.toUpperCase(Locale.ROOT), r.bid, r.verdict.isEmpty() ? "-" : r.verdict, r.hold.isEmpty() ? (r.m.planRunning() ? "RUN" : "STOP") : "HELD"));
            }
        }
        if (lines.isEmpty()) lines.add(p == null ? "NO PLAN" : "NOTHING TO SHOW");
        for (int k = 0; k < 6; k++) text("s" + (k + 1), k < lines.size() ? lines.get(k).toUpperCase(Locale.ROOT) : "");
        text("tg", "RAIL " + RAIL[Math.min(4, ctl.get("s_rail"))] + "/H STEEL " + STEEL[Math.min(3, ctl.get("s_steel"))] + "/H FRT " + (ctl.get("s_frt") == 0 ? "ON" : "OFF"));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        { String dr = drawers.open(id, who, this); if (dr != null) return dr; }
        switch (id) {
            case "test": return lampTest();
            case "release": release("RELEASE by " + who(who)); for (Planner.PlantRow r : last == null ? Collections.<Planner.PlantRow>emptyList() : last.rows) r.m.planHold = ""; return "§aEvery planner hold lifted";
            case "replan":
                if (!powered() || mode() == 0) return "§cPlanner is " + (powered() ? "OFF" : "dead");
                replan(); live(); return "§eReplanned: bottleneck " + (last.bottleneck == null ? "none" : last.bottleneck.label) + ", " + last.held + " plant(s) held";
            case "print": print(who); return "§7Printing the production plan...";
            default: return null;
        }
    }

    private void print(EntityPlayer who) {
        if (last == null) replan();
        List<String> all = new ArrayList<>();
        all.add("§lPRODUCTION PLAN§r");
        all.add(Recorder.clock(world, world.getTotalWorldTime()) + " " + new String[]{"OFF", "ADVISE", "AUTO"}[mode()]);
        all.add("Bottleneck: " + (last.bottleneck == null ? "-" : last.bottleneck.label) + String.format(Locale.ROOT, " %.0f%%", last.bottleneckUtil * 100));
        all.add("");
        for (Planner.Stage s : Planner.Stage.values()) all.add(String.format(Locale.ROOT, "%s: %d plants, need %.0f/h, can %.0f/h, stock %.0f", s.label, last.plants.get(s), last.demand.get(s), last.capacity.get(s), last.stock.get(s)));
        all.add("");
        all.add("§lPOWER§r"); all.addAll(last.power);
        all.add("§lFREIGHT§r"); all.addAll(last.freight);
        all.add("§lPLANTS§r");
        for (Planner.PlantRow r : last.rows) all.add(r.m.type().label + " $" + Math.round(r.bid) + " " + r.verdict + (r.hold.isEmpty() ? "" : " - " + r.hold));
        NBTTagList pages = new NBTTagList();
        StringBuilder pg = new StringBuilder();
        int n = 0;
        for (String l : all) {
            if (n >= 9) { pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(pg.toString())))); pg = new StringBuilder(); n = 0; }
            pg.append(l.length() > 70 ? l.substring(0, 69) + "." : l).append('\n');
            n += 1 + l.length() / 22;
        }
        pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(pg.toString()))));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", "Production Plan");
        t.setString("author", "Planner");
        t.setTag("pages", pages);
        book.setTagCompound(t);
        world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, book));
    }

    @Override public void invalidate() { super.invalidate(); if (world != null && !world.isRemote) release("planner removed"); }
    @Override public void onChunkUnload() { super.onChunkUnload(); if (world != null && !world.isRemote) release("planner unloaded"); }

    @Override protected void save(NBTTagCompound t) { drawers.write(t); }
    @Override protected void load(NBTTagCompound t) { drawers.read(t); }
}
