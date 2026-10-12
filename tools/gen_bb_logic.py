#!/usr/bin/env python3
"""Railway logic modules (her ChatGPT list #99): seven DIN-rail logic relays in a small wall enclosure - GATE, TIMER,
COUNTER, COMPARE, LATCH / EDGE, MATH and SEQUENCER (state machine). Front = north; the enclosure hangs on the wall
behind (z 6..16), the module's working panel faces you at z = 6.5:
  LCD (two lines), LEDs 12V / A / B / C / Q / ERR, FUNCTION selector, STEP x1/x10/x100, preset UP / DOWN, RESET,
  the FORCE key (AUTO / ON / OFF); the timer adds an elapsed gauge, the sequencer eight state LEDs.
Inside: DIN rail, input / output terminal blocks with coloured wire tails into slotted ducts, an earth bar, the
12 V terminal, cable glands. Writes models, controls layouts, blockstates, item models, recipes and lang lines."""
import json, os
from bbkit import Model, ASSETS

KINDS = [  # registry id, panel title, label strip, functions (selector positions), colour of the module's front stripe
    ('logic_gate', 'LOGIC GATE', 'GATE', ['AND', 'OR', 'XOR', 'NAND', 'NOR', 'XNOR', 'NOT A', 'BUF A'], '3a7bd5'),
    ('logic_timer', 'TIMER RELAY', 'TIMER', ['ON DLY', 'OFF DLY', 'PULSE', 'FLASH'], 'e0a020'),
    ('logic_counter', 'COUNTER', 'COUNTER', ['UP', 'DOWN', 'UP/DN'], '30a060'),
    ('logic_compare', 'COMPARATOR', 'COMPARE', ['A>B', 'A>=B', 'A=B', 'A<=B', 'A<B', 'A<>B', 'WINDOW'], '8a4fd0'),
    ('logic_latch', 'LATCH + EDGE', 'LATCH', ['SR', 'RS', 'TOGGLE', 'RISE', 'FALL', 'EDGE'], 'd04a6a'),
    ('logic_math', 'ARITHMETIC', 'MATH', ['A+B', 'A-B', 'AxB', 'A/B', 'MIN', 'MAX', 'AVG', 'DIFF'], '20a0b0'),
    ('logic_sequencer', 'SEQUENCER', 'SEQUENCER', ['STEP', 'TIMED'], 'c06020'),
]
WIRE = {'A': 'f9801d', 'B': '3ab3da', 'C': 'fed83d', 'Q': 'b02e26', '+': 'c81e1e', '-': '1d1d21'}


