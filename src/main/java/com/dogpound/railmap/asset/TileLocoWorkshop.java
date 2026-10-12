package com.dogpound.railmap.asset;

import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import com.dogpound.railmap.depot.*;
import com.dogpound.railmap.item.ItemPart;
import com.dogpound.railmap.server.WearData;
import com.dogpound.railmap.sim.SimData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;

import java.util.*;

/**
 * Locomotive Design + Rebuild Workshop (her 48-next list #28): the design office desk of the works. Six rotary
 * selectors set the design - TRACTION package, GEAR ratio, COOLING, STORAGE, BOGIES, BRAKES - for the locomotive in reach
 * (SELECT steps through those within 16 blocks, LOAD reads its current design). The screen and the gauges work out the
 * result from its real IR definition: mass and axle load, power, starting tractive effort, top speed, brake and cooling
 * margins, cost. REBUILD fits it (the locomotive standing, the parts in the desk drawers or the depot stores within 32
 * blocks: traction motors / power kits, gearboxes, fans, plates, batteries, bogies, brake blocks - and the old traction
 * gear goes back to the stores as cores). ACCEPT runs the acceptance test: static checks here, then a 2 km test run
 * that the workshop watches (Design.tick). PRINT gives the design sheet. Drawers and the cupboard are real storage, the
 * shelf shows what is on it.
 */
public class TileLocoWorkshop extends TileFurniture {
    private static final double REACH = 16;
    static final String[] SEL = {"s_motor", "s_gear", "s_cool", "s_store", "s_bogie", "s_brake"};
    static double[][] SPOTS = {{20.0, 7.0, 13.4}, {21.8, 7.0, 13.4}, {23.6, 7.0, 13.4}, {25.4, 7.0, 13.4}, {27.2, 7.0, 13.4}, {29.0, 7.0, 13.4}};
    private UUID linked;
    private String last = "";

    public TileLocoWorkshop() {
        super(new String[]{"d1", "d2", "d3", "d4", "d5", "d6", "cup"},
                new String[]{"Drawer: motors + power kits", "Drawer: gearboxes", "Drawer: fans + coolers", "Drawer: plates + batteries", "Drawer: bogies + brakes", "Drawer: drawings + certificates", "Parts cupboard"},
                new int[]{9, 9, 9, 9, 9, 9, 27}, 6);
    }

    @Override public String wiredName() { return "Loco design workshop"; }
    @Override public double[][] shelfSpots() { return SPOTS; }
    @Override protected boolean mechanical(String id) { return id.startsWith("s_"); }
    @Override public String gearState() { Locomotive l = loco(); return !term.powered() ? "NO 12 V" : l == null ? "no locomotive in reach" : DepotTicker.name(l) + " · " + state(unit(l)); }

    int[] design() { int[] d = new int[6]; for (int i = 0; i < 6; i++) { int v = Math.floorMod(ctl.get(SEL[i]), Design.NAMES[i].length); d[i] = v == 0 ? -1 : v; } return d; }

    private List<Locomotive> inReach() {
        List<Locomotive> out = new ArrayList<>();
        try {
            for (Locomotive l : cam72cam.mod.world.World.get(world).getEntities(Locomotive.class)) {
                Vec3d p = l.getPosition();
                if (!l.isDead() && pos.distanceSqToCenter(p.x, p.y, p.z) <= REACH * REACH) out.add(l);
            }
        } catch (LinkageError | RuntimeException ignored) { }
        out.sort(Comparator.comparingDouble(l -> pos.distanceSqToCenter(l.getPosition().x, l.getPosition().y, l.getPosition().z)));
        return out;
    }

    Locomotive loco() {
        List<Locomotive> l = inReach();
        if (l.isEmpty()) return null;
        if (linked != null) for (Locomotive x : l) if (x.getUUID().equals(linked)) return x;
        linked = l.get(0).getUUID();
        return l.get(0);
    }

    FleetData.Unit unit(Locomotive l) { return l == null ? null : FleetData.get(world).unit(l.getUUID()); }

