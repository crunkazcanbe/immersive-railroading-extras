package com.dogpound.railmap.build6a;

import com.dogpound.railmap.wire.IWiredGear;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /irextras build                 every loaded construction machine and hi-rail truck, with its state
 * /irextras build plan [len] [L|R] [radius]   dry-run a track panel from where you stand, heading the way you look (no track laid)
 * /irextras build selftest        headings, diesel pack, store routing, TBM ring geometry, works bill, IR panel planning
 */
public final class B6Command {
    private B6Command() {}

    public static final String USAGE = "build [plan [len] [L|R] [radius]|selftest]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("build")) return false;
        try { run(w, s, a); } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }

    private static void run(World w, ICommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "" -> {
                int n = 0;
                for (TileEntity t : new ArrayList<>(w.loadedTileEntityList))
                    if ((t instanceof TileTrackLayer || t instanceof TileTbm || t instanceof TileTamper || t instanceof TileLocoWorks) && !t.isInvalid()) {
                        IWiredGear g = (IWiredGear) t;
                        say(s, "§f" + g.wiredName() + " §7" + t.getPos().getX() + "," + t.getPos().getY() + "," + t.getPos().getZ() + " · " + g.gearState());
                        n++;
                    }
                for (Entity e : new ArrayList<>(w.loadedEntityList))
                    if (e instanceof EntityHiRail h) { say(s, "§fHi-rail truck §7" + (int) h.posX + "," + (int) h.posY + "," + (int) h.posZ + " · " + h.hud()); n++; }
                if (n == 0) say(s, "§7No construction machines loaded");
                say(s, "§7" + USAGE);
            }
            case "plan" -> {
                Entity e = s.getCommandSenderEntity();
                if (e == null) { say(s, "§7Run it as a player"); return; }
                int len = a.length > 2 ? Integer.parseInt(a[2]) : 10;
                int curve = a.length > 3 ? (a[3].equalsIgnoreCase("L") ? 0 : a[3].equalsIgnoreCase("R") ? 2 : 1) : 1;
                int rad = a.length > 4 ? Integer.parseInt(a[4]) : 100;
                BlockPos p = new BlockPos(e);
                IrLay.Plan pl = IrLay.plan(w, p, 0.5, 0.5, B6.yawOf(e.getHorizontalFacing()), 1.435, len, curve, rad, true);
                if (pl.err != null) { say(s, "§c" + pl.err); return; }
                String b = IrLay.blocked(w, pl, p);
                say(s, String.format(Locale.ROOT, "§a%d m %s panel from %d,%d,%d: %d rails, %d sleepers, %d ballast · next railhead %d,%d,%d heading %.0f° · %s",
                        len, curve == 1 ? "straight" : (curve == 0 ? "left" : "right") + " R" + rad, p.getX(), p.getY(), p.getZ(), pl.rails, pl.ties, pl.bed,
                        pl.next.getX(), pl.next.getY(), pl.next.getZ(), pl.nextYaw, b == null ? "§aclear to build" : "§c" + b));
            }
            case "selftest" -> selftest(w, s);
            default -> say(s, "§7" + USAGE);
        }
    }

    static void selftest(World w, ICommandSender s) {
        List<String> bad = new ArrayList<>();
        for (EnumFacing f : EnumFacing.HORIZONTALS) if (B6.facingOf(B6.yawOf(f)) != f) bad.add("heading round trip " + f);
        if (B6.facingOf(91) != EnumFacing.EAST || B6.facingOf(-170) != EnumFacing.NORTH) bad.add("yaw -> facing rounding");
        // diesel pack: won't crank flat or dry, runs and burns when fuelled
        Diesel d = new Diesel();
        d.battery = 10;
        d.tick(null, true, true, false, false);
        if (d.running) bad.add("diesel cranked on a flat battery");
        d.battery = 80;
        d.tick(null, true, true, false, false);
        if (d.running) bad.add("diesel started with an empty tank");
        net.minecraftforge.fluids.Fluid diesel = null;
        for (net.minecraftforge.fluids.Fluid f : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().values()) if (f.getName().toLowerCase(Locale.ROOT).contains("diesel")) { diesel = f; break; }
        if (diesel != null) {
            d.tank.fill(new net.minecraftforge.fluids.FluidStack(diesel, 1000), true);
            if (d.tank.getFluidAmount() != 1000) bad.add("diesel tank would not take diesel");
            d.tick(null, true, true, false, true);
            if (!d.running) bad.add("diesel would not start fuelled: " + d.why);
            if (d.tank.getFluidAmount() != 1000 - B6Config.dieselIdleMb * 4) bad.add("working burn: " + (1000 - d.tank.getFluidAmount()) + " mB, want " + B6Config.dieselIdleMb * 4);
            d.tick(null, true, true, true, false);
            if (d.running) bad.add("E-stop did not stop the engine");
        }
        if (d.tank.fill(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 100), false) > 0) bad.add("diesel tank took water");
        // store routing
        InventoryBasic r = new InventoryBasic("r", false, 9), t = new InventoryBasic("t", false, 9);
        Routed ro = new Routed(new net.minecraft.inventory.IInventory[]{r, t}, new boolean[]{false, true}, x -> x.getItem() == net.minecraft.init.Items.IRON_INGOT, x -> x.getItem() == net.minecraft.init.Items.STICK);
        if (!ro.insertItem(0, new ItemStack(net.minecraft.init.Items.STICK, 5), false).isEmpty()) {
            if (!ro.insertItem(9, new ItemStack(net.minecraft.init.Items.STICK, 5), false).isEmpty()) bad.add("routing: sticks refused by the stick store");
        } else bad.add("routing: sticks went into the iron store");
        if (!ro.extractItem(0, 1, true).isEmpty()) bad.add("routing: an input store gave items out");
        if (ro.extractItem(9, 2, false).getCount() != 2) bad.add("routing: the output store would not give items out");
        if (B6.take(t, x -> true, 10) != 3) bad.add("take: wanted the 3 sticks left");
        // TBM ring geometry: 7 m bore = 35 bored, 10 walls, 2 haunches, 7 crown
        TileTbm tbm = new TileTbm();
        int[] k = new int[4];
        for (Object[] c : tbm.cells(BlockPos.ORIGIN.up(64))) k[(int) c[1]]++;
        int[] need = tbm.ringNeeds();
        if (k[0] != 35 || k[1] != 10 || k[2] != 2 || k[3] != 7) bad.add("TBM 7 m ring cells " + java.util.Arrays.toString(k) + ", want [35, 10, 2, 7]");
        if (need[0] != k[1] || need[1] != k[2] || need[2] != k[3]) bad.add("TBM segment count does not match the ring");
        // works: every IR stock has a bill and a build time; the item it makes is that stock
        int defs = 0;
        try {
            for (cam72cam.immersiverailroading.registry.EntityRollingStockDefinition def : cam72cam.immersiverailroading.registry.DefinitionManager.getDefinitions()) {
                cam72cam.immersiverailroading.library.Gauge g = def.recommended_gauge;
                if (TileLocoWorks.bill(def, g).size() < 7 || TileLocoWorks.needTicks(def, g) < 40) bad.add("works bill / time for " + def.name());
                if (defs++ == 0) {
                    ItemStack it = TileLocoWorks.stockItem(def, g);
                    cam72cam.immersiverailroading.items.ItemRollingStock.Data dd = new cam72cam.immersiverailroading.items.ItemRollingStock.Data(new cam72cam.mod.item.ItemStack(it));
                    if (dd.def == null || !dd.def.defID.equals(def.defID)) bad.add("works: stock item does not carry " + def.defID);
                }
            }
        } catch (LinkageError | RuntimeException e) { bad.add("works: IR definitions unreadable (" + e + ")"); }
        // IR panel planning where the sender stands: a 10 m straight ends 10 blocks on
        Entity e = s.getCommandSenderEntity();
        String irNote = "";
        if (e != null) {
            BlockPos p = new BlockPos(e);
            IrLay.Plan pl = IrLay.plan(w, p, 0.5, 0.5, 0, 1.435, 10, 1, 100, false);
            if (pl.err != null) bad.add("IR planning: " + pl.err);
            else {
                if (!pl.next.equals(p.south(10))) bad.add("IR 10 m straight south: next railhead " + pl.next + ", want " + p.south(10));
                if (pl.rails <= 0 || pl.ties <= 0) bad.add("IR material costs " + pl.rails + "/" + pl.ties);
                IrLay.Plan cv = IrLay.plan(w, p, 0.5, 0.5, 0, 1.435, 16, 2, 50, false);
                if (cv.err != null) bad.add("IR curve planning: " + cv.err);
                else if (Math.abs(cv.nextYaw) < 5 || Math.abs(cv.nextYaw - 360) < 5) bad.add("IR 16 m R50 curve did not turn (heading " + cv.nextYaw + ")");
                irNote = String.format(Locale.ROOT, ", IR panels (10 m = %d rails / %d sleepers; R50 curve turns %.0f°)", pl.rails, pl.ties, cv.err == null ? Math.min(cv.nextYaw, 360 - cv.nextYaw) : 0);
            }
        }
        if (bad.isEmpty()) say(s, "§aBuild self-test PASSED (headings, diesel" + (diesel == null ? " - no diesel fluid registered, fuel part skipped" : "") + ", store routing, TBM ring, works bills for " + defs + " stock" + irNote + ")");
        else for (String x : bad) say(s, "§cFAIL: " + x);
    }
}
