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
    save('door_warn', buf); reg('door_warn', 'Doors closing')

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
    save('alarm', np.sign(np.sin(2 * np.pi * (700 + 300 * (np.sin(2 * np.pi * 2 * x) > 0)) * x)) * 0.3); reg('alarm', 'Alarm')

    with open(os.path.join(ROOT, 'sounds.json'), 'w') as fh:
        json.dump(sounds, fh, indent=1)
    print('wrote', len(sounds), 'sounds')


if __name__ == '__main__':
    main()
