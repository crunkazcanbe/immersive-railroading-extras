#!/usr/bin/env python3
"""IR Extras power grid machines, part 3: generators, controls and HV switchgear, modelled in detail with bbkit
(her ask 2026-10-09: "make everything in Blockbench and make it as complex as you physically can and make it wire up.
Low voltage stuff gets the little 12 volt wire, high voltage gets the other wires ... like real life").

  grid_diesel_0/1/2   diesel genset           (stopped / running / fault)
  grid_turbine_0/1/2  steam turbine-generator (stopped / running / tripped)  - the game's TURBINE runs on steam
  grid_solar_0/1/2    single-axis tracker row: piles, bearings, slew drive, combiner + DC isolator (deck = SolarDeck TESR)
  grid_wind_0/1/2     wind turbine tower foot: foundation, base flange, door + steps, base controller, cable box
                      (the nacelle + rotor are drawn by WindRotor on top of the stacked tower; the model stays <= 16 high)
  wind_tower          one tower section (tube r = 5 like BlockWindTower's box)
  grid_scada_0/1      SCADA control desk          grid_auto_0/1      PLC automation cabinet
  grid_earthing_0/1   neutral earthing switch     grid_relay_0/1     protection relay cubicle
  grid_isolator_0/1   3-pole disconnector (closed / open)            grid_arrester_0/1  surge arrester

Front = north (z = 0). Each machine's live control panel is drawn by GridPanelRenderer over Panel.java's rectangle
(x = 16 - u, y = v) standing in front of z = Panel.face; this file keeps that rectangle (and the door that swings over
it) free and gives it a flat surface to sit on. Run from tools/:  python3 gen_bb_grid3.py
"""
import math
import sys
from bbkit import Model
from gen_bb_grid2 import live, beacon_live

STEEL, GALV, CU = 'metal:steel', 'galv', 'metal:copper'
GENSET = 'paint:#2f3a33'
ENGINE = 'paint:#2e5a3c'
HOT = 'paint:#7a5f48'          # heat-blued / rusted exhaust iron
LAG = 'metal:alu'              # aluminium cladding over lagging
BLACK_CABLE, RED_CABLE = 'cable:1d1d21', 'cable:c81e1e'
EARTH_CABLE = 'cable:3a8a3a'


# ------------------------------------------------------------------------------------------------ shape helpers
def oct_(m, c, r, h, axis, mat, name=None, cap=None):
    """true octagonal prism (4 boxes: two crosses, one turned 45) - for big round parts where a star would show"""
    i = 'xyz'.index(axis)
    o = [k for k in range(3) if k != i]
    b = r * 0.4142
    kw = {e: cap for e in {0: ('east', 'west'), 1: ('up', 'down'), 2: ('north', 'south')}[i]} if cap else {}
    origin = [c[k] if k != i else c[k] + h / 2 for k in range(3)]
    for rot in (None, {'axis': axis, 'angle': 45, 'origin': origin}):
        for (ha, hb) in ((r, b), (b, r)):
            a_, b_ = list(c), list(c)
            a_[i], b_[i] = c[i], c[i] + h
            a_[o[0]], b_[o[0]] = c[o[0]] - ha, c[o[0]] + ha
            a_[o[1]], b_[o[1]] = c[o[1]] - hb, c[o[1]] + hb
            m.box(a_, b_, mat, name, rot=rot, **kw)


def tube16(m, c, r, h, mat, name=None, cap=None):
    """16-sided vertical tube (8 boxes) - wind tower shell"""
    b = r * 0.1989
    kw = {'up': cap, 'down': cap} if cap else {}
    origin = [c[0], c[1] + h / 2, c[2]]
    for ang in (0, 22.5, 45, -22.5):
        rot = {'axis': 'y', 'angle': ang, 'origin': origin} if ang else None
        for (ha, hb) in ((r, b), (b, r)):
            m.box((c[0] - ha, c[1], c[2] - hb), (c[0] + ha, c[1] + h, c[2] + hb), mat, name, rot=rot, **kw)


def seg(m, a, b, r, mat, name=None):
    """a straight round run between two points that differ on one axis (single box when thin)"""
    i = [k for k in range(3) if abs(a[k] - b[k]) > 1e-6]
    if not i: return
    i = i[0]
    lo = list(a) if a[i] < b[i] else list(b)
    L = abs(a[i] - b[i])
    if r <= 0.3:
        p, q = list(lo), list(lo)
        for k in range(3):
            if k == i: q[k] = lo[k] + L
            else: p[k], q[k] = lo[k] - r, lo[k] + r
        m.box(p, q, mat, name)
    else:
        m.cyl(lo, r, L, 'xyz'[i], mat, name)


def path(m, pts, r, mat, name=None, elbow=None):
    """a pipe / cable through axis-aligned way points, with elbow blocks at the corners"""
    for a, b in zip(pts, pts[1:]): seg(m, a, b, r, mat, name)
    for p in pts[1:-1]:
        e = r * 1.15
        m.box((p[0] - e, p[1] - e, p[2] - e), (p[0] + e, p[1] + e, p[2] + e), elbow or mat, (name or 'pipe') + ' bend')


def flange(m, c, r, axis, mat=STEEL, t=0.3, bolts=4):
    """pipe flange with bolt heads"""
    m.cyl(c, r, t, axis, mat, 'flange')
    i = 'xyz'.index(axis)
    o = [k for k in range(3) if k != i]
    for n in range(bolts):
        a = math.tau * (n + 0.5) / bolts
        p = list(c)
        p[o[0]] += math.cos(a) * r * 0.72
        p[o[1]] += math.sin(a) * r * 0.72
        lo = [p[k] - 0.15 for k in range(3)]
        hi = [p[k] + 0.15 for k in range(3)]
        lo[i], hi[i] = c[i] - 0.12, c[i] + t + 0.12
        m.box(lo, hi, 'metal:steel', 'flange bolt')


def sign(m, face, a, b, at, spec, t=0.12):
    """a flat sign on any face: a/b = the two in-plane corners (x,y) for north/south, (z,y) for east/west, (x,z) up"""
    if face in ('north', 'south'):
        z0, z1 = (at - t, at) if face == 'north' else (at, at + t)
        m.box((a[0], a[1], z0), (b[0], b[1], z1), 'paint:#d8d8d0', 'sign', **{face: spec})
    elif face in ('east', 'west'):
        x0, x1 = (at, at + t) if face == 'east' else (at - t, at)
        m.box((x0, a[1], a[0]), (x1, b[1], b[0]), 'paint:#d8d8d0', 'sign', **{face: spec})
    else:
        m.box((a[0], at, a[1]), (b[0], at + t, b[1]), 'paint:#d8d8d0', 'sign', up=spec)


def hbeam(m, x0, x1, y0, y1, z0, z1, mat=GALV, along='y', flanges='z'):
    """an H / I section: 'along' = length axis, 'flanges' = axis the flange faces point along"""
    if along == 'y' and flanges == 'z':
        t = 0.25
        m.box((x0, y0, z0), (x1, y1, z0 + t), mat, 'H flange')
        m.box((x0, y0, z1 - t), (x1, y1, z1), mat, 'H flange')
        xm = (x0 + x1) / 2
        m.box((xm - 0.13, y0, z0 + t), (xm + 0.13, y1, z1 - t), mat, 'H web')
    elif along == 'y' and flanges == 'x':
        t = 0.25
        m.box((x0, y0, z0), (x0 + t, y1, z1), mat, 'H flange')
        m.box((x1 - t, y0, z0), (x1, y1, z1), mat, 'H flange')
        zm = (z0 + z1) / 2
        m.box((x0 + t, y0, zm - 0.13), (x1 - t, y1, zm + 0.13), mat, 'H web')


def earth_rod(m, x, z, y=0.0):
    """earth electrode head in its pit: copper rod top and a bolted clamp"""
    m.box((x - 0.9, y, z - 0.9), (x + 0.9, y + 0.25, z + 0.9), 'concrete', 'earth pit cover rim')
    m.cyl((x, y, z), 0.35, 0.9, 'y', CU, 'earth rod')
    m.box((x - 0.45, y + 0.45, z - 0.3), (x + 0.45, y + 0.8, z + 0.3), 'metal:brass', 'rod clamp')


# ------------------------------------------------------------------------------------------------ checks
def _bounds(lo, hi, rot):
    if not rot: return lo, hi
    i = 'xyz'.index(rot['axis'])
    o = [k for k in range(3) if k != i]
    a = math.radians(rot['angle'])
    pts = []
    for cx in (lo[0], hi[0]):
        for cy in (lo[1], hi[1]):
            for cz in (lo[2], hi[2]):
                p = [cx, cy, cz]
                u, v = p[o[0]] - rot['origin'][o[0]], p[o[1]] - rot['origin'][o[1]]
                p[o[0]] = rot['origin'][o[0]] + u * math.cos(a) - v * math.sin(a)
                p[o[1]] = rot['origin'][o[1]] + u * math.sin(a) + v * math.cos(a)
                pts.append(p)
    return [min(p[k] for p in pts) for k in range(3)], [max(p[k] for p in pts) for k in range(3)]


def check_panel(m, u0, v0, u1, v1, face):
    """nothing may stand in front of the live panel or in the door's way; something must back it"""
    back = 0.0 if face < 0 else face + 0.2
    bad = []
    for nm, lo, hi, rot, fm, grp in m.boxes:
        a, b = _bounds(lo, hi, rot)
        # the panel itself (any depth in front of its back), and the door leaf (0.3 bigger, 1.6..2.1 in front of the face)
        for (mg, z0, z1) in ((0.0, -99, back - 0.02), (0.3, face - 2.2, face - 1.5)):
            if a[0] < 16 - u0 + mg - 0.01 and b[0] > 16 - u1 - mg + 0.01 and a[1] < v1 + mg - 0.01 and b[1] > v0 - mg + 0.01 \
                    and a[2] < z1 and b[2] > z0:
                bad.append((nm, [round(v, 2) for v in a], [round(v, 2) for v in b]))
                break
    if bad: raise SystemExit(f'{m.name}: in front of the live panel: {bad[:6]}')
    fronts = [_bounds(lo, hi, rot) for nm, lo, hi, rot, fm, grp in m.boxes]
    fronts = [(a, b) for a, b in fronts if abs(a[2] - back) < 0.35]
    for i in range(9):
        for j in range(9):
            x, y = 16 - u1 + (u1 - u0) * (i + 0.5) / 9, v0 + (v1 - v0) * (j + 0.5) / 9
            if not any(a[0] <= x <= b[0] and a[1] <= y <= b[1] for a, b in fronts):
                raise SystemExit(f'{m.name}: nothing behind the live panel at x={x:.1f} y={y:.1f} z={back}')


def check_deck(m):
    """SolarDeck turns a 15.6 x 15.6 deck (+ purlins + torque tube) about (8, 10.8) through +-60 deg"""
    def hit(a, b):
        cx, cy = 8.0, 10.8
        purlin = (a[2] < 1.85 and b[2] > 0.95) or (a[2] < 15.05 and b[2] > 14.15)   # purlins hang under the deck ends
        for deg in range(-60, 61, 3):
            t = math.radians(deg)
            ca, sa = math.cos(t), math.sin(t)
            for (u0, u1, w0, w1) in ((-7.9, 7.9, -1.05 if purlin else -0.65, 0.05), (-0.85, 0.85, -1.65, -0.55)):
                # SAT: axis-aligned rect [a,b] vs rotated rect u in [u0,u1], w in [w0,w1]
                corners = [(px - cx, py - cy) for px in (a[0], b[0]) for py in (a[1], b[1])]
                us = [x * ca + y * sa for x, y in corners]
                ws = [-x * sa + y * ca for x, y in corners]
                if max(us) < u0 or min(us) > u1 or max(ws) < w0 or min(ws) > w1: continue
                rc = [(u * ca - w * sa + cx, u * sa + w * ca + cy) for u in (u0, u1) for w in (w0, w1)]
                if max(p[0] for p in rc) < a[0] or min(p[0] for p in rc) > b[0]: continue
                if max(p[1] for p in rc) < a[1] or min(p[1] for p in rc) > b[1]: continue
                return deg
        return None
    bad = []
    for nm, lo, hi, rot, fm, grp in m.boxes:
        a, b = _bounds(lo, hi, rot)
        if b[2] <= 0.2 or a[2] >= 15.8: continue
        d = hit(a, b)
        if d is not None: bad.append((nm, d, [round(v, 2) for v in a], [round(v, 2) for v in b]))
    if bad: raise SystemExit(f'{m.name}: in the tracker deck swing: {bad[:6]}')


def check_height(m, top):
    for nm, lo, hi, rot, fm, grp in m.boxes:
        a, b = _bounds(lo, hi, rot)
        if b[1] > top + 0.01: raise SystemExit(f'{m.name}: {nm} reaches y={b[1]:.2f} (max {top})')


