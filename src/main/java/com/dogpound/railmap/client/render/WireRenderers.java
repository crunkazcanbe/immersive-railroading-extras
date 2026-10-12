package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.wire.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Instrument wiring: the thin wires side by side, bundled cable, Grid Taps, radios and the Gauge Panel. */
public final class WireRenderers {
    private WireRenderers() {}

    private static void light(World w, BlockPos p) {
        int l = w.getCombinedLight(p, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
    }

    /**
     * A lead on the floor of this block (world-aligned pixels) from (sx, sz) to its edge toward d, arriving on the lane
     * the wire next door runs that colour in (o px off the centre line), so the two meet exactly.
     */
    public static void floorLead(EnumFacing d, double sx, double sz, double o, double hw, double h, int rgb) {
        if (d.getAxis() == EnumFacing.Axis.Z) {
            double lx = 8 + o, ez = d == EnumFacing.NORTH ? 0 : 16;
            Prims.box(Math.min(sx, lx) - hw, 0, sz - hw, Math.max(sx, lx) + hw, h, sz + hw, rgb);
            Prims.box(lx - hw, 0, Math.min(sz, ez), lx + hw, h, Math.max(sz, ez), rgb);
        } else {
            double lz = 8 + o, ex = d == EnumFacing.WEST ? 0 : 16;
            Prims.box(sx - hw, 0, Math.min(sz, lz) - hw, sx + hw, h, Math.max(sz, lz) + hw, rgb);
            Prims.box(Math.min(sx, ex), 0, lz - hw, Math.max(sx, ex), h, lz + hw, rgb);
        }
    }

    // ---- Instrument Wire / Bundled Cable ----------------------------------------------------------------------
    public static class Wire extends TileEntitySpecialRenderer<TileWire> {
        private static final EnumFacing[] H = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

        @Override
        public void render(TileWire te, double x, double y, double z, float pt, int ds, float a) {
            World w = te.getWorld();
            if (w == null || x * x + y * y + z * z > 64 * 64) return;
            boolean[] j = new boolean[4];
            boolean any = false;
            for (int i = 0; i < 4; i++) { j[i] = WireNet.joins(w, te.getPos(), H[i], te); any |= j[i]; }
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
            GlStateManager.disableCull();
            light(w, te.getPos());
            Prims.begin();
            if (te.bundled()) {
                strand(j, any, 0, 1.6, 2.6, 0x2E2E33);                                  // the sheath
                for (int k = 0; k < 6; k++)                                             // colour bands showing what's inside
                    Prims.box(6.3 + k * 0.6, 2.6, 7.2, 6.8 + k * 0.6, 2.75, 8.8, WireNet.RGB[new int[]{14, 1, 4, 5, 11, 10}[k]]);
            } else {
                List<Integer> cs = new ArrayList<>();
                for (int c = 0; c <= WireNet.POWER; c++) if (te.has(c)) cs.add(c);
                for (int i = 0; i < cs.size(); i++) {
                    double o = (i - (cs.size() - 1) / 2.0) * 1.15;
                    if (cs.get(i) == WireNet.POWER) {                                   // the 12 V pair: red + black side by side
                        strand(j, any, o - 0.3, 0.27, 0.7, WireNet.POWER_RGB);
                        strand(j, any, o + 0.3, 0.27, 0.7, 0x1A1A1A);
                    } else strand(j, any, o, 0.42, 0.7, WireNet.RGB[cs.get(i)]);
                }
            }
            for (int i = 0; i < 4; i++) if (j[i]) terminal(w, te, H[i]);
            Prims.end();
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }

        /**
         * Where the wire lands on a machine's control terminal (or a Data Link's input): a little grey junction box
         * against that machine's face at floor level, a brass terminal strip and the landed colour's band.
         */
        private static void terminal(World w, TileWire te, EnumFacing d) {
            if (te.has(WireNet.POWER) && toGearTerminal(w, te, d)) return;
            net.minecraft.tileentity.TileEntity t = w.getTileEntity(te.getPos().offset(d));
            int band;
            if (t instanceof com.dogpound.railmap.grid.TileGrid && te.has(WireNet.POWER)) band = WireNet.POWER_RGB;     // the 12 V supply point
            else if (t instanceof com.dogpound.railmap.wire.IWiredMachine m && m.ctlWire() >= 0) band = WireNet.RGB[m.ctlWire()];
            else if (t instanceof com.dogpound.railmap.block.TileDataLink) band = 0x3060C0;
            else return;
            double c = 8 + (te.bundled() ? 0 : 0), hw = 2.2, dep = 1.6, h = 4.2;
            double x0, x1, z0, z1;                                                        // the box, flat against the face toward d
            switch (d) {
                case NORTH: x0 = c - hw; x1 = c + hw; z0 = 0; z1 = dep; break;
                case SOUTH: x0 = c - hw; x1 = c + hw; z0 = 16 - dep; z1 = 16; break;
                case WEST: z0 = c - hw; z1 = c + hw; x0 = 0; x1 = dep; break;
                default: z0 = c - hw; z1 = c + hw; x0 = 16 - dep; x1 = 16; break;
            }
            Prims.box(x0, 0, z0, x1, h, z1, 0x6A7077);                                   // junction box
            boolean ns = d.getAxis() == EnumFacing.Axis.Z;
            double in = 0.25;                                                            // the lid's front face
            double fx0 = ns ? x0 + 0.4 : (d == EnumFacing.WEST ? x1 : x0 - 0.12), fx1 = ns ? x1 - 0.4 : (d == EnumFacing.WEST ? x1 + 0.12 : x0);
            double fz0 = ns ? (d == EnumFacing.NORTH ? z1 : z0 - 0.12) : z0 + 0.4, fz1 = ns ? (d == EnumFacing.NORTH ? z1 + 0.12 : z0) : z1 - 0.4;
            Prims.box(fx0, 2.6, fz0, fx1, 3.6, fz1, 0xC9A043);                            // brass terminal strip
            Prims.box(fx0, 1.5, fz0, fx1, 2.1, fz1, band);                               // colour band: which circuit it's on
            Prims.box(fx0 + (ns ? 1.6 : 0), 0.6, fz0 + (ns ? 0 : 1.6), fx1 - (ns ? 1.6 : 0), 1.2, fz1 - (ns ? 0 : 1.6), 0x1A1A1A);   // the gland the wire goes in
        }

        /**
         * A 12 V pair landing on gear with a modelled terminal box (requested feature): the
         * red + black pair runs on along the floor to under the gear's own 12V DC box (read from its model), rises into
         * it through a gland. Works for a wide desk's side blocks too (the terminal is the desk's). False = no modelled
         * terminal, use the old junction box.
         */
        private static boolean toGearTerminal(World w, TileWire te, EnumFacing d) {
            net.minecraft.util.math.BlockPos n = te.getPos().offset(d), gear = n;
            if (!(w.getTileEntity(n) instanceof com.dogpound.railmap.wire.IWiredGear)) {
                if (!(w.getBlockState(n).getBlock() instanceof com.dogpound.railmap.block.BlockOfficePart)) return false;
                gear = com.dogpound.railmap.block.BlockOfficePart.desk(w, n);
                if (gear == null || !(w.getTileEntity(gear) instanceof com.dogpound.railmap.wire.IWiredGear)) return false;
            }
            java.util.List<double[]> spots = TerminalSpots.of(w.getBlockState(gear));
            if (spots.isEmpty()) return false;
            double ox = (gear.getX() - te.getPos().getX()) * 16, oz = (gear.getZ() - te.getPos().getZ()) * 16;
            double[] best = null; double bd = Double.MAX_VALUE;
            for (double[] s : spots) {                                                    // the terminal nearest this wire
                double dx = ox + s[0] - 8, dz = oz + s[2] - 8, dd = dx * dx + dz * dz;
                if (dd < bd) { bd = dd; best = s; }
            }
            double tx = ox + best[0], tz = oz + best[2], ty = best[1];
            boolean alongX = d.getAxis() == EnumFacing.Axis.X;
            double cx = alongX ? tx : 8, cz = alongX ? 8 : tz;                           // the corner: out along d, then across
            pair(8, 8, cx, cz);
            pair(cx, cz, tx, tz);
            Prims.box(tx - 0.9, 0, tz - 0.9, tx + 0.9, 0.9, tz + 0.9, 0x1A1A1A);          // floor gland
            if (ty > 0.9) {                                                               // up into the box
                Prims.box(tx - 0.57, 0.9, tz - 0.27, tx - 0.03, ty, tz + 0.27, WireNet.POWER_RGB);
                Prims.box(tx + 0.03, 0.9, tz - 0.27, tx + 0.57, ty, tz + 0.27, 0x1A1A1A);
            }
            return true;
        }

        /** the red + black 12 V pair lying on the floor from (x0,z0) to (x1,z1) - one straight, axis-aligned run */
        private static void pair(double x0, double z0, double x1, double z1) {
            if (Math.abs(x1 - x0) < 0.01 && Math.abs(z1 - z0) < 0.01) return;
            boolean xRun = Math.abs(x1 - x0) >= Math.abs(z1 - z0);
            double a0 = Math.min(xRun ? x0 : z0, xRun ? x1 : z1) - 0.3, a1 = Math.max(xRun ? x0 : z0, xRun ? x1 : z1) + 0.3;
            double c = xRun ? z0 : x0;
            for (int k = 0; k < 2; k++) {
                double o = k == 0 ? -0.3 : 0.3; int rgb = k == 0 ? WireNet.POWER_RGB : 0x1A1A1A;
                if (xRun) Prims.box(a0, 0, c + o - 0.27, a1, 0.7, c + o + 0.27, rgb);
                else Prims.box(c + o - 0.27, 0, a0, c + o + 0.27, 0.7, a1, rgb);
            }
        }

        /** one lane: offset o from the centre line, half-width hw, height h; arms to every joined side */
        private static void strand(boolean[] j, boolean any, double o, double hw, double h, int rgb) {
            double c = 8 + o;
            if (j[0]) Prims.box(c - hw, 0, 0, c + hw, h, c + hw, rgb);                // north
            if (j[1]) Prims.box(c - hw, 0, c - hw, c + hw, h, 16, rgb);              // south
            if (j[2]) Prims.box(0, 0, c - hw, c + hw, h, c + hw, rgb);               // west
            if (j[3]) Prims.box(c - hw, 0, c - hw, 16, h, c + hw, rgb);              // east
            if (!any) Prims.box(c - hw, 0, 4, c + hw, h, 12, rgb);                   // a loose end
        }
    }

    // ---- Grid Tap ---------------------------------------------------------------------------------------------
    public static class Tap extends TileEntitySpecialRenderer<TileTap> {
        @Override
        public void render(TileTap te, double x, double y, double z, float pt, int ds, float a) {
            World w = te.getWorld();
            if (w == null || x * x + y * y + z * z > 64 * 64) return;
            IBlockState s = w.getBlockState(te.getPos());
            if (!(s.getBlock() instanceof BlockTap)) return;
            AxisAlignedBB b = s.getBoundingBox(w, te.getPos());
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
            light(w, te.getPos());
            Prims.begin();
            double x0 = b.minX * 16, y0 = b.minY * 16, z0 = b.minZ * 16, x1 = b.maxX * 16, y1 = b.maxY * 16, z1 = b.maxZ * 16;
            Prims.box(x0, y0, z0, x1, y1, z1, 0x5C636B);                                   // the instrument transformer
            Prims.box(x0 - 0.15, y0 + (y1 - y0) * 0.35, z0 - 0.15, x1 + 0.15, y0 + (y1 - y0) * 0.65, z1 + 0.15, WireNet.RGB[te.channel]);   // its colour band
            Prims.box(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, 0xC9A043);           // brass terminal block showing at the ends
            double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            int col = WireNet.RGB[te.channel];
            for (EnumFacing d : EnumFacing.HORIZONTALS) {
                if (!(w.getTileEntity(te.getPos().offset(d)) instanceof TileWire tw) || !tw.has(te.channel)) continue;
                double o = WireNet.laneOffset(tw, te.channel);
                if (y0 > 0.01) Prims.box(cx - 0.42, 0, cz - 0.42, cx + 0.42, y0, cz + 0.42, col);   // down from the terminal
                Prims.box(cx - 0.9, Math.max(0, y0 - 0.6), cz - 0.9, cx + 0.9, y0 + 0.01, cz + 0.9, 0x1A1A1A);   // gland
                floorLead(d, cx, cz, o, 0.42, 0.7, col);
            }
            Prims.end();
            GlStateManager.popMatrix();
        }
    }

    // ---- Radio Transmitter / Receiver -------------------------------------------------------------------------
    public static class Radio extends TileEntitySpecialRenderer<TileRadio> {
        @Override
        public void render(TileRadio te, double x, double y, double z, float pt, int ds, float a) {
            World w = te.getWorld();
            if (w == null || x * x + y * y + z * z > 64 * 64) return;
            boolean rx = te.receiver();
            long now = w.getTotalWorldTime();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
            GlStateManager.disableCull();
            light(w, te.getPos());
            Prims.begin();
            Prims.box(3, 0, 3, 13, 10, 13, 0x3A3F46);                                       // cabinet
            for (EnumFacing d : EnumFacing.HORIZONTALS) {
                if (!(w.getTileEntity(te.getPos().offset(d)) instanceof TileWire tw)) continue;
                boolean along = d.getAxis() == EnumFacing.Axis.Z;
                double wall = d == EnumFacing.NORTH || d == EnumFacing.WEST ? 3 : 13;
                if (tw.bundled()) {
                    floorLead(d, along ? 8 : wall, along ? wall : 8, 0, 1.6, 2.6, 0x2E2E33);
                    Prims.box(along ? 6 : wall - 0.6, 0, along ? wall - 0.6 : 6, along ? 10 : wall + 0.6, 3.4, along ? wall + 0.6 : 10, 0x1A1A1A);
                } else for (int c = 0; c <= WireNet.POWER; c++) {
                    if (!tw.has(c)) continue;
                    double o = WireNet.laneOffset(tw, c);
                    if (c == WireNet.POWER) {
                        floorLead(d, along ? 8 + o - 0.3 : wall, along ? wall : 8 + o - 0.3, o - 0.3, 0.27, 0.7, WireNet.POWER_RGB);
                        floorLead(d, along ? 8 + o + 0.3 : wall, along ? wall : 8 + o + 0.3, o + 0.3, 0.27, 0.7, 0x1A1A1A);
                    } else floorLead(d, along ? 8 + o : wall, along ? wall : 8 + o, o, 0.42, 0.7, WireNet.RGB[c]);
                    Prims.box(along ? 8 + o - 0.8 : wall - 0.5, 0, along ? wall - 0.5 : 8 + o - 0.8, along ? 8 + o + 0.8 : wall + 0.5, 1.4, along ? wall + 0.5 : 8 + o + 0.8, 0x1A1A1A);
                }
            }
            Prims.box(3.4, 7.5, 2.8, 12.6, 9.4, 3, rx ? 0x2E7D32 : 0x8E2424);              // stripe: green RX, red TX
            Prims.box(6.5, 10, 6.5, 9.5, 11, 9.5, 0x222222);                               // antenna base
            Prims.box(7.6, 11, 7.6, 8.4, 28, 8.4, 0xB8BEC4);                               // mast
            for (int k = 0; k < 3; k++) Prims.box(5.5, 15 + k * 4, 7.7, 10.5, 15.4 + k * 4, 8.3, 0x9AA0A6);   // elements
            if (te.burnt) {                                                                 // burnt out: sooty cabinet, scorched vents
                Prims.box(2.9, 3.5, 2.9, 13.1, 10.1, 13.1, 0x15100C, 0.7f);
                Prims.box(5, 9.9, 5, 11, 10.15, 11, 0x0A0A0A);
            }
            boolean lit = te.pw == 1 && ((now + te.getPos().hashCode()) / 10 & 1) == 0;   // the beacon only blinks with 12 V on it
            if (lit) Prims.emissive(true);
            Prims.box(7.2, 28, 7.2, 8.8, 29.6, 8.8, lit ? (rx ? 0x30FF50 : 0xFF3030) : 0x402020);
            Prims.end();
            GlStateManager.enableLighting();
            light(w, te.getPos());
            // channel number on all four sides
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            String s = te.pw == 2 ? "BURNT" : te.pw == 0 ? "NO 12V" : (rx ? "RX " : "TX ") + te.channel;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.disableLighting();
            for (int i = 0; i < 4; i++) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(8, 5, 8);
                GlStateManager.rotate(90f * i, 0, 1, 0);
                GlStateManager.translate(0, 0, 5.05);
                GlStateManager.scale(0.12f, -0.12f, 0.12f);
                fr.drawString(s, -fr.getStringWidth(s) / 2, -4, te.pw == 1 ? 0x50FF70 : 0xFF5040);
                GlStateManager.popMatrix();
            }
            GlStateManager.enableLighting();
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }
    }

