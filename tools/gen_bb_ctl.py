#!/usr/bin/env python3
"""Grid, control and comms gear (her "next 48" list #20 #21 #23 #25 #30 #43): Harmonic Spectrum Meter, Harmonic Filter
Bank, Microgrid Controller, Field RTU, PIS Server, PIS platform sign, Approval Panel, Production Planning Console.
Models face north; controls by u = 16 - x (X()). Every lamp, LED bar segment, button, key, selector, dial, synchroscope,
display and drawer is a live control the Java tile reads or works (grid/TileSpectrumMeter, TileFilterBank, TileMicrogrid,
wire/TileRtu, station/TilePisServer, TilePisBoard, admin/TileApprovalPanel, factory/TilePlanner). Also writes the
blockstates, item models, recipes and lang lines; the fault lab console gains its CHAOS scenario controls in
gen_bb_signalling.py."""
import json, os
from bbkit import Model, ASSETS

X = lambda u: 16 - u
GREY = 'ral7035'
DARK = 'paint:#2a2d30'
PANEL = 'paint:#c9cbc7'


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


def digits(m, sf, cid, u, v, w, col='7cf29a', h=0.75, fmt='%s'):
    m.ctl(sf, 'digits', cid, u, v, w=w, h=h, col=col, label='', fmt=fmt)


def label(m, sf, u, v, text, col='e0e0e0'):
    m.ctl(sf, 'label', '', u, v, w=0, h=0, col=col, label=text)


def plinth(m, x0, x1, z0, z1, h=1.0):
    m.box((x0, 0, z0), (x1, h, z1), DARK, 'plinth')
    m.box((x0, h, z0 - 0.15), (x1, h + 0.3, z0), 'haz', 'kick strip', faces=('north',))


def door_hardware(m, x, y0, y1, z, hinge_x):
    m.box((x - 0.2, (y0 + y1) / 2 - 1.5, z - 0.4), (x + 0.2, (y0 + y1) / 2 + 1.5, z), 'metal:steel', 'door handle')
    m.box((x - 0.3, (y0 + y1) / 2 + 2.0, z - 0.15), (x + 0.3, (y0 + y1) / 2 + 2.6, z), 'metal:brass', 'cam lock')
    for y in (y0 + 1.5, y1 - 2.5):
        m.box((hinge_x - 0.25, y, z - 0.3), (hinge_x + 0.25, y + 1.2, z), 'metal:steel', 'hinge')


def glands(m, xs, y, z, r=0.35):
    for x in xs:
        m.gland(x, y, z, 'down', r=r)


def keyboard(m, x0, x1, y, z0, z1, rows=4):
    """a keyboard on a desk: base and real keycaps"""
    m.box((x0, y, z0), (x1, y + 0.25, z1), 'paint:#1a1a1c', 'keyboard base')
    n = int((x1 - x0 - 0.3) / 0.55)
    for r in range(rows):
        for k in range(n):
            if r == rows - 1 and 3 <= k < n - 3:
                if k == 3: m.box((x0 + 0.2 + 3 * 0.55, y + 0.25, z0 + 0.15 + r * 0.55), (x0 + 0.2 + (n - 3) * 0.55 - 0.08, y + 0.4, z0 + 0.6 + r * 0.55), 'paint:#3a3d42', 'space bar')
                continue
            m.box((x0 + 0.2 + k * 0.55, y + 0.25, z0 + 0.15 + r * 0.55), (x0 + 0.67 + k * 0.55, y + 0.4, z0 + 0.62 + r * 0.55), 'paint:#3a3d42', 'keycap')


def chair(m, x, z, y0=0.0):
    """an operator's chair in front of the desk (5-star base, gas lift, seat, back, arms)"""
    for (dx, dz) in ((1.6, 0), (-1.6, 0), (0.5, 1.5), (0.5, -1.5), (-1.3, 1.0)):
        m.box((x + min(0, dx), y0 + 0.4, z + min(0, dz) - 0.15), (x + max(0, dx) + 0.01, y0 + 0.7, z + max(0, dz) + 0.15), 'paint:#1a1a1c', 'chair leg')
        m.cyl((x + dx, y0, z + dz), 0.25, 0.4, 'y', 'rubber', 'castor')
    m.cyl((x, y0 + 0.7, z), 0.25, 3.0, 'y', 'metal:steel', 'gas lift')
    m.box((x - 1.6, y0 + 3.7, z - 1.6), (x + 1.6, y0 + 4.3, z + 1.6), 'paint:#2a3a5a', 'seat')
    m.box((x - 1.5, y0 + 4.3, z - 2.0), (x + 1.5, y0 + 8.0, z - 1.5), 'paint:#2a3a5a', 'back rest')
    for sx in (-1.8, 1.6):
        m.box((x + sx, y0 + 4.3, z - 1.2), (x + sx + 0.2, y0 + 5.6, z - 0.9), 'paint:#1a1a1c', 'arm post')
        m.box((x + sx - 0.1, y0 + 5.6, z - 1.4), (x + sx + 0.3, y0 + 5.9, z + 0.8), 'paint:#1a1a1c', 'arm rest')


def terminal_blocks(m, x0, y, z, n, face_z_out=-0.1):
    """a DIN rail of terminal blocks (front at z)"""
    m.box((x0, y - 0.15, z), (x0 + n * 0.45 + 0.2, y + 0.15, z + 0.2), 'metal:steel', 'DIN rail')
    for i in range(n):
        m.box((x0 + 0.1 + i * 0.45, y - 0.6, z + face_z_out), (x0 + 0.5 + i * 0.45, y + 0.6, z), 'paint:#8a8c90' if i % 4 else 'paint:#2050d0', 'terminal block')


# ============================================================================================ #20 spectrum meter
SPEC = [3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25]


