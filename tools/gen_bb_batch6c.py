#!/usr/bin/env python3
"""Batch 6c station life (#11-15): bus bay stop pole, bus departure board, taxi rank call point, hotel reception desk,
room lock, room wardrobe, pub bar, pub kitchen, accessibility boarding lift (5 deck heights x gate shut / open) and the
platform gap filler (in / half / out). Models face north; controls on north surfaces sit at u = 16 - x, on 'up' surfaces
v = z. Live controls: stnlife6/Tile*.java (ids must match). Every one has a 12 V terminal; machines also a mains isolator.
Writes models, controls, blockstates, item models, item textures and recipes, and prints the shelf spots + lang lines."""
import json, os
from PIL import Image
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing
from gen_bb_economy import back_wall, desk, strip, cupboard_shelf, monitor, X

SPOTS = {}


def display(m, x0, x1, y0, y1, z, ids, cols, title='DISPLAY', bezel='#1c1e20'):
    """a flush LED / LCD display: bezel, dark glass, one DIGITS line per id (north surface)"""
    m.box((x0 - 0.3, y0 - 0.3, z + 0.02), (x1 + 0.3, y1 + 0.3, z + 0.5), 'paint:' + bezel, title.lower() + ' bezel')
    m.box((x0, y0, z - 0.06), (x1, y1, z + 0.02), 'paint:#03140a', title.lower() + ' glass')
    sf = m.surface('north', face=z - 0.1, title=title)
    h = (y1 - y0) / len(ids)
    for i, cid in enumerate(ids):
        m.ctl(sf, 'digits', cid, X((x0 + x1) / 2), y1 - h * (i + 0.5), w=x1 - x0 - 0.4, h=h * 0.86, col=cols[i % len(cols)], label='', fmt='%s')
    return sf


def screws(m, pts, z, face='north', s=0.3):
    for (x, y) in pts:
        m.bolt(x, y, z, face, s)


