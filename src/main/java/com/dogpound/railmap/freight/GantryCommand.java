package com.dogpound.railmap.freight;

import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.item.ItemContainer;
import com.dogpound.railmap.proxy.CommonProxy;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.*;

/** /irextras gantry [list|jobs|plan|auto|press <id>|give [n]|selftest] - yard gantry cranes (batch 5 #1); acts on the nearest one */
public final class GantryCommand {
    private GantryCommand() {}

    static final String USAGE = "gantry [list|jobs|plan|auto|press <control id>|give [n]|selftest]";

    static void say(ICommandSender s, String t) { s.sendMessage(new TextComponentString(t)); }

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("gantry")) return false;
        try { run(w, s, a); } catch (RuntimeException e) { say(s, "§c/irextras " + USAGE + " (" + e + ")"); }
        return true;
    }

    static TileYardGantry nearest(World w, BlockPos me) {
        TileYardGantry best = null;
        double bd = Double.MAX_VALUE;
        for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
            if (t instanceof TileYardGantry g && !t.isInvalid() && t.getPos().distanceSq(me) < bd) { bd = t.getPos().distanceSq(me); best = g; }
        return best;
    }

    private static void run(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (sub.equals("selftest")) { selftest(s, w); return; }
        if (sub.equals("list")) {
            for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
                if (t instanceof TileYardGantry g && !t.isInvalid()) say(s, "§e" + g.name() + " §7@ " + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ() + " · " + g.gearState());
            return;
        }
        TileYardGantry g = nearest(w, s.getPosition());
        if (g == null) { say(s, "§cNo yard gantry loaded"); return; }
        switch (sub) {
            case "jobs" -> {
                if (g.jobs.isEmpty()) say(s, "§7" + g.name() + ": no jobs");
                for (int[] j : g.jobs) say(s, "§7" + TileYardGantry.slot(j[0], j[1]) + " -> " + TileYardGantry.slot(j[2], j[3]));
                for (String[] b : g.boxes()) say(s, "§e" + b[0] + " §7" + b[1] + (b[2].isEmpty() ? "" : " to " + b[2]) + " · " + b[3]);
            }
            case "plan" -> say(s, "§a" + g.plan() + " job(s) planned at " + g.name());
            case "auto" -> say(s, IControlled.operate(g, "k_auto", s instanceof EntityPlayer p ? p : null));
            case "press" -> say(s, IControlled.operate(g, a[2], s instanceof EntityPlayer p ? p : null));
            case "give" -> say(s, "§a" + g.give(a.length > 2 ? Integer.parseInt(a[2]) : 4, s instanceof EntityPlayer p ? p : null) + " test box(es) put in " + g.name() + "'s stack");
            default -> say(s, "§7/irextras " + USAGE);
        }
    }

    /** geometry, the pendulum + anti-sway, the stack planner and the save / client-sync format */
    static void selftest(ICommandSender s, World w) {
        List<String> fail = new ArrayList<>();
        int ok = 0;
        for (int b = 0; b < TileYardGantry.BAYS; b++) if (TileYardGantry.bayAt(TileYardGantry.BAY_X[b]) != b || TileYardGantry.bayAt(TileYardGantry.BAY_X[b] + 1) != -1) fail.add("bayAt " + b);
        for (int l = 0; l < TileYardGantry.LANES; l++) if (TileYardGantry.laneAt(TileYardGantry.LANE_Z[l]) != l) fail.add("laneAt " + l);
        if (fail.isEmpty()) ok++;
        // a box carried over a full stack must clear it
        if (TileYardGantry.TRAVEL - TileYardGantry.BOX_H < TileYardGantry.TIERS * TileYardGantry.BOX_H) fail.add("hoist too low to carry over a full stack"); else ok++;
        // pendulum: a swing dies out fast with anti-sway, slowly without
        double[] on = {0.5, 0}, off = {0.5, 0};
        for (int i = 0; i < 60; i++) { on = TileYardGantry.sway(on[0], on[1], 0, 6, true); off = TileYardGantry.sway(off[0], off[1], 0, 6, false); }
        double peak = 0;
        for (int i = 0; i < 80; i++) { off = TileYardGantry.sway(off[0], off[1], 0, 6, false); peak = Math.max(peak, Math.abs(off[0])); }
        if (Math.abs(on[0]) > 0.05) fail.add("anti-sway does not settle (" + on[0] + ")"); else ok++;
        if (peak < 0.2) fail.add("free swing dies too fast (" + peak + ")"); else ok++;
        double[] kick = TileYardGantry.sway(0, 0, 0.1, 6, false);
        if (kick[0] >= 0) fail.add("acceleration should swing the load back"); else ok++;
        // planner: the nearest free slot, lowest first
        int[][] h = new int[TileYardGantry.BAYS][TileYardGantry.ROWS];
        h[2][0] = 2;
        int[] f = TileYardGantry.freeStack(h, 2);
        if (f == null || f[0] != 2 || f[1] != 1) fail.add("freeStack " + Arrays.toString(f)); else ok++;
        for (int[] r : h) Arrays.fill(r, TileYardGantry.TIERS);
        if (TileYardGantry.freeStack(h, 0) != null) fail.add("freeStack on a full yard"); else ok++;
        // save + the slim client copy
        try {
            TileYardGantry g = new TileYardGantry();
            g.setWorld(w);
            g.setPos(new BlockPos(0, 255, 0));
            ItemStack box = ItemContainer.make(CommonProxy.container, "TST", 123456, null, "test");
            ItemContainer.setContents(box, Collections.singletonList(new ItemStack(net.minecraft.init.Items.COAL, 64)));
            g.stack[1][2][0] = box;
            g.jobs.add(new int[]{1, 4, 2, 1});
            NBTTagCompound full = g.writeToNBT(new NBTTagCompound());
            TileYardGantry g2 = new TileYardGantry();
            g2.readFromNBT(full);
            if (!ItemContainer.id(g2.stack[1][2][0]).equals(ItemContainer.id(box)) || ItemContainer.count(g2.stack[1][2][0]) != 64 || g2.jobs.size() != 1 || g2.jobs.get(0)[3] != 1) fail.add("save / load"); else ok++;
            NBTTagList l = g.getUpdateTag().getTagList("stack", 10);
            boolean slim = true;
            for (int i = 0; i < l.tagCount(); i++) slim &= !l.getCompoundTagAt(i).getCompoundTag("tag").hasKey("inv");
            if (!slim || ItemContainer.count(g.stack[1][2][0]) != 64) fail.add("client copy not slim (or it emptied the real box)"); else ok++;
        } catch (RuntimeException | LinkageError e) { fail.add("tile: " + e); }
        say(s, (fail.isEmpty() ? "§aGANTRY SELF-TEST PASSED " : "§cGANTRY SELF-TEST FAILED ") + ok + " ok" + (fail.isEmpty() ? "" : ": " + String.join(", ", fail)));
    }
}
