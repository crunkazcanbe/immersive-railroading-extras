#!/usr/bin/env python3
"""Synthesised sounds for IR Extras / Pride Rail (numpy -> wav -> ogg via ffmpeg). Run: python3 tools/gen_sounds.py
Writes assets/irextras/sounds/*.ogg and sounds.json."""
import json, os, subprocess, tempfile, wave
import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/irextras')
OUT = os.path.join(ROOT, 'sounds')
rng = np.random.default_rng(7)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a=0.005, d=None, s=1.0, r=0.05):
    """attack / (exp decay | sustain) / release envelope over n samples"""
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if d:
        e *= np.exp(-np.arange(n) / (d * SR))
    if nr and nr < n:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e * s


def bell(freq, sec, decay=0.6):
    x = t(sec)
    tone = (np.sin(2 * np.pi * freq * x) + 0.45 * np.sin(2 * np.pi * freq * 2.01 * x) * np.exp(-x / (decay * 0.4))
            + 0.25 * np.sin(2 * np.pi * freq * 3.02 * x) * np.exp(-x / (decay * 0.25)) + 0.1 * np.sin(2 * np.pi * freq * 4.2 * x) * np.exp(-x / 0.08))
    return tone * env(len(x), 0.003, decay)


def noise(sec):
    return rng.standard_normal(int(SR * sec))


def lowpass(x, cut):
    """one-pole low pass"""
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cut):
    return x - lowpass(x, cut)


def place(buf, sig, at):
    i = int(at * SR)
    buf[i:i + len(sig)] += sig[:max(0, len(buf) - i)]
    return buf


def thunk(sec=0.25, f=70):
    x = t(sec)
    return (np.sin(2 * np.pi * f * x * (1 - 0.3 * x)) * np.exp(-x / 0.05) + 0.4 * lowpass(noise(sec), 900) * np.exp(-x / 0.02))


def hiss(sec, cut_lo=1500, cut_hi=6000, shape=None):
    n = noise(sec)
    y = lowpass(highpass(n, cut_lo), cut_hi)
    return y * (shape if shape is not None else env(len(y), 0.01, None, 1, 0.15))


def motor(sec, f0, f1, amp=0.3):
    x = t(sec)
    f = np.linspace(f0, f1, len(x))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return amp * (np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)) * env(len(x), 0.08, None, 1, 0.2)


def hum(sec, base=50, harmonics=(1, 2, 3, 4, 6), amp=0.25, buzz=0.05):
    """mains-frequency transformer hum (magnetostriction = 2x line frequency) with a little buzz"""
    x = t(sec)
    y = sum(np.sin(2 * np.pi * base * 2 * h * x + h) / h for h in harmonics)
    y += buzz * lowpass(noise(sec), 3000)
    return amp * y / max(1e-9, np.max(np.abs(y)))


def loopify(y, xf=0.3):
    """seamless loop: crossfade the tail into the head"""
    n = int(xf * SR)
    y = np.asarray(y, dtype=np.float64).copy()
    head, tail = y[:n].copy(), y[-n:]
    w = np.linspace(0, 1, n)
    y[:n] = head * w + tail * (1 - w)
    return y[:-n]


def click(sec=0.012, cut=2500, d=0.002, amp=1.0):
    x = t(sec)
    return amp * highpass(noise(sec), cut) * env(len(x), 0.0003, d)


def tone(f, sec, amp=1.0, a=0.005, r=0.02, square=False):
    x = t(sec)
    w = np.sin(2 * np.pi * f * x)
    if square:
        w = np.sign(w) * 0.6
    return amp * w * env(len(x), a, None, 1, r)


def am(y, rate, depth=0.5, phase=0.0):
    x = np.arange(len(y)) / SR
    return y * (1 - depth + depth * (0.5 + 0.5 * np.sin(2 * np.pi * rate * x + phase)))


def resonant(f, sec, d, amp=1.0):
    """struck metal / wood body: damped sine cluster"""
    x = t(sec)
    return amp * (np.sin(2 * np.pi * f * x) + 0.5 * np.sin(2 * np.pi * f * 2.7 * x) * np.exp(-x / (d * 0.5))) * np.exp(-x / d)


def save(name, y, gain=0.85):
    y = np.asarray(y, dtype=np.float64)
    m = np.max(np.abs(y)) or 1.0
    y = (y / m * gain * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        w = wave.open(tf.name, 'wb')
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(y.tobytes())
        w.close()
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tf.name, '-c:a', 'libvorbis', '-q:a', '5',
                        os.path.join(OUT, name + '.ogg')], check=True)
        os.unlink(tf.name)


