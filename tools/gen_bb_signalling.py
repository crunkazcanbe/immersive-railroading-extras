#!/usr/bin/env python3
"""Signalling, safety and communications gear (her ChatGPT list section 4, #19-28 + the fault-injection lab).
Front = north. Every lamp, button, key, selector, dial and display here is a live control (m.ctl) that the Java tile
reads or works - see com.dogpound.railmap.safety. The 12 V terminal boxes are wire terminals, not buttons."""
import json, os
from bbkit import Model, ASSETS

LIGHT = 'paint:#c9cbc7'      # light grey control plates (labels are printed dark)
DARK = 'paint:#2a2d30'


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label, s=0.7, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


# ------------------------------------------------------------------------------------------------ 19 axle counter head
def axle_head():
    m = Model('axle_counter_head')
    m.g('base')
    m.box((2, 0, 3), (14, 0.6, 13), 'concrete', 'concrete pad')
    for x in (2.6, 13.4):
        for z in (3.6, 12.4):
            m.bolt(x, 0.6, z, 'up', 0.45)
    m.g('junction box')
    m.box((2.8, 0.6, 3.4), (8.6, 5.4, 6.6), 'paint:#d9b52a', 'trackside junction box')
    m.box((2.6, 5.4, 3.2), (8.8, 5.8, 6.8), 'paint:#c4a020', 'box lid')
    m.box((3.0, 1.0, 3.3), (8.4, 5.0, 3.4), LIGHT, 'LED panel')
    m.nameplate(3.4, 4.25, 8.0, 4.9, 3.3, 'AXLE CTR')
    for x in (3.2, 8.2):
        m.bolt(x, 5.2, 3.4, 'north', 0.3)
    m.gland(4.2, 0.6, 5.6, 'down', r=0.3)
    m.gland(7.4, 0.6, 5.6, 'down', r=0.3)
    m.terminal_12v(8.6, 1.2, 5.0, 'east')
    f = m.surface('north', face=3.28, title='AXLE HEAD')
    lamp(m, f, 'pwr', 16 - 3.9, 3.4, '30e060', 'PWR', 0.55)
    lamp(m, f, 'sa', 16 - 5.1, 3.4, 'ffb020', 'A', 0.55)
    lamp(m, f, 'sb', 16 - 6.3, 3.4, 'ffb020', 'B', 0.55)
    lamp(m, f, 'fault', 16 - 7.5, 3.4, 'ff3030', 'FLT', 0.55, blink=True)
    m.ctl(f, 'push', 'test', 16 - 5.7, 1.8, w=0.8, h=0.8, col='f0f0f0', label='TEST')
    m.g('rail clamp and sensors')
    m.box((9.0, 0.6, 7.6), (13.6, 1.4, 12.4), 'metal:steel', 'clamp base plate')
    m.box((10.2, 1.4, 8.0), (12.4, 3.6, 12.0), 'paint:#3a3d40', 'rail web clamp')
    m.box((9.6, 3.6, 8.4), (13.0, 4.2, 11.6), 'metal:steel', 'clamp jaw')
    for z in (8.6, 10.6):
        m.box((10.0, 4.2, z), (12.6, 5.6, z + 0.9), 'paint:#2f3236', 'sensor body')
        m.box((10.2, 5.6, z + 0.1), (12.4, 5.95, z + 0.8), 'paint:#f0c020', 'sensor cap')
    m.box((10.8, 5.95, 9.0), (11.8, 6.0, 11.0), 'label:A B', 'sensor label', faces=('up',))
    for z in (8.4, 11.6):
        m.bolt(9.8, 1.4, z, 'up', 0.4)
        m.bolt(12.8, 1.4, z, 'up', 0.4)
    m.g('direction arrow')
    m.box((10.9, 6.0, 4.4), (11.7, 6.1, 7.4), 'paint:#f0f0f0', 'arrow shaft')
    m.box((10.4, 6.0, 4.0), (12.2, 6.1, 4.8), 'paint:#f0f0f0', 'arrow head')
    m.box((10.0, 5.9, 3.8), (12.6, 6.0, 7.8), DARK, 'arrow plate')
    m.box((10.6, 0.6, 4.2), (12.0, 5.9, 5.0), 'metal:steel', 'arrow post')
    m.g('cable')
    m.box((8.6, 0.8, 9.6), (10.0, 1.3, 10.2), 'cable:1d1d21', 'armoured sensor cable')
    m.box((7.0, 0.8, 6.6), (7.6, 1.3, 10.2), 'cable:1d1d21', 'armoured sensor cable')
    m.save(item_gui=0.9)
    facing_states('axle_counter_head')


