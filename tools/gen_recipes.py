#!/usr/bin/env python3
"""Expensive, parts-based recipes for IR Extras power gear, instruments, tools and wiring (her ask 2026-10-07:
"I want them to be expensive for all the recipes" + "add the parts ... fans, belts"). Parts = irextras:part@N.
Only writes the files it owns; the older station / signal recipes are left alone."""
import json, os
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/irextras/recipes')
PARTS = ['reinforced_plate', 'machine_frame', 'control_circuit', 'motor_core', 'hydraulic_cylinder', 'gearbox', 'heating_coil', 'cooling_fan',
         'drive_belt', 'ball_bearing', 'steel_shaft', 'spring', 'rubber_seal', 'copper_winding', 'transformer_core', 'insulator',
         'capacitor_cell', 'rectifier_diode', 'relay_contactor', 'heat_sink', 'display_screen', 'meter_movement', 'battery_cell',
         'solar_cell', 'turbine_blade', 'antenna']
def part(n): return {"item": "irextras:part", "data": PARTS.index(n)}
def ing(v):
    if isinstance(v, dict): return v
    if v.startswith('#'): return {"type": "forge:ore_dict", "ore": v[1:]}
    if v.startswith('part:'): return part(v[5:])
    if '@' in v: i, m = v.split('@'); return {"item": i, "data": int(m)}
    return {"item": v}
def out(item, count=1):
    if item.startswith('part:'): return {"item": "irextras:part", "data": PARTS.index(item[5:]), "count": count}
    return {"item": item, "count": count}
n = 0
def shaped(name, pat, key, result, count=1):
    global n
    json.dump({"type": "forge:ore_shaped", "pattern": pat, "key": {k: ing(v) for k, v in key.items()}, "result": out(result, count)}, open(os.path.join(R, name + '.json'), 'w'), indent=1); n += 1
def shapeless(name, items, result, count=1):
    global n
    json.dump({"type": "forge:ore_shapeless", "ingredients": [ing(v) for v in items], "result": out(result, count)}, open(os.path.join(R, name + '.json'), 'w'), indent=1); n += 1
