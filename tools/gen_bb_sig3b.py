#!/usr/bin/env python3
"""Signalling batch 3, part B (her ChatGPT batch-3 list #14 #16 #17 #18): Signal Battery Cabinet, Signal Sighting
Checker, Radio Repeater, Signal Maintenance Isolation Box. Models face north; controls by u = 16 - x. Every lamp,
gauge, display, button, key and selector is a live control the Java tile reads or works (safety/TileSignalBattery,
TileSightingChecker, TileRadioRepeater, TileIsolationBox). The 12 V terminal boxes are wire terminals, not buttons.
Also writes blockstates, item models, recipes and lang lines."""
import json, os
from bbkit import Model, ASSETS

X = lambda x: 16 - x
PANEL = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
GREY = 'ral7035'


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label='', s=0.6, blink=False):
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **({'blink': True} if blink else {}))


def push(m, sf, cid, u, v, col, label, s=0.9):
    m.ctl(sf, 'push', cid, u, v, w=s, h=s, col=col, label=label)


def digits(m, sf, cid, u, v, w, col='7cf29a', h=0.7, fmt='%s'):
    m.ctl(sf, 'digits', cid, u, v, w=w, h=h, col=col, label='', fmt=fmt)


# ------------------------------------------------------------------------------------------------ 14 battery cabinet
def battery():
    m = Model('signal_battery')
    m.g('plinth')
    m.box((0.6, 0, 1.8), (15.4, 0.8, 15.6), 'concrete', 'concrete plinth')
    m.box((0.6, 0.8, 1.6), (15.4, 1.1, 15.6), 'haz', 'kick strip', faces=('north',))
    for x in (1.2, 14.8):
        for z in (2.4, 15.0):
            m.bolt(x, 0.8, z, 'up', 0.45)
    m.g('cabinet')
    m.box((1.0, 1.1, 2.4), (15.0, 23.0, 15.0), 'ral7032', 'battery cabinet')
    m.box((0.7, 23.0, 2.0), (15.3, 23.8, 15.4), 'ral7032', 'rain hood')
    m.box((0.7, 23.8, 3.4), (15.3, 24.3, 14.6), 'ral7032', 'roof ridge')
    for z in (5.0, 9.0, 13.0):
        m.box((0.7, 3.0, z), (1.0, 21.0, z + 0.6), 'ral7032', 'side stiffener')
        m.box((15.0, 3.0, z), (15.3, 21.0, z + 0.6), 'ral7032', 'side stiffener')
    for y in (4.0, 9.0, 14.0):                                  # side vents (batteries gas when charging)
        m.box((14.95, y, 6.0), (15.05, y + 2.4, 12.0), 'louvre', 'side vent', faces=('east',))
        m.box((0.95, y, 6.0), (1.05, y + 2.4, 12.0), 'louvre', 'side vent', faces=('west',))
    m.box((6.0, 23.8, 7.0), (10.0, 24.9, 11.0), 'paint:#8a8678', 'roof vent cowl')
    m.box((6.4, 24.9, 7.4), (9.6, 25.1, 10.6), 'mesh', 'roof vent mesh')
    m.g('door')
    m.box((1.3, 1.4, 2.15), (14.7, 22.6, 2.4), 'ral7032', 'door leaf')
    for y in (2.4, 11.0, 20.0):
        m.box((0.9, y, 2.1), (1.3, y + 1.6, 2.7), 'metal:steel', 'hinge')
    m.box((14.2, 11.0, 1.7), (14.6, 14.0, 2.15), 'metal:steel', 'door handle')
    m.box((14.1, 14.4, 2.0), (14.7, 15.0, 2.15), 'metal:brass', 'lock barrel')
    m.g('control panel')
    m.box((1.8, 6.6, 2.0), (14.2, 22.2, 2.15), PANEL, 'control panel')
    m.nameplate(3.0, 21.2, 9.8, 22.0, 2.0, 'SIGNAL BATTERY')
    m.box((10.3, 21.3, 1.96), (13.9, 21.9, 2.0), 'label:12V 230VAC', 'rating label', faces=('north',))
    for x in (2.1, 13.9):
        for y in (6.9, 21.9):
            m.bolt(x, y, 2.0, 'north', 0.3)
    m.box((2.2, 19.4, 1.98), (14.0, 20.6, 2.0), DARK, 'lamp strip')
    for i, t in enumerate(('PWR', 'MAINS', 'CHG', 'BATT', 'LOW', 'LVD', 'FLT', 'TEST')):
        m.box((X(13.5 - i * 1.55) - 0.6, 18.8, 1.97), (X(13.5 - i * 1.55) + 0.6, 19.3, 2.0), 'label:' + t, 'lamp legend', faces=('north',))
    for gx in (12.0, 8.0, 4.0):                                 # meter bezels
        m.box((X(gx) - 1.8, 13.8, 1.9), (X(gx) + 1.8, 17.6, 2.0), 'paint:#1a1a1a', 'meter bezel')
    for (gx, t) in ((12.0, 'CHARGE %'), (8.0, 'HEALTH %'), (4.0, 'LOAD W')):
        m.box((X(gx) - 1.6, 13.3, 1.97), (X(gx) + 1.6, 13.75, 2.0), 'label:' + t, 'meter legend', faces=('north',))
    m.box((1.9, 9.0, 1.96), (14.1, 12.8, 2.0), 'paint:#101418', 'display bezel')
    m.g('battery window')
    m.box((1.8, 1.6, 2.0), (14.2, 6.2, 2.1), 'glass', 'battery inspection window', faces=('north',))
    m.box((1.6, 1.4, 1.95), (14.4, 1.6, 2.15), 'metal:alu', 'window frame')
    m.box((1.6, 6.2, 1.95), (14.4, 6.4, 2.15), 'metal:alu', 'window frame')
    m.g('battery strings')
    for row, y in enumerate((1.6, 4.0)):
        for i in range(6):                                     # 2 strings of 6 x 2 V cells (12 V)
            x0 = 2.2 + i * 2.0
            m.box((x0, y, 3.0), (x0 + 1.8, y + 2.0, 7.0), 'battery', 'lead-acid block')
            m.box((x0 + 0.3, y + 2.0, 3.6), (x0 + 0.7, y + 2.2, 4.0), 'paint:ral3000', '+ post')
            m.box((x0 + 1.1, y + 2.0, 3.6), (x0 + 1.5, y + 2.2, 4.0), 'paint:ral9005', '- post')
            if i < 5:
                m.box((x0 + 1.1, y + 2.15, 3.7), (x0 + 2.7, y + 2.3, 3.9), 'metal:copper', 'intercell link')
        m.box((2.0, y - 0.2, 2.8), (14.2, y, 7.4), 'paint:#3a3d40', 'battery shelf')
    m.g('charger')
    m.box((2.0, 8.0, 9.0), (14.0, 14.0, 14.6), 'paint:#2b2f33', 'charger rectifier')
    m.louvres(3.0, 13.0, 8.6, 13.4, 9.0, n=5)
    m.box((2.0, 15.0, 9.0), (8.0, 21.0, 14.6), 'paint:#33373c', 'distribution board')
    for i in range(6):
        m.box((2.6 + i * 0.9, 18.0, 8.8), (3.3 + i * 0.9, 19.6, 9.0), 'paint:#f0f0f0', 'mcb')
    m.box((9.0, 15.0, 9.0), (14.0, 21.0, 14.6), 'paint:#33373c', 'load bank (test)')
    m.box((9.4, 15.4, 8.9), (13.6, 20.6, 9.0), 'fins', 'load bank resistors')
    m.g('mains inlet')
    m.box((15.0, 2.0, 10.0), (16.0, 5.0, 13.6), GREY, 'AC inlet box', east='label:MAINS')
    m.box((15.0, 5.2, 10.0), (15.1, 6.2, 13.6), 'plate:230V AC', 'mains plate', faces=('east',))
    m.gland(16.0, 3.5, 11.8, 'east', r=0.45, cable='cable:202020')
    m.terminal_12v(1.0, 2.0, 11.0, 'west')
    m.gland(4.0, 1.1, 12.0, 'down', r=0.35)
    m.gland(12.0, 1.1, 12.0, 'down', r=0.35)
    m.box((2.0, 22.6, 2.1), (4.4, 23.0, 2.4), 'danger:ACID', 'acid warning', faces=('north',))
    f = m.surface('north', face=1.94, title='SIGNAL BATTERY')
    for i, (cid, col, bl) in enumerate((('pwr', '30e060', False), ('mainsl', '30e060', False), ('chg', 'ffd040', False), ('onbat', 'ffb020', False),
                                        ('low', 'ff3030', True), ('lvd', 'ff3030', False), ('fault', 'ff3030', True), ('testl', '30a0ff', True))):
        lamp(m, f, cid, 13.5 - i * 1.55, 20.0, col, '', 0.6, bl)
    m.ctl(f, 'gauge', 'soc', 12.0, 15.7, w=3.2, h=3.2, col='f2f0e6', label='SOC', max=100)
    m.ctl(f, 'gauge', 'health', 8.0, 15.7, w=3.2, h=3.2, col='f2f0e6', label='HEALTH', max=100)
    m.ctl(f, 'gauge', 'load', 4.0, 15.7, w=3.2, h=3.2, col='f2f0e6', label='WATTS', max=300)
    digits(m, f, 'state', 8.0, 12.2, 11.6, col='ffd040', h=0.75)
    digits(m, f, 'volts', 12.4, 11.1, 3.2, col='7cf29a')
    digits(m, f, 'run', 8.2, 11.1, 4.2, col='7cf29a')
    digits(m, f, 'cap', 3.8, 11.1, 4.0, col='a0d8ff', h=0.6)
    digits(m, f, 'cov', 12.0, 9.8, 4.0, col='a0d8ff', h=0.6)
    digits(m, f, 'fedn', 7.6, 9.8, 2.6, col='ffb020', h=0.6, fmt='%d FED')
    digits(m, f, 'covn', 4.4, 9.8, 2.6, col='a0a0a0', h=0.6, fmt='%d COV')
    m.ctl(f, 'selector', 's_ah', 13.0, 7.6, w=1.2, h=1.2, col='202020', label='STRING', pos=['24AH', '65AH', '100AH', '200AH'])
    m.ctl(f, 'selector', 's_cov', 10.6, 7.6, w=1.2, h=1.2, col='202020', label='COVER', pos=['LINKED', '16M', '32M'])
    push(m, f, 'btest', 8.4, 7.6, '2050d0', 'TEST')
    push(m, f, 'bstop', 6.9, 7.6, 'd02020', 'STOP')
    push(m, f, 'renew', 5.4, 7.6, 'f0d020', 'NEW')
    push(m, f, 'test', 3.9, 7.6, 'f0f0f0', 'LAMP', 0.7)
    m.ctl(f, 'key', 'k_door', 2.4, 7.6, w=1.0, h=1.0, col='c0a040', label='DOOR', pos=['LOCK', 'OPEN'])
    m.ctl(f, 'zone', 'link', 9.6, 21.6, w=6.8, h=0.8, col='000000', label='')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 14.0, 3.0, 21.0, 1.0, side='south', col='ral7032', title='SIGNAL BATTERY|CABINET', volts='230V AC 12V DC')
    m.save(item_gui=0.5)
    facing_states('signal_battery')


