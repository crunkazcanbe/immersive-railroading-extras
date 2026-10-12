#!/usr/bin/env python3
"""Batch 6a (her ask: "complex buttons and knobs and stuff, screens", Blockbench models, real looking, everything
functional): the construction and works machines - Track Laying Machine, Tunnel Boring Machine (operator cabin +
cutterhead shield), Ballast Tamper (09-3X style), Locomotive Works desk + Erecting Bay - and the Road-Rail Hi-Rail
Truck (body + guide-wheel gear, drawn live by build6a/client/B6Client). Models face north (the operator's side),
controls by u = 16 - x on north faces. Live control ids match build6a/Tile*.java. Also writes blockstates, item
models, recipes and lang lines."""
import json, math, os
from bbkit import Model, ASSETS
import cabkit
from gen_bb_economy import X
from gen_bb_depot import lamp_row, push_row, estop_plate
from gen_bb_steam import gauges, lab, screen, beacon


# ------------------------------------------------------------------------------------------------------ helpers
def screen4(m, sf, x0, x1, y0, y1, z, page_pos, col='7cf29a'):
    """a 4-line display in a bezel on a north face (model x0..x1, y0..y1 at z), its PAGE selector to the right"""
    screen(m, x0, x1, y0, y1, z, 'screen')
    h = (y1 - y0) / 4.0
    for i in range(4):
        m.ctl(sf, 'digits', 'line%d' % (i + 1), X((x0 + x1) / 2), y1 - h * (i + 0.5), w=(x1 - x0) - 0.6, h=h * 0.8,
              col=col if i else 'ffd040', label='', fmt='%s')
    m.ctl(sf, 'selector', 'page', X(x0 - 1.6), (y0 + y1) / 2 + 0.3, w=1.2, h=1.2, col='202020', label='PAGE', pos=page_pos)


def counter(m, sf, cid, x, y, w, text, fmt='%s', col='ffd040'):
    """a 7-segment counter window with an engraved label under it"""
    m.box((x - w / 2 - 0.25, y - 0.75, sf['face'] + 0.02), (x + w / 2 + 0.25, y + 0.75, sf['face'] + 0.3), 'paint:#141414', cid + ' window')
    m.ctl(sf, 'digits', cid, X(x), y, w=w, h=1.0, col=col, label='', fmt=fmt)
    lab(m, sf, X(x), y - 1.25, text)


def tread(m, x0, x1, z0, z1, y0=0.0, h=3.6, name='crawler'):
    """a crawler track: frame, sprocket + idler, rollers and the plates round it (x0..x1 wide, z0..z1 long)"""
    m.box((x0 + 0.4, y0 + 0.9, z0 + 1.2), (x1 - 0.4, y0 + h - 0.9, z1 - 1.2), 'paint:#2a2c2e', name + ' frame')
    r = h / 2
    m.cyl((x0, y0 + r, z0 + r), r, x1 - x0, 'x', 'paint:#1e1e20', name + ' idler')
    m.cyl((x0, y0 + r, z1 - r), r, x1 - x0, 'x', 'paint:#1e1e20', name + ' sprocket')
    m.cyl((x0 - 0.15, y0 + r, z1 - r), r * 0.45, x1 - x0 + 0.3, 'x', 'metal:steel', name + ' final drive')
    z = z0 + r
    while z < z1 - r:
        m.box((x0, y0, z), (x1, y0 + 0.35, z + 0.75), 'paint:#3a3a3c', 'track plate')
        m.box((x0, y0 + h - 0.35, z), (x1, y0 + h, z + 0.75), 'paint:#3a3a3c', 'track plate')
        z += 0.95
    for k in range(3):
        zz = z0 + r + (z1 - z0 - 2 * r) * (k + 1) / 4
        m.cyl((x0 + 0.2, y0 + 0.9, zz), 0.55, x1 - x0 - 0.4, 'x', 'metal:steel', 'track roller')


def wheelset(m, x0, x1, z, r=1.4, name='wheelset'):
    m.cyl((x0, r, z), r, 0.6, 'x', 'metal:steel', name + ' wheel')
    m.cyl((x1 - 0.6, r, z), r, 0.6, 'x', 'metal:steel', name + ' wheel')
    m.cyl((x0 + 0.6, r, z), 0.35, x1 - x0 - 1.2, 'x', 'paint:#2a2a2a', name + ' axle')
    for x in (x0 - 0.7, x1 + 0.1):
        m.box((x, r - 0.7, z - 0.9), (x + 0.6, r + 0.7, z + 0.9), 'paint:#3a3f44', 'axlebox')


def handrail_run(m, x0, x1, z, y, h=3.0):
    m.handrail([(x0, z), (x1, z)], y, h=h)


def sink(m, dy):
    """drop the whole model (boxes, rotation origins, live controls) by dy px - a machine set down ON the track, its
    block one above the rail block, its wheels on the rail heads"""
    out = []
    for (nm, lo, hi, rot, fm, grp) in m.boxes:
        lo = [lo[0], lo[1] - dy, lo[2]]; hi = [hi[0], hi[1] - dy, hi[2]]
        if rot: rot = dict(rot); rot['origin'] = [rot['origin'][0], rot['origin'][1] - dy, rot['origin'][2]]
        out.append((nm, lo, hi, rot, fm, grp))
    m.boxes[:] = out
    for sf in m.surfaces:
        if 'pivot' in sf: sf['pivot'] = round(sf['pivot'] - dy, 3)
        for c in sf['controls']: c['v'] = round(c['v'] - dy, 3)


def stores_zone(m, side, face, u, v, w, h, cid):
    sf = m.surface(side, face=face, title=cid.upper())
    m.ctl(sf, 'zone', cid, u, v, w=w, h=h, col='000000', label='')


