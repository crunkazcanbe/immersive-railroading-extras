#!/usr/bin/env python3
"""Signalling batch 3, part A (her batch-3 list #11 #12 #20): Train-Stop Trip Cock, Balise Group + LEU, Independent
Safety Supervisor rack. Models face north; u = 16 - x. Every lamp, key, selector, button, display, gauge and the trip
arm is a live control the Java tile reads or works (safety/TileTripCock, TileBalise, TileSafetySupervisor). 12 V
terminal boxes are wire terminals, not buttons. Also writes blockstates, item models, recipes and lang lines."""
import json, os
from bbkit import Model, ASSETS

X = lambda u: 16 - u
PANEL = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
YEL = 'paint:#f2c418'


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label='', s=0.6, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def push(m, sf, cid, u, v, col, label, s=0.9):
    m.ctl(sf, 'push', cid, u, v, w=s, h=s, col=col, label=label)


def digits(m, sf, cid, u, v, w, col='7cf29a', h=0.7, fmt='%s'):
    m.ctl(sf, 'digits', cid, u, v, w=w, h=h, col=col, label='', fmt=fmt)


# ------------------------------------------------------------------------------------------------ 11 train stop
def train_stop():
    m = Model('train_stop')
    m.g('foundation')
    m.box((2, 0, 2), (14, 0.8, 14), 'concrete', 'concrete sleeper base')
    for x in (2.6, 13.4):
        for z in (2.6, 13.4):
            m.bolt(x, 0.8, z, 'up', 0.45)
    m.box((2.2, 0.8, 7.6), (14, 1.2, 8.4), 'metal:steel', 'rail clip bar')
    m.g('valve cabinet')
    m.box((2.4, 0.8, 2.6), (8.8, 9.0, 7.0), 'ral7032', 'solenoid valve cabinet')
    m.box((2.2, 9.0, 2.4), (9.0, 9.4, 7.2), 'ral7032', 'cabinet lid')
    m.box((2.6, 1.2, 2.5), (8.6, 8.6, 2.6), PANEL, 'control plate')
    m.nameplate(3.0, 7.7, 8.2, 8.4, 2.5, 'TRAIN STOP')
    for x in (2.8, 8.2):
        for y in (1.4, 8.2):
            m.bolt(x, y, 2.5, 'north', 0.3)
    for y in (2.0, 7.0):
        m.box((8.8, y, 3.0), (9.1, y + 1.0, 3.6), 'metal:steel', 'hinge')
    m.box((2.4, 1.0, 7.0), (8.8, 1.6, 7.2), 'louvre', 'vent')
    m.terminal_12v(2.4, 3.0, 5.0, 'west')
    m.gland(5.6, 0.8, 5.0, 'down', r=0.3)
    m.g('air system')
    m.cyl((10.0, 1.2, 4.0), 1.3, 5.0, 'y', 'paint:#3a5070', 'air reservoir')
    m.cyl((10.0, 6.2, 4.0), 0.5, 0.6, 'y', 'metal:brass', 'reservoir drain cock')
    m.box((8.8, 2.0, 3.8), (9.0, 2.3, 4.2), 'metal:copper', 'air pipe')
    m.box((9.0, 5.4, 3.8), (12.8, 5.7, 4.2), 'metal:copper', 'air pipe to motor')
    m.box((12.5, 1.6, 3.8), (12.8, 5.7, 4.2), 'metal:copper', 'air pipe drop')
    m.cyl((10.0, 7.0, 4.0), 0.55, 0.5, 'y', 'paint:#f0f0f0', 'pressure gauge body')
    m.g('air motor + arm housing')
    m.box((11.0, 0.8, 5.0), (14.0, 4.6, 11.0), 'paint:#2b2e33', 'cast iron housing')
    m.box((10.8, 4.6, 4.8), (14.2, 5.0, 11.2), 'paint:#2b2e33', 'housing cover')
    for z in (5.4, 7.0, 8.6, 10.4):
        m.bolt(12.5, 5.0, z, 'up', 0.35)
    m.cyl((11.2, 2.6, 6.0), 0.9, 2.6, 'x', 'paint:#3a3d40', 'air cylinder')
    m.cyl((13.8, 2.6, 6.0), 0.6, 0.6, 'x', 'metal:steel', 'cylinder end cap')
    m.cyl((12.5, 3.2, 8.0), 0.6, 1.0, 'z', 'metal:steel', 'arm shaft')
    m.box((11.6, 2.4, 9.0), (13.4, 4.2, 10.6), 'paint:#5a5d60', 'return spring housing')
    for y in (2.6, 3.1, 3.6, 4.1):
        m.box((11.5, y, 9.2), (13.5, y + 0.15, 10.4), 'metal:steel', 'spring coil')
    m.box((11.2, 0.8, 11.0), (13.8, 3.0, 12.6), 'paint:#2b2e33', 'arm stop block')
    m.box((11.5, 3.0, 11.2), (13.5, 3.3, 12.4), 'rubber', 'arm buffer')
    m.box((12.0, 4.6, 7.2), (13.0, 5.6, 8.8), 'haz', 'arm boss (moving arm is live)')
    m.g('trip arm guard')
    m.box((14.0, 0.8, 4.4), (14.2, 3.6, 12.0), 'haz', 'guard plate', faces=('east', 'west', 'up'))
    m.box((10.6, 0.8, 12.6), (14.2, 1.4, 13.2), 'metal:steel', 'guard foot')
    m.g('details')
    for z in (5.6, 7.4, 9.2, 10.8):
        m.bolt(14.0, 1.6, z, 'east', 0.3)
        m.bolt(11.0, 1.6, z, 'west', 0.3)
    m.box((9.2, 0.8, 7.0), (10.6, 1.3, 12.4), 'cable', 'air hose run')
    m.box((9.4, 1.3, 11.6), (10.4, 2.0, 12.4), 'metal:brass', 'hose coupling')
    m.box((3.0, 0.8, 9.0), (3.4, 6.0, 9.4), 'galv', 'sign post')
    m.box((2.2, 6.0, 9.0), (6.4, 8.0, 9.2), 'paint:#f0f0f0', 'warning sign', north='warn:TRAIN|STOP')
    m.box((2.2, 6.0, 9.2), (6.4, 8.0, 9.3), 'galv', 'sign back')
    m.box((4.6, 0.8, 9.6), (8.6, 1.2, 10.2), 'cable:1d1d21', 'control cable')
    m.box((8.6, 0.8, 9.6), (11.0, 1.2, 10.2), 'cable:1d1d21', 'control cable')
    for x in (3.4, 5.0, 6.6, 8.2):
        m.box((x, 0.8, 12.0), (x + 1.0, 1.1, 13.6), 'paint:#6a6a6a', 'ballast')
    m.cyl((12.5, 4.6, 7.0), 0.3, 0.8, 'y', 'metal:steel', 'arm roller pin')
    m.box((11.8, 0.8, 12.6), (13.2, 2.4, 13.4), 'paint:#2b2e33', 'drain trap')
    # panel
    f = m.surface('north', face=2.48, title='TRAIN STOP')
    m.ctl(f, 'key', 'k_iso', X(3.6), 6.5, w=1.0, h=1.0, col='c0a040', label='ISOL', pos=['NORMAL', 'ISOL'])
    m.ctl(f, 'selector', 's_mode', X(5.6), 6.5, w=1.0, h=1.0, col='202020', label='MODE', pos=['SIGNAL', 'SCTS'])
    m.ctl(f, 'selector', 's_kmh', X(7.6), 6.5, w=1.0, h=1.0, col='202020', label='TRAP', pos=['15', '25', '40'])
    lamp(m, f, 'up', X(3.4), 4.7, 'ff3030', 'UP', 0.5)
    lamp(m, f, 'down', X(4.4), 4.7, '30e060', 'DN', 0.5)
    lamp(m, f, 'air', X(5.4), 4.7, '30a0ff', 'AIR', 0.5)
    lamp(m, f, 'isol', X(6.4), 4.7, 'ffb020', 'ISO', 0.5, blink=True)
    lamp(m, f, 'lnk', X(7.4), 4.7, 'f0f0f0', 'LNK', 0.5)
    lamp(m, f, 'nolnk', X(8.2), 4.7, 'ff3030', '', 0.4, blink=True)
    digits(m, f, 'trips', X(4.6), 3.2, 2.8, col='ff4040', fmt='%d TRIP')
    digits(m, f, 'sig', X(6.9), 2.2, 3.4, col='ffd27a', h=0.55)
    push(m, f, 'test', X(7.4), 3.4, 'f0d020', 'TEST', 0.8)
    push(m, f, 'lamptest', X(3.3), 1.8, 'f0f0f0', 'LAMP', 0.6)
    lamp(m, f, 'pwr', X(4.4), 1.8, '30e060', 'PWR', 0.45)
    a = m.surface('north', face=7.0, title='ARM')
    m.ctl(a, 'lever', 'arm', X(12.5), 4.4, w=0.8, h=4.0, col='e8c020', label='ARM', pos=['DOWN', 'UP'])
    m.save(item_gui=0.8)
    facing_states('train_stop')


