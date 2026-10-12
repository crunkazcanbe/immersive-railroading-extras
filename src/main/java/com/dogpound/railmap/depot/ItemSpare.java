package com.dogpound.railmap.depot;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.List;

/**
 * Rolling-stock spares (her ChatGPT list #53): what a depot keeps in its Stores and a job card uses up. Bogies carry the
 * serial and the mileage of the bogie they are (a defective one taken off at the Bogie Drop keeps its history).
 */
public class ItemSpare extends Item {
    public enum Spare {
        BRAKE_BLOCKS("brake_blocks", "Brake Block Set", "Composition brake blocks for one vehicle - fixes BRAKES"),
        WHEELSET("wheelset", "Wheelset", "New wheels pressed on an axle - for wheels turned past the scrap limit"),
        AXLE_BEARING("axle_bearing", "Axle Bearing Cartridge", "Sealed taper-roller bearing - fixes BEARINGS"),
        TRACTION_MOTOR("traction_motor", "Traction Motor", "Electric locomotives: replaces a worn motor (ENGINE)"),
        POWER_KIT("power_kit", "Power Unit Overhaul Kit", "Liners, pistons, injectors, gaskets - diesel / steam ENGINE"),
        CONTROL_CARD("control_card", "Control Electronics Card", "Traction control / converter card - fixes ELECTRICS"),
        COUPLER("coupler_knuckle", "Coupler Knuckle", "Cast knuckle and pin - renews worn couplers"),
        AIR_HOSE("brake_hose", "Brake Hose", "Brake pipe hose and angle cock gasket - cures brake pipe leaks"),
        FILTER_SET("filter_set", "Filter and Oil Set", "Consumables used by every exam: filters, oil, grease"),
        BOGIE("bogie", "Bogie (serviceable)", "A complete overhauled bogie for the Bogie Drop: wheels, bearings and brakes"),
        BOGIE_BAD("bogie_defective", "Bogie (defective)", "Taken off a vehicle - OVERHAUL it at a Bogie Drop to make it serviceable"),
        // ---- infrastructure spares (her 48-next list #2 / #16 / #27): what an asset repair takes from the stores ----
        RAIL("rail_length", "Rail Length (60 kg/m, 18 m)", "Replaces a cracked or worn rail - TRACK defects"),
        POINT_MOTOR("point_motor", "Point Machine Motor + Clutch", "Drive motor, clutch and detection contacts - POINT MACHINE defects"),
        SIGNAL_MODULE("signal_led", "Signal LED Module", "LED aspect module with its driver card - SIGNAL defects"),
        INSULATOR("insulator", "Composite Insulator", "Silicone-shed line insulator - INSULATOR defects"),
        BREAKER_KIT("breaker_kit", "Breaker Contact + Mechanism Kit", "Main / arcing contacts, springs and trip latch - CIRCUIT BREAKER defects"),
        BUSHING("tx_bushing", "Transformer Bushing + Gasket Set", "HV bushing, tap-changer contacts and gaskets - TRANSFORMER defects"),
        MOTOR_KIT("motor_kit", "Motor Bearing + Rewind Kit", "Bearings, slot wedges, varnish and coils - MOTOR defects"),
        STRUCTURE_KIT("structure_kit", "Structural Repair Kit", "Resin, rebar, mortar, weld consumables - BRIDGE / TUNNEL / LINING / PLATFORM defects");
        public final String id, label, tip;
        Spare(String id, String label, String tip) { this.id = id; this.label = label; this.tip = tip; }
        /** a rolling-stock spare (the original set the stock board and freight reorders cover) */
        public boolean rolling() { return ordinal() <= BOGIE_BAD.ordinal(); }
    }

    public ItemSpare() {
        setRegistryName(RailMap.MODID, "spare");
        setTranslationKey(RailMap.MODID + ".spare");
        setHasSubtypes(true);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    public static Spare of(ItemStack s) { Spare[] p = Spare.values(); return p[Math.min(p.length - 1, Math.max(0, s.getMetadata()))]; }
    /** a serviceable spare of this kind (a used core waiting for remanufacture is not) */
    public static boolean is(ItemStack s, Spare p) { return !s.isEmpty() && s.getItem() instanceof ItemSpare && s.getMetadata() == p.ordinal() && !isCore(s); }
    /** a used component taken off in the depot (her 48-next list #24): NBT "core" = its condition % */
    public static boolean isCore(ItemStack s) { return !s.isEmpty() && s.getItem() instanceof ItemSpare && s.hasTagCompound() && s.getTagCompound().hasKey("core"); }

    /** a core: the used part, its condition % and where it came from */
    public static ItemStack core(Spare p, float cond, String from) {
        ItemStack s = stack(p, 1);
        NBTTagCompound t = new NBTTagCompound();
        t.setFloat("core", Math.max(0, Math.min(100, cond)));
        t.setString("from", from == null ? "" : from);
        s.setTagCompound(t);
        return s;
    }
    public static ItemStack stack(Spare p, int n) { return new ItemStack(com.dogpound.railmap.proxy.CommonProxy.spare, n, p.ordinal()); }

    /** a bogie with its serial + km run */
    public static ItemStack bogie(boolean good, String serial, double km) {
        ItemStack s = stack(good ? Spare.BOGIE : Spare.BOGIE_BAD, 1);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("serial", serial);
        t.setDouble("km", km);
        s.setTagCompound(t);
        return s;
    }

    @Override public String getItemStackDisplayName(ItemStack s) {
        if (isCore(s)) return "Used " + of(s).label + " (core)";
        if (s.hasTagCompound() && s.getTagCompound().hasKey("grade")) return of(s).label + " (reman. grade " + s.getTagCompound().getString("grade") + ")";
        return of(s).label;
    }
    @Override public int getItemStackLimit(ItemStack s) { Spare p = of(s); return p == Spare.BOGIE || p == Spare.BOGIE_BAD || p == Spare.WHEELSET || p == Spare.TRACTION_MOTOR ? 1 : 16; }
    @Override public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> l) { if (isInCreativeTab(tab)) for (Spare p : Spare.values()) l.add(new ItemStack(this, 1, p.ordinal())); }

    @Override
    public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) {
        t.add("§7" + of(s).tip);
        if (isCore(s)) t.add(String.format(java.util.Locale.ROOT, "§6Core: %.0f%% condition §8- strip it at a Remanufacturing Bench%s", s.getTagCompound().getFloat("core"),
                s.getTagCompound().getString("from").isEmpty() ? "" : " · off " + s.getTagCompound().getString("from")));
        if (s.hasTagCompound() && s.getTagCompound().hasKey("grade")) t.add("§bRemanufactured, grade " + s.getTagCompound().getString("grade") + " §8(as good as new in service)");
        if (s.hasTagCompound() && s.getTagCompound().hasKey("serial"))
            t.add(String.format(java.util.Locale.ROOT, "§8Serial %s · %.1f km run", s.getTagCompound().getString("serial"), s.getTagCompound().getDouble("km")));
    }
}