def spectrum_meter():
    m = Model('spectrum_meter')
    plinth(m, 1.0, 15.0, 2.2, 15.0)
    m.g('cabinet')
    m.box((1.0, 1.0, 2.4), (15.0, 24.0, 15.0), GREY, 'cabinet')
    m.box((0.8, 24.0, 2.2), (15.2, 24.6, 15.2), GREY, 'roof')
    for z in (5.0, 9.0, 13.0):
        m.box((0.7, 3.0, z), (1.0, 22.0, z + 0.5), GREY, 'side stiffener')
        m.box((15.0, 3.0, z), (15.3, 22.0, z + 0.5), GREY, 'side stiffener')
    m.louvres(1.6, 4.4, 1.6, 4.2, 2.4, n=3)
    m.louvres(11.6, 14.4, 1.6, 4.2, 2.4, n=3)
    m.g('instrument bezel')
    m.box((1.4, 8.4, 2.1), (14.6, 23.4, 2.4), 'paint:#1c1f22', 'instrument bezel')
    m.box((2.0, 12.0, 2.02), (14.0, 20.2, 2.1), 'paint:#050806', 'bar graph window')
    m.box((2.0, 20.6, 2.02), (14.0, 22.9, 2.1), 'paint:#03140a', 'top display')
    m.box((2.0, 9.1, 2.02), (14.0, 11.8, 2.1), 'paint:#03140a', 'lower display')
    for x in (1.7, 14.3):
        for y in (8.8, 23.0):
            m.bolt(x, y, 2.1, 'north', 0.35)
    for i in range(9):                                                   # graticule lines behind the LEDs
        y = 12.3 + i * 0.95
        m.box((2.1, y, 2.01), (13.9, y + 0.05, 2.02), 'paint:#1d3324', 'graticule')
    m.nameplate(4.5, 23.45, 11.5, 23.95, 2.1, 'HARMONIC ANALYSER')
    m.g('control panel')
    m.box((1.4, 1.6, 2.2), (14.6, 8.2, 2.4), PANEL, 'control plate')
    m.box((1.2, 8.2, 2.0), (14.8, 8.4, 2.4), 'metal:steel', 'plate rail')
    m.g('measuring inputs')
    for i, (lab, col) in enumerate((('V1', '#c81e1e'), ('V2', '#f0d020'), ('V3', '#2050d0'), ('N', '#1a1a1a'), ('I1', '#c81e1e'), ('I2', '#f0d020'), ('I3', '#2050d0'))):
        z = 4.0 + i * 1.4
        m.cyl((15.0, 6.0, z), 0.3, 0.4, 'x', 'paint:' + col, 'input socket ' + lab)
        m.box((15.05, 6.6, z - 0.4), (15.1, 7.2, z + 0.4), 'label:' + lab, 'socket label', faces=('east',))
    m.box((15.0, 4.6, 3.2), (15.4, 8.0, 13.6), 'paint:#2b2f33', 'input terminal strip')
    m.g('CT leads')
    for i in range(3):
        m.box((15.4, 5.8 - i * 0.3, 8.6 + i * 1.4), (16.0, 6.0 - i * 0.3, 8.8 + i * 1.4), 'cable:c81e1e' if i == 0 else 'cable:f0d020' if i == 1 else 'cable:2050d0', 'CT lead')
    m.box((2.0, 13.0, 15.0), (14.0, 18.0, 15.2), 'danger:CT CIRCUITS|NEVER OPEN', 'warning label', faces=('south',))
    m.terminal_12v(4.0, 1.6, 15.0, 'south')
    glands(m, (6.0, 8.0, 10.0, 12.0), 1.0, 12.0)
    m.g('printer')
    m.box((9.5, 24.6, 4.0), (14.5, 25.8, 9.0), 'paint:#d8d8d4', 'event printer')
    m.box((10.0, 25.2, 3.9), (14.0, 25.4, 4.0), 'paint:#1a1a1a', 'paper slot')
    m.box((10.2, 25.25, 3.2), (13.8, 25.3, 3.9), 'paint:#fbfbf6', 'printout')
    m.box((2.0, 24.6, 4.0), (8.0, 25.0, 12.0), 'paint:#1c1f22', 'gps time sync module')
    m.cyl((5.0, 25.0, 8.0), 0.8, 1.0, 'y', 'paint:#e8e8e4', 'gps antenna')
    # ---- the live front
    f = m.surface('north', face=2.0, title='SPECTRUM')
    for k, h in enumerate(SPEC):
        u = X(2.6 + k * 0.98)
        for s in range(1, 9):
            col = '30e060' if s <= 4 else 'ffb020' if s <= 6 else 'ff3030'
            m.ctl(f, 'lamp', 'b%d_%d' % (h, s), u, 12.75 + (s - 1) * 0.92, w=0.72, h=0.62, col=col, label='')
        m.ctl(f, 'lamp', 'c%d' % h, u, 12.2, w=0.4, h=0.25, col='f0f0f0', label='')
        label(m, f, u, 11.85, str(h), '9ad8a8')
    digits(m, f, 'net', 8.0, 22.4, 11.6, '5bcefa', 0.6)
    digits(m, f, 'thdv', X(5.0), 21.4, 5.4, 'ffd040', 0.8)
    digits(m, f, 'thdi', X(11.0), 21.4, 5.0, '7cf29a', 0.8)
    digits(m, f, 'cur', 8.0, 11.2, 11.6, 'b8f0c8', 0.6)
    digits(m, f, 'restxt', 8.0, 10.4, 11.6, 'ff8060', 0.6)
    digits(m, f, 'mode', 8.0, 9.6, 11.6, '8ac8a0', 0.55)
    p = m.surface('north', face=2.15, title='CONTROLS')
    m.ctl(p, 'selector', 's_mode', X(3.0), 6.6, w=1.1, h=1.1, col='202020', label='MODE', pos=['V', 'I'])
    m.ctl(p, 'selector', 's_std', X(5.4), 6.6, w=1.1, h=1.1, col='202020', label='LIMIT', pos=['519', 'G54', 'STR'])
    m.ctl(p, 'selector', 's_range', X(7.8), 6.6, w=1.1, h=1.1, col='202020', label='RANGE', pos=['1', '2', '5', '10'])
    m.ctl(p, 'gauge', 'g_thd', X(12.0), 6.2, w=2.6, h=2.6, col='f2f0e6', label='THD 0-10%', max=100)
    for (cid, x, col, lab) in (('hl', 2.8, '1a1a1a', 'H<'), ('hr', 4.3, '1a1a1a', 'H>'), ('hold', 5.8, 'f0d020', 'HOLD'), ('reset', 7.3, '2050d0', 'PEAK'), ('ack', 8.8, '1fa34a', 'ACK')):
        push(m, p, cid, X(x), 3.6, col, lab, 0.95)
    push(m, p, 'test', X(10.2), 3.6, 'f0f0f0', 'LAMP', 0.7)
    for (cid, x, col, lab, bl) in (('pwr', 11.4, '30e060', '12V', False), ('lim', 12.4, 'ff3030', 'LIMIT', True), ('res', 13.4, 'ff8020', 'RES', True), ('lhold', 11.4, 'f0d020', 'HOLD', False), ('relay', 12.4, 'ff3030', 'RELAY', False)):
        lamp(m, p, cid, X(x), 3.9 if cid in ('pwr', 'lim', 'res') else 2.3, col, lab, 0.55, bl)
    m.save(item_gui=0.45)
    facing_states('spectrum_meter')


# ============================================================================================ #20 filter bank
def filter_bank():
    m = Model('filter_bank')
    plinth(m, 0.2, 15.8, 0.8, 15.8)
    m.g('cubicle')
    m.box((0.2, 1.0, 1.0), (15.8, 26.0, 15.8), GREY, 'cubicle')
    m.box((0.0, 26.0, 0.8), (16.0, 26.6, 16.0), GREY, 'roof')
    m.box((0.4, 26.6, 2.0), (15.6, 27.0, 15.0), 'paint:#9a9c98', 'roof vent hood')
    m.g('capacitor compartment (door with window)')
    m.box((0.6, 10.0, 0.8), (15.4, 25.6, 1.0), GREY, 'upper door')
    m.box((2.0, 13.0, 0.7), (10.0, 24.0, 0.8), 'glass', 'viewing window')
    door_hardware(m, 14.4, 10.0, 25.6, 0.8, 0.9)
    for i in range(6):                                                    # capacitor cans seen through the window
        x = 2.8 + (i % 3) * 2.5
        y = 14.0 if i < 3 else 19.0
        m.box((x, y, 2.0), (x + 1.9, y + 4.0, 4.4), 'metal:steel', 'capacitor can')
        m.cyl((x + 0.55, y + 4.0, 3.2), 0.25, 0.6, 'y', 'porcelain', 'can bushing')
        m.cyl((x + 1.35, y + 4.0, 3.2), 0.25, 0.6, 'y', 'porcelain', 'can bushing')
        m.box((x + 0.5, y + 4.6, 3.1), (x + 1.4, y + 4.75, 3.3), 'metal:copper', 'can link')
        m.box((x + 0.2, y + 1.0, 1.95), (x + 1.7, y + 2.2, 2.0), 'label:50KVAR', 'can rating')
    m.box((2.4, 23.2, 2.9), (10.2, 23.4, 3.5), 'metal:copper', 'capacitor busbar')
    m.g('reactor (air-core, behind the mesh)')
    m.box((10.6, 13.0, 0.75), (14.2, 24.0, 0.85), 'mesh', 'reactor mesh')
    for k in range(5):
        m.cyl((12.4, 14.0 + k * 1.8, 5.0), 1.4 - (k % 2) * 0.15, 1.4, 'y', 'cable:7a5a2a', 'reactor winding')
    m.cyl((12.4, 13.4, 5.0), 0.3, 11.0, 'y', 'porcelain', 'reactor support')
    m.box((0.6, 25.0, 0.6), (15.4, 25.6, 0.8), 'danger:DANGER 11000 VOLTS', 'danger sign')
    m.g('contactor + relay compartment')
    m.box((0.6, 1.4, 0.8), (15.4, 9.8, 1.0), PANEL, 'control door')
    m.box((0.4, 9.8, 0.6), (15.6, 10.0, 1.0), 'metal:steel', 'door rail')
    m.nameplate(1.2, 9.0, 8.6, 9.6, 0.8, 'HARMONIC FILTER')
    m.g('cable boxes')
    m.hv_box(8.0, 1.0, 15.8, 'south', phases=3)
    m.terminal_12v(14.0, 1.6, 15.8, 'south')
    for z in (4.0, 8.0, 12.0):
        m.box((15.8, 6.0, z), (16.0, 22.0, z + 0.6), GREY, 'side stiffener')
        m.box((0.0, 6.0, z), (0.2, 22.0, z + 0.6), GREY, 'side stiffener')
    m.louvres(1.4, 6.0, 1.6, 3.6, 0.8, n=2)
    m.box((0.4, 4.0, 2.0), (0.6, 8.0, 6.0), 'label:EARTH|BAR', 'earth label', faces=('west',))
    f = m.surface('north', face=0.75, title='FILTER CONTROL')
    m.ctl(f, 'selector', 's_tune', X(2.2), 7.4, w=1.2, h=1.2, col='202020', label='TUNING', pos=['5', '7', '11', '13', 'HP'])
    m.ctl(f, 'selector', 's_size', X(4.6), 7.4, w=1.2, h=1.2, col='202020', label='KVAR', pos=['250', '500', '1000', '2000'])
    m.ctl(f, 'key', 'k_lr', X(7.0), 7.4, w=1.1, h=1.1, col='c0a040', label='', pos=['LOCAL', 'REMOTE'])
    m.ctl(f, 'gauge', 'g_ih', X(10.4), 7.3, w=2.2, h=2.2, col='f2f0e6', label='DUTY %', max=150)
    m.ctl(f, 'gauge', 'g_temp', X(13.4), 7.3, w=2.2, h=2.2, col='f2f0e6', label='TEMP C', max=120)
    for (cid, x, col, lab) in (('close', 2.0, '1fa34a', 'CLOSE'), ('open', 3.6, 'd02020', 'OPEN'), ('reset', 5.2, '2050d0', 'RESET'), ('recan', 6.8, 'f0f0f0', 'CANS')):
        push(m, f, cid, X(x), 4.6, col, lab, 1.0)
    push(m, f, 'test', X(8.2), 4.6, 'f0f0f0', 'LAMP', 0.7)
    for (cid, x, col, lab, bl) in (('closed', 9.4, 'ff3030', 'IN', False), ('open', 10.4, '30e060', 'OUT', False), ('trip', 11.4, 'ffb020', 'TRIP', True),
                                   ('ovl', 12.4, 'ff3030', 'OVL', True), ('detune', 13.4, 'ffb020', 'DETUNE', False), ('live', 14.4, '30a0ff', 'BUS', False)):
        lamp(m, f, cid if cid != 'open' else 'openl', X(x), 4.9, col, lab, 0.55, bl)
    lamp(m, f, 'pwr', X(14.4), 3.4, '30e060', '12V', 0.5)
    digits(m, f, 'kvar', X(5.0), 2.8, 7.0, 'ffd040', 0.6)
    digits(m, f, 'tune', X(5.0), 2.1, 7.0, '7cf29a', 0.55)
    digits(m, f, 'ih', X(11.4), 2.8, 6.4, 'b8f0c8', 0.6)
    digits(m, f, 'cap', X(11.4), 2.1, 6.4, 'b8f0c8', 0.55)
    digits(m, f, 'state', 8.0, 1.6, 13.0, 'ff8060', 0.45)
    m.save(item_gui=0.4)
    facing_states('filter_bank')


