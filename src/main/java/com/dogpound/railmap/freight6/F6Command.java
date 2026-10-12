package com.dogpound.railmap.freight6;

import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.time.LocalDate;
import java.util.*;

/**
 * Batch 6 freight + fun commands:
 * <pre>
 * /irextras grain [bins | fill &lt;bin&gt; &lt;t&gt; [moisture] | selftest]
 * /irextras tankfarm [tanks | fill &lt;tank&gt; &lt;litres&gt; [fluid] | fire | selftest]
 * /irextras warehouse [stock | jobs | selftest]
 * /irextras lockers [list | codes | send &lt;player&gt; | selftest]
 * /irextras holiday [status | season auto|christmas|halloween|none | dress xmas|ghost|off | selftest]
 * </pre>
 */
public final class F6Command {
    private F6Command() {}

    public static final String USAGE = "grain|tankfarm|warehouse|lockers|holiday [..|selftest]";

    public static boolean command(World w, ICommandSender s, String[] a) {
        if (a.length == 0) return false;
        String top = a[0].toLowerCase(Locale.ROOT);
        if (!Arrays.asList("grain", "tankfarm", "warehouse", "lockers", "holiday").contains(top)) return false;
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        try {
            switch (top) {
                case "grain" -> grain(w, s, sub, a);
                case "tankfarm" -> tankfarm(w, s, sub, a);
                case "warehouse" -> warehouse(w, s, sub);
                case "lockers" -> lockers(w, s, sub, a);
                default -> holiday(w, s, sub, a);
            }
        } catch (RuntimeException | LinkageError e) { say(s, "§c" + e); }
        return true;
    }

    static void say(ICommandSender s, String m) { s.sendMessage(new TextComponentString(m)); }
    static BlockPos at(ICommandSender s) { return s.getPosition(); }

    // ---- #26 ----------------------------------------------------------------------------------------------------------
    static void grain(World w, ICommandSender s, String sub, String[] a) {
        switch (sub) {
            case "fill" -> {
                TileGrainSilo b = Grain.bin(w, at(s), Integer.parseInt(a[2]) - 1);
                if (b == null) { say(s, "§7No such bin within 24 blocks"); return; }
                long n = Math.round(Double.parseDouble(a[3]) / Grain.T_PER_ITEM);
                b.set(new ItemStack(Items.WHEAT), n, a.length > 4 ? Float.parseFloat(a[4]) : 14f);
                say(s, "§aBin " + a[2] + ": " + Grain.t(n) + " t wheat");
            }
            case "selftest" -> selfGrain(s);
            default -> {
                List<TileGrainSilo> l = Grain.bins(w, at(s));
                say(s, "§6Grain bins near you: §f" + l.size());
                for (int i = 0; i < l.size(); i++) say(s, "§fBIN " + (i + 1) + " §7" + l.get(i).gearState());
                for (TileGrainLeg g : StnUtil.near(w, TileGrainLeg.class, at(s), 48)) say(s, "§fLeg §7" + g.gearState());
                for (TileGrainDryer g : StnUtil.near(w, TileGrainDryer.class, at(s), 48)) say(s, "§fDryer §7" + g.gearState());
                for (TileGrainLoadout g : StnUtil.near(w, TileGrainLoadout.class, at(s), 48)) say(s, "§fLoadout §7" + g.gearState());
                say(s, "§7/irextras grain [bins|fill <bin> <t> [moisture]|selftest]");
            }
        }
    }

    static void selfGrain(ICommandSender s) {
        List<String> bad = new ArrayList<>();
        if (Math.abs(Grain.mix(20f, 100, 10f, 100) - 15f) > 1e-4) bad.add("moisture mixing 100 @ 20 % + 100 @ 10 % != 15 %");
        if (Math.abs(Grain.mix(14f, 0, 18f, 50) - 18f) > 1e-4) bad.add("mixing into an empty bin should take the new moisture");
        if (!Grain.isGrain(new ItemStack(Items.WHEAT)) || !Grain.isGrain(new ItemStack(Items.WHEAT_SEEDS))) bad.add("wheat / seeds not recognised as grain");
        if (Grain.isGrain(new ItemStack(Items.IRON_INGOT))) bad.add("iron counted as grain");
        if (!"1000.0".equals(Grain.t(20000))) bad.add("20 000 items should be 1000.0 t, got " + Grain.t(20000));
        TileGrainSilo b = new TileGrainSilo();
        int put = b.add(new ItemStack(Items.WHEAT), 100, 16f);
        if (put != 100 || b.qty() != 100) bad.add("silo add");
        if (b.room(new ItemStack(Items.WHEAT_SEEDS)) != 0) bad.add("a wheat bin must refuse seeds (one grain per bin)");
        ItemStack t = b.take(40);
        if (t.getCount() != 40 || b.qty() != 60) bad.add("silo take");
        b.take(100);
        if (!b.kind().isEmpty()) bad.add("an emptied bin must forget its grain");
        TileGrainDryer d = new TileGrainDryer();
        if (d.fill(new ItemStack(Items.WHEAT), 3000, 20f) != TileGrainDryer.GARNER) bad.add("dryer garner limit");
        report(s, "Grain", bad, "moisture mixing, grain recognition, tonnes, one grain per bin, take / empty, dryer garner");
    }

