package com.dogpound.railmap.ems.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.ems.Ems;
import com.dogpound.railmap.ems.EntityEmergencyVehicle;
import com.dogpound.railmap.ems.TileEmergencyStation;
import com.dogpound.railmap.shop.ItemDrink;
import com.dogpound.railmap.shop.TileCoffeeKiosk;
import com.dogpound.railmap.shop.TileNewsstand;
import com.dogpound.railmap.shop.TileTicketOffice;
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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * Client side of the station shops and emergency services: the coffee cups' item models, the shop / station desks'
 * shelves, and the vehicles' renderer - the bbkit body (modelled at half size, drawn twice as big) with its light bar,
 * beacons and grille lamps flashing full-bright in the service's colours while it responds or works the scene, and the
 * headlights on.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class EmsClient {
    private EmsClient() {}

    static final String[] BODIES = {"ems_fire_engine", "ems_ambulance", "ems_police_car"};

    /** ClientProxy.preInit */
    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityEmergencyVehicle.class, VehicleRender::new);
        for (Class<? extends com.dogpound.railmap.block.TileFurniture> c : java.util.Arrays.asList(TileNewsstand.class, TileCoffeeKiosk.class, TileTicketOffice.class,
                TileEmergencyStation.Fire.class, TileEmergencyStation.Ambulance.class, TileEmergencyStation.Police.class))
            ClientRegistry.bindTileEntitySpecialRenderer(c, new com.dogpound.railmap.client.render.ShelfRenderer());
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        if (ItemDrink.INSTANCE == null) return;
        for (int i = 0; i < ItemDrink.NAMES.length; i++)
            ModelLoader.setCustomModelResourceLocation(ItemDrink.INSTANCE, i, new ModelResourceLocation(RailMap.MODID + ":coffee_" + ItemDrink.NAMES[i], "inventory"));
        ResourceLocation[] v = new ResourceLocation[ItemDrink.NAMES.length + BODIES.length];
        for (int i = 0; i < ItemDrink.NAMES.length; i++) v[i] = new ResourceLocation(RailMap.MODID, "coffee_" + ItemDrink.NAMES[i]);
        for (int i = 0; i < BODIES.length; i++) v[ItemDrink.NAMES.length + i] = new ResourceLocation(RailMap.MODID, BODIES[i]);   // vehicle bodies ride along as item variants
        ModelBakery.registerItemVariants(ItemDrink.INSTANCE, v);
    }

    public static final class VehicleRender extends Render<EntityEmergencyVehicle> {
        /** lamp boxes in model px (half scale, front = -z): {x0, y0, z0, x1, y1, z1, colour set, phase} - tools/gen_bb_shops.py LIGHTS */
        static final float[][][] LIGHTS = {
                {   // fire engine: cab light bar, cab-corner beacons, grille strobes, rear beacons
                        {3.0f, 18.2f, -10.4f, 7.6f, 19.0f, -8.8f, 0, 0}, {8.4f, 18.2f, -10.4f, 13.0f, 19.0f, -8.8f, 0, 1},
                        {0.4f, 16.8f, -11.6f, 1.4f, 17.8f, -10.8f, 0, 1}, {14.6f, 16.8f, -11.6f, 15.6f, 17.8f, -10.8f, 0, 0},
                        {4.4f, 5.0f, -12.12f, 5.6f, 5.8f, -12.0f, 0, 0}, {10.4f, 5.0f, -12.12f, 11.6f, 5.8f, -12.0f, 0, 1},
                        {0.4f, 20.2f, 26.4f, 1.6f, 21.4f, 27.6f, 0, 0}, {14.4f, 20.2f, 26.4f, 15.6f, 21.4f, 27.6f, 0, 1}},
                {   // ambulance: cab light bar, grille lamps, body-top beacons front + rear
                        {4.0f, 14.0f, -4.2f, 7.8f, 14.8f, -3.0f, 1, 0}, {8.2f, 14.0f, -4.2f, 12.0f, 14.8f, -3.0f, 1, 1},
                        {3.0f, 6.4f, -10.12f, 4.2f, 7.0f, -10.0f, 1, 1}, {11.8f, 6.4f, -10.12f, 13.0f, 7.0f, -10.0f, 1, 0},
                        {0.6f, 19.0f, 0.4f, 1.8f, 20.0f, 1.6f, 1, 0}, {14.2f, 19.0f, 0.4f, 15.4f, 20.0f, 1.6f, 1, 1},
                        {0.6f, 19.0f, 24.4f, 1.8f, 20.0f, 25.6f, 1, 1}, {14.2f, 19.0f, 24.4f, 15.4f, 20.0f, 25.6f, 1, 0}},
                {   // police car: roof bar (blue / blue / white takedown), grille, rear deck
                        {3.6f, 12.2f, 6.4f, 7.4f, 13.0f, 7.6f, 2, 0}, {8.6f, 12.2f, 6.4f, 12.4f, 13.0f, 7.6f, 2, 1},
                        {7.4f, 12.2f, 6.4f, 8.6f, 13.0f, 7.6f, 3, 0},
                        {4.6f, 4.6f, -6.12f, 5.8f, 5.2f, -6.0f, 2, 1}, {10.2f, 4.6f, -6.12f, 11.4f, 5.2f, -6.0f, 2, 0},
                        {4.0f, 8.0f, 14.6f, 5.6f, 8.6f, 15.0f, 2, 0}, {10.4f, 8.0f, 14.6f, 12.0f, 8.6f, 15.0f, 2, 1}}};
        /** colour sets: {on A rgb, on B rgb} */
        static final float[][] COLS = {{1f, 0.08f, 0.05f, 1f, 0.55f, 0.1f}, {0.15f, 0.35f, 1f, 1f, 0.1f, 0.08f}, {0.12f, 0.3f, 1f, 0.12f, 0.3f, 1f}, {1f, 1f, 1f, 1f, 1f, 1f}};
        /** headlights per kind {x0,y0,z0,x1,y1,z1} pairs */
        static final float[][] HEAD = {{1.6f, 6.8f, -12.12f, 3.6f, 8.0f, -12.0f, 12.4f, 6.8f, -12.12f, 14.4f, 8.0f, -12.0f},
                {1.0f, 7.2f, -10.12f, 2.8f, 8.2f, -10.0f, 13.2f, 7.2f, -10.12f, 15.0f, 8.2f, -10.0f},
                {2.0f, 5.4f, -6.12f, 4.0f, 6.4f, -6.0f, 12.0f, 5.4f, -6.12f, 14.0f, 6.4f, -6.0f}};
        static final float SCALE = 2f;
        private final IBakedModel[] body = new IBakedModel[3];

        public VehicleRender(RenderManager rm) { super(rm); shadowSize = 1.2f; }

        @Override protected ResourceLocation getEntityTexture(EntityEmergencyVehicle e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

        @Override
        public void doRender(EntityEmergencyVehicle e, double x, double y, double z, float yaw, float pt) {
            Minecraft mc = Minecraft.getMinecraft();
            int k = e.kind().ordinal();
            if (body[k] == null) {
                ModelManager mm = mc.getRenderItem().getItemModelMesher().getModelManager();
                body[k] = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":" + BODIES[k], "inventory"));
            }
            int light = e.getBrightnessForRender();
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.color(1, 1, 1, 1);
            float ry = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
            BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(180f - ry, 0, 1, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);
            GlStateManager.translate(-0.5, 0, -0.5);                     // model px (8, 0, 8) = the vehicle's middle
            r.renderModelBrightnessColor(body[k], 1f, 1f, 1f, 1f);
            // lamps, full-bright
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            long t = mc.world == null ? 0 : mc.world.getTotalWorldTime();
            boolean flash = e.lights();
            Tessellator ts = Tessellator.getInstance();
            BufferBuilder b = ts.getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (float[] l : LIGHTS[k]) {
                float[] c = COLS[(int) l[6]];
                boolean phaseA = ((t / 4) + (int) l[7]) % 2 == 0;
                boolean on = flash && (((t / 2) % 4 != 3) || l[6] == 3);
                float m = on ? 1f : 0.28f;
                int o = phaseA ? 0 : 3;
                box(b, l, c[o] * m, c[o + 1] * m, c[o + 2] * m);
            }
            float[] h = HEAD[k];
            for (int i = 0; i < 2; i++) box(b, new float[]{h[i * 6], h[i * 6 + 1], h[i * 6 + 2], h[i * 6 + 3], h[i * 6 + 4], h[i * 6 + 5]}, 1f, 0.97f, 0.85f);
            ts.draw();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            super.doRender(e, x, y, z, yaw, pt);
        }

        /** a lamp box (model px) with all six faces */
        private static void box(BufferBuilder b, float[] l, float r, float g, float bl) {
            float x0 = l[0] / 16, y0 = l[1] / 16, z0 = l[2] / 16, x1 = l[3] / 16, y1 = l[4] / 16, z1 = l[5] / 16;
            float[][] q = {
                    {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1},
                    {x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
                    {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1}};
            for (float[] f : q) for (int i = 0; i < 4; i++) b.pos(f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(r, g, bl, 1f).endVertex();
        }

        static { if (Ems.Kind.values().length != BODIES.length) throw new IllegalStateException("one body per service"); }
    }
}
