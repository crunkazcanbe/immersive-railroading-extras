package com.dogpound.railmap.modelrail;

import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Locale;

/** /irextras modelrail status | selftest | demo - the model railway table (her ChatGPT list #8) */
public final class ModelRailCommand {
    private ModelRailCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("modelrail")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> {
                int n = 0;
                for (TileEntity t : w.loadedTileEntityList)
                    if (t instanceof TileModelRailway m && t.getPos().distanceSq(s.getPosition()) < 64 * 64) {
                        n++;
                        say(s, "§e" + t.getPos().getX() + " " + t.getPos().getY() + " " + t.getPos().getZ() + "§7: " + m.gearState() + ", table " + (m.tableOk ? m.tw + "x" + m.td + " (" + m.lay.w + "x" + m.lay.d + " squares)" : "missing")
                                + ", " + m.lay.trains.size() + " trains, " + m.lay.count(MiniLayout.POINTS) + " points, " + m.lay.count(MiniLayout.SIGNAL) + " signals, mains " + (m.live() ? "ok" : "off") + (m.mirror() ? ", MIRROR" : ""));
                    }
                if (n == 0) say(s, "§7No Model Railway Control Desk within 64 blocks");
            }
            case "demo" -> {
                TileModelRailway best = null;
                for (TileEntity t : w.loadedTileEntityList)
                    if (t instanceof TileModelRailway m && t.getPos().distanceSq(s.getPosition()) < 16 * 16) best = m;
                if (best == null) { say(s, "§cStand within 16 blocks of a Model Railway Control Desk"); return true; }
                if (!best.tableOk) { say(s, "§cBuild a 2x2 to 4x4 Model Railway Table behind the desk first"); return true; }
                if (!best.lay.trains.isEmpty()) { say(s, "§cLift the trains first (layout editor)"); return true; }
                best.lay.demo();
                if (s.canUseCommand(2, "irextras")) {
                    best.dropAll();
                    net.minecraft.inventory.IInventory box = best.storage(0);
                    box.setInventorySlotContents(0, new ItemStack(ModelRailRegistry.stock, 1, 0));
                    box.setInventorySlotContents(1, new ItemStack(ModelRailRegistry.stock, 2, 3));
                    box.setInventorySlotContents(2, new ItemStack(ModelRailRegistry.stock, 1, 1));
                    box.setInventorySlotContents(3, new ItemStack(ModelRailRegistry.stock, 1, 4));
                    box.setInventorySlotContents(4, new ItemStack(ModelRailRegistry.stock, 1, 6));
                }
                best.controlsChanged();
                say(s, "§aStarter layout laid" + (s.canUseCommand(2, "irextras") ? " and two trains' stock put in the stock box (old box contents dropped)" : "") + ". Right-click the table: PLACE A / PLACE B on a track square.");
            }
            case "selftest" -> selftest(s);
            default -> say(s, "§e/irextras modelrail §7status | demo | selftest");
        }
        return true;
    }

    /** headless: the starter layout runs a full lap, signals hold, points route into the siding, buffers stop, collisions short, curves derail, NBT round-trips */
    static void selftest(ICommandSender s) {
        int[] r = {0, 0};
        MiniLayout l = new MiniLayout();
        l.w = 8; l.d = 8;
        l.demo();
        check(s, r, "starter layout has points, a signal, a station and a buffer",
                l.count(MiniLayout.POINTS) == 1 && l.count(MiniLayout.SIGNAL) == 1 && l.count(MiniLayout.STATION) == 1 && l.count(MiniLayout.BUFFER) == 1);

        MiniLayout.Train t = train(1, 3);
        String why = l.place(t, 3, 6);
        l.trains.add(t);
        check(s, r, "a 3-car train places on the station road (" + why + ")", why == null);
        MiniLayout.Inputs in = new MiniLayout.Inputs();
        in.power = true; in.target[1] = 2.0;
        int sig = l.numbered(MiniLayout.SIGNAL).get(0);
        l.green[sig] = true;
        double travelled = 0;
        for (int i = 0; i < 400; i++) { l.tick(in, 0.05); travelled += Math.abs(t.v) * 0.05; if (t.v == 0 && i > 20) break; }
        check(s, r, "it runs 20 s round the oval without stopping (" + Math.round(travelled) + " squares)", travelled > 35 && t.v > 0 && !t.derailed);

        l.trains.clear();
        t = train(1, 1);
        t.loco = new MiniLayout.Loc(MiniLayout.idx(2, 1), 3, 1, 0.5);
        l.trains.add(t);
        l.green[sig] = false;
        for (int i = 0; i < 200; i++) l.tick(in, 0.05);
        check(s, r, "a red signal holds the train at it", t.loco.c == sig && Math.abs(t.loco.t - 0.45) < 0.02 && t.v == 0);
        l.green[sig] = true;
        for (int i = 0; i < 20; i++) l.tick(in, 0.05);
        check(s, r, "clearing the signal lets it go", t.loco.c != sig || t.loco.t > 0.5);

        l.trains.clear();
        t = train(1, 1);
        t.loco = new MiniLayout.Loc(MiniLayout.idx(6, 4), 0, 2, 0.5);
        l.trains.add(t);
        int pts = l.numbered(MiniLayout.POINTS).get(0);
        l.rev[pts] = true;
        in.target[1] = 1.5;
        for (int i = 0; i < 300; i++) l.tick(in, 0.05);
        check(s, r, "reversed points send it into the siding and the buffer stop holds it",
                l.type(t.loco.c) == MiniLayout.BUFFER && Math.abs(t.loco.t - 0.5) < 0.02 && t.v == 0);

        l.trains.clear();
        MiniLayout.Train a = train(1, 1), b = train(2, 1);
        a.loco = new MiniLayout.Loc(MiniLayout.idx(2, 6), 1, 3, 0.5);                // bottom road, heading west
        b.loco = new MiniLayout.Loc(MiniLayout.idx(1, 4), 0, 2, 0.5);                // left road heading south, round the corner into a
        l.trains.add(a); l.trains.add(b);
        in.target[1] = 1.5; in.target[2] = 1.5;
        String hit = null;
        for (int i = 0; i < 200 && hit == null; i++) hit = l.tick(in, 0.05).collision;
        check(s, r, "two trains meeting short the track (" + hit + ")", hit != null && a.v == 0 && b.v == 0);

        l.trains.clear();
        t = train(1, 1);
        t.loco = new MiniLayout.Loc(MiniLayout.idx(3, 6), 1, 3, 0.5);
        l.trains.add(t);
        in.target[1] = 8 * MiniLayout.STEP; in.inertia = false;
        String der = null;
        for (int i = 0; i < 200 && der == null; i++) der = l.tick(in, 0.05).derail;
        check(s, r, "full speed into a curve derails (" + der + ")", der != null && t.derailed);

        l.trains.clear();
        t = train(1, 2);
        t.loco = new MiniLayout.Loc(MiniLayout.idx(5, 6), 1, 3, 0.9);
        l.rev[pts] = false;
        l.trains.add(t);
        in.target[1] = 1.0; in.stationStops = true;
        boolean dwelt = false;
        for (int i = 0; i < 200 && !dwelt; i++) { l.tick(in, 0.05); dwelt = t.dwell > 0; }
        check(s, r, "station stops: the train calls at the platform", dwelt && l.type(t.loco.c) == MiniLayout.STATION);

        in.stationStops = false; in.target[1] = -1.0;
        MiniLayout.Loc start = t.loco.copy();
        t.dwell = 0;
        for (int i = 0; i < 20; i++) l.tick(in, 0.05);
        check(s, r, "the direction switch backs it up", t.v < 0 && (t.loco.c != start.c || t.loco.t < start.t));

        in.power = false;
        for (int i = 0; i < 20; i++) l.tick(in, 0.05);
        check(s, r, "no track power: it stops", t.v == 0);

        NBTTagCompound n = new NBTTagCompound();
        l.write(n);
        MiniLayout m = new MiniLayout();
        m.read(n);
        check(s, r, "the layout and its trains save and load", java.util.Arrays.equals(m.cell, l.cell) && m.trains.size() == 1 && m.w == 8 && m.trains.get(0).cars.length == 2);

        MiniLayout old = new MiniLayout();
        old.read(new NBTTagCompound());
        check(s, r, "an empty / old tag loads as an empty layout", old.trains.isEmpty() && old.count(MiniLayout.STRAIGHT) == 0);
        say(s, (r[1] == 0 ? "§aModel railway selftest PASS " : "§cModel railway selftest FAIL ") + r[0] + " ok, " + r[1] + " failed");
    }

    private static MiniLayout.Train train(int id, int cars) {
        MiniLayout.Train t = new MiniLayout.Train();
        t.id = id; t.name = "T" + id;
        t.cars = new int[cars];
        t.cars[0] = 0;
        for (int i = 1; i < cars; i++) t.cars[i] = 3;
        return t;
    }

    private static void check(ICommandSender s, int[] r, String what, boolean ok) {
        r[ok ? 0 : 1]++;
        say(s, (ok ? "§a  ok  §7" : "§c  FAIL §7") + what);
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