# ============================================================================================ #21 microgrid controller
def microgrid():
    m = Model('microgrid_controller')
    m.g('desk')
    m.box((0, 8.0, 1.0), (16, 8.8, 8.0), 'paint:#3e4248', 'desk top')
    m.box((-0.2, 7.8, 0.8), (16.2, 8.0, 8.2), 'paint:#24262a', 'desk edge')
    for x in (0.4, 12.6):
        m.box((x, 0, 1.6), (x + 3.0, 7.8, 7.8), 'paint:#33363b', 'pedestal')
    m.box((0, 0, 1.8), (16, 0.4, 7.8), 'paint:#1a1a1c', 'kick plinth')
    dr = m.surface('north', face=1.45, title='DRAWERS')
    for (x, zid, lab) in ((0.4, 'dr1', 'SWITCHING LOG'), (12.6, 'dr2', 'SPARES')):
        for k in range(3):
            y0 = 0.6 + k * 2.4
            m.box((x + 0.2, y0, 1.5), (x + 2.8, y0 + 2.1, 1.6), 'paint:#40444a', 'drawer front')
            m.box((x + 1.0, y0 + 1.3, 1.3), (x + 2.0, y0 + 1.55, 1.5), 'metal:alu', 'drawer pull')
        m.box((x + 0.4, 7.0, 1.48), (x + 2.6, 7.5, 1.5), 'label:' + lab, 'drawer label')
        m.ctl(dr, 'zone', zid, X(x + 1.5), 3.9, w=2.6, h=7.0, col='000000', label='')
    m.g('control strip')
    m.box((0.8, 8.8, 1.2), (15.2, 9.0, 6.6), PANEL, 'control strip')
    m.box((0.6, 8.8, 1.0), (15.4, 9.05, 1.2), 'metal:steel', 'strip edge')
    m.g('mimic board')
    m.box((0.2, 8.8, 9.0), (15.8, 24.0, 10.6), 'paint:#22252a', 'mimic board frame')
    m.box((0.6, 9.4, 8.9), (15.4, 23.6, 9.0), 'paint:#3a5a3a', 'mimic face')
    m.box((0.0, 24.0, 8.8), (16.0, 24.4, 10.8), 'paint:#22252a', 'board top')
    m.nameplate(4.0, 23.65, 12.0, 24.15, 8.9, 'MICROGRID CONTROL')
    # one-line diagram painted on the mimic: utility bus, intertie, local bus, feeders
    W = 'paint:#f0f0e8'
    m.box((2.0, 22.0, 8.86), (14.0, 22.3, 8.9), W, 'utility line')
    m.box((7.9, 18.8, 8.86), (8.1, 22.0, 8.9), W, 'intertie drop')
    m.box((1.4, 18.5, 8.86), (14.6, 18.8, 8.9), 'paint:#ffd040', 'local bus')
    for x in (2.6, 5.0, 11.0, 13.4):
        m.box((x - 0.1, 14.6, 8.86), (x + 0.1, 18.5, 8.9), W, 'feeder line')
    m.box((3.6, 18.8, 8.86), (3.8, 21.0, 8.9), W, 'genset riser')
    m.box((12.2, 18.8, 8.86), (12.4, 21.0, 8.9), W, 'battery riser')
    m.box((3.0, 20.8, 8.85), (4.4, 21.6, 8.9), 'label:G', 'genset symbol')
    m.box((11.6, 20.8, 8.85), (13.0, 21.6, 8.9), 'label:BESS', 'battery symbol')
    m.box((6.6, 22.4, 8.85), (9.4, 23.1, 8.9), 'label:UTILITY', 'utility label')
    m.g('desk items')
    m.box((10.4, 8.8, 7.0), (14.6, 9.4, 8.6), 'paint:#5a3a24', 'switching log book')
    m.box((10.6, 9.4, 7.1), (14.4, 9.45, 8.5), 'paint:#fbfbf6', 'log page')
    m.box((1.0, 8.8, 7.2), (3.4, 10.4, 8.6), 'paint:#1a1a1a', 'desk phone')
    m.box((1.2, 10.4, 7.4), (3.2, 10.8, 8.4), 'paint:#1a1a1a', 'handset')
    keyboard(m, 4.4, 9.8, 9.05, 6.7, 8.9)
    m.g('monitors on the mimic shelf')
    for x0 in (0.6, 11.4):
        m.box((x0 + 1.6, 9.0, 8.2), (x0 + 2.4, 10.2, 8.8), 'paint:#1a1a1c', 'monitor stand')
        m.box((x0, 10.2, 7.8), (x0 + 4.0, 13.0, 8.4), 'paint:#1a1a1c', 'monitor')
        m.box((x0 + 0.2, 10.4, 7.75), (x0 + 3.8, 12.8, 7.8), 'screen:5bcefa', 'monitor screen', faces=('north',))
    m.g('cable tray + trunking')
    m.box((0.4, 7.4, 2.0), (15.6, 7.8, 7.6), 'paint:#3a3d42', 'cable tray')
    for i in range(6):
        m.box((1.0 + i * 2.5, 7.0, 6.8), (1.3 + i * 2.5, 7.4, 7.2), 'cable:' + ('c81e1e' if i % 2 else '2050d0'), 'tray cable')
    m.box((15.8, 0.4, 9.2), (16.2, 24.0, 10.4), 'paint:#2a2d30', 'cable trunking')
    chair(m, 8.0, -2.4)
    m.terminal_12v(16.0, 2.0, 5.0, 'east')
    m.gland(8.0, 0.4, 5.0, 'down', r=0.3)
    mim = m.surface('north', face=8.85, title='MIMIC')
    lamp(m, mim, 'util', X(10.6), 22.15, '30e060', '', 0.7)
    m.ctl(mim, 'lamp', 'poi', X(8.0), 20.4, w=1.1, h=1.1, col='ff3030', label='POI')
    lamp(m, mim, 'lgen', X(3.7), 20.2, '30e060', '', 0.6)
    lamp(m, mim, 'lbat', X(12.3), 20.2, '30e060', '', 0.6)
    for (cid, x, lab) in (('shedA', 2.6, 'SHED A'), ('shedB', 5.0, 'SHED B'), ('shedC', 11.0, 'SHED C')):
        m.ctl(mim, 'lamp', cid, X(x), 14.4, w=1.4, h=0.9, col='ffb020', label=lab)
    lamp(m, mim, 'tied', X(13.4), 14.4, '30e060', 'TIED', 0.6)
    m.ctl(mim, 'synchro', 'sync', X(8.0), 16.4, w=2.6, h=2.6, col='f2f0e6', label='SYNC')
    m.ctl(mim, 'gauge', 'g_hz', X(3.0), 11.6, w=2.4, h=2.4, col='f2f0e6', label='FREQ', max=100)
    m.ctl(mim, 'gauge', 'g_load', X(6.2), 11.6, w=2.4, h=2.4, col='f2f0e6', label='GEN %', max=150)
    m.ctl(mim, 'gauge', 'g_soc', X(9.4), 11.6, w=2.4, h=2.4, col='f2f0e6', label='SOC %', max=100)
    for (cid, x, col, lab, bl) in (('lisl', 11.8, 'ffb020', 'ISLAND', False), ('resync', 12.8, '30a0ff', 'SYNC', True), ('blackout', 13.8, 'ff3030', 'BLACK', True), ('lock', 14.8, 'ff3030', 'LOCK', False)):
        lamp(m, mim, cid, X(x), 12.2, col, lab, 0.55, bl)
    lamp(m, mim, 'pwr', X(14.8), 10.6, '30e060', '12V', 0.45)
    scr = m.surface('north', face=8.85, title='STATUS')
    digits(m, scr, 'st', 8.0, 23.1 - 0.6, 6.0, 'ffd040', 0.45)
    digits(m, scr, 'hz', X(3.4), 9.9, 4.8, '7cf29a', 0.55)
    digits(m, scr, 'kv', X(8.4), 9.9, 4.8, '7cf29a', 0.55)
    digits(m, scr, 'poikw', X(12.6), 9.9, 5.4, '5bcefa', 0.55)
    up = m.surface('up', face=16 - 9.0, title='MICROGRID DESK')
    m.ctl(up, 'selector', 's_mode', X(2.0), 5.0, w=1.4, h=1.4, col='202020', label='', pos=['OFF', 'TIED', 'AUTO', 'ISL'])
    m.ctl(up, 'selector', 's_lim', X(4.4), 5.0, w=1.2, h=1.2, col='202020', label='', pos=['OFF', '250', '500', '1K', '2K'])
    for (cid, x, col, lab) in (('island', 1.8, 'f08020', 'ISLAND'), ('dosync', 3.5, '2050d0', 'SYNC'), ('shed', 5.2, 'f0d020', 'SHED'), ('restore', 6.9, '1fa34a', 'RESTR'), ('reset', 8.6, 'f0f0f0', 'RESET')):
        push(m, up, cid, X(x), 2.6, col, lab, 1.05)
    push(m, up, 'test', X(10.0), 2.6, 'f0f0f0', 'LAMP', 0.7)
    m.ctl(up, 'mushroom', 'm_trip', X(13.0), 4.2, w=2.0, h=2.0, col='d01818', label='INTERTIE TRIP')
    digits(m, up, 'gen', X(8.4), 5.6, 5.6, 'ffd040', 0.55)
    digits(m, up, 'shed', X(8.4), 4.8, 5.6, 'ffb020', 0.55)
    digits(m, up, 'why', 8.0, 1.5, 14.0, 'b8f0c8', 0.45)
    m.save(item_gui=0.45)
    facing_states('microgrid_controller')


