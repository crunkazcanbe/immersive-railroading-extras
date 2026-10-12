#!/usr/bin/env python3
"""Track infrastructure and railway engineering (her ChatGPT list section 3, #13-18) + tunnel building blocks.
Front = north. Every lamp, button, key, selector, dial and display is a live control (m.ctl) that the Java tile reads or
works - see com.dogpound.railmap.track. The 12 V terminal boxes are wire terminals, not buttons.
Tunnel lining pieces are plain JSON models on vanilla textures (concrete / brick / stone brick) so resource packs apply.
Run: python3 tools/gen_bb_track.py   (writes models, textures, blockstates, item models, recipes)"""
import json, math, os
from PIL import Image, ImageDraw
from bbkit import Model, ASSETS

LIGHT = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
CAST = 'paint:#4a5258'          # cast iron grey-green
SIGNAL_GREY = 'ral7032'


def facing_states(name, model=None):
    model = model or name
    bs = {'variants': {'facing=north': {'model': 'irextras:' + model}, 'facing=east': {'model': 'irextras:' + model, 'y': 90},
                       'facing=south': {'model': 'irextras:' + model, 'y': 180}, 'facing=west': {'model': 'irextras:' + model, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + model}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label, s=0.7, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def bolt_ring(m, x0, x1, y0, y1, z, n=4, face='north', s=0.35):
    for i in range(n):
        x = x0 + (x1 - x0) * i / max(1, n - 1)
        m.bolt(x, y0, z, face, s)
        m.bolt(x, y1, z, face, s)


# ------------------------------------------------------------------------------------------------ 14 point machine
def point_machine():
    m = Model('point_machine')
    m.g('bearers')
    for z in (8.4, 13.2):                                        # two concrete bearers carry machine and the stretcher bars
        m.box((-0.5, 0, z), (16.5, 1.4, z + 2.4), 'concrete', 'concrete bearer')
        for x in (1.0, 15.0):
            m.box((x - 0.5, 1.4, z + 0.5), (x + 0.5, 1.6, z + 1.9), 'metal:steel', 'baseplate clip')
    m.box((0.6, 1.4, 8.2), (15.4, 1.9, 16.0), 'metal:steel', 'machine baseplate')
    for x in (1.2, 4.0, 12.0, 14.8):
        for z in (8.8, 15.4):
            m.bolt(x, 1.9, z, 'up', 0.45)
    m.g('housing')
    m.box((1.0, 1.9, 9.0), (15.0, 6.2, 15.4), 'paint:#3d5a4a', 'cast housing')
    m.box((0.8, 6.2, 8.8), (15.2, 6.8, 15.6), 'paint:#35503f', 'cover flange')
    m.box((1.4, 6.8, 9.4), (14.6, 7.6, 15.0), 'paint:#3d5a4a', 'top cover')
    for i in range(9):                                           # cast stiffening ribs on the cover
        x = 1.8 + i * 1.55
        m.box((x, 7.6, 9.6), (x + 0.35, 8.0, 14.8), 'paint:#35503f', 'cover rib')
    for x in (1.2, 4.6, 8.0, 11.4, 14.8):
        m.bolt(x, 6.8, 8.9, 'north', 0.3)
        m.bolt(x, 6.8, 15.5, 'south', 0.3)
    for x in (3.0, 13.0):
        m.box((x - 0.5, 8.0, 11.8), (x + 0.5, 8.9, 12.6), 'metal:steel', 'lifting eye')
        m.box((x - 0.2, 8.9, 11.8), (x + 0.2, 9.3, 12.6), 'metal:steel', 'lifting eye loop')
    m.box((4.6, 2.6, 8.92), (11.4, 5.4, 9.0), 'plate:S700K POINT MACHINE', 'maker plate', faces=('north',))
    m.box((12.0, 2.6, 8.94), (14.4, 5.4, 9.0), 'warn:', 'warning label', faces=('north',))
    m.g('motor + gearbox')
    m.cyl((-1.6, 4.0, 12.2), 2.2, 2.6, 'x', 'paint:#2f4a3a', 'motor', cap='paint:#2a3f33')
    m.cyl((-2.2, 4.0, 12.2), 1.0, 0.6, 'x', 'metal:steel', 'motor end bell')
    for i in range(5):
        m.box((-1.4 + i * 0.5, 1.8, 10.2), (-1.2 + i * 0.5, 6.2, 14.2), 'paint:#2a3f33', 'motor cooling fin')
    m.box((15.0, 2.4, 10.0), (16.6, 6.0, 14.4), 'paint:#35503f', 'gearbox / clutch housing')
    m.cyl((16.6, 4.2, 12.2), 1.2, 0.5, 'x', 'metal:steel', 'clutch cap')
    m.g('crank socket')
    m.box((2.2, 7.6, 13.2), (3.8, 8.2, 14.6), 'paint:#2a3f33', 'crank socket boss')
    m.box((2.4, 8.2, 13.4), (3.6, 8.4, 14.4), 'paint:#f0c020', 'crank flap (hand crank interlock)')
    m.g('rods')
    for (x, r, nm) in ((5.0, 0.55, 'drive (throw) rod'), (9.0, 0.35, 'detection rod A'), (11.0, 0.35, 'detection rod B'), (7.0, 0.4, 'locking rod')):
        m.cyl((x, 3.2, 15.4), r, 14.6, 'z', 'metal:steel', nm)
        m.box((x - 0.9, 2.2, 15.4), (x + 0.9, 4.2, 16.4), 'paint:#35503f', 'rod gland')
    for z in (19.0, 24.0, 28.6):
        m.box((4.0, 2.2, z), (12.0, 2.8, z + 0.8), 'metal:steel', 'rod guide / stretcher clamp')
        m.box((4.2, 1.4, z), (4.8, 2.2, z + 0.8), 'metal:steel', 'guide foot')
        m.box((11.2, 1.4, z), (11.8, 2.2, z + 0.8), 'metal:steel', 'guide foot')
    m.box((3.8, 3.9, 16.4), (12.2, 4.3, 29.6), 'paint:#35503f', 'rod cover plate')
    m.g('conduit')
    m.cyl((8.0, 1.6, 4.4), 0.5, 4.6, 'z', 'cable', 'machine cable')
    m.cyl((8.0, 1.2, 4.0), 0.75, 0.6, 'z', 'metal:brass', 'conduit gland')
    m.g('local control cabinet')
    m.box((1.6, 0, 1.4), (14.4, 0.8, 4.6), 'concrete', 'cabinet plinth')
    m.box((2.0, 0.8, 1.6), (14.0, 13.6, 4.4), SIGNAL_GREY, 'local control cabinet')
    m.box((1.8, 13.6, 1.3), (14.2, 14.2, 4.6), SIGNAL_GREY, 'cabinet rain hood')
    m.box((2.2, 1.0, 1.0), (13.8, 13.4, 1.6), 'paint:#b9bbb7', 'open door frame')
    m.box((2.4, 1.2, 0.9), (13.6, 13.2, 1.0), LIGHT, 'control plate')
    m.box((13.8, 2.0, -2.8), (14.1, 12.6, 1.0), SIGNAL_GREY, 'open door leaf', faces=('east', 'west', 'north', 'up', 'down'))
    m.box((13.6, 6.6, -2.4), (13.8, 8.6, -2.0), 'metal:steel', 'door handle')
    for y in (2.6, 11.0):
        m.box((13.8, y, 0.9), (14.4, y + 1.2, 1.5), 'metal:steel', 'hinge')
    m.box((2.6, 12.5, 0.86), (13.4, 13.1, 0.9), 'label:POINTS LOCAL CONTROL', 'legend', faces=('north',))
    m.terminal_12v(14.0, 2.0, 3.0, 'east')
    for x in (4.0, 6.0, 8.0):
        m.gland(x, 0.8, 3.0, 'down', r=0.3, length=0.7)
    f = m.surface('north', face=0.88, title='POINTS LOCAL CONTROL')
    m.ctl(f, 'digits', 'pos', 8.0, 11.7, w=7.4, h=0.9, col='7cf29a', label='', fmt='%s')
    for i, (cid, col, lab, bl) in enumerate((('nor', '30e060', 'NOR', False), ('rev', 'ffd020', 'REV', False), ('nodet', 'ff3030', 'NO DET', True),
                                             ('run', 'ffb020', 'MOTOR', False), ('fail', 'ff2020', 'FAIL', True), ('obs', 'ff6020', 'OBSTR', False))):
        lamp(m, f, cid, 3.4 + i * 1.85, 10.4, col, lab, 0.62, bl)
    for i, (cid, col, lab) in enumerate((('ice', '9ad8ff', 'FROST'), ('heat', 'ff8040', 'RAIL HOT'), ('crank', 'ffb020', 'CRANK'),
                                         ('pwr', '30e060', '12V'), ('m1', '30e060', 'M1'), ('m2', '30e060', 'M2'))):
        lamp(m, f, cid, 3.4 + i * 1.85, 9.0, col, lab, 0.55)
    m.ctl(f, 'digits', 'sw', 5.2, 7.7, w=4.4, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'throws', 11.0, 7.7, w=3.6, h=0.7, col='ffb020', label='', fmt='%d THR')
    m.ctl(f, 'digits', 'amps', 4.6, 6.6, w=3.2, h=0.7, col='ff6040', label='', fmt='%s')
    m.ctl(f, 'digits', 'temp', 10.4, 6.6, w=6.4, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'gauge', 'g_amps', 4.0, 4.6, w=2.4, h=2.4, col='f2f0e6', label='MOTOR A', max=12)
    m.ctl(f, 'gauge', 'g_press', 7.0, 4.6, w=2.4, h=2.4, col='f2f0e6', label='PRESS %', max=100)
    m.ctl(f, 'digits', 'strk', 11.2, 5.0, w=4.0, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'selector', 's_type', 10.0, 3.6, w=1.1, h=1.1, col='202020', label='DRIVE', pos=['ELEC', 'HYD', 'PNEU'])
    m.ctl(f, 'selector', 's_motors', 12.4, 3.6, w=1.1, h=1.1, col='202020', label='MOTORS', pos=['SINGLE', 'DUAL'])
    m.ctl(f, 'selector', 's_mode', 3.6, 2.0, w=1.1, h=1.1, col='202020', label='MODE', pos=['REMOTE', 'LOCAL'])
    m.ctl(f, 'key', 'k_crank', 5.8, 2.0, w=1.0, h=1.0, col='c0a040', label='CRANK', pos=['OUT', 'IN'])
    m.ctl(f, 'push', 'p_nor', 7.8, 2.0, w=1.0, h=1.0, col='1fa34a', label='NOR')
    m.ctl(f, 'push', 'p_rev', 9.5, 2.0, w=1.0, h=1.0, col='d8b020', label='REV')
    m.ctl(f, 'push', 'p_crank', 11.2, 2.0, w=1.0, h=1.0, col='606060', label='TURN')
    m.ctl(f, 'push', 'p_reset', 12.9, 2.0, w=0.9, h=0.9, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'test', 12.9, 12.3, w=0.45, h=0.45, col='f0f0f0', label='')
    m.save(item_gui=0.5)
    facing_states('point_machine')


# ------------------------------------------------------------------------------------------------ 15 structure board
def structure_board():
    m = Model('structure_board')
    m.g('posts')
    for x in (2.0, 14.0):
        m.box((x - 1.2, 0, 7.0), (x + 1.2, 0.8, 10.0), 'concrete', 'post footing')
        m.box((x - 0.5, 0.8, 7.9), (x + 0.5, 16.6, 8.9), 'galv', 'post')
        m.box((x - 0.7, 16.6, 7.7), (x + 0.7, 17.0, 9.1), 'galv', 'post cap')
        for y in (3.0, 12.0, 15.5):
            m.box((x - 0.8, y, 7.7), (x + 0.8, y + 0.5, 9.1), 'galv', 'post clamp')
            m.bolt(x, y + 0.25, 7.7, 'north', 0.3)
    m.g('cast asset plate')
    m.box((1.0, 9.6, 6.9), (15.0, 16.2, 7.7), CAST, 'cast iron plate')
    m.box((1.0, 9.6, 6.7), (15.0, 9.9, 6.9), CAST, 'plate bead bottom')
    m.box((1.0, 15.9, 6.7), (15.0, 16.2, 6.9), CAST, 'plate bead top')
    m.box((1.0, 9.9, 6.7), (1.3, 15.9, 6.9), CAST, 'plate bead left')
    m.box((14.7, 9.9, 6.7), (15.0, 15.9, 6.9), CAST, 'plate bead right')
    for x in (1.6, 14.4):
        for y in (10.2, 15.6):
            m.bolt(x, y, 6.7, 'north', 0.35)
    m.box((1.6, 9.9, 6.68), (14.4, 15.9, 6.7), 'paint:#20262a', 'plate field', faces=('north',))
    m.g('condition cabinet')
    m.box((0.8, 0.8, 7.0), (15.2, 9.0, 10.6), SIGNAL_GREY, 'condition cabinet')
    m.box((0.6, 9.0, 6.6), (15.4, 9.5, 11.0), SIGNAL_GREY, 'cabinet hood')
    m.box((1.1, 1.0, 6.8), (14.9, 8.8, 7.0), LIGHT, 'cabinet face')
    for x in (1.4, 14.6):
        for y in (1.3, 8.5):
            m.bolt(x, y, 6.8, 'north', 0.25)
    m.louvres(1.6, 5.0, 0.95, 1.9, 10.6, n=2)
    m.terminal_12v(15.2, 2.0, 8.8, 'east')
    m.gland(4.0, 0.8, 9.0, 'down', r=0.3, length=0.7)
    m.gland(12.0, 0.8, 9.0, 'down', r=0.3, length=0.7)
    m.g('inspection kit')
    m.box((15.4, 4.0, 7.6), (15.9, 7.0, 9.8), 'paint:#d8aa18', 'logbook box')
    m.box((15.9, 4.6, 8.2), (16.1, 6.4, 9.2), 'metal:steel', 'logbook hasp')
    m.box((0.2, 5.0, 7.8), (0.8, 8.0, 9.6), 'paint:#c81e1e', 'hammer clip (sounding hammer)')
    m.cyl((0.5, 2.6, 8.7), 0.2, 3.0, 'y', 'wood', 'sounding hammer handle')
    m.box((0.1, 5.4, 8.3), (0.9, 6.0, 9.1), 'metal:steel', 'sounding hammer head')
    m.g('flood staff gauge')
    m.box((15.0, 0.8, 11.2), (16.2, 16.0, 11.6), 'paint:#f4f4ee', 'water level staff')
    for i in range(15):
        m.box((15.0 if i % 2 == 0 else 15.5, 1.0 + i, 11.1), (16.2, 1.4 + i, 11.2), 'paint:#1a1a1a' if i % 5 else 'paint:#c81e1e', 'staff graduation')
    m.box((14.6, 8.0, 11.0), (15.0, 8.6, 11.8), 'metal:steel', 'staff bracket')
    m.g('earthing + cables')
    m.box((1.6, 0.0, 9.6), (2.0, 0.8, 10.0), 'metal:copper', 'earth rod')
    m.box((1.6, 0.8, 9.6), (1.8, 7.0, 9.8), 'metal:copper', 'earth strap')
    for y in (2.0, 4.0, 6.0, 8.0):
        m.box((10.6, y, 10.6), (13.4, y + 0.2, 11.0), 'galv', 'cable ladder rung')
    m.box((10.6, 1.0, 10.6), (10.8, 9.0, 11.0), 'galv', 'cable ladder rail')
    m.box((13.2, 1.0, 10.6), (13.4, 9.0, 11.0), 'galv', 'cable ladder rail')
    f = m.surface('north', face=6.66, title='STRUCTURE')
    m.ctl(f, 'digits', 'name', 8.0, 14.9, w=11.6, h=1.0, col='f4f4ee', label='', fmt='%s')
    m.ctl(f, 'digits', 'ra', 4.8, 13.4, w=5.2, h=0.9, col='ffd020', label='', fmt='%s')
    m.ctl(f, 'digits', 'clr', 11.2, 13.4, w=5.6, h=0.9, col='ffd020', label='', fmt='%s')
    m.ctl(f, 'digits', 'spd', 4.8, 11.9, w=5.2, h=0.9, col='ff8040', label='', fmt='%s')
    m.ctl(f, 'digits', 'cond', 11.2, 11.9, w=5.6, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'due', 8.0, 10.6, w=8.0, h=0.8, col='9ad8ff', label='', fmt='%s')
    f2 = m.surface('north', face=6.78, title='CONDITION MONITOR')
    for i, (cid, col, lab, bl) in enumerate((('ok', '30e060', 'OK', False), ('restr', 'ffb020', 'TSR', False), ('closed', 'ff2020', 'CLOSED', True),
                                             ('overdue', 'ffb020', 'EXAM', True), ('heavy', 'ff2020', 'HEAVY', True), ('oog', 'ff2020', 'GAUGE', True))):
        lamp(m, f2, cid, 2.4 + i * 1.6, 8.0, col, lab, 0.55, bl)
    for i, (cid, col, lab, bl) in enumerate((('flood', '4080ff', 'FLOOD', True), ('rock', 'ff6020', 'ROCK', True), ('insp', '30e060', 'IN DATE', False),
                                             ('wo', 'ffd020', 'W/ORDER', False), ('pwr', '30e060', '12V', False))):
        lamp(m, f2, cid, 2.4 + i * 1.6, 6.8, col, lab, 0.5, bl)
    m.ctl(f2, 'gauge', 'g_cond', 12.8, 7.0, w=2.6, h=2.6, col='f2f0e6', label='COND %', max=100)
    m.ctl(f2, 'digits', 'last', 6.4, 5.6, w=9.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f2, 'digits', 'd_start', 4.0, 4.6, w=5.2, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f2, 'digits', 'd_end', 9.8, 4.6, w=5.2, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f2, 'selector', 's_kind', 2.4, 3.0, w=1.0, h=1.0, col='202020', label='KIND', pos=['BRIDGE', 'VIADUCT', 'TUNNEL', 'CULVERT', 'RET WALL', 'CUTTING'])
    m.ctl(f2, 'selector', 's_mat', 4.2, 3.0, w=1.0, h=1.0, col='202020', label='MATL', pos=['STEEL', 'CONCRETE', 'MASONRY'])
    m.ctl(f2, 'selector', 's_ra', 6.0, 3.0, w=1.0, h=1.0, col='202020', label='RA T', pos=['16', '20', '22.5', '25', '30', '35'])
    m.ctl(f2, 'selector', 's_spd', 7.8, 3.0, w=1.0, h=1.0, col='202020', label='LIMIT', pos=['NONE', '120', '80', '60', '40', '20'])
    m.ctl(f2, 'push', 'p_insp', 9.8, 3.0, w=0.9, h=0.9, col='1fa34a', label='EXAM')
    m.ctl(f2, 'push', 'p_wo', 11.4, 3.0, w=0.9, h=0.9, col='d8b020', label='W/O')
    m.ctl(f2, 'push', 'p_rep', 13.0, 3.0, w=0.9, h=0.9, col='2050d0', label='REPAIR')
    m.ctl(f2, 'push', 'p_meas', 13.0, 1.6, w=0.7, h=0.7, col='606060', label='GAUGE')
    m.ctl(f2, 'push', 'test', 14.4, 8.0, w=0.4, h=0.4, col='f0f0f0', label='')
    m.save(item_gui=0.5)
    facing_states('structure_board')


# ------------------------------------------------------------------------------------------------ 17 crossing predictor
def crossing_predictor():
    m = Model('crossing_predictor')
    m.g('plinth')
    m.box((0.6, 0, 2.6), (15.4, 1.0, 13.6), 'concrete', 'concrete plinth')
    m.box((0.6, 1.0, 2.4), (15.4, 1.3, 13.6), 'haz', 'kick strip', faces=('north',))
    m.g('case')
    m.box((1.0, 1.0, 3.2), (15.0, 24.0, 13.0), 'ral7032', 'location case')
    m.box((0.7, 24.0, 2.6), (15.3, 24.8, 13.4), 'ral7032', 'rain hood')
    m.box((0.7, 24.8, 4.0), (15.3, 25.3, 12.6), 'ral7032', 'roof ridge')
    for z in (5.0, 8.0, 11.0):
        m.box((0.7, 3.0, z), (1.0, 22.0, z + 0.6), 'ral7032', 'side stiffener')
        m.box((15.0, 3.0, z), (15.3, 22.0, z + 0.6), 'ral7032', 'side stiffener')
    m.louvres(1.6, 6.4, 1.6, 4.0, 3.2, n=3)
    m.louvres(9.6, 14.4, 1.6, 4.0, 3.2, n=3)
    m.box((1.4, 4.4, 2.9), (14.6, 23.6, 3.2), LIGHT, 'panel')
    m.nameplate(2.4, 22.4, 13.6, 23.3, 2.9, 'LX PREDICTOR')
    for x in (1.8, 14.2):
        for y in (4.8, 23.2):
            m.bolt(x, y, 2.9, 'north', 0.35)
    m.g('relay rack (behind)')
    for i in range(6):
        m.box((2.0 + i * 2.1, 5.0, 2.86), (3.8 + i * 2.1, 6.2, 2.9), 'paint:#b0b2ae', 'relay base')
    m.g('battery box')
    m.box((15.0, 1.0, 9.0), (16.4, 6.0, 13.0), 'paint:#3a4248', 'battery box')
    m.box((15.0, 6.0, 8.8), (16.6, 6.4, 13.2), 'paint:#2a2d30', 'battery box lid')
    for z in (9.4, 10.4, 11.4, 12.4):
        m.box((16.4, 2.0, z), (16.5, 5.0, z + 0.5), 'paint:#1a1a1a', 'battery vent slot')
    m.box((15.3, 6.4, 10.4), (15.9, 6.8, 11.6), 'metal:steel', 'lid handle')
    m.g('road traffic loops')
    for x in (3.0, 13.0):
        m.box((x, 0.2, 7.0), (x + 0.6, 1.0, 7.6), 'cable', 'cable trough')
    for x in (3.0, 5.5, 8.0, 10.5, 13.0):
        m.gland(x, 1.0, 10.0, 'down', r=0.35)
    m.terminal_12v(15.0, 6.0, 8.0, 'east')
    m.g('bell')
    m.cyl((8.0, 25.3, 8.0), 1.2, 0.6, 'y', 'metal:brass', 'test bell')
    m.cyl((8.0, 25.9, 8.0), 0.3, 0.6, 'y', 'metal:steel', 'bell stalk')
    m.g('door')
    m.box((14.6, 11.0, 2.6), (15.0, 14.0, 2.9), 'metal:steel', 'door handle')
    for y in (6.0, 21.0):
        m.box((0.6, y, 3.2), (1.0, y + 1.4, 3.8), 'metal:steel', 'hinge')
    f = m.surface('north', face=2.86, title='LX PREDICTOR')
    m.box((2.0, 18.6, 2.86), (14.0, 21.6, 2.9), 'paint:#20262a', 'display window', faces=('north',))
    m.ctl(f, 'digits', 'eta', 5.0, 20.8, w=5.4, h=1.0, col='ff4040', label='', fmt='%s')
    m.ctl(f, 'digits', 'wt', 11.2, 20.8, w=5.2, h=1.0, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'q', 5.0, 19.4, w=5.4, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'veh', 11.2, 19.4, w=5.2, h=0.9, col='7cf29a', label='', fmt='%s')
    for i, (cid, col, lab, bl) in enumerate((('act', 'ff2020', 'WARN', True), ('down', 'ffb020', 'DOWN', False), ('up', '30e060', 'UP', False),
                                             ('occ', 'ff2020', 'OBSTACLE', True), ('fail', 'ff2020', 'FAIL', True))):
        lamp(m, f, cid, 2.6 + i * 2.6, 17.0, col, lab, 0.75, bl)
    for i, (cid, col, lab, bl) in enumerate((('emerg', '4080ff', 'EMERG VEH', True), ('train', 'ffb020', 'TRAIN', False), ('prot', 'ff2020', 'SIG HELD', False),
                                             ('pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.6 + i * 2.6, 15.0, col, lab, 0.65, bl)
    m.ctl(f, 'gauge', 'g_q', 13.0, 15.2, w=2.8, h=2.8, col='f2f0e6', label='QUEUE', max=60)
    m.ctl(f, 'digits', 'acts', 4.6, 12.4, w=5.6, h=0.8, col='ffd020', label='', fmt='%s')
    m.ctl(f, 'digits', 'rel', 11.2, 12.4, w=5.6, h=0.8, col='ffd020', label='', fmt='%s')
    m.ctl(f, 'digits', 'avg', 4.6, 11.2, w=5.6, h=0.8, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'xs', 11.2, 11.2, w=5.6, h=0.8, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'selector', 's_wt', 3.2, 8.6, w=1.6, h=1.6, col='202020', label='WARN TIME', pos=['20S', '25S', '30S', '35S', '45S'])
    m.ctl(f, 'selector', 's_flow', 6.4, 8.6, w=1.6, h=1.6, col='202020', label='ROAD FLOW', pos=['LOW', 'MED', 'HIGH', 'ARTERIAL'])
    m.ctl(f, 'push', 'p_reset', 9.4, 8.6, w=1.4, h=1.4, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'p_emerg', 12.4, 8.6, w=1.4, h=1.4, col='4080ff', label='EMV PRIO')
    m.ctl(f, 'push', 'p_rep', 9.4, 6.4, w=1.1, h=1.1, col='f0f0f0', label='REPORT')
    m.ctl(f, 'push', 'test', 12.4, 6.4, w=0.6, h=0.6, col='f0f0f0', label='LAMPS')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 14.0, 10.0, 22.5, 3.0, side='south', col='ral7032', title='LX PREDICTOR', volts='110V AC 12V DC', fns=(('ctl', '12V CTL'), ('scr', 'SCREENS')))
    m.save(item_gui=0.45)
    facing_states('crossing_predictor')


# ------------------------------------------------------------------------------------------------ 16 tunnel control panel
def tunnel_control():
    m = Model('tunnel_control')
    m.g('wall frame')
    m.box((0.0, 0.0, 14.6), (16.0, 22.0, 16.0), 'concrete', 'wall backing plate')
    for y in (0.6, 21.0):
        m.box((0.4, y, 14.0), (15.6, y + 0.4, 14.6), 'galv', 'unistrut')
    m.g('panel cabinet')
    m.box((0.6, 1.2, 11.2), (15.4, 20.6, 14.0), 'paint:#d9dbd6', 'SCADA cabinet')
    m.box((0.4, 20.6, 10.8), (15.6, 21.2, 14.2), 'paint:#c5c7c2', 'cabinet top')
    m.box((0.4, 0.8, 10.8), (15.6, 1.2, 14.2), 'paint:#c5c7c2', 'cabinet base')
    m.box((0.8, 1.4, 11.0), (15.2, 20.4, 11.2), LIGHT, 'panel face')
    for x in (1.1, 14.9):
        for y in (1.7, 20.1):
            m.bolt(x, y, 11.0, 'north', 0.3)
    m.nameplate(2.0, 19.2, 14.0, 20.1, 11.0, 'TUNNEL CONTROL')
    m.g('mimic')
    m.box((1.4, 15.0, 10.94), (14.6, 15.4, 11.0), 'paint:#2a2d30', 'mimic bore top', faces=('north',))
    m.box((1.4, 12.6, 10.94), (14.6, 13.0, 11.0), 'paint:#2a2d30', 'mimic bore bottom', faces=('north',))
    m.box((1.0, 12.6, 10.94), (1.4, 15.4, 11.0), 'paint:#8a5a2b', 'mimic portal A', faces=('north',))
    m.box((14.6, 12.6, 10.94), (15.0, 15.4, 11.0), 'paint:#8a5a2b', 'mimic portal B', faces=('north',))
    m.box((1.4, 13.75, 10.94), (14.6, 13.85, 11.0), 'paint:#606060', 'mimic track', faces=('north',))
    m.box((1.2, 18.0, 10.94), (5.6, 18.6, 11.0), 'label:FANS', 'legend fans', faces=('north',))
    m.box((1.2, 11.6, 10.94), (6.2, 12.2, 11.0), 'label:DETECTORS', 'legend det', faces=('north',))
    m.box((9.4, 11.6, 10.94), (14.8, 12.2, 11.0), 'label:X-PASSAGES', 'legend xp', faces=('north',))
    m.g('services')
    m.terminal_12v(15.4, 2.0, 12.6, 'east')
    for x in (3.0, 5.0, 7.0, 9.0, 11.0, 13.0):
        m.gland(x, 0.8, 12.6, 'down', r=0.3, length=0.7)
    m.box((15.4, 8.0, 11.6), (16.0, 16.0, 13.6), 'paint:#c81e1e', 'fire phone box')
    m.box((15.6, 9.0, 11.4), (15.9, 15.0, 11.6), 'label:FIRE', 'fire phone label', faces=('north',))
    m.cyl((0.0, 4.0, 12.6), 0.8, 0.6, 'x', 'paint:#c81e1e', 'beacon base')
    m.cyl((-0.8, 4.0, 12.6), 0.6, 0.8, 'x', 'glass', 'alarm beacon')
    m.g('cabinet details')
    for i in range(6):
        m.box((0.4, 3.0 + i * 0.9, 11.6), (0.6, 3.4 + i * 0.9, 13.6), 'paint:#2a2d30', 'side vent slot')
        m.box((15.4, 3.0 + i * 0.9, 11.6), (15.6, 3.4 + i * 0.9, 13.6), 'paint:#2a2d30', 'side vent slot')
    m.box((0.0, -0.6, 12.0), (16.0, 0.0, 14.6), 'galv', 'cable tray')
    for x in (1.0, 5.0, 9.0, 13.0):
        m.box((x, -0.6, 14.0), (x + 0.4, 0.8, 14.6), 'galv', 'tray bracket')
    for i, col in enumerate(('cable:c81e1e', 'cable:1d1d21', 'cable:1d58c8', 'cable:e0c020')):
        m.cyl((0.0, -0.3, 12.4 + i * 0.5), 0.2, 16.0, 'x', col, 'tray cable')
    m.box((15.5, 10.0, 11.3), (15.9, 14.0, 11.6), 'paint:#1a1a1a', 'fire phone handset')
    m.box((2.0, 0.9, 10.9), (5.0, 1.3, 11.1), 'paint:#2a2d30', 'printer slot')
    m.box((12.6, 18.9, 10.9), (14.4, 19.1, 11.0), 'label:SCADA', 'scada tag', faces=('north',))
    f = m.surface('north', face=10.92, title='TUNNEL CONTROL')
    for i in range(6):
        u = 16 - (2.4 + i * 2.2)
        lamp(m, f, 'f%d' % (i + 1), u, 16.6, '30e060', 'F%d' % (i + 1), 0.7)
        lamp(m, f, 'd%d' % (i + 1), u, 12.0 + 2.2, 'ff2020', 'D%d' % (i + 1), 0.55, True)
    for i in range(4):
        lamp(m, f, 'x%d' % (i + 1), 16 - (3.4 + i * 3.1), 13.2, '30e060', 'X%d' % (i + 1), 0.5)
    lamp(m, f, 'train', 8.0, 13.8, 'ffb020', '', 0.5, True)
    for i, (cid, col, lab, bl) in enumerate((('fire', 'ff2020', 'FIRE', True), ('co_hi', 'ffb020', 'CO HIGH', True), ('emerg', 'ff2020', 'EMERG', True),
                                             ('trac', 'ff6020', 'TRACTION', False), ('held', 'ff2020', 'SIG HELD', False), ('auto', '30e060', 'AUTO', False))):
        lamp(m, f, cid, 16 - (1.8 + i * 2.5), 10.6, col, lab, 0.65, bl)
    for i, (cid, col, lab) in enumerate((('phone', 'ffb020', 'PHONE'), ('radio', '30e060', 'RADIO'), ('lt', 'fff0c0', 'LIGHTS'), ('pwr', '30e060', '12V'))):
        lamp(m, f, cid, 16 - (1.8 + i * 2.5), 9.0, col, lab, 0.55)
    m.ctl(f, 'gauge', 'g_co', 3.4, 9.0, w=2.0, h=2.0, col='f2f0e6', label='CO PPM', max=200)
    for (cid, u, v, col) in (('co', 4.6, 7.4, 'ffb020'), ('vis', 11.4, 7.4, '9ad8ff'), ('air', 4.6, 6.3, '7cf29a'), ('kw', 11.4, 6.3, 'ffd020'),
                             ('trains', 4.6, 5.2, 'ffb020'), ('pers', 11.4, 5.2, 'ff8080')):
        m.ctl(f, 'digits', cid, u, v, w=6.4, h=0.8, col=col, label='', fmt='%s')
    m.ctl(f, 'digits', 'rescue', 8.0, 4.1, w=13.0, h=0.8, col='ff4040', label='', fmt='%s')
    m.ctl(f, 'digits', 'zone', 2.6, 18.2, w=3.6, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'selector', 's_mode', 14.0, 2.2, w=1.3, h=1.3, col='202020', label='VENT', pos=['OFF', 'AUTO', 'MANUAL'])
    m.ctl(f, 'selector', 's_dir', 11.8, 2.2, w=1.3, h=1.3, col='202020', label='DIR', pos=['FWD', 'REV'])
    m.ctl(f, 'selector', 's_light', 9.6, 2.2, w=1.3, h=1.3, col='202020', label='LIGHTS', pos=['OFF', 'NORMAL', 'BOOST'])
    m.ctl(f, 'selector', 's_zone', 7.4, 2.2, w=1.3, h=1.3, col='202020', label='ZONE M', pos=['64', '128', '256', '512'])
    m.ctl(f, 'push', 'p_vent', 5.5, 2.2, w=1.0, h=1.0, col='1fa34a', label='VENT')
    m.ctl(f, 'push', 'p_sil', 4.0, 1.9, w=0.8, h=0.8, col='202020', label='SIL')
    m.ctl(f, 'push', 'p_reset', 4.0, 3.0, w=0.8, h=0.8, col='2050d0', label='RESET')
    m.ctl(f, 'mushroom', 'm_emerg', 2.0, 2.4, w=1.8, h=1.8, col='d01818', label='EMERG')
    m.ctl(f, 'push', 'test', 14.8, 18.6, w=0.4, h=0.4, col='f0f0f0', label='')
    m.save(item_gui=0.45)
    facing_states('tunnel_control')


# ------------------------------------------------------------------------------------------------ 16 jet fan
def tunnel_fan():
    m = Model('tunnel_fan')
    cy, cz = 9.0, 8.0
    m.g('hangers')
    for x in (-4.0, 3.0, 13.0, 20.0):
        m.box((x - 0.8, 15.4, cz - 2.4), (x + 0.8, 16.0, cz + 2.4), 'galv', 'roof anchor plate')
        for z in (cz - 1.8, cz + 1.8):
            m.bolt(x, 15.4, z, 'down', 0.3)
        m.box((x - 0.25, 13.6, cz - 0.25), (x + 0.25, 15.4, cz + 0.25), 'galv', 'hanger rod')
        m.box((x - 0.6, 13.0, cz - 0.6), (x + 0.6, 13.6, cz + 0.6), 'paint:#1a1a1a', 'anti-vibration mount')
    for x in (3.0, 13.0):
        m.box((x - 0.4, 12.2, cz - 5.4), (x + 0.4, 13.0, cz + 5.4), 'galv', 'saddle')
    m.g('casing')
    m.cyl((-7.0, cy, cz), 4.2, 6.0, 'x', 'galv', 'silencer A')
    m.cyl((-1.0, cy, cz), 4.6, 0.6, 'x', 'galv', 'flange A')
    m.cyl((-0.4, cy, cz), 4.2, 16.8, 'x', 'paint:#5f6a72', 'fan casing')
    m.cyl((16.4, cy, cz), 4.6, 0.6, 'x', 'galv', 'flange B')
    m.cyl((17.0, cy, cz), 4.2, 6.0, 'x', 'galv', 'silencer B')
    for x in (-6.0, -4.0, -2.2, 18.2, 20.0, 22.0):
        m.cyl((x, cy, cz), 4.35, 0.25, 'x', 'paint:#7a868e', 'silencer band')
    for xx in (-0.7, 2.6, 6.0, 9.4, 12.8, 16.1):
        for k in range(6):                                       # flange bolts round the joints
            a = k * math.pi / 3
            m.box((xx, cy + 4.3 * math.sin(a) - 0.2, cz + 4.3 * math.cos(a) - 0.2), (xx + 0.3, cy + 4.3 * math.sin(a) + 0.2, cz + 4.3 * math.cos(a) + 0.2), 'metal:steel', 'flange bolt')
    m.g('ends')
    m.box((-7.2, cy - 3.6, cz - 3.6), (-7.0, cy + 3.6, cz + 3.6), 'mesh', 'guard grille A (outlet)')
    m.box((23.0, cy - 3.6, cz - 3.6), (23.2, cy + 3.6, cz + 3.6), 'mesh', 'guard grille B')
    m.cyl((-6.9, cy, cz), 1.2, 0.4, 'x', 'paint:#2a2d30', 'nose cone A')
    m.cyl((22.5, cy, cz), 1.2, 0.4, 'x', 'paint:#2a2d30', 'nose cone B')
    for k in range(4):
        m.box((-6.95, cy - 0.15, cz - 3.4 + k * 2.2), (-6.85, cy + 0.15, cz - 3.1 + k * 2.2), 'paint:#2a2d30', 'grille strut')
    m.g('motor')
    m.box((4.0, cy - 4.8, cz - 1.6), (12.0, cy - 4.2, cz + 1.6), 'paint:#5f6a72', 'motor terminal bulge')
    m.box((5.6, cy - 5.6, cz - 4.6), (10.4, cy - 3.0, cz - 3.6), 'paint:#3a4248', 'motor terminal box')
    m.cyl((8.0, cy - 6.6, cz - 4.1), 0.35, 1.0, 'y', 'cable', 'motor supply cable')
    m.box((1.0, cy + 4.0, cz - 0.8), (15.0, cy + 4.4, cz + 0.8), 'metal:steel', 'lifting rail')
    m.g('isolator + starter (north side)')
    m.box((2.6, 3.8, 1.6), (13.4, 9.6, 3.6), 'paint:#d9dbd6', 'local isolator / starter box')
    m.box((2.4, 9.6, 1.4), (13.6, 10.0, 3.8), 'paint:#c5c7c2', 'starter box lid')
    m.box((2.8, 4.0, 1.4), (13.2, 9.4, 1.6), LIGHT, 'starter face')
    m.box((3.2, 8.6, 1.36), (8.6, 9.2, 1.4), 'label:JET FAN', 'legend', faces=('north',))
    m.box((9.2, 8.6, 1.36), (12.8, 9.2, 1.4), 'danger:400V', 'hv warning', faces=('north',))
    m.box((10.0, 4.2, 1.36), (13.0, 5.0, 1.4), 'label:FWD >', 'direction label', faces=('north',))
    m.terminal_12v(13.4, 4.4, 2.6, 'east')
    f = m.surface('north', face=1.34, title='JET FAN')
    m.ctl(f, 'selector', 's_local', 16 - 4.4, 6.2, w=1.8, h=1.8, col='c81e1e', label='ISOLATOR', pos=['REMOTE', 'OFF', 'HAND FWD', 'HAND REV'])
    lamp(m, f, 'run_f', 16 - 7.2, 7.6, '30e060', 'FWD', 0.55)
    lamp(m, f, 'run_r', 16 - 8.4, 7.6, '30e060', 'REV', 0.55)
    lamp(m, f, 'fault', 16 - 9.6, 7.6, 'ff2020', 'FAULT', 0.55, True)
    lamp(m, f, 'sup', 16 - 10.8, 7.6, 'ffd020', '400V', 0.55)
    lamp(m, f, 'pwr', 16 - 12.0, 7.6, '30e060', '12V', 0.5)
    m.ctl(f, 'digits', 'kw', 16 - 8.4, 6.4, w=3.4, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'rpm', 16 - 8.4, 5.4, w=4.4, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'amps', 16 - 11.6, 6.4, w=2.2, h=0.7, col='ff6040', label='', fmt='%s')
    m.ctl(f, 'gauge', 'g_rpm', 16 - 11.6, 5.0, w=1.6, h=1.6, col='f2f0e6', label='RPM', max=1480)
    m.ctl(f, 'digits', 'id', 16 - 4.4, 4.4, w=2.4, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'push', 'test', 16 - 3.3, 8.8, w=0.4, h=0.4, col='f0f0f0', label='')
    m.save(item_gui=0.35)
    facing_states('tunnel_fan')


# ------------------------------------------------------------------------------------------------ 16 smoke detector
def smoke_detector():
    m = Model('smoke_detector')
    m.g('mounting')
    m.box((2.6, 2.6, 15.4), (13.4, 13.4, 16.0), 'galv', 'wall plate')
    for x in (3.2, 12.8):
        for y in (3.2, 12.8):
            m.bolt(x, y, 15.4, 'north', 0.3)
    m.g('detector')
    m.box((3.2, 3.4, 12.4), (12.8, 12.4, 15.4), 'metal:steel', 'stainless aspirating detector')
    m.box((3.0, 12.4, 12.2), (13.0, 12.8, 15.6), 'metal:steel', 'detector lid')
    m.box((3.5, 3.6, 12.2), (12.5, 12.2, 12.4), LIGHT, 'detector face')
    m.box((4.0, 11.2, 12.16), (12.0, 11.9, 12.2), 'label:SMOKE + CO', 'legend', faces=('north',))
    m.box((4.2, 6.2, 12.16), (11.8, 7.8, 12.2), 'paint:#20262a', 'display window', faces=('north',))
    m.louvres(9.6, 12.2, 3.8, 5.6, 12.4, n=3)
    m.g('sampling pipe')
    m.cyl((8.0, 12.8, 13.8), 0.45, 3.2, 'y', 'paint:#c81e1e', 'sampling pipe riser')
    m.cyl((-8.0, 16.0, 13.8), 0.45, 32.0, 'x', 'paint:#c81e1e', 'sampling pipe')
    for x in (-6.0, 0.0, 6.0, 12.0, 18.0):
        m.box((x - 0.4, 15.4, 13.2), (x + 0.4, 16.6, 14.4), 'galv', 'pipe clip')
        m.cyl((x, 15.2, 13.8), 0.18, 0.4, 'y', 'paint:#1a1a1a', 'sampling hole')
    m.g('CO cell')
    m.cyl((4.6, 2.2, 13.6), 0.9, 1.2, 'y', 'paint:#e8e8e2', 'CO sensor head')
    m.box((4.0, 2.0, 13.0), (5.2, 2.2, 14.2), 'mesh', 'CO sensor mesh')
    m.g('aspirator')
    m.box((9.6, 2.0, 12.8), (12.4, 3.4, 15.0), 'paint:#3a4248', 'aspirator fan housing')
    m.cyl((11.0, 1.4, 13.9), 0.6, 0.6, 'y', 'mesh', 'exhaust port')
    m.box((6.0, 12.8, 13.0), (7.6, 14.2, 14.6), 'paint:#e8e8e2', 'inline filter')
    m.box((8.4, 13.2, 13.2), (9.6, 14.0, 14.4), 'paint:#3a4248', 'airflow sensor')
    m.cyl((-8.0, 16.0, 13.8), 0.6, 0.4, 'x', 'paint:#a01818', 'pipe end cap')
    for x in (-7.0, -5.0, -3.0, -1.0, 1.0, 3.0, 5.0, 9.0, 11.0, 15.0, 17.0, 19.0, 21.0):
        m.box((x - 0.3, 15.5, 13.4), (x + 0.3, 16.5, 14.2), 'paint:#a01818', 'pipe socket')
    m.cyl((13.4, 4.4, 14.6), 0.3, 11.6, 'y', 'paint:#e8e8e2', 'detector conduit')
    for y in (6.0, 10.0, 14.0):
        m.box((13.1, y, 14.2), (13.7, y + 0.4, 15.4), 'galv', 'conduit saddle')
    for i in range(6):
        m.box((4.2 + i * 1.3, 8.4, 12.16), (4.8 + i * 1.3, 8.8, 12.2), 'paint:#30e060' if i < 3 else 'paint:#f0c020', 'level bar segment', faces=('north',))
    m.terminal_12v(12.8, 4.0, 13.8, 'east')
    m.gland(7.0, 3.4, 13.8, 'down', r=0.3, length=0.8)
    f = m.surface('north', face=12.14, title='SMOKE DETECTOR')
    m.ctl(f, 'digits', 'obsc', 16 - 6.2, 7.3, w=4.0, h=0.7, col='ff4040', label='', fmt='%s')
    m.ctl(f, 'digits', 'co', 5.9, 7.3, w=3.2, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'id', 16 - 8.0, 6.5, w=3.6, h=0.6, col='7cf29a', label='', fmt='%s')
    lamp(m, f, 'alarm', 16 - 4.6, 10.0, 'ff2020', 'ALARM', 0.75, True)
    lamp(m, f, 'fault', 16 - 6.6, 10.0, 'ffb020', 'FAULT', 0.6)
    lamp(m, f, 'pwr', 16 - 8.4, 10.0, '30e060', 'PWR', 0.5)
    m.ctl(f, 'gauge', 'g_obs', 16 - 11.0, 9.6, w=2.2, h=2.2, col='f2f0e6', label='%/M x10', max=100)
    m.ctl(f, 'selector', 's_sens', 16 - 4.6, 4.6, w=1.1, h=1.1, col='202020', label='SENS', pos=['LOW', 'MED', 'HIGH'])
    m.ctl(f, 'selector', 's_iso', 16 - 6.6, 4.6, w=1.1, h=1.1, col='c0a040', label='ISOLATE', pos=['NORMAL', 'ISOLATED'])
    m.ctl(f, 'push', 'test', 16 - 8.5, 4.6, w=0.9, h=0.9, col='d02020', label='SMOKE TEST')
    m.ctl(f, 'push', 'lamp', 16 - 10.0, 4.6, w=0.5, h=0.5, col='f0f0f0', label='')
    m.save(item_gui=0.6)
    facing_states('smoke_detector')


# ------------------------------------------------------------------------------------------------ 16 tunnel light
def tunnel_light():
    m = Model('tunnel_light')
    m.g('bracket')
    for x in (2.0, 14.0):
        m.box((x - 1.0, 5.0, 15.4), (x + 1.0, 11.0, 16.0), 'galv', 'wall bracket plate')
        m.bolt(x, 6.0, 15.4, 'north', 0.3)
        m.bolt(x, 10.0, 15.4, 'north', 0.3)
        m.box((x - 0.3, 7.4, 13.6), (x + 0.3, 8.6, 15.4), 'galv', 'bracket arm')
    m.g('luminaire')
    m.box((0.8, 5.6, 12.2), (15.2, 10.4, 13.8), 'paint:#3a4248', 'die-cast housing')
    for i in range(14):
        m.box((1.0 + i * 1.0, 10.4, 12.6), (1.4 + i * 1.0, 11.2, 13.6), 'paint:#3a4248', 'heat sink fin')
    m.box((0.6, 5.4, 12.0), (15.4, 5.8, 13.9), 'paint:#2a2d30', 'housing lip bottom')
    m.box((0.6, 10.2, 12.0), (15.4, 10.6, 13.9), 'paint:#2a2d30', 'housing lip top')
    for x in (1.2, 5.0, 11.0, 14.8):
        m.box((x - 0.3, 5.3, 11.9), (x + 0.3, 10.7, 12.2), 'metal:steel', 'diffuser clip')
    m.box((1.0, 5.8, 12.0), (15.0, 10.2, 12.2), 'glass', 'polycarbonate diffuser')
    m.g('emergency pack')
    m.box((12.2, 4.2, 12.6), (15.0, 5.4, 13.6), 'paint:#2a2d30', 'battery pack')
    m.box((1.0, 4.4, 12.56), (7.0, 5.2, 12.6), 'label:EMERGENCY', 'emergency sticker', faces=('north',))
    m.gland(8.0, 5.4, 13.0, 'down', r=0.25, length=0.6)
    m.cyl((-8.0, 4.4, 14.8), 0.35, 32.0, 'x', 'paint:#e8e8e2', 'lighting circuit conduit')
    m.terminal_12v(0.8, 6.4, 13.0, 'west')
    f = m.surface('north', face=11.98, title='TUNNEL LIGHT')
    m.ctl(f, 'lamp', 'lens', 8.0, 8.0, w=13.4, h=3.9, col='fff6e0', label='')
    lamp(m, f, 'batt', 16 - 12.8, 4.8, '30e060', '', 0.35)
    lamp(m, f, 'chg', 16 - 13.6, 4.8, 'ffb020', '', 0.35)
    m.ctl(f, 'digits', 'pct', 16 - 10.8, 4.8, w=1.6, h=0.5, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'push', 'test', 16 - 14.6, 4.8, w=0.35, h=0.35, col='f0f0f0', label='')
    m.save(item_gui=0.6)
    facing_states('tunnel_light')


# ------------------------------------------------------------------------------------------------ 16 exit sign
def exit_sign():
    m = Model('exit_sign')
    m.g('bracket')
    m.box((3.0, 4.6, 15.4), (13.0, 11.4, 16.0), 'galv', 'wall plate')
    for x in (3.6, 12.4):
        for y in (5.2, 10.8):
            m.bolt(x, y, 15.4, 'north', 0.3)
    m.g('sign box')
    m.box((1.0, 4.4, 13.6), (15.0, 11.6, 15.4), 'paint:#f4f4ee', 'sign box')
    m.box((0.8, 4.2, 13.4), (15.2, 4.6, 15.6), 'paint:#d0d0ca', 'sign frame bottom')
    m.box((0.8, 11.4, 13.4), (15.2, 11.8, 15.6), 'paint:#d0d0ca', 'sign frame top')
    m.box((0.8, 4.6, 13.4), (1.2, 11.4, 15.6), 'paint:#d0d0ca', 'sign frame left')
    m.box((14.8, 4.6, 13.4), (15.2, 11.4, 15.6), 'paint:#d0d0ca', 'sign frame right')
    m.box((1.2, 4.6, 13.4), (14.8, 11.4, 13.6), 'paint:#14843a', 'green legend panel')
    m.g('running man')
    man = [((7.2, 10.0), (7.9, 10.7)), ((6.9, 8.4), (7.7, 9.9)), ((7.7, 9.0), (8.8, 9.4)), ((8.8, 9.4), (9.2, 9.9)),
           ((6.0, 8.8), (6.9, 9.2)), ((5.6, 8.3), (6.0, 8.9)), ((6.9, 7.6), (7.5, 8.4)), ((7.5, 6.9), (8.4, 7.4)),
           ((8.2, 6.1), (8.6, 7.0)), ((6.2, 7.2), (6.9, 7.7)), ((5.6, 6.6), (6.2, 7.3))]
    for (a, b) in man:
        m.box((16 - b[0], a[1], 13.32), (16 - a[0], b[1], 13.4), 'paint:#ffffff', 'running man')
    m.box((16 - 11.6, 6.0, 13.32), (16 - 9.8, 10.4, 13.4), 'paint:#ffffff', 'door frame')
    m.box((16 - 11.3, 6.2, 13.28), (16 - 10.1, 10.2, 13.32), 'paint:#14843a', 'door opening')
    m.g('battery')
    m.box((4.0, 3.6, 14.0), (12.0, 4.4, 15.2), 'paint:#2a2d30', 'battery tray')
    m.terminal_12v(15.0, 5.0, 14.4, 'east')
    f = m.surface('north', face=13.26, title='EXIT')
    m.ctl(f, 'lamp', 'man', 8.0, 8.4, w=3.4, h=3.4, col='30e060', label='')
    m.ctl(f, 'lamp', 'arl', 2.6, 9.6, w=1.8, h=1.4, col='e0ffe0', label='<')
    m.ctl(f, 'lamp', 'arr', 13.4, 9.6, w=1.8, h=1.4, col='e0ffe0', label='>')
    m.ctl(f, 'digits', 'left', 3.8, 5.6, w=5.4, h=0.8, col='ffffff', label='', fmt='%s')
    m.ctl(f, 'digits', 'right', 12.2, 5.6, w=5.4, h=0.8, col='ffffff', label='', fmt='%s')
    m.save(item_gui=0.6)
    facing_states('exit_sign')


# ------------------------------------------------------------------------------------------------ 16 cross-passage door
def cross_passage():
    m = Model('cross_passage')
    m.g('portal frame')
    m.box((0.0, 0.0, 10.0), (2.4, 30.0, 16.0), 'concrete', 'frame jamb left')
    m.box((13.6, 0.0, 10.0), (16.0, 30.0, 16.0), 'concrete', 'frame jamb right')
    m.box((0.0, 26.0, 10.0), (16.0, 32.0, 16.0), 'concrete', 'frame head')
    m.box((2.4, 0.0, 15.0), (13.6, 0.4, 16.0), 'concrete', 'threshold')
    m.box((2.2, 0.0, 11.6), (2.6, 26.2, 12.6), 'metal:steel', 'steel door frame L')
    m.box((13.4, 0.0, 11.6), (13.8, 26.2, 12.6), 'metal:steel', 'steel door frame R')
    m.box((2.2, 25.8, 11.6), (13.8, 26.2, 12.6), 'metal:steel', 'steel door frame head')
    m.g('door leaf')
    m.box((2.6, 0.4, 12.2), (13.4, 25.8, 12.8), 'paint:#14843a', 'fire door leaf')
    for y in (4.0, 13.0, 22.0):
        m.box((2.6, y, 12.0), (13.4, y + 0.3, 12.2), 'paint:#10702f', 'leaf stiffener')
    m.box((5.0, 16.0, 11.96), (11.0, 22.0, 12.2), 'glass', 'wired-glass vision panel')
    m.box((4.8, 15.8, 11.9), (11.2, 16.0, 12.2), 'metal:steel', 'vision bead')
    m.box((4.8, 22.0, 11.9), (11.2, 22.2, 12.2), 'metal:steel', 'vision bead')
    m.box((3.4, 11.0, 11.4), (12.6, 11.6, 11.8), 'metal:steel', 'panic push bar')
    for x in (3.6, 12.4):
        m.box((x - 0.3, 10.8, 11.8), (x + 0.3, 11.8, 12.2), 'metal:steel', 'push bar bracket')
    m.box((3.0, 24.6, 11.4), (8.0, 25.2, 12.0), 'paint:#2a2d30', 'door closer')
    m.box((7.6, 24.8, 11.0), (11.0, 25.0, 11.4), 'paint:#2a2d30', 'closer arm')
    for y in (3.0, 13.0, 23.0):
        m.box((13.2, y, 12.0), (13.6, y + 1.6, 12.9), 'metal:steel', 'hinge')
    m.g('signs')
    m.box((3.0, 27.0, 9.9), (13.0, 29.4, 10.0), 'paint:#14843a', 'sign plate', faces=('north',))
    m.box((3.4, 27.4, 9.86), (12.6, 29.0, 9.9), 'label:CROSS PASSAGE', 'sign text', faces=('north',))
    m.box((4.0, 23.0, 11.86), (12.0, 24.4, 11.96), 'label:REFUGE', 'refuge sign', faces=('north',))
    m.g('door panel')
    m.box((14.0, 9.0, 9.0), (16.0, 19.0, 10.0), 'paint:#d9dbd6', 'door control panel')
    m.box((14.1, 9.2, 8.94), (15.9, 18.8, 9.0), LIGHT, 'door panel face')
    m.terminal_12v(16.0, 9.6, 9.6, 'east')
    m.g('pressurisation')
    m.box((3.0, 30.0, 9.4), (8.0, 31.6, 10.0), 'louvre', 'pressurisation grille')
    m.box((9.0, 30.0, 9.4), (13.0, 31.6, 10.0), 'louvre', 'relief damper')
    m.g('refuge equipment')
    m.box((2.8, 0.6, 11.9), (13.2, 3.0, 12.2), 'metal:steel', 'kick plate')
    m.cyl((0.6, 2.0, 8.6), 0.9, 5.6, 'y', 'paint:#c81e1e', 'fire extinguisher')
    m.cyl((0.6, 7.6, 8.6), 0.4, 0.8, 'y', 'paint:#1a1a1a', 'extinguisher valve')
    m.box((0.1, 7.8, 8.4), (0.5, 8.2, 8.8), 'paint:#1a1a1a', 'extinguisher handle')
    m.cyl((1.3, 5.0, 8.6), 0.15, 3.0, 'y', 'paint:#1a1a1a', 'extinguisher hose')
    m.box((0.0, 1.0, 9.4), (1.2, 1.6, 10.0), 'galv', 'extinguisher bracket')
    m.box((0.0, 5.8, 9.4), (1.2, 6.4, 10.0), 'galv', 'extinguisher bracket')
    m.box((0.2, 10.0, 9.2), (2.2, 12.4, 10.0), 'paint:#14843a', 'first aid box')
    m.box((0.9, 10.6, 9.16), (1.5, 11.8, 9.2), 'paint:#ffffff', 'first aid cross v', faces=('north',))
    m.box((0.6, 11.0, 9.16), (1.8, 11.4, 9.2), 'paint:#ffffff', 'first aid cross h', faces=('north',))
    m.box((2.4, 0.0, 9.0), (13.6, 0.05, 11.6), 'haz', 'floor hatching', faces=('up',))
    m.box((7.4, 12.6, 11.9), (8.6, 13.4, 12.2), 'metal:steel', 'door pull')
    for y in (1.0, 3.5, 6.0, 8.5, 11.0, 13.5, 16.0, 18.5, 21.0, 23.5, 26.0):
        m.bolt(1.2, y, 10.0, 'north', 0.3)
        m.bolt(14.8, y, 10.0, 'north', 0.3)
    f = m.surface('north', face=8.92, title='CROSS PASSAGE')
    m.ctl(f, 'digits', 'id', 16 - 15.0, 18.0, w=1.6, h=0.6, col='7cf29a', label='', fmt='%s')
    lamp(m, f, 'open', 16 - 14.6, 16.8, '30e060', 'OPEN', 0.5)
    lamp(m, f, 'shut', 16 - 15.4, 16.8, 'ff3030', 'SHUT', 0.5)
    lamp(m, f, 'press', 16 - 14.6, 15.6, '4080ff', 'PRESS', 0.5)
    lamp(m, f, 'occ', 16 - 15.4, 15.6, 'ffb020', 'OCC', 0.5)
    lamp(m, f, 'emerg', 16 - 14.6, 14.4, 'ff2020', 'EMRG', 0.5, True)
    lamp(m, f, 'pwr', 16 - 15.4, 14.4, '30e060', '12V', 0.45)
    m.ctl(f, 'digits', 'occn', 16 - 15.0, 13.2, w=1.7, h=0.5, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'push', 'p_open', 16 - 15.0, 11.4, w=1.3, h=1.3, col='1fa34a', label='OPEN')
    m.ctl(f, 'push', 'test', 16 - 15.0, 9.8, w=0.35, h=0.35, col='f0f0f0', label='')
    m.save(item_gui=0.4)
    facing_states('cross_passage')


# ------------------------------------------------------------------------------------------------ 16 leaky feeder
def leaky_feeder():
    m = Model('leaky_feeder')
    m.g('mounting')
    m.box((2.0, 0.6, 15.4), (14.0, 14.6, 16.0), 'galv', 'wall plate')
    for x in (2.6, 13.4):
        for y in (1.2, 14.0):
            m.bolt(x, y, 15.4, 'north', 0.3)
    m.g('repeater')
    m.box((2.6, 1.4, 10.4), (13.4, 13.4, 15.4), 'paint:#e8e8e2', 'GSM-R repeater cabinet')
    m.box((2.4, 13.4, 10.2), (13.6, 13.8, 15.6), 'paint:#d0d0ca', 'cabinet lid')
    for i in range(10):
        m.box((13.4, 2.0 + i * 1.1, 11.0), (14.6, 2.4 + i * 1.1, 15.0), 'fins', 'heat sink fin')
    m.box((2.8, 1.6, 10.2), (13.2, 13.2, 10.4), LIGHT, 'repeater face')
    m.box((3.2, 12.0, 10.16), (12.8, 12.8, 10.2), 'label:TUNNEL RADIO', 'legend', faces=('north',))
    m.box((3.2, 2.0, 10.16), (8.0, 2.6, 10.2), 'label:GSM-R', 'band label', faces=('north',))
    m.g('feeders')
    for x in (5.0, 11.0):
        m.cyl((x, 13.8, 12.8), 0.5, 0.8, 'y', 'metal:brass', 'N-type connector')
        m.cyl((x, 14.6, 12.8), 0.4, 1.4, 'y', 'paint:#1a1a1a', 'feeder jumper')
    m.cyl((-8.0, 16.0, 12.8), 0.55, 32.0, 'x', 'paint:#1a1a1a', 'radiating (leaky) cable')
    for x in (-6.0, -2.0, 2.0, 6.0, 10.0, 14.0, 18.0, 22.0):
        m.box((x - 0.3, 15.2, 12.0), (x + 0.3, 16.8, 13.6), 'paint:#c81e1e', 'fire-rated cable clip')
    for x in (-7.0, -5.0, -3.0, -1.0, 1.0, 3.0, 9.0, 13.0, 15.0, 17.0, 21.0, 23.0):
        m.box((x, 15.9, 12.2), (x + 0.6, 16.1, 12.24), 'paint:#3a3a3a', 'cable slot (radiating aperture)', faces=('north',))
    m.terminal_12v(2.6, 2.4, 12.8, 'west')
    m.g('backup + fibre')
    m.box((2.6, -3.0, 11.0), (8.6, 1.0, 15.4), 'paint:#3a4248', 'battery backup box')
    for z in (11.6, 12.6, 13.6, 14.6):
        m.box((2.5, -2.6, z), (2.6, 0.4, z + 0.5), 'paint:#1a1a1a', 'battery vent')
    m.box((9.4, -2.2, 12.0), (13.4, 1.0, 15.4), 'paint:#e8e8e2', 'fibre splice box')
    m.cyl((11.4, -3.6, 13.6), 0.25, 1.4, 'y', 'paint:#f0c020', 'fibre optic cable')
    m.box((9.6, -1.4, 11.96), (13.2, -0.8, 12.0), 'label:FIBRE', 'fibre label', faces=('north',))
    for i in range(4):
        m.box((3.4 + i * 1.2, 3.0, 10.16), (4.0 + i * 1.2, 3.6, 10.2), 'paint:#2a2d30', 'RF test port', faces=('north',))
    m.gland(8.0, 1.4, 12.8, 'down', r=0.3, length=0.8)
    f = m.surface('north', face=10.14, title='TUNNEL RADIO')
    lamp(m, f, 'pwr', 16 - 4.2, 10.6, '30e060', 'PWR', 0.6)
    lamp(m, f, 'tx', 16 - 6.0, 10.6, '30e060', 'TX', 0.6)
    lamp(m, f, 'fault', 16 - 7.8, 10.6, 'ff2020', 'FAULT', 0.6, True)
    m.ctl(f, 'digits', 'cell', 16 - 8.0, 8.8, w=7.6, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'links', 16 - 5.4, 7.4, w=3.6, h=0.8, col='ffb020', label='', fmt='%d TRN')
    m.ctl(f, 'digits', 'rssi', 16 - 10.4, 7.4, w=4.4, h=0.8, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'selector', 's_power', 16 - 5.0, 4.6, w=1.6, h=1.6, col='202020', label='CABLE RUN', pos=['128M', '192M', '256M'])
    m.ctl(f, 'push', 'test', 16 - 9.6, 4.6, w=0.7, h=0.7, col='f0f0f0', label='LAMPS')
    m.save(item_gui=0.6)
    facing_states('leaky_feeder')


# ------------------------------------------------------------------------------------------------ tunnel portal name plate
def tunnel_portal():
    m = Model('tunnel_portal')
    m.g('headwall')
    m.box((0, 0, 1.0), (16, 16, 16), 'paint:#8e8a80', 'dressed stone headwall')
    for row in range(4):                                         # rusticated ashlar courses, staggered joints
        y = row * 4.0
        off = 0 if row % 2 == 0 else 4.0
        for k in range(-1, 4):
            x0 = off + k * 8.0
            a, b = max(0.0, x0 + 0.25), min(16.0, x0 + 7.75)
            if b - a < 0.6: continue
            m.box((a, y + 0.25, 0.6), (b, y + 3.75, 1.0), 'paint:#a19c90', 'ashlar block')
    m.box((-0.4, 15.0, 0.0), (16.4, 16.0, 1.0), 'paint:#7a766c', 'string course')
    m.g('name plate')
    m.box((1.4, 6.0, 0.2), (14.6, 13.6, 0.6), CAST, 'cast name plate')
    m.box((1.2, 5.8, 0.0), (14.8, 6.2, 0.6), CAST, 'plate rim')
    m.box((1.2, 13.4, 0.0), (14.8, 13.8, 0.6), CAST, 'plate rim')
    m.box((1.2, 6.2, 0.0), (1.6, 13.4, 0.6), CAST, 'plate rim')
    m.box((14.4, 6.2, 0.0), (14.8, 13.4, 0.6), CAST, 'plate rim')
    for x in (2.0, 14.0):
        for y in (6.6, 12.8):
            m.bolt(x, y, 0.2, 'north', 0.3)
    m.box((1.6, 6.2, 0.16), (14.4, 13.4, 0.2), 'paint:#20262a', 'plate field', faces=('north',))
    f = m.surface('north', face=0.14, title='PORTAL')
    m.ctl(f, 'digits', 'name', 8.0, 11.6, w=12.0, h=1.6, col='f4f0d8', label='', fmt='%s')
    m.ctl(f, 'digits', 'end', 8.0, 9.4, w=10.0, h=1.0, col='f4f0d8', label='', fmt='%s')
    m.ctl(f, 'digits', 'len', 8.0, 7.6, w=10.0, h=1.0, col='f4f0d8', label='', fmt='%s')
    m.save(item_gui=0.6)
    facing_states('tunnel_portal')


# ------------------------------------------------------------------------------------------------ tunnel distance marker
def tunnel_marker():
    m = Model('tunnel_marker')
    m.box((3.0, 4.0, 15.4), (13.0, 12.0, 16.0), 'paint:#f4f4ee', 'enamel plate')
    m.box((2.8, 3.8, 15.2), (13.2, 4.1, 16.0), 'paint:#1a1a1a', 'plate border')
    m.box((2.8, 11.9, 15.2), (13.2, 12.2, 16.0), 'paint:#1a1a1a', 'plate border')
    m.box((2.8, 4.1, 15.2), (3.1, 11.9, 16.0), 'paint:#1a1a1a', 'plate border')
    m.box((12.9, 4.1, 15.2), (13.2, 11.9, 16.0), 'paint:#1a1a1a', 'plate border')
    for x in (3.6, 12.4):
        for y in (4.6, 11.4):
            m.bolt(x, y, 15.4, 'north', 0.25)
    m.box((3.4, 10.4, 15.36), (12.6, 11.1, 15.4), 'label:DISTANCE KM', 'legend', faces=('north',))
    f = m.surface('north', face=15.34, title='MARKER')
    m.ctl(f, 'digits', 'km', 8.0, 8.8, w=8.4, h=1.6, col='101010', label='', fmt='%s')
    m.ctl(f, 'digits', 'from', 8.0, 6.6, w=8.4, h=0.7, col='202020', label='', fmt='%s')
    m.ctl(f, 'digits', 'exit', 8.0, 5.4, w=8.4, h=0.7, col='14843a', label='', fmt='%s')
    m.save(item_gui=0.7)
    facing_states('tunnel_marker')


# ------------------------------------------------------------------------------------------------ 18 survey peg
def survey_peg():
    m = Model('survey_peg')
    m.box((7.2, -2.0, 7.2), (8.8, 9.0, 8.8), 'wood', 'timber stake')
    m.box((7.4, -3.0, 7.4), (8.6, -2.0, 8.6), 'wood', 'stake point')
    m.box((7.0, 9.0, 7.0), (9.0, 9.6, 9.0), 'paint:#ff4fa0', 'painted top')
    m.box((8.8, 8.0, 7.9), (11.6, 8.3, 8.1), 'paint:#ff4fa0', 'flagging tape')
    m.box((11.0, 6.6, 7.9), (11.6, 8.0, 8.1), 'paint:#ff4fa0', 'flagging tape tail')
    m.box((3.4, 2.4, 6.9), (12.6, 7.4, 7.2), 'wood', 'peg board')
    m.box((3.6, 2.6, 6.86), (12.4, 7.2, 6.9), 'paint:#f4f4ee', 'board paint', faces=('north',))
    for x in (4.2, 11.8):
        m.bolt(x, 4.9, 6.9, 'north', 0.3)
    m.box((6.8, 0.0, 6.8), (9.2, 0.4, 9.2), 'paint:#6b5a3a', 'trodden earth')
    f = m.surface('north', face=6.84, title='PEG')
    m.ctl(f, 'digits', 'ch', 8.0, 6.4, w=8.2, h=0.9, col='101010', label='', fmt='%s')
    m.ctl(f, 'digits', 'lev', 8.0, 5.0, w=8.2, h=0.9, col='c81e1e', label='', fmt='%s')
    m.ctl(f, 'digits', 'info', 8.0, 3.6, w=8.2, h=0.8, col='202060', label='', fmt='%s')
    m.save(item_gui=0.7)
    facing_states('survey_peg')


# ------------------------------------------------------------------------------------------------ tunnel lining (vanilla textures)
TEX = {'concrete': 'minecraft:blocks/concrete_silver', 'brick': 'minecraft:blocks/brick', 'stone': 'minecraft:blocks/stonebrick'}
JOINT = 'minecraft:blocks/concrete_gray'


def el(a, b, tex='#m', name='', faces='nsewud', rot=None):
    names = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}
    e = {'__comment': name, 'from': list(a), 'to': list(b), 'faces': {}}
    for c in faces:
        d = names[c]
        if d in ('north', 'south'): uv = [a[0], 16 - b[1], b[0], 16 - a[1]]
        elif d in ('east', 'west'): uv = [a[2], 16 - b[1], b[2], 16 - a[1]]
        else: uv = [a[0], a[2], b[0], b[2]]
        uv = [max(0, min(16, v)) for v in uv]
        e['faces'][d] = {'uv': uv, 'texture': tex}
    if rot: e['rotation'] = rot
    return e


def save_json_model(name, els, textures):
    model = {'credit': 'IR Extras tunnel lining (tools/gen_bb_track.py)', 'ambientocclusion': True, 'textures': textures, 'elements': els}
    json.dump(model, open(os.path.join(ASSETS, 'models/block', name + '.json'), 'w'), indent=0)


def lining(mat):
    tex = {'m': TEX[mat], 'j': JOINT, 'particle': TEX[mat]}
    seg = mat == 'concrete'
    # WALL: inner face north at z=6
    els = [el((0, 0, 6), (16, 16, 16), name='wall body')]
    if seg:
        els += [el((0, 0, 5.9), (0.4, 16, 6), '#j', 'ring joint', 'n'), el((15.6, 0, 5.9), (16, 16, 6), '#j', 'ring joint', 'n'),
                el((0.4, 7.8, 5.9), (15.6, 8.2, 6), '#j', 'segment joint', 'n')]
        for (x, y) in ((3, 3.5), (13, 3.5), (3, 12.5), (13, 12.5)):
            els.append(el((x - 0.8, y - 0.5, 5.8), (x + 0.8, y + 0.5, 6), '#j', 'bolt pocket', 'nsewud'))
    else:
        els.append(el((0, 0, 5.6), (16, 0.6, 6), name='plinth course', faces='nud'))
    save_json_model('tunnel_%s_wall' % mat, els, tex)
    # HAUNCH: stepped quarter-curve from the wall face (z 6) to the roof (z 0 at the top)
    steps = [(0, 3, 6.0), (3, 6, 5.2), (6, 9, 4.2), (9, 11.5, 3.0), (11.5, 13.5, 1.8), (13.5, 16, 0.0)]
    els = [el((0, y0, z), (16, y1, 16), name='haunch voussoir') for (y0, y1, z) in steps]
    if seg:
        els += [el((0, 0, 5.9), (0.4, 16, 6), '#j', 'ring joint', 'n'), el((15.6, 0, 5.9), (16, 16, 6), '#j', 'ring joint', 'n')]
        els.append(el((7.2, 9.4, 4.1), (8.8, 10.4, 4.2), '#j', 'bolt pocket', 'n'))
    save_json_model('tunnel_%s_haunch' % mat, els, tex)
    # CROWN: the roof slab, its soffit flush with the bottom of the block (meets the haunch tops)
    els = [el((0, 0, 0), (16, 10, 16), name='crown slab')]
    if seg:
        els += [el((0, -0.1, 0), (0.4, 0, 16), '#j', 'ring joint', 'd'), el((15.6, -0.1, 0), (16, 0, 16), '#j', 'ring joint', 'd'),
                el((0.4, -0.1, 7.8), (15.6, 0, 8.2), '#j', 'key segment joint', 'd')]
        for (x, z) in ((4, 4), (12, 4), (4, 12), (12, 12)):
            els.append(el((x - 0.8, -0.2, z - 0.5), (x + 0.8, 0, z + 0.5), '#j', 'bolt pocket', 'nsewd'))
    else:
        els.append(el((6.5, -0.4, 0), (9.5, 0, 16), name='keystone course', faces='nsewd'))
    save_json_model('tunnel_%s_crown' % mat, els, tex)
    # NICHE: a refuge recess in the wall, whitewashed, REFUGE sign
    t2 = dict(tex)
    t2['w'] = 'minecraft:blocks/concrete_white'
    t2['s'] = 'irextras:blocks/refuge_sign'
    els = [el((0, 0, 13), (16, 16, 16), name='niche back'), el((0, 0, 6), (2, 16, 13), name='niche cheek L'), el((14, 0, 6), (16, 16, 13), name='niche cheek R'),
           el((2, 13, 6), (14, 16, 13), name='niche lintel'), el((2, 0.01, 12.9), (14, 13, 13), '#w', 'whitewash back', 'n'),
           el((1.99, 0, 6), (2, 13, 13), '#w', 'whitewash side', 'e'), el((14, 0, 6), (14.01, 13, 13), '#w', 'whitewash side', 'w'),
           el((2, 12.99, 6), (14, 13, 13), '#w', 'whitewash soffit', 'd'), el((4, 8.5, 12.8), (12, 12, 12.9), '#s', 'REFUGE sign', 'n'),
           el((3, 4, 12.6), (3.4, 9, 12.9), '#j', 'grab handle', 'nsewud')]
    for e in els:
        if e['__comment'] == 'REFUGE sign': e['faces']['north']['uv'] = [0, 0, 16, 8]   # the sign is the top half of a square texture
    save_json_model('tunnel_%s_niche' % mat, els, t2)
    for p in ('wall', 'haunch', 'crown', 'niche'):
        facing_states('tunnel_%s_%s' % (mat, p))


def cable_trough():
    tex = {'m': 'minecraft:blocks/concrete_silver', 'j': 'minecraft:blocks/concrete_gray', 'r': 'minecraft:blocks/concrete_red',
           'k': 'minecraft:blocks/concrete_black', 'particle': 'minecraft:blocks/concrete_silver'}
    els = [el((0, 0, 3), (16, 3.6, 3.8), name='trough wall'), el((0, 0, 14.2), (16, 3.6, 15), name='trough wall'), el((0, 0, 3.8), (16, 0.8, 14.2), name='trough floor')]
    for k, (z, t) in enumerate(((5.0, '#k'), (6.6, '#r'), (8.2, '#k'), (9.8, '#k'), (11.4, '#r'), (12.8, '#k'))):
        els.append(el((-0.01, 0.8, z), (16.01, 2.0, z + 1.2), t, 'cable', 'nsewu'))
    for i in range(4):
        x0 = i * 4 + 0.1
        els.append(el((x0, 3.6, 3), (x0 + 3.8, 4.6, 15), name='trough lid'))
        els.append(el((x0 + 1.5, 4.6, 8.4), (x0 + 2.3, 4.62, 9.6), '#j', 'lifting slot', 'u'))
    save_json_model('cable_trough', els, tex)
    facing_states('cable_trough')


def refuge_texture():
    from bbkit import FONT
    img = Image.new('RGBA', (64, 32), (20, 132, 58, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 63, 31), outline=(240, 240, 236, 255))
    d.rectangle((2, 2, 61, 29), outline=(240, 240, 236, 255))
    x = (64 - (6 * 6 - 1) * 1) // 2
    for ch in 'REFUGE':
        for r, row in enumerate(FONT[ch]):
            for c, bit in enumerate(row):
                if bit == '1':
                    for dy in range(2):
                        img.putpixel((x + c, 9 + r * 2 + dy), (240, 240, 236, 255))
        x += 6
    sq = Image.new('RGBA', (64, 64), (0, 0, 0, 0))                     # 1.12 wants square textures
    sq.paste(img, (0, 0))
    sq.save(os.path.join(ASSETS, 'textures/blocks/refuge_sign.png'))


# ------------------------------------------------------------------------------------------------ handheld items
def item_textures():
    os.makedirs(os.path.join(ASSETS, 'textures/items'), exist_ok=True)

    def save(name, px):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for (x, y, c) in px: img.putpixel((x, y), c)
        img.save(os.path.join(ASSETS, 'textures/items', name + '.png'))
        json.dump({'parent': 'item/handheld', 'textures': {'layer0': 'irextras:items/' + name}}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)

    Y, Yd, K, G, W, R, B, S = (240, 196, 24, 255), (180, 140, 10, 255), (30, 30, 34, 255), (110, 116, 122, 255), (236, 236, 228, 255), (200, 40, 30, 255), (60, 160, 255, 255), (170, 176, 182, 255)
    px = []
    for x in range(1, 15): px += [(x, 10, Y), (x, 11, Yd)]                # trolley beam across the rails
    for x in (2, 13):
        for y in range(4, 10): px.append((x, y, Y))                        # handle uprights
    for x in range(2, 14): px.append((x, 3, Y))                            # push handle
    for x in (1, 2, 3, 12, 13, 14): px += [(x, 12, K), (x, 13, K)]         # wheels
    px += [(2, 14, G), (13, 14, G)]
    for x in range(6, 10):
        for y in range(5, 9): px.append((x, y, K))                         # recording unit
    px += [(7, 6, B), (8, 6, B), (7, 7, (124, 242, 154, 255))]
    for y in range(8, 10): px.append((8, y, G))
    save('track_trolley', px)
    px = []
    for x in range(2, 14):
        for y in range(7, 14): px.append((x, y, R if y > 8 else (160, 28, 22, 255)))   # gang tool box
    for x in range(5, 11): px += [(x, 5, K), (x, 6, K)]
    px += [(5, 4, K), (10, 4, K)]
    for i in range(6): px.append((3 + i, 3 + i // 3, S))                   # shovel shaft poking out
    px += [(12, 1, S), (13, 1, S), (12, 2, S), (13, 2, S), (11, 3, S), (10, 4, S)]   # spanner
    for x in range(2, 14): px.append((x, 10, Y))                           # hi-vis stripe
    save('track_kit', px)
    px = []
    for y in range(9, 16):                                                 # tripod
        px += [(8 - (y - 9) // 2 - 1, y, Yd), (8, y, Yd), (8 + (y - 9) // 2 + 1, y, Yd)]
    for x in range(5, 12): px.append((x, 8, K))                            # tribrach
    for x in range(5, 11):
        for y in range(2, 8): px.append((x, y, Y))                         # instrument body
    for y in range(3, 7): px += [(4, y, K)]
    px += [(10, 4, K), (11, 4, K), (12, 4, K), (12, 3, B)]                 # telescope + lens
    px += [(6, 4, (124, 242, 154, 255)), (7, 4, (124, 242, 154, 255)), (6, 5, K), (7, 5, K)]   # screen
    px += [(7, 1, R), (8, 1, R)]
    save('surveyor', px)


# ------------------------------------------------------------------------------------------------ recipes
def recipes():
    rdir = os.path.join(ASSETS, 'recipes')

    def r(name, pattern, key, count=1):
        k = {}
        for c, it in key.items():
            if isinstance(it, tuple): k[c] = {'item': it[0], 'data': it[1]}
            else: k[c] = {'item': it}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': k, 'result': {'item': 'irextras:' + name, 'count': count}},
                  open(os.path.join(rdir, name + '.json'), 'w'), indent=1)

    I, RS, GL, RE, QZ = 'minecraft:iron_ingot', 'minecraft:redstone', ('minecraft:stained_glass_pane', 13), 'minecraft:repeater', 'minecraft:quartz'
    r('point_machine', ['IRI', 'PCP', 'III'], {'I': I, 'R': RS, 'P': 'minecraft:piston', 'C': 'minecraft:comparator'})
    r('structure_board', ['III', 'ICI', 'S S'], {'I': I, 'C': 'minecraft:comparator', 'S': 'minecraft:stick'})
    r('crossing_predictor', ['IRI', 'ICI', 'IBI'], {'I': I, 'R': RE, 'C': 'minecraft:comparator', 'B': 'minecraft:noteblock'})
    r('tunnel_control', ['IGI', 'CRC', 'III'], {'I': I, 'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'R': RS})
    r('tunnel_fan', ['III', 'IPI', 'III'], {'I': 'minecraft:iron_bars', 'P': 'minecraft:piston'})
    r('smoke_detector', ['III', 'IQI', ' R '], {'I': I, 'Q': QZ, 'R': RS})
    r('tunnel_light', ['III', 'GTG'], {'I': I, 'G': 'minecraft:glass_pane', 'T': 'minecraft:glowstone_dust'}, count=4)
    r('exit_sign', ['GGG', 'GTG'], {'G': GL, 'T': 'minecraft:glowstone_dust'}, count=4)
    r('cross_passage', ['III', 'IDI', 'III'], {'I': I, 'D': 'minecraft:iron_door'})
    r('leaky_feeder', ['ITI', 'IRI', 'III'], {'I': I, 'T': 'minecraft:redstone_torch', 'R': RE})
    r('tunnel_portal', ['SSS', 'SIS', 'SSS'], {'S': ('minecraft:stonebrick', 0), 'I': I})
    r('tunnel_marker', ['QQQ', 'QIQ'], {'Q': QZ, 'I': 'minecraft:iron_nugget'}, count=4)
    r('survey_peg', ['P', 'S'], {'P': ('minecraft:dye', 9), 'S': 'minecraft:stick'}, count=8)
    mats = {'concrete': ('minecraft:concrete', 8), 'brick': 'minecraft:brick_block', 'stone': ('minecraft:stone', 0)}
    for mat, it in mats.items():
        r('tunnel_%s_wall' % mat, ['MM', 'MM', 'MM'], {'M': it}, count=8)
        r('tunnel_%s_haunch' % mat, ['M  ', 'MM ', 'MMM'], {'M': it}, count=8)
        r('tunnel_%s_crown' % mat, ['MMM', 'MMM'], {'M': it}, count=8)
        r('tunnel_%s_niche' % mat, ['MMM', 'M M', 'MMM'], {'M': it}, count=4)
    r('cable_trough', ['C C', 'CRC'], {'C': ('minecraft:stone_slab', 0), 'R': RS}, count=8)
    r('track_trolley', ['I  ', 'ICI', 'B B'], {'I': I, 'C': 'minecraft:compass', 'B': 'minecraft:iron_bars'})
    r('track_kit', ['ISI', 'WCW', 'WWW'], {'I': I, 'S': 'minecraft:iron_shovel', 'W': ('minecraft:wool', 1), 'C': 'minecraft:chest'})
    r('surveyor', ['GCG', ' I ', 'S S'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:compass', 'I': I, 'S': 'minecraft:stick'})


if __name__ == '__main__':
    for f in (point_machine, structure_board, crossing_predictor, tunnel_control, tunnel_fan, smoke_detector, tunnel_light, exit_sign,
              cross_passage, leaky_feeder, tunnel_portal, tunnel_marker, survey_peg):
        f()
    for mat in TEX: lining(mat)
    cable_trough()
    refuge_texture()
    item_textures()
    recipes()
    print('track infrastructure assets written')
