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
        if (te.getWorld() != null && te.kind() != GridKind.FEEDER) { GridPanelRenderer.render(te, x, y, z, partialTicks); return; }   // real control panels
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
}
