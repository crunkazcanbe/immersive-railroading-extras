#!/usr/bin/env python3
"""The depot (her ChatGPT list section 8 #51-62 + condition-based restrictions, #83 servicing, #84 crew): Depot Control
Office, Depot Stores, Tool Crib, Crew Booking-On Desk (three-block-wide furniture, built like the economy offices), and
the depot plant: Underfloor Wheel Lathe, Bogie Drop, Brake Test Rig, Fuelling and Sanding Point, Train Wash Plant (half
gantry - one each side of the track) and the Carriage Servicing Point. Models face north; controls by u = 16 - x.
Also writes blockstates, item models, the spare-part item textures, recipes and lang lines (depot + station blocks).
Live controls: see depot/Tile*.java (ids must match)."""
import json, os, random
from PIL import Image
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, led_board, desk, strip, buttons, hood, monitor, cupboard_shelf, X

SPOTS = {}


def lamp_row(m, sf, items, u0, v, step=1.3, size=0.6, label_dv=-0.55):
    """lamps with a label under each: items = (id, colour, label, blink)"""
    for i, it in enumerate(items):
        cid, col, lab = it[0], it[1], it[2]
        blink = len(it) > 3 and it[3]
        u = u0 + i * step
        m.ctl(sf, 'lamp', cid, u, v, w=size, h=size, col=col, label=lab, **({'blink': True} if blink else {}))
        m.ctl(sf, 'label', '', u, v + label_dv, w=0, h=0, col='e8e8e8', label=lab)


def push_row(m, sf, items, u0, v, step=1.3, size=0.95, label_dv=-0.8):
    for i, (cid, col, lab) in enumerate(items):
        u = u0 + i * step
        m.ctl(sf, 'push', cid, u, v, w=size, h=size, col=col, label=lab)
        m.ctl(sf, 'label', '', u, v + label_dv, w=0, h=0, col='e8e8e8', label=lab)


def cabinet(m, x0, x1, y0, y1, z0, z1, col='ral7035', door=None, name='cabinet'):
    """an enclosure with a door panel, hinges, handle and corner bolts on its north face"""
    m.box((x0, y0, z0), (x1, y1, z1), col, name)
    m.box((x0 + 0.3, y0 + 0.3, z0 - 0.15), (x1 - 0.3, y1 - 0.3, z0), door or col, name + ' door')
    for y in (y0 + 0.8, y1 - 1.6):
        m.box((x1 - 0.35, y, z0 - 0.3), (x1 - 0.05, y + 0.8, z0), 'metal:steel', 'hinge')
    m.box((x0 + 0.5, (y0 + y1) / 2 - 0.6, z0 - 0.45), (x0 + 0.8, (y0 + y1) / 2 + 0.6, z0 - 0.15), 'metal:steel', 'handle')
    for x in (x0 + 0.5, x1 - 0.5):
        for y in (y0 + 0.5, y1 - 0.5):
            m.bolt(x, y, z0 - 0.15, 'north', 0.3)


def mains_box(m, x, y, z, face='east'):
    """a rotary mains isolator with a DANGER plate and the armoured supply cable to the floor"""
    g = m.group
    m.g('mains isolator')
    if face == 'east':
        m.box((x, y, z - 1.4), (x + 1.4, y + 3.0, z + 1.4), 'paint:#3a3f44', 'isolator box', east='danger:400V')
        m.cyl((x + 1.4, y + 1.9, z), 0.5, 0.35, 'x', 'paint:#c82020', 'isolator handle')
        m.box((x + 1.75, y + 1.75, z - 0.9), (x + 1.95, y + 2.05, z + 0.9), 'paint:#f0c818', 'handle bar')
        m.cyl((x + 0.7, 0.0, z), 0.35, y, 'y', 'cable:202020', 'armoured mains cable')
    else:
        m.box((x - 1.4, y, z), (x + 1.4, y + 3.0, z + 1.4), 'paint:#3a3f44', 'isolator box', south='danger:400V')
        m.cyl((x, y + 1.9, z + 1.4), 0.5, 0.35, 'z', 'paint:#c82020', 'isolator handle')
        m.cyl((x, 0.0, z + 0.7), 0.35, y, 'y', 'cable:202020', 'armoured mains cable')
    m.g(g)


def estop_plate(m, sf, u, v, cid='m_estop'):
    m.ctl(sf, 'mushroom', cid, u, v, w=1.3, h=1.3, col='d02020', label='E-STOP')