    // ---- #27 ----------------------------------------------------------------------------------------------------------
    static void tankfarm(World w, ICommandSender s, String sub, String[] a) {
        switch (sub) {
            case "fill" -> {
                List<TileOilTank> l = TileOilTank.TankFarm.tanks(w, at(s));
                int i = Integer.parseInt(a[2]) - 1;
                if (i < 0 || i >= l.size()) { say(s, "§7No such tank within 32 blocks"); return; }
                net.minecraftforge.fluids.Fluid f = FluidRegistry.getFluid(a.length > 4 ? a[4] : "diesel");
                if (f == null) f = FluidRegistry.getFluid("oil");
                if (f == null) f = FluidRegistry.WATER;
                int n = l.get(i).tank().fill(new FluidStack(f, Integer.parseInt(a[3])), true);
                l.get(i).markDirty();
                say(s, "§aTank " + a[2] + ": +" + n + " L " + f.getName());
            }
            case "fire" -> {
                List<TileOilTank> l = TileOilTank.TankFarm.tanks(w, at(s));
                if (l.isEmpty()) { say(s, "§7No tank near"); return; }
                BlockPos p = l.get(0).getPos().up(2).east();
                if (w.isAirBlock(p)) { w.setBlockState(p, net.minecraft.init.Blocks.FIRE.getDefaultState()); say(s, "§cTest fire lit at " + p.getX() + "," + p.getY() + "," + p.getZ() + " (beside TANK 1)"); }
                else say(s, "§7No air beside TANK 1 for the test fire");
            }
            case "selftest" -> selfOil(s);
            default -> {
                List<TileOilTank> l = TileOilTank.TankFarm.tanks(w, at(s));
                for (int i = 0; i < l.size(); i++) say(s, "§fTANK " + (i + 1) + " §7" + l.get(i).gearState());
                for (TileOilRack r : StnUtil.near(w, TileOilRack.class, at(s), 48)) say(s, "§fRack §7" + r.gearState());
                for (TileFireGas g : StnUtil.near(w, TileFireGas.class, at(s), 48)) say(s, "§fF+G panel §7" + g.gearState());
                say(s, "§7/irextras tankfarm [tanks|fill <tank> <litres> [fluid]|fire|selftest]");
            }
        }
    }

    static void selfOil(ICommandSender s) {
        List<String> bad = new ArrayList<>();
        TileOilTank t = new TileOilTank();
        if (t.tank().getCapacity() != F6Config.tankLitres) bad.add("tank capacity " + t.tank().getCapacity());
        t.tank().fill(new FluidStack(FluidRegistry.WATER, F6Config.tankLitres * 96 / 100), true);
        if (!t.highHigh()) bad.add("96 % must be HIGH-HIGH");
        if (t.pct() != 96) bad.add("level % " + t.pct());
        TileFireGas g = new TileFireGas();
        if (g.water.fill(new FluidStack(FluidRegistry.LAVA, 1000), false) != 0) bad.add("the fire-water tank must refuse lava");
        if (g.water.fill(new FluidStack(FluidRegistry.WATER, 1000), false) != 1000) bad.add("the fire-water tank must take water");
        if (TileFireGas.RATE * 0.03f != 15f) bad.add("foam proportioning: 3 % of 500 L/s = 15 L/s");
        report(s, "Tank farm", bad, "capacity, HIGH-HIGH, level %, fire-water filter, 3 % foam");
    }

