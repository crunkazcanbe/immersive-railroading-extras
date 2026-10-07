#!/usr/bin/env python3
"""Pride Rail power grid block models (Java block JSON = Blockbench native), textures, blockstates, item models.
Every machine is modelled on the real thing: HV gantry + bushings (intake), oil transformer with radiator banks and
conservator, rectifier cubicles with louvres, switchgear with mimic diagram + operating handle, track feeder clamp,
meter, battery container, control desk, armoured feeder cable."""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/irextras')
MOD = 'irextras'


def tex(name, fn, w=16, h=16):
    if os.path.exists(os.path.join(ROOT, 'textures/blocks', name + '.png')):
        return   # HD version from gen_hd_textures.py wins
    im = Image.new('RGBA', (w, h))
    r = random.Random(name)
    for y in range(h):
        for x in range(w):
            im.putpixel((x, y), tuple(int(max(0, min(255, v))) for v in fn(x, y, r)))
    im.save(os.path.join(ROOT, 'textures/blocks', name + '.png'))


def paint(base, n=5):
    def f(x, y, r):
        b = r.randint(-n, n)
        return (base[0] + b, base[1] + b, base[2] + b, 255)
    return f


def tank(x, y, r):                       # transformer tank: grey-green paint with weld seams
    b = r.randint(-4, 4)
    seam = -14 if y in (0, 15) or x in (0, 15) else 0
    return (92 + b + seam, 108 + b + seam, 100 + b + seam, 255)


def fins(x, y, r):                       # radiator: vertical fins
    v = 70 if x % 2 == 0 else 112
    return (v - 6, v + 6, v, 255)


def cabinet(x, y, r):                    # RAL 7035 light grey cubicle with a door seam
    b = r.randint(-3, 3)
    d = -30 if x == 8 else 0
    return (198 + b + d, 200 + b + d, 196 + b + d, 255)


def louvre(x, y, r):
    return (40, 42, 46, 255) if y % 3 == 0 else (176, 178, 174, 255)


def switchgear(x, y, r):                 # cream switchgear front with a mimic diagram (busbar + breaker symbol)
    if y == 4 and 1 <= x <= 14: return (30, 60, 160, 255)
    if x == 8 and 4 <= y <= 12: return (30, 60, 160, 255)
    if 6 <= x <= 10 and 7 <= y <= 9: return (220, 40, 40, 255)
    b = r.randint(-3, 3)
    return (226 + b, 220 + b, 196 + b, 255)


def warning(x, y, r):                    # yellow warning triangle with a black bolt
    inside = y >= 3 and abs(x - 7.5) <= (y - 3) * 0.6 and y <= 14
    if not inside: return (226, 220, 196, 255)
    if (x, y) in {(8, 6), (7, 7), (8, 7), (7, 8), (6, 9), (7, 9), (8, 9), (7, 10), (6, 11), (7, 12)}: return (15, 15, 15, 255)
    edge = y == 14 or abs(abs(x - 7.5) - (y - 3) * 0.6) < 0.8
    return (15, 15, 15, 255) if edge else (245, 200, 30, 255)


def display(x, y, r):                    # dark display with green figures
    if y in (5, 6, 9, 10) and 2 <= x <= 13 and (x % 4) != 1:
        return (90, 250, 120, 255)
    return (12, 18, 16, 255)


def scada(x, y, r):                      # control room screen: network diagram
    if y in (4, 11) and 1 <= x <= 14: return (90, 200, 255, 255)
    if x in (3, 8, 12) and 4 <= y <= 11: return (90, 200, 255, 255)
    if (x, y) in {(3, 4), (8, 11), (12, 4)}: return (120, 255, 140, 255)
    if (x, y) == (12, 11): return (255, 80, 60, 255)
    return (10, 16, 30, 255)


def battery(x, y, r):                    # container with ribbed doors and a blue band
    if 6 <= y <= 7: return (40, 110, 200, 255)
    v = 222 if x % 4 else 196
    return (v, v + 2, v + 4, 255)


def cable(x, y, r):
    b = r.randint(-4, 4)
    return (28 + b + (12 if y in (6, 7) else 0), 28 + b, 30 + b, 255)


def copper(x, y, r):
    b = r.randint(-8, 8)
    return (196 + b, 122 + b, 62 + b, 255)


