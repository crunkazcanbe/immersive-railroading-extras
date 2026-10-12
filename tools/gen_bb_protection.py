#!/usr/bin/env python3
"""IR Extras protection + grid automation gear (her ChatGPT list section 6: "electrical protection and grid
automation"; her ask: as detailed and realistic as possible, lots of working buttons, screens and lamps, everything on
12 V). Every control here is LIVE: the ids are read / worked by grid/GridGear.java and grid/TilePermitBoard.java.

  grid_ied_0/1       Protection IED cubicle: numerical relay (3-line LCD, 12 target LEDs, keypad, I / O keys,
                     IN SERVICE / TEST key), CT / VT terminal rail at the back, multicores down to the trench
  grid_ats_0/1       Transfer Switch Controller: mimic door (two sources, two breakers, the bus), source voltmeters,
                     status display, mode selector, TEST / XFER NOW / RESET / LAMP TEST, multicores to both breakers
  grid_pqa_0/1       Power Quality Analyzer cubicle: class-A analyser (4-line colour screen, page keys, limit LEDs),
                     U and THD meters, CT shorting + VT fuse test blocks, GPS time-sync antenna
  grid_ups_0/1       12 V Battery Charger / UPS: charger section (LCD, charge gauge, charger ammeter, 7 lamps, output
                     switch, BATTERY TEST / MUTE / RESET), open battery stand with six 2 V cells, links, DC fuses
  grid_restore_0/1   Black Start Panel: mimic board with five stage windows, frequency + generator load displays,
                     mode selector, START / NEXT / HOLD / ABORT, dead-grid and complete lamps
  permit_board       Permit-to-Work Board: key safe with 12 working hooks behind a glazed door, permit printer with a
                     paper tray, register display, procedure lamps (isolated / earthed / proven dead / locked off)
Front = north (z = 0). Run from tools/:  python3 gen_bb_protection.py
"""
import json, os
from bbkit import Model, ASSETS
from gen_bb_grid2 import live
from gen_bb_grid3 import path, seg, sign, oct_

STEEL, GALV, CU = 'metal:steel', 'galv', 'metal:copper'
GREY, DARK, BLACK = 'ral7035', 'paint:#2b2f33', 'black'
RED_CABLE, BLACK_CABLE, BLUE_CABLE, YEL_CABLE = 'cable:c81e1e', 'cable:1d1d21', 'cable:3060c0', 'cable:c0a020'
ON = {'GREEN': '30ff50', 'RED': 'ff3020', 'AMBER': 'ffb020', 'WHITE': 'f0f0e0', 'BLUE': '3080ff'}


# ------------------------------------------------------------------------------------------------ shared hardware
def cubicle(m, x0, x1, z0, z1, top, col=GREY, plinth=True, title=None):
    """a floor-standing steel cubicle: plinth, body, roof, lifting eyes, door seam, hinges, handle, louvres, earth bar"""
    g = m.group
    m.g('cubicle')
    if plinth:
        m.box((x0 + 0.2, 0, z0 + 0.2), (x1 - 0.2, 1.0, z1 - 0.2), BLACK, 'plinth', north='haz')
    m.box((x0, 1.0, z0), (x1, top, z1), col, 'cubicle body')
    m.box((x0 - 0.2, top, z0 - 0.3), (x1 + 0.2, top + 0.4, z1 + 0.3), col, 'roof')
    for (x, z) in ((x0 + 0.5, z0 + 0.5), (x1 - 0.5, z0 + 0.5), (x0 + 0.5, z1 - 0.5), (x1 - 0.5, z1 - 0.5)):
        m.cyl((x, top + 0.4, z), 0.3, 0.6, 'y', STEEL, 'lifting eye')
    for x in (x0 - 0.2, x1):
        for (y0, y1) in ((3.0, 6.0), (top - 6.0, top - 3.0)):
            m.box((x, y0, z0 + 1.5), (x + 0.2, y1, z1 - 1.5), 'louvre', 'side louvre')
    m.box((x0 + 0.4, 1.2, z0 - 0.15), (x1 - 0.4, 1.4, z0), DARK, 'door seam bottom')
    m.box((x0 + 0.4, top - 0.4, z0 - 0.15), (x1 - 0.4, top - 0.2, z0), DARK, 'door seam top')
    for y in (3.0, top - 4.0):
        m.box((x0 - 0.15, y, z0 - 0.1), (x0 + 0.15, y + 1.2, z0 + 0.4), STEEL, 'door hinge')
    m.box((x1 - 0.9, top * 0.45, z0 - 0.5), (x1 - 0.5, top * 0.45 + 2.0, z0), DARK, 'door handle')
    m.box((x1 - 1.0, top * 0.45 + 2.4, z0 - 0.2), (x1 - 0.4, top * 0.45 + 3.0, z0), 'metal:brass', 'door lock')
    if title: m.nameplate(x0 + 1.0, top - 2.2, x1 - 1.0, top - 0.9, z0, title)
    m.g('earthing')
    m.box((x0 + 0.6, 1.0, z1 - 0.6), (x0 + 1.0, 1.4, z1), CU, 'earth bar link')
    m.box((x0 + 0.6, 0.0, z1), (x0 + 1.0, 0.1, 16.0), CU, 'earth strap to grid')
    m.g(g)


def trench_cables(m, xs, z, cols, y_from=1.0):
    """multicores dropping out of the gland plate into the cable trench and away to the back"""
    g = m.group
    m.g('cable entry')
    for x, c in zip(xs, cols):
        m.cyl((x, y_from - 0.4, z), 0.32, 0.4, 'y', 'metal:brass', 'gland')
        path(m, [(x, y_from - 0.4, z), (x, -0.4, z), (x, -0.4, 16.0)], 0.2, c, 'multicore')
    m.box((min(xs) - 1.0, 0.0, z + 1.0), (max(xs) + 1.0, 0.12, 16.0), 'paint:#6a6e72', 'chequer trench cover')
    m.g(g)


def door_frame(m, x0, y0, x1, y1, z, col=DARK, t=0.3):
    """a raised bezel round an instrument or window cut-out (four strips)"""
    m.box((x0 - t, y0 - t, z - 0.25), (x1 + t, y0, z), col, 'bezel')
    m.box((x0 - t, y1, z - 0.25), (x1 + t, y1 + t, z), col, 'bezel')
    m.box((x0 - t, y0, z - 0.25), (x0, y1, z), col, 'bezel')
    m.box((x1, y0, z - 0.25), (x1 + t, y1, z), col, 'bezel')


def rivets(m, x0, x1, y0, y1, z, n=4, s=0.35):
    """screws round a panel's edge (on a north face at z)"""
    for i in range(n):
        x = x0 + (x1 - x0) * i / max(1, n - 1)
        m.bolt(x, y0, z, 'north', s); m.bolt(x, y1, z, 'north', s)
    for j in range(1, n - 1):
        y = y0 + (y1 - y0) * j / max(1, n - 1)
        m.bolt(x0, y, z, 'north', s); m.bolt(x1, y, z, 'north', s)


