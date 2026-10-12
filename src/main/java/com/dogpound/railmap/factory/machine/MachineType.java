package com.dogpound.railmap.factory.machine;

import com.dogpound.railmap.grid.Panel;

/**
 * Every Pride Industry plant: its structure, its power, and the real control panel on its controller.
 *
 * Patterns are layers from the ground up; each layer is rows from the FRONT (the controller's panel side, row 0)
 * to the back; each row reads from the viewer's LEFT to right while facing the panel.
 *   C controller · # casing · I item input hatch · O item output hatch · W water inlet · D drive coupling (power in)
 *   T clearance that must stay open (the track runs through it) · space = not part of the machine
 */
public enum MachineType {
    JAW_CRUSHER("jaw_crusher", "Jaw Crusher",
            "Crushes any mod's ore: 1 ore -> 2 crushed ore. Ore in the top hopper, crushed ore out of the front chute, an electric motor on the back coupling.",
            60, 24_000,
            new String[][]{
                    {"#CO", "###", "#D#"},
                    {"###", "###", "###"},
                    {"###", "#I#", "###"}}),
    WASHING_PLANT("washing_plant", "Washing Plant",
            "Scrubs crushed ore in a rotating trommel: 1 crushed -> 2 washed ore (250 mB water each). Water in through the side inlet.",
            40, 16_000,
            new String[][]{
                    {"#C#", "###", "###", "#O#"},
                    {"###", "W##", "##D", "###"},
                    {"#I#", "###", "###", "###"}}),
    FLOTATION("flotation", "Flotation Cells",
            "Froth flotation: 2 washed ore -> 3 concentrate (500 mB water per batch). Air blown through the cells floats the metal off.",
            50, 20_000,
            new String[][]{
                    {"#CO", "###", "#D#"},
                    {"###", "W##", "###"},
                    {"###", "#I#", "###"}}),
    SMELTER("smelter", "Induction Smelter",
            "Electric induction furnace: melts ore into real molten metal (3 concentrate -> 4 ingots worth, 144 mB an ingot) at 1,450 °C, then pours it into ingot moulds - or holds it for pipes at the Fluid Output Hatch (Tinkers' molten metals when Tinkers has the metal).",
            300, 120_000,
            new String[][]{
                    {"#CO", "###", "FD#"},
                    {"###", "###", "###"},
                    {"###", "#I#", "###"}}),
    ASSEMBLY_LINE("assembly_line", "Assembly Line",
            "A five-station production line (press, weld, rivet, paint, inspect) that builds parts from any mod's ingots: casings, motors, couplings, belts, cables, rails, carts, hatches. Pick the product on its panel.",
            80, 40_000,
            new String[][]{
                    {"IC#", "###", "###", "##D", "###", "###", "#O#"},
                    {"###", "###", "###", "###", "###", "###", "###"},
                    {"###", "###", "###", "###", "###", "###", "###"}}),
    BULK_LOADER("bulk_loader", "Bulk Loading Station",
            "A loadout tower that straddles the track: fills freight cars standing under its chute from the silo above. Feed the silo through the top hatch.",
            15, 6_000,
            new String[][]{
                    {"CT#", " T ", "#T#"},
                    {"#T#", " T ", "#T#"},
                    {"#T#", " T ", "#T#"},
                    {"#T#", " T ", "#T#"},
                    {"#T#", " T ", "#T#"},
                    {"###", "###", "###"},
                    {"#D#", "###", "###"},
                    {"###", "#I#", "###"}}),
    UNLOADER("unloader", "Rail Unloading Pit",
            "A grated pit under the track: empties freight cars that stop over it into its bin; take the cargo out of the side hatch.",
            15, 6_000,
            new String[][]{
                    {"###", "O##", "###"},
                    {"CT#", "#T#", "DT#"}}),
    BOREHOLE_PUMP("borehole_pump", "Borehole Pump",
            "Pumps groundwater up a borehole drilled to bedrock under the pump house: deeper wells through water-bearing gravel, sand and flooded caves give more. Overpumping draws the aquifer down - rain and wet biomes refill it. Water leaves through the Fluid Output Hatch into any mod's pipes.",
            40, 16_000,
            new String[][]{
                    {"#C#", "###", "FD#"},
                    {"###", "###", "###"},
                    {"   ", " # ", "   "},
                    {"   ", " # ", "   "}}),
    WATER_TOWER("water_tower", "Water Tower",
            "64,000 L elevated storage: water in at the Water Inlet, out by gravity at the Fluid Output Hatch - the fuller and higher the tank, the harder it pushes. Motor-operated inlet / outlet valves, level alarms, AUTO fill control and a comparator signal for the level.",
            4, 4_000,
            new String[][]{
                    {"WCF", "   ", "# D"},
                    {"   ", "   ", "   "},
                    {"   ", "   ", "   "},
                    {"###", "###", "###"},
                    {"###", "# #", "###"},
                    {"###", "# #", "###"},
                    {"###", "###", "###"}}),
    // ---- trains ----
    OVERHEAD_LOADER("overhead_loader", "Overhead Loading Silo",
            "Sits on the floor of your building above the track: a telescoping chute drops through a hole in the floor (up to 12 blocks) into the open top of the car below. Fill-to %, live fill readout, and a STOP / ADVANCE signal for the driver.",
            25, 10_000,
            new String[][]{
                    {"#C#", "###", "DI#"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {" # ", "###", " # "}}),
    CAR_DUMPER("car_dumper", "Rotary Car Dumper",
            "The track runs through a steel cradle: it clamps the freight car, rolls it over 155 degrees, empties it into the hopper underneath and rolls it back. Empties a whole car in one cycle.",
            120, 40_000,
            new String[][]{
                    {"#CTO#", "##T##", "#DT##"},
                    {"# T #", "  T  ", "# T #"},
                    {"# T #", "  T  ", "# T #"},
                    {"# T #", "  T  ", "# T #"},
                    {"#####", "     ", "#####"}}),
    TANK_LOADER("tank_loader", "Tank Car Loading Arm",
            "A loading gantry beside the track: its arm swings down into the dome of the tank car standing under it and pumps any fluid in from the inlet - also refuels diesel locomotives.",
            30, 12_000,
            new String[][]{
                    {"#C T", "W# T", "#D T"},
                    {"## T", "## T", "## T"},
                    {"## T", "## T", "## T"},
                    {"####", "####", "####"}}),
    TANK_UNLOADER("tank_unloader", "Tank Car Unloading Station",
            "Couples to the bottom outlet of a tank car standing beside it and pumps the load out to the fluid hatch and on into any mod's pipes.",
            30, 12_000,
            new String[][]{
                    {"#C T", "F# T", "#D T"},
                    {"## T", "## T", "## T"},
                    {"####", "####", "####"}}),
    // ---- steel ----
    COKE_OVEN("coke_oven", "Coke Oven Battery",
            "Bakes coal into coke at 1,100 degrees with the air shut out, and runs the creosote off to the fluid hatch. Coal in the top, coke out of the side.",
            90, 30_000,
            new String[][]{
                    {"OC#", "###", "FD#"},
                    {"###", "###", "###"},
                    {"###", "#I#", "###"}}),
    BLAST_FURNACE("blast_furnace", "Blast Furnace",
            "Iron and coke in at the top, hot blast in at the tuyeres: steel out of the tap hole at 1,500 degrees, slag skimmed off. 1 iron + 1 coke -> 1 steel.",
            220, 90_000,
            new String[][]{
                    {"OC#", "###", "#D#"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {" # ", "#I#", " # "}}),
    ROLLING_MILL("rolling_mill", "Rolling Mill",
            "Hot rolls ingots into plates: steel into Reinforced Steel Plates (the base of every part), and any mod's metal into its own plates.",
            140, 50_000,
            new String[][]{
                    {"IC#", "###", "D##", "###", "#O#"},
                    {"###", "###", "###", "###", "###"}}),
    WIRE_DRAWER("wire_drawer", "Wire Drawing Machine",
            "Pulls ingots through shrinking dies onto a capstan: copper or gold into Copper Windings, iron into Instrument Wire.",
            60, 20_000,
            new String[][]{
                    {"IC#", "###", "##D", "#O#"},
                    {"###", "###", "###", "###"}}),
    // ---- fuel and materials ----
    OIL_PRESS("oil_press", "Oil Press",
            "A screw press that squeezes seeds into plant oil (to the fluid hatch). Feed it any mod's seeds.",
            40, 12_000,
            new String[][]{
                    {"#C#", "###", "FD#"},
                    {"###", "###", "###"},
                    {"###", "#I#", "###"}}),
    REFINERY("refinery", "Refinery",
            "A distillation column: plant oil, crude oil or creosote in at the fluid inlet, diesel out of the fluid hatch - for your gensets and diesel locomotives.",
            110, 40_000,
            new String[][]{
                    {"WC#", "###", "FD#"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {" # ", "###", " # "}}),
    SAWMILL("sawmill", "Sawmill",
            "A carriage runs every log through the big saw blade: 1 log -> 6 planks + 2 sawdust (any mod's wood).",
            70, 20_000,
            new String[][]{
                    {"#CI", "###", "D##", "#O#"},
                    {"###", "###", "###", "###"}}),
    CRATE_PACKER("crate_packer", "Crate Packer",
            "Packs a full stack of anything into one Shipping Crate (crates stack 16 high - 16x the freight in a car), or unpacks crates back out.",
            30, 10_000,
            new String[][]{
                    {"IC#", "###", "#DO"},
                    {"###", "###", "###"}}),
    WINDMILL("windmill", "Grain Windmill",
            "A brick tower mill: four sails turn the millstones with the wind - grain -> flour (bake it into bread). No electricity. Reef or furl the sails in a storm or it tears them off.",
            0, 1_000,
            new String[][]{
                    {"#C#", "###", "I#O"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {" # ", "###", " # "}}),
    ARC_FURNACE("arc_furnace", "Electric Arc Furnace",
            "Three graphite electrodes arc scrap, iron and ore molten: iron + coke -> steel, faster than the Blast Furnace. Plugs straight into the grid (13.8 kV 3-phase AC) - overload it and the furnace transformer EXPLODES.",
            1000, 60_000,
            new String[][]{
                    {"IC#", "###", "WPO"},
                    {"###", "###", "###"},
                    {"###", "###", "###"},
                    {" # ", "###", " # "}}),
    ELECTROLYSER("electrolyser", "Electrolysis Plant",
            "A hall of cells splits piped water into hydrogen + oxygen (pipe the oxygen to the Arc Furnace). Needs DC (750 V) - AC does nothing, too much voltage and the hydrogen BLOWS UP.",
            250, 15_000,
            new String[][]{
                    {"#C#", "###", "###", "WPF"},
                    {"###", "###", "###", "###"},
                    {"###", "###", "###", "###"}}),
    WELDING_CELL("welding_cell", "Robotic Welding Cell",
            "A robot arm welds steel into IR Extras parts: reinforced plates, machine frames, steel shafts. Runs on 480 V 3-phase - too much and the welding transformer burns up.",
            75, 6_000,
            new String[][]{
                    {"IC#", "###", "#PO"},
                    {"###", "###", "###"},
                    {"###", "###", "###"}}),
    // ---- railway materials (her list section 13, chain A "build your own railway") ----
    TIE_PLANT("tie_plant", "Sleeper Treatment Plant",
            "A Bethell full-cell creosoting works: a charge of timber (2 planks a sleeper, up to 8 sleepers) goes into the pressure cylinder; vacuum, flood with hot creosote from the inlet, 12 bar pressure, drain - out come Creosoted Sleepers that Immersive Railroading track is built from.",
            70, 24_000,
            new String[][]{
                    {"IC#", "W##", "##D", "###", "#O#"},
                    {"###", "###", "###", "###", "###"}});

    public final String id, label, tip;
    /** FE per tick it draws at full rate */
    public final int power;
    /** FE its drive / power inlet can store */
    public final int buffer;
    public final String[][] pattern;
    private Panel panel;

    MachineType(String id, String label, String tip, int power, int buffer, String[][] pattern) {
        this.id = id; this.label = label; this.tip = tip; this.power = power; this.buffer = buffer; this.pattern = pattern;
    }

    public int width() { return pattern[0][0].length(); }
    public int height() { return pattern.length; }
    public int depth() { return pattern[0].length; }

    /** pattern coordinates of the controller: {x, y, z} */
    public int[] controller() {
        for (int y = 0; y < pattern.length; y++)
            for (int z = 0; z < pattern[y].length; z++) {
                int x = pattern[y][z].indexOf('C');
                if (x >= 0) return new int[]{x, y, z};
            }
        throw new IllegalStateException("no controller in " + id);
    }

    public Panel panel() {
        if (panel == null) panel = buildPanel();
        return panel;
    }

    private static final int GREY = 0xC5C7C4, DARK = 0x3A3F44, CREAM = 0xE4DCC4;

    /** the real controls of each plant (only what the real equipment has) */
    private Panel buildPanel() {
        Panel p;
        switch (this) {
            case JAW_CRUSHER: {
                p = new Panel("JAW CRUSHER", 2.5, 1.5, 13.5, 14.5, -1.0, GREY);
                p.label(8, 13.6, "JAW CRUSHER");
                p.digits("rate", 5.8, 12.1, 4.2, 0x40FF60, "%d/min", 1);
                p.digits("ores", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("load", 4.8, 8.6, 1.6, "MOTOR %", 150);
                p.sel("feed", 8.6, 8.8, "FEED", "LOW", "MED", "HIGH");
                p.lamp("lpower", 12.0, 10.0, Panel.GREEN, "POWER");
                p.lamp("ljam", 12.0, 8.2, Panel.AMBER, "JAM").blink = true;
                p.push("start", 3.8, 5.0, Panel.GREEN, "START");
                p.push("stop", 6.0, 5.0, Panel.RED, "STOP");
                p.push("clear", 8.2, 5.0, Panel.YELLOW, "CLEAR JAM");
                p.lamp("lrun", 3.8, 2.9, Panel.GREEN, "RUN");
                p.lamp("lfault", 6.0, 2.9, Panel.RED, "FAULT").blink = true;
                p.push("reset", 8.2, 2.9, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 4.0, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case WASHING_PLANT: {
                p = new Panel("WASHING PLANT", 2.5, 1.5, 13.5, 14.5, -1.0, GREY);
                p.label(8, 13.6, "WASHING PLANT");
                p.digits("rate", 5.8, 12.1, 4.2, 0x40FF60, "%d/min", 1);
                p.digits("ores", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("water", 4.8, 8.6, 1.6, "WATER %", 100);
                p.sel("valve", 8.6, 8.8, "WATER", "SHUT", "OPEN");
                p.lamp("llow", 12.0, 10.0, Panel.AMBER, "LOW H2O").blink = true;
                p.lamp("lpower", 12.0, 8.2, Panel.GREEN, "POWER");
                p.push("start", 3.8, 5.0, Panel.GREEN, "START");
                p.push("stop", 6.0, 5.0, Panel.RED, "STOP");
                p.lamp("lrun", 3.8, 2.9, Panel.GREEN, "DRUM");
                p.lamp("lfault", 6.0, 2.9, Panel.RED, "FAULT").blink = true;
                p.push("reset", 8.4, 2.9, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 4.0, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case FLOTATION: {
                p = new Panel("FLOTATION", 2.5, 1.5, 13.5, 14.5, -1.0, GREY);
                p.label(8, 13.6, "FLOTATION CELLS");
                p.digits("rate", 5.8, 12.1, 4.2, 0x40FF60, "%d/min", 1);
                p.digits("ores", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("froth", 4.6, 8.6, 1.5, "FROTH %", 100);
                p.gauge("water", 8.0, 8.6, 1.5, "WATER %", 100);
                p.sel("air", 11.6, 8.8, "AIR", "LOW", "HIGH");
                p.push("start", 3.8, 5.0, Panel.GREEN, "START");
                p.push("stop", 6.0, 5.0, Panel.RED, "STOP");
                p.lamp("lrun", 3.8, 2.9, Panel.GREEN, "BLOWER");
                p.lamp("llow", 6.0, 2.9, Panel.AMBER, "LOW H2O").blink = true;
                p.push("reset", 8.4, 2.9, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 4.0, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case SMELTER: {
                p = new Panel("INDUCTION", 2.5, 1.5, 13.5, 14.5, -1.0, DARK);
                p.label(8, 13.6, "INDUCTION FURNACE");
                p.digits("temp", 5.8, 12.1, 4.2, 0xFF7030, "%d°C", 1);
                p.digits("metal", 10.6, 12.1, 4.2, 0xFFB040, "%d mB", 1);
                p.gauge("tempg", 4.2, 8.6, 1.4, "°C x100", 16);
                p.sel("setpoint", 7.4, 8.8, "POWER", "50%", "75%", "100%");
                p.sel("outlet", 10.4, 8.8, "OUTLET", "MOULD", "PIPE");
                p.lamp("lheat", 12.6, 10.6, Panel.AMBER, "HEATING");
                p.lamp("lready", 12.6, 7.4, Panel.GREEN, "MOLTEN");
                p.push("start", 3.8, 5.0, Panel.GREEN, "POWER ON");
                p.push("stop", 6.0, 5.0, Panel.RED, "POWER OFF");
                p.lamp("lrun", 3.8, 2.9, Panel.RED, "COIL LIVE");
                p.lamp("lfault", 6.0, 2.9, Panel.RED, "FAULT").blink = true;
                p.push("reset", 8.4, 2.9, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 4.0, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case ASSEMBLY_LINE: {
                p = new Panel("ASSEMBLY", 2.5, 1.5, 13.5, 14.5, -1.0, GREY);
                p.label(8, 13.6, "ASSEMBLY LINE");
                p.digits("product", 5.8, 12.1, 4.2, 0x40FF60, "%s", 1);
                p.digits("ores", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.push("prev", 3.6, 9.8, Panel.BLACK, "◀");
                p.push("next", 6.0, 9.8, Panel.BLACK, "▶");
                p.gauge("load", 9.8, 9.4, 1.4, "LINE %", 150);
                p.lamp("s0", 3.2, 7.3, Panel.GREEN, "PRS"); p.lamp("s1", 5.0, 7.3, Panel.GREEN, "WLD"); p.lamp("s2", 6.8, 7.3, Panel.GREEN, "RVT");
                p.lamp("s3", 8.6, 7.3, Panel.GREEN, "PNT"); p.lamp("s4", 10.4, 7.3, Panel.GREEN, "QC");
                p.lamp("lwait", 12.4, 7.3, Panel.AMBER, "PARTS").blink = true;
                p.push("start", 3.8, 4.6, Panel.GREEN, "START");
                p.push("stop", 6.0, 4.6, Panel.RED, "STOP");
                p.push("reset", 8.2, 4.6, Panel.BLUE, "RESET");
                p.lamp("lrun", 3.8, 2.6, Panel.GREEN, "RUN");
                p.lamp("lfault", 6.0, 2.6, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.8, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case BULK_LOADER: {
                p = new Panel("LOADOUT", 2.5, 1.5, 13.5, 14.5, -1.0, CREAM);
                p.label(8, 13.6, "BULK LOADOUT");
                p.digits("car", 5.8, 12.1, 4.2, 0x40FF60, "CAR %d%%", 1);
                p.digits("carname", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("bin", 4.8, 8.6, 1.6, "SILO %", 100);
                p.sel("mode", 8.6, 8.8, "MODE", "MAN", "AUTO");
                p.lamp("lcar", 12.0, 10.0, Panel.GREEN, "CAR");
                p.lamp("lgate", 12.0, 8.2, Panel.AMBER, "CHUTE");
                p.push("start", 3.8, 5.0, Panel.GREEN, "LOAD");
                p.push("stop", 6.0, 5.0, Panel.RED, "STOP");
                p.lamp("lrun", 3.8, 2.9, Panel.GREEN, "LOADING");
                p.lamp("lfull", 6.0, 2.9, Panel.BLUE, "CAR FULL");
                p.push("reset", 8.4, 2.9, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 4.0, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case BOREHOLE_PUMP: {
                p = new Panel("BOREHOLE", 2.5, 1.5, 13.5, 14.5, -1.0, 0x4F7FB8);
                p.label(8, 13.6, "BOREHOLE PUMP");
                p.digits("flow", 5.8, 12.1, 4.2, 0x40C8FF, "%d mB/s", 1);
                p.digits("depth", 10.6, 12.1, 4.2, 0xFFB040, "%d m", 1);
                p.gauge("well", 3.8, 8.7, 1.35, "WELL %", 100);
                p.gauge("press", 6.9, 8.7, 1.35, "BAR", 10);
                p.sel("speed", 9.9, 8.9, "SPEED", "50%", "75%", "100%");
                p.sel("hoa", 12.6, 8.9, "H-O-A", "HAND", "OFF", "AUTO");
                p.lamp("lrun", 3.4, 6.3, Panel.GREEN, "RUN");
                p.lamp("llow", 5.6, 6.3, Panel.AMBER, "LOW WELL");
                p.lamp("ldry", 7.8, 6.3, Panel.RED, "DRY RUN").blink = true;
                p.lamp("lhigh", 10.0, 6.3, Panel.AMBER, "HI PRESS");
                p.lamp("lpower", 12.2, 6.3, Panel.GREEN, "POWER");
                p.push("start", 3.6, 4.0, Panel.GREEN, "START");
                p.push("stop", 5.8, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.0, 4.0, Panel.BLUE, "RESET");
                p.push("survey", 3.6, 2.0, Panel.YELLOW, "SURVEY");
                p.lamp("lfault", 5.8, 2.0, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case WATER_TOWER: {
                p = new Panel("WATER TOWER", 2.5, 1.5, 13.5, 14.5, -1.0, GREY);
                p.label(8, 13.6, "WATER TOWER");
                p.digits("stored", 5.8, 12.1, 4.2, 0x40C8FF, "%d L", 1);
                p.digits("outflow", 10.6, 12.1, 4.2, 0x40FF60, "%d L/s", 1);
                p.gauge("level", 3.8, 8.7, 1.35, "LEVEL %", 100);
                p.gauge("head", 6.9, 8.7, 1.35, "HEAD m", 8);
                p.sel("inlet", 9.9, 8.9, "INLET", "SHUT", "OPEN");
                p.sel("outlet", 12.6, 8.9, "OUTLET", "SHUT", "OPEN");
                p.lamp("lhi", 3.4, 6.3, Panel.BLUE, "HIGH");
                p.lamp("llo", 5.6, 6.3, Panel.AMBER, "LOW").blink = true;
                p.lamp("lover", 7.8, 6.3, Panel.RED, "OVERFLOW").blink = true;
                p.lamp("lfill", 10.0, 6.3, Panel.GREEN, "FILLING");
                p.lamp("lpower", 12.2, 6.3, Panel.GREEN, "POWER");
                p.push("silence", 3.6, 4.0, Panel.BLACK, "SILENCE");
                p.push("test", 5.8, 4.0, Panel.BLUE, "LAMP TEST");
                p.sel("mode", 8.6, 4.2, "FILL", "MAN", "AUTO");
                p.lamp("lalarm", 3.6, 2.0, Panel.RED, "ALARM").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case OVERHEAD_LOADER: {
                p = new Panel("LOADING SILO", 2.5, 1.5, 13.5, 14.5, -1.0, CREAM);
                p.label(8, 13.6, "LOADING SILO");
                p.digits("car", 5.8, 12.1, 4.2, 0x40FF60, "CAR %d%%", 1);
                p.digits("carname", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("bin", 3.8, 8.7, 1.35, "SILO %", 100);
                p.gauge("chuteg", 6.9, 8.7, 1.35, "CHUTE m", 12);
                p.sel("fill", 9.9, 8.9, "FILL TO", "50%", "75%", "100%");
                p.sel("mode", 12.6, 8.9, "MODE", "MAN", "AUTO");
                p.lamp("lstop", 3.4, 6.3, Panel.RED, "STOP");
                p.lamp("ladv", 5.6, 6.3, Panel.GREEN, "ADVANCE");
                p.lamp("lgate", 7.8, 6.3, Panel.AMBER, "GATE OPEN");
                p.lamp("lblock", 10.0, 6.3, Panel.RED, "CHUTE BLKD").blink = true;
                p.lamp("lcar", 12.2, 6.3, Panel.GREEN, "CAR");
                p.push("start", 3.6, 4.0, Panel.GREEN, "LOAD");
                p.push("stop", 5.8, 4.0, Panel.RED, "STOP");
                p.push("raise", 8.0, 4.0, Panel.YELLOW, "RAISE CHUTE");
                p.push("reset", 3.6, 2.0, Panel.BLUE, "RESET");
                p.lamp("lfault", 5.8, 2.0, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case CAR_DUMPER: {
                p = new Panel("CAR DUMPER", 2.5, 1.5, 13.5, 14.5, -1.0, CREAM);
                p.label(8, 13.6, "ROTARY CAR DUMPER");
                p.digits("angled", 5.8, 12.1, 4.2, 0x40FF60, "%d deg", 1);
                p.digits("ores", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("bin", 3.8, 8.7, 1.35, "HOPPER %", 100);
                p.gauge("load", 6.9, 8.7, 1.35, "DRIVE %", 150);
                p.sel("mode", 9.9, 8.9, "MODE", "MAN", "AUTO");
                p.lamp("lcar", 12.4, 9.6, Panel.GREEN, "CAR");
                p.lamp("lclamp", 12.4, 8.0, Panel.AMBER, "CLAMPED");
                p.lamp("lrot", 3.4, 6.3, Panel.AMBER, "ROTATING").blink = true;
                p.lamp("ldump", 5.6, 6.3, Panel.GREEN, "DUMPING");
                p.lamp("lempty", 7.8, 6.3, Panel.BLUE, "CAR EMPTY");
                p.lamp("lrun", 10.0, 6.3, Panel.GREEN, "RUN");
                p.push("start", 3.6, 4.0, Panel.GREEN, "CYCLE");
                p.push("stop", 5.8, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.0, 4.0, Panel.BLUE, "RESET");
                p.lamp("lfault", 3.6, 2.0, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case TANK_LOADER: case TANK_UNLOADER: {
                boolean in = this == TANK_LOADER;
                p = new Panel(in ? "TANK LOADING" : "TANK UNLOADING", 2.5, 1.5, 13.5, 14.5, -1.0, CREAM);
                p.label(8, 13.6, in ? "TANK CAR LOADING" : "TANK CAR UNLOADING");
                p.digits("car", 5.8, 12.1, 4.2, 0x40FF60, "CAR %d%%", 1);
                p.digits("carname", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("tankg", 3.8, 8.7, 1.35, in ? "SUPPLY %" : "RECEIVE %", 100);
                p.gauge("flowg", 6.9, 8.7, 1.35, "FLOW", 100);
                p.sel("fill", 9.9, 8.9, in ? "FILL TO" : "DRAIN TO", in ? "50%" : "50%", in ? "90%" : "10%", in ? "98%" : "0%");
                p.sel("mode", 12.6, 8.9, "MODE", "MAN", "AUTO");
                p.lamp("lcar", 3.4, 6.3, Panel.GREEN, "CAR");
                p.lamp("larm", 5.6, 6.3, Panel.AMBER, in ? "ARM DOWN" : "COUPLED");
                p.lamp("lflow", 7.8, 6.3, Panel.GREEN, "PUMPING");
                p.lamp("ldone", 10.0, 6.3, Panel.BLUE, in ? "FULL" : "EMPTY");
                p.lamp("lwrong", 12.2, 6.3, Panel.RED, "WRONG FLUID").blink = true;
                p.push("start", 3.6, 4.0, Panel.GREEN, in ? "LOAD" : "UNLOAD");
                p.push("stop", 5.8, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.0, 4.0, Panel.BLUE, "RESET");
                p.lamp("lfault", 3.6, 2.0, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case COKE_OVEN: case BLAST_FURNACE: case REFINERY: {
                String title = this == COKE_OVEN ? "COKE OVENS" : this == BLAST_FURNACE ? "BLAST FURNACE" : "REFINERY";
                p = new Panel(title, 2.5, 1.5, 13.5, 14.5, -1.0, DARK);
                p.label(8, 13.6, title);
                p.digits("temp", 5.8, 12.1, 4.2, 0xFF7030, "%d°C", 1);
                p.digits("ores", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("tempg", 3.8, 8.7, 1.35, "°C x100", 16);
                p.gauge("prog", 6.9, 8.7, 1.35, this == REFINERY ? "COLUMN %" : "BATCH %", 100);
                p.sel("setpoint", 9.9, 8.9, "HEAT", "50%", "75%", "100%");
                if (this == BLAST_FURNACE) p.sel("blast", 12.6, 8.9, "HOT BLAST", "LOW", "HIGH");
                else if (this == REFINERY) p.sel("reflux", 12.6, 8.9, "REFLUX", "LOW", "HIGH");
                else p.sel("doors", 12.6, 8.9, "DOORS", "SHUT", "OPEN");
                p.lamp("lheat", 3.4, 6.3, Panel.AMBER, "HEATING");
                p.lamp("lready", 5.6, 6.3, Panel.GREEN, "AT TEMP");
                p.lamp("lwait", 7.8, 6.3, Panel.AMBER, "NO CHARGE").blink = true;
                p.lamp("lfull", 10.0, 6.3, Panel.RED, "OUTPUT FULL");
                p.lamp("lpower", 12.2, 6.3, Panel.GREEN, "POWER");
                p.push("start", 3.6, 4.0, Panel.GREEN, "START");
                p.push("stop", 5.8, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.0, 4.0, Panel.BLUE, "RESET");
                p.lamp("lrun", 3.6, 2.0, Panel.GREEN, "RUN");
                p.lamp("lfault", 5.8, 2.0, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case ARC_FURNACE: {
                p = new Panel("ARC FURNACE", 2.5, 1.5, 13.5, 14.5, -1.0, DARK);
                p.label(8, 13.6, "ELECTRIC ARC FURNACE");
                p.digits("temp", 5.8, 12.1, 4.2, 0xFF7030, "%d°C", 1);
                p.digits("mw", 10.6, 12.1, 4.2, 0x60C0FF, "%d kW", 1);
                p.gauge("tempg", 3.8, 8.7, 1.35, "°C x100", 20);
                p.gauge("prog", 6.9, 8.7, 1.35, "HEAT %", 100);
                p.sel("tap", 9.6, 8.9, "POWER TAP", "LOW", "MED", "HIGH");
                p.sel("lance", 12.4, 8.9, "O2 LANCE", "OFF", "ON");
                p.lamp("larc", 3.4, 6.3, Panel.AMBER, "ARC").blink = true;
                p.lamp("lhot", 5.6, 6.3, Panel.RED, "MOLTEN");
                p.lamp("lelec", 7.8, 6.3, Panel.AMBER, "NO ELECTRODE").blink = true;
                p.lamp("lwait", 10.0, 6.3, Panel.AMBER, "NO CHARGE");
                p.lamp("lvolt", 12.2, 6.3, Panel.RED, "SUPPLY").blink = true;
                p.push("start", 3.6, 4.0, Panel.GREEN, "ARC ON");
                p.push("stop", 6.0, 4.0, Panel.RED, "ARC OFF");
                p.push("reset", 8.4, 4.0, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.6, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case ELECTROLYSER: {
                p = new Panel("ELECTROLYSIS", 2.5, 1.5, 13.5, 14.5, -1.0, GREY);
                p.label(8, 13.6, "ELECTROLYSIS PLANT");
                p.digits("h2", 5.8, 12.1, 4.2, 0xDCEBFF, "H2 %d", 1);
                p.digits("o2", 10.6, 12.1, 4.2, 0x80D0FF, "O2 %d", 1);
                p.gauge("amps", 3.8, 8.7, 1.35, "kA", 10);
                p.gauge("waterg", 6.9, 8.7, 1.35, "WATER %", 100);
                p.sel("current", 10.6, 8.9, "CURRENT", "LOW", "MED", "HIGH");
                p.lamp("lrun", 3.4, 6.3, Panel.GREEN, "CELLS ON");
                p.lamp("lwait", 5.6, 6.3, Panel.AMBER, "NO WATER").blink = true;
                p.lamp("lfull", 7.8, 6.3, Panel.RED, "GAS FULL");
                p.lamp("lac", 10.0, 6.3, Panel.AMBER, "NOT DC").blink = true;
                p.lamp("lvolt", 12.2, 6.3, Panel.RED, "SUPPLY").blink = true;
                p.push("start", 3.6, 4.0, Panel.GREEN, "START");
                p.push("stop", 6.0, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.4, 4.0, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.6, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case WELDING_CELL: {
                p = new Panel("WELDING", 2.5, 1.5, 13.5, 14.5, -1.0, GREY);
                p.label(8, 13.6, "ROBOTIC WELDING CELL");
                p.digits("rate", 5.8, 12.1, 4.2, 0x40FF60, "%d/min", 1);
                p.digits("prog_txt", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("amps", 3.8, 8.7, 1.35, "WELD A x10", 40);
                p.gauge("prog", 6.9, 8.7, 1.35, "CYCLE %", 100);
                p.sel("program", 10.6, 8.9, "PROGRAM", "PLATE", "FRAME", "SHAFT");
                p.lamp("lrun", 3.4, 6.3, Panel.GREEN, "WELDING");
                p.lamp("lwait", 5.6, 6.3, Panel.AMBER, "NO STEEL").blink = true;
                p.lamp("lfull", 7.8, 6.3, Panel.RED, "OUTPUT FULL");
                p.lamp("lgate", 10.0, 6.3, Panel.GREEN, "GUARD SHUT");
                p.lamp("lvolt", 12.2, 6.3, Panel.RED, "SUPPLY").blink = true;
                p.push("start", 3.6, 4.0, Panel.GREEN, "CYCLE START");
                p.push("stop", 6.0, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.4, 4.0, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.6, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case WINDMILL: {
                p = new Panel("WINDMILL", 2.5, 1.5, 13.5, 14.5, -1.0, CREAM);
                p.label(8, 13.6, "GRAIN WINDMILL");
                p.digits("rate", 5.8, 12.1, 4.2, 0x40FF60, "%d/min", 1);
                p.digits("wind", 10.6, 12.1, 4.2, 0x60C0FF, "%d m/s", 1);
                p.gauge("windg", 3.8, 8.7, 1.35, "WIND m/s", 30);
                p.gauge("prog", 6.9, 8.7, 1.35, "STONES %", 100);
                p.sel("sails", 10.6, 8.9, "SAILS", "FULL", "REEFED", "FURLED");
                p.lamp("lrun", 3.4, 6.3, Panel.GREEN, "TURNING");
                p.lamp("lcalm", 5.6, 6.3, Panel.BLUE, "CALM");
                p.lamp("lstorm", 7.8, 6.3, Panel.RED, "STORM").blink = true;
                p.lamp("lwait", 10.0, 6.3, Panel.AMBER, "NO GRAIN").blink = true;
                p.lamp("lfull", 12.2, 6.3, Panel.RED, "BIN FULL");
                p.push("start", 3.6, 4.0, Panel.GREEN, "RELEASE");
                p.push("stop", 6.0, 4.0, Panel.RED, "BRAKE");
                p.push("reset", 8.4, 4.0, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.6, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case ROLLING_MILL: case WIRE_DRAWER: case OIL_PRESS: case SAWMILL: case CRATE_PACKER: {
                String title = this == ROLLING_MILL ? "ROLLING MILL" : this == WIRE_DRAWER ? "WIRE DRAWING" : this == OIL_PRESS ? "OIL PRESS" : this == SAWMILL ? "SAWMILL" : "CRATE PACKER";
                p = new Panel(title, 2.5, 1.5, 13.5, 14.5, -1.0, this == SAWMILL ? CREAM : GREY);
                p.label(8, 13.6, title);
                p.digits("rate", 5.8, 12.1, 4.2, 0x40FF60, "%d/min", 1);
                p.digits("ores", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("load", 3.8, 8.7, 1.35, "MOTOR %", 150);
                p.gauge("prog", 6.9, 8.7, 1.35, "CYCLE %", 100);
                if (this == ROLLING_MILL) p.sel("gap", 9.9, 8.9, "PASS", "FINE", "STD", "RAIL");
                else if (this == WIRE_DRAWER) p.sel("die", 9.9, 8.9, "DIE", "FINE", "HEAVY");
                else if (this == OIL_PRESS) p.sel("feed", 9.9, 8.9, "FEED", "LOW", "MED", "HIGH");
                else if (this == SAWMILL) p.sel("feed", 9.9, 8.9, "FEED", "LOW", "MED", "HIGH");
                else p.sel("mode", 9.9, 8.9, "MODE", "PACK", "UNPACK");
                p.sel("speed", 12.6, 8.9, "SPEED", "SLOW", "FAST");
                p.lamp("lrun", 3.4, 6.3, Panel.GREEN, "RUN");
                p.lamp("lwait", 5.6, 6.3, Panel.AMBER, "NO FEED").blink = true;
                p.lamp("lfull", 7.8, 6.3, Panel.RED, "OUTPUT FULL");
                p.lamp("lpower", 10.0, 6.3, Panel.GREEN, "POWER");
                p.lamp("ljam", 12.2, 6.3, Panel.AMBER, "JAM");
                p.push("start", 3.6, 4.0, Panel.GREEN, "START");
                p.push("stop", 5.8, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.0, 4.0, Panel.BLUE, "RESET");
                p.lamp("lfault", 3.6, 2.0, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            case TIE_PLANT: {
                p = new Panel("TIE PLANT", 2.5, 1.5, 13.5, 14.5, -1.0, DARK);
                p.label(8, 13.6, "SLEEPER TREATMENT");
                p.digits("rate", 5.8, 12.1, 4.2, 0x40FF60, "%d/min", 1);
                p.digits("stagetxt", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("bar", 3.8, 8.7, 1.35, "BAR", 14);
                p.gauge("creo", 6.9, 8.7, 1.35, "CREOSOTE %", 100);
                p.sel("process", 9.9, 8.9, "PROCESS", "FULL", "EMPTY");
                p.sel("charge", 12.6, 8.9, "CHARGE", "4", "8");
                p.lamp("lvac", 3.4, 6.3, Panel.BLUE, "VACUUM");
                p.lamp("lflood", 5.6, 6.3, Panel.AMBER, "FLOOD");
                p.lamp("lpress", 7.8, 6.3, Panel.RED, "PRESSURE").blink = true;
                p.lamp("ldrain", 10.0, 6.3, Panel.GREEN, "DRAIN");
                p.lamp("ldoor", 12.2, 6.3, Panel.AMBER, "DOOR LOCKED");
                p.push("start", 3.6, 4.0, Panel.GREEN, "START");
                p.push("stop", 5.8, 4.0, Panel.RED, "STOP");
                p.push("reset", 8.0, 4.0, Panel.BLUE, "RESET");
                p.lamp("lwait", 3.6, 2.0, Panel.AMBER, "NO TIMBER").blink = true;
                p.lamp("lcreo", 5.8, 2.0, Panel.AMBER, "NO CREOSOTE").blink = true;
                p.lamp("lfault", 8.0, 2.0, Panel.RED, "FAULT").blink = true;
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 3.3, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
            default: {   // UNLOADER
                p = new Panel("UNLOADER", 2.5, 1.5, 13.5, 14.5, -1.0, CREAM);
                p.label(8, 13.6, "UNLOADING PIT");
                p.digits("car", 5.8, 12.1, 4.2, 0x40FF60, "CAR %d%%", 1);
                p.digits("carname", 10.6, 12.1, 4.2, 0xFFB040, "%s", 1);
                p.gauge("bin", 4.8, 8.6, 1.6, "PIT %", 100);
                p.sel("mode", 8.6, 8.8, "MODE", "MAN", "AUTO");
                p.lamp("lcar", 12.0, 10.0, Panel.GREEN, "CAR");
                p.lamp("lgate", 12.0, 8.2, Panel.AMBER, "GRATE");
                p.push("start", 3.8, 5.0, Panel.GREEN, "UNLOAD");
                p.push("stop", 6.0, 5.0, Panel.RED, "STOP");
                p.lamp("lrun", 3.8, 2.9, Panel.GREEN, "RUNNING");
                p.lamp("lempty", 6.0, 2.9, Panel.BLUE, "CAR EMPTY");
                p.push("reset", 8.4, 2.9, Panel.BLUE, "RESET");
                p.add(new Panel.C("estop", Panel.T.MUSHROOM, 11.6, 4.0, 2.2, 2.2, Panel.RED, "E-STOP"));
                break;
            }
        }
        return p;
    }
}
