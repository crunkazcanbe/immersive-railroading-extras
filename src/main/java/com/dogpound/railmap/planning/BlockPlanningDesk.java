package com.dogpound.railmap.planning;

import com.dogpound.railmap.block.BlockWideDesk;
import net.minecraft.block.material.Material;

/** The Network Planning Desk: three blocks wide (see TilePlanningDesk) */
public class BlockPlanningDesk extends BlockWideDesk {
    public BlockPlanningDesk() {
        super("planning_desk", Material.WOOD, TilePlanningDesk::new,
                "Network planning desk: digital twin + shadow simulation, promote to live, resilience, robustness, what-if, versions, AI dispatchers",
                "Drawers, plan chest and binders cupboard are real storage; the shelf shows what's on it. Needs 12 V DC");
    }
}
