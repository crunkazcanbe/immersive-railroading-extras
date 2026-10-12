package com.dogpound.railmap.cctv.client;

import com.dogpound.railmap.client.gui.PrideFrame;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** Shared bits of the CCTV screens (house PrideFrame look): immediate-mode buttons and a picture drawn through the CCTV shader. */
abstract class CctvGui extends GuiScreen {
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    protected int mx, my;

    @Override public boolean doesGuiPauseGame() { return false; }   // the world (and the live feed) keeps running

    protected void startHits() { hits.clear(); acts.clear(); }

    protected boolean btn(int x, int y, int w, int h, String label, int col, Runnable act) {
        boolean over = PrideFrame.button(x, y, w, h, label, col, mx, my);
        hits.add(new int[]{x, y, w, h});
        acts.add(act);
        return over;
    }

    protected void area(int x, int y, int w, int h, Runnable act) { hits.add(new int[]{x, y, w, h}); acts.add(act); }

    @Override
    protected void mouseClicked(int x, int y, int b) throws java.io.IOException {
        super.mouseClicked(x, y, b);
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] r = hits.get(i);
            if (x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3]) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1f));
                acts.get(i).run();
                return;
            }
        }
    }

    /** a camera picture in the GUI (x, y top-left, w x h) */
    protected static void picture(CctvClient.Pic p, int x, int y, int w, int h, boolean dewarp) {
        if (p.tex < 0) {
            Gui.drawRect(x, y, x + w, y + h, 0xFF102058);
            String m = p.msg.isEmpty() ? "NO SIGNAL" : p.msg;
            net.minecraft.client.Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(m, x + (w - net.minecraft.client.Minecraft.getMinecraft().fontRenderer.getStringWidth(m)) / 2f, y + h / 2f - 4, 0xFFFFFF);
            return;
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.bindTexture(p.tex);
        int prog = CctvShader.begin(p.look, p.gain, p.fisheye && !dewarp, (p.fx & 1) != 0 ? 0.12f : 0f, (p.fx & 2) != 0 ? 1.5f : 0f, p.size);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        double v0 = p.flip ? 1 : 0, v1 = p.flip ? 0 : 1;
        b.pos(x, y + h, 0).tex(0, v1).color(1f, 1f, 1f, 1f).endVertex();
        b.pos(x + w, y + h, 0).tex(1, v1).color(1f, 1f, 1f, 1f).endVertex();
        b.pos(x + w, y, 0).tex(1, v0).color(1f, 1f, 1f, 1f).endVertex();
        b.pos(x, y, 0).tex(0, v0).color(1f, 1f, 1f, 1f).endVertex();
        t.draw();
        CctvShader.end(prog);
        GlStateManager.enableAlpha();
        if (p.live) for (int[] m : p.masks) Gui.drawRect(x + m[0] * w / 1000, y + m[1] * h / 1000, x + m[2] * w / 1000, y + m[3] * h / 1000, 0xFF000000);
    }
}
