# Changelog

## 0.5.0
- Driverless trains: line, shuttle, on-call and send modes; route search through switches; switch locking; braking curves; station stops with loaders.
- Ticket Machine and Ticket item; passenger calls dispatch trains; platform signal holds.
- CTC entrance-exit route setting and interlocking; signal CTC mode.
- Train protection (penalty / emergency brake) for driven trains; settable speed limit signs.
- Arrivals Board, Defect Detector, Railroad Data Link (OpenComputers `component.railroad`).
- Blockbench models for the new station hardware.

## 0.4.x
- Renamed to Immersive Railroading Extras (`irextras`); signal bridges, relay cases, Signal Wrench, 3D lineside signs.

## 0.3.0
- American signalling: six signal families, automatic block signalling, crossings, track circuits, remote train control.

## 0.2.0
- Dispatcher Board, Rail Display Panel wall, handheld Rail Map, stations and timetable, Dynmap layer.

## [0.5.1] - 2026-09-15
First round of in-game testing on a loop with two stations.

### Fixed
- Signals, crossings, bridges, speed signs and joints were forgotten every time a world loaded, so none of them reacted to trains.
- Defect detectors, track circuits and signals saw no trains unless someone had a board open or a station was named.
- Defect detectors missed trains moving faster than a crawl (they only checked twice a second at one point).
- The Dispatcher Board and Rail Display Panel screens never opened.
- The Dispatcher Board never scanned its track (rescan timer overflow).
- The handheld Rail Map crashed when opening.
- Driverless trains never braked for stations: track pieces crossed "backwards" measured distance the wrong way. They also now allow for air-brake build-up and count a stop a few metres past the mark.
- Signals mid-way along a straight didn't appear on the map.
- A signal set to "Fixed aspect" couldn't be clicked back to Automatic.
- Train panel on the board overlapped its own buttons (EMERGENCY STOP was hidden).
- Station markers were very hard to click on the map.
- Insulated joints: detected beside the rail (up to 3 blocks from the centre line); the tooltip no longer says to put them under the rail.
- Position-light and colour-position-light heads were paper-thin from the side.
- Defect detector crafting recipe failed to load.

### Added
- `/irextras status` command.
