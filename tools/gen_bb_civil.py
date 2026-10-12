#!/usr/bin/env python3
"""Moving railway structures (her ChatGPT batch 3): #3 turntable / traverser operator's panel and #8 movable bridge
control desk. Front = north. Every lamp, button, key, selector, lever, dial and display is a live control (m.ctl) that
com.dogpound.railmap.track.TileTurntableControl / TileMovableBridge read or work. 12 V terminal boxes are wire
terminals; the grey isolator with the DANGER plate is where the 400 V motor supply lands (a touching Power Cable).
Run: python3 tools/gen_bb_civil.py   (writes models, textures, controls, blockstates, item models, recipes)"""
import json, math, os
from bbkit import Model, ASSETS
from gen_bb_track import facing_states, lamp

BODY = 'paint:#5b6b5e'        # operator's desk green-grey
BODY_D = 'paint:#46544a'
PANEL = 'paint:#d4d6d1'
MIMIC = 'paint:#2b3a44'
TRIM = 'paint:#2a2d30'


def desk_shell(m, name):
    """the common operator's desk: plinth, lower panel (z=4), writing shelf, upright mimic panel (z=10), canopy,
    side cheeks, kick plate, cable entry, 12 V terminal, 400 V isolator with DANGER plate, cable tray"""
    m.g('plinth')
    m.box((-0.5, 0, 3.0), (16.5, 0.6, 16.5), 'concrete', 'concrete plinth')
    for x in (0.4, 15.6):
        for z in (3.6, 15.8):
            m.bolt(x, 0.6, z, 'up', 0.45)
    m.g('lower desk')
    m.box((0.2, 0.6, 4.0), (15.8, 9.4, 10.0), BODY, 'desk body')
    m.box((0.6, 1.0, 3.9), (15.4, 9.0, 4.0), PANEL, 'lower control plate', faces=('north',))
    m.box((0.2, 0.6, 3.8), (15.8, 1.0, 4.0), TRIM, 'kick plate')
    for x in (0.9, 15.1):
        for y in (1.4, 8.6):
            m.bolt(x, y, 3.9, 'north', 0.28)
    m.box((-0.2, 9.4, 2.4), (16.2, 10.0, 10.2), 'wood:oak', 'writing shelf')
    m.box((-0.2, 9.2, 2.4), (16.2, 9.4, 2.7), TRIM, 'shelf nosing')
    m.box((1.0, 10.0, 3.2), (4.2, 10.15, 7.0), 'paint:#f2efe2', 'train register (open)')
    m.box((2.5, 10.0, 3.3), (2.7, 10.2, 6.9), 'paint:#8a6a3a', 'register spine')
    m.box((12.4, 10.0, 3.6), (14.6, 10.9, 5.2), 'paint:#1a1a1a', 'desk phone')
    m.box((12.6, 10.9, 3.7), (14.4, 11.3, 4.4), 'paint:#1a1a1a', 'handset')
    m.g('mimic panel')
    m.box((0.2, 10.0, 10.0), (15.8, 22.0, 14.0), BODY, 'upright cabinet')
    m.box((0.5, 10.4, 9.9), (15.5, 21.6, 10.0), MIMIC, 'mimic board', faces=('north',))
    for x in (0.8, 15.2):
        for y in (10.8, 21.2):
            m.bolt(x, y, 9.9, 'north', 0.26)
    m.box((-0.2, 22.0, 8.6), (16.2, 22.6, 14.4), BODY_D, 'cabinet hood')
    m.box((0.4, 21.0, 8.8), (15.6, 22.0, 9.2), 'paint:#fff4c0', 'panel light diffuser')
    m.g('side cheeks')
    for x0, x1 in ((-0.2, 0.2), (15.8, 16.2)):
        m.box((x0, 0.6, 3.8), (x1, 10.0, 10.2), BODY_D, 'desk cheek')
        m.box((x0, 10.0, 9.8), (x1, 22.0, 14.2), BODY_D, 'cabinet cheek')
        for i in range(5):
            m.box((x0 - 0.02, 12.0 + i * 1.6, 11.0), (x1 + 0.02, 12.4 + i * 1.6, 13.4), TRIM, 'vent louvre')
    m.g('back + services')
    m.box((0.2, 0.6, 14.0), (15.8, 22.0, 16.0), BODY_D, 'rear cabinet')
    m.box((1.0, 2.0, 15.95), (15.0, 20.0, 16.05), BODY, 'rear door', faces=('south',))
    m.box((13.6, 9.0, 16.05), (14.0, 12.0, 16.35), 'metal:steel', 'rear door handle')
    m.terminal_12v(16.2, 2.0, 7.0, 'east')
    m.box((16.2, 4.0, 11.0), (18.2, 9.0, 14.0), 'ral7035', '400 V motor isolator')
    m.box((18.2, 5.6, 12.0), (18.6, 7.4, 13.0), 'paint:#c81e1e', 'isolator handle')
    m.box((18.2, 7.6, 11.2), (18.25, 8.8, 13.8), 'danger:DANGER|400V', 'isolator danger plate', faces=('east',))
    m.cyl((17.2, 0.6, 12.5), 0.55, 3.4, 'y', 'cable:1d1d21', '400 V armoured cable')
    m.cyl((17.2, 3.6, 12.5), 0.75, 0.4, 'y', 'metal:brass', 'cable gland')
    for x in (3.0, 5.0, 7.0, 9.0, 11.0, 13.0):
        m.gland(x, 0.6, 12.0, 'down', r=0.3, length=0.6)
    m.box((-0.5, -0.6, 10.0), (16.5, 0.0, 15.0), 'galv', 'cable trough')
    for i, col in enumerate(('cable:c81e1e', 'cable:1d1d21', 'cable:1d58c8', 'cable:e0c020', 'cable:707070')):
        m.cyl((-0.5, -0.3, 10.6 + i * 0.9), 0.22, 17.0, 'x', col, 'trough cable')
    m.nameplate(3.0, 20.6, 13.0, 21.4, 9.9, name)
    m.g("operator's stool")
    m.cyl((8.0, 0.6, -2.0), 0.35, 4.4, 'y', 'metal:steel', 'stool column')
    for k in range(5):
        a = k * 2 * math.pi / 5
        m.box((8.0 + 1.6 * math.cos(a) - 0.25, 0.6, -2.0 + 1.6 * math.sin(a) - 0.25), (8.0 + 1.6 * math.cos(a) + 0.25, 0.9, -2.0 + 1.6 * math.sin(a) + 0.25), TRIM, 'castor foot')
        m.box((8.0 + 0.8 * math.cos(a) - 0.18, 0.75, -2.0 + 0.8 * math.sin(a) - 0.18), (8.0 + 0.8 * math.cos(a) + 0.18, 0.95, -2.0 + 0.8 * math.sin(a) + 0.18), TRIM, 'stool leg')
    m.box((6.6, 5.0, -3.4), (9.4, 5.6, -0.6), 'paint:#2a3f5a', 'stool seat')
    m.box((6.8, 5.6, -3.6), (9.2, 8.6, -3.2), 'paint:#2a3f5a', 'stool back')
    m.g('clock')
    m.cyl((8.0, 22.6, 11.0), 1.3, 0.3, 'z', 'paint:#f2efe2', 'clock face', cap='paint:#f2efe2')
    for k in range(12):
        a = k * math.pi / 6
        m.box((8.0 + 1.05 * math.cos(a) - 0.06, 23.9 + 1.05 * math.sin(a) - 0.06, 10.92), (8.0 + 1.05 * math.cos(a) + 0.06, 23.9 + 1.05 * math.sin(a) + 0.06, 11.0), TRIM, 'hour tick', faces=('north',))
    m.box((7.95, 23.9, 10.9), (8.05, 24.8, 10.95), TRIM, 'minute hand', faces=('north',))
    m.box((7.95, 23.4, 10.9), (8.05, 23.95, 10.95), TRIM, 'hour hand', faces=('north',))
    m.g('relay rack (seen through the side window)')
    m.box((-0.25, 2.0, 10.6), (-0.15, 8.6, 13.8), 'glass', 'side window', faces=('west',))
    for row in range(3):
        m.box((-0.1, 2.4 + row * 2.1, 10.8), (0.1, 2.6 + row * 2.1, 13.6), 'metal:steel', 'relay shelf')
        for k in range(6):
            m.box((-0.05, 2.6 + row * 2.1, 10.9 + k * 0.45), (0.15, 3.9 + row * 2.1, 11.25 + k * 0.45), 'paint:#b02020' if (row + k) % 3 == 0 else 'paint:#2a2d30', 'plug-in relay')
    m.box((0.8, 1.2, 3.86), (3.2, 2.6, 3.9), 'warn:', 'warning label', faces=('north',))


