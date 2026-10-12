#!/usr/bin/env python3
"""Batch 6 #16-20 (her words 10-12: "complex buttons and knobs and stuff, screens", Blockbench models, real looking,
everything functional): rack (cog) railway, train ferry, motorail ramp, ride-on garden railway, avalanche protection.

Models face north. Every lamp, button, key, knob, gauge and screen line is a live control the Java tiles
(com.dogpound.railmap.newrail6) read or work. Moving / travelling things (the ferry and its stern door, the garden
locomotive and cars, the tarpaulined motorail vehicle) are baked as item variants of the Garden Train Set and drawn by
Java. Also writes blockstates, item models, the train-set icon, recipes and lang."""
import json, math, os
from PIL import Image, ImageDraw
from bbkit import Model, ASSETS
from gen_bb_ctl import facing_states, ing
from gen_bb_ride import lamp, push, mush, digits, sel, gauge, bar

GALV = 'galv'
STEEL = 'metal:steel'
DARK = 'paint:#2c2f34'
CAB = 'paint:ral7035'
CONC = 'concrete'
BALLAST = 'paint:#6e665c'


def screen_lines(m, sf, x, y_top, w, n=6, h=0.75, gap=0.95, first='ffb020', rest='7cf29a'):
    for i in range(n):
        digits(m, sf, 'd%d' % (i + 1), x, y_top - i * gap, w, h, first if i == 0 else rest)


def page_knob(m, sf, x, y, pages, s=1.3):
    sel(m, sf, 's_page', x, y, 'PAGE', pages, s)


def cabinet(m, x0, x1, y0, y1, z0, z1, mat=CAB, name='cabinet', hinge='east'):
    """an outdoor steel cabinet: body, rain hood, door seam, hinges, a lock, vent louvres on the side"""
    m.box((x0, y0, z0), (x1, y1, z1), mat, name)
    m.box((x0 - 0.3, y1, z0 - 0.6), (x1 + 0.3, y1 + 0.5, z1 + 0.3), 'paint:#a8aaa6', 'rain hood')
    m.box((x0 - 0.2, y1 - 0.1, z0 - 0.7), (x1 + 0.2, y1 + 0.1, z0 - 0.5), 'paint:#8c8e8a', 'hood drip edge')
    hx = x1 - 0.2 if hinge == 'east' else x0
    for y in (y0 + 1.2, y1 - 1.6):
        m.box((hx, y, z0 - 0.25), (hx + 0.2, y + 1.0, z0), 'metal:steel', 'door hinge')
    lx = x0 + 0.4 if hinge == 'east' else x1 - 0.9
    m.box((lx, (y0 + y1) / 2 - 0.6, z0 - 0.3), (lx + 0.5, (y0 + y1) / 2 + 0.6, z0), 'metal:alu', 'door handle')
    m.box((lx + 0.05, (y0 + y1) / 2 - 1.3, z0 - 0.35), (lx + 0.45, (y0 + y1) / 2 - 0.8, z0), 'metal:brass', 'cam lock')
    for k in range(4):
        y = y0 + 1.0 + k * 0.8
        m.box((x1, y, z0 + 0.8), (x1 + 0.15, y + 0.35, z1 - 0.8), 'paint:#8c8e8a', 'side louvre')


def plinth(m, x0, x1, z0, z1, h=1.0):
    m.box((x0, 0, z0), (x1, h, z1), CONC, 'concrete plinth')
    for (x, z) in ((x0 + 0.6, z0 + 0.6), (x1 - 0.6, z0 + 0.6), (x0 + 0.6, z1 - 0.6), (x1 - 0.6, z1 - 0.6)):
        m.box((x - 0.3, h, z - 0.3), (x + 0.3, h + 0.4, z + 0.3), STEEL, 'anchor bolt')


# ================================================================================================ #16 rack railway
def rack_section(m, entry=False):
    m.g('ballast bed')
    m.box((0, 0, 0), (16, 15.2, 16), BALLAST, 'ballast')
    m.box((0, 15.2, 0), (16, 15.6, 16), 'paint:#7a7268', 'ballast top', faces=('up',))
    m.g('rack sleepers')
    for z in (1.5, 5.5, 9.5, 13.5):
        m.box((1.0, 15.0, z - 1.2), (15.0, 16.2, z + 1.2), CONC, 'concrete rack sleeper')
        for x in (6.0, 10.0):
            m.box((x - 1.4, 16.2, z - 0.9), (x + 1.4, 16.5, z + 0.9), STEEL, 'rack chair base')
            m.bolt(x - 0.9, 16.5, z - 0.5, 'up', 0.35); m.bolt(x + 0.9, 16.5, z + 0.5, 'up', 0.35)
        m.box((6.2, 16.5, z - 0.7), (9.8, 16.9, z + 0.7), STEEL, 'rack chair')
    m.g('Abt rack bars')
    for bx, off in ((6.6, 0.0), (8.6, 0.5)):
        lo = 16.9
        if entry:
            m.box((bx, lo, 0), (bx + 0.8, lo + 0.9, 16), 'metal:steel', 'entry rack bar (sprung)')
        else:
            m.box((bx, lo, 0), (bx + 0.8, lo + 1.1, 16), 'metal:steel', 'rack bar')
        for i in range(16):
            z = i + off
            if z + 0.55 > 16: continue
            h = 1.0 if not entry else 0.25 + 0.75 * min(1.0, i / 12.0)              # entry: tapered teeth rising to full height
            top = lo + (0.9 if entry else 1.1)
            m.box((bx - 0.05, top, z + 0.1), (bx + 0.85, top + h, z + 0.55), 'paint:#8a9096', 'rack tooth')
        for z in (0.2, 15.6):
            m.box((bx - 0.2, lo + 0.2, z), (bx + 1.0, lo + 0.7, z + 0.2), 'metal:brass', 'fishplate')


def rack_rail():
    m = Model('rack_rail')
    rack_section(m)
    m.g('earthing')
    m.box((3.0, 15.6, 0.0), (3.4, 15.9, 16.0), 'metal:copper', 'rack bond')
    m.box((12.0, 15.6, 7.6), (13.0, 16.4, 8.4), 'metal:copper', 'bond clamp')
    m.save(item_gui=0.55)
    return m


def rack_entry():
    m = Model('rack_entry')
    rack_section(m, entry=True)
    m.g('entry springs')
    for z in (2.0, 7.0):
        for bx in (7.0, 9.0):
            for k in range(4):
                m.cyl((bx, 15.6 + k * 0.3, z), 0.55, 0.18, 'y', 'paint:ral3000', 'entry spring coil')
    m.box((6.0, 16.9, 0.0), (10.0, 17.1, 1.2), 'haz', 'entry nose')
    m.g('lubricator nozzles')
    for bx in (6.4, 9.6):
        m.cyl((bx, 16.6, 12.0), 0.3, 1.2, 'y', 'metal:brass', 'oil nozzle')
    m.cyl((4.0, 15.6, 12.0), 0.25, 0.4, 'y', 'cable:202020', 'oil feed pipe')
    m.box((4.0, 15.6, 11.8), (6.4, 15.9, 12.2), 'cable:202020', 'oil feed pipe')
    m.g('rack board')
    m.box((14.2, 15.6, 7.2), (14.8, 26.0, 7.8), GALV, 'board post')
    m.box((12.6, 22.0, 6.9), (16.0, 26.0, 7.2), 'paint:ral9010', 'RACK board')
    m.box((12.7, 22.1, 6.85), (15.9, 25.9, 6.9), 'label:RACK|10', 'RACK board face', faces=('north',))
    m.box((14.0, 15.6, 7.0), (15.0, 16.0, 8.0), CONC, 'post footing')
    m.save(item_gui=0.55)
    return m


