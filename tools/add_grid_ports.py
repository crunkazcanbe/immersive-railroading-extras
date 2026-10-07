#!/usr/bin/env python3
"""Cable-entry ports on the power-grid machines (her ask 2026-10-06: "I want the wire to really connect to it like it's
meant for it"). A port is drawn on a side ONLY when something really hooks in there: our Feeder Cable, any mod's power
cable / machine (Forge Energy), or any mod's pipe for the diesel / steam turbine. BlockGrid works the sides out
(N/S/E/W actual-state); this writes the multipart blockstates + the port model, and strips the old fixed ports.
Port = framed socket plate at the block face (tall enough for any mod's cable) + a conduit back into the machine; our
Feeder Cable runs in at the bottom of the plate. Run after gen_grid_models (it calls this)."""
import glob, json, os
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src/main/resources/assets/irextras")
MOD = "irextras"

def el(a, b, t):
    return {"from": list(a), "to": list(b), "faces": {f: {"texture": "#" + t, "uv": [0, 0, 16, 16]} for f in ("north", "south", "east", "west", "up", "down")}}

# the port, for the NORTH side (z = 0); the blockstate turns it for the others
port = {"textures": {"particle": MOD + ":blocks/grid_port", "port": MOD + ":blocks/grid_port", "dark": MOD + ":blocks/ohle_dark",
                     "cu": MOD + ":blocks/grid_copper", "cable": MOD + ":blocks/grid_cable"},
        "elements": [el((4, 0, -0.3), (12, 11, 1.0), "port"),          # socket plate on the machine face
                     el((3.6, 0, 1.0), (12.4, 11.4, 1.6), "dark"),      # mounting frame
                     el((6, 1, 1.6), (10, 5, 8), "cable"),              # conduit into the machine
                     el((6.3, 0.2, -0.6), (9.7, 2.8, -0.3), "cu")]}     # gland nut where Feeder Cable enters
json.dump(port, open(os.path.join(R, "models/block/grid_port_side.json"), "w"), indent=1)

n = 0
for p in glob.glob(os.path.join(R, "models/block/grid_*.json")):
    m = json.load(open(p))
    keep = [e for e in m.get("elements", []) if e.get("__comment") != "port"]
    if len(keep) != len(m.get("elements", [])):
        m["elements"] = keep
        json.dump(m, open(p, "w"), indent=1)

rot = {"north": 0, "east": 90, "south": 180, "west": 270}
for bs in glob.glob(os.path.join(R, "blockstates/grid_*.json")):
    kind = os.path.basename(bs)[5:-5]
    if kind == "cable":
        continue
    d = json.load(open(bs))
    variants = d.get("variants")
    if variants is None:   # already multipart: rebuild from its base parts
        variants = {}
        for part in d["multipart"]:
            w = part.get("when", {})
            if "facing" in w:
                variants["facing=%s,state=%s" % (w["facing"], w["state"])] = part["apply"]
    parts = []
    for key, mdl in variants.items():
        f, st = [kv.split("=")[1] for kv in key.split(",")]
        parts.append({"when": {"facing": f, "state": st}, "apply": mdl})
    for side, y in rot.items():
        a = {"model": MOD + ":grid_port_side"}
        if y: a["y"] = y
        parts.append({"when": {side: "true"}, "apply": a})
    json.dump({"multipart": parts}, open(bs, "w"), indent=1)
    n += 1
print("port-ready blockstates: %d" % n)