# ============================================================================================== Track Laying Machine
def track_layer():
    """front power unit of a new-track-construction train: crawlers on the formation (south, the way it lays), rail
    bogie on the laid track (north), engine room, sleeper conveyor, rail threader portal, ballast hopper, clip guns
    and the operator's desk facing back down the line"""
    m = Model('track_layer')
    m.g('crawlers')
    tread(m, -10.0, -4.0, 14.0, 31.6, name='left crawler')
    tread(m, 20.0, 26.0, 14.0, 31.6, name='right crawler')
    m.g('rail bogie')
    for z in (-10.5, -4.5):
        wheelset(m, 0.9, 15.1, z, r=1.5)
    m.box((-0.6, 2.2, -13.0), (16.6, 3.8, -2.0), 'paint:#2f3338', 'bogie frame')
    for x in (-0.8, 16.0):
        m.box((x, 1.0, -9.2), (x + 0.8, 3.0, -5.8), 'paint:#c8a020', 'primary spring')
    m.g('main frame')
    m.box((-11.0, 3.8, -14.0), (27.0, 6.4, 31.0), 'paint:#e0b818', 'main frame')
    m.box((-11.2, 3.6, -14.2), (27.2, 4.0, 31.2), 'paint:#2a2a2a', 'frame sole bar')
    for z in (-14.0, 31.0):
        m.box((-11.0, 2.4, z - 0.4), (27.0, 3.8, z + 0.4), 'haz', 'headstock stripes')
    for x in (-11.0, 26.4):
        for z in range(-12, 30, 4):
            m.bolt(x if x < 0 else x + 0.6, 5.1, z, face='west' if x < 0 else 'east', s=0.4)
    m.g('engine room')
    m.box((-9.0, 6.4, -12.0), (25.0, 20.0, 3.0), 'paint:#e8c020', 'engine room')
    m.box((-9.4, 20.0, -12.4), (25.4, 20.8, 3.4), 'paint:#3a3a3a', 'engine room roof')
    m.louvres(-8.0, 2.0, 9.0, 17.0, -12.05, n=7)
    m.louvres(14.0, 24.0, 9.0, 17.0, -12.05, n=7)
    for x in (-9.05, 25.0):
        m.box((x, 8.0, -10.0), (x + 0.05, 18.0, 1.0), 'louvre', 'side louvres')
    m.cyl((20.0, 20.8, -2.0), 0.9, 6.0, 'y', 'paint:#2a2a2a', 'exhaust stack')
    m.cyl((20.0, 26.6, -2.0), 1.1, 0.5, 'y', 'paint:#1a1a1a', 'rain cap')
    m.box((-4.0, 20.8, -8.0), (6.0, 22.0, 0.0), 'fins', 'radiator top')
    m.cyl((1.0, 22.0, -4.0), 3.0, 0.4, 'y', 'mesh', 'radiator fan guard')
    m.box((-9.6, 7.0, -6.0), (-9.0, 12.0, 0.0), 'paint:#3a3f44', 'fuel tank', west='label:DIESEL')
    m.cyl((-10.0, 12.0, -3.0), 0.5, 0.6, 'y', 'paint:#c82020', 'filler cap')
    m.box((25.0, 7.0, -10.0), (26.0, 11.0, -5.0), 'paint:#2a2a2a', 'battery box', east='label:24V BATT')
    m.box((26.0, 9.0, -8.2), (26.4, 10.4, -6.8), 'paint:#c82020', 'battery isolator')
    m.box((25.0, 12.0, -10.0), (26.0, 18.0, -2.0), 'paint:#3a4a5a', 'hydraulic tank', east='label:HYD OIL')
    m.cyl((25.2, 14.0, -1.0), 0.3, 14.0, 'z', 'cable:202020', 'hydraulic hose')
    m.cyl((25.6, 13.0, -1.0), 0.3, 14.0, 'z', 'cable:202020', 'hydraulic hose')
    m.g('sleeper conveyor')
    m.box((-1.0, 16.0, 3.0), (17.0, 17.0, 28.0), 'paint:#3a3f44', 'conveyor bed')
    for z in range(4, 28, 2):
        m.cyl((-1.0, 17.2, z), 0.35, 18.0, 'x', 'metal:steel', 'conveyor roller')
    for z in (6.0, 12.0, 18.0, 24.0):
        m.box((-0.5, 17.4, z), (16.5, 18.6, z + 1.6), 'wood:#4a3420', 'creosoted sleeper')
    for x in (-1.6, 17.0):
        m.box((x, 17.0, 3.0), (x + 0.6, 19.0, 28.0), 'paint:#e0b818', 'conveyor side rail')
    for z in (6.0, 14.0, 22.0):
        for x in (-1.0, 16.2):
            m.box((x, 6.4, z), (x + 0.8, 16.0, z + 0.8), 'paint:#e0b818', 'conveyor stanchion')
    m.g('rail racks + sleeper magazine')
    for x in (-8.0, 22.0):
        m.box((x, 6.4, 4.0), (x + 2.0, 14.0, 13.0), 'paint:#2f3338', 'rail rack' if x < 0 else 'sleeper magazine')
        for y in (7.4, 9.0, 10.6, 12.2):
            if x < 0: m.box((x + 0.2, y, 4.2), (x + 1.8, y + 0.8, 12.8), 'metal:steel', 'rail section')
            else: m.box((x + 0.2, y, 4.4), (x + 1.8, y + 1.2, 12.6), 'wood:#4a3420', 'stacked sleepers')
    m.g('ballast hopper')
    for i, y in enumerate((6.4, 8.4, 10.4, 12.4)):
        m.box((1.0 + 1.2 - i * 0.6, y, 13.5 - i * 0.4), (15.0 - 1.2 + i * 0.6, y + 2.0, 19.5 + i * 0.4), 'paint:#5a5a50', 'hopper course')
    m.box((6.0, 4.0, 15.6), (10.0, 6.4, 17.4), 'paint:#3a3f44', 'discharge gate')
    m.box((6.4, 3.2, 15.8), (9.6, 4.0, 17.2), 'paint:#2a2a2a', 'gate chute')
    m.g('threader portal')
    for x in (-3.0, 18.0):
        m.box((x, 6.4, 27.0), (x + 1.4, 26.0, 28.4), 'paint:#e0b818', 'portal leg')
    m.box((-3.0, 24.6, 26.8), (19.4, 26.6, 28.6), 'paint:#e0b818', 'portal beam')
    m.box((-2.0, 26.6, 27.2), (18.4, 27.0, 28.2), 'haz', 'portal beam stripe')
    for x in (2.6, 12.4):
        m.cyl((x, 22.0, 27.7), 0.9, 1.0, 'x', 'paint:#2a2a2a', 'threader roller')
        m.box((x - 0.2, 4.0, 18.0), (x + 1.2, 4.8, 31.8), 'metal:steel', 'rail being threaded')
        m.box((x - 0.4, 3.2, 29.0), (x + 1.4, 4.4, 31.0), 'paint:#3a3f44', 'rail clamp')
        m.cyl((x + 0.5, 6.4, 29.8), 0.35, 4.0, 'y', 'metal:steel', 'clamp ram')
    m.box((0.0, 2.0, 22.0), (16.0, 3.2, 24.0), 'wood:#4a3420', 'sleeper being placed')
    m.cyl((2.0, 3.2, 23.0), 0.4, 8.0, 'y', 'metal:steel', 'sleeper tong')
    m.cyl((14.0, 3.2, 23.0), 0.4, 8.0, 'y', 'metal:steel', 'sleeper tong')
    for x in (-1.5, 15.5):
        m.box((x, 8.0, 24.0), (x + 2.0, 12.0, 26.0), 'paint:#c82020', 'clip gun')
        m.cyl((x + 1.0, 4.4, 25.0), 0.3, 3.6, 'y', 'metal:steel', 'clip gun barrel')
    m.g('access')
    handrail_run(m, -10.0, 26.0, 31.0, 6.4, h=3.0)
    m.ladder(-11.8, -10.2, 0.0, 6.4, 0.0)
    beacon(m, -6.0, 20.8, -6.0)
    beacon(m, 22.0, 20.8, -6.0)
    for x in (-8.0, 22.0):
        m.box((x, 20.8, -12.4), (x + 2.0, 21.8, -11.6), 'paint:#2a2a2a', 'work light')
        m.box((x + 0.2, 21.0, -12.5), (x + 1.8, 21.6, -12.4), 'lamp:fff4d0', 'work light lens')
    m.box((-8.0, 15.6, -12.1), (8.0, 19.4, -12.0), 'danger:DANGER|MOVING MACHINERY', 'danger sign')
    m.nameplate(10.0, 18.0, 24.0, 19.6, -12.0, 'TRACK LAYER TL-1')
    # ---- operator desk (north): reads the screens, sets the panel, lays
    m.g('operator desk')
    m.box((-9.0, 0.0, -15.6), (25.0, 11.0, -12.0), 'paint:#2f3d4a', 'desk body')
    m.box((-9.0, 11.0, -14.4), (25.0, 15.0, -12.0), 'paint:#2b3743', 'desk upstand')
    m.box((-9.2, 15.0, -15.0), (25.2, 15.6, -12.0), 'paint:#1c242c', 'desk hood')
    m.box((-8.8, 0.0, -15.8), (24.8, 0.8, -15.6), 'paint:#1c1d20', 'kick plate')
    m.terminal_12v(25.0, 2.0, -13.8, 'east')
    f = m.surface('north', face=-15.62, title='LAYING DESK')
    screen4(m, f, -6.0, 5.6, 6.2, 10.6, -15.6, ['STATUS', 'STORES', 'RUN', 'LOG'])
    counter(m, f, 'metres', 10.0, 9.6, 3.4, 'METRES LAID', fmt='%s M')
    counter(m, f, 'panels', 15.0, 9.6, 2.6, 'PANELS')
    gauges(m, f, [('fuelg', 'DIESEL'), ('batg', 'BATT'), ('rpmg', 'RPM'), ('oilg', 'OIL'), ('coolg', 'COOLANT'), ('railg', 'RAILS'), ('tieg', 'SLEEPERS'), ('progg', 'PANEL')],
           X(-6.0), 4.0, step=-2.4, size=1.8)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('eng', '30e060', 'ENGINE'), ('batl', 'ffb020', 'BATT LOW'), ('fuell', 'ffb020', 'FUEL LOW'),
                    ('raill', 'ff3030', 'RAILS'), ('tiel', 'ff3030', 'SLEEPERS'), ('balll', 'ffb020', 'BALLAST')], X(12.0), 6.6, step=-1.9, size=0.55)
    lamp_row(m, f, [('work', '8ad4ff', 'LAYING', True), ('fault', 'ff3030', 'FAULT', True), ('autol', '8ad4ff', 'AUTO'), ('obst', 'ff3030', 'OBSTRUCT'),
                    ('form', 'ff3030', 'FORMATION'), ('done', '30e060', 'DONE')], X(12.0), 5.0, step=-1.9, size=0.55)
    push_row(m, f, [('lay', '1fa34a', 'LAY'), ('start', '2a6ad0', 'AUTO'), ('stop', 'd02020', 'STOP'), ('reset', 'e0c020', 'RESET'), ('test', 'b0b0b0', 'TEST')],
             X(12.0), 2.2, step=-2.4, size=1.0)
    u = m.surface('north', face=-14.42, title='LAYING UPSTAND')
    m.ctl(u, 'key', 'k_batt', X(-6.4), 13.4, w=1.1, h=1.1, col='c02020', label='BATTERY', pos=['OFF', 'ON'])
    m.ctl(u, 'key', 'k_engine', X(-3.4), 13.4, w=1.1, h=1.1, col='c0a040', label='ENGINE', pos=['OFF', 'RUN'])
    m.ctl(u, 'selector', 's_gauge', X(0.2), 13.4, w=1.2, h=1.2, col='202020', label='GAUGE', pos=['STD', 'CAPE', '3FT', 'MIN'])
    m.ctl(u, 'selector', 's_panel', X(3.8), 13.4, w=1.2, h=1.2, col='202020', label='PANEL', pos=['6M', '10M', '16M', '24M'])
    m.ctl(u, 'selector', 's_curve', X(7.4), 13.4, w=1.2, h=1.2, col='202020', label='CURVE', pos=['STR', 'LEFT', 'RIGHT'])
    m.ctl(u, 'selector', 's_radius', X(11.0), 13.4, w=1.2, h=1.2, col='202020', label='RADIUS', pos=['100', '200', '400', '50', '25'])
    m.ctl(u, 'selector', 's_mode', X(14.6), 13.4, w=1.2, h=1.2, col='202020', label='MODE', pos=['MAN', 'AUTO'])
    m.ctl(u, 'selector', 's_target', X(18.0), 13.4, w=1.2, h=1.2, col='202020', label='PANELS', pos=['5', '10', '25', '50', '99'])
    m.ctl(u, 'lever', 'm_ballast', X(21.0), 12.4, w=0.8, h=2.4, col='e8c020', label='BALLAST', pos=['OFF', 'DROP'])
    estop_plate(m, u, X(23.4), 13.4)
    # ---- the stores: rail racks (west / east), sleeper magazine (conveyor end), ballast hopper, clip bins
    stores_zone(m, 'west', -8.02, 8.5, 10.0, 9.0, 7.6, 'rails')
    stores_zone(m, 'east', 16 - 24.02, 16 - 8.5, 10.0, 9.0, 7.6, 'ties')
    stores_zone(m, 'east', 16 - 15.02, 16 - 16.5, 10.0, 5.0, 6.0, 'ballast')
    m.box((15.5, 8.0, 21.0), (17.5, 11.0, 23.6), 'paint:#3a4a5a', 'clip bin', east='label:CLIPS')
    stores_zone(m, 'east', 16 - 17.52, 16 - 22.3, 9.5, 2.6, 3.0, 'clips')
    m.save(item_gui=0.32)
    return m


