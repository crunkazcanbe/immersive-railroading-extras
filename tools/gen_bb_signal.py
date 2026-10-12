#!/usr/bin/env python3
"""IR Extras signalling, lineside and track-side power gear, modelled in detail with bbkit (her ask 2026-10-09:
"make everything in Blockbench and make it as complex as you physically can and make it wire up ... low voltage
stuff gets the little 12 volt wire, high voltage gets the other wires ... like real life").

What the code renderers already draw is left to them:
  signal_post   - SignalMastRenderer draws the mast, ladder, heads and a 0.4-block footing (or the dwarf base)
  crossing_post - CrossingRenderer draws the 0.44-block footing, mast, crossbuck, flashers, gate and cantilever
  speed_sign    - SpeedSignRenderer writes SPEED LIMIT + the number onto the plate's south face at z = 10
so those models are only the foundation, junction box, conduit and cable routing around the code-drawn part.
`empty` (the scaled variants) is untouched: the TESR redraws these same models scaled.

Facings kept from the old models: equipment cases, the bumper and the defect detector face NORTH (doors at z=0);
lineside signs are read from the SOUTH (plate on the post's +z side, where SpeedSignRenderer paints).
Every powered piece gets m.terminal_12v() low down so 12 V wire on the floor meets it; the substation and the
charging station also take HV grid cable through hv_box() terminations with heavy cables into the ground.
Run from tools/:  python3 gen_bb_signal.py
"""
import json
import math
import os
import random
import sys

import bbkit
import cabkit
from bbkit import Model, MATS, PXU, _text, _hex, _cl


# ------------------------------------------------------------------------------------------------ extra materials
def m_txt(img, x0, y0, w, h, rng, arg):            # 'txt:FG,BG,TEXT' - lettering in any colours
    fg, bg, s = arg.split(',', 2)
    _text(img, x0, y0, w, h, s, _hex(fg), _hex(bg))


def m_perf(img, x0, y0, w, h, rng, arg):           # perforated galvanised U-channel sign post
    for y in range(h):
        for x in range(w):
            hole = (y % 4 == 1) and (x % max(2, w) == w // 2)
            v = 158 + rng.choice((0, 0, 8, -8, 14)) + rng.randint(-3, 3)
            img.putpixel((x0 + x, y0 + y), (24, 24, 26, 255) if hole else (_cl(v), _cl(v + 4), _cl(v + 8), 255))


def m_solar(img, x0, y0, w, h, rng, arg):          # PV module: dark blue cells, silver busbars and frame
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1): c = (176, 182, 190)
            elif x % 5 == 0 or y % 5 == 0: c = (120, 130, 150)
            else:
                n = rng.randint(-6, 6)
                c = (_cl(22 + n), _cl(38 + n), _cl(78 + n))
            img.putpixel((x0 + x, y0 + y), c + (255,))


def m_ballast(img, x0, y0, w, h, rng, arg):        # crushed-stone ballast
    for y in range(h):
        for x in range(w):
            v = rng.choice((96, 110, 124, 138, 150)) + rng.randint(-6, 6)
            img.putpixel((x0 + x, y0 + y), (_cl(v), _cl(v - 3), _cl(v - 8), 255))


def m_tread(img, x0, y0, w, h, rng, arg):          # diamond-pattern steel tread plate
    for y in range(h):
        for x in range(w):
            raised = (x + 2 * y) % 6 == 0 or (x - 2 * y) % 6 == 0
            v = (150 if raised else 112) + rng.randint(-4, 4)
            img.putpixel((x0 + x, y0 + y), (_cl(v), _cl(v + 3), _cl(v + 6), 255))


def m_grp(img, x0, y0, w, h, rng, arg):            # glass-reinforced-plastic case: matt with faint fibre
    c = _hex(arg) if arg else (196, 200, 198)
    for y in range(h):
        for x in range(w):
            n = rng.randint(-5, 5) + (6 if (x * 3 + y * 7) % 11 == 0 else 0)
            e = 12 if (x == 0 or y == 0) and w >= 4 and h >= 4 else -16 if (x == w - 1 or y == h - 1) and w >= 4 and h >= 4 else 0
            img.putpixel((x0 + x, y0 + y), (_cl(c[0] + n + e), _cl(c[1] + n + e), _cl(c[2] + n + e), 255))


MATS.update({'txt': m_txt, 'perf': m_perf, 'solar': m_solar, 'ballast': m_ballast, 'tread': m_tread, 'grp': m_grp})
PXU.update({'txt': 12, 'solar': 4})

BOLT = 'galv'


# ------------------------------------------------------------------------------------------------ helpers
def pad(m, x0, z0, x1, z1, h, name='concrete foundation', ch=0.3):
    """cast concrete pad with a chamfered top edge"""
    m.box((x0, 0, z0), (x1, h - ch, z1), 'concrete', name)
    m.box((x0 + ch, h - ch, z0 + ch), (x1 - ch, h, z1 - ch), 'concrete', name + ' chamfer')


def anchor(m, x, y, z, s=0.7):
    """galvanised holding-down bolt on an up face at height y: washer, hex nut, thread end"""
    m.box((x - s / 2, y, z - s / 2), (x + s / 2, y + 0.12, z + s / 2), 'galv', 'washer')
    m.box((x - s * 0.32, y + 0.12, z - s * 0.32), (x + s * 0.32, y + 0.48, z + s * 0.32), 'galv', 'nut')
    m.box((x - 0.12, y + 0.48, z - 0.12), (x + 0.12, y + 0.72, z + 0.12), 'metal:steel', 'thread end')


def side_louvres(m, face, x, z0, z1, y0, y1, n=4, mat='ral7035'):
    """slanted slats on an east/west face (rain sheds outward-down), with the insect mesh behind"""
    sg = 1 if face == 'east' else -1
    step = (y1 - y0) / n
    m.box((x, y0, z0), (x + sg * 0.05, y1, z1), 'louvre', 'louvre mesh', faces=(face,))
    for i in range(n):
        y = y0 + i * step + step * 0.3
        m.box((x, y, z0), (x + sg * 0.45, y + step * 0.45, z1), mat, 'louvre slat',
              rot={'axis': 'z', 'angle': -22.5 * sg, 'origin': [x, y, z0]})
    m.box((x, y1, z0 - 0.1), (x + sg * 0.5, y1 + 0.15, z1 + 0.1), mat, 'louvre hood')


def t_handle(m, x, y, z):
    """quarter-turn lever lock on a north face at z"""
    m.cyl((x, y, z - 0.3), 0.32, 0.3, 'z', 'paint:#2a2c30', 'lock barrel')
    m.box((x - 0.14, y - 1.1, z - 0.55), (x + 0.14, y + 0.15, z - 0.3), 'metal:steel', 'lever handle')
    m.box((x - 0.18, y - 1.25, z - 0.62), (x + 0.18, y - 0.95, z - 0.3), 'paint:#2a2c30', 'handle grip')


def hasp_lock(m, x, y, z):
    """padlock hasp across a door seam on a north face"""
    m.box((x - 1.0, y - 0.28, z - 0.12), (x + 0.15, y + 0.28, z), 'galv', 'hasp plate')
    m.box((x + 0.05, y - 0.32, z - 0.5), (x + 0.45, y + 0.32, z), 'galv', 'hasp staple')
    m.box((x - 0.05, y - 1.45, z - 0.8), (x + 0.65, y - 0.55, z - 0.4), 'metal:brass', 'padlock body')
    m.box((x + 0.05, y - 0.55, z - 0.68), (x + 0.14, y + 0.05, z - 0.52), 'metal:steel', 'padlock shackle')
    m.box((x + 0.46, y - 0.55, z - 0.68), (x + 0.55, y + 0.05, z - 0.52), 'metal:steel', 'padlock shackle')
    m.box((x + 0.2, y - 1.15, z - 0.82), (x + 0.4, y - 0.95, z - 0.8), 'paint:#202020', 'key way')


def hinge(m, x, y, z, h=1.1):
    """lift-off barrel hinge on a north face edge"""
    m.cyl((x, y, z - 0.2), 0.24, h, 'y', 'metal:steel', 'hinge knuckle')
    m.box((x - 0.05, y + h * 0.45, z - 0.45), (x + 0.05, y + h * 0.55, z - 0.0), 'metal:steel', 'hinge pin collar')


def case(m, x0, x1, z0, z1, y0, y1, col='ral7035', doors=2, vents=True, label=None, label2=None, hinges=2, roof=0.7):
    """trackside equipment case: body, doors on the north face (pressed panels, hinges, lever locks, padlock hasp,
    rain drip), louvred side vents, overhanging roof with lifting eyes"""
    m.g('case body')
    m.box((x0, y0, z0), (x1, y1, z1), col, 'case body')
    m.box((x0 - 0.1, y0, z0 - 0.1), (x1 + 0.1, y0 + 0.35, z1 + 0.1), col, 'bottom flange')
    m.g('doors')
    zf = z0
    mid = (x0 + x1) / 2
    dy0, dy1 = y0 + 0.5, y1 - 0.45
    leaves = [(x0 + 0.3, mid - 0.06, 'L'), (mid + 0.06, x1 - 0.3, 'R')] if doors == 2 else [(x0 + 0.3, x1 - 0.3, 'S')]
    for a, b, side in leaves:
        m.box((a, dy0, zf - 0.22), (b, dy1, zf), col, 'door leaf')
        m.box((a + 0.55, dy0 + 0.7, zf - 0.34), (b - 0.55, dy1 - 0.7, zf - 0.22), col, 'door pressing')
        if dy1 - dy0 > 7:
            ym = (dy0 + dy1) / 2
            m.box((a + 0.55, ym - 0.12, zf - 0.38), (b - 0.55, ym + 0.12, zf - 0.34), col, 'door stiffener')
        hx = a - 0.05 if side in ('L', 'S') else b + 0.05
        for i in range(hinges):
            hy = dy0 + 0.6 + i * (dy1 - dy0 - 1.8) / max(1, hinges - 1)
            hinge(m, hx, hy, zf)
        lx = b - 0.65 if side in ('L', 'S') else a + 0.65
        t_handle(m, lx, (dy0 + dy1) / 2 + 0.6, zf - 0.22)
        m.box((a + 0.3, dy0 + 0.15, zf - 0.27), (b - 0.3, dy0 + 0.3, zf - 0.22), 'rubber', 'door bottom seal')
    if doors == 2:
        m.box((mid - 0.06, dy0, zf - 0.06), (mid + 0.06, dy1, zf), 'rubber', 'door seam gasket')
        m.box((mid + 0.06, dy0 + 0.2, zf - 0.32), (mid + 0.32, dy1 - 0.2, zf - 0.22), col, 'meeting-stile cover strip')
        hasp_lock(m, mid, (dy0 + dy1) / 2 - 0.4, zf - 0.22)
    else:
        hasp_lock(m, x1 - 0.95, (dy0 + dy1) / 2 - 1.6, zf - 0.22)
    m.box((x0 - 0.1, dy1 + 0.05, zf - 0.6), (x1 + 0.1, dy1 + 0.3, zf), col, 'rain drip over doors')
    if label:
        lx0 = leaves[0][0] + 0.8
        m.box((lx0, dy1 - 2.4, zf - 0.4), (lx0 + min(4.2, leaves[0][1] - leaves[0][0] - 1.4), dy1 - 1.0, zf - 0.34),
              'label', 'case label', north='label:' + label)
    if label2:
        a, b = leaves[-1][0], leaves[-1][1]
        w = min(3.2, b - a - 1.6)
        m.box((b - 0.8 - w, dy1 - 3.2, zf - 0.4), (b - 0.8, dy1 - 3.2 + w * 0.85, zf - 0.34), 'warn', 'shock warning sign')
    if vents:
        m.g('vents')
        vh = min(2.6, (y1 - y0) * 0.22)
        for face, x in (('east', x1), ('west', x0)):
            side_louvres(m, face, x, z0 + 1.2, z1 - 1.2, y1 - 0.8 - vh, y1 - 0.8, n=4, mat=col)
            if y1 - y0 > 8:
                side_louvres(m, face, x, z0 + 1.2, z1 - 1.2, y0 + 1.0, y0 + 1.0 + vh * 0.8, n=3, mat=col)
    m.g('roof')
    m.box((x0 - 0.45, y1, z0 - 0.7), (x1 + 0.45, y1 + roof * 0.6, z1 + 0.45), col, 'roof overhang')
    m.box((x0 - 0.2, y1 + roof * 0.6, z0 - 0.45), (x1 + 0.2, y1 + roof, z1 + 0.2), col, 'roof crown')
    m.box((x0 - 0.45, y1 - 0.25, z0 - 0.7), (x1 + 0.45, y1, z0 - 0.55), col, 'drip edge')
    for (x, z) in ((x0 + 0.9, z0 + 0.8), (x1 - 0.9, z0 + 0.8), (x0 + 0.9, z1 - 0.8), (x1 - 0.9, z1 - 0.8)):
        m.box((x - 0.35, y1 + roof, z - 0.1), (x + 0.35, y1 + roof + 0.25, z + 0.1), 'galv', 'lifting eye base')
        m.box((x - 0.3, y1 + roof + 0.25, z - 0.08), (x - 0.12, y1 + roof + 0.75, z + 0.08), 'galv', 'lifting eye')
        m.box((x + 0.12, y1 + roof + 0.25, z - 0.08), (x + 0.3, y1 + roof + 0.75, z + 0.08), 'galv', 'lifting eye')
        m.box((x - 0.3, y1 + roof + 0.75, z - 0.08), (x + 0.3, y1 + roof + 0.9, z + 0.08), 'galv', 'lifting eye')
    return m


