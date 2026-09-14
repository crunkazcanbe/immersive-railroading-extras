"""Convert a Blockbench .bbmodel (java_block, per-face UVs, one texture) into a Minecraft
1.12 block model JSON, and write its texture PNG.

  python3 bb2java.py model.bbmodel <texture id e.g. irextras:blocks/ticket_machine> out.json [--item gui_scale]

Java block UVs are 0-16 whatever the texture resolution, so every UV is scaled by 16/width.
Rotations must be one axis at 0 / +-22.5 / +-45 (the only angles 1.12 accepts).
"""
import base64, io, json, sys
from PIL import Image

src, tex_id, out = sys.argv[1], sys.argv[2], sys.argv[3]
gui = float(sys.argv[5]) if len(sys.argv) > 5 and sys.argv[4] == '--item' else 0.625
m = json.load(open(src))
W, H = m['resolution']['width'], m['resolution']['height']
sx, sy = 16.0 / W, 16.0 / H

elements = []
for e in m['elements']:
    if e.get('type', 'cube') != 'cube' or e.get('visibility') is False:
        continue
    el = {'name': e['name'], 'from': [round(v, 4) for v in e['from']], 'to': [round(v, 4) for v in e['to']]}
    rot = e.get('rotation') or [0, 0, 0]
    axes = [(i, a) for i, a in enumerate(rot) if abs(a) > 1e-6]
    if len(axes) > 1:
        raise SystemExit(f"{e['name']}: rotation on more than one axis")
    if axes:
        i, a = axes[0]
        if a not in (-45, -22.5, 22.5, 45):
            raise SystemExit(f"{e['name']}: angle {a} not allowed in 1.12")
        el['rotation'] = {'origin': e.get('origin', [8, 8, 8]), 'axis': 'xyz'[i], 'angle': a}
    faces = {}
    for d, fc in e['faces'].items():
        if fc.get('texture') is None:
            continue
        u0, v0, u1, v1 = fc['uv']
        face = {'uv': [round(u0 * sx, 4), round(v0 * sy, 4), round(u1 * sx, 4), round(v1 * sy, 4)], 'texture': '#0'}
        if fc.get('rotation'):
            face['rotation'] = fc['rotation']
        faces[d] = face
    el['faces'] = faces
    for v in el['from'] + el['to']:
        if v < -16 or v > 32:
            raise SystemExit(f"{e['name']}: coordinate {v} outside -16..32")
    elements.append(el)

model = {
    'credit': 'Immersive Railroading Extras — made in Blockbench',
    'ambientocclusion': False,
    'textures': {'0': tex_id, 'particle': tex_id},
    'elements': elements,
    'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [gui, gui, gui]},
        'ground': {'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.4, 0.4, 0.4]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.4, 0.4, 0.4]},
    },
}
json.dump(model, open(out, 'w'), indent=1)
if len(sys.argv) > 6:
    png = sys.argv[6]
    s = m['textures'][0]['source'].split(',', 1)[1]
    Image.open(io.BytesIO(base64.b64decode(s))).save(png)
print(f"{out}: {len(elements)} elements, {sum(len(x['faces']) for x in elements)} faces")
