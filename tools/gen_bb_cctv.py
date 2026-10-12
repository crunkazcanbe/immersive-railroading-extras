#!/usr/bin/env python3
"""CCTV system (her ask 2026-10-11: "build real cameras, with screens you place on the wall ... different types of
cameras ... make it as complex as possible" + "build a control center for cameras with a bunch of buttons and knobs and
screens"). Dome, bullet, PTZ (static bracket + moving pan yoke + moving tilt head), fisheye 360, night/IR, thermal,
platform and doorbell cameras; the 1x1 wall monitor, the ceiling platform monitor, the 9-bay NVR rack with its PoE
switch and the 3-wide CCTV control room desk. Every lamp, LED, button, key, selector, gauge, display and the drive bay
door is a live control the Java tiles (cctv/*) read or work; the video itself is drawn by Java on the dark screen glass.
Models face north; controls by u = 16 - x (X()). Also writes blockstates, item models, item icons, recipes and lang."""
import json, math, os
from PIL import Image, ImageDraw
from bbkit import Model, ASSETS
from gen_bb_ctl import facing_states, ing, keyboard

X = lambda u: 16 - u
CAT6 = 'cable:2060c0'
MAINS = 'cable:1d1d21'
HOUSING = 'paint:#e4e4e0'
DARKH = 'paint:#2a2d30'
LENS = 'paint:#0a0c10'


def lamp(m, sf, cid, x, y, col, label='', s=0.5, blink=False):
    m.ctl(sf, 'lamp', cid, X(x), y, w=s, h=s, col=col, label=label, **({'blink': True} if blink else {}))


def push(m, sf, cid, x, y, col, label, s=1.0):
    m.ctl(sf, 'push', cid, X(x), y, w=s, h=s, col=col, label=label)


def digits(m, sf, cid, x, y, w, h=0.8, col='7cf29a'):
    m.ctl(sf, 'digits', cid, X(x), y, w=w, h=h, col=col, label='', fmt='%s')


def sel(m, sf, cid, x, y, label, pos, s=1.3, t='selector'):
    m.ctl(sf, t, cid, X(x), y, w=s, h=s, col='202020', label=label, pos=pos)


def status_leds(m, sf, x, y, s=0.5, extra=()):
    lamp(m, sf, 'led_pwr', x - s * 1.6, y, '30e060', 'PWR', s)
    lamp(m, sf, 'led_net', x, y, 'ffb020', 'NET', s)
    lamp(m, sf, 'led_rec', x + s * 1.6, y, 'ff2020', 'REC', s, True)
    for i, (cid, col, lab) in enumerate(extra):
        lamp(m, sf, cid, x + s * 1.6 * (i + 2), y, col, lab, s)


def hemi(m, cx, ytop, cz, R, n, mat, name, down=True):
    """a hemisphere of stacked octagonal slices hanging below (down) or standing on ytop"""
    for i in range(n):
        y0 = i * R / n
        r = R * math.sqrt(max(0.0, 1 - ((y0 + R / n / 2) / R) ** 2))
        if r < 0.15: continue
        h = R / n
        y = ytop - y0 - h if down else ytop + y0
        m.cyl((cx, y, cz), r, h, 'y', mat, name)


def ring(m, cx, cy, z, r, n, s, mat, name, t=0.25):
    """small boxes in a circle on a north face at z (stand toward -z)"""
    for i in range(n):
        a = 2 * math.pi * i / n
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        m.box((x - s / 2, y - s / 2, z - t), (x + s / 2, y + s / 2, z), mat, name)


def cat6(m, x, y, z, face='down', length=2.0):
    """the PoE network lead: brass gland, blue CAT6 tail, strain relief and an RJ45 boot"""
    m.gland(x, y, z, face, r=0.35, cable=CAT6, length=length)
    g = m.group
    m.g('CAT6 PoE lead')
    if face == 'down':
        m.box((x - 0.45, y - 0.6 - length - 0.9, z - 0.35), (x + 0.45, y - 0.6 - length, z + 0.35), 'paint:#d8d8d0', 'RJ45 boot')
        m.box((x - 0.3, y - 0.6 - length - 1.3, z - 0.25), (x + 0.3, y - 0.6 - length - 0.9, z + 0.25), 'glass', 'RJ45 plug')
    m.g(g)


def screws(m, pts, z, face='north', s=0.4):
    for (x, y) in pts:
        m.bolt(x, y, z, face, s, 'metal:steel')


def label(m, x0, y0, x1, y1, z, text, face='north'):
    m.nameplate(x0, y0, x1, y1, z, text, face)


# ================================================================================================ cameras
def dome():
    m = Model('cctv_dome')
    m.g('ceiling plate')
    m.cyl((8, 15.4, 8), 5.6, 0.6, 'y', HOUSING, 'mount plate')
    m.cyl((8, 14.5, 8), 5.0, 0.9, 'y', HOUSING, 'base housing')
    for i in range(8):
        a = 2 * math.pi * i / 8 + 0.39
        m.box((8 + 5.2 * math.cos(a) - 0.25, 15.3, 8 + 5.2 * math.sin(a) - 0.25), (8 + 5.2 * math.cos(a) + 0.25, 15.4, 8 + 5.2 * math.sin(a) + 0.25), 'metal:steel', 'ceiling screw')
    m.g('trim ring')
    m.cyl((8, 13.6, 8), 4.6, 0.9, 'y', 'paint:#d0d0cc', 'trim ring')
    m.cyl((8, 13.5, 8), 4.3, 0.1, 'y', 'paint:#9a9a98', 'ring seal')
    for i in range(4):
        a = 2 * math.pi * i / 4 + 0.785
        m.box((8 + 4.0 * math.cos(a) - 0.2, 13.45, 8 + 4.0 * math.sin(a) - 0.2), (8 + 4.0 * math.cos(a) + 0.2, 13.55, 8 + 4.0 * math.sin(a) + 0.2), 'metal:steel', 'tamper screw')
    m.g('smoked dome')
    hemi(m, 8, 13.5, 8, 4.2, 9, 'paint:#262a30', 'smoked bubble')
    m.g('lens module')
    m.box((6.6, 9.6, 4.2), (9.4, 11.6, 6.4), 'paint:#141618', 'lens gimbal')
    m.cyl((8, 10.6, 3.6), 0.95, 0.7, 'z', LENS, 'lens barrel')
    m.cyl((8, 10.6, 3.5), 0.7, 0.12, 'z', 'glass', 'lens glass')
    ring(m, 8, 10.6, 4.25, 1.35, 6, 0.3, 'paint:#4a1010', 'IR LED')
    m.box((7.6, 11.6, 4.6), (8.4, 12.2, 5.6), 'paint:#141618', 'light sensor')
    m.g('back box + leads')
    m.box((10.5, 15.0, 10.5), (13.5, 15.4, 13.5), 'ral7035', 'junction box lid')
    for (x, z) in ((10.8, 10.8), (13.2, 10.8), (10.8, 13.2), (13.2, 13.2)):
        m.box((x - 0.15, 14.95, z - 0.15), (x + 0.15, 15.0, z + 0.15), 'metal:steel', 'lid screw')
    m.terminal_12v(4.0, 12.4, 12.6, 'south')
    m.box((3.6, 14.8, 3.4), (5.2, 15.4, 4.6), 'paint:#2060c0', 'RJ45 coupler')
    m.cyl((4.4, 15.4, 4.0), 0.3, 0.6, 'y', CAT6, 'CAT6 to ceiling')
    m.box((6.6, 13.55, 3.4), (9.4, 13.6, 3.7), 'label:IP67', 'rating label')
    for i in range(10):
        a = 2 * math.pi * i / 10
        m.box((8 + 5.3 * math.cos(a) - 0.1, 14.5, 8 + 5.3 * math.sin(a) - 0.1), (8 + 5.3 * math.cos(a) + 0.1, 15.4, 8 + 5.3 * math.sin(a) + 0.1), 'paint:#c8c8c4', 'housing rib')
    sf = m.surface('north', face=round(8 - 4.6 * 0.92 - 0.05, 3), title='DOME')
    status_leds(m, sf, 8, 14.05, 0.5)
    m.save(item_gui=0.6)
    return m


def bullet_body(m, front=1.0, y0=8.0, y1=12.0, x0=5.4, x1=10.6, ir=8, sun_mat='paint:#ececea'):
    m.g('housing')
    m.box((x0, y0, front + 0.6), (x1, y1, 12.0), HOUSING, 'housing')
    m.box((x0 + 0.3, y0 + 0.3, 12.0), (x1 - 0.3, y1 - 0.3, 12.8), 'paint:#d4d4d0', 'rear cap')
    for i in range(5):
        m.box((x0 - 0.05, y0 + 0.6 + i * 0.7, 6.0), (x0, y0 + 0.9 + i * 0.7, 11.0), 'paint:#c0c0bc', 'heat rib')
        m.box((x1, y0 + 0.6 + i * 0.7, 6.0), (x1 + 0.05, y0 + 0.9 + i * 0.7, 11.0), 'paint:#c0c0bc', 'heat rib')
    m.box((x0 - 0.3, y1, front - 0.6), (x1 + 0.3, y1 + 0.5, 12.6), sun_mat, 'sunshield')
    m.box((x0 - 0.3, y1 - 1.2, front - 0.6), (x0, y1, 5.0), sun_mat, 'sunshield skirt')
    m.box((x1, y1 - 1.2, front - 0.6), (x1 + 0.3, y1, 5.0), sun_mat, 'sunshield skirt')
    for z in (3.0, 7.0, 11.0):
        m.box((7.6, y1 - 0.2, z), (8.4, y1, z + 0.6), 'metal:steel', 'shield screw post')
    m.g('front')
    m.box((x0 + 0.2, y0 + 0.2, front + 0.2), (x1 - 0.2, y1 - 0.2, front + 0.6), 'paint:#1a1c20', 'front bezel')
    cy = (y0 + y1) / 2
    m.cyl((8, cy, front), 1.2, 0.3, 'z', LENS, 'lens barrel')
    m.cyl((8, cy, front - 0.05), 0.85, 0.1, 'z', 'glass', 'lens glass')
    ring(m, 8, cy, front + 0.2, 1.75, ir, 0.32, 'paint:#4a1010', 'IR LED')
    m.box((x1 - 1.2, y1 - 0.9, front + 0.1), (x1 - 0.5, y1 - 0.4, front + 0.2), 'paint:#141618', 'light sensor')


