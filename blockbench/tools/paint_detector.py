import sys; sys.path.insert(0, 'tools')
from bbpaint import Model
M = Model('defect_detector.bbmodel')
DARK, RUST = (92, 88, 76), (150, 110, 70)
# Cabinet: galvanized-beige sheet with vertical ribs, louvers on the sides, door outline
for s in ('east', 'west', 'south'):
    W, H = M.size('cabinet', s)
    for x in range(4, W - 2, 8): M.rect('cabinet', s, x, 2, 1, H - 4, (178, 171, 152))
    for y in range(8, 26, 4): M.rect('cabinet', s, W // 2 - 14, y, 28, 2, (70, 68, 60))
    M.rect('cabinet', s, 0, H - 3, W, 3, (140, 132, 112))          # splash dirt at the foot
W, H = M.size('door_seam', 'north')
M.vgrad('door_seam', 'north', '#d8d2bc', '#bdb59c')
M.rect('door_seam', 'north', 0, 0, W, 1, DARK); M.rect('door_seam', 'north', 0, H - 1, W, 1, DARK)
M.rect('door_seam', 'north', 0, 0, 1, H, DARK); M.rect('door_seam', 'north', W - 1, 0, 1, H, DARK)
M.rect('door_seam', 'north', 3, 6, 6, 1, (120, 116, 100)); M.rect('door_seam', 'north', 3, H - 8, 6, 1, (120, 116, 100))   # hinges
# Sign plate: DEFECT / DETECTOR, black on white with a red rule
c = 'sign_plate'; W, H = M.size(c, 'north')
M.fill(c, 'north', (244, 244, 240)); M.rect(c, 'north', 0, 0, W, 1, (30, 30, 30)); M.rect(c, 'north', 0, H - 1, W, 1, (30, 30, 30))
M.ctext(c, 'north', 'DETECTOR', max(1, (H - 7) // 2), (20, 20, 20))
M.rect(c, 'north', 3, H - 3, W - 6, 1, (200, 40, 40))
# Roof: standing seams + drip edge
for s in ('up',):
    W, H = M.size('roof', s)
    for x in range(3, W, 6): M.rect('roof', s, x, 0, 1, H, (120, 128, 134))
for s in ('north', 'south', 'east', 'west'):
    W, H = M.size('roof', s); M.hline('roof', s, H - 1, (70, 76, 82))
# Warning lamp: amber lens with a hot centre
for s in ('north', 'south', 'east', 'west', 'up'):
    W, H = M.size('lamp_glow', s); M.vgrad('lamp_glow', s, '#ffd24a', '#ff9a10'); M.rect('lamp_glow', s, W // 3, H // 3, max(1, W // 3), max(1, H // 3), (255, 245, 200))
# Solar panel: cells in a grid with a silver frame
c = 'solar_panel'
for s in ('up', 'down'):
    W, H = M.size(c, s)
    M.fill(c, s, (170, 178, 186) if s == 'down' else (22, 36, 66))
    if s == 'up':
        for x in range(0, W, 7): M.rect(c, s, x, 0, 1, H, (160, 170, 180))
        for y in range(0, H, 7): M.rect(c, s, 0, y, W, 1, (160, 170, 180))
        M.rect(c, s, 2, 2, 3, 2, (90, 130, 200))
# Pad: concrete with tie-down bolts and a crack
for s in ('up', 'north', 'east', 'west', 'south'):
    W, H = M.size('pad', s); M.hline('pad', s, 0, (170, 170, 166))
W, H = M.size('pad', 'up')
for px, py in ((3, 3), (W - 5, 3), (3, H - 5), (W - 5, H - 5)): M.rect('pad', 'up', px, py, 2, 2, (60, 62, 64))
for k in range(12): M.rect('pad', 'up', 30 + k * 2, 40 + (k % 3), 2, 1, (110, 110, 106))
# Mast + whip: galvanised with a black base band; yagi elements dark
for part in ('mast', 'whip'):
    for s in ('north', 'south', 'east', 'west'):
        W, H = M.size(part, s); M.vgrad(part, s, '#c5ccd2', '#8a939a')
W, H = M.size('mast', 'north')
for s in ('north', 'south', 'east', 'west'): M.rect('mast', s, 0, H - 10, M.size('mast', s)[0], 10, (40, 40, 40))
for part in ('yagi_boom', 'yagi_el_a', 'yagi_el_b', 'yagi_el_c'):
    for s in ('north', 'south', 'east', 'west', 'up', 'down'): M.fill(part, s, (60, 64, 70))
for s in ('north', 'south', 'east', 'west', 'up'): M.fill('track_cable', s, (22, 22, 22))
M.save('../src/main/resources/assets/irextras/textures/blocks/defect_detector.png')
print('ok')
