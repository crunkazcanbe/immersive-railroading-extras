package com.dogpound.railmap.modelrail.client;

import com.dogpound.railmap.modelrail.MiniLayout;
import com.dogpound.railmap.modelrail.TileModelRailway;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the model railway on its table: ballast, sleepers and rails along every road, points with their lever,
 * signals showing their real aspect, platforms with canopies and lamps, tunnel hills with dark portals, trees and
 * houses (windows lit at NIGHT); then the mini trains, each vehicle modelled by type, run on from the last sync so
 * they glide. MIRROR mode draws the real railway's track and live trains in miniature instead. The scenery is
 * baked into display lists, rebuilt only when the layout changes.
 */
public class ModelRailRenderer extends TileEntitySpecialRenderer<TileModelRailway> {
    private static final class Lists { int solid = -1, glow = -1, hash; }
    private static final Map<TileModelRailway, Lists> LISTS = new WeakHashMap<>();

    @Override
    public boolean isGlobalRenderer(TileModelRailway te) { return true; }

    @Override
    public void render(TileModelRailway te, double x, double y, double z, float pt, int destroyStage, float alpha) {
        if (!te.tableOk) return;
        EnumFacing f = te.controlFacing(), back = te.back(), right = te.right();
        BlockPos b0 = te.tableBlock(0, te.td - 1).subtract(te.getPos());
        double cx = b0.getX() + 0.5 + back.getXOffset() * 0.5 - right.getXOffset() * 0.5;
        double cz = b0.getZ() + 0.5 + back.getZOffset() * 0.5 - right.getZOffset() * 0.5;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + cx, y + 13 / 16.0 + 0.002, z + cz);
        GlStateManager.rotate(-f.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.scale(0.25, 0.25, 0.25);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        if (++frames % 400 == 0) sweep();
        Lists l = LISTS.computeIfAbsent(te, k -> new Lists());
        int hash = hash(te);
        if (l.solid < 0 || l.hash != hash) {
            if (l.solid < 0) { l.solid = GLAllocation.generateDisplayLists(2); l.glow = l.solid + 1; }
            l.hash = hash;
            GlStateManager.glNewList(l.solid, GL11.GL_COMPILE);
            Q.begin();
            if (te.mirror()) mirrorTrack(te); else scenery(te.lay, te.night(), false);
            Q.end();
            GlStateManager.glEndList();
            GlStateManager.glNewList(l.glow, GL11.GL_COMPILE);
            Q.begin();
            if (!te.mirror()) scenery(te.lay, te.night(), true);
            Q.end();
            GlStateManager.glEndList();
        }
        GlStateManager.callList(l.solid);
        float bx = OpenGlHelper.lastBrightnessX, by = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.callList(l.glow);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bx, by);

        Q.begin();
        if (te.mirror()) mirrorTrains(te);
        else {
            MiniLayout m = te.predicted(pt);
            for (MiniLayout.Train t : m.trains) for (int i = 0; i < Math.max(1, t.cars.length); i++) vehicle(m, t, i);
        }
        Q.end();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static int frames;

    /** free the display lists of desks that are gone */
    private static void sweep() {
        LISTS.entrySet().removeIf(e -> {
            if (!e.getKey().isInvalid()) return false;
            if (e.getValue().solid >= 0) GLAllocation.deleteDisplayLists(e.getValue().solid, 2);
            return true;
        });
    }

    private static int hash(TileModelRailway te) {
        int h = Arrays.hashCode(te.lay.cell);
        h = h * 31 + Arrays.hashCode(te.lay.rev);
        h = h * 31 + Arrays.hashCode(te.lay.green);
        h = h * 31 + te.lay.w * 17 + te.lay.d;
        h = h * 31 + (te.night() ? 1 : 0) + (te.mirror() ? 2 : 0);
        if (te.mirror()) h = h * 31 + Arrays.hashCode(te.mirChains);
        return h;
    }

