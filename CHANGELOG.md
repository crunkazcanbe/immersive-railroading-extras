# Changelog

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
