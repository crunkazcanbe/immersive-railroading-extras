# IR Extras — Build List (Bell's requests)

## ✅ Done
- Automatic level crossing: train-detected, flashing lamps, bell — VALIDATED with a live loco
- Gate arm was inverted (never blocked the road) — FIXED (drops horizontal when closed)
- Signs repainted (crossbuck ✕, milepost, speed, whistle W)
- Oversized items now fit their inventory slots
- 4 broken recipes fixed (id_plate, cable_drum, bollard, blank_board)

## 🚧 In progress — Crossing polish
- [ ] Click the GATE ARM itself (not just the post) to change settings — extend the clickable box along the arm
- [ ] Right-click cycles the approach distance (how close the train gets before it lowers) — ALREADY works on the post; extend to the arm + clearer feedback
- [ ] Redstone control: power the box next to the track (track circuit / relay case) → wire to the crossing → gates drop. ALREADY supported (crossing takes redstone in, outputs 15 when active). Verify + document the wiring, maybe add a direct link so no wire is needed.

## 📋 Queued features
- [ ] **Smart Station System** — live arrivals/departures board + PA speaker announcements (code exists, untested → test + polish)
- [ ] **CTC Dispatch Board** — interactive network map, live train positions, remote switch/signal control (code + GUI exist, untested → test + polish)
- [ ] **Station Furniture Pack** — benches, working clock, vending machines, lockers, luggage trolleys, platform lamps (build fresh in Blockbench)

## 🔒 Do LAST — Dispatch display: rich info
- [ ] Show a LOT more on the dispatch/display screen: every train's cargo, what's loaded, fill %, everything possible
- [ ] **Our own cargo-detection system** (NOT AE2) — sense what's loaded/unloaded from each train (freight items, tank liquid + level) and show it on the screen
