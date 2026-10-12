package com.dogpound.railmap.modelrail.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.gui.PrideFrame;
import com.dogpound.railmap.modelrail.ItemMiniStock;
import com.dogpound.railmap.modelrail.MiniLayout;
import com.dogpound.railmap.modelrail.PacketModelRail;
import com.dogpound.railmap.modelrail.TileModelRailway;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.List;

/**
 * The layout editor: a palette of track pieces and scenery on the left, the table's grid in the middle (live trains,
 * numbered points and signals, a ghost of the piece under the mouse), cabs / stock box / shop / layout on the right.
 * Left click places (drag to paint), right click or the wheel turns the piece, OPERATE throws points and clears
 * signals, PLACE A / B puts the next train from the stock box on a track square.
 */
public class GuiModelRail extends GuiScreen {
    private static final String[] TOOLS = {"STRAIGHT", "CURVE", "POINTS L", "POINTS R", "CROSSING", "STATION", "TUNNEL", "SIGNAL", "BUFFER", "TREES", "HOUSE", "ERASE", "OPERATE", "PLACE A", "PLACE B"};
    private static final int[] TYPE = {MiniLayout.STRAIGHT, MiniLayout.CURVE, MiniLayout.POINTS, MiniLayout.POINTS, MiniLayout.CROSSING, MiniLayout.STATION,
            MiniLayout.TUNNEL, MiniLayout.SIGNAL, MiniLayout.BUFFER, MiniLayout.TREE, MiniLayout.HOUSE, -1, -2, -3, -4};
    private static final int BAIZE = 0xFF2F5A2C, BAIZE2 = 0xFF2B5428, RAIL = 0xFFC8CCD8, DIMRAIL = 0xFF6E7A70;

    private final BlockPos pos;
    private PrideFrame f;
    private int tool, rot, lastPaint = -1;
    private int gx0, gy0, cs;

    public GuiModelRail(BlockPos pos) { this.pos = pos; }

    private TileModelRailway tile() { return mc.world != null && mc.world.getTileEntity(pos) instanceof TileModelRailway t ? t : null; }

