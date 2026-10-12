package com.dogpound.railmap.depot;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.block.TileFurniture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

/**
 * Crew Booking-On Desk (her ChatGPT list #84): the roster board shows every driver - on or off duty, on a break, hours
 * on duty, the train they have. SIGN ON / SIGN OFF / BREAK for whoever presses (a player becomes a driver the first
 * time), HIRE adds a depot driver, PASS ON (with the traction selector) and ROUTE give the selected driver a traction
 * qualification or route knowledge of the selected line, ASSIGN puts the selected driver on the selected train (a
 * handover if it had one), ATTENDED marks the selected line as needing a driver. The duty log keeps every sign-on,
 * handover, break and sign-off; PRINT gives it to you as a book.
 */
public class TileCrewDesk extends TileFurniture {
    private int dcur, tcur, lcur;
    private String lastMsg = "";

    public TileCrewDesk() {
        super(new String[]{"cd1", "cd2", "lk"}, new String[]{"Desk drawer", "Desk drawer", "Crew lockers"}, new int[]{9, 9, 27}, 4);
    }

    @Override public String wiredName() { return "Crew booking-on desk"; }
    @Override public String gearState() { long on = FleetData.get(world).drivers.values().stream().filter(d -> d.onDuty).count(); return on + " driver(s) on duty"; }
    @Override protected boolean mechanical(String id) { return id.startsWith("s_"); }
    @Override public double[][] shelfSpots() { return SPOTS; }
    static double[][] SPOTS = {{-12.1, 18.0, 12.1}, {-9.0, 18.0, 12.1}, {-5.9, 18.0, 12.1}, {-2.8, 18.0, 12.1}};

    List<FleetData.Driver> drivers() { return new ArrayList<>(FleetData.get(world).drivers.values()); }
    FleetData.Driver driver() { List<FleetData.Driver> l = drivers(); if (l.isEmpty()) return null; dcur = Math.floorMod(dcur, l.size()); return l.get(dcur); }
    List<UUID> trains() { List<UUID> l = new ArrayList<>(RailwayData.get(world).trains().keySet()); l.sort(Comparator.comparing(u -> FleetData.get(world).unit(u).name)); return l; }
    UUID train() { List<UUID> l = trains(); if (l.isEmpty()) return null; tcur = Math.floorMod(tcur, l.size()); return l.get(tcur); }
    String line() { List<String> l = new ArrayList<>(RailwayData.get(world).lines().keySet()); Collections.sort(l); if (l.isEmpty()) return ""; lcur = Math.floorMod(lcur, l.size()); return l.get(lcur); }

    private FleetData.Driver me(FleetData f, EntityPlayer who) {
        if (who == null) return driver();
        for (FleetData.Driver d : f.drivers.values()) if (who.getUniqueID().equals(d.player)) return d;
        FleetData.Driver d = new FleetData.Driver();
        d.name = who.getName();
        d.player = who.getUniqueID();
        f.drivers.put(d.name, d);
        f.crewNote(world, d.name + " joined the roster");
        return d;
    }

    private long testUntil;