    // ---- #28 ----------------------------------------------------------------------------------------------------------
    static void warehouse(World w, ICommandSender s, String sub) {
        switch (sub) {
            case "selftest" -> {
                List<String> bad = new ArrayList<>();
                TileWarehouseRack r = new TileWarehouseRack();
                ItemStack rest = r.putAway(new ItemStack(Items.IRON_INGOT, 64 * 81 + 10));
                if (rest.getCount() != 10) bad.add("a rack holds 81 stacks, overflow " + rest.getCount());
                if (r.count() != 64 * 81) bad.add("rack count " + r.count());
                ItemStack p = r.pick(64, x -> true);
                if (p.getCount() != 64 || r.count() != 64 * 80) bad.add("pick one pallet");
                TileWarehouseOffice.Worker k = new TileWarehouseOffice.Worker();
                k.go(10, 0, 0);
                int ticks = 0;
                while (!k.move(0.15) && ticks < 1000) ticks++;
                if (ticks < 60 || ticks > 70) bad.add("forklift 10 blocks at 3 b/s should take ~66 ticks, took " + ticks);
                report(s, "Warehouse", bad, "rack capacity + overflow, pallet pick, forklift travel");
            }
            case "jobs" -> {
                for (TileWarehouseOffice o : StnUtil.near(w, TileWarehouseOffice.class, at(s), 64)) {
                    say(s, "§fOffice " + o.getPos().getX() + "," + o.getPos().getZ() + " §7" + o.gearState());
                    for (int i = 0; i < o.workers().size(); i++) { TileWarehouseOffice.Worker k = o.workers().get(i); say(s, String.format(Locale.ROOT, "§7  FLT%d %s at %.1f,%.1f %s", i + 1, TileWarehouseOffice.PHASE[k.phase], k.x, k.z, k.fork.isEmpty() ? "" : k.fork.getCount() + " " + k.fork.getDisplayName())); }
                }
            }
            default -> {
                Map<String, int[]> m = Warehouse.stock(w, at(s));
                say(s, "§6Warehouse stock near you: §f" + m.size() + " lines");
                for (Map.Entry<String, int[]> e : m.entrySet()) say(s, "§f" + e.getKey() + " §7" + e.getValue()[0] + " in " + e.getValue()[1] + " slots");
                for (TileWarehouseDock d : Warehouse.docks(w, at(s))) say(s, "§fDock §7" + d.gearState());
                say(s, "§7/irextras warehouse [stock|jobs|selftest]");
            }
        }
    }

    static void report(ICommandSender s, String what, List<String> bad, String checked) {
        if (bad.isEmpty()) say(s, "§a" + what + " self-test PASSED (" + checked + ")");
        else for (String x : bad) say(s, "§c" + what + " FAIL: " + x);
    }

    // ---- #29 ----------------------------------------------------------------------------------------------------------
    static void lockers(World w, ICommandSender s, String sub, String[] a) {
        switch (sub) {
            case "codes" -> {
                if (!(s instanceof EntityPlayer p)) { say(s, "§7Players only"); return; }
                int n = 0;
                for (TileParcelLockers l : StnUtil.loaded(w, TileParcelLockers.class))
                    for (int i = 0; i < TileParcelLockers.N; i++) {
                        TileParcelLockers.Box b = l.box(i);
                        if (b != null && p.getUniqueID().equals(b.owner)) { say(s, "§f" + (l.station().isEmpty() ? l.getPos().getX() + "," + l.getPos().getZ() : l.station()) + " compartment " + (i + 1) + " §7from " + b.from + " - code §f" + b.code); n++; }
                    }
                if (n == 0) say(s, "§7No parcels waiting for you in loaded lockers");
            }
            case "send" -> {
                if (!(s instanceof EntityPlayer p) || a.length < 3) { say(s, "§7/irextras lockers send <player> (test: your held item into the nearest lockers)"); return; }
                List<TileParcelLockers> l = StnUtil.near(w, TileParcelLockers.class, at(s), 32);
                java.util.UUID u = com.dogpound.railmap.mail.Mail.playerId(w, a[2]);
                if (l.isEmpty() || u == null) { say(s, "§7No lockers within 32 blocks, or no such player"); return; }
                ItemStack h = p.getHeldItemMainhand().isEmpty() ? new ItemStack(Items.COOKIE, 4) : p.getHeldItemMainhand().copy();
                int i = l.get(0).deposit(u, a[2], p.getName(), Collections.singletonList(h), 0);
                say(s, i < 0 ? "§cNo free compartment" : "§aIn compartment " + (i + 1) + " for " + a[2]);
            }
            case "selftest" -> {
                List<String> bad = new ArrayList<>();
                int s9 = 0, s18 = 0, s27 = 0;
                for (int sz : TileParcelLockers.SIZE) { if (sz == 9) s9++; else if (sz == 18) s18++; else if (sz == 27) s27++; }
                if (s9 != 12 || s18 != 8 || s27 != 4) bad.add("compartments should be 12 S / 8 M / 4 L");
                for (int sz : TileParcelLockers.SIZE) if (sz % 9 != 0 || sz > 54) bad.add("compartment size " + sz + " is not a chest screen");
                if (Season.of(LocalDate.of(2026, 12, 25)) != Season.CHRISTMAS) bad.add("(holiday) 25 Dec");
                report(s, "Parcel lockers", bad, "compartment sizes, chest screens");
            }
            default -> {
                for (TileParcelLockers l : StnUtil.loaded(w, TileParcelLockers.class)) {
                    say(s, "§f" + (l.station().isEmpty() ? "lockers" : l.station()) + " " + l.getPos().getX() + "," + l.getPos().getZ() + " §7" + l.gearState());
                    for (int i = 0; i < TileParcelLockers.N; i++) if (l.box(i) != null) say(s, "§7  " + (i + 1) + ": for " + l.box(i).name + " from " + l.box(i).from);
                }
                say(s, "§7/irextras lockers [list|codes|send <player>|selftest]");
            }
        }
    }

