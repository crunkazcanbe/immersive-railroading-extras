#!/usr/bin/env python3
"""Train and vehicle systems (her ChatGPT list section 15, items 106-110): the Formation & Livery Desk - a depot
planning desk beside a siding (vehicle/TileFormationDesk). Three blocks wide like the economy desks: the consist board
on the back wall shows the selected formation car by car (lead cab and powered-unit lamps), the livery swatch board,
the monitor (click it for the Formation Editor), the control strip, drawers, the paint store with its shelf.
Models face north; controls are placed by u (viewer-left -> right): x = 16 - u.
Also writes the blockstate, item model, recipe and lang lines."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, led_board, desk, strip, buttons, hood, monitor, cupboard_shelf, X

SPOTS = {}


def formation_desk():
    m = Model('formation_desk')
    back_wall(m, panel='#2c3b2a', plaster='#e8ecdf')

    # ---- the consist board: the selected formation, car by car
    m.g('consist board')
    led_board(m, 1.0, 30, 11.6, 28.6)
    m.box((4, 29.0, 14.7), (27, 31.0, 15.4), 'paint:#16241a', 'board header')
    m.box((4.4, 29.2, 14.62), (26.6, 30.8, 14.7), 'label:FORMATIONS', 'header lettering', north='plate:TRAIN FORMATIONS')
    b = m.surface('north', face=14.42, title='CONSIST BOARD')
    m.ctl(b, 'digits', 'title', X(15.5), 27.4, w=27, h=1.3, col='ffd040', label='', fmt='%s')
    for r in range(1, 9):
        v = 25.4 - (r - 1) * 1.75
        m.ctl(b, 'lamp', 'lead%d' % r, X(2.2), v, w=0.6, h=0.6, col='ffd040', label='')
        m.ctl(b, 'lamp', 'pow%d' % r, X(3.3), v, w=0.6, h=0.6, col='30e060', label='')
        m.ctl(b, 'digits', 'r%d' % r, X(13.0), v, w=17.6, h=1.4, col='7cf29a', label='', fmt='%s')
    # the little side-on train painted along the bottom of the board
    m.box((22.4, 12.2, 14.36), (29.4, 12.3, 14.42), 'paint:#8a8a8a', 'painted rail')
    for i, (x0, x1, c) in enumerate(((22.6, 24.4, '#e0a030'), (24.6, 26.4, '#5bcefa'), (26.6, 28.4, '#5bcefa'), (28.6, 29.2, '#d0d040'))):
        m.box((x0, 12.3, 14.34), (x1, 13.6 if i == 0 else 13.3, 14.42), 'paint:' + c, 'painted car')
    m.box((15.0, 12.0, 14.38), (21.6, 15.4, 14.42), 'paint:#0a120c', 'key panel')
    m.box((15.2, 14.4, 14.36), (21.4, 15.2, 14.38), 'label:LEAD = AMBER', 'key text', north='label:LEAD AMBER')
    m.box((15.2, 13.4, 14.36), (21.4, 14.2, 14.38), 'label:POWER = GREEN', 'key text', north='label:POWER GREEN')
    m.box((15.2, 12.4, 14.36), (21.4, 13.2, 14.38), 'label:> FORWARD < BACK', 'key text', north='label:> FWD < REV')

    # ---- livery swatch board: real painted sample panels
    m.g('livery swatches')
    m.box((-14, 12.0, 14.9), (0, 29.0, 15.4), 'paint:#f4f6f0', 'swatch board')
    m.box((-13.6, 27.0, 14.84), (-0.4, 28.6, 14.9), 'label:LIVERIES', 'swatch title', north='plate:LIVERY SAMPLES')
    cols = ['#8e24aa', '#1e88e5', '#43a047', '#e53935', '#fb8c00', '#fdd835', '#00897b', '#3949ab', '#6d4c41', '#546e7a', '#d81b60', '#c0ca33']
    for i, c in enumerate(cols):
        x = -13.2 + (i % 4) * 3.2
        y = 22.6 - (i // 4) * 3.4
        m.box((x, y, 14.8), (x + 2.6, y + 2.8, 14.9), 'paint:' + c, 'paint sample')
        m.box((x + 0.2, y + 2.0, 14.78), (x + 2.4, y + 2.6, 14.8), 'paint:#f4f6f0', 'sample label strip')
        m.bolt(x + 1.3, y + 2.75, 14.8, 'north', 0.25)
    m.box((-13.2, 12.6, 14.8), (-0.8, 14.6, 14.9), 'paint:#3a3a3a', 'weathering strip')
    for i in range(6):
        g = 0x3a + i * 0x18
        m.box((-13.0 + i * 2.0, 12.8, 14.76), (-11.2 + i * 2.0, 14.4, 14.8), 'paint:#%02x%02x%02x' % (min(255, g + 40), min(255, g + 24), g), 'weathering step')
    m.box((-13.2, 14.8, 14.78), (-0.8, 15.6, 14.8), 'label:CLEAN - WEATHERED', 'strip label', north='label:CLEAN > WEATHERED')

    # ---- the drawers and the paint store with its shelf of tins
    SPOTS['formation_desk'] = cupboard_shelf(m, -13.4, -2.6, ['cup1'], 5, title='PAINT SHELF', body='#2c3b2a', door='#3e5238')
    m.box((-13.0, 3.0, 11.36), (-3.2, 3.8, 11.4), 'danger:FLAMMABLE', 'flammable sign', north='danger:FLAMMABLE')
    desk(m, {('l', 0): 'dl1', ('l', 1): 'dl2', ('r', 0): 'dr1'}, body='#2c3b2a', front='#3e5238', pull='metal:alu')

    # ---- the control strip
    sf = strip(m, 3.6, 8.4, col='#1e2622')
    buttons(m, sf, 5.0, [('prev', '1a1a1a', 'PREV'), ('next', '1a1a1a', 'NEXT'), ('new', 'f0f0f0', 'NEW'), ('del', 'd02020', 'DEL'),
                         ('read', '2050d0', 'READ'), ('check', 'f0d020', 'CHECK')], u0=1.2, step=1.3)
    buttons(m, sf, 7.6, [('edit', '20a0a0', 'EDIT'), ('apply', '1fa34a', 'APPLY'), ('paint', '8e24aa', 'PAINT'), ('restore', 'e08020', 'RESTORE'),
                         ('place', 'b02020', 'PLACE')], u0=1.2, step=1.3)
    m.ctl(sf, 'selector', 'len', 9.4, 5.2, w=1.2, h=1.2, col='202020', label='SIDING', pos=['ANY', '100', '150', '200', '300', '400', '600'])
    m.ctl(sf, 'selector', 'grade', 11.0, 5.2, w=1.2, h=1.2, col='202020', label='GRADE', pos=['0.5', '1.0', '1.5', '2.0', '2.5', '3.0'])
    m.ctl(sf, 'selector', 'livsel', 12.6, 5.2, w=1.2, h=1.2, col='202020', label='LIVERY', pos=['BUILDER', 'COMPANY'])
    m.ctl(sf, 'key', 'bkey', 14.4, 5.2, w=1.3, h=1.3, col='c0a040', label='BUILD', pos=['SAFE', 'BUILD'])
    m.ctl(sf, 'digits', 'lenv', 9.6, 7.6, w=2.0, h=0.8, col='8ad4ff', label='', fmt='%s')
    m.ctl(sf, 'digits', 'gradev', 11.8, 7.6, w=1.8, h=0.8, col='8ad4ff', label='', fmt='%s')
    m.ctl(sf, 'digits', 'trainn', 14.2, 7.6, w=3.2, h=0.8, col='ffd040', label='', fmt='%s')

    # ---- instrument hood: lamps and the siding train's condition gauges
    hd = hood(m, col='#1b2420')
    for i, (cid, col, lab, blink) in enumerate((('power', '30e060', '12V', False), ('ok', '30e060', 'CHECK OK', False), ('bad', 'ff3030', 'PROBLEM', True),
                                                ('train', '8ad4ff', 'ON SIDING', False), ('dirty', 'c08040', 'DIRTY', False), ('damaged', 'ff8030', 'DAMAGED', True))):
        u = 1.3 + i * 1.45
        m.ctl(hd, 'lamp', cid, u, 11.0, w=0.6, h=0.6, col=col, label=lab, **({'blink': True} if blink else {}))
        m.ctl(hd, 'label', '', u, 10.45, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(hd, 'gauge', 'dirtg', 11.4, 10.3, w=2.0, h=2.0, col='f2f0e6', label='DIRT %', max=100)
    m.ctl(hd, 'gauge', 'bodyg', 13.9, 10.3, w=2.0, h=2.0, col='f2f0e6', label='DAMAGE %', max=100)

    # ---- the monitor: the formation's figures, click it for the editor
    mon = monitor(m, 3.0, 13.0, 12.2, 17.6, 10.2, ['m1', 'm2', 'm3', 'm4', 'm5'], cols=['7cf29a', '7cf29a', '7cf29a', 'b0b0b0', 'ffb020'])
    m.ctl(mon, 'zone', 'screen', X(8.0), 14.9, w=9.0, h=5.0, col='000000', label='')

    # ---- desk things: a scale model consist, drawing board lamp, paint tins, ruler
    m.g('scale model')
    m.box((0.6, 9.0, 11.0), (8.6, 9.2, 12.0), 'wood', 'model plinth')
    m.box((0.8, 9.2, 11.35), (8.4, 9.3, 11.45), 'metal:steel', 'model rail')
    m.box((0.8, 9.2, 11.55), (8.4, 9.3, 11.65), 'metal:steel', 'model rail')
    for i, (x0, x1, c) in enumerate(((1.0, 3.2, '#e0a030'), (3.4, 5.6, '#5bcefa'), (5.8, 8.0, '#5bcefa'))):
        m.box((x0, 9.4, 11.2), (x1, 10.4 if i == 0 else 10.2, 11.8), 'paint:' + c, 'model car')
        m.box((x0 + 0.2, 9.3, 11.25), (x0 + 0.6, 9.5, 11.75), 'paint:#202020', 'model bogie')
        m.box((x1 - 0.6, 9.3, 11.25), (x1 - 0.2, 9.5, 11.75), 'paint:#202020', 'model bogie')
        m.box((x0 + 0.3, 9.9, 11.18), (x1 - 0.3, 10.1, 11.2), 'glass', 'model windows')
    m.g('drafting lamp')
    m.cyl((14.6, 9.0, 12.4), 0.7, 0.3, 'y', 'paint:#1a1a1a', 'lamp foot')
    m.box((14.5, 9.3, 12.3), (14.7, 12.4, 12.5), 'metal:steel', 'lamp arm')
    m.box((14.5, 12.2, 11.0), (14.7, 12.4, 12.5), 'metal:steel', 'lamp arm')
    m.box((13.8, 11.4, 10.4), (15.4, 12.2, 11.6), 'paint:#2f6b3a', 'lamp shade')
    m.box((14.0, 11.3, 10.6), (15.2, 11.4, 11.4), 'lamp:fff2c0', 'lamp bulb')
    m.g('paint tins')
    for (x, c) in ((10.0, '#8e24aa'), (11.2, '#1e88e5'), (12.4, '#e53935')):
        m.cyl((x, 9.0, 12.4), 0.5, 0.9, 'y', 'metal:steel', 'paint tin', cap='paint:' + c)
        m.box((x - 0.5, 9.3, 11.95), (x + 0.5, 9.7, 11.98), 'paint:' + c, 'tin label')
    m.box((9.0, 9.0, 9.0), (13.0, 9.1, 9.4), 'paint:#e8d890', 'steel rule')
    for i in range(9):
        m.box((9.1 + i * 0.45, 9.1, 9.0), (9.15 + i * 0.45, 9.12, 9.2), 'paint:#202020', 'rule mark')
    m.box((0.6, 9.0, 8.8), (3.4, 9.2, 10.6), 'paint:#f4f0d8', 'formation sheets')
    m.box((0.8, 9.2, 9.0), (3.2, 9.22, 10.4), 'paint:#d8e0f0', 'drawing')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


RECIPES = {
    'formation_desk': (['GLG', 'PDP', 'WCW'], {'G': 'minecraft:glass_pane', 'L': 'minecraft:redstone_lamp', 'P': 'minecraft:paper',
                                               'D': 'irextras:freight_office', 'W': 'minecraft:planks', 'C': 'minecraft:chest'}),
}

LANG = {
    'tile.irextras.formation_desk.name': 'Formation & Livery Desk',
    'key.irextras.cab_dmi': 'Driver display (DMI)',
}

if __name__ == '__main__':
    formation_desk()
    for n in ('formation_desk',):
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        ing = lambda v: {'item': v, 'data': 0} if v == 'minecraft:planks' else {'item': v}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# train & vehicle systems (her list #106-110)\n' + '\n'.join(add) + '\n')
    print('shelf spots (model px, for TileFormationDesk):')
    for k, v in SPOTS.items(): print(' ', k, v)
