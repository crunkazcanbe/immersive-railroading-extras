package com.dogpound.railmap.client.gui;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.network.PacketSignText;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;

/** The little editor that pops up for a CUSTOM sign: type the text, it updates the sign live. */
public class GuiSignText extends GuiScreen {
    private static final int DONE = 0, CANCEL = 1;

    private final BlockPos pos;
    private final String initial;
    private GuiTextField field;
    private PrideFrame f;

    public GuiSignText(BlockPos pos, String initial) {
        this.pos = pos;
        this.initial = initial == null ? "" : initial;
    }

    @Override
    public void initGui() {
        f = PrideFrame.sized(width, height, 300, 110);
        field = new GuiTextField(0, fontRenderer, f.cx + 1, f.cy + 6, f.cw - 2, 20);
        field.setMaxStringLength(60);
        field.setText(initial);
        field.setFocused(true);
        field.setCanLoseFocus(false);
        int half = (f.cw - 4) / 2;
        buttonList.add(new PrideButton(DONE, f.cx, f.cy + 34, half, 20, "Set"));
        buttonList.add(new PrideButton(CANCEL, f.cx + f.cw - half, f.cy + 34, half, 20, "Cancel"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        f.draw(this, "Sign text", "\u00a77use | for a new line");
        field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Esc
            mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == 28 || keyCode == 156) { // Enter
            apply();
            return;
        }
        field.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        field.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) apply();
        else mc.displayGuiScreen(null);
    }

    private void apply() {
        RailMap.NETWORK.sendToServer(new PacketSignText(pos, field.getText()));
        mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