# ------------------------------------------------------------------------------------------------ 16 sighting checker
def checker():
    m = Model('sighting_checker')
    m.g('tripod')
    for (x, z, ax, az) in ((4.0, 4.0, 1, 1), (12.0, 4.0, -1, 1), (8.0, 13.0, 0, -1)):
        for k in range(6):                                     # each leg in short slanted pieces
            t0, t1 = k / 6.0, (k + 1) / 6.0
            x0, z0 = x + ax * 3.4 * t0, z + az * 3.4 * t0
            x1, z1 = x + ax * 3.4 * t1, z + az * 3.4 * t1
            y0, y1 = 0.3 + 10.2 * t0, 0.3 + 10.2 * t1
            m.box((min(x0, x1) - 0.3, y0, min(z0, z1) - 0.3), (max(x0, x1) + 0.3, y1, max(z0, z1) + 0.3), 'metal:alu', 'tripod leg')
        m.box((x - 0.5, 0, z - 0.5), (x + 0.5, 0.3, z + 0.5), 'rubber', 'leg foot')
        m.box((x - 0.15, -0.0, z - 0.15), (x + 0.15, 0.3, z + 0.15), 'metal:steel', 'leg spike')
    m.box((6.6, 10.4, 6.6), (9.4, 11.2, 9.4), 'paint:#2b2e33', 'tripod head')
    m.box((7.4, 3.0, 7.4), (8.6, 10.4, 8.6), 'metal:alu', 'centre column')
    m.box((7.0, 4.0, 7.0), (9.0, 4.4, 9.0), 'paint:#2b2e33', 'column clamp')
    m.box((6.0, 7.0, 7.7), (10.0, 7.4, 8.3), 'metal:alu', 'leg spreader')
    for (x, z) in ((5.0, 5.0), (11.0, 5.0), (8.0, 11.5)):          # leg flip-locks and spreader chains
        m.box((x - 0.45, 5.0, z - 0.45), (x + 0.45, 5.6, z + 0.45), 'paint:#1a1a1a', 'leg lock')
        m.box((x - 0.2, 5.6, z - 0.2), (x + 0.2, 5.9, z + 0.2), 'paint:ral2004', 'lock lever')
        m.box((min(x, 8.0) - 0.08, 3.6, min(z, 8.0) - 0.08), (max(x, 8.0) + 0.08, 3.7, max(z, 8.0) + 0.08), 'metal:steel', 'spreader chain')
    m.box((7.0, 9.6, 7.0), (9.0, 10.4, 9.0), 'paint:#2b2e33', 'levelling base')
    m.box((7.2, 10.15, 6.8), (7.8, 10.35, 7.0), 'glass', 'bubble level', faces=('north',))
    for a in (6.8, 9.2):
        m.cyl((a, 10.0, 7.0), 0.25, 0.3, 'z', 'paint:#1a1a1a', 'levelling screw')
    m.box((9.6, 2.0, 9.4), (11.4, 4.2, 10.4), 'paint:#3a3d42', 'battery pack')
    m.box((9.8, 4.2, 9.6), (11.2, 4.4, 10.2), 'paint:#1a1a1a', 'pack handle')
    m.box((9.9, 3.0, 9.35), (11.1, 3.4, 9.4), 'label:12V', 'pack label', faces=('north',))
    m.g('pan head')
    m.cyl((8.0, 11.2, 8.0), 1.2, 0.8, 'y', 'paint:#1a1a1a', 'pan base')
    m.box((7.4, 12.0, 7.0), (8.6, 12.6, 9.0), 'paint:#1a1a1a', 'tilt yoke')
    m.box((9.0, 11.6, 7.8), (11.8, 12.0, 8.2), 'paint:#1a1a1a', 'pan handle')
    m.box((11.8, 11.4, 7.6), (12.6, 12.2, 8.4), 'rubber', 'handle grip')
    m.g('survey camera')
    m.box((5.0, 12.6, 6.4), (11.0, 15.4, 11.6), 'paint:#e8e6de', 'camera body')
    m.box((5.4, 15.4, 7.0), (10.6, 15.8, 11.0), 'paint:#d0cec6', 'camera top')
    m.cyl((8.0, 14.0, 4.4), 1.4, 2.0, 'z', 'paint:#1a1a1a', 'telephoto lens')
    m.cyl((8.0, 14.0, 3.9), 1.55, 0.5, 'z', 'rubber', 'lens hood')
    m.cyl((8.0, 14.0, 3.85), 1.2, 0.05, 'z', 'glass', 'lens glass')
    m.box((5.6, 15.8, 8.0), (7.0, 16.0, 10.0), 'paint:#1a1a1a', 'gps puck')
    m.box((9.0, 15.8, 7.4), (10.4, 16.4, 9.6), 'paint:#2b2e33', 'laser rangefinder')
    m.box((9.3, 15.9, 7.3), (10.1, 16.3, 7.4), 'lamp:ff3030', 'laser aperture', faces=('north',))
    m.box((4.4, 13.0, 7.6), (5.0, 15.0, 10.6), 'paint:#2b2e33', 'battery grip')
    m.nameplate(5.4, 12.8, 10.6, 13.4, 6.4, 'SIGHTING')
    m.g('screen arm')
    m.box((10.9, 13.4, 10.6), (11.4, 13.8, 12.4), 'metal:alu', 'screen arm')
    m.box((3.0, 9.0, 12.4), (13.0, 15.6, 13.2), 'paint:#2b2e33', 'field screen housing')
    m.box((3.4, 10.2, 12.32), (12.6, 15.2, 12.4), 'screen:08140c', 'field screen', faces=('north',))
    m.box((3.0, 15.6, 12.2), (13.0, 16.0, 13.4), 'paint:#1a1a1a', 'sun hood')
    m.box((3.0, 8.4, 12.2), (13.0, 9.0, 13.2), 'paint:#3a3d42', 'button strip')
    for x in (3.4, 12.6):
        m.bolt(x, 9.4, 12.4, 'north', 0.25)
    m.box((13.0, 10.0, 12.4), (13.8, 14.0, 13.0), 'paint:#3a3d42', 'screen battery')
    for x in (3.6, 5.2, 6.8, 8.4, 10.0, 11.6):                    # rubber button boots behind the keys
        m.box((x - 0.4, 8.45, 12.15), (x + 0.4, 8.95, 12.2), 'rubber', 'button boot')
    m.box((2.6, 10.0, 12.6), (3.0, 15.0, 13.0), 'rubber', 'screen bumper')
    m.box((13.0, 14.4, 12.6), (13.4, 15.6, 13.0), 'rubber', 'screen bumper')
    m.box((6.0, 16.0, 12.6), (10.0, 16.3, 13.0), 'metal:alu', 'carry handle')
    m.terminal_12v(8.0, 0.8, 13.6, 'south')
    m.box((7.6, 0.2, 13.6), (8.4, 0.8, 15.6), 'cable:1d1d21', '12 V lead')
    f = m.surface('north', face=12.28, title='SIGHTING')
    digits(m, f, 'sig', 8.0, 14.7, 9.0, col='ffd040', h=0.7)
    digits(m, f, 'sight', 11.0, 13.7, 3.0, col='7cf29a', h=0.7)
    digits(m, f, 'need', 7.4, 13.7, 4.2, col='a0d8ff', h=0.6)
    digits(m, f, 'short', 3.8, 13.7, 3.2, col='ff6060', h=0.6)
    digits(m, f, 'obs', 8.0, 12.7, 9.0, col='ffb020', h=0.55)
    digits(m, f, 'ban', 8.0, 11.85, 9.0, col='7cf29a', h=0.55)
    digits(m, f, 'cnt', 11.0, 10.85, 3.0, col='ff8080', h=0.5)
    m.ctl(f, 'gauge', 'vis', 6.5, 10.9, w=1.4, h=1.4, col='f2f0e6', label='VIS', max=100)
    lamp(m, f, 'pwr', 4.2, 10.6, '30e060', '', 0.4)
    lamp(m, f, 'ok', 3.6, 11.2, '30e060', '', 0.4)
    lamp(m, f, 'short', 4.8, 11.2, 'ff3030', '', 0.4, blink=True)
    lamp(m, f, 'showl', 3.6, 10.6, 'f0f0f0', '', 0.4, blink=True)
    for (cid, u, col, lab) in (('survey', 12.2, '2050d0', 'SURVEY'), ('show', 10.6, '30c050', 'SHOW'), ('prev', 9.0, '606060', 'PREV'),
                               ('next', 7.6, '606060', 'NEXT'), ('test', 6.2, 'f0f0f0', 'LAMP')):
        push(m, f, cid, u, 8.7, col, lab, 0.55)
    m.ctl(f, 'selector', 's_kmh', 4.2, 8.7, w=0.7, h=0.7, col='202020', label='KMH', pos=['LINE', '40', '60', '80', '100', '125', '160'])
    m.save(item_gui=0.6)
    facing_states('sighting_checker')