# ------------------------------------------------------------------------------------------------ 19 axle evaluator
def evaluator():
    m = Model('axle_evaluator')
    m.g('plinth')
    m.box((0.6, 0, 2.6), (15.4, 1.0, 15.2), 'concrete', 'concrete plinth')
    m.box((0.6, 1.0, 2.4), (15.4, 1.3, 15.2), 'haz', 'kick strip', faces=('north',))
    m.g('cabinet')
    m.box((1.0, 1.0, 3.2), (15.0, 25.0, 15.0), 'ral7032', 'location case')
    m.box((0.7, 25.0, 2.6), (15.3, 25.8, 15.4), 'ral7032', 'rain hood')
    m.box((0.7, 25.8, 4.0), (15.3, 26.3, 14.6), 'ral7032', 'roof ridge')
    for z in (6.0, 10.0, 14.0):
        m.box((0.7, 3.0, z), (1.0, 23.0, z + 0.6), 'ral7032', 'side stiffener')
        m.box((15.0, 3.0, z), (15.3, 23.0, z + 0.6), 'ral7032', 'side stiffener')
    m.louvres(1.6, 6.4, 1.8, 4.4, 3.2, n=3)
    m.louvres(9.6, 14.4, 1.8, 4.4, 3.2, n=3)
    m.g('front panel')
    m.box((1.4, 4.8, 2.9), (14.6, 24.4, 3.2), LIGHT, 'evaluator front panel')
    m.nameplate(3.4, 23.2, 12.6, 24.1, 2.9, 'AXLE COUNTER EVALUATOR')
    for x in (1.8, 14.2):
        for y in (5.2, 24.0):
            m.bolt(x, y, 2.9, 'north', 0.35)
    for i in range(4):                                       # section windows
        x0 = 1.8 + i * 3.25
        m.box((x0, 8.0, 2.82), (x0 + 3.0, 21.6, 2.9), 'paint:#b9bbb7', 'section bezel')
        m.box((x0 + 0.5, 20.6, 2.78), (x0 + 2.5, 21.4, 2.82), 'label:SECTION %d' % (i + 1), 'section label', faces=('north',))
    m.box((1.8, 5.2, 2.84), (14.2, 7.6, 2.9), 'paint:#b0b2ae', 'control strip')
    for x in (3.0, 13.0):
        m.box((x, 0.2, 9.0), (x + 0.6, 1.0, 9.6), 'cable', 'cable trough')
    for x in (3.0, 5.5, 8.0, 10.5, 13.0):
        m.gland(x, 1.0, 12.0, 'down', r=0.35)
    m.terminal_12v(15.0, 6.0, 9.0, 'east')
    m.g('door hardware')
    m.box((14.6, 12.0, 2.6), (15.0, 15.0, 2.9), 'metal:steel', 'door handle')
    for y in (6.0, 22.0):
        m.box((0.6, y, 3.2), (1.0, y + 1.4, 3.8), 'metal:steel', 'hinge')
    f = m.surface('north', face=2.76, title='EVALUATOR')
    for i in range(4):
        n = i + 1
        cu = 16 - (1.8 + i * 3.25 + 1.5)                     # column centre in u
        m.ctl(f, 'digits', 'c%d' % n, cu, 19.3, w=2.6, h=1.3, col='ff4040', label='', fmt='%d')
        m.ctl(f, 'digits', 'st%d' % n, cu, 17.6, w=2.7, h=0.9, col='ffd27a', label='', fmt='%s')
        lamp(m, f, 'clr%d' % n, cu - 0.7, 15.6, '30e060', 'CLR', 0.65)
        lamp(m, f, 'occ%d' % n, cu + 0.7, 15.6, 'ff3030', 'OCC', 0.65)
        lamp(m, f, 'dis%d' % n, cu - 0.7, 13.4, 'ffb020', 'DIST', 0.65, blink=True)
        lamp(m, f, 'prp%d' % n, cu + 0.7, 13.4, 'f0f0f0', 'PREP', 0.65)
        m.ctl(f, 'push', 'pr%d' % n, cu, 10.3, w=1.3, h=1.3, col='2050d0', label='RESET')
    m.ctl(f, 'key', 'k_reset', 16 - 3.0, 6.4, w=1.2, h=1.2, col='c0a040', label='', pos=['OFF', 'PREP', 'DIR'])
    m.ctl(f, 'digits', 'msg', 16 - 8.8, 6.4, w=5.0, h=0.9, col='7cf29a', label='', fmt='%s')
    lamp(m, f, 'pwr', 16 - 12.2, 7.0, '30e060', '', 0.5)
    lamp(m, f, 'buzz', 16 - 13.2, 7.0, 'ff3030', '', 0.5, blink=True)
    lamp(m, f, 'keyon', 16 - 12.2, 5.9, 'ffb020', '', 0.5)
    m.ctl(f, 'push', 'ack', 16 - 13.3, 5.9, w=0.7, h=0.7, col='1a1a1a', label='')
    m.ctl(f, 'push', 'test', 16 - 5.3, 6.4, w=0.6, h=0.6, col='f0f0f0', label='')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 14.0, 11.0, 23.5, 1.0, side='south', col='ral7032', title='AXLE COUNTER|EVALUATOR', volts='110V AC 12V DC', fns=(('ctl', '12V CTL'), ('scr', 'SCREENS')))
    m.save(item_gui=0.45)
    facing_states('axle_evaluator')


# ------------------------------------------------------------------------------------------------ 21 radio mast
def radio_mast():
    m = Model('radio_mast')
    m.g('foundation')
    m.box((3.0, 0, 3.0), (13.0, 0.8, 13.0), 'concrete', 'foundation')
    m.g('lattice tower')
    for (x, z) in ((5.0, 7.0), (11.0, 7.0), (5.0, 12.4), (11.0, 12.4)):
        m.box((x - 0.35, 0.8, z - 0.35), (x + 0.35, 30.0, z + 0.35), 'galv', 'tower leg')
        m.box((x - 0.6, 0.8, z - 0.6), (x + 0.6, 1.4, z + 0.6), 'galv', 'leg base plate')
    for y in range(4, 30, 5):                                # horizontals
        m.box((5.0, y, 6.85), (11.0, y + 0.25, 7.15), 'galv', 'horizontal')
        m.box((5.0, y, 12.25), (11.0, y + 0.25, 12.55), 'galv', 'horizontal')
        m.box((4.85, y, 7.0), (5.15, y + 0.25, 12.4), 'galv', 'horizontal')
        m.box((10.85, y, 7.0), (11.15, y + 0.25, 12.4), 'galv', 'horizontal')
    for y0 in range(4, 25, 5):                               # cross bracing (45 degrees in the faces)
        for z in (7.0, 12.4):
            m.box((5.2, y0 + 2.5 - 0.12, z - 0.1), (10.8, y0 + 2.5 + 0.12, z + 0.1), 'galv', 'brace', rot={'axis': 'z', 'angle': 45, 'origin': [8.0, y0 + 2.5, z]})
            m.box((5.2, y0 + 2.5 - 0.12, z - 0.1), (10.8, y0 + 2.5 + 0.12, z + 0.1), 'galv', 'brace', rot={'axis': 'z', 'angle': -45, 'origin': [8.0, y0 + 2.5, z]})
    m.ladder(9.4, 10.6, 1.4, 29.0, 12.6)
    m.g('antennas')
    m.box((4.6, 29.0, 6.6), (11.4, 29.4, 12.8), 'galv', 'antenna platform')
    for (x, z, rx) in ((8.0, 6.0, None), (4.0, 9.7, None), (12.0, 9.7, None)):
        m.box((x - 0.8, 25.5, z - 0.35), (x + 0.8, 31.0, z + 0.35), 'paint:#e8e8e2', 'sector panel antenna')
        m.box((x - 0.15, 26.0, z - 0.1), (x + 0.15, 27.0, z + 0.1), 'metal:steel', 'antenna bracket')
    m.cyl((8.0, 29.4, 9.7), 0.25, 2.6, 'y', 'metal:steel', 'lightning rod')
    m.cyl((8.0, 31.6, 9.7), 0.35, 0.35, 'y', 'paint:#c81e1e', 'aviation light')
    for x in (6.0, 10.0):
        m.box((x - 0.2, 1.4, 12.9), (x + 0.2, 29.0, 13.3), 'cable', 'feeder cable')
    m.g('equipment cabinet')
    m.box((3.4, 0.8, 1.6), (12.6, 9.0, 6.4), 'ral7035', 'base station cabinet')
    m.box((3.2, 9.0, 1.4), (12.8, 9.5, 6.6), 'ral7035', 'cabinet hood')
    m.box((3.8, 1.4, 1.5), (12.2, 8.6, 1.6), LIGHT, 'cabinet front')
    m.nameplate(4.4, 7.6, 11.6, 8.4, 1.5, 'GSM-R BTS')
    m.louvres(4.4, 11.6, 1.6, 2.8, 1.5, n=3)
    m.box((12.6, 3.0, 2.2), (13.0, 5.6, 2.6), 'metal:steel', 'cabinet handle')
    m.terminal_12v(3.4, 2.0, 4.0, 'west')
    m.gland(6.0, 9.5, 5.6, 'up', r=0.25, length=0.8)
    m.gland(10.0, 9.5, 5.6, 'up', r=0.25, length=0.8)
    f = m.surface('north', face=1.48, title='RADIO MAST')
    lamp(m, f, 'pwr', 16 - 4.8, 6.7, '30e060', 'PWR', 0.6)
    lamp(m, f, 'tx', 16 - 6.2, 6.7, '30a0ff', 'TX', 0.6, blink=True)
    lamp(m, f, 'fault', 16 - 7.6, 6.7, 'ff3030', 'FLT', 0.6, blink=True)
    m.ctl(f, 'digits', 'links', 16 - 10.4, 6.7, w=2.6, h=0.9, col='ffb020', label='', fmt='%d TRN')
    m.ctl(f, 'digits', 'cell', 16 - 6.6, 4.9, w=4.8, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'rssi', 16 - 10.4, 4.9, w=2.6, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'selector', 's_power', 16 - 5.6, 3.6, w=1.0, h=1.0, col='202020', label='', pos=['1W', '5W', '25W'])
    m.ctl(f, 'push', 'test', 16 - 11.0, 3.4, w=0.7, h=0.7, col='f0f0f0', label='TEST')
    m.save(item_gui=0.3)
    facing_states('radio_mast')


