package com.dogpound.railmap.drone.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.drone.DroneRegistry;
import com.dogpound.railmap.drone.EntityDrone;
import com.dogpound.railmap.weather.TileWxDepot;
import com.dogpound.railmap.weather.WxRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Client side of the drones and weather trains: item models, the drone's renderer (bbkit body, four spinning rotors
 * turning in pairs, the camera gimbal pitched to where it looks, the whole craft banking into its flight) and the depot
 * desk's shelf.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class DroneClient {
    private DroneClient() {}

    /** ClientProxy.preInit */
    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityDrone.class, DroneRender::new);
        ClientRegistry.bindTileEntitySpecialRenderer(TileWxDepot.class, new com.dogpound.railmap.client.render.ShelfRenderer());
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(DroneRegistry.dock), 0, new ModelResourceLocation(DroneRegistry.dock.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(WxRegistry.depot), 0, new ModelResourceLocation(WxRegistry.depot.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(DroneRegistry.airframe, 0, new ModelResourceLocation(RailMap.MODID + ":drone", "inventory"));
        ModelLoader.setCustomModelResourceLocation(DroneRegistry.battery, 0, new ModelResourceLocation(RailMap.MODID + ":drone_battery", "inventory"));
        ModelLoader.setCustomModelResourceLocation(DroneRegistry.controller, 0, new ModelResourceLocation(RailMap.MODID + ":drone_controller", "inventory"));
        ModelLoader.setCustomModelResourceLocation(WxRegistry.plough, 0, new ModelResourceLocation(RailMap.MODID + ":snow_plough", "inventory"));
        // the drone's moving parts are baked as extra variants of its item, drawn by DroneRender
        ModelBakery.registerItemVariants(DroneRegistry.airframe, new ResourceLocation(RailMap.MODID, "drone"), new ResourceLocation(RailMap.MODID, "drone_body"),
                new ResourceLocation(RailMap.MODID, "drone_rotor"), new ResourceLocation(RailMap.MODID, "drone_gimbal"));
    }

    public static final class DroneRender extends Render<EntityDrone> {
        /** motor centres (model px) - tools/gen_bb_drone.py MOTORS */
        static final double[][] MOTORS = {{-0.5, 7.4, -0.5}, {16.5, 7.4, -0.5}, {16.5, 7.4, 16.5}, {-0.5, 7.4, 16.5}};
        /** gimbal pivot (model px) */
        static final double GX = 8, GY = 2.6, GZ = 2.4;
        static final float SCALE = 0.62f;
        private IBakedModel body, rotor, gimbal;

        public DroneRender(RenderManager rm) { super(rm); shadowSize = 0.45f; }

        @Override protected ResourceLocation getEntityTexture(EntityDrone e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

        @Override
        public void doRender(EntityDrone e, double x, double y, double z, float yaw, float pt) {
            Minecraft mc = Minecraft.getMinecraft();
            if (body == null) {
                ModelManager mm = mc.getRenderItem().getItemModelMesher().getModelManager();
                body = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":drone_body", "inventory"));
                rotor = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":drone_rotor", "inventory"));
                gimbal = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":drone_gimbal", "inventory"));
            }
            int light = e.getBrightnessForRender();
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.color(1, 1, 1, 1);
            float ry = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
            double vx = e.posX - e.prevPosX, vz = e.posZ - e.prevPosZ;
            double fwd = -Math.sin(Math.toRadians(ry)) * vx + Math.cos(Math.toRadians(ry)) * vz;   // speed along its heading, blocks/tick
            double side = Math.cos(Math.toRadians(ry)) * vx + Math.sin(Math.toRadians(ry)) * vz;
            float pitch = (float) Math.max(-22, Math.min(22, fwd * 55)), roll = (float) Math.max(-18, Math.min(18, side * 45));
            BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(180f - ry, 0, 1, 0);
            GlStateManager.translate(0, 0.28, 0);
            GlStateManager.rotate(-pitch, 1, 0, 0);                                // nose down into the flight
            GlStateManager.rotate(roll, 0, 0, 1);
            GlStateManager.translate(0, -0.28, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);
            GlStateManager.translate(-0.5, 0, -0.5);
            r.renderModelBrightnessColor(body, 1f, 1f, 1f, 1f);
            float spin = e.spinPrev + (e.spin - e.spinPrev) * pt;
            for (int i = 0; i < 4; i++) {
                double[] m = MOTORS[i];
                GlStateManager.pushMatrix();
                GlStateManager.translate(m[0] / 16, m[1] / 16, m[2] / 16);
                GlStateManager.rotate((i % 2 == 0 ? spin : -spin) + i * 45f, 0, 1, 0);   // diagonal pairs counter-rotate
                GlStateManager.translate(-0.5, 0, -0.5);
                r.renderModelBrightnessColor(rotor, 1f, 1f, 1f, 1f);
                GlStateManager.popMatrix();
            }
            GlStateManager.translate(GX / 16, GY / 16, GZ / 16);
            GlStateManager.rotate(-e.gimbal(), 1, 0, 0);                           // the camera looks down by the gimbal angle
            GlStateManager.translate(-GX / 16, -GY / 16, -GZ / 16);
            r.renderModelBrightnessColor(gimbal, 1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            super.doRender(e, x, y, z, yaw, pt);
        }
    }
}