def heater_and_light(m, x, y, z):
    """inside every cubicle: anti-condensation heater, its thermostat and a door-switch lamp (seen through the mesh)"""
    m.box((x, y, z), (x + 3.0, y + 0.8, z + 0.6), 'paint:#8a8e92', 'anti-condensation heater')
    m.box((x + 3.4, y, z), (x + 4.4, y + 1.2, z + 0.6), 'paint:#e0e0dc', 'thermostat')
    m.box((x + 3.7, y + 0.4, z - 0.05), (x + 4.1, y + 0.8, z), 'paint:#3060c0', 'thermostat dial')
    m.box((x, y + 1.6, z), (x + 1.6, y + 2.4, z + 0.6), 'paint:#f0f0e0', 'cubicle lamp')


def mimic(m, a, b, z, col='paint:#e0e0e0', w=0.35):
    """a mimic-diagram line painted on the door (a strip standing a hair proud)"""
    lo = [min(a[0], b[0]), min(a[1], b[1])]
    hi = [max(a[0], b[0]), max(a[1], b[1])]
    if hi[0] - lo[0] < w: lo[0] -= w / 2; hi[0] += w / 2
    if hi[1] - lo[1] < w: lo[1] -= w / 2; hi[1] += w / 2
    m.box((lo[0], lo[1], z - 0.06), (hi[0], hi[1], z), col, 'mimic line', faces=('north',))


