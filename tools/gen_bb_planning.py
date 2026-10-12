#!/usr/bin/env python3
"""Operations planning (her "next 48" list #1, 3, 4, 6, 7, 9, 40, 42, 46, 48): the Network Planning Desk - the digital
twin's desk. Models face north; controls are placed by u (viewer-left -> right): x = 16 - u. Live controls:
planning/TilePlanningDesk. Also writes the blockstate, item model, recipe and lang line, and prints the shelf spots."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, led_board, desk, strip, buttons, hood, monitor, cupboard_shelf

X = lambda u: 16 - u
SPOTS = {}


def lamp_row(m, sf, v, items, u0, step, size=0.55):
    for i, (cid, col, lab, blink) in enumerate(items):
        m.ctl(sf, 'lamp', cid, u0 + i * step, v, w=size, h=size, col=col, label=lab, **({'blink': True} if blink else {}))


def planning_desk():
    m = Model('planning_desk')
    back_wall(m, panel='#2c3440', plaster='#e4e6e8')
    # ---- the network board on the wall: six stretches with SPOF / CUT lamps, the score and the twin's verdict
    m.g('network board')
    led_board(m, -14, 30, 14.4, 28.6)
    m.box((-6, 29.0, 14.7), (22, 31.2, 15.4), 'paint:#14365a', 'board header')
    m.box((-5.6, 29.2, 14.62), (21.6, 31.0, 14.7), 'label:NETWORK PLANNING', 'header lettering')
    for cx in (-11, 27):
        m.cyl((cx, 30.1, 14.4), 0.95, 0.6, 'z', 'gauge:clock', 'planning office clock', cap='gauge:clock')
    b = m.surface('north', face=14.42, title='NETWORK BOARD')
    m.ctl(b, 'digits', 'title', X(8), 27.6, w=30, h=1.4, col='5bcefa', label='', fmt='%s')
    for r in range(1, 7):
        v = 25.6 - (r - 1) * 1.95
        m.ctl(b, 'lamp', 'r%ds' % r, X(-13.2), v, w=0.6, h=0.6, col='ffb020', label='')
        m.ctl(b, 'lamp', 'r%dc' % r, X(-12.2), v, w=0.6, h=0.6, col='ff3020', label='', blink=True)
        m.ctl(b, 'digits', 'r%da' % r, X(5.0), v, w=32, h=1.6, col='7cf29a', label='', fmt='%s')
        m.ctl(b, 'digits', 'r%db' % r, X(25.6), v, w=8.4, h=1.6, col='ffd27a', label='', fmt='%s')
    m.ctl(b, 'digits', 'verdict', X(8), 15.4, w=40, h=1.3, col='ffd040', label='', fmt='%s')
    # the route map strip under the board: a painted network diagram
    m.g('network diagram')
    m.box((-14, 12.2, 15.25), (30, 14.0, 15.4), 'paint:#f2f0e8', 'diagram board')
    for x0 in range(-13, 29, 5):
        m.box((x0, 13.0, 15.2), (x0 + 4.4, 13.25, 15.25), 'paint:#2a2a2a', 'main line')
        m.box((x0 + 4.0, 12.75, 15.18), (x0 + 4.8, 13.5, 15.25), 'paint:#1f6fb0', 'station mark')
    for x0 in (-9, 3, 17):
        m.box((x0, 12.5, 15.21), (x0 + 3.2, 12.68, 15.25), 'paint:#c03020', 'branch line')
        m.box((x0 + 2.8, 12.3, 15.19), (x0 + 3.4, 12.9, 15.25), 'paint:#e0a020', 'junction mark')
    # ---- left end: the plan chest (wide flat drawers) with the plotter on top
    m.g('plan chest')
    m.box((-13.6, 0, 9.4), (-1.0, 9.4, 15.2), 'paint:#3c4652', 'plan chest body')
    for k in range(5):
        y0 = 0.6 + k * 1.7
        m.box((-13.3, y0, 9.2), (-1.3, y0 + 1.5, 9.4), 'paint:#4e5a68', 'plan drawer front')
        m.box((-8.2, y0 + 0.55, 8.95), (-6.0, y0 + 0.85, 9.2), 'metal:steel', 'drawer pull')
        m.box((-12.6, y0 + 0.4, 9.15), (-11.0, y0 + 1.1, 9.2), 'paint:#f0eee6', 'drawer label card')
    pc = m.surface('north', face=9.1, title='PLAN CHEST')
    m.ctl(pc, 'zone', 'plans', X(-7.15), 4.7, w=12.0, h=8.4, col='000000', label='')
    m.box((-13.8, 9.4, 9.2), (-0.8, 9.8, 15.4), 'paint:#22282e', 'plan chest top')
    m.g('plotter')
    m.box((-13.0, 9.8, 11.0), (-2.0, 11.4, 14.6), 'paint:#d8dade', 'plotter body')
    m.box((-12.6, 11.4, 11.4), (-2.4, 11.6, 14.2), 'paint:#bfc3c8', 'plotter lid')
    m.box((-12.4, 10.4, 10.9), (-2.6, 10.6, 11.0), 'paint:#101010', 'paper slot')
    m.box((-12.2, 9.82, 9.0), (-2.8, 9.9, 11.0), 'paint:#f8f8f2', 'plotted network map')
    for x0 in (-11.5, -9.0, -6.5, -4.0):
        m.box((x0, 9.9, 9.6), (x0 + 1.8, 9.92, 9.75), 'paint:#2050d0', 'plotted line')
    m.box((-3.4, 11.0, 10.9), (-2.6, 11.4, 11.0), 'led:30ff60', 'plotter ready lamp')
    m.box((-12.0, 11.6, 12.6), (-11.0, 12.6, 13.4), 'paint:#303030', 'pen carousel')
    # ---- right end: binders cupboard + the shelf
    SPOTS['planning_desk'] = cupboard_shelf(m, 18.4, 29.4, ['binders'], 6, title='SHELF', body='#3c4652', door='#4e5a68')
    m.g('wall charts')
    m.box((18.8, 9.4, 15.0), (29.0, 12.0, 15.32), 'paint:#f4f2ea', 'string-line diagram')
    for i in range(6):
        m.box((19.2 + i * 1.6, 9.8, 14.98), (19.4 + i * 1.6 + 1.0, 11.6, 15.0), 'paint:#d02020' if i % 2 else 'paint:#2050d0', 'train path')
    # ---- the planner's desk
    desk(m, {('l', 0): 'dl1', ('l', 1): 'dl2', ('l', 2): 'dl3', ('r', 0): 'dr1', ('r', 1): 'dr2', ('r', 2): 'dr3'}, body='#3c4652', front='#4e5a68', pull='metal:steel')
    sf = strip(m, 3.3, 8.7, col='#1e2329')
    # row 1: the working case's selections and timetable knobs
    buttons(m, sf, 8.1, [('line', '2050d0', 'LINE'), ('seg', '2050d0', 'STRETCH'), ('stop', '2050d0', 'STATION'), ('dwp', 'f0f0f0', 'DWELL+'),
                         ('dwm', 'f0f0f0', 'DWELL-'), ('trp', '1fa34a', 'TRAIN+'), ('trm', 'a02020', 'TRAIN-'), ('reset', '505050', 'RESET')], u0=1.0, step=1.45, size=0.8)
    # row 2: the faults and works to test
    buttons(m, sf, 6.2, [('close', 'd02020', 'CLOSE'), ('tsr', 'e0a020', 'TSR'), ('single', 'e0a020', '1-LINE'), ('power', 'f0d020', 'POWER'),
                         ('elec', '20a0a0', 'WIRE'), ('double', '20a0a0', 'DOUBLE'), ('cbtc', '20a0a0', 'CBTC'), ('delay', 'e08020', 'DELAY')], u0=1.0, step=1.45, size=0.8)
    # row 3: studies and actions
    buttons(m, sf, 4.3, [('run', '1fa34a', 'RUN'), ('promote', 'd02020', 'PROMOTE'), ('robust', '2050d0', 'ROBUST'), ('resil', '2050d0', 'RESIL'),
                         ('wiwire', '20a0a0', 'WI WIRE'), ('wicap', '20a0a0', 'WI CAP'), ('commit', 'f0f0f0', 'COMMIT'), ('print', 'f0f0f0', 'PRINT')], u0=1.0, step=1.45, size=0.8)
    m.ctl(sf, 'selector', 'view', 13.3, 7.4, w=1.2, h=1.2, col='202020', label='VIEW', pos=['TWIN', 'RESIL', 'ROBUST', 'WORKS', 'AI', 'VERS', 'TREND', 'YARD'])
    m.ctl(sf, 'selector', 'rec', 14.9, 7.4, w=1.2, h=1.2, col='202020', label='RECOV', pos=['0', '5', '10', '15', '20', '30'])
    m.ctl(sf, 'key', 'auth', 13.3, 5.0, w=1.1, h=1.1, col='c0a040', label='PROMOTE', pos=['SAFE', 'AUTH'])
    m.ctl(sf, 'key', 'ai', 14.9, 5.0, w=1.1, h=1.1, col='c0a040', label='AI DISP', pos=['OFF', 'ON'])
    m.ctl(sf, 'push', 'survey', 14.1, 3.7, w=0.7, h=0.7, col='5bcefa', label='SURVEY')
    # the instrument hood: status lamps + readouts
    hd = hood(m, col='#1c2026')
    lamp_row(m, hd, 11.05, (('power', '30e060', '12V', False), ('promotable', '30e060', 'PROMO', False), ('worse', 'ff3030', 'WORSE', False), ('deadlock', 'ff3030', 'DLOCK', True),
                            ('spof', 'ffb020', 'SPOF', False), ('isolated', 'ff3030', 'ISOL', True), ('works', 'ffb020', 'WORKS', False), ('crew', 'ffb020', 'CREW', True),
                            ('aion', '30e060', 'AI', False), ('predok', '30e060', 'PRED', False)), u0=1.3, step=1.48)
    for i, (cid, col, fmt) in enumerate((('resil', '7cf29a', 'RES %s'), ('punct', 'ffd040', '%s'), ('delay', 'ff8040', '%s'), ('ver', '8ad4ff', '%s'))):
        m.ctl(hd, 'digits', cid, 2.1 + i * 3.9, 9.75, w=3.5, h=0.75, col=col, label='', fmt=fmt)
    monitor(m, 0.6, 7.8, 12.4, 17.8, 10.2, ['m1', 'm2', 'm3', 'm4', 'm5', 'm6'], cols=['5bcefa', 'f0f0f0', 'ffd040', 'ffd040', 'ff8040', '7cf29a'])
    monitor(m, 8.2, 15.4, 12.4, 17.8, 10.2, ['p1', 'p2', 'p3', 'p4', 'p5', 'p6'], cols=['5bcefa'] + ['7cf29a'] * 5)
    # desk clutter: keyboard, mouse, timetable book, desk lamp, coffee, binder stack
    m.g('desk things')
    m.box((4.2, 9.0, 8.85), (11.8, 9.25, 9.6), 'paint:#2a2c30', 'keyboard')
    for i in range(12):
        m.box((4.5 + i * 0.6, 9.25, 9.0), (4.95 + i * 0.6, 9.35, 9.45), 'paint:#d8d8d8', 'key cap')
    m.box((12.4, 9.0, 9.0), (13.0, 9.2, 9.8), 'paint:#2a2c30', 'mouse')
    m.box((0.8, 9.0, 8.9), (3.4, 9.5, 10.4), 'paint:#8a1414', 'working timetable book')
    m.box((0.9, 9.5, 9.0), (3.3, 9.55, 10.3), 'paint:#f2eee0', 'timetable pages')
    m.cyl((15.1, 9.0, 9.4), 0.35, 0.9, 'y', 'paint:#f0f0f0', 'coffee mug')
    m.g('desk lamp')
    m.cyl((-0.3, 9.8, 13.6), 0.6, 0.3, 'y', 'paint:#202020', 'lamp base')
    m.box((-0.42, 10.1, 13.48), (-0.18, 13.0, 13.72), 'metal:steel', 'lamp arm')
    m.box((-1.2, 12.6, 12.4), (0.6, 13.3, 13.8), 'paint:#202020', 'lamp shade')
    m.box((-1.0, 12.55, 12.6), (0.4, 12.6, 13.6), 'led:fff4c0', 'lamp bulb')
    # pin board over the plan chest: possession notices, work-crew rosters, a route availability chart
    m.g('pin board')
    m.box((-13.6, 10.2, 15.0), (-1.2, 13.0, 15.32), 'paint:#a07848', 'cork board')
    m.box((-13.8, 10.0, 15.0), (-1.0, 10.2, 15.36), 'paint:#5a3a24', 'board frame bottom')
    m.box((-13.8, 13.0, 15.0), (-1.0, 13.2, 15.36), 'paint:#5a3a24', 'board frame top')
    for i, (col, w) in enumerate((('#f8f6ee', 1.8), ('#fff070', 1.4), ('#f8f6ee', 2.0), ('#f0c0c0', 1.6), ('#c0e0f0', 1.8), ('#f8f6ee', 1.5))):
        x0 = -13.2 + i * 2.0
        m.box((x0, 10.6 + (i % 2) * 0.3, 14.96), (x0 + w, 12.5 - (i % 3) * 0.2, 15.0), 'paint:' + col, 'notice')
        m.box((x0 + w / 2 - 0.12, 12.2 - (i % 3) * 0.2, 14.86), (x0 + w / 2 + 0.12, 12.44 - (i % 3) * 0.2, 14.96), 'paint:#d02020', 'pin')
        m.box((x0 + 0.2, 11.6, 14.95), (x0 + w - 0.2, 11.68, 14.96), 'paint:#404040', 'notice text line')
        m.box((x0 + 0.2, 11.2, 14.95), (x0 + w - 0.5, 11.28, 14.96), 'paint:#404040', 'notice text line')
    # telephone + the out-trays
    m.g('telephone')
    m.box((13.6, 9.0, 11.4), (15.6, 9.6, 12.8), 'paint:#2a2c30', 'phone base')
    m.box((13.7, 9.6, 11.5), (15.5, 9.9, 11.9), 'paint:#2a2c30', 'handset rest')
    m.cyl((13.6, 10.05, 11.7), 0.3, 2.0, 'x', 'paint:#2a2c30', 'handset')
    for r in range(3):
        for c in range(3):
            m.box((13.9 + c * 0.5, 9.6, 12.0 + r * 0.25), (14.2 + c * 0.5, 9.68, 12.2 + r * 0.25), 'paint:#d8d8d8', 'phone key')
    m.g('trays')
    for k, col in enumerate(('#2050d0', '#d02020')):
        y0 = 9.0 + k * 0.7
        m.box((0.6, y0, 11.2), (3.2, y0 + 0.12, 13.4), 'paint:' + col, 'tray base')
        m.box((0.6, y0 + 0.12, 13.2), (3.2, y0 + 0.5, 13.4), 'paint:' + col, 'tray back')
        m.box((0.8, y0 + 0.12, 11.4), (3.0, y0 + 0.3, 13.1), 'paint:#f8f6ee', 'papers')
    m.box((0.7, 9.0, 11.3), (0.85, 10.4, 11.45), 'metal:steel', 'tray post')
    m.box((2.95, 9.0, 11.3), (3.1, 10.4, 11.45), 'metal:steel', 'tray post')
    # cable trunking behind the desk with its sockets, and the monitor speakers
    m.g('trunking')
    m.box((0.2, 7.8, 13.6), (15.8, 8.6, 14.2), 'paint:#e8e8e4', 'cable trunking')
    for x0 in (1.2, 4.6, 8.0, 11.4):
        m.box((x0, 7.95, 13.5), (x0 + 1.4, 8.45, 13.6), 'paint:#f8f8f8', 'double socket')
        m.box((x0 + 0.25, 8.1, 13.46), (x0 + 0.45, 8.3, 13.5), 'paint:#202020', 'socket pins')
        m.box((x0 + 0.95, 8.1, 13.46), (x0 + 1.15, 8.3, 13.5), 'paint:#202020', 'socket pins')
    for x0 in (0.1, 15.5):
        m.box((x0, 12.6, 10.4), (x0 + 0.5, 14.0, 11.0), 'paint:#1c1e20', 'monitor speaker')
        m.box((x0 + 0.05, 12.8, 10.35), (x0 + 0.45, 13.8, 10.4), 'louvre', 'speaker grille')
    # board speakers and the status beacon on top of the board
    m.g('board fittings')
    for x0 in (-15.0, 30.2):
        m.box((x0, 22.0, 14.4), (x0 + 0.8, 25.0, 15.4), 'paint:#1c1e20', 'PA speaker')
        m.box((x0 + 0.1, 22.2, 14.35), (x0 + 0.7, 24.8, 14.4), 'louvre', 'speaker cloth')
    m.cyl((8, 31.2, 14.9), 0.5, 0.2, 'y', 'paint:#303030', 'beacon base')
    m.cyl((8, 31.4, 14.9), 0.4, 0.55, 'y', 'led:40a0ff', 'beacon lens')
    # waste bin + mat
    m.g('floor')
    m.cyl((17.2, 0, 4.0), 0.9, 2.6, 'y', 'paint:#4a5866', 'waste bin')
    m.cyl((17.2, 2.6, 4.0), 0.95, 0.15, 'y', 'metal:steel', 'bin rim')
    m.box((1.0, 0, 0.4), (15.0, 0.12, 3.2), 'paint:#2a2a2a', 'anti-fatigue mat')
    m.terminal_12v(16.2, 1.0, 8.0, 'east')
    m.save(item_gui=0.28)


NAMES = ['planning_desk']
RECIPES = {
    'planning_desk': (['LML', 'PDP', 'WCW'], {'L': 'minecraft:redstone_lamp', 'M': 'minecraft:map', 'P': 'minecraft:paper', 'D': 'irextras:incident_desk', 'W': 'minecraft:planks', 'C': 'minecraft:chest'}),
}
LANG = {
    'tile.irextras.planning_desk.name': 'Network Planning Desk',
}
DATA0 = {'minecraft:planks', 'minecraft:stone_slab'}

if __name__ == '__main__':
    planning_desk()
    for n in NAMES:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        ing = lambda v: {'item': v, 'data': 0} if v in DATA0 else {'item': v}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# operations planning + simulation (next-48 #1 3 4 6 7 9 40 42 46 48)\n' + '\n'.join(add) + '\n')
    print('shelf spots (model px, for TilePlanningDesk.SPOTS):')
    for k, v in SPOTS.items(): print(' ', k, v)