def main():
    sounds = {}

    def reg(name, sub, stream=False):
        sounds[name] = {'category': 'neutral', 'subtitle': 'irextras.sub.' + name,
                        'sounds': [{'name': 'irextras:' + name, 'stream': stream}]}

    # door chime: three-note bell (station melody style)
    buf = np.zeros(int(SR * 1.6))
    for i, f in enumerate((659.3, 830.6, 987.8)):
        place(buf, bell(f, 1.1, 0.55) * (0.9 if i < 2 else 1.0), i * 0.22)
    save('door_chime', buf); reg('door_chime', 'Door chime')

    # doors opening: valve hiss, sliding rumble, end stop thunk
    buf = np.zeros(int(SR * 1.6))
    place(buf, 0.6 * hiss(0.45, 1200, 7000, env(int(SR * 0.45), 0.005, 0.18)), 0.0)
    place(buf, motor(1.0, 55, 80, 0.25) + 0.15 * lowpass(noise(1.0), 400), 0.12)
    place(buf, 0.9 * thunk(0.3, 85), 1.15)
    save('door_open', buf); reg('door_open', 'Train doors open')

    # door-closing warning: rapid two-tone beeps
    buf = np.zeros(int(SR * 2.0))
    for k in range(6):
        f = 2093.0 if k % 2 == 0 else 1760.0
        x = t(0.14)
        place(buf, 0.5 * np.sign(np.sin(2 * np.pi * f * x)) * 0.4 * env(len(x), 0.004, None, 1, 0.02), k * 0.3)
    save('door_warn', buf, 0.45); reg('door_warn', 'Doors closing')

    # doors closing: rumble, rubber seal thump, lock click
    buf = np.zeros(int(SR * 1.5))
    place(buf, motor(0.9, 80, 52, 0.25) + 0.12 * lowpass(noise(0.9), 400), 0.0)
    place(buf, 1.0 * thunk(0.35, 65), 0.9)
    x = t(0.03)
    place(buf, 0.5 * highpass(noise(0.03), 3000) * env(len(x), 0.001, 0.006), 1.05)
    place(buf, 0.35 * hiss(0.3, 1500, 6000, env(int(SR * 0.3), 0.005, 0.1)), 1.1)
    save('door_close', buf); reg('door_close', 'Train doors close')

    # platform screen doors: lighter, electric
    buf = np.zeros(int(SR * 1.2))
    place(buf, motor(0.9, 120, 160, 0.2), 0.0)
    place(buf, 0.5 * thunk(0.2, 140), 0.9)
    save('psd_move', buf); reg('psd_move', 'Platform doors move')

    # transformer / substation hum (looped by the block)
    save('hum_transformer', hum(4.0, 50, (1, 2, 3, 5), 0.3, 0.04), 0.6); reg('hum_transformer', 'Transformer hums')
    save('hum_rectifier', hum(4.0, 50, (1, 3, 6, 12), 0.3, 0.12), 0.6); reg('hum_rectifier', 'Rectifier whines')
    # breaker: big clunk + arc snap
    buf = np.zeros(int(SR * 0.8))
    place(buf, 1.0 * thunk(0.4, 55), 0.0)
    x = t(0.06)
    place(buf, 0.7 * highpass(noise(0.06), 2500) * env(len(x), 0.001, 0.015), 0.01)
    save('breaker_clunk', buf); reg('breaker_clunk', 'Breaker operates')
    # relay click
    x = t(0.05)
    save('relay_click', highpass(noise(0.05), 2000) * env(len(x), 0.0005, 0.004)); reg('relay_click', 'Relay clicks')
    # alarm (fault)
    x = t(1.2)
    save('alarm', np.sign(np.sin(2 * np.pi * (700 + 300 * (np.sin(2 * np.pi * 2 * x) > 0)) * x)) * 0.3, 0.3); reg('alarm', 'Alarm')


    # ================= Pride sound pack (her ask 10-10: "start the sound pack") =================
    # ---- panel controls: every control type gets its own real mechanical sound
    buf = np.zeros(int(SR * 0.18))                                    # push button: plunger down + contact snap
    place(buf, click(0.012, 1800, 0.003, 0.9), 0.0); place(buf, resonant(1900, 0.05, 0.008, 0.25), 0.0)
    place(buf, click(0.008, 4000, 0.0015, 0.6), 0.035)
    save('ctl_push', buf); reg('ctl_push', 'Button pressed')
    buf = np.zeros(int(SR * 0.22))                                    # rotary selector: detent ratchet + clack
    for k, at in enumerate((0.0, 0.025, 0.05)):
        place(buf, click(0.01, 2200 + 600 * k, 0.0025, 0.5 + 0.2 * k), at)
    place(buf, resonant(1300, 0.08, 0.012, 0.35), 0.06)
    save('ctl_selector', buf); reg('ctl_selector', 'Switch turned')
    buf = np.zeros(int(SR * 0.35))                                    # key switch: key scrape, cylinder turn, spring clack
    place(buf, 0.25 * hiss(0.08, 3000, 9000, env(int(SR * 0.08), 0.003, 0.03)), 0.0)
    place(buf, click(0.015, 1500, 0.004, 0.8), 0.1); place(buf, resonant(2400, 0.1, 0.015, 0.3), 0.1)
    save('ctl_key', buf); reg('ctl_key', 'Key turned')
    buf = np.zeros(int(SR * 0.6))                                     # big operating lever: catch, swing, heavy stop
    place(buf, click(0.02, 1200, 0.004, 0.7), 0.0)
    place(buf, 0.2 * lowpass(noise(0.3), 700) * env(int(SR * 0.3), 0.05, None, 1, 0.1), 0.03)
    place(buf, thunk(0.3, 95), 0.3); place(buf, resonant(420, 0.25, 0.06, 0.4), 0.3)
    save('ctl_lever', buf); reg('ctl_lever', 'Lever thrown')
    buf = np.zeros(int(SR * 1.0))                                     # signal box lever: catch handle, long pull, frame clank
    place(buf, click(0.02, 1500, 0.005, 0.8), 0.0); place(buf, resonant(900, 0.15, 0.03, 0.3), 0.0)
    place(buf, 0.25 * lowpass(noise(0.45), 500) * env(int(SR * 0.45), 0.1, None, 1, 0.1), 0.08)
    place(buf, thunk(0.4, 70), 0.55); place(buf, resonant(310, 0.4, 0.12, 0.55), 0.55); place(buf, resonant(780, 0.3, 0.07, 0.3), 0.57)
    save('ctl_signal_lever', buf); reg('ctl_signal_lever', 'Signal lever pulled')
    buf = np.zeros(int(SR * 0.4))                                     # emergency stop: palm slap + latch
    place(buf, 0.8 * lowpass(noise(0.05), 1500) * env(int(SR * 0.05), 0.001, 0.012), 0.0)
    place(buf, thunk(0.15, 160), 0.0); place(buf, click(0.012, 2500, 0.003, 0.8), 0.06)
    save('ctl_estop', buf); reg('ctl_estop', 'Emergency stop pressed')
    buf = np.zeros(int(SR * 0.3))                                     # toggle switch
    place(buf, click(0.006, 3000, 0.0012, 1.0), 0.0); place(buf, resonant(3200, 0.04, 0.005, 0.3), 0.0)
    save('ctl_toggle', buf); reg('ctl_toggle', 'Switch flicked')
    buf = np.zeros(int(SR * 0.5))                                     # pull handle / chain
    place(buf, 0.4 * hiss(0.2, 2000, 8000, env(int(SR * 0.2), 0.01, 0.08)), 0.0)
    place(buf, resonant(600, 0.25, 0.05, 0.6), 0.2); place(buf, click(0.015, 1500, 0.004, 0.6), 0.2)
    save('ctl_pull', buf); reg('ctl_pull', 'Handle pulled')
    # ---- switchgear
    buf = np.zeros(int(SR * 0.3))                                     # contactor: coil pull-in clack
    place(buf, thunk(0.12, 180), 0.0); place(buf, click(0.015, 2000, 0.004, 1.0), 0.0); place(buf, 0.15 * hum(0.25, 50, (1, 2), 1, 0.2), 0.02)
    save('contactor', buf); reg('contactor', 'Contactor clacks')
    buf = np.zeros(int(SR * 0.5))                                     # fuse blows: pop + fizz
    place(buf, thunk(0.15, 120), 0.0); place(buf, click(0.03, 1000, 0.008, 1.0), 0.0)
    place(buf, 0.3 * hiss(0.4, 2500, 9000, env(int(SR * 0.4), 0.001, 0.12)), 0.01)
    save('fuse_blow', buf); reg('fuse_blow', 'Fuse blows')
    buf = np.zeros(int(SR * 2.4))                                     # arc flash: bang, crackling plasma, decay
    place(buf, 1.0 * thunk(0.6, 45), 0.0)
    crackle = noise(1.8) * (rng.random(int(SR * 1.8)) > 0.97) * 3
    place(buf, 3.0 * (highpass(crackle, 1500) + 0.6 * lowpass(noise(1.8), 2500)) * env(int(SR * 1.8), 0.001, 0.5), 0.0)
    buf = np.tanh(1.8 * buf / np.max(np.abs(buf)))
    place(buf, 0.4 * hum(1.2, 50, (1, 2, 3), 1, 0.6) * env(int(SR * 1.2), 0.001, 0.3), 0.02)
    save('arc_flash', buf, 0.95); reg('arc_flash', 'Arc flash!')
    buf = np.zeros(int(SR * 1.5))                                     # flashover / partial discharge: angry buzzing sizzle
    sz = noise(1.5) * (rng.random(int(SR * 1.5)) > 0.9)
    place(buf, am(highpass(sz, 2000), 100, 0.8) * env(int(SR * 1.5), 0.05, None, 1, 0.2), 0.0)
    save('arc_buzz', buf); reg('arc_buzz', 'Insulation arcing')
    # ---- running machines (looped by the block while it runs)
    x = t(4.3)                                                        # inverter: IGBT switching whine + 100 Hz ripple + fan
    y = 0.12 * np.sin(2 * np.pi * 4000 * x + 3 * np.sin(2 * np.pi * 60 * x)) + 0.05 * np.sin(2 * np.pi * 8000 * x)
    y += 0.3 * hum(4.3, 60, (1, 2, 3), 1, 0.02) + 0.2 * lowpass(noise(4.3), 600)
    save('loop_inverter', loopify(y), 0.5); reg('loop_inverter', 'Inverter whines')
    x = t(4.3)                                                        # DC-DC converter: higher, thinner whine
    y = 0.1 * np.sin(2 * np.pi * 6200 * x) + 0.06 * np.sin(2 * np.pi * 12400 * x) + 0.12 * lowpass(noise(4.3), 900)
    save('loop_dcdc', loopify(y), 0.4); reg('loop_dcdc', 'Converter whines')
    x = t(4.3)                                                        # cooling fans: blade pass + air rush
    y = am(lowpass(noise(4.3), 1800), 23, 0.25) + 0.3 * np.sin(2 * np.pi * 23 * 7 * x)
    save('loop_fans', loopify(y), 0.5); reg('loop_fans', 'Cooling fans run')
    x = t(4.3)                                                        # diesel genset at 1800 rpm: firing pulses + mechanical rattle
    fire = 1800 / 60 * 3                                              # 6-cylinder 4-stroke firing frequency (90 Hz)
    pulses = np.sin(2 * np.pi * fire * x) ** 9
    y = 0.6 * lowpass(pulses * (1 + 0.3 * noise(4.3)), 500) + 0.15 * am(highpass(noise(4.3), 2000), fire, 0.9)
    y += 0.25 * np.sin(2 * np.pi * fire / 2 * x)
    save('loop_diesel', loopify(y), 0.7); reg('loop_diesel', 'Diesel engine runs')
    x = t(4.3)                                                        # steam turbine: high whistle + steam roar
    y = 0.15 * np.sin(2 * np.pi * 3000 * x) + 0.08 * np.sin(2 * np.pi * 6000 * x) + 0.5 * lowpass(highpass(noise(4.3), 300), 4000)
    y += 0.2 * hum(4.3, 60, (1, 2), 1, 0.01)
    save('loop_turbine', loopify(y), 0.6); reg('loop_turbine', 'Steam turbine roars')
    x = t(4.3)                                                        # wind turbine: slow blade swoosh (3 blades at ~15 rpm)
    y = am(lowpass(noise(4.3), 900), 0.75, 0.85) + 0.1 * np.sin(2 * np.pi * 45 * x)
    save('loop_wind', loopify(y), 0.5); reg('loop_wind', 'Wind turbine swooshes')
    x = t(4.3)                                                        # air compressor: piston thump + intake hiss
    y = 0.6 * lowpass(np.sin(2 * np.pi * 12 * x) ** 15, 300) + 0.25 * am(hiss(4.3, 1500, 6000, np.ones(int(SR * 4.3))), 12, 0.8)
    save('loop_compressor', loopify(y), 0.6); reg('loop_compressor', 'Compressor thumps')
    x = t(4.3)                                                        # pump: motor + fluid swirl
    y = 0.4 * hum(4.3, 60, (1, 2), 1, 0.02) + 0.3 * am(lowpass(noise(4.3), 1200), 29, 0.4)
    save('loop_pump', loopify(y), 0.5); reg('loop_pump', 'Pump runs')
    x = t(4.3)                                                        # conveyor belt: rollers + idler squeak
    y = 0.35 * am(lowpass(noise(4.3), 700), 8, 0.6) + 0.06 * np.sin(2 * np.pi * 2300 * x) * (np.sin(2 * np.pi * 0.47 * x) > 0.6)
    save('loop_conveyor', loopify(y), 0.5); reg('loop_conveyor', 'Conveyor runs')
    x = t(4.3)                                                        # heavy crusher / mill: grinding with irregular impacts
    y = 0.6 * lowpass(noise(4.3), 400) + 0.25 * lowpass(highpass(noise(4.3) * (rng.random(int(SR * 4.3)) > 0.995), 300), 2500) * 3 + 0.3 * am(lowpass(noise(4.3), 900), 3.1, 0.7)
    y = np.tanh(2.5 * y / np.max(np.abs(y)))
    save('loop_crusher', loopify(y), 0.7); reg('loop_crusher', 'Machine grinds')
    x = t(4.3)                                                        # control room: computers, aircon, soft hum
    y = 0.3 * lowpass(noise(4.3), 500) + 0.06 * np.sin(2 * np.pi * 15600 * x) + 0.1 * hum(4.3, 60, (1,), 1, 0)
    save('loop_control_room', loopify(y), 0.3); reg('loop_control_room', 'Control room hums')
    x = t(4.3)                                                        # furnace / coke oven roar
    y = 0.7 * am(lowpass(noise(4.3), 350), 0.9, 0.3) + 0.2 * lowpass(highpass(noise(4.3), 800), 2500)
    save('loop_furnace', loopify(y), 0.7); reg('loop_furnace', 'Furnace roars')
    # ---- alarms and signals
    buf = np.zeros(int(SR * 1.0))                                     # control-room buzzer
    place(buf, am(tone(400, 0.9, 1, square=True), 100, 0.6), 0.0)
    save('buzzer', buf, 0.3); reg('buzzer', 'Buzzer')
    buf = np.zeros(int(SR * 1.6))                                     # annunciator horn (ISA new alarm)
    for k in range(2):
        place(buf, 0.6 * (tone(880, 0.55, 1, square=True) + 0.5 * tone(1320, 0.55, 1, square=True)), k * 0.75)
    save('annunciator', buf, 0.3); reg('annunciator', 'Annunciator sounds')
    x = t(2.0)                                                        # klaxon (evacuation / major incident)
    f = 450 + 250 * (np.abs((x * 1.5) % 1 - 0.5) * 2)
    save('klaxon', np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.5 * env(len(x), 0.01, None, 1, 0.05), 0.3); reg('klaxon', 'Klaxon')
    buf = np.zeros(int(SR * 2.0))                                     # fire alarm bell (striker)
    for k in range(int(2.0 * 18)):
        place(buf, bell(1150, 0.12, 0.08) * 0.5, k / 18)
    save('fire_bell', buf); reg('fire_bell', 'Fire alarm rings')
    buf = np.zeros(int(SR * 1.0))                                     # level crossing bell: one strike (block repeats it)
    place(buf, bell(760, 0.9, 0.35), 0.0)
    save('crossing_bell', buf); reg('crossing_bell', 'Crossing bell')
    buf = np.zeros(int(SR * 1.2))                                     # block instrument bell: single tinny ding
    place(buf, bell(1567, 1.1, 0.45) * 0.8, 0.0)
    save('block_bell', buf); reg('block_bell', 'Block bell rings')
    buf = np.zeros(int(SR * 3.0))                                     # point machine: motor run, clutch, lock thunk
    place(buf, click(0.02, 1500, 0.005, 0.7), 0.0)
    place(buf, motor(2.2, 140, 150, 0.35) + 0.15 * am(lowpass(noise(2.2), 1500), 31, 0.5), 0.05)
    place(buf, thunk(0.4, 90), 2.3); place(buf, click(0.02, 2000, 0.005, 0.8), 2.35)
    save('point_motor', buf); reg('point_motor', 'Points move')
    buf = np.zeros(int(SR * 2.6))                                     # station PA chime (four-note) before announcements
    for i, f in enumerate((523.3, 659.3, 784.0, 1046.5)):
        place(buf, bell(f, 1.4, 0.7) * 0.8, i * 0.35)
    save('pa_chime', buf); reg('pa_chime', 'Station chime')
    buf = np.zeros(int(SR * 4.0))                                     # control-room telephone ring (UK double ring)
    for k in range(2):
        for j in range(2):
            place(buf, am(tone(420, 0.4, 0.5) + tone(450, 0.4, 0.5), 25, 0.9), k * 2.0 + j * 0.6)
    save('phone_ring', buf); reg('phone_ring', 'Telephone rings')
    buf = np.zeros(int(SR * 2.2))                                     # dot-matrix / thermal printer (event recorder, waybills)
    for k in range(40):
        place(buf, am(click(0.03, 1800, 0.01, 0.4), 400, 0.8), k * 0.045)
    place(buf, 0.3 * hiss(0.4, 2000, 7000, env(int(SR * 0.4), 0.01, 0.1)), 1.85)
    save('printer', buf); reg('printer', 'Printer prints')
    buf = np.zeros(int(SR * 0.5))                                     # ticket machine / validator beep
    place(buf, tone(2400, 0.12, 0.5), 0.0); place(buf, tone(3200, 0.15, 0.5), 0.15)
    save('beep_ok', buf, 0.5); reg('beep_ok', 'Accepted beep')
    buf = np.zeros(int(SR * 0.6))
    place(buf, tone(330, 0.45, 0.6, square=True), 0.0)
    save('beep_error', buf, 0.35); reg('beep_error', 'Error beep')
    buf = np.zeros(int(SR * 0.9))                                     # ticket gate / turnstile paddles
    place(buf, click(0.015, 1500, 0.004, 0.7), 0.0); place(buf, motor(0.5, 200, 230, 0.25), 0.05); place(buf, thunk(0.2, 150), 0.6)
    save('gate', buf); reg('gate', 'Ticket gate opens')
    buf = np.zeros(int(SR * 1.2))                                     # cash register (freight office / shops)
    place(buf, bell(2637, 1.0, 0.4) * 0.6, 0.0); place(buf, thunk(0.3, 110), 0.15); place(buf, 0.3 * hiss(0.2, 2000, 6000), 0.15)
    save('cash', buf); reg('cash', 'Payment taken')
    buf = np.zeros(int(SR * 1.8))                                     # air brake release / pneumatic valve exhaust
    place(buf, 0.8 * hiss(1.6, 800, 7000, env(int(SR * 1.6), 0.005, 0.5)), 0.0)
    save('air_release', buf); reg('air_release', 'Air released')
    buf = np.zeros(int(SR * 2.5))                                     # generator start: starter crank, catch, rev up
    place(buf, motor(0.8, 8, 12, 0.5) + 0.3 * am(lowpass(noise(0.8), 800), 10, 0.9), 0.0)
    xs = t(1.6); ff = np.linspace(20, 90, len(xs)); ph = 2 * np.pi * np.cumsum(ff) / SR
    place(buf, 0.6 * lowpass(np.sin(ph) ** 9, 500) * env(len(xs), 0.02, None, 1, 0.3), 0.8)
    save('engine_start', buf); reg('engine_start', 'Engine starts')
    buf = np.zeros(int(SR * 2.5))                                     # generator shutdown: run-down
    xs = t(2.4); ff = np.linspace(90, 6, len(xs)); ph = 2 * np.pi * np.cumsum(ff) / SR
    place(buf, 0.6 * lowpass(np.sin(ph) ** 9, 500) * env(len(xs), 0.01, 0.9), 0.0)
    save('engine_stop', buf); reg('engine_stop', 'Engine stops')

    import gen_sounds_plant
    gen_sounds_plant.add(save, reg)

    try:                                   # keep the sound pack's dotted ids (gen_soundpack.py / gen_voice.py)
        with open(os.path.join(ROOT, 'sounds.json')) as fh:
            sounds.update({k: v for k, v in json.load(fh).items() if '.' in k})
    except (OSError, ValueError):
        pass
    with open(os.path.join(ROOT, 'sounds.json'), 'w') as fh:
        json.dump(sounds, fh, indent=1)
    print('wrote', len(sounds), 'sounds')


if __name__ == '__main__':
    main()
