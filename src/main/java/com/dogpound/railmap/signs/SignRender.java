package com.dogpound.railmap.signs;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the live surfaces of Pride Rail cars: the orange dot-matrix destination boards outside (text steps along one
 * character at a time like a real LED board, pages flip between destination / next stop / train name) and the
 * screens inside - next-stop info, a clock, the speed readout, and looping GIF animations (your own GIFs from
 * config/irextras/screens/ or the built-in Pride ones).
 */
public class SignRender {
    private static final Map<Integer, PacketSigns.Entry> DATA = new HashMap<>();
    private static Map<String, List<float[]>> rects;          // defID -> [kind, x0, x1, y0, y1, p, nz, nx]
    private static final String[] KINDS = {"dest", "door", "aisle", "end", "speed"};

    public static void receive(PacketSigns p) {
        DATA.clear();
        for (PacketSigns.Entry e : p.entries) DATA.put(e.entityId, e);
    }

    private static Map<String, List<float[]>> rects() {
        if (rects != null) return rects;
        rects = new HashMap<>();
        try (InputStreamReader r = new InputStreamReader(SignRender.class.getResourceAsStream("/assets/irextras/pride_signs.json"), StandardCharsets.UTF_8)) {
            JsonObject o = new JsonParser().parse(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                List<float[]> l = new ArrayList<>();
                for (JsonElement je : (JsonArray) e.getValue()) {
                    JsonObject s = je.getAsJsonObject();
                    int k = java.util.Arrays.asList(KINDS).indexOf(s.get("kind").getAsString());
                    l.add(new float[]{k, s.get("x0").getAsFloat(), s.get("x1").getAsFloat(), s.get("y0").getAsFloat(), s.get("y1").getAsFloat(),
                            s.get("p").getAsFloat(), s.get("nz").getAsFloat(), s.get("nx").getAsFloat()});
                }
                rects.put(e.getKey(), l);
            }
        } catch (Exception ex) {
            com.dogpound.railmap.RailMap.LOG.warn("[IR Extras] no sign layout: {}", ex.toString());
        }
        return rects;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        Map<String, List<float[]>> layout = rects();
        if (layout.isEmpty()) return;
        float pt = e.getPartialTicks();
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        List<EntityRollingStock> stock;
        try {
            stock = cam72cam.mod.world.World.get(mc.world).getEntities(EntityRollingStock.class);
        } catch (RuntimeException ex) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        try {
            for (EntityRollingStock s : stock) {
                List<float[]> l = layout.get(s.getDefinitionID());
                if (l == null) continue;
                Entity in = s.internal;
                double x = in.lastTickPosX + (in.posX - in.lastTickPosX) * pt;
                double y = in.lastTickPosY + (in.posY - in.lastTickPosY) * pt;
                double z = in.lastTickPosZ + (in.posZ - in.lastTickPosZ) * pt;
                if ((x - vx) * (x - vx) + (z - vz) * (z - vz) > 96 * 96) continue;
                PacketSigns.Entry d = DATA.get(in.getEntityId());
                double kmh = Math.sqrt((in.posX - in.lastTickPosX) * (in.posX - in.lastTickPosX) + (in.posZ - in.lastTickPosZ) * (in.posZ - in.lastTickPosZ)) * 72.0;
                if (s instanceof EntityMoveableRollingStock m && m.getCurrentSpeed() != null) kmh = Math.abs(m.getCurrentSpeed().metric());
                GlStateManager.pushMatrix();
                GlStateManager.translate(x - vx, y - vy, z - vz);
                GlStateManager.rotate(180f - s.getRotationYaw(), 0, 1, 0);
                GlStateManager.rotate(s.getRotationPitch(), 1, 0, 0);
                GlStateManager.rotate(-90f, 0, 1, 0);
                float sc = (float) s.gauge.scale();
                GlStateManager.scale(sc, sc, sc);
                // the camera in the car's own model space (inverse of the model rotation, pitch ignored)
                double th = Math.toRadians(90.0 - s.getRotationYaw());
                double dx = (vx - x) / sc, dz = (vz - z) / sc;
                double lx = dx * Math.cos(-th) + dz * Math.sin(-th), lz = -dx * Math.sin(-th) + dz * Math.cos(-th);
                for (float[] r : l) {
                    // only draw a sign from the side it faces (no mirror-image text through the body from inside)
                    float p = r[5], nz = r[6], nx = r[7];
                    if (nz > 0 && lz < p || nz < 0 && lz > p || nx > 0 && lx < p || nx < 0 && lx > p) continue;
                    draw(mc.fontRenderer, r, d, kmh, s.getUUID().hashCode());
                }
                GlStateManager.popMatrix();
            }
        } finally {
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    /** move into the rect's plane: local +x runs along the rect (left->right as seen from outside), +y up */
    private static void face(float[] r) {
        float x0 = r[1], x1 = r[2], y1 = r[4], p = r[5], nz = r[6], nx = r[7];
        if (nz > 0) { GlStateManager.translate(x0, y1, p); }
        else if (nz < 0) { GlStateManager.translate(x1, y1, p); GlStateManager.rotate(180, 0, 1, 0); }
        else if (nx > 0) { GlStateManager.translate(p, y1, x1); GlStateManager.rotate(90, 0, 1, 0); }
        else { GlStateManager.translate(p, y1, x0); GlStateManager.rotate(-90, 0, 1, 0); }
    }

    private static void draw(FontRenderer f, float[] r, PacketSigns.Entry d, double kmh, int seed) {
        int kind = (int) r[0];
        float w = r[2] - r[1], h = r[4] - r[3];
        long now = System.currentTimeMillis();
        GlStateManager.pushMatrix();
        face(r);
        if (kind == 0) {                                      // outside LED destination board
            if (d == null || d.mode == 2 || d.dest.isEmpty()) { GlStateManager.popMatrix(); return; }
            String[] pages = d.next.isEmpty() ? new String[]{d.dest, d.title} : new String[]{d.dest, "Next: " + d.next, d.title};
            String txt = pages[(int) ((now / 5000 + (seed & 3)) % pages.length)];
            led(f, txt, w, h, 0xFF000000 | d.color, now);
        } else if (kind == 4) {                               // speed readout
            String sp = String.format(java.util.Locale.ROOT, "%3d km/h", Math.round(kmh));
            led(f, sp, w, h, 0xFFFF3B2F, 0);
        } else {                                              // inside screens: pages of info + GIF
            int page = (int) ((now / 6000 + kind + (seed & 7)) % 3);
            boolean gif = page == 2 && ScreenGifs.any();
            if (gif) {
                ScreenGifs.draw(w, h, now, seed);
            } else if (d != null && !d.next.isEmpty() && page == 0) {
                text(f, "Next stop", w, h * 0.38f, 0, 0xFFB0B8C8);
                text(f, d.next, w, h * 0.55f, h * 0.42f, 0xFFFFFFFF);
            } else {
                String clock = new SimpleDateFormat("HH:mm").format(new Date());
                String top = d == null || d.line.isEmpty() ? "Pride Rail" : d.line;
                text(f, top, w, h * 0.42f, 0, d == null ? 0xFFFFD040 : 0xFF000000 | d.color);
                text(f, (d == null || d.title.isEmpty() ? "" : d.title + "  ") + clock, w, h * 0.38f, h * 0.5f, 0xFFE0E0E0);
            }
        }
        GlStateManager.popMatrix();
    }

    /** dot-matrix LED text: fixed height, steps one character at a time when too long */
    private static void led(FontRenderer f, String txt, float w, float h, int argb, long now) {
        float k = h / 9f;
        int fit = Math.max(1, (int) (w / (6 * k)));
        String shown = txt;
        if (txt.length() > fit) {
            String loop = txt + "     ";
            int off = (int) ((now / 260) % loop.length());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fit; i++) sb.append(loop.charAt((off + i) % loop.length()));
            shown = sb.toString();
        }
        GlStateManager.translate(0, 0, 0.002f);
        GlStateManager.scale(k, -k, k);
        int tw = f.getStringWidth(shown);
        float pad = Math.max(0, (w / k - tw) / 2f);
        f.drawString(shown, (int) pad, 1, argb, false);
    }

    private static void text(FontRenderer f, String s, float w, float lineH, float dy, int argb) {
        GlStateManager.pushMatrix();
        float k = lineH / 9f;
        GlStateManager.translate(0.02f, -dy, 0.002f);
        GlStateManager.scale(k, -k, k);
        String t = f.trimStringToWidth(s, (int) ((w - 0.04f) / k));
        f.drawString(t, 0, 0, argb, false);
        GlStateManager.popMatrix();
    }

    /** a flat coloured quad in the current rect plane (used by the GIF player) */
    static void quad(float x0, float y0, float x1, float y1) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x0, -y1, 0.003).tex(0, 1).endVertex();
        b.pos(x1, -y1, 0.003).tex(1, 1).endVertex();
        b.pos(x1, -y0, 0.003).tex(1, 0).endVertex();
        b.pos(x0, -y0, 0.003).tex(0, 0).endVertex();
        t.draw();
    }
}
