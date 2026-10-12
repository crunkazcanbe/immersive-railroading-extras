package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.os.PacketOs;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Pride OS, the desk computer's screen (requested feature): boot screen with
 * the rainbow logo, an optional PIN lock, then a desktop with wallpaper, icons, a start menu, a taskbar clock (world and
 * real time) and draggable, closable app windows. Apps are in {@link OsApps}; saving goes to the desk via PacketOs.
 */
public class GuiPrideOs extends GuiScreen {
    static final int BOOT = 0, LOCK = 1, DESK = 2, NOPOWER = 3;
    static final int BOOT_TICKS = 40, BAR = 16;
    public static final int[] ACCENTS = {0xFFF5A9B8, 0xFF5BCEFA, 0xFFA060E0, 0xFFE40303, 0xFFFF8C00, 0xFFE0C000, 0xFF22B04B, 0xFFB0B0C0};
    public static final String[] WALLS = {"Pride", "Trans", "Night", "Sunset", "Ocean", "Synthwave", "Forest", "Dark"};
    static final String[] APPS = {"Notepad", "Calculator", "Paint", "Snake", "Minesweeper", "2048", "Solitaire", "Railway", "Terminal", "Music", "Settings"};
    static final String[] GLYPH = {"✎", "=", "✏", "S", "*", "2K", "♠", "▣", ">_", "♫", "⚙"};

    final BlockPos pos;
    NBTTagCompound st = new NBTTagCompound();
    int mode, nextMode, ticks, bootAt;
    String pinTry = "", lockMsg = "";
    final List<Win> wins = new ArrayList<>();
    boolean startOpen;
    Win dragging;
    int dragDx, dragDy, dragBtn = -1;
    String toast = "";
    int toastAt = -100;

    static final class Win {
        final OsApps.App app;
        int x, y;
        Win(OsApps.App app, int x, int y) { this.app = app; this.x = x; this.y = y; }
        int w() { return app.w + 8; }
        int h() { return app.h + 18; }
    }

    GuiPrideOs(BlockPos pos, int mode, NBTTagCompound data) {
        this.pos = pos;
        if (data != null) st = data;
        this.mode = mode == NOPOWER ? NOPOWER : BOOT;
        this.nextMode = mode;
    }

