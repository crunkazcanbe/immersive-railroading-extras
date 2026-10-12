package com.dogpound.railmap.asset;

import com.dogpound.railmap.depot.FleetData;
import com.dogpound.railmap.depot.ItemSpare;
import com.dogpound.railmap.depot.Stores;
import com.dogpound.railmap.depot.TileDepotStores;
import com.dogpound.railmap.item.ItemPart;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Component remanufacturing (her 48-next list #24). A worn part taken off in the depot (or out of an asset repair) comes
 * back to the stores as a CORE carrying its condition. At the Remanufacturing Bench it is washed, stripped and
 * inspected; the inspection measures its condition (± 8 %) and grades it:
 * <pre>
 *   A  55 % +    cleaned, new seals, tested: a remanufactured spare (grade A), as good as new in service
 *   B  30-55 %   with its overhaul kit in the input bin: a remanufactured spare (grade B); without, stripped for parts
 *   C  10-30 %   stripped: the reusable sub-parts that pass, the metals, the rest is scrap
 *   SCRAP <10 %  steel scrap for the arc furnace
 * </pre>
 */
public final class Reman {
    private Reman() {}

    public static final String[] GRADES = {"A", "B", "C", "SCRAP"};

    public static int grade(double cond) { return cond >= 55 ? 0 : cond >= 30 ? 1 : cond >= 10 ? 2 : 3; }

    /** the overhaul kit a grade-B core needs to be rebuilt (null = it can't be) */
    public static ItemSpare.Spare kit(ItemSpare.Spare core) {
        return switch (core) {
            case TRACTION_MOTOR, POINT_MOTOR -> ItemSpare.Spare.MOTOR_KIT;
            case POWER_KIT -> ItemSpare.Spare.FILTER_SET;
            case BREAKER_KIT -> ItemSpare.Spare.AIR_HOSE;            // the springs and seals of the mechanism
            case WHEELSET, BOGIE_BAD -> ItemSpare.Spare.AXLE_BEARING;
            default -> null;
        };
    }

    /** what a remanufactured core becomes (a defective bogie becomes a serviceable one) */
    public static ItemSpare.Spare product(ItemSpare.Spare core) { return core == ItemSpare.Spare.BOGIE_BAD ? ItemSpare.Spare.BOGIE : core; }

    /** {ItemPart ordinal, count} sub-parts a stripped core yields, and {iron, copper, gold nuggets, scrap} metals */
    static int[][] parts(ItemSpare.Spare s) {
        return switch (s) {
            case TRACTION_MOTOR -> new int[][]{{ItemPart.Part.WINDING.ordinal(), 2}, {ItemPart.Part.BEARING.ordinal(), 2}, {ItemPart.Part.SHAFT.ordinal(), 1}};
            case POWER_KIT -> new int[][]{{ItemPart.Part.SHAFT.ordinal(), 1}, {ItemPart.Part.BEARING.ordinal(), 1}, {ItemPart.Part.GEARBOX.ordinal(), 1}};
            case CONTROL_CARD, SIGNAL_MODULE -> new int[][]{{ItemPart.Part.CIRCUIT.ordinal(), 1}, {ItemPart.Part.CONTACTOR.ordinal(), 1}};
            case AXLE_BEARING -> new int[][]{{ItemPart.Part.BEARING.ordinal(), 1}};
            case WHEELSET -> new int[][]{{ItemPart.Part.SHAFT.ordinal(), 1}};
            case BOGIE_BAD, BOGIE -> new int[][]{{ItemPart.Part.SPRING.ordinal(), 2}, {ItemPart.Part.BEARING.ordinal(), 2}, {ItemPart.Part.FRAME.ordinal(), 1}};
            case POINT_MOTOR -> new int[][]{{ItemPart.Part.MOTOR.ordinal(), 1}, {ItemPart.Part.CONTACTOR.ordinal(), 1}};
            case BREAKER_KIT -> new int[][]{{ItemPart.Part.SPRING.ordinal(), 1}, {ItemPart.Part.CONTACTOR.ordinal(), 1}};
            case BUSHING, INSULATOR -> new int[][]{{ItemPart.Part.INSULATOR.ordinal(), 1}};
            case MOTOR_KIT -> new int[][]{{ItemPart.Part.BEARING.ordinal(), 1}, {ItemPart.Part.WINDING.ordinal(), 1}};
            default -> new int[0][];
        };
    }

    static int[] metals(ItemSpare.Spare s) {
        return switch (s) {
            case TRACTION_MOTOR -> new int[]{3, 2, 0, 2};
            case POWER_KIT -> new int[]{4, 0, 0, 3};
            case CONTROL_CARD, SIGNAL_MODULE -> new int[]{0, 1, 2, 0};
            case WHEELSET -> new int[]{2, 0, 0, 6};
            case BOGIE_BAD, BOGIE -> new int[]{4, 0, 0, 6};
            case RAIL -> new int[]{2, 0, 0, 4};
            case BRAKE_BLOCKS, COUPLER, AXLE_BEARING -> new int[]{1, 0, 0, 1};
            case BREAKER_KIT, MOTOR_KIT, POINT_MOTOR -> new int[]{1, 1, 0, 1};
            case BUSHING -> new int[]{0, 2, 0, 0};
            default -> new int[]{0, 0, 0, 1};
        };
    }

    /** a stripped core's output: sub-parts that pass inspection (good share by grade), metals, scrap */
    static List<ItemStack> strip(World w, ItemSpare.Spare s, int grade) {
        List<ItemStack> o = new ArrayList<>();
        double pass = grade <= 1 ? 0.85 : grade == 2 ? 0.4 : 0;
        for (int[] p : parts(s)) {
            int n = 0;
            for (int i = 0; i < p[1]; i++) if (w.rand.nextDouble() < pass) n++;
            if (n > 0 && com.dogpound.railmap.proxy.CommonProxy.part != null) o.add(new ItemStack(com.dogpound.railmap.proxy.CommonProxy.part, n, p[0]));
        }
        int[] m = metals(s);
        if (grade < 3) {
            if (m[0] > 0) o.add(new ItemStack(Items.IRON_INGOT, m[0]));
            if (m[1] > 0) { ItemStack cu = copper(m[1]); if (!cu.isEmpty()) o.add(cu); else o.add(new ItemStack(Items.IRON_NUGGET, m[1] * 3)); }
            if (m[2] > 0) o.add(new ItemStack(Items.GOLD_NUGGET, m[2]));
        }
        int scrap = m[3] + (grade == 3 ? m[0] + 1 : 0);
        if (scrap > 0) o.add(com.dogpound.railmap.factory.ore.ItemMaterial.make(com.dogpound.railmap.factory.ore.ItemMaterial.M.SCRAP, scrap));
        return o;
    }

    static ItemStack copper(int n) {
        List<ItemStack> l = OreDictionary.getOres("ingotCopper", false);
        if (l.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = l.get(0).copy();
        if (s.getMetadata() == OreDictionary.WILDCARD_VALUE) s.setItemDamage(0);
        s.setCount(n);
        return s;
    }

    /** a remanufactured spare */
    public static ItemStack remanned(World w, ItemSpare.Spare core, int grade) {
        ItemSpare.Spare p = product(core);
        ItemStack s = p == ItemSpare.Spare.BOGIE ? ItemSpare.bogie(true, FleetData.get(w).serial(FleetData.Kind.BOGIE_A), 0) : ItemSpare.stack(p, 1);
        if (!s.hasTagCompound()) s.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        s.getTagCompound().setString("grade", GRADES[grade]);
        return s;
    }

    // ---- cores coming back from the depot ------------------------------------------------------------------------------
    /** the rotable a REPAIR job card replaced, for its core */
    static ItemSpare.Spare rotable(com.dogpound.railmap.depot.FleetData.Job j) {
        for (ItemSpare.Spare s : new ItemSpare.Spare[]{ItemSpare.Spare.TRACTION_MOTOR, ItemSpare.Spare.POWER_KIT, ItemSpare.Spare.CONTROL_CARD, ItemSpare.Spare.AXLE_BEARING, ItemSpare.Spare.BRAKE_BLOCKS})
            if (j.spares[s.ordinal()] > 0) return s;
        return null;
    }

    /** Jobs.complete: the old part goes back to the stores as a core with its condition */
    public static void coreBack(World w, com.dogpound.railmap.depot.FleetData.Job j, float wear, String from) {
        if (!AssetConfig.cores || j.depot == 0) return;
        ItemSpare.Spare s = rotable(j);
        if (s == null) return;
        int n = Math.max(1, j.spares[s.ordinal()]);
        for (int i = 0; i < n; i++) toStores(w, BlockPos.fromLong(j.depot), ItemSpare.core(s, Math.max(0, 100 - wear) * (0.8f + w.rand.nextFloat() * 0.3f), from));
    }

    /** a core into the bins of the stores nearest a place (dropped there if every bin is full or none is loaded) */
    public static void toStores(World w, BlockPos near, ItemStack s) {
        List<TileDepotStores> all = Procurement.allStores(w);
        all.sort(java.util.Comparator.comparingDouble(t -> t.getPos().distanceSq(near)));
        for (TileDepotStores t : all) for (int k = 0; k < 6; k++) if (Stores.put(t.storage(k), s)) { t.controlsChanged(); return; }
        if (w.isBlockLoaded(near)) net.minecraft.inventory.InventoryHelper.spawnItemStack(w, near.getX() + 0.5, near.getY() + 1, near.getZ() + 0.5, s);
    }

    public static String describe(ItemStack core) {
        return String.format(Locale.ROOT, "%s %.0f%%", ItemSpare.of(core).label, core.getTagCompound().getFloat("core"));
    }
}