def turntable_control():
    m = Model('turntable_control')
    desk_shell(m, 'TURNTABLE CONTROL')
    m.g('mimic: pit + roads')
    cx, cy, z = 8.0, 16.2, 9.86
    for i in range(36):                                          # the pit ring
        a = i * 2 * math.pi / 36
        x, y = cx + 3.4 * math.cos(a), cy + 3.4 * math.sin(a)
        m.box((x - 0.22, y - 0.22, z), (x + 0.22, y + 0.22, 9.9), 'paint:#c0c4c8', 'pit ring', faces=('north',))
    m.box((cx - 3.0, cy - 0.15, z - 0.02), (cx + 3.0, cy + 0.15, 9.9), 'paint:#f0d020', 'bridge (mimic)', faces=('north',))
    m.box((cx - 0.35, cy - 0.35, z - 0.04), (cx + 0.35, cy + 0.35, 9.9), 'paint:#f0f0f0', 'king pin', faces=('north',))
    for k in range(8):                                           # 8 exit roads radiating out
        a = k * math.pi / 4 + math.pi / 8
        for d in (3.8, 4.15, 4.5, 4.85, 5.2):
            x, y = cx + d * math.cos(a), cy + d * math.sin(a) * 0.95
            m.box((x - 0.12, y - 0.12, z), (x + 0.12, y + 0.12, 9.9), 'paint:#9aa0a6', 'road', faces=('north',))
    m.box((1.0, 11.0, z), (5.4, 11.5, 9.9), 'label:ROADS', 'legend roads', faces=('north',))
    m.box((10.6, 11.0, z), (15.0, 11.5, 9.9), 'label:INTERLOCKS', 'legend interlocks', faces=('north',))
    m.g('lower panel details')
    for x in (2.2, 13.8):
        m.box((x - 1.2, 4.2, 3.86), (x + 1.2, 4.3, 3.9), TRIM, 'group line', faces=('north',))
    m.box((6.0, 8.4, 3.86), (10.0, 8.9, 3.9), 'label:ROAD SELECT', 'legend road select', faces=('north',))
    m.box((5.0, 1.1, 3.86), (11.0, 1.5, 3.9), 'label:MOTOR CONTROL', 'legend motor', faces=('north',))
    m.g('crank stowage')
    m.box((0.6, 3.0, 2.8), (1.4, 7.0, 3.8), 'metal:steel', 'crank bracket')
    m.cyl((1.0, 3.4, 2.6), 0.25, 3.2, 'y', 'paint:#f0c020', 'hand crank')
    m.box((0.4, 6.4, 2.0), (1.6, 6.8, 3.0), 'paint:#f0c020', 'crank handle')
    # ---- live controls: mimic panel (z = 9.88)
    f = m.surface('north', face=9.88, title='TURNTABLE')
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, y = cx + 5.6 * math.cos(a), cy + 5.6 * math.sin(a) * 0.95
        m.ctl(f, 'lamp', 'r%d' % (k + 1), 16 - x, y, w=0.6, h=0.6, col='30e060', label='%d' % (k + 1))
        x2, y2 = cx + 6.6 * math.cos(a), cy + 6.6 * math.sin(a) * 0.95
        m.ctl(f, 'lamp', 't%d' % (k + 1), 16 - x2, y2, w=0.3, h=0.3, col='ffffff', label='')
    for i, (cid, col, lab, bl) in enumerate((('aln', '30e060', 'ALIGNED', False), ('pin_in', '30e060', 'PINS IN', False), ('pin_out', 'ffb020', 'PINS OUT', False),
                                             ('run', 'ffb020', 'MOVING', True), ('stock', '9ad8ff', 'STOCK ON', False), ('unbal', 'ff8020', 'UNBAL', False))):
        lamp(m, f, cid, 13.6 + (i % 2) * 1.5, 20.0 - (i // 2) * 1.3, col, lab, 0.55, bl)
    for i, (cid, col, lab, bl) in enumerate((('foul', 'ff2020', 'FOULING', True), ('ovl', 'ff2020', 'OVERLOAD', True), ('appr', 'ffb020', 'APPROACH', False),
                                             ('sup', '30e060', '400V', False), ('pwr', '30e060', '12V', False), ('estop', 'ff2020', 'E-STOP', True))):
        lamp(m, f, cid, 1.0 + (i % 2) * 1.5, 20.0 - (i // 2) * 1.3, col, lab, 0.55, bl)
    m.ctl(f, 'digits', 'tbl', 3.8, 12.2, w=6.6, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'pos', 12.2, 12.2, w=6.0, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'road', 13.6, 15.4, w=4.0, h=0.6, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'state', 2.4, 15.4, w=4.2, h=0.6, col='ff6040', label='', fmt='%s')
    m.ctl(f, 'digits', 'pins', 2.4, 14.4, w=4.2, h=0.6, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'moves', 13.6, 14.4, w=4.0, h=0.6, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'supt', 8.0, 21.0, w=8.0, h=0.5, col='ffd020', label='', fmt='%s')
    m.ctl(f, 'push', 'test', 15.2, 21.1, w=0.4, h=0.4, col='f0f0f0', label='')
    # ---- live controls: lower panel (z = 3.88)
    g = m.surface('north', face=3.88, title='OPERATOR')
    for k in range(8):
        m.ctl(g, 'push', 'p_r%d' % (k + 1), 3.6 + k * 1.25, 7.6, w=0.9, h=0.9, col='f0f0f0', label='R%d' % (k + 1))
    m.ctl(g, 'digits', 'kw', 4.0, 6.2, w=5.0, h=0.6, col='ffd020', label='', fmt='%s')
    m.ctl(g, 'digits', 'load', 12.0, 6.2, w=5.0, h=0.6, col='9ad8ff', label='', fmt='%s')
    m.ctl(g, 'gauge', 'g_amps', 2.0, 4.6, w=2.2, h=2.2, col='f2f0e6', label='MOTOR A', max=200)
    m.ctl(g, 'gauge', 'g_load', 14.0, 4.6, w=2.2, h=2.2, col='f2f0e6', label='LOAD %', max=150)
    m.ctl(g, 'push', 'p_jogl', 4.6, 4.6, w=1.0, h=1.0, col='606060', label='JOG-')
    m.ctl(g, 'push', 'p_go', 6.4, 4.6, w=1.2, h=1.2, col='1fa34a', label='GO')
    m.ctl(g, 'push', 'p_stop', 8.2, 4.6, w=1.2, h=1.2, col='c02020', label='STOP')
    m.ctl(g, 'push', 'p_jogr', 10.0, 4.6, w=1.0, h=1.0, col='606060', label='JOG+')
    m.ctl(g, 'push', 'p_crank', 11.8, 4.6, w=1.0, h=1.0, col='d8b020', label='CRANK')
    m.ctl(g, 'selector', 's_mode', 3.0, 2.2, w=1.2, h=1.2, col='202020', label='MODE', pos=['REMOTE', 'LOCAL', 'HAND'])
    m.ctl(g, 'selector', 's_rate', 5.4, 2.2, w=1.2, h=1.2, col='202020', label='RATING T', pos=['120', '250', '400'])
    m.ctl(g, 'pull', 'k_pins', 8.6, 2.4, w=1.0, h=2.4, col='d02020', label='PINS', pos=['OUT', 'IN'])
    m.ctl(g, 'mushroom', 'm_estop', 13.4, 2.4, w=1.8, h=1.8, col='d01818', label='E-STOP')
    m.save(item_gui=0.4)
    facing_states('turntable_control')
    return m


def bridge_control():
    m = Model('bridge_control')
    desk_shell(m, 'BRIDGE CONTROL')
    m.g('mimic: waterway + span')
    z = 9.86
    m.box((0.8, 12.4, z), (15.2, 14.6, 9.9), 'paint:#2a64a8', 'waterway (mimic)', faces=('north',))
    for i in range(14):
        m.box((1.0 + i * 1.0, 12.9 + (i % 2) * 0.8, z - 0.01), (1.6 + i * 1.0, 13.05 + (i % 2) * 0.8, 9.9), 'paint:#7ab0e8', 'water ripple', faces=('north',))
    for x0, x1 in ((0.8, 5.2), (10.8, 15.2)):
        m.box((x0, 14.6, z), (x1, 15.2, 9.9), 'paint:#8a7a60', 'abutment', faces=('north',))
        m.box((x0, 15.2, z - 0.01), (x1, 15.4, 9.9), 'paint:#c0c4c8', 'approach rails', faces=('north',))
    for i in range(10):                                          # the leaf drawn raised, stepped
        m.box((5.2 + i * 0.15, 15.2 + i * 0.45, z - 0.02), (5.7 + i * 0.15, 16.0 + i * 0.45, 9.9), 'paint:#f0d020', 'leaf (mimic)', faces=('north',))
    m.box((4.6, 14.2, z - 0.02), (5.6, 15.2, 9.9), 'paint:#f0f0f0', 'trunnion (mimic)', faces=('north',))
    for i in range(9):                                           # counterweight pit + approach truss on the mimic
        m.box((10.8 + i * 0.5, 15.4, z - 0.02), (10.9 + i * 0.5, 16.2, 9.9), 'paint:#c0c4c8', 'truss post', faces=('north',))
        m.box((0.8 + i * 0.5, 15.4, z - 0.02), (0.9 + i * 0.5, 16.2, 9.9), 'paint:#c0c4c8', 'truss post', faces=('north',))
    m.box((0.8, 16.2, z - 0.02), (5.2, 16.35, 9.9), 'paint:#c0c4c8', 'truss chord', faces=('north',))
    m.box((10.8, 16.2, z - 0.02), (15.2, 16.35, 9.9), 'paint:#c0c4c8', 'truss chord', faces=('north',))
    m.box((3.8, 12.4, z - 0.02), (5.0, 14.0, 9.9), 'paint:#6a6a64', 'counterweight pit', faces=('north',))
    m.box((1.0, 11.0, z), (5.4, 11.5, 9.9), 'label:SPAN', 'legend span', faces=('north',))
    m.box((10.6, 11.0, z), (15.0, 11.5, 9.9), 'label:WATERWAY', 'legend waterway', faces=('north',))
    m.g('marine lights mast')
    m.box((15.0, 22.6, 11.0), (16.0, 27.0, 12.0), 'galv', 'mast')
    for y, col in ((25.6, 'paint:#3a0a0a'), (24.0, 'paint:#0a3a0a')):
        m.box((14.6, y, 10.2), (16.4, y + 1.4, 12.0), 'paint:#1a1a1a', 'lamp housing')
        m.box((14.4, y + 1.4, 10.0), (16.6, y + 1.6, 12.0), 'paint:#1a1a1a', 'hood')
    m.g('bridge horn')
    m.cyl((1.0, 22.6, 12.0), 0.6, 1.0, 'y', 'paint:#c0c4c8', 'horn base')
    m.cyl((1.0, 23.6, 12.0), 0.35, 1.6, 'z', 'metal:brass', 'horn trumpet')
    m.box((0.4, 23.1, 10.2), (1.6, 24.1, 10.4), 'metal:brass', 'horn bell')
    m.g('lever frame')
    m.box((7.0, 1.0, 2.6), (9.2, 1.6, 3.9), TRIM, 'lever quadrant')
    m.box((7.2, 1.6, 3.0), (9.0, 1.8, 3.7), 'metal:steel', 'quadrant plate')
    # ---- mimic panel controls (z = 9.88)
    f = m.surface('north', face=9.88, title='BRIDGE')
    lamp(m, f, 'closed', 16 - 7.0, 16.6, '30e060', 'CLOSED', 0.6)
    lamp(m, f, 'open', 16 - 7.0, 19.6, 'ffb020', 'OPEN', 0.6)
    lamp(m, f, 'lka', 16 - 4.6, 15.9, '30e060', 'LOCK A', 0.5)
    lamp(m, f, 'lkb', 16 - 10.8, 15.9, '30e060', 'LOCK B', 0.5)
    lamp(m, f, 'lkout', 16 - 8.0, 15.9, 'ffb020', 'LKS OUT', 0.5)
    lamp(m, f, 'onspan', 16 - 9.4, 16.9, 'ff2020', 'TRAIN', 0.5, True)
    lamp(m, f, 'wred', 16 - 12.2, 13.5, 'ff2020', 'STOP', 0.6)
    lamp(m, f, 'wgrn', 16 - 13.6, 13.5, '30e060', 'GO', 0.6)
    lamp(m, f, 'boat', 16 - 3.0, 13.5, '9ad8ff', 'VESSEL', 0.5, True)
    for i, (cid, col, lab, bl) in enumerate((('prot', 'ff2020', 'SIG HELD', False), ('appr', 'ffb020', 'APPROACH', True), ('run', 'ffb020', 'MOTOR', False),
                                             ('sup', '30e060', '400V', False), ('pwr', '30e060', '12V', False), ('estop', 'ff2020', 'E-STOP', True))):
        lamp(m, f, cid, 1.0 + (i % 2) * 1.5, 20.6 - (i // 2) * 1.2, col, lab, 0.5, bl)
    for i in range(4):
        lamp(m, f, 's%d' % (i + 1), 12.6 + (i % 2) * 1.6, 20.6 - (i // 2) * 1.2, 'ff2020', 'SIG%d' % (i + 1), 0.5)
    m.ctl(f, 'digits', 'phase', 8.0, 21.0, w=6.4, h=0.55, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'enc', 4.0, 12.1, w=6.4, h=0.55, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'boats', 12.0, 12.1, w=5.6, h=0.55, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'next', 12.0, 11.3, w=5.6, h=0.5, col='9ad8ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'span', 4.0, 11.3, w=6.4, h=0.5, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'gauge', 'g_pos', 8.0, 18.0, w=1.8, h=1.8, col='f2f0e6', label='POS', max=1000)
    m.ctl(f, 'push', 'test', 15.2, 21.4, w=0.4, h=0.4, col='f0f0f0', label='')
    # marine traffic lights on the mast (the north face of the housings)
    h = m.surface('north', face=10.18, title='MARINE')
    m.ctl(h, 'lamp', 'wred', 16 - 15.5, 26.3, w=1.2, h=1.2, col='ff2020', label='')
    m.ctl(h, 'lamp', 'wgrn', 16 - 15.5, 24.7, w=1.2, h=1.2, col='30e060', label='')
    # ---- lower panel controls (z = 3.88)
    g = m.surface('north', face=3.88, title='OPERATOR')
    m.ctl(g, 'digits', 'kw', 3.6, 7.8, w=4.6, h=0.55, col='ffd020', label='', fmt='%s')
    m.ctl(g, 'digits', 'opens', 12.4, 7.8, w=4.6, h=0.55, col='9ad8ff', label='', fmt='%s')
    m.ctl(g, 'digits', 'supt', 8.0, 8.6, w=7.0, h=0.5, col='ffd020', label='', fmt='%s')
    m.ctl(g, 'gauge', 'g_kw', 2.0, 5.6, w=2.2, h=2.2, col='f2f0e6', label='MOTOR KW', max=100)
    m.ctl(g, 'gauge', 'g_lock', 14.0, 5.6, w=2.2, h=2.2, col='f2f0e6', label='LOCKS %', max=100)
    for i, cid in enumerate(('p_s1', 'p_s2', 'p_s3', 'p_s4')):
        m.ctl(g, 'push', cid, 4.4 + i * 1.1, 6.4, w=0.7, h=0.7, col='c02020', label='S%d' % (i + 1))
    m.ctl(g, 'push', 'tbl', 9.4, 6.4, w=0.7, h=0.7, col='2050d0', label='TABLE')
    m.ctl(g, 'push', 'p_rail', 10.5, 6.4, w=0.7, h=0.7, col='2050d0', label='RAIL')
    m.ctl(g, 'push', 'heel', 11.6, 6.4, w=0.7, h=0.7, col='2050d0', label='HEEL')
    m.ctl(g, 'push', 'toe', 12.7, 6.4, w=0.7, h=0.7, col='2050d0', label='TOE')
    m.ctl(g, 'push', 'p_open', 4.4, 4.4, w=1.1, h=1.1, col='d8b020', label='OPEN')
    m.ctl(g, 'push', 'p_close', 6.0, 4.4, w=1.1, h=1.1, col='1fa34a', label='CLOSE')
    m.ctl(g, 'pull', 'k_cmd', 8.1, 2.6, w=1.0, h=2.4, col='d02020', label='SPAN', pos=['CLOSE', 'OPEN'])
    m.ctl(g, 'push', 'p_crank', 10.2, 4.4, w=1.0, h=1.0, col='606060', label='CRANK')
    m.ctl(g, 'push', 'p_horn', 11.7, 4.4, w=0.9, h=0.9, col='f0f0f0', label='HORN')
    m.ctl(g, 'selector', 's_type', 2.2, 2.2, w=1.1, h=1.1, col='202020', label='TYPE', pos=['SWING', 'BASCULE'])
    m.ctl(g, 'selector', 's_mode', 4.2, 2.2, w=1.1, h=1.1, col='202020', label='MODE', pos=['REMOTE', 'LOCAL', 'HAND'])
    m.ctl(g, 'selector', 's_sched', 6.2, 2.2, w=1.1, h=1.1, col='202020', label='SCHEDULE', pos=['DEMAND', '10 MIN', '20 MIN', 'RAIL PRIO'])
    m.ctl(g, 'selector', 's_win', 10.2, 2.2, w=1.1, h=1.1, col='202020', label='WINDOW S', pos=['60', '120'])
    m.ctl(g, 'selector', 's_span', 12.0, 2.2, w=1.1, h=1.1, col='202020', label='SPAN M', pos=['6', '9', '12', '16', '20', '24'])
    m.ctl(g, 'mushroom', 'm_estop', 14.2, 2.4, w=1.6, h=1.6, col='d01818', label='E-STOP')
    m.save(item_gui=0.4)
    facing_states('bridge_control')
    return m


def recipes():
    rdir = os.path.join(ASSETS, 'recipes')

    def r(name, pattern, key):
        k = {c: ({'item': it[0], 'data': it[1]} if isinstance(it, tuple) else {'item': it}) for c, it in key.items()}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': k, 'result': {'item': 'irextras:' + name, 'count': 1}},
                  open(os.path.join(rdir, name + '.json'), 'w'), indent=1)

    r('turntable_control', ['GCG', 'RPR', 'III'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:compass', 'R': 'minecraft:redstone', 'P': 'minecraft:piston', 'I': 'minecraft:iron_ingot'})
    r('bridge_control', ['GCG', 'RPR', 'IBI'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'R': 'minecraft:redstone', 'P': 'minecraft:sticky_piston', 'I': 'minecraft:iron_ingot', 'B': 'minecraft:boat'})


if __name__ == '__main__':
    for f in (turntable_control, bridge_control):
        mm = f()
        print(mm.name, len(mm.boxes), 'boxes', sum(len(s['controls']) for s in mm.surfaces), 'controls')
    recipes()
    print('civil (turntable + bridge) assets written')
