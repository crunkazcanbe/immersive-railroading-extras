#!/usr/bin/env python3
"""Signalling batch 3, part C (her ChatGPT batch-3 list #46 #48 #49): Driver Competency Assessor, Fault Diagnosis
Terminal and the control panel builder elements (push button, turn switch, indication lamps, gauge, track mimic).
Models face north; controls by u = 16 - x on north faces (u from the viewer's left), v = y; on 'up' surfaces v = z.
Every lamp, button, selector, gauge, display, drawer and zone is a live control the Java tile reads or works
(safety/TileDriverAssessor, TileDiagTerminal, TilePanelElement). The panel elements are small plates meant to be
placed side by side into one big panel, so they are detailed but light (30-80 boxes each).
Also writes the blockstates, item models, recipes and lang lines."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_depot import mains_box

X = lambda x: 16 - x
PANEL = 'paint:#c9cbc7'
DARK = 'paint:#2a2d30'
DESK = 'paint:#3e4248'


def facing_states(name):
    bs = {'variants': {'facing=north': {'model': 'irextras:' + name}, 'facing=east': {'model': 'irextras:' + name, 'y': 90},
                       'facing=south': {'model': 'irextras:' + name, 'y': 180}, 'facing=west': {'model': 'irextras:' + name, 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/' + name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def lamp(m, sf, cid, u, v, col, label='', s=0.6, blink=False):
    kw = {'blink': True} if blink else {}
    m.ctl(sf, 'lamp', cid, u, v, w=s, h=s, col=col, label=label, **kw)


def push(m, sf, cid, u, v, col, label, s=1.0):
    m.ctl(sf, 'push', cid, u, v, w=s, h=s, col=col, label=label)


def digits(m, sf, cid, u, v, w, col='7cf29a', h=0.7, fmt='%s'):
    m.ctl(sf, 'digits', cid, u, v, w=w, h=h, col=col, label='', fmt=fmt)


def zone(m, sf, cid, u, v, w, h, label=''):
    m.ctl(sf, 'zone', cid, u, v, w=w, h=h, col='000000', label=label)


# ===================================================================================================== #46 assessor
def assessor():
    m = Model('driver_assessor')
    m.g('desk')
    m.box((0, 8.4, 1.0), (16, 9.0, 10.6), DESK, 'desk top')
    m.box((-0.1, 8.2, 0.9), (16.1, 8.4, 10.7), 'paint:#24262a', 'desk edge')
    m.box((0, 0, 1.6), (16, 0.4, 10.2), 'paint:#1a1a1c', 'kick plinth')
    m.box((0.4, 0.4, 9.8), (15.6, 8.2, 10.4), 'paint:#33363b', 'modesty panel')
    m.g('drawer pedestal')
    m.box((0.4, 0.4, 1.6), (5.0, 8.2, 9.8), 'paint:#33363b', 'pedestal')
    for k, (y0, y1, cid) in enumerate(((4.6, 8.0, 'dr1'), (0.8, 4.4, 'dr2'))):
        m.box((0.6, y0, 1.45), (4.8, y1, 1.6), 'paint:#40444a', 'drawer front')
        m.box((2.0, (y0 + y1) / 2 - 0.15, 1.25), (3.4, (y0 + y1) / 2 + 0.15, 1.45), 'metal:alu', 'drawer pull')
        m.box((0.9, y1 - 0.9, 1.42), (2.0, y1 - 0.4, 1.45), 'label:%s' % ('FORMS' if k == 0 else 'CARDS'), 'drawer label', faces=('north',))
    m.g('document cabinet')
    m.box((11.0, 0.4, 1.6), (15.6, 8.2, 9.8), 'paint:#33363b', 'cabinet')
    m.box((11.2, 0.8, 1.45), (15.4, 8.0, 1.6), 'paint:#40444a', 'cabinet door')
    m.box((11.5, 3.6, 1.25), (11.8, 5.6, 1.45), 'metal:alu', 'door handle')
    m.cyl((14.6, 7.2, 1.35), 0.25, 0.15, 'z', 'metal:brass', 'cabinet lock')
    m.box((12.2, 6.6, 1.42), (15.0, 7.2, 1.45), 'label:RECORDS', 'cabinet label', faces=('north',))
    for y in (1.4, 7.0):
        m.box((15.4, y, 1.5), (15.6, y + 0.8, 1.9), 'metal:steel', 'hinge')
    m.g('knee space')
    m.box((5.0, 0.4, 9.4), (11.0, 8.2, 9.8), 'paint:#2b2e33', 'knee panel')
    m.box((6.0, 0.4, 3.0), (10.0, 0.8, 7.0), 'paint:#1a1a1a', 'foot rest')
    m.box((6.2, 0.8, 3.2), (9.8, 0.9, 6.8), 'rubber', 'foot rest mat')
    m.g('sloped control panel')
    m.box((0.6, 9.0, 1.2), (15.4, 9.25, 7.4), PANEL, 'control plate')
    m.box((0.4, 9.0, 1.0), (15.6, 9.3, 1.2), 'haz', 'plate edge stripe')
    for x in (0.9, 15.1):
        for z in (1.5, 7.1):
            m.bolt(x, 9.25, z, 'up', 0.3)
    m.box((1.0, 9.25, 6.1), (6.0, 9.3, 6.9), 'label:ANSWER', 'label strip')
    m.box((7.0, 9.25, 6.1), (14.6, 9.3, 6.9), 'label:QUALIFY', 'label strip')
    m.g('keyboard')
    m.box((3.2, 9.0, 7.6), (12.8, 9.4, 10.0), 'paint:#1c1d20', 'keyboard')
    for r in range(4):
        for c in range(14):
            m.box((3.5 + c * 0.66, 9.4, 7.85 + r * 0.52), (4.0 + c * 0.66, 9.55, 8.25 + r * 0.52), 'paint:#2c2e33', 'key')
    m.box((13.4, 9.0, 8.4), (14.4, 9.35, 9.8), 'paint:#1c1d20', 'mouse')
    m.g('card reader + printer')
    m.box((0.8, 9.0, 7.6), (2.8, 9.9, 9.8), 'paint:#2b2e33', 'card reader')
    m.box((1.0, 9.9, 8.4), (2.6, 9.95, 8.6), 'paint:#0a0a0a', 'card slot')
    m.box((0.6, 8.9, 10.6), (4.4, 10.8, 13.4), 'paint:#d8d8d4', 'certificate printer')
    m.box((1.0, 10.2, 10.55), (4.0, 10.4, 10.6), 'paint:#1a1a1a', 'paper slot')
    m.box((1.2, 10.25, 9.9), (3.8, 10.3, 10.55), 'paint:#fbfbf6', 'certificate')
    m.g('monitor')
    m.box((6.6, 9.0, 12.4), (9.4, 9.4, 14.4), 'paint:#1c1d20', 'monitor foot')
    m.box((7.5, 9.4, 12.9), (8.5, 12.0, 13.6), 'paint:#1c1d20', 'monitor neck')
    m.box((0.6, 11.0, 11.0), (15.4, 22.6, 12.6), 'paint:#16171a', 'monitor housing')
    m.box((1.0, 11.6, 10.95), (15.0, 22.2, 11.0), 'screen:03140a', 'assessor screen', faces=('north',))
    m.box((0.6, 10.6, 11.0), (15.4, 11.0, 12.2), 'paint:#16171a', 'monitor chin')
    m.nameplate(5.0, 10.62, 11.0, 10.98, 11.0, 'ASSESSOR')
    m.box((14.0, 10.66, 10.9), (14.6, 10.9, 11.0), 'led:30e060', 'monitor power led')
    m.g('wall rail + headset')
    m.box((0.2, 14.0, 13.6), (15.8, 14.6, 14.2), 'metal:alu', 'accessory rail')
    m.box((13.6, 12.0, 13.0), (15.2, 14.0, 13.6), 'paint:#1a1a1a', 'headset')
    m.box((13.8, 11.6, 12.8), (15.0, 12.4, 13.2), 'paint:#2a2a2a', 'headset cup')
    m.g('power')
    mains_box(m, 15.6, 1.2, 6.0, face='east')
    m.terminal_12v(8.0, 0.6, 10.4, 'south')
    m.box((9.6, 1.0, 10.4), (11.0, 2.4, 10.8), 'paint:#3a3f44', 'mains inlet')
    m.box((9.7, 1.6, 10.8), (10.9, 2.3, 10.85), 'label:230V AC', 'inlet label', faces=('south',))
    m.gland(10.3, 0.4, 10.6, 'down', r=0.3)

    # ---- screen
    s = m.surface('north', face=10.9, title='ASSESSOR SCREEN')
    digits(m, s, 'who', 8.0, 21.6, 13.0, col='ffd040', h=0.7)
    digits(m, s, 'q1', 8.0, 20.3, 13.0, col='e8ecf0', h=0.6)
    digits(m, s, 'q2', 8.0, 19.5, 13.0, col='e8ecf0', h=0.6)
    digits(m, s, 'q3', 8.0, 18.7, 13.0, col='e8ecf0', h=0.6)
    for i, c in enumerate('ABCD'):
        digits(m, s, 'a' + c, 8.0, 17.4 - i * 0.85, 13.0, col='5bcefa', h=0.6)
    digits(m, s, 'prog', 6.0, 13.3, 9.0, col='7cf29a', h=0.6)
    digits(m, s, 'line', 12.6, 13.3, 4.0, col='ffb020', h=0.6)
    digits(m, s, 'msg', 8.0, 12.3, 13.0, col='ffb020', h=0.55)
    m.ctl(s, 'digits', 'score', 2.0, 14.4, w=2.0, h=0.6, col='ffd040', label='', fmt='%d PTS')
    m.ctl(s, 'digits', 'known', 6.0, 14.4, w=3.0, h=0.6, col='7cf29a', label='', fmt='%d SIGS')
    m.ctl(s, 'digits', 'hrs', 10.0, 14.4, w=2.6, h=0.6, col='7cf29a', label='', fmt='%d HRS')
    lamp(m, s, 'susp', 14.0, 14.4, 'ff3030', '', 0.55, blink=True)

    # ---- desk controls (top, v = z)
    up = m.surface('up', face=16 - 9.25, title='ASSESSOR DESK')
    push(m, up, 'ident', X(1.8), 2.2, '2050d0', 'IDENT', 1.0)
    push(m, up, 'start', X(1.8), 4.2, '1fa34a', 'START', 1.0)
    for i, c in enumerate('abcd'):
        push(m, up, c, X(3.6 + i * 1.6), 3.0, 'f0f0f0', c.upper(), 1.1)
    push(m, up, 'abort', X(1.8), 5.8, 'd02020', 'ABORT', 0.8)
    lamp(m, up, 'right', X(4.4), 5.0, '30e060', 'RIGHT', 0.6)
    lamp(m, up, 'wrong', X(6.2), 5.0, 'ff3030', 'WRONG', 0.6, blink=True)
    lamp(m, up, 'testing', X(8.0), 5.0, 'ffb020', 'TEST', 0.6, blink=True)
    push(m, up, 'lprev', X(8.6), 2.2, '202020', '<', 0.7)
    push(m, up, 'lnext', X(9.6), 2.2, '202020', '>', 0.7)
    push(m, up, 'routeq', X(11.0), 2.2, 'f0d020', 'ROUTE', 0.9)
    m.ctl(up, 'selector', 's_trac', X(12.8), 3.0, w=1.1, h=1.1, col='202020', label='TRACTION', pos=['DSL', 'ELEC', 'STM'])
    push(m, up, 'passout', X(14.6), 2.2, 'f08020', 'PASS', 0.9)
    push(m, up, 'print', X(11.0), 4.6, 'f0f0f0', 'PRINT', 0.8)
    push(m, up, 'test', X(14.6), 5.6, 'f0f0f0', 'LAMP', 0.5)
    lamp(m, up, 'pwr', X(10.2), 5.8, '30e060', '12V', 0.5)
    lamp(m, up, 'mains', X(11.4), 5.8, '30e060', 'MAINS', 0.5)
    lamp(m, up, 'pass', X(12.6), 5.8, '30e060', 'PASS', 0.5)
    lamp(m, up, 'fail', X(13.6), 5.8, 'ff3030', 'FAIL', 0.5)
    # drawers + cabinet are real storage (zones on their fronts)
    fr = m.surface('north', face=1.4, title='DESK FRONT')
    zone(m, fr, 'dr1', X(2.7), 6.3, 4.2, 3.4)
    zone(m, fr, 'dr2', X(2.7), 2.6, 4.2, 3.6)
    zone(m, fr, 'cab', X(13.3), 4.4, 4.2, 7.2)
    m.save(item_gui=0.45)
    facing_states('driver_assessor')


# ===================================================================================================== #48 terminal
def diag_terminal():
    m = Model('diagnostic_terminal')
    m.g('stand base')
    for (x, z) in ((3.0, 3.0), (13.0, 3.0), (3.0, 13.0), (13.0, 13.0)):
        m.box((x - 0.6, 0, z - 0.6), (x + 0.6, 0.9, z + 0.6), 'rubber', 'castor')
        m.cyl((x, 0.45, z), 0.45, 0.6, 'x', 'paint:#1a1a1a', 'castor wheel')
    m.box((2.4, 0.9, 2.4), (13.6, 1.5, 13.6), 'paint:#ffb020', 'base frame')
    m.box((2.6, 1.5, 2.6), (13.4, 1.7, 13.4), 'paint:#2a2d30', 'base tray')
    m.g('tool case (lower shelf)')
    m.box((3.0, 1.7, 4.0), (13.0, 5.4, 12.0), 'paint:#c81e1e', 'tool case')
    m.box((3.0, 5.4, 4.0), (13.0, 5.8, 12.0), 'paint:#a01818', 'case lid')
    for x in (5.0, 11.0):
        m.box((x, 3.6, 3.8), (x + 0.8, 4.4, 4.0), 'metal:steel', 'case latch')
    m.box((7.0, 5.8, 7.6), (9.0, 6.3, 8.4), 'paint:#1a1a1a', 'case handle')
    m.box((4.0, 2.4, 3.95), (8.0, 3.2, 4.0), 'label:TOOLS', 'case label', faces=('north',))
    m.g('column + drawer')
    m.box((6.6, 5.8, 9.6), (9.4, 9.0, 12.4), 'paint:#2a2d30', 'column')
    m.box((3.0, 6.4, 3.2), (13.0, 8.4, 9.6), 'paint:#3a3d42', 'drawer unit')
    m.box((3.4, 6.6, 3.05), (12.6, 8.2, 3.2), 'paint:#4a4e55', 'drawer front')
    m.box((7.0, 7.2, 2.85), (9.0, 7.6, 3.05), 'metal:alu', 'drawer pull')
    m.g('tray')
    m.box((1.6, 8.4, 2.0), (14.4, 9.0, 14.0), 'paint:#ffb020', 'tray')
    m.box((1.4, 9.0, 1.8), (14.6, 9.4, 2.2), 'paint:#e09010', 'tray lip')
    m.box((1.4, 9.0, 13.8), (14.6, 9.4, 14.2), 'paint:#e09010', 'tray lip')
    m.g('rugged laptop')
    m.box((2.2, 9.0, 3.0), (10.6, 9.7, 9.0), 'paint:#3b4a32', 'laptop base')
    for (x, z) in ((2.2, 3.0), (10.0, 3.0), (2.2, 8.4), (10.0, 8.4)):
        m.box((x, 9.0, z), (x + 0.6, 9.9, z + 0.6), 'rubber', 'corner bumper')
    for r in range(4):
        for c in range(11):
            m.box((2.8 + c * 0.66, 9.7, 3.8 + r * 0.6), (3.3 + c * 0.66, 9.8, 4.2 + r * 0.6), 'paint:#22261f', 'key')
    m.box((5.4, 9.7, 6.4), (7.4, 9.75, 7.6), 'paint:#1e221b', 'trackpad')
    m.box((2.2, 9.7, 8.4), (10.6, 15.4, 9.2), 'paint:#3b4a32', 'laptop lid')
    m.box((2.7, 10.2, 8.35), (10.1, 15.0, 8.4), 'screen:03140a', 'laptop screen', faces=('north',))
    m.box((2.2, 15.0, 8.2), (10.6, 15.4, 8.4), 'paint:#2c3826', 'lid bumper')
    m.nameplate(4.6, 9.75, 8.2, 10.15, 8.4, 'DIAG')
    m.g('test set')
    m.box((11.0, 9.0, 3.0), (14.2, 11.6, 9.0), 'paint:#ffb020', 'test set case')
    m.box((11.2, 11.6, 3.2), (14.0, 11.8, 8.8), PANEL, 'test set panel')
    m.box((11.0, 9.0, 2.8), (14.2, 9.6, 3.0), 'rubber', 'bumper')
    m.nameplate(11.3, 10.0, 13.9, 10.6, 3.0, 'TEST SET')
    m.g('meter leads + charger')
    m.box((11.4, 11.8, 9.0), (12.0, 12.0, 11.6), 'cable:c81e1e', 'red test lead')
    m.box((12.4, 11.8, 9.0), (13.0, 12.0, 11.6), 'cable:1d1d21', 'black test lead')
    m.cyl((11.7, 11.8, 11.6), 0.2, 0.8, 'y', 'paint:#c81e1e', 'probe')
    m.cyl((12.7, 11.8, 11.6), 0.2, 0.8, 'y', 'paint:#1d1d21', 'probe')
    m.box((3.0, 9.0, 10.0), (6.0, 9.9, 12.4), 'paint:#1a1a1a', 'charger brick')
    m.box((3.4, 9.9, 10.4), (5.6, 9.95, 11.0), 'label:230V', 'charger label')
    m.box((6.0, 9.2, 10.8), (10.4, 9.5, 11.1), 'cable:1d1d21', 'charger lead')
    m.cyl((4.5, 1.7, 13.0), 0.3, 7.3, 'y', 'cable:202020', 'mains lead down the column')
    m.g('power')
    mains_box(m, 13.6, 1.8, 11.0, face='east')
    m.terminal_12v(8.0, 1.8, 14.0, 'south')

    s = m.surface('north', face=8.3, title='LAPTOP')
    digits(m, s, 'hdr', X(6.4), 14.4, 7.0, col='ffd040', h=0.45)
    for i in range(6):
        digits(m, s, 'l%d' % (i + 1), X(6.4), 13.7 - i * 0.55, 7.0, col='b8f0c8' if i < 3 else '8ac8a0', h=0.4)
    digits(m, s, 'msg', X(6.4), 10.5, 7.0, col='ffb020', h=0.4)
    t = m.surface('up', face=16 - 11.8, title='TEST SET')
    m.ctl(t, 'gauge', 'meter', X(12.6), 7.6, w=2.2, h=2.2, col='f2f0e6', label='V x10', max=150)
    digits(m, t, 'v', X(12.6), 6.0, 2.4, col='ff4040', h=0.5)
    digits(m, t, 'time', X(12.6), 5.3, 2.4, col='7cf29a', h=0.45)
    m.ctl(t, 'digits', 'score', X(12.6), 4.6, w=2.4, h=0.45, col='ffd040', label='', fmt='%d PTS')
    lamp(m, t, 'st1', X(11.6), 3.8, 'ffb020', '1', 0.4)
    lamp(m, t, 'st2', X(12.3), 3.8, 'ffb020', '2', 0.4)
    lamp(m, t, 'st3', X(13.0), 3.8, 'ffb020', '3', 0.4)
    lamp(m, t, 'st4', X(13.7), 3.8, 'ffb020', '4', 0.4)
    # laptop keyboard row: function keys = the procedure (v = z on the up surface)
    k = m.surface('up', face=16 - 9.8, title='KEYS')
    for i, (cid, col, lab) in enumerate((('d_sup', 'f0d020', 'SUP'), ('d_wir', 'f0d020', 'WIR'), ('d_brn', 'f0d020', 'BRN'), ('d_lmp', 'f0d020', 'LMP'),
                                         ('d_det', 'f0d020', 'DET'), ('d_rad', 'f0d020', 'RAD'), ('d_pts', 'f0d020', 'PTS'))):
        push(m, k, cid, X(3.0 + i * 1.1), 3.4, col, lab, 0.6)
    for i, (cid, col, lab) in enumerate((('prev', '202020', '<'), ('next', '202020', '>'), ('take', '1fa34a', 'TAKE'), ('train', 'f08020', 'TRAIN'),
                                         ('repair', '2050d0', 'FIT'), ('verify', '1fa34a', 'VRFY'), ('abort', 'd02020', 'ABRT'), ('print', 'f0f0f0', 'PRNT'))):
        push(m, k, cid, X(2.9 + i * 1.0), 7.9, col, lab, 0.55)
    lamp(m, k, 'pwr', X(2.6), 7.1, '30e060', '', 0.3)
    lamp(m, k, 'mains', X(3.3), 7.1, '30e060', '', 0.3)
    lamp(m, k, 'charge', X(4.0), 7.1, 'ffb020', '', 0.3)
    lamp(m, k, 'done', X(9.2), 7.1, '30e060', '', 0.3)
    lamp(m, k, 'err', X(9.9), 7.1, 'ff3030', '', 0.3, blink=True)
    push(m, k, 'test', X(10.4), 3.4, 'f0f0f0', '', 0.4)
    fr = m.surface('north', face=2.8, title='STAND')
    zone(m, fr, 'drw', X(8.0), 7.4, 9.0, 1.8)
    zone(m, fr, 'case', X(8.0), 3.6, 10.0, 3.8)
    m.save(item_gui=0.55)
    facing_states('diagnostic_terminal')


# ===================================================================================================== #49 panel
def plate(m, legend, body='paint:#2f3338', face_col=PANEL):
    """the shared element plate: bezel, face, screws, engraved legend strip, 12 V terminal at the back, link studs"""
    m.g('plate')
    m.box((0, 0, 13.6), (16, 16, 16), body, 'element body')
    m.box((0.4, 0.4, 13.1), (15.6, 15.6, 13.6), face_col, 'element face')
    m.box((0, 0, 13.0), (16, 0.4, 13.6), 'paint:#1c1e21', 'bezel')
    m.box((0, 15.6, 13.0), (16, 16, 13.6), 'paint:#1c1e21', 'bezel')
    m.box((0, 0.4, 13.0), (0.4, 15.6, 13.6), 'paint:#1c1e21', 'bezel')
    m.box((15.6, 0.4, 13.0), (16, 15.6, 13.6), 'paint:#1c1e21', 'bezel')
    for x in (1.1, 14.9):
        for y in (1.1, 14.9):
            m.cyl((x, y, 13.05), 0.32, 0.1, 'z', 'metal:steel', 'face screw')
            m.box((x - 0.25, y - 0.04, 13.0), (x + 0.25, y + 0.04, 13.05), 'paint:#555555', 'screw slot')
    m.g('legend')
    m.box((3.0, 0.7, 13.05), (13.0, 1.9, 13.1), 'paint:#f2f0e6', 'legend strip')
    m.box((1.4, 13.6, 13.05), (3.6, 14.4, 13.1), 'label:BIND', 'bind label', faces=('north',))
    m.nameplate(5.2, 14.4, 10.8, 15.2, 13.1, legend)
    m.g('rear')
    for y in (4.0, 12.0):
        m.box((0.6, y, 16.0), (2.0, y + 1.0, 16.4), 'metal:copper', 'link stud (12 V through the panel)')
        m.box((14.0, y, 16.0), (15.4, y + 1.0, 16.4), 'metal:copper', 'link stud (12 V through the panel)')
    m.box((6.0, 0, 15.0), (10.0, 0.6, 16.0), 'paint:#1a1a1a', 'cable comb')
    m.terminal_12v(8.0, 6.0, 16.0, 'south')
    s = m.surface('north', face=12.95, title=legend)
    zone(m, s, 'bind', X(2.5), 14.0, 2.4, 1.0)
    digits(m, s, 'tag', 8.0, 1.3, 9.6, col='f0f0f0', h=0.7)
    lamp(m, s, 'pwr', X(14.6), 2.0, '30e060', '', 0.45)
    push(m, s, 'sel', X(1.6), 2.4, '202020', 'NEXT', 0.7)
    push(m, s, 'test', X(14.6), 14.0, 'f0f0f0', '', 0.45)
    return s


def bezel_ring(m, x, y, r, z=13.1, mat='metal:alu'):
    m.box((x - r, y - r, z - 0.25), (x + r, y - r + 0.35, z), mat, 'bezel ring')
    m.box((x - r, y + r - 0.35, z - 0.25), (x + r, y + r, z), mat, 'bezel ring')
    m.box((x - r, y - r + 0.35, z - 0.25), (x - r + 0.35, y + r - 0.35, z), mat, 'bezel ring')
    m.box((x + r - 0.35, y - r + 0.35, z - 0.25), (x + r, y + r - 0.35, z), mat, 'bezel ring')


def panel_button():
    m = Model('panel_button')
    s = plate(m, 'PUSH')
    m.g('button guard')
    bezel_ring(m, 8, 8.5, 3.0)
    m.box((4.6, 5.1, 12.4), (5.0, 11.9, 13.1), 'paint:#ffb020', 'guard post')
    m.box((11.0, 5.1, 12.4), (11.4, 11.9, 13.1), 'paint:#ffb020', 'guard post')
    m.ctl(s, 'push', 'btn', 8.0, 8.5, w=3.6, h=3.6, col='1fa34a', label='')
    bezel_ring(m, 8, 12.6, 0.9)
    lamp(m, s, 'ind', 8.0, 12.6, 'ffffff', '', 1.0)
    m.save(item_gui=0.6)
    facing_states('panel_button')


def panel_switch():
    m = Model('panel_switch')
    s = plate(m, 'SWITCH')
    m.g('switch escutcheon')
    bezel_ring(m, 8, 8.3, 3.2)
    for (x, y, t) in ((5.0, 11.6, 'N'), (10.0, 11.6, 'R')):
        m.box((x, y, 13.02), (x + 1.0, y + 0.9, 13.08), 'label:' + t, 'position engraving', faces=('north',))
    m.ctl(s, 'selector', 's_sw', 8.0, 8.3, w=3.4, h=3.4, col='202020', label='', pos=['N', 'R'])
    bezel_ring(m, 8, 3.5, 0.8)
    lamp(m, s, 'ind', 8.0, 3.5, 'ff3030', '', 0.9)
    m.save(item_gui=0.6)
    facing_states('panel_switch')


def panel_lamp():
    m = Model('panel_lamp')
    s = plate(m, 'INDICATION')
    m.g('lamp holders')
    for x in (5.0, 11.0):
        bezel_ring(m, x, 8.5, 2.2)
        m.cyl((x, 8.5, 12.7), 1.7, 0.4, 'z', 'paint:#1a1a1a', 'lamp holder')
    m.box((3.4, 5.2, 13.02), (6.6, 5.9, 13.08), 'label:ON', 'engraving', faces=('north',))
    m.box((9.4, 5.2, 13.02), (12.6, 5.9, 13.08), 'label:OFF', 'engraving', faces=('north',))
    lamp(m, s, 'on', X(5.0), 8.5, 'ff3030', '', 3.0)
    lamp(m, s, 'off', X(11.0), 8.5, '30e060', '', 3.0)
    m.save(item_gui=0.6)
    facing_states('panel_lamp')


def panel_gauge():
    m = Model('panel_gauge')
    s = plate(m, 'GAUGE')
    m.g('meter case')
    bezel_ring(m, 8, 9.3, 4.6, mat='paint:#1a1a1a')
    m.box((3.6, 4.9, 12.6), (12.4, 5.3, 13.1), 'paint:#1a1a1a', 'meter case foot')
    for x in (4.0, 12.0):
        for y in (5.4, 13.4):
            m.cyl((x, y, 12.75), 0.2, 0.1, 'z', 'metal:steel', 'meter screw')
    m.ctl(s, 'gauge', 'g', 8.0, 9.3, w=7.6, h=7.6, col='f2f0e6', label='', max=100)
    digits(m, s, 'val', 8.0, 3.9, 5.0, col='ff4040', h=0.9)
    m.ctl(s, 'selector', 's_rng', X(13.2), 4.2, w=1.2, h=1.2, col='202020', label='', pos=['10', '100', '1K', '10K'])
    m.save(item_gui=0.6)
    facing_states('panel_gauge')


def panel_mimic():
    m = Model('panel_mimic')
    s = plate(m, 'MIMIC', face_col='paint:#0b0d0c')
    m.g('mimic grid')
    for v in (4.0, 8.0, 12.0):
        m.box((2.0, v - 0.03, 13.06), (14.0, v + 0.03, 13.09), 'paint:#1c2420', 'grid line')
        m.box((v - 0.03, 2.4, 13.06), (v + 0.03, 13.4, 13.09), 'paint:#1c2420', 'grid line')
    # glyph spokes (drawn as coloured text by the tile: red occupied, white route set, grey idle)
    digits(m, s, 'mW', 4.4, 8.0, 6.4, col='c0c0c0', h=1.0)
    digits(m, s, 'mE', 11.6, 8.0, 6.4, col='c0c0c0', h=1.0)
    for i, d in enumerate((1.4, 2.8, 4.2)):
        for name, du, dv in (('NE', 1, 1), ('SE', 1, -1), ('SW', -1, -1), ('NW', -1, 1)):
            digits(m, s, 'm%s%d' % (name, i + 1), 8 + du * d, 8 + dv * d, 1.1, col='c0c0c0', h=1.0)
    digits(m, s, 'msig', 8.0, 10.6, 1.4, col='c0c0c0', h=1.0)
    for i, v in enumerate((6.8, 8.0, 9.2)):
        digits(m, s, 'mb%d' % (i + 1), 9.2, v, 1.0, col='c0c0c0', h=1.0)
    m.ctl(s, 'selector', 's_shape', 8.0, 3.6, w=1.0, h=1.0, col='202020', label='', pos=['ST', 'CL', 'CR', 'PL', 'PR', 'X', 'SG', 'BF'])
    m.save(item_gui=0.6)
    facing_states('panel_mimic')


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


RECIPES = {
    'driver_assessor': (['GCG', 'BRB', 'PIP'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'B': 'minecraft:book', 'R': 'minecraft:redstone', 'P': ('minecraft:planks', 0), 'I': 'minecraft:iron_ingot'}, 1),
    'diagnostic_terminal': (['GCQ', 'IRI', 'I I'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'Q': 'minecraft:clock', 'R': 'minecraft:redstone', 'I': 'minecraft:iron_ingot'}, 1),
    'panel_button': (['INI', 'IBI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:redstone', 'B': 'minecraft:stone_button'}, 4),
    'panel_switch': (['INI', 'ILI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:redstone', 'L': 'minecraft:lever'}, 4),
    'panel_lamp': (['INI', 'IGI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:redstone', 'G': 'minecraft:glowstone_dust'}, 4),
    'panel_gauge': (['INI', 'ICI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:redstone', 'C': 'minecraft:compass'}, 4),
    'panel_mimic': (['INI', 'IRI'], {'I': 'minecraft:iron_ingot', 'N': 'minecraft:redstone', 'R': 'minecraft:rail'}, 4),
}

LANG = {
    'tile.irextras.driver_assessor.name': 'Driver Competency Assessor', 'tile.irextras.diagnostic_terminal.name': 'Fault Diagnosis Terminal',
    'tile.irextras.panel_button.name': 'Control Panel Push Button', 'tile.irextras.panel_switch.name': 'Control Panel Turn Switch',
    'tile.irextras.panel_lamp.name': 'Control Panel Indication Lamps', 'tile.irextras.panel_gauge.name': 'Control Panel Gauge',
    'tile.irextras.panel_mimic.name': 'Control Panel Track Mimic',
}

if __name__ == '__main__':
    for fn in (assessor, diag_terminal, panel_button, panel_switch, panel_lamp, panel_gauge, panel_mimic):
        fn()
    for n, (pat, key, cnt) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': cnt}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# signalling batch 3 part C (#46 #48 #49)\n' + '\n'.join(add) + '\n')
    print('signalling batch 3 part C assets written')