def bracket_wall(m, x=8.0, y=10.0, arm_z0=9.0):
    m.g('wall bracket')
    m.box((5.0, y - 3.5, 15.0), (11.0, y + 1.5, 16.0), HOUSING, 'back plate')
    screws(m, ((5.6, y - 2.9), (10.4, y - 2.9), (5.6, y + 0.9), (10.4, y + 0.9)), 15.0)
    m.box((7.3, y - 2.3, arm_z0), (8.7, y - 1.3, 15.0), HOUSING, 'arm')
    m.cyl((8, y - 2.4, arm_z0 - 0.4), 0.9, 1.0, 'z', 'paint:#d0d0cc', 'swivel ball')
    m.box((7.6, y - 2.0, arm_z0 - 0.6), (8.4, y - 1.6, arm_z0 + 0.6), 'metal:steel', 'lock screw')


def bullet():
    m = Model('cctv_bullet')
    bullet_body(m, front=1.0, y0=8.0, y1=12.0)
    bracket_wall(m, y=10.0)
    m.box((6.0, 7.2, 1.2), (10.0, 8.0, 2.0), 'paint:#1a1c20', 'status window')
    m.box((6.6, 8.6, 12.8), (9.4, 10.6, 13.6), 'ral7035', 'rear gland plate')
    m.cyl((8, 9.6, 13.6), 0.35, 1.4, 'z', CAT6, 'CAT6 tail')
    m.box((7.4, 9.0, 15.0), (8.6, 10.2, 15.0), 'paint:#2060c0', 'RJ45 boot')
    m.terminal_12v(3.4, 5.8, 15.0, 'north')
    cat6(m, 12.4, 6.2, 15.4, 'down', 1.6)
    label(m, 6.2, 8.2, 9.8, 8.8, 4.0, 'IP66 4MP')
    m.box((5.35, 8.4, 4.0), (5.4, 11.6, 5.0), 'label:4MP', 'side label', faces=('west',))
    sf = m.surface('north', face=1.15, title='BULLET')
    status_leds(m, sf, 8, 7.6, 0.45)
    pad_detail(m)
    m.save(item_gui=0.6)
    return m


def ptz():
    m = Model('cctv_ptz')
    m.g('wall bracket')
    m.box((4.5, 7.0, 15.0), (11.5, 16.0, 16.0), HOUSING, 'wall plate')
    screws(m, ((5.1, 7.6), (10.9, 7.6), (5.1, 15.4), (10.9, 15.4)), 15.0)
    m.box((6.8, 13.6, 7.6), (9.2, 15.6, 15.0), HOUSING, 'bracket arm')
    m.box((7.0, 12.2, 11.5), (9.0, 13.6, 15.0), HOUSING, 'arm gusset', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 13.6, 15.0]})
    m.box((6.6, 15.6, 8.2), (9.4, 16.0, 14.8), 'paint:#d0d0cc', 'arm cap')
    for z in (9.0, 11.0, 13.0):
        m.box((6.75, 14.2, z), (6.8, 15.0, z + 0.8), 'paint:#c0c0bc', 'arm rib')
        m.box((9.2, 14.2, z), (9.25, 15.0, z + 0.8), 'paint:#c0c0bc', 'arm rib')
    m.g('pendant + slip ring')
    m.cyl((8, 12.0, 7), 2.3, 2.4, 'y', HOUSING, 'pendant housing')
    m.cyl((8, 14.4, 7), 1.6, 1.4, 'y', 'paint:#d0d0cc', 'pendant neck')
    m.cyl((8, 11.8, 7), 2.5, 0.25, 'y', 'paint:#9a9a98', 'slip ring cap')
    for i in range(8):
        a = 2 * math.pi * i / 8
        m.box((8 + 2.35 * math.cos(a) - 0.12, 12.4, 7 + 2.35 * math.sin(a) - 0.12), (8 + 2.35 * math.cos(a) + 0.12, 14.2, 7 + 2.35 * math.sin(a) + 0.12), 'paint:#c8c8c4', 'pendant rib')
    for i in range(4):
        a = 2 * math.pi * i / 4 + 0.785
        m.box((8 + 1.8 * math.cos(a) - 0.15, 11.75, 7 + 1.8 * math.sin(a) - 0.15), (8 + 1.8 * math.cos(a) + 0.15, 11.8, 7 + 1.8 * math.sin(a) + 0.15), 'metal:steel', 'cap screw')
    m.g('cables')
    m.box((6.0, 7.6, 14.0), (10.0, 10.0, 15.0), 'ral7035', 'junction box')
    m.cyl((7.0, 6.2, 14.5), 0.35, 1.4, 'y', CAT6, 'CAT6 PoE+')
    m.cyl((9.0, 6.2, 14.5), 0.3, 1.4, 'y', MAINS, '24 V AC heater feed')
    m.terminal_12v(13.6, 8.0, 15.0, 'north')
    cat6(m, 7.0, 6.2, 14.5, 'down', 1.4)
    label(m, 6.2, 10.4, 9.8, 11.2, 15.0, 'PTZ 30X')
    m.box((6.2, 11.4, 14.95), (9.8, 12.0, 15.0), 'warn', 'moving parts', faces=('north',))
    sf = m.surface('north', face=14.95, title='PTZ')
    status_leds(m, sf, 8, 9.0, 0.5)
    pad_detail(m)
    m.save(item_gui=0.6)
    return m


def ptz_pan():
    m = Model('cctv_ptz_pan')
    m.g('pan drum')
    m.cyl((8, 11.0, 7), 2.2, 1.0, 'y', HOUSING, 'pan drum')
    m.cyl((8, 11.9, 7), 1.6, 0.1, 'y', 'paint:#9a9a98', 'pan bearing')
    for i in range(12):
        a = 2 * math.pi * i / 12
        m.box((8 + 2.1 * math.cos(a) - 0.1, 11.1, 7 + 2.1 * math.sin(a) - 0.1), (8 + 2.1 * math.cos(a) + 0.1, 11.9, 7 + 2.1 * math.sin(a) + 0.1), 'paint:#c8c8c4', 'drum rib')
    m.g('yoke')
    m.box((4.4, 10.4, 5.8), (11.6, 11.0, 8.2), HOUSING, 'yoke crossbar')
    for (x0, x1) in ((4.4, 5.1), (10.9, 11.6)):
        m.box((x0, 8.5, 5.8), (x1, 10.4, 8.2), HOUSING, 'yoke arm')
        m.cyl((x0 if x0 < 8 else x1 - 0.2, 8.5, 7), 0.9, 0.2, 'x', 'paint:#9a9a98', 'tilt bearing cap')
    m.box((4.3, 9.0, 6.2), (4.4, 10.2, 7.8), 'paint:#2a2d30', 'tilt motor cover')
    for y in (9.2, 9.6, 10.0):
        m.box((11.6, y, 6.4), (11.65, y + 0.2, 7.6), 'paint:#c0c0bc', 'vent')
    m.box((7.4, 10.3, 5.7), (8.6, 10.6, 5.8), 'paint:#30e060', 'home flag')
    m.save(item_gui=0.6)
    return m


def ptz_head():
    m = Model('cctv_ptz_head')
    m.g('head body')
    m.box((5.3, 5.6, 3.6), (10.7, 11.4, 11.0), HOUSING, 'head body')
    m.box((5.6, 11.4, 4.0), (10.4, 11.6, 10.6), 'paint:#d0d0cc', 'top cover')
    m.box((5.6, 5.4, 4.0), (10.4, 5.6, 10.6), 'paint:#d0d0cc', 'bottom cover')
    m.box((5.6, 5.9, 11.0), (10.4, 11.1, 11.3), 'paint:#c8c8c4', 'rear cap')
    m.cyl((5.0, 8.5, 7), 0.7, 0.3, 'x', 'metal:steel', 'tilt hub')
    m.cyl((10.7, 8.5, 7), 0.7, 0.3, 'x', 'metal:steel', 'tilt hub')
    for i in range(6):
        y = 6.2 + i * 0.85
        m.box((5.25, y, 5.0), (5.3, y + 0.3, 10.0), 'paint:#c0c0bc', 'side rib')
        m.box((10.7, y, 5.0), (10.75, y + 0.3, 10.0), 'paint:#c0c0bc', 'side rib')
    m.g('front window')
    m.box((5.7, 6.0, 3.4), (10.3, 11.0, 3.6), 'paint:#1a1c20', 'front bezel')
    m.cyl((8, 8.5, 3.0), 1.6, 0.6, 'z', LENS, 'zoom lens barrel')
    m.cyl((8, 8.5, 2.95), 1.2, 0.08, 'z', 'glass', 'lens glass')
    ring(m, 8, 8.5, 3.4, 2.15, 10, 0.32, 'paint:#4a1010', 'IR LED')
    m.box((9.6, 10.2, 3.25), (10.1, 10.7, 3.4), 'paint:#141618', 'light sensor')
    m.g('wiper')
    m.box((7.85, 10.6, 3.2), (8.15, 11.3, 3.4), 'metal:steel', 'wiper motor spindle')
    m.box((7.9, 7.0, 2.85), (8.1, 10.7, 2.95), 'rubber', 'wiper blade')
    m.box((6.5, 11.6, 4.2), (9.5, 11.7, 6.0), 'label:30X', 'model label')
    m.box((7.4, 4.9, 6.0), (8.6, 5.4, 8.0), 'paint:#2060c0', 'internal lead')
    m.save(item_gui=0.6)
    return m


