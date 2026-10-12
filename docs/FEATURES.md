# IR Extras: every feature

The complete plain-language list of what Immersive Railroading Extras adds (0.7.0-beta).

**How to read it:** a bold name is a block or item you can hold (names as the game shows them); entries marked
*(system)* are things the mod does by itself, with no block. "12 V" = low-voltage control power through Instrument
Wire; "Mains" = real grid power (120-480 V AC) through a Feeder Cable. Sneak + right-click with the Signal Wrench opens
the Settings Console on almost every block, and every button, lamp, gauge, key and lever drawn on a model really works.

## 1. Control Center, Maps + Driverless Trains

- **Control Center Console**: a big 3-wide, 2-tall dispatcher desk. Huge live map screen on top; the desk is covered in working buttons: pick a train, HOLD / GO, horn, send home, E-STOP ALL, screen page selector, zoom, control key, CBTC key, ACK, lamp test. Right-click the screen for the full Control Center. Needs mains (120/208/240 V AC) for the computer + 12 V for the buttons.
- **Control Center** *(system)*: 30 tabs: Map, Trains (live sortable table), Lines (lines + orders), Stops, Signals, Power, Protect (relays, breakers, UPS, black start), Wired (every 12 V machine with its real buttons), Freight, Company, Market, Pax, Alarms, Safety, Ops, OLE/Wx, Track, Rewind (replay), Depot, Assets, Money, API, Plan, World, Ctrl, Stn, CCTV, Stats, Log, Setup.
- **Rail Display Panel**: wall screen tile. Put several side by side and they join into one big live map screen.
- **Rail Map**: handheld map of the whole network with live trains.
- **Web map** *(system)*: Dynmap layers (track, signals, live trains) plus a new live train map web page for the Pride website (read-only, off until you switch it on).
- **Autopilot (driverless trains)** *(system)*: trains drive their own line, obey signals and speed limits, stop at stations, answer tickets.
- **Dispatcher** *(system)*: decides each train's next stop and matches passengers (tickets) to trains.
- **Interlocking** *(system)*: routes lock their points and signals so two routes can never cross.
- **Train protection (like PTC)** *(system)*: brakes a train that passes a red or goes too fast.
- **Moving-block signalling (CBTC)** *(system)*: per line, trains run on a live "limit of authority" from the train ahead instead of fixed blocks; cab display shows target speed + distance to go.
- **Timetables + scheduler** *(system)*: repeating departures and time-space charts.
- **OpenTTD-style orders per line** *(system)*: Stop, Full load, Unload all, Load only, Go via, Wait 60 s. Income by cargo x distance.
- **Disruption recovery** *(system)*: a wreck or dead train blocks its stretch; routes go around it, trains are held at the last signal, crews clear it, "+N min late" shows on boards.
- **Loco Changeover Cabinet**: driverless trains change engines here (electric to diesel at the end of the wires); a standby loco comes on, brake test. 12 V.
- **Banking Board (Bank Engine Control)**: a helper loco pushes heavy trains up a hill and drops off at the top. 12 V.
- **Automatic Coupling Point**: joins and splits driverless units at the platform with safety checks. 12 V + 400 V air compressor.
- **Automatic Turnback Panel**: trains reversing at a terminus change ends properly (crew walk, cab change, brake test). 12 V.
- Diamond crossings + single-line sections in the interlocking (conflict protection).
- **Route conflict visualizer** *(system)*: shows exactly what blocks a route request.
- **Energy-aware dispatch** *(system)*: departures timed to spread electric load.

## 2. Signals + Signal Gantries

- **Color Light Signal**: modern coloured lights. All signals: 10 aspects, number plate, fixed or auto mode, redstone in/out, live status. 12 V control.
- **Searchlight Signal**: one lamp that changes colour.
- **Position Light Signal (PRR)**: rows of lamps (Pennsylvania style).
- **Color Position Light (B&O)**: coloured lamp pairs (Baltimore & Ohio style).
- **Dwarf Signal**: small ground signal for shunting.
- **Semaphore Signal**: moving arm signal.
- **Signal Bridge**: a gantry with two legs that span the tracks by themselves.
- **Gantry Leg**: placed automatically with a gantry; break the gantry to remove.
- **Gantry Tower Leg**: build your own gantry tower.
- **Gantry Walkway**: walkway deck for gantries.
- **Gantry Handrail**: safety rail for walkways.
- **Gantry Radio Mast**: antenna on top of a gantry.
- **Signal ID Plate**: the numbered plate on a signal post.
- **Banner Repeating Signal**: warns early about a signal hidden round a bend (arm level = danger/caution, 45 degrees = clear). 12 V.
- **Insulated Joint**: marks a block boundary in the rail.
- **Track Circuit**: detects trains on the rail; 4 redstone output modes, radius, which trains, min speed.
- **Relay Case**: where redstone meets the signalling (inputs, outputs, settings).
- **Axle Counter Head**: wheel sensor clamped to the rail, counts each wheelset and its direction. 12 V.
- **Axle Counter Evaluator**: 4 sections, shows CLEAR / OCCUPIED / DISTURBED; reset key (PREP or DIRECT). 12 V.
- **Balise Group + LEU**: a beacon between the rails that tells passing trains a speed limit, end of authority or route. PROGRAM key. 12 V.
- **Train-Stop Trip Cock**: arm rises at a red signal and trips the brakes of a train that passes it. TEST key, speed-trap mode. 12 V.
- **Signal Battery Cabinet**: battery + charger that keeps signals lit through a power cut. Charger needs 230 V AC. 12 V.
- **Signal Sighting Checker**: survey camera that checks how far back a driver can see a signal; SHOW draws the sight line. 12 V.
- **Signal Isolation Box**: 4 slots to take signals out of use for maintenance and give them back safely. 12 V.
- **Train Describer**: 8 windows showing the train code at each signal. 12 V.
- Signal aspects that depend on the route ahead.
- **Cab signal acknowledge** *(system)*: driver must press the acknowledge key (vigilance / AWS / after a SPAD).
- Signal + sensor self-tests every few minutes.

## 3. Signal Boxes, Desks + Programming

- **Signal Box Computer**: program editor on the live map: WHEN / THEN / ELSE rules (15 conditions, 15 actions), 64 wireless channels, alarm log. Also a real interlocked 12-lever frame (yellow distants, red signals, black points, blue locks) you pull by hand.
- **Signalling Control Desk**: 12 lit push buttons you program (channels, routes, points, signals) with labels and two live screens.
- **NX Route-Setting Desk**: lit mini map of the real track; click entrance + exit to set a route; trains shown as moving lights with their codes.
- **Route Macro Panel**: 16 stored route sequences; one button sets them all. Record key. 12 V.
- **Redstone Interface (channels + Project Red bundled)**: joins redstone and bundled cables (16 per bundle) to the 64 signal box channels.
- **Control Panel Push Button**: build your own control panel! Works the device you bind it to.
- **Control Panel Turn Switch**: panel switch for your own panel.
- **Control Panel Indication Lamps**: panel lamps that show a device's state.
- **Control Panel Gauge**: panel gauge.
- **Control Panel Track Mimic**: a piece of track diagram for your panel.
- Visual rule designer + custom dashboard designer (Control Center widgets per user or role).

