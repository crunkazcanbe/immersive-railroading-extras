#!/usr/bin/env python3
"""IR Extras station, control-room and freight gear, modelled in detail with bbkit (her ask 2026-10-09: "make everything
in Blockbench and make it as complex as you physically can and make it wire up ... low voltage stuff gets the little
12 volt wire ... if it has buttons put buttons on it, if it don't, don't").

Every model faces NORTH (front = z=0), like the old ones. Each powered piece has the 12 V DC control terminal low down
so 12 V wire laid on the floor beside it meets it; the two big motors (silo gate actuator, pit conveyor) also get a
3-phase mains cable box. Areas the code renderers draw over are left free:
  nx_desk        top at y=13 over x -13.6..29.6, z 3.2..28.8 (NXDeskRenderer: mimic, buttons, train lights)
  control_desk   top at y=14 (ControlDeskRenderer: 12 buttons, lamps, labels; two screens on 14..14.3 bezels)
  arrivals_board screen plane z=10.45 over x 1..15, y 4.5..14.5 (ArrivalsBoardRenderer); nothing in front of it
display_panel is NOT rebuilt: its whole face is covered by DisplayPanelRenderer's screen quad, so extra detail (or a
12 V terminal) would be hidden or poke through the picture - the hand-made blockbench/display_panel.bbmodel stays.
Run from tools/:  python3 gen_bb_station.py
"""
import json, os
from bbkit import Model, ASSETS


# ------------------------------------------------------------------------------------------------ helpers
def bar(m, a, b, r, mat, name=None):
    """square bar from a to b (they differ along one axis), half-thickness r"""
    lo = [min(a[i], b[i]) for i in range(3)]
    hi = [max(a[i], b[i]) for i in range(3)]
    for i in range(3):
        if hi[i] - lo[i] < 1e-6: lo[i] -= r; hi[i] += r
    m.box(lo, hi, mat, name)


def run(m, pts, r, mat, name='conduit'):
    """a conduit / cable run through axis-aligned points, with a fitting at each bend"""
    for p, q in zip(pts, pts[1:]): bar(m, p, q, r, mat, name)
    for p in pts[1:-1]: m.box([v - r * 1.3 for v in p], [v + r * 1.3 for v in p], mat, name + ' elbow')


OFF = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0)}


def stub(m, x, y, z, face, w, h, depth, mat, name, **per):
    """a w x h box standing `depth` out of `face` at centre (x, y, z) (for 'up': w along x, h along z)"""
    d = OFF[face]
    if face == 'up':
        a, b = (x - w / 2, y, z - h / 2), (x + w / 2, y + depth, z + h / 2)
    elif face in ('north', 'south'):
        a, b = (x - w / 2, y - h / 2, z), (x + w / 2, y + h / 2, z + d[2] * depth)
    else:
        a, b = (x, y - h / 2, z - w / 2), (x + d[0] * depth, y + h / 2, z + w / 2)
    m.box(a, b, mat, name, **per)


def lab(m, x0, y0, x1, y1, z, text, face='north', kind='label', t=0.1, name='label'):
    """a text plate on a north/south face (x0..x1, y0..y1) or east/west face (x0..x1 read as z0..z1)"""
    spec = kind + ':' + text
    side = {'label': 'paint:#e8e8e0', 'plate': 'paint:#babec2', 'danger': 'paint:#f4f4f0'}[kind]
    if face == 'north': m.box((x0, y0, z - t), (x1, y1, z), side, name, north=spec)
    elif face == 'south': m.box((x0, y0, z), (x1, y1, z + t), side, name, south=spec)
    elif face == 'east': m.box((z, y0, x0), (z + t, y1, x1), side, name, east=spec)
    else: m.box((z - t, y0, x0), (z, y1, x1), side, name, west=spec)


def seam(m, a, b, name='panel seam'):
    m.box(a, b, 'paint:#1c1e22', name)


# ------------------------------------------------------------------------------------------------ live controls
def F(side, x, y, z):
    """a model point -> (u, v, face) in the surface frame of `side` (see bbkit Model.surface)"""
    return {'north': (16 - x, y, z), 'south': (x, y, 16 - z), 'west': (z, y, x), 'east': (16 - z, y, 16 - x),
            'up': (16 - x, z, 16 - y)}[side]


def surf(m, side, x, y, z, title=''):
    """a control surface lying on the model face through (x, y, z), a hair proud of it"""
    return m.surface(side, F(side, x, y, z)[2] - 0.01, title=title)


def ctl(m, sf, t, cid, x, y, z, **kw):
    """a live control at model point (x, y, z) on surface sf (drawn + worked by the game, see the block's tile)"""
    u, v, _ = F(sf['side'], x, y, z)
    m.ctl(sf, t, cid, u, v, **kw)


# ------------------------------------------------------------------------------------------------ ticket gate
def ticket_gate(open_):
    m = Model('ticket_gate_open' if open_ else 'ticket_gate', block='ticket_gate')
    readers = surf(m, 'up', 0, 13.4, 0, 'READERS')          # green = open / go, red = shut (TileTicketGate)
    front = surf(m, 'north', 0, 0, 1.7, 'STAFF')
    m.g('floor plate')
    m.box((3.2, 0, 1), (12.8, 0.25, 15), 'metal:steel', 'tread plate')
    for z in (1.2, 14.4): m.box((3.2, 0.25, z), (12.8, 0.3, z + 0.4), 'paint:ral1023', 'yellow line')
    for (x, z) in ((3.6, 2.1), (12.4, 2.1), (3.6, 13.9), (12.4, 13.9), (8, 2.1), (8, 13.9)): m.bolt(x, 0.25, z, 'up', 0.4)
    for side in (0, 1):
        x0, x1 = (0, 3.2) if side == 0 else (12.8, 16)
        xi = x1 if side == 0 else x0                  # aisle face
        xo = x0 if side == 0 else x1                  # outer face
        fi, fo = ('east', 'west') if side == 0 else ('west', 'east')
        sg = 1 if side == 0 else -1                   # direction into the aisle
        xm = (x0 + x1) / 2
        m.g('cabinet ' + ('left', 'right')[side])
        m.box((x0 + 0.15, 0, 2.2), (x1 - 0.15, 0.7, 13.8), 'rubber', 'kick plinth')
        m.box((x0, 0.7, 2), (x1, 12.6, 14), 'metal:steel', 'stainless cabinet')
        m.box((x0 + 0.2, 0.7, 1.7), (x1 - 0.2, 12.6, 2.0), 'metal:steel', 'north end panel')
        m.box((x0 + 0.2, 0.7, 14), (x1 - 0.2, 12.6, 14.3), 'metal:steel', 'south end panel')
        # service door on the outer face: seams, hinges, lock
        xs = xo - 0.05 if side == 0 else xo
        for z in (3.2, 12.8): seam(m, (xs, 1.2, z), (xs + 0.05, 11.8, z + 0.12))
        for y in (1.2, 11.8): seam(m, (xs, y, 3.2), (xs + 0.05, y + 0.12, 12.92))
        for y in (2.5, 10.0): stub(m, xo, y, 3.4, fo, 0.4, 1.0, 0.15, 'metal:steel', 'door hinge')
        m.cyl((xo - 0.15 if side == 0 else xo, 9.0, 12.2), 0.3, 0.15, 'x', 'metal:brass', 'door lock')
        # lid, card reader, ticket slots
        m.g('lid ' + ('left', 'right')[side])
        m.box((x0 - 0.1 if side == 0 else x0, 12.6, 1.5), (x1 if side == 0 else x1 + 0.1, 13.4, 14.5), 'paint:#25282d', 'composite lid')
        m.box((x0 + 0.1, 12.6, 1.1), (x1 - 0.1, 13.2, 1.5), 'paint:#25282d', 'lid nose')
        m.box((xm - 1.1, 13.4, 2.6), (xm + 1.1, 13.6, 5.2), 'paint:ral1023', 'smartcard reader pad')
        m.box((xm - 0.7, 13.6, 3.3), (xm + 0.7, 13.65, 4.5), 'paint:#c89a10', 'reader target ring')
        ctl(m, readers, 'lamp', 'go%d' % (side + 1), xm - 0.4, 13.4, 5.8, w=0.55, h=0.55, col='40ff70')
        ctl(m, readers, 'lamp', 'stop%d' % (side + 1), xm + 0.4, 13.4, 5.8, w=0.55, h=0.55, col='ff3a30')
        m.box((xm - 0.9, 13.4, 7.0), (xm + 0.9, 14.2, 8.6), 'paint:#3a3f46', 'ticket slot housing')
        m.box((xm - 0.7, 14.2, 7.6), (xm + 0.7, 14.25, 7.85), 'paint:black', 'ticket slot')
        m.box((xm - 0.9, 13.4, 10.6), (xm + 0.9, 14.0, 12.2), 'paint:#3a3f46', 'ticket return housing')
        m.box((xm - 0.7, 14.0, 11.2), (xm + 0.7, 14.05, 11.45), 'paint:black', 'ticket return slot')
        # direction displays: green arrow at the way in (north), red X at the way out (south)
        m.g('direction display ' + ('left', 'right')[side])
        m.box((x0 + 0.45, 8.4, 1.45), (x1 - 0.45, 11.8, 1.7), 'paint:black', 'arrow display')
        g = 'lamp:40ff70'
        m.box((xm - 0.18, 8.8, 1.35), (xm + 0.18, 10.7, 1.45), g, 'arrow shaft')
        for a in (45, -45):
            m.box((xm - 0.16, 10.0, 1.35), (xm + 0.16, 11.3, 1.45), g, 'arrow head', rot={'axis': 'z', 'angle': a, 'origin': [xm, 11.15, 1.4]})
        m.box((x0 + 0.45, 8.4, 14.3), (x1 - 0.45, 11.8, 14.55), 'paint:black', 'no-entry display')
        for a in (45, -45):
            m.box((xm - 0.16, 9.0, 14.55), (xm + 0.16, 11.2, 14.65), 'lamp:ff3a30', 'no-entry cross', rot={'axis': 'z', 'angle': a, 'origin': [xm, 10.1, 14.6]})
        lab(m, x0 + 0.4, 6.4, x1 - 0.4, 7.8, 1.7, 'WAY|IN', 'north')
        lab(m, x0 + 0.4, 6.0, x1 - 0.4, 7.8, 14.3, 'NO|ENTRY', 'south', kind='danger')
        m.louvres(x0 + 0.4, x1 - 0.4, 1.4, 4.4, 1.7, n=4, mat='metal:steel')
        # aisle face: IR safety sensors, paddle slot, blue LED light bar
        m.g('aisle face ' + ('left', 'right')[side])
        for z in (3.6, 6.2, 9.8, 12.4):
            for y in (3.2, 7.4):
                stub(m, xi, y, z, fi, 0.8, 0.6, 0.1, 'paint:black', 'IR sensor window')
        stub(m, xi, 7.6, 8.0, fi, 1.4, 7.0, 0.05, 'paint:#1c1e22', 'paddle slot')
        stub(m, xi, 12.3, 8.0, fi, 11.0, 0.3, 0.1, 'lamp:' + ('5bcefa' if open_ else '2f7ce0'), 'aisle light bar')
        # glass paddle on its pivot hub
        m.g('paddle ' + ('left', 'right')[side])
        m.cyl((xi + sg * 0.35, 4.0, 8.0), 0.45, 7.4, 'y', 'metal:steel', 'paddle pivot hub')
        if open_:
            m.box((xi - (0.35 if sg < 0 else 0), 4.4, 7.75), (xi + (0.35 if sg > 0 else 0), 10.9, 8.25), 'rubber', 'paddle edge (retracted)')
        else:
            tip = 7.9 if sg > 0 else 8.1
            m.box((min(xi, tip), 4.4, 7.8), (max(xi, tip), 10.9, 8.2), 'glass', 'glass paddle')
            m.box((min(xi, tip), 10.9, 7.75), (max(xi, tip), 11.2, 8.25), 'metal:steel', 'paddle top rail')
            e0 = tip - 0.35 * sg
            m.box((min(e0, tip), 4.4, 7.7), (max(e0, tip), 11.2, 8.3), 'rubber', 'paddle soft edge')
    m.g('cabinet right')
    m.nameplate(13.3, 4.6, 15.5, 5.6, 1.7, 'GATE 1')
    # staff override key on the left cabinet's way-in end: AUTO = tickets, OPEN = paddles held open
    ctl(m, front, 'key', 'override', 1.6, 5.2, 1.7, w=1.0, h=1.0, col='c0a040', label='', pos=['AUTO', 'OPEN'])
    m.terminal_12v(14.4, 1.4, 14.3, 'south')
    m.save()