def stand(m, x0, x1, z0, z1, y0, y1):
    """galvanised channel stand lifting a case off its plinth: corner legs, side rails, holding-down bolts"""
    m.g('galvanised stand')
    for (x, z) in ((x0, z0), (x1 - 0.7, z0), (x0, z1 - 0.7), (x1 - 0.7, z1 - 0.7)):
        m.box((x, y0, z), (x + 0.7, y1, z + 0.7), 'galv', 'stand leg')
        m.box((x - 0.3, y0, z - 0.3), (x + 1.0, y0 + 0.15, z + 1.0), 'galv', 'leg foot plate')
        m.bolt(x - 0.1, y0 + 0.15, z - 0.1, 'up', 0.35, BOLT)
        m.bolt(x + 0.8, y0 + 0.15, z + 0.8, 'up', 0.35, BOLT)
    m.box((x0, y1 - 0.5, z0), (x1, y1, z0 + 0.6), 'galv', 'stand rail')
    m.box((x0, y1 - 0.5, z1 - 0.6), (x1, y1, z1), 'galv', 'stand rail')
    m.box((x0, y1 - 0.5, z0), (x0 + 0.6, y1, z1), 'galv', 'stand rail')
    m.box((x1 - 0.6, y1 - 0.5, z0), (x1, y1, z1), 'galv', 'stand rail')


def cable_entry(m, pts, y_floor, y_case, z_duct=None):
    """cables rising out of ducts cast in the plinth, through brass glands into the case gland plate"""
    m.g('cable entry')
    for (x, z, col) in pts:
        m.cyl((x, y_floor - 0.3, z), 0.62, 0.5, 'y', 'paint:#202224', 'duct mouth')
        m.cyl((x, y_floor + 0.2, z), 0.3, y_case - 0.55 - y_floor - 0.2, 'y', 'cable:' + col, 'multicore cable')
        m.cyl((x, y_case - 0.55, z), 0.44, 0.55, 'y', 'metal:brass', 'cable gland')


def earth(m, x, y, z, face='north'):
    """green/yellow earth lead from a case stud down to a copper earth rod"""
    m.g('earthing')
    m.box((x - 0.3, y, z - 0.3), (x + 0.3, y + 0.5, z + 0.3), 'metal:copper', 'earth stud')
    m.box((x - 0.12, 0.2, z - 0.12), (x + 0.12, y, z + 0.12), 'paint:#3c9a3c', 'earth lead (G/Y)')
    m.cyl((x, -0.4, z), 0.32, 0.9, 'y', 'metal:copper', 'earth rod head')
    m.box((x - 0.4, 0.3, z - 0.4), (x + 0.4, 0.55, z + 0.4), 'metal:brass', 'rod clamp')


def ub(m, x, y, z0, z1, w=1.4):
    """U-bolt clamp round a post (two legs and a saddle), legs along z"""
    m.box((x - w / 2, y, z0), (x + w / 2, y + 0.35, z1), 'galv', 'clamp saddle')


def sign_post_base(m, x, z, r=0.9, collar=True):
    """concrete collar the post is set in"""
    m.g('foundation')
    if collar:
        m.cyl((x, 0, z), r + 1.2, 0.5, 'y', 'concrete', 'concrete collar')
        m.cyl((x, 0.5, z), r + 0.7, 0.2, 'y', 'concrete', 'collar haunch')


# ================================================================================================ models
def relay_case():
    m = Model('relay_case')
    m.g('foundation')
    pad(m, 2.0, 3.0, 14.0, 12.8, 1.0, 'concrete plinth')
    for (x, z) in ((2.6, 3.6), (13.4, 3.6), (2.6, 12.2), (13.4, 12.2)):
        anchor(m, x, 1.0, z, 0.6)
    stand(m, 2.6, 13.4, 3.6, 12.4, 1.0, 2.0)
    cable_entry(m, [(5.0, 9.5, '1d1d21'), (7.0, 10.5, '2a3f8a'), (9.0, 9.5, '1d1d21'), (11.0, 10.5, '6a6a20')], 1.0, 2.0)
    case(m, 2.5, 13.5, 3.5, 12.5, 2.0, 13.0, 'ral7035', doors=2, label='RELAY CASE|LOC 14', label2=True, hinges=3)
    m.g('case details')
    m.nameplate(9.2, 11.3, 12.6, 12.3, 3.12, 'MP 12.4', 'north')
    m.box((2.2, 7.0, 6.0), (2.5, 8.6, 10.0), 'ral7035', 'side cable box')                  # aux cable box on west side
    m.gland(2.35, 7.0, 7.0, 'down', 0.28, 'cable:1d1d21', length=0.8)
    m.gland(2.35, 7.0, 9.0, 'down', 0.28, 'cable:1d1d21', length=0.8)
    m.box((3.5, 2.2, 12.5), (6.5, 6.2, 12.75), 'ral7035', 'back gland box')
    m.bolts(3.8, 6.2, 5.9, 12.75, 2, 'south', s=0.3, mat=BOLT)
    m.terminal_12v(10.8, 2.1, 12.5, face='south')
    earth(m, 13.0, 2.3, 12.95)
    cabkit.fit(m, 3.0, 13.0, 2.6, 12.4, 3.5, depth=4.0, title='RELAY CASE|LOC 14', volts='110V AC 12V DC', fns=(('ctl', '12V CTL'),))   # opening doors + working insides
    m.save()


def track_circuit():
    """tuned unit (TU) beside the rail: GRP case on a galvanised stand, rail bonds both ways, feed to the TU"""
    m = Model('track_circuit')
    m.g('foundation')
    m.box((1, 0, 1), (15, 0.6, 15), 'ballast', 'ballast bed')
    pad(m, 3.0, 3.8, 13.0, 12.2, 1.4, 'precast base')
    for (x, z) in ((3.6, 4.4), (12.4, 4.4), (3.6, 11.6), (12.4, 11.6)):
        anchor(m, x, 1.4, z, 0.55)
    stand(m, 3.4, 12.6, 4.2, 11.8, 1.4, 2.4)
    m.g('tuned unit case')
    m.box((3.5, 2.4, 4.5), (12.5, 9.6, 11.5), 'grp', 'GRP tuned unit case')
    m.box((3.2, 9.6, 4.2), (12.8, 10.4, 11.8), 'grp', 'hinged lid')
    m.box((3.6, 10.4, 4.6), (12.4, 10.8, 11.4), 'grp', 'lid crown')
    for x in (4.6, 11.4):
        m.box((x - 0.4, 8.4, 4.1), (x + 0.4, 10.0, 4.5), 'metal:steel', 'lid toggle clamp')
        m.box((x - 0.15, 9.0, 3.9), (x + 0.15, 9.8, 4.1), 'metal:steel', 'clamp lever')
        m.cyl((x - 0.6, 9.6, 11.5), 0.25, 1.2, 'x', 'metal:steel', 'lid hinge')
    m.box((5.0, 5.6, 4.3), (11.0, 8.2, 4.5), 'label', 'unit label', north='label:TC 12A|TUNED UNIT')
    m.box((6.6, 3.4, 4.3), (9.4, 5.2, 4.5), 'warn', 'warning sign')
    m.box((3.5, 2.4, 4.4), (12.5, 2.6, 11.6), 'grp:9aa09c', 'case seal')
    m.g('rail bonds')
    for sx, xf, xe in ((-1, 3.5, 0.6), (1, 12.5, 15.4)):
        for k, z in enumerate((6.4, 9.6)):
            gx = xf + sx * 0.6
            m.cyl((min(xf, gx), 4.2, z), 0.5, 0.6, 'x', 'metal:brass', 'bond gland')
            end = xf + sx * 1.6
            m.box((min(gx, end), 3.85, z - 0.35), (max(gx, end), 4.55, z + 0.35), 'cable:1d1d21', 'bond cable')
            m.box((end - 0.35, 0.9, z - 0.35), (end + 0.35, 4.55, z + 0.35), 'cable:1d1d21', 'bond cable drop')
            m.box((min(end, xe), 0.6, z - 0.35), (max(end, xe), 1.3, z + 0.35), 'cable:1d1d21', 'bond cable to rail')
            m.box((xe - 0.5, 0.6, z - 0.55), (xe + 0.5, 1.3, z + 0.55), 'metal:copper', 'rail bond lug')
            m.bolt(xe, 1.3, z, 'up', 0.4, BOLT)
            m.box((min(end, xe) + 0.3, 1.3, z - 0.4), (min(end, xe) + 0.7, 1.4, z + 0.4), 'paint:ral1023', 'cable tie')
    m.g('lid details')
    m.box((7.0, 10.8, 7.0), (9.0, 11.1, 9.0), 'galv', 'lid lifting handle base')
    m.box((7.2, 11.1, 7.8), (8.8, 11.6, 8.2), 'galv', 'lid lifting handle')
    m.terminal_12v(8.0, 2.4, 11.5, face='south')
    earth(m, 4.2, 2.6, 12.2)
    m.save(item_gui=0.6)


def insulated_joint():
    """glued insulated rail joint: rail stub on two timber ties, insulated fishplates, six bolts, nylon endpost"""
    m = Model('insulated_joint')
    m.g('ballast and ties')
    m.box((1, 0, 1), (15, 0.8, 15), 'ballast', 'ballast')
    for x in (2.5, 10.5):
        m.box((x, 0.8, 1.2), (x + 3.0, 2.4, 14.8), 'wood', 'timber tie')
        m.box((x + 0.3, 2.4, 5.2), (x + 2.7, 2.7, 10.8), 'paint:#4a4038', 'tie plate')
        for z in (5.6, 10.4):
            m.box((x + 0.6, 2.7, z - 0.25), (x + 1.0, 3.4, z + 0.25), 'metal:steel', 'track spike')
            m.box((x + 2.0, 2.7, z - 0.25), (x + 2.4, 3.4, z + 0.25), 'metal:steel', 'track spike')
    m.g('rail')
    ys = 2.7
    for z0, z1, lab in ((6.2, 9.8, 'rail foot'),):
        m.box((1.0, ys, z0), (15.0, ys + 0.5, z1), 'paint:#5a4a3c', lab)
    m.box((1.0, ys + 0.5, 7.55), (15.0, ys + 3.6, 8.45), 'paint:#5a4a3c', 'rail web')
    m.box((1.0, ys + 3.6, 7.0), (15.0, ys + 4.8, 9.0), 'metal:steel', 'rail head')
    for (z0, z1, y0, y1) in ((6.15, 9.85, 0, 0.5), (7.5, 8.5, 0.5, 3.6), (6.95, 9.05, 3.6, 4.8)):
        m.box((7.85, ys + y0, z0), (8.15, ys + y1, z1), 'paint:#d03020', 'nylon endpost')
    m.g('insulated fishplates')
    for sz, zf in ((-1, 7.55), (1, 8.45)):
        z0, z1 = (zf - 0.9, zf) if sz < 0 else (zf, zf + 0.9)
        m.box((3.6, ys + 0.7, z0), (12.4, ys + 3.4, z1), 'paint:#3a3c40', 'fishplate')
        lz0, lz1 = (z1 - 0.12, z1 + 0.0) if sz < 0 else (z0, z0 + 0.12)
        m.box((3.5, ys + 0.6, min(lz0, lz1) + (0 if sz < 0 else -0.0)), (12.5, ys + 3.5, max(lz0, lz1)), 'paint:#e07a20', 'insulating liner')
        for x in (4.4, 5.9, 7.2, 8.8, 10.1, 11.6):
            if sz < 0:
                m.cyl((x, ys + 2.05, z0 - 0.6), 0.42, 0.6, 'z', BOLT, 'bolt head')
            else:
                m.cyl((x, ys + 2.05, z1), 0.4, 0.5, 'z', BOLT, 'nut')
                m.cyl((x, ys + 2.05, z1 + 0.5), 0.2, 0.3, 'z', 'metal:steel', 'bolt end')
                m.box((x - 0.5, ys + 1.55, z1), (x + 0.5, ys + 2.55, z1 + 0.1), 'paint:#e07a20', 'insulating washer')
    m.g('marking')
    m.box((6.8, ys + 4.8, 7.0), (7.85, ys + 4.85, 9.0), 'paint:ral1023', 'joint paint mark')
    m.box((8.15, ys + 4.8, 7.0), (9.2, ys + 4.85, 9.0), 'paint:ral1023', 'joint paint mark')
    m.g('marker post')
    m.box((13.2, 0.8, 12.2), (14.4, 9.8, 13.4), 'paint:white', 'IRJ marker post')
    m.box((13.15, 7.4, 12.15), (14.45, 8.6, 13.45), 'paint:ral5010', 'blue band')
    m.box((13.0, 9.8, 12.0), (14.6, 10.4, 13.6), 'paint:ral5010', 'marker cap')
    m.box((12.6, 4.4, 13.45), (15.0, 6.6, 13.55), 'txt:101010,f0f0ea,IJ', 'IJ plate', faces=('south', 'north'))
    m.save(item_gui=0.6)


