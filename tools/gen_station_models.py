#!/usr/bin/env python3
"""Block models (open in Blockbench) + textures for the station door controller and platform screen doors."""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/irextras')
MOD = 'irextras'


def tex(name, fn, w=16, h=16):
    im = Image.new('RGBA', (w, h))
    r = random.Random(name)
    for y in range(h):
        for x in range(w):
            im.putpixel((x, y), fn(x, y, r))
    im.save(os.path.join(ROOT, 'textures/blocks', name + '.png'))


def alu(x, y, r):
    b = 196 + (8 if y % 6 == 0 else 0) + r.randint(-4, 4)
    return (b, b + 2, b + 6, 255)


def glass(x, y, r):          # clear cut-out glass with a faint frit dot band and edge tint
    if x in (0, 15) or y in (0, 15):
        return (150, 190, 205, 255)
    if 6 <= y <= 7 and x % 3 == 0:
        return (200, 215, 222, 255)                # manifestation dots at eye level
    return (0, 0, 0, 0)


def header(x, y, r):         # charcoal header with one thin amber LED message line
    if y == 8 and 2 <= x <= 13 and x % 3 != 0:
        return (255, 170, 50, 255)
    if y in (0, 15):
        return (70, 74, 80, 255)
    return (34, 36, 40, 255)


def lamp_off(x, y, r): return (70, 72, 70, 255)
def lamp_green(x, y, r): return (90, 240, 120, 255) if 2 <= x <= 13 and 2 <= y <= 13 else (40, 140, 60, 255)
def lamp_red(x, y, r): return (250, 70, 60, 255) if 2 <= x <= 13 and 2 <= y <= 13 else (150, 30, 25, 255)


def screen(x, y, r):         # controller status screen: green text lines
    if y in (3, 6, 9, 12) and 2 <= x <= (13 if y != 12 else 8) and r.random() > 0.25:
        return (110, 245, 140, 255)
    return (10, 16, 14, 255)


def hazard(x, y, r):
    return (240, 196, 30, 255) if ((x + y) // 4) % 2 == 0 else (24, 24, 26, 255)


def rubber(x, y, r): return (22, 22, 24, 255)


def el(a, b, t, name=None):
    e = {'from': list(a), 'to': list(b), 'faces': {d: {'texture': '#' + t} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if name:
        e['__comment'] = name
    return e


def model(name, els, tx, particle='psd_alu'):
    d = {'textures': dict({'particle': MOD + ':blocks/' + particle}, **{k: MOD + ':blocks/' + v for k, v in tx.items()}), 'elements': els}
    json.dump(d, open(os.path.join(ROOT, 'models/block', name + '.json'), 'w'), indent=1)


T = {'a': 'psd_alu', 'g': 'psd_glass', 'h': 'psd_header', 'r': 'psd_rubber', 'lo': 'psd_lamp_off', 'lg': 'psd_lamp_green',
     'lr': 'psd_lamp_red', 's': 'sdc_screen', 'z': 'psd_hazard', 'd': 'ohle_dark', 'm': 'ohle_galv'}


def main():
    tex('psd_alu', alu); tex('psd_glass', glass); tex('psd_header', header); tex('psd_lamp_off', lamp_off)
    tex('psd_lamp_green', lamp_green); tex('psd_lamp_red', lamp_red); tex('sdc_screen', screen); tex('psd_hazard', hazard)
    tex('psd_rubber', rubber)

    # station door controller (front = north face)
    model('station_doors', [
        el((2, 0, 2), (14, 1, 14), 'd', 'base plate'),
        el((3, 1, 4.6), (13, 3, 11.4), 'z', 'hazard plinth'),
        el((4, 3, 5), (12, 17, 11), 'm', 'cabinet'),
        el((3.5, 17, 4.5), (12.5, 18, 11.5), 'd', 'rain cap'),
        el((5, 11, 4.7), (11, 15.5, 5), 's', 'status screen'),
        el((5.5, 8, 4.7), (7.2, 9.6, 5), 'lg', 'doors-open lamp'),
        el((8.8, 8, 4.7), (10.5, 9.6, 5), 'lr', 'doors-closed lamp'),
        el((7.4, 5, 4.6), (8.6, 6.2, 5), 'd', 'key switch'),
        el((5, 4, 4.7), (11, 4.6, 5), 'a', 'door-open push bar'),
        el((7.5, 18, 7.5), (8.5, 22, 8.5), 'd', 'antenna'),
    ], T, 'ohle_galv')
    bs = {'variants': {}}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        bs['variants']['facing=%s' % f] = {'model': MOD + ':station_doors', 'y': y} if y else {'model': MOD + ':station_doors'}
    json.dump(bs, open(os.path.join(ROOT, 'blockstates/station_doors.json'), 'w'), indent=1)

    # platform screen door, stages 0..4 (leaves slide apart 2 px per stage), wall along x, 2 blocks tall
    def leaf(x0, x1):
        return [
            el((x0, -7.5, 7.3), (x0 + 1, 19.5, 8.7), 'a'), el((x1 - 1, -7.5, 7.3), (x1, 19.5, 8.7), 'a'),
            el((x0 + 1, -7.5, 7.3), (x1 - 1, -5, 8.7), 'a'), el((x0 + 1, 17.5, 7.3), (x1 - 1, 19.5, 8.7), 'a'),
            el((x0 + 1, -5, 7.8), (x1 - 1, 17.5, 8.2), 'g', 'glass')]
    for st in range(4):
        off = round(st * 8 / 3, 2)
        els = [el((0, -8, 6), (16, -7.5, 10), 'a', 'sill'),
               el((-0.5, 19.5, 6), (16.5, 24, 10), 'h', 'header'),
               el((7, 20.5, 5.8), (9, 22.5, 6), 'lg' if st > 0 else 'lo', 'lamp')]
        els += leaf(0 - off, 8 - off) + leaf(8 + off, 16 + off)
        els += [el((8 - off - 0.3, -7.5, 7.2), (8 - off, 19.5, 8.8), 'r'), el((8 + off, -7.5, 7.2), (8 + off + 0.3, 19.5, 8.8), 'r')]
        model('psd_door_s%d' % st, els, T)
    model('psd_panel', [el((0, -8, 6), (16, -7.5, 10), 'a'), el((-0.5, 19.5, 6), (16.5, 24, 10), 'h'),
                        el((0, -7.5, 7.3), (1, 19.5, 8.7), 'a'), el((15, -7.5, 7.3), (16, 19.5, 8.7), 'a'),
                        el((1, -7.5, 7.3), (15, -5, 8.7), 'a'), el((1, -5, 7.8), (15, 19.5, 8.2), 'g'),
                        el((4, 2, 7.6), (12, 10, 7.8), 'h', 'route map panel')], T)
    for name in ('psd_door', 'psd_panel'):
        bs = {'variants': {}}
        for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for st in range(4):
                mdl = MOD + ':' + ('psd_door_s%d' % st if name == 'psd_door' else 'psd_panel')
                bs['variants']['facing=%s,stage=%d' % (f, st)] = {'model': mdl, 'y': y} if y else {'model': mdl}
        json.dump(bs, open(os.path.join(ROOT, 'blockstates/%s.json' % name), 'w'), indent=1)
    for n, mdl in (('station_doors', 'station_doors'), ('psd_door', 'psd_door_s0'), ('psd_panel', 'psd_panel')):
        json.dump({'parent': MOD + ':block/' + mdl}, open(os.path.join(ROOT, 'models/item', n + '.json'), 'w'))
    print('ok')


if __name__ == '__main__':
    main()
