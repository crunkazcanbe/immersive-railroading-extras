#!/usr/bin/env python3
"""Model railway (her ChatGPT list #8): the Model Railway Control Desk (12 V + mains, every control live - see
modelrail/TileModelRailway.java, ids must match), the Model Railway Table section (2x2 .. 4x4 of them behind the desk
make the baseboard) and the seven mini rolling stock items (bbkit block models used as item models).
Models face north; north-surface controls sit at u = 16 - x, 'up' surface controls at u = 16 - x, v = z.
Writes models, controls, blockstates, item models and recipes (lang lines are appended by hand)."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_stationfx import mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing

X = lambda x: 16 - x
WOOD = 'wood:8a5a32'
DARKWOOD = 'paint:#4a3020'
BAIZE = 'paint:#3f7a3a'


# ======================================================================================= the control desk
def controller():
    m = Model('model_railway_controller')
    m.g('cabinet')
    m.box((0.6, 0, 3.4), (15.4, 0.8, 15.6), 'paint:#2a2018', 'plinth')
    m.box((0.8, 0.8, 3.2), (15.2, 11.0, 15.8), WOOD, 'cabinet carcass')
    for x in (0.8, 15.2):
        m.box((x - 0.3, 0.8, 3.0), (x + 0.3, 11.0, 3.6), DARKWOOD, 'corner post')
    m.box((1.2, 1.2, 3.0), (7.8, 10.4, 3.2), DARKWOOD, 'stock box door')
    m.box((1.6, 1.6, 2.95), (7.4, 10.0, 3.0), WOOD, 'door panel')
    m.box((6.8, 5.0, 2.6), (7.2, 7.0, 2.95), 'metal:brass', 'door handle')
    m.box((1.6, 8.6, 2.94), (7.4, 9.6, 2.96), 'label:ROLLING STOCK', 'door label')
    m.box((8.2, 7.2, 3.0), (14.8, 10.4, 3.2), DARKWOOD, 'scenery drawer front')
    m.box((10.6, 8.5, 2.6), (12.4, 8.9, 3.0), 'metal:brass', 'drawer pull')
    m.box((8.6, 9.6, 2.94), (14.4, 10.2, 2.96), 'label:SCENERY', 'drawer label')
    m.box((8.2, 1.2, 3.0), (14.8, 6.8, 3.2), DARKWOOD, 'transformer bay cover')
    m.louvres(8.8, 14.2, 1.8, 6.2, 2.95, n=6)
    m.box((9.0, 5.6, 2.94), (14.0, 6.4, 2.96), 'label:230V / 16V AC 4A', 'transformer plate')
    stores = m.surface('north', face=2.9, title='STORAGE')
    m.ctl(stores, 'zone', 'box', X(4.5), 5.8, w=6.4, h=9.0, col='000000', label='')
    m.ctl(stores, 'zone', 'drawer', X(11.5), 8.8, w=6.4, h=3.0, col='000000', label='')

    m.g('desk top')
    m.box((0.2, 11.0, 1.0), (15.8, 12.0, 16.0), DARKWOOD, 'desk slab')
    m.box((0.0, 11.2, 0.8), (16.0, 12.1, 1.0), 'metal:brass', 'brass edge strip')
    m.box((0.6, 12.0, 1.4), (15.4, 12.08, 8.8), 'paint:#22262b', 'control plate')
    for x in (1.0, 15.0):
        for z in (1.8, 8.4):
            m.bolt(x, 12.08, z, 'up', 0.25)
    desk = m.surface('up', face=16 - 12.16, title='DESK')

    # controller A (operator's left = high x)
    m.g('controller A')
    m.box((11.0, 12.08, 2.2), (15.0, 12.11, 8.0), 'paint:#1a3a6a', 'controller A panel')
    m.cyl((13.0, 12.11, 4.6), 1.5, 0.03, 'y', 'metal:steel', 'knob skirt A')
    m.box((11.2, 12.11, 2.4), (14.8, 12.14, 3.0), 'label:A  SPEED 0-8', 'knob scale A')
    m.ctl(desk, 'selector', 's_spdA', X(13.0), 4.6, w=2.4, h=2.4, col='d8d8d8', label='CAB A',
          pos=['0', '1', '2', '3', '4', '5', '6', '7', '8'])
    m.ctl(desk, 'selector', 's_dirA', X(14.0), 7.0, w=1.0, h=1.0, col='202020', label='DIR A', pos=['FWD', 'REV'])
    m.ctl(desk, 'push', 'a_stop', X(12.0), 7.0, w=0.9, h=0.9, col='d02020', label='STOP A')

    # controller B (operator's right = low x)
    m.g('controller B')
    m.box((1.0, 12.08, 2.2), (5.0, 12.11, 8.0), 'paint:#6a1a4a', 'controller B panel')
    m.cyl((3.0, 12.11, 4.6), 1.5, 0.03, 'y', 'metal:steel', 'knob skirt B')
    m.box((1.2, 12.11, 2.4), (4.8, 12.14, 3.0), 'label:B  SPEED 0-8', 'knob scale B')
    m.ctl(desk, 'selector', 's_spdB', X(3.0), 4.6, w=2.4, h=2.4, col='d8d8d8', label='CAB B',
          pos=['0', '1', '2', '3', '4', '5', '6', '7', '8'])
    m.ctl(desk, 'selector', 's_dirB', X(4.0), 7.0, w=1.0, h=1.0, col='202020', label='DIR B', pos=['FWD', 'REV'])
    m.ctl(desk, 'push', 'b_stop', X(2.0), 7.0, w=0.9, h=0.9, col='d02020', label='STOP B')

    # middle: key, E-stop, mode selectors
    m.g('master')
    m.box((6.0, 12.08, 6.2), (7.6, 12.12, 7.8), 'plate', 'key escutcheon')
    m.ctl(desk, 'key', 'k_main', X(6.8), 7.0, w=1.0, h=1.0, col='c0a040', label='POWER KEY', pos=['OFF', 'ON'])
    m.box((8.4, 12.08, 5.8), (10.4, 12.13, 8.2), 'haz', 'e-stop hazard plate')
    for (x0, x1) in ((8.5, 8.8), (10.0, 10.3)):
        m.box((x0, 12.13, 6.0), (x1, 13.2, 8.0), 'paint:#e0c020', 'e-stop guard')
    m.ctl(desk, 'mushroom', 'm_estop', X(9.4), 7.0, w=1.2, h=1.2, col='d02020', label='E-STOP')
    m.ctl(desk, 'selector', 's_mode', X(6.4), 4.2, w=1.0, h=1.0, col='202020', label='MODE', pos=['MANUAL', 'AUTO', 'MIRROR'])
    m.ctl(desk, 'selector', 's_stn', X(8.0), 4.2, w=1.0, h=1.0, col='202020', label='STATIONS', pos=['PASS', 'STOP'])
    m.ctl(desk, 'selector', 's_inert', X(9.6), 4.2, w=1.0, h=1.0, col='202020', label='MOMENTUM', pos=['OFF', 'ON'])
    m.ctl(desk, 'selector', 's_lite', X(8.0), 2.2, w=0.9, h=0.9, col='202020', label='', pos=['DAY', 'NIGHT'])
    m.box((5.6, 12.08, 1.4), (10.4, 12.12, 1.8), 'label:LAYOUT LIGHTS', 'lights label')

    m.g('upstand')
    m.box((0.4, 12.0, 9.0), (15.6, 26.6, 10.4), 'paint:#2b2f36', 'upstand panel')
    m.box((0.2, 26.6, 8.6), (15.8, 27.2, 10.6), DARKWOOD, 'upstand cap')
    for x in (0.4, 15.6):
        m.box((x - 0.2, 12.0, 8.8), (x + 0.2, 26.6, 9.0), 'metal:brass', 'upstand trim')
    m.box((2.0, 25.0, 8.96), (14.0, 26.3, 9.0), 'plate', 'title plate', north='plate:MODEL RAILWAY CONTROL')
    up = m.surface('north', face=8.93, title='CONTROL')
    lamp_items = [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('trk', GREEN, 'TRACK'), ('shortl', RED, 'SHORT', True),
                  ('derl', RED, 'DERAIL', True), ('bufl', AMBER, 'BUFFER'), ('stnl', BLUE, 'STATION'), ('mirl', WHITE, 'MIRROR')]
    for i, it in enumerate(lamp_items):
        u = 1.6 + i * 1.83
        m.box((X(u) - 0.55, 23.1, 8.96), (X(u) + 0.55, 24.2, 9.0), 'paint:#101214', 'lamp bezel')
        kw = {'blink': True} if len(it) > 3 else {}
        m.ctl(up, 'lamp', it[0], u, 23.65, w=0.75, h=0.75, col=it[1], label=it[2], **kw)
    # meters + screens
    for (u, cid, lab, mx) in ((2.4, 'volts', 'TRACK V', 16), (13.6, 'amps', 'TRACK mA', 1000)):
        m.box((X(u) - 1.7, 17.6, 8.96), (X(u) + 1.7, 21.0, 9.0), 'metal:steel', 'meter bezel')
        m.ctl(up, 'gauge', cid, u, 19.3, w=2.9, h=2.9, col='f2f0e6', label=lab, max=mx)
    m.box((4.4, 17.4, 8.96), (11.6, 22.2, 9.0), 'paint:#0c0e10', 'screen bezel')
    for i, (cid, col) in enumerate((('scr', 'ffd040'), ('scrA', '5bcefa'), ('scrB', 'f5a9b8'), ('clk', '7cf29a'))):
        m.ctl(up, 'digits', cid, 8.0, 21.5 - i * 1.05, w=6.8, h=0.85, col=col, label='', fmt='%s')
    # points + signals
    m.box((7.0, 13.8, 8.96), (15.2, 16.9, 9.0), 'paint:#1e2a22', 'points panel')
    m.box((0.8, 13.8, 8.96), (6.6, 16.9, 9.0), 'paint:#2a1e1e', 'signals panel')
    for i in range(6):
        u = 1.6 + i * 1.3
        m.ctl(up, 'lamp', 'pl%d' % (i + 1), u, 16.2, w=0.45, h=0.45, col=AMBER, label='')
        m.ctl(up, 'push', 'p%d' % (i + 1), u, 14.9, w=0.85, h=0.85, col='e0e0e0', label='P%d' % (i + 1))
    for i in range(4):
        u = 10.0 + i * 1.4
        m.ctl(up, 'lamp', 'gl%d' % (i + 1), u, 16.2, w=0.45, h=0.45, col=GREEN, label='')
        m.ctl(up, 'push', 'g%d' % (i + 1), u, 14.9, w=0.85, h=0.85, col='d02020', label='S%d' % (i + 1))
    # bottom row: speed readouts + service pushes
    m.ctl(up, 'digits', 'spdA', 2.6, 12.7, w=3.8, h=0.75, col='5bcefa', label='', fmt='%s')
    m.ctl(up, 'digits', 'spdB', 13.4, 12.7, w=3.8, h=0.75, col='f5a9b8', label='', fmt='%s')
    for i, (cid, col, lab) in enumerate((('reset', 'f0c018', 'RESET'), ('rerail', '2050d0', 'RE-RAIL'), ('test', 'f0f0f0', 'TEST'))):
        m.ctl(up, 'push', cid, 6.2 + i * 1.8, 13.2, w=0.8, h=0.8, col=col, label=lab)

    m.g('wiring')
    m.terminal_12v(16.0, 3.0, 9.0, 'east')
    mains_box(m, 0.0, 3.0, 9.0, 'west')
    m.box((6.0, 12.0, 15.8), (10.0, 13.0, 16.0), 'paint:#1d1d21', 'track feed loom')
    for i, col in enumerate(('cable:c81e1e', 'cable:1d1d21', 'cable:f0c018', 'cable:2050d0', 'cable:30a050')):
        m.box((6.3 + i * 0.75, 11.0, 15.6), (6.7 + i * 0.75, 12.0, 15.9), col, 'feeder')
    m.box((2.0, 10.4, 15.8), (14.0, 11.0, 16.0), 'metal:copper', 'track bus bar')
    m.save(item_gui=0.5)
    return m


# ======================================================================================= one baseboard section
def table():
    m = Model('model_railway_table')
    m.g('baseboard')
    m.box((0, 12.0, 0), (16, 13.0, 16), WOOD, 'plywood top', up=BAIZE)
    m.box((0, 11.0, 0), (16, 12.0, 0.6), DARKWOOD, 'front fascia')
    m.box((0, 11.0, 15.4), (16, 12.0, 16), DARKWOOD, 'back fascia')
    m.box((0, 11.0, 0.6), (0.6, 12.0, 15.4), DARKWOOD, 'side fascia')
    m.box((15.4, 11.0, 0.6), (16, 12.0, 15.4), DARKWOOD, 'side fascia')
    for z in (5.0, 10.6):
        m.box((0.6, 11.2, z), (15.4, 12.0, z + 0.4), WOOD, 'cross bearer')
    m.g('legs')
    for (x, z) in ((1.2, 1.2), (14.8, 1.2), (1.2, 14.8), (14.8, 14.8)):
        m.box((x - 0.6, 0.4, z - 0.6), (x + 0.6, 11.0, z + 0.6), DARKWOOD, 'leg')
        m.box((x - 0.8, 0, z - 0.8), (x + 0.8, 0.4, z + 0.8), 'rubber', 'leveling foot')
        m.box((x - 0.7, 10.4, z - 0.7), (x + 0.7, 11.0, z + 0.7), 'metal:steel', 'leg bracket')
    m.box((1.8, 3.0, 1.0), (14.2, 3.6, 1.4), WOOD, 'front rail')
    m.box((1.8, 3.0, 14.6), (14.2, 3.6, 15.0), WOOD, 'back rail')
    m.box((1.0, 3.0, 1.8), (1.4, 3.6, 14.2), WOOD, 'side rail')
    m.box((14.6, 3.0, 1.8), (15.0, 3.6, 14.2), WOOD, 'side rail')
    m.g('fixings')
    for (x, z) in ((1.2, 1.2), (14.8, 1.2), (1.2, 14.8), (14.8, 14.8)):
        for dy in (9.0, 10.0):
            m.bolt(x, dy, z - 0.6, 'north', 0.3)
            m.bolt(x, dy, z + 0.6, 'south', 0.3)
    for x in (3.0, 8.0, 13.0):
        m.bolt(x, 11.5, 0.0, 'north', 0.35)
        m.bolt(x, 11.5, 16.0, 'south', 0.35)
    for (x0, z0, x1, z1) in ((1.8, 0.6, 3.4, 1.0), (12.6, 0.6, 14.2, 1.0), (1.8, 15.0, 3.4, 15.4), (12.6, 15.0, 14.2, 15.4)):
        m.box((x0, 10.2, z0), (x1, 11.0, z1), 'metal:steel', 'corner bracket')
    m.g('under-board wiring')
    m.box((2.0, 10.6, 7.6), (14.0, 11.0, 8.4), 'metal:copper', 'track bus')
    for x in (3.0, 6.0, 9.0, 12.0):
        m.box((x, 10.8, 7.0), (x + 0.3, 11.0, 7.6), 'cable:c81e1e', 'track dropper')
        m.box((x + 0.5, 10.8, 8.4), (x + 0.8, 11.0, 9.0), 'cable:1d1d21', 'track dropper')
    m.box((7.0, 10.0, 12.0), (9.0, 11.0, 13.5), 'paint:#2b2f36', 'point motor')
    m.box((7.6, 9.6, 12.4), (8.4, 10.0, 13.1), 'metal:copper', 'solenoid coil')
    m.save(item_gui=0.5)
    return m


# ======================================================================================= mini rolling stock (items)
def plinth(m, length=12.0):
    m.g('display plinth')
    x0, x1 = 8 - length / 2 - 1, 8 + length / 2 + 1
    m.box((x0, 0, 5.0), (x1, 1.2, 11.0), DARKWOOD, 'display plinth')
    m.box((x0 + 0.4, 1.2, 6.2), (x1 - 0.4, 1.5, 9.8), 'paint:#6b6458', 'ballast')
    for i in range(int(length) + 1):
        x = x0 + 1 + i
        m.box((x, 1.5, 6.4), (x + 0.5, 1.7, 9.6), 'wood:4a3424', 'sleeper')
    for z in (6.9, 8.9):
        m.box((x0 + 0.4, 1.7, z), (x1 - 0.4, 2.0, z + 0.2), 'metal:steel', 'rail')
    m.g('vehicle')


def bogies(m, xs, col='black'):
    for x in xs:
        m.box((x - 1.2, 2.0, 6.6), (x + 1.2, 3.0, 9.4), col, 'bogie frame')
        for dx in (-0.7, 0.7):
            m.cyl((x + dx, 2.6, 6.5), 0.6, 0.3, 'z', 'metal:steel', 'wheel')
            m.cyl((x + dx, 2.6, 9.2), 0.6, 0.3, 'z', 'metal:steel', 'wheel')


def buffers(m, x0, x1, y=3.6):
    for x, d in ((x0, -0.6), (x1, 0.6)):
        for z in (6.9, 9.1):
            m.cyl((x if d > 0 else x + d, y, z), 0.25, 0.6, 'x', 'metal:steel', 'buffer')
        m.box((min(x, x + d * 0.6) - 0.05, y - 0.3, 7.8), (max(x, x + d * 0.6) + 0.05, y + 0.3, 8.2), 'black', 'coupling')


def mini_steam():
    m = Model('mini_steam'); plinth(m)
    m.box((3.0, 3.0, 6.4), (13.0, 3.6, 9.6), 'black', 'running plate')
    m.cyl((3.4, 5.4, 8.0), 1.9, 6.8, 'x', 'paint:#1e5a2a', 'boiler')
    for x in (4.6, 6.6, 8.6):
        m.cyl((x, 5.4, 8.0), 2.0, 0.25, 'x', 'metal:brass', 'boiler band')
    m.cyl((3.0, 5.4, 8.0), 1.7, 0.4, 'x', 'black', 'smokebox door')
    m.cyl((4.0, 7.2, 8.0), 0.55, 1.6, 'y', 'black', 'chimney')
    m.cyl((7.0, 7.3, 8.0), 0.6, 0.8, 'y', 'metal:brass', 'dome')
    m.box((10.2, 3.6, 6.3), (13.4, 8.6, 9.7), 'paint:#1e5a2a', 'cab')
    m.box((10.0, 8.6, 6.1), (13.6, 9.0, 9.9), 'black', 'cab roof')
    m.box((10.6, 6.6, 6.25), (12.8, 8.0, 6.3), 'glass', 'cab window')
    m.box((10.6, 6.6, 9.7), (12.8, 8.0, 9.75), 'glass', 'cab window')
    for x in (5.0, 7.4, 9.8):
        m.cyl((x, 3.2, 6.2), 1.1, 0.3, 'z', 'paint:#1e5a2a', 'driving wheel')
        m.cyl((x, 3.2, 9.5), 1.1, 0.3, 'z', 'paint:#1e5a2a', 'driving wheel')
    m.box((5.0, 3.0, 6.0), (9.8, 3.3, 6.15), 'metal:steel', 'coupling rod')
    m.box((5.0, 3.0, 9.85), (9.8, 3.3, 10.0), 'metal:steel', 'coupling rod')
    m.box((3.2, 2.6, 6.6), (4.4, 3.4, 9.4), 'paint:#c82020', 'buffer beam')
    buffers(m, 3.0, 13.0)
    return m.save(item_gui=0.6)


def mini_diesel():
    m = Model('mini_diesel'); plinth(m)
    m.box((2.6, 3.0, 6.4), (13.4, 3.6, 9.6), 'black', 'underframe')
    m.box((2.8, 3.6, 6.5), (13.2, 7.6, 9.5), 'paint:#d8a020', 'body')
    m.box((2.8, 7.6, 6.7), (13.2, 8.2, 9.3), 'paint:#c8c8c8', 'roof')
    for x in (2.8, 12.6):
        m.box((x, 5.6, 6.45), (x + 0.6, 7.2, 9.55), 'glass', 'cab windscreen')
    m.louvres(5.0, 11.0, 5.0, 7.0, 6.45, n=6)
    m.box((6.0, 8.2, 7.2), (10.0, 8.6, 8.8), 'mesh', 'radiator fan grille')
    m.box((2.8, 4.0, 6.45), (13.2, 4.4, 9.55), 'paint:#202020', 'body stripe')
    bogies(m, (4.6, 11.4))
    m.box((6.6, 2.4, 6.8), (9.4, 3.0, 9.2), 'paint:#3a3a3a', 'fuel tank')
    buffers(m, 2.6, 13.4)
    return m.save(item_gui=0.6)


def mini_electric():
    m = Model('mini_electric'); plinth(m)
    m.box((2.6, 3.0, 6.4), (13.4, 3.6, 9.6), 'black', 'underframe')
    m.box((2.8, 3.6, 6.5), (13.2, 7.6, 9.5), 'paint:#c02030', 'body')
    m.box((2.8, 7.6, 6.7), (13.2, 8.0, 9.3), 'paint:#a0a0a0', 'roof')
    for x in (2.8, 12.6):
        m.box((x, 5.6, 6.45), (x + 0.6, 7.2, 9.55), 'glass', 'cab windscreen')
    m.box((2.8, 4.4, 6.45), (13.2, 4.8, 9.55), 'paint:#f0f0f0', 'body stripe')
    for x in (5.0, 11.0):
        m.box((x - 0.6, 8.0, 7.4), (x + 0.6, 8.4, 8.6), 'porcelain', 'pantograph insulator')
        m.box((x - 0.1, 8.4, 7.9), (x + 0.1, 10.0, 8.1), 'metal:steel', 'pantograph arm')
        m.box((x - 0.8, 10.0, 7.0), (x + 0.8, 10.2, 9.0), 'metal:copper', 'pantograph head')
    m.box((7.0, 8.0, 7.6), (9.0, 8.5, 8.4), 'paint:#2b2f36', 'roof equipment')
    bogies(m, (4.6, 11.4))
    buffers(m, 2.6, 13.4)
    return m.save(item_gui=0.6)


def mini_coach():
    m = Model('mini_coach'); plinth(m)
    m.box((2.4, 3.0, 6.4), (13.6, 3.6, 9.6), 'black', 'underframe')
    m.box((2.6, 3.6, 6.5), (13.4, 7.4, 9.5), 'paint:#7a1a2a', 'body')
    m.box((2.6, 5.0, 6.45), (13.4, 7.4, 6.5), 'paint:#e8dcc0', 'upper panel')
    m.box((2.6, 5.0, 9.5), (13.4, 7.4, 9.55), 'paint:#e8dcc0', 'upper panel')
    for i in range(6):
        x = 3.4 + i * 1.65
        m.box((x, 5.4, 6.4), (x + 1.2, 6.9, 6.45), 'glass', 'window')
        m.box((x, 5.4, 9.55), (x + 1.2, 6.9, 9.6), 'glass', 'window')
    m.box((2.4, 7.4, 6.6), (13.6, 8.0, 9.4), 'paint:#3a3a3a', 'roof')
    for x in (4.0, 8.0, 12.0):
        m.cyl((x, 8.0, 8.0), 0.25, 0.3, 'y', 'metal:steel', 'roof vent')
    bogies(m, (4.4, 11.6))
    buffers(m, 2.4, 13.6)
    return m.save(item_gui=0.6)


def mini_goods():
    m = Model('mini_goods'); plinth(m, 10)
    m.box((3.4, 3.0, 6.4), (12.6, 3.6, 9.6), 'black', 'underframe')
    m.box((3.6, 3.6, 6.5), (12.4, 7.2, 9.5), 'wood:7a4a2a', 'van body')
    for x in (3.8, 6.0, 8.0, 10.0, 12.0):
        m.box((x, 3.6, 6.45), (x + 0.2, 7.2, 6.5), 'black', 'body strapping')
    m.box((7.0, 3.8, 6.4), (9.0, 6.8, 6.45), 'wood:6a3a20', 'sliding door')
    m.box((3.4, 7.2, 6.3), (12.6, 7.8, 9.7), 'paint:#5a5a5a', 'curved roof')
    for x in (5.0, 11.0):
        m.box((x - 0.6, 2.0, 6.6), (x + 0.6, 3.0, 9.4), 'black', 'axle box')
        m.cyl((x, 2.6, 6.5), 0.65, 0.3, 'z', 'metal:steel', 'wheel')
        m.cyl((x, 2.6, 9.2), 0.65, 0.3, 'z', 'metal:steel', 'wheel')
    buffers(m, 3.4, 12.6)
    return m.save(item_gui=0.6)


def mini_tank():
    m = Model('mini_tank'); plinth(m, 10)
    m.box((3.4, 3.0, 6.4), (12.6, 3.6, 9.6), 'black', 'underframe')
    m.cyl((3.8, 5.4, 8.0), 1.7, 8.4, 'x', 'paint:#2a2a2a', 'tank barrel')
    for x in (5.0, 8.0, 11.0):
        m.cyl((x, 5.4, 8.0), 1.8, 0.2, 'x', 'paint:#202020', 'tank band')
    m.cyl((8.0, 7.2, 8.0), 0.6, 0.6, 'y', 'metal:steel', 'filler dome')
    m.box((3.8, 6.8, 6.4), (12.2, 7.0, 6.6), 'paint:#f0c018', 'hazard stripe')
    for x in (5.0, 11.0):
        m.box((x - 0.6, 2.0, 6.6), (x + 0.6, 3.0, 9.4), 'black', 'axle box')
        m.cyl((x, 2.6, 6.5), 0.65, 0.3, 'z', 'metal:steel', 'wheel')
        m.cyl((x, 2.6, 9.2), 0.65, 0.3, 'z', 'metal:steel', 'wheel')
    buffers(m, 3.4, 12.6)
    return m.save(item_gui=0.6)


def mini_brake():
    m = Model('mini_brake'); plinth(m, 9)
    m.box((4.0, 3.0, 6.4), (12.0, 3.6, 9.6), 'black', 'underframe')
    m.box((5.4, 3.6, 6.6), (12.0, 7.4, 9.4), 'wood:5a4a3a', 'van body')
    m.box((4.0, 3.6, 6.6), (5.4, 4.6, 9.4), 'wood:5a4a3a', 'veranda floor')
    m.box((4.0, 4.6, 6.6), (4.2, 6.6, 9.4), 'black', 'veranda rail')
    m.box((5.2, 7.4, 6.4), (12.2, 8.0, 9.6), 'paint:#4a4a4a', 'roof')
    m.box((6.0, 5.6, 6.55), (7.4, 6.8, 6.6), 'glass', 'look-out window')
    m.box((11.6, 6.4, 7.6), (12.0, 7.0, 8.4), 'lamp:c82020', 'tail lamp')
    for x in (6.0, 10.4):
        m.box((x - 0.6, 2.0, 6.6), (x + 0.6, 3.0, 9.4), 'black', 'axle box')
        m.cyl((x, 2.6, 6.5), 0.65, 0.3, 'z', 'metal:steel', 'wheel')
        m.cyl((x, 2.6, 9.2), 0.65, 0.3, 'z', 'metal:steel', 'wheel')
    buffers(m, 4.0, 12.0)
    return m.save(item_gui=0.6)


STOCK = ['steam', 'diesel', 'electric', 'coach', 'goods', 'tank', 'brake']
RECIPES = {
    'model_railway_controller': (['RLR', 'PCP', 'WTW'], {'R': 'irextras:instrument_wire', 'L': 'minecraft:redstone_lamp', 'P': 'minecraft:stone_button',
                                                           'C': 'minecraft:comparator', 'W': 'minecraft:planks@0', 'T': 'minecraft:iron_ingot'}),
    'model_railway_table': (['GGG', 'PPP', 'S S'], {'G': 'minecraft:carpet@13', 'P': 'minecraft:planks@0', 'S': 'minecraft:stick'}),
}
STOCK_RECIPES = {
    'steam': (['  C', 'III', 'R R'], {'C': 'minecraft:coal@0', 'I': 'minecraft:iron_nugget', 'R': 'minecraft:rail'}),
    'diesel': (['YYY', 'III', 'R R'], {'Y': 'minecraft:dye@11', 'I': 'minecraft:iron_nugget', 'R': 'minecraft:rail'}),
    'electric': (['  G', 'III', 'R R'], {'G': 'minecraft:gold_nugget', 'I': 'minecraft:iron_nugget', 'R': 'minecraft:rail'}),
    'coach': (['GGG', 'PPP', 'R R'], {'G': 'minecraft:glass_pane', 'P': 'minecraft:planks@0', 'R': 'minecraft:rail'}),
    'goods': (['PPP', 'P P', 'R R'], {'P': 'minecraft:planks@0', 'R': 'minecraft:rail'}),
    'tank': (['III', 'IBI', 'R R'], {'I': 'minecraft:iron_nugget', 'B': 'minecraft:bucket', 'R': 'minecraft:rail'}),
    'brake': (['PTP', 'P P', 'R R'], {'P': 'minecraft:planks@0', 'T': 'minecraft:redstone_torch', 'R': 'minecraft:rail'}),
}


def recipe(name, pat, key, result, meta=None):
    res = {'item': 'irextras:' + result, 'count': 1}
    if meta is not None: res['data'] = meta
    json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': res},
              open(os.path.join(ASSETS, 'recipes/%s.json' % name), 'w'), indent=1)


if __name__ == '__main__':
    for fn in (controller, table, mini_steam, mini_diesel, mini_electric, mini_coach, mini_goods, mini_tank, mini_brake):
        mm = fn()
        print('%-28s %d boxes' % (mm.name, len(mm.boxes)))
    n = 'model_railway_controller'
    bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                       'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
    json.dump({'variants': {'normal': {'model': 'irextras:model_railway_table'}}}, open(os.path.join(ASSETS, 'blockstates/model_railway_table.json'), 'w'), indent=1)
    for b in ('model_railway_controller', 'model_railway_table'):
        json.dump({'parent': 'irextras:block/' + b}, open(os.path.join(ASSETS, 'models/item/%s.json' % b), 'w'), indent=1)
        recipe(b, RECIPES[b][0], RECIPES[b][1], b)
    for i, s in enumerate(STOCK):
        json.dump({'parent': 'irextras:block/mini_' + s}, open(os.path.join(ASSETS, 'models/item/mini_%s.json' % s), 'w'), indent=1)
        recipe('mini_' + s, STOCK_RECIPES[s][0], STOCK_RECIPES[s][1], 'mini_stock', i)