# ============================================================================================ #25 field RTU
def field_rtu():
    m = Model('field_rtu')
    m.g('plinth')
    m.box((1.6, 0, 2.6), (14.4, 1.2, 14.4), 'concrete', 'concrete plinth')
    m.g('cabinet')
    m.box((2.0, 1.2, 3.0), (14.0, 20.0, 14.0), 'paint:#5d6b5a', 'GRP cabinet')
    m.box((1.6, 20.0, 2.6), (14.4, 20.8, 14.4), 'paint:#4e5a4c', 'rain hood')
    m.box((2.2, 1.6, 2.8), (13.8, 19.6, 3.0), 'paint:#68776a', 'door')
    door_hardware(m, 13.2, 1.6, 19.6, 2.8, 2.4)
    m.box((3.0, 9.0, 2.75), (13.0, 18.8, 2.8), 'glass', 'door window')
    m.g('inside: rack, modem, fibre tray, batteries')
    m.box((3.4, 9.2, 4.0), (12.6, 18.6, 12.0), 'paint:#24262a', 'equipment rack')
    m.box((3.6, 16.2, 3.9), (12.4, 18.2, 4.0), 'paint:#2f3236', 'RTU processor')
    m.box((3.6, 13.8, 3.9), (12.4, 15.8, 4.0), 'paint:#2f3236', 'IO cards')
    for i in range(8):
        m.box((3.9 + i * 1.05, 14.0, 3.85), (4.7 + i * 1.05, 15.6, 3.9), 'paint:#3a3d42', 'IO card')
    m.box((3.6, 11.6, 3.9), (8.0, 13.4, 4.0), 'paint:#2f3236', 'fibre patch tray')
    for i in range(6):
        m.box((3.9 + i * 0.65, 12.2, 3.8), (4.3 + i * 0.65, 12.6, 3.9), 'paint:#2050d0', 'fibre connector')
    m.box((8.4, 11.6, 3.9), (12.4, 13.4, 4.0), 'paint:#2f3236', 'GSM-R modem')
    m.box((3.6, 9.4, 3.9), (12.4, 11.2, 4.0), 'battery', 'backup batteries')
    m.g('antenna + fibre duct')
    m.box((11.0, 20.8, 11.0), (12.0, 22.0, 12.0), 'paint:#2a2d30', 'antenna base')
    m.cyl((11.5, 22.0, 11.5), 0.25, 6.0, 'y', 'paint:#e8e8e4', 'whip antenna')
    m.cyl((11.5, 25.0, 11.5), 0.45, 0.4, 'y', 'paint:#e8e8e4', 'loading coil')
    m.cyl((4.0, 0.0, 12.0), 0.6, 1.2, 'y', 'paint:#ff8020', 'fibre duct')
    m.cyl((6.0, 0.0, 12.0), 0.5, 1.2, 'y', 'cable:1d1d21', 'copper cable')
    m.box((2.0, 6.0, 14.0), (14.0, 8.0, 14.2), 'warn:', 'warning', faces=('south',))
    m.g('terminal rails + earth bar')
    terminal_blocks(m, 3.6, 7.6, 4.2, 18)
    terminal_blocks(m, 3.6, 5.8, 4.2, 18)
    m.box((3.6, 3.6, 4.0), (12.4, 4.0, 4.3), 'metal:copper', 'earth bar')
    for i in range(6):
        m.box((4.0 + i * 1.4, 2.6, 4.1), (4.2 + i * 1.4, 3.6, 4.2), 'cable:2a8a2a', 'earth lead')
    m.g('cabinet ribs, gasket, hasp')
    for y in (4.0, 8.0, 12.0, 16.0):
        m.box((1.8, y, 3.2), (2.0, y + 0.6, 13.8), 'paint:#4e5a4c', 'side rib')
        m.box((14.0, y, 3.2), (14.2, y + 0.6, 13.8), 'paint:#4e5a4c', 'side rib')
    m.box((2.1, 1.5, 2.95), (13.9, 1.6, 3.0), 'rubber', 'door gasket')
    m.box((12.6, 15.0, 2.6), (13.4, 16.2, 2.8), 'metal:steel', 'padlock hasp')
    m.box((12.7, 14.2, 2.5), (13.3, 15.0, 2.7), 'metal:brass', 'padlock')
    for x in (3.0, 5.0, 7.0, 9.0):
        m.gland(x, 1.2, 10.0, 'down', r=0.3)
    m.nameplate(4.0, 19.0, 12.0, 19.5, 2.75, 'FIELD RTU')
    m.terminal_12v(14.0, 2.0, 8.0, 'east')
    f = m.surface('north', face=2.7, title='RTU PANEL')
    lamp(m, f, 'pwr', X(3.4), 8.1, '30e060', '12V', 0.55)
    lamp(m, f, 'cpu', X(4.6), 8.1, '30e060', 'RUN', 0.55)
    lamp(m, f, 'cpuf', X(5.8), 8.1, 'ff3030', 'FAIL', 0.55, True)
    lamp(m, f, 'local', X(7.0), 8.1, '30a0ff', 'LOCAL', 0.55)
    lamp(m, f, 'stale', X(8.2), 8.1, 'ffb020', 'STALE', 0.55, True)
    lamp(m, f, 'tx', X(9.4), 8.1, 'ffb020', 'TX', 0.45)
    lamp(m, f, 'rx', X(10.4), 8.1, '30e060', 'RX', 0.45)
    for (base, x, lab) in (('a', 3.4, 'A FIBRE'), ('b', 7.6, 'B RADIO')):
        lamp(m, f, base + '_ok', X(x), 6.3, '30e060', 'UP', 0.5)
        lamp(m, f, base + '_bad', X(x + 1.1), 6.3, 'ff3030', 'DOWN', 0.5)
        lamp(m, f, 'act_' + base, X(x + 2.2), 6.3, '30a0ff', 'ACTIVE', 0.5)
        label(m, f, X(x + 1.1), 7.05, lab, '1a1a1a')
    m.ctl(f, 'selector', 's_a', X(3.4), 4.4, w=1.0, h=1.0, col='202020', label='LINK A', pos=['IN', 'OUT'])
    m.ctl(f, 'selector', 's_b', X(5.4), 4.4, w=1.0, h=1.0, col='202020', label='LINK B', pos=['IN', 'OUT'])
    m.ctl(f, 'selector', 's_pref', X(7.4), 4.4, w=1.0, h=1.0, col='202020', label='PREFER', pos=['A', 'B'])
    m.ctl(f, 'selector', 's_cover', X(9.4), 4.4, w=1.0, h=1.0, col='202020', label='COVER', pos=['32', '48', '64', '96'])
    m.ctl(f, 'gauge', 'g_lat', X(11.6), 5.6, w=1.8, h=1.8, col='f2f0e6', label='LAT MS', max=1000)
    m.ctl(f, 'gauge', 'g_q', X(11.6), 3.0, w=1.8, h=1.8, col='f2f0e6', label='QUAL %', max=100)
    for (cid, x, col, lab) in (('poll', 3.4, '1fa34a', 'POLL'), ('reset', 5.0, 'f0f0f0', 'CTRS'), ('restart', 6.6, 'd02020', 'RESTART')):
        push(m, f, cid, X(x), 2.4, col, lab, 0.9)
    push(m, f, 'test', X(8.2), 2.4, 'f0f0f0', 'LAMP', 0.6)
    s = m.surface('north', face=2.7, title='RTU DISPLAY')
    for (cid, v, col) in (('id', 18.3, 'ffd040'), ('path', 17.6, '5bcefa'), ('lat', 16.9, '7cf29a'), ('gear', 16.2, 'b8f0c8'), ('ctr', 15.5, '8ac8a0'), ('last', 14.8, 'ffb020')):
        digits(m, s, cid, 8.0, v, 9.4, col, 0.55)
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 3.0, 13.0, 3.0, 19.0, 2.0, side='south', col='paint:#5d6b5a', title='FIELD RTU', volts='230V 12V DC', fns=(('ctl', '12V CTL'), ('scr', 'SCREENS')))
    m.save(item_gui=0.45)
    facing_states('field_rtu')