# ------------------------------------------------------------------------------------------------ PA speaker
def pa_speaker():
    m = Model('pa_speaker')
    m.g('wall plate')
    m.box((5, 3.6, 15.4), (11, 14.6, 16), 'galv', 'wall plate')
    for x in (5.6, 10.4):
        for y in (4.2, 14.0): m.bolt(x, y, 15.4, 'north', 0.5)
    m.g('bracket')
    m.box((7.3, 11.8, 12.4), (8.7, 12.8, 15.4), 'galv', 'bracket arm')
    m.box((6.9, 11.4, 15.0), (9.1, 13.2, 15.4), 'galv', 'arm foot')
    m.box((3.2, 11.2, 11.0), (12.8, 12.0, 12.4), 'galv', 'yoke top')
    for x0 in (3.2, 12.0): m.box((x0, 7.4, 11.0), (x0 + 0.8, 12.0, 12.4), 'galv', 'yoke cheek')
    m.cyl((2.6, 9.5, 11.7), 0.6, 0.6, 'x', 'paint:black', 'tilt clamp knob')
    m.cyl((12.8, 9.5, 11.7), 0.6, 0.6, 'x', 'paint:black', 'tilt clamp knob')
    m.g('horn')
    m.cyl((8, 9.5, 10.6), 1.7, 4.0, 'z', 'paint:ral7035', 'compression driver', cap='paint:ral7035')
    m.cyl((8, 9.5, 14.6), 1.0, 0.4, 'z', 'paint:dark', 'driver back cap', cap='paint:dark')
    for (r, z0, ln) in ((2.3, 9.2, 1.4), (2.9, 7.7, 1.5), (3.5, 6.3, 1.4), (4.1, 5.1, 1.2)):
        m.cyl((8, 9.5, z0), r, ln, 'z', 'paint:ral7035', 'horn flare')
    m.cyl((8, 9.5, 4.7), 4.4, 0.4, 'z', 'paint:#a2a6a8', 'mouth rim', cap='paint:#141516')
    m.cyl((8, 9.5, 3.9), 1.4, 1.2, 'z', 'paint:#5a5e62', 're-entrant phase plug', cap='paint:#3a3e42')
    lab(m, 5.0, 11.2, 11.0, 12.0, 11.0, 'PA 30 W', 'north', kind='plate')
    m.g('junction box')
    m.box((5.6, 4.0, 13.4), (10.4, 7.2, 15.4), 'ral7035', 'line junction box', north='label:PA|LINE')
    for x in (5.9, 10.1):
        for y in (4.3, 6.9): m.bolt(x, y, 13.4, 'north', 0.3)
    jb = surf(m, 'north', 0, 0, 13.4, 'PA LINE')            # TilePaSpeaker: line lamp = 12 V + station, TEST = announce
    ctl(m, jb, 'lamp', 'line', 6.9, 4.8, 13.4, w=0.5, h=0.5, col='30e060')
    ctl(m, jb, 'push', 'test', 9.1, 4.8, 13.4, w=0.6, h=0.6, col='e8e8e8')
    m.gland(9.6, 7.2, 14.6, face='up', cable='cable:1d1d21', length=1.0)
    run(m, [(9.6, 8.8, 14.6), (9.6, 9.5, 14.6), (9.6, 9.5, 15.1), (8.6, 9.5, 15.1)], 0.2, 'cable:1d1d21', 'speaker cable')
    m.box((4.6, 4.6, 15.2), (5.0, 6.4, 15.4), 'cable:3a8a3a', 'earth bond')
    m.g('conduit')
    m.cyl((8, 3.3, 15.0), 0.4, 0.7, 'y', 'galv', 'conduit to terminal')
    m.cyl((8, 3.75, 15.0), 0.5, 0.25, 'y', 'galv', 'conduit coupling')
    m.terminal_12v(8, 0.9, 16, 'north')
    m.save()


# ------------------------------------------------------------------------------------------------ maintenance depot
def maintenance_depot():
    m = Model('maintenance_depot')
    m.g('pad and pit edge')
    m.box((0.6, 0, 0.3), (15.4, 0.6, 15.0), 'concrete', 'concrete apron')
    m.box((0.6, 0.6, 0.3), (15.4, 0.8, 1.5), 'haz', 'pit edge hazard coping')
    m.box((0.6, -3.0, 0.0), (15.4, 0.6, 0.3), 'concrete', 'inspection pit wall')
    m.box((0.6, -0.4, 0.3), (15.4, 0.0, 0.9), 'metal:steel', 'pit edge angle')
    for x in (2.5, 13.5): m.box((x - 0.2, 0.8, 0.5), (x + 0.2, 4.0, 0.9), 'paint:ral1023', 'pit guard post')
    m.box((2.3, 3.6, 0.55), (13.7, 3.9, 0.85), 'paint:ral1023', 'pit guard chain bar')
    m.box((7.4, 0.8, 0.6), (8.6, 1.5, 1.2), 'paint:#3a3f46', 'pit lamp housing')
    ctl(m, surf(m, 'north', 0, 0, 0.6, 'PIT'), 'lamp', 'pitlamp', 8.0, 1.15, 0.6, w=1.0, h=0.55, col='fff0c0')   # lit = stock over the pit
    # mobile column lift (lifting jack)
    m.g('lifting jack')
    m.box((1.4, 0.6, 2.4), (5.6, 1.4, 7.4), 'paint:ral1023', 'jack base')
    for (x, z) in ((1.9, 2.9), (5.1, 2.9), (1.9, 6.9), (5.1, 6.9)): m.cyl((x - 0.3, 0.9, z), 0.5, 0.6, 'x', 'paint:black', 'castor')
    m.box((2.6, 1.4, 5.4), (4.4, 15.0, 7.0), 'paint:ral1023', 'lift column')
    m.box((2.3, 15.0, 5.1), (4.7, 15.6, 7.3), 'paint:ral1023', 'column cap')
    m.box((2.8, 1.4, 5.2), (4.2, 15.0, 5.4), 'metal:steel', 'column guide')
    m.box((2.2, 8.0, 3.6), (4.8, 10.0, 5.2), 'paint:#3a3f46', 'carriage')
    for x in (2.4, 4.0): m.box((x, 8.0, 0.9), (x + 0.6, 8.6, 3.6), 'metal:steel', 'lifting fork')
    m.cyl((3.5, 1.4, 6.2), 0.45, 6.4, 'y', 'metal:steel', 'hydraulic ram')
    m.box((4.4, 4.2, 5.6), (5.8, 7.4, 7.0), 'ral7035', 'jack control box')
    jack = surf(m, 'north', 0, 0, 5.6, 'JACK')               # TileMaintenanceDepot: raise / lower / E-stop the jack
    ctl(m, jack, 'push', 'jackup', 5.1, 6.7, 5.6, w=0.5, h=0.5, col='2fcb5a')
    ctl(m, jack, 'push', 'jackdown', 5.1, 5.8, 5.6, w=0.5, h=0.5, col='26282c')
    ctl(m, jack, 'mushroom', 'jackstop', 5.1, 4.8, 5.6, w=0.8, h=0.8, col='c81e1e')
    lab(m, 2.6, 11.0, 4.4, 12.4, 5.4, 'MAX|5 T', 'north')
    run(m, [(5.8, 6.0, 6.3), (6.3, 6.0, 6.3), (6.3, 0.9, 6.3), (7.6, 0.9, 6.3)], 0.15, 'cable:1d1d21', 'jack control cable')
    # control pillar
    m.g('depot control pillar')
    m.box((6.8, 0.6, 3.0), (9.8, 11.0, 5.2), 'ral7035', 'control pillar')
    m.box((6.6, 11.0, 2.8), (10.0, 11.5, 5.4), 'ral7035', 'pillar cap')
    m.box((7.2, 7.2, 2.95), (9.4, 9.6, 3.0), 'paint:black', 'status display')
    lab(m, 7.0, 9.8, 9.6, 10.8, 3.0, 'DEPOT', 'north')
    pil = surf(m, 'north', 0, 0, 2.95, 'DEPOT')              # status display lines, start / stop repairs, lamps
    for i, y in enumerate((9.15, 8.4, 7.65)):
        ctl(m, pil, 'digits', 'line%d' % (i + 1), 8.3, y, 2.95, w=1.9, h=0.6, col='ffb02e', fmt='%s')
    ctl(m, pil, 'push', 'start', 7.6, 6.2, 2.95, w=0.6, h=0.6, col='2fcb5a')
    ctl(m, pil, 'push', 'stop', 8.9, 6.2, 2.95, w=0.6, h=0.6, col='d8322c')
    ctl(m, pil, 'lamp', 'stock', 7.6, 5.0, 2.95, w=0.6, h=0.6, col='ffb02e')
    ctl(m, pil, 'lamp', 'ready', 8.9, 5.0, 2.95, w=0.6, h=0.6, col='30e060')
    m.terminal_12v(8.3, 0.9, 3.0, 'north')
    # hose reel post + floodlight mast with the depot radio aerial
    m.g('hose reels and mast')
    m.box((12.2, 0.6, 2.4), (14.8, 1.0, 5.0), 'galv', 'post base plate')
    m.box((13.1, 1.0, 3.3), (13.9, 22.0, 4.1), 'galv', 'mast')
    for (y, col, nm) in ((4.2, 'cable:f2c318', 'air hose reel'), (8.6, 'cable:d8322c', 'water hose reel')):
        m.cyl((13.5, y, 2.0), 1.7, 0.25, 'z', 'paint:ral3000', 'reel flange')
        m.cyl((13.5, y, 2.25), 1.2, 1.0, 'z', col, nm)
        m.cyl((13.5, y, 3.25), 1.7, 0.25, 'z', 'paint:ral3000', 'reel flange')
        m.box((13.3, y - 0.2, 0.9), (13.7, y + 0.2, 2.0), col, 'hose tail')
        m.box((13.0, y - 0.4, 0.6), (14.0, y + 0.4, 0.9), 'metal:brass', 'hose coupling')
    m.box((12.8, 21.0, 2.4), (14.2, 22.4, 4.6), 'paint:dark', 'floodlight housing')
    m.box((12.9, 21.1, 2.3), (14.1, 22.3, 2.4), 'lamp:fff6d8', 'floodlight lens')
    m.cyl((13.5, 22.0, 3.7), 0.6, 0.8, 'y', 'paint:#5a3a14', 'amber beacon')
    ctl(m, surf(m, 'north', 0, 0, 3.7 - 0.6 * 0.92, 'BEACON'), 'lamp', 'beacon', 13.5, 22.4, 0, w=0.9, h=0.6, col='ff9a20', blink=True)
    m.cyl((13.5, 22.8, 3.7), 0.12, 7.8, 'y', 'paint:black', 'radio whip aerial')
    run(m, [(13.9, 12.0, 4.1), (13.9, 12.0, 4.5), (13.9, 1.0, 4.5)], 0.18, 'galv', 'mast conduit')
    # compressor: horizontal receiver, motor, pump, belt guard, gauge
    m.g('compressor')
    m.cyl((1.6, 3.2, 11.5), 2.2, 6.4, 'x', 'paint:ral3000', 'air receiver', cap='paint:ral3000')
    for x in (2.4, 7.0):
        m.box((x - 0.4, 0.6, 9.9), (x + 0.4, 1.4, 13.1), 'paint:dark', 'receiver foot')
    m.box((2.0, 5.4, 10.0), (7.6, 5.8, 13.4), 'paint:dark', 'deck plate')
    m.cyl((2.2, 7.0, 12.2), 1.1, 2.6, 'x', 'paint:ral5010', 'electric motor', cap='mesh')
    m.box((3.0, 5.8, 11.1), (4.2, 6.2, 13.3), 'paint:ral5010', 'motor foot')
    m.box((3.2, 8.0, 11.6), (4.0, 8.6, 12.8), 'paint:ral5010', 'motor terminal box')
    m.box((5.4, 5.8, 10.4), (7.4, 8.4, 12.2), 'paint:#5a5e62', 'pump crankcase')
    m.box((5.6, 8.4, 10.6), (7.2, 9.4, 12.0), 'fins', 'cylinder head')
    m.box((4.8, 5.8, 9.8), (7.8, 9.0, 10.4), 'paint:ral1023', 'belt guard')
    m.cyl((7.6, 4.0, 13.8), 0.5, 0.5, 'z', 'paint:#2b2f36', 'pressure gauge case')
    ctl(m, surf(m, 'south', 0, 0, 14.3, 'AIR'), 'gauge', 'air', 7.6, 4.0, 14.3, w=0.8, h=0.8, col='f2f0e6', max=100)   # tenths of a bar
    m.box((7.4, 3.0, 13.5), (8.2, 3.6, 14.1), 'paint:#3a3f46', 'pressure switch')
    m.cyl((8.0, 1.6, 11.5), 0.3, 0.8, 'x', 'metal:brass', 'outlet valve')
    m.cyl((1.0, 1.8, 11.5), 0.25, 0.6, 'x', 'metal:brass', 'drain cock')
    lab(m, 2.6, 3.6, 6.6, 4.6, 9.3, 'AIR 10 BAR', 'north', kind='plate')
    run(m, [(8.3, 0.75, 6.0), (8.3, 0.75, 9.6), (6.0, 0.75, 9.6)], 0.2, 'cable:1d1d21', 'compressor feed (12 V control)')
    # tool cabinet: roll cab with drawers and a top chest
    m.g('tool cabinet')
    m.box((9.2, 0.6, 9.0), (15.0, 1.4, 14.6), 'paint:dark', 'castor frame')
    for (x, z) in ((9.7, 9.5), (14.5, 9.5), (9.7, 14.1), (14.5, 14.1)): m.cyl((x, 0.6, z), 0.35, 0.5, 'y', 'paint:black', 'castor')
    m.box((9.2, 1.4, 9.0), (15.0, 9.6, 14.6), 'paint:ral3000', 'roll cabinet')
    for i, (y0, y1) in enumerate(((1.8, 3.6), (3.8, 5.2), (5.4, 6.6), (6.8, 7.8), (8.0, 9.2))):
        m.box((9.5, y0, 8.85), (14.7, y1, 9.0), 'paint:#b42a24', 'drawer front')
        m.box((10.4, y1 - 0.45, 8.6), (13.8, y1 - 0.2, 8.85), 'metal:alu', 'drawer handle')
    m.box((9.0, 9.6, 8.8), (15.2, 10.0, 14.8), 'paint:dark', 'worktop')
    m.box((9.4, 10.0, 9.6), (14.8, 12.6, 14.4), 'paint:ral3000', 'top chest')
    m.box((9.3, 12.6, 9.5), (14.9, 13.0, 14.5), 'paint:#b42a24', 'chest lid')
    m.box((11.2, 13.0, 11.6), (13.0, 13.3, 12.4), 'metal:alu', 'lid handle')
    for i, (y0, y1) in enumerate(((10.2, 11.2), (11.3, 12.4))):
        m.box((9.7, y0, 9.45), (14.5, y1, 9.6), 'paint:#b42a24', 'chest drawer')
    m.box((10.0, 10.0, 13.0), (10.4, 11.8, 13.4), 'metal:steel', 'spanner')
    m.box((11.0, 10.0, 12.8), (12.6, 10.6, 13.6), 'paint:ral1023', 'impact gun')
    lab(m, 10.0, 7.0, 14.2, 7.7, 14.6, 'TOOLS', 'south')
    m.save()