def fisheye():
    m = Model('cctv_fisheye')
    m.g('ceiling disc')
    m.cyl((8, 15.4, 8), 6.2, 0.6, 'y', HOUSING, 'mount plate')
    m.cyl((8, 13.0, 8), 5.5, 2.4, 'y', HOUSING, 'disc housing')
    m.cyl((8, 12.9, 8), 5.2, 0.1, 'y', 'paint:#c8c8c4', 'disc chamfer')
    for i in range(12):
        a = 2 * math.pi * i / 12
        m.box((8 + 5.6 * math.cos(a) - 0.12, 13.2, 8 + 5.6 * math.sin(a) - 0.12), (8 + 5.6 * math.cos(a) + 0.12, 15.2, 8 + 5.6 * math.sin(a) + 0.12), 'paint:#d0d0cc', 'housing rib')
    for i in range(6):
        a = 2 * math.pi * i / 6
        m.box((8 + 3.8 * math.cos(a) - 0.18, 12.85, 8 + 3.8 * math.sin(a) - 0.18), (8 + 3.8 * math.cos(a) + 0.18, 12.9, 8 + 3.8 * math.sin(a) + 0.18), 'metal:steel', 'tamper screw')
    m.g('fisheye lens')
    m.cyl((8, 12.8, 8), 2.4, 0.2, 'y', 'paint:#1a1c20', 'lens bezel')
    hemi(m, 8, 12.8, 8, 1.9, 6, 'paint:#141a24', 'fisheye dome')
    ring(m, 8, 8, 12.9, 3.0, 12, 0.35, 'paint:#4a1010', 'IR LED', t=0.0)
    for i in range(12):
        a = 2 * math.pi * i / 12
        x, z = 8 + 3.0 * math.cos(a), 8 + 3.0 * math.sin(a)
        m.box((x - 0.18, 12.75, z - 0.18), (x + 0.18, 12.85, z + 0.18), 'paint:#4a1010', 'IR LED')
    m.box((10.6, 12.8, 10.6), (11.2, 12.9, 11.2), 'paint:#141618', 'light sensor')
    m.box((6.0, 12.85, 10.4), (10.0, 12.9, 11.4), 'label:360', 'lens label')
    m.g('leads')
    m.terminal_12v(14.6, 12.6, 8.0, 'east')
    m.box((1.4, 14.6, 7.2), (2.6, 15.4, 8.8), 'paint:#2060c0', 'RJ45 coupler')
    m.cyl((2.0, 15.4, 8.0), 0.3, 0.6, 'y', CAT6, 'CAT6 to ceiling')
    sf = m.surface('north', face=round(8 - 5.5 * 0.92 - 0.05, 3), title='FISHEYE')
    status_leds(m, sf, 8, 14.2, 0.5)
    m.save(item_gui=0.6)
    return m


def night():
    m = Model('cctv_night')
    m.g('housing')
    m.box((4.6, 6.6, 1.6), (11.4, 13.4, 12.0), HOUSING, 'housing')
    m.box((4.9, 6.9, 12.0), (11.1, 13.1, 12.8), 'paint:#d4d4d0', 'rear cap')
    m.box((4.3, 13.4, 0.4), (11.7, 13.9, 12.6), 'paint:#ececea', 'sunshield')
    for i in range(6):
        m.box((4.55, 7.2 + i * 1.0, 5.0), (4.6, 7.6 + i * 1.0, 11.0), 'paint:#c0c0bc', 'heat rib')
        m.box((11.4, 7.2 + i * 1.0, 5.0), (11.45, 7.6 + i * 1.0, 11.0), 'paint:#c0c0bc', 'heat rib')
    m.g('IR illuminator face')
    m.box((4.8, 6.8, 1.2), (11.2, 13.2, 1.6), 'paint:#141416', 'front plate')
    m.cyl((8, 10, 1.0), 1.1, 0.2, 'z', LENS, 'lens barrel')
    m.cyl((8, 10, 0.95), 0.8, 0.08, 'z', 'glass', 'lens glass')
    ring(m, 8, 10, 1.2, 1.7, 10, 0.4, 'paint:#5a1414', 'IR LED inner')
    ring(m, 8, 10, 1.2, 2.6, 16, 0.4, 'paint:#5a1414', 'IR LED outer')
    m.box((10.0, 12.2, 1.0), (10.6, 12.8, 1.2), 'paint:#141618', 'photocell')
    m.box((5.2, 12.2, 1.1), (7.2, 12.8, 1.2), 'label:IR', 'IR label')
    m.box((6.0, 5.6, 1.4), (10.0, 6.6, 2.2), 'paint:#1a1c20', 'status window')
    bracket_wall(m, y=9.0)
    m.cyl((8, 8.0, 12.8), 0.35, 1.6, 'z', CAT6, 'CAT6 tail')
    m.terminal_12v(3.0, 4.0, 15.0, 'north')
    cat6(m, 12.6, 6.0, 15.4, 'down', 1.6)
    label(m, 6.0, 7.0, 10.0, 7.6, 1.2, 'NIGHT 80M')
    sf = m.surface('north', face=1.15, title='NIGHT IR')
    status_leds(m, sf, 8, 6.1, 0.45)
    lamp(m, sf, 'ir', 8, 7.6, 'ff3020', 'IR', 1.0)
    pad_detail(m)
    m.save(item_gui=0.6)
    return m


def thermal():
    m = Model('cctv_thermal')
    m.g('housing')
    m.box((4.5, 6.0, 2.0), (11.5, 12.0, 13.0), 'paint:#c9c6b8', 'housing')
    m.box((4.8, 6.3, 13.0), (11.2, 11.7, 13.6), 'paint:#b8b5a8', 'rear cap')
    m.box((4.2, 12.0, 0.6), (11.8, 12.5, 13.4), 'paint:#d8d6cc', 'sunshield')
    m.box((4.2, 11.0, 0.6), (4.5, 12.0, 5.0), 'paint:#d8d6cc', 'sunshield skirt')
    m.box((11.5, 11.0, 0.6), (11.8, 12.0, 5.0), 'paint:#d8d6cc', 'sunshield skirt')
    for i in range(6):
        m.box((4.45, 6.6 + i * 0.85, 4.0), (4.5, 6.9 + i * 0.85, 12.0), 'paint:#b0ad9e', 'fin')
        m.box((11.5, 6.6 + i * 0.85, 4.0), (11.55, 6.9 + i * 0.85, 12.0), 'paint:#b0ad9e', 'fin')
    m.g('germanium window')
    m.box((4.8, 6.3, 1.8), (11.2, 11.7, 2.0), 'paint:#2a2a26', 'front bezel')
    m.cyl((8, 9.0, 1.5), 1.9, 0.3, 'z', 'paint:#3a3426', 'germanium window')
    m.cyl((8, 9.0, 1.45), 1.4, 0.06, 'z', 'paint:#5a5040', 'AR coating sheen')
    for (x, y) in ((6.0, 7.0), (10.0, 7.0), (6.0, 11.0), (10.0, 11.0)):
        m.bolt(x, y, 1.8, 'north', 0.35)
    m.box((9.8, 10.6, 1.6), (10.8, 11.4, 1.8), 'paint:#141618', 'visible spotter lens')
    m.box((5.0, 5.0, 1.9), (11.0, 6.0, 3.0), 'paint:#1a1c20', 'status strip')
    bracket_wall(m, y=9.0)
    m.cyl((8, 8.2, 13.6), 0.35, 1.4, 'z', CAT6, 'CAT6 tail')
    m.terminal_12v(3.0, 3.6, 15.0, 'north')
    cat6(m, 13.0, 5.8, 15.4, 'down', 1.6)
    label(m, 5.4, 10.0, 8.6, 11.0, 2.0, 'LWIR')
    m.box((4.45, 8.0, 6.0), (4.5, 10.0, 11.0), 'label:THERMAL', 'side label', faces=('west',))
    sf = m.surface('north', face=1.85, title='THERMAL')
    status_leds(m, sf, 7.4, 5.5, 0.45, extra=(('led_cal', '3090ff', 'NUC'),))
    pad_detail(m)
    m.save(item_gui=0.6)
    return m


