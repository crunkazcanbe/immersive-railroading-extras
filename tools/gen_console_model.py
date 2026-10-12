#!/usr/bin/env python3
"""Control Center console model (her ask 2026-10-09). Writes the block model JSON (opens in Blockbench: File > Open
Model) and its textures. The master block is the middle of the desk; elements run x -16..32 (three blocks) and
y 0..32 (two tall), front toward -z (north). The desk slope / screen rectangle must match ConsoleGeometry.java."""
import json, math, os, random
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'irextras')
TEX = os.path.join(ROOT, 'textures', 'blocks')
MOD = os.path.join(ROOT, 'models', 'block')
random.seed(7)

def noise_fill(img, base, amp, grain=1):
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            n = random.randint(-amp, amp)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)

def edge(img, dark, light):
    d = ImageDraw.Draw(img)
    w, h = img.size
    d.line([(0, 0), (w - 1, 0)], fill=light); d.line([(0, 0), (0, h - 1)], fill=light)
    d.line([(0, h - 1), (w - 1, h - 1)], fill=dark); d.line([(w - 1, 0), (w - 1, h - 1)], fill=dark)

def tex_metal():
    im = Image.new('RGBA', (32, 32)); noise_fill(im, (197, 199, 196), 4)
    edge(im, (150, 152, 150, 255), (222, 224, 221, 255)); return im

def tex_dark():
    im = Image.new('RGBA', (32, 32)); noise_fill(im, (46, 50, 54), 3)
    edge(im, (28, 30, 33, 255), (70, 74, 78, 255)); return im

def tex_door():
    im = tex_metal(); d = ImageDraw.Draw(im)
    d.rectangle([2, 2, 29, 29], outline=(160, 162, 160, 255))           # panel line
    d.rectangle([3, 3, 28, 28], outline=(215, 217, 214, 255))
    d.rectangle([12, 24, 19, 26], fill=(60, 64, 68, 255))               # rating plate
    return im

def tex_vent():
    im = Image.new('RGBA', (32, 32)); noise_fill(im, (40, 43, 46), 2); d = ImageDraw.Draw(im)
    for y in range(3, 30, 4):
        d.rectangle([2, y, 29, y + 1], fill=(12, 13, 14, 255))
    return im

def tex_plinth():
    im = Image.new('RGBA', (32, 32)); noise_fill(im, (22, 23, 24), 3); return im

def tex_screen():
    im = Image.new('RGBA', (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            g = 8 + int(10 * max(0, 1 - ((x - 8) ** 2 + (y - 6) ** 2) / 300.0))   # a soft reflection, top left
            px[x, y] = (g, g + 2, g + 5, 255)
    return im

def tex_term():
    im = tex_dark(); d = ImageDraw.Draw(im)
    for i, col in enumerate([(200, 40, 40), (40, 40, 40), (230, 200, 40), (230, 200, 40)]):   # L N +12 -12 terminals
        d.rectangle([3 + i * 7, 12, 7 + i * 7, 18], fill=col + (255,))
    d.rectangle([2, 22, 29, 27], fill=(240, 200, 30, 255))                                       # warning label
    return im

TEXTURES = {'console_metal': tex_metal, 'console_dark': tex_dark, 'console_door': tex_door, 'console_vent': tex_vent,
            'console_plinth': tex_plinth, 'console_screen': tex_screen, 'console_term': tex_term}

els = []
def box(name, a, b, tex, rot=None, faces=None):
    f = {}
    for side in faces or ['north', 'south', 'east', 'west', 'up', 'down']:
        f[side] = {'uv': [0, 0, 16, 16], 'texture': '#' + tex}
    e = {'name': name, 'from': [round(v, 3) for v in a], 'to': [round(v, 3) for v in b], 'faces': f}
    if rot: e['rotation'] = rot
    els.append(e)

# ---- the cabinet ----
box('plinth', [-15.5, 0, 2.6], [31.5, 1.5, 15.6], 'plinth')
box('cabinet', [-16, 1.5, 2], [32, 11, 16], 'metal')
for i, (x0, x1) in enumerate([(-15.2, -0.8), (0.8, 15.2), (16.8, 31.2)]):
    box(f'door{i}', [x0, 2.4, 1.55], [x1, 10.4, 2], 'door')
    hx = x0 + 1.2 if i == 2 else x1 - 1.8                       # handles toward the middle of the desk
    box(f'handle{i}', [hx, 5.2, 0.9], [hx + 0.6, 7.8, 1.55], 'dark')
    box(f'vent{i}', [x0 + 1.5, 2.9, 1.45], [x1 - 1.5, 4.1, 1.55], 'vent')
# ---- the desk ----
box('armrest', [-16, 10.2, 0], [32, 11.7, 1.6], 'dark')
box('desk_top', [-16, 10.6, 1], [32, 11.5, 13.6], 'dark',
    rot={'origin': [8, 11.5, 1], 'axis': 'x', 'angle': -22.5})
box('shelf', [-16, 15.3, 12.2], [32, 16.3, 16], 'metal')
box('end_left', [31.2, 1.5, 1.2], [32, 16.3, 16], 'dark')
box('end_right', [-16, 1.5, 1.2], [-15.2, 16.3, 16], 'dark')
# ---- the screen ----
box('screen_housing', [-15.4, 16.3, 12.0], [31.4, 31.9, 15.4], 'dark')
box('screen_glass', [-14.8, 17.2, 11.9], [30.8, 30.8, 12.0], 'screen', faces=['north'])
box('bezel_top', [-15.4, 30.8, 11.6], [31.4, 31.9, 12.0], 'dark')
box('bezel_bottom', [-15.4, 16.3, 11.6], [31.4, 17.2, 12.0], 'dark')
box('bezel_left', [30.8, 17.2, 11.6], [31.4, 30.8, 12.0], 'dark')
box('bezel_right', [-15.4, 17.2, 11.6], [-14.8, 30.8, 12.0], 'dark')
box('speaker_l', [26.5, 16.45, 11.5], [30.2, 17.05, 11.6], 'vent')
box('speaker_r', [-14.2, 16.45, 11.5], [-10.5, 17.05, 11.6], 'vent')
box('housing_back_rib', [-12, 18, 15.4], [28, 30, 15.9], 'metal')
# ---- services at the back ----
box('cable_duct', [6, 1.5, 15.4], [10, 15.3, 16], 'dark')
box('terminal_box', [11, 3, 15.2], [15.5, 8, 16], 'term')

model = {
    '__comment': 'Control Center console - generated by tools/gen_console_model.py (opens in Blockbench). 3 wide x 2 tall from the middle desk block.',
    'parent': 'block/block',
    'textures': {'particle': 'irextras:blocks/console_metal', **{k.replace('console_', ''): f'irextras:blocks/{k}' for k in TEXTURES}},
    'elements': els,
    'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -2, 0], 'scale': [0.3, 0.3, 0.3]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.2, 0.2, 0.2]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.2, 0.2, 0.2]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.2, 0.2, 0.2]},
    },
}

for k, fn in TEXTURES.items():
    fn().save(os.path.join(TEX, k + '.png'))
with open(os.path.join(MOD, 'dispatcher_board.json'), 'w') as f:
    json.dump(model, f, indent=1)
with open(os.path.join(MOD, 'console_part.json'), 'w') as f:
    json.dump({'textures': {'particle': 'irextras:blocks/console_metal'}, 'elements': []}, f)
bs = os.path.join(ROOT, 'blockstates', 'console_part.json')
with open(bs, 'w') as f:
    json.dump({'variants': {'normal': {'model': 'irextras:console_part'}}}, f)
print(len(els), 'elements')