# ------------------------------------------------------------------------------------------------ signal box lever frame
LEVERS = [(1, 'ral1023', 'distant'), (2, 'ral3000', 'home'), (3, 'ral3000', 'starter'), (4, 'ral5010', 'facing point lock'),
          (5, 'ral9005', 'points'), (6, 'white', 'spare'), (7, 'ral9005', 'points'), (8, 'ral5010', 'facing point lock'),
          (9, 'ral3000', 'starter'), (10, 'ral3000', 'home'), (11, 'ral3000', 'shunt'), (12, 'ral1023', 'distant')]
REVERSED = {2, 3, 5}       # pulled off for a route
LEVER_RGB = {'ral1023': 'f0c418', 'ral3000': 'aa1e1a', 'ral5010': '16427c', 'ral9005': '161618', 'white': 'ececec'}


def signal_box():
    m = Model('signal_box')
    m.g('lever frame')
    m.box((0.2, 0, 6.8), (15.8, 3.0, 11.2), 'paint:#2a2d30', 'cast iron frame bed')
    for x in (0.2, 15.0): m.box((x, 0, 6.4), (x + 0.8, 3.4, 13.2), 'paint:#2a2d30', 'frame standard')
    m.box((0.2, 0, 11.2), (15.8, 5.0, 13.0), 'paint:dark', 'locking trough')
    for x in (4.0, 8.0, 12.0): seam(m, (x, 1.0, 13.0), (x + 0.1, 4.8, 13.05), 'trough cover joint')
    lab(m, 5.0, 3.6, 11.0, 4.6, 13.05, 'TAPPET LOCKING', 'south', kind='plate')
    m.box((0.2, 3.0, 6.6), (15.8, 3.3, 11.4), 'paint:#3a3d40', 'quadrant top plate')
    m.g('levers')
    levers = m.surface('north', face=9.23, title='LEVER FRAME')
    for i, (n, col, kind) in enumerate(LEVERS):
        x = 1.3 + i * 1.22
        m.box((x - 0.3, 2.95, 7.1), (x + 0.3, 3.35, 10.9), 'paint:black', 'lever slot')
        for dx in (-0.48, 0.34): m.box((x + dx, 3.3, 8.2), (x + dx + 0.14, 4.4, 9.8), 'paint:#3a3d40', 'quadrant')
        # the lever itself is a live control (pulls over, interlocked): drawn by the game, see TileSignalBox
        m.ctl(levers, 'pull', 'lever%d' % n, 16 - x, 3.2, w=0.46, h=10.4, col=LEVER_RGB[col], label='', kind=kind)
        lab(m, x - 0.5, 1.6, x + 0.5, 2.6, 6.8, str(n), 'north', name='frame number %d' % n)
    m.g('back wall and diagram')
    m.box((-1.0, 0, 15.6), (17.0, 24.0, 16.0), 'wood', 'panelled back wall')
    m.box((1.0, 17.0, 15.3), (15.0, 23.4, 15.6), 'paint:#ece8d8', 'track diagram')
    for y in (19.0, 21.4): m.box((1.4, y, 15.2), (14.6, y + 0.3, 15.3), 'paint:black', 'running line')
    m.box((6.0, 19.0, 15.2), (6.3, 21.7, 15.3), 'paint:black', 'crossover', rot={'axis': 'z', 'angle': -45, 'origin': [7.3, 20.35, 15.25]})
    m.box((10.8, 19.3, 15.2), (13.6, 19.6, 15.3), 'paint:black', 'siding', rot={'axis': 'z', 'angle': 22.5, 'origin': [10.8, 19.3, 15.25]})
    diagram = m.surface('north', face=15.28, title='DIAGRAM')
    for k, (x, y, lv) in enumerate(((2.4, 19.8, 2), (4.6, 22.2, 1), (9.2, 19.8, 3), (12.8, 22.2, 10), (8.6, 22.2, 9))):
        m.ctl(diagram, 'lamp', 'sig%d' % lv, 16 - (x + 0.25), y + 0.25, w=0.55, h=0.55, col='30e060', label='')   # lit = lever %d pulled + signal off
    for k, (x, y) in enumerate(((3.4, 18.6), (7.8, 18.6), (11.4, 21.0))):
        m.ctl(diagram, 'lamp', 'tc%d' % (k + 1), 16 - (x + 0.6), y + 0.125, w=1.2, h=0.3, col='ff5030', label='')   # lit red = occupied
    lab(m, 4.0, 22.4, 12.0, 23.2, 15.3, 'IR EXTRAS SIGNAL BOX', 'north')
    m.cyl((15.9, 13.8, 15.2), 1.0, 0.4, 'z', 'gauge:clock', 'signal box clock', cap='gauge:clock')
    m.g('block shelf')
    m.box((-0.6, 12.0, 13.2), (16.6, 12.6, 15.6), 'wood', 'block shelf')
    for x in (0.2, 7.7, 15.2): m.box((x, 9.0, 14.6), (x + 0.6, 12.0, 15.6), 'paint:dark', 'shelf bracket')
    for i, (x, state) in enumerate(((0.4, 'LINE|CLEAR'), (5.8, 'TRAIN ON|LINE'), (11.2, 'LINE|BLOCKED'))):
        m.box((x, 12.6, 13.6), (x + 4.4, 17.0, 15.5), 'wood', 'block instrument case')
        m.box((x - 0.1, 17.0, 13.5), (x + 4.5, 17.4, 15.6), 'wood', 'instrument cornice')
        m.box((x + 0.9, 14.6, 13.5), (x + 3.5, 16.8, 13.6), 'paint:#f2f0e6', 'block indicator dial face')
        lab(m, x + 0.4, 13.2, x + 4.0, 14.4, 13.5, 'TO BOX ' + 'ABC'[i], 'north')
        m.box((x + 0.3, 16.9, 13.3), (x + 0.6, 17.0, 13.6), 'metal:brass', 'commutator stop')
        inst = m.surface('north', face=13.48, title='BLOCK ' + 'ABC'[i])
        m.ctl(inst, 'gauge', 'ind' + 'abc'[i], 16 - (x + 2.2), 15.7, w=2.0, h=2.0, col='f2f0e6', label='FROM ' + 'ABC'[i], max=2)
        m.ctl(inst, 'push', 'bell' + 'abc'[i], 16 - (x + 3.8), 13.8, w=0.6, h=0.6, col='c8a040', label='BELL')
        m.ctl(inst, 'selector', 'com' + 'abc'[i], 16 - (x + 0.6), 15.4, w=0.9, h=0.9, col='c8a040', label='', pos=['BLOCKED', 'CLEAR', 'TOL'])
    for x in (5.1, 10.5):
        m.box((x - 0.6, 12.6, 14.0), (x + 0.6, 13.0, 15.2), 'wood', 'bell base')
        m.cyl((x, 13.0, 14.6), 0.6, 0.7, 'y', 'metal:brass', 'block bell')
    m.g('register desk')
    m.box((-1.0, 7.0, 6.4), (0.2, 7.5, 13.0), 'wood', 'train register desk')
    m.box((-0.9, 7.5, 7.4), (0.1, 7.7, 11.6), 'paint:white', 'train register book')
    m.box((-0.5, 0, 7.0), (0.0, 7.0, 7.5), 'wood', 'desk leg')
    m.terminal_12v(14.0, 0.9, 6.8, 'north')
    run(m, [(14.0, 3.3, 6.3), (14.0, 4.0, 6.3), (14.0, 4.0, 11.0)], 0.15, 'cable:1d1d21', 'lever-band cable')
    m.save()


