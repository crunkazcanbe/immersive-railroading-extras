import sys; sys.path.insert(0, 'tools')
from bbpaint import Model
M = Model('ticket_machine.bbmodel')
c = 'keypad_shelf'
for f in ('north', 'up'):
    W, H = M.size(c, f)
    M.vgrad(c, f, '#c3cbd3', '#8f98a2')
    cols, rows = 6, 3
    kw, kh = (W - 4) // cols - 1, max(2, (H - 3) // rows - 1)
    for r in range(rows):
        for k in range(cols):
            col = (28, 31, 36)
            if k == cols - 1: col = [(40, 170, 90), (230, 190, 40), (200, 50, 50)][r]
            x, y = 2 + k * (kw + 1), 2 + r * (kh + 1)
            M.rect(c, f, x, y, kw, kh, col)
            M.rect(c, f, x, y, kw, 1, (96, 102, 110) if k < cols - 1 else tuple(min(255, v + 50) for v in col))
W, H = M.size('back_panel', 'south')
M.rect('back_panel', 'south', W // 2 - 8, 24, 16, 10, (245, 247, 250))
for i in range(3): M.rect('back_panel', 'south', W // 2 - 6, 26 + i * 3, 12, 1, (17, 80, 156))
M.save('../src/main/resources/assets/irextras/textures/blocks/ticket_machine.png')
print('ok')
