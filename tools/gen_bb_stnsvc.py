#!/usr/bin/env python3
"""Station services (ChatGPT batch 3 #26 #28 #30 #31 #34 #35): formation planner, service diagram board, interchange
board, station emergency power switchboard, disruption control desk, lost property office and parcels office (wide
desks). Models face north; controls on north surfaces sit at u = 16 - x, on 'up' surfaces v = z. Live controls:
stn/Tile*.java (ids must match). Every one has a 12 V terminal AND a mains isolator (owner rule: real supply).
Writes models, controls, blockstates, item models and recipes (lang lines are appended by hand)."""
import json, os
from bbkit import Model, ASSETS
from gen_bb_stationfx import lamps, pushes, mains_box, GREEN, RED, AMBER, BLUE, WHITE, ing
from gen_bb_platform import cabinet, screen
from gen_bb_economy import back_wall, desk, strip, cupboard_shelf, monitor, X

SPOTS = {}


def trimmings(m, top=30.0, z=12.8):
    """cable tray with cables on the roof, earth bar, glands, lifting eyes, vent louvres - the cabinet's working detail"""
    m.g('services')
    m.box((1.0, top + 0.6, 14.6), (15.0, top + 1.0, 15.8), 'galv', 'cable tray')
    for x in (1.6, 3.4, 5.2, 7.0, 8.8, 10.6, 12.4, 14.2):
        m.box((x, top + 1.0, 14.8), (x + 0.6, top + 1.4, 15.6), 'cable:1d1d21', 'tray cable')
    for x in (2.0, 14.0):
        m.box((x - 0.4, top + 0.6, 12.4), (x + 0.4, top + 1.6, 13.2), 'metal:steel', 'lifting eye')
    m.box((1.2, 1.2, 15.9), (14.8, 2.0, 16.0), 'metal:copper', 'earth bar')
    for x in range(2, 15, 2):
        m.bolt(x, 1.6, 16.0, 'south', 0.3)
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 1.0, 13.5, 'down', r=0.35)
    m.louvres(1.5, 6.0, 1.6, 3.4, 9.4, n=3)


def gauge_bezels(m, pts, z=9.4, r=1.25):
    for (x, y) in pts:
        m.box((x - r, y - r, z - 0.12), (x + r, y + r, z), 'metal:steel', 'meter bezel')


def lamp_row(m, sf, items, u0, v, step=1.6, size=0.55):
    lamps(m, sf, items, u0, v, step=step, size=size, lab_dv=0.55)


