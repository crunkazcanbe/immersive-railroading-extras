#!/usr/bin/env python3
"""The world + business layer's furniture (her ChatGPT "next 48" list #12-15, #17, #18, #32-38): Port Berth with its
ship-to-shore crane, the Railway Stock Exchange board, the Regulator Office, the Insurance Desk and the Town Hall
Planning Board - three-block-wide desks (BlockEconomyDesk / BlockWideDesk) with live controls, real storages behind the
drawer / cabinet / cupboard fronts and shelves that show what stands on them. Also adds the project-management
programme board to the Construction Site Office and the APPT / WASH buttons to the Container Terminal gate desk
(those two models are rebuilt by gen_bb_industry.py / gen_bb_economy.py, which import the helpers from here).
Models face north; controls are placed by u = 16 - x. Live controls: biz/Tile*.java (ids must match).
Writes blockstates, item models, recipes and lang lines; prints the shelf spots for the tiles' shelfSpots()."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_economy import back_wall, led_board, desk, strip, buttons, hood, monitor, cupboard_shelf

X = lambda x: 16 - x
SPOTS = {}


def lamps(m, sf, items, u0, v, step=1.3, size=0.6, dv=-0.55):
    for i, it in enumerate(items):
        cid, col, lab = it[0], it[1], it[2]
        u = u0 + i * step
        m.ctl(sf, 'lamp', cid, u, v, w=size, h=size, col=col, label=lab, **({'blink': True} if len(it) > 3 and it[3] else {}))
        m.ctl(sf, 'label', '', u, v + dv, w=0, h=0, col='e8e8e8', label=lab)


def filing_cabinet(m, x0, x1, top, zid, title, body='ral7035', front='#b9bcb8', drawers=4):
    """a steel filing cabinet against the back wall: drawer fronts with label holders and pulls; one storage zone"""
    m.g(title.lower())
    m.box((x0, 0, 9.4), (x1, top, 15.2), body, 'filing cabinet')
    h = (top - 0.8) / drawers
    for k in range(drawers):
        y0 = 0.4 + k * h
        m.box((x0 + 0.3, y0, 9.2), (x1 - 0.3, y0 + h - 0.25, 9.4), 'paint:' + front, 'cabinet drawer')
        xc = (x0 + x1) / 2
        m.box((xc - 1.0, y0 + h - 1.3, 9.12), (xc + 1.0, y0 + h - 0.7, 9.2), 'label:' + title.split()[0], 'label holder')
        m.box((xc - 0.9, y0 + 0.5, 8.9), (xc + 0.9, y0 + 0.8, 9.2), 'metal:alu', 'drawer pull')
    m.box((x0, top, 9.4), (x1, top + 0.3, 15.2), 'metal:steel', 'cabinet top')
    sf = m.surface('north', face=9.1, title=title)
    m.ctl(sf, 'zone', zid, X((x0 + x1) / 2), top / 2, w=x1 - x0 - 0.4, h=top - 0.6, col='000000', label='')


def plant_pots(m, x, z):
    m.box((x - 0.8, 0, z - 0.8), (x + 0.8, 1.6, z + 0.8), 'paint:#8a4a2a', 'plant pot')
    m.box((x - 0.6, 1.6, z - 0.6), (x + 0.6, 1.7, z + 0.6), 'paint:#3a2a1a', 'soil')
    for dx, dz, hh in ((-0.3, 0, 3.4), (0.3, 0.2, 4.0), (0, -0.3, 3.0)):
        m.box((x + dx - 0.4, 1.7, z + dz - 0.4), (x + dx + 0.4, 1.7 + hh, z + dz + 0.4), 'paint:#2f7a3a', 'leaves')


# ========================================================================================================= Port Berth
def port_berth():
    m = Model('port_berth')
    m.g('quay apron')
    m.box((-16, 0, 0.2), (32, 0.4, 16), 'concrete', 'quay paving')
    m.box((-16, 0.4, 15.0), (32, 1.2, 16), 'paint:#2a2a2a', 'quay edge fender')
    for x in range(-14, 31, 6):
        m.box((x, 0.4, 15.2), (x + 1.4, 2.4, 15.9), 'paint:#1a1a1a', 'rubber fender')
    m.box((-16, 0.4, 2.0), (32, 0.45, 2.4), 'paint:ral1023', 'yellow safety line')
    for x in (-13.0, 26.0):
        m.cyl((x, 0.4, 14.0), 0.7, 1.2, 'y', 'paint:#2a2a2a', 'bollard')
        m.cyl((x, 1.6, 14.0), 0.95, 0.3, 'y', 'paint:#2a2a2a', 'bollard cap')
    # the STS crane: portal legs, sill beams, portal beam, boom, A-frame, trolley, ropes, spreader, cab
    m.g('crane portal')
    for x in (-13.4, 27.6):
        for z in (9.4, 14.0):
            m.box((x, 0.4, z), (x + 1.6, 26.0, z + 1.4), 'paint:#d03a2a', 'portal leg')
            m.box((x - 0.2, 0.4, z - 0.2), (x + 1.8, 1.6, z + 1.6), 'paint:#2a2a2a', 'leg bogie')
        m.box((x, 12.0, 9.4), (x + 1.6, 13.0, 15.4), 'paint:#d03a2a', 'sill brace')
        m.box((x, 22.0, 9.4), (x + 1.6, 23.0, 15.4), 'paint:#d03a2a', 'portal tie')
        m.box((x + 0.2, 13.0, 11.6), (x + 1.4, 22.0, 12.4), 'paint:#b02a1a', 'leg diagonal', rot={'axis': 'x', 'angle': 22.5, 'origin': [x + 0.8, 17.5, 12.0]})
        for y in range(2, 25, 2):
            m.box((x + 1.6, y, 14.0), (x + 1.8, y + 0.2, 15.4), 'metal:steel', 'ladder rung')
    m.box((-13.4, 26.0, 9.4), (29.2, 27.8, 15.4), 'paint:#d03a2a', 'portal beam')
    m.box((-13.4, 27.8, 11.4), (29.2, 28.2, 13.4), 'mesh', 'walkway grating')
    m.handrail([(-13.0, 11.2), (28.8, 11.2)], 27.8, h=1.8, r=0.12, mat='paint:ral1023')
    m.g('boom')
    for x in (4.0, 11.0):
        m.box((x, 28.2, -2.0), (x + 1.0, 29.6, 32.0), 'paint:#d03a2a', 'boom girder')
    for z in range(-1, 32, 4):
        m.box((4.0, 28.4, z), (12.0, 28.8, z + 0.5), 'paint:#b02a1a', 'boom cross member')
    m.box((5.0, 28.2, 30.6), (11.0, 29.6, 32.0), 'paint:#d03a2a', 'boom tip')
    m.box((3.6, 28.0, 30.0), (12.4, 28.4, 31.8), 'lamp:fff0c0', 'boom floodlight')
    m.g('a-frame')
    for x in (4.2, 11.0):
        m.box((x, 29.6, 12.0), (x + 0.8, 32.0, 12.8), 'paint:#d03a2a', 'apex leg')
    m.box((4.2, 31.2, 12.0), (11.8, 32.0, 12.8), 'paint:#d03a2a', 'apex head')
    m.box((7.6, 30.4, 13.0), (8.4, 31.0, 30.0), 'metal:steel', 'forestay', rot={'axis': 'x', 'angle': -22.5, 'origin': [8.0, 30.7, 21.0]})
    m.box((7.6, 30.4, -1.0), (8.4, 31.0, 12.0), 'metal:steel', 'backstay', rot={'axis': 'x', 'angle': 22.5, 'origin': [8.0, 30.7, 5.0]})
    m.box((5.0, 29.6, 2.0), (11.0, 31.6, 7.0), 'paint:#e8e0d0', 'machinery house')
    m.box((5.2, 30.2, 1.9), (10.8, 31.2, 2.0), 'louvre', 'machinery house louvre')
    m.box((5.4, 29.8, 1.95), (10.6, 30.2, 2.0), 'label:PRIDE PORT', 'machinery house name')
    m.g('trolley + spreader')
    m.box((4.6, 27.0, 20.0), (11.4, 28.2, 24.0), 'paint:#f0c020', 'trolley')
    for x in (5.0, 10.4):
        for z in (20.4, 23.0):
            m.cyl((x, 27.6, z), 0.5, 0.6, 'x', 'metal:steel', 'trolley wheel')
    for x in (5.6, 10.0):
        m.box((x, 17.6, 21.6), (x + 0.2, 27.0, 21.8), 'cable:2a2a2a', 'hoist rope')
        m.box((x, 17.6, 22.6), (x + 0.2, 27.0, 22.8), 'cable:2a2a2a', 'hoist rope')
    m.box((3.0, 16.6, 20.6), (13.0, 17.6, 23.8), 'paint:#f0c020', 'spreader frame')
    m.box((3.4, 16.2, 21.0), (12.6, 16.6, 23.4), 'paint:#c8a018', 'spreader beam')
    for (x, z) in ((3.2, 20.8), (12.4, 20.8), (3.2, 23.4), (12.4, 23.4)):
        m.box((x - 0.2, 15.6, z - 0.2), (x + 0.2, 16.2, z + 0.2), 'metal:steel', 'twistlock')
    m.box((3.2, 11.6, 20.8), (12.8, 16.0, 23.6), 'paint:#1f5fa8', 'box on the spreader')
    for x in range(4, 13, 1):
        m.box((x, 11.8, 20.7), (x + 0.4, 15.8, 20.8), 'paint:#174a85', 'container corrugation')
    m.box((5.0, 13.2, 20.65), (11.0, 14.4, 20.7), 'label:PLNU 123456 7', 'box number')
    m.g('cab')
    m.box((12.6, 22.6, 14.0), (16.6, 26.0, 18.0), 'paint:#e8e0d0', 'operator cab')
    m.box((12.7, 23.4, 13.9), (16.5, 25.6, 14.0), 'glass', 'cab windscreen')
    m.box((12.7, 22.7, 14.1), (16.5, 22.9, 17.9), 'glass', 'cab floor window', faces=('down',))
    m.box((13.0, 22.2, 14.4), (16.2, 22.6, 17.6), 'paint:#2a2a2a', 'cab hanger')
    # the transit sheds (left wing): two corrugated roller-door cabinets
    m.g('transit sheds')
    for k, (x0, x1, zid, lab) in enumerate(((-12.0, -7.2, 'shed1', 'TRANSIT SHED A'), (-7.0, -2.2, 'shed2', 'TRANSIT SHED B'))):
        m.box((x0, 0.4, 3.0), (x1, 9.0, 8.8), 'paint:#8a96a0', 'shed body')
        m.box((x0, 9.0, 2.8), (x1, 9.4, 9.0), 'paint:#5a646c', 'shed roof')
        for y in range(1, 8):
            m.box((x0 + 0.4, 0.4 + y, 2.85), (x1 - 0.4, 0.6 + y, 3.0), 'paint:#6a7680', 'roller door slat')
        m.box((x0 + 0.5, 7.8, 2.8), (x1 - 0.5, 8.6, 2.9), 'label:' + lab.split()[-1], 'shed letter')
        m.box((x0 + 1.6, 1.0, 2.7), (x1 - 1.6, 1.4, 2.9), 'metal:alu', 'door bottom handle')
        sh = m.surface('north', face=2.7, title=lab)
        m.ctl(sh, 'zone', zid, X((x0 + x1) / 2), 4.6, w=x1 - x0 - 0.6, h=8.0, col='000000', label='')
    m.box((-12.0, 9.4, 3.2), (-2.2, 10.6, 3.4), 'plate:TRANSIT SHEDS', 'shed sign')
    # port office: manifest cupboard + shelf (right wing)
    SPOTS['port_berth'] = cupboard_shelf(m, 18.0, 27.2, ['office'], 4, title='MANIFEST SHELF', body='#34444f', door='#46596a')
    m.box((18.4, 9.6, 15.0), (27.0, 12.6, 15.4), 'paint:#10304a', 'office sign', north='plate:PORT OFFICE')
    # the anemometer mast + wind panel (far right)
    m.g('anemometer')
    m.box((30.4, 0.4, 3.0), (31.0, 30.0, 3.6), 'galv', 'anemometer mast')
    m.box((29.6, 30.0, 2.6), (31.8, 30.3, 4.0), 'galv', 'cup arm')
    for (x, z) in ((29.6, 2.6), (31.4, 2.6), (30.5, 3.8)):
        m.box((x, 29.8, z), (x + 0.5, 30.4, z + 0.5), 'paint:#d8d8d4', 'anemometer cup')
    m.box((30.6, 30.4, 3.6), (30.8, 31.6, 5.6), 'paint:#d8d8d4', 'wind vane')
    m.box((28.0, 12.6, 2.4), (31.4, 17.4, 3.2), 'paint:#2a2f34', 'wind panel')
    wp = m.surface('north', face=2.38, title='WIND')
    m.ctl(wp, 'gauge', 'windg', X(29.7), 15.6, w=2.6, h=2.6, col='f2f0e6', label='WIND M/S', max=30)
    m.ctl(wp, 'digits', 'windd', X(29.7), 13.5, w=3.0, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(wp, 'lamp', 'wind', X(28.6), 17.0, w=0.5, h=0.5, col='ff3030', label='HIGH WIND', blink=True)
    # the quay operator's desk (centre)
    desk(m, {}, body='#2b3a44', front='#3a4c58', pull='metal:alu')
    sf = strip(m, 3.6, 8.4, col='#1e2226')
    buttons(m, sf, 4.8, [('prev', '1a1a1a', 'PREV'), ('next', '1a1a1a', 'NEXT'), ('disch', '1fa34a', 'DISCH'), ('load', '2050d0', 'LOAD'),
                         ('toterm', 'e08020', 'TO RAIL'), ('seal', 'f0d020', 'SEAL'), ('wash', '20a0a0', 'WASH'), ('manifest', 'f0f0f0', 'MANIF')], u0=0.9, step=1.28, size=0.85)
    buttons(m, sf, 7.4, [('call', '8040c0', 'CALL'), ('sail', 'd02020', 'SAIL'), ('totruck', 'e08020', 'TRK OUT'), ('fromtruck', '1fa34a', 'TRK IN'),
                         ('appt', 'b0b0b0', 'APPT')], u0=0.9, step=1.28, size=0.85)
    m.ctl(sf, 'key', 'k_crane', 11.6, 5.0, w=1.1, h=1.1, col='c0a040', label='CRANE', pos=['OFF', 'ON'])
    m.ctl(sf, 'mushroom', 'estop', 14.4, 5.0, w=1.2, h=1.2, col='d02020', label='E-STOP')
    m.ctl(sf, 'lever', 'boom', 13.0, 7.4, w=1.0, h=1.4, col='202020', label='BOOM', pos=['UP', 'DOWN'])
    m.ctl(sf, 'label', '', 13.0, 6.5, w=0, h=0, col='e8e8e8', label='BOOM UP/DN')
    hd = hood(m, col='#1b2025')
    lamps(m, hd, [('pw', '30e060', '12V'), ('craneon', '30e060', 'CRANE'), ('ship', '8ad4ff', 'SHIP'), ('boomdn', 'ffb020', 'BOOM DN'), ('busy', 'ffb020', 'BUSY', True),
                  ('lockd', '30e060', 'LOCKED'), ('cutoff', 'ff3030', 'CUTOFF', True), ('full', 'ff3030', 'QUAY FULL'), ('demur', 'ffb020', 'DEMURR')], 1.0, 11.0, step=1.35, size=0.55)
    m.ctl(hd, 'digits', 'cstat', 3.8, 9.7, w=6.6, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(hd, 'digits', 'mph', 9.6, 9.7, w=4.4, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(hd, 'gauge', 'loadg', 14.2, 10.4, w=2.0, h=2.0, col='f2f0e6', label='LOAD T', max=40)
    monitor(m, 0.6, 7.8, 12.2, 17.6, 10.2, ['sname', 'seta', 'sman', 'box', 'cargo', 'qual'], cols=['ffd040', '8ad4ff', '7cf29a', 'f0f0f0', 'b0b0b0', 'ffb020'])
    monitor(m, 8.2, 15.4, 12.2, 17.6, 10.2, ['q1', 'q2', 'q3', 'q4', 'fees'], cols=['7cf29a', '7cf29a', '7cf29a', '7cf29a', 'ffd040'])
    m.g('desk things')
    m.box((0.4, 9.0, 11.6), (2.4, 9.3, 12.6), 'paint:#f4f0d8', 'bills of lading')
    m.box((14.6, 9.0, 11.4), (15.6, 10.6, 12.4), 'paint:#1a1a1a', 'vhf radio')
    m.box((15.0, 10.6, 11.8), (15.15, 12.4, 11.95), 'paint:#1a1a1a', 'radio aerial')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.22)


# ================================================================================================= Stock Exchange Board
def stock_exchange():
    m = Model('stock_exchange')
    back_wall(m, panel='#1a2a40', plaster='#e8ecf2')
    m.g('quotation board')
    led_board(m, -2.6, 30, 11.6, 29.0)
    m.box((2, 29.4, 14.7), (26, 31.4, 15.4), 'paint:#0c1626', 'header board')
    m.box((2.4, 29.6, 14.62), (25.6, 31.2, 14.7), 'label:PRIDE RAILWAY STOCK EXCHANGE', 'header lettering', north='plate:PRIDE RAILWAY STOCK EXCHANGE')
    t = m.surface('north', face=14.42, title='QUOTATIONS')
    m.ctl(t, 'digits', 'idx', X(-0.4), 30.4, w=4.2, h=1.2, col='8ad4ff', label='', fmt='%s')
    m.ctl(t, 'digits', 'time', X(28.0), 30.4, w=3.6, h=1.2, col='ffd040', label='', fmt='%s')
    m.box((-2.0, 27.4, 14.45), (29.4, 27.9, 14.5), 'label:STOCK - LAST - MOVE - DAY', 'column heads', north='label:STOCK - LAST - MOVE - DAY')
    for r in range(1, 9):
        v = 26.4 - (r - 1) * 1.85
        m.ctl(t, 'digits', 'r%dn' % r, X(5.6), v, w=15.6, h=1.5, col='ffb020', label='', fmt='%s')
        m.ctl(t, 'digits', 'r%dp' % r, X(17.4), v, w=7.4, h=1.5, col='f0f0f0', label='', fmt='%s')
        m.ctl(t, 'lamp', 'r%du' % r, X(22.4), v, w=0.8, h=0.8, col='30e060', label='')
        m.ctl(t, 'lamp', 'r%dd' % r, X(23.6), v, w=0.8, h=0.8, col='ff3030', label='')
        m.ctl(t, 'digits', 'r%dc' % r, X(27.0), v, w=4.6, h=1.5, col='7cf29a', label='', fmt='%s')
    m.ctl(t, 'gauge', 'idxg', X(28.0), 13.4, w=2.6, h=2.6, col='f2f0e6', label='INDEX', max=2000)
    # hall of fame: the trophy board (operational mastery, #37)
    m.g('hall of fame')
    m.box((-14, 12.0, 14.9), (-3.4, 29.0, 15.4), 'paint:#2a1a0c', 'trophy board')
    m.box((-13.6, 27.0, 14.84), (-3.8, 28.6, 14.9), 'plate:HALL OF FAME', 'trophy title')
    for i in range(4):
        y = 24.6 - i * 3.0
        m.box((-13.4, y - 1.0, 14.8), (-4.0, y + 1.2, 14.9), 'paint:#b8902e', 'brass plaque')
    hf = m.surface('north', face=14.78, title='HALL OF FAME')
    for i in range(4):
        m.ctl(hf, 'digits', 't%d' % (i + 1), X(-8.7), 24.6 - i * 3.0, w=9.0, h=1.6, col='1a1a1a', label='', fmt='%s')
    for (x, y) in ((-12.0, 13.4), (-9.0, 13.4), (-6.0, 13.4)):
        m.cyl((x, y, 14.4), 0.6, 0.3, 'y', 'metal:brass', 'cup base')
        m.box((x - 0.15, y + 0.3, 14.25), (x + 0.15, y + 1.2, 14.55), 'metal:brass', 'cup stem')
        m.cyl((x, y + 1.2, 14.4), 0.7, 1.2, 'y', 'metal:brass', 'trophy cup')
    # the share certificate cabinet (left) and the cash safe + trophy shelf (right)
    filing_cabinet(m, -13.4, -4.0, 10.6, 'certs', 'SHARE CERTIFICATES', body='ral7016', front='#4a5258')
    SPOTS['stock_exchange'] = cupboard_shelf(m, 18.4, 29.4, ['cash'], 5, title='TROPHY SHELF', body='#1a2a40', door='#26405e')
    m.box((19.0, 1.0, 11.3), (21.0, 1.4, 11.45), 'label:CASH', 'cash plate')
    # the dealing desk
    desk(m, {}, body='#1a2a40', front='#26405e', pull='metal:alu')
    sf = strip(m, 3.6, 8.4, col='#161c22')
    buttons(m, sf, 4.6, [('prev', '1a1a1a', 'PREV'), ('next', '1a1a1a', 'NEXT'), ('qtydn', '404040', 'QTY-'), ('qtyup', '404040', 'QTY+'), ('pxdn', '404040', 'PX-'),
                         ('pxup', '404040', 'PX+'), ('buy', '1fa34a', 'BUY'), ('sell', 'd02020', 'SELL'), ('cancel', 'e08020', 'CANCEL')], u0=0.9, step=1.25, size=0.85)
    buttons(m, sf, 7.4, [('list', '8040c0', 'LIST'), ('issue', '8040c0', 'ISSUE'), ('bprev', '1a1a1a', 'BOND<'), ('bnext', '1a1a1a', 'BOND>'),
                         ('bbuy', '2050d0', 'BUY BD'), ('bell', 'f0d020', 'BELL'), ('tape', 'f0f0f0', 'TAPE')], u0=0.9, step=1.25, size=0.85)
    m.ctl(sf, 'key', 'trading', 13.8, 5.0, w=1.3, h=1.3, col='c0a040', label='TRADING', pos=['OPEN', 'CLOSED'])
    m.ctl(sf, 'digits', 'm6', 12.6, 7.4, w=5.4, h=0.9, col='ffd040', label='', fmt='%s')
    hd = hood(m, col='#141a20')
    lamps(m, hd, [('power', '30e060', '12V'), ('open', '30e060', 'OPEN'), ('bull', '30e060', 'BULL'), ('bear', 'ff3030', 'BEAR'), ('halt', 'ffb020', 'NO LIST', True)], 1.6, 11.0, step=1.6, size=0.65)
    monitor(m, 0.6, 9.4, 12.2, 17.6, 10.2, ['m1', 'm2', 'm3', 'm4', 'm5'], cols=['ffd040', '8ad4ff', '7cf29a', 'b0b0b0', '7cf29a'])
    monitor(m, 9.8, 15.4, 12.2, 15.8, 10.2, ['b1', 'b2', 'b3'], cols=['ffd040', '8ad4ff', '7cf29a'])
    m.g('desk things')
    m.cyl((1.6, 9.0, 9.4), 0.7, 1.2, 'y', 'metal:brass', 'opening bell')
    m.box((1.5, 10.2, 9.3), (1.7, 10.8, 9.5), 'paint:#2a1a10', 'bell handle')
    m.box((14.2, 9.0, 8.8), (15.6, 9.4, 10.0), 'paint:#1a1a1a', 'calculator')
    m.box((12.0, 9.0, 11.4), (14.0, 9.6, 12.4), 'paint:#f4f0d8', 'share certificates')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ===================================================================================================== Regulator Office
CHECKS = ['MAINTENANCE', 'BRAKE TESTS', 'PUNCTUALITY', 'OVERCROWDING', 'STEP-FREE', 'MAINT BUDGET', 'INSURANCE', 'CARGO CASES', 'INCIDENT REPORTS']


def regulator_office():
    m = Model('regulator_office')
    back_wall(m, panel='#3a2a1c', plaster='#ece6d8')
    m.g('audit board')
    led_board(m, -14, 13.6, 11.6, 29.0)
    m.box((-12, 29.4, 14.7), (12, 31.4, 15.4), 'paint:#1c140c', 'header board')
    m.box((-11.6, 29.6, 14.62), (11.6, 31.2, 14.7), 'plate:OFFICE OF RAIL REGULATION', 'header lettering')
    ab = m.surface('north', face=14.42, title='AUDIT BOARD')
    m.ctl(ab, 'digits', 'co', X(-0.2), 27.8, w=26, h=1.3, col='ffd040', label='', fmt='%s')
    for i, ch in enumerate(CHECKS):
        v = 25.8 - i * 1.55
        m.ctl(ab, 'lamp', 'a%dl' % i, X(-13.2), v, w=0.6, h=0.6, col='30e060', label='')
        m.ctl(ab, 'lamp', 'a%df' % i, X(-12.3), v, w=0.6, h=0.6, col='ff3030', label='')
        m.ctl(ab, 'label', '', X(-8.6), v, w=0, h=0, col='ffb020', label=ch)
        m.ctl(ab, 'digits', 'a%dd' % i, X(5.0), v, w=16.4, h=1.3, col='7cf29a', label='', fmt='%s')
    m.ctl(ab, 'gauge', 'scoreg', X(11.4), 13.6, w=2.6, h=2.6, col='f2f0e6', label='SCORE', max=100)
    m.g('register board')
    led_board(m, 14.6, 30, 11.6, 29.0)
    rb = m.surface('north', face=14.42, title='REGISTER')
    m.box((15.0, 27.6, 14.45), (29.6, 28.6, 14.5), 'label:CORRECTIVE ACTIONS', 'ca heading', north='label:CORRECTIVE ACTIONS')
    for i in range(4):
        m.ctl(rb, 'digits', 'ca%d' % (i + 1), X(22.3), 26.4 - i * 1.6, w=14.6, h=1.3, col='ffb020', label='', fmt='%s')
    m.box((15.0, 19.0, 14.45), (29.6, 20.0, 14.5), 'label:CAPACITY REGISTER', 'cr heading', north='label:CAPACITY REGISTER')
    for i in range(5):
        m.ctl(rb, 'digits', 'cr%d' % (i + 1), X(22.3), 17.8 - i * 1.4, w=14.6, h=1.2, col='8ad4ff' if i < 4 else 'b0b0b0', label='', fmt='%s')
    # the audit files (left) and the binder cupboard (right)
    filing_cabinet(m, -13.4, -3.6, 10.6, 'files', 'AUDIT FILES', body='ral7032', front='#c8c2ae')
    SPOTS['regulator_office'] = cupboard_shelf(m, 18.4, 29.4, ['dr'], 4, title='BINDER SHELF', body='#3a2a1c', door='#4e3826')
    for (x, c) in ((19.2, '#1f4a8a'), (19.9, '#8a1f1f'), (20.6, '#1f6b3a'), (21.3, '#6a4a1a')):
        m.box((x, 9.4, 14.0), (x + 0.6, 11.6, 15.0), 'paint:' + c, 'lever arch file')
    desk(m, {('l', 0): 'dl'}, body='#3a2a1c', front='#4e3826', pull='metal:brass')
    sf = strip(m, 3.6, 8.4, col='#22180e')
    buttons(m, sf, 4.6, [('prev', '1a1a1a', 'CO<'), ('next', '1a1a1a', 'CO>'), ('audit', '2050d0', 'AUDIT'), ('stamp', 'c03030', 'STAMP'),
                         ('caprev', '1a1a1a', 'CA<'), ('canext', '1a1a1a', 'CA>'), ('accept', '1fa34a', 'ACCEPT'), ('appeal', 'f0d020', 'APPEAL')], u0=0.9, step=1.3, size=0.85)
    buttons(m, sf, 7.4, [('resprev', '1a1a1a', 'RES<'), ('resnext', '1a1a1a', 'RES>'), ('winprev', '404040', 'WIN<'), ('winnext', '404040', 'WIN>'), ('season', '8040c0', 'TERM'),
                         ('pxdn', '404040', 'PX-'), ('pxup', '404040', 'PX+'), ('bid', '1fa34a', 'BID'), ('close', 'd02020', 'CLOSE')], u0=0.9, step=1.22, size=0.8)
    hd = hood(m, col='#1c140c')
    lamps(m, hd, [('pw', '30e060', '12V'), ('due', 'ffb020', 'AUDIT DUE'), ('over', 'ff3030', 'OVERDUE', True), ('clean', '30e060', 'CLEAN'),
                  ('auct', '8ad4ff', 'BIDS', True), ('off', 'b0b0b0', 'REG OFF')], 1.4, 11.0, step=1.9, size=0.6)
    m.g('desk things')
    m.box((12.6, 9.0, 9.6), (14.6, 9.3, 11.0), 'paint:#2a2a6a', 'ink pad')
    m.box((13.2, 9.3, 10.0), (13.8, 10.6, 10.6), 'wood', 'rubber stamp handle')
    m.box((12.9, 9.3, 9.8), (14.1, 9.6, 10.8), 'paint:#1a1a1a', 'rubber stamp')
    for i, c in enumerate(('#f4f0d8', '#e8e0c8', '#f4f0d8')):
        m.box((1.0, 9.0 + i * 0.25, 9.4), (3.6, 9.2 + i * 0.25, 11.6), 'paint:' + c, 'in tray papers')
    m.box((0.8, 9.0, 9.2), (3.8, 9.9, 11.8), 'paint:#3a3a3a', 'in tray', faces=('east', 'west', 'north', 'south'))
    m.cyl((8.0, 9.0, 12.2), 0.6, 0.3, 'y', 'metal:brass', 'desk lamp foot')
    m.box((7.9, 9.3, 12.1), (8.1, 11.6, 12.3), 'metal:brass', 'desk lamp stem')
    m.box((6.8, 11.4, 11.6), (9.2, 12.2, 12.6), 'paint:#1f6b3a', 'green shade')
    plant_pots(m, -14.6, 3.0)
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ====================================================================================================== Insurance Desk
def insurance_desk():
    m = Model('insurance_desk')
    back_wall(m, panel='#203a30', plaster='#eaeee6')
    m.g('claims board')
    led_board(m, -14, 30, 18.6, 29.0)
    m.box((-4, 29.4, 14.7), (20, 31.4, 15.4), 'paint:#0e2018', 'header board')
    m.box((-3.6, 29.6, 14.62), (19.6, 31.2, 14.7), 'plate:CLAIMS AND LOSS ADJUSTING', 'header lettering')
    cb = m.surface('north', face=14.42, title='CLAIMS BOARD')
    for i in range(4):
        m.ctl(cb, 'digits', 'cl%d' % (i + 1), X(-1.0), 27.6 - i * 2.0, w=24.6, h=1.6, col='ffb020', label='', fmt='%s')
    for i in range(4):
        m.ctl(cb, 'digits', 'm%d' % (i + 1), X(23.0), 27.6 - i * 2.0, w=13.4, h=1.5, col=['ffd040', '7cf29a', '8ad4ff', 'b0b0b0'][i], label='', fmt='%s')
    # the actuarial tables poster + clock
    m.g('poster')
    m.box((-14, 12.0, 15.0), (-6, 18.0, 15.4), 'paint:#f4f2ea', 'actuarial poster')
    m.box((-13.6, 16.6, 14.96), (-6.4, 17.6, 15.0), 'label:RISK TABLES', 'poster title')
    for y in range(13, 16):
        for x in range(-13, -7, 2):
            m.box((x, y, 14.97), (x + 1.4, y + 0.6, 15.0), 'paint:#c8d0c8', 'table cell')
    m.cyl((26.0, 15.0, 14.6), 1.2, 0.6, 'z', 'gauge:clock', 'office clock', cap='gauge:clock')
    # policy cupboard + shelf (right), the evidence drawer is in the desk
    SPOTS['insurance_desk'] = cupboard_shelf(m, 18.4, 29.4, ['pol'], 4, title='POLICY SHELF', body='#203a30', door='#2e5242')
    m.box((19.0, 1.0, 11.3), (23.0, 1.4, 11.45), 'label:POLICY FILES', 'cupboard plate')
    m.g('records safe')
    m.box((-13.4, 0, 9.6), (-7.4, 8.4, 15.2), 'paint:#2f3a32', 'records cabinet')
    m.box((-13.1, 0.4, 9.4), (-7.7, 8.0, 9.6), 'paint:#3b4a40', 'cabinet door')
    m.box((-12.4, 6.4, 9.35), (-8.6, 7.2, 9.4), 'label:SALVAGE', 'cabinet label')
    m.box((-8.4, 3.6, 9.1), (-8.1, 5.0, 9.4), 'metal:steel', 'cabinet handle')
    for (x, c) in ((-12.6, '#8a1c1c'), (-11.2, '#1f6b3a'), (-9.8, '#1f4a8a')):
        m.box((x, 8.4, 11.0), (x + 1.2, 9.8, 13.0), 'paint:' + c, 'claims ledger')
    # the roll-top desk
    desk(m, {('l', 0): 'evid', ('l', 1): 'dl', ('r', 0): 'dr'}, body='#4a2e1a', front='#6a4228', pull='metal:brass')
    m.box((4.6, 1.5, 3.72), (11.4, 2.2, 3.8), 'label:EVIDENCE - DOCUMENTS', 'evidence plate')
    m.g('roll top')
    m.box((0.2, 9.0, 11.6), (15.8, 14.6, 13.0), 'paint:#4a2e1a', 'roll top housing')
    for y in range(9, 14):
        m.box((0.4, y + 0.1, 11.5), (15.6, y + 0.9, 11.6), 'paint:#5a3a22', 'tambour slat')
    m.box((0.2, 14.6, 10.8), (15.8, 15.2, 13.2), 'paint:#3a2412', 'roll top cap')
    sf = strip(m, 3.6, 8.4, col='#2a1a0e')
    buttons(m, sf, 4.6, [('prev', '1a1a1a', 'CO<'), ('next', '1a1a1a', 'CO>'), ('cover', '2050d0', 'COVER'), ('excess', '2050d0', 'EXCESS'),
                         ('quote', 'f0f0f0', 'QUOTE'), ('sign', '1fa34a', 'SIGN')], u0=0.9, step=1.35, size=0.9)
    buttons(m, sf, 7.4, [('cprev', '1a1a1a', 'CLM<'), ('cnext', '1a1a1a', 'CLM>'), ('assess', 'e08020', 'ASSESS'), ('endorse', '8040c0', 'ENDORSE')], u0=0.9, step=1.35, size=0.9)
    m.ctl(sf, 'digits', 'sel', 11.8, 4.6, w=6.6, h=0.9, col='ffd040', label='', fmt='%s')
    m.ctl(sf, 'digits', 'docs', 11.8, 7.4, w=6.6, h=0.9, col='8ad4ff', label='', fmt='%s')
    hd = m.surface('north', face=11.48, title='ROLL TOP')
    lamps(m, hd, [('pw', '30e060', '12V'), ('ins', '30e060', 'INSURED'), ('lapse', 'ff3030', 'UNINSURED', True), ('neg', 'ff3030', 'NEGLIGENCE', True),
                  ('filed', 'ffb020', 'CLAIMS')], 1.6, 14.0, step=2.4, size=0.6)
    m.ctl(hd, 'gauge', 'compg', 13.8, 11.4, w=2.2, h=2.2, col='f2f0e6', label='COMPLY %', max=100)
    monitor(m, 1.0, 11.0, 15.4, 18.2, 10.6, ['co', 'cover', 'prem', 'comp'], cols=['ffd040', '7cf29a', 'ffb020', '8ad4ff'], stand_y=15.2)
    m.g('desk things')
    m.box((12.0, 9.0, 8.8), (14.6, 9.3, 10.6), 'paint:#f4f0d8', 'claim forms')
    m.box((14.8, 9.0, 9.0), (15.6, 9.2, 10.2), 'paint:#1a1a1a', 'fountain pen')
    m.box((0.6, 9.0, 9.0), (2.4, 9.8, 10.4), 'paint:#2a2a2a', 'magnifier stand')
    m.cyl((1.5, 9.8, 9.6), 0.6, 0.15, 'y', 'glass', 'magnifier lens')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ============================================================================================ Town Hall Planning Board
def planning_board():
    m = Model('planning_board')
    back_wall(m, panel='#3a3020', plaster='#f2ecdc')
    m.g('town map')
    m.box((-14, 11.6, 14.9), (5.6, 29.0, 15.4), 'paint:#cfe3b0', 'map board')
    m.box((-14.2, 11.4, 14.8), (5.8, 29.2, 14.9), 'paint:#5a3a1a', 'map frame', faces=('north',))
    m.box((-13.6, 27.4, 14.84), (5.2, 28.8, 14.9), 'plate:TOWN PLAN AND RAILWAY', 'map title')
    m.box((-14.0, 15.0, 14.86), (5.6, 16.2, 14.9), 'paint:#5aa0d8', 'river')
    m.box((-10.0, 11.6, 14.85), (-9.0, 27.0, 14.9), 'paint:#6a6a6a', 'high street')
    m.box((-14.0, 21.0, 14.85), (5.6, 21.6, 14.9), 'paint:#6a6a6a', 'cross road')
    m.box((-14.0, 18.2, 14.83), (5.6, 18.6, 14.9), 'paint:#2a2a2a', 'railway line')
    for x in range(-13, 6, 2):
        m.box((x, 18.0, 14.82), (x + 0.3, 18.8, 14.88), 'paint:#8a8a8a', 'sleeper tick')
    for (x, y, w, h, c) in ((-13.0, 22.4, 2.4, 1.6, '#d8a070'), (-8.4, 22.6, 3.0, 2.0, '#c88a60'), (-4.6, 22.2, 2.2, 3.0, '#e0b080'), (-1.6, 23.0, 3.2, 1.8, '#c88a60'),
                          (2.4, 22.4, 2.4, 2.4, '#d8a070'), (-13.2, 12.4, 3.0, 2.0, '#b0b0b0'), (-7.6, 12.2, 4.0, 2.2, '#a0a0a0'), (-2.0, 12.6, 3.6, 1.8, '#b8b8b8'),
                          (-6.0, 19.0, 2.6, 1.6, '#d02020'), (1.0, 19.2, 2.0, 1.4, '#d02020')):
        m.box((x, y, 14.8), (x + w, y + h, 14.86), 'paint:' + c, 'map block')
    for (x, y) in ((-5.0, 19.4), (1.6, 19.4)):
        m.box((x, y, 14.75), (x + 0.6, y + 0.6, 14.8), 'paint:#f0f020', 'station pin')
    mb = m.surface('north', face=14.72, title='TOWN MAP')
    lamps(m, mb, [('pw', '30e060', '12V'), ('grow', '30e060', 'GROWING'), ('shrink', 'ff3030', 'SHRINKING'), ('noisy', 'ffb020', 'NOISE'),
                  ('tend', '8ad4ff', 'TENDER', True), ('psoon', '30e060', 'PSO')], X(-12.6), 25.8, step=-3.1, size=0.6, dv=-0.6)
    m.ctl(mb, 'gauge', 'accg', X(-11.2), 13.4, w=2.4, h=2.4, col='f2f0e6', label='ACCESS', max=100)
    m.ctl(mb, 'gauge', 'noiseg', X(3.2), 13.4, w=2.4, h=2.4, col='f2f0e6', label='NOISE DB', max=90)
    m.g('statistics board')
    led_board(m, 6.4, 30, 11.6, 29.0)
    sb = m.surface('north', face=14.42, title='STATISTICS')
    for i, (cid, col) in enumerate((('town', 'ffd040'), ('pop', '7cf29a'), ('hist', '7cf29a'), ('acc', '8ad4ff'), ('jobs', '8ad4ff'), ('goods', '8ad4ff'), ('noise', 'ffb020'),
                                    ('why', 'b0b0b0'), ('st1', 'f0f0f0'), ('st2', 'f0f0f0'), ('st3', 'f0f0f0'), ('st4', 'f0f0f0'), ('pso1', 'c080ff'), ('pso2', 'c080ff'),
                                    ('pso3', 'c080ff'), ('env1', '30e060'), ('env2', '30e060'))):
        m.ctl(sb, 'digits', cid, X(18.2), 28.2 - i * 0.98, w=22.8, h=0.86, col=col, label='', fmt='%s')
    # plan chest (left) and the planning-applications cupboard (right)
    m.g('plan chest')
    m.box((-13.6, 0, 7.0), (-3.0, 6.4, 15.2), 'paint:#5a4630', 'plan chest')
    for k in range(5):
        y0 = 0.4 + k * 1.15
        m.box((-13.3, y0, 6.8), (-3.3, y0 + 1.0, 7.0), 'paint:#6a5440', 'plan drawer')
        m.box((-9.2, y0 + 0.35, 6.6), (-7.4, y0 + 0.6, 6.8), 'metal:brass', 'drawer pull')
    m.box((-13.6, 6.4, 7.0), (-3.0, 6.8, 15.2), 'wood', 'chest top')
    pc = m.surface('north', face=6.58, title='PLAN CHEST')
    m.ctl(pc, 'zone', 'plans', X(-8.3), 3.2, w=10.0, h=5.8, col='000000', label='')
    for (x, c) in ((-12.8, '#f4f0d8'), (-10.6, '#d8e8f4')):
        m.box((x, 6.8, 9.0), (x + 2.2, 7.0, 14.0), 'paint:' + c, 'rolled plans')
    m.cyl((-6.0, 6.8, 11.0), 0.5, 3.6, 'z', 'paint:#e8e0c8', 'plan roll')
    m.g('applications tray')
    m.box((18.4, 0, 9.0), (29.4, 7.0, 15.2), 'paint:#3a3020', 'application cupboard')
    m.box((18.7, 0.4, 8.8), (29.1, 6.6, 9.0), 'paint:#4e4230', 'cupboard door')
    m.box((20.0, 5.4, 8.75), (28.0, 6.2, 8.8), 'label:PLANNING APPLICATIONS', 'cupboard label')
    m.box((27.6, 2.6, 8.5), (28.0, 4.0, 8.8), 'metal:brass', 'cupboard knob')
    ap = m.surface('north', face=8.75, title='APPLICATIONS')
    m.ctl(ap, 'zone', 'apps', X(23.9), 3.5, w=10.0, h=6.0, col='000000', label='')
    for i in range(3):
        m.box((19.0 + i * 3.4, 7.0, 10.0), (21.6 + i * 3.4, 7.3 + i * 0.2, 13.6), 'paint:#f4f0d8', 'application file')
    # the planning table: building models stand on it (shelf spots)
    desk(m, {}, body='#3a3020', front='#4e4230', pull='metal:brass')
    m.g('planning table')
    m.box((0.6, 9.0, 8.6), (15.4, 9.05, 12.6), 'paint:#cfe3b0', 'site plan sheet')
    m.box((0.6, 9.05, 10.2), (15.4, 9.08, 10.6), 'paint:#2a2a2a', 'proposed railway')
    sh = m.surface('up', face=16 - 9.08, title='PLANNING TABLE')
    spots = []
    for k, x in enumerate((4.0, 6.6, 9.4, 12.0)):
        m.ctl(sh, 'zone', 'sh%d' % (k + 1), X(x), 11.6, w=2.4, h=1.8, col='000000', label='')
        spots.append((x, 9.08, 11.6))
    SPOTS['planning_board'] = spots
    sf = strip(m, 3.6, 8.4, col='#22180e')
    buttons(m, sf, 4.6, [('tprev', '1a1a1a', 'TOWN<'), ('tnext', '1a1a1a', 'TOWN>'), ('report', 'f0f0f0', 'REPORT'), ('rename', 'b0b0b0', 'RENAME'),
                         ('pxdn', '404040', 'BID-'), ('pxup', '404040', 'BID+'), ('bid', '8040c0', 'PSO BID')], u0=0.9, step=1.35, size=0.85)
    buttons(m, sf, 7.4, [('barrier', '1fa34a', 'BARRIER'), ('trees', '2f7a3a', 'TREES'), ('solar', 'f0d020', 'SOLAR'), ('dpf', '606060', 'FILTER'),
                         ('water', '2080d0', 'WATER')], u0=0.9, step=1.35, size=0.85)
    m.ctl(sf, 'digits', 'bidpx', 12.6, 7.4, w=5.4, h=0.9, col='c080ff', label='', fmt='%s')
    m.g('town things')
    m.box((13.6, 9.0, 4.0), (15.4, 9.6, 5.4), 'paint:#b8902e', 'mayor chain box')
    plant_pots(m, -14.6, 3.0)
    m.box((-1.6, 0, 1.0), (-0.4, 9.0, 2.2), 'wood', 'flag pole base')
    m.box((-1.2, 9.0, 1.4), (-0.8, 20.0, 1.8), 'metal:brass', 'flag pole')
    for i, c in enumerate(('#e40303', '#ff8c00', '#ffed00', '#008026', '#004dff', '#750787')):
        m.box((-0.8, 19.4 - i * 0.5, 1.5), (3.6, 19.9 - i * 0.5, 1.6), 'paint:' + c, 'pride flag stripe')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# =================================================== additions to existing models (called by their own generators)
def construction_programme(m):
    """the Construction Site Office's project-management board on posts in front of the compound (#11)"""
    m.g('programme board')
    for x in (-15.2, -1.8):
        m.box((x, 0, -0.9), (x + 0.6, 10.6, -0.3), 'galv', 'board post')
        m.box((x - 0.3, 0, -1.2), (x + 0.9, 0.4, 0.0), 'concrete', 'post foot')
    m.box((-15.6, 10.4, -0.5), (-0.8, 18.4, -0.2), 'paint:#f4f4f0', 'programme board')
    m.box((-15.8, 10.2, -0.45), (-0.6, 18.6, -0.15), 'paint:#2b2f33', 'board frame', faces=('north', 'up', 'down', 'east', 'west'))
    m.box((-15.4, 17.6, -0.56), (-1.0, 18.3, -0.5), 'plate:WORKS PROGRAMME', 'board title')
    m.box((-15.6, 18.4, -0.9), (-0.8, 18.8, 0.2), 'paint:#2b2f33', 'board rain hood')
    pb = m.surface('north', face=-0.58, title='PROGRAMME')
    stages = ['SURVEY', 'EARTHWKS', 'FOUNDTN', 'TRACK', 'ELECTRIC', 'TESTING', 'OPENING']
    for i, s in enumerate(stages):
        m.ctl(pb, 'tile', 'st%d' % i, X(-14.4 + i * 2.0), 16.6, w=1.9, h=0.9, col='f0f0e0', label=s)
    m.ctl(pb, 'digits', 'pm_stage', X(-11.0), 15.3, w=8.4, h=0.8, col='1a3aa0', label='', fmt='%s')
    m.ctl(pb, 'digits', 'pm_fcst', X(-4.4), 15.3, w=6.0, h=0.8, col='1a3aa0', label='', fmt='%s')
    m.ctl(pb, 'digits', 'pm_budget', X(-11.6), 14.3, w=7.2, h=0.7, col='1a7a3a', label='', fmt='%s')
    m.ctl(pb, 'digits', 'pm_spent', X(-4.4), 14.3, w=6.0, h=0.7, col='a01a1a', label='', fmt='%s')
    m.ctl(pb, 'digits', 'pm_crew', X(-13.4), 13.3, w=3.6, h=0.7, col='1a1a1a', label='', fmt='%s')
    m.ctl(pb, 'digits', 'pm_plant', X(-8.8), 13.3, w=5.0, h=0.7, col='1a1a1a', label='', fmt='%s')
    m.ctl(pb, 'digits', 'pm_why', X(-3.4), 13.3, w=4.6, h=0.7, col='a01a1a', label='', fmt='%s')
    for i, lab in enumerate(('MAT', 'TRAFFIC', 'FUNDS', 'CREW')):
        m.ctl(pb, 'lamp', 'dl%d' % i, X(-14.8 + i * 1.6), 12.2, w=0.5, h=0.5, col='ff3030', label=lab, blink=True)
        m.ctl(pb, 'label', '', X(-14.8 + i * 1.6), 11.7, w=0, h=0, col='1a1a1a', label=lab)
    m.ctl(pb, 'lamp', 'poss', X(-8.2), 12.2, w=0.5, h=0.5, col='ffb020', label='POSSESSION')
    m.ctl(pb, 'label', '', X(-8.2), 11.7, w=0, h=0, col='1a1a1a', label='POSS')
    m.ctl(pb, 'gauge', 'pm_rate', X(-2.2), 11.6, w=1.6, h=1.6, col='f2f0e6', label='RATE %', max=150)
    for i, (cid, col, lab) in enumerate((('crewdn', '1a1a1a', 'CREW-'), ('crewup', '1a1a1a', 'CREW+'), ('fund', '1fa34a', 'FUND'), ('loan', '2050d0', 'LOAN'), ('report', 'f0f0f0', 'RPT'))):
        m.ctl(pb, 'push', cid, X(-14.6 + i * 1.3), 10.9, w=0.7, h=0.7, col=col, label=lab)
    m.ctl(pb, 'selector', 's_plant', X(-7.0), 11.0, w=0.9, h=0.9, col='202020', label='PLANT',
          pos=['NONE', 'SURVEY KIT', 'EXCAVATOR', 'PILING RIG', 'TRACK MACHINE', 'WIRING TRAIN', 'TEST TRAIN'])
    m.ctl(pb, 'key', 'k_poss', X(-5.4), 11.0, w=0.9, h=0.9, col='c8a040', label='POSSESSION', pos=['OFF', 'ON'])
    m.box((-15.4, 10.5, -0.56), (-1.0, 10.7, -0.5), 'paint:#d02020', 'board stripe')


