#!/usr/bin/env python3
"""The railway economy's office furniture (her ChatGPT list #46-50, items 10/46/47/48/49/50): Company HQ boardroom,
Commodity Exchange trading floor, Travel Centre ticket office and the Container Terminal crane desk. Each is a three-block
wide desk (BlockWideDesk) with live controls, real storages behind its drawer / cupboard / safe fronts and a shelf that
shows what you put on it. Models face north; controls are placed by u (viewer-left -> right): x = 16 - u.
Also writes blockstates, item models, the container item texture, recipes and lang lines.
Live controls: see block/TileCompanyHQ, TileExchange, TileTravelCentre, TileContainerTerminal."""
import json, os
from bbkit import Model, ASSETS

X = lambda u: 16 - u                     # model x of a control at u


def back_wall(m, panel='#5a3a24', plaster='#e9e1cc', top=32):
    m.g('back wall')
    m.box((-14, 0, 15.4), (30, 9.6, 16), 'paint:' + panel, 'wainscot')
    m.box((-14, 9.6, 15.5), (30, top, 16), 'paint:' + plaster, 'plastered wall')
    m.box((-14, 9.4, 15.2), (30, 9.9, 15.6), 'paint:#3e2716', 'dado rail')
    m.box((-14, 0, 15.1), (30, 0.8, 15.5), 'paint:#2a1a10', 'skirting')
    for x in range(-13, 30, 4):
        m.box((x, 1.2, 15.32), (x + 3.2, 8.6, 15.4), 'paint:#6b4630', 'wainscot panel')
    m.box((-14, top - 0.6, 15.0), (30, top, 16), 'paint:#3e2716', 'cornice')


def led_board(m, x0, x1, y0, y1, z=14.42, bezel='#23272b'):
    m.box((x0 - 0.4, y0 - 0.4, z + 0.18), (x1 + 0.4, y1 + 0.4, 15.4), 'paint:' + bezel, 'board bezel')
    m.box((x0, y0, z + 0.08), (x1, y1, z + 0.18), 'paint:#070a08', 'LED panel')
    for (x, y) in ((x0 - 0.2, y0 - 0.2), (x1 + 0.2, y0 - 0.2), (x0 - 0.2, y1 + 0.2), (x1 + 0.2, y1 + 0.2)):
        m.bolt(x, y, z + 0.18, 'north', 0.35)


def desk(m, drawers, top=9.0, body='#5a3a24', front='#7a5238', pull='metal:brass'):
    """the middle block's desk: top, modesty panel, two pedestals of 3 drawers; drawers = {(side, k): zone id}"""
    m.g('desk')
    m.box((0, top - 0.6, 3.4), (16, top, 12.8), 'wood', 'desk top')
    m.box((-0.2, top - 0.8, 3.2), (16.2, top - 0.6, 13.0), 'paint:#3e2716', 'desk top edge')
    m.box((0.6, 0.6, 4.0), (15.4, top - 0.8, 4.5), 'paint:#6b4630', 'modesty panel')
    m.box((1.2, 1.4, 3.9), (14.8, top - 1.6, 4.0), 'paint:' + front, 'raised panel')
    dr = m.surface('north', face=3.5, title='DESK DRAWERS')
    for side, (x0, x1) in (('l', (10.4, 15.6)), ('r', (0.4, 5.6))):
        m.box((x0, 0.4, 4.0), (x1, top - 0.8, 12.6), 'paint:' + body, 'pedestal')
        for k in range(3):
            y0 = 0.9 + k * 2.4
            zid = drawers.get((side, k))
            m.box((x0 + 0.3, y0, 3.8), (x1 - 0.3, y0 + 2.1, 4.0), 'paint:' + front, 'drawer front')
            m.box(((x0 + x1) / 2 - 0.8, y0 + 1.2, 3.55), ((x0 + x1) / 2 + 0.8, y0 + 1.5, 3.8), pull, 'pull')
            if zid: m.ctl(dr, 'zone', zid, X((x0 + x1) / 2), y0 + 1.05, w=4.6, h=2.1, col='000000', label='')
    m.box((0, 0, 4.2), (16, 0.4, 12.6), 'paint:#2a1a10', 'kick plinth')


def strip(m, z0, z1, top=9.0, col='#2b2f33'):
    m.g('control strip')
    m.box((0.6, top, z0), (15.4, top + 0.15, z1), 'paint:' + col, 'steel control strip')
    m.box((0.4, top, z0 - 0.2), (15.6, top + 0.2, z0), 'metal:steel', 'strip edge')
    return m.surface('up', face=16 - (top + 0.15), title='DESK')


def buttons(m, sf, row_v, items, u0=1.2, step=1.25, size=0.95):
    """a row of push buttons with labels under them: items = (id, colour, label)"""
    for i, (cid, col, lab) in enumerate(items):
        u = u0 + i * step
        m.ctl(sf, 'push', cid, u, row_v, w=size, h=size, col=col, label=lab)
        m.ctl(sf, 'label', '', u, row_v - 0.8, w=0, h=0, col='e8e8e8', label=lab)


def hood(m, x0=0.6, x1=15.4, y0=9.0, y1=11.8, z=9.8, col='#24282c'):
    """the raised instrument hood at the back of the desk top: a north surface for lamps and readouts"""
    m.g('instrument hood')
    m.box((x0, y0, z), (x1, y1, z + 1.6), 'paint:' + col, 'instrument hood')
    m.box((x0 - 0.1, y1, z - 0.1), (x1 + 0.1, y1 + 0.2, z + 1.7), 'metal:steel', 'hood cap')
    return m.surface('north', face=z - 0.02, title='HOOD')


