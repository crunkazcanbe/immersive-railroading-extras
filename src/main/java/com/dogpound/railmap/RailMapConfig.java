package com.dogpound.railmap;

import net.minecraftforge.common.config.Config;

/** Forge-managed config (config/railmap.cfg). All values are read on the server at scan time. */
@Config(modid = RailMap.MODID)
public final class RailMapConfig {
    @Config.Comment("Max IR track pieces one board will walk. Each piece costs a few tile lookups; " +
            "2000 covers a large network without hurting an OOM-prone client.")
    @Config.RangeInt(min = 50, max = 20000)
    public static int nodeBudget = 2000;

    @Config.Comment("Radius (blocks) around the board in which to look for track to start walking from.")
    @Config.RangeInt(min = 4, max = 256)
    public static int seedRadius = 48;

    @Config.Comment("Ticks between automatic rescans while a player is within 48 blocks of the board.")
    @Config.RangeInt(min = 20, max = 6000)
    public static int rescanIntervalTicks = 200;

    @Config.Comment("A signal further than this (blocks) from any walked track piece is ignored.")
    @Config.RangeInt(min = 1, max = 32)
    public static int signalTrackDistance = 6;

    @Config.Comment("How far a signal will follow the track looking for the end of its block, in blocks. " +
            "A block normally ends at an insulated joint or the next facing signal; this is the safety stop.")
    @Config.RangeInt(min = 16, max = 2048)
    public static int maxBlockLength = 400;

    @Config.Comment("Three-block signalling: two blocks clear shows flashing yellow (Advance Approach) " +
            "instead of green. Turn off for simple two-block territory.")
    public static boolean threeBlockSignalling = true;

    @Config.Comment("Draw RailMap's own signals on the board and wall screens.")
    public static boolean showOwnSignals = true;

    // ---- driverless trains, routes, protection ------------------------------------------

    @Config.Comment("How far (blocks of track) a route search may go looking for a station or signal.")
    @Config.RangeInt(min = 100, max = 20000)
    public static int routeSearchBlocks = 4000;

    @Config.Comment("Braking a driverless train plans for, m/s². Lower = gentler stops that start earlier.")
    @Config.RangeDouble(min = 0.1, max = 3.0)
    public static double autopilotBraking = 0.45;

    @Config.Comment("Seconds a driverless train allows for air-brake pressure to build before it starts slowing")
    @Config.RangeDouble(min = 0, max = 10)
    public static double autopilotBrakeLagSeconds = 2.5;

    @Config.Comment("Orders: the longest a 'Full load' stop waits for every car to fill, in minutes")
    @Config.RangeInt(min = 1, max = 120)
    public static int fullLoadMaxMinutes = 10;

    @Config.Comment("Profit: money per item (or bucket) delivered, before the distance bonus")
    @Config.RangeDouble(min = 0, max = 100)
    public static double profitPerItem = 1.0;

    @Config.Comment("Profit: every this many blocks between loading and unloading adds one more times the base pay")
    @Config.RangeInt(min = 1, max = 10000)
    public static int profitBlocksPerCoin = 100;

    @Config.Comment("Default top speed for a new driverless train, km/h (you can change it per train on the board).")
    @Config.RangeInt(min = 5, max = 250)
    public static int autopilotDefaultKmh = 60;

    @Config.Comment("Train protection: brake any train that would run a Stop signal or overspeed a sign.")
    public static boolean trainProtection = true;

    @Config.Comment("Apply train protection to trains a player is driving, not just driverless ones.")
    public static boolean protectManualTrains = true;

    @Config.Comment("Moving block (CBTC): metres a train keeps back from the tail of whatever is in front of it.")
    @Config.RangeInt(min = 3, max = 500)
    public static int movingBlockMarginM = 20;

    @Config.Comment("Moving block protects every train a player drives from running into the train ahead (rear-end protection).")
    public static boolean movingBlockManualTrains = true;

    @Config.Comment("Below this (km/h) a player-driven train may close right up on another one, so you can still couple.")
    @Config.RangeInt(min = 0, max = 40)
    public static int movingBlockShuntKmh = 12;

    @Config.Comment("Show the driver's cab display (speed, permitted speed, distance-to-go) to riders of protected and driverless trains.")
    public static boolean cabDisplay = true;

    @Config.Comment("Working locomotive cab controls: throttle notches, reverser, automatic / independent / dynamic brake handles, horn, bell, lights, engine, isolate, sander, pantograph, alerter and radio handset, kept in IR's control groups (CAB_*) and mirrored to the real systems.")
    public static boolean cabControls = true;

