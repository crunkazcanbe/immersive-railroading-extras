package com.dogpound.railmap.ops6.client;

import com.dogpound.railmap.client.gui.PrideFrame;
import com.dogpound.railmap.ops6.PacketOps6;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A device's screen (crane LMI, master clock, lubricator, sim instructor): tabbed pages the server fills (PacketOps6
 * Screen) - text lines, bar graphs and buttons - refreshed twice a second while it is open.
 */
public class GuiOps6Screen extends GuiScreen {
    final BlockPos pos;
    private NBTTagCompound d;
    private int page, tick;
    private final List<int[]> hits = new ArrayList<>();   // x, y, w, h, kind (0 tab, 1 button), index
    private final List<String> hitIds = new ArrayList<>();

    GuiOps6Screen(BlockPos pos, NBTTagCompound d) { this.pos = pos; this.d = d; page = d.getInteger("page"); }

    void update(NBTTagCompound n) { d = n; }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void updateScreen() {
        if (++tick % 10 == 0) Ops6Client.send(PacketOps6.of("poll").pos(pos).num("page", page));
        if (mc.player != null && mc.player.getDistanceSq(pos) > 14 * 14) mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        PrideFrame f = PrideFrame.sized(width, height, 560, 330);
        f.draw(this, d.getString("title"), d.getString("right"));
        hits.clear(); hitIds.clear();
        NBTTagList tabs = d.getTagList("tabs", 8);
        int x = f.cx, y = f.cy;
        for (int i = 0; i < tabs.tagCount(); i++) {
            String s = tabs.getStringTagAt(i);
            int w = fontRenderer.getStringWidth(s) + 16;
            PrideFrame.tile(x, y, w, 16, PrideFrame.RAINBOW[i % PrideFrame.RAINBOW.length], in(mx, my, x, y, w, 16), i == page);
            fontRenderer.drawStringWithShadow(s, x + 8, y + 4, 0xFFFFFF);
            hits.add(new int[]{x, y, w, 16, 0, i}); hitIds.add("");
            x += w + 4;
        }
        y += 22;
        NBTTagList bars = d.getTagList("bars", 8);
        for (int i = 0; i < bars.tagCount(); i++) {
            String[] b = bars.getStringTagAt(i).split("\\|", 5);
            if (b.length < 5) continue;
            double v = Double.parseDouble(b[1]), max = Math.max(1e-6, Double.parseDouble(b[2]));
            int col = 0xFF000000 | Integer.parseInt(b[3]);
            fontRenderer.drawStringWithShadow("§7" + b[0], f.cx, y + 2, 0xFFFFFF);
            int bx = f.cx + 120, bw = f.cw - 130;
            Gui.drawRect(bx, y, bx + bw, y + 12, 0x60000000);
            Gui.drawRect(bx, y, bx + (int) (bw * Math.max(0, Math.min(1, v / max))), y + 12, col);
            fontRenderer.drawStringWithShadow(b[4], bx + 4, y + 2, 0xFFFFFF);
            y += 16;
        }
        NBTTagList lines = d.getTagList("lines", 8);
        NBTTagList btns = d.getTagList("buttons", 8);
        int rows = (btns.tagCount() + 3) / 4;
        int maxY = f.y + f.h - 12 - rows * 20;
        PrideFrame.card(f.cx, y, f.cw, Math.max(10, maxY - y - 4), PrideFrame.BLUE);
        int ly = y + 4;
        for (int i = 0; i < lines.tagCount() && ly < maxY - 12; i++) {
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(lines.getStringTagAt(i), f.cw - 8), f.cx + 4, ly, 0xFFFFFF);
            ly += 11;
        }
        int bw = (f.cw - 12) / 4;
        for (int i = 0; i < btns.tagCount(); i++) {
            String[] b = btns.getStringTagAt(i).split("\\|", 3);
            if (b.length < 3) continue;
            int bx = f.cx + (i % 4) * (bw + 4), by = maxY + (i / 4) * 20;
            PrideFrame.button(bx, by, bw, 16, b[1], 0xFF000000 | Integer.parseInt(b[2]), mx, my);
            hits.add(new int[]{bx, by, bw, 16, 1, i}); hitIds.add(b[0]);
        }
        super.drawScreen(mx, my, pt);
    }

    static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        super.mouseClicked(mx, my, button);
        for (int i = 0; i < hits.size(); i++) {
            int[] h = hits.get(i);
            if (!in(mx, my, h[0], h[1], h[2], h[3])) continue;
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1f));
            if (h[4] == 0) { page = h[5]; Ops6Client.send(PacketOps6.of("poll").pos(pos).num("page", page)); }
            else Ops6Client.send(PacketOps6.of("act").pos(pos).num("page", page).str("a", hitIds.get(i)));
            return;
        }
    }
}