    // ---- Gauge Panel ------------------------------------------------------------------------------------------
    public static class Panel extends TileEntitySpecialRenderer<TileGaugePanel> {
        @Override
        public void render(TileGaugePanel te, double x, double y, double z, float pt, int ds, float a) {
            World w = te.getWorld();
            if (w == null || x * x + y * y + z * z > 64 * 64) return;
            IBlockState st = w.getBlockState(te.getPos());
            if (!(st.getBlock() instanceof BlockGaugePanel)) return;
            EnumFacing f = st.getValue(BlockGaugePanel.FACING);
            int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
            int lightFront = w.getCombinedLight(te.getPos().offset(f), 0);
            long now = w.getTotalWorldTime();
            boolean close = x * x + y * y + z * z < 10 * 10;

            // the body: whatever block it was disguised as, else plain grey steel
            IBlockState camo = te.camoState();
            if (camo != null) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, z + 1);                                   // renderBlockBrightness draws one block toward -z
                bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                RenderHelper.disableStandardItemLighting();
                int l = camoLight(w, te.getPos());
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
                Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlockBrightness(camo, 1.0f);
                RenderHelper.enableStandardItemLighting();
                GlStateManager.popMatrix();
            }

            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 0.5, y, z + 0.5);
            GlStateManager.rotate(-90f * turns, 0, 1, 0);
            GlStateManager.translate(-0.5, 0, -0.5);
            GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
            GlStateManager.disableCull();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightFront & 0xFFFF, lightFront >> 16);
            Prims.begin();
            if (camo == null) {
                Prims.box(0, 0, 0, 16, 16, 16, 0x4E555D);
                Prims.box(0.6, 0.6, -0.04, 15.4, 15.4, 0, 0x3A4047);                        // face plate
                Prims.box(7.85, 0.6, -0.07, 8.15, 15.4, -0.04, 0x30353B);                    // dividing seams between the holes
                Prims.box(0.6, 7.85, -0.07, 15.4, 8.15, -0.04, 0x30353B);
            }
            for (int i = 0; i < 4; i++) {
                double u0 = (i % 2) * 8, v0 = i < 2 ? 8 : 0, xc = 16 - (u0 + 4), vc = v0 + 4;
                Prims.box(xc - 2.6, vc - 2.6, -0.12, xc + 2.6, vc + 2.6, -0.06, 0x0A0A0A);   // the hole
                if (te.wire[i] >= 0 && !(w.getTileEntity(te.getPos().offset(f.getOpposite())) instanceof com.dogpound.railmap.wire.TileWireChase)) {
                    int col = WireNet.RGB[te.wire[i]];
                    if (te.inst[i] < 0) Prims.box(xc - 0.6, vc - 0.6, -0.5, xc + 0.6, vc + 0.6, -0.1, col);   // wire end poking through
                    Prims.box(xc - 1.3, vc - 1.3, 16, xc + 1.3, vc + 1.3, 16.35, 0x1A1A1A);  // grommet at the back
                    Prims.box(xc - 0.42, vc - 0.42, 16.05, xc + 0.42, vc + 0.42, 16.9, col);  // wire out of the back
                    Prims.box(xc - 0.42, 0, 16.5, xc + 0.42, vc + 0.42, 16.92, col);         // down the back
                    double xl = xc;                                                          // where the wire behind runs this colour
                    if (w.getTileEntity(te.getPos().offset(f.getOpposite())) instanceof TileWire tw && tw.has(te.wire[i]))
                        xl = 8 + (f == EnumFacing.NORTH || f == EnumFacing.EAST ? 1 : -1) * WireNet.laneOffset(tw, te.wire[i]);
                    Prims.box(Math.min(xc, xl) - 0.42, 0, 16.0, Math.max(xc, xl) + 0.42, 0.7, 16.92, col);   // along the foot of the back onto it
                }
            }
            if (w.getTileEntity(te.getPos().offset(f.getOpposite())) instanceof TileWire tp && tp.has(WireNet.POWER)) {
                double xl = 8 + (f == EnumFacing.NORTH || f == EnumFacing.EAST ? 1 : -1) * WireNet.laneOffset(tp, WireNet.POWER);
                Prims.box(6.4, 0, 16.0, 9.6, 2.4, 16.7, 0x4A4F55);                          // 12 V terminal box on the back
                Prims.box(6.7, 1.6, 16.7, 9.3, 2.0, 16.75, WireNet.POWER_RGB);              // its red "12 V" band
                for (int k = 0; k < 2; k++) {                                                // red + black down onto the pair
                    double xs = 7.6 + k * 0.8, xe = xl + (k == 0 ? -0.3 : 0.3);
                    Prims.box(Math.min(xs, xe) - 0.27, 0, 16.6, Math.max(xs, xe) + 0.27, 0.7, 16.92, k == 0 ? WireNet.POWER_RGB : 0x1A1A1A);
                }
            }
            Prims.end();
            for (int i = 0; i < 4; i++) {                                                    // the instruments over the holes
                if (te.inst[i] < 0) continue;
                double u0 = (i % 2) * 8, v0 = i < 2 ? 8 : 0;
                GlStateManager.pushMatrix();
                GlStateManager.translate(16 - u0 - 8, v0, -8);
                GlStateManager.scale(0.5, 0.5, 0.5);
                InstrumentRenderer.draw(BlockInstrument.Inst.values()[te.inst[i]], te.rd[i], now, pt, close, te.wire[i] >= 0, lightFront, now - te.pressedAt[i] < 6);
                GlStateManager.popMatrix();
            }
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }
    }

    /** the light a disguised block should get: the brightest of its open sides (inside a wall the light is 0 = black) */
    static int camoLight(World w, BlockPos p) {
        int best = 0, bestSum = -1;
        for (EnumFacing d : EnumFacing.values()) {
            int l = w.getCombinedLight(p.offset(d), 0), sum = (l & 0xFFFF) + (l >> 16);
            if (sum > bestSum) { bestSum = sum; best = l; }
        }
        return best;
    }

    // ---- Wire Chase Wall ----------------------------------------------------------------------------------------------
    public static class Chase extends TileEntitySpecialRenderer<com.dogpound.railmap.wire.TileWireChase> {
        @Override
        public void render(com.dogpound.railmap.wire.TileWireChase te, double x, double y, double z, float pt, int ds, float a) {
            World w = te.getWorld();
            if (w == null || x * x + y * y + z * z > 64 * 64) return;
            IBlockState camo = te.camoState();
            if (camo != null) {                                                       // disguised: draw the chosen block
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, z + 1);
                bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                RenderHelper.disableStandardItemLighting();
                int l = camoLight(w, te.getPos());
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l & 0xFFFF, l >> 16);
                Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlockBrightness(camo, 1.0f);
                RenderHelper.enableStandardItemLighting();
                GlStateManager.popMatrix();
            }
            // the little holes drilled through it: a rubber grommet, at the floor where a wire comes up to it, else mid-face
            if (te.holes == 0) return;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
            GlStateManager.disableCull();
            light(w, te.getPos());
            Prims.begin();
            for (EnumFacing d : EnumFacing.values()) {
                if (!te.hole(d)) continue;
                boolean floor = w.getTileEntity(te.getPos().offset(d)) instanceof TileWire;
                double y0 = floor ? 0.2 : 6.5, y1 = y0 + 3, c = 8;
                if (d == EnumFacing.UP || d == EnumFacing.DOWN) {
                    double yy = d == EnumFacing.UP ? 16 : -0.35;
                    Prims.box(6.3, yy, 6.3, 9.7, yy + 0.35, 9.7, 0x2A2A2A); Prims.box(7.0, d == EnumFacing.UP ? 16.35 : -0.37, 7.0, 9.0, d == EnumFacing.UP ? 16.37 : -0.35, 9.0, 0x050505);
                    continue;
                }
                boolean ns = d.getAxis() == EnumFacing.Axis.Z;
                double f0 = d == EnumFacing.NORTH || d == EnumFacing.WEST ? -0.35 : 16, f1 = f0 + 0.35;
                double out0 = d == EnumFacing.NORTH || d == EnumFacing.WEST ? f0 - 0.02 : f1, out1 = out0 + 0.02;
                if (ns) { Prims.box(c - 1.7, y0, f0, c + 1.7, y1, f1, 0x2A2A2A); Prims.box(c - 1.0, y0 + 0.6, out0, c + 1.0, y1 - 0.6, out1, 0x050505); }
                else { Prims.box(f0, y0, c - 1.7, f1, y1, c + 1.7, 0x2A2A2A); Prims.box(out0, y0 + 0.6, c - 1.0, out1, y1 - 0.6, c + 1.0, 0x050505); }
            }
            Prims.end();
            GlStateManager.enableCull();
            GlStateManager.popMatrix();
        }
    }
}
