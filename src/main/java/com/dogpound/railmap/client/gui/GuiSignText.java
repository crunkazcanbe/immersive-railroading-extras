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

    public GuiSignText(BlockPos pos, String initial) {
        this.pos = pos;
        this.initial = initial == null ? "" : initial;
    }

    @Override
    public void initGui() {
        int cx = width / 2, cy = height / 2;
        field = new GuiTextField(0, fontRenderer, cx - 130, cy - 10, 260, 20);
        field.setMaxStringLength(60);
        field.setText(initial);
        field.setFocused(true);
        field.setCanLoseFocus(false);
        buttonList.add(new GuiButton(DONE, cx - 130, cy + 18, 128, 20, "Set"));
        buttonList.add(new GuiButton(CANCEL, cx + 2, cy + 18, 128, 20, "Cancel"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Sign text  (use | for a new line)", width / 2, height / 2 - 34, 0xFFFFFF);
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