# ------------------------------------------------------------------------------------------------ 12 balise + LEU
def balise():
    m = Model('balise')
    m.g('sleeper bed')
    m.box((0.6, 0, 8.4), (15.4, 0.6, 15.6), 'paint:#5a4a3a', 'ballast pad')
    for x in (1.0, 5.2, 9.4, 13.4):
        m.box((x, 0.6, 8.6), (x + 1.6, 1.0, 15.4), 'concrete', 'sleeper')
    m.g('eurobalise')
    m.box((2.6, 1.0, 9.6), (13.4, 1.9, 14.4), YEL, 'balise body')
    m.box((2.4, 1.0, 9.4), (13.6, 1.2, 14.6), 'paint:#c89c10', 'balise flange')
    m.box((3.4, 1.9, 10.4), (12.6, 1.95, 13.6), 'label:EUROBALISE', 'balise label', faces=('up',))
    for x in (2.9, 13.1):
        for z in (9.8, 14.2):
            m.bolt(x, 1.2, z, 'up', 0.3)
    for i in range(6):
        m.box((3.6 + i * 1.5, 1.9, 9.8), (4.2 + i * 1.5, 2.0, 10.2), 'paint:#d0a812', 'rib')
    m.box((4.0, 1.0, 9.0), (12.0, 1.6, 9.4), 'metal:steel', 'mounting bracket')
    m.box((4.0, 1.0, 14.6), (12.0, 1.6, 15.0), 'metal:steel', 'mounting bracket')
    m.g('tail cable')
    m.box((7.6, 1.2, 6.6), (8.4, 1.6, 9.6), 'cable:1d1d21', 'balise tail cable')
    m.box((7.6, 0.6, 6.4), (8.4, 1.6, 6.8), 'cable:1d1d21', 'cable bend')
    m.g('LEU cabinet')
    m.box((2.0, 0, 1.4), (14.0, 0.6, 7.0), 'concrete', 'LEU plinth')
    m.box((2.6, 0.6, 1.6), (13.4, 9.0, 6.6), 'ral7032', 'LEU cabinet')
    m.box((2.4, 9.0, 1.4), (13.6, 9.5, 6.8), 'ral7032', 'rain hood')
    m.box((2.8, 0.8, 1.5), (13.2, 8.8, 1.6), PANEL, 'LEU front panel')
    m.nameplate(3.4, 8.0, 12.6, 8.7, 1.5, 'LEU BALISE ENCODER')
    for x in (3.0, 12.9):
        for y in (1.0, 8.6):
            m.bolt(x, y, 1.5, 'north', 0.28)
    for y in (2.0, 6.0):
        m.box((13.4, y, 2.0), (13.7, y + 1.2, 2.6), 'metal:steel', 'hinge')
    m.box((2.6, 0.6, 6.6), (13.4, 1.4, 6.8), 'louvre', 'rear vent')
    for x in (4.0, 6.0, 8.0, 10.0, 12.0):
        m.gland(x, 0.6, 4.0, 'down', r=0.25)
    m.terminal_12v(13.4, 2.4, 4.0, 'east')
    m.box((3.0, 1.0, 1.42), (5.0, 2.0, 1.5), 'label:12V DC', 'supply label', faces=('north',))
    m.g('track details')
    for x in (1.4, 5.6, 9.8, 13.8):
        for z in (9.2, 14.8):
            m.bolt(x + 0.4, 1.0, z, 'up', 0.3)
    for (x, z) in ((0.8, 8.8), (4.6, 15.0), (8.8, 8.7), (12.8, 15.0), (15.0, 11.0), (0.8, 13.0), (7.2, 15.1), (11.2, 8.8)):
        m.box((x, 0.6, z), (x + 0.6, 0.9, z + 0.5), 'paint:#7a7a72', 'ballast stone')
    m.box((1.0, 0, 7.2), (2.0, 1.4, 8.0), 'concrete', 'cable route marker')
    m.box((1.1, 1.0, 7.18), (1.9, 1.3, 7.2), 'label:BG', 'marker label', faces=('north',))
    m.box((14.0, 0, 7.2), (15.0, 1.4, 8.0), 'concrete', 'cable route marker')
    m.box((6.6, 0.6, 6.8), (9.4, 0.8, 9.0), 'paint:#3a3d40', 'cable duct cover')
    f = m.surface('north', face=1.48, title='LEU')
    digits(m, f, 'bg', X(4.4), 7.2, 2.6, col='ffb020', h=0.7)
    digits(m, f, 'tel', X(9.4), 7.2, 7.0, col='7cf29a', h=0.7)
    digits(m, f, 'kmh', X(4.4), 6.2, 2.6, col='ffd27a', h=0.6)
    digits(m, f, 'rd', X(9.4), 6.2, 7.0, col='5bcefa', h=0.6)
    m.ctl(f, 'key', 'k_prog', X(3.8), 4.5, w=1.0, h=1.0, col='c0a040', label='PROG', pos=['RUN', 'PROG'])
    push(m, f, 'spdup', X(5.6), 5.0, '2050d0', 'SPD+', 0.7)
    push(m, f, 'spddn', X(5.6), 3.9, '2050d0', 'SPD-', 0.7)
    m.ctl(f, 'selector', 's_off', X(7.4), 4.5, w=1.0, h=1.0, col='202020', label='OFFSET', pos=['0', '50', '100', '200', '400'])
    m.ctl(f, 'selector', 's_mode', X(9.4), 4.5, w=1.0, h=1.0, col='202020', label='MODE', pos=['FIXED', 'SWITCHED'])
    m.ctl(f, 'selector', 's_dir', X(11.4), 4.5, w=1.0, h=1.0, col='202020', label='DIR', pos=['BOTH', 'FACING', 'TRAIL'])
    lamp(m, f, 'pwr', X(3.6), 2.4, '30e060', 'PWR', 0.5)
    lamp(m, f, 'tx', X(4.8), 2.4, '30a0ff', 'TX', 0.5)
    lamp(m, f, 'fault', X(6.0), 2.4, 'ff3030', 'FLT', 0.5, blink=True)
    lamp(m, f, 'prog', X(7.2), 2.4, 'ffb020', 'PRG', 0.5, blink=True)
    lamp(m, f, 'sigstop', X(8.4), 2.4, 'ff3030', 'SIG', 0.5)
    digits(m, f, 'passes', X(10.6), 2.4, 2.8, col='ffb020', h=0.6, fmt='%d PASS')
    push(m, f, 'lamptest', X(12.4), 2.4, 'f0f0f0', 'LAMP', 0.55)
    m.save(item_gui=0.7)
    facing_states('balise')