def rack_panel():
    m = Model('rack_panel')
    m.g('plinth + post')
    plinth(m, 3.0, 13.0, 5.0, 12.0)
    m.box((6.8, 1.0, 9.0), (9.2, 3.0, 11.0), GALV, 'post foot')
    m.g('panel cabinet')
    cabinet(m, 2.0, 14.0, 3.0, 24.0, 5.0, 11.0, CAB, 'panel cabinet')
    m.box((2.2, 3.2, 4.9), (13.8, 23.8, 5.0), 'paint:#b8bab6', 'panel door')
    m.box((3.0, 17.0, 4.88), (13.0, 23.4, 4.92), 'screen:0a1410', 'screen glass')
    m.box((2.8, 16.8, 4.85), (13.2, 17.0, 4.95), 'paint:#141416', 'screen bezel')
    m.box((4.0, 23.45, 4.85), (12.0, 23.95, 4.9), 'label:RACK PANEL', 'title plate', faces=('north',))
    m.g('lubricator')
    LUB = 'paint:#33506e'
    m.box((14.0, 3.0, 6.0), (18.6, 9.0, 10.0), LUB, 'oil tank')
    m.box((14.4, 4.0, 5.9), (15.6, 8.0, 6.0), 'glass', 'oil level glass')
    m.box((16.0, 3.4, 5.9), (18.2, 8.6, 6.0), 'label:LUBE OIL', 'tank plate', faces=('north',))
    m.box((14.5, 5.6, 5.85), (15.5, 5.9, 5.9), 'paint:#c08020', 'oil level')
    m.cyl((16.3, 9.0, 8.0), 0.8, 0.8, 'y', 'metal:brass', 'filler cap')
    m.cyl((16.3, 9.0, 6.6), 1.2, 3.0, 'y', 'paint:#2f5a40', 'pump motor')
    m.cyl((16.3, 12.0, 6.6), 1.0, 0.4, 'y', 'mesh', 'motor cowl')
    m.box((14.0, 3.0, 10.0), (18.6, 3.4, 10.4), 'paint:#2a2d30', 'tank strap')
    m.cyl((18.6, 4.0, 8.0), 0.25, 3.0, 'x', 'cable:202020', 'oil line to rack')
    m.box((14.2, 9.6, 5.9), (17.8, 10.6, 6.0), 'label:LUBE', 'tank label', faces=('north',))
    m.g('heater + supply')
    m.box((-1.6, 6.0, 6.0), (2.0, 10.0, 10.0), 'paint:ral7032', 'heater box')
    m.louvres(-1.4, 1.8, 6.4, 9.6, 6.0, n=4)
    m.gland(0.2, 6.0, 8.0, 'down', r=0.35, cable='cable:1d1d21')
    m.gland(4.0, 3.0, 9.0, 'down', r=0.4, cable='cable:2060c0', length=2.0)
    m.terminal_12v(8.0, 6.0, 11.0, 'south')
    m.box((5.0, 12.0, 11.0), (11.0, 15.0, 11.1), 'danger:DANGER|240V AC', 'danger plate', faces=('south',))
    m.box((10.0, 3.0, 11.0), (10.8, 9.0, 11.6), 'metal:copper', 'earth bar')
    m.g('lightning arrester')
    m.cyl((12.0, 24.5, 9.0), 0.4, 3.0, 'y', 'porcelain', 'arrester')
    m.cyl((12.0, 27.5, 9.0), 0.2, 1.0, 'y', 'metal:copper', 'arrester stud')
    m.cyl((12.0, 24.0, 9.6), 0.12, 0.5, 'z', 'metal:copper', 'arrester lead')
    m.g('battery backup')
    m.box((3.0, 1.0, 11.4), (13.0, 3.0, 14.6), 'paint:ral7032', 'battery box')
    for x in (3.6, 8.2):
        m.box((x, 3.0, 11.8), (x + 4.2, 3.4, 14.2), 'battery', '12 V battery')
        for dx in (0.6, 3.4):
            m.box((x + dx, 3.4, 12.6), (x + dx + 0.4, 3.7, 13.0), 'metal:brass', 'battery post')
    m.box((3.2, 1.6, 14.6), (12.8, 2.6, 14.7), 'label:24H BATTERY', 'battery label', faces=('south',))
    m.g('oil line to the rack')
    for x in (19.0, 21.5, 24.0, 26.5, 29.0, 31.5):
        m.box((x - 0.3, 3.6, 7.6), (x + 0.3, 4.4, 8.4), 'metal:steel', 'pipe clamp')
        m.box((x - 0.2, 1.0, 7.8), (x + 0.2, 3.6, 8.2), 'metal:steel', 'pipe stanchion')
    m.box((20.6, 3.4, 7.4), (21.8, 5.2, 8.6), 'paint:#c03020', 'pressure switch')
    m.box((23.0, 3.6, 7.5), (24.0, 4.8, 8.5), 'metal:brass', 'line strainer')
    m.box((14.4, 0.0, 6.6), (18.4, 1.0, 9.4), CONC, 'tank plinth')
    m.g('conduits + signs')
    for x in (4.0, 6.0):
        m.box((x - 0.3, 1.0, 9.0), (x + 0.3, 3.0, 9.6), 'paint:#6a6e74', 'conduit')
    m.box((2.0, 3.2, 11.0), (2.6, 23.8, 11.2), 'paint:#8c8e8a', 'door gasket edge')
    m.box((13.4, 3.2, 11.0), (14.0, 23.8, 11.2), 'paint:#8c8e8a', 'door gasket edge')
    m.box((4.0, 18.0, 11.0), (12.0, 21.0, 11.1), 'warn', 'rack warning', faces=('south',))
    m.box((4.0, 16.0, 11.0), (12.0, 17.6, 11.1), 'label:KEEP OFF RACK', 'rack sign', faces=('south',))
    m.box((-1.4, 10.4, 6.0), (1.8, 11.6, 5.9), 'label:HEATER', 'heater label', faces=('north',))
    m.box((14.2, 10.8, 5.9), (18.4, 12.0, 6.0), 'label:OIL ONLY', 'oil label', faces=('north',))
    m.box((1.4, 13.0, 6.0), (2.0, 14.0, 7.0), 'metal:brass', 'padlock hasp')
    m.cyl((10.4, 0.0, 12.4), 0.25, 1.0, 'y', 'metal:copper', 'earth rod')
    m.box((10.0, 0.9, 12.0), (10.8, 1.3, 12.8), 'metal:brass', 'earth clamp')

    sf = m.surface('north', face=4.85, title='RACK PANEL')
    screen_lines(m, sf, 8.0, 22.7, 9.4, h=0.72, gap=0.98)
    for (cid, x, lab) in (('g_spd', 4.4, 'KM/H %'), ('g_grade', 8.0, 'GRADE'), ('g_lube', 11.6, 'LUBE BAR')):
        gauge(m, sf, cid, x, 14.6, lab, 2.8)
    for i, (cid, col, lab) in enumerate((('l_pwr', '30e060', '12V'), ('l_lube', '30e060', 'LUBE'), ('l_on', 'f0f0f0', 'ON RACK'), ('l_ovs', 'ff3030', 'OVERSPEED'),
                                         ('l_non', 'ffb020', 'NON-RACK'), ('l_entry', 'ff8020', 'ENTRY'), ('l_wear', 'ffd040', 'WEAR'), ('l_estop', 'ff2020', 'E-STOP'))):
        lamp(m, sf, cid, 3.6 + (i % 4) * 2.9, 12.2 - (i // 4) * 1.1, col, lab, 0.6)
    sel(m, sf, 'k_en', 3.4, 8.8, 'ENABLE', ['OFF', 'ON'], 1.3, t='key')
    sel(m, sf, 's_vmax', 5.6, 8.8, 'RACK KM/H', ['10', '15', '20', '25', '30'], 1.3)
    sel(m, sf, 's_entry', 7.8, 8.8, 'ENTRY KM/H', ['5', '8', '10', '15'], 1.3)
    sel(m, sf, 's_act', 10.0, 8.8, 'OVERSPEED', ['ALARM', 'BRAKE'], 1.3)
    sel(m, sf, 's_lube', 12.4, 8.8, 'LUBRICATOR', ['OFF', 'AUTO'], 1.3)
    push(m, sf, 'tag', 3.4, 6.2, '30c040', 'TAG COG', 1.1)
    push(m, sf, 'untag', 5.4, 6.2, '808080', 'UNTAG', 1.1)
    push(m, sf, 'renew', 7.4, 6.2, '2050d0', 'RENEW PINION', 1.1)
    push(m, sf, 'ack', 9.4, 6.2, 'f0d020', 'ACK', 1.1)
    push(m, sf, 'test', 11.4, 6.2, 'f0f0f0', 'LAMP TEST', 0.9)
    page_knob(m, sf, 12.8, 4.2, ['OVERVIEW', 'LOCOS', 'SECTION', 'EVENTS'], 1.2)
    mush(m, sf, 'm_estop', 6.0, 4.3, 'E-STOP', 1.8)
    m.save(item_gui=0.5)
    return m


# ================================================================================================ #17 train ferry
def ferry_dock():
    m = Model('ferry_dock')
    m.g('quay slab')
    m.box((-16, 0, 0), (32, 2.0, 16), CONC, 'quay slab')
    m.box((-16, 2.0, 0.0), (32, 2.3, 0.6), 'paint:ral1023', 'quay edge line')
    for x in (-12, -4, 20, 28):
        m.cyl((x, 2.0, 12.0), 1.2, 2.4, 'y', 'paint:#2a2d30', 'bollard')
        m.cyl((x, 4.4, 12.0), 1.5, 0.5, 'y', 'paint:#2a2d30', 'bollard cap')
    for x in (-15.0, 31.0):
        m.box((x - 0.8, 0.0, 15.0), (x + 0.8, 2.0, 16.6), 'rubber', 'quay fender')
    m.g('control kiosk')
    K = 'paint:#e8e4d0'
    m.box((0.5, 2.0, 1.6), (15.5, 21.0, 12.0), K, 'kiosk body')
    m.box((0.0, 21.0, 1.0), (16.0, 22.0, 12.6), 'paint:#3a3e44', 'kiosk roof')
    m.box((-0.5, 20.4, 0.4), (16.5, 21.0, 1.6), 'paint:#3a3e44', 'awning')
    m.box((0.5, 2.0, 1.4), (15.5, 3.0, 1.6), 'paint:#141416', 'kick plate')
    for x in (0.5, 15.0):
        m.box((x, 2.0, 1.2), (x + 0.5, 20.4, 1.6), 'paint:#3a3e44', 'panel frame post')
    m.box((1.0, 3.2, 1.55), (15.0, 20.2, 1.6), 'paint:#c8c4b0', 'control panel face')
    m.box((1.4, 15.0, 1.5), (10.6, 20.0, 1.55), 'screen:0a1420', 'screen glass')
    m.box((1.2, 14.8, 1.45), (10.8, 15.0, 1.55), 'paint:#141416', 'screen bezel')
    for side, x in (('east', 15.5), ('west', 0.5)):
        m.box((x - 0.05 if side == 'east' else x - 0.05, 10.0, 4.0), (x + 0.05, 17.0, 10.0), 'glass', 'kiosk window')
    m.box((15.5, 2.0, 4.5), (15.6, 16.0, 8.5), 'paint:#5a5e62', 'kiosk door')
    m.box((15.6, 8.6, 7.6), (15.9, 9.4, 8.0), 'metal:alu', 'door handle')
    m.box((3.0, 21.1, 0.9), (13.0, 21.9, 1.0), 'label:LINK SPAN CONTROL', 'kiosk sign', faces=('north',))
    m.g('floodlight mast')
    m.box((1.0, 22.0, 10.0), (2.0, 32.0, 11.0), GALV, 'floodlight mast')
    m.box((-0.5, 30.0, 8.0), (3.5, 31.6, 10.0), 'paint:#2a2d30', 'floodlight head')
    m.box((-0.3, 30.2, 7.9), (3.3, 31.4, 8.0), 'lamp:fff2d0', 'floodlight lens', faces=('north',))
    m.box((13.0, 22.0, 6.0), (15.0, 23.0, 8.0), 'paint:#c03020', 'warning beacon base')
    m.cyl((14.0, 23.0, 7.0), 0.7, 1.2, 'y', 'lamp:ff9020', 'warning beacon')
    m.box((6.0, 22.0, 4.0), (8.0, 25.0, 6.0), 'paint:#2a2d30', 'horn bracket')
    m.box((5.0, 24.0, 3.0), (9.0, 25.5, 4.0), 'paint:#2a2d30', 'loudhailer')
    m.g('hydraulic power pack')
    HPU = 'paint:#33506e'
    m.box((17.0, 2.0, 2.0), (30.0, 9.0, 11.0), HPU, 'HPU oil tank')
    m.box((18.0, 4.0, 1.9), (21.0, 6.0, 2.0), 'glass', 'tank level glass')
    m.box((18.2, 4.8, 1.85), (20.8, 5.0, 1.9), 'paint:#c08020', 'oil level')
    m.cyl((22.0, 9.0, 6.5), 2.4, 5.6, 'y', 'paint:#2f5a40', 'pump motor 45 KW')
    m.cyl((22.0, 14.6, 6.5), 2.0, 0.6, 'y', 'mesh', 'motor fan cover')
    m.box((20.0, 9.0, 3.4), (24.0, 10.2, 4.0), 'plate:PUMP|45KW 480V', 'motor plate', faces=('north',))
    for x in (26.0, 28.6):
        m.cyl((x, 9.0, 4.0), 1.1, 9.0, 'y', 'paint:#c03020', 'accumulator')
        m.cyl((x, 18.0, 4.0), 0.4, 0.8, 'y', 'metal:brass', 'charging valve')
    m.box((25.0, 9.0, 7.0), (30.0, 11.4, 10.6), 'paint:#2a2d30', 'valve block')
    for x in (26.0, 27.6, 29.2):
        m.cyl((x, 11.4, 8.8), 0.4, 1.2, 'y', 'paint:ral1023', 'solenoid valve')
    m.cyl((25.4, 12.6, 6.9), 0.8, 0.2, 'z', 'gauge', 'pressure gauge')
    m.hv_box(23.5, 3.0, 11.0, 'south')
    m.box((17.5, 9.4, 1.9), (24.0, 11.4, 2.0), 'danger:DANGER|480V', 'HPU danger', faces=('north',))
    m.g('span cylinders + hoses')
    for z in (13.0, 15.0):
        m.cyl((17.0, 4.0, z), 0.3, 13.0, 'x', 'cable:101010', 'hydraulic hose')
    m.cyl((30.0, 2.0, 14.0), 1.2, 14.0, 'y', 'paint:ral1023', 'span lift cylinder')
    m.cyl((30.0, 16.0, 14.0), 0.6, 10.0, 'y', 'metal:steel', 'cylinder rod')
    m.box((28.5, 26.0, 12.5), (31.5, 27.5, 15.5), 'paint:#2a2d30', 'rod eye')
    m.box((28.0, 2.0, 12.0), (32.0, 3.0, 16.0), STEEL, 'cylinder trunnion')
    m.g('life-saving')
    m.box((-14.0, 2.0, 4.0), (-13.0, 12.0, 5.0), 'paint:#3a3e44', 'lifebuoy post')
    m.cyl((-13.5, 8.0, 3.4), 1.8, 0.6, 'z', 'paint:#e05020', 'lifebuoy')
    m.cyl((-13.5, 8.0, 3.2), 1.0, 1.0, 'z', 'paint:#3a3e44', 'lifebuoy hole')
    m.box((-15.0, 12.0, 4.0), (-11.0, 14.0, 4.2), 'label:LIFEBUOY', 'lifebuoy sign', faces=('north',))
    m.handrail([(-15.6, 1.0), (-1.0, 1.0)], 2.0, h=3.0)
    m.g('supply')
    m.terminal_12v(8.0, 4.0, 12.0, 'south')
    m.box((3.0, 4.0, 12.0), (6.0, 7.0, 12.1), 'warn', 'warning triangle', faces=('south',))

    sf = m.surface('north', face=1.45, title='LINK SPAN')
    screen_lines(m, sf, 6.0, 19.6, 9.0, h=0.72, gap=0.92)
    for (cid, y, lab) in (('g_span', 18.2, 'SPAN %'), ('g_bar', 15.4, 'HYD BAR'), ('g_deck', 12.6, 'DECK %')):
        gauge(m, sf, cid, 12.8, y, lab, 2.4)
    for i, (cid, col, lab) in enumerate((('l_pwr', '30e060', '12V'), ('l_main', '30e060', 'MAINS'), ('l_up', 'f0f0f0', 'SPAN UP'), ('l_down', 'f0f0f0', 'SPAN DOWN'),
                                         ('l_seat', '30e060', 'SEATED'), ('l_in', '40a0ff', 'FERRY IN'), ('l_door', 'ffb020', 'DOOR OPEN'), ('l_sail', '40a0ff', 'AT SEA'),
                                         ('l_pump', '30e060', 'PUMP'), ('l_flt', 'ff3030', 'FAULT'), ('l_hold', 'ff2020', 'SIGNALS HELD'))):
        lamp(m, sf, cid, 1.9 + (i % 6) * 1.65, 13.8 - (i // 6) * 1.0, col, lab, 0.55)
    sel(m, sf, 'k_en', 2.2, 10.4, 'ENABLE', ['OFF', 'ON'], 1.3, t='key')
    sel(m, sf, 's_route', 4.6, 10.4, 'ROUTE', ['1', '2', '3', '4', '5', '6', '7', '8'], 1.3)
    sel(m, sf, 's_what', 7.0, 10.4, 'LOAD', ['WAGONS', 'ALL'], 1.3)
    page_knob(m, sf, 9.4, 10.4, ['OVERVIEW', 'DECK', 'VOYAGE', 'EVENTS'], 1.3)
    push(m, sf, 'raise', 2.2, 7.8, 'f0f0f0', 'SPAN RAISE', 1.2)
    push(m, sf, 'lower', 4.2, 7.8, 'f0f0f0', 'SPAN LOWER', 1.2)
    push(m, sf, 'door', 6.2, 7.8, 'e0a020', 'STERN DOOR', 1.2)
    push(m, sf, 'load', 8.2, 7.8, '30c040', 'LOAD', 1.2)
    push(m, sf, 'unload', 10.2, 7.8, '2050d0', 'UNLOAD', 1.2)
    push(m, sf, 'depart', 2.2, 5.8, '30c040', 'DEPART', 1.3)
    push(m, sf, 'horn', 4.2, 5.8, '303438', 'HORN', 1.1)
    push(m, sf, 'ack', 6.2, 5.8, 'f0d020', 'ACK', 1.1)
    push(m, sf, 'test', 8.2, 5.8, 'f0f0f0', 'LAMP TEST', 0.9)
    mush(m, sf, 'm_estop', 12.6, 6.2, 'E-STOP', 2.2)
    m.save(item_gui=0.28)
    return m


def ship():
    """the train ferry at 1/6 scale (1 px = 37.5 cm): keel y=0, main (train) deck y=6.4, bow north (z=-16), stern z=32"""
    m = Model('train_ferry_ship')
    HULL, RED, WHITE = 'paint:#1c2c4a', 'paint:#9a2a20', 'paint:#ecebe4'
    m.g('hull')
    m.box((0.5, 0, -10), (15.5, 3.0, 32), RED, 'bottom (antifouling)')
    m.box((0, 3.0, -10), (16, 6.4, 32), HULL, 'topsides')
    m.box((0, 2.9, -10), (16, 3.1, 32), 'paint:#ecebe4', 'boot top line', faces=('east', 'west'))
    for (z0, z1, w) in ((-14, -10, 1.6), (-16, -14, 4.0)):
        m.box((w, 1.0, z0), (16 - w, 6.4, z1), HULL, 'bow section')
        m.box((w + 0.4, 0.4, z0), (16 - w - 0.4, 3.0, z1), RED, 'bow forefoot')
    for side in (-1, 1):
        x = 8 + side * 6.4
        m.box((x - 1.2, 1.4, -14.2), (x + 1.2, 6.4, -9.8), HULL, 'bow flare', rot={'axis': 'y', 'angle': -22.5 * side, 'origin': [x, 3.9, -12]})
    m.box((3.0, 6.4, -16.0), (13.0, 7.2, -10.0), WHITE, 'forecastle deck')
    m.box((7.4, 3.0, -16.0), (8.6, 6.4, -15.6), 'paint:#2a2d30', 'stem bar')
    for z in range(-8, 32, 3):
        for x in (-0.05, 16.0):
            m.box((x, 4.4, z), (x + 0.05, 5.0, z + 0.6), 'glass', 'porthole')
    m.box((0, 6.4, -10), (16, 6.6, 32), 'paint:#5a5e62', 'train deck plating', faces=('up',))
    m.box((-0.2, 6.4, -10), (0.4, 8.0, 32), WHITE, 'bulwark')
    m.box((15.6, 6.4, -10), (16.2, 8.0, 32), WHITE, 'bulwark')
    m.box((2, 0.0, 30.0), (14, 1.0, 32.0), RED, 'stern skeg')
    for x in (4.0, 12.0):
        m.cyl((x, 0.8, 31.2), 1.4, 0.4, 'z', 'metal:brass', 'propeller')
    m.box((7.6, 0.4, 30.8), (8.4, 3.2, 32.0), 'paint:#2a2d30', 'rudder')
    m.box((0.6, 6.0, -9.0), (15.4, 6.4, -8.6), 'paint:ral1023', 'deck edge mark')
    m.box((3.0, 4.2, -9.95), (13.0, 5.6, -10.05), 'label:PRIDE TRAIN FERRY', 'hull name', faces=('north',))
    m.box((0.0, 4.6, 20.0), (-0.05, 5.6, 28.0), 'label:RAIL FERRY', 'hull side name', faces=('west',))
    m.g('train deck rails')
    for cx in (3.6, 8.0, 12.4):
        for d in (-0.9, 0.9):
            m.box((cx + d - 0.15, 6.6, -6.0), (cx + d + 0.15, 7.0, 32.0), STEEL, 'deck rail')
        for z in range(-5, 32, 4):
            m.box((cx - 1.2, 6.6, z), (cx + 1.2, 6.7, z + 0.4), 'paint:#3a3e44', 'rail clamp')
    for z in range(-4, 32, 6):
        for x in (1.0, 15.0):
            m.box((x - 0.3, 6.6, z), (x + 0.3, 7.0, z + 0.6), 'paint:ral1023', 'lashing pot')
    m.box((5.0, 6.6, -6.0), (6.6, 7.6, -5.0), 'paint:ral3000', 'buffer stop')
    m.box((9.4, 6.6, -6.0), (11.0, 7.6, -5.0), 'paint:ral3000', 'buffer stop')
    m.box((0.8, 6.6, -6.0), (2.4, 7.6, -5.0), 'paint:ral3000', 'buffer stop')
    m.box((13.6, 6.6, -6.0), (15.2, 7.6, -5.0), 'paint:ral3000', 'buffer stop')
    m.g('superstructure')
    m.box((1.0, 7.2, -15.0), (15.0, 13.0, -7.0), WHITE, 'accommodation block')
    for z in (-14.0, -12.0, -10.0, -8.4):
        for x in (0.95, 15.0):
            m.box((x, 10.0, z), (x + 0.05, 11.2, z + 1.0), 'glass', 'saloon window')
    m.box((2.0, 13.0, -14.0), (14.0, 16.4, -9.0), WHITE, 'wheelhouse')
    m.box((2.2, 14.6, -14.05), (13.8, 16.0, -14.0), 'glass', 'wheelhouse windows', faces=('north',))
    m.box((-0.5, 15.6, -13.0), (16.5, 16.0, -10.0), WHITE, 'bridge wings')
    m.box((1.6, 16.4, -14.4), (14.4, 16.8, -8.6), 'paint:#3a3e44', 'wheelhouse roof')
    m.box((7.4, 16.8, -12.0), (8.6, 24.0, -11.0), WHITE, 'radar mast')
    m.box((5.0, 23.0, -11.8), (11.0, 23.4, -11.2), 'paint:#2a2d30', 'radar scanner')
    m.box((7.0, 24.0, -11.8), (9.0, 24.6, -11.2), 'lamp:f8f8f0', 'masthead light')
    m.box((-0.3, 15.0, -11.8), (0.0, 15.6, -11.2), 'lamp:ff2020', 'port light')
    m.box((16.0, 15.0, -11.8), (16.3, 15.6, -11.2), 'lamp:20ff40', 'starboard light')
    m.g('funnels')
    for x in (1.6, 14.4):
        m.box((x - 1.2, 7.2, -4.0), (x + 1.2, 17.0, 0.0), WHITE, 'funnel casing')
        m.box((x - 1.25, 14.0, -4.05), (x + 1.25, 15.4, 0.05), 'paint:ral3000', 'funnel band')
        m.box((x - 1.2, 17.0, -4.0), (x + 1.2, 17.6, 0.0), 'paint:#141416', 'funnel top')
        m.cyl((x, 17.6, -2.0), 0.5, 1.0, 'y', 'paint:#2a2d30', 'exhaust pipe')
    m.g('lifeboats')
    for x in (-1.2, 15.6):
        m.box((x, 9.0, -6.6), (x + 1.6, 10.6, -1.0), 'paint:#e05020', 'lifeboat')
        m.box((x + 0.2, 10.6, -6.2), (x + 1.4, 11.0, -1.4), 'paint:#ecebe4', 'lifeboat canopy')
        for z in (-6.0, -1.6):
            m.box((x + 0.6, 7.2, z), (x + 1.0, 11.6, z + 0.4), 'paint:#3a3e44', 'davit')
    m.g('stern portal')
    for x in (0.0, 14.6):
        m.box((x, 6.4, 30.6), (x + 1.4, 16.0, 32.0), WHITE, 'stern portal post')
    m.box((0.0, 16.0, 30.6), (16.0, 17.4, 31.9), WHITE, 'stern portal beam')
    m.box((4.0, 16.2, 31.9), (12.0, 17.2, 31.95), 'label:STAND CLEAR', 'portal sign', faces=('south',))
    for x in (0.4, 15.2):
        m.cyl((x + 0.2, 7.2, 28.0), 0.6, 1.2, 'y', 'paint:#2a2d30', 'mooring bollard')
    m.handrail([(1.0, -15.0), (15.0, -15.0)], 13.0, h=1.2, r=0.15, mat='paint:#ecebe4')
    m.save(item_gui=0.2)
    return m


def ship_door():
    m = Model('train_ferry_door')
    m.g('stern door')
    m.box((1.4, 6.4, 31.4), (14.6, 15.8, 31.95), 'paint:#1c2c4a', 'stern door leaf')
    for y in (8.0, 10.0, 12.0, 14.0):
        m.box((1.4, y, 31.2), (14.6, y + 0.4, 31.4), 'paint:#2a3a58', 'door stiffener')
    for cx in (3.6, 8.0, 12.4):
        for d in (-0.9, 0.9):
            m.box((cx + d - 0.15, 6.4, 31.0), (cx + d + 0.15, 15.8, 31.2), STEEL, 'door rail')
    for x in (2.0, 13.4):
        m.box((x, 6.0, 31.4), (x + 0.6, 6.8, 32.0), STEEL, 'door hinge')
    m.box((1.4, 15.4, 31.95), (14.6, 15.8, 32.0), 'haz', 'door edge', faces=('south',))
    m.save(item_gui=0.4)
    return m


# ================================================================================================ #18 motorail
def motorail_ramp():
    m = Model('motorail_ramp')
    DECK = 'paint:#5c6066'
    m.g('approach + deck')
    m.box((-8.0, 0.0, -16.0), (24.0, 0.6, -6.0), 'paint:#3a3c40', 'approach apron')
    for x in (-7.0, 23.0):
        m.box((x - 0.6, 0.6, -16.0), (x + 0.6, 1.4, -6.0), 'paint:ral1023', 'apron kerb')
    m.box((-8.0, 2.0, -6.0), (24.0, 3.0, 32.0), DECK, 'ramp deck', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 2.5, -6.0]})
    m.box((-8.0, 3.0, -6.0), (24.0, 3.1, 32.0), 'paint:#6a6e74', 'diamond plate', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 2.5, -6.0]}, faces=('up',))
    for x in (-8.0, 23.0):
        m.box((x, 3.0, -6.0), (x + 1.0, 4.4, 32.0), 'paint:ral1023', 'ramp kerb', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 2.5, -6.0]})
    m.g('rear platform')
    m.box((-8.0, 12.0, 22.0), (24.0, 13.0, 29.0), DECK, 'level platform')
    m.box((-8.0, 13.0, 22.0), (24.0, 13.1, 29.0), 'paint:#6a6e74', 'platform plate', faces=('up',))
    m.box((-8.0, 12.5, 21.8), (24.0, 13.1, 22.0), 'haz', 'platform edge', faces=('north',))
    m.g('bridge plate')
    m.box((-4.0, 12.6, 29.0), (20.0, 13.2, 32.0), 'paint:#8a8e94', 'bridge plate')
    for x in (-3.0, 6.0, 14.0, 19.0):
        m.box((x, 12.4, 28.6), (x + 1.0, 13.4, 29.4), STEEL, 'plate hinge')
    m.box((-4.0, 13.2, 31.6), (20.0, 13.3, 32.0), 'haz', 'plate lip', faces=('up',))
    m.g('legs + hydraulics')
    for x in (-6.0, 22.0):
        for z in (23.0, 28.0):
            m.box((x - 0.8, 0.0, z - 0.8), (x + 0.8, 12.0, z + 0.8), 'paint:#3a3e44', 'deck leg')
            m.box((x - 1.4, 0.0, z - 1.4), (x + 1.4, 0.6, z + 1.4), STEEL, 'leg foot')
        m.cyl((x, 0.6, 25.5), 0.9, 7.0, 'y', 'paint:ral1023', 'levelling cylinder')
        m.cyl((x, 7.6, 25.5), 0.45, 4.4, 'y', 'metal:steel', 'cylinder rod')
    for x in (-2.0, 8.0, 18.0):
        m.box((x - 0.5, 0.0, 10.0), (x + 0.5, 8.0, 11.0), 'paint:#3a3e44', 'deck trestle')
    m.box((-7.0, 10.6, 22.4), (23.0, 12.0, 23.4), 'paint:#3a3e44', 'cross girder')
    m.box((-7.0, 10.6, 27.6), (23.0, 12.0, 28.6), 'paint:#3a3e44', 'cross girder')
    m.g('power pack')
    m.box((2.0, 0.0, 13.0), (14.0, 5.0, 20.0), 'paint:#33506e', 'HPU under deck')
    m.cyl((5.0, 5.0, 16.5), 1.6, 3.0, 'y', 'paint:#2f5a40', 'pump motor')
    m.box((9.0, 5.0, 14.0), (13.0, 6.6, 17.0), 'paint:#2a2d30', 'valve block')
    m.box((2.2, 2.0, 12.9), (8.0, 4.0, 13.0), 'danger:DANGER|480V', 'HPU danger', faces=('north',))
    m.hv_box(11.0, 1.0, 20.0, 'south')
    for x in (4.0, 6.0):
        m.cyl((x, 5.0, 20.0), 0.25, 6.0, 'z', 'cable:101010', 'hydraulic hose')
    m.g('chocks + tie-downs')
    for x in (0.0, 14.0):
        for z in (23.0, 26.0):
            m.box((x, 13.1, z), (x + 2.0, 14.1, z + 1.0), 'paint:ral1023', 'wheel chock')
    for x in (-6.0, 22.0):
        for z in (23.0, 27.0):
            m.box((x - 0.3, 13.1, z - 0.3), (x + 0.3, 13.6, z + 0.3), STEEL, 'tie-down ring')
    m.handrail([(-8.0, 22.2), (-8.0, 29.0)], 13.1, h=3.0)
    m.handrail([(24.0, 22.2), (24.0, 29.0)], 13.1, h=3.0)
    m.g('signs + light')
    m.box((26.0, 0.0, -8.0), (27.0, 22.0, -7.0), GALV, 'sign + lamp post')
    m.box((24.0, 15.0, -8.1), (30.0, 19.0, -8.0), 'plate:MOTORAIL|LOADING', 'ramp sign', faces=('north',))
    m.box((25.0, 12.4, -8.1), (29.0, 14.6, -8.0), 'label:MAX 3.5T 5KM/H', 'limit sign', faces=('north',))
    m.box((24.0, 21.0, -9.0), (30.0, 22.0, -6.0), 'paint:#2a2d30', 'floodlight')
    m.box((24.2, 20.9, -8.8), (29.8, 21.0, -6.2), 'lamp:fff2d0', 'floodlight lens', faces=('down',))
    m.g('pedestal')
    PX0, PX1 = -15.6, -9.4
    m.box((PX0 - 0.4, 0.0, -8.4), (PX1 + 0.4, 0.8, -2.0), CONC, 'pedestal plinth')
    cabinet(m, PX0, PX1, 0.8, 20.0, -8.0, -3.0, CAB, 'pedestal')
    m.box((PX0 + 0.2, 13.8, -8.06), (PX1 - 0.2, 19.6, -8.02), 'screen:0a1410', 'screen glass')
    m.terminal_12v((PX0 + PX1) / 2, 4.0, -3.0, 'south')
    m.gland(PX0 + 1.0, 0.8, -5.0, 'down', r=0.35, cable='cable:2060c0')

    sf = m.surface('north', face=-8.1, title='MOTORAIL')
    cx = (PX0 + PX1) / 2
    screen_lines(m, sf, cx, 19.2, 5.6, h=0.6, gap=0.9)
    gauge(m, sf, 'g_plate', cx - 1.5, 12.2, 'PLATE %', 2.2)
    gauge(m, sf, 'g_level', cx + 1.5, 12.2, 'LEVEL', 2.2)
    for i, (cid, col, lab) in enumerate((('l_pwr', '30e060', '12V'), ('l_main', '30e060', 'MAINS'), ('l_plate', 'f0f0f0', 'PLATE DN'), ('l_up', 'f0f0f0', 'PLATE UP'),
                                         ('l_chock', 'ffb020', 'CHOCKS'), ('l_veh', '40a0ff', 'VEHICLE'), ('l_car', '40a0ff', 'WAGON'), ('l_busy', '30e060', 'PUMP'),
                                         ('l_flt', 'ff3030', 'FAULT'), ('l_iv', 'f0f0f0', 'IV LINK'))):
        lamp(m, sf, cid, PX0 + 0.9 + (i % 5) * 1.1, 10.2 - (i // 5) * 0.8, col, lab, 0.48)
    sel(m, sf, 'k_en', cx - 1.6, 7.6, 'ENABLE', ['OFF', 'ON'], 1.1, t='key')
    sel(m, sf, 's_page', cx + 1.6, 7.6, 'PAGE', ['OVERVIEW', 'WAGONS', 'EVENTS'], 1.1)
    push(m, sf, 'plate', cx - 2.2, 5.6, 'f0f0f0', 'PLATE', 1.0)
    push(m, sf, 'chock', cx - 0.75, 5.6, 'e0a020', 'CHOCKS', 1.0)
    push(m, sf, 'load', cx + 0.75, 5.6, '30c040', 'LOAD', 1.0)
    push(m, sf, 'unload', cx + 2.2, 5.6, '2050d0', 'UNLOAD', 1.0)
    push(m, sf, 'ack', cx - 2.0, 4.1, 'f0d020', 'ACK', 0.9)
    push(m, sf, 'test', cx - 0.6, 4.1, 'f0f0f0', 'TEST', 0.8)
    mush(m, sf, 'm_estop', cx + 1.4, 2.6, 'E-STOP', 1.7)
    m.save(item_gui=0.28)
    return m


def motorail_tarp():
    """a car under a tarpaulin, chained to the wagon deck (drawn x2: 1 px = 12.5 cm), front to the north"""
    m = Model('motorail_tarp')
    TARP = 'paint:#3c5a3a'
    m.g('wheels')
    for z in (-6.0, 18.0):
        for x in (1.2, 13.4):
            m.cyl((x, 2.6, z), 2.6, 1.4, 'x', 'rubber', 'tyre')
            m.cyl((x + (0.2 if x < 8 else -0.2), 2.6, z), 1.4, 1.0, 'x', 'metal:alu', 'wheel rim')
    m.g('tarpaulin')
    m.box((1.4, 2.6, -11.0), (14.6, 8.0, 27.0), TARP, 'tarp body')
    m.box((2.0, 8.0, -9.0), (14.0, 9.6, 24.0), TARP, 'tarp bonnet + boot')
    m.box((2.6, 9.6, -2.0), (13.4, 13.6, 18.0), TARP, 'tarp cabin')
    m.box((3.4, 13.6, 0.0), (12.6, 14.2, 16.0), TARP, 'tarp roof')
    for z in (-6.0, 4.0, 14.0, 22.0):
        m.box((1.3, 2.6, z), (14.7, 14.3 if 0 <= z <= 16 else 9.7, z + 0.5), 'paint:ral1023', 'tarp strap')
    m.box((6.0, 3.0, -11.05), (10.0, 4.4, -11.0), 'label:MOTORAIL', 'tag', faces=('north',))
    m.g('tie-downs')
    for z in (-8.0, 20.0):
        for x in (0.4, 15.0):
            m.box((x, 0.0, z), (x + 0.6, 3.0, z + 0.6), 'paint:#5a5e62', 'tie-down chain')
    for z in (-9.0, 25.0):
        m.box((2.0, 0.0, z), (14.0, 1.0, z + 1.5), 'paint:ral1023', 'wheel chock beam')
    m.save(item_gui=0.3)
    return m


# ================================================================================================ #19 garden railway
def track_sleeper_rails(m, straight=True):
    if straight:
        m.g('sleepers')
        for i in range(5):
            z = 1.6 + i * 3.2
            m.box((3.0, 0.0, z - 0.7), (13.0, 1.0, z + 0.7), 'paint:#3c3a36', 'plastic sleeper')
            for x in (6.5, 9.5):
                m.bolt(x, 1.0, z, 'up', 0.35)
        m.g('rails')
        for x in (6.5, 9.5):
            m.box((x - 0.5, 1.0, 0), (x + 0.5, 1.4, 16), 'metal:alu', 'rail foot')
            m.box((x - 0.15, 1.4, 0), (x + 0.15, 2.9, 16), 'metal:alu', 'rail web')
            m.box((x - 0.35, 2.9, 0), (x + 0.35, 3.5, 16), 'metal:alu', 'rail head')
            for z in (0.0, 15.6):
                m.box((x - 0.45, 1.6, z), (x + 0.45, 2.6, z + 0.4), STEEL, 'fishplate')
        m.box((0, 0, 0), (16, 0.2, 16), 'paint:#6a7a52', 'ballast / lawn edge', faces=('up',))
    else:
        # quarter circle about the SE corner (16, 16), centreline radius 8
        m.g('sleepers')
        for i in range(5):
            a = math.radians(180 + 9 + i * 18)               # from the south side (x=8,z=16 at a=180) round to the east side
            r = 8.0
            cx, cz = 16 + r * math.cos(a), 16 + r * math.sin(a)
            phi = math.degrees(a)                              # radial direction
            bar(m, cx, cz, phi, 10.0, 1.4, 0.0, 1.0, 'paint:#3c3a36', 'plastic sleeper')
        m.g('rails')
        for r in (6.5, 9.5):
            for i in range(5):
                a = math.radians(180 + 9 + i * 18)
                cx, cz = 16 + r * math.cos(a), 16 + r * math.sin(a)
                tang = math.degrees(a) + 90
                seg = r * math.radians(18) + 0.3
                bar(m, cx, cz, tang, seg, 1.0, 1.0, 1.4, 'metal:alu', 'rail foot')
                bar(m, cx, cz, tang, seg, 0.3, 1.4, 2.9, 'metal:alu', 'rail web')
                bar(m, cx, cz, tang, seg, 0.7, 2.9, 3.5, 'metal:alu', 'rail head')
        m.box((0, 0, 0), (16, 0.2, 16), 'paint:#6a7a52', 'ballast / lawn edge', faces=('up',))


def garden_track():
    m = Model('garden_track', block='garden_track')
    track_sleeper_rails(m, True)
    m.save(item_gui=0.7)
    m2 = Model('garden_track_curve', block='garden_track')
    track_sleeper_rails(m2, False)
    m2.save(item_gui=0.7)
    return m, m2


def garden_station():
    m = Model('garden_station')
    BRICK, WOOD = 'paint:#8a4a32', 'wood'
    m.g('platform')
    m.box((0, 0, 0), (16, 3.6, 16), BRICK, 'platform wall')
    m.box((-0.2, 3.6, -0.4), (16.2, 4.2, 16), 'paint:#c8c0b0', 'coping stones')
    m.box((-0.2, 4.15, -0.4), (16.2, 4.25, 0.4), 'paint:ral9010', 'platform edge line', faces=('up',))
    for x in range(1, 16, 3):
        for y in (0.9, 2.3):
            m.box((x, y, -0.05), (x + 2.6, y + 0.1, 0), 'paint:#c8c0b0', 'mortar course', faces=('north',))
    m.g('pedestal')
    cabinet(m, 9.6, 15.4, 4.2, 17.6, 1.0, 4.4, 'paint:#2f5a40', "station master's pedestal", hinge='west')
    m.box((9.8, 13.6, 0.92), (15.2, 17.2, 0.96), 'screen:0a1410', 'screen glass')
    m.box((10.0, 17.15, 0.85), (15.0, 17.55, 0.9), 'label:STATION', 'pedestal label', faces=('north',))
    m.terminal_12v(12.5, 5.2, 4.4, 'south')
    m.g('starter signal')
    m.box((0.6, 4.2, 1.6), (1.6, 25.0, 2.6), 'paint:#ecebe4', 'signal post')
    m.box((0.2, 4.2, 1.2), (2.0, 5.0, 3.0), CONC, 'post footing')
    m.box((-0.6, 20.0, 1.0), (2.8, 26.0, 1.6), 'paint:#141416', 'signal head')
    m.box((-0.8, 25.2, 0.4), (3.0, 25.6, 1.6), 'paint:#141416', 'lamp hood')
    m.box((-0.8, 22.4, 0.4), (3.0, 22.8, 1.6), 'paint:#141416', 'lamp hood')
    m.box((-1.0, 18.0, 1.0), (3.2, 19.6, 1.2), 'paint:ral9010', 'signal plate')
    m.box((-0.9, 18.1, 0.95), (3.1, 19.5, 1.0), 'label:S1', 'signal number', faces=('north',))
    m.cyl((1.1, 26.0, 2.1), 0.5, 1.6, 'y', 'paint:#ecebe4', 'post finial')
    m.g('bell')
    m.box((4.0, 13.0, 1.4), (4.6, 17.0, 2.0), 'paint:#141416', 'bell bracket')
    m.box((4.0, 16.4, 0.6), (4.6, 17.0, 2.0), 'paint:#141416', 'bell arm')
    m.cyl((4.3, 14.6, 0.6), 0.9, 1.8, 'y', 'metal:brass', 'station bell')
    m.cyl((4.3, 14.0, 0.6), 0.2, 0.6, 'y', 'metal:brass', 'clapper')
    m.g('lamp + name board')
    m.cyl((7.0, 4.2, 13.0), 0.35, 16.0, 'y', 'paint:#2a2d30', 'lamp post')
    m.box((6.0, 20.2, 12.0), (8.0, 22.2, 14.0), 'paint:#2a2d30', 'lantern')
    m.box((6.2, 20.4, 11.95), (7.8, 22.0, 14.05), 'lamp:fff2c0', 'lantern glass')
    m.box((6.6, 22.2, 12.6), (7.4, 22.8, 13.4), 'paint:#2a2d30', 'lantern cap')
    m.box((1.0, 8.0, 14.6), (1.6, 13.0, 15.2), WOOD, 'board leg')
    m.box((12.4, 8.0, 14.6), (13.0, 13.0, 15.2), WOOD, 'board leg')
    m.box((0.4, 13.0, 14.6), (13.6, 15.6, 15.0), 'paint:ral9010', 'name board')
    m.box((0.6, 13.2, 14.55), (13.4, 15.4, 14.6), 'label:GARDEN HALT', 'name board face', faces=('north',))
    m.g('bench + flowers')
    m.box((2.0, 4.2, 8.0), (6.0, 6.2, 10.0), WOOD, 'bench seat')
    m.box((2.0, 6.2, 9.6), (6.0, 8.4, 10.0), WOOD, 'bench back')
    for x in (2.2, 5.4):
        m.box((x, 4.2, 8.2), (x + 0.4, 6.0, 9.8), 'paint:#2a2d30', 'bench end')
    m.box((13.0, 4.2, 9.0), (15.6, 6.0, 11.6), 'paint:#6a4028', 'flower tub')
    for (x, z, c) in ((13.6, 9.6, '#e04040'), (14.6, 10.4, '#f0d020'), (14.0, 11.0, '#c040c0'), (15.0, 9.4, '#f08020')):
        m.box((x, 6.0, z), (x + 0.6, 7.0, z + 0.6), 'paint:' + c, 'flower')
    m.g('fence')
    for x in (0.4, 4.4, 8.4, 12.4, 15.6):
        m.box((x - 0.2, 4.2, 15.4), (x + 0.2, 7.6, 15.8), 'paint:ral9010', 'fence post')
    m.box((0.2, 6.6, 15.45), (15.8, 7.0, 15.75), 'paint:ral9010', 'fence rail')
    m.box((0.2, 5.2, 15.45), (15.8, 5.6, 15.75), 'paint:ral9010', 'fence rail')
    for x in range(1, 16, 2):
        m.box((x - 0.15, 4.25, 15.5), (x + 0.15, 7.2, 15.7), 'paint:ral9010', 'fence paling')
    m.g('clock + timetable')
    m.box((6.8, 17.0, 12.8), (7.2, 18.4, 13.2), 'paint:#2a2d30', 'clock bracket')
    m.cyl((7.0, 18.6, 12.4), 1.0, 0.4, 'z', 'gauge', 'platform clock')
    m.cyl((7.0, 18.6, 12.8), 1.1, 0.4, 'z', 'paint:#2a2d30', 'clock case')
    m.box((9.8, 6.0, 13.6), (13.4, 10.0, 14.0), 'paint:#2f5a40', 'timetable case')
    m.box((10.0, 6.2, 13.55), (13.2, 9.8, 13.6), 'label:TRAINS|EVERY|10 MIN', 'timetable', faces=('north',))
    m.box((10.4, 4.2, 13.7), (10.8, 6.0, 13.9), 'paint:#2a2d30', 'timetable leg')
    m.box((12.4, 4.2, 13.7), (12.8, 6.0, 13.9), 'paint:#2a2d30', 'timetable leg')
    m.g('platform furniture')
    m.box((2.0, 4.2, 4.0), (4.0, 6.6, 6.0), 'paint:#2f5a40', 'ticket box')
    m.box((2.4, 6.6, 4.4), (3.6, 6.8, 5.6), 'paint:#141416', 'ticket slot')
    m.box((2.2, 5.0, 3.95), (3.8, 5.8, 4.0), 'label:FARES', 'ticket box label', faces=('north',))
    for (x, z) in ((5.0, 12.0), (5.6, 13.4)):
        m.cyl((x, 4.2, z), 0.7, 2.0, 'y', 'metal:alu', 'milk churn')
        m.cyl((x, 6.2, z), 0.4, 0.4, 'y', 'metal:alu', 'churn lid')
    m.box((8.4, 4.8, 5.0), (12.0, 5.2, 7.4), 'wood', 'luggage trolley deck')
    m.box((8.4, 5.2, 5.0), (8.8, 7.4, 7.4), 'paint:#2a2d30', 'trolley handle')
    for z in (5.4, 7.0):
        m.cyl((11.4, 4.4, z), 0.4, 0.3, 'x', 'rubber', 'trolley wheel')
    m.cyl((14.6, 4.2, 6.4), 0.6, 1.8, 'y', 'paint:#2a2d30', 'litter bin')
    m.box((-0.1, 0.0, 0.0), (0.0, 4.2, 16.0), 'paint:#6a3a24', 'end wall', faces=('west',))
    m.box((0.4, 4.25, 0.6), (15.6, 4.3, 1.0), 'paint:#d8d0a0', 'tactile strip', faces=('up',))
    m.box((6.0, 21.0, 12.8), (8.0, 21.4, 13.2), 'paint:#2a2d30', 'basket bracket')
    m.cyl((5.4, 19.6, 13.0), 0.7, 1.0, 'y', 'paint:#4a7a3a', 'hanging basket')

    sf = m.surface('north', face=0.9, title='GARDEN STATION')
    screen_lines(m, sf, 12.5, 16.8, 5.2, h=0.5, gap=0.62)
    gauge(m, sf, 'g_spd', 11.2, 11.8, 'M/S', 2.0)
    gauge(m, sf, 'g_dwell', 13.8, 11.8, 'DWELL', 2.0)
    for i, (cid, col, lab) in enumerate((('l_pwr', '30e060', '12V'), ('l_in', 'f0f0f0', 'TRAIN IN'), ('l_hold', 'ffb020', 'HOLD'),
                                         ('l_estop', 'ff2020', 'E-STOP'), ('l_lights', 'fff0c0', 'LIGHTS'), ('l_auto', '40a0ff', 'AUTO'))):
        lamp(m, sf, cid, 10.6 + (i % 3) * 1.9, 10.0 - (i // 3) * 0.7, col, lab, 0.45)
    sel(m, sf, 's_mode', 10.3, 7.9, 'MODE', ['MANUAL', 'AUTO'], 0.95)
    sel(m, sf, 's_dwell', 11.7, 7.9, 'DWELL S', ['10', '20', '30', '60'], 0.95)
    sel(m, sf, 's_vline', 13.1, 7.9, 'LINE M/S', ['1', '1.5', '2', '3'], 0.95)
    sel(m, sf, 's_page', 14.5, 7.9, 'PAGE', ['OVERVIEW', 'TRAINS', 'TIMES', 'EVENTS'], 0.95)
    push(m, sf, 'dispatch', 10.5, 6.2, '30c040', 'DISPATCH', 1.1)
    push(m, sf, 'hold', 12.0, 6.2, 'e0a020', 'HOLD', 0.9)
    push(m, sf, 'bell', 13.3, 6.2, 'c0a040', 'BELL', 0.9)
    push(m, sf, 'reset', 14.6, 6.2, '2050d0', 'RESET', 0.8)
    push(m, sf, 'test', 14.6, 5.0, 'f0f0f0', 'TEST', 0.7)
    mush(m, sf, 'm_estop', 11.8, 4.9, 'STOP', 1.3)
    sig = m.surface('north', face=0.95, title='STARTER')
    lamp(m, sig, 'sig_r', 1.1, 24.0, 'ff2020', 'DANGER', 1.5)
    lamp(m, sig, 'sig_g', 1.1, 21.2, '30ff60', 'CLEAR', 1.5)
    m.save(item_gui=0.55)
    return m


def wheelset(m, z, x0=4.6, x1=11.4, r=1.6, name='wheel'):
    for x in (x0, x1 - 0.8):
        m.cyl((x, r, z), r, 0.8, 'x', 'paint:#2a2d30', name)
        m.cyl((x + (-0.1 if x < 8 else 0.1), r, z), r * 0.45, 1.0, 'x', STEEL, 'axle box')
    m.cyl((x0, r, z), 0.25, x1 - x0, 'x', STEEL, 'axle')


def garden_loco():
    """0-4-0 tank engine for 7 1/4 inch gauge (1 px = 6.25 cm, real size), facing north, rail top y=0"""
    m = Model('garden_loco')
    GREEN, RED, BLACK = 'paint:#1f5a32', 'paint:ral3000', 'paint:#1a1a1c'
    m.g('wheels + frames')
    for z in (2.0, 9.0):
        wheelset(m, z, 5.0, 11.0, 1.7)
    m.box((5.6, 1.2, -1.6), (6.0, 4.4, 14.6), BLACK, 'frame plate')
    m.box((10.0, 1.2, -1.6), (10.4, 4.4, 14.6), BLACK, 'frame plate')
    m.box((4.4, 1.8, -2.4), (11.6, 4.4, -1.6), RED, 'front buffer beam')
    m.box((4.4, 1.8, 14.6), (11.6, 4.4, 15.4), RED, 'rear buffer beam')
    for x in (5.2, 10.8):
        m.cyl((x, 3.0, -3.2), 0.5, 0.8, 'z', STEEL, 'buffer')
        m.cyl((x, 3.0, 15.4), 0.5, 0.8, 'z', STEEL, 'buffer')
    m.box((7.6, 2.6, -2.8), (8.4, 3.4, -2.4), STEEL, 'coupling hook')
    m.box((7.6, 2.6, 15.4), (8.4, 3.4, 16.4), STEEL, 'drawbar')
    for x in (4.7, 11.3):
        m.box((x - 0.15, 1.5, 2.0), (x + 0.15, 1.9, 9.0), 'metal:steel', 'coupling rod')
    m.g('cylinders')
    for x in (4.0, 12.0):
        m.cyl((x, 3.0, -1.4), 1.2, 3.6, 'z', BLACK, 'cylinder')
        m.cyl((x, 3.0, 2.2), 0.25, 3.0, 'z', STEEL, 'piston rod')
    m.g('boiler')
    m.cyl((8.0, 8.0, -1.6), 3.0, 9.6, 'z', GREEN, 'boiler barrel')
    m.cyl((8.0, 8.0, -2.2), 3.1, 1.6, 'z', BLACK, 'smokebox')
    m.cyl((8.0, 8.0, -2.4), 2.4, 0.3, 'z', BLACK, 'smokebox door')
    m.box((7.8, 7.6, -2.6), (8.2, 8.4, -2.4), 'metal:brass', 'door handle')
    m.cyl((8.0, 10.8, -1.4), 0.8, 3.2, 'y', BLACK, 'chimney')
    m.cyl((8.0, 13.6, -1.4), 1.0, 0.6, 'y', 'metal:copper', 'chimney cap')
    m.cyl((8.0, 10.8, 3.0), 1.1, 1.4, 'y', 'metal:brass', 'steam dome')
    m.cyl((8.0, 10.8, 6.2), 0.35, 1.2, 'y', 'metal:brass', 'safety valve')
    m.cyl((8.6, 10.8, 6.2), 0.25, 1.0, 'y', 'metal:brass', 'whistle')
    for z in (0.0, 3.8, 7.0):
        m.cyl((8.0, 8.0, z), 3.08, 0.3, 'z', 'metal:brass', 'boiler band')
    m.box((6.6, 4.6, -3.0), (9.4, 6.0, -2.6), 'lamp:fff2c0', 'head lamp')
    m.g('side tanks')
    for x in (4.2, 10.0):
        m.box((x, 4.4, 0.4), (x + 1.8, 9.2, 7.6), GREEN, 'side tank')
        m.box((x, 9.2, 0.4), (x + 1.8, 9.4, 7.6), BLACK, 'tank top')
        m.box((x + (-0.05 if x < 8 else 1.8), 6.0, 2.0), (x + (0.0 if x < 8 else 1.85), 7.6, 6.0), 'plate:PRIDE', 'nameplate', faces=('west' if x < 8 else 'east',))
    m.box((4.0, 4.4, -1.6), (12.0, 4.6, 14.6), BLACK, 'running plate')
    m.g('cab')
    m.box((4.2, 4.6, 8.4), (11.8, 13.2, 14.4), GREEN, 'cab')
    m.box((4.6, 9.4, 8.35), (6.6, 11.6, 8.4), 'glass', 'spectacle', faces=('north',))
    m.box((9.4, 9.4, 8.35), (11.4, 11.6, 8.4), 'glass', 'spectacle', faces=('north',))
    m.box((3.8, 13.2, 8.0), (12.2, 13.8, 15.0), BLACK, 'cab roof')
    m.box((4.15, 6.0, 10.0), (4.2, 11.0, 13.6), BLACK, 'cab side opening', faces=('west',))
    m.box((11.8, 6.0, 10.0), (11.85, 11.0, 13.6), BLACK, 'cab side opening', faces=('east',))
    m.box((6.0, 4.6, 12.0), (10.0, 6.6, 14.4), BLACK, 'coal bunker')
    m.box((6.2, 6.6, 12.2), (9.8, 7.0, 14.2), 'paint:#101012', 'coal')
    m.cyl((8.0, 8.6, 8.6), 0.9, 0.3, 'z', 'gauge', 'pressure gauge')
    m.box((7.6, 8.8, 9.0), (8.4, 9.2, 10.4), 'metal:brass', 'regulator')
    m.save(item_gui=0.6)
    return m


def bogie_car(m, z0, z1):
    m.g('bogies')
    for z in (z0 + 2.5, z1 - 2.5):
        m.box((4.4, 1.0, z - 2.0), (11.6, 2.2, z + 2.0), 'paint:#2a2d30', 'bogie frame')
        wheelset(m, z - 1.2, 5.0, 11.0, 1.2)
        wheelset(m, z + 1.2, 5.0, 11.0, 1.2)
    m.box((4.6, 2.2, z0), (11.4, 3.4, z1), 'paint:#3a3e44', 'underframe')
    for z in (z0 - 0.6, z1):
        m.box((7.6, 2.4, z), (8.4, 3.0, z + 0.6), STEEL, 'coupling')


def garden_truck():
    m = Model('garden_truck')
    bogie_car(m, -1.0, 17.0)
    WOOD = 'wood'
    m.g('driving truck')
    m.box((4.0, 3.4, -1.0), (12.0, 3.8, 17.0), WOOD, 'deck boards')
    for x in (3.8, 11.8):
        m.box((x, 3.4, -1.0), (x + 0.4, 5.0, 17.0), 'paint:#2f5a40', 'side rail')
    m.box((6.6, 3.8, 1.0), (9.4, 6.2, 15.0), 'paint:#6a4028', 'seat box')
    m.box((6.4, 6.2, 1.0), (9.6, 6.8, 15.0), 'paint:#8a2a20', 'seat cushion')
    for x in (4.2, 10.6):
        m.box((x, 3.8, 1.0), (x + 1.2, 4.2, 15.0), 'paint:#3a3e44', 'footboard')
    m.box((7.0, 6.8, 0.8), (9.0, 7.4, 2.0), 'metal:brass', 'brake valve')
    m.cyl((8.0, 3.8, 0.2), 0.3, 4.0, 'y', 'metal:steel', 'regulator rod')
    m.box((7.4, 7.8, 0.0), (8.6, 8.2, 0.6), 'paint:ral3000', 'regulator handle')
    m.box((4.4, 3.8, 16.0), (11.6, 5.6, 16.6), 'paint:#2f5a40', 'backrest')
    m.box((5.0, 4.6, 16.6), (11.0, 5.4, 16.65), 'label:DRIVER', 'truck label', faces=('south',))
    m.save(item_gui=0.6)
    return m


def garden_car():
    m = Model('garden_car')
    bogie_car(m, -1.0, 17.0)
    m.g('sit-astride car')
    m.box((4.0, 3.4, -1.0), (12.0, 3.8, 17.0), 'paint:#8a2a20', 'body sole')
    for x in (3.6, 11.6):
        m.box((x, 3.4, -1.0), (x + 0.8, 5.6, 17.0), 'paint:#8a2a20', 'body side')
        m.box((x, 5.6, -1.0), (x + 0.8, 5.9, 17.0), 'metal:brass', 'side capping')
    m.box((6.4, 3.8, 0.0), (9.6, 6.4, 16.0), 'paint:#5a3a20', 'seat ridge')
    m.box((6.2, 6.4, 0.0), (9.8, 7.0, 16.0), 'paint:#2a3a6a', 'seat cushion')
    for x in (4.4, 10.2):
        m.box((x, 3.8, 0.0), (x + 1.4, 4.2, 16.0), 'paint:#3a3e44', 'footwell')
    for z in (4.0, 12.0):
        m.box((7.4, 7.0, z), (8.6, 8.6, z + 0.6), 'metal:steel', 'grab handle post')
        m.box((6.8, 8.6, z), (9.2, 9.0, z + 0.6), 'metal:steel', 'grab handle')
    m.box((4.6, 4.2, 17.0), (11.4, 5.2, 17.05), 'label:PRIDE GR', 'car label', faces=('south',))
    m.save(item_gui=0.6)
    return m


# ================================================================================================ #20 avalanche
def avalanche_gallery():
    m = Model('avalanche_gallery')
    m.g('back wall')
    m.box((0, 0, 9.0), (16, 32, 16), CONC, 'gallery wall')
    m.box((0, 0, 8.6), (16, 1.6, 9.0), CONC, 'wall footing')
    for x in (0.0, 15.8):
        m.box((x, 0, 8.8), (x + 0.2, 32, 16), 'paint:#3a3a38', 'expansion joint')
    for y in (8, 18):
        for x in (3, 8, 13):
            m.box((x - 0.4, y - 0.4, 8.7), (x + 0.4, y + 0.4, 9.0), 'metal:steel', 'rock anchor head')
    m.g('roof slab')
    m.box((0, 28, -16), (16, 32, 9.0), CONC, 'roof slab')
    m.box((0, 24, -15.95), (16, 28, -13.0), CONC, 'front downstand beam')
    m.box((0, 24.6, -16.0), (16, 26.0, -15.95), 'paint:ral9010', 'clearance stripe', faces=('north',))
    for x in (2.0, 14.0):
        m.box((x - 1.2, 22.0, -13.0), (x + 1.2, 28.0, -10.0), CONC, 'haunch')
    m.g('snow deflector')
    m.box((0, 30.0, 4.0), (16, 32.0, 16.0), 'paint:#7a7468', 'earth cover', rot={'axis': 'x', 'angle': -22.5, 'origin': [8, 31, 10]})
    m.box((0, 31.4, -16.0), (16, 32.0, 4.0), 'paint:#e8eef2', 'snow on roof', faces=('up', 'north', 'east', 'west'))
    m.g('drainage + services')
    for x in (4.0, 12.0):
        m.box((x - 0.5, 28.0, -16.0), (x + 0.5, 28.6, -14.6), 'paint:#3a3e44', 'roof scupper')
    m.box((0, 0, 7.0), (16, 1.4, 8.6), CONC, 'cable trough')
    m.box((0, 1.4, 7.0), (16, 1.6, 8.6), 'paint:#8a8a86', 'trough lid', faces=('up',))
    m.box((7.0, 20.0, 8.7), (9.0, 21.6, 9.0), 'paint:#2a2d30', 'gallery light')
    m.box((7.2, 20.0, 8.65), (8.8, 20.2, 8.7), 'lamp:fff2c0', 'light diffuser', faces=('north',))
    m.box((5.0, 12.0, 8.95), (11.0, 14.0, 9.0), 'plate:GALLERY|G7', 'gallery number', faces=('north',))
    m.box((1.0, 3.0, 8.95), (2.4, 6.0, 9.0), 'paint:ral9010', 'reflector post', faces=('north',))
    m.box((13.6, 3.0, 8.95), (15.0, 6.0, 9.0), 'paint:ral9010', 'reflector post', faces=('north',))
    m.box((1.2, 5.0, 8.9), (2.2, 5.6, 8.95), 'lamp:ffb020', 'reflector', faces=('north',))
    m.box((13.8, 5.0, 8.9), (14.8, 5.6, 8.95), 'lamp:ffb020', 'reflector', faces=('north',))
    m.g('formwork + weathering')
    for y in range(3, 28, 3):
        m.box((0.2, y, 8.95), (15.8, y + 0.1, 9.0), 'paint:#9a9890', 'formwork line', faces=('north',))
    for x in (2.0, 7.0, 12.0):
        m.box((x, 1.6, 8.9), (x + 0.6, 2.2, 9.0), 'paint:#202020', 'weep hole', faces=('north',))
        m.box((x - 0.2, 1.6, 8.95), (x + 1.0, 8.0, 9.0), 'paint:#8a8880', 'water stain', faces=('north',))
    m.g('roof structure')
    for x in (1.0, 5.5, 10.5, 15.0):
        m.box((x - 0.5, 26.4, -13.0), (x + 0.5, 28.0, 9.0), CONC, 'roof rib')
    m.box((0, 32.0 - 0.6, -16.0), (16, 32.0, -15.0), 'paint:#a8a6a0', 'roof parapet')
    m.g('snow nets on the slope above')
    for x in (2.0, 8.0, 14.0):
        m.box((x - 0.3, 26.0, 13.0), (x + 0.3, 32.0, 13.6), GALV, 'snow net post', rot={'axis': 'x', 'angle': 22.5, 'origin': [x, 26.0, 13.3]})
    for y in (28.0, 29.6, 31.2):
        m.box((0.4, y, 14.6), (15.6, y + 0.12, 14.8), 'cable:a0a4a8', 'snow net wire')
    m.g('drain + cables')
    m.cyl((15.0, 1.6, 8.4), 0.4, 26.4, 'y', 'paint:#3a3e44', 'downpipe')
    for y in (6.0, 14.0, 22.0):
        m.box((14.4, y, 8.2), (15.6, y + 0.4, 9.0), 'metal:steel', 'pipe clip')
    for y in (10.0, 16.0):
        m.box((3.0, y, 8.4), (3.6, y + 0.6, 9.0), 'paint:#2a2d30', 'cable hanger')
        m.box((3.0, y + 0.1, 7.6), (6.0, y + 0.5, 8.4), 'cable:202020', 'line cable')
    m.box((9.0, 6.0, 8.95), (14.0, 8.0, 9.0), 'warn', 'avalanche warning', faces=('north',))
    m.box((9.0, 4.4, 8.95), (14.0, 6.0, 9.0), 'label:AVALANCHE AREA', 'warning text', faces=('north',))
    m.save(item_gui=0.3)
    return m


def avalanche_detector():
    m = Model('avalanche_detector')
    m.g('foundation + mast')
    plinth(m, 2.0, 14.0, 3.0, 13.0, 1.2)
    m.box((7.0, 1.2, 9.0), (9.0, 32.0, 11.0), GALV, 'mast')
    for y in (8.0, 16.0, 24.0):
        m.box((6.8, y - 0.3, 8.8), (9.2, y + 0.3, 11.2), GALV, 'mast flange')
        m.bolt(7.0, y, 8.8, 'north', 0.35); m.bolt(9.0, y, 8.8, 'north', 0.35)
    for (x, z, ang) in ((4.0, 10.0, 22.5), (12.0, 10.0, -22.5)):
        m.box((x - 0.3, 1.2, z - 0.3), (x + 0.3, 12.0, z + 0.3), GALV, 'mast stay', rot={'axis': 'z', 'angle': ang, 'origin': [x, 1.2, z]})
    m.g('snow depth sensor')
    m.box((8.0, 29.0, -12.0), (9.0, 30.0, 9.0), GALV, 'sensor boom')
    m.box((7.0, 27.0, -13.0), (10.0, 29.0, -10.0), 'paint:#e8e4d0', 'ultrasonic sensor')
    m.cyl((8.5, 26.4, -11.5), 1.0, 0.6, 'y', 'paint:#2a2d30', 'transducer')
    m.box((8.2, 0.0, -12.0), (8.8, 8.0, -11.4), 'paint:#e05020', 'snow stake')
    for y in range(1, 8):
        m.box((8.1, y, -12.05), (8.9, y + 0.15, -11.95), 'paint:#141416', 'stake mark', faces=('north',))
    m.box((7.6, 0.0, -12.6), (9.4, 0.3, -10.8), 'paint:#8a8a86', 'reference plate')
    m.g('avalanche radar')
    m.box((9.0, 25.0, 9.4), (13.0, 26.0, 10.6), GALV, 'radar arm')
    m.cyl((13.6, 23.4, 10.0), 1.8, 3.6, 'y', 'paint:#ecebe4', 'radar radome')
    m.cyl((13.6, 27.0, 10.0), 1.2, 0.6, 'y', 'paint:#ecebe4', 'radome cap')
    m.box((12.2, 23.8, 8.0), (15.0, 25.2, 8.2), 'label:RADAR', 'radar label', faces=('north',))
    m.g('weather')
    m.box((3.0, 30.0, 9.6), (7.0, 30.6, 10.4), GALV, 'weather arm')
    for k in range(5):
        m.cyl((3.6, 26.0 + k * 0.6, 10.0), 1.1 - k * 0.08, 0.3, 'y', 'paint:#ecebe4', 'radiation shield plate')
    m.cyl((3.6, 29.0, 10.0), 0.15, 1.0, 'y', STEEL, 'shield rod')
    m.cyl((5.6, 30.6, 10.0), 0.15, 1.2, 'y', STEEL, 'anemometer spindle')
    for (dx, dz) in ((1.0, 0), (-0.5, 0.86), (-0.5, -0.86)):
        m.box((5.6 + dx - 0.25, 31.6, 10.0 + dz - 0.25), (5.6 + dx + 0.25, 32.0, 10.0 + dz + 0.25), 'paint:#2a2d30', 'anemometer cup')
    m.g('beacon')
    m.box((7.4, 30.6, 8.6), (8.6, 31.0, 9.0), 'paint:#2a2d30', 'beacon bracket')
    m.g('slide fence')
    for x in (-15.0, -8.0, 24.0, 31.0):
        m.box((x - 0.3, 0.0, -14.0), (x + 0.3, 10.0, -13.4), GALV, 'fence post')
    for y in (3.0, 5.5, 8.0):
        m.box((-15.0, y, -13.8), (-8.0, y + 0.12, -13.6), 'cable:c0c0c0', 'trip wire')
        m.box((24.0, y, -13.8), (31.0, y + 0.12, -13.6), 'cable:c0c0c0', 'trip wire')
    m.box((-8.6, 0.0, -13.0), (-7.4, 1.6, -11.8), 'paint:#3a3e44', 'geophone housing')
    m.box((-8.0, 0.0, -12.5), (2.0, 0.3, -12.2), 'cable:202020', 'sensor cable')
    m.g('gas exploder')
    m.box((26.0, 0.0, 14.0), (30.0, 3.0, 18.0), CONC, 'exploder base')
    m.box((27.0, 3.0, 15.0), (29.0, 14.0, 17.0), 'paint:#3a3e44', 'exploder tube')
    m.box((26.6, 14.0, 13.0), (29.4, 16.0, 17.4), 'paint:#3a3e44', 'exploder mouth', rot={'axis': 'x', 'angle': 22.5, 'origin': [28, 14, 16]})
    m.box((24.0, 0.0, 18.4), (30.0, 4.0, 22.0), 'paint:#8a8a2a', 'gas shelter')
    m.box((24.2, 1.6, 18.35), (29.8, 3.2, 18.4), 'danger:DANGER|EXPLOSIVE GAS', 'gas danger', faces=('north',))
    m.g('control cabinet')
    cabinet(m, 3.0, 13.0, 1.2, 17.0, 4.0, 9.0, CAB, 'control cabinet', hinge='west')
    m.box((3.4, 12.6, 3.92), (12.6, 16.6, 3.96), 'screen:0a1410', 'screen glass')
    m.box((4.0, 16.95, 3.85), (12.0, 17.45, 3.9), 'label:AVALANCHE DETECTOR', 'cabinet label', faces=('north',))
    m.terminal_12v(6.0, 3.0, 9.0, 'south')
    m.gland(10.0, 1.2, 7.0, 'down', r=0.4, cable='cable:2060c0', length=1.0)
    m.box((10.0, 4.0, 9.0), (12.6, 6.6, 9.1), 'danger:DANGER|240V', 'heater danger', faces=('south',))
    m.cyl((12.0, 17.5, 7.0), 0.35, 2.0, 'y', 'porcelain', 'surge arrester')
    m.g('cable runs')
    m.box((8.6, 17.4, 9.0), (9.0, 28.0, 9.3), 'cable:202020', 'sensor cable up the mast')
    for y in (19.0, 22.0, 25.0):
        m.box((8.5, y, 8.95), (9.1, y + 0.3, 9.4), 'metal:steel', 'cable tie')
    m.box((9.0, 25.2, 9.6), (9.4, 25.6, 10.4), 'cable:202020', 'radar cable')
    m.box((-6.0, 0.0, -11.0), (-4.0, 1.4, -9.0), 'paint:#3a3e44', 'junction pit lid')

    sf = m.surface('north', face=3.9, title='AVALANCHE')
    screen_lines(m, sf, 8.0, 16.2, 8.8, h=0.58, gap=0.7)
    for (cid, x, lab) in (('g_snow', 5.2, 'SNOW MM'), ('g_haz', 8.0, 'HAZARD'), ('g_temp', 10.8, 'AIR C')):
        gauge(m, sf, cid, x, 10.6, lab, 2.3)
    for i, (cid, col, lab) in enumerate((('l_pwr', '30e060', '12V'), ('l_heat', '30e060', 'HEATERS'), ('l_low', '30e060', 'LOW'), ('l_mod', 'ffd040', 'CONSID'),
                                         ('l_high', 'ff3030', 'HIGH'), ('l_trip', 'ff2020', 'TRIPPED'), ('l_obs', 'ff8020', 'OBSTRUCTED'), ('l_hold', 'ff2020', 'LINE HELD'),
                                         ('l_clear', '30e060', 'LINE CLEAR'), ('l_ice', '80c0ff', 'ICED'))):
        lamp(m, sf, cid, 4.2 + (i % 5) * 1.9, 8.4 - (i // 5) * 0.8, col, lab, 0.5)
    sel(m, sf, 'k_arm', 4.4, 5.9, 'ARMED', ['DISARMED', 'ARMED'], 1.2, t='key')
    sel(m, sf, 's_r', 6.6, 5.9, 'RANGE', ['16', '24', '32', '48'], 1.2)
    sel(m, sf, 's_thr', 8.8, 5.9, 'HOLD AT', ['CONSID', 'HIGH', 'V HIGH'], 1.2)
    sel(m, sf, 's_page', 11.2, 5.9, 'PAGE', ['OVERVIEW', 'SNOW', 'SENSORS', 'EVENTS'], 1.2)
    push(m, sf, 'clear', 4.2, 3.7, '30c040', 'LINE CLEAR', 1.0)
    push(m, sf, 'hold', 5.7, 3.7, 'd02020', 'HOLD LINE', 1.0)
    push(m, sf, 'blast', 7.2, 3.7, 'e06010', 'BLAST', 1.0)
    push(m, sf, 'trip', 8.7, 3.7, 'c0c0c0', 'TEST TRIP', 0.9)
    push(m, sf, 'ack', 10.2, 3.7, 'f0d020', 'ACK', 0.9)
    push(m, sf, 'test', 11.7, 3.7, 'f0f0f0', 'LAMP TEST', 0.8)
    bc = m.surface('north', face=8.55, title='BEACON')
    lamp(m, bc, 'l_beacon', 8.0, 30.8, 'ff9020', 'BEACON', 1.1, blink=True)
    m.save(item_gui=0.32)
    return m


# ================================================================================================ assets
def set_icon(d):
    d.rectangle((1, 9, 14, 10), fill=(150, 150, 156, 255))
    d.rectangle((2, 4, 9, 8), fill=(31, 90, 50, 255))
    d.rectangle((3, 2, 4, 4), fill=(26, 26, 28, 255))
    d.rectangle((8, 3, 11, 8), fill=(31, 90, 50, 255))
    d.rectangle((8, 2, 12, 3), fill=(26, 26, 28, 255))
    d.rectangle((1, 7, 2, 8), fill=(170, 30, 26, 255))
    for x in (4, 9):
        d.ellipse((x - 2, 8, x + 1, 11), fill=(40, 40, 44, 255))
    d.rectangle((12, 6, 15, 8), fill=(138, 42, 32, 255))
    d.ellipse((12, 8, 14, 10), fill=(40, 40, 44, 255))


def icon(name, draw):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    os.makedirs(os.path.join(ASSETS, 'textures/items'), exist_ok=True)
    img.save(os.path.join(ASSETS, 'textures/items', name + '.png'))
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/' + name}}, open(os.path.join(ASSETS, 'models/item', name + '.json'), 'w'), indent=1)


BLOCKS = {'rack_rail': 'Rack Rail (Abt)', 'rack_entry': 'Rack Entry Section', 'rack_panel': 'Rack Section Panel', 'ferry_dock': 'Train Ferry Dock',
          'motorail_ramp': 'Motorail Loading Ramp', 'garden_track': 'Garden Railway Track', 'garden_station': 'Garden Railway Station',
          'avalanche_gallery': 'Avalanche Gallery', 'avalanche_detector': 'Avalanche Detector'}
PARTS = ['train_ferry_ship', 'train_ferry_door', 'garden_loco', 'garden_truck', 'garden_car', 'motorail_tarp']

I, G, R, C = 'minecraft:iron_ingot', 'minecraft:glass_pane', 'minecraft:redstone', 'minecraft:comparator'
RECIPES = {
    'rack_rail': (['IBI', 'IBI', 'SSS'], {'I': I, 'B': 'minecraft:iron_bars', 'S': ('minecraft:stone', 0)}, 8),
    'rack_entry': (['IPI', 'IBI', 'SSS'], {'I': I, 'P': 'minecraft:piston', 'B': 'minecraft:iron_bars', 'S': ('minecraft:stone', 0)}, 2),
    'rack_panel': (['GCG', 'RKR', 'ISI'], {'G': G, 'C': C, 'R': R, 'K': 'minecraft:tripwire_hook', 'I': I, 'S': ('minecraft:stone', 0)}, 1),
    'ferry_dock': (['GCG', 'PRP', 'SBS'], {'G': G, 'C': C, 'P': 'minecraft:piston', 'R': 'minecraft:redstone_block', 'S': ('minecraft:stone', 0), 'B': 'minecraft:boat'}, 1),
    'motorail_ramp': (['  C', ' II', 'PII'], {'C': C, 'I': I, 'P': 'minecraft:piston'}, 1),
    'garden_track': (['I I', 'SSS', 'I I'], {'I': I, 'S': ('minecraft:wooden_slab', 0)}, 16),
    'garden_station': (['GCG', 'BRB', 'BBB'], {'G': G, 'C': C, 'R': R, 'B': ('minecraft:brick_block', None)}, 1),
    'avalanche_gallery': (['SSS', 'S  ', 'S  '], {'S': ('minecraft:stone', 0)}, 4),
    'avalanche_detector': (['ECE', 'GRG', 'ISI'], {'E': 'minecraft:ender_eye', 'C': C, 'G': G, 'R': R, 'I': I, 'S': ('minecraft:stone', 0)}, 1),
    'garden_train_set': (['  I', 'GII', 'WWW'], {'I': I, 'G': 'minecraft:gold_nugget', 'W': ('minecraft:wooden_slab', 0)}, 1),
}
ITEM_META = {'garden_train_set'}
LANG = {'tile.irextras.%s.name' % k: v for k, v in BLOCKS.items()}
LANG.update({'item.irextras.garden_train_set.name': 'Garden Train Set', 'entity.irextras.train_ferry.name': 'Train Ferry',
             'entity.irextras.garden_train.name': 'Garden Train'})

if __name__ == '__main__':
    counts = {}
    for fn in (rack_rail, rack_entry, rack_panel, ferry_dock, ship, ship_door, motorail_ramp, motorail_tarp, garden_station,
               garden_loco, garden_truck, garden_car, avalanche_gallery, avalanche_detector):
        mm = fn()
        counts[mm.name] = len(mm.boxes)
    for mm in garden_track():
        counts[mm.name] = len(mm.boxes)
    for n in BLOCKS:
        if n != 'garden_track': facing_states(n)
    rot = {'ns': ('garden_track', 0), 'ew': ('garden_track', 90), 'se': ('garden_track_curve', 0), 'sw': ('garden_track_curve', 90),
           'nw': ('garden_track_curve', 180), 'ne': ('garden_track_curve', 270)}
    tv = {}
    for f in ('north', 'east', 'south', 'west'):
        for sh, (mdl, y) in rot.items():
            tv['facing=%s,shape=%s' % (f, sh)] = {'model': 'irextras:' + mdl, **({'y': y} if y else {})}
    json.dump({'variants': tv}, open(os.path.join(ASSETS, 'blockstates', 'garden_track.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/garden_track'}, open(os.path.join(ASSETS, 'models/item', 'garden_track.json'), 'w'), indent=1)
    for n in PARTS:
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item', n + '.json'), 'w'), indent=1)
    icon('garden_train_set', set_icon)
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
            f.write(('' if have.endswith('\n') else '\n') + '\n# batch 6 #16-20 rack railway, train ferry, motorail, garden railway, avalanche protection\n' + '\n'.join(add) + '\n')
    print('batch 6d assets written')
    for k, v in counts.items(): print('  %-24s %d boxes' % (k, v))