# ================================================================================================ GENSETS
def base_frame(m, fuel_label):
    """fabricated skid: C-channel rails, cross members, fork pockets, belly tank, lifting lugs, earth boss"""
    m.g('base frame')
    for x0, x1, web in ((0.4, 1.8, (0.4, 0.7)), (14.2, 15.6, (15.3, 15.6))):
        m.box((web[0], 0, 0.2), (web[1], 1.8, 15.8), 'dark', 'rail web')
        m.box((x0, 0, 0.2), (x1, 0.3, 15.8), 'dark', 'rail flange')
        m.box((x0, 1.5, 0.2), (x1, 1.8, 15.8), 'dark', 'rail flange')
        for z in (0.2, 15.5):
            m.box((x0, 0, z), (x1, 1.8, z + 0.3), 'dark', 'end plate')
    for z in (0.5, 7.0, 15.0):
        m.box((1.8, 0.2, z), (14.2, 1.6, z + 0.5), 'dark', 'cross member')
    for z in (4.6, 10.4):
        m.box((0.2, 0.3, z), (15.8, 1.4, z + 1.4), 'dark', 'fork pocket', west='paint:#0a0a0a', east='paint:#0a0a0a')
    for (x, z) in ((0.6, 1.2), (15.0, 1.2), (0.6, 14.4), (15.0, 14.4)):
        m.box((x - 0.2, 1.8, z - 0.5), (x + 0.2, 2.6, z + 0.5), 'paint:ral1023', 'lifting lug')
        m.box((x - 0.25, 2.0, z - 0.15), (x + 0.25, 2.3, z + 0.15), 'paint:#101010', 'lug eye')
    m.box((0.2, 0.6, 2.0), (0.4, 1.3, 4.0), 'haz', 'skid stripe')
    m.box((15.6, 0.6, 2.0), (15.8, 1.3, 4.0), 'haz', 'skid stripe')
    m.g('belly tank')
    m.box((1.8, 0.25, 1.2), (14.2, 1.5, 14.8), GENSET, 'belly tank')
    m.box((12.6, 1.5, 13.2), (13.8, 1.8, 14.4), GENSET, 'filler neck boss')
    m.cyl((13.2, 1.8, 13.8), 0.45, 1.0, 'y', STEEL, 'filler neck')
    m.cyl((13.2, 2.8, 13.8), 0.6, 0.35, 'y', 'paint:ral1023', 'filler cap')
    sign(m, 'up', (11.2, 13.3), (12.4, 14.4), 1.5, 'label:' + fuel_label)
    m.box((4.0, 1.5, 13.4), (5.2, 1.62, 14.6), 'paint:dark', 'tank level gauge boss')
    live(m, 'up', 4.6, 1.62, 14.0, 'gauge', 'fuel' if fuel_label != 'LUBE OIL' else 'lube', 1.0, 1.0, 'f2f0e6', '', max=100)
    path(m, [(2.4, 1.5, 14.2), (2.4, 4.6, 14.2), (2.4, 4.6, 14.8)], 0.15, STEEL, 'tank vent')
    m.box((2.2, 4.4, 14.8), (2.6, 4.8, 15.1), 'mesh', 'vent breather')
    m.cyl((7.6, 0.0, 14.0), 0.3, 0.25, 'y', STEEL, 'drain plug')
    m.g('earthing')
    m.box((0.0, 0.0, 14.6), (0.4, 1.6, 15.0), CU, 'earth strap')
    m.box((0.0, 0.0, 14.6), (0.4, 0.1, 16.0), CU, 'earth strap to grid')
    m.bolt(0.0, 1.2, 14.8, 'west', 0.35)
    m.box((-0.1, 1.6, 14.5), (0.4, 2.2, 15.1), 'paint:#3a8a3a', 'earth label')


def control_cubicle(m, st, title):
    """the genset controller cubicle at the front: the live panel (Panel DIESEL/TURBINE: u 1.6..14.4, v 3.6..15.6)
    stands on its face; louvred sides, drip roof, beacon, cable gland plate and harness conduit underneath"""
    m.g('control cubicle')
    for x in (1.4, 13.8):
        m.box((x, 1.8, 0.3), (x + 0.8, 3.2, 2.0), 'dark', 'cubicle stand')
        m.box((x - 0.1, 1.8, 0.2), (x + 0.9, 1.95, 2.1), 'rubber', 'anti-vibration pad')
    m.box((1.2, 3.2, 0), (14.8, 16.0, 2.2), GENSET, 'controller cubicle')
    m.box((1.0, 16.0, -0.4), (15.0, 16.35, 2.4), GENSET, 'drip roof')
    for x in (1.0, 14.8):
        m.box((x, 5.0, 0.4), (x + 0.2, 8.4, 1.8), 'louvre', 'side louvre')
        m.box((x, 10.4, 0.4), (x + 0.2, 13.8, 1.8), 'louvre', 'side louvre')
    sign(m, 'west', (0.3, 9.0), (1.9, 10.2), 1.0, 'danger:DANGER|400 V')
    sign(m, 'east', (0.3, 14.0), (1.9, 15.6), 15.0, 'plate:' + title)
    m.box((3.0, 2.9, 0.4), (13.0, 3.2, 1.8), 'dark', 'gland plate')
    for x in (4.2, 5.4, 6.6, 9.4, 10.6, 11.8):
        m.cyl((x, 2.5, 1.1), 0.3, 0.4, 'y', 'metal:brass', 'cable gland')
    # control harness: down from the gland plate, along the skid to the engine and alternator
    path(m, [(5.4, 2.5, 1.1), (5.4, 2.05, 1.1), (5.4, 2.05, 7.6)], 0.22, 'cable:2a2a2a', 'control harness')
    path(m, [(10.6, 2.5, 1.1), (10.6, 2.05, 1.1), (10.6, 2.05, 9.4)], 0.22, 'cable:2a2a2a', 'sensor harness')
    m.g('beacon')
    beacon_live(m, 12.6, 16.35, 1.0)
    m.cyl((3.4, 16.35, 1.0), 0.3, 1.6, 'y', 'black', 'GSM antenna')


def hv_output(m, from_pts):
    """the generator's main output: cable box on the west side, cables from the machine terminal box into it"""
    m.hv_box(1.0, 2.6, 5.8, 'west', phases=3)
    m.g('main output cables')
    for i, z in enumerate((4.2, 5.0, 5.8, 6.6)):
        col = BLACK_CABLE if i < 3 else 'cable:2a4aa0'
        x, y, z0 = from_pts
        path(m, [(x, y, z0 + (z - 5.4) * 0.6), (x, y, z), (0.0, y, z), (0.0, 6.7, z)], 0.3, col, 'output cable' if i < 3 else 'neutral cable', 'dark')
    path(m, [(5.0, 2.6, 2.6), (5.0, 2.2, 2.6), (-0.1, 2.2, 2.6), (-0.1, 0.0, 2.6)], 0.2, EARTH_CABLE, 'earth cable')
    m.g('12 V terminal')
    m.box((15.2, 1.8, 8.2), (15.6, 5.2, 10.8), 'dark', 'terminal bracket')
    m.terminal_12v(15.6, 2.5, 9.5, face='east')


def fan_and_radiator(m, st, cool_label):
    m.g('fan')
    fc = (8.0, 8.2)
    m.cyl((fc[0], fc[1], 12.2), 0.9, 0.9, 'z', 'dark', 'fan hub')
    if st == 1:
        oct_(m, (fc[0], fc[1], 12.75), 4.9, 0.12, 'z', 'paint:#6a7076', 'fan blades (spinning)')
        oct_(m, (fc[0], fc[1], 12.7), 3.2, 0.12, 'z', 'paint:#5a6066', 'fan blur inner')
    else:
        for ang in (0, 45, -45):
            m.box((7.55, 3.4, 12.65), (8.45, 13.0, 12.85), 'paint:#3c4248', 'fan blade',
                  rot={'axis': 'z', 'angle': ang, 'origin': [fc[0], fc[1], 12.75]} if ang else None)
        m.box((3.2, 7.75, 12.65), (12.8, 8.65, 12.85), 'paint:#3c4248', 'fan blade')
    m.g('fan shroud')
    m.box((2.6, 13.3, 12.6), (13.4, 13.7, 13.6), 'dark', 'shroud top')
    m.box((2.6, 2.6, 12.6), (13.4, 3.0, 13.6), 'dark', 'shroud bottom')
    m.box((2.6, 3.0, 12.6), (3.0, 13.3, 13.6), 'dark', 'shroud side')
    m.box((13.0, 3.0, 12.6), (13.4, 13.3, 13.6), 'dark', 'shroud side')
    for ang in (45, -45):
        m.box((7.9, 3.4, 12.45), (8.1, 13.0, 12.55), STEEL, 'fan guard wire', rot={'axis': 'z', 'angle': ang, 'origin': [8, 8.2, 12.5]})
    m.box((3.2, 8.1, 12.45), (12.8, 8.3, 12.55), STEEL, 'fan guard wire')
    m.box((7.9, 3.2, 12.45), (8.1, 13.2, 12.55), STEEL, 'fan guard wire')
    m.g('radiator')
    m.box((1.6, 2.6, 13.6), (14.4, 13.6, 14.6), 'fins', 'radiator core', up='dark', down='dark')
    m.box((1.4, 13.6, 13.4), (14.6, 14.8, 14.8), 'dark', 'top tank')
    m.box((1.4, 1.8, 13.4), (14.6, 2.6, 14.8), 'dark', 'bottom tank')
    m.box((1.2, 1.8, 13.4), (1.6, 14.8, 14.8), 'dark', 'radiator side')
    m.box((14.4, 1.8, 13.4), (14.8, 14.8, 14.8), 'dark', 'radiator side')
    m.cyl((11.4, 14.8, 14.1), 0.45, 0.4, 'y', 'metal:brass', 'pressure cap')
    m.box((14.8, 11.0, 13.8), (14.95, 12.6, 14.4), 'glass', 'coolant sight glass', east='glass')
    sign(m, 'up', (12.2, 13.5), (14.2, 14.7), 14.8, 'warn')
    sign(m, 'south', (5.0, 14.9), (11.0, 15.6), 14.8, 'label:' + cool_label)
    m.cyl((13.6, 1.0, 14.2), 0.25, 0.8, 'y', 'metal:brass', 'radiator drain cock')
    for y in (3.6, 5.0, 6.4, 7.8, 9.2, 10.6, 12.0):
        m.box((1.4, y, 15.2), (14.6, y + 0.2, 15.4), STEEL, 'rear guard bar')
    for x in (1.4, 14.4):
        m.box((x, 1.8, 14.8), (x + 0.2, 13.6, 15.4), STEEL, 'guard frame')


def enclosure(m):
    """acoustic canopy parts: corner posts, roof with lifting eyes, louvred lower skirts"""
    m.g('enclosure')
    for (x0, z0) in ((0.0, 2.4), (15.2, 2.4), (0.0, 15.2), (15.2, 15.2)):
        m.box((x0, 1.8, z0), (x0 + 0.8, 16.0, z0 + 0.8), GENSET, 'canopy post')
    m.box((0.0, 16.0, 2.4), (16.0, 16.5, 6.2), GENSET, 'canopy roof (alternator end)')
    m.box((0.0, 16.0, 12.8), (16.0, 16.5, 16.0), GENSET, 'canopy roof (radiator end)')
    m.box((0.0, 16.0, 6.2), (16.0, 16.5, 6.8), GENSET, 'roof beam')
    m.box((0.0, 16.0, 12.2), (16.0, 16.5, 12.8), GENSET, 'roof beam')
    m.box((1.8, 16.0, 6.8), (2.4, 16.5, 12.2), GENSET, 'silencer rail')
    m.box((4.8, 16.0, 6.8), (5.4, 16.5, 12.2), GENSET, 'silencer rail')
    m.box((0.0, 16.5, 2.4), (16.0, 16.65, 2.7), GENSET, 'roof rain lip')
    m.box((12.8, 16.5, 13.2), (15.6, 17.1, 15.6), 'louvre', 'radiator air discharge hood')
    for (x, z) in ((1.2, 3.6), (14.8, 3.6), (1.2, 14.8), (14.8, 14.8)):
        m.box((x - 0.4, 16.5, z - 0.12), (x + 0.4, 17.3, z + 0.12), 'paint:ral1023', 'roof lifting eye')
    for x0 in (0.0, 15.6):
        m.box((x0, 1.8, 7.6), (x0 + 0.4, 3.4, 13.0), 'louvre', 'skirt air inlet louvre')
    m.box((0.0, 15.4, 3.2), (0.8, 16.0, 15.2), GENSET, 'canopy eaves beam')
    m.box((15.2, 15.4, 3.2), (16.0, 16.0, 15.2), GENSET, 'canopy eaves beam')


