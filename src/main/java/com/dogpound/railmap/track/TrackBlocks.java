package com.dogpound.railmap.track;

import com.dogpound.railmap.RailMap;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.List;

/** Track infrastructure blocks and tools (her ChatGPT list section 3, #13-18 + tunnel building); CommonProxy registers them. */
public final class TrackBlocks {
    private TrackBlocks() {}

    public static final String[] LININGS = {"concrete", "brick", "stone"};

    public static List<Block> create() {
        List<Block> l = new ArrayList<>();
        l.add(new TrackBlock("point_machine", TilePointMachine::new, new double[]{0, 0, 1, 16, 14, 16},
                "Point machine (S700K style): drives a set of points, proves the blades, heats them, reports failures",
                "Signal Wrench: pick the switch, click the POSITION display. Electric / hydraulic / pneumatic, dual motors, hand crank. 12 V"));
        l.add(new TrackBlock("structure_board", TileStructureBoard::new, new double[]{0, 0, 6, 16, 16, 12},
                "Structure asset board: bridge / viaduct / tunnel / culvert - load rating, clearance, condition, examinations",
                "Wrench a rail onto START and END. Weighs every axle, stops overweight / out-of-gauge trains, floods + rockfall hold signals. 12 V"));
        l.add(new TrackBlock("crossing_predictor", TileCrossingPredictor::new, new double[]{1, 0, 3, 15, 16, 13},
                "Level crossing predictor: constant warning time, road traffic queues, obstacle detection, barrier failures",
                "Wrench crossings and protecting signals onto it. Emergency vehicles, counters, reliability report. 12 V"));
        l.add(new TrackBlock("tunnel_control", TileTunnelControl::new, new double[]{0, 0, 11, 16, 16, 16},
                "Tunnel control panel: ventilation, smoke + CO, fans, lights, cross-passages, emergency sequence",
                "Covers the tunnel around it (zone selector). Fire = signals held, traction off, evacuate. 12 V"));
        l.add(new TrackBlock("tunnel_fan", TileTunnelFan::new, new double[]{0, 4, 0, 16, 16, 16},
                "Tunnel jet fan: 37 kW reversible, worked by the tunnel control panel",
                "Needs a grid Power Cable touching it (400 V - 11 kV) for the motor + 12 V for the starter. Hang it from the roof"));
        l.add(new TrackBlock("smoke_detector", TileSmokeDetector::new, new double[]{3, 3, 12, 13, 13, 16},
                "Tunnel smoke + CO detector: sees fire, lava and burning things; reads the tunnel air",
                "ALARM puts the tunnel into emergency (ISOLATE it before testing). 12 V"));
        l.add(new TrackBlock("tunnel_light", TileTunnelLight::new, new double[]{1, 4, 12, 15, 12, 16},
                "Tunnel light: really lights the tunnel on 12 V, 3 h emergency battery without",
                "The tunnel control panel switches them OFF / NORMAL / BOOST; full in an emergency"));
        l.add(new TrackBlock("exit_sign", TileExitSign::new, new double[]{1, 4, 13, 15, 12, 16},
                "Evacuation sign: green running man, distance to the nearest cross-passage / portal each way",
                "Lit (battery backed); arrows flash in an emergency"));
        l.add(new TrackBlock("cross_passage", TileCrossPassage::new, new double[]{0, 0, 10, 16, 16, 16}, true,
                "Cross-passage / refuge door: self-closing fire door, refuge pressurised in an emergency",
                "Counts the people sheltering behind it. Set it in the tunnel wall"));
        l.add(new TrackBlock("leaky_feeder", TileLeakyFeeder::new, new double[]{2, 0, 9, 14, 14, 16},
                "Tunnel radio: GSM-R leaky feeder repeater - a radio cell inside the tunnel",
                "Trains in the tunnel stay on the radio. Cable run 128 / 192 / 256 m. 12 V"));
        l.add(new TrackBlock("tunnel_portal", TileTunnelPortal::new, null, true,
                "Tunnel portal headwall with the tunnel's name plate, end and length",
                "Name it with a renamed Name Tag. Put it over the tunnel mouth"));
        l.add(new TrackBlock("tunnel_marker", TileTunnelMarker::new, new double[]{3, 4, 14, 13, 12, 16},
                "Tunnel distance marker: metres from the portal, distance to the nearest exit", "Measured from the real portals and cross-passages"));
        l.add(new TrackBlock("survey_peg", TileSurveyPeg::new, new double[]{6, 0, 6, 10, 14, 10},
                "Survey peg: chainage, cut / fill and grade, set out by the Surveyor's instrument", "Build to the pegs"));
        for (String m : LININGS)
            for (BlockLining.Piece p : new BlockLining.Piece[]{BlockLining.Piece.WALL, BlockLining.Piece.HAUNCH, BlockLining.Piece.CROWN, BlockLining.Piece.NICHE})
                l.add(new BlockLining(m, p));
        l.add(new BlockLining("concrete", BlockLining.Piece.TROUGH));
        l.add(new TrackBlock("turntable_control", TileTurntableControl::new, new double[]{0, 0, 2, 16, 16, 16},   // batch 3 #3
                "Turntable / traverser panel: drives an IR turntable or transfer table - roads, motor, locking pins, interlocks",
                "Wrench a table rail onto TABLE, road-end rails onto ROAD 1-8. Pins in only when aligned; signals held unless aligned + pinned. 12 V + 400 V motor"));
        l.add(new TrackBlock("bridge_control", TileMovableBridge::new, new double[]{0, 0, 2, 16, 16, 16},   // batch 3 #8
                "Movable bridge desk: swing (an IR turntable span) or bascule - locks, position detection, approach locking, waterway",
                "Swing: wrench the turntable onto TABLE, the railway onto RAIL. Bascule: wrench HEEL + TOE rails. Boats wait for it to open. 12 V + 400 V motor"));
        l.add(new TrackBlock("loco_changeover", com.dogpound.railmap.planning.TileChangeover::new, new double[]{1, 0, 8, 15, 16, 16},   // batch 3 #1
                "Loco changeover cabinet: driverless trains change traction here - a standby loco comes on, the old one comes off",
                "MODE picks the traction wanted, MAX DWELL the limit, BRAKE key the continuity test. Park the standby loco at the outbound end. 12 V"));
        l.add(new TrackBlock("banking_board", com.dogpound.railmap.planning.TileBankingBoard::new, new double[]{1, 0, 8, 15, 16, 16},   // batch 3 #2
                "Banking board at the foot of a bank: a banker loco helps heavy trains up - coupled or buffered, shared TE, drops off at the summit",
                "Signal Wrench: pick the summit rail, click SUMMIT (and FOOT for the banker siding). Park a loose loco nearby as the banker. 12 V"));
        l.add(new TrackBlock("coupling_point", com.dogpound.railmap.planning.TileCouplingPoint::new, new double[]{1, 0, 8, 15, 16, 16},   // batch 3 #4
                "Automatic coupling point: joins and splits driverless units at the platform - compatibility, length, brake, order checks",
                "RULE JOIN / SPLIT. Platform length on the selector or wrench both platform ends onto END A / END B. 12 V + 400 V coupler air compressor"));
        l.add(new TrackBlock("turnback_panel", com.dogpound.railmap.planning.TileTurnbackPanel::new, new double[]{1, 0, 8, 15, 16, 16},   // batch 3 #5
                "Automatic turnback panel: trains reversing here change ends properly - crew walk, cab change, route check, brake test",
                "Put it at a terminus. MODE PUSH-PULL lets a loco propel, CAB END ONLY needs a cab at the other end. 12 V"));
        l.add(new TrackBlock("buffer_stop", TileBufferStop::new, new double[]{0, 0, 0, 16, 14, 16},   // batch 3 #9
                "Intelligent buffer stop: fixed / hydraulic / friction - absorbs the hit, radar-supervises the approach, emergency-brakes trains too fast to stop",
                "Place it at the end of a dead-end road facing the track. INHIBIT key for shunting. RESET after a hit (friction / damaged). 12 V radar"));
        l.add(new TrackBlock("single_line_control", TileSingleLine::new, new double[]{1, 0, 9, 15, 16, 16},   // batch 3 #7
                "Single-line / gauntlet section controller: one direction at a time - directional occupancy, entry reservation, entry signals held",
                "Signal Wrench: pick a rail at each end, click END A / END B; pick entry signals, click SIG A / SIG B. MODE AUTO / FIXED / CLOSED. 12 V"));
        l.add(new TrackBlock("yard_retarder", com.dogpound.railmap.yard.TileRetarder::new, new double[]{0, 0, 0, 16, 12, 16},   // batch 3 #25
                "Yard retarder: pneumatic rail brake - speed radar, weigh-in-motion, target / calculated exit speed, air, shoe wear",
                "Place beside a hump or class track (or wrench its rail onto RAIL). Needs 12 V + a 400 V Power Cable for the compressor"));
        return l;
    }