# ------------------------------------------------------------------------------------------------ NX desk (3 x 2)
def nx_desk():
    m = Model('nx_desk')
    m.g('plinth and pedestals')
    m.box((-14.5, 0, 1.6), (30.5, 0.8, 28.4), 'paint:black', 'recessed plinth')
    for (x0, x1, nm) in ((-15.0, -8.0, 'left pedestal'), (4.0, 12.0, 'centre equipment bay'), (24.0, 31.0, 'right pedestal')):
        m.box((x0, 0.8, 1.0), (x1, 11.6, 29.0), 'paint:ral7035', nm)
    m.box((-8.0, 0.8, 14.0), (24.0, 11.6, 16.0), 'paint:ral7032', 'centre spine (modesty panel)')
    m.box((-8.0, 9.6, 1.0), (24.0, 11.6, 29.0), 'paint:ral7035', 'apron')
    for (x0, x1) in ((-8.0, 4.0), (12.0, 24.0)):
        m.box((x0 + 0.4, 0.8, 1.6), (x1 - 0.4, 1.4, 2.2), 'metal:steel', 'foot rail')
        m.box((x0 + 0.4, 0.8, 27.8), (x1 - 0.4, 1.4, 28.4), 'metal:steel', 'foot rail')
        m.box((x0 + 0.3, 8.6, 10.0), (x1 - 0.3, 9.6, 14.0), 'mesh', 'cable tray')
    m.g('drawers')
    for (x0, x1) in ((-15.0, -8.0), (24.0, 31.0)):
        for zf, sgn in ((1.0, -1), (29.0, 1)):
            for (y0, y1) in ((1.4, 4.4), (4.8, 7.0), (7.4, 9.2)):
                a, b = (x0 + 0.5, y0, zf + sgn * 0.12), (x1 - 0.5, y1, zf)
                m.box((a[0], a[1], min(a[2], b[2])), (b[0], b[1], max(a[2], b[2])), 'paint:#b9bbb8', 'drawer front')
                h = (zf + sgn * 0.12, zf + sgn * 0.35)
                m.box(((x0 + x1) / 2 - 1.2, y1 - 0.7, min(h)), ((x0 + x1) / 2 + 1.2, y1 - 0.4, max(h)), 'metal:alu', 'drawer pull')
    m.g('equipment bay')
    for zf, sgn, face in ((1.0, -1, 'north'), (29.0, 1, 'south')):
        for (x0, x1) in ((4.4, 7.9), (8.1, 11.6)):
            z0, z1 = sorted((zf, zf + sgn * 0.1))
            m.box((x0, 1.2, z0), (x1, 9.2, z1), 'paint:#b9bbb8', 'bay door')
            vz = (z0 - 0.05, z0) if sgn < 0 else (z1, z1 + 0.05)
            m.box((x0 + 0.5, 1.8, vz[0]), (x1 - 0.5, 4.2, vz[1]), 'louvre', 'door vent')
            hx = x1 - 0.5 if x0 < 8 else x0 + 0.3
            m.box((hx, 4.8, min(zf + sgn * 0.1, zf + sgn * 0.35)), (hx + 0.2, 6.6, max(zf + sgn * 0.1, zf + sgn * 0.35)), 'metal:alu', 'door handle')
        lab(m, 5.0, 9.8, 11.0, 10.8, zf, 'NX PANEL' if sgn < 0 else 'RELAY BAY', face, kind='plate')
    m.g('desk top')
    m.box((-15.0, 11.6, 0.0), (31.0, 12.4, 30.0), 'paint:ral7016', 'top carcass')
    m.box((-14.4, 12.4, 0.8), (30.4, 13.0, 29.2), 'paint:#0a1420', 'mimic panel bed (drawn on by the desk)')
    m.box((-15.0, 12.0, -0.5), (31.0, 13.6, 0.8), 'paint:#3a2a20', 'padded arm rest (front)')
    m.box((-15.0, 12.0, 29.2), (31.0, 13.6, 30.5), 'paint:#3a2a20', 'padded arm rest (back)')
    m.box((-15.5, 12.0, 0.0), (-14.4, 13.6, 30.0), 'wood', 'end rail')
    m.box((30.4, 12.0, 0.0), (31.5, 13.6, 30.0), 'wood', 'end rail')
    for x in (-15.2, 30.9):
        m.box((x, 11.4, -0.6), (x + 0.4, 13.7, 0.0), 'metal:steel', 'corner guard')
    m.g('function buttons (front fascia)')
    m.box((-6.0, 10.2, 0.6), (22.0, 11.6, 1.0), 'paint:dark', 'function strip')
    fn = surf(m, 'north', 0, 0, 0.6, 'FUNCTIONS')            # TileNXDesk: the fascia function buttons (desk top is code-drawn)
    for i, (txt, cid, col) in enumerate((('ALL SIG ON', 'allsig', 'd8322c'), ('EMERG REPL', 'emerg', 'd8322c'), ('CANCEL', 'cancel', 'e8e8e8'),
                                         ('LAMP TEST', 'lamptest', 'e8e8e8'), ('DAY NIGHT', 'night', '2f7ce0'), ('ALARM ACK', 'ack', 'f2c318'))):
        x = -4.6 + i * 4.8
        ctl(m, fn, 'push', cid, x, 10.9, 0.6, w=0.7, h=0.7, col=col)
        lab(m, x + 0.7, 10.4, x + 4.0, 11.4, 0.6, txt.replace(' ', '|'), 'north')
    lab(m, -14.6, 9.6, -8.4, 11.0, 1.0, 'NX DESK|ENTRANCE EXIT', 'north', kind='plate')
    m.g('end panels')
    for x, face in ((-15.0, 'west'), (31.0, 'east')):
        for z in (6.0, 15.0, 24.0):
            seam(m, (x - 0.05 if face == 'west' else x, 1.2, z), (x if face == 'west' else x + 0.05, 11.2, z + 0.12))
    m.terminal_12v(30.4, 1.0, 9.0, 'east')
    run(m, [(31.0, 3.4, 9.0), (31.0, 4.4, 9.0), (31.0, 4.4, 4.0)], 0.18, 'galv', 'terminal conduit')
    m.save(item_gui=0.3)


# ------------------------------------------------------------------------------------------------ control desk
def control_desk():
    m = Model('control_desk')
    m.g('carcass')
    m.box((0.6, 0, 1.6), (15.4, 0.8, 15.6), 'paint:black', 'recessed plinth')
    for (x0, x1) in ((0.0, 4.0), (12.0, 16.0)):
        m.box((x0, 0.8, 1.0), (x1, 12.0, 16.0), 'paint:ral7035', 'pedestal')
        for (y0, y1) in ((4.0, 6.6), (6.9, 9.0), (9.3, 11.4)):
            m.box((x0 + 0.4, y0, 0.88), (x1 - 0.4, y1, 1.0), 'paint:#b9bbb8', 'drawer front')
            m.box((x0 + 1.2, y1 - 0.6, 0.65), (x1 - 1.2, y1 - 0.35, 0.88), 'metal:alu', 'drawer pull')
    m.box((4.0, 0.8, 13.6), (12.0, 12.0, 16.0), 'paint:ral7032', 'modesty panel')
    m.box((4.0, 10.4, 1.0), (12.0, 12.0, 13.6), 'paint:ral7035', 'apron')
    m.box((4.3, 9.4, 4.0), (11.7, 10.4, 13.0), 'mesh', 'cable tray')
    m.box((4.4, 0.8, 1.6), (11.6, 1.4, 2.2), 'metal:steel', 'foot rail')
    for x0 in (4.6, 8.2): m.box((x0, 1.4, 13.5), (x0 + 3.2, 4.2, 13.6), 'louvre', 'modesty vent')
    m.g('desk top (drawn on by the desk)')
    m.box((-0.5, 12.0, 0.0), (16.5, 13.0, 16.5), 'paint:ral7016', 'top carcass')
    m.box((0.5, 13.0, 0.5), (15.5, 14.0, 15.5), 'paint:#1a1e24', 'button mat')
    m.box((-0.5, 13.0, -0.4), (16.5, 14.4, 0.5), 'paint:#3a2a20', 'padded arm rest')
    m.box((-0.5, 13.0, 0.5), (0.5, 14.2, 15.5), 'wood', 'side trim')
    m.box((15.5, 13.0, 0.5), (16.5, 14.2, 15.5), 'wood', 'side trim')
    for x0, x1 in ((0.96, 7.52), (8.48, 15.04)):
        m.box((x0, 14.0, 9.76), (x1, 14.3, 15.36), 'paint:black', 'screen bezel')
    m.g('instrument upstand')
    m.box((-0.5, 12.0, 15.5), (16.5, 21.0, 16.5), 'paint:ral7016', 'upstand')
    m.box((-0.5, 21.0, 15.2), (16.5, 21.4, 16.5), 'paint:ral7016', 'upstand cap')
    lab(m, 4.5, 19.4, 11.5, 20.6, 15.5, 'CONTROL DESK', 'north', kind='plate')
    m.cyl((8.0, 17.2, 15.1), 1.3, 0.4, 'z', 'gauge:clock', 'desk clock', cap='gauge:clock')
    meters = surf(m, 'north', 0, 0, 15.2, 'METERS')          # TileControlDesk: supply volts and desk load
    for (x, txt, cid, mx) in ((2.6, '12V DC', 'volts', 15), (13.4, 'LOAD A', 'load', 30)):
        m.box((x - 1.3, 15.4, 15.2), (x + 1.3, 18.0, 15.5), 'paint:#2b2f36', 'meter case')
        ctl(m, meters, 'gauge', cid, x, 16.7, 15.2, w=1.8, h=1.8, col='f2f0e6', max=mx)
        lab(m, x - 1.3, 14.4, x + 1.3, 15.3, 15.5, txt, 'north')
    m.box((5.0, 14.6, 15.3), (6.2, 15.8, 15.5), 'mesh', 'alarm buzzer grille')
    up = surf(m, 'north', 0, 0, 15.5, 'DESK')                # lamp test, alarm acknowledge, desk key
    ctl(m, up, 'push', 'lamptest', 10.4, 15.2, 15.5, w=0.6, h=0.6, col='e8e8e8')
    ctl(m, up, 'push', 'ack', 11.4, 15.2, 15.5, w=0.6, h=0.6, col='f2c318')
    ctl(m, up, 'key', 'deskkey', 9.4, 15.2, 15.5, w=0.7, h=0.7, col='c0a040', label='', pos=['ON', 'LOCK'])
    m.g('telephone')
    m.box((16.5, 15.0, 13.0), (17.0, 19.0, 15.6), 'paint:ral7035', 'phone cradle')
    m.box((17.0, 15.4, 13.6), (17.6, 18.6, 14.8), 'paint:black', 'handset')
    m.terminal_12v(14.0, 1.0, 1.0, 'north')
    m.save()


# ------------------------------------------------------------------------------------------------ channel I/O cabinet
def channel_io():
    m = Model('channel_io')
    m.g('cabinet')
    m.box((0, 0, 3), (16, 16, 16), 'ral7035', 'cabinet body (mounting plate face)', north='galv')
    m.box((0, 0, 0), (1.6, 16, 3), 'ral7035', 'door frame')
    m.box((14.4, 0, 0), (16, 16, 3), 'ral7035', 'door frame')
    m.box((1.6, 14.0, 0), (14.4, 16, 3), 'ral7035', 'door frame')
    m.box((1.6, 0, 0), (14.4, 3.5, 3), 'ral7035', 'door frame')
    for y in (3.0, 12.0): m.box((-0.3, y, 0.6), (0.0, y + 1.2, 1.2), 'metal:steel', 'door hinge')
    m.box((15.0, 7.0, -0.4), (15.6, 9.0, 0.0), 'paint:black', 'quarter-turn latch')
    lab(m, 2.0, 14.4, 9.0, 15.6, 0, 'CHANNEL IO', 'north')
    m.box((10.0, 14.3, -0.1), (11.4, 15.7, 0.0), 'warn', 'warning triangle')
    lab(m, 2.0, 1.6, 8.4, 3.0, 0, '64 CH WIRELESS', 'north', kind='plate')
    m.g('wire duct')
    for y in (7.6, 12.4): m.box((1.6, y, 1.4), (14.4, y + 1.2, 3.0), 'paint:#9aa0a6', 'slotted trunking')
    m.box((1.6, 3.5, 1.4), (2.6, 12.4, 3.0), 'paint:#9aa0a6', 'slotted trunking')
    m.box((13.4, 3.5, 1.4), (14.4, 12.4, 3.0), 'paint:#9aa0a6', 'slotted trunking')
    for y in (7.6, 12.4):
        for x in (3.4, 5.4, 7.4, 9.4, 11.4): m.box((x, y + 1.2, 1.38), (x + 0.3, y + 1.21, 3.0), 'paint:#5a5e62', 'duct slot')
    m.g('DIN rails and modules')
    for y in (10.0, 5.2): m.box((2.6, y, 2.6), (13.4, y + 0.7, 3.0), 'metal:alu', 'DIN rail')
    upper = [('ral7016', 'IN', '30e060'), ('ral7016', 'IN', '30e060'), ('ral7016', 'IN', '30e060'), ('ral7016', 'IN', '303830'),
             ('ral5010', 'OUT', 'ffb02e'), ('ral5010', 'OUT', '383020'), ('ral5010', 'OUT', 'ffb02e'), ('paint:#3a8a3a', 'BUS', '5bcefa'),
             ('paint:#3a8a3a', 'BUS', '5bcefa')]
    x = 3.0
    leds = surf(m, 'north', 0, 0, 1.5, 'CHANNELS')           # TileChannelIO: the channels it reads (IN), writes (OUT), bus
    seen = {}
    for (col, kind, led) in upper:
        c = col if col.startswith('paint:') else 'paint:' + col
        m.box((x, 8.9, 1.5), (x + 1.1, 11.8, 2.6), c, kind.lower() + ' module')
        m.box((x + 0.1, 11.4, 1.45), (x + 1.0, 11.75, 1.5), 'metal:brass', 'terminal screws')
        m.box((x + 0.1, 8.95, 1.45), (x + 1.0, 9.3, 1.5), 'metal:brass', 'terminal screws')
        seen[kind] = seen.get(kind, 0) + 1
        ctl(m, leds, 'lamp', '%s%d' % (kind.lower(), seen[kind]), x + 0.55, 10.8, 1.5, w=0.4, h=0.4,
            col={'IN': '30e060', 'OUT': 'ffb02e', 'BUS': '5bcefa'}[kind])
        m.box((x + 0.15, 9.5, 1.45), (x + 0.95, 10.4, 1.5), 'paint:#e8e8e0', 'module legend')
        m.box((x + 0.3, 11.8, 1.9), (x + 0.8, 12.4, 2.3), 'cable:' + ('c81e1e' if kind == 'OUT' else '2f7ce0'), 'wire to duct')
        x += 1.15
    m.box((3.0, 3.6, 1.2), (6.8, 7.2, 2.6), 'paint:#5a5e62', 'power supply', north='label:PSU|12V DC')
    m.box((3.6, 7.2, 1.6), (4.0, 7.6, 2.0), 'cable:c81e1e', 'PSU + wire')
    m.box((5.6, 7.2, 1.6), (6.0, 7.6, 2.0), 'cable:1d1d21', 'PSU 0 V wire')
    ctl(m, surf(m, 'north', 0, 0, 1.2, 'PSU'), 'lamp', 'psu', 6.55, 4.15, 1.2, w=0.3, h=0.3, col='30e060')   # 12 V in
    x = 7.2
    for i in range(10):
        col = 'paint:#c8a050' if i == 0 else 'paint:#e8e8e0' if i < 8 else 'paint:#3a8a3a'
        m.box((x, 4.4, 1.7), (x + 0.5, 6.6, 2.6), col, 'terminal block' if i else 'fuse terminal')
        m.box((x + 0.1, 6.2, 1.65), (x + 0.4, 6.5, 1.7), 'metal:steel', 'screw')
        x += 0.55
    m.box((7.2, 3.6, 1.6), (12.7, 4.4, 2.6), 'paint:#2f7ce0', 'bundled cable connector', north='label:BUNDLED')
    m.box((12.8, 4.4, 1.4), (13.3, 7.4, 2.6), 'paint:ral3000', 'redstone port')
    m.terminal_12v(12.0, 1.0, 0, 'north')
    m.save()