def diesel(st):
    m = Model('grid_diesel_%d' % st, block='grid_diesel')
    base_frame(m, 'DIESEL')
    control_cubicle(m, st, 'STANDBY 500 KVA|400 V 3PH')

    m.g('alternator')
    ac = (8.0, 5.8)
    oct_(m, (ac[0], ac[1], 2.4), 3.3, 4.6, 'z', 'ral7016', 'alternator frame', cap='mesh')
    for z in (3.2, 4.4, 5.6):
        oct_(m, (ac[0], ac[1], z), 3.4, 0.2, 'z', 'ral7016', 'frame rib')
    oct_(m, (ac[0], ac[1], 6.4), 3.45, 0.6, 'z', 'louvre', 'air outlet screen', cap='dark')
    for x in (3.6, 11.0):
        m.box((x, 1.8, 3.0), (x + 1.4, 2.6, 6.6), 'ral7016', 'alternator foot')
        m.box((x - 0.1, 1.8, 2.8), (x + 1.5, 1.95, 6.8), 'rubber', 'AV mount')
        m.bolt(x + 0.7, 2.6, 3.4, 'up', 0.35)
        m.bolt(x + 0.7, 2.6, 6.2, 'up', 0.35)
    m.box((7.6, 9.0, 5.2), (8.4, 9.9, 5.6), 'paint:ral1023', 'alternator lifting eye')
    m.g('alternator terminal box')
    m.box((5.4, 8.8, 3.0), (10.6, 11.2, 6.2), 'ral7016', 'terminal box')
    m.box((5.2, 11.2, 2.8), (10.8, 11.5, 6.4), 'ral7016', 'terminal box lid')
    for x in (5.5, 8.0, 10.5):
        for z in (3.0, 6.2):
            m.bolt(x, 11.5, z, 'up', 0.3)
    sign(m, 'east', (3.4, 9.2), (5.8, 10.8), 10.6, 'plate:ALTERNATOR|500 KVA 0.8 PF')
    m.box((10.6, 9.4, 3.4), (10.75, 10.6, 3.9), 'warn', 'shock sign', east='warn')

    m.g('flywheel housing')
    oct_(m, (8.0, 5.8, 7.0), 3.0, 0.8, 'z', 'dark', 'SAE bell housing')
    m.g('starter')
    m.cyl((4.4, 3.6, 7.0), 0.7, 1.8, 'z', 'black', 'starter motor')
    m.cyl((4.4, 4.5, 7.2), 0.4, 1.3, 'z', 'black', 'starter solenoid')

    m.g('engine block')
    m.box((4.6, 2.8, 7.8), (11.4, 8.4, 12.2), ENGINE, 'crankcase')
    m.box((5.2, 1.8, 8.0), (10.8, 2.8, 12.0), ENGINE, 'oil sump')
    m.cyl((7.0, 1.7, 11.0), 0.25, 0.2, 'y', STEEL, 'sump drain')
    for x0, x1 in ((3.2, 4.6), (11.4, 12.8)):
        for z in (8.4, 11.2):
            m.box((x0, 2.0, z), (x1, 2.8, z + 0.7), ENGINE, 'engine foot')
            m.box((x0, 1.8, z), (x1, 2.0, z + 0.7), 'rubber', 'engine AV mount')
    for z in (8.4, 11.2):
        m.box((1.8, 1.4, z), (3.2, 1.8, z + 0.7), 'dark', 'engine bearer')
        m.box((12.8, 1.4, z), (14.2, 1.8, z + 0.7), 'dark', 'engine bearer')
    for z in (8.6, 9.6, 10.6, 11.6):
        m.box((4.45, 3.4, z), (4.6, 7.8, z + 0.25), ENGINE, 'block rib')
        m.box((11.4, 3.4, z), (11.55, 7.8, z + 0.25), ENGINE, 'block rib')
    m.g('cylinder heads')
    for i in range(6):
        z = 7.9 + i * 0.72
        m.box((5.0, 8.4, z), (11.0, 9.3, z + 0.66), ENGINE, 'cylinder head %d' % (i + 1))
        m.box((5.5, 9.3, z + 0.05), (10.5, 9.95, z + 0.61), 'metal:alu', 'rocker cover %d' % (i + 1))
        for x in (5.7, 10.3):
            m.bolt(x, 9.95, z + 0.33, 'up', 0.25)
        seg(m, (11.3, 7.9, z + 0.33), (11.3, 9.0, z + 0.33), 0.09, STEEL, 'injector pipe')
        m.box((11.0, 8.9, z + 0.25), (11.4, 9.1, z + 0.41), STEEL, 'injector pipe')
    m.cyl((7.0, 9.95, 9.0), 0.3, 0.35, 'y', 'paint:ral1023', 'oil filler cap')
    seg(m, (4.8, 3.0, 9.4), (4.8, 6.6, 9.4), 0.08, STEEL, 'dipstick tube')
    m.box((4.65, 6.6, 9.25), (4.95, 7.0, 9.55), 'paint:ral1023', 'dipstick handle')
    m.g('fuel system')
    m.box((11.4, 6.0, 8.2), (12.6, 7.9, 11.8), 'dark', 'injection pump')
    for z in (9.0, 10.2):
        m.cyl((12.2, 3.6, z), 0.45, 1.8, 'y', 'paint:#e8e4d8', 'fuel filter')
        m.box((11.7, 5.4, z - 0.5), (12.7, 5.8, z + 0.5), 'dark', 'filter head')
    m.cyl((12.0, 3.4, 11.4), 0.55, 2.0, 'y', 'paint:#d0d0c8', 'oil filter')
    path(m, [(12.2, 1.5, 9.0), (12.2, 3.6, 9.0)], 0.12, CU, 'fuel supply line')
    path(m, [(12.6, 1.5, 7.6), (12.6, 6.4, 7.6), (12.6, 6.4, 8.2)], 0.12, CU, 'fuel return line')
    m.cyl((14.4, 2.6, 7.6), 0.35, 1.6, 'x', 'metal:brass', 'fuel inlet')
    flange(m, (15.6, 2.6, 7.6), 0.65, 'x', 'metal:brass', 0.25, 4)
    path(m, [(14.4, 2.6, 7.6), (13.0, 2.6, 7.6), (13.0, 1.5, 7.6)], 0.15, CU, 'fuel inlet line')
    m.g('intake')
    m.box((11.0, 8.6, 7.9), (12.2, 9.6, 12.2), 'metal:alu', 'intake manifold')
    for i in range(6):
        z = 8.0 + i * 0.72
        m.box((10.7, 8.8, z + 0.15), (11.0, 9.4, z + 0.5), 'metal:alu', 'intake runner')
    oct_(m, (8.0, 13.2, 6.6), 1.3, 1.6, 'z', 'paint:#1d1d21', 'air cleaner', cap='dark')
    m.box((7.7, 14.4, 7.2), (8.3, 15.1, 7.6), 'paint:#30c040', 'restriction indicator')
    m.cyl((8.0, 13.2, 6.3), 0.8, 0.3, 'z', 'rubber', 'air cleaner cap')
    path(m, [(6.7, 13.2, 7.4), (3.2, 13.2, 7.4), (3.2, 10.8, 7.4), (3.2, 10.8, 8.0)], 0.5, 'rubber', 'air inlet hose', 'rubber')
    m.g('turbocharger')
    m.cyl((3.2, 10.8, 8.0), 1.0, 0.8, 'z', 'metal:alu', 'compressor housing')
    m.box((2.4, 9.6, 8.8), (4.0, 11.6, 10.2), HOT, 'turbine housing')
    m.cyl((3.2, 10.8, 8.75), 0.45, 0.15, 'z', 'dark', 'centre housing')
    path(m, [(3.2, 11.6, 8.4), (3.2, 14.9, 8.4), (3.2, 14.9, 13.9), (3.2, 14.4, 13.9)], 0.45, 'metal:alu', 'charge air pipe', 'rubber')
    path(m, [(12.8, 14.2, 13.4), (12.8, 14.2, 11.4), (12.8, 9.1, 11.4), (12.2, 9.1, 11.4)], 0.45, 'metal:alu', 'charge air return', 'rubber')
    m.g('exhaust manifold')
    for i in range(6):
        z = 8.0 + i * 0.72
        m.box((4.2, 8.6, z + 0.1), (5.0, 9.2, z + 0.55), HOT, 'exhaust runner')
    m.cyl((3.6, 8.9, 7.9), 0.55, 4.3, 'z', HOT, 'exhaust collector')
    m.box((3.0, 8.9, 9.2), (4.2, 9.7, 9.9), HOT, 'turbine inlet')
    m.box((3.0, 8.2, 7.9), (4.2, 9.6, 12.2), 'paint:#8a8c8e', 'heat shield', faces=('west',))
    m.g('exhaust')
    for k in range(5):
        m.cyl((3.2, 11.6 + k * 0.5, 9.5), 0.62 if k % 2 == 0 else 0.5, 0.5, 'y', 'metal:steel', 'exhaust bellows')
    seg(m, (3.2, 14.1, 9.5), (3.2, 16.8, 9.5), 0.55, HOT, 'exhaust riser')
    m.cyl((3.2, 16.5, 9.5), 0.85, 0.3, 'y', GENSET, 'roof flashing collar')
    oct_(m, (3.6, 18.4, 5.6), 1.6, 7.0, 'z', 'paint:#4a4c4e', 'roof silencer', cap='dark')
    for z in (5.8, 12.2):
        oct_(m, (3.6, 18.4, z), 1.7, 0.2, 'z', 'paint:#3a3c3e', 'silencer end band')
    for z in (6.6, 11.6):
        m.box((2.0, 16.5, z), (5.2, 17.0, z + 0.5), 'dark', 'silencer saddle')
    seg(m, (3.2, 16.8, 9.5), (3.2, 17.4, 9.5), 0.55, HOT, 'silencer inlet')
    path(m, [(3.6, 18.4, 12.6), (3.6, 18.4, 13.4), (3.6, 23.2, 13.4)], 0.6, HOT, 'tail pipe')
    m.cyl((3.6, 22.4, 13.4), 0.75, 0.25, 'y', STEEL, 'tail pipe band')
    m.box((3.1, 23.3, 12.75), (4.1, 23.4, 12.95), STEEL, 'rain flap hinge')
    if st == 1:
        m.box((2.8, 23.3, 12.8), (4.4, 23.45, 14.4), 'paint:#3a3a3a', 'rain flap (open)', rot={'axis': 'x', 'angle': -45, 'origin': [3.6, 23.35, 12.8]})
        m.box((3.1, 23.0, 13.0), (4.1, 23.21, 13.8), 'paint:#1a1a1a', 'soot')
    else:
        m.box((2.8, 23.2, 12.6), (4.4, 23.35, 14.2), 'paint:#3a3a3a', 'rain flap (shut)')
    sign(m, 'west', (6.0, 17.4), (7.6, 19.0), 1.9, 'warn')

    m.g('battery')
    m.box((12.6, 1.8, 2.4), (15.2, 2.0, 7.2), 'dark', 'battery tray')
    for z0 in (2.6, 4.9):
        m.box((12.8, 2.0, z0), (15.0, 3.8, z0 + 2.1), 'black', '12 V starter battery', up='paint:#2a2a2a', east='label:12V 180AH')
        m.cyl((13.3, 3.8, z0 + 0.4), 0.22, 0.3, 'y', 'paint:ral3000', 'battery + post')
        m.cyl((14.5, 3.8, z0 + 1.7), 0.22, 0.3, 'y', 'paint:#202020', 'battery - post')
    m.box((12.7, 3.8, 4.7), (15.1, 4.0, 4.9), STEEL, 'hold-down bar')
    path(m, [(13.3, 4.1, 3.0), (13.3, 4.6, 3.0), (13.3, 4.6, 6.4), (5.1, 4.6, 6.4), (5.1, 4.5, 7.4)], 0.22, RED_CABLE, 'starter cable (+)')
    path(m, [(14.5, 4.1, 6.6), (14.5, 4.8, 6.6), (14.5, 4.8, 7.4), (11.4, 4.8, 7.4)], 0.22, BLACK_CABLE, 'battery cable (-)')
    path(m, [(14.5, 4.1, 4.3), (14.5, 4.35, 4.3), (14.5, 4.35, 5.3), (13.3, 4.35, 5.3)], 0.14, BLACK_CABLE, 'battery link')
    m.box((15.0, 2.0, 7.6), (15.6, 3.2, 8.2), 'paint:ral3000', 'battery isolator')
    # the battery isolator is live: OFF kills the controller + fuel solenoid (the set stops) and nothing can crank it
    live(m, 'east', 15.6, 2.6, 7.9, 'key', 'batiso', 0.55, 0.55, 'c81e1e', '', pos=['ON', 'OFF'])
    m.g('engine front')
    m.cyl((8.0, 4.2, 12.2), 1.1, 0.35, 'z', 'dark', 'crank pulley')
    m.cyl((8.0, 8.2, 12.1), 0.75, 0.3, 'z', 'dark', 'fan pulley')
    for x in (6.95, 8.8):
        m.box((x, 4.3, 12.25), (x + 0.25, 8.2, 12.42), 'rubber', 'fan belt')
    m.cyl((10.8, 5.6, 11.6), 0.75, 0.9, 'z', 'metal:alu', 'charging alternator')
    m.box((5.0, 3.0, 12.2), (6.6, 4.6, 12.6), ENGINE, 'water pump')
    m.box((9.6, 9.0, 11.4), (10.8, 10.2, 12.2), ENGINE, 'thermostat housing')
    path(m, [(10.2, 10.2, 11.8), (10.2, 14.2, 11.8), (10.2, 14.2, 13.4)], 0.42, 'rubber', 'top hose', 'dark')
    path(m, [(5.8, 3.0, 12.4), (5.8, 2.2, 12.4), (5.8, 2.2, 13.4)], 0.42, 'rubber', 'bottom hose', 'dark')
    sign(m, 'west', (8.0, 3.6), (11.6, 5.2), 4.6, 'plate:6 CYL DIESEL|1500 RPM')
    fan_and_radiator(m, st, 'COOLANT')
    enclosure(m)
    hv_output(m, (5.4, 10.0, 4.6))
    check_panel(m, 1.6, 3.6, 14.4, 15.6, -1.0)
    m.save()