# ------------------------------------------------------------------------------------------------ 26 radio dispatch desk
def radio_desk():
    m = Model('radio_desk')
    m.g('desk')
    m.box((0, 8.2, 1.2), (16, 9.0, 12.0), 'paint:#4a4d52', 'desk top')
    m.box((-0.2, 8.0, 1.0), (16.2, 8.2, 12.2), 'paint:#2a2c30', 'desk edge')
    for x in (0.4, 13.8):
        m.box((x, 0, 2.0), (x + 1.8, 8.0, 11.6), 'paint:#3a3d42', 'pedestal')
        m.box((x + 0.2, 0.6, 1.9), (x + 1.6, 7.4, 2.0), 'paint:#45484d', 'pedestal door')
    m.box((2.2, 0.6, 10.8), (13.8, 8.0, 11.4), 'paint:#3a3d42', 'modesty panel')
    m.box((0, 0, 2.2), (16, 0.4, 11.6), 'paint:#1a1a1c', 'kick plinth')
    m.g('control strip')
    m.box((0.6, 9.0, 1.4), (15.4, 9.2, 7.0), LIGHT, 'control strip')
    m.box((0.4, 9.0, 1.2), (15.6, 9.25, 1.4), 'metal:steel', 'strip edge')
    m.g('screen hood')
    m.box((0.6, 9.0, 7.6), (15.4, 15.0, 11.6), 'paint:#2b2e33', 'screen housing')
    m.box((0.4, 15.0, 7.2), (15.6, 15.4, 11.8), 'paint:#2b2e33', 'hood top')
    m.box((1.2, 9.6, 7.5), (14.8, 14.6, 7.6), 'paint:#03140a', 'comms screen')
    m.nameplate(4.6, 14.62, 11.4, 14.98, 7.5, 'GSM-R DISPATCH')
    m.g('microphone and handset')
    m.box((13.6, 9.2, 8.0), (14.6, 9.5, 9.0), 'paint:#1a1a1a', 'mic base')
    m.box((14.0, 9.5, 8.4), (14.2, 11.6, 8.6), 'metal:steel', 'gooseneck')
    m.box((13.85, 11.6, 7.6), (14.35, 12.1, 8.6), 'paint:#1a1a1a', 'mic head', rot={'axis': 'x', 'angle': 22.5, 'origin': [14.1, 11.6, 8.5]})
    m.box((0.8, 9.2, 8.2), (3.0, 9.8, 10.8), 'paint:#1a1a1a', 'handset cradle')
    m.cyl((0.9, 10.1, 9.5), 0.4, 2.0, 'x', 'paint:#1a1a1a', 'handset')
    m.g('desk things')
    m.box((3.4, 9.2, 7.0), (6.4, 9.35, 7.5), 'paint:#1a1a1c', 'keyboard', faces=('up', 'north', 'east', 'west'))
    m.box((9.8, 9.2, 7.05), (12.6, 9.6, 7.5), 'paint:#5a3a24', 'train register', faces=('up', 'north', 'east', 'west'))
    m.box((10.0, 9.6, 7.1), (12.4, 9.65, 7.45), 'paint:#fbfbf6', 'register page', faces=('up',))
    m.box((15.0, 9.2, 9.0), (15.6, 11.6, 11.0), 'paint:#2b2e33', 'speaker')
    m.box((14.98, 9.6, 9.3), (15.0, 11.2, 10.7), 'mesh', 'speaker grille', faces=('west',))
    m.box((0.4, 9.2, 9.0), (1.0, 11.6, 11.0), 'paint:#2b2e33', 'speaker')
    m.box((1.0, 9.6, 9.3), (1.02, 11.2, 10.7), 'mesh', 'speaker grille', faces=('east',))
    m.g('equipment under the desk')
    m.box((4.0, 0.6, 6.0), (12.0, 6.6, 10.6), 'paint:#24262a', 'radio equipment rack')
    m.box((4.2, 1.0, 5.9), (11.8, 2.8, 6.0), 'paint:#2f3236', 'radio unit')
    m.box((4.2, 3.2, 5.9), (11.8, 5.0, 6.0), 'paint:#2f3236', 'recorder unit')
    for x in (4.8, 5.6, 6.4):
        m.box((x, 2.2, 5.85), (x + 0.4, 2.5, 5.9), 'paint:#30e060', 'unit led', faces=('north',))
    m.louvres(8.0, 11.4, 3.4, 4.8, 5.9, n=2)
    m.box((2.2, 7.4, 2.0), (13.8, 7.8, 10.6), 'paint:#3a3d42', 'cable tray')
    m.g('wiring')
    m.terminal_12v(8.0, 2.0, 11.6, 'south')
    m.gland(5.0, 0.4, 9.0, 'down', r=0.3)
    up = m.surface('up', face=16 - 9.2, title='DISPATCH DESK')
    row1, row2, row3 = 6.1, 3.9, 1.9
    for (cid, u, col, lab) in (('tprev', 1.8, '1a1a1a', 'TRN<'), ('tnext', 3.2, '1a1a1a', 'TRN>'), ('mprev', 4.8, '1a1a1a', 'MSG<'),
                               ('mnext', 6.2, '1a1a1a', 'MSG>'), ('lnext', 7.8, '1a1a1a', 'LINE')):
        m.ctl(up, 'push', cid, u, row1, w=1.0, h=1.0, col=col, label=lab)
    for (cid, u, col, lab) in (('call', 1.9, '1fa34a', 'CALL'), ('bcast', 3.7, 'f0d020', 'LINE'), ('all', 5.5, 'f08020', 'ALL'),
                               ('answer', 7.3, '2050d0', 'ANSWER'), ('confirm', 9.1, 'f0f0f0', 'INTEG')):
        m.ctl(up, 'push', cid, u, row2, w=1.2, h=1.2, col=col, label=lab)
    m.ctl(up, 'mushroom', 'm_emerg', 12.7, 3.6, w=1.8, h=1.8, col='d01818', label='ALL STOP')
    lamp(m, up, 'pwr', 9.6, 6.3, '30e060', '12V', 0.6)
    lamp(m, up, 'incoming', 10.8, 6.3, 'ff3030', 'CALL', 0.6, blink=True)
    lamp(m, up, 'pending', 12.0, 6.3, 'ffb020', 'ACK?', 0.6)
    lamp(m, up, 'fail', 13.2, 6.3, 'ff3030', 'FAIL', 0.6)
    lamp(m, up, 'tx', 14.4, 6.3, '30a0ff', 'TX', 0.6)
    lamp(m, up, 'emerg', 14.8, 1.9, 'ff2020', '', 0.6, blink=True)
    m.ctl(up, 'digits', 'npend', 2.6, row3, w=2.2, h=0.9, col='ffb020', label='', fmt='%d UNACK')
    m.ctl(up, 'digits', 'ncall', 5.4, row3, w=2.2, h=0.9, col='ff4040', label='', fmt='%d CALLS')
    m.ctl(up, 'push', 'test', 7.6, row3, w=0.7, h=0.7, col='f0f0f0', label='')
    scr = m.surface('north', face=7.48, title='COMMS SCREEN')
    m.ctl(scr, 'digits', 'train', 8.0, 13.9, w=13.0, h=0.9, col='ffd040', label='', fmt='%s')
    m.ctl(scr, 'digits', 'msg', 8.0, 12.9, w=13.0, h=0.9, col='7cf29a', label='', fmt='%s')
    m.ctl(scr, 'digits', 'line', 8.0, 11.9, w=13.0, h=0.8, col='5bcefa', label='', fmt='%s')
    for i in range(4):
        m.ctl(scr, 'digits', 'log%d' % (i + 1), 8.0, 10.95 - i * 0.75, w=13.2, h=0.65, col='b8f0c8', label='', fmt='%s')
    m.save(item_gui=0.5)
    facing_states('radio_desk')


