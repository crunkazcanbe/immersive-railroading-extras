#!/usr/bin/env python3
"""Wire the plant sound pack into IR Extras (run from ~/mc-mods/railmap). Every edit asserts its anchor."""
import re, sys

J = 'src/main/java/com/dogpound/railmap/'
miss = []


def sub(f, a, b, count=1):
    p = J + f
    s = open(p).read()
    if s.count(a) != count:
        miss.append(f'{f}: expected {count}x, found {s.count(a)}x: {a[:90]!r}')
        return
    open(p, 'w').write(s.replace(a, b))


T = 'grid/TileGrid.java'
# every grid panel control sounds like what it is (button, e-stop, selector, key, handle)
sub(T, '        pressId = id;\n        pressAt = world.getTotalWorldTime();\n        panelDirty = true;\n',
    '        pressId = id;\n        pressAt = world.getTotalWorldTime();\n        panelDirty = true;\n'
    '        Panel pnl = Panel.of(kind);\n'
    '        if (pnl != null) for (Panel.C c : pnl.cs) if (c.id.equals(id)) {\n'
    '            net.minecraft.util.SoundEvent cs = ModSounds.forControl(c.t);\n'
    '            if (cs != null) world.playSound(null, pos, cs, SoundCategory.BLOCKS, 0.6f, 0.95f + world.rand.nextFloat() * 0.1f);\n'
    '            break;\n'
    '        }\n')
# on-load tap changer
sub(T, 'n.tap = t; d.markDirty(); clunk(id.equals("raise") ? 1.3f : 1.1f);',
    'n.tap = t; d.markDirty(); world.playSound(null, pos, ModSounds.TAP_CHANGE, SoundCategory.BLOCKS, 0.9f, id.equals("raise") ? 1.05f : 0.95f);')
# breaker: close / open / trip each sound like it
sub(T, 'world.playSound(null, pos, b == 2 ? ModSounds.BREAKER : ModSounds.RELAY, SoundCategory.BLOCKS, 1f, b == 0 ? 1f : 0.85f);',
    'world.playSound(null, pos, b == 2 ? ModSounds.BREAKER_TRIP : b == 0 ? ModSounds.BREAKER_CLOSE : ModSounds.BREAKER, SoundCategory.BLOCKS, 1f, 1f);')
sub(T, '        if (b == 0) world.playSound(null, pos, ModSounds.BREAKER, SoundCategory.BLOCKS, 0.7f, 1.2f);\n', '')
# the old re-triggered 4 s hum clips: the client GridSounds loops replace them
old_hum = re.search(r'        if \(kind\.buttons\(\) && state == 1 && cfg\.bool\("sound"\).*?\n        \}\n', open(J + T).read(), re.S)
if old_hum and 'HUM_TRANSFORMER' in old_hum.group(0) and old_hum.group(0).count('\n') <= 12:
    sub(T, old_hum.group(0), '        // plant hum / whine / fans / engines: client GridSounds loops, following the synced panel values\n')
else:
    miss.append('TileGrid: old hum block not found')
# transformers report their load (% of kVA) so the hum can swell with it
sub(T, '                put("temp", Math.round(n.heat));\n',
    '                put("temp", Math.round(n.heat));\n'
    '                put("load", net == null ? 0 : net.level != null ? (int) Math.min(150, net.sVA * 100 / Math.max(1, n.kva * 1000.0))\n'
    '                        : (int) Math.min(150, load * 100 / Math.max(1, (long) GridData.RATING * Math.max(1, net.transformers))));\n')
# how loud this machine's plant sounds are (its Hum / Volume settings; gear without them: 70 %)
sub(T, '    public int pv(String k) {',
    '    public float soundLevel() {\n'
    '        if (!kind.converter() && !kind.buttons()) return 0.7f;\n'
    '        return cfg.bool("sound") ? cfg.num("volume") / 100f : 0f;\n'
    '    }\n\n'
    '    public int pv(String k) {')

# every other machine's controls (640+): real sounds by control type instead of piston / stone button
I = 'controls/IControlled.java'
s = open(J + I).read()
m = re.search(r'        t\.getWorld\(\)\.playSound\(null, t\.getPos\(\), c\.t == Panel\.T\.PULL.*?\n.*?\n', s)
if m:
    sub(I, m.group(0),
        '        net.minecraft.util.SoundEvent snd = com.dogpound.railmap.ModSounds.forControl(c.t);\n'
        '        if (snd != null) t.getWorld().playSound(null, t.getPos(), snd, net.minecraft.util.SoundCategory.BLOCKS,\n'
        '                c.t == Panel.T.PUSH ? 0.5f : 0.8f, 0.95f + t.getWorld().rand.nextFloat() * 0.1f);\n')
else:
    miss.append('IControlled: playSound not found')

# arc flash when a breaker fails, sizzle when a cable burns
S = 'grid/GridSolver.java'
sub(S, '                        w.newExplosion(null, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2.0f, true, false);   // arc flash: fire, no crater\n',
    '                        w.newExplosion(null, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2.0f, true, false);   // arc flash: fire, no crater\n'
    '                        w.playSound(null, bp, com.dogpound.railmap.ModSounds.ARC_FLASH, net.minecraft.util.SoundCategory.BLOCKS, 4f, 1f);\n')
sub(S, '            if (w.isAirBlock(bp)) w.setBlockState(bp, Blocks.FIRE.getDefaultState());\n',
    '            if (w.isAirBlock(bp)) w.setBlockState(bp, Blocks.FIRE.getDefaultState());\n'
    '            w.playSound(null, bp, com.dogpound.railmap.ModSounds.CABLE_SIZZLE, net.minecraft.util.SoundCategory.BLOCKS, 2f, 1f);\n')

# client: run the live loops
sub('proxy/ClientProxy.java', '        MinecraftForge.EVENT_BUS.register(com.dogpound.railmap.client.CabDisplay.class);\n',
    '        MinecraftForge.EVENT_BUS.register(com.dogpound.railmap.client.CabDisplay.class);\n'
    '        MinecraftForge.EVENT_BUS.register(com.dogpound.railmap.client.GridSounds.class);\n')

# generator: build the plant pack too
G = 'tools/gen_sounds.py'
s = open(G).read()
a = "    with open(os.path.join(ROOT, 'sounds.json'), 'w') as fh:"
if 'gen_sounds_plant' not in s and s.count(a) == 1:
    open(G, 'w').write(s.replace(a, "    import gen_sounds_plant\n    gen_sounds_plant.add(save, reg)\n\n" + a))
elif 'gen_sounds_plant' not in s:
    miss.append('gen_sounds.py anchor')

if miss:
    print('MISSED:\n  ' + '\n  '.join(miss))
    sys.exit(1)
print('all edits applied')
