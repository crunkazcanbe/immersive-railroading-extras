#!/usr/bin/env python3
"""Train simulation hardware (her ChatGPT list items 8, 11, 12). Front = north.

yard_panel  - Hump Yard Control Panel: the tower's classification desk. A live mimic of the lead and eight class
              tracks (occupancy strips, route lamps, car counts, hump signal, retarder lamp), the cut-list screen,
              route buttons 1-8, mode / retarder / exit-speed / page selectors, SCAN / START / STOP / REL HB, E-stop,
              pusher speed gauge, status lamps. Runs on 12 V. Code: yard.TileYardPanel.
loco_diag   - Locomotive Diagnostic Panel: the depot's engine test set. Engine gauges (rpm, coolant, oil, boost,
              battery, traction motor), a fault / readout screen, SELECT / page, START / STOP / FAULT RESET / SAND,
              AESS, RUN-ISOLATE, auto-sand, traction control and WSP switches, warning lamps. Code: yard.TileLocoDiag.
"""
import json, os
from bbkit import Model, ASSETS


def X(x):          # model x -> surface u (u = 16 - x on a north surface)
    return 16 - x


def blockstate(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % name), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item/%s.json' % name), 'w'), indent=1)


def yard_panel():
    m = Model('yard_panel')
    # ---- plinth and cabinet
    m.g('plinth')
    m.box((0, 0, 1.4), (16, 1.0, 16), 'paint:#2a2d30', 'plinth')
    m.box((0, 1.0, 1.2), (16, 1.3, 16), 'haz', 'kick strip', faces=('north',))
    for x in (0.6, 15.0):
        m.box((x, 0, 1.0), (x + 0.4, 0.6, 1.4), 'metal:steel', 'levelling foot')
    m.g('cabinet')
    m.box((0.4, 1.0, 2.4), (15.6, 29.0, 15.6), 'ral7032', 'desk cabinet')
    m.box((0.1, 29.0, 2.0), (15.9, 29.8, 15.9), 'ral7016', 'roof cap')
    m.box((0.4, 1.0, 2.1), (15.6, 2.8, 2.4), 'ral7016', 'lower trim')
    for z in (5.0, 9.0, 13.0):
        m.box((0.1, 3.0, z), (0.4, 27.0, z + 0.6), 'ral7032', 'side stiffener')
        m.box((15.6, 3.0, z), (15.9, 27.0, z + 0.6), 'ral7032', 'side stiffener')
    m.box((15.6, 14.0, 7.0), (15.75, 22.0, 11.0), 'louvre', 'side vent', faces=('east',))
    m.box((0.25, 14.0, 7.0), (0.4, 22.0, 11.0), 'louvre', 'side vent', faces=('west',))
    m.box((2.0, 3.0, 15.6), (14.0, 26.0, 15.75), 'paint:#b9b6a3', 'rear access door', faces=('south',))
    m.box((12.6, 12.0, 15.75), (13.2, 15.0, 15.95), 'metal:steel', 'rear door handle')
    m.g('mimic diagram')
    m.box((0.6, 20.2, 2.0), (15.4, 28.4, 2.4), 'paint:#1f4a2a', 'mimic board')
    m.box((0.6, 28.4, 2.0), (15.4, 28.9, 2.4), 'ral7016', 'mimic top rail')
    m.box((0.6, 19.9, 2.0), (15.4, 20.2, 2.4), 'ral7016', 'mimic bottom rail')
    # the lead, the crest and the fan of class tracks, painted on the board (the live lamps sit on top)
    m.box((1.0, 23.95, 1.95), (5.0, 24.15, 2.0), 'paint:#e8e8e0', 'lead track')
    m.box((3.2, 23.6, 1.95), (3.4, 24.5, 2.0), 'paint:#ffd600', 'crest mark')
    for i in range(8):
        y = 27.4 - i * 0.92
        m.box((5.0, min(y, 24.05) - 0.1, 1.95), (5.2, max(y, 24.05) + 0.1, 2.0), 'paint:#e8e8e0', 'ladder leg')
        m.box((5.2, y - 0.1, 1.95), (14.2, y + 0.1, 2.0), 'paint:#e8e8e0', 'class track %d' % (i + 1))
        m.box((14.2, y - 0.25, 1.95), (14.35, y + 0.25, 2.0), 'paint:#c03030', 'buffer stop')
    m.box((4.6, 21.2, 1.9), (5.6, 21.8, 2.0), 'paint:#3a3a3a', 'retarder symbol')
    m.nameplate(1.0, 26.9, 4.4, 27.9, 2.0, 'HUMP')
    m.nameplate(1.0, 20.5, 4.4, 21.3, 2.0, 'RETARDER')
    for x in (0.9, 15.1):
        for y in (20.5, 28.1):
            m.bolt(x, y, 2.0, 'north', 0.35)
    m.g('screen')
    m.box((0.6, 12.4, 1.8), (15.4, 19.8, 2.4), 'paint:#24272b', 'screen bezel')
    m.box((1.2, 12.9, 1.75), (14.8, 19.4, 1.8), 'paint:#03140a', 'cut list screen')
    m.box((13.6, 12.5, 1.75), (14.6, 12.8, 1.8), 'paint:#5a6066', 'screen maker plate')
    m.g('control desk')
    m.box((0.6, 2.8, 1.9), (15.4, 12.4, 2.4), 'paint:#3a3f44', 'button panel')
    m.box((1.0, 7.6, 1.85), (8.8, 12.0, 1.9), 'paint:#2d3135', 'route button field')
    m.nameplate(1.0, 11.95, 8.8, 12.3, 1.9, 'ROUTE TO TRACK')
    m.box((9.2, 3.0, 1.85), (15.2, 12.0, 1.9), 'paint:#2d3135', 'selector field')
    m.box((1.0, 3.0, 1.85), (8.8, 7.4, 1.9), 'paint:#2d3135', 'command field')
    for x in (0.9, 15.1):
        for y in (3.1, 12.1):
            m.bolt(x, y, 1.9, 'north', 0.3)
    m.g('telephone')
    m.box((15.6, 8.0, 3.0), (16.0, 12.0, 5.6), 'paint:#1a1a1a', 'phone body')
    m.box((15.9, 8.6, 3.3), (16.4, 11.4, 4.0), 'paint:#101010', 'handset')
    m.box((15.9, 9.4, 4.4), (16.2, 10.6, 5.2), 'mesh', 'talkback grille')
    m.cyl((16.05, 7.2, 4.2), 0.18, 0.9, 'y', 'cable:202020', 'handset cord')
    m.g('hump signal repeater')
    m.box((6.8, 29.8, 6.8), (9.2, 30.3, 9.2), 'paint:#1a1a1a', 'repeater base')
    m.box((7.4, 30.3, 7.6), (8.6, 31.9, 8.4), 'paint:#1a1a1a', 'repeater head')
    m.box((7.2, 31.9, 7.4), (8.8, 32.0, 8.6), 'paint:#1a1a1a', 'repeater hood')
    top = m.surface('north', face=7.55, title='REPEATER')
    m.ctl(top, 'lamp', 'hsg', X(8.0), 31.4, w=0.6, h=0.6, col='30e060', label='')
    m.ctl(top, 'lamp', 'hsr', X(8.0), 30.75, w=0.6, h=0.6, col='ff2020', label='')
    m.g('cable entry')
    for x in (3.0, 6.0, 9.0, 12.0):
        m.gland(x, 1.0, 13.5, 'down', r=0.4)
    m.terminal_12v(15.6, 1.6, 12.0, 'east')

    # ---- the live front
    f = m.surface('north', face=1.84, title='YARD PANEL')
    m.ctl(f, 'lamp', 'hsg', X(2.0), 25.6, w=0.7, h=0.7, col='30e060', label='')
    m.ctl(f, 'lamp', 'hsr', X(2.0), 24.8, w=0.7, h=0.7, col='ff2020', label='')
    m.ctl(f, 'lamp', 'crest', X(3.3), 23.2, w=0.5, h=0.5, col='ffd600', label='CREST')
    m.ctl(f, 'lamp', 'rc', X(5.1), 22.4, w=0.7, h=0.5, col='ffb020', label='')
    for i in range(8):
        y = 27.4 - i * 0.92
        m.ctl(f, 'lamp', 'l%d' % (i + 1), X(6.0), y, w=0.55, h=0.35, col='f4f4f0', label='')
        m.ctl(f, 'lamp', 'o%d' % (i + 1), X(9.8), y, w=6.0, h=0.32, col='ff3030', label='')
        m.ctl(f, 'lamp', 'f%d' % (i + 1), X(13.4), y, w=0.5, h=0.32, col='ffb020', label='', blink=True)
        m.ctl(f, 'digits', 'c%d' % (i + 1), X(14.9), y, w=0.9, h=0.55, col='ffb020', label='', fmt='%d')
        m.ctl(f, 'label', '', X(4.5), y, w=0, h=0, col='e0e0e0', label=str(i + 1))
    for k in range(6):
        m.ctl(f, 'digits', 's%d' % (k + 1), 8.0, 18.75 - k * 1.08, w=13.2, h=0.85, col='7cf29a', label='', fmt='%s')
    for i in range(8):
        x = 2.0 + (i % 4) * 2.0
        y = 10.6 if i < 4 else 8.6
        m.ctl(f, 'push', 'r%d' % (i + 1), X(x), y, w=1.3, h=1.3, col='f0f0f0', label=str(i + 1))
    m.ctl(f, 'selector', 'mode', X(10.2), 10.8, w=1.4, h=1.4, col='202020', label='MODE', pos=['OFF', 'HUMP', 'FLAT'])
    m.ctl(f, 'selector', 'ret', X(12.2), 10.8, w=1.4, h=1.4, col='202020', label='RETARDER', pos=['OFF', 'AUTO', 'CLOSE'])
    m.ctl(f, 'selector', 'retspd', X(14.2), 10.8, w=1.4, h=1.4, col='202020', label='EXIT KMH', pos=['6', '8', '10', '12', '15'])
    m.ctl(f, 'selector', 'page', X(10.2), 8.0, w=1.4, h=1.4, col='202020', label='SCREEN', pos=['CUTS', 'TRACKS', 'LOG'])
    m.ctl(f, 'gauge', 'kmh', X(13.2), 6.4, w=2.8, h=2.8, col='f2f0e6', label='PUSHER KMH', max=20)
    m.ctl(f, 'push', 'scan', X(2.0), 6.2, w=1.3, h=1.3, col='f0f0f0', label='SCAN')
    m.ctl(f, 'push', 'start', X(3.9), 6.2, w=1.3, h=1.3, col='20b040', label='START')
    m.ctl(f, 'push', 'stop', X(5.8), 6.2, w=1.3, h=1.3, col='d03020', label='STOP')
    m.ctl(f, 'push', 'relhb', X(7.7), 6.2, w=1.3, h=1.3, col='2050d0', label='REL HB')
    m.ctl(f, 'mushroom', 'estop', X(2.4), 3.9, w=1.9, h=1.9, col='e01010', label='E-STOP')
    for (cid, x, col, lab, kw) in (('power', 4.6, '30e060', '12V', {}), ('run', 5.9, '30e060', 'HUMPING', {}), ('hold', 7.2, 'ffb020', 'HOLD', {'blink': True}),
                                   ('fault', 8.5, 'ff2020', 'FAULT', {'blink': True}), ('done', 9.8, '2fa0ff', 'DONE', {})):
        m.ctl(f, 'lamp', cid, X(x), 3.7, w=0.7, h=0.7, col=col, label=lab, **kw)
    m.save(item_gui=0.45)
    blockstate('yard_panel')


