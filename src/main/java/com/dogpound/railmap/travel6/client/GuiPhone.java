package com.dogpound.railmap.travel6.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.travel6.PacketPhone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Pride Rail phone app (batch 6b #7): a phone in your hand - status bar with the game clock, the app header with
 * the rainbow strip, five tabs (PLAN, LIVE, WALLET, TRAINS, ALERTS). PLAN: FROM / TO pickers (< >, SWAP, HERE) and GO;
 * LIVE: a station picker (< >, HERE) with departures refreshing every 5 s. Everything on the screen comes from the
 * server (PacketPhone); the wheel scrolls.
 */
public class GuiPhone extends GuiScreen {
    static final String[] TABS = {"PLAN", "LIVE", "WALLET", "TRAINS", "ALERTS"};
    static final String[] OPS = {"plan", "dep", "wallet", "trains", "alerts"};
    static final int[] RAINBOW = {0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF24408E, 0xFF732982};
    static final int PW = 236, PH = 330;

    private static GuiPhone open;
    private int tab = -1, from = -1, to = -1, dep = -1, scroll, ticks;
    private PacketPhone data = new PacketPhone();
    private boolean loading = true;
    private final List<int[]> hits = new ArrayList<>();

    public GuiPhone() { open = this; ask("home"); }

    /** the server's answer: show it if this screen is still up */
    public static void fromServer(PacketPhone m) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (open == null || Minecraft.getMinecraft().currentScreen != open) return;
            open.data = m;
            open.loading = false;
            if (open.from < 0 && !m.here.isEmpty()) open.from = m.stations.indexOf(m.here);
            if (open.dep < 0 && !m.here.isEmpty()) open.dep = m.stations.indexOf(m.here);
        });
    }

    void ask(String op, String... a) { loading = true; RailMap.NETWORK.sendToServer(PacketPhone.request(op, a)); }

    String st(int i) { return i >= 0 && i < data.stations.size() ? data.stations.get(i) : ""; }

    void request() {
        scroll = 0;
        if (tab < 0) ask("home");
        else if (tab == 0) { if (from >= 0 && to >= 0) ask("plan", st(from), st(to)); else { data.lines.clear(); data.lines.add(".Pick FROM and TO with < >, then GO"); data.title = "Journey planner"; } }
        else if (tab == 1) ask("dep", st(dep));
        else ask(OPS[tab]);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() {
        if (++ticks % 100 == 0 && (tab == 1 || tab == 3 || tab == 4)) request();
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) scroll = Math.max(0, scroll + (d > 0 ? -3 : 3));
    }

    private void hit(int x, int y, int w, int h, int action) { hits.add(new int[]{x, y, w, h, action}); }

    private void button(int x, int y, int w, int h, String label, int action, int col) {
        drawRect(x, y, x + w, y + h, col);
        drawRect(x, y + h - 1, x + w, y + h, 0x40000000);
        drawCenteredString(fontRenderer, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        hit(x, y, w, h, action);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        hits.clear();
        int x0 = (width - PW) / 2, y0 = Math.max(4, (height - PH) / 2);
        // body, bezel, screen
        drawRect(x0 - 6, y0 - 6, x0 + PW + 6, y0 + PH + 6, 0xFF101114);
        drawRect(x0 - 4, y0 - 4, x0 + PW + 4, y0 + PH + 4, 0xFF2A2C33);
        drawRect(x0, y0, x0 + PW, y0 + PH, 0xFF0E1420);
        drawRect(x0 + PW / 2 - 24, y0 - 3, x0 + PW / 2 + 24, y0 + 3, 0xFF050505);           // the earpiece / camera notch
        // status bar: clock, network, battery
        long t = mc.world == null ? 0 : (mc.world.getWorldTime() + 6000) % 24000;
        fontRenderer.drawString(String.format(Locale.ROOT, "%02d:%02d", t / 1000, t % 1000 * 60 / 1000), x0 + 6, y0 + 5, 0xFFE8ECF0);
        for (int i = 0; i < 4; i++) drawRect(x0 + PW - 46 + i * 4, y0 + 11 - i * 2, x0 + PW - 43 + i * 4, y0 + 12, 0xFFE8ECF0);
        drawRect(x0 + PW - 24, y0 + 5, x0 + PW - 8, y0 + 12, 0xFFE8ECF0);
        drawRect(x0 + PW - 23, y0 + 6, x0 + PW - 12, y0 + 11, 0xFF30E060);
        drawRect(x0 + PW - 8, y0 + 7, x0 + PW - 6, y0 + 10, 0xFFE8ECF0);
        // app header + rainbow strip
        drawRect(x0, y0 + 16, x0 + PW, y0 + 36, 0xFF1C2840);
        fontRenderer.drawString("PRIDE RAIL", x0 + 8, y0 + 22, 0xFFFFFFFF);
        fontRenderer.drawString(loading ? "..." : data.title, x0 + 76, y0 + 22, 0xFF8AD4FF);
        for (int i = 0; i < 6; i++) drawRect(x0 + i * PW / 6, y0 + 36, x0 + (i + 1) * PW / 6, y0 + 38, RAINBOW[i]);
        hit(x0, y0 + 16, 70, 20, 50);                                                     // PRIDE RAIL = home
        int cy = y0 + 42;
        // pickers
        if (tab == 0) {
            cy = picker(x0, cy, "FROM", from, 10);
            cy = picker(x0, cy, "TO", to, 20);
            button(x0 + 8, cy, 60, 14, "SWAP", 30, 0xFF3A4A6A);
            button(x0 + 72, cy, 60, 14, "HERE", 31, 0xFF3A4A6A);
            button(x0 + PW - 68, cy, 60, 14, "GO", 32, 0xFF1FA34A);
            cy += 18;
        } else if (tab == 1) {
            cy = picker(x0, cy, "AT", dep, 40);
            button(x0 + 8, cy, 60, 14, "HERE", 41, 0xFF3A4A6A);
            button(x0 + PW - 68, cy, 60, 14, "REFRESH", 42, 0xFF2050D0);
            cy += 18;
        }
        // content
        int bottom = y0 + PH - 26;
        drawRect(x0 + 4, cy, x0 + PW - 4, bottom, 0xFF0A0F18);
        float sc = 0.75f;
        List<String> wrapped = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        int wrapW = (int) ((PW - 16) / sc);
        for (String raw : data.lines) {
            int col = 0xFFE8ECF0;
            String s = raw;
            if (!s.isEmpty()) switch (s.charAt(0)) {
                case '!' -> { col = 0xFFFF5252; s = s.substring(1); }
                case '~' -> { col = 0xFFFFC44D; s = s.substring(1); }
                case '+' -> { col = 0xFF7CF29A; s = s.substring(1); }
                case '.' -> { col = 0xFF8A94A0; s = s.substring(1); }
                case '#' -> { col = 0xFF5BCEFA; s = s.substring(1); }
                default -> { }
            }
            List<String> parts = s.isEmpty() ? java.util.Collections.singletonList("") : fontRenderer.listFormattedStringToWidth(s, wrapW);
            for (String q : parts) { wrapped.add(q); cols.add(col); }
        }
        int lh = 9, rows = (int) ((bottom - cy - 6) / (lh * sc));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, wrapped.size() - rows)));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x0 + 8, cy + 4, 0);
        GlStateManager.scale(sc, sc, 1);
        if (loading && wrapped.isEmpty()) fontRenderer.drawString("Loading...", 0, 0, 0xFF8A94A0);
        for (int i = 0; i < rows && scroll + i < wrapped.size(); i++) fontRenderer.drawString(wrapped.get(scroll + i), 0, i * lh, cols.get(scroll + i));
        GlStateManager.popMatrix();
        if (wrapped.size() > rows) {                                                     // scroll bar
            int h = bottom - cy, bh = Math.max(10, h * rows / wrapped.size()), by = cy + (h - bh) * scroll / Math.max(1, wrapped.size() - rows);
            drawRect(x0 + PW - 7, by, x0 + PW - 5, by + bh, 0xFF5BCEFA);
        }
        // tab bar
        int tw = PW / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int bx = x0 + i * tw;
            drawRect(bx, bottom + 2, bx + tw - 1, y0 + PH - 4, tab == i ? 0xFF3A6FD8 : 0xFF1C2840);
            drawCenteredString(fontRenderer, TABS[i], bx + tw / 2, bottom + 8, tab == i ? 0xFFFFFFFF : 0xFFB0B8C4);
            hit(bx, bottom + 2, tw - 1, 22, 100 + i);
        }
        drawRect(x0 + PW / 2 - 20, y0 + PH + 1, x0 + PW / 2 + 20, y0 + PH + 3, 0xFF8A94A0);   // home indicator
        super.drawScreen(mx, my, pt);
    }

    private int picker(int x0, int cy, String label, int idx, int base) {
        fontRenderer.drawString(label, x0 + 8, cy + 3, 0xFF8A94A0);
        button(x0 + 36, cy, 16, 14, "<", base, 0xFF3A4A6A);
        drawRect(x0 + 54, cy, x0 + PW - 26, cy + 14, 0xFF1C2433);
        String s = idx < 0 ? "- pick -" : st(idx);
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(s, PW - 84), x0 + 54 + (PW - 80) / 2, cy + 3, 0xFFFFD040);
        button(x0 + PW - 24, cy, 16, 14, ">", base + 1, 0xFF3A4A6A);
        return cy + 17;
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws java.io.IOException {
        super.mouseClicked(mx, my, b);
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            if (mx < h[0] || mx >= h[0] + h[2] || my < h[1] || my >= h[1] + h[3]) continue;
            act(h[4]);
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.4f));
            return;
        }
    }

    private int step(int i, int d) { int n = data.stations.size(); return n == 0 ? -1 : Math.floorMod((i < 0 ? 0 : i + d), n); }

    private void act(int a) {
        int here = data.stations.indexOf(data.here);
        switch (a) {
            case 10 -> from = step(from, -1);
            case 11 -> from = step(from, 1);
            case 20 -> to = step(to, -1);
            case 21 -> to = step(to, 1);
            case 30 -> { int x = from; from = to; to = x; }
            case 31 -> from = here;
            case 32 -> request();
            case 40 -> { dep = step(dep, -1); request(); }
            case 41 -> { dep = here; request(); }
            case 42 -> request();
            case 50 -> { tab = -1; request(); }
            default -> { if (a >= 100) { tab = a - 100; request(); } }
        }
    }

    @Override public void onGuiClosed() { if (open == this) open = null; }
}
