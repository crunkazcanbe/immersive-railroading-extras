#!/usr/bin/env python3
"""Batch 5 #5 + #10 (her words 10-11: "do all of it, Blockbench and buttons, functional screens, real looking ... as
complex as possible"): funiculars, aerial trams and gondolas with their machine room, control desk, stations, pylons,
tension weight, cabins and cars; and the cinematic train camera's trackside TV camera tripod and director desk.

Models face north. Every lamp, button, key, knob, gauge and display is a live control the Java tiles (ride/*, cine/*)
read or work; the screens (mimic, preview / program monitors) are drawn by Java on the dark glass. The moving parts
(cabins, pylon heads, bullwheels, machine rotor, tension weight, camera head) are baked as item variants and drawn
turned / spun by Java. Also writes blockstates, item models, the spool icon, recipes and lang."""
import json, math, os
from PIL import Image, ImageDraw
from bbkit import Model, ASSETS
from gen_bb_ctl import facing_states, ing

X = lambda x: 16 - x
GALV = 'galv'
STEEL = 'metal:steel'
DARK = 'paint:#2c2f34'
ROPE = 'cable:3a3d40'


def lamp(m, sf, cid, x, y, col, label='', s=0.6, blink=False):
    m.ctl(sf, 'lamp', cid, X(x), y, w=s, h=s, col=col, label=label, **({'blink': True} if blink else {}))


def push(m, sf, cid, x, y, col, label, s=1.1):
    m.ctl(sf, 'push', cid, X(x), y, w=s, h=s, col=col, label=label)


def mush(m, sf, cid, x, y, label='E-STOP', s=2.0):
    m.ctl(sf, 'mushroom', cid, X(x), y, w=s, h=s, col='d01818', label=label)


def digits(m, sf, cid, x, y, w, h=0.85, col='7cf29a'):
    m.ctl(sf, 'digits', cid, X(x), y, w=w, h=h, col=col, label='', fmt='%s')


def sel(m, sf, cid, x, y, label, pos, s=1.4, t='selector'):
    m.ctl(sf, t, cid, X(x), y, w=s, h=s, col='202020', label=label, pos=pos)


def gauge(m, sf, cid, x, y, label, s=3.0):
    m.ctl(sf, 'gauge', cid, X(x), y, w=s, h=s, col='f2f0e6', label=label, max=100)


def tile(m, sf, cid, x, y, label, w=3.2, h=1.4):
    m.ctl(sf, 'tile', cid, X(x), y, w=w, h=h, col='ff3030', label=label)


def bar(m, cx, cz, phi, length, width, y0, y1, mat, name):
    """a straight bar centred on (cx, cz), its length along the direction phi degrees (from +x toward +z)"""
    phi = ((phi + 90) % 180) - 90
    if -45 <= phi <= 45:
        a = (cx - length / 2, y0, cz - width / 2); b = (cx + length / 2, y1, cz + width / 2); ang = -phi
    else:
        if phi < 0: phi += 180
        a = (cx - width / 2, y0, cz - length / 2); b = (cx + width / 2, y1, cz + length / 2); ang = 90 - phi
    ang = round(ang / 22.5) * 22.5
    rot = None if ang == 0 else {'axis': 'y', 'angle': ang, 'origin': [cx, (y0 + y1) / 2, cz]}
    m.box(a, b, mat, name, rot=rot)


def bolt_ring(m, cx, cz, r, y, n, mat='metal:steel', s=0.5, name='bolt'):
    for i in range(n):
        a = 2 * math.pi * i / n
        x, z = cx + r * math.cos(a), cz + r * math.sin(a)
        m.box((x - s / 2, y, z - s / 2), (x + s / 2, y + 0.3, z + s / 2), mat, name)


