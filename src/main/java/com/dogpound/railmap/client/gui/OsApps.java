package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.os.OsLogic;
import com.dogpound.railmap.os.PacketOs;
import com.dogpound.railmap.os.PrideOs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Pride OS apps (requested feature). Each app draws itself
 * immediate-mode inside its window; buttons register a hit box while drawing, and a click on one calls button(id).
 */
public final class OsApps {
    private OsApps() {}

    static final Random RNG = new Random();

    static final class Hit {
        final String id; final int x, y, w, h;
        Hit(String id, int x, int y, int w, int h) { this.id = id; this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    // ---- the base every app shares --------------------------------------------------------------------------------------
    public abstract static class App {
        final GuiPrideOs os;
        final String title;
        final int w, h;
        int x, y;                                   // content origin on screen (set by the window each frame)
        final List<Hit> hits = new ArrayList<>();

        App(GuiPrideOs os, String title, int w, int h) { this.os = os; this.title = title; this.w = w; this.h = h; }

        abstract void draw(int mx, int my);
        void button(String id, int b) {}
        void area(int rx, int ry, int b) {}
        void drag(int rx, int ry, int b) {}
        boolean key(char c, int k) { return false; }
        void tick() {}
        void wheel(int d) {}
        void closed() {}

        final void click(int mx, int my, int b) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x && my >= h.y && mx < h.x + h.w && my < h.y + h.h) { os.sfx("ui.click", 1.2f); button(h.id, b); return; }
            }
            area(mx - x, my - y, b);
        }

        FontRenderer fr() { return Minecraft.getMinecraft().fontRenderer; }
        void rect(int rx, int ry, int rw, int rh, int col) { Gui.drawRect(x + rx, y + ry, x + rx + rw, y + ry + rh, col); }
        void text(String s, int rx, int ry, int col) { fr().drawStringWithShadow(s, x + rx, y + ry, col); }
        void textC(String s, int rx, int ry, int col) { fr().drawStringWithShadow(s, x + rx - fr().getStringWidth(s) / 2f, y + ry, col); }