def monitor(m, x0, x1, y0, y1, z, lines, cols=None, stand_y=11.8):
    m.g('monitor')
    xc = (x0 + x1) / 2
    m.box((xc - 0.5, stand_y, z + 0.6), (xc + 0.5, y0, z + 1.1), 'paint:#2a2c2e', 'monitor neck')
    m.box((x0, y0, z), (x1, y1, z + 0.9), 'paint:#1c1e20', 'monitor bezel')
    m.box((x0 + 0.4, y0 + 0.4, z - 0.06), (x1 - 0.4, y1 - 0.4, z), 'paint:#03140a', 'screen')
    mon = m.surface('north', face=z - 0.1, title='MONITOR')
    h = (y1 - y0 - 0.8) / len(lines)
    for i, cid in enumerate(lines):
        m.ctl(mon, 'digits', cid, X(xc), y1 - 0.4 - h * (i + 0.5), w=x1 - x0 - 1.2, h=h * 0.9,
              col=(cols[i] if cols else ('ffd040' if i == 0 else '7cf29a')), label='', fmt='%s')
    return mon


def cupboard_shelf(m, x0, x1, ids, shelf_n, title='SHELF', top=6.6, body='#5a3a24', door='#6b4630'):
    """a low cupboard against the back wall with doors (storage zones) and a shelf top that shows what stands on it"""
    m.g('cupboard')
    m.box((x0, 0, 11.6), (x1, top, 15.4), 'paint:' + body, 'cupboard')
    m.box((x0 + 0.2, top, 11.4), (x1 + 0.2, top + 0.4, 15.4), 'paint:#3e2716', 'shelf top')
    m.box((x0 + 0.3, top + 0.4, 15.0), (x1 + 0.1, top + 2.8, 15.4), 'paint:#3e2716', 'shelf back board')
    for x in (x0 + 0.2, x1 - 0.2):
        m.box((x, top + 0.4, 11.6), (x + 0.4, top + 2.8, 15.4), 'paint:#3e2716', 'bookend')
    cup = m.surface('north', face=11.4, title='CUPBOARD')
    w = (x1 - x0 - 0.6) / len(ids)
    for k, cid in enumerate(ids):
        a = x0 + 0.5 + k * w
        m.box((a, 0.6, 11.45), (a + w - 0.3, top - 0.6, 11.6), 'paint:' + door, 'cupboard door')
        m.box((a + w - 1.0, top / 2, 11.2), (a + w - 0.7, top / 2 + 0.9, 11.45), 'metal:brass', 'door knob')
        m.ctl(cup, 'zone', cid, X(a + (w - 0.3) / 2), top / 2, w=w - 0.4, h=top - 1.2, col='000000', label='')
    sh = m.surface('up', face=16 - (top + 0.4), title=title)
    spots = []
    sw = (x1 - x0 - 1.0) / shelf_n
    for k in range(shelf_n):
        x = x0 + 0.7 + sw * (k + 0.5)
        m.ctl(sh, 'zone', 'sh%d' % (k + 1), X(x), 13.4, w=sw - 0.05, h=3.8, col='000000', label='')
        spots.append((round(x, 3), round(top + 0.4, 3), 13.4))
    return spots


SPOTS = {}


