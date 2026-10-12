package com.dogpound.railmap.ctl;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.safety.SafetyBlock;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** The grid / control / comms blocks of her "next 48" list (#20 #21 #23 #25 #30 #43); CommonProxy registers them. */
public final class CtlBlocks {
    private CtlBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new SafetyBlock("spectrum_meter", com.dogpound.railmap.grid.TileSpectrumMeter::new, new double[]{1, 0, 2, 15, 16, 15},
                        "Harmonic spectrum meter: LED bar-graph spectrum h3-h25, THD V / I, resonance, limits (IEEE 519, G5/4)",
                        "Place it touching grid plant. Alarm relay past the limit. 12 V"),
                new SafetyBlock("filter_bank", com.dogpound.railmap.grid.TileFilterBank::new, null,
                        "Harmonic filter bank: tuned LC filter 5th / 7th / 11th / 13th / high-pass, 250-2000 kvar",
                        "Touching the bus it filters. Placed OPEN. Overload, over-temperature, capacitor unbalance trips. 12 V"),
                new SafetyBlock("microgrid_controller", com.dogpound.railmap.grid.TileMicrogrid::new, new double[]{0, 0, 1, 16, 16, 15},
                        "Microgrid controller: islanding, frequency control, load shedding, resync with sync-check, intertie limit",
                        "Touch the local plant. Wrench-link the intertie breaker (POI) and SHED A / B / C breakers. 12 V"),
                new SafetyBlock("field_rtu", com.dogpound.railmap.wire.TileRtu::new, new double[]{2, 0, 3, 14, 16, 14},
                        "Field RTU: polls wired gear around it, fibre + radio paths with failover",
                        "Lost comms = STALE values on the console, commands refused; gear keeps working locally. 12 V"),
                new SafetyBlock("pis_server", com.dogpound.railmap.station.TilePisServer::new, new double[]{1, 0, 1, 15, 16, 15},
                        "PIS server rack: the live passenger information feed for every sign, board, PA and ticket machine",
                        "AUTO writes delays, cancellations, connection risks, evacuations. Operator messages + EVACUATE. 12 V"),
                new SafetyBlock("pis_board", com.dogpound.railmap.station.TilePisBoard::new, new double[]{0, 6, 6, 16, 16, 10},
                        "PIS platform sign: next trains + the live feed for the nearest station, scrolling ticker",
                        "Falls back to the fallback message when it can't reach a PIS server. 12 V"),
                new SafetyBlock("approval_panel", com.dogpound.railmap.admin.TileApprovalPanel::new, new double[]{1, 0, 11, 15, 16, 16},
                        "Approval panel: request -> approve -> execute; two AUTHORISE keys for dual authorisation",
                        "Two different people must turn both keys within 10 s for a DUAL request. Audit trail on screen. 12 V"),
                new SafetyBlock("production_planner", com.dogpound.railmap.factory.TilePlanner::new, new double[]{0, 0, 1, 16, 15, 15},
                        "Production planning console: ore -> steel -> rail plan, power bids, freight departures",
                        "ADVISE shows the plan; AUTO pauses plants that lose their power bid and switches the rolling mill. 12 V"));
    }

    public static void registerTiles() {
        reg(com.dogpound.railmap.grid.TileSpectrumMeter.class, "spectrum_meter");
        reg(com.dogpound.railmap.grid.TileFilterBank.class, "filter_bank");
        reg(com.dogpound.railmap.grid.TileMicrogrid.class, "microgrid_controller");
        reg(com.dogpound.railmap.wire.TileRtu.class, "field_rtu");
        reg(com.dogpound.railmap.station.TilePisServer.class, "pis_server");
        reg(com.dogpound.railmap.station.TilePisBoard.class, "pis_board");
        reg(com.dogpound.railmap.admin.TileApprovalPanel.class, "approval_panel");
        reg(com.dogpound.railmap.factory.TilePlanner.class, "production_planner");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