# ================================================================================================= bus bay (#11)
def bus_bay():
    m = Model('bus_bay')
    m.g('foundation')
    m.box((4, 0, 4), (12, 1.0, 12), 'concrete', 'concrete foot')
    m.box((5, 1.0, 5), (11, 1.6, 11), 'galv', 'base plate')
    for x in (5.6, 10.4):
        for z in (5.6, 10.4):
            m.box((x - 0.35, 1.6, z - 0.35), (x + 0.35, 2.2, z + 0.35), 'metal:steel', 'holding-down nut')
    m.g('pole')
    m.cyl((8, 1.6, 8), 0.9, 29.4, 'y', 'galv', 'stop pole')
    for y in (9.6, 16.8, 22.0, 27.4):
        m.cyl((8, y, 8), 1.15, 0.8, 'y', 'metal:steel', 'pole clamp band')
    m.g('flag')
    m.box((1.6, 23.0, 7.5), (14.4, 30.2, 8.5), 'paint:#f4f2ea', 'stop flag', north='label:BUS|STOP', south='label:BUS|STOP')
    m.box((1.4, 22.8, 7.4), (14.6, 23.0, 8.6), 'paint:#c8202a', 'flag lower trim')
    m.box((1.4, 30.2, 7.4), (14.6, 30.5, 8.6), 'paint:#c8202a', 'flag upper trim')
    m.box((1.6, 21.0, 7.5), (14.4, 22.8, 8.5), 'paint:#1f2a4a', 'route tile strip', north='label:ROUTES 1-4', south='label:ROUTES 1-4')
    for x in (2.2, 13.8):
        m.box((x - 0.3, 21.2, 7.3), (x + 0.3, 30.0, 7.5), 'metal:steel', 'flag fixing bar')
    m.g('countdown display')
    m.box((2.6, 16.2, 6.0), (13.4, 20.8, 8.6), 'paint:#24282c', 'countdown housing')
    m.box((2.4, 20.8, 5.6), (13.6, 21.2, 8.8), 'paint:#24282c', 'display hood')
    display(m, 3.2, 12.8, 16.6, 20.4, 6.0, ['name', 'place', 'd1', 'd2'], ['ffb020', '8a94a0', 'ffd040', '7cf29a'], 'COUNTDOWN', '#24282c')
    m.g('timetable case')
    m.box((3.0, 10.0, 6.8), (13.0, 15.8, 7.8), 'paint:#3a3e44', 'timetable case')
    m.box((3.4, 10.4, 6.7), (12.6, 15.4, 6.8), 'glass', 'case glazing')
    m.box((3.8, 14.2, 6.85), (12.2, 15.0, 6.9), 'plate', 'timetable header', north='label:TIMETABLE')
    for r in range(5):
        y = 13.4 - r * 0.6
        m.box((3.9, y, 6.85), (8.0, y + 0.3, 6.9), 'paint:#2a2a2a', 'timetable line')
        m.box((8.4, y, 6.85), (12.1, y + 0.3, 6.9), 'paint:#2a2a2a', 'timetable times')
    m.box((3.0, 9.8, 6.7), (3.3, 16.0, 7.0), 'metal:steel', 'case hinge')
    m.g('request box')
    m.box((4.2, 4.8, 5.8), (11.8, 9.4, 7.6), 'paint:#e0b818', 'request + setting box')
    m.box((4.0, 9.4, 5.6), (12.0, 9.8, 7.8), 'paint:#24282c', 'box lid')
    screws(m, [(4.6, 5.2), (11.4, 5.2), (4.6, 9.0), (11.4, 9.0)], 5.8, 'north', 0.25)
    sf = m.surface('north', face=5.7, title='BAY')
    lamps(m, sf, [('l_due', AMBER, 'DUE', True), ('l_at', GREEN, 'AT'), ('l_req', WHITE, 'REQ'), ('l_road', RED, 'ROAD'), ('l_out', AMBER, 'OUT'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAIN')],
          X(11.3), 8.7, step=1.02, size=0.5, lab_dv=0.5)
    m.ctl(sf, 'push', 'req', X(10.9), 6.6, w=1.3, h=1.3, col='30a040', label='REQUEST')
    m.ctl(sf, 'selector', 's_route', X(8.9), 6.6, w=0.9, h=0.9, col='202020', label='ROUTE', pos=['1', '2', '3', '4'])
    m.ctl(sf, 'selector', 's_seq', X(7.6), 6.6, w=0.9, h=0.9, col='202020', label='STOP', pos=['1', '2', '3', '4', '5', '6', '7', '8'])
    m.ctl(sf, 'selector', 's_bay', X(6.3), 6.6, w=0.9, h=0.9, col='202020', label='BAY', pos=['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'])
    m.ctl(sf, 'key', 'k_out', X(5.0), 6.6, w=0.8, h=0.8, col='c0a040', label='OUT', pos=['IN USE', 'OUT'])
    m.g('perch + bin')
    m.box((9.0, 11.0, 9.0), (15.6, 11.6, 10.0), 'paint:#5a5e64', 'leaning rail')
    m.box((14.8, 1.6, 9.0), (15.6, 11.0, 10.0), 'paint:#5a5e64', 'leaning rail leg')
    m.cyl((3.2, 4.0, 11.0), 1.6, 5.0, 'y', 'paint:#2a4a2a', 'litter bin')
    m.box((1.8, 9.0, 9.6), (4.6, 9.4, 12.4), 'paint:#1a2a1a', 'bin lid')
    m.box((3.0, 6.0, 9.2), (5.6, 6.6, 9.6), 'metal:steel', 'bin bracket')
    m.terminal_12v(8.0, 2.2, 9.0, 'south')
    mains_box(m, 12.0, 1.6, 9.0, 'east')
    m.g('fixings')
    for x in (2.2, 13.8):
        for y in (22.0, 26.0, 29.6):
            m.bolt(x, y, 7.5, 'north', 0.25)
    m.box((6.8, 2.4, 7.05), (9.2, 4.2, 7.1), 'plate', 'stop id plate', north='label:STOP')
    for y in (12.0, 3.0):
        m.cyl((8, y, 8), 0.95, 0.5, 'y', 'paint:#f0f0f0', 'reflective band')
    for x in (3.0, 13.0):
        m.box((x - 0.3, 16.0, 7.6), (x + 0.3, 16.4, 8.8), 'metal:steel', 'display bracket arm')
    screws(m, [(3.4, 10.4), (12.6, 10.4), (3.4, 15.4), (12.6, 15.4)], 6.8, 'north', 0.2)
    m.save(item_gui=0.4)
    return m


# ================================================================================================= bus departures (#11)
def bus_departures():
    m = Model('bus_departures')
    m.g('legs')
    for x in (1.0, 13.6):
        m.box((x, 0, 7.0), (x + 1.4, 1.0, 9.0), 'concrete', 'leg footing')
        m.box((x + 0.2, 1.0, 7.3), (x + 1.2, 31.0, 8.7), 'galv', 'board leg')
        for y in (3, 14, 28):
            m.bolt(x + 0.7, y, 7.3, 'north', 0.3)
    m.g('board')
    m.box((0, 15.2, 6.2), (16, 31.0, 9.8), 'paint:#1f2a4a', 'board housing')
    m.box((-0.4, 31.0, 5.0), (16.4, 31.6, 10.2), 'paint:#1f2a4a', 'rain hood')
    m.box((0, 15.0, 6.0), (16, 15.2, 10.0), 'paint:#c8202a', 'livery band')
    display(m, 0.7, 15.3, 15.8, 30.4, 6.2, ['title', 'clock', 'r1', 'r2', 'r3', 'r4', 'r5', 'r6', 'status'],
            ['ffd040', '8a94a0', 'ffb020', 'ffb020', 'ffb020', 'ffb020', 'ffb020', '7cf29a', '5bcefa'], 'BOARD', '#14161a')
    m.louvres(1.0, 15.0, 15.4, 15.9, 9.8, n=6)
    m.g('control cabinet')
    m.box((1.8, 1.0, 6.6), (14.2, 13.4, 9.6), 'paint:ral7035', 'control cabinet')
    m.box((1.6, 13.4, 6.4), (14.4, 13.8, 9.8), 'paint:ral7016', 'cabinet roof')
    m.box((2.0, 1.2, 6.5), (14.0, 13.2, 6.6), 'paint:ral7035', 'cabinet door')
    for y in (2.4, 11.6):
        m.box((14.0, y, 6.3), (14.4, y + 1.0, 6.7), 'metal:steel', 'door hinge')
    m.box((2.2, 6.6, 6.2), (2.8, 8.0, 6.5), 'metal:brass', 'door lock')
    m.box((2.4, 1.4, 6.4), (6.0, 2.2, 6.5), 'plate', 'cabinet plate', north='label:BUS CONTROL')
    m.cyl((8, 13.8, 8.0), 0.5, 1.4, 'y', 'galv', 'conduit to board')
    sf = m.surface('north', face=6.3, title='CABINET')
    m.ctl(sf, 'gauge', 'g_buses', X(3.8), 12.0, w=1.8, h=1.8, col='f2f0e6', label='BUSES OUT', max=8)
    m.ctl(sf, 'gauge', 'g_punct', X(12.2), 12.0, w=1.8, h=1.8, col='f2f0e6', label='ON TIME %', max=100)
    lamps(m, sf, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], X(9.0), 12.2, step=1.6, size=0.6)
    lamps(m, sf, [('l_run1', GREEN, 'RUN1'), ('l_run2', GREEN, 'RUN2'), ('l_run3', GREEN, 'RUN3'), ('l_run4', GREEN, 'RUN4'),
                  ('l_flt1', RED, 'FLT1', True), ('l_flt2', RED, 'FLT2', True), ('l_flt3', RED, 'FLT3', True), ('l_flt4', RED, 'FLT4', True)], X(13.2), 9.9, step=1.4, size=0.55)
    for r in range(4):
        u = X(12.6 - r * 2.8)
        m.ctl(sf, 'key', 'k_svc%d' % (r + 1), u, 8.0, w=1.0, h=1.0, col='c0a040', label='SVC %d' % (r + 1), pos=['OFF', 'ON'])
        m.ctl(sf, 'selector', 's_hw%d' % (r + 1), u, 5.7, w=1.0, h=1.0, col='202020', label='HDWY %d' % (r + 1), pos=['2', '4', '6', '10', '15'])
    m.ctl(sf, 'selector', 's_sel', X(12.6), 3.2, w=1.0, h=1.0, col='2050d0', label='ROUTE', pos=['R1', 'R2', 'R3', 'R4'])
    pushes(m, sf, [('pg', 'e0e0e0', 'PAGE'), ('send', '30a040', 'SEND'), ('recall', 'd03030', 'RECALL'), ('ann', '2050d0', 'ANNOUNCE')], X(10.2), 3.2, step=2.2, size=1.0)
    m.terminal_12v(14.2, 2.0, 8.1, 'east')
    mains_box(m, 14.2, 6.0, 8.1, 'east')
    m.g('structure details')
    m.box((0.2, 31.6, 6.6), (15.8, 32.0, 9.4), 'plate', 'header plate', north='label:BUS STATION')
    for x in (1.0, 13.6):
        m.box((x - 0.4, 1.0, 6.6), (x + 1.8, 1.3, 9.4), 'metal:steel', 'leg base plate')
        for (bx, bz) in ((x - 0.1, 6.9), (x + 1.5, 6.9), (x - 0.1, 9.1), (x + 1.5, 9.1)):
            m.box((bx - 0.2, 1.3, bz - 0.2), (bx + 0.2, 1.7, bz + 0.2), 'metal:steel', 'anchor nut')
    m.box((2.6, 14.0, 7.8), (13.4, 14.6, 8.2), 'galv', 'leg cross brace')
    for k in range(6):
        m.box((1.0 + k * 2.6, 15.6, 9.8), (1.6 + k * 2.6, 30.6, 10.2), 'paint:#1a2440', 'board back stiffener')
    m.box((4.0, 17.0, 10.2), (12.0, 29.0, 10.3), 'paint:#1a2440', 'rear service door')
    for y in (18.0, 28.0):
        m.box((11.8, y, 10.2), (12.2, y + 0.8, 10.5), 'metal:steel', 'service door hinge')
    m.box((4.2, 22.6, 10.3), (4.8, 23.6, 10.5), 'metal:brass', 'service door lock')
    for x in (4.0, 7.0, 10.0):
        m.gland(x, 1.0, 8.0, 'down', r=0.35)
    for x in (2.2, 13.2):
        m.box((x, 0.6, 7.0), (x + 0.6, 1.0, 9.2), 'paint:#2a2c30', 'cabinet foot')
    m.box((2.2, 13.4, 9.6), (13.8, 13.6, 9.8), 'metal:copper', 'cabinet earth bar')
    m.louvres(3.0, 13.0, 1.6, 3.0, 9.65, n=5)
    m.save(item_gui=0.4)
    return m


# ================================================================================================= taxi rank (#12)
def taxi_rank():
    m = Model('taxi_rank')
    m.g('plinth')
    m.box((2.6, 0, 4.6), (13.4, 1.0, 11.4), 'concrete', 'plinth')
    m.box((2.4, 0.95, 4.4), (13.6, 1.25, 11.6), 'haz', 'kerb stripe')
    m.g('pillar')
    m.box((3.2, 1.0, 5.4), (12.8, 26.0, 10.6), 'paint:#1c1c1e', 'call point pillar')
    for y in (4.0, 9.0):
        m.box((3.0, y, 5.2), (13.0, y + 0.6, 10.8), 'paint:#e8c21a', 'chequer band')
        for k in range(8):
            if k % 2 == 0: m.box((3.2 + k * 1.2, y, 5.15), (4.4 + k * 1.2, y + 0.6, 5.2), 'paint:#1c1c1e', 'chequer')
    m.g('roof sign')
    m.box((1.6, 26.0, 4.4), (14.4, 30.6, 11.6), 'paint:#f4d020', 'TAXI roof sign', north='label:TAXI', south='label:TAXI')
    m.box((1.4, 30.6, 4.2), (14.6, 31.0, 11.8), 'paint:#1c1c1e', 'sign cap')
    m.box((1.4, 25.6, 4.2), (14.6, 26.0, 11.8), 'paint:#1c1c1e', 'sign base')
    m.g('call point')
    m.box((3.6, 9.8, 5.0), (12.4, 25.2, 5.4), 'paint:#2a2c30', 'call point fascia')
    display(m, 4.0, 12.0, 16.4, 24.6, 5.0, ['sign', 'dest', 'meter', 'status', 'takings', 'last'], ['ffd040', '5bcefa', '7cf29a', 'ffb020', '8a94a0', '8a94a0'], 'METER', '#14161a')
    sf = m.surface('north', face=4.9, title='CALL POINT')
    pushes(m, sf, [('dprev', 'e0e0e0', 'DEST <'), ('dnext', 'e0e0e0', 'DEST >')], X(11.4), 14.6, step=1.6, size=0.9)
    m.ctl(sf, 'push', 'call', X(8.0), 14.6, w=1.5, h=1.5, col='30a040', label='CALL TAXI')
    m.ctl(sf, 'push', 'cancel', X(5.2), 14.6, w=0.9, h=0.9, col='d03030', label='CANCEL')
    m.ctl(sf, 'selector', 's_tariff', X(11.2), 12.2, w=1.0, h=1.0, col='202020', label='TARIFF', pos=['1 DAY', '2 NIGHT', '3 HOLIDAY'])
    m.ctl(sf, 'key', 'k_rank', X(9.4), 12.2, w=0.9, h=0.9, col='c0a040', label='RANK', pos=['CLOSED', 'OPEN'])
    m.ctl(sf, 'gauge', 'g_eta', X(6.0), 12.2, w=1.8, h=1.8, col='f2f0e6', label='TAXI DIST', max=100)
    lamps(m, sf, [('l_avail', GREEN, 'FOR HIRE'), ('l_coming', AMBER, 'COMING', True), ('l_hire', RED, 'ON HIRE'), ('l_closed', RED, 'CLOSED'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')],
          X(11.6), 10.6, step=1.4, size=0.55)
    m.g('call point fittings')
    m.louvres(4.2, 7.0, 6.4, 8.6, 5.3, n=4)
    m.box((8.4, 6.6, 5.0), (11.6, 8.4, 5.4), 'paint:#404448', 'receipt printer')
    m.box((8.8, 7.2, 4.95), (11.2, 7.5, 5.0), 'paint:#101010', 'receipt slot')
    m.box((3.8, 2.0, 5.2), (12.2, 3.4, 5.4), 'plate', 'rank plate', north='label:TAXI RANK')
    screws(m, [(4.0, 10.2), (12.0, 10.2), (4.0, 24.8), (12.0, 24.8)], 5.0, 'north', 0.25)
    m.terminal_12v(8.0, 2.0, 10.6, 'south')
    mains_box(m, 12.8, 2.0, 8.0, 'east')
    m.g('rank details')
    for z in (5.4, 10.4):
        for k in range(4):
            m.box((3.2, 2.0 + k * 6.0, z), (12.8, 2.1 + k * 6.0, z + 0.2), 'paint:#2a2a2c', 'panel seam')
    m.box((4.4, 2.0, 10.6), (11.6, 22.0, 10.7), 'paint:#262628', 'rear service door')
    for y in (4.0, 20.0):
        m.box((11.4, y, 10.6), (11.8, y + 0.8, 10.9), 'metal:steel', 'door hinge')
    m.box((4.6, 12.0, 10.7), (5.2, 13.0, 10.9), 'metal:brass', 'door lock')
    for x in (2.2, 13.4):
        m.box((x, 25.0, 7.4), (x + 0.4, 26.0, 8.6), 'metal:steel', 'sign bracket')
    for x in (0.6, 15.4):
        m.cyl((x, 0, 2.0), 0.6, 7.0, 'y', 'paint:#1c1c1e', 'rank bollard')
        m.cyl((x, 5.4, 2.0), 0.65, 0.8, 'y', 'paint:#f0f0f0', 'bollard reflective band')
        m.cyl((x, 7.0, 2.0), 0.7, 0.3, 'y', 'metal:steel', 'bollard cap')
    for x in (5.0, 8.0, 11.0):
        m.gland(x, 1.0, 9.0, 'down', r=0.3)
    screws(m, [(3.6, 1.6), (12.4, 1.6), (3.6, 25.4), (12.4, 25.4)], 5.4, 'north', 0.25)
    m.save(item_gui=0.4)
    return m


# ================================================================================================= hotel reception (#13)
def hotel_reception():
    m = Model('hotel_reception')
    back_wall(m, panel='#4a2a3a', plaster='#f2ead8', top=30)
    m.g('fascia')
    m.box((-14, 26.0, 14.6), (30, 30.0, 15.4), 'paint:#3a1a2a', 'fascia board')
    m.box((-6, 26.6, 14.5), (22, 29.4, 14.6), 'plate', 'fascia lettering', north='label:HOTEL RECEPTION')
    SPOTS['hotel_reception'] = cupboard_shelf(m, -13.4, -2.6, ['linen'], 6, title='BROCHURES', body='#4a2a3a', door='#6a3a4a')
    m.g('key board')
    m.box((18.4, 8.0, 13.8), (29.4, 24.0, 15.4), 'wood', 'key board')
    m.box((18.2, 23.8, 13.6), (29.6, 24.6, 15.4), 'paint:#3a1a2a', 'key board cornice')
    for r in range(4):
        for c in range(5):
            x0, y0 = 19.0 + c * 2.1, 21.4 - r * 3.4
            m.box((x0, y0, 13.7), (x0 + 1.8, y0 + 1.8, 13.8), 'paint:#2a1a10', 'key pigeonhole')
            m.box((x0 + 0.8, y0 + 1.4, 13.5), (x0 + 1.0, y0 + 1.6, 13.7), 'metal:brass', 'key hook')
            if (r + c) % 3: m.box((x0 + 0.5, y0 + 0.2, 13.55), (x0 + 1.3, y0 + 1.3, 13.6), 'paint:#f4f2ea', 'key card')
            m.box((x0 + 0.2, y0 - 0.5, 13.7), (x0 + 1.6, y0 - 0.1, 13.8), 'plate', 'room number plate')
    m.box((18.4, 0, 11.6), (29.4, 8.0, 15.4), 'paint:#4a2a3a', 'key cabinet')
    m.box((18.8, 0.6, 11.45), (29.0, 7.4, 11.6), 'paint:#6a3a4a', 'key cabinet door')
    m.box((28.0, 3.6, 11.2), (28.4, 4.6, 11.45), 'metal:brass', 'cabinet knob')
    kc = m.surface('north', face=11.4, title='KEYS')
    m.ctl(kc, 'zone', 'keys', X(23.9), 4.0, w=10.0, h=6.6, col='000000', label='')
    desk(m, {('l', 1): 'cash'}, top=9.0, body='#4a2a3a', front='#6a3a4a', pull='metal:brass')
    sf = strip(m, 4.0, 8.4, top=9.0)
    monitor(m, 1.6, 14.4, 12.0, 19.6, 10.6, ['sign', 'room', 'price', 'occ', 'take', 'clock'], cols=['ffd040', '5bcefa', '7cf29a', '7cf29a', 'ffb020', '8a94a0'], stand_y=9.0)
    m.g('counter fittings')
    m.box((13.2, 9.0, 9.0), (15.4, 9.8, 10.4), 'paint:#c8a020', 'service bell base')
    m.box((13.7, 9.8, 9.4), (14.9, 10.4, 10.0), 'metal:brass', 'service bell')
    m.box((0.6, 9.0, 8.8), (3.2, 9.6, 10.6), 'paint:#f4f2ea', 'registration cards')
    m.box((0.9, 9.6, 9.0), (1.2, 9.7, 10.4), 'paint:#202020', 'pen')
    m.box((3.6, 9.0, 9.4), (5.6, 10.2, 10.6), 'paint:#2a2c2e', 'key card encoder')
    m.box((4.0, 10.2, 9.8), (5.2, 10.25, 10.4), 'paint:#101010', 'encoder slot')
    m.box((-0.2, 22.0, 2.6), (16.2, 22.6, 3.4), 'paint:#3a1a2a', 'counter canopy')
    for x in (0.0, 15.6):
        m.box((x, 9.0, 2.8), (x + 0.4, 22.0, 3.2), 'metal:brass', 'canopy post')
    for x in (2.0, 8.0, 14.0):
        m.box((x - 0.6, 21.4, 2.7), (x + 0.6, 22.0, 3.3), 'paint:#fff2c0', 'canopy downlight')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    pushes(m, sf, [('rprev', 'e0e0e0', 'ROOM <'), ('rnext', 'e0e0e0', 'ROOM >'), ('nminus', 'e0e0e0', 'NIGHTS -'), ('nplus', 'e0e0e0', 'NIGHTS +'),
                   ('checkin', '30a040', 'CHECK IN'), ('checkout', 'f0d020', 'CHECK OUT'), ('dup', 'c08030', 'DUPLICATE'), ('print', '2050d0', 'REGISTER'), ('bell', 'c0a040', 'BELL')],
           X(14.8), 7.6, step=1.6, size=0.85)
    m.ctl(sf, 'selector', 's_rate', X(14.6), 5.1, w=0.9, h=0.9, col='202020', label='RATE', pos=['STANDARD', 'SUPERIOR', 'SUITE'])
    m.ctl(sf, 'key', 'k_open', X(13.0), 5.1, w=0.9, h=0.9, col='c0a040', label='DESK', pos=['CLOSED', 'OPEN'])
    m.ctl(sf, 'gauge', 'g_occ', X(11.2), 5.0, w=1.5, h=1.5, col='f2f0e6', label='LET %', max=100)
    lamps(m, sf, [('l_vac', GREEN, 'VACANT'), ('l_occ', RED, 'LET'), ('l_dirty', AMBER, 'DIRTY'), ('l_dnd', BLUE, 'DND'), ('l_full', RED, 'FULL', True), ('l_open', GREEN, 'OPEN'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')],
          X(9.6), 5.2, step=1.15, size=0.5, lab_dv=0.6)
    m.save(item_gui=0.28)
    return m


# ================================================================================================= room lock (#13)
def room_lock():
    m = Model('room_lock')
    m.g('back plate')
    m.box((4.0, 1.0, 15.0), (12.0, 15.0, 16.0), 'metal:steel', 'escutcheon back plate')
    m.box((3.8, 0.8, 15.6), (12.2, 15.2, 16.0), 'paint:#2a2c30', 'plate gasket')
    screws(m, [(4.6, 1.6), (11.4, 1.6), (4.6, 14.4), (11.4, 14.4), (8.0, 14.4), (8.0, 1.6)], 15.0, 'north', 0.25)
    m.g('reader')
    m.box((4.8, 2.2, 13.8), (11.2, 13.8, 15.0), 'paint:#1c1e22', 'reader body')
    m.box((4.6, 13.8, 13.6), (11.4, 14.2, 15.0), 'metal:steel', 'reader cap')
    m.box((4.6, 2.0, 13.6), (11.4, 2.2, 15.0), 'metal:steel', 'reader foot')
    display(m, 5.4, 10.6, 11.0, 13.4, 13.8, ['num', 'msg'], ['ffd040', '7cf29a'], 'LOCK', '#1c1e22')
    m.box((5.4, 2.8, 13.7), (10.6, 6.6, 13.8), 'paint:#2a3a5a', 'card pad')
    m.box((6.0, 5.6, 13.65), (10.0, 6.2, 13.7), 'plate', 'pad legend', north='label:TAP CARD')
    for k in range(4):
        m.box((6.4 + k * 0.9, 3.4, 13.65), (6.6 + k * 0.9, 5.0, 13.7), 'paint:#4a6a9a', 'pad antenna mark')
    m.box((5.4, 7.4, 13.7), (10.6, 9.6, 13.8), 'paint:#101214', 'thumbwheel window')
    sf = m.surface('north', face=13.6, title='LOCK')
    m.ctl(sf, 'push', 'reader', X(8.0), 4.4, w=4.0, h=2.6, col='2a3a5a', label='')
    m.ctl(sf, 'selector', 's_d1', X(6.8), 8.5, w=0.9, h=0.9, col='e0e0e0', label='ROOM', pos=[str(i) for i in range(10)])
    m.ctl(sf, 'selector', 's_d2', X(8.0), 8.5, w=0.9, h=0.9, col='e0e0e0', label='', pos=[str(i) for i in range(10)])
    m.ctl(sf, 'push', 'dnd', X(9.5), 9.0, w=0.7, h=0.7, col='4080ff', label='DND')
    m.ctl(sf, 'push', 'clean', X(9.5), 7.9, w=0.7, h=0.7, col='30a040', label='CLEANED')
    lamps(m, sf, [('l_green', GREEN, 'OK'), ('l_red', RED, 'NO'), ('l_dnd', BLUE, 'DND'), ('l_occ', AMBER, 'LET'), ('l_dirty', AMBER, 'DIRTY'), ('pwr', GREEN, '12V')],
          X(10.3), 10.4, step=0.92, size=0.4, lab_dv=0.4)
    m.g('room plaque + conduit')
    m.box((5.6, 15.4, 15.2), (10.4, 17.6, 16.0), 'metal:brass', 'room number plaque', north='label:ROOM')
    m.box((5.4, 15.2, 15.6), (10.6, 15.4, 16.0), 'metal:brass', 'plaque fixing')
    m.box((7.4, 0.0, 15.2), (8.6, 1.0, 16.0), 'paint:#d8d8d0', 'cable conduit')
    m.box((7.2, 0.6, 15.0), (8.8, 1.0, 16.0), 'paint:#d8d8d0', 'conduit box')
    m.terminal_12v(14.0, 0.4, 14.6, 'north')
    m.g('reader details')
    for r in range(3):
        for c in range(4):
            m.box((8.9 + c * 0.45, 2.6 + r * 0.45 + 4.6, 13.75), (9.1 + c * 0.45, 2.8 + r * 0.45 + 4.6, 13.8), 'paint:#050505', 'speaker hole')
    for d in (6.8, 8.0):
        for k in range(4):
            m.box((16 - d - 0.5, 7.7 + k * 0.45, 13.72), (16 - d + 0.5, 7.8 + k * 0.45, 13.75), 'paint:#3a3a3a', 'thumbwheel ridge')
    for x in (4.8, 11.0):
        m.box((x, 2.2, 13.75), (x + 0.2, 13.8, 13.8), 'metal:steel', 'body bevel')
    m.box((4.8, 2.2, 13.75), (11.2, 2.4, 13.8), 'metal:steel', 'body bevel')
    m.box((4.8, 13.6, 13.75), (11.2, 13.8, 13.8), 'metal:steel', 'body bevel')
    m.box((7.4, -0.0, 15.0), (8.6, 0.6, 15.2), 'paint:#d8d8d0', 'conduit clip')
    for x in (5.6, 10.2):
        m.box((x, 15.4, 15.1), (x + 0.2, 17.6, 15.2), 'metal:brass', 'plaque edge')
    m.box((4.2, 6.8, 13.9), (4.8, 7.2, 14.6), 'metal:steel', 'tamper switch')
    m.box((11.2, 6.8, 13.9), (11.8, 7.2, 14.6), 'metal:steel', 'tamper switch')
    for y in (3.0, 6.0, 9.0, 12.0):
        m.box((3.9, y, 15.1), (4.1, y + 0.6, 15.6), 'paint:#1c1e22', 'gasket clip')
        m.box((11.9, y, 15.1), (12.1, y + 0.6, 15.6), 'paint:#1c1e22', 'gasket clip')
    m.save(item_gui=0.6)
    return m


# ================================================================================================= room wardrobe (#13)
def room_wardrobe():
    m = Model('room_wardrobe')
    m.g('carcass')
    m.box((1.0, 0.6, 4.2), (15.0, 15.4, 16.0), 'wood', 'wardrobe carcass')
    m.box((0.8, 15.4, 4.0), (15.2, 16.0, 16.0), 'paint:#5a3a24', 'top board')
    m.box((1.2, 0, 4.6), (14.8, 0.6, 15.6), 'paint:#2a1a10', 'plinth')
    m.box((0.6, 15.0, 3.8), (15.4, 15.4, 4.2), 'paint:#5a3a24', 'cornice')
    for x in (1.0, 14.6):
        m.box((x, 0.6, 4.0), (x + 0.4, 15.4, 4.2), 'paint:#5a3a24', 'stile')
    m.g('wardrobe doors')
    for (a, b) in ((1.4, 7.9), (8.1, 14.6)):
        m.box((a, 6.4, 4.0), (b, 15.0, 4.2), 'paint:#7a5238', 'wardrobe door')
        m.box((a + 0.6, 7.0, 3.95), (b - 0.6, 14.4, 4.0), 'paint:#6a4630', 'door panel')
        m.box((a + 1.0, 7.4, 3.9), (b - 1.0, 14.0, 3.95), 'paint:#7a5238', 'raised field')
    for x in (7.4, 8.6):
        m.box((x - 0.15, 9.6, 3.6), (x + 0.15, 11.8, 3.9), 'metal:brass', 'door pull')
    for y in (7.4, 13.6):
        m.box((1.2, y, 3.9), (1.5, y + 0.8, 4.1), 'metal:brass', 'hinge')
        m.box((14.5, y, 3.9), (14.8, y + 0.8, 4.1), 'metal:brass', 'hinge')
    sf = m.surface('north', face=3.85, title='WARDROBE')
    m.ctl(sf, 'zone', 'hang', X(8.0), 10.7, w=13.0, h=8.2, col='000000', label='')
    m.g('room safe')
    m.box((1.4, 0.8, 4.0), (14.6, 6.2, 4.2), 'paint:#6a4630', 'safe drawer front')
    m.box((3.0, 1.2, 3.6), (13.0, 5.8, 4.0), 'paint:#3a3e44', 'safe door')
    m.box((3.4, 1.6, 3.55), (12.6, 5.4, 3.6), 'paint:#4a4e54', 'safe door skin')
    m.box((10.4, 2.4, 3.3), (11.2, 4.6, 3.55), 'metal:steel', 'safe handle')
    display(m, 3.8, 7.6, 4.2, 5.2, 3.5, ['sd'], ['ffb020'], 'SAFE', '#1c1e22')
    for r in range(3):
        for c in range(3):
            m.box((3.9 + c * 0.9, 1.7 + r * 0.8, 3.45), (4.6 + c * 0.9, 2.3 + r * 0.8, 3.55), 'paint:#c0c4c8', 'keypad key')
    sk = m.surface('north', face=3.4, title='SAFE')
    m.ctl(sk, 'zone', 'safe', X(9.0), 3.4, w=3.2, h=3.6, col='000000', label='')
    lamps(m, sk, [('l_safe', RED, 'LOCKED'), ('pwr', GREEN, '12V')], X(10.0), 5.6, step=1.2, size=0.4, lab_dv=0.4)
    m.g('hat shelf')
    sh = m.surface('up', face=0.0, title='HAT SHELF')
    spots = []
    for k in range(3):
        x = 3.0 + k * 5.0
        m.ctl(sh, 'zone', 'sh%d' % (k + 1), X(x), 10.0, w=4.6, h=8.0, col='000000', label='')
        spots.append((x, 16.0, 10.0))
    SPOTS['room_wardrobe'] = spots
    m.terminal_12v(15.0, 1.0, 12.0, 'east')
    m.g('joinery')
    for (a, b) in ((1.4, 7.9), (8.1, 14.6)):
        for y in (7.0, 14.2):
            m.box((a + 0.6, y, 3.92), (b - 0.6, y + 0.2, 3.97), 'paint:#5a3a24', 'door moulding')
        for x in (a + 0.6, b - 0.8):
            m.box((x, 7.0, 3.92), (x + 0.2, 14.4, 3.97), 'paint:#5a3a24', 'door moulding')
    for x in (1.0, 14.8):
        for (y0, y1) in ((1.0, 7.0), (8.0, 14.6)):
            m.box((x - 0.05, y0, 6.0), (x + 0.25, y1, 14.0), 'paint:#6a4630', 'side panel field')
    for (x, z) in ((1.8, 5.0), (14.2, 5.0), (1.8, 15.0), (14.2, 15.0)):
        m.cyl((x, 0, z), 0.5, 0.6, 'y', 'paint:#2a1a10', 'bun foot')
    for y in (2.0, 5.0):
        m.box((2.6, y, 3.5), (3.0, y + 0.6, 3.6), 'metal:steel', 'safe hinge')
    for (x, y) in ((3.6, 1.5), (12.4, 1.5), (3.6, 5.5), (12.4, 5.5)):
        m.bolt(x, y, 3.55, 'north', 0.2)
    m.box((1.4, 15.98, 15.2), (14.6, 17.4, 15.6), 'paint:#5a3a24', 'hat shelf back rail')
    for x in (1.6, 14.0):
        m.box((x, 16.0, 15.0), (x + 0.4, 17.6, 15.8), 'paint:#5a3a24', 'rail post')
    for z in (8.0, 12.0):
        m.box((14.9, 11.0, z), (15.3, 11.4, z + 0.4), 'metal:brass', 'coat hook')
        m.box((15.3, 11.4, z), (15.5, 12.2, z + 0.4), 'metal:brass', 'coat hook tip')
    m.save(item_gui=0.6)
    return m


# ================================================================================================= pub bar (#14)
def pub_bar():
    m = Model('pub_bar')
    back_wall(m, panel='#3a2414', plaster='#5a3a24', top=30)
    m.g('back bar')
    m.box((-13.6, 12.0, 14.8), (29.6, 26.0, 15.4), 'glass', 'back bar mirror')
    for y in (16.0, 20.6):
        m.box((-13.6, y, 12.6), (29.6, y + 0.4, 15.4), 'wood', 'back bar shelf')
        for k in range(18):
            x = -12.6 + k * 2.3
            col = ['#2a6a2a', '#7a3a10', '#d8d0b0', '#3a1a10', '#a03030', '#c8a020'][(k + int(y)) % 6]
            m.cyl((x, y + 0.4, 13.8), 0.45, 2.4 + (k % 3) * 0.5, 'y', 'paint:' + col, 'bottle')
    for k in range(6):
        x = 18.6 + k * 1.8
        m.box((x - 0.5, 23.0, 13.2), (x + 0.5, 25.6, 14.2), 'glass', 'optic bottle')
        m.box((x - 0.3, 22.2, 13.4), (x + 0.3, 23.0, 14.0), 'metal:steel', 'optic measure')
    m.box((-6, 26.6, 14.5), (22, 29.4, 14.6), 'plate', 'pub name board', north='label:THE STATION ARMS')
    SPOTS['pub_bar'] = cupboard_shelf(m, -13.4, -2.6, ['glasses'], 6, title='BACK BAR', body='#3a2414', door='#5a3a24')
    m.g('keg cooler')
    m.box((18.4, 0, 9.4), (29.4, 11.6, 15.4), 'metal:steel', 'keg cooler + cellar hatch')
    m.box((18.8, 0.6, 9.25), (28.0, 11.0, 9.4), 'paint:#c0c4c8', 'cooler door')
    m.box((27.2, 4.0, 9.0), (27.6, 7.0, 9.25), 'metal:steel', 'cooler handle')
    m.louvres(18.8, 28.6, 0.4, 1.4, 9.3, n=6)
    for k in range(3):
        m.cyl((20.4 + k * 3.2, 11.6, 12.6), 1.3, 3.2, 'y', 'metal:steel', 'keg')
        m.box((20.0 + k * 3.2, 14.8, 12.2), (20.8 + k * 3.2, 15.4, 13.0), 'paint:#202020', 'keg coupler')
        m.box((20.3 + k * 3.2, 15.4, 12.5), (20.5 + k * 3.2, 15.6, 15.4), 'cable:c03030', 'beer line')
    cs = m.surface('north', face=9.1, title='CELLAR')
    m.ctl(cs, 'zone', 'cellar', X(23.4), 5.8, w=9.0, h=10.0, col='000000', label='')
    m.g('bar counter')
    m.box((0, 0, 4.0), (16, 13.0, 12.8), 'wood', 'bar counter')
    m.box((-0.4, 13.0, 3.0), (16.4, 13.8, 13.0), 'paint:#2a1608', 'bar top')
    m.box((0, 0.6, 3.8), (16, 1.2, 4.0), 'metal:brass', 'foot rail')
    for x in (0.4, 15.6):
        m.box((x - 0.2, 0, 3.0), (x + 0.2, 1.4, 3.8), 'metal:brass', 'foot rail bracket')
    for k in range(4):
        m.box((0.6 + k * 3.9, 2.0, 3.95), (3.9 + k * 3.9, 11.6, 4.0), 'paint:#4a2a14', 'counter panel')
    m.box((0.6, 7.2, 3.9), (3.4, 9.2, 3.95), 'paint:#3a1a08', 'till drawer front')
    desk_till = m.surface('north', face=3.85, title='TILL')
    m.ctl(desk_till, 'zone', 'till', X(2.0), 8.2, w=2.6, h=1.8, col='000000', label='')
    m.g('beer fonts')
    sf_tap = m.surface('north', face=7.4, title='TAPS')
    for k, (lab, col, cap) in enumerate((('ALE', '#7a3a10', '6a2a10'), ('LAGER', '#d8b030', 'e0c040'), ('CIDER', '#6aa030', '70b030'))):
        x = 3.6 + k * 4.4
        m.box((x - 1.2, 13.8, 7.6), (x + 1.2, 14.2, 10.6), 'metal:steel', 'drip tray')
        m.box((x - 1.0, 14.2, 7.8), (x + 1.0, 14.25, 10.4), 'mesh', 'drip grille')
        m.cyl((x, 13.8, 9.6), 0.5, 4.6, 'y', 'metal:brass', 'font column')
        m.box((x - 1.0, 17.6, 8.6), (x + 1.0, 19.0, 9.6), 'paint:' + col, 'font badge', north='label:' + lab)
        m.box((x - 0.2, 16.4, 7.8), (x + 0.2, 17.2, 9.6), 'metal:brass', 'spout')
        m.ctl(sf_tap, 'pull', 'tap%d' % (k + 1), X(x), 19.6, w=0.7, h=2.6, col=cap, label=lab)
    m.g('menu screen')
    monitor(m, 1.4, 14.6, 20.6, 26.0, 13.0, ['sign', 'm1', 'm2', 'm3', 'm4', 'msg', 'take'], cols=['ffd040', '7cf29a', '7cf29a', '7cf29a', '7cf29a', 'ffb020', '8a94a0'], stand_y=20.0)
    sf = strip(m, 3.2, 6.8, top=13.8)
    pushes(m, sf, [('mprev', 'e0e0e0', 'MENU <'), ('mnext', 'e0e0e0', 'MENU >'), ('order', '30a040', 'ORDER'), ('kegs', 'c08030', 'CHANGE KEGS')], X(14.6), 6.0, step=1.8, size=0.85)
    m.ctl(sf, 'key', 'k_open', X(6.6), 6.0, w=0.85, h=0.85, col='c0a040', label='BAR', pos=['CLOSED', 'OPEN'])
    lamps(m, sf, [('l_empty1', RED, 'ALE'), ('l_empty2', RED, 'LAGER'), ('l_empty3', RED, 'CIDER'), ('l_kitchen', GREEN, 'KITCHEN'), ('l_ready', AMBER, 'READY', True),
                  ('l_cool', BLUE, 'COOLER'), ('l_open', GREEN, 'OPEN'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], X(15.0), 4.0, step=1.25, size=0.45, lab_dv=0.5)
    gs = m.surface('north', face=9.0, title='KEG GAUGES')
    m.box((19.0, 12.0, 9.2), (28.8, 14.6, 9.4), 'paint:#24282c', 'gauge panel')
    for k in range(3):
        m.ctl(gs, 'gauge', 'g%d' % (k + 1), X(20.6 + k * 3.2), 13.3, w=1.8, h=1.8, col='f2f0e6', label=['ALE %', 'LAGER %', 'CIDER %'][k], max=100)
    m.terminal_12v(16.4, 1.0, 8.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    m.save(item_gui=0.28)
    return m


# ================================================================================================= pub kitchen (#14)
def pub_kitchen():
    m = Model('pub_kitchen')
    m.g('tiled wall')
    m.box((-14, 0, 15.4), (30, 30, 16), 'paint:#e8ece8', 'white wall tiles')
    for y in range(2, 30, 3):
        m.box((-14, y, 15.35), (30, y + 0.1, 15.4), 'paint:#c0c4c0', 'tile grout line')
    m.g('extraction hood')
    m.box((-14, 24.0, 5.0), (30, 26.0, 15.4), 'metal:steel', 'extraction canopy')
    m.box((-14, 26.0, 9.0), (30, 30.0, 15.4), 'metal:steel', 'canopy plenum')
    m.louvres(-12.0, -2.0, 24.2, 25.8, 4.9, n=8)
    m.louvres(2.0, 14.0, 24.2, 25.8, 4.9, n=8)
    m.box((-13.6, 23.8, 4.8), (29.6, 24.0, 5.2), 'paint:#c03030', 'fire suppression pipe')
    for x in (-10.0, -4.0, 4.0, 12.0):
        m.box((x - 0.3, 23.2, 6.0), (x + 0.3, 23.8, 6.6), 'metal:brass', 'suppression nozzle')
    m.g('fryer')
    m.box((-13.6, 0, 4.0), (-7.2, 12.0, 14.0), 'metal:steel', 'fryer cabinet')
    m.box((-13.2, 12.0, 6.0), (-7.6, 12.2, 13.2), 'paint:#3a2a10', 'oil well')
    for x in (-12.8, -10.2):
        m.box((x, 12.2, 7.0), (x + 2.2, 13.6, 11.0), 'mesh', 'fry basket')
        m.box((x + 0.9, 13.6, 4.2), (x + 1.3, 13.9, 7.0), 'paint:#202020', 'basket handle')
    m.g('grill')
    m.box((-7.0, 0, 4.0), (-0.4, 12.0, 14.0), 'metal:steel', 'char grill cabinet')
    for k in range(9):
        m.box((-6.6 + k * 0.7, 12.0, 6.0), (-6.3 + k * 0.7, 12.4, 13.2), 'paint:#202020', 'grill bar')
    m.box((-6.8, 11.0, 5.6), (-0.6, 11.4, 6.0), 'metal:steel', 'grease channel')
    m.g('range: oven + hob')
    m.box((0.2, 0, 4.0), (15.8, 12.0, 14.0), 'metal:steel', 'range cooker')
    m.box((1.4, 1.6, 3.8), (14.6, 7.6, 4.0), 'paint:#3a3e44', 'oven door')
    m.box((3.0, 3.0, 3.75), (13.0, 6.4, 3.8), 'glass', 'oven window')
    m.box((2.0, 7.0, 3.3), (14.0, 7.4, 3.8), 'metal:steel', 'oven door handle')
    for (x, z) in ((4.0, 7.0), (12.0, 7.0), (4.0, 11.0), (12.0, 11.0)):
        m.cyl((x, 12.0, z), 1.5, 0.3, 'y', 'paint:#202020', 'hob ring')
        m.cyl((x, 12.3, z), 0.8, 0.2, 'y', 'metal:steel', 'burner cap')
    m.cyl((12.0, 12.5, 11.0), 1.4, 1.6, 'y', 'metal:steel', 'stock pot')
    m.box((3.0, 12.4, 6.4), (5.6, 12.9, 7.6), 'paint:#202020', 'frying pan')
    m.box((5.6, 12.6, 6.8), (8.0, 12.8, 7.2), 'paint:#202020', 'pan handle')
    m.g('the pass')
    SPOTS['pub_kitchen'] = cupboard_shelf(m, 17.0, 29.4, ['pantry', 'fridge'], 6, title='THE PASS', top=11.0, body='#9aa0a6', door='#c0c4c8')
    for x in (19.0, 23.2, 27.4):
        m.box((x - 1.0, 21.0, 12.0), (x + 1.0, 22.0, 14.0), 'paint:#2a2c30', 'heat lamp hood')
        m.box((x - 0.6, 20.6, 12.4), (x + 0.6, 21.0, 13.6), 'paint:#ff6020', 'heat lamp')
        m.box((x - 0.1, 22.0, 12.9), (x + 0.1, 24.0, 13.1), 'cable:202020', 'heat lamp cord')
    m.g('ticket rail')
    m.box((17.0, 17.6, 14.4), (29.4, 18.2, 15.4), 'metal:steel', 'ticket rail')
    display(m, 17.6, 28.8, 18.4, 23.4, 14.8, ['sign', 'o1', 'o2', 'o3', 'o4'], ['ffd040', 'f0f0e0', 'f0f0e0', 'f0f0e0', 'f0f0e0'], 'TICKETS')
    m.g('station controls')
    sf = m.surface('north', face=3.85, title='LINE')
    for name, x0, x1, opts in (('fry', -13.6, -7.2, ['FISH+CHIPS', 'CHIPS']), ('grill', -7.0, -0.4, ['BURGER', 'STEAK']), ('oven', 0.2, 8.0, ['PIE+MASH', 'ROAST']), ('hob', 8.0, 15.8, ['BREAKFAST', 'SOUP'])):
        xc = (x0 + x1) / 2
        m.box((x0 + 0.4, 8.0, 3.9), (x1 - 0.4, 11.6, 4.0), 'paint:#24282c', name + ' control panel')
        display(m, x0 + 0.8, x1 - 0.8, 10.4, 11.3, 3.9, ['t_' + name], ['ffb020'], name.upper())
        m.ctl(sf, 'selector', 's_' + name, X(xc - 1.6), 9.0, w=0.9, h=0.9, col='202020', label=name.upper(), pos=opts)
        m.ctl(sf, 'push', 'go_' + name, X(xc), 9.0, w=0.9, h=0.9, col='30a040', label='START')
        m.ctl(sf, 'lamp', 'l_' + name, X(xc + 1.6), 9.0, w=0.55, h=0.55, col='ff6020', label='HEAT')
    m.box((17.0, 22.2, 4.6), (29.4, 26.0, 5.0), 'paint:#24282c', 'kitchen control panel')
    kp = m.surface('north', face=4.5, title='KITCHEN')
    m.ctl(kp, 'key', 'k_chef', X(28.4), 25.2, w=0.8, h=0.8, col='c0a040', label='CHEF', pos=['OFF', 'ON DUTY'])
    m.ctl(kp, 'key', 'k_hood', X(27.0), 25.2, w=0.8, h=0.8, col='c0a040', label='HOOD', pos=['OFF', 'FAN ON'])
    pushes(m, kp, [('bump', 'e0e0e0', 'BUMP'), ('bell', 'c0a040', 'PASS BELL')], X(25.4), 25.2, step=1.6, size=0.8)
    m.ctl(kp, 'gauge', 'g_load', X(18.4), 24.6, w=1.6, h=1.6, col='f2f0e6', label='LOAD %', max=100)
    lamps(m, kp, [('l_ready', GREEN, 'PASS', True), ('l_noing', RED, 'NO STOCK', True), ('l_smoke', RED, 'SMOKE', True), ('l_hood', GREEN, 'FAN'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')],
          X(28.4), 23.2, step=1.4, size=0.45, lab_dv=0.5)
    m.terminal_12v(16.4, 1.0, 8.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    m.save(item_gui=0.28)
    return m


# ================================================================================================= boarding lift (#15)
def boarding_lift(step, gate_open):
    deck = step * 3.0
    top = deck + 2.0
    m = Model('boarding_lift_l%d%s' % (step, '_open' if gate_open else ''), block='boarding_lift')
    m.g('pit frame')
    m.box((0, 0, 0), (16, 0.6, 16), 'metal:steel', 'pit frame')
    m.box((0.4, 0.6, 0.4), (15.6, 0.8, 15.6), 'paint:#2a2c30', 'pit floor')
    for x in (0.2, 15.2):
        m.box((x, 0.6, 1.4), (x + 0.6, 1.0, 14.6), 'haz', 'pit edge')
    m.g('lifting rams')
    for (x, z) in ((3.0, 3.0), (13.0, 3.0), (3.0, 13.0), (13.0, 13.0)):
        m.cyl((x, 0.8, z), 0.7, max(0.2, deck), 'y', 'metal:steel', 'hydraulic ram')
        m.cyl((x, 0.8, z), 1.0, 0.8, 'y', 'paint:#2a2c30', 'ram cylinder base')
    if deck > 0.5:
        for z in (2.0, 14.0):
            m.box((2.0, 0.8, z - 0.3), (14.0, 0.8 + deck * 0.5, z + 0.3), 'paint:#1c1c1e', 'scissor bellows')
    m.g('deck')
    m.box((1.0, deck, 1.0), (15.0, top, 15.0), 'metal:steel', 'deck plate')
    for k in range(6):
        m.box((1.6 + k * 2.3, top, 1.4), (2.4 + k * 2.3, top + 0.05, 14.6), 'paint:#8a9096', 'chequer rib')
    m.box((1.0, top, 14.2), (15.0, top + 0.08, 15.0), 'haz', 'track-side edge')
    m.box((1.0, top, 1.0), (15.0, top + 0.08, 1.8), 'haz', 'platform-side edge')
    m.g('side panels')
    for x in (0.0, 15.0):
        m.box((x, 0, 0), (x + 1.0, 16, 0.8), 'paint:ral1023', 'corner post')
        m.box((x, 0, 15.2), (x + 1.0, 16, 16), 'paint:ral1023', 'corner post')
        m.box((x + 0.3, 1.0, 0.8), (x + 0.7, 15.6, 15.2), 'glass', 'side glazing')
        m.box((x, 15.4, 0.8), (x + 1.0, 16.0, 15.2), 'paint:ral1023', 'top rail')
        m.cyl((x + 0.5 + (0.8 if x == 0 else -0.8), 10.0, 0.8), 0.3, 14.4, 'z', 'metal:steel', 'handrail')
        for z in (4.0, 8.0, 12.0):
            m.box((x + 0.3, 9.6, z - 0.15), (x + 0.7, 10.0, z + 0.15), 'metal:steel', 'handrail bracket')
    m.g('gates')
    if not gate_open:
        m.box((1.0, top, 15.2), (15.0, top + 0.8, 15.8), 'paint:ral1023', 'track gate bottom rail')
        m.box((1.0, top + 13.6, 15.2), (15.0, top + 14.2, 15.8), 'paint:ral1023', 'track gate top rail')
        for k in range(7):
            m.box((1.6 + k * 2.0, top + 0.8, 15.35), (2.0 + k * 2.0, top + 13.6, 15.65), 'paint:ral1023', 'track gate bar')
        m.box((7.6, top + 6.0, 15.0), (8.4, top + 7.2, 15.2), 'paint:#c03030', 'gate interlock switch')
    else:
        m.box((1.0, top, 14.4), (1.6, top + 14.2, 15.8), 'paint:ral1023', 'track gate folded')
        m.box((14.4, top, 14.4), (15.0, top + 14.2, 15.8), 'paint:ral1023', 'track gate folded')
    if not gate_open or step > 0:
        m.box((1.0, top, 0.2), (9.4, top + 0.8, 0.8), 'paint:ral1023', 'platform gate bottom rail')
        m.box((1.0, top + 13.0, 0.2), (9.4, top + 13.6, 0.8), 'paint:ral1023', 'platform gate top rail')
        for k in range(4):
            m.box((1.6 + k * 2.0, top + 0.8, 0.35), (2.0 + k * 2.0, top + 13.0, 0.65), 'paint:ral1023', 'platform gate bar')
    else:
        m.box((1.0, top, 0.2), (1.6, top + 13.6, 1.4), 'paint:ral1023', 'platform gate folded')
    m.g('control post')
    m.box((9.5, 0, 0.0), (15.0, 16.0, 1.4), 'paint:#2a2c30', 'control post')
    m.box((9.3, 16.0, -0.2), (15.2, 16.4, 1.6), 'paint:ral1023', 'post cap')
    m.box((9.7, 0.2, -0.05), (14.8, 0.9, 0.0), 'plate', 'post plate', north='label:LIFT')
    sf = m.surface('north', face=-0.1, title='LIFT')
    for (cid, v, col) in (('ht', 15.1, 'ffd040'), ('tr', 14.2, '5bcefa'), ('msg', 13.3, '7cf29a')):
        m.box((9.8, v - 0.42, -0.06), (14.7, v + 0.42, 0.0), 'paint:#03140a', cid + ' window')
        m.ctl(sf, 'digits', cid, X(12.25), v, w=4.7, h=0.7, col=col, label='', fmt='%s')
    lamps(m, sf, [('l_up', GREEN, 'UP'), ('l_dn', GREEN, 'DN'), ('l_gate', AMBER, 'GATE', True), ('l_train', BLUE, 'TRAIN'), ('l_ready', GREEN, 'LEVEL')], X(10.2), 12.0, step=-1.05, size=0.42, lab_dv=0.42)
    lamps(m, sf, [('l_over', RED, 'OVLD', True), ('l_gap', GREEN, 'GAP'), ('l_fault', RED, 'FLT', True), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAIN')], X(10.2), 10.8, step=-1.05, size=0.42, lab_dv=0.42)
    pushes(m, sf, [('up', '30a040', 'UP'), ('down', '30a040', 'DOWN'), ('gate', 'f0d020', 'GATE'), ('call', 'e0e0e0', 'CALL')], X(10.3), 9.1, step=-1.3, size=0.8, lab_dv=0.6)
    m.ctl(sf, 'mushroom', 'm_estop', X(10.6), 7.0, w=1.3, h=1.3, col='e02020', label='E-STOP')
    m.ctl(sf, 'push', 'reset', X(12.3), 7.0, w=0.8, h=0.8, col='2050d0', label='RESET')
    m.ctl(sf, 'key', 'k_staff', X(13.9), 7.0, w=0.8, h=0.8, col='c0a040', label='STAFF', pos=['OFF', 'ON'])
    m.ctl(sf, 'selector', 's_mode', X(10.6), 5.2, w=0.8, h=0.8, col='202020', label='MODE', pos=['AUTO', 'MANUAL'])
    m.ctl(sf, 'selector', 's_trim', X(12.3), 5.2, w=0.8, h=0.8, col='202020', label='TRIM', pos=['-2', '-1', '0', '+1', '+2'])
    m.ctl(sf, 'key', 'k_hand', X(13.9), 5.2, w=0.8, h=0.8, col='c03030', label='HAND LWR', pos=['OFF', 'BRAKE OFF'])
    m.ctl(sf, 'gauge', 'g_ht', X(12.25), 2.6, w=1.8, h=1.8, col='f2f0e6', label='DECK PX', max=12)
    m.terminal_12v(16.0, 1.0, 5.0, 'east')
    mains_box(m, 16.0, 1.0, 11.0, 'east')
    m.save(item_gui=0.5)
    return m


# ================================================================================================= gap filler (#15)
def gap_filler(e):
    m = Model('gap_filler_%d' % e, block='gap_filler')
    out = [0.0, 0.5, 1.0][e]
    m.g('housing')
    m.box((0, 0, 0), (16, 2.0, 12), 'metal:steel', 'housing frame')
    for k in range(7):
        m.box((0.6 + k * 2.2, 2.0, 0.4), (1.6 + k * 2.2, 2.05, 11.4), 'paint:#8a9096', 'chequer rib')
    m.box((0, 1.6, 10.8), (16, 2.06, 12.0), 'haz', 'edge stripe')
    m.box((0.2, 0.4, 11.9), (15.8, 1.6, 12.0), 'paint:#101010', 'plate slot')
    for x in (2.0, 8.0, 14.0):
        m.bolt(x, 2.05, 6.0, 'up', 0.35)
    m.g('plate')
    zl = 12.0 + 18.0 * out
    if out > 0:
        m.box((0.4, 0.4, 12.0), (15.6, 1.5, zl - 0.6), 'metal:steel', 'bridging plate')
        m.box((0.4, 0.4, zl - 0.6), (15.6, 1.5, zl), 'haz', 'plate nose')
        for k in range(6):
            m.box((1.0 + k * 2.5, 1.5, 12.2), (1.8 + k * 2.5, 1.55, zl - 0.8), 'paint:#8a9096', 'plate rib')
        m.box((7.6, 0.2, 12.0), (8.4, 0.4, zl - 0.6), 'metal:steel', 'drive rack')
    else:
        m.box((0.4, 0.4, 11.95), (15.6, 1.5, 12.0), 'haz', 'plate nose (in)')
    m.g('control cabinet')
    m.box((0, 2.0, 0), (5, 10.0, 5), 'paint:ral7035', 'gap filler cabinet')
    m.box((-0.1, 10.0, -0.1), (5.1, 10.4, 5.1), 'paint:ral7016', 'cabinet cap')
    m.box((0.2, 2.2, -0.05), (4.8, 2.9, 0.0), 'plate', 'cabinet plate', north='label:GAP FILLER')
    sf = m.surface('north', face=-0.1, title='GAP FILLER')
    for (cid, v, col) in (('msg', 9.3, 'ffd040'), ('cnt', 8.4, '8a94a0')):
        m.box((0.3, v - 0.4, -0.06), (4.7, v + 0.4, 0.0), 'paint:#03140a', cid + ' window')
        m.ctl(sf, 'digits', cid, X(2.5), v, w=4.2, h=0.7, col=col, label='', fmt='%s')
    lamps(m, sf, [('l_ext', GREEN, 'OUT'), ('l_ret', GREEN, 'IN'), ('l_moving', AMBER, 'MOVE', True), ('l_obst', RED, 'OBST', True)], X(0.8), 7.1, step=-1.1, size=0.42, lab_dv=0.42)
    lamps(m, sf, [('l_fault', RED, 'FLT', True), ('l_train', BLUE, 'TRAIN'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAIN')], X(0.8), 5.8, step=-1.1, size=0.42, lab_dv=0.42)
    m.ctl(sf, 'selector', 's_mode', X(1.4), 3.9, w=0.9, h=0.9, col='202020', label='MODE', pos=['AUTO', 'OUT', 'IN'])
    m.ctl(sf, 'push', 'reset', X(3.6), 3.9, w=0.9, h=0.9, col='2050d0', label='RESET')
    m.terminal_12v(2.5, 2.2, 5.0, 'south')
    mains_box(m, 5.0, 2.0, 2.5, 'east')
    m.g('mechanism details')
    for x in (0.6, 15.0):
        for z in (2.0, 6.0, 10.0):
            m.bolt(x + 0.2, 1.0, z, 'up', 0.2)
    for x in (1.2, 14.4):
        m.box((x, 0.2, 5.4), (x + 0.4, 0.4, 12.0), 'metal:steel', 'plate guide rail')
        for z in (6.0, 10.0):
            m.cyl((x - 0.2, 0.3, z), 0.25, 0.8, 'x', 'paint:#202020', 'guide roller')
    m.cyl((5.0, 4.0, 3.0), 0.9, 2.6, 'x', 'paint:#3a5a8a', 'drive motor')
    m.box((7.6, 3.2, 2.2), (8.4, 4.8, 3.8), 'paint:#2a2c30', 'gearbox')
    for x in (3.0, 13.0):
        m.box((x - 0.4, 2.0, 11.0), (x + 0.4, 2.4, 11.6), 'paint:#1c1e22', 'train presence sensor')
        m.box((x - 0.2, 2.4, 11.2), (x + 0.2, 2.5, 11.6), 'paint:#ff4020', 'sensor lens')
    for k in range(10):
        m.box((5.6 + k * 1.0, 2.0, 8.4), (6.2 + k * 1.0, 2.2, 9.0), 'paint:#f0c018', 'tactile blister')
    m.box((4.8, 2.0, 1.0), (5.4, 2.4, 5.0), 'cable:1d1d21', 'cabinet cable')
    m.save(item_gui=0.5)
    return m


# ================================================================================================= items
FOOD_PIX = {   # simple 16x16 item art: (base colour, accent)
    'pint_ale': ('#8a4a14', '#f4ecd0'), 'pint_lager': ('#e0b030', '#f8f4e8'), 'pint_cider': ('#d89a30', '#f8f0d8'),
    'fish_chips': ('#d8a040', '#f0d070'), 'pie_mash': ('#b07030', '#f0e8d8'), 'burger': ('#8a4a20', '#e0b060'),
    'breakfast': ('#c04020', '#f0e080'), 'soup': ('#d07020', '#a87850'), 'roast': ('#8a4a20', '#e0c070'),
    'chips': ('#f0c050', '#d04030'), 'steak': ('#7a2a1a', '#c04030'),
}


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def item_textures():
    d = os.path.join(ASSETS, 'textures/items')
    os.makedirs(d, exist_ok=True)
    for name, (base, acc) in FOOD_PIX.items():
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        b, a = hexrgb(base), hexrgb(acc)
        if name.startswith('pint'):
            for y in range(3, 15):
                for x in range(4, 12):
                    img.putpixel((x, y), (200, 220, 230, 140) if x in (4, 11) else (a if y < 5 else b))
            for x in range(4, 12): img.putpixel((x, 15), (200, 220, 230, 200))
        else:
            for y in range(8, 14):
                for x in range(1, 15):
                    if (x - 7.5) ** 2 / 49 + (y - 10.5) ** 2 / 9 <= 1: img.putpixel((x, y), (236, 236, 232, 255))
            for y in range(7, 12):
                for x in range(4, 12):
                    if (x - 7.5) ** 2 / 14 + (y - 9) ** 2 / 4 <= 1: img.putpixel((x, y), a if (x + y) % 3 == 0 else b)
        img.save(os.path.join(d, 'pub_%s.png' % name))
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/pub_' + name}}, open(os.path.join(ASSETS, 'models/item/pub_%s.json' % name), 'w'), indent=1)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(4, 12):
        for x in range(1, 15):
            img.putpixel((x, y), (40, 60, 110, 255) if y > 5 else (200, 160, 40, 255))
    for x in range(3, 8): img.putpixel((x, 9), (230, 230, 230, 255))
    for y in range(7, 11): img.putpixel((12, y), (230, 200, 60, 255))
    img.save(os.path.join(d, 'room_key.png'))
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/room_key'}}, open(os.path.join(ASSETS, 'models/item/room_key.json'), 'w'), indent=1)


RECIPES = {
    'bus_bay': (['SFS', ' P ', ' I '], {'S': 'irextras:part@20', 'F': 'minecraft:sign', 'P': 'minecraft:iron_bars', 'I': 'minecraft:iron_ingot'}),
    'bus_departures': (['SLS', 'CFC', 'I I'], {'S': 'irextras:part@20', 'L': 'minecraft:redstone_lamp', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot'}),
    'taxi_rank': (['YLY', 'SCS', 'IRI'], {'Y': 'minecraft:dye@11', 'L': 'minecraft:redstone_lamp', 'S': 'irextras:part@20', 'C': 'irextras:part@2', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'hotel_reception': (['WBW', 'WSW', 'WCW'], {'W': 'minecraft:planks', 'B': 'minecraft:bed', 'S': 'irextras:part@20', 'C': 'minecraft:chest'}),
    'room_lock': ([' S ', 'IRI', ' T '], {'S': 'irextras:part@20', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone', 'T': 'minecraft:tripwire_hook'}),
    'room_wardrobe': (['WWW', 'WCW', 'WIW'], {'W': 'minecraft:planks', 'C': 'minecraft:chest', 'I': 'minecraft:iron_ingot'}),
    'pub_bar': (['GBG', 'WSW', 'WCW'], {'G': 'minecraft:glass_bottle', 'B': 'minecraft:brewing_stand', 'W': 'minecraft:planks', 'S': 'irextras:part@20', 'C': 'minecraft:chest'}),
    'pub_kitchen': (['IHI', 'FSF', 'ICI'], {'I': 'minecraft:iron_ingot', 'H': 'minecraft:hopper', 'F': 'minecraft:furnace', 'S': 'irextras:part@20', 'C': 'minecraft:chest'}),
    'boarding_lift': (['B B', 'BPB', 'IRI'], {'B': 'minecraft:iron_bars', 'P': 'minecraft:piston', 'I': 'minecraft:iron_block', 'R': 'irextras:part@2'}),
    'gap_filler': (['   ', 'PSP', 'IRI'], {'P': 'minecraft:heavy_weighted_pressure_plate', 'S': 'minecraft:piston', 'I': 'minecraft:iron_ingot', 'R': 'irextras:part@2'}),
}
DATA = {'minecraft:planks': 0, 'minecraft:bed': 0}


def ing6(v):
    r = ing(v)
    if v in DATA: r['data'] = DATA[v]
    return r


LANG = {
    'bus_bay': 'Bus Bay', 'bus_departures': 'Bus Departure Board', 'taxi_rank': 'Taxi Rank', 'hotel_reception': 'Hotel Reception Desk',
    'room_lock': 'Hotel Room Lock', 'room_wardrobe': 'Hotel Room Wardrobe', 'pub_bar': 'Pub Bar', 'pub_kitchen': 'Pub Kitchen',
    'boarding_lift': 'Accessibility Boarding Lift', 'gap_filler': 'Platform Gap Filler',
}


def facing_variants(model, extra=''):
    return {('facing=%s%s' % (f, extra)): ({'model': 'irextras:' + model, 'y': y} if y else {'model': 'irextras:' + model}) for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}


if __name__ == '__main__':
    for fn in (bus_bay, bus_departures, taxi_rank, hotel_reception, room_lock, room_wardrobe, pub_bar, pub_kitchen):
        mm = fn()
        print('%-18s %d boxes' % (mm.name, len(mm.boxes)))
    for s in range(5):
        for g in (False, True):
            mm = boarding_lift(s, g)
            print('%-18s %d boxes' % (mm.name, len(mm.boxes)))
    for e in range(3):
        mm = gap_filler(e)
        print('%-18s %d boxes' % (mm.name, len(mm.boxes)))
    item_textures()
    for n in RECIPES:
        if n == 'boarding_lift':
            v = {}
            for lv in range(10):
                step, g = lv % 5, lv >= 5
                v.update(facing_variants('boarding_lift_l%d%s' % (step, '_open' if g else ''), ',level=%d' % lv))
            bs = {'variants': v}
            item_parent = 'irextras:block/boarding_lift_l0'
        elif n == 'gap_filler':
            v = {}
            for lv in range(10):
                v.update(facing_variants('gap_filler_%d' % min(lv, 2), ',level=%d' % lv))
            bs = {'variants': v}
            item_parent = 'irextras:block/gap_filler_2'
        else:
            bs = {'variants': facing_variants(n)}
            item_parent = 'irextras:block/' + n
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': item_parent}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': [p for p in pat if p.strip()] or pat, 'key': {k: ing6(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    for k, v in SPOTS.items():
        print(k, 'shelf spots (model px):', v)
    print('--- lang')
    for n, t in LANG.items():
        print('tile.irextras.%s.name=%s' % (n, t))
    print('item.irextras.room_key.name=Hotel Room Key Card')
    for n, t in zip(['pint_ale', 'pint_lager', 'pint_cider', 'fish_chips', 'pie_mash', 'burger', 'breakfast', 'soup', 'roast', 'chips', 'steak'],
                    ['Pint of Ale', 'Pint of Lager', 'Pint of Cider', 'Fish and Chips', 'Pie and Mash', 'Burger', 'Full Breakfast', 'Vegetable Soup', 'Roast Dinner', 'Chips', 'Steak']):
        print('item.irextras.pub_food.%s.name=%s' % (n, t))
    print('entity.irextras.service_vehicle.name=Bus / Taxi')
