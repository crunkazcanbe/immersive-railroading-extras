#!/usr/bin/env python3
"""Give every model element that reaches outside its block (-16..32 models) explicit face UVs.

Without a uv, Minecraft derives it from the element's from/to; outside 0..16 that samples the
NEIGHBOURING textures on the atlas (industries showed scraps of warning signs). Run after any
gen_*.py:   python3 tools/fix_model_uvs.py
"""
import glob, json, os
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src/main/resources/assets/irextras/models/block")
n = 0
for p in glob.glob(os.path.join(root, "*.json")):
    try:
        m = json.load(open(p))
    except Exception:
        continue
    ch = False
    for e in m.get("elements", []):
        if min(e["from"] + e["to"]) < 0 or max(e["from"] + e["to"]) > 16:
            for face in e["faces"].values():
                if "uv" not in face:
                    face["uv"] = [0, 0, 16, 16]
                    ch = True
    if ch:
        json.dump(m, open(p, "w"), indent=1)
        n += 1
print("fixed %d models" % n)