P, F, C, M = 'part:reinforced_plate', 'part:machine_frame', 'part:control_circuit', 'part:motor_core'
# ---- the parts tree ----
shaped('part_reinforced_plate', ["IOI", "OBO", "IOI"], {'I': '#ingotIron', 'O': 'minecraft:obsidian', 'B': '#blockIron'}, P, 2)
shaped('part_machine_frame', ["PBP", "B B", "PBP"], {'P': P, 'B': 'minecraft:iron_bars'}, F)
shaped('part_steel_shaft', ["  I", " B ", "I  "], {'I': '#ingotIron', 'B': '#blockIron'}, 'part:steel_shaft', 2)
shaped('part_spring', ["I  ", " I ", "  I"], {'I': '#ingotIron'}, 'part:spring', 4)
shaped('part_rubber_seal', ["SLS"], {'S': '#slimeball', 'L': 'minecraft:leather'}, 'part:rubber_seal', 4)
shaped('part_ball_bearing', ["NIN", "I I", "NIN"], {'N': '#nuggetIron', 'I': '#ingotIron'}, 'part:ball_bearing', 4)
shaped('part_copper_winding', ["GRG", "RIR", "GRG"], {'G': '#ingotGold', 'R': '#dustRedstone', 'I': '#ingotIron'}, 'part:copper_winding', 2)
shaped('part_transformer_core', ["PIP", "I I", "PIP"], {'P': P, 'I': '#ingotIron'}, 'part:transformer_core')
shaped('part_insulator', ["BQB", "BQB"], {'B': 'minecraft:brick', 'Q': '#gemQuartz'}, 'part:insulator', 2)
shaped('part_capacitor_cell', ["IXI", "GLG", "IXI"], {'I': '#ingotIron', 'X': 'part:insulator', 'G': '#ingotGold', 'L': '#blockLapis'}, 'part:capacitor_cell')
shaped('part_rectifier_diode', ["QRQ", "GXG"], {'Q': '#gemQuartz', 'R': '#dustRedstone', 'G': '#ingotGold', 'X': 'minecraft:repeater'}, 'part:rectifier_diode', 2)
shaped('part_relay_contactor', ["GWG", "PSP"], {'G': '#ingotGold', 'W': 'part:copper_winding', 'P': P, 'S': 'part:spring'}, 'part:relay_contactor')
shaped('part_heat_sink', ["IXI", "IXI", "PPP"], {'I': '#ingotIron', 'X': 'minecraft:iron_bars', 'P': P}, 'part:heat_sink')
shaped('part_display_screen', ["GGG", "LCL"], {'G': '#paneGlass', 'L': '#dustGlowstone', 'C': C}, 'part:display_screen', 2)
shaped('part_meter_movement', [" X ", "NWN", " S "], {'X': 'minecraft:clock', 'N': '#nuggetGold', 'W': 'part:copper_winding', 'S': 'part:spring'}, 'part:meter_movement', 2)
shaped('part_battery_cell', ["PRP", "PLP", "PRP"], {'P': P, 'R': '#blockRedstone', 'L': '#blockLapis'}, 'part:battery_cell')
shaped('part_solar_cell', ["GGG", "LDL", "PPP"], {'G': '#paneGlass', 'L': '#gemLapis', 'D': 'minecraft:daylight_detector', 'P': P}, 'part:solar_cell', 2)
shaped('part_turbine_blade', ["  P", " P ", "I  "], {'P': P, 'I': '#ingotIron'}, 'part:turbine_blade')
shaped('part_antenna', [" E ", " I ", "III"], {'E': 'minecraft:ender_pearl', 'I': '#ingotIron'}, 'part:antenna')
shaped('part_control_circuit', ["RQR", "GXG", "RQR"], {'R': '#blockRedstone', 'Q': '#gemQuartz', 'G': '#ingotGold', 'X': 'minecraft:comparator'}, C)
shaped('part_motor_core', ["PWP", "WSW", "PBP"], {'P': P, 'W': 'part:copper_winding', 'S': 'part:steel_shaft', 'B': 'part:ball_bearing'}, M)
shaped('part_hydraulic_cylinder', ["PIP", "PSP", "PEP"], {'P': P, 'I': '#ingotIron', 'S': 'minecraft:sticky_piston', 'E': 'part:rubber_seal'}, 'part:hydraulic_cylinder')
shaped('part_gearbox', ["PSP", "BDB", "PSP"], {'P': P, 'S': 'part:steel_shaft', 'B': 'part:ball_bearing', 'D': '#gemDiamond'}, 'part:gearbox')
shaped('part_heating_coil', ["GBG", "XRX", "GBG"], {'G': '#blockGold', 'B': 'minecraft:blaze_rod', 'X': 'part:insulator', 'R': '#blockRedstone'}, 'part:heating_coil')
shaped('part_cooling_fan', [" I ", "IMI", " I "], {'I': '#ingotIron', 'M': M}, 'part:cooling_fan')
shaped('part_drive_belt', ["LLL", "LSL", "LLL"], {'L': 'minecraft:leather', 'S': 'part:rubber_seal'}, 'part:drive_belt', 2)
# ---- power grid machines ----
X, W, K, A, V = 'part:insulator', 'part:copper_winding', 'part:transformer_core', 'part:cooling_fan', 'part:display_screen'
g = lambda k: 'irextras:grid_' + k
shaped('grid_intake', ["PCP", "XFX", "PRP"], {'P': P, 'C': C, 'X': X, 'F': F, 'R': '#blockRedstone'}, g('intake'))
shaped('grid_transformer', ["XAX", "WKW", "PFP"], {'X': X, 'A': A, 'W': W, 'K': K, 'P': P, 'F': F}, g('transformer'))
shaped('grid_traction', ["XCX", "WKW", "PFP"], {'X': X, 'C': C, 'W': W, 'K': K, 'P': P, 'F': F}, g('traction'))
shaped('grid_rectifier', ["PCP", "DFD", "PHP"], {'P': P, 'C': C, 'D': 'part:rectifier_diode', 'F': F, 'H': 'part:heat_sink'}, g('rectifier'))
shaped('grid_inverter', ["PCP", "DFD", "PEP"], {'P': P, 'C': C, 'D': 'part:rectifier_diode', 'F': F, 'E': 'part:capacitor_cell'}, g('inverter'))
shaped('grid_dcdc', ["PCP", "EFE", "DHD"], {'P': P, 'C': C, 'E': 'part:capacitor_cell', 'F': F, 'D': 'part:rectifier_diode', 'H': 'part:heat_sink'}, g('dcdc'))
shaped('grid_motor', ["PMP", "BFB", "PSP"], {'P': P, 'M': M, 'B': 'part:ball_bearing', 'F': F, 'S': 'part:steel_shaft'}, g('motor'))
shaped('grid_breaker', ["PCP", "TFT", "SXS"], {'P': P, 'C': C, 'T': 'part:relay_contactor', 'F': F, 'S': 'part:spring', 'X': X}, g('breaker'))
shaped('grid_feeder', ["XPX", "WFW", "XCX"], {'X': X, 'P': P, 'W': W, 'F': F, 'C': C}, g('feeder'))
shaped('grid_meter', ["PVP", "CFC"], {'P': P, 'V': V, 'C': C, 'F': F}, g('meter'))
shaped('grid_battery', ["YYY", "CFC", "YYY"], {'Y': 'part:battery_cell', 'C': C, 'F': F}, g('battery'))
shaped('grid_scada', ["VVV", "CFC", "PDP"], {'V': V, 'C': C, 'F': F, 'P': P, 'D': '#gemDiamond'}, g('scada'))
shaped('grid_cable', ["LLL", "WWW", "LLL"], {'L': 'minecraft:leather', 'W': W}, g('cable'), 8)
shaped('grid_diesel', ["PAP", "MFH", "GEG"], {'P': P, 'A': A, 'M': M, 'F': F, 'H': 'part:hydraulic_cylinder', 'G': 'part:gearbox', 'E': 'part:rubber_seal'}, g('diesel'))
shaped('grid_turbine', ["BBB", "GFM", "PAP"], {'B': 'part:turbine_blade', 'G': 'part:gearbox', 'F': F, 'M': M, 'P': P, 'A': A}, g('turbine'))
shaped('grid_solar', ["SSS", "CFC", "PPP"], {'S': 'part:solar_cell', 'C': C, 'F': F, 'P': P}, g('solar'))
shaped('grid_wind', ["BBB", "MFG", "PSP"], {'B': 'part:turbine_blade', 'M': M, 'F': F, 'G': 'part:gearbox', 'P': P, 'S': 'part:steel_shaft'}, g('wind'))
shaped('grid_capacitor', ["EEE", "CFC", "PXP"], {'E': 'part:capacitor_cell', 'C': C, 'F': F, 'P': P, 'X': X}, g('capacitor'))
shaped('grid_earthing', ["PSP", "XFX", "PBP"], {'P': P, 'S': 'part:spring', 'X': X, 'F': F, 'B': '#blockIron'}, g('earthing'))
shaped('grid_relay', [" V ", "CTC", " M "], {'V': V, 'C': C, 'T': 'part:relay_contactor', 'M': 'part:meter_movement'}, g('relay'))
shaped('grid_arrester', ["XXX", "XFX", "PDP"], {'X': X, 'F': F, 'P': P, 'D': 'part:rectifier_diode'}, g('arrester'))
shaped('grid_isolator', ["XPX", "SFS", "PXP"], {'X': X, 'P': P, 'S': 'part:spring', 'F': F}, g('isolator'))
shaped('grid_auto', ["VCV", "CFC", "PEP"], {'V': V, 'C': C, 'F': F, 'P': P, 'E': 'minecraft:ender_eye'}, g('auto'))
shaped('wind_tower', ["P P", "PFP", "P P"], {'P': P, 'F': F}, 'irextras:wind_tower', 4)   # tower sections
# ---- panel instruments ----
for k, x in [('voltmeter', 'minecraft:compass'), ('ammeter', 'minecraft:clock'), ('wattmeter', 'minecraft:daylight_detector'), ('varmeter', 'minecraft:repeater'),
             ('pf_meter', 'minecraft:observer'), ('hz_meter', 'minecraft:noteblock'), ('thermometer', 'minecraft:magma_cream')]:
    shaped('grid_inst_' + k, [" X ", "IMI", " I "], {'X': x, 'I': '#ingotIron', 'M': 'part:meter_movement'}, 'irextras:grid_inst_' + k)
