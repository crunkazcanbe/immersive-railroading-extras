#!/usr/bin/env python3
"""IR Extras industrial parts: 16x16 icons + item models (part_<id>)."""
import json, os, math, random, shutil
from PIL import Image, ImageDraw
random.seed(11)
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/irextras')
T = os.path.join(R, 'textures/items'); os.makedirs(T, exist_ok=True)
PI = os.path.expanduser('~/mc-mods/prideindustry/src/main/resources/assets/prideindustry/textures/items')
for a, b in [('reinforced_plate',)*2, ('machine_frame',)*2, ('control_circuit',)*2, ('motor_core',)*2, ('hydraulic_cylinder',)*2, ('gearbox',)*2, ('heating_coil',)*2]:
    shutil.copy(os.path.join(PI, 'component_' + a + '.png'), os.path.join(T, 'part_' + b + '.png'))
def new(): im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); return im, ImageDraw.Draw(im)
STEEL, DARK, GOLD, COPPER = (150, 156, 164, 255), (70, 74, 80, 255), (226, 182, 40, 255), (184, 115, 51, 255)
def save(n, im): im.save(os.path.join(T, 'part_' + n + '.png'))
im, d = new()                                                        # cooling fan
d.ellipse((1, 1, 14, 14), fill=DARK)
for a in range(0, 360, 72):
    x, y = 7.5 + 5 * math.cos(math.radians(a)), 7.5 + 5 * math.sin(math.radians(a)); d.line((7.5, 7.5, x, y), fill=STEEL, width=2)
