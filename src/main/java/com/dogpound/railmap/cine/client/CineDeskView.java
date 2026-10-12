package com.dogpound.railmap.cine.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.client.CctvFeeds;
import com.dogpound.railmap.cine.*;
import cam72cam.immersiverailroading.entity.Locomotive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * The director desk's live monitors and the tripods' moving heads. Each desk's preview and program are fed through the
 * CCTV live-video renderer (CctvFeeds) as pseudo camera numbers -1000 - 2 x slot (- 1 for program); CctvClient.livePose
 * asks feedPose here where such a "camera" is. The tripod heads pan and tilt after the nearest train they can see.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CineDeskView {
    private CineDeskView() {}

    /** monitor glass in the desk's north-facing model (px): x0, y0, x1, y1, z */
    public static final double[] PGM = {1.0, 15.0, 15.0, 24.0, 9.3}, PVW = {18.0, 16.0, 30.0, 23.5, 9.3};
    /** tripod head pivot (px) */
    static final double HX = 8, HY = 22, HZ = 8;
    private static final List<BlockPos> SLOTS = new ArrayList<>();

    static int slot(BlockPos p) {
        int i = SLOTS.indexOf(p);
        if (i < 0) { SLOTS.add(p); i = SLOTS.size() - 1; }
        return i;
    }

    /** CctvClient.livePose hook: {x, y, z, yaw, pitch, fov} of a desk monitor's feed, or null */
    public static double[] feedPose(int no, float pt) {
        int k = -1000 - no, slot = k / 2, which = k % 2;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || slot < 0 || slot >= SLOTS.size()) return null;
        if (!(mc.world.getTileEntity(SLOTS.get(slot)) instanceof TileDirectorDesk d) || !d.live()) return null;
        double[] p = sourcePose(d, which == 1 ? d.program : d.preview, "desk" + slot + "_" + which, pt);
        return p == null ? null : new double[]{p[0], p[1], p[2], p[3], p[4], p[5]};
    }

    public static double[] sourcePose(TileDirectorDesk d, int src, String key, float pt) {
        double[] subject = CinePoses.live(d.target, pt);
        return CinePoses.pose(key, src, subject, d.target, src == CineMath.AUTO ? d.autoCam : -1, d.fov(), pt);
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        try {
            if (CineCam.active && CineCam.mode == CineMath.REPLAY && !CctvFeeds.rendering) CineReplay.render(cx, cy, cz);
            for (TileEntity t : mc.world.loadedTileEntityList) {
                if (t.isInvalid()) continue;
                BlockPos p = t.getPos();
                double x = p.getX() - cx, y = p.getY() - cy, z = p.getZ() - cz, d2 = x * x + y * y + z * z;
                if (t instanceof TileDirectorDesk d && d2 < 28 * 28 && d.live() && !CctvFeeds.rendering) monitors(d, x, y, z);
                else if (t instanceof TileCamTripod c && d2 < 64 * 64) head(mc, c, x, y, z, pt);
            }
        } catch (RuntimeException ex) {
            RailMap.LOG.debug("[IR Extras] director desk render: {}", ex.toString());
        }
    }

    static int turns(EnumFacing f) { return f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3; }

    static void monitors(TileDirectorDesk d, double x, double y, double z) {
        int s = slot(d.getPos());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns(d.controlFacing()), 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        glass(PVW, CctvFeeds.texture(-1000 - 2 * s, 0), 0);
        glass(PGM, CctvFeeds.texture(-1001 - 2 * s, 0), d.letterbox());
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** one monitor: the live picture (or dark), cropped to the glass's shape, letterboxed if asked */
    static void glass(double[] g, int tex, int lb) {
        Tessellator ts = Tessellator.getInstance();
        BufferBuilder b = ts.getBuffer();
        double x0 = g[0], y0 = g[1], x1 = g[2], y1 = g[3], zf = g[4] - 0.03;
        double aspect = (x1 - x0) / (y1 - y0), vs = Math.min(1, 1 / aspect), v0 = 0.5 - vs / 2, v1 = 0.5 + vs / 2;
        if (tex >= 0) {
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(tex);
            GlStateManager.color(1, 1, 1, 1);
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            b.pos(x1, y0, zf).tex(0, v0).endVertex();                         // viewer's left = high x on a north face
            b.pos(x1, y1, zf).tex(0, v1).endVertex();
            b.pos(x0, y1, zf).tex(1, v1).endVertex();
            b.pos(x0, y0, zf).tex(1, v0).endVertex();
            ts.draw();
        }
        GlStateManager.disableTexture2D();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        if (tex < 0) quad(b, x0, y0, x1, y1, zf, 0x0a0c10);
        double ratio = CineMath.LETTERBOX[Math.max(0, Math.min(3, lb))];
        if (ratio > 0 && aspect < ratio) {
            double bar = ((y1 - y0) - (x1 - x0) / ratio) / 2;
            quad(b, x0, y1 - bar, x1, y1, zf - 0.01, 0x000000);
            quad(b, x0, y0, x1, y0 + bar, zf - 0.01, 0x000000);
        }
        ts.draw();
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    static void quad(BufferBuilder b, double x0, double y0, double x1, double y1, double z, int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        b.pos(x0, y0, z).color(r, g, bl, 255).endVertex();
        b.pos(x0, y1, z).color(r, g, bl, 255).endVertex();
        b.pos(x1, y1, z).color(r, g, bl, 255).endVertex();
        b.pos(x1, y0, z).color(r, g, bl, 255).endVertex();
    }

    // ---- tripod heads -------------------------------------------------------------------------------------------------------
    static void head(Minecraft mc, TileCamTripod c, double x, double y, double z, float pt) {
        float yaw = c.controlFacing().getOpposite().getHorizontalAngle(), pitch = 0;
        if (c.powered()) {
            Vec3d l = c.lens();
            List<Locomotive> near = CineTrains.near(mc.world, l.x, l.z, CineConfig.tripodRange);
            for (Locomotive loco : near) {
                double[] tp = CineTrains.pose(loco, pt);
                if (tp == null || !CinePoses.sees(l.x, l.y, l.z, tp[0], tp[1] + 1.5, tp[2])) continue;
                CinePoses.St s = CinePoses.st("tri" + c.getPos().toLong());
                CinePoses.smoothLook(s, tp[0], tp[1] + 1.6, tp[2], 0.05, 6);
                double[] lk = CineMath.look(l.x, l.y, l.z, s.lx, s.ly, s.lz);
                yaw = (float) lk[0]; pitch = (float) lk[1];
                break;
            }
        }
        int light = mc.world.getCombinedLight(c.getPos().up(), 0);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + HX / 16, y + HY / 16, z + HZ / 16);
        GlStateManager.rotate(180f - yaw, 0, 1, 0);
        GlStateManager.rotate(-pitch, 1, 0, 0);
        GlStateManager.translate(-HX / 16, -HY / 16, -HZ / 16);
        mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(
                com.dogpound.railmap.ride.client.RideClient.baked("camera_tripod_head"), 1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        CineCam.registerKey();
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(CineRegistry.tripod), 0, new ModelResourceLocation(CineRegistry.tripod.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(CineRegistry.desk), 0, new ModelResourceLocation(CineRegistry.desk.getRegistryName(), "inventory"));
        ModelBakery.registerItemVariants(Item.getItemFromBlock(CineRegistry.tripod), new ResourceLocation(RailMap.MODID, "camera_tripod"), new ResourceLocation(RailMap.MODID, "camera_tripod_head"));
    }
}
