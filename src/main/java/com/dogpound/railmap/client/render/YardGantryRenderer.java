package com.dogpound.railmap.client.render;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.freight.TileYardGantry;
import com.dogpound.railmap.item.ItemContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import static com.dogpound.railmap.freight.TileYardGantry.*;

/**
 * The Yard Gantry Crane (batch 5 #1), drawn live behind its cab desk in block units (+z = into the yard, x = along the
 * crane rails): the crane rails, the gantry (four legs on wheeled bogies, sill beams, two box girders, portal ties,
 * walkways, the E-house, floodlights, beacons), the cable reel and the trailing cable to the feed pit, the trolley with
 * its hoist drums and the operator's cab, four hoist ropes down to the spreader (swinging with the load), the spreader's
 * twistlock and landed lamps, the box it carries, the trucks and boxes in the truck lane and the 4 x 4 x 4 stack.
 */
public class YardGantryRenderer extends TileEntitySpecialRenderer<TileYardGantry> {
    private static final int YELLOW = 0xE8B820, DARK = 0x2A2E33, STEEL = 0x6A7076, ROPE = 0x181818, RAIL = 0x50555A, WHITE = 0xE8E8E2, GLASS = 0x2A4458;
    private static final ShelfRenderer SHELF = new ShelfRenderer();

    @Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
    public static final class Reg {
        private Reg() {}
        @SubscribeEvent public static void models(ModelRegistryEvent e) { ClientRegistry.bindTileEntitySpecialRenderer(TileYardGantry.class, new YardGantryRenderer()); }
    }