# ------------------------------------------------------------------------------------------------ 17 radio repeater
def repeater():
    m = Model('radio_repeater')
    m.g('foundation')
    m.box((4.0, 0, 4.0), (12.0, 0.8, 12.0), 'concrete', 'foundation')
    m.box((6.0, 0.8, 6.0), (10.0, 1.4, 10.0), 'galv', 'base flange')
    for (x, z) in ((6.4, 6.4), (9.6, 6.4), (6.4, 9.6), (9.6, 9.6)):
        m.bolt(x, 1.4, z, 'up', 0.4)
    m.g('pole')
    for i in range(5):                                         # tapered tubular pole in sections
        r = 1.0 - i * 0.12
        m.box((8 - r, 1.4 + i * 4.6, 8 - r), (8 + r, 1.4 + (i + 1) * 4.6, 8 + r), 'galv', 'pole section')
        m.box((8 - r - 0.15, 1.4 + (i + 1) * 4.6 - 0.3, 8 - r - 0.15), (8 + r + 0.15, 1.4 + (i + 1) * 4.6, 8 + r + 0.15), 'galv', 'section collar')
    for y in range(4, 24, 2):
        m.box((8.5, y, 8.6), (9.3, y + 0.25, 8.9), 'metal:steel', 'climbing peg')
    m.box((7.4, 2.0, 8.9), (8.6, 24.4, 9.3), 'cable:1d1d21', 'feeder cables')
    m.g('donor yagi')
    m.box((7.6, 25.4, 7.6), (8.4, 26.0, 14.0), 'galv', 'donor boom')
    for k in range(7):                                         # directors pointing back at the donor mast
        z = 8.4 + k * 0.9
        w = 2.2 - k * 0.15
        m.box((8 - w, 25.55, z), (8 + w, 25.85, z + 0.18), 'metal:alu', 'yagi element')
    m.box((7.2, 24.8, 7.2), (8.8, 25.4, 8.8), 'paint:#2a2d30', 'yagi clamp')
    m.box((7.7, 26.0, 13.2), (8.3, 26.1, 14.0), 'label:DONOR', 'donor label', faces=('up',))
    m.g('service antenna')
    m.box((7.7, 26.0, 7.7), (8.3, 31.0, 8.3), 'paint:#e8e6de', 'omni radome')
    m.box((7.5, 31.0, 7.5), (8.5, 31.4, 8.5), 'paint:#e8e6de', 'radome cap')
    m.box((7.3, 24.4, 7.3), (8.7, 25.0, 8.7), 'galv', 'antenna mount')
    m.box((7.85, 31.4, 7.85), (8.15, 32.0, 8.15), 'paint:ral3000', 'aviation marker')
    m.g('repeater cabinet')
    m.box((4.0, 8.0, 3.4), (12.0, 18.0, 7.0), GREY, 'repeater cabinet')
    m.box((3.8, 18.0, 3.2), (12.2, 18.6, 7.2), GREY, 'cabinet hood')
    m.box((7.2, 8.0, 7.0), (8.8, 17.0, 7.4), 'galv', 'pole bracket')
    for y in (9.0, 16.4):
        m.box((5.0, y, 7.0), (11.0, y + 0.6, 7.4), 'galv', 'band clamp')
    m.box((4.3, 8.4, 3.2), (11.7, 17.6, 3.4), PANEL, 'cabinet front')
    m.nameplate(4.8, 16.7, 11.2, 17.4, 3.2, 'REPEATER')
    m.louvres(4.6, 7.0, 8.8, 10.4, 3.2, n=3)
    m.louvres(9.0, 11.4, 8.8, 10.4, 3.2, n=3)
    for x in (4.6, 11.4):
        for y in (8.7, 17.3):
            m.bolt(x, y, 3.2, 'north', 0.22)
    m.box((4.6, 10.8, 3.15), (11.4, 14.6, 3.2), 'paint:#101418', 'display bezel')
    m.gland(6.0, 8.0, 5.2, 'down', r=0.3)
    m.gland(10.0, 8.0, 5.2, 'down', r=0.3)
    m.box((5.8, 1.4, 4.9), (6.2, 7.4, 5.5), 'cable:1d1d21', 'cable down')
    m.terminal_12v(12.0, 9.0, 5.2, 'east')
    m.box((4.2, 18.6, 4.6), (5.0, 21.0, 5.4), 'paint:#2a2d30', 'gps antenna stalk')
    m.cyl((4.6, 21.0, 5.0), 0.6, 0.4, 'y', 'paint:#e8e6de', 'gps puck')
    f = m.surface('north', face=3.14, title='REPEATER')
    digits(m, f, 'cell', 8.0, 14.0, 6.2, col='ffd040', h=0.6)
    digits(m, f, 'donor', 8.0, 13.1, 6.2, col='7cf29a', h=0.6)
    digits(m, f, 'rssi', 9.6, 12.2, 3.0, col='a0d8ff', h=0.55)
    digits(m, f, 'links', 6.4, 12.2, 2.8, col='ffb020', h=0.55, fmt='%d TRN')
    m.ctl(f, 'gauge', 'dbar', 9.6, 11.2, w=1.2, h=1.2, col='f2f0e6', label='DONOR', max=100)
    lamp(m, f, 'pwr', 6.8, 11.4, '30e060', '', 0.4)
    lamp(m, f, 'tx', 6.0, 11.4, '30a0ff', '', 0.4)
    lamp(m, f, 'dlink', 5.2, 11.4, '30e060', '', 0.4)
    lamp(m, f, 'fault', 6.0, 10.9, 'ff3030', '', 0.4, blink=True)
    m.ctl(f, 'selector', 's_power', 11.0, 9.6, w=0.9, h=0.9, col='202020', label='OUT', pos=['0.5W', '2W', '10W'])
    push(m, f, 'test', 8.0, 9.6, 'f0f0f0', 'LAMP', 0.6)
    m.save(item_gui=0.35)
    facing_states('radio_repeater')