# ============================================================================================ #30 PIS server rack
def pis_server():
    m = Model('pis_server')
    m.g('rack')
    m.box((1.0, 0, 1.4), (15.0, 0.8, 15.0), 'paint:#141416', 'rack plinth')
    for x in (1.0, 14.2):
        m.box((x, 0.8, 1.4), (x + 0.8, 28.0, 15.0), 'paint:#1c1d20', 'rack upright')
    m.box((1.8, 0.8, 14.2), (14.2, 28.0, 15.0), 'mesh', 'rear mesh door')
    m.box((1.0, 28.0, 1.4), (15.0, 28.6, 15.0), 'paint:#1c1d20', 'rack top')
    for y in range(1, 28, 2):
        m.box((1.7, y, 1.3), (1.95, y + 0.25, 1.4), 'metal:steel', 'rack hole')
        m.box((14.05, y, 1.3), (14.3, y + 0.25, 1.4), 'metal:steel', 'rack hole')
    m.g('servers')
    for k, y in enumerate((22.0, 19.6)):
        m.box((1.8, y, 1.7), (14.2, y + 2.2, 13.0), 'paint:#2b2f33', 'server chassis')
        m.box((1.6, y, 1.5), (14.4, y + 2.2, 1.7), 'paint:#3a3f44', 'server front')
        for i in range(4):
            m.box((2.2 + i * 1.6, y + 0.3, 1.4), (3.6 + i * 1.6, y + 1.9, 1.5), 'paint:#202226', 'drive bay')
        m.louvres(9.0, 13.6, y + 0.4, y + 1.8, 1.5, n=2)
        m.nameplate(9.2, y + 1.85, 13.6, y + 2.15, 1.5, 'PIS ' + ('A' if k == 0 else 'B'))
    m.g('KVM drawer + screen')
    m.box((1.8, 16.4, 1.0), (14.2, 19.2, 10.0), 'paint:#24262a', 'KVM chassis')
    m.box((2.0, 16.6, 0.9), (14.0, 19.0, 1.0), 'paint:#03140a', 'KVM screen')
    m.g('message panel')
    m.box((1.6, 9.6, 1.5), (14.4, 16.2, 1.7), PANEL, 'operator panel')
    m.nameplate(5.0, 15.6, 11.0, 16.1, 1.5, 'PASSENGER INFO')
    m.g('drawer + UPS')
    m.box((1.8, 6.4, 1.7), (14.2, 9.4, 13.0), 'paint:#2b2f33', 'spares drawer body')
    m.box((1.6, 6.4, 1.5), (14.4, 9.4, 1.7), 'paint:#3a3f44', 'drawer front')
    m.box((6.6, 7.6, 1.2), (9.4, 8.0, 1.5), 'metal:alu', 'drawer pull')
    m.box((2.0, 6.8, 1.45), (5.6, 7.6, 1.5), 'label:SPARE BOARDS', 'drawer label')
    m.box((1.8, 1.0, 1.7), (14.2, 6.0, 13.0), 'paint:#1c1d20', 'UPS')
    m.box((1.6, 1.0, 1.5), (14.4, 6.0, 1.7), 'paint:#26282c', 'UPS front')
    m.louvres(8.0, 13.8, 1.6, 5.4, 1.5, n=4)
    m.nameplate(2.2, 4.6, 7.0, 5.4, 1.5, 'UPS 3KVA')
    m.terminal_12v(15.0, 2.0, 8.0, 'east')
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 0.8, 12.0, 'down', r=0.35)
    dr = m.surface('north', face=1.45, title='DRAWER')
    m.ctl(dr, 'zone', 'dr1', 8.0, 7.9, w=12.6, h=2.8, col='000000', label='')
    k = m.surface('north', face=0.88, title='KVM')
    digits(m, k, 'stn', X(5.0), 18.6, 5.8, 'ffd040', 0.45)
    digits(m, k, 'cnt', X(11.0), 18.6, 5.8, '5bcefa', 0.45)
    digits(m, k, 'tpl', 8.0, 18.05, 11.6, 'b8f0c8', 0.4)
    for i in range(4):
        digits(m, k, 'l%d' % (i + 1), 8.0, 17.55 - i * 0.42, 11.6, 'ff8060' if i == 0 else '7cf29a', 0.36)
    p = m.surface('north', face=1.45, title='PIS PANEL')
    m.ctl(p, 'selector', 's_mode', X(2.6), 14.2, w=1.1, h=1.1, col='202020', label='MODE', pos=['OFF', 'AUTO', 'MAN'])
    m.ctl(p, 'selector', 's_reach', X(4.8), 14.2, w=1.0, h=1.0, col='202020', label='REACH', pos=['256', '512', '1K', '2K'])
    m.ctl(p, 'selector', 's_plat', X(7.0), 14.2, w=1.0, h=1.0, col='202020', label='PLAT', pos=['1', '2', '3', '4', '5', '6', '7', '8'])
    for (cid, x, lab) in (('stn<', 2.4, 'STN<'), ('stn>', 3.6, 'STN>'), ('tpl<', 5.0, 'TPL<'), ('tpl>', 6.2, 'TPL>')):
        push(m, p, cid, X(x), 11.9, '1a1a1a', lab, 0.8)
    for (cid, x, col, lab) in (('pub', 2.4, '1fa34a', 'PUBLISH'), ('sec', 4.0, 'f0d020', 'SECURITY'), ('plat', 5.6, '2050d0', 'PLATFORM'), ('clr', 7.2, 'f0f0f0', 'CLEAR'), ('allclr', 8.8, '1fa34a', 'ALL CLR')):
        push(m, p, cid, X(x), 10.4, col, lab, 0.85)
    m.ctl(p, 'mushroom', 'm_evac', X(12.0), 12.6, w=2.2, h=2.2, col='d01818', label='EVACUATE')
    for (cid, x, col, lab, bl) in (('pwr', 9.2, '30e060', '12V', False), ('run', 10.2, '30e060', 'RUN', False), ('auto', 9.2, '30a0ff', 'AUTO', False), ('lead', 10.2, 'ffb020', 'MSTR', False), ('evac', 13.8, 'ff3030', 'EVAC', True), ('msgs', 13.8, 'ffb020', 'MSGS', False)):
        lamp(m, p, cid, X(x), 14.6 if cid in ('pwr', 'run', 'evac') else 13.2 if cid != 'msgs' else 11.6, col, lab, 0.5, bl)
    push(m, p, 'test', X(10.4), 10.4, 'f0f0f0', 'LAMP', 0.6)
    digits(m, p, 'status', 8.0, 9.9, 12.0, 'ffb020', 0.35)
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 14.0, 14.5, 27.5, 1.0, side='south', col='paint:#1c1d20', title='PIS SERVER', volts='230V 12V DC', fns=(('ctl', '12V CTL'), ('scr', 'SCREENS')))
    m.save(item_gui=0.45)
    facing_states('pis_server')


