package com.dogpound.railmap.light;

import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.asset.AssetKind;
import net.minecraft.command.ICommandSender;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * /irextras light ... - night lighting test + inspection:
 * status | selftest | lux | flicker | fail | repair | flood rebuild|clear
 */
public final class LightCommand {
    private LightCommand() {}

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("light")) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "status" -> status(w, s);
            case "selftest" -> selftest(w, s);
            case "lux" -> {
                BlockPos p = s.getPosition();
                double lx = Lux.at(w, p.up());
                say(s, "§7Light at you: §f" + Lux.show(lx) + "§7 - a photocell would switch lamps " + (lx < 35 ? "§eON" : lx > 70 ? "§7OFF" : "§7(between 35 and 70 lx: stays as it is)"));
            }
            case "flicker", "fail", "repair" -> defect(w, s, sub, a);
            case "flood" -> {
                boolean clear = a.length > 2 && a[2].equalsIgnoreCase("clear");
                int n = 0;
                for (TileLuminaire l : lamps(w, s.getPosition(), 64)) if (l.kind == TileLuminaire.Kind.FLOOD) { l.floodGround(!clear && l.lampOn()); n++; }
                say(s, "§7" + (clear ? "Cleared" : "Re-laid") + " the ground light of " + n + " floodlight(s) within 64 blocks (" + LightHelpers.pending(w) + " light-block jobs queued)");
            }
            default -> say(s, "§7/irextras light status | selftest | lux | flicker | fail | repair | flood rebuild|clear  (flicker / fail / repair act on the nearest luminaire)");
        }
        return true;
    }

    private static List<TileLuminaire> lamps(World w, BlockPos near, int r) {
        List<TileLuminaire> o = new ArrayList<>();
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof TileLuminaire l && !t.isInvalid() && (near == null || t.getPos().distanceSq(near) <= (double) r * r)) o.add(l);
        return o;
    }

    private static void status(World w, ICommandSender s) {
        int[] n = new int[TileLuminaire.Kind.values().length], lit = new int[n.length];
        int other = 0, otherLit = 0, flick = 0, failed = 0, ground = 0, ctl = 0, lcp = 0;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (t.isInvalid()) continue;
            if (t instanceof TileLuminaire l) {
                n[l.kind.ordinal()]++; if (l.lampOn()) lit[l.kind.ordinal()]++;
                ground += l.lampOn() ? l.helperCount() : 0;
                if (l.gearState().contains("FLICKER")) flick++;
            } else if (t instanceof ILamp l) { other++; if (l.lampOn()) otherLit++; }
            if (t instanceof ILamp l && l.lampFailed()) failed++;
            if (t instanceof TileLightController) ctl++;
            if (t instanceof TileLightingContactor) lcp++;
        }
        say(s, "§6Night lighting§7 (loaded):");
        for (TileLuminaire.Kind k : TileLuminaire.Kind.values()) say(s, String.format(Locale.ROOT, "§7  %-18s %d (%d lit)", k.label, n[k.ordinal()], lit[k.ordinal()]));
        say(s, "§7  platform lamps / tunnel lights " + other + " (" + otherLit + " lit) · flickering " + flick + " · §cfailed " + failed);
        say(s, "§7  ground light blocks held by lit floodlights " + ground + " · queued jobs " + LightHelpers.pending(w) + " · placed " + LightHelpers.placed + " / removed " + LightHelpers.removed + " since start");
        say(s, "§7  photocell controllers " + ctl + " · contactor panels " + lcp + " · lamps under a controller now " + LightingBus.size(w));
        say(s, "§7  trains: " + (LightConfig.trainLights ? "on" : "off") + " · dynamic lights mod: " + (net.minecraftforge.fml.common.Loader.isModLoaded("celeritasdynamiclights") ? "Celeritas Dynamic Lights" : "none (IR's own needs OptiFine)")
                + " · beams " + LightConfig.headlightBeams + " · max " + LightConfig.maxTrainLights + " moving lights");
    }

    private static void defect(World w, ICommandSender s, String sub, String[] a) {
        TileEntity best = null;
        double bd = 16 * 16;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) {
            if (!(t instanceof ILamp) || t.isInvalid()) continue;
            double d = t.getPos().distanceSq(s.getPosition());
            if (d < bd) { bd = d; best = t; }
        }
        if (best == null) { say(s, "§cNo luminaire within 16 blocks"); return; }
        AssetData d = AssetData.get(w);
        AssetData.Asset as = d.register(w, AssetKind.LUMINAIRE, best.getPos().toLong(), ((ILamp) best).lampName(), false);
        if (sub.equals("repair")) {
            int k = 0;
            for (AssetData.Defect df : d.defectsOf(as.id)) if (df.stage < AssetData.REPAIRED) { df.size = 0; df.step(w, AssetData.VERIFIED, "repaired (/irextras light repair)"); k++; }
            as.use = 0; as.m[0] = 0; d.markDirty();
            say(s, "§aRepaired " + k + " defect(s) on " + as.id + " " + as.name + " - it relights within 10 s");
            return;
        }
        double size = AssetKind.LUMINAIRE.crit * (sub.equals("fail") ? 1.05 : 0.8);
        AssetData.Defect df = null;
        for (AssetData.Defect x : d.defectsOf(as.id)) if (x.stage < AssetData.REPAIRED) df = x;
        if (df == null) df = d.addDefect(w, as.id, as.pos, AssetKind.LUMINAIRE.defect, AssetKind.LUMINAIRE.defectUnit, size, AssetKind.LUMINAIRE.crit);
        df.size = size;
        d.markDirty();
        say(s, "§e" + as.id + " " + as.name + ": test defect at " + Math.round(size) + " / " + Math.round(AssetKind.LUMINAIRE.crit) + " °C - it "
                + (sub.equals("fail") ? "FAILS" : "starts to FLICKER") + " within 10 s (repair: /irextras light repair)");
    }

    // ---- self-test -----------------------------------------------------------------------------------------------------
    private static int pass, fail;

    private static void check(ICommandSender s, String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        say(s, (ok ? "§a PASS §7" : "§c FAIL §7") + what + (detail == null || detail.isEmpty() ? "" : " §8(" + detail + ")"));
    }

    private static void selftest(World w, ICommandSender s) {
        pass = 0; fail = 0;
        say(s, "§6Night lighting self-test");
        double noon = Lux.model(0f, 0, 0, 0, 1, true), sunset = Lux.model(0.25f, 0, 0, 0, 1, true), night = Lux.model(0.5f, 4, 0, 0, 1, true);
        check(s, "photocell model: noon is daylight", noon > 50000, Lux.show(noon));
        check(s, "photocell model: sunset is dusk", sunset > 50 && sunset < 5000, Lux.show(sunset));
        check(s, "photocell model: moonless midnight is dark", night < 1, Lux.show(night));
        check(s, "photocell model: rain darkens", Lux.model(0.1f, 0, 1, 0, 1, true) < Lux.model(0.1f, 0, 0, 0, 1, true), "");
        check(s, "photocell model: a roofed-over cell reads dark", Lux.model(0f, 0, 0, 0, 0, true) < 1, "");
        check(s, "night-cut schedule window", TileLightController.inWindow(2, 1, 5) && !TileLightController.inWindow(6, 1, 5) && TileLightController.inWindow(23, 22, 2) && !TileLightController.inWindow(3, 4, 4), "");
        BlockPos probe = new BlockPos(0, -64, 0);
        LightingBus.set(w, probe, true, BlockPos.ORIGIN);
        check(s, "lighting bus carries a command", LightingBus.command(w, probe) == 1 && BlockPos.ORIGIN.equals(LightingBus.from(w, probe)), "");
        LightingBus.set(w, probe, false, BlockPos.ORIGIN);
        check(s, "lighting bus carries OFF", LightingBus.command(w, probe) == 0, "");
        check(s, "LUMINAIRE asset kind (failures age through the register)", AssetKind.of("LUM") == AssetKind.LUMINAIRE && AssetKind.LUMINAIRE.spare != null, "");
        check(s, "invisible light block registered and gives its level", LightHelpers.helper != null
                && LightHelpers.helper.getDefaultState().withProperty(BlockLightHelper.LEVEL, 12).getLightValue(w, probe) == 12, "");
        Random r = new Random(7);
        int okOn = 0, failOn = 0, flickOn = 0;
        for (int i = 0; i < 400; i++) { if (LampHealth.visible(0, true, r)) okOn++; if (LampHealth.visible(1.2, true, r)) failOn++; if (LampHealth.visible(0.9, true, r)) flickOn++; }
        check(s, "healthy lamp steady, failing flickers, failed mostly dark", okOn == 400 && flickOn > 80 && flickOn < 360 && failOn < (com.dogpound.railmap.asset.AssetConfig.defectConsequences ? 60 : 200), okOn + " / " + flickOn + " / " + failOn + " of 400");
        check(s, "config sane", LightConfig.floodReach >= 6 && LightConfig.helperOpsPerTick >= 1 && LightConfig.signalDistance >= 64, "");
        // the world: every lit lamp really lights its block
        int lamps = 0, dark = 0, groundOk = 0, groundAll = 0;
        for (TileLuminaire l : lamps(w, null, 0)) {
            lamps++;
            if (l.level() > 0 && w.getLightFor(EnumSkyBlock.BLOCK, l.getPos()) < l.level() - 1) dark++;
            if (l.kind == TileLuminaire.Kind.FLOOD && l.lampOn()) {
                int[] c = groundLights(w, l);
                groundOk += c[0]; groundAll += c[1];
            }
        }
        check(s, "every lit luminaire lights its block (" + lamps + " loaded)", dark == 0, dark + " not lit yet - a relight may still be queued");
        check(s, "floodlight ground light in place", groundAll == 0 || groundOk >= groundAll * 0.8 || LightHelpers.pending(w) > 0, groundOk + " / " + groundAll + " light blocks" + (LightHelpers.pending(w) > 0 ? ", " + LightHelpers.pending(w) + " still queued" : ""));
        say(s, (fail == 0 ? "§a" : "§c") + "Self-test: " + pass + " passed, " + fail + " failed");
    }

    private static int[] groundLights(World w, TileLuminaire l) {
        int ok = 0, all = 0;
        net.minecraft.nbt.NBTTagCompound t = l.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
        net.minecraft.nbt.NBTTagList list = t.getTagList("helpers", 4);
        for (int i = 0; i < list.tagCount(); i++) {
            BlockPos p = BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong) list.get(i)).getLong());
            all++;
            if (!w.isBlockLoaded(p) || w.getBlockState(p).getBlock() == LightHelpers.helper) ok++;
        }
        return new int[]{ok, all};
    }

    private static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
}