def signal_post():
    """foundation ring round the code-drawn mast footing, base junction box, conduit into the mast, cable trough"""
    m = Model('signal_post')
    m.g('foundation')
    # TESR footing = x,z 4.8..11.2 (mast) / x 4.48..11.52, z 5.44..10.56 (dwarf), up to y 1.92; keep the hole clear
    for a, b in (((2.6, 2.6), (13.4, 4.6)), ((2.6, 11.4), (13.4, 13.4)), ((2.6, 4.6), (4.3, 11.4)), ((11.7, 4.6), (13.4, 11.4))):
        m.box((a[0], 0, a[1]), (b[0], 0.8, b[1]), 'concrete', 'foundation ring')
    m.box((2.9, 0.8, 2.9), (13.1, 1.0, 4.4), 'concrete', 'ring chamfer')
    m.box((2.9, 0.8, 11.6), (13.1, 1.0, 13.1), 'concrete', 'ring chamfer')
    for (x, z) in ((3.4, 3.4), (12.6, 3.4), (3.4, 12.6), (12.6, 12.6)):
        anchor(m, x, 1.0, z, 0.6)
    m.box((4.3, 0.0, 4.6), (4.45, 0.4, 11.4), 'rubber', 'grout seal')
    m.box((11.55, 0.0, 4.6), (11.7, 0.4, 11.4), 'rubber', 'grout seal')
    m.g('junction box')
    m.box((12.2, 1.0, 6.4), (13.2, 3.0, 7.0), 'galv', 'box bracket leg')
    m.box((12.2, 1.0, 9.4), (13.2, 3.0, 10.0), 'galv', 'box bracket leg')
    m.box((11.9, 3.0, 5.6), (15.6, 7.6, 10.8), 'paint:#2b2e33', 'base junction box')
    m.box((11.7, 7.6, 5.4), (15.8, 8.1, 11.0), 'paint:#2b2e33', 'box lid')
    m.box((15.6, 3.4, 6.0), (15.8, 7.2, 10.4), 'paint:#2b2e33', 'box door')
    m.box((15.8, 3.6, 6.2), (16.0, 7.0, 6.6), 'metal:steel', 'door hinge strap')
    m.box((15.8, 5.0, 9.65), (16.1, 5.8, 10.15), 'metal:brass', 'door latch')
    m.box((15.8, 6.4, 8.0), (15.9, 7.0, 10.0), 'txt:101010,f0f0ea,S14', 'signal number plate', faces=('east',))
    m.bolts(12.2, 15.3, 7.85, 5.4, 4, 'north', s=0.3, mat=BOLT)
    m.g('conduit')
    m.cyl((9.5, 3.6, 8.0), 0.42, 2.4, 'x', BOLT, 'conduit into mast')
    m.cyl((10.6, 3.6, 8.0), 0.55, 0.5, 'x', BOLT, 'conduit coupling')
    m.cyl((11.3, 3.6, 8.0), 0.6, 0.6, 'x', 'metal:brass', 'conduit gland')
    m.cyl((13.0, 0.4, 8.0), 0.5, 2.6, 'y', 'paint:#202224', 'cable duct up into box')
    m.cyl((14.6, 0.4, 8.0), 0.5, 2.6, 'y', 'paint:#202224', 'cable duct up into box')
    m.g('cable trough')
    for z in (11.0, 13.6):
        m.box((12.4, 0, z), (15.6, 0.7, z + 2.4), 'concrete', 'troughing unit')
        m.box((12.5, 0.7, z + 0.1), (15.5, 0.95, z + 2.3), 'concrete', 'trough lid')
        m.box((13.7, 0.95, z + 1.0), (14.3, 1.05, z + 1.4), 'metal:steel', 'lid lifting slot')
    m.terminal_12v(14.3, 2.0, 5.6, face='north')
    earth(m, 2.4, 0.9, 8.0)
    m.save(item_gui=0.6)


def share_controls(src, *names):
    """one model serves several blocks: give each block the same live controls layout"""
    d = os.path.join(bbkit.ASSETS, 'controls')
    lay = json.load(open(os.path.join(d, src + '.json')))
    for n in names:
        lay['title'] = n
        json.dump(lay, open(os.path.join(d, n + '.json'), 'w'), indent=1)


def crossing_post():
    """foundation ring round the code-drawn crossing footing, the base junction (bell/flasher) box, conduit.
    The box door carries the maintainer's TEST button and the bell cut-out key (TileCrossing: test / bell)."""
    m = Model('crossing_post', block='crossing_signal')
    m.g('foundation')
    # TESR footing = x,z 4.48..11.52, y 0..1.92 - keep the middle clear
    for a, b in (((2.4, 2.4), (13.6, 4.3)), ((2.4, 11.7), (13.6, 13.6)), ((2.4, 4.3), (4.3, 11.7)), ((11.7, 4.3), (13.6, 11.7))):
        m.box((a[0], 0, a[1]), (b[0], 0.9, b[1]), 'concrete', 'foundation ring')
    m.box((2.7, 0.9, 2.7), (13.3, 1.1, 4.2), 'concrete', 'ring chamfer')
    m.box((2.7, 0.9, 11.8), (13.3, 1.1, 13.3), 'concrete', 'ring chamfer')
    for (x, z) in ((3.2, 3.2), (12.8, 3.2), (3.2, 12.8), (12.8, 12.8)):
        anchor(m, x, 1.1, z, 0.6)
    m.g('base junction box')
    for x in (5.6, 9.8):
        m.box((x, 1.1, 12.2), (x + 0.6, 2.4, 12.8), 'galv', 'box support leg')
    m.box((4.8, 2.4, 11.9), (11.2, 7.4, 15.4), 'galv', 'junction box')
    m.box((4.6, 7.4, 11.7), (11.4, 7.9, 15.6), 'galv', 'box lid')
    m.box((5.2, 2.8, 15.4), (10.8, 7.0, 15.6), 'galv', 'box door')
    m.box((5.6, 5.4, 15.6), (8.6, 6.6, 15.7), 'txt:101010,f0f0ea,XING 3', 'crossing number plate', faces=('south',))
    for y in (3.4, 6.2):
        m.box((5.0, y, 15.4), (5.4, y + 0.6, 15.8), 'metal:steel', 'door hinge')
    m.box((10.2, 4.3, 15.6), (10.6, 5.3, 15.9), 'metal:brass', 'door latch')
    m.box((10.1, 3.6, 15.75), (10.7, 4.3, 16.0), 'metal:brass', 'padlock')
    # live: door face z 15.6 seen from the south -> surface 'south', u = x, face = 16 - z
    door = m.surface('south', face=0.4, title='CROSSING CONTROL')
    m.ctl(door, 'push', 'test', 6.4, 3.9, w=0.9, h=0.9, col='1a1a1a', label='TEST')
    m.ctl(door, 'key', 'bell', 8.7, 3.9, w=0.9, h=0.9, col='c0a040', label='BELL', pos=['ON', 'CUT'])
    m.bolts(5.0, 11.0, 7.65, 11.7, 4, 'north', s=0.3, mat=BOLT)
    m.g('conduit')
    m.cyl((8.0, 3.8, 9.3), 0.42, 2.6, 'z', BOLT, 'conduit into mast')
    m.cyl((8.0, 3.8, 10.4), 0.56, 0.5, 'z', BOLT, 'conduit coupling')
    m.cyl((8.0, 3.8, 11.3), 0.6, 0.6, 'z', 'metal:brass', 'conduit gland')
    for x in (6.0, 10.0):
        m.cyl((x, 0.4, 13.6), 0.5, 2.0, 'y', 'paint:#202224', 'cable duct up into box')
    # 12 V on the box's south end, low: leads drop to the ground outside the ring
    m.box((11.2, 1.1, 13.0), (13.4, 1.6, 15.2), 'galv', 'terminal bracket')
    m.terminal_12v(12.3, 1.6, 15.2, face='south')
    earth(m, 2.0, 0.9, 8.0)
    m.save(item_gui=0.6)
    share_controls('crossing_signal', 'crossing_gate', 'crossing_cantilever')


# ------------------------------------------------------------------------------------------------ gantry
def gantry_frame():
    """one storey of a lattice tower leg: galvanised angle corners, girts, zig-zag bracing, gussets, step bolts"""
    m = Model('gantry_frame')
    m.g('corner angles')
    for (x, z) in ((2.0, 2.0), (12.5, 2.0), (2.0, 12.5), (12.5, 12.5)):
        fx = x if x < 8 else x + 1.5 - 0.4        # angle legs on the outside faces
        fz = z if z < 8 else z + 1.5 - 0.4
        m.box((x, 0, fz), (x + 1.5, 16, fz + 0.4), 'galv', 'angle leg')
        m.box((fx, 0, z), (fx + 0.4, 16, z + 1.5), 'galv', 'angle leg')
        m.box((x - 0.1, 7.2, z - 0.1), (x + 1.6, 9.8, z + 1.6), 'galv', 'splice plate')
        for y in (7.6, 9.4):
            m.bolt(x + 0.75, y, z - 0.1 if z < 8 else z + 1.6, 'north' if z < 8 else 'south', 0.35, BOLT)
    m.g('girts')
    for y in (0.0, 8.0):
        m.box((3.5, y, 2.0), (12.5, y + 0.9, 2.4), 'galv', 'girt')
        m.box((3.5, y, 13.6), (12.5, y + 0.9, 14.0), 'galv', 'girt')
        m.box((2.0, y, 3.5), (2.4, y + 0.9, 12.5), 'galv', 'girt')
        m.box((13.6, y, 3.5), (14.0, y + 0.9, 12.5), 'galv', 'girt')
    m.g('bracing')
    L = 5.5
    for yc, ang in ((4.45, 45), (11.85, -45)):
        for zf in (2.45, 13.15):
            m.box((8 - L, yc - 0.3, zf), (8 + L, yc + 0.3, zf + 0.4), 'galv', 'diagonal brace',
                  rot={'axis': 'z', 'angle': ang, 'origin': [8, yc, zf + 0.2]})
            m.box((7.2, yc - 0.8, zf - 0.05 if zf < 8 else zf + 0.4), (8.8, yc + 0.8, zf if zf < 8 else zf + 0.45), 'galv', 'gusset')
        for xf in (2.45, 13.15):
            m.box((xf, yc - 0.3, 8 - L), (xf + 0.4, yc + 0.3, 8 + L), 'galv', 'diagonal brace',
                  rot={'axis': 'x', 'angle': -ang, 'origin': [xf + 0.2, yc, 8]})
    m.g('step bolts')
    for i, y in enumerate((1.6, 3.6, 5.6, 9.6, 11.6, 13.6)):
        z = 2.4 if i % 2 == 0 else 3.2
        m.box((0.6, y, z), (2.0, y + 0.3, z + 0.3), 'galv', 'step bolt')
        m.box((0.6, y + 0.3, z - 0.05), (0.9, y + 0.6, z + 0.35), 'galv', 'step bolt end')
    m.save()


def gantry_deck():
    """walkway: open steel grating on bearing bars, edge angles, cross bearers, grating clips"""
    m = Model('gantry_deck')
    m.g('grating')
    m.box((0.2, 15.4, 0.2), (15.8, 16, 15.8), 'mesh', 'grating panel', faces=('up', 'down'))
    for x in range(1, 16, 2):
        m.box((x - 0.12, 14.0, 0.2), (x + 0.12, 15.4, 15.8), 'galv', 'bearing bar')
    m.g('edge angles')
    for z0, z1 in ((0, 0.4), (15.6, 16)):
        m.box((0, 13.2, z0), (16, 16, z1), 'galv', 'edge angle')
    for x0, x1 in ((0, 0.4), (15.6, 16)):
        m.box((x0, 13.2, 0.4), (x1, 16, 15.6), 'galv', 'edge angle')
    m.g('cross bearers')
    for z in (3.5, 12.5):
        m.box((0.4, 12.0, z - 0.6), (15.6, 14.0, z + 0.6), 'galv', 'channel bearer')
        m.box((0.4, 12.0, z - 0.9), (15.6, 12.3, z + 0.9), 'galv', 'channel flange')
    m.g('clips')
    for x in (2.0, 8.0, 14.0):
        for z in (3.5, 12.5):
            m.box((x - 0.5, 16.0, z - 0.3), (x + 0.5, 16.15, z + 0.3), 'galv', 'grating clip')
            m.bolt(x, 16.15, z, 'up', 0.3, BOLT)
    m.save()


