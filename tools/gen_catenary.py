#!/usr/bin/env python3
"""Overhead line (OHLE) block models for IR Extras, built like the real thing:
mast (stacking H-beam column, base plate + footing at the bottom, cap on top, number plate),
cantilever arm (upper + lower tube, diagonal brace, insulators + hinge at the mast, messenger clamp),
contact wire (contact wire + messenger wire + droppers, steady arm where a cantilever meets it, drop hangers under
a portal girder) and the portal girder (lattice truss). Writes models/block/ohle_*.json, the multipart blockstates
and the textures. Every model opens in Blockbench. Heights: contact wire 2/16 above the wire block's floor,
messenger 19/16 (in the block above) - the cantilever's lower tube meets the contact wire, the upper tube the
messenger."""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/irextras')
MOD = 'irextras'


def tex(name, fn, size=16):
    im = Image.new('RGBA', (size, size))
    rnd = random.Random(name)
    for y in range(size):
        for x in range(size):
            im.putpixel((x, y), fn(x, y, rnd))
    im.save(os.path.join(ROOT, 'textures/blocks', name + '.png'))


def clamp(v): return max(0, min(255, int(v)))


def galv(x, y, r):      # galvanised steel: light grey with faint vertical streaks + speckle
    b = 168 + (6 if x % 5 == 0 else 0) - (8 if x % 7 == 3 else 0) + r.randint(-6, 6)
    return (clamp(b), clamp(b + 3), clamp(b + 8), 255)


def dark(x, y, r):      # painted dark steel (fittings, hinges)
    b = 70 + r.randint(-5, 5)
    return (b, b + 2, b + 6, 255)


def wire(x, y, r):      # weathered copper contact wire: dark bronze with a brighter running face
    b = r.randint(-6, 6)
    return (clamp(118 + b + (25 if y in (7, 8) else 0)), clamp(82 + b + (15 if y in (7, 8) else 0)), clamp(52 + b), 255)


def insul(x, y, r):     # brown glazed porcelain insulator with ribs
    rib = y % 4 in (0, 1)
    b = r.randint(-4, 4)
    return (clamp((124 if rib else 96) + b), clamp((62 if rib else 46) + b), clamp((34 if rib else 26) + b), 255)


def plate(x, y, r):     # mast number plate: white with black digits
    if 1 <= x <= 14 and 4 <= y <= 11 and ((x in (3, 4, 7, 8, 11, 12) and y != 7) or (y in (4, 7, 11) and x not in (5, 6, 9, 10, 13, 14))):
        return (20, 20, 22, 255)
    return (236, 236, 232, 255)


def concrete(x, y, r):
    b = 160 + r.randint(-12, 12)
    return (b, b, clamp(b - 4), 255)


def face(t, uv=None):
    f = {'texture': '#' + t}
    if uv:
        f['uv'] = uv
    return f