# ------------------------------------------------------------------------------------------------ station doors controller
def station_doors():
    m = Model('station_doors')
    m.g('base')
    m.box((2, 0, 2), (14, 0.6, 14), 'galv', 'base plate')
    for (x, z) in ((2.6, 2.6), (13.4, 2.6), (2.6, 13.4), (13.4, 13.4)):
        m.bolt(x, 0.6, z, 'up', 0.6)
    m.box((3, 0.6, 4.6), (13, 2.6, 11.4), 'haz', 'hazard plinth')
    m.g('cabinet')
    m.box((4, 2.6, 5), (12, 17, 11), 'metal:steel', 'stainless cabinet')
    seam(m, (4.3, 3.0, 4.95), (11.7, 3.12, 5.0))
    seam(m, (4.3, 16.6, 4.95), (11.7, 16.72, 5.0))
    seam(m, (4.3, 3.0, 4.95), (4.42, 16.72, 5.0))
    seam(m, (11.58, 3.0, 4.95), (11.7, 16.72, 5.0))
    for y in (4.0, 15.0): stub(m, 12.0, y, 5.6, 'east', 0.5, 1.0, 0.15, 'metal:steel', 'door hinge')
    m.cyl((4.2, 9.0, 4.85), 0.3, 0.15, 'z', 'metal:brass', 'door lock')
    m.box((3.5, 17, 4.5), (12.5, 17.6, 11.5), 'metal:steel', 'rain cap')
    m.box((3.5, 16.6, 4.3), (12.5, 17.6, 4.5), 'metal:steel', 'drip edge')
    m.box((5.0, 3.4, 11.0), (11.0, 5.4, 11.1), 'louvre', 'rear vent', faces=('south',))
    m.g('door control panel')
    m.box((5, 11.2, 4.6), (11, 15.6, 5.0), 'paint:black', 'status screen bezel')
    m.box((5.3, 11.5, 4.55), (10.7, 15.3, 4.6), 'paint:#06121a', 'status screen')
    scr = surf(m, 'north', 0, 0, 4.55, 'SCREEN')             # TileStationDoors: phase + train / countdown
    ctl(m, scr, 'digits', 'phase', 8.0, 14.2, 4.55, w=5.0, h=1.2, col='5bcefa', fmt='%s')
    ctl(m, scr, 'digits', 'info', 8.0, 12.6, 4.55, w=5.0, h=1.2, col='5bcefa', fmt='%s')
    pnl = surf(m, 'north', 0, 0, 5.0, 'DOORS')               # lamps = the real door phase; buttons, key and E-stop work them
    for (x, col, txt) in ((5.8, '30e060', 'OPEN'), (8.0, 'f4f4f4', 'SHUT'), (10.2, 'ffb02e', 'LOCK')):
        ctl(m, pnl, 'lamp', 'l' + txt.lower(), x, 10.3, 5.0, w=0.7, h=0.7, col=col)
        lab(m, x - 1.0, 8.9, x + 1.0, 9.8, 5.0, txt, 'north')
    ctl(m, pnl, 'push', 'open', 5.8, 7.6, 5.0, w=0.9, h=0.9, col='2fcb5a')
    ctl(m, pnl, 'push', 'close', 8.0, 7.6, 5.0, w=0.9, h=0.9, col='26282c')
    ctl(m, pnl, 'key', 'mode', 10.2, 7.6, 5.0, w=0.8, h=0.8, col='c0a040', label='', pos=['AUTO', 'MAN', 'LOCK'])
    lab(m, 4.8, 6.1, 7.0, 7.0, 5.0, 'OPEN', 'north')
    lab(m, 7.0, 6.1, 9.0, 7.0, 5.0, 'CLOSE', 'north')
    lab(m, 9.2, 6.1, 11.2, 7.0, 5.0, 'MODE', 'north')
    ctl(m, pnl, 'mushroom', 'estop', 8.0, 4.6, 5.0, w=1.0, h=1.0, col='c81e1e')
    m.box((5.0, 3.4, 4.7), (6.2, 5.6, 5.0), 'mesh', 'chime speaker')
    m.box((9.8, 3.4, 4.6), (11.2, 5.6, 5.0), 'paint:#3a3f46', 'interlock relay housing')
    m.g('beacon and aerial')
    m.cyl((6.0, 17.6, 8.0), 0.8, 1.2, 'y', 'paint:#5a3a14', 'doors beacon')
    ctl(m, surf(m, 'north', 0, 0, 8.0 - 0.8 * 0.92, 'BEACON'), 'lamp', 'beacon', 6.0, 18.2, 0, w=1.1, h=0.9, col='ff9a20', blink=True)   # doors moving
    m.cyl((6.0, 18.8, 8.0), 0.85, 0.2, 'y', 'paint:dark', 'beacon cap')
    m.cyl((10.0, 17.6, 8.0), 0.3, 0.6, 'y', 'paint:dark', 'aerial base')
    m.cyl((10.0, 18.2, 8.0), 0.12, 3.8, 'y', 'paint:black', 'radio aerial')
    m.g('wiring')
    run(m, [(13.6, 3.2, 8.0), (13.6, 4.0, 8.0), (12.0, 4.0, 8.0)], 0.25, 'galv', 'conduit into cabinet')
    m.terminal_12v(13.0, 0.8, 8.0, 'east')
    m.save()


# ------------------------------------------------------------------------------------------------ station master's desk
def monitor(m, x, z, w, col, yrot=0, name='monitor'):
    """the power LED is a live lamp (station_master() places it: on the bezel when square-on, on the foot when angled)"""
    r = {'axis': 'y', 'angle': yrot, 'origin': [x, 13, z]} if yrot else None
    m.box((x - 1.0, 13.0, z - 0.6), (x + 1.0, 13.2, z + 0.8), 'paint:#2b2f36', name + ' stand foot', rot=r)
    m.box((x - 0.3, 13.2, z + 0.2), (x + 0.3, 15.0, z + 0.6), 'paint:#2b2f36', name + ' neck', rot=r)
    m.box((x - w / 2, 14.6, z - 0.2), (x + w / 2, 18.8, z + 0.2), 'paint:black', name + ' bezel', rot=r)
    m.box((x - w / 2 + 0.25, 14.85, z - 0.25), (x + w / 2 - 0.25, 18.55, z - 0.2), 'screen:' + col, name + ' screen', rot=r)


def station_master():
    m = Model('station_master')
    m.g('desk')
    m.box((-0.5, 12.0, 3.5), (16.5, 13.0, 14.5), 'wood', 'desk top')
    m.box((-0.5, 11.8, 3.3), (16.5, 12.2, 3.5), 'paint:#3a2a1e', 'edge band')
    m.box((10.4, 0, 4.0), (16.0, 11.8, 14.0), 'paint:#5a4636', 'drawer pedestal')
    for (y0, y1) in ((0.8, 4.4), (4.8, 8.0), (8.4, 11.4)):
        m.box((10.8, y0, 3.88), (15.6, y1, 4.0), 'wood', 'drawer front')
        m.box((12.4, y1 - 0.7, 3.65), (14.0, y1 - 0.45, 3.88), 'metal:brass', 'drawer pull')
    m.box((0.0, 0, 4.0), (0.8, 11.8, 14.0), 'paint:#5a4636', 'end panel')
    m.box((0.8, 2.0, 13.2), (10.4, 11.8, 14.0), 'paint:#5a4636', 'modesty panel')
    m.g('computer')
    m.box((1.4, 0, 8.0), (4.0, 6.0, 13.0), 'paint:#2b2f36', 'PC tower')
    m.box((1.6, 4.8, 7.95), (3.8, 5.6, 8.0), 'paint:black', 'drive bay')
    ctl(m, surf(m, 'north', 0, 0, 8.0, 'PC'), 'lamp', 'pc', 3.4, 4.2, 8.0, w=0.4, h=0.4, col='5bcefa')   # TileStationMaster: 12 V
    m.box((1.6, 0.6, 7.95), (3.8, 3.6, 8.0), 'mesh', 'PC intake')
    m.g('monitors')
    monitor(m, 8.0, 10.2, 5.0, '30e060', name='network map monitor')
    monitor(m, 3.0, 10.0, 4.2, 'ffb02e', yrot=-22.5, name='timetable monitor')
    monitor(m, 13.0, 10.0, 4.2, 'c8d0d8', yrot=22.5, name='CCTV monitor')
    ctl(m, surf(m, 'north', 0, 0, 9.95, 'MAP'), 'lamp', 'map', 10.0, 14.72, 9.95, w=0.3, h=0.2, col='30e060')   # trains at platforms = blink
    feet = surf(m, 'up', 0, 13.2, 0, 'MONITORS')             # the angled monitors' power LEDs, on their stand feet
    ctl(m, feet, 'lamp', 'timetable', 3.0, 13.2, 9.6, w=0.35, h=0.35, col='30e060')
    ctl(m, feet, 'lamp', 'cctv', 13.0, 13.2, 9.6, w=0.35, h=0.35, col='30e060')
    m.g('desk things')
    m.box((5.4, 13.0, 5.0), (10.6, 13.3, 7.0), 'paint:#2b2f36', 'keyboard')
    for i, z in enumerate((5.3, 5.8, 6.3)):
        m.box((5.7, 13.3, z), (10.3, 13.4, z + 0.35), 'paint:#5a5e62', 'key row')
    m.box((11.2, 13.0, 5.4), (11.9, 13.4, 6.6), 'paint:#2b2f36', 'mouse')
    m.box((12.8, 13.0, 4.6), (15.6, 13.8, 7.2), 'paint:ral7016', 'desk phone')
    m.box((12.9, 13.8, 4.8), (13.8, 14.3, 7.0), 'paint:black', 'phone handset')
    for (x, z) in ((14.2, 5.0), (14.8, 5.0), (14.2, 5.7), (14.8, 5.7), (14.2, 6.4), (14.8, 6.4)):
        m.box((x - 0.2, 13.8, z - 0.2), (x + 0.2, 13.95, z + 0.2), 'paint:#e8e8e8', 'phone key')
    m.box((0.6, 13.0, 4.4), (2.4, 13.3, 6.0), 'paint:#2b2f36', 'PA microphone base')
    ctl(m, surf(m, 'up', 0, 13.3, 0, 'PA'), 'push', 'talk', 1.5, 13.3, 5.0, w=0.5, h=0.5, col='d8322c')   # PA: speak on the station's speakers
    m.cyl((2.0, 13.3, 5.6), 0.15, 2.0, 'y', 'metal:steel', 'gooseneck')
    m.cyl((2.0, 15.3, 5.3), 0.3, 0.8, 'z', 'paint:black', 'microphone head')
    m.box((3.0, 13.0, 4.0), (5.0, 13.15, 6.6), 'paint:#f0ece0', 'station log (left page)')
    m.box((5.0, 13.0, 4.0), (5.1, 13.2, 6.6), 'paint:#7a2a20', 'log spine')
    m.box((3.2, 13.15, 5.0), (4.8, 13.2, 5.1), 'paint:#3a3a3a', 'log entry line')
    m.cyl((15.6, 13.0, 9.0), 0.45, 1.0, 'y', 'paint:#2f7ce0', 'mug')
    lab(m, 6.0, 12.0, 10.0, 12.9, 3.3, 'STATION|MASTER', 'north', kind='plate')
    m.g('sign post and clock')
    m.box((7.5, 13.0, 12.6), (8.5, 22.0, 13.6), 'paint:dark', 'sign post')
    m.box((4.6, 22.0, 12.2), (11.4, 24.4, 13.8), 'paint:ral5010', 'sign board', north='label:STATION MASTER', south='label:STATION MASTER')
    m.cyl((8.0, 24.4, 13.0), 0.5, 0.4, 'y', 'paint:dark', 'sign finial')
    m.cyl((8.0, 19.4, 12.2), 1.1, 0.4, 'z', 'gauge:clock', 'office clock', cap='gauge:clock')
    m.terminal_12v(13.2, 0.9, 3.9, 'north')
    m.save()