def gantry_rail():
    """walkway handrail: stanchion with base plate, top rail, knee rail, toe board (runs along z)"""
    m = Model('gantry_rail')
    m.g('stanchion')
    m.box((6.6, 0, 6.4), (9.4, 0.35, 9.6), 'galv', 'base plate')
    for (x, z) in ((7.0, 6.8), (9.0, 6.8), (7.0, 9.2), (9.0, 9.2)):
        m.bolt(x, 0.35, z, 'up', 0.35, BOLT)
    m.cyl((8.0, 0.35, 8.0), 0.7, 14.4, 'y', 'galv', 'stanchion tube')
    for y in (8.0, 14.4):
        m.cyl((8.0, y - 0.6, 8.0), 0.95, 1.2, 'y', 'galv', 'rail clamp fitting')
    m.g('rails')
    m.cyl((8.0, 14.6, 0), 0.62, 16, 'z', 'galv', 'top rail')
    m.cyl((8.0, 8.2, 0), 0.55, 16, 'z', 'galv', 'knee rail')
    m.box((7.55, 0.35, 0), (8.45, 2.6, 16), 'paint:ral1023', 'toe board')
    m.box((7.5, 0.35, 0), (8.5, 0.55, 16), 'galv', 'toe board foot')
    for z in (0.0, 16.0):
        m.cyl((8.0, 14.6, z - 0.4), 0.72, 0.8, 'z', 'galv', 'rail joint sleeve') if 0 < z < 16 else None
    m.box((7.4, 1.0, 3.0), (8.6, 1.9, 3.6), 'galv', 'toe board bolt strip')
    m.box((7.4, 1.0, 12.4), (8.6, 1.9, 13.0), 'galv', 'toe board bolt strip')
    m.save()


def gantry_antenna():
    """radio mast section: galvanised pole, yagi (toward north), microwave dish, omni dipoles, coax down the pole"""
    m = Model('gantry_antenna')
    m.g('pole')
    m.cyl((8, 0, 8), 0.9, 16, 'y', 'galv', 'mast pole')
    for y in (0.4, 15.0):
        m.cyl((8, y, 8), 1.1, 0.6, 'y', 'galv', 'flange collar')
    m.g('yagi')
    m.box((7.3, 11.6, 8.9), (8.7, 12.6, 9.6), 'galv', 'pole clamp')
    m.box((7.6, 11.8, 2.0), (8.4, 12.4, 9.0), 'paint:#3a3e44', 'yagi boom')
    for i, z in enumerate((2.4, 3.5, 4.6, 5.7, 6.9)):
        half = 2.4 - i * 0.15 if i else 2.0
        m.box((8 - half, 12.0, z - 0.1), (8 + half, 12.2, z + 0.1), 'metal:alu', 'yagi element')
    m.box((5.0, 11.9, 7.9), (11.0, 12.3, 8.3), 'metal:alu', 'driven element')
    m.box((7.4, 11.6, 7.7), (8.6, 12.0, 8.5), 'paint:#202224', 'balun box')
    m.g('dish')
    m.box((8.8, 4.6, 7.2), (9.8, 6.6, 8.8), 'galv', 'dish mount clamp')
    m.cyl((8.0, 5.6, 3.6), 2.6, 0.6, 'z', 'paint:#e4e4de', 'dish reflector', cap='paint:#d8d8d2')
    m.cyl((8.0, 5.6, 4.2), 1.2, 0.8, 'z', 'paint:#c8c8c2', 'dish back hub')
    m.box((7.6, 5.2, 5.0), (8.4, 6.0, 8.1), 'galv', 'dish mount arm')
    m.box((7.85, 5.45, 1.8), (8.15, 5.75, 3.6), 'metal:alu', 'feed strut')
    m.cyl((8.0, 5.6, 1.3), 0.45, 0.6, 'z', 'paint:#2a2c30', 'feed horn')
    m.g('omni dipoles')
    m.box((8.9, 13.6, 7.6), (12.8, 14.0, 8.4), 'galv', 'side arm')
    m.box((12.4, 13.0, 7.7), (12.8, 15.6, 8.3), 'paint:white', 'dipole radome')
    m.box((3.2, 2.6, 7.6), (7.1, 3.0, 8.4), 'galv', 'side arm')
    m.box((3.2, 2.0, 7.7), (3.6, 4.6, 8.3), 'paint:white', 'dipole radome')
    m.g('coax')
    for x, y1 in ((8.95, 12.0), (8.95, 5.6)):
        m.box((x, 0, 8.25), (x + 0.3, y1, 8.55), 'cable:1d1d21', 'coax feeder')
    m.box((6.7, 0, 7.8), (7.0, 16, 8.1), 'cable:1d1d21', 'coax feeder')
    for y in (1.5, 6.5, 10.5, 14.0):
        m.box((6.6, y, 7.7), (9.4, y + 0.2, 8.65), 'paint:#202224', 'cable hanger')
    m.save()


def gantry_leg():
    """one storey of a loadout gantry leg: box-section column, stiffeners, splice, ladder rungs"""
    m = Model('gantry_leg')
    col = 'paint:ral1023'
    m.g('column')
    m.box((5.0, 0, 5.0), (11.0, 16, 5.6), col, 'column flange')
    m.box((5.0, 0, 10.4), (11.0, 16, 11.0), col, 'column flange')
    m.box((5.0, 0, 5.6), (5.6, 16, 10.4), col, 'column web')
    m.box((10.4, 0, 5.6), (11.0, 16, 10.4), col, 'column web')
    m.g('splice')
    for y0 in (0.0, 14.6):
        m.box((4.6, y0, 4.6), (11.4, y0 + 1.4, 11.4), col, 'splice band')
    for face, z in (('north', 4.6), ('south', 11.4)):
        for y in (0.45, 1.0, 15.0, 15.55):
            for x in (5.6, 8.0, 10.4):
                m.bolt(x, y, z, face, 0.3, BOLT)
    m.g('stiffeners')
    for y in (5.0, 10.0):
        m.box((4.7, y, 7.6), (5.0, y + 1.6, 8.4), col, 'stiffener')
        m.box((11.0, y, 7.6), (11.3, y + 1.6, 8.4), col, 'stiffener')
    m.box((4.9, 7.0, 4.9), (11.1, 8.0, 11.1), 'haz', 'hazard band')
    m.g('ladder')
    m.box((6.0, 0, 11.0), (6.4, 16, 12.6), 'galv', 'ladder stile')
    m.box((9.6, 0, 11.0), (9.9, 16, 12.6), 'galv', 'ladder stile')
    for y in (0.9, 4.9, 8.9, 12.9):
        m.cyl((6.4, y, 12.0), 0.2, 3.2, 'x', 'galv', 'rung')
    m.g('services')
    m.box((4.3, 0, 7.8), (4.7, 16, 8.2), 'galv', 'cable tray riser')
    m.box((4.0, 0, 7.5), (4.3, 16, 8.5), 'galv', 'tray lip')
    m.box((4.35, 0, 7.85), (4.6, 16, 8.15), 'cable:1d1d21', 'control cable')
    m.save()


# ------------------------------------------------------------------------------------------------ lineside (signs read from SOUTH)
def ground_sleeve(m, x, z, w=2.4, h=0.9):
    m.g('foundation')
    m.box((x - w / 2 - 1.0, 0, z - w / 2 - 1.0), (x + w / 2 + 1.0, 0.45, z + w / 2 + 1.0), 'concrete', 'concrete footing')
    m.box((x - w / 2, 0.45, z - w / 2), (x + w / 2, h, z + w / 2), 'galv', 'ground socket')
    m.bolt(x + w / 2, h - 0.3, z, 'east', 0.3, BOLT)
    m.bolt(x - w / 2, h - 0.3, z, 'west', 0.3, BOLT)


def whistle_post():
    m = Model('whistle_post')
    ground_sleeve(m, 8, 8.1, 2.4, 1.0)
    m.g('post')
    m.box((7.0, 1.0, 7.6), (9.0, 15.2, 8.6), 'perf', 'U-channel post', south='perf', north='galv')
    m.box((7.0, 15.2, 7.6), (9.0, 15.5, 8.6), 'galv', 'post cap')
    m.g('sign')
    m.box((3.0, 7.6, 8.6), (13.0, 15.4, 8.85), 'galv', 'sign blank (aluminium)')
    m.box((3.1, 7.7, 8.85), (12.9, 15.3, 8.95), 'txt:111111,f4f4ee,W', 'W board face', faces=('south',))
    for y in (8.4, 14.6):
        m.bolt(8.0, y, 8.95, 'south', 0.45, BOLT)
        m.box((7.4, y - 0.3, 8.42), (8.6, y + 0.3, 8.6), 'galv', 'backing washer')
    m.save()


def milepost():
    m = Model('milepost')
    m.g('foundation')
    m.box((5.4, 0, 5.4), (10.6, 0.6, 10.6), 'concrete', 'base slab')
    m.box((5.8, 0.6, 5.8), (10.2, 2.2, 10.2), 'concrete', 'plinth')
    m.box((5.6, 2.2, 5.6), (10.4, 2.5, 10.4), 'concrete', 'plinth cap')
    m.g('post')
    m.box((6.4, 2.5, 6.4), (9.6, 9.0, 9.6), 'paint:#ecece6', 'post shaft (whitewashed)')
    m.box((6.6, 9.0, 6.6), (9.4, 12.6, 9.4), 'paint:#ecece6', 'post head')
    m.box((6.6, 12.6, 6.6), (9.4, 13.2, 9.4), 'paint:#ecece6', 'head cap')
    m.box((6.8, 12.9, 7.2), (9.2, 13.5, 8.8), 'paint:#ecece6', 'weathering',
          rot={'axis': 'z', 'angle': 45, 'origin': [8, 13.2, 8]})
    m.g('number plates')
    for face, z0, z1 in (('south', 9.4, 9.55), ('north', 6.45, 6.6)):
        m.box((6.7, 9.3, z0), (9.3, 12.3, z1), 'paint:#1e5c34', 'mile plate', **{face: 'txt:f8f8f0,1e5c34,124'})
        for x in (6.95, 9.05):
            m.box((x - 0.12, 12.05, z0 if face == 'north' else z1), (x + 0.12, 12.2, z0 - 0.08 if face == 'north' else z1 + 0.08), BOLT, 'rivet')
    for face, x0, x1 in (('east', 9.4, 9.55), ('west', 6.45, 6.6)):
        m.box((x0, 9.6, 7.0), (x1, 11.8, 9.0), 'paint:#1e5c34', 'side plate', **{face: 'txt:f8f8f0,1e5c34,MP'})
    m.box((6.35, 3.4, 6.35), (9.65, 3.9, 9.65), 'paint:#1e5c34', 'painted band')
    m.save()


def speed_sign():
    """SpeedSignRenderer paints SPEED LIMIT + the number on the plate's south face at z = 10.0, centre y 11.5"""
    m = Model('speed_sign')
    ground_sleeve(m, 8, 8.2, 2.2, 1.0)
    m.g('post')
    m.box((7.0, 1.0, 7.7), (9.0, 15.0, 8.7), 'perf', 'U-channel post', south='perf', north='galv')
    m.box((7.0, 15.0, 7.7), (9.0, 15.2, 8.7), 'galv', 'post cap')
    m.g('sign')
    m.box((6.6, 8.6, 8.7), (9.4, 9.4, 9.6), 'galv', 'mounting bracket')
    m.box((6.6, 13.6, 8.7), (9.4, 14.4, 9.6), 'galv', 'mounting bracket')
    m.box((3.5, 7.5, 9.6), (12.5, 15.5, 10.0), 'galv', 'sign plate', south='paint:#f4f4ee')
    for (x0, y0, x1, y1) in ((3.85, 7.85, 12.15, 8.1), (3.85, 14.9, 12.15, 15.15), (3.85, 7.85, 4.1, 15.15), (11.9, 7.85, 12.15, 15.15)):
        m.box((x0, y0, 10.0), (x1, y1, 10.02), 'paint:#141414', 'black border', faces=('south',))
    for y in (7.75, 15.25):
        m.bolt(8.0, y, 10.0, 'south', 0.3, BOLT)
    m.save()


