package com.dogpound.railmap.infra;

import cam72cam.immersiverailroading.tile.TileRail;
import com.dogpound.railmap.asset.AssetConfig;
import com.dogpound.railmap.asset.AssetData;
import com.dogpound.railmap.asset.AssetKind;
import com.dogpound.railmap.grid.GridData;
import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.planning.PossessionPlanner;
import com.dogpound.railmap.track.Rails;
import com.dogpound.railmap.weather.Weather;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/** /irextras infra ... - inspection and test commands for batch 3 (#36-50). */
public final class InfraCommand {
    private InfraCommand() {}

    public static final String USAGE = "infra [ns|share|ess [soc|temp|soh <v>]|faults|dispatch <id> [0-2]|relay [drift <pct>]|flaws|flaw <211|2223|227|135|421> [mm]|drain|water <mm>|rec|"
            + "poss plan <r> <min> [best]|poss list|poss rescan <id>|drill <0-4>|expand|log|selftest]";

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("infra")) return false;
        try { run(w, s, a); } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { say(s, "§c/irextras " + USAGE); }
        return true;
    }

    private static List<String[]> rows(World w, java.util.function.BiConsumer<World, List<String[]>> f) { List<String[]> l = new ArrayList<>(); f.accept(w, l); return l; }

    private static void run(World w, ICommandSender s, String[] a) {
        BlockPos me = s.getPosition();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        String who = s.getName();
        switch (sub) {
            case "": {
                InfraData d = InfraData.get(w);
                say(s, "§b== Batch 3 infrastructure (#36-50)");
                say(s, "§7neutral sections " + NeutralSections.all(w).size() + " · parallel-fed sections " + LoadSharing.groups(w).size() + " · energy stores " + TileWaysideEss.REG.all(w).size()
                        + " · fault records " + d.faults.size() + " · relay certs " + d.certs.size() + " · recording runs " + d.runs.size() + " · possession plans " + d.plans.size() + " · drill grades " + d.bestGrade);
                say(s, "§8/irextras " + USAGE);
                return;
            }
            case "ns": for (String[] r : rows(w, NeutralSections::rows)) say(s, "§d NS " + r[1] + "," + r[2] + "," + r[3] + " §7" + r[6] + " · A " + r[4] + " · B " + r[5] + " · coasted " + r[7] + " · drew power " + r[8]); return;
            case "share": for (String[] r : rows(w, LoadSharing::rows)) say(s, "§7" + r[1] + " feeder " + r[2] + ": " + r[3] + " kW (" + r[4] + "%), " + r[5] + " pu " + r[6] + ", " + r[7] + " trains, spread " + r[8] + "% circ " + r[9] + "% " + r[10]); return;
            case "ess": {
                TileWaysideEss e = nearest(w, me, TileWaysideEss.class);
                if (e == null) { say(s, "§7No energy store loaded"); return; }
                if (a.length > 3) {
                    NBTEdit n = new NBTEdit(e);
                    double v = Double.parseDouble(a[3]);
                    switch (a[2]) { case "soc": n.t.setDouble("soc", v); break; case "temp": n.t.setDouble("temp", v); break; case "soh": n.t.setDouble("soh", v / 100); break; default: }
                    n.apply();
                }
                say(s, "§b" + e.wiredName() + ": §7" + e.gearState());
                return;
            }
            case "faults": for (String[] r : rows(w, FaultLocator::rows)) if (r[0].equals("FL")) say(s, "§c#" + r[1] + " §7" + r[2] + " ago · feeder " + r[3] + " · " + r[4] + " · single " + r[6] + " m · double " + r[7] + " · |Z| " + r[9] + " Ω · " + r[12] + " kA · crew " + r[13] + " " + r[14]); return;
            case "dispatch": say(s, FaultLocator.dispatch(w, Integer.parseInt(a[2]), me, a.length > 3 ? Integer.parseInt(a[3]) : 0, who)); return;
            case "relay": {
                if (a.length > 3 && a[2].equals("drift")) {
                    long ied = nearestIed(w, me);
                    if (ied == 0) { say(s, "§7No IED within 16 blocks"); return; }
                    InfraData.Cal c = RelayCal.of(w, ied);
                    c.pick = Double.parseDouble(a[3]) / 100; c.time = c.pick * 1.2;
                    InfraData.get(w).markDirty();
                    say(s, "§eIED " + BlockPos.fromLong(ied) + " drifted to " + a[3] + "% (test)");
                    return;
                }
                for (String[] r : rows(w, RelayCal::rows)) say(s, r[0].equals("RC") ? "§7IED " + r[1] + ": pick-up " + r[2] + "% · time " + r[3] + "% · calibrated " + r[4] + " d ago · due " + r[5] + " d · last " + r[6] : "§8 " + r[1]);
                return;
            }
            case "flaws": for (String[] r : rows(w, RailFlaws::rows)) say(s, r[0].equals("RF") ? "§6#" + r[1] + " §7" + r[2] + " " + r[3] + " " + r[4] + " mm (crit " + r[5] + ") " + r[6] + " -> " + r[7] : "§8UT " + r[1]); return;
            case "flaw": {
                TileRail piece = Rails.pieceAt(w, me.down());
                if (piece == null) piece = Rails.pieceAt(w, me);
                if (piece == null) { say(s, "§cStand on a rail"); return; }
                AssetData d = AssetData.get(w);
                BlockPos key = Rails.pos(piece);
                AssetData.Asset as = d.at(AssetKind.TRACK, key.toLong());
                if (as == null) as = d.register(w, AssetKind.TRACK, key.toLong(), "track " + Rails.xz(key), false);
                RailFlaws.Flaw f = RailFlaws.Flaw.SQUAT;
                for (RailFlaws.Flaw x : RailFlaws.Flaw.values()) if (x.code.endsWith(a[2])) f = x;
                AssetData.Defect df = d.addDefect(w, as.id, as.pos, f.type(), "mm", a.length > 3 ? Double.parseDouble(a[3]) : f.crit * 0.2, f.crit);
                say(s, "§eSeeded hidden defect #" + df.id + " " + f.type() + " " + String.format(Locale.ROOT, "%.1f mm", df.size) + " in " + as.id + " (find it with the ultrasonic tester / test train)"
                        + (AssetConfig.assetRegister ? "" : " §7- asset register is off"));
                return;
            }
            case "drain": for (String[] r : rows(w, InfraConsole::drainage)) say(s, "§7" + String.join(" | ", Arrays.copyOf(r, Math.min(r.length, 9)))); return;
            case "water": {
                if (!Weather.on()) { say(s, "§cRailway weather is off"); return; }
                Weather.cell(w, me).water = Double.parseDouble(a[2]);
                say(s, "§bWater in this cell set to " + a[2] + " mm (test)");
                return;
            }
            case "rec": for (String[] r : rows(w, OhleRecording::rows)) if (!r[0].equals("OT")) say(s, "§7" + String.join(" | ", r)); return;
            case "poss": {
                String what = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
                if (what.equals("plan")) say(s, PossessionPlanner.plan(w, me.getX() + 0.5, me.getY(), me.getZ() + 0.5, Double.parseDouble(a[3]), Long.parseLong(a[4]), a.length > 5 && a[5].equalsIgnoreCase("best"), who));
                else if (what.equals("rescan")) say(s, PossessionPlanner.rescan(w, Integer.parseInt(a[3])));
                else for (String[] r : rows(w, PossessionPlanner::rows)) say(s, r[0].equals("PP") ? "§b#" + r[1] + " " + r[2] + " " + r[3] + " · " + r[4] + " · " + r[6] + "/" + r[5] + " done, " + r[7] + " blocked · work/window " + r[8] + " min · +" + r[9] + " s delay, -" + r[10] + " deps " + r[11]
                        : "§7   " + r[2] + " " + r[3] + " - " + r[4] + " " + r[5] + "% " + r[6]);
                return;
            }
            case "drill": say(s, com.dogpound.railmap.incident.GradedDrills.run(w, Integer.parseInt(a[2]), s instanceof EntityPlayer p ? p : null)); return;
            case "expand": say(s, com.dogpound.railmap.planning.WhatIf.ask(w, "expand", who)); return;
            case "log": { int n = 0; for (String l : InfraData.get(w).log) { if (n++ > 15) break; say(s, "§8" + l); } return; }
            case "selftest": for (String l : InfraSelfTest.run()) say(s, l); return;
            default: say(s, "§c/irextras " + USAGE);
        }
    }

    /** edit a tile's saved state in place (test commands) */
    private static final class NBTEdit {
        final TileEntity te;
        final net.minecraft.nbt.NBTTagCompound t;
        NBTEdit(TileEntity te) { this.te = te; this.t = te.writeToNBT(new net.minecraft.nbt.NBTTagCompound()); }
        void apply() { te.readFromNBT(t); te.markDirty(); }
    }

    @SuppressWarnings("unchecked")
    private static <T extends TileEntity> T nearest(World w, BlockPos p, Class<T> c) {
        T best = null;
        double bd = 64 * 64;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList)) if (c.isInstance(t) && t.getPos().distanceSq(p) < bd) { bd = t.getPos().distanceSq(p); best = (T) t; }
        return best;
    }

    private static long nearestIed(World w, BlockPos p) {
        long best = 0;
        double bd = 16 * 16;
        for (Map.Entry<Long, GridData.Node> en : GridData.get(w).nodes().entrySet())
            if (en.getValue().kind == GridKind.IED && BlockPos.fromLong(en.getKey()).distanceSq(p) < bd) { bd = BlockPos.fromLong(en.getKey()).distanceSq(p); best = en.getKey(); }
        return best;
    }
}