# ------------------------------------------------------------------------------------------------ 23 lineside posts
def post(m, h=10.0):
    m.g('post')
    m.box((5.4, 0, 6.4), (10.6, 0.6, 11.6), 'concrete', 'post foot')
    m.box((7.2, 0.6, 8.2), (8.8, h, 9.8), 'galv', 'post')
    m.box((6.9, 0.6, 7.9), (9.1, 1.2, 10.1), 'galv', 'post base')
    m.box((7.3, h - 0.4, 9.8), (8.7, h + 0.1, 10.2), 'galv', 'mounting bracket')


def lineside(m, ident, top):
    """what every lineside post has: cable troughing, conduit up the back, earth strap, anchor bolts, an ID plate"""
    m.g('troughing and earthing')
    m.box((6.6, 0, 11.6), (9.4, 0.9, 16.0), 'concrete', 'cable trough')
    m.box((6.4, 0.9, 11.6), (9.6, 1.1, 16.0), 'paint:#8c8a84', 'trough lid')
    m.box((7.6, 1.2, 9.8), (8.4, top - 0.6, 10.4), 'paint:#3a3d40', 'cable conduit')
    for y in (2.5, top / 2, top - 1.6):
        m.box((7.3, y, 9.7), (8.7, y + 0.35, 10.5), 'metal:steel', 'conduit saddle')
    m.box((8.8, 0.6, 9.0), (9.0, 2.6, 9.2), 'metal:copper', 'earth strap')
    m.box((8.6, 0.6, 9.0), (9.6, 0.8, 9.6), 'metal:copper', 'earth clamp')
    for (x, z) in ((5.9, 6.9), (10.1, 6.9), (5.9, 11.1), (10.1, 11.1)):
        m.bolt(x, 0.6, z, 'up', 0.45)
    m.box((7.2, 2.0, 8.08), (8.8, 3.4, 8.2), 'label:' + ident, 'ID plate', faces=('north',))
    m.box((7.15, 1.95, 8.05), (8.85, 3.45, 8.08), DARK, 'ID plate frame', faces=('north',))