# =========================================================================================================== Company HQ
def company_hq():
    m = Model('company_hq')
    back_wall(m, panel='#4a2318', plaster='#efe6d2')
    m.g('ledger board')
    led_board(m, -14, 30, 13.6, 28.6)
    m.box((-6, 29.0, 14.7), (22, 31.2, 15.4), 'paint:#2b120c', 'crest board')
    m.box((-5.6, 29.2, 14.62), (21.6, 31.0, 14.7), 'label:THE COMPANY BOOKS', 'crest lettering', north='plate:THE BOARD OF DIRECTORS')
    for cx in (-11, 27):
        m.cyl((cx, 30.1, 14.4), 0.95, 0.6, 'z', 'gauge:clock', 'boardroom clock', cap='gauge:clock')
    b = m.surface('north', face=14.42, title='LEDGER BOARD')
    m.ctl(b, 'digits', 'title', X(8), 27.6, w=28, h=1.4, col='ffd040', label='', fmt='%s')
    m.ctl(b, 'digits', 'pg', X(25.5), 27.6, w=8.5, h=1.2, col='8ad4ff', label='', fmt='%s')
    for r in range(1, 7):
        v = 25.6 - (r - 1) * 2.0
        m.ctl(b, 'lamp', 'r%ds' % r, X(-13.2), v, w=0.6, h=0.6, col='ffb020', label='')
        m.ctl(b, 'digits', 'r%da' % r, X(4.6), v, w=33, h=1.6, col='7cf29a', label='', fmt='%s')
        m.ctl(b, 'digits', 'r%db' % r, X(25.6), v, w=8.4, h=1.6, col='ffd27a', label='', fmt='%s')
    # portraits of the founders
    m.g('portraits')
    for (x0, c) in ((-13.4, '#7a5c3a'), (25.0, '#5c3a2a')):
        m.box((x0, 9.8, 15.0), (x0 + 4.4, 13.0, 15.4), 'paint:#b8902e', 'gilt frame')
        m.box((x0 + 0.4, 10.2, 14.96), (x0 + 4.0, 12.6, 15.0), 'paint:' + c, 'oil portrait')
        m.box((x0 + 1.6, 10.6, 14.94), (x0 + 2.8, 12.0, 14.96), 'paint:#e0b890', 'portrait face')
    # the safe (left end): heavy steel, combination dial and handle - storage for staff only
    m.g('safe')
    m.box((-13.4, 0, 8.8), (-6.6, 8.6, 15.2), 'paint:#2f3a32', 'safe body')
    m.box((-13.1, 0.5, 8.6), (-6.9, 8.1, 8.8), 'paint:#3b4a40', 'safe door')
    m.box((-13.4, 8.6, 8.8), (-6.6, 9.0, 15.2), 'metal:steel', 'safe top')
    m.cyl((-10.0, 5.2, 8.2), 1.0, 0.4, 'z', 'metal:brass', 'combination dial')
    m.cyl((-10.0, 5.2, 8.0), 0.35, 0.2, 'z', 'paint:#1a1a1a', 'dial knob')
    m.box((-11.6, 2.6, 8.2), (-8.4, 3.0, 8.6), 'metal:steel', 'safe handle')
    for y in (1.0, 7.4):
        m.box((-7.2, y, 8.5), (-6.8, y + 0.9, 8.8), 'metal:steel', 'hinge')
    m.box((-12.6, 6.8, 8.75), (-11.0, 7.6, 8.8), 'label:SAFE', 'safe plate')
    sf = m.surface('north', face=8.6, title='SAFE')
    m.ctl(sf, 'zone', 'safe', X(-10.0), 4.3, w=6.0, h=7.4, col='000000', label='')
    m.box((-12.6, 9.0, 10.0), (-11.6, 10.6, 11.2), 'paint:#1f6b3a', 'ledger book')
    m.box((-11.4, 9.0, 10.0), (-10.4, 10.4, 11.0), 'paint:#8a1c1c', 'ledger book')
    m.cyl((-8.4, 9.0, 11.6), 0.7, 0.3, 'y', 'metal:brass', 'globe foot')
    m.box((-8.5, 9.3, 11.5), (-8.3, 10.4, 11.7), 'metal:brass', 'globe stem')
    m.cyl((-8.4, 10.4, 11.6), 1.1, 2.0, 'y', 'paint:#2f6fb0', 'terrestrial globe')
    m.box((-9.0, 11.0, 11.0), (-7.8, 11.8, 12.2), 'paint:#3fa043', 'continent')
    # records cupboard + trophy shelf (right end)
    SPOTS['company_hq'] = cupboard_shelf(m, 18.4, 29.4, ['bk1', 'bk2'], 6, title='TROPHY SHELF', body='#4a2318', door='#5e3020')
    # the executive desk
    desk(m, {('l', 0): 'dl1', ('l', 1): 'dl2', ('l', 2): 'dl3', ('r', 0): 'dr1', ('r', 1): 'dr2', ('r', 2): 'dr3'}, body='#4a2318', front='#6a3424')
    sf = strip(m, 3.6, 8.4)
    buttons(m, sf, 5.0, [('dep10', '1fa34a', '+$10'), ('dep100', '1fa34a', '+$100'), ('wd100', 'd02020', '-$100'), ('tomaint', 'f0d020', 'MAINT'),
                         ('wages', 'f0f0f0', 'WAGES'), ('dividend', '2050d0', 'DIVI'), ('loan', 'e08020', 'LOAN'), ('repay', '20a0a0', 'REPAY')], u0=1.2, step=1.3)
    m.ctl(sf, 'selector', 'page', 12.4, 5.2, w=1.3, h=1.3, col='202020', label='BOOK', pos=['ACCTS', 'STAFF', 'FLEET', 'SHARES', 'REPORT'])
    m.ctl(sf, 'key', 'books', 14.4, 5.2, w=1.3, h=1.3, col='c0a040', label='BOOKS', pos=['OPEN', 'LOCKED'])
    buttons(m, sf, 7.6, [('prev', '1a1a1a', 'PREV'), ('next', '1a1a1a', 'NEXT'), ('found', 'b0b0b0', 'FOUND'), ('join', 'b0b0b0', 'JOIN'),
                         ('hire', '1fa34a', 'HIRE'), ('role', 'f0d020', 'ROLE'), ('wageup', '30c060', 'W+'), ('wagedn', 'c06030', 'W-'),
                         ('fire', 'd02020', 'FIRE'), ('claim', '2050d0', 'CLAIM'), ('pso', '8040c0', 'PSO'), ('report', 'f0f0f0', 'RPT')], u0=1.0, step=1.18, size=0.85)
    hd = hood(m)
    for i, (cid, col, lab, blink) in enumerate((('power', '30e060', '12V', False), ('lowfunds', 'ff3030', 'LOW $', True), ('loanlamp', 'ffb020', 'LOAN', False),
                                                ('owed', 'ffb020', 'OWED', False), ('profit', '30e060', 'PROFIT', False), ('loss', 'ff3030', 'LOSS', False), ('apply', '8ad4ff', 'APPLY', True))):
        u = 1.4 + i * 1.3
        m.ctl(hd, 'lamp', cid, u, 11.0, w=0.6, h=0.6, col=col, label=lab, **({'blink': True} if blink else {}))
        m.ctl(hd, 'label', '', u, 10.45, w=0, h=0, col='e8e8e8', label=lab)
    for i, (cid, col, lab) in enumerate((('oper', 'ffd040', 'OPERATING'), ('maint', '40ff60', 'MAINTENANCE'), ('pl', 'ffd040', 'MONTH P/L'))):
        u = 2.4 + i * 3.6
        m.ctl(hd, 'digits', cid, u, 9.7, w=3.3, h=0.8, col=col, label='', fmt='%s')
        m.ctl(hd, 'label', '', u, 9.15, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(hd, 'gauge', 'repgauge', 13.6, 10.4, w=2.2, h=2.2, col='f2f0e6', label='REP', max=100)
    monitor(m, 3.0, 13.0, 12.4, 17.6, 10.2, ['m1', 'm2', 'm3', 'm4', 'share', 'loanamt'], cols=['ffd040', '7cf29a', '7cf29a', 'b0b0b0', '8ad4ff', 'ff8040'])
    m.g('phone + lamp')
    m.box((14.0, 9.0, 8.6), (15.6, 9.9, 9.8), 'paint:#1a1a1a', 'phone body')
    m.cyl((13.9, 10.3, 9.2), 0.4, 1.8, 'x', 'paint:#1a1a1a', 'handset')
    ph = m.surface('north', face=8.55, title='PHONE')
    m.ctl(ph, 'push', 'phone', X(14.8), 9.45, w=0.7, h=0.5, col='d02020', label='CLERK')
    m.cyl((1.4, 9.0, 12.2), 0.6, 0.3, 'y', 'metal:brass', 'banker lamp foot')
    m.box((1.3, 9.3, 12.1), (1.5, 11.6, 12.3), 'metal:brass', 'banker lamp stem')
    m.box((0.2, 11.4, 11.6), (2.6, 12.2, 12.6), 'paint:#1f6b3a', 'green shade')
    m.box((15.0, 9.0, 11.4), (15.8, 9.6, 12.4), 'paint:#f4f0d8', 'share certificates')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ===================================================================================================== Commodity Exchange
def exchange():
    m = Model('commodity_exchange')
    back_wall(m, panel='#23303a', plaster='#dfe3e6')
    m.g('ticker board')
    led_board(m, -14, 30, 11.6, 29.0)
    m.box((-8, 29.4, 14.7), (24, 31.4, 15.4), 'paint:#10161c', 'header board')
    m.box((-7.6, 29.6, 14.62), (23.6, 31.2, 14.7), 'label:PRIDE COMMODITY EXCHANGE', 'header lettering', north='plate:PRIDE COMMODITY EXCHANGE')
    for (a, b) in ((-13.4, -4.6), (24.4, 29.6)):
        m.box((a, 29.6, 14.5), (b, 31.2, 15.4), 'paint:#10161c', 'readout backing')
    t = m.surface('north', face=14.42, title='TICKER')
    m.ctl(t, 'digits', 'idx', X(-9.0), 30.4, w=8, h=1.3, col='8ad4ff', label='', fmt='%s')
    m.ctl(t, 'digits', 'time', X(27.0), 30.4, w=5, h=1.3, col='ffd040', label='', fmt='%s')
    m.box((-13.4, 27.4, 14.45), (29.4, 27.9, 14.5), 'label:COMMODITY - PRICE - MOVE - 24H', 'column heads', north='label:COMMODITY - PRICE - MOVE - 24H')
    for r in range(1, 9):
        v = 26.4 - (r - 1) * 1.85
        m.ctl(t, 'digits', 'r%dn' % r, X(-4.6), v, w=18, h=1.5, col='ffb020', label='', fmt='%s')
        m.ctl(t, 'digits', 'r%dp' % r, X(10.8), v, w=8.4, h=1.5, col='f0f0f0', label='', fmt='%s')
        m.ctl(t, 'lamp', 'r%du' % r, X(16.6), v, w=0.8, h=0.8, col='30e060', label='')
        m.ctl(t, 'lamp', 'r%dd' % r, X(18.0), v, w=0.8, h=0.8, col='ff3030', label='')
        m.ctl(t, 'digits', 'r%dc' % r, X(24.4), v, w=9.6, h=1.5, col='7cf29a', label='', fmt='%s')
    # warehouse cabinets: tall steel on the left, low with the sample shelf on the right
    m.g('warehouse cabinet')
    m.box((-13.4, 0, 9.0), (-2.6, 11.0, 15.2), 'ral7035', 'warehouse cabinet')
    for k, (a, b) in enumerate(((-13.1, -8.1), (-7.9, -2.9))):
        m.box((a, 0.4, 8.8), (b, 10.6, 9.0), 'paint:#b9bcb8', 'cabinet door')
        m.box((b - 0.8 if k == 0 else a + 0.5, 4.6, 8.5), (b - 0.5 if k == 0 else a + 0.8, 6.6, 8.8), 'metal:alu', 'door handle')
    m.box((-12.4, 9.2, 8.74), (-9.0, 10.2, 8.8), 'label:STORE A', 'store plate')
    m.box((-7.2, 9.2, 8.74), (-3.8, 10.2, 8.8), 'label:STORE B', 'store plate')
    wc = m.surface('north', face=8.8, title='WAREHOUSE')
    m.ctl(wc, 'zone', 'wh1', X(-10.6), 5.5, w=4.8, h=10, col='000000', label='')
    m.ctl(wc, 'zone', 'wh2', X(-5.4), 5.5, w=4.8, h=10, col='000000', label='')
    m.box((-13.4, 11.0, 9.0), (-2.6, 11.3, 15.2), 'metal:steel', 'cabinet top')
    for (x, c) in ((-12.4, '#2a2a2a'), (-10.6, '#c0c0c0'), (-8.6, '#c8a060'), (-6.6, '#e8d8a0')):
        m.box((x, 11.3, 11.0), (x + 1.4, 12.3, 13.0), 'paint:' + c, 'sample crate')
    SPOTS['commodity_exchange'] = cupboard_shelf(m, 18.4, 29.4, ['cash'], 6, title='SAMPLE SHELF', body='#23303a', door='#34444f')
    desk(m, {}, body='#2b3a44', front='#3a4c58', pull='metal:alu')
    sf = strip(m, 3.6, 8.4, col='#1e2226')
    buttons(m, sf, 5.0, [('prev', '1a1a1a', 'PREV'), ('next', '1a1a1a', 'NEXT'), ('chart', '2050d0', 'CHART'), ('bell', 'f0d020', 'BELL')], u0=1.4, step=1.5, size=1.1)
    buttons(m, sf, 7.6, [('sellhand', 'd02020', 'SELL'), ('selllot', 'e08020', 'SELL LOT'), ('buylot', '1fa34a', 'BUY 16')], u0=1.6, step=2.4, size=1.4)
    m.ctl(sf, 'key', 'trading', 13.6, 5.2, w=1.4, h=1.4, col='c0a040', label='TRADING', pos=['OPEN', 'CLOSED'])
    m.ctl(sf, 'digits', 'stock', 11.6, 7.6, w=6.4, h=0.9, col='ffd040', label='', fmt='%s')
    hd = hood(m, col='#1b2025')
    for i, (cid, col, lab, blink) in enumerate((('power', '30e060', '12V', False), ('open', '30e060', 'OPEN', False), ('bull', '30e060', 'BULL', False),
                                                ('bear', 'ff3030', 'BEAR', False), ('volatile', 'ffb020', 'VOLATILE', True))):
        u = 1.6 + i * 1.6
        m.ctl(hd, 'lamp', cid, u, 11.0, w=0.65, h=0.65, col=col, label=lab, **({'blink': True} if blink else {}))
        m.ctl(hd, 'label', '', u, 10.45, w=0, h=0, col='e8e8e8', label=lab)
    for i, (cid, col, lab) in enumerate((('bid', 'ff8080', 'BID'), ('ask', '80ff80', 'ASK'), ('high', 'ffd040', '24H HIGH'), ('low', 'ffd040', '24H LOW'))):
        u = 2.2 + i * 3.4
        m.ctl(hd, 'digits', cid, u, 9.7, w=3.0, h=0.8, col=col, label='', fmt='%s')
        m.ctl(hd, 'label', '', u, 9.15, w=0, h=0, col='e8e8e8', label=lab)
    monitor(m, 2.6, 13.4, 12.2, 17.4, 10.2, ['m1', 'm2', 'm3', 'm4'], cols=['ffd040', '8ad4ff', '7cf29a', 'b0b0b0'])
    m.g('desk things')
    m.box((14.2, 9.0, 8.8), (15.6, 9.4, 10.0), 'paint:#1a1a1a', 'calculator')
    m.box((14.4, 9.4, 9.0), (15.4, 9.45, 9.8), 'paint:#3a6a3a', 'calculator keys')
    m.cyl((1.6, 9.0, 9.4), 0.7, 1.2, 'y', 'metal:brass', 'trading bell')
    m.box((1.5, 10.2, 9.3), (1.7, 10.8, 9.5), 'paint:#2a1a10', 'bell handle')
    m.box((0.6, 9.0, 11.0), (3.0, 9.3, 12.6), 'paint:#f4f0d8', 'price sheets')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ========================================================================================================= Travel Centre
def travel_centre():
    m = Model('travel_centre')
    back_wall(m, panel='#1c3d5a', plaster='#eef1f4')
    m.g('zone map')
    led_board(m, 1.0, 30, 12.0, 28.4)
    m.box((4, 28.8, 14.7), (27, 30.8, 15.4), 'paint:#0e2236', 'zone header')
    m.box((4.4, 29.0, 14.62), (26.6, 30.6, 14.7), 'label:FARE ZONES', 'zone lettering', north='plate:FARE ZONES AND SERVICE')
    zb = m.surface('north', face=14.42, title='ZONE MAP')
    m.ctl(zb, 'digits', 'stn', X(15.5), 27.2, w=27, h=1.3, col='ffd040', label='', fmt='%s')
    for r in range(1, 7):
        v = 25.0 - (r - 1) * 2.1
        m.ctl(zb, 'lamp', 'r%dg' % r, X(29.0), v, w=0.6, h=0.6, col='30e060', label='')
        m.ctl(zb, 'lamp', 'r%dr' % r, X(28.0), v, w=0.6, h=0.6, col='ff3030', label='')
        m.ctl(zb, 'digits', 'r%da' % r, X(19.6), v, w=15.4, h=1.6, col='ffb020', label='', fmt='%s')
        m.ctl(zb, 'digits', 'r%db' % r, X(6.6), v, w=10.4, h=1.6, col='7cf29a', label='', fmt='%s')
    m.g('passenger charter')
    m.box((-14, 12.0, 14.9), (0, 29.0, 15.4), 'paint:#f4f6f8', 'charter board')
    m.box((-13.6, 27.0, 14.84), (-0.4, 28.6, 14.9), 'label:PASSENGER CHARTER', 'charter title', north='plate:OUR PASSENGER CHARTER')
    ch = m.surface('north', face=14.82, title='CHARTER')
    for i, (cid, lab, tgt) in enumerate((('punct', 'ON TIME', '90'), ('crowd', 'SPACE', '75'), ('wait', 'FREQUENT', '70'),
                                          ('fac', 'FACILITIES', '60'), ('clean', 'CLEAN', '75'), ('sat', 'SATISFIED', '70'))):
        v = 25.2 - i * 2.3
        m.ctl(ch, 'label', '', X(-9.6), v, w=0, h=0, col='1a1a1a', label=lab)
        m.ctl(ch, 'digits', 'c' + cid, X(-4.6), v, w=3.4, h=1.4, col='ffd040', label='', fmt='%s')
        m.ctl(ch, 'lamp', 'ok' + cid, X(-2.0), v, w=0.7, h=0.7, col='30e060', label='')
        m.ctl(ch, 'lamp', 'no' + cid, X(-1.0), v, w=0.7, h=0.7, col='ff3030', label='')
        m.ctl(ch, 'label', '', X(-6.8), v - 0.9, w=0, h=0, col='606060', label='TARGET ' + tgt + '%')
    # leaflet rack and stationery cupboard (right as you face it = low x)
    SPOTS['travel_centre'] = cupboard_shelf(m, -13.4, -2.6, ['cup1', 'cup2'], 6, title='LEAFLET RACK', body='#1c3d5a', door='#2a5478')
    # the counter: a ticket office desk with the glass screen
    desk(m, {('l', 0): 'till'}, body='#1c3d5a', front='#2a5478', pull='metal:alu')
    m.g('counter screen')
    for x in (0.2, 15.4):
        m.box((x, 9.0, 3.0), (x + 0.4, 17.0, 3.4), 'metal:alu', 'screen post')
    m.box((0.2, 17.0, 3.0), (15.8, 17.4, 3.4), 'metal:alu', 'screen header')
    m.box((0.6, 12.6, 3.15), (15.4, 17.0, 3.25), 'glass', 'counter glass')
    m.box((4.0, 9.0, 3.0), (12.0, 9.5, 3.4), 'metal:steel', 'pass-through tray')
    m.box((2.0, 17.4, 3.0), (14.0, 18.6, 3.4), 'paint:#0e2236', 'window sign', north='plate:TICKETS AND TRAVEL')
    sf = strip(m, 3.6, 8.4, col='#1e2a36')
    buttons(m, sf, 5.0, [('zlo-', '1a1a1a', 'FROM-'), ('zlo+', '1a1a1a', 'FROM+'), ('zhi-', '1a1a1a', 'TO-'), ('zhi+', '1a1a1a', 'TO+'),
                         ('print', '1fa34a', 'PRINT'), ('refund', 'e08020', 'REFUND')], u0=1.2, step=1.35)
    m.ctl(sf, 'selector', 'ptype', 10.4, 5.2, w=1.3, h=1.3, col='202020', label='PASS', pos=['DAY', '7-DAY', '28-DAY'])
    m.ctl(sf, 'selector', 'conc', 12.4, 5.2, w=1.3, h=1.3, col='202020', label='FARE', pos=['ADULT', 'CHILD', 'SENIOR'])
    buttons(m, sf, 7.6, [('up', '1a1a1a', 'UP'), ('down', '1a1a1a', 'DOWN'), ('clean', '20a0a0', 'CLEAN'), ('complain', 'f0d020', 'CMPLT'),
                         ('announce', 'd02020', 'SORRY')], u0=1.2, step=1.35)
    m.ctl(sf, 'digits', 'price', 10.8, 7.6, w=3.6, h=0.9, col='ffd040', label='', fmt='%s')
    m.ctl(sf, 'digits', 'zones', 14.2, 7.6, w=2.8, h=0.9, col='8ad4ff', label='', fmt='%s')
    hd = hood(m, y1=11.6, col='#16222e')
    for i, (cid, col, lab, blink) in enumerate((('power', '30e060', '12V', False), ('peak', 'ffb020', 'PEAK', False), ('disrupt', 'ff3030', 'DISRUPT', True),
                                                ('full', 'ffb020', 'CROWDED', True), ('dirty', 'c08040', 'DIRTY', False))):
        u = 1.4 + i * 1.5
        m.ctl(hd, 'lamp', cid, u, 10.9, w=0.6, h=0.6, col=col, label=lab, **({'blink': True} if blink else {}))
        m.ctl(hd, 'label', '', u, 10.35, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(hd, 'digits', 'pass', 3.8, 9.6, w=6.2, h=0.8, col='7cf29a', label='', fmt='%s')
    m.ctl(hd, 'digits', 'today', 9.6, 9.6, w=3.4, h=0.8, col='ffd040', label='', fmt='%s')
    m.ctl(hd, 'gauge', 'satg', 13.6, 10.3, w=2.2, h=2.2, col='f2f0e6', label='SAT %', max=100)
    monitor(m, 3.4, 12.6, 11.9, 15.4, 10.0, ['waitn', 'complaints'], cols=['7cf29a', 'ffb020'], stand_y=11.6)
    m.g('counter things')
    m.box((13.6, 9.0, 8.6), (15.4, 9.8, 10.2), 'paint:#d8d8d4', 'ticket printer')
    m.box((13.9, 9.4, 8.5), (15.1, 9.5, 8.6), 'paint:#1a1a1a', 'ticket slot')
    m.box((0.6, 9.0, 8.8), (2.6, 9.3, 10.4), 'paint:#2a2a2a', 'card reader')
    m.box((1.0, 9.3, 9.0), (2.2, 9.35, 9.6), 'paint:#30e060', 'reader screen')
    m.box((-0.6, 0, 1.0), (-0.2, 3.0, 1.4), 'metal:steel', 'queue post')
    m.box((-0.8, 0, 0.8), (0, 0.2, 1.6), 'metal:steel', 'queue post foot')
    m.box((-0.5, 2.6, 1.1), (-0.3, 2.8, 1.3), 'paint:#d02020', 'queue rope')
    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


# ===================================================================================================== Container Terminal
def container_terminal():
    m = Model('container_terminal')
    m.g('control room floor')
    m.box((-14, 0, 0.6), (30, 0.4, 15.6), 'paint:#4a4f54', 'chequer plate floor')
    for x in range(-13, 30, 3):
        m.box((x, 0.4, 1.0), (x + 0.2, 0.45, 15.2), 'paint:#5a6066', 'floor rib')
    m.g('back rail')                               # the operator looks over it into the yard
    m.handrail([(-13.6, 15.2), (29.6, 15.2)], 0.4, h=6.0, r=0.25, mat='paint:ral1023')
    for x in (-13.6, -3.0, 8.0, 19.0, 29.6):
        m.cyl((x, 0.4, 15.2), 0.25, 6.0, 'y', 'paint:ral1023', 'rail post')
    m.box((-13.6, 3.0, 15.0), (29.6, 3.25, 15.4), 'paint:ral1023', 'knee rail')
    m.box((-6.0, 6.6, 15.1), (22.0, 8.4, 15.3), 'paint:#10304a', 'yard sign', north='plate:CONTAINER YARD - CRANE 1')
    m.g('locker')
    m.box((-13.4, 0.4, 9.6), (-7.0, 14.0, 15.0), 'ral7035', 'operator locker')
    m.box((-13.2, 0.8, 9.4), (-7.2, 13.6, 9.6), 'paint:#b9bcb8', 'locker door')
    for y in range(2, 13, 1):
        m.box((-12.6, y, 9.35), (-11.0, y + 0.2, 9.4), 'paint:#8a8c88', 'locker vent')
    m.box((-8.2, 6.6, 9.1), (-7.9, 8.2, 9.4), 'metal:alu', 'locker handle')
    lk = m.surface('north', face=9.4, title='LOCKER')
    m.ctl(lk, 'zone', 'locker', X(-10.2), 7.2, w=5.8, h=12.6, col='000000', label='')
    for (x, c) in ((-12.6, '#f0c020'), (-10.6, '#f0f0f0'), (-8.6, '#f08020')):
        m.box((x, 14.0, 11.0), (x + 1.6, 14.9, 12.6), 'paint:' + c, 'hard hat')
    m.g('hi-vis rack')
    m.box((18.6, 0.4, 13.6), (29.0, 9.0, 15.0), 'paint:#3a3f44', 'tool board')
    for i, c in enumerate(('#f0d020', '#f08020', '#f0d020')):
        m.box((19.6 + i * 3.0, 3.6, 13.2), (21.8 + i * 3.0, 8.0, 13.6), 'paint:' + c, 'hi-vis vest')
        m.box((19.6 + i * 3.0, 5.2, 13.15), (21.8 + i * 3.0, 5.6, 13.2), 'paint:#d8d8d8', 'reflective band')
    m.box((19.0, 0.4, 10.0), (23.0, 3.0, 13.0), 'paint:#c03020', 'twistlock bin')
    for i in range(3):
        m.box((19.4 + i * 1.2, 3.0, 10.6), (20.2 + i * 1.2, 3.6, 12.2), 'metal:steel', 'spare twistlock')
    # the operator's console: steel desk with two joystick pods
    m.g('console')
    m.box((0, 0.4, 4.0), (16, 8.2, 12.6), 'paint:#30363c', 'console body')
    m.box((-0.2, 8.2, 3.2), (16.2, 8.8, 13.0), 'paint:#22272c', 'console top')
    m.box((0.4, 1.0, 3.9), (15.6, 7.6, 4.0), 'louvre', 'console vents')
    sf = m.surface('up', face=16 - 8.95, title='CRANE DESK')
    m.box((0.6, 8.8, 3.6), (15.4, 8.95, 9.0), 'paint:#1a1e22', 'control plate')
    for (cx, lab) in ((13.6, 'TROLLEY'), (2.4, 'HOIST')):
        m.box((cx - 1.4, 8.8, 5.0), (cx + 1.4, 9.4, 7.8), 'paint:#2a2f34', 'joystick pod', north='label:' + lab)
        m.cyl((cx, 9.4, 6.4), 0.45, 0.4, 'y', 'rubber', 'joystick boot')
        m.box((cx - 0.15, 9.8, 6.25), (cx + 0.15, 11.4, 6.55), 'metal:steel', 'joystick lever')
        m.cyl((cx, 11.4, 6.4), 0.4, 0.9, 'y', 'paint:#1a1a1a', 'joystick grip')
        m.box((cx - 0.5, 12.3, 6.0), (cx + 0.5, 12.4, 6.8), 'paint:#d02020', 'deadman button')
    # trolley buttons beside the trolley stick, hoist buttons beside the hoist stick
    for (cid, x, v, lab) in (('tl', 11.4, 5.4, 'IN'), ('tr', 11.4, 7.4, 'OUT'), ('hu', 4.6, 5.4, 'UP'), ('hd', 4.6, 7.4, 'DOWN')):
        m.ctl(sf, 'push', cid, X(x), v, w=0.9, h=0.9, col='1a1a1a', label=lab)
        m.ctl(sf, 'label', '', X(x), v - 0.75, w=0, h=0, col='e8e8e8', label=lab)
    buttons(m, sf, 4.3, [('lock', '1fa34a', 'LOCK'), ('unlock', 'd02020', 'UNLOCK'), ('pick', '2050d0', 'PICK'), ('drop', '2050d0', 'DROP')], u0=5.9, step=1.3, size=0.9)
    m.ctl(sf, 'selector', 'from', 6.3, 6.1, w=1.1, h=1.1, col='202020', label='FROM', pos=['APRON', 'RAIL', 'ROW A', 'ROW B'])
    m.ctl(sf, 'selector', 'to', 7.9, 6.1, w=1.1, h=1.1, col='202020', label='TO', pos=['APRON', 'RAIL', 'ROW A', 'ROW B'])
    m.ctl(sf, 'push', 'go', 9.6, 6.1, w=0.9, h=0.9, col='f0f0f0', label='GO')
    m.ctl(sf, 'label', '', 9.6, 5.35, w=0, h=0, col='e8e8e8', label='GO')
    m.ctl(sf, 'push', 'auto', 8.0, 8.1, w=1.5, h=0.9, col='f0d020', label='MOVE BOX')
    m.ctl(sf, 'label', '', 8.0, 7.4, w=0, h=0, col='e8e8e8', label='MOVE BOX')
    m.ctl(sf, 'mushroom', 'estop', X(14.6), 4.3, w=1.1, h=1.1, col='d02020', label='E-STOP')
    m.ctl(sf, 'key', 'power', X(1.4), 4.3, w=1.0, h=1.0, col='c0a040', label='CRANE', pos=['ON', 'OFF'])
    m.g('gate desk')                                # the gate clerk's buttons on the left wing
    m.box((-6.6, 0.4, 3.6), (-0.4, 8.0, 9.0), 'paint:#30363c', 'gate desk')
    m.box((-6.8, 8.0, 3.4), (-0.2, 8.4, 9.2), 'paint:#22272c', 'gate desk top')
    gd = m.surface('up', face=16 - 8.4, title='GATE')
    from gen_bb_biz import terminal_gate_buttons                 # + APPT (truck appointments) and WASH (her next-48 #15, #17)
    terminal_gate_buttons(m, gd)
    m.ctl(gd, 'digits', 'fees', X(-3.6), 8.0, w=4.6, h=0.8, col='ffd040', label='', fmt='%s')
    m.box((-6.4, 8.4, 7.4), (-4.4, 9.4, 8.8), 'paint:#d8d8d4', 'seal printer')
    # instrument hood + monitors
    hd = hood(m, y0=8.8, y1=12.0, z=9.4, col='#1b2025')
    for i, (cid, col, lab, blink) in enumerate((('pw', '30e060', '12V', False), ('on', '30e060', 'CRANE', False), ('locked', 'ffb020', 'LOCKED', False),
                                                ('moving', 'ffb020', 'MOVING', True), ('car', '8ad4ff', 'CAR', False), ('wh', '8ad4ff', 'WHSE', False),
                                                ('fault', 'ff3030', 'FAULT', True), ('overdue', 'ff3030', 'OVERDUE', False))):
        u = 1.2 + i * 1.4
        m.ctl(hd, 'lamp', cid, u, 11.3, w=0.55, h=0.55, col=col, label=lab, **({'blink': True} if blink else {}))
        m.ctl(hd, 'label', '', u, 10.8, w=0, h=0, col='e8e8e8', label=lab)
    for i, (cid, col) in enumerate((('box', 'ffd040'), ('wt', '7cf29a'), ('dst', '8ad4ff'))):
        m.ctl(hd, 'digits', cid, 2.4 + i * 3.6, 9.9, w=3.3, h=0.8, col=col, label='', fmt='%s')
    m.ctl(hd, 'gauge', 'load', 13.8, 10.4, w=2.2, h=2.2, col='f2f0e6', label='LOAD T', max=40)
    monitor(m, 1.0, 9.0, 12.4, 17.0, 10.0, ['stat', 'pos', 'fromto', 'cnt'], cols=['ffd040', '7cf29a', '8ad4ff', 'b0b0b0'], stand_y=12.0)
    monitor(m, 9.4, 15.6, 12.4, 17.0, 10.0, ['m1', 'm2', 'm3', 'm4'], cols=['f0f0f0', '8ad4ff', '7cf29a', '7cf29a'], stand_y=12.0)
    m.g('radio')
    m.box((15.0, 8.8, 10.6), (15.8, 10.4, 11.4), 'paint:#1a1a1a', 'yard radio')
    m.box((15.3, 10.4, 10.9), (15.45, 12.6, 11.05), 'paint:#1a1a1a', 'radio aerial')
    m.terminal_12v(16.2, 1.0, 8.0, 'east')
    m.save(item_gui=0.28)


def container_item_texture():
    from PIL import Image
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(4, 13):
        for x in range(1, 15):
            c = (176, 58, 46, 255) if x % 2 else (150, 46, 36, 255)
            if y in (4, 12) or x in (1, 14): c = (90, 30, 24, 255)
            im.putpixel((x, y), c)
    for (x, y) in ((1, 4), (14, 4), (1, 12), (14, 12)):
        im.putpixel((x, y), (40, 40, 40, 255))
    for x in range(4, 11):
        im.putpixel((x, 7), (240, 240, 240, 255))
    os.makedirs(os.path.join(ASSETS, 'textures/items'), exist_ok=True)
    im.save(os.path.join(ASSETS, 'textures/items/shipping_container.png'))


RECIPES = {
    'company_hq': (['GPG', 'BDB', 'WSW'], {'G': 'minecraft:gold_ingot', 'P': 'minecraft:paper', 'B': 'minecraft:bookshelf', 'D': 'irextras:freight_office', 'W': 'minecraft:planks', 'S': 'minecraft:iron_block'}),
    'commodity_exchange': (['RLR', 'GDG', 'ICI'], {'R': 'minecraft:redstone', 'L': 'minecraft:redstone_lamp', 'G': 'minecraft:glowstone_dust', 'D': 'irextras:freight_office', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest'}),
    'travel_centre': (['GLG', 'PTP', 'WCW'], {'G': 'minecraft:glass_pane', 'L': 'minecraft:redstone_lamp', 'P': 'minecraft:paper', 'T': 'irextras:ticket_machine', 'W': 'minecraft:planks', 'C': 'minecraft:chest'}),
    'container_terminal': (['III', 'PDP', 'ICI'], {'I': 'minecraft:iron_block', 'P': 'minecraft:piston', 'D': 'irextras:freight_office', 'C': 'minecraft:chest'}),
    'shipping_container': (['III', 'ICI', 'III'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:chest'}),
}

LANG = {
    'tile.irextras.company_hq.name': 'Company HQ',
    'tile.irextras.commodity_exchange.name': 'Commodity Exchange',
    'tile.irextras.travel_centre.name': 'Travel Centre',
    'tile.irextras.container_terminal.name': 'Container Terminal',
    'item.irextras.shipping_container.name': 'Shipping Container',
}

if __name__ == '__main__':
    company_hq(); exchange(); travel_centre(); container_terminal()
    for n in ('company_hq', 'commodity_exchange', 'travel_centre', 'container_terminal'):
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/shipping_container'}}, open(os.path.join(ASSETS, 'models/item/shipping_container.json'), 'w'), indent=1)
    container_item_texture()
    for n, (pat, key) in RECIPES.items():
        ing = lambda v: {'item': v, 'data': 0} if v == 'minecraft:planks' else {'item': v}   # items with subtypes need their data
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# railway economy (her list #46-50)\n' + '\n'.join(add) + '\n')
    print('shelf spots (model px, for TileFurniture.shelfSpots):')
    for k, v in SPOTS.items(): print(' ', k, v)
