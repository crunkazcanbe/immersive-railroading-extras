package com.dogpound.railmap.factory;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.factory.machine.MachineType;
import com.dogpound.railmap.factory.ore.ItemMaterial;
import com.dogpound.railmap.factory.ore.OreRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The factory: what used to be the separate Pride Industry mod (requested feature). Merged into IR Extras 2026-10-10 ("merge the
 * factory mod with the IR extras and do away with that mod").
 *
 * Real plants built as multiblocks: you build the casing structure with its ports in the right places, commission it
 * with the Electrician's Screwdriver and it becomes one machine with a real control panel. Machines run on Forge Energy
 * through their drive / power inlet, normally from an Electric Motor coupled to the shaft, so every plant is a real load
 * on the electrical grid.
 *
 * Old worlds: blocks/items were prideindustry:x and are now irextras:x (remapped below); tile entities keep their old
 * prideindustry:* ids, fluids keep their pi_* names, the owner map keeps its "prideindustry_owners" save name and the ore
 * list keeps config/prideindustry-ores.txt (its line numbers are the ore items' metadata).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class Factory {
    private Factory() {}

    public static final String MODID = RailMap.MODID;
    /** the merged mod's id: still used for tile-entity ids and save names so old worlds load */
    public static final String OLD_MODID = "prideindustry";
    public static final Logger LOG = LogManager.getLogger("IRExtras-Factory");

    /** every block (and its ItemBlock) Pride Industry 0.1.0 registered; generated from its blockstates */
    public static final String[] OLD_BLOCKS = {
            "arc_furnace", "assembly_line", "blast_furnace", "borehole_pump", "bulk_loader", "car_dumper", "coke_oven",
            "conveyor", "conveyor_express", "conveyor_fast", "crate_packer", "drive_coupling", "electrolyser", "flotation",
            "flow_meter", "fluid_output_hatch", "gate_valve", "item_input_hatch", "item_output_hatch", "jaw_crusher",
            "machine_casing", "oil_press", "overhead_loader", "pipe", "power_inlet", "refinery", "rolling_mill", "sawmill",
            "smelter", "tank_loader", "tank_unloader", "tie_plant", "unloader", "washing_plant", "water_inlet", "water_tower",
            "welding_cell", "windmill", "wire_drawer"};
    /** the plain items it registered */
    public static final String[] OLD_ITEMS = {"crushed_ore", "washed_ore", "concentrate_ore", "material", "crate"};
    public static final String[] FLUIDS = {"pi_molten_metal", "pi_ore_slurry", "pi_froth", "pi_creosote", "pi_plant_oil", "pi_diesel", "pi_hydrogen", "pi_oxygen"};
    public static final String[] OLD_TILES = {"machine", "port", "pipe", "conveyor"};

    public static void preInit(java.io.File configDir) {
        OreRegistry.configDir = configDir;
        com.dogpound.railmap.factory.fluid.ModFluids.register();
        RailMap.factoryProxy.preInit();
    }

    public static void init() { RailMap.factoryProxy.init(); }

    public static void postInit() {
        OreDictionary.registerOre("fuelCoke", ItemMaterial.make(ItemMaterial.M.COKE, 1));
        OreDictionary.registerOre("ingotSteel", ItemMaterial.make(ItemMaterial.M.STEEL, 1));
        OreDictionary.registerOre("dustWood", ItemMaterial.make(ItemMaterial.M.SAWDUST, 1));
        OreDictionary.registerOre("pulpWood", ItemMaterial.make(ItemMaterial.M.SAWDUST, 1));
        OreDictionary.registerOre("dustWheat", ItemMaterial.make(ItemMaterial.M.FLOUR, 1));
        OreDictionary.registerOre("foodFlour", ItemMaterial.make(ItemMaterial.M.FLOUR, 1));
        // her list section 13 chain A: Immersive Railroading track is built from these (its ore names irRail / irTie)
        OreDictionary.registerOre("irRail", ItemMaterial.make(ItemMaterial.M.RAIL, 1));
        OreDictionary.registerOre("irTie", ItemMaterial.make(ItemMaterial.M.SLEEPER, 1));
        OreDictionary.registerOre("scrapSteel", ItemMaterial.make(ItemMaterial.M.SCRAP, 1));
        net.minecraft.item.crafting.FurnaceRecipes.instance().addSmeltingRecipe(ItemMaterial.make(ItemMaterial.M.FLOUR, 1), new ItemStack(net.minecraft.init.Items.BREAD), 0.35f);
        OreRegistry.discover();          // every mod's ores: ore<Name> with an ingot<Name>
        OreRegistry.addSmelting();
        LOG.info("Factory: {} machines, {} ores processed", MachineType.values().length, OreRegistry.count());
        List<String> bad = selfTest();
        if (bad.isEmpty()) LOG.info("Factory self-test PASS: all {} old prideindustry ids resolve", OLD_BLOCKS.length * 2 + OLD_ITEMS.length);
        else LOG.error("Factory self-test FAIL (old worlds will lose these): {}", bad);
    }

    // ---- old-world compatibility: prideindustry:x -> irextras:x -------------------------------------------------------

    @SubscribeEvent
    public static void missingBlocks(RegistryEvent.MissingMappings<Block> e) { remap(e, ForgeRegistries.BLOCKS); }

    @SubscribeEvent
    public static void missingItems(RegistryEvent.MissingMappings<Item> e) { remap(e, ForgeRegistries.ITEMS); }

    private static <T extends net.minecraftforge.registries.IForgeRegistryEntry<T>> void remap(RegistryEvent.MissingMappings<T> e, IForgeRegistry<T> reg) {
        for (RegistryEvent.MissingMappings.Mapping<T> m : e.getAllMappings()) {
            ResourceLocation to = remapTarget(m.key);
            if (to != null && reg.containsKey(to)) { m.remap(reg.getValue(to)); LOG.debug("remapped {} -> {}", m.key, to); }
        }
    }

    /** the new id of an old Pride Industry id, or null when it isn't one */
    public static ResourceLocation remapTarget(ResourceLocation old) {
        return OLD_MODID.equals(old.getNamespace()) ? new ResourceLocation(MODID, old.getPath()) : null;
    }

    /** Every old id resolves under irextras, the tile entities keep their old ids, every fluid exists. Empty = pass. */
    public static List<String> selfTest() {
        List<String> bad = new ArrayList<>();
        for (String n : OLD_BLOCKS) {
            ResourceLocation to = remapTarget(new ResourceLocation(OLD_MODID, n));
            if (to == null || !ForgeRegistries.BLOCKS.containsKey(to)) bad.add("block " + n);
            if (to == null || !ForgeRegistries.ITEMS.containsKey(to)) bad.add("itemblock " + n);
        }
        for (String n : OLD_ITEMS) {
            ResourceLocation to = remapTarget(new ResourceLocation(OLD_MODID, n));
            if (to == null || !ForgeRegistries.ITEMS.containsKey(to)) bad.add("item " + n);
        }
        Class<?>[] tiles = {com.dogpound.railmap.factory.machine.TileMachine.class, com.dogpound.railmap.factory.block.TilePort.class,
                com.dogpound.railmap.factory.block.TilePipe.class, com.dogpound.railmap.factory.conveyor.TileConveyor.class};
        for (int i = 0; i < tiles.length; i++) {
            @SuppressWarnings("unchecked") ResourceLocation k = net.minecraft.tileentity.TileEntity.getKey((Class<? extends net.minecraft.tileentity.TileEntity>) tiles[i]);
            if (k == null || !k.equals(new ResourceLocation(OLD_MODID, OLD_TILES[i]))) bad.add("tile " + OLD_TILES[i] + " is " + k);
        }
        for (String f : FLUIDS) if (!FluidRegistry.isFluidRegistered(f)) bad.add("fluid " + f);
        return bad;
    }
}