def switch_stand():
    """US high switch stand on the head-block ties: base casting, crank housing, hand lever + keeper, target, lamp"""
    m = Model('switch_stand')
    m.g('head-block ties')
    for z0 in (4.0, 9.6):
        m.box((0.8, 0, z0), (15.2, 1.3, z0 + 2.4), 'wood', 'head-block tie')
    m.g('base casting')
    m.box((3.6, 1.3, 4.4), (12.4, 1.8, 11.6), 'paint:#2c2c2e', 'base plate')
    for (x, z) in ((4.2, 5.0), (11.8, 5.0), (4.2, 11.0), (11.8, 11.0)):
        m.box((x - 0.25, 1.8, z - 0.25), (x + 0.25, 2.4, z + 0.25), 'metal:steel', 'lag screw')
    m.box((5.4, 1.8, 5.4), (10.6, 4.6, 10.6), 'paint:#2c2c2e', 'crank housing')
    m.box((5.1, 4.6, 5.1), (10.9, 5.0, 10.9), 'paint:#2c2c2e', 'housing cover')
    m.bolts(5.6, 10.4, 4.8, 5.1, 3, 'north', s=0.35, mat=BOLT)
    m.g('switch rod')
    m.box((7.6, 2.2, 0), (8.4, 2.8, 5.4), 'galv', 'connecting rod to points')
    m.box((7.3, 2.0, 1.0), (8.7, 3.0, 1.8), 'galv', 'rod jaw')
    m.g('hand lever')
    # live: the hand lever throws the IR switch beside the stand (TileLineside). East face of the crank housing x 10.6
    # -> surface 'east', u = 16 - z, face = 16 - x. Up = NORMAL, down into the keeper = REVERSE.
    lev = m.surface('east', face=5.4, title='SWITCH STAND')
    m.ctl(lev, 'lever', 'lever', 8.0, 4.1, w=0.6, h=2.6, col='2c2c2e', label='')
    m.box((13.9, 1.3, 7.0), (15.5, 3.2, 7.4), 'paint:#2c2c2e', 'lever keeper')
    m.box((13.9, 1.3, 8.6), (15.5, 3.2, 9.0), 'paint:#2c2c2e', 'lever keeper')
    m.box((14.3, 2.2, 6.6), (14.9, 3.0, 9.4), 'galv', 'keeper latch')
    m.box((14.3, 1.4, 9.0), (14.9, 2.2, 9.6), 'metal:brass', 'switch padlock')
    m.g('spindle')
    m.cyl((8, 5.0, 8), 0.65, 9.0, 'y', 'galv', 'spindle')
    m.g('target')
    m.box((3.0, 9.4, 7.75), (13.0, 12.8, 8.25), 'paint:ral3000', 'red target blade')
    m.box((7.75, 9.4, 3.0), (8.25, 12.8, 13.0), 'paint:#1f8a3c', 'green target blade')
    for x in (3.4, 12.6):
        m.box((x - 0.2, 10.6, 7.7), (x + 0.2, 11.6, 8.3), 'paint:#f0f0f0', 'reflector strip')
    m.g('switch lamp')
    m.box((6.5, 14.0, 6.5), (9.5, 15.4, 9.5), 'paint:#2c2c2e', 'lamp body')
    m.box((6.9, 15.4, 6.9), (9.1, 15.8, 9.1), 'paint:#2c2c2e', 'lamp chimney')
    for face, a, b, col in (('north', (7.3, 14.2, 6.2), (8.7, 15.2, 6.5), '3ad06a'), ('south', (7.3, 14.2, 9.5), (8.7, 15.2, 9.8), '3ad06a'),
                            ('east', (9.5, 14.2, 7.3), (9.8, 15.2, 8.7), 'f0b020'), ('west', (6.2, 14.2, 7.3), (6.5, 15.2, 8.7), 'f0b020')):
        m.box(a, b, 'paint:#2c2c2e', 'lens', **{face: 'lamp:' + col})
    m.save()


def derail():
    """hinged block derail on a rail stub (rail runs along x), throw lever, DERAIL sign"""
    m = Model('derail')
    m.g('ties')
    for x0 in (1.0, 6.4, 11.8):
        m.box((x0, 0, 4.5), (x0 + 3.0, 1.2, 11.5), 'wood', 'tie')
        m.box((x0 + 0.3, 1.2, 6.2), (x0 + 2.7, 1.45, 9.8), 'paint:#4a4038', 'tie plate')
        for z in (6.6, 9.4):
            m.box((x0 + 0.6, 1.45, z - 0.22), (x0 + 1.0, 2.0, z + 0.22), 'metal:steel', 'spike')
    m.g('rail')
    m.box((0, 1.45, 6.9), (16, 1.75, 9.1), 'paint:#5a4a3c', 'rail foot')
    m.box((0, 1.75, 7.7), (16, 3.3, 8.3), 'paint:#5a4a3c', 'rail web')
    m.box((0, 3.3, 7.3), (16, 4.0, 8.7), 'metal:steel', 'rail head')
    m.g('derail block')
    y = 'paint:ral1023'
    m.box((5.0, 1.8, 6.4), (11.4, 4.4, 7.1), y, 'body side')
    m.box((5.0, 1.8, 8.9), (11.4, 4.4, 9.6), y, 'body side')
    m.box((5.0, 4.0, 6.6), (11.4, 4.6, 9.4), y, 'shoe over head')
    m.box((5.4, 4.2, 6.9), (10.6, 5.2, 9.1), y, 'ramp', rot={'axis': 'z', 'angle': 22.5, 'origin': [5.4, 4.6, 8]})
    m.box((9.6, 4.6, 6.6), (11.4, 6.4, 7.6), y, 'throw-off flange')
    m.box((5.0, 1.8, 9.6), (6.2, 3.0, 11.0), y, 'hinge bracket')
    m.cyl((4.8, 2.4, 10.4), 0.35, 1.6, 'x', 'metal:steel', 'hinge pin')
    for x in (5.6, 7.4, 9.2, 11.0):
        m.bolt(x, 2.6, 6.4, 'north', 0.35, BOLT)
    m.g('throw lever')
    m.box((6.6, 1.2, 10.4), (8.2, 3.6, 11.3), 'paint:#2c2c2e', 'lever stand')
    m.box((7.1, 3.6, 10.6), (7.7, 7.2, 11.1), 'paint:#2c2c2e', 'throw lever', rot={'axis': 'x', 'angle': 22.5, 'origin': [7.4, 3.6, 10.85]})
    m.box((6.9, 1.2, 9.5), (7.9, 2.0, 10.4), 'galv', 'operating rod')
    m.box((8.2, 1.6, 10.6), (8.7, 2.3, 11.1), 'metal:brass', 'padlock')
    m.g('DERAIL sign')
    m.box((1.4, 0, 12.3), (2.6, 0.5, 13.5), 'concrete', 'sign footing')
    m.box((1.6, 0.5, 12.6), (2.4, 8.6, 13.2), 'perf', 'sign post')
    m.box((0.0, 6.2, 13.2), (4.0, 8.4, 13.4), 'galv', 'sign plate', south='txt:101010,f2c419,DERAIL')
    m.bolt(2.0, 6.6, 13.4, 'south', 0.25, BOLT)
    m.bolt(2.0, 8.0, 13.4, 'south', 0.25, BOLT)
    m.save()


def bumper():
    """bumping post facing NORTH (train arrives from z<0): concrete base, two columns, raking struts, timber head block"""
    m = Model('bumper')
    m.g('base')
    pad(m, 1.0, 3.0, 15.0, 13.0, 1.2, 'concrete base')
    for (x, z) in ((1.6, 3.6), (14.4, 3.6), (1.6, 12.4), (14.4, 12.4)):
        anchor(m, x, 1.2, z, 0.6)
    col = 'paint:ral3000'
    m.g('frame')
    for x0 in (2.4, 11.6):
        m.box((x0 - 0.4, 1.2, 4.6), (x0 + 2.4, 1.5, 7.0), 'galv', 'column base plate')
        m.box((x0, 1.5, 5.0), (x0 + 2.0, 9.0, 5.4), col, 'column flange')
        m.box((x0, 1.5, 6.2), (x0 + 2.0, 9.0, 6.6), col, 'column flange')
        m.box((x0 + 0.8, 1.5, 5.4), (x0 + 1.2, 9.0, 6.2), col, 'column web')
        m.box((x0 + 0.4, 4.6 - 4.8, 9.0), (x0 + 1.6, 4.6 + 4.8, 10.2), col, 'raking strut',
              rot={'axis': 'x', 'angle': -45, 'origin': [x0 + 1.0, 4.6, 9.6]})
        m.box((x0 - 0.2, 1.2, 11.4), (x0 + 2.2, 1.5, 13.0), 'galv', 'strut foot plate')
        for z in (5.0, 6.6):
            m.bolt(x0 - 0.1, 1.5, z, 'up', 0.35, BOLT)
            m.bolt(x0 + 2.1, 1.5, z, 'up', 0.35, BOLT)
    m.box((2.4, 4.0, 5.4), (13.6, 4.8, 6.2), col, 'tie beam')
    m.g('head block')
    m.box((1.4, 8.4, 4.4), (14.6, 12.4, 6.8), col, 'head beam')
    m.box((1.6, 8.6, 3.4), (14.4, 12.2, 4.4), 'wood', 'timber buffer face')
    m.box((1.2, 12.4, 4.2), (14.8, 12.8, 7.0), col, 'beam cap')
    for x in (3.0, 6.0, 10.0, 13.0):
        m.bolt(x, 9.3, 3.4, 'north', 0.45, BOLT)
        m.bolt(x, 11.5, 3.4, 'north', 0.45, BOLT)
    for x in (4.3, 11.7):
        m.box((x - 0.9, 9.5, 3.3), (x + 0.9, 11.3, 3.4), 'lamp:ff2a1a', 'red reflector')
    m.box((6.0, 9.6, 3.3), (10.0, 11.2, 3.4), 'haz', 'hazard plate')
    m.save()


def crossbuck():
    m = Model('crossbuck')
    m.g('foundation')
    m.cyl((8, 0, 8), 2.0, 0.5, 'y', 'concrete', 'concrete footing')
    m.box((6.4, 0.5, 6.4), (9.6, 0.8, 9.6), 'galv', 'breakaway base plate')
    for (x, z) in ((6.8, 6.8), (9.2, 6.8), (6.8, 9.2), (9.2, 9.2)):
        m.bolt(x, 0.8, z, 'up', 0.35, BOLT)
    m.g('post')
    m.cyl((8, 0.8, 8), 0.85, 14.2, 'y', 'galv', 'round post')
    m.cyl((8, 15.0, 8), 0.95, 0.4, 'y', 'galv', 'post cap')
    m.box((7.3, 1.4, 8.78), (8.7, 7.4, 8.9), 'paint:#f4f4ee', 'retroreflective strip')
    m.g('crossbuck')
    yc = 12.0
    m.box((6.8, yc - 1.0, 8.8), (9.2, yc + 1.0, 9.05), 'galv', 'mounting bracket')
    for ang, z0, word in ((45, 9.05, 'RAILROAD'), (-45, 9.35, 'CROSSING')):
        m.box((2.4, yc - 1.05, z0), (13.6, yc + 1.05, z0 + 0.3), 'galv', 'crossbuck blade', south='label:' + word,
              rot={'axis': 'z', 'angle': ang, 'origin': [8, yc, z0 + 0.15]})
    m.bolt(8.0, yc, 9.65, 'south', 0.5, BOLT)
    m.save()


def lineside_cabinet(name, x0, x1, z0, z1, h, doors, label, item_gui=0.625):
    m = Model(name)
    m.g('foundation')
    pad(m, x0 - 0.5, z0 - 0.3, x1 + 0.5, z1 + 0.3, 0.9, 'concrete plinth')
    for (x, z) in ((x0, z0 + 0.1), (x1, z0 + 0.1), (x0, z1 - 0.1), (x1, z1 - 0.1)):
        anchor(m, x, 0.9, z, 0.5)
    stand(m, x0 + 0.4, x1 - 0.4, z0 + 0.5, z1 - 0.5, 0.9, 1.8)
    n = 2 if (x1 - x0) > 7 else 1
    cable_entry(m, [(x0 + 2.0 + i * (x1 - x0 - 4.0) / max(1, n), (z0 + z1) / 2 + 0.5, '1d1d21') for i in range(n + 1)], 0.9, 1.8)
    case(m, x0, x1, z0, z1, 1.8, h, 'ral7035', doors=doors, label=label, label2=(x1 - x0) > 7, hinges=3 if h > 18 else 2)
    m.g('case details')
    m.box((x0 + 1.0, 2.2, z1), (x1 - 1.0, 3.4, z1 + 0.25), 'ral7035', 'rear gland plate')
    m.bolts(x0 + 1.4, x1 - 1.4, 2.8, z1 + 0.25, 3, 'south', s=0.3, mat=BOLT)
    earth(m, x1 - 0.6, 2.2, z1 + 0.45)
    m.save(item_gui=item_gui)


def cable_drum():
    """wooden cable drum (axis along x) with signalling cable wound on, chocked"""
    m = Model('cable_drum')
    m.g('flanges')
    yc, r = 6.0, 6.1
    for x0 in (2.8, 12.0):
        m.cyl((x0, yc, 8), r, 1.2, 'x', 'wood', 'drum flange')
        m.cyl((x0 - 0.15 if x0 < 8 else x0 + 1.2, yc, 8), 1.4, 0.15, 'x', 'galv', 'arbor plate')
        face = 'west' if x0 < 8 else 'east'
        xf = x0 if x0 < 8 else x0 + 1.2
        for k in range(8):
            a = k * math.pi / 4
            m.bolt(xf, yc + math.sin(a) * 3.6, 8 + math.cos(a) * 3.6, face, 0.4, BOLT)
        for k in (-1, 1):
            m.box((xf - 0.05 if face == 'west' else xf, yc - 5.4, 8 + k * 1.4 - 0.4),
                  (xf if face == 'west' else xf + 0.05, yc + 5.4, 8 + k * 1.4 + 0.4), 'paint:#6a4a2a', 'plank joint')
    m.box((2.6, yc + 1.4, 5.6), (2.8, yc + 3.6, 10.4), 'txt:f0f0e8,7a5434,SIG CABLE|500M', 'stencil', faces=('west',))
    m.g('barrel and cable')
    m.cyl((4.0, yc, 8), 4.6, 8.0, 'x', 'cable:1d1d21', 'cable layers')
    for i in range(9):
        m.cyl((4.15 + i * 0.88, yc, 8), 4.75, 0.5, 'x', 'cable:26262a', 'cable turn')
    m.cyl((1.6, yc, 8), 0.65, 12.8, 'x', 'metal:steel', 'drum spindle')
    m.g('cable tail')
    m.box((7.0, 0.4, 3.0), (7.8, yc + 4.4, 3.8), 'cable:1d1d21', 'cable end',
          rot={'axis': 'x', 'angle': 22.5, 'origin': [7.4, yc + 4.4, 3.4]})
    m.box((6.9, 0.0, 0.6), (7.9, 0.8, 3.0), 'cable:1d1d21', 'cable end on ground')
    m.box((6.8, 0.0, 0.2), (8.0, 0.9, 0.6), 'paint:ral3000', 'end cap')
    m.g('chocks')
    for x0 in (2.8, 12.0):
        for zc, ang in ((2.2, -45), (13.8, 45)):
            m.box((x0, 0, zc - 0.8), (x0 + 1.2, 1.6, zc + 0.8), 'wood', 'chock',
                  rot={'axis': 'x', 'angle': ang, 'origin': [x0 + 0.6, 0, zc]})
    m.save()


