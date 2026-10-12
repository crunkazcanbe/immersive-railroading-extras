#!/usr/bin/env python3
"""Platform operations (ChatGPT batch 3 #27 #29 #32 #33): crowd monitor, zone marker, door safety controller, station
security panel. Models face north; controls on north surfaces sit at u = 16 - x. Live controls: station/Tile*.java
(ids must match). Every one has a 12 V terminal AND a mains isolator (owner rule: screens run off the grid).
Writes models, controls, blockstates, item models and recipes (lang lines are appended by hand)."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing

X = lambda x: 16 - x


def cabinet(m, title, top=30.0, col='ral7035'):
    """a wall-hung steel control cabinet (front at z=9.4), plinth, roof, bolts, hinges, cable tray and earth bar"""
    m.g('cabinet')
    m.box((0.2, 0, 9.4), (15.8, 1.0, 16), 'paint:#2a2d30', 'plinth')
    m.box((0.4, 1.0, 9.6), (15.6, top, 16), col, 'cabinet body')
    m.box((0.2, top, 9.4), (15.8, top + 0.6, 16), col, 'roof')
    m.box((0.6, 1.2, 9.4), (15.4, top, 9.6), 'paint:#1e2226', 'fascia')
    m.box((1.0, top - 3.0, 9.3), (15.0, top - 0.6, 9.4), 'plate', 'title plate', north='plate:' + title)
    for x in (0.9, 15.1):
        for y in (1.6, top - 0.4):
            m.bolt(x, y, 9.4, 'north', 0.35)
    m.g('hinges + fittings')
    for y in (4.0, 12.0, 20.0, top - 4):
        m.box((0.1, y, 9.6), (0.4, y + 1.2, 10.2), 'metal:steel', 'hinge')
        m.box((15.6, y, 9.6), (15.9, y + 1.2, 10.2), 'metal:steel', 'hinge')
    for z in (11.0, 13.0, 15.0):
        m.box((0.1, 4.0, z), (0.4, top - 4, z + 0.5), col, 'side stiffener')
        m.box((15.6, 4.0, z), (15.9, top - 4, z + 0.5), col, 'side stiffener')
    for x in (2.0, 14.0):
        m.box((x - 0.4, top + 0.6, 12.4), (x + 0.4, top + 1.6, 13.2), 'metal:steel', 'lifting eye')
    m.box((1.0, top + 0.6, 14.6), (15.0, top + 1.0, 15.8), 'galv', 'cable tray')
    for x in range(2, 15, 2):
        m.box((x, top + 1.0, 14.8), (x + 0.6, top + 1.4, 15.6), 'cable:1d1d21', 'tray cable')
    m.box((1.2, 1.2, 15.9), (14.8, 2.0, 16.0), 'metal:copper', 'earth bar')
    for x in range(2, 15, 2):
        m.bolt(x, 1.6, 16.0, 'south', 0.3)
    m.louvres(1.5, 6.0, 1.4, 3.2, 9.4, n=3)
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 1.0, 13.5, 'down', r=0.35)
    m.terminal_12v(15.6, 2.0, 12.8, 'east')
    mains_box(m, 0.4, 2.0, 12.6, 'west')


def screen(m, x0, y0, x1, y1, z, name='screen', bezel='#23272b'):
    m.box((x0 - 0.3, y0 - 0.3, z + 0.08), (x1 + 0.3, y1 + 0.3, z + 0.2), 'paint:' + bezel, name + ' bezel')
    m.box((x0, y0, z), (x1, y1, z + 0.08), 'screen', name)


# ======================================================================================================= crowd monitor
def crowd_monitor():
    m = Model('crowd_monitor')
    cabinet(m, 'PLATFORM CROWD MONITOR')
    m.g('cctv wall')
    for i, (x0, y0) in enumerate(((1.2, 21.0), (5.4, 21.0), (9.6, 21.0), (1.2, 17.6), (5.4, 17.6), (9.6, 17.6))):
        screen(m, x0, y0, x0 + 3.6, y0 + 2.8, 9.2, 'cctv monitor %d' % (i + 1))
        for k in range(3):                                         # people blobs on the picture
            m.box((x0 + 0.6 + k * 1.0, y0 + 0.4, 9.18), (x0 + 1.0 + k * 1.0, y0 + 1.3 + (k % 2) * 0.4, 9.2), 'paint:#3a5a46', 'figure')
    m.box((13.6, 17.4, 9.2), (15.0, 24.2, 9.4), 'paint:#14181c', 'los ladder backing')
    m.g('camera')
    m.box((6.0, 31.8, 9.0), (10.0, 32.0, 12.0), 'metal:steel', 'camera bracket')
    m.box((6.6, 30.6, 7.4), (9.4, 31.8, 11.0), 'paint:#e8e8e4', 'camera housing')
    m.box((6.8, 30.8, 7.2), (9.2, 31.6, 7.4), 'glass', 'camera window')
    m.box((6.4, 31.8, 7.2), (9.6, 32.0, 11.2), 'paint:#e8e8e4', 'sun shield')
    f = m.surface('north', face=9.27, title='CROWDS')
    m.ctl(f, 'digits', 'losd', X(14.3), 25.2, w=1.6, h=1.4, col='ff4040', label='', fmt='%s')
    for i, (c, col) in enumerate(zip('abcdef', ('30e060', '80e040', 'c0e030', 'ffd020', 'ff8020', 'ff3030'))):
        m.ctl(f, 'lamp', 'los' + c, X(14.3), 23.6 - i * 1.1, w=0.7, h=0.7, col=col, label=c.upper(), **({'blink': True} if c == 'f' else {}))
        m.ctl(f, 'label', '', X(13.4), 23.6 - i * 1.1, w=0, h=0, col='e8e8e8', label=c.upper())
    m.ctl(f, 'gauge', 'densg', X(14.3), 14.6, w=2.4, h=2.4, col='f2f0e6', label='P/M2', max=100)
    for k, (cid, v, col) in enumerate((('dens', 15.8, '7cf29a'), ('waitn', 14.8, 'ffd040'), ('inflow', 13.8, '8ad4ff'), ('gateq', 12.8, 'ffd040'), ('tvmq', 11.8, 'ffd040'), ('area', 10.8, 'e8e8e8'))):
        m.ctl(f, 'digits', cid, X(6.4), v, w=10.0, h=0.8, col=col, label='', fmt='%s')
    m.box((1.2, 10.3, 9.2), (11.6, 16.4, 9.4), 'paint:#070a08', 'readout panel')
    m.ctl(f, 'digits', 'line', X(8.0), 26.4, w=13.0, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'selector', 's_len', X(2.2), 8.2, w=1.4, h=1.4, col='202020', label='LENGTH', pos=['120M', '160M', '240M', '320M', '40M', '80M'])
    m.ctl(f, 'selector', 's_wid', X(4.6), 8.2, w=1.4, h=1.4, col='202020', label='WIDTH', pos=['4M', '5M', '6M', '8M', '3M'])
    m.ctl(f, 'selector', 's_meter', X(7.0), 8.2, w=1.4, h=1.4, col='202020', label='METERING', pos=['OFF', 'AUTO', 'ON'])
    m.ctl(f, 'key', 'k_close', X(9.6), 8.2, w=1.3, h=1.3, col='c0a040', label='ENTRANCE', pos=['OPEN', 'CLOSED'])
    m.ctl(f, 'key', 'k_dwell', X(12.0), 8.2, w=1.3, h=1.3, col='c0a040', label='DWELL', pos=['OFF', 'ON'])
    pushes(m, f, [('pa', '2050d0', 'PA'), ('reset', 'f0d020', 'RESET'), ('test', 'f0f0f0', 'TEST')], X(2.4), 5.4, step=-1.6, size=1.0)
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('meterl', AMBER, 'METER'), ('closedl', RED, 'CLOSED'), ('dwelll', BLUE, 'DWELL'), ('alarml', RED, 'ALARM', True)],
          X(8.0), 5.6, step=-1.4, size=0.6)
    m.box((0.6, 4.0, 9.32), (15.4, 4.15, 9.4), 'paint:#f0c018', 'control strip line')
    m.g('mimic')
    for i in range(12):                                            # a little platform mimic strip with a train
        m.box((1.2 + i * 1.1, 27.6 - 0.9, 9.32), (2.1 + i * 1.1, 27.9 - 0.9, 9.4), 'paint:#3060a0' if i < 8 else 'paint:#606468', 'mimic coach')
    m.save(item_gui=0.45)
    return m


# ========================================================================================================= zone marker
def zone_marker():
    m = Model('zone_marker')
    m.g('base')
    m.box((5.0, 0, 5.0), (11.0, 0.8, 11.0), 'concrete', 'foundation')
    m.box((5.6, 0.8, 5.6), (10.4, 1.4, 10.4), 'galv', 'base plate')
    for (x, z) in ((6.0, 6.0), (10.0, 6.0), (6.0, 10.0), (10.0, 10.0)):
        m.cyl((x, 1.4, z), 0.25, 0.5, 'y', 'metal:steel', 'anchor bolt')
    m.g('column')
    m.box((7.2, 1.4, 7.2), (8.8, 30.0, 8.8), 'galv', 'post')
    for y in (8.0, 16.0, 24.0):
        m.box((7.0, y, 7.0), (9.0, y + 0.3, 9.0), 'galv', 'band')
    m.box((6.8, 30.0, 6.8), (9.2, 30.4, 9.2), 'galv', 'post cap')
    m.g('control box')
    m.box((6.2, 3.0, 4.6), (9.8, 9.0, 7.2), 'ral7035', 'control box')
    m.box((6.4, 3.2, 4.5), (9.6, 8.8, 4.6), 'paint:#2b2f33', 'control box door')
    for (x, y) in ((6.6, 3.4), (9.4, 3.4), (6.6, 8.6), (9.4, 8.6)):
        m.bolt(x, y, 4.5, 'north', 0.25)
    m.box((7.6, 2.0, 5.6), (8.4, 3.0, 6.4), 'metal:brass', 'gland')
    m.terminal_12v(7.4, 9.6, 8.8, 'south')
    m.box((9.0, 9.6, 8.8), (10.8, 11.6, 10.0), 'ral7035', 'mains junction box', south='label:230V')
    m.cyl((9.9, 1.4, 9.4), 0.3, 8.2, 'y', 'cable', 'mains cable')
    m.g('sign')
    m.box((3.0, 18.0, 7.6), (13.0, 29.0, 8.0), 'paint:#1d4f9a', 'zone sign')
    m.box((2.8, 17.8, 7.9), (13.2, 29.2, 8.4), 'metal:alu', 'sign frame')
    m.box((3.4, 27.0, 7.5), (12.6, 28.6, 7.6), 'paint:#f4f4f0', 'header strip', north='label:ZONE')
    m.box((4.0, 19.4, 7.5), (12.0, 26.4, 7.6), 'paint:#f4f4f0', 'letter panel')
    for y in (19.0, 27.8):
        m.box((7.0, y, 8.0), (9.0, y + 0.6, 8.8), 'galv', 'sign clamp')
        m.bolt(7.4, y + 0.3, 8.8, 'south', 0.3); m.bolt(8.6, y + 0.3, 8.8, 'south', 0.3)
    m.box((3.0, 18.0, 8.0), (13.0, 29.0, 8.4), 'paint:#1d4f9a', 'sign back', south='label:ZONE')
    m.g('sign rivets')
    for i in range(9):
        x = 3.3 + i * 1.175
        m.bolt(x, 29.0, 7.9, 'north', 0.22); m.bolt(x, 18.0, 7.9, 'north', 0.22)
    for i in range(1, 8):
        y = 18.0 + i * 1.375
        m.bolt(3.0, y, 7.9, 'north', 0.22); m.bolt(13.0, y, 7.9, 'north', 0.22)
    m.g('cable clips')
    for y in range(2, 30, 3):
        m.box((9.6, y, 9.0), (10.3, y + 0.4, 9.8), 'galv', 'cable clip')
    m.g('access + coach lamps')
    m.box((3.2, 14.6, 7.4), (12.8, 17.4, 8.0), 'paint:#14181c', 'lamp bar')
    m.box((3.0, 14.4, 7.8), (13.0, 17.6, 8.2), 'metal:alu', 'lamp bar frame')
    m.box((3.4, 12.0, 7.6), (6.2, 14.2, 7.8), 'paint:#1d4f9a', 'access symbol', north='label:WC')
    m.box((9.8, 12.0, 7.6), (12.6, 14.2, 7.8), 'paint:#f0c018', 'stop plate', north='label:STOP')
    for y in (10.0, 11.0):
        m.box((6.4, y, 7.0), (9.6, y + 0.4, 7.4), 'paint:#f0c018', 'hazard band')
    for x in (4.2, 11.8):
        m.box((x - 0.5, 15.4, 7.3), (x + 0.5, 16.4, 7.4), 'paint:#303030', 'lamp hood')
    m.box((5.0, 0.8, 4.0), (11.0, 1.0, 5.0), 'paint:#f0c018', 'tactile strip')
    for x in range(5, 11):
        m.cyl((x + 0.5, 1.0, 4.5), 0.25, 0.15, 'y', 'paint:#f0c018', 'tactile blister')
    f = m.surface('north', face=7.38, title='ZONE')
    m.ctl(f, 'digits', 'zone', X(8.0), 22.4, w=6.0, h=5.6, col='1d4f9a', label='', fmt='%s')
    m.ctl(f, 'digits', 'info', X(8.0), 16.0, w=9.0, h=1.0, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'lamp', 'here', X(4.2), 14.9, w=0.6, h=0.6, col=GREEN, label='COACH')
    m.ctl(f, 'lamp', 'short', X(11.8), 14.9, w=0.6, h=0.6, col=RED, label='SHORT', blink=True)
    m.ctl(f, 'lamp', 'accl', X(4.8), 13.1, w=0.6, h=0.6, col=BLUE, label='ACCESS')
    m.ctl(f, 'lamp', 'stopl', X(11.2), 13.1, w=0.6, h=0.6, col=AMBER, label='STOP')
    box = m.surface('north', face=4.45, title='ZONE CONTROLS')
    m.ctl(box, 'selector', 's_zone', X(7.0), 7.6, w=1.0, h=1.0, col='202020', label='ZONE', pos=list('ABCDEFGH'))
    m.ctl(box, 'key', 'k_acc', X(9.0), 7.6, w=0.8, h=0.8, col='c0a040', label='ACCESS', pos=['NO', 'YES'])
    m.ctl(box, 'key', 'k_stop', X(9.0), 5.8, w=0.8, h=0.8, col='c0a040', label='STOP END', pos=['NO', 'YES'])
    m.ctl(box, 'push', 'ann', X(7.0), 5.8, w=0.8, h=0.8, col='2050d0', label='ANNOUNCE')
    m.ctl(box, 'push', 'test', X(7.0), 4.2, w=0.6, h=0.6, col='f0f0f0', label='TEST')
    m.ctl(box, 'lamp', 'pwr', X(8.4), 4.2, w=0.4, h=0.4, col=GREEN, label='12V')
    m.ctl(box, 'lamp', 'mainsl', X(9.3), 4.2, w=0.4, h=0.4, col=GREEN, label='MAINS')
    m.save(item_gui=0.5)
    return m


# ===================================================================================================== door controller
def door_controller():
    m = Model('door_controller')
    m.g('pedestal')
    m.box((3.0, 0, 3.0), (13.0, 1.0, 13.0), 'concrete', 'plinth')
    m.box((3.6, 1.0, 4.0), (12.4, 20.0, 12.0), 'paint:#2a3a4a', 'pedestal body')
    m.box((3.4, 20.0, 3.4), (12.6, 21.0, 12.6), 'paint:#1e2a36', 'pedestal cap')
    m.box((3.5, 1.2, 3.9), (12.5, 2.0, 4.0), 'paint:#f0c018', 'kick strip')
    for i in range(6):
        m.box((3.5 + i * 1.5, 1.2, 3.85), (4.2 + i * 1.5, 2.0, 3.9), 'paint:#202020', 'hazard stripe')
    m.box((4.0, 20.6, 4.0), (12.0, 22.4, 5.0), 'paint:#1e2a36', 'hood')
    for (x, z) in ((3.8, 4.2), (12.2, 4.2), (3.8, 11.8), (12.2, 11.8)):
        m.bolt(x, 0.9, z, 'up', 0.35)
    m.g('beacon mast')
    m.box((7.6, 21.0, 9.0), (8.4, 29.0, 9.8), 'galv', 'beacon mast')
    m.cyl((8.0, 29.0, 9.4), 0.9, 1.6, 'y', 'paint:#ff8020', 'door beacon')
    m.cyl((8.0, 30.6, 9.4), 0.6, 0.3, 'y', 'paint:#202020', 'beacon cap')
    m.box((5.0, 25.0, 9.0), (11.0, 27.6, 9.6), 'plate', 'sdo sign', north='label:SDO')
    m.g('services')
    m.terminal_12v(12.4, 2.4, 8.0, 'east')
    mains_box(m, 3.6, 2.4, 8.0, 'west')
    m.box((4.2, 6.0, 12.0), (11.8, 18.0, 12.2), 'paint:#24323e', 'rear access door')
    m.box((10.6, 11.6, 12.2), (11.2, 12.6, 12.5), 'metal:brass', 'rear door lock')
    m.louvres(5.0, 11.0, 3.0, 5.0, 12.2, n=3)
    m.g('pedestal detail')
    for x in (3.6, 12.4):
        sgn = -1 if x < 8 else 1
        for y in range(4, 20, 2):
            m.box((x if sgn > 0 else x - 0.2, y, 5.0), (x + 0.2 if sgn > 0 else x, y + 0.8, 11.0), 'paint:#1e2a36', 'side rib')
    for (x, y) in ((4.0, 3.0), (12.0, 3.0), (4.0, 19.6), (12.0, 19.6), (4.0, 12.6), (12.0, 12.6)):
        m.bolt(x, y, 4.0, 'north', 0.3)
    for i in range(10):
        m.box((4.2 + i * 0.76, 21.0, 3.6), (4.6 + i * 0.76, 22.2, 4.0), 'paint:#141c24', 'hood louvre')
    m.box((6.0, 0.0, 12.0), (10.0, 1.0, 14.0), 'concrete', 'cable trench cover')
    m.cyl((8.0, 1.0, 13.0), 0.4, 5.0, 'y', 'cable:1d1d21', 'door control cable')
    m.cyl((6.6, 1.0, 13.0), 0.3, 5.0, 'y', 'cable:c81e1e', 'door release cable')
    m.box((5.0, 2.4, 3.9), (11.0, 3.4, 4.0), 'plate', 'warning plate', north='label:DOOR SAFETY')
    f = m.surface('north', face=3.95, title='DOOR SAFETY')
    m.box((4.2, 16.0, 3.9), (11.8, 19.6, 4.0), 'paint:#070a08', 'display panel')
    m.ctl(f, 'digits', 'state', X(8.0), 19.0, w=7.2, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'plat', X(8.0), 18.0, w=7.2, h=0.6, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'train', X(8.0), 17.2, w=7.2, h=0.6, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'inout', X(8.0), 16.4, w=7.2, h=0.6, col='ffd040', label='', fmt='%s')
    m.box((4.2, 13.2, 3.9), (11.8, 15.6, 4.0), 'paint:#14181c', 'coach lamp backing')
    for i in range(12):
        x = 4.6 + i * 0.6
        m.ctl(f, 'lamp', 'ci%d' % (i + 1), X(x + 0.2), 15.0, w=0.4, h=0.4, col=GREEN, label='')
        m.ctl(f, 'lamp', 'co%d' % (i + 1), X(x + 0.2), 14.2, w=0.4, h=0.4, col=RED, label='')
    m.ctl(f, 'label', '', X(8.0), 13.5, w=0, h=0, col='e8e8e8', label='COACH 1-12 IN / OUT')
    lamps(m, f, [('stopl', GREEN, 'STOP'), ('rel', GREEN, 'REL'), ('sdol', AMBER, 'SDO'), ('over', AMBER, 'LONG'), ('ovr', RED, 'OVRUN', True), ('lockl', RED, 'LOCK'), ('byl', RED, 'BYPAS', True)],
          X(4.9), 12.0, step=-1.0, size=0.5, lab_dv=0.5)
    m.ctl(f, 'selector', 's_mode', X(5.4), 9.6, w=1.2, h=1.2, col='202020', label='MODE', pos=['SDO AUTO', 'SDO OFF', 'LOCKED'])
    m.ctl(f, 'selector', 's_len', X(7.6), 9.6, w=1.2, h=1.2, col='202020', label='LENGTH', pos=['120M', '160M', '240M', '320M', '40M', '80M'])
    m.ctl(f, 'key', 'k_bypass', X(10.2), 9.6, w=1.1, h=1.1, col='c03030', label='BYPASS', pos=['NORMAL', 'BYPASS'])
    pushes(m, f, [('enda', '30a0e0', 'END A'), ('endb', '30a0e0', 'END B'), ('clrend', 'f0d020', 'CLR'), ('reset', 'f0f0f0', 'RESET')], X(4.9), 7.0, step=-1.5, size=0.9, lab_dv=0.7)
    pushes(m, f, [('ann', '2050d0', 'PA'), ('test', 'f0f0f0', 'TEST')], X(5.6), 4.8, step=-1.6, size=0.8, lab_dv=0.7)
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], X(9.8), 4.8, step=-1.4, size=0.5, lab_dv=0.5)
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 4.4, 11.6, 6.0, 18.0, 4.0, side='south', doors=1, col='paint:#2a3a4a', title='PSD CONTROL', volts='230V 12V DC')
    m.save(item_gui=0.5)
    return m


# ===================================================================================================== security panel
def station_security():
    m = Model('station_security')
    cabinet(m, 'STATION SECURITY', col='ral7016')
    m.g('clock')
    m.cyl((8.0, 26.2, 9.2), 1.6, 0.2, 'z', 'paint:#f4f4f0', 'clock face')
    m.box((7.9, 26.2, 9.0), (8.1, 27.4, 9.2), 'paint:#101010', 'minute hand')
    m.box((7.9, 26.2, 9.0), (8.8, 26.4, 9.2), 'paint:#101010', 'hour hand')
    m.g('card reader')
    m.box((11.6, 13.6, 9.1), (14.6, 18.0, 9.4), 'paint:#202226', 'card reader')
    m.box((12.0, 16.4, 9.0), (14.2, 17.6, 9.1), 'screen', 'reader screen')
    m.box((12.2, 14.0, 9.0), (14.0, 15.6, 9.1), 'paint:#3a3a3a', 'reader pad')
    m.g('cctv + siren')
    m.box((3.0, 30.6, 10.0), (5.0, 31.3, 12.0), 'paint:#c02020', 'siren')
    m.cyl((4.0, 31.3, 11.0), 0.5, 0.5, 'y', 'paint:#ff4040', 'strobe')
    m.box((10.0, 30.6, 8.6), (13.0, 31.4, 11.0), 'paint:#e8e8e4', 'pir detector')
    m.box((10.2, 30.7, 8.4), (12.8, 31.3, 8.6), 'glass', 'pir lens')
    for x in range(2, 15):
        m.box((x, 2.2, 9.3), (x + 0.5, 3.6, 9.4), 'paint:#2a2e32', 'keypad grille')
    m.g('mimic')
    m.box((1.2, 18.6, 9.2), (10.6, 23.4, 9.4), 'paint:#14181c', 'station mimic')
    for i in range(4):
        m.box((1.6 + i * 2.3, 20.6, 9.18), (3.4 + i * 2.3, 21.2, 9.2), 'paint:#808080', 'gate line')
    m.box((1.6, 19.2, 9.18), (10.2, 19.5, 9.2), 'paint:#f0f0e0', 'platform edge')
    f = m.surface('north', face=9.27, title='SECURITY')
    m.ctl(f, 'digits', 'clock', X(8.0), 24.2, w=4.0, h=1.0, col='ff8030', label='', fmt='%s')
    m.ctl(f, 'digits', 'status', X(6.0), 22.6, w=9.0, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'hours', X(6.0), 21.6, w=9.0, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'staff', X(6.0), 20.6, w=9.0, h=0.7, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'intr', X(6.0), 19.6, w=9.0, h=0.7, col='ff6060', label='', fmt='%s')
    m.ctl(f, 'push', 'sign', X(13.1), 14.8, w=1.4, h=1.0, col='30a0e0', label='SIGN IN')
    lamps(m, f, [('openl', GREEN, 'OPEN'), ('closedl', RED, 'CLOSED'), ('gatesl', AMBER, 'GATES'), ('nightl', BLUE, 'NIGHT'), ('armedl', AMBER, 'ARMED'), ('intrl', RED, 'INTRUDR', True), ('staffl', GREEN, 'STAFF')],
          X(1.6), 16.6, step=-1.4, size=0.6)
    m.ctl(f, 'selector', 's_open', X(2.2), 11.4, w=1.4, h=1.4, col='202020', label='OPENS', pos=['%02d' % ((h + 5) % 24) for h in range(24)])
    m.ctl(f, 'selector', 's_close', X(4.6), 11.4, w=1.4, h=1.4, col='202020', label='CLOSES', pos=['%02d' % h for h in range(24)])
    m.ctl(f, 'selector', 's_mode', X(7.0), 11.4, w=1.4, h=1.4, col='202020', label='MODE', pos=['AUTO', 'OPEN', 'CLOSED'])
    m.ctl(f, 'selector', 's_lamps', X(9.4), 11.4, w=1.4, h=1.4, col='202020', label='NIGHT LAMPS', pos=['SECURITY', 'OFF', 'ON'])
    m.ctl(f, 'selector', 's_rad', X(11.8), 11.4, w=1.4, h=1.4, col='202020', label='AREA', pos=['32M', '48M', '16M', '24M'])
    m.ctl(f, 'key', 'k_arm', X(14.0), 11.4, w=1.3, h=1.3, col='c03030', label='ALARM', pos=['OFF', 'ARMED'])
    pushes(m, f, [('ack', 'f0d020', 'ACK'), ('ann', '2050d0', 'PA'), ('test', 'f0f0f0', 'TEST')], X(2.4), 7.6, step=-1.6, size=1.0)
    lamps(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], X(9.0), 7.8, step=-1.6, size=0.6)
    m.box((0.6, 4.0, 9.32), (15.4, 4.15, 9.4), 'paint:#f0c018', 'control strip line')
    m.save(item_gui=0.45)
    return m


RECIPES = {
    'crowd_monitor': (['SGS', 'CFC', 'IRI'], {'S': 'irextras:part@20', 'G': 'minecraft:glass_pane', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'zone_marker': (['DSD', ' I ', ' B '], {'D': 'minecraft:dye@4', 'S': 'minecraft:sign', 'I': 'minecraft:iron_bars', 'B': 'irextras:part@2'}),
    'door_controller': (['LSL', 'CFC', 'IBI'], {'L': 'minecraft:redstone_lamp', 'S': 'irextras:part@20', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot', 'B': 'minecraft:iron_door'}),
    'station_security': (['LSL', 'CKC', 'IRI'], {'L': 'minecraft:redstone_lamp', 'S': 'irextras:part@20', 'C': 'irextras:part@2', 'K': 'minecraft:clock', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
}
FACING = list(RECIPES)

if __name__ == '__main__':
    for fn in (crowd_monitor, zone_marker, door_controller, station_security):
        mm = fn()
        print('%-18s %d boxes' % (mm.name, len(mm.boxes)))
    for n in FACING:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 4 if n == 'zone_marker' else 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
