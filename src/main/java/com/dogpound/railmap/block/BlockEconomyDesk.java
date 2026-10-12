package com.dogpound.railmap.block;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;

import java.util.function.Supplier;

/** the railway economy's office furniture (her ChatGPT list #46-50): each a wide desk with its own tile */
public class BlockEconomyDesk extends BlockWideDesk {
    public enum Kind {
        COMPANY_HQ("company_hq", TileCompanyHQ::new, "A railway company's boardroom: accounts, staff, fleet, shares, loans and dividends",
                "FOUND a company or JOIN one; the safe is for staff only. Needs 12 V DC"),
        EXCHANGE("commodity_exchange", TileExchange::new, "Live commodity prices that follow supply and demand; sell and buy goods for Pride coins",
                "Its warehouse cabinets take deliveries from Unloading Pits and pipes. Needs 12 V DC"),
        TRAVEL_CENTRE("travel_centre", TileTravelCentre::new, "The station ticket office: zone passes, refunds, cleaning, complaints and the Passenger Charter",
                "Set each station's fare zone on its Station Master's Desk. Needs 12 V DC"),
        CONTAINER_TERMINAL("container_terminal", TileContainerTerminal::new, "A gantry crane over a container yard: truck apron, rail lane and two stack rows",
                "Lay a track parallel to the desk 4-5 blocks behind it; a chest against the desk is the warehouse. Needs 12 V DC"),
        // world + business layer (biz/, her "next 48" list #12-38) - appended
        PORT_BERTH("port_berth", com.dogpound.railmap.biz.TilePortBerth::new, "A quay desk under a ship-to-shore crane: ships call, boxes are discharged, loaded, trucked and hauled to the rail terminal",
                "Key ON, boom DOWN and a ship alongside to work the crane; a Container Terminal within 24 blocks takes the tractor. Needs 12 V DC"),
        STOCK_EXCHANGE("stock_exchange", com.dogpound.railmap.biz.TileStockBoard::new, "The railway stock exchange board: shares, bonds, order book, index and trophies - Pride coins only",
                "Directors LIST their company or ISSUE bonds; anyone BUYs and SELLs. Needs 12 V DC"),
        REGULATOR_OFFICE("regulator_office", com.dogpound.railmap.biz.TileRegulatorOffice::new, "The rail regulator's office: audits, corrective actions and the capacity register (slot auctions)",
                "AUDIT a company, STAMP the report, BID for paths on a corridor. Needs 12 V DC"),
        INSURANCE_DESK("insurance_desk", com.dogpound.railmap.biz.TileInsuranceDesk::new, "The loss adjuster's desk: policies, premiums priced from maintenance records, claims and evidence",
                "Documents filed in the EVIDENCE drawer count towards claims. Needs 12 V DC"),
        PLANNING_BOARD("planning_board", com.dogpound.railmap.biz.TilePlanningBoard::new, "The town hall planning board: founds a town here; population, catchment, noise, mitigation and public-service tenders",
                "Towns grow with rail access, jobs and goods. Needs 12 V DC");

        public final String id;
        final Supplier<TileEntity> tile;
        final String[] tips;
        Kind(String id, Supplier<TileEntity> tile, String... tips) { this.id = id; this.tile = tile; this.tips = tips; }
    }

    public final Kind kind;

    public BlockEconomyDesk(Kind k) {
        super(k.id, Material.WOOD, k.tile, k.tips);
        kind = k;
    }
}