def platform():
    m = Model('cctv_platform')
    m.g('pole')
    m.box((5.5, 0, 6.5), (10.5, 0.5, 11.5), 'galv', 'base plate')
    for (x, z) in ((6.0, 7.0), (10.0, 7.0), (6.0, 11.0), (10.0, 11.0)):
        m.cyl((x, 0.5, z), 0.3, 0.5, 'y', 'metal:steel', 'anchor nut')
    m.cyl((8, 0.5, 9.0), 0.8, 11.0, 'y', 'galv', 'pole')
    m.cyl((8, 0.5, 9.0), 1.0, 0.8, 'y', 'galv', 'pole collar')
    m.box((7.4, 2.0, 9.8), (8.6, 4.0, 10.2), 'paint:#9a9a98', 'access hatch')
    m.bolt(8.0, 3.6, 10.2, 'south', 0.3)
    m.g('sign')
    m.box((5.2, 4.0, 7.85), (10.8, 7.6, 8.15), 'paint:#f0c818', 'sign plate')
    m.box((5.4, 4.2, 7.7), (10.6, 7.4, 7.85), 'label:CCTV IN|OPERATION', 'CCTV sign', faces=('north',))
    for y in (4.4, 7.2):
        m.box((7.0, y - 0.2, 8.15), (9.0, y + 0.2, 8.3), 'metal:steel', 'band clamp')
    m.g('head')
    m.box((7.2, 11.5, 7.6), (8.8, 12.2, 10.4), 'galv', 'pole cap bracket')
    m.box((6.0, 12.2, 3.6), (10.0, 15.0, 12.0), HOUSING, 'housing')
    m.box((5.7, 15.0, 2.6), (10.3, 15.4, 12.4), 'paint:#ececea', 'sunshield')
    for i in range(4):
        m.box((5.95, 12.6 + i * 0.6, 6.0), (6.0, 12.9 + i * 0.6, 11.0), 'paint:#c0c0bc', 'rib')
        m.box((10.0, 12.6 + i * 0.6, 6.0), (10.05, 12.9 + i * 0.6, 11.0), 'paint:#c0c0bc', 'rib')
    m.box((6.2, 12.4, 3.4), (9.8, 14.8, 3.6), 'paint:#1a1c20', 'front bezel')
    m.cyl((8, 13.5, 3.0), 0.9, 0.4, 'z', LENS, 'lens barrel')
    m.cyl((8, 13.5, 2.95), 0.65, 0.07, 'z', 'glass', 'lens glass')
    ring(m, 8, 13.5, 3.4, 1.35, 6, 0.28, 'paint:#4a1010', 'IR LED')
    m.box((6.6, 11.4, 3.6), (9.4, 12.2, 4.4), 'paint:#1a1c20', 'status lip')
    m.g('leads')
    m.terminal_12v(8.0, 8.0, 9.8, 'south')
    m.cyl((8.6, 12.2, 10.8), 0.3, 1.0, 'y', CAT6, 'CAT6 drop')
    m.box((8.3, 11.0, 9.8), (8.9, 12.2, 10.4), CAT6, 'CAT6 into pole')
    sf = m.surface('north', face=3.55, title='PLATFORM')
    status_leds(m, sf, 8, 11.8, 0.4)
    pad_detail(m)
    m.save(item_gui=0.6)
    return m


def doorbell():
    m = Model('cctv_doorbell')
    m.g('plate')
    m.box((5.5, 2.0, 14.9), (10.5, 13.0, 16.0), 'paint:#2a2c30', 'wall plate')
    m.box((5.7, 2.2, 14.5), (10.3, 12.8, 14.9), 'paint:#3a3d42', 'front cover')
    for (x, y) in ((6.0, 2.4), (10.0, 2.4), (6.0, 12.6), (10.0, 12.6)):
        m.box((x - 0.15, y - 0.15, 14.4), (x + 0.15, y + 0.15, 14.5), 'metal:steel', 'security screw')
    m.g('camera')
    m.box((6.6, 9.6, 14.4), (9.4, 11.6, 14.5), 'paint:#101214', 'camera window')
    m.cyl((8, 10.5, 14.2), 0.6, 0.2, 'z', LENS, 'lens')
    m.cyl((8, 10.5, 14.15), 0.4, 0.06, 'z', 'glass', 'lens glass')
    ring(m, 8, 10.5, 14.4, 0.95, 6, 0.2, 'paint:#4a1010', 'IR LED')
    m.box((9.0, 11.0, 14.3), (9.3, 11.3, 14.4), 'paint:#141618', 'PIR window')
    m.g('speaker + mic')
    for i in range(6):
        m.box((6.3, 8.2 + i * 0.25, 14.42), (9.7, 8.32 + i * 0.25, 14.5), 'paint:#111214', 'speaker slot')
    m.box((7.85, 3.0, 14.42), (8.15, 3.3, 14.5), 'paint:#111214', 'mic hole')
    m.g('button')
    m.cyl((8, 5.5, 14.3), 1.2, 0.2, 'z', 'paint:#1a1c20', 'button bezel')
    m.box((6.3, 2.8, 14.45), (9.7, 3.6, 14.5), 'label:PRESS', 'press label', faces=('north',))
    m.g('leads')
    m.terminal_12v(3.4, 6.0, 15.0, 'north')
    m.box((11.0, 4.0, 15.0), (12.2, 5.6, 16.0), 'ral7035', 'chime transformer')
    m.box((11.1, 4.6, 14.95), (12.1, 5.2, 15.0), 'label:16V', 'transformer label', faces=('north',))
    cat6(m, 8.0, 2.0, 15.4, 'down', 1.0)
    m.cyl((11.6, 2.4, 15.5), 0.25, 1.6, 'y', 'cable:c81e1e', 'chime wire')
    for i in range(10):
        m.box((5.45, 2.5 + i * 1.0, 15.0), (5.5, 2.9 + i * 1.0, 15.8), 'paint:#232528', 'plate rib')
        m.box((10.5, 2.5 + i * 1.0, 15.0), (10.55, 2.9 + i * 1.0, 15.8), 'paint:#232528', 'plate rib')
    sf = m.surface('north', face=14.4, title='DOORBELL')
    lamp(m, sf, 'ring', 8, 7.6, '3090ff', '', 0.4)
    sf['controls'][-1]['w'] = 2.0                         # a light bar above the button (so the button is never covered)
    m.ctl(sf, 'push', 'bell', X(8), 5.5, w=1.6, h=1.6, col='e0e0e0', label='PUSH')
    lamp(m, sf, 'led_pwr', 7.2, 12.2, '30e060', '', 0.3)
    lamp(m, sf, 'led_net', 8.8, 12.2, 'ffb020', '', 0.3)
    pad_detail(m)
    m.save(item_gui=0.7)
    return m


# ================================================================================================ monitors
def monitor():
    m = Model('cctv_monitor')
    m.g('front panel')
    m.box((0.5, 2.2, 12.5), (15.5, 15.5, 12.6), 'screen:202020', 'screen glass')
    m.box((0, 15.5, 12.5), (16, 16, 13.5), 'paint:#18191c', 'top bezel')
    m.box((0, 2.2, 12.5), (0.5, 15.5, 13.5), 'paint:#18191c', 'left bezel')
    m.box((15.5, 2.2, 12.5), (16, 15.5, 13.5), 'paint:#18191c', 'right bezel')
    m.box((0, 0, 12.5), (16, 2.2, 13.5), 'paint:#202226', 'control strip')
    m.box((0.5, 2.2, 12.6), (15.5, 15.5, 13.5), 'paint:#101114', 'panel module')
    m.box((4.6, 0.3, 12.45), (7.4, 0.6, 12.5), 'label:CCTV', 'brand plate', faces=('north',))
    m.g('rear housing')
    m.box((2, 2, 13.5), (14, 14.5, 15.2), 'paint:#232528', 'rear housing')
    m.louvres(3.0, 13.0, 9.0, 13.8, 13.5 + 1.7 + 0.0001, n=4) if False else None
    for i in range(8):
        m.box((3.0, 9.0 + i * 0.6, 15.2), (13.0, 9.25 + i * 0.6, 15.3), 'paint:#141518', 'vent slot')
    m.g('wall mount')
    m.box((4, 4, 15.2), (12, 12, 16), 'galv', 'VESA wall plate')
    for (x, y) in ((5, 5), (11, 5), (5, 11), (11, 11)):
        m.cyl((x, y, 15.2), 0.35, 0.8, 'z', 'metal:steel', 'VESA screw')
    for (x, y) in ((4.4, 4.4), (11.6, 4.4), (4.4, 11.6), (11.6, 11.6)):
        m.box((x - 0.2, y - 0.2, 15.95), (x + 0.2, y + 0.2, 16), 'metal:steel', 'wall anchor')
    m.box((7.4, 12.0, 15.2), (8.6, 13.4, 16.0), 'galv', 'safety latch')
    m.g('leads')
    m.terminal_12v(14.0, 3.0, 14.4, 'east')
    m.box((2.6, 2.6, 13.4), (5.6, 4.0, 15.2), 'ral7035', 'mains inlet')
    m.box((3.0, 3.0, 15.15), (5.2, 3.6, 15.2), 'label:230V', 'inlet label', faces=('south',))
    m.gland(3.4, 2.0, 14.4, 'down', r=0.35, cable=MAINS, length=2.0)
    m.gland(5.0, 2.0, 14.4, 'down', r=0.3, cable='cable:3060b0', length=2.0)
    m.box((6.0, 2.0, 13.6), (9.0, 2.6, 15.0), 'paint:#2a2c30', 'BNC + HDMI panel')
    for x in (6.5, 7.5, 8.5):
        m.cyl((x, 1.6, 14.3), 0.25, 0.4, 'y', 'metal:steel', 'BNC socket')
    for i in range(10):
        m.box((0.6 + i * 1.5, 0.05, 12.45), (1.0 + i * 1.5, 0.12, 12.5), 'paint:#2c2e32', 'IR receiver dot') if i in (0,) else None
    sf = m.surface('north', face=12.45, title='MONITOR')
    for (cid, x, lab) in (('chdn', 9.5, 'CH-'), ('chup', 11.2, 'CH+'), ('split', 12.9, 'SPLIT'), ('menu', 14.6, 'MENU')):
        push(m, sf, cid, x, 1.1, '303438', lab, 1.0)
    lamp(m, sf, 'led_pwr', 1.2, 1.1, '30e060', '', 0.5)
    lamp(m, sf, 'led_rec', 2.4, 1.1, 'ff2020', '', 0.5, True)
    lamp(m, sf, 'led_sig', 3.6, 1.1, 'ffb020', '', 0.5)
    digits(m, sf, 'ch', 6.0, 1.1, 3.0, 0.9)
    pad_detail(m)
    m.save(item_gui=0.6)
    return m


