#!/usr/bin/env python3
"""Station voice library for IR Extras (her sound-pack ask 2026-10-10).

Concatenative announcer, the way real station PA systems work: every word and common phrase is recorded once
(here: Piper TTS, public-domain-trained voices) and the client chains them for any announcement text
(client/Voice.java). Numbers, times and platforms are spoken from number words; station names are covered by
place-name parts plus the words of every station name found in local saves.

  uv run --with piper-tts --with nbtlib python3 tools/gen_voice.py [--models DIR] [--names "Foo Bar,Baz"] [--no-saves]

Writes assets/irextras/sounds/voice/<voice>/<token>.ogg, assets/irextras/voice/<voice>.txt (token<TAB>ms) and the
tools/sound_fragments/voice.json (gen_soundpack.py merges it into sounds.json).
Voices (see CREDITS-SOUNDS.md): cori = en_GB-cori-high (LibriVox, public domain), lj = en_US-ljspeech-high
(LJ Speech, public domain); Piper voice models MIT.
"""
import argparse, glob, json, os, re, subprocess, sys, tempfile, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src/main/resources/assets/irextras')
SR = 44100
VOICES = {'cori': 'en_GB-cori-high', 'lj': 'en_US-ljspeech-high'}

NUMBERS = ('zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen '
           'seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred thousand oh '
           'first second third fourth fifth sixth seventh eighth ninth tenth half quarter').split()

# words of the announcement templates, PIS messages and station dialogue (incident/IncidentConfig, station/Pis, PA)
CORE = '''a about above after again all along also am an and announce any apologise apologies are area around arrival
arrivals arrive arriving as assembly at attention available away back be because been before behind being belongings
between board boarding both bus but by call calling calls can cancel cancelled cancellation carriage carriages
carriage change check close closed closing coach coaches come connecting connection connections continue cross
customer customers day delay delayed delays depart departing departs departure departures derailment destination do
doors down due during each early emergency end engineering exit expected express failed failure fire first follow for
formed from front gap go good have help here hold how if in information instructions into is it its keep late leave
line lines listen local luggage make may mind minute minutes more morning near nearest new next no not now of off on
one only operational or other over passenger passengers patience platform platforms please police posted
priority problem promptly rear reason reasons replacement report request resume return running safe safety security
seats served service services short shortly should signal signalling sorry staff stand stand standing station
stations stop stopping stops suspended suspicious system take terminate terminates terminating test than thank that
the their them there these they this through ticket tickets time to today tomorrow track train trains travel
traveling travelling unattended until up via wait waiting want we weather weekend welcome were what when where which
while will with work works yellow you your evening afternoon night attend attended items item left leaving right
side opposite carriages approaching arriving approach do not cross leave belongings unattended removed destroyed
'''.split()

# common English for operator-typed / custom template announcements
COMMON = '''able accept access across act action add address advice affected again against ahead air alarm alert
allow already always another answer anyone anything apply arrange ask assist assistance assistant avoid aware bag
bags be become begin best better big bike bikes book booked bottom break bring broken build busy buy cafe can card
care careful case cash catch caused certain chance children clean clear coming complete condition contact control
could crew crowded current customer dear delay details different direction directions disabled disruption disruptions
door doors driver drivers drop east easy eat enter entrance escalator escalators even ever every everyone everything
exact except extra fare fares fast few find fine floor full further get give given goods great green group guard
happy has he hear heavy her high him his home hot hour hours however ice important inside instead journey journeys
just kind know large last later least less level lift lifts like limited little live long look lost lower main
many maybe me member members message messages middle might mobile money most move much must my name needed never
north northbound note number office often old open opened opening operator order our out outside overhead own paid
part pay people person personal phone place placed plan point power present probably proceed provide public
quickly quiet rail railway ready real really red remain remember repair repairs required reserved rest route same
say seat see seen send set several she show sign signs single slowly small smoking so some someone something soon
south southbound speed start started step steps still storm street such sure taken talk tell temporary thing things
think those though three today together toilet toilets too top towards trolley trolleys trouble true try turn two
under understand unfortunately upper use used usual usually very visit walk walking warning was watch way well
west westbound eastbound who why window without word would year years yes yet young zone ten points
'''.split()