    @Config.Comment("Cab desk drawn in the cab: 0 off, 1 auto (a full desk in locos whose model has no throttle / brake / reverser controls, and a panel with the extra switches, lamps, gauges and screen in the others), 2 the full desk in every loco.")
    @Config.RangeInt(min = 0, max = 2)
    public static int cabDeskMode = 1;

    @Config.Comment("Cab desk: move it up (+) or down (-), metres, if it sits wrong in a loco.")
    @Config.RangeDouble(min = -3, max = 3)
    public static double cabDeskYOffset = 0;

    @Config.Comment("Cab desk: move it back toward the driver (+) or forward (-), metres.")
    @Config.RangeDouble(min = -10, max = 10)
    public static double cabDeskZOffset = 0;

    @Config.Comment("Cab desk: put it at the other end of the passenger area (for locos whose cab is at the rear of the model).")
    public static boolean cabDeskFlip = false;

    @Config.Comment("How far over a limit (km/h) a driven train may go before protection brakes it.")
    @Config.RangeInt(min = 0, max = 50)
    public static int protectionMarginKmh = 8;

    // ---- tickets -------------------------------------------------------------------------

    @Config.Comment("Item a ticket costs, as modid:name (empty = tickets are free).")
    public static String fareItem = "minecraft:iron_nugget";

    @Config.Comment("Fare: one fare item per this many blocks between stations (minimum one).")
    @Config.RangeInt(min = 10, max = 100000)
    public static int fareBlocksPerItem = 200;

    @Config.Comment("A ticket machine belongs to the nearest named station within this many blocks.")
    @Config.RangeInt(min = 4, max = 256)
    public static int stationReach = 48;

    // ---- defect detectors ----------------------------------------------------------------

    @Config.Comment("How far (blocks) a defect detector's radio announcement carries.")
    @Config.RangeInt(min = 16, max = 1024)
    public static int detectorRadioRange = 160;

    // ---- maintenance and wear ------------------------------------------------------------

    @Config.Comment("Rolling stock wears out with use (wheels, brakes, engine, bearings, electrics) and needs a Maintenance Depot.")
    public static boolean wearEnabled = true;

    @Config.Comment("How fast things wear. 1.0 = wheels last about 200 km; 2.0 = twice as fast.")
    @Config.RangeDouble(min = 0.05, max = 20)
    public static double wearRate = 1.0;

    @Config.Comment("Worn-out parts limit the train (worn engine = half throttle, worn brakes/wheels/bearings = speed cap). Off = warnings only.")
    public static boolean wearEffects = true;

    @Config.Comment("Speed cap (km/h) for a train whose brakes, wheels or bearings are worn out.")
    @Config.RangeInt(min = 5, max = 200)
    public static int wornSpeedCapKmh = 40;

    @Config.Comment("Percent of wear per second a Maintenance Depot repairs on each part of a stopped train beside it.")
    @Config.RangeDouble(min = 0.1, max = 100)
    public static double depotRepairPerSecond = 4.0;

    @Config.Comment("Blocks around a Maintenance Depot in which stopped rolling stock gets serviced.")
    @Config.RangeInt(min = 2, max = 32)
    public static int depotReach = 8;

    // ---- electrification and batteries ----------------------------------------------------

    @Config.Comment("Electric locomotives under live contact wire (or beside a live third rail) run without fuel; off the wire they run on their battery.")
    public static boolean electrification = true;

    @Config.Comment("A locomotive counts as electric when its name contains any of these (lower-case). Override per loco with /irextras electric on|off.")
    public static String[] electricNames = {"electric", "emu", "metro", "subway", "tram", "gg1", "ae ", "acela", "tgv", "ice ", "shinkansen", "bullet", "pantograph", "battery", "e44", "e60", "class 9", "eurostar", "sprinter", "monorail", "maglev", "pride rail"};

    @Config.Comment("Substations must hold Forge Energy (FE) to power the wire. Off = free power (the breaker still works). Pride default: on, trains run on RF.")
    public static boolean powerNeedsEnergy = true;

    @Config.Comment("FE a running electric loco draws from its substation each second, at full throttle.")
    @Config.RangeInt(min = 0, max = 100000)
    public static int fePerSecond = 400;