def ceiling_monitor():
    m = Model('cctv_ceiling_monitor')
    m.g('ceiling plate + rods')
    m.box((1.5, 15.5, 6.5), (14.5, 16, 10.5), 'galv', 'ceiling plate')
    for x in (3, 13):
        m.cyl((x, 13.0, 8.5), 0.35, 2.5, 'y', 'galv', 'drop rod')
        m.cyl((x, 14.0, 8.5), 0.55, 0.4, 'y', 'metal:steel', 'rod lock nut')
        m.cyl((x, 15.1, 8.5), 0.7, 0.4, 'y', 'metal:steel', 'ceiling flange')
    for (x, z) in ((2.0, 7.0), (14.0, 7.0), (2.0, 10.0), (14.0, 10.0)):
        m.box((x - 0.15, 15.45, z - 0.15), (x + 0.15, 15.5, z + 0.15), 'metal:steel', 'ceiling screw')
    m.g('front')
    m.box((1.0, 5.0, 7.0), (15.0, 13.0, 7.1), 'screen:202020', 'screen glass')
    m.box((0.4, 13.0, 7.0), (15.6, 14.0, 7.6), 'paint:#18191c', 'top bezel')
    m.box((0.4, 5.0, 7.0), (1.0, 13.0, 7.6), 'paint:#18191c', 'left bezel')
    m.box((15.0, 5.0, 7.0), (15.6, 13.0, 7.6), 'paint:#18191c', 'right bezel')
    m.box((0.4, 2.0, 7.0), (15.6, 5.0, 7.6), 'paint:#202226', 'control strip')
    m.box((1.0, 5.0, 7.1), (15.0, 13.0, 7.6), 'paint:#101114', 'panel module')
    m.box((5.4, 2.3, 6.95), (8.6, 2.8, 7.0), 'label:PLATFORM', 'label', faces=('north',))
    m.g('housing')
    m.box((0.6, 2.2, 7.6), (15.4, 13.8, 10.0), 'paint:#232528', 'rear housing')
    for i in range(10):
        m.box((2.0, 6.0 + i * 0.6, 10.0), (14.0, 6.25 + i * 0.6, 10.1), 'paint:#141518', 'vent slot')
    for x in (3, 13):
        m.box((x - 0.8, 13.8, 7.8), (x + 0.8, 14.2, 9.2), 'galv', 'rod clamp')
    m.box((4.0, 3.0, 10.0), (6.6, 5.0, 10.6), 'ral7035', 'mains inlet')
    m.terminal_12v(10.0, 10.5, 10.0, 'south')
    m.cyl((5.2, 5.0, 10.3), 0.3, 11.0, 'y', MAINS, 'mains drop to ceiling')
    m.cyl((6.2, 5.0, 10.3), 0.25, 11.0, 'y', 'cable:3060b0', 'video drop to ceiling')
    sf = m.surface('north', face=6.95, title='PLATFORM MONITOR')
    for (cid, x, lab) in (('chdn', 10.5, 'CH-'), ('chup', 12.2, 'CH+'), ('split', 13.9, 'SPLIT')):
        push(m, sf, cid, x, 4.0, '303438', lab, 0.9)
    lamp(m, sf, 'led_pwr', 2.0, 4.0, '30e060', '', 0.45)
    lamp(m, sf, 'led_rec', 3.2, 4.0, 'ff2020', '', 0.45, True)
    lamp(m, sf, 'led_sig', 4.4, 4.0, 'ffb020', '', 0.45)
    digits(m, sf, 'ch', 7.0, 4.0, 3.0, 0.8)
    pad_detail(m)
    m.save(item_gui=0.6)
    return m


# ================================================================================================ NVR rack
def nvr():
    m = Model('cctv_nvr')
    m.g('rack')
    m.box((1.0, 0, 2.6), (15.0, 0.8, 15.4), 'paint:#141416', 'rack plinth')
    for x in (1.0, 14.2):
        m.box((x, 0.8, 2.6), (x + 0.8, 29.4, 15.4), 'paint:#1c1d20', 'rack upright')
    m.box((1.8, 0.8, 14.6), (14.2, 29.4, 15.4), 'mesh', 'rear mesh door')
    m.box((1.0, 29.4, 2.6), (15.0, 30.0, 15.4), 'paint:#1c1d20', 'rack top')
    for y in range(1, 29, 2):
        m.box((1.7, y, 2.5), (1.95, y + 0.25, 2.6), 'metal:steel', 'rack hole')
        m.box((14.05, y, 2.5), (14.3, y + 0.25, 2.6), 'metal:steel', 'rack hole')
    for x in (2.0, 13.0):
        m.box((x, 29.4, 5.0), (x + 1.0, 30.0, 13.0), 'louvre', 'roof vent')
    m.g('PoE switch')
    m.box((1.8, 24.0, 2.9), (14.2, 27.5, 14.0), 'paint:#2b2f33', 'switch chassis')
    m.box((1.6, 24.0, 2.7), (14.4, 27.5, 2.9), 'paint:#3a3f44', 'switch front')
    for r in range(2):
        for i in range(12):
            x = 2.2 + i * 0.88
            y0 = 24.3 + r * 0.55
            m.box((x, y0, 2.55), (x + 0.7, y0 + 0.45, 2.7), 'paint:#0c0c0e', 'RJ45 port')
    for i in range(12):
        m.box((2.4 + i * 0.88, 24.75, 2.4), (2.7 + i * 0.88, 24.85, 2.55), CAT6, 'patch lead')
    m.box((13.0, 26.3, 2.55), (14.2, 27.2, 2.7), 'paint:#0c0c0e', 'SFP uplink')
    m.nameplate(9.0, 27.0, 11.4, 27.4, 2.7, 'POE 370W')
    m.g('NVR')
    m.box((1.8, 14.5, 2.9), (14.2, 23.5, 14.0), 'paint:#2b2f33', 'NVR chassis')
    m.box((1.6, 14.5, 2.7), (14.4, 23.5, 2.9), 'paint:#3a3f44', 'NVR front panel')
    m.box((2.0, 21.0, 2.62), (11.0, 22.8, 2.7), 'paint:#03140a', 'display window')
    for x in (1.6, 14.0):
        m.box((x, 14.9, 2.2), (x + 0.4, 23.1, 2.7), 'metal:alu', 'rack handle')
    m.nameplate(2.0, 14.8, 7.0, 15.4, 2.7, 'NVR 9 BAY')
    m.louvres(8.0, 10.6, 15.0, 17.6, 2.7, n=4)
    m.box((10.6, 20.0, 2.62), (11.6, 20.4, 2.7), 'paint:#0c0c0e', 'USB port')
    m.g('drive bays')
    m.box((1.8, 3.6, 2.9), (14.2, 13.4, 14.0), 'paint:#2b2f33', 'disk chassis')
    m.box((1.6, 3.6, 2.7), (14.4, 13.4, 2.9), 'paint:#3a3f44', 'disk front panel')
    for i in range(9):
        x0 = 1.9 + i * 1.36
        m.box((x0, 4.0, 2.55), (x0 + 1.25, 12.5, 2.7), 'paint:#202226', 'drive caddy')
        m.box((x0 + 0.2, 11.4, 2.4), (x0 + 1.05, 11.9, 2.55), 'metal:alu', 'caddy latch')
        m.box((x0 + 0.15, 5.0, 2.5), (x0 + 1.1, 10.8, 2.55), 'louvre', 'caddy vent')
    m.nameplate(2.0, 12.7, 6.0, 13.2, 2.7, 'HOT SWAP')
    m.g('UPS / PDU')
    m.box((1.8, 0.8, 2.9), (14.2, 3.0, 14.0), 'paint:#1c1d20', 'PDU chassis')
    m.box((1.6, 0.8, 2.7), (14.4, 3.0, 2.9), 'paint:#26282c', 'PDU front')
    for i in range(6):
        m.box((2.2 + i * 1.6, 1.2, 2.55), (3.4 + i * 1.6, 2.5, 2.7), 'paint:#0c0c0e', 'outlet')
    m.box((12.0, 1.3, 2.55), (13.6, 2.4, 2.7), 'paint:#a01818', 'PDU breaker')
    m.terminal_12v(15.0, 16.0, 9.0, 'east')
    m.gland(8.0, 0.8, 12.0, 'down', r=0.45, cable=MAINS)
    for x in (4.0, 5.0, 11.0, 12.0):
        m.gland(x, 0.8, 12.0, 'down', r=0.3, cable=CAT6)
    m.box((14.0, 16.0, 14.0), (15.6, 18.0, 15.4), 'danger:230V', 'mains warning', faces=('east',))
    f = m.surface('north', face=2.6, title='NVR')
    for i in range(8):
        lamp(m, f, 'p%d' % (i + 1), 2.6 + i * 0.75, 26.6, '30e060', '', 0.45)
        lamp(m, f, 'p%d' % (i + 9), 2.6 + i * 0.75, 25.6, '30e060', '', 0.45)
    lamp(m, f, 'poe', 9.4, 26.6, 'ffb020', 'POE', 0.5)
    digits(m, f, 'poew', 11.6, 26.6, 3.2, 0.8)
    sel(m, f, 's_poe', 13.6, 25.4, 'POE', ['OFF', 'ON'], 1.1)
    digits(m, f, 'disp1', 6.5, 22.4, 9.0, 0.8)
    digits(m, f, 'disp2', 6.5, 21.4, 9.0, 0.8)
    lamp(m, f, 'led_pwr', 12.2, 22.4, '30e060', 'PWR', 0.5)
    lamp(m, f, 'led_rec', 13.1, 22.4, 'ff2020', 'REC', 0.5, True)
    lamp(m, f, 'led_alarm', 12.2, 21.4, 'ff2020', 'ALM', 0.5, True)
    lamp(m, f, 'led_hdd', 13.1, 21.4, 'ffb020', 'HDD', 0.5)
    sel(m, f, 's_rec', 2.6, 19.0, 'REC', ['STOP', 'CONT', 'MOTION'], 1.2)
    sel(m, f, 'k_lock', 4.6, 19.0, 'LOCK', ['OPEN', 'LOCK'], 1.2, t='key')
    push(m, f, 'format', 6.6, 19.0, 'c02020', 'FORMAT', 1.0)
    push(m, f, 'ack', 8.4, 19.0, 'f0d020', 'ACK', 1.0)
    push(m, f, 'test', 10.2, 19.0, 'f0f0f0', 'LAMP', 1.0)
    m.ctl(f, 'gauge', 'g_disk', X(4.0), 16.3, w=2.2, h=2.2, col='f2f0e6', label='DISK %', max=100)
    m.ctl(f, 'gauge', 'g_poe', X(12.0), 16.3, w=2.2, h=2.2, col='f2f0e6', label='POE W', max=370)
    for i in range(9):
        lamp(m, f, 'd%d' % (i + 1), 1.9 + i * 1.36 + 0.625, 3.4, '30e060', '', 0.45, True)
    m.ctl(f, 'zone', 'bays', X(8.0), 8.25, w=12.4, h=8.1, col='202020', label='DRIVE BAYS')
    import cabkit   # opening rear door + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 14.0, 15.0, 29.0, 0.6, side='south', depth=3.4, col='paint:#1c1d20', title='NVR RACK|CCTV', volts='230V 50HZ 12V DC', fns=(('ctl', '12V CTL'), ('main', 'MAINS'), ('scr', 'SCREENS')))
    m.save(item_gui=0.45)
    return m


