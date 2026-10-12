-- Pride Control: the IR Extras dashboard for OpenComputers.
-- Needs a Railroad Data Link (IR Extras) touching the computer (or an OC Adapter next to one).
-- Tabs: 1 MAP · 2 TRAINS · 3 POWER · 4 ALARMS · 5 WIRED (machines wired into the Data Link)   ·   mouse / touch for every button   ·   Q quits
-- No code needed: run `pridectl`. Want your own logic? Every button here is one line of Lua on
-- component.railroad (see `pridectl --help` or the Data Link's methods in the OC manual).

local component = require("component")
local event = require("event")
local computer = require("computer")
local term = require("term")
local unicode = require("unicode")

local args = {...}
if args[1] == "--help" or args[1] == "-h" then
  print([[pridectl - IR Extras dashboard
  local rr = require("component").railroad
  rr.getTrains()  rr.getStations()  rr.getSignals()  rr.getMap()
  rr.setSwitch(x,y,z,"turn"|"straight")  rr.holdSignal(x,y,z,true|false)
  rr.trainCommand(id,"horn"|"throttle_up"|"emergency_stop"...)  rr.sendTrain(id,"Station")
  rr.getGrid()  rr.getDevices([kind])  rr.getAlarms()
  rr.control(x,y,z,"close"|"open"|"reset"|"start"|"stop"|"gcbclose"...)
  rr.getWired()  rr.pressWired("Red"|14, "start"|"stop"|...)   -- machines wired in with Instrument Wire]])
  return
end

if not component.isAvailable("gpu") then io.stderr:write("pridectl needs a graphics card and a screen\n") return end
if not component.isAvailable("railroad") then
  io.stderr:write("No Railroad Data Link found: place an IR Extras Data Link next to this computer (or an Adapter).\n")
  return
end

local gpu = component.gpu
local rr = component.railroad
local W, H = gpu.getResolution()
local color = gpu.getDepth() > 1

-- palette ----------------------------------------------------------------------------------------------
local C = {
  bg = 0x101418, bar = 0x2A2F38, text = 0xE6E6E6, dim = 0x8A8F98, accent = 0xE040A0,
  ok = 0x40D060, warn = 0xF0B030, bad = 0xF04040, info = 0x40B8F0, track = 0x7A7F88, white = 0xFFFFFF,
  btn = 0x3A4250, btnOn = 0x2E7D32, btnBad = 0x8E2424,
}
local RAINBOW = {0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982}

local function fg(c) if color then gpu.setForeground(c) end end
local function bgc(c) if color then gpu.setBackground(c) end end
local function put(x, y, s, f, b)
  if b then bgc(b) end
  if f then fg(f) end
  if x <= W and y <= H and y >= 1 then gpu.set(x, y, unicode.sub(s, 1, math.max(0, W - x + 1))) end
end
local function fill(x, y, w, h, b, ch)
  bgc(b) gpu.fill(x, y, w, h, ch or " ")
end
local function pad(s, n)
  s = tostring(s or "")
  local l = unicode.len(s)
  if l >= n then return unicode.sub(s, 1, n) end
  return s .. string.rep(" ", n - l)
end
local function call(f, ...)
  local ok, a, b = pcall(f, ...)
  if not ok then return nil, tostring(a) end
  return a, b
end

-- state ------------------------------------------------------------------------------------------------
local tab = 1
local buttons = {}
local msg, msgColor = "Welcome to Pride Control", C.info
local scroll = {0, 0, 0, 0, 0}
local selTrain, selSignal = nil, nil
local ackd = {}
local lastAlarmCount = 0
local mapData, mapAt = nil, -100

local function say(s, c) msg, msgColor = s, c or C.info end
local function button(x, y, label, fn, b)
  label = " " .. label .. " "
  put(x, y, label, C.white, b or C.btn)
  buttons[#buttons + 1] = {x1 = x, x2 = x + unicode.len(label) - 1, y = y, fn = fn}
  return x + unicode.len(label) + 1
end

-- header / footer ----------------------------------------------------------------------------------------
local TABS = {"MAP", "TRAINS", "POWER", "ALARMS", "WIRED"}
local function header(alarms)
  fill(1, 1, W, 1, C.bar)
  for i = 1, math.min(6, W) do put(i, 1, " ", nil, RAINBOW[i]) end
  put(8, 1, "PRIDE CONTROL", C.accent, C.bar)
  local x = 23
  for i, t in ipairs(TABS) do
    local label = i .. " " .. t .. (i == 4 and alarms > 0 and (" (" .. alarms .. ")") or "")
    local b = (i == tab) and C.accent or (i == 4 and alarms > 0 and C.btnBad or C.btn)
    x = button(x, 1, label, function() tab = i end, b)
  end
  local clock = os.date("%H:%M")
  put(W - unicode.len(clock), 1, clock, C.dim, C.bar)
end
local function footer()
  fill(1, H, W, 1, C.bar)
  put(2, H, pad(msg, W - 12), msgColor, C.bar)
  button(W - 7, H, "QUIT", function() return "quit" end, C.btnBad)
end

-- 1 MAP --------------------------------------------------------------------------------------------------
local ASPECT = {clear = C.ok, approach = C.warn, caution = C.warn, stop = C.bad, danger = C.bad}
local function drawMap()
  local top, rows = 3, H - 5
  if computer.uptime() - mapAt > 5 or not mapData then
    local m, err = call(rr.getMap)
    if m then mapData, mapAt = m, computer.uptime() else say("map: " .. tostring(err), C.bad) end
  end
  local m = mapData
  if not m or not m.tracks or #m.tracks == 0 then put(3, top + 1, "No track found near the Data Link.", C.dim, C.bg) return end
  local b = m.bounds
  local dx, dz = math.max(1, b.maxX - b.minX), math.max(1, b.maxZ - b.minZ)
  local kx = math.min((W - 4) / dx, 2 * rows / dz)
  local kz = kx / 2
  local ox = 3 + math.floor(((W - 4) - dx * kx) / 2)
  local function sx(x) return ox + math.floor((x - b.minX) * kx) end
  local function sz(z) return top + math.floor((z - b.minZ) * kz) end
  for _, t in ipairs(m.tracks) do
    local p = t.points
    local col = t.switch and C.warn or C.track
    for i = 1, #p - 3, 2 do
      local x1, z1, x2, z2 = p[i], p[i + 1], p[i + 2], p[i + 3]
      local steps = math.max(1, math.floor(math.max(math.abs(x2 - x1) * kx, math.abs(z2 - z1) * kz * 2)))
      for s = 0, steps do
        local x, z = x1 + (x2 - x1) * s / steps, z1 + (z2 - z1) * s / steps
        put(sx(x), sz(z), "█", col, C.bg)
      end
    end
    if t.switch then
      local cx, cz = sx(t.x + 0.5), sz(t.z + 0.5)
      put(cx, cz, t.switch == "turn" and "◣" or "◆", C.warn, C.bg)
      buttons[#buttons + 1] = {x1 = cx - 1, x2 = cx + 1, y = cz, fn = function()
        local want = t.switch == "turn" and "straight" or "turn"
        local ok, err = call(rr.setSwitch, t.x, t.y, t.z, want)
        if ok then t.switch = want say("Switch at " .. t.x .. " " .. t.z .. " -> " .. want, C.ok) else say("switch refused: " .. tostring(err), C.bad) end
      end}
    end
  end
  for _, s in ipairs(m.stops or {}) do
    if s.name and s.name ~= "" then put(sx(s.x) + 1, sz(s.z), s.name, C.info, C.bg) end
  end
  for _, s in ipairs(m.signals or {}) do
    local x, z = sx(s.x + 0.5), sz(s.z + 0.5)
    put(x, z, "●", ASPECT[s.aspect] or C.dim, C.bg)
    buttons[#buttons + 1] = {x1 = x, x2 = x, y = z, fn = function() selSignal = s end}
  end
  local trains = call(rr.getTrains) or {}
  for _, t in ipairs(trains) do
    if t.lead ~= false then
      local x, z = sx(t.x), sz(t.z)
      put(x, z, "▶", C.white, C.accent)
      put(x + 1, z, " " .. unicode.sub(t.name or "?", 1, 10) .. " " .. math.floor(t.speed or 0), C.white, C.bg)
    end
  end
  if selSignal then
    local y = H - 2
    fill(1, y, W, 1, C.bar)
    local x = put(2, y, "Signal " .. selSignal.x .. " " .. selSignal.y .. " " .. selSignal.z .. " (" .. tostring(selSignal.aspect) .. ")", C.text, C.bar) or 40
    x = button(40, y, "HOLD AT STOP", function() local ok, e = call(rr.holdSignal, selSignal.x, selSignal.y, selSignal.z, true) say(ok and "Signal held at Stop" or ("refused: " .. tostring(e)), ok and C.warn or C.bad) mapAt = -100 end, C.btnBad)
    x = button(x, y, "RELEASE", function() local ok, e = call(rr.holdSignal, selSignal.x, selSignal.y, selSignal.z, false) say(ok and "Signal released" or ("refused: " .. tostring(e)), ok and C.ok or C.bad) mapAt = -100 end, C.btnOn)
    button(x, y, "X", function() selSignal = nil end)
  else
    put(2, H - 2, "Tap a ◆ switch to throw it, a ● signal to hold / release it.", C.dim, C.bg)
  end
end

-- 2 TRAINS -----------------------------------------------------------------------------------------------
local COMMANDS = {{"HORN", "horn"}, {"BELL", "bell"}, {"THR+", "throttle_up"}, {"THR-", "throttle_down"}, {"BRK+", "brake_up"},
  {"BRK-", "brake_down"}, {"FWD", "forward"}, {"NEU", "neutral"}, {"REV", "reverse"}}
local function drawTrains()
  local trains = call(rr.getTrains) or {}
  local lead = {}
  for _, t in ipairs(trains) do if t.lead ~= false then lead[#lead + 1] = t end end
  put(2, 3, pad("TRAIN", 18) .. pad("KM/H", 6) .. pad("STATUS", 18) .. "NEXT STOP", C.dim, C.bg)
  local rows = H - 9
  for i = 1, rows do
    local t = lead[i + scroll[2]]
    if not t then break end
    local y = 3 + i
    local b = (selTrain == t.id) and C.bar or C.bg
    put(2, y, pad(t.name or ("#" .. t.id), 18), C.text, b)
    put(20, y, pad(math.floor(t.speed or 0), 6), (t.speed or 0) > 0.5 and C.ok or C.dim, b)
    put(26, y, pad(t.status or "", 18), t.protected and C.bad or C.text, b)
    put(44, y, pad(t.nextStop or "", W - 44), C.info, b)
    buttons[#buttons + 1] = {x1 = 1, x2 = W, y = y, fn = function() selTrain = t.id end}
  end
  if #lead == 0 then put(2, 5, "No trains loaded.", C.dim, C.bg) end
  if selTrain then
    local y = H - 4
    fill(1, y, W, 3, C.bar)
    local x = 2
    for _, c in ipairs(COMMANDS) do
      x = button(x, y, c[1], function() local r = call(rr.trainCommand, selTrain, c[2]) say(tostring(r or "sent"), C.info) end)
    end
    button(x, y, "E-STOP", function() local r = call(rr.trainCommand, selTrain, "emergency_stop") say(tostring(r or "EMERGENCY STOP"), C.bad) end, C.btnBad)
    put(2, y + 1, "Send to:", C.dim, C.bar)
    x = 11
    for _, s in ipairs(call(rr.getStations) or {}) do
      if x + unicode.len(s.name) + 3 > W then break end
      x = button(x, y + 1, s.name, function() local r = call(rr.sendTrain, selTrain, s.name) say("Send to " .. s.name .. ": " .. tostring(r), C.ok) end)
    end
  end
end

-- 3 POWER ------------------------------------------------------------------------------------------------
local DEVICE_BUTTONS = {
  breaker = {{"CLOSE", "close", C.btnOn}, {"OPEN", "open", C.btnBad}, {"RESET", "reset"}},
  diesel = {{"START", "start", C.btnOn}, {"STOP", "stop", C.btnBad}, {"SYNC+CLOSE", "gcbclose"}, {"GCB OPEN", "gcbopen"}},
  turbine = {{"START", "start", C.btnOn}, {"STOP", "stop", C.btnBad}, {"SYNC+CLOSE", "gcbclose"}, {"GCB OPEN", "gcbopen"}},
  motor = {{"START", "start", C.btnOn}, {"STOP", "stop", C.btnBad}},
  battery = {{"ON", "start", C.btnOn}, {"OFF", "stop", C.btnBad}},
  rectifier = {{"DC CLOSE", "dcclose", C.btnOn}, {"DC OPEN", "dcopen", C.btnBad}},
  inverter = {{"RUN/STOP", "run"}}, dcdc = {{"RUN/STOP", "run"}},
}
local function drawPower()
  local nets = call(rr.getGrid) or {}
  put(2, 3, pad("NET", 5) .. pad("LEVEL", 14) .. pad("VOLTS", 8) .. pad("HZ", 7) .. pad("KW", 9) .. pad("PF", 6) .. "STATUS", C.dim, C.bg)
  local y = 4
  for i = 1, math.min(#nets, math.floor((H - 8) / 2)) do
    local n = nets[i]
    put(2, y, pad("#" .. n.id, 5) .. pad(n.level or "-", 14) .. pad(n.volts or "-", 8) .. pad(n.hz or "-", 7) .. pad(n.kw or 0, 9) .. pad(n.pf or "-", 6), C.text, C.bg)
    put(51, y, pad(n.live and "LIVE" or n.problem, W - 51), n.live and C.ok or C.bad, C.bg)
    y = y + 1
  end
  y = y + 1
  put(2, y, "CONTROLS", C.accent, C.bg)
  y = y + 1
  local devs = {}
  for _, d in ipairs(call(rr.getDevices) or {}) do if DEVICE_BUTTONS[d.kind] then devs[#devs + 1] = d end end
  for i = 1 + scroll[3], #devs do
    if y > H - 2 then put(W - 18, H - 1, "scroll for more ▼", C.dim, C.bg) break end
    local d = devs[i]
    local state = d.state or (d.running ~= nil and (d.running and "running" or "stopped")) or (d.on ~= nil and (d.on and "on" or "off")) or ""
    local sc = (state == "closed" or state == "running" or state == "on") and C.ok or (state == "tripped" or state == "overload tripped") and C.bad or C.warn
    put(2, y, pad(d.name or d.kind, 18) .. pad(d.x .. " " .. d.y .. " " .. d.z, 16), C.text, C.bg)
    put(36, y, pad(state .. (d.remote == false and " (LOCAL key)" or ""), 22), sc, C.bg)
    local x = 59
    for _, b in ipairs(DEVICE_BUTTONS[d.kind]) do
      if x + unicode.len(b[1]) + 2 > W then break end
      x = button(x, y, b[1], function()
        local ok, r = call(rr.control, d.x, d.y, d.z, b[2])
        say((d.name or d.kind) .. ": " .. tostring(r), ok and C.ok or C.bad)
      end, b[3])
    end
    y = y + 1
  end
end

-- 4 ALARMS -----------------------------------------------------------------------------------------------
local function drawAlarms(alarms)
  if #alarms == 0 then put(3, 4, "✔ All clear - every network live, nothing tripped.", C.ok, C.bg) return end
  local y = 3
  for i = 1 + scroll[4], #alarms do
    if y > H - 3 then break end
    put(2, y, (ackd[alarms[i]] and "  " or "! ") .. alarms[i], ackd[alarms[i]] and C.dim or C.bad, C.bg)
    y = y + 1
  end
  button(2, H - 2, "ACK ALL", function() for _, a in ipairs(alarms) do ackd[a] = true end say("Alarms acknowledged", C.dim) end, C.btn)
end

-- 5 WIRED: machines with Instrument Wire run from their control terminal into the Data Link ----------------
local WIRE_RGB = {0xF0F0F0, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
  0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21}
local function drawWired()
  local ms, err = call(rr.getWired)
  if not ms then put(3, 4, "This Data Link has no getWired (update IR Extras): " .. tostring(err), C.bad, C.bg) return end
  if #ms == 0 then
    put(3, 4, "Nothing wired in yet.", C.warn, C.bg)
    put(3, 6, "1. Right-click a machine with Instrument Wire: the colour lands on its control terminal.", C.dim, C.bg)
    put(3, 7, "2. Lay that colour (or a Bundled Cable) from the machine to this Data Link.", C.dim, C.bg)
    put(3, 8, "   One colour per machine - up to 16 machines on one Bundled Cable.", C.dim, C.bg)
    return
  end
  put(2, 3, pad("WIRE", 12) .. pad("MACHINE", 24) .. pad("STATE", 12) .. "READING", C.dim, C.bg)
  local y = 4
  for i = 1 + scroll[5], #ms do
    if y > H - 3 then put(W - 18, H - 1, "scroll for more ▼", C.dim, C.bg) break end
    local m = ms[i]
    put(2, y, "■", WIRE_RGB[(m.colour or 0) + 1], C.bg)
    put(4, y, pad(m.wire, 10), C.text, C.bg)
    put(14, y, pad(m.name, 23), C.white, C.bg)
    local st = m.state or (m.running and "running" or "stopped")
    local sc = m.lamp == 1 and C.ok or m.lamp == 3 and C.bad or C.warn
    put(38, y, pad(st, 11), sc, C.bg)
    local rd = {}
    if m.temp then rd[#rd + 1] = m.temp .. "°C" end
    if m.progress then rd[#rd + 1] = m.progress .. "%" end
    if m.made then rd[#rd + 1] = "made " .. m.made end
    if m.fluidOut then rd[#rd + 1] = m.fluidOut end
    if m.volts then rd[#rd + 1] = m.volts .. " V" end
    if m.amps then rd[#rd + 1] = m.amps .. " A" end
    if m.kw then rd[#rd + 1] = m.kw .. " kW" end
    if m.fault and m.fault ~= "" then rd[#rd + 1] = "! " .. m.fault end
    put(50, y, pad(table.concat(rd, "  "), W - 50), (m.fault and m.fault ~= "") and C.bad or C.text, C.bg)
    y = y + 1
    local x = 6
    for _, id in ipairs(m.controls or {}) do
      local lbl = string.upper(id)
      if x + unicode.len(lbl) + 2 > W then y = y + 1 x = 6 end
      if y > H - 3 then break end
      local col = (id == "stop" or id == "estop" or id == "trip" or id == "open") and C.btnBad or (id == "start" or id == "close" or id == "run") and C.btnOn or C.btn
      x = button(x, y, lbl, function()
        local ok, r = call(rr.pressWired, m.colour, id)
        say(m.name .. " (" .. m.wire .. "): " .. tostring(r), ok and C.ok or C.bad)
      end, col) + 1
    end
    y = y + 2
  end
end

-- main loop ----------------------------------------------------------------------------------------------
local function draw()
  buttons = {}
  local alarms = call(rr.getAlarms) or {}
  local unacked = 0
  for _, a in ipairs(alarms) do if not ackd[a] then unacked = unacked + 1 end end
  if unacked > lastAlarmCount then computer.beep(880, 0.15) computer.beep(660, 0.15) say("NEW ALARM - see tab 4", C.bad) end
  lastAlarmCount = unacked
  fill(1, 1, W, H, C.bg)
  header(unacked)
  if tab == 1 then drawMap() elseif tab == 2 then drawTrains() elseif tab == 3 then drawPower() elseif tab == 4 then drawAlarms(alarms) else drawWired() end
  footer()
end

local running = true
local oldBg, oldFg = gpu.getBackground(), gpu.getForeground()
while running do
  local ok, err = pcall(draw)
  if not ok then fill(1, 1, W, H, C.bg) put(2, 2, "pridectl error: " .. tostring(err), C.bad, C.bg) end
  local e = {event.pull(2)}
  if e[1] == "touch" then
    local x, y = e[3], e[4]
    for _, b in ipairs(buttons) do
      if y == b.y and x >= b.x1 and x <= b.x2 then
        if b.fn() == "quit" then running = false end
        break
      end
    end
  elseif e[1] == "scroll" then
    scroll[tab] = math.max(0, scroll[tab] - e[5])
  elseif e[1] == "key_down" then
    local ch = e[3]
    if ch >= 49 and ch <= 53 then tab = ch - 48
    elseif ch == 113 or ch == 81 then running = false
    elseif e[4] == 200 then scroll[tab] = math.max(0, scroll[tab] - 1)
    elseif e[4] == 208 then scroll[tab] = scroll[tab] + 1 end
  elseif e[1] == "interrupted" then
    running = false
  end
end
gpu.setBackground(oldBg) gpu.setForeground(oldFg)
term.clear()
print("Pride Control closed. Run `pridectl` to open it again.")