def el(a, b, t, rot=None, name=None):
    e = {'from': list(a), 'to': list(b), 'faces': {d: face(t) for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rot:
        e['rotation'] = rot
    if name:
        e['__comment'] = name
    return e


def model(name, elements, textures):
    tx = {'particle': MOD + ':blocks/ohle_galv'}
    tx.update({k: MOD + ':blocks/' + v for k, v in textures.items()})
    d = {'textures': tx, 'elements': elements}
    with open(os.path.join(ROOT, 'models/block', name + '.json'), 'w') as fh:
        json.dump(d, fh, indent=1)


T = {'g': 'ohle_galv', 'd': 'ohle_dark', 'w': 'ohle_wire', 'i': 'ohle_insulator', 'p': 'ohle_plate', 'c': 'ohle_concrete'}


def diag(y0, z0, y1, z1, x0=7.4, x1=8.6, t=1.2, tx='g'):
    """a straight bar from (y0,z0) to (y1,z1) in the y-z plane, as one element rotated about x (±45 / ±22.5)"""
    import math
    cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
    L = math.hypot(y1 - y0, z1 - z0)
    ang = math.degrees(math.atan2(y1 - y0, z1 - z0))     # angle from +z towards +y
    # minecraft allows -45..45 in 22.5 steps; bars are laid along z then rotated
    snap = min((-45, -22.5, 0, 22.5, 45), key=lambda a: abs(a - (ang if abs(ang) <= 90 else ang - 180 * (1 if ang > 0 else -1))))
    return el((x0, cy - t / 2, cz - L / 2), (x1, cy + t / 2, cz + L / 2), tx,
              {'origin': [8, cy, cz], 'axis': 'x', 'angle': -snap, 'rescale': False})


def main():
    tex('ohle_galv', galv)
    tex('ohle_dark', dark)
    tex('ohle_wire', wire)
    tex('ohle_insulator', insul)
    tex('ohle_plate', plate)
    tex('ohle_concrete', concrete)

    # ---- mast (drawn facing north: the cantilever leaves towards -z) ----
    model('ohle_mast_body', [
        el((4, 0, 5), (12, 16, 6.5), 'g'), el((4, 0, 9.5), (12, 16, 11), 'g'), el((7.2, 0, 6.5), (8.8, 16, 9.5), 'g'),
        el((6.5, 9, 4.2), (9.5, 11.5, 5), 'p'),                                               # number plate
    ], T)
    model('ohle_mast_base', [
        el((1.5, -3, 1.5), (14.5, 0, 14.5), 'c'),                                              # concrete footing
        el((2.5, 0, 2.5), (13.5, 1, 13.5), 'd'),                                               # base plate
        el((3, 1, 3), (4, 2, 4), 'd'), el((12, 1, 3), (13, 2, 4), 'd'), el((3, 1, 12), (4, 2, 13), 'd'), el((12, 1, 12), (13, 2, 13), 'd'),
        el((7, 1, 3.5), (9, 4, 5), 'd'), el((7, 1, 11), (9, 4, 12.5), 'd'),                   # gusset plates
    ], T)
    model('ohle_mast_cap', [
        el((3.5, 16, 4.5), (12.5, 17, 11.5), 'd'),
        el((7, 17, 7), (9, 18.5, 9), 'd'),                                                     # earth wire clamp
    ], T)

    # ---- cantilever (facing north: mast at +z / south edge, track at -z / north edge) ----
    model('ohle_cant_arm', [
        el((7.2, 20, 0), (8.8, 21.4, 16), 'g'),                                                # upper tube (messenger support)
        el((7.2, 3, 0), (8.8, 4.4, 16), 'g'),                                                  # lower tube (registration)
        diag(4.4, 16, 20, 0.5),                                                                # diagonal brace
        el((7, 21.4, 0.5), (9, 22.6, 2.5), 'd'),                                               # messenger clamp
        el((7.5, 4.4, 1), (8.5, 20, 2), 'g'),                                                  # vertical tie at the track end
    ], T)
    model('ohle_cant_hinge', [
        el((5.5, 1.5, 15), (10.5, 23.5, 16.5), 'd'),                                           # hinge bracket bolted to the mast
        el((6.5, 2, 13.8), (9.5, 5.4, 14.6), 'i'), el((6.2, 2.2, 12.6), (9.8, 5.2, 13.2), 'i'), el((6.5, 2, 11.4), (9.5, 5.4, 12.2), 'i'),
        el((6.5, 19, 13.8), (9.5, 22.4, 14.6), 'i'), el((6.2, 19.2, 12.6), (9.8, 22.2, 13.2), 'i'), el((6.5, 19, 11.4), (9.5, 22.4, 12.2), 'i'),
    ], T)

    # ---- contact wire (side piece drawn for 'north'; rotated for the others) ----
    model('ohle_wire_core', [
        el((7.5, 1.5, 7.5), (8.5, 2.5, 8.5), 'w'), el((7.6, 19, 7.6), (8.4, 19.8, 8.4), 'w')], T)
    model('ohle_wire_side', [
        el((7.5, 1.5, 0), (8.5, 2.5, 7.5), 'w', name='contact wire'),
        el((7.6, 19, 0), (8.4, 19.8, 7.6), 'w', name='messenger wire'),
        el((7.85, 2.5, 3.6), (8.15, 19, 3.9), 'w', name='dropper'),
        el((7.4, 2.2, 3.4), (8.6, 2.9, 4.1), 'd'), el((7.4, 18.6, 3.4), (8.6, 19.3, 4.1), 'd'),   # dropper clamps
    ], T)
    model('ohle_wire_arm', [
        el((7.2, 3, 0), (8.8, 4.4, 4), 'g', name='registration tube'),
        diag(4.4, 3, 2.5, 7.6, x0=7.6, x1=8.4, t=0.8, tx='d'),                                  # steady arm down to the wire
        el((7.3, 1.2, 7.2), (8.7, 2.8, 8.8), 'd', name='clamp'),
        el((7.2, 20, 0), (8.8, 21.4, 8.6), 'g', name='upper tube'),
        el((7, 19.6, 6.8), (9, 21.6, 9), 'd', name='messenger support'),
    ], T)
    model('ohle_wire_hanger', [
        el((7.6, 19.8, 7.6), (8.4, 24.5, 8.4), 'g'),
        el((6.8, 21.4, 6.8), (9.2, 22, 9.2), 'i'), el((7, 22.4, 7), (9, 23, 9), 'i'),
    ], T)

    # ---- portal girder (lattice truss along z when facing north) ----
    els = [el((5, 14.4, 0), (6.4, 16, 16), 'g'), el((9.6, 14.4, 0), (11, 16, 16), 'g'),      # top chords
           el((5, 8, 0), (6.4, 9.6, 16), 'g'), el((9.6, 8, 0), (11, 9.6, 16), 'g'),          # bottom chords
           el((6.4, 15, 0), (9.6, 15.6, 16), 'g'), el((6.4, 8.4, 0), (9.6, 9, 16), 'g')]     # top + bottom ties
    for x0, x1 in ((5.2, 6.2), (9.8, 10.8)):
        els += [el((x0, 9.6, 0), (x1, 14.4, 1), 'g'), el((x0, 9.6, 7.5), (x1, 14.4, 8.5), 'g'),
                diag(9.6, 1, 14.4, 7.5, x0=x0, x1=x1, t=0.8), diag(14.4, 8.5, 9.6, 15, x0=x0, x1=x1, t=0.8)]
    model('ohle_portal', els, T)

    # ---- blockstates (multipart) ----
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

    def bs(name, parts):
        with open(os.path.join(ROOT, 'blockstates', name + '.json'), 'w') as fh:
            json.dump({'multipart': parts}, fh, indent=1)

    m = lambda n, y=0: {'model': MOD + ':' + n, 'y': y} if y else {'model': MOD + ':' + n}
    bs('catenary_mast', [{'when': {'facing': f}, 'apply': m('ohle_mast_body', y)} for f, y in rot.items()] +
       [{'when': {'down': 'false'}, 'apply': m('ohle_mast_base')}, {'when': {'up': 'false'}, 'apply': m('ohle_mast_cap')}])
    bs('catenary_cantilever', [{'when': {'facing': f}, 'apply': m('ohle_cant_arm', y)} for f, y in rot.items()] +
       [{'when': {'facing': f, 'hung': 'true'}, 'apply': m('ohle_cant_hinge', y)} for f, y in rot.items()])
    # the cantilever beside the wire comes from that side, so the arm piece points back at it
    side = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    armkey = {'north': 'arm_n', 'east': 'arm_e', 'south': 'arm_s', 'west': 'arm_w'}
    parts = [{'apply': m('ohle_wire_core')}]
    parts += [{'when': {d: 'true'}, 'apply': m('ohle_wire_side', y)} for d, y in side.items()]
    parts += [{'when': {'north': 'false', 'south': 'false', 'east': 'false', 'west': 'false'}, 'apply': m('ohle_wire_side', y)} for y in (0, 180)]
    parts += [{'when': {armkey[d]: 'true'}, 'apply': m('ohle_wire_arm', y)} for d, y in side.items()]
    parts += [{'when': {'hung': 'true'}, 'apply': m('ohle_wire_hanger')}]
    bs('catenary_wire', parts)
    bs('catenary_portal', [{'when': {'facing': f}, 'apply': m('ohle_portal', y)} for f, y in rot.items()])

    # items
    for n, mdl in (('catenary_mast', 'ohle_mast_body'), ('catenary_cantilever', 'ohle_cant_arm'),
                   ('catenary_wire', 'ohle_wire_side'), ('catenary_portal', 'ohle_portal')):
        with open(os.path.join(ROOT, 'models/item', n + '.json'), 'w') as fh:
            json.dump({'parent': MOD + ':block/' + mdl}, fh)
    print('ok')


if __name__ == '__main__':
    main()
