# Changelog

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
