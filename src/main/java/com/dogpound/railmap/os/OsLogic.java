package com.dogpound.railmap.os;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pride OS (requested feature) - the parts that are pure rules, shared by
 * the desktop on the client and the self-test on the server: the calculator's parser, 2048, Minesweeper, Snake,
 * Klondike, and the notes' save format. No Minecraft client classes here.
 */
public final class OsLogic {
    private OsLogic() {}

    // ---- calculator ---------------------------------------------------------------------------------------------------
    /** evaluate + - * / % ^ ( ) with unary minus; throws IllegalArgumentException on bad input */
    public static double calc(String s) {
        int[] i = {0};
        String e = s.replace(" ", "").replace('x', '*').replace('×', '*').replace('÷', '/');
        if (e.isEmpty()) throw new IllegalArgumentException("empty");
        double v = sum(e, i);
        if (i[0] != e.length()) throw new IllegalArgumentException("unexpected '" + e.charAt(i[0]) + "'");
        return v;
    }

    private static double sum(String e, int[] i) {
        double v = prod(e, i);
        while (i[0] < e.length() && (e.charAt(i[0]) == '+' || e.charAt(i[0]) == '-')) v = e.charAt(i[0]++) == '+' ? v + prod(e, i) : v - prod(e, i);
        return v;
    }

    private static double prod(String e, int[] i) {
        double v = unary(e, i);
        while (i[0] < e.length() && "*/%".indexOf(e.charAt(i[0])) >= 0) {
            char op = e.charAt(i[0]++);
            double r = unary(e, i);
            v = op == '*' ? v * r : op == '/' ? v / r : v % r;
        }
        return v;
    }

    /** a sign binds looser than ^ (so -2^2 = -4) */
    private static double unary(String e, int[] i) {
        if (i[0] < e.length() && e.charAt(i[0]) == '-') { i[0]++; return -unary(e, i); }
        if (i[0] < e.length() && e.charAt(i[0]) == '+') { i[0]++; return unary(e, i); }
        double b = atom(e, i);
        if (i[0] < e.length() && e.charAt(i[0]) == '^') { i[0]++; return Math.pow(b, unary(e, i)); }
        return b;
    }

    private static double atom(String e, int[] i) {
        if (i[0] < e.length() && e.charAt(i[0]) == '(') {
            i[0]++;
            double v = sum(e, i);
            if (i[0] >= e.length() || e.charAt(i[0]) != ')') throw new IllegalArgumentException("missing )");
            i[0]++;
            return v;
        }
        int st = i[0];
        while (i[0] < e.length() && (Character.isDigit(e.charAt(i[0])) || e.charAt(i[0]) == '.')) i[0]++;
        if (st == i[0]) throw new IllegalArgumentException(i[0] < e.length() ? "unexpected '" + e.charAt(i[0]) + "'" : "number expected");
        return Double.parseDouble(e.substring(st, i[0]));
    }

