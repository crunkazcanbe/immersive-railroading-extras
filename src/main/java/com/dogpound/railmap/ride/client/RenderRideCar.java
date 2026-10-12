package com.dogpound.railmap.ride.client;

import com.dogpound.railmap.ride.EntityRideCar;
import com.dogpound.railmap.ride.RideGeom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

/**
 * Draws a cabin / car from its bbkit model (built at 1/2 scale for the cabins, 1/4 for the funicular car so they fit a
 * block model's limits): turned to its travel, the funicular pitched to the track, a hanging cabin swinging on its
 * hanger about the grip on the rope.
 */
public class RenderRideCar extends Render<EntityRideCar> {
    static final float[] SCALE = {2f, 2f, 4f};
    static final String[] MODEL = {"ride_cabin_gondola", "ride_cabin_tram", "ride_car_funicular"};

    public RenderRideCar(RenderManager rm) { super(rm); shadowSize = 0.8f; }

    @Override protected ResourceLocation getEntityTexture(EntityRideCar e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

    @Override
    public void doRender(EntityRideCar e, double x, double y, double z, float yawIn, float pt) {
        int k = Math.max(0, Math.min(2, e.kind()));
        Minecraft mc = Minecraft.getMinecraft();
        int light = e.getBrightnessForRender();
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        float yaw = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
        float pitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * pt;
        float swing = e.prevSwing + (e.swing - e.prevSwing) * pt;
        BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180f - yaw, 0, 1, 0);
        if (k == RideGeom.FUNICULAR) GlStateManager.rotate(-pitch, 1, 0, 0);
        else {                                                                    // swing about the grip on the rope
            GlStateManager.translate(0, RideGeom.HANG, 0);
            GlStateManager.rotate(swing, 1, 0, 0);
            GlStateManager.translate(0, -RideGeom.HANG, 0);
        }
        float s = SCALE[k];
        GlStateManager.scale(s, s, s);
        GlStateManager.translate(-0.5, 0, -0.5);
        r.renderModelBrightnessColor(RideClient.baked(MODEL[k]), 1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        super.doRender(e, x, y, z, yawIn, pt);
    }
}