# ------------------------------------------------------------------------------------------------ loading silo (gantry head)
def loading_silo():
    m = Model('loading_silo')
    m.g('gantry girders')
    for (z0, z1) in ((2.0, 5.0), (11.0, 14.0)):
        zc = (z0 + z1) / 2
        m.box((-4.0, 12.0, z0), (20.0, 12.8, z1), 'paint:ral6005', 'girder bottom flange')
        m.box((-4.0, 12.8, zc - 0.35), (20.0, 15.2, zc + 0.35), 'paint:ral6005', 'girder web')
        m.box((-4.0, 15.2, z0), (20.0, 16.0, z1), 'paint:ral6005', 'girder top flange')
        for x in (-1.0, 4.0, 12.0, 17.0):
            m.box((x - 0.2, 12.8, z0 + 0.2), (x + 0.2, 15.2, z1 - 0.2), 'paint:ral6005', 'web stiffener')
        for x0 in (-4.0, 19.4):
            m.box((x0, 11.4, z0 - 0.4), (x0 + 0.6, 16.4, z1 + 0.4), 'paint:ral6005', 'end plate (bolted to leg)')
            for y in (12.2, 15.6):
                m.bolt(x0 if x0 < 0 else x0 + 0.6, y, z0 + 0.3, 'west' if x0 < 0 else 'east', 0.4)
                m.bolt(x0 if x0 < 0 else x0 + 0.6, y, z1 - 0.3, 'west' if x0 < 0 else 'east', 0.4)
    for x in (-2.0, 18.0):
        m.box((x - 0.4, 12.0, 5.0), (x + 0.4, 16.0, 11.0), 'paint:ral6005', 'cross beam')
    m.box((-1.5, 12.4, 5.0), (1.0, 12.8, 11.0), 'paint:ral6005', 'bracing', rot={'axis': 'y', 'angle': 45, 'origin': [-0.25, 12.6, 8.0]})
    m.box((15.0, 12.4, 5.0), (17.5, 12.8, 11.0), 'paint:ral6005', 'bracing', rot={'axis': 'y', 'angle': -45, 'origin': [16.25, 12.6, 8.0]})
    m.g('silo')
    m.box((1.2, 16.0, 1.2), (14.8, 17.0, 14.8), 'galv', 'ring beam')
    for (x, z) in ((2.0, 2.0), (14.0, 2.0), (2.0, 14.0), (14.0, 14.0)):
        m.box((x - 0.5, 16.0, z - 0.5), (x + 0.5, 18.0, z + 0.5), 'galv', 'silo leg')
    m.cyl((8, 17.0, 8), 6.0, 13.0, 'y', 'concrete', 'concrete silo shell')
    for y in (21.0, 26.0): m.cyl((8, y, 8), 6.15, 0.25, 'y', 'paint:#8a8a86', 'pour joint')
    m.cyl((8, 30.0, 8), 6.3, 0.6, 'y', 'paint:#7a7e82', 'roof slab')
    m.cyl((8, 30.6, 8), 4.0, 0.6, 'y', 'paint:#7a7e82', 'roof cone')
    m.box((4.0, 31.2, 4.0), (6.4, 31.6, 6.4), 'paint:ral1023', 'roof hatch')
    m.cyl((11.0, 31.2, 5.0), 0.6, 0.8, 'y', 'galv', 'dust vent')
    m.cyl((11.0, 31.2, 11.0), 0.5, 0.5, 'y', 'paint:ral7035', 'radar level sensor')
    m.cyl((14.6, 18.0, 8.0), 0.45, 13.4, 'y', 'galv', 'fill pipe')
    m.box((14.1, 31.0, 7.4), (15.1, 31.6, 8.6), 'galv', 'fill pipe bend')
    lab(m, 4.0, 22.5, 12.0, 24.5, 1.9, 'SILO 1|BALLAST 120 T', 'north')
    lab(m, 4.6, 27.0, 11.4, 28.4, 1.9, 'IR EXTRAS', 'north', kind='plate')
    m.g('hopper and discharge chute')
    for (y, r, h) in ((14.6, 5.0, 1.4), (13.4, 3.6, 1.2), (12.4, 2.4, 1.0)):
        m.cyl((8, y, 8), r, h, 'y', 'paint:ral7032', 'hopper cone')
    m.box((4.6, 10.6, 4.6), (11.4, 12.4, 11.4), 'paint:ral7016', 'slide gate body')
    m.box((11.4, 10.8, 6.0), (14.6, 12.2, 10.0), 'paint:ral7016', 'gate blade housing')
    for (y, r, h) in ((7.4, 2.6, 3.2), (4.4, 2.2, 3.0), (2.0, 1.8, 2.4)):
        m.cyl((8, y, 8), r, h, 'y', 'paint:ral7032', 'telescopic chute')
    m.cyl((8, 0.2, 8), 2.4, 1.8, 'y', 'rubber', 'dust skirt')
    for x in (5.2, 10.8):
        m.box((x - 0.1, 2.6, 7.9), (x + 0.1, 10.6, 8.1), 'metal:steel', 'chute lift rope')
    lab(m, 5.0, 9.0, 11.0, 10.4, 5.4, 'DANGER|MOVING CHUTE', 'north', kind='danger')
    m.g('gate actuator')
    m.cyl((14.6, 11.5, 8.0), 1.0, 2.0, 'x', 'paint:ral5010', 'actuator motor', cap='mesh')
    m.box((14.2, 10.2, 7.0), (15.4, 10.7, 9.0), 'paint:ral5010', 'motor foot')
    m.box((15.0, 12.4, 7.6), (16.0, 13.0, 8.6), 'paint:ral5010', 'motor terminal box')
    m.box((13.0, 10.6, 6.6), (14.6, 12.6, 9.4), 'paint:#5a5e62', 'worm gearbox')
    m.cyl((11.4, 11.5, 8.0), 0.3, 1.6, 'x', 'metal:steel', 'screw spindle')
    m.box((12.6, 12.6, 7.0), (13.6, 13.2, 9.0), 'paint:ral3000', 'limit switch box')
    run(m, [(15.5, 13.0, 8.1), (15.5, 13.6, 8.1), (19.0, 13.6, 8.1), (19.0, 11.0, 8.1)], 0.3, 'cable:202020', 'motor mains cable')
    m.g('walkway')
    m.box((-4.0, 16.0, 14.0), (20.0, 16.6, 17.0), 'mesh', 'grating walkway')
    m.box((-4.0, 15.4, 16.8), (20.0, 16.0, 17.0), 'paint:ral6005', 'walkway edge channel')
    m.box((-4.0, 16.6, 16.85), (16.6, 17.2, 17.0), 'paint:ral1023', 'kick plate')
    m.handrail([(-3.8, 16.8), (6.0, 16.8), (16.4, 16.8)], 16.6, h=4.0, r=0.2)
    m.box((-3.6, 18.4, 16.6), (16.4, 18.7, 16.9), 'paint:ral1023', 'knee rail')
    m.handrail([(-3.8, 16.8), (-3.8, 14.2)], 16.6, h=4.0, r=0.2)
    m.box((19.2, 16.6, 14.2), (19.6, 20.6, 14.6), 'paint:ral1023', 'rail post')
    m.box((16.6, 18.6, 16.7), (19.4, 19.0, 16.9), 'paint:ral1023', 'self-closing ladder gate', rot={'axis': 'y', 'angle': -22.5, 'origin': [16.6, 18.8, 16.8]})
    m.g('caged ladder')
    m.ladder(16.6, 19.4, -15.6, 20.4, 17.0)
    for y in (-6.0, -2.0, 2.0, 6.0, 10.0, 14.0, 18.0):
        m.box((16.2, y, 17.3), (16.5, y + 0.3, 20.0), 'paint:ral1023', 'cage hoop')
        m.box((19.5, y, 17.3), (19.8, y + 0.3, 20.0), 'paint:ral1023', 'cage hoop')
        m.box((16.2, y, 20.0), (19.8, y + 0.3, 20.3), 'paint:ral1023', 'cage hoop')
    for x in (16.2, 18.0, 19.5):
        m.box((x, -6.0, 20.0 if x == 18.0 else 18.6), (x + 0.3, 18.3, 20.3 if x == 18.0 else 18.9), 'paint:ral1023', 'cage strap')
    m.g('local control station')
    m.box((18.0, -9.0, 9.4), (20.0, -4.0, 12.6), 'ral7035', 'local control station')
    lab(m, 9.6, -4.9, 12.4, -4.1, 18.0, 'CHUTE', 'west')
    cs = surf(m, 'west', 18.0, 0, 0, 'CHUTE')                # TileFreightTerminal: slide gate open / close / E-stop
    ctl(m, cs, 'push', 'open', 18.0, -5.8, 10.4, w=0.6, h=0.6, col='2fcb5a')
    ctl(m, cs, 'push', 'close', 18.0, -5.8, 11.6, w=0.6, h=0.6, col='26282c')
    ctl(m, cs, 'lamp', 'lopen', 18.0, -7.0, 10.4, w=0.6, h=0.6, col='30e060')
    ctl(m, cs, 'lamp', 'lshut', 18.0, -7.0, 11.6, w=0.6, h=0.6, col='ff3030')
    ctl(m, cs, 'mushroom', 'estop', 18.0, -8.2, 11.0, w=0.8, h=0.8, col='c81e1e')
    run(m, [(19.0, -4.0, 11.0), (19.0, 11.0, 11.0), (17.0, 11.0, 11.0)], 0.25, 'galv', 'control conduit')
    m.terminal_12v(20.0, -14.4, 10.0, 'west')
    for dz in (-0.5, 0.5):
        m.cyl((19.4, -16.0, 10.0 + dz), 0.18, 1.2, 'y', 'cable:c81e1e' if dz < 0 else 'cable:1d1d21', '12 V lead to ground')
    m.hv_box(20.0, -12.6, 5.0, 'west', phases=3)
    run(m, [(19.0, -8.6, 5.0), (19.0, 11.0, 5.0), (19.0, 11.0, 8.1)], 0.3, 'cable:202020', 'mains cable up the leg')
    m.save()