    // ---- packets -------------------------------------------------------------------------------------------------------
    public static void fromServer(PacketOs m) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            GuiPrideOs cur = mc.currentScreen instanceof GuiPrideOs && ((GuiPrideOs) mc.currentScreen).pos.equals(m.pos) ? (GuiPrideOs) mc.currentScreen : null;
            switch (m.op) {
                case PacketOs.OPEN:
                    if (cur != null && cur.mode == LOCK) { cur.st = m.data; cur.mode = DESK; cur.sfx("ui.beep_ok", 1f); }
                    else mc.displayGuiScreen(new GuiPrideOs(m.pos, DESK, m.data));
                    break;
                case PacketOs.LOCKED:
                    if (cur != null) { cur.pinTry = ""; cur.lockMsg = "§cWrong PIN"; cur.sfx("ui.beep_error", 1f); }
                    else mc.displayGuiScreen(new GuiPrideOs(m.pos, LOCK, m.data));
                    break;
                case PacketOs.NOPOWER:
                    mc.displayGuiScreen(new GuiPrideOs(m.pos, NOPOWER, null));
                    mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(com.dogpound.railmap.sound.PackSounds.ev("ui.beep_error"), 0.8f));
                    break;
                case PacketOs.STATUS:
                    if (cur != null) cur.st.setTag("status", m.data);
                    break;
                default:
            }
        });
    }

    void send(byte op, String key, NBTTagCompound d) { RailMap.NETWORK.sendToServer(new PacketOs(op, pos, key, d)); }

    /** save one section; refuses (with a beep) anything too big for a packet */
    void save(String key, NBTTagCompound d) {
        PacketBuffer b = new PacketBuffer(Unpooled.buffer());
        b.writeCompoundTag(d);
        if (b.readableBytes() > 30000) { sfx("ui.beep_error", 1f); toast("Too big to save"); return; }
        send(PacketOs.SAVE, key, d);
    }

    // ---- shared bits the apps use ----------------------------------------------------------------------------------------
    NBTTagCompound hs() { if (!st.hasKey("hs")) st.setTag("hs", new NBTTagCompound()); return st.getCompoundTag("hs"); }
    NBTTagCompound set() { if (!st.hasKey("set")) st.setTag("set", new NBTTagCompound()); return st.getCompoundTag("set"); }
    NBTTagCompound status() { return st.getCompoundTag("status"); }
    void saveHs() { save("hs", hs().copy()); }

    /** settings to the desk; pin: null keeps the PIN, "" removes it, digits set it */
    void saveSet(String pin) {
        NBTTagCompound s = set().copy();
        s.removeTag("hasPin");
        if (pin != null) s.setString("pin", pin);
        save("set", s);
    }

    int accent() { return ACCENTS[Math.floorMod(set().getInteger("theme"), ACCENTS.length)]; }
    boolean blink() { return ticks / 8 % 2 == 0; }
    int ticks() { return ticks; }
    String deskName() { String n = set().getString("name"); return n.isEmpty() ? st.getString("desk").isEmpty() ? "Desk" : st.getString("desk") : n; }
    void toast(String s) { toast = s; toastAt = ticks; }

    void sfx(String id, float pitch) {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(com.dogpound.railmap.sound.PackSounds.ev(id), pitch));
    }

    String worldClock() {
        long t = mc.world.getWorldTime();
        return String.format("Day %d %02d:%02d", t / 24000 + 1, (t % 24000 / 1000 + 6) % 24, (t % 1000) * 60 / 1000);
    }

    void reboot() {
        for (Win w : wins) w.app.closed();
        wins.clear();
        startOpen = false;
        mode = BOOT;
        nextMode = DESK;
        bootAt = ticks;
    }

    void closeApp(OsApps.App a) {
        for (int i = 0; i < wins.size(); i++) if (wins.get(i).app == a) { a.closed(); wins.remove(i); return; }
    }

    void openApp(int i) {
        startOpen = false;
        for (Win w : wins) if (w.app.title.equals(APPS[i]) || i == 7 && w.app instanceof OsApps.Rail) { wins.remove(w); wins.add(w); return; }
        OsApps.App a;
        switch (i) {
            case 0: a = new OsApps.Notepad(this); break;
            case 1: a = new OsApps.Calc(this); break;
            case 2: a = new OsApps.Paint(this); break;
            case 3: a = new OsApps.SnakeApp(this); break;
            case 4: a = new OsApps.MinesApp(this); break;
            case 5: a = new OsApps.G2048(this); break;
            case 6: a = new OsApps.Solitaire(this); break;
            case 7: a = new OsApps.Rail(this); send(PacketOs.STATUS, "", null); break;
            case 8: a = new OsApps.Term(this); break;
            case 9: a = new OsApps.Music(this); break;
            default: a = new OsApps.Settings(this);
        }
        int n = wins.size();
        Win w = new Win(a, 60 + n * 14, 10 + n * 10);
        clamp(w);
        wins.add(w);
    }

    void clamp(Win w) {
        w.x = Math.max(0, Math.min(w.x, width - w.w()));
        w.y = Math.max(0, Math.min(w.y, height - BAR - w.h()));
    }

    // ---- life cycle ----------------------------------------------------------------------------------------------------
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        for (Win w : wins) clamp(w);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (Win w : wins) w.app.closed();
    }

    @Override
    public void updateScreen() {
        ticks++;
        if (mode == BOOT) {
            if (ticks - bootAt == 2) sfx("station.chime_airport", 1f);
            if (ticks - bootAt >= BOOT_TICKS) mode = nextMode == BOOT ? DESK : nextMode;
        }
        if (mode == DESK) {
            for (Win w : wins) w.app.tick();
            if (ticks % 100 == 0) send(PacketOs.STATUS, "", null);
        }
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------
    @Override
    public void drawScreen(int mx, int my, float pt) {
        PrideFrame.sync();
        GlStateManager.disableLighting();
        switch (mode) {
            case NOPOWER: drawNoPower(); break;
            case BOOT: drawBoot(pt); break;
            case LOCK: drawLock(mx, my); break;
            default: drawDesk(mx, my);
        }
        if (ticks - toastAt < 50 && !toast.isEmpty()) {
            int tw = fontRenderer.getStringWidth(toast) + 12;
            Gui.drawRect(width - tw - 6, height - BAR - 22, width - 6, height - BAR - 6, 0xE0140E22);
            Gui.drawRect(width - tw - 6, height - BAR - 22, width - 6, height - BAR - 21, accent());
            fontRenderer.drawStringWithShadow(toast, width - tw, height - BAR - 18, 0xFFFFFF);
        }
    }

    void drawNoPower() {
        Gui.drawRect(0, 0, width, height, 0xFF000000);
        String s = "NO POWER";
        GlStateManager.pushMatrix();
        GlStateManager.scale(3, 3, 1);
        fontRenderer.drawString(s, (width / 3 - fontRenderer.getStringWidth(s)) / 2, height / 6 - 4, ticks / 10 % 2 == 0 ? 0xC02020 : 0x501010);
        GlStateManager.popMatrix();
        String sub = "§7The desk computer needs its 12 V supply (and mains, where it uses mains)";
        fontRenderer.drawStringWithShadow(sub, (width - fontRenderer.getStringWidth(sub)) / 2f, height / 2f + 20, 0xFFFFFF);
    }

    /** the rainbow logo: six stripes in a square with a dark notch, like a little flag-monitor */
    void logo(int cx, int cy, int size) {
        int s = size / 6;
        Gui.drawRect(cx - size / 2 - 2, cy - size / 2 - 2, cx + size / 2 + 2, cy + size / 2 + 2, 0xFFFFFFFF);
        for (int i = 0; i < 6; i++) Gui.drawRect(cx - size / 2, cy - size / 2 + i * s, cx + size / 2, cy - size / 2 + (i + 1) * s, PrideFrame.RAINBOW[i]);
        int tri = size / 3;                                         // the progress-flag chevron
        int[] chev = {0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA, 0xFF613915, 0xFF000000};
        for (int k = 0; k < chev.length; k++) {
            int d = tri - k * tri / chev.length;
            for (int r = -d; r <= d; r++) Gui.drawRect(cx - size / 2, cy + r, cx - size / 2 + Math.max(1, d - Math.abs(r)), cy + r + 1, chev[k]);
        }
    }

    void drawBoot(float pt) {
        Gui.drawRect(0, 0, width, height, 0xFF000000);
        float f = Math.min(1f, (ticks - bootAt + pt) / BOOT_TICKS);
        logo(width / 2, height / 2 - 30, 48);
        String t = "Pride OS";
        GlStateManager.pushMatrix();
        GlStateManager.scale(2, 2, 1);
        fontRenderer.drawStringWithShadow(t, (width / 2f - fontRenderer.getStringWidth(t)) / 2f, (height / 2f + 4) / 2f, 0xFFFFFF);
        GlStateManager.popMatrix();
        int bw = 160, bx = (width - bw) / 2, by = height / 2 + 30;
        Gui.drawRect(bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF444444);
        Gui.drawRect(bx, by, bx + bw, by + 5, 0xFF111111);
        int fill = (int) (bw * f), seg = bw / PrideFrame.RAINBOW.length;
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++) {
            int a = bx + i * seg, b = Math.min(bx + fill, i == PrideFrame.RAINBOW.length - 1 ? bx + bw : a + seg);
            if (b > a) Gui.drawRect(a, by, b, by + 5, PrideFrame.RAINBOW[i]);
        }
        String[] steps = {"Checking 12 V supply...", "Loading railway drivers...", "Mounting desk storage...", "Starting desktop..."};
        String s = "§7" + steps[Math.min(3, (int) (f * 4))];
        fontRenderer.drawStringWithShadow(s, (width - fontRenderer.getStringWidth(s)) / 2f, by + 12, 0xFFFFFF);
    }

    static final String[] PAD = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};

    void drawLock(int mx, int my) {
        wallpaper(set().getInteger("wall"), 0, 0, width, height);
        int pw = 120, ph = 170, px = (width - pw) / 2, py = (height - ph) / 2;
        PrideFrame.card(px, py, pw, ph, accent());
        Gui.drawRect(px, py, px + pw, py + ph, 0xC0140E22);
        logo(width / 2, py + 20, 20);
        String n = st.getString("name").isEmpty() ? "Pride OS" : st.getString("name");
        fontRenderer.drawStringWithShadow(n, (width - fontRenderer.getStringWidth(n)) / 2f, py + 36, 0xFFFFFF);
        String dots = pinTry.isEmpty() ? "§7Enter PIN" : pinTry.replaceAll(".", "● ");
        fontRenderer.drawStringWithShadow(dots, (width - fontRenderer.getStringWidth(dots)) / 2f, py + 48, 0xFFFFFF);
        if (!lockMsg.isEmpty()) fontRenderer.drawStringWithShadow(lockMsg, (width - fontRenderer.getStringWidth(lockMsg)) / 2f, py + 58, 0xFFFFFF);
        for (int i = 0; i < PAD.length; i++)
            PrideFrame.button(px + 14 + (i % 3) * 32, py + 70 + (i / 3) * 24, 28, 20, PAD[i], PAD[i].equals("OK") ? accent() : PrideFrame.BUTTON, mx, my);
    }

    void lockPress(String k) {
        sfx("ui.keypad", 1f);
        lockMsg = "";
        if (k.equals("C")) pinTry = "";
        else if (k.equals("OK")) { NBTTagCompound d = new NBTTagCompound(); d.setString("pin", pinTry); send(PacketOs.UNLOCK, "", d); lockMsg = "§7Checking..."; }
        else if (pinTry.length() < 8) pinTry += k;
    }

    /** wallpaper i into a rectangle (also the Settings thumbnails) */
    void wallpaper(int i, int x, int y, int w, int h) {
        switch (Math.floorMod(i, WALLS.length)) {
            case 0: for (int k = 0; k < 6; k++) Gui.drawRect(x, y + k * h / 6, x + w, y + (k + 1) * h / 6, dim(PrideFrame.RAINBOW[k])); break;
            case 1: { int[] c = {0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA}; for (int k = 0; k < 5; k++) Gui.drawRect(x, y + k * h / 5, x + w, y + (k + 1) * h / 5, dim(c[k])); break; }
            case 2:
                PrideFrame.gradient(x, y, x + w, y + h, 0xFF2A1050, 0xFF05030A);
                for (int k = 0; k < w * h / 900; k++) { int sx = x + (k * 7919 % Math.max(1, w)), sy = y + (k * 104729 % Math.max(1, h * 2 / 3)); Gui.drawRect(sx, sy, sx + 1, sy + 1, (k + ticks / 20) % 7 == 0 ? 0x60FFFFFF : 0xE0FFFFFF); }
                break;
            case 3: PrideFrame.gradient(x, y, x + w, y + h / 2, 0xFF3A1060, 0xFFE05080); PrideFrame.gradient(x, y + h / 2, x + w, y + h, 0xFFE05080, 0xFFFF9030); break;
            case 4: PrideFrame.gradient(x, y, x + w, y + h, 0xFF0A4080, 0xFF021020); break;
            case 5:
                PrideFrame.gradient(x, y, x + w, y + h, 0xFF1A0530, 0xFF400A50);
                for (int gx = x; gx < x + w; gx += Math.max(4, w / 16)) Gui.drawRect(gx, y + h / 2, gx + 1, y + h, 0x80F5A9B8);
                for (int k = 0; k < 6; k++) { int gy = y + h / 2 + (h / 2) * k * k / 25; Gui.drawRect(x, gy, x + w, gy + 1, 0x80F5A9B8); }
                Gui.drawRect(x + w / 2 - h / 6, y + h / 4, x + w / 2 + h / 6, y + h / 2, 0xC0FF8C00);
                break;
            case 6: PrideFrame.gradient(x, y, x + w, y + h, 0xFF0F3018, 0xFF040A06); break;
            default: PrideFrame.gradient(x, y, x + w, y + h, PrideFrame.PANEL_TOP | 0xFF000000, PrideFrame.PANEL_BOTTOM | 0xFF000000);
        }
    }

    static int dim(int c) { return 0xFF000000 | ((c >> 17 & 0x7F) << 16) | ((c >> 9 & 0x7F) << 8) | (c >> 1 & 0x7F); }

    void drawDesk(int mx, int my) {
        wallpaper(set().getInteger("wall"), 0, 0, width, height - BAR);
        fontRenderer.drawStringWithShadow("✦ Pride OS  §7" + deskName(), width - 6 - fontRenderer.getStringWidth("✦ Pride OS  §7" + deskName()), 4, 0xFFFFFF);
        // desktop icons
        for (int i = 0; i < APPS.length; i++) {
            int rows = Math.max(1, (height - BAR - 6) / 38), ix = 6 + (i / rows) * 72 + 16, iy = 6 + (i % rows) * 38;   // 72-wide columns so "Minesweeper" never runs into its neighbour; only as many rows as fit above the taskbar
            boolean over = topWinAt(mx, my) == null && !startOpen && mx >= ix && my >= iy && mx < ix + 40 && my < iy + 34;
            Gui.drawRect(ix + 8, iy, ix + 32, iy + 22, over ? 0xC0FFFFFF : 0x90140E22);
            Gui.drawRect(ix + 8, iy, ix + 32, iy + 2, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length]);
            fontRenderer.drawString(GLYPH[i], ix + 20 - fontRenderer.getStringWidth(GLYPH[i]) / 2, iy + 9, over ? 0x140E22 : 0xFFFFFF);
            String l = APPS[i];
            fontRenderer.drawStringWithShadow(l, ix + 20 - fontRenderer.getStringWidth(l) / 2f, iy + 24, 0xFFFFFF);
        }
        Win top = wins.isEmpty() ? null : wins.get(wins.size() - 1);
        Win hover = startOpen ? null : topWinAt(mx, my);
        for (Win w : wins) drawWin(w, w == top, hover == w ? mx : -9999, hover == w ? my : -9999);
        drawTaskbar(mx, my);
        if (startOpen) drawStart(mx, my);
    }

    void drawWin(Win w, boolean focus, int mx, int my) {
        int x = w.x, y = w.y, ww = w.w(), wh = w.h(), acc = accent();
        Gui.drawRect(x + 2, y + 2, x + ww + 2, y + wh + 2, 0x60000000);                         // shadow
        Gui.drawRect(x, y, x + ww, y + wh, focus ? 0xF8181226 : 0xF0141020);
        Gui.drawRect(x, y, x + ww, y + 12, focus ? 0xFF2A2140 : 0xFF1C1530);
        int seg = ww / PrideFrame.RAINBOW.length;
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++) Gui.drawRect(x + i * seg, y, i == PrideFrame.RAINBOW.length - 1 ? x + ww : x + (i + 1) * seg, y + 1, focus ? PrideFrame.RAINBOW[i] : 0xFF555555);
        fontRenderer.drawStringWithShadow(w.app.title, x + 4, y + 3, focus ? 0xFFFFFF : 0xAAAAAA);
        boolean overX = mx >= x + ww - 12 && mx < x + ww && my >= y && my < y + 12;
        Gui.drawRect(x + ww - 12, y + 1, x + ww, y + 12, overX ? 0xFFE04050 : 0x00000000);
        fontRenderer.drawString("x", x + ww - 8, y + 2, 0xFFFFFF);
        Gui.drawRect(x, y + wh - 1, x + ww, y + wh, focus ? acc : 0xFF333333);
        w.app.x = x + 4;
        w.app.y = y + 14;
        w.app.draw(mx, my);
        GlStateManager.color(1, 1, 1, 1);
    }

    void drawTaskbar(int mx, int my) {
        int y = height - BAR;
        Gui.drawRect(0, y, width, height, 0xF0100B1C);
        int seg = width / PrideFrame.RAINBOW.length;
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++) Gui.drawRect(i * seg, y, i == PrideFrame.RAINBOW.length - 1 ? width : (i + 1) * seg, y + 1, PrideFrame.RAINBOW[i]);
        PrideFrame.button(2, y + 2, 44, BAR - 3, "✦ Start", startOpen ? PrideFrame.brighten(accent()) : accent(), mx, my);
        String clock = worldClock() + "  §7|§f  " + java.time.LocalTime.now().withNano(0).toString().substring(0, 5);
        int cw = fontRenderer.getStringWidth(clock);
        fontRenderer.drawStringWithShadow(clock, width - cw - 6, y + 5, 0xFFFFFF);
        int bx = 50, bw = Math.min(64, (width - cw - 60 - bx) / Math.max(1, wins.size()));
        for (int i = 0; i < wins.size(); i++) {
            Win w = wins.get(i);
            boolean top = i == wins.size() - 1;
            PrideFrame.button(bx + i * (bw + 2), y + 2, bw, BAR - 3, fontRenderer.trimStringToWidth(w.app.title, bw - 4), top ? PrideFrame.TILE_ON : PrideFrame.BUTTON, mx, my);
        }
    }

    int startH() { return (APPS.length + 2) * 12 + 18; }

    void drawStart(int mx, int my) {
        int w = 110, h = startH(), x = 2, y = height - BAR - h;
        Gui.drawRect(x, y, x + w, y + h, 0xF8140E22);
        Gui.drawRect(x, y, x + 3, y + h, accent());
        fontRenderer.drawStringWithShadow("§l✦ Pride OS", x + 8, y + 4, 0xFFFFFF);
        for (int i = 0; i < APPS.length + 2; i++) {
            int iy = y + 16 + i * 12;
            boolean over = mx >= x && mx < x + w && my >= iy && my < iy + 12;
            if (over) Gui.drawRect(x + 3, iy, x + w, iy + 12, 0x60FFFFFF);
            String l = i < APPS.length ? GLYPH[i] + "  " + APPS[i] : i == APPS.length ? "↻  Reboot" : "⏻  Shut down";
            fontRenderer.drawStringWithShadow(l, x + 8, iy + 2, i >= APPS.length ? 0xFFB0B0 : 0xFFFFFF);
        }
    }

    Win topWinAt(int mx, int my) {
        for (int i = wins.size() - 1; i >= 0; i--) {
            Win w = wins.get(i);
            if (mx >= w.x && my >= w.y && mx < w.x + w.w() && my < w.y + w.h()) return w;
        }
        return null;
    }

    // ---- input ---------------------------------------------------------------------------------------------------------
    @Override
    protected void mouseClicked(int mx, int my, int b) {
        if (mode == NOPOWER || mode == BOOT) { if (mode == NOPOWER) mc.displayGuiScreen(null); return; }
        if (mode == LOCK) {
            int pw = 120, ph = 170, px = (width - pw) / 2, py = (height - ph) / 2;
            for (int i = 0; i < PAD.length; i++) {
                int bx = px + 14 + (i % 3) * 32, by = py + 70 + (i / 3) * 24;
                if (mx >= bx && my >= by && mx < bx + 28 && my < by + 20) lockPress(PAD[i]);
            }
            return;
        }
        int ty = height - BAR;
        if (startOpen) {
            int h = startH(), y = ty - h;
            if (mx < 112 && my >= y + 16 && my < ty) {
                int i = (my - y - 16) / 12;
                sfx("ui.click", 1f);
                if (i < APPS.length) openApp(i);
                else if (i == APPS.length) reboot();
                else mc.displayGuiScreen(null);
                return;
            }
            startOpen = false;
            if (my < ty) return;
        }
        if (my >= ty) {                                             // taskbar
            if (mx < 48) { startOpen = !startOpen; sfx("ui.click", 1f); return; }
            int cw = fontRenderer.getStringWidth(worldClock() + "  §7|§f  00:00"), bw = Math.min(64, (width - cw - 60 - 50) / Math.max(1, wins.size()));
            int i = (mx - 50) / (bw + 2);
            if (mx >= 50 && i < wins.size()) { Win w = wins.remove(i); wins.add(w); sfx("ui.click", 1.2f); }
            return;
        }
        Win w = topWinAt(mx, my);
        if (w != null) {
            wins.remove(w);
            wins.add(w);
            if (my < w.y + 12) {
                if (mx >= w.x + w.w() - 12) { sfx("ui.click", 0.8f); closeApp(w.app); return; }
                dragging = w; dragDx = mx - w.x; dragDy = my - w.y;
                return;
            }
            dragBtn = b;
            w.app.click(mx, my, b);
            return;
        }
        for (int i = 0; i < APPS.length; i++) {
            int rows = Math.max(1, (height - BAR - 6) / 38), ix = 6 + (i / rows) * 72 + 16, iy = 6 + (i % rows) * 38;   // 72-wide columns so "Minesweeper" never runs into its neighbour; only as many rows as fit above the taskbar
            if (mx >= ix && my >= iy && mx < ix + 40 && my < iy + 34) { sfx("ui.click", 1f); openApp(i); return; }
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int b, long t) {
        if (dragging != null) { dragging.x = mx - dragDx; dragging.y = my - dragDy; clamp(dragging); return; }
        if (dragBtn >= 0 && !wins.isEmpty()) { OsApps.App a = wins.get(wins.size() - 1).app; a.drag(mx - a.x, my - a.y, b); }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) { dragging = null; dragBtn = -1; }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0 && mode == DESK && !wins.isEmpty()) wins.get(wins.size() - 1).app.wheel(d);
    }

    @Override
    protected void keyTyped(char c, int k) throws java.io.IOException {
        if (k == Keyboard.KEY_ESCAPE) {
            if (startOpen) { startOpen = false; return; }
            mc.displayGuiScreen(null);
            return;
        }
        if (mode == LOCK) {
            if (Character.isDigit(c)) lockPress(String.valueOf(c));
            else if (k == Keyboard.KEY_BACK && !pinTry.isEmpty()) pinTry = pinTry.substring(0, pinTry.length() - 1);
            else if (OsApps.enter(k)) lockPress("OK");
            return;
        }
        if (mode != DESK) return;
        if (!wins.isEmpty() && wins.get(wins.size() - 1).app.key(c, k)) return;
        if (k == Keyboard.KEY_LMETA || k == Keyboard.KEY_RMETA) startOpen = !startOpen;
    }
}
