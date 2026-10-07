package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.network.PacketScale;
import com.dogpound.railmap.network.PacketSettings;
import com.dogpound.railmap.settings.Setting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Settings Console every IR Extras block shares (sneak-right-click with the Signal Wrench). Pages down the left
 * (railway-console style: dark panel, LED readouts, Pride rainbow), options on the right: switches, -/+ steppers
 * (shift = x10), choice chips, text fields, and live readouts. Every change goes to the server, which answers with the
 * block's real values.
 */
public class GuiBlockSettings extends GuiScreen {
    public final BlockPos pos;
    private String title;
    private float scale;
    private List<Setting> settings = new ArrayList<>();
    private final Map<String, List<Setting>> pages = new LinkedHashMap<>();
    private String page = "";
    private int scroll, contentH, top, bottom, px, pw, oy;
    private String hint = "";
    private final List<Object[]> hits = new ArrayList<>();
    private final Map<String, GuiTextField> fields = new LinkedHashMap<>();
    /** text fields on the page being shown this frame (others are hidden and ignore clicks/keys) */
    private final java.util.Set<String> shownFields = new java.util.HashSet<>();
    private PrideFrame f;
    private long refreshAt;

    public GuiBlockSettings(PacketSettings msg) {
        this.pos = msg.pos;
        accept(msg);
    }

