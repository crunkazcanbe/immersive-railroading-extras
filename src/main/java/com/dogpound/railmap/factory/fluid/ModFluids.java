package com.dogpound.railmap.factory.fluid;

import com.dogpound.railmap.factory.Factory;
import com.dogpound.railmap.factory.ore.OreRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Real fluids (requested feature): molten metal, ore slurry (mud)
 * and flotation froth. One fluid each, coloured per ore from the FluidStack's "ore" tag. When Tinkers' Construct (or
 * any mod) already has the metal as a fluid (molten "iron", "copper"...), the smelter makes THAT fluid instead, so it
 * casts in Tinkers' tables and basins.
 */
public final class ModFluids {
    private ModFluids() {}

    /** client sets this: ore name -> RGB of its ingot */
    public static ToIntFunction<String> oreColor = ore -> 0xC8C8C8;

    public static Fluid MOLTEN, SLURRY, FROTH, CREOSOTE, PLANT_OIL, DIESEL, HYDROGEN, OXYGEN;

    static class OreFluid extends Fluid {
        private final String what; private final int mix;
        OreFluid(String name, String tex, String what, int mix) {
            super(name, new ResourceLocation(Factory.MODID, "blocks/fluids/" + tex + "_still"), new ResourceLocation(Factory.MODID, "blocks/fluids/" + tex + "_flow"));
            this.what = what; this.mix = mix;
        }
        @Override public int getColor(FluidStack s) {
            String ore = oreOf(s);
            if (ore == null) return 0xFF000000 | mix;
            int c = oreColor.applyAsInt(ore);
            int r = ((c >> 16 & 255) + (mix >> 16 & 255)) / 2, g = ((c >> 8 & 255) + (mix >> 8 & 255)) / 2, b = ((c & 255) + (mix & 255)) / 2;
            return 0xFF000000 | r << 16 | g << 8 | b;
        }
        @Override public String getLocalizedName(FluidStack s) {
            String ore = oreOf(s);
            return what.replace("%s", ore == null ? "Metal" : OreRegistry.pretty(ore));
        }
    }

    public static void register() {
        MOLTEN = new OreFluid("pi_molten_metal", "molten_metal", "Molten %s", 0xFF8A2E).setLuminosity(15).setTemperature(1700).setDensity(3000).setViscosity(6000);
        SLURRY = new OreFluid("pi_ore_slurry", "ore_slurry", "%s Ore Slurry", 0x6E5A44).setDensity(1600).setViscosity(3000);
        FROTH = new OreFluid("pi_froth", "froth", "%s Froth", 0xE8E4D8).setDensity(600).setViscosity(1500);
        CREOSOTE = plain("pi_creosote", "ore_slurry", 0x3A2A18).setDensity(1100).setViscosity(4000);
        PLANT_OIL = plain("pi_plant_oil", "froth", 0xD8B83A).setDensity(920).setViscosity(2500);
        DIESEL = plain("pi_diesel", "froth", 0xC89A30).setDensity(830).setViscosity(1200);
        HYDROGEN = plain("pi_hydrogen", "froth", 0xDCEBFF).setGaseous(true).setDensity(-100).setViscosity(100);   // from the Electrolysis Plant
        OXYGEN = plain("pi_oxygen", "froth", 0xBFE6FF).setGaseous(true).setDensity(-50).setViscosity(100);
        for (Fluid f : new Fluid[]{MOLTEN, SLURRY, FROTH, CREOSOTE, PLANT_OIL, DIESEL, HYDROGEN, OXYGEN}) {
            FluidRegistry.registerFluid(f);
            FluidRegistry.addBucketForFluid(f);
        }
    }

    private static Fluid plain(String name, String tex, int rgb) {
        return new Fluid(name, new ResourceLocation(Factory.MODID, "blocks/fluids/" + tex + "_still"), new ResourceLocation(Factory.MODID, "blocks/fluids/" + tex + "_flow")).setColor(0xFF000000 | rgb);
    }

    /** another mod's fluid of that kind when there is one (so it works in their machines and IR's locomotives), else ours */
    public static Fluid prefer(Fluid ours, String... names) {
        for (String n : names) { Fluid f = FluidRegistry.getFluid(n); if (f != null) return f; }
        return ours;
    }

    public static String oreOf(FluidStack s) {
        return s != null && s.tag != null && s.tag.hasKey("ore") ? s.tag.getString("ore") : null;
    }

    public static FluidStack tagged(Fluid f, String ore, int mb) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("ore", ore);
        return new FluidStack(f, mb, t);
    }

    /** the molten form of an ore: another mod's fluid (Tinkers' "iron"...) when one exists, else ours */
    public static FluidStack molten(String ore, int mb) {
        Fluid tc = FluidRegistry.getFluid(ore.toLowerCase(Locale.ROOT));
        if (tc != null && tc.getTemperature() > 600) return new FluidStack(tc, mb);
        return tagged(MOLTEN, ore, mb);
    }
}