# ============================================================================================ #30 PIS platform sign
def pis_board():
    m = Model('pis_board')
    m.g('hangers')
    for x in (2.0, 14.0):
        m.box((x - 0.2, 11.2, 7.8), (x + 0.2, 16.0, 8.2), 'metal:steel', 'hanger rod')
        m.box((x - 0.8, 15.6, 7.2), (x + 0.8, 16.0, 8.8), 'metal:steel', 'ceiling plate')
        m.bolt(x - 0.4, 15.8, 7.3, 'north', 0.25); m.bolt(x + 0.4, 15.8, 7.3, 'north', 0.25)
    m.g('sign case')
    m.box((0.2, 6.2, 6.4), (15.8, 11.2, 9.6), 'paint:#1c1f24', 'sign case')
    m.box((0.0, 6.0, 6.2), (16.0, 6.4, 9.8), 'paint:#2a2e34', 'case bottom rail')
    m.box((0.0, 11.0, 6.2), (16.0, 11.4, 9.8), 'paint:#2a2e34', 'case top rail')
    m.box((0.6, 7.45, 6.3), (15.4, 10.9, 6.4), 'paint:#0a0806', 'LED matrix front')
    m.box((0.6, 6.45, 6.32), (15.4, 7.35, 6.4), 'paint:#2a2e34', 'service strip')
    m.box((0.6, 6.7, 9.6), (15.4, 10.9, 9.7), 'led:ffa020', 'LED matrix rear')
    m.box((0.0, 6.4, 6.2), (0.2, 11.0, 9.8), 'paint:#5bcefa', 'end cap')
    m.box((15.8, 6.4, 6.2), (16.0, 11.0, 9.8), 'paint:#5bcefa', 'end cap')
    m.box((6.0, 6.0, 9.4), (10.0, 6.2, 9.6), 'louvre', 'vent')
    m.box((1.0, 11.4, 7.6), (3.0, 12.0, 8.4), 'paint:#2a2e34', 'cable entry')
    m.cyl((2.0, 12.0, 8.0), 0.25, 4.0, 'y', 'cable:1d1d21', 'supply cable')
    m.g('LED modules + seams')
    for i in range(1, 8):
        m.box((0.6 + i * 1.85, 7.45, 6.28), (0.65 + i * 1.85, 10.9, 6.3), 'paint:#141210', 'module seam')
    m.box((0.6, 9.15, 6.28), (15.4, 9.2, 6.3), 'paint:#141210', 'module seam')
    m.g('rear access panel')
    for x in (1.0, 5.0, 11.0, 15.0):
        for y in (6.8, 10.6):
            m.bolt(x, y, 9.7, 'south', 0.25)
    m.box((4.0, 7.0, 9.7), (12.0, 7.4, 9.75), 'label:PLATFORM 1', 'rear plate', faces=('south',))
    m.g('turnbuckles + safety wires')
    for x in (2.0, 14.0):
        m.box((x - 0.3, 12.6, 7.7), (x + 0.3, 13.6, 8.3), 'metal:steel', 'turnbuckle')
        m.box((x - 0.05, 11.4, 9.0), (x + 0.05, 15.6, 9.1), 'metal:steel', 'safety wire')
    m.g('hanging chains')
    for x in (3.4, 12.6):
        for k in range(6):
            y = 11.4 + k * 0.75
            if k % 2: m.box((x - 0.05, y, 7.85), (x + 0.05, y + 0.8, 8.15), 'metal:steel', 'chain link')
            else: m.box((x - 0.2, y, 7.95), (x + 0.2, y + 0.8, 8.05), 'metal:steel', 'chain link')
    m.g('cooling fins + gussets')
    for i in range(8):
        m.box((1.0 + i * 1.9, 11.4, 7.0), (1.3 + i * 1.9, 11.9, 9.0), 'metal:alu', 'cooling fin')
    for x in (1.4, 14.2):
        m.box((x, 11.0, 7.2), (x + 0.4, 11.6, 8.8), 'metal:steel', 'gusset')
    m.box((3.0, 11.6, 7.85), (13.0, 11.85, 8.15), 'paint:#2a2e34', 'cable conduit')
    m.g('PA speaker under the sign')
    m.box((6.6, 5.4, 7.4), (9.4, 6.0, 8.6), 'paint:#2a2e34', 'speaker bracket')
    m.cyl((8.0, 4.6, 6.8), 0.9, 1.6, 'z', 'paint:#d8d8d4', 'speaker horn', cap='mesh')
    m.terminal_12v(13.0, 11.4, 9.8, 'south')
    f = m.surface('north', face=6.28, title='PIS SIGN')
    digits(m, f, 'l1', 8.0, 10.4, 14.4, 'ffb020', 0.65)
    digits(m, f, 'l2', 8.0, 9.55, 14.4, 'ffd040', 0.7)
    digits(m, f, 'l3', 8.0, 8.7, 14.4, 'ffd040', 0.7)
    digits(m, f, 'tick', 8.0, 7.9, 14.4, 'ff8020', 0.55)
    st = m.surface('north', face=6.3, title='SERVICE STRIP')
    m.ctl(st, 'selector', 's_mode', X(1.6), 6.9, w=0.75, h=0.75, col='202020', label='', pos=['FEED', 'TRAINS', 'BOTH'])
    push(m, st, 'test', X(2.8), 6.9, 'f0f0f0', '', 0.5)
    for (cid, x, col, bl) in (('pwr', 11.6, '30e060', False), ('online', 12.6, '30e060', False), ('fallback', 13.6, 'ffb020', True), ('evac', 14.6, 'ff3030', True)):
        lamp(m, st, cid, X(x), 6.9, col, '', 0.4, bl)
    m.save(item_gui=0.6)
    facing_states('pis_board')