def turbine(st):
    m = Model('grid_turbine_%d' % st, block='grid_turbine')
    base_frame(m, 'LUBE OIL')
    control_cubicle(m, st, 'STEAM TG 2 MW|11 KV 3PH')

    m.g('generator')
    gc = (8.0, 6.2)
    oct_(m, (gc[0], gc[1], 2.4), 3.6, 5.4, 'z', 'ral7016', 'generator stator', cap='ral7016')
    for z in (3.4, 5.0, 6.6):
        oct_(m, (gc[0], gc[1], z), 3.7, 0.25, 'z', 'ral7016', 'stator band')
    for x in (3.4, 11.2):
        m.box((x, 1.8, 2.8), (x + 1.4, 3.0, 7.4), 'ral7016', 'generator foot')
        for z in (3.2, 7.0):
            m.bolt(x + 0.7, 3.0, z, 'up', 0.35)
    m.box((4.4, 9.6, 3.0), (11.6, 11.4, 7.2), 'ral7016', 'air cooler housing')
    m.box((4.2, 9.9, 3.4), (4.4, 11.1, 6.8), 'fins', 'cooler fins', west='fins')
    m.box((11.6, 9.9, 3.4), (11.8, 11.1, 6.8), 'fins', 'cooler fins', east='fins')
    path(m, [(11.8, 10.2, 3.8), (13.2, 10.2, 3.8), (13.2, 1.8, 3.8)], 0.3, 'paint:#2a6aa0', 'cooling water in')
    path(m, [(11.8, 10.8, 6.4), (13.8, 10.8, 6.4), (13.8, 1.8, 6.4)], 0.3, 'paint:#2a6aa0', 'cooling water out')
    m.box((5.6, 11.4, 4.4), (10.4, 13.0, 6.0), 'ral7016', 'exciter + AVR box')
    sign(m, 'east', (4.0, 7.0), (6.8, 8.6), 11.7, 'plate:GENERATOR|2500 KVA 11 KV')
    m.box((3.6, 3.0, 2.2), (12.4, 4.2, 2.6), 'dark', 'exciter end bearing')
    m.g('coupling')
    m.box((6.2, 4.6, 7.8), (9.8, 7.8, 9.0), 'paint:ral1023', 'coupling guard')
    m.box((6.6, 7.8, 8.0), (9.4, 8.3, 8.8), 'paint:ral1023', 'guard lid')
    m.cyl((8.0, 8.3, 8.4), 0.5, 1.2, 'y', 'paint:#2a5aa0', 'turning gear motor')
    m.box((7.4, 9.5, 8.1), (8.6, 9.8, 8.7), 'dark', 'turning gear motor cap')
    m.g('turbine casing')
    m.box((4.4, 2.8, 9.0), (11.6, 6.2, 13.0), LAG, 'casing lower half')
    m.box((4.0, 6.2, 9.0), (12.0, 6.7, 13.0), 'paint:#5a6066', 'horizontal joint flange')
    for z in (9.3, 9.9, 10.5, 11.1, 11.7, 12.3, 12.8):
        for x in (4.3, 11.7):
            m.cyl((x, 6.7, z), 0.18, 0.5, 'y', STEEL, 'joint stud')
            m.box((x - 0.22, 7.0, z - 0.22), (x + 0.22, 7.25, z + 0.22), STEEL, 'joint nut')
    m.box((4.7, 6.7, 9.2), (11.3, 9.6, 12.8), LAG, 'casing upper half')
    m.box((5.4, 9.6, 9.6), (10.6, 10.0, 12.4), LAG, 'casing crown')
    for z in (9.6, 10.8, 12.0):
        m.box((4.6, 6.7, z), (11.4, 9.7, z + 0.12), 'paint:#8a9096', 'cladding seam')
    m.box((3.6, 2.4, 13.0), (12.4, 10.0, 14.8), LAG, 'exhaust hood')
    for x in (4.6, 11.4):
        m.box((x - 0.6, 1.8, 8.6), (x + 0.6, 6.2, 9.2), 'paint:#5a6066', 'front bearing pedestal')
        m.box((x - 0.6, 1.8, 14.8), (x + 0.6, 6.4, 15.4), 'paint:#5a6066', 'rear bearing pedestal')
    for z in (8.9, 15.1):
        m.box((7.2, 1.8, z - 0.4), (8.8, 5.4, z + 0.4), 'paint:#5a6066', 'bearing housing')
        path(m, [(8.4, 5.4, z), (8.4, 5.9, z), (12.8, 5.9, z), (12.8, 2.2, z)], 0.14, 'paint:#c08020', 'lube oil supply')
        seg(m, (7.6, 1.5, z), (7.6, 1.8, z), 0.2, 'paint:#806020', 'oil drain')
    for z in (10.0, 12.0):
        m.box((11.6, 4.0, z), (12.0, 4.6, z + 0.4), 'metal:brass', 'thermocouple head')
        m.box((4.0, 4.0, z), (4.4, 4.6, z + 0.4), 'metal:brass', 'thermocouple head')
    m.box((6.6, 5.4, 8.5), (7.2, 6.0, 8.9), 'dark', 'speed probe')
    m.g('steam chest + valves')
    m.box((6.0, 10.0, 9.6), (10.0, 11.4, 11.8), LAG, 'steam chest')
    lift = 0.6 if st == 1 else 0.0
    for x in (6.9, 9.1):
        m.cyl((x, 11.4, 10.7), 0.55, 0.6, 'y', 'paint:#5a6066', 'control valve bonnet')
        m.box((x - 0.5, 12.0, 10.6), (x - 0.35, 13.2, 10.8), STEEL, 'valve yoke')
        m.box((x + 0.35, 12.0, 10.6), (x + 0.5, 13.2, 10.8), STEEL, 'valve yoke')
        m.cyl((x, 12.0, 10.7), 0.12, 1.2 + lift, 'y', 'metal:steel', 'valve stem')
    m.box((6.4, 13.2 + lift, 10.5), (9.6, 13.6 + lift, 10.9), 'dark', 'valve lever beam')
    m.cyl((8.0, 13.6 + lift, 10.7), 0.18, 0.5 - lift * 0.5, 'y', STEEL, 'actuator rod')
    m.cyl((8.0, 14.1, 10.7), 0.7, 1.4, 'y', 'paint:#2a5aa0', 'hydraulic governor actuator', cap='dark')
    path(m, [(8.7, 14.4, 10.7), (11.2, 14.4, 10.7), (11.2, 6.9, 10.7)], 0.1, 'paint:#c08020', 'control oil line')
    m.g('main steam inlet')
    m.cyl((13.2, 3.8, 11.0), 1.0, 3.2, 'y', 'paint:#5a6066', 'trip + throttle valve body')
    m.cyl((13.2, 7.0, 11.0), 0.5, 1.0, 'y', 'paint:#5a6066', 'valve bonnet')
    m.cyl((13.2, 8.0, 11.0), 0.12, 0.9, 'y', STEEL, 'valve spindle')
    for ang in (0, 45, -45):
        m.box((12.0, 8.8, 10.95), (14.4, 8.95, 11.05), 'paint:ral3000', 'handwheel spoke',
              rot={'axis': 'y', 'angle': ang, 'origin': [13.2, 8.875, 11.0]} if ang else None)
    m.box((13.15, 8.8, 9.8), (13.25, 8.95, 12.2), 'paint:ral3000', 'handwheel spoke')
    oct_(m, (13.2, 8.8, 11.0), 1.25, 0.15, 'y', 'paint:ral3000', 'handwheel rim')
    m.box((14.1, 5.8, 10.8), (14.5, 6.1, 11.2), 'dark', 'trip latch')
    # the hand trip lever is live: knock it down and the trip + throttle valve slams shut (latched up = ready to run)
    live(m, 'east', 14.5, 6.0, 11.0, 'lever', 'trip', 0.3, 1.6, 'c81e1e', 'TRIP', pos=['LATCHED', 'TRIPPED'])
    m.cyl((14.0, 5.4, 11.0), 0.7, 1.4, 'x', LAG, 'steam inlet pipe')
    flange(m, (15.4, 5.4, 11.0), 1.0, 'x', STEEL, 0.35, 6)
    m.box((14.4, 6.15, 10.6), (15.0, 6.3, 11.4), 'paint:ral3000', 'steam pipe band')
    path(m, [(13.2, 7.4, 12.1), (13.2, 10.6, 12.1), (10.0, 10.6, 12.1), (10.0, 10.6, 11.0)], 0.5, LAG, 'steam lead to chest', LAG)
    sign(m, 'east', (9.4, 2.4), (12.6, 3.6), 15.0, 'label:STEAM IN|40 BAR')
    m.g('gland steam')
    path(m, [(4.4, 5.0, 9.2), (3.6, 5.0, 9.2), (3.6, 2.6, 9.2), (3.6, 2.6, 13.0)], 0.12, STEEL, 'gland leak-off')
    m.cyl((2.4, 2.4, 11.0), 0.55, 2.2, 'z', 'paint:#5a6066', 'gland condenser')
    m.g('lube oil unit')
    m.cyl((12.0, 2.6, 2.8), 0.55, 1.2, 'x', 'paint:#2a5aa0', 'oil pump motor')
    m.box((13.2, 2.1, 2.4), (14.0, 3.1, 3.2), 'paint:#806020', 'oil pump')
    m.cyl((13.0, 3.6, 4.2), 0.5, 3.0, 'z', 'paint:#806020', 'oil cooler')
    m.box((12.2, 1.8, 7.4), (13.8, 2.1, 8.4), 'dark', 'oil filter duplex base')
    m.cyl((12.6, 2.1, 7.9), 0.35, 1.2, 'y', 'paint:#d0d0c8', 'oil filter')
    m.cyl((13.4, 2.1, 7.9), 0.35, 1.2, 'y', 'paint:#d0d0c8', 'oil filter')
    m.g('exhaust')
    oct_(m, (8.0, 10.0, 13.9), 1.3, 2.4, 'y', LAG, 'exhaust neck')
    flange(m, (8.0, 12.4, 13.9), 1.6, 'y', STEEL, 0.3, 8)
    oct_(m, (8.0, 12.7, 13.9), 1.9, 6.8, 'y', 'paint:#6a6e72', 'exhaust vent silencer', cap='dark')
    for y in (13.6, 16.6, 18.8):
        oct_(m, (8.0, y, 13.9), 2.0, 0.2, 'y', 'paint:#5a5e62', 'silencer band')
    seg(m, (8.0, 19.5, 13.9), (8.0, 22.4, 13.9), 1.0, 'paint:#6a6e72', 'vent stack')
    m.box((7.0, 22.4, 12.6), (9.0, 22.55, 12.85), STEEL, 'rain flap hinge')
    if st == 1:
        m.box((6.9, 22.4, 12.8), (9.1, 22.55, 15.0), 'paint:#3a3a3a', 'rain flap (open)', rot={'axis': 'x', 'angle': -45, 'origin': [8.0, 22.47, 12.8]})
    else:
        m.box((6.9, 22.4, 12.8), (9.1, 22.55, 15.0), 'paint:#3a3a3a', 'rain flap (shut)')
    m.cyl((11.4, 8.0, 13.9), 0.45, 1.2, 'x', 'paint:ral3000', 'exhaust relief valve')
    sign(m, 'south', (6.0, 4.0), (10.0, 6.4), 14.8, 'danger:DANGER|HOT STEAM')
    sign(m, 'south', (5.4, 7.0), (10.6, 9.2), 14.8, 'plate:BACK PRESSURE|TURBINE 6000 RPM')
    m.g('turbine skid')
    m.box((2.0, 1.8, 8.4), (14.0, 2.4, 15.6), 'dark', 'turbine bedplate')
    hv_output(m, (5.6, 12.0, 5.2))
    check_panel(m, 1.6, 3.6, 14.4, 15.6, -1.0)
    m.save()


