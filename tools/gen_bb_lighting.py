#!/usr/bin/env python3
"""Night lighting (her ask: "the railway looks beautiful and alive at night"): canopy LED batten, yard floodlight head
on its mast sections, lit station name sign, maintained emergency light, photocell lighting controller and the lighting
contactor panel. Models face north; controls on north surfaces sit at u = 16 - x. Live controls: see
light/TileLuminaire, TileLightController, TileLightingContactor (ids must match).
Writes models, controls, blockstates, item models and recipes (lang lines are in en_us.lang)."""
import json, math, os
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, enclosure, mains_box, GREEN, RED, AMBER, BLUE, WHITE

X = lambda x: 16 - x
GALV, DARKGREY, WHITEPAINT = 'galv', 'paint:#3c4045', 'paint:#e6e6e0'


def lamp_controls(m, sf, u0, v, step=0.8, size=0.4, extra=()):
    """the usual status row: 12 V, mains, lit, fault (blinks), under a controller"""
    lamps(m, sf, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('litl', WHITE, 'LIT'), ('flt', RED, 'FLT', True),
                  ('ctrl', BLUE, 'LCP')] + list(extra), u0, v, step=step, size=size, lab_dv=0.42)


# ================================================================================================== canopy light
def canopy_light():
    m = Model('canopy_light')
    m.g('suspension')
    for x in (3.0, 13.0):
        m.cyl((x, 15.4, 8.0), 0.9, 0.6, 'y', WHITEPAINT, 'ceiling rose')
        m.cyl((x, 11.8, 8.0), 0.12, 3.6, 'y', 'metal:steel', 'suspension wire')
        m.box((x - 0.35, 11.6, 7.65), (x + 0.35, 12.0, 8.35), 'metal:steel', 'wire gripper')
        m.box((x - 0.5, 15.2, 7.5), (x + 0.5, 15.4, 8.5), 'metal:steel', 'rose plate')
    m.cyl((8.0, 14.2, 9.6), 0.25, 1.8, 'y', 'cable:f0f0f0', 'flex to the ceiling box')
    m.box((7.0, 15.2, 8.6), (9.0, 16.0, 10.6), WHITEPAINT, 'ceiling junction box')
    m.g('batten')
    m.box((1.0, 10.2, 5.2), (15.0, 11.6, 10.8), WHITEPAINT, 'gear tray')
    m.box((0.6, 9.4, 5.0), (1.0, 11.8, 11.0), 'paint:#c8c8c4', 'end cap')
    m.box((15.0, 9.4, 5.0), (15.4, 11.8, 11.0), 'paint:#c8c8c4', 'end cap')
    m.box((1.0, 9.4, 5.6), (15.0, 10.2, 10.4), 'paint:#fff6e0', 'opal diffuser')
    for x in [1.6 + i * 1.1 for i in range(13)]:
        m.box((x, 9.3, 5.7), (x + 0.15, 9.4, 10.3), 'paint:#f2e8d0', 'diffuser prism rib')
    for z in (5.4, 10.6):
        m.box((1.0, 10.1, z - 0.2), (15.0, 10.3, z + 0.2), 'metal:steel', 'diffuser clip rail')
    for x in (2.0, 5.5, 10.5, 14.0):
        m.box((x - 0.2, 9.9, 5.0), (x + 0.2, 10.5, 5.2), 'metal:steel', 'diffuser clip')
        m.box((x - 0.2, 9.9, 10.8), (x + 0.2, 10.5, 11.0), 'metal:steel', 'diffuser clip')
    for x in [2.0 + i * 0.6 for i in range(4)] + [11.8 + i * 0.6 for i in range(4)]:
        m.box((x, 11.6, 5.6), (x + 0.3, 11.7, 10.4), 'paint:#2a2a2a', 'heat-sink slot')
    m.g('LED driver')
    m.box((3.0, 11.6, 6.4), (13.0, 14.2, 9.6), 'ral7035', 'driver box')
    m.box((3.2, 13.8, 6.3), (12.8, 14.1, 6.4), 'plate', 'driver plate', north='plate:LED DRIVER 230V')
    for x in (3.4, 12.6):
        for y in (11.9, 13.9):
            m.bolt(x, y, 6.4, 'north', 0.3)
    m.gland(5.0, 14.2, 8.0, 'up', r=0.3, length=0.5)
    m.gland(11.0, 14.2, 8.0, 'up', r=0.3, length=0.5)
    m.box((3.0, 11.6, 9.6), (13.0, 14.2, 9.7), 'label', 'rating label', south='label:LUM-C 36W')
    m.terminal_12v(13.0, 11.6, 8.0, 'east')
    mains_box(m, 3.0, 11.6, 8.0, 'west')
    f = m.surface('north', face=6.35, title='CANOPY')
    m.ctl(f, 'selector', 's_mode', X(11.6), 12.8, w=0.9, h=0.9, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    m.ctl(f, 'selector', 's_circ', X(10.0), 12.8, w=0.9, h=0.9, col='202020', label='CIRC', pos=['C1', 'C2', 'C3', 'C4'])
    lamp_controls(m, f, X(8.6), 13.1, step=0.75, size=0.32)
    m.ctl(f, 'push', 'test', X(4.2), 12.4, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    m.ctl(f, 'digits', 'circ', X(6.0), 12.1, w=1.4, h=0.5, col='ffd040', label='', fmt='%s')
    for side, face in (('north', 5.55), ('south', 16 - 10.45)):
        s = m.surface(side, face=face, title='DIFFUSER')
        m.ctl(s, 'lamp', 'lens', 8.0, 9.8, w=13.6, h=0.7, col='fff1d8', label='')
    m.save(item_gui=0.5)
    return m


# ============================================================================================== yard floodlight
def projector(m, x):
    """one floodlight projector on its yoke, tipped 22.5 degrees down toward the yard"""
    rot = {'axis': 'x', 'angle': -22.5, 'origin': [x, 24.6, 5.0]}
    m.box((x - 2.6, 22.6, 1.2), (x + 2.6, 26.6, 5.2), DARKGREY, 'projector housing', rot=rot)
    m.box((x - 2.3, 22.9, 0.9), (x + 2.3, 26.3, 1.2), 'lamp:f2f6ff', 'toughened glass', rot=rot)
    m.box((x - 2.6, 22.6, 0.7), (x + 2.6, 22.9, 1.2), DARKGREY, 'bezel', rot=rot)
    m.box((x - 2.6, 26.3, 0.7), (x + 2.6, 26.6, 1.2), DARKGREY, 'bezel', rot=rot)
    m.box((x - 2.9, 26.6, 0.2), (x + 2.9, 26.9, 5.2), DARKGREY, 'glare visor', rot=rot)
    for i in range(7):
        xx = x - 2.4 + i * 0.8
        m.box((xx, 22.8, 5.2), (xx + 0.25, 26.4, 6.0), 'fins', 'cooling fin', rot=rot)
    m.box((x - 1.2, 23.4, 6.0), (x + 1.2, 25.4, 6.8), 'ral7035', 'LED driver pod', rot=rot)
    for (xx, yy) in ((x - 2.3, 22.9), (x + 2.3, 22.9), (x - 2.3, 26.3), (x + 2.3, 26.3)):
        m.box((xx - 0.15, yy - 0.15, 0.6), (xx + 0.15, yy + 0.15, 0.75), 'metal:steel', 'bezel screw', rot=rot)
    m.box((x - 2.2, 23.0, 1.25), (x + 2.2, 26.2, 1.3), 'paint:#e8f0ff', 'LED array', rot=rot)
    # yoke
    for xx in (x - 2.9, x + 2.6):
        m.box((xx, 23.6, 4.4), (xx + 0.3, 25.6, 5.6), GALV, 'yoke cheek')
        m.cyl((xx + 0.15, 24.6, 5.0), 0.45, 0.3, 'x', 'metal:steel', 'aiming pivot') if False else m.box((xx - 0.1, 24.3, 4.7), (xx + 0.4, 24.9, 5.3), 'metal:steel', 'aiming pivot')
    m.box((x - 2.9, 23.2, 5.6), (x + 2.9, 23.6, 7.0), GALV, 'yoke base')
    m.box((x - 0.4, 22.4, 6.2), (x + 0.4, 23.2, 7.6), GALV, 'yoke stem')
    m.box((x - 1.0, 23.9, 5.3), (x + 1.0, 24.3, 5.5), 'plate', 'aiming scale', north='plate:-22')


def yard_floodlight():
    m = Model('yard_floodlight')
    m.g('mast top')
    m.cyl((8.0, 0.0, 8.0), 2.4, 15.0, 'y', GALV, 'mast tube')
    m.cyl((8.0, 0.0, 8.0), 2.9, 0.6, 'y', GALV, 'joint flange')
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        m.bolt(8.0 + math.cos(a) * 2.6, 0.6, 8.0 + math.sin(a) * 2.6, 'up', 0.35)
    m.cyl((8.0, 14.4, 8.0), 3.0, 0.6, 'y', GALV, 'head flange')
    m.cyl((10.6, 0.0, 6.0), 0.35, 15.0, 'y', 'cable:1d1d21', 'cable riser')
    for y in (2.0, 7.0, 12.0):
        m.box((10.1, y, 5.6), (11.1, y + 0.4, 6.5), 'metal:steel', 'cable cleat')
    m.g('ladder + cage')
    m.ladder(6.4, 9.6, 0.0, 16.0, 10.6)
    for y in (2.0, 6.0, 10.0, 14.0):
        m.box((5.4, y, 10.6), (5.7, y + 0.3, 14.0), GALV, 'cage hoop side')
        m.box((10.3, y, 10.6), (10.6, y + 0.3, 14.0), GALV, 'cage hoop side')
        m.box((5.4, y, 13.7), (10.6, y + 0.3, 14.0), GALV, 'cage hoop back')
    for x in (5.5, 8.0, 10.5):
        m.box((x - 0.12, 0.0, 13.75), (x + 0.12, 16.0, 13.95), GALV, 'cage strap')
    m.g('control gear')
    m.box((4.6, 2.0, 4.4), (11.4, 9.6, 6.0), 'ral7035', 'gear box')
    m.box((4.8, 2.2, 4.3), (11.2, 9.4, 4.4), 'paint:#2b2f33', 'gear box fascia')
    for x in (4.9, 11.1):
        for y in (2.3, 9.3):
            m.bolt(x, y, 4.3, 'north', 0.3)
    m.nameplate(5.2, 9.8, 10.8, 10.8, 4.4, 'FLOOD MAST')
    m.box((5.8, 11.2, 4.25), (10.2, 13.2, 4.4), 'danger', 'danger plate', north='danger:400V')
    m.gland(8.0, 2.0, 5.2, 'down', r=0.3, length=0.6)
    m.terminal_12v(4.6, 3.0, 5.2, 'west')
    mains_box(m, 11.4, 2.4, 5.4, 'east')
    f = m.surface('north', face=4.25, title='FLOODLIGHT')
    m.ctl(f, 'selector', 's_mode', X(9.8), 7.6, w=1.1, h=1.1, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    m.ctl(f, 'selector', 's_circ', X(7.8), 7.6, w=1.1, h=1.1, col='202020', label='CIRC', pos=['C1', 'C2', 'C3', 'C4'])
    m.ctl(f, 'digits', 'circ', X(5.8), 7.6, w=1.2, h=0.6, col='ffd040', label='', fmt='%s')
    lamp_controls(m, f, X(10.4), 4.8, step=0.85, size=0.4, extra=[('cell', AMBER, 'DARK')])
    m.ctl(f, 'push', 'test', X(5.6), 3.0, w=0.6, h=0.6, col='f0f0f0', label='TEST')
    m.g('service platform')
    m.box((0.0, 16.0, 0.0), (16.0, 16.4, 16.0), 'mesh', 'grating')
    for (a, b) in (((0, 15.6, -0.2), (16, 16.6, 0.2)), ((0, 15.6, 15.8), (16, 16.6, 16.2)), ((-0.2, 15.6, 0), (0.2, 16.6, 16)), ((15.8, 15.6, 0), (16.2, 16.6, 16))):
        m.box(a, b, GALV, 'platform frame')
    for (a, b) in (((0, 16.4, 0), (16, 17.2, 0.2)), ((0, 16.4, 0), (0.2, 17.2, 16)), ((15.8, 16.4, 0), (16, 17.2, 16))):
        m.box(a, b, 'paint:ral1023', 'toe board')
    m.handrail([(0.3, 13.0), (0.3, 0.3), (15.7, 0.3), (15.7, 13.0)], 16.4, h=4.4, r=0.18)
    m.box((0.12, 18.4, 0.3), (0.48, 18.7, 13.0), 'paint:ral1023', 'knee rail')
    m.box((15.52, 18.4, 0.3), (15.88, 18.7, 13.0), 'paint:ral1023', 'knee rail')
    m.box((0.3, 18.4, 0.12), (15.7, 18.7, 0.48), 'paint:ral1023', 'knee rail')
    m.box((6.0, 16.4, 10.4), (10.0, 16.6, 13.8), 'paint:#2a2a2a', 'ladder hatch')
    m.box((5.6, 16.6, 13.6), (10.4, 20.8, 13.9), 'paint:ral1023', 'self-closing gate')
    for k, (dx, dz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        m.box((8.0 + dx * 2.2 - 0.25, 12.0, 8.0 + dz * 2.2 - 0.25), (8.0 + dx * 2.2 + 0.25, 16.0, 8.0 + dz * 2.2 + 0.25), GALV, 'platform stay')
    m.g('head')
    m.cyl((8.0, 16.4, 8.0), 1.4, 6.2, 'y', GALV, 'head spigot')
    m.box((0.6, 22.6, 7.0), (15.4, 24.0, 9.0), GALV, 'crossarm')
    for x in (1.0, 15.0):
        m.box((x - 0.4, 22.4, 6.8), (x + 0.4, 24.2, 9.2), GALV, 'crossarm end plate')
    m.box((5.0, 21.0, 7.6), (11.0, 22.6, 8.4), GALV, 'crossarm gusset', rot={'axis': 'z', 'angle': 0, 'origin': [8, 21.8, 8]})
    m.cyl((3.5, 24.0, 8.6), 0.2, 1.0, 'y', 'cable:1d1d21', 'projector lead')
    m.cyl((12.5, 24.0, 8.6), 0.2, 1.0, 'y', 'cable:1d1d21', 'projector lead')
    m.box((3.5, 24.0, 8.4), (12.5, 24.4, 8.8), 'cable:1d1d21', 'crossarm cable')
    projector(m, 3.5)
    projector(m, 12.5)
    m.cyl((8.0, 24.0, 8.0), 0.35, 2.4, 'y', GALV, 'finial post')
    m.box((7.4, 26.4, 7.4), (8.6, 27.4, 8.6), 'paint:#5a0808', 'obstruction light')
    m.box((7.5, 27.4, 7.5), (8.5, 27.7, 8.5), 'paint:#202020', 'obstruction light cap')
    m.cyl((10.2, 24.0, 9.6), 0.35, 1.4, 'y', GALV, 'photocell arm')
    m.box((9.8, 25.4, 9.2), (10.6, 26.0, 10.0), 'glass', 'photocell')
    lens = m.surface('north', face=0.35, title='PROJECTORS')
    m.ctl(lens, 'lamp', 'lens', X(3.5), 23.0, w=4.4, h=3.4, col='f2f6ff', label='')
    m.ctl(lens, 'lamp', 'lens', X(12.5), 23.0, w=4.4, h=3.4, col='f2f6ff', label='')
    m.ctl(lens, 'lamp', 'obst', X(8.0), 26.9, w=1.1, h=0.9, col='ff2020', label='')
    m.save(item_gui=0.3)
    return m


def floodlight_mast():
    m = Model('floodlight_mast')
    m.g('tube')
    m.cyl((8.0, 0.0, 8.0), 2.4, 16.0, 'y', GALV, 'mast tube')
    m.cyl((8.0, 0.0, 8.0), 2.9, 0.6, 'y', GALV, 'joint flange')
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        m.bolt(8.0 + math.cos(a) * 2.6, 0.6, 8.0 + math.sin(a) * 2.6, 'up', 0.35)
    m.cyl((10.6, 0.0, 6.0), 0.35, 16.0, 'y', 'cable:1d1d21', 'cable riser')
    for y in (1.5, 6.5, 11.5):
        m.box((10.1, y, 5.6), (11.1, y + 0.4, 6.5), 'metal:steel', 'cable cleat')
        m.bolt(10.6, y + 0.2, 5.6, 'north', 0.25)
    m.cyl((5.4, 0.0, 8.0), 0.12, 16.0, 'y', 'metal:copper', 'earth tape')
    m.box((6.6, 4.0, 5.55), (9.4, 5.2, 5.6), 'plate', 'mast number', north='plate:M 01')
    m.g('ladder + cage')
    m.ladder(6.4, 9.6, 0.0, 16.0, 10.6)
    for y in (1.0, 5.0, 9.0, 13.0):
        m.box((5.4, y, 10.6), (5.7, y + 0.3, 14.0), GALV, 'cage hoop side')
        m.box((10.3, y, 10.6), (10.6, y + 0.3, 14.0), GALV, 'cage hoop side')
        m.box((5.4, y, 13.7), (10.6, y + 0.3, 14.0), GALV, 'cage hoop back')
        m.box((5.4, y, 10.4), (6.4, y + 0.3, 10.7), GALV, 'hoop bracket')
        m.box((9.6, y, 10.4), (10.6, y + 0.3, 10.7), GALV, 'hoop bracket')
    for x in (5.5, 8.0, 10.5):
        m.box((x - 0.12, 0.0, 13.75), (x + 0.12, 16.0, 13.95), GALV, 'cage strap')
    for y in (3.0, 11.0):
        m.box((6.0, y, 9.9), (10.0, y + 0.5, 10.6), GALV, 'ladder stand-off')
    m.g('access hatch')
    m.box((6.6, 6.0, 5.6), (9.4, 10.0, 5.75), GALV, 'hatch cover')
    m.box((6.4, 5.8, 5.7), (9.6, 6.0, 5.8), GALV, 'hatch frame')
    m.box((6.4, 10.0, 5.7), (9.6, 10.2, 5.8), GALV, 'hatch frame')
    for y in (6.6, 9.4):
        m.box((6.45, y - 0.3, 5.5), (6.75, y + 0.3, 5.65), 'metal:steel', 'hatch hinge')
    m.box((8.8, 7.8, 5.45), (9.2, 8.4, 5.6), 'metal:brass', 'triangle lock')
    m.box((6.8, 10.6, 5.55), (9.2, 12.2, 5.6), 'danger', 'climbing warning', north='danger:NO CLIMB')
    for k in range(8):
        a = math.radians(k * 45)
        m.bolt(8.0 + math.cos(a) * 2.7, 0.0, 8.0 + math.sin(a) * 2.7, 'down', 0.3)
    m.save(item_gui=0.5)
    return m


# =============================================================================================== station name sign
def station_name_sign():
    m = Model('station_name_sign')
    m.g('posts')
    for x in (-4.0, 20.0):
        m.box((x - 0.8, 0.0, 7.2), (x + 0.8, 15.6, 8.8), 'paint:#1d3f8a', 'post')
        m.box((x - 1.8, 0.0, 6.2), (x + 1.8, 0.4, 9.8), 'metal:steel', 'base plate')
        for (dx, dz) in ((-1.4, -1.2), (1.4, -1.2), (-1.4, 1.2), (1.4, 1.2)):
            m.bolt(x + dx, 0.4, 8.0 + dz, 'up', 0.35)
        m.box((x - 0.9, 15.6, 7.1), (x + 0.9, 16.0, 8.9), 'paint:#14306a', 'post cap')
    m.g('board')
    m.box((-8.0, 8.4, 7.0), (24.0, 14.6, 9.0), 'paint:#1d3f8a', 'sign board')
    for (y0, y1) in ((8.4, 8.8), (14.2, 14.6)):
        m.box((-8.0, y0, 6.9), (24.0, y1, 9.1), 'paint:#f2f2f2', 'white border')
    for x0 in (-8.0, 23.6):
        m.box((x0, 8.4, 6.9), (x0 + 0.4, 14.6, 9.1), 'paint:#f2f2f2', 'white border')
    for x in (-6.0, 0.0, 8.0, 16.0, 22.0):
        m.box((x - 0.15, 8.9, 6.95), (x + 0.15, 9.1, 7.0), 'metal:steel', 'board rivet')
        m.box((x - 0.15, 8.9, 9.0), (x + 0.15, 9.1, 9.05), 'metal:steel', 'board rivet')
    m.box((-6.5, 9.0, 6.95), (-5.1, 9.8, 7.0), 'paint:#d01818', 'roundel')
    m.g('lighting hood')
    m.box((-8.4, 14.6, 4.8), (24.4, 15.2, 11.2), 'paint:#14306a', 'hood')
    m.box((-8.4, 14.0, 4.8), (24.4, 14.6, 5.2), 'paint:#14306a', 'hood lip')
    m.box((-8.4, 14.0, 10.8), (24.4, 14.6, 11.2), 'paint:#14306a', 'hood lip')
    for z in (5.4, 10.2):
        m.box((-7.6, 14.4, z), (23.6, 14.6, z + 0.4), 'lamp:fff6e0', 'LED strip')
    for x in [-7.0 + i * 3.0 for i in range(11)]:
        m.box((x, 14.55, 5.3), (x + 0.3, 14.62, 10.7), 'metal:steel', 'hood rib')
    m.g('fixings')
    for x in (-4.0, 20.0):
        for y in (9.2, 13.8):
            m.box((x - 1.1, y - 0.3, 6.6), (x + 1.1, y + 0.3, 7.0), 'metal:steel', 'board clamp')
            m.box((x - 1.1, y - 0.3, 9.0), (x + 1.1, y + 0.3, 9.4), 'metal:steel', 'board clamp')
            m.bolt(x - 0.7, y, 6.6, 'north', 0.25)
            m.bolt(x + 0.7, y, 6.6, 'north', 0.25)
        m.box((x - 2.2, -0.4, 5.8), (x + 2.2, 0.0, 10.2), 'concrete', 'foundation pad')
    for x in [-7.0 + i * 1.55 for i in range(21)]:
        m.box((x, 14.35, 7.6), (x + 0.6, 14.4, 8.4), 'paint:#fff8e8', 'LED module')
    m.cyl((20.9, 0.4, 8.0), 0.3, 13.8, 'y', 'paint:#404040', 'conduit up the post')
    m.box((20.4, 14.2, 7.6), (21.4, 14.6, 8.4), 'metal:brass', 'conduit box')
    m.g('control box')
    m.box((-5.4, 2.0, 5.6), (-2.6, 6.4, 7.2), 'ral7035', 'control box')
    m.box((-5.2, 2.2, 5.5), (-2.8, 6.2, 5.6), 'paint:#2b2f33', 'control fascia')
    m.gland(-4.0, 2.0, 6.4, 'down', r=0.25, length=0.5)
    m.terminal_12v(-2.6, 2.4, 6.4, 'east') if False else m.terminal_12v(-4.0, 0.2, 9.0, 'south')
    mains_box(m, 21.0, 1.0, 8.0, 'east')
    f = m.surface('north', face=5.45, title='SIGN')
    m.ctl(f, 'selector', 's_mode', X(-4.6), 5.4, w=0.8, h=0.8, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    m.ctl(f, 'selector', 's_circ', X(-3.4), 5.4, w=0.8, h=0.8, col='202020', label='CIRC', pos=['C1', 'C2', 'C3', 'C4'])
    lamps(m, f, [('pwr', GREEN, '12V'), ('litl', WHITE, 'LIT'), ('flt', RED, 'FLT', True)], X(-4.8), 3.6, step=0.8, size=0.35, lab_dv=0.4)
    m.ctl(f, 'push', 'test', X(-3.2), 2.6, w=0.45, h=0.45, col='f0f0f0', label='TEST')
    for side, face in (('north', 4.75), ('south', 16 - 11.25)):
        s = m.surface(side, face=face, title='HOOD')
        m.ctl(s, 'lamp', 'lens', 8.0, 14.3, w=32.0, h=0.5, col='fff6e0', label='')
    m.save(item_gui=0.3)
    return m


# =================================================================================================== emergency light
def spot(m, x):
    m.cyl((x, 13.0, 12.0), 0.5, 0.8, 'y', WHITEPAINT, 'swivel')
    m.box((x - 0.3, 11.2, 11.7), (x + 0.3, 13.0, 12.3), WHITEPAINT, 'spot stem')
    rot = {'axis': 'x', 'angle': -22.5, 'origin': [x, 10.4, 7.4]}
    m.box((x - 1.2, 9.2, 3.8), (x + 1.2, 11.6, 11.2), WHITEPAINT, 'spot head', rot=rot)
    m.box((x - 1.0, 9.4, 3.6), (x + 1.0, 11.4, 3.8), 'lamp:ffffff', 'spot lens', rot=rot)
    m.box((x - 1.3, 11.6, 3.4), (x + 1.3, 11.9, 6.0), WHITEPAINT, 'spot visor', rot=rot)
    for i in range(4):
        m.box((x - 1.25, 9.5 + i * 0.55, 8.0), (x + 1.25, 9.7 + i * 0.55, 10.8), 'paint:#d0d0cc', 'spot fin', rot=rot)


def emergency_light():
    m = Model('emergency_light')
    m.g('backbox')
    m.box((1.0, 5.0, 15.6), (15.0, 15.0, 16.0), 'metal:steel', 'wall plate')
    m.box((1.6, 5.4, 12.4), (14.4, 13.0, 15.6), WHITEPAINT, 'battery housing')
    m.box((1.8, 5.6, 12.3), (14.2, 12.8, 12.4), 'paint:#f4f4ee', 'front cover')
    for x in (1.4, 14.6):
        for y in (5.4, 14.6):
            m.bolt(x, y, 15.6, 'north', 0.35)
    m.louvres(9.6, 13.8, 6.0, 8.6, 12.3, n=5)
    m.box((2.2, 11.4, 12.2), (9.0, 12.4, 12.3), 'plate', 'title plate', north='plate:EMERGENCY')
    m.box((9.6, 11.2, 12.2), (13.8, 12.6, 12.3), 'label', 'duration label', north='label:3H M')
    m.box((2.0, 5.0, 12.6), (14.0, 5.4, 15.6), WHITEPAINT, 'housing base')
    m.box((3.0, 13.0, 12.6), (13.0, 13.6, 15.4), WHITEPAINT, 'spot rail')
    m.g('battery (behind the cover)')
    for i in range(3):
        m.box((2.4 + i * 2.2, 5.6, 13.0), (4.2 + i * 2.2, 8.2, 15.4), 'battery', 'NiMH cell pack')
    m.box((9.0, 9.0, 13.0), (13.8, 11.0, 15.4), 'paint:#1a5a2a', 'charger board')
    m.g('spots')
    spot(m, 4.6)
    spot(m, 11.4)
    m.g('fixings + detail')
    for x in (2.2, 13.8):
        m.box((x - 0.3, 5.6, 12.3), (x + 0.3, 6.0, 12.4), 'metal:steel', 'cover screw')
        m.box((x - 0.3, 12.4, 12.3), (x + 0.3, 12.8, 12.4), 'metal:steel', 'cover screw')
    for x in (4.6, 11.4):
        m.box((x - 1.4, 9.0, 3.2), (x + 1.4, 9.2, 3.5), WHITEPAINT, 'lens ring', rot={'axis': 'x', 'angle': -22.5, 'origin': [x, 10.4, 7.4]})
        m.box((x - 1.4, 11.6, 3.2), (x + 1.4, 11.8, 3.5), WHITEPAINT, 'lens ring', rot={'axis': 'x', 'angle': -22.5, 'origin': [x, 10.4, 7.4]})
        m.box((x - 0.6, 12.9, 11.4), (x + 0.6, 13.1, 12.6), 'metal:steel', 'swivel lock nut')
    for i in range(3):
        m.box((2.4 + i * 1.0, 5.2, 12.5), (2.9 + i * 1.0, 5.4, 13.0), 'paint:#808080', 'cable knockout')
    m.box((2.2, 8.8, 12.2), (5.6, 9.0, 12.3), 'paint:#c8c8c4', 'control recess rim')
    m.box((6.0, 5.6, 12.2), (9.4, 5.8, 12.3), 'paint:#c8c8c4', 'control recess rim')
    m.box((13.0, 12.9, 12.2), (13.8, 13.3, 12.3), 'paint:#30a040', 'maintained mark')
    m.g('exit sign')
    m.box((3.6, 1.0, 14.4), (12.4, 4.6, 15.6), 'paint:#1a8a3a', 'exit sign body')
    m.box((4.0, 1.4, 14.3), (12.0, 4.2, 14.4), 'label', 'exit legend', north='label:EXIT >')
    m.box((7.6, 4.6, 14.8), (8.4, 5.0, 15.2), WHITEPAINT, 'sign hanger')
    m.terminal_12v(15.0, 6.0, 14.0, 'east')
    mains_box(m, 1.0, 6.0, 14.0, 'west')
    f = m.surface('north', face=12.25, title='EMERGENCY')
    m.ctl(f, 'selector', 's_mode', X(3.0), 9.6, w=1.0, h=1.0, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    m.ctl(f, 'key', 'k_maint', X(4.8), 9.6, w=1.0, h=1.0, col='c0a040', label='MAINT', pos=['MAINT', 'NON-M'])
    m.ctl(f, 'selector', 's_circ', X(6.6), 9.6, w=1.0, h=1.0, col='202020', label='CIRC', pos=['C1', 'C2', 'C3', 'C4'])
    lamps(m, f, [('chg', GREEN, 'CHG'), ('batt', GREEN, 'BATT'), ('flt', RED, 'FLT', True), ('litl', WHITE, 'LIT'), ('pwr', GREEN, '12V')],
          X(2.8), 7.2, step=0.9, size=0.4, lab_dv=0.42)
    m.ctl(f, 'digits', 'pct', X(8.4), 9.7, w=1.6, h=0.6, col='30e060', label='', fmt='%s')
    m.ctl(f, 'push', 'btest', X(8.4), 6.2, w=0.6, h=0.6, col='f0d020', label='BATT TEST')
    m.ctl(f, 'push', 'test', X(3.0), 6.0, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    lens = m.surface('north', face=3.0, title='SPOTS')
    m.ctl(lens, 'lamp', 'lens', X(4.6), 10.0, w=2.0, h=2.2, col='ffffff', label='')
    m.ctl(lens, 'lamp', 'lens', X(11.4), 10.0, w=2.0, h=2.2, col='ffffff', label='')
    ex = m.surface('north', face=14.25, title='EXIT')
    m.ctl(ex, 'lamp', 'litl', X(8.0), 1.0, w=7.8, h=0.4, col='30ff60', label='')
    m.save(item_gui=0.55)
    return m


# ========================================================================================== photocell controller
def lighting_controller():
    m = Model('lighting_controller')
    m.g('enclosure')
    enclosure(m, 3.0, 13.0, 1.0, 15.0, 11.0, 16.0)
    m.box((2.6, 0.6, 15.6), (13.4, 15.4, 16.0), 'metal:steel', 'back plate')
    m.box((3.4, 14.0, 10.85), (12.6, 14.8, 11.0), 'plate', 'title plate', north='plate:LIGHTING PC-1')
    m.box((11.4, 1.4, 10.85), (12.8, 2.6, 11.0), 'warn', 'hazard sticker', north='warn:')
    for y in (4.0, 12.0):
        m.box((12.95, y, 11.6), (13.25, y + 1.2, 12.2), 'metal:steel', 'door hinge')
    m.box((2.75, 7.0, 11.6), (3.0, 9.0, 12.2), 'paint:#202020', 'door catch')
    m.g('fixings + detail')
    m.box((2.4, 15.0, 10.4), (13.6, 15.4, 16.0), 'ral7035', 'rain hood')
    m.box((2.4, 14.6, 10.4), (13.6, 15.0, 10.6), 'ral7035', 'rain hood drip')
    for (x, y) in ((2.2, 1.6), (13.8, 1.6), (2.2, 14.4), (13.8, 14.4)):
        m.box((x - 0.5, y - 0.5, 15.4), (x + 0.5, y + 0.5, 15.6), 'metal:steel', 'mounting lug')
        m.bolt(x, y, 15.4, 'north', 0.3)
    for x in (5.0, 11.0):
        m.gland(x, 1.0, 13.6, 'down', r=0.3, length=0.6)
    m.box((12.8, 1.2, 14.6), (13.2, 1.6, 15.0), 'metal:copper', 'earth stud')
    for row in range(3):
        v = 7.0 - row * 1.5
        m.box((X(7.9), v - 0.45, 10.9), (X(5.9), v + 0.45, 11.0), 'paint:#3a3e44', 'push bezel')
    m.box((X(13.0), 1.4, 10.9), (X(7.2), 2.0, 11.0), 'label', 'lamp legend', north='label:PWR DARK OUT CUT DLY')
    m.box((X(12.4), 7.6, 10.9), (X(8.8), 11.2, 11.0), 'paint:#3a3e44', 'meter bezel')
    for (a, b) in (((3.0, 1.0, 10.95), (13.0, 1.15, 11.05)), ((3.0, 14.85, 10.95), (13.0, 15.0, 11.05)), ((3.0, 1.0, 10.95), (3.15, 15.0, 11.05)), ((12.85, 1.0, 10.95), (13.0, 15.0, 11.05))):
        m.box(a, b, 'paint:#202020', 'door gasket')
    m.box((2.6, 8.2, 11.2), (3.0, 8.8, 11.8), 'metal:steel', 'padlock hasp')
    m.box((2.5, 7.9, 11.3), (2.7, 8.5, 11.7), 'metal:brass', 'padlock')
    for y in (4.2, 12.2):
        m.cyl((13.1, y - 0.2, 11.9), 0.12, 1.6, 'y', 'metal:steel', 'hinge pin')
    m.box((3.4, 0.6, 12.0), (12.6, 1.0, 15.6), 'ral7035', 'gland plate')
    m.g('photocell')
    for k in range(8):
        a = math.radians(k * 45)
        m.box((8.0 + math.cos(a) * 1.1 - 0.15, 17.4, 13.6 + math.sin(a) * 1.1 - 0.15), (8.0 + math.cos(a) * 1.1 + 0.15, 17.9, 13.6 + math.sin(a) * 1.1 + 0.15), 'paint:#303030', 'sun shield segment')
    m.cyl((8.0, 15.0, 13.6), 0.45, 2.4, 'y', GALV, 'cell stem')
    m.box((7.0, 17.4, 12.6), (9.0, 17.8, 14.6), 'paint:#303030', 'cell base')
    m.box((7.2, 17.8, 12.8), (8.8, 18.8, 14.4), 'glass', 'cell window')
    m.box((7.6, 18.8, 13.2), (8.4, 19.1, 14.0), 'paint:#303030', 'cell cap')
    m.box((7.4, 18.0, 12.75), (8.6, 18.6, 12.8), 'paint:#8a6a20', 'CdS element')
    m.box((6.6, 15.0, 13.0), (9.4, 15.4, 14.2), 'metal:steel', 'stem boss')
    m.g('conduit')
    m.cyl((8.0, -1.0, 13.6), 0.5, 2.0, 'y', 'paint:#404040', 'conduit')
    m.box((7.2, 0.4, 12.8), (8.8, 1.0, 14.4), 'metal:brass', 'conduit coupler')
    m.terminal_12v(3.0, 2.0, 13.6, 'west')
    f = m.surface('north', face=10.95, title='LIGHTING CONTROL')
    m.ctl(f, 'digits', 'luxd', X(8.0), 12.6, w=6.4, h=1.3, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'gauge', 'lux', X(10.6), 9.4, w=3.2, h=3.2, col='f2f0e6', label='LUX', max=100)
    m.ctl(f, 'digits', 'clock', X(6.4), 10.8, w=3.6, h=0.9, col='30e060', label='', fmt='%s')
    m.ctl(f, 'digits', 'sched', X(6.4), 9.6, w=3.6, h=0.8, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'onlx', X(6.4), 8.5, w=3.6, h=0.8, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'selector', 's_mode', X(11.4), 6.0, w=1.2, h=1.2, col='202020', label='MODE', pos=['AUTO', 'ON', 'OFF'])
    m.ctl(f, 'key', 'k_cut', X(9.4), 6.0, w=1.1, h=1.1, col='c0a040', label='NIGHT CUT', pos=['OFF', 'ON'])
    for row, (a, b, lab) in enumerate((('lx_dn', 'lx_up', 'ON LUX'), ('cs_dn', 'cs_up', 'CUT FROM'), ('ce_dn', 'ce_up', 'CUT TO'))):
        v = 7.0 - row * 1.5
        m.ctl(f, 'push', a, X(7.4), v, w=0.6, h=0.6, col='e0e0e0', label='-')
        m.ctl(f, 'push', b, X(6.4), v, w=0.6, h=0.6, col='e0e0e0', label='+')
        m.ctl(f, 'label', '', X(4.6), v, w=0, h=0, col='e8e8e8', label=lab)
    lamps(m, f, [('pwr', GREEN, '12V'), ('darkl', AMBER, 'DARK'), ('outl', GREEN, 'OUT'), ('cutl', BLUE, 'CUT'), ('dly', AMBER, 'DLY', True)],
          X(12.2), 3.2, step=0.9, size=0.4, lab_dv=0.42)
    m.ctl(f, 'digits', 'lamps', X(6.4), 2.4, w=3.4, h=0.6, col='30e060', label='', fmt='%s')
    m.ctl(f, 'push', 'test', X(4.0), 2.4, w=0.5, h=0.5, col='f0f0f0', label='TEST')
    m.save(item_gui=0.5)
    return m


# ============================================================================================ lighting contactor panel
def contactor(m, x, y, z):
    """one 4-pole lighting contactor on the DIN rail behind the window"""
    m.box((x - 1.1, y, z), (x + 1.1, y + 3.0, z + 2.2), 'paint:#e8e6dc', 'contactor body')
    m.box((x - 1.0, y + 0.9, z - 0.25), (x + 1.0, y + 2.1, z), 'paint:#3a3a3a', 'arc chute')
    m.box((x - 0.3, y + 1.2, z - 0.4), (x + 0.3, y + 1.8, z - 0.25), 'paint:#e0b020', 'position flag')
    for k in range(4):
        m.box((x - 0.95 + k * 0.5, y + 2.7, z - 0.1), (x - 0.65 + k * 0.5, y + 3.0, z), 'metal:brass', 'pole screw')
        m.box((x - 0.95 + k * 0.5, y, z - 0.1), (x - 0.65 + k * 0.5, y + 0.3, z), 'metal:brass', 'pole screw')
    m.box((x - 1.1, y + 3.0, z + 0.6), (x + 1.1, y + 3.6, z + 1.6), 'cable:1d1d21', 'load cables')


def lighting_contactor():
    m = Model('lighting_contactor')
    m.g('cabinet')
    m.box((0.2, 0.0, 7.2), (15.8, 1.2, 16.0), 'paint:#2a2d30', 'plinth')
    m.box((0.4, 1.2, 7.6), (15.6, 30.0, 16.0), 'ral7035', 'cabinet')
    m.box((0.2, 30.0, 7.4), (15.8, 30.6, 16.0), 'ral7035', 'roof')
    m.box((0.6, 1.4, 7.4), (15.4, 29.8, 7.6), 'ral7035', 'door leaf')
    for y in (4.0, 15.0, 26.0):
        m.box((15.4, y, 7.3), (15.7, y + 1.6, 7.9), 'metal:steel', 'door hinge')
    m.box((1.0, 13.0, 7.0), (1.6, 17.0, 7.4), 'paint:#202020', 'door handle')
    m.box((1.1, 14.4, 6.8), (1.5, 15.6, 7.0), 'metal:brass', 'cam lock')
    for x in (3.0, 13.0):
        m.box((x - 0.6, 30.6, 11.2), (x + 0.6, 31.6, 12.4), 'metal:steel', 'lifting eye')
    for z in (9.6, 12.0, 14.4):
        m.louvres(0.0, 0.0, 0, 0, 0, n=0) if False else None
    for side_x in (0.25, 15.75):
        for i in range(6):
            y = 3.0 + i * 0.7
            m.box((side_x - 0.15, y, 9.0), (side_x + 0.15, y + 0.35, 14.6), 'louvre', 'side vent')
    m.g('window + gear')
    m.box((1.4, 21.0, 7.3), (14.6, 28.6, 7.4), 'glass', 'inspection window')
    for (a, b) in (((1.2, 20.8, 7.2), (14.8, 21.0, 7.4)), ((1.2, 28.6, 7.2), (14.8, 28.8, 7.4)), ((1.2, 21.0, 7.2), (1.4, 28.6, 7.4)), ((14.6, 21.0, 7.2), (14.8, 28.6, 7.4))):
        m.box(a, b, 'paint:#202020', 'window seal')
    m.box((1.4, 21.4, 10.6), (14.6, 28.4, 10.8), 'paint:#d8d4c8', 'mounting plate')
    m.box((1.6, 22.6, 10.2), (14.4, 23.0, 10.6), 'metal:steel', 'DIN rail')
    m.box((1.6, 26.2, 10.2), (14.4, 26.6, 10.6), 'metal:steel', 'DIN rail')
    for k in range(4):
        contactor(m, 3.0 + k * 3.3, 22.4, 8.2)
    for k in range(10):
        x = 2.0 + k * 1.25
        m.box((x, 26.0, 8.6), (x + 1.0, 28.2, 10.2), 'paint:#f0f0ea', 'MCB')
        m.box((x + 0.3, 27.4, 8.4), (x + 0.7, 27.9, 8.6), 'paint:#2050c0' if k < 8 else 'paint:#d02020', 'MCB toggle')
    m.box((1.6, 21.5, 9.6), (14.4, 22.2, 10.6), 'paint:#5a5a5a', 'terminal strip')
    for k in range(16):
        m.box((1.8 + k * 0.78, 21.6, 9.5), (2.3 + k * 0.78, 22.1, 9.6), 'metal:brass', 'terminal')
    m.g('front')
    m.box((2.0, 29.0, 7.25), (14.0, 29.6, 7.4), 'plate', 'title plate', north='plate:LIGHTING CONTACTOR LCP-1')
    m.box((11.6, 2.0, 7.25), (14.6, 4.8, 7.4), 'danger', 'danger plate', north='danger:400V')
    for k, lab in enumerate(('C1 ALL NIGHT', 'C2 ALL NIGHT', 'C3 NIGHT CUT', 'C4 NIGHT CUT')):
        x = X(2.6 + k * 3.6)
        m.box((x - 1.5, 15.6, 7.3), (x + 1.5, 16.6, 7.4), 'label', 'circuit label', north='label:' + lab)
    m.gland(4.0, 0.0, 12.0, 'down', r=0.6, length=1.0)
    m.gland(8.0, 0.0, 12.0, 'down', r=0.6, length=1.0)
    m.gland(12.0, 0.0, 12.0, 'down', r=0.6, length=1.0)
    m.terminal_12v(0.4, 6.0, 12.0, 'west')
    mains_box(m, 15.6, 6.0, 12.0, 'east')
    f = m.surface('north', face=7.25, title='LCP')
    for k in range(4):
        u = 2.6 + k * 3.6
        m.ctl(f, 'lamp', 'k%d' % (k + 1), u, 14.4, w=0.9, h=0.9, col=GREEN, label='IN')
        m.ctl(f, 'selector', 's_k%d' % (k + 1), u, 12.0, w=1.6, h=1.6, col='202020', label='K%d' % (k + 1), pos=['AUTO', 'HAND', 'OFF'])
        m.ctl(f, 'digits', 'n%d' % (k + 1), u, 9.6, w=2.6, h=0.9, col='30e060', label='', fmt='%s')
    lamps(m, f, [('pwr', GREEN, '12V'), ('ctrl', BLUE, 'PC'), ('cell', AMBER, 'DARK'), ('flt', RED, 'LAMP FLT', True)], 2.4, 18.4, step=1.6, size=0.6)
    pushes(m, f, [('scan', 'e0e0e0', 'SCAN'), ('ack', 'f0d020', 'ACK'), ('test', 'f0f0f0', 'TEST')], 9.6, 18.4, step=1.7, size=0.8)
    m.ctl(f, 'digits', 'wire', 5.0, 6.6, w=5.0, h=0.9, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'tot', 11.0, 6.6, w=4.0, h=0.9, col='ffd040', label='', fmt='%s')
    m.save(item_gui=0.4)
    return m


RECIPES = {
    'canopy_light': (['ISI', 'GLG'], {'I': 'minecraft:iron_ingot', 'S': 'irextras:part@2', 'G': 'minecraft:glass_pane', 'L': 'minecraft:redstone_lamp'}),
    'yard_floodlight': (['LIL', 'GSG', ' I '], {'L': 'minecraft:redstone_lamp', 'I': 'minecraft:iron_block', 'G': 'minecraft:glass_pane', 'S': 'irextras:part@2'}),
    'floodlight_mast': (['IBI', ' I ', 'IBI'], {'I': 'minecraft:iron_ingot', 'B': 'minecraft:iron_bars'}),
    'station_name_sign': (['LLL', 'SBS', 'I I'], {'L': 'minecraft:redstone_lamp', 'S': 'minecraft:sign', 'B': 'minecraft:lapis_block', 'I': 'minecraft:iron_ingot'}),
    'emergency_light': (['LIL', 'RCR', ' D '], {'L': 'minecraft:redstone_lamp', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone', 'C': 'irextras:part@2', 'D': 'minecraft:daylight_detector'}),
    'lighting_controller': ([' D ', 'ICI', 'IRI'], {'D': 'minecraft:daylight_detector', 'I': 'minecraft:iron_ingot', 'C': 'irextras:part@2', 'R': 'minecraft:clock'}),
    'lighting_contactor': (['ICI', 'PRP', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': 'irextras:part@2', 'P': 'minecraft:piston', 'R': 'minecraft:redstone_block'}),
}
DATA = {}
FACING = ['canopy_light', 'yard_floodlight', 'floodlight_mast', 'station_name_sign', 'emergency_light', 'lighting_controller', 'lighting_contactor']


def ing(v):
    if '@' in v:
        i, d = v.split('@')
        return {'item': i, 'data': int(d)}
    return {'item': v, 'data': DATA[v]} if v in DATA else {'item': v}


if __name__ == '__main__':
    built = [canopy_light(), yard_floodlight(), floodlight_mast(), station_name_sign(), emergency_light(), lighting_controller(), lighting_contactor()]
    for n in FACING:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    # the invisible light block: an empty model for every level
    json.dump({'textures': {'particle': 'minecraft:blocks/glass'}, 'elements': []}, open(os.path.join(ASSETS, 'models/block/light_helper.json'), 'w'), indent=1)
    json.dump({'variants': {'level=%d' % i: {'model': 'irextras:light_helper'} for i in range(16)}}, open(os.path.join(ASSETS, 'blockstates/light_helper.json'), 'w'), indent=1)
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'minecraft:items/glowstone_dust'}}, open(os.path.join(ASSETS, 'models/item/light_helper.json'), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        count = 4 if n == 'floodlight_mast' else 1
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': count}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    for m in built:
        print('%-22s %3d boxes' % (m.name, len(m.boxes)))