def blockstate_grid(name):
    """multipart like every other grid block: two state models + the cable-entry ports on connected sides"""
    parts = []
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for st in ('0', '1', '2'):
            a = {'model': 'irextras:%s_%s' % (name, '0' if st == '0' else '1')}
            if y: a['y'] = y
            parts.append({'when': {'facing': f, 'state': st}, 'apply': a})
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        a = {'model': 'irextras:grid_port_side'}
        if y: a['y'] = y
        parts.append({'when': {f: 'true'}, 'apply': a})
    json.dump({'multipart': parts}, open(os.path.join(ASSETS, 'blockstates', name + '.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/%s_1' % name}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


# ================================================================================================ PROTECTION IED
def ied(st):
    m = Model('grid_ied_%d' % st, block='grid_ied')
    cubicle(m, 1.0, 15.0, 3.0, 14.0, 26.0, title='FEEDER PROTECTION')
    z = 3.0
    m.g('relay case')
    m.box((3.0, 12.4, z - 0.9), (13.0, 23.6, z), DARK, 'IED case (flush mounted)')
    door_frame(m, 3.0, 12.4, 13.0, 23.6, z - 0.9, col='paint:#1c1f22', t=0.25)
    m.box((3.3, 23.0, z - 1.05), (12.7, 23.5, z - 0.9), 'label:MICOM-STYLE IED 50/51/50N/87T', 'model strip', faces=('north',))
    m.box((4.0, 19.9, z - 1.0), (12.0, 22.6, z - 0.9), 'paint:#0a2010' if not st else 'paint:#163a1c', 'LCD window', faces=('north',))
    door_frame(m, 4.0, 19.9, 12.0, 22.6, z - 0.9, col='paint:#111315', t=0.15)
    # LED legends (engraved strips beside each column)
    for i, txt in enumerate(('HEALTHY', 'TRIP', 'PICKUP', 'TEST', '64 DC E/F', '50BF')):
        m.box((10.3, 18.75 - i * 0.8, z - 1.0), (12.15, 19.25 - i * 0.8, z - 0.9), 'label:' + txt, 'LED legend', faces=('north',))
    for i, txt in enumerate(('50 I>>', '51 I>', '50N IN>', '87T DIFF', '27/59 U', '81 FREQ')):
        m.box((3.85, 18.75 - i * 0.8, z - 1.0), (5.95, 19.25 - i * 0.8, z - 0.9), 'label:' + txt, 'LED legend', faces=('north',))
    m.box((6.3, 15.3, z - 1.0), (9.7, 19.3, z - 0.92), 'paint:#3a3f44', 'keypad membrane', faces=('north',))
    m.box((3.4, 12.8, z - 1.0), (12.6, 14.6, z - 0.92), 'paint:#3a3f44', 'control key strip', faces=('north',))
    rivets(m, 3.2, 12.8, 12.6, 23.4, z - 0.9, n=2, s=0.3)
    m.g('door')
    m.box((3.4, 6.0, z - 0.1), (12.6, 11.2, z), 'paint:#c9cbc8', 'lower door panel', faces=('north',))
    m.box((4.0, 3.0, z - 0.7), (10.0, 5.6, z), 'paint:#30343a', 'trip-circuit supervision relay')
    m.box((4.4, 4.2, z - 0.8), (5.6, 5.2, z - 0.7), 'glass', 'TCS flag window', faces=('north',))
    m.box((4.5, 4.3, z - 0.75), (5.5, 4.7, z - 0.7), 'paint:#f0f0f0' if st else 'paint:#ff7020', 'TCS flag', faces=('north',))
    m.box((6.0, 3.4, z - 0.8), (9.6, 4.0, z - 0.7), 'label:TCS 74', 'TCS label', faces=('north',))
    for k in range(3):
        m.box((6.0 + k * 1.3, 4.4, z - 0.85), (6.9 + k * 1.3, 5.3, z - 0.7), 'paint:#20242a', 'aux trip relay')
    sign(m, 'north', (4.2, 6.6), (9.8, 8.4), z - 0.1, 'plate:CT 400/1A 5P20|VT 13.8KV/110V')
    sign(m, 'north', (4.2, 9.0), (9.8, 10.8), z - 0.1, 'danger:DANGER|CT CIRCUITS')
    m.box((11.6, 9.0, z - 0.4), (13.4, 11.0, z), 'paint:#3a3f44', 'key switch bezel')
    m.box((11.7, 3.0, z - 0.2), (13.3, 5.4, z), 'warn', 'trip-circuit warning', faces=('north', 'east', 'west', 'up', 'down'))
    heater_and_light(m, 3.0, 2.0, 13.0)
    m.g('rear terminal rail')
    m.box((2.0, 4.0, 14.0), (14.0, 22.0, 14.15), 'mesh', 'rear mesh door', south='mesh')
    for y in (6.0, 10.0, 14.0, 18.0):
        m.box((2.6, y, 13.2), (13.4, y + 0.5, 13.9), GALV, 'DIN rail')
        for k in range(12):
            col = ('paint:#808080', 'paint:#3060c0', 'paint:#c08020')[k % 3] if y < 12 else ('paint:#d0d0d0', 'paint:#c03030')[k % 2]
            m.box((2.8 + k * 0.88, y + 0.5, 13.0), (3.5 + k * 0.88, y + 1.6, 13.9), col, 'terminal block (CT / VT / trip)')
    for k, (x, c) in enumerate(((4.0, BLUE_CABLE), (6.0, BLACK_CABLE), (8.0, YEL_CABLE), (10.0, RED_CABLE), (12.0, BLACK_CABLE))):
        seg(m, (x, 1.0, 13.4), (x, 6.0, 13.4), 0.18, c, 'core loom')
    m.g('cable tray overhead')
    m.box((0.0, 27.6, 7.0), (16.0, 27.8, 10.2), GALV, 'ladder tray base')
    for zz in (7.0, 10.0):
        m.box((0.0, 27.6, zz), (16.0, 28.6, zz + 0.2), GALV, 'tray side rail')
    for x in (1.0, 4.0, 7.0, 10.0, 13.0):
        m.box((x, 27.8, 7.2), (x + 0.3, 27.9, 10.0), GALV, 'tray rung')
    for k, zz in enumerate((7.6, 8.3, 9.0, 9.6)):
        seg(m, (0.0, 28.05, zz), (16.0, 28.05, zz), 0.25, (BLACK_CABLE, BLUE_CABLE, BLACK_CABLE, YEL_CABLE)[k], 'tray cable')
    for x in (3.6, 12.4):
        m.box((x - 0.15, 26.8, 8.7), (x + 0.15, 27.6, 9.0), GALV, 'tray hanger')
    path(m, [(6.0, 27.6, 8.3), (6.0, 26.4, 8.3)], 0.2, BLUE_CABLE, 'drop to cubicle')
    trench_cables(m, (4.0, 6.0, 8.0, 10.0, 12.0), 11.5, (BLUE_CABLE, BLACK_CABLE, YEL_CABLE, RED_CABLE, BLACK_CABLE))
    m.terminal_12v(15.0, 2.5, 8.5, face='east')
    # ---- live controls
    f = z - 1.0
    for i, cid in enumerate(('lcd1', 'lcd2', 'lcd3')):
        live(m, 'north', 8.0, 22.05 - i * 0.82, f - 0.02, 'digits', cid, 7.6, 0.66, '9cff9c', '', fmt='%s')
    for i, (cid, col, blink) in enumerate((('lhealthy', ON['GREEN'], False), ('ltrip', ON['RED'], False), ('lpick', ON['AMBER'], True),
                                          ('ltest', ON['AMBER'], True), ('l64', ON['AMBER'], False), ('lbf', ON['RED'], False))):
        live(m, 'north', 12.6, 19.0 - i * 0.8, f, 'lamp', cid, 0.55, 0.55, col, '', **({'blink': True} if blink else {}))
    for i, cid in enumerate(('l50', 'l51', 'l50n', 'l87', 'l27', 'l81')):
        live(m, 'north', 3.4, 19.0 - i * 0.8, f, 'lamp', cid, 0.55, 0.55, ON['RED'], '')
    live(m, 'north', 8.0, 18.6, f, 'push', 'up', 0.9, 0.9, '2a2a2a', '▲')
    live(m, 'north', 8.0, 16.0, f, 'push', 'down', 0.9, 0.9, '2a2a2a', '▼')
    live(m, 'north', 9.3, 17.3, f, 'push', 'esc', 0.9, 0.9, '2a2a2a', 'ESC')
    live(m, 'north', 6.7, 17.3, f, 'push', 'enter', 0.9, 0.9, '2a2a2a', 'ENT')
    live(m, 'north', 11.4, 13.7, f, 'push', 'reset', 1.1, 1.1, '2050d0', 'RESET')
    live(m, 'north', 8.0, 13.7, f, 'push', 'close', 1.1, 1.1, '1fa34a', 'I CLOSE')
    live(m, 'north', 4.6, 13.7, f, 'push', 'trip', 1.1, 1.1, 'd02020', 'O OPEN')
    live(m, 'north', 12.5, 10.0, z - 0.42, 'key', 'mode', 1.3, 1.3, 'c0a040', 'MODE', pos=['IN SVC', 'TEST'])
    m.save(item_gui=0.45)


# ================================================================================================ TRANSFER SWITCH
def ats(st):
    m = Model('grid_ats_%d' % st, block='grid_ats')
    cubicle(m, 1.5, 14.5, 5.0, 13.0, 24.0, title='AUTOMATIC TRANSFER SWITCH')
    z = 5.0
    m.g('mimic door')
    m.box((2.0, 4.2, z - 0.1), (14.0, 21.6, z), 'paint:#d8dad6', 'mimic panel', faces=('north',))
    blk = 'paint:#202020'
    # two sources: A on the viewer's left (x high), B on the right (x low) - feeders drop to the bus
    for xs in (11.5, 4.5):
        mimic(m, (xs, 20.6), (xs, 13.6), z - 0.1, blk)
        m.box((xs - 0.9, 14.6, z - 0.25), (xs + 0.9, 16.4, z - 0.1), 'paint:#f0f0f0', 'breaker symbol', faces=('north',))
        door_frame(m, xs - 0.9, 14.6, xs + 0.9, 16.4, z - 0.1, col=blk, t=0.18)
    mimic(m, (4.5, 13.6), (11.5, 13.6), z - 0.1, 'paint:#c81e1e')
    mimic(m, (8.0, 13.6), (8.0, 12.2), z - 0.1, blk)
    m.box((7.4, 11.6, z - 0.2), (8.6, 12.2, z - 0.1), blk, 'load arrow', faces=('north',))
    m.box((10.0, 21.0, z - 0.2), (13.0, 21.5, z - 0.1), 'label:SOURCE A NORMAL', 'mimic legend', faces=('north',))
    m.box((3.0, 21.0, z - 0.2), (6.0, 21.5, z - 0.1), 'label:SOURCE B STANDBY', 'mimic legend', faces=('north',))
    m.box((6.6, 11.0, z - 0.2), (9.4, 11.45, z - 0.1), 'label:ESSENTIAL BUS', 'mimic legend', faces=('north',))
    m.box((2.4, 4.6, z - 0.2), (13.6, 6.8, z - 0.1), 'paint:#3a3f44', 'control strip', faces=('north',))
    rivets(m, 2.2, 13.8, 4.4, 21.4, z - 0.1, n=4, s=0.3)
    for xs in (11.5, 4.5):                                         # little isolator blades on the mimic, closed
        m.box((xs - 0.12, 19.0, z - 0.2), (xs + 0.12, 20.0, z - 0.1), 'paint:#202020', 'mimic isolator', faces=('north',))
    sign(m, 'north', (2.4, 1.6), (6.4, 3.8), z, 'danger:DANGER|TWO SUPPLIES')
    sign(m, 'north', (9.6, 1.6), (13.6, 3.8), z, 'plate:INTERLOCKED|CHANGEOVER')
    m.g('control multicores to the incomers')
    for x0, x1 in ((14.5, 16.0), (0.0, 1.5)):                 # out of both sides to the incomers beside it
        gx = x0 if x0 > 8 else x1 - 0.5
        m.cyl((gx, 8.0, 9.0), 0.4, 0.5, 'x', 'metal:brass', 'gland')
        seg(m, (x0 + (0.5 if x0 > 8 else 0), 8.0, 9.0), (x1 - (0 if x0 > 8 else 0.5), 8.0, 9.0), 0.2, YEL_CABLE, 'control multicore (trip / close / status)')
        seg(m, (x0 + (0.5 if x0 > 8 else 0), 7.2, 9.0), (x1 - (0 if x0 > 8 else 0.5), 7.2, 9.0), 0.16, BLACK_CABLE, 'VT voltage sensing')
    m.terminal_12v(8.0, 1.2, 13.0, face='south')
    m.g('inside')
    heater_and_light(m, 3.0, 2.0, 12.0)
    for k in range(4):
        m.box((3.0 + k * 2.5, 14.0, 12.0), (4.8 + k * 2.5, 17.0, 12.8), 'paint:#20242a', 'interposing relay')
    m.box((4.0, 24.4, 6.0), (6.0, 25.6, 8.0), DARK, 'sounder')
    m.cyl((5.0, 25.0, 5.4), 0.6, 0.6, 'z', 'mesh', 'sounder grille')
    trench_cables(m, (4.0, 8.0, 12.0), 11.0, (YEL_CABLE, BLACK_CABLE, YEL_CABLE))
    # ---- live controls (u = 16 - x)
    f = z - 0.27
    live(m, 'north', 11.5, 19.6, f, 'lamp', 'la', 0.9, 0.9, ON['GREEN'], 'A AVAIL')
    live(m, 'north', 4.5, 19.6, f, 'lamp', 'lb', 0.9, 0.9, ON['GREEN'], 'B AVAIL')
    live(m, 'north', 11.5, 15.5, f - 0.05, 'lamp', 'lca', 0.9, 0.9, ON['RED'], '')
    live(m, 'north', 4.5, 15.5, f - 0.05, 'lamp', 'lcb', 0.9, 0.9, ON['RED'], '')
    live(m, 'north', 11.5, 17.6, f, 'gauge', 'ga', 2.0, 2.0, 'f2f0e6', 'U A %', max=120)
    live(m, 'north', 4.5, 17.6, f, 'gauge', 'gb', 2.0, 2.0, 'f2f0e6', 'U B %', max=120)
    live(m, 'north', 11.5, 20.6, f, 'digits', 'va', 4.6, 0.6, '9cff9c', '', fmt='%s')
    live(m, 'north', 4.5, 20.6, f, 'digits', 'vb', 4.6, 0.6, '9cff9c', '', fmt='%s')
    live(m, 'north', 8.0, 19.4, f, 'lamp', 'lauto', 0.7, 0.7, ON['WHITE'], 'AUTO')
    live(m, 'north', 8.0, 18.2, f, 'lamp', 'lgen', 0.7, 0.7, ON['AMBER'], 'GEN START')
    live(m, 'north', 8.0, 17.0, f, 'lamp', 'ltimer', 0.7, 0.7, ON['AMBER'], 'TIMING', blink=True)
    live(m, 'north', 8.0, 15.8, f, 'lamp', 'lfault', 0.7, 0.7, ON['RED'], 'LOCKOUT', blink=True)
    live(m, 'north', 8.0, 10.2, f, 'digits', 'status', 9.0, 0.8, 'ffb020', '', fmt='%s')
    live(m, 'north', 8.0, 9.2, f, 'digits', 'timer', 9.0, 0.7, 'ffd27a', '', fmt='%s')
    live(m, 'north', 12.6, 5.7, f, 'selector', 'mode', 1.5, 1.5, '202020', 'MODE', pos=['OFF', 'AUTO', 'MAN A', 'MAN B'])
    live(m, 'north', 9.9, 5.7, f, 'push', 'test', 1.1, 1.1, 'f0d020', 'TEST')
    live(m, 'north', 7.8, 5.7, f, 'push', 'retx', 1.1, 1.1, '2050d0', 'XFER NOW')
    live(m, 'north', 5.7, 5.7, f, 'push', 'reset', 1.1, 1.1, '2050d0', 'RESET')
    live(m, 'north', 3.6, 5.7, f, 'push', 'lamptest', 1.1, 1.1, 'f0f0f0', 'LAMP TEST')
    live(m, 'north', 2.6, 8.2, f, 'lamp', 'lpwr', 0.5, 0.5, ON['GREEN'], '12V')
    m.save(item_gui=0.45)


# ================================================================================================ POWER QUALITY
def pqa(st):
    m = Model('grid_pqa_%d' % st, block='grid_pqa')
    cubicle(m, 2.0, 14.0, 4.0, 13.0, 23.0, title='METERING - POWER QUALITY')
    z = 4.0
    m.g('analyser')
    m.box((3.4, 13.0, z - 0.8), (12.6, 21.0, z), 'paint:#e6e6e2', 'class-A analyser case')
    door_frame(m, 3.4, 13.0, 12.6, 21.0, z - 0.8, col='paint:#9a9c98', t=0.2)
    m.box((4.0, 16.4, z - 0.9), (12.0, 20.4, z - 0.8), 'paint:#081018' if not st else 'paint:#0e2236', 'colour TFT', faces=('north',))
    m.box((4.0, 20.45, z - 0.9), (8.0, 20.85, z - 0.8), 'label:PQ CLASS A IEC 61000-4-30', 'model strip', faces=('north',))
    rivets(m, 3.6, 12.4, 13.2, 20.8, z - 0.8, n=2, s=0.3)
    m.g('meters')
    for x in (5.5, 10.5):
        m.box((x - 2.0, 7.6, z - 0.5), (x + 2.0, 11.6, z), 'paint:#1c1d20', 'switchboard meter case')
    m.g('test blocks')
    for i in range(2):
        x0 = 4.0 + i * 4.6
        m.box((x0, 3.6, z - 0.6), (x0 + 3.6, 6.0, z), 'paint:#30343a', 'test block' if i else 'CT shorting block')
        for k in range(6):
            m.box((x0 + 0.3 + k * 0.55, 4.2, z - 0.9), (x0 + 0.6 + k * 0.55, 5.4, z - 0.6), ('paint:#c03030' if k < 3 else 'paint:#2050c0') if i == 0 else 'paint:#d0d0d0', 'test link')
    sign(m, 'north', (4.0, 6.2), (7.6, 7.2), z, 'label:CT SHORT')
    sign(m, 'north', (8.6, 6.2), (12.2, 7.2), z, 'label:VT FUSES')
    m.g('GPS time sync')
    m.cyl((8.0, 23.4, 8.0), 0.9, 0.5, 'y', 'paint:#f0f0ea', 'GPS antenna puck')
    m.cyl((8.0, 23.9, 8.0), 0.6, 0.2, 'y', 'paint:#e0e0da', 'antenna dome')
    path(m, [(8.0, 23.4, 9.0), (8.0, 23.4, 11.8), (8.0, 22.0, 13.2)], 0.12, BLACK_CABLE, 'antenna lead')
    m.g('CT / VT wiring')
    for x, c in ((3.0, RED_CABLE), (3.6, YEL_CABLE), (4.2, BLUE_CABLE), (4.8, BLACK_CABLE)):
        path(m, [(x, 1.0, 12.0), (x, -0.4, 12.0), (x, -0.4, 16.0)], 0.17, c, 'CT / VT secondary')
    m.box((2.4, 0.0, 12.8), (13.6, 0.12, 16.0), 'paint:#6a6e72', 'chequer trench cover')
    m.terminal_12v(14.0, 2.5, 8.5, face='east')
    heater_and_light(m, 3.5, 2.0, 12.0)
    m.g('rear')
    m.box((2.6, 13.0, 12.4), (13.4, 21.0, 12.9), 'paint:#20242a', 'analyser rear (Ethernet, IRIG-B, CT inputs)')
    for k in range(6):
        m.box((3.2 + k * 1.6, 14.0, 12.2), (4.2 + k * 1.6, 15.0, 12.4), ('paint:#c0a020', 'paint:#3060c0')[k % 2], 'rear terminal')
    path(m, [(8.0, 22.0, 13.2), (8.0, 21.2, 13.2), (8.0, 21.2, 12.9)], 0.12, BLACK_CABLE, 'antenna lead in')
    # ---- live controls
    f = z - 0.92
    for i in range(4):
        live(m, 'north', 8.0, 19.75 - i * 0.95, f, 'digits', 'l%d' % (i + 1), 7.6, 0.8, ('7fd7ff', 'ffffff', 'ffffff', 'ffd27a')[i], '', fmt='%s')
    for i, (cid, lab) in enumerate((('av', 'U'), ('af', 'F'), ('au', 'UNBAL'), ('ah', 'THD'), ('ap', 'PF'))):
        live(m, 'north', 11.6 - i * 1.3, 15.6, f, 'lamp', cid, 0.5, 0.5, ON['AMBER'], lab)
    live(m, 'north', 4.8, 15.6, f, 'lamp', 'lev', 0.5, 0.5, ON['RED'], 'EVENT', blink=True)
    live(m, 'north', 3.95, 15.6, f, 'lamp', 'lpwr', 0.4, 0.4, ON['GREEN'], '')
    for i, (cid, col, lab) in enumerate((('page', '2a2a2a', 'PAGE'), ('trig', 'f0d020', 'CAPTURE'), ('ack', '2a2a2a', 'ACK'), ('reset', '2050d0', 'RESET'))):
        live(m, 'north', 11.2 - i * 2.1, 14.1, f, 'push', cid, 0.95, 0.95, col, lab)
    live(m, 'north', 10.5, 9.6, z - 0.52, 'gauge', 'gv', 3.4, 3.4, 'f2f0e6', 'U %', max=120)
    live(m, 'north', 5.5, 9.6, z - 0.52, 'gauge', 'gthd', 3.4, 3.4, 'f2f0e6', 'THD x0.1%', max=100)
    m.save(item_gui=0.45)


# ================================================================================================ 12 V UPS
def ups(st):
    m = Model('grid_ups_%d' % st, block='grid_ups')
    m.g('frame')
    m.box((1.0, 0, 3.0), (15.0, 0.8, 15.0), BLACK, 'plinth')
    for x in (1.0, 14.4):
        for zz in (3.0, 14.4):
            m.box((x, 0.8, zz), (x + 0.6, 24.0, zz + 0.6), 'paint:#3a3f44', 'corner post')
    m.box((1.0, 13.2, 3.0), (15.0, 14.0, 15.0), 'paint:#3a3f44', 'mid frame')
    m.box((1.6, 0.8, 14.4), (14.4, 13.2, 15.0), 'paint:#2b2f33', 'battery stand back')
    m.g('charger section')
    m.box((1.0, 14.0, 3.0), (15.0, 24.0, 15.0), GREY, 'charger cabinet')
    m.box((0.8, 24.0, 2.7), (15.2, 24.4, 15.3), GREY, 'roof')
    m.box((15.0, 15.0, 5.0), (15.6, 23.0, 13.0), 'fins', 'heatsink fins', faces=('east', 'up', 'down', 'north', 'south'))
    m.box((0.6, 16.0, 6.0), (1.0, 22.0, 12.0), 'louvre', 'intake louvre', faces=('west',))
    m.box((1.4, 14.4, 2.85), (14.6, 23.6, 3.0), 'paint:#c9cbc8', 'charger door', faces=('north',))
    m.box((1.8, 21.4, 2.75), (10.2, 23.4, 2.85), 'paint:#141a14' if not st else 'paint:#2a2410', 'LCD window', faces=('north',))
    door_frame(m, 1.8, 21.4, 10.2, 23.4, 2.85, col='paint:#2b2f33', t=0.2)
    m.nameplate(10.8, 22.4, 14.2, 23.4, 2.85, '12V UPS')
    m.box((14.0, 17.0, 2.4), (14.4, 19.4, 2.85), DARK, 'door handle')
    for y in (15.0, 22.4):
        m.box((0.85, y, 2.8), (1.2, y + 1.0, 3.2), STEEL, 'door hinge')
    m.g('battery stand')
    for y in (0.8, 7.0):
        m.box((1.6, y, 3.4), (14.4, y + 0.4, 14.4), 'paint:#2b2f33', 'battery shelf')
        m.box((1.6, y + 0.4, 3.4), (14.4, y + 0.55, 14.4), 'rubber', 'acid-proof mat')
    for row, y in enumerate((1.35, 7.55)):
        for k in range(3):
            x0 = 2.2 + k * 4.0
            m.box((x0, y, 5.0), (x0 + 3.4, y + 4.8, 12.6), 'battery', '2 V lead-acid cell', north='label:2V CELL|VRLA')
            m.box((x0 + 0.3, y + 4.8, 5.3), (x0 + 3.1, y + 5.1, 12.3), 'paint:#30343a', 'cell lid')
            m.cyl((x0 + 0.8, y + 5.1, 7.0), 0.35, 0.5, 'y', 'paint:#c81e1e', '+ post')
            m.cyl((x0 + 2.6, y + 5.1, 7.0), 0.35, 0.5, 'y', 'paint:#1d1d21', '- post')
            m.cyl((x0 + 1.7, y + 5.1, 10.4), 0.3, 0.4, 'y', 'paint:#e0e0e0', 'vent plug')
            if k < 2: m.box((x0 + 2.5, y + 5.4, 6.8), (x0 + 4.9, y + 5.6, 7.2), CU, 'inter-cell link')
        m.box((2.2, y + 4.9, 4.2), (14.0, y + 5.3, 4.6), 'paint:#f0d020', 'cell number strip', north='label:1  2  3  4  5  6')
    path(m, [(12.6, 6.5, 7.0), (14.0, 6.5, 7.0), (14.0, 12.9, 7.0), (11.4, 12.9, 7.0)], 0.22, RED_CABLE, 'series link between shelves')
    rivets(m, 1.4, 14.6, 14.6, 23.75, 2.85, n=2, s=0.3)
    m.g('battery isolator + shunt')
    m.box((2.0, 13.3, 3.2), (4.0, 13.9, 4.0), 'paint:#c81e1e', 'battery fuse switch')
    m.box((10.0, 13.3, 12.8), (13.0, 13.9, 13.6), CU, 'current shunt')
    m.g('DC distribution')
    m.box((15.0, 2.0, 4.0), (15.8, 12.0, 8.0), DARK, 'DC fuse board')
    for i in range(4):
        m.box((15.8, 3.0 + i * 2.2, 4.6), (16.0, 4.6 + i * 2.2, 7.4), 'paint:#e0e0d8', 'DC fuse carrier')
    sign(m, 'east', (9.0, 9.6), (13.0, 11.6), 15.0, 'warn:')
    for i, (y, c) in enumerate(((3.6, RED_CABLE), (5.8, BLACK_CABLE))):
        path(m, [(15.8, y, 9.0), (16.0, y, 9.0)], 0.22, c, '12 V DC out')
    m.g('AC input')
    m.box((6.0, 18.0, 15.0), (10.0, 21.0, 15.8), DARK, 'AC input box', south='label:AC IN|240V')
    m.cyl((8.0, 17.4, 15.4), 0.4, 0.6, 'y', 'metal:brass', 'gland')
    path(m, [(8.0, 17.4, 15.4), (8.0, 15.0, 15.4), (8.0, 15.0, 16.0)], 0.25, BLACK_CABLE, 'mains cable')
    m.terminal_12v(4.0, 14.6, 15.0, face='south')
    # ---- live controls
    f = 2.72
    for i, cid in enumerate(('lcd1', 'lcd2', 'lcd3')):
        live(m, 'north', 6.0, 22.95 - i * 0.62, f, 'digits', cid, 7.8, 0.52, 'ffb020', '', fmt='%s')
    live(m, 'north', 12.4, 21.4, f, 'digits', 'soc', 2.6, 0.8, '9cff9c', '', fmt='%d%%')
    live(m, 'north', 11.2, 18.6, 2.84, 'gauge', 'gsoc', 2.6, 2.6, 'f2f0e6', 'BATT %', max=100)
    live(m, 'north', 4.8, 18.6, 2.84, 'gauge', 'gamps', 2.6, 2.6, 'f2f0e6', 'CHG A', max=40)
    live(m, 'north', 8.0, 19.6, 2.84, 'lamp', 'lac', 0.6, 0.6, ON['GREEN'], 'AC OK')
    live(m, 'north', 8.0, 18.6, 2.84, 'lamp', 'lbat', 0.6, 0.6, ON['AMBER'], 'ON BATT', blink=True)
    live(m, 'north', 8.0, 17.6, 2.84, 'lamp', 'llow', 0.6, 0.6, ON['RED'], 'BATT LOW', blink=True)
    for i, (cid, col, lab) in enumerate((('lout', ON['GREEN'], 'DC OUT'), ('lcf', ON['RED'], 'CHGR FLT'), ('lef', ON['AMBER'], 'EARTH FLT'), ('ltf', ON['RED'], 'TEST FAIL'))):
        live(m, 'north', 12.5 - i * 3.0, 16.3, 2.84, 'lamp', cid, 0.55, 0.55, col, lab)
    live(m, 'north', 13.0, 14.9, 2.84, 'selector', 'out', 1.2, 1.2, '202020', 'OUTPUT', pos=['OFF', 'ON'])
    for i, (cid, col, lab) in enumerate((('test', 'f0d020', 'BATT TEST'), ('mute', '2a2a2a', 'MUTE'), ('reset', '2050d0', 'RESET'), ('lamptest', 'f0f0f0', 'LAMP TEST'))):
        live(m, 'north', 10.4 - i * 2.4, 14.9, 2.84, 'push', cid, 0.9, 0.9, col, lab)
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 12.0, 14.6, 23.4, 1.0, side='west', title='12V UPS|CHARGER', volts='240V AC 12V DC', fns=(('ctl', '12V AUX'), ('out', 'DC OUT')))
    m.save(item_gui=0.45)


# ================================================================================================ BLACK START PANEL
def restore(st):
    m = Model('grid_restore_%d' % st, block='grid_restore')
    cubicle(m, 0.5, 15.5, 4.0, 13.0, 26.0, col='paint:#d6d8d2', title='GRID RESTORATION - BLACK START')
    z = 4.0
    m.g('mimic board')
    m.box((1.0, 5.0, z - 0.1), (15.0, 23.4, z), 'paint:#3c4a3e', 'mimic board', faces=('north',))
    w, g = 'paint:#e8e8e0', 'paint:#c81e1e'
    # generators at the top, through the stages to the traction feeders at the bottom
    for x in (2.2, 4.0):
        m.cyl((x, 22.2, z - 0.3), 0.8, 0.2, 'z', 'paint:#e8e8e0', 'generator symbol')
        m.box((x - 0.3, 22.1, z - 0.35), (x + 0.3, 22.3, z - 0.3), 'paint:#202020', 'generator symbol tilde', faces=('north',))
        mimic(m, (x, 21.4), (x, 19.6), z - 0.1, w)
    mimic(m, (2.0, 19.6), (14.0, 19.6), z - 0.1, g)
    for x, y1 in ((4.0, 16.6), (7.0, 16.6), (10.0, 16.6)):
        mimic(m, (x, 19.6), (x, y1), z - 0.1, w)
    for x in (7.0, 10.0):
        m.cyl((x, 16.0, z - 0.3), 0.55, 0.2, 'z', 'paint:#e8e8e0', 'transformer winding')
        m.cyl((x, 15.2, z - 0.3), 0.55, 0.2, 'z', 'paint:#e8e8e0', 'transformer winding')
    mimic(m, (2.0, 14.0), (14.0, 14.0), z - 0.1, 'paint:#40a0ff')
    mimic(m, (7.0, 14.6), (7.0, 14.0), z - 0.1, w); mimic(m, (10.0, 14.6), (10.0, 14.0), z - 0.1, w)
    for x in (3.0, 6.0, 10.0, 13.0):
        mimic(m, (x, 14.0), (x, 12.8), z - 0.1, w)
        m.box((x - 0.3, 12.5, z - 0.2), (x + 0.3, 12.8, z - 0.1), w, 'feeder arrow', faces=('north',))
    m.box((2.0, 11.9, z - 0.2), (14.0, 12.4, z - 0.1), 'label:AUX    FEEDERS    TRACTION', 'mimic legend', faces=('north',))
    m.box((1.4, 5.4, z - 0.25), (14.6, 9.0, z - 0.1), 'paint:#2b2f33', 'control strip', faces=('north',))
    rivets(m, 1.2, 14.8, 5.2, 23.2, z - 0.1, n=5, s=0.3)
    heater_and_light(m, 2.0, 2.0, 12.0)
    m.g('procedure card holder')
    m.box((15.5, 8.0, 5.0), (15.9, 20.0, 12.0), 'paint:#2b2f33', 'card holder')
    m.box((15.9, 9.0, 5.6), (16.0, 19.0, 11.4), 'label:BLACK START|1 GENSETS|2 AUX|3 TX|4 FEEDERS|5 TRACTION', 'procedure card', faces=('east',))
    m.terminal_12v(0.5, 2.5, 8.5, face='west')
    trench_cables(m, (3.0, 5.5, 8.0, 10.5, 13.0), 11.5, (YEL_CABLE, BLACK_CABLE, BLUE_CABLE, BLACK_CABLE, YEL_CABLE))
    # ---- live controls
    f = z - 0.22
    for i, (cid, lab) in enumerate((('s1', '1 GENSETS'), ('s2', '2 AUX'), ('s3', '3 TRANS-|FORMERS'), ('s4', '4 FEEDERS'), ('s5', '5 TRACTION'))):
        live(m, 'north', 13.4 - i * 2.7, 10.8, f, 'tile', cid, 2.4, 1.7, ('f0f0e0', 'f0f0e0', 'f0f0e0', 'f0f0e0', 'ffb020')[i], lab)
    live(m, 'north', 8.0, 22.6, f, 'digits', 'stage', 6.0, 0.8, 'ffb020', '', fmt='%s')
    live(m, 'north', 11.0, 21.5, f, 'digits', 'freq', 4.0, 0.7, '9cff9c', '', fmt='%s')
    live(m, 'north', 11.0, 20.6, f, 'digits', 'gen', 4.0, 0.7, '9cff9c', '', fmt='%s')
    live(m, 'north', 8.0, 8.5, f - 0.15, 'digits', 'msg', 12.0, 0.7, 'ffd27a', '', fmt='%s')
    live(m, 'north', 13.4, 16.8, f, 'gauge', 'gload', 2.4, 2.4, 'f2f0e6', 'GEN %', max=120)
    live(m, 'north', 3.0, 17.4, f, 'lamp', 'ldead', 0.8, 0.8, ON['RED'], 'DEAD GRID', blink=True)
    live(m, 'north', 5.2, 17.4, f, 'lamp', 'lhold', 0.8, 0.8, ON['AMBER'], 'HOLD')
    live(m, 'north', 3.0, 15.8, f, 'lamp', 'ldone', 0.8, 0.8, ON['GREEN'], 'RESTORED')
    live(m, 'north', 5.2, 15.8, f, 'lamp', 'lfault', 0.8, 0.8, ON['RED'], 'FAULT', blink=True)
    live(m, 'north', 14.4, 7.2, f - 0.15, 'selector', 'mode', 1.5, 1.5, '202020', 'MODE', pos=['OFF', 'STEP', 'AUTO'])
    for i, (cid, col, lab) in enumerate((('start', '1fa34a', 'START'), ('next', 'f0f0f0', 'NEXT'), ('hold', 'f0a020', 'HOLD'), ('abort', 'd02020', 'ABORT'), ('lamptest', 'f0f0f0', 'LAMP TEST'))):
        live(m, 'north', 11.6 - i * 2.2, 7.2, f - 0.15, 'push', cid, 1.2, 1.2, col, lab)
    live(m, 'north', 1.8, 6.0, f - 0.15, 'lamp', 'lpwr', 0.45, 0.45, ON['GREEN'], '')
    m.save(item_gui=0.4)


# ================================================================================================ PERMIT BOARD
HOOKS = [(16 - (3.6 + c * 3.0), 18.6 - r * 3.4) for r in range(3) for c in range(4)]   # (u, v) of each hook peg


def permit_board():
    m = Model('permit_board')
    m.g('cabinet')
    m.box((0.5, 0, 10.0), (15.5, 0.6, 16.0), BLACK, 'plinth')
    m.box((0.5, 0.6, 10.0), (15.5, 27.0, 16.0), 'paint:#2c5a3a', 'cabinet body')
    m.box((0.3, 27.0, 9.6), (15.7, 27.5, 16.0), 'paint:#2c5a3a', 'cabinet top')
    m.box((0.5, 23.2, 9.8), (15.5, 26.8, 10.0), 'paint:#f0f0ea', 'header board', north='label:PERMIT TO WORK|ELECTRICAL SAFETY')
    m.g('key safe')
    m.box((1.4, 11.0, 12.6), (14.6, 21.6, 12.8), 'paint:#e8e6dc', 'key safe back board', faces=('north',))
    for r in range(3):
        y = 18.6 - r * 3.4
        m.box((1.6, y + 0.9, 12.4), (14.4, y + 1.1, 12.6), 'paint:#c8c4b0', 'hook rail')
    for i, (u, v) in enumerate(HOOKS):
        x = 16 - u
        m.box((x - 0.12, v + 0.6, 11.2), (x + 0.12, v + 0.84, 12.6), 'metal:brass', 'hook peg')
        m.box((x - 0.12, v + 0.6, 11.2), (x + 0.12, v + 1.1, 11.44), 'metal:brass', 'hook tip')
        m.box((x - 0.6, v + 1.25, 12.5), (x + 0.6, v + 1.65, 12.6), 'label:%d' % (i + 1), 'hook number', faces=('north',))
    # the glazed door: frame and glazing bars, clear glass (the clicks reach the hooks, as in a real key cabinet you open)
    for (a, b) in (((1.0, 10.6), (15.0, 11.0)), ((1.0, 21.6), (15.0, 22.0)), ((1.0, 11.0), (1.4, 21.6)), ((14.6, 11.0), (15.0, 21.6))):
        m.box((a[0], a[1], 9.6), (b[0], b[1], 10.0), 'paint:#1e3c28', 'door frame')
    for x in (4.9, 7.9, 10.9):                                     # glazing bars (the glass itself is clear)
        m.box((x - 0.08, 11.0, 9.7), (x + 0.08, 21.6, 9.8), 'paint:#1e3c28', 'glazing bar')
    m.box((14.0, 15.0, 9.3), (14.4, 17.0, 9.6), STEEL, 'door handle')
    m.box((13.9, 17.4, 9.4), (14.5, 18.0, 9.6), 'metal:brass', 'key safe lock')
    for y in (12.0, 20.0):
        m.box((0.85, y, 9.5), (1.15, y + 1.0, 10.1), STEEL, 'door hinge')
    m.g('control panel')
    m.box((0.5, 0.6, 9.8), (15.5, 10.6, 10.0), 'paint:#30343a', 'lower panel')
    m.box((1.4, 7.8, 9.7), (14.6, 10.0, 9.8), 'paint:#081018', 'register display window', faces=('north',))
    m.box((1.4, 1.0, 9.4), (6.4, 4.4, 9.8), 'paint:#d8d8d4', 'permit printer')
    m.box((1.8, 3.4, 9.3), (6.0, 3.7, 9.4), 'paint:#1a1a1a', 'printer paper slot')
    m.box((2.0, 3.45, 8.9), (5.8, 3.55, 9.3), 'paint:#fbfbf6', 'printout lip')
    m.box((1.4, 4.4, 9.5), (6.4, 5.2, 9.8), 'paint:#bcbcb8', 'paper tray')
    m.box((1.8, 4.8, 9.45), (6.0, 5.1, 9.5), 'paint:#fbfbf6', 'paper stack edge')
    rivets(m, 0.8, 15.2, 0.9, 10.3, 9.8, n=5, s=0.3)
    m.box((10.0, 22.2, 9.6), (15.0, 23.0, 9.8), 'label:KEY SAFE - 1 KEY PER HOOK', 'key safe label')
    m.box((1.0, 22.2, 9.6), (9.0, 23.0, 9.8), 'label:ISOLATE EARTH PROVE LOCK', 'procedure strip')
    m.g('wall fixing + cables')
    for (x, y) in ((1.5, 25.5), (14.5, 25.5), (1.5, 2.0), (14.5, 2.0)):
        m.bolt(x, y, 16.0, 'south', 0.5)
    m.terminal_12v(15.5, 2.0, 13.0, face='east')
    sign(m, 'east', (8.0, 14.0), (14.0, 20.0), 15.5, 'danger:NO PERMIT|NO WORK')
    # ---- live controls (u = 16 - x)
    f = 9.68
    for i, (u, v) in enumerate(HOOKS):
        live(m, 'north', 16 - u, v - 0.3, 9.55, 'zone', 'k%d' % (i + 1), 2.6, 3.0, '000000', '')
    live(m, 'north', 3.9, 4.8, 9.45, 'zone', 'tray', 4.6, 1.2, '000000', '')
    live(m, 'north', 8.0, 9.4, f, 'digits', 'reg1', 12.6, 0.8, '9cff9c', '', fmt='%s')
    live(m, 'north', 8.0, 8.45, f, 'digits', 'reg2', 12.6, 0.8, '9cff9c', '', fmt='%s')
    live(m, 'north', 11.7, 6.9, f, 'digits', 'count', 3.0, 0.7, 'ffb020', '', fmt='%d LIVE')
    live(m, 'north', 11.7, 6.1, f, 'digits', 'paper', 3.0, 0.6, 'ffd27a', '', fmt='%d SHEETS')
    for i, (lab) in enumerate(('ISOLATED', 'EARTHED', 'PROVEN DEAD', 'LOCKED OFF')):
        u = 9.6 + i * 1.6
        live(m, 'north', 16 - u, 6.9, f, 'lamp', 's%d' % (i + 1), 0.6, 0.6, ON['GREEN'], lab)
        live(m, 'north', 16 - u, 6.1, f, 'lamp', 'x%d' % (i + 1), 0.6, 0.6, ON['RED'], '')
    for i, (cid, col, lab) in enumerate((('print', 'f0f0f0', 'PRINT'), ('check', 'f0d020', 'CHECK'), ('issue', '1fa34a', 'ISSUE'), ('cancel', 'd02020', 'CANCEL'),
                                         ('prev', '2a2a2a', '◄'), ('next', '2a2a2a', '►'))):
        live(m, 'north', 16 - (1.7 + i * 1.4), 3.6, f, 'push', cid, 1.0, 1.0, col, lab)
    live(m, 'north', 16 - 1.7, 1.6, f, 'push', 'lamptest', 0.8, 0.8, 'f0f0f0', 'TEST')
    for i, (cid, col, lab, blink) in enumerate((('lsafe', ON['RED'], 'SAFE LOCKED', False), ('llive', ON['AMBER'], 'PERMITS LIVE', True), ('lpaper', ON['AMBER'], 'PAPER', False), ('lpwr', ON['GREEN'], '12V', False))):
        live(m, 'north', 16 - (3.4 + i * 1.6), 1.6, f, 'lamp', cid, 0.55, 0.55, col, lab, **({'blink': True} if blink else {}))
    m.save(item_gui=0.4)
    bs = {'variants': {'facing=north': {'model': 'irextras:permit_board'}, 'facing=east': {'model': 'irextras:permit_board', 'y': 90},
                       'facing=south': {'model': 'irextras:permit_board', 'y': 180}, 'facing=west': {'model': 'irextras:permit_board', 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates/permit_board.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/permit_board'}, open(os.path.join(ASSETS, 'models/item/permit_board.json'), 'w'), indent=1)


# ================================================================================================ items, recipes
def item_textures():
    from PIL import Image, ImageDraw
    d = os.path.join(ASSETS, 'textures/items')
    # permit form: paper, red header band, ruled lines, a signature and a stamp box
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); g = ImageDraw.Draw(im)
    g.rectangle((2, 1, 13, 14), fill=(246, 244, 236, 255), outline=(170, 168, 160, 255))
    g.rectangle((3, 2, 12, 4), fill=(200, 30, 30, 255))
    for y in (6, 8, 10): g.line((4, y, 11, y), fill=(120, 130, 150, 255))
    g.rectangle((8, 11, 11, 13), outline=(30, 140, 60, 255))
    g.line((4, 12, 6, 11), fill=(40, 40, 120, 255))
    im.save(os.path.join(d, 'work_permit.png'))
    # lockout key: brass key with a red danger tag on its ring
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); g = ImageDraw.Draw(im)
    g.ellipse((2, 2, 7, 7), outline=(200, 160, 40, 255), width=2)
    g.line((6, 6, 12, 12), fill=(210, 170, 50, 255), width=2)
    g.line((10, 12, 11, 13), fill=(180, 140, 30, 255)); g.line((12, 10, 13, 11), fill=(180, 140, 30, 255))
    g.rectangle((1, 8, 4, 13), fill=(210, 30, 30, 255)); g.line((2, 10, 3, 10), fill=(255, 255, 255, 255))
    im.save(os.path.join(d, 'lockout_key.png'))
    # laptop: grey shell, screen with a time-current plot
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); g = ImageDraw.Draw(im)
    g.rectangle((2, 2, 13, 10), fill=(50, 54, 60, 255)); g.rectangle((3, 3, 12, 9), fill=(14, 24, 40, 255))
    g.line((4, 4, 6, 7), fill=(80, 220, 120, 255)); g.line((6, 7, 11, 8), fill=(80, 220, 120, 255))
    g.line((4, 5, 7, 8), fill=(255, 170, 40, 255)); g.point((9, 5), fill=(255, 60, 60, 255))
    g.polygon([(1, 11), (14, 11), (15, 13), (0, 13)], fill=(120, 124, 130, 255)); g.line((5, 12, 10, 12), fill=(80, 84, 90, 255))
    im.save(os.path.join(d, 'protection_laptop.png'))
    for n in ('work_permit', 'lockout_key', 'protection_laptop'):
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + n}}, open(os.path.join(ASSETS, 'models/item', n + '.json'), 'w'), indent=1)