def module(name, title, strip, fns, accent):
    m = Model(name)
    # ---- the enclosure on the wall
    m.g('mounting plate')
    m.box((0.6, 0.2, 15.4), (15.4, 15.2, 16.0), 'galv', 'mounting plate')
    for x in (1.4, 14.6):
        for y in (1.0, 14.4):
            m.bolt(x, y, 15.4, 'north', 0.5)
    m.g('enclosure')
    m.box((1.0, 0.5, 7.0), (15.0, 1.0, 15.4), 'ral7035', 'enclosure floor')
    m.box((1.0, 14.0, 7.0), (15.0, 14.5, 15.4), 'ral7035', 'enclosure roof')
    m.box((1.0, 1.0, 7.0), (1.5, 14.0, 15.4), 'ral7035', 'left wall')
    m.box((14.5, 1.0, 7.0), (15.0, 14.0, 15.4), 'ral7035', 'right wall')
    m.box((1.5, 1.0, 14.9), (14.5, 14.0, 15.4), 'paint:#b9bbb8', 'back pan')
    m.box((0.9, 14.0, 6.8), (15.1, 14.7, 7.2), 'paint:#2b2f33', 'door gasket top')
    m.box((0.9, 0.3, 6.8), (15.1, 1.0, 7.2), 'paint:#2b2f33', 'door gasket bottom')
    for y in (2.0, 12.0):                                                  # the clear door hinged open against the left wall
        m.box((0.4, y, 7.0), (1.0, y + 1.2, 7.6), 'metal:steel', 'door hinge')
    m.box((-0.2, 1.0, 7.4), (0.4, 14.0, 13.8), 'glass', 'clear door (open)')
    m.box((-0.3, 7.0, 13.0), (-0.2, 8.6, 13.6), 'paint:#2b2f33', 'door latch')
    m.nameplate(4.0, 14.7, 12.0, 15.6, 7.0, title)
    # ---- DIN rail + module
    m.g('DIN rail')
    m.box((1.5, 7.2, 14.4), (14.5, 8.6, 14.9), 'metal:alu', 'DIN rail 35 mm')
    for x in (2.2, 13.8):
        m.box((x - 0.3, 7.5, 14.2), (x + 0.3, 8.3, 14.4), 'metal:steel', 'rail screw')
    m.g('module')
    m.box((3.0, 2.5, 6.5), (13.0, 12.5, 14.4), 'paint:#e6e7e3', 'module housing')
    m.box((3.0, 12.5, 7.4), (13.0, 13.0, 14.4), 'paint:#d6d7d3', 'module top step')
    m.box((3.0, 2.0, 7.4), (13.0, 2.5, 14.4), 'paint:#d6d7d3', 'module bottom step')
    m.box((3.0, 11.9, 6.4), (13.0, 12.5, 6.5), 'paint:#' + accent, 'function stripe')
    m.box((3.4, 8.6, 6.42), (12.6, 11.9, 6.5), 'paint:#2b2f33', 'display bezel')
    m.box((3.8, 9.8, 6.4), (12.2, 11.6, 6.42), 'screen', 'LCD')
    m.box((3.4, 2.6, 6.42), (12.6, 3.2, 6.5), 'label:' + strip, 'label strip')
    m.box((7.6, 1.6, 9.0), (8.4, 2.0, 11.0), 'paint:#2b2f33', 'DIN clip')
    for x in (3.4, 12.6):
        m.box((x - 0.15, 4.0, 6.45), (x + 0.15, 8.0, 6.5), 'paint:#c8c9c5', 'housing rib')
    # ---- terminal blocks (top: inputs, bottom: output + supply) with wire tails into the ducts
    m.g('terminals')
    for i, t in enumerate(['A', 'B', 'C', '+', '-']):
        x = 4.0 + i * 1.9
        m.box((x - 0.8, 13.0, 8.0), (x + 0.8, 13.9, 14.4), 'paint:#3a3f44' if t in 'ABC' else 'paint:#8a1c1c' if t == '+' else 'paint:#202226', 'input terminal')
        m.box((x - 0.35, 13.9, 8.4), (x + 0.35, 14.0, 9.1), 'metal:steel', 'terminal screw')
        m.box((x - 0.7, 13.2, 7.9), (x + 0.7, 13.7, 8.0), 'label:' + ('12V' if t == '+' else '0V' if t == '-' else t), 'terminal marker')
        m.cyl((x, 13.9, 10.6), 0.22, 0.6, 'y', 'cable:' + WIRE[t], 'wire ' + t)
    for i, t in enumerate(['Q', 'Q', '-']):
        x = 5.0 + i * 3.0
        m.box((x - 0.8, 1.1, 8.0), (x + 0.8, 2.0, 14.4), 'paint:#3a3f44' if t == 'Q' else 'paint:#202226', 'output terminal')
        m.box((x - 0.35, 1.0, 8.4), (x + 0.35, 1.1, 9.1), 'metal:steel', 'terminal screw')
        m.box((x - 0.7, 1.3, 7.9), (x + 0.7, 1.8, 8.0), 'label:' + ('Q' if t == 'Q' else '0V'), 'terminal marker')
        m.cyl((x, 0.95, 11.0), 0.22, 0.1, 'y', 'cable:' + WIRE[t], 'wire ' + t)
    m.g('wire ducts')
    for x0, x1 in ((1.5, 2.8), (13.2, 14.5)):
        m.box((x0, 1.0, 8.0), (x1, 14.0, 14.9), 'paint:#8d8f8c', 'slotted duct')
        for y in range(2, 14, 2):
            m.box((x0, y, 7.95), (x1, y + 0.4, 8.0), 'paint:#5c5e5b', 'duct slot')
        m.box((x0 - 0.05, 1.0, 7.8), (x1 + 0.05, 14.0, 8.0), 'paint:#a3a5a2', 'duct cover')
    for i, (y, c) in enumerate(((13.4, WIRE['A']), (12.9, WIRE['B']), (12.4, WIRE['C']))):
        m.box((2.8, y, 10.0 + i * 0.6), (3.0, y + 0.3, 10.3 + i * 0.6), 'cable:' + c, 'wire into duct')
    m.g('earth bar')
    m.box((4.0, 1.1, 14.3), (12.0, 1.6, 14.9), 'metal:brass', 'earth bar')
    for x in (5.0, 6.5, 8.0, 9.5, 11.0):
        m.box((x - 0.2, 1.6, 14.4), (x + 0.2, 1.8, 14.8), 'metal:steel', 'earth screw')
    m.box((11.5, 1.3, 14.0), (12.8, 1.5, 14.3), 'cable:5e7c16', 'earth lead')
    m.g('cable entry')
    for x in (4.0, 6.5, 9.5, 12.0):
        m.gland(x, 0.5, 12.0, 'down', r=0.35)
    m.terminal_12v(15.0, 3.0, 11.5, 'east')
    # ---- the live front panel
    f = m.surface('north', face=6.38, title=title)
    m.ctl(f, 'digits', 'd1', 8.0, 11.05, w=8.2, h=0.7, col='7cf29a', label='', fmt='%s')
    m.ctl(f, 'digits', 'd2', 8.0, 10.25, w=8.2, h=0.7, col='b8f2c8', label='', fmt='%s')
    for i, (cid, lab, col, blink) in enumerate((('pwr', '12V', '30e060', False), ('la', 'A', 'ffb020', False), ('lb', 'B', 'ffb020', False),
                                                  ('lc', 'C', 'ffb020', False), ('lq', 'Q', 'ff3030', False), ('err', 'ERR', 'ff3030', True))):
        m.ctl(f, 'lamp', cid, 4.2 + i * 1.52, 9.05, w=0.55, h=0.55, col=col, label=lab, **({'blink': True} if blink else {}))
    m.ctl(f, 'selector', 'fn', 4.6, 6.5, w=1.5, h=1.5, col='202020', label='FUNCTION', pos=fns)
    m.ctl(f, 'selector', 'step', 6.9, 6.5, w=1.1, h=1.1, col='404448', label='STEP', pos=['X1', 'X10', 'X100'])
    m.ctl(f, 'push', 'up', 9.0, 7.1, w=1.0, h=0.9, col='e8e8e8', label='')
    m.ctl(f, 'push', 'down', 9.0, 5.8, w=1.0, h=0.9, col='e8e8e8', label='PRESET')
    m.ctl(f, 'push', 'reset', 11.4, 6.5, w=1.3, h=1.3, col='f0d020', label='RESET')
    m.ctl(f, 'key', 'force', 4.6, 4.0, w=1.2, h=1.2, col='c0a040', label='FORCE', pos=['AUTO', 'ON', 'OFF'])
    if name == 'logic_timer':
        m.ctl(f, 'gauge', 'elapsed', 9.6, 4.1, w=2.4, h=2.4, col='f2f0e6', label='ELAPSED %', max=100)
    elif name == 'logic_sequencer':
        for i in range(8):
            m.ctl(f, 'lamp', 's%d' % (i + 1), 6.6 + i * 0.75, 4.1, w=0.45, h=0.45, col='40c0ff', label=str(i + 1))
    else:
        m.ctl(f, 'label', '', 9.6, 4.3, w=0, h=0, col='303030', label='IN A B C')
        m.ctl(f, 'label', '', 9.6, 3.7, w=0, h=0, col='303030', label='OUT Q')
    m.save(item_gui=0.55)
    return len(m.boxes)