# ============================================================================================ #43 approval panel
def approval_panel():
    m = Model('approval_panel')
    m.g('wall cabinet')
    m.box((1.0, 0, 13.0), (15.0, 1.0, 16.0), DARK, 'floor stand')
    m.box((1.0, 1.0, 12.0), (15.0, 22.0, 16.0), 'paint:#c4c6c2', 'cabinet')
    m.box((0.8, 22.0, 11.8), (15.2, 22.6, 16.0), 'paint:#b4b6b2', 'cabinet top')
    m.box((1.4, 4.0, 11.8), (14.6, 21.6, 12.0), 'paint:#2b2f33', 'panel fascia')
    m.box((2.0, 15.2, 11.75), (14.0, 21.0, 11.8), 'paint:#03140a', 'request screen')
    m.nameplate(3.6, 21.15, 12.4, 21.55, 11.75, 'AUTHORISATION')
    for x in (1.7, 14.3):
        for y in (4.3, 21.3):
            m.bolt(x, y, 11.8, 'north', 0.35)
    m.g('forms tray')
    m.box((2.0, 1.4, 10.4), (14.0, 3.6, 12.0), 'paint:#a8aaa6', 'forms tray')
    m.box((2.4, 3.6, 10.6), (13.6, 3.7, 11.8), 'paint:#fbfbf6', 'request forms')
    m.box((6.6, 2.4, 10.3), (9.4, 2.8, 10.4), 'metal:alu', 'tray pull')
    m.box((2.6, 1.8, 10.35), (6.0, 2.4, 10.4), 'label:FORMS', 'tray label')
    m.g('key plates')
    for x in (4.0, 12.0):
        m.box((x - 1.6, 6.6, 11.7), (x + 1.6, 10.6, 11.8), 'metal:steel', 'key plate')
    m.box((5.0, 11.0, 11.75), (11.0, 11.8, 11.8), 'label:TWO PERSON RULE', 'rule label')
    m.g('keypad + card reader')
    for r in range(4):
        for c in range(3):
            m.box((1.9 + c * 0.6, 13.6 - r * 0.6, 11.6), (2.4 + c * 0.6, 14.1 - r * 0.6, 11.75), 'paint:#3a3d42', 'keypad key')
    m.box((12.6, 12.0, 11.5), (14.2, 14.4, 11.8), 'paint:#1a1a1c', 'card reader')
    m.box((12.9, 13.6, 11.45), (13.9, 13.75, 11.5), 'paint:#30e060', 'reader led')
    m.box((13.0, 12.4, 11.4), (13.8, 13.2, 11.5), 'paint:#101010', 'card slot')
    m.g('slip printer + clock')
    m.box((2.0, 22.6, 12.4), (7.0, 24.0, 15.6), 'paint:#d8d8d4', 'approval slip printer')
    m.box((2.4, 23.2, 12.3), (6.6, 23.4, 12.4), 'paint:#1a1a1a', 'paper slot')
    m.box((2.6, 23.25, 11.6), (6.4, 23.3, 12.3), 'paint:#fbfbf6', 'approval slip')
    m.cyl((11.0, 22.6, 13.8), 1.4, 0.3, 'y', 'paint:#f0f0e8', 'clock face')
    m.cyl((11.0, 22.9, 13.8), 0.1, 0.2, 'y', 'paint:#1a1a1a', 'clock spindle')
    m.g('lockout station (padlocks)')
    m.box((15.0, 14.0, 12.4), (17.0, 14.3, 15.6), 'metal:steel', 'padlock rail')
    for k in range(6):
        z = 12.8 + k * 0.5
        m.box((16.1, 13.2, z), (16.6, 14.0, z + 0.35), 'paint:' + ('#c81e1e', '#f0d020', '#2050d0')[k % 3], 'padlock body')
        m.box((16.25, 14.0, z + 0.05), (16.45, 14.4, z + 0.3), 'metal:steel', 'padlock shackle')
    m.box((15.0, 15.0, 12.4), (17.0, 16.6, 15.6), 'danger:LOCKOUT|STATION', 'lockout sign', faces=('east',))
    m.g('key cabinet')
    m.box((-1.0, 12.0, 12.2), (1.0, 19.0, 15.8), 'paint:#c4c6c2', 'key cabinet')
    for r in range(3):
        for k in range(3):
            z = 12.8 + k * 1.0
            m.box((-1.15, 17.6 - r * 2.0, z), (-1.0, 17.8 - r * 2.0, z + 0.2), 'metal:brass', 'key hook')
            m.box((-1.25, 16.8 - r * 2.0, z + 0.02), (-1.1, 17.6 - r * 2.0, z + 0.18), 'metal:brass', 'key')
    m.g('back plate + trunking')
    m.box((0.4, 0.0, 15.8), (15.6, 23.0, 16.0), 'paint:#8a8c88', 'wall back plate')
    m.box((15.0, 1.0, 13.0), (15.6, 22.0, 15.8), 'paint:#b4b6b2', 'cable trunking')
    m.box((0.4, 1.0, 13.0), (1.0, 22.0, 15.8), 'paint:#b4b6b2', 'cable trunking')
    for y in (5.0, 10.0, 15.0, 20.0):
        m.bolt(0.7, y, 13.0, 'north', 0.25)
        m.bolt(15.3, y, 13.0, 'north', 0.25)
    m.terminal_12v(15.0, 2.0, 14.0, 'east')
    m.gland(8.0, 1.0, 14.0, 'down', r=0.3)
    tr = m.surface('north', face=10.35, title='TRAY')
    m.ctl(tr, 'zone', 'tray', 8.0, 2.5, w=11.6, h=2.2, col='000000', label='')
    s = m.surface('north', face=11.73, title='REQUEST')
    for (cid, v, col) in (('r1', 20.4, 'ffd040'), ('r2', 19.6, '5bcefa'), ('r3', 18.8, '7cf29a'), ('r4', 18.0, 'b8f0c8'), ('t1', 16.6, '8ac8a0'), ('t2', 15.9, '8ac8a0')):
        digits(m, s, cid, 8.0, v, 11.6, col, 0.6 if cid.startswith('r') else 0.5)
    p = m.surface('north', face=11.73, title='AUTHORISE')
    m.ctl(p, 'key', 'k_a', X(4.0), 8.6, w=1.6, h=1.6, col='c0a040', label='KEY A', pos=['OFF', 'AUTH'])
    m.ctl(p, 'key', 'k_b', X(12.0), 8.6, w=1.6, h=1.6, col='c0a040', label='KEY B', pos=['OFF', 'AUTH'])
    lamp(m, p, 'ka', X(4.0), 10.2, '30e060', '', 0.5)
    lamp(m, p, 'kb', X(12.0), 10.2, '30e060', '', 0.5)
    for (cid, x, col, lab) in (('prev', 6.4, '1a1a1a', '<'), ('next', 7.6, '1a1a1a', '>'), ('reject', 9.2, 'd02020', 'REJECT')):
        push(m, p, cid, X(x), 8.6, col, lab, 0.95)
    push(m, p, 'test', X(9.2), 6.6, 'f0f0f0', 'LAMP', 0.6)
    for (cid, x, col, lab, bl) in (('pwr', 2.4, '30e060', '12V', False), ('pend', 4.4, 'ffb020', 'PENDING', True), ('appr', 6.4, '30e060', 'APPROVED', False), ('dual', 8.4, '30a0ff', 'DUAL', False)):
        lamp(m, p, cid, X(x), 13.4, col, lab, 0.6, bl)
    digits(m, p, 'nopen', X(11.6), 13.4, 3.2, 'ffb020', 0.7, '%d OPEN')
    digits(m, p, 'pg', X(13.8), 13.4, 1.8, '7cf29a', 0.6)
    m.save(item_gui=0.45)
    facing_states('approval_panel')


# ============================================================================================ #23 production planner
STAGES = ['CRUSH', 'WASH', 'FLOAT', 'COKE', 'STEEL', 'ROLL', 'SLEEP', 'FAB']