# =================================================================================================== Tunnel Boring Machine
def tbm():
    """the TBM: the operator's cabin on the backup gantry (north, facing back down the tunnel) and the shield with its
    cutterhead (south - the face it bores), thrust rams, segment erector, screw conveyor and the MV switchgear"""
    m = Model('tbm')
    m.g('shield')
    for i, (r, z0, z1) in enumerate(((15.0, 22.0, 30.0), (14.6, 14.0, 22.0), (14.2, 6.0, 14.0))):
        m.cyl((8.0, 15.5, z0), r, z1 - z0, 'z', 'paint:#e0b818' if i else 'paint:#d8a818', 'shield can')
    for z in (14.0, 22.0):
        m.cyl((8.0, 15.5, z - 0.3), 15.2, 0.6, 'z', 'paint:#3a3a3a', 'shield joint')
    m.g('cutterhead')
    m.cyl((8.0, 15.5, 30.0), 15.4, 1.6, 'z', 'paint:#3a3f44', 'cutterhead')
    for k in range(8):
        a = math.radians(k * 45)
        x, y = 8.0 + math.sin(a) * 10.0, 15.5 + math.cos(a) * 10.0
        m.cyl((x, y, 31.6), 1.1, 0.4, 'z', 'metal:steel', 'disc cutter')
        x2, y2 = 8.0 + math.sin(a) * 5.0, 15.5 + math.cos(a) * 5.0
        m.cyl((x2, y2, 31.6), 0.9, 0.4, 'z', 'metal:steel', 'disc cutter')
    for k in range(4):
        a = math.radians(k * 45 + 22.5)
        m.box((7.4, 15.5, 31.55), (8.6, 29.0, 31.95), 'paint:#2a2a2a', 'muck bucket spoke', rot={'axis': 'z', 'angle': [-45, -22.5, 22.5, 45][k], 'origin': [8, 15.5, 31.7]})
    m.cyl((8.0, 15.5, 31.6), 2.0, 0.4, 'z', 'metal:steel', 'centre cutter')
    m.g('thrust rams')
    for (x, y) in ((-3.0, 6.0), (19.0, 6.0), (-3.0, 25.0), (19.0, 25.0), (8.0, 28.0), (8.0, 3.0)):
        m.cyl((x, y, 2.0), 0.9, 12.0, 'z', 'paint:#2a5a8a', 'thrust cylinder')
        m.cyl((x, y, 14.0), 0.45, 4.0, 'z', 'metal:steel', 'thrust ram rod')
        m.box((x - 1.2, y - 1.2, 1.4), (x + 1.2, y + 1.2, 2.0), 'paint:#3a3a3a', 'ram shoe')
    m.g('erector')
    m.cyl((8.0, 15.5, 4.0), 4.0, 2.0, 'z', 'paint:#c82020', 'erector ring')
    m.box((6.6, 15.5, 4.2), (9.4, 26.0, 5.8), 'paint:#c82020', 'erector arm')
    m.box((4.0, 25.0, 3.8), (12.0, 27.0, 6.2), 'concrete', 'segment in the grab')
    m.g('screw conveyor')
    m.cyl((8.0, 3.0, -14.0), 1.4, 28.0, 'z', 'paint:#5a5a50', 'screw conveyor')
    for z in range(-12, 14, 3):
        m.cyl((8.0, 3.0, z), 1.6, 0.4, 'z', 'paint:#3a3a3a', 'screw flange')
    m.box((6.0, 0.0, -15.6), (10.0, 2.4, -13.8), 'paint:#3a3f44', 'muck belt head')
    m.box((6.4, 2.4, -15.4), (9.6, 2.8, 2.0), 'paint:#2a2a2a', 'muck belt')
    m.g('backup gantry')
    for x in (-6.0, 20.6):
        m.box((x, 0.0, -14.0), (x + 1.4, 20.0, 0.0), 'paint:#e0b818', 'gantry leg')
        m.cyl((x + 0.7, 0.8, -12.0), 0.8, 1.4, 'x', 'paint:#2a2a2a', 'gantry wheel')
        m.cyl((x + 0.7, 0.8, -2.0), 0.8, 1.4, 'x', 'paint:#2a2a2a', 'gantry wheel')
    m.box((-6.0, 20.0, -14.0), (22.0, 21.4, 0.0), 'paint:#e0b818', 'gantry deck')
    m.box((-5.0, 21.4, -12.0), (4.0, 26.0, -4.0), 'paint:#5a6a7a', 'MV transformer', north='label:11KV/690V')
    for x in (-3.6, -0.4, 2.6):
        m.bushing(x, -8.0, 26.0, 2.0)
    m.box((14.0, 21.4, -13.0), (21.0, 24.0, -2.0), 'paint:#3a3f44', 'cable drum frame')
    m.cyl((14.0, 24.6, -7.5), 2.4, 7.0, 'x', 'paint:#2a2a2a', 'MV cable drum')
    m.cyl((13.6, 24.6, -7.5), 2.8, 0.4, 'x', 'paint:#c82020', 'drum flange')
    m.cyl((21.0, 24.6, -7.5), 2.8, 0.4, 'x', 'paint:#c82020', 'drum flange')
    m.cyl((17.5, 22.0, -14.0), 0.5, 14.0, 'z', 'cable:202020', 'trailing MV cable')
    m.cyl((-1.0, 21.4, 0.0), 0.6, 6.0, 'z', 'cable:1d1d21', 'vent duct')
    m.box((-6.0, 16.0, -14.1), (6.0, 19.0, -14.0), 'danger:DANGER|11000 VOLTS', 'MV danger sign')
    beacon(m, 20.0, 21.4, -12.0)
    m.g('switchgear')
    m.box((-6.0, 0.0, -14.0), (1.0, 14.0, -10.0), 'paint:#3a4a5a', 'MV switchgear')
    cabkit.fit(m, -5.4, 0.4, 0.8, 13.4, -14.0, col='paint:#3a4a5a', title='MV PANEL|TBM 11KV', volts='11KV 3PH', hv=True, fns=(('ctl', '12V CTL'), ('scr', 'SCREEN')))
    # ---- the operator cabin (north face): screens, gauges, steering, cutterhead controls
    m.g('operator cabin')
    m.box((2.0, 0.0, -15.6), (21.0, 13.0, -10.0), 'paint:#2f3d4a', 'cabin desk')
    m.box((2.0, 13.0, -14.0), (21.0, 19.6, -10.0), 'paint:#2b3743', 'cabin upstand')
    m.box((1.8, 19.6, -15.0), (21.2, 20.0, -10.0), 'paint:#1c242c', 'cabin hood')
    m.box((2.2, 0.0, -15.8), (20.8, 0.8, -15.6), 'paint:#1c1d20', 'kick plate')
    m.terminal_12v(21.0, 2.0, -12.8, 'east')
    m.nameplate(3.0, 18.6, 12.0, 19.4, -14.0, 'TBM S-1 HARD ROCK')
    f = m.surface('north', face=-15.62, title='TBM DESK')
    screen4(m, f, 5.0, 14.0, 8.0, 12.4, -15.6, ['STATUS', 'GEOLOGY', 'STORES', 'GUIDE'])
    counter(m, f, 'rings', 16.6, 11.4, 2.4, 'RING')
    counter(m, f, 'chain', 19.4, 11.4, 2.4, 'CHAINAGE M')
    gauges(m, f, [('thrustg', 'THRUST'), ('torqueg', 'TORQUE'), ('rpmg', 'HEAD RPM'), ('wearg', 'CUTTERS'), ('mvg', 'MV KV'), ('spoilg', 'SPOIL'), ('segg', 'SEGMENTS'), ('progg', 'RING')],
           X(3.0), 5.8, step=-2.3, size=1.8)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mv', '30e060', 'MV'), ('cutl', '8ad4ff', 'CUTTING', True), ('ring', '8ad4ff', 'ERECTOR', True), ('autol', '8ad4ff', 'CONT'),
                    ('steer', 'ffb020', 'STEER'), ('wear', 'ffb020', 'WORN')], X(3.4), 3.6, step=-2.6, size=0.55)
    lamp_row(m, f, [('spoil', 'ff3030', 'SPOIL'), ('seg', 'ff3030', 'NO SEG'), ('water', '30a0ff', 'WATER'), ('obst', 'ff3030', 'OBSTRUCT'),
                    ('cable', 'ffb020', 'CABLE'), ('fault', 'ff3030', 'FAULT', True)], X(3.4), 2.2, step=-2.6, size=0.55)
    push_row(m, f, [('bore', '1fa34a', 'BORE'), ('stop', 'd02020', 'STOP'), ('cutchg', 'e08020', 'CUTTERS'), ('reset', 'e0c020', 'RESET'), ('test', 'b0b0b0', 'TEST')],
             X(3.6), 0.9, step=-3.0, size=0.9)
    u = m.surface('north', face=-14.02, title='TBM UPSTAND')
    m.ctl(u, 'key', 'k_main', X(3.6), 17.2, w=1.1, h=1.1, col='c02020', label='MV MAIN', pos=['OPEN', 'CLOSED'])
    m.ctl(u, 'key', 'k_auto', X(6.2), 17.2, w=1.1, h=1.1, col='c0a040', label='RING', pos=['SINGLE', 'CONT'])
    m.ctl(u, 'selector', 's_dia', X(8.8), 17.2, w=1.2, h=1.2, col='202020', label='BORE', pos=['7M', '5M', '9M'])
    m.ctl(u, 'selector', 's_thrust', X(11.4), 17.2, w=1.2, h=1.2, col='202020', label='THRUST', pos=['MED', 'LOW', 'HIGH'])
    m.ctl(u, 'selector', 's_steer', X(14.0), 17.2, w=1.2, h=1.2, col='2a4a8a', label='STEER', pos=['AHEAD', 'LEFT', 'RIGHT'])
    m.ctl(u, 'selector', 's_grade', X(16.6), 17.2, w=1.2, h=1.2, col='2a4a8a', label='GRADE', pos=['LEVEL', 'UP', 'DOWN'])
    m.ctl(u, 'selector', 's_lining', X(19.0), 17.2, w=1.2, h=1.2, col='202020', label='LINING', pos=['CONC', 'BRICK', 'STONE', 'OFF'])
    estop_plate(m, u, X(19.4), 14.4)
    # stores: spoil skip (west side of the gantry), segment magazine (east), cutter store (gantry deck front)
    m.box((-7.6, 0.0, -9.0), (-6.0, 6.0, -1.0), 'paint:#5a5a50', 'spoil skip', west='label:SPOIL')
    stores_zone(m, 'west', -7.62, -5.0, 3.0, 8.0, 6.0, 'spoil')
    m.box((22.0, 0.0, -9.0), (24.0, 10.0, -1.0), 'concrete', 'segment magazine', east='label:SEGMENTS')
    stores_zone(m, 'east', 16 - 24.02, 16 - (-5.0), 5.0, 8.0, 10.0, 'segs')
    m.box((8.0, 21.4, -14.0), (12.0, 24.0, -11.0), 'paint:#3a3f44', 'cutter store', north='label:CUTTERS')
    stores_zone(m, 'north', -14.02, X(10.0), 22.7, 4.0, 2.6, 'cutters')
    m.save(item_gui=0.32)
    return m