def plunger():
    m = Model('emergency_plunger')
    post(m, 9.0)
    lineside(m, 'ESP', 9.0)
    m.g('plunger box')
    for (x0, x1) in ((4.0, 4.4), (11.6, 12.0)):                 # hinged guard frame either side of the mushroom
        m.box((x0, 11.6, 4.6), (x1, 15.6, 5.6), 'paint:#d8aa18', 'guard cheek')
    m.box((4.0, 15.6, 4.6), (12.0, 16.0, 5.6), 'paint:#d8aa18', 'guard top bar')
    m.box((4.6, 9.0, 9.8), (11.4, 9.6, 10.4), 'paint:#d8aa18', 'back flange')
    m.box((3.8, 9.0, 5.4), (12.2, 9.4, 10.0), 'paint:#d8aa18', 'housing skirt')
    m.box((4.4, 9.0, 5.6), (11.6, 16.2, 9.8), 'paint:#f0c020', 'plunger housing')
    m.box((4.2, 16.2, 5.4), (11.8, 16.6, 10.0), 'paint:#d8aa18', 'rain cap')
    m.box((4.8, 9.4, 5.5), (11.2, 15.8, 5.6), LIGHT, 'front plate')
    m.box((5.0, 15.0, 5.44), (11.0, 15.7, 5.5), 'danger:EMERGENCY STOP', 'legend', faces=('north',))
    m.box((6.2, 12.4, 5.3), (9.8, 15.0, 5.5), 'paint:#f0d020', 'mushroom guard collar')
    m.terminal_12v(11.6, 9.4, 7.8, 'east')
    m.gland(8.0, 9.0, 8.0, 'down', r=0.3, length=0.8)
    for x in (4.8, 11.2):
        m.bolt(x, 15.9, 5.6, 'north', 0.3)
    f = m.surface('north', face=5.28, title='EMERGENCY STOP')
    m.ctl(f, 'mushroom', 'm_esp', 8.0, 13.7, w=2.2, h=2.2, col='d01818', label='')
    lamp(m, f, 'op', 16 - 10.4, 14.3, 'ff2020', '', 0.7, blink=True)
    lamp(m, f, 'pwr', 16 - 5.6, 14.3, '30e060', '', 0.55)
    m.ctl(f, 'key', 'k_reset', 16 - 5.8, 10.9, w=1.0, h=1.0, col='c0a040', label='', pos=['NRM', 'RST'])
    for i, (cid, lab) in enumerate((('st1', 'SIG'), ('st2', 'STAND'), ('st3', 'ANS'), ('st4', 'POSS'))):
        lamp(m, f, cid, 16 - (7.6 + i * 0.95), 11.4, '30e060', '', 0.45)
    m.ctl(f, 'label', '', 16 - 9.0, 10.5, w=0, h=0, col='101010', label='SIG STD ANS POS')
    m.ctl(f, 'selector', 's_zone', 16 - 10.7, 10.2, w=0.8, h=0.8, col='202020', label='', pos=['32', '64', '128'])
    m.ctl(f, 'digits', 'zone', 16 - 8.4, 9.75, w=2.6, h=0.6, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'push', 'test', 16 - 5.4, 9.7, w=0.5, h=0.5, col='f0f0f0', label='')
    m.save(item_gui=0.55)
    facing_states('emergency_plunger')


def possession():
    m = Model('possession_switch')
    post(m, 8.0)
    lineside(m, 'TBS', 8.0)
    m.g('switch box')
    m.box((4.0, 8.0, 5.6), (12.0, 8.4, 10.0), 'ral7035', 'box skirt')
    m.box((4.6, 16.4, 6.4), (11.4, 16.8, 9.2), 'paint:#c81e1e', 'possession flag')
    for y in (9.0, 15.0):
        m.bolt(4.6, y, 5.7, 'north', 0.3)
        m.bolt(11.4, y, 5.7, 'north', 0.3)
    m.box((4.5, 12.6, 5.62), (6.3, 13.0, 5.7), 'paint:#c81e1e', 'red band')
    m.box((4.2, 8.0, 5.8), (11.8, 16.0, 9.8), 'ral7035', 'possession box')
    m.box((4.0, 16.0, 5.6), (12.0, 16.4, 10.0), 'ral7035', 'rain cap')
    m.box((4.6, 8.4, 5.7), (11.4, 15.6, 5.8), LIGHT, 'front plate')
    m.box((4.8, 14.8, 5.64), (11.2, 15.5, 5.7), 'label:TRACK BLOCKING', 'legend', faces=('north',))
    m.box((10.6, 9.0, 5.5), (11.3, 13.0, 5.8), 'metal:steel', 'key guard')
    m.box((11.8, 10.0, 6.4), (12.2, 13.6, 7.0), 'metal:steel', 'door hinge')
    m.terminal_12v(4.2, 8.6, 7.8, 'west')
    m.gland(8.0, 8.0, 8.0, 'down', r=0.3, length=0.8)
    f = m.surface('north', face=5.48, title='TRACK BLOCKING SWITCH')
    m.ctl(f, 'key', 'k_poss', 16 - 9.5, 12.0, w=1.6, h=1.6, col='c0a040', label='', pos=['NORMAL', 'BLOCK'])
    lamp(m, f, 'blocked', 16 - 5.6, 13.7, 'ff2020', 'BLOCKED', 0.75)
    lamp(m, f, 'req', 16 - 5.6, 12.1, 'ffb020', 'REQ', 0.65, blink=True)
    lamp(m, f, 'clear', 16 - 6.9, 13.7, '30e060', 'CLEAR', 0.65)
    lamp(m, f, 'train', 16 - 6.9, 12.1, 'ffb020', 'TRAIN', 0.65)
    lamp(m, f, 'pwr', 16 - 10.9, 14.3, '30e060', '', 0.45)
    m.ctl(f, 'digits', 'holder', 16 - 7.4, 10.3, w=5.2, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'mins', 16 - 6.0, 9.2, w=2.2, h=0.7, col='ffb020', label='', fmt='%d MIN')
    m.ctl(f, 'digits', 'zone', 16 - 9.0, 9.2, w=2.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'selector', 's_zone', 16 - 10.9, 9.4, w=0.7, h=0.7, col='202020', label='', pos=['32', '64', '128'])
    m.ctl(f, 'push', 'test', 16 - 4.9, 9.0, w=0.5, h=0.5, col='f0f0f0', label='')
    m.save(item_gui=0.55)
    facing_states('possession_switch')


