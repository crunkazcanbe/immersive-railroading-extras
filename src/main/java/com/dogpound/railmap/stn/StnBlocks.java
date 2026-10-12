package com.dogpound.railmap.stn;

import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatGPT batch 3 (#21-35, station operations + cargo care): every block of the three groups. CommonProxy registers
 * them: cargo care (freight.CargoBlocks: reefers, container tracking, washout, axle weighbridge), platforms
 * (station.PlatformOpsBlocks: crowds, zones, doors, security) and station services (stn.StnServiceBlocks: formation,
 * diagrams, interchange, emergency power, lost property, parcels, disruption).
 */
public final class StnBlocks {
    private StnBlocks() {}

    public static List<Block> create() {
        List<Block> l = new ArrayList<>();
        l.addAll(com.dogpound.railmap.freight.CargoBlocks.create());
        l.addAll(com.dogpound.railmap.station.PlatformOpsBlocks.create());
        l.addAll(StnServiceBlocks.create());
        return l;
    }

    public static void registerTiles() {
        com.dogpound.railmap.freight.CargoBlocks.registerTiles();
        com.dogpound.railmap.station.PlatformOpsBlocks.registerTiles();
        StnServiceBlocks.registerTiles();
    }
}
