package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.BlockGrid;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.TileGrid;
import com.dogpound.railmap.grid.PanelHost;
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
        IBlockState st0 = te.getWorld().getBlockState(te.getPos());
        if (!(st0.getBlock() instanceof BlockGrid) || !st0.getPropertyKeys().contains(BlockGrid.FACING)) return;
        render(te, p, st0.getValue(BlockGrid.FACING), te.getWorld(), te.getPos(), x, y, z, pt);
    }

    /** API: draw `p` live on the front (`facing`) of the block at pos, reading its state from `te` */
    public static void render(PanelHost te, Panel p, EnumFacing facing, net.minecraft.world.World world, net.minecraft.util.math.BlockPos bpos,
                              double x, double y, double z, float pt) {
        if (p == null || world == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        double dist2 = x * x + y * y + z * z;
        if (dist2 > 40 * 40) return;
        int turns = facing.getHorizontalIndex() == 2 ? 0 : facing.getHorizontalIndex() == 3 ? 1 : facing.getHorizontalIndex() == 0 ? 2 : 3;
        long now = world.getTotalWorldTime();
        boolean blinkOn = ((now / 8) & 1) == 0;
        boolean lampTest = te.pv("lamptest") > 0;
        face = p.face;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);                         // same turn the blockstate gives the model
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        int light = world.getCombinedLight(bpos.offset(facing), 0);
        lightU = light & 0xFFFF; lightV = light >> 16;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);

        // the cabinet door (requested feature): eased swing
        float[] dr = te.anim().computeIfAbsent("_door", k -> new float[]{te.pv("door") == 1 ? 1 : 0, now + pt});
        double tNowD = now + pt, dtD = Math.max(0, Math.min(5, tNowD - dr[1]));
        dr[1] = (float) tNowD;
        dr[0] += (float) Math.max(-0.12 * dtD, Math.min(0.12 * dtD, (te.pv("door") == 1 ? 1 : 0) - dr[0]));
        double doorAng = dr[0] * 105;
        boolean inside = doorAng > 2;                                           // the controls only show with the door open

        Prims.begin();
        // the cabinet / door the controls are mounted on
        double back = p.face < 0 ? 0 : p.face + 0.2;
        box(p.u0, p.v0, p.u1, p.v1, -(back - p.face), 0, p.plate);
        if (inside) for (Panel.C c : p.cs) drawShape(te, c, now, pt, blinkOn, lampTest);
        drawDoor(te, p, doorAng, blinkOn);
        Panel.C lockAt = te.pv("loto") == 1 ? lockPoint(p) : null;
        if (lockAt != null) drawPadlock(lockAt.u + 1.3, lockAt.v - 0.4);
        Prims.end();

        if (dist2 < 12 * 12) {                                                 // engraving / displays: only up close
            FontRenderer fr = mc.fontRenderer;
            GlStateManager.disableLighting();
            if (inside) for (Panel.C c : p.cs) drawText(fr, te, c);
            if (doorAng < 60) {                                                // the nameplate on the door
                GlStateManager.pushMatrix();
                doorFrame(p, doorAng);
                double plateW = Math.min(6.5, p.u1 - p.u0 - 1.5) - 0.6;
                text(fr, p.title, (p.u0 + p.u1) / 2 + 0.3, p.v1 - 1.25, 2.35, Math.min(0.75, plateW * 8.0 / Math.max(1, fr.getStringWidth(p.title))), 0xE8E8E8, false);
                GlStateManager.popMatrix();
            }
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

    /**
     * API: draw a panel (plate, every control, and their engraving / displays when {@code withText}) in the CURRENT GL
     * frame, which must already be in model pixels with the panel's face plane at z = p.face (x = 16 - u, y = v,
     * controls standing toward -z). For desks and consoles that tilt their own surface (the Control Center console).
     * No cabinet door. {@code lightAt} is where the lighting is sampled.
     */
    public static void drawInFrame(PanelHost te, Panel p, net.minecraft.world.World world, net.minecraft.util.math.BlockPos lightAt,
                                   float pt, boolean withText, boolean plate) {
        if (p == null || world == null) return;
        long now = world.getTotalWorldTime();
        boolean blinkOn = ((now / 8) & 1) == 0;
        boolean lampTest = te.pv("lamptest") > 0;
        face = p.face;
        int light = world.getCombinedLight(lightAt, 0);
        lightU = light & 0xFFFF; lightV = light >> 16;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);
        Prims.begin();
        if (plate) {
            double back = p.face < 0 ? 0 : p.face + 0.2;
            box(p.u0, p.v0, p.u1, p.v1, -(back - p.face), 0, p.plate);
        }
        for (Panel.C c : p.cs) drawShape(te, c, now, pt, blinkOn, lampTest);
        Prims.end();
        if (withText) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            GlStateManager.disableLighting();
            for (Panel.C c : p.cs) drawText(fr, te, c);
            GlStateManager.enableLighting();
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);
    }

    /** API: centred text on the current panel frame (see drawInFrame), {@code heightPx} tall, glowing if lit */
    public static void label(String s, double u, double v, double heightPx, int rgb, boolean glow) {
        text(Minecraft.getMinecraft().fontRenderer, s, u, v, 0.2, heightPx, rgb, glow);
    }

    // ---- the cabinet door ----------------------------------------------------------------------------------------
    /** turn the frame so the door, hinged on the viewer's left edge, swings out toward the viewer by `ang` degrees */
    private static void doorFrame(Panel p, double ang) {
        double hx = 16 - (p.u0 - 0.3), hz = face - 1.6;
        GlStateManager.translate(hx, 0, hz);
        GlStateManager.rotate((float) -ang, 0, 1, 0);
        GlStateManager.translate(-hx, 0, -hz);
    }

    private static void drawDoor(PanelHost te, Panel p, double ang, boolean blinkOn) {
        GlStateManager.pushMatrix();
        doorFrame(p, ang);
        double u0 = p.u0 - 0.3, u1 = p.u1 + 0.3, v0 = p.v0 - 0.3, v1 = p.v1 + 0.3, mid = (u0 + u1) / 2;
        int col = 0xB9BDBF;                                                     // RAL 7035 cabinet grey
        box(u0, v0, u1, v1, 1.6, 2.1, col);                                     // the door leaf
        box(u0 + 0.4, v0 + 0.4, u1 - 0.4, v1 - 0.4, 2.1, 2.16, Prims.shade(col, 0.93f));   // pressed panel
        boxC(u1 - 0.75, (v0 + v1) / 2, 0.35, Math.min(2.4, (v1 - v0) * 0.3), 2.1, 2.75, 0x2A2A2A);   // handle
        for (double hv : new double[]{v0 + 1.2, v1 - 1.2}) boxC(u0 + 0.15, hv, 0.35, 1.0, 1.5, 2.2, 0x5A5E62);   // hinges
        boxC(mid, v1 - 1.25, Math.min(6.5, u1 - u0 - 1.5), 1.0, 2.16, 2.3, 0x202326);           // nameplate
        boxC(mid, v0 + 1.4, 1.3, 1.1, 2.16, 2.22, 0xF0D020);                                   // hazard sticker
        boolean run = te.pv("lrun") == 1 || te.pv("anim") == 1 || te.pv("on") == 1 || te.pv("grid") == 1;
        boolean fault = te.pv("lfault") == 1 || te.pv("fault") == 1 || te.pv("alarm") == 1;
        if (run) { Prims.emissive(true); }
        boxC(mid - 1.4, v0 + 3.0, 0.9, 0.9, 2.16, 2.45, run ? 0x30FF50 : 0x1E4A26);             // RUN
        if (run) { GlStateManager.enableLighting(); OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV); }
        boolean f = fault && blinkOn;
        if (f) Prims.emissive(true);
        boxC(mid + 1.4, v0 + 3.0, 0.9, 0.9, 2.16, 2.45, f ? 0xFF3030 : 0x4A1E1E);              // FAULT
        if (f) { GlStateManager.enableLighting(); OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV); }
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

    private static void drawShape(PanelHost te, Panel.C c, long now, float pt, boolean blinkOn, boolean lampTest) {
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
                float[] a = te.anim().computeIfAbsent(c.id, k -> new float[]{val, now + pt});
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
                float[] a = te.anim().computeIfAbsent(c.id, k -> new float[]{(float) target, now + pt});
                double tNow = now + pt, dt = Math.max(0, Math.min(5, tNow - a[1]));
                a[1] = (float) tNow;
                double step = 9 * dt;                                                      // ~76°/swing in ~8 ticks
                a[0] += (float) Math.max(-step, Math.min(step, target - a[0]));
                drawLever(c, a[0]);
                break;
            }
            case TILE: {                                                                // annunciator window, ISA flash sequence
                boolean lit = lampTest || val == 2 || val == 1 && ((now / 4) & 1) == 0 || val == 3 && ((now / 14) & 1) == 0;
                boxC(c.u, c.v, c.w + 0.35, c.h + 0.35, 0, 0.25, 0x1A1A1A);                       // bezel
                if (lit) {
                    Prims.emissive(true);
                    boxC(c.u, c.v, c.w, c.h, 0.25, 0.42, Prims.shade(c.colour, 1.1f));
                    Prims.glow(16 - c.u, c.v, face - 0.5, Math.max(c.w, c.h) * 0.9, c.colour);
                    GlStateManager.enableLighting();
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);
                } else boxC(c.u, c.v, c.w, c.h, 0.25, 0.42, Prims.shade(c.colour, 0.28f));
                break;
            }
            case PULL: {                                                                // lever frame: 1 = pulled over toward you
                double target = val == 1 ? -28 : 10;
                float[] a = te.anim().computeIfAbsent(c.id, k -> new float[]{(float) target, now + pt});
                double tNow = now + pt, dt = Math.max(0, Math.min(5, tNow - a[1]));
                a[1] = (float) tNow;
                a[0] += (float) Math.max(-6 * dt, Math.min(6 * dt, target - a[0]));
                drawPull(c, a[0], val == 2);
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

    /** a lever-frame lever standing on its pivot, leaning back (normal) or pulled over toward you; locked = catch shown red */
    private static void drawPull(Panel.C c, double ang, boolean locked) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - c.u, c.v, face - c.w / 2);
        GlStateManager.rotate((float) ang, 1, 0, 0);                                           // +x leans the top away (back)
        double hw = c.w / 2, h = c.h;
        Prims.box(-hw, 0, -hw, hw, h * 0.82, hw, c.colour);                                    // painted lever
        Prims.box(-hw * 0.8, h * 0.82, -hw * 0.8, hw * 0.8, h, hw * 0.8, 0xD8DCE0);            // polished handle
        Prims.box(-hw * 1.1, h * 0.62, -hw * 1.6, hw * 1.1, h * 0.7, -hw, locked ? 0xC02020 : 0xB0B4B8);   // catch handle
        Prims.box(-hw * 1.05, h * 0.2, -hw - 0.08, hw * 1.05, h * 0.2 + c.w * 1.3, -hw, 0xF0F0E8);          // number plate
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

    private static void drawText(FontRenderer fr, PanelHost te, Panel.C c) {
        switch (c.t) {
            case LABEL: text(fr, c.label, c.u, c.v, 0.15, 0.55, 0x101010, false); break;
            case DIGITS: {
                String s;
                if ("%s".equals(c.fmt)) s = te.ptext(c.id);
                else try {
                    if (c.fmt.contains("f")) s = String.format(Locale.ROOT, c.fmt, te.pv(c.id) * c.scale);
                    else s = String.format(Locale.ROOT, c.fmt, (int) Math.round(te.pv(c.id) * c.scale));
                } catch (java.util.IllegalFormatException bad) {          // a typo'd model format must never crash the game
                    s = String.valueOf((int) Math.round(te.pv(c.id) * c.scale));
                }
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
            case PULL: text(fr, c.label, c.u, c.v - 0.55, 0.2, 0.55, 0x151515, false); break;
            case TILE: {                                                                // the engraving on the window
                String[] lines = c.label.split("\\|");
                double lh = Math.min(0.55, c.h / (lines.length + 0.6));
                for (int i = 0; i < lines.length; i++)
                    text(fr, lines[i], c.u, c.v + (lines.length - 1) * lh * 0.6 - i * lh * 1.2 - lh / 2 + lh / 2, 0.44, lh, 0x101010, false);
                break;
            }
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
