package com.dogpound.railmap.client.render;

import com.dogpound.railmap.signal.TileLineside;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/**
 * Lineside scenery drawn from its own block model, scaled about the middle of its footing. The
 * blocks are INVISIBLE to the chunk renderer so a resized piece doesn't also show at normal size.
 */
public class LinesideRenderer extends TileEntitySpecialRenderer<TileLineside> {
    @Override
    public void render(TileLineside te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        drawModel(te, x, y, z);
    }

    static void drawModel(TileLineside te, double x, double y, double z) {
        if (te.getWorld() == null) return;
        if (te.scale() == 1f && !te.moved()) return;   // normal size, on its block: the chunk draws it
        drawScaled(te.getWorld(), te.getPos(), te.scale(), x + te.offX(), y + te.lift(), z + te.offZ(), te.turn());
    }

    /**
     * Draws a block's own model scaled about the middle of its footing. Shared by every
     * resizable piece in the mod, so a loading gantry can be dialled down to match small-gauge
     * stock exactly the way a signal or a milepost can.
     */
    public static void drawScaled(net.minecraft.world.World world, BlockPos pos, float s,
                                  double x, double y, double z) {
        if (s == 1f) return;   // normal size: the chunk draws it
        drawScaled(world, pos, s, x, y, z, 0f);
    }

    /** same, turned about the footing's middle by the Settings Console angle */
    public static void drawScaled(net.minecraft.world.World world, BlockPos pos, float s,
                                  double x, double y, double z, float turn) {
        if (world == null) return;
        IBlockState state = world.getBlockState(pos);
        BlockRendererDispatcher brd = Minecraft.getMinecraft().getBlockRendererDispatcher();
        IBakedModel model = brd.getModelForState(state);

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        if (turn != 0f) GlStateManager.rotate(-turn, 0, 1, 0);
        GlStateManager.scale(s, s, s);
        GlStateManager.translate(-0.5, 0, -0.5);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buf.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
        brd.getBlockModelRenderer().renderModelFlat(world, model, state, pos, buf, false, 0L);   // flat: smooth-light AO is wrong once the model is moved/turned
        buf.setTranslation(0, 0, 0);
        tess.draw();
        GlStateManager.popMatrix();
        RenderHelper.enableStandardItemLighting();
    }
}
