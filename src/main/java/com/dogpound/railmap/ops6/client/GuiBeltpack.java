package com.dogpound.railmap.ops6.client;

import com.dogpound.railmap.ops6.PacketOps6;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The RCO beltpack's face (batch 6 E #21): the rugged yellow case with its LCD (loco, link, speed, set speed, brakes,
 * radio bars, vigilance countdown, the latched stop), the direction switch, the speed selector, the throttle / brake
 * notches (THROTTLE mode), the independent brake lever, HORN, BELL, SAND, VIGILANCE, RESET and the red E-STOP.
 * Keys: W / S speed selector or notch, A / D direction, B / N train brake, I / K independent, SPACE vigilance, H horn,
 * X E-STOP, R reset.
 */
public class GuiBeltpack extends GuiScreen {
    private NBTTagCompound s = new NBTTagCompound();
    private int tick;
    private final List<Object[]> hits = new ArrayList<>();   // {x, y, w, h, cmd, value}
    static final String[] SPEED = {"STOP", "COAST", "COUPLE", "4 MPH", "7 MPH", "10 MPH"};

    void update(NBTTagCompound n) { s = n; }

    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void initGui() { Ops6Client.send(PacketOps6.of("rcopoll")); }
    @Override public void updateScreen() { if (++tick % 5 == 0) Ops6Client.send(PacketOps6.of("rcopoll")); }

