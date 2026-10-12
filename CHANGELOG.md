# Changelog

## 0.7.0-beta - 2026-10-12

**Beta / development version.** A very big update: everything since 0.6.0-beta. Most of the new machines were tested with in-game self-tests and on test rigs, but not yet in long survival play, and some models and textures are still being polished. **Back up your world first.** Bug reports, crash logs and screenshots are very welcome.

Pride Industry is now part of IR Extras (its factory machines are in the new *IR Extras: Factory* creative tab; old `prideindustry:*` blocks and items are remapped automatically, so existing worlds keep their machines). The full list of features is in [docs/FEATURES.md](https://github.com/crunkazcanbe/immersive-railroading-extras/blob/main/docs/FEATURES.md).

### Added - control center and signalling
- **Control Center Console**: a 3x2 dispatcher desk with a big live map screen and working buttons (select / hold / go / horn / send home, E-STOP ALL, page selector, zoom, CBTC key, ACK, lamp test, cancel routes). Replaces the Dispatcher Board. Needs mains (120/240 V AC) plus 12 V instrument power. The full Control Center now has 30 tabs.
- **Moving-block signalling (CBTC)**: trains on a CBTC line run on a live limit of movement authority from the train ahead, with a braking curve; rear-end protection for player-driven trains; a driver cab display with target speed and distance to go.
- Signal box with a real interlocked 12-lever frame you pull by hand; every lever, gauge and button drawn on a model now works.
- Unified alarm manager, Alarm Annunciator and an Event Recorder with replay.
- 50 more signalling and operations features: turntables and traversers, movable bridges, banking engines, loco changeover, automatic coupling and turnback, diamonds and single-line sections, buffer stops, retarders, trip cocks, balises, cab acknowledge, signal batteries, a route conflict visualizer, signal sighting checker, radio repeaters, signal isolation, route macros, a safety supervisor, driver competency, fault diagnosis and a panel builder.

### Added - power and wiring
- **12 V control power** for (almost) every machine: Instrument Wire from a 12 V battery charger / UPS to each machine's modelled terminal box. Big machines also need real mains from the grid. No power = dark screens and dead buttons.
- Instrument wiring: Gauge Panel, 16-colour Instrument Wire, Bundled Cable, Grid Tap, Radio TX/RX, Wire Chase Wall (hidden wiring in walls), wired control terminals and Control Button instrument.
- Traction load flow with regenerative braking; neutral sections, load sharing, wayside energy storage, OHLE fault locator and recording.
- Tower wind turbines (stackable sections, nacelle yaws into a drifting wind); sun-tracking solar array.
- Burn-outs: converters fed over their rating fry, transformers melt down after repeated overheat trips, motors burn on a second overload.
- OpenComputers SCADA: grid control on the Data Link, a dashboard disk and an Auto Controller block. A public Panel API so other mods can carry real control panels.
- 26 industrial parts and parts-based recipes for every power machine, instrument and tool.

### Added - stations, freight and the world
- Freight contracts and the Freight Office (working drawers, filing cabinet, cupboard and a display shelf that shows its items).
- Walking passengers, night lighting, a working loco cab desk, a realistic sound pack and station voice announcements, railway career quest events.
- Station shops and a daily newspaper, fire / ambulance / police services, a live web train map, and a model railway table.
- Walk-in train interiors (seats, cafe, sleeper, toilet, racks), machine cabinet doors that open onto working internals.
- Inspection drones with a dock; snowplough, sandite and de-icer trains with a weather depot.
- Economy, companies, research, insurance, a regulator and much more (asset lifecycle, digital-twin simulation, yard planner, intermodal port, microgrids, production planner, and others).
- **Pride OS desk computers**: notepad, calculator, paint, games (snake, minesweeper, 2048, solitaire), railway status, terminal, music, settings with PIN.
- **Real CCTV**: live camera feeds (with automatic fallback to snapshots when live video fails), 9 camera types, monitors that merge into video walls, NVR recording and playback, a CCTV control desk.
- Batch 5: yard gantry crane; mail trains, sorting office and travelling post office; steam servicing depot; battery and hydrogen traction; road traffic with traffic signals and trams; ropeways and funiculars; cinematic camera and director desk; crew NPCs and ticket inspectors.
- Batch 6 (30 features): track laying machine, tunnel boring machine, ballast tamper, locomotive works, hi-rail truck; Pride Card, phone app, left-luggage lockers, baggage, park and ride; bus station, taxis, hotel, pub with kitchen, boarding lift and gap filler; rack railway, train ferry, motorail, garden railway, avalanche galleries; RCO beltpack, 250 t breakdown crane, flange lubricators, master clock, driver training simulator; grain elevator, oil tank farm, warehouse with forklifts, parcel lockers, holiday trains.
- Detailed Blockbench models for all the railway gear (sources in `blockbench/`).
- Test commands: `/irextras track`, `stock`, `drive`, `fill`; most systems have a `selftest`.

### Fixed
- A bare `%` in a digital display format no longer crashes the game.
- Gantry crane anti-sway now settles under load.
- Autopilot direction and train protection on long track pieces.
- Pride OS desktop icon columns no longer overlap; desk controls no longer swallow sneak-clicks.
- Train map contrast and glow; model lettering (missing Z, J, Q, / and % glyphs).
- Burnt cables and failed breakers are removed after the grid solve, not during it (server crash).
- A network that ran out of energy comes back when its sources refill.

## 0.6.0-beta - 2026-10-07

**Beta / development version.** The big new systems were tested in game on test rigs but not yet in long survival play, and **textures and models are not final** (they're still being reviewed by hand). Back up your world first. Bug reports are very welcome.

### Added - real electricity (Pride Rail power grid)
- Voltage levels from 12 V DC to 69 kV plus 600 / 750 / 1500 / 3000 V DC and 25 kV traction; each network solved every second for P, Q, S, power factor, current, I²R losses, voltage drop, efficiency and conductor temperature. Losses are real energy.
- Power cables you choose: copper / aluminium (1-300 mm²) or superconductor, single / 2-core DC / 3φ+N+PE / motor cable / busbar, LV / MV / HV insulation; undersized cables overheat and burn out, under-insulated ones flash over. Each kind looks different.
- Inverter (DC→AC) and DC-DC converter; converters have real primary (back) and secondary sides, kVA ratings, wrong-input faults and 150 % overload trips.
- Generators run up to speed with live frequency, synchroscope and sync-check relay, AUTO synchronising, and under-frequency / overload / reverse-power trips that can cascade into blackouts.
- Electric Motor with direct-on-line, star-delta, soft-starter or VFD starting (inrush, voltage dip, stall and thermal overload), HAND / OFF / AUTO, driving any mod's Forge Energy machine from its shaft.
- Prospective short-circuit current per network, breaker breaking capacity (10-63 kA), selective tripping; a breaker that can't break the fault fails violently.
- Real control panels on every grid machine (only the controls the real equipment has), control-room instruments (volt / amp / watt / var / PF / Hz / temperature meters, screen, lamp), Multimeter / clamp meter, smart energy meter.
- Lockout padlock + danger tag, electrician's screwdriver (settings, off-circuit taps, zap when live), Unit Substation Kit.

### Added - railway
- Pride Rail: 40 trains (monorails, maglevs, subways, high-speed) with Real / Pride / Trans liveries, walk-through interiors, automatic doors, platform screen doors, whole-train items and `/irextras consist`.
- Monorail beams, maglev guideways and third rail as live power rails; redesigned overhead line.
- Orders and profit, Railway Control Center, maintenance and wear, 4 station styles, industries with supply chains, signal-box programming, NX route-setting desk, Settings Console on every block.

### Fixed
- Client disconnect with long train names; seat z-fighting; car gaps; flat lighting for moved lineside pieces; many model UV issues.
- A network that ran out of energy now comes back when its sources refill.
- Burnt-out cables / failed breakers no longer crash the server mid-solve.
- Cables never connect into a machine's control panel (old builds keep working).

All notable changes to Immersive Railroading Extras. This project is in **alpha**; versions
before 1.0 may change behaviour between releases.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.5.2] - 2026-09-21

### Added
- **Timetable planning layer.** A scheduler that plans services on paper before a train
  moves, so conflicts are found up front instead of on the rails.
  - `Timetable` — named services on a line, each with a first departure, a repeat headway
    and a list of calls (arrive/depart offsets from departure). Saved to NBT with the world.
  - `TimeSpaceMap` — reserves each piece of track for a time window and reports clashes,
    with a headway pad so two trains are never planned nose-to-tail.
  - `Scheduler` — plans a run, and when it clashes pushes the departure later and retries
    (up to 20 minutes, 24 attempts) rather than giving up. Also plans **meets**: where two
    opposing services share a stretch of single track, it picks a passing place, preferring
    the middle of the shared run.
  - `Recurrence` — the repeat maths on its own, with no Minecraft imports, so it can be
    reasoned about and tested directly.
  - Ships with 23 self-check assertions covering the scheduling invariants.
- **Gantry tower parts.** Build a signal gantry tower by hand, any shape you like:
  - **Gantry Tower Leg** — steel lattice, climbable like a ladder.
  - **Gantry Walkway** — grating you can actually stand on.
  - **Gantry Handrail** — uses a 1.5-block collision box, the same trick vanilla fences use,
    so Minecraft's Auto-Jump cannot hop over it and drop you off the deck.
- **Gantry Radio Mast** — a slim lineside mast with cross elements and a dish. Rotates to
  four facings, stacks for a taller mast, and is decoration only: it changes no signalling.

### Fixed
- The mod reported version `0.5.0` in the mod list while the jar was named `0.5.1`. The
  `@Mod` annotation and `mod_version` are now kept in step.

## [0.5.1] - 2026-09-15

### Changed
- First round of in-game testing. Signals, grade crossings, track circuits and the defect
  detector react to trains. The Dispatcher Board and Rail Map open and scan. Driverless
  trains stop at stations.

### Added
- `/irextras status` command.

## [0.5.0] - 2026-09-14

### Added
- First alpha release: driverless trains (line / shuttle / on-call / send), the ticket
  machine and tickets that call trains, CTC entrance–exit routes with interlocking, train
  protection, settable speed signs, the arrivals board, the talking defect detector, an
  OpenComputers `component.railroad` API, and Blockbench models throughout.
