package com.dogpound.railmap.steam.client;

import com.dogpound.railmap.client.render.Prims;
import com.dogpound.railmap.steam.TileCoalingTower;
import com.dogpound.railmap.steam.TileWaterColumn;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The moving parts of the steam depot, drawn live (model px, north-facing like the block models):
 * the Coaling Tower's skip running up its guides and tipping at the top, and its chute apron swinging down over the track;
 * the Water Column's delivery arm swinging out over the track with its leather bag, and the water running from it.
 */
final class SteamRenderer {
    private SteamRenderer() {}

    private static final Map<TileEntity, float[]> ANIM = new WeakHashMap<>();

    static float ease(TileEntity t, int k, float target, float speed, float pt) {
        float[] a = ANIM.computeIfAbsent(t, x -> new float[]{target, target, target, target});
        a[k] += Math.max(-speed, Math.min(speed, target - a[k])) * Math.max(0.2f, pt);
        return a[k];
    }

    static void begin(TileEntity te, double x, double y, double z, EnumFacing f) {
        int turns = turns(f);
        int l = te.getWorld().getCombinedLight(te.getPos().up(), 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        Prims.begin();
    }

    static void end() {
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    /** coaling tower: legs + skip guides from the base (0..32 px) up to the bunker (dy blocks above, its hopper cone
     *  from dy*16-16), the skip running up them and tipping at the top, the hoist rope, the bunker's chute apron */
    static final class Tower extends TileEntitySpecialRenderer<TileCoalingTower> {
        @Override
        public void render(TileCoalingTower te, double x, double y, double z, float pt, int destroy, float alpha) {
            if (te.getWorld() == null || x * x + y * y + z * z > 128 * 128) return;
            int dy = te.controls().get("bdy");
            float h = ease(te, 0, te.skip(), 0.02f, pt);
            float apron = ease(te, 1, te.controls().get("m_chute") == 1 ? 118 : 4, 3f, pt);
            begin(te, x, y, z, te.controlFacing());
            if (dy >= 2) {
                double top = dy * 16 - 16;                                          // the bunker's cone bottom
                for (double[] l : new double[][]{{0.4, 0.4}, {14.0, 0.4}, {0.4, 14.0}, {14.0, 14.0}})
                    if (top > 32) Prims.box(l[0], 32, l[1], l[0] + 1.6, top, l[1] + 1.6, 0x9A9890);
                for (double ty = 46; ty < top - 4; ty += 32) {                      // tie beams every two blocks
                    Prims.box(0.4, ty, 14.2, 15.6, ty + 1.2, 15.4, 0x8E8C84);
                    Prims.box(0.6, ty, 0.4, 1.8, ty + 1.2, 15.6, 0x8E8C84);
                    Prims.box(14.2, ty, 0.4, 15.4, ty + 1.2, 15.6, 0x8E8C84);
                }
                for (double gz : new double[]{5.2, 10.4}) if (top > 32) Prims.box(16.0, 32, gz, 17.2, top, gz + 0.6, 0x8C9298);
                // the skip: bottom at 3 px, tips at the bunker's horns (dy*16 + 16)
                double sy = 3 + h * (dy * 16 + 13 - 3);
                GlStateManager.pushMatrix();
                GlStateManager.translate(17.6, sy, 8);
                if (h > 0.97f) GlStateManager.rotate((h - 0.97f) / 0.03f * -55f, 0, 0, 1);
                Prims.box(-1.6, 0, -2.2, 1.6, 3.2, 2.2, 0x3A3F44);
                if ((h < 0.99f && te.controls().get("hoistl") == 1) || h < 0.02f) Prims.box(-1.4, 3.2, -2.0, 1.4, 3.6, 2.0, 0x23272A);   // coal aboard
                Prims.box(-1.8, -0.4, -2.5, 1.8, 0, 2.5, 0x5A5E62);
                for (int s = -1; s <= 1; s += 2) Prims.box(-0.4, 0.4, s * 2.5 - 0.3, 0.4, 1.2, s * 2.5 + 0.3, 0x8A8E92);
                GlStateManager.popMatrix();
                double sheave = dy * 16 + 27;
                if (sheave > sy + 3.6) Prims.box(17.45, sy + 3.6, 7.85, 17.75, sheave, 8.15, 0x9A9A9A);    // hoist rope
                // the chute apron on the bunker (its own facing)
                net.minecraft.block.state.IBlockState bs = te.getWorld().getBlockState(te.getPos().up(dy));
                int rel = 0;
                if (bs.getPropertyKeys().contains(com.dogpound.railmap.block.BlockStationDevice.FACING)) rel = turns(bs.getValue(com.dogpound.railmap.block.BlockStationDevice.FACING)) - turns(te.controlFacing());
                GlStateManager.pushMatrix();
                GlStateManager.translate(8, dy * 16, 8);
                GlStateManager.rotate(-90f * rel, 0, 1, 0);
                GlStateManager.translate(-8, 0, -8);
                GlStateManager.translate(8, -9.8, 16.6);
                GlStateManager.rotate(apron, 1, 0, 0);
                Prims.box(-3.2, 0, -0.3, 3.2, 14, 0.3, 0x4A4F54);
                Prims.box(-3.4, 0, -0.3, -3.0, 14, 2.2, 0x5A5E62);
                Prims.box(3.0, 0, -0.3, 3.4, 14, 2.2, 0x5A5E62);
                Prims.box(-3.4, 13.4, -0.3, 3.4, 14, 1.0, 0xC8A020);
                if (te.controls().get("flow") == 1) Prims.box(-2.6, 0.5, 0.3, 2.6, 13.5, 1.6, 0x15181A);
                GlStateManager.popMatrix();
            }
            end();
        }
        @Override public boolean isGlobalRenderer(TileCoalingTower te) { return true; }
    }

    static int turns(EnumFacing f) { return f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3; }

    /** water column: arm pivot on the column top (8, 29, 8); stowed along +x, out over the track at +z */
    static final class Column extends TileEntitySpecialRenderer<TileWaterColumn> {
        @Override
        public void render(TileWaterColumn te, double x, double y, double z, float pt, int destroy, float alpha) {
            if (te.getWorld() == null || x * x + y * y + z * z > 96 * 96) return;
            float a = ease(te, 0, te.armIsOut() ? -90 : 0, 1.5f, pt);
            begin(te, x, y, z, te.controlFacing());
            GlStateManager.translate(8, 29, 8);
            GlStateManager.rotate(a, 0, 1, 0);
            Prims.box(-1.4, -0.6, -1.4, 1.4, 1.2, 1.4, 0x2D3A32);                    // swivel head
            Prims.box(0, -0.2, -0.9, 26, 1.6, 0.9, 0x2F4A3A);                        // the delivery arm (cast pipe)
            for (double u = 3; u < 26; u += 5.5) Prims.box(u, -0.4, -1.0, u + 0.6, 1.8, 1.0, 0x23382C);   // flange joints
            Prims.box(0, 1.6, -0.15, 22, 1.9, 0.15, 0x3A3F44);                       // tie rod
            Prims.box(-0.2, 1.9, -0.15, 0.2, 4.6, 0.15, 0x3A3F44);                   // king post
            Prims.box(-6, -0.4, -1.2, -2, 1.6, 1.2, 0x1E2A22);                       // balance weight
            Prims.box(23.6, -2.0, -1.6, 27.0, 0, 1.6, 0x2F4A3A);                     // elbow
            Prims.box(24.0, -12.0, -1.2, 26.6, -2.0, 1.2, 0x5C3A1E);                 // leather bag
            for (double v = -11; v < -2; v += 2.5) Prims.box(23.8, v, -1.4, 26.8, v + 0.5, 1.4, 0x3A2412);   // bag hoops
            Prims.box(24.6, -13.0, -0.6, 26.0, -12.0, 0.6, 0x8A6A3A);                // nozzle ring
            Prims.box(26.6, -9, -0.2, 27.2, -2, 0.2, 0x8A8E92);                      // guide chain
            if (te.flowing()) { Prims.emissive(true); Prims.box(24.8, -22, -0.8, 25.8, -13, 0.8, 0x4FA8E8, 0.75f); Prims.emissive(false); }
            end();
        }
        @Override public boolean isGlobalRenderer(TileWaterColumn te) { return true; }
    }
}