# ================================================================================================ SOLAR
def solar(st):
    m = Model('grid_solar_%d' % st, block='grid_solar')
    m.g('front board')                           # the tracker / string inverter station board (Panel SOLAR, face -0.6)
    for x0 in (0.0, 15.2):
        m.box((x0, 0, -0.6), (x0 + 0.8, 10.0, -0.4), GALV, 'board post flange')
        m.box((x0 + 0.3, 0, -0.4), (x0 + 0.5, 10.0, 0.18), GALV, 'board post web')
        m.box((x0 - 0.2, 0, -0.8), (x0 + 1.0, 0.3, 0.18), 'concrete', 'post footing')
    m.box((0.6, 0.2, 0.0), (15.4, 9.8, 0.18), 'paint:#e6e6e6', 'mounting board')
    m.box((0.6, 3.0, 0.0), (15.4, 3.4, 0.18), GALV, 'board rail', south=GALV)
    m.box((0.6, 7.0, 0.0), (15.4, 7.4, 0.18), GALV, 'board rail', south=GALV)
    m.box((-0.1, 9.8, -2.0), (16.1, 10.1, 0.18), GALV, 'sun shade')
    m.box((-0.1, 9.6, -2.0), (16.1, 9.8, -1.8), GALV, 'shade drip edge')
    sign(m, 'south', (2.0, 4.0), (6.0, 6.4), 0.18, 'danger:DANGER|1000 V DC', t=0.02)
    sign(m, 'south', (10.0, 4.0), (14.0, 6.4), 0.18, 'plate:PV TRACKER ROW|STRING INV', t=0.02)
    m.g('piles + bearings')
    # the deck turns about its top surface, so at +-60 deg its underside sweeps close in under the pivot: the piles
    # are narrow sections and the cradles hug the tube (only a slim wedge under the pivot stays clear)
    for z in (2.5, 13.5):
        hbeam(m, 7.45, 8.55, 0, 8.2, z - 0.45, z + 0.45)
        m.box((7.4, 8.2, z - 0.55), (8.6, 8.4, z + 0.55), GALV, 'pile cap plate')
        m.box((7.7, 8.4, z - 0.45), (8.3, 8.8, z + 0.45), 'paint:#2a2a2a', 'tube bearing cradle')
        m.box((7.75, 8.8, z - 0.4), (8.25, 8.88, z + 0.4), 'paint:#e0e0d8', 'polymer bearing liner')
        for zz in (z - 0.5, z + 0.5):
            m.bolt(8.0, 8.4, zz, 'up', 0.2)
        m.box((6.6, 0, z - 0.9), (9.4, 0.25, z + 0.9), 'concrete', 'pile collar')
    m.g('slew drive')
    hbeam(m, 7.45, 8.55, 0, 6.4, 7.6, 8.4)
    m.box((7.3, 6.4, 7.3), (8.7, 8.1, 8.7), 'paint:#1d1d21', 'slew drive gearbox')
    m.box((7.7, 8.1, 7.4), (8.3, 8.88, 8.6), 'paint:#1d1d21', 'slew drive output clamp')
    m.cyl((8.0, 7.0, 8.7), 0.5, 1.6, 'z', 'paint:#3a6ea5', 'drive motor')
    m.box((7.6, 6.2, 9.4), (8.4, 6.5, 10.0), 'paint:#3a6ea5', 'motor terminal box')
    m.box((6.6, 0, 7.1), (9.4, 0.25, 8.9), 'concrete', 'pile collar')
    m.g('tracker controller')
    m.box((8.7, 3.4, 7.4), (10.2, 5.4, 8.6), 'paint:#e6e6e6', 'tracker control unit', east='label:TCU')
    live(m, 'east', 10.2, 4.95, 8.0, 'lamp', 'ltcu', 0.35, 0.25, '30ff50', '')
    path(m, [(8.0, 6.2, 9.7), (8.0, 5.8, 9.7), (9.4, 5.8, 9.7), (9.4, 5.8, 8.0), (9.4, 5.4, 8.0)], 0.12, BLACK_CABLE, 'motor cable')
    m.box((8.55, 3.0, 7.6), (8.7, 3.8, 8.4), GALV, 'TCU bracket')
    m.g('string cabling')
    for k in range(2):
        col = RED_CABLE if k == 0 else BLACK_CABLE
        x = 7.75 + k * 0.5
        path(m, [(x, 8.3, 12.85), (x, 3.2, 12.85), (x, 3.2, 14.0), (9.6 + k * 0.5, 3.2, 14.0), (9.6 + k * 0.5, 3.0, 14.0)], 0.12, col, 'PV string lead')
        for y in (7.0, 5.5, 4.2):
            m.box((x - 0.2, y, 12.75), (x + 0.2, y + 0.15, 13.05), 'black', 'cable tie')
    m.g('combiner box')
    m.box((9.0, 0.8, 13.2), (12.0, 3.0, 14.8), 'paint:#e6e6e6', 'string combiner box', north='label:PV COMBINER|1000 V DC')
    m.box((8.9, 1.9, 13.1), (12.1, 2.0, 14.9), 'paint:#c8c8c8', 'lid seam')
    for x in (9.3, 11.7):
        for y in (1.0, 2.8):
            m.bolt(x, y, 13.2, 'north', 0.25, 'paint:#a0a0a0')
    for i in range(6):
        m.cyl((9.4 + i * 0.44, 0.55, 14.3), 0.13, 0.25, 'y', 'paint:#202020', 'MC4 gland')
    live(m, 'north', 11.6, 2.6, 13.2, 'lamp', 'lmon', 0.3, 0.22, '30ff50', '')
    m.box((8.9, 0.0, 13.0), (12.1, 0.8, 15.0), GALV, 'combiner stand')
    m.g('DC isolator')
    m.box((12.2, 0.8, 13.3), (14.6, 3.0, 14.7), 'paint:#e6e6e6', 'DC isolator enclosure')
    m.box((12.7, 1.3, 13.15), (14.1, 2.7, 13.3), 'paint:ral1023', 'isolator escutcheon')
    live(m, 'north', 13.4, 2.0, 13.15, 'selector', 'dc', 1.1, 1.1, 'c81e1e', '', pos=['OFF', 'ON'])   # the real DC isolator
    sign(m, 'north', (12.4, 0.85), (14.4, 1.25), 13.3, 'label:PV ISOLATOR')
    m.box((12.1, 0.0, 13.1), (14.7, 0.8, 14.9), GALV, 'isolator stand')
    path(m, [(12.0, 1.8, 14.0), (12.2, 1.8, 14.0)], 0.2, BLACK_CABLE, 'link cable')
    m.g('DC main cables')
    for k, col in enumerate((RED_CABLE, BLACK_CABLE)):
        x = 13.1 + k * 0.6
        path(m, [(x, 0.8, 14.3), (x, 0.4, 14.3), (x, 0.4, 0.2)], 0.2, col, 'DC main cable')
    seg(m, (13.4, 0.4, 2.0), (13.4, 0.4, 12.0), 0.5, GALV, 'cable conduit')
    m.g('earthing')
    for z in (2.5, 13.5):
        path(m, [(6.9, 0.6, z), (6.0, 0.6, z), (6.0, 0.05, z), (6.0, 0.05, 15.6)], 0.12, EARTH_CABLE, 'earth bond')
    earth_rod(m, 4.4, 15.0)
    m.g('12 V terminal')
    m.box((14.6, 0, 12.8), (15.0, 5.0, 13.2), GALV, 'terminal post')
    m.terminal_12v(15.0, 2.5, 13.0, face='east')
    check_panel(m, 1.0, 0.6, 15.0, 9.4, -0.6)
    check_deck(m)
    m.save()


# ================================================================================================ WIND
TOWER_R = 5.0


def tower_shell(m, y0, y1):
    tube16(m, (8.0, y0, 8.0), TOWER_R, y1 - y0, 'paint:#e4e6e8', 'tower shell', cap='paint:#c8cacc')


def wind(st):
    m = Model('grid_wind_%d' % st, block='grid_wind')
    m.g('foundation')
    oct_(m, (8.0, 0, 8.6), 7.0, 1.2, 'y', 'concrete', 'foundation pedestal')
    m.box((3.9, 0, -0.2), (12.1, 1.2, 2.9), 'concrete', 'controller plinth')
    m.g('tower base')
    tube16(m, (8.0, 1.2, 8.0), 5.6, 1.0, 'paint:#c8cacc', 'base flange', cap='paint:#c8cacc')
    for k in range(16):
        a = math.tau * (k + 0.5) / 16
        x, z = 8 + math.cos(a) * 5.35, 8 + math.sin(a) * 5.35
        if z < 2.9 and 3.6 < x < 12.4: continue
        m.box((x - 0.2, 2.2, z - 0.2), (x + 0.2, 2.6, z + 0.2), STEEL, 'anchor nut')
    tower_shell(m, 2.2, 16.0)
    tube16(m, (8.0, 15.6, 8.0), TOWER_R + 0.15, 0.4, 'paint:#d0d2d4', 'section flange seam')
    tube16(m, (8.0, 8.8, 8.0), TOWER_R + 0.05, 0.12, 'paint:#d4d6d8', 'weld seam')
    m.g('tower door')
    m.box((12.9, 4.2, 5.6), (13.4, 12.2, 10.4), 'paint:#c8cacc', 'door frame')
    m.box((13.4, 4.5, 5.9), (13.6, 11.9, 10.1), 'ral7035', 'tower door')
    m.box((13.6, 9.6, 6.5), (13.7, 11.2, 9.5), 'louvre', 'door vent', east='louvre')
    m.box((13.6, 7.4, 9.3), (13.9, 8.4, 9.6), 'dark', 'door handle')
    for y in (5.2, 11.0):
        m.box((13.4, y, 5.6), (13.8, y + 0.6, 5.9), STEEL, 'door hinge')
    sign(m, 'east', (6.4, 5.4), (9.6, 7.2), 13.6, 'danger:DANGER|690 V')
    m.g('door platform + steps')
    m.box((13.4, 3.6, 5.0), (16.0, 4.0, 11.0), 'mesh', 'platform grating')
    for z in (5.2, 10.6):
        m.box((14.8, 1.2, z), (15.2, 3.6, z + 0.3), GALV, 'platform leg')
    for i in range(3):
        y = 1.2 + i * 0.8
        m.box((13.6, y + 0.6, 13.6 - i * 0.9), (15.8, y + 0.8, 14.5 - i * 0.9), 'mesh', 'step tread')
    m.box((13.6, 1.2, 11.0), (13.8, 4.0, 14.6), GALV, 'stair stringer')
    m.box((15.6, 1.2, 11.0), (15.8, 4.0, 14.6), GALV, 'stair stringer')
    m.handrail([(15.8, 5.0), (15.8, 11.0), (15.8, 14.4)], 4.0, 3.2)
    m.g('base controller')
    m.box((4.1, 1.2, 0), (11.9, 9.8, 2.8), 'paint:#dadada', 'base controller cabinet')
    m.box((3.9, 9.8, -0.4), (12.1, 10.2, 3.0), 'paint:#dadada', 'cabinet roof')
    for x in (3.9, 11.9):
        m.box((x, 2.4, 0.4), (x + 0.2, 5.2, 2.4), 'louvre', 'cabinet louvre')
    beacon_live(m, 8.0, 10.2, 1.5)
    path(m, [(8.0, 1.8, 2.8), (8.0, 1.8, 3.4)], 0.45, GALV, 'cable conduit to tower')
    sign(m, 'west', (0.5, 6.0), (2.3, 7.8), 3.9, 'plate:WTG 1.5 MW|690 V')
    m.terminal_12v(11.9, 2.5, 1.2, face='east')
    m.g('HV cable box')
    m.hv_box(3.0, 2.6, 8.0, 'west', phases=3)
    m.g('earthing')
    m.box((3.6, 1.2, 12.6), (3.9, 2.4, 12.9), CU, 'earth strap')
    m.box((2.2, 1.0, 12.6), (3.9, 1.2, 12.9), CU, 'earth strap')
    earth_rod(m, 1.6, 14.4)
    check_panel(m, 4.5, 1.2, 11.5, 9.0, -1.0)
    check_height(m, 16.0)
    m.save()


def wind_tower():
    m = Model('wind_tower')
    m.g('tower section')
    tower_shell(m, 0.0, 16.0)
    tube16(m, (8.0, 0.0, 8.0), TOWER_R + 0.15, 0.4, 'paint:#d0d2d4', 'section flange seam')
    tube16(m, (8.0, 7.94, 8.0), TOWER_R + 0.05, 0.12, 'paint:#d4d6d8', 'weld seam')
    m.box((7.0, 4.0, 2.85), (9.0, 5.0, 3.0), 'plate', 'section plate', north='plate:SECTION 7 M')
    check_height(m, 16.0)
    m.save()


# ================================================================================================ SCADA DESK
def scada(st):
    m = Model('grid_scada_%d' % st, block='grid_scada')
    m.g('desk')
    m.box((0.6, 0, 0.4), (15.4, 0.3, 8.6), 'black', 'plinth')
    m.box((0.4, 0.3, 0), (15.6, 10.4, 9.0), 'paint:#4a4f54', 'console body')
    m.box((0.2, 10.4, -1.6), (15.8, 11.0, 9.4), 'paint:#d8d4c8', 'desk top')
    m.box((0.2, 10.3, -1.8), (15.8, 11.1, -1.6), 'rubber', 'front edge bumper')
    for x0 in (0.4, 12.9):
        m.box((x0, 0.6, -0.1), (x0 + 2.7, 9.8, 0.0), 'paint:#555a60', 'front door')
        m.box((x0 + 0.4, 6.6, -0.15), (x0 + 2.3, 9.0, -0.1), 'louvre', 'door vent')
        m.box((x0 + (2.2 if x0 < 1 else 0.3), 4.6, -0.3), (x0 + (2.4 if x0 < 1 else 0.5), 5.8, -0.1), 'dark', 'door handle')
    m.box((0.4, 2.0, 9.0), (15.6, 8.0, 9.2), 'louvre', 'rear vent', south='louvre')
    m.g('monitors')
    scr = ('#50ff70', '#ff5050', '#40c8ff', '#ffb040', '#50ff70', '#40c8ff')
    k = 0
    for row, (y0, h, zc) in enumerate(((11.6, 3.1, 6.8), (14.9, 3.1, 7.3))):
        for i, (xc, ang) in enumerate(((3.2, 22.5), (8.0, 0), (12.8, -22.5))):
            rot = {'axis': 'y', 'angle': ang, 'origin': [xc, y0, zc]} if ang else None
            m.box((xc - 2.25, y0, zc - 0.2), (xc + 2.25, y0 + h, zc + 0.2), 'black', 'monitor bezel', rot=rot,
                  north=('screen:' + scr[k]) if st else 'paint:#0e1012')
            m.box((xc - 0.6, y0 + 0.8, zc + 0.2), (xc + 0.6, y0 + 2.0, zc + 0.5), 'black', 'VESA mount', rot=rot)
            if row == 0:
                live(m, 'north', xc + 1.82, y0 + 0.17, zc - 0.2, 'lamp', 'pwr%d' % (i + 1), 0.22, 0.14, '30a0ff', '')
            k += 1
    for x in (5.6, 10.4):
        m.box((x - 0.3, 11.0, 7.9), (x + 0.3, 18.6, 8.5), 'dark', 'monitor pole')
        m.box((x - 0.9, 11.0, 7.5), (x + 0.9, 11.2, 8.9), 'dark', 'pole clamp')
    m.box((1.0, 13.0, 7.3), (15.0, 13.4, 7.9), 'dark', 'monitor arm')
    m.box((1.0, 16.3, 7.8), (15.0, 16.7, 8.3), 'dark', 'monitor arm')
    m.box((5.0, 18.6, 7.6), (11.0, 20.0, 8.6), 'black', 'GPS master clock')
    live(m, 'north', 8.0, 19.3, 7.6, 'digits', 'clock', 5.4, 1.1, 'ff3020', '', fmt='%s')
    m.g('keyboards + mice')
    for x0 in (3.8, 8.6):
        m.box((x0, 11.0, -1.2), (x0 + 3.6, 11.25, 0.4), 'black', 'keyboard')
        for r in range(4):
            m.box((x0 + 0.2, 11.25, -1.0 + r * 0.36), (x0 + 3.4, 11.33, -0.75 + r * 0.36), 'paint:#5a5a5a', 'key row')
        m.box((x0 + 0.9, 11.25, -1.15), (x0 + 2.7, 11.33, -1.05), 'paint:#5a5a5a', 'space bar')
        m.box((x0 + 3.9, 11.0, -0.6), (x0 + 4.6, 11.05, 0.6), 'paint:#2a2a40', 'mouse mat') if x0 > 5 else None
        mx = x0 + 4.0 if x0 > 5 else x0 - 0.9
        m.box((mx, 11.0, -0.4), (mx + 0.5, 11.3, 0.3), 'black', 'mouse')
    m.g('phone')
    m.box((12.9, 11.0, -0.6), (15.0, 11.5, 1.2), 'black', 'desk phone')
    m.box((12.9, 11.5, 0.3), (15.0, 11.9, 1.2), 'black', 'phone top')
    m.box((13.0, 11.9, 0.35), (14.9, 12.25, 0.8), 'black', 'handset')
    m.box((13.1, 11.5, -0.4), (14.0, 11.55, 0.2), 'screen:9fd0a0' if st else 'paint:#203020', 'phone display')
    for r in range(4):
        for c in range(3):
            m.box((14.1 + c * 0.28, 11.5, -0.5 + r * 0.25), (14.3 + c * 0.28, 11.6, -0.35 + r * 0.25), 'paint:#c8c8c8', 'phone key')
    path(m, [(12.9, 11.2, 0.5), (12.4, 11.2, 0.5), (12.4, 11.2, 2.6)], 0.1, 'cable:202020', 'handset cord')
    m.g('printer')
    m.box((0.6, 11.0, 1.2), (3.4, 12.6, 4.4), 'paint:#e4e2dc', 'alarm printer')
    m.box((0.9, 12.6, 2.2), (3.1, 12.65, 4.0), 'paint:#2a2a2a', 'scanner lid seam')
    m.box((0.9, 11.6, 0.6), (3.1, 11.7, 1.2), 'white', 'paper tray', rot=None)
    m.box((1.0, 11.7, 0.7), (3.0, 11.75, 1.2), 'label:ALARM LOG', 'printout')
    live(m, 'north', 2.95, 12.3, 1.2, 'lamp', 'lprn', 0.25, 0.18, '30ff50', '')
    m.g('desk extras')
    m.cyl((12.4, 11.0, 3.4), 0.35, 1.1, 'y', 'black', 'PA microphone base')
    path(m, [(12.4, 12.1, 3.4), (12.4, 13.2, 3.4), (12.4, 13.2, 2.7)], 0.08, 'metal:steel', 'gooseneck')
    m.box((12.2, 13.0, 2.4), (12.6, 13.4, 2.7), 'black', 'mic head')
    m.cyl((14.6, 11.0, 3.8), 0.4, 0.9, 'y', 'paint:#ffffff', 'mug')
    m.box((4.0, 11.0, 2.2), (6.8, 11.05, 4.2), 'white', 'switching log sheet')
    m.box((4.2, 11.05, 2.4), (6.6, 11.07, 4.0), 'label:LOG', 'log text')
    m.g('back cabling')
    m.box((1.0, 9.0, 9.2), (15.0, 9.6, 9.8), GALV, 'cable tray')
    for x in (3.0, 8.0, 13.0):
        seg(m, (x, 9.6, 9.5), (x, 11.6, 9.5), 0.1, 'cable:3060c0', 'video cable')
        seg(m, (x + 0.3, 9.6, 9.5), (x + 0.3, 11.6, 9.5), 0.1, BLACK_CABLE, 'mains lead')
    path(m, [(2.0, 9.0, 9.5), (2.0, 0.0, 9.5)], 0.18, 'cable:3060c0', 'data trunk to floor')
    path(m, [(2.5, 9.0, 9.5), (2.5, 0.0, 9.5)], 0.18, 'cable:e0c020', 'fibre to RTU')
    sign(m, 'south', (5.0, 1.0), (9.0, 2.2), 9.0, 'plate:SCADA DESK 1')
    m.terminal_12v(12.0, 2.5, 9.0, face='south')
    check_panel(m, 3.5, 1.2, 12.5, 7.4, -1.0)
    m.save()