def lamp(c):
    def f(x, y, r):
        if 3 <= x <= 12 and 3 <= y <= 12: return c + (255,)
        return tuple(v // 2 for v in c) + (255,)
    return f


def engine(x, y, r):                     # painted engine block: deep green with bolt rows
    b = r.randint(-5, 5)
    if y % 5 == 0 or x % 8 == 0: return (36 + b, 70 + b, 52 + b, 255)
    if (x % 4 == 2 and y % 5 == 2): return (150, 150, 150, 255)
    return (46 + b, 96 + b, 68 + b, 255)


def solar(x, y, r):                      # monocrystalline cells with silver busbars
    if x % 8 == 0 or y % 8 == 0: return (190, 196, 205, 255)
    if x % 4 == 0: return (70, 90, 140, 255)
    b = r.randint(-6, 6)
    return (22 + b, 34 + b, 78 + b, 255)


def blade(x, y, r):                      # white composite with a red tip band
    if y < 3: return (200, 40, 40, 255)
    b = r.randint(-3, 3)
    return (236 + b, 238 + b, 240 + b, 255)


def button(c):
    def f(x, y, r):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if d < 5.5: return tuple(min(255, int(v * (1.25 - d / 14))) for v in c) + (255,)
        if d < 7: return (40, 40, 44, 255)
        return (70, 72, 78, 255)
    return f


def arrow(up):
    def f(x, y, r):
        yy = y if up else 15 - y
        if 3 <= yy <= 12 and abs(x - 7.5) <= (yy - 3) * 0.55: return (250, 230, 120, 255)
        return (52, 54, 60, 255)
    return f


def el(a, b, t, name=None, rot=None):
    e = {'from': [round(v, 3) for v in a], 'to': [round(v, 3) for v in b], 'faces': {d: {'texture': '#' + t} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rot: e['rotation'] = rot
    if name: e['__comment'] = name
    return e


T = {'tank': 'grid_tank', 'fins': 'grid_fins', 'cab': 'grid_cabinet', 'lv': 'grid_louvre', 'sw': 'grid_switchgear',
     'warn': 'grid_warning', 'disp': 'grid_display', 'scada': 'grid_scada', 'bat': 'grid_battery', 'cable': 'grid_cable',
     'cu': 'grid_copper', 'ins': 'ohle_insulator', 'con': 'ohle_concrete', 'galv': 'ohle_galv', 'dark': 'ohle_dark',
     'haz': 'psd_hazard', 'lg': 'psd_lamp_green', 'lr': 'psd_lamp_red', 'lo': 'psd_lamp_off', 'la': 'grid_lamp_amber', 'plate': 'ohle_plate',
     'eng': 'grid_engine', 'sol': 'grid_solar_cells', 'blade': 'grid_blade', 'bstart': 'grid_btn_start', 'bstop': 'grid_btn_stop',
     'bup': 'grid_btn_up', 'bdown': 'grid_btn_down'}


def model(name, els, particle='grid_cabinet'):
    d = {'textures': dict({'particle': MOD + ':blocks/' + particle}, **{k: MOD + ':blocks/' + v for k, v in T.items()}), 'elements': els}
    json.dump(d, open(os.path.join(ROOT, 'models/block', name + '.json'), 'w'), indent=1)


def bushing(x, z, y0, h, cap=True):
    """porcelain bushing: stacked sheds + copper terminal"""
    out = [el((x - 0.7, y0, z - 0.7), (x + 0.7, y0 + h, z + 0.7), 'ins')]
    k = 0
    y = y0 + 0.5
    while y < y0 + h - 0.6:
        out.append(el((x - 1.3, y, z - 1.3), (x + 1.3, y + 0.5, z + 1.3), 'ins'))
        y += 1.2
        k += 1
    if cap:
        out += [el((x - 0.9, y0 + h, z - 0.9), (x + 0.9, y0 + h + 0.8, z + 0.9), 'cu'),
                el((x - 0.3, y0 + h + 0.8, z - 0.3), (x + 0.3, y0 + h + 2.2, z + 0.3), 'cu')]
    return out


def main():
    tex('grid_tank', tank); tex('grid_fins', fins); tex('grid_cabinet', cabinet); tex('grid_louvre', louvre)
    tex('grid_switchgear', switchgear); tex('grid_warning', warning); tex('grid_display', display); tex('grid_scada', scada)
    tex('grid_battery', battery); tex('grid_cable', cable); tex('grid_copper', copper); tex('grid_lamp_amber', lamp((255, 170, 30)))
    tex('grid_engine', engine); tex('grid_solar_cells', solar); tex('grid_blade', blade)
    tex('grid_btn_start', button((60, 200, 90))); tex('grid_btn_stop', button((220, 50, 50)))
    tex('grid_btn_up', arrow(True)); tex('grid_btn_down', arrow(False))

    for st in (0, 1):
        on = 'lg' if st else 'lo'
        # ---- grid intake: concrete pad, HV gantry, three bushings, incoming conductors, arrester, LV cubicle
        model('grid_intake_%d' % st, [
            el((0, 0, 0), (16, 1, 16), 'con', 'pad'),
            el((1, 1, 6), (3, 30, 8), 'galv', 'gantry leg'), el((13, 1, 6), (15, 30, 8), 'galv', 'gantry leg'),
            el((0, 28, 5.5), (16, 30, 8.5), 'galv', 'crossbeam'),
            *bushing(4, 7, 18, 9), *bushing(8, 7, 18, 9), *bushing(12, 7, 18, 9),
            el((3.7, 27, 6.7), (4.3, 32, 7.3), 'cu'), el((7.7, 27, 6.7), (8.3, 32, 7.3), 'cu'), el((11.7, 27, 6.7), (12.3, 32, 7.3), 'cu'),
            el((2.5, 1, 10), (13.5, 10, 15), 'cab', 'LV cubicle'),
            el((3.5, 6, 9.8), (6, 9, 10), 'disp'), el((7, 7, 9.8), (8.2, 8.2, 10), on), el((9, 3, 9.8), (12.5, 8.5, 10), 'warn'),
            el((14, 1, 12), (15.2, 14, 13.2), 'ins', 'surge arrester'), el((13.9, 14, 11.9), (15.3, 14.6, 13.3), 'cu'),
            el((2, 1, 1), (14, 3, 3), 'haz', 'kerb'),
        ], 'grid_tank')
        # ---- power transformer: tank, radiator banks, 3 HV bushings, conservator, fans, gauge, nameplate
        tr = [el((1, 0, 2), (15, 1, 14), 'dark', 'skid'),
              el((3, 1, 3), (13, 14, 13), 'tank', 'tank'),
              el((2.5, 14, 2.5), (13.5, 15, 13.5), 'tank', 'cover')]
        for zs in (0, 1):
            z0 = 0.5 if zs == 0 else 13
            for k in range(6):
                tr.append(el((3.5 + k * 1.6, 2, z0), (4.5 + k * 1.6, 13, z0 + 2.5), 'fins'))
            tr.append(el((5, 3, z0 + (0 if zs == 0 else 2.2)), (11, 7, z0 + (0.3 if zs == 0 else 2.5)), 'dark', 'cooling fan'))
        tr += bushing(5, 8, 15, 7) + bushing(8, 8, 15, 7) + bushing(11, 8, 15, 7)
        tr += [el((4, 17, 4), (12, 20, 6), 'tank', 'conservator'), el((5, 15, 4.5), (6, 17, 5.5), 'dark'),
               el((12, 9, 2.8), (13, 11, 3), 'disp', 'oil gauge'), el((5, 4, 2.8), (9, 6, 3), 'plate', 'nameplate'),
               el((10, 4, 2.8), (12, 7, 3), 'warn'), el((7, 1, 2.6), (9, 2.4, 3), on)]
        model('grid_transformer_%d' % st, tr, 'grid_tank')
        # ---- traction transformer (25 kV single phase): taller, two big bushings
        tt = [el((1, 0, 2), (15, 1, 14), 'dark'), el((3, 1, 3), (13, 16, 13), 'tank'), el((2.5, 16, 2.5), (13.5, 17, 13.5), 'tank')]
        for zs in (0, 1):
            z0 = 0.5 if zs == 0 else 13
            for k in range(6):
                tt.append(el((3.5 + k * 1.6, 2, z0), (4.5 + k * 1.6, 15, z0 + 2.5), 'fins'))
        tt += bushing(6, 8, 17, 11) + bushing(10, 8, 17, 11)
        tt += [el((5, 4, 2.8), (9, 6, 3), 'plate'), el((10, 4, 2.8), (12, 7, 3), 'warn'), el((7, 1, 2.6), (9, 2.4, 3), on)]
        model('grid_traction_%d' % st, tt, 'grid_tank')
        # ---- rectifier: two cubicles in real relief (raised doors in frames, handles, slatted louvres, roof fans, bus duct)
        rect = [el((0.5, 0, 3.5), (15.5, 2, 12.5), 'haz', 'cable plinth')]
        for x0, x1 in ((1, 8), (8, 15)):
            rect += [el((x0, 2, 4), (x1, 22, 12), 'cab', 'cubicle'),
                     el((x0 + 0.5, 2.5, 3.4), (x1 - 0.5, 21.5, 4), 'cab', 'door (raised)'),
                     el((x0 + 0.3, 21.5, 3.6), (x1 - 0.3, 22, 4), 'dark', 'door head'),
                     el((x1 - 1.6, 11, 2.7), (x1 - 1.0, 14, 3.4), 'dark', 'handle'),
                     el((x1 - 1.8, 12.2, 2.5), (x1 - 0.8, 12.8, 2.7), 'dark', 'handle grip')]
            for k in range(5):                                   # stepped louvre slats you can see into
                y = 4 + k * 1.1
                rect.append(el((x0 + 1, y, 3.0), (x1 - 2.2, y + 0.5, 3.4), 'dark', {'origin': [x0 + 1, y, 3.2], 'axis': 'x', 'angle': -22.5}))
            rect += [el((x0 + 1, 3.6, 3.3), (x1 - 2.2, 9.6, 3.4), 'lv')]
        rect += [el((2, 14, 3.2), (5.5, 17, 3.4), 'disp'), el((9, 15, 3.2), (13, 19, 3.4), 'warn'),
                 el((6, 18, 3.1), (7, 19, 3.4), on), el((2, 19, 3.1), (3, 20, 3.4), on),
                 el((1.5, 22, 4.5), (7.5, 24, 11.5), 'dark', 'fan cowl'), el((8.5, 22, 4.5), (14.5, 24, 11.5), 'dark', 'fan cowl'),
                 el((2.5, 24, 5.5), (6.5, 24.4, 10.5), 'lv'), el((9.5, 24, 5.5), (13.5, 24.4, 10.5), 'lv'),
                 el((3.5, 24.4, 7.5), (5.5, 24.8, 8.5), 'galv', 'fan hub'), el((10.5, 24.4, 7.5), (12.5, 24.8, 8.5), 'galv', 'fan hub'),
                 el((0, 18, 12), (16, 20, 15), 'galv', 'bus duct'), el((0, 2, 12.5), (1, 18, 14.5), 'cable', 'riser'),
                 el((15, 2, 12.5), (16, 18, 14.5), 'cable', 'riser')]
        model('grid_rectifier_%d' % st, rect)
        # ---- meter: pedestal, rain hood, framed glass door over a real dial + display, conduit into the ground
        model('grid_meter_%d' % st, [
            el((5, 0, 5), (11, 0.6, 11), 'galv', 'base plate'), el((6.5, 0.6, 6.5), (9.5, 8, 9.5), 'galv'),
            el((7.3, 0, 9.5), (8.7, 9, 10.9), 'dark', 'conduit'),
            el((3.5, 8, 5), (12.5, 16, 11), 'cab'),
            el((3, 16, 4), (13, 16.8, 11.5), 'galv', 'rain hood'),
            el((3.8, 8.3, 4.4), (12.2, 8.9, 5), 'dark', 'door frame'), el((3.8, 15.1, 4.4), (12.2, 15.7, 5), 'dark'),
            el((3.8, 8.3, 4.4), (4.4, 15.7, 5), 'dark'), el((11.6, 8.3, 4.4), (12.2, 15.7, 5), 'dark'),
            el((4.6, 12, 4.8), (11.4, 15, 5), 'disp' if st else 'dark', 'display'),
            el((5, 9.2, 4.6), (7.6, 11.6, 4.9), 'plate', 'dial face'), el((6.1, 10.2, 4.4), (6.5, 11.4, 4.6), 'dark', 'needle'),
            el((8.5, 9.4, 4.7), (9.5, 10.4, 4.9), on), el((10, 9.4, 4.7), (11, 11, 4.9), 'warn'),
        ])
        # ---- battery container in relief: framed doors, ribs, handles, HVAC with fan grilles, cable glands
        bat = [el((0, 0, 0), (16, 1, 16), 'con'), el((0.5, 1, 1), (15.5, 18, 15), 'bat'), el((0.2, 18, 0.7), (15.8, 19, 15.3), 'cab', 'roof')]
        for x0 in (1, 5, 9, 12.5):                                   # four doors standing proud of the box
            x1 = x0 + 3.2 if x0 != 12.5 else 15
            bat += [el((x0, 2, 0.5), (x1, 17, 1), 'bat', 'door'), el((x0 + 0.2, 2.2, 0.3), (x1 - 0.2, 2.6, 0.5), 'dark'),
                    el((x1 - 0.9, 8.5, 0.0), (x1 - 0.4, 11, 0.5), 'dark', 'handle')]
        for z in (3, 6, 9, 12):                                       # side ribs
            bat += [el((0.1, 1.5, z), (0.5, 17.5, z + 0.8), 'cab'), el((15.5, 1.5, z), (15.9, 17.5, z + 0.8), 'cab')]
        bat += [el((3, 12, 0.1), (13, 13, 0.5), on), el((2, 4, 0.2), (5, 8, 0.5), 'warn'),
                el((3, 19, 3), (8, 22, 9), 'cab', 'HVAC'), el((9, 19, 3), (14, 22, 9), 'cab', 'HVAC'),
                el((3.5, 22, 3.5), (7.5, 22.3, 8.5), 'lv', 'fan grille'), el((9.5, 22, 3.5), (13.5, 22.3, 8.5), 'lv', 'fan grille'),
                el((5, 22.3, 5.5), (6, 22.6, 6.5), 'galv'), el((11, 22.3, 5.5), (12, 22.6, 6.5), 'galv'),
                el((2, 19, 11), (14, 20.5, 13.5), 'cable', 'cable tray')]
        for x in (3, 7, 11):                                          # cable glands at the bottom of the back
            bat.append(el((x, 1.5, 15), (x + 1.4, 2.9, 15.8), 'cu'))
        model('grid_battery_%d' % st, bat)
        # ---- SCADA desk: console, three screens, keyboard, chair-side panel
        model('grid_scada_%d' % st, [
            el((0, 0, 4), (16, 11, 13), 'cab', 'desk'), el((0, 11, 3), (16, 12, 14), 'dark', 'desk top'),
            el((1, 12, 9), (6, 17, 10), 'dark'), el((5.5, 12, 8.5), (10.5, 18, 9.5), 'dark'), el((10, 12, 9), (15, 17, 10), 'dark'),
            el((1.3, 12.4, 8.9), (5.7, 16.6, 9), 'scada' if st else 'dark'), el((5.8, 12.4, 8.4), (10.2, 17.6, 8.5), 'scada' if st else 'dark'),
            el((10.3, 12.4, 8.9), (14.7, 16.6, 9), 'scada' if st else 'dark'),
            el((4, 12, 4.5), (12, 12.4, 7), 'plate', 'keyboard'), el((13, 12, 5), (15, 12.6, 7), on),
        ])
        # ---- track feeder: post + riser cable + clamp arm reaching to the line (north = -z) + insulator
        model('grid_feeder_%d' % st, [
            el((5, 0, 5), (11, 1, 11), 'con'), el((6.5, 1, 6.5), (9.5, 22, 9.5), 'galv', 'post'),
            el((9.5, 1, 7.4), (10.7, 21, 8.6), 'cable', 'riser'),
            el((7, 20, -8), (9, 21.5, 9), 'galv', 'clamp arm'), el((7.4, 17, -8), (8.6, 20, -6.5), 'ins'),
            el((7, 14, -8.5), (9, 17, -6), 'cu', 'clamp'), el((5.5, 10, 5.4), (10.5, 14, 6.5), 'warn'),
            el((7.4, 15, 5.3), (8.6, 16.2, 5.5), on),
        ], 'ohle_galv')
    # ---- breaker: closed / open / tripped (handle position + lamps)
    for st, (handle_y, lamp_c, lamp_o) in enumerate(((16, 'lg', 'lo'), (9, 'lo', 'lr'), (12.5, 'la', 'la'))):
        model('grid_breaker_%d' % st, [
            el((1.5, 0, 3.5), (14.5, 1, 12.5), 'dark'),
            el((2, 1, 4), (14, 26, 12), 'cab', 'switchgear cubicle'),
            el((3, 12, 3.8), (13, 24, 4), 'sw', 'mimic diagram'),
            el((3, 4, 3.8), (8, 9, 4), 'warn'), el((9, 6, 3.8), (13, 8, 4), 'disp'),
            el((4, 24.5, 3.7), (5.5, 25.6, 4), lamp_c, 'closed lamp'), el((10.5, 24.5, 3.7), (12, 25.6, 4), lamp_o, 'open lamp'),
            el((7.4, handle_y - 3, 2.8), (8.6, handle_y, 3.8), 'dark', 'operating handle'), el((6.8, handle_y, 2.3), (9.2, handle_y + 1.2, 3.8), 'lr'),
            el((2, 26, 4.5), (14, 27, 11.5), 'dark', 'arc vent'),
        ])
    # ---- cable: centre + arms (multipart)
    # ---- power plants: the front panel (z = 0) carries the buttons BlockGrid.button() reads
    def panel(st):
        lamp_ = {0: 'lo', 1: 'lg', 2: 'lr'}[st]
        return [el((2, 4, -0.2), (14, 13, 0.6), 'cab', 'control panel'),
                el((3, 5, -0.35), (9, 8, -0.2), 'disp', 'display'),
                el((3, 9, -0.6), (5.5, 11, -0.2), 'bstart', 'START'), el((6, 9, -0.6), (8.5, 11, -0.2), 'bstop', 'STOP'),
                el((10.5, 10, -0.5), (12.5, 12, -0.2), 'bup', 'POWER UP'), el((10.5, 7, -0.5), (12.5, 9, -0.2), 'bdown', 'POWER DOWN'),
                el((10.7, 5, -0.5), (12.3, 6.4, -0.2), lamp_, 'status lamp'), el((2, 12, -0.4), (14, 12.6, -0.2), 'warn')]
    for st in (0, 1, 2):
        model('grid_diesel_%d' % st, [
            el((0, 0, 0), (16, 1, 16), 'dark', 'skid'),
            el((1, 1, 2), (15, 4, 15), 'eng', 'oil sump'), el((2, 4, 3), (14, 11, 14), 'eng', 'engine block'),
            el((3, 11, 4), (13, 13, 13), 'eng', 'rocker covers'), el((4, 13, 6), (12, 14, 11), 'dark', 'air filter'),
            el((1, 4, 14.5), (15, 13, 16), 'fins', 'radiator'), el((0.5, 1, 1), (2, 12, 2.5), 'galv', 'frame'), el((14, 1, 1), (15.5, 12, 2.5), 'galv', 'frame'),
            el((12, 13, 12), (13.6, 22, 13.6), 'dark', 'exhaust stack'), el((11.7, 22, 11.7), (13.9, 22.6, 13.9), 'galv', 'rain cap'),
            el((14.5, 2, 6), (16, 4, 8), 'cu', 'fuel inlet'), el((0, 2, 6), (1.5, 4, 8), 'cable', 'power out'),
            *panel(st)], 'grid_engine')
        model('grid_turbine_%d' % st, [
            el((0, 0, 0), (16, 1, 16), 'dark', 'skid'),
            el((1, 1, 3), (15, 10, 15), 'tank', 'turbine casing'), el((2, 10, 4), (14, 12, 14), 'tank', 'casing top'),
            el((6, 12, 6), (10, 16, 10), 'galv', 'steam chest'), el((7, 16, 7), (9, 18, 16), 'galv', 'steam pipe in'),
            el((14.5, 3, 7), (16, 6, 10), 'cu', 'steam inlet'), el((0, 3, 7), (1.5, 5, 9), 'cable', 'power out'),
            el((3, 1, 15), (13, 8, 16), 'fins', 'condenser'),
            *panel(st)], 'grid_tank')
        model('grid_solar_%d' % st, [
            el((0, 0, 0), (16, 1, 16), 'con', 'footing'),
            el((7, 1, 7), (9, 6, 9), 'galv', 'post'),
            el((0, 6, 0), (16, 7, 16), 'sol', 'panels', {'origin': [8, 6.5, 8], 'axis': 'x', 'angle': -22.5}),
            el((1, 5.5, 1), (15, 6, 15), 'galv', 'frame', {'origin': [8, 6.5, 8], 'axis': 'x', 'angle': -22.5}),
            el((12, 1, 2), (15, 4, 4), 'cab', 'inverter'), el((12.5, 3, 1.8), (13.5, 3.8, 2), {0: 'lo', 1: 'lg', 2: 'lr'}[st])], 'grid_solar_cells')
        # models may only reach 32 px (2 blocks) high: a stout tower with the rotor at the top
        wind = [el((5, 0, 5), (11, 1, 11), 'con', 'foundation'),
                el((6.5, 1, 6.5), (9.5, 20, 9.5), 'galv', 'tower'),
                el((5.5, 20, 4), (10.5, 23, 13), 'galv', 'nacelle'), el((7, 20.5, 2.8), (9, 22.5, 4), 'dark', 'hub')]
        for ang in (0, 45, -45):
            wind.append(el((7.6, 21.5, 2.4), (8.4, 31.5, 2.9), 'blade', 'blade up', {'origin': [8, 21.5, 2.6], 'axis': 'z', 'angle': ang}))
        wind.append(el((7.6, 11.5, 2.4), (8.4, 21.5, 2.9), 'blade', 'blade down'))
        wind.append(el((7.5, 1, 9.6), (8.5, 3, 10), {0: 'lo', 1: 'lg', 2: 'lr'}[st]))
        model('grid_wind_%d' % st, wind, 'grid_blade')

    # ---- realism parts (her ask 2026-10-06: "hook all this stuff up realistically, capacitors, whatever")
    for st in (0, 1):
        on = 'lg' if st else 'lo'
        cap = [el((0.5, 0, 2), (15.5, 1, 14), 'dark', 'frame'),
               el((1, 1, 3), (2, 20, 4), 'galv'), el((14, 1, 3), (15, 20, 4), 'galv'),
               el((1, 1, 12), (2, 20, 13), 'galv'), el((14, 1, 12), (15, 20, 13), 'galv'),
               el((1, 19, 3), (15, 20, 13), 'galv', 'rack top')]
        for i, x in enumerate((3, 6.5, 10)):
            for z in (4.5, 8.5):
                cap += [el((x, 2, z), (x + 3, 12, z + 3), 'cab', 'capacitor can'),
                        el((x + 1, 12, z + 1), (x + 2, 15, z + 2), 'ins'), el((x + 1.2, 15, z + 1.2), (x + 1.8, 16, z + 1.8), 'cu')]
        cap += [el((2, 16, 9.4), (14, 16.6, 10), 'cu', 'busbar'), el((2, 16, 5.4), (14, 16.6, 6), 'cu', 'busbar'),
                el((12.5, 1, 1.6), (15, 5, 2.5), 'warn'), el((2, 3, 1.8), (3.2, 4.2, 2.1), on)]
        model('grid_capacitor_%d' % st, cap, 'grid_cabinet')
        model('grid_earthing_%d' % st, [
            el((3, 0, 3), (13, 0.6, 13), 'cu', 'earth mat'),
            el((7, 0.6, 7), (9, 14, 9), 'galv', 'post'),
            el((6, 14, 6), (10, 15, 10), 'ins'),
            el((7.6, 15, 4), (8.4, 15.8, 12), 'cu', 'earth blade'),
            el((7.3, 0.6, 9.5), (8.7, 12, 10.5), 'cu', 'earth tail'),
            el((5, 6, 6.7), (11, 9, 7), 'warn'), el((7.5, 10, 6.6), (8.5, 11, 6.8), on),
        ], 'grid_copper')
        model('grid_relay_%d' % st, [
            el((2, 0, 4), (14, 1, 12), 'dark'),
            el((3, 1, 5), (13, 15, 11), 'cab', 'relay panel'),
            el((4, 9, 4.8), (12, 13.5, 5), 'disp', 'relay display'),
            el((4, 6, 4.8), (5.5, 7.5, 5), on), el((6.5, 6, 4.8), (8, 7.5, 5), 'la'), el((9, 6, 4.8), (10.5, 7.5, 5), 'lo'),
            el((4, 2, 4.8), (12, 4.5, 5), 'plate', 'test block'),
        ])
        model('grid_arrester_%d' % st, [
            el((4, 0, 4), (12, 1, 12), 'con'),
            el((7, 1, 7), (9, 6, 9), 'galv', 'stand'),
            *bushing(8, 8, 6, 16),
            el((7.5, 24.2, 7.5), (8.5, 26, 8.5), 'cu'),
            el((8.5, 2, 7.6), (11, 3, 8.4), 'cu', 'earth lead'), el((10, 1, 7), (11.5, 3, 9), 'disp', 'surge counter'),
        ], 'ohle_insulator')
    # disconnector: closed / open (blade up)
    for st in (0, 1):
        blade_ = (el((2.5, 18, 7.5), (13.5, 19, 8.5), 'cu', 'blade closed') if st == 0 else
                  el((2.5, 18, 7.5), (13.5, 19, 8.5), 'cu', 'blade open', {'origin': [3, 18.5, 8], 'axis': 'z', 'angle': 45}))
        model('grid_isolator_%d' % st, [
            el((0, 0, 5), (16, 1, 11), 'con'),
            el((2, 1, 7), (4, 8, 9), 'galv'), el((12, 1, 7), (14, 8, 9), 'galv'),
            el((1.5, 8, 6.5), (14.5, 9, 9.5), 'galv', 'base frame'),
            *bushing(3, 8, 9, 8, cap=False), *bushing(13, 8, 9, 8, cap=False),
            el((2, 17, 7), (4, 18, 9), 'cu'), el((12, 17, 7), (14, 18, 9), 'cu', 'jaw'),
            blade_,
            el((7, 1, 9), (9, 7, 10), 'dark', 'operating rod'), el((6, 4, 9.8), (10, 6, 10.2), 'lr' if st else 'lg'),
        ], 'ohle_galv')

    model('grid_cable_core', [el((6, 0, 6), (10, 3, 10), 'cable'), el((5.6, 0, 5.6), (10.4, 0.6, 10.4), 'dark', 'cleat')], 'grid_cable')
    model('grid_cable_side', [el((6.5, 0.4, 0), (9.5, 2.6, 6), 'cable'), el((6.2, 0, 2), (9.8, 0.4, 3), 'dark')], 'grid_cable')
    model('grid_cable_up', [el((6.5, 3, 6.5), (9.5, 16, 9.5), 'cable')], 'grid_cable')

    # blockstates
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for kind in ('intake', 'transformer', 'traction', 'rectifier', 'breaker', 'feeder', 'meter', 'battery', 'scada', 'diesel', 'turbine', 'solar', 'wind',
                 'capacitor', 'earthing', 'relay', 'arrester', 'isolator'):
        v = {}
        three = kind in ('breaker', 'diesel', 'turbine', 'solar', 'wind')
        for f, y in rot.items():
            for st in (0, 1, 2):
                mdl = '%s:grid_%s_%d' % (MOD, kind, st if three else min(st, 1))
                if kind == 'isolator':
                    mdl = '%s:grid_isolator_%d' % (MOD, 1 if st else 0)     # state 0 closed, 1/2 open
                v['facing=%s,state=%d' % (f, st)] = {'model': mdl, 'y': y} if y else {'model': mdl}
        json.dump({'variants': v}, open(os.path.join(ROOT, 'blockstates/grid_%s.json' % kind), 'w'), indent=1)
        json.dump({'parent': '%s:block/grid_%s_%d' % (MOD, kind, 0 if kind in ('breaker', 'isolator') else 1)}, open(os.path.join(ROOT, 'models/item/grid_%s.json' % kind), 'w'))
    parts = [{'apply': {'model': MOD + ':grid_cable_core'}}]
    for d, y in rot.items():
        parts.append({'when': {d: 'true'}, 'apply': {'model': MOD + ':grid_cable_side', 'y': y} if y else {'model': MOD + ':grid_cable_side'}})
    parts.append({'when': {'up': 'true'}, 'apply': {'model': MOD + ':grid_cable_up'}})
    json.dump({'multipart': parts}, open(os.path.join(ROOT, 'blockstates/grid_cable.json'), 'w'), indent=1)
    json.dump({'parent': MOD + ':block/grid_cable_core'}, open(os.path.join(ROOT, 'models/item/grid_cable.json'), 'w'))
    print('ok')


if __name__ == '__main__':
    main()

if __name__ == "__main__":
    import subprocess, sys as _s
    subprocess.run([_s.executable, __file__.replace("gen_grid_models.py", "fix_model_uvs.py")])

if __name__ == "__main__":
    import subprocess as _sp, sys as _s2
    _sp.run([_s2.executable, __file__.replace("gen_grid_models.py", "add_grid_ports.py")])