        boolean btn(String id, int rx, int ry, int bw, int bh, String label, int col, int mx, int my) {
            hits.add(new Hit(id, x + rx, y + ry, bw, bh));
            return PrideFrame.button(x + rx, y + ry, bw, bh, label, col, mx, my);
        }
    }

    static boolean in(int rx, int ry, int x, int y, int w, int h) { return rx >= x && ry >= y && rx < x + w && ry < y + h; }

    static boolean enter(int k) { return k == Keyboard.KEY_RETURN || k == Keyboard.KEY_NUMPADENTER; }

    // ---- Notepad --------------------------------------------------------------------------------------------------------
    static final class Notepad extends App {
        final List<String[]> notes;
        int sel, cur, scroll;
        boolean dirty;
        private final List<int[]> lines = new ArrayList<>();   // visual lines: [start, end) in the text
        private int ex, ew, eh;

        Notepad(GuiPrideOs os) {
            super(os, "Notepad", 300, 180);
            notes = OsLogic.notesFromNbt(os.st.getTagList("notes", 10));
            sel = notes.isEmpty() ? -1 : 0;
            cur = sel < 0 ? 0 : notes.get(0)[1].length();
        }

        String body() { return sel < 0 ? "" : notes.get(sel)[1]; }

        void setBody(String b) {
            notes.get(sel)[1] = b;
            String first = b.split("\n", 2)[0].trim();
            notes.get(sel)[0] = first.length() > 40 ? first.substring(0, 40) : first;
            dirty = true;
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            NBTTagList l = OsLogic.notesToNbt(notes);
            os.st.setTag("notes", l.copy());
            NBTTagCompound d = new NBTTagCompound();
            d.setTag("v", l);
            os.save("notes", d);
        }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            int acc = os.accent();
            btn("new", 0, 0, 30, 12, "New", PrideFrame.BUTTON, mx, my);
            btn("del", 32, 0, 30, 12, "Del", PrideFrame.BUTTON, mx, my);
            btn("save", 64, 0, 34, 12, dirty ? "Save*" : "Save", PrideFrame.BUTTON, mx, my);
            btn("print", 100, 0, 36, 12, "Print", PrideFrame.BUTTON, mx, my);
            text("§7" + body().length() + "/" + 2000, 142, 2, 0xFFFFFF);
            rect(0, 15, 72, h - 15, 0x60000000);
            for (int i = 0; i < notes.size(); i++) {
                String t = notes.get(i)[0].isEmpty() ? "Untitled" : notes.get(i)[0];
                if (i == sel) rect(0, 16 + i * 11, 72, 11, acc & 0x80FFFFFF);
                hits.add(new Hit("n" + i, x, y + 16 + i * 11, 72, 11));
                text(fr().trimStringToWidth(t, 68), 2, 18 + i * 11, i == sel ? 0xFFFFFF : 0xBBBBBB);
            }
            ex = 76; ew = w - 76; eh = h - 15;
            rect(ex, 15, ew, eh, 0xFF0E0A18);
            if (sel < 0) { text("§7No notes - press New", ex + 4, 19, 0xFFFFFF); return; }
            layout();
            int curLine = lineOf(cur), rows = (eh - 4) / 9;
            if (curLine < scroll) scroll = curLine;
            if (curLine >= scroll + rows) scroll = curLine - rows + 1;
            String b = body();
            for (int r = 0; r < rows && scroll + r < lines.size(); r++) {
                int[] L = lines.get(scroll + r);
                text(b.substring(L[0], L[1]), ex + 3, 18 + r * 9, 0xEEEEEE);
            }
            if (os.blink() && curLine >= scroll && curLine < scroll + rows) {
                int[] L = lines.get(curLine);
                int cx = ex + 3 + fr().getStringWidth(b.substring(L[0], Math.min(cur, L[1])));
                rect(cx, 17 + (curLine - scroll) * 9, 1, 9, 0xFFFFFFFF);
            }
        }

        private void layout() {
            lines.clear();
            String b = body();
            int maxW = ew - 8, start = 0;
            while (true) {
                int nl = b.indexOf('\n', start), end = nl < 0 ? b.length() : nl, ls = start, wsum = 0;
                for (int i = start; i < end; i++) {
                    int cw = fr().getCharWidth(b.charAt(i));
                    if (wsum + cw > maxW && i > ls) { lines.add(new int[]{ls, i}); ls = i; wsum = 0; }
                    wsum += cw;
                }
                lines.add(new int[]{ls, end});
                if (nl < 0) break;
                start = nl + 1;
            }
        }

        private int lineOf(int c) {
            for (int i = lines.size() - 1; i >= 0; i--) if (c >= lines.get(i)[0]) return i;
            return 0;
        }

        @Override
        void button(String id, int b) {
            switch (id) {
                case "new":
                    if (notes.size() >= 12) { os.sfx("ui.beep_error", 1f); return; }
                    flush();
                    notes.add(new String[]{"", ""});
                    sel = notes.size() - 1; cur = 0; scroll = 0; dirty = true;
                    break;
                case "del":
                    if (sel < 0) return;
                    notes.remove(sel);
                    sel = Math.min(sel, notes.size() - 1); cur = body().length(); scroll = 0; dirty = true;
                    flush();
                    break;
                case "save": dirty = true; flush(); os.toast("Saved"); break;
                case "print":
                    if (sel < 0) return;
                    flush();
                    NBTTagCompound d = new NBTTagCompound();
                    d.setInteger("n", sel);
                    os.send(PacketOs.BOOK, "", d);
                    break;
                default:
                    if (id.startsWith("n")) { flush(); sel = Integer.parseInt(id.substring(1)); cur = body().length(); scroll = 0; }
            }
        }

        @Override
        void area(int rx, int ry, int b) {
            if (sel < 0 || !in(rx, ry, ex, 15, ew, eh) || lines.isEmpty()) return;
            int line = Math.min(lines.size() - 1, scroll + Math.max(0, (ry - 18) / 9));
            int[] L = lines.get(line);
            String s = body();
            int c = L[0];
            while (c < L[1] && fr().getStringWidth(s.substring(L[0], c + 1)) < rx - ex - 3) c++;
            cur = c;
        }

        @Override
        boolean key(char c, int k) {
            if (sel < 0) return false;
            String b = body();
            cur = Math.max(0, Math.min(cur, b.length()));
            if (GuiScreen.isKeyComboCtrlV(k)) { insert(GuiScreen.getClipboardString().replace("\r", "")); return true; }
            switch (k) {
                case Keyboard.KEY_BACK: if (cur > 0) { setBody(b.substring(0, cur - 1) + b.substring(cur)); cur--; } return true;
                case Keyboard.KEY_DELETE: if (cur < b.length()) setBody(b.substring(0, cur) + b.substring(cur + 1)); return true;
                case Keyboard.KEY_LEFT: cur = Math.max(0, cur - 1); return true;
                case Keyboard.KEY_RIGHT: cur = Math.min(b.length(), cur + 1); return true;
                case Keyboard.KEY_HOME: cur = lines.isEmpty() ? 0 : lines.get(lineOf(cur))[0]; return true;
                case Keyboard.KEY_END: cur = lines.isEmpty() ? b.length() : lines.get(lineOf(cur))[1]; return true;
                case Keyboard.KEY_UP: case Keyboard.KEY_DOWN: {
                    if (lines.isEmpty()) return true;
                    int l = lineOf(cur), t = l + (k == Keyboard.KEY_UP ? -1 : 1);
                    if (t < 0 || t >= lines.size()) return true;
                    int col = cur - lines.get(l)[0];
                    cur = Math.min(lines.get(t)[0] + col, lines.get(t)[1]);
                    return true;
                }
                default:
            }
            if (enter(k)) { insert("\n"); return true; }
            if (ChatAllowedCharacters.isAllowedCharacter(c)) { insert(String.valueOf(c)); return true; }
            return false;
        }

        private void insert(String s) {
            String b = body();
            s = s.substring(0, Math.max(0, Math.min(s.length(), 2000 - b.length())));
            if (s.isEmpty()) { os.sfx("ui.beep_error", 1f); return; }
            setBody(b.substring(0, cur) + s + b.substring(cur));
            cur += s.length();
        }

        @Override void wheel(int d) { scroll = Math.max(0, scroll - Integer.signum(d)); }
        @Override void closed() { flush(); }
    }

    // ---- Calculator ----------------------------------------------------------------------------------------------------
    static final class Calc extends App {
        String expr = "", shown = "0";
        boolean done;
        static final String[] KEYS = {"7", "8", "9", "/", "4", "5", "6", "*", "1", "2", "3", "-", "0", ".", "=", "+", "(", ")", "^", "%", "C", "<", "", ""};

        Calc(GuiPrideOs os) { super(os, "Calculator", 112, 150); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            rect(0, 0, w, 28, 0xFF0B1A10);
            text("§7" + fr().trimStringToWidth(expr, w - 6), 3, 3, 0xFFFFFF);
            String s = shown;
            text(s, w - 3 - fr().getStringWidth(s), 16, 0x7CF29A);
            for (int i = 0; i < KEYS.length; i++) {
                if (KEYS[i].isEmpty()) continue;
                int col = "=".equals(KEYS[i]) ? os.accent() : "C<".contains(KEYS[i]) ? 0xFF6A2030 : "/*-+^%()".contains(KEYS[i]) ? 0xFF33304A : PrideFrame.BUTTON;
                btn(KEYS[i], (i % 4) * 28, 32 + (i / 4) * 20, 26, 18, KEYS[i], col, mx, my);
            }
        }

        @Override
        void button(String id, int b) { press(id); }

        void press(String k) {
            if (k.equals("C")) { expr = ""; shown = "0"; done = false; return; }
            if (k.equals("<")) { if (!expr.isEmpty()) expr = expr.substring(0, expr.length() - 1); return; }
            if (k.equals("=")) {
                try { shown = OsLogic.show(OsLogic.calc(expr)); if (shown.equals("Error")) os.sfx("ui.beep_error", 1f); }
                catch (IllegalArgumentException e) { shown = "Error"; os.sfx("ui.beep_error", 1f); }
                done = true;
                return;
            }
            if (done) { expr = Character.isDigit(k.charAt(0)) || k.equals(".") || k.equals("(") || shown.equals("Error") ? "" : shown; done = false; }
            if (expr.length() < 60) expr += k;
        }

        @Override
        boolean key(char c, int k) {
            if (enter(k) || c == '=') { press("="); return true; }
            if (k == Keyboard.KEY_BACK) { press("<"); return true; }
            if (k == Keyboard.KEY_DELETE || c == 'c' || c == 'C') { press("C"); return true; }
            if ("0123456789.+-*/()^%".indexOf(c) >= 0) { press(String.valueOf(c)); return true; }
            return false;
        }
    }

    // ---- Paint ----------------------------------------------------------------------------------------------------------
    static final class Paint extends App {
        static final int N = 32, PX = 5;
        final List<byte[]> pics = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        int sel, color = 3;
        boolean fill, dirty;

        Paint(GuiPrideOs os) {
            super(os, "Paint", 214, 176);
            NBTTagList l = os.st.getTagList("draws", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                byte[] p = l.getCompoundTagAt(i).getByteArray("p");
                pics.add(p.length == N * N ? p.clone() : new byte[N * N]);
                names.add(l.getCompoundTagAt(i).getString("n"));
            }
            if (pics.isEmpty()) { pics.add(new byte[N * N]); names.add("Drawing 1"); }
        }

        static int rgb(int c) { return 0xFF000000 | PrideOs.PALETTE[c & 15].colorValue; }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            btn("prev", 0, 0, 12, 12, "<", PrideFrame.BUTTON, mx, my);
            btn("next", 14, 0, 12, 12, ">", PrideFrame.BUTTON, mx, my);
            text((sel + 1) + "/" + pics.size(), 30, 2, 0xFFFFFF);
            btn("new", 56, 0, 28, 12, "New", PrideFrame.BUTTON, mx, my);
            btn("save", 86, 0, 34, 12, dirty ? "Save*" : "Save", PrideFrame.BUTTON, mx, my);
            btn("del", 122, 0, 28, 12, "Del", PrideFrame.BUTTON, mx, my);
            btn("map", 152, 0, 30, 12, "Map", os.accent(), mx, my);
            byte[] p = pics.get(sel);
            rect(-1, 15, N * PX + 2, N * PX + 2, 0xFF000000);
            for (int i = 0; i < N * N; i++) rect((i % N) * PX, 16 + (i / N) * PX, PX, PX, rgb(p[i]));
            for (int c = 0; c < 16; c++) {
                int px = 166 + (c % 2) * 12, py = 16 + (c / 2) * 12;
                if (c == color) rect(px - 1, py - 1, 12, 12, 0xFFFFFFFF);
                rect(px, py, 10, 10, rgb(c));
                hits.add(new Hit("c" + c, x + px, y + py, 10, 10));
            }
            btn("pen", 166, 116, 46, 12, fill ? "Pen" : "§lPen", fill ? PrideFrame.BUTTON : os.accent(), mx, my);
            btn("fill", 166, 130, 46, 12, fill ? "§lFill" : "Fill", fill ? os.accent() : PrideFrame.BUTTON, mx, my);
            btn("clear", 166, 144, 46, 12, "Clear", 0xFF6A2030, mx, my);
            text("§7R-click", 166, 160, 0xFFFFFF);
            text("§7= pick", 166, 168, 0xFFFFFF);
        }

        @Override
        void button(String id, int b) {
            switch (id) {
                case "prev": sel = (sel + pics.size() - 1) % pics.size(); break;
                case "next": sel = (sel + 1) % pics.size(); break;
                case "new":
                    if (pics.size() >= 8) { os.sfx("ui.beep_error", 1f); return; }
                    pics.add(new byte[N * N]); names.add("Drawing " + pics.size()); sel = pics.size() - 1; dirty = true; break;
                case "save": dirty = true; flush(); os.toast("Saved"); break;
                case "del":
                    pics.remove(sel); names.remove(sel);
                    if (pics.isEmpty()) { pics.add(new byte[N * N]); names.add("Drawing 1"); }
                    sel = Math.min(sel, pics.size() - 1); dirty = true; flush(); break;
                case "map": {
                    dirty = true; flush();
                    NBTTagCompound d = new NBTTagCompound();
                    d.setInteger("n", sel);
                    os.send(PacketOs.MAP, "", d);
                    break;
                }
                case "pen": fill = false; break;
                case "fill": fill = true; break;
                case "clear": java.util.Arrays.fill(pics.get(sel), (byte) 0); dirty = true; break;
                default: if (id.startsWith("c")) color = Integer.parseInt(id.substring(1));
            }
        }

        @Override void area(int rx, int ry, int b) { paint(rx, ry, b, true); }
        @Override void drag(int rx, int ry, int b) { if (!fill) paint(rx, ry, b, false); }

        private void paint(int rx, int ry, int b, boolean click) {
            int cx = rx / PX, cy = (ry - 16) / PX;
            if (rx < 0 || ry < 16 || cx >= N || cy >= N) return;
            byte[] p = pics.get(sel);
            int i = cy * N + cx;
            if (b == 1) { color = p[i]; return; }
            if (fill && click) { flood(p, i, p[i], (byte) color); os.sfx("ui.click", 0.7f); }
            else if (!fill) p[i] = (byte) color;
            dirty = true;
        }

        private static void flood(byte[] p, int start, byte from, byte to) {
            if (from == to) return;
            java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
            q.add(start);
            while (!q.isEmpty()) {
                int i = q.poll();
                if (p[i] != from) continue;
                p[i] = to;
                int x = i % N, y = i / N;
                if (x > 0) q.add(i - 1);
                if (x < N - 1) q.add(i + 1);
                if (y > 0) q.add(i - N);
                if (y < N - 1) q.add(i + N);
            }
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            NBTTagList l = new NBTTagList();
            for (int i = 0; i < pics.size(); i++) {
                NBTTagCompound t = new NBTTagCompound();
                t.setString("n", names.get(i));
                t.setByteArray("p", pics.get(i).clone());
                l.appendTag(t);
            }
            os.st.setTag("draws", l.copy());
            NBTTagCompound d = new NBTTagCompound();
            d.setTag("v", l);
            os.save("draws", d);
        }

        @Override void closed() { flush(); }
    }

    // ---- Snake ----------------------------------------------------------------------------------------------------------
    static final class SnakeApp extends App {
        OsLogic.Snake g;
        int t, speed = 3;
        boolean run;

        SnakeApp(GuiPrideOs os) { super(os, "Snake", 160, 128); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            int best = os.hs().getInteger("snake");
            text("Score " + (g == null ? 0 : g.score) + "  §7Best " + best, 0, 2, 0xFFFFFF);
            btn("go", w - 40, 0, 40, 12, g == null || g.dead ? "Start" : run ? "Pause" : "Go", os.accent(), mx, my);
            rect(0, 15, 160, 112, 0xFF07140A);
            if (g == null) { textC("§7Arrows / WASD", 80, 66, 0xFFFFFF); return; }
            for (int c : g.body) rect((c % 20) * 8, 15 + (c / 20) * 8, 8, 8, c == g.body.peekFirst() ? 0xFF7CF29A : 0xFF30B050);
            if (g.food >= 0) rect((g.food % 20) * 8 + 1, 15 + (g.food / 20) * 8 + 1, 6, 6, 0xFFE40303);
            if (g.dead) textC("§cGAME OVER", 80, 66, 0xFFFFFF);
        }

        @Override
        void button(String id, int b) {
            if (g == null || g.dead) { g = new OsLogic.Snake(20, 14); g.placeFood(RNG); run = true; t = 0; }
            else run = !run;
        }

        @Override
        void tick() {
            if (g == null || g.dead || !run || ++t % Math.max(1, speed - g.score / 10) != 0) return;
            g.step(RNG);
            if (g.dead) {
                os.sfx("ui.beep_error", 0.8f);
                if (g.score > os.hs().getInteger("snake")) { os.hs().setInteger("snake", g.score); os.saveHs(); os.toast("New best: " + g.score); }
            }
        }

        @Override
        boolean key(char c, int k) {
            if (g == null) return false;
            int d = k == Keyboard.KEY_UP || c == 'w' ? 0 : k == Keyboard.KEY_RIGHT || c == 'd' ? 1 : k == Keyboard.KEY_DOWN || c == 's' ? 2 : k == Keyboard.KEY_LEFT || c == 'a' ? 3 : -1;
            if (d < 0) return false;
            g.turn(d);
            return true;
        }
    }

    // ---- Minesweeper ----------------------------------------------------------------------------------------------------
    static final class MinesApp extends App {
        OsLogic.Mines g = new OsLogic.Mines(9, 9, 10);
        long start;
        int secs;

        MinesApp(GuiPrideOs os) { super(os, "Minesweeper", 108, 136); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            int flags = 0;
            for (boolean f : g.flag) if (f) flags++;
            text("§c" + (10 - flags), 0, 3, 0xFFFFFF);
            btn("new", 40, 0, 28, 12, g.dead ? "x(" : g.won ? "B)" : ":)", os.accent(), mx, my);
            text(String.valueOf(secs), w - fr().getStringWidth(String.valueOf(secs)), 3, 0xFFFFFF);
            int best = os.hs().getInteger("mines");
            for (int i = 0; i < 81; i++) {
                int cx = (i % 9) * 12, cy = 16 + (i / 9) * 12;
                if (!g.open[i]) {
                    rect(cx, cy, 11, 11, 0xFF4A4060);
                    rect(cx, cy, 11, 1, 0xFF7A70A0);
                    if (g.flag[i]) text("§cF", cx + 3, cy + 2, 0xFFFFFF);
                    if (g.dead && g.mine[i] && !g.flag[i]) text("*", cx + 3, cy + 2, 0xFFFFFF);
                } else {
                    rect(cx, cy, 11, 11, g.mine[i] ? 0xFFB02020 : 0xFF1C1828);
                    int n = g.mine[i] ? -1 : g.near(i);
                    if (n != 0) text(n < 0 ? "*" : String.valueOf(n), cx + 3, cy + 2, n < 0 ? 0xFFFFFF : NUM[n]);
                }
            }
            if (best > 0) text("§7Best " + best + "s", 0, 126, 0xFFFFFF);
        }

        static final int[] NUM = {0, 0x5BCEFA, 0x7CF29A, 0xFF6060, 0xC080FF, 0xFFB020, 0x40E0D0, 0xFFFFFF, 0xAAAAAA};

        @Override void button(String id, int b) { g = new OsLogic.Mines(9, 9, 10); secs = 0; start = 0; }

        @Override
        void area(int rx, int ry, int b) {
            if (g.dead || g.won || ry < 16 || rx < 0 || rx >= 108 || ry >= 16 + 108) return;
            int i = ((ry - 16) / 12) * 9 + rx / 12;
            if (b == 1) { if (!g.open[i]) g.flag[i] = !g.flag[i]; return; }
            if (start == 0) start = System.currentTimeMillis();
            g.reveal(i, RNG);
            if (g.dead) os.sfx("ui.beep_error", 0.7f);
            if (g.won) {
                os.sfx("ui.beep_ok", 1f);
                int best = os.hs().getInteger("mines");
                if (best == 0 || secs < best) { os.hs().setInteger("mines", Math.max(1, secs)); os.saveHs(); os.toast("New best: " + Math.max(1, secs) + " s"); }
            }
        }

        @Override void tick() { if (start > 0 && !g.dead && !g.won) secs = (int) ((System.currentTimeMillis() - start) / 1000); }
    }

    // ---- 2048 -----------------------------------------------------------------------------------------------------------
    static final class G2048 extends App {
        int[][] g = new int[4][4];
        int score;
        boolean over;

        G2048(GuiPrideOs os) { super(os, "2048", 127, 146); reset(); }

        void reset() { g = new int[4][4]; score = 0; over = false; OsLogic.spawn2048(g, RNG); OsLogic.spawn2048(g, RNG); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            text(score + " §7best " + os.hs().getInteger("2048"), 0, 3, 0xFFFFFF);
            btn("new", w - 28, 0, 28, 12, "New", os.accent(), mx, my);
            rect(0, 16, 127, 127, 0xFF2A2238);
            for (int yy = 0; yy < 4; yy++) for (int xx = 0; xx < 4; xx++) {
                int v = g[yy][xx], px = 3 + xx * 31, py = 19 + yy * 31;
                rect(px, py, 28, 28, v == 0 ? 0xFF3A3250 : tile(v));
                if (v > 0) { String s = String.valueOf(v); textC(s, px + 14, py + 10, v <= 4 ? 0x332040 : 0xFFFFFF); }
            }
            if (over) textC("§lGAME OVER", 63, 74, 0xFFFFFF);
        }

        static int tile(int v) {
            int[] c = {0xFFEEE4DA, 0xFFEDE0C8, 0xFFF5A9B8, 0xFFF08CA8, 0xFFE070C0, 0xFFB060D0, 0xFF8060E0, 0xFF5BCEFA, 0xFF40B0E0, 0xFF30D090, 0xFFFFD020};
            int k = Integer.numberOfTrailingZeros(v) - 1;
            return c[Math.max(0, Math.min(c.length - 1, k))];
        }

        @Override void button(String id, int b) { reset(); }

        @Override
        boolean key(char c, int k) {
            int d = k == Keyboard.KEY_LEFT || c == 'a' ? 0 : k == Keyboard.KEY_RIGHT || c == 'd' ? 1 : k == Keyboard.KEY_UP || c == 'w' ? 2 : k == Keyboard.KEY_DOWN || c == 's' ? 3 : -1;
            if (d < 0 || over) return d >= 0;
            int got = OsLogic.move2048(g, d);
            if (got < 0) return true;
            score += got;
            OsLogic.spawn2048(g, RNG);
            os.sfx("ui.click", 1.5f);
            if (!OsLogic.canMove2048(g)) { over = true; os.sfx("ui.beep_error", 0.8f); }
            if (score > os.hs().getInteger("2048") && (over || got > 0)) { os.hs().setInteger("2048", score); if (over) os.saveHs(); }
            return true;
        }

        @Override void closed() { if (score > 0) os.saveHs(); }
    }

    // ---- Solitaire (Klondike, draw one) --------------------------------------------------------------------------------
    static final class Solitaire extends App {
        static final int CW = 26, CH = 36, GAP = 30, TY = 44;
        OsLogic.Klondike k = new OsLogic.Klondike(RNG);
        int selSrc = -1, selIdx = -1;
        boolean won;
        static final String RANKS = "A23456789TJQK", SUITS = "♠♥♦♣";

        Solitaire(GuiPrideOs os) { super(os, "Solitaire", 206, 220); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            // stock + waste
            if (k.stock.isEmpty()) frame(0, 0, 0xFF3A3250); else back(0, 0);
            if (!k.waste.isEmpty()) card(GAP, 0, k.waste.get(k.waste.size() - 1), selSrc == 7);
            btn("new", 62, 0, 28, 11, "New", os.accent(), mx, my);
            text("§7" + k.moves, 62, 14, 0xFFFFFF);
            int best = os.hs().getInteger("sol");
            if (best > 0) text("§7b" + best, 62, 24, 0xFFFFFF);
            for (int f = 0; f < 4; f++) {
                int fx = GAP * 3 + f * GAP;
                if (k.found[f].isEmpty()) { frame(fx, 0, 0xFF3A3250); textC("§8" + SUITS.charAt(f), fx + CW / 2, 14, 0xFFFFFF); }
                else card(fx, 0, k.found[f].get(k.found[f].size() - 1), selSrc == 8 + f);
            }
            for (int p = 0; p < 7; p++) {
                int px = p * GAP, py = TY;
                if (k.tab[p].isEmpty()) frame(px, py, 0xFF3A3250);
                for (int i = 0; i < k.tab[p].size(); i++) {
                    if (i < k.down[p]) back(px, py); else card(px, py, k.tab[p].get(i), selSrc == p && i >= selIdx);
                    py += i < k.down[p] ? 4 : 9;
                }
            }
            if (won) { rect(30, 90, 146, 30, 0xE0140E22); textC("§lYOU WIN! §r" + k.moves + " moves", 103, 101, 0xFFFFFF); }
        }

        void frame(int rx, int ry, int col) { rect(rx, ry, CW, CH, col); rect(rx + 1, ry + 1, CW - 2, CH - 2, 0xFF1C1530); }

        void back(int rx, int ry) {
            rect(rx, ry, CW, CH, 0xFFFFFFFF);
            for (int i = 0; i < 6; i++) rect(rx + 1, ry + 1 + i * 34 / 6, CW - 2, 34 / 6 + 1, PrideFrame.RAINBOW[i]);
        }

        void card(int rx, int ry, int c, boolean hl) {
            rect(rx, ry, CW, CH, hl ? os.accent() : 0xFF202020);
            rect(rx + 1, ry + 1, CW - 2, CH - 2, 0xFFF4F0F8);
            int col = OsLogic.Klondike.red(c) ? 0xD02030 : 0x101018;
            String s = RANKS.charAt(OsLogic.Klondike.rank(c)) + "" + SUITS.charAt(c / 13);
            fr().drawString(s, x + rx + 2, y + ry + 1, col);
            fr().drawString(String.valueOf(SUITS.charAt(c / 13)), x + rx + CW / 2 - 3, y + ry + 16, col);
        }

        @Override void button(String id, int b) { k = new OsLogic.Klondike(RNG); selSrc = -1; won = false; }

        /** which pile is at (rx, ry): 0..6 tableau (and the card index), 7 waste, 8..11 foundations, 12 stock */
        int[] pileAt(int rx, int ry) {
            if (ry >= 0 && ry < CH) {
                if (in(rx, ry, 0, 0, CW, CH)) return new int[]{12, -1};
                if (in(rx, ry, GAP, 0, CW, CH)) return new int[]{7, k.waste.size() - 1};
                for (int f = 0; f < 4; f++) if (in(rx, ry, GAP * 3 + f * GAP, 0, CW, CH)) return new int[]{8 + f, k.found[f].size() - 1};
                return null;
            }
            int p = rx / GAP;
            if (p < 0 || p > 6 || rx % GAP >= CW || ry < TY) return null;
            int py = TY, hit = -1;
            for (int i = 0; i < k.tab[p].size(); i++) {
                if (ry >= py) hit = i;
                py += i < k.down[p] ? 4 : 9;
            }
            if (!k.tab[p].isEmpty() && ry > py - (k.down[p] >= k.tab[p].size() ? 4 : 9) + CH) hit = -2;   // below the pile: drop target only
            return new int[]{p, hit};
        }

        @Override
        void area(int rx, int ry, int b) {
            if (won) return;
            int[] at = pileAt(rx, ry);
            if (at == null) { selSrc = -1; return; }
            if (at[0] == 12) { k.draw(); selSrc = -1; os.sfx("ui.paper", 1.4f); return; }
            if (b == 1) { if (at[0] < 8 && k.auto(at[0])) afterMove(); selSrc = -1; return; }
            if (selSrc >= 0) {
                int dst = at[0];
                if (dst != selSrc && k.move(selSrc, selIdx, dst)) afterMove();
                else if (dst == selSrc) { selSrc = -1; return; }
                else os.sfx("ui.beep_error", 1.2f);
                selSrc = -1;
                return;
            }
            int idx = at[1];
            if (at[0] < 7 && (idx < k.down[at[0]] || idx < 0)) return;
            if (at[0] == 7 && k.waste.isEmpty() || at[0] >= 8 && idx < 0) return;
            selSrc = at[0];
            selIdx = idx;
        }

        void afterMove() {
            os.sfx("ui.paper", 1.2f);
            if (!k.won()) return;
            won = true;
            os.sfx("ui.beep_ok", 1f);
            NBTTagCompound hs = os.hs();
            hs.setInteger("solWins", hs.getInteger("solWins") + 1);
            if (hs.getInteger("sol") == 0 || k.moves < hs.getInteger("sol")) hs.setInteger("sol", k.moves);
            os.saveHs();
        }
    }

    // ---- Railway Status ---------------------------------------------------------------------------------------------------
    static final class Rail extends App {
        int scroll;

        Rail(GuiPrideOs os) { super(os, "Railway Status", 260, 180); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            NBTTagCompound s = os.status();
            btn("ref", w - 46, 0, 46, 12, "Refresh", os.accent(), mx, my);
            int unacked = s.getInteger("unacked");
            tile(0, 0, "TRAINS", String.valueOf(s.getInteger("locos")), s.getInteger("cars") + " vehicles", 0xFF5BCEFA);
            tile(70, 0, "ALARMS", String.valueOf(s.getInteger("active")), unacked + " unacked", unacked > 0 ? 0xFFE40303 : 0xFF30C060);
            int trip = s.getInteger("gridTripped");
            tile(140, 0, "GRID", s.getInteger("gridNodes") == 0 ? "-" : trip > 0 ? trip + " TRIP" : "OK", s.getInteger("gridNets") + " nets " + s.getInteger("gridOpen") + " open", trip > 0 ? 0xFFFF8C00 : 0xFF30C060);
            List<String> rows = new ArrayList<>();
            NBTTagList tr = s.getTagList("trains", 8);
            for (int i = 0; i < tr.tagCount(); i++) { String[] f = tr.getStringTagAt(i).split("\\|"); if (f.length >= 5) rows.add("§b" + f[0] + " §f" + f[1] + " km/h §7x" + f[2] + " @" + f[3] + "," + f[4]); }
            NBTTagList al = s.getTagList("alarms", 8);
            for (int i = 0; i < al.tagCount(); i++) { String[] f = al.getStringTagAt(i).split("\\|", 3); if (f.length == 3) rows.add((f[1].equals("1") ? "§7" : f[0].equals("CRITICAL") ? "§c" : f[0].equals("MAJOR") ? "§6" : "§e") + f[0].charAt(0) + " " + f[2]); }
            if (rows.isEmpty()) rows.add("§7No trains, no alarms");
            rect(0, 40, w, h - 40, 0x60000000);
            int vis = (h - 44) / 10;
            scroll = Math.max(0, Math.min(scroll, rows.size() - vis));
            for (int i = 0; i < vis && scroll + i < rows.size(); i++) text(fr().trimStringToWidth(rows.get(scroll + i), w - 6), 3, 43 + i * 10, 0xFFFFFF);
        }

        void tile(int rx, int ry, String t, String v, String sub, int col) {
            rect(rx, ry + 14, 66, 24, 0xFF1C1530);
            rect(rx, ry + 14, 66, 1, col);
            text("§7" + t, rx + 3, ry + 16, 0xFFFFFF);
            text("§l" + v, rx + 66 - 3 - fr().getStringWidth("§l" + v), ry + 16, col & 0xFFFFFF);
            text("§8" + fr().trimStringToWidth(sub, 60), rx + 3, ry + 27, 0xFFFFFF);
        }

        @Override void button(String id, int b) { os.send(PacketOs.STATUS, "", null); }
        @Override void wheel(int d) { scroll -= Integer.signum(d) * 2; }
    }

    // ---- Terminal ---------------------------------------------------------------------------------------------------------
    static final class Term extends App {
        final List<String> out = new ArrayList<>();
        final List<String> hist = new ArrayList<>();
        String line = "";
        int histAt;

        Term(GuiPrideOs os) {
            super(os, "Terminal", 280, 170);
            out.add("§dPride OS §7shell - type §fhelp");
        }

        String prompt() { return "§a" + Minecraft.getMinecraft().player.getName() + "§7@§b" + os.deskName().replace(' ', '-').toLowerCase(Locale.ROOT) + "§f$ "; }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            rect(0, 0, w, h, 0xFF05030A);
            int rows = (h - 4) / 9;
            List<String> all = new ArrayList<>(out);
            all.add(prompt() + line + (os.blink() ? "_" : ""));
            int from = Math.max(0, all.size() - rows);
            for (int i = from; i < all.size(); i++) text(fr().trimStringToWidth(all.get(i), w - 4), 2, 2 + (i - from) * 9, 0xE0E0E0);
        }

        @Override
        boolean key(char c, int k) {
            if (enter(k)) { run(line.trim()); if (!line.trim().isEmpty()) hist.add(line.trim()); histAt = hist.size(); line = ""; return true; }
            if (k == Keyboard.KEY_BACK) { if (!line.isEmpty()) line = line.substring(0, line.length() - 1); return true; }
            if (k == Keyboard.KEY_UP && histAt > 0) { line = hist.get(--histAt); return true; }
            if (k == Keyboard.KEY_DOWN) { histAt = Math.min(hist.size(), histAt + 1); line = histAt < hist.size() ? hist.get(histAt) : ""; return true; }
            if (ChatAllowedCharacters.isAllowedCharacter(c) && line.length() < 60) { line += c; return true; }
            return false;
        }

        void say(String s) { out.add(s); while (out.size() > 200) out.remove(0); }

        void run(String cmd) {
            say(prompt() + cmd);
            if (cmd.isEmpty()) return;
            String[] a = cmd.split("\\s+", 2);
            String arg = a.length > 1 ? a[1] : "";
            NBTTagCompound s = os.status();
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "help":
                    say("§7help time whoami echo <text> ls cat <n> clear");
                    say("§7ping <station> trains alarms reboot exit");
                    break;
                case "time": say("World: " + os.worldClock() + "   Real: " + java.time.LocalTime.now().withNano(0)); break;
                case "whoami": say(Minecraft.getMinecraft().player.getName() + " on " + os.deskName()); break;
                case "echo": say(arg); break;
                case "clear": out.clear(); break;
                case "ls": {
                    List<String[]> n = OsLogic.notesFromNbt(os.st.getTagList("notes", 10));
                    if (n.isEmpty()) say("§7(no notes)");
                    for (int i = 0; i < n.size(); i++) say(String.format(Locale.ROOT, "%2d  %5d  %s", i + 1, n.get(i)[1].length(), n.get(i)[0].isEmpty() ? "Untitled" : n.get(i)[0]));
                    break;
                }
                case "cat": {
                    List<String[]> n = OsLogic.notesFromNbt(os.st.getTagList("notes", 10));
                    int i = -1;
                    try { i = Integer.parseInt(arg.trim()) - 1; } catch (NumberFormatException e) {
                        for (int j = 0; j < n.size(); j++) if (n.get(j)[0].equalsIgnoreCase(arg.trim())) i = j;
                    }
                    if (i < 0 || i >= n.size()) { say("§ccat: " + arg + ": no such note"); os.sfx("ui.beep_error", 1f); break; }
                    for (String l : n.get(i)[1].split("\n")) say(l);
                    break;
                }
                case "ping": {
                    if (arg.isEmpty()) { say("usage: ping <station>"); break; }
                    NBTTagList st = s.getTagList("stations", 8);
                    String hit = null;
                    int sx = 0, sz = 0;
                    for (int i = 0; i < st.tagCount(); i++) {
                        String[] f = st.getStringTagAt(i).split("\\|");
                        if (f.length == 3 && f[0].toLowerCase(Locale.ROOT).startsWith(arg.toLowerCase(Locale.ROOT))) { hit = f[0]; sx = Integer.parseInt(f[1]); sz = Integer.parseInt(f[2]); break; }
                    }
                    if (hit == null) { say("§cping: " + arg + ": unknown host"); os.sfx("ui.beep_error", 1f); break; }
                    double dist = Math.sqrt(os.pos.distanceSq(sx, os.pos.getY(), sz));
                    say("PING " + hit + " (" + sx + "," + sz + "): 56 data bytes");
                    for (int i = 0; i < 4; i++) say(String.format(Locale.ROOT, "64 bytes from %s: seq=%d time=%.1f ms", hit, i, 2 + dist / 40 + RNG.nextDouble() * 6));
                    say("§a4 packets sent, 4 received, 0% loss");
                    break;
                }
                case "trains": {
                    NBTTagList tr = s.getTagList("trains", 8);
                    say(s.getInteger("locos") + " locomotive(s), " + s.getInteger("cars") + " vehicle(s)");
                    for (int i = 0; i < tr.tagCount(); i++) { String[] f = tr.getStringTagAt(i).split("\\|"); if (f.length >= 5) say("  " + f[0] + "  " + f[1] + " km/h  x" + f[2] + "  @" + f[3] + "," + f[4]); }
                    os.send(PacketOs.STATUS, "", null);
                    break;
                }
                case "alarms": {
                    NBTTagList al = s.getTagList("alarms", 8);
                    say(s.getInteger("active") + " active alarm(s), " + s.getInteger("unacked") + " unacknowledged");
                    for (int i = 0; i < al.tagCount(); i++) { String[] f = al.getStringTagAt(i).split("\\|", 3); if (f.length == 3) say("  " + (f[1].equals("1") ? "§7" : "§c") + f[0] + " §f" + f[2]); }
                    os.send(PacketOs.STATUS, "", null);
                    break;
                }
                case "reboot": say("Rebooting..."); os.reboot(); break;
                case "exit": os.closeApp(this); break;
                default: say("§c" + a[0] + ": command not found"); os.sfx("ui.beep_error", 1f);
            }
        }
    }

    // ---- Music player -----------------------------------------------------------------------------------------------------
    /** a record that follows the player's volume slider while it plays */
    static final class Track extends MovingSound {
        Track(SoundEvent e) { super(e, SoundCategory.RECORDS); attenuationType = ISound.AttenuationType.NONE; repeat = false; volume = Music.vol; }
        @Override public void update() { volume = Math.max(0.001f, Music.vol); }
        void end() { donePlaying = true; }
    }

    static final class Music extends App {
        static float vol = 0.8f;
        static Track playing;
        static int playingIdx = -1;
        int sel, scroll;
        static final String[] NAMES = {"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait",
                "Station chime", "Two-tone chime", "Bell chime", "Ding-dong", "Pling", "PA chime", "Lift ding"};

        static SoundEvent event(int i) {
            switch (i) {
                case 0: return SoundEvents.RECORD_13; case 1: return SoundEvents.RECORD_CAT; case 2: return SoundEvents.RECORD_BLOCKS;
                case 3: return SoundEvents.RECORD_CHIRP; case 4: return SoundEvents.RECORD_FAR; case 5: return SoundEvents.RECORD_MALL;
                case 6: return SoundEvents.RECORD_MELLOHI; case 7: return SoundEvents.RECORD_STAL; case 8: return SoundEvents.RECORD_STRAD;
                case 9: return SoundEvents.RECORD_WARD; case 10: return SoundEvents.RECORD_11; case 11: return SoundEvents.RECORD_WAIT;
                case 12: return com.dogpound.railmap.sound.PackSounds.ev("station.chime_airport");
                case 13: return com.dogpound.railmap.sound.PackSounds.ev("station.chime_two_tone");
                case 14: return com.dogpound.railmap.sound.PackSounds.ev("station.chime_bell");
                case 15: return com.dogpound.railmap.sound.PackSounds.ev("station.chime_dingdong");
                case 16: return com.dogpound.railmap.sound.PackSounds.ev("station.chime_pling");
                case 17: return com.dogpound.railmap.sound.PackSounds.ev("pa_chime");
                default: return com.dogpound.railmap.sound.PackSounds.ev("station.lift_ding");
            }
        }

        Music(GuiPrideOs os) {
            super(os, "Music", 200, 150);
            if (os.set().hasKey("vol")) vol = os.set().getInteger("vol") / 100f;
            sel = Math.max(0, playingIdx);
        }

        static boolean isPlaying() { return playing != null && Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(playing); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            boolean on = isPlaying();
            rect(0, 0, w, 22, 0xFF120C20);
            text(on ? "§d♫ §f" + (playingIdx < 12 ? "C418 - " : "") + NAMES[playingIdx] : "§7Stopped", 4, 3, 0xFFFFFF);
            if (on) for (int i = 0; i < 16; i++) {                             // a little visualiser
                int bh = 2 + (int) (Math.abs(Math.sin(os.ticks() * 0.3 + i * 1.7)) * 6 * vol);
                rect(4 + i * 4, 20 - bh, 3, bh, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            }
            int rows = 10;
            scroll = Math.max(0, Math.min(scroll, NAMES.length - rows));
            for (int i = 0; i < rows && scroll + i < NAMES.length; i++) {
                int t = scroll + i;
                if (t == sel) rect(0, 26 + i * 10, 120, 10, os.accent() & 0x80FFFFFF);
                hits.add(new Hit("t" + t, x, y + 26 + i * 10, 120, 10));
                text((t < 12 ? "§b● " : "§d● ") + NAMES[t], 3, 27 + i * 10, t == playingIdx && on ? 0xFFE080 : 0xFFFFFF);
            }
            btn("play", 126, 28, 74, 14, "▶ Play", os.accent(), mx, my);
            btn("stop", 126, 46, 74, 14, "■ Stop", 0xFF6A2030, mx, my);
            text("Volume " + Math.round(vol * 100) + "%", 128, 68, 0xFFFFFF);
            btn("vd", 126, 80, 22, 14, "-", PrideFrame.BUTTON, mx, my);
            rect(150, 86, 26, 3, 0xFF333333);
            rect(150, 86, (int) (26 * vol), 3, os.accent());
            btn("vu", 178, 80, 22, 14, "+", PrideFrame.BUTTON, mx, my);
        }

        @Override
        void button(String id, int b) {
            switch (id) {
                case "play": stop(); playing = new Track(event(sel)); playingIdx = sel; Minecraft.getMinecraft().getSoundHandler().playSound(playing); break;
                case "stop": stop(); break;
                case "vd": vol = Math.max(0, Math.round(vol * 10 - 1) / 10f); os.set().setInteger("vol", Math.round(vol * 100)); os.saveSet(null); break;
                case "vu": vol = Math.min(1, Math.round(vol * 10 + 1) / 10f); os.set().setInteger("vol", Math.round(vol * 100)); os.saveSet(null); break;
                default: if (id.startsWith("t")) sel = Integer.parseInt(id.substring(1));
            }
        }

        static void stop() {
            if (playing != null) { playing.end(); Minecraft.getMinecraft().getSoundHandler().stopSound(playing); }
            playing = null;
        }

        @Override void wheel(int d) { scroll -= Integer.signum(d); }
    }

    // ---- Settings -------------------------------------------------------------------------------------------------------
    static final class Settings extends App {
        String name, pin = "";
        int field = -1;                       // 0 name, 1 pin

        Settings(GuiPrideOs os) { super(os, "Settings", 230, 180); name = os.set().getString("name"); }

        @Override
        void draw(int mx, int my) {
            hits.clear();
            text("Wallpaper", 0, 0, 0xFFFFFF);
            for (int i = 0; i < GuiPrideOs.WALLS.length; i++) {
                int px = (i % 4) * 56, py = 10 + (i / 4) * 30;
                if (i == os.set().getInteger("wall")) rect(px - 1, py - 1, 54, 28, 0xFFFFFFFF);
                os.wallpaper(i, x + px, y + py, 52, 26);
                hits.add(new Hit("w" + i, x + px, y + py, 52, 26));
            }
            text("Theme", 0, 74, 0xFFFFFF);
            for (int i = 0; i < GuiPrideOs.ACCENTS.length; i++) {
                int px = 36 + i * 16;
                if (i == os.set().getInteger("theme")) rect(px - 1, 72, 14, 14, 0xFFFFFFFF);
                rect(px, 73, 12, 12, GuiPrideOs.ACCENTS[i]);
                hits.add(new Hit("a" + i, x + px, y + 73, 12, 12));
            }
            text("Desk name", 0, 94, 0xFFFFFF);
            fieldBox(60, 91, 120, name, field == 0);
            hits.add(new Hit("f0", x + 60, y + 91, 120, 13));
            text("PIN lock", 0, 112, 0xFFFFFF);
            fieldBox(60, 109, 60, pin.replaceAll(".", "*"), field == 1);
            hits.add(new Hit("f1", x + 60, y + 109, 60, 13));
            text(os.set().getBoolean("hasPin") ? "§aLocked with a PIN" : "§7No PIN", 126, 112, 0xFFFFFF);
            btn("setpin", 0, 128, 70, 14, "Set PIN", PrideFrame.BUTTON, mx, my);
            btn("clrpin", 74, 128, 70, 14, "Remove PIN", 0xFF6A2030, mx, my);
            btn("save", 0, 150, 70, 16, "Save name", os.accent(), mx, my);
            text("§7Digits 1-8. Only someone", 148, 128, 0xFFFFFF);
            text("§7with the PIN can open it.", 148, 138, 0xFFFFFF);
        }

        void fieldBox(int rx, int ry, int fw, String v, boolean focus) {
            rect(rx, ry, fw, 13, focus ? os.accent() : 0xFF555555);
            rect(rx + 1, ry + 1, fw - 2, 11, 0xFF0E0A18);
            text(fr().trimStringToWidth(v, fw - 6, true) + (focus && os.blink() ? "_" : ""), rx + 3, ry + 3, 0xFFFFFF);
        }

        @Override
        void button(String id, int b) {
            NBTTagCompound s = os.set();
            switch (id) {
                case "f0": field = 0; return;
                case "f1": field = 1; return;
                case "setpin":
                    if (pin.isEmpty()) { os.sfx("ui.beep_error", 1f); os.toast("Type a PIN first"); return; }
                    os.saveSet(pin); s.setBoolean("hasPin", true); pin = ""; os.toast("PIN set"); return;
                case "clrpin": os.saveSet(""); s.setBoolean("hasPin", false); os.toast("PIN removed"); return;
                case "save": s.setString("name", name.trim()); os.saveSet(null); os.toast("Saved"); return;
                default:
            }
            if (id.startsWith("w")) { s.setInteger("wall", Integer.parseInt(id.substring(1))); os.saveSet(null); }
            if (id.startsWith("a")) { s.setInteger("theme", Integer.parseInt(id.substring(1))); os.saveSet(null); }
        }

        @Override void area(int rx, int ry, int b) { field = -1; }

        @Override
        boolean key(char c, int k) {
            if (field < 0) return false;
            if (k == Keyboard.KEY_BACK) { if (field == 0 && !name.isEmpty()) name = name.substring(0, name.length() - 1); if (field == 1 && !pin.isEmpty()) pin = pin.substring(0, pin.length() - 1); return true; }
            if (enter(k)) { button(field == 0 ? "save" : "setpin", 0); field = -1; return true; }
            if (field == 0 && ChatAllowedCharacters.isAllowedCharacter(c) && name.length() < 24) { name += c; return true; }
            if (field == 1 && Character.isDigit(c) && pin.length() < 8) { pin += c; return true; }
            return true;
        }
    }
}