def call_point():
    m = Model('call_point')
    post(m, 7.0)
    lineside(m, 'TEL', 7.0)
    m.g('telephone cabinet')
    m.box((3.8, 7.0, 6.0), (12.2, 7.4, 10.0), 'paint:#d8aa18', 'cabinet skirt')
    m.box((5.2, 16.6, 7.6), (10.8, 18.6, 8.0), 'paint:#f4f4ee', 'telephone sign')
    m.box((5.0, 16.6, 7.5), (11.0, 18.8, 7.6), 'label:TELEPHONE', 'telephone sign face', faces=('north',))
    m.box((7.7, 16.6, 8.0), (8.3, 17.2, 8.6), 'galv', 'sign bracket')
    m.cyl((11.6, 9.0, 4.6), 0.25, 1.0, 'y', 'metal:steel', 'door catch')
    for k in range(3):
        m.box((5.0, 10.4 + k * 1.2, 5.98), (7.4, 10.8 + k * 1.2, 6.0), DARK, 'instruction text')
    m.box((4.0, 7.0, 6.2), (12.0, 16.2, 9.8), 'paint:#f0c020', 'telephone cabinet')
    m.box((3.8, 16.2, 6.0), (12.2, 16.6, 10.0), 'paint:#d8aa18', 'rain cap')
    m.box((4.4, 7.4, 6.1), (11.6, 15.8, 6.2), 'paint:#2a2d30', 'cabinet interior')
    m.box((11.6, 7.4, 4.4), (12.0, 15.8, 6.2), 'paint:#f0c020', 'open door', faces=('east', 'west', 'north', 'up', 'down'))
    m.box((4.6, 14.8, 6.04), (11.4, 15.6, 6.1), 'label:EMERGENCY TEL', 'legend', faces=('north',))
    m.box((4.8, 9.8, 6.0), (7.6, 14.4, 6.1), LIGHT, 'instruction plate')
    m.box((8.0, 8.0, 5.8), (11.2, 14.4, 6.1), 'paint:#1a1a1a', 'telephone body')
    m.box((8.6, 12.8, 5.3), (10.6, 14.2, 5.8), 'paint:#1a1a1a', 'handset cradle')
    m.cyl((8.5, 13.5, 5.3), 0.4, 2.2, 'x', 'paint:#202020', 'handset')
    for k in range(4):
        m.cyl((9.0 + k * 0.4, 10.2 - k * 0.5, 5.5), 0.15, 0.3, 'x', 'paint:#1a1a1a', 'coiled cord')
    m.terminal_12v(4.0, 7.6, 8.0, 'west')
    m.gland(8.0, 7.0, 8.0, 'down', r=0.3, length=0.8)
    f = m.surface('north', face=5.78, title='EMERGENCY TELEPHONE')
    m.ctl(f, 'push', 'call', 16 - 9.6, 11.4, w=1.4, h=1.4, col='1fa34a', label='CALL')
    m.ctl(f, 'push', 'cancel', 16 - 9.6, 9.0, w=1.0, h=1.0, col='d02020', label='')
    lamp(m, f, 'calling', 16 - 6.2, 13.6, 'ffb020', 'CALLING', 0.6, blink=True)
    lamp(m, f, 'answered', 16 - 6.2, 12.0, '30e060', 'ANSWERED', 0.6)
    lamp(m, f, 'pwr', 16 - 6.2, 10.4, '30e060', '12V', 0.45)
    m.ctl(f, 'digits', 'id', 16 - 6.2, 8.6, w=2.6, h=0.7, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'push', 'test', 16 - 5.2, 7.9, w=0.45, h=0.45, col='f0f0f0', label='')
    m.save(item_gui=0.55)
    facing_states('call_point')


# ------------------------------------------------------------------------------------------------ 24 banner repeater
def banner():
    m = Model('banner_repeater')
    post(m, 9.0)
    lineside(m, 'BR', 9.0)
    m.g('banner')
    m.box((3.0, 15.6, 5.4), (13.0, 16.0, 7.0), 'paint:#1d1f22', 'sun visor')
    for x in (3.4, 12.6):
        m.box((x - 0.2, 9.4, 6.2), (x + 0.2, 15.6, 7.0), 'paint:#1d1f22', 'visor cheek')
    m.box((4.0, 9.6, 9.8), (12.0, 10.2, 10.4), 'paint:#1d1f22', 'lamp door hinge')
    m.ladder(9.0, 10.4, 0.6, 9.0, 10.2)
    m.box((3.6, 9.0, 7.0), (12.4, 16.0, 9.4), 'paint:#1d1f22', 'banner case')
    m.cyl((8.0, 12.5, 6.2), 3.5, 0.8, 'z', 'paint:#1d1f22', 'case front ring')
    m.box((3.4, 16.0, 6.4), (12.6, 16.4, 9.6), 'paint:#1d1f22', 'case hood')
    m.box((4.6, 9.2, 9.4), (11.4, 15.2, 9.8), 'paint:#2a2d30', 'lamp housing')
    m.terminal_12v(12.4, 9.4, 8.2, 'east')
    m.gland(8.0, 9.0, 8.2, 'down', r=0.3, length=0.8)
    m.box((5.2, 9.2, 6.1), (10.8, 9.9, 6.2), 'label:BANNER', 'legend', faces=('north',))
    f = m.surface('north', face=6.18, title='BANNER')
    m.ctl(f, 'synchro', 'arm', 8.0, 12.5, w=6.0, h=6.0, col='f4f4ee', label='')
    lamp(m, f, 'lit', 16 - 4.6, 15.2, 'fff0c0', '', 0.45)
    lamp(m, f, 'pwr', 16 - 11.4, 15.2, '30e060', '', 0.45)
    m.ctl(f, 'digits', 'sig', 8.0, 8.6, w=3.6, h=0.6, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'push', 'test', 16 - 11.2, 9.2, w=0.5, h=0.5, col='f0f0f0', label='')
    m.save(item_gui=0.55)
    facing_states('banner_repeater')