    void cmd(String c, int v) { Ops6Client.send(PacketOps6.of("rco").str("c", c).num("v", v)); mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.3f)); }

    private boolean btn(int x, int y, int w, int h, String label, int col, boolean lit, int mx, int my, String c, int v) {
        boolean over = mx >= x && my >= y && mx < x + w && my < y + h;
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF101010);
        Gui.drawRect(x, y, x + w, y + h, lit ? brighten(col, 70) : over ? brighten(col, 30) : col);
        Gui.drawRect(x, y + h - 2, x + w, y + h, 0x50000000);
        fontRenderer.drawString(label, x + (w - fontRenderer.getStringWidth(label)) / 2, y + (h - 8) / 2, lit ? 0x101010 : 0xFFFFFF);
        hits.add(new Object[]{x, y, w, h, c, v});
        return over;
    }

    static int brighten(int c, int k) {
        int r = Math.min(255, ((c >> 16) & 255) + k), g = Math.min(255, ((c >> 8) & 255) + k), b = Math.min(255, (c & 255) + k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        hits.clear();
        int W = 300, H = 236, x = (width - W) / 2, y = (height - H) / 2;
        // the case: yellow, black bumpers, the antenna, the belt clip
        Gui.drawRect(x + W - 30, y - 22, x + W - 24, y, 0xFF202020);
        Gui.drawRect(x - 4, y - 4, x + W + 4, y + H + 4, 0xFF151515);
        Gui.drawRect(x, y, x + W, y + H, 0xFFE8B818);
        Gui.drawRect(x, y, x + W, y + 3, 0xFFF8D040);
        for (int i = 0; i < 4; i++) Gui.drawRect(x + (i % 2 == 0 ? -4 : W - 6), y + (i < 2 ? -4 : H - 6), x + (i % 2 == 0 ? 6 : W + 4), y + (i < 2 ? 6 : H + 4), 0xFF2A2A2A);
        fontRenderer.drawString("RCO BELTPACK  #" + s.getInteger("serial"), x + 8, y + 6, 0x202020);
        // the LCD
        int lx = x + 8, ly = y + 18, lw = W - 16, lh = 62;
        Gui.drawRect(lx - 1, ly - 1, lx + lw + 1, ly + lh + 1, 0xFF303030);
        Gui.drawRect(lx, ly, lx + lw, ly + lh, 0xFF9DB58A);
        int ink = 0x10200A;
        if (!s.getBoolean("has")) fontRenderer.drawString("NO BELTPACK IN HAND", lx + 4, ly + 4, ink);
        else if (!s.getBoolean("paired")) { fontRenderer.drawString("NOT PAIRED", lx + 4, ly + 4, ink); fontRenderer.drawString("LOOK AT A LOCO, SNEAK + RIGHT-CLICK", lx + 4, ly + 16, ink); }
        else if (!s.getBoolean("link")) { fontRenderer.drawString(s.getString("loco").toUpperCase(Locale.ROOT), lx + 4, ly + 4, ink); fontRenderer.drawString("NO LINK - LOCO NOT LOADED / OUT OF RANGE", lx + 4, ly + 16, ink); }
        else {
            String latched = s.getString("latched");
            fontRenderer.drawString(fontRenderer.trimStringToWidth(s.getString("loco").toUpperCase(Locale.ROOT), 150), lx + 4, ly + 3, ink);
            double dist = s.getDouble("dist"), range = Math.max(1, s.getInteger("range"));
            int bars = dist > range ? 0 : (int) Math.ceil(5 * (1 - dist / range));
            for (int i = 0; i < 5; i++) Gui.drawRect(lx + lw - 40 + i * 7, ly + 11 - i * 2 - 2, lx + lw - 35 + i * 7, ly + 11, i < bars ? 0xFF10200A : 0x3010200A);
            fontRenderer.drawString(String.format(Locale.ROOT, "%.0fm", dist), lx + lw - 74, ly + 3, ink);
            fontRenderer.drawString(String.format(Locale.ROOT, "%5.1f KM/H", s.getDouble("kmh")), lx + 4, ly + 15, ink);
            fontRenderer.drawString(s.getInteger("mode") == 0 ? "SET " + SPEED[Math.max(0, Math.min(5, s.getInteger("spd")))] + String.format(Locale.ROOT, " %.0f", s.getDouble("target")) : "NOTCH " + s.getInteger("notch") + "  TB " + s.getInteger("brake") * 10 + "%", lx + 90, ly + 15, ink);
            fontRenderer.drawString(String.format(Locale.ROOT, "THR %3d%%  TB %3d%%  IB %3d%%  %s", Math.round(s.getFloat("thr") * 100), Math.round(s.getFloat("tb") * 100), Math.round(s.getFloat("ib") * 100),
                    s.getInteger("dir") > 0 ? "FWD" : s.getInteger("dir") < 0 ? "REV" : "NEUT"), lx + 4, ly + 27, ink);
            boolean blink = (tick / 5) % 2 == 0;
            if (!latched.isEmpty()) { if (blink) Gui.drawRect(lx + 2, ly + 38, lx + lw - 2, ly + 50, 0xFF10200A); fontRenderer.drawString(fontRenderer.trimStringToWidth(latched, lw - 8), lx + 4, ly + 40, blink ? 0x9DB58A : ink); }
            else if (s.getBoolean("vig")) fontRenderer.drawString((blink ? "VIGILANCE! " : "           ") + s.getInteger("vigLeft") + " S", lx + 4, ly + 40, ink);
            else if (s.getBoolean("tilt")) fontRenderer.drawString(blink ? "TILT WARNING" : "", lx + 4, ly + 40, ink);
            else fontRenderer.drawString("LIVE  VIG " + s.getInteger("vigLeft") + " S" + (s.getBoolean("bell") ? "  BELL" : "") + (s.getBoolean("sand") ? "  SAND" : ""), lx + 4, ly + 40, ink);
            NBTTagList log = s.getTagList("log", 8);
            if (log.tagCount() > 0) fontRenderer.drawString(fontRenderer.trimStringToWidth(log.getStringTagAt(0), lw - 8), lx + 4, ly + 51, 0x40502A);
        }
        // the controls
        int cy = ly + lh + 8;
        fontRenderer.drawString("DIRECTION", x + 10, cy, 0x202020);
        String[] dir = {"REV", "NEUT", "FWD"};
        for (int i = 0; i < 3; i++) btn(x + 10 + i * 34, cy + 10, 32, 16, dir[i], 0x303030, s.getInteger("dir") == i - 1, mx, my, "dir", i - 1);
        fontRenderer.drawString("MODE", x + 120, cy, 0x202020);
        btn(x + 120, cy + 10, 50, 16, "SPEED", 0x305080, s.getInteger("mode") == 0, mx, my, "mode", 0);
        btn(x + 172, cy + 10, 56, 16, "THROTTLE", 0x305080, s.getInteger("mode") == 1, mx, my, "mode", 1);
        btn(x + 236, cy + 2, 56, 30, "E-STOP", 0xC01818, false, mx, my, "estop", 0);
        cy += 34;
        if (s.getInteger("mode") == 0) {
            fontRenderer.drawString("SPEED SELECTOR", x + 10, cy, 0x202020);
            for (int i = 0; i < 6; i++) btn(x + 10 + i * 47, cy + 10, 45, 16, SPEED[i], 0x404040, s.getInteger("spd") == i, mx, my, "spd", i);
        } else {
            fontRenderer.drawString("THROTTLE NOTCH", x + 10, cy, 0x202020);
            for (int i = 0; i <= 8; i++) btn(x + 10 + i * 15, cy + 10, 13, 16, String.valueOf(i), 0x2A6A2A, s.getInteger("notch") == i, mx, my, "notch", i);
            fontRenderer.drawString("TRAIN BRAKE", x + 150, cy, 0x202020);
            for (int i = 0; i <= 10; i += 2) btn(x + 150 + i / 2 * 23, cy + 10, 21, 16, String.valueOf(i * 10), 0x8A2A2A, s.getInteger("brake") == i, mx, my, "brake", i);
        }
        cy += 32;
        fontRenderer.drawString("INDEPENDENT", x + 10, cy, 0x202020);
        String[] ib = {"REL", "1", "2", "3", "FULL"};
        for (int i = 0; i < 5; i++) btn(x + 10 + i * 27, cy + 10, 25, 16, ib[i], 0x6A3A1A, s.getInteger("indep") == i, mx, my, "indep", i);
        btn(x + 152, cy + 10, 34, 16, "HORN", 0x505050, false, mx, my, "horn", 0);
        btn(x + 188, cy + 10, 34, 16, "BELL", 0x505050, s.getBoolean("bell"), mx, my, "bell", 0);
        btn(x + 224, cy + 10, 34, 16, "SAND", 0x505050, s.getBoolean("sand"), mx, my, "sand", 0);
        btn(x + 260, cy + 10, 32, 16, "RESET", 0x2050A0, false, mx, my, "reset", 0);
        cy += 32;
        btn(x + 10, cy, 180, 20, "VIGILANCE  (SPACE)", 0x1A7A3A, s.getBoolean("vig") && (tick / 5) % 2 == 0, mx, my, "vig", 0);
        btn(x + 196, cy, 96, 20, "RELEASE LOCO", 0x404040, false, mx, my, "release", 0);
        fontRenderer.drawString("W/S speed or notch  A/D dir  B/N brake  I/K ind  H horn  X stop  R reset", x + 6, y + H - 10, 0x403010);
        super.drawScreen(mx, my, pt);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        super.mouseClicked(mx, my, button);
        for (Object[] h : hits) {
            int x = (int) h[0], y = (int) h[1], w = (int) h[2], hh = (int) h[3];
            if (mx >= x && my >= y && mx < x + w && my < y + hh) { cmd((String) h[4], (int) h[5]); return; }
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        int mode = s.getInteger("mode");
        switch (key) {
            case Keyboard.KEY_W -> { if (mode == 0) cmd("spd", Math.min(5, s.getInteger("spd") + 1)); else cmd("notch", Math.min(8, s.getInteger("notch") + 1)); }
            case Keyboard.KEY_S -> { if (mode == 0) cmd("spd", Math.max(0, s.getInteger("spd") - 1)); else cmd("notch", Math.max(0, s.getInteger("notch") - 1)); }
            case Keyboard.KEY_A -> cmd("dir", Math.max(-1, s.getInteger("dir") - 1));
            case Keyboard.KEY_D -> cmd("dir", Math.min(1, s.getInteger("dir") + 1));
            case Keyboard.KEY_B -> cmd("brake", Math.min(10, s.getInteger("brake") + 1));
            case Keyboard.KEY_N -> cmd("brake", Math.max(0, s.getInteger("brake") - 1));
            case Keyboard.KEY_I -> cmd("indep", Math.min(4, s.getInteger("indep") + 1));
            case Keyboard.KEY_K -> cmd("indep", Math.max(0, s.getInteger("indep") - 1));
            case Keyboard.KEY_SPACE -> cmd("vig", 0);
            case Keyboard.KEY_H -> cmd("horn", 0);
            case Keyboard.KEY_X -> cmd("estop", 0);
            case Keyboard.KEY_R -> cmd("reset", 0);
            default -> super.keyTyped(c, key);
        }
    }
}