# ------------------------------------------------------------------------------------------------ 18 isolation box
def isolation():
    m = Model('signal_isolation')
    m.g('backboard')
    m.box((1.4, 0.0, 15.2), (14.6, 16.0, 16.0), 'wood:#6a4a2a', 'wall backboard')
    for x in (2.0, 14.0):
        for y in (0.8, 15.2):
            m.bolt(x, y, 15.2, 'north', 0.3)
    m.g('box')
    m.box((2.0, 1.0, 10.0), (14.0, 15.6, 15.2), 'paint:ral2004', 'isolation box')
    m.box((1.8, 15.6, 9.6), (14.2, 16.0, 15.2), 'paint:ral2004', 'box lid lip')
    m.box((2.2, 1.2, 9.8), (13.8, 15.4, 10.0), PANEL, 'front panel')
    m.nameplate(3.0, 14.4, 13.0, 15.2, 9.8, 'SIGNAL ISOLATION')
    for x in (2.4, 13.6):
        for y in (1.5, 15.1):
            m.bolt(x, y, 9.8, 'north', 0.22)
    for i in range(4):                                         # slot columns
        x0 = 2.4 + i * 2.85
        m.box((x0, 1.6, 9.75), (x0 + 2.65, 13.8, 9.8), 'paint:#b9bbb7', 'slot bezel')
        m.box((x0 + 0.4, 13.3, 9.72), (x0 + 2.25, 13.75, 9.75), 'label:SLOT %d' % (4 - i), 'slot label', faces=('north',))
        m.box((x0 + 0.2, 10.6, 9.7), (x0 + 2.45, 12.6, 9.75), 'paint:#101418', 'slot display')
        m.box((x0 + 0.3, 5.2, 9.72), (x0 + 2.35, 5.6, 9.75), 'label:L A D S', 'test legend', faces=('north',))
        m.box((x0 + 0.3, 3.6, 9.72), (x0 + 2.35, 4.0, 9.75), 'label:N I', 'key legend', faces=('north',))
        m.box((x0 + 1.1, 1.6, 9.6), (x0 + 1.55, 2.2, 9.75), 'metal:brass', 'key chain eye')
        for k, col in enumerate(('metal:brass', 'paint:ral3000')):     # test sockets: signal lamp circuit + 0 V
            m.cyl((x0 + 0.7 + k * 1.2, 7.1, 9.75), 0.22, 0.12, 'z', col, 'test socket')
        m.box((x0 + 0.4, 6.6, 9.72), (x0 + 2.25, 6.85, 9.75), 'label:TEST', 'socket legend', faces=('north',))
        m.box((x0 + 0.5, 2.3, 9.55), (x0 + 2.15, 2.6, 9.75), 'paint:ral1023', 'tag holder')
        m.box((x0 + 0.7, 15.6, 11.0), (x0 + 1.9, 16.0, 14.0), 'paint:#2a2d30', 'disconnection link carrier')
    m.g('hinged cover')
    m.box((1.9, 1.0, 9.3), (2.2, 15.6, 9.6), 'paint:ral2004', 'cover hinge side')
    for y in (2.0, 8.0, 14.0):
        m.box((1.7, y, 9.2), (2.1, y + 1.2, 9.8), 'metal:steel', 'cover hinge')
    m.box((2.2, 15.2, 9.3), (13.8, 15.5, 9.6), 'paint:ral2004', 'cover top rail')
    m.g('padlock hasp')
    m.box((13.9, 6.0, 9.4), (14.4, 8.0, 10.0), 'metal:steel', 'hasp')
    m.box((13.7, 5.2, 9.2), (14.6, 6.2, 9.8), 'paint:ral3000', 'personal padlock')
    m.g('conduits')
    for x in (4.0, 8.0, 12.0):
        m.box((x - 0.35, 0.0, 12.0), (x + 0.35, 1.0, 12.7), 'metal:steel', 'conduit')
        m.cyl((x, 0.8, 12.35), 0.5, 0.3, 'y', 'metal:brass', 'conduit gland')
    m.box((3.0, 0.0, 12.6), (13.0, 0.4, 13.4), 'metal:steel', 'conduit run')
    m.box((2.2, 15.4, 9.6), (5.0, 15.6, 9.8), 'danger:DANGER', 'danger plate', faces=('north',))
    m.terminal_12v(14.0, 2.0, 12.6, 'east')
    f = m.surface('north', face=9.68, title='ISOLATION')
    for i in range(4):
        n = str(i + 1)
        cu = X(2.4 + (3 - i) * 2.85 + 1.32)                     # slot 1 on the left as you face it
        digits(m, f, 'sig' + n, cu, 12.1, 2.1, col='ffd040', h=0.5)
        digits(m, f, 'st' + n, cu, 11.15, 2.1, col='7cf29a', h=0.45)
        lamp(m, f, 'l_req' + n, cu - 0.75, 9.9, 'ffb020', '', 0.42, blink=True)
        lamp(m, f, 'l_grt' + n, cu - 0.25, 9.9, 'f0f0f0', '', 0.42)
        lamp(m, f, 'l_iso' + n, cu + 0.25, 9.9, 'ff3030', '', 0.42)
        lamp(m, f, 'l_rdy' + n, cu + 0.75, 9.9, '30e060', '', 0.42, blink=True)
        for k in range(4):
            lamp(m, f, 't%d%s' % (k + 1, n), cu - 0.75 + k * 0.5, 6.0, '30a0ff', '', 0.36)
        push(m, f, 'req' + n, cu - 0.55, 8.2, 'f08020', 'REQ', 0.65)
        push(m, f, 'rst' + n, cu + 0.55, 8.2, '1fa34a', 'RST', 0.65)
        m.ctl(f, 'key', 'k_iso' + n, cu, 3.0, w=0.9, h=0.9, col='c0a040', label='', pos=['NORM', 'ISOL'])
    lamp(m, f, 'pwr', 12.6, 14.8, '30e060', '', 0.4)
    push(m, f, 'test', 13.3, 14.8, 'f0f0f0', '', 0.4)
    m.save(item_gui=0.6)
    facing_states('signal_isolation')


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