    // ---- scenery ------------------------------------------------------------------------------------------------------------
    private static final int BALLAST = 0x7a7064, SLEEPER = 0x4a3424, RAIL = 0x9aa0a8, GRASS = 0x4c8a40;

    /** glow = only the lit parts (lamps, windows, signal lenses) */
    private static void scenery(MiniLayout m, boolean night, boolean glow) {
        for (int gz = 0; gz < m.d; gz++)
            for (int gx = 0; gx < m.w; gx++) {
                int c = MiniLayout.idx(gx, gz), t = m.type(c), r = m.rot(c);
                if (t == MiniLayout.EMPTY) continue;
                switch (t) {
                    case MiniLayout.STRAIGHT: if (!glow) road(m, c, r, (r + 2) & 3, 1); break;
                    case MiniLayout.CURVE: if (!glow) road(m, c, r, (r + 1) & 3, 1); break;
                    case MiniLayout.CROSSING: if (!glow) { road(m, c, 0, 2, 1); road(m, c, 1, 3, 1); } break;
                    case MiniLayout.POINTS: {
                        int div = (r + (m.mirror(c) ? 3 : 1)) & 3;
                        if (!glow) {
                            road(m, c, r, (r + 2) & 3, 1);
                            road(m, c, r, div, 1);
                            double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.25));
                            double nx = -Math.sin(p[2]), nz = Math.cos(p[2]);
                            double side = m.mirror(c) ? 1 : -1;
                            Q.obox(p[0] + nx * 0.42 * side, p[1] + nz * 0.42 * side, p[2], 0.12, 0.06, 0, 0.08, 0x3a3e44);         // point motor
                            Q.obox(p[0] + nx * 0.42 * side, p[1] + nz * 0.42 * side, p[2] + (m.rev[c] ? 0.6 : -0.6), 0.02, 0.1, 0.08, 0.11, 0xd8b020);   // its throw bar
                        }
                        break;
                    }
                    case MiniLayout.STATION: {
                        if (!glow) road(m, c, r, (r + 2) & 3, 1);
                        station(c, r, night, glow);
                        break;
                    }
                    case MiniLayout.TUNNEL: if (!glow) { road(m, c, r, (r + 2) & 3, 1); tunnel(c, r); } break;
                    case MiniLayout.SIGNAL: {
                        if (!glow) road(m, c, r, (r + 2) & 3, 1);
                        signal(m, c, r, glow);
                        break;
                    }
                    case MiniLayout.BUFFER: {
                        if (!glow) {
                            road(m, c, (r + 2) & 3, r, 0.5);
                            double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.5));
                            Q.obox(p[0], p[1], p[2], 0.05, 0.22, 0.05, 0.2, 0x5a3a20);
                            Q.obox(p[0] - Math.cos(p[2]) * 0.03, p[1] - Math.sin(p[2]) * 0.03, p[2], 0.02, 0.24, 0.13, 0.19, 0xc02020);
                        } else {
                            double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.5));
                            Q.obox(p[0], p[1], p[2], 0.03, 0.03, 0.2, 0.24, 0xff3020);
                        }
                        break;
                    }
                    case MiniLayout.TREE: if (!glow) trees(c); break;
                    case MiniLayout.HOUSE: house(c, r, night, glow); break;
                    default: break;
                }
            }
    }

    /** ballast, sleepers and two rails along a road through cell c, for the first `upTo` of it */
    private static void road(MiniLayout m, int c, int from, int to, double upTo) {
        int n = MiniLayout.curved(from, to) ? 8 : 2;
        double len = MiniLayout.length(from, to);
        for (int i = 0; i < n; i++) {
            double t0 = upTo * i / n, t1 = upTo * (i + 1) / n;
            double[] a = MiniLayout.pose(new MiniLayout.Loc(c, from, to, t0)), b = MiniLayout.pose(new MiniLayout.Loc(c, from, to, t1));
            double mx = (a[0] + b[0]) / 2, mz = (a[1] + b[1]) / 2, h = Math.atan2(b[1] - a[1], b[0] - a[0]);
            double sl = Math.hypot(b[0] - a[0], b[1] - a[1]) / 2 + 0.006;
            double nx = -Math.sin(h), nz = Math.cos(h);
            Q.obox(mx, mz, h, sl, 0.3, 0, 0.04, BALLAST);
            for (double s : new double[]{-0.13, 0.13}) Q.obox(mx + nx * s, mz + nz * s, h, sl, 0.018, 0.07, 0.11, RAIL);
        }
        int sleepers = (int) Math.round(len * upTo * 8);
        for (int i = 0; i < sleepers; i++) {
            double[] p = MiniLayout.pose(new MiniLayout.Loc(c, from, to, (i + 0.5) / sleepers * upTo));
            Q.obox(p[0], p[1], p[2], 0.03, 0.22, 0.04, 0.07, SLEEPER);
        }
    }

    private static void station(int c, int r, boolean night, boolean glow) {
        double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.5));
        double h = p[2], nx = -Math.sin(h), nz = Math.cos(h);
        double px = p[0] + nx * 0.36, pz = p[1] + nz * 0.36;
        if (!glow) {
            Q.obox(px, pz, h, 0.5, 0.13, 0, 0.16, 0xb8b4a8);                          // platform
            Q.obox(px - nx * 0.11, pz - nz * 0.11, h, 0.5, 0.02, 0.16, 0.165, 0xf0d020);   // yellow line
            for (double s : new double[]{-0.3, 0.3}) {
                double ox = Math.cos(h) * s, oz = Math.sin(h) * s;
                Q.obox(px + ox + nx * 0.05, pz + oz + nz * 0.05, h, 0.015, 0.015, 0.16, 0.48, 0x2b2f36);   // canopy posts
            }
            Q.obox(px + nx * 0.02, pz + nz * 0.02, h, 0.45, 0.13, 0.48, 0.51, 0x7a1a2a);   // canopy roof
            Q.obox(px + Math.cos(h) * 0.12 + nx * 0.06, pz + Math.sin(h) * 0.12 + nz * 0.06, h, 0.06, 0.02, 0.16, 0.21, 0x5a3a20);   // bench
            Q.obox(px - Math.cos(h) * 0.15 + nx * 0.06, pz - Math.sin(h) * 0.15 + nz * 0.06, h, 0.04, 0.025, 0.16, 0.26, 0x2050d0);   // nameboard
        } else {
            int lamp = night ? 0xfff2b0 : 0x8a8478;
            Q.obox(px + nx * 0.02, pz + nz * 0.02, h, 0.4, 0.03, 0.465, 0.48, lamp);   // canopy light strip
        }
    }

    private static void tunnel(int c, int r) {
        double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.5));
        double h = p[2];
        Q.obox(p[0], p[1], h, 0.42, 0.5, 0, 0.42, GRASS);
        Q.obox(p[0], p[1], h, 0.34, 0.42, 0.42, 0.62, 0x427a38);
        Q.obox(p[0], p[1], h, 0.2, 0.3, 0.62, 0.72, 0x3a6c32);
        for (double s : new double[]{-0.43, 0.43}) {
            double ox = Math.cos(h) * s, oz = Math.sin(h) * s;
            Q.obox(p[0] + ox, p[1] + oz, h, 0.02, 0.24, 0, 0.36, 0x8a8478);              // stone portal
            Q.obox(p[0] + ox * 1.02, p[1] + oz * 1.02, h, 0.015, 0.17, 0, 0.28, 0x101010);   // the dark mouth
        }
    }

    private static void signal(MiniLayout m, int c, int r, boolean glow) {
        double[] p = MiniLayout.pose(new MiniLayout.Loc(c, r, (r + 2) & 3, 0.42));
        double h = p[2], nx = -Math.sin(h), nz = Math.cos(h);
        double sx = p[0] - nx * 0.3, sz = p[1] - nz * 0.3;            // on the driver's left
        if (!glow) {
            Q.obox(sx, sz, h, 0.02, 0.02, 0, 0.55, 0xd0d0d0);
            Q.obox(sx, sz, h, 0.035, 0.05, 0.5, 0.68, 0x151515);
        } else {
            boolean g = m.green[c];
            Q.obox(sx - Math.cos(h) * 0.036, sz - Math.sin(h) * 0.036, h, 0.004, 0.03, 0.62, 0.66, g ? 0x2a2a2a : 0xff2020);
            Q.obox(sx - Math.cos(h) * 0.036, sz - Math.sin(h) * 0.036, h, 0.004, 0.03, 0.53, 0.57, g ? 0x30ff60 : 0x2a2a2a);
        }
    }

    private static void trees(int c) {
        int seed = c * 73 + 41;
        for (int k = 0; k < 2; k++) {
            double x = c % MiniLayout.N + 0.3 + ((seed >> k) & 3) * 0.13, z = c / MiniLayout.N + 0.3 + ((seed >> (k + 2)) & 3) * 0.12;
            double s = 0.8 + ((seed >> k) & 1) * 0.3;
            Q.obox(x, z, 0, 0.03, 0.03, 0, 0.2 * s, 0x5a3a20);
            Q.obox(x, z, 0.3, 0.16 * s, 0.16 * s, 0.15 * s, 0.35 * s, 0x2e6a2a);
            Q.obox(x, z, 0.9, 0.12 * s, 0.12 * s, 0.33 * s, 0.5 * s, 0x387a30);
            Q.obox(x, z, 0.2, 0.07 * s, 0.07 * s, 0.48 * s, 0.62 * s, 0x44882e);
        }
    }

    private static void house(int c, int r, boolean night, boolean glow) {
        double x = c % MiniLayout.N + 0.5, z = c / MiniLayout.N + 0.5, h = r * Math.PI / 2;
        if (!glow) {
            Q.obox(x, z, h, 0.3, 0.22, 0, 0.32, 0xe8dcc0);                   // walls
            for (int i = 0; i < 4; i++) Q.obox(x, z, h, 0.33, 0.25 - i * 0.065, 0.32 + i * 0.05, 0.37 + i * 0.05, 0xa03a2a);   // stepped roof
            Q.obox(x + Math.cos(h) * 0.18, z + Math.sin(h) * 0.18, h, 0.04, 0.04, 0.4, 0.55, 0x8a3a2a);   // chimney
            double nx = -Math.sin(h), nz = Math.cos(h);
            Q.obox(x + nx * 0.221, z + nz * 0.221, h, 0.04, 0.004, 0, 0.14, 0x5a3a20);   // door
        } else {
            int win = night ? 0xffe090 : 0x404a58;
            double nx = -Math.sin(h), nz = Math.cos(h);
            for (double s : new double[]{-0.18, 0.18})
                for (double side : new double[]{-1, 1}) {
                    double ox = Math.cos(h) * s + nx * 0.222 * side, oz = Math.sin(h) * s + nz * 0.222 * side;
                    Q.obox(x + ox, z + oz, h, 0.05, 0.004, 0.17, 0.26, win);
                }
        }
    }

    // ---- trains ------------------------------------------------------------------------------------------------------------
    private static final int[] BODY = {0x1e5a2a, 0xd8a020, 0xc02030, 0x7a1a2a, 0x7a4a2a, 0x2a2a2a, 0x5a4a3a};

    private static void vehicle(MiniLayout m, MiniLayout.Train t, int i) {
        double[] p = MiniLayout.pose(m.car(t, i));
        int type = i < t.cars.length ? Math.max(0, Math.min(6, t.cars[i])) : 0;
        double h = p[2] + (t.derailed ? (i % 2 == 0 ? 0.35 : -0.3) : 0), x = p[0], z = p[1], L = 0.24, W = 0.12;
        double fx = Math.cos(h), fz = Math.sin(h), nx = -Math.sin(h), nz = Math.cos(h);
        int body = BODY[type];
        Q.obox(x, z, h, L, W * 0.8, 0.12, 0.16, 0x1a1a1a);                                  // underframe
        for (double s : new double[]{-0.15, 0.15})
            for (double side : new double[]{-1, 1}) Q.obox(x + fx * s + nx * W * 0.85 * side, z + fz * s + nz * W * 0.85 * side, h, 0.035, 0.012, 0.1, 0.16, 0x303030);   // wheels
        for (double s : new double[]{-1, 1}) Q.obox(x + fx * (L + 0.012) * s, z + fz * (L + 0.012) * s, h, 0.012, W * 0.6, 0.13, 0.15, 0x808080);   // buffers
        switch (type) {
            case 0 -> {                                                                         // steam: boiler, cab, chimney, dome
                Q.obox(x + fx * 0.05, z + fz * 0.05, h, 0.17, W * 0.75, 0.16, 0.3, body);
                Q.obox(x + fx * 0.08, z + fz * 0.08, h, 0.12, W * 0.8, 0.29, 0.31, 0xc8a040);
                Q.obox(x - fx * 0.16, z - fz * 0.16, h, 0.08, W, 0.16, 0.38, body);
                Q.obox(x - fx * 0.16, z - fz * 0.16, h, 0.09, W * 1.08, 0.38, 0.4, 0x151515);
                Q.obox(x + fx * 0.18, z + fz * 0.18, h, 0.025, 0.025, 0.3, 0.4, 0x151515);
                Q.obox(x + fx * 0.04, z + fz * 0.04, h, 0.025, 0.03, 0.3, 0.34, 0xc8a040);
            }
            case 1, 2 -> {                                                                      // diesel / electric
                Q.obox(x, z, h, L - 0.01, W, 0.16, 0.34, body);
                Q.obox(x, z, h, L - 0.02, W * 0.9, 0.34, 0.36, 0x9a9a9a);
                for (double s : new double[]{-1, 1}) Q.obox(x + fx * (L - 0.005) * s, z + fz * (L - 0.005) * s, h, 0.006, W * 0.8, 0.26, 0.32, 0x203040);
                Q.obox(x, z, h, L - 0.01, W * 1.01, 0.2, 0.22, type == 1 ? 0x202020 : 0xf0f0f0);
                if (type == 2) {
                    Q.obox(x + fx * 0.1, z + fz * 0.1, h + 0.5, 0.005, 0.05, 0.36, 0.44, 0x808080);
                    Q.obox(x + fx * 0.1, z + fz * 0.1, h, 0.012, 0.07, 0.44, 0.45, 0xb87333);
                }
            }
            case 3 -> {                                                                         // coach: maroon + cream, windows
                Q.obox(x, z, h, L, W, 0.16, 0.24, body);
                Q.obox(x, z, h, L, W, 0.24, 0.33, 0xe8dcc0);
                for (int k = -2; k <= 2; k++) Q.obox(x + fx * k * 0.085, z + fz * k * 0.085, h, 0.03, W * 1.02, 0.26, 0.31, 0x304050);
                Q.obox(x, z, h, L, W * 0.92, 0.33, 0.36, 0x3a3a3a);
            }
            case 4 -> { Q.obox(x, z, h, L - 0.04, W, 0.16, 0.33, body); Q.obox(x, z, h, L - 0.03, W * 1.05, 0.33, 0.35, 0x5a5a5a); }
            case 5 -> { Q.obox(x, z, h, L - 0.04, W * 0.85, 0.17, 0.31, body); Q.obox(x, z, h, 0.03, 0.04, 0.31, 0.34, 0x909090); Q.obox(x, z, h, L - 0.04, W * 0.87, 0.28, 0.29, 0xf0c018); }
            default -> { Q.obox(x + fx * 0.03, z + fz * 0.03, h, L - 0.07, W, 0.16, 0.32, body); Q.obox(x - fx * (L - 0.02), z - fz * (L - 0.02), h, 0.015, 0.02, 0.24, 0.27, 0xff2020); }
        }
        if (i == 0) Q.obox(x + fx * (L + 0.003), z + fz * (L + 0.003), h, 0.004, 0.03, 0.2, 0.23, t.v != 0 ? 0xfff2c0 : 0x606060);   // head lamp
    }

    // ---- mirror ---------------------------------------------------------------------------------------------------------------
    private static void mirrorTrack(TileModelRailway te) {
        int[] a = te.mirChains;
        double px = Double.NaN, pz = Double.NaN;
        for (int v : a) {
            if (v == -1) { px = Double.NaN; continue; }
            double x = (v >>> 16) / 64.0, z = (v & 0xFFFF) / 64.0;
            if (!Double.isNaN(px)) {
                double h = Math.atan2(z - pz, x - px), l = Math.hypot(x - px, z - pz) / 2 + 0.01;
                Q.obox((x + px) / 2, (z + pz) / 2, h, l, 0.07, 0, 0.03, BALLAST);
                Q.obox((x + px) / 2, (z + pz) / 2, h, l, 0.025, 0.03, 0.06, RAIL);
            }
            px = x; pz = z;
        }
    }

    private static void mirrorTrains(TileModelRailway te) {
        int[] a = te.mirTrains;
        for (int i = 0; i + 1 < a.length; i += 2) {
            double x = (a[i] >>> 16) / 64.0, z = (a[i] & 0xFFFF) / 64.0;
            int col = a[i + 1] & 0xFFFFFF;
            Q.obox(x, z, 0, 0.1, 0.1, 0.06, 0.24, col);
            Q.obox(x, z, 0, 0.11, 0.11, 0.24, 0.26, 0x202020);
        }
    }

    // ---- batched oriented boxes ------------------------------------------------------------------------------------------------
    /** all boxes between begin() and end() go out in one draw */
    static final class Q {
        private static final Tessellator T = Tessellator.getInstance();
        private static BufferBuilder b;

        static void begin() { b = T.getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR); }
        static void end() { T.draw(); b = null; }

        /** a box centred (x,z), turned h radians (grid frame), half-length hl along h, half-width hw across, from y0 to y1 */
        static void obox(double x, double z, double h, double hl, double hw, double y0, double y1, int rgb) {
            double c = Math.cos(h), s = Math.sin(h);
            double[][] k = new double[4][];
            double[][] o = {{-hl, -hw}, {hl, -hw}, {hl, hw}, {-hl, hw}};
            for (int i = 0; i < 4; i++) k[i] = new double[]{x + o[i][0] * c - o[i][1] * s, z + o[i][0] * s + o[i][1] * c};
            int top = shade(rgb, 1.12f), side1 = shade(rgb, 0.86f), side2 = shade(rgb, 0.74f);
            q(k[0], y1, k[3], y1, k[2], y1, k[1], y1, top);
            q(k[0], y0, k[1], y0, k[2], y0, k[3], y0, shade(rgb, 0.6f));
            for (int i = 0; i < 4; i++) {
                double[] a = k[i], bb = k[(i + 1) & 3];
                q(a, y0, bb, y0, bb, y1, a, y1, (i & 1) == 0 ? side1 : side2);
            }
        }

        private static void q(double[] a, double ya, double[] p, double yb, double[] cc, double yc, double[] d, double yd, int rgb) {
            int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255;
            b.pos(a[0], ya, a[1]).color(r, g, bl, 255).endVertex();
            b.pos(p[0], yb, p[1]).color(r, g, bl, 255).endVertex();
            b.pos(cc[0], yc, cc[1]).color(r, g, bl, 255).endVertex();
            b.pos(d[0], yd, d[1]).color(r, g, bl, 255).endVertex();
        }

        static int shade(int rgb, float f) {
            return Math.min(255, (int) ((rgb >> 16 & 255) * f)) << 16 | Math.min(255, (int) ((rgb >> 8 & 255) * f)) << 8 | Math.min(255, (int) ((rgb & 255) * f));
        }
    }
}
