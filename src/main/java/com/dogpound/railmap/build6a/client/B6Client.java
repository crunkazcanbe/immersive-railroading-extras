package com.dogpound.railmap.build6a.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.build6a.B6Blocks;
import com.dogpound.railmap.build6a.B6Net;
import com.dogpound.railmap.build6a.EntityHiRail;
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
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/** batch 6a client: the hi-rail truck's model (bbkit body + guide-wheel gear drawn at its live height) and cab panel */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class B6Client {
    private B6Client() {}

    /** ClientProxy.preInit */
    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityHiRail.class, TruckRender::new);
        B6Net.openCab = e -> Minecraft.getMinecraft().displayGuiScreen(new GuiHiRailCab(e));
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        if (B6Blocks.hiRail == null) return;
        ModelLoader.setCustomModelResourceLocation(B6Blocks.hiRail, 0, new ModelResourceLocation(RailMap.MODID + ":hirail_truck", "inventory"));
        ModelBakery.registerItemVariants(B6Blocks.hiRail, new ResourceLocation(RailMap.MODID, "hirail_truck"), new ResourceLocation(RailMap.MODID, "hirail_guides"));
    }

    /** driving: right-click (empty hand or an item) opens the cab panel - sneaking would get you out */
    @SubscribeEvent
    public static void cab(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickEmpty e) { openIfDriving(e.getEntityPlayer()); }

    @SubscribeEvent
    public static void cabItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem e) { if (e.getWorld().isRemote) openIfDriving(e.getEntityPlayer()); }

    static void openIfDriving(net.minecraft.entity.player.EntityPlayer p) {
        if (p != null && p.getRidingEntity() instanceof EntityHiRail t && Minecraft.getMinecraft().currentScreen == null) B6Net.openCab.accept(t);
    }

    public static final class TruckRender extends Render<EntityHiRail> {
        static final float SCALE = 2.5f;
        private IBakedModel body, guides;

        public TruckRender(RenderManager rm) { super(rm); shadowSize = 1.2f; }

        @Override protected ResourceLocation getEntityTexture(EntityHiRail e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }

        @Override
        public void doRender(EntityHiRail e, double x, double y, double z, float yaw, float pt) {
            Minecraft mc = Minecraft.getMinecraft();
            if (body == null) {
                ModelManager mm = mc.getRenderItem().getItemModelMesher().getModelManager();
                body = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":hirail_truck", "inventory"));
                guides = mm.getModel(new ModelResourceLocation(RailMap.MODID + ":hirail_guides", "inventory"));
            }
            int light = e.getBrightnessForRender();
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.color(1, 1, 1, 1);
            float ry = e.prevRotationYaw + net.minecraft.util.math.MathHelper.wrapDegrees(e.rotationYaw - e.prevRotationYaw) * pt;
            BlockModelRenderer r = mc.getBlockRendererDispatcher().getBlockModelRenderer();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(180f - ry, 0, 1, 0);
            GlStateManager.scale(SCALE, SCALE, SCALE);
            GlStateManager.translate(-0.5, 0, -0.5);                          // model px (8, 0, 8) = the truck's middle
            r.renderModelBrightnessColor(body, 1f, 1f, 1f, 1f);
            float up = (100 - e.gear()) / 100f * 3.2f / 16f;                  // guide wheels: 3.2 px of hydraulic travel
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, up, 0);
            r.renderModelBrightnessColor(guides, 1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
            // beacons + lamps, full-bright
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            long t = mc.world == null ? 0 : mc.world.getTotalWorldTime();
            boolean bea = e.has(EntityHiRail.F_BEACON), flash = (t / 4) % 2 == 0, work = e.has(EntityHiRail.F_WORK), head = e.has(EntityHiRail.F_IGN) && (e.has(EntityHiRail.F_HEAD) || work);
            Tessellator ts = Tessellator.getInstance();
            BufferBuilder b = ts.getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (float[] l : LAMPS_BEACON) { float m = bea && ((l[0] < 8) == flash) ? 1f : 0.3f; box(b, l, m, 0.55f * m, 0.05f * m); }
            for (float[] l : LAMPS_HEAD) box(b, l, head ? 1f : 0.35f, head ? 0.97f : 0.34f, head ? 0.86f : 0.3f);
            for (float[] l : LAMPS_WORK) { float m = work ? 1f : 0.3f; box(b, l, m, m, m * 0.92f); }
            for (float[] l : LAMPS_TAIL) { float m = e.has(EntityHiRail.F_IGN) ? (Math.abs(e.speed) < 0.002 || e.kmh() < 0.5 ? 1f : 0.6f) : 0.28f; box(b, l, m, 0.04f * m, 0.03f * m); }
            ts.draw();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
            super.doRender(e, x, y, z, yaw, pt);
        }

        /** model px boxes: written by tools/gen_bb_batch6a.py (HIRAIL_LAMPS) */
        static final float[][] LAMPS_BEACON = {{4.0f, 18.6f, -5.4f, 5.6f, 19.6f, -4.2f}, {10.4f, 18.6f, -5.4f, 12.0f, 19.6f, -4.2f}};
        static final float[][] LAMPS_HEAD = {{1.2f, 5.4f, -15.32f, 3.6f, 6.8f, -15.2f}, {12.4f, 5.4f, -15.32f, 14.8f, 6.8f, -15.2f}};
        static final float[][] LAMPS_WORK = {{3.0f, 17.6f, -6.42f, 5.0f, 18.4f, -6.3f}, {11.0f, 17.6f, -6.42f, 13.0f, 18.4f, -6.3f}, {6.8f, 10.4f, 31.8f, 9.2f, 11.4f, 31.92f}};
        static final float[][] LAMPS_TAIL = {{0.8f, 3.2f, 31.8f, 2.6f, 4.6f, 31.92f}, {13.4f, 3.2f, 31.8f, 15.2f, 4.6f, 31.92f}};

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
