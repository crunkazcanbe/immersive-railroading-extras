package com.dogpound.railmap.client.render;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.controls.Layout;
import com.dogpound.railmap.controls.Surface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Draws every machine's working controls (requested feature) live, on whichever faces the model puts them: lamps light,
 * needles move, keys and selectors turn, buttons push in, levers swing. One pass for all machines, within 32 blocks.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class ControlsRenderer {
    private ControlsRenderer() {}

    @SubscribeEvent
    public static void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.getRenderViewEntity() == null) return;
        Entity v = mc.getRenderViewEntity();
        float pt = e.getPartialTicks();
        double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt, cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt, cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableLighting();
        for (TileEntity t : mc.world.loadedTileEntityList) {
            if (!(t instanceof IControlled m) || t.isInvalid()) continue;
            BlockPos p = t.getPos();
            double x = p.getX() - cx, y = p.getY() - cy, z = p.getZ() - cz;
            double d2 = x * x + y * y + z * z;
            if (d2 > 32 * 32) continue;
            Layout l = m.layout();
            if (l.isEmpty()) continue;
            draw(m, l, x, y, z, pt, d2 < 12 * 12);
        }
        RenderHelper.disableStandardItemLighting();
    }

    static void draw(IControlled m, Layout l, double x, double y, double z, float pt, boolean text) {
        TileEntity t = (TileEntity) m;
        EnumFacing f = m.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        for (Surface s : l.surfaces) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(8, 8, 8);
            if (s.top()) GlStateManager.rotate(90f, 1, 0, 0);
            else if (s.yaw() != 0) GlStateManager.rotate((float) s.yaw(), 0, 1, 0);
            GlStateManager.translate(-8, -8, -8);
            if (s.tilt != 0) {
                GlStateManager.translate(0, s.pivot, s.panel.face);
                GlStateManager.rotate((float) s.tilt, 1, 0, 0);
                GlStateManager.translate(0, -s.pivot, -s.panel.face);
            }
            GridPanelRenderer.drawInFrame(m, s.panel, t.getWorld(), t.getPos().up(), pt, text, s.plate);
            GlStateManager.popMatrix();
        }
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }
}