# ========================================================================================================= Ballast Tamper
def ballast_tamper():
    """a 09-3X style tamping-lining machine set down on the track (its block sits on the rail, the model sunk 13 px so
    the wheels are on the rail heads): bogies, tamping banks with tines, lifting / lining unit, measuring trolley,
    ballast hopper, engine, and the cab's work desk at the end facing the operator"""
    m = Model('ballast_tamper')
    m.g('frame')
    m.box((-12.0, 4.0, -12.0), (28.0, 6.4, 28.0), 'paint:#e0b818', 'main frame')
    for z in (-12.0, 28.0):
        m.box((-12.0, 3.0, z - 0.4), (28.0, 4.0, z + 0.4), 'haz', 'headstock stripes')
    m.g('bogies')
    for zc in (-8.0, 24.0):
        for dz in (-2.5, 2.5):
            wheelset(m, 0.9, 15.1, zc + dz, r=1.6)
        m.box((-0.8, 2.4, zc - 4.6), (16.8, 4.0, zc + 4.6), 'paint:#2f3338', 'bogie frame')
    m.g('tamping banks')
    for (x0, x1) in ((-4.0, 4.0), (12.0, 20.0)):
        m.box((x0, 6.4, 6.0), (x1, 16.0, 12.0), 'paint:#c8a020', 'tamping bank')
        m.cyl(((x0 + x1) / 2, 16.0, 9.0), 1.4, 4.0, 'y', 'paint:#2a5a8a', 'bank lift cylinder')
        m.box((x0 + 0.5, 10.0, 5.6), (x1 - 0.5, 14.0, 6.0), 'paint:#3a3a3a', 'vibrator housing', north='label:35HZ')
        for k in range(4):
            xx = x0 + 1.0 + k * (x1 - x0 - 2.0) / 3
            for zz in (6.6, 11.0):
                m.box((xx - 0.3, 0.2, zz), (xx + 0.3, 6.4, zz + 0.4), 'metal:steel', 'tamping tine')
                m.box((xx - 0.6, 0.0, zz - 0.2), (xx + 0.6, 0.8, zz + 0.6), 'metal:steel', 'tine paddle')
        m.cyl((x0, 8.0, 9.0), 0.5, x1 - x0, 'x', 'paint:#2a5a8a', 'squeeze cylinder')
    m.g('lifting + lining unit')
    m.box((-1.0, 6.4, 14.0), (17.0, 9.0, 18.0), 'paint:#c82020', 'lifting lining unit')
    for x in (1.4, 13.0):
        m.cyl((x, 1.6, 16.0), 1.2, 0.8, 'x', 'paint:#2a2a2a', 'rail clamp roller')
        m.cyl((x + 0.4, 2.8, 16.0), 0.4, 3.6, 'y', 'metal:steel', 'lifting hook')
    m.g('measuring trolley')
    m.box((-0.6, 1.6, 0.0), (16.6, 2.6, 2.0), 'paint:#3a3f44', 'measuring trolley')
    for x in (0.6, 14.6):
        m.cyl((x, 1.0, 1.0), 1.0, 0.8, 'x', 'paint:#1a1a1a', 'trolley wheel')
    m.cyl((8.0, 2.6, 1.0), 0.25, 4.0, 'y', 'metal:steel', 'chord wire post')
    m.cyl((-4.0, 5.0, -10.0), 0.08, 36.0, 'z', 'metal:steel', 'measuring chord')
    m.g('body')
    m.box((-11.0, 6.4, -11.0), (27.0, 20.0, 4.0), 'paint:#e8c020', 'engine + cab body')
    m.box((-11.4, 20.0, -11.4), (27.4, 21.0, 4.4), 'paint:#3a3a3a', 'roof')
    m.louvres(14.0, 26.0, 9.0, 18.0, -11.05, n=7)
    m.cyl((22.0, 21.0, -4.0), 0.9, 5.0, 'y', 'paint:#2a2a2a', 'exhaust stack')
    for x in (-11.05, 27.0):
        m.box((x, 12.0, -9.0), (x + 0.05, 18.0, 2.0), 'glass', 'side window')
    m.box((-10.0, 6.4, 20.0), (26.0, 16.0, 27.0), 'paint:#5a5a50', 'ballast hopper')
    m.box((-9.0, 16.0, 21.0), (25.0, 16.4, 26.0), 'mesh', 'hopper grid')
    m.box((-11.6, 7.0, -6.0), (-11.0, 12.0, 0.0), 'paint:#3a3f44', 'fuel tank', west='label:DIESEL')
    m.box((27.0, 7.0, -8.0), (28.0, 11.0, -3.0), 'paint:#2a2a2a', 'battery box', east='label:24V BATT')
    beacon(m, -8.0, 21.0, -8.0)
    beacon(m, 24.0, 21.0, -8.0)
    m.box((-10.0, 17.0, -11.1), (4.0, 19.6, -11.0), 'danger:DANGER|TAMPING TINES', 'danger sign')
    m.nameplate(6.0, 18.0, 13.6, 19.6, -11.0, '09-3X TAMPER')
    handrail_run(m, -11.0, 27.0, 28.0, 6.4)
    # ---- work desk (north)
    m.g('work desk')
    m.box((-10.0, 0.0, -15.6), (26.0, 11.0, -11.0), 'paint:#2f3d4a', 'desk body')
    m.box((-10.0, 11.0, -14.0), (26.0, 16.0, -11.0), 'paint:#2b3743', 'desk upstand')
    m.box((-10.2, 16.0, -15.0), (26.2, 16.6, -11.0), 'paint:#1c242c', 'desk hood')
    m.box((-9.8, 0.0, -15.8), (25.8, 0.8, -15.6), 'paint:#1c1d20', 'kick plate')
    m.terminal_12v(26.0, 2.0, -13.4, 'east')
    f = m.surface('north', face=-15.62, title='TAMPER DESK')
    screen4(m, f, -7.0, 4.6, 6.4, 10.6, -15.6, ['STATUS', 'BEFORE/AFTER', 'WORST', 'ENGINE'])
    counter(m, f, 'tqib', 8.0, 9.8, 2.4, 'TQI BEFORE')
    counter(m, f, 'tqia', 11.6, 9.8, 2.4, 'TQI AFTER', col='7cf29a')
    counter(m, f, 'pieces', 15.2, 9.8, 2.4, 'PIECES')
    gauges(m, f, [('fuelg', 'DIESEL'), ('batg', 'BATT'), ('rpmg', 'RPM'), ('vibg', 'VIBRATION'), ('ballg', 'BALLAST'), ('progg', 'WORK'), ('tqibg', 'TQI BEFORE'), ('tqiag', 'TQI AFTER')],
           X(-7.0), 4.2, step=-2.4, size=1.8)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('eng', '30e060', 'ENGINE'), ('batl', 'ffb020', 'BATT LOW'), ('fuell', 'ffb020', 'FUEL LOW'),
                    ('heads', '8ad4ff', 'HEADS DN'), ('ball', 'ffb020', 'BALLAST')], X(13.0), 6.8, step=-2.0, size=0.55)
    lamp_row(m, f, [('meas', '8ad4ff', 'MEASURE', True), ('work', '8ad4ff', 'TAMPING', True), ('done', '30e060', 'DONE'), ('train', 'ff3030', 'TRAIN', True),
                    ('tsr', 'ffb020', 'TSR'), ('fault', 'ff3030', 'FAULT')], X(13.0), 5.2, step=-2.0, size=0.55)
    push_row(m, f, [('measure', '2a6ad0', 'MEASURE'), ('start', '1fa34a', 'START'), ('stop', 'd02020', 'STOP'), ('reset', 'e0c020', 'RESET'), ('test', 'b0b0b0', 'TEST')],
             X(13.0), 2.4, step=-2.6, size=1.0)
    u = m.surface('north', face=-14.02, title='TAMPER UPSTAND')
    m.ctl(u, 'key', 'k_batt', X(-7.0), 13.8, w=1.1, h=1.1, col='c02020', label='BATTERY', pos=['OFF', 'ON'])
    m.ctl(u, 'key', 'k_engine', X(-4.0), 13.8, w=1.1, h=1.1, col='c0a040', label='ENGINE', pos=['OFF', 'RUN'])
    m.ctl(u, 'selector', 's_range', X(0.0), 13.8, w=1.2, h=1.2, col='202020', label='SECTION', pos=['32M', '16M', '64M', '128M'])
    m.ctl(u, 'selector', 's_lift', X(4.0), 13.8, w=1.2, h=1.2, col='202020', label='LIFT', pos=['20MM', '10MM', '30MM', '40MM'])
    m.ctl(u, 'selector', 's_squeeze', X(8.0), 13.8, w=1.2, h=1.2, col='202020', label='SQUEEZE', pos=['0.8S', '0.6S', '1.0S', '1.2S'])
    m.ctl(u, 'selector', 's_mode', X(12.0), 13.8, w=1.2, h=1.2, col='2a4a8a', label='MODE', pos=['TAMP', 'T+LINE', 'FULL'])
    m.ctl(u, 'lever', 'm_heads', X(16.0), 12.8, w=0.8, h=2.4, col='e8c020', label='HEADS', pos=['UP', 'DOWN'])
    estop_plate(m, u, X(20.0), 13.8)
    stores_zone(m, 'east', 16 - 26.02, 16 - 23.5, 11.0, 7.0, 9.6, 'ballast')
    sink(m, 13.0)                                        # it stands ON the track: block above the rail, wheels on the rails
    m.save(item_gui=0.32)
    return m