RECIPES = {
    'signal_battery': (['ICI', 'BRB', 'BTB'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:comparator', 'B': 'minecraft:redstone_block', 'R': 'minecraft:repeater', 'T': 'minecraft:iron_trapdoor'}),
    'sighting_checker': (['GEG', ' C ', 'S S'], {'G': 'minecraft:glass_pane', 'E': 'minecraft:ender_eye', 'C': 'minecraft:comparator', 'S': 'minecraft:stick'}),
    'radio_repeater': (['ERE', ' I ', ' I '], {'E': 'minecraft:ender_pearl', 'R': 'minecraft:repeater', 'I': 'minecraft:iron_bars'}),
    'signal_isolation': (['IKI', 'LRL', 'IPI'], {'I': 'minecraft:iron_ingot', 'K': 'minecraft:tripwire_hook', 'L': 'minecraft:lever', 'R': 'minecraft:redstone', 'P': ('minecraft:planks', 0)}),
}

LANG = {
    'tile.irextras.signal_battery.name': 'Signal Battery Cabinet', 'tile.irextras.sighting_checker.name': 'Signal Sighting Checker',
    'tile.irextras.radio_repeater.name': 'Radio Repeater', 'tile.irextras.signal_isolation.name': 'Signal Isolation Box',
}

if __name__ == '__main__':
    for fn in (battery, checker, repeater, isolation):
        fn()
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# signalling batch 3 part B (#14 #16 #17 #18)\n' + '\n'.join(add) + '\n')
    print('signalling batch-3 part B assets written')
