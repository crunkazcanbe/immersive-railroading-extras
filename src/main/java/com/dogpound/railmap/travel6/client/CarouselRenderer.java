package com.dogpound.railmap.travel6.client;

import com.dogpound.railmap.travel6.TileBagHall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/**
 * The reclaim carousel's bags (batch 6b #9): every bag in the carousel store rides the oval slat track (model px, the
 * track in tools/gen_bb_batch6b.py: straights x XA..XB at z ZF / ZB, round ends of radius R), going round while the
 * drive runs, standing still when it is stopped or has no power.
 */
public class CarouselRenderer extends TileEntitySpecialRenderer<TileBagHall> {
    static final double XA = -6.0, XB = 22.0, ZC = 6.5, R = 4.2, Y = 6.6;   // tools/gen_bb_batch6b.py bag_hall()
    static final double STRAIGHT = XB - XA, PERIM = 2 * STRAIGHT + 2 * Math.PI * R;

    /** a point on the loop, s in model px along it: {x, z, heading deg} */
    static double[] at(double s) {
        s = ((s % PERIM) + PERIM) % PERIM;
        if (s < STRAIGHT) return new double[]{XA + s, ZC - R, 0};
        s -= STRAIGHT;
        if (s < Math.PI * R) { double a = s / R; return new double[]{XB + Math.sin(a) * R, ZC - Math.cos(a) * R, -Math.toDegrees(a)}; }
        s -= Math.PI * R;
        if (s < STRAIGHT) return new double[]{XB - s, ZC + R, 180};
        s -= STRAIGHT;
        double a = s / R;
        return new double[]{XA - Math.sin(a) * R, ZC + Math.cos(a) * R, 180 - Math.toDegrees(a)};
    }

    @Override
    public void render(TileBagHall te, double x, double y, double z, float pt, int destroy, float alpha) {
        if (te.getWorld() == null || x * x + y * y + z * z > 48 * 48) return;
        IInventory inv = te.reclaim();
        EnumFacing f = te.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        double run = te.turning() ? (te.getWorld().getTotalWorldTime() + pt) * 0.35 : 0;   // px per tick
        int n = inv.getSizeInventory();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        RenderHelper.enableStandardItemLighting();
        for (int i = 0; i < n; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            double[] p = at(i * PERIM / n + run);
            GlStateManager.pushMatrix();
            GlStateManager.translate(p[0] / 16.0, Y / 16.0 + 0.12, p[1] / 16.0);
            GlStateManager.rotate((float) p[2], 0, 1, 0);
            GlStateManager.scale(0.42, 0.42, 0.42);
            Minecraft.getMinecraft().getRenderItem().renderItem(s, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    @Override public boolean isGlobalRenderer(TileBagHall te) { return true; }
}
