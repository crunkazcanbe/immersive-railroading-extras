package com.dogpound.railmap.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** A vanilla GuiButton (same ids, clicks, sounds) drawn in the PrideFrame house style. */
public class PrideButton extends GuiButton {
    public PrideButton(int id, int x, int y, int w, int h, String label) {
        super(id, x, y, w, h, label);
    }

    @Override
    public void drawButton(Minecraft mc, int mx, int my, float partialTicks) {
        if (!visible) return;
        hovered = mx >= x && my >= y && mx < x + width && my < y + height;
        if (enabled) {
            PrideFrame.button(x, y, width, height, displayString, PrideFrame.BUTTON, mx, my);
        } else {
            drawRect(x, y, x + width, y + height, 0xFF18131F);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, 0xFF5A5466);
        }
    }
}