# ======================================================================================================= Locomotive Works
def loco_works():
    """the works manager's desk: build-board screen, catalogue picker, shop controls, parts stores and despatch"""
    m = Model('loco_works')
    m.g('desk')
    m.box((-8.0, 0.0, 0.6), (24.0, 12.0, 6.0), 'paint:#2f3d4a', 'desk body')
    m.box((-8.0, 12.0, 2.4), (24.0, 22.0, 6.0), 'paint:#2b3743', 'desk upstand')
    m.box((-8.2, 22.0, 1.6), (24.2, 22.6, 6.4), 'paint:#1c242c', 'desk hood')
    m.box((-7.8, 0.0, 0.4), (23.8, 0.8, 0.6), 'paint:#1c1d20', 'kick plate')
    m.g('build board')
    m.box((-7.0, 22.6, 4.0), (23.0, 31.0, 5.4), 'paint:#14324a', 'build board')
    m.box((-6.6, 29.4, 3.92), (22.6, 30.6, 4.0), 'plate:PRIDE LOCOMOTIVE WORKS - ERECTING SHOP', 'board title')
    for i, x in enumerate((-6.0, 1.2, 8.4, 15.6)):
        m.box((x, 23.6, 3.9), (x + 6.4, 28.8, 4.0), 'screen', 'bay screen')
        m.box((x + 0.4, 27.6, 3.85), (x + 6.0, 28.4, 3.9), 'label:BAY %d' % (i + 1), 'bay label')
    m.g('crane pendant')
    m.cyl((25.0, 8.0, 3.0), 0.2, 24.0, 'y', 'cable:202020', 'pendant cable')
    m.box((24.0, 4.0, 2.2), (26.0, 8.0, 3.8), 'paint:#e0b818', 'crane pendant')
    for y in (5.0, 6.0, 7.0):
        m.box((24.4, y, 2.1), (25.0, y + 0.6, 2.2), 'paint:#1fa34a', 'pendant button')
        m.box((25.0, y, 2.1), (25.6, y + 0.6, 2.2), 'paint:#d02020', 'pendant button')
    m.g('drawing chest')
    m.box((-15.6, 0.0, 1.0), (-8.4, 10.0, 15.0), 'wood:#6a4a2a', 'plan chest')
    for y in (1.0, 3.2, 5.4, 7.6):
        m.box((-15.7, y, 2.0), (-15.6, y + 1.8, 14.0), 'wood:#7a5a3a', 'plan drawer')
        m.box((-15.8, y + 0.7, 7.4), (-15.7, y + 1.1, 8.6), 'metal:brass', 'drawer pull')
    m.box((-15.4, 10.0, 1.4), (-8.6, 10.6, 14.6), 'paint:#e8e0c8', 'general arrangement drawing')
    m.g('parts racks')
    for x in (26.4, 30.0):
        m.box((x, 0.0, 6.0), (x + 0.4, 24.0, 15.0), 'paint:#3a5a8a', 'rack upright')
    for y in (0.6, 6.0, 11.4, 16.8, 22.2):
        m.box((26.4, y, 6.0), (30.4, y + 0.4, 15.0), 'paint:#e07020', 'rack beam')
    for (y, z, col) in ((1.0, 7.0, '#5a6a7a'), (1.0, 11.0, '#3a3f44'), (6.4, 7.0, '#2a5a8a'), (6.4, 11.0, '#c8a020'), (11.8, 8.0, '#5a5a50'), (17.2, 7.0, '#3a3f44')):
        m.box((26.8, y, z), (30.0, y + 3.6, z + 3.4), 'paint:' + col, 'parts box')
    m.cyl((28.4, 17.4, 12.2), 1.8, 0.6, 'x', 'metal:steel', 'wheelset on rack')
    m.g('despatch')
    m.box((-14.0, 0.0, -8.0), (-6.0, 1.2, 0.0), 'paint:#3a3f44', 'despatch dock')
    m.box((-14.0, 1.2, -8.0), (-13.6, 4.0, 0.0), 'haz', 'dock barrier')
    m.box((-13.0, 1.2, -6.0), (-7.0, 5.0, -1.0), 'wood:#8a6a3a', 'crated stock')
    m.box((-13.2, 4.6, -6.2), (-6.8, 5.2, -0.8), 'label:DESPATCH', 'crate stencil')
    m.terminal_12v(21.0, 2.0, 6.0, 'south')
    m.box((24.0, 0.0, 6.0), (26.4, 14.0, 12.0), 'paint:#3a4a5a', 'shop MCC')
    cabkit.fit(m, 24.0, 26.4, 0.4, 13.6, 6.0, side='north', col='paint:#3a4a5a', title='SHOP MCC|480V', volts='480V 3PH')
    m.nameplate(-6.0, 21.0, 6.0, 21.8, 2.4, 'WORKS MANAGER')
    f = m.surface('north', face=0.58, title='WORKS DESK')
    screen4(m, f, -5.0, 8.0, 6.4, 11.2, 0.6, ['SELECT', 'PARTS', 'QUEUE', 'BAYS'])
    m.box((10.0, 9.8, 0.3), (22.6, 11.6, 0.6), 'paint:#141414', 'selection window')
    m.ctl(f, 'digits', 'sel', X(16.3), 10.7, w=12.0, h=1.2, col='8ad4ff', label='', fmt='%s')
    counter(m, f, 'qn', 11.0, 7.6, 2.0, 'QUEUE')
    counter(m, f, 'built', 14.0, 7.6, 2.6, 'BUILT')
    gauges(m, f, [('progg', 'BAY 1'), ('partg', 'PARTS'), ('queueg', 'QUEUE'), ('loadg', 'SHOP KW')], X(17.2), 7.6, step=-2.0, size=1.6)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', '480V'), ('bays', '8ad4ff', 'BAYS'), ('runl', '30e060', 'RUN'), ('hold', 'ffb020', 'HOLD'),
                    ('short', 'ff3030', 'SHORT', True)], X(-5.0), 4.8, step=-2.4, size=0.55)
    lamp_row(m, f, [('b1', '8ad4ff', 'BAY 1', True), ('b2', '8ad4ff', 'BAY 2', True), ('b3', '8ad4ff', 'BAY 3', True), ('b4', '8ad4ff', 'BAY 4', True),
                    ('ready', '30e060', 'READY', True), ('fault', 'ff3030', 'FAULT')], X(-5.0), 3.4, step=-2.4, size=0.55)
    push_row(m, f, [('prev', '505860', 'PREV'), ('next', '505860', 'NEXT'), ('add', '1fa34a', 'ADD'), ('del', 'e08020', 'DEL'),
                    ('start', '2a6ad0', 'START'), ('hold', 'e0c020', 'HOLD'), ('test', 'b0b0b0', 'TEST')], X(-5.0), 1.4, step=-2.7, size=1.0)
    u = m.surface('north', face=2.38, title='WORKS UPSTAND')
    m.ctl(u, 'key', 'k_shop', X(-4.4), 16.0, w=1.1, h=1.1, col='c0a040', label='SHOP', pos=['OFF', 'ON'])
    m.ctl(u, 'selector', 's_cat', X(-0.6), 16.0, w=1.2, h=1.2, col='202020', label='STOCK', pos=['ALL', 'LOCO', 'FREIGHT', 'PASS'])
    m.ctl(u, 'selector', 's_gauge', X(3.4), 16.0, w=1.2, h=1.2, col='202020', label='GAUGE', pos=['REC', 'STD', 'CAPE', '3FT', 'MIN'])
    estop_plate(m, u, X(7.4), 16.0)
    m.box((10.0, 13.0, 2.3), (22.0, 20.0, 2.4), 'plate:BUILD ORDER - QUEUE|1 SELECT STOCK|2 ADD|3 SHOP KEY ON|4 START', 'instruction plate')
    stores_zone(m, 'east', 16 - 30.42, 16 - 10.5, 12.0, 9.0, 23.0, 'parts')
    stores_zone(m, 'north', -8.02, X(-10.0), 3.0, 8.0, 6.0, 'despatch')
    m.save(item_gui=0.36)
    return m