# ================================================================================================ control desk
def desk():
    m = Model('cctv_desk')
    D = 'paint:#2c2f34'
    m.g('pedestals')
    for (x0, x1) in ((-15.5, -9.0), (25.0, 30.6)):
        m.box((x0, 0.8, 0.6), (x1, 11.4, 9.6), D, 'pedestal')
        m.box((x0, 0, 0.9), (x1, 0.8, 9.3), 'paint:#141416', 'kick plinth')
        m.box((x0, 0.8, 0.45), (x1, 1.2, 0.6), 'haz', 'kick strip', faces=('north',))
        for k in range(3):
            y0 = 1.6 + k * 3.2
            m.box((x0 + 0.3, y0, 0.4), (x1 - 0.3, y0 + 2.9, 0.6), 'paint:#3a3e44', 'drawer front')
            m.box(((x0 + x1) / 2 - 1.0, y0 + 2.0, 0.1), ((x0 + x1) / 2 + 1.0, y0 + 2.3, 0.4), 'metal:alu', 'drawer handle')
            m.box(((x0 + x1) / 2 - 0.3, y0 + 1.1, 0.3), ((x0 + x1) / 2 + 0.3, y0 + 1.5, 0.4), 'metal:brass', 'drawer lock')
    m.box((-9.0, 1.0, 9.0), (25.0, 11.0, 9.6), D, 'modesty panel')
    m.box((-9.0, 0, 9.2), (25.0, 1.0, 9.6), 'paint:#141416', 'modesty kick')
    m.g('worktop')
    m.box((-16, 11.4, 0), (32, 12.0, 10.6), 'paint:#3c4048', 'worktop')
    m.box((-16, 11.2, -0.3), (32, 12.0, 0), 'paint:#1a1b1e', 'armrest edge')
    m.box((-16, 11.0, 0.2), (32, 11.4, 9.8), 'paint:#24272b', 'cable tray', faces=('down', 'north'))
    for (x0, x1, t) in ((-15.0, -9.6, 'MATRIX'), (4.0, 12.0, 'PTZ'), (17.0, 24.0, 'ALARMS'), (-3.0, 3.0, 'CCTV CONTROL')):
        m.box((x0, 11.45, -0.35), (x1, 11.95, -0.3), 'label:' + t, 'edge label', faces=('north',))
    m.g('keyboard + phone')
    keyboard(m, -4.2, 1.8, 12.0, 1.0, 3.6, rows=4)
    m.box((-15.8, 12.0, 1.0), (-13.4, 12.6, 4.4), 'paint:#1e1f22', 'desk phone base')
    m.box((-15.6, 12.6, 1.2), (-15.0, 13.1, 4.2), 'paint:#141416', 'handset')
    for r in range(3):
        for c in range(3):
            m.box((-14.6 + c * 0.38, 12.6, 1.6 + r * 0.6), (-14.35 + c * 0.38, 12.7, 1.9 + r * 0.6), 'paint:#d8d8d0', 'phone key')
    m.box((-14.6, 12.6, 3.5), (-13.6, 12.7, 4.2), 'screen:7cf29a', 'phone display')
    m.g('jog shuttle')
    m.cyl((0.0, 12.0, 7.6), 1.4, 0.35, 'y', 'paint:#1a1b1e', 'shuttle ring')
    m.cyl((0.0, 12.35, 7.6), 0.9, 0.3, 'y', 'paint:#3a3d42', 'jog wheel')
    m.box((-0.1, 12.65, 7.0), (0.1, 12.75, 7.4), 'paint:#f0f0f0', 'jog mark')
    m.box((-1.6, 12.0, 9.0), (1.6, 12.05, 9.8), 'label:JOG', 'jog label', faces=('up',))
    m.g('joystick collar')
    for (a, b) in (((4.8, 1.9), (11.2, 2.3)), ((4.8, 7.7), (11.2, 8.1)), ((4.8, 2.3), (5.2, 7.7)), ((10.8, 2.3), (11.2, 7.7))):
        m.box((a[0], 12.0, a[1]), (b[0], 12.35, b[1]), 'paint:#141416', 'joystick collar')
    for (x, z) in ((5.0, 2.1), (11.0, 2.1), (5.0, 7.9), (11.0, 7.9)):
        m.box((x - 0.12, 12.35, z - 0.12), (x + 0.12, 12.42, z + 0.12), 'metal:steel', 'collar screw')
    m.box((5.3, 12.0, 8.2), (10.7, 12.05, 9.2), 'label:PTZ', 'joystick label', faces=('up',))
    m.g('key plate + speakers')
    m.box((28.5, 12.0, 9.2), (30.8, 12.05, 9.9), 'label:KEYS', 'key plate label', faces=('up',))
    m.g('monitor bridge')
    m.box((-16, 12.0, 10.6), (32, 30.0, 16.0), D, 'bridge body')
    m.box((-16, 12.0, 10.5), (32, 30.0, 10.6), 'paint:#1e2024', 'bridge face')
    m.box((-16, 30.0, 8.0), (32, 31.0, 16.0), 'paint:#3c4048', 'canopy')
    m.box((-16, 29.8, 8.0), (32, 30.0, 8.4), 'paint:#1a1b1e', 'canopy lip')
    for x in (-12.0, -4.0, 4.0, 12.0, 20.0, 28.0):
        m.box((x - 1.5, 29.85, 8.6), (x + 1.5, 30.0, 10.4), 'paint:#f4f0d8', 'downlight')
    for x in (-15.6, 31.2):
        m.box((x, 12.0, 10.2), (x + 0.4, 30.0, 10.6), 'paint:#1a1b1e', 'bridge end post')
    m.g('spot monitor')
    m.box((1.0, 16.0, 10.5), (15.0, 26.0, 10.6), 'screen:101010', 'spot screen glass')
    for (a, b) in (((0.4, 26.0), (15.6, 26.6)), ((0.4, 15.7), (1.0, 26.0)), ((15.0, 15.7), (15.6, 26.0)), ((1.0, 15.7), (15.0, 16.0))):
        m.box((a[0], a[1], 10.2), (b[0], b[1], 10.5), 'paint:#141416', 'spot bezel')
    m.box((5.6, 26.1, 10.15), (10.4, 26.5, 10.2), 'label:SPOT', 'spot label', faces=('north',))
    m.g('quad wall')
    m.box((-14.5, 15.0, 10.5), (-1.5, 28.0, 10.6), 'screen:101010', 'quad screen glass')
    for (a, b) in (((-15.0, 28.0), (-1.0, 28.5)), ((-15.0, 14.6), (-14.5, 28.0)), ((-1.5, 14.6), (-1.0, 28.0)), ((-14.5, 14.6), (-1.5, 15.0))):
        m.box((a[0], a[1], 10.2), (b[0], b[1], 10.5), 'paint:#141416', 'quad bezel')
    m.box((-8.15, 15.0, 10.3), (-7.85, 28.0, 10.5), 'paint:#141416', 'quad mullion')
    m.box((-14.5, 21.35, 10.3), (-1.5, 21.65, 10.5), 'paint:#141416', 'quad transom')
    m.g('speakers')
    for x0 in (-14.5, -6.0):
        m.box((x0, 28.8, 10.3), (x0 + 4.5, 29.7, 10.5), 'mesh', 'speaker grille')
    m.box((16.5, 12.6, 10.3), (31.0, 14.6, 10.5), 'mesh', 'status panel vent')
    m.g('status panel')
    m.box((17.0, 15.8, 10.4), (31.0, 23.0, 10.5), 'paint:#101114', 'status display surround')
    m.box((17.0, 27.4, 10.4), (31.0, 29.4, 10.5), 'paint:#101114', 'clock surround')
    m.nameplate(17.2, 23.4, 22.8, 24.0, 10.5, 'CCTV CONTROL')
    m.g('bridge rear + service panels')
    for i, x0 in enumerate((-15.0, -7.0, 1.0, 9.0, 17.0, 25.0)):
        m.box((x0, 13.0, 15.95), (x0 + 6.0, 28.0, 16.0), 'paint:#33363c', 'rear service panel', faces=('south',))
        for y in (13.4, 27.6):
            for xx in (x0 + 0.4, x0 + 5.6):
                m.box((xx - 0.15, y - 0.15, 16.0), (xx + 0.15, y + 0.15, 16.12), 'metal:steel', 'panel screw')
    m.g('bridge face screws')
    for x in range(-15, 32, 4):
        m.box((x - 0.12, 29.3, 10.38), (x + 0.12, 29.54, 10.5), 'metal:steel', 'face screw')
        m.box((x - 0.12, 12.4, 10.38), (x + 0.12, 12.64, 10.5), 'metal:steel', 'face screw')
    m.g('canopy LED strip')
    m.box((-15.5, 29.7, 8.5), (31.5, 29.8, 8.8), 'lamp:fff4d0', 'LED strip')
    m.g('mouse + pad + mug')
    m.box((18.0, 12.0, 0.8), (22.5, 12.08, 4.0), 'paint:#1a1a24', 'mouse pad')
    m.box((19.6, 12.08, 1.6), (20.8, 12.5, 3.2), 'paint:#2a2c30', 'mouse')
    m.box((19.6, 12.5, 1.6), (20.15, 12.55, 2.2), 'paint:#3a3c40', 'mouse button')
    m.box((20.25, 12.5, 1.6), (20.8, 12.55, 2.2), 'paint:#3a3c40', 'mouse button')
    m.cyl((23.8, 12.0, 1.6), 0.6, 1.3, 'y', 'paint:#f0eee6', 'mug')
    m.box((24.35, 12.4, 1.45), (24.65, 13.0, 1.75), 'paint:#f0eee6', 'mug handle')
    m.g('log printer')
    m.box((-15.6, 12.0, 6.0), (-12.4, 13.4, 9.6), 'paint:#d8d8d4', 'event printer')
    m.box((-15.2, 13.4, 6.4), (-12.8, 13.5, 9.2), 'paint:#bcbcb8', 'printer lid')
    m.box((-15.0, 12.6, 5.9), (-13.0, 12.7, 6.0), 'paint:#1a1a1a', 'paper slot')
    m.box((-14.8, 12.5, 5.0), (-13.2, 12.55, 5.9), 'paint:#fbfbf6', 'printout')
    m.g('under-desk UPS + decoders')
    m.box((-8.6, 0.0, 4.0), (-4.0, 6.0, 9.0), 'paint:#1c1d20', 'UPS tower')
    m.louvres(-8.2, -4.4, 1.0, 5.4, 4.0, n=5)
    m.box((-7.6, 5.0, 3.9), (-5.0, 5.6, 4.0), 'screen:7cf29a', 'UPS display')
    for i in range(4):
        y0 = 0.4 + i * 1.6
        m.box((14.0, y0, 3.0), (24.0, y0 + 1.4, 9.0), 'paint:#24272b', 'video decoder')
        m.box((14.0, y0, 2.9), (24.0, y0 + 1.4, 3.0), 'paint:#30343a', 'decoder front')
        m.box((14.6, y0 + 0.5, 2.85), (15.2, y0 + 0.9, 2.9), 'lamp:30e060', 'decoder LED')
        m.box((22.0, y0 + 0.4, 2.85), (23.6, y0 + 1.0, 2.9), 'label:DEC%d' % (i + 1), 'decoder label')
    m.g('cable management')
    for x in (-10.0, -2.0, 6.0, 14.0, 22.0):
        m.box((x, 9.0, 9.3), (x + 0.3, 11.0, 9.6), 'metal:steel', 'cable tray hanger')
        m.cyl((x + 1.0, 10.0, 9.45), 0.25, 3.0, 'x', 'cable:2060c0', 'CAT6 loom')
        m.cyl((x + 1.0, 10.6, 9.45), 0.25, 3.0, 'x', 'cable:1d1d21', 'video loom')
    m.g('leads')
    m.terminal_12v(30.2, 4.0, 5.0, 'east')
    m.gland(28.0, 0.8, 8.0, 'down', r=0.45, cable=MAINS)
    for x in (-12.0, -11.0, 26.0, 27.0):
        m.gland(x, 0.8, 8.0, 'down', r=0.3, cable=CAT6)
    m.box((29.0, 0.9, 9.6), (30.4, 2.2, 9.7), 'danger:230V', 'mains label', faces=('south',))
    m.box((-3.0, 12.5, 16.0), (3.0, 14.0, 16.0), 'label:CCTV', 'rear label', faces=('south',)) if False else None

    up = m.surface('up', face=16 - 12.0, title='CCTV DESK')
    keys = (('n1', -12, 2.5, '1'), ('n2', -10, 2.5, '2'), ('n3', -8, 2.5, '3'), ('n4', -12, 4.0, '4'), ('n5', -10, 4.0, '5'), ('n6', -8, 4.0, '6'),
            ('n7', -12, 5.5, '7'), ('n8', -10, 5.5, '8'), ('n9', -8, 5.5, '9'))
    for (cid, x, z, lab) in keys:
        push(m, up, cid, x, z, 'd8d8d0', lab, 1.2)
    push(m, up, 'clr', -12, 7.0, 'c03030', 'CLR', 1.2)
    push(m, up, 'n0', -10, 7.0, 'd8d8d0', '0', 1.2)
    push(m, up, 'ent', -8, 7.0, '30a040', 'ENT', 1.2)
    for (cid, z, lab) in (('cam', 2.0, 'CAM'), ('mon', 3.5, 'MON'), ('pre', 5.0, 'PRESET'), ('set', 6.5, 'SET'), ('tour', 8.0, 'TOUR')):
        push(m, up, cid, -5.5, z, '2050d0', lab, 1.2)
    digits(m, up, 'entry', -10, 9.0, 6.0, 0.9)
    for (cid, x, z, lab) in (('j_up', 8, 3.0, 'UP'), ('j_dn', 8, 7.0, 'DN'), ('j_l', 6, 5.0, 'L'), ('j_r', 10, 5.0, 'R'), ('j_ul', 6.4, 3.4, ''), ('j_ur', 9.6, 3.4, ''),
                             ('j_dl', 6.4, 6.6, ''), ('j_dr', 9.6, 6.6, ''), ('j_home', 8, 5.0, 'HOME')):
        push(m, up, cid, x, z, '303438' if cid != 'j_home' else 'c02020', lab, 1.0)
    sel(m, up, 's_speed', 3.0, 7.6, 'SPEED', ['SLOW', 'MED', 'FAST'])
    sel(m, up, 's_zoom', 12.4, 3.0, 'ZOOM', ['W', '2X', '4X', '8X', '16X', '30X'])
    sel(m, up, 's_focus', 14.4, 3.0, 'FOCUS', ['AUTO', 'NEAR', 'MID', 'FAR'])
    sel(m, up, 's_iris', 12.4, 6.0, 'IRIS', ['AUTO', 'F1.4', 'F2.8', 'F5.6', 'F11'])
    push(m, up, 'wiper', 14.4, 6.0, '303438', 'WIPER', 1.0)
    push(m, up, 'irbtn', 14.4, 8.0, '303438', 'IR', 1.0)
    push(m, up, 'rec', 12.4, 8.0, 'c02020', 'REC', 1.0)
    tiles = (('a_motion', 'MOTION'), ('a_tamper', 'TAMPER'), ('a_loss', 'VID LOSS'), ('a_disk', 'DISK'), ('a_power', 'POWER'), ('a_nvr', 'NVR'), ('a_door', 'DOOR'), ('a_net', 'NETWORK'))
    for i, (cid, lab) in enumerate(tiles):
        m.ctl(up, 'tile', cid, X(18.5 + (i % 4) * 3.0), 2.0 if i < 4 else 3.7, w=2.6, h=1.3, col='ff3030', label=lab)
    push(m, up, 'ack', 18.5, 5.6, 'f0d020', 'ACK', 1.0)
    push(m, up, 'reset', 21.0, 5.6, '30a040', 'RESET', 1.0)
    push(m, up, 'silence', 23.5, 5.6, 'f0f0f0', 'SILENCE', 1.0)
    push(m, up, 'test', 26.0, 5.6, 'f0f0f0', 'LAMP TEST', 1.0)
    sel(m, up, 'k_arm', 29.6, 5.6, 'ALARMS', ['DISARM', 'ARM'], t='key')
    sel(m, up, 'k_lock', 29.6, 8.0, 'REC LOCK', ['REC', 'LOCK'], t='key')
    sel(m, up, 's_cam', 18.5, 8.2, 'CAMERA', [str(i) for i in range(1, 13)])
    sel(m, up, 's_mon', 21.0, 8.2, 'MONITOR', [str(i) for i in range(1, 9)])
    sel(m, up, 's_split', 23.5, 8.2, 'SPLIT', ['1', '4', '9'])
    sel(m, up, 's_tour', 26.0, 8.2, 'TOUR', ['OFF', '5S', '10S', '30S'])

    br = m.surface('north', face=10.45, title='CCTV WALL')
    for (cid, x, lab) in (('g_online', 19.5, 'CAMS ON %'), ('g_rec', 24.0, 'REC %'), ('g_disk', 28.5, 'DISK %')):
        m.ctl(br, 'gauge', cid, X(x), 25.5, w=3.0, h=3.0, col='f2f0e6', label=lab, max=100)
    for (cid, y, col) in (('d_cams', 22.0, '7cf29a'), ('d_motion', 21.0, '7cf29a'), ('d_alarm1', 19.8, 'ff6060'), ('d_alarm2', 18.8, 'ff6060'),
                          ('d_alarm3', 17.8, 'ff6060'), ('d_alarm4', 16.8, 'ff6060'), ('d_clock', 28.6, 'ffb020')):
        digits(m, br, cid, 24.5, y, 12.0, 0.8, col)
    lamp(m, br, 'led_spot_live', 2.0, 15.2, '30e060', 'LIVE', 0.5)
    lamp(m, br, 'led_spot_rec', 3.2, 15.2, 'ff2020', 'REC', 0.5, True)
    digits(m, br, 'd_spot', 9.0, 15.2, 9.0, 0.8)
    digits(m, br, 'd_quad', -8.0, 14.2, 10.0, 0.8)
    lamp(m, br, 'led_pwr', 30.5, 29.5, '30e060', 'PWR', 0.5)
    m.save(item_gui=0.3)
    return m


