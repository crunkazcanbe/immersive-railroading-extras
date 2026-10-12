#!/usr/bin/env python3
"""Track infrastructure, her ChatGPT batch 3: #9 intelligent buffer stop, #7 single-line / gauntlet section controller,
#25 yard retarder. Front = north. Every lamp, button, key, selector, dial and display is a live control (m.ctl) that the
Java tile reads or works (com.dogpound.railmap.track.TileBufferStop / TileSingleLine, com.dogpound.railmap.yard.
TileRetarder). The 12 V terminal boxes are wire terminals; the retarder's grey isolator with the DANGER plate is where
its 400 V compressor supply lands (a touching grid Power Cable).
Run: python3 tools/gen_bb_track3.py   (writes models, textures, controls, blockstates, item models, recipes)"""
import json, os
from bbkit import Model, ASSETS
from gen_bb_track import facing_states, lamp

SIGNAL_GREY = 'ral7032'
LIGHT = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
RED = 'paint:#b01e1e'
YEL = 'paint:#e8b820'
STEEL = 'metal:steel'


# ------------------------------------------------------------------------------------------------ 9 buffer stop
def buffer_stop():
    m = Model('buffer_stop')
    m.g('track end')
    for z in (2.0, 9.0, 16.0, 23.0, 28.8):                       # sleepers under the stop
        m.box((-12.0, 0, z), (28.0, 1.4, z + 3.2), 'concrete', 'concrete sleeper')
    for x in (-3.5, 19.5):                                       # the running rails, ending in the stop
        m.box((x - 0.5, 1.4, -2.0), (x + 0.5, 1.8, 32.0), STEEL, 'rail foot')
        m.box((x - 0.18, 1.8, -2.0), (x + 0.18, 3.0, 32.0), STEEL, 'rail web')
        m.box((x - 0.45, 3.0, -2.0), (x + 0.45, 3.6, 32.0), 'metal:steel', 'rail head')
    m.g('friction shoes')
    for x in (-3.5, 19.5):                                       # clamped to the rails: they slide when it is struck
        for z in (3.0, 10.0, 17.0, 24.0):
            m.box((x - 1.6, 1.6, z), (x - 0.5, 4.0, z + 5.0), 'paint:#3a3f44', 'friction shoe (outer)')
            m.box((x + 0.5, 1.6, z), (x + 1.6, 4.0, z + 5.0), 'paint:#3a3f44', 'friction shoe (inner)')
            for zz in (z + 1.0, z + 4.0):
                m.box((x - 2.0, 2.4, zz - 0.3), (x + 2.0, 2.8, zz + 0.3), STEEL, 'clamp bolt')
                m.bolt(x - 2.0, 2.6, zz, 'west', 0.4)
                m.bolt(x + 2.0, 2.6, zz, 'east', 0.4)
        m.box((x - 1.7, 4.0, 3.0), (x + 1.7, 4.4, 29.0), YEL, 'shoe tie bar')
    m.g('frame')
    for x in (-3.5, 19.5):                                       # main longitudinal members (rail-built)
        m.box((x - 1.0, 4.4, 4.0), (x + 1.0, 7.0, 28.0), RED, 'longitudinal')
        for i in range(6):                                       # the inclined struts, stepped
            z0 = 6.0 + i * 3.6
            y1 = 20.0 - i * 2.4
            m.box((x - 0.8, 7.0, z0), (x + 0.8, y1, z0 + 3.0), RED, 'inclined strut')
        for z in (6.0, 14.0, 22.0):
            m.box((x - 1.4, 7.0, z), (x + 1.4, 8.0, z + 1.2), RED, 'gusset')
    for z in (8.0, 18.0):
        m.box((-3.0, 8.0, z), (19.0, 9.4, z + 1.4), RED, 'cross brace')
    m.box((-1.0, 13.0, 12.0), (17.0, 14.0, 13.0), RED, 'upper brace')
    m.g('headstock')
    m.box((-12.0, 12.0, 0.0), (28.0, 20.0, 4.0), RED, 'headstock beam')
    for i in range(10):                                          # hazard chevrons on the beam's face
        x = -11.5 + i * 3.9
        m.box((x, 12.6, -0.06), (x + 1.8, 19.4, 0.0), 'haz:', 'hazard chevron', faces=('north',))
    for x in (-11.0, -1.0, 9.0, 17.0, 27.0):
        m.bolt(x, 12.6, 0.0, 'north', 0.4)
        m.bolt(x, 19.4, 0.0, 'north', 0.4)
    m.box((2.0, 20.0, 1.0), (14.0, 21.0, 3.0), RED, 'beam top cap')
    m.g('hydraulic buffers')
    for x in (-6.0, 22.0):                                       # UIC buffer centres 1.75 m apart
        m.cyl((x, 16.0, 4.0), 2.6, 7.0, 'z', 'paint:#2b2f33', 'buffer cylinder', cap='paint:#22262a')
        m.cyl((x, 16.0, -3.0), 1.4, 3.0, 'z', 'metal:steel', 'ram (plunger)')
        m.cyl((x, 16.0, -4.0), 3.4, 1.0, 'z', 'metal:steel', 'buffer head', cap='paint:#d8d8d8')
        m.box((x - 0.4, 18.8, 5.0), (x + 0.4, 19.4, 10.5), 'cable:202020', 'hydraulic hose')
        m.box((x - 0.3, 13.0, 9.0), (x + 0.3, 13.6, 9.6), 'metal:brass', 'grease nipple')
        for dx in (-2.6, 2.6):
            m.bolt(x + dx, 16.0, 4.0, 'north', 0.45)
        m.box((x - 3.0, 12.6, 10.6), (x + 3.0, 19.4, 11.6), RED, 'buffer mounting plate')
    m.g('stop lamp + board')
    m.box((7.0, 20.0, 1.5), (9.0, 30.0, 3.5), 'galv', 'lamp post')
    m.box((4.0, 25.6, 0.6), (12.0, 31.0, 1.6), 'paint:#f2f2f2', 'STOP board')
    m.box((4.3, 25.9, 0.5), (11.7, 30.7, 0.6), 'label:STOP', 'STOP lettering', faces=('north',))
    m.box((6.6, 21.0, 0.0), (9.4, 24.2, 1.6), DARK, 'lamp housing')
    m.box((6.4, 24.2, -0.6), (9.6, 24.6, 1.6), DARK, 'lamp hood')
    for x in (-10.0, 26.0):
        m.box((x - 1.2, 20.0, 1.0), (x + 1.2, 22.4, 1.6), 'paint:#f0e020', 'reflector')
    m.g('radar + panel')
    m.box((1.0, 0.6, 0.0), (15.0, 12.0, 8.0), SIGNAL_GREY, 'instrument housing')
    m.box((1.2, 0.8, -0.1), (14.8, 11.8, 0.0), LIGHT, 'panel plate', faces=('north',))
    m.box((0.8, 11.6, -0.4), (15.2, 12.0, 8.2), SIGNAL_GREY, 'housing rain lip')
    m.box((5.4, 21.0, -0.8), (6.4, 23.0, 1.0), DARK, 'Doppler radar head')
    m.box((5.5, 21.2, -0.9), (6.3, 22.8, -0.8), 'glass', 'radar window', faces=('north',))
    m.box((1.4, 11.2, -0.14), (14.6, 11.7, -0.1), 'label:BUFFER STOP SUPERVISION', 'legend', faces=('north',))
    m.terminal_12v(15.0, 2.0, 5.0, 'east')
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 0.6, 5.0, 'down', r=0.3, length=0.5)
    m.box((15.0, 6.0, 1.0), (15.4, 10.0, 7.0), 'plate:IR EXTRAS BS-1', 'maker plate', faces=('east',))
    f = m.surface('north', face=-0.12, title='BUFFER STOP')
    m.ctl(f, 'digits', 'id', 3.0, 10.6, w=3.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'speed', 10.2, 10.6, w=7.0, h=0.7, col='7cf29a', label='', fmt='%s')
    for i, (cid, col, lab, bl) in enumerate((('app', 'ffd020', 'APPR', False), ('ovs', 'ff3030', 'OVSPD', True), ('int', 'ff2020', 'E-BRK', True),
                                             ('hit', 'ffb020', 'HIT', False), ('run', 'ff2020', 'OVRUN', True), ('inh', 'ffb020', 'INHIB', False),
                                             ('rec', '9ad8ff', 'RECOV', False), ('ok', '30e060', 'READY', False), ('pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.0 + i * 1.5, 9.4, col, lab, 0.6, bl)
    m.ctl(f, 'digits', 'allow', 4.6, 8.1, w=6.0, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'mass', 11.6, 8.1, w=5.0, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'cap', 4.6, 7.0, w=6.0, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'stroke', 11.6, 7.0, w=5.0, h=0.7, col='ff8040', label='', fmt='%s')
    m.ctl(f, 'digits', 'last', 6.0, 5.9, w=9.0, h=0.7, col='f2f0e6', label='', fmt='%s')
    m.ctl(f, 'digits', 'hits', 13.2, 5.9, w=2.8, h=0.7, col='ffb020', label='', fmt='%d HIT')
    m.ctl(f, 'gauge', 'g_kmh', 3.2, 3.4, w=2.4, h=2.4, col='f2f0e6', label='KM/H', max=60)
    m.ctl(f, 'gauge', 'g_cap', 6.2, 3.4, w=2.4, h=2.4, col='f2f0e6', label='CAP %', max=100)
    m.ctl(f, 'selector', 's_type', 9.4, 3.8, w=1.1, h=1.1, col='202020', label='TYPE', pos=['FIXED', 'HYD', 'FRICT'])
    m.ctl(f, 'selector', 's_mode', 11.8, 3.8, w=1.1, h=1.1, col='202020', label='MODE', pos=['SUPV', 'WARN', 'OFF'])
    m.ctl(f, 'key', 'k_inh', 9.4, 1.6, w=1.0, h=1.0, col='c0a040', label='INHIBIT', pos=['OFF', 'ON'])
    m.ctl(f, 'push', 'p_reset', 11.8, 1.6, w=1.0, h=1.0, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'test', 14.0, 1.6, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    s = m.surface('north', face=-0.02, title='')
    m.ctl(s, 'lamp', 'lamp', 8.0, 22.6, w=2.2, h=2.2, col='ff2020', label='')
    m.save(item_gui=0.4)
    facing_states('buffer_stop')
    return m


# ------------------------------------------------------------------------------------------------ 7 single-line controller
def single_line_control():
    m = Model('single_line_control')
    m.g('plinth')
    m.box((0.4, 0, 8.4), (15.6, 0.8, 16.0), 'concrete', 'cabinet plinth')
    for x in (1.0, 15.0):
        m.bolt(x, 0.8, 15.4, 'up', 0.4)
    m.g('cabinet')
    m.box((1.0, 0.8, 9.0), (15.0, 15.0, 16.0), SIGNAL_GREY, 'instrument cabinet')
    m.box((0.6, 15.0, 8.4), (15.4, 15.8, 16.2), SIGNAL_GREY, 'cabinet roof')
    m.box((0.8, 15.8, 8.6), (15.2, 16.0, 16.0), 'paint:#8a8e8a', 'roof capping')
    m.box((1.2, 1.0, 8.95), (14.8, 14.8, 9.0), LIGHT, 'instrument face', faces=('north',))
    for x in (1.3, 14.7):
        for y in (1.2, 14.6):
            m.bolt(x, y, 8.95, 'north', 0.25)
    for i in range(4):                                           # vent louvres on the sides
        y = 3.0 + i * 2.6
        m.box((0.96, y, 11.0), (1.0, y + 0.5, 14.5), DARK, 'louvre', faces=('west',))
        m.box((15.0, y, 11.0), (15.04, y + 0.5, 14.5), DARK, 'louvre', faces=('east',))
    m.box((1.4, 14.0, 8.9), (14.6, 14.6, 8.95), 'label:SINGLE LINE SECTION', 'legend', faces=('north',))
    m.g('direction instrument')                                  # a block-instrument style head on the roof
    m.box((4.0, 16.0, 9.6), (12.0, 22.0, 14.6), 'wood:walnut', 'instrument case')
    m.box((4.3, 16.3, 9.5), (11.7, 21.7, 9.6), 'paint:#f2efe2', 'dial face', faces=('north',))
    m.box((3.6, 22.0, 9.2), (12.4, 22.6, 15.0), 'wood:walnut', 'case top')
    m.cyl((8.0, 22.6, 12.0), 1.6, 1.0, 'y', 'metal:brass', 'bell gong')
    m.box((7.7, 23.6, 11.7), (8.3, 24.2, 12.3), 'metal:brass', 'gong stud')
    m.box((4.4, 16.4, 9.4), (11.6, 16.9, 9.5), 'label:A>B   B>A', 'dial legend', faces=('north',))
    for x, y in ((4.6, 16.6), (11.4, 16.6), (4.6, 21.4), (11.4, 21.4)):
        m.bolt(x, y, 9.5, 'north', 0.3, 'metal:brass')
    m.box((6.4, 20.7, 9.45), (9.6, 21.3, 9.5), 'plate:TOKENLESS', 'instrument plate', faces=('north',))
    for y in (2.4, 7.0, 11.6):                                   # door hinges (the instrument face is the door)
        m.box((0.6, y, 8.8), (1.1, y + 1.2, 9.4), STEEL, 'door hinge')
    m.box((14.6, 6.4, 8.7), (15.0, 8.4, 9.0), STEEL, 'door handle')
    m.g('signal post telephone')
    m.box((-4.0, 4.0, 10.0), (0.6, 12.0, 14.0), 'paint:#f0d020', 'telephone cabinet')
    m.box((-4.1, 4.4, 10.4), (-4.0, 11.6, 13.6), 'paint:#e0c018', 'telephone door', faces=('west',))
    m.box((-4.3, 9.6, 11.0), (-4.1, 10.6, 13.0), 'label:SPT', 'SPT plate', faces=('west',))
    m.box((-4.4, 6.0, 11.4), (-4.1, 8.4, 12.6), DARK, 'handset')
    m.box((-1.0, 12.0, 9.6), (0.8, 12.6, 14.4), 'paint:#d8c018', 'telephone hood')
    m.g('battery + cable route')
    m.box((2.0, 0.8, 16.4), (14.0, 4.4, 22.0), 'paint:#3a4a3a', 'battery box')
    m.box((1.8, 4.4, 16.2), (14.2, 4.8, 22.2), 'paint:#2f3d2f', 'battery lid')
    for x in (4.0, 12.0):
        m.box((x - 0.5, 4.8, 18.6), (x + 0.5, 5.2, 19.6), 'metal:brass', 'lid catch')
    m.box((2.2, 2.4, 22.0), (6.0, 3.6, 22.1), 'label:24V BATT', 'battery label', faces=('south',))
    for z in (22.0, 25.0, 28.0, 31.0):
        m.box((7.0, 0, z), (9.0, 1.0, z + 0.9), 'concrete', 'cable trough')
        m.box((7.1, 1.0, z), (8.9, 1.2, z + 0.9), 'concrete', 'trough lid')
    m.g('services')
    m.terminal_12v(15.0, 2.0, 12.5, 'east')
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 0.8, 12.5, 'down', r=0.3, length=0.7)
    m.box((15.0, 9.0, 10.0), (15.3, 13.0, 15.0), 'plate:IR EXTRAS SLC-2', 'maker plate', faces=('east',))
    m.box((2.0, 4.0, 16.0), (14.0, 12.0, 16.4), SIGNAL_GREY, 'rear door', faces=('south', 'up', 'down', 'east', 'west'))
    m.box((12.6, 7.0, 16.4), (13.0, 9.0, 16.8), STEEL, 'rear door handle')
    f = m.surface('north', face=8.88, title='SINGLE LINE')
    # top: what the section is doing
    m.ctl(f, 'digits', 'id', 3.0, 13.0, w=3.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'dir', 8.6, 13.0, w=5.4, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'len', 13.2, 13.0, w=3.0, h=0.7, col='9ad8ff', label='', fmt='%s')
    # the section mimic: end A - 5 track sections - end B, direction arrows over it
    m.box((1.8, 10.9, 8.86), (14.2, 11.1, 8.9), 'paint:#202020', 'mimic track line', faces=('north',))
    lamp(m, f, 'end_a', 2.0, 11.0, '30e060', 'A', 0.55)
    lamp(m, f, 'end_b', 14.0, 11.0, '30e060', 'B', 0.55)
    for i in range(5):
        lamp(m, f, 't%d' % (i + 1), 4.0 + i * 2.0, 11.0, 'ff3030', '', 0.6)
    lamp(m, f, 'ab', 5.0, 12.1, '30e060', 'A>B', 0.6)
    lamp(m, f, 'ba', 11.0, 12.1, '30e060', 'B>A', 0.6)
    lamp(m, f, 'enta', 2.0, 9.9, '30e060', 'ENT A', 0.55)
    lamp(m, f, 'reqa', 3.6, 9.9, 'ffd020', 'REQ A', 0.55, True)
    lamp(m, f, 'occ', 8.0, 9.9, 'ff3030', 'OCC', 0.6)
    lamp(m, f, 'reqb', 12.4, 9.9, 'ffd020', 'REQ B', 0.55, True)
    lamp(m, f, 'entb', 14.0, 9.9, '30e060', 'ENT B', 0.55)
    m.ctl(f, 'digits', 'qa', 3.0, 8.7, w=2.8, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'trains', 8.0, 8.7, w=3.0, h=0.7, col='ff6040', label='', fmt='%d IN')
    m.ctl(f, 'digits', 'qb', 13.0, 8.7, w=2.8, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'ends', 6.0, 7.6, w=9.0, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'grant', 13.0, 7.6, w=2.8, h=0.7, col='7cf29a', label='', fmt='%s')
    # links + state lamps
    for i, (cid, col, lab, bl) in enumerate((('sig_a', '30e060', 'SIG A', False), ('sig_b', '30e060', 'SIG B', False), ('link', '30e060', 'SECT', False),
                                             ('clsd', 'ff3030', 'CLOSED', False), ('cfl', 'ff2020', 'CONFL', True), ('pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.4 + i * 2.25, 6.3, col, lab, 0.55, bl)
    m.ctl(f, 'gauge', 'g_dir', 3.2, 3.6, w=2.6, h=2.6, col='f2f0e6', label='DIRECTION', max=2)
    m.ctl(f, 'selector', 's_mode', 6.6, 4.4, w=1.1, h=1.1, col='202020', label='MODE', pos=['AUTO', 'A>B', 'B>A', 'CLOSED'])
    m.ctl(f, 'selector', 's_follow', 9.0, 4.4, w=1.1, h=1.1, col='202020', label='WORKING', pos=['ONE', 'FOLLOW'])
    m.ctl(f, 'selector', 's_kind', 11.4, 4.4, w=1.1, h=1.1, col='202020', label='TRACK', pos=['SINGLE', 'GAUNTLET'])
    m.ctl(f, 'push', 'p_cancel', 6.6, 1.8, w=1.0, h=1.0, col='d8b020', label='CANCEL')
    m.ctl(f, 'push', 'p_reset', 9.0, 1.8, w=1.0, h=1.0, col='2050d0', label='RESET')
    m.ctl(f, 'push', 'test', 13.8, 1.8, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    d = m.surface('north', face=9.48, title='')
    m.ctl(d, 'gauge', 'g_dir', 8.0, 19.0, w=5.0, h=5.0, col='f2efe2', label='', max=2)
    m.save(item_gui=0.55)
    facing_states('single_line_control')
    return m


# ------------------------------------------------------------------------------------------------ 25 yard retarder
def yard_retarder():
    m = Model('yard_retarder')
    m.g('beams')                                                 # brake beams along the near rail, in the track's cell
    m.box((-16.0, 1.4, -5.0), (32.0, 1.8, -3.6), STEEL, 'rail foot')
    m.box((-16.0, 1.8, -4.5), (32.0, 3.0, -4.1), STEEL, 'rail web')
    m.box((-16.0, 3.0, -4.75), (32.0, 3.6, -3.85), STEEL, 'rail head')
    for side, (z0, z1) in (('outer', (-6.6, -5.2)), ('inner', (-3.4, -2.0))):
        m.box((-15.0, 2.4, z0), (31.0, 4.4, z1), 'paint:#3a3f44', 'brake beam (' + side + ')')
        for i in range(12):                                      # replaceable shoe segments
            x = -14.6 + i * 3.8
            m.box((x, 3.0, z0 + (0.9 if side == 'outer' else -0.2)), (x + 3.2, 3.8, z0 + (1.4 if side == 'outer' else 0.3)), 'metal:steel', 'brake shoe')
    for i in range(7):                                           # bearers + levers + cylinders
        x = -13.0 + i * 7.0
        m.box((x - 0.8, 0, -8.0), (x + 0.8, 1.4, 0.0), 'concrete', 'bearer')
        m.box((x - 0.4, 1.4, -6.8), (x + 0.4, 2.4, -1.8), STEEL, 'clasp lever')
        m.cyl((x, 1.0, -1.8), 0.7, 3.4, 'z', 'paint:#2b3a44', 'air cylinder', cap='paint:#22303a')
        m.box((x - 0.2, 1.6, 1.4), (x + 0.2, 2.0, 2.2), 'cable:202020', 'air hose')
    m.box((-16.0, 0.2, 0.6), (32.0, 0.8, 1.4), 'galv', 'air main')
    m.g('cabinet + compressor')
    m.box((0.4, 0, 2.0), (15.6, 0.6, 16.0), 'concrete', 'plinth')
    m.box((1.0, 0.6, 2.0), (15.0, 12.0, 8.0), SIGNAL_GREY, 'control cabinet')
    m.box((1.2, 0.8, 1.9), (14.8, 11.8, 2.0), LIGHT, 'panel plate', faces=('north',))
    m.box((0.6, 12.0, 1.6), (15.4, 12.6, 8.4), SIGNAL_GREY, 'cabinet hood')
    m.box((1.4, 11.2, 1.86), (14.6, 11.7, 1.9), 'label:YARD RETARDER', 'legend', faces=('north',))
    m.cyl((1.5, 4.0, 12.0), 2.6, 13.0, 'x', 'paint:#c8c8c0', 'air receiver', cap='paint:#b0b0a8')
    for x in (3.0, 12.0):
        m.box((x - 0.6, 0.6, 10.4), (x + 0.6, 1.6, 13.6), 'paint:#5a5f64', 'receiver saddle')
    m.box((6.0, 6.8, 11.6), (6.6, 8.4, 12.4), 'metal:brass', 'safety valve')
    m.cyl((9.0, 6.6, 12.0), 0.6, 0.4, 'y', 'paint:#f2f0e6', 'receiver gauge', cap='gauge')
    m.box((2.0, 7.0, 9.0), (8.0, 11.0, 15.4), 'paint:#2f5a8a', 'compressor')
    m.cyl((8.0, 9.0, 12.2), 1.7, 5.0, 'x', 'paint:#2f4a6a', 'compressor motor', cap='paint:#263f5a')
    for i in range(5):
        m.box((8.6 + i * 0.9, 7.2, 10.4), (8.9 + i * 0.9, 10.8, 14.0), 'paint:#263f5a', 'motor fin')
    m.box((2.4, 11.0, 10.0), (7.6, 11.6, 14.6), 'louvre', 'compressor intake')
    m.terminal_12v(1.0, 2.0, 5.0, 'west')
    m.box((15.0, 4.0, 9.0), (17.0, 9.0, 13.0), 'ral7035', '400 V compressor isolator')
    m.box((17.0, 5.6, 10.4), (17.4, 7.4, 11.6), 'paint:#c81e1e', 'isolator handle')
    m.box((17.0, 7.6, 9.2), (17.05, 8.8, 12.8), 'danger:DANGER|400V', 'isolator danger plate', faces=('east',))
    m.box((15.0, 9.6, 3.0), (15.3, 11.0, 7.0), 'plate:IR EXTRAS RT-3', 'maker plate', faces=('east',))
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 0.6, 5.0, 'down', r=0.3, length=0.5)
    m.box((14.0, 12.6, 3.0), (14.6, 15.0, 3.6), 'galv', 'radar post')
    m.box((13.4, 15.0, 1.8), (15.2, 16.4, 3.8), DARK, 'speed radar head')
    m.box((13.5, 15.2, 1.7), (15.1, 16.2, 1.8), 'glass', 'radar window', faces=('north',))
    f = m.surface('north', face=1.88, title='RETARDER')
    m.ctl(f, 'digits', 'id', 3.0, 10.6, w=3.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'rail', 7.8, 10.6, w=4.6, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'bar', 12.6, 10.6, w=4.0, h=0.7, col='7cf29a', label='', fmt='%s')
    for i, (cid, col, lab, bl) in enumerate((('car', 'ffd020', 'CAR', False), ('brk', 'ff8020', 'BRAKE', False), ('air', '30e060', 'AIR', False),
                                             ('lowair', 'ff2020', 'LO AIR', True), ('comp', '9ad8ff', 'COMP', False), ('shoe', 'ffb020', 'SHOES', False),
                                             ('fast', 'ff3030', 'FAST', True), ('closed', 'ff3030', 'CLOSED', False), ('open', '30e060', 'OPEN', False),
                                             ('sup', '30e060', '400V', False), ('pwr', '30e060', '12V', False))):
        lamp(m, f, cid, 2.0 + i * 1.25, 9.4, col, lab, 0.52, bl)
    m.ctl(f, 'digits', 'in', 4.4, 8.1, w=5.6, h=0.7, col='f2f0e6', label='', fmt='%s')
    m.ctl(f, 'digits', 'out', 11.4, 8.1, w=5.6, h=0.7, col='f2f0e6', label='', fmt='%s')
    m.ctl(f, 'digits', 'tgt', 4.4, 7.0, w=5.6, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'wim', 11.4, 7.0, w=5.6, h=0.7, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'free', 4.4, 5.9, w=5.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'shoes', 11.4, 5.9, w=5.6, h=0.7, col='ff8040', label='', fmt='%s')
    m.ctl(f, 'gauge', 'g_bar', 2.6, 3.6, w=2.2, h=2.2, col='f2f0e6', label='AIR BAR', max=100)
    m.ctl(f, 'gauge', 'g_beam', 5.2, 3.6, w=2.2, h=2.2, col='f2f0e6', label='BEAM %', max=100)
    m.ctl(f, 'selector', 's_mode', 8.0, 4.0, w=1.05, h=1.05, col='202020', label='MODE', pos=['AUTO', 'MAN', 'OPEN', 'CLOSE'])
    m.ctl(f, 'selector', 's_target', 10.4, 4.0, w=1.05, h=1.05, col='202020', label='TARGET', pos=['4', '6', '8', '10', '12', '15', 'CALC'])
    m.ctl(f, 'selector', 's_type', 12.8, 4.0, w=1.05, h=1.05, col='202020', label='TYPE', pos=['BEAM', 'CLASP'])
    m.ctl(f, 'selector', 's_step', 2.6, 1.4, w=1.05, h=1.05, col='202020', label='STEP', pos=['OFF', '1', '2', '3', '4'])
    m.ctl(f, 'push', 'p_ack', 5.0, 1.4, w=0.9, h=0.9, col='d8b020', label='ACK')
    m.ctl(f, 'push', 'p_shoes', 7.1, 1.4, w=0.9, h=0.9, col='606060', label='SHOES')
    m.ctl(f, 'push', 'p_drain', 9.2, 1.4, w=0.9, h=0.9, col='2050d0', label='DRAIN')
    m.ctl(f, 'digits', 'cars', 12.0, 1.4, w=3.4, h=0.7, col='ffb020', label='', fmt='%d CARS')
    m.ctl(f, 'push', 'test', 14.4, 2.6, w=0.45, h=0.45, col='f0f0f0', label='TEST')
    m.save(item_gui=0.4)
    facing_states('yard_retarder')
    return m


def recipes():
    rdir = os.path.join(ASSETS, 'recipes')

    def r(name, pattern, key):
        k = {c: ({'item': it[0], 'data': it[1]} if isinstance(it, tuple) else {'item': it}) for c, it in key.items()}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': k, 'result': {'item': 'irextras:' + name, 'count': 1}},
                  open(os.path.join(rdir, name + '.json'), 'w'), indent=1)

    I = 'minecraft:iron_ingot'
    r('buffer_stop', ['RLR', 'IPI', 'III'], {'R': ('minecraft:wool', 14), 'L': 'minecraft:redstone_lamp', 'I': I, 'P': 'minecraft:piston'})
    r('single_line_control', ['GBG', 'RCR', 'III'], {'G': 'minecraft:glass_pane', 'B': 'minecraft:noteblock', 'R': 'minecraft:redstone', 'C': 'minecraft:comparator', 'I': I})
    r('yard_retarder', ['III', 'PCP', 'IRI'], {'I': I, 'P': 'minecraft:sticky_piston', 'C': 'minecraft:comparator', 'R': 'minecraft:redstone'})


if __name__ == '__main__':
    for fn in (buffer_stop, single_line_control, yard_retarder):
        mm = fn()
        print(mm.name, len(mm.boxes), 'boxes', sum(len(s['controls']) for s in mm.surfaces), 'controls')
    recipes()
    print('batch-3 track assets written')