# ========================================================================================================= Depot Office
def depot_office():
    m = Model('depot_office')
    back_wall(m, panel='#2f3d4a', plaster='#e4e7e2')
    m.g('job board')
    led_board(m, -13, 29, 14.6, 28.6)
    m.box((-6, 29.0, 14.7), (22, 31.2, 15.4), 'paint:#14324a', 'board header')
    m.box((-5.6, 29.2, 14.62), (21.6, 31.0, 14.7), 'plate:DEPOT CONTROL - JOB CARDS', 'board title')
    for cx in (-11.5, 27.5):
        m.cyl((cx, 30.1, 14.4), 0.95, 0.6, 'z', 'gauge:clock', 'depot clock', cap='gauge:clock')
    b = m.surface('north', face=14.42, title='JOB BOARD')
    m.ctl(b, 'digits', 'jhead', X(8), 27.5, w=40, h=1.3, col='ffd040', label='', fmt='%s')
    for r in range(1, 7):
        v = 25.4 - (r - 1) * 1.85
        m.ctl(b, 'lamp', 'j%ds' % r, X(-12.2), v, w=0.6, h=0.6, col='ffb020', label='')
        m.ctl(b, 'digits', 'j%d' % r, X(8.6), v, w=40, h=1.5, col='7cf29a', label='', fmt='%s')
    # left wing: filing cabinet + the exam plan console
    m.g('filing cabinet')
    m.box((-13.6, 0, 9.0), (-9.2, 12.0, 15.2), 'paint:#5d6a72', 'filing cabinet')
    for k in range(4):
        y0 = 0.5 + k * 2.85
        m.box((-13.3, y0, 8.85), (-9.5, y0 + 2.6, 9.0), 'paint:#6f7d86', 'cabinet drawer')
        m.box((-12.0, y0 + 1.6, 8.6), (-10.8, y0 + 1.9, 8.85), 'metal:steel', 'drawer pull')
        m.box((-12.2, y0 + 0.5, 8.82), (-10.6, y0 + 1.2, 8.85), 'label:JOB CARDS', 'drawer label')
    fc = m.surface('north', face=8.8, title='FILES')
    m.ctl(fc, 'zone', 'files', X(-11.4), 6.0, w=4.2, h=11.4, col='000000', label='')
    m.box((-13.4, 12.0, 10.0), (-12.0, 13.6, 13.0), 'paint:#2a5d8a', 'binder')
    m.box((-11.9, 12.0, 10.0), (-10.6, 13.4, 13.0), 'paint:#8a2a2a', 'binder')
    m.box((-10.5, 12.0, 10.0), (-9.4, 13.2, 13.0), 'paint:#2a8a4a', 'binder')
    m.g('plan console')
    m.box((-8.6, 0, 9.2), (-1.2, 8.4, 14.8), 'paint:#3a4652', 'plan console pedestal')
    m.box((-8.8, 8.4, 9.0), (-1.0, 8.8, 15.0), 'paint:#24303a', 'plan console top')
    m.box((-8.5, 8.8, 14.2), (-1.3, 10.6, 14.8), 'paint:#24303a', 'console upstand')
    m.box((-8.0, 9.2, 14.15), (-1.8, 10.2, 14.2), 'label:EXAM PLAN', 'plan title')
    for x in (-8.4, -1.4):
        m.bolt(x, 4.0, 9.2, 'north', 0.35)
    pc = m.surface('up', face=16 - 8.8, title='EXAM PLAN')
    for i, (cid, lab, pos) in enumerate((('s_km', 'KM', ['25', '50', '100', '200', 'OFF']), ('s_hrs', 'HOURS', ['12', '24', '48', '96', 'OFF']),
                                         ('s_days', 'DAYS', ['3', '7', '14', '28', 'OFF']), ('s_flt', 'FAULTS', ['3', '5', '10', '20', 'OFF']))):
        u = X(-7.6 + i * 1.75)
        m.ctl(pc, 'selector', cid, u, 10.6, w=1.25, h=1.25, col='202020', label=lab, pos=pos)
        m.ctl(pc, 'label', '', u, 9.6, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(pc, 'push', 'planset', X(-6.6), 13.0, w=1.0, h=1.0, col='1fa34a', label='PLAN')
    m.ctl(pc, 'label', '', X(-6.6), 12.1, w=0, h=0, col='e8e8e8', label='PLAN TYPE')
    m.ctl(pc, 'push', 'planall', X(-4.6), 13.0, w=1.0, h=1.0, col='2050d0', label='ALL')
    m.ctl(pc, 'label', '', X(-4.6), 12.1, w=0, h=0, col='e8e8e8', label='PLAN ALL')
    m.ctl(pc, 'selector', 's_page', X(-2.4), 13.0, w=1.3, h=1.3, col='202020', label='PAGE', pos=['JOBS', 'FLEET', 'UNIT', 'PLAN', 'ALLOC', 'COMPS', 'DLOAD', 'LOG'])
    m.ctl(pc, 'label', '', X(-2.4), 11.9, w=0, h=0, col='e8e8e8', label='SCREEN')
    # right wing: manuals cupboard + shelf, and the routing key box on the wall
    SPOTS['depot_office'] = cupboard_shelf(m, 18.4, 30.2, ['cup'], 6, title='MANUALS SHELF', body='#2f3d4a', door='#3d4f5e')
    m.g('routing box')
    m.box((17.6, 10.2, 14.4), (30.0, 13.8, 15.4), 'paint:#2b2f33', 'routing key box')
    for x in (17.9, 29.7):
        for y in (10.5, 13.5):
            m.bolt(x, y, 14.4, 'north', 0.3)
    rb = m.surface('north', face=14.38, title='ROUTING')
    for i, (cid, lab) in enumerate((('k_auto', 'AUTO ROUTE'), ('k_night', 'NIGHT STABLE'), ('k_ret', 'AUTO RETURN'))):
        u = X(19.2 + i * 2.4)
        m.ctl(rb, 'key', cid, u, 12.5, w=1.1, h=1.1, col='c0a040', label=lab, pos=['OFF', 'ON'])
        m.ctl(rb, 'label', '', u, 11.2, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(rb, 'push', 'road', X(26.6), 12.6, w=1.4, h=1.0, col='f0f0f0', label='ROAD')
    m.ctl(rb, 'label', '', X(26.6), 11.6, w=0, h=0, col='e8e8e8', label='STABLING ROAD')
    m.ctl(rb, 'digits', 'roadt', X(23.8), 10.75, w=11.6, h=0.7, col='8ad4ff', label='', fmt='%s')
    m.ctl(rb, 'digits', 'capt', X(28.7), 12.6, w=2.0, h=0.6, col='ffd040', label='', fmt='%s')
    # the shift manager's desk
    desk(m, {('l', 0): 'd1', ('l', 1): 'd2', ('l', 2): 'd3', ('r', 0): 'd4', ('r', 1): 'd5', ('r', 2): 'd6'}, body='#2f3d4a', front='#3d4f5e')
    sf = strip(m, 3.6, 8.4)
    push_row(m, sf, [('prev', '1a1a1a', 'JOB<'), ('next', '1a1a1a', 'JOB>'), ('newexam', '1fa34a', 'EXAM'), ('newrep', 'e08020', 'REPAIR'),
                     ('assign', 'f0d020', 'ASSIGN'), ('start', '30c060', 'START'), ('cancel', 'd02020', 'CANCEL'), ('dload', '2050d0', 'DLOAD'),
                     ('print', 'f0f0f0', 'PRINT'), ('uprev', '505860', 'UNIT<'), ('unext', '505860', 'UNIT>')], 1.1, 4.7, step=1.24, size=0.85)
    push_row(m, sf, [('todepot', 'e08020', 'DEPOT'), ('retsvc', '1fa34a', 'RETURN'), ('spare', '20a0a0', 'SPARE'), ('stopsvc', 'd02020', 'STOP'),
                     ('alloc', '2050d0', 'ALLOC'), ('lprev', '505860', 'LINE<'), ('lnext', '505860', 'LINE>'), ('tgtup', '30c060', 'TGT+'),
                     ('tgtdn', 'c06030', 'TGT-'), ('test', 'f0f0f0', 'TEST')], 1.1, 7.5, step=1.24, size=0.85)
    hd = hood(m)
    lamp_row(m, hd, [('pwr', '30e060', '12V'), ('due', 'ffb020', 'EXAM DUE'), ('crit', 'ff8030', 'CRIT'), ('fail', 'ff3030', 'FAILED', True),
                     ('parts', 'ff3030', 'PARTS', True), ('spare', '30e060', 'SPARES'), ('routed', '8ad4ff', 'DEPOT'), ('night', '8a8aff', 'NIGHT')], 1.2, 11.0, step=1.25, size=0.55)
    for i, (cid, col, lab) in enumerate((('nopen', 'ffd040', 'OPEN JOBS'), ('nfleet', '7cf29a', 'FLEET'), ('nspare', '8ad4ff', 'SPARES'))):
        u = 2.2 + i * 3.4
        m.ctl(hd, 'digits', cid, u, 9.7, w=2.8, h=0.8, col=col, label='', fmt='%d')
        m.ctl(hd, 'label', '', u, 9.15, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(hd, 'gauge', 'avail', 13.6, 10.4, w=2.2, h=2.2, col='f2f0e6', label='AVAIL %', max=100)
    mon = monitor(m, 2.6, 13.4, 12.4, 18.2, 10.2, ['m1', 'm2', 'm3', 'm4', 'm5', 'm6'], cols=['ffd040', '7cf29a', '7cf29a', '7cf29a', '7cf29a', 'b0b0b0'])
    m.box((2.6, 11.85, 10.15), (13.4, 12.35, 11.0), 'paint:#1c1e20', 'status bar')
    m.ctl(mon, 'digits', 'msg', X(8), 12.1, w=10.6, h=0.45, col='8ad4ff', label='', fmt='%s')
    m.g('desk things')
    m.box((14.0, 9.0, 8.6), (15.6, 9.9, 9.8), 'paint:#1a1a1a', 'phone body')
    m.cyl((13.9, 10.3, 9.2), 0.4, 1.8, 'x', 'paint:#1a1a1a', 'handset')
    m.box((0.6, 9.0, 11.2), (2.4, 9.4, 12.6), 'paint:#f4f0d8', 'job card tray')
    m.box((0.8, 9.4, 11.4), (2.2, 9.5, 12.4), 'paint:#fafaf2', 'job cards')
    m.box((0.6, 9.0, 9.0), (2.0, 10.6, 10.2), 'paint:#d8d8d4', 'card printer')
    m.box((0.8, 10.0, 8.95), (1.8, 10.1, 9.0), 'paint:#1a1a1a', 'printer slot')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ========================================================================================================= Depot Stores
def depot_stores():
    m = Model('depot_stores')
    m.g('racking')
    for x in (-14.0, -4.6, 4.8, 14.2, 23.6):
        for z in (8.8, 15.0):
            m.box((x, 0, z), (x + 0.6, 30.0, z + 0.6), 'paint:#1f4fa0', 'rack upright')
        m.box((x + 0.1, 0, 9.4), (x + 0.5, 30.0, 15.0), 'mesh', 'upright bracing', faces=('east', 'west'))
        m.box((x - 0.3, 0, 8.6), (x + 0.9, 0.3, 15.8), 'metal:steel', 'base plate')
    for y in (0.6, 10.2, 19.8, 29.4):
        m.box((-14.0, y, 8.6), (24.2, y + 0.8, 9.0), 'paint:#f07818', 'beam')
        m.box((-14.0, y, 15.0), (24.2, y + 0.8, 15.4), 'paint:#f07818', 'beam')
        m.box((-13.4, y + 0.8, 9.0), (23.6, y + 1.0, 15.0), 'galv', 'shelf deck')
    rk = m.surface('north', face=8.55, title='BINS')
    bins = [('b1', 'BRAKES + HOSES'), ('b2', 'BEARINGS + COUPLERS'), ('b3', 'MOTORS + POWER KITS'), ('b4', 'ELECTRICS'), ('b5', 'WHEELSETS + BOGIES'), ('b6', 'CONSUMABLES')]
    m.g('bins')
    for i, (bid, lab) in enumerate(bins):
        col, row = i % 3, i // 3
        x0 = -13.4 + col * 9.4
        y0 = 11.2 if row == 0 else 20.8
        if i == 4:
            x0, y0 = -13.4 + 2 * 9.4, 1.6                    # the heavy bin sits on the floor
        for k in range(3):
            bx = x0 + k * 2.95
            m.box((bx, y0, 9.2), (bx + 2.7, y0 + 3.6, 14.6), 'paint:#2a62b8', 'parts bin')
            m.box((bx, y0, 9.0), (bx + 2.7, y0 + 1.6, 9.2), 'paint:#3876d0', 'bin lip')
        m.box((x0, y0 + 4.2, 9.0), (x0 + 8.6, y0 + 5.4, 9.1), 'label:' + lab, 'bin label')
        m.ctl(rk, 'zone', bid, X(x0 + 4.3), y0 + 2.4, w=8.6, h=5.4, col='000000', label='')
        for k in range(4):
            m.box((x0 + 0.3 + k * 2.1, y0 + 3.7, 10.0), (x0 + 1.9 + k * 2.1, y0 + 4.2, 13.4), random.Random(i * 7 + k).choice(['paint:#b08c5a', 'paint:#a07848', 'paint:#c49a64']), 'carton')
    m.g('wheelset on the floor')
    m.cyl((-12.0, 1.0, 12.0), 2.6, 0.5, 'x', 'metal:steel', 'wheel')
    m.cyl((-5.0, 1.0, 12.0), 2.6, 0.5, 'x', 'metal:steel', 'wheel')
    m.cyl((-12.0, 3.6, 12.0), 0.5, 7.5, 'x', 'paint:#3a3a3a', 'axle')
    m.box((-13.0, 0.0, 9.6), (-4.0, 1.0, 14.4), 'wood', 'wheelset chocks')
    m.cyl((-8.5, 1.0, 12.0), 1.8, 1.6, 'x', 'paint:#3a3a3a', 'axlebox')
    m.g('stock board')
    m.box((0.4, 0, 3.2), (15.6, 9.0, 8.6), 'paint:#3a4048', 'issue counter')
    m.box((0.2, 9.0, 3.0), (15.8, 9.4, 8.8), 'wood', 'counter top')
    m.box((0.4, 0, 3.0), (15.6, 0.6, 3.2), 'haz', 'kick strip', faces=('north',))
    m.box((0.6, 9.4, 7.4), (15.4, 19.6, 8.4), 'paint:#22272c', 'stock board')
    m.box((1.0, 18.4, 7.32), (15.0, 19.3, 7.4), 'plate:STOCK - REORDER AT MIN', 'board title')
    sb = m.surface('north', face=7.3, title='STOCK')
    names = ['BRAKE BLK', 'WHEELSET', 'BEARING', 'TR MOTOR', 'POWER KIT', 'CONTROL', 'COUPLER', 'BRK HOSE', 'FILTERS', 'BOGIE OK', 'BOGIE BAD']
    for i, nm in enumerate(names):
        col, row = i // 6, i % 6
        u = 2.3 + col * 7.2
        v = 17.2 - row * 1.35
        m.ctl(sb, 'label', '', u, v, w=0, h=0, col='e8e8e8', label=nm)
        m.ctl(sb, 'digits', 'c%d' % i, u + 2.6, v, w=1.6, h=0.9, col='7cf29a', label='', fmt='%d')
        m.ctl(sb, 'lamp', 'l%d' % i, u + 4.1, v, w=0.55, h=0.55, col='ff3030', label='LOW', blink=True)
    m.ctl(sb, 'lamp', 'pwr', 9.5, 10.6, w=0.6, h=0.6, col='30e060', label='12V')
    m.ctl(sb, 'label', '', 9.5, 10.05, w=0, h=0, col='e8e8e8', label='12V')
    m.ctl(sb, 'digits', 'nlow', 12.6, 10.6, w=2.0, h=0.9, col='ff8040', label='', fmt='%d LOW')
    ct = m.surface('up', face=16 - 9.4, title='COUNTER')
    m.ctl(ct, 'selector', 's_min', 4.0, 5.6, w=1.3, h=1.3, col='202020', label='MIN', pos=['1', '2', '4', '8'])
    m.ctl(ct, 'label', '', 4.0, 4.4, w=0, h=0, col='e8e8e8', label='REORDER MIN')
    m.ctl(ct, 'push', 'count', 7.0, 5.6, w=1.1, h=1.1, col='f0d020', label='STOCKTAKE')
    m.ctl(ct, 'label', '', 7.0, 4.4, w=0, h=0, col='e8e8e8', label='STOCKTAKE')
    m.ctl(ct, 'push', 'test', 9.4, 5.6, w=0.9, h=0.9, col='f0f0f0', label='TEST')
    m.box((11.0, 9.4, 4.0), (14.4, 9.9, 6.4), 'paint:#f4f0d8', 'issue ledger')
    m.box((11.2, 9.9, 4.2), (14.2, 10.0, 6.2), 'paint:#fafaf2', 'ledger page')
    m.box((1.0, 9.4, 4.2), (2.4, 10.2, 5.6), 'paint:#1a1a1a', 'barcode scanner')
    m.ladder(24.4, 26.4, 0, 22.0, 9.0)
    m.box((24.2, 0, 10.0), (26.6, 0.4, 14.0), 'metal:steel', 'ladder trolley')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.26)


# ============================================================================================================ Tool Crib
def tool_crib():
    m = Model('tool_crib')
    back_wall(m, panel='#3a3f44', plaster='#d8dcd6')
    m.g('pegboard')
    m.box((-14.0, 9.6, 15.0), (-0.6, 20.4, 15.4), 'paint:#c8a676', 'pegboard')
    for x in range(-13, 0):
        for y in range(10, 20, 2):
            m.box((x + 0.3, y + 0.3, 14.98), (x + 0.5, y + 0.5, 15.0), 'paint:#3a2a1a', 'peg hole')
    spots = []
    pg = m.surface('north', face=14.9, title='PEGBOARD')
    for i in range(8):
        x = -12.5 + (i % 4) * 3.3
        y = 15.2 - (i // 4) * 5.0
        m.box((x - 0.15, y + 2.0, 14.2), (x + 0.15, y + 2.3, 15.0), 'metal:steel', 'tool hook')
        m.box((x - 1.2, y - 0.1, 14.9), (x + 1.2, y + 0.0, 15.0), 'paint:#202020', 'tool outline')
        m.ctl(pg, 'zone', 'sh%d' % (i + 1), X(x), y + 1.4, w=3.0, h=4.4, col='000000', label='')
        spots.append((x, y, 14.4))
    SPOTS['tool_crib'] = spots
    m.g('cage')
    for x in (-14.0, 30.0 - 0.4):
        m.box((x, 0, 0.4), (x + 0.4, 30.0, 15.4), 'mesh', 'cage side')
    m.box((-14.0, 29.6, 0.4), (30.0, 30.0, 15.4), 'mesh', 'cage roof')
    for x in (-14.0, -6.0, 22.0, 29.6):
        m.box((x, 0, 0.2), (x + 0.4, 30.0, 0.6), 'paint:#3a3f44', 'cage post')
    m.box((-14.0, 14.0, 0.3), (-6.0, 30.0, 0.5), 'mesh', 'cage front')
    m.box((22.0, 14.0, 0.3), (30.0, 30.0, 0.5), 'mesh', 'cage front')
    m.box((-6.0, 24.0, 0.3), (22.0, 30.0, 0.5), 'mesh', 'cage transom')
    m.box((-14.0, 13.6, 0.2), (30.0, 14.0, 0.6), 'paint:#3a3f44', 'cage rail')
    m.g('issue window')
    m.box((-6.0, 0, 0.6), (22.0, 9.0, 5.0), 'paint:#4a5258', 'issue counter')
    m.box((-6.2, 9.0, 0.4), (22.2, 9.4, 5.2), 'wood', 'counter top')
    m.box((-6.0, 0, 0.4), (22.0, 0.5, 0.6), 'haz', 'kick strip', faces=('north',))
    m.box((-6.0, 9.4, 4.6), (22.0, 24.0, 5.0), 'mesh', 'window grille')
    m.box((2.0, 9.4, 4.4), (14.0, 12.0, 4.6), 'glass', 'hatch glass')
    cr = m.surface('up', face=16 - 9.4, title='COUNTER')
    m.ctl(cr, 'push', 'out', X(4.0), 2.6, w=1.1, h=1.1, col='e08020', label='SIGN OUT')
    m.ctl(cr, 'label', '', X(4.0), 1.5, w=0, h=0, col='e8e8e8', label='SIGN OUT')
    m.ctl(cr, 'push', 'in', X(7.0), 2.6, w=1.1, h=1.1, col='1fa34a', label='SIGN IN')
    m.ctl(cr, 'label', '', X(7.0), 1.5, w=0, h=0, col='e8e8e8', label='SIGN IN')
    m.ctl(cr, 'push', 'test', X(9.4), 2.6, w=0.8, h=0.8, col='f0f0f0', label='TEST')
    m.ctl(cr, 'key', 'k_cage', X(12.0), 2.6, w=1.1, h=1.1, col='c0a040', label='CAGE', pos=['OPEN', 'LOCKED'])
    m.ctl(cr, 'label', '', X(12.0), 1.5, w=0, h=0, col='e8e8e8', label='CAGE')
    m.g('register display')
    m.box((-5.0, 14.6, 4.2), (13.0, 19.4, 4.6), 'paint:#1c1e20', 'register display')
    m.box((-4.6, 18.6, 4.15), (12.6, 19.3, 4.2), 'plate:SIGN-OUT REGISTER', 'register title')
    rd = m.surface('north', face=4.12, title='REGISTER')
    for i in range(4):
        m.ctl(rd, 'digits', 'r%d' % (i + 1), X(4.0), 18.0 - i * 0.85, w=17.4, h=0.75, col='7cf29a' if i else 'ffd040', label='', fmt='%s')
    m.ctl(rd, 'lamp', 'pwr', X(-4.3), 14.9, w=0.45, h=0.45, col='30e060', label='12V')
    m.ctl(rd, 'lamp', 'cagel', X(-3.4), 14.9, w=0.45, h=0.45, col='ffb020', label='LOCKED')
    m.ctl(rd, 'digits', 'nout', X(11.0), 14.9, w=3.2, h=0.5, col='ff8040', label='', fmt='%d OUT')
    m.g('tool chest')
    m.box((0.6, 0, 8.6), (15.4, 9.6, 14.8), 'paint:#b01e1e', 'roller tool chest')
    tc = m.surface('north', face=8.45, title='DRAWERS')
    for k in range(4):
        y0 = 0.8 + k * 2.15
        m.box((0.9, y0, 8.45), (15.1, y0 + 1.95, 8.6), 'paint:#c42828', 'chest drawer')
        m.box((5.0, y0 + 1.3, 8.2), (11.0, y0 + 1.6, 8.45), 'metal:alu', 'drawer pull')
        m.ctl(tc, 'zone', 't%d' % (4 - k), X(8.0), y0 + 1.0, w=14.0, h=1.9, col='000000', label='')
    for x in (1.4, 14.6):
        m.cyl((x, 0, 9.4), 0.5, 0.4, 'y', 'rubber', 'castor')
    m.box((0.6, 9.6, 8.6), (15.4, 10.0, 14.8), 'rubber', 'chest mat')
    m.box((2.0, 10.0, 10.0), (6.0, 10.6, 11.0), 'paint:#2a2a2a', 'torque wrench')
    m.box((8.0, 10.0, 10.5), (12.0, 10.8, 13.5), 'paint:#f0c818', 'impact gun case')
    m.g('cabinet')
    cabinet(m, 17.0, 29.0, 0, 26.0, 9.2, 15.2, col='paint:#4a5258', door='paint:#5a636a', name='tool cabinet')
    m.box((18.0, 22.0, 9.0), (28.0, 23.4, 9.05), 'label:SPECIAL TOOLS', 'cabinet label')
    cb = m.surface('north', face=9.0, title='CABINET')
    m.ctl(cb, 'zone', 'cab', X(23.0), 12.0, w=11.4, h=20.0, col='000000', label='')
    m.terminal_12v(15.6, 1.0, 12.0, 'east')
    m.save(item_gui=0.26)


# ============================================================================================================ Crew Desk
def crew_desk():
    m = Model('crew_desk')
    back_wall(m, panel='#2a3a2a', plaster='#e6e8dc')
    m.g('roster board')
    led_board(m, -1, 29, 14.6, 28.6)
    m.box((4, 29.0, 14.7), (24, 31.2, 15.4), 'paint:#1c2c1c', 'roster header')
    m.box((4.4, 29.2, 14.62), (23.6, 31.0, 14.7), 'plate:CREW ROSTER - DUTY', 'roster title')
    rb = m.surface('north', face=14.42, title='ROSTER')
    for r in range(1, 7):
        v = 27.2 - (r - 1) * 2.0
        m.ctl(rb, 'lamp', 'd%ds' % r, X(0.0), v, w=0.6, h=0.6, col='ffb020', label='')
        m.ctl(rb, 'digits', 'd%d' % r, X(14.6), v, w=27.6, h=1.6, col='7cf29a', label='', fmt='%s')
    m.ctl(rb, 'digits', 'clock', X(26.4), 15.6, w=4.6, h=1.0, col='ffd040', label='', fmt='%s')
    m.g('lockers')
    for i in range(4):
        x0 = -13.6 + i * 3.1
        m.box((x0, 0, 9.0), (x0 + 3.0, 18.0, 15.2), 'paint:#5a7a5a', 'crew locker')
        m.box((x0 + 0.15, 0.4, 8.85), (x0 + 2.85, 17.6, 9.0), 'paint:#6a8a6a', 'locker door')
        for y in range(12, 17):
            m.box((x0 + 0.6, y, 8.82), (x0 + 2.4, y + 0.25, 8.85), 'paint:#2a3a2a', 'vent slot')
        m.box((x0 + 2.2, 8.0, 8.6), (x0 + 2.5, 9.6, 8.85), 'metal:steel', 'locker handle')
        m.box((x0 + 0.6, 10.2, 8.83), (x0 + 2.4, 10.9, 8.85), 'label:CREW', 'locker tag')
    lk = m.surface('north', face=8.8, title='LOCKERS')
    m.ctl(lk, 'zone', 'lk', X(-7.6), 8.0, w=12.0, h=15.0, col='000000', label='')
    spots = []
    sh = m.surface('up', face=16 - 18.0, title='HAT SHELF')
    for k in range(4):
        x = -12.1 + k * 3.1
        m.ctl(sh, 'zone', 'sh%d' % (k + 1), X(x), 12.1, w=2.9, h=5.8, col='000000', label='')
        spots.append((round(x, 3), 18.0, 12.1))
    SPOTS['crew_desk'] = spots
    m.g('duty notices')
    m.box((17.0, 10.0, 15.2), (29.0, 13.6, 15.4), 'paint:#c8a676', 'notice board')
    for i, c in enumerate(('#fafaf2', '#f6e27a', '#fafaf2', '#a8d8f0')):
        m.box((17.5 + i * 2.9, 10.4, 15.15), (19.9 + i * 2.9, 13.2, 15.2), 'paint:' + c, 'notice')
    m.box((17.4, 1.0, 9.6), (29.4, 8.0, 15.2), 'paint:#3a4a3a', 'signing-in lectern')
    m.box((17.2, 8.0, 9.4), (29.6, 8.4, 15.4), 'wood', 'lectern top')
    m.box((19.0, 8.4, 10.4), (27.0, 8.8, 14.0), 'paint:#f4f0d8', 'duty book')
    m.box((22.8, 8.8, 10.4), (23.2, 8.9, 14.0), 'paint:#8a2a2a', 'book ribbon')
    desk(m, {('l', 0): 'cd1', ('l', 1): 'cd2'}, body='#2a3a2a', front='#3a4e3a')
    sf = strip(m, 3.6, 8.4)
    push_row(m, sf, [('signon', '1fa34a', 'SIGN ON'), ('signoff', 'd02020', 'SIGN OFF'), ('brk', 'f0d020', 'BREAK'), ('hire', 'b0b0b0', 'HIRE'),
                     ('assign', '2050d0', 'ASSIGN'), ('qual', '20a0a0', 'PASS ON'), ('route', '8040c0', 'ROUTE'), ('attend', 'e08020', 'ATTEND'),
                     ('print', 'f0f0f0', 'PRINT'), ('test', 'f0f0f0', 'TEST')], 1.2, 4.7, step=1.35, size=0.85)
    push_row(m, sf, [('prev', '505860', 'DRV<'), ('next', '505860', 'DRV>'), ('tprev', '505860', 'TRN<'), ('tnext', '505860', 'TRN>'),
                     ('lprev', '505860', 'LINE<'), ('lnext', '505860', 'LINE>')], 1.2, 7.5, step=1.35, size=0.85)
    m.ctl(sf, 'selector', 's_trac', 12.6, 7.4, w=1.3, h=1.3, col='202020', label='TRACTION', pos=['DIESEL', 'ELECTRIC', 'STEAM'])
    m.ctl(sf, 'label', '', 12.6, 6.3, w=0, h=0, col='e8e8e8', label='TRACTION')
    hd = hood(m)
    lamp_row(m, hd, [('pwr', '30e060', '12V'), ('overh', 'ff3030', 'OVER HRS', True), ('brkdue', 'ffb020', 'BREAK DUE'), ('nodrv', 'ff3030', 'NO DRIVER', True)], 1.4, 11.0, step=1.6, size=0.55)
    m.ctl(hd, 'digits', 'ndrv', 8.6, 11.0, w=2.6, h=0.8, col='7cf29a', label='', fmt='%d ON')
    m.ctl(hd, 'digits', 'tsel', 4.6, 9.7, w=7.6, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(hd, 'digits', 'lsel', 12.0, 9.7, w=6.4, h=0.8, col='8ad4ff', label='', fmt='%s')
    mon = monitor(m, 2.6, 13.4, 12.4, 17.4, 10.2, ['m1', 'm2', 'm3', 'm4'], cols=['ffd040', '7cf29a', '7cf29a', '7cf29a'])
    m.box((2.6, 11.85, 10.15), (13.4, 12.35, 11.0), 'paint:#1c1e20', 'status bar')
    m.ctl(mon, 'digits', 'msg', X(8), 12.1, w=10.6, h=0.45, col='8ad4ff', label='', fmt='%s')
    m.g('desk things')
    m.box((0.6, 9.0, 11.0), (2.6, 9.3, 12.8), 'paint:#f4f0d8', 'route cards')
    m.cyl((14.6, 9.0, 11.6), 0.5, 1.2, 'y', 'paint:#f4f4f0', 'tea mug')
    m.box((14.0, 9.0, 8.6), (15.6, 9.9, 9.8), 'paint:#1a1a1a', 'radio base')
    m.box((14.9, 9.9, 9.0), (15.1, 11.8, 9.2), 'paint:#1a1a1a', 'radio aerial')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ========================================================================================================= Wheel Lathe
def wheel_lathe():
    m = Model('wheel_lathe')
    m.g('machine bed')
    m.box((0.5, 0, 6.0), (15.5, 3.0, 16.0), 'paint:#2e4a3a', 'cast bed')
    m.box((0.3, 0, 5.8), (15.7, 0.4, 16.0), 'paint:#1a2a20', 'bed foot')
    for x in (1.2, 14.8):
        for z in (7.0, 15.0):
            m.bolt(x, 3.0, z, 'up', 0.5)
    m.box((1.0, 3.0, 7.0), (15.0, 3.6, 15.4), 'metal:steel', 'slideways')
    m.g('tool heads')
    for x0, nm in ((1.8, 'left tool head'), (9.4, 'right tool head')):
        m.box((x0, 3.6, 9.0), (x0 + 4.8, 8.6, 14.6), 'paint:#3a5a48', nm)
        m.box((x0 + 0.6, 8.6, 9.6), (x0 + 4.2, 9.4, 14.0), 'paint:#24382c', nm + ' cover')
        m.box((x0 + 1.6, 5.0, 14.6), (x0 + 3.2, 6.4, 24.0), 'metal:steel', 'tool slide')
        m.box((x0 + 1.9, 5.2, 24.0), (x0 + 2.9, 6.2, 25.2), 'paint:#d8b040', 'carbide insert')
        m.cyl((x0 + 2.4, 7.4, 14.6), 0.35, 8.0, 'z', 'cable:202020', 'coolant hose')
        m.box((x0 + 0.8, 7.0, 8.8), (x0 + 4.0, 8.2, 9.0), 'label:X AXIS', 'axis plate')
    m.g('drive rollers')
    for x in (3.0, 13.0):
        m.cyl((x, 2.0, 20.0), 1.6, 2.0, 'x', 'metal:steel', 'drive roller')
        m.box((x - 1.2, 0, 18.4), (x + 1.2, 2.0, 21.6), 'paint:#2e4a3a', 'roller cradle')
    m.box((0.8, 0, 16.0), (15.2, 1.4, 24.0), 'paint:#2e4a3a', 'pit bed extension')
    m.box((5.0, 2.2, 18.0), (11.0, 3.2, 22.0), 'paint:#3a3a3a', 'axlebox clamp')
    m.box((0.5, 0, 24.0), (15.5, 0.6, 26.0), 'haz', 'pit edge', faces=('up',))
    m.g('swarf + coolant')
    m.box((1.0, 0, 26.0), (15.0, 1.6, 30.0), 'paint:#5a6066', 'swarf conveyor')
    m.box((1.4, 1.6, 26.4), (14.6, 1.8, 29.6), 'metal:steel', 'swarf')
    m.box((12.0, 0, 1.0), (15.4, 4.0, 5.6), 'paint:#2a6aa0', 'coolant tank')
    m.box((12.4, 4.0, 1.4), (15.0, 4.4, 5.2), 'paint:#1e5080', 'coolant lid')
    m.cyl((13.7, 4.4, 3.3), 0.4, 1.2, 'y', 'paint:#202020', 'coolant pump motor')
    m.g('operator console')
    m.box((1.0, 0, 0.8), (11.0, 9.0, 5.6), 'paint:#3a4048', 'console pedestal')
    m.box((0.8, 9.0, 0.6), (11.2, 15.6, 3.4), 'paint:#2b2f33', 'console head')
    m.box((0.6, 15.6, 0.4), (11.4, 16.0, 3.6), 'paint:#1c1e20', 'console hood')
    m.box((1.4, 1.0, 0.65), (10.6, 2.0, 0.8), 'plate:UNDERFLOOR WHEEL LATHE', 'nameplate')
    m.box((1.2, 0, 0.6), (10.8, 0.5, 0.8), 'haz', 'kick strip', faces=('north',))
    f = m.surface('north', face=0.58, title='LATHE')
    m.ctl(f, 'digits', 'line1', X(6.0), 15.0, w=9.6, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(6.0), 14.2, w=9.6, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'gauge', 'rpm', X(2.8), 12.2, w=2.0, h=2.0, col='f2f0e6', label='SPINDLE RPM', max=100)
    m.ctl(f, 'gauge', 'feed', X(5.6), 12.2, w=2.0, h=2.0, col='f2f0e6', label='FEED %', max=100)
    m.ctl(f, 'gauge', 'prog', X(8.4), 12.2, w=2.0, h=2.0, col='f2f0e6', label='CUT %', max=100)
    m.ctl(f, 'digits', 'dia', X(3.0), 10.4, w=2.4, h=0.7, col='8ad4ff', label='', fmt='%s MM')
    m.ctl(f, 'label', '', X(3.0), 9.8, w=0, h=0, col='e8e8e8', label='TREAD DIA')
    m.ctl(f, 'digits', 'flg', X(6.0), 10.4, w=2.4, h=0.7, col='8ad4ff', label='', fmt='%s MM')
    m.ctl(f, 'label', '', X(6.0), 9.8, w=0, h=0, col='e8e8e8', label='FLANGE')
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('clampl', 'ffb020', 'CLAMP'), ('ready', '30e060', 'READY'),
                    ('cutting', 'ffb020', 'CUT', True), ('cool', '8ad4ff', 'COOL'), ('fault', 'ff3030', 'FAULT', True)], X(1.6), 8.6, step=1.35, size=0.5)
    push_row(m, f, [('clamp', '1fa34a', 'CLAMP'), ('unclamp', 'f0d020', 'UNCLAMP'), ('measure', '2050d0', 'MEASURE'), ('cut', 'f0f0f0', 'CUT'), ('test', 'b0b0b0', 'TEST')],
             X(1.8), 6.4, step=1.6, size=0.95)
    m.ctl(f, 'selector', 's_axle', X(2.4), 3.6, w=1.3, h=1.3, col='202020', label='AXLE', pos=['1', '2', '3', '4', 'ALL'])
    m.ctl(f, 'selector', 's_cool', X(5.2), 3.6, w=1.3, h=1.3, col='202020', label='COOLANT', pos=['OFF', 'ON'])
    estop_plate(m, f, X(8.6), 3.6)
    m.box((11.2, 10.0, 1.4), (11.8, 15.0, 2.0), 'paint:#f0c818', 'console grab bar')
    m.cyl((6.0, 16.0, 2.0), 0.6, 1.2, 'y', 'paint:#2b2f33', 'beacon base')
    m.cyl((6.0, 17.2, 2.0), 0.5, 1.0, 'y', 'lamp:ffa020', 'amber beacon')
    mains_box(m, 15.5, 5.0, 3.0, 'east')
    m.terminal_12v(11.0, 4.0, 3.0, 'east')
    m.g('rail sections')
    for x in (4.2, 11.8):
        m.box((x - 0.35, 3.0, 16.0), (x + 0.35, 3.8, 24.0), 'metal:steel', 'drop-away rail section')
        m.box((x - 0.8, 2.4, 16.0), (x + 0.8, 3.0, 24.0), 'metal:steel', 'rail foot')
        m.cyl((x, 0.6, 17.0), 0.45, 1.8, 'y', 'metal:steel', 'rail section ram')
        m.cyl((x, 0.6, 23.0), 0.45, 1.8, 'y', 'metal:steel', 'rail section ram')
    m.g('guarding')
    m.handrail([(0.4, 16.2), (0.4, 29.6)], 0.0, h=4.0)
    m.handrail([(15.6, 16.2), (15.6, 29.6)], 0.0, h=4.0)
    m.box((0.5, 3.6, 8.6), (15.5, 7.6, 8.8), 'mesh', 'chip guard')
    m.box((0.4, 7.6, 8.4), (15.6, 8.0, 9.0), 'paint:#f0c818', 'guard rail')
    m.box((5.4, 4.0, 8.5), (10.6, 6.8, 8.6), 'danger:DANGER|ROTATING|MACHINERY', 'danger sign')
    m.g('drives')
    for x in (3.0, 13.0):
        m.cyl((x, 0.4, 19.0), 0.9, 1.8, 'y', 'paint:#2a2a2a', 'roller drive motor')
        m.box((x - 0.6, 2.2, 18.4), (x + 0.6, 2.6, 19.6), 'paint:#f0c818', 'motor terminal box')
    for x0 in (1.8, 9.4):
        m.cyl((x0 + 2.4, 8.6, 11.8), 0.7, 1.2, 'y', 'paint:#2a2a2a', 'axis servo')
        m.box((x0 + 1.9, 9.8, 11.3), (x0 + 2.9, 10.2, 12.3), 'paint:#1a1a1a', 'encoder')
        m.box((x0 + 0.2, 3.6, 13.8), (x0 + 0.6, 7.8, 14.4), 'metal:steel', 'gib strip')
        m.box((x0 + 4.2, 3.6, 13.8), (x0 + 4.6, 7.8, 14.4), 'metal:steel', 'gib strip')
    m.g('cable tray')
    m.box((15.6, 3.0, 5.6), (16.0, 3.6, 16.0), 'galv', 'cable tray')
    for z in (7.0, 10.0, 13.0):
        m.cyl((15.8, 3.6, z), 0.2, 0.6, 'y', 'cable:202020', 'cable tie')
    m.cyl((12.6, 4.4, 2.0), 0.25, 7.0, 'z', 'cable:2a62c8', 'coolant line')
    m.cyl((14.0, 1.0, 5.6), 0.25, 3.4, 'z', 'cable:2a62c8', 'coolant return')
    m.bolts(1.0, 15.0, 2.2, 6.0, 6, face='north')
    m.save(item_gui=0.45)


# ============================================================================================================ Bogie Drop
def bogie_drop():
    m = Model('bogie_drop')
    m.g('pit')
    m.box((0, 0, 6.0), (16, 1.0, 32.0), 'concrete', 'pit floor')
    m.box((0, 0, 30.0), (16, 4.0, 32.0), 'concrete', 'pit wall')
    m.box((0, 3.6, 6.0), (16, 4.0, 30.0), 'haz', 'pit edge', faces=('up',))
    m.g('drop table')
    m.box((2.0, 1.0, 12.0), (14.0, 2.0, 28.0), 'paint:#3a5a8a', 'table frame')
    m.box((2.4, 2.0, 12.4), (13.6, 2.6, 27.6), 'metal:steel', 'table deck')
    for x in (3.0, 13.0):
        m.box((x - 0.2, 2.6, 12.4), (x + 0.2, 3.4, 27.6), 'metal:steel', 'table rail')
    for z in (14.0, 26.0):
        for x in (3.5, 12.5):
            m.cyl((x, 0.2, z), 0.9, 1.0, 'y', 'metal:steel', 'ram')
            m.cyl((x, 1.0, z), 0.6, 0.6, 'y', 'metal:steel', 'ram rod')
    m.g('bogie on the table')
    m.box((4.0, 3.4, 15.0), (12.0, 5.0, 25.0), 'paint:#2a2a2a', 'bogie frame')
    for z in (16.5, 23.5):
        m.cyl((2.8, 4.6, z), 1.8, 0.5, 'x', 'metal:steel', 'wheel')
        m.cyl((12.7, 4.6, z), 1.8, 0.5, 'x', 'metal:steel', 'wheel')
        m.cyl((3.3, 4.6, z), 0.35, 9.4, 'x', 'paint:#3a3a3a', 'axle')
        m.box((3.6, 3.8, z - 0.9), (4.6, 5.4, z + 0.9), 'paint:#4a4a4a', 'axlebox')
        m.box((11.4, 3.8, z - 0.9), (12.4, 5.4, z + 0.9), 'paint:#4a4a4a', 'axlebox')
    for z in (18.0, 22.0):
        m.cyl((5.0, 5.0, z), 0.5, 1.2, 'y', 'paint:#c8a020', 'spring')
        m.cyl((11.0, 5.0, z), 0.5, 1.2, 'y', 'paint:#c8a020', 'spring')
    m.box((6.0, 5.0, 19.0), (10.0, 6.0, 21.0), 'paint:#3a3a3a', 'bolster')
    m.g('body supports')
    for x in (0.4, 14.6):
        m.box((x, 0, 9.0), (x + 1.0, 14.0, 10.0), 'paint:#f0c818', 'body support post')
        m.box((x - 0.2, 14.0, 8.8), (x + 1.2, 14.6, 10.2), 'paint:#2a2a2a', 'support pad')
        m.box((x, 0, 9.0), (x + 1.0, 2.0, 10.0), 'haz', 'post base')
    m.g('hydraulic power pack')
    m.box((0.6, 0, 0.6), (15.4, 4.4, 5.6), 'paint:#3a5a8a', 'power pack')
    m.box((1.0, 4.4, 1.0), (7.0, 5.6, 5.2), 'paint:#2a4a6a', 'oil tank')
    m.cyl((10.0, 4.4, 3.0), 1.3, 2.6, 'y', 'paint:#2a2a2a', 'pump motor')
    m.louvres(8.0, 14.6, 0.8, 3.8, 0.6, n=4)
    m.box((1.2, 1.2, 0.45), (7.0, 2.2, 0.6), 'plate:BOGIE DROP 60T', 'nameplate')
    for z in (2.0, 4.0):
        m.cyl((2.0, 2.0, z + 3.6), 0.25, 8.0, 'z', 'cable:101010', 'hydraulic hose')
    m.g('control pedestal')
    m.box((4.0, 4.4, 1.4), (14.0, 14.4, 4.6), 'paint:#2b2f33', 'control pedestal')
    m.box((3.8, 14.4, 1.2), (14.2, 14.8, 4.8), 'paint:#1c1e20', 'pedestal cap')
    f = m.surface('north', face=1.38, title='BOGIE DROP')
    m.ctl(f, 'digits', 'line1', X(9.0), 13.8, w=9.4, h=0.65, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(9.0), 13.05, w=9.4, h=0.65, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'gauge', 'table', X(5.6), 11.2, w=1.9, h=1.9, col='f2f0e6', label='TABLE %', max=100)
    m.ctl(f, 'gauge', 'load', X(8.0), 11.2, w=1.9, h=1.9, col='f2f0e6', label='LOAD T', max=100)
    m.ctl(f, 'digits', 'serial', X(11.6), 11.8, w=3.6, h=0.6, col='8ad4ff', label='', fmt='%s')
    m.ctl(f, 'digits', 'nbay', X(11.0), 10.9, w=1.6, h=0.6, col='7cf29a', label='', fmt='%d OK')
    m.ctl(f, 'digits', 'nbad', X(12.8), 10.9, w=1.6, h=0.6, col='ff8040', label='', fmt='%d BAD')
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('locked', 'ffb020', 'LOCK'), ('down', 'ffb020', 'DOWN'),
                    ('out', '8ad4ff', 'OUT'), ('newin', '30e060', 'NEW'), ('moving', 'ff8030', 'MOVE', True), ('ovh', '8ad4ff', 'OVH', True)], X(4.6), 9.4, step=1.2, size=0.45)
    push_row(m, f, [('lock', 'ffb020', 'LOCK'), ('lower', '2050d0', 'LOWER'), ('traverse', '20a0a0', 'TRAV'), ('fit', '1fa34a', 'FIT')], X(4.8), 7.4, step=1.55, size=0.85)
    push_row(m, f, [('raise', '2050d0', 'RAISE'), ('unlock', 'f0d020', 'UNLOCK'), ('overhaul', '8040c0', 'OVERHAUL'), ('test', 'b0b0b0', 'TEST')], X(4.8), 5.6, step=1.55, size=0.85)
    m.ctl(f, 'selector', 's_end', X(11.2), 7.4, w=1.1, h=1.1, col='202020', label='END', pos=['A', 'B'])
    m.ctl(f, 'selector', 's_mode', X(13.2), 7.4, w=1.1, h=1.1, col='202020', label='MODE', pos=['BOGIE', 'WHEELSET'])
    estop_plate(m, f, X(12.2), 5.4)
    m.g('bogie bay')
    m.box((0.4, 0, 28.4), (15.6, 0.4, 31.6), 'metal:steel', 'bay plate')
    bb = m.surface('north', face=5.5, title='BAY')
    m.box((0.6, 4.4, 5.6), (3.6, 7.4, 6.0), 'paint:#3a5a8a', 'bay door')
    m.box((0.8, 6.6, 5.55), (3.4, 7.2, 5.6), 'label:BAY', 'bay label')
    m.ctl(bb, 'zone', 'bay', X(2.1), 5.9, w=2.8, h=2.8, col='000000', label='')
    mains_box(m, 15.4, 4.6, 3.0, 'east')
    m.terminal_12v(4.0, 5.0, 3.0, 'west')
    m.save(item_gui=0.4)


