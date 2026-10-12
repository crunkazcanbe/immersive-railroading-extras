package com.dogpound.railmap.item;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapTab;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.List;

/**
 * Industrial parts (requested feature): every IR Extras power machine and every Pride Industry plant is crafted
 * from these, and the parts from each other, so the gear is expensive and built up step by step.
 */
public class ItemPart extends Item {
    public enum Part {
        PLATE("reinforced_plate", "Reinforced Steel Plate", "Iron, iron blocks and obsidian pressed together: the base of everything"),
        FRAME("machine_frame", "Machine Frame", "The welded skeleton of a machine"),
        CIRCUIT("control_circuit", "Control Circuit", "The brains of every control panel"),
        MOTOR("motor_core", "Motor Core", "Windings, a shaft and bearings: anything that turns"),
        HYDRAULIC("hydraulic_cylinder", "Hydraulic Cylinder", "Crushers, breaker mechanisms, chutes"),
        GEARBOX("gearbox", "Gearbox", "Diamond-hardened gears on shafts and bearings"),
        HEATING_COIL("heating_coil", "Heating Coil", "Induction heating: smelters and furnaces"),
        FAN("cooling_fan", "Cooling Fan", "Blows air: transformers, gensets, flotation cells"),
        BELT("drive_belt", "Drive Belt", "Rubber-and-leather belt: conveyors and drives"),
        BEARING("ball_bearing", "Ball Bearing", "Lets a shaft spin"),
        SHAFT("steel_shaft", "Steel Shaft", "Carries the turning"),
        SPRING("spring", "Spring", "Breaker closing springs, relays, valves"),
        SEAL("rubber_seal", "Rubber Seal", "Keeps the water and oil in"),
        WINDING("copper_winding", "Copper Winding", "Coils for motors, transformers and relays"),
        CORE("transformer_core", "Transformer Core", "Laminated steel the windings sit on"),
        INSULATOR("insulator", "Insulator", "Ceramic: keeps the high voltage where it belongs"),
        CAPACITOR("capacitor_cell", "Capacitor Cell", "Stores charge: capacitor banks, inverters"),
        DIODE("rectifier_diode", "Rectifier Diode", "Turns AC into DC"),
        CONTACTOR("relay_contactor", "Relay Contactor", "An electrically worked switch"),
        HEAT_SINK("heat_sink", "Heat Sink", "Finned block that carries the heat away"),
        SCREEN("display_screen", "Display Screen", "Glowing readout glass"),
        MOVEMENT("meter_movement", "Meter Movement", "The needle mechanism inside every gauge"),
        BATTERY("battery_cell", "Battery Cell", "Stores energy"),
        SOLAR("solar_cell", "Solar Cell", "Turns daylight into power"),
        BLADE("turbine_blade", "Turbine Blade", "Steam turbines and wind turbines"),
        ANTENNA("antenna", "Antenna", "Sends and picks up the radio");
        public final String id, label, tip;
        Part(String id, String label, String tip) { this.id = id; this.label = label; this.tip = tip; }
    }

    public ItemPart() {
        setRegistryName(RailMap.MODID, "part");
        setTranslationKey(RailMap.MODID + ".part");
        setHasSubtypes(true);
        setCreativeTab(RailMapTab.INSTANCE);
    }

    public static Part part(ItemStack s) { Part[] p = Part.values(); return p[Math.min(p.length - 1, Math.max(0, s.getMetadata()))]; }
    @Override public String getItemStackDisplayName(ItemStack s) { return part(s).label; }
    @Override public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> l) { if (isInCreativeTab(tab)) for (Part p : Part.values()) l.add(new ItemStack(this, 1, p.ordinal())); }
    @Override public void addInformation(ItemStack s, World w, List<String> t, ITooltipFlag f) { t.add("§7" + part(s).tip); }
}
