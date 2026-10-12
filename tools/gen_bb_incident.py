#!/usr/bin/env python3
"""Emergency response + control-room gear (her ChatGPT list #67-70, #74-80): Incident Command Desk, Breakdown Crew Depot,
Fire Alarm & Suppression Panel, Emergency Exit Sign, Assembly Point, Remote Maintenance Terminal, Announcement Control
Unit. Models face north; controls are placed by u (viewer-left -> right): x = 16 - u. Live controls: incident/Tile*.
Also writes blockstates, item models, recipes and lang lines, and prints the desk's shelf spots for TileIncidentDesk."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, led_board, desk, strip, buttons, hood, monitor, cupboard_shelf

X = lambda u: 16 - u
SPOTS = {}


def lamp_row(m, sf, v, items, u0, step, size=0.6):
    for i, (cid, col, lab, blink) in enumerate(items):
        u = u0 + i * step
        m.ctl(sf, 'lamp', cid, u, v, w=size, h=size, col=col, label=lab, **({'blink': True} if blink else {}))


# ======================================================================================================= incident desk
def incident_desk():
    m = Model('incident_desk')
    back_wall(m, panel='#26303a', plaster='#dfe2e4')
    m.g('incident board')
    led_board(m, -14, 30, 14.4, 28.6)
    m.box((-6, 29.0, 14.7), (22, 31.2, 15.4), 'paint:#5a0d0d', 'board header')
    m.box((-5.6, 29.2, 14.62), (21.6, 31.0, 14.7), 'label:EMERGENCY CONTROL', 'header lettering', north='danger:EMERGENCY CONTROL CENTRE')
    for cx in (-11, 27):
        m.cyl((cx, 30.1, 14.4), 0.95, 0.6, 'z', 'gauge:clock', 'control room clock', cap='gauge:clock')
    b = m.surface('north', face=14.42, title='INCIDENT BOARD')
    m.ctl(b, 'digits', 'title', X(8), 27.6, w=30, h=1.4, col='ff6040', label='', fmt='%s')
    for r in range(1, 7):
        v = 25.6 - (r - 1) * 1.95
        m.ctl(b, 'lamp', 'r%ds' % r, X(-13.2), v, w=0.6, h=0.6, col='ff3020', label='', blink=True)
        m.ctl(b, 'digits', 'r%da' % r, X(4.6), v, w=33, h=1.6, col='ffb020', label='', fmt='%s')
        m.ctl(b, 'digits', 'r%db' % r, X(25.6), v, w=8.4, h=1.6, col='ffd27a', label='', fmt='%s')
    # the line diagram strip under the board
    m.g('line diagram')
    m.box((-14, 12.6, 15.25), (30, 14.0, 15.4), 'paint:#f0eee6', 'line diagram board')
    for x0 in range(-13, 29, 6):
        m.box((x0, 13.15, 15.2), (x0 + 5, 13.45, 15.25), 'paint:#2a2a2a', 'track line')
        m.box((x0 + 4.6, 12.9, 15.18), (x0 + 5.4, 13.7, 15.25), 'paint:#1f6fb0', 'station mark')
    # left end: grab-bag locker with hi-vis tabards on hooks, extinguisher on its bracket
    m.g('grab-bag locker')
    m.box((-13.6, 0, 9.6), (-6.4, 17.6, 15.2), 'paint:#8a1414', 'locker body')
    m.box((-13.3, 0.6, 9.4), (-6.7, 17.0, 9.6), 'paint:#a61c1c', 'locker door')
    m.box((-12.6, 14.6, 9.32), (-7.4, 15.8, 9.4), 'label:GRAB BAGS', 'locker plate')
    m.box((-12.6, 13.0, 9.32), (-7.4, 14.2, 9.4), 'label:ICS TABARDS', 'locker plate 2')
    m.box((-7.6, 7.0, 9.1), (-7.2, 9.6, 9.4), 'metal:steel', 'locker handle')
    for y in (2.0, 15.0):
        m.box((-13.7, y, 9.4), (-13.3, y + 1.2, 9.8), 'metal:steel', 'hinge')
    for i in range(4):
        m.box((-12.8 + i * 1.6, 1.2, 9.36), (-11.6 + i * 1.6, 4.0, 9.4), 'louvre', 'vent slot')
    gl = m.surface('north', face=9.3, title='LOCKER')
    m.ctl(gl, 'zone', 'grab', X(-10.0), 8.8, w=6.4, h=16.0, col='000000', label='')
    m.box((-13.6, 17.6, 9.4), (-6.4, 18.0, 15.2), 'metal:steel', 'locker top')
    m.box((-13.2, 18.0, 11.0), (-11.0, 20.6, 14.4), 'paint:#e07010', 'grab bag')
    m.box((-10.8, 18.0, 11.0), (-8.6, 20.0, 14.4), 'paint:#e0c010', 'grab bag 2')
    m.box((-8.4, 18.0, 11.6), (-6.8, 19.0, 14.0), 'paint:#202020', 'torch case')
    m.g('fire extinguisher')
    m.box((-5.8, 4.0, 14.6), (-4.2, 4.6, 15.4), 'metal:steel', 'extinguisher bracket')
    m.cyl((-5.0, 0.6, 14.0), 0.75, 5.6, 'y', 'paint:#c81e1e', 'extinguisher cylinder')
    m.cyl((-5.0, 6.2, 14.0), 0.35, 0.8, 'y', 'paint:#1a1a1a', 'extinguisher valve')
    m.box((-5.5, 6.9, 13.6), (-4.5, 7.1, 14.8), 'paint:#1a1a1a', 'extinguisher handle')
    m.box((-5.6, 3.0, 13.2), (-4.4, 4.0, 13.26), 'label:CO2', 'extinguisher label')
    # right end: plan chest + the shelf
    SPOTS['incident_desk'] = cupboard_shelf(m, 18.4, 29.4, ['plans'], 6, title='SHELF', body='#3a4652', door='#4a5866')
    m.box((18.8, 9.4, 15.0), (29.0, 12.4, 15.32), 'paint:#f4f2ea', 'wall map')
    for x0 in (19.6, 22.4, 25.8):
        m.box((x0, 10.2, 14.98), (x0 + 1.6, 10.5, 15.0), 'paint:#d02020', 'map route')
    m.box((20.4, 10.9, 14.98), (27.6, 11.1, 15.0), 'paint:#2050d0', 'map river')
    # the commander's desk
    desk(m, {('l', 0): 'dl1', ('l', 1): 'dl2', ('l', 2): 'dl3', ('r', 0): 'dr1', ('r', 1): 'dr2', ('r', 2): 'dr3'}, body='#3a4652', front='#4a5866', pull='metal:steel')
    sf = strip(m, 3.6, 8.4, col='#22262a')
    buttons(m, sf, 7.6, [('prev', '1a1a1a', 'PREV'), ('next', '1a1a1a', 'NEXT'), ('accept', '1fa34a', 'ACCEPT'), ('level', 'f0d020', 'LEVEL'),
                         ('crew', 'e08020', 'CREW'), ('rescue', '2050d0', 'RESCUE'), ('fire', 'd02020', 'FIRE'), ('medical', 'f0f0f0', 'MEDIC')], u0=1.0, step=1.55, size=0.9)
    buttons(m, sf, 5.0, [('protect', 'e0a020', 'PROTECT'), ('isolate', 'f0d020', 'ISOLATE'), ('evac', 'd02020', 'EVAC'), ('allclear', '1fa34a', 'CLEAR'),
                         ('reopen', '20a0a0', 'REOPEN'), ('close', '505050', 'CLOSE'), ('print', 'f0f0f0', 'PRINT'), ('ack', '1a1a1a', 'ACK')], u0=1.0, step=1.55, size=0.9)
    m.ctl(sf, 'selector', 'view', 14.6, 6.3, w=1.3, h=1.3, col='202020', label='VIEW', pos=['PLAN', 'LOG', 'REC'])
    hd = hood(m, col='#1e2226')
    lamp_row(m, hd, 11.05, (('power', '30e060', '12V', False), ('newinc', 'ff3030', 'NEW', True), ('command', '30e060', 'CMD', False), ('crewout', 'ffb020', 'CREW', False),
                            ('onscene', '30e060', 'SCENE', False), ('protected', 'ffb020', 'PROT', False), ('isolated', 'ffb020', 'ISOL', False), ('evac', 'ff3030', 'EVAC', True),
                            ('hazmat', 'ff8020', 'HAZ', True), ('reopening', '30e060', 'REOPEN', False)), u0=1.3, step=1.48, size=0.55)
    for i, (cid, col, lab, fmt) in enumerate((('resp', 'ffd040', 'RESPONSE', '%s'), ('open', 'ffd040', 'OPEN', '%s'), ('held', 'ff8040', 'HELD', '%d TRAINS'), ('pax', '8ad4ff', 'PAX', '%d PAX'))):
        u = 2.1 + i * 3.9
        m.ctl(hd, 'digits', cid, u, 9.75, w=3.5, h=0.75, col=col, label='', fmt=fmt)
    monitor(m, 0.6, 7.8, 12.4, 17.8, 10.2, ['m1', 'm2', 'm3', 'm4', 'm5', 'm6'], cols=['ff6040', 'f0f0f0', '8ad4ff', 'ffd040', 'ff8040', '7cf29a'])
    monitor(m, 8.2, 15.4, 12.4, 17.8, 10.2, ['p1', 'p2', 'p3', 'p4', 'p5', 'p6'], cols=['7cf29a'] * 6)
    m.g('phones and radio')
    m.box((14.2, 9.0, 8.6), (15.8, 9.9, 9.8), 'paint:#b01010', 'red emergency phone')
    m.cyl((14.1, 10.3, 9.2), 0.4, 1.8, 'x', 'paint:#b01010', 'handset')
    ph = m.surface('north', face=8.55, title='PHONE')
    m.ctl(ph, 'push', 'phone', X(15.0), 9.45, w=0.7, h=0.5, col='f0f0f0', label='CONTROL')
    m.box((0.4, 9.0, 8.8), (2.2, 10.0, 10.0), 'paint:#1a1a1a', 'radio base station')
    m.box((0.6, 9.4, 8.7), (2.0, 9.9, 8.8), 'led:30ff60', 'radio display')
    m.box((1.9, 10.0, 9.6), (2.05, 13.6, 9.75), 'paint:#1a1a1a', 'radio aerial')
    m.cyl((3.2, 9.0, 8.2), 0.5, 0.3, 'y', 'paint:#1a1a1a', 'microphone base')
    m.box((3.12, 9.3, 8.12), (3.28, 10.8, 8.28), 'metal:steel', 'gooseneck')
    m.cyl((3.2, 10.8, 8.0), 0.3, 0.7, 'z', 'paint:#1a1a1a', 'microphone')
    m.box((5.0, 9.0, 7.0), (8.6, 9.1, 8.2), 'paint:#f8f6ee', 'incident log sheet')
    m.box((9.0, 9.0, 7.2), (11.0, 9.1, 8.0), 'paint:#fff070', 'sticky notes')
    m.terminal_12v(16.2, 1.0, 8.0, 'east')
    m.save(item_gui=0.28)


# ===================================================================================================== breakdown depot
def breakdown_depot():
    m = Model('breakdown_depot')
    m.g('plinth')
    m.box((0, 0, 1.0), (16, 0.8, 16), 'concrete', 'concrete plinth')
    m.box((0, 0.8, 0.8), (16, 1.1, 1.0), 'haz', 'kick strip', faces=('north',))
    m.g('tool store')
    m.box((0.4, 0.8, 2.0), (15.6, 18.0, 15.8), 'paint:#2a4a7a', 'tool store body')
    m.box((0.2, 18.0, 1.8), (15.8, 18.6, 16.0), 'paint:#20385e', 'tool store roof')
    for z in (4.0, 8.0, 12.0):
        m.box((0.1, 1.0, z), (0.4, 17.6, z + 0.5), 'paint:#20385e', 'side rib')
        m.box((15.6, 1.0, z), (15.9, 17.6, z + 0.5), 'paint:#20385e', 'side rib')
    lk = m.surface('north', face=1.7, title='LOCKERS')
    for (cid, x0, x1, lab) in (('jacks', 0.6, 5.6, 'JACKS'), ('pack', 5.6, 10.6, 'PACKING'), ('slings', 10.6, 15.6, 'SLINGS')):
        m.box((x0 + 0.2, 1.2, 1.8), (x1 - 0.2, 9.8, 2.0), 'paint:#33598f', 'locker door')
        m.box((x0 + 0.8, 8.4, 1.72), (x1 - 0.8, 9.4, 1.8), 'label:' + lab, 'locker label')
        m.box((x1 - 1.2, 4.4, 1.5), (x1 - 0.9, 6.2, 1.8), 'metal:steel', 'locker handle')
        for i in range(3):
            m.box((x0 + 1.0, 2.0 + i * 0.9, 1.75), (x1 - 1.0, 2.4 + i * 0.9, 1.8), 'louvre', 'locker vent')
        m.ctl(lk, 'zone', cid, X((x0 + x1) / 2), 5.5, w=x1 - x0 - 0.6, h=8.2, col='000000', label='')
    # crew board
    m.g('crew board')
    m.box((0.8, 10.4, 1.8), (15.2, 17.4, 2.0), 'paint:#c4c6c2', 'crew board panel')
    m.nameplate(2.0, 16.4, 14.0, 17.2, 1.8, 'BREAKDOWN CREW')
    m.box((1.2, 14.0, 1.72), (14.8, 15.9, 1.8), 'paint:#03140a', 'status window')
    cb = m.surface('north', face=1.7, title='CREW BOARD')
    m.ctl(cb, 'digits', 'stat', 5.0, 15.35, w=7.0, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(cb, 'digits', 'eta', 11.4, 15.35, w=5.0, h=0.9, col='ffd040', label='', fmt='%s')
    m.ctl(cb, 'digits', 'job', 8.0, 14.45, w=13.2, h=0.8, col='ffb020', label='', fmt='%s')
    lamp_row(m, cb, 13.0, (('ready', '30e060', 'READY', False), ('out', 'ffb020', 'OUT', False), ('onsite', 'ff3030', 'SITE', False), ('returning', '30a0ff', 'RTN', False),
                            ('power', '30e060', '12V', False)), u0=1.8, step=1.7, size=0.6)
    lamp_row(m, cb, 11.2, (('kjack', '30e060', 'JACK', False), ('kpack', '30e060', 'PACK', False), ('ksling', '30e060', 'SLING', False)), u0=1.6, step=1.6, size=0.5)
    m.ctl(cb, 'digits', 'jacks', 1.6, 10.6, w=1.2, h=0.6, col='ffd040', label='', fmt='%d')
    m.ctl(cb, 'digits', 'packs', 3.2, 10.6, w=1.2, h=0.6, col='ffd040', label='', fmt='%d')
    m.ctl(cb, 'digits', 'slingn', 4.8, 10.6, w=1.2, h=0.6, col='ffd040', label='', fmt='%d')
    m.ctl(cb, 'gauge', 'kit', 7.4, 11.5, w=1.8, h=1.8, col='f2f0e6', label='KIT %', max=100)
    m.ctl(cb, 'push', 'turnout', 9.6, 12.1, w=0.9, h=0.9, col='d02020', label='TURN OUT')
    m.ctl(cb, 'push', 'standdown', 11.2, 12.1, w=0.9, h=0.9, col='1fa34a', label='STAND DN')
    m.ctl(cb, 'push', 'test', 12.8, 12.1, w=0.7, h=0.7, col='f0f0f0', label='TEST')
    m.ctl(cb, 'key', 'k_avail', 14.4, 12.0, w=0.9, h=0.9, col='c0a040', label='AVAIL', pos=['ON CALL', 'STOOD DN'])
    m.ctl(cb, 'selector', 's_crews', 10.4, 10.6, w=0.8, h=0.8, col='202020', label='', pos=['1 GANG', '2 GANGS'])
    # turn-out bell and beacon on the roof
    m.g('bell and beacon')
    m.box((3.0, 18.6, 4.0), (5.4, 19.2, 6.4), 'paint:#1a1a1a', 'bell bracket')
    m.cyl((4.2, 19.2, 5.2), 1.3, 1.0, 'y', 'metal:brass', 'turn-out bell', cap='metal:brass')
    m.cyl((4.2, 20.2, 5.2), 0.3, 0.4, 'y', 'metal:steel', 'bell striker')
    m.box((10.6, 18.6, 7.0), (13.4, 19.2, 9.8), 'paint:#1a1a1a', 'beacon base')
    m.cyl((12.0, 19.2, 8.4), 1.0, 1.8, 'y', 'glass', 'amber beacon')
    top = m.surface('up', face=16 - 21.05, title='BEACON')
    m.ctl(top, 'lamp', 'bell', X(12.0), 8.4, w=1.4, h=1.4, col='ffa020', label='', blink=True)
    # kit lying alongside: rerailing ramps, a hydraulic jack, packing timbers, rope coil
    m.g('kit outside')
    for i in range(2):
        m.box((15.9, 1.2 + i * 0.2, 3.0 + i * 5.0), (16.6, 9.2 + i * 0.2, 7.4 + i * 5.0), 'paint:#ffcc00', 'rerailing ramp', rot={'axis': 'z', 'angle': 22.5, 'origin': [16.2, 1.0, 5.0 + i * 5.0]})
    m.cyl((-1.2, 0.8, 4.0), 1.0, 2.6, 'y', 'paint:#c01818', 'hydraulic jack body')
    m.cyl((-1.2, 3.4, 4.0), 0.5, 1.0, 'y', 'metal:steel', 'jack ram')
    m.box((-2.0, 0.8, 3.4), (-0.4, 1.0, 6.4), 'metal:steel', 'jack foot')
    for i in range(4):
        m.box((-3.4, 0.8 + i * 0.8, 8.0), (-0.2, 1.6 + i * 0.8, 15.0), 'wood', 'packing timber')
    m.cyl((-2.0, 0.8, 1.2), 1.2, 0.8, 'y', 'paint:#c8b070', 'rope coil')
    m.g('locker hardware')
    for (x0, x1) in ((0.6, 5.6), (5.6, 10.6), (10.6, 15.6)):
        for y in (2.0, 8.4):
            m.box((x0 + 0.1, y, 1.6), (x0 + 0.4, y + 1.0, 1.85), 'metal:steel', 'door hinge')
        m.box((x1 - 1.3, 6.6, 1.55), (x1 - 0.8, 7.4, 1.8), 'metal:brass', 'padlock hasp')
        m.box((x1 - 1.4, 6.0, 1.45), (x1 - 0.7, 6.7, 1.55), 'metal:brass', 'padlock')
    m.g('roof edge')
    m.box((0.2, 18.6, 1.6), (15.8, 19.2, 2.0), 'paint:#f0c000', 'roof sign board', north='label:TOOL VAN STORE')
    m.box((0.0, 17.8, 15.8), (16.0, 18.2, 16.4), 'metal:steel', 'gutter')
    m.cyl((15.6, 0.8, 16.1), 0.25, 17.0, 'y', 'metal:steel', 'downpipe')
    m.g('side window')
    m.box((15.62, 11.0, 5.0), (15.8, 15.6, 11.0), 'glass', 'side window')
    for z in (5.0, 8.0, 11.0):
        m.box((15.6, 11.0, z - 0.1), (15.85, 15.6, z + 0.1), 'paint:#20385e', 'window bar')
    m.g('hooks and chains')
    for i, z in enumerate((3.0, 6.0, 9.0, 12.0)):
        m.box((0.0, 14.6, z), (0.4, 14.9, z + 0.4), 'metal:steel', 'hook')
        for k in range(4):
            m.box((-0.2, 13.8 - k * 0.8, z + 0.05), (0.1, 14.5 - k * 0.8, z + 0.35), 'metal:steel', 'chain link')
    m.g('hand tools')
    m.box((-0.6, 0.8, 13.2), (-0.3, 9.0, 13.5), 'paint:#c01818', 'crowbar', rot={'axis': 'x', 'angle': -22.5, 'origin': [-0.45, 0.8, 13.35]})
    m.box((-0.8, 0.8, 11.0), (-0.5, 7.0, 11.3), 'wood', 'sledge handle')
    m.box((-1.2, 7.0, 10.6), (-0.1, 8.0, 11.7), 'metal:steel', 'sledge head')
    for x in (3.0, 13.0):
        m.box((x - 0.8, 0.8, 0.2), (x + 0.8, 1.6, 1.0), 'paint:#ffcc00', 'wheel chock')
    m.terminal_12v(15.6, 1.4, 14.0, 'east')
    m.gland(8.0, 0.8, 14.0, 'down', r=0.35)
    m.save(item_gui=0.4)


# ========================================================================================================== fire panel
def fire_panel():
    m = Model('fire_panel')
    m.g('wall board')
    m.box((0.6, 0, 14.6), (15.4, 30, 16), 'paint:#d8d6cf', 'backboard')
    m.box((0.6, 0, 14.4), (15.4, 0.6, 14.6), 'paint:#3a3a3a', 'skirting')
    m.g('control panel')
    m.box((1.6, 15.0, 11.2), (14.4, 28.0, 14.6), 'paint:#b81818', 'panel cabinet')
    m.box((1.4, 15.0, 11.0), (14.6, 28.0, 11.2), 'paint:#c82020', 'panel door')
    m.box((2.0, 25.4, 10.94), (14.0, 27.6, 11.0), 'paint:#f4f2ea', 'panel title plate', north='label:FIRE ALARM')
    m.box((2.2, 22.0, 10.96), (13.8, 25.0, 11.0), 'paint:#06120a', 'LCD window')
    m.box((14.2, 18.0, 10.8), (14.6, 21.0, 11.0), 'metal:steel', 'door lock')
    for (x, y) in ((1.8, 15.4), (14.2, 15.4), (1.8, 27.6), (14.2, 27.6)):
        m.bolt(x, y, 11.0, 'north', 0.35)
    fp = m.surface('north', face=10.9, title='FIRE PANEL')
    m.ctl(fp, 'digits', 'stat', 8.0, 24.4, w=11.2, h=0.8, col='ff6040', label='', fmt='%s')
    m.ctl(fp, 'digits', 'plac', 8.0, 23.5, w=11.2, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(fp, 'digits', 'rule', 8.0, 22.6, w=11.2, h=0.7, col='f0f0f0', label='', fmt='%s')
    lamp_row(m, fp, 21.0, (('fire', 'ff2020', 'FIRE', True), ('pre', 'ffb020', 'PRE', False), ('fault', 'ffd020', 'FAULT', False), ('predis', 'ff2020', 'PRE-DIS', True),
                            ('disch', 'ff8020', 'DISCH', False), ('haz', 'ff8020', 'HAZ', True)), u0=2.2, step=2.3, size=0.6)
    lamp_row(m, fp, 19.2, (('iso', 'ffb020', 'ISOL', False), ('abort', 'ffd020', 'ABORT', False), ('auto', '30e060', 'AUTO', False), ('sil', 'ffd020', 'SIL', False),
                            ('power', '30e060', '12V', False)), u0=2.2, step=2.3, size=0.55)
    m.ctl(fp, 'digits', 'cnt', 13.4, 19.2, w=1.6, h=0.8, col='ff3020', label='', fmt='%d S')
    m.ctl(fp, 'digits', 'zonem', 13.4, 18.2, w=1.6, h=0.7, col='7cf29a', label='', fmt='%d M')
    for i, (cid, col, lab) in enumerate((('silence', 'f0d020', 'SILENCE'), ('reset', '2050d0', 'RESET'), ('evac', 'd02020', 'EVACUATE'), ('isolate', 'e08020', 'ISOLATE'),
                                         ('discharge', 'd02020', 'RELEASE'), ('test', 'f0f0f0', 'TEST'))):
        m.ctl(fp, 'push', cid, 2.2 + i * 1.95, 17.0, w=0.9, h=0.9, col=col, label=lab)
    m.ctl(fp, 'selector', 's_zone', 3.0, 15.9, w=0.8, h=0.8, col='202020', label='', pos=['8M', '16M', '24M', '32M'])
    m.ctl(fp, 'key', 'k_mode', 6.4, 15.9, w=0.8, h=0.8, col='c0a040', label='', pos=['MAN', 'AUTO'])
    m.ctl(fp, 'key', 'k_iso', 9.4, 15.9, w=0.8, h=0.8, col='c0a040', label='', pos=['MAN', 'AUTO'])
    m.ctl(fp, 'selector', 's_abort', 12.6, 15.9, w=0.8, h=0.8, col='ffd020', label='', pos=['NORM', 'ABORT'])
    # sounder + strobe above
    m.g('sounder and strobe')
    m.box((5.4, 28.0, 13.0), (10.6, 28.6, 14.6), 'paint:#b81818', 'sounder base')
    m.cyl((8.0, 28.6, 13.8), 1.6, 1.2, 'y', 'paint:#c82020', 'sounder bell', cap='metal:steel')
    m.box((12.0, 28.0, 13.2), (14.0, 29.2, 14.6), 'paint:#f0f0f0', 'strobe base')
    m.box((12.2, 29.2, 13.4), (13.8, 29.9, 14.4), 'glass', 'strobe lens')
    st = m.surface('north', face=13.15, title='STROBE')
    m.ctl(st, 'lamp', 'fire', X(13.0), 29.0, w=1.4, h=1.0, col='ff2020', label='', blink=True)
    # manual call point and release mushroom
    m.g('manual release')
    m.box((1.8, 11.2, 12.6), (5.2, 14.4, 14.6), 'paint:#ffd020', 'release station')
    m.box((2.0, 13.4, 12.54), (5.0, 14.2, 12.6), 'label:MANUAL RELEASE', 'release plate')
    mr = m.surface('north', face=12.5, title='RELEASE')
    m.ctl(mr, 'mushroom', 'm_release', X(3.5), 12.3, w=1.4, h=1.4, col='d02020', label='')
    # agent cylinder on its brackets (store), extinguisher cabinet (store), kit box (store)
    m.g('agent cylinder')
    m.cyl((12.6, 0.6, 12.4), 1.6, 9.0, 'y', 'paint:#c81e1e', 'agent cylinder')
    m.cyl((12.6, 9.6, 12.4), 0.7, 0.9, 'y', 'metal:brass', 'cylinder valve')
    m.cyl((12.6, 10.5, 12.4), 0.25, 1.4, 'y', 'metal:copper', 'discharge pipe')
    m.box((12.35, 11.9, 12.15), (12.85, 12.15, 14.6), 'metal:copper', 'pipe to wall')
    for y in (2.0, 7.0):
        m.box((10.8, y, 13.9), (14.4, y + 0.5, 14.6), 'metal:steel', 'cylinder strap')
    m.box((11.4, 5.0, 10.82), (13.8, 6.4, 10.9), 'label:WATER MIST', 'cylinder label')
    m.cyl((12.6, 7.2, 10.6), 0.6, 0.4, 'z', 'gauge:pressure', 'cylinder gauge', cap='gauge:pressure')
    ag = m.surface('north', face=10.6, title='AGENT')
    m.ctl(ag, 'gauge', 'agent', X(12.6), 3.6, w=1.6, h=1.6, col='f2f0e6', label='AGENT %', max=100)
    m.ctl(ag, 'zone', 'agent', X(12.6), 7.0, w=3.0, h=3.0, col='000000', label='')
    m.g('extinguisher cabinet')
    m.box((1.4, 0.6, 11.8), (10.2, 10.8, 14.6), 'paint:#b81818', 'cabinet body')
    m.box((1.8, 1.0, 11.6), (9.8, 10.4, 11.8), 'glass', 'cabinet glass door')
    m.box((2.2, 9.0, 11.56), (9.4, 10.0, 11.6), 'label:FIRE EXTINGUISHER', 'cabinet plate')
    m.cyl((4.0, 1.4, 13.2), 0.9, 5.6, 'y', 'paint:#c81e1e', 'extinguisher (water)')
    m.cyl((7.4, 1.4, 13.2), 0.9, 5.6, 'y', 'paint:#202020', 'extinguisher (CO2)')
    m.box((3.4, 3.6, 12.25), (4.6, 4.6, 12.3), 'label:H2O', 'water label')
    m.box((6.8, 3.6, 12.25), (8.0, 4.6, 12.3), 'label:CO2', 'co2 label')
    ec = m.surface('north', face=11.5, title='CABINET')
    m.ctl(ec, 'zone', 'ext', X(5.8), 5.0, w=7.6, h=7.6, col='000000', label='')
    m.box((1.6, 10.8, 12.2), (10.0, 11.2, 14.6), 'metal:steel', 'cabinet top')
    m.box((6.6, 11.2, 12.4), (10.0, 13.0, 14.4), 'paint:#2a7a3a', 'first-aid kit box')
    m.box((7.6, 11.6, 12.36), (9.0, 12.6, 12.4), 'label:+', 'first-aid cross')
    kb = m.surface('north', face=12.3, title='KIT')
    m.ctl(kb, 'zone', 'kit', X(8.3), 12.1, w=3.2, h=1.6, col='000000', label='')
    m.g('suppression pipework')
    m.box((12.35, 12.15, 14.2), (12.85, 29.6, 14.6), 'metal:copper', 'riser to the nozzles')
    m.box((1.0, 29.3, 14.2), (15.0, 29.8, 14.6), 'metal:copper', 'nozzle header')
    for x in (2.0, 5.0, 8.0, 11.0, 14.0):
        m.cyl((x, 28.6, 14.0), 0.35, 0.7, 'y', 'metal:brass', 'mist nozzle')
        m.box((x - 0.3, 29.3, 14.0), (x + 0.3, 29.8, 14.2), 'metal:copper', 'nozzle drop')
    m.g('conduits')
    for x in (3.0, 4.0, 5.0):
        m.box((x, 28.0, 13.6), (x + 0.5, 30.0, 14.1), 'paint:#9a9a96', 'conduit')
    m.box((2.8, 29.9, 13.4), (5.7, 30.0, 14.6), 'paint:#9a9a96', 'conduit saddle')
    m.g('notices')
    m.box((15.0, 15.0, 14.5), (15.4, 25.0, 15.4), 'paint:#d8d6cf', 'notice frame')
    m.box((0.62, 16.0, 14.0), (1.4, 26.0, 14.6), 'paint:#f4f2ea', 'fire action notice', west='label:FIRE ACTION')
    m.box((10.6, 11.2, 14.0), (14.6, 14.6, 14.6), 'paint:#f4f2ea', 'zone chart', north='label:ZONE 1|TRACK + ROOM')
    m.g('call point')
    m.box((6.0, 11.6, 13.6), (9.0, 14.6, 14.6), 'paint:#c81e1e', 'manual call point')
    m.box((6.4, 12.0, 13.5), (8.6, 14.2, 13.6), 'paint:#f4f2ea', 'call point glass', north='label:BREAK')
    m.g('door hinges')
    for y in (16.0, 26.0):
        m.box((1.2, y, 10.9), (1.6, y + 1.2, 11.3), 'metal:steel', 'panel hinge')
    m.g('fire blanket')
    m.box((15.4, 3.0, 12.6), (15.9, 9.0, 14.6), 'paint:#c81e1e', 'fire blanket pouch', east='label:FIRE BLANKET')
    m.box((15.5, 9.0, 13.2), (15.8, 9.6, 14.0), 'paint:#1a1a1a', 'pull tapes')
    m.terminal_12v(1.0, 2.0, 13.0, 'west')
    m.save(item_gui=0.4)


# ==================================================================================================== emergency exit sign
def evac_sign():
    m = Model('evac_sign')
    m.g('wall plate')
    m.box((1.0, 6.0, 15.4), (15.0, 16.0, 16.0), 'paint:#e8e6de', 'wall back plate')
    for (x, y) in ((1.6, 6.6), (14.4, 6.6), (1.6, 15.4), (14.4, 15.4)):
        m.bolt(x, y, 15.4, 'north', 0.35)
    m.g('sign box')
    m.box((1.6, 10.2, 13.0), (14.4, 15.6, 15.4), 'paint:#f0f0ee', 'sign housing')
    m.box((1.8, 10.4, 12.8), (14.2, 15.4, 13.0), 'paint:#1e1e1e', 'sign face')
    m.box((2.0, 14.6, 12.76), (14.0, 15.2, 12.8), 'paint:#13a54a', 'green header band')
    m.box((2.0, 10.6, 12.76), (14.0, 11.2, 12.8), 'paint:#13a54a', 'green footer band')
    # running man pictogram (green on the face, beside the lettering)
    m.g('running man')
    m.box((2.4, 13.4, 12.74), (3.2, 14.2, 12.76), 'paint:#20d060', 'head')
    m.box((2.6, 12.0, 12.74), (3.4, 13.4, 12.76), 'paint:#20d060', 'body')
    m.box((3.4, 12.6, 12.74), (4.2, 13.0, 12.76), 'paint:#20d060', 'arm')
    m.box((2.0, 11.4, 12.74), (2.6, 12.2, 12.76), 'paint:#20d060', 'back leg')
    m.box((3.4, 11.4, 12.74), (4.0, 12.0, 12.76), 'paint:#20d060', 'front leg')
    m.box((4.4, 11.4, 12.74), (5.0, 14.2, 12.76), 'paint:#f0f0f0', 'door frame')
    sf = m.surface('north', face=12.7, title='SIGN')
    m.ctl(sf, 'digits', 'exit', X(9.4), 12.9, w=8.6, h=2.2, col='30ff60', label='', fmt='%s')
    # emergency luminaire under it: two spot heads, charge / fault LEDs, test button
    m.g('emergency lights')
    m.box((2.4, 7.4, 12.6), (13.6, 9.6, 15.4), 'paint:#f0f0ee', 'battery housing')
    m.box((3.0, 7.8, 12.54), (6.4, 9.2, 12.6), 'label:EMERGENCY', 'housing label')
    m.box((9.0, 7.6, 12.52), (13.2, 8.4, 12.6), 'label:3H MAINT', 'housing label 2')
    for x in (1.2, 13.2):
        m.box((x + 0.4, 9.6, 13.4), (x + 1.2, 10.2, 14.2), 'paint:#d0d0cc', 'spot bracket')
        m.cyl((x + 0.8, 8.8, 12.4), 0.9, 1.6, 'z', 'paint:#f0f0ee', 'spot head', cap='lamp:fff6d8')
    sp = m.surface('north', face=12.35, title='SPOTS')
    m.ctl(sp, 'lamp', 'spotl', X(2.0), 8.8, w=1.4, h=1.4, col='fff6d8', label='')
    m.ctl(sp, 'lamp', 'spotr', X(14.0), 8.8, w=1.4, h=1.4, col='fff6d8', label='')
    lb = m.surface('north', face=12.5, title='LEDS')
    m.ctl(lb, 'lamp', 'charge', X(7.6), 8.2, w=0.4, h=0.4, col='30e060', label='')
    m.ctl(lb, 'lamp', 'fault', X(8.3), 8.2, w=0.4, h=0.4, col='ffb020', label='')
    m.ctl(lb, 'push', 'test', X(7.95), 9.1, w=0.5, h=0.5, col='f0f0f0', label='')
    m.ctl(lb, 'digits', 'battp', X(11.2), 9.05, w=1.6, h=0.6, col='7cf29a', label='', fmt='%d%%')
    m.ctl(lb, 'selector', 's_arrow', X(4.6), 6.8, w=0.6, h=0.6, col='202020', label='', pos=['-', '<', '>', '^', 'V'])
    m.g('housing detail')
    for i in range(6):
        m.box((6.6 + i * 0.4, 7.5, 12.56), (6.8 + i * 0.4, 9.4, 12.6), 'louvre', 'housing vent')
    for (x, y) in ((1.9, 10.5), (14.1, 10.5), (1.9, 15.3), (14.1, 15.3)):
        m.bolt(x, y, 13.0, 'north', 0.25)
    for x in (1.6, 14.0):
        m.box((x, 10.2, 13.0), (x + 0.4, 15.6, 15.4), 'paint:#dcdcd8', 'housing side trim')
    m.box((1.6, 15.6, 13.4), (14.4, 15.9, 15.4), 'paint:#dcdcd8', 'housing top cap')
    m.g('LEDs')
    for i in range(8):
        m.box((2.6 + i * 1.5, 15.0, 12.74), (3.0 + i * 1.5, 15.15, 12.76), 'paint:#80ffa0', 'LED')
        m.box((2.6 + i * 1.5, 10.65, 12.74), (3.0 + i * 1.5, 10.8, 12.76), 'paint:#80ffa0', 'LED')
    m.g('supply')
    m.box((6.8, 16.0, 14.6), (9.2, 17.4, 16.0), 'paint:#d0d0cc', 'junction box')
    for (x, y) in ((7.0, 16.2), (9.0, 16.2), (7.0, 17.2), (9.0, 17.2)):
        m.bolt(x, y, 14.6, 'north', 0.2)
    m.box((7.7, 17.4, 15.0), (8.3, 22.0, 15.6), 'paint:#d0d0cc', 'conduit up the wall')
    m.box((7.4, 19.0, 14.9), (8.6, 19.4, 15.7), 'paint:#b0b0ac', 'conduit saddle')
    for x in (1.3, 13.3):
        m.box((x + 0.6, 8.6, 13.2), (x + 1.0, 9.0, 13.6), 'metal:steel', 'aim knob')
    m.box((14.4, 7.6, 13.0), (14.8, 9.4, 15.4), 'label:MAINT', 'side label', east='label:MAINT')
    for x in (3.0, 6.0, 10.0, 13.0):
        m.box((x, 9.6, 14.6), (x + 0.8, 10.2, 15.4), 'metal:steel', 'mounting bracket')
    m.terminal_12v(8.0, 6.0, 15.4, 'north')
    m.save(item_gui=0.55)


# ======================================================================================================= assembly point
def assembly_point():
    m = Model('assembly_point')
    m.g('base')
    m.box((4.0, 0, 4.0), (12.0, 1.0, 12.0), 'concrete', 'concrete footing')
    m.cyl((8.0, 1.0, 8.0), 0.8, 29.0, 'y', 'galv', 'sign post')
    m.cyl((8.0, 30.0, 8.0), 0.9, 0.3, 'y', 'paint:#1a1a1a', 'post cap')
    m.g('sign')
    m.box((1.0, 18.4, 7.4), (15.0, 30.6, 8.0), 'paint:#13a54a', 'green sign board')
    m.box((1.4, 18.8, 7.34), (14.6, 30.2, 7.4), 'paint:#f4f4f0', 'white border')
    m.box((1.8, 19.2, 7.3), (14.2, 29.8, 7.34), 'paint:#13a54a', 'green field')
    # four people and an arrow pointing in, white on green
    for i, x in enumerate((3.0, 6.0, 9.0, 12.0)):
        y0 = 22.4
        m.box((x - 0.5, y0 + 3.4, 7.26), (x + 0.5, y0 + 4.4, 7.3), 'paint:#f4f4f0', 'person head')
        m.box((x - 0.7, y0 + 1.2, 7.26), (x + 0.7, y0 + 3.3, 7.3), 'paint:#f4f4f0', 'person body')
        m.box((x - 0.6, y0, 7.26), (x - 0.15, y0 + 1.2, 7.3), 'paint:#f4f4f0', 'person leg')
        m.box((x + 0.15, y0, 7.26), (x + 0.6, y0 + 1.2, 7.3), 'paint:#f4f4f0', 'person leg')
    for i in range(4):
        m.box((4.0 + i * 2.2, 28.0, 7.26), (5.8 + i * 2.2, 28.4, 7.3), 'paint:#f4f4f0', 'arrow shaft')
    m.box((2.6, 19.6, 7.24), (13.4, 21.6, 7.3), 'label:ASSEMBLY POINT', 'lettering')
    m.box((1.0, 18.4, 8.0), (15.0, 30.6, 8.4), 'galv', 'sign back plate')
    for y in (20.0, 29.0):
        m.box((6.8, y, 8.4), (9.2, y + 0.8, 9.2), 'galv', 'post clamp')
    m.g('roll-call box')
    m.box((4.4, 7.0, 6.0), (11.6, 15.0, 7.2), 'paint:#2a2d30', 'roll-call box')
    m.box((4.2, 7.0, 5.8), (11.8, 15.0, 6.0), 'paint:#c4c6c2', 'box door')
    m.box((4.6, 13.8, 5.74), (11.4, 14.8, 5.8), 'label:FIRE WARDEN', 'warden plate')
    m.box((5.0, 11.6, 5.76), (11.0, 13.4, 5.8), 'paint:#03140a', 'count window')
    for y in (8.0, 14.0):
        m.box((6.8, y, 7.2), (9.2, y + 0.6, 7.6), 'galv', 'box clamp')
    m.box((11.8, 12.0, 6.2), (12.4, 14.0, 7.0), 'paint:#1a1a1a', 'radio aerial base')
    m.box((12.0, 14.0, 6.5), (12.2, 17.6, 6.7), 'paint:#1a1a1a', 'radio aerial')
    rb = m.surface('north', face=5.7, title='ROLL CALL')
    m.ctl(rb, 'digits', 'count', X(6.4), 12.9, w=2.4, h=0.9, col='7cf29a', label='', fmt='%d IN')
    m.ctl(rb, 'digits', 'need', X(9.6), 12.9, w=2.4, h=0.9, col='ffd040', label='', fmt='OF %d')
    m.ctl(rb, 'digits', 'inc', X(8.0), 12.0, w=5.6, h=0.6, col='ffb020', label='', fmt='%s')
    m.ctl(rb, 'lamp', 'evac', X(5.4), 10.6, w=0.6, h=0.6, col='ff2020', label='EVAC', blink=True)
    m.ctl(rb, 'lamp', 'all', X(7.0), 10.6, w=0.6, h=0.6, col='30e060', label='ALL IN')
    m.ctl(rb, 'lamp', 'power', X(10.6), 10.6, w=0.5, h=0.5, col='30e060', label='12V')
    m.ctl(rb, 'push', 'rollcall', X(5.6), 8.4, w=0.9, h=0.9, col='ffd020', label='ROLL')
    m.ctl(rb, 'push', 'allin', X(8.0), 8.4, w=0.9, h=0.9, col='1fa34a', label='ALL IN')
    m.ctl(rb, 'push', 'test', X(10.4), 8.4, w=0.7, h=0.7, col='f0f0f0', label='TEST')
    m.g('footing detail')
    for (x, z) in ((5.0, 5.0), (11.0, 5.0), (5.0, 11.0), (11.0, 11.0)):
        m.cyl((x, 1.0, z), 0.35, 0.4, 'y', 'metal:steel', 'anchor bolt')
    m.box((6.6, 1.0, 6.6), (9.4, 1.3, 9.4), 'galv', 'base plate')
    for y in (3.0, 4.6, 16.0, 17.2):
        m.cyl((8.0, y, 8.0), 0.85, 0.5, 'y', 'paint:#f0f0f0', 'reflective band')
    m.g('beacon')
    m.box((7.2, 30.6, 7.2), (8.8, 31.0, 8.8), 'paint:#1a1a1a', 'beacon base')
    m.cyl((8.0, 31.0, 8.0), 0.7, 0.9, 'y', 'glass', 'green beacon')
    bt = m.surface('up', face=16 - 31.95, title='BEACON')
    m.ctl(bt, 'lamp', 'evac', 8.0, 8.0, w=1.0, h=1.0, col='30ff60', label='', blink=True)
    m.g('box detail')
    for y in (8.0, 13.6):
        m.box((4.0, y, 5.8), (4.4, y + 0.9, 6.2), 'metal:steel', 'box hinge')
    m.box((11.4, 10.0, 5.6), (11.8, 11.0, 5.8), 'metal:brass', 'box lock')
    for i in range(3):
        m.box((4.8 + i * 0.8, 7.2, 5.76), (5.3 + i * 0.8, 7.8, 5.8), 'louvre', 'box vent')
    m.box((4.4, 15.0, 5.6), (11.6, 15.4, 7.2), 'paint:#2a2d30', 'box rain hood')
    m.g('clipboard')
    m.box((12.0, 8.0, 6.0), (13.6, 10.2, 6.2), 'paint:#8a6a40', 'clipboard')
    m.box((12.2, 8.2, 5.96), (13.4, 9.8, 6.0), 'paint:#f8f6ee', 'roll-call sheet')
    m.box((12.5, 10.0, 5.9), (13.1, 10.3, 6.0), 'metal:steel', 'clip')
    m.box((2.4, 17.2, 7.6), (13.6, 18.2, 7.8), 'paint:#f4f4f0', 'sub plate', north='label:FIRE ASSEMBLY POINT A')
    m.terminal_12v(8.0, 3.0, 8.8, 'south')
    m.save(item_gui=0.4)


# ===================================================================================================== maintenance terminal
def maint_terminal():
    m = Model('maint_terminal')
    m.g('desk')
    m.box((0.2, 8.4, 2.0), (15.8, 9.0, 14.6), 'paint:#5c6670', 'desk top')
    m.box((0, 8.2, 1.8), (16, 8.4, 14.8), 'paint:#3a4048', 'desk edge')
    for x in (0.6, 14.4):
        m.box((x, 0, 12.6), (x + 1.0, 8.2, 14.0), 'paint:#2a2e33', 'desk leg')
    m.box((1.6, 0.4, 13.0), (14.4, 1.0, 13.6), 'paint:#2a2e33', 'foot rail')
    m.g('drawer pedestal')
    m.box((9.6, 0.4, 2.6), (15.0, 8.2, 12.6), 'paint:#46505a', 'pedestal')
    dr = m.surface('north', face=2.4, title='DRAWERS')
    for k in range(3):
        y0 = 0.9 + k * 2.4
        m.box((9.9, y0, 2.4), (14.7, y0 + 2.1, 2.6), 'paint:#56626e', 'drawer front')
        m.box((11.6, y0 + 1.2, 2.15), (13.0, y0 + 1.5, 2.4), 'metal:steel', 'drawer pull')
    m.box((10.2, 6.0, 2.36), (14.4, 6.6, 2.4), 'label:WORK ORDERS', 'drawer label')
    m.ctl(dr, 'zone', 'files', X(12.3), 4.2, w=4.6, h=7.0, col='000000', label='')
    m.g('CRT monitor')
    m.box((3.0, 9.0, 8.6), (12.6, 9.6, 13.0), 'paint:#d8d4c4', 'monitor plinth')
    m.box((2.6, 9.6, 7.6), (13.0, 17.4, 13.6), 'paint:#d8d4c4', 'monitor case')
    m.box((3.4, 10.4, 7.46), (12.2, 16.6, 7.6), 'paint:#041008', 'screen')
    m.box((3.0, 17.4, 9.0), (12.6, 17.8, 12.4), 'louvre', 'monitor top vents')
    mon = m.surface('north', face=7.4, title='SCREEN')
    for i, cid in enumerate(['t1', 't2', 't3', 't4', 't5', 't6']):
        m.ctl(mon, 'digits', cid, X(7.8), 16.1 - i * 0.98, w=8.4, h=0.85, col='ffd040' if i == 0 else '7cf29a', label='', fmt='%s')
    m.box((3.0, 9.8, 7.5), (5.0, 10.3, 7.6), 'label:TELEMETRY', 'monitor badge')
    m.g('keyboard + keypad')
    m.box((2.4, 9.0, 3.0), (11.6, 9.5, 6.6), 'paint:#cfcbbb', 'keyboard')
    for r in range(3):
        for c in range(9):
            m.box((2.7 + c * 1.0, 9.5, 3.3 + r * 1.05), (3.5 + c * 1.0, 9.7, 4.1 + r * 1.05), 'paint:#e8e4d4', 'key')
    kp = m.surface('up', face=16 - 9.75, title='KEYPAD')
    m.box((11.8, 9.0, 2.4), (15.6, 9.6, 7.6), 'paint:#2a2e33', 'function keypad')
    keys = [('prev', '1a1a1a', '<'), ('next', '1a1a1a', '>'), ('cprev', '404040', 'C<'), ('cnext', '404040', 'C>'), ('ack', 'f0d020', 'ACK'),
            ('test', 'f0f0f0', 'TEST'), ('operate', '2050d0', 'OPER'), ('wo', 'e08020', 'W/O'), ('print', 'f0f0f0', 'PRINT')]
    for i, (cid, col, lab) in enumerate(keys):
        r, c = i // 3, i % 3
        m.ctl(kp, 'push', cid, X(12.5 + c * 1.25), 3.2 + r * 1.6, w=0.7, h=0.7, col=col, label=lab)
    m.ctl(kp, 'selector', 's_filter', X(9.5), 7.6, w=0.9, h=0.9, col='202020', label='FILTER', pos=['ALL', 'FAULT', 'NO 12V'])
    m.g('printer')
    m.box((0.4, 9.0, 8.6), (2.4, 11.6, 13.4), 'paint:#d0ccbc', 'printer body')
    m.box((0.6, 11.6, 9.4), (2.2, 12.6, 12.6), 'paint:#fbfbf6', 'paper stack')
    m.box((0.6, 10.0, 8.4), (2.2, 10.4, 8.6), 'paint:#1a1a1a', 'paper slot')
    pr = m.surface('north', face=8.3, title='PRINTER')
    m.ctl(pr, 'zone', 'paper', X(1.4), 10.6, w=2.0, h=2.4, col='000000', label='')
    m.g('status strip')
    m.box((3.4, 17.8, 9.2), (12.2, 19.2, 10.0), 'paint:#2a2e33', 'status bar')
    sb = m.surface('north', face=9.1, title='STATUS')
    lamp_row(m, sb, 18.5, (('power', '30e060', '', False), ('link', '30e060', '', False), ('local', 'ff3020', '', True), ('fault', 'ffb020', '', False)), u0=X(11.6), step=0.9, size=0.45)
    m.ctl(sb, 'digits', 'count', X(6.6), 18.5, w=2.0, h=0.8, col='ffd040', label='', fmt='%d ON BUS')
    m.ctl(sb, 'digits', 'filt', X(4.4), 18.5, w=1.6, h=0.8, col='8ad4ff', label='', fmt='%s')
    m.g('telephone + modem')
    m.box((12.0, 9.0, 9.4), (15.4, 10.0, 12.4), 'paint:#1a1a1a', 'line modem')
    m.box((12.4, 9.6, 9.3), (15.0, 9.9, 9.4), 'led:30ff60', 'modem leds')
    m.cyl((13.7, 10.0, 11.0), 0.3, 2.6, 'y', 'cable', 'bus cable riser')
    m.box((13.0, 0.0, 14.0), (14.4, 8.2, 14.8), 'paint:#3a4048', 'cable duct')
    m.g('desk lamp')
    m.cyl((14.6, 9.0, 4.0), 0.6, 0.3, 'y', 'paint:#1a1a1a', 'lamp foot')
    m.box((14.5, 9.3, 3.9), (14.7, 12.4, 4.1), 'metal:steel', 'lamp arm')
    m.box((13.6, 12.0, 3.4), (15.2, 12.8, 4.6), 'paint:#2a6a9a', 'lamp shade')
    m.box((13.8, 11.9, 3.6), (15.0, 12.0, 4.4), 'paint:#fff6c8', 'lamp bulb glow')
    m.g('mouse and phone')
    m.box((0.6, 9.0, 3.4), (1.6, 9.4, 4.8), 'paint:#cfcbbb', 'mouse')
    m.box((0.6, 9.4, 4.4), (1.6, 9.45, 4.8), 'paint:#8a887c', 'mouse buttons')
    m.box((0.4, 9.0, 5.4), (2.2, 9.6, 7.6), 'paint:#1a1a1a', 'desk phone')
    m.cyl((0.4, 9.9, 6.5), 0.3, 1.8, 'x', 'paint:#1a1a1a', 'phone handset')
    m.g('chair')
    m.cyl((8.0, 0.0, -4.0), 0.4, 4.2, 'y', 'metal:steel', 'chair column')
    for (dx, dz) in ((2.0, 0), (-2.0, 0), (0, 2.0), (0, -2.0)):
        m.box((8.0 + min(0, dx) - 0.2, 0.0, -4.0 + min(0, dz) - 0.2), (8.0 + max(0, dx) + 0.2, 0.4, -4.0 + max(0, dz) + 0.2), 'paint:#1a1a1a', 'chair base leg')
    m.box((5.4, 4.2, -6.6), (10.6, 5.2, -1.4), 'paint:#2a3a5a', 'chair seat')
    m.box((5.6, 5.2, -7.0), (10.4, 11.0, -6.2), 'paint:#2a3a5a', 'chair back')
    m.box((7.6, 4.6, -6.6), (8.4, 6.0, -6.0), 'metal:steel', 'back post')
    m.g('printout')
    m.box((0.6, 9.6, 7.0), (2.2, 9.7, 8.4), 'paint:#fbfbf6', 'printout', rot={'axis': 'x', 'angle': -22.5, 'origin': [1.4, 9.6, 8.4]})
    m.terminal_12v(16.0, 1.0, 6.0, 'east')
    m.save(item_gui=0.45)


# ===================================================================================================== announcement unit
def announce_unit():
    m = Model('announce_unit')
    m.g('pedestal')
    m.box((2.0, 0, 6.0), (14.0, 0.6, 14.0), 'paint:#2a2d30', 'pedestal foot')
    m.box((5.0, 0.6, 8.0), (11.0, 9.0, 12.0), 'paint:#3a3f44', 'pedestal column')
    m.box((5.2, 2.0, 7.9), (10.8, 3.0, 8.0), 'louvre', 'column vents')
    m.g('console')
    m.box((1.0, 9.0, 3.0), (15.0, 10.6, 15.0), 'paint:#2b2f33', 'console base')
    m.box((1.0, 10.6, 10.0), (15.0, 16.8, 15.0), 'paint:#2b2f33', 'console back')
    m.box((1.2, 10.6, 3.4), (14.8, 11.0, 10.0), 'paint:#c4c6c2', 'control deck')
    m.box((1.0, 10.4, 3.2), (15.0, 10.6, 10.0), 'metal:steel', 'deck edge')
    m.box((1.4, 11.6, 9.86), (14.6, 16.2, 10.0), 'paint:#c4c6c2', 'upper panel')
    m.nameplate(4.0, 15.4, 12.0, 16.1, 9.86, 'PASSENGER INFO')
    m.box((1.8, 13.0, 9.8), (14.2, 15.2, 9.86), 'paint:#140c04', 'LED window')
    up = m.surface('north', face=9.78, title='LED')
    m.ctl(up, 'digits', 'l1', X(8.0), 14.65, w=12.0, h=0.85, col='ffb020', label='', fmt='%s')
    m.ctl(up, 'digits', 'l2', X(8.0), 13.6, w=12.0, h=0.85, col='ffb020', label='', fmt='%s')
    m.ctl(up, 'digits', 'stn', X(4.6), 12.2, w=5.6, h=0.75, col='8ad4ff', label='', fmt='%s')
    m.ctl(up, 'digits', 'train', X(11.2), 12.2, w=6.2, h=0.75, col='7cf29a', label='', fmt='%s')
    dk = m.surface('up', face=16 - 11.0, title='DECK')
    m.ctl(dk, 'selector', 's_tpl', 3.0, 4.6, w=1.2, h=1.2, col='202020', label='TEMPLATE', pos=['DELAY', 'PLAT', 'CANCEL', 'CONN', 'NEXT', 'EMERG', 'EVAC', 'CLEAR'])
    m.ctl(dk, 'selector', 's_plat', 6.4, 4.6, w=1.0, h=1.0, col='202020', label='PLAT', pos=['1', '2', '3', '4', '5', '6', '7', '8'])
    m.ctl(dk, 'key', 'k_auto', 13.4, 4.6, w=1.0, h=1.0, col='c0a040', label='MODE', pos=['MANUAL', 'AUTO'])
    for i, (cid, col, lab) in enumerate((('tprev', '1a1a1a', 'TRAIN <'), ('tnext', '1a1a1a', 'TRAIN >'), ('preview', 'f0d020', 'PREVIEW'), ('announce', 'd02020', 'ANNOUNCE'), ('test', 'f0f0f0', 'TEST'))):
        m.ctl(dk, 'push', cid, 2.4 + i * 2.8, 7.0, w=1.0, h=1.0, col=col, label=lab)
    lamp_row(m, dk, 9.2, (('power', '30e060', '12V', False), ('onair', 'ff2020', 'ON AIR', True), ('auto', '30e060', 'AUTO', False), ('delay', 'ffb020', 'DELAYS', False)), u0=2.4, step=2.0, size=0.55)
    m.ctl(dk, 'digits', 'cnt', 11.0, 9.2, w=1.8, h=0.7, col='ffd040', label='', fmt='%d')
    m.ctl(dk, 'digits', 'due', 13.4, 9.2, w=1.8, h=0.7, col='7cf29a', label='', fmt='%d DUE')
    m.g('microphone')
    m.cyl((12.6, 10.6, 12.6), 0.8, 0.5, 'y', 'paint:#1a1a1a', 'mic base')
    for i in range(5):
        m.cyl((12.6 - i * 0.25, 11.1 + i * 0.9, 12.4 - i * 0.35), 0.18, 0.9, 'y', 'metal:steel', 'gooseneck segment')
    m.cyl((11.4, 15.6, 10.6), 0.35, 1.0, 'z', 'paint:#1a1a1a', 'microphone head')
    m.box((11.1, 15.3, 10.5), (11.7, 15.9, 10.6), 'mesh', 'mic grille')
    m.g('speaker + chime')
    m.box((2.0, 16.8, 11.0), (7.0, 18.4, 14.6), 'paint:#2b2f33', 'monitor speaker')
    m.box((2.2, 17.0, 10.9), (6.8, 18.2, 11.0), 'mesh', 'speaker grille')
    m.box((8.4, 16.8, 11.6), (11.6, 17.8, 14.4), 'paint:#3a3f44', 'chime unit')
    m.cyl((10.0, 17.8, 13.0), 0.7, 0.3, 'y', 'metal:brass', 'chime bar')
    m.g('pedestal detail')
    for (x, z) in ((2.6, 6.6), (13.4, 6.6), (2.6, 13.4), (13.4, 13.4)):
        m.bolt(x, 0.6, z, 'up', 0.45)
        m.box((x - 0.5, -0.2, z - 0.5), (x + 0.5, 0.0, z + 0.5), 'rubber', 'rubber foot')
    for i in range(4):
        m.box((5.4 + i * 1.4, 4.0, 7.9), (6.2 + i * 1.4, 7.6, 8.0), 'louvre', 'column vent')
    m.box((7.4, 0.6, 11.9), (8.6, 9.0, 12.4), 'paint:#3a3f44', 'cable conduit')
    m.g('console detail')
    for x in (0.6, 15.0):
        m.box((x, 9.4, 5.0), (x + 0.4, 10.2, 8.6), 'metal:steel', 'side handle')
    for i in range(5):
        m.box((2.4 + i * 0.9, 17.0, 10.86), (2.7 + i * 0.9, 18.2, 10.9), 'paint:#1a1a1a', 'grille bar')
    m.cyl((9.4, 17.8, 12.0), 0.25, 1.6, 'x', 'metal:brass', 'chime bar 2')
    m.cyl((9.4, 17.8, 13.4), 0.25, 1.4, 'x', 'metal:brass', 'chime bar 3')
    m.box((13.4, 16.8, 12.0), (14.2, 18.4, 12.4), 'metal:steel', 'headset hook')
    m.box((13.0, 16.0, 11.6), (14.6, 16.8, 12.0), 'paint:#1a1a1a', 'headset band')
    for z in (11.2, 12.6):
        m.cyl((13.8, 15.4, z), 0.5, 0.4, 'z', 'paint:#1a1a1a', 'earcup')
    m.box((1.4, 11.0, 8.4), (4.4, 11.1, 9.8), 'paint:#f8f6ee', 'timetable card')
    m.box((1.6, 11.1, 8.6), (4.2, 11.12, 9.6), 'paint:#2050d0', 'timetable print')
    m.box((4.6, 11.0, 9.6), (11.4, 11.4, 9.9), 'label:TEMPLATE  PLAT  MODE', 'deck legend')
    m.terminal_12v(15.0, 1.0, 10.0, 'east')
    m.gland(8.0, 0.0, 10.0, 'down', r=0.35)
    m.save(item_gui=0.45)


NAMES = ['incident_desk', 'breakdown_depot', 'fire_panel', 'evac_sign', 'assembly_point', 'maint_terminal', 'announce_unit']

RECIPES = {
    'incident_desk': (['LRL', 'PDP', 'WCW'], {'L': 'minecraft:redstone_lamp', 'R': 'minecraft:redstone_block', 'P': 'minecraft:paper', 'D': 'irextras:freight_office', 'W': 'minecraft:planks', 'C': 'minecraft:chest'}),
    'breakdown_depot': (['IBI', 'PCP', 'ILI'], {'I': 'minecraft:iron_block', 'B': 'minecraft:noteblock', 'P': 'minecraft:piston', 'C': 'minecraft:chest', 'L': 'minecraft:lead'}),
    'fire_panel': (['RLR', 'RNR', 'BCB'], {'R': 'minecraft:red_nether_brick', 'L': 'minecraft:redstone_lamp', 'N': 'minecraft:noteblock', 'B': 'minecraft:bucket', 'C': 'minecraft:chest'}),
    'evac_sign': ([' G ', 'GLG', ' R '], {'G': 'minecraft:green_glazed_terracotta', 'L': 'minecraft:glowstone', 'R': 'minecraft:redstone'}),
    'assembly_point': ([' G ', ' I ', ' S '], {'G': 'minecraft:green_glazed_terracotta', 'I': 'minecraft:iron_bars', 'S': 'minecraft:stone_slab'}),
    'maint_terminal': (['GRG', 'IPI', 'ICI'], {'G': 'minecraft:glass_pane', 'R': 'minecraft:redstone', 'I': 'minecraft:iron_ingot', 'P': 'minecraft:paper', 'C': 'minecraft:comparator'}),
    'announce_unit': (['NRN', 'IJI', ' I '], {'N': 'minecraft:noteblock', 'R': 'minecraft:redstone_lamp', 'I': 'minecraft:iron_ingot', 'J': 'minecraft:jukebox'}),
}
LANG = {
    'tile.irextras.incident_desk.name': 'Incident Command Desk',
    'tile.irextras.breakdown_depot.name': 'Breakdown Crew Depot',
    'tile.irextras.fire_panel.name': 'Fire Alarm & Suppression Panel',
    'tile.irextras.evac_sign.name': 'Emergency Exit Sign',
    'tile.irextras.assembly_point.name': 'Emergency Assembly Point',
    'tile.irextras.maint_terminal.name': 'Remote Maintenance Terminal',
    'tile.irextras.announce_unit.name': 'Announcement Control Unit',
}
DATA0 = {'minecraft:planks', 'minecraft:stone_slab'}

if __name__ == '__main__':
    incident_desk(); breakdown_depot(); fire_panel(); evac_sign(); assembly_point(); maint_terminal(); announce_unit()
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
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# emergency response + control room (her list #67-80)\n' + '\n'.join(add) + '\n')
    print('shelf spots (model px, for TileIncidentDesk.SPOTS):')
    for k, v in SPOTS.items(): print(' ', k, v)
