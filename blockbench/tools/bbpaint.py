"""Paint crisp details onto a Blockbench .bbmodel's texture, face by face.

A face is addressed by cube name + direction; coordinates are in that face's own pixels,
(0,0) = top-left as seen from outside the model. Blockbench's smooth base coat stays; this
adds the things a base coat can't: screens, keys, seams, rivets, lettering.
"""
import base64, io, json, random
from PIL import Image, ImageDraw

FONT = {  # 5x7 pixel font, rows top->bottom
 'A':["01110","10001","10001","11111","10001","10001","10001"], 'B':["11110","10001","11110","10001","10001","10001","11110"],
 'C':["01111","10000","10000","10000","10000","10000","01111"], 'D':["11110","10001","10001","10001","10001","10001","11110"],
 'E':["11111","10000","11110","10000","10000","10000","11111"], 'F':["11111","10000","11110","10000","10000","10000","10000"],
 'G':["01111","10000","10000","10011","10001","10001","01111"], 'H':["10001","10001","11111","10001","10001","10001","10001"],
 'I':["11111","00100","00100","00100","00100","00100","11111"], 'K':["10001","10010","11100","10010","10001","10001","10001"],
 'L':["10000","10000","10000","10000","10000","10000","11111"], 'M':["10001","11011","10101","10001","10001","10001","10001"],
 'N':["10001","11001","10101","10011","10001","10001","10001"], 'O':["01110","10001","10001","10001","10001","10001","01110"],
 'P':["11110","10001","10001","11110","10000","10000","10000"], 'R':["11110","10001","10001","11110","10100","10010","10001"],
 'S':["01111","10000","10000","01110","00001","00001","11110"], 'T':["11111","00100","00100","00100","00100","00100","00100"],
 'U':["10001","10001","10001","10001","10001","10001","01110"], 'V':["10001","10001","10001","10001","10001","01010","00100"],
 'W':["10001","10001","10001","10101","10101","11011","10001"], 'X':["10001","01010","00100","00100","00100","01010","10001"],
 'Y':["10001","01010","00100","00100","00100","00100","00100"], ' ':["00000"]*7,
 '0':["01110","10011","10101","10101","11001","10001","01110"], '1':["00100","01100","00100","00100","00100","00100","01110"],
 '2':["01110","10001","00001","00110","01000","10000","11111"], '3':["11110","00001","00001","01110","00001","00001","11110"],
 '4':["00010","00110","01010","10010","11111","00010","00010"], '5':["11111","10000","11110","00001","00001","10001","01110"],
 '6':["01110","10000","11110","10001","10001","10001","01110"], '7':["11111","00001","00010","00100","01000","01000","01000"],
 '8':["01110","10001","01110","10001","10001","10001","01110"], '9':["01110","10001","10001","01111","00001","00001","01110"],
 '-':["00000","00000","00000","11111","00000","00000","00000"], '.':["00000"]*6+["00100"], ':':["00000","00100","00000","00000","00000","00100","00000"],
 '>':["10000","01000","00100","00010","00100","01000","10000"], '$':["00100","01111","10100","01110","00101","11110","00100"],
}

class Model:
    def __init__(self, path):
        self.path = path
        self.m = json.load(open(path))
        src = self.m['textures'][0]['source'].split(',', 1)[1]
        self.img = Image.open(io.BytesIO(base64.b64decode(src))).convert('RGBA')
        self.d = ImageDraw.Draw(self.img)
        self.el = {e['name']: e for e in self.m['elements']}
        self.mirror = {}          # per (cube, face): flip u if the face reads mirrored

    def rect_of(self, cube, face):
        u0, v0, u1, v1 = self.el[cube]['faces'][face]['uv']
        return int(min(u0, u1)), int(min(v0, v1)), int(max(u0, u1)), int(max(v0, v1))

    def size(self, cube, face):
        x0, y0, x1, y1 = self.rect_of(cube, face)
        return x1 - x0, y1 - y0

    # ---- drawing in face coordinates ----
    def _map(self, cube, face, x, y):
        x0, y0, x1, y1 = self.rect_of(cube, face)
        return x0 + x, y0 + y

    def fill(self, cube, face, color):
        x0, y0, x1, y1 = self.rect_of(cube, face)
        self.d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=color)

    def rect(self, cube, face, x, y, w, h, color):
        X0, Y0, X1, Y1 = self.rect_of(cube, face)
        a, b = X0 + x, Y0 + y
        a2, b2 = min(X1, a + w) - 1, min(Y1, b + h) - 1
        if a2 >= a and b2 >= b:
            self.d.rectangle([a, b, a2, b2], fill=color)

    def vgrad(self, cube, face, top, bottom, x=0, y=0, w=None, h=None):
        W, H = self.size(cube, face)
        w = W if w is None else w; h = H if h is None else h
        t = [int(top[i:i+2], 16) for i in (1, 3, 5)]; b = [int(bottom[i:i+2], 16) for i in (1, 3, 5)]
        for r in range(h):
            k = r / max(1, h - 1)
            c = tuple(int(t[i] + (b[i] - t[i]) * k) for i in range(3))
            self.rect(cube, face, x, y + r, w, 1, c)

    def text(self, cube, face, s, x, y, color, scale=1):
        cx = x
        for ch in s.upper():
            g = FONT.get(ch, FONT[' '])
            for r, row in enumerate(g):
                for c, bit in enumerate(row):
                    if bit == '1':
                        self.rect(cube, face, cx + c * scale, y + r * scale, scale, scale, color)
            cx += (len(g[0]) + 1) * scale
        return cx - x

    def text_width(self, s, scale=1):
        return len(s) * 6 * scale - scale

    def ctext(self, cube, face, s, y, color, scale=1):
        W, _ = self.size(cube, face)
        self.text(cube, face, s, (W - self.text_width(s, scale)) // 2, y, color, scale)

    def rivets(self, cube, face, color, inset=2, step=None):
        W, H = self.size(cube, face)
        pts = [(inset, inset), (W - inset - 1, inset), (inset, H - inset - 1), (W - inset - 1, H - inset - 1)]
        for px, py in pts:
            self.rect(cube, face, px, py, 1, 1, color)

    def hline(self, cube, face, y, color, x=0, w=None):
        W, _ = self.size(cube, face)
        self.rect(cube, face, x, y, W - x if w is None else w, 1, color)

    def vline(self, cube, face, x, color, y=0, h=None):
        _, H = self.size(cube, face)
        self.rect(cube, face, x, y, 1, H - y if h is None else h, color)

    def streaks(self, cube, face, dark, light, n=12, seed=1):
        W, H = self.size(cube, face)
        rnd = random.Random(seed)
        for _ in range(n):
            self.rect(cube, face, rnd.randrange(W), 0, 1, H, rnd.choice([dark, light]))

    def save(self, png_path):
        self.img.save(png_path)
        buf = io.BytesIO(); self.img.save(buf, 'PNG')
        self.m['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
        json.dump(self.m, open(self.path, 'w'))