def loco_works_bay():
    """an erecting bay: pit road with rails, frame jacks, a loco frame on stands, wheeling gantry"""
    m = Model('loco_works_bay')
    m.g('pit')
    m.box((0.0, 0.0, -16.0), (16.0, 1.0, 32.0), 'concrete', 'shop floor')
    m.box((4.0, 0.0, -15.0), (12.0, 1.02, 31.0), 'paint:#2a2a2a', 'inspection pit')
    for x in (3.4, 11.8):
        m.box((x, 1.0, -16.0), (x + 0.8, 1.6, 32.0), 'metal:steel', 'pit road rail')
    for z in range(-14, 31, 4):
        m.box((3.0, 1.0, z), (13.0, 1.2, z + 0.6), 'paint:#e0b818', 'pit edge marking')
    m.g('frame on stands')
    for z in (-10.0, 0.0, 10.0, 20.0):
        for x in (1.0, 13.6):
            m.box((x, 1.0, z), (x + 1.4, 7.0, z + 1.4), 'paint:#c82020', 'frame stand')
    m.box((1.0, 7.0, -12.0), (15.0, 9.0, 26.0), 'paint:#1e1e20', 'locomotive underframe')
    m.box((2.0, 9.0, -8.0), (14.0, 16.0, 6.0), 'paint:#2a5a3a', 'body shell section')
    m.box((2.4, 9.0, 6.0), (13.6, 12.0, 22.0), 'paint:#3a3f44', 'power unit on its bed')
    m.cyl((4.0, 12.0, 14.0), 2.0, 8.0, 'x', 'paint:#2a2a2a', 'engine block')
    m.g('jacks')
    for (x, z) in ((-0.6, -14.0), (15.2, -14.0), (-0.6, 28.0), (15.2, 28.0)):
        m.box((x, 0.0, z), (x + 1.4, 14.0, z + 1.4), 'paint:#e0b818', 'lifting jack column')
        m.box((x - 0.2, 12.0, z + 1.4), (x + 1.6, 13.0, z + 2.4), 'paint:#3a3a3a', 'jack arm')
        m.box((x + 0.2, 1.0, z - 0.1), (x + 1.2, 3.0, z), 'paint:#3a3a3a', 'jack control box')
    m.g('gantry')
    for x in (-1.0, 16.0):
        m.box((x, 0.0, 30.0), (x + 1.0, 30.0, 31.0), 'paint:#e0b818', 'gantry leg')
    m.box((-1.0, 29.0, 29.6), (17.0, 31.0, 31.4), 'paint:#e0b818', 'gantry beam')
    m.box((6.0, 26.0, 29.4), (10.0, 29.0, 31.6), 'paint:#3a3f44', 'hoist trolley')
    m.cyl((8.0, 18.0, 30.5), 0.12, 8.0, 'y', 'metal:steel', 'hoist rope')
    m.box((7.0, 16.6, 30.0), (9.0, 18.0, 31.0), 'paint:#e0b818', 'hook block')
    m.box((0.0, 1.0, 31.8), (16.0, 3.0, 32.0), 'danger:DANGER|OVERHEAD CRANE', 'danger sign')
    m.g('bay kit')
    for z in (-6.0, 4.0):                                                       # wheelsets waiting on the pit road
        m.cyl((3.4, 3.2, z), 2.2, 0.8, 'x', 'metal:steel', 'waiting wheel')
        m.cyl((11.8, 3.2, z), 2.2, 0.8, 'x', 'metal:steel', 'waiting wheel')
        m.cyl((4.2, 3.2, z), 0.5, 7.6, 'x', 'paint:#2a2a2a', 'waiting axle')
    for z in range(-12, 28, 6):                                                 # pit lights
        m.box((4.1, 0.2, z), (4.3, 0.8, z + 1.0), 'lamp:fff4d0', 'pit light')
        m.box((11.7, 0.2, z), (11.9, 0.8, z + 1.0), 'lamp:fff4d0', 'pit light')
    m.box((-0.8, 1.0, 22.0), (2.4, 5.0, 25.0), 'paint:#2a5a8a', 'welding set', north='label:MIG 400A')
    for (x, y) in ((-0.2, 4.0), (0.6, 4.0), (1.4, 4.0)):
        m.box((x, y, 21.8), (x + 0.5, y + 0.5, 22.0), 'paint:#202020', 'welder knob')
    m.cyl((0.8, 5.0, 23.5), 0.25, 3.0, 'y', 'cable:202020', 'welding lead')
    m.box((13.6, 1.0, 21.0), (16.0, 5.6, 25.0), 'paint:#c82020', 'tool trolley')
    for y in (2.0, 3.2, 4.4):
        m.box((13.5, y, 21.4), (13.6, y + 0.9, 24.6), 'paint:#a01818', 'tool drawer')
        m.box((13.4, y + 0.3, 22.6), (13.5, y + 0.6, 23.4), 'metal:steel', 'drawer pull')
    for z in (-10.0, 10.0):                                                     # air hose reels on the columns
        m.cyl((-0.9, 9.0, z), 1.2, 0.6, 'x', 'paint:#e0b818', 'air hose reel')
        m.cyl((-1.0, 9.0, z), 0.4, 0.8, 'x', 'paint:#2a2a2a', 'hose drum')
    m.handrail([(2.6, -15.0), (2.6, 30.0)], 1.0, h=2.4)
    m.handrail([(13.4, -15.0), (13.4, 30.0)], 1.0, h=2.4)
    m.box((3.6, 1.0, -15.2), (12.4, 4.0, -14.8), 'paint:#2a2a2a', 'bay sign post')
    m.nameplate(4.0, 2.2, 12.0, 3.6, -15.2, 'ERECTING BAY')
    m.save(item_gui=0.3)
    return m


