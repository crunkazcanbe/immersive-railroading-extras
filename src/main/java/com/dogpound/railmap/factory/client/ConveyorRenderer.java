package com.dogpound.railmap.factory.client;

import com.dogpound.railmap.factory.conveyor.BlockConveyor.Shape;
import com.dogpound.railmap.factory.conveyor.TileConveyor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/** The moving belt surface (straight, curve or ramp) and what rides on it, sliding smoothly between ticks. */
public class ConveyorRenderer extends TileEntitySpecialRenderer<TileConveyor> {
    static final double HALF = 6 / 16.0;

    @Override
    public void render(TileConveyor te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        EnumFacing f = te.facing();
        Shape sh = te.shape;
        float run = te.travelPrev + (te.travel - te.travelPrev) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        BufferBuilder bb = BeltDraw.begin(te.getWorld().getCombinedLight(te.getPos(), 0));
        belt(bb, sh, f, run);
        BeltDraw.end();

        if (!te.loads.isEmpty()) {
            RenderHelper.enableStandardItemLighting();
            for (TileConveyor.Load l : te.loads) {
                double p = Math.min(1, l.prev + (l.pos - l.prev) * pt);
                double[] at = TileConveyor.point(sh, f, p);
                double[] ahead = TileConveyor.point(sh, f, Math.min(1, p + 0.02));
                double yaw = Math.toDegrees(Math.atan2(ahead[0] - at[0], ahead[2] - at[2]));
                double pitch = sh == Shape.UP ? -45 : sh == Shape.DOWN ? 45 : 0;
                GlStateManager.pushMatrix();
                GlStateManager.translate(at[0], at[1] + 0.07, at[2]);
                GlStateManager.rotate((float) yaw, 0, 1, 0);
                GlStateManager.rotate((float) (90 + pitch), 1, 0, 0);
                GlStateManager.scale(0.45f, 0.45f, 0.45f);
                Minecraft.getMinecraft().getRenderItem().renderItem(l.stack, ItemCameraTransforms.TransformType.FIXED);
                GlStateManager.popMatrix();
            }
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.popMatrix();
    }

    /** the belt surface along the conveyor's path */
    static void belt(BufferBuilder bb, Shape sh, EnumFacing f, double run) {
        if (sh == Shape.LEFT || sh == Shape.RIGHT) {
            EnumFacing in = sh == Shape.LEFT ? f.rotateYCCW() : f.rotateY();
            double cx = 0.5 + 0.5 * (in.getXOffset() + f.getXOffset()), cz = 0.5 + 0.5 * (in.getZOffset() + f.getZOffset());
            int n = 10;
            double[][] pts = new double[n + 1][], side = new double[n + 1][];
            for (int i = 0; i <= n; i++) {
                double[] p = TileConveyor.point(sh, f, i / (double) n);
                double rx = p[0] - cx, rz = p[2] - cz, r = Math.sqrt(rx * rx + rz * rz);
                pts[i] = p;
                side[i] = new double[]{rx / r * HALF, 0, rz / r * HALF};
            }
            BeltDraw.path(bb, pts, side, run, 255);
            return;
        }
        double lift = sh == Shape.STRAIGHT ? 0 : 0.3 / 16;
        double[] a = TileConveyor.point(sh, f, 0), b = TileConveyor.point(sh, f, 1);
        a[1] += lift; b[1] += lift;
        BeltDraw.straight(bb, a, b, HALF, run, 255);
    }

    @Override public boolean isGlobalRenderer(TileConveyor te) { return false; }
}