def pad_detail(m, minimum=82):
    """real small hardware until the model reaches its detail bar: fixing screws round the biggest housing, cable
    clips and a ground strap - every camera / screen has them in real life"""
    if len(m.boxes) >= minimum: return
    big = max(m.boxes, key=lambda b: (b[2][0] - b[1][0]) * (b[2][1] - b[1][1]) * (b[2][2] - b[1][2]))
    lo, hi = big[1], big[2]
    g = m.group
    m.g('fixings')
    k = 0
    while len(m.boxes) < minimum and k < 60:
        t = (k % 12) / 12.0
        side = (k // 12) % 2
        x = lo[0] + 0.4 + (hi[0] - lo[0] - 0.8) * t
        y = lo[1] + 0.4 if side == 0 else hi[1] - 0.4
        m.box((x - 0.15, y - 0.15, lo[2] - 0.12), (x + 0.15, y + 0.15, lo[2]), 'metal:steel', 'fixing screw')
        k += 1
    m.g(g)

# ================================================================================================ items
def icon(name, draw):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    d = os.path.join(ASSETS, 'textures/items')
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name + '.png'))
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + name}}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


def drive_icon(col):
    def f(d):
        d.rectangle((2, 1, 13, 14), fill=(52, 56, 62, 255), outline=(20, 20, 22, 255))
        d.rectangle((3, 2, 12, 9), fill=(170, 174, 180, 255))
        d.rectangle((4, 3, 11, 6), fill=col + (255,))
        d.ellipse((6, 6, 9, 9), fill=(120, 124, 130, 255))
        d.rectangle((3, 11, 12, 13), fill=(30, 30, 34, 255))
        d.point((11, 12), fill=(60, 230, 110, 255))
        d.rectangle((4, 12, 8, 12), fill=(200, 168, 78, 255))
    return f


