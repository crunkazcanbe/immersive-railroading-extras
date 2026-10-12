package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.stn.StnUtil;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.command.ICommandSender;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** /irextras stnlife [selftest|bus|taxi|hotel|pub|lift] - batch 6c station life */
public final class LifeCommand {
    private LifeCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("stnlife")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(java.util.Locale.ROOT) : "help";
        try {
            switch (sub) {
                case "selftest" -> {
                    List<String> f = selfTest();
                    say(s, f.isEmpty() ? "§aStation life self-test: all passed" : "§cStation life self-test: " + f.size() + " failed");
                    for (String x : f) say(s, "§c - " + x);
                }
                case "bus" -> list(w, s, TileBusBoard.class, TileBusBay.class);
                case "taxi" -> list(w, s, TileTaxiRank.class);
                case "hotel" -> list(w, s, TileHotelDesk.class, TileRoomLock.class, TileRoomWardrobe.class);
                case "pub" -> list(w, s, TileBar.class, TileKitchen.class);
                case "lift" -> list(w, s, TileBoardingLift.class, TileGapFiller.class);
                default -> say(s, "§e/irextras stnlife §7selftest · bus · taxi · hotel · pub · lift");
            }
        } catch (RuntimeException | LinkageError e) {
            say(s, "§c/irextras stnlife " + sub + " failed: " + e);
        }
        return true;
    }

    @SafeVarargs
    private static void list(World w, ICommandSender s, Class<? extends TileEntity>... cs) {
        int n = 0;
        for (Class<? extends TileEntity> c : cs)
            for (TileEntity t : StnUtil.loaded(w, c)) {
                IWiredGear g = (IWiredGear) t;
                say(s, "§b" + g.wiredName() + " §7@ " + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ() + " §f" + g.gearState());
                n++;
            }
        if (n == 0) say(s, "§7None loaded");
    }

    static List<String> selfTest() {
        List<String> f = new ArrayList<>(TileBusBoard.selfTest());
        f.addAll(TileKitchen.selfTest());
        TileBoardingLift l = new TileBoardingLift();
        l.deck = 6;
        if (l.top() != 8 || l.level() != 2) f.add("lift deck top / model step (gates shut)");
        l.gate = true;
        if (l.level() != 2 + 5) f.add("lift model step (gates open)");
        for (double[] b : l.boxes()) if (b[2] == 15 && b[5] == 16) f.add("track gate box present while the gate is open");
        TileGapFiller g = new TileGapFiller();
        if (g.boxes().size() != 2 || !g.in()) f.add("gap filler retracted must be just the housing");
        g.ext = 1;
        if (!g.out() || g.boxes().get(2)[5] != 30) f.add("gap filler out must reach 14 px past the edge");
        if (TileHotelDesk.RATE[2] != 250) f.add("suite rate");
        if (TileTaxiRank.TARIFF[1] != 150) f.add("night tariff");
        return f;
    }

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }
}