# ------------------------------------------------------------------------------------------------ 25 train describer
def describer():
    m = Model('train_describer')
    m.g('panel case')
    m.box((0.2, 0.4, 9.6), (15.8, 15.6, 16.0), 'paint:#2b2e33', 'describer case')
    m.box((0, 0.2, 9.2), (16, 0.6, 16.0), 'paint:#1a1c1f', 'bottom rail')
    m.box((0, 15.4, 9.2), (16, 15.8, 16.0), 'paint:#1a1c1f', 'top rail')
    m.box((0.6, 1.0, 9.5), (15.4, 15.0, 9.6), 'paint:#16181b', 'mimic panel')
    m.nameplate(4.0, 14.1, 12.0, 14.8, 9.5, 'TRAIN DESCRIBER')
    m.g('track diagram')
    for row, y in ((0, 9.6), (1, 4.2)):
        m.box((0.8, y, 9.44), (15.2, y + 0.3, 9.5), 'paint:#d8d8d0', 'track line')
        for i in range(5):
            m.box((0.8 + i * 3.6, y - 0.3, 9.42), (0.95 + i * 3.6, y + 0.6, 9.5), 'paint:#d8d8d0', 'signal mark')
    m.box((14.9, 4.2, 9.44), (15.2, 9.9, 9.5), 'paint:#d8d8d0', 'loop line')
    m.g('mimic details')
    for x0 in (3.0, 9.6):
        m.box((x0, 7.2, 9.46), (x0 + 3.4, 7.9, 9.5), 'paint:#5a6068', 'platform', faces=('north',))
    m.box((6.4, 6.6, 9.45), (9.2, 6.9, 9.5), 'paint:#d8d8d0', 'crossover', faces=('north',), rot={'axis': 'z', 'angle': 22.5, 'origin': [7.8, 6.8, 9.5]})
    m.box((6.4, 8.9, 9.45), (9.2, 9.2, 9.5), 'paint:#d8d8d0', 'crossover', faces=('north',), rot={'axis': 'z', 'angle': -22.5, 'origin': [7.8, 9.0, 9.5]})
    for i in range(8):
        r, c = i // 4, i % 4
        y = 11.4 if r == 0 else 6.0
        x = 2.6 + c * 3.6
        m.box((x - 1.6, y - 0.8, 9.48), (x + 1.6, y + 0.8, 9.52), 'paint:#3a3d42', 'berth frame', faces=('north',))
    m.box((1.0, 1.4, 9.46), (6.0, 2.8, 9.5), 'label:UP / DOWN MAIN', 'line label', faces=('north',))
    for y in (2.0, 13.0):
        m.bolt(0.8, y, 9.6, 'north', 0.3)
        m.bolt(15.2, y, 9.6, 'north', 0.3)
    m.louvres(10.0, 15.0, 0.8, 1.6, 9.6, n=2)
    m.terminal_12v(8.0, 0.6, 16.0, 'south')
    for x in (2.0, 14.0):
        m.box((x, 15.8, 12.0), (x + 0.6, 16.0, 13.0), 'metal:steel', 'wall hook')
    f = m.surface('north', face=9.38, title='TRAIN DESCRIBER')
    for i in range(8):
        r, c = i // 4, i % 4
        u = 16 - (2.6 + c * 3.6)
        y = 11.4 if r == 0 else 6.0
        m.ctl(f, 'digits', 'b%d' % (i + 1), u, y, w=2.8, h=1.2, col='ffb020', label='', fmt='%s')
        m.ctl(f, 'digits', 'n%d' % (i + 1), u, y - 1.2, w=2.6, h=0.55, col='a0a0a0', label='', fmt='%s')
        lamp(m, f, 'o%d' % (i + 1), u + 1.7, y + 0.9, 'ffffff', '', 0.35)
    lamp(m, f, 'pwr', 16 - 1.4, 2.0, '30e060', '', 0.45)
    lamp(m, f, 'stale', 16 - 2.4, 2.0, 'ff3030', '', 0.45, blink=True)
    m.ctl(f, 'selector', 's_show', 16 - 12.6, 2.2, w=1.0, h=1.0, col='202020', label='', pos=['HC', 'RID', 'KMH'])
    m.ctl(f, 'push', 'test', 16 - 14.6, 2.0, w=0.6, h=0.6, col='f0f0f0', label='')
    m.save(item_gui=0.6)
    facing_states('train_describer')


