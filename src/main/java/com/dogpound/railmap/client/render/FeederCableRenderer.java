package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.GridKind;
import com.dogpound.railmap.grid.TileGrid;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.math.BlockPos;

/**
 * The Track Feeder's bonding jumper: a heavy cable sagging from the feeder's terminal down to the
 * nearest rail, ending in a bolted clamp on the rail web. Only feeders draw anything.
 */
public class FeederCableRenderer extends TileEntitySpecialRenderer<TileGrid> {
    private static final int SEGMENTS = 36;
    private static final double R = 0.035;      // cable radius

    @Override
    public void render(TileGrid te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() != null && te.kind() == GridKind.SOLAR) SolarDeck.render(te, x, y, z, partialTicks);   // the tracking deck
        if (te.getWorld() != null && te.kind() == GridKind.EARTHING) BondStrap.render(te, x, y, z);              // earth bonding straps
        if (te.getWorld() != null && te.kind() == GridKind.WIND) WindRotor.render(te, x, y, z, partialTicks);     // nacelle + rotor on its tower
        if (te.getWorld() != null && te.kind() != GridKind.FEEDER) {
            GridPanelRenderer.render(te, x, y, z, partialTicks);                                                    // real control panels
            if (te.burntClient) scorch(x, y, z, te.getPos().hashCode());                                           // burnt out: charred over everything
            return;
        }
        if (te.getWorld() != null && te.burntClient) scorch(x, y, z, te.getPos().hashCode());
        if (te.getWorld() == null || te.kind() != GridKind.FEEDER || !te.jumper()) return;
        BlockPos rail = te.nearestRail();
        if (rail == null) return;
        BlockPos p = te.getPos();
        // terminal on top of the feeder -> clamp on the rail side facing the feeder
        double ax = 0.5, ay = 0.95, az = 0.5;
        double rx = rail.getX() - p.getX() + 0.5, rz = rail.getZ() - p.getZ() + 0.5;
        double len = Math.sqrt((rx - ax) * (rx - ax) + (rz - az) * (rz - az));
        if (len > 1.5) { rx -= (rx - ax) / len * 0.4; rz -= (rz - az) / len * 0.4; }   // stop at the rail's near side
        double ry = rail.getY() - p.getY() + 0.12;
        int col = te.cableColour();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableCull();
        Prims.begin();
        double sag = 0.25 + 0.08 * len;
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            double cx = ax + (rx - ax) * t, cz = az + (rz - az) * t;
            double cy = Math.max(Math.min(ry, 0.0) + R + 0.02, ay + (ry - ay) * t - sag * 4 * t * (1 - t));   // rests on the ground, never through it
            Prims.box(cx - R, cy - R, cz - R, cx + R, cy + R, cz + R, col);
        }
        // terminal post + lug on the feeder, bolted clamp on the rail
        Prims.box(ax - 0.07, ay - 0.08, az - 0.07, ax + 0.07, ay + 0.04, az + 0.07, 0xB87333);
        Prims.box(rx - 0.09, ry - 0.06, rz - 0.09, rx + 0.09, ry + 0.06, rz + 0.09, 0x7A7F86);
        Prims.box(rx - 0.03, ry + 0.06, rz - 0.03, rx + 0.03, ry + 0.10, rz + 0.03, 0xC9CDD2);
        Prims.end();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean isGlobalRenderer(TileGrid te) {
        return te.kind() == GridKind.FEEDER;
    }

    /** soot over the whole machine, black streaks running up from where it burnt, a blistered top */
    static void scorch(double x, double y, double z, int seed) {
        java.util.Random r = new java.util.Random(seed);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        Prims.begin();
        Prims.box(-0.08, -0.02, -0.08, 16.08, 16.08, 16.08, 0x120D09, 0.62f);           // soot
        for (int i = 0; i < 7; i++) {                                                      // smoke streaks up the sides
            double u = 1 + r.nextDouble() * 14, h = 6 + r.nextDouble() * 10, w = 0.6 + r.nextDouble() * 1.4;
            switch (i % 4) {
                case 0: Prims.box(u, 16 - h, -0.12, u + w, 16.1, -0.1, 0x050505, 0.85f); break;
                case 1: Prims.box(u, 16 - h, 16.1, u + w, 16.1, 16.12, 0x050505, 0.85f); break;
                case 2: Prims.box(-0.12, 16 - h, u, -0.1, 16.1, u + w, 0x050505, 0.85f); break;
                default: Prims.box(16.1, 16 - h, u, 16.12, 16.1, u + w, 0x050505, 0.85f); break;
            }
        }
        for (int i = 0; i < 5; i++) {                                                      // blistered paint on top
            double a = 2 + r.nextDouble() * 10, b = 2 + r.nextDouble() * 10, sz = 1 + r.nextDouble() * 2.5;
            Prims.box(a, 16.1, b, a + sz, 16.35, b + sz, 0x2A1E14);
        }
        Prims.end();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }
}