def terminal_gate_buttons(m, gd):
    """the Container Terminal gate desk's buttons, with APPT (truck appointment, #15) and WASH (#17)"""
    for i, (cid, col, lab) in enumerate((('gatein', '1fa34a', 'GATE IN'), ('gateout', 'e08020', 'GATE OUT'), ('stuff', '2050d0', 'STUFF'), ('strip', '20a0a0', 'STRIP'),
                                         ('dest', 'f0d020', 'SEAL/DEST'), ('appt', 'b0b0b0', 'APPT'), ('wash', '40c0e0', 'WASH'))):
        u = X(-6.0 + i * 0.9)
        m.ctl(gd, 'push', cid, u, 5.6, w=0.7, h=0.7, col=col, label=lab)
        m.ctl(gd, 'label', '', u, 4.8 if i % 2 == 0 else 6.4, w=0, h=0, col='e8e8e8', label=lab)


RECIPES = {
    'port_berth': (['ICI', 'PDP', 'IBI'], {'I': 'minecraft:iron_block', 'C': 'irextras:shipping_container', 'P': 'minecraft:piston', 'D': 'irextras:container_terminal', 'B': 'minecraft:boat'}),
    'stock_exchange': (['GLG', 'RDR', 'PCP'], {'G': 'minecraft:gold_ingot', 'L': 'minecraft:redstone_lamp', 'R': 'minecraft:redstone', 'D': 'irextras:commodity_exchange', 'P': 'minecraft:paper', 'C': 'minecraft:chest'}),
    'regulator_office': (['BSB', 'PDP', 'WCW'], {'B': 'minecraft:book', 'S': 'minecraft:sign', 'P': 'minecraft:paper', 'D': 'irextras:company_hq', 'W': 'minecraft:planks', 'C': 'minecraft:chest'}),
    'insurance_desk': (['PWP', 'BDB', 'WCW'], {'P': 'minecraft:paper', 'W': 'minecraft:writable_book', 'B': 'minecraft:book', 'D': 'irextras:freight_office', 'C': 'minecraft:chest'}),
    'planning_board': (['MSM', 'PDP', 'WCW'], {'M': 'minecraft:map', 'S': 'minecraft:sign', 'P': 'minecraft:paper', 'D': 'irextras:freight_office', 'W': 'minecraft:planks', 'C': 'minecraft:chest'}),
}

LANG = {
    'tile.irextras.port_berth.name': 'Port Berth (STS Crane)',
    'tile.irextras.stock_exchange.name': 'Railway Stock Exchange Board',
    'tile.irextras.regulator_office.name': 'Regulator Office',
    'tile.irextras.insurance_desk.name': 'Insurance Desk',
    'tile.irextras.planning_board.name': 'Town Hall Planning Board',
}

if __name__ == '__main__':
    port_berth(); stock_exchange(); regulator_office(); insurance_desk(); planning_board()
    for n in LANG:
        n = n.split('.')[2]
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    for n, (pat, key) in RECIPES.items():
        ing = lambda v: {'item': v, 'data': 0} if v == 'minecraft:planks' else {'item': v}
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# world + business layer (her next-48 list #12-38)\n' + '\n'.join(add) + '\n')
    print('shelf spots (model px, for the tiles\' shelfSpots):')
    for k, v in SPOTS.items(): print(' ', k, v)
