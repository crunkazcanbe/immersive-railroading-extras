package com.dogpound.railmap.safety;

import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Signalling batch 3 blocks (her ChatGPT batch-3 list #11-20, #46, #48, #49); CommonProxy registers them. */
public final class SigBlocks {
    private SigBlocks() {}

    public static List<Block> create() {
        List<Block> l = new ArrayList<>();
        l.addAll(SigA.blocks());
        l.addAll(SigB.blocks());
        l.addAll(SigC.blocks());
        l.addAll(SigM.blocks());
        return l;
    }

    public static void registerTiles() {
        SigA.registerTiles();
        SigB.registerTiles();
        SigC.registerTiles();
        SigM.registerTiles();
    }
}
