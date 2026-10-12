package com.dogpound.railmap.traction;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotBlock;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** battery + hydrogen train infrastructure (batch 5 #6); CommonProxy registers them */
public final class TractionBlocks {
    private TractionBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotBlock("charge_post", TileChargePost::new, new double[]{2, 0, 2, 14, 16, 14},
                        "Train Charging Post: plug post + power-electronics cabinet, kW meter and charge-profile screen",
                        "PLUG the battery train standing at it, pick ECO / NORMAL / RAPID, START. Draws its kW from the grid cable. Mains + 12 V"),
                new DepotBlock("h2_compressor", TileH2Compressor::new, new double[]{0, 0, 1, 16, 14, 15},
                        "Hydrogen Compressor: 3-stage diaphragm compressor, 30 -> 500 bar, from an Electrolysis Plant's hydrogen",
                        "Pipe pi_hydrogen into it; fills the Storage Tubes within 8 blocks. AUTO key. Mains + 12 V"),
                new DepotBlock("h2_storage", TileH2Storage::new, new double[]{0, 0, 1, 16, 16, 15},
                        "Hydrogen Storage Tubes: LOW / MEDIUM / HIGH cascade banks with pressure gauges, hand valves and gas detection",
                        "Filled by the compressor, drawn by the dispenser. 12 V for the solenoid valves and gas detector"),
                new DepotBlock("h2_dispenser", TileH2Dispenser::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Hydrogen Dispenser: 350 bar nozzle + breakaway hose, pre-cooler, mass meter, gas detectors and safety zone",
                        "CONNECT the nozzle to the hydrogen train, PRE-COOL, START. E-STOP trips the whole station. Mains + 12 V"));
    }

    public static void registerTiles() {
        reg(TileChargePost.class, "charge_post");
        reg(TileH2Compressor.class, "h2_compressor");
        reg(TileH2Storage.class, "h2_storage");
        reg(TileH2Dispenser.class, "h2_dispenser");
    }

    static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) { GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id)); }
}
