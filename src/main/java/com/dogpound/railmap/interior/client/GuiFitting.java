package com.dogpound.railmap.interior.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.gui.PrideFrame;
import com.dogpound.railmap.interior.Interior;
import com.dogpound.railmap.interior.PacketInterior;
import net.minecraft.client.gui.GuiScreen;

/** Interior Fitting Kit: pick a car's layout (automatic by name, none, coach, café, sleeper). */
public class GuiFitting extends GuiScreen {
    private final int entity, current;
    private final String car;
    private final boolean ok;
    private PrideFrame f;

    public GuiFitting(int entity, String car, int current, boolean ok) { this.entity = entity; this.car = car; this.current = current; this.ok = ok; }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        f = PrideFrame.sized(width, height, 460, 300);
        f.draw(this, "Interior Fitting Kit", car);
        if (!ok) {
            fontRenderer.drawSplitString("§cOnly Immersive Railroading passenger cars can be fitted out.", f.cx, f.cy + 6, f.cw, 0xFFFFFF);
            return;
        }
        fontRenderer.drawStringWithShadow("§7Now: §f" + Interior.Layout.of(current).label + "  §8(one kit per fit-out; storage contents come back to you)", f.cx, f.cy + 2, 0xFFFFFF);
        int y = f.cy + 16, rh = (f.ch - 20) / 6;
        String[] title = {"Automatic (by the car's name)", Interior.Layout.NONE.label, Interior.Layout.COACH.label, Interior.Layout.CAFE.label, Interior.Layout.SLEEPER.label, Interior.Layout.TPO.label};
        String[] info = {"dining / buffet -> café, sleeper / couchette -> sleeper, mail / post / TPO -> TPO, coach / passenger -> coach", Interior.Layout.NONE.info, Interior.Layout.COACH.info, Interior.Layout.CAFE.info, Interior.Layout.SLEEPER.info, Interior.Layout.TPO.info};
        int[] acc = {PrideFrame.DIM, 0xFF606060, PrideFrame.BLUE, 0xFFE0A040, PrideFrame.PINK, 0xFFC03030};
        for (int i = 0; i < 6; i++) {
            int ty = y + i * rh;
            boolean over = mx >= f.cx && mx < f.cx + f.cw && my >= ty && my < ty + rh - 3;
            PrideFrame.tile(f.cx, ty, f.cw, rh - 3, acc[i], over, i >= 1 && i - 1 == current);
            fontRenderer.drawStringWithShadow("§l" + title[i], f.cx + 8, ty + 5, 0xFFFFFF);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(info[i], f.cw - 16), f.cx + 8, ty + 17, 0xB8B0C8);
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        super.mouseClicked(mx, my, button);
        if (f == null || !ok) return;
        int y = f.cy + 16, rh = (f.ch - 20) / 6;
        for (int i = 0; i < 6; i++) {
            int ty = y + i * rh;
            if (mx >= f.cx && mx < f.cx + f.cw && my >= ty && my < ty + rh - 3) {
                RailMap.NETWORK.sendToServer(new PacketInterior(PacketInterior.FIT, entity, 0, -1, i - 1));
                mc.displayGuiScreen(null);
                return;
            }
        }
    }
}
