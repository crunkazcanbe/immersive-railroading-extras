package com.dogpound.railmap.freight6.client;

import com.dogpound.railmap.client.render.Prims;
import com.dogpound.railmap.freight6.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/**
 * The live parts of batch 6 (model px, north-facing like the block models): pallets of goods on the rack decks, the
 * dock's roll-up door and leveller lip, the Christmas tree's LED string + presents, the festoon's bulbs, the
 * jack-o'-lanterns' flicker, and the forklifts.
 */
final class F6Render {
    private F6Render() {}

    static int turns(EnumFacing f) { return f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3; }

    static void begin(TileEntity te, double x, double y, double z, EnumFacing f, boolean bright) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns(f), 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        int l = bright ? 0xF000F0 : te.getWorld().getCombinedLight(te.getPos().up(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        Prims.begin();
    }

    static void end() {
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    static void item(ItemStack s, double px, double py, double pz, double scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, pz);
        GlStateManager.scale(16 * scale, 16 * scale, 16 * scale);
        GlStateManager.rotate(180f, 0, 1, 0);
        GlStateManager.enableTexture2D();
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.popMatrix();
    }

    /** festive LED colour of bulb i at time t in pattern p (0 off) */
    static int bulb(int i, int season, int pat, boolean show, long t) {
        int[] xmas = {0xE02020, 0x20C040, 0xFFC830, 0x3070FF, 0xFFF4E0}, hallow = {0xFF7A10, 0x9A30E0, 0x60E040, 0xFF7A10};
        int[] pal = season == Season.HALLOWEEN ? hallow : xmas;
        int col = pal[Math.floorMod(i, pal.length)];
        if (show) { int k = (int) (t / 3); return ((i + k) % 3 == 0) ? col : pal[Math.floorMod(i + k, pal.length)]; }
        switch (pat) {
            case 1 -> { return (i + t / 6) % 4 == 0 ? Prims.shade(col, 0.25f) : col; }
            case 2 -> { long h = (i * 7919L + (t / 9) * 104729L) % 13; return h < 3 ? Prims.shade(col, 0.2f) : col; }
            case 3 -> { float f = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin((t + i * 4) * 0.08)); return Prims.shade(col, f); }
            default -> { return col; }
        }
    }

    static void lamp(double x, double y, double z, double r, int col, boolean on) {
        if (!on) { Prims.box(x - r, y - r, z - r, x + r, y + r, z + r, Prims.shade(col, 0.3f)); return; }
        Prims.box(x - r, y - r, z - r, x + r, y + r, z + r, col);
        Prims.glow(x, y, z - r - 0.01, r * 3.2, col);
    }

    // ---- warehouse rack: pallets on three levels (decks at 1, 11, 21 px; three pallet spots across) -------------------
    static final class Rack extends TileEntitySpecialRenderer<TileWarehouseRack> {
        @Override
        public void render(TileWarehouseRack te, double x, double y, double z, float pt, int destroy, float alpha) {
            if (te.getWorld() == null || x * x + y * y + z * z > 40 * 40) return;
            begin(te, x, y, z, te.controlFacing(), false);
            for (int k = 0; k < 3; k++) {
                double deck = 1.0 + k * 10.0;
                int shown = 0;
                for (int i = 0; i < 27 && shown < 3; i++) {
                    ItemStack s = te.level(k).getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    double cx = 3.0 + shown * 5.0;
                    Prims.box(cx - 2.2, deck, 3.0, cx + 2.2, deck + 0.9, 13.0, 0xB08850);                         // the pallet
                    for (double sx : new double[]{cx - 2.2, cx - 0.3, cx + 1.6}) Prims.box(sx, deck, 3.0, sx + 0.6, deck + 0.5, 13.0, 0x8A6838);
                    int layers = Math.max(1, Math.min(4, s.getCount() / 16 + 1));
                    Prims.box(cx - 2.0, deck + 0.9, 3.3, cx + 2.0, deck + 0.9 + layers * 1.6, 12.7, 0xC8B48A, 0.55f);   // stretch-wrapped load
                    item(s, cx, deck + 1.2 + layers * 1.6, 2.6, 0.24);
                    shown++;
                }
            }
            end();
        }
        @Override public boolean isGlobalRenderer(TileWarehouseRack te) { return true; }
    }

