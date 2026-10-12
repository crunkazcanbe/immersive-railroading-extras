#!/usr/bin/env python3
"""Alarm Annunciator cabinet (her ChatGPT list: unified alarm manager). Front = north. Twelve backlit windows (live TILE
controls, one per AlarmManager.Cat), message display, counters, ACK / SILENCE / RESET / LAMP TEST, horn + strobe."""
import json, os
from bbkit import Model, ASSETS

CATS = [('power', 'POWER TRIP', 'ff3a2a'), ('fault', 'SHORT / EARTH|FAULT', 'ff3a2a'), ('fire', 'FIRE /|BURN-OUT', 'ff3a2a'), ('machine', 'MOTOR / GEN|FAULT', 'ffb020'),
        ('auto', 'AUTO|CONTROL', 'f0f0e0'), ('wired', '12 V /|WIRED FAULT', 'ffb020'), ('signal', 'SIGNAL|FAULT', 'ffb020'), ('incident', 'TRAIN|INCIDENT', 'ff3a2a'),
        ('allstop', 'ALL STOP', 'ff3a2a'), ('freight', 'FREIGHT', 'f0f0e0'), ('console', 'CONTROL|ROOM', 'ff3a2a'), ('general', 'GENERAL', 'f0f0e0')]


def annunciator():
    m = Model('annunciator')
    m.g('plinth')
    m.box((0, 0, 1.6), (16, 1.0, 16), 'paint:#2a2d30', 'plinth')
    m.box((0, 1.0, 1.4), (16, 1.3, 16), 'haz', 'kick strip', faces=('north',))
    m.g('cabinet')
    m.box((0.5, 1.0, 2.5), (15.5, 26.0, 15.5), 'ral7035', 'cabinet')
    m.box((0.2, 26.0, 2.2), (15.8, 26.8, 15.8), 'ral7035', 'roof')
    m.box((0.5, 1.0, 2.2), (15.5, 14.2, 2.5), 'paint:#c4c6c2', 'lower door')
    m.box((15.0, 5.0, 1.9), (15.4, 9.0, 2.2), 'metal:steel', 'door handle')
    m.box((14.7, 10.0, 2.0), (15.3, 10.6, 2.2), 'metal:brass', 'key lock')
    for y in (3.0, 12.0):
        m.box((0.0, y, 3.0), (0.5, y + 1.4, 3.6), 'metal:steel', 'hinge')
    m.louvres(2.0, 8.0, 2.0, 4.6, 2.2, n=4)
    m.nameplate(4.0, 1.6, 12.0, 3.0, 2.2, 'ALARM ANNUNCIATOR')
    m.box((0.5, 14.2, 2.0), (15.5, 25.6, 2.5), 'paint:#2b2f33', 'annunciator fascia')
    m.box((1.0, 16.0, 1.9), (15.0, 24.6, 2.05), 'paint:#111315', 'window frame')
    for x in (1.2, 14.8):
        for y in (14.6, 25.2):
            m.bolt(x, y, 2.0, 'north', 0.4)
    for z in (5.0, 9.0, 13.0):
        m.box((0.2, 4.0, z), (0.5, 22.0, z + 0.6), 'ral7035', 'side stiffener')
        m.box((15.5, 4.0, z), (15.8, 22.0, z + 0.6), 'ral7035', 'side stiffener')
    m.g('horn and strobe')
    m.box((6.6, 26.8, 6.6), (9.4, 27.6, 9.4), 'paint:#2a2d30', 'beacon base')
    m.cyl((8.0, 27.6, 8.0), 1.1, 1.8, 'y', 'glass', 'strobe dome')
    m.box((10.6, 26.8, 9.0), (12.6, 28.4, 11.0), 'paint:#2a2d30', 'horn bracket')
    m.cyl((11.6, 27.8, 8.6), 0.7, 1.4, 'z', 'paint:#c8c8c4', 'horn throat')
    m.cyl((11.6, 27.8, 6.8), 1.4, 1.8, 'z', 'paint:#c8c8c4', 'horn bell', cap='mesh')
    top = m.surface('up', face=16 - 29.45, title='STROBE')
    m.ctl(top, 'lamp', 'strobe', 8.0, 8.0, w=1.6, h=1.6, col='ff2020', label='', blink=True)
    m.g('cable entry')
    for x in (3.0, 6.0, 9.0, 12.0):
        m.gland(x, 1.0, 13.5, 'down', r=0.4)
    m.terminal_12v(15.5, 1.6, 8.5, 'east')
    # ---- the live front: windows, displays, counters, buttons, lamps
    f = m.surface('north', face=1.9, title='ANNUNCIATOR')
    for i, (cid, lab, col) in enumerate(CATS):
        r, c = i // 4, i % 4
        m.ctl(f, 'tile', 't' + cid, 2.9 + c * 3.4, 23.0 - r * 2.75, w=3.1, h=2.45, col=col, label=lab)
    m.ctl(f, 'digits', 'msg1', 8.0, 15.35, w=13.2, h=0.85, col='ffb020', label='', fmt='%s')
    m.ctl(f, 'digits', 'msg2', 8.0, 14.45, w=13.2, h=0.85, col='ffd27a', label='', fmt='%s')
    for (cid, u, col, lab) in (('nnew', 3.3, 'ff4040', 'NEW'), ('nack', 8.0, 'ffb020', 'STANDING'), ('nclr', 12.7, '7cf29a', 'CLEARED')):
        m.ctl(f, 'digits', cid, u, 12.9, w=3.2, h=1.0, col=col, label='', fmt='%d')
        m.ctl(f, 'label', '', u, 12.05, w=0, h=0, col='e0e0e0', label=lab)
    for (cid, u, col, lab) in (('ack', 2.9, '1a1a1a', 'ACK'), ('silence', 6.3, 'f0d020', 'SILENCE'), ('reset', 9.7, '2050d0', 'RESET'), ('test', 13.1, 'f0f0f0', 'LAMP TEST')):
        m.ctl(f, 'push', cid, u, 9.8, w=1.7, h=1.7, col=col, label=lab)
    m.ctl(f, 'lamp', 'power', 2.4, 6.7, w=0.8, h=0.8, col='30e060', label='12V')
    m.ctl(f, 'lamp', 'horn', 4.4, 6.7, w=0.8, h=0.8, col='ff3030', label='HORN', blink=True)
    m.save(item_gui=0.45)