    static String state(FleetData.Unit u) {
        if (u == null) return "";
        return new String[]{"AS BUILT", "REBUILT - NOT ACCEPTED", "TEST RUN", "ACCEPTED", "FAILED TEST"}[Math.max(0, Math.min(4, u.rebuild))];
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        Locomotive l = loco();
        FleetData.Unit u = unit(l);
        switch (id) {
            case "test" -> { ctl.set("lamptest", 1); ctl.pressAt = world.getTotalWorldTime(); return "§7Lamp test"; }
            case "sel" -> {
                List<Locomotive> all = inReach();
                if (all.isEmpty()) return "§7No locomotive within 16 blocks";
                int i = 0;
                for (int k = 0; k < all.size(); k++) if (all.get(k).getUUID().equals(linked)) i = (k + 1) % all.size();
                linked = all.get(i).getUUID();
                return "§bWorking on " + DepotTicker.name(all.get(i)) + " (" + (i + 1) + "/" + all.size() + ")";
            }
            case "load" -> {
                if (u == null) return "§7No locomotive in reach";
                for (int i = 0; i < 6; i++) ctl.set(SEL[i], Math.max(0, u.design[i]));
                return "§7Loaded the current design of " + u.name;
            }
            case "reset" -> { for (String s : SEL) ctl.set(s, 0); return "§7Selectors back to AS BUILT"; }
            case "apply" -> { return rebuild(l, u, who); }
            case "accept" -> { return accept(l, u, who); }
            case "print" -> { print(l, u); return "§7Printing the design sheet"; }
            default -> { return null; }
        }
    }

    // ---- parts: drawers first, then the depot stores within 32 blocks ---------------------------------------------------
    private List<IInventory> bins() {
        List<IInventory> o = new ArrayList<>();
        for (int k = 0; k < 7; k++) o.add(storage(k));
        for (TileDepotStores s : Stores.near(world, pos)) for (int k = 0; k < 6; k++) o.add(s.storage(k));
        return o;
    }

    private static boolean match(ItemStack s, String key) {
        if (s.isEmpty()) return false;
        String[] k = key.split(":");
        if (k[0].equals("spare")) return ItemSpare.is(s, ItemSpare.Spare.valueOf(k[1]));
        return s.getItem() instanceof ItemPart && s.getMetadata() == ItemPart.Part.valueOf(k[1]).ordinal();
    }

    private int count(String key) { int n = 0; for (IInventory inv : bins()) for (int i = 0; i < inv.getSizeInventory(); i++) if (match(inv.getStackInSlot(i), key)) n += inv.getStackInSlot(i).getCount(); return n; }