# ========================================================================================================= Brake Test Rig
def brake_test():
    m = Model('brake_test')
    m.g('cabinet')
    m.box((1.0, 0, 3.0), (15.0, 0.8, 14.0), 'paint:#2a2d30', 'plinth')
    m.box((1.4, 0.8, 3.6), (14.6, 24.0, 13.6), 'paint:#c8ccd0', 'rig cabinet')
    m.box((1.2, 24.0, 3.4), (14.8, 24.6, 13.8), 'paint:#2a2d30', 'cabinet roof')
    m.box((1.6, 0.8, 3.4), (14.4, 10.0, 3.6), 'paint:#b8bcc0', 'lower door')
    m.louvres(2.4, 7.4, 2.0, 6.0, 3.4, n=4)
    m.box((13.6, 4.0, 3.1), (14.0, 7.0, 3.4), 'metal:steel', 'door handle')
    m.box((8.0, 1.4, 3.32), (13.4, 2.4, 3.4), 'plate:BRAKE TEST RIG', 'nameplate')
    m.g('air system')
    m.cyl((8.0, 0.8, 9.0), 2.4, 1.0, 'y', 'paint:#8a1e1e', 'air receiver foot')
    for x in (3.0, 13.0):
        m.cyl((x, 25.2, 8.5), 1.2, 0.0001 + 1.0, 'y', 'paint:#8a1e1e', 'receiver end')
    m.cyl((2.0, 25.6, 8.5), 1.1, 12.0, 'x', 'paint:#8a1e1e', 'main reservoir')
    m.box((1.6, 24.6, 7.8), (2.6, 25.6, 9.2), 'paint:#2a2d30', 'reservoir saddle')
    m.box((13.4, 24.6, 7.8), (14.4, 25.6, 9.2), 'paint:#2a2d30', 'reservoir saddle')
    m.cyl((7.6, 26.7, 8.5), 0.4, 0.8, 'y', 'metal:brass', 'safety valve')
    m.g('hose reel')
    m.cyl((15.0, 12.0, 9.0), 2.6, 1.4, 'x', 'paint:#f0c818', 'hose reel')
    m.cyl((16.4, 12.0, 9.0), 1.6, 0.6, 'x', 'rubber', 'wound hose')
    m.cyl((16.9, 3.0, 9.0), 0.35, 9.0, 'y', 'rubber', 'brake pipe hose')
    m.box((16.4, 2.2, 8.4), (17.4, 3.0, 9.6), 'metal:brass', 'glad hand')
    m.g('front panel')
    m.box((1.8, 10.6, 3.2), (14.2, 23.4, 3.6), 'paint:#2b2f33', 'test panel')
    f = m.surface('north', face=3.18, title='BRAKE TEST')
    m.ctl(f, 'digits', 'line1', X(8.0), 22.6, w=11.6, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(8.0), 21.7, w=11.6, h=0.7, col='7cf29a', label='', fmt='%s')
    for i, (cid, lab, mx) in enumerate((('bp', 'BRAKE PIPE', 60), ('bc', 'CYLINDER', 50), ('mr', 'MAIN RES', 100), ('leakg', 'LEAK', 100))):
        m.ctl(f, 'gauge', cid, X(3.4 + i * 3.1), 19.2, w=2.6, h=2.6, col='f2f0e6', label=lab, max=mx)
        m.ctl(f, 'label', '', X(3.4 + i * 3.1), 17.6, w=0, h=0, col='e8e8e8', label=lab)
    for i, (cid, lab) in enumerate((('cars', 'VEH'), ('resp', 'RESP'), ('force', 'FORCE %'))):
        m.ctl(f, 'digits', cid, X(3.6 + i * 3.2), 16.6, w=2.4, h=0.8, col='8ad4ff', label='', fmt='%d')
        m.ctl(f, 'label', '', X(3.6 + i * 3.2), 15.9, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(f, 'digits', 'leakd', X(12.6), 16.6, w=2.4, h=0.8, col='ff8040', label='', fmt='%s BAR')
    m.ctl(f, 'label', '', X(12.6), 15.9, w=0, h=0, col='e8e8e8', label='LEAK/MIN')
    lamp_row(m, f, [('l1', 'ffb020', 'CHARGE'), ('l2', 'ffb020', 'APPLY'), ('l3', 'ffb020', 'LEAK'), ('l4', 'ffb020', 'PARK'), ('l5', 'ffb020', 'RESULT')],
             X(3.0), 14.6, step=2.4, size=0.55)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('run', 'ffb020', 'RUN', True), ('pass', '30e060', 'PASS'), ('fail', 'ff3030', 'FAIL', True)], X(3.0), 13.0, step=2.4, size=0.6)
    push_row(m, f, [('start', '1fa34a', 'START'), ('abort', 'd02020', 'ABORT'), ('print', 'f0f0f0', 'PRINT'), ('test', 'b0b0b0', 'TEST')], X(3.2), 11.4, step=2.2, size=0.95)
    estop_plate(m, f, X(12.6), 11.4)
    m.g('printer')
    m.box((4.0, 24.6, 4.0), (8.0, 25.6, 7.0), 'paint:#d8d8d4', 'ticket printer')
    m.box((4.4, 25.0, 3.9), (7.6, 25.1, 4.0), 'paint:#1a1a1a', 'paper slot')
    m.terminal_12v(14.6, 1.6, 6.0, 'east')
    m.g('air pipework')
    m.cyl((13.0, 1.2, 13.0), 0.3, 24.0, 'y', 'metal:copper', 'reservoir down pipe')
    m.cyl((3.0, 1.2, 13.0), 0.3, 24.0, 'y', 'metal:copper', 'brake pipe riser')
    m.cyl((3.0, 6.0, 13.0), 0.3, 13.6, 'x', 'metal:copper', 'manifold')
    for x in (5.0, 7.5, 10.0, 12.5):
        m.cyl((x, 6.0, 13.6), 0.45, 0.6, 'z', 'metal:brass', 'isolating cock')
        m.box((x - 0.15, 6.4, 13.9), (x + 0.15, 7.4, 14.1), 'paint:#c82020', 'cock handle')
    m.cyl((8.0, 4.0, 13.6), 0.9, 0.8, 'z', 'paint:#2a2a2a', 'pressure regulator')
    m.cyl((8.0, 4.0, 14.4), 0.6, 0.2, 'z', 'gauge:reg', 'regulator gauge')
    m.cyl((4.0, 0.8, 11.0), 0.3, 1.0, 'y', 'metal:brass', 'drain valve')
    m.g('compressor')
    m.box((9.6, 0.8, 9.6), (14.4, 4.0, 13.4), 'paint:#3a3f44', 'compressor set')
    m.box((9.8, 4.0, 9.8), (14.2, 4.4, 13.2), 'fins', 'aftercooler')
    m.cyl((12.0, 1.4, 9.4), 1.1, 2.0, 'z', 'paint:#2a2a2a', 'compressor motor')
    m.g('side')
    m.louvres(4.0, 12.0, 14.0, 20.0, 3.4, n=4)
    m.box((1.2, 20.0, 6.0), (1.4, 23.0, 11.0), 'warn', 'air warning', faces=('west',))
    m.box((1.2, 15.0, 6.0), (1.4, 19.0, 11.0), 'label:COMPRESSED|AIR 10 BAR', 'air label', faces=('west',))
    m.box((14.6, 15.0, 6.0), (14.8, 19.0, 11.0), 'label:BRAKE PIPE|5 BAR', 'pipe label', faces=('east',))
    m.g('chocks and hose rack')
    for z in (4.0, 6.0):
        m.box((16.4, 0, z), (18.4, 1.0, z + 1.4), 'paint:#f0c818', 'wheel chock')
    m.box((-1.0, 0, 6.0), (1.0, 12.0, 6.6), 'galv', 'hose rack post')
    for y in (4.0, 7.0, 10.0):
        m.box((-1.4, y, 6.0), (1.0, y + 0.4, 6.6), 'galv', 'hose hook')
        m.cyl((-0.8, y - 1.6, 6.3), 0.6, 1.6, 'y', 'rubber', 'coiled test hose')
    m.bolts(2.0, 14.0, 0.8, 3.4, 5, face='north')
    m.save(item_gui=0.4)