def bollard():
    m = Model('bollard')
    m.g('base')
    m.box((5.4, 0, 5.4), (10.6, 0.4, 10.6), 'galv', 'base plate')
    for (x, z) in ((5.9, 5.9), (10.1, 5.9), (5.9, 10.1), (10.1, 10.1)):
        anchor(m, x, 0.4, z, 0.5)
    for (a, b) in (((7.85, 0.4, 6.3), (8.15, 1.6, 7.0)), ((7.85, 0.4, 9.0), (8.15, 1.6, 9.7)),
                   ((6.3, 0.4, 7.85), (7.0, 1.6, 8.15)), ((9.0, 0.4, 7.85), (9.7, 1.6, 8.15))):
        m.box(a, b, 'galv', 'gusset')
    m.g('post')
    m.cyl((8, 0.4, 8), 1.6, 14.2, 'y', 'haz', 'steel tube')
    for y, c in ((11.6, 'paint:#f4f4ee'), (12.6, 'paint:ral3000'), (13.6, 'paint:#f4f4ee')):
        m.cyl((8, y, 8), 1.66, 0.9, 'y', c, 'reflective band')
    m.cyl((8, 14.6, 8), 1.7, 0.6, 'y', 'paint:#1c1c1c', 'cap')
    m.cyl((8, 15.2, 8), 1.1, 0.5, 'y', 'paint:#1c1c1c', 'dome')
    m.save()


def marker_post():
    m = Model('marker_post')
    m.g('base')
    m.box((5.8, 0, 5.8), (10.2, 0.4, 10.2), 'galv', 'base plate')
    for (x, z) in ((6.3, 6.3), (9.7, 6.3), (6.3, 9.7), (9.7, 9.7)):
        m.bolt(x, 0.4, z, 'up', 0.4, BOLT)
    m.box((6.6, 0.4, 6.6), (9.4, 1.2, 9.4), 'galv', 'socket')
    m.g('banded post')
    y = 1.2
    i = 0
    while y < 16.8:
        y1 = min(16.8, y + 2.0)
        m.box((6.8, y, 6.8), (9.2, y1, 9.2), 'paint:ral3000' if i % 2 == 0 else 'paint:#f2f2ee', 'band')
        y, i = y1, i + 1
    m.box((6.6, 16.8, 6.6), (9.4, 17.2, 9.4), 'paint:#1c1c1c', 'post cap')
    m.box((7.4, 13.0, 9.2), (8.6, 14.4, 9.3), 'lamp:ff3020', 'reflector', faces=('south',))
    m.box((7.4, 13.0, 6.7), (8.6, 14.4, 6.8), 'lamp:ff3020', 'reflector', faces=('north',))
    m.save(item_gui=0.55)


def board_post(m, h=12.0):
    """galvanised RHS post on a base plate, read from the south"""
    m.g('post')
    m.box((5.6, 0, 6.2), (10.4, 0.4, 9.8), 'galv', 'base plate')
    for (x, z) in ((6.1, 6.7), (9.9, 6.7), (6.1, 9.3), (9.9, 9.3)):
        anchor(m, x, 0.4, z, 0.45)
    m.box((7.0, 0.4, 7.0), (9.0, h, 8.6), 'galv', 'RHS post')
    m.box((6.9, h, 6.9), (9.1, h + 0.2, 8.7), 'galv', 'post cap')
    m.box((7.6, 0.4, 8.6), (8.4, 1.6, 9.2), 'galv', 'gusset')
    m.box((7.6, 0.4, 6.4), (8.4, 1.6, 7.0), 'galv', 'gusset')


def board_clamps(m, ys, z):
    for y in ys:
        m.box((6.6, y, 6.8), (9.4, y + 0.6, 8.8), 'galv', 'post clamp')
        m.bolt(6.6, y + 0.3, 7.8, 'west', 0.3, BOLT)
        m.bolt(9.4, y + 0.3, 7.8, 'east', 0.3, BOLT)


def stop_board():
    m = Model('stop_board')
    board_post(m, 13.0)
    m.g('board')
    m.box((2.5, 11.0, 8.8), (13.5, 21.0, 9.1), 'galv', 'board backing')
    for i in range(4):
        for j in range(4):
            c = 'paint:ral3000' if (i + j) % 2 == 0 else 'paint:#f4f4ee'
            m.box((2.6 + i * 2.7, 11.1 + j * 2.45, 9.1), (2.6 + (i + 1) * 2.7, 11.1 + (j + 1) * 2.45, 9.15), c, 'checker square', faces=('south',))
    for (a, b) in (((2.5, 11.0, 8.6), (13.5, 11.4, 8.8)), ((2.5, 20.6, 8.6), (13.5, 21.0, 8.8)),
                   ((2.5, 11.0, 8.6), (2.9, 21.0, 8.8)), ((13.1, 11.0, 8.6), (13.5, 21.0, 8.8))):
        m.box(a, b, 'galv', 'stiffening frame')
    board_clamps(m, (11.8, 12.6), 7.8)
    m.box((7.2, 13.0, 7.2), (8.8, 20.6, 8.6), 'galv', 'board stiffener rail')
    m.save(item_gui=0.5)


def limit_disc():
    m = Model('limit_disc')
    board_post(m, 12.0)
    m.g('disc')
    m.box((7.0, 12.0, 7.4), (9.0, 16.0, 8.6), 'galv', 'disc support')
    m.cyl((8, 16, 8.6), 5.4, 0.4, 'z', 'galv', 'disc backing', cap='galv')
    m.cyl((8, 16, 9.0), 5.0, 0.12, 'z', 'paint:#f4f4ee', 'white disc face', cap='paint:#f4f4ee')
    m.box((3.6, 15.3, 9.12), (12.4, 16.7, 9.2), 'paint:ral3000', 'red diagonal bar', faces=('south', 'up', 'down', 'east', 'west'),
          rot={'axis': 'z', 'angle': 45, 'origin': [8, 16, 9.15]})
    board_clamps(m, (11.0,), 7.8)
    m.bolt(8, 16, 9.2, 'south', 0.4, BOLT)
    m.save(item_gui=0.5)


def blank_board():
    m = Model('blank_board')
    board_post(m, 12.0)
    m.g('disc')
    m.box((7.0, 12.0, 7.4), (9.0, 16.0, 8.6), 'galv', 'disc support')
    m.cyl((8, 16, 8.6), 5.4, 0.4, 'z', 'paint:#f4f4ee', 'white rim', cap='paint:#f4f4ee')
    m.cyl((8, 16, 9.0), 4.7, 0.12, 'z', 'paint:#161618', 'black disc face', cap='paint:#161618')
    board_clamps(m, (11.0,), 7.8)
    m.bolt(8, 16, 9.12, 'south', 0.4, BOLT)
    m.save(item_gui=0.5)


def id_plate():
    m = Model('id_plate')
    m.g('post')
    m.box((6.0, 0, 6.6), (10.0, 0.4, 9.4), 'galv', 'base plate')
    for (x, z) in ((6.5, 7.0), (9.5, 7.0), (6.5, 9.0), (9.5, 9.0)):
        m.bolt(x, 0.4, z, 'up', 0.35, BOLT)
    m.box((7.0, 0.4, 7.2), (9.0, 9.0, 8.6), 'galv', 'post')
    m.box((6.9, 9.0, 7.1), (9.1, 9.2, 8.7), 'galv', 'post cap')
    m.g('plate')
    m.box((4.5, 7.0, 8.6), (11.5, 13.0, 8.85), 'galv', 'plate blank')
    m.box((4.6, 7.1, 8.85), (11.4, 12.9, 8.9), 'txt:101010,f2c829,LS|123', 'ID face', faces=('south',))
    for (x, y) in ((5.1, 7.6), (10.9, 7.6), (5.1, 12.4), (10.9, 12.4)):
        m.bolt(x, y, 8.9, 'south', 0.3, BOLT)
    m.box((7.6, 9.2, 7.6), (8.4, 12.6, 8.6), 'galv', 'plate extension bracket')
    m.save()


# ------------------------------------------------------------------------------------------------ track-side power
def palisade(m, pts, h=9.0, step=1.0):
    """galvanised palisade fence along a polyline of (x, z): rails + pales with split tops"""
    m.g('palisade fence')
    for (xa, za), (xb, zb) in zip(pts, pts[1:]):
        L = math.hypot(xb - xa, zb - za)
        n = max(1, int(L / step))
        alongx = abs(xb - xa) > abs(zb - za)
        for y in (1.6, h - 1.6):
            if alongx: m.box((min(xa, xb), y, za - 0.15), (max(xa, xb), y + 0.4, za + 0.15), 'galv', 'fence rail')
            else: m.box((xa - 0.15, y, min(za, zb)), (xa + 0.15, y + 0.4, max(za, zb)), 'galv', 'fence rail')
        for i in range(n + 1):
            t = i / n
            x, z = xa + (xb - xa) * t, za + (zb - za) * t
            if alongx: m.box((x - 0.2, 0.6, z - 0.32), (x + 0.2, h, z - 0.15), 'galv', 'pale')
            else: m.box((x - 0.32, 0.6, z - 0.2), (x - 0.15, h, z + 0.2), 'galv', 'pale')
    for (x, z) in pts:
        m.box((x - 0.35, 0.6, z - 0.35), (x + 0.35, h + 0.4, z + 0.35), 'galv', 'fence post')