# ============================================================================================== formation planner (#26)
def formation_planner():
    m = Model('formation_planner')
    cabinet(m, 'FORMATION PLANNER')
    trimmings(m)
    m.terminal_12v(15.6, 2.0, 12.8, 'east')
    mains_box(m, 16.0, 4.0, 12.6, 'east')
    m.g('screens')
    screen(m, 1.4, 20.6, 14.6, 26.6, 9.3, 'LED planner screen')
    m.g('demand dials')
    pts = []
    for row in range(2):
        for i in range(6):
            pts.append((14.0 - i * 2.4, 18.4 - row * 3.0))
    gauge_bezels(m, pts)
    m.box((1.0, 13.4, 9.3), (15.0, 13.6, 9.4), 'paint:#f0c018', 'strip line')
    m.box((1.0, 7.0, 9.3), (15.0, 7.2, 9.4), 'paint:#f0c018', 'strip line')
    m.g('printer')
    m.box((10.6, 2.0, 9.0), (15.0, 4.0, 9.4), 'paint:#2a2d30', 'plan printer')
    m.box((11.0, 3.4, 8.95), (14.6, 3.6, 9.0), 'paint:#0a0a0a', 'paper slot')
    m.box((11.4, 3.6, 8.9), (14.2, 4.6, 8.95), 'paint:#f4f2ea', 'printed plan')
    f = m.surface('north', face=9.27, title='FORMATION')
    for i, (cid, col) in enumerate([('line', 'ffd040'), ('now', '7cf29a'), ('form', '7cf29a'), ('rec', '5bcefa'), ('max', '7cf29a'), ('adv', 'ffb020')]):
        m.ctl(f, 'digits', cid, 8.0, 26.0 - i * 0.95, w=12.6, h=0.8, col=col, label='', fmt='%s')
    for k, (x, y) in enumerate(pts):
        m.ctl(f, 'gauge', 'h%d' % k, X(x), y, w=2.0, h=2.0, col='f2f0e6', label='%02d' % (k * 2), max=150)
    lamp_row(m, f, [('over', AMBER, 'OVER'), ('under', RED, 'UNDER', True), ('okl', GREEN, 'OK'), ('autol', BLUE, 'AUTO'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 2.4, 12.4, step=2.2)
    m.ctl(f, 'selector', 's_lf', 2.2, 9.4, w=1.4, h=1.4, col='202020', label='TARGET LF', pos=['70%', '85%', '100%', '120%'])
    m.ctl(f, 'key', 'k_auto', 4.6, 9.4, w=1.2, h=1.2, col='c0a040', label='AUTO SPLIT', pos=['OFF', 'AUTO'])
    pushes(m, f, [('prev', 'e0e0e0', 'LINE <'), ('next', 'e0e0e0', 'LINE >'), ('maxm', 'f0d020', 'MAX -'), ('maxp', 'f0d020', 'MAX +')], 7.2, 9.4, step=2.0, size=1.0)
    pushes(m, f, [('apply', '30a040', 'APPLY'), ('print', '2050d0', 'PRINT'), ('test', 'f0f0f0', 'TEST')], 2.4, 5.4, step=2.6, size=1.0)
    m.save(item_gui=0.45)
    return m


# ============================================================================================ interchange board (#28)
def interchange_board():
    m = Model('interchange_board')
    cabinet(m, 'INTERCHANGE')
    trimmings(m)
    m.terminal_12v(15.6, 2.0, 12.8, 'east')
    mains_box(m, 16.0, 4.0, 12.6, 'east')
    m.g('screens')
    screen(m, 1.4, 23.2, 14.6, 26.6, 9.3, 'header screen')
    screen(m, 1.4, 14.0, 12.4, 22.4, 9.3, 'connections screen')
    m.g('route diagram')
    for i in range(6):
        y = 21.6 - i * 1.4
        m.box((12.8, y - 0.5, 9.32), (15.2, y + 0.5, 9.4), 'paint:#14181c', 'lamp backing')
    m.box((1.0, 12.6, 9.3), (15.0, 12.8, 9.4), 'paint:#f0c018', 'strip line')
    m.box((2.0, 4.0, 9.3), (14.0, 4.2, 9.4), 'paint:#3080e0', 'walk route line')
    for x in (2.0, 8.0, 14.0):
        m.box((x - 0.3, 3.8, 9.28), (x + 0.3, 4.4, 9.3), 'paint:#f0f0f0', 'platform node')
    f = m.surface('north', face=9.27, title='INTERCHANGE')
    m.ctl(f, 'digits', 'stn', 8.0, 26.0, w=12.6, h=0.9, col='ffd040', label='', fmt='%s')
    m.ctl(f, 'digits', 'pair', 8.0, 24.9, w=12.6, h=0.8, col='5bcefa', label='', fmt='%s')
    m.ctl(f, 'digits', 'walk', 8.0, 23.9, w=12.6, h=0.8, col='7cf29a', label='', fmt='%s')
    for i in range(6):
        v = 21.6 - i * 1.4
        m.ctl(f, 'digits', 'c%d' % (i + 1), 9.1, v, w=10.6, h=0.9, col='7cf29a', label='', fmt='%s')
        m.ctl(f, 'lamp', 'c%dg' % (i + 1), 1.4, v, w=0.5, h=0.5, col=GREEN, label='')
        m.ctl(f, 'lamp', 'c%da' % (i + 1), 2.2, v, w=0.5, h=0.5, col=AMBER, label='')
        m.ctl(f, 'lamp', 'c%dr' % (i + 1), 3.0, v, w=0.5, h=0.5, col=RED, label='', blink=True)
    m.ctl(f, 'label', '', 2.2, 22.6, w=0, h=0, col='e8e8e8', label='MK TI MS')
    lamp_row(m, f, [('riskl', AMBER, 'RISK', True), ('heldl', BLUE, 'HELD'), ('annl', GREEN, 'ANNOUNCE'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 2.4, 11.6, step=2.6)
    m.ctl(f, 'digits', 'holds', 13.6, 11.6, w=3.0, h=0.7, col='ffd040', label='', fmt='%s')
    pushes(m, f, [('from', 'e0e0e0', 'FROM >'), ('to', 'e0e0e0', 'TO >'), ('wminus', 'f0d020', 'WALK -'), ('wplus', 'f0d020', 'WALK +'), ('annnow', '2050d0', 'ANNOUNCE')], 2.0, 9.0, step=2.6, size=1.0)
    m.ctl(f, 'key', 'k_sf', 2.2, 6.0, w=1.2, h=1.2, col='c0a040', label='ROUTE', pos=['STAIRS', 'LIFT'])
    m.ctl(f, 'selector', 's_hold', 5.2, 6.0, w=1.4, h=1.4, col='202020', label='HOLD', pos=['OFF', '2 MIN', '5 MIN'])
    m.ctl(f, 'key', 'k_ann', 8.2, 6.0, w=1.2, h=1.2, col='c0a040', label='ANNOUNCE', pos=['OFF', 'ON'])
    m.ctl(f, 'push', 'test', 13.8, 6.0, w=0.8, h=0.8, col='f0f0f0', label='TEST')
    m.save(item_gui=0.45)
    return m


# ========================================================================================= service diagram board (#31)
def diagram_board():
    m = Model('diagram_board')
    cabinet(m, 'SERVICE DIAGRAMS')
    trimmings(m)
    m.terminal_12v(15.6, 2.0, 12.8, 'east')
    mains_box(m, 16.0, 4.0, 12.6, 'east')
    m.g('board')
    screen(m, 1.0, 15.6, 15.0, 26.4, 9.3, 'diagram screen')
    screen(m, 4.4, 9.8, 15.0, 15.0, 9.3, 'detail screen')
    for i in range(10):
        y = 25.8 - i * 1.05
        m.box((0.7, y - 0.45, 9.25), (1.0, y + 0.45, 9.3), 'paint:#3a3e44', 'row divider')
    m.box((0.8, 9.6, 9.3), (4.0, 15.2, 9.4), 'paint:#14181c', 'selector backing')
    m.box((1.0, 2.4, 9.3), (15.0, 2.6, 9.4), 'paint:#f0c018', 'strip line')
    f = m.surface('north', face=9.27, title='DIAGRAMS')
    for i in range(10):
        v = 25.8 - i * 1.05
        n = 'r%d' % (i + 1)
        m.ctl(f, 'lamp', n + 's', 14.6, v, w=0.4, h=0.4, col=WHITE, label='')
        m.ctl(f, 'digits', n, 7.6, v, w=11.4, h=0.85, col='7cf29a', label='', fmt='%s')
        m.ctl(f, 'lamp', n + 'g', 1.0, v, w=0.4, h=0.4, col=GREEN, label='')
        m.ctl(f, 'lamp', n + 'a', 1.55, v, w=0.4, h=0.4, col=AMBER, label='')
        m.ctl(f, 'lamp', n + 'r', 2.1, v, w=0.4, h=0.4, col=RED, label='')
    for i, (cid, col) in enumerate([('code', 'ffd040'), ('hours', '5bcefa'), ('stats', '7cf29a'), ('days', '7cf29a'), ('yest', '8a94a0')]):
        m.ctl(f, 'digits', cid, 6.3, 14.4 - i * 0.95, w=9.8, h=0.8, col=col, label='', fmt='%s')
    m.ctl(f, 'key', 'k_enf', 13.6, 13.6, w=1.2, h=1.2, col='c0a040', label='ENFORCE', pos=['OFF', 'ON'])
    m.ctl(f, 'lamp', 'enfl', 13.6, 11.6, w=0.6, h=0.6, col=AMBER, label='ENF')
    m.ctl(f, 'lamp', 'markl', 13.6, 10.4, w=0.6, h=0.6, col=BLUE, label='MARK', blink=True)
    pushes(m, f, [('prev', 'e0e0e0', 'UNIT <'), ('next', 'e0e0e0', 'UNIT >'), ('stm', 'f0d020', 'START -'), ('stp', 'f0d020', 'START +'), ('enm', 'f0d020', 'END -'), ('enp', 'f0d020', 'END +')], 1.6, 7.6, step=2.5, size=0.95)
    pushes(m, f, [('mark', '2050d0', 'MARK'), ('swap', 'd06020', 'SWAP'), ('print', '2050d0', 'PRINT'), ('test', 'f0f0f0', 'TEST')], 1.6, 4.8, step=2.5, size=0.95)
    lamp_row(m, f, [('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 12.0, 4.8, step=2.2)
    m.save(item_gui=0.45)
    return m


# ===================================================================================== station emergency power (#30)
def station_power():
    m = Model('station_power')
    m.g('switchboard')
    m.box((0, 0, 2.0), (16, 1.0, 16), 'paint:#2a2d30', 'plinth')
    m.box((0.2, 1.0, 2.4), (15.8, 30.0, 16), 'ral7035', 'switchboard body')
    m.box((0, 30.0, 2.0), (16, 30.6, 16), 'ral7035', 'roof')
    m.box((0.4, 14.0, 2.2), (15.6, 29.8, 2.4), 'paint:#1e2226', 'control fascia')
    m.box((0.4, 1.2, 2.2), (15.6, 13.8, 2.4), 'ral7035', 'battery compartment door')
    m.box((1.0, 27.6, 2.1), (15.0, 29.4, 2.2), 'plate', 'title plate', north='plate:EMERGENCY POWER')
    m.box((1.0, 1.4, 2.1), (6.0, 3.0, 2.2), 'danger', 'danger sign', north='danger:DANGER 400V')
    for x in (0.7, 15.3):
        for y in (1.6, 13.4, 14.4, 29.4):
            m.bolt(x, y, 2.2, 'north', 0.3)
    m.g('battery window')
    m.box((6.6, 3.6, 2.15), (15.0, 12.6, 2.2), 'glass', 'battery window')
    for row in range(2):
        for i in range(6):
            x0 = 7.0 + i * 1.3
            y0 = 4.0 + row * 4.2
            m.box((x0, y0, 2.6), (x0 + 1.1, y0 + 3.6, 6.0), 'battery', 'VRLA cell')
            m.box((x0 + 0.2, y0 + 3.6, 3.0), (x0 + 0.4, y0 + 3.9, 3.4), 'metal:copper', 'cell post +')
            m.box((x0 + 0.7, y0 + 3.6, 3.0), (x0 + 0.9, y0 + 3.9, 3.4), 'metal:steel', 'cell post -')
    m.g('mimic')
    m.box((2.0, 25.6, 2.15), (14.0, 25.8, 2.2), 'paint:#d02020', 'incoming busbar')
    m.box((8.0, 23.0, 2.15), (8.2, 25.6, 2.2), 'paint:#d02020', 'ATS leg')
    m.box((2.0, 23.0, 2.15), (2.2, 25.6, 2.2), 'paint:#30c040', 'battery leg')
    m.box((13.8, 23.0, 2.15), (14.0, 25.6, 2.2), 'paint:#f0c018', 'generator leg')
    m.g('generator louvres')
    m.louvres(1.0, 6.0, 4.0, 8.4, 2.15, n=3)
    for z in (5.0, 8.0, 11.0, 14.0):
        m.box((15.8, 6.0, z), (16.0, 26.0, z + 0.4), 'ral7035', 'side stiffener')
        m.box((0.0, 6.0, z), (0.2, 26.0, z + 0.4), 'ral7035', 'side stiffener')
    m.box((0.0, 16.0, 4.0), (0.2, 22.0, 13.0), 'louvre', 'genset intake louvre')
    m.g('exhaust + fuel')
    m.cyl((13.0, 30.6, 13.0), 0.7, 1.0, 'y', 'metal:steel', 'genset exhaust')
    m.cyl((13.0, 31.6, 13.0), 0.9, 0.3, 'y', 'paint:#202020', 'rain cap')
    m.box((1.0, 30.6, 10.0), (5.0, 31.4, 15.0), 'paint:#3a3a3a', 'day tank')
    m.box((2.6, 31.4, 12.0), (3.4, 31.9, 12.8), 'paint:#d02020', 'filler cap')
    trimmings(m, top=30.0)
    m.terminal_12v(16.0, 2.0, 9.0, 'east')
    mains_box(m, 16.0, 5.0, 12.6, 'east')
    f = m.surface('north', face=2.07, title='EMERGENCY POWER')
    for i, (cid, col) in enumerate([('stn', 'ffd040'), ('line1', '7cf29a'), ('line2', '7cf29a'), ('rt', '5bcefa'), ('lvl', 'ffb020')]):
        m.ctl(f, 'digits', cid, 8.0, 21.8 - i * 0.9, w=8.0, h=0.75, col=col, label='', fmt='%s')
    lamp_row(m, f, [('gridl', GREEN, 'MAINS IN'), ('atsn', GREEN, 'NORMAL'), ('atse', AMBER, 'EMERG'), ('battl', AMBER, 'ON BATT'), ('genl', GREEN, 'GEN RUN'), ('crankl', AMBER, 'CRANK', True)], 1.8, 24.2, step=2.5)
    m.ctl(f, 'gauge', 'socg', 13.8, 20.2, w=2.0, h=2.0, col='f2f0e6', label='BATT %', max=100)
    m.ctl(f, 'gauge', 'healthg', 13.8, 17.6, w=1.6, h=1.6, col='f2f0e6', label='HEALTH', max=100)
    m.ctl(f, 'gauge', 'fuelg', 2.2, 20.2, w=2.0, h=2.0, col='f2f0e6', label='FUEL %', max=100)
    m.ctl(f, 'gauge', 'loadg', 2.2, 17.6, w=1.6, h=1.6, col='f2f0e6', label='GEN LOAD', max=150)
    lamp_row(m, f, [('faill', RED, 'GEN FAIL', True), ('lowb', RED, 'BATT LOW'), ('shedl', AMBER, 'SHED'), ('maintl', BLUE, 'BYPASS'), ('estopl', RED, 'E-STOP'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'SUPPLY')], 1.6, 16.2, step=2.1, size=0.5)
    # controls row on the upper fascia
    m.ctl(f, 'selector', 's_mode', 2.0, 26.2, w=1.2, h=1.2, col='202020', label='MODE', pos=['AUTO', 'MANUAL', 'OFF'])
    m.ctl(f, 'selector', 's_shed', 4.6, 26.2, w=1.2, h=1.2, col='202020', label='SHED LEVEL', pos=['LIFE SAFETY', '+LIFTS', '+ESCAL', 'ALL STN'])
    m.ctl(f, 'selector', 's_delay', 7.2, 26.2, w=1.2, h=1.2, col='202020', label='START DELAY', pos=['5 S', '10 S', '30 S'])
    m.ctl(f, 'key', 'k_maint', 9.8, 26.2, w=1.1, h=1.1, col='c0a040', label='MAINT', pos=['NORMAL', 'BYPASS'])
    m.ctl(f, 'mushroom', 'm_estop', 13.6, 26.2, w=1.5, h=1.5, col='d02020', label='GEN E-STOP')
    m.g('push panel')
    m.box((0.8, 14.2, 2.1), (15.2, 15.0, 2.2), 'paint:#2b2f33', 'push strip')
    pushes(m, f, [('gstart', '30a040', 'START'), ('gstop', 'd02020', 'STOP'), ('xfer', 'f0d020', 'TRANSFER'), ('gtest', '2050d0', 'GEN TEST'), ('btest', '2050d0', 'BATT TEST'), ('reset', 'f0f0f0', 'RESET'), ('test', 'f0f0f0', 'LAMPS')], 1.5, 14.6, step=2.15, size=0.8)
    # the day tank filler door: the fuel storage
    m.box((1.0, 9.0, 2.15), (5.6, 12.6, 2.2), 'paint:#5a5e62', 'day tank filler door')
    m.box((4.8, 10.4, 2.0), (5.2, 11.2, 2.15), 'metal:brass', 'filler door latch')
    m.box((1.4, 11.8, 2.1), (5.2, 12.4, 2.15), 'plate', 'fuel label', north='label:FUEL')
    m.ctl(f, 'zone', 'fuel', X(3.3), 10.8, w=4.4, h=3.4, col='000000', label='')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 1.5, 14.5, 15.0, 28.0, 0.0, side='south', title='EMERGENCY POWER|STATION', volts='400V 50HZ')
    m.save(item_gui=0.45)
    return m


# ===================================================================================== disruption control desk (#35)
def disruption_desk():
    m = Model('disruption_desk')
    desk(m, {}, top=9.0, body='#30343a', front='#40464e', pull='metal:steel')
    m.g('upstand')
    m.box((0.4, 9.0, 11.8), (15.6, 20.6, 13.0), 'paint:#24282c', 'monitor upstand')
    m.box((0.2, 20.6, 11.6), (15.8, 21.0, 13.2), 'metal:steel', 'upstand cap')
    mon = monitor(m, 1.0, 15.0, 11.4, 20.2, 11.2, ['stn', 'inc', 'a1', 'a2', 'a3', 'a4', 'a5', 'a6'],
                  cols=['ffd040', 'ff6060', '7cf29a', '7cf29a', '7cf29a', '7cf29a', '7cf29a', '7cf29a'], stand_y=9.0)
    sf = strip(m, 4.0, 9.6, top=9.0)
    m.g('desk furniture')
    for i in range(12):                                                      # keyboard
        m.box((5.0 + i * 0.5, 9.15, 9.9), (5.4 + i * 0.5, 9.35, 10.3), 'paint:#2a2c2e', 'key')
        m.box((5.0 + i * 0.5, 9.15, 10.4), (5.4 + i * 0.5, 9.35, 10.8), 'paint:#2a2c2e', 'key')
    m.box((4.8, 9.0, 9.8), (11.4, 9.15, 10.9), 'paint:#1a1c1e', 'keyboard')
    m.box((12.2, 9.0, 10.0), (14.2, 9.6, 11.2), 'paint:#202020', 'desk phone')
    m.box((12.0, 9.6, 10.1), (14.4, 9.9, 10.5), 'paint:#303030', 'phone handset')
    m.box((1.0, 9.0, 10.0), (3.8, 9.2, 11.4), 'paint:#f4f2ea', 'incident log pad')
    m.box((1.2, 9.2, 10.2), (3.6, 9.25, 10.4), 'paint:#3050c0', 'pen')
    m.g('radio + trays')
    m.box((0.6, 9.0, 11.0), (4.2, 10.6, 11.8), 'paint:#2a2c2e', 'control radio')
    m.box((0.9, 9.6, 10.95), (2.4, 10.4, 11.0), 'paint:#03140a', 'radio display')
    for i in range(3):
        m.box((2.8 + i * 0.4, 9.5, 10.95), (3.1 + i * 0.4, 9.8, 11.0), 'paint:#808080', 'radio key')
    m.cyl((3.8, 10.6, 11.4), 0.12, 2.0, 'y', 'paint:#101010', 'radio aerial')
    for k in range(3):
        m.box((14.6, 9.0 + k * 0.5, 9.8), (15.8, 9.1 + k * 0.5, 11.0), 'paint:#3a3e44', 'document tray')
        m.box((14.7, 9.1 + k * 0.5, 9.9), (15.7, 9.2 + k * 0.5, 10.9), 'paint:#f4f2ea', 'incident sheet')
    m.terminal_12v(16.0, 2.0, 8.0, 'east')
    mains_box(m, 0.0, 2.0, 8.0, 'west')
    pushes(m, sf, [('prev', 'e0e0e0', 'STN <'), ('next', 'e0e0e0', 'STN >'), ('post', '30a040', 'POST'), ('say', '2050d0', 'SAY'), ('clear', 'f0d020', 'CLEAR'), ('test', 'f0f0f0', 'TEST')], 1.4, 8.6, step=2.4, size=0.9)
    m.ctl(sf, 'key', 'k_tkt', 1.6, 6.0, w=1.0, h=1.0, col='c0a040', label='TICKETS', pos=['OFF', 'ACCEPT'])
    m.ctl(sf, 'key', 'k_bus', 3.8, 6.0, w=1.0, h=1.0, col='c0a040', label='BUSES', pos=['OFF', 'CALLED'])
    m.ctl(sf, 'gauge', 'late', 6.6, 5.6, w=1.6, h=1.6, col='f2f0e6', label='AVG LATE', max=60)
    lamps(m, sf, [('disl', RED, 'DISRUPT', True), ('tktl', GREEN, 'TKT'), ('busl', AMBER, 'BUS'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], 9.0, 6.0, step=1.4, size=0.5, lab_dv=0.6)
    m.save(item_gui=0.45)
    return m


# ========================================================================================== lost property office (#34)
def lost_property():
    m = Model('lost_property')
    back_wall(m, panel='#3a3e5a', plaster='#ecebe4', top=30)
    m.g('fascia')
    m.box((-14, 26.0, 14.6), (30, 30.0, 15.4), 'paint:#1f2a4a', 'fascia board')
    m.box((-6, 26.6, 14.5), (22, 29.4, 14.6), 'plate', 'fascia lettering', north='label:LOST PROPERTY OFFICE')
    SPOTS['lost_property'] = cupboard_shelf(m, -13.4, -2.6, ['r1', 'r2', 'r3'], 8, title='FOUND DISPLAY', body='#3a3e5a', door='#4a5070')
    m.g('pigeonhole rack')
    m.box((18.4, 0, 11.6), (29.4, 22.0, 15.4), 'paint:#5a3a24', 'pigeonhole carcass')
    for r in range(5):
        for c in range(4):
            x0, y0 = 18.8 + c * 2.6, 1.0 + r * 4.1
            m.box((x0, y0, 11.5), (x0 + 2.3, y0 + 3.7, 11.6), 'paint:#2a1a10', 'pigeonhole')
            m.box((x0 + 0.3, y0 + 0.2, 11.8), (x0 + 2.0, y0 + 1.4 + (r + c) % 3 * 0.5, 14.0), 'paint:' + ['#c03030', '#3060c0', '#30a050', '#d0a020', '#808080'][(r * 4 + c) % 5], 'tagged property')
            m.box((x0 + 0.8, y0 + 0.4, 11.75), (x0 + 1.5, y0 + 0.9, 11.8), 'paint:#f4f2ea', 'property tag')
    m.box((18.6, 22.0, 11.4), (29.2, 23.0, 15.4), 'plate', 'unclaimed plate', north='label:UNCLAIMED')
    rack = m.surface('north', face=11.4, title='UNCLAIMED')
    m.ctl(rack, 'zone', 'un', X(23.9), 11.0, w=10.0, h=20.0, col='000000', label='')
    desk(m, {('l', 1): 'dr'}, top=9.0, body='#3a3e5a', front='#4a5070', pull='metal:brass')
    sf = strip(m, 4.0, 8.4, top=9.0)
    monitor(m, 2.0, 14.0, 12.0, 18.0, 10.6, ['sign', 'held', 'today', 'stn'], cols=['ffd040', '7cf29a', '7cf29a', '5bcefa'], stand_y=9.0)
    m.g('counter fittings')
    m.box((13.0, 9.0, 9.0), (15.4, 9.8, 10.4), 'paint:#c8a020', 'service bell base')
    m.box((13.6, 9.8, 9.4), (14.8, 10.4, 10.0), 'metal:brass', 'service bell')
    m.box((0.6, 9.0, 8.8), (3.4, 9.6, 10.6), 'paint:#f4f2ea', 'claim forms')
    m.box((0.8, 9.6, 9.0), (3.2, 9.7, 10.4), 'paint:#e0ddd0', 'claim form')
    m.box((-0.2, 22.0, 2.6), (16.2, 22.6, 3.4), 'paint:#1f2a4a', 'counter canopy')
    for x in (0.0, 15.6):
        m.box((x, 9.0, 2.8), (x + 0.4, 22.0, 3.2), 'metal:steel', 'canopy post')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    pushes(m, sf, [('claim', '30a040', 'CLAIM'), ('handin', 'f0d020', 'HAND IN'), ('print', '2050d0', 'REGISTER'), ('bell', 'c0a040', 'BELL')], X(1.6), 7.6, step=-2.4, size=0.9)
    m.ctl(sf, 'key', 'k_sweep', X(1.6), 5.2, w=0.9, h=0.9, col='c0a040', label='SWEEP', pos=['OFF', 'ON'])
    m.ctl(sf, 'gauge', 'heldg', X(4.2), 5.0, w=1.5, h=1.5, col='f2f0e6', label='RACKS %', max=100)
    lamps(m, sf, [('sweepl', GREEN, 'SWEEP'), ('fulll', RED, 'FULL', True), ('unl', AMBER, 'UNCL'), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')], X(6.4), 5.2, step=-1.6, size=0.5, lab_dv=0.6)
    m.save(item_gui=0.28)
    return m


# ================================================================================================= parcels office (#34)
def parcels_office():
    m = Model('parcels_office')
    back_wall(m, panel='#5a3a24', plaster='#efe6d2', top=30)
    m.g('fascia')
    m.box((-14, 26.0, 14.6), (30, 30.0, 15.4), 'paint:#5a1a1a', 'fascia board')
    m.box((-6, 26.6, 14.5), (22, 29.4, 14.6), 'plate', 'fascia lettering', north='label:PARCELS - SEND BY RAIL')
    SPOTS['parcels_office'] = cupboard_shelf(m, -13.4, -2.6, ['out', 'arr'], 6, title='SCALE + COUNTER SHELF', body='#5a3a24', door='#7a5238')
    m.box((-13.0, 7.0, 13.6), (-9.6, 7.2, 15.2), 'metal:steel', 'scale platform')
    m.box((-13.2, 7.2, 15.0), (-9.4, 9.6, 15.3), 'paint:#202020', 'scale display')
    m.g('parcel racking')
    m.box((18.4, 0, 11.6), (29.4, 24.0, 15.4), 'galv', 'racking frame')
    for lvl in range(4):
        y = 1.0 + lvl * 5.8
        m.box((18.6, y, 11.6), (29.2, y + 0.4, 15.2), 'galv', 'rack deck')
        for k in range(3):
            x0 = 19.0 + k * 3.4
            hgt = 2.2 + (lvl + k) % 3 * 0.9
            m.box((x0, y + 0.4, 12.2), (x0 + 2.8, y + 0.4 + hgt, 14.8), 'paint:#b08850', 'parcel box')
            m.box((x0 + 1.2, y + 0.4, 12.1), (x0 + 1.6, y + 0.4 + hgt, 12.2), 'paint:#d8c090', 'parcel tape')
            m.box((x0 + 0.3, y + 0.8, 12.15), (x0 + 1.1, y + 1.4, 12.2), 'paint:#f4f2ea', 'waybill label')
    m.box((18.6, 24.0, 11.4), (29.2, 25.0, 15.4), 'plate', 'consign plate', north='label:CONSIGN')
    rack = m.surface('north', face=11.4, title='CONSIGN')
    m.ctl(rack, 'zone', 'in', X(23.9), 12.0, w=10.0, h=22.0, col='000000', label='')
    desk(m, {('l', 1): 'dr'}, top=9.0, body='#5a3a24', front='#7a5238', pull='metal:brass')
    sf = strip(m, 4.0, 8.4, top=9.0)
    monitor(m, 2.0, 14.0, 12.0, 18.6, 10.6, ['sign', 'dest', 'fee', 'counts', 'last', 'stn'], cols=['ffd040', '5bcefa', '7cf29a', '7cf29a', 'ffb020', '8a94a0'], stand_y=9.0)
    m.g('counter fittings')
    m.box((12.6, 9.0, 9.0), (15.4, 10.2, 10.6), 'paint:#2a2c2e', 'label printer')
    m.box((13.0, 10.2, 9.4), (15.0, 10.3, 10.2), 'paint:#f4f2ea', 'label roll')
    m.box((0.6, 9.0, 8.8), (3.4, 9.3, 10.6), 'paint:#f4f2ea', 'waybill pad')
    m.box((1.0, 9.3, 9.2), (1.3, 9.4, 10.2), 'paint:#202020', 'pen')
    m.box((-0.2, 22.0, 2.6), (16.2, 22.6, 3.4), 'paint:#5a1a1a', 'counter canopy')
    for x in (0.0, 15.6):
        m.box((x, 9.0, 2.8), (x + 0.4, 22.0, 3.2), 'metal:steel', 'canopy post')
    m.terminal_12v(16.4, 1.0, 6.0, 'east')
    mains_box(m, 30.0, 1.0, 13.0, 'east')
    pushes(m, sf, [('dprev', 'e0e0e0', 'DEST <'), ('dnext', 'e0e0e0', 'DEST >'), ('consign', '30a040', 'CONSIGN'), ('collect', 'f0d020', 'COLLECT'), ('print', '2050d0', 'BOOK'), ('weigh', 'c0a040', 'WEIGH')],
           X(1.4), 7.6, step=-2.3, size=0.85)
    m.ctl(sf, 'key', 'k_open', X(1.6), 5.2, w=0.9, h=0.9, col='c0a040', label='COUNTER', pos=['CLOSED', 'OPEN'])
    lamps(m, sf, [('openl', GREEN, 'OPEN'), ('arrl', BLUE, 'ARRIVED'), ('waitl', AMBER, 'WAITING'), ('nodl', RED, 'NO ROUTE', True), ('pwr', GREEN, '12V'), ('mainsl', GREEN, 'MAINS')],
          X(4.0), 5.2, step=-1.7, size=0.5, lab_dv=0.6)
    m.save(item_gui=0.28)
    return m


RECIPES = {
    'formation_planner': (['SGS', 'CFC', 'IRI'], {'S': 'irextras:part@20', 'G': 'minecraft:clock', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'diagram_board': (['SSS', 'CPC', 'IRI'], {'S': 'irextras:part@20', 'C': 'irextras:part@2', 'P': 'minecraft:paper', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'interchange_board': (['SLS', 'CFC', 'IRI'], {'S': 'irextras:part@20', 'L': 'minecraft:redstone_lamp', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone'}),
    'station_power': (['IFI', 'BCB', 'IGI'], {'I': 'minecraft:iron_block', 'F': 'minecraft:furnace', 'B': 'irextras:part@6', 'C': 'irextras:part@2', 'G': 'irextras:part@3'}),
    'disruption_desk': (['SLS', 'CFC', 'WWW'], {'S': 'irextras:part@20', 'L': 'minecraft:redstone_lamp', 'C': 'irextras:part@2', 'F': 'irextras:part@1', 'W': 'minecraft:planks'}),
    'lost_property': (['WCW', 'WSW', 'WPW'], {'W': 'minecraft:planks', 'C': 'minecraft:chest', 'S': 'irextras:part@20', 'P': 'minecraft:paper'}),
    'parcels_office': (['WCW', 'WSW', 'WBW'], {'W': 'minecraft:planks', 'C': 'minecraft:chest', 'S': 'irextras:part@20', 'B': 'minecraft:book'}),
}
FACING = list(RECIPES)

if __name__ == '__main__':
    for fn in (formation_planner, diagram_board, interchange_board, station_power, disruption_desk, lost_property, parcels_office):
        mm = fn()
        print('%-18s %d boxes' % (mm.name, len(mm.boxes)))
    for n in FACING:
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        pat, key = RECIPES[n]
        json.dump({'type': 'minecraft:crafting_shaped', 'pattern': pat, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'item': 'irextras:' + n, 'count': 1}},
                  open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    for k, v in SPOTS.items():
        print(k, 'shelf spots (model px):', v)
