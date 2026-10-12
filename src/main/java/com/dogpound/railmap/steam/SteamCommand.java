package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.command.ICommandSender;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras steam                 every steam boiler: scale, hardness, ash, leaks, hours since washout
 * /irextras steam plant           the loaded servicing plant (coaling, water, ash, sand, washout) and its state
 * /irextras steam scale <mm>      set the nearest steam loco's scale (test)
 * /irextras steam ash <kg>        set the nearest steam loco's ash pan (test)
 * /irextras steam log             the steam depot log
 * /irextras steam selftest        checks the boiler / water / ash sums
 */
public final class SteamCommand {
    private SteamCommand() {}

    public static final String USAGE = "steam [plant|scale <mm>|ash <kg>|log|selftest]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("steam")) return false;
        try { run(w, s, a); } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    private static void run(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        SteamData d = SteamData.get(w);
        switch (sub) {
            case "" -> {
                say(s, "§6Steam boilers: §f" + d.all().size() + " known");
                for (LocomotiveSteam l : cam72cam.mod.world.World.get(w).getEntities(LocomotiveSteam.class)) {
                    SteamData.Boiler b = d.of(l.getUUID());
                    say(s, String.format(Locale.ROOT, "§f%s §7scale %.2f mm (+%d%% coal) · %d ppm · ash %.0f/%d kg · %.1f h since washout · %d washouts%s", Stock.name(l), b.scaleMm,
                            Math.round(SteamTicker.loss(b) * 100), Math.round(SteamTicker.hardness(d, l)), b.ashKg, SteamConfig.ashPanKg, b.steamH, b.washouts, b.leak ? " §cTUBES LEAKING" : ""));
                }
                say(s, "§7" + USAGE);
            }
            case "plant" -> {
                int n = 0;
                for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
                    if ((t instanceof TileCoalingTower || t instanceof TileCoalPit || t instanceof TileWaterColumn || t instanceof TileAshPit || t instanceof TileSandTower || t instanceof TileBoilerWashout) && !t.isInvalid()) {
                        IWiredGear g = (IWiredGear) t;
                        say(s, "§f" + g.wiredName() + " §7" + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ() + " · " + g.gearState());
                        n++;
                    }
                if (n == 0) say(s, "§7No steam servicing plant loaded");
            }
            case "scale", "ash" -> {
                LocomotiveSteam l = Stock.nearest(w, s.getPosition(), 64, LocomotiveSteam.class, null);
                if (l == null || a.length < 3) { say(s, "§7Stand within 64 blocks of a steam loco: steam " + sub + " <value>"); return; }
                SteamData.Boiler b = d.of(l.getUUID());
                float v = Float.parseFloat(a[2]);
                if (sub.equals("scale")) { b.scaleMm = Math.max(0, v); if (v < SteamConfig.tubeLeakMm) b.leak = false; } else b.ashKg = Math.max(0, v);
                d.markDirty();
                say(s, "§a" + Stock.name(l) + ": " + sub + " = " + v);
            }
            case "log" -> { for (String x : d.log) say(s, "§7" + x); if (d.log.isEmpty()) say(s, "§7(empty)"); }
            case "selftest" -> selftest(s);
            default -> say(s, "§7" + USAGE);
        }
    }

    static void selftest(ICommandSender s) {
        List<String> bad = new ArrayList<>();
        SteamData.Boiler b = new SteamData.Boiler();
        b.ppm = 300;
        b.mix(40, 1000, 1000);
        if (Math.abs(b.ppm - 170) > 0.5) bad.add("water mixing: 1000 L of 300 ppm + 1000 L of 40 ppm = " + b.ppm + " (want 170)");
        b.scaleMm = 1;
        b.ashKg = 0;
        if (SteamConfig.boilerScale && Math.abs(SteamTicker.loss(b) - 0.08f) > 1e-4) bad.add("1 mm of scale should cost 8 %, got " + SteamTicker.loss(b));
        b.scaleMm = 100;
        if (SteamConfig.boilerScale && SteamTicker.loss(b) > 0.351f) bad.add("scale penalty not capped at 35 %");
        b.scaleMm = 0; b.ashKg = SteamConfig.ashPanKg;
        if (SteamConfig.ashPan && Math.abs(SteamTicker.loss(b) - 0.15f) > 1e-4) bad.add("full ash pan should cost 15 %");
        double perHour = 3600 * 4.5 * 250 * SteamTicker.K_SCALE;
        if (Math.abs(perHour - 0.5) > 1e-6) bad.add("scale rate: " + perHour + " mm/h at the reference, want 0.5");
        if (TileCoalingTower.SKIP <= 0 || TileCoalingTower.RATE <= 0) bad.add("coaling tower skip / chute rates");
        if (bad.isEmpty()) say(s, "§aSteam self-test PASSED (water mixing, scale + ash penalties, scale rate, coaling rates)");
        else for (String x : bad) say(s, "§cFAIL: " + x);
    }
}