# ================================================================================================ PLC CABINET
def auto(st):
    m = Model('grid_auto_%d' % st, block='grid_auto')
    m.g('cabinet')
    m.box((1.6, 0, 0.6), (14.4, 0.8, 14.0), 'black', 'plinth', north='haz')
    m.box((1.4, 0.8, 0), (13.0, 14.8, 14.2), 'paint:#c5c7c4', 'cabinet carcass')
    m.box((13.0, 0.8, 0), (14.6, 14.8, 0.4), 'paint:#c5c7c4', 'carcass front post')
    m.box((13.0, 0.8, 13.8), (14.6, 14.8, 14.2), 'paint:#c5c7c4', 'carcass rear post')
    m.box((13.0, 14.4, 0.4), (14.6, 14.8, 13.8), 'paint:#c5c7c4', 'carcass top rail')
    m.box((13.0, 0.8, 0.4), (14.6, 2.6, 13.8), 'paint:#c5c7c4', 'carcass bottom rail')
    m.box((13.0, 2.6, 0.4), (13.2, 14.4, 13.8), 'paint:#3a3a3a', 'cabinet interior', faces=('east',))
    live(m, 'east', 14.4, 14.05, 7.0, 'lamp', 'llight', 5.6, 0.25, 'f4f0e0', '')
    m.box((1.0, 14.8, -0.4), (15.0, 15.3, 14.6), 'paint:#c5c7c4', 'roof')
    for (x, z) in ((1.8, 0.6), (14.2, 0.6), (1.8, 13.6), (14.2, 13.6)):
        m.cyl((x, 15.3, z), 0.3, 0.6, 'y', STEEL, 'lifting eye')
    m.g('side window + DIN rails')                # east door with a viewing window into the panel
    # door frame round the window opening (no pane drawn, so the gear inside shows)
    m.box((14.6, 0.8, 0.4), (14.8, 2.6, 13.8), 'paint:#c5c7c4', 'side door rail')
    m.box((14.6, 12.8, 0.4), (14.8, 14.6, 13.8), 'paint:#c5c7c4', 'side door rail')
    m.box((14.6, 2.6, 0.4), (14.8, 12.8, 1.8), 'paint:#c5c7c4', 'side door stile')
    m.box((14.6, 2.6, 12.4), (14.8, 12.8, 13.8), 'paint:#c5c7c4', 'side door stile')
    for (a, b) in (((14.8, 2.4, 1.6), (14.95, 2.6, 12.6)), ((14.8, 12.8, 1.6), (14.95, 13.0, 12.6)),
                   ((14.8, 2.6, 1.6), (14.95, 12.8, 1.8)), ((14.8, 2.6, 12.4), (14.95, 12.8, 12.6))):
        m.box(a, b, 'rubber', 'window gasket')
    m.box((14.6, 6.8, 13.8), (15.0, 8.8, 14.1), 'dark', 'door lock')
    m.box((13.0, 2.6, 1.8), (13.2, 12.8, 12.4), GALV, 'mounting plate')
    for y in (11.2, 8.6, 6.0, 3.4):
        m.box((13.2, y, 2.0), (13.35, y + 0.35, 12.2), 'metal:alu', 'DIN rail')
        m.box((13.2, y + 1.0, 2.0), (13.9, y + 1.6, 12.2), 'paint:#9a9c98', 'wiring duct', east='louvre')
    # row 1: PLC rack
    m.box((13.35, 10.2, 2.2), (14.2, 12.0, 3.6), 'paint:#2a3a4a', 'PLC power supply', east='label:PS 24V')
    m.box((13.35, 10.2, 3.7), (14.3, 12.0, 5.0), 'paint:#2a3a4a', 'PLC CPU')
    live(m, 'east', 14.3, 11.5, 4.35, 'lamp', 'lcpu', 0.5, 0.3, '50ff70', '')                # RUN
    for i in range(7):                       # each IO card's LED shows its real channel (see TileGrid.modelTick)
        z = 5.1 + i * 0.75
        m.box((13.35, 10.2, z), (14.2, 12.0, z + 0.68), 'paint:#2a3a4a', 'IO module')
        live(m, 'east', 14.2, 11.5, z + 0.34, 'lamp', 'io%d' % (i + 1), 0.3, 0.3, '50ff70' if i < 4 else 'ffb040', '')
    m.box((13.35, 10.2, 10.4), (14.2, 12.0, 11.6), 'paint:#3a4a5a', 'comms module', east='label:IEC 104')
    # row 2: breakers, contactors, relays
    for i in range(8):
        z = 2.2 + i * 0.5
        m.box((13.35, 7.6, z), (14.0, 9.1, z + 0.46), 'white', 'MCB')
        if i == 0:                           # the PLC supply MCB is live: OFF = the controller is dead
            live(m, 'east', 14.0, 8.35, z + 0.23, 'lever', 'mcb', 0.18, 0.3, '2a2a2a', '')
        else:
            m.box((14.0, 8.25, z + 0.14), (14.15, 8.55, z + 0.32), 'paint:#2a2a2a', 'MCB toggle')
    for i in range(3):
        z = 6.4 + i * 1.0
        m.box((13.35, 7.4, z), (14.3, 9.2, z + 0.9), 'paint:#3a3a3a', 'contactor', east='paint:#5a5a5a')
        m.box((14.3, 8.4, z + 0.3), (14.4, 8.8, z + 0.6), 'paint:#888888', 'contactor flag')
    for i in range(6):
        z = 9.6 + i * 0.42
        m.box((13.35, 7.6, z), (14.1, 9.0, z + 0.38), 'paint:#e8e030', 'interposing relay')
    # row 3: terminal blocks
    tcols = ('#9aa0a6', '#9aa0a6', '#3060c0', '#9aa0a6', '#9aa0a6', '#3a8a3a', '#9aa0a6', '#9aa0a6', '#3060c0', '#9aa0a6',
             '#9aa0a6', '#d02020', '#9aa0a6', '#9aa0a6', '#3a8a3a', '#9aa0a6', '#9aa0a6', '#9aa0a6', '#3060c0', '#9aa0a6')
    for i, c in enumerate(tcols):
        z = 2.2 + i * 0.48
        m.box((13.35, 5.4, z), (13.95, 6.9, z + 0.42), 'paint:' + c, 'terminal block')
    m.box((13.35, 5.6, 12.0 - 0.2), (13.8, 6.7, 12.05), 'paint:#2a2a2a', 'end stop')
    # row 4: 24 V supply, UPS, network switch, fuses
    m.box((13.35, 2.8, 2.2), (14.3, 4.6, 4.4), 'paint:#5a6066', '24 V DC supply', east='label:24V 10A')
    m.box((13.35, 2.8, 4.6), (14.3, 4.6, 6.6), 'paint:#2a2a2a', 'DC UPS', east='label:UPS')
    m.box((13.35, 2.8, 6.8), (14.2, 4.6, 9.4), 'paint:#3a4a5a', 'network switch')
    live(m, 'east', 14.2, 4.2, 8.1, 'lamp', 'lnet', 0.4, 0.25, '50ff70', '')
    for i in range(5):
        z = 9.6 + i * 0.5
        m.box((13.35, 3.0, z), (14.0, 4.4, z + 0.44), 'paint:#3a3a3a', 'fuse holder')
    # a few wires from the gear into the ducts
    for (y, z, c) in ((9.1, 3.0, '#d02020'), (9.1, 7.0, '#3060c0'), (6.9, 4.0, '#3060c0'), (6.9, 8.0, '#d02020'), (4.6, 5.6, '#3060c0')):
        seg(m, (13.8, y, z), (13.8, y + 0.9, z), 0.06, 'cable:' + c[1:], 'control wire')
    m.g('west side')
    m.box((1.2, 3.0, 4.0), (1.4, 6.6, 7.6), 'paint:#5a5e62', 'filter fan housing')
    m.box((1.1, 3.4, 4.4), (1.2, 6.2, 7.2), 'mesh', 'filter fan grille', west='mesh')
    m.box((1.2, 10.4, 4.0), (1.4, 13.6, 7.6), 'paint:#5a5e62', 'exhaust filter housing')
    m.box((1.1, 10.8, 4.4), (1.2, 13.2, 7.2), 'louvre', 'exhaust filter louvre', west='louvre')
    sign(m, 'west', (9.0, 9.0), (12.0, 11.2), 1.4, 'danger:DANGER|400 V')
    sign(m, 'west', (8.6, 5.4), (12.4, 7.4), 1.4, 'plate:AUTO CONTROL|PLC CABINET')
    m.terminal_12v(1.4, 2.5, 10.4, face='west')
    m.g('rear + cable entry')
    m.box((3.0, 0.8, 14.2), (13.0, 1.2, 14.6), 'dark', 'gland plate')
    for x in (4.0, 5.4, 6.8, 9.2, 10.6, 12.0):
        m.gland(x, 1.0, 14.4, face='down', r=0.3, cable=BLACK_CABLE if x < 8 else 'cable:3060c0', length=1.0)
    sign(m, 'south', (4.4, 9.0), (11.6, 11.0), 14.2, 'plate:IP54  24V DC CONTROL')
    m.box((2.0, 2.0, 14.2), (14.0, 2.2, 14.4), 'paint:#b5b7b4', 'rear door seam')
    m.box((7.6, 7.0, 14.2), (8.4, 9.0, 14.5), 'dark', 'rear door handle')
    m.g('roof gear')
    m.cyl((8.0, 15.3, 4.0), 0.6, 0.4, 'y', 'dark', 'stack light base')
    for i, (c, cid) in enumerate((('30ff50', 'sgreen'), ('ffb020', 'samber'), ('ff3020', 'sred'))):
        m.box((7.5, 15.7 + i * 0.9, 3.5), (8.5, 16.5 + i * 0.9, 4.5), 'paint:#303030', 'stack light module')
        live(m, 'north', 8.0, 16.1 + i * 0.9, 3.5, 'lamp', cid, 0.8, 0.6, c, '')
    m.box((7.6, 18.4, 3.6), (8.4, 18.6, 4.4), 'dark', 'stack light cap')
    m.box((10.6, 15.3, 3.0), (13.4, 16.9, 5.6), 'paint:#c5c7c4', 'alarm sounder')
    m.cyl((12.0, 16.1, 2.6), 0.6, 0.4, 'z', 'black', 'sounder horn')
    m.cyl((4.0, 15.3, 10.0), 0.3, 0.3, 'y', 'dark', 'antenna base')
    m.cyl((4.0, 15.6, 10.0), 0.12, 3.4, 'y', 'black', '4G antenna')
    m.box((2.6, 15.3, 8.0), (6.0, 15.8, 12.6), 'louvre', 'roof vent hood')
    m.g('earthing')
    m.box((2.2, 0.0, 14.6), (2.6, 1.0, 15.0), CU, 'earth strap')
    m.box((2.2, 0.0, 14.6), (2.6, 0.1, 16.0), CU, 'earth strap to grid')
    check_panel(m, 2.0, 1.0, 14.0, 13.6, -1.0)
    m.save()


