package com.dogpound.railmap.build6a.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.build6a.B6Config;
import com.dogpound.railmap.build6a.B6Net;
import com.dogpound.railmap.build6a.EntityHiRail;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.Locale;

/**
 * The hi-rail truck's cab panel: ignition key, guide-wheel lever, parking brake, beacon and work-light switches, horn
 * button, with the speedometer, the guide-wheel position bar, rail / road status and its warning lamps live.
 */
public class GuiHiRailCab extends GuiScreen {
    private final EntityHiRail t;
    private static final String[][] BTN = {{"ign", "IGNITION"}, {"gear", "GUIDE WHEELS"}, {"park", "PARK BRAKE"}, {"beacon", "BEACONS"}, {"work", "WORK LIGHTS"}, {"horn", "HORN"}};

    public GuiHiRailCab(EntityHiRail t) { this.t = t; }

    @Override
    public void initGui() {
        buttonList.clear();
        int x0 = width / 2 - 150, y0 = height / 2 + 10;
        for (int i = 0; i < BTN.length; i++) buttonList.add(new GuiButton(i, x0 + (i % 3) * 102, y0 + (i / 3) * 24, 96, 20, BTN[i][1]));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id >= 0 && b.id < BTN.length) RailMap.NETWORK.sendToServer(new B6Net(t.getEntityId(), BTN[b.id][0]));
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() { if (t.isDead || mc.player.getDistanceSq(t) > 64) mc.displayGuiScreen(null); }

    private void lamp(int x, int y, boolean on, int col, String label) {
        drawRect(x - 1, y - 1, x + 11, y + 11, 0xFF101010);
        drawRect(x, y, x + 10, y + 10, on ? 0xFF000000 | col : 0xFF000000 | ((col >> 2) & 0x3F3F3F));
        fontRenderer.drawString(label, x + 14, y + 1, 0xE0E0E0);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        int x0 = width / 2 - 156, y0 = height / 2 - 100;
        drawRect(x0, y0, x0 + 312, y0 + 168, 0xF0262B30);
        drawRect(x0, y0, x0 + 312, y0 + 14, 0xFF14324A);
        fontRenderer.drawString("HI-RAIL TRUCK - CAB PANEL", x0 + 6, y0 + 3, 0xFFFFFF);
        // speedometer
        drawRect(x0 + 8, y0 + 22, x0 + 110, y0 + 62, 0xFF07120C);
        String spd = String.format(Locale.ROOT, "%3d", Math.round(t.kmh()));
        GlScale(spd, x0 + 18, y0 + 28, 2.4f, 0x7CF29A);
        fontRenderer.drawString("km/h", x0 + 80, y0 + 48, 0x7CF29A);
        int lim = t.onRail() ? B6Config.hiRailRailKmh : B6Config.hiRailRoadKmh;
        fontRenderer.drawString("LIMIT " + lim, x0 + 12, y0 + 66, 0xFFD040);
        // guide wheel position
        int g = t.gear();
        fontRenderer.drawString("GUIDE WHEELS", x0 + 120, y0 + 22, 0xE0E0E0);
        drawRect(x0 + 120, y0 + 33, x0 + 300, y0 + 43, 0xFF101010);
        drawRect(x0 + 121, y0 + 34, x0 + 121 + g * 178 / 100, y0 + 42, g >= 100 ? 0xFF30E060 : 0xFFFFB020);
        fontRenderer.drawString(g >= 100 ? "DOWN - ON RAIL" : g <= 0 ? "UP - ROAD" : g + "%", x0 + 124, y0 + 46, 0xFFFFFF);
        fontRenderer.drawString(t.statusLine(), x0 + 120, y0 + 60, t.onRail() ? 0x8AD4FF : 0xE0E0E0);
        // lamps
        int ly = y0 + 82;
        lamp(x0 + 10, ly, t.has(EntityHiRail.F_IGN), 0x30E060, "ENGINE");
        lamp(x0 + 90, ly, t.has(EntityHiRail.F_PARK), 0xFF3030, "PARK");
        lamp(x0 + 160, ly, t.onRail(), 0x8AD4FF, "RAIL");
        lamp(x0 + 230, ly, t.has(EntityHiRail.F_OBST) || t.obst, 0xFF3030, "STOCK AHEAD");
        lamp(x0 + 10, ly + 16, t.has(EntityHiRail.F_BEACON), 0xFF8A00, "BEACONS");
        lamp(x0 + 90, ly + 16, t.has(EntityHiRail.F_WORK), 0xFFFFF0, "WORK LTS");
        lamp(x0 + 160, ly + 16, t.gear() > 0 && t.gear() < 100, 0xFFB020, "GEAR MOVING");
        lamp(x0 + 230, ly + 16, t.has(EntityHiRail.F_HEAD), 0xFFF4D0, "HEADLIGHTS");
        super.drawScreen(mx, my, pt);
        fontRenderer.drawString("W/S throttle + brake · A/D steer (road) · sneak to get out", x0 + 6, y0 + 156, 0x909090);
    }

    private void GlScale(String s, int x, int y, float k, int col) {
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(x, y, 0);
        net.minecraft.client.renderer.GlStateManager.scale(k, k, 1);
        fontRenderer.drawString(s, 0, 0, col);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }
}