# ========================================================================================================= Fuel + Sand Point
def fuel_point():
    m = Model('fuel_point')
    m.g('bund')
    m.box((0, 0, 2.0), (16, 1.2, 16.0), 'concrete', 'bund floor')
    m.box((0, 1.2, 15.2), (16, 3.0, 16.0), 'concrete', 'bund wall')
    m.box((0, 1.2, 2.0), (0.8, 3.0, 16.0), 'concrete', 'bund wall')
    m.box((15.2, 1.2, 2.0), (16, 3.0, 16.0), 'concrete', 'bund wall')
    m.g('tank')
    m.cyl((8.0, 1.2, 11.0), 4.2, 20.0, 'y', 'paint:#2a5a3a', 'fuel tank')
    m.cyl((8.0, 21.2, 11.0), 1.0, 1.0, 'y', 'metal:steel', 'tank vent')
    m.box((7.6, 22.2, 10.6), (8.4, 23.0, 11.4), 'mesh', 'vent cap')
    m.box((3.4, 14.0, 6.6), (12.6, 16.0, 6.8), 'label:DIESEL - NO SMOKING', 'tank label')
    m.box((3.4, 8.0, 6.6), (6.4, 11.0, 6.8), 'warn', 'flammable sign')
    m.ladder(11.6, 13.4, 1.2, 20.0, 15.0)
    m.g('sand tower')
    m.box((12.6, 1.2, 2.6), (15.4, 18.0, 5.4), 'paint:#8a6a3a', 'sand tower leg')
    m.box((11.6, 18.0, 1.6), (16.0, 24.0, 6.4), 'paint:#a07a44', 'sand hopper')
    m.box((12.4, 24.0, 2.4), (15.2, 24.6, 5.6), 'paint:#6a4a2a', 'hopper lid')
    m.cyl((13.8, 18.0, 6.4), 0.5, 10.0, 'z', 'rubber', 'sand hose')
    m.box((13.0, 14.0, 16.4), (14.6, 18.6, 17.4), 'metal:steel', 'sand nozzle')
    hs = m.surface('north', face=1.55, title='HOPPER')
    m.box((11.8, 18.6, 1.4), (15.8, 21.6, 1.6), 'paint:#8a6a3a', 'hopper door')
    m.box((12.2, 20.8, 1.35), (15.4, 21.4, 1.4), 'label:SAND', 'hopper label')
    m.ctl(hs, 'zone', 'hopper', X(13.8), 20.1, w=3.8, h=2.8, col='000000', label='')
    m.g('dispenser')
    m.box((1.4, 1.2, 2.4), (10.6, 16.0, 6.4), 'paint:#d8dcd8', 'dispenser')
    m.box((1.2, 16.0, 2.2), (10.8, 17.0, 6.6), 'paint:#2a5a3a', 'dispenser canopy')
    m.box((1.6, 16.2, 2.15), (10.4, 16.8, 2.2), 'plate:FUEL + SAND', 'canopy plate')
    m.cyl((10.6, 9.0, 4.4), 0.4, 0.8, 'x', 'paint:#202020', 'nozzle holster')
    m.box((11.2, 7.6, 3.8), (12.0, 9.6, 5.0), 'paint:#202020', 'nozzle')
    m.cyl((11.6, 3.0, 4.4), 0.35, 4.6, 'y', 'rubber', 'delivery hose')
    f = m.surface('north', face=2.38, title='DISPENSER')
    m.box((2.0, 12.0, 2.3), (10.0, 15.4, 2.4), 'paint:#101410', 'meter window')
    m.ctl(f, 'digits', 'litres', X(6.0), 14.4, w=7.4, h=1.4, col='ffd040', label='', fmt='%s L')
    m.ctl(f, 'digits', 'line1', X(6.0), 13.1, w=7.4, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(6.0), 12.4, w=7.4, h=0.6, col='8ad4ff', label='', fmt='%s')
    for i, (cid, lab) in enumerate((('tankg', 'TANK %'), ('sandg', 'HOPPER %'), ('boxg', 'SANDBOX %'))):
        m.ctl(f, 'gauge', cid, X(3.0 + i * 3.0), 10.0, w=2.2, h=2.2, col='f2f0e6', label=lab, max=100)
        m.ctl(f, 'label', '', X(3.0 + i * 3.0), 8.6, w=0, h=0, col='e8e8e8', label=lab)
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('pumpl', 'ffb020', 'PUMP', True), ('low', 'ff3030', 'LOW'), ('locol', '8ad4ff', 'LOCO')],
             X(2.4), 7.6, step=1.8, size=0.55)
    push_row(m, f, [('pump', '1fa34a', 'PUMP'), ('stop', 'd02020', 'STOP'), ('sand', 'c8a060', 'SAND'), ('fill', '2050d0', 'FILL'), ('test', 'b0b0b0', 'TEST')],
             X(2.4), 5.4, step=1.8, size=0.95)
    estop_plate(m, f, X(5.2), 3.0)
    m.box((6.6, 2.2, 2.2), (10.0, 3.8, 2.4), 'danger:NO SMOKING', 'warning plate')
    m.g('pipework')
    m.cyl((4.0, 1.2, 8.0), 0.4, 0.8, 'z', 'metal:steel', 'suction pipe')
    m.cyl((2.0, 2.0, 8.6), 0.5, 4.0, 'x', 'paint:#c82020', 'fire valve')
    m.box((0.8, 3.0, 13.0), (2.6, 6.0, 14.6), 'paint:#c82020', 'fire extinguisher')
    m.box((1.2, 6.0, 13.4), (2.2, 6.6, 14.2), 'paint:#202020', 'extinguisher head')
    m.box((0.2, 7.0, 8.6), (1.6, 10.0, 11.4), 'paint:#3a3f44', 'isolator box', west='danger:400V')
    m.cyl((0.2, 8.6, 10.0), 0.45, 0.3, 'x', 'paint:#c82020', 'isolator handle')
    m.cyl((0.9, 1.2, 10.0), 0.3, 5.8, 'y', 'cable:202020', 'armoured mains cable')
    m.terminal_12v(10.6, 4.0, 4.4, 'east')
    m.g('tank fittings')
    for y in (4.0, 10.0, 16.0):
        m.cyl((8.0, y, 11.0), 4.35, 0.5, 'y', 'paint:#1e4a2a', 'tank band')
    m.cyl((3.4, 2.0, 11.0), 0.25, 17.0, 'y', 'glass', 'sight tube')
    m.box((3.0, 1.6, 10.6), (3.8, 2.0, 11.4), 'metal:brass', 'sight tube valve')
    m.box((3.0, 19.0, 10.6), (3.8, 19.4, 11.4), 'metal:brass', 'sight tube cap')
    m.cyl((10.0, 21.2, 11.0), 0.7, 0.6, 'y', 'paint:#c8a020', 'filler cap')
    m.cyl((8.0, 21.2, 14.0), 0.3, 0.8, 'y', 'metal:steel', 'dip point')
    m.cyl((8.0, 2.0, 6.8), 0.4, 3.2, 'z', 'metal:steel', 'draw-off pipe')
    m.cyl((8.0, 2.0, 6.4), 0.6, 0.4, 'z', 'metal:brass', 'draw-off valve')
    m.g('spill kit + drain')
    m.box((12.4, 1.2, 9.0), (15.0, 4.0, 11.6), 'paint:#f0c818', 'spill kit bin')
    m.box((12.6, 3.2, 8.95), (14.8, 3.9, 9.0), 'label:SPILL KIT', 'spill label')
    m.cyl((14.0, 1.2, 15.0), 0.4, 0.6, 'z', 'metal:brass', 'bund drain valve')
    m.g('canopy lights')
    for x in (2.6, 5.6, 8.6):
        m.box((x, 15.8, 3.2), (x + 1.6, 16.0, 5.6), 'lamp:fff0c0', 'canopy light')
    m.box((1.6, 1.2, 6.4), (10.4, 1.8, 7.0), 'haz', 'dispenser kerb')
    m.box((1.0, 1.2, 1.6), (11.0, 1.4, 2.4), 'paint:#f0c818', 'stop line', faces=('up',))
    m.save(item_gui=0.4)


