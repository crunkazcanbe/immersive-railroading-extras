#!/usr/bin/env python3
"""Train crew + revenue protection (batch 5 #7 #9): the Crew Office (roster board, sign-on terminal, four crew lockers,
staff fridge, kettle + break room, notice board ledge) and the Revenue Protection Office (case file board, case
terminal, filing cabinet, evidence store, fines ledger shelf, CCTV link monitors, ticket machine charging rack). Both
are three-block wide desks (BlockWideDesk) with a 12 V terminal AND a mains isolator. Models face north; controls on
north surfaces sit at u = 16 - x (centre), on 'up' surfaces v = z. Live controls: crew/TileCrewOffice.java and
crew/TileRevenueOffice.java (ids must match). Writes models, controls, blockstates, item models, recipes, lang lines."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing
from gen_bb_economy import back_wall, led_board, desk, strip, monitor, hood, X

SPOTS = {}
GREY = '505860'


def fascia(m, text, col):
    m.g('fascia')
    m.box((-14, 29.6, 14.6), (30, 32.0, 15.4), 'paint:' + col, 'fascia board')
    m.box((-4, 30.0, 14.5), (20, 31.6, 14.6), 'plate', 'fascia lettering', north='label:' + text)


def wall_shelf(m, x0, x1, y, n, title):
    """a wall ledge on brackets: its top is a shelf whose spots show what is put there"""
    m.box((x0, y - 0.4, 12.6), (x1, y, 15.4), 'wood', 'ledge')
    m.box((x0, y, 15.0), (x1, y + 0.6, 15.4), 'paint:#3e2716', 'ledge lip')
    for x in (x0 + 0.8, x1 - 1.2):
        m.box((x, y - 2.2, 14.6), (x + 0.4, y - 0.4, 15.4), 'metal:steel', 'bracket')
        m.box((x, y - 0.8, 13.0), (x + 0.4, y - 0.4, 14.6), 'metal:steel', 'bracket arm')
    sh = m.surface('up', face=16 - y, title=title)
    spots = []
    sw = (x1 - x0 - 0.8) / n
    for k in range(n):
        x = x0 + 0.4 + sw * (k + 0.5)
        m.ctl(sh, 'zone', 'sh%d' % (k + 1), X(x), 14.0, w=sw - 0.05, h=2.6, col='000000', label='')
        spots.append((round(x, 3), round(y, 3), 14.0))
    return spots


def locker(m, sf, x0, cid, num):
    """a full-height steel crew locker: door, vents, hinge, handle with padlock hasp, name card"""
    m.box((x0, 0, 9.4), (x0 + 3.2, 19.0, 15.4), 'paint:#3c5878', 'locker carcass')
    m.box((x0 + 0.15, 0.5, 9.25), (x0 + 3.05, 18.6, 9.4), 'paint:#4a6a90', 'locker door')
    for y in (14.0, 14.6, 15.2, 15.8, 16.4):
        m.box((x0 + 0.7, y, 9.2), (x0 + 2.5, y + 0.25, 9.25), 'paint:#22344a', 'vent slot')
    for y in (2.0, 9.0, 16.0):
        m.box((x0 + 0.02, y, 9.1), (x0 + 0.2, y + 1.0, 9.4), 'metal:steel', 'hinge')
    m.box((x0 + 2.4, 8.4, 8.9), (x0 + 2.7, 10.2, 9.25), 'metal:steel', 'locker handle')
    m.box((x0 + 2.3, 7.4, 8.95), (x0 + 2.8, 8.2, 9.25), 'metal:brass', 'padlock hasp')
    m.box((x0 + 0.5, 11.0, 9.22), (x0 + 2.2, 11.8, 9.25), 'label:%d' % num, 'number card')
    m.ctl(sf, 'zone', cid, X(x0 + 1.6), 9.5, w=2.9, h=18.0, col='000000', label='')


def chair(m, x, z, col):
    m.box((x, 4.0, z), (x + 3.4, 4.6, z + 3.4), 'paint:' + col, 'chair seat')
    m.box((x, 4.6, z + 3.0), (x + 3.4, 9.0, z + 3.4), 'paint:' + col, 'chair back')
    for dx in (0.2, 2.9):
        for dz in (0.2, 2.9):
            m.box((x + dx, 0, z + dz), (x + dx + 0.3, 4.0, z + dz + 0.3), 'metal:steel', 'chair leg')


# ============================================================================================================ Crew Office
def crew_office():
    m = Model('crew_office')
    back_wall(m, panel='#24324a', plaster='#e6e8e2', top=29.6)
    fascia(m, 'CREW OFFICE - SIGN ON', '#1c2840')

    # ---- roster board (the whole railway's crew) -------------------------------------------------------------
    m.g('roster board')
    led_board(m, 0.4, 15.6, 19.8, 29.0)
    m.box((0.4, 29.0, 14.62), (15.6, 29.5, 14.7), 'plate', 'roster title', north='label:CREW ROSTER - DIAGRAMS')
    rb = m.surface('north', face=14.42, title='ROSTER')
    for r in range(1, 9):
        v = 28.3 - (r - 1) * 1.08
        m.ctl(rb, 'lamp', 'r%ds' % r, X(1.0), v, w=0.5, h=0.5, col='ffb020', label='')
        m.ctl(rb, 'digits', 'r%d' % r, X(8.4), v, w=14.0, h=0.92, col='7cf29a', label='', fmt='%s')
    m.ctl(rb, 'digits', 'clock', X(12.8), 20.4, w=5.0, h=0.8, col='ffd040', label='', fmt='%s')

    # ---- notice board + ledge (shelf) and four crew lockers (left block) -------------------------------------
    m.g('notice board')
    m.box((-13.4, 21.4, 15.0), (-0.8, 28.6, 15.4), 'paint:#b48a5a', 'cork board')
    m.box((-13.6, 21.2, 14.9), (-0.6, 21.4, 15.4), 'wood', 'frame')
    m.box((-13.6, 28.6, 14.9), (-0.6, 28.8, 15.4), 'wood', 'frame')
    notes = [('#fafaf2', 'label:ROSTER'), ('#f6e27a', 'label:REST 11H'), ('#a8d8f0', 'label:SAFETY'), ('#fafaf2', 'label:BREAKS'), ('#f4c0d0', 'label:PRIDE'), ('#d8f0c0', 'label:UNION')]
    for i, (c, lab) in enumerate(notes):
        x0, y0 = -13.0 + (i % 3) * 4.1, 22.0 + (i // 3) * 3.3
        m.box((x0, y0, 14.92), (x0 + 3.5, y0 + 2.8, 15.0), 'paint:' + c, 'notice', north=lab)
        m.box((x0 + 1.6, y0 + 2.4, 14.82), (x0 + 1.9, y0 + 2.7, 14.92), 'paint:#d02020', 'drawing pin')
    SPOTS['crew_office'] = wall_shelf(m, -13.4, -0.8, 20.6, 6, 'NOTICE LEDGE')
    m.g('crew lockers')
    lk = m.surface('north', face=9.2, title='LOCKERS')
    for i in range(4):
        locker(m, lk, -13.6 + i * 3.3, 'lk%d' % (i + 1), i + 1)
    m.box((-13.8, 19.0, 9.2), (-0.2, 19.4, 15.4), 'paint:#2c3e58', 'locker top')
    m.box((-13.4, 0, 6.0), (-1.0, 2.2, 8.2), 'wood', 'changing bench')
    for x in (-13.0, -1.8):
        m.box((x, 0, 6.4), (x + 0.6, 2.0, 7.8), 'metal:steel', 'bench leg')
    m.box((-12.0, 2.2, 6.4), (-9.6, 3.4, 7.8), 'paint:#ff7a1a', 'folded hi-vis')
    m.box((-6.0, 2.2, 6.2), (-3.6, 3.0, 7.6), 'paint:#1c2740', 'uniform cap')
    m.box((-5.6, 2.2, 5.6), (-4.0, 2.4, 6.2), 'paint:#121212', 'cap peak')

    # ---- the sign-on desk (middle block) ---------------------------------------------------------------------
    desk(m, {('l', 1): 'dr'}, top=9.0, body='#24324a', front='#34486a', pull='metal:steel')
    sf = strip(m, 3.4, 8.6, top=9.0)
    pushes(m, sf, [('signon', '1fa34a', 'SIGN ON'), ('signoff', 'd02020', 'SIGN OFF'), ('brk', 'f0d020', 'BREAK'), ('hire', 'b0b0b0', 'HIRE'),
                   ('dismiss', '8a2020', 'DISMISS'), ('print', 'f0f0f0', 'PRINT'), ('test', 'f0f0f0', 'TEST')], 1.4, 4.5, step=2.05, size=0.85)
    pushes(m, sf, [('prev', GREY, 'STAFF<'), ('next', GREY, 'STAFF>'), ('tprev', GREY, 'TRN<'), ('tnext', GREY, 'TRN>'), ('alloc', '2050d0', 'ALLOC'),
                   ('lprev', GREY, 'LINE<'), ('lnext', GREY, 'LINE>'), ('beat', '8040c0', 'BEAT')], 1.2, 6.2, step=1.8, size=0.8)
    pushes(m, sf, [('sh-', GREY, 'SHIFT-'), ('sh+', GREY, 'SHIFT+'), ('ln-', GREY, 'LEN-'), ('ln+', GREY, 'LEN+'), ('nodiag', 'c08020', 'NO DIAG')], 1.2, 7.9, step=1.8, size=0.8)
    m.ctl(sf, 'selector', 's_role', 11.2, 7.8, w=1.2, h=1.2, col='202020', label='ROLE', pos=['DRIVER', 'GUARD', 'INSPECTOR'])
    m.ctl(sf, 'label', '', 11.2, 6.9, w=0, h=0, col='e8e8e8', label='ROLE')
    m.ctl(sf, 'key', 'k_auto', 13.8, 7.8, w=0.9, h=0.9, col='c0a040', label='AUTO', pos=['MANUAL', 'AUTO'])
    m.ctl(sf, 'label', '', 13.8, 6.9, w=0, h=0, col='e8e8e8', label='AUTO ROSTER')
    hd = hood(m, z=9.8)
    lamps(m, hd, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('autol', BLUE, 'AUTO'), ('overh', RED, 'OVER HRS', True), ('fatl', AMBER, 'FATIGUE', True),
                  ('restl', AMBER, 'REST'), ('nogd', RED, 'NO GUARD', True)], 1.2, 11.1, step=1.5, size=0.5, lab_dv=0.55)
    m.ctl(hd, 'digits', 'ndty', 12.4, 11.1, w=2.4, h=0.7, col='7cf29a', label='', fmt='%d ON')
    m.ctl(hd, 'digits', 'rolesel', 14.6, 11.1, w=1.6, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(hd, 'digits', 'tsel', 4.6, 9.8, w=7.2, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(hd, 'digits', 'lsel', 12.0, 9.8, w=6.2, h=0.7, col='8ad4ff', label='', fmt='%s')
    monitor(m, 2.0, 14.0, 12.2, 19.2, 10.6, ['t1', 't2', 't3', 't4', 't5', 't6'], cols=['ffd040', '5bcefa', '7cf29a', '7cf29a', 'ffb020', '8a94a0'], stand_y=11.8)
    mon2 = m.surface('north', face=10.5, title='TERMINAL STATUS')
    m.box((2.0, 11.8, 10.55), (14.0, 12.2, 11.2), 'paint:#1c1e20', 'status bar')
    m.ctl(mon2, 'digits', 'msg', X(8), 12.0, w=11.0, h=0.35, col='8ad4ff', label='', fmt='%s')
    m.g('desk things')
    m.box((0.8, 9.0, 11.6), (2.6, 9.6, 13.4), 'paint:#2a2c2e', 'card reader')
    m.box((1.0, 9.6, 12.0), (2.4, 9.7, 13.0), 'paint:#60c8f0', 'reader window')
    m.box((14.0, 9.0, 11.0), (15.6, 9.9, 12.4), 'paint:#1a1a1a', 'radio base')
    m.box((14.9, 9.9, 11.4), (15.1, 11.8, 11.6), 'paint:#1a1a1a', 'radio aerial')
    m.box((0.8, 9.0, 13.6), (3.0, 9.4, 15.0), 'paint:#f4f0d8', 'route cards')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')

    # ---- break room: kitchenette, kettle, fridge, table + chairs (right block) ---------------------------------
    m.g('kitchenette')
    m.box((17.0, 0, 10.4), (25.4, 8.6, 15.4), 'paint:#e8e4da', 'kitchen base unit')
    for x0 in (17.2, 21.3):
        m.box((x0, 0.6, 10.25), (x0 + 3.9, 8.0, 10.4), 'paint:#f2efe6', 'cupboard door')
        m.box((x0 + 3.2, 6.4, 10.0), (x0 + 3.5, 7.4, 10.25), 'metal:steel', 'door pull')
    m.box((16.8, 8.6, 10.2), (25.6, 9.2, 15.4), 'paint:#3a3a3e', 'worktop')
    m.box((17.4, 9.2, 13.6), (20.6, 9.5, 15.2), 'metal:steel', 'sink')
    m.cyl((19.0, 9.5, 15.0), 0.25, 2.2, 'y', 'metal:steel', 'tap')
    m.box((18.8, 11.4, 14.0), (19.2, 11.7, 15.0), 'metal:steel', 'tap spout')
    m.g('kettle')
    m.cyl((22.6, 9.2, 12.2), 1.1, 0.3, 'y', 'paint:#202024', 'kettle base')
    m.cyl((22.6, 9.5, 12.2), 1.0, 2.8, 'y', 'metal:steel', 'kettle')
    m.box((23.5, 10.4, 11.9), (24.2, 12.0, 12.5), 'paint:#202024', 'kettle handle')
    m.box((21.2, 11.4, 12.0), (21.7, 11.8, 12.4), 'metal:steel', 'spout')
    m.box((22.0, 9.8, 11.1), (23.2, 11.8, 11.2), 'glass', 'water window')
    kt = m.surface('north', face=11.0, title='KETTLE')
    m.ctl(kt, 'zone', 'kettle', X(22.6), 11.0, w=2.2, h=3.0, col='000000', label='')
    m.box((24.4, 9.2, 13.0), (25.2, 10.4, 13.8), 'paint:#f4f4f0', 'mug')
    m.box((24.4, 9.2, 14.2), (25.2, 10.4, 15.0), 'paint:#e40303', 'mug')
    m.box((17.6, 9.2, 11.0), (18.8, 10.8, 12.2), 'paint:#c8a020', 'tea caddy', north='label:TEA')
    m.box((19.2, 9.2, 11.0), (20.2, 10.4, 12.0), 'glass', 'sugar jar')
    ks = m.surface('north', face=10.15, title='KETTLE PANEL')
    m.box((20.8, 5.6, 10.18), (25.0, 8.2, 10.25), 'paint:#2b2f33', 'kettle switch plate')
    pushes(m, ks, [('boil', 'd02020', 'BOIL'), ('tea', '1fa34a', 'TEA')], X(21.6), 7.4, step=-1.6, size=0.7, lab_dv=0.7)
    lamps(m, ks, [('boill', RED, 'ON'), ('teal', GREEN, 'BREW')], X(21.6), 6.2, step=-1.6, size=0.4, lab_dv=0.45)
    m.ctl(ks, 'gauge', 'waterg', X(24.4), 7.0, w=1.0, h=1.0, col='f2f0e6', label='WATER', max=100)
    m.ctl(ks, 'digits', 'cups', X(24.4), 5.9, w=1.0, h=0.4, col='7cf29a', label='', fmt='%s')
    m.g('fridge')
    m.box((25.8, 0, 10.0), (29.6, 15.0, 15.4), 'paint:#f2f2ee', 'fridge')
    m.box((25.9, 0.4, 9.85), (29.5, 14.6, 10.0), 'paint:#fafaf6', 'fridge door')
    m.box((29.0, 6.0, 9.6), (29.3, 11.0, 9.85), 'metal:steel', 'fridge handle')
    m.box((26.4, 12.6, 9.82), (28.4, 13.8, 9.85), 'paint:#e40303', 'magnet')
    m.box((26.6, 11.0, 9.82), (28.2, 12.2, 9.85), 'paint:#24408e', 'magnet')
    fr = m.surface('north', face=9.8, title='FRIDGE')
    m.ctl(fr, 'zone', 'fr', X(27.7), 7.5, w=3.4, h=14.0, col='000000', label='')
    m.g('break table')
    m.box((18.6, 6.2, 1.2), (25.0, 6.8, 7.0), 'wood', 'table top')
    m.cyl((21.8, 0, 4.1), 0.4, 6.2, 'y', 'metal:steel', 'table pedestal')
    m.box((20.0, 0, 2.8), (23.6, 0.3, 5.4), 'metal:steel', 'table foot')
    chair(m, 16.4, 2.2, '#3c5878')
    chair(m, 25.4, 2.2, '#3c5878')
    m.box((19.4, 6.8, 2.0), (21.0, 7.0, 3.6), 'paint:#f4f0d8', 'newspaper')
    m.box((23.0, 6.8, 4.2), (23.8, 7.8, 5.0), 'paint:#732982', 'mug')
    m.g('wall clock + first aid')
    m.cyl((22.0, 24.0, 15.2), 1.8, 0.2, 'z', 'paint:#f4f4f0', 'wall clock face')
    m.box((21.9, 24.0, 15.0), (22.1, 25.4, 15.1), 'paint:#101010', 'hour hand')
    m.box((22.0, 23.9, 15.0), (23.2, 24.1, 15.1), 'paint:#101010', 'minute hand')
    m.box((26.0, 17.0, 14.0), (29.0, 20.0, 15.4), 'paint:#18a040', 'first aid box', north='label:FIRST AID')
    mains_box(m, 30.0, 16.0, 12.6, 'east')
    m.save(item_gui=0.26)
    return m


# =============================================================================================== Revenue Protection Office
def revenue_office():
    m = Model('revenue_office')
    back_wall(m, panel='#3e1a26', plaster='#ecebe4', top=29.6)
    fascia(m, 'REVENUE PROTECTION', '#2e1018')

    # ---- case file board (middle, on the wall) -----------------------------------------------------------------
    m.g('case board')
    led_board(m, 0.4, 15.6, 20.6, 29.0)
    m.box((0.4, 29.0, 14.62), (15.6, 29.5, 14.7), 'plate', 'case title', north='label:CASE FILE - PENALTY FARES')
    cb = m.surface('north', face=14.42, title='CASES')
    for r in range(1, 7):
        v = 28.2 - (r - 1) * 1.2
        m.ctl(cb, 'lamp', 'c%ds' % r, X(1.0), v, w=0.5, h=0.5, col='ffb020', label='')
        m.ctl(cb, 'digits', 'c%d' % r, X(8.4), v, w=14.0, h=1.0, col='ffd040', label='', fmt='%s')
    m.ctl(cb, 'digits', 'st1', X(8.0), 21.9, w=14.6, h=0.6, col='7cf29a', label='', fmt='%s')
    m.ctl(cb, 'digits', 'st2', X(8.0), 21.2, w=14.6, h=0.6, col='8a94a0', label='', fmt='%s')

    # ---- filing cabinet, evidence store, ledger shelf (left block) ----------------------------------------------
    m.g('filing cabinet')
    cf = m.surface('north', face=9.6, title='CABINETS')
    m.box((-13.6, 0, 9.8), (-7.0, 17.0, 15.4), 'paint:#5a6068', 'filing cabinet')
    for k in range(4):
        y0 = 0.6 + k * 4.1
        m.box((-13.4, y0, 9.65), (-7.2, y0 + 3.8, 9.8), 'paint:#6a7078', 'cabinet drawer')
        m.box((-11.2, y0 + 2.6, 9.4), (-9.4, y0 + 3.0, 9.65), 'metal:steel', 'drawer pull')
        m.box((-11.0, y0 + 1.6, 9.62), (-9.6, y0 + 2.3, 9.65), 'label:%s' % 'ABCD'[k], 'drawer card')
    m.cyl((-8.0, 16.2, 9.7), 0.35, 0.3, 'z', 'metal:brass', 'cabinet lock')
    m.ctl(cf, 'zone', 'cf', X(-10.3), 8.5, w=6.0, h=16.0, col='000000', label='')
    m.g('evidence store')
    m.box((-6.6, 0, 9.8), (-0.4, 19.0, 15.4), 'paint:#2e3236', 'evidence cupboard')
    m.box((-6.4, 0.4, 9.65), (-0.6, 18.6, 9.8), 'paint:#3a3e44', 'cupboard door')
    m.box((-6.4, 13.0, 9.6), (-0.6, 14.4, 9.65), 'label:EVIDENCE', 'evidence label')
    m.box((-1.6, 8.0, 9.3), (-1.2, 10.4, 9.65), 'metal:steel', 'hasp')
    m.box((-1.9, 7.2, 9.2), (-0.9, 8.0, 9.6), 'metal:brass', 'padlock')
    for y in (1.6, 9.0, 16.4):
        m.box((-6.6, y, 9.5), (-6.3, y + 1.2, 9.8), 'metal:steel', 'hinge')
    m.ctl(cf, 'zone', 'ev', X(-3.5), 9.5, w=5.6, h=18.0, col='000000', label='')
    SPOTS['revenue_office'] = wall_shelf(m, -13.4, -0.8, 22.0, 4, 'LEDGER SHELF')
    for i, c in enumerate(('#6e1f2e', '#24408e', '#2e5e3a', '#5a3a24', '#c9a227')):
        m.box((-13.0 + i * 0.7, 22.0, 13.0), (-12.4 + i * 0.7, 25.6 - (i % 2) * 0.6, 15.0), 'paint:' + c, 'binder')
    m.box((-13.0, 26.0, 15.0), (-1.2, 29.0, 15.4), 'paint:#f4f2ea', 'policy poster', north='label:NO TICKET|PENALTY FARE')

    # ---- the case desk (middle block) ----------------------------------------------------------------------------
    desk(m, {('l', 1): 'ld'}, top=9.0, body='#3e1a26', front='#5a2a38', pull='metal:brass')
    sf = strip(m, 3.4, 8.6, top=9.0)
    pushes(m, sf, [('cprev', GREY, 'CASE<'), ('cnext', GREY, 'CASE>'), ('waive', 'f0d020', 'WAIVE'), ('uphold', '30a040', 'UPHOLD'), ('print', '2050d0', 'LEDGER'),
                   ('test', 'f0f0f0', 'TEST')], 1.4, 4.6, step=2.4, size=0.85)
    pushes(m, sf, [('iprev', GREY, 'INSP<'), ('inext', GREY, 'INSP>'), ('lprev', GREY, 'LINE<'), ('lnext', GREY, 'LINE>'), ('deploy', '8040c0', 'DEPLOY')], 1.2, 6.6, step=2.0, size=0.8)
    m.ctl(sf, 'selector', 's_mode', 12.8, 7.2, w=1.3, h=1.3, col='202020', label='PENALTY', pos=['WARN', 'FIRST', 'FARE'])
    m.ctl(sf, 'label', '', 12.8, 6.0, w=0, h=0, col='e8e8e8', label='PENALTY')
    hd = hood(m, z=9.8)
    lamps(m, hd, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS'), ('openl', AMBER, 'CASES'), ('patl', BLUE, 'PATROL'), ('cctvl', GREEN, 'CCTV')], 1.2, 11.1, step=1.7, size=0.5, lab_dv=0.55)
    m.ctl(hd, 'digits', 'nopen', 10.4, 11.1, w=1.8, h=0.7, col='ffb020', label='', fmt='%d OPEN')
    m.ctl(hd, 'digits', 'npat', 13.6, 11.1, w=2.6, h=0.7, col='8ad4ff', label='', fmt='%d OUT')
    m.ctl(hd, 'digits', 'isel', 4.8, 9.8, w=7.6, h=0.7, col='ffd040', label='', fmt='%s')
    m.ctl(hd, 'digits', 'lsel', 12.2, 9.8, w=6.0, h=0.7, col='8ad4ff', label='', fmt='%s')
    monitor(m, 2.0, 14.0, 12.2, 18.6, 10.6, ['d1', 'd2', 'd3', 'd4', 'st3'], cols=['ffd040', 'f0f0e0', '7cf29a', '5bcefa', 'ffb020'], stand_y=11.8)
    mon2 = m.surface('north', face=10.5, title='TERMINAL STATUS')
    m.box((2.0, 11.8, 10.55), (14.0, 12.2, 11.2), 'paint:#1c1e20', 'status bar')
    m.ctl(mon2, 'digits', 'msg', X(8), 12.0, w=11.0, h=0.35, col='8ad4ff', label='', fmt='%s')
    m.g('fines ledger + stamps')
    m.box((0.8, 9.0, 11.4), (4.0, 9.7, 14.0), 'paint:#6e1f2e', 'fines ledger')
    m.box((1.0, 9.7, 11.6), (3.8, 9.75, 13.8), 'paint:#f4f2ea', 'ledger page')
    m.box((2.3, 9.0, 11.3), (2.5, 9.8, 14.1), 'paint:#c9a227', 'ledger spine band')
    m.box((13.0, 9.0, 11.6), (14.2, 9.4, 12.8), 'paint:#202020', 'stamp pad')
    m.cyl((14.8, 9.0, 12.2), 0.4, 1.4, 'y', 'wood', 'rubber stamp')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')

    # ---- CCTV link + ticket machine charging rack (right block) -------------------------------------------------
    m.g('cctv link')
    m.box((17.0, 14.8, 13.6), (29.4, 15.4, 15.4), 'metal:steel', 'monitor bracket')
    cc = m.surface('north', face=12.1, title='CCTV LINK')
    for i, x0 in enumerate((17.2, 23.4)):
        m.box((x0, 15.4, 12.2), (x0 + 6.0, 20.2, 13.6), 'paint:#1c1e20', 'cctv monitor')
        m.box((x0 + 0.3, 15.7, 12.12), (x0 + 5.7, 19.9, 12.2), 'screen', 'cctv picture')
        m.box((x0 + 0.3, 15.1, 12.15), (x0 + 1.6, 15.4, 12.2), 'label:CAM', 'cam tag')
    m.ctl(cc, 'lamp', 'cctvl', X(28.6), 15.25, w=0.35, h=0.35, col=GREEN, label='LINK')
    m.ctl(cc, 'digits', 'ncam', X(23.3), 20.6, w=3.0, h=0.6, col='7cf29a', label='', fmt='%s')
    m.box((21.6, 20.2, 12.6), (25.0, 21.0, 13.6), 'paint:#1c1e20', 'cam count bezel')
    m.cyl((28.0, 20.2, 13.0), 0.3, 1.6, 'y', 'cable:1d1d21', 'video cable')
    m.g('charging rack')
    m.box((17.0, 0, 11.0), (29.4, 9.0, 15.4), 'paint:#3a3e44', 'side cabinet')
    m.box((16.8, 9.0, 10.8), (29.6, 9.5, 15.4), 'paint:#2a2c2e', 'cabinet top')
    m.box((17.6, 9.5, 12.4), (28.8, 10.4, 15.0), 'paint:#202024', 'charging rack')
    rk = m.surface('north', face=12.3, title='CHARGERS')
    for i in range(4):
        x0 = 18.0 + i * 2.7
        m.box((x0, 10.4, 12.8), (x0 + 1.8, 13.2, 14.2), 'paint:#2a2c30', 'ticket machine')
        m.box((x0 + 0.2, 12.0, 12.75), (x0 + 1.6, 12.9, 12.8), 'paint:#60c8f0', 'machine screen')
        m.box((x0 + 0.3, 10.8, 12.75), (x0 + 1.5, 11.6, 12.8), 'paint:#808890', 'machine keypad')
        m.ctl(rk, 'lamp', 'ch%d' % (i + 1), X(x0 + 0.9), 10.0, w=0.35, h=0.35, col=GREEN, label='CHG')
    m.box((18.0, 9.5, 11.0), (22.4, 11.8, 12.2), 'paint:#e8e8e4', 'ledger printer')
    m.box((18.6, 11.8, 11.2), (21.8, 11.9, 12.0), 'paint:#f4f2ea', 'printer paper')
    for x0 in (17.4, 23.4):
        m.box((x0, 0.6, 10.85), (x0 + 5.6, 8.4, 11.0), 'paint:#4a4e54', 'cabinet door')
        m.box((x0 + 4.8, 6.6, 10.6), (x0 + 5.1, 7.6, 10.85), 'metal:steel', 'door pull')
    m.box((24.0, 9.5, 11.0), (28.8, 10.0, 12.2), 'paint:#f4f2ea', 'penalty notice pad', up='label:PENALTY NOTICE')
    m.g('office odds')
    m.box((10.6, 9.0, 13.2), (12.6, 9.6, 14.6), 'paint:#202024', 'desk phone')
    m.box((10.8, 9.6, 13.4), (12.4, 10.0, 13.8), 'paint:#202024', 'handset')
    m.cyl((11.2, 9.0, 14.9), 0.15, 0.8, 'y', 'cable:1d1d21', 'phone cord')
    m.cyl((8.6, 9.0, 13.8), 0.6, 0.3, 'y', 'paint:#2a2c2e', 'desk lamp base')
    m.box((8.5, 9.3, 13.7), (8.7, 12.4, 13.9), 'metal:steel', 'lamp arm')
    m.box((7.8, 12.0, 12.8), (9.2, 12.8, 14.0), 'paint:#2a2c2e', 'lamp shade')
    m.cyl((15.0, 0, 1.6), 1.0, 3.0, 'y', 'paint:#3a3e44', 'waste bin')
    for y in (12.0, 13.2):
        m.box((-0.4, y, 11.0), (0.2, y + 0.3, 12.2), 'metal:steel', 'coat hook')
    m.box((-0.4, 7.0, 10.8), (0.2, 12.0, 12.4), 'paint:#2a2235', 'inspector jacket')
    m.box((-0.5, 12.4, 10.9), (0.3, 13.4, 12.3), 'paint:#2a2235', 'inspector cap')
    m.box((-0.55, 12.4, 10.9), (0.35, 12.7, 12.3), 'paint:#b02020', 'cap band')
    mains_box(m, 30.0, 16.0, 12.6, 'east')
    m.save(item_gui=0.26)
    return m


RECIPES = {
    'crew_office': (['SCS', 'LDL', 'WBW'], {'S': 'irextras:part@20', 'C': 'minecraft:clock', 'L': 'minecraft:chest', 'D': 'irextras:crew_desk', 'W': 'minecraft:planks', 'B': 'minecraft:cauldron'}),
    'revenue_office': (['SPS', 'CTC', 'WBW'], {'S': 'irextras:part@20', 'P': 'minecraft:paper', 'C': 'minecraft:chest', 'T': 'irextras:ticket_machine', 'W': 'minecraft:planks', 'B': 'minecraft:book'}),
}
LANG = {
    'tile.irextras.crew_office.name': 'Crew Office', 'tile.irextras.revenue_office.name': 'Revenue Protection Office',
    'entity.irextras.railway_crew.name': 'Railway Crew',
}

if __name__ == '__main__':
    for fn in (crew_office, revenue_office):
        mm = fn()
        print('%-16s %d boxes' % (mm.name, len(mm.boxes)))
    for n in RECIPES:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# train crew + revenue protection (batch 5 #7 #9)\n' + '\n'.join(add) + '\n')
    for k, v in SPOTS.items():
        print(k, 'shelf spots (model px):', v)