def pad(m, minimum, x0, x1, y, z):
    """real fixings (cable clips + screws along a rail) until the model reaches its detail bar"""
    g = m.group
    m.g('fixings')
    k = 0
    while len(m.boxes) < minimum and k < 120:
        x = x0 + (x1 - x0) * ((k % 24) / 23.0)
        yy = y + (k // 24) * 0.8
        m.box((x - 0.15, yy - 0.15, z - 0.12), (x + 0.15, yy + 0.15, z), 'metal:steel', 'fixing screw')
        k += 1
    m.g(g)


# ================================================================================================ wide desk shell
def desk_shell(m, title_labels):
    m.g('pedestals')
    for (x0, x1) in ((-15.5, -9.0), (25.0, 30.6)):
        m.box((x0, 0.8, 0.6), (x1, 11.4, 9.6), DARK, 'pedestal')
        m.box((x0, 0, 0.9), (x1, 0.8, 9.3), 'paint:#141416', 'kick plinth')
        m.box((x0, 0.8, 0.45), (x1, 1.2, 0.6), 'haz', 'kick strip', faces=('north',))
        for k in range(3):
            y0 = 1.6 + k * 3.2
            m.box((x0 + 0.3, y0, 0.4), (x1 - 0.3, y0 + 2.9, 0.6), 'paint:#3a3e44', 'drawer front')
            m.box(((x0 + x1) / 2 - 1.0, y0 + 2.0, 0.1), ((x0 + x1) / 2 + 1.0, y0 + 2.3, 0.4), 'metal:alu', 'drawer handle')
    m.box((-9.0, 1.0, 9.0), (25.0, 11.0, 9.6), DARK, 'modesty panel')
    m.box((-9.0, 0, 9.2), (25.0, 1.0, 9.6), 'paint:#141416', 'modesty kick')
    m.g('worktop')
    m.box((-16, 11.4, 0), (32, 12.0, 9.4), 'paint:#3c4048', 'worktop')
    m.box((-16, 11.2, -0.3), (32, 12.0, 0), 'paint:#1a1b1e', 'armrest edge')
    m.box((-16, 11.0, 0.2), (32, 11.4, 9.0), 'paint:#24272b', 'cable tray', faces=('down', 'north'))
    for (x0, x1, t) in title_labels:
        m.box((x0, 11.45, -0.35), (x1, 11.95, -0.3), 'label:' + t, 'edge label', faces=('north',))
    m.g('bridge')
    m.box((-16, 12.0, 9.4), (32, 30.0, 15.0), DARK, 'bridge body')
    m.box((-16, 30.0, 7.5), (32, 31.0, 15.0), 'paint:#3c4048', 'canopy')
    m.box((-16, 29.0, 7.6), (32, 30.0, 8.0), 'lamp:fff2d0', 'canopy downlight', faces=('down',))
    for x in (-16, 31.4):
        m.box((x, 12.0, 9.0), (x + 0.6, 31.0, 15.0), 'paint:#1e2024', 'bridge end cheek')
    m.g('leads')
    m.terminal_12v(30.2, 4.0, 5.0, 'east')
    m.gland(28.0, 0.8, 8.0, 'down', r=0.45, cable='cable:1d1d21')


# ================================================================================================ #5 ropeway
def ropeway_desk():
    m = Model('ropeway_desk')
    desk_shell(m, ((-15.0, -9.6, 'LINE'), (-6.0, 6.0, 'ROPEWAY CONTROL'), (17.0, 24.0, 'FAULTS')))
    m.g('desk furniture')
    m.box((-15.8, 12.0, 6.0), (-13.4, 12.6, 9.0), 'paint:#1e1f22', 'line phone base')
    m.box((-15.6, 12.6, 6.2), (-15.0, 13.1, 8.8), 'paint:#141416', 'handset')
    m.box((9.0, 12.0, 4.0), (15.0, 12.2, 9.0), 'paint:#e8e4d0', 'log book')
    m.box((9.2, 12.2, 4.2), (14.8, 12.25, 8.8), 'label:LINE LOG', 'log book cover', faces=('up',))
    m.box((10.0, 12.2, 2.0), (14.0, 12.3, 3.4), 'paint:#141416', 'pen tray')
    m.g('mimic + displays')
    m.box((-14.0, 18.0, 9.3), (12.0, 28.6, 9.4), 'screen:0a1420', 'mimic screen')
    m.box((-14.4, 17.6, 9.32), (12.4, 18.0, 9.4), 'paint:#111214', 'mimic bezel')
    m.box((-14.4, 28.6, 9.32), (12.4, 29.0, 9.4), 'paint:#111214', 'mimic bezel')
    m.box((-8.0, 29.05, 9.32), (6.0, 29.7, 9.38), 'label:LINE MIMIC', 'mimic label', faces=('north',))
    m.box((18.0, 13.2, 9.33), (31.0, 29.6, 9.4), 'screen:0c1210', 'readout panel')
    for (x, lab) in ((-11.0, 'SPEED'), (-5.0, 'WIND'), (1.0, 'MOTOR')):
        m.box((x - 2.0, 12.6, 9.3), (x + 2.0, 13.2, 9.38), 'label:' + lab, 'gauge label', faces=('north',))
    m.box((5.0, 13.0, 9.3), (12.0, 17.0, 9.38), 'plate:ROPEWAY|DESK', 'nameplate', faces=('north',))

    up = m.surface('up', face=16 - 12.0, title='ROPEWAY DESK')
    sel(m, up, 'k_en', -13.0, 2.4, 'ENABLE', ['OFF', 'ON'], t='key')
    push(m, up, 'start', -10.0, 2.4, '30c040', 'START', 1.5)
    push(m, up, 'stop', -7.4, 2.4, '202020', 'STOP', 1.5)
    push(m, up, 'reset', -4.8, 2.4, '2050d0', 'RESET')
    mush(m, up, 'm_estop', -1.4, 2.6)
    push(m, up, 'horn', 2.0, 2.4, '303438', 'HORN')
    push(m, up, 'ack', 4.4, 2.4, 'f0d020', 'ACK')
    push(m, up, 'test', 6.8, 2.4, 'f0f0f0', 'LAMP TEST')
    sel(m, up, 's_spd', -12.4, 6.0, 'SPEED M/S', ['0.5', '1', '2', '3', '4', '5', '6'])
    sel(m, up, 's_wlim', -8.4, 6.0, 'WIND LIMIT', ['10', '12', '15', '18', '22'])
    sel(m, up, 's_mode', -4.4, 6.0, 'MODE', ['AUTO', 'MANUAL', 'EVAC'])
    sel(m, up, 's_cab', -0.4, 6.0, 'CABINS', ['4', '6', '8', '12', '16', '24'])
    push(m, up, 'doors', 3.4, 6.0, 'e0a020', 'GATES')
    push(m, up, 'brel', 6.4, 6.0, 'c06010', 'BRAKE REL')
    for i, (cid, col, lab) in enumerate((('l_run', '30e060', 'RUN'), ('l_stop', 'ff3030', 'STOP'), ('l_eb', 'ff2020', 'E-BRAKE'), ('l_sb', 'ffb020', 'BRAKE'),
                                         ('l_doors', 'ffb020', 'GATES'), ('l_brel', 'ff8020', 'REL'), ('l_restr', 'ffd040', 'WIND RESTR'))):
        lamp(m, up, cid, -13.0 + i * 2.4, 8.6, col, lab)
    for i, (cid, lab) in enumerate((('t_estop', 'E-STOP'), ('t_wind', 'WIND'), ('t_rpd', 'ROPE POS'), ('t_sup', 'SUPPLY'), ('t_doors', 'DOORS'),
                                    ('t_ovs', 'OVERSPEED'), ('t_tens', 'TENSION'), ('t_motor', 'MOTOR'), ('t_drive', 'DRIVE'))):
        tile(m, up, cid, 19.0 + (i % 3) * 3.8, 2.0 + (i // 3) * 1.9, lab)

    br = m.surface('north', face=9.28, title='ROPEWAY BRIDGE')
    for (cid, x, lab) in (('g_spd', -11.0, 'M/S'), ('g_wind', -5.0, 'WIND'), ('g_kw', 1.0, 'KW %')):
        gauge(m, br, cid, x, 15.0, lab, 3.2)
    for i, cid in enumerate(('d_name', 'd_state', 'd_speed', 'd_rope', 'd_rpd', 'd_wind', 'd_motor', 'd_cars', 'd_next', 'd_tens')):
        digits(m, br, cid, 24.5, 28.6 - i * 1.55, 12.0, 0.9, 'ffb020' if i < 2 else '7cf29a')
    pad(m, 160, -15, 31, 12.3, 9.4)
    m.save(item_gui=0.3)
    return m


def ropeway_machine():
    m = Model('ropeway_machine')
    GREEN = 'paint:ral6005'
    m.g('skid')
    m.box((-16, 0, 1.0), (16, 1.6, 15.0), 'paint:#3a3e44', 'skid base')
    for z in (1.0, 13.6):
        m.box((-16, 1.6, z), (16, 2.2, z + 1.4), 'paint:#2a2d30', 'skid I-beam flange')
    for x in range(-15, 16, 4):
        m.bolt(x, 1.6, 2.0, 'up', 0.6)
        m.bolt(x, 1.6, 14.0, 'up', 0.6)
    m.box((-16, 0.2, 0.9), (16, 0.6, 1.0), 'haz', 'skid edge', faces=('north',))
    m.g('drive motor')
    m.cyl((-15.0, 8.0, 8.0), 5.2, 11.6, 'x', GREEN, 'motor frame')
    for i in range(10):
        m.cyl((-14.4 + i * 1.12, 8.0, 8.0), 5.7, 0.35, 'x', 'paint:#2f5a40', 'cooling fin')
    m.cyl((-16.0, 8.0, 8.0), 4.6, 1.0, 'x', 'mesh', 'fan cowl')
    m.cyl((-3.6, 8.0, 8.0), 3.6, 0.6, 'x', GREEN, 'D-end shield')
    for z in (3.4, 10.4):
        m.box((-14.0, 2.2, z), (-5.0, 3.6, z + 2.2), GREEN, 'motor foot')
        m.bolt(-13.0, 3.6, z + 1.1, 'up', 0.5); m.bolt(-6.0, 3.6, z + 1.1, 'up', 0.5)
    m.box((-12.0, 13.0, 5.0), (-6.0, 16.2, 11.0), GREEN, 'terminal box')
    m.box((-12.2, 16.2, 4.8), (-5.8, 16.6, 11.2), 'paint:#2f5a40', 'terminal box lid')
    for x in (-11.0, -9.0, -7.0):
        m.gland(x, 13.0, 11.0, 'south', r=0.5, cable='cable:202020', length=2.0)
    m.box((-11.0, 9.0, 2.7), (-6.0, 11.0, 2.8), 'plate:DRIVE MOTOR|400 KW 4160V', 'motor nameplate', faces=('north',))
    m.box((-9.4, 13.4, 4.9), (-7.6, 15.0, 5.0), 'danger:DANGER|HV', 'terminal danger', faces=('north',))
    for x in (-13.0, -5.0):
        m.box((x - 0.6, 13.2, 7.4), (x + 0.6, 14.6, 8.6), STEEL, 'lifting eye')
    m.g('service brake')
    for (y0, y1) in ((14.0, 15.8), (0.6, 2.4)):
        m.box((-2.4, y0, 5.6), (-0.8, y1, 10.4), 'paint:ral1023', 'service brake caliper')
        m.box((-2.0, y0 + 0.3, 4.6), (-1.2, y1 - 0.3, 5.6), 'paint:#c0a010', 'caliper piston')
        m.gland(-1.6, y0 + 0.9, 4.6, 'north', r=0.25, cable='cable:101010', length=1.5)
    m.box((-3.2, 14.6, 4.0), (0.8, 16.4, 12.0), 'mesh', 'coupling guard')
    m.g('gearbox')
    GB = 'paint:#4a5560'
    m.box((1.0, 2.2, 1.6), (15.0, 17.0, 14.4), GB, 'gearbox housing')
    for x in (2.0, 5.0, 8.0, 11.0, 14.0):
        m.box((x - 0.3, 2.2, 1.2), (x + 0.3, 17.0, 1.6), GB, 'housing rib')
    m.box((0.6, 9.0, 1.4), (15.4, 9.6, 14.6), 'paint:#3c464f', 'split flange')
    for x in range(1, 16, 2):
        m.bolt(x + 0.5, 9.6, 1.4, 'north', 0.45)
    m.cyl((-0.4, 8.0, 8.0), 2.6, 1.4, 'x', GB, 'input bearing cap')
    m.box((3.0, 4.0, 1.1), (5.0, 6.0, 1.2), 'glass', 'oil sight glass')
    m.box((3.2, 4.6, 1.05), (4.8, 4.8, 1.1), 'paint:#c08020', 'oil level')
    m.cyl((12.0, 17.0, 4.0), 0.6, 1.4, 'y', 'metal:brass', 'breather')
    m.cyl((13.0, 2.2, 2.0), 0.4, 0.8, 'x', 'metal:brass', 'oil drain')
    m.box((6.0, 12.0, 1.5), (12.0, 15.0, 1.6), 'plate:PLANETARY|72:1 GEARBOX', 'gearbox nameplate', faces=('north',))
    m.cyl((8.0, 17.0, 8.0), 1.7, 4.6, 'y', STEEL, 'output shaft')
    m.cyl((8.0, 17.0, 8.0), 3.0, 1.0, 'y', GB, 'output seal housing')
    m.box((9.0, 2.6, 14.4), (14.0, 6.0, 15.4), 'paint:#2a3036', 'oil cooler', south='fins')
    m.g('emergency rim brake')
    for (bx, bz) in ((8.0, -14.0), (8.0, 30.0), (-14.0, 8.0), (30.0, 8.0)):
        m.box((bx - 1.6, 20.4, bz - 1.0), (bx + 1.6, 26.0, bz + 1.0), 'paint:ral3000', 'rim brake caliper')
        m.box((bx - 1.0, 18.0, bz - 0.6), (bx + 1.0, 20.4, bz + 0.6), 'paint:#2a2d30', 'caliper post')
    m.cyl((8.0, 2.2, -14.0), 0.5, 15.8, 'y', 'paint:#2a2d30', 'caliper pedestal')
    m.g('control cabinet')
    CAB = 'paint:ral7035'
    m.box((17.0, 0.0, 0.4), (31.4, 30.0, 5.4), CAB, 'cabinet')
    m.box((16.8, 30.0, 0.2), (31.6, 30.6, 5.6), 'paint:#a8aaa6', 'cabinet roof')
    m.box((17.2, 0.4, 0.3), (31.2, 29.6, 0.4), CAB, 'cabinet door')
    m.box((30.4, 13.0, 0.0), (30.9, 17.0, 0.3), 'metal:alu', 'door handle')
    m.box((17.6, 29.0, 0.25), (31.0, 29.8, 0.3), 'label:DRIVE CONTROL', 'cabinet label', faces=('north',))
    m.louvres(18.0, 22.0, 1.0, 3.6, 0.3, n=4)
    m.louvres(26.0, 30.0, 1.0, 3.6, 0.3, n=4)
    m.g('brake HPU')
    m.box((17.0, 0.0, 6.0), (31.4, 8.6, 15.4), 'paint:#33506e', 'HPU oil tank')
    m.box((18.0, 3.0, 5.9), (21.0, 5.0, 6.0), 'glass', 'tank level glass')
    m.cyl((24.0, 8.6, 10.6), 2.6, 5.2, 'y', 'paint:#2f5a40', 'pump motor')
    m.cyl((24.0, 13.8, 10.6), 2.2, 0.8, 'y', 'mesh', 'pump fan cover')
    for x in (19.0, 29.0):
        m.cyl((x, 8.6, 12.6), 1.4, 9.0, 'y', 'paint:#c03020', 'accumulator')
        m.cyl((x, 17.6, 12.6), 0.5, 0.8, 'y', 'metal:brass', 'charging valve')
    m.box((25.0, 8.6, 6.4), (30.8, 11.0, 9.0), 'paint:#2a2d30', 'valve block')
    for x in (26.0, 27.6, 29.2):
        m.cyl((x, 11.0, 7.6), 0.4, 1.2, 'y', 'paint:ral1023', 'solenoid valve')
    m.cyl((26.0, 13.0, 7.4), 0.9, 0.2, 'z', 'gauge', 'HPU pressure gauge')
    m.g('evacuation drive')
    m.box((18.0, 17.0, 7.0), (26.0, 22.0, 13.0), 'paint:ral2004', 'evac hydraulic motor')
    m.cyl((20.0, 22.0, 10.0), 1.2, 1.4, 'y', STEEL, 'evac pinion')
    m.box((18.2, 18.0, 6.9), (25.8, 20.0, 7.0), 'label:EVAC DRIVE', 'evac label', faces=('north',))
    m.box((26.0, 17.0, 9.0), (31.0, 20.0, 12.0), 'paint:#3a3e44', 'evac engine')
    m.box((31.0, 18.0, 10.0), (31.6, 19.0, 11.0), 'metal:steel', 'exhaust')
    m.g('hoses')
    for (x, y, z, l) in ((16.0, 4.0, 11.0, 2.0), (2.0, 19.0, 9.0, 4.0)):
        m.cyl((x, y, z), 0.3, l, 'x', 'cable:101010', 'brake hose')
    m.g('supply')
    m.hv_box(-9.0, 4.0, 15.0, 'south')
    m.terminal_12v(26.0, 22.0, 15.4, 'south')
    m.box((-14.0, 18.0, 15.0), (-4.0, 21.0, 15.1), 'danger:DANGER|ROTATING MACHINERY', 'danger sign', faces=('south',))

    sf = m.surface('north', face=0.25, title='DRIVE CONTROL')
    for i, (cid, col, lab) in enumerate((('l_pwr', '30e060', '12V'), ('l_sup', '30e060', 'SUPPLY'), ('l_run', '30e060', 'RUN'), ('l_hpu', '30e060', 'HPU'),
                                         ('l_sb', 'ffb020', 'BRAKE'), ('l_eb', 'ff2020', 'E-BRAKE'), ('l_local', 'ffd040', 'LOCAL'), ('l_flt', 'ff3030', 'FAULT'), ('l_evac', 'ff8020', 'EVAC'))):
        lamp(m, sf, cid, 18.4 + i * 1.5, 27.4, col, lab, 0.6)
    for (cid, x, y, lab) in (('g_kw', 19.6, 23.0, 'KW %'), ('g_amp', 24.2, 23.0, 'AMPS'), ('g_rpm', 28.8, 23.0, 'RPM'), ('g_bar', 21.9, 18.8, 'BRAKE BAR'), ('g_oil', 26.5, 18.8, 'OIL C')):
        gauge(m, sf, cid, x, y, lab, 3.0)
    for i, cid in enumerate(('d_state', 'd_kw', 'd_rpm', 'd_bar', 'd_temp', 'd_sup')):
        digits(m, sf, cid, 24.2, 15.6 - i * 1.25, 12.4, 0.85, 'ffb020' if i == 0 else '7cf29a')
    push(m, sf, 'hpu', 19.2, 6.2, '30c040', 'HPU START/STOP', 1.3)
    push(m, sf, 'reset', 22.0, 6.2, '2050d0', 'RESET', 1.3)
    push(m, sf, 'test', 24.8, 6.2, 'f0f0f0', 'LAMP TEST', 1.1)
    sel(m, sf, 'k_local', 27.8, 6.2, 'CONTROL', ['REMOTE', 'LOCAL'], t='key')
    mush(m, sf, 'm_estop', 24.0, 3.0, 'E-STOP', 1.8)
    pad(m, 220, -15, 15, 0.8, 1.0)
    m.save(item_gui=0.3)
    return m


def mach_wheel():
    """the machine room's drive bullwheel (spun by Java about x=8, z=8)"""
    m = Model('ropeway_mach_wheel')
    m.g('bullwheel')
    m.cyl((8, 21.6, 8), 21.0, 1.0, 'y', 'paint:#4a5560', 'wheel web')
    m.cyl((8, 21.0, 8), 22.0, 2.6, 'y', 'paint:#3c464f', 'wheel rim')
    m.cyl((8, 21.8, 8), 22.4, 1.0, 'y', 'rubber', 'rope groove liner')
    m.cyl((8, 20.4, 8), 3.6, 4.0, 'y', STEEL, 'hub')
    for ang in (0, 45, 90, 135):
        bar(m, 8, 8, ang, 40.0, 1.4, 22.6, 23.4, 'paint:#5a6670', 'spoke')
    bolt_ring(m, 8, 8, 2.6, 24.4, 8, name='hub bolt')
    bolt_ring(m, 8, 8, 19.6, 23.6, 24, name='rim bolt')
    m.box((26.0, 22.6, 7.0), (29.6, 23.7, 9.0), 'paint:ral1023', 'index mark')
    m.box((-13.6, 22.6, 7.0), (-10.0, 23.7, 9.0), 'paint:ral9010', 'index mark')
    m.save(item_gui=0.2)
    return m


def mach_rotor():
    """motor shaft + coupling + service brake disc (spun by Java about the x axis through y=8, z=8)"""
    m = Model('ropeway_mach_rotor')
    m.g('rotor')
    m.cyl((-3.4, 8, 8), 1.0, 4.4, 'x', STEEL, 'shaft')
    m.cyl((-3.0, 8, 8), 2.4, 1.0, 'x', 'paint:#4a5560', 'motor coupling hub')
    m.cyl((-0.6, 8, 8), 2.4, 1.0, 'x', 'paint:#4a5560', 'gearbox coupling hub')
    m.cyl((-1.9, 8, 8), 6.6, 0.6, 'x', 'metal:steel', 'brake disc')
    for i in range(8):
        a = 2 * math.pi * i / 8
        y, z = 8 + 5.0 * math.cos(a), 8 + 5.0 * math.sin(a)
        m.box((-2.0, y - 0.35, z - 0.35), (-1.2, y + 0.35, z + 0.35), 'paint:#2a2d30', 'disc vent hole')
        y2, z2 = 8 + 1.7 * math.cos(a), 8 + 1.7 * math.sin(a)
        m.box((-2.1, y2 - 0.2, z2 - 0.2), (-0.4, y2 + 0.2, z2 + 0.2), 'metal:steel', 'coupling bolt')
    m.box((-1.95, 13.6, 7.6), (-1.25, 14.5, 8.4), 'paint:ral1023', 'disc wear mark')
    m.save(item_gui=0.4)
    return m


def station_pedestal(m, x0=11.5, z0=0.8):
    """the attendant pedestal with its live panel (same controls on both station kinds)"""
    m.g('attendant pedestal')
    PED = 'paint:ral7035'
    m.box((x0, 3.0, z0), (x0 + 4.2, 15.0, z0 + 3.4), PED, 'pedestal')
    m.box((x0 - 0.2, 15.0, z0 - 0.2), (x0 + 4.4, 15.6, z0 + 3.6), 'paint:#a8aaa6', 'pedestal hood')
    m.box((x0 + 0.2, 3.0, z0 + 3.4), (x0 + 4.0, 7.0, z0 + 3.6), PED, 'cable box')
    m.gland(x0 + 1.2, 3.0, z0 + 2.0, 'down', r=0.35, cable='cable:1d1d21')
    m.gland(x0 + 3.0, 3.0, z0 + 2.0, 'down', r=0.25, cable='cable:2060c0')
    m.terminal_12v(x0 + 2.1, 8.0, z0 + 3.4, 'south')
    sf = m.surface('north', face=z0 - 0.05, title='STATION')
    digits(m, sf, 'd_line', x0 + 2.1, 14.2, 3.9, 0.7, 'ffb020')
    digits(m, sf, 'd_dep', x0 + 2.1, 13.2, 3.9, 0.7)
    for i, (cid, col, lab) in enumerate((('l_closed', '30e060', 'GATES SHUT'), ('l_open', 'ffb020', 'GATES OPEN'), ('l_car', 'f0f0f0', 'CAR IN'), ('l_run', '30e060', 'LINE RUN'))):
        lamp(m, sf, cid, x0 + 0.7 + i * 0.95, 12.0, col, lab, 0.5)
    for i, (cid, col, lab) in enumerate((('l_flt', 'ff3030', 'FAULT'), ('l_ready', 'f0f0f0', 'READY'), ('l_hold', 'ffb020', 'HOLD'))):
        lamp(m, sf, cid, x0 + 0.7 + i * 0.95, 11.0, col, lab, 0.5)
    push(m, sf, 'dopen', x0 + 0.9, 9.4, '30c040', 'GATES OPEN', 0.9)
    push(m, sf, 'dclose', x0 + 2.1, 9.4, 'd02020', 'GATES CLOSE', 0.9)
    push(m, sf, 'ready', x0 + 3.3, 9.4, 'f0f0f0', 'READY', 0.9)
    push(m, sf, 'hold', x0 + 0.9, 8.0, 'ffb020', 'HOLD', 0.9)
    push(m, sf, 'test', x0 + 2.1, 8.0, 'f0f0f0', 'TEST', 0.7)
    mush(m, sf, 'm_estop', x0 + 2.1, 5.6, 'STOP', 1.5)


def ropeway_station():
    m = Model('ropeway_station')
    m.g('platform')
    m.box((0, 0, 0), (16, 2.6, 16), 'concrete', 'platform slab')
    m.box((0, 2.6, 0), (16, 3.0, 16), 'paint:#5a5e62', 'platform surface')
    m.box((0, 2.95, 0.4), (16, 3.05, 1.2), 'paint:ral1023', 'platform edge line')
    for x in range(1, 16, 2):
        m.box((x - 0.4, 3.0, 1.6), (x + 0.4, 3.12, 2.4), 'paint:#c8c8c0', 'tactile paving')
    m.box((0, 0, -0.1), (16, 2.6, 0), 'paint:#3a3e42', 'platform nosing', faces=('north',))
    for z in (4.0, 8.0, 12.0):
        m.box((0.2, 3.0, z - 0.1), (11.0, 3.06, z + 0.1), 'paint:#4a4e52', 'grating seam')
    m.g('gate')
    for x in (1.0, 9.0):
        m.box((x - 0.4, 3.0, 2.6), (x + 0.4, 11.0, 3.4), 'paint:#3a3e44', 'gate post')
        m.box((x - 0.5, 11.0, 2.5), (x + 0.5, 11.4, 3.5), 'paint:#2a2d30', 'post cap')
    m.box((1.4, 7.0, 2.8), (8.6, 7.4, 3.2), 'paint:ral1023', 'gate bar')
    m.box((1.4, 4.0, 2.8), (8.6, 4.4, 3.2), 'paint:ral1023', 'gate bar')
    for x in (3.0, 5.0, 7.0):
        m.box((x - 0.15, 4.4, 2.85), (x + 0.15, 7.0, 3.15), 'paint:ral1023', 'gate rail')
    m.box((0.6, 9.0, 2.5), (1.4, 10.0, 2.6), 'lamp:30e060', 'gate lamp', faces=('north',))
    m.box((2.0, 8.0, 2.75), (8.0, 9.0, 2.8), 'label:MIND THE GAP', 'gate sign', faces=('north',))
    m.g('signs + lights')
    m.box((0.4, 11.0, 14.0), (1.2, 26.0, 14.8), 'paint:#3a3e44', 'lamp column')
    m.box((0.2, 26.0, 12.0), (3.0, 26.6, 15.0), 'paint:#2a2d30', 'lamp head')
    m.box((0.6, 25.8, 12.4), (2.6, 26.0, 14.6), 'lamp:fff2d0', 'lamp lens', faces=('down',))
    m.box((1.2, 18.0, 14.0), (7.0, 21.0, 14.1), 'plate:ROPEWAY|STATION', 'station sign', faces=('north',))
    m.box((4.0, 3.0, 12.0), (8.0, 6.0, 15.0), 'paint:#5a3a20', 'bench seat')
    for x in (4.4, 7.6):
        m.box((x - 0.2, 3.0, 12.2), (x + 0.2, 4.6, 14.8), 'paint:#2a2d30', 'bench leg')
    station_pedestal(m)
    pad(m, 90, 0.5, 15.5, 0.6, 0.0)
    m.save(item_gui=0.6)
    return m


def funicular_station():
    m = Model('funicular_station', block='funicular_station')
    m.g('platform')
    m.box((0, 0, 0), (16, 3.4, 16), 'concrete', 'platform base')
    for i in range(4):
        m.box((0, 3.4, 10 + i * 1.5), (16, 3.4 + (i + 1) * 0.8, 11.5 + i * 1.5), 'concrete', 'platform step')
        m.box((0, 3.3 + (i + 1) * 0.8, 10 + i * 1.5), (16, 3.5 + (i + 1) * 0.8, 10.3 + i * 1.5), 'paint:ral1023', 'step nosing')
    m.box((0, 3.4, 0), (16, 3.6, 10), 'paint:#5a5e62', 'platform surface')
    m.box((0, 3.55, 0.4), (16, 3.65, 1.2), 'paint:ral1023', 'platform edge line')
    m.g('buffer stop + sheave pit')
    m.box((5.0, 3.6, 0.2), (11.0, 7.0, 2.0), 'paint:ral3000', 'buffer beam')
    for x in (5.6, 7.6, 9.6):
        m.box((x, 4.0, 0.15), (x + 0.8, 6.6, 0.2), 'paint:ral9010', 'buffer stripe', faces=('north',))
    for x in (6.0, 10.0):
        m.cyl((x, 5.3, 0.0), 0.8, 0.6, 'z', STEEL, 'buffer head')
    m.box((3.0, 3.58, 3.0), (13.0, 3.62, 9.0), 'mesh', 'sheave pit grating')
    m.cyl((8.0, 1.0, 6.0), 4.2, 1.2, 'y', 'paint:#4a5560', 'return sheave')
    m.cyl((8.0, 2.2, 6.0), 1.0, 1.2, 'y', STEEL, 'sheave hub')
    m.g('canopy')
    for x in (0.6, 15.4):
        m.box((x - 0.5, 3.6, 13.5), (x + 0.5, 30.0, 14.5), 'paint:#3a3e44', 'canopy column')
    m.box((-1.0, 30.0, 2.0), (17.0, 31.0, 16.0), 'paint:#5a6066', 'canopy roof')
    m.box((-1.0, 29.0, 2.0), (17.0, 30.0, 2.4), 'paint:#2a2d30', 'canopy fascia')
    m.box((2.0, 29.2, 1.9), (14.0, 29.9, 2.0), 'label:FUNICULAR', 'canopy sign', faces=('north',))
    for x in (4.0, 12.0):
        m.box((x - 1.5, 28.8, 6.0), (x + 1.5, 29.0, 9.0), 'lamp:fff2d0', 'canopy light', faces=('down',))
    m.g('gate')
    for x in (1.0, 9.0):
        m.box((x - 0.4, 3.6, 2.6), (x + 0.4, 11.0, 3.4), 'paint:#3a3e44', 'gate post')
    m.box((1.4, 7.0, 2.8), (8.6, 7.4, 3.2), 'paint:ral1023', 'gate bar')
    m.box((1.4, 4.4, 2.8), (8.6, 4.8, 3.2), 'paint:ral1023', 'gate bar')
    station_pedestal(m, 11.5, 0.8)
    pad(m, 90, 0.5, 15.5, 0.8, 0.0)
    m.save(item_gui=0.5)
    return m


def station_frame():
    """the bullwheel column + detached-cabin rail (drawn by Java turned to the rope, origin 1 block above the platform)"""
    m = Model('ropeway_station_frame')
    m.g('column')
    m.box((4.0, -16.0, 4.0), (12.0, -15.0, 12.0), STEEL, 'base plate')
    for (x, z) in ((4.6, 4.6), (11.4, 4.6), (4.6, 11.4), (11.4, 11.4)):
        m.bolt(x, -15.0, z, 'up', 0.6)
    m.cyl((8.0, -15.0, 8.0), 2.4, 41.0, 'y', 'paint:#4a5560', 'bullwheel column')
    m.cyl((8.0, 26.0, 8.0), 3.4, 3.4, 'y', 'paint:#3c464f', 'bearing housing')
    bolt_ring(m, 8.0, 8.0, 2.8, 29.4, 8, name='bearing bolt')
    m.g('cabin rail')
    R = 21.0                                                      # (the model's limits: just inside the rope's 22.4 px arc)
    for k in range(8):
        if k == 2: continue                                       # open toward the line
        th = math.radians(k * 45)
        cx, cz = 8 + R * math.cos(th), 8 + R * math.sin(th)
        bar(m, cx, cz, k * 45 + 90, 2 * R * math.tan(math.radians(22.5)), 1.0, 29.4, 30.4, STEEL, 'station rail')
        bar(m, (8 + cx) / 2, (8 + cz) / 2, k * 45, R, 0.8, 28.0, 29.0, 'paint:#3c464f', 'rail arm')
    m.g('conveyor tyres')
    for k in range(16):
        if 5 <= k <= 7: continue
        th = math.radians(k * 22.5)
        x, z = 8 + (R - 1.2) * math.cos(th), 8 + (R - 1.2) * math.sin(th)
        m.cyl((x, 30.6, z), 0.7, 0.6, 'y', 'rubber', 'conveyor tyre')
    m.g('ladder')
    m.ladder(9.8, 11.8, -15.0, 25.0, 10.4)
    m.save(item_gui=0.25)
    return m


def bullwheel():
    """a station's bullwheel (spun by Java; its rope groove is 4 px up from its origin)"""
    m = Model('ropeway_bullwheel')
    m.g('bullwheel')
    m.cyl((8, 2.4, 8), 21.0, 0.8, 'y', 'paint:#4a5560', 'wheel web')
    m.cyl((8, 2.8, 8), 22.0, 2.4, 'y', 'paint:#3c464f', 'wheel rim')
    m.cyl((8, 3.6, 8), 22.4, 0.8, 'y', 'rubber', 'rope groove liner')
    m.cyl((8, 0.0, 8), 3.4, 5.0, 'y', STEEL, 'hub')
    for ang in (0, 45, 90, 135):
        bar(m, 8, 8, ang, 40.0, 1.4, 3.2, 4.0, 'paint:#5a6670', 'spoke')
    bolt_ring(m, 8, 8, 2.4, 5.0, 8, name='hub bolt')
    bolt_ring(m, 8, 8, 19.6, 3.2, 24, name='rim bolt')
    m.box((26.0, 3.2, 7.0), (29.6, 4.2, 9.0), 'paint:ral1023', 'index mark')
    m.box((-13.6, 3.2, 7.0), (-10.0, 4.2, 9.0), 'paint:ral9010', 'index mark')
    m.save(item_gui=0.2)
    return m


def pylon():
    m = Model('ropeway_pylon')
    m.g('lattice')
    for (x, z) in ((3.2, 3.2), (12.8, 3.2), (3.2, 12.8), (12.8, 12.8)):
        m.box((x - 0.6, 0, z - 0.6), (x + 0.6, 16, z + 0.6), GALV, 'corner leg')
        m.box((x - 0.8, 7.6, z - 0.8), (x + 0.8, 8.4, z + 0.8), GALV, 'leg splice')
        for y in (2.0, 14.0):
            m.bolt(x, y, z - 0.6, 'north', 0.4)
    for y in (0.4, 8.0, 15.6):
        m.box((3.2, y - 0.3, 2.9), (12.8, y + 0.3, 3.5), GALV, 'horizontal')
        m.box((3.2, y - 0.3, 12.5), (12.8, y + 0.3, 13.1), GALV, 'horizontal')
        m.box((2.9, y - 0.3, 3.2), (3.5, y + 0.3, 12.8), GALV, 'horizontal')
        m.box((12.5, y - 0.3, 3.2), (13.1, y + 0.3, 12.8), GALV, 'horizontal')
    for (y0, y1) in ((0.4, 8.0), (8.0, 15.6)):
        yc = (y0 + y1) / 2
        L = math.hypot(9.6, y1 - y0)
        for z in (3.2, 12.8):
            for ang in (45, -45):
                m.box((8 - L / 2, yc - 0.25, z - 0.25), (8 + L / 2, yc + 0.25, z + 0.25), GALV, 'X brace', rot={'axis': 'z', 'angle': ang, 'origin': [8, yc, z]})
        for x in (3.2, 12.8):
            for ang in (45, -45):
                m.box((x - 0.25, yc - 0.25, 8 - L / 2), (x + 0.25, yc + 0.25, 8 + L / 2), GALV, 'X brace', rot={'axis': 'x', 'angle': ang, 'origin': [x, yc, 8]})
    m.g('ladder + cage')
    m.ladder(6.6, 9.4, 0.0, 16.0, 2.0)
    for y in (3.0, 9.0, 15.0):
        m.box((6.0, y, 0.4), (10.0, y + 0.3, 0.7), GALV, 'cage hoop')
    m.box((6.0, 0.0, 0.4), (6.3, 16.0, 0.7), GALV, 'cage bar')
    m.box((9.7, 0.0, 0.4), (10.0, 16.0, 0.7), GALV, 'cage bar')
    m.g('earthing + signs')
    m.cyl((12.8, 0.0, 12.0), 0.15, 6.0, 'y', 'metal:copper', 'earth strap')
    m.box((4.0, 5.0, 2.8), (8.0, 7.0, 2.9), 'danger:DANGER|NO CLIMBING', 'climb sign', faces=('north',))
    m.box((9.6, 10.0, 2.8), (12.0, 11.4, 2.9), 'plate:PYLON', 'number plate', faces=('north',))
    m.save(item_gui=0.6)
    return m


def pylon_head():
    """crossarm + sheave trains (drawn by Java on the top pylon section, turned to the rope; rope on the sheaves at y=16)"""
    m = Model('ropeway_pylon_head')
    m.g('head frame')
    m.box((3.0, 0.0, 3.0), (13.0, 9.0, 13.0), GALV, 'head frame', north='mesh', south='mesh')
    m.box((2.6, 0.0, 2.6), (13.4, 1.0, 13.4), GALV, 'head flange')
    m.box((-16.0, 9.0, 6.6), (32.0, 11.0, 9.4), GALV, 'crossarm')
    m.box((-16.0, 11.0, 7.4), (32.0, 11.4, 8.6), GALV, 'crossarm top chord')
    for x in range(-14, 31, 4):
        m.box((x - 0.2, 9.0, 6.4), (x + 0.2, 11.0, 6.6), GALV, 'crossarm stiffener')
    m.g('sheave trains')
    for sx in (8 - 22.4, 8 + 22.4):
        m.box((sx - 0.9, 11.4, -8.0), (sx + 0.9, 13.0, 24.0), 'paint:#4a5560', 'balance beam')
        m.box((sx - 1.2, 11.0, 7.0), (sx + 1.2, 12.0, 9.0), STEEL, 'beam pivot')
        for z in (-6.0, 2.0, 14.0, 22.0):
            m.cyl((sx - 0.6, 14.8, z), 1.2, 1.2, 'x', 'paint:#3c464f', 'sheave')
            m.cyl((sx - 0.7, 14.8, z), 1.25, 0.4, 'x', 'rubber', 'sheave liner')
            m.box((sx - 1.0, 12.6, z - 0.3), (sx + 1.0, 14.8, z + 0.3), 'paint:#4a5560', 'sheave cheek')
        for z in (-8.0, 24.0):
            m.box((sx - 1.6, 13.0, z - 0.3), (sx - 1.2, 17.0, z + 0.3), 'paint:ral1023', 'rope catcher')
            m.box((sx + 1.2, 13.0, z - 0.3), (sx + 1.6, 17.0, z + 0.3), 'paint:ral1023', 'rope catcher')
        d = -1 if sx > 8 else 1
        m.box((min(sx + d * 1.0, sx + d * 2.2), 12.0, 4.0), (max(sx + d * 1.0, sx + d * 2.2), 13.4, 6.0), 'paint:#c03020', 'rope position detector')
        m.cyl((sx + d * 1.6, 9.0, 5.0), 0.2, 3.0, 'y', 'cable:202020', 'RPD cable')
    m.g('platform')
    m.box((-14.0, 8.6, 9.4), (30.0, 9.0, 12.0), 'mesh', 'service walkway')
    m.handrail([(-14.0, 11.8), (30.0, 11.8)], 9.0, h=3.0)
    m.box((4.0, 4.0, 2.9), (12.0, 6.0, 3.0), 'plate:SHEAVE TRAIN|RPD FITTED', 'head plate', faces=('north',))
    m.save(item_gui=0.25)
    return m


def tension():
    m = Model('ropeway_tension')
    m.g('frame')
    for (x, z) in ((1.0, 1.0), (15.0, 1.0), (1.0, 15.0), (15.0, 15.0)):
        m.box((x - 0.8, 0, z - 0.8), (x + 0.8, 26.0, z + 0.8), GALV, 'frame post')
        m.box((x - 1.2, 0, z - 1.2), (x + 1.2, 0.6, z + 1.2), STEEL, 'base plate')
    for y in (10.0, 18.0, 25.4):
        m.box((0.2, y, 0.4), (15.8, y + 0.6, 1.6), GALV, 'tie')
        m.box((0.2, y, 14.4), (15.8, y + 0.6, 15.6), GALV, 'tie')
    for x in (1.0, 15.0):
        m.box((x - 0.4, 2.0, 1.8), (x + 0.4, 28.0, 2.4), STEEL, 'weight guide')
        m.box((x - 0.4, 2.0, 13.6), (x + 0.4, 28.0, 14.2), STEEL, 'weight guide')
    m.box((0.0, 26.0, 0.0), (16.0, 27.4, 16.0), GALV, 'head beam')
    m.cyl((7.4, 29.2, 8.0), 2.6, 1.2, 'x', 'paint:#4a5560', 'deflection sheave')
    m.cyl((7.2, 29.2, 8.0), 0.7, 1.6, 'x', STEEL, 'sheave axle')
    for x in (6.6, 8.8):
        m.box((x, 27.4, 7.0), (x + 0.4, 29.6, 9.0), GALV, 'sheave cheek')
    m.g('carriage scale')
    m.box((15.8, 2.0, 3.0), (16.4, 28.0, 5.0), 'paint:ral9010', 'position scale')
    for i in range(11):
        y = 2.4 + i * 2.5
        m.box((16.4, y, 3.2), (16.6, y + 0.2, 4.4 if i % 5 else 4.8), 'paint:#141416', 'scale mark')
    m.box((15.8, 26.0, 2.8), (16.6, 28.0, 5.2), 'paint:ral3000', 'HIGH limit band')
    m.box((15.8, 2.0, 2.8), (16.6, 4.0, 5.2), 'paint:ral3000', 'LOW limit band')
    m.g('monitor')
    MON = 'paint:ral7035'
    m.box((1.6, 9.0, -1.4), (7.4, 17.0, 0.6), MON, 'monitor box')
    m.box((1.4, 17.0, -1.6), (7.6, 17.4, 0.8), 'paint:#a8aaa6', 'monitor hood')
    m.gland(4.5, 9.0, -0.4, 'down', r=0.3, cable='cable:2060c0', length=2.0)
    m.terminal_12v(10.0, 9.0, 0.4, 'north')
    m.box((8.0, 20.0, 0.3), (14.0, 24.0, 0.4), 'danger:DANGER|MOVING WEIGHT', 'danger sign', faces=('north',))
    sf = m.surface('north', face=-1.45, title='TENSION')
    digits(m, sf, 'd_kn', 4.5, 15.6, 4.8, 0.8, 'ffb020')
    digits(m, sf, 'd_pos', 4.5, 14.6, 4.8, 0.8)
    gauge(m, sf, 'g_pos', 4.5, 11.6, 'CARR %', 2.6)
    lamp(m, sf, 'l_hi', 2.6, 9.8, 'ff3030', 'HIGH', 0.5)
    lamp(m, sf, 'l_ok', 3.8, 9.8, '30e060', 'OK', 0.5)
    lamp(m, sf, 'l_lo', 5.0, 9.8, 'ff3030', 'LOW', 0.5)
    lamp(m, sf, 'l_pwr', 6.4, 9.8, '30e060', '12V', 0.4)
    push(m, sf, 'test', 6.6, 15.0, 'f0f0f0', 'TEST', 0.6)
    pad(m, 90, 1.5, 14.5, 0.8, 0.4)
    m.save(item_gui=0.4)
    return m


def tension_weight():
    m = Model('ropeway_tension_weight')
    m.g('counterweight')
    m.box((2.6, 0.0, 2.6), (13.4, 9.0, 13.4), 'concrete', 'concrete block')
    m.box((2.4, 8.6, 2.4), (13.6, 9.4, 13.6), STEEL, 'top plate')
    m.box((2.0, 3.0, 2.0), (2.6, 6.0, 4.0), 'rubber', 'guide shoe')
    m.box((13.4, 3.0, 2.0), (14.0, 6.0, 4.0), 'rubber', 'guide shoe')
    m.box((2.0, 3.0, 12.0), (2.6, 6.0, 14.0), 'rubber', 'guide shoe')
    m.box((13.4, 3.0, 12.0), (14.0, 6.0, 14.0), 'rubber', 'guide shoe')
    m.box((7.0, 9.4, 7.0), (9.0, 11.0, 9.0), STEEL, 'lifting lug')
    m.cyl((8.0, 11.0, 8.0), 0.3, 10.0, 'y', ROPE, 'tension rope')
    m.box((4.0, 4.0, 2.5), (12.0, 7.0, 2.6), 'label:120 KN', 'weight label', faces=('north',))
    for x in (3.4, 12.6):
        for z in (3.4, 12.6):
            m.bolt(x, 9.4, z, 'up', 0.5)
    m.save(item_gui=0.5)
    return m


def track(steep=False):
    name = 'funicular_track_steep' if steep else 'funicular_track'
    m = Model(name, block='funicular_track')
    y0 = 8.0 if steep else 0.0
    z0, z1 = (8 - 8 * math.sqrt(2), 8 + 8 * math.sqrt(2)) if steep else (0.0, 16.0)
    rot = (lambda: {'axis': 'x', 'angle': 45, 'origin': [8, 8, 8]}) if steep else (lambda: None)

    def b(a, c, mat, nm, **kw):
        m.box((a[0], a[1] + y0, a[2]), (c[0], c[1] + y0, c[2]), mat, nm, rot=rot(), **kw)
    m.g('bed')
    b((0.5, -1.0 if steep else 0.0, z0), (15.5, 0.6 if not steep else 0.0, z1), 'concrete', 'track bed')
    m.g('sleepers')
    L = z1 - z0
    n = 4 if not steep else 6
    for i in range(n):
        z = z0 + (i + 0.5) * L / n
        b((0.5, 0.6 if not steep else 0.0, z - 1.0), (15.5, 2.0 if not steep else 1.4, z + 1.0), 'wood', 'sleeper')
        for x in (2.0, 14.0):
            b((x - 0.9, 2.0 if not steep else 1.4, z - 0.6), (x + 0.9, 2.3 if not steep else 1.7, z + 0.6), STEEL, 'base plate')
            b((x - 1.1, 2.3 if not steep else 1.7, z - 0.2), (x - 0.7, 2.7 if not steep else 2.1, z + 0.2), 'metal:steel', 'rail clip')
            b((x + 0.7, 2.3 if not steep else 1.7, z - 0.2), (x + 1.1, 2.7 if not steep else 2.1, z + 0.2), 'metal:steel', 'rail clip')
    m.g('rails')
    base = 2.0 if not steep else 1.4
    for x in (2.0, 14.0):
        b((x - 0.7, base + 0.3, z0), (x + 0.7, base + 0.7, z1), 'metal:steel', 'rail foot')
        b((x - 0.2, base + 0.7, z0), (x + 0.2, base + 1.5, z1), 'metal:steel', 'rail web')
        b((x - 0.5, base + 1.5, z0), (x + 0.5, base + 2.2, z1), 'metal:steel', 'rail head')
    m.g('rope rollers')
    for i in range(2):
        z = z0 + (i + 0.5) * L / 2
        b((6.0, base, z - 0.6), (10.0, base + 0.4, z + 0.6), 'paint:#3a3e44', 'roller frame')
        b((6.8, base + 0.4, z - 0.5), (9.2, base + 1.4, z + 0.5), 'rubber', 'rope roller')
        b((5.6, base, z - 0.4), (6.2, base + 1.6, z + 0.4), 'paint:#3a3e44', 'roller cheek')
        b((9.8, base, z - 0.4), (10.4, base + 1.6, z + 0.4), 'paint:#3a3e44', 'roller cheek')
    b((7.85, base + 1.3, z0), (8.15, base + 1.6, z1), ROPE, 'haul rope')
    m.save(item_gui=0.6)
    return m


def cabin_gondola():
    """8-seat detachable gondola cabin at 1/2 scale (1 px = 12.5 cm): floor at y=0, grip on the rope at y=23.2"""
    m = Model('ride_cabin_gondola')
    BODY = 'paint:#d02a3a'
    m.g('floor pan')
    m.box((1.0, 0.0, 1.0), (15.0, 1.0, 15.0), 'paint:#2a2d30', 'floor pan')
    m.box((1.4, 1.0, 1.4), (14.6, 1.2, 14.6), 'paint:#3a3e44', 'floor')
    for z in (3.0, 6.0, 9.0, 12.0):
        m.box((1.6, 1.2, z), (14.4, 1.25, z + 0.3), 'rubber', 'floor tread')
    m.g('body')
    m.box((1.0, 1.0, 1.0), (15.0, 6.0, 1.6), BODY, 'front skirt')
    m.box((1.0, 1.0, 14.4), (15.0, 6.0, 15.0), BODY, 'back skirt')
    m.box((1.0, 1.0, 1.6), (1.6, 6.0, 14.4), BODY, 'side skirt')
    m.box((14.4, 1.0, 1.6), (15.0, 6.0, 14.4), BODY, 'side skirt')
    m.box((1.0, 15.0, 1.0), (15.0, 17.6, 15.0), BODY, 'roof')
    m.box((2.0, 17.6, 2.0), (14.0, 18.0, 14.0), 'paint:#b02030', 'roof crown')
    for (x0, z0, x1, z1) in ((1.0, 1.0, 1.6, 1.6), (14.4, 1.0, 15.0, 1.6), (1.0, 14.4, 1.6, 15.0), (14.4, 14.4, 15.0, 15.0)):
        m.box((x0, 6.0, z0), (x1, 15.0, z1), 'paint:#20222a', 'corner pillar')
    m.g('glazing')
    m.box((1.6, 6.0, 1.1), (14.4, 15.0, 1.4), 'glass', 'front window')
    m.box((1.6, 6.0, 14.6), (14.4, 15.0, 14.9), 'glass', 'back window')
    m.box((1.1, 6.0, 1.6), (1.4, 15.0, 14.4), 'glass', 'side window')
    m.box((14.6, 6.0, 1.6), (14.9, 15.0, 7.8), 'glass', 'door leaf')
    m.box((14.6, 6.0, 8.2), (14.9, 15.0, 14.4), 'glass', 'door leaf')
    m.box((14.9, 1.2, 7.8), (15.2, 15.0, 8.2), 'paint:#20222a', 'door seal')
    for z in (4.0, 12.0):
        m.box((15.0, 9.0, z - 0.2), (15.3, 11.0, z + 0.2), 'metal:alu', 'door handle')
    m.box((14.9, 15.0, 1.6), (15.4, 15.6, 14.4), 'paint:#20222a', 'door track')
    m.box((14.9, 0.6, 1.6), (15.4, 1.0, 14.4), 'paint:#20222a', 'door sill track')
    m.g('benches')
    for z0 in (1.8, 11.6):
        m.box((2.0, 1.2, z0), (14.0, 4.0, z0 + 2.6), 'paint:#2a2d30', 'bench base')
        m.box((2.0, 4.0, z0), (14.0, 4.6, z0 + 2.6), 'paint:#3050a0', 'bench cushion')
        zb = z0 if z0 < 8 else z0 + 2.0
        m.box((2.0, 4.6, zb), (14.0, 9.0, zb + 0.6), 'paint:#3050a0', 'bench back')
    m.box((7.6, 15.0, 7.6), (8.4, 17.6, 8.4), 'lamp:fff2d0', 'cabin light', faces=('down',))
    m.box((2.0, 13.0, 1.5), (6.0, 14.0, 1.6), 'led:', 'cabin number display', faces=('south',))
    m.g('hanger')
    m.box((6.6, 17.6, 6.6), (9.4, 18.6, 9.4), STEEL, 'hanger clamp')
    m.box((7.2, 18.6, 7.0), (8.8, 21.2, 9.0), 'paint:#3a3e44', 'hanger arm')
    m.box((7.4, 21.2, 7.0), (8.6, 22.0, 9.0), 'paint:#3a3e44', 'hanger head')
    m.g('grip')
    m.box((6.0, 22.0, 4.0), (10.0, 23.0, 12.0), 'paint:#4a5560', 'grip body')
    m.box((6.4, 23.0, 7.0), (7.4, 24.0, 9.0), STEEL, 'grip jaw')
    m.box((8.6, 23.0, 7.0), (9.6, 24.0, 9.0), STEEL, 'grip jaw')
    for z in (4.6, 11.4):
        m.cyl((5.6, 22.8, z), 0.8, 0.8, 'x', 'rubber', 'running wheel')
        m.cyl((9.6, 22.8, z), 0.8, 0.8, 'x', 'rubber', 'running wheel')
    m.box((9.6, 22.2, 5.0), (11.0, 22.8, 11.0), 'paint:ral1023', 'grip lever')
    m.cyl((10.4, 22.5, 5.6), 0.5, 0.8, 'x', STEEL, 'lever roller')
    for z in (5.0, 6.0, 10.0, 11.0):
        m.cyl((7.0, 22.0, z), 0.25, 0.8, 'y', 'metal:steel', 'grip spring')
    m.save(item_gui=0.6)
    return m


def cabin_tram():
    """aerial tram cabin (40 riders standing) at 1/2 scale: floor y=0, carriage on the track rope at y=23.2"""
    m = Model('ride_cabin_tram')
    BODY = 'paint:#1e5aa8'
    m.g('cabin')
    m.box((-4.0, 0.0, -14.0), (20.0, 1.2, 30.0), 'paint:#2a2d30', 'floor pan')
    m.box((-3.6, 1.2, -13.6), (19.6, 1.4, 29.6), 'paint:#3a3e44', 'floor')
    m.box((-4.0, 1.2, -14.0), (20.0, 5.0, -13.4), BODY, 'front skirt')
    m.box((-4.0, 1.2, 29.4), (20.0, 5.0, 30.0), BODY, 'back skirt')
    m.box((-4.0, 1.2, -13.4), (-3.4, 5.0, 29.4), BODY, 'side skirt')
    m.box((19.4, 1.2, -13.4), (20.0, 5.0, 29.4), BODY, 'side skirt')
    m.box((-4.0, 16.0, -14.0), (20.0, 18.4, 30.0), BODY, 'roof')
    m.box((-2.0, 18.4, -10.0), (18.0, 19.0, 26.0), 'paint:#164a90', 'roof deck')
    for z in (-14.0, -4.0, 6.0, 16.0, 29.4):
        for x in (-4.0, 19.4):
            m.box((x, 5.0, z), (x + 0.6, 16.0, z + 0.6), 'paint:#20222a', 'window pillar')
    m.box((-3.6, 5.0, -14.0), (19.6, 16.0, -13.6), 'glass', 'front windscreen')
    m.box((-3.6, 5.0, 29.6), (19.6, 16.0, 30.0), 'glass', 'rear windscreen')
    m.box((-4.0, 5.0, -13.4), (-3.6, 16.0, 29.4), 'glass', 'side glazing')
    m.box((19.6, 5.0, -13.4), (20.0, 16.0, 4.0), 'glass', 'side glazing')
    m.box((19.6, 5.0, 4.0), (20.1, 16.0, 12.0), 'glass', 'sliding door')
    m.box((19.6, 5.0, 12.0), (20.0, 16.0, 29.4), 'glass', 'side glazing')
    m.box((20.1, 15.6, 3.0), (20.4, 16.4, 13.0), 'paint:#20222a', 'door track')
    m.g('interior')
    for z in (-6.0, 2.0, 10.0, 18.0, 24.0):
        m.box((7.6, 1.4, z), (8.4, 16.0, z + 0.8), 'metal:alu', 'grab pole')
    m.box((-3.0, 14.0, -12.0), (19.0, 14.4, 28.0), 'metal:alu', 'grab rail')
    m.box((-3.0, 1.4, -13.2), (6.0, 7.0, -10.0), 'paint:#2a2d30', 'conductor console')
    m.box((-2.6, 7.0, -12.8), (5.6, 7.2, -10.4), 'screen:30e060', 'console screen', faces=('up',))
    m.box((2.0, 15.0, -13.5), (14.0, 15.8, -13.4), 'led:', 'destination display', faces=('north',))
    m.g('suspension + carriage')
    for z in (-2.0, 18.0):
        m.box((5.0, 18.4, z), (11.0, 19.4, z + 2.0), STEEL, 'hanger clamp')
        m.box((6.6, 19.4, z + 0.4), (9.4, 21.6, z + 1.6), 'paint:#3a3e44', 'hanger')
    m.box((5.0, 21.6, -6.0), (11.0, 22.6, 22.0), 'paint:#4a5560', 'carriage beam')
    for z in (-5.0, -1.0, 3.0, 13.0, 17.0, 21.0):
        m.cyl((5.0, 23.2, z), 1.0, 1.0, 'x', 'paint:#3c464f', 'track rope wheel')
        m.cyl((10.0, 23.2, z), 1.0, 1.0, 'x', 'paint:#3c464f', 'track rope wheel')
    m.box((7.0, 22.6, 6.0), (9.0, 24.6, 10.0), 'paint:ral1023', 'haul rope clamp')
    m.box((5.0, 22.6, 23.0), (11.0, 23.6, 24.0), 'paint:ral3000', 'track brake')
    m.save(item_gui=0.3)
    return m


def car_funicular():
    """funicular car at 1/4 scale (1 px = 25 cm): rail top at y=0, 10 m long, stepped compartments"""
    m = Model('ride_car_funicular')
    BODY = 'paint:#e8b020'
    m.g('underframe + bogies')
    m.box((3.0, 1.0, -11.0), (13.0, 1.8, 27.0), 'paint:#2a2d30', 'underframe')
    for zc in (-6.0, 22.0):
        m.box((3.6, 0.0, zc - 2.2), (12.4, 1.0, zc + 2.2), 'paint:#3a3e44', 'bogie frame')
        for z in (zc - 1.4, zc + 1.4):
            for x in (3.4, 12.6):
                m.cyl((x - 0.3, 0.4, z), 0.6, 0.6, 'x', STEEL, 'wheel')
        m.box((7.0, 0.0, zc - 1.0), (9.0, 0.8, zc + 1.0), 'paint:ral3000', 'track brake')
    m.box((7.4, 0.2, -11.6), (8.6, 1.2, -10.8), 'paint:ral1023', 'rope socket')
    m.g('stepped body')
    for i in range(5):
        z0 = -11.0 + i * 7.6
        y0 = 1.8 + i * 0.6
        m.box((2.8, y0, z0), (13.2, y0 + 0.6, z0 + 7.6), 'paint:#3a3e44', 'compartment floor')
        m.box((2.8, y0 + 0.6, z0), (3.1, y0 + 9.0, z0 + 7.6), BODY, 'body side')
        m.box((12.9, y0 + 0.6, z0), (13.2, y0 + 9.0, z0 + 7.6), BODY, 'body side')
        m.box((2.7, y0 + 3.4, z0 + 0.4), (2.8, y0 + 7.8, z0 + 7.2), 'glass', 'window')
        m.box((13.2, y0 + 3.4, z0 + 0.4), (13.3, y0 + 7.8, z0 + 4.0), 'glass', 'window')
        m.box((13.2, y0 + 0.8, z0 + 4.2), (13.35, y0 + 8.4, z0 + 7.2), 'paint:#c08010', 'door')
        m.box((13.35, y0 + 4.0, z0 + 4.4), (13.5, y0 + 4.6, z0 + 4.8), 'metal:alu', 'door handle')
        m.box((3.4, y0 + 0.6, z0 + 1.0), (12.6, y0 + 2.2, z0 + 3.0), 'paint:#3050a0', 'bench')
        m.box((3.4, y0 + 2.2, z0 + 2.6), (12.6, y0 + 4.6, z0 + 3.0), 'paint:#3050a0', 'bench back')
        m.box((2.8, y0 + 9.0, z0), (13.2, y0 + 9.6, z0 + 7.6), 'paint:#f0f0ea', 'roof section')
        m.box((2.6, y0 + 3.0, z0), (13.4, y0 + 3.4, z0 + 7.6), 'paint:#c08010', 'waist band')
    m.g('ends')
    m.box((2.8, 1.8, -11.4), (13.2, 10.8, -11.0), BODY, 'lower end')
    m.box((3.4, 5.0, -11.5), (12.6, 10.0, -11.4), 'glass', 'end window')
    for x in (4.0, 12.0):
        m.box((x - 0.6, 3.0, -11.6), (x + 0.6, 4.0, -11.4), 'lamp:fff2d0', 'headlight')
    m.box((2.8, 4.2, 27.0), (13.2, 13.8, 27.4), BODY, 'upper end')
    m.box((3.4, 7.6, 27.4), (12.6, 12.8, 27.5), 'glass', 'upper end window')
    for x in (4.0, 12.0):
        m.box((x - 0.6, 5.0, 27.4), (x + 0.6, 6.0, 27.6), 'lamp:ff2020', 'tail light')
    m.box((5.0, 10.4, -11.6), (11.0, 11.0, -11.5), 'led:', 'destination display', faces=('north',))
    m.box((6.0, 1.9, -11.7), (10.0, 2.7, -11.6), 'label:FUNICULAR', 'car label', faces=('north',))
    m.g('roof gear')
    m.box((6.0, 13.4, 14.0), (10.0, 14.4, 20.0), 'paint:#3a3e44', 'radio + battery box')
    m.cyl((8.0, 14.4, 17.0), 0.2, 1.6, 'y', 'metal:steel', 'radio aerial')
    m.save(item_gui=0.3)
    return m


# ================================================================================================ #10 camera
def tripod():
    m = Model('camera_tripod')
    m.g('tripod')
    for (lx, lz, axis, ang) in ((8.0, 2.0, 'x', -22.5), (2.8, 12.0, 'z', 22.5), (13.2, 12.0, 'z', -22.5)):
        m.box((lx - 0.4, 0.0, lz - 0.4), (lx + 0.4, 18.0, lz + 0.4), 'paint:#20222a', 'tripod leg', rot={'axis': axis, 'angle': ang, 'origin': [lx, 0, lz]})
        m.box((lx - 0.6, 0.0, lz - 0.6), (lx + 0.6, 0.6, lz + 0.6), 'rubber', 'leg foot')
    m.box((7.0, 8.0, 7.0), (9.0, 8.6, 9.0), 'metal:alu', 'spreader hub')
    for ang in (0, 45, 90, 135):
        bar(m, 8.0, 8.0, ang, 9.0, 0.3, 8.0, 8.4, 'metal:alu', 'spreader')
    m.cyl((8.0, 14.0, 8.0), 0.9, 6.0, 'y', 'metal:alu', 'centre column')
    m.cyl((8.0, 17.0, 8.0), 1.8, 2.0, 'y', 'paint:#20222a', 'head base / bowl')
    m.cyl((8.0, 19.0, 8.0), 1.4, 2.6, 'y', 'paint:#2a2d30', 'robotic pan motor')
    m.g('camera control unit')
    m.box((9.4, 9.0, 6.0), (13.4, 14.0, 10.0), 'paint:#2a2d30', 'CCU box')
    m.box((9.6, 13.0, 5.9), (13.2, 13.8, 6.0), 'label:CAM', 'ccu label', faces=('north',))
    m.cyl((11.4, 9.0, 10.0), 0.3, 6.0, 'z', 'cable:202020', 'triax cable')
    m.box((3.0, 9.0, 6.0), (6.4, 12.4, 9.0), 'battery', 'V-mount battery')
    m.terminal_12v(11.4, 4.0, 10.0, 'south')
    m.g('trackside kit')
    for (x, z) in ((8.0, -1.4), (-0.6, 13.8), (16.6, 13.8)):
        m.box((x - 1.2, 0.0, z - 0.8), (x + 1.2, 1.2, z + 0.8), 'paint:#a08858', 'sandbag')
        m.box((x - 0.2, 1.2, z - 0.3), (x + 0.2, 1.5, z + 0.3), 'cable:3a3020', 'sandbag tie')
    m.cyl((4.0, 0.0, 12.0), 2.0, 0.4, 'y', 'paint:ral3000', 'cable drum flange')
    m.cyl((4.0, 0.4, 12.0), 1.4, 2.0, 'y', 'cable:202020', 'cable drum')
    m.cyl((4.0, 2.4, 12.0), 2.0, 0.4, 'y', 'paint:ral3000', 'cable drum flange')
    m.box((1.6, 0.0, 14.6), (6.4, 0.3, 15.4), 'paint:ral1023', 'cable ramp')
    m.g('tally pole')
    m.box((13.6, 9.0, 7.6), (14.2, 31.0, 8.4), 'metal:alu', 'tally pole')
    m.box((13.0, 30.0, 7.0), (14.8, 31.6, 9.0), 'paint:#141416', 'tally lamp housing')
    sf = m.surface('north', face=5.85, title='TV CAMERA')
    digits(m, sf, 'd_no', 11.4, 12.2, 3.6, 0.7, 'ffb020')
    sel(m, sf, 's_no', 10.4, 10.6, 'CAM', ['1', '2', '3', '4', '5', '6', '7', '8'], s=1.0)
    lamp(m, sf, 'pvw', 12.4, 10.8, '30e060', 'PVW', 0.5)
    lamp(m, sf, 'tally_f', 12.4, 9.8, 'ff2020', 'TALLY', 0.5)
    lamp(m, sf, 'l_pwr', 10.4, 9.4, '30e060', 'PWR', 0.4)
    push(m, sf, 'test', 12.4, 9.0, 'f0f0f0', 'TEST', 0.5)
    tp = m.surface('north', face=6.95, title='TALLY')
    lamp(m, tp, 'tally', 13.9, 30.8, 'ff2020', 'ON AIR', 1.2)
    pad(m, 80, 9.6, 13.2, 9.3, 6.0)
    m.save(item_gui=0.5)
    return m


def tripod_head():
    """pan / tilt head + broadcast camera (Java turns it about (8, 22, 8) to follow the train); lens looks north"""
    m = Model('camera_tripod_head')
    m.g('fluid head')
    m.box((6.4, 20.6, 6.4), (9.6, 22.6, 9.6), 'paint:#2a2d30', 'tilt motor')
    m.box((5.6, 22.6, 4.0), (10.4, 23.2, 13.0), 'paint:#20222a', 'camera plate')
    m.box((9.6, 21.0, 12.0), (10.0, 22.2, 17.0), 'metal:alu', 'pan bar')
    m.box((9.5, 21.0, 16.0), (10.1, 22.2, 17.6), 'rubber', 'pan bar grip')
    m.g('camera body')
    m.box((5.4, 23.2, 3.4), (10.6, 28.4, 13.6), 'paint:#3a3d42', 'camera body')
    m.box((5.3, 25.0, 6.0), (5.4, 27.0, 11.0), 'label:HD', 'side badge', faces=('west',))
    m.box((10.6, 24.0, 5.0), (10.8, 27.6, 12.0), 'paint:#2a2d30', 'side panel')
    for i in range(4):
        m.box((10.8, 24.4 + i * 0.8, 6.0), (10.95, 24.8 + i * 0.8, 6.6), 'paint:#d0d0d0', 'side switch')
    m.box((6.0, 28.4, 6.0), (10.0, 29.4, 11.0), 'paint:#20222a', 'top handle')
    m.box((7.6, 29.4, 6.6), (8.4, 30.4, 7.4), 'paint:ral3000', 'tally lamp')
    m.box((6.0, 23.6, 13.6), (10.0, 27.6, 15.4), 'battery', 'camera battery')
    m.g('lens')
    m.cyl((8.0, 25.8, -4.0), 1.9, 7.4, 'z', 'paint:#141416', 'zoom lens')
    for z in (-2.0, 0.6):
        m.cyl((8.0, 25.8, z), 2.1, 0.8, 'z', 'rubber', 'zoom ring')
    m.cyl((8.0, 25.8, -5.4), 2.5, 1.4, 'z', 'paint:#141416', 'lens hood')
    m.cyl((8.0, 25.8, -5.5), 1.6, 0.1, 'z', 'glass', 'front element')
    m.box((9.6, 24.0, -2.0), (11.2, 26.4, 2.4), 'paint:#2a2d30', 'zoom servo')
    m.g('viewfinder + mic')
    m.box((3.4, 27.0, 4.0), (5.4, 29.6, 8.4), 'paint:#20222a', 'viewfinder')
    m.box((3.5, 27.2, 3.9), (5.3, 29.4, 4.0), 'screen:30e060', 'viewfinder screen', faces=('north',))
    m.cyl((9.6, 28.6, -1.0), 0.5, 5.0, 'z', 'paint:#20222a', 'shotgun mic')
    m.cyl((9.6, 28.6, -1.6), 0.7, 1.4, 'z', 'mesh', 'mic windshield')
    m.save(item_gui=0.5)
    return m


def director_desk():
    m = Model('director_desk')
    desk_shell(m, ((-15.0, -9.6, 'SHOTS'), (-4.0, 8.0, 'DIRECTOR'), (17.0, 24.0, 'PREVIEW')))
    m.g('monitors')
    for (g, lab) in (((1.0, 15.0, 15.0, 24.0), 'PROGRAM'), ((18.0, 16.0, 30.0, 23.5), 'PREVIEW')):
        x0, y0, x1, y1 = g
        m.box((x0, y0, 9.3), (x1, y1, 9.4), 'screen:05070a', lab.lower() + ' glass')
        m.box((x0 - 0.5, y0 - 0.5, 9.32), (x1 + 0.5, y0, 9.4), 'paint:#111214', 'bezel')
        m.box((x0 - 0.5, y1, 9.32), (x1 + 0.5, y1 + 0.5, 9.4), 'paint:#111214', 'bezel')
        m.box((x0 - 0.5, y0, 9.32), (x0, y1, 9.4), 'paint:#111214', 'bezel')
        m.box((x1, y0, 9.32), (x1 + 0.5, y1, 9.4), 'paint:#111214', 'bezel')
        m.box(((x0 + x1) / 2 - 3, y1 + 0.6, 9.32), ((x0 + x1) / 2 + 3, y1 + 1.3, 9.38), 'label:' + lab, 'monitor label', faces=('north',))
    m.box((-15.0, 13.0, 9.33), (-1.0, 29.6, 9.4), 'screen:0c1210', 'shot list panel')
    m.box((-3.0, 29.2, 9.3), (15.0, 30.0, 9.38), 'paint:#300808', 'on air sign')
    m.g('vision mixer')
    m.box((-10.0, 12.0, 0.6), (26.0, 12.5, 7.4), 'paint:#24272b', 'mixer panel')
    m.box((24.0, 12.5, 3.6), (25.2, 15.0, 4.8), 'metal:alu', 'T-bar')
    m.box((23.6, 15.0, 3.4), (25.6, 15.6, 5.0), 'paint:#141416', 'T-bar handle')
    m.box((23.0, 12.5, 2.0), (26.0, 12.6, 6.4), 'paint:#141416', 'T-bar slot')

    up = m.surface('up', face=16 - 12.5, title='VISION MIXER')
    srcs = ('1', '2', '3', '4', '5', '6', '7', '8', 'AUTO', 'CHASE', 'SIDE', 'CAB', 'HELI', 'FLYBY')
    for i, lab in enumerate(srcs):
        x = -9.0 + i * 2.25
        lamp(m, up, 'pg%d' % (i + 1), x, 1.4, 'ff2020', '', 0.5)
        push(m, up, ('b%d' % (i + 1)) if i < 8 else ['b_auto', 'b_chase', 'b_side', 'b_cab', 'b_orbit', 'b_fly'][i - 8], x, 2.6, '303438', lab, 1.3)
        lamp(m, up, 'pv%d' % (i + 1), x, 4.0, '30e060', '', 0.5)
    push(m, up, 'cut', 22.0, 3.0, 'd02020', 'CUT', 2.0)
    push(m, up, 'add', -14.4, 2.0, '30a040', 'ADD SHOT', 1.2)
    push(m, up, 'del', -12.2, 2.0, 'c06010', 'DEL', 1.2)
    push(m, up, 'clr', -14.4, 4.0, 'c03030', 'CLEAR', 1.2)
    push(m, up, 'next', -12.2, 4.0, '2050d0', 'NEXT', 1.2)
    sel(m, up, 's_dur', -13.3, 6.4, 'SECONDS', ['3', '5', '8', '12', '20'])
    push(m, up, 'trn_p', -6.0, 6.4, '303438', '< TRAIN', 1.2)
    push(m, up, 'trn_n', -3.6, 6.4, '303438', 'TRAIN >', 1.2)
    sel(m, up, 's_lb', 0.4, 6.4, 'LETTERBOX', ['OFF', '2.39', '2.00', '1.85'])
    sel(m, up, 's_fov', 3.6, 6.4, 'ZOOM', ['30', '45', '60', '75'])
    push(m, up, 'watch', 7.0, 6.4, '2080c0', 'WATCH', 1.3)
    push(m, up, 'test', 9.6, 6.4, 'f0f0f0', 'TEST', 1.0)
    sel(m, up, 'k_air', 27.4, 6.0, 'ON AIR', ['OFF', 'ON'], t='key')
    sel(m, up, 'k_run', 29.6, 6.0, 'RUN LIST', ['OFF', 'RUN'], t='key')
    lamp(m, up, 'l_run', 29.6, 8.2, 'ffb020', 'LIST', 0.5)
    lamp(m, up, 'l_pwr', 26.4, 8.2, '30e060', '12V', 0.5)
    lamp(m, up, 'l_sup', 27.6, 8.2, '30e060', 'MAINS', 0.5)

    br = m.surface('north', face=9.25, title='DIRECTOR BRIDGE')
    m.ctl(br, 'lamp', 'l_air', X(6.0), 29.6, w=8.0, h=0.7, col='ff2020', label='ON AIR')
    digits(m, br, 'd_pgm', 8.0, 25.8, 13.0, 0.8, 'ff6060')
    digits(m, br, 'd_pvw', 24.0, 25.0, 11.0, 0.8, '60ff80')
    digits(m, br, 'd_trn', 24.0, 14.8, 11.0, 0.8, 'ffb020')
    digits(m, br, 'd_tc', 8.0, 13.8, 13.0, 0.8, 'ff6060')
    digits(m, br, 'd_auto', 24.0, 13.8, 11.0, 0.8)
    for i in range(6):
        digits(m, br, 'd_s%d' % (i + 1), -8.0, 27.8 - i * 1.6, 13.0, 0.9)
    pad(m, 200, -15, 31, 12.3, 9.4)
    m.save(item_gui=0.3)
    return m


# ================================================================================================ assets
def spool_icon(d):
    d.ellipse((1, 2, 14, 14), fill=(110, 80, 50, 255), outline=(60, 40, 24, 255))
    d.ellipse((3, 4, 12, 12), fill=(70, 72, 76, 255))
    for r in range(4, 9, 2):
        d.ellipse((8 - r // 2 - 1, 8 - r // 2, 8 + r // 2, 8 + r // 2), outline=(150, 154, 160, 255))
    d.ellipse((6, 6, 9, 9), fill=(40, 30, 20, 255))
    d.line((12, 9, 15, 15), fill=(150, 154, 160, 255))


def icon(name, draw):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    os.makedirs(os.path.join(ASSETS, 'textures/items'), exist_ok=True)
    img.save(os.path.join(ASSETS, 'textures/items', name + '.png'))
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + name}}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


BLOCKS = {'ropeway_station': 'Ropeway Station', 'funicular_station': 'Funicular Station', 'ropeway_machine': 'Ropeway Machine Room',
          'ropeway_desk': 'Ropeway Control Desk', 'ropeway_tension': 'Ropeway Tension Weight', 'ropeway_pylon': 'Ropeway Pylon Section',
          'funicular_track': 'Funicular Track', 'camera_tripod': 'Trackside TV Camera (Tripod)', 'director_desk': 'Director Desk'}
PARTS = ['ride_cabin_gondola', 'ride_cabin_tram', 'ride_car_funicular', 'ropeway_pylon_head', 'ropeway_bullwheel', 'ropeway_station_frame',
         'ropeway_mach_wheel', 'ropeway_mach_rotor', 'ropeway_tension_weight', 'camera_tripod_head']

I, G, R, C = 'minecraft:iron_ingot', 'minecraft:glass_pane', 'minecraft:redstone', 'minecraft:comparator'
RECIPES = {
    'ropeway_station': (['ICI', 'SRS', 'SSS'], {'I': I, 'C': C, 'R': R, 'S': ('minecraft:stone', 0)}, 1),
    'funicular_station': (['ICI', 'TRT', 'SSS'], {'I': I, 'C': C, 'R': R, 'T': 'minecraft:rail', 'S': ('minecraft:stone', 0)}, 1),
    'ropeway_machine': (['IPI', 'BRB', 'III'], {'I': I, 'P': 'minecraft:piston', 'B': 'minecraft:iron_block', 'R': 'minecraft:redstone_block'}, 1),
    'ropeway_desk': (['GGG', 'CRC', 'PPP'], {'G': G, 'C': C, 'R': 'minecraft:repeater', 'P': ('minecraft:planks', 0)}, 1),
    'ropeway_tension': (['IHI', 'I I', 'ISI'], {'I': I, 'H': 'minecraft:tripwire_hook', 'S': ('minecraft:stone', 0)}, 1),
    'ropeway_pylon': (['IBI', 'I I', 'IBI'], {'I': I, 'B': 'minecraft:iron_bars'}, 4),
    'funicular_track': (['ITI', 'WWW', 'ITI'], {'I': I, 'T': 'minecraft:rail', 'W': ('minecraft:wooden_slab', 0)}, 8),
    'rope_spool': (['SSS', 'SIS', 'SSS'], {'S': 'minecraft:string', 'I': I}, 1),
    'camera_tripod': (['GEG', 'ISI', 'I I'], {'G': G, 'E': 'minecraft:ender_eye', 'S': 'minecraft:stick', 'I': I}, 1),
    'director_desk': (['GEG', 'CRC', 'PPP'], {'G': G, 'E': 'minecraft:ender_eye', 'C': C, 'R': 'minecraft:repeater', 'P': ('minecraft:planks', 0)}, 1),
}
ITEM_META = {'rope_spool'}
LANG = {'tile.irextras.%s.name' % k: v for k, v in BLOCKS.items()}
LANG.update({'item.irextras.rope_spool.name': 'Haul Rope Spool', 'entity.irextras.ride_car.name': 'Cabin',
             'key.irextras.photomode': 'Photo mode / cinematic train camera'})

if __name__ == '__main__':
    counts = {}
    for fn in (ropeway_desk, ropeway_machine, mach_wheel, mach_rotor, ropeway_station, funicular_station, station_frame, bullwheel, pylon, pylon_head,
               tension, tension_weight, lambda: track(False), lambda: track(True), cabin_gondola, cabin_tram, car_funicular, tripod, tripod_head, director_desk):
        mm = fn()
        counts[mm.name] = len(mm.boxes)
    for n in BLOCKS:
        if n != 'funicular_track': facing_states(n)
    tv = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        tv['facing=%s,steep=false' % f] = {'model': 'irextras:funicular_track', **({'y': y} if y else {})}
        tv['facing=%s,steep=true' % f] = {'model': 'irextras:funicular_track_steep', **({'y': y} if y else {})}
    json.dump({'variants': tv}, open(os.path.join(ASSETS, 'blockstates', 'funicular_track.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/funicular_track'}, open(os.path.join(ASSETS, 'models/item', 'funicular_track.json'), 'w'), indent=1)
    for n in PARTS:
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item', n + '.json'), 'w'), indent=1)
    icon('rope_spool', spool_icon)
    for n, (pat, key, cnt) in RECIPES.items():
        res = {'item': 'irextras:' + n, 'count': cnt}
        if n in ITEM_META: res['data'] = 0
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': res},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f:
            f.write(('' if have.endswith('\n') else '\n') + '\n# batch 5 #5 ropeways + funiculars, #10 cinematic camera\n' + '\n'.join(add) + '\n')
    print('ride + cine assets written')
    for k, v in counts.items(): print('  %-26s %d boxes' % (k, v))