    @Config.Comment("How far (blocks) a substation feeds its contact wire and third rail.")
    @Config.RangeInt(min = 16, max = 4096)
    public static int substationRange = 512;

    @Config.Comment("Thunderstorms can trip substations exposed to the sky (right-click to reset).")
    public static boolean stormOutages = true;

    @Config.Comment("Battery size: millibuckets of fuel the battery can stand in for when off the wire (bigger = longer range).")
    @Config.RangeInt(min = 0, max = 1000000)
    public static int batteryFuelMb = 24000;

    @Config.Comment("Battery % per second a Charging Station (or live wire) puts back.")
    @Config.RangeDouble(min = 0.1, max = 100)
    public static double chargePerSecond = 2.0;

    // ---- overhead line, pantographs, energy dispatch (her ChatGPT list #31-33) --------------------------------------

    @Config.Comment("OHLE sectioning: contact wire splits into electrical sections at Section Insulators / Neutral Sections; a Track Feeder within 4 blocks of the wire feeds its section. Lines with neither keep the old radius feeding.")
    public static boolean ohleSectioning = true;
    @Config.Comment("Feeder circuit breakers auto-reclose once after an OHLE fault, after first opening AUTO isolators round the faulted section (fault isolation and restoration)")
    public static boolean ohleAutoReclose = true;
    @Config.Comment("Pantograph + contact-wire mechanics: raise/lower sequence, contact force, wire height/stagger/tension/ice, arcing, wire and carbon wear, maintenance alerts. Off = simple compatibility mode (wire overhead = power)")
    public static boolean ohleContactDetail = true;
    @Config.Comment("HARSH: a pantograph far off the wire / at speed through a hard spot can tear the wire down (dewirement: earth fault, section trips, repair the wire)")
    public static boolean ohleDewirement = false;
    @Config.Comment("HARSH: contact wire worn to its renewal limit snaps (otherwise it only arcs and alarms)")
    public static boolean ohleWireBreaks = false;
    @Config.Comment("Pantograph passes a contact wire stands before it reaches its renewal limit (normal contact force, no arcing)")
    @Config.RangeInt(min = 500, max = 10000000)
    public static int ohleWirePasses = 200000;
    @Config.Comment("Default pantograph static contact force, N (EN 50367: 70 N AC). Per loco: /irextras ohle pan force <N>")
    @Config.RangeInt(min = 20, max = 300)
    public static int ohlePanForceN = 70;
    @Config.Comment("Highest contact wire a pantograph reaches, metres above the rail (wire block 7 above the track = 7.1 m)")
    @Config.RangeDouble(min = 5.0, max = 9.0)
    public static double ohlePanReachM = 7.2;
    @Config.Comment("Energy-aware dispatch: driverless trains wait (up to the limits below) so starts on one feeder don't coincide, to catch a braking train's regen, and for a battery charge before leaving a charger")
    public static boolean energyDispatch = true;
    @Config.Comment("Longest a departure is held for a busy feeder / regen match, seconds")
    @Config.RangeInt(min = 0, max = 300)
    public static int energyHoldSeconds = 30;
    @Config.Comment("Electricity tariff, coins per MWh: off-peak / shoulder / peak (peak 07-10 and 17-20 game clock, shoulder 10-17)")
    public static int[] energyTariff = {40, 80, 160};

    // ---- weather (her ChatGPT list #63-66) ----------------------------------------------------------------------

    @Config.Comment("Railway weather: snow on track, ice on the wire, flooding, rail temperature, fog/visibility, worked out per 64x64 cell where trains run. Weather2 is used when installed")
    public static boolean railWeather = true;
    @Config.Comment("Weather speed restrictions (snow, flood, heat, fog) apply to driverless trains and train protection")
    public static boolean weatherRestrictions = true;
    @Config.Comment("Stress-free temperature of continuous welded rail, °C (UK 27). Heat restrictions start 16 °C above it")
    @Config.RangeInt(min = 10, max = 40)
    public static int railStressFreeC = 27;
    @Config.Comment("HARSH: very hot rails can buckle (line closed until repaired at a Rail Temperature Monitor)")
    public static boolean railBuckling = false;
    @Config.Comment("Frozen / heat-bound points can fail to move (Points Heater cabinets prevent the frozen kind)")
    public static boolean frozenPoints = true;
    @Config.Comment("Snow-clearing gangs working through the snow work orders, by track priority")
    @Config.RangeInt(min = 0, max = 10)
    public static int snowCrews = 2;

