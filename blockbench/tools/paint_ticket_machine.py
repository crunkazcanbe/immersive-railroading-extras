import sys; sys.path.insert(0, 'tools')
from bbpaint import Model
M = Model('ticket_machine.bbmodel')
NAVY_D, NAVY_L, STEEL_D, STEEL_L = (18, 36, 62), (46, 82, 128), (104, 112, 122), (198, 206, 214)
BLACK, WHITE, GOLD, BLUE, GREEN, CYAN = (14, 16, 20), (245, 247, 250), (255, 194, 58), (17, 80, 156), (31, 168, 90), (98, 214, 255)

# --- touch screen: the same kiosk UI the GUI shows ---
c, f = 'screen_glow', 'north'
W, H = M.size(c, f)
M.vgrad(c, f, '#1466c4', '#07275a')
M.rect(c, f, 0, 0, W, 8, (7, 42, 92)); M.rect(c, f, 0, 8, W, 1, GOLD)
M.text(c, f, 'RAIL', 2, 1, WHITE)
M.rect(c, f, W - 16, 3, 13, 2, GOLD)
for i, sel in enumerate([False, True, False, False]):
    y = 12 + i * 8
    M.rect(c, f, 2, y, 30, 6, GOLD if sel else BLUE)
    M.rect(c, f, 4, y + 2, 16, 2, (27, 19, 0) if sel else WHITE)
    M.rect(c, f, 27, y + 2, 3, 2, (27, 19, 0) if sel else GOLD)
