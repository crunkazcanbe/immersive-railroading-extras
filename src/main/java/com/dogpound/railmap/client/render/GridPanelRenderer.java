package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.BlockGrid;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.TileGrid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.EnumFacing;

import java.util.Locale;

/**
 * Draws a grid machine's real control panel live (requested feature): lamps light and blink, selector knobs and key
 * switches point at their position, needles move, displays glow, push buttons sink in when pressed, E-stops latch, and
 * operating handles SWING between positions (eased, so you watch the breaker handle travel). Layouts: {@link Panel}.
 * Face space: u from the viewer's left, v up, d = depth toward the viewer, all in model pixels; the model is built
 * facing north, so u maps to model x = 16 - u and "toward the viewer" is -z.
 */
public final class GridPanelRenderer {
    private GridPanelRenderer() {}

    private static double face;                       // model z of the current panel's face
    private static int lightU, lightV;                // the block light here, restored after each lit lamp

    public static void render(TileGrid te, double x, double y, double z, float pt) {
        Panel p = Panel.of(te.kind());
        if (p == null || te.getWorld() == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        double dist2 = x * x + y * y + z * z;
        if (dist2 > 40 * 40) return;
        IBlockState st = te.getWorld().getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockGrid) || !st.getPropertyKeys().contains(BlockGrid.FACING)) return;
        EnumFacing facing = st.getValue(BlockGrid.FACING);
        int turns = facing.getHorizontalIndex() == 2 ? 0 : facing.getHorizontalIndex() == 3 ? 1 : facing.getHorizontalIndex() == 0 ? 2 : 3;
        long now = te.getWorld().getTotalWorldTime();
        boolean blinkOn = ((now / 8) & 1) == 0;
        boolean lampTest = te.pv("lamptest") > 0;
        face = p.face;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);                         // same turn the blockstate gives the model
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        int light = te.getWorld().getCombinedLight(te.getPos().offset(facing), 0);
        lightU = light & 0xFFFF; lightV = light >> 16;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);

        Prims.begin();
        // the cabinet / door the controls are mounted on
        double back = p.face < 0 ? 0 : p.face + 0.2;
        box(p.u0, p.v0, p.u1, p.v1, -(back - p.face), 0, p.plate);
        for (Panel.C c : p.cs) drawShape(te, c, now, pt, blinkOn, lampTest);
        Panel.C lockAt = te.pv("loto") == 1 ? lockPoint(p) : null;
        if (lockAt != null) drawPadlock(lockAt.u + 1.3, lockAt.v - 0.4);
        Prims.end();

        if (dist2 < 12 * 12) {                                                 // engraving / displays: only up close
            FontRenderer fr = mc.fontRenderer;
            GlStateManager.disableLighting();
            for (Panel.C c : p.cs) drawText(fr, te, c);
            if (lockAt != null) {
                text(fr, "DANGER", lockAt.u + 1.3, lockAt.v - 2.15, 0.36, 0.32, 0xC01010, false);
                text(fr, "DO NOT OPERATE", lockAt.u + 1.3, lockAt.v - 2.5, 0.36, 0.17, 0x101010, false);
                text(fr, te.ptext("lotoBy"), lockAt.u + 1.3, lockAt.v - 2.8, 0.36, 0.2, 0x101010, false);
            }
            GlStateManager.enableLighting();
        }
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    // ---- shapes ------------------------------------------------------------------------------------------------

    /** a box in face space: u0..u1 × v0..v1, from depth d0 to d1 in front of the face */
    private static void box(double u0, double v0, double u1, double v1, double d0, double d1, int rgb) {
        Prims.box(16 - u1, v0, face - d1, 16 - u0, v1, face - d0, rgb);
    }

    private static void boxC(double u, double v, double w, double h, double d0, double d1, int rgb) {
        box(u - w / 2, v - h / 2, u + w / 2, v + h / 2, d0, d1, rgb);
    }

    private static void disc(double u, double v, double d, double r, int rgb) { Prims.disc(16 - u, v, face - d, r, rgb, 1f); }

    private static void drawShape(TileGrid te, Panel.C c, long now, float pt, boolean blinkOn, boolean lampTest) {
        int val = te.pv(c.id);
        switch (c.t) {
            case PUSH: {
                boolean in = te.pressedRecently(c.id, now) || (c.id.equals("lock") && val == 1);
                boxC(c.u, c.v, c.w + 0.5, c.h + 0.5, 0, 0.25, 0x2A2A2A);                       // bezel
                boxC(c.u, c.v, c.w, c.h, 0.25, in ? 0.4 : 0.8, c.colour);                       // cap
                boxC(c.u, c.v, c.w * 0.55, c.h * 0.55, in ? 0.4 : 0.8, in ? 0.45 : 0.85, Prims.shade(c.colour, 1.25f));
                if (c.id.equals("lock") && val == 1) boxC(c.u, c.v + c.h * 0.7, c.w * 0.6, 0.35, 0.3, 1.0, 0xC8C8C8);   // shackle through the hasp
                break;
            }
            case MUSHROOM: {
                boolean in = val == 1;
                boxC(c.u, c.v, c.w + 0.8, c.h + 0.8, 0, 0.15, Panel.YELLOW);                    // yellow legend plate
                boxC(c.u, c.v, c.w * 0.45, c.h * 0.45, 0.15, in ? 0.5 : 1.0, 0x2A2A2A);          // stem
                boxC(c.u, c.v, c.w, c.h, in ? 0.5 : 1.0, in ? 1.1 : 1.6, c.colour);              // red head
                boxC(c.u, c.v, c.w * 0.7, c.h * 0.7, in ? 1.1 : 1.6, in ? 1.2 : 1.7, Prims.shade(c.colour, 1.2f));
                break;
            }
            case LAMP: {
                boolean lit = lampTest || val == 1 && (!c.blink || blinkOn);
                boxC(c.u, c.v, c.w + 0.4, c.h + 0.4, 0, 0.2, 0x303030);
                if (lit) {
                    Prims.emissive(true);
                    boxC(c.u, c.v, c.w, c.h, 0.2, 0.55, Prims.shade(c.colour, 1.15f));
                    Prims.glow(16 - c.u, c.v, face - 0.6, c.w * 1.4, c.colour);
                    GlStateManager.enableLighting();
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);
                }
                else boxC(c.u, c.v, c.w, c.h, 0.2, 0.55, Prims.shade(c.colour, 0.35f));
                break;
            }
            case SELECTOR: case KEY: {
                int n = Math.max(2, c.positions.length);
                double spread = n == 2 ? 90 : n == 3 ? 120 : 150;
                double ang = -spread / 2 + spread * Math.min(n - 1, val) / (n - 1);          // degrees clockwise from up
                if (c.t == Panel.T.KEY) {
                    boxC(c.u, c.v, c.w, c.h, 0, 0.35, 0xB08A30);                               // brass barrel
                    turned(c.u, c.v, ang, 0.12, -0.2, c.h * 0.5, 0.35, 0.9, 0xD0D0D0);        // key blade
                    turned(c.u, c.v, ang, 0.45, c.h * 0.35, c.h * 0.75, 0.9, 1.1, 0xE8E8E8);  // key bow
                } else {
                    boxC(c.u, c.v, c.w + 0.3, c.h + 0.3, 0, 0.2, 0x3A3A3A);                   // escutcheon
                    turned(c.u, c.v, ang, 0.32, -c.h * 0.45, c.h * 0.45, 0.2, 0.9, c.colour); // knob bar across the centre
                    turned(c.u, c.v, ang, 0.08, 0, c.h * 0.42, 0.9, 0.95, 0xF0F0F0);          // white pointer line
                }
                break;
            }
            case GAUGE: {
                double r = c.w / 2;
                disc(c.u, c.v, 0.05, r + 0.25, 0x202020);
                disc(c.u, c.v, 0.12, r, c.colour);
                double f = Math.max(0, Math.min(1, val / (double) Math.max(1, c.max)));
                double ang = -120 + 240 * f;
                turned(c.u, c.v, ang, 0, 0.12, r * 0.85, 0.16, 0.22, val > c.max * 0.9 ? 0xD02020 : 0x111111);
                boxC(c.u, c.v, 0.3, 0.3, 0.16, 0.26, 0x111111);
                break;
            }
            case SYNCHRO: {
                double r = c.w / 2;
                disc(c.u, c.v, 0.05, r + 0.3, 0x202020);
                disc(c.u, c.v, 0.12, r, c.colour);
                boolean inPhase = Math.abs(val) < 20;
                turned(c.u, c.v, 0, 0.14, r * 0.62, r * 0.98, 0.12, 0.2, inPhase ? 0x20C040 : 0xC02020);   // 12 o'clock mark
                for (int i = 1; i < 12; i++) turned(c.u, c.v, i * 30, 0.05, r * 0.8, r * 0.98, 0.12, 0.17, 0x333333);
                float[] a = te.anim.computeIfAbsent(c.id, k -> new float[]{val, now + pt});
                double tNow = now + pt, dt = Math.max(0, Math.min(5, tNow - a[1]));
                a[1] = (float) tNow;
                double diff = ((val - a[0]) % 360 + 540) % 360 - 180, step = 14 * dt;
                a[0] += (float) Math.max(-step, Math.min(step, diff));
                turned(c.u, c.v, a[0], 0.1, -r * 0.25, r * 0.88, 0.16, 0.22, 0x111111);
                boxC(c.u, c.v, 0.3, 0.3, 0.16, 0.26, 0x111111);
                break;
            }
            case DIGITS:
                boxC(c.u, c.v, c.w + 0.3, c.h + 0.3, 0, 0.2, 0x1A1A1A);
                boxC(c.u, c.v, c.w, c.h, 0.2, 0.25, 0x050805);
                break;
            case FLAG: {
                boolean on = val == 1;
                boxC(c.u, c.v, c.w + 0.3, c.h + 0.3, 0, 0.15, 0x1A1A1A);
                boxC(c.u, c.v, c.w, c.h, 0.15, 0.2, on ? 0xC81E1E : 0x1E8C3C);
                break;
            }
            case LEVER: {
                // positions 0 = ON (handle up), 1 = OFF (down), 2 = tripped (free, half way)
                double target = val == 0 ? 38 : val == 2 ? 2 : -38;
                float[] a = te.anim.computeIfAbsent(c.id, k -> new float[]{(float) target, now + pt});
                double tNow = now + pt, dt = Math.max(0, Math.min(5, tNow - a[1]));
                a[1] = (float) tNow;
                double step = 9 * dt;                                                      // ~76°/swing in ~8 ticks
                a[0] += (float) Math.max(-step, Math.min(step, target - a[0]));
                drawLever(c, a[0]);
                break;
            }
            default: break;
        }
    }

    /** where the padlock hangs: on the operating handle if there is one, else beside the first switch / button */
    private static Panel.C lockPoint(Panel p) {
        for (Panel.C c : p.cs) if (c.t == Panel.T.LEVER) return c;
        for (Panel.C c : p.cs) if (c.clickable()) return c;
        return null;
    }

    /** a red lockout padlock with its steel shackle, and the yellow DANGER tag hanging under it */
    private static void drawPadlock(double u, double v) {
        boxC(u, v, 0.9, 0.75, 0.3, 0.95, 0xC81E1E);                 // body
        boxC(u - 0.3, v + 0.6, 0.12, 0.5, 0.55, 0.7, 0xC8C8C8);      // shackle legs
        boxC(u + 0.3, v + 0.6, 0.12, 0.5, 0.55, 0.7, 0xC8C8C8);
        boxC(u, v + 0.85, 0.72, 0.12, 0.55, 0.7, 0xC8C8C8);         // shackle top
        boxC(u, v - 0.5, 0.05, 0.35, 0.3, 0.35, 0x222222);           // tag string
        boxC(u, v - 1.75, 1.5, 1.7, 0.3, 0.34, 0xF0D020);           // tag
        boxC(u, v - 1.3, 1.3, 0.45, 0.34, 0.35, 0xFFFFFF);
    }

    /** a bar from a centre, turned `ang` degrees clockwise from up (as the viewer sees it) */
    private static void turned(double u, double v, double ang, double halfW, double fromR, double toR, double d0, double d1, int rgb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - u, v, 0);
        GlStateManager.rotate((float) ang, 0, 0, 1);              // +z turn = clockwise for a viewer standing in front (-z)
        double hw = Math.max(halfW, 0.08);
        Prims.box(-hw, fromR, face - d1, hw, toR, face - d0, rgb);
        GlStateManager.popMatrix();
    }

    /** the operating handle: a boss on the panel, an arm sticking out toward you, tilted up (ON) or down (OFF), a grip */
    private static void drawLever(Panel.C c, double tilt) {
        boxC(c.u, c.v, c.w * 2.2, c.w * 2.2, 0, 0.5, 0x2A2A2A);                               // pivot boss
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - c.u, c.v, face - 0.5);
        GlStateManager.rotate((float) tilt, 1, 0, 0);                                          // +x tilt lifts the tip (toward -z)
        double len = c.h * 1.4, hw = c.w / 2;
        Prims.box(-hw, -hw, -len, hw, hw, 0, 0x3C3C3C);                                        // arm
        Prims.box(-hw * 1.5, -hw * 1.5, -len - c.w * 1.3, hw * 1.5, hw * 1.5, -len + 0.1, c.colour);   // grip
        GlStateManager.popMatrix();
    }

    // ---- text ----------------------------------------------------------------------------------------------------

    private static void drawText(FontRenderer fr, TileGrid te, Panel.C c) {
        switch (c.t) {
            case LABEL: text(fr, c.label, c.u, c.v, 0.15, 0.55, 0x101010, false); break;
            case DIGITS: {
                String s;
                if ("%s".equals(c.fmt)) s = te.ptext(c.id);
                else if (c.fmt.contains("f")) s = String.format(Locale.ROOT, c.fmt, te.pv(c.id) * c.scale);
                else s = String.format(Locale.ROOT, c.fmt, (int) Math.round(te.pv(c.id) * c.scale));
                text(fr, s, c.u, c.v, 0.3, Math.min(1.0, c.w / Math.max(1, fr.getStringWidth(s)) * 8 * 0.95) , c.colour, true);
                break;
            }
            case FLAG: text(fr, te.pv(c.id) == 1 ? "ON" : "OFF", c.u, c.v, 0.25, 0.9, 0xFFFFFF, true); break;
            case PUSH: case LAMP: case GAUGE: case MUSHROOM: case SYNCHRO:
                if (!c.label.isEmpty()) text(fr, c.label, c.u, c.v - c.h / 2 - (c.t == Panel.T.GAUGE ? 0.55 : 0.75), 0.2, 0.5, 0x151515, false);
                break;
            case SELECTOR: case KEY: {
                text(fr, c.label, c.u, c.v + c.h / 2 + 0.75, 0.2, 0.5, 0x151515, false);
                int n = c.positions.length;
                double spread = n == 2 ? 90 : n == 3 ? 120 : 150, r = c.w / 2 + 0.9;
                for (int i = 0; i < n; i++) {
                    double ang = Math.toRadians(-spread / 2 + spread * i / Math.max(1, n - 1));
                    text(fr, c.positions[i], c.u + Math.sin(ang) * r, c.v + Math.cos(ang) * r - 0.2, 0.2, 0.42,
                            i == te.pv(c.id) ? 0x003080 : 0x202020, false);
                }
                break;
            }
            case LEVER: text(fr, c.label, c.u, c.v - c.h - 0.4, 0.2, 0.5, 0x151515, false); break;
            default: break;
        }
    }

    /** text centred at (u, v) on the face, `heightPx` tall, standing `d` in front; glow = a lit display */
    private static void text(FontRenderer fr, String s, double u, double v, double d, double heightPx, int rgb, boolean glow) {
        if (s == null || s.isEmpty()) return;
        float k = (float) (heightPx / 8.0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - u, v + heightPx / 2, face - d - 0.01);
        GlStateManager.rotate(180f, 0, 0, 1);                                  // font draws +x right, +y down
        GlStateManager.scale(k, k, k);
        if (glow) OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        fr.drawString(s, -fr.getStringWidth(s) / 2, 0, rgb);
        GlStateManager.popMatrix();
    }
}
