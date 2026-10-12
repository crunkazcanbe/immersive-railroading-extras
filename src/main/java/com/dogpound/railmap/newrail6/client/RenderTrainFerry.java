package com.dogpound.railmap.newrail6.client;

import com.dogpound.railmap.newrail6.EntityTrainFerry;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

/**
 * The train ferry from its bbkit model, built at 1/6 scale (the hull runs z -16..32, drawn x6 = an 18-block
 * ship), bow pointing along its heading, the stern door hinged down onto the link span while it is open.
 */
public class RenderTrainFerry extends Render<EntityTrainFerry> {
    static final float S = 6f;

    public RenderTrainFerry(RenderManager rm) { super(rm); shadowSize = 0; }

    @Override protected ResourceLocation getEntityTexture(EntityTrainFerry e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

    @Override
    public void doRender(EntityTrainFerry e, double x, double y, double z, float yawIn, float pt) {
        int light = e.getBrightnessForRender();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        float yaw = e.prevRotationYaw + net.minecraft.util.math.MathHelper.wrapDegrees(e.rotationYaw - e.prevRotationYaw) * pt;
        float door = e.prevDoor + (e.door - e.prevDoor) * pt;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y - 1.2, z);
        GlStateManager.rotate(180f - yaw, 0, 1, 0);
        GlStateManager.rotate((float) Math.sin(e.ticksExisted * 0.04) * 0.6f, 0, 0, 1);         // a slow roll
        GlStateManager.scale(S, S, S);
        GlStateManager.translate(-0.5, 0, -0.5);
        NR6Client.draw("train_ferry_ship", light);
        // stern door: hinged at the stern's deck edge (model z = 32, y = 6/16 above the keel datum)
        GlStateManager.translate(0.5, 6 / 16.0, 2.0);
        GlStateManager.rotate(door * 80f, 1, 0, 0);
        GlStateManager.translate(-0.5, -6 / 16.0, -2.0);
        NR6Client.draw("train_ferry_door", light);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        super.doRender(e, x, y, z, yawIn, pt);
    }
}