    public static Item[] items() { return new Item[]{new ItemTrackTrolley(), new ItemTrackKit(), new ItemSurveyor()}; }

    public static void registerTiles() {
        reg(TilePointMachine.class, "point_machine");
        reg(TileStructureBoard.class, "structure_board");
        reg(TileCrossingPredictor.class, "crossing_predictor");
        reg(TileTunnelControl.class, "tunnel_control");
        reg(TileTunnelFan.class, "tunnel_fan");
        reg(TileSmokeDetector.class, "smoke_detector");
        reg(TileTunnelLight.class, "tunnel_light");
        reg(TileExitSign.class, "exit_sign");
        reg(TileCrossPassage.class, "cross_passage");
        reg(TileLeakyFeeder.class, "leaky_feeder");
        reg(TileTunnelPortal.class, "tunnel_portal");
        reg(TileTunnelMarker.class, "tunnel_marker");
        reg(TileSurveyPeg.class, "survey_peg");
        reg(com.dogpound.railmap.planning.TileChangeover.class, "loco_changeover");
        reg(com.dogpound.railmap.planning.TileBankingBoard.class, "banking_board");
        reg(com.dogpound.railmap.planning.TileCouplingPoint.class, "coupling_point");
        reg(com.dogpound.railmap.planning.TileTurnbackPanel.class, "turnback_panel");
        reg(TileTurntableControl.class, "turntable_control");
        reg(TileMovableBridge.class, "bridge_control");
        reg(TileBufferStop.class, "buffer_stop");
        reg(TileSingleLine.class, "single_line_control");
        reg(com.dogpound.railmap.yard.TileRetarder.class, "yard_retarder");
    }

    private static void reg(Class<? extends net.minecraft.tileentity.TileEntity> c, String id) {
        GameRegistry.registerTileEntity(c, new ResourceLocation(RailMap.MODID, id));
    }
}
