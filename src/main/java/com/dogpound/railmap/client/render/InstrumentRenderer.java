package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.TileInstrument;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

import java.util.Locale;

/**
 * Switchboard instruments (requested feature): a square black bezel with a cream dial, 90° scale, ticks, figures and a
 * needle that eases to the reading; the readout screen; the status lamp. Built in model pixels facing north (front at
 * z = 13, wall behind at z = 16), turned like the block; u runs from the viewer's left, v up.
 */
public class InstrumentRenderer extends TileEntitySpecialRenderer<TileInstrument> {
    private static final double FACE = 13;

    @Override
    public void render(TileInstrument te, double x, double y, double z, float pt, int destroyStage, float alpha) {
        if (te.getWorld() == null || x * x + y * y + z * z > 48 * 48) return;
        IBlockState st = te.getWorld().getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockInstrument b)) return;
        EnumFacing f = st.getValue(BlockInstrument.FACING);
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        long now = te.getWorld().getTotalWorldTime();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        int light = te.getWorld().getCombinedLight(te.getPos().offset(f), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        boolean close = x * x + y * y + z * z < 14 * 14;

        Prims.begin();
        box(1, 1, 15, 15, -3, 0, 0x1C1C1C);                                     // case, 3 px off the wall
        switch (b.inst) {
            case SCREEN: box(2, 2.5, 14, 13.5, 0, 0.1, 0x050A06); break;
            case LAMP: {
                boolean lit = te.lamp == 1 || te.lamp == 2 || (te.lamp == 3 && ((now / 8) & 1) == 0);
                int col = te.lamp == 1 ? 0x22C04A : te.lamp == 2 ? 0xD02020 : 0xF0A020;
                box(4, 4.5, 12, 12.5, 0, 0.4, 0x303030);                             // bezel ring
                if (lit) {
                    Prims.emissive(true);
                    box(5, 5.5, 11, 11.5, 0.4, 1.4, col);
                    Prims.glow(16 - 8, 8.5, FACE - 1.5, 6, col);
                    GlStateManager.enableLighting();
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
                } else box(5, 5.5, 11, 11.5, 0.4, 1.4, Prims.shade(te.lamp == 0 ? 0x555555 : col, 0.35f));
                break;
            }
            default: {
                box(2, 2, 14, 14, 0, 0.1, 0xEFE9D8);                                // cream dial
                // scale: 90° arc, pivot low and centred (classic switchboard meter)
                double cu = 8, cv = 3.2, r = 9.2;
                for (int i = 0; i <= 10; i++) {
                    double a = Math.toRadians(-45 + 9 * i);
                    double len = i % 5 == 0 ? 1.3 : 0.7;
                    bar(cu, cv, Math.toDegrees(a), 0.07, r - len, r, 0.1, 0.18, 0x111111);
                }
                bar(cu, cv, -45, 0.06, r - 0.15, r, 0.1, 0.15, 0x111111);
                // red zone from 100 % to 125 % of the nominal span (full scale is 125 %)
                for (int i = 8; i <= 10; i++) bar(cu, cv, -45 + 9 * i, 0.22, r + 0.15, r + 0.55, 0.1, 0.16, 0xC01818);
                // needle eases to the reading
                double frac = Math.max(0, Math.min(1.05, te.value / Math.max(1e-9, te.full)));
                double target = -45 + 90 * frac;
                double tNow = now + pt;
                if (te.needleAt == 0) { te.needle = (float) target; te.needleAt = (long) tNow; }
                double dt = Math.max(0, Math.min(5, tNow - te.needleAt));
                te.needleAt = (long) tNow;
                double step = 6 * Math.max(dt, 0.05);
                te.needle += (float) Math.max(-step, Math.min(step, target - te.needle));
                bar(cu, cv, te.needle, 0.12, -0.8, r - 0.4, 0.25, 0.4, frac > 1 ? 0xD01010 : 0x0C0C0C);
                box(cu - 0.55, cv - 0.55, cu + 0.55, cv + 0.55, 0.1, 0.55, 0x2A2A2A);       // pivot boss
                break;
            }
        }
        box(1, 1, 15, 1.6, 0, 0.3, 0x2A2A2A); box(1, 14.4, 15, 15, 0, 0.3, 0x2A2A2A); // bezel lips
        box(1, 1, 1.6, 15, 0, 0.3, 0x2A2A2A); box(14.4, 1, 15, 15, 0, 0.3, 0x2A2A2A);
        Prims.end();

        if (close) {
            GlStateManager.disableLighting();
            switch (b.inst) {
                case SCREEN:
                    text(fr, te.line1, 8, 11.0, 0.95, 11.4, 0x50FF70, true);
                    text(fr, te.line2, 8, 8.2, 0.95, 11.4, 0x50FF70, true);
                    text(fr, te.line3, 8, 5.4, 0.95, 11.4, 0x50FF70, true);
                    text(fr, te.status.length() > 26 ? te.status.substring(0, 26) : te.status, 8, 3.2, 0.7, 11.4, te.status.equals("LIVE") ? 0x50FF70 : 0xFF6050, true);
                    break;
                case LAMP: text(fr, b.inst.label.toUpperCase(Locale.ROOT), 8, 2.3, 0.75, 13, 0xDDDDDD, false); break;
                default: {
                    double cu = 8, cv = 3.2, r = 9.2;
                    String[] figs = {"0", fig(te.full * 0.4, te.unit), fig(te.full * 0.8, te.unit)};
                    double[] at = {-45, -9, 27};
                    for (int i = 0; i < 3; i++) {
                        double a = Math.toRadians(at[i]);
                        text(fr, figs[i], cu + Math.sin(a) * (r - 2.4), cv + Math.cos(a) * (r - 2.4) - 0.35, 0.65, 4, 0x111111, false);
                    }
                    text(fr, te.unit, 8, 8.0, 1.3, 6, 0x111111, false);
                    text(fr, b.inst.label.toUpperCase(Locale.ROOT), 8, 2.4, 0.55, 10, 0x333333, false);
                    if (te.target() == null) text(fr, "NOT LINKED", 8, 6.2, 0.6, 10, 0xB01010, false);
                    break;
                }
            }
            GlStateManager.enableLighting();
        }
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    /** scale figure: the value converted to the meter's unit */
    private static String fig(double v, String unit) {
        double k = unit.startsWith("k") ? 1e3 : unit.startsWith("M") ? 1e6 : 1;
        if (unit.equals("PF")) return String.format(Locale.ROOT, "%.1f", v);
        double s = v / k;
        return s >= 100 ? String.format(Locale.ROOT, "%.0f", s) : s >= 10 ? String.format(Locale.ROOT, "%.0f", s) : String.format(Locale.ROOT, "%.1f", s);
    }

    private static void box(double u0, double v0, double u1, double v1, double d0, double d1, int rgb) {
        Prims.box(16 - u1, v0, FACE - d1, 16 - u0, v1, FACE - d0, rgb);
    }

    /** a bar from (u, v) turned `ang` degrees clockwise from up, from radius r0 to r1 */
    private static void bar(double u, double v, double ang, double hw, double r0, double r1, double d0, double d1, int rgb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - u, v, 0);
        GlStateManager.rotate((float) ang, 0, 0, 1);
        Prims.box(-hw, r0, FACE - d1, hw, r1, FACE - d0, rgb);
        GlStateManager.popMatrix();
    }

    private static void text(FontRenderer fr, String s, double u, double v, double heightPx, double maxW, int rgb, boolean glow) {
        if (s == null || s.isEmpty()) return;
        int w = fr.getStringWidth(s);
        double h = Math.min(heightPx, maxW * 8.0 / Math.max(1, w));
        float k = (float) (h / 8.0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - u, v + h / 2, FACE - 0.3);
        GlStateManager.rotate(180f, 0, 0, 1);
        GlStateManager.scale(k, k, k);
        if (glow) OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        fr.drawString(s, -w / 2, 0, rgb);
        GlStateManager.popMatrix();
    }
}
