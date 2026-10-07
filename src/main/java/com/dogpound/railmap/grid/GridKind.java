package com.dogpound.railmap.grid;

/** Every piece of the Pride Rail power grid. A train only gets power through a complete, correctly built chain. */
public enum GridKind {
    INTAKE("grid_intake", "Grid Intake", "Takes RF / FE from any mod (cables, generators, reactors) into the railway grid", 4_000_000),
    TRANSFORMER("grid_transformer", "Power Transformer", "Steps grid power down - every railway network needs one", 0),
    RECTIFIER("grid_rectifier", "Traction Rectifier", "Turns it into DC for monorail beams, maglev guideways and third rail", 0),
    TRACTION("grid_traction", "Traction Transformer", "25 kV single-phase AC for overhead contact wire", 0),
    INVERTER("grid_inverter", "Inverter", "DC in at the back (battery, solar, 600-1500 V DC line), clean AC out of the other faces", 0),
    MOTOR("grid_motor", "Electric Motor", "3-phase induction motor with its starter. START it and its shaft (the back) drives any mod's machine. A direct start pulls ~6x current - star-delta, soft-starter or VFD in its settings", 0),
    DCDC("grid_dcdc", "DC-DC Converter", "Steps one DC voltage to another: 48 V up to 750 V, 1500 V down to 24 V…", 0),
    BREAKER("grid_breaker", "Circuit Breaker", "Switches a section on / off; trips on overload or storms - right-click to close / reset", 0),
    FEEDER("grid_feeder", "Track Feeder", "Clamps onto the line and energises beam / wire / third rail. Wire ANY mod's cable or wire connector straight into it, or run it off the railway grid", 1_000_000),
    METER("grid_meter", "Energy Meter", "Shows the network's load and energy used; comparator output", 0),
    BATTERY("grid_battery", "Battery Storage", "Stores energy for when the intake runs dry; recharges from surplus", 10_000_000),
    SCADA("grid_scada", "Grid Control Panel", "Overview of every network: what is live, loads, breakers, alarms", 0),
    CABLE("grid_cable", "Feeder Cable", "Connects grid machines; works across unloaded chunks", 0),
    // ---- power plants (requested feature) ----
    DIESEL("grid_diesel", "Diesel Generator", "Burns diesel, biodiesel, oil or ethanol piped in from any mod. START / STOP / power buttons on its panel", 2_000_000),
    TURBINE("grid_turbine", "Steam Turbine", "Turns steam from any mod's boilers into grid power. START / STOP / power buttons on its panel", 2_000_000),
    SOLAR("grid_solar", "Solar Array", "Free power in daylight under open sky (less when it rains)", 400_000),
    WIND("grid_wind", "Wind Turbine", "Stronger the higher it stands, and in storms", 400_000),
    // ---- realism parts (requested feature) ----
    CAPACITOR("grid_capacitor", "Capacitor Bank", "Smooths rectifier DC (no bank = rough power, trains get ~70%) and corrects AC power factor (cuts losses)", 0),
    EARTHING("grid_earthing", "Earthing Switch", "Every network needs an earth: without one protection can't see faults and breakers refuse to close", 0),
    RELAY("grid_relay", "Protection Relay", "Place one touching each breaker: a breaker with no relay is blind and will not close", 0),
    ARRESTER("grid_arrester", "Surge Arrester", "Sends lightning to earth: without one, thunderstorms flash over and trip the network", 0),
    ISOLATOR("grid_isolator", "Disconnector", "Manual isolating switch for maintenance. NOT for breaking load: open it while current flows and it arcs! Open the breaker first", 0);

    public final String id, label, tip;
    public final int capacity;

    GridKind(String id, String label, String tip, int capacity) {
        this.id = id; this.label = label; this.tip = tip; this.capacity = capacity;
    }

    public boolean machine() { return this != CABLE; }

    /** makes power for the grid (and hands it to other mods' cables) */
    public boolean generator() { return this == DIESEL || this == TURBINE || this == SOLAR || this == WIND; }

    /** puts energy into a network: the intake and every generator */
    public boolean source() { return this == INTAKE || generator(); }

    /** a two-sided machine: back = primary network, other faces = secondary network */
    public boolean converter() { return this == TRANSFORMER || this == TRACTION || this == RECTIFIER || this == INVERTER || this == DCDC; }

    /** power electronics: an output breaker (dcOn) cuts the secondary off; a DC primary */
    public boolean electronic() { return this == RECTIFIER || this == INVERTER || this == DCDC; }

    /** draws from the grid (counted as load by the electrical solve) */
    public boolean load() { return this == FEEDER || this == MOTOR; }

    /** has START / STOP / power buttons on its front panel */
    public boolean buttons() { return this == DIESEL || this == TURBINE; }
}