    // ---- freight contracts -----------------------------------------------------------------

    @Config.Comment("Freight contracts: industries post jobs (move N goods from the industry that makes them to the one that needs them), accepted at a Freight Office, paid in Pride coins.")
    public static boolean freightContracts = true;
    @Config.Comment("Minutes between new contracts being posted")
    public static int contractEveryMinutes = 4;
    @Config.Comment("Most open (not yet accepted) contracts at once")
    public static int contractMaxOpen = 8;
    @Config.Comment("In-game hours an open contract waits to be accepted before it is withdrawn")
    public static int contractOpenHours = 48;
    @Config.Comment("In-game hours after the deadline before an unfinished contract fails")
    public static int contractGraceHours = 48;
    @Config.Comment("Multiplier on contract pay")
    public static double contractPayScale = 1.0;

    // ---- industry integration (her list section 13: Pride Industry and any mod's factories) -----------------------
    @Config.Comment("Automatic freight orders: a factory, depot store or fuel point that runs low posts an order contract to fetch it from a plant that makes it")
    public static boolean industryOrders = true;
    @Config.Comment("Wagon cargo rules: hoppers take bulk, box cars packaged goods, flat cars long loads and containers, tank cars only fluids; dense ore and metal fill a car only to its weight limit. Cars whose type can't be told take anything.")
    public static boolean cargoRules = false;   // off by default: existing loaders in the world keep working (turn on for realism, or a HARDCORE profile)
    @Config.Comment("Under-voltage / under-frequency load shedding: when a network sags below 92 % or its frequency falls 1.5 %, factories on it are dropped first so traction keeps the generation")
    public static boolean industryLoadShedding = true;
    @Config.Comment("Power-quality trips: sensitive factory plant (welding robots, electrolysis cells, assembly lines) trips when the supply's voltage, frequency, harmonics or unbalance go past its limits")
    public static boolean industryPowerQuality = true;

    // ---- railway economy (companies, markets, passengers) --------------------------------------------------------

    @Config.Comment("Railway economy: companies, commodity prices, simulated passengers and fares")
    public static boolean railwayEconomy = true;
    @Config.Comment("Multiplier on how many simulated passengers stations generate")
    @Config.RangeDouble(min = 0, max = 20)
    public static double passengerDemand = 1.0;

    // ---- 12 V wiring ------------------------------------------------------------------------

    @Config.Comment("Railway gear (signals, crossings, track circuits, station and lineside equipment) needs 12 V DC instrument power: lay the 12 V power wire beside it. With none it fails safe (signals dark = stop, crossings down, track circuits occupied). Gear placed before this update keeps working until it first gets 12 V.")
    public static boolean gearNeedsPower = true;

    // ---- electrical safety (permits to work) -------------------------------------------------

    @Config.Comment("Permit to work: repairs, overhauls, settings changes and off-circuit tap moves on grid equipment at 1 kV and above need an ISSUED permit (isolated, earthed, proven dead, locked off with the padlock keys in a Permit Board's key safe). Working on it live zaps you.")
    public static boolean permitToWork = true;

    @Config.Comment("Opening a high-voltage machine cabinet's door while it is energised shocks you (the HV door alarm is raised either way). Off by default.")
    public static boolean doorShock = false;

    // ---- disruption recovery --------------------------------------------------------------

    @Config.Comment("Disruption recovery: a wreck or a train stranded without power blocks its stretch of line; driverless trains reroute around it or hold at the last signal before it, a recovery crew clears it, and held trains restart in order.")
    public static boolean disruptionRecovery = true;

    @Config.Comment("Seconds before the recovery crew reaches a wreck (0 = no crew: players clear wrecks with the Breakdown Crane and Rerailing Frogs).")
    @Config.RangeInt(min = 0, max = 3600)
    public static int recoveryCrewSeconds = 60;

    @Config.Comment("Seconds the crew works on each wrecked car (standing it up, rerailing or cutting it up).")
    @Config.RangeInt(min = 1, max = 600)
    public static int recoveryWorkSeconds = 20;

    @Config.Comment("Seconds between held trains being released once the line is clear (so they don't all set off together).")
    @Config.RangeInt(min = 0, max = 300)
    public static int resumeGapSeconds = 10;

    @Config.Comment("A driverless train stopped this many seconds with no power counts as a failed train blocking the line.")
    @Config.RangeInt(min = 5, max = 600)
    public static int failedTrainSeconds = 15;

    private RailMapConfig() {}
}