# ------------------------------------------------------------------------------------------------ unloading pit
def unloading_pit():
    m = Model('unloading_pit')
    m.g('pit frame and hazard edge')
    for (a, b) in (((0, 14, 0), (16, 16, 1.5)), ((0, 14, 14.5), (16, 16, 16)), ((0, 14, 1.5), (1.5, 16, 14.5)), ((14.5, 14, 1.5), (16, 16, 14.5))):
        m.box(a, b, 'paint:dark', 'pit frame angle', up='haz')
    for x in (3.0, 11.8): m.box((x, 12.0, 0), (x + 1.2, 15.6, 16), 'paint:ral6005', 'rail bearer')
    m.g('grating')
    m.box((1.5, 15.4, 1.5), (14.5, 15.6, 14.5), 'mesh', 'bar grating')
    for x in (1.5, 4.5, 7.5, 10.5, 13.5):
        m.box((x, 14.2, 1.5), (x + 0.3, 15.4, 14.5), 'paint:dark', 'bearing bar')
    for z in (5.0, 10.0): m.box((1.5, 14.6, z), (14.5, 15.4, z + 0.3), 'paint:dark', 'cross bar')
    m.g('hopper')
    m.box((1.5, 12.0, 1.5), (14.5, 14.0, 14.5), 'paint:ral7032', 'hopper collar', up=None)
    for ax, ang, a, b, org in (('x', -45, (1.5, 3.6, 1.5), (14.5, 12.0, 2.0), (8, 12, 1.5)), ('x', 45, (1.5, 3.6, 14.0), (14.5, 12.0, 14.5), (8, 12, 14.5)),
                               ('z', 45, (1.5, 3.6, 1.5), (2.0, 12.0, 14.5), (1.5, 12, 8)), ('z', -45, (14.0, 3.6, 1.5), (14.5, 12.0, 14.5), (14.5, 12, 8))):
        m.box(a, b, 'paint:ral7032', 'hopper slope plate', rot={'axis': ax, 'angle': ang, 'origin': list(org)})
    m.box((6.0, 2.0, 6.0), (10.0, 6.4, 10.0), 'paint:ral7032', 'hopper throat')
    m.box((5.6, 0.0, 5.6), (10.4, 0.6, 10.4), 'paint:ral7032', 'throat flange')
    for (x, z) in ((5.9, 5.9), (10.1, 5.9), (5.9, 10.1), (10.1, 10.1)): m.bolt(x, 0.6, z, 'up', 0.35)
    m.box((6.3, -6.0, 6.3), (9.7, 0.0, 9.7), 'paint:ral7032', 'discharge chute')
    m.box((5.4, -4.2, 6.4), (6.3, -2.8, 9.6), 'paint:ral1023', 'vibrator motor')
    m.g('belt conveyor')
    for z in (4.6, 11.0):
        m.box((0.4, -11.4, z), (15.6, -9.6, z + 0.4), 'paint:ral1023', 'conveyor stringer')
    for x in (1.0, 15.0):
        for z in (4.8, 11.2): m.box((x - 0.3, -16.0, z - 0.3), (x + 0.3, -11.4, z + 0.3), 'paint:ral1023', 'conveyor leg')
    for x in (2.6, 5.2, 7.8, 10.4, 13.0):
        m.cyl((x, -9.7, 7.0), 0.4, 2.0, 'z', 'metal:steel', 'centre idler')
        m.box((x - 0.4, -9.9, 5.0), (x + 0.4, -9.1, 7.0), 'metal:steel', 'wing idler', rot={'axis': 'x', 'angle': 22.5, 'origin': [x, -9.7, 7.0]})
        m.box((x - 0.4, -9.9, 9.0), (x + 0.4, -9.1, 11.0), 'metal:steel', 'wing idler', rot={'axis': 'x', 'angle': -22.5, 'origin': [x, -9.7, 9.0]})
        m.cyl((x, -11.0, 5.0), 0.3, 6.0, 'z', 'metal:steel', 'return idler')
    m.box((0.8, -9.3, 5.6), (15.2, -9.0, 10.4), 'rubber', 'conveyor belt (carrying)')
    m.box((0.8, -11.6, 5.4), (15.2, -11.4, 10.6), 'rubber', 'conveyor belt (return)')
    m.cyl((1.0, -10.3, 5.0), 1.0, 6.0, 'z', 'paint:dark', 'tail pulley')
    m.cyl((15.0, -10.3, 5.0), 1.0, 6.0, 'z', 'paint:dark', 'head pulley')
    for z in (5.8, 9.9): m.box((5.0, -9.0, z), (11.0, -7.4, z + 0.3), 'paint:ral7032', 'skirt board')
    m.g('drive')
    m.box((12.6, -12.2, 11.4), (15.6, -9.4, 14.0), 'paint:#5a5e62', 'shaft-mounted gearbox')
    m.cyl((9.0, -10.8, 12.7), 1.3, 3.2, 'x', 'paint:ral5010', 'conveyor motor', cap='mesh')
    m.box((9.6, -9.6, 12.0), (11.0, -8.8, 13.4), 'paint:ral5010', 'motor terminal box')
    m.box((12.2, -11.8, 11.8), (12.6, -9.8, 13.6), 'paint:ral1023', 'coupling guard')
    lab(m, 12.8, -9.2, 15.4, -8.4, 11.4, 'DRIVE', 'north')
    m.hv_box(6.0, -9.0, 15.0, 'north', phases=3)
    run(m, [(7.6, -9.8, 14.0), (7.6, -9.8, 13.2), (9.6, -9.8, 13.2)], 0.3, 'cable:202020', 'motor mains tail')
    m.g('local control')
    m.box((16.4, 16.0, 12.0), (18.6, 23.0, 14.4), 'ral7035', 'pit control post')
    m.box((16.2, 23.0, 11.8), (18.8, 23.4, 14.6), 'ral7035', 'post cap')
    lab(m, 12.2, 21.4, 14.2, 22.6, 16.4, 'PIT', 'west')
    ps = surf(m, 'west', 16.4, 0, 0, 'PIT')                  # TileFreightTerminal: conveyor start / stop / E-stop
    ctl(m, ps, 'push', 'start', 16.4, 20.2, 12.7, w=0.6, h=0.6, col='2fcb5a')
    ctl(m, ps, 'push', 'stop', 16.4, 20.2, 13.7, w=0.6, h=0.6, col='26282c')
    ctl(m, ps, 'lamp', 'run', 16.4, 19.0, 13.2, w=0.6, h=0.6, col='30e060')
    ctl(m, ps, 'mushroom', 'estop', 16.4, 17.8, 13.2, w=0.8, h=0.8, col='c81e1e')
    m.terminal_12v(16.0, 16.0, 4.0, 'east')
    run(m, [(17.5, 17.0, 6.4), (17.5, 17.0, 12.0)], 0.2, 'galv', 'control conduit')
    m.box((15.6, 16.0, 1.4), (16.4, 16.6, 6.6), 'paint:ral1023', 'terminal kerb')
    m.save()


# ------------------------------------------------------------------------------------------------ data link (rebuild)
def data_link():
    m = Model('data_link')
    m.g('enclosure')
    m.box((0.3, 0.6, 0.6), (15.7, 15.4, 15.7), 'paint:#2b2f36', 'enclosure', east='louvre', west='louvre')
    for (x, z) in ((1.2, 1.4), (14.8, 1.4), (1.2, 14.9), (14.8, 14.9)):
        m.cyl((x, 0, z), 0.5, 0.6, 'y', 'rubber', 'rubber foot')
    m.box((0.2, 15.4, 0.5), (15.8, 15.8, 15.8), 'paint:#3a4048', 'lid')
    for x in (4.0, 8.0, 12.0): seam(m, (x, 15.8, 1.0), (x + 0.1, 15.85, 15.4), 'lid rib')
    m.g('front panel')
    m.box((0.3, 0.6, 0.0), (15.7, 15.4, 0.6), 'paint:#3a4048', 'front bezel')
    for x0 in (0.3, 14.7):
        m.box((x0, 1.0, -0.3), (x0 + 1.0, 15.0, 0.0), 'paint:#4a5058', 'rack ear')
        for y in (2.0, 14.0): m.bolt(x0 + 0.5, y, -0.3, 'north', 0.5)
    for x in (1.6, 13.6):
        m.box((x, 4.0, -1.0), (x + 0.8, 4.6, -0.3), 'metal:alu', 'handle leg')
        m.box((x, 11.4, -1.0), (x + 0.8, 12.0, -0.3), 'metal:alu', 'handle leg')
        m.box((x, 4.0, -1.0), (x + 0.8, 12.0, -0.7), 'metal:alu', 'carry handle')
    m.box((3.3, 9.3, -0.1), (12.7, 13.7, 0.0), 'paint:black', 'LCD bezel')
    m.box((3.6, 9.6, -0.15), (12.4, 13.4, -0.1), 'paint:#0a1a10', 'status LCD')
    lab(m, 3.4, 13.9, 12.6, 14.9, 0.0, 'RAILROAD DATA LINK', 'north')
    lcd = surf(m, 'north', 0, 0, -0.15, 'LCD')               # TileDataLink: mode + call counters, port link LEDs
    ctl(m, lcd, 'digits', 'lcd1', 8.0, 12.4, -0.15, w=8.2, h=1.4, col='3cdc78', fmt='%s')
    ctl(m, lcd, 'digits', 'lcd2', 8.0, 10.6, -0.15, w=8.2, h=1.4, col='3cdc78', fmt='%s')
    front = surf(m, 'north', 0, 0, 0.0, 'LINK')              # status LEDs, RESET, MODE
    for i, (c, t) in enumerate((('30e060', 'PWR'), ('ffb02e', 'TX'), ('30e060', 'RX'), ('5bcefa', 'LINK'), ('ff3030', 'ERR'))):
        x = 3.8 + i * 1.8
        ctl(m, front, 'lamp', t.lower(), x, 8.1, 0.0, w=0.45, h=0.45, col=c, blink=(t == 'ERR'))
        lab(m, x - 0.8, 6.9, x + 0.8, 7.7, 0.0, t, 'north')
    for i in range(5):
        x = 3.4 + i * 1.4
        m.box((x, 4.6, -0.15), (x + 1.1, 5.8, 0.0), 'metal:steel', 'RJ45 port shield')
        m.box((x + 0.15, 4.75, -0.2), (x + 0.95, 5.5, -0.15), 'paint:black', 'RJ45 socket')
        ctl(m, lcd, 'lamp', 'port%d' % (i + 1), x + 0.28, 5.62, -0.15, w=0.25, h=0.2, col='30e060' if i % 2 == 0 else 'ffb02e')
    m.box((10.6, 4.5, -0.25), (12.8, 5.9, 0.0), 'metal:steel', 'DB9 serial port')
    m.cyl((13.3, 5.2, -0.7), 0.4, 0.7, 'z', 'metal:brass', 'SMA antenna socket')
    ctl(m, front, 'push', 'reset', 3.6, 3.0, 0.0, w=0.45, h=0.45, col='d8322c')
    ctl(m, front, 'push', 'mode', 4.8, 3.0, 0.0, w=0.45, h=0.45, col='e8e8e8')
    lab(m, 2.0, 1.2, 6.4, 2.2, 0.0, 'RESET MODE', 'north')
    lab(m, 6.8, 2.6, 10.6, 3.6, 0.0, 'OC COMPONENT', 'north', kind='plate')
    m.g('aerial and glands')
    m.cyl((13.0, 15.8, 13.0), 1.0, 0.6, 'y', 'metal:steel', 'aerial base')
    m.cyl((13.0, 16.4, 13.0), 0.45, 0.6, 'y', 'metal:brass', 'N connector')
    m.cyl((13.0, 17.0, 13.0), 0.3, 5.0, 'y', 'paint:black', 'whip aerial')
    m.cyl((13.0, 22.0, 13.0), 0.45, 0.6, 'y', 'paint:#5a3a14', 'aerial tip')
    ctl(m, surf(m, 'up', 0, 22.6, 0, 'AERIAL'), 'lamp', 'tip', 13.0, 22.6, 13.0, w=0.7, h=0.7, col='ffb02e')   # lit while transmitting
    m.gland(6.0, 15.8, 12.0, face='up', cable='cable:2f7ce0', length=0.8)
    m.gland(9.0, 15.8, 12.0, face='up', cable='cable:1d1d21', length=0.8)
    m.terminal_12v(12.9, 1.0, 0.0, 'north')
    m.save()