# ========================================================================================================= Train Wash (half)
def train_wash():
    m = Model('train_wash')
    m.g('gantry')
    for z in (2.0, 14.0):
        m.box((12.0, 0, z), (15.0, 30.0, z + 2.0), 'galv', 'gantry column')
        m.box((11.6, 0, z - 0.4), (15.4, 0.6, z + 2.4), 'metal:steel', 'column base')
        for x in (12.2, 14.8):
            m.bolt(x, 0.6, z + 1.0, 'up', 0.4)
    m.box((-16.0, 28.0, 2.0), (15.0, 30.0, 16.0), 'galv', 'gantry beam')
    m.box((-16.0, 30.0, 2.0), (15.0, 30.6, 16.0), 'paint:#3a6aa0', 'beam cladding')
    m.box((-12.0, 28.4, 1.8), (8.0, 29.6, 2.0), 'label:TRAIN WASH - 1-8 KM/H', 'gantry sign')
    m.g('brushes')
    for z in (4.6, 11.4):
        m.cyl((10.0, 2.0, z), 1.8, 24.0, 'y', 'paint:#2a62c8', 'side brush')
        m.cyl((10.0, 2.0, z), 0.5, 25.5, 'y', 'metal:steel', 'brush spindle')
        m.box((9.4, 26.0, z - 0.6), (13.0, 27.4, z + 0.6), 'paint:#3a3f44', 'brush motor')
    m.cyl((-14.0, 25.0, 8.0), 1.6, 26.0, 'x', 'paint:#2a62c8', 'roof brush')
    m.box((-15.6, 26.6, 6.6), (12.0, 28.0, 9.4), 'paint:#3a3f44', 'roof brush carriage')
    m.g('arches')
    for z, nm in ((1.0, 'pre-wet arch'), (15.4, 'rinse arch')):
        m.box((11.0, 2.0, z), (11.6, 27.0, z + 0.6), 'metal:steel', nm + ' riser')
        m.box((-16.0, 26.4, z), (11.6, 27.0, z + 0.6), 'metal:steel', nm + ' header')
        for y in range(4, 26, 4):
            m.box((10.6, y, z + 0.1), (11.0, y + 0.4, z + 0.5), 'metal:brass', 'spray nozzle')
    m.g('plant room')
    m.box((13.0, 0, 5.0), (16.0, 10.0, 13.0), 'paint:#3a6aa0', 'plant room')
    m.box((15.9, 1.0, 6.0), (16.0, 9.0, 12.0), 'paint:#2a5a90', 'plant room door', faces=('east',))
    m.cyl((14.5, 10.0, 6.5), 1.2, 4.0, 'y', 'paint:#e0e0e0', 'water tank')
    m.cyl((14.5, 10.0, 11.0), 1.0, 3.0, 'y', 'paint:#f0c818', 'detergent tank')
    m.box((13.4, 13.2, 10.2), (15.6, 13.6, 11.8), 'label:CHEM', 'chem label')
    m.g('control desk')
    m.box((12.6, 10.0, 0.4), (15.6, 18.0, 2.0), 'paint:#2b2f33', 'wash control panel')
    m.box((12.4, 18.0, 0.2), (15.8, 18.4, 2.2), 'paint:#1c1e20', 'panel hood')
    f = m.surface('north', face=0.38, title='WASH')
    m.ctl(f, 'digits', 'line1', X(14.1), 17.4, w=2.8, h=0.55, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'line2', X(14.1), 16.8, w=2.8, h=0.5, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'gauge', 'waterg', X(13.3), 15.4, w=1.3, h=1.3, col='f2f0e6', label='WATER', max=100)
    m.ctl(f, 'gauge', 'chemg', X(14.9), 15.4, w=1.3, h=1.3, col='f2f0e6', label='CHEM', max=100)
    m.ctl(f, 'digits', 'ntoday', X(14.1), 14.3, w=2.6, h=0.5, col='8ad4ff', label='', fmt='%d TODAY')
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('runl', '30e060', 'RUN')], X(13.0), 13.5, step=1.1, size=0.4, label_dv=-0.4)
    lamp_row(m, f, [('busy', 'ffb020', 'WASH', True), ('loww', 'ff3030', 'LO H2O'), ('nochem', 'ffb020', 'NO CHEM')], X(13.0), 12.5, step=1.1, size=0.4, label_dv=-0.4)
    m.ctl(f, 'push', 'start', X(13.2), 11.3, w=0.7, h=0.7, col='1fa34a', label='START')
    m.ctl(f, 'push', 'stop', X(14.2), 11.3, w=0.7, h=0.7, col='d02020', label='STOP')
    m.ctl(f, 'push', 'fill', X(15.2), 11.3, w=0.7, h=0.7, col='2050d0', label='FILL')
    m.ctl(f, 'selector', 's_prog', X(13.2), 10.5, w=0.75, h=0.75, col='202020', label='PROG', pos=['EXT', '+UNDER', '+ROOF'])
    m.ctl(f, 'selector', 's_recycle', X(14.2), 10.5, w=0.75, h=0.75, col='202020', label='RECYC', pos=['OFF', 'ON'])
    m.ctl(f, 'selector', 's_main', X(15.2), 10.5, w=0.75, h=0.75, col='202020', label='MAIN', pos=['SHUT', 'OPEN'])
    m.g('driver signal')
    m.box((11.6, 20.0, 0.6), (13.2, 25.0, 2.0), 'paint:#1a1a1a', 'signal head')
    sg = m.surface('north', face=0.58, title='SIGNAL')
    m.ctl(sg, 'lamp', 'go', X(12.4), 23.8, w=1.0, h=1.0, col='30ff60', label='GO')
    m.ctl(sg, 'lamp', 'fast', X(12.4), 22.5, w=1.0, h=1.0, col='ffb020', label='TOO FAST', blink=True)
    m.ctl(sg, 'lamp', 'stopl', X(12.4), 21.2, w=1.0, h=1.0, col='ff3030', label='STOP')
    st = m.surface('east', face=16 - 16.05, title='PLANT ROOM')
    m.box((15.6, 2.0, 2.6), (16.2, 4.0, 4.6), 'paint:#d02020', 'e-stop plate')
    m.ctl(st, 'mushroom', 'm_estop', 13.4, 3.0, w=1.3, h=1.3, col='d02020', label='E-STOP')
    m.ctl(st, 'zone', 'chem', 7.0, 5.0, w=6.0, h=8.0, col='000000', label='')
    m.box((15.9, 9.4, 5.6), (16.05, 10.2, 8.4), 'label:DOSING', 'dosing label')
    m.box((15.2, 2.0, 12.6), (16.0, 6.4, 15.0), 'paint:#3a3f44', 'isolator box', east='danger:400V')
    m.cyl((15.9, 1.0, 13.8), 0.3, 1.0, 'y', 'cable:202020', 'mains cable')
    m.terminal_12v(16.0, 6.6, 14.0, 'east')
    m.g('spray bars')
    for z in (3.6, 12.4):
        m.cyl((11.2, 3.0, z), 0.25, 22.0, 'y', 'metal:steel', 'spray bar')
        for y in range(5, 25, 4):
            m.box((10.8, y, z - 0.2), (11.2, y + 0.3, z + 0.2), 'metal:brass', 'jet')
    m.cyl((-15.0, 25.6, 3.6), 0.25, 26.0, 'x', 'metal:steel', 'roof spray bar')
    m.cyl((-15.0, 25.6, 12.4), 0.25, 26.0, 'x', 'metal:steel', 'roof spray bar')
    m.g('bristles')
    for z in (4.6, 11.4):
        for y in range(3, 26, 3):
            m.cyl((10.0, y, z), 2.1, 0.8, 'y', 'paint:#3a76e0', 'bristle ring')
    m.g('floor + drains')
    m.box((-16.0, 0, 0.6), (11.0, 0.3, 15.4), 'concrete', 'wash apron')
    for x in (-12.0, -4.0, 4.0):
        m.box((x, 0.3, 7.4), (x + 3.0, 0.35, 8.6), 'mesh', 'drain grate')
    m.box((-16.0, 0.3, 0.6), (11.0, 0.4, 1.0), 'haz', 'apron edge')
    m.g('recycling')
    m.cyl((14.4, 0, 14.0), 1.2, 4.0, 'y', 'paint:#5a8a5a', 'reclaim tank')
    m.cyl((14.4, 4.0, 14.0), 0.5, 1.0, 'y', 'paint:#2a2a2a', 'reclaim pump')
    m.cyl((13.0, 8.0, 7.0), 0.3, 6.0, 'z', 'paint:#2a62c8', 'feed pipe')
    m.g('railings + lights')
    for z in (2.6, 13.4):
        m.box((-15.0, 27.4, z), (-12.0, 28.0, z + 0.4), 'lamp:fff0c0', 'gantry light')
        m.box((-1.0, 27.4, z), (2.0, 28.0, z + 0.4), 'lamp:fff0c0', 'gantry light')
    m.box((12.0, 6.0, 1.8), (15.0, 9.0, 2.0), 'danger:DANGER|MOVING|BRUSHES', 'danger sign')
    m.bolts(12.4, 14.6, 0.6, 2.0, 3, face='north')
    m.bolts(12.4, 14.6, 0.6, 14.0, 3, face='north')
    m.save(item_gui=0.3)