# ================================================================================================ EARTHING SWITCH
def earthing(st):
    m = Model('grid_earthing_%d' % st, block='grid_earthing')
    m.g('foundation')
    m.box((1.0, 0, 1.0), (15.0, 0.4, 15.0), 'concrete', 'concrete pad')
    m.g('earth bar')                      # BondStrap ties every neighbour to this copper boss (centre 7..9)
    m.box((6.4, 0.4, 6.4), (9.6, 0.9, 9.6), CU, 'main earth bar')
    for (x, z) in ((6.8, 6.8), (9.2, 6.8), (6.8, 9.2), (9.2, 9.2)):
        m.bolt(x, 0.9, z, 'up', 0.35)
    m.box((7.4, 0.9, 7.4), (8.6, 1.3, 8.6), 'paint:#3a8a3a', 'earth symbol tag')
    earth_rod(m, 2.4, 13.6, 0.4)
    earth_rod(m, 13.6, 13.6, 0.4)
    path(m, [(2.4, 1.1, 13.6), (2.4, 1.1, 10.2), (6.4, 1.1, 10.2), (6.4, 0.9, 10.2)], 0.15, CU, 'earth electrode tail')
    path(m, [(13.6, 1.1, 13.6), (13.6, 1.1, 10.2), (9.6, 1.1, 10.2), (9.6, 0.9, 10.2)], 0.15, CU, 'earth electrode tail')
    m.g('support column')
    m.box((9.2, 0.4, 3.2), (11.8, 0.7, 5.8), GALV, 'column base plate')
    for (x, z) in ((9.5, 3.5), (11.5, 3.5), (9.5, 5.5), (11.5, 5.5)):
        m.bolt(x, 0.7, z, 'up', 0.3)
    hbeam(m, 9.6, 11.4, 0.7, 13.4, 3.6, 5.4)
    m.box((3.0, 13.4, 3.4), (13.0, 14.2, 5.6), GALV, 'top channel')
    m.box((3.0, 13.4, 4.4), (13.0, 14.2, 4.6), 'dark', 'channel web line', faces=('up',))
    for x in (4.0, 12.0):
        m.box((x - 0.15, 10.6, 4.35), (x + 0.15, 13.4, 4.65), GALV, 'channel brace', rot={'axis': 'z', 'angle': 45 if x < 8 else -45, 'origin': [x, 13.4, 4.5]})
    sign(m, 'north', (9.6, 10.0), (11.4, 12.2), 3.6, 'danger:DANGER|11000 V')
    m.g('post insulator')
    m.cyl((10.5, 14.2, 4.5), 1.0, 0.4, 'y', GALV, 'insulator base flange')
    m.bushing(10.5, 4.5, 14.6, 6.4, sheds=5, cap=False)
    m.cyl((10.5, 21.0, 4.5), 0.95, 0.5, 'y', GALV, 'insulator cap flange')
    m.g('fixed contact + neutral')
    m.box((4.0, 21.5, 4.1), (11.2, 21.9, 4.9), CU, 'contact arm')
    m.box((3.5, 21.0, 3.8), (3.8, 22.4, 5.2), CU, 'contact jaw finger')
    m.box((4.6, 21.0, 3.8), (4.9, 22.4, 5.2), CU, 'contact jaw finger')
    m.box((10.9, 21.9, 3.6), (12.6, 22.3, 5.4), CU, 'terminal pad')
    for x in (11.3, 12.2):
        m.bolt(x, 22.3, 4.5, 'up', 0.3)
    # the transformer neutral comes in as a cable: up the column in cleats to a sealing end
    path(m, [(12.6, -0.6, 6.4), (12.6, 13.0, 6.4)], 0.4, BLACK_CABLE, 'neutral cable')
    for y in (3.0, 6.0, 9.0, 12.0):
        m.box((11.4, y, 5.9), (13.1, y + 0.4, 6.9), GALV, 'cable cleat')
    m.box((12.0, 13.0, 5.8), (13.2, 14.2, 7.0), GALV, 'sealing end bracket')
    m.cyl((12.6, 14.2, 6.4), 0.5, 3.0, 'y', 'porcelain', 'cable sealing end')
    for y in (14.6, 15.4, 16.2):
        m.cyl((12.6, y, 6.4), 0.8, 0.25, 'y', 'porcelain', 'sealing end shed')
    m.cyl((12.6, 17.2, 6.4), 0.35, 0.6, 'y', CU, 'sealing end stalk')
    path(m, [(12.6, 17.8, 6.4), (12.6, 22.1, 6.4), (12.6, 22.1, 5.4)], 0.22, CU, 'neutral jumper')
    path(m, [(12.6, 22.1, 4.5), (16.0, 22.1, 4.5)], 0.3, CU, 'neutral conductor to bus')
    m.g('earth blade')
    m.box((3.0, 14.2, 3.6), (5.0, 15.4, 5.4), GALV, 'hinge bracket')
    m.cyl((4.2, 15.0, 3.4), 0.3, 2.2, 'z', STEEL, 'hinge pin')
    m.box((3.9, 15.0, 4.2), (4.5, 22.0, 4.8), CU, 'earth blade (closed)')
    m.box((3.8, 21.4, 4.1), (4.6, 22.0, 4.9), CU, 'blade contact tip')
    m.g('earth braid')
    path(m, [(3.4, 14.4, 5.6), (3.4, 14.4, 6.2), (3.4, 1.1, 6.2), (6.4, 1.1, 6.2), (6.4, 0.9, 6.4)], 0.18, CU, 'flexible earth braid')
    for y in (3.0, 7.0, 11.0):
        m.box((3.0, y, 5.8), (3.8, y + 0.3, 6.6), GALV, 'braid clip')
    m.g('operating mechanism')
    for x in (9.6, 11.4):
        m.box((x - 0.2, 3.0, 3.2), (x + 0.2, 3.6, 3.6), GALV, 'box bracket')
        m.box((x - 0.2, 9.0, 3.2), (x + 0.2, 9.6, 3.6), GALV, 'box bracket')
    m.box((3.6, 1.0, 0), (12.4, 11.6, 3.2), 'ral7035', 'mechanism box')
    m.box((3.4, 11.6, -0.4), (12.6, 12.0, 3.4), 'ral7035', 'box roof')
    m.box((12.4, 3.0, 0.6), (12.6, 6.0, 2.6), 'louvre', 'heater vent')
    m.box((3.4, 3.0, 0.6), (3.6, 6.0, 2.6), 'louvre', 'heater vent')
    sign(m, 'east', (0.4, 8.8), (2.8, 10.8), 12.4, 'plate:NEUTRAL EARTH|SWITCH 630 A')
    live(m, 'east', 12.4, 7.3, 1.3, 'lamp', 'vpis1', 0.5, 0.5, 'ff3020', '')     # neutral voltage (self-powered)
    live(m, 'east', 12.4, 7.3, 2.1, 'lamp', 'vpis2', 0.5, 0.5, 'ff3020', '')
    m.box((12.4, 6.4, 0.9), (12.5, 6.9, 2.5), 'label:LIVE', 'VPIS label', east='label:LIVE')
    m.cyl((4.6, 12.0, 1.6), 0.25, 3.0, 'y', GALV, 'operating shaft')
    m.box((4.3, 13.6, 1.3), (4.9, 14.0, 4.6), GALV, 'crank link')
    m.cyl((4.6, 12.0, 1.6), 0.4, 0.4, 'y', STEEL, 'shaft coupling')
    for x in (5.6, 7.0, 8.4, 9.8, 11.2):
        m.cyl((x, 0.4, 1.6), 0.25, 0.6, 'y', 'metal:brass', 'cable gland')
    path(m, [(5.6, 0.4, 1.6), (5.6, 0.4, 2.4), (1.4, 0.4, 2.4)], 0.15, 'cable:2a2a2a', 'aux cable')
    m.terminal_12v(3.6, 2.5, 1.6, face='west')
    check_panel(m, 4.0, 1.5, 12.0, 11.0, -1.0)
    m.save()


# ================================================================================================ PROTECTION RELAY
def relay(st):
    m = Model('grid_relay_%d' % st, block='grid_relay')
    m.g('cubicle')                       # Panel RELAY sits at face 4.6: its plate stands on the front at z 4.8
    m.box((2.6, 0, 5.0), (13.4, 1.0, 5.4), 'black', 'plinth front', north='haz')
    m.box((2.6, 0, 11.8), (13.4, 1.0, 12.2), 'black', 'plinth rear')
    m.box((2.6, 0, 5.4), (3.0, 1.0, 11.8), 'black', 'plinth side')
    m.box((13.0, 0, 5.4), (13.4, 1.0, 11.8), 'black', 'plinth side')
    for x in (2.4, 13.6):
        for y in (2.0, 5.0, 8.0, 11.0, 14.0):
            for z in (5.2, 12.0):
                m.bolt(x, y, z, 'west' if x < 8 else 'east', 0.3)
    m.box((2.4, 1.0, 4.8), (13.6, 16.0, 12.4), 'paint:#24272b', 'relay cubicle')
    m.box((2.2, 16.0, 4.4), (13.8, 16.4, 12.8), 'paint:#24272b', 'cubicle roof')
    for (x, z) in ((2.8, 5.2), (13.2, 5.2), (2.8, 12.0), (13.2, 12.0)):
        m.cyl((x, 16.4, z), 0.3, 0.6, 'y', STEEL, 'lifting eye')
    m.box((2.4, 14.4, 4.6), (13.6, 15.8, 4.8), 'paint:#24272b', 'top fascia', north='label:FEEDER 1|PROTECTION')
    m.box((2.4, 1.0, 4.6), (13.6, 1.4, 4.8), 'paint:#24272b', 'kick plate')
    m.box((2.4, 1.4, 4.6), (3.0, 14.4, 4.8), 'paint:#24272b', 'door post')
    m.box((13.0, 1.4, 4.6), (13.6, 14.4, 4.8), 'paint:#24272b', 'door post')
    for x in (2.2, 13.6):
        m.box((x, 3.0, 6.0), (x + 0.2, 6.4, 11.2), 'louvre', 'side louvre')
        m.box((x, 10.0, 6.0), (x + 0.2, 13.4, 11.2), 'louvre', 'side louvre')
    m.g('rear door')
    m.box((2.8, 1.6, 12.4), (13.2, 15.6, 12.55), 'paint:#2c3035', 'rear door')
    m.box((12.2, 7.4, 12.55), (12.6, 9.4, 12.9), 'dark', 'rear handle')
    for y in (3.0, 13.0):
        m.box((2.6, y, 12.4), (2.9, y + 1.0, 12.7), STEEL, 'rear hinge')
    m.box((4.0, 2.4, 12.55), (12.0, 4.6, 12.65), 'louvre', 'rear vent', south='louvre')
    sign(m, 'south', (4.4, 10.0), (11.6, 12.6), 12.55, 'plate:IED 50 51 50N|CT 400:1  VT 11KV')
    sign(m, 'south', (5.6, 5.4), (10.4, 7.6), 12.55, 'danger:DANGER|CT CIRCUITS')
    m.g('cable entry')
    m.box((3.4, 0.6, 9.0), (12.6, 1.0, 12.0), 'dark', 'gland plate')
    for i, x in enumerate((4.2, 5.6, 7.0, 8.4, 9.8, 11.2)):
        col = ('cable:2a2a2a', 'cable:2a2a2a', 'cable:3060c0', 'cable:2a2a2a', 'cable:c0a020', 'cable:2a2a2a')[i]
        path(m, [(x, 0.6, 10.5), (x, -0.4, 10.5), (x, -0.4, 16.0)], 0.22, col, 'multicore (CT/VT/trip)')
    m.box((2.4, 0.0, 12.6), (13.6, 0.12, 16.0), 'paint:#6a6e72', 'chequer trench cover')
    for x in (2.4, 6.2, 10.0):
        m.box((x + 0.2, 0.12, 13.0), (x + 0.6, 0.2, 13.4), 'dark', 'cover lifting slot')
    m.g('cable tray overhead')
    m.box((0.0, 17.6, 8.0), (16.0, 17.8, 11.2), GALV, 'ladder tray base')
    for z in (8.0, 11.0):
        m.box((0.0, 17.6, z), (16.0, 18.6, z + 0.2), GALV, 'tray side rail')
    for x in (1.0, 4.0, 7.0, 10.0, 13.0):
        m.box((x, 17.8, 8.2), (x + 0.3, 17.9, 11.0), GALV, 'tray rung')
    for k, z in enumerate((8.6, 9.3, 10.0, 10.6)):
        seg(m, (0.0, 18.05, z), (16.0, 18.05, z), 0.25, ('cable:2a2a2a', 'cable:3060c0', 'cable:2a2a2a', 'cable:c0a020')[k], 'tray cable')
    for x in (4.6, 11.4):
        m.box((x - 0.15, 16.4, 9.7), (x + 0.15, 17.6, 10.0), GALV, 'tray hanger')
    path(m, [(6.0, 17.6, 9.3), (6.0, 16.4, 9.3)], 0.2, 'cable:3060c0', 'drop to cubicle')
    path(m, [(9.0, 17.6, 10.0), (9.0, 16.4, 10.0)], 0.2, 'cable:2a2a2a', 'drop to cubicle')
    m.g('earthing')
    m.box((3.0, 0.0, 12.2), (3.4, 1.0, 12.6), CU, 'earth strap')
    m.box((3.0, 0.0, 12.2), (3.4, 0.1, 16.0), CU, 'earth strap to grid')
    m.g('status')
    live(m, 'east', 13.6, 15.3, 6.3, 'lamp', 'ldc', 0.5, 0.5, '30ff50', 'DC OK')
    m.terminal_12v(13.6, 2.5, 9.0, face='east')
    check_panel(m, 3.4, 2.0, 12.6, 14.0, 4.6)
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 3.0, 13.0, 1.8, 14.2, 3.6, side='south', col='paint:#2c3035', title='FEEDER 1|PROTECTION', volts='110V DC', fns=(('ctl', '12V AUX'),))
    m.save()


