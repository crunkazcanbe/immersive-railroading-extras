package com.dogpound.railmap.factory.client;

import com.dogpound.railmap.factory.block.BlockPort;
import com.dogpound.railmap.factory.block.TilePort;
import com.dogpound.railmap.factory.conveyor.BlockConveyor;
import com.dogpound.railmap.factory.conveyor.TileConveyor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Where a belt meets an item hatch the hatch opens a mouth for it: a steel hood over the belt, a dark opening and a
 * rubber strip curtain, so the belt runs flush into the input hatch / out of the output hatch.
 */
public class PortRenderer extends TileEntitySpecialRenderer<TilePort> {
    static final double S = TileConveyor.SURFACE;

    @Override
    public void render(TilePort te, double x, double y, double z, float pt, int destroy, float alpha) {
        World w = te.getWorld();
        if (w == null) return;
        IBlockState st = w.getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockPort port)) return;
        BlockPos p = te.getPos();
        if (port.kind == BlockPort.Kind.FLUID_IN || port.kind == BlockPort.Kind.FLUID_OUT) {        // pipes plug into the hatch
            com.dogpound.railmap.factory.machine.TileMachine m = te.machine();
            for (EnumFacing d : EnumFacing.values()) {
                BlockPos n = p.offset(d);
                if (m != null && (m.isMember(n) || n.equals(m.getPos()))) continue;
                net.minecraft.tileentity.TileEntity t = w.getTileEntity(n);
                if (t == null || !t.hasCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, d.getOpposite())) continue;
                pipeStub(x, y, z, d, w.getCombinedLight(n, 0), port.kind == BlockPort.Kind.FLUID_IN);
            }
            return;
        }
        if (port.kind != BlockPort.Kind.ITEM_IN && port.kind != BlockPort.Kind.ITEM_OUT) return;
        for (EnumFacing d : EnumFacing.HORIZONTALS) {
            BlockPos n = p.offset(d);
            IBlockState ns = w.getBlockState(n);
            if (!(ns.getBlock() instanceof BlockConveyor)) continue;
            EnumFacing bf = ns.getValue(BlockConveyor.FACING);
            boolean in = BlockConveyor.target(w, n, bf).equals(p);
            if (port.kind == BlockPort.Kind.ITEM_IN ? !in : bf == d.getOpposite()) continue;
            mouth(x, y, z, d, w.getCombinedLight(n, 0));
        }
    }

    /** a pipe connection on side d: brass flange at the face, a pipe into the hatch, a valve body at the hatch */
    private static void pipeStub(double x, double y, double z, EnumFacing d, int light, boolean inlet) {
        TextureAtlasSprite steel = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("irextras:blocks/machine_casing");
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
        if (d.getAxis() == EnumFacing.Axis.Y) GlStateManager.rotate(d == EnumFacing.UP ? 90 : -90, 1, 0, 0);
        else GlStateManager.rotate(d == EnumFacing.NORTH ? 0 : d == EnumFacing.EAST ? -90 : d == EnumFacing.SOUTH ? 180 : 90, 0, 1, 0);
        GlStateManager.translate(-0.5, -0.5, -0.5);
        BufferBuilder bb = BeltDraw.begin(light);
        double px = 1 / 16.0;
        BeltDraw.box(bb, steel, 4 * px, 4 * px, -0.02, 12 * px, 12 * px, 1 * px, 196, 150, 70);                 // flange at the face
        for (double[] b : new double[][]{{4.6, 4.6}, {10.6, 4.6}, {4.6, 10.6}, {10.6, 10.6}})                   // flange bolts
            BeltDraw.box(bb, steel, b[0] * px, b[1] * px, -0.06, (b[0] + 0.8) * px, (b[1] + 0.8) * px, 0, 70, 70, 74);
        BeltDraw.box(bb, steel, 5 * px, 5 * px, 1 * px, 11 * px, 11 * px, 6 * px, 64, 68, 74);                  // pipe into the hatch
        BeltDraw.box(bb, steel, 3.5 * px, 3.5 * px, 6 * px, 12.5 * px, 12.5 * px, 10 * px, inlet ? 52 : 120, inlet ? 96 : 60, inlet ? 160 : 40);   // hatch valve body: blue in, rust out
        BeltDraw.box(bb, steel, 7.5 * px, 12.5 * px, 7.5 * px, 8.5 * px, 14 * px, 8.5 * px, 180, 180, 180);       // valve stem
        BeltDraw.box(bb, steel, 5.5 * px, 14 * px, 7.6 * px, 10.5 * px, 14.6 * px, 8.4 * px, 178, 34, 30);        // handwheel
        BeltDraw.box(bb, steel, 7.6 * px, 14 * px, 5.5 * px, 8.4 * px, 14.6 * px, 10.5 * px, 178, 34, 30);
        BeltDraw.end();
        GlStateManager.popMatrix();
    }

    private static void mouth(double x, double y, double z, EnumFacing d, int light) {
        float ang = d == EnumFacing.NORTH ? 0 : d == EnumFacing.EAST ? -90 : d == EnumFacing.SOUTH ? 180 : 90;
        TextureAtlasSprite steel = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("irextras:blocks/machine_casing");
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(ang, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        BufferBuilder bb = BeltDraw.begin(light);
        double px = 1 / 16.0, out = -4.5 * px;
        // the dark opening the belt disappears into, with dark inner walls so it reads as a tunnel
        BeltDraw.box(bb, steel, 2 * px, S, -0.004, 14 * px, 12 * px, -0.002, 12, 12, 14);
        BeltDraw.box(bb, steel, 2 * px, S, out, 2.3 * px, 12 * px, 0, 26, 26, 30);
        BeltDraw.box(bb, steel, 13.7 * px, S, out, 14 * px, 12 * px, 0, 26, 26, 30);
        // hood: roof and cheeks
        BeltDraw.box(bb, steel, 0.5 * px, 12 * px, out, 15.5 * px, 14 * px, 0, 128, 132, 138);
        BeltDraw.box(bb, steel, 0.5 * px, 2.5 * px, out, 2 * px, 12 * px, 0, 110, 114, 120);
        BeltDraw.box(bb, steel, 14 * px, 2.5 * px, out, 15.5 * px, 12 * px, 0, 110, 114, 120);
        // black-and-yellow hazard lip round the opening
        for (int i = 0; i < 8; i++) {
            boolean yel = i % 2 == 0;
            double x0 = (0.5 + i * 15 / 8.0) * px;
            BeltDraw.box(bb, steel, x0, 12.6 * px, out - 0.3 * px, x0 + 15 / 8.0 * px, 14.2 * px, out + 0.5 * px, yel ? 232 : 24, yel ? 182 : 24, yel ? 40 : 26);
        }
        for (double cx : new double[]{0.3, 14.2})
            BeltDraw.box(bb, steel, cx * px, 2.5 * px, out - 0.3 * px, (cx + 1.5) * px, 12.6 * px, out + 0.5 * px, 232, 182, 40);
        // rubber strip curtain
        for (int i = 0; i < 6; i++) {
            double x0 = (2.4 + i * 1.9) * px;
            BeltDraw.box(bb, steel, x0, S + 1.2 * px, out + 1.2 * px, x0 + 1.6 * px, 12 * px, out + 1.45 * px, 34, 34, 38);
        }
        BeltDraw.end();
        GlStateManager.popMatrix();
    }
}