    private void take(String key, int want) {
        for (IInventory inv : bins()) for (int i = 0; i < inv.getSizeInventory() && want > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!match(s, key)) continue;
            int t = Math.min(want, s.getCount());
            s.shrink(t); want -= t;
            if (s.isEmpty()) inv.setInventorySlotContents(i, ItemStack.EMPTY);
            inv.markDirty();
        }
    }

    static String partName(String key) { String[] k = key.split(":"); return k[0].equals("spare") ? ItemSpare.Spare.valueOf(k[1]).label : ItemPart.Part.valueOf(k[1]).label; }

    String shortOf(FleetData.Unit u, int[] d, boolean electric) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : Design.parts(u, d, electric).entrySet()) {
            int have = count(e.getKey());
            if (have < e.getValue()) b.append(b.length() > 0 ? ", " : "").append(e.getValue() - have).append(" x ").append(partName(e.getKey()));
        }
        return b.toString();
    }

    private String rebuild(Locomotive l, FleetData.Unit u, EntityPlayer who) {
        if (l == null || u == null) return "§7No locomotive within 16 blocks";
        if (l.getCurrentSpeed() != null && Math.abs(l.getCurrentSpeed().metric()) > 0.5) return "§cThe locomotive must be standing";
        if (u.rebuild == 2) return "§cIt is on its acceptance test run";
        int[] d = design();
        boolean same = Arrays.equals(d, u.design);
        if (same) return "§7That is already its design";
        boolean electric = u.electric;
        String sh = shortOf(u, d, electric);
        if (!sh.isEmpty()) return "§cParts short: " + sh + " §7(desk drawers or the depot stores within 32 blocks)";
        Map<String, Integer> parts = Design.parts(u, d, electric);
        for (Map.Entry<String, Integer> e : parts.entrySet()) take(e.getKey(), e.getValue());
        // the old traction gear goes back to the stores as cores
        WearData.Record r = WearData.get(world).peek(l.getUUID());
        if (parts.containsKey("spare:TRACTION_MOTOR") || parts.containsKey("spare:POWER_KIT")) {
            float worn = r == null ? 30 : r.wear[WearData.Part.ENGINE.ordinal()];
            ItemSpare.Spare cs = electric ? ItemSpare.Spare.TRACTION_MOTOR : ItemSpare.Spare.POWER_KIT;
            for (int i = 0; i < 2; i++) Reman.toStores(world, pos, ItemSpare.core(cs, 100 - worn, u.name));
            if (r != null) { r.wear[WearData.Part.ENGINE.ordinal()] = 0; WearData.get(world).markDirty(); }
        }
        if (parts.containsKey("spare:BOGIE") && r != null) { r.wear[WearData.Part.WHEELS.ordinal()] = 0; r.wear[WearData.Part.BEARINGS.ordinal()] = 0; WearData.get(world).markDirty(); }
        try { u.baseKmh = (float) l.getDefinition().getMaxSpeed(l.gauge).metric(); } catch (LinkageError | RuntimeException ignored) { }
        System.arraycopy(d, 0, u.design, 0, 6);
        u.rebuild = Design.rebuilt(u) ? 1 : 0;
        u.rebuilds++;
        u.testPeak = 0;
        if (d[5] > 0 && DepotConfig.brakeTestAfterBrakeWork) { u.brakeWork = true; u.brakeOk = false; }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) if (d[i] > 0) b.append(b.length() > 0 ? ", " : "").append(Design.SLOTS[i]).append(' ').append(Design.NAMES[i][d[i]]);
        u.note(world, "REBUILT at the works by " + who(who) + ": " + (b.length() == 0 ? "back to AS BUILT" : b));
        Recorder.event(world, "REBUILD: " + u.name + " -> " + (b.length() == 0 ? "as built" : b));
        AssetData.get(world).note(world, "REBUILD " + u.name + ": " + (b.length() == 0 ? "as built" : b));
        FleetData.get(world).markDirty();
        last = "REBUILT - ACCEPTANCE DUE";
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.air_tool"), SoundCategory.BLOCKS, 0.8f, 1.0f);
        return u.rebuild == 0 ? "§a" + u.name + " restored to its as-built design" : "§a" + u.name + " rebuilt - §eNOT ACCEPTED: press ACCEPT for its acceptance test" + (AssetConfig.rebuildAcceptance ? " (held to 25 km/h until then)" : "");
    }

    private String accept(Locomotive l, FleetData.Unit u, EntityPlayer who) {
        if (l == null || u == null) return "§7No locomotive within 16 blocks";
        if (u.rebuild == 0 || u.rebuild == 3) return "§7" + u.name + " needs no acceptance test (" + state(u) + ")";
        if (u.rebuild == 2) return "§7The test run is on: " + String.format(Locale.ROOT, "%.2f km, peak %.0f km/h", u.km - u.testKm, u.testPeak);
        SimData.Car c = SimData.get(world).peek(l.getUUID());
        if (c != null && !c.faults.isEmpty()) return "§cStatic test FAILED: active fault " + c.faults.iterator().next() + " - reset it at the Loco Diagnostic Panel";
        if (!u.brakeOk) return "§cStatic test FAILED: no brake test certificate since the brake work - test it on the Brake Test Rig";
        WearData.Record r = WearData.get(world).peek(l.getUUID());
        if (r != null && r.wear[WearData.Part.ELECTRICAL.ordinal()] >= 80) return "§cStatic test FAILED: control electronics CRITICAL - repair them first";
        u.rebuild = 2;
        u.testKm = u.km;
        u.testStart = world.getTotalWorldTime();
        u.testPeak = 0;
        u.note(world, "acceptance TEST RUN started by " + who(who));
        Recorder.event(world, "REBUILD: " + u.name + " acceptance test run started");
        FleetData.get(world).markDirty();
        last = "TEST RUN";
        return "§6Static tests passed. TEST RUN: drive it 2 km and reach 60 % of its design speed within 10 minutes, with no faults or overheating";
    }

    private String who(EntityPlayer p) { return p == null ? "console" : p.getName(); }

    @Override
    protected boolean refresh() {
        Locomotive l = loco();
        FleetData.Unit u = unit(l);
        boolean ch = StationControls.put(ctl, "pwr", true);
        ch |= StationControls.put(ctl, "link", l != null);
        int[] d = design();
        Design.Sheet s = l == null ? null : Design.sheet(l, d);
        String sh = u == null ? "" : shortOf(u, d, u.electric);
        ch |= StationControls.put(ctl, "pend", u != null && (u.rebuild == 1 || u.rebuild == 4));
        ch |= StationControls.put(ctl, "run", u != null && u.rebuild == 2);
        ch |= StationControls.put(ctl, "acc", u != null && u.rebuild == 3);
        ch |= StationControls.put(ctl, "fail", u != null && u.rebuild == 4);
        ch |= StationControls.put(ctl, "parts", u != null && !Arrays.equals(d, u.design) && !sh.isEmpty());
        ch |= StationControls.put(ctl, "warn", s != null && !s.notes.isEmpty());
        ch |= StationControls.put(ctl, "gpow", s == null ? 0 : (int) Math.round(s.powerPct));
        ch |= StationControls.put(ctl, "gvmax", s == null ? 0 : (int) Math.round(s.vmaxKmh));
        ch |= StationControls.put(ctl, "gaxle", s == null ? 0 : (int) Math.round(s.axleT));
        ch |= StationControls.put(ctl, "mass", s == null ? 0 : (int) Math.round(s.massT));
        ch |= StationControls.put(ctl, "kw", s == null ? 0 : (int) Math.round(s.powerKw));
        ch |= StationControls.put(ctl, "te", s == null ? 0 : (int) Math.round(s.teKn));
        ch |= StationControls.put(ctl, "vmax", s == null ? 0 : (int) Math.round(s.vmaxKmh));
        ch |= StationControls.put(ctl, "cost", s == null ? 0 : s.cost);
        String[] t = new String[6];
        if (l == null) { t[0] = "NO LOCOMOTIVE"; t[1] = "WITHIN 16 BLOCKS"; }
        else {
            t[0] = cut(DepotTicker.name(l).toUpperCase(Locale.ROOT), 30);
            t[1] = cut(state(u) + (u != null && u.rebuild == 2 ? String.format(Locale.ROOT, " %.1fKM %.0fKMH", u.km - u.testKm, u.testPeak) : ""), 30);
            t[2] = String.format(Locale.ROOT, "%.0fT %.1fT/AXLE BRK %.0f%%", s.massT, s.axleT, s.brakePct);
            t[3] = String.format(Locale.ROOT, "%.0fKW %.0f%% TE %.0fKN", s.powerKw, s.powerPct, s.teKn);
            t[4] = String.format(Locale.ROOT, "VMAX %.0f COOL %.0f%% $%,d", s.vmaxKmh, s.coolPct, s.cost);
            t[5] = !sh.isEmpty() && u != null && !Arrays.equals(d, u.design) ? cut("SHORT: " + sh.toUpperCase(Locale.ROOT), 30) : !s.notes.isEmpty() ? cut(s.notes.get(0), 30) : cut(last, 30);
        }
        for (int i = 0; i < 6; i++) ch |= StationControls.text(ctl, "d" + (i + 1), t[i] == null ? "" : t[i]);
        for (int i = 0; i < 6; i++) ch |= StationControls.text(ctl, "n" + (i + 1), Design.NAMES[i][Math.floorMod(ctl.get(SEL[i]), Design.NAMES[i].length)]);
        if (ctl.get("lamptest") == 1 && world.getTotalWorldTime() - ctl.pressAt > 60) { ctl.set("lamptest", 0); ch = true; }
        return ch;
    }

    @Override public int controlValue(String id) { return term.powered() && ctl.get("lamptest") == 1 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }

    private void print(Locomotive l, FleetData.Unit u) {
        List<String> o = new ArrayList<>();
        o.add("DESIGN SHEET " + DepotClock.stamp(world));
        if (l == null) o.add("no locomotive");
        else {
            int[] d = design();
            Design.Sheet s = Design.sheet(l, d);
            o.add(DepotTicker.name(l) + " - " + state(u));
            for (int i = 0; i < 6; i++) o.add(Design.SLOTS[i] + ": " + Design.NAMES[i][Math.max(0, d[i])] + (u != null && u.design[i] != d[i] ? " (now " + Design.NAMES[i][Math.max(0, u.design[i])] + ")" : ""));
            o.add(String.format(Locale.ROOT, "Mass %.1f t, %.1f t per axle", s.massT, s.axleT));
            o.add(String.format(Locale.ROOT, "Power %.0f kW (%.0f %%), TE %.0f kN", s.powerKw, s.powerPct, s.teKn));
            o.add(String.format(Locale.ROOT, "Top speed %.0f km/h, brakes %.0f %%, cooling %.0f %%", s.vmaxKmh, s.brakePct, s.coolPct));
            o.add(String.format(Locale.ROOT, "Cost $%,d", s.cost));
            if (u != null) for (Map.Entry<String, Integer> e : Design.parts(u, d, u.electric).entrySet()) o.add("Parts: " + e.getValue() + " x " + partName(e.getKey()) + " (have " + count(e.getKey()) + ")");
            o.addAll(s.notes);
        }
        ItemStack book = Diagnostics.book(o.get(0), "Loco works", o);
        if (!Stores.put(storage(5), book)) world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, book));
    }

    @Override protected void writeExtra(NBTTagCompound t) { if (linked != null) t.setUniqueId("linked", linked); t.setString("last", last); }
    @Override protected void readExtra(NBTTagCompound t) { linked = t.hasUniqueId("linked") ? t.getUniqueId("linked") : null; last = t.getString("last"); }
}