if __name__ == '__main__':
    annunciator()
    a = ASSETS
    bs = {'variants': {'facing=north': {'model': 'irextras:annunciator'}, 'facing=east': {'model': 'irextras:annunciator', 'y': 90},
                       'facing=south': {'model': 'irextras:annunciator', 'y': 180}, 'facing=west': {'model': 'irextras:annunciator', 'y': 270}}}
    json.dump(bs, open(os.path.join(a, 'blockstates/annunciator.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/annunciator'}, open(os.path.join(a, 'models/item/annunciator.json'), 'w'), indent=1)
    print('annunciator assets written')


def recorder():
    m = Model('event_recorder')
    m.g('rack')
    m.box((1.0, 0, 2.6), (15.0, 0.8, 15.4), 'paint:#141416', 'rack plinth')
    for x in (1.0, 14.2):
        m.box((x, 0.8, 2.6), (x + 0.8, 26.4, 15.4), 'paint:#1c1d20', 'rack upright')
    m.box((1.8, 0.8, 14.6), (14.2, 26.4, 15.4), 'mesh', 'rear mesh door')
    m.box((1.0, 26.4, 2.6), (15.0, 27.0, 15.4), 'paint:#1c1d20', 'rack top')
    for y in range(1, 26, 2):
        m.box((1.7, y, 2.5), (1.95, y + 0.25, 2.6), 'metal:steel', 'rack hole')
        m.box((14.05, y, 2.5), (14.3, y + 0.25, 2.6), 'metal:steel', 'rack hole')
    m.g('recorder unit')
    m.box((1.8, 18.0, 2.9), (14.2, 24.8, 14.0), 'paint:#2b2f33', 'recorder chassis')
    m.box((1.6, 18.0, 2.7), (14.4, 24.8, 2.9), 'paint:#3a3f44', 'recorder front panel')
    m.box((2.2, 23.0, 2.62), (13.8, 24.5, 2.7), 'paint:#03140a', 'display window')
    m.nameplate(10.0, 18.3, 13.8, 18.9, 2.7, 'EVENT REC')
    for x in (1.6, 14.0):
        m.box((x, 18.4, 2.2), (x + 0.4, 24.4, 2.7), 'metal:alu', 'rack handle')
    m.g('disk array')
    m.box((1.8, 9.8, 2.9), (14.2, 17.4, 14.0), 'paint:#2b2f33', 'disk chassis')
    m.box((1.6, 9.8, 2.7), (14.4, 17.4, 2.9), 'paint:#3a3f44', 'disk front panel')
    for i in range(6):
        x0 = 2.0 + i * 2.0
        m.box((x0, 10.4, 2.5), (x0 + 1.8, 16.8, 2.7), 'paint:#202226', 'drive caddy')
        m.box((x0 + 0.3, 11.0, 2.4), (x0 + 1.5, 11.5, 2.5), 'metal:alu', 'caddy latch')
        m.box((x0 + 0.2, 12.0, 2.45), (x0 + 1.6, 15.2, 2.5), 'louvre', 'caddy vent')
    m.g('ups')
    m.box((1.8, 1.4, 2.9), (14.2, 8.8, 14.0), 'paint:#1c1d20', 'ups chassis')
    m.box((1.6, 1.4, 2.7), (14.4, 8.8, 2.9), 'paint:#26282c', 'ups front')
    m.louvres(9.6, 13.8, 2.0, 8.2, 2.7, n=5)
    m.nameplate(2.2, 1.8, 7.0, 2.6, 2.7, 'UPS 1KVA')
    m.g('printer')
    m.box((3.0, 27.0, 4.0), (13.0, 28.8, 12.0), 'paint:#d8d8d4', 'thermal printer')
    m.box((4.0, 28.8, 9.0), (12.0, 29.1, 11.5), 'paint:#bcbcb8', 'paper bin lid')
    m.box((4.5, 27.6, 3.9), (11.5, 27.9, 4.0), 'paint:#1a1a1a', 'paper slot')
    m.box((5.0, 27.7, 3.2), (11.0, 27.8, 3.9), 'paint:#fbfbf6', 'printout')
    m.terminal_12v(15.0, 1.6, 9.0, 'east')
    for x in (4.0, 8.0, 12.0):
        m.gland(x, 0.8, 12.0, 'down', r=0.35)
    f = m.surface('north', face=2.6, title='RECORDER')
    m.ctl(f, 'digits', 'last', 8.0, 23.75, w=11.2, h=1.2, col='7cf29a', label='', fmt='%s')
    for (cid, u, col, lab, blink) in (('rec', 2.6, 'ff2020', 'REC', True), ('disk', 4.0, 'ffb020', 'DISK', False), ('power', 5.4, '30e060', 'PWR', False)):
        m.ctl(f, 'lamp', cid, u, 22.0, w=0.7, h=0.7, col=col, label=lab, **({'blink': True} if blink else {}))
    m.ctl(f, 'digits', 'smp', 8.6, 22.0, w=3.0, h=1.0, col='ffb020', label='', fmt='%d SMP')
    m.ctl(f, 'digits', 'evt', 12.2, 22.0, w=3.0, h=1.0, col='ffb020', label='', fmt='%d EVT')
    m.ctl(f, 'digits', 'span', 3.6, 19.7, w=3.0, h=1.0, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'selector', 'rate', 7.0, 19.5, w=1.3, h=1.3, col='202020', label='RATE', pos=['1S', '2S', '5S'])
    m.ctl(f, 'push', 'mark', 10.0, 19.5, w=1.3, h=1.3, col='f0d020', label='MARK')
    m.ctl(f, 'push', 'print', 12.6, 19.5, w=1.3, h=1.3, col='f0f0f0', label='PRINT')
    for i in range(6):
        m.ctl(f, 'lamp', 'hd%d' % (i + 1), 16 - (2.9 + i * 2.0), 16.2, w=0.45, h=0.45, col='30e060', label='')
    m.ctl(f, 'gauge', 'batt', 12.0, 5.4, w=3.2, h=3.2, col='f2f0e6', label='BATT %', max=100)
    m.ctl(f, 'lamp', 'ups', 7.6, 6.2, w=0.8, h=0.8, col='30e060', label='ONLINE')
    import cabkit   # opening cabinet door(s) + working insides (2026-10-11)
    cabkit.fit(m, 2.0, 14.0, 13.0, 26.0, 0.6, side='south', col='paint:#1c1d20', title='EVENT RECORDER', volts='230V 12V DC', fns=(('ctl', '12V CTL'), ('scr', 'SCREENS')))
    m.save(item_gui=0.45)


if __name__ == '__main__':
    recorder()
    bs = {'variants': {'facing=north': {'model': 'irextras:event_recorder'}, 'facing=east': {'model': 'irextras:event_recorder', 'y': 90},
                       'facing=south': {'model': 'irextras:event_recorder', 'y': 180}, 'facing=west': {'model': 'irextras:event_recorder', 'y': 270}}}
    json.dump(bs, open(os.path.join(ASSETS, 'blockstates/event_recorder.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/event_recorder'}, open(os.path.join(ASSETS, 'models/item/event_recorder.json'), 'w'), indent=1)