    @Override public void initGui() { f = PrideFrame.fit(width, height); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private void send(String op, int... a) { RailMap.NETWORK.sendToServer(new PacketModelRail(pos, op, a)); }

    // ---- drawing -------------------------------------------------------------------------------------------------------------
    @Override
    public void drawScreen(int mx, int my, float pt) {
        TileModelRailway t = tile();
        if (t == null || t.isInvalid()) { mc.displayGuiScreen(null); return; }
        f.draw(this, "Model Railway Layout", t.tableOk ? t.tw + " x " + t.td + " table · " + t.lay.w + " x " + t.lay.d + " squares" : "no table");
        int lw = 124, rw = Math.min(210, f.cw / 4);
        drawTools(f.cx, f.cy, lw, mx, my);
        int ax = f.cx + lw + 8, aw = f.cw - lw - rw - 16, ay = f.cy + 10, ah = f.ch - 24;
        if (!t.tableOk) {
            PrideFrame.card(ax, ay, aw, ah, PrideFrame.PINK);
            fontRenderer.drawSplitString("Build the table first: place 2x2 up to 4x4 Model Railway Table sections directly behind the desk (the desk on the front edge, facing you). Each section gives 4 x 4 squares.", ax + 8, ay + 10, aw - 16, 0xFFFFFF);
        } else drawGrid(t, ax, ay, aw, ah, mx, my, pt);
        drawRight(t, f.cx + f.cw - rw, f.cy, rw, mx, my);
        String help = "§7Left click: place (drag paints) · Right click / wheel / R: turn · M: mirror points · OPERATE: throw points, clear signals · PLACE A/B: train from the stock box";
        fontRenderer.drawString(fontRenderer.trimStringToWidth(help, f.cw), f.cx, f.y + f.h - 11, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
    }

    private void drawTools(int x, int y, int w, int mx, int my) {
        PrideFrame.card(x, y, w, f.ch - 14, PrideFrame.BLUE);
        fontRenderer.drawString("PIECES", x + 4, y + 4, PrideFrame.BLUE);
        int bw = (w - 12) / 2;
        for (int i = 0; i < TOOLS.length; i++) {
            int bx = x + 4 + (i % 2) * (bw + 4), by = y + 16 + (i / 2) * 20;
            boolean over = mx >= bx && my >= by && mx < bx + bw && my < by + 17;
            PrideFrame.tile(bx, by, bw, 17, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], over, tool == i);
            fontRenderer.drawString(TOOLS[i], bx + (bw - fontRenderer.getStringWidth(TOOLS[i])) / 2, by + 6, tool == i ? 0xFFFFFF : 0xC8C8D8);
        }
        int ry = y + 16 + ((TOOLS.length + 1) / 2) * 20 + 4;
        PrideFrame.button(x + 4, ry, w - 8, 16, "TURN: " + new String[]{"N", "E", "S", "W"}[rot] + " (" + rot * 90 + "°)", PrideFrame.BUTTON, mx, my);
        int py = ry + 22, ps = Math.min(w - 8, f.cy + f.ch - 20 - py);
        if (ps > 24 && TYPE[tool] > 0) {
            Gui.drawRect(x + 4, py, x + 4 + ps, py + ps, BAIZE);
            MiniLayout g = new MiniLayout();
            g.w = 1; g.d = 1;
            g.set(0, 0, TYPE[tool], rot, tool == 3);
            drawCell(g, 0, x + 4, py, ps, 0xFF);
        }
    }

    private void drawGrid(TileModelRailway t, int ax, int ay, int aw, int ah, int mx, int my, float pt) {
        MiniLayout m = t.mirror() ? t.lay : t.predicted(pt);
        cs = Math.max(6, Math.min(26, Math.min((aw - 14) / Math.max(1, m.w), (ah - 14) / Math.max(1, m.d))));
        gx0 = ax + 12 + (aw - 12 - cs * m.w) / 2;
        gy0 = ay + 10 + (ah - 10 - cs * m.d) / 2;
        Gui.drawRect(gx0 - 3, gy0 - 3, gx0 + cs * m.w + 3, gy0 + cs * m.d + 3, 0xFF5A3A20);
        for (int gz = 0; gz < m.d; gz++) {
            fontRenderer.drawString(String.valueOf(gz + 1), gx0 - 6 - fontRenderer.getStringWidth(String.valueOf(gz + 1)), gy0 + gz * cs + (cs - 8) / 2, 0x9AA0C0);
            for (int gx = 0; gx < m.w; gx++) {
                int x = gx0 + gx * cs, y = gy0 + gz * cs;
                Gui.drawRect(x, y, x + cs, y + cs, ((gx / 4 + gz / 4) & 1) == 0 ? BAIZE : BAIZE2);
            }
        }
        for (int gx = 0; gx < m.w; gx++) fontRenderer.drawString(String.valueOf((char) ('A' + gx)), gx0 + gx * cs + (cs - 6) / 2, gy0 - 12, 0x9AA0C0);
        for (int i = 4; i < m.w; i += 4) Gui.drawRect(gx0 + i * cs, gy0, gx0 + i * cs + 1, gy0 + m.d * cs, 0x40000000);
        for (int i = 4; i < m.d; i += 4) Gui.drawRect(gx0, gy0 + i * cs, gx0 + m.w * cs, gy0 + i * cs + 1, 0x40000000);
        if (t.mirror()) {
            fontRenderer.drawSplitString("MIRROR mode: the table shows the real railway in miniature. Turn MODE back to MANUAL or AUTO to run your own layout.", gx0 + 8, gy0 + 8, cs * m.w - 16, 0xFFFFFF);
            return;
        }
        for (int c = 0; c < MiniLayout.N * MiniLayout.N; c++) if (m.inside(c % MiniLayout.N, c / MiniLayout.N) && m.type(c) != MiniLayout.EMPTY) drawCell(m, c, gx0 + c % MiniLayout.N * cs, gy0 + c / MiniLayout.N * cs, cs, 0xFF);
        if (cs >= 12) {
            List<Integer> pts = m.numbered(MiniLayout.POINTS), sig = m.numbered(MiniLayout.SIGNAL);
            for (int i = 0; i < pts.size(); i++) tag(pts.get(i), "P" + (i + 1), i < 6 ? 0xFFD040 : 0x9AA0C0);
            for (int i = 0; i < sig.size(); i++) tag(sig.get(i), "S" + (i + 1), i < 4 ? 0xFF8080 : 0x9AA0C0);
        }
        for (MiniLayout.Train tr : m.trains)
            for (int i = Math.max(1, tr.cars.length) - 1; i >= 0; i--) {
                double[] p = MiniLayout.pose(m.car(tr, i));
                int px = gx0 + (int) Math.round(p[0] * cs), py = gy0 + (int) Math.round(p[1] * cs), r = Math.max(2, cs / 5);
                int col = tr.derailed ? 0xFFFF3030 : i == 0 ? (tr.id == 1 ? 0xFF5BCEFA : 0xFFF5A9B8) : (tr.id == 1 ? 0xFF3A8AB0 : 0xFFB07888);
                Gui.drawRect(px - r, py - r, px + r, py + r, 0xFF101010);
                Gui.drawRect(px - r + 1, py - r + 1, px + r - 1, py + r - 1, col);
                if (i == 0 && cs >= 12) fontRenderer.drawStringWithShadow(tr.id == 1 ? "A" : "B", px - 2, py - r - 9, col);
            }
        int hx = (mx - gx0) / cs, hz = (my - gy0) / cs;
        if (mx >= gx0 && my >= gy0 && m.inside(hx, hz)) {
            int x = gx0 + hx * cs, y = gy0 + hz * cs;
            if (TYPE[tool] > 0) {
                MiniLayout g = new MiniLayout();
                g.w = 1; g.d = 1;
                g.set(0, 0, TYPE[tool], rot, tool == 3);
                Gui.drawRect(x, y, x + cs, y + cs, 0x80FFFFFF);
                drawCell(g, 0, x, y, cs, 0xB0);
            }
            outline(x, y, cs, TYPE[tool] == -1 ? 0xFFFF4040 : 0xFFFFFFFF);
            int c = MiniLayout.idx(hx, hz);
            String what = (char) ('A' + hx) + "" + (hz + 1) + " " + MiniLayout.NAMES[m.type(c)] + (m.type(c) == MiniLayout.POINTS ? (m.rev[c] ? " (turnout)" : " (straight)") : m.type(c) == MiniLayout.SIGNAL ? (m.green[c] ? " (clear)" : " (danger)") : "");
            fontRenderer.drawStringWithShadow(what, ax + 4, ay + ah - 2, 0xFFFFFF);
        }
    }

    private void tag(int c, String s, int col) {
        int x = gx0 + c % MiniLayout.N * cs + 1, y = gy0 + c / MiniLayout.N * cs + 1;
        Gui.drawRect(x, y, x + fontRenderer.getStringWidth(s) + 2, y + 9, 0xA0000000);
        fontRenderer.drawString(s, x + 1, y + 1, col);
    }

    private static void outline(int x, int y, int s, int col) {
        Gui.drawRect(x, y, x + s, y + 1, col); Gui.drawRect(x, y + s - 1, x + s, y + s, col);
        Gui.drawRect(x, y, x + 1, y + s, col); Gui.drawRect(x + s - 1, y, x + s, y + s, col);
    }

    /** one square of the layout, drawn flat from above */
    private void drawCell(MiniLayout m, int c, int x, int y, int s, int alpha) {
        int t = m.type(c), r = m.rot(c), a = alpha << 24;
        switch (t) {
            case MiniLayout.STRAIGHT: road(m, c, r, (r + 2) & 3, x, y, s, RAIL, a); break;
            case MiniLayout.CURVE: road(m, c, r, (r + 1) & 3, x, y, s, RAIL, a); break;
            case MiniLayout.CROSSING: road(m, c, 0, 2, x, y, s, RAIL, a); road(m, c, 1, 3, x, y, s, RAIL, a); break;
            case MiniLayout.POINTS: {
                int div = (r + (m.mirror(c) ? 3 : 1)) & 3;
                road(m, c, r, m.rev[c] ? (r + 2) & 3 : div, x, y, s, DIMRAIL, a);
                road(m, c, r, m.rev[c] ? div : (r + 2) & 3, x, y, s, 0xFFFFFFFF, a);
                break;
            }
            case MiniLayout.STATION: {
                boolean ns = (r & 1) == 0;
                if (ns) Gui.drawRect(x + s * 3 / 4, y + 1, x + s - 1, y + s - 1, (a | 0xB8B4A8)); else Gui.drawRect(x + 1, y + s * 3 / 4, x + s - 1, y + s - 1, (a | 0xB8B4A8));
                road(m, c, r, (r + 2) & 3, x, y, s, RAIL, a);
                break;
            }
            case MiniLayout.TUNNEL: Gui.drawRect(x + 1, y + 1, x + s - 1, y + s - 1, a | 0x3A6C32); road(m, c, r, (r + 2) & 3, x, y, s, 0xFF202020, a); break;
            case MiniLayout.SIGNAL: {
                road(m, c, r, (r + 2) & 3, x, y, s, RAIL, a);
                double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.42));
                double nx = -Math.sin(p[2]), nz = Math.cos(p[2]);
                int px = x + (int) Math.round((p[0] - c % MiniLayout.N - nx * 0.3) * s), py = y + (int) Math.round((p[1] - c / MiniLayout.N - nz * 0.3) * s), d = Math.max(2, s / 7);
                Gui.drawRect(px - d - 1, py - d - 1, px + d + 1, py + d + 1, a | 0x101010);
                Gui.drawRect(px - d, py - d, px + d, py + d, a | (m.green[c] ? 0x30FF60 : 0xFF2020));
                break;
            }
            case MiniLayout.BUFFER: {
                road(m, c, (r + 2) & 3, r, x, y, s, RAIL, a, 0.5);
                double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.5));
                int px = x + (int) Math.round((p[0] - c % MiniLayout.N) * s), py = y + (int) Math.round((p[1] - c / MiniLayout.N) * s), d = Math.max(2, s / 4);
                boolean ns = (r & 1) == 0;
                Gui.drawRect(px - (ns ? d : 1), py - (ns ? 1 : d), px + (ns ? d : 1), py + (ns ? 1 : d), a | 0xD02020);
                break;
            }
            case MiniLayout.TREE: {
                int d = Math.max(2, s / 5);
                for (int k = 0; k < 3; k++) {
                    int px = x + s / 4 + (k * s / 4), py = y + s / 3 + ((k & 1) * s / 3);
                    Gui.drawRect(px - d, py - d, px + d, py + d, a | (k == 1 ? 0x387A30 : 0x2E6A2A));
                }
                break;
            }
            case MiniLayout.HOUSE: {
                Gui.drawRect(x + s / 5, y + s / 5, x + s * 4 / 5, y + s * 4 / 5, a | 0xA03A2A);
                Gui.drawRect(x + s / 5, y + s / 2 - 1, x + s * 4 / 5, y + s / 2, a | 0x6A2018);
                break;
            }
            default: break;
        }
    }

    private void road(MiniLayout m, int c, int from, int to, int x, int y, int s, int col, int a) { road(m, c, from, to, x, y, s, col, a, 1); }

    private void road(MiniLayout m, int c, int from, int to, int x, int y, int s, int col, int a, double upTo) {
        int n = Math.max(4, (int) (s * 1.6 * upTo));
        int ox = c % MiniLayout.N, oz = c / MiniLayout.N;
        for (int i = 0; i <= n; i++) {
            double[] p = MiniLayout.pose(new MiniLayout.Loc(c, from, to, upTo * i / n));
            double nx = -Math.sin(p[2]), nz = Math.cos(p[2]);
            int bx = x + (int) Math.round((p[0] - ox) * s), by = y + (int) Math.round((p[1] - oz) * s);
            if (i % 3 == 0) Gui.drawRect(bx - (int) Math.round(Math.abs(nx) * s * 0.2) - 1, by - (int) Math.round(Math.abs(nz) * s * 0.2) - 1,
                    bx + (int) Math.round(Math.abs(nx) * s * 0.2) + 1, by + (int) Math.round(Math.abs(nz) * s * 0.2) + 1, a | 0x6B4A2E);   // sleeper
            for (double side : new double[]{-0.12, 0.12}) {
                int px = bx + (int) Math.round(nx * s * side), py = by + (int) Math.round(nz * s * side);
                Gui.drawRect(px, py, px + 1, py + 1, (col & 0xFFFFFF) | a);
            }
        }
    }

    private void drawRight(TileModelRailway t, int x, int y, int w, int mx, int my) {
        int yy = y;
        PrideFrame.card(x, yy, w, 58, PrideFrame.PINK);
        fontRenderer.drawString("CABS", x + 4, yy + 4, PrideFrame.PINK);
        for (int id = 1; id <= 2; id++) {
            MiniLayout.Train tr = t.lay.train(id);
            int ly = yy + 16 + (id - 1) * 20;
            String s = (id == 1 ? "§bA§r " : "§dB§r ") + (tr == null ? "§7no train" : tr.name + " §7" + tr.cars.length + " veh." + (tr.derailed ? " §cOFF RAILS" : tr.dwell > 0 ? " §9STATION" : ""));
            fontRenderer.drawString(fontRenderer.trimStringToWidth(s, w - 52), x + 4, ly + 4, 0xFFFFFF);
            if (tr != null) PrideFrame.button(x + w - 44, ly, 40, 16, "LIFT", 0xFF6A2A3A, mx, my);
        }
        yy += 62;
        IInventory box = t.storage(0);
        int[] count = new int[ItemMiniStock.IDS.length];
        if (box != null) for (int i = 0; i < box.getSizeInventory(); i++) {
            ItemStack s = box.getStackInSlot(i);
            if (s.getItem() instanceof ItemMiniStock) count[ItemMiniStock.meta(s)] += s.getCount();
        }
        int rows = ItemMiniStock.IDS.length;
        PrideFrame.card(x, yy, w, 16 + rows * 15, PrideFrame.BLUE);
        fontRenderer.drawString("STOCK BOX · SHOP", x + 4, yy + 4, PrideFrame.BLUE);
        for (int i = 0; i < rows; i++) {
            int ly = yy + 15 + i * 15;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(count[i] + "× " + ItemMiniStock.NAMES[i].replace("Mini ", ""), w - 70), x + 4, ly + 3, count[i] > 0 ? 0xFFFFFF : 0x8A8499);
            PrideFrame.button(x + w - 64, ly, 60, 13, com.dogpound.railmap.economy.Coins.fmt(ItemMiniStock.PRICE[i]), 0xFF2A5A3A, mx, my);
        }
        yy += 20 + rows * 15;
        PrideFrame.card(x, yy, w, 40, PrideFrame.DIM);
        fontRenderer.drawString("LAYOUT", x + 4, yy + 4, PrideFrame.DIM);
        PrideFrame.button(x + 4, yy + 18, w / 2 - 6, 16, "STARTER", 0xFF24408E, mx, my);
        PrideFrame.button(x + w / 2 + 2, yy + 18, w / 2 - 6, 16, "CLEAR", 0xFF6A2A3A, mx, my);
        yy += 44;
        String st = t.shorted ? "§cSHORT - press RESET on the desk" : !t.live() ? "§cDesk has no 12 V / mains" : t.trackPower() ? "§aTrack power ON" : "§eTrack power off (POWER KEY / E-STOP)";
        fontRenderer.drawSplitString(st + "§7 · " + t.lay.count(MiniLayout.POINTS) + " points, " + t.lay.count(MiniLayout.SIGNAL) + " signals. Points P1-P6 and signals S1-S4 are on the desk too.", x + 2, yy + 2, w - 4, 0xFFFFFF);
    }

    // ---- input -----------------------------------------------------------------------------------------------------------------
    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        TileModelRailway t = tile();
        if (t == null) return;
        int lw = 124, rw = Math.min(210, f.cw / 4);
        int bw = (lw - 12) / 2;
        for (int i = 0; i < TOOLS.length; i++) {
            int bx = f.cx + 4 + (i % 2) * (bw + 4), by = f.cy + 16 + (i / 2) * 20;
            if (mx >= bx && my >= by && mx < bx + bw && my < by + 17) { tool = i; click(); return; }
        }
        int ry = f.cy + 16 + ((TOOLS.length + 1) / 2) * 20 + 4;
        if (mx >= f.cx + 4 && mx < f.cx + lw - 4 && my >= ry && my < ry + 16) { rot = (rot + 1) & 3; click(); return; }
        int x = f.cx + f.cw - rw;
        for (int id = 1; id <= 2; id++) {
            int ly = f.cy + 16 + (id - 1) * 20;
            if (t.lay.train(id) != null && in(mx, my, x + rw - 44, ly, 40, 16)) { send("lift", id); click(); return; }
        }
        int yy = f.cy + 62;
        for (int i = 0; i < ItemMiniStock.IDS.length; i++) if (in(mx, my, x + rw - 64, yy + 15 + i * 15, 60, 13)) { send("buy", i); click(); return; }
        yy += 20 + ItemMiniStock.IDS.length * 15;
        if (in(mx, my, x + 4, yy + 18, rw / 2 - 6, 16)) { send("demo"); click(); return; }
        if (in(mx, my, x + rw / 2 + 2, yy + 18, rw / 2 - 6, 16)) { send("clear"); click(); return; }
        if (cs > 0 && t.tableOk && mx >= gx0 && my >= gy0) {
            int gx = (mx - gx0) / cs, gz = (my - gy0) / cs;
            if (!t.lay.inside(gx, gz)) return;
            if (btn == 1) { rot = (rot + 1) & 3; return; }
            lastPaint = -1;
            apply(gx, gz);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        TileModelRailway t = tile();
        if (t == null || btn != 0 || cs <= 0 || TYPE[tool] < -1 || TYPE[tool] == MiniLayout.POINTS || TYPE[tool] == MiniLayout.SIGNAL) return;
        if (mx < gx0 || my < gy0) return;
        int gx = (mx - gx0) / cs, gz = (my - gy0) / cs;
        if (t.lay.inside(gx, gz)) apply(gx, gz);
    }

    private void apply(int gx, int gz) {
        int c = MiniLayout.idx(gx, gz);
        if (c == lastPaint) return;
        lastPaint = c;
        int ty = TYPE[tool];
        if (ty > 0) send("set", gx, gz, ty, rot, tool == 3 ? 1 : 0);
        else if (ty == -1) send("set", gx, gz, MiniLayout.EMPTY, 0, 0);
        else if (ty == -2) send("op", gx, gz);
        else send("place", ty == -3 ? 1 : 2, gx, gz);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) rot = (rot + (d > 0 ? 1 : 3)) & 3;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_R) { rot = (rot + 1) & 3; return; }
        if (key == Keyboard.KEY_M && (tool == 2 || tool == 3)) { tool = tool == 2 ? 3 : 2; return; }
        if (c >= '1' && c <= '9') { tool = c - '1'; return; }
        super.keyTyped(c, key);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    private void click() {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