def loco_diag():
    m = Model('loco_diag')
    m.g('pedestal')
    m.box((2.0, 0, 3.0), (14.0, 0.8, 14.0), 'paint:#2a2d30', 'base plate')
    for (x, z) in ((2.4, 3.4), (13.2, 3.4), (2.4, 13.2), (13.2, 13.2)):
        m.bolt(x, 0.8, z, 'up', 0.4)
    m.box((4.0, 0.8, 7.0), (12.0, 10.0, 13.0), 'ral1023', 'pedestal column')
    m.box((4.0, 3.0, 6.9), (12.0, 3.4, 7.0), 'haz', 'column stripe', faces=('north',))
    m.box((5.0, 4.5, 6.9), (11.0, 8.5, 7.0), 'paint:#c89a10', 'column access cover', faces=('north',))
    m.box((10.2, 6.0, 6.8), (10.6, 7.2, 6.9), 'metal:brass', 'cover lock')
    m.g('cabinet')
    m.box((0.5, 10.0, 3.0), (15.5, 27.0, 14.0), 'ral7035', 'test set cabinet')
    m.box((0.3, 27.0, 2.8), (15.7, 27.6, 14.2), 'ral7016', 'cabinet lid')
    m.box((0.5, 10.0, 2.7), (15.5, 10.6, 3.0), 'ral7016', 'cabinet sill')
    for x in (0.2, 15.5):
        m.box((x, 22.0, 6.0), (x + 0.3, 23.0, 11.0), 'metal:alu', 'carry handle')
    m.box((15.5, 12.0, 5.0), (15.7, 20.0, 12.0), 'louvre', 'fan grille', faces=('east',))
    m.box((0.3, 12.0, 5.0), (0.5, 20.0, 12.0), 'louvre', 'fan grille', faces=('west',))
    m.g('umbilical')
    m.box((6.0, 10.0, 14.0), (10.0, 13.0, 15.0), 'paint:#1a1a1a', 'connector housing')
    m.cyl((8.0, 11.5, 15.0), 0.7, 1.0, 'z', 'metal:steel', 'MU jumper socket')
    m.box((7.6, 2.0, 13.0), (8.4, 10.0, 13.8), 'cable:1a1a1a', 'jumper cable down')
    m.box((7.6, 0.8, 13.8), (8.4, 1.6, 16.0), 'cable:1a1a1a', 'jumper cable to the loco')
    m.g('fascia')
    m.box((0.5, 10.6, 2.6), (15.5, 26.6, 3.0), 'paint:#2b2f33', 'instrument fascia')
    m.box((1.0, 20.2, 2.5), (15.0, 26.2, 2.6), 'paint:#3a3f44', 'gauge field')
    m.box((1.0, 14.6, 2.5), (9.4, 19.8, 2.6), 'paint:#1c1e21', 'screen bezel')
    m.box((1.4, 15.0, 2.45), (9.0, 19.4, 2.5), 'paint:#03140a', 'diagnostic screen')
    m.box((9.8, 14.6, 2.5), (15.0, 19.8, 2.6), 'paint:#3a3f44', 'switch field')
    m.box((1.0, 11.0, 2.5), (15.0, 14.2, 2.6), 'paint:#3a3f44', 'button field')
    m.nameplate(4.0, 26.25, 12.0, 26.55, 2.6, 'LOCO DIAGNOSTICS')
    for x in (0.9, 15.1):
        for y in (10.9, 26.3):
            m.bolt(x, y, 2.6, 'north', 0.3)
    m.g('printer')
    m.box((10.0, 27.6, 5.0), (15.0, 28.6, 10.0), 'paint:#d8d8d4', 'fault printer')
    m.box((10.6, 28.0, 4.9), (14.4, 28.2, 5.0), 'paint:#1a1a1a', 'paper slot')
    m.box((11.0, 28.05, 4.3), (14.0, 28.1, 4.9), 'paint:#fbfbf6', 'printout')
    m.terminal_12v(15.5, 10.8, 9.0, 'east')
    m.gland(4.0, 10.0, 11.0, 'down', r=0.35)
    m.gland(12.0, 10.0, 11.0, 'down', r=0.35)

    f = m.surface('north', face=2.44, title='DIAGNOSTICS')
    for (cid, x, lab, mx) in (('rpm', 3.0, 'RPM', 1100), ('temp', 6.0, 'COOLANT C', 130), ('oil', 9.0, 'OIL KPA', 700),
                              ('boost', 12.4, 'BOOST KPA', 300)):
        m.ctl(f, 'gauge', cid, X(x), 24.2, w=2.6, h=2.6, col='f2f0e6', label=lab, max=mx)
    m.ctl(f, 'gauge', 'bat', X(3.0), 21.3, w=2.0, h=2.0, col='f2f0e6', label='BATT V', max=80)
    m.ctl(f, 'gauge', 'tm', X(6.0), 21.3, w=2.0, h=2.0, col='f2f0e6', label='TM C', max=220)
    m.ctl(f, 'gauge', 'fuel', X(9.0), 21.3, w=2.0, h=2.0, col='f2f0e6', label='FUEL %', max=100)
    m.ctl(f, 'gauge', 'adh', X(12.4), 21.3, w=2.0, h=2.0, col='f2f0e6', label='ADHESION %', max=150)
    for k in range(5):
        m.ctl(f, 'digits', 'd%d' % (k + 1), X(5.2), 19.0 - k * 0.86, w=7.2, h=0.7, col='7cf29a', label='', fmt='%s')
    for i, (cid, lab, pos) in enumerate((('aess', 'AESS', ['OFF', 'ON']), ('iso', 'ENGINE', ['RUN', 'ISOL']),
                                         ('sand', 'AUTO SAND', ['OFF', 'ON']), ('tc', 'TRACTION CTL', ['OFF', 'ON']),
                                         ('wsp', 'WSP', ['OFF', 'ON']), ('page', 'PAGE', ['ENGINE', 'ADHESION', 'CONSIST', 'FAULTS', 'AUX', 'MU', 'TREND', 'P-CODES']))):
        x = 10.8 + (i % 3) * 1.7
        y = 18.6 if i < 3 else 16.2
        m.ctl(f, 'selector', cid, X(x), y, w=1.1, h=1.1, col='202020', label=lab, pos=pos)
    for i, (cid, col, lab) in enumerate((('sel', 'f0f0f0', 'SELECT'), ('start', '20b040', 'START'), ('stop', 'd03020', 'STOP'),
                                        ('reset', 'f0d020', 'RESET'), ('sandp', 'c8a060', 'SAND'), ('print', 'f0f0f0', 'PRINT'))):
        m.ctl(f, 'push', cid, X(2.0 + i * 2.2), 13.0, w=1.1, h=1.1, col=col, label=lab)
    for i, (cid, col, lab, blink) in enumerate((('llink', '30e060', 'LINK', False), ('lrun', '30e060', 'RUN', False), ('lslip', 'ffb020', 'SLIP', True),
                                               ('lslide', 'ffb020', 'SLIDE', True), ('lflt', 'ff2020', 'FAULT', True), ('lhot', 'ff2020', 'HOT', False),
                                               ('loil', 'ff2020', 'OIL', False), ('lbat', 'ffb020', 'BATT', False), ('power', '30e060', '12V', False))):
        kw = {'blink': True} if blink else {}
        m.ctl(f, 'lamp', cid, X(1.8 + i * 1.55), 11.5, w=0.6, h=0.6, col=col, label=lab, **kw)
    m.save(item_gui=0.5)
    blockstate('loco_diag')


if __name__ == '__main__':
    yard_panel()
    loco_diag()
    print('yard panel + loco diagnostic assets written')