    // ---- #30 ----------------------------------------------------------------------------------------------------------
    static void holiday(World w, ICommandSender s, String sub, String[] a) {
        switch (sub) {
            case "season" -> {
                if (a.length < 3) { say(s, "§7/irextras holiday season auto|christmas|halloween|none"); return; }
                F6Config.forceSeason = a[2].toLowerCase(Locale.ROOT);
                net.minecraftforge.common.config.ConfigManager.sync(com.dogpound.railmap.RailMap.MODID, net.minecraftforge.common.config.Config.Type.INSTANCE);
                say(s, "§aSeason now " + Season.NAMES[Season.now()] + " (forceSeason=" + F6Config.forceSeason + ")");
            }
            case "dress" -> {
                if (!(s instanceof EntityPlayer p) || a.length < 3) { say(s, "§7/irextras holiday dress xmas|ghost|off (the car you are in / beside)"); return; }
                if (a[2].equalsIgnoreCase("off")) { p.setSneaking(true); }
                ItemStack k = new ItemStack(F6Registry.kit, 1, a[2].equalsIgnoreCase("ghost") ? 1 : 0);
                boolean was = p.capabilities.isCreativeMode;
                p.capabilities.isCreativeMode = true;
                say(s, Holiday.apply(w, p, k, null));
                p.capabilities.isCreativeMode = was;
                p.setSneaking(false);
            }
            case "selftest" -> {
                List<String> bad = new ArrayList<>();
                if (Season.of(LocalDate.of(2026, 12, 1)) != Season.CHRISTMAS || Season.of(LocalDate.of(2027, 1, 6)) != Season.CHRISTMAS) bad.add("Christmas runs 1 Dec - 6 Jan");
                if (Season.of(LocalDate.of(2027, 1, 7)) != Season.NONE || Season.of(LocalDate.of(2026, 11, 30)) != Season.NONE) bad.add("outside Christmas");
                if (Season.of(LocalDate.of(2026, 10, 15)) != Season.HALLOWEEN || Season.of(LocalDate.of(2026, 11, 2)) != Season.HALLOWEEN) bad.add("Halloween runs 15 Oct - 2 Nov");
                if (Season.of(LocalDate.of(2026, 10, 14)) != Season.NONE) bad.add("14 Oct is no season");
                if (Season.daysTo(LocalDate.of(2026, 12, 24), Season.CHRISTMAS) != 1) bad.add("one day to Christmas on 24 Dec");
                if (Season.daysTo(LocalDate.of(2026, 12, 26), Season.CHRISTMAS) != 364) bad.add("26 Dec: next Christmas in 364 days");
                if (Season.daysTo(LocalDate.of(2026, 10, 30), Season.HALLOWEEN) != 1) bad.add("one day to Halloween on 30 Oct");
                report(s, "Holiday", bad, "season windows, days-to countdowns");
            }
            default -> {
                say(s, "§6Holidays: §f" + Holiday.status(w));
                for (TileHolidayController c : StnUtil.near(w, TileHolidayController.class, at(s), 96)) say(s, "§fController " + c.getPos().getX() + "," + c.getPos().getZ() + " §7" + c.gearState());
                say(s, "§7/irextras holiday [status|season auto|christmas|halloween|none|dress xmas|ghost|off|selftest]");
            }
        }
    }
}