M.rect(c, f, 35, 12, W - 38, 2, (157, 182, 216))
M.rect(c, f, 35, 17, (W - 38) // 2 - 1, 5, GOLD); M.rect(c, f, 35 + (W - 38) // 2 + 1, 17, (W - 38) // 2 - 1, 5, BLUE)
M.rect(c, f, 35, 26, 14, 2, GOLD); M.rect(c, f, 35, 30, 10, 2, (157, 182, 216))
M.rect(c, f, 35, 36, W - 38, 9, GREEN); M.rect(c, f, 38, 39, W - 44, 3, WHITE)
M.rect(c, f, 2, H - 4, W - 20, 1, (157, 182, 216))
M.rect(c, f, W - 6, 2, 1, 1, (255, 255, 255))                 # glare
for k in range(10): M.rect(c, f, W - 12 + k // 2, 10 + k * 3, 1, 3, (60, 130, 215))

M.fill('screen_bezel', 'north', BLACK)
M.rect('screen_bezel', 'north', 0, 0, M.size('screen_bezel', 'north')[0], 1, (40, 44, 52))

# --- lit header sign ---
c, f = 'header_sign_glow', 'north'
W, H = M.size(c, f)
M.vgrad(c, f, '#ffffff', '#dfe8f5')
M.rect(c, f, 0, 0, 5, H, BLUE); M.rect(c, f, W - 5, 0, 5, H, BLUE)
M.ctext(c, f, 'TICKETS', (H - 7) // 2, (11, 58, 120))
for side in ('east', 'west', 'up', 'down', 'south'):
    M.fill(c, side, (220, 230, 245))

# --- header cap: chrome edge ---
for side in ('north', 'east', 'west', 'south'):
    W, H = M.size('header', side)
    M.hline('header', side, H - 1, (150, 158, 168)); M.hline('header', side, 0, (48, 54, 62))

# --- beacon lens: green with ribs ---
for side in ('up', 'north', 'south', 'east', 'west'):
    W, H = M.size('beacon_glow', side)
    M.vgrad('beacon_glow', side, '#7dffa6', '#1fd65a')
    for x in range(2, W, 4): M.rect('beacon_glow', side, x, 0, 1, H, (30, 190, 80))

# --- speaker grille ---
W, H = M.size('speaker', 'north'); M.fill('speaker', 'north', (30, 33, 38))
for x in range(1, W, 2):
    for y in range(1, H, 2): M.rect('speaker', 'north', x, y, 1, 1, BLACK)

# --- card reader, coin slot, printer slot ---
W, H = M.size('card_slot', 'north'); M.fill('card_slot', 'north', (32, 36, 42))
M.rect('card_slot', 'north', 2, H // 2, W - 4, 1, BLACK); M.rect('card_slot', 'north', W - 4, 1, 2, 2, (60, 220, 110))
for s in ('north', 'south', 'east', 'west', 'up', 'down'): M.fill('card_slot_glow', s, CYAN)
W, H = M.size('coin_slot', 'north'); M.vgrad('coin_slot', 'north', '#c9d1d9', '#8a939c')
M.rect('coin_slot', 'north', W // 2, 1, 1, H - 2, BLACK)
W, H = M.size('printer_slot', 'north'); M.fill('printer_slot', 'north', (26, 29, 34))
M.rect('printer_slot', 'north', 1, 0, W - 2, 1, (70, 76, 84))
for s in ('north', 'south', 'east', 'west', 'up', 'down'): M.fill('printer_slot_glow', s, CYAN)

# --- keypad: brushed steel shelf, 3x4 black keys + green OK / red CANCEL ---
for s in ('north', 'up', 'east', 'west'):
    M.vgrad('keypad_shelf', s, '#c3cbd3', '#8f98a2'); M.streaks('keypad_shelf', s, (150, 158, 166), (205, 212, 220), 10, 3)
for s in ('up', 'north'):
    W, H = M.size('keypad_keys', s); M.fill('keypad_keys', s, (70, 76, 84))
    cols, rows = 5, max(1, H // 3)
    kw = max(2, (W - 2) // cols - 1)
    for r in range(rows):
        for k in range(cols):
            col = (24, 27, 32)
            if k == cols - 1: col = (40, 170, 90) if r == 0 else (200, 50, 50)
            M.rect('keypad_keys', s, 1 + k * (kw + 1), r * 3, kw, 2, col)
            M.rect('keypad_keys', s, 1 + k * (kw + 1), r * 3, kw, 1, (90, 96, 104) if k < cols - 1 else col)

# --- tray and brand plate ---
M.fill('ticket_tray', 'up', (28, 31, 36)); M.fill('ticket_tray', 'north', (88, 96, 104))
for s in ('north', 'up'): M.vgrad('tray_lip', s, '#b9c1c9', '#7b848d')
W, H = M.size('brand_plate', 'north'); M.vgrad('brand_plate', 'north', '#d6dce2', '#98a1aa')
M.ctext('brand_plate', 'north', 'IRX', max(0, (H - 7) // 2), BLUE)

# --- cabinet front: service door outline, screws; sides: panel seams and a train pictogram ---
W, H = M.size('body', 'north')
M.rect('body', 'north', 5, H - 60, W - 10, 1, NAVY_D); M.rect('body', 'north', 5, H - 8, W - 10, 1, NAVY_D)
M.rect('body', 'north', 5, H - 60, 1, 52, NAVY_D); M.rect('body', 'north', W - 6, H - 60, 1, 52, NAVY_D)
M.rect('body', 'north', W - 12, H - 36, 3, 6, (160, 168, 176))       # door handle
for s in ('east', 'west'):
    W, H = M.size('body', s)
    for y in (H // 3, 2 * H // 3): M.hline('body', s, y, NAVY_D, 3, W - 6)
    M.rivets('body', s, (140, 150, 160), 3)
    # little white train pictogram near the bottom
    bx, by = W // 2 - 10, H - 26
    M.rect('body', s, bx, by, 20, 8, WHITE); M.rect('body', s, bx + 2, by + 2, 4, 3, NAVY_D); M.rect('body', s, bx + 8, by + 2, 4, 3, NAVY_D)
    M.rect('body', s, bx + 14, by + 2, 4, 3, NAVY_D); M.rect('body', s, bx + 3, by + 8, 3, 2, WHITE); M.rect('body', s, bx + 14, by + 8, 3, 2, WHITE)
    M.rect('body', s, bx - 4, by + 11, 28, 1, WHITE)
for s in ('east', 'west'):
    W, H = M.size('side_stripe_l', s)
    M.fill('side_stripe_l', s, (255, 179, 0)); M.fill('side_stripe_r', s, (255, 179, 0))
    for y in range(0, H, 6):
        M.rect('side_stripe_l', s, 0, y, W, 2, (230, 150, 0)); M.rect('side_stripe_r', s, 0, y, W, 2, (230, 150, 0))
# corner posts brushed
for cpost in ('corner_post_l', 'corner_post_r'):
    for s in ('north', 'east', 'west', 'south'):
        M.vgrad(cpost, s, '#d3d9df', '#8b949d'); M.streaks(cpost, s, (150, 158, 166), (225, 230, 236), 3, 7)
# back service panel + vent louvers
W, H = M.size('back_panel', 'south')
M.rect('back_panel', 'south', 6, 10, W - 12, 1, NAVY_D); M.rect('back_panel', 'south', 6, H - 10, W - 12, 1, NAVY_D)
M.rect('back_panel', 'south', W // 2 - 8, 24, 16, 10, WHITE); M.text('back_panel', 'south', 'IRX', W // 2 - 8, 25, BLUE)
M.rect('back_panel', 'south', W - 14, H // 2, 4, 4, (170, 176, 184))
W, H = M.size('vent', 'south'); M.fill('vent', 'south', (40, 44, 50))
for y in range(1, H, 3): M.rect('vent', 'south', 1, y, W - 2, 1, BLACK)
# plinth concrete: top chamfer highlight, bolts
for s in ('north', 'south', 'east', 'west'):
    W, H = M.size('plinth', s); M.hline('plinth', s, 0, (140, 144, 150)); M.rect('plinth', s, 3, H // 2, 2, 2, (40, 42, 46)); M.rect('plinth', s, W - 5, H // 2, 2, 2, (40, 42, 46))
M.save('../src/main/resources/assets/irextras/textures/blocks/ticket_machine.png')
print('painted')
