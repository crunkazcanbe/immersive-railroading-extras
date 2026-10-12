package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.TileGrid;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/**
 * Earth bonding straps (requested feature): a flat copper strap on the ground from the Earthing Switch's copper base to every grid machine beside it,
 * bolted down at both ends - how real equipment is bonded to earth.
 */
final class BondStrap {
    private BondStrap() {}

    static void render(TileGrid te, double x, double y, double z) {
        World w = te.getWorld();
        int l = w.getCombinedLight(te.getPos(), 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
        Prims.begin();
        for (EnumFacing d : EnumFacing.HORIZONTALS) {
            if (!(w.getTileEntity(te.getPos().offset(d)) instanceof TileGrid)) continue;
            double x0 = 7, x1 = 9, z0 = 7, z1 = 9;                                   // centre pad
            switch (d) {
                case NORTH: z0 = 0; break;
                case SOUTH: z1 = 16; break;
                case WEST: x0 = 0; break;
                default: x1 = 16; break;
            }
            Prims.box(x0, 0.05, z0, x1, 0.75, z1, 0xC27A3A);                           // the flat copper strap
            double bx = d == EnumFacing.WEST ? 1 : d == EnumFacing.EAST ? 15 : 8, bz = d == EnumFacing.NORTH ? 1 : d == EnumFacing.SOUTH ? 15 : 8;
            Prims.box(bx - 0.6, 0.75, bz - 0.6, bx + 0.6, 1.15, bz + 0.6, 0x8A9098);   // bolt at the far end
            Prims.box(bx - 0.6 + (8 - bx) * 0.55, 0.75, bz - 0.6 + (8 - bz) * 0.55, bx + 0.6 + (8 - bx) * 0.55, 1.15, bz + 0.6 + (8 - bz) * 0.55, 0x8A9098);
        }
        Prims.end();
        GlStateManager.popMatrix();
    }
}
