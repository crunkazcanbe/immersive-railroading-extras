package com.dogpound.railmap.safety;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** The signalling-safety blocks (her ChatGPT list section 4, #19-28 + the fault lab); CommonProxy registers them. */
public final class SafetyBlocks {
    private SafetyBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new SafetyBlock("axle_counter_head", TileAxleHead::new, new double[]{2, 0, 3, 14, 6, 13},
                        "Axle counter head: double wheel sensor clamped to the rail, counts each wheelset and its direction",
                        "Arrow along the track. Wrench-pick it, then click a section on an Axle Counter Evaluator. 12 V"),
                new SafetyBlock("axle_evaluator", TileAxleEvaluator::new, new double[]{1, 0, 2, 15, 16, 15},
                        "Axle counter evaluator: 4 sections, counts in / out, CLEAR / OCCUPIED / DISTURBED",
                        "Reset key: PREP (a train sweeps it) or DIRECT. Wrench heads and signals onto a section. 12 V"),
                new SafetyBlock("radio_mast", TileRadioMast::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Train radio mast: a GSM-R cell, 96 / 160 / 256 m, trains register and report",
                        "Lost link inside the cell = fail-safe fallback (brake / hold / warn). 12 V"),
                new SafetyBlock("radio_desk", TileRadioDesk::new, new double[]{0, 0, 1, 16, 15, 15},
                        "Radio dispatch desk: call a train, broadcast to a line, all-call, emergency all-stop",
                        "Answers lineside emergency calls; communications log on screen. 12 V"),
                new SafetyBlock("emergency_plunger", TileEmergencyPlunger::new, new double[]{4, 0, 5, 12, 16, 12},
                        "Emergency stop plunger: all signals into its zone to danger, radio trains stopped",
                        "Opens an incident; resets only with the key once trains are stopped and the signaller answered"),
                new SafetyBlock("possession_switch", TilePossessionSwitch::new, new double[]{4, 0, 5, 12, 16, 12},
                        "Track blocking switch: key to BLOCK takes a possession of the line",
                        "Signals into its zone to danger, routes through it refused; granted once no train is in it"),
                new SafetyBlock("call_point", TileCallPoint::new, new double[]{4, 0, 6, 12, 16, 12},
                        "Lineside emergency telephone: CALL rings every Radio Dispatch Desk", "Logged; alarms if not answered in 30 s. 12 V"),
                new SafetyBlock("banner_repeater", TileBannerRepeater::new, new double[]{4, 0, 5, 12, 16, 11},
                        "Banner repeating signal: arm level = signal ahead at danger / caution, 45 degrees = clear",
                        "Wrench-link it to a poorly sighted signal: its sighting counts, no early braking. 12 V"),
                new SafetyBlock("train_describer", TileTrainDescriber::new, new double[]{0, 0, 9, 16, 16, 16},
                        "Train describer: 8 berths showing the headcode of the train at each signal",
                        "Wrench a signal onto each berth window. Headcode / radio id / speed selector. 12 V"),
                new SafetyBlock("fault_lab", TileFaultLab::new, new double[]{0, 0, 1, 16, 15, 15},
                        "Fault-injection lab console: TEST MODE, inject faults, automated pass/fail sequence",
                        "Drop track circuits, fail lamps, jam points, phantom trains, cut radio / 12 V. Prints a report"));
    }

    public static void registerTiles() {
        reg(TileAxleHead.class, "axle_counter_head");
        reg(TileAxleEvaluator.class, "axle_evaluator");
        reg(TileRadioMast.class, "radio_mast");
        reg(TileRadioDesk.class, "radio_desk");
        reg(TileEmergencyPlunger.class, "emergency_plunger");
        reg(TilePossessionSwitch.class, "possession_switch");
        reg(TileCallPoint.class, "call_point");
        reg(TileBannerRepeater.class, "banner_repeater");
        reg(TileTrainDescriber.class, "train_describer");
        reg(TileFaultLab.class, "fault_lab");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