## 4. Signalling Safety + Radio

- **Train Radio Mast (GSM-R)**: a radio cell (96 / 160 / 256 m); trains register and report; lost link = safe fallback (brake / hold / warn). 12 V.
- **Radio Repeater**: fills a radio dead zone; needs a donor mast. 12 V.
- **Radio Dispatch Desk**: call a train, broadcast to a line, all-call, emergency all-stop, answers lineside emergency calls. 12 V.
- **Emergency Stop Plunger**: big red button: every signal into its zone goes red, trains radioed to stop. Key reset.
- **Track Blocking Switch**: a worker's key switch that takes a possession (closes the line).
- **Lineside Emergency Telephone**: CALL rings every Radio Dispatch Desk; alarm if nobody answers in 30 s. 12 V.
- **Independent Safety Supervisor**: a watchdog that checks every driverless train against its own limits; MONITOR or ENFORCE (brakes it). 12 V.
- **Fault Lab Console**: TEST MODE: break the railway on purpose (drop track circuits, fail lamps, jam points, phantom trains, cut radio / 12 V) and get a pass/fail report.
- **Fault Diagnosis Terminal**: maintainer's laptop: live faults, measure with the multimeter, diagnose, repair, verify; TRAIN mode. 230 V AC + 12 V.
- **Driver Competency Assessor**: theory test (answers A-D), route knowledge, traction pass-out, competence record. Mains + 12 V. Drawers are storage.
- Radio dead-zone coverage map.
- **Fail-safe settings** *(system)*: what each system does when power, wiring, radio or a sensor fails.
- **Chaos engineering** *(system)*: whole failure scenarios with pass/fail.
- Train tracking, train integrity (lost cars) and headcodes.

## 5. Level Crossings + Lineside Signs

- **Crossing Signal**: flashing lights + bell (21 settings: detection, timing, lights and bell, redstone).
- **Crossing Gate**: barrier arm that lowers for trains.
- **Crossing Cantilever**: overhead arm with lights over the road.
- **Crossbuck**: plain RAILROAD CROSSING sign.
- **Level Crossing Predictor**: constant warning time, road queues, obstacle detection, barrier failures, reliability report. 12 V.
- **Whistle Post**: the W board, sound the horn.
- **Milepost**: distance along the line (works it out by itself if blank).
- **Speed Limit Sign**: number drawn live; permanent / temporary / advance / end.
- **Switch Stand**: target and lamp beside the points.
- **Derail**: yellow wedge that puts a runaway train on the ground.
- **Bumper Post**: end of track.
- **Lineside Box**: small trackside equipment box.
- **Equipment Cabinet**: trackside apparatus case.
- **Location Case**: the tall cabinet that feeds a signal.
- **Cable Drum**: spare signalling cable.
- **Hazard Bollard**: black-and-yellow post.
- **Marker Post**: red-and-white banded post.
- **Stop Board**: red-and-white checker; trains stop here.
- **Limit of Shunt Disc**: ends shunting moves.
- **Blank Board**: black disc, no entry beyond.
- **Railway Sign**: write your own text on a sign (or auto text).
- **Defect Detector**: checks every passing train (hot box / dragging) and reads a report on the radio (24 settings).
- **Track Scale**: weighs every vehicle that rolls over it.
- **AEI Reader**: reads and logs every car that passes.

## 6. Track, Points, Tunnels + Structures

- **Point Machine (S700K)**: drives a set of points, proves the blades, heats them, reports failures; electric / hydraulic / pneumatic, hand crank. 12 V.
- **Structure Asset Board**: for a bridge, viaduct, tunnel or culvert: load rating, clearance, condition; stops overweight trains, floods + rockfall hold signals. 12 V.
- **Turntable / Traverser Control Panel**: drives an IR turntable or transfer table: roads 1-8, motor, locking pins, interlocks. 12 V + 400 V motor.
- **Movable Bridge Control Desk**: swing or lifting bridge: locks, position detection, boats wait for it. 12 V + 400 V motor.
- **Single-Line / Gauntlet Section Controller**: one direction at a time on a single track. 12 V.
- **Intelligent Buffer Stop**: fixed / hydraulic / friction; watches approaching trains by radar and emergency-brakes ones too fast. 12 V.
- **Rail Grinding Machine**: drives itself along the rail grinding out wear. Burns diesel / oil / coal.
- **Mobile Flash-Butt Rail Welder**: finds logged rail defects, cuts them out, welds in new rail (CUT ALIGN PREHEAT WELD SHEAR GRIND UT).
- **Survey Peg**: setting-out stake with chainage, cut / fill and grade.
- **Cable Trough**: concrete trough for cables along the track.
- **Tunnel Control Panel**: ventilation, smoke + CO, fans, lights, cross-passages, fire sequence (signals held, power off, evacuate). 12 V.
- **Tunnel Jet Fan**: 37 kW reversible fan hung from the roof. Grid cable (400 V - 11 kV) + 12 V.
- **Tunnel Smoke + CO Detector**: sees fire, lava and burning things. 12 V.
- **Tunnel Light**: really lights the tunnel; 3 h battery backup.
- **Tunnel Evacuation Sign**: green running man with distance to the nearest exit each way.
- **Cross-Passage Door**: self-closing fire door to a safe refuge; counts people sheltering.
- **Tunnel Radio (Leaky Feeder)**: keeps trains on the radio inside tunnels. 12 V.
- **Tunnel Portal Name Plate**: stone headwall with the tunnel's name, end and length.
- **Tunnel Distance Marker**: metres from the portal and to the nearest exit.
- **Concrete Tunnel Lining**: Wall, Haunch, Crown, Refuge Niche: 4 building blocks to build concrete tunnels.
- **Brick Tunnel Lining**: Wall, Haunch, Crown, Refuge Niche: the same 4 shapes in brick.
- **Stone Tunnel Lining**: Wall, Haunch, Crown, Refuge Niche: the same 4 shapes in stone.
- **Track wear + geometry** *(system)*: every train wears the track; bad track gets automatic temporary speed limits.
- **Track standards** *(system)*: ballasted, slab, heavy-haul, metro, high-speed (cost, upkeep, load, speed).
- Rail flaw detection (hidden cracks found by ultrasonic testing).
- Tunnel airflow + smoke spreading between sections.
- **Surveying + GIS** *(system)*: chainage, gradients, land boundaries, blueprint / GeoJSON export.

## 7. Stations + Passengers

12 station pieces, each in 4 styles: Victorian, Modern, Metro and Rural Halt (48 blocks):