def recipes():
    d = os.path.join(ASSETS, 'recipes')
    P = lambda n: {'item': 'irextras:part', 'data': n}
    SCREEN, CIRCUIT, CONTACTOR, FRAME, WINDING, MOVEMENT, ANTENNA, BATTERY, DIODE, PLATE = 20, 2, 18, 1, 13, 21, 25, 22, 17, 0
    rs = {
        'grid_ied': (['SCS', 'CRC', 'TFT'], {'S': P(SCREEN), 'C': P(CIRCUIT), 'R': {'item': 'irextras:grid_relay'}, 'T': P(CONTACTOR), 'F': P(FRAME)}),
        'grid_ats': (['SCS', 'TFT', 'CWC'], {'S': P(SCREEN), 'C': P(CIRCUIT), 'T': P(CONTACTOR), 'F': P(FRAME), 'W': P(WINDING)}),
        'grid_pqa': (['SMS', 'CFC', 'WAW'], {'S': P(SCREEN), 'M': P(MOVEMENT), 'C': P(CIRCUIT), 'F': P(FRAME), 'W': P(WINDING), 'A': P(ANTENNA)}),
        'grid_ups': (['CSC', 'BDB', 'BFB'], {'C': P(CIRCUIT), 'S': P(SCREEN), 'B': P(BATTERY), 'D': P(DIODE), 'F': P(FRAME)}),
        'grid_restore': (['SSS', 'CFC', 'TMT'], {'S': P(SCREEN), 'C': P(CIRCUIT), 'F': P(FRAME), 'T': P(CONTACTOR), 'M': P(MOVEMENT)}),
        'permit_board': (['GSG', 'PFP', 'GCG'], {'G': {'item': 'minecraft:glass_pane'}, 'S': P(SCREEN), 'P': {'item': 'minecraft:paper'}, 'F': P(FRAME), 'C': P(CIRCUIT)}),
        'protection_laptop': (['SSS', 'CCC', 'LBL'], {'S': P(SCREEN), 'C': P(CIRCUIT), 'L': P(PLATE), 'B': P(BATTERY)}),
    }
    for name, (pat, key) in rs.items():
        json.dump({'type': 'forge:ore_shaped', 'pattern': pat, 'key': key, 'result': {'item': 'irextras:' + name, 'count': 1}},
                  open(os.path.join(d, name + '.json'), 'w'), indent=1)


if __name__ == '__main__':
    for st in (0, 1):
        ied(st); ats(st); pqa(st); ups(st); restore(st)
    for n in ('grid_ied', 'grid_ats', 'grid_pqa', 'grid_ups', 'grid_restore'):
        blockstate_grid(n)
    permit_board()
    item_textures()
    recipes()
    print('protection gear assets written')