def substation():
    """railway DC substation kiosk on its pad: HV cable boxes (grid feed), transformer, rectifier cubicle with
    breaker controls, DC feeder + return cables to the track (north), palisade fence, DANGER signs"""
    m = Model('substation')
    m.g('pad')
    pad(m, 0.3, 0.3, 15.7, 15.7, 0.6, 'concrete pad')
    m.box((0.3, 0.6, 0.3), (15.7, 0.7, 0.8), 'haz', 'kerb stripe')
    palisade(m, [(0.6, 4.0), (0.6, 15.4), (15.4, 15.4), (15.4, 4.0)], h=9.0)
    for x, z, f in ((0.45, 9.6, 'west'), (15.55, 9.6, 'east')):
        m.box((x - (0.12 if f == 'west' else 0), 5.0, z - 2.0), (x + (0.12 if f == 'east' else 0), 7.6, z + 2.0),
              'danger', 'fence DANGER sign', **{f: 'danger:DANGER|HIGH VOLTAGE'})
    m.box((5.0, 5.0, 15.55), (11.0, 7.6, 15.7), 'danger', 'fence DANGER sign', south='danger:DANGER|HIGH VOLTAGE|KEEP OUT')
    # ---- transformer (west half)
    m.g('transformer')
    m.box((1.6, 0.7, 6.0), (7.4, 1.2, 13.6), 'paint:#3a3e44', 'transformer skid')
    m.box((2.2, 1.2, 6.6), (6.8, 8.4, 13.0), 'tank', 'transformer tank')
    m.box((2.0, 8.4, 6.4), (7.0, 8.8, 13.2), 'tank', 'tank cover')
    for z in (7.2, 8.4, 9.6, 10.8, 12.0):
        m.box((1.2, 2.0, z), (2.2, 7.6, z + 0.35), 'fins', 'radiator fin')
    m.cyl((1.7, 7.9, 6.9), 0.3, 5.6, 'z', 'tank', 'radiator header')
    m.cyl((1.7, 1.7, 6.9), 0.3, 5.6, 'z', 'tank', 'radiator header')
    for z in (7.6, 9.8, 12.0):
        m.bushing(3.2, z, 8.8, 3.6, sheds=3)
    for z in (8.6, 11.0):
        m.bushing(5.8, z, 8.8, 2.0, sheds=2, mat='porcelain:grey')
    m.box((5.0, 12.4, 7.8), (8.6, 13.8, 11.8), 'ral7035', 'LV bus trunking to rectifier')
    m.bolts(5.3, 8.3, 13.1, 7.8, 4, 'north', s=0.3, mat=BOLT)
    m.box((6.8, 4.0, 10.6), (7.0, 6.4, 12.6), 'plate', 'rating plate', east='plate:1.5 MVA|33KV/1.2KV')
    # live: oil temperature (follows the load); tank east face x 6.8 -> surface 'east', u = 16 - z, face = 16 - x
    oil = m.surface('east', face=9.2, title='TRANSFORMER')
    m.ctl(oil, 'gauge', 'temp', 8.3, 2.4, w=1.4, h=1.4, col='f2f0e6', label='OIL C', max=120)
    m.cyl((4.5, 8.8, 9.8), 0.5, 0.8, 'y', 'paint:ral1023', 'pressure relief device')
    # HV grid cable boxes on the transformer's back (south) - cables go into the ground
    m.hv_box(4.5, 2.2, 13.0, 'south', phases=3)
    # ---- rectifier cubicle (east half)
    m.g('rectifier cubicle')
    case(m, 8.6, 14.8, 2.4, 13.4, 1.2, 13.6, 'ral7035', doors=2, vents=True, label='RECTIFIER|1500V DC', label2=True, hinges=3)
    m.box((8.4, 0.7, 2.2), (15.0, 1.2, 13.6), 'galv', 'cubicle base frame')
    m.g('control panel')
    zf = 2.4 - 0.34
    m.box((12.3, 9.6, zf - 0.1), (14.1, 10.6, zf), 'plate', 'breaker plate', north='plate:FEEDER CB')
    # live (TilePowerPost): meters, breaker lamps under the plate, CLOSE / TRIP / RESET under their lamps, LOCAL/REMOTE
    # selector, HV-in lamp and an energy-store meter. North face -> u = 16 - x, face = zf.
    cp = m.surface('north', face=zf, title='FEEDER CB')
    m.ctl(cp, 'gauge', 'volts', 16 - 10.25, 10.1, w=1.8, h=1.8, col='f2f0e6', label='LINE V %', max=125)
    m.ctl(cp, 'gauge', 'amps', 16 - 10.25, 8.1, w=1.8, h=1.8, col='f2f0e6', label='DC A', max=4000)
    m.ctl(cp, 'gauge', 'store', 16 - 9.9, 5.3, w=1.4, h=1.4, col='f2f0e6', label='STORE %', max=100)
    for x, col, cid, lab in ((12.6, '30d050', 'closed', 'CLOSED'), (13.2, 'e03020', 'opened', 'OPEN'), (13.8, 'f0b020', 'tripped', 'TRIP')):
        m.ctl(cp, 'lamp', cid, 16 - x, 9.22, w=0.44, h=0.44, col=col, label='', **({'blink': True} if cid == 'tripped' else {}))
    m.ctl(cp, 'lamp', 'grid', 16 - 12.6, 11.4, w=0.44, h=0.44, col='f0f0f0', label='HV IN')
    for x, col, cid, lab in ((12.6, '2aa040', 'close', 'CLOSE'), (13.2, 'd02a20', 'trip', 'TRIP'), (13.8, '2050d0', 'reset', 'RESET')):
        m.ctl(cp, 'push', cid, 16 - x, 8.0, w=0.46, h=0.46, col=col, label=lab)
    m.ctl(cp, 'selector', 'mode', 16 - 13.2, 6.6, w=0.7, h=0.7, col='2a2c30', label='CONTROL', pos=['REMOTE', 'LOCAL'])
    m.g('DC feeders')
    for x, col, nm in ((10.2, 'cable:b01c1c', 'DC + feeder to contact system'), (12.6, 'cable:1d1d21', 'DC - return to rails')):
        m.box((x - 0.5, 1.2, 1.6), (x + 0.5, 2.2, 2.4), 'metal:brass', 'feeder gland')
        m.box((x - 0.4, 0.7, 0.0), (x + 0.4, 1.5, 2.0), col, nm)
    m.terminal_12v(11.6, 1.4, 13.6, face='south')
    m.g('earthing')
    m.box((7.6, 0.6, 2.6), (8.0, 1.0, 14.6), 'metal:copper', 'earth bar')
    for z in (4.0, 13.0):
        m.box((7.4, 0.6, z), (8.2, 1.4, z + 0.5), 'metal:copper', 'earth connection')
    m.box((7.2, 1.0, 14.2), (8.4, 1.6, 14.6), 'paint:#3a8a3a', 'earth label')
    m.save(item_gui=0.55)


def charging_station():
    """battery-train charger: HV grid cable box, transformer-rectifier cabinet with status display and START / STOP /
    emergency stop, charging connector on a cable boom toward the track (north), bollards, DANGER sign"""
    m = Model('charging_station')
    m.g('pad')
    pad(m, 0.6, 0.6, 15.4, 15.4, 0.6, 'concrete pad')
    m.box((0.6, 0.6, 0.6), (15.4, 0.7, 1.1), 'haz', 'kerb stripe')
    for x in (1.6, 14.4):
        m.cyl((x, 0.6, 2.0), 0.7, 8.0, 'y', 'paint:ral1023', 'protection bollard')
        m.cyl((x, 8.6, 2.0), 0.75, 0.6, 'y', 'paint:#1c1c1c', 'bollard cap')
        m.cyl((x, 6.0, 2.0), 0.74, 0.8, 'y', 'paint:#f4f4ee', 'reflective band')
    m.g('charger cabinet')
    case(m, 3.2, 12.8, 5.0, 13.6, 1.4, 18.0, 'ral7016', doors=2, vents=True, label='CHARGER 2', label2=True, hinges=3)
    m.box((3.0, 0.6, 4.8), (13.0, 1.4, 13.8), 'galv', 'cabinet base frame')
    m.g('operator panel')
    zf = 5.0 - 0.34
    m.box((4.6, 12.4, zf - 0.2), (8.6, 15.2, zf), 'paint:#1c1c1e', 'display bezel')
    m.box((9.6, 13.0, zf - 0.1), (12.0, 13.8, zf), 'plate', 'lamp legend', north='plate:RDY CHG FLT')
    m.box((4.6, 9.0, zf - 0.1), (8.6, 11.0, zf), 'plate', 'rating plate', north='plate:DC 750V|500 KW')
    # live (TilePowerPost, charger): status display, READY / CHARGING / FAULT, START / STOP (ids close / trip, as on the
    # console before), the latching EMERGENCY STOP. North face -> u = 16 - x.
    scr = m.surface('north', face=zf - 0.2, title='CHARGER DISPLAY')
    m.ctl(scr, 'digits', 'disp1', 16 - 6.6, 14.3, w=3.3, h=0.9, col='3ad0ff', label='', fmt='%s')
    m.ctl(scr, 'digits', 'disp2', 16 - 6.6, 13.25, w=3.3, h=0.9, col='3ad0ff', label='', fmt='%s')
    op = m.surface('north', face=zf, title='CHARGER')
    for x, col, cid in ((10.0, '30d050', 'ready'), (10.8, '3a8aff', 'charging'), (11.6, 'e03020', 'fault')):
        m.ctl(op, 'lamp', cid, 16 - x, 14.45, w=0.5, h=0.5, col=col, label='', **({'blink': True} if cid == 'fault' else {}))
    m.ctl(op, 'push', 'close', 16 - 10.0, 11.4, w=0.64, h=0.64, col='2aa040', label='START')
    m.ctl(op, 'push', 'trip', 16 - 11.6, 11.4, w=0.64, h=0.64, col='3a3c40', label='STOP')
    m.ctl(op, 'mushroom', 'estop', 16 - 10.8, 9.7, w=1.1, h=1.1, col='d02020', label='')
    m.g('connector holster')
    m.box((12.8, 7.0, 7.0), (14.0, 10.6, 9.6), 'paint:#2a2c30', 'connector holster')
    m.box((14.0, 7.6, 7.6), (14.8, 9.6, 9.0), 'paint:#3a3c40', 'charging connector')
    m.box((14.8, 8.0, 7.9), (15.2, 9.2, 8.7), 'paint:ral1023', 'connector latch')
    m.g('charging cable')
    m.box((13.0, 10.6, 8.0), (13.8, 18.4, 8.8), 'cable:2a2a2e', 'charging cable riser')
    m.box((12.6, 18.0, 1.4), (14.2, 18.8, 9.2), 'galv', 'cable boom')
    m.box((12.8, 18.8, 1.4), (14.0, 19.2, 9.2), 'galv', 'boom cap')
    m.box((13.0, 13.0, 1.6), (13.8, 18.0, 2.4), 'cable:2a2a2e', 'pendant cable')
    m.box((12.7, 11.8, 1.3), (14.1, 13.0, 2.7), 'paint:#3a3c40', 'pendant connector')
    m.box((12.6, 16.0, 8.6), (14.2, 18.0, 9.4), 'galv', 'boom brace', rot={'axis': 'x', 'angle': 45, 'origin': [13.4, 17.0, 9.0]})
    m.g('beacon')
    m.box((6.2, 18.7, 7.0), (7.4, 19.2, 8.2), 'paint:#2a2c30', 'beacon base')
    m.box((6.3, 19.2, 7.1), (7.3, 20.4, 8.1), 'paint:#1a3050', 'status beacon lens')
    bc = m.surface('north', face=7.1, title='BEACON')          # live: flashes blue while a train is charging
    m.ctl(bc, 'lamp', 'beacon', 16 - 6.8, 19.8, w=1.0, h=1.2, col='30a0ff', label='', blink=True)
    m.g('grid feed')
    m.hv_box(8.0, 2.0, 13.6, 'south', phases=3)
    m.box((4.0, 0.6, 13.8), (12.0, 1.2, 15.0), 'galv', 'cable trench cover')
    m.box((5.0, 6.0, 13.62), (11.0, 8.6, 13.7), 'danger', 'DANGER sign', south='danger:DANGER|HIGH VOLTAGE')
    m.terminal_12v(4.6, 2.0, 5.0, face='north')
    earth(m, 2.4, 1.6, 9.0)
    m.save(item_gui=0.5)


def aei_reader():
    """AEI tag reader: antenna on the ties between the rails, reader cabinet (west) and junction post (east)"""
    m = Model('aei_reader')
    m.g('ballast and ties')
    m.box((0, 0, 0), (16, 0.8, 16), 'ballast', 'ballast')
    for z0 in (1.0, 6.6, 12.2):
        m.box((0.4, 0.8, z0), (15.6, 2.0, z0 + 2.8), 'wood', 'tie')
    m.g('antenna')
    m.box((5.4, 2.0, 4.4), (10.6, 2.3, 11.6), 'galv', 'antenna mounting plate')
    for (x, z) in ((5.8, 4.8), (10.2, 4.8), (5.8, 11.2), (10.2, 11.2)):
        m.bolt(x, 2.3, z, 'up', 0.35, BOLT)
    m.box((6.2, 2.3, 5.0), (9.8, 3.4, 11.0), 'paint:#2a2c30', 'AEI antenna')
    m.box((6.4, 3.4, 5.2), (9.6, 3.5, 10.8), 'paint:#3a3e44', 'antenna radome')
    m.box((6.8, 3.5, 6.0), (9.2, 3.52, 7.4), 'txt:f0f0f0,3a3e44,AEI', 'antenna label', faces=('up',))
    m.cyl((8.0, 2.6, 11.0), 0.3, 0.6, 'z', 'metal:brass', 'coax gland')
    m.g('cable route')
    m.box((-1.0, 0.8, 8.1), (16.0, 1.2, 8.9), 'cable:1d1d21', 'reader cable under ties')
    m.box((15.6, 0.8, 7.9), (17.0, 1.3, 9.1), 'galv', 'cable duct')
    m.box((-1.0, 0.8, 7.9), (0.4, 1.3, 9.1), 'galv', 'cable duct')
    m.g('wheel sensors')
    for z in (3.6, 13.0):
        m.box((2.6, 2.0, z - 0.6), (4.0, 3.2, z + 0.6), 'paint:#2a2c30', 'wheel sensor')
        m.box((12.0, 2.0, z - 0.6), (13.4, 3.2, z + 0.6), 'paint:#2a2c30', 'wheel sensor')
    # west: reader cabinet on a pedestal
    m.g('reader cabinet')
    m.box((-4.4, 0, 5.6), (-0.6, 1.4, 10.4), 'concrete', 'pedestal footing')
    m.box((-3.4, 1.4, 7.2), (-1.6, 4.8, 8.8), 'galv', 'pedestal')
    m.box((-4.6, 4.8, 5.6), (-0.4, 9.6, 10.4), 'ral7035', 'reader cabinet')
    m.box((-4.8, 9.6, 5.4), (-0.2, 10.0, 10.6), 'ral7035', 'cabinet roof')
    m.box((-0.4, 5.2, 6.0), (-0.2, 9.2, 10.0), 'ral7035', 'cabinet door')
    m.box((-0.2, 6.0, 6.6), (-0.15, 7.2, 9.4), 'plate', 'reader plate', east='plate:AEI|READER')
    # live (TileWayside): the last train read, or the count while one is passing. Door east face x -0.2 ->
    # surface 'east', u = 16 - z, face = 16 - x
    rd = m.surface('east', face=16.2, title='AEI READER')
    m.ctl(rd, 'digits', 'disp', 8.0, 8.25, w=2.8, h=1.2, col='ffb020', label='', fmt='%s')
    m.box((-0.2, 5.4, 9.4), (0.05, 6.0, 9.8), 'metal:brass', 'door lock')
    m.box((-2.9, 0.8, 8.2), (-2.1, 4.8, 8.8), 'cable:1d1d21', 'cable riser')
    m.terminal_12v(-2.5, 1.4, 5.6, face='north')
    m.g('junction post')
    m.box((16.6, 0, 6.0), (19.6, 1.4, 10.0), 'concrete', 'post footing')
    m.box((17.4, 1.4, 7.2), (18.8, 7.6, 8.8), 'galv', 'junction post')
    m.box((16.9, 4.4, 6.4), (19.3, 7.4, 9.6), 'ral7035', 'sensor junction box')
    m.box((16.7, 7.4, 6.2), (19.5, 7.8, 9.8), 'ral7035', 'box lid')
    m.box((16.7, 5.0, 6.8), (16.9, 6.8, 9.2), 'plate', 'box plate', west='plate:WS JB')
    m.box((17.8, 7.8, 7.6), (18.4, 8.4, 8.2), 'paint:#2a2c30', 'beacon base')
    m.box((17.85, 8.4, 7.65), (18.35, 9.4, 8.15), 'paint:#4a3a18', 'reading beacon lens')
    # live: lit while a train is being read; west face (toward the track) x 17.85 -> surface 'west', u = z, face = x
    bn = m.surface('west', face=17.85, title='READING')
    m.ctl(bn, 'lamp', 'beacon', 7.9, 8.9, w=0.5, h=1.0, col='f0b020', label='')
    m.save(item_gui=0.45)