# parts of place / station names: covers most names players invent
PLACES = '''abbey airport alley bank barn bay beach bridge brook burgh bury castle central centre chapel church city
cliff coast college common corner court cove creek cross crossing dale dam dock docks downs east edge end estate
falls farm ferry field fields fort forest gardens gate glen grange green grove hall halt harbour harbor haven heath
heights hill hills hollow house island junction keep king kings lake lakes lane lea manor market marsh meadow
meadows mill mills mine mines moor mount mountain new north oak oaks old palace park parkway pass pier pine pines
place plains point pond port quarry queen queens quay rise river road rock rocks royal saint square stadium stone
street south spring springs station summit tower town university upper lower valley view village vale water way
well wells west wharf wood woods works yard yards abbey arena hospital museum terminal interchange parade bridge
north eastern western southern northern grand union great little long high low black white red green blue golden
silver iron copper coal steel sand sandy deep dark sun moon star lake spawn base camp outpost colony mesa desert
jungle snow ice frost cherry birch spruce willow maple ash elm cedar redwood ridge peak canyon gorge basin delta
'''.split()

# whole phrases (more natural than chained words); token = phrase with _
PHRASES = ['mind the gap', 'mind the doors', 'please mind the gap between the train and the platform',
           'please stand behind the yellow line', 'stand clear of the closing doors',
           'please keep your belongings with you at all times', 'please report anything suspicious to staff',
           'we are sorry to announce that', 'this is due to', 'the next train', 'is now approaching',
           'the train now arriving at', 'is the', 'will now depart from platform', 'platform alteration',
           'attention please', 'this is an emergency', 'thank you for your patience', 'services are running normally',
           'services are suspended', 'please follow the instructions of station staff', 'is ready to depart',
           'has been cancelled', 'is delayed by approximately', 'expected in', 'please change here',
           'do not return for belongings', 'by the nearest exit', 'go to the assembly point', 'the emergency is now over',
           'services will resume shortly', 'this is a test of the public address system', 'calling at',
           'this train terminates here', 'all change please', 'please take all your belongings with you',
           'the train standing at platform', 'operational reasons', 'a failed train', 'a derailment',
           'an emergency on the line', 'a fire near the line', 'an emergency evacuation', 'engineering works',
           'check before you travel', 'this weekend', 'please listen for further announcements',
           'we are sorry for the disruption to services at', 'this is the station master at',
           'your connection', 'is at risk', 'running late', 'minutes late', 'passengers for the',
           'passengers on the']

LETTERS = list('abcdefghijklmnopqrstuvwxyz')


def tok(s):
    return re.sub(r'[^a-z0-9]+', '_', s.lower()).strip('_')


def template_words():
    """every word in the mod's own announcement strings"""
    src = os.path.join(HERE, '..', 'src/main/java/com/dogpound/railmap')
    files = glob.glob(src + '/incident/*.java') + glob.glob(src + '/station/*.java') + [
        src + '/block/TilePaSpeaker.java', src + '/block/TileTravelCentre.java', src + '/block/TileArrivalsBoard.java']
    words = set()
    for f in files:
        try:
            s = open(f).read()
        except OSError:
            continue
        for lit in re.findall(r'"((?:[^"\\]|\\.)*)"', s):
            if ' ' not in lit or '§' in lit and len(lit) < 8:
                continue
            for w in re.findall(r"[A-Za-z]+", re.sub(r'\{[a-z]+}|§.', ' ', lit)):
                if 2 <= len(w) <= 14 and w.lower() == w or w[:1].isupper() and w[1:].islower():
                    words.add(w.lower())
    return words


def station_words(names):
    out = set()
    for n in names:
        for w in re.findall(r"[A-Za-z]+", n):
            if len(w) >= 2:
                out.add(w.lower())
    return out


def saved_station_names():
    """station names from every local save (PrismLauncher instances): their words get recorded too"""
    names = set()
    try:
        import nbtlib
    except ImportError:
        print('  (nbtlib missing: no station names from saves)')
        return names
    pats = [os.path.expanduser('~/.local/share/PrismLauncher/instances/*/minecraft/saves/*/data/railmap_stations.dat')]
    for p in pats:
        for f in glob.glob(p):
            try:
                d = nbtlib.load(f)
                def walk(x):
                    if isinstance(x, dict):
                        for k, v in x.items():
                            if k == 'n' and isinstance(v, str):
                                names.add(str(v))
                            walk(v)
                    elif isinstance(x, list):
                        for v in x:
                            walk(v)
                walk(d)
            except Exception as e:  # a damaged save: skip it
                print('  skip', f, e)
    return names


