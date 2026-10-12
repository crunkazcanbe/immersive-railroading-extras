package com.dogpound.railmap.crew;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.depot.DepotDesk;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** train crew + revenue protection blocks (batch 5 #7 #9); CommonProxy registers them */
public final class CrewBlocks {
    private CrewBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new DepotDesk("crew_office", Material.WOOD, TileCrewOffice::new,
                        "Crew Office: roster board, sign-on terminal, lockers, fridge, kettle and break room - drivers, guards and inspectors sign on here",
                        "ROLE + HIRE, SHIFT / LEN diagram, ALLOC a train, BEAT a line, AUTO key runs the diagrams. Kettle: water bucket + BOIL. 12 V + mains"),
                new DepotDesk("revenue_office", Material.WOOD, TileRevenueOffice::new,
                        "Revenue Protection Office: ticket inspectors' case files, fines ledger, CCTV link, evidence store",
                        "PENALTY selector (warn / warn first / fare), WAIVE refunds, DEPLOY an inspector to a line, PRINT LEDGER. 12 V + mains"));
    }

    public static void registerTiles() {
        GameRegistry.registerTileEntity(TileCrewOffice.class, new ResourceLocation(RailMap.MODID, "crew_office"));
        GameRegistry.registerTileEntity(TileRevenueOffice.class, new ResourceLocation(RailMap.MODID, "revenue_office"));
    }
}