    /** a result the way a desk calculator shows it */
    public static String show(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        String s = String.format(java.util.Locale.ROOT, "%.10g", v);
        if (s.contains("e") || s.contains("E")) return s;
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    // ---- 2048 ---------------------------------------------------------------------------------------------------------
    /** slide one row toward index 0, merging equal pairs once; returns the points scored, writes the row back */
    public static int slideRow(int[] row) {
        int[] out = new int[row.length];
        int n = 0, score = 0;
        boolean merged = false;
        for (int v : row) {
            if (v == 0) continue;
            if (n > 0 && !merged && out[n - 1] == v) { out[n - 1] *= 2; score += out[n - 1]; merged = true; }
            else { out[n++] = v; merged = false; }
        }
        System.arraycopy(out, 0, row, 0, row.length);
        return score;
    }

    /** move the 4x4 board g[y][x]: dir 0 left, 1 right, 2 up, 3 down. Returns points, or -1 if nothing moved */
    public static int move2048(int[][] g, int dir) {
        int sz = g.length, score = 0;
        boolean moved = false;
        for (int k = 0; k < sz; k++) {
            int[] row = new int[sz];
            for (int j = 0; j < sz; j++) row[j] = cell(g, dir, k, j);
            int[] before = row.clone();
            score += slideRow(row);
            if (!java.util.Arrays.equals(before, row)) moved = true;
            for (int j = 0; j < sz; j++) setCell(g, dir, k, j, row[j]);
        }
        return moved ? score : -1;
    }

    private static int cell(int[][] g, int dir, int k, int j) {
        int n = g.length - 1;
        switch (dir) { case 0: return g[k][j]; case 1: return g[k][n - j]; case 2: return g[j][k]; default: return g[n - j][k]; }
    }

    private static void setCell(int[][] g, int dir, int k, int j, int v) {
        int n = g.length - 1;
        switch (dir) { case 0: g[k][j] = v; break; case 1: g[k][n - j] = v; break; case 2: g[j][k] = v; break; default: g[n - j][k] = v; }
    }

    /** drop a 2 (90 %) or 4 on a random empty square; false if full */
    public static boolean spawn2048(int[][] g, Random r) {
        List<int[]> free = new ArrayList<>();
        for (int y = 0; y < g.length; y++) for (int x = 0; x < g.length; x++) if (g[y][x] == 0) free.add(new int[]{x, y});
        if (free.isEmpty()) return false;
        int[] c = free.get(r.nextInt(free.size()));
        g[c[1]][c[0]] = r.nextInt(10) == 0 ? 4 : 2;
        return true;
    }

    public static boolean canMove2048(int[][] g) {
        for (int d = 0; d < 4; d++) {
            int[][] c = new int[g.length][];
            for (int y = 0; y < g.length; y++) c[y] = g[y].clone();
            if (move2048(c, d) >= 0) return true;
        }
        return false;
    }

    // ---- Minesweeper --------------------------------------------------------------------------------------------------
    public static final class Mines {
        public final int w, h, count;
        public final boolean[] mine, open, flag;
        public boolean placed, dead, won;

        public Mines(int w, int h, int count) { this.w = w; this.h = h; this.count = count; mine = new boolean[w * h]; open = new boolean[w * h]; flag = new boolean[w * h]; }

        /** lay the mines, never on (or next to) the first square opened */
        public void place(Random r, int safe) {
            List<Integer> cells = new ArrayList<>();
            for (int i = 0; i < w * h; i++) if (Math.abs(i % w - safe % w) > 1 || Math.abs(i / w - safe / w) > 1) cells.add(i);
            Collections.shuffle(cells, r);
            for (int k = 0; k < Math.min(count, cells.size()); k++) mine[cells.get(k)] = true;
            placed = true;
        }

        public int near(int i) {
            int x = i % w, y = i / w, n = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int ax = x + dx, ay = y + dy;
                if ((dx != 0 || dy != 0) && ax >= 0 && ay >= 0 && ax < w && ay < h && mine[ay * w + ax]) n++;
            }
            return n;
        }

        /** open a square: a mine ends the game, a 0 floods its neighbours open */
        public void reveal(int i, Random r) {
            if (dead || won || open[i] || flag[i]) return;
            if (!placed) place(r, i);
            if (mine[i]) { dead = true; open[i] = true; return; }
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(i);
            while (!q.isEmpty()) {
                int c = q.poll();
                if (open[c] || flag[c]) continue;
                open[c] = true;
                if (near(c) != 0) continue;
                int x = c % w, y = c / w;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int ax = x + dx, ay = y + dy;
                    if (ax >= 0 && ay >= 0 && ax < w && ay < h && !open[ay * w + ax]) q.add(ay * w + ax);
                }
            }
            int shut = 0;
            for (int k = 0; k < w * h; k++) if (!open[k]) shut++;
            if (shut == count) won = true;
        }
    }

    // ---- Snake --------------------------------------------------------------------------------------------------------
    public static final class Snake {
        public final int w, h;
        public final ArrayDeque<Integer> body = new ArrayDeque<>();   // head first, cells y*w+x
        public int dir = 1, nextDir = 1, food = -1, score;            // 0 up 1 right 2 down 3 left
        public boolean dead;

        public Snake(int w, int h) {
            this.w = w; this.h = h;
            int y = h / 2;
            for (int x = 3; x >= 1; x--) body.add(y * w + x);
        }

        public void turn(int d) { if ((d + 2) % 4 != dir) nextDir = d; }

        public void placeFood(Random r) {
            if (body.size() >= w * h) { food = -1; return; }
            do food = r.nextInt(w * h); while (body.contains(food));
        }

        /** one move: walls and your own tail kill you, food grows you */
        public void step(Random r) {
            if (dead) return;
            dir = nextDir;
            int head = body.peekFirst(), x = head % w + (dir == 1 ? 1 : dir == 3 ? -1 : 0), y = head / w + (dir == 2 ? 1 : dir == 0 ? -1 : 0);
            if (x < 0 || y < 0 || x >= w || y >= h) { dead = true; return; }
            int n = y * w + x;
            boolean eat = n == food;
            if (!eat) body.pollLast();
            if (body.contains(n)) { dead = true; if (!eat) body.addLast(n); return; }
            body.addFirst(n);
            if (eat) { score++; placeFood(r); }
        }
    }

    // ---- Klondike -----------------------------------------------------------------------------------------------------
    /** cards 0..51: suit = c / 13 (0 spades, 1 hearts, 2 diamonds, 3 clubs), rank = c % 13 (0 ace .. 12 king) */
    public static final class Klondike {
        public final List<Integer> stock = new ArrayList<>(), waste = new ArrayList<>();
        @SuppressWarnings("unchecked") public final List<Integer>[] found = new List[4], tab = new List[7];
        public final int[] down = new int[7];     // face-down cards at the bottom of each tableau pile
        public int moves;

        public Klondike(Random r) {
            List<Integer> deck = new ArrayList<>();
            for (int i = 0; i < 52; i++) deck.add(i);
            Collections.shuffle(deck, r);
            for (int i = 0; i < 4; i++) found[i] = new ArrayList<>();
            int k = 0;
            for (int p = 0; p < 7; p++) { tab[p] = new ArrayList<>(); for (int j = 0; j <= p; j++) tab[p].add(deck.get(k++)); down[p] = p; }
            while (k < 52) stock.add(deck.get(k++));
        }

        public static boolean red(int c) { int s = c / 13; return s == 1 || s == 2; }
        public static int rank(int c) { return c % 13; }

        /** turn one card from the stock, or put the waste back when the stock is empty */
        public void draw() {
            if (stock.isEmpty()) { for (int i = waste.size() - 1; i >= 0; i--) stock.add(waste.get(i)); waste.clear(); }
            else waste.add(stock.remove(stock.size() - 1));
            moves++;
        }

        public boolean toFoundation(int c, int f) {
            List<Integer> d = found[f];
            return d.isEmpty() ? rank(c) == 0 : d.get(d.size() - 1) / 13 == c / 13 && rank(d.get(d.size() - 1)) + 1 == rank(c);
        }

        public boolean onTableau(int c, int p) {
            List<Integer> d = tab[p];
            if (d.isEmpty()) return rank(c) == 12;
            int top = d.get(d.size() - 1);
            return d.size() > down[p] && red(top) != red(c) && rank(top) == rank(c) + 1;
        }

        /** src: 0..6 tableau (from card index idx), 7 waste, 8..11 foundation. dst: 0..6 tableau, 8..11 foundation */
        public boolean move(int src, int idx, int dst) {
            List<Integer> from = src < 7 ? tab[src] : src == 7 ? waste : found[src - 8];
            if (from.isEmpty() || src == dst) return false;
            if (src >= 7) idx = from.size() - 1;
            if (idx < 0 || idx >= from.size() || (src < 7 && idx < down[src])) return false;
            int c = from.get(idx);
            if (dst >= 8) {
                if (idx != from.size() - 1 || !toFoundation(c, dst - 8)) return false;
            } else if (!onTableau(c, dst)) return false;
            List<Integer> run = new ArrayList<>(from.subList(idx, from.size()));
            from.subList(idx, from.size()).clear();
            (dst >= 8 ? found[dst - 8] : tab[dst]).addAll(run);
            if (src < 7 && down[src] >= tab[src].size()) down[src] = Math.max(0, tab[src].size() - 1);   // turn the new top card up
            moves++;
            return true;
        }

        /** the top card of src to whichever foundation takes it */
        public boolean auto(int src) {
            for (int f = 0; f < 4; f++) {
                List<Integer> from = src < 7 ? tab[src] : waste;
                if (!from.isEmpty() && move(src, from.size() - 1, 8 + f)) return true;
            }
            return false;
        }

        public boolean won() { int n = 0; for (List<Integer> f : found) n += f.size(); return n == 52; }
    }

    // ---- notes save format ----------------------------------------------------------------------------------------------
    /** notes as [title, body] pairs -> the desk NBT list */
    public static NBTTagList notesToNbt(List<String[]> notes) {
        NBTTagList l = new NBTTagList();
        for (String[] n : notes) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("t", n[0]);
            t.setString("b", n[1]);
            l.appendTag(t);
        }
        return l;
    }

    public static List<String[]> notesFromNbt(NBTTagList l) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < l.tagCount(); i++) out.add(new String[]{l.getCompoundTagAt(i).getString("t"), l.getCompoundTagAt(i).getString("b")});
        return out;
    }

    // ---- self-test ----------------------------------------------------------------------------------------------------
    /** /irextras os selftest: each line "PASS ..." or "FAIL ..." */
    public static List<String> selftest() {
        List<String> out = new ArrayList<>();
        // 2048: a pair merges once, a merged tile does not merge again in the same move
        int[] r1 = {2, 2, 4, 0};
        int s1 = slideRow(r1);
        check(out, "2048 [2,2,4,0] -> [4,4,0,0] +4", java.util.Arrays.equals(r1, new int[]{4, 4, 0, 0}) && s1 == 4);
        int[] r2 = {2, 2, 2, 2};
        slideRow(r2);
        check(out, "2048 [2,2,2,2] -> [4,4,0,0]", java.util.Arrays.equals(r2, new int[]{4, 4, 0, 0}));
        int[][] g = {{0, 0, 0, 2}, {0, 0, 0, 2}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        int s2 = move2048(g, 2);
        check(out, "2048 board up merges the column", g[0][3] == 4 && g[1][3] == 0 && s2 == 4);
        int[][] stuck = {{2, 4, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 2}};
        check(out, "2048 full board without pairs is game over", !canMove2048(stuck) && move2048(stuck, 0) == -1);

        // Minesweeper: a blank square floods, a mine kills, opening every safe square wins
        Mines m = new Mines(5, 5, 1);
        m.placed = true;
        m.mine[24] = true;
        m.reveal(0, new Random(1));
        int open = 0;
        for (boolean b : m.open) if (b) open++;
        check(out, "minesweeper flood opens 24 safe squares and wins", open == 24 && m.won && !m.dead);
        Mines m2 = new Mines(5, 5, 1);
        m2.placed = true;
        m2.mine[12] = true;
        m2.reveal(12, new Random(1));
        check(out, "minesweeper opening a mine ends the game", m2.dead);
        Mines m3 = new Mines(9, 9, 10);
        m3.reveal(40, new Random(7));
        check(out, "minesweeper first click is never a mine", !m3.dead && m3.open[40] && m3.near(40) == 0);

        // Snake: walls and its own body kill it, food grows it
        Snake sn = new Snake(6, 6);
        sn.food = sn.body.peekFirst() + 1;
        sn.step(new Random(1));
        check(out, "snake eats and grows to 4", sn.body.size() == 4 && sn.score == 1 && !sn.dead);
        Snake wall = new Snake(6, 6);
        wall.food = 0;
        for (int i = 0; i < 6; i++) wall.step(new Random(1));
        check(out, "snake hitting the wall dies", wall.dead);
        Snake self = new Snake(10, 10);
        self.body.clear();
        for (int c : new int[]{55, 54, 53, 52, 51}) self.body.addLast(c);   // head at (5,5) heading right, length 5
        self.food = 0;
        self.turn(2); self.step(new Random(1));
        self.turn(3); self.step(new Random(1));
        self.turn(0); self.step(new Random(1));
        check(out, "snake turning into its own body dies", self.dead);

        // calculator
        check(out, "calc 2+3*4 = 14", calc("2+3*4") == 14);
        check(out, "calc (1+2)*3 - 4/2 = 7", calc("(1+2)*3 - 4/2") == 7);
        check(out, "calc -2^2 = -4, 2^3^2 = 512", calc("-2^2") == -4 && calc("2^3^2") == 512);
        check(out, "calc 1/0 shows Error", show(calc("1/0")).equals("Error"));
        boolean bad;
        try { calc("2+*3"); bad = false; } catch (IllegalArgumentException e) { bad = true; }
        check(out, "calc rejects 2+*3", bad);

        // notes: save to NBT bytes and back
        List<String[]> notes = new ArrayList<>();
        notes.add(new String[]{"Shopping", "rails x64\nsleepers"});
        notes.add(new String[]{"", "§unicode ✦ ok"});
        try {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("notes", notesToNbt(notes));
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            CompressedStreamTools.write(root, new DataOutputStream(bo));
            NBTTagCompound back = CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())), net.minecraft.nbt.NBTSizeTracker.INFINITE);
            List<String[]> got = notesFromNbt(back.getTagList("notes", 10));
            check(out, "notes save/load round-trip", got.size() == 2 && got.get(0)[1].equals("rails x64\nsleepers") && got.get(1)[1].equals("§unicode ✦ ok"));
        } catch (Exception e) {
            check(out, "notes save/load round-trip (" + e + ")", false);
        }

        // Klondike: the deal is legal
        Klondike k = new Klondike(new Random(3));
        int total = k.stock.size();
        for (List<Integer> t : k.tab) total += t.size();
        check(out, "klondike deals 28 + 24 cards", total == 52 && k.tab[6].size() == 7 && k.down[6] == 6);
        return out;
    }

    private static void check(List<String> out, String what, boolean ok) { out.add((ok ? "§aPASS " : "§cFAIL ") + what); }
}
