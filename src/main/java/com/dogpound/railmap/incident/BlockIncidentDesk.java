package com.dogpound.railmap.incident;

import com.dogpound.railmap.block.BlockWideDesk;
import net.minecraft.block.material.Material;

/** The Incident Command Desk: three blocks wide (see TileIncidentDesk) */
public class BlockIncidentDesk extends BlockWideDesk {
    public BlockIncidentDesk() {
        super("incident_desk", Material.WOOD, TileIncidentDesk::new,
                "Emergency control centre desk: incident board, commander's orders, rescue / evacuation plans, final reports",
                "Drawers, grab-bag cupboard and plan chest are real storage. Needs 12 V DC");
    }
}
