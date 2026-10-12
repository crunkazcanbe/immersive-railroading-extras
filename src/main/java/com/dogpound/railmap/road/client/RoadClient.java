package com.dogpound.railmap.road.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.road.EntityRoadVehicle;
import com.dogpound.railmap.road.RoadBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * Road vehicles' renderer (batch 5 #3): the bbkit body (tools/gen_bb_road.py, modelled at 1/2 - 1/4 size, drawn 2-4x)
 * plus its painted panels tinted to the vehicle's colour, and the live lamps drawn full-bright over the lenses: brake
 * lights, the flashing indicators before a turn, tail and headlights at night.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class RoadClient {
    private RoadClient() {}

    static final String[] BODIES = {"road_car", "road_van", "road_truck", "road_bus"};

    /** ClientProxy.preInit */
    public static void init() { RenderingRegistry.registerEntityRenderingHandler(EntityRoadVehicle.class, VehicleRender::new); }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        Item host = RoadBlocks.controller == null ? null : Item.getItemFromBlock(RoadBlocks.controller);
        if (host == null || host == net.minecraft.init.Items.AIR) return;
        ResourceLocation[] v = new ResourceLocation[BODIES.length * 2 + 1];
        for (int i = 0; i < BODIES.length; i++) { v[i * 2] = new ResourceLocation(RailMap.MODID, BODIES[i]); v[i * 2 + 1] = new ResourceLocation(RailMap.MODID, BODIES[i] + "_paint"); }
        v[v.length - 1] = host.getRegistryName();                                  // keep the cabinet's own item model
        ModelBakery.registerItemVariants(host, v);                                 // vehicle bodies ride along as item variants
    }

    public static final class VehicleRender extends Render<EntityRoadVehicle> {
        /** per kind: {head lamps x2, tail / brake lamps x2, indicators front-left, rear-left, front-right, rear-right}, model px - gen_bb_road.py LAMPS */
        static final float[][][][] LAMPS = {
            {{{1.6f, 5.0f, -9.62f, 4.4f, 6.2f, -9.5f}, {11.6f, 5.0f, -9.62f, 14.4f, 6.2f, -9.5f}}, {{1.2f, 5.0f, 25.4f, 4.0f, 6.4f, 25.52f}, {12.0f, 5.0f, 25.4f, 14.8f, 6.4f, 25.52f}}, {{1.2f, 3.8f, -9.62f, 2.8f, 4.6f, -9.5f}, {1.2f, 4.0f, 25.4f, 2.6f, 4.8f, 25.52f}, {13.2f, 3.8f, -9.62f, 14.8f, 4.6f, -9.5f}, {13.4f, 4.0f, 25.4f, 14.8f, 4.8f, 25.52f}}},
            {{{0.8f, 6.4f, -13.12f, 3.8f, 8.0f, -13.0f}, {12.2f, 6.4f, -13.12f, 15.2f, 8.0f, -13.0f}}, {{0.2f, 8.0f, 29.2f, 1.6f, 13.0f, 29.34f}, {14.4f, 8.0f, 29.2f, 15.8f, 13.0f, 29.34f}}, {{0.8f, 5.0f, -13.12f, 2.6f, 6.0f, -13.0f}, {0.2f, 13.2f, 29.2f, 1.6f, 14.4f, 29.34f}, {13.4f, 5.0f, -13.12f, 15.2f, 6.0f, -13.0f}, {14.4f, 13.2f, 29.2f, 15.8f, 14.4f, 29.34f}}},
            {{{2.0f, 4.6f, -13.66f, 4.6f, 6.0f, -13.56f}, {11.4f, 4.6f, -13.66f, 14.0f, 6.0f, -13.56f}}, {{1.6f, 3.0f, 29.4f, 3.6f, 4.2f, 29.5f}, {12.4f, 3.0f, 29.4f, 14.4f, 4.2f, 29.5f}}, {{1.2f, 4.6f, -13.66f, 2.0f, 6.0f, -13.56f}, {3.6f, 3.0f, 29.4f, 4.6f, 4.2f, 29.5f}, {14.0f, 4.6f, -13.66f, 14.8f, 6.0f, -13.56f}, {11.4f, 3.0f, 29.4f, 12.4f, 4.2f, 29.5f}}},
            {{{3.2f, 2.2f, -15.12f, 5.0f, 3.4f, -15.0f}, {11.0f, 2.2f, -15.12f, 12.8f, 3.4f, -15.0f}}, {{3.0f, 2.4f, 31.0f, 4.0f, 4.6f, 31.12f}, {12.0f, 2.4f, 31.0f, 13.0f, 4.6f, 31.12f}}, {{3.2f, 3.6f, -15.12f, 4.4f, 4.2f, -15.0f}, {3.0f, 5.0f, 31.0f, 4.0f, 5.8f, 31.12f}, {11.6f, 3.6f, -15.12f, 12.8f, 4.2f, -15.0f}, {12.0f, 5.0f, 31.0f, 13.0f, 5.8f, 31.12f}}}};
        private final IBakedModel[] body = new IBakedModel[4], paint = new IBakedModel[4];

        public VehicleRender(RenderManager rm) { super(rm); shadowSize = 1.0f; }

        @Override protected ResourceLocation getEntityTexture(EntityRoadVehicle e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

        @Override
        public void doRender(EntityRoadVehicle e, double x, double y, double z, float yaw, float pt) {
            Minecraft mc = Minecraft.getMinecraft();
            int k = e.kind().ordinal();
            if (body[k] == null) {
                ModelManager mm = mc.getRenderItem().getItemModelMesher().getModelManager();
                body[k] = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":" + BODIES[k], "inventory"));
                paint[k] = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":" + BODIES[k] + "_paint", "inventory"));
            }
            int light = e.getBrightnessForRender();
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.color(1, 1, 1, 1);
            float ry = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
            float s = e.kind().scale;
            BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(180f - ry, 0, 1, 0);
            GlStateManager.scale(s, s, s);
            GlStateManager.translate(-0.5, 0, -0.5);                          // model px (8, 0, 8) = the vehicle's middle
            r.renderModelBrightnessColor(body[k], 1f, 1f, 1f, 1f);
            int c = e.colour();
            r.renderModelBrightnessColor(paint[k], 1f, (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f);
            // lamps, full-bright
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            int l = e.lights();
            boolean blink = mc.world != null && (mc.world.getTotalWorldTime() / 7) % 2 == 0;
            boolean head = (l & EntityRoadVehicle.L_HEAD) != 0, brake = (l & EntityRoadVehicle.L_BRAKE) != 0;
            Tessellator ts = Tessellator.getInstance();
            BufferBuilder b = ts.getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            float[][][] L = LAMPS[k];
            for (float[] h : L[0]) box(b, h, head ? 1f : 0.35f, head ? 0.97f : 0.34f, head ? 0.86f : 0.3f);
            for (float[] t : L[1]) { float m = brake ? 1f : head ? 0.6f : 0.28f; box(b, t, m, 0.04f * m, 0.03f * m); }
            for (int i = 0; i < 4; i++) {
                boolean on = blink && (i < 2 ? (l & EntityRoadVehicle.L_LEFT) != 0 : (l & EntityRoadVehicle.L_RIGHT) != 0);
                float m = on ? 1f : 0.3f;
                box(b, L[2][i], m, 0.55f * m, 0.08f * m);
            }
            ts.draw();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            super.doRender(e, x, y, z, yaw, pt);
        }

        /** a lamp box (model px), a hair proud of its lens, all six faces */
        private static void box(BufferBuilder b, float[] l, float r, float g, float bl) {
            float e = 0.03f;
            float x0 = (l[0] - e) / 16, y0 = (l[1] - e) / 16, z0 = (l[2] - e) / 16, x1 = (l[3] + e) / 16, y1 = (l[4] + e) / 16, z1 = (l[5] + e) / 16;
            float[][] q = {
                    {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1},
                    {x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
                    {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1}};
            for (float[] f : q) for (int i = 0; i < 4; i++) b.pos(f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(r, g, bl, 1f).endVertex();
        }
    }
}