# ===================================================================================================== Servicing Point
def servicing_point():
    m = Model('servicing_point')
    m.g('bollard')
    m.box((3.0, 0, 3.0), (13.0, 0.8, 13.0), 'concrete', 'plinth')
    m.box((3.6, 0.8, 3.6), (12.4, 26.0, 12.4), 'paint:#2a6aa0', 'service bollard')
    m.box((3.4, 26.0, 3.4), (12.6, 26.8, 12.6), 'paint:#1e5080', 'bollard cap')
    m.cyl((8.0, 26.8, 8.0), 0.6, 1.2, 'y', 'paint:#2b2f33', 'beacon base')
    m.cyl((8.0, 28.0, 8.0), 0.5, 1.0, 'y', 'lamp:30a0ff', 'blue beacon')
    m.box((3.8, 24.0, 3.45), (12.2, 25.4, 3.6), 'plate:CARRIAGE SERVICING', 'nameplate')
    m.g('hose reels')
    for z, col, nm in ((5.0, 'paint:#2a62c8', 'fresh water reel'), (11.0, 'paint:#6a4a2a', 'CET hose reel')):
        m.cyl((12.4, 14.0, z), 2.0, 1.6, 'x', col, nm)
        m.cyl((14.0, 14.0, z), 1.2, 0.5, 'x', 'rubber', 'wound hose')
        m.cyl((14.5, 6.0, z), 0.3, 8.0, 'y', 'rubber', 'hose drop')
    m.box((14.0, 6.0, 4.4), (15.0, 7.0, 5.6), 'metal:brass', 'water coupling')
    m.box((14.0, 6.0, 10.4), (15.0, 7.0, 11.6), 'paint:#3a3a3a', 'CET coupling')
    m.g('catering store')
    m.box((0.2, 0, 4.0), (3.4, 12.0, 12.0), 'paint:#c8ccd0', 'catering cage')
    m.box((0.1, 0.4, 4.4), (0.2, 11.6, 11.6), 'mesh', 'cage door', faces=('west',))
    m.g('control panel')
    m.box((4.4, 8.0, 3.2), (11.6, 23.6, 3.6), 'paint:#22272c', 'panel')
    f = m.surface('north', face=3.18, title='SERVICING')
    m.ctl(f, 'digits', 'line1', X(8.0), 22.8, w=6.8, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'unit', X(8.0), 22.0, w=6.8, h=0.6, col='7cf29a', label='', fmt='%s')
    for i, (cid, lab) in enumerate((('cetg', 'CET TANK'), ('foodg', 'CATERING'), ('needg', 'NEED %'))):
        m.ctl(f, 'gauge', cid, X(5.8 + i * 2.2), 19.8, w=1.8, h=1.8, col='f2f0e6', label=lab, max=100)
        m.ctl(f, 'label', '', X(5.8 + i * 2.2), 18.6, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(f, 'digits', 'ntoday', X(8.0), 17.8, w=4.0, h=0.6, col='8ad4ff', label='', fmt='%d TODAY')
    for i, (pid, lid, col, lab) in enumerate((('water', 'l_water', '2050d0', 'WATER'), ('cetp', 'l_cetp', '8a6a3a', 'CET'), ('clean', 'l_clean', '30c060', 'CLEAN'), ('cater', 'l_cater', 'f0d020', 'CATER'))):
        u = X(5.6 + i * 1.6)
        m.ctl(f, 'lamp', lid, u, 16.6, w=0.5, h=0.5, col='ffb020', label=lab)
        m.ctl(f, 'push', pid, u, 15.4, w=0.95, h=0.95, col=col, label=lab)
        m.ctl(f, 'label', '', u, 14.6, w=0, h=0, col='e8e8e8', label=lab)
    push_row(m, f, [('all', '1fa34a', 'ALL'), ('stop', 'd02020', 'STOP'), ('discharge', '6a4a2a', 'DISCH'), ('test', 'b0b0b0', 'TEST')], X(5.6), 13.0, step=1.6, size=0.9)
    m.ctl(f, 'selector', 's_auto', X(6.4), 10.6, w=1.2, h=1.2, col='202020', label='MODE', pos=['MANUAL', 'AUTO'])
    m.ctl(f, 'label', '', X(6.4), 9.6, w=0, h=0, col='e8e8e8', label='LAYOVER')
    lamp_row(m, f, [('pwr', '30e060', '12V'), ('mainsl', '30e060', 'MAINS'), ('main', '2a8aff', 'H2O MAIN'), ('full', 'ff3030', 'CET FULL', True)], X(8.6), 10.8, step=1.0, size=0.45, label_dv=-0.45)
    cg = m.surface('north', face=3.95, title='CATERING')
    m.box((0.4, 2.0, 3.85), (3.2, 10.0, 4.0), 'paint:#d8dcd8', 'cage door front')
    m.box((0.6, 8.8, 3.8), (3.0, 9.6, 3.85), 'label:CATERING', 'cage label')
    m.ctl(cg, 'zone', 'stock', X(1.8), 6.0, w=2.6, h=7.6, col='000000', label='')
    m.box((12.4, 1.0, 12.4), (14.0, 5.0, 14.0), 'paint:#3a3f44', 'isolator box', south='danger:400V')
    m.cyl((13.2, 0.0, 13.2), 0.3, 1.0, 'y', 'cable:202020', 'mains cable')
    m.terminal_12v(8.0, 1.6, 12.4, 'south')
    m.g('water + CET pipework')
    m.cyl((5.0, 0.8, 12.6), 0.4, 24.0, 'y', 'paint:#2a62c8', 'water riser')
    m.cyl((11.0, 0.8, 12.6), 0.5, 18.0, 'y', 'paint:#6a4a2a', 'CET riser')
    m.cyl((5.0, 18.0, 12.6), 0.4, 6.0, 'x', 'paint:#2a62c8', 'water header')
    m.cyl((5.0, 18.0, 13.2), 0.6, 0.6, 'z', 'metal:brass', 'water valve')
    m.box((4.9, 18.0, 13.8), (5.1, 19.2, 14.0), 'paint:#2a62c8', 'valve wheel')
    m.cyl((11.0, 12.0, 13.2), 1.0, 1.4, 'z', 'paint:#3a3a3a', 'CET pump')
    m.cyl((11.0, 12.0, 14.6), 0.7, 1.0, 'z', 'paint:#2a2a2a', 'CET pump motor')
    m.box((3.8, 20.0, 12.4), (12.2, 21.4, 12.6), 'label:POTABLE WATER - CET', 'pipe label', faces=('south',))
    m.g('bollards')
    for x, z in ((1.0, 1.0), (15.0, 1.0), (1.0, 15.0), (15.0, 15.0)):
        m.cyl((x, 0, z), 0.6, 6.0, 'y', 'paint:#f0c818', 'protection bollard')
        m.cyl((x, 6.0, z), 0.65, 0.4, 'y', 'paint:#202020', 'bollard cap')
        m.box((x - 0.62, 3.0, z - 0.62), (x + 0.62, 3.6, z + 0.62), 'haz', 'bollard band')
    m.g('drip tray + standpipe')
    m.box((13.6, 0, 3.6), (16.0, 0.6, 12.4), 'metal:steel', 'drip tray')
    m.cyl((15.0, 0.6, 8.0), 0.5, 4.0, 'y', 'metal:brass', 'standpipe')
    m.cyl((15.0, 4.6, 8.0), 0.7, 0.3, 'y', 'paint:#2a62c8', 'standpipe cap')
    m.g('side vents + light')
    m.louvres(5.0, 11.0, 2.0, 7.0, 3.6, n=4)
    m.box((3.6, 22.0, 6.0), (3.4, 24.0, 10.0), 'lamp:fff0c0', 'flood light', faces=('west',))
    m.box((3.4, 21.6, 5.6), (3.6, 24.4, 10.4), 'paint:#2b2f33', 'light housing')
    m.bolts(4.0, 12.0, 0.8, 3.6, 5, face='north')
    m.box((12.4, 22.0, 4.0), (12.6, 25.0, 12.0), 'danger:DANGER|SEWAGE|CET', 'cet sign', faces=('east',))
    m.save(item_gui=0.4)


# ============================================================================================================ items etc.
SPARES = [('brake_blocks', (90, 90, 96), 'block'), ('wheelset', (150, 154, 160), 'wheel'), ('axle_bearing', (200, 170, 80), 'ring'),
          ('traction_motor', (60, 90, 140), 'motor'), ('power_kit', (150, 60, 40), 'box'), ('control_card', (30, 120, 60), 'card'),
          ('coupler_knuckle', (70, 70, 74), 'hook'), ('brake_hose', (24, 24, 26), 'hose'), ('filter_set', (220, 180, 40), 'box'),
          ('bogie', (40, 40, 44), 'bogie'), ('bogie_defective', (110, 60, 40), 'bogie')]


def spare_textures():
    d = os.path.join(ASSETS, 'textures/items')
    os.makedirs(d, exist_ok=True)
    for name, c, kind in SPARES:
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        dk = tuple(max(0, v - 50) for v in c) + (255,)
        lt = tuple(min(255, v + 50) for v in c) + (255,)
        cc = tuple(c) + (255,)
        px = im.putpixel
        if kind == 'block':
            for y in range(5, 12):
                for x in range(2, 14): px((x, y), lt if y == 5 else dk if y == 11 else cc)
            for x in range(3, 13, 3): px((x, 8), dk)
        elif kind in ('wheel', 'ring'):
            for y in range(16):
                for x in range(16):
                    r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                    if 3 <= r <= 7: px((x, y), lt if r > 6.2 else cc)
                    elif r < 2 and kind == 'wheel': px((x, y), dk)
        elif kind == 'motor':
            for y in range(4, 13):
                for x in range(2, 13): px((x, y), lt if y == 4 else dk if (x % 2 == 0) else cc)
            for x in range(13, 15): px((x, 8), (180, 180, 186, 255))
        elif kind == 'card':
            for y in range(3, 13):
                for x in range(2, 14): px((x, y), cc)
            for x in range(4, 12, 2): px((x, 6), (20, 20, 20, 255)); px((x, 9), (200, 170, 60, 255))
            for x in range(3, 13): px((x, 12), (210, 180, 60, 255))
        elif kind == 'hook':
            for y in range(3, 13):
                for x in range(4, 9): px((x, y), cc)
            for x in range(8, 13):
                px((x, 3), cc); px((x, 4), cc)
            for y in range(4, 8): px((12, y), lt)
        elif kind == 'hose':
            for i in range(14):
                x, y = 1 + i, 8 + int(3 * __import__('math').sin(i / 2.2))
                px((x, y), cc); px((x, y + 1), dk)
            for y in range(6, 12): px((14, y), (200, 170, 60, 255))
        elif kind == 'box':
            for y in range(4, 13):
                for x in range(3, 13): px((x, y), lt if y == 4 else dk if x == 12 else cc)
            for x in range(5, 11): px((x, 8), (240, 240, 230, 255))
        elif kind == 'bogie':
            for x in range(2, 14):
                for y in range(5, 9): px((x, y), cc)
            for cx in (4, 11):
                for y in range(16):
                    for x in range(16):
                        if ((x - cx) ** 2 + (y - 10) ** 2) ** 0.5 <= 2.6: px((x, y), lt)
        im.save(os.path.join(d, 'spare_' + name + '.png'))
        json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/spare_' + name}}, open(os.path.join(ASSETS, 'models/item/spare_' + name + '.json'), 'w'), indent=1)


P = lambda i: {'item': 'irextras:part', 'data': i}       # ItemPart ordinals
PART = {'plate': 0, 'frame': 1, 'circuit': 2, 'motor': 3, 'hydraulic': 4, 'gearbox': 5, 'fan': 7, 'bearing': 9, 'shaft': 10, 'spring': 11, 'seal': 12,
        'winding': 13, 'screen': 20, 'movement': 21}
RECIPES = {
    'depot_office': (['SLS', 'CDC', 'PBP'], {'S': P(PART['screen']), 'L': 'minecraft:redstone_lamp', 'C': P(PART['circuit']), 'D': 'irextras:maintenance_depot', 'P': ('minecraft:planks', 0), 'B': 'minecraft:bookshelf'}),
    'depot_stores': (['ICI', 'CBC', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest', 'B': P(PART['screen'])}),
    'tool_crib': (['BBB', 'TCT', 'IKI'], {'B': 'minecraft:iron_bars', 'T': 'minecraft:chest', 'C': 'minecraft:crafting_table', 'I': 'minecraft:iron_ingot', 'K': 'minecraft:tripwire_hook'}),
    'crew_desk': (['SLS', 'PCP', 'WBW'], {'S': P(PART['screen']), 'L': 'minecraft:clock', 'P': 'minecraft:paper', 'C': P(PART['circuit']), 'W': ('minecraft:planks', 0), 'B': 'minecraft:chest'}),
    'wheel_lathe': (['CSC', 'GMG', 'FFF'], {'C': P(PART['circuit']), 'S': P(PART['shaft']), 'G': P(PART['gearbox']), 'M': P(PART['motor']), 'F': P(PART['frame'])}),
    'bogie_drop': (['HCH', 'HFH', 'PMP'], {'H': P(PART['hydraulic']), 'C': P(PART['circuit']), 'F': P(PART['frame']), 'P': P(PART['plate']), 'M': P(PART['motor'])}),
    'brake_test': (['GCG', 'VRV', 'IFI'], {'G': P(PART['movement']), 'C': P(PART['circuit']), 'V': P(PART['seal']), 'R': 'minecraft:bucket', 'I': 'minecraft:iron_ingot', 'F': P(PART['frame'])}),
    'fuel_point': (['SHS', 'TMT', 'PCP'], {'S': ('minecraft:sand', 0), 'H': 'minecraft:hopper', 'T': 'minecraft:cauldron', 'M': P(PART['motor']), 'P': P(PART['plate']), 'C': P(PART['circuit'])}),
    'train_wash': (['PPP', 'WMW', 'FCF'], {'P': P(PART['plate']), 'W': ('minecraft:wool', 0), 'M': P(PART['motor']), 'F': P(PART['frame']), 'C': P(PART['circuit'])}),
    'servicing_point': (['BCB', 'HMH', 'PFP'], {'B': 'minecraft:bucket', 'C': P(PART['circuit']), 'H': P(PART['seal']), 'M': P(PART['motor']), 'P': P(PART['plate']), 'F': P(PART['frame'])}),
}
SPARE_RECIPES = [  # (spare id, count, pattern, key)
    ('brake_blocks', 4, ['II', 'CC'], {'I': 'minecraft:iron_ingot', 'C': ('minecraft:clay_ball', None)}),
    ('wheelset', 1, ['I I', 'ISI', 'I I'], {'I': 'minecraft:iron_block', 'S': P(PART['shaft'])}),
    ('axle_bearing', 2, [' B ', 'BSB', ' B '], {'B': P(PART['bearing']), 'S': P(PART['seal'])}),
    ('traction_motor', 1, ['WMW', 'IBI'], {'W': P(PART['winding']), 'M': P(PART['motor']), 'I': 'minecraft:iron_ingot', 'B': P(PART['bearing'])}),
    ('power_kit', 1, ['GSG', 'IPI'], {'G': P(PART['gearbox']), 'S': P(PART['seal']), 'I': 'minecraft:iron_ingot', 'P': 'minecraft:piston'}),
    ('control_card', 2, ['RCR', 'GQG'], {'R': 'minecraft:redstone', 'C': P(PART['circuit']), 'G': 'minecraft:gold_nugget', 'Q': 'minecraft:quartz'}),
    ('coupler_knuckle', 2, ['II ', 'IBI', ' II'], {'I': 'minecraft:iron_ingot', 'B': 'minecraft:iron_block'}),
    ('brake_hose', 4, ['L L', ' S '], {'L': 'minecraft:leather', 'S': P(PART['seal'])}),
    ('filter_set', 4, ['PSP', 'PSP'], {'P': 'minecraft:paper', 'S': 'minecraft:string'}),
    ('bogie', 1, ['BWB', 'FSF', 'BWB'], {'B': 'irextras:spare:2', 'W': 'irextras:spare:1', 'F': P(PART['frame']), 'S': P(PART['spring'])}),
]


def ing(v):
    if isinstance(v, dict): return v
    if isinstance(v, tuple): return {'item': v[0]} if v[1] is None else {'item': v[0], 'data': v[1]}
    if v.startswith('irextras:spare:'): return {'item': 'irextras:spare', 'data': int(v.split(':')[2])}
    return {'item': v}


LANG = {
    'tile.irextras.depot_office.name': 'Depot Control Office', 'tile.irextras.depot_stores.name': 'Depot Stores', 'tile.irextras.tool_crib.name': 'Tool Crib',
    'tile.irextras.crew_desk.name': 'Crew Booking-On Desk', 'tile.irextras.wheel_lathe.name': 'Underfloor Wheel Lathe', 'tile.irextras.bogie_drop.name': 'Bogie Drop',
    'tile.irextras.brake_test.name': 'Brake Test Rig', 'tile.irextras.fuel_point.name': 'Fuelling and Sanding Point', 'tile.irextras.train_wash.name': 'Train Wash Plant',
    'tile.irextras.servicing_point.name': 'Carriage Servicing Point', 'item.irextras.spare.name': 'Rolling Stock Spare',
    'tile.irextras.platform_panel.name': 'Platform Allocation Panel', 'tile.irextras.station_lift.name': 'Station Lift',
    'tile.irextras.help_point.name': 'Help Point', 'tile.irextras.tactile_blister.name': 'Tactile Paving (Blister)',
    'tile.irextras.tactile_edge.name': 'Tactile Paving (Platform Edge)', 'tile.irextras.station_toilet.name': 'Accessible Toilet',
    'tile.irextras.vending_machine.name': 'Vending Machine', 'tile.irextras.retail_kiosk.name': 'Retail Kiosk', 'tile.irextras.platform_lamp.name': 'Platform Lamp',
    'tile.irextras.platform_heater.name': 'Waiting Shelter Heater', 'tile.irextras.escalator.name': 'Escalator',
    'tile.irextras.facilities_panel.name': 'Station Facilities Panel',
}

BLOCKS = ['depot_office', 'depot_stores', 'tool_crib', 'crew_desk', 'wheel_lathe', 'bogie_drop', 'brake_test', 'fuel_point', 'train_wash', 'servicing_point']

if __name__ == '__main__':
    depot_office(); depot_stores(); tool_crib(); crew_desk(); wheel_lathe(); bogie_drop(); brake_test(); fuel_point(); train_wash(); servicing_point()
    for n in BLOCKS:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    spare_textures()
    for n, (pat, key) in RECIPES.items():
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    ids = [s[0] for s in SPARES]
    for sid, cnt, pat, key in SPARE_RECIPES:
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()},
                   'result': {'item': 'irextras:spare', 'data': ids.index(sid), 'count': cnt}}, open(os.path.join(ASSETS, 'recipes/spare_%s.json' % sid), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# depots, rolling stock, crew and station facilities (her list 8 + 12)\n' + '\n'.join(add) + '\n')
    print('shelf spots (model px, for the tiles\' SPOTS):')
    for k, v in SPOTS.items(): print(' ', k, v)