    @Override
    public void render(TileYardGantry te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        SHELF.render(te, x, y, z, pt, destroy, alpha);
        double d2 = x * x + y * y + z * z;
        if (d2 > 200 * 200) return;
        boolean near = d2 < 56 * 56;
        EnumFacing f = te.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.disableLighting();
        int light = te.getWorld().getCombinedLight(te.getPos().up(4), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        Prims.begin();
        double g = te.drawG + 0.5, t = te.drawT + 0.5, h = te.drawH, sx = te.drawSX, sz = te.drawSZ;
        long tick = te.getWorld().getTotalWorldTime();
        boolean moving = Math.abs(te.gx - te.drawG) + Math.abs(te.tz - te.drawT) + Math.abs(te.hy - te.drawH) > 0.01 || te.controls().get("movel") == 1;
        // ---- crane rails, painted lanes
        for (double rz : new double[]{LEG_Z0 + 0.5, LEG_Z1 + 0.5}) {
            Prims.box(GX_MIN - 3.5, 0, rz - 0.35, GX_MAX + 4.5, 0.08, rz + 0.35, 0x8A8A86);
            Prims.box(GX_MIN - 3.5, 0.08, rz - 0.06, GX_MAX + 4.5, 0.24, rz + 0.06, RAIL);
            for (double ex : new double[]{GX_MIN - 3.5, GX_MAX + 4.2}) Prims.box(ex, 0, rz - 0.4, ex + 0.3, 0.7, rz + 0.4, 0xC02020);   // end stops
        }
        for (int l = 0; l < LANES; l++) {
            double lz = LANE_Z[l] + 0.5;
            int c = l == 0 ? 0xF0F0F0 : l == 1 ? 0xC8C8C8 : 0xF0D020;
            Prims.box(GX_MIN - 3, 0.01, lz - 1.35, GX_MAX + 4, 0.025, lz - 1.25, c);
            Prims.box(GX_MIN - 3, 0.01, lz + 1.25, GX_MAX + 4, 0.025, lz + 1.35, c);
        }
        for (int b = 0; b < BAYS; b++) for (double ex : new double[]{-3.15, 3.15}) Prims.box(BAY_X[b] + 0.5 + ex - 0.05, 0.01, LANE_Z[2] - 0.9, BAY_X[b] + 0.5 + ex + 0.05, 0.025, LANE_Z[LANES - 1] + 1.9, 0xF0D020);
        // ---- the gantry
        double top = GIRDER, z0 = LEG_Z0 + 0.5, z1 = LEG_Z1 + 0.5;
        for (double lx : new double[]{g - 3.2, g + 3.2})
            for (double lz : new double[]{z0, z1}) {
                Prims.box(lx - 0.8, 0.1, lz - 0.45, lx + 0.8, 0.9, lz + 0.45, DARK);                                  // bogie
                if (near) for (double wx : new double[]{lx - 0.5, lx + 0.5}) Prims.box(wx - 0.28, 0.0, lz - 0.2, wx + 0.28, 0.56, lz + 0.2, 0x3A3A3A);   // wheels
                Prims.box(lx - 0.35, 0.9, lz - 0.35, lx + 0.35, top, lz + 0.35, YELLOW);                               // leg
                if (near) Prims.box(lx - 0.4, 6.0, lz - 0.4, lx + 0.4, 6.3, lz + 0.4, Prims.shade(YELLOW, 0.8f));     // splice
            }
        for (double lz : new double[]{z0, z1}) {
            Prims.box(g - 3.6, 0.9, lz - 0.3, g + 3.6, 1.6, lz + 0.3, YELLOW);                                         // sill beam
            Prims.box(g - 3.6, top - 0.1, lz - 0.5, g + 3.6, top + 1.5, lz + 0.5, YELLOW);                              // portal tie
            if (near) for (int k = 0; k < 6; k++) {                                                                   // knee braces
                double f1 = k / 6.0;
                Prims.box(g - 3.2 + f1 * 1.6, top - 2.4 + f1 * 2.2, lz - 0.12, g - 3.0 + f1 * 1.6, top - 2.1 + f1 * 2.2, lz + 0.12, YELLOW);
                Prims.box(g + 3.0 - f1 * 1.6, top - 2.4 + f1 * 2.2, lz - 0.12, g + 3.2 - f1 * 1.6, top - 2.1 + f1 * 2.2, lz + 0.12, YELLOW);
            }
        }
        for (double gxo : new double[]{g - 1.5, g + 1.5}) {
            Prims.box(gxo - 0.45, top, z0 - 1.2, gxo + 0.45, top + 1.5, z1 + 1.2, YELLOW);                             // box girder
            Prims.box(gxo - 0.08, top + 1.5, z0 - 1.2, gxo + 0.08, top + 1.62, z1 + 1.2, RAIL);                       // trolley rail
            double wx = gxo + Math.signum(gxo - g) * 0.75;
            Prims.box(wx - 0.3, top + 0.5, z0 - 1.2, wx + 0.3, top + 0.58, z1 + 1.2, 0x8C8E8A);                        // walkway grating
            if (near) {
                double rx = wx + Math.signum(gxo - g) * 0.28;
                Prims.box(rx - 0.03, top + 1.55, z0 - 1.2, rx + 0.03, top + 1.6, z1 + 1.2, YELLOW);                    // handrail
                for (double pz = z0 - 1.2; pz <= z1 + 1.2; pz += 2.0) Prims.box(rx - 0.03, top + 0.58, pz - 0.03, rx + 0.03, top + 1.58, pz + 0.03, YELLOW);
            }
        }
        // E-house, ladder, floodlights, beacons, nameboard
        Prims.box(g - 2.4, top + 1.5, z1 - 1.0, g + 2.4, top + 3.6, z1 + 1.4, 0xD8D8D2);
        Prims.box(g - 2.45, top + 3.6, z1 - 1.05, g + 2.45, top + 3.75, z1 + 1.45, 0x9A9A96);
        if (near) for (int k = 0; k < 4; k++) Prims.box(g - 2.2 + k * 1.15, top + 2.0, z1 - 1.05, g - 1.6 + k * 1.15, top + 3.0, z1 - 1.0, 0x6A6E70);   // louvres
        Prims.box(g - 3.25, top + 0.3, z0 - 0.62, g + 3.25, top + 1.2, z0 - 0.5, 0x10304A);                            // nameboard
        if (near) for (int k = 0; k < 12; k++) Prims.box(g - 3.2 + 0.18 + k * 0.53, top + 0.42, z0 - 0.66, g - 3.2 + 0.48 + k * 0.53, top + 1.08, z0 - 0.62, k == 0 || k == 1 || k == 2 ? 0xF0F0F0 : 0x10304A);
        if (near) for (int k = 0; k < 20; k++) Prims.box(g + 3.55, 1.6 + k * 0.6, z0 - 0.2, g + 3.95, 1.68 + k * 0.6, z0 + 0.2, 0x8C8E8A);   // ladder rungs
        boolean night = te.getWorld().getSkylightSubtracted() > 6 || te.getWorld().isRaining();
        Prims.emissive(true);
        for (double fz : new double[]{z0 - 1.0, (z0 + z1) / 2, z1 + 1.0})
            for (double fx : new double[]{g - 2.2, g + 2.2}) Prims.box(fx - 0.25, top - 0.35, fz - 0.2, fx + 0.25, top - 0.05, fz + 0.2, night && te.controls().get("pw") == 1 ? 0xFFF4C8 : 0x707060);   // floodlights
        boolean flash = moving && (tick / 6) % 2 == 0;
        for (double bz : new double[]{z0, z1}) for (double bx : new double[]{g - 3.2, g + 3.2}) Prims.box(bx - 0.15, top + 1.5, bz - 0.15, bx + 0.15, top + 1.85, bz + 0.15, flash ? 0xFFA020 : 0x704010);
        Prims.emissive(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        // cable reel on the near leg, the trailing cable to the feed pit by the desk
        int cable = te.hv ? 0xE06A10 : 0x1A1A1A;
        double rx = g - 4.2, rr = 0.6 + 0.6 * (100 - te.reel) / 100;
        Prims.box(rx - 0.2, 4.0 - 1.3, z0 - 1.3, rx + 0.2, 4.0 + 1.3, z0 + 1.3, DARK);                                  // drum flange
        Prims.box(rx - 0.6, 4.0 - rr, z0 - rr, rx - 0.2, 4.0 + rr, z0 + rr, cable);                                    // wound cable
        Prims.box(rx - 0.8, 4.0 - 1.3, z0 - 1.3, rx - 0.6, 4.0 + 1.3, z0 + 1.3, DARK);
        Prims.box(rx - 0.9, 3.4, z0 - 0.3, rx + 0.3, 3.8, z0 + 0.3, 0x3A6A9A);                                          // reel motor
        Prims.box(rx - 0.5, 0.02, z0 - 0.08, rx - 0.3, 4.0, z0 + 0.08, cable);                                        // cable down to the ground
        double feed = 0.5;
        Prims.box(Math.min(rx - 0.5, feed), 0.02, z0 - 0.08, Math.max(rx - 0.3, feed), 0.14, z0 + 0.08, cable);                    // trailing cable in its trough
        Prims.box(feed - 0.6, 0, z0 - 0.5, feed + 0.6, 0.18, z0 + 0.5, 0x5A5E60);                                      // feed pit cover
        // ---- the trolley, cab, ropes, spreader
        Prims.box(g - 2.1, top + 1.62, t - 1.3, g + 2.1, top + 2.3, t + 1.3, STEEL);                                    // trolley frame
        Prims.box(g - 1.4, top + 2.3, t - 1.1, g + 1.4, top + 3.6, t + 1.1, 0xD8D8D2);                                  // machinery house
        if (near) {
            for (double dz : new double[]{t - 0.6, t + 0.6}) Prims.box(g - 1.2, top + 1.0, dz - 0.35, g + 1.2, top + 1.62, dz + 0.35, 0x404448);   // hoist drums (hang between the girders)
            for (double wx : new double[]{g - 1.5, g + 1.5}) for (double wz : new double[]{t - 1.1, t + 1.1}) Prims.box(wx - 0.2, top + 1.6, wz - 0.25, wx + 0.2, top + 1.95, wz + 0.25, DARK);   // trolley wheels
        }
        Prims.box(g + 2.3, top - 2.0, t - 0.9, g + 3.6, top + 0.2, t + 0.9, 0xE6E6E0);                                  // operator cab
        Prims.box(g + 2.25, top - 1.7, t - 0.8, g + 2.3, top - 0.1, t + 0.8, GLASS);
        Prims.box(g + 2.4, top - 2.05, t - 0.8, g + 3.5, top - 2.0, t + 0.8, GLASS);                                   // floor window
        Prims.box(g + 2.3, top + 0.2, t - 0.3, g + 2.6, top + 1.62, t + 0.3, STEEL);                                   // cab hanger
        double bx = g + sx, bz = t + sz, hb = h + 0.42;
        int seg = near ? 8 : 2;
        for (double ox : new double[]{-1.0, 1.0})
            for (double oz : new double[]{-0.45, 0.45})
                for (int k = 0; k < seg; k++) {                                                                          // the ropes, in steps so they lean with the sway
                    double f0 = k / (double) seg, f1 = (k + 1) / (double) seg;
                    double ya = top + 1.0 + (hb - top - 1.0) * f0, yb = top + 1.0 + (hb - top - 1.0) * f1;
                    double xm = g + ox * 0.6 + (bx - g + ox * 0.4) * (f0 + f1) / 2, zm = t + oz + (bz - t) * (f0 + f1) / 2;
                    Prims.box(xm - 0.035, Math.min(ya, yb), zm - 0.035, xm + 0.035, Math.max(ya, yb), zm + 0.035, ROPE);
                }
        Prims.box(bx - 1.3, h + 0.3, bz - 0.5, bx + 1.3, h + 0.62, bz + 0.5, YELLOW);                                  // headblock
        Prims.box(bx - BOX_L / 2, h, bz - BOX_W / 2 + 0.2, bx + BOX_L / 2, h + 0.3, bz - BOX_W / 2 + 0.45, YELLOW);     // spreader frame
        Prims.box(bx - BOX_L / 2, h, bz + BOX_W / 2 - 0.45, bx + BOX_L / 2, h + 0.3, bz + BOX_W / 2 - 0.2, YELLOW);
        Prims.box(bx - 0.4, h, bz - BOX_W / 2, bx + 0.4, h + 0.3, bz + BOX_W / 2, YELLOW);
        for (double ex : new double[]{-BOX_L / 2, BOX_L / 2 - 0.3}) Prims.box(bx + ex, h, bz - BOX_W / 2, bx + ex + 0.3, h + 0.3, bz + BOX_W / 2, YELLOW);
        if (near) for (double fx : new double[]{-BOX_L / 2 - 0.15, BOX_L / 2}) for (double fz : new double[]{-BOX_W / 2 - 0.15, BOX_W / 2})
            Prims.box(bx + fx, h - 0.4, bz + fz, bx + fx + 0.15, h + 0.2, bz + fz + 0.15, 0x303030);                     // guide flippers
        Prims.emissive(true);
        int lock = te.locked ? 0x30E060 : 0xFF3030;
        for (double cx : new double[]{-BOX_L / 2 + 0.2, BOX_L / 2 - 0.2}) for (double cz : new double[]{-BOX_W / 2 + 0.3, BOX_W / 2 - 0.3})
            Prims.box(bx + cx - 0.1, h + 0.3, bz + cz - 0.1, bx + cx + 0.1, h + 0.42, bz + cz + 0.1, lock);
        Prims.box(bx - 0.12, h + 0.62, bz - 0.12, bx + 0.12, h + 0.78, bz + 0.12, te.controls().get("landed") == 1 ? 0xFFB020 : 0x402810);
        Prims.emissive(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        if (!te.carrying.isEmpty()) box(te.carrying, bx, h - BOX_H, bz, near);
        // ---- the truck lane and the stack
        for (int b = 0; b < BAYS; b++) {
            double cx = BAY_X[b] + 0.5, tz0 = LANE_Z[0] + 0.5;
            if (!te.truck[b].isEmpty() || te.controls().get("tk" + b) == 1) {
                Prims.box(cx - 3.2, 0.6, tz0 - 1.1, cx + 3.2, TRUCK_DECK, tz0 + 1.1, 0x303438);                             // skeletal trailer
                Prims.box(cx + 3.3, 0.5, tz0 - 1.15, cx + 6.0, 3.2, tz0 + 1.15, 0x2050A0);                                  // tractor unit
                Prims.box(cx + 5.2, 1.8, tz0 - 1.05, cx + 6.02, 2.9, tz0 + 1.05, GLASS);
                if (near) for (double wx : new double[]{cx - 2.6, cx - 1.6, cx + 4.0, cx + 5.4}) for (double wz : new double[]{tz0 - 1.1, tz0 + 0.8})
                    Prims.box(wx - 0.45, 0, wz, wx + 0.45, 0.9, wz + 0.3, 0x1A1A1A);
                if (!te.truck[b].isEmpty()) box(te.truck[b], cx, TRUCK_DECK, tz0, near);
            }
            for (int r = 0; r < ROWS; r++)
                for (int k = 0; k < TIERS; k++) {
                    ItemStack s = te.stack[b][r][k];
                    if (!s.isEmpty()) box(s, cx, k * BOX_H, LANE_Z[r + 2] + 0.5, near);
                }
        }
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void box(ItemStack s, double cx, double y, double cz, boolean near) {
        if (near) { ContainerCraneRenderer.box(s, cx, y, cz); return; }
        Prims.box(cx - BOX_L / 2, y, cz - BOX_W / 2, cx + BOX_L / 2, y + BOX_H, cz + BOX_W / 2, ItemContainer.colour(s));
    }

    @Override public boolean isGlobalRenderer(TileYardGantry te) { return true; }
}
