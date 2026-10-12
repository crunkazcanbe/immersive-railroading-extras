package com.dogpound.railmap.mail.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.mail.ItemMail;
import com.dogpound.railmap.mail.PacketMail;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/** A sheet of writing paper: To + eight lines (write mode), or a letter you hold (read mode, with its postmark). */
public class GuiLetter extends GuiScreen {
    private static final int W = 240, H = 200, LINES = 8;
    private final EnumHand hand;
    private final ItemStack reading;
    private GuiTextField to;
    private final List<GuiTextField> body = new ArrayList<>();

    public GuiLetter(EnumHand hand, ItemStack reading) { this.hand = hand; this.reading = reading == null ? null : reading.copy(); }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        body.clear();
        if (reading == null) {
            to = new GuiTextField(0, fontRenderer, x0() + 34, y0() + 26, W - 46, 14);
            to.setMaxStringLength(48);
            to.setFocused(true);
            for (int i = 0; i < LINES; i++) {
                GuiTextField f = new GuiTextField(i + 1, fontRenderer, x0() + 12, y0() + 50 + i * 15, W - 24, 13);
                f.setMaxStringLength(60);
                f.setEnableBackgroundDrawing(false);
                body.add(f);
            }
            buttonList.add(new GuiButton(1, x0() + 12, y0() + H - 26, 104, 20, "Seal the envelope"));
        }
        buttonList.add(new GuiButton(0, x0() + W - 116, y0() + H - 26, 104, 20, reading == null ? "Cancel" : "Done"));
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            List<String> l = new ArrayList<>();
            for (GuiTextField f : body) l.add(f.getText());
            while (!l.isEmpty() && l.get(l.size() - 1).trim().isEmpty()) l.remove(l.size() - 1);
            RailMap.NETWORK.sendToServer(new PacketMail(hand, to.getText(), l));
        }
        mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        if (reading != null) return;
        if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_RETURN) {                     // next field
            List<GuiTextField> all = new ArrayList<>();
            all.add(to); all.addAll(body);
            for (int i = 0; i < all.size(); i++) if (all.get(i).isFocused()) { all.get(i).setFocused(false); all.get((i + 1) % all.size()).setFocused(true); break; }
            return;
        }
        to.textboxKeyTyped(c, key);
        for (GuiTextField f : body) f.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws java.io.IOException {
        super.mouseClicked(mx, my, btn);
        if (reading != null) return;
        to.mouseClicked(mx, my, btn);
        for (GuiTextField f : body) f.mouseClicked(mx, my, btn);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        int x = x0(), y = y0();
        drawRect(x + 3, y + 3, x + W + 3, y + H + 3, 0x60000000);                  // shadow
        drawRect(x, y, x + W, y + H, 0xFFF4EEDC);                                   // the paper
        for (int i = 0; i < LINES; i++) drawRect(x + 10, y + 61 + i * 15, x + W - 10, y + 62 + i * 15, 0xFFB8C8E0);   // ruled lines
        drawRect(x + 26, y + 6, x + 27, y + H - 30, 0xFFE0A0A0);                    // margin
        drawRect(x + W - 30, y + 6, x + W - 8, y + 30, 0xFFC03040);                  // the stamp
        drawRect(x + W - 28, y + 8, x + W - 10, y + 28, 0xFFE8D0A0);
        fontRenderer.drawString("♥", x + W - 22, y + 14, 0xC03040);
        if (reading == null) {
            fontRenderer.drawString("PRIDE POST - writing paper", x + 34, y + 10, 0x6A5030);
            fontRenderer.drawString("To:", x + 12, y + 29, 0x303030);
            to.drawTextBox();
            for (GuiTextField f : body) f.drawTextBox();
            fontRenderer.drawString("Alex  |  Alex, Riverside  |  Riverside", x + 12, y + H - 40, 0x8A7A60);
        } else {
            String pm = ItemMail.postmark(reading);
            fontRenderer.drawString("To: " + ItemMail.to(reading), x + 34, y + 12, 0x202020);
            fontRenderer.drawString("From: " + ItemMail.from(reading), x + 34, y + 26, 0x404040);
            if (!pm.isEmpty()) fontRenderer.drawString(pm, x + W - 8 - fontRenderer.getStringWidth(pm), y + 36, 0x4060A0);
            List<String> l = ItemMail.body(reading);
            for (int i = 0; i < Math.min(LINES, l.size()); i++) fontRenderer.drawString(l.get(i), x + 12, y + 51 + i * 15, 0x1A1A40);
        }
        super.drawScreen(mx, my, pt);
    }
}