    // ---- dock door: slatted curtain in the opening x 2..14, y 0..28 at z 1.2; leveller lip at the south edge -------------
    static final class Dock extends TileEntitySpecialRenderer<TileWarehouseDock> {
        @Override
        public void render(TileWarehouseDock te, double x, double y, double z, float pt, int destroy, float alpha) {
            if (te.getWorld() == null || x * x + y * y + z * z > 64 * 64) return;
            float open = te.controls().get("doorg") / 100f;
            boolean lip = te.controls().get("m_lev") == 1;
            begin(te, x, y, z, te.controlFacing(), false);
            double bottom = 28.0 * open;
            for (double sy = bottom; sy < 27.8; sy += 1.2) {
                Prims.box(2.0, sy, 7.6, 14.0, Math.min(28.0, sy + 1.1), 8.0, 0xD8DCE0);
                Prims.box(2.0, Math.min(28.0, sy + 1.1), 7.65, 14.0, Math.min(28.0, sy + 1.2), 7.95, 0x9AA0A6);
            }
            if (open < 0.99) {
                Prims.box(2.0, bottom, 7.4, 14.0, bottom + 0.6, 8.2, 0x2A2A2A);                                            // bottom rubber + bar
                Prims.box(7.4, bottom + 1.0, 7.3, 8.6, bottom + 1.4, 7.6, 0x3A3F44);                                       // lift handle
            }
            GlStateManager.pushMatrix();                                                                                     // the lip
            GlStateManager.translate(8, 2.0, 16.0);
            GlStateManager.rotate(lip ? 0f : -80f, 1, 0, 0);
            Prims.box(-6.0, -0.4, 0, 6.0, 0, 6.0, 0x5A5E62);
            for (double gx = -5.5; gx < 6; gx += 1.0) Prims.box(gx, 0, 0.5, gx + 0.3, 0.12, 5.5, 0x6E7276);
            Prims.box(-6.0, -0.4, 5.6, 6.0, 0, 6.0, 0xD8B020);
            GlStateManager.popMatrix();
            end();
        }
        @Override public boolean isGlobalRenderer(TileWarehouseDock te) { return true; }
    }

    // ---- Christmas tree: a spiral LED string on the cone (6..30 px), the star, presents round the tub ------------------
    static final class Tree extends TileEntitySpecialRenderer<TileXmasTree> {
        @Override
        public void render(TileXmasTree te, double x, double y, double z, float pt, int destroy, float alpha) {
            if (te.getWorld() == null || x * x + y * y + z * z > 80 * 80) return;
            boolean lit = te.controls().get("lit") == 1;
            int pat = te.controls().get("pat"), sea = Season.CHRISTMAS;
            boolean show = te.controls().get("show") == 1;
            long t = te.getWorld().getTotalWorldTime();
            begin(te, x, y, z, te.controlFacing(), lit);
            int n = 0;
            for (double a = 0; a < Math.PI * 2 * 5.2; a += 0.55, n++) {
                double yy = 7.0 + a / (Math.PI * 2 * 5.2) * 22.0;
                double r = 6.8 * (1 - (yy - 6.0) / 25.5) + 0.35;
                lamp(8 + Math.cos(a) * r, yy, 8 + Math.sin(a) * r, 0.28, bulb(n, sea, pat, show, t), lit);
            }
            int col = lit ? 0xFFE060 : 0xA08830;
            Prims.box(7.4, 29.4, 7.4, 8.6, 31.8, 8.6, col);
            Prims.box(6.6, 30.2, 7.7, 9.4, 31.0, 8.3, col);
            if (lit) Prims.glow(8, 30.6, 7.3, 2.6 + (show ? 1.2 * Math.sin(t * 0.3) : 0), 0xFFE070);
            int presents = 0;
            for (int i = 0; i < 27; i++) if (!te.storage(0).getStackInSlot(i).isEmpty()) presents++;
            int[] paper = {0xC02028, 0x2050C0, 0x20A050, 0xE0C030, 0xA030B0, 0xE8E8E8};
            double[][] spot = {{2.2, 2.4}, {13.6, 3.0}, {3.0, 13.4}, {13.0, 13.2}, {8.0, 1.6}, {1.6, 8.0}, {14.2, 8.4}, {8.2, 14.4}};
            for (int i = 0; i < Math.min(spot.length, presents); i++) {
                double s = 1.2 + (i % 3) * 0.35, px = spot[i][0], pz = spot[i][1];
                int c = paper[i % paper.length];
                Prims.box(px - s, 0, pz - s, px + s, s * 1.5, pz + s, c);
                Prims.box(px - 0.2, 0, pz - s - 0.02, px + 0.2, s * 1.5 + 0.02, pz + s + 0.02, 0xF0E0A0);                  // ribbon
                Prims.box(px - s - 0.02, 0, pz - 0.2, px + s + 0.02, s * 1.5 + 0.02, pz + 0.2, 0xF0E0A0);
                Prims.box(px - 0.5, s * 1.5, pz - 0.3, px + 0.5, s * 1.5 + 0.5, pz + 0.3, 0xF0E0A0);                     // bow
            }
            end();
        }
        @Override public boolean isGlobalRenderer(TileXmasTree te) { return true; }
    }