# ========================================================================================================= Hi-Rail Truck
HIRAIL_LAMPS = {
    'beacon': [(4.0, 18.6, -5.4, 5.6, 19.6, -4.2), (10.4, 18.6, -5.4, 12.0, 19.6, -4.2)],
    'head': [(1.2, 5.4, -15.32, 3.6, 6.8, -15.2), (12.4, 5.4, -15.32, 14.8, 6.8, -15.2)],
    'work': [(3.0, 17.6, -6.42, 5.0, 18.4, -6.3), (11.0, 17.6, -6.42, 13.0, 18.4, -6.3), (6.8, 10.4, 31.8, 9.2, 11.4, 31.92)],
    'tail': [(0.8, 3.2, 31.8, 2.6, 4.6, 31.92), (13.4, 3.2, 31.8, 15.2, 4.6, 31.92)],
}


def hirail_truck():
    """the truck at 1:2.5 (drawn 2.5x by B6Client): crew cab, flatbed with tool boxes and a crane, rail gear mounts;
    the guide wheels themselves are hirail_guides (drawn at their live height)"""
    m = Model('hirail_truck')
    from gen_bb_road import tyre
    m.g('chassis')
    m.box((1.6, 1.8, -15.0), (14.4, 3.0, 31.0), 'paint:#18181a', 'chassis rails')
    for (x, z) in ((0.2, -9.0), (13.8, -9.0), (0.2, 20.0), (13.8, 20.0), (0.2, 25.0), (13.8, 25.0)):
        tyre(m, x, z, 2.4, 2.0)
    m.box((0.6, 2.0, -15.9), (15.4, 3.6, -15.2), 'paint:#202022', 'front bumper')
    m.box((0.4, 2.4, 31.0), (15.6, 3.6, 31.8), 'haz', 'rear underrun bar')
    m.g('cab')
    m.box((0.6, 3.0, -15.2), (15.4, 8.2, -9.6), 'paint:#f0f0ec', 'bonnet')
    m.box((0.4, 3.0, -9.6), (15.6, 16.6, 4.0), 'paint:#f0f0ec', 'crew cab')
    m.box((0.6, 16.6, -9.4), (15.4, 17.2, 3.8), 'paint:#e6e6e2', 'cab roof')
    m.box((1.2, 8.4, -9.65), (14.8, 15.6, -9.55), 'glass', 'windscreen', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 8.4, -9.6]})
    for x in (0.3, 15.6):
        m.box((x, 9.0, -8.6), (x + 0.1, 15.0, -3.0), 'glass', 'front door window')
        m.box((x, 9.0, -2.4), (x + 0.1, 15.0, 3.4), 'glass', 'rear door window')
        m.box((x - 0.04, 3.4, -2.8), (x + 0.14, 15.6, -2.6), 'paint:#303034', 'door shut line')
        m.box((x - 0.05, 8.0, -4.6), (x + 0.15, 8.4, -3.4), 'metal:steel', 'door handle')
        m.box((x - 0.05, 4.0, -8.0), (x + 0.15, 7.0, 2.0), 'paint:#e06a1a', 'hi-vis side stripe')
    for x in (-0.8, 15.6):
        m.box((x, 10.0, -9.0), (x + 1.2, 11.6, -8.0), 'paint:#202022', 'door mirror')
    m.box((3.0, 3.6, -15.3), (13.0, 7.6, -15.2), 'mesh', 'grille')
    m.box((5.4, 2.2, -16.0), (10.6, 3.4, -15.9), 'plate', 'number plate', north='label:RAIL 06')
    m.g('roof gear')
    m.box((2.0, 17.2, -6.0), (14.0, 18.0, -3.6), 'paint:#202022', 'light bar')
    m.box((6.2, 17.2, 0.0), (9.8, 18.4, 3.0), 'paint:#3a3f44', 'radio + GPS dome')
    m.cyl((12.0, 17.2, 2.0), 0.15, 4.0, 'y', 'paint:#1a1a1a', 'radio aerial')
    m.g('flatbed')
    m.box((0.2, 4.6, 4.4), (15.8, 5.6, 31.2), 'paint:#3a3f44', 'flatbed deck')
    for x in (0.2, 15.2):
        m.box((x, 5.6, 4.4), (x + 0.6, 8.6, 31.2), 'paint:#e06a1a', 'drop side')
    m.box((0.2, 5.6, 30.6), (15.8, 8.6, 31.2), 'paint:#e06a1a', 'tail board')
    m.box((0.2, 5.6, 4.4), (15.8, 11.0, 5.0), 'mesh', 'headboard guard')
    for x in (1.0, 11.0):
        m.box((x, 5.6, 6.0), (x + 4.0, 9.6, 12.0), 'paint:#c8c8c4', 'tool box', north='label:TOOLS')
    m.box((6.0, 5.6, 14.0), (10.0, 7.4, 24.0), 'wood:#4a3420', 'spare sleepers')
    m.box((2.0, 5.6, 25.0), (14.0, 6.6, 29.0), 'metal:steel', 'rail offcuts')
    m.g('crane')
    m.cyl((12.0, 5.6, 27.0), 1.2, 3.0, 'y', 'paint:#e0b818', 'crane slew base')
    m.box((11.4, 8.6, 14.0), (12.6, 9.8, 27.6), 'paint:#e0b818', 'crane boom (stowed)')
    m.cyl((12.0, 7.0, 25.0), 0.4, 3.0, 'z', 'paint:#2a5a8a', 'luff cylinder')
    m.box((11.6, 6.0, 13.6), (12.4, 8.6, 14.2), 'paint:#3a3a3a', 'boom rest')
    m.g('rail gear mounts')
    for z in (-14.6, 28.0):
        m.box((1.0, 2.6, z), (15.0, 4.0, z + 1.6), 'paint:#e0b818', 'rail gear crossbeam')
        for x in (2.0, 13.2):
            m.cyl((x + 0.4, 4.0, z + 0.8), 0.45, 2.4, 'y', 'paint:#2a5a8a', 'rail gear ram')
    m.g('lamps')
    for b in HIRAIL_LAMPS['beacon']: m.box(b[:3], b[3:], 'lamp:a05010', 'beacon lens')
    for b in HIRAIL_LAMPS['head']: m.box(b[:3], b[3:], 'lamp:fff4d0', 'headlight')
    for b in HIRAIL_LAMPS['work']: m.box(b[:3], b[3:], 'lamp:e8e8e0', 'work light')
    for b in HIRAIL_LAMPS['tail']: m.box(b[:3], b[3:], 'lamp:8a1010', 'tail lamp')
    m.save(item_gui=0.28)
    return m