shaped('grid_inst_screen', [" X ", "IVI", " I "], {'X': '#dustGlowstone', 'I': '#ingotIron', 'V': V}, 'irextras:grid_inst_screen')
shaped('grid_inst_lamp', [" X ", "IVI", " I "], {'X': 'minecraft:redstone_lamp', 'I': '#ingotIron', 'V': V}, 'irextras:grid_inst_lamp')
shaped('grid_inst_button', [" B ", "IRI"], {'B': 'minecraft:stone_button', 'I': '#ingotIron', 'R': '#dustRedstone'}, 'irextras:grid_inst_button', 2)
# ---- tools ----
shaped('screwdriver', ["  S", " I ", "Y  "], {'S': 'part:steel_shaft', 'I': '#ingotIron', 'Y': '#dyeYellow'}, 'irextras:screwdriver')
shaped('lockout_padlock', [" I ", "I I", "ISI"], {'I': '#ingotIron', 'S': 'part:spring'}, 'irextras:lockout_padlock')
shaped('multimeter', [" V ", "MCM", "PYP"], {'V': V, 'M': 'part:meter_movement', 'C': C, 'P': P, 'Y': 'part:battery_cell'}, 'irextras:multimeter')
shapeless('substation_kit', [g('breaker'), g('breaker'), g('relay'), g('relay'), g('transformer'), g('earthing'), g('earthing'), g('arrester'), g('meter')], 'irextras:substation_kit')
# ---- wiring + computer ----
dyes = ['White', 'Orange', 'Magenta', 'LightBlue', 'Yellow', 'Lime', 'Pink', 'Gray', 'LightGray', 'Cyan', 'Purple', 'Blue', 'Brown', 'Green', 'Red', 'Black']
for i, dn in enumerate(dyes):
    shapeless('instrument_wire_%d' % i, ['#ingotIron', '#dye' + dn, '#slimeball'], 'irextras:instrument_wire@%d' % i, 16)   # wired = cheap (her ask)
    os.path.exists(os.path.join(R, 'instrument_wire_%d.json' % i))