# ------------------------------------------------------------------------------------------------ arrivals board (rebuild)
def arrivals_board():
    m = Model('arrivals_board')
    m.g('housing')
    m.box((0.2, 3.4, 11.3), (15.8, 15.6, 15.6), 'paint:#22262c', 'housing', up='louvre', down='louvre')
    for x in (4.0, 8.0, 12.0): seam(m, (x, 3.6, 15.6), (x + 0.1, 15.4, 15.65), 'rear access panel joint')
    for x in (1.2, 14.8):
        for y in (4.4, 14.6): m.bolt(x, y, 15.6, 'south', 0.4)
    m.g('display (drawn by the board)')
    m.box((1.0, 4.5, 10.5), (15.0, 14.5, 11.3), 'paint:#08090b', 'LED matrix module', north='led:3a2810')
    m.box((0.0, 3.4, 10.5), (1.0, 15.6, 11.5), 'paint:#2b3038', 'side bezel')
    m.box((15.0, 3.4, 10.5), (16.0, 15.6, 11.5), 'paint:#2b3038', 'side bezel')
    m.g('bezel bars')
    m.box((0.0, 2.9, 9.8), (16.0, 4.5, 11.5), 'paint:#2b3038', 'bottom bar')
    lab(m, 1.2, 3.0, 7.0, 4.3, 9.8, 'ARRIVALS', 'north', kind='plate')
    m.box((9.0, 3.1, 9.7), (10.6, 4.3, 9.8), 'mesh', 'chime speaker grille')
    m.cyl((12.0, 3.7, 9.4), 0.35, 0.4, 'z', 'paint:black', 'ambient light sensor')
    ctl(m, surf(m, 'north', 0, 0, 9.8, 'STATUS'), 'lamp', 'status', 14.3, 3.65, 9.8, w=0.8, h=0.4, col='30e060')   # TileArrivalsBoard
    m.box((0.0, 14.5, 9.8), (16.0, 15.6, 11.5), 'paint:#2b3038', 'top bar')
    m.g('hood')
    m.box((-0.2, 15.6, 9.2), (16.2, 16.4, 16.0), 'paint:#2e333a', 'sun hood')
    m.box((-0.2, 15.1, 9.2), (16.2, 15.6, 9.6), 'paint:#2e333a', 'hood drip lip')
    m.g('mounting')
    for x in (2.0, 11.8):
        m.box((x, 5.0, 15.5), (x + 2.2, 14.0, 16.0), 'galv', 'wall plate')
        for y in (5.6, 13.4): m.bolt(x + 1.1, y, 15.5, 'north', 0.4)
    for x in (2.5, 12.3):
        m.box((x, 16.4, 13.0), (x + 1.2, 18.6, 14.2), 'galv', 'suspension ear')
        m.cyl((x + 0.6, 17.6, 12.8), 0.35, 1.6, 'z', 'metal:steel', 'shackle pin')
    m.g('wiring')
    m.cyl((13.2, 2.8, 14.4), 0.45, 0.6, 'y', 'metal:brass', 'cable gland')
    m.cyl((13.2, 2.6, 14.4), 0.3, 0.3, 'y', 'cable:1d1d21', 'supply tail')
    m.terminal_12v(13.2, 0.2, 16.0, 'north')
    m.save()


# ------------------------------------------------------------------------------------------------ ticket machine (rebuild)
def ticket_machine():
    m = Model('ticket_machine')
    m.g('plinth')
    m.box((1, 0, 2), (15, 1.8, 14.4), 'paint:#2a2d33', 'plinth')
    m.box((1.4, 1.8, 2.4), (14.6, 2.4, 14.0), 'rubber', 'kick strip')
    for (x, z) in ((1.6, 2.6), (14.4, 2.6), (1.6, 13.8), (14.4, 13.8)): m.bolt(x, 1.8, z, 'up', 0.5)
    m.g('cabinet')
    m.box((1.6, 2.2, 3), (14.4, 28.2, 14), 'paint:ral5010', 'cabinet')
    m.box((1.9, 3, 13.9), (14.1, 27.5, 14.6), 'paint:ral5010', 'back panel')
    for x in (1.2, 14.0): m.box((x, 2.2, 2.6), (x + 0.8, 28.1, 3.6), 'metal:steel', 'stainless corner post')
    for x in (1.3, 14.3): m.box((x, 19, 4.2), (x + 0.4, 27, 13), 'paint:ral1023', 'side stripe')
    seam(m, (2.1, 2.6, 2.95), (13.9, 2.72, 3.0), 'service door seam')
    m.box((13.2, 6.4, 2.85), (13.6, 7.6, 3.0), 'metal:brass', 'service door lock')
    m.box((4.0, 2.8, 14.6), (12.0, 5.0, 14.7), 'louvre', 'rear vent', faces=('south',))
    m.g('header')
    m.box((1.1, 28, 2.4), (14.9, 31, 14.4), 'paint:#1c3a6a', 'header')
    lab(m, 2.0, 28.5, 14.0, 30.6, 2.4, 'TICKETS', 'north')
    m.box((2.0, 28.0, 2.2), (14.0, 28.4, 2.4), 'lamp:fff2c0', 'canopy light strip')
    m.cyl((8.0, 31.0, 8.0), 1.0, 0.7, 'y', 'paint:#1a3a22', 'in-service beacon')
    ctl(m, surf(m, 'up', 0, 31.7, 0, 'BEACON'), 'lamp', 'service', 8.0, 31.7, 8.0, w=1.3, h=1.3, col='30e060')   # TileTicketMachine: selling
    m.g('touch screen')
    m.box((2.6, 17.0, 2.5), (13.4, 26.6, 3.0), 'paint:black', 'screen bezel')
    m.box((3.3, 17.7, 2.45), (12.7, 25.9, 2.5), 'screen:5bcefa', 'touch screen')
    lab(m, 3.4, 24.6, 12.6, 25.8, 2.45, 'SELECT DESTINATION', 'north', t=0.05)
    scr = surf(m, 'north', 0, 0, 2.45, 'SCREEN')             # on-screen keys + the selection line
    for i, (txt, cid) in enumerate((('SINGLE', 'single'), ('RETURN', 'return'), ('MENU', 'menu'), ('CALL TRAIN', 'call'))):
        y = 23.1 - i * 1.3
        m.box((3.6, y, 2.4), (10.6, y + 1.0, 2.45), 'paint:#2f7ce0', 'on-screen button', north='label:' + txt)
        ctl(m, scr, 'push', cid, 11.5, y + 0.5, 2.45, w=0.8, h=0.8, col='2f7ce0')
    ctl(m, scr, 'digits', 'sel', 8.0, 18.4, 2.45, w=8.6, h=1.1, col='e8f4ff', fmt='%s')
    m.box((5.0, 26.9, 2.6), (11.0, 27.6, 3.0), 'mesh', 'speaker grille')
    m.g('payment')
    m.box((9.2, 15.0, 2.3), (12.8, 16.6, 3.0), 'paint:#2b2f36', 'card reader')
    m.box((9.6, 15.5, 2.2), (12.4, 15.8, 2.3), 'paint:black', 'card slot')
    ctl(m, surf(m, 'north', 0, 0, 2.3, 'CARD'), 'lamp', 'card', 11.0, 16.2, 2.3, w=1.1, h=0.35, col='30e060')
    m.box((6.6, 15.0, 2.2), (8.8, 16.8, 3.0), 'paint:ral1023', 'contactless pad')
    m.box((7.2, 15.6, 2.15), (8.2, 16.2, 2.2), 'paint:#c89a10', 'contactless symbol')
    m.box((3.2, 15.2, 2.3), (5.6, 16.4, 3.0), 'metal:steel', 'coin slot plate')
    m.box((3.8, 15.6, 2.25), (5.0, 15.9, 2.3), 'paint:black', 'coin slot')
    face = surf(m, 'north', 0, 0, 3.0, 'FRONT')              # coin return, help point
    ctl(m, face, 'push', 'coinret', 5.0, 14.6, 3.0, w=0.4, h=0.4, col='e8e8e8')
    m.g('keypad')
    kp = {'axis': 'x', 'angle': -22.5, 'origin': [8.0, 12.0, 3.2]}
    m.box((3.0, 11.2, 1.6), (13.0, 12.4, 3.3), 'paint:#2b2f36', 'keypad shelf')
    m.box((3.2, 12.0, 1.4), (12.8, 12.4, 3.6), 'metal:steel', 'keypad plate', rot=kp)
    # the plate leans toward you 22.5 deg: a north surface tilted back 67.5 deg about the plate's top line
    pad = m.surface('north', face=3.2 - 0.4 * 0.383, tilt=67.5, pivot=12.0 + 0.4 * 0.924, title='KEYPAD')
    keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', 'OK']
    for i, k in enumerate(keys):
        cx, rz = 4.0 + (i % 3) * 1.25, 1.75 + (3 - i // 3) * 0.48       # 1 2 3 on the far (top) row
        col = 'd8322c' if k == 'C' else '2fcb5a' if k == 'OK' else 'd8dadc'
        m.ctl(pad, 'push', 'k' + k.lower(), 16 - cx, pad['pivot'] + (rz - 3.2), w=0.8, h=0.32, col=col)
    for i, (k, col) in enumerate((('CANCEL', 'd8322c'), ('CLEAR', 'f0c418'), ('ENTER', '2fcb5a'))):
        x = 9.2 + i * 1.2
        m.ctl(pad, 'push', k.lower(), 16 - x, pad['pivot'] + (2.55 - 3.2), w=0.8, h=1.4, col=col)
    m.g('ticket issue')
    m.box((3.5, 8.4, 2.4), (12.5, 10.2, 3.2), 'paint:#2b2f36', 'printer slot')
    m.box((4.2, 8.9, 2.3), (11.8, 9.4, 2.4), 'paint:black', 'ticket exit')
    ctl(m, surf(m, 'north', 0, 0, 2.4, 'ISSUE'), 'lamp', 'collect', 8.0, 9.6, 2.4, w=7.4, h=0.2, col='ffb02e', blink=True)
    m.box((3.5, 4.2, 1.4), (12.5, 5.4, 3.2), 'metal:steel', 'ticket tray')
    m.box((3.5, 5.4, 1.2), (12.5, 6.2, 1.7), 'metal:steel', 'tray lip')
    m.nameplate(4.5, 6.8, 11.5, 7.9, 3.0, 'RAIL TICKETS')
    m.g('help point')
    m.box((12.0, 11.4, 2.9), (13.4, 13.8, 3.0), 'mesh', 'help point speaker')
    ctl(m, face, 'push', 'help', 12.7, 10.6, 3.0, w=0.6, h=0.6, col='2f7ce0')
    lab(m, 11.6, 9.6, 13.8, 10.2, 3.0, 'HELP', 'north')
    m.g('wiring')
    m.box((14.4, 1.8, 10.8), (14.6, 4.6, 13.6), 'paint:ral5010', 'terminal backing plate')
    m.terminal_12v(14.6, 2.0, 12.2, 'east')
    m.save()


# ------------------------------------------------------------------------------------------------ check
BLOCKS = ['ticket_gate', 'pa_speaker', 'maintenance_depot', 'signal_box', 'nx_desk', 'control_desk', 'channel_io', 'station_doors',
          'station_master', 'loading_silo', 'unloading_pit', 'data_link', 'display_panel', 'ticket_machine', 'arrivals_board']


def check():
    bad = 0
    for b in BLOCKS:
        bs = json.load(open(os.path.join(ASSETS, 'blockstates', b + '.json')))
        for v in bs['variants'].values():
            name = v['model'].split(':')[1]
            j = json.load(open(os.path.join(ASSETS, 'models/block', name + '.json')))
            for t in j.get('textures', {}).values():
                if t.startswith('irextras:') and not os.path.exists(os.path.join(ASSETS, 'textures', t.split(':')[1] + '.png')):
                    print('MISSING TEXTURE', name, t); bad += 1
        it = json.load(open(os.path.join(ASSETS, 'models/item', b + '.json')))
        p = it['parent'].split(':')[-1].replace('block/', '')
        if not os.path.exists(os.path.join(ASSETS, 'models/block', p + '.json')):
            print('ITEM PARENT MISSING', b, it['parent']); bad += 1
        els = len(json.load(open(os.path.join(ASSETS, 'models/block', p + '.json'))).get('elements', []))
        print('  %-18s item -> %-22s %4d elements' % (b, it['parent'], els))
    print('check: %s' % ('OK' if not bad else '%d problems' % bad))


if __name__ == '__main__':
    ticket_gate(False)
    ticket_gate(True)
    pa_speaker()
    maintenance_depot()
    signal_box()
    nx_desk()
    control_desk()
    channel_io()
    station_doors()
    station_master()
    loading_silo()
    unloading_pit()
    data_link()
    arrivals_board()
    ticket_machine()
    check()