- Platform Edge (Victorian / Modern / Metro / Rural Halt)
- Platform (Victorian / Modern / Metro / Rural Halt)
- Canopy Column (Victorian / Modern / Metro / Rural Halt)
- Canopy Roof (Victorian / Modern / Metro / Rural Halt)
- **Platform Bench (Victorian / Modern / Metro / Rural Halt)**: you can sit on it.
- Waiting Shelter (Victorian / Modern / Metro / Rural Halt)
- Platform Lamp (Victorian / Modern / Metro / Rural Halt)
- Litter Bin (Victorian / Modern / Metro / Rural Halt)
- Station Clock (Victorian / Modern / Metro / Rural Halt)
- Station Name Board (Victorian / Modern / Metro / Rural Halt)
- Platform Number Sign (Victorian / Modern / Metro / Rural Halt)
- Timetable Board (Victorian / Modern / Metro / Rural Halt)
- **Station Master's Desk**: names the station and runs it: platforms, announcements, the day's log, fare zone. Station type changes behaviour (a Rural Halt is a request stop).
- **Station Door Controller (block name**: station_doors): opens train doors at the platform: chime, open, dwell, warning beeps, close; holds the train until doors are shut; redstone in/out.
- **Platform Screen Door**: glass door on the platform edge that opens with the train doors.
- **Platform Screen Panel**: fixed glass panel between the screen doors.
- **Arrivals Board**: next driverless trains; 5 themes, split flaps, 12/24 h clock, chimes; join several into one wide board (20 settings).
- **Station PA Speaker**: reads out arrivals and departures (13 settings).
- **Ticket Machine**: sells tickets to every named station; prices, round trips, opening hours; hold a ticket to call a train.
- **Fare Gate**: shows a ticket in, lets a passenger through.
- **Ticket (item)**: from the machine; punched by the gate.
- **Platform Allocation Panel**: gives arriving trains the best of up to 6 platforms (length, step-free, direction). 12 V.
- **Station Lift**: one landing per level; CALL and floor buttons. Mains + 12 V.
- **Escalator**: moving steps up or down; START / STOP, direction key, E-STOP. Mains + 12 V.
- **Help Point**: HELP calls staff, INFO tells the next train, MIND THE GAP. 12 V.
- **Tactile Paving (Blister)**: bumpy paving at crossings and stairs.
- **Tactile Paving (Platform Edge)**: safety-yellow edge paving.
- **Accessible Toilet**: grab rails, emergency cord, cleaning log. 12 V.
- **Vending Machine**: sells what you stock it with, to you and to passengers. 12 V.
- **Retail Kiosk**: shop counter with display shelf and stock cupboards; pays rent to the station. 12 V.
- **Platform Lamp**: lamp column that really lights the platform dusk to dawn. Mains + 12 V.
- **Waiting Shelter Heater**: radiant heater with thermostat. Mains + 12 V.
- **Station Facilities Panel**: accessibility rating, facilities in service, running costs + income. 12 V.
- **Platform Crowd Monitor**: counts people, crowding level A-F, queues; can meter gates and close entrances. 12 V.
- **Platform Zone Marker**: lettered A-H coach zone signs; trains announced by zone. 12 V.
- **Platform Door Safety Controller**: short platforms, selective door opening, overrun lock-out. 12 V.
- **Station Security + Opening Hours Panel**: opening hours, night mode, gates lock, intruder alarm. 12 V.
- **Station Emergency Power**: UPS battery, standby generator and transfer switch for the station. Mains + 12 V.
- **Interchange Board**: walking times between lines, live connections MAKE / TIGHT / MISS; HOLD a train for a late connection. Mains + 12 V.
- **Service Diagram Board**: every unit's daily workings; swap units. Mains + 12 V.
- **Formation Planner**: how many cars each line needs now / peak / off-peak; splits surplus cars. Mains + 12 V.
- **Disruption Control Desk**: disrupted services per station and travel alternatives, replacement bus. Mains + 12 V.
- **Lost Property Office**: found racks, unclaimed store, display shelf; staff sweep up lost items, CLAIM gets yours back. Mains + 12 V.
- **Parcels Office**: send parcels to another station on driverless trains. Mains + 12 V.
- **PIS Server Rack**: the live passenger information feed for every sign, board, PA and ticket machine. 12 V.
- **PIS Platform Sign**: next trains + scrolling ticker from the PIS server. 12 V.
- **Announcement Control Unit**: announces delays, platform changes, cancellations and emergencies by itself (with a real recorded voice). 12 V.
- **Passenger demand simulation** *(system)*: catchments, fare zones, integrated tickets, service quality + satisfaction.
- Walking passengers you can see on platforms (only a picture of the numbers; can't block trains).
- Station types, dwell times, terminal announcements.

## 8. Station Shops + Newspaper

- **Newsstand**: prints and sells The Pride Rail Times, a real daily newspaper written from what happened on your server. Stock paper + ink sacs, PRINT, BUY. Mains + 12 V.
- **Coffee Kiosk**: espresso machine, milk fridge, menu board; cocoa beans + milk + cups, BOILER key, order a drink. Mains + 12 V.
- **Ticket Office Counter**: staffed booking window: keypad destination, SINGLE / RETURN, ISSUE. Mains + 12 V.
- **Coffee (item)**: Espresso, Americano, Latte, Cappuccino, Mocha. Drink it for a little food and a short speed boost.
- **Travel Centre**: zone passes, refunds, complaints and the Passenger Charter. 12 V.

## 9. Freight, Yards + Cargo

- **Loading Silo**: overhead loadout that fills the car under it (filters, full-load rules, redstone).
- **Unloading Pit**: bottom-dump pit that empties the car over it.
- **Freight Office**: freight contracts: industries post jobs, accept one, print the Waybill, haul it, get paid. Working drawers, filing cabinet, cupboard + display shelf. 12 V.
- **Waybill (item)**: the paperwork for a contract load.
- **Container Terminal**: gantry crane over a container yard (truck apron, rail lane, 2 stack rows). 12 V.
- **Shipping Container (item)**: a box with its own ID number.
- **Port Berth (STS Crane)**: quay desk under a ship-to-shore crane: ships call, boxes unloaded and loaded, trucked to the rail terminal. 12 V.
- **Yard Gantry Crane (RMG Cab Desk)**: operator's cab desk for a rail-mounted gantry over a truck lane, a rail lane and a 4x4x4 container stack. Joysticks, LOCK / UNLOCK, AUTO job list, anti-sway (the box stops swinging), load cell, wind alarm, spreader camera to CCTV. 12 V + 480 V / 4.16 kV / 13.8 kV.
- **Hump Yard Control Panel**: sorts cars over the hump into 8 class tracks from their waybills; retarders, driverless pusher. 12 V.
- **Yard Retarder**: air rail brake that slows rolling cars to a target speed; speed radar, weighing. 12 V + 400 V compressor.
- **Reefer Plug Post**: 4 sockets for refrigerated containers + shore power for reefer wagons; set temperature. Mains + 12 V.
- **Wagon + Container Washout Plant**: RINSE, DETERGENT or STEAM clean; certifies food-grade after dirty loads. Mains + 12 V.
- **Axle-Load Weighbridge**: weighs every axle and checks left/right and front/back balance; prints a ticket. 12 V.
- **Container Tracking Terminal**: any box's number, status, seal, location, condition and history; PRINT the trace. 12 V.
- **Cold chain** *(system)*: reefer cargo spoils if its temperature drifts.
- Cargo contamination + chain of custody.
- **Intermodal logistics** *(system)*: truck appointments, demurrage (late fees).
- **Wagon types** *(system)*: which car can carry what (off by default on old worlds).

## 10. Economy, Companies + The World

- **Company HQ**: boardroom: accounts, staff, wages, fleet, shares, loans, dividends; FOUND or JOIN a company; staff-only safe. 12 V.
- **Commodity Exchange**: live prices that follow supply and demand; buy and sell for Pride coins. 12 V.
- **Railway Stock Exchange Board**: shares, bonds, order book, index, trophies (in-game money only). 12 V.
- **Regulator Office**: audits, corrective actions, slot auctions for busy lines. 12 V.
- **Insurance Desk**: policies priced from your maintenance records, claims with evidence drawer. 12 V.
- **Town Hall Planning Board**: founds a town; population, catchment, noise, public-service tenders. 12 V.
- **Construction Site Office**: build a new mine, factory or power station from materials delivered by train. 12 V.
- Fares + fare zones, peak / flat / per-block prices.
- Mergers, takeovers, franchising, leases between companies, track access deals, service-level agreements.
- Public-service obligations (subsidised lines), environment accounting (energy, emissions, noise).
- Optional research tree (off by default), achievements / trophies, generated scenarios.

## 11. Depots, Maintenance + Rolling Stock

- **Maintenance Depot**: repairs worn trains parked beside it, part by part, with reports.
- **Depot Control Office**: job board, fleet, exam plans, routing, allocation, spares, downloads; AUTO ROUTE / NIGHT STABLING keys. 12 V.
- **Depot Stores**: 6 real spare-part bins + stock board with reorder lamps. 12 V.
- **Tool Crib**: caged tool store: pegboard shows hung tools, drawers, cabinet, sign-out register; key-locked to you. 12 V.
- **Crew Booking-On Desk**: driver roster, sign on / off, breaks, qualifications. 12 V.
- **Underfloor Wheel Lathe**: re-cuts worn or flat wheels where the train stands. Mains + 12 V.
- **Bogie Drop**: swaps a bad bogie from under a car (LOCK, LOWER, TRAVERSE, FIT, RAISE). Mains + 12 V.
- **Brake Test Rig**: full brake test on a standing train before it returns to service. 12 V.
- **Fuelling and Sanding Point**: 40,000 L fuel tank + pump nozzle, sand hopper. Mains + 12 V.
- **Train Wash Plant**: drive-through brushes (one each side of the track). Mains + 12 V.
- **Carriage Servicing Point**: fresh water, toilet emptying, cleaning, catering restock. Mains + 12 V.
- **Locomotive Diagnostic Panel**: plugs into the nearest loco: gauges, fault codes, start / stop, sanders, traction control. 12 V.
- **Formation & Livery Desk**: plan train formations (length, weight, brakes, power) and repaint in company colours. 12 V.
- **Locomotive Design + Rebuild Workshop**: change a loco's traction, gearing, cooling, bogies and brakes, then test run. 12 V.
- **Remanufacturing Bench**: wash, strip and grade used parts into rebuilt spares or scrap. Mains + 12 V.
- **Commissioning Test Set**: scans new gear, plans and runs acceptance tests, signs it off; injection test bench. 12 V.
- **Rolling Stock Spare (item)**: 20 kinds: wheelsets, bogies, brake blocks, hoses, bearings, couplers, motors, traction motors, power kits, breaker kits, control cards, filters, insulators, point motors, rail lengths, signal LEDs, bushings, structure kits and more.
- **Inspection Instrument (item)**: 5 kinds: ultrasonic rail tester, ultrasonic axle tester, thermal imager, insulation tester, structural monitor.
- **Wear** *(system)*: odometer, wheels, brakes, engine, bearings, electrics; MAINTENANCE REQUIRED alerts.
- **Condition-based limits** *(system)*: worn trains run slower instead of breaking at once.
- Predictive maintenance, job cards, spare-part ordering with lead times, asset register for all gear.
- Crew scheduling + duty hours. Fleet allocation, spare locos, automatic depot routing.
- **Train cleaning** *(system)*: cars get dirty inside and outside; dirt shows on the model.

## 12. Steam Depot + Battery / Hydrogen Trains

- **Coaling Tower**: concrete bunker with skip hoist and chute; coals engines under it. Mains + 12 V.
- **Coaling Tower Bunker**: the high bunker, 2-14 blocks above the tower base.
- **Coal Receiving Pit**: grated hopper where wagons tip coal for the tower. 12 V for the level gauge.
- **Water Column**: swinging arm + leather bag, 30,000 L tank, feed pump. Works by hand; pump needs mains + 12 V.
- **Ash Pit**: drops a steam engine's ash, quench sprays, hoist into the ash wagon. Mains + 12 V.
- **Locomotive Ash (item)**: what comes out of the ash pit.
- **Sand Drier and Tower**: dries damp sand and blows it up into the hopper over the engine. Mains + 12 V.
- **Boiler Washout Plant**: step-by-step boiler washout that clears scale and leaky tubes. Mains + 12 V.
- **Train Charging Post**: plug in a battery train; ECO / NORMAL / RAPID charge. Grid cable + 12 V.
- **Hydrogen Compressor**: squeezes hydrogen from the Electrolysis Plant up to 500 bar. Mains + 12 V.
- **Hydrogen Storage Tubes**: LOW / MEDIUM / HIGH pressure banks with gauges and gas detection. 12 V.
- **Hydrogen Dispenser**: 350 bar nozzle with pre-cooler; E-STOP trips the whole station. Mains + 12 V.
- Steam boilers build up scale (more coal for the same steam) until washed out.
- Battery-electric and hydrogen fuel-cell trains.

## 13. Overhead Line + Electric Trains

- **Catenary Mast (stack it)**: stack masts as tall as you need.
- **Portal Girder (spans tracks, hangs wire)**: lattice girder over several tracks.
- **Cantilever Arm (from mast over the track)**: holds the wire over the rail.
- **Contact Wire**: the live wire (with messenger wire and droppers).
- **Section Insulator**: splits the wire into separate electrical sections.
- **Neutral Section**: a dead gap; trains open their breaker and coast through.
- **Motorised OHLE Isolator**: switch that joins two sections; LOCAL / REMOTE / AUTO fault isolation, crew earth switch. 12 V (hand crank without).
- **Balance-Weight Tensioner**: keeps the wire tight in any weather (no power needed).
- **Pantograph Sign + Track Magnet**: tells locos to lower / raise their pantograph or coast.
- **OHLE Feeding Diagram**: live map of the line sections, feeder breakers and isolators. 12 V.
- **OHLE Fault Locator**: tells you how far away a wire fault is and sends a crew. 12 V.
- **OHLE Recording Station**: records wire height, zig-zag and wear from a recording car. Mains + 12 V.
- **Wayside Energy Storage (BESS)**: big battery that soaks up braking energy and gives it to accelerating trains. 12 V.
- **Railway Substation**: powers contact wire and third rail nearby (storm trips, auto-reset).
- **Train Charging Station**: charges parked electric and battery trains.
- Electric locos only run under live wire; battery trains charge on it.
- **Traction load flow** *(system)*: each train draws real power from the grid; regenerative braking feeds power back.
- Parallel substations share the load (fed from both ends).
- Pantograph + wire wear, sagging wire in heat, arcing.

## 14. Power Grid (Real Electricity)

Real voltage levels from 12 V DC up to 69 kV, plus rail 600 / 750 / 1500 / 3000 V DC and 25 kV AC. Every network has kW, kVAR, kVA and power factor; each cable has current, losses, voltage drop and temperature. Machines burn out if fed the wrong voltage; fix them with the screwdriver.

- **Grid Intake (RF / FE in)**: takes power from any mod (cables, generators, reactors) into the grid.
- **Power Transformer**: steps voltage down (or up); tap changer, voltage regulator, fans.
- **Traction Rectifier (DC)**: makes DC for third rail, monorail beams and maglev.
- **Traction Transformer (25 kV AC)**: powers overhead contact wire.
- **Inverter (DC to AC)**: DC in at the back, clean AC out.
- **DC-DC Converter**: one DC voltage to another (48 V up to 750 V, 1500 V down to 24 V).
- **Circuit Breaker**: switches a section; trips on overload or storms; auto-reclose; kA rating; CLOSE / OPEN / RESET, LOCAL / REMOTE key.
- **Disconnector**: maintenance switch; open it under load and it arcs!
- **Earthing Switch**: every network needs an earth or breakers won't close.
- **Protection Relay**: one per breaker; a breaker without one won't close.
- **Surge Arrester**: sends lightning to earth.
- **Capacitor Bank**: smooths DC and fixes power factor.
- **Battery Storage**: stores power and gives it back when the intake runs dry.
- **Energy Meter**: shows load and energy; comparator output.
- **Grid Control Panel**: overview of every network: live parts, loads, breakers, alarms.
- **Track Feeder**: clamps onto the line and powers beam / wire / third rail; any mod's cable plugs in.
- **Feeder Cable**: copper / aluminium / superconductor, 1-300 mm2, LV / MV / HV, busbars; picker screen. Works across unloaded chunks.
- **Diesel Generator**: burns diesel / biodiesel / oil / ethanol from any mod's pipes; OFF / MAN / AUTO, E-STOP.
- **Steam Turbine**: turns any mod's steam into power.
- **Solar Array**: tracks the sun; less in rain.
- **Wind Turbine**: stronger high up and in storms.
- **Wind Tower Section**: stack tower sections; the top turns into a drifting wind, 3 blades at real rpm, feathers in storms.
- **Electric Motor (3-phase, with starter)**: DOL / star-delta / soft-start / VFD; its shaft drives any mod's machine; stalls and overload trips.
- **Auto Controller**: runs the grid by itself: auto-reclose, load shedding, standby generator start, alarm horn.
- **Protection IED (50/51 · 87T · 50N · 27/59 · 81)**: smart relay: overcurrent, transformer differential, earth fault, voltage, frequency. 12 V.
- **Transfer Switch Controller (ATS)**: switches to standby power when the main fails, starts the generator.
- **Power Quality Analyzer**: voltage, frequency, unbalance, harmonics, sags / swells with alarms.
- **12 V Battery Charger / UPS**: AC in, 12 V DC out from a real battery; keeps signals and lights going in a power cut.
- **Black Start Panel**: restarts a dead grid in stages.
- **Harmonic Spectrum Meter**: LED bar graph of harmonics; alarm past the limit. 12 V.
- **Harmonic Filter Bank**: tuned filters that clean up harmonics. 12 V.
- **Microgrid Controller**: islanding, frequency control, load shedding, resync. 12 V.
- **Unit Substation Kit (13.8 kV → 480 V) (item)**: places a whole ready-wired small substation.
- Generators spin up, show Hz, sync with a synchroscope; trip on under-frequency, overload, reverse power; blackouts cascade.
- **Short circuits** *(system)*: a breaker explodes if the fault is bigger than its kA rating.
- **Burn-outs** *(system)*: converters fry, transformers melt after 3 overheat trips, motors burn on a 2nd overload; burnt gear smokes until repaired.
- Load shedding, transformer cooling + heat, electrical maintenance history, protection studies.

## 15. Safe Working, Instruments + 12 V Wiring

- **Permit-to-Work Board**: key safe, permit printer, register; HV work needs a permit (isolate, earth, prove dead, lock off). 12 V.
- **Permit to Work (item)**: the printed permit.
- **Lockout Padlock + Danger Tag (item)**: lock a switch off while you work.
- **Lockout Padlock Key (item)**: the key for your padlock.
- **Protection Engineer's Laptop (item)**: relay settings and protection studies.
- **Electrician's Screwdriver (1000 V insulated) (item)**: settings and repairs; zaps you if live.
- **Multimeter / Clamp Meter (item)**: measure volts, amps and more on anything.
- **Relay Test Set**: tests and calibrates protection relays (pick-up, timing, certificate). Mains + 12 V.
- Voltmeter, Ammeter, Wattmeter, Var Meter, Power Factor Meter, Frequency Meter, Temperature Gauge: analog wall meters with moving needles. 12 V.
- **Readout Screen**: digital readout of any value. 12 V.
- **Status Lamp**: lamp that shows a state. 12 V.
- **Control Button**: works a machine's button from far away over the wire. 12 V.
- **Gauge Panel**: holds any 4 instruments in one block; disguise it as any block. 12 V.
- **Instrument Wire (item)**: 16 colours running side by side; also carries the 12 V power pair.
- **Bundled Instrument Cable**: many colours in one cable.
- **Grid Tap**: takes a reading from a grid machine onto a wire.
- **Radio Transmitter**: sends wire signals wirelessly.
- **Radio Receiver**: receives them.
- **Wire Chase Wall**: hide wires inside walls, drill holes, disguise as any block.
- **Field RTU**: polls wired gear; fibre + radio with failover; lost comms = STALE values. 12 V.
- **Approval Panel**: request → approve → execute, two keys for dual authorisation. 12 V.
- **Railroad Data Link**: OpenComputers / ComputerCraft link: trains, stations, signals, grid, routes from Lua.
- **Alarm Annunciator**: 12 lit alarm windows, horn + strobe, ACK / SILENCE / RESET / LAMP TEST. 12 V.
- **Event Recorder**: the railway black box; replay on the console; prints incident reports. 12 V.
- **Industrial Part (item)**: 26 parts (motor cores, windings, bearings, gearboxes, circuits, screens, solar cells, turbine blades...) used in expensive recipes.
- **Cartridge Fuse (item)**: spare fuse for machine cabinets; a Blown Cartridge Fuse comes out after a fault.
- **Opening machine cabinets** *(system)*: on 24 machines the cabinet doors really swing open; inside are working breakers, fuses, contactors and terminals.
- **Unified alarm manager** *(system)*: every alarm in one list; root-cause grouping.

## 16. Factory (Was Pride Industry)

Big walk-in multiblock plants built from Machine Casing + hatches + a controller. Each controller has a real control panel. Electrical plants plug into the grid at their rated voltage: too much and they burn or explode.

- **Machine Casing**: the walls of every plant.
- **Item Input Hatch**: feed items in (pipe, conveyor, hopper).
- **Item Output Hatch**: products come out here.
- **Water Inlet**: pipe water in, or set next to water.
- **Fluid Output Hatch**: fluids (even molten metal) come out to pipes.
- **Drive Coupling**: bolt an Electric Motor's shaft here.
- **Power Inlet**: plug the plant into the grid.
- **Jaw Crusher Controller**: 1 ore → 2 crushed ore.
- **Washing Plant Controller**: 1 crushed → 2 washed ore (uses water).
- **Flotation Cells Controller**: 2 washed → 3 concentrate.
- **Induction Smelter Controller**: melts ore into real molten metal at 1,450 C; pours ingots or sends it to pipes.
- **Coke Oven Battery Controller**: coal → coke, creosote to the hatch.
- **Blast Furnace Controller**: iron + coke → steel at 1,500 C.
- **Electric Arc Furnace Controller**: faster steel; 13.8 kV; overload and its transformer explodes.
- **Rolling Mill Controller**: ingots → plates.
- **Wire Drawing Machine Controller**: copper/gold → windings, iron → instrument wire.
- **Robotic Welding Cell Controller**: robot arm welds plates, frames, shafts. 480 V.
- **Assembly Line Controller**: 5 stations build 8 products.
- **Oil Press Controller**: seeds → plant oil.
- **Refinery Controller**: plant oil / crude / creosote → diesel.
- **Electrolysis Plant Controller**: water → hydrogen + oxygen. Needs 750 V DC; too much and it blows up.
- **Grain Windmill Controller**: wind-driven mill, grain → flour; furl the sails in storms.
- **Sawmill Controller**: 1 log → 6 planks + 2 sawdust.
- **Sleeper Treatment Plant Controller**: creosotes timber into the sleepers IR track is made from.
- **Borehole Pump Controller**: pumps groundwater; overpumping drains the aquifer.
- **Water Tower Controller**: 64,000 L tank with gravity pressure, valves, level alarms.
- **Bulk Loading Station Controller**: loadout tower over the track fills freight cars.
- **Overhead Loading Silo Controller**: chute drops through the floor (up to 12 blocks), fill-to %, STOP / ADVANCE signal.
- **Rail Unloading Pit Controller**: grated pit empties cars over it.
- **Rotary Car Dumper Controller**: rolls a car over 155 degrees to empty it.
- **Tank Car Loading Arm Controller**: fills tank cars, refuels diesel locos.
- **Tank Car Unloading Station Controller**: drains tank cars to pipes.
- **Crate Packer Controller**: packs a stack into one Shipping Crate (16x freight) or unpacks.
- **Production Planning Console**: plans ore → steel → rail across plants, power bids, freight departures. 12 V.
- **Belt Conveyor**: carries 4 stacks; curves, ramps, side-merge; redstone stops it; an Electric Motor can drive a line.
- **Fast Belt Conveyor**: the faster tier.
- **Express Belt Conveyor**: the fastest tier.
- **Pride Pipe**: carries any fluid (250 mB/t), joins any mod's pipes.
- **Gate Valve**: handwheel open / shut; redstone shuts it.
- **Flow Meter**: shows the flow; comparator output.
- **Crushed / Washed Ore, Concentrate (items)**: the ore-doubling stages for every metal.
- **Industrial Material (item)**: coke, flour, graphite electrode, rail clips, rail section, sawdust, slag, steel ingot, steel scrap, creosoted sleeper.
- **Shipping Crate (item)**: holds a whole stack, crates stack 16.
- **Fluids** *(system)*: Hydrogen, Oxygen, Molten Metal, Ore Slurry, Froth, Creosote, Plant Oil, Diesel.

## 17. Industries (Simple Supply Chains)

- **Coal Mine**: digs coal for power stations and steel mills.
- **Iron Mine**: iron ore for the steel mill.
- **Quarry**: stone for the cement works.
- **Logging Camp**: timber for the sawmill.
- **Farm**: grain for the flour mill (faster in daylight).
- **Sawmill**: logs in, planks out. Needs power.
- **Steel Mill**: iron ore + coal in, iron out. Needs power.
- **Flour Mill & Bakery**: wheat in, bread out. Needs power.
- **Cement Works**: gravel in, concrete powder out. Needs power.
- **Coal Power Station**: burns coal delivered by train, pushes power into the grid or any mod's cables.
- **Town Market**: buys bread, planks, iron and concrete; a well-supplied town grows.
- Industry orders, load shedding when power is short, freight contracts from demand.

## 18. Weather, Drainage + Weather Trains

- **Lineside Weather Station**: wind, rain, air + rail temperature, visibility, snow, ice, flood; feeds the console. 12 V.
- **Points Heater Cabinet**: heats every switch within 12 blocks so points don't freeze (12 kW). 12 V + power.
- **Rail Temperature Monitor**: rail heat vs safe limits, heat speed limits, buckle risk. 12 V.
- **Box Culvert**: drains water from its area; clear storm debris by hand. No power.
- **Drainage Pump Station**: two pumps on a float switch; needs servicing. 12 V.
- **Flood Warning Outstation**: water level, rate of rise, siren + beacon; can impose a 20 km/h flood limit. 12 V.
- **Weather Train Depot Desk**: plans snowplough, sandite (rail treatment) and de-icing runs from the forecast. 12 V + 120/240 V.
- **Snowplough Attachment (item)**: fit to a loco; clears snow and sweeps the rail above 5 km/h.
- Snow, ice, floods, heatwaves, fog per area; snow-clearing crews; leaf-fall low adhesion.

## 19. Night Lighting

- **Canopy Light**: LED batten under a canopy; real light. Mains or 12 V.
- **Yard Floodlight**: twin head that lights 20 blocks ahead. Mains or 12 V.
- **Floodlight Mast Section**: stack as tall as you like, with ladder.
- **Lit Station Name Sign**: the station's name, lit at night. Mains or 12 V.
- **Emergency Light**: stays lit 3 h on battery in a power cut.
- **Photocell Lighting Controller**: dusk-dawn switch with live lux reading. 12 V.
- **Lighting Contactor Panel**: 4 lighting circuits, HAND / OFF / AUTO each. 12 V.
- **Light**: invisible helper block a floodlight uses to light the ground (not for building).
- Lamps glow at night, age and flicker before they fail; lit trains with headlight beams (with a dynamic-lights mod).

## 20. Emergencies + Emergency Services

- **Incident Command Desk**: incident board, orders, rescue / evacuation plans, reports; drawers are storage. 12 V.
- **Breakdown Crew Depot**: the rerailing gang; jacks, packing and slings in its lockers set how fast they work. 12 V.
- **Fire Alarm & Suppression Panel**: detects fire, alarms, cuts power, sprays water mist; reads hazard placards. 12 V.
- **Emergency Exit Sign**: lights on mains failure; TEST button.
- **Emergency Assembly Point**: roll-call box counts people during an evacuation. 12 V.
- **Remote Maintenance Terminal**: every machine on the bus, test / operate remotely, work orders. 12 V.
- **Fire Station**: one fire engine; turns out to fires, dangerous goods and big derailments. Mains + 12 V.
- **Ambulance Station**: one ambulance; treats the injured. Mains + 12 V.
- **Police Station**: one patrol car; cordons the scene and closes crossings. Mains + 12 V.
- **Police Cordon Barrier**: POLICE LINE barrier the police car puts up and takes away.
- Fire engines, ambulances and police cars drive real roads with sirens.
- Incident levels, train rescue + towing, hazmat rules, train black boxes, KPIs, daily + weekly reports, scenario lab, graded drills.

## 21. Road Traffic + Trams

- **Traffic Signal Controller**: runs road signals, ped crossings and tram signals within 48 blocks; tram priority. Mains + 12 V.
- **Traffic Signal**: red / amber / green head; road vehicles stop at it.
- **Pedestrian Crossing Unit**: push button, WAIT lamp, red / green man, beeps.
- **Tram Signal**: white-bar tram signals (STOP / GO / LEFT / RIGHT / PREPARE).
- **Detection Loop**: in the road; trams and cars call the controller.
- **Tram Stop**: shelter, live display, help point; a station trams call at. Mains + 12 V.
- **Ticket Validator**: tap a ticket or pass at a tram stop. 12 V.
- **Lit Platform Edge**: LED edge lights flash while a tram is due.
- **Tramway OHLE Pole**: street pole with arm for the tram's wire; LINE LIVE / DEAD lamp. 12 V.
- **Tram Road (street track)**: road surface with rails in it.
- Cars, vans, trucks and buses drive the roads, queue at closed crossings and stop at signals (never saved, can't pile up).
- **Trams** *(system)*: any stock tagged TRAM runs on sight, obeys tram signals.

## 22. Ropeways + Funiculars

- **Ropeway Station**: boarding platform, bullwheel, gates / READY / HOLD / E-stop. 12 V + 120/240 V.
- **Funicular Station**: stepped platform, buffer, gates. 12 V + 120/240 V.
- **Ropeway Machine Room**: drive motor, brake disc, gearbox, emergency brakes, evacuation drive. 480 V / 4.16 kV / 13.8 kV + 12 V.
- **Ropeway Control Desk**: START / STOP / E-STOP, speed, wind limit, AUTO / MANUAL / EVAC, doors, fault lamps, mimic screen. 12 V.
- **Ropeway Tension Weight**: keeps the rope tight. 12 V monitor.
- **Ropeway Pylon Section**: stack lattice pylons; top carries the rope wheels.
- **Funicular Track**: rails with rope rollers; becomes a 45 degree ramp on slopes.
- **Haul Rope Spool (item)**: strings a line: GONDOLA, AERIAL TRAM or FUNICULAR.
- Gondola cabins, aerial tram cabins and funicular cars you ride, with rope sag and wind stops.

## 23. Crew, Passengers + Ticket Inspectors

- **Crew Office**: roster board, sign-on terminal, lockers, fridge, kettle; HIRE drivers, guards, inspectors; shifts and diagrams. Mains + 12 V.
- **Revenue Protection Office**: inspectors' case files, fines ledger, CCTV link; DEPLOY an inspector. Mains + 12 V.
- **Train crew you can see** *(system)*: drivers and guards in uniform walk to their trains, take breaks, get tired.
- Ticket inspectors check passengers and catch fare dodgers (warn or fine).

## 24. Mail by Train

- **Sorting Office (Pride Post)**: post letters and parcels, sort into town pigeonholes, bag them for mail trains; PO boxes. Mains + 12 V.
- Writing Paper + Envelope / Letter / Parcel / Sealed Mail Bag (item, 4 kinds): write a letter, post it, it travels by train.
- Travelling post office (TPO) cars with sorting frames.
- Pride Realms delivery robots carry mail from the sorting office to mailboxes.

## 25. CCTV + Security

- Dome Camera, Bullet Camera, PTZ Camera (pan / tilt / 30x zoom), Fisheye 360 Camera, Night / IR Camera, Thermal Camera, Platform Camera, Doorbell Camera (chimes the desk): live pictures. 12 V + PoE from the NVR or 230 V.
- **CCTV Wall Monitor**: live video, CH+/CH-, split 1/4/9; side by side they join into a video wall up to 4x4. 12 V + mains.
- **Ceiling Platform Monitor**: small screen over a platform. 12 V + mains.
- **CCTV NVR Rack**: 9 drive bays, 16-port PoE switch, records and plays back. 12 V + mains.
- **CCTV Control Desk**: keypad, PTZ joystick, presets, tours, alarm windows, spot monitor + 2x2 preview wall. 12 V + mains.
- **CCTV Hard Drive (item)**: 1, 2, 4 or 8 TB for the NVR.
- **Train Forward-Facing Camera (item)**: bolt onto a loco; streams the view ahead.
- **CCTV Configurator (IPC Tester) (item)**: aim, zoom, privacy masks, motion zones.
- Motion + tamper alarms, bookmarks, recordings on disk.

## 26. Pride OS Computers, Furniture + Storage

- **Pride OS** *(system)*: sneak + right-click (empty hand) any of 26 desks with a monitor: Notepad, Calculator, Paint, Snake, Minesweeper, 2048, Solitaire, Railway status, Terminal, Music, Settings + PIN lock. Needs 12 V (and mains where the desk needs it).
- **Real furniture storage** *(system)*: drawers, cupboards, safes and lockers on desks open as chests; shelves show what you put on them.
- **Interior Fitting Kit (item)**: fits a passenger car with a walk-in interior: coach, café, sleeper (or auto by name).
- **Walk-in train interiors** *(system)*: sit in seats, café till, kettle + espresso, fridge, luggage racks, berths you sleep in, toilet with lock, fresh + waste water tanks.

## 27. Planning, Simulation, Admin + API

- **Network Planning Desk**: digital twin + shadow simulation, what-if questions, resilience, timetable robustness, versions, AI dispatchers; real drawers + shelf. 12 V.
- **Railway Administration Console**: admins only: inspect / safe-stop trains, pause systems, backups, statistics, CASUAL / REALISTIC / HARDCORE profile. 12 V.
- Temporary works (speed limits, single-line working), possession planner, stock compatibility check, split / join trains, yard planner, expansion advisor.
- Config version control (diff + rollback), trends, AI dispatchers per region.
- Access control, command locking, persistent stats, backups, load-aware simulation.
- **Railway API for other mods and computers** *(system)*: transport adapters (IR, Traincraft, PrideCarts), telemetry, train order templates, audit log, JSON export.
- Quest events for the Railway Career quest line (PrideQuests).
- **Logic Gate Module**: AND / OR / XOR / NAND / NOR / XNOR / NOT / BUFFER.
- **Timer Relay**: on-delay, off-delay, pulse, flasher.
- **Counter Module**: up / down with preset; survives power cuts.
- **Comparator Module**: A vs B or a setpoint, with window and hysteresis.
- **Latch + Edge Module**: SR, RS, toggle, edge pulses.
- **Arithmetic Module**: add, subtract, multiply, divide, min, max, average.
- **Sequencer (State Machine)**: 2-8 steps by input or time.

## 28. Drones

- **Drone Dock**: landing + charging pad, battery bay, hangar, wind / rain mast; ARM, pick a mission, LAUNCH. 12 V + 120/240 V.
- **Inspection Drone (item)**: the quadcopter; flies TRACK / OHLE / STRUCTURES / TUNNEL missions with zoom + thermal camera; streams to CCTV; logs findings.
- Drone Flight Battery (500 Wh) (item).
- **Drone Handheld Controller (item)**: fly it by hand.

## 29. Fun: Model Railway, Cameras, Sounds

- **Model Railway Control Desk**: 2 cab controllers, points P1-P6, signals S1-S4, meters, fast clock; MIRROR mode shows your real railway. 12 V + mains.
- **Model Railway Table**: build 2x2 to 4x4; edit the mini layout.
- **Mini Rolling Stock (item)**: steam, diesel, electric, coach, goods, tank and brake van minis.
- **Trackside TV Camera (Tripod)**: robotic broadcast camera that follows trains; CAM 1-8, tally lamps. 12 V.
- **Director Desk**: vision mixer: preview / program, CUT, shot list, ON AIR; WATCH puts your screen on the program. 12 V + 120/240 V.
- **Photo mode / cinematic train camera (key)** *(system)*: chase, side track, cab view, heli orbit, flyby, replays.
- **Realistic sound pack** *(system)*: 3,500+ sounds (horns, brakes, relays, breakers, machines, bells) + recorded voice announcements; volume per group.

## 30. The Trains Themselves

- **Working loco cab desk** *(system)*: every switch and handle works; driver display (DMI) key; acknowledge key.
- Consist physics (coupler forces), engine management (oil, coolant, battery, faults), wheel-slip + slide control, sanding.
- Multiple-unit control, auxiliary systems, condition + dirt shown on the model.
- Train derailments + wrecks (with the IR fork).
- **Pride Rail train pack built in** *(system)*: subways (NYC R211 + R160, London 2009 Tube + S Stock, Tokyo Ginza 1000, Paris MP14), monorails (Las Vegas, Seattle, Tokyo, Innovia, Chongqing), maglevs (Transrapid, L0), high speed (N700S, ICE 3), GG1 and more: 400 stock files, Real / Pride / Trans liveries, detailed interiors, walk-through gangways, live destination screens.
- **Pride tracks** *(system)*: concrete / steel / slim monorail beams, maglev guideway, Pride Third Rail.

## 31. Other Tools + Items

- **Signal Wrench (item)**: links signals, rails and gear; sneak + right-click = Settings Console.
- **Track Geometry Trolley (item)**: walk it along the rails for an inspection report.
- **Track Maintenance Kit (item)**: fixes track condition by hand.
- **Surveyor's Total Station (item)**: design a railway before you build it; drives Survey Pegs.

## 32. Commands

Main command: `/irextras <part> ...` (most parts have "selftest").

- `/irextras status, list [radius], despawn [radius], consist <set>, sign <text|auto|off>`
- `/irextras wear [radius] | wear add <part|all> <%> | wear repair`
- `/irextras electric <on|off|auto>, cbtc, mbdebug, incidents, send`
- `/irextras grid [fill | press x y z control], press, wired, alarm`
- `/irextras contracts, industry, quest, pax, company, market`
- `/irextras sig, safety, macro, track, ohle, energy, weather, infra`
- `/irextras plan, incident, admin (profile CASUAL / REALISTIC / HARDCORE, backup, stats, pause), api`
- `/irextras cab, physics, engine, adhesion, mu, aux, livery, yard`
- `/irextras depot, asset, biz, ctl, stn, shop, ems, road, tram`
- `/irextras steam, traction, crew, mail, gantry, ride, cine`
- `/irextras drone, wxtrain, interior, cctv, light, cabinet, os`
- `/irextras modelrail, trainmap`
- Test helpers: `/irextras track <length> [deg] [left|right], stock <ids>, drive <throttle> [reverser], fill <fluid>`
- Factory: `/irfactory <form|unform|press|status> x y z [control] | /irfactory selftest`

Keys: driver acknowledge, driver display (DMI), photo mode / cinematic camera.

## 33. Batch 6: 30 More Features

**Building machines**

- **Track Laying Machine**: lays real IR track panel by panel from rails, sleepers + ballast; gauge/curve/radius knobs, AUTO up to 99 panels.
- **Tunnel Boring Machine**: bores 5/7/9 m tunnels, lines them, moves itself; steering, cutter wear, spoil conveyor. 4.16-13.8 kV + 12 V.
- **Ballast Tamper**: measures + fixes track geometry, before/after screen.
- **Locomotive Works + Erecting Bay**: builds real IR locos and cars from parts, 6-deep queue. 208-480 V + 12 V.
- **Hi-Rail Truck (item)**: drive on roads, drop guide wheels, run on rails.

**Stations + travel**

- **Pride Card (item) + Card Reader + Top-Up Machine**: tap in/out, zone fares, daily cap, lost-card transfer.
- **Pride Rail Phone (item)**: PLAN, LIVE, WALLET, TRAINS, ALERTS tabs.
- **Left Luggage Lockers**: 12 lockers, rent + PIN, tamper alarm.
- **Baggage Check-In, Baggage Belt, Baggage Hall**: bags ride the trains to a reclaim carousel.
- **Car Park Barrier + Pay Station**: real boom, SPACES FREE sign, park & ride discount.

**Station life**

- **Bus Bay + Bus Departure Board**: 4 timetabled bus routes you can ride.
- **Taxi Rank**: call a taxi, ride it, meter + fare.
- **Hotel Reception, Room Lock, Room Wardrobe**: check in, key cards.
- **Pub Bar + Pub Kitchen**: 3 taps, fryer/grill/oven/hob, 11 new foods.
- **Boarding Lift + Gap Filler**: deck rises to the train floor, safety gates, E-STOP; plate slides over the platform gap.

**New railways**

- **Rack Rail, Rack Entry, Rack Panel**: cog locos climb steep grades.
- **Ferry Dock**: an 18-block ship carries train cars across water.
- **Motorail Ramp**: Immersive Vehicles cars ride on flatcars.
- **Garden Track + Garden Station + Garden Train Set**: ride-on mini train.
- **Avalanche Detector + Avalanche Gallery**: holds trains, BLAST button.

**Operations**

- **RCO Beltpack (item)**: drive a loco from your hand while walking.
- **250 t Breakdown Crane**: lifts wrecked cars back onto the track.
- **Flange Lubricator**: cuts curve wear up to 70 %, stops squeal.
- **Master Clock**: GPS/radio time syncs every station clock.
- **Driver Training Simulator**: drive scenarios, score, driver record.

**Freight + fun**

- Grain Pit, Grain Leg, Grain Dryer, Grain Silo, Grain Loadout.
- Oil Tank (400,000 L), Oil Loading Rack, Fire + Gas Panel (foam).
- **Warehouse Dock, Rack + Office**: forklift workers load boxcars.
- **Parcel Lockers**: 24 compartments, 6-digit codes.
- Holiday Controller, Christmas Tree, Holiday Lights, Halloween Display + Holiday Kit (item): Christmas + ghost trains, presents.

Commands: `/irextras build | travel | stnlife | nr6 | rack | ferry | motorail | garden | avalanche | rco | crane | lube | masterclock | trainsim | grain | tankfarm | warehouse | lockers | holiday` (each has selftest).

## Count

Before batch 6: 433 blocks, 38 items, 7 entities, 84 systems. Batch 6 adds about 30 more.

(433 blocks = 430 you can hold + 3 hidden helper parts: console part, office part and the placed Instrument Wire. Many items have several kinds: 26 parts, 20 spares, 10 materials, 5 coffees, 5 inspection tools, 4 drives, 4 mail kinds, 7 mini trains.)
