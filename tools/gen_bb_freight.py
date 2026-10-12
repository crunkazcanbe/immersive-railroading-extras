#!/usr/bin/env python3
"""The Freight Office (her ask 2026-10-09: "add the next feature ... as complex and as many buttons ... screens, levers
... realistic ... make it pretty"). A yard clerk's office: a panelled back wall three blocks wide with the big LED
contract board, an oak desk with the steel control strip (live buttons, key, lamps, readouts), the clerk's monitor,
phone, printer, banker's lamp, trays, filing cabinet, pinboard, network map, ledgers and a plant.
Front = north (where you stand). Live controls: see block/TileFreightOffice."""
import json, os
from bbkit import Model, ASSETS


def office():
    m = Model('freight_office')
    # ---------------------------------------------------------------- the room: panelled back wall
    m.g('back wall')
    m.box((-14, 0, 15.4), (30, 9.6, 16), 'paint:#5a3a24', 'oak wainscot')
    m.box((-14, 9.6, 15.5), (30, 32, 16), 'paint:#e9e1cc', 'plastered wall')
    m.box((-14, 9.4, 15.2), (30, 9.9, 15.6), 'paint:#3e2716', 'dado rail')
    m.box((-14, 0, 15.1), (30, 0.8, 15.5), 'paint:#2a1a10', 'skirting')
    for x in range(-13, 30, 4):
        m.box((x, 1.2, 15.32), (x + 3.2, 8.6, 15.4), 'paint:#6b4630', 'wainscot panel')
    m.box((-14, 31.4, 15.0), (30, 32, 16), 'paint:#3e2716', 'cornice')

    # ---------------------------------------------------------------- the contract board (live rows)
    m.g('contract board')
    m.box((-14.4, 14.2, 14.6), (30.4, 30.2, 15.4), 'paint:#23272b', 'board bezel')
    m.box((-14, 14.6, 14.5), (30, 29.8, 14.6), 'paint:#070a08', 'LED panel')
    for i in range(1, 6):
        y = 29.0 - i * 2.4
        m.box((-14, y, 14.45), (30, y + 0.08, 14.5), 'paint:#1e2622', 'row rule')
    for x in (-11.6, 2.8, 20.2):
        m.box((x, 14.6, 14.45), (x + 0.08, 29.8, 14.5), 'paint:#1e2622', 'column rule')
    m.box((-8, 30.2, 14.7), (24, 31.4, 15.4), 'paint:#2b1d12', 'header board')
    m.box((-7.6, 30.35, 14.62), (23.6, 31.25, 14.7), 'label:FREIGHT CONTRACTS', 'header lettering', north='plate:FREIGHT CONTRACTS')
    for cx in (-11, 27):
        m.cyl((cx, 30.9, 14.4), 0.95, 0.6, 'z', 'gauge:clock', 'station clock', cap='gauge:clock')
        m.cyl((cx, 30.9, 15.0), 1.1, 0.4, 'z', 'paint:#23272b', 'clock case')
    m.box((-13.4, 28.9, 14.45), (29.4, 29.5, 14.5), 'label:STATUS   JOB                  ROUTE                    PAY  DUE', 'column heads',
          north='label:STATUS - JOB - ROUTE - PAY AND DUE')
    for (x, y) in ((-14.2, 14.4), (30.2, 14.4), (-14.2, 30.0), (30.2, 30.0)):
        m.bolt(x, y, 14.6, 'north', 0.35)
    board = m.surface('north', face=14.42, title='CONTRACT BOARD')
    for r in range(1, 7):
        v = 28.2 - (r - 1) * 2.4 - 0.9
        for k, (cid, col) in enumerate((('o', '30e060'), ('p', 'ffb020'), ('x', 'ff3030'))):
            m.ctl(board, 'lamp', 'r%d%s' % (r, cid), -13.2 + k * 0.9, v, w=0.6, h=0.6, col=col, label='', **({'blink': True} if cid == 'x' else {}))
        m.ctl(board, 'digits', 'r%da' % r, -4.4, v, w=13.6, h=1.7, col='ffb020', label='', fmt='%s')
        m.ctl(board, 'digits', 'r%db' % r, 11.5, v, w=16.8, h=1.7, col='ffd27a', label='', fmt='%s')
        m.ctl(board, 'digits', 'r%dc' % r, 25.1, v, w=9.4, h=1.7, col='7cf29a', label='', fmt='%s')

    # ---------------------------------------------------------------- left: pinboard + filing cabinet
    m.g('pinboard')
    m.box((-13.4, 10.4, 15.1), (-2.6, 14.0, 15.4), 'paint:#b9875a', 'cork pinboard')
    m.box((-13.6, 10.2, 15.0), (-2.4, 10.4, 15.4), 'wood', 'pinboard frame')
    m.box((-13.6, 14.0, 15.0), (-2.4, 14.2, 15.4), 'wood', 'pinboard frame')
    for (x, y, c, t) in ((-12.8, 11.0, '#f4f1e6', 'label:RULES'), (-10.2, 12.0, '#f6e27a', 'label:NOTE'), (-7.6, 10.8, '#f4f1e6', 'label:RATES'),
                         (-5.4, 11.8, '#ffd1dc', 'label:SHIFT'), (-9.0, 10.6, '#cfe8ff', 'label:MAP')):
        m.box((x, y, 15.02), (x + 2.0, y + 2.0, 15.1), 'paint:' + c, 'pinned notice', north=t)
        m.box((x + 0.9, y + 1.7, 14.92), (x + 1.1, y + 1.9, 15.02), 'paint:#d02020', 'drawing pin')
    m.g('filing cabinet')
    m.box((-13.2, 0, 9.4), (-8.0, 9.2, 15.2), 'paint:ral7035', 'filing cabinet')
    fc = m.surface('north', face=9.2, title='FILING CABINET')
    for k in range(3):
        m.ctl(fc, 'zone', 'fc%d' % (k + 1), 16 - (-10.6), 0.6 + k * 2.9 + 1.3, w=4.6, h=2.6, col='000000', label='')
    for k in range(3):
        y0 = 0.6 + k * 2.9
        m.box((-12.9, y0, 9.25), (-8.3, y0 + 2.6, 9.4), 'paint:#c4c6c2', 'drawer front')
        m.box((-11.4, y0 + 1.6, 9.0), (-9.9, y0 + 1.9, 9.25), 'metal:alu', 'drawer handle')
        m.box((-11.2, y0 + 0.6, 9.18), (-10.1, y0 + 1.2, 9.25), 'label:' + ('ABC'[k] + '-' + 'HPZ'[k]), 'label holder')
    m.box((-12.4, 9.2, 10.4), (-9.0, 9.6, 13.6), 'paint:#2f5f9e', 'binder')
    m.box((-12.2, 9.6, 10.6), (-9.2, 10.0, 13.4), 'paint:#9e2f2f', 'binder')

    # ---------------------------------------------------------------- right: network map, ledgers, plant
    m.g('network map')
    m.box((18.4, 10.4, 15.1), (29.4, 14.0, 15.4), 'paint:#f2efe2', 'network map')
    for (a, b) in (((19, 12.2), (28.8, 12.2)), ((19, 11.0), (24, 11.0)), ((22, 13.2), (28.8, 13.2))):
        m.box((a[0], a[1], 15.04), (b[0], b[1] + 0.2, 15.1), 'paint:#1b1b1b', 'map line')
    for (x, y, c) in ((20, 12.0, '#d02020'), (23.4, 12.0, '#2050d0'), (26.8, 12.0, '#20a040'), (21.6, 10.8, '#d0a020'), (27.4, 13.0, '#d02020')):
        m.box((x, y, 15.0), (x + 0.5, y + 0.5, 15.1), 'paint:' + c, 'map station')
    m.box((18.2, 10.2, 15.0), (29.6, 10.4, 15.4), 'wood', 'map frame')
    m.box((18.2, 14.0, 15.0), (29.6, 14.2, 15.4), 'wood', 'map frame')
    m.g('ledger shelf')
    m.box((18.4, 0, 11.6), (29.4, 6.6, 15.4), 'paint:#5a3a24', 'ledger cabinet')
    m.box((18.6, 6.6, 11.4), (29.6, 7.0, 15.4), 'paint:#3e2716', 'shelf top')
    # the shelf top is real storage now: 8 spots, what you put there stands on it (FreightOfficeRenderer)
    shelf = m.surface('up', face=16 - 7.0, title='SHELF')
    for k in range(8):
        m.ctl(shelf, 'zone', 'sh%d' % (k + 1), 16 - (19.3 + k * 1.3), 13.4, w=1.25, h=3.8, col='000000', label='')
    m.box((18.7, 7.0, 15.0), (29.5, 9.4, 15.4), 'paint:#3e2716', 'shelf back board')
    for x in (18.6, 29.2):
        m.box((x, 7.0, 11.6), (x + 0.4, 9.4, 15.4), 'paint:#3e2716', 'shelf bookend')
    cup = m.surface('north', face=11.4, title='CUPBOARD')
    for k in range(2):
        m.ctl(cup, 'zone', 'cb%d' % (k + 1), 16 - (21.4 + k * 5.3), 3.3, w=5.0, h=5.4, col='000000', label='')
    for k in range(2):
        m.box((18.9 + k * 5.3, 0.6, 11.45), (23.9 + k * 5.3, 6.0, 11.6), 'paint:#6b4630', 'cupboard door')
        m.box((23.1 + k * 5.3, 3.0, 11.2), (23.4 + k * 5.3, 3.9, 11.45), 'metal:brass', 'door knob')
    m.g('plant')
    m.cyl((26.0, 7.0, 12.8), 0.9, 1.3, 'y', 'paint:#b0532a', 'terracotta pot')
    m.cyl((26.0, 8.3, 12.8), 0.8, 0.15, 'y', 'paint:#3b2a1a', 'soil')
    for (dx, dz, h, rot) in ((0, 0, 3.2, None), (-0.5, 0.3, 2.4, -22.5), (0.6, -0.2, 2.6, 22.5), (0.1, 0.6, 2.0, 45)):
        r = {'axis': 'z', 'angle': rot, 'origin': [26.0 + dx, 8.4, 12.8 + dz]} if rot else None
        m.box((25.85 + dx, 8.4, 12.65 + dz), (26.15 + dx, 8.4 + h, 12.95 + dz), 'paint:#2f7a32', 'leaf stem', rot=r)
        m.box((25.4 + dx, 8.4 + h - 0.8, 12.4 + dz), (26.6 + dx, 8.4 + h, 13.2 + dz), 'paint:#3fa043', 'leaf', rot=r)

    # ---------------------------------------------------------------- the desk
    m.g('desk')
    m.box((0, 8.4, 3.4), (16, 9.0, 12.8), 'wood', 'desk top')
    m.box((-0.2, 8.2, 3.2), (16.2, 8.4, 13.0), 'paint:#3e2716', 'desk top edge')
    m.box((0.6, 0.6, 4.0), (15.4, 8.2, 4.5), 'paint:#6b4630', 'modesty panel')
    m.box((1.2, 1.4, 3.9), (14.8, 7.4, 4.0), 'paint:#7a5238', 'raised panel')
    dr = m.surface('north', face=3.5, title='DESK DRAWERS')
    for side, (x0, x1) in (('l', (10.4, 15.6)), ('r', (0.4, 5.6))):      # left / right as you sit at the desk (facing the room)
        for k in range(3):
            m.ctl(dr, 'zone', 'd%s%d' % (side, k + 1), 16 - (x0 + x1) / 2, 0.9 + k * 2.4 + 1.05, w=4.6, h=2.1, col='000000', label='')
    for (x0, x1) in ((0.4, 5.6), (10.4, 15.6)):
        m.box((x0, 0.4, 4.0), (x1, 8.2, 12.6), 'paint:#5a3a24', 'pedestal')
        for k in range(3):
            y0 = 0.9 + k * 2.4
            m.box((x0 + 0.3, y0, 3.8), (x1 - 0.3, y0 + 2.1, 4.0), 'paint:#7a5238', 'drawer front')
            m.box(((x0 + x1) / 2 - 0.8, y0 + 1.2, 3.55), ((x0 + x1) / 2 + 0.8, y0 + 1.5, 3.8), 'metal:brass', 'brass pull')
    m.box((0, 0, 4.2), (16, 0.4, 12.6), 'paint:#2a1a10', 'kick plinth')

    m.g('control strip')
    m.box((0.8, 9.0, 3.7), (15.2, 9.15, 7.7), 'paint:#2b2f33', 'steel control strip')
    m.box((0.6, 9.0, 3.5), (15.4, 9.2, 3.7), 'metal:steel', 'strip edge')
    desk = m.surface('up', face=16 - 9.15, title='CLERK DESK')
    for (cid, x, col, lab) in (('prev', 1.8, '1a1a1a', 'PREV'), ('next', 3.3, '1a1a1a', 'NEXT'), ('accept', 5.2, '1fa34a', 'ACCEPT'),
                               ('decline', 6.9, 'd02020', 'DECLINE'), ('print', 8.6, 'f0f0f0', 'PRINT'), ('collect', 10.3, 'f0d020', 'COLLECT')):
        m.ctl(desk, 'push', cid, x, 4.9, w=1.15, h=1.15, col=col, label=lab)            # u = x: left -> right as you face the desk
        m.ctl(desk, 'label', '', x, 4.05, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(desk, 'key', 'office', 12.6, 5.2, w=1.3, h=1.3, col='c0a040', label='OFFICE', pos=['OPEN', 'CLOSED'])
    for (cid, x, col, lab, blink) in (('power', 1.8, '30e060', '12V', False), ('new', 3.1, 'ffb020', 'NEW', True),
                                      ('late', 4.4, 'ff3030', 'LATE', True), ('paper', 5.7, 'f0f0f0', 'PRINTER', False)):
        m.ctl(desk, 'lamp', cid, x, 6.6, w=0.75, h=0.75, col=col, label=lab, **({'blink': True} if blink else {}))
        m.ctl(desk, 'label', '', x, 7.35, w=0, h=0, col='e8e8e8', label=lab)
    m.ctl(desk, 'digits', 'earned', 8.9, 6.7, w=3.2, h=1.1, col='ffd040', label='', fmt='%s')
    m.ctl(desk, 'label', '', 8.9, 7.45, w=0, h=0, col='e8e8e8', label='EARNED')
    m.ctl(desk, 'digits', 'unpaid', 12.9, 6.7, w=3.4, h=1.1, col='40ff60', label='', fmt='%s')
    m.ctl(desk, 'label', '', 12.9, 7.45, w=0, h=0, col='e8e8e8', label='YOUR PAY')

    m.g('monitor')
    m.box((6.0, 9.0, 9.0), (10.0, 9.3, 11.0), 'paint:#1c1e20', 'monitor foot')
    m.box((7.6, 9.3, 9.9), (8.4, 10.6, 10.5), 'paint:#2a2c2e', 'monitor neck')
    m.box((2.8, 10.4, 8.9), (13.2, 16.4, 9.8), 'paint:#1c1e20', 'monitor bezel')
    m.box((3.2, 10.8, 8.84), (12.8, 16.0, 8.9), 'paint:#03140a', 'screen')
    m.box((7.2, 10.5, 8.85), (8.8, 10.7, 8.9), 'paint:#30e060', 'power led')
    mon = m.surface('north', face=8.8, title='MONITOR')
    for i in range(4):
        m.ctl(mon, 'digits', 'd%d' % (i + 1), 8.0, 15.1 - i * 1.25, w=9.2, h=1.1, col='7cf29a' if i else 'ffd040', label='', fmt='%s')

    m.g('phone')
    m.box((1.0, 9.0, 8.4), (4.2, 9.9, 11.4), 'paint:#1a1a1a', 'phone body')
    m.box((1.4, 9.9, 8.8), (3.8, 10.2, 11.0), 'paint:#262626', 'cradle')
    m.cyl((0.9, 10.5, 9.9), 0.45, 3.4, 'x', 'paint:#1a1a1a', 'handset')
    for x in (1.0, 3.9):
        m.box((x, 10.1, 9.4), (x + 0.4, 10.9, 10.4), 'paint:#1a1a1a', 'earpiece')
    for k in range(4):
        m.cyl((0.6 - k * 0.25, 9.05 + k * 0.1, 10.9 + k * 0.4), 0.18, 0.3, 'x', 'paint:#1a1a1a', 'coiled cord')
    ph = m.surface('north', face=8.35, title='PHONE')
    m.ctl(ph, 'push', 'phone', 16 - 2.6, 9.45, w=0.8, h=0.6, col='d02020', label='YARD')

    m.g('printer')
    m.box((11.6, 9.0, 8.6), (15.4, 11.0, 12.2), 'paint:#d8d8d4', 'printer')
    m.box((11.8, 11.0, 10.4), (15.2, 11.4, 12.4), 'paint:#bcbcb8', 'paper tray', rot={'axis': 'x', 'angle': -22.5, 'origin': [13.5, 11.0, 10.4]})
    m.box((12.2, 11.2, 11.0), (14.8, 11.3, 12.6), 'paint:#fbfbf6', 'blank paper', rot={'axis': 'x', 'angle': -22.5, 'origin': [13.5, 11.0, 10.4]})
    m.box((12.0, 9.6, 8.45), (15.0, 9.9, 8.6), 'paint:#1a1a1a', 'output slot')
    m.box((12.3, 9.15, 7.6), (14.7, 9.25, 8.6), 'paint:#fbfbf6', 'printed waybill')
    m.box((12.5, 9.25, 7.8), (14.5, 9.27, 8.4), 'paint:#2050a0', 'waybill print')
    m.box((14.2, 10.2, 8.5), (14.9, 10.6, 8.6), 'paint:#1a1a1a', 'printer panel')
    pr = m.surface('north', face=8.5, title='PRINTER')
    m.ctl(pr, 'lamp', 'paper', 16 - 12.6, 10.5, w=0.45, h=0.45, col='30e060', label='')

    m.g('desk things')
    m.box((5.0, 9.0, 11.0), (7.6, 9.6, 12.6), 'paint:#5a3a24', 'in tray')
    m.box((5.2, 9.6, 11.2), (7.4, 9.8, 12.4), 'paint:#fbfbf6', 'paper stack')
    m.box((8.4, 9.0, 11.0), (11.0, 9.6, 12.6), 'paint:#5a3a24', 'out tray')
    m.box((8.6, 9.6, 11.2), (10.8, 10.2, 12.4), 'paint:#f4f0d8', 'waybill stack')
    m.cyl((4.6, 9.0, 12.2), 0.6, 0.3, 'y', 'metal:brass', 'banker lamp foot')
    m.box((4.5, 9.3, 12.1), (4.7, 11.6, 12.3), 'metal:brass', 'banker lamp stem')
    m.box((3.4, 11.4, 11.6), (5.8, 12.2, 12.6), 'paint:#1f6b3a', 'green shade')
    m.box((3.6, 11.3, 11.7), (5.6, 11.4, 12.5), 'paint:#fff4c0', 'bulb glow')
    m.box((2.2, 9.0, 7.9), (3.2, 9.8, 8.3), 'paint:#2a2a2a', 'stamp handle')
    m.box((2.0, 9.0, 7.8), (3.4, 9.2, 8.4), 'paint:#8a1c1c', 'rubber stamp')
    m.box((0.8, 9.0, 12.4), (2.4, 9.3, 12.8), 'paint:#3a5ad0', 'ink pad')
    m.box((15.0, 9.0, 4.0), (15.6, 9.7, 4.6), 'paint:#f0f0f0', 'coffee mug')

    m.terminal_12v(15.6, 1.0, 6.0, 'east')
    m.save(item_gui=0.28)


if __name__ == '__main__':
    office()
    a = ASSETS
    bs = {'variants': {'facing=north': {'model': 'irextras:freight_office'}, 'facing=east': {'model': 'irextras:freight_office', 'y': 90},
                       'facing=south': {'model': 'irextras:freight_office', 'y': 180}, 'facing=west': {'model': 'irextras:freight_office', 'y': 270}}}
    json.dump(bs, open(os.path.join(a, 'blockstates/freight_office.json'), 'w'), indent=1)
    json.dump({'parent': 'irextras:block/freight_office'}, open(os.path.join(a, 'models/item/freight_office.json'), 'w'), indent=1)
    json.dump({'parent': 'item/generated', 'textures': {'layer0': 'irextras:items/waybill'}}, open(os.path.join(a, 'models/item/waybill.json'), 'w'), indent=1)
    from PIL import Image
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(2, 15):
        for x in range(3, 13):
            im.putpixel((x, y), (246, 242, 226, 255) if (x, y) != (12, 2) else (210, 200, 170, 255))
    for y in (4, 6, 8, 10):
        for x in range(4, 12 if y != 10 else 8):
            im.putpixel((x, y), (60, 70, 110, 255))
    for (x, y) in ((9, 11), (10, 11), (11, 11), (9, 12), (11, 12), (9, 13), (10, 13), (11, 13)):
        im.putpixel((x, y), (200, 30, 30, 255))
    os.makedirs(os.path.join(a, 'textures/items'), exist_ok=True)
    im.save(os.path.join(a, 'textures/items/waybill.png'))
    print('blockstate, item models, waybill texture written')
