package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.network.PacketScale;
import com.dogpound.railmap.signal.IScalable;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.client.config.GuiSlider;

/** The Signal Wrench's size slider: resizes one trackside piece live while you drag. */
public class GuiScale extends GuiScreen implements GuiSlider.ISlider {
    private static final int SLIDER = 0, RESET = 1, DONE = 2;

    private final BlockPos pos;
    private final String what;
    private float scale;
    private float sent;
    private GuiSlider slider;

    public GuiScale(BlockPos pos, String what, float scale) {
        this.pos = pos;
        this.what = what;
        this.scale = scale;
        this.sent = scale;
    }

    @Override
    public void initGui() {
        int cx = width / 2, cy = height / 2;
        slider = new GuiSlider(SLIDER, cx - 100, cy - 10, 200, 20, "Size: ", "x",
                IScalable.MIN, IScalable.MAX, scale, true, true, this);
        slider.precision = 2;
        slider.updateSlider();
        buttonList.add(slider);
        buttonList.add(new GuiButton(RESET, cx - 100, cy + 16, 98, 20, "Normal size"));
        buttonList.add(new GuiButton(DONE, cx + 2, cy + 16, 98, 20, "Done"));
    }

    @Override
    public void onChangeSliderValue(GuiSlider s) {
        scale = Math.round((float) s.getValue() * 20f) / 20f;   // 0.05 steps
        send();
    }

    private void send() {
        if (Math.abs(scale - sent) < 0.001f) return;
        sent = scale;
        RailMap.NETWORK.sendToServer(new PacketScale(pos, scale));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == RESET) {
            scale = 1f;
            slider.setValue(1.0);
            slider.updateSlider();
            send();
        } else if (b.id == DONE) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(width / 2 - 110, height / 2 - 40, width / 2 + 110, height / 2 + 44, 0xC0101418, 0xC0101418);
        drawCenteredString(fontRenderer, what, width / 2, height / 2 - 30, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