# ================================================================================================ DISCONNECTOR
def isolator(st):
    m = Model('grid_isolator_%d' % st, block='grid_isolator')
    poles = (2.6, 8.0, 13.4)
    m.g('foundations')
    for x in (2.6, 13.4):
        m.box((x - 1.6, 0, 6.4), (x + 1.6, 1.0, 9.6), 'concrete', 'column plinth')
        for (dx, dz) in ((-1.1, -1.1), (1.1, -1.1), (-1.1, 1.1), (1.1, 1.1)):
            m.bolt(x + dx, 1.0, 8.0 + dz, 'up', 0.35)
    m.g('support columns')
    for x in (2.6, 13.4):
        m.box((x - 1.2, 1.0, 6.8), (x + 1.2, 1.3, 9.2), GALV, 'column base plate')
        hbeam(m, x - 0.8, x + 0.8, 1.3, 9.6, 7.2, 8.8)
    m.box((1.8, 6.0, 8.2), (14.2, 6.8, 8.8), GALV, 'mechanism crossbeam')
    m.box((1.8, 2.4, 8.2), (14.2, 3.0, 8.8), GALV, 'lower crossbeam')
    m.g('base frame')
    for x in (2.6, 13.4):
        m.box((x - 1.0, 9.6, 0.6), (x + 1.0, 10.4, 15.4), GALV, 'pole channel')
        m.box((x - 1.0, 9.6, 0.6), (x - 0.8, 9.6 + 0.8, 15.4), 'dark', 'channel lip', faces=('up',))
    m.box((1.6, 9.8, 7.6), (14.4, 10.2, 8.4), GALV, 'tie bar')
    m.g('operating mechanism')
    m.box((4.2, 0.8, 5.2), (11.8, 9.2, 8.2), 'paint:#4a4f54', 'mechanism box')
    m.box((4.0, 9.2, 4.8), (12.0, 9.55, 8.6), 'paint:#4a4f54', 'box roof')
    m.box((11.8, 3.0, 5.8), (12.0, 6.0, 7.6), 'louvre', 'heater vent')
    sign(m, 'west', (5.6, 6.0), (7.8, 8.0), 4.2, 'plate:DISCONNECTOR|36 KV 1250 A')
    for x in (5.4, 7.0, 9.0, 10.6):
        m.cyl((x, 0.4, 6.8), 0.25, 0.4, 'y', 'metal:brass', 'cable gland')
    path(m, [(7.0, 0.4, 6.8), (7.0, 0.2, 6.8), (7.0, 0.2, 16.0)], 0.18, 'cable:2a2a2a', 'motor + aux cable')
    m.g('drive linkage')
    m.cyl((5.0, 9.55, 7.6), 0.35, 0.3, 'y', STEEL, 'output shaft coupling')
    m.cyl((5.0, 9.85, 7.6), 0.22, 1.0, 'y', GALV, 'vertical drive pipe')
    crank = 0.0 if st == 0 else 0.6
    m.box((2.4, 10.85, 7.4), (5.3, 11.15, 7.8), STEEL, 'drive crank')
    m.box((0.9, 10.5, 1.8 + crank), (1.3, 10.9, 14.2 + crank), GALV, 'interphase coupling rod')
    for z in poles:
        m.box((1.0, 10.5, z - 0.2), (2.6, 10.9, z + 0.2), STEEL, 'pole crank arm')
        m.cyl((2.6, 10.4, z), 0.9, 0.5, 'y', 'dark', 'rotating base bearing')
        m.cyl((13.4, 10.4, z), 0.9, 0.3, 'y', GALV, 'fixed insulator base')
    m.g('post insulators')
    for z in poles:
        for x in (2.6, 13.4):
            m.bushing(x, z, 10.9 if x < 8 else 10.7, 5.6, sheds=5, cap=False)
            m.cyl((x, 16.4, z), 0.8, 0.4, 'y', GALV, 'insulator cap')
    m.g('contacts + blades')
    for z in poles:
        m.box((1.8, 16.8, z - 0.6), (3.4, 18.0, z + 0.6), CU, 'hinge contact')
        m.box((12.9, 16.8, z - 0.7), (14.0, 17.2, z + 0.7), CU, 'jaw base')
        for dz in (-0.55, 0.35):
            m.box((13.0, 17.2, z + dz), (13.9, 18.4, z + dz + 0.2), CU, 'jaw finger')
        m.box((13.8, 18.0, z - 0.1), (14.0, 19.6, z + 0.1), CU, 'arcing horn', rot={'axis': 'z', 'angle': -22.5, 'origin': [13.9, 18.0, z]})
        rot = None if st == 0 else {'axis': 'z', 'angle': 45, 'origin': [2.6, 17.6, z]}
        m.box((2.6, 17.3, z - 0.3), (13.6, 17.9, z + 0.3), CU, 'blade (closed)' if st == 0 else 'blade (open)', rot=rot)
        m.box((12.6, 17.2, z - 0.35), (13.6, 18.0, z + 0.35), 'metal:tin', 'silver-plated blade tip', rot=rot)
        # line terminals: conductors out to the neighbouring bays at the block edges
        m.box((0.6, 16.8, z - 0.5), (1.8, 17.3, z + 0.5), CU, 'terminal pad')
        seg(m, (-0.2, 17.05, z), (0.6, 17.05, z), 0.3, CU, 'line conductor')
        m.box((14.0, 16.8, z - 0.5), (15.2, 17.3, z + 0.5), CU, 'terminal pad')
        seg(m, (15.2, 17.05, z), (16.2, 17.05, z), 0.3, CU, 'line conductor')
    m.g('signs + earthing')
    sign(m, 'north', (12.6, 4.0), (14.2, 6.0), 7.2, 'danger:DANGER|33000 V')
    sign(m, 'north', (1.8, 4.0), (3.4, 6.0), 7.2, 'warn')
    for x in (2.6, 13.4):
        m.box((x + 1.2, 0.0, 8.6), (x + 1.5, 1.6, 8.9), CU, 'column earth strap')
        m.box((x + 1.2, 0.0, 8.6), (x + 1.5, 0.1, 16.0), CU, 'earth strap to grid')
    m.terminal_12v(14.2, 2.5, 8.0, face='east')
    check_panel(m, 4.5, 1.0, 11.5, 8.8, 5.0)
    m.save()


# ================================================================================================ SURGE ARRESTER
def arrester(st):
    m = Model('grid_arrester_%d' % st, block='grid_arrester')
    cz = 4.0
    m.g('foundation')
    m.box((4.4, 0, 1.4), (11.6, 0.6, 6.8), 'concrete', 'footing')
    m.box((6.2, 0.6, cz - 1.4), (9.8, 0.9, cz + 1.4), GALV, 'column base plate')
    for (dx, dz) in ((-1.5, -1.1), (1.5, -1.1), (-1.5, 1.1), (1.5, 1.1)):
        m.bolt(8 + dx, 0.9, cz + dz, 'up', 0.3)
    m.g('support column')
    hbeam(m, 6.6, 9.4, 0.9, 11.0, cz - 1.0, cz + 1.0)
    m.box((6.0, 11.0, cz - 2.0), (10.0, 11.4, cz + 2.0), GALV, 'top plate')
    sign(m, 'north', (6.7, 6.0), (9.3, 8.4), cz - 1.0, 'danger:DANGER|33000 V')
    m.g('insulating base')
    for (dx, dz) in ((-1.1, -0.8), (1.1, -0.8), (0.0, 1.1)):
        m.cyl((8 + dx, 11.4, cz + dz), 0.45, 0.8, 'y', 'porcelain', 'insulating base')
    m.box((6.4, 12.2, cz - 1.6), (9.6, 12.6, cz + 1.6), 'metal:alu', 'arrester base flange')
    m.g('arrester stack')
    for y0 in (12.6, 19.0):
        m.cyl((8.0, y0, cz), 1.1, 0.4, 'y', 'metal:alu', 'unit flange')
        m.cyl((8.0, y0 + 0.4, cz), 0.75, 5.8, 'y', 'porcelain:grey', 'polymer housing')
        for k in range(7):
            m.cyl((8.0, y0 + 0.8 + k * 0.78, cz), 1.35 if k % 2 == 0 else 1.1, 0.22, 'y', 'porcelain:grey', 'silicone shed')
    m.cyl((8.0, 25.2, cz), 1.1, 0.5, 'y', 'metal:alu', 'top cap')
    sign(m, 'north', (7.0, 13.2), (9.0, 14.0), cz - 0.78, 'plate:36 KV 10 KA')
    m.g('grading ring')
    ry, R = 24.0, 2.6
    for ang in (0, 45):
        rot = {'axis': 'y', 'angle': ang, 'origin': [8.0, ry, cz]} if ang else None
        h = R * 0.4142 + 0.12
        for (a, b) in (((8 - h, ry, cz - R - 0.15), (8 + h, ry + 0.3, cz - R + 0.15)), ((8 - h, ry, cz + R - 0.15), (8 + h, ry + 0.3, cz + R + 0.15)),
                       ((8 - R - 0.15, ry, cz - h), (8 - R + 0.15, ry + 0.3, cz + h)), ((8 + R - 0.15, ry, cz - h), (8 + R + 0.15, ry + 0.3, cz + h))):
            m.box(a, b, 'metal:alu', 'grading ring', rot=rot)
    for (a, b) in (((8.9, 24.2, cz - 0.1), (8 + R, 24.4, cz + 0.1)), ((8 - R, 24.2, cz - 0.1), (7.1, 24.4, cz + 0.1)),
                   ((7.9, 24.2, cz + 0.9), (8.1, 24.4, cz + R)), ((7.9, 24.2, cz - R), (8.1, 24.4, cz - 0.9))):
        m.box(a, b, 'metal:alu', 'ring stay')
    for (x, z) in ((8.9, cz), (7.1, cz), (8.0, cz + 0.9), (8.0, cz - 0.9)):
        m.box((x - 0.1, 24.2, z - 0.1), (x + 0.1, 25.3, z + 0.1), 'metal:alu', 'ring stay')
    m.g('line terminal')
    m.cyl((8.0, 25.7, cz), 0.35, 0.8, 'y', CU, 'line stud')
    m.box((7.4, 26.5, cz - 0.6), (8.6, 26.9, cz + 0.6), CU, 'line clamp')
    path(m, [(8.0, 26.7, cz + 0.6), (8.0, 26.7, 9.0), (16.2, 26.7, 9.0)], 0.3, CU, 'HV line connection')
    m.g('earth lead + counter')
    m.box((9.6, 12.2, cz - 0.3), (10.4, 12.5, cz + 0.3), 'metal:brass', 'earth terminal')
    path(m, [(10.2, 12.2, cz), (10.2, 11.8, cz), (10.2, 11.8, 2.0), (9.6, 11.8, 2.0), (9.6, 5.0, 2.0)], 0.2, 'cable:2a2a2a', 'insulated earth lead')
    for y in (7.0, 9.4):
        m.box((9.2, y, 1.6), (10.0, y + 0.3, 3.0), GALV, 'lead cleat')
    m.box((5.6, 0.6, 0), (10.4, 5.0, 1.2), 'paint:#3c3c3c', 'surge counter')
    m.box((5.4, 5.0, -0.3), (10.6, 5.25, 1.3), 'paint:#3c3c3c', 'counter hood')
    for x in (6.6, 9.4):
        m.box((x - 0.2, 2.0, 1.2), (x + 0.2, 3.0, cz - 1.0), GALV, 'counter bracket')
    sign(m, 'east', (0.2, 2.0), (1.0, 4.2), 10.4, 'label:SURGE|COUNT')
    m.box((5.9, 0.0, 0.3), (6.3, 0.6, 0.7), CU, 'counter earth tail')
    m.box((5.9, 0.0, 0.3), (6.3, 0.1, 16.0), CU, 'earth strap to grid')
    earth_rod(m, 13.6, 12.8)
    path(m, [(6.3, 0.15, 12.8), (13.6, 0.15, 12.8)], 0.12, CU, 'earth to rod')
    m.terminal_12v(9.4, 2.5, cz, face='east')
    check_panel(m, 6.0, 1.0, 10.0, 4.6, -0.6)
    m.save()


if __name__ == '__main__':
    only = set(sys.argv[1:])
    jobs = [('diesel', lambda: [diesel(s) for s in (0, 1, 2)]), ('turbine', lambda: [turbine(s) for s in (0, 1, 2)]),
            ('solar', lambda: [solar(s) for s in (0, 1, 2)]), ('wind', lambda: [wind(s) for s in (0, 1, 2)]),
            ('tower', wind_tower), ('scada', lambda: [scada(s) for s in (0, 1)]), ('auto', lambda: [auto(s) for s in (0, 1)]),
            ('earthing', lambda: [earthing(s) for s in (0, 1)]), ('relay', lambda: [relay(s) for s in (0, 1)]),
            ('isolator', lambda: [isolator(s) for s in (0, 1)]), ('arrester', lambda: [arrester(s) for s in (0, 1)])]
    for k, fn in jobs:
        if not only or k in only: fn()