    /** LAMP TEST lights every lamp and segment for 3 s */
    @Override public int controlValue(String id) { return term.powered() && ctl.get("lamptest") == 1 && !mechanical(id) ? Math.max(1, ctl.get(id)) : super.controlValue(id); }
    @Override public void update() { super.update(); if (!world.isRemote && testUntil > 0 && world.getTotalWorldTime() > testUntil) { testUntil = 0; ctl.set("lamptest", 0); controlsChanged(); } }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.equals("test")) { testUntil = world.getTotalWorldTime() + 60; ctl.set("lamptest", 1); return "§7Lamp test"; }
        FleetData f = FleetData.get(world);
        FleetData.Driver d = driver();
        String r = switch (id) {
            case "prev" -> { dcur--; yield ""; }
            case "next" -> { dcur++; yield ""; }
            case "tprev" -> { tcur--; yield ""; }
            case "tnext" -> { tcur++; yield ""; }
            case "lprev" -> { lcur--; yield ""; }
            case "lnext" -> { lcur++; yield ""; }
            case "signon" -> Crew.signOn(world, f, me(f, who));
            case "signoff" -> { FleetData.Driver x = me(f, who); yield x == null ? "§7No driver" : Crew.signOff(world, f, x); }
            case "brk" -> { FleetData.Driver x = me(f, who); yield x == null ? "§7No driver" : Crew.takeBreak(world, f, x); }
            case "hire" -> {
                int n = 1;
                while (f.drivers.containsKey("Driver " + n)) n++;
                FleetData.Driver x = new FleetData.Driver();
                x.name = "Driver " + n;
                f.drivers.put(x.name, x);
                f.crewNote(world, x.name + " hired (depot driver, passed on diesel)");
                f.markDirty();
                yield "§a" + x.name + " hired";
            }
            case "qual" -> {
                if (d == null) yield "§7No driver selected";
                int bit = 1 << Math.floorMod(ctl.get("s_trac"), 3);
                d.quals ^= bit;
                f.markDirty();
                yield "§e" + d.name + ((d.quals & bit) != 0 ? " passed on " : " no longer passed on ") + Crew.TRACTION[Integer.numberOfTrailingZeros(bit)];
            }
            case "route" -> {
                String l = line();
                if (d == null || l.isEmpty()) yield "§7Select a driver and a line";
                boolean had = !d.routes.remove(l);
                if (had) d.routes.add(l);
                f.markDirty();
                yield "§e" + d.name + (had ? " signed for the route " : " no longer signed for ") + l;
            }
            case "attend" -> {
                String l = line();
                if (l.isEmpty()) yield "§7No lines";
                boolean on = !f.attended.remove(l);
                if (on) f.attended.add(l);
                f.crewNote(world, l + (on ? " ATTENDED - driverless running not authorised" : " unattended running authorised"));
                yield "§e" + l + (on ? " ATTENDED: trains need a driver" : " may run unattended");
            }
            case "assign" -> {
                UUID t = train();
                if (d == null || t == null) yield "§7Select a driver and a train";
                Locomotive l = Autopilot.find(world, t);
                String no = Crew.cannot(world, d, l, RailwayData.get(world).train(t) == null ? "" : RailwayData.get(world).train(t).line);
                String res = Crew.assign(world, f, d, t, "");
                yield no == null ? res : res + " §6(" + no + ")";
            }
            case "print" -> {
                if (who == null) yield "§7Collect the printout at the desk";
                who.inventory.addItemStackToInventory(Diagnostics.book("Crew duty log", "Crew desk", new ArrayList<>(f.crewLog)));
                yield "§aDuty log printed";
            }
            default -> null;
        };
        if (r != null && !r.isEmpty()) lastMsg = net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(r);
        return r;
    }

    @Override
    protected boolean refresh() {
        FleetData f = FleetData.get(world);
        boolean ch = StationControls.put(ctl, "pwr", true);
        List<FleetData.Driver> ds = drivers();
        if (!ds.isEmpty()) dcur = Math.floorMod(dcur, ds.size());
        int top = ds.isEmpty() ? 0 : dcur / 6 * 6, onDuty = 0;
        boolean over = false, due = false, noDriver = false;
        for (FleetData.Driver d : ds) {
            if (!d.onDuty) continue;
            onDuty++;
            if (Crew.dutyHours(world, d) >= DepotConfig.dutyMaxHours) over = true;
            if (Crew.sinceBreak(world, d) >= DepotConfig.breakAfterHours) due = true;
        }
        for (int i = 0; i < 6; i++) {
            FleetData.Driver d = top + i < ds.size() ? ds.get(top + i) : null;
            String s = "";
            if (d != null) {
                String st = !d.onDuty ? "OFF" : Crew.onBreak(world, d) ? "BREAK" : "ON";
                s = String.format(Locale.ROOT, "%-10s %-5s %4.1fH %s %s", cut(d.name.toUpperCase(Locale.ROOT), 10), st, Crew.dutyHours(world, d),
                        ((d.quals & 1) != 0 ? "D" : "-") + ((d.quals & 2) != 0 ? "E" : "-") + ((d.quals & 4) != 0 ? "S" : "-"), d.train == null ? "" : cut(f.unit(d.train).name.toUpperCase(Locale.ROOT), 12));
            }
            ch |= StationControls.text(ctl, "d" + (i + 1), s);
            ch |= StationControls.put(ctl, "d" + (i + 1) + "s", d != null && top + i == dcur);
        }
        for (RailwayData.AutoTrain a : RailwayData.get(world).trains().values())
            if (!a.line.isEmpty() && f.attended.contains(a.line) && Crew.driverOf(f, a.loco) == null) noDriver = true;
        UUID t = train();
        String l = line();
        ch |= StationControls.text(ctl, "tsel", t == null ? "NO TRAINS" : cut(f.unit(t).name.toUpperCase(Locale.ROOT), 18));
        ch |= StationControls.text(ctl, "lsel", l.isEmpty() ? "NO LINES" : cut(l.toUpperCase(Locale.ROOT), 12) + (f.attended.contains(l) ? " ATTENDED" : " UNATT."));
        ch |= StationControls.text(ctl, "clock", DepotClock.stamp(world));
        ch |= StationControls.put(ctl, "ndrv", onDuty) | StationControls.put(ctl, "overh", over) | StationControls.put(ctl, "brkdue", due) | StationControls.put(ctl, "nodrv", noDriver);
        for (int i = 0; i < 4; i++) ch |= StationControls.text(ctl, "m" + (i + 1), i < f.crewLog.size() ? cut(f.crewLog.get(i).toUpperCase(Locale.ROOT), 44) : "");
        ch |= StationControls.text(ctl, "msg", cut(lastMsg, 44));
        return ch;
    }

    @Override protected void writeExtra(NBTTagCompound t) { t.setInteger("dcur", dcur); t.setInteger("tcur", tcur); t.setInteger("lcur", lcur); }
    @Override protected void readExtra(NBTTagCompound t) { dcur = t.getInteger("dcur"); tcur = t.getInteger("tcur"); lcur = t.getInteger("lcur"); }
}
