package com.dogpound.railmap.client.render;

import com.dogpound.railmap.block.TileContainerTerminal;
import com.dogpound.railmap.item.ItemContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/**
 * The Container Terminal's gantry crane and its yard (her ChatGPT list #47), drawn live in block units behind the desk
 * (+z in the desk's own frame): four legs and two girders spanning the yard, the trolley riding the girders with its
 * machinery house and cab, hoist ropes down to the spreader (twistlocks lit when locked), the box it carries, the box
 * on the truck apron and the two stack rows. Boxes are corrugated, in their owner's livery, with corner castings.
 */
public class ContainerCraneRenderer extends TileEntitySpecialRenderer<TileContainerTerminal> {
    private static final int YELLOW = 0xE8B820, STEEL = 0x5A6066, DARK = 0x2A2E33, ROPE = 0x1A1A1A;
    private static final double X0 = -3.6, X1 = 4.6;            // crane legs, across the yard (block units, block = 0..1)
    private static final double Z0 = 1.6, Z1 = 10.4, TOP = 10.4, W = 1.25;   // legs along the yard; girder height; box half width

    @Override
    public void render(TileContainerTerminal te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null || x * x + y * y + z * z > 160 * 160) return;
        EnumFacing f = te.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.disableLighting();
        Prims.begin();
        // ---- the gantry
        for (double lx : new double[]{X0, X1})
            for (double lz : new double[]{Z0, Z1}) {
                Prims.box(lx - 0.3, 0, lz - 0.3, lx + 0.3, TOP, lz + 0.3, YELLOW);
                Prims.box(lx - 0.45, 0, lz - 0.45, lx + 0.45, 0.35, lz + 0.45, DARK);        // bogie
            }
        for (double lx : new double[]{X0, X1}) {
            Prims.box(lx - 0.35, TOP, Z0 - 0.6, lx + 0.35, TOP + 0.8, Z1 + 0.6, YELLOW);         // girder along the yard
            Prims.box(lx - 0.25, 0.35, Z0, lx + 0.25, 0.7, Z1, YELLOW);                           // sill beam
            for (double d = Z0 + 1; d < Z1; d += 1.6) Prims.box(lx - 0.05, TOP - 0.9, d, lx + 0.05, TOP, d + 0.08, YELLOW);
        }
        for (double lz : new double[]{Z0 - 0.4, Z1 + 0.4}) Prims.box(X0 - 0.35, TOP + 0.2, lz - 0.3, X1 + 0.35, TOP + 0.9, lz + 0.3, YELLOW);   // portal beams
        Prims.box(X1 + 0.35, TOP + 0.9, Z0 - 0.6, X1 + 0.5, TOP + 1.0, Z1 + 0.6, 0xC03020);           // festoon track
        // ---- the trolley, ropes, spreader and the box it holds
        double tz = 0.5 + te.drawT, hy = te.drawH;
        Prims.box(X0 - 0.2, TOP + 0.8, tz - 0.9, X1 + 0.2, TOP + 1.5, tz + 0.9, STEEL);
        Prims.box(0.0, TOP + 1.5, tz - 0.8, 2.0, TOP + 2.6, tz + 0.8, 0xD8D8D4);                     // machinery house
        Prims.box(X1 + 0.4, TOP - 1.6, tz - 0.7, X1 + 1.6, TOP + 0.6, tz + 0.7, 0xD8D8D4);           // operator cab
        Prims.box(X1 + 0.35, TOP - 1.2, tz - 0.6, X1 + 0.4, TOP + 0.3, tz + 0.6, 0x203040);          // cab window
        double sx0 = 0.5 - 3.0, sx1 = 0.5 + 3.0;
        for (double rx : new double[]{sx0 + 0.6, sx1 - 0.6})
            for (double rz : new double[]{tz - 0.5, tz + 0.5}) Prims.box(rx - 0.04, hy + 0.3, rz - 0.04, rx + 0.04, TOP + 0.8, rz + 0.04, ROPE);
        Prims.box(sx0, hy, tz - W, sx1, hy + 0.3, tz + W, YELLOW);                                  // spreader
        int lock = te.locked ? 0x30E060 : 0xFF3030;
        Prims.emissive(true);
        for (double cx : new double[]{sx0 + 0.15, sx1 - 0.15}) for (double cz : new double[]{tz - W + 0.15, tz + W - 0.15}) Prims.box(cx - 0.1, hy + 0.3, cz - 0.1, cx + 0.1, hy + 0.42, cz + 0.1, lock);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
        if (!te.carrying.isEmpty()) box(te.carrying, 0.5, hy - TileContainerTerminal.BOX_H, tz);
        // ---- the yard
        if (!te.apron.isEmpty()) box(te.apron, 0.5, 0, 0.5 + TileContainerTerminal.POS_D[0]);
        for (int r = 0; r < 2; r++)
            for (int t = 0; t < TileContainerTerminal.TIERS; t++) {
                ItemStack s = te.rows[r][t];
                if (!s.isEmpty()) box(s, 0.5, t * TileContainerTerminal.BOX_H, 0.5 + TileContainerTerminal.POS_D[2 + r]);
            }
        // painted bay markings on the ground
        for (int i = 0; i < 4; i++) {
            double cz = 0.5 + TileContainerTerminal.POS_D[i];
            int c = i == 1 ? 0xF0F0F0 : 0xF0D020;
            Prims.box(sx0 - 0.1, 0.01, cz - W - 0.1, sx1 + 0.1, 0.03, cz - W, c);
            Prims.box(sx0 - 0.1, 0.01, cz + W, sx1 + 0.1, 0.03, cz + W + 0.1, c);
        }
        Prims.end();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** a 20 ft box, bottom centre at (cx, y, cz), long side across the yard */
    static void box(ItemStack s, double cx, double y, double cz) {
        int c = ItemContainer.colour(s), rib = Prims.shade(c, 0.8f);
        double x0 = cx - 3.0, x1 = cx + 3.0, h = TileContainerTerminal.BOX_H;
        Prims.box(x0, y, cz - W, x1, y + h, cz + W, c);
        for (double rx = x0 + 0.3; rx < x1 - 0.2; rx += 0.35) {
            Prims.box(rx, y + 0.15, cz - W - 0.04, rx + 0.12, y + h - 0.15, cz - W, rib);
            Prims.box(rx, y + 0.15, cz + W, rx + 0.12, y + h - 0.15, cz + W + 0.04, rib);
        }
        Prims.box(x1, y + 0.1, cz - W + 0.1, x1 + 0.05, y + h - 0.1, cz + W - 0.1, rib);           // doors
        Prims.box(x1 + 0.05, y + 0.3, cz - 0.3, x1 + 0.1, y + h - 0.3, cz - 0.2, 0x909090);       // locking bars
        Prims.box(x1 + 0.05, y + 0.3, cz + 0.2, x1 + 0.1, y + h - 0.3, cz + 0.3, 0x909090);
        for (double kx : new double[]{x0, x1 - 0.2}) for (double ky : new double[]{y, y + h - 0.2}) for (double kz : new double[]{cz - W, cz + W - 0.2})
            Prims.box(kx, ky, kz, kx + 0.2, ky + 0.2, kz + 0.2, 0x303030);                       // corner castings
        Prims.box(x0 + 0.4, y + h - 0.7, cz - W - 0.06, x0 + 2.2, y + h - 0.3, cz - W - 0.02, 0xF0F0F0);   // number plate
    }

    @Override public boolean isGlobalRenderer(TileContainerTerminal te) { return true; }
}