def hirail_guides():
    """the guide wheel units, front and rear: pivot arms, steel flanged wheels on 1435 mm centres (9.2 px at 1:2.5)"""
    m = Model('hirail_guides')
    for z in (-14.6, 28.0):
        m.g('guide unit')
        m.box((2.0, 1.4, z + 0.2), (14.0, 2.6, z + 1.4), 'paint:#2a2a2a', 'guide axle beam')
        for xc in (3.4, 12.6):
            m.box((xc - 0.5, 1.6, z - 0.4), (xc + 0.5, 3.0, z + 2.0), 'paint:#e0b818', 'pivot arm')
            m.cyl((xc - 0.4, 0.8, z + 0.8), 0.8, 0.8, 'x', 'metal:steel', 'guide wheel')
            m.cyl((xc + (-0.6 if xc < 8 else 0.4), 0.8, z + 0.8), 0.95, 0.2, 'x', 'metal:steel', 'wheel flange')
            m.cyl((xc - 0.2, 0.8, z + 0.8), 0.3, 0.4, 'x', 'paint:#3a3a3a', 'hub')
    m.save(item_gui=0.3)
    return m


# ========================================================================================================== registration
def P(meta): return {'item': 'irextras:part', 'data': meta}
PART = {'plate': 0, 'frame': 1, 'circuit': 2, 'motor': 3, 'hydraulic': 4, 'gearbox': 5, 'heating': 6, 'fan': 7, 'belt': 8, 'bearing': 9,
        'shaft': 10, 'screen': 20, 'battery': 22, 'contactor': 18}


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    return {'item': v}


RECIPES = {
    'track_layer': (['HSH', 'MFM', 'BCB'], {'H': P(PART['hydraulic']), 'S': P(PART['screen']), 'M': P(PART['motor']), 'F': P(PART['frame']), 'B': P(PART['belt']), 'C': P(PART['circuit'])}),
    'tbm': (['GPG', 'HFH', 'MCM'], {'G': P(PART['gearbox']), 'P': P(PART['plate']), 'H': P(PART['hydraulic']), 'F': P(PART['frame']), 'M': P(PART['motor']), 'C': P(PART['circuit'])}),
    'ballast_tamper': (['HSH', 'MFM', 'PCP'], {'H': P(PART['hydraulic']), 'S': P(PART['screen']), 'M': P(PART['motor']), 'F': P(PART['frame']), 'P': P(PART['plate']), 'C': P(PART['circuit'])}),
    'loco_works': (['SCS', 'PFP', 'KBK'], {'S': P(PART['screen']), 'C': P(PART['circuit']), 'P': P(PART['plate']), 'F': P(PART['frame']), 'K': P(PART['contactor']), 'B': ('minecraft:bookshelf', None)}),
    'loco_works_bay': (['PHP', 'I I', 'FFF'], {'P': P(PART['plate']), 'H': P(PART['hydraulic']), 'I': ('minecraft:iron_block', None), 'F': ('minecraft:stone', 0)}),
    'hirail_truck': (['GSG', 'MFM', 'WBW'], {'G': ('minecraft:glass_pane', None), 'S': P(PART['screen']), 'M': P(PART['motor']), 'F': P(PART['frame']), 'W': P(PART['bearing']), 'B': P(PART['battery'])}),
}

LANG = {
    'tile.irextras.track_layer.name': 'Track Laying Machine', 'tile.irextras.tbm.name': 'Tunnel Boring Machine',
    'tile.irextras.ballast_tamper.name': 'Ballast Tamper', 'tile.irextras.loco_works.name': 'Locomotive Works',
    'tile.irextras.loco_works_bay.name': 'Locomotive Works Erecting Bay', 'item.irextras.hirail_truck.name': 'Road-Rail Hi-Rail Truck',
    'entity.irextras.hirail_truck.name': 'Hi-Rail Truck',
}

BLOCKS = ['track_layer', 'tbm', 'ballast_tamper', 'loco_works', 'loco_works_bay']

if __name__ == '__main__':
    for fn in (track_layer, tbm, ballast_tamper, loco_works, loco_works_bay, hirail_truck, hirail_guides):
        mm = fn()
        print('%-16s %d boxes, %d live controls' % (mm.name, len(mm.boxes), sum(len(s['controls']) for s in mm.surfaces)))
    for n in BLOCKS:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
    for n in BLOCKS + ['hirail_truck', 'hirail_guides']:
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# batch 6a construction + works machines, hi-rail truck\n' + '\n'.join(add) + '\n')
    print('batch 6a written')
