package com.dogpound.railmap.cctv.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.CctvConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * LIVE CCTV video: each wanted camera is rendered from its own position and angle into a small framebuffer - a real
 * second world render (EntityRenderer.renderWorld with an invisible view entity), so players, mobs and trains move on
 * the monitors. Budget: CctvConfig.liveBudget renders per frame, each feed at most liveFps, oldest first; only feeds a
 * monitor in range and in view asked for in the last half second. The render reuses the chunk meshes already built for
 * you (no rebuilds): vanilla's render-chunk grid is pinned to your position during the camera pass (so it doesn't
 * re-centre and rebuild), Celeritas / Sodium-style renderers take the camera as a normal camera move. The hand is not
 * drawn, the fog closes in at CctvConfig.cameraFogBlocks. Anything throwing in the pass switches live video off for the
 * session and every monitor falls back to the server's snapshot pictures.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class CctvFeeds {
    private CctvFeeds() {}

    static final class Feed { Framebuffer fb; long wantedAt, renderedAt; boolean ready; int look; }

    private static final Map<Integer, Feed> FEEDS = new HashMap<>();
    /** true during a camera pass (other renderers can skip work) */
    public static boolean rendering;
    private static float fov;
    private static int renderingCam;
    private static int look;
    public static boolean broken;
    public static String brokenWhy = "";
    private static EntityCctvView view;
    private static Field fFramebuffer, fFx, fFy, fFz, fFcx, fFcy, fFcz;
    private static int badFrames;
    private static final int BAD_LIMIT = 60;
    private static final java.nio.ByteBuffer PROBE = org.lwjgl.BufferUtils.createByteBuffer(4);

    /** how bad this pass was: 10 = it raised a framebuffer GL error, 1 = still pure black (the clear colour, nothing drawn),
     *  0 = a real picture. A camera honestly filming one flat colour is fine; only untouched black counts. */
    static int badFrame(Framebuffer fb) {
        boolean err = false;
        for (int e; (e = GL11.glGetError()) != GL11.GL_NO_ERROR; ) err |= e == 0x0506 || e == GL11.GL_INVALID_OPERATION;
        if (err) return 10;
        fb.bindFramebuffer(false);
        int w = fb.framebufferWidth, h = fb.framebufferHeight;
        int[][] pts = {{w / 2, h / 2}, {w / 5, h / 5}, {w * 4 / 5, h / 5}, {w / 5, h * 4 / 5}, {w * 4 / 5, h * 4 / 5}};
        for (int[] pt : pts) {
            PROBE.clear();
            GL11.glReadPixels(pt[0], pt[1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PROBE);
            if ((PROBE.getInt(0) & 0x00FFFFFF) != 0) return 0;
        }
        return 1;
    }

    /** a monitor wants this camera live: its texture id, or -1 (not rendered yet) */
    public static int texture(int cam, int lookMode) {
        if (broken || !CctvConfig.liveVideo || !OpenGlHelper.isFramebufferEnabled()) return -1;
        if (rendering) {                                     // a monitor seen by a camera: its last picture (never the one being drawn)
            Feed g = FEEDS.get(cam);
            return cam != renderingCam && g != null && g.ready ? g.fb.framebufferTexture : -1;
        }
        Feed f = FEEDS.computeIfAbsent(cam, k -> new Feed());
        f.wantedAt = System.currentTimeMillis();
        f.look = lookMode;
        return f.ready && f.fb != null ? f.fb.framebufferTexture : -1;
    }

    public static boolean usable() { return !broken && CctvConfig.liveVideo && OpenGlHelper.isFramebufferEnabled(); }

    public static int liveCount() { int n = 0; for (Feed f : FEEDS.values()) if (f.ready) n++; return n; }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || broken || !CctvConfig.liveVideo || !OpenGlHelper.isFramebufferEnabled()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, Feed>> it = FEEDS.entrySet().iterator(); it.hasNext(); ) {
            Feed f = it.next().getValue();
            if (now - f.wantedAt > 10000) { if (f.fb != null) f.fb.deleteFramebuffer(); it.remove(); }
        }
        long gap = 1000L / Math.max(1, CctvConfig.liveFps);
        for (int n = 0; n < CctvConfig.liveBudget; n++) {
            Integer pick = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<Integer, Feed> en : FEEDS.entrySet()) {
                Feed f = en.getValue();
                if (now - f.wantedAt > 500 || now - f.renderedAt < gap) continue;
                if (f.renderedAt < oldest) { oldest = f.renderedAt; pick = en.getKey(); }
            }
            if (pick == null) return;
            Feed f = FEEDS.get(pick);
            f.renderedAt = now;
            double[] p = CctvClient.livePose(pick, e.renderTickTime);
            if (p == null) continue;
            renderingCam = pick;
            render(f, p, e.renderTickTime);
        }
    }

    private static void reflect() {
        if (fFramebuffer != null) return;
        fFramebuffer = ReflectionHelper.findField(Minecraft.class, "framebuffer", "field_147124_at", "framebufferMc");
        fFx = ReflectionHelper.findField(RenderGlobal.class, "frustumUpdatePosX", "field_174992_B");
        fFy = ReflectionHelper.findField(RenderGlobal.class, "frustumUpdatePosY", "field_174993_C");
        fFz = ReflectionHelper.findField(RenderGlobal.class, "frustumUpdatePosZ", "field_174987_D");
        fFcx = ReflectionHelper.findField(RenderGlobal.class, "frustumUpdatePosChunkX", "field_174988_E");
        fFcy = ReflectionHelper.findField(RenderGlobal.class, "frustumUpdatePosChunkY", "field_174989_F");
        fFcz = ReflectionHelper.findField(RenderGlobal.class, "frustumUpdatePosChunkZ", "field_174990_G");
    }

    private static void render(Feed f, double[] p, float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        int res = CctvConfig.liveRes;
        if (f.fb == null || f.fb.framebufferWidth != res) {
            if (f.fb != null) f.fb.deleteFramebuffer();
            f.fb = new Framebuffer(res, res, true);
            f.fb.setFramebufferColor(0, 0, 0, 1);
            f.fb.setFramebufferFilter(GL11.GL_LINEAR);
        }
        if (view == null || view.world != mc.world) view = new EntityCctvView(mc.world);
        view.place(p[0], p[1], p[2], (float) p[3], (float) p[4]);
        Entity oldView = mc.getRenderViewEntity();
        RayTraceResult oldHit = mc.objectMouseOver;
        Entity oldPointed = mc.pointedEntity;
        int oldW = mc.displayWidth, oldH = mc.displayHeight, oldThird = mc.gameSettings.thirdPersonView;
        Framebuffer oldFb = null;
        Object[] frustum = null;
        try {
            reflect();
            oldFb = (Framebuffer) fFramebuffer.get(mc);
            RenderGlobal rg = mc.renderGlobal;
            frustum = new Object[]{fFx.get(rg), fFy.get(rg), fFz.get(rg), fFcx.get(rg), fFcy.get(rg), fFcz.get(rg)};
            // pin vanilla's render-chunk grid where it is: the camera pass must not re-centre it (that would rebuild chunks)
            fFx.setDouble(rg, view.posX); fFy.setDouble(rg, view.posY); fFz.setDouble(rg, view.posZ);
            fFcx.setInt(rg, view.chunkCoordX); fFcy.setInt(rg, view.chunkCoordY); fFcz.setInt(rg, view.chunkCoordZ);
            fov = (float) p[5];
            look = f.look;
            rendering = true;
            fFramebuffer.set(mc, f.fb);
            mc.displayWidth = res;
            mc.displayHeight = res;
            mc.gameSettings.thirdPersonView = 0;
            mc.setRenderViewEntity(view);
            f.fb.bindFramebuffer(true);
            GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
            while (GL11.glGetError() != GL11.GL_NO_ERROR) { }     // only count errors from this pass
            try {
                mc.entityRenderer.renderWorld(pt, System.nanoTime());
            } finally {
                GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix();
            }
            // Some packs (Celeritas + GL-debug HUD mods under Zink) break the pass WITHOUT throwing: the feed stays
            // black and the log fills with "1286 / Invalid framebuffer operation". Catch that silent failure too.
            // ponytail: 60 points = 6 GL-error passes or 60 untouched-black ones in a row; a pitch-dark scene with no light
            // at all can trip the black rule, so tune BAD_LIMIT if night cameras ever fall back on their own.
            int bad = badFrame(f.fb);
            if (bad == 0) { badFrames = 0; f.ready = true; }
            else if ((badFrames += bad) >= BAD_LIMIT) throw new IllegalStateException("camera pass draws nothing (" + (bad == 10 ? "GL framebuffer errors" : "frames stay black") + ")");
        } catch (Throwable t) {
            broken = true;
            brokenWhy = t.toString();
            RailMap.LOG.warn("[IR Extras] CCTV live video switched off for this session (snapshots from the server instead): {}", t.toString());
        } finally {
            rendering = false;
            mc.setRenderViewEntity(oldView == null ? mc.player : oldView);
            mc.objectMouseOver = oldHit;
            mc.pointedEntity = oldPointed;
            mc.displayWidth = oldW;
            mc.displayHeight = oldH;
            mc.gameSettings.thirdPersonView = oldThird;
            try {
                if (oldFb != null) fFramebuffer.set(mc, oldFb);
                if (frustum != null) {
                    RenderGlobal rg = mc.renderGlobal;
                    fFx.set(rg, frustum[0]); fFy.set(rg, frustum[1]); fFz.set(rg, frustum[2]); fFcx.set(rg, frustum[3]); fFcy.set(rg, frustum[4]); fFcz.set(rg, frustum[5]);
                }
            } catch (IllegalAccessException ignored) { }
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
            GlStateManager.enableDepth();
            GlStateManager.colorMask(true, true, true, true);
        }
    }

    // ---- the camera pass: lens, no hand, short fog, hot mask for thermal -----------------------------------------------
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(EntityViewRenderEvent.FOVModifier e) { if (rendering) e.setFOV(fov); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void camera(EntityViewRenderEvent.CameraSetup e) {
        if (!rendering || view == null) return;
        e.setYaw(view.rotationYaw + 180f);
        e.setPitch(view.rotationPitch);
        e.setRoll(0);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) { if (rendering) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fog(EntityViewRenderEvent.RenderFogEvent e) {
        if (!rendering) return;
        GlStateManager.setFogStart(CctvConfig.cameraFogBlocks * 0.45f);
        GlStateManager.setFogEnd(CctvConfig.cameraFogBlocks);
    }

    /** thermal: after the world is drawn, the alpha channel becomes the "hot" mask - living things only */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hotMask(RenderWorldLastEvent e) {
        if (!rendering || look != 2) return;
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.colorMask(false, false, false, true);
        GlStateManager.clearColor(0, 0, 0, 0);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.disableBlend();
        RenderHelper.enableStandardItemLighting();
        double r = CctvConfig.cameraFogBlocks;
        for (Entity en : mc.world.loadedEntityList) {
            if (!(en instanceof EntityLivingBase) || en.getDistanceSq(view) > r * r) continue;
            try { mc.getRenderManager().renderEntityStatic(en, e.getPartialTicks(), false); } catch (RuntimeException ignored) { }
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.clearColor(0, 0, 0, 1);
    }
}
