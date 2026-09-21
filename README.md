# Immersive Railroading Extras

An add-on for **Immersive Railroading** on Minecraft **1.12.2** (Forge / Cleanroom) that turns a
set of tracks into a railroad that runs itself: driverless trains, ticket machines, American
signalling with real interlocking, train protection, a live dispatcher map, station arrivals
boards, talking defect detectors and an OpenComputers API.

> **Status: alpha (0.5.2).** Play-tested on a loop with a station: automatic block signals, grade
> crossings, track circuits, the defect detector, the dispatcher board, the handheld map, lines and
> driverless trains stopping at stations all work. Every block and item has been placed and used
> in-game (51 blocks, 3 items) with no exceptions; the Dispatcher Board, Display Panel and Ticket
> Machine screens all open. Tickets, CTC routes, train protection, the new timetable planner and
> OpenComputers still need more testing with real traffic.
> Bug reports, crash logs and pull requests are very welcome — see [Help wanted](#help-wanted).

---

## Features

### Driverless trains
Click any locomotive on the Dispatcher Board → **Make driverless…**
- **Line** — loops through a list of stations. **Shuttle** — end to end and back. **On call** —
  waits at home until a ticket calls it. **Send** — one trip to one station.
- Finds its own way along the real track: every junction is tried both ways, forward or
  reverse, so it can back out of a terminus.
- Locks and throws the switches ahead of it (never under another train, never against
  another route), clears CTC signals it owns, and drops them behind it.
- Reads the signals and speed signs facing it and brakes on a proper braking curve — stops
  short of red, slows for yellow, obeys posted limits.
- Drives with the normal throttle, brake and reverser (IR physics still applies), stops at the
  platform, rings the bell, runs IR item/fluid loaders at the station, waits, and whistles off.

### Tickets & the Ticket Machine
- Two-block-tall station kiosk (Blockbench model). Right-click for a touch screen: pick a
  destination, one way or round trip, pay the fare (configurable item, distance-based).
- Right-click the machine **holding a ticket** from that station: the ticket is punched and the
  dispatcher sends a train — a line train that serves both stations, or the nearest on-call train.
- Link platform signals with the Signal Wrench and they are held at Stop while a train boards.
- Redstone: pulse when a ticket is accepted, steady while a train is boarding.

### Timetables & planning
- Write a **timetable**: named services on a line, each with a first departure, a repeat headway
  and a call list (arrive/depart offsets). Stored with the world.
- The planner reserves track in **time and space** before anything moves, with a headway pad, so
  two services are never planned nose-to-tail. If a run clashes it pushes the departure later and
  retries instead of failing.
- **Meet planning** on single track: where two opposing services share a stretch, it picks a
  passing place, preferring the middle of the shared run.

### Build your own gantry tower
The gantry is made of parts, so the tower is yours to design rather than something the mod stamps
out for you:
- **Gantry Tower Leg** — steel lattice, climbable like a ladder.
- **Gantry Walkway** — grating you can stand on.
- **Gantry Handrail** — 1.5 blocks tall in collision (the same trick vanilla fences use) so
  Auto-Jump cannot hop it and drop you off the deck.
- **Gantry Radio Mast** — slim mast with cross elements and a dish. Four facings, stacks for a
  taller mast, decoration only.

### Signalling
- Six American signal families: color light, searchlight, PRR position light, B&O color
  position light, dwarf, semaphore. Automatic block signalling with 3-block aspects and
  diverging (medium) aspects on multi-head masts.
- Insulated joints, track circuits, relay cases, signal bridges (gantries), grade crossings
  with gates, lineside signs.
- **CTC route setting** (entrance–exit): on the board press *Set route*, click the start signal,
  then the end signal. Switches are lined and locked, the entrance signal clears, and the route
  releases itself after the train passes. Signals can be put in **CTC mode** so they hold Stop
  until a route is set.
- **Interlocking**: a locked switch can't be thrown by hand, by another route, or by another
  driverless train until it is released.

### Train protection (PTC)
Every train a player drives is watched: approaching a Stop signal or a speed sign too fast
triggers a penalty brake; reaching a Stop signal at speed is an emergency brake. The driver is
told why. Speed limit signs carry a settable mph value.

### Stations
- **Dispatcher Board** / **Rail Display Panel** wall / handheld **Rail Map**: live map of track,
  switches (click to throw), signals, stations, trains (click for a driving desk), block
  occupancy, timetable.
- **Lines** editor on the board: name a line and click stations in order.
- **Arrivals Board**: amber split-flap style display of the driverless trains due at the
  nearest station; boards placed side by side join into one wide board; chimes on arrival.
- **Defect Detector**: announces every passing train in chat, the way the real ones do —
  *"…milepost 12.4, track 1. No defects. Train speed 42. Total axles 36. Temperature 71 degrees.
  Detector out."*

### OpenComputers — `component.railroad`
Place a **Railroad Data Link** next to a computer:
```lua
local rr = require("component").railroad
for _, t in ipairs(rr.getTrains()) do print(t.id, t.name, t.speed, t.status) end
rr.getStations()  rr.getLines()  rr.getSignals()
rr.setSwitch(x, y, z, "turn")          -- "straight" | "turn" | "auto"
rr.holdSignal(x, y, z, true)
rr.setRoute(x1, y1, z1, x2, y2, z2)    rr.cancelRoute(x1, y1, z1)
rr.trainCommand(id, "horn")            -- throttle_up, brake_up, forward, emergency_stop, ...
rr.sendTrain(id, "Harbor")
rr.setAutopilot(id, "line", "Blue Line", 60, 20)   -- or "shuttle", "oncall", "off"
rr.ticket("Downtown", "Airport", "Alex")
rr.isProtected(id)
```

---

## Quick start
Keep blocks you place beside the track at least **2 blocks from the track centre** (outside the
train's width): rolling stock knocks out small blocks inside its own width. Insulated joints go on
the ground **beside** the rail, never under it (replacing the block under IR track breaks the track).

1. Place a **Dispatcher Board** near your track and open it.
2. Click a piece of track at each stop → type a station name → Save.
3. **Lines** → New line → name it → click the stations in order → Save line.
4. Click a locomotive → **Make driverless…** → Line → pick the line → Start driverless.
5. Put a **Ticket Machine** and an **Arrivals Board** at a station.

## Commands
`/irextras status` — trains being tracked, and every registered signal, crossing, bridge, speed sign
and joint with its current state, plus what a track scan from where you stand finds. Handy for
checking a layout, and please paste it into bug reports.

## Requirements
- Minecraft 1.12.2, Forge 14.23.5 or Cleanroom
- Immersive Railroading 1.10+ and **UniversalModCore 1.3.1+** (1.3.2 used for development)
- Optional: Land of Signals, Dynmap, OpenComputers 1.8.9

## Building
```
./gradlew build
```
Java 21 for Gradle; the mod targets Java 8 bytecode (modern syntax via Jabel). Put the
compile-only jars listed in [`libs/README.md`](libs/README.md) into `libs/` first.

Blockbench sources for the models are in [`blockbench/`](blockbench/) with the two helper
scripts used to paint details and convert `.bbmodel` → 1.12 block JSON.

## Help wanted
The things most likely to need real-world fixing:
- **Driverless trains** on unusual track (turntables, transfer tables, very tight curves, slopes).
- **Switch detection** — which leg is "turn" vs "straight" across IR versions/forks.
- Route searches through **unloaded chunks** (currently they simply fail).
- Braking tuning for heavy consists and steam locomotives.
- Multiplayer testing of tickets and routes.

Crash logs (`logs/latest.log`) and a description of the track layout help the most.

## Code map
`com.dogpound.railmap` — `auto/` (TrackPath, Wayside, Interlocking, Autopilot, Dispatcher,
Protection, RailwayData), `signal/` (masts, engine, joints, crossings, bridges, relay cases,
speed signs), `block/` (board, panels, ticket machine, arrivals board, defect detector, data
link), `scan/` (IR track walker), `client/` (map renderer, GUIs, TESRs), `network/`.