    /** fresh data from the server */
    public void accept(PacketSettings msg) {
        title = msg.title;
        scale = msg.scale;
        settings = new ArrayList<>(msg.settings);
        pages.clear();
        for (Setting s : settings) pages.computeIfAbsent(s.page, k -> new ArrayList<>()).add(s);
        if (scale >= 0) pages.computeIfAbsent("Size", k -> new ArrayList<>());
        if (!pages.containsKey(page)) page = pages.isEmpty() ? "" : pages.keySet().iterator().next();
        for (Setting s : settings) {
            GuiTextField tf = fields.get(s.key);
            if (tf != null && !tf.isFocused()) tf.setText(s.value);
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        fields.clear();
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() {
        for (GuiTextField tf : fields.values()) tf.updateCursorCounter();
        long now = System.currentTimeMillis();
        if (now > refreshAt) {                         // live readouts: ask again every 2 s
            refreshAt = now + 2000;
            RailMap.NETWORK.sendToServer(PacketSettings.request(pos));
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        shownFields.clear();
        hint = "";
        f = PrideFrame.fit(width, height);
        f.draw(this, title == null || title.isEmpty() ? "Settings" : title, "§7" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        int tw = Math.min(120, Math.max(90, f.cw / 6)), ty = f.cy, th = 18;
        int i = 0;
        for (String p : pages.keySet()) {
            boolean over = in(mx, my, f.cx, ty, tw, th), on = p.equals(page);
            PrideFrame.tile(f.cx, ty, tw, th, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], over, on);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(p, tw - 10), f.cx + 6, ty + 5, on ? 0xFFFFFFFF : 0xFFD8D0E8);
            final String pp = p;
            hits.add(new Object[]{f.cx, ty, tw, th, (Runnable) () -> { page = pp; scroll = 0; }});
            ty += th + 2;
            i++;
        }
        px = f.cx + tw + 12;
        pw = f.cx + f.cw - px - 8;
        top = f.cy;
        bottom = f.cy + f.ch - 28;
        PrideFrame.card(px - 6, top - 2, pw + 14, bottom - top + 4, PrideFrame.RAINBOW[Math.max(0, indexOf(page)) % PrideFrame.RAINBOW.length]);
        PrideFrame.clip(px - 6, top, pw + 14, bottom - top);
        oy = top + 4 - scroll;
        int start = hits.size();
        if ("Size".equals(page) && scale >= 0) sizePage(mx, my);
        for (Setting s : pages.getOrDefault(page, new ArrayList<>())) row(s, mx, my);
        contentH = oy + scroll - top;
        PrideFrame.unclip();
        for (int k = hits.size() - 1; k >= start; k--) {
            Object[] h = hits.get(k);
            int y = (Integer) h[1];
            if (y + (Integer) h[3] < top || y > bottom) hits.remove(k);
        }
        PrideFrame.scrollbar(px + pw + 5, top, bottom - top, scroll, bottom - top, contentH);
        for (Map.Entry<String, GuiTextField> e : fields.entrySet())
            if (shownFields.contains(e.getKey()) && e.getValue().y >= top && e.getValue().y + 12 <= bottom) e.getValue().drawTextBox();
        int by = f.y + f.h - 24;
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(hint.isEmpty()
                ? "§8Changes apply at once. Shift + click a +/- for x10. Sneak-right-click a block with the Signal Wrench to open this." : "§7" + hint, f.cw - 200), f.cx, by + 5, 0xFFFFFFFF);
        button(mx, my, f.cx + f.cw - 190, by, 100, 18, "Reset to default", 0xFF8A2238,
                () -> RailMap.NETWORK.sendToServer(PacketSettings.reset(pos)), "Put every option on this block back to how it started");
        button(mx, my, f.cx + f.cw - 84, by, 84, 18, "Done", PrideFrame.BUTTON, () -> mc.displayGuiScreen(null), null);
        super.drawScreen(mx, my, pt);
    }

    private int indexOf(String p) { int i = 0; for (String k : pages.keySet()) { if (k.equals(p)) return i; i++; } return -1; }

    private void row(Setting s, int mx, int my) {
        int lw = Math.min(220, pw / 2);
        boolean over = in(mx, my, px - 2, oy - 1, pw + 4, 16);
        if (over && !s.help.isEmpty()) hint = s.help;
        switch (s.type) {
            case INFO -> {
                fontRenderer.drawString(fontRenderer.trimStringToWidth(s.label, lw - 4), px, oy + 4, 0xFF8A94A0);
                fontRenderer.drawString(fontRenderer.trimStringToWidth(s.value, pw - lw), px + lw, oy + 4, 0xFF7CF29A);
            }
            case BOOL -> {
                boolean on = Boolean.parseBoolean(s.value);
                if (over) Gui.drawRect(px - 2, oy - 1, px + pw + 2, oy + 15, 0x30FFFFFF);
                Gui.drawRect(px, oy + 4, px + 16, oy + 12, on ? 0xFF8CE06A : 0xFF3D2168);
                Gui.drawRect(on ? px + 9 : px + 1, oy + 5, on ? px + 15 : px + 7, oy + 11, 0xFFFFFFFF);
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(s.label, pw - 30), px + 22, oy + 4, on ? 0xFFFFFFFF : 0xFFA79FBF);
                hits.add(new Object[]{px, oy, pw, 16, (Runnable) () -> send(s, Boolean.toString(!on))});
            }
            case NUM -> {
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(s.label, lw - 4), px, oy + 4, 0xFFFFFFFF);
                int v;
                try { v = Integer.parseInt(s.value.trim()); } catch (NumberFormatException e) { v = s.min; }
                int bx = px + lw;
                final int cur = v;
                button(mx, my, bx, oy, 16, 14, "-", PrideFrame.BUTTON, () -> send(s, Integer.toString(cur - s.step * (isShiftKeyDown() ? 10 : 1))), s.help);
                int barW = Math.max(30, pw - lw - 90);
                Gui.drawRect(bx + 20, oy + 5, bx + 20 + barW, oy + 9, 0xFF2A2240);
                float frac = s.max == s.min ? 0 : (v - s.min) / (float) (s.max - s.min);
                Gui.drawRect(bx + 20, oy + 5, bx + 20 + (int) (barW * frac), oy + 9, PrideFrame.PINK);
                button(mx, my, bx + 24 + barW, oy, 16, 14, "+", PrideFrame.BUTTON, () -> send(s, Integer.toString(cur + s.step * (isShiftKeyDown() ? 10 : 1))), s.help);
                fontRenderer.drawString(v + (s.unit.isEmpty() ? "" : " " + s.unit), bx + 44 + barW, oy + 4, 0xFFF5A9B8);
                hits.add(new Object[]{bx + 20, oy, barW, 14, (Runnable) () -> {
                    int pick = s.min + Math.round((float) (lastMx - bx - 20) / barW * (s.max - s.min));
                    send(s, Integer.toString(pick - (pick - s.min) % Math.max(1, s.step)));
                }});
            }
            case CHOICE -> {
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(s.label, lw - 4), px, oy + 4, 0xFFFFFFFF);
                int x = px + lw;
                for (String c : s.choices) {
                    int w = fontRenderer.getStringWidth(c) + 8;
                    if (x + w > px + pw) { x = px + lw; oy += 15; }
                    boolean on = c.equals(s.value), ov = in(mx, my, x, oy, w, 13);
                    Gui.drawRect(x, oy, x + w, oy + 13, on ? 0xFF6A3FA0 : ov ? 0xFF3A2E52 : 0xFF241C33);
                    fontRenderer.drawString(c, x + 4, oy + 3, on ? 0xFFFFFFFF : 0xFFC8C0D8);
                    hits.add(new Object[]{x, oy, w, 13, (Runnable) () -> send(s, c)});
                    x += w + 3;
                }
            }
            case TEXT -> {
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(s.label, lw - 4), px, oy + 4, 0xFFFFFFFF);
                GuiTextField tf = fields.computeIfAbsent(s.key, k -> {
                    GuiTextField t = new GuiTextField(fields.size(), fontRenderer, 0, 0, 100, 12);
                    t.setMaxStringLength(Math.max(8, s.max));
                    t.setText(s.value);
                    return t;
                });
                shownFields.add(s.key);
                tf.x = px + lw;
                tf.y = oy + 1;
                tf.width = pw - lw - 50;
                button(mx, my, px + pw - 44, oy, 44, 14, "Save", PrideFrame.BUTTON, () -> send(s, tf.getText()), "Save this text");
            }
        }
        oy += 17;
    }

    private void sizePage(int mx, int my) {
        fontRenderer.drawStringWithShadow("Size: " + String.format(java.util.Locale.ROOT, "%.2fx", scale), px, oy + 4, 0xFFFFFFFF);
        oy += 18;
        float[] steps = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f, 4f};
        int x = px;
        for (float v : steps) {
            String l = v + "x";
            int w = fontRenderer.getStringWidth(l) + 10;
            if (x + w > px + pw) { x = px; oy += 16; }
            boolean on = Math.abs(v - scale) < 0.01f;
            button(mx, my, x, oy, w, 14, l, on ? 0xFF6A3FA0 : PrideFrame.BUTTON, () -> {
                scale = v;
                RailMap.NETWORK.sendToServer(new PacketScale(pos, v));
            }, "Grow or shrink this piece so it fits tall Immersive Railroading trains");
            x += w + 3;
        }
        oy += 22;
    }

    private void send(Setting s, String v) {
        s.value = s.sanitize(v);   // instant feedback; the server's answer replaces it
        RailMap.NETWORK.sendToServer(PacketSettings.set(pos, s.key, s.value));
    }

    private void button(int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r, String tip) {
        if (PrideFrame.button(x, y, w, h, label, color, mx, my) && tip != null) hint = tip;
        hits.add(new Object[]{x, y, w, h, r});
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    private int lastMx;

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        lastMx = mx;
        for (Map.Entry<String, GuiTextField> e : fields.entrySet()) {
            if (shownFields.contains(e.getKey())) e.getValue().mouseClicked(mx, my, b);
            else e.getValue().setFocused(false);
        }
        if (b != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                ((Runnable) h[4]).run();
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.2F));
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        for (Map.Entry<String, GuiTextField> e : fields.entrySet()) {
            GuiTextField tf = e.getValue();
            if (!tf.isFocused()) continue;
            if (key == Keyboard.KEY_RETURN) {
                for (Setting s : settings) if (s.key.equals(e.getKey())) send(s, tf.getText());
                tf.setFocused(false);
                return;
            }
            tf.textboxKeyTyped(c, key);
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w != 0) scroll = Math.max(0, Math.min(Math.max(0, contentH - (bottom - top) + 8), scroll - (w > 0 ? 24 : -24)));
    }
}