# ------------------------------------------------------------------------------------------------ the fault lab
def fault_lab():
    m = Model('fault_lab')
    m.g('desk')
    m.box((0, 8.0, 1.0), (16, 8.8, 9.0), 'paint:#3e4248', 'desk top')
    m.box((-0.2, 7.8, 0.8), (16.2, 8.0, 9.2), 'paint:#24262a', 'desk edge')
    for x in (0.4, 12.6):
        m.box((x, 0, 1.6), (x + 3.0, 7.8, 8.8), 'paint:#33363b', 'pedestal')
        for k in range(3):
            m.box((x + 0.2, 0.6 + k * 2.4, 1.5), (x + 2.8, 2.6 + k * 2.4, 1.6), 'paint:#40444a', 'drawer front')
            m.box((x + 1.0, 1.5 + k * 2.4, 1.3), (x + 2.0, 1.75 + k * 2.4, 1.5), 'metal:alu', 'drawer pull')
    m.box((0, 0, 1.8), (16, 0.4, 8.8), 'paint:#1a1a1c', 'kick plinth')
    m.box((0.8, 8.8, 1.2), (15.2, 9.0, 6.4), LIGHT, 'test panel')
    m.box((0.6, 8.8, 1.0), (15.4, 9.05, 1.2), 'haz', 'panel edge stripe')
    m.g('test rack')
    m.box((0.4, 8.8, 9.0), (15.6, 15.6, 15.4), 'paint:#22252a', 'test rack')
    m.box((0.2, 15.6, 8.8), (15.8, 16.0, 15.6), 'paint:#22252a', 'rack top')
    m.box((0.8, 9.2, 8.9), (15.2, 15.2, 9.0), LIGHT, 'rack front')
    m.box((1.2, 12.0, 8.84), (14.8, 14.8, 8.9), 'paint:#03140a', 'status screen')
    m.nameplate(4.0, 15.0, 12.0, 15.5, 8.84, 'FAULT LAB - TEST MODE')
    m.box((0.6, 14.2, 8.4), (2.0, 15.4, 9.0), 'paint:#ffb020', 'test beacon base')
    m.g('printer')
    m.box((12.4, 8.8, 6.6), (15.6, 10.2, 8.8), 'paint:#d8d8d4', 'report printer')
    m.box((12.8, 9.6, 6.5), (15.2, 9.8, 6.6), 'paint:#1a1a1a', 'paper slot')
    m.box((13.0, 8.85, 5.6), (15.0, 8.95, 6.6), 'paint:#fbfbf6', 'printed report')
    m.g('test leads')
    m.box((1.0, 9.0, 7.0), (2.4, 9.6, 8.6), 'paint:#c81e1e', 'test lead red')
    m.box((2.6, 9.0, 7.0), (4.0, 9.6, 8.6), 'paint:#1a1a1a', 'test lead black')
    m.g('test instruments')
    m.box((1.0, 16.0, 10.0), (6.0, 18.6, 14.6), 'paint:#3a4048', 'oscilloscope')
    m.box((1.4, 16.4, 9.9), (4.0, 18.2, 10.0), 'screen:30e060', 'scope screen', faces=('north',))
    for y in (16.8, 17.6):
        m.cyl((4.8, y, 9.6), 0.3, 0.4, 'z', 'paint:#1a1a1a', 'scope knob')
    m.box((9.0, 16.0, 10.5), (15.0, 17.2, 14.6), 'paint:#2b2e33', 'patch panel')
    for i in range(6):
        m.cyl((9.6 + i * 0.95, 16.6, 10.1), 0.22, 0.4, 'z', 'metal:brass', 'test jack')
    m.box((9.4, 16.2, 10.3), (14.6, 16.25, 10.5), 'cable:c81e1e', 'patch lead')
    m.box((1.0, 9.6, 8.86), (3.0, 11.6, 8.9), 'label:TEST|ONLY', 'warning label', faces=('north',))
    m.terminal_12v(16.0, 2.0, 5.0, 'east')
    m.gland(8.0, 0.4, 6.0, 'down', r=0.3)
    up = m.surface('up', face=16 - 9.0, title='TEST PANEL')
    m.ctl(up, 'key', 'k_test', 2.0, 4.9, w=1.6, h=1.6, col='c0a040', label='', pos=['NORMAL', 'TEST'])
    m.ctl(up, 'selector', 's_fault', 5.6, 4.8, w=1.4, h=1.4, col='202020', label='', pos=['TC', 'LAMP', 'PTS', 'OCC', 'RADIO', 'AXLE', '12V'])
    m.ctl(up, 'selector', 's_zone', 9.0, 4.9, w=1.1, h=1.1, col='202020', label='', pos=['32', '64', '128'])
    m.ctl(up, 'selector', 's_guard', 11.6, 4.9, w=1.1, h=1.1, col='202020', label='', pos=['ISOL', 'TRAINS'])
    for (cid, u, col, lab) in (('inject', 1.8, 'f08020', 'INJECT'), ('clear', 3.6, '1fa34a', 'CLEAR'), ('run', 5.4, '2050d0', 'RUN'),
                               ('abort', 7.2, 'd02020', 'ABORT'), ('print', 9.0, 'f0f0f0', 'PRINT'), ('selftest', 10.8, 'f0d020', 'SELF'),
                               ('lamptest', 12.4, 'f0f0f0', 'LAMP')):
        m.ctl(up, 'push', cid, u, 2.9, w=1.1, h=1.1, col=col, label=lab)
    lamp(m, up, 'test', 14.0, 5.4, 'ffb020', 'TEST', 0.6, blink=True)
    lamp(m, up, 'busy', 14.0, 4.0, '30a0ff', 'RUN', 0.6, blink=True)
    lamp(m, up, 'pwr', 14.0, 2.9, '30e060', '', 0.5)
    rk = m.surface('north', face=8.82, title='TEST RACK')
    m.ctl(rk, 'digits', 'step', 8.0, 14.3, w=13.0, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(rk, 'digits', 'line1', 8.0, 13.4, w=13.0, h=0.7, col='b8f0c8', label='', fmt='%s')
    m.ctl(rk, 'digits', 'line2', 8.0, 12.6, w=13.0, h=0.7, col='8ac8a0', label='', fmt='%s')
    m.ctl(rk, 'digits', 'fault', 16 - 4.6, 10.9, w=6.0, h=0.8, col='ffb020', label='', fmt='%s')
    m.ctl(rk, 'digits', 'target', 16 - 4.6, 9.9, w=6.0, h=0.8, col='5bcefa', label='', fmt='%s')
    m.ctl(rk, 'digits', 'nfault', 16 - 9.4, 10.9, w=2.2, h=0.8, col='ffb020', label='', fmt='%d FLT')
    m.ctl(rk, 'digits', 'npass', 16 - 9.4, 9.9, w=2.2, h=0.8, col='30e060', label='', fmt='%d OK')
    m.ctl(rk, 'digits', 'nfail', 16 - 11.8, 9.9, w=2.2, h=0.8, col='ff4040', label='', fmt='%d NG')
    lamp(m, rk, 'pass', 16 - 13.6, 10.9, '30e060', '', 0.6)
    lamp(m, rk, 'fail', 16 - 14.6, 10.9, 'ff3030', '', 0.6, blink=True)
    lamp(m, rk, 'faults', 16 - 11.8, 10.9, 'ffb020', '', 0.6, blink=True)
    # chaos engineering (her "next 48" list #44): scenario selector, CHAOS / STOP on the desk, the running scenario on the rack
    m.ctl(rk, 'digits', 'scn', 8.0, 11.75, w=13.0, h=0.55, col='ff8060', label='', fmt='%s')
    m.g('chaos scenario plate')
    m.box((4.4, 8.8, 6.7), (12.2, 8.9, 8.7), 'paint:#3a2a2a', 'chaos plate')
    m.box((4.6, 8.9, 8.2), (7.2, 8.95, 8.6), 'label:CHAOS', 'chaos label')
    ch = m.surface('up', face=16 - 8.9, title='CHAOS')
    m.ctl(ch, 'selector', 's_scn', 10.6, 7.6, w=1.0, h=1.0, col='202020', label='SCENARIO', pos=['TRIP', 'PART', 'RTU', 'FIBRE', 'FIRE', 'FILTER', 'PIS'])
    m.ctl(ch, 'push', 'chaos', 8.6, 7.6, w=0.9, h=0.9, col='f08020', label='CHAOS')
    m.ctl(ch, 'push', 'chabort', 7.0, 7.6, w=0.9, h=0.9, col='d02020', label='STOP')
    m.save(item_gui=0.5)
    facing_states('fault_lab')


if __name__ == '__main__':
    for f in (axle_head, evaluator, radio_mast, radio_desk, plunger, possession, call_point, banner, describer, fault_lab):
        f()
    print('signalling safety assets written')