# ------------------------------------------------------------------------------------------------ 20 safety supervisor
def supervisor():
    m = Model('safety_supervisor')
    m.g('rack frame')
    m.box((1.0, 0, 1.6), (15.0, 0.8, 15.0), 'paint:#141416', 'rack plinth')
    for x in (1.0, 14.2):
        m.box((x, 0.8, 1.6), (x + 0.8, 15.4, 15.0), 'paint:#1c1d20', 'rack upright')
    m.box((1.0, 15.4, 1.6), (15.0, 16.0, 15.0), 'paint:#1c1d20', 'rack top')
    m.box((1.8, 0.8, 14.2), (14.2, 15.4, 15.0), 'mesh', 'rear mesh door')
    for y in range(1, 15):
        m.box((1.7, y, 1.5), (1.95, y + 0.25, 1.6), 'metal:steel', 'rack hole')
        m.box((14.05, y, 1.5), (14.3, y + 0.25, 1.6), 'metal:steel', 'rack hole')
    m.nameplate(4.0, 15.45, 12.0, 15.95, 1.55, 'SAFETY SUPERVISOR 2OO2')
    m.g('channel A + B processors')
    for (y0, ch) in ((10.6, 'A'), (7.4, 'B')):
        m.box((1.8, y0, 2.0), (14.2, y0 + 2.8, 13.8), 'paint:#2b2f33', 'processor chassis ' + ch)
        m.box((1.6, y0, 1.8), (14.4, y0 + 2.8, 2.0), 'paint:#3a3f44', 'processor front ' + ch)
        m.box((2.2, y0 + 2.0, 1.72), (4.2, y0 + 2.6, 1.8), 'label:CH ' + ch, 'channel label', faces=('north',))
        for i in range(8):
            m.box((9.0 + i * 0.6, y0 + 2.2, 1.74), (9.4 + i * 0.6, y0 + 2.5, 1.8), 'led:30e060' if i % 3 else 'led:ffb020', 'status LED')
        for x in (1.6, 14.0):
            m.box((x, y0 + 0.3, 1.3), (x + 0.4, y0 + 2.5, 1.8), 'metal:alu', 'rack handle')
        m.box((10.0, y0 + 0.3, 1.74), (13.8, y0 + 1.6, 1.8), 'louvre', 'processor vent')
    m.g('cross-compare + watchdog')
    m.box((1.8, 5.0, 2.0), (14.2, 7.2, 13.8), 'paint:#22252a', 'cross-compare unit')
    m.box((1.6, 5.0, 1.8), (14.4, 7.2, 2.0), 'paint:#33373c', 'cross-compare front')
    m.box((10.2, 5.3, 1.74), (13.8, 6.9, 1.8), 'label:XCMP|WATCHDOG', 'unit label', faces=('north',))
    m.g('power supplies')
    m.box((1.8, 1.0, 2.0), (14.2, 4.8, 13.8), 'paint:#1c1d20', 'PSU shelf')
    m.box((1.6, 1.0, 1.8), (14.4, 4.8, 2.0), 'paint:#26282c', 'PSU front')
    for i in range(3):
        x0 = 2.2 + i * 4.0
        m.box((x0, 1.3, 1.6), (x0 + 3.6, 4.5, 1.8), 'paint:#30343a', 'PSU module')
        m.box((x0 + 0.3, 3.6, 1.55), (x0 + 1.6, 4.2, 1.6), 'louvre', 'PSU fan grille')
        m.box((x0 + 2.2, 1.6, 1.55), (x0 + 3.3, 2.2, 1.6), 'metal:alu', 'PSU latch')
    m.box((2.4, 1.32, 1.52), (5.6, 1.9, 1.56), 'label:230V AC', 'PSU A label', faces=('north',))
    m.g('cabling')
    for y in (3.0, 6.0, 9.0, 12.0):
        m.box((12.8, y, 13.8), (13.4, y + 2.4, 14.2), 'cable:1d1d21', 'loom')
    m.box((2.0, 0.8, 13.4), (14.0, 1.2, 14.2), 'cable', 'cable tray')
    m.g('mains inlet')
    m.box((15.0, 2.0, 5.0), (15.8, 5.2, 9.0), 'ral7016', 'mains inlet box', east='danger:MAINS|230V')
    for z in (5.3, 8.7):
        for y in (2.3, 4.9):
            m.bolt(15.8, y, z, 'east', 0.3)
    m.gland(15.4, 2.0, 7.0, 'down', r=0.35)
    m.terminal_12v(15.0, 10.0, 7.0, 'east')
    f = m.surface('north', face=1.5, title='SUPERVISOR')
    # top strip on the channel A front: lamps
    for (cid, u, col, lab, bl) in (('mains', 3.0, '30e060', 'MAINS', False), ('pwr', 4.2, '30e060', '12V', False), ('ok', 5.4, '30e060', 'HLTHY', False),
                                   ('cha', 6.6, '30a0ff', 'CH A', False), ('chb', 7.8, '30a0ff', 'CH B', False)):
        lamp(m, f, cid, X(u), 12.0, col, lab, 0.55, blink=bl)
    digits(m, f, 'mode', X(5.4), 10.9, 6.0, col='ffd27a', h=0.6)
    for (cid, u, col, lab, bl) in (('wd', 3.0, '30e060', 'WDOG', True), ('xc', 4.2, '30e060', 'XC OK', False), ('xcf', 5.4, 'ffb020', 'XC !', True),
                                   ('vio', 6.6, 'ff3030', 'VIOL', True), ('enf', 7.8, 'ff8020', 'ENF', False), ('held', 9.0, 'ff3030', 'HELD', True)):
        lamp(m, f, cid, X(u), 8.8, col, lab, 0.55, blink=bl)
    digits(m, f, 'n', X(11.0), 8.6, 2.6, col='7cf29a', h=0.6, fmt='%d TRN')
    digits(m, f, 'last', X(8.0), 6.6, 11.0, col='ff8060', h=0.6)
    digits(m, f, 'iv', X(3.6), 5.6, 2.6, col='ffb020', h=0.6, fmt='%d INTV')
    m.ctl(f, 'gauge', 'margin', X(7.8), 5.9, w=1.6, h=1.6, col='f2f0e6', label='V/ENV %', max=150)
    m.ctl(f, 'gauge', 'cyc', X(10.0), 5.9, w=1.6, h=1.6, col='f2f0e6', label='CYCLE', max=100)
    m.ctl(f, 'key', 'k_mode', X(3.2), 3.2, w=1.2, h=1.2, col='c0a040', label='MODE', pos=['MONITOR', 'ENFORCE'])
    m.ctl(f, 'selector', 's_cov', X(5.6), 3.2, w=1.1, h=1.1, col='202020', label='COVER', pos=['256', '1024', 'ALL'])
    push(m, f, 'rst', X(8.0), 3.2, 'd02020', 'RESET', 1.0)
    push(m, f, 'ack', X(10.0), 3.2, 'f0d020', 'ACK', 0.9)
    push(m, f, 'lamptest', X(12.0), 3.2, 'f0f0f0', 'LAMP', 0.8)
    m.save(item_gui=0.55)
    facing_states('safety_supervisor')


def ing(v):
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


RECIPES = {
    'train_stop': (['  L', 'PRI', 'SSS'], {'L': 'minecraft:lever', 'P': 'minecraft:piston', 'R': 'minecraft:redstone', 'I': 'minecraft:iron_ingot', 'S': ('minecraft:stone', 0)}),
    'balise': (['YCY', 'IRI', 'ICI'], {'Y': ('minecraft:dye', 11), 'C': 'minecraft:comparator', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'safety_supervisor': (['ICI', 'ROR', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:comparator', 'R': 'minecraft:redstone', 'O': 'minecraft:observer'}),
}

LANG = {
    'tile.irextras.train_stop.name': 'Train-Stop Trip Cock',
    'tile.irextras.balise.name': 'Balise Group + LEU',
    'tile.irextras.safety_supervisor.name': 'Independent Safety Supervisor',
}

if __name__ == '__main__':
    for fn in (train_stop, balise, supervisor):
        fn()
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# signalling batch 3 part A (#11 #12 #20)\n' + '\n'.join(add) + '\n')
    for n in ('train_stop', 'balise', 'safety_supervisor'):
        print(n, len(json.load(open(os.path.join(ASSETS, 'models/block', n + '.json')))['elements']), 'boxes')