    // ---- festoon: bulbs on a swag hanging from the fascia (x 0..16, top 12.6, dip 3 px), z 12.2 ------------------------
    static final class Festoon extends TileEntitySpecialRenderer<TileHolidayLights> {
        @Override
        public void render(TileHolidayLights te, double x, double y, double z, float pt, int destroy, float alpha) {
            if (te.getWorld() == null || x * x + y * y + z * z > 80 * 80) return;
            boolean lit = te.controls().get("lit") == 1;
            int pat = te.controls().get("pat"), sea = te.controls().get("season");
            boolean show = te.controls().get("show") == 1;
            long t = te.getWorld().getTotalWorldTime();
            begin(te, x, y, z, te.controlFacing(), lit);
            int n = 0;
            for (double u = 0.8; u < 16; u += 1.6, n++) {
                double yy = 12.4 - 3.0 * Math.sin(Math.PI * u / 16.0);
                Prims.box(u - 0.08, yy + 0.3, 12.1, u + 0.08, yy + 0.7, 12.3, 0x202020);
                lamp(u, yy, 12.2, 0.32, bulb(n, sea, pat, show, t), lit);
            }
            end();
        }
        @Override public boolean isGlobalRenderer(TileHolidayLights te) { return true; }
    }

    // ---- jack-o'-lanterns: candle flicker through the carved faces (eyes + mouth on the north faces) --------------------
    static final class Pumpkins extends TileEntitySpecialRenderer<TileHalloweenDisplay> {
        static final double[][] FACES = {{4.0, 5.04, 4.3, 2.6}, {11.5, 5.04, 4.3, 2.6}, {8.0, 9.36, 4.8, 3.4}};   // centre u(x), y, z of the face, size
        @Override
        public void render(TileHalloweenDisplay te, double x, double y, double z, float pt, int destroy, float alpha) {
            if (te.getWorld() == null || x * x + y * y + z * z > 64 * 64) return;
            boolean lit = te.controls().get("lit") == 1;
            if (!lit) return;
            long t = te.getWorld().getTotalWorldTime();
            begin(te, x, y, z, te.controlFacing(), true);
            for (int i = 0; i < FACES.length; i++) {
                double[] f = FACES[i];
                float fl = 0.65f + 0.35f * (float) Math.abs(Math.sin(t * 0.37 + i * 2.1) * Math.sin(t * 0.13 + i));
                int c = Prims.shade(0xFFA020, fl);
                double s = f[3] / 2.6, fz = f[2] - 0.04;
                Prims.box(f[0] - 0.9 * s, f[1] + 0.4 * s, fz - 0.05, f[0] - 0.3 * s, f[1] + 0.9 * s, fz, c);       // eyes
                Prims.box(f[0] + 0.3 * s, f[1] + 0.4 * s, fz - 0.05, f[0] + 0.9 * s, f[1] + 0.9 * s, fz, c);
                Prims.box(f[0] - 0.9 * s, f[1] - 0.6 * s, fz - 0.05, f[0] + 0.9 * s, f[1] - 0.2 * s, fz, c);       // mouth
                Prims.glow(f[0], f[1], fz - 0.1, 2.4 * s * fl, 0xFF8A10);
            }
            end();
        }
        @Override public boolean isGlobalRenderer(TileHalloweenDisplay te) { return true; }
    }

    // ---- forklift: the baked body + mast, the fork carriage at its height, the pallet and goods ------------------------
    static final class Forklift extends Render<EntityForklift> {
        Forklift(RenderManager rm) { super(rm); shadowSize = 0.7f; }

        @Override protected ResourceLocation getEntityTexture(EntityForklift e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

        @Override
        public void doRender(EntityForklift e, double x, double y, double z, float yawIn, float pt) {
            Minecraft mc = Minecraft.getMinecraft();
            int light = e.getBrightnessForRender();
            float yaw = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
            float fy = e.prevForkY + (e.forkY - e.prevForkY) * pt;
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(180f - yaw, 0, 1, 0);
            GlStateManager.translate(-0.5, 0, -0.5);
            BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
            r.renderModelBrightnessColor(F6Client.baked("forklift"), 1f, 1f, 1f, 1f);
            GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
            double cy = 1.0 + fy * 16;                                                         // the fork carriage
            Prims.begin();
            Prims.box(3.0, cy, 1.6, 13.0, cy + 6.0, 2.2, 0x2A2C30);
            for (double fx : new double[]{4.4, 10.6}) { Prims.box(fx, cy - 0.6, -10.0, fx + 1.0, cy, 2.2, 0x3A3C40); Prims.box(fx, cy - 0.6, 1.8, fx + 1.0, cy + 6.0, 2.2, 0x3A3C40); }
            ItemStack s = e.load();
            if (!s.isEmpty()) {
                Prims.box(2.4, cy, -10.4, 13.6, cy + 0.9, 1.2, 0xB08850);
                int layers = Math.max(1, Math.min(4, s.getCount() / 16 + 1));
                Prims.box(2.6, cy + 0.9, -10.2, 13.4, cy + 0.9 + layers * 1.8, 1.0, 0xC8B48A, 0.6f);
                Prims.end();
                item(s, 8, cy + 1.6 + layers * 1.8, -4.6, 0.3);
            } else Prims.end();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            super.doRender(e, x, y, z, yawIn, pt);
        }
    }
}
