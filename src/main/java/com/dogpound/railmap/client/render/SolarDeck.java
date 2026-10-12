package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.TileGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * The Solar Array's single-axis tracker (requested feature):
 * the panel deck on a north-south torque tube, turned by its drive to face the sun across the sky; flat at night,
 * in FIXED mode and when stowed for a storm. The drive is slow, so the deck eases round.
 */
final class SolarDeck {
    private SolarDeck() {}

    static void render(TileGrid te, double x, double y, double z, float pt) {
        World w = te.getWorld();
        float[] a = te.anim().computeIfAbsent("solarTilt", k -> new float[]{(float) te.solarTilt(w, pt)});
        double target = te.solarTilt(w, pt);
        a[0] += (float) Math.max(-0.4, Math.min(0.4, target - a[0]));

        int l = w.getCombinedLight(te.getPos().up(), 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 10.8 / 16.0, z + 0.5);
        GlStateManager.rotate(a[0], 0, 0, 1);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        Prims.begin();
        Prims.box(-0.8, -1.6, -8, 0.8, -0.6, 8, 0x7A8088);                // torque tube
        Prims.box(-7.8, -0.6, -7.8, 7.8, 0, 7.8, 0xB8BEC4);              // aluminium frame
        Prims.box(-7.8, -1.0, -7.0, 7.8, -0.6, -6.2, 0x8A9098);          // purlins under the deck
        Prims.box(-7.8, -1.0, 6.2, 7.8, -0.6, 7.0, 0x8A9098);
        Prims.end();
        // the cells: the real panel texture on the top face
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sp = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("irextras:blocks/grid_solar_cells");
        GlStateManager.disableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        double e = 7.4, h = 0.03;
        b.pos(-e, h, -e).tex(sp.getMinU(), sp.getMinV()).endVertex();
        b.pos(-e, h, e).tex(sp.getMinU(), sp.getMaxV()).endVertex();
        b.pos(e, h, e).tex(sp.getMaxU(), sp.getMaxV()).endVertex();
        b.pos(e, h, -e).tex(sp.getMaxU(), sp.getMinV()).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }
}
