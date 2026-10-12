package com.dogpound.railmap.traction;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import com.dogpound.railmap.server.RailProps;
import com.dogpound.railmap.steam.Stock;
import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.command.ICommandSender;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/**
 * /irextras traction                          every battery / hydrogen unit: SoC, H2, range, energy
 * /irextras traction set battery|hydrogen|off|auto   force the nearest loco's traction type
 * /irextras traction soc <0-100>              set the nearest unit's state of charge (test)
 * /irextras traction h2 <kg>                  set the nearest hydrogen unit's hydrogen (test)
 * /irextras traction fc on|off                the nearest hydrogen unit's fuel cell
 * /irextras traction feed <mB>                hydrogen gas into the nearest compressor's suction (test)
 * /irextras traction plant                    loaded charging posts + hydrogen station parts
 * /irextras traction selftest                 checks the sums
 */
public final class TractionCommand {
    private TractionCommand() {}

    public static final String USAGE = "traction [set battery|hydrogen|off|auto|soc <%>|h2 <kg>|fc on|off|feed <mB>|plant|selftest]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("traction")) return false;
        try { run(w, s, a); } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    private static void run(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        LocomotiveDiesel near = Stock.nearest(w, s.getPosition(), 64, LocomotiveDiesel.class, null);
        switch (sub) {
            case "" -> {
                int n = 0;
                for (LocomotiveDiesel l : cam72cam.mod.world.World.get(w).getEntities(LocomotiveDiesel.class))
                    if (Traction.kind(l) != Traction.NONE) { say(s, "§f" + Stock.name(l) + " §7" + Traction.status(l)); n++; }
                if (n == 0) say(s, "§7No battery or hydrogen units (tag one by name, rebuild it at the Loco Design Workshop, or: traction set battery)");
                say(s, "§7" + USAGE);
            }
            case "set" -> {
                if (near == null || a.length < 3) { say(s, "§7Stand near a loco: traction set battery|hydrogen|off|auto"); return; }
                TractionData.Unit u = TractionData.get(w).of(near.getUUID());
                u.forced = switch (a[2].toLowerCase(Locale.ROOT)) { case "battery", "bemu" -> (byte) Traction.BEMU; case "hydrogen", "h2" -> (byte) Traction.H2; case "off", "none" -> (byte) Traction.NONE; default -> (byte) -1; };
                if (u.forced == Traction.H2 && u.h2kg <= 0) u.h2kg = (float) Traction.h2CapKg(near);
                TractionData.get(w).markDirty();
                say(s, "§a" + Stock.name(near) + ": " + Traction.KIND[Traction.kind(near)] + (u.forced < 0 ? " (auto)" : ""));
            }
            case "soc" -> {
                if (near == null || a.length < 3) { say(s, "§7traction soc <0-100>"); return; }
                RailProps.get(w).of(near.getUUID()).battery = Math.max(0, Math.min(1, Float.parseFloat(a[2]) / 100f));
                RailProps.get(w).markDirty();
                say(s, "§a" + Stock.name(near) + " SoC " + a[2] + " %");
            }
            case "h2" -> {
                if (near == null || a.length < 3) { say(s, "§7traction h2 <kg>"); return; }
                TractionData.Unit u = Traction.unit(near);
                u.h2kg = (float) Math.max(0, Math.min(Traction.h2CapKg(near), Double.parseDouble(a[2])));
                TractionData.get(w).markDirty();
                say(s, "§a" + Stock.name(near) + " H2 " + u.h2kg + " kg");
            }
            case "fc" -> {
                if (near == null || a.length < 3) { say(s, "§7traction fc on|off"); return; }
                Traction.unit(near).fcOn = a[2].equalsIgnoreCase("on");
                TractionData.get(w).markDirty();
                say(s, "§a" + Stock.name(near) + " fuel cell " + (Traction.unit(near).fcOn ? "ON" : "OFF"));
            }
            case "feed" -> {
                TileH2Compressor c = null;
                double bd = 32 * 32;
                for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (t instanceof TileH2Compressor x && t.getPos().distanceSq(s.getPosition()) < bd) { bd = t.getPos().distanceSq(s.getPosition()); c = x; }
                if (c == null) { say(s, "§7No hydrogen compressor within 32 blocks"); return; }
                int mb = c.feed(a.length > 2 ? Integer.parseInt(a[2]) : 8000);
                say(s, mb > 0 ? "§a" + mb + " mB of hydrogen into the compressor suction" : "§cNo pi_hydrogen fluid registered (is the factory loaded?) or the buffer is full");
            }
            case "plant" -> {
                int n = 0;
                for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
                    if ((t instanceof TileChargePost || t instanceof H2Station.Part) && !t.isInvalid()) {
                        IWiredGear g = (IWiredGear) t;
                        say(s, "§f" + g.wiredName() + " §7" + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ() + " · " + g.gearState());
                        n++;
                    }
                if (n == 0) say(s, "§7No charging posts or hydrogen station loaded");
            }
            case "selftest" -> selftest(s);
            default -> say(s, "§7" + USAGE);
        }
    }

    static void selftest(ICommandSender s) {
        List<String> bad = new ArrayList<>();
        if (Math.abs(Traction.clamp(0.6 * 1000, 100, 5000) - 600) > 1e-9) bad.add("a 1 MW BEMU should carry 600 kWh");
        double kg = 1000.0 / TractionConfig.kwhPerKgH2;                       // 1 MWh out of the fuel cell
        if (kg < 30 || kg > 200) bad.add("hydrogen per MWh out of range: " + kg + " kg");
        if (TileH2Storage.RATED[0] >= TileH2Storage.RATED[1] || TileH2Storage.RATED[1] >= TileH2Storage.RATED[2]) bad.add("cascade banks not in pressure order");
        if (TileChargePost.PROF_KW[0] >= TileChargePost.PROF_KW[1] || TileChargePost.PROF_KW[1] >= TileChargePost.PROF_KW[2]) bad.add("charge profiles not in order");
        String eta = TileChargePost.eta(600, 0.2f, 0.8f, 400);                 // 360 kWh at 400 kW = 54 min
        if (!eta.equals("54 MIN")) bad.add("charge ETA 600 kWh 20->80 % at 400 kW = '" + eta + "' (want 54 MIN)");
        if (bad.isEmpty()) say(s, "§aTraction self-test PASSED (battery size, fuel-cell hydrogen use, cascade order, charge profiles, ETA)");
        else for (String x : bad) say(s, "§cFAIL: " + x);
    }
}
