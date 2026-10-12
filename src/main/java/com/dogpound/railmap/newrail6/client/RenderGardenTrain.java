package com.dogpound.railmap.newrail6.client;

import com.dogpound.railmap.newrail6.EntityGardenTrain;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/** The garden train: the locomotive at the entity, the driving truck and two cars where they are on the track behind it. */
public class RenderGardenTrain extends Render<EntityGardenTrain> {
    static final String[] CAR = {"garden_truck", "garden_car", "garden_car"};

    public RenderGardenTrain(RenderManager rm) { super(rm); shadowSize = 0.4f; }

    @Override protected ResourceLocation getEntityTexture(EntityGardenTrain e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

    @Override
    public void doRender(EntityGardenTrain e, double x, double y, double z, float yawIn, float pt) {
        int light = e.getBrightnessForRender();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        float yaw = e.prevRotationYaw + MathHelper.wrapDegrees(e.rotationYaw - e.prevRotationYaw) * pt;
        part("garden_loco", x, y, z, yaw, light);
        double ex = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt, ey = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt, ez = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
        for (int i = 0; i < CAR.length; i++) {
            double[] a = e.prevCarPose[i], b = e.carPose[i];
            if (a == null || b == null) continue;
            double cx = a[0] + (b[0] - a[0]) * pt, cy = a[1] + (b[1] - a[1]) * pt, cz = a[2] + (b[2] - a[2]) * pt;
            float cyaw = (float) (a[3] + MathHelper.wrapDegrees(b[3] - a[3]) * pt);
            part(CAR[i], x + cx - ex, y + cy - ey, z + cz - ez, cyaw, light);
        }
        GlStateManager.enableLighting();
        super.doRender(e, x, y, z, yawIn, pt);
    }

    static void part(String name, double x, double y, double z, float yaw, int light) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180f - yaw, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        NR6Client.draw(name, light);
        GlStateManager.popMatrix();
    }
}