LANG = {'tile.irextras.%s.name' % k[0]: n for k, n in zip(KINDS, ['Logic Gate Module', 'Timer Relay', 'Counter Module', 'Comparator Module',
                                                                    'Latch + Edge Module', 'Arithmetic Module', 'Sequencer (State Machine)'])}

P = lambda n: {'item': 'irextras:part', 'data': n}
SCREEN, CIRCUIT, CONTACTOR, FRAME = 20, 2, 18, 1
EXTRA = {'logic_gate': {'item': 'minecraft:comparator'}, 'logic_timer': {'item': 'minecraft:clock'}, 'logic_counter': {'item': 'minecraft:repeater'},
         'logic_compare': {'item': 'minecraft:comparator'}, 'logic_latch': {'item': 'minecraft:lever'}, 'logic_math': {'item': 'minecraft:redstone'},
         'logic_sequencer': {'item': 'minecraft:repeater'}}

if __name__ == '__main__':
    for (n, title, strip, fns, acc) in KINDS:
        boxes = module(n, title, strip, fns, acc)
        print('%-16s %d boxes' % (n, boxes))
        bs = {'variants': {'facing=north': {'model': 'irextras:' + n}, 'facing=east': {'model': 'irextras:' + n, 'y': 90},
                           'facing=south': {'model': 'irextras:' + n, 'y': 180}, 'facing=west': {'model': 'irextras:' + n, 'y': 270}}}
        json.dump(bs, open(os.path.join(ASSETS, 'blockstates/%s.json' % n), 'w'), indent=1)
        json.dump({'parent': 'irextras:block/' + n}, open(os.path.join(ASSETS, 'models/item/%s.json' % n), 'w'), indent=1)
        json.dump({'type': 'forge:ore_shaped', 'pattern': ['SCS', 'XTX', 'FRF'],
                   'key': {'S': P(SCREEN), 'C': P(CIRCUIT), 'T': P(CONTACTOR), 'F': P(FRAME), 'X': EXTRA[n], 'R': {'item': 'minecraft:redstone'}},
                   'result': {'item': 'irextras:' + n, 'count': 1}}, open(os.path.join(ASSETS, 'recipes/%s.json' % n), 'w'), indent=1)
    lang = os.path.join(ASSETS, 'lang/en_us.lang')
    have = open(lang, encoding='utf-8').read()
    add = [k + '=' + v for k, v in LANG.items() if k + '=' not in have]
    if add:
        with open(lang, 'a', encoding='utf-8') as f: f.write(('' if have.endswith('\n') else '\n') + '\n# railway logic modules (her list #99)\n' + '\n'.join(add) + '\n')
    print('logic module assets written')