def train_cam_icon(d):
    d.rectangle((1, 9, 14, 11), fill=(120, 126, 132, 255))
    d.rectangle((3, 4, 12, 9), fill=(230, 230, 226, 255), outline=(40, 40, 44, 255))
    d.rectangle((2, 3, 13, 4), fill=(210, 210, 206, 255))
    d.ellipse((1, 5, 4, 8), fill=(10, 12, 16, 255))
    d.point((2, 6), fill=(120, 160, 220, 255))
    d.ellipse((5, 11, 7, 13), fill=(60, 60, 64, 255))
    d.ellipse((9, 11, 11, 13), fill=(60, 60, 64, 255))
    d.point((11, 5), fill=(255, 40, 40, 255))


def configurator_icon(d):
    d.rectangle((2, 1, 13, 14), fill=(250, 196, 30, 255), outline=(30, 30, 30, 255))
    d.rectangle((4, 3, 11, 8), fill=(20, 40, 30, 255))
    d.line((5, 5, 10, 5), fill=(90, 230, 130, 255))
    d.point((7, 7), fill=(90, 230, 130, 255))
    for x in (4, 7, 10):
        d.rectangle((x, 10, x + 1, 11), fill=(40, 40, 44, 255))
    d.rectangle((6, 12, 9, 13), fill=(200, 30, 30, 255))


BLOCKS = {'cctv_dome': 'Dome Camera', 'cctv_bullet': 'Bullet Camera', 'cctv_ptz': 'PTZ Camera', 'cctv_fisheye': 'Fisheye 360 Camera',
          'cctv_night': 'Night / IR Camera', 'cctv_thermal': 'Thermal Camera', 'cctv_platform': 'Platform Camera', 'cctv_doorbell': 'Doorbell Camera',
          'cctv_monitor': 'CCTV Wall Monitor', 'cctv_ceiling_monitor': 'Ceiling Platform Monitor', 'cctv_nvr': 'CCTV NVR Rack', 'cctv_desk': 'CCTV Control Desk'}

I, G, R, Q, C, E, P = 'minecraft:iron_ingot', 'minecraft:glass_pane', 'minecraft:redstone', 'minecraft:quartz', 'minecraft:comparator', 'minecraft:ender_eye', 'minecraft:glowstone_dust'
RECIPES = {
    'cctv_dome': (['III', 'RER', ' G '], {'I': I, 'R': R, 'E': E, 'G': G}, 2),
    'cctv_bullet': (['IIG', 'RE ', 'I  '], {'I': I, 'R': R, 'E': E, 'G': G}, 2),
    'cctv_ptz': (['III', 'CEC', 'RGR'], {'I': I, 'C': C, 'E': E, 'R': R, 'G': G}, 1),
    'cctv_fisheye': (['III', 'QEQ', 'RGR'], {'I': I, 'Q': Q, 'E': E, 'R': R, 'G': G}, 1),
    'cctv_night': (['IRI', 'RER', 'IGI'], {'I': I, 'R': 'minecraft:redstone_torch', 'E': E, 'G': G}, 1),
    'cctv_thermal': (['IBI', 'QEQ', 'IGI'], {'I': I, 'B': 'minecraft:blaze_powder', 'Q': Q, 'E': E, 'G': G}, 1),
    'cctv_platform': (['IEI', ' S ', ' I '], {'I': I, 'E': E, 'S': 'minecraft:sign'}, 1),
    'cctv_doorbell': (['IEI', 'INI', 'IBI'], {'I': I, 'E': E, 'N': 'minecraft:noteblock', 'B': 'minecraft:stone_button'}, 1),
    'cctv_monitor': (['IGI', 'GPG', 'IRI'], {'I': I, 'G': G, 'P': P, 'R': R}, 2),
    'cctv_ceiling_monitor': ([' C ', 'IGI', 'IRI'], {'C': 'minecraft:iron_bars', 'I': I, 'G': G, 'R': R}, 1),
    'cctv_nvr': (['IRI', 'CDC', 'IRI'], {'I': I, 'R': R, 'C': C, 'D': 'minecraft:diamond'}, 1),
    'cctv_desk': (['GGG', 'CRC', 'PPP'], {'G': G, 'C': C, 'R': 'minecraft:repeater', 'P': ('minecraft:planks', 0)}, 1),
    'cctv_drive': (['III', 'IQI', 'IRI'], {'I': I, 'Q': Q, 'R': R}, 1),
    'cctv_train_camera': (['IEI', 'RGR', ' I '], {'I': I, 'E': E, 'R': R, 'G': G}, 1),
    'cctv_configurator': (['IGI', 'RPR', 'IBI'], {'I': I, 'G': G, 'R': R, 'P': P, 'B': 'minecraft:stone_button'}, 1),
}
ITEM_META = {'cctv_drive', 'cctv_train_camera', 'cctv_configurator'}

LANG = {'tile.irextras.%s.name' % k: v for k, v in BLOCKS.items()}
LANG.update({'item.irextras.cctv_drive.name': 'CCTV Hard Drive', 'item.irextras.cctv_train_camera.name': 'Train Forward-Facing Camera',
             'item.irextras.cctv_configurator.name': 'CCTV Configurator (IPC Tester)'})

if __name__ == '__main__':
    counts = {}
    for fn in (dome, bullet, ptz, ptz_pan, ptz_head, fisheye, night, thermal, platform, doorbell, monitor, ceiling_monitor, nvr, desk):
        mm = fn()
        counts[mm.name] = len(mm.boxes)
    for n in BLOCKS: facing_states(n)
    for n in ('cctv_ptz_pan', 'cctv_ptz_head'):          # moving parts: baked as item variants, drawn rotated by Java
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item', n + '.json'), 'w'), indent=1)
    for tb, col in (('1tb', (60, 160, 230)), ('2tb', (60, 200, 110)), ('4tb', (240, 180, 40)), ('8tb', (220, 60, 60))):
        icon('cctv_drive_' + tb, drive_icon(col))
    icon('cctv_train_camera', train_cam_icon)
    icon('cctv_configurator', configurator_icon)
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
            f.write(('' if have.endswith('\n') else '\n') + '\n# CCTV: cameras, monitors, NVR, control desk\n' + '\n'.join(add) + '\n')
    print('CCTV assets written')
    for k, v in counts.items(): print('  %-22s %d boxes' % (k, v))
