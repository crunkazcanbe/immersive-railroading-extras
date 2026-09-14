import sys; sys.path.insert(0, 'tools')
from bbpaint import Model
M = Model('arrivals_board.bbmodel')
STEEL_HI, STEEL_LO, BLACK = (120, 128, 138), (18, 20, 24), (6, 7, 9)
for s in ('north', 'south', 'east', 'west', 'up', 'down'): M.fill('glass_glow', s, BLACK)
W, H = M.size('glass_glow', 'north')
for x in range(0, W, 7): M.rect('glass_glow', 'north', x, 0, 6, H, (12, 13, 16))
for part in ('frame_top', 'frame_bottom', 'frame_left', 'frame_right'):
    for s in ('north', 'up', 'down', 'east', 'west'):
        W, H = M.size(part, s)
        M.vgrad(part, s, '#3a404a', '#22262c')
        M.hline(part, s, 0, STEEL_HI)
W, H = M.size('frame_bottom', 'north')
M.rect('frame_bottom', 'north', 6, 4, 22, H - 8, (180, 132, 30)); M.text('frame_bottom', 'north', 'ARRIVALS', 8, max(0, (H - 7) // 2), (20, 16, 8)) if H >= 9 else None
for x in (3, W - 5):
    M.rect('frame_bottom', 'north', x, H // 2 - 1, 2, 2, (150, 158, 168))
    M.rect('frame_top', 'north', x, M.size('frame_top', 'north')[1] // 2 - 1, 2, 2, (150, 158, 168))
for s in ('east', 'west', 'up', 'down', 'south'):
    W, H = M.size('housing', s)
    M.rivets('housing', s, (90, 96, 104), 3)
    for x in range(6, W - 6, 5): M.rect('housing', s, x, H // 2, 3, 1, (12, 14, 17))
for s in ('up', 'north', 'down'):
    W, H = M.size('hood', s); M.vgrad('hood', s, '#2e333a', '#1a1d22'); M.hline('hood', s, 0, (70, 76, 84))
for part in ('ear_l', 'ear_r', 'wall_plate_l', 'wall_plate_r'):
    for s in ('north', 'south', 'east', 'west', 'up', 'down'):
        W, H = M.size(part, s); M.vgrad(part, s, '#b8c0c8', '#7a838c')
    W, H = M.size(part, 'north')
    if part.startswith('wall'):
        for y in (3, H - 5): M.rect(part, 'north', W // 2 - 1, y, 3, 3, (40, 44, 50))
for s in ('north', 'up', 'east', 'west', 'down', 'south'): M.fill('status_led_glow', s, (60, 255, 120))
M.save('../src/main/resources/assets/irextras/textures/blocks/arrivals_board.png')
print('ok')