d.ellipse((6, 6, 9, 9), fill=GOLD); d.rectangle((0, 0, 15, 15), outline=(110, 116, 124, 255)); save('cooling_fan', im)
im, d = new()                                                        # drive belt
d.ellipse((1, 3, 14, 12), outline=(40, 40, 44, 255), width=2); d.ellipse((3, 5, 12, 10), outline=(80, 60, 40, 255)); save('drive_belt', im)
im, d = new()                                                        # ball bearing
d.ellipse((1, 1, 14, 14), fill=STEEL, outline=DARK); d.ellipse((4, 4, 11, 11), fill=DARK); d.ellipse((6, 6, 9, 9), fill=(20, 20, 20, 255))
for a in range(0, 360, 45): x, y = 7.5 + 4.6 * math.cos(math.radians(a)), 7.5 + 4.6 * math.sin(math.radians(a)); d.point((x, y), fill=(230, 232, 236, 255))
save('ball_bearing', im)
im, d = new(); d.line((2, 13, 13, 2), fill=STEEL, width=3); d.line((3, 13, 13, 3), fill=(200, 205, 212, 255)); d.rectangle((1, 12, 3, 14), fill=GOLD); save('steel_shaft', im)
im, d = new()                                                        # spring
for i, y in enumerate(range(1, 15, 2)): d.line((4, y, 11, y + 1), fill=(200, 205, 212, 255) if i % 2 else STEEL)
save('spring', im)
im, d = new(); d.ellipse((2, 2, 13, 13), outline=(30, 30, 32, 255), width=3); save('rubber_seal', im)
im, d = new()                                                        # copper winding (spool)
d.rectangle((2, 2, 13, 3), fill=DARK); d.rectangle((2, 12, 13, 13), fill=DARK)
for y in range(4, 12): d.line((3, y, 12, y), fill=COPPER if y % 2 else (214, 150, 80, 255))
save('copper_winding', im)
im, d = new()                                                        # transformer core (laminations)
for y in range(2, 14): d.line((2, y, 13, y), fill=(120, 126, 134, 255) if y % 2 else (95, 100, 108, 255))
d.rectangle((5, 5, 10, 10), fill=(0, 0, 0, 0)); save('transformer_core', im)
im, d = new()                                                        # insulator (ceramic sheds)
for i, y in enumerate((2, 6, 10)): d.ellipse((2, y, 13, y + 3), fill=(236, 232, 220, 255), outline=(180, 170, 150, 255))
d.rectangle((6, 1, 9, 14), fill=(220, 214, 200, 255)); save('insulator', im)
im, d = new(); d.rectangle((4, 2, 11, 13), fill=(40, 70, 160, 255), outline=(20, 40, 100, 255)); d.rectangle((5, 3, 10, 4), fill=STEEL)
d.line((6, 14, 6, 15), fill=STEEL); d.line((9, 14, 9, 15), fill=STEEL); save('capacitor_cell', im)
im, d = new(); d.rectangle((3, 6, 12, 9), fill=(30, 30, 34, 255)); d.rectangle((10, 6, 11, 9), fill=(230, 230, 230, 255)); d.line((0, 7, 3, 7), fill=STEEL); d.line((12, 8, 15, 8), fill=STEEL); save('rectifier_diode', im)
im, d = new(); d.rectangle((2, 4, 13, 13), fill=(60, 64, 70, 255)); d.rectangle((4, 2, 6, 4), fill=GOLD); d.rectangle((9, 2, 11, 4), fill=GOLD); d.rectangle((4, 7, 11, 10), fill=COPPER); save('relay_contactor', im)
im, d = new()                                                        # heat sink fins
d.rectangle((1, 11, 14, 14), fill=STEEL)
for x in range(2, 14, 3): d.rectangle((x, 2, x + 1, 11), fill=(200, 205, 212, 255))
save('heat_sink', im)
im, d = new(); d.rectangle((1, 3, 14, 12), fill=(30, 30, 34, 255)); d.rectangle((2, 4, 13, 11), fill=(10, 30, 14, 255))
d.line((3, 6, 9, 6), fill=(80, 255, 112, 255)); d.line((3, 9, 11, 9), fill=(80, 255, 112, 255)); save('display_screen', im)
im, d = new(); d.ellipse((1, 1, 14, 14), fill=(239, 233, 216, 255), outline=(30, 30, 30, 255)); d.line((7, 11, 11, 4), fill=(200, 30, 30, 255)); d.ellipse((6, 10, 8, 12), fill=(30, 30, 30, 255)); save('meter_movement', im)
im, d = new(); d.rectangle((4, 2, 11, 14), fill=(40, 120, 60, 255), outline=(20, 70, 30, 255)); d.rectangle((6, 0, 9, 2), fill=STEEL); d.rectangle((5, 5, 10, 6), fill=(230, 230, 230, 255)); save('battery_cell', im)
im, d = new(); d.rectangle((1, 1, 14, 14), fill=(30, 40, 90, 255), outline=(150, 156, 164, 255))
for k in (5, 10): d.line((1, k, 14, k), fill=(150, 160, 200, 255)); d.line((k, 1, k, 14), fill=(150, 160, 200, 255))
save('solar_cell', im)
im, d = new(); d.polygon([(2, 14), (5, 13), (14, 2), (12, 1), (3, 10)], fill=(200, 205, 212, 255), outline=DARK); save('turbine_blade', im)
im, d = new(); d.line((7, 2, 7, 14), fill=STEEL); d.line((4, 5, 10, 5), fill=STEEL); d.line((5, 8, 9, 8), fill=STEEL); d.ellipse((6, 0, 8, 2), fill=(255, 60, 60, 255)); d.rectangle((5, 13, 9, 15), fill=DARK); save('antenna', im)
import sys; sys.path.insert(0, os.path.join(R, '..', '..', '..', '..', '..'))
ids = ['reinforced_plate', 'machine_frame', 'control_circuit', 'motor_core', 'hydraulic_cylinder', 'gearbox', 'heating_coil', 'cooling_fan', 'drive_belt', 'ball_bearing', 'steel_shaft', 'spring', 'rubber_seal', 'copper_winding', 'transformer_core', 'insulator', 'capacitor_cell', 'rectifier_diode', 'relay_contactor', 'heat_sink', 'display_screen', 'meter_movement', 'battery_cell', 'solar_cell', 'turbine_blade', 'antenna']
for i in ids: json.dump({"parent": "item/generated", "textures": {"layer0": "irextras:items/part_" + i}}, open(os.path.join(R, 'models/item/part_' + i + '.json'), 'w'))
print(len(ids), 'parts')
