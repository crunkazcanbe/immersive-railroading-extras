package com.dogpound.railmap.ohle;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.safety.SafetyBlock;
import com.dogpound.railmap.weather.*;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.Arrays;
import java.util.List;

/** Overhead line gear (her ChatGPT list #31-32) and railway weather gear (#63-66); CommonProxy registers them. */
public final class OhleBlocks {
    private OhleBlocks() {}

    public static List<Block> create() {
        return Arrays.asList(
                new SafetyBlock("ohle_isolator", TileIsolator::new, new double[]{2, 0, 2, 14, 16, 14},
                        "Motorised OHLE isolator: bridges the Section Insulator beside it (auto-linked within 6 blocks)",
                        "LOCAL / REMOTE / AUTO (fault isolation), earth switch for crews, hand crank without 12 V. Off-load only!"),
                new SafetyBlock("ohle_tensioner", TileTensioner::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Balance-weight tensioner: holds the wire of its section at 15 kN whatever the weather",
                        "Without one (or locked) the wire sags in heat and arcs at speed. Mechanical - no power"),
                new SafetyBlock("ohle_magnet", TileApcMagnet::new, new double[]{4, 0, 5, 12, 16, 11},
                        "Pantograph sign + track magnet: LOWER PAN / RAISE PAN / COAST (APC)",
                        "Place beside the track; locos in AUTO lower / raise / coast as they pass it"),
                new SafetyBlock("ohle_diagram", TileFeedingDiagram::new, new double[]{0, 0, 12, 16, 16, 16},
                        "OHLE feeding diagram: live mimic of the nearest sections, feeder breakers and isolators",
                        "CLOSE / OPEN feeder breakers (reset a LOCKOUT), operate REMOTE isolators. 12 V"),
                new SafetyBlock("points_heater", TilePointsHeater::new, new double[]{3, 0, 4, 13, 16, 12},
                        "Points heater cabinet: heats every switch within 12 blocks (12 kW)",
                        "AUTO thermostat / ON / OFF. Power: a cable beside it, the OHLE, or a substation. 12 V control"),
                new SafetyBlock("weather_station", TileWeatherStation::new, new double[]{3, 0, 3, 13, 16, 13},
                        "Lineside weather station: wind, rain, air + rail temperature, visibility, snow, ice, flood",
                        "Feeds the Control Center's weather page; raises frost / fog alarms. 12 V"),
                new SafetyBlock("rail_temp_monitor", TileRailTempMonitor::new, new double[]{3, 0, 4, 13, 14, 12},
                        "Rail temperature monitor: rail vs stress-free temperature, heat restrictions, buckle risk",
                        "PATROL eases a heat restriction; 16 gravel = ballast work; 6 iron ingots repair a buckle. 12 V"),
                new SafetyBlock("culvert", TileCulvert::new, new double[]{0, 0, 0, 16, 10, 16},
                        "Box culvert with trash screen: drains 1.5 mm / 5 s from its 64-block cell",
                        "Storm debris blocks the screen - click it to clear. Passive, no power"),
                new SafetyBlock("pump_station", TilePumpStation::new, new double[]{1, 0, 1, 15, 16, 15},
                        "Drainage pump station: two 15 kW pumps (duty / standby) on a float switch",
                        "Drains 3 mm / 5 s per pump. Service every 120 running minutes (MAINT key + SERVICE). 12 V"),
                // ---- batch 3 (#36-50): com.dogpound.railmap.infra
                new SafetyBlock("wayside_ess", com.dogpound.railmap.infra.TileWaysideEss::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Wayside energy storage: soaks up regenerative braking, gives it back to accelerating trains",
                        "Wire its PCS to the traction network (600 V-3 kV DC or 13.8 kV AC cable touching it) + 12 V. Li-ion / supercap / hybrid"),
                new SafetyBlock("ohle_fault_locator", com.dogpound.railmap.infra.TileFaultLocator::new, new double[]{1, 0, 4, 15, 16, 16},
                        "OHLE fault locator: distance to the fault from the feeder relay's impedance measurement",
                        "Single / double-ended methods; DISPATCH sends an inspection crew. 12 V"),
                new SafetyBlock("relay_test_set", com.dogpound.railmap.infra.TileRelayTestSet::new, new double[]{2, 0, 2, 14, 15, 14},
                        "Relay test set: secondary injection - pick-up, timing, full certificate, calibration",
                        "Within 16 blocks of a Protection IED. 120 / 240 V AC cable touching it + 12 V. Isolate the TEST BLOCK!"),
                new SafetyBlock("rail_grinder", com.dogpound.railmap.infra.TileRailGrinder::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Rail grinding machine: drives itself along the rails grinding out corrugation and RCF",
                        "Place it ON a rail facing along the track. Diesel / oil / coal in the filler, flint for stones"),
                new SafetyBlock("rail_welder", com.dogpound.railmap.infra.TileRailWelder::new, new double[]{0, 0, 0, 16, 16, 16},
                        "Mobile flash-butt rail welder: seeks logged rail defects, cuts them out, welds in a closure rail",
                        "Place it ON a rail facing along the track. CUT ALIGN PREHEAT WELD SHEAR GRIND UT in order; 2 iron = 1 plug"),
                new SafetyBlock("flood_sensor", com.dogpound.railmap.infra.TileFloodSensor::new, new double[]{4, 0, 4, 12, 16, 12},
                        "Flood warning outstation: water level, rate of rise, time to the rail head, siren + beacon",
                        "TSR imposes a 20 km/h flood watch, PUMPS forces the cell's pumps on. 12 V"),
                new SafetyBlock("ohle_rec_station", com.dogpound.railmap.infra.TileOhleRecStation::new, new double[]{1, 0, 3, 15, 16, 15},
                        "OHLE recording station: wire height, stagger and wear from a recording car (IR stock tagged OHLE)",
                        "NEW RUN / END / ORDERS / PRINT. 120 / 240 V AC cable touching it + 12 V"));
    }

    public static void registerTiles() {
        reg(TileIsolator.class, "ohle_isolator");
        reg(TileTensioner.class, "ohle_tensioner");
        reg(TileApcMagnet.class, "ohle_magnet");
        reg(TileFeedingDiagram.class, "ohle_diagram");
        reg(TilePointsHeater.class, "points_heater");
        reg(TileWeatherStation.class, "weather_station");
        reg(TileRailTempMonitor.class, "rail_temp_monitor");
        reg(TileCulvert.class, "culvert");
        reg(TilePumpStation.class, "pump_station");
        reg(com.dogpound.railmap.infra.TileWaysideEss.class, "wayside_ess");
        reg(com.dogpound.railmap.infra.TileFaultLocator.class, "ohle_fault_locator");
        reg(com.dogpound.railmap.infra.TileRelayTestSet.class, "relay_test_set");
        reg(com.dogpound.railmap.infra.TileRailGrinder.class, "rail_grinder");
        reg(com.dogpound.railmap.infra.TileRailWelder.class, "rail_welder");
        reg(com.dogpound.railmap.infra.TileFloodSensor.class, "flood_sensor");
        reg(com.dogpound.railmap.infra.TileOhleRecStation.class, "ohle_rec_station");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