def track_scale():
    """weigh-in-motion track scale: pit walls, weighbridge girders on four load cells, junction box, indicator"""
    m = Model('track_scale')
    m.g('pit')
    m.box((0, 0, 0), (16, 2.0, 1.0), 'concrete', 'pit wall')
    m.box((0, 0, 15.0), (16, 2.0, 16), 'concrete', 'pit wall')
    m.box((0, 0, 1.0), (1.0, 2.0, 15.0), 'concrete', 'pit wall')
    m.box((15.0, 0, 1.0), (16, 2.0, 15.0), 'concrete', 'pit wall')
    m.box((1.0, 0, 1.0), (15.0, 0.4, 15.0), 'concrete', 'pit floor')
    m.box((0, 2.0, 0), (16, 2.2, 1.0), 'galv', 'pit edge angle')
    m.box((0, 2.0, 15.0), (16, 2.2, 16), 'galv', 'pit edge angle')
    m.g('load cells')
    for (x, z) in ((2.5, 2.5), (13.5, 2.5), (2.5, 13.5), (13.5, 13.5)):
        m.box((x - 1.0, 0.4, z - 1.0), (x + 1.0, 0.8, z + 1.0), 'galv', 'load cell base plate')
        m.cyl((x, 0.8, z), 0.75, 1.3, 'y', 'metal:steel', 'load cell')
        m.box((x - 0.9, 2.1, z - 0.9), (x + 0.9, 2.4, z + 0.9), 'galv', 'top plate')
        m.box((x + (0.6 if x < 8 else -1.6), 1.2, z - 0.15), (x + (1.6 if x < 8 else -0.6), 1.5, z + 0.15), 'cable:e07a20', 'load cell cable')
    m.g('weighbridge')
    for x0 in (3.0, 11.8):
        m.box((x0, 2.4, 1.2), (x0 + 1.2, 2.8, 14.8), 'paint:#3a3e44', 'girder bottom flange')
        m.box((x0 + 0.4, 2.8, 1.2), (x0 + 0.8, 4.4, 14.8), 'paint:#3a3e44', 'girder web')
        m.box((x0, 4.4, 1.2), (x0 + 1.2, 4.8, 14.8), 'paint:#3a3e44', 'girder top flange')
    for z in (2.0, 8.0, 14.0):
        m.box((1.4, 2.4, z - 0.4), (14.6, 4.4, z + 0.4), 'paint:#3a3e44', 'cross girder')
    m.box((1.2, 4.4, 1.2), (3.0, 4.8, 14.8), 'tread', 'deck plate')
    m.box((4.2, 4.4, 1.2), (11.8, 4.8, 14.8), 'tread', 'deck plate')
    m.box((13.0, 4.4, 1.2), (14.8, 4.8, 14.8), 'tread', 'deck plate')
    for (x, z) in ((3.6, 3.0), (3.6, 13.0), (12.4, 3.0), (12.4, 13.0), (3.6, 8.0), (12.4, 8.0)):
        m.bolt(x, 4.8, z, 'up', 0.35, BOLT)
    m.box((1.0, 2.2, 7.6), (1.6, 4.6, 8.4), 'paint:ral1023', 'check rod')
    m.g('junction')
    m.box((-0.2, 0.4, 7.6), (1.0, 0.9, 8.4), 'cable:e07a20', 'summing cable through wall')
    m.g('indicator cabinet')
    m.box((-3.4, 0, 5.8), (-0.4, 1.4, 10.2), 'concrete', 'cabinet footing')
    m.box((-3.0, 1.4, 6.2), (-0.6, 8.0, 9.8), 'ral7035', 'indicator cabinet')
    m.box((-3.2, 8.0, 6.0), (-0.4, 8.4, 10.0), 'ral7035', 'cabinet roof')
    # live (TileWayside): the weight on the bridge / of the last train. East face x -0.6 -> u = 16 - z, face = 16 - x
    ind = m.surface('east', face=16.6, title='SCALE')
    m.ctl(ind, 'digits', 'weight', 8.0, 6.7, w=2.8, h=1.4, col='ff3020', label='', fmt='%s')
    m.box((-0.6, 4.4, 6.6), (-0.5, 5.6, 9.4), 'plate', 'scale plate', east='plate:SCALE|TONS')
    m.box((-0.6, 2.2, 8.6), (-0.3, 3.8, 9.2), 'metal:brass', 'cabinet lock')
    m.box((-1.0, 0.4, 7.6), (-0.2, 1.4, 8.4), 'cable:e07a20', 'summing cable')
    m.box((-2.2, 8.4, 7.4), (-1.4, 8.8, 8.6), 'paint:#2a2c30', 'lamp base')
    m.box((-2.1, 8.8, 7.5), (-1.5, 9.6, 8.5), 'paint:#1e3a24', 'weigh complete lens')
    wc = m.surface('east', face=17.5, title='WEIGH COMPLETE')   # live: lit for 30 s after a train is weighed
    m.ctl(wc, 'lamp', 'done', 8.0, 9.2, w=1.0, h=0.8, col='30d050', label='')
    m.terminal_12v(-1.8, 1.4, 6.2, face='north')
    m.save(item_gui=0.5)


def defect_detector():
    """wayside defect detector bungalow (faces NORTH, track side): equipment case, hot-bearing / dragging-equipment
    scanners at the track edge, radio talker mast with yagi and whip, solar panel, battery box, warning lamp"""
    m = Model('defect_detector')
    m.g('foundation')
    pad(m, 1.0, 2.0, 15.0, 14.2, 1.2, 'concrete pad')
    for (x, z) in ((1.6, 2.6), (14.4, 2.6), (1.6, 13.6), (14.4, 13.6)):
        anchor(m, x, 1.2, z, 0.6)
    case(m, 2.6, 11.4, 3.6, 12.4, 1.4, 11.6, 'paint:#c8c2aa', doors=2, label='DEFECT|DETECTOR', label2=True, hinges=2, roof=0.8)
    m.box((2.4, 1.2, 3.4), (11.6, 1.4, 12.6), 'galv', 'case base frame')
    m.nameplate(7.0, 9.2, 10.4, 10.3, 3.26, 'MP 124.6', 'north')
    m.g('battery box')
    m.box((11.8, 1.2, 4.0), (14.6, 6.0, 9.6), 'paint:#c8c2aa', 'battery box')
    m.box((11.6, 6.0, 3.8), (14.8, 6.4, 9.8), 'paint:#c8c2aa', 'battery box lid')
    side_louvres(m, 'east', 14.6, 4.8, 8.8, 3.0, 5.4, n=3, mat='paint:#c8c2aa')
    m.box((12.4, 3.0, 3.8), (14.0, 4.6, 4.0), 'txt:101010,f0f0ea,BATT', 'battery label', faces=('north',))
    m.g('scanners at track edge')
    for x in (3.6, 12.4):
        m.box((x - 1.2, 0.0, 0.3), (x + 1.2, 1.6, 1.9), 'paint:#2a2c30', 'hot-bearing scanner')
        m.box((x - 0.5, 0.6, 0.2), (x + 0.5, 1.2, 0.3), 'glass', 'scanner window')
        m.box((x - 1.3, 1.6, 0.2), (x + 1.3, 1.9, 2.0), 'paint:#2a2c30', 'scanner hood')
    m.box((6.6, 0.0, 0.4), (9.4, 0.9, 1.6), 'paint:ral1023', 'dragging-equipment paddle')
    m.g('track cable')
    m.box((3.4, 0.0, 1.9), (3.8, 0.4, 3.6), 'cable:1d1d21', 'scanner cable')
    m.box((12.2, 0.0, 1.9), (12.6, 0.4, 3.6), 'cable:1d1d21', 'scanner cable')
    m.box((7.8, 0.0, 1.6), (8.2, 0.4, 3.6), 'cable:1d1d21', 'paddle cable')
    m.g('warning lamp')
    m.box((3.4, 12.4, 4.4), (5.2, 12.9, 6.2), 'paint:#2a2c30', 'lamp base')
    m.box((3.7, 12.9, 4.7), (4.9, 14.2, 5.9), 'paint:#4a3a18', 'amber warning lens')
    wl = m.surface('north', face=4.7, title='DEFECT ALARM')     # live: flashes for a minute after a defect report
    m.ctl(wl, 'lamp', 'warn', 16 - 4.3, 13.55, w=1.2, h=1.3, col='ffb020', label='', blink=True)
    m.box((3.6, 14.2, 4.6), (5.0, 14.5, 6.0), 'paint:#2a2c30', 'lamp cap')
    m.g('radio mast')
    m.box((9.8, 1.2, 12.6), (12.6, 1.6, 15.2), 'galv', 'mast base plate')
    for (x, z) in ((10.2, 13.0), (12.2, 13.0), (10.2, 14.8), (12.2, 14.8)):
        m.bolt(x, 1.6, z, 'up', 0.35, BOLT)
    m.cyl((11.2, 1.6, 13.9), 0.55, 25.6, 'y', 'galv', 'radio mast')
    for y in (6.0, 12.4):
        m.box((10.4, y, 12.4), (12.0, y + 0.6, 12.6), 'galv', 'mast stay bracket')
        m.box((10.6, y, 12.4), (11.8, y + 0.6, 13.4), 'galv', 'mast stay bracket')
    m.cyl((11.2, 27.2, 13.9), 0.2, 4.6, 'y', 'metal:steel', 'whip antenna')
    m.cyl((11.2, 26.6, 13.9), 0.35, 0.6, 'y', 'paint:#202224', 'whip base')
    m.box((10.9, 22.8, 8.6), (11.5, 23.4, 13.4), 'galv', 'yagi boom')
    for i, z in enumerate((9.0, 10.2, 11.4, 12.6)):
        m.box((11.12, 21.0 + i * 0.1, z - 0.1), (11.28, 25.2 - i * 0.1, z + 0.1), 'metal:alu', 'yagi element')
    m.box((11.0, 22.6, 13.3), (11.4, 23.6, 13.5), 'galv', 'boom clamp')
    m.box((11.75, 1.6, 13.8), (12.05, 22.8, 14.1), 'cable:1d1d21', 'coax feeder')
    m.box((11.75, 1.6, 12.4), (12.05, 2.0, 13.8), 'cable:1d1d21', 'coax into case')
    m.g('solar panel')
    m.box((13.5, 1.2, 10.8), (14.3, 15.6, 11.6), 'galv', 'solar post')
    m.box((11.4, 15.0, 9.0), (15.8, 15.4, 13.6), 'solar', 'solar panel', rot={'axis': 'x', 'angle': 22.5, 'origin': [13.6, 15.2, 11.2]})
    m.box((13.6, 6.0, 10.6), (13.9, 15.0, 10.8), 'cable:1d1d21', 'PV lead')
    m.terminal_12v(5.6, 1.6, 12.4, face='south')
    earth(m, 1.8, 1.4, 8.0)
    m.save(item_gui=0.45)


MODELS = [relay_case, track_circuit, insulated_joint, signal_post, crossing_post,
          gantry_frame, gantry_deck, gantry_rail, gantry_antenna, gantry_leg,
          whistle_post, milepost, speed_sign, switch_stand, derail, bumper, crossbuck,
          lambda: lineside_cabinet('cabinet_small', 4.5, 11.5, 5.5, 10.5, 8.2, 1, 'LSB 7', 0.7),
          lambda: lineside_cabinet('cabinet', 3.5, 12.5, 4.5, 11.5, 14.4, 2, 'APPARATUS|CASE'),
          lambda: lineside_cabinet('cabinet_tall', 3.0, 13.0, 4.0, 12.0, 24.4, 2, 'LOC CASE|S14', 0.45),
          cable_drum, bollard, marker_post, stop_board, limit_disc, blank_board, id_plate,
          substation, charging_station, aei_reader, track_scale, defect_detector]

if __name__ == '__main__':
    for f in MODELS:
        f()