for f in os.listdir(R):                                  # the result data for the wire colours
    if f.startswith('instrument_wire_'):
        d = json.load(open(os.path.join(R, f))); i = int(f[16:-5]); d['result'] = {"item": "irextras:instrument_wire", "data": i, "count": 16}; json.dump(d, open(os.path.join(R, f), 'w'), indent=1)
shapeless('power_wire_12v', ['#ingotIron', '#dyeRed', '#dyeBlack', '#slimeball'], 'irextras:instrument_wire@16', 16)   # 12 V DC power pair
d = json.load(open(os.path.join(R, 'power_wire_12v.json'))); d['result'] = {"item": "irextras:instrument_wire", "data": 16, "count": 16}; json.dump(d, open(os.path.join(R, 'power_wire_12v.json'), 'w'), indent=1)
shaped('bundled_cable', ["WWW", "WSW", "WWW"], {'W': {"item": "irextras:instrument_wire", "data": 32767}, 'S': 'part:rubber_seal'}, 'irextras:bundled_cable', 2)
shaped('grid_tap', ["XKX", "WCW", "XIX"], {'X': X, 'K': K, 'W': W, 'C': C, 'I': '#ingotIron'}, 'irextras:grid_tap')
shaped('wire_chase', ["SSS", "SBS", "SSS"], {'S': 'minecraft:stonebrick', 'B': 'irextras:bundled_cable'}, 'irextras:wire_chase', 8)   # hidden wiring in the wall
shaped('gauge_panel', ["PGP", "GFG", "PGP"], {'P': P, 'G': '#paneGlass', 'F': F}, 'irextras:gauge_panel')
# wireless costs more than running a wire (her ask): two antennas, three circuits, a battery
shaped('radio_transmitter', ["AYA", "CTC", "PCP"], {'A': 'part:antenna', 'C': C, 'T': 'minecraft:redstone_torch', 'P': P, 'Y': 'part:battery_cell'}, 'irextras:radio_transmitter')
shaped('radio_receiver', ["AYA", "CXC", "PCP"], {'A': 'part:antenna', 'C': C, 'X': 'minecraft:comparator', 'P': P, 'Y': 'part:battery_cell'}, 'irextras:radio_receiver')
shaped('data_link', ["PAP", "CVC", "PFP"], {'P': P, 'A': 'part:antenna', 'C': C, 'V': V, 'F': F}, 'irextras:data_link')
print(n, 'recipes written')
