import sys; sys.path.insert(0, 'tools')
from bbpaint import Model
M = Model('data_link.bbmodel')
GREEN, AMBER = (60, 255, 120), (255, 190, 60)
# Case sides: vent slots, rack rails, OC-style circuit traces on top
for s in ('east', 'west'):
    W, H = M.size('case', s)
    for y in range(20, H - 20, 6): M.rect('case', s, 20, y, W - 40, 2, (14, 16, 19))
    M.rivets('case', s, (120, 128, 136), 4)
W, H = M.size('case', 'up')
for i, (x, y, w, h) in enumerate([(10, 20, 60, 2), (68, 20, 2, 40), (68, 58, 50, 2), (30, 100, 90, 2), (30, 60, 2, 42)]):
    M.rect('case', 'up', x, y, w, h, (40, 150, 90))
for px, py in ((10, 20), (116, 58), (30, 100), (118, 100)): M.rect('case', 'up', px - 1, py - 1, 4, 4, (200, 170, 60))
# Front panel: brushed with a rack ear line each side and screws
W, H = M.size('front_panel', 'north')
M.vgrad('front_panel', 'north', '#48505a', '#30363e')
for x in (6, W - 7): M.rect('front_panel', 'north', x, 4, 1, H - 8, (24, 27, 31))
for px, py in ((3, 3), (W - 5, 3), (3, H - 5), (W - 5, H - 5)): M.rect('front_panel', 'north', px, py, 2, 2, (170, 176, 184))
# Status screen: green terminal text lines like an OC monitor
c = 'screen_glow'; W, H = M.size(c, 'north')
M.fill(c, 'north', (8, 20, 12))
M.text(c, 'north', 'RR', 2, 2, GREEN)
for i, w in enumerate([50, 38, 60, 28]):
    if 12 + i * 5 < H - 1: M.rect(c, 'north', 2, 12 + i * 5, min(w, W - 4), 2, (40, 190, 90))
M.rect(c, 'north', W - 10, 3, 6, 5, AMBER)
# Ports: RJ-ish sockets with brass pins
c = 'port_row'; W, H = M.size(c, 'north')
M.fill(c, 'north', (20, 22, 26))
for k in range(6):
    x = 2 + k * ((W - 4) // 6)
    M.rect(c, 'north', x, 3, (W - 4) // 6 - 3, H - 6, (8, 9, 11)); M.rect(c, 'north', x + 2, 4, (W - 4) // 6 - 7, 2, (200, 170, 60))
# LEDs
c = 'led_row_glow'; W, H = M.size(c, 'north')
M.fill(c, 'north', (10, 12, 14))
for k in range(8):
    col = GREEN if k % 3 else AMBER
    M.rect(c, 'north', 2 + k * ((W - 4) // 8), 1, max(2, (W - 4) // 8 - 4), max(1, H - 2), col)
# Badge
c = 'badge'; W, H = M.size(c, 'north')
M.fill(c, 'north', (232, 226, 200)); M.ctext(c, 'north', 'DATA LINK', max(0, (H - 7) // 2), (30, 60, 40))
for s in ('north', 'south', 'east', 'west', 'up'): M.fill('antenna_tip_glow', s, (255, 60, 60))
M.save('../src/main/resources/assets/irextras/textures/blocks/data_link.png')
print('ok')
