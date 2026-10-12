package com.dogpound.railmap.freight6.client;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.client.render.Prims;
import com.dogpound.railmap.freight6.HolidayData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * Holiday trains, drawn on the IR cars in their own frame (z along the car, x across, y up from the rail):
 * Christmas - multicolour bulbs chasing along both roof edges and across the ends, a garland under them, a wreath with a
 * red bow on each end; ghost train - orange / purple bulbs, jack-o'-lanterns along the roof, cobwebs in the corners and,
 * at night, ghosts circling the car (with the odd moan).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class HolidayTrains {
    private HolidayTrains() {}

    static final int[] XMAS = {0xE02020, 0x20C040, 0xFFC830, 0x3070FF, 0xFFF4E0}, GHOST = {0xFF7A10, 0x9A30E0, 0xFF7A10, 0x60E040};

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) { F6Client.KITS.clear(); return; }
        if (System.currentTimeMillis() - F6Client.kitsAt > 10000) F6Client.KITS.clear();
        if (F6Client.KITS.isEmpty() || mc.world.isDaytime() || mc.world.rand.nextInt(400) != 0) return;
        for (Integer id : F6Client.KITS.keySet())                                                   // a ghost train's moan, now and then
            if (F6Client.KITS.get(id) == HolidayData.GHOST) {
                Entity en = mc.world.getEntityByID(id);
                if (en != null && en.getDistanceSq(mc.player) < 40 * 40) { mc.world.playSound(en.posX, en.posY + 2, en.posZ, net.minecraft.init.SoundEvents.ENTITY_GHAST_AMBIENT, net.minecraft.util.SoundCategory.AMBIENT, 0.35f, 0.5f, false); break; }
            }
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || F6Client.KITS.isEmpty()) return;
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;
        float pt = e.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt, vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt, vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        List<EntityRollingStock> cars = new ArrayList<>();
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(mc.world).getEntities(EntityRollingStock.class))
                if (F6Client.KITS.containsKey(s.internal.getEntityId()) && s.internal.getDistanceSq(vx, vy, vz) < 128 * 128) cars.add(s);
        } catch (RuntimeException | LinkageError ex) { return; }
        if (cars.isEmpty()) return;
        cars.sort((a, b) -> Double.compare(a.internal.getDistanceSq(vx, vy, vz), b.internal.getDistanceSq(vx, vy, vz)));
        long t = mc.world.getTotalWorldTime();
        boolean night = !mc.world.isDaytime();
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        Prims.begin();
        try {
            for (int i = 0; i < Math.min(24, cars.size()); i++) {
                EntityRollingStock s = cars.get(i);
                Entity in = s.internal;
                double x = in.lastTickPosX + (in.posX - in.lastTickPosX) * pt, y = in.lastTickPosY + (in.posY - in.lastTickPosY) * pt, z = in.lastTickPosZ + (in.posZ - in.lastTickPosZ) * pt;
                double L = s.getDefinition().getLength(s.gauge), W = s.getDefinition().getWidth(s.gauge), H = s.getDefinition().getHeight(s.gauge);
                GlStateManager.pushMatrix();
                GlStateManager.translate(x - vx, y - vy, z - vz);
                GlStateManager.rotate(-s.getRotationYaw(), 0, 1, 0);
                if (F6Client.KITS.get(in.getEntityId()) == HolidayData.XMAS) christmas(L, W, H, t, i);
                else ghost(L, W, H, t, night, i);
                GlStateManager.popMatrix();
            }
        } catch (RuntimeException | LinkageError ex) {
            RailMap.LOG.debug("[IR Extras] holiday train render: {}", ex.toString());
        } finally {
            Prims.end();
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    static int chase(int[] pal, int k, long t) {
        int c = pal[Math.floorMod(k, pal.length)];
        return (k + t / 5) % 5 == 0 ? Prims.shade(c, 0.3f) : c;
    }

    static void bulb(double x, double y, double z, int c) { Prims.box(x - 0.045, y - 0.05, z - 0.045, x + 0.045, y + 0.05, z + 0.045, c); }

    /** bulbs along both roof edges and across both ends, at roof height h */
    static void strings(double L, double W, double h, int[] pal, long t, int seed) {
        double hw = W / 2 - 0.04, hl = L / 2 - 0.1;
        int k = seed;
        for (double zz = -hl; zz <= hl; zz += 0.45, k++) { bulb(hw, h, zz, chase(pal, k, t)); bulb(-hw, h, zz, chase(pal, k + 2, t)); }
        for (double xx = -hw; xx <= hw; xx += 0.4, k++) { bulb(xx, h, hl + 0.06, chase(pal, k, t)); bulb(xx, h, -hl - 0.06, chase(pal, k + 1, t)); }
        // a drop line down each end corner
        for (double yy = h - 0.4; yy > h * 0.35; yy -= 0.45, k++) for (double sx : new double[]{hw, -hw}) { bulb(sx, yy, hl + 0.06, chase(pal, k, t)); bulb(sx, yy, -hl - 0.06, chase(pal, k + 3, t)); }
    }

    static void christmas(double L, double W, double H, long t, int seed) {
        double hw = W / 2 - 0.02, hl = L / 2 - 0.1, h = H - 0.04;
        for (double zz = -hl; zz < hl; zz += 0.3) {                                          // garland: alternating greens under the bulbs
            int g = ((int) Math.floor(zz / 0.3) & 1) == 0 ? 0x1E5A2A : 0x2E7A38;
            Prims.box(hw - 0.06, h - 0.16, zz, hw + 0.06, h - 0.06, zz + 0.3, g);
            Prims.box(-hw - 0.06, h - 0.16, zz, -hw + 0.06, h - 0.06, zz + 0.3, g);
        }
        strings(L, W, h, XMAS, t, seed);
        for (int end = -1; end <= 1; end += 2) {                                            // a wreath with a red bow on each end
            double ez = end * (L / 2 + 0.03), cy = H * 0.62;
            for (int a = 0; a < 12; a++) {
                double ang = a * Math.PI / 6, rx = Math.cos(ang) * 0.32, ry = Math.sin(ang) * 0.32;
                Prims.box(rx - 0.09, cy + ry - 0.09, ez - 0.05, rx + 0.09, cy + ry + 0.09, ez + 0.05, (a & 1) == 0 ? 0x1E5A2A : 0x2A6E34);
                if (a % 3 == 0) Prims.box(rx - 0.03, cy + ry - 0.03, ez - 0.08 * end - 0.03, rx + 0.03, cy + ry + 0.03, ez - 0.08 * end + 0.03, 0xD02020);   // berries
            }
            Prims.box(-0.12, cy - 0.42, ez - 0.06, 0.12, cy - 0.3, ez + 0.06, 0xD02020);
            Prims.box(-0.05, cy - 0.6, ez - 0.05, 0.0, cy - 0.42, ez + 0.05, 0xD02020);
            Prims.box(0.02, cy - 0.58, ez - 0.05, 0.07, cy - 0.42, ez + 0.05, 0xD02020);
        }
    }

    static void ghost(double L, double W, double H, long t, boolean night, int seed) {
        double hw = W / 2 - 0.02, hl = L / 2 - 0.1, h = H - 0.04;
        strings(L, W, h, GHOST, t, seed);
        for (double zz = -hl + 1.2; zz < hl - 0.6; zz += 4.0) {                              // jack-o'-lanterns along the roof
            Prims.box(-0.26, h, zz - 0.26, 0.26, h + 0.44, zz + 0.26, 0xE07010);
            Prims.box(-0.04, h + 0.44, zz - 0.04, 0.04, h + 0.56, zz + 0.04, 0x3A5A20);
            float fl = night ? 0.7f + 0.3f * (float) Math.abs(Math.sin(t * 0.4 + zz)) : 0.25f;
            int eye = Prims.shade(0xFFC040, fl);
            for (int sd = -1; sd <= 1; sd += 2) {
                double fx = sd * 0.265;
                Prims.box(fx - 0.01, h + 0.26, zz - 0.15, fx + 0.01, h + 0.34, zz - 0.05, eye);
                Prims.box(fx - 0.01, h + 0.26, zz + 0.05, fx + 0.01, h + 0.34, zz + 0.15, eye);
                Prims.box(fx - 0.01, h + 0.1, zz - 0.15, fx + 0.01, h + 0.17, zz + 0.15, eye);
            }
        }
        for (int c = 0; c < 4; c++) {                                                       // cobwebs in the roof corners
            double cx = (c & 1) == 0 ? hw : -hw, cz = (c & 2) == 0 ? hl : -hl;
            for (int s = 0; s < 5; s++) {
                double d = 0.08 + s * 0.08;
                Prims.box(cx - Math.signum(cx) * d - 0.01, h - d, cz - 0.01, cx - Math.signum(cx) * d + 0.01, h, cz + 0.01, 0xE8E8E8, 0.6f);
                Prims.box(cx - 0.01, h - d, cz - Math.signum(cz) * d - 0.01, cx + 0.01, h, cz - Math.signum(cz) * d + 0.01, 0xE8E8E8, 0.6f);
            }
        }
        if (!night) return;
        for (int k = 0; k < 3; k++) {                                                       // ghosts circling the car
            double a = t * 0.025 + k * 2.09 + seed;
            double gx = Math.cos(a) * (W / 2 + 1.1), gz = Math.sin(a) * (L / 2 + 0.6), gy = H * 0.55 + Math.sin(t * 0.09 + k) * 0.35;
            GlStateManager.pushMatrix();
            GlStateManager.translate(gx, gy, gz);
            GlStateManager.rotate((float) Math.toDegrees(-a), 0, 1, 0);
            Prims.box(-0.22, 0.0, -0.18, 0.22, 0.62, 0.18, 0xF4F4FF, 0.55f);                 // sheet
            Prims.box(-0.2, 0.62, -0.17, 0.2, 0.98, 0.17, 0xF8F8FF, 0.6f);                  // head
            for (int f = 0; f < 3; f++) Prims.box(-0.2 + f * 0.15, -0.16 - (f & 1) * 0.06, -0.15, -0.09 + f * 0.15, 0.0, 0.15, 0xF0F0FF, 0.4f);   // ragged hem
            Prims.box(-0.12, 0.76, -0.19, -0.04, 0.88, -0.17, 0x101018);                     // eyes
            Prims.box(0.04, 0.76, -0.19, 0.12, 0.88, -0.17, 0x101018);
            Prims.box(-0.05, 0.66, -0.19, 0.05, 0.72, -0.17, 0x101018);
            GlStateManager.popMatrix();
        }
    }
}