def production_planner():
    m = Model('production_planner')
    m.g('desk')
    m.box((0, 8.2, 1.0), (16, 9.0, 9.0), 'wood', 'desk top')
    m.box((-0.2, 8.0, 0.8), (16.2, 8.2, 9.2), 'paint:#3e2716', 'desk edge')
    m.box((0, 0, 1.6), (16, 0.4, 8.8), 'paint:#2a1a10', 'kick plinth')
    dr = m.surface('north', face=1.45, title='DRAWERS')
    for (x0, x1, zids) in ((0.4, 4.6, (('dr1', 'PLANS'), ('dr2', 'RECORDS'))), (11.4, 15.6, (('cab', 'REPORTS'),))):
        m.box((x0, 0.4, 1.6), (x1, 8.0, 8.6), 'paint:#5a3a24', 'pedestal')
        if len(zids) == 2:
            for k, (zid, lab) in enumerate(zids):
                y0 = 0.8 + k * 3.6
                m.box((x0 + 0.3, y0, 1.5), (x1 - 0.3, y0 + 3.3, 1.6), 'paint:#7a5238', 'drawer front')
                m.box(((x0 + x1) / 2 - 0.8, y0 + 2.4, 1.3), ((x0 + x1) / 2 + 0.8, y0 + 2.7, 1.5), 'metal:brass', 'drawer pull')
                m.box((x0 + 0.6, y0 + 0.4, 1.45), (x1 - 0.6, y0 + 1.0, 1.5), 'label:' + lab, 'drawer label')
                m.ctl(dr, 'zone', zid, X((x0 + x1) / 2), y0 + 1.65, w=x1 - x0 - 0.6, h=3.3, col='000000', label='')
        else:
            zid, lab = zids[0]
            m.box((x0 + 0.3, 0.8, 1.5), (x1 - 0.3, 7.6, 1.6), 'paint:#7a5238', 'cabinet door')
            m.box((x0 + 0.6, 4.0, 1.3), (x0 + 0.9, 5.6, 1.5), 'metal:brass', 'door handle')
            m.box((x0 + 1.2, 6.4, 1.45), (x1 - 0.6, 7.0, 1.5), 'label:' + lab, 'door label')
            m.ctl(dr, 'zone', zid, X((x0 + x1) / 2), 4.2, w=x1 - x0 - 0.6, h=6.8, col='000000', label='')
    m.box((4.6, 1.0, 7.8), (11.4, 8.0, 8.4), 'paint:#6b4630', 'modesty panel')
    m.g('control strip')
    m.box((0.8, 9.0, 1.2), (15.2, 9.2, 6.4), 'paint:#2b2f33', 'control strip')
    m.box((0.6, 9.0, 1.0), (15.4, 9.25, 1.2), 'metal:steel', 'strip edge')
    m.g('plan wall')
    m.box((0.2, 9.0, 9.2), (15.8, 25.0, 10.4), 'paint:#22252a', 'plan wall frame')
    m.box((0.6, 9.6, 9.1), (15.4, 24.6, 9.2), 'paint:#2f3a46', 'plan wall face')
    m.box((0.0, 25.0, 9.0), (16.0, 25.4, 10.6), 'paint:#22252a', 'wall top')
    m.box((1.0, 17.0, 9.06), (15.0, 23.8, 9.1), 'paint:#03140a', 'plan screen')
    m.nameplate(4.0, 24.65, 12.0, 25.0, 9.1, 'PRODUCTION PLAN')
    # the chain mimic: eight stages joined by arrows
    for i, s in enumerate(STAGES):
        x = 1.2 + i * 1.75
        m.box((x, 13.4, 9.06), (x + 1.4, 14.2, 9.1), 'label:' + s, 'stage label')
        if i < len(STAGES) - 1:
            m.box((x + 1.4, 14.95, 9.06), (x + 1.75, 15.05, 9.1), 'paint:#f0f0e8', 'chain arrow')
    m.g('desk items')
    m.box((6.4, 9.0, 6.8), (9.6, 10.6, 8.8), 'paint:#1a1a1a', 'keyboard tray')
    m.box((12.0, 9.0, 6.8), (14.8, 9.6, 8.6), 'paint:#fbfbf6', 'printed plan')
    m.box((12.2, 9.6, 7.0), (14.6, 9.65, 8.4), 'paint:#e8e4d4', 'plan sheet')
    m.box((1.2, 9.0, 6.8), (3.6, 11.4, 8.6), 'paint:#3a3f44', 'calculator')
    keyboard(m, 4.6, 11.2, 9.0, 6.4, 8.6)
    chair(m, 8.0, -2.4)
    m.g('wall clock + document trays')
    m.cyl((8.0, 25.4, 9.6), 1.2, 0.3, 'y', 'paint:#f0f0e8', 'wall clock')
    for k in range(3):
        m.box((13.0, 9.2 + k * 0.6, 7.0), (15.4, 9.3 + k * 0.6, 8.8), 'paint:#3a3f44', 'document tray')
        m.box((13.2, 9.3 + k * 0.6, 7.2), (15.2, 9.45 + k * 0.6, 8.6), 'paint:#fbfbf6', 'documents')
    m.box((0.8, 9.2, 4.6), (1.6, 10.4, 5.4), 'paint:#5a3a24', 'pen pot')
    for k in range(3):
        m.box((1.0 + k * 0.2, 10.4, 4.9), (1.1 + k * 0.2, 11.2, 5.0), 'paint:' + ('#2050d0', '#c81e1e', '#1a1a1a')[k], 'pen')
    m.terminal_12v(16.0, 2.0, 5.0, 'east')
    m.gland(8.0, 0.4, 5.0, 'down', r=0.3)
    w = m.surface('north', face=9.05, title='PLAN WALL')
    digits(m, w, 'hd', 8.0, 23.3, 13.6, 'ffd040', 0.6)
    for i in range(6):
        digits(m, w, 's%d' % (i + 1), 8.0, 22.4 - i * 0.82, 13.6, 'b8f0c8', 0.55)
    digits(m, w, 'tg', 8.0, 17.4, 13.6, '5bcefa', 0.45)
    for i in range(len(STAGES)):
        x = 1.2 + i * 1.75 + 0.7
        lamp(m, w, 'st%d' % i, X(x), 15.6, '30e060', '', 0.55)
        lamp(m, w, 'bn%d' % i, X(x), 12.6, 'ff3030', '', 0.55, True)
    m.ctl(w, 'gauge', 'g_util', X(3.0), 10.7, w=2.2, h=2.2, col='f2f0e6', label='BOTTLENECK %', max=200)
    m.ctl(w, 'gauge', 'g_bid', X(6.6), 10.7, w=2.2, h=2.2, col='f2f0e6', label='CLEAR $/MWH', max=300)
    for (cid, x, col, lab, bl) in (('pwr', 10.0, '30e060', '12V', False), ('auto', 11.4, '30a0ff', 'AUTO', False), ('held', 12.8, 'ffb020', 'HELD', False), ('risk', 14.2, 'ff3030', 'FRT RISK', True)):
        lamp(m, w, cid, X(x), 10.9, col, lab, 0.6, bl)
    up = m.surface('up', face=16 - 9.2, title='PLANNER DESK')
    for (cid, x, lab, pos) in (('s_mode', 1.8, 'MODE', ['OFF', 'ADV', 'AUTO']), ('s_rail', 3.8, 'RAIL/H', ['0', '16', '32', '64', '128']),
                               ('s_steel', 5.8, 'STEEL/H', ['0', '16', '32', '64']), ('s_frt', 7.8, 'FREIGHT', ['ON', 'OFF']), ('s_view', 9.8, 'VIEW', ['STG', 'PWR', 'FRT', 'PLT'])):
        m.ctl(up, 'selector', cid, X(x), 4.6, w=1.2, h=1.2, col='202020', label=lab, pos=pos)
    for (cid, x, col, lab) in (('replan', 11.8, '2050d0', 'REPLAN'), ('release', 13.4, '1fa34a', 'RELEASE'), ('print', 11.8, 'f0f0f0', 'PRINT')):
        push(m, up, cid, X(x), 4.6 if cid != 'print' else 2.4, col, lab, 1.0)
    push(m, up, 'test', X(13.4), 2.4, 'f0f0f0', 'LAMP', 0.7)
    m.save(item_gui=0.45)
    facing_states('production_planner')


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


RECIPES = {
    'spectrum_meter': (['GCG', 'RQR', 'I I'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator', 'R': 'minecraft:redstone', 'Q': 'minecraft:quartz', 'I': 'minecraft:iron_ingot'}),
    'filter_bank': (['ICI', 'CRC', 'IGI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:repeater', 'R': 'minecraft:redstone_block', 'G': 'minecraft:gold_ingot'}),
    'microgrid_controller': (['GCG', 'RTR', 'PPP'], {'G': 'minecraft:glass_pane', 'C': 'minecraft:clock', 'R': 'minecraft:redstone', 'T': 'minecraft:comparator', 'P': ('minecraft:planks', 0)}),
    'field_rtu': (['EIE', 'IRI', 'ICI'], {'E': 'minecraft:ender_pearl', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:repeater', 'C': 'minecraft:comparator'}),
    'pis_server': (['IGI', 'RCR', 'IDI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glowstone_dust', 'R': 'minecraft:redstone', 'C': 'minecraft:comparator', 'D': 'minecraft:diamond'}),
    'pis_board': (['ISI', 'GRG', '   '], {'I': 'minecraft:iron_ingot', 'S': ('minecraft:sign', None), 'G': 'minecraft:glowstone_dust', 'R': 'minecraft:redstone'}),
    'approval_panel': (['IGI', 'KRK', 'IPI'], {'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass_pane', 'K': 'minecraft:tripwire_hook', 'R': 'minecraft:redstone', 'P': 'minecraft:paper'}),
    'production_planner': (['GBG', 'RCR', 'PPP'], {'G': 'minecraft:glass_pane', 'B': 'minecraft:book', 'R': 'minecraft:redstone', 'C': 'minecraft:comparator', 'P': ('minecraft:planks', 0)}),
}

LANG = {
    'tile.irextras.spectrum_meter.name': 'Harmonic Spectrum Meter', 'tile.irextras.filter_bank.name': 'Harmonic Filter Bank',
    'tile.irextras.microgrid_controller.name': 'Microgrid Controller', 'tile.irextras.field_rtu.name': 'Field RTU',
    'tile.irextras.pis_server.name': 'PIS Server Rack', 'tile.irextras.pis_board.name': 'PIS Platform Sign',
    'tile.irextras.approval_panel.name': 'Approval Panel', 'tile.irextras.production_planner.name': 'Production Planning Console',
}

if __name__ == '__main__':
    for fn in (spectrum_meter, filter_bank, microgrid, field_rtu, pis_server, pis_board, approval_panel, production_planner):
        fn()
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# grid, control + comms (next-48 #20 #21 #23 #25 #30 #43)\n' + '\n'.join(add) + '\n')
    print('grid / control / comms assets written')