def pa_colour(wav_in, ogg_out):
    """trim silence, 44.1 kHz, gentle PA-horn band shaping, Vorbis"""
    flt = ('silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.01,'
           'areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.03,areverse,'
           'highpass=f=150,lowpass=f=7500,equalizer=f=2500:t=q:w=1.2:g=3,'
           'aresample=44100,alimiter=limit=0.9')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav_in, '-af', flt, '-ac', '1', '-c:a', 'libvorbis',
                    '-q:a', '5', ogg_out], check=True)
    r = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', ogg_out],
                       capture_output=True, text=True)
    return int(float(r.stdout.strip() or 0) * 1000)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--models', default=os.environ.get('PIPER_MODELS', os.path.expanduser('~/.cache/piper-voices')))
    ap.add_argument('--names', default='', help='extra station names, comma separated')
    ap.add_argument('--no-saves', action='store_true')
    ap.add_argument('--voices', default='cori,lj')
    a = ap.parse_args()
    from piper import PiperVoice, SynthesisConfig

    words = set(NUMBERS) | set(CORE) | set(COMMON) | set(PLACES) | template_words()
    names = set(n.strip() for n in a.names.split(',') if n.strip())
    if not a.no_saves:
        names |= saved_station_names()
    words |= station_words(names)
    words = sorted(w for w in words if re.fullmatch(r'[a-z]+', w))
    print(len(words), 'words,', len(PHRASES), 'phrases,', len(names), 'station names')

    sj = {}
    os.makedirs(os.path.join(ROOT, 'voice'), exist_ok=True)
    for v in a.voices.split(','):
        model = os.path.join(a.models, VOICES[v] + '.onnx')
        if not os.path.exists(model):                       # fetch the voice once (rhasspy/piper-voices, MIT)
            import urllib.request
            os.makedirs(a.models, exist_ok=True)
            lang, name, q = VOICES[v].split('-')
            base = f'https://huggingface.co/rhasspy/piper-voices/resolve/main/{lang[:2]}/{lang}/{name}/{q}/{VOICES[v]}.onnx'
            for ext in ('', '.json'):
                urllib.request.urlretrieve(base + ext, model + ext)
        voice = PiperVoice.load(model)
        out = os.path.join(ROOT, 'sounds', 'voice', v)
        os.makedirs(out, exist_ok=True)
        index = []
        jobs = [(w, w) for w in words] + [(tok(p), p) for p in PHRASES] + [('letter_' + c, c.upper() + '.') for c in LETTERS]
        for i, (t, text) in enumerate(jobs):
            # isolated words get a full stop's falling tone; slightly slow, clear PA delivery
            cfg = SynthesisConfig(length_scale=1.05 if ' ' in text else 1.0, noise_scale=0.5, noise_w_scale=0.6)
            pcm = b''.join(c.audio_int16_bytes for c in voice.synthesize(text + ('' if text.endswith('.') else '.'), cfg))
            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
                w = wave.open(tf.name, 'wb')
                w.setnchannels(1); w.setsampwidth(2); w.setframerate(voice.config.sample_rate)
                w.writeframes(pcm); w.close()
                ms = pa_colour(tf.name, os.path.join(out, t + '.ogg'))
                os.unlink(tf.name)
            index.append(f'{t}\t{ms}')
            sj[f'voice.{v}.{t}'] = {'subtitle': 'irextras.sub.voice',
                                    'sounds': [{'name': f'irextras:voice/{v}/{t}', 'stream': False}]}
            if i % 200 == 0:
                print(f'  {v}: {i}/{len(jobs)}', flush=True)
        open(os.path.join(ROOT, 'voice', v + '.txt'), 'w').write('\n'.join(index) + '\n')
    os.makedirs(os.path.join(HERE, 'sound_fragments'), exist_ok=True)
    json.dump(sj, open(os.path.join(HERE, 'sound_fragments', 'voice.json'), 'w'), indent=1)   # gen_soundpack.py merges it
    print('done')


if __name__ == '__main__':
    main()
