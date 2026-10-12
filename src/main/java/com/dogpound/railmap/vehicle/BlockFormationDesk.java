package com.dogpound.railmap.vehicle;

import com.dogpound.railmap.block.BlockWideDesk;
import net.minecraft.block.material.Material;

/** Formation & Livery Desk (her ChatGPT list items 107 + 110): see TileFormationDesk */
public class BlockFormationDesk extends BlockWideDesk {
    public BlockFormationDesk() {
        super("formation_desk", Material.WOOD, TileFormationDesk::new,
                "Plan train formations (length, mass, braked %, power, checks), read / apply / build them on the siding beside it",
                "Repaint in the company livery and restore damaged bodywork. Needs 12 V DC");
    }
}
