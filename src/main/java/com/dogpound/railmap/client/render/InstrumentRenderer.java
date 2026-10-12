package com.dogpound.railmap.client.render;

import com.dogpound.railmap.grid.BlockInstrument;
import com.dogpound.railmap.grid.TileInstrument;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

import java.util.Locale;

/**
 * Switchboard instruments (requested feature): a square black bezel with a cream dial, 90° scale, ticks, figures and a
 * needle that eases to the reading; the readout screen; the status lamp. Built in model pixels facing north (front at
 * z = 13, wall behind at z = 16), turned like the block; u runs from the viewer's left, v up.
 */
public class InstrumentRenderer extends TileEntitySpecialRenderer<TileInstrument> {
    private static final double FACE = 13;

    @Override
    public void render(TileInstrument te, double x, double y, double z, float pt, int destroyStage, float alpha) {
        if (te.getWorld() == null || x * x + y * y + z * z > 48 * 48) return;
        IBlockState st = te.getWorld().getBlockState(te.getPos());
        if (!(st.getBlock() instanceof BlockInstrument b)) return;
        EnumFacing f = st.getValue(BlockInstrument.FACING);
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        long now = te.getWorld().getTotalWorldTime();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(-90f * turns, 0, 1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        GlStateManager.disableCull();
        int light = te.getWorld().getCombinedLight(te.getPos().offset(f), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
        draw(b.inst, te.r, now, pt, x * x + y * y + z * z < 14 * 14, te.target() != null || te.wire >= 0, light, now - te.pressedAt < 6);
        boolean chase = te.getWorld().getTileEntity(te.getPos().offset(f.getOpposite())) instanceof com.dogpound.railmap.wire.TileWireChase;   // wired in at the back
        if (te.wire >= 0 && !chase) box(6.6, 0.2, 9.4, 1.0, -2.6, -0.2, 0x111111);           // the hole (gland) in the bottom of the case
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
        if (chase) return;                                                        // the wires run inside the wall: nothing down the front
        if (te.wire >= 0) wireLead(te, f, x, y, z, te.wire, com.dogpound.railmap.wire.WireNet.RGB[te.wire], 0, 0);
        wireLead(te, f, x, y, z, com.dogpound.railmap.wire.WireNet.POWER, com.dogpound.railmap.wire.WireNet.POWER_RGB, 1.5, -0.3);   // the 12 V pair: red...
        wireLead(te, f, x, y, z, com.dogpound.railmap.wire.WireNet.POWER, 0x1A1A1A, 2.2, 0.3);                                      // ...and black
    }

    /**
     * One instrument (case, dial / screen / lamp, needle, figures) in model pixels: front at z = FACE (13), the wall at
     * z = 16, u from the viewer's left, v up. Callers set up the GL transform (a whole block, or a Gauge Panel hole).
     */
    public static void draw(BlockInstrument.Inst inst, com.dogpound.railmap.grid.Reading rd, long now, float pt, boolean close, boolean linked, int light) {
        draw(inst, rd, now, pt, close, linked, light, false);
    }

    /** pressed: a Control Button's cap is pushed in (for a few ticks after a click) */
    public static void draw(BlockInstrument.Inst inst, com.dogpound.railmap.grid.Reading rd, long now, float pt, boolean close, boolean linked, int light, boolean pressed) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        Prims.begin();
        box(1, 1, 15, 15, -3, 0, 0x1C1C1C);                                     // case, 3 px off the wall
        switch (inst) {
            case SCREEN: box(2, 2.5, 14, 13.5, 0, 0.1, 0x050A06); break;
            case LAMP: {
                boolean lit = rd.pw == 1 && (rd.lamp == 1 || rd.lamp == 2 || (rd.lamp == 3 && ((now / 8) & 1) == 0));
                int col = rd.lamp == 1 ? 0x22C04A : rd.lamp == 2 ? 0xD02020 : 0xF0A020;
                box(4, 4.5, 12, 12.5, 0, 0.4, 0x303030);                             // bezel ring
                if (lit) {
                    Prims.emissive(true);
                    box(5, 5.5, 11, 11.5, 0.4, 1.4, col);
                    Prims.glow(16 - 8, 8.5, FACE - 1.5, 6, col);
                    GlStateManager.enableLighting();
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
                } else box(5, 5.5, 11, 11.5, 0.4, 1.4, Prims.shade(rd.lamp == 0 ? 0x555555 : col, 0.35f));
                break;
            }
            case BUTTON: {
                boolean knob = !rd.line3.isEmpty();                                  // a selector / key switch: a knob, not a cap
                box(2.5, 11.4, 13.5, 14.2, 0, 0.15, 0xE8E2CC);                      // engraved legend plate
                box(4.2, 3.6, 11.8, 10.8, 0, 0.5, 0xB4B6B8);                        // chrome bezel
                box(4.8, 4.2, 11.2, 10.2, 0.5, 0.6, 0x101010);                      // the hole round the cap
                if (knob) {
                    box(5.6, 5.0, 10.4, 9.4, 0.5, 2.0, 0x1E1E1E);                    // knob
                    double a = -45 + 90 * Math.max(0, Math.min(1, rd.value / Math.max(1e-9, rd.full)));
                    bar(8, 7.2, a, 0.32, -0.4, 2.0, 2.0, 2.15, 0xF0F0F0);           // white pointer on the knob
                    for (int i = 0; i <= (int) rd.full; i++) bar(8, 7.2, -45 + 90.0 * i / Math.max(1, rd.full), 0.12, 3.6, 4.2, 0.5, 0.65, 0xE0E0E0);   // position marks
                } else {
                    double d = pressed ? 0.9 : 1.9;
                    int cap = rd.pw == 2 ? Prims.shade(rd.col, 0.3f) : rd.col;           // a burnt one is blackened
                    box(5.2, 4.6, 10.8, 9.8, 0.6, 0.6 + d, cap);                      // the cap
                    box(5.8, 5.2, 10.2, 9.2, 0.6 + d, 0.75 + d, Prims.shade(cap, 1.25f));   // its domed top catching the light
                }
                boolean lit = rd.pw == 1 && (rd.lamp == 1 || rd.lamp == 2 || (rd.lamp == 3 && ((now / 8) & 1) == 0));
                int lc = rd.lamp == 1 ? 0x22C04A : rd.lamp == 2 ? 0xD02020 : 0xF0A020;
                box(12.3, 7.4, 14.3, 9.4, 0, 0.35, 0x303030);                         // pilot light: the machine runs / stopped / fault
                if (lit) {
                    Prims.emissive(true);
                    box(12.7, 7.8, 13.9, 9.0, 0.35, 0.9, lc);
                    Prims.glow(16 - 13.3, 8.4, FACE - 1.0, 2.2, lc);
                    GlStateManager.enableLighting();
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16);
                } else box(12.7, 7.8, 13.9, 9.0, 0.35, 0.9, Prims.shade(rd.lamp == 0 ? 0x555555 : lc, 0.35f));
                break;
            }
            default: {
                box(2, 2, 14, 14, 0, 0.1, 0xEFE9D8);                                // cream dial
                // scale: 90° arc, pivot low and centred (classic switchboard meter)
                double cu = 8, cv = 3.2, r = 9.2;
                for (int i = 0; i <= 10; i++) {
                    double a = Math.toRadians(-45 + 9 * i);
                    double len = i % 5 == 0 ? 1.3 : 0.7;
                    bar(cu, cv, Math.toDegrees(a), 0.07, r - len, r, 0.1, 0.18, 0x111111);
                }
                bar(cu, cv, -45, 0.06, r - 0.15, r, 0.1, 0.15, 0x111111);
                // red zone from 100 % to 125 % of the nominal span (full scale is 125 %)
                for (int i = 8; i <= 10; i++) bar(cu, cv, -45 + 9 * i, 0.22, r + 0.15, r + 0.55, 0.1, 0.16, 0xC01818);
                // needle eases to the reading
                double frac = Math.max(0, Math.min(1.05, rd.value / Math.max(1e-9, rd.full)));
                double target = -45 + 90 * frac;
                double tNow = now + pt;
                if (rd.needleAt == 0) { rd.needle = (float) target; rd.needleAt = (long) tNow; }
                double dt = Math.max(0, Math.min(5, tNow - rd.needleAt));
                rd.needleAt = (long) tNow;
                double step = 6 * rd.damp * Math.max(dt, 0.05);
                rd.needle += (float) Math.max(-step, Math.min(step, target - rd.needle));
                bar(cu, cv, rd.needle, 0.12, -0.8, r - 0.4, 0.25, 0.4, frac > 1 ? 0xD01010 : 0x0C0C0C);
                if (rd.hiFrac > 0) bar(cu, cv, -45 + 90 * Math.min(1.25, rd.hiFrac), 0.28, r - 0.2, r + 0.9, 0.15, 0.3, 0xE01010);   // high setpoint
                if (rd.loFrac > 0) bar(cu, cv, -45 + 90 * Math.min(1.25, rd.loFrac), 0.28, r - 0.2, r + 0.9, 0.15, 0.3, 0x1E50E0);                     // low setpoint
                if (rd.peakFrac >= 0) bar(cu, cv, -45 + 90 * Math.min(1.05, rd.peakFrac), 0.07, 0, r - 1.2, 0.18, 0.24, 0xD02020);             // peak-hold drag pointer
                if (rd.pw == 1 && rd.alarm && ((now / 6) & 1) == 0) { Prims.emissive(true); box(12.6, 12.6, 14.2, 14.2, 0, 0.5, 0xFF2020);
                    GlStateManager.enableLighting(); OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >> 16); }
                else box(12.6, 12.6, 14.2, 14.2, 0, 0.3, rd.hiFrac > 0 || rd.loFrac > 0 ? 0x501010 : 0x1C1C1C);                                // alarm lamp
                box(cu - 0.55, cv - 0.55, cu + 0.55, cv + 0.55, 0.1, 0.55, 0x2A2A2A);       // pivot boss
                break;
            }
        }
        if (rd.pw == 2) {                                                         // burnt out: soot over the face, the glass cracked
            double sd = inst == BlockInstrument.Inst.BUTTON ? 0.62 : 0.5;            // just over the glass / round the cap
            Prims.box(16 - 14.4, 1.6, FACE - sd - 0.1, 16 - 1.6, 14.4, FACE - sd, 0x15100C, 0.78f);
            double[][] cracks = {{5, 9, 35, 4.2}, {5, 9, 160, 3.4}, {5, 9, 250, 2.8}, {11, 6, 80, 3.0}, {11, 6, 200, 3.6}, {9, 12, 300, 2.4}};
            for (double[] c : cracks) bar(c[0], c[1], c[2], 0.06, 0, c[3], sd + 0.1, sd + 0.18, 0xBDBDBD);
            box(4, 14.2, 12, 15.6, 0, 0.32, 0x0A0A0A);                                // scorch on the top of the case
        }
        box(1, 1, 15, 1.6, 0, 0.3, 0x2A2A2A); box(1, 14.4, 15, 15, 0, 0.3, 0x2A2A2A); // bezel lips
        box(1, 1, 1.6, 15, 0, 0.3, 0x2A2A2A); box(14.4, 1, 15, 15, 0, 0.3, 0x2A2A2A);
        Prims.end();

        if (close) {
            GlStateManager.disableLighting();
            switch (inst) {
                case SCREEN:
                    if (rd.pw != 1) break;                                              // dark: no 12 V / burnt out
                    text(fr, rd.line1, 8, 11.0, 0.95, 11.4, 0x50FF70, true);
                    text(fr, rd.line2, 8, 8.2, 0.95, 11.4, 0x50FF70, true);
                    text(fr, rd.line3, 8, 5.4, 0.95, 11.4, 0x50FF70, true);
                    text(fr, rd.status.length() > 26 ? rd.status.substring(0, 26) : rd.status, 8, 3.2, 0.7, 11.4, rd.status.equals("LIVE") ? 0x50FF70 : 0xFF6050, true);
                    break;
                case LAMP: text(fr, inst.label.toUpperCase(Locale.ROOT), 8, 2.3, 0.75, 13, 0xDDDDDD, false); break;
                case BUTTON:
                    text(fr, rd.line1.toUpperCase(Locale.ROOT), 8, 12.8, 1.0, 10.4, 0x151515, false);
                    if (!rd.line3.isEmpty()) text(fr, rd.line3.toUpperCase(Locale.ROOT), 8, 11.0, 0.6, 5, 0xFFFFFF, false);
                    text(fr, linked ? rd.line2 : "NO WIRE", 8, 2.2, 0.62, 12.5, linked ? 0xC8C8C8 : 0xD04040, false);
                    break;
                default: {
                    double cu = 8, cv = 3.2, r = 9.2;
                    String[] figs = {"0", fig(rd.full * 0.4, rd.unit), fig(rd.full * 0.8, rd.unit)};
                    double[] at = {-45, -9, 27};
                    for (int i = 0; i < 3; i++) {
                        double a = Math.toRadians(at[i]);
                        text(fr, figs[i], cu + Math.sin(a) * (r - 2.4), cv + Math.cos(a) * (r - 2.4) - 0.35, 0.65, 4, 0x111111, false);
                    }
                    text(fr, rd.unit, 8, 8.0, 1.3, 6, 0x111111, false);
                    text(fr, inst.label.toUpperCase(Locale.ROOT), 8, 2.4, 0.55, 10, 0x333333, false);
                    if (rd.pw != 1) text(fr, rd.pw == 2 ? "BURNT OUT" : "NO 12 V", 8, 6.2, 0.6, 10, 0xB01010, false);
                    else if (!linked) text(fr, "NOT LINKED", 8, 6.2, 0.6, 10, 0xB01010, false);
                    break;
                }
            }
            GlStateManager.enableLighting();
        }
    }

    /** the wire out of the hole in the case: down to the floor, onto the lane of the wire beside it (or straight down to one below) */
    private static void wireLead(TileInstrument te, EnumFacing f, double x, double y, double z, int c, int col, double du, double lane) {
        net.minecraft.world.World w = te.getWorld();
        boolean any = w.getTileEntity(te.getPos().down()) instanceof com.dogpound.railmap.wire.TileWire t0 && t0.has(c);
        for (EnumFacing d : EnumFacing.HORIZONTALS) any |= w.getTileEntity(te.getPos().offset(d)) instanceof com.dogpound.railmap.wire.TileWire t1 && t1.has(c);
        if (!any) return;
        double wx = f == EnumFacing.EAST ? 1.5 : f == EnumFacing.WEST ? 14.5 : 8, wz = f == EnumFacing.NORTH ? 14.5 : f == EnumFacing.SOUTH ? 1.5 : 8;
        if (f.getAxis() == EnumFacing.Axis.Z) wx += du; else wz += du;                 // leads side by side under the case
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);
        Prims.begin();
        Prims.box(wx - 0.42, 0, wz - 0.42, wx + 0.42, 1.0, wz + 0.42, col);
        for (EnumFacing d : EnumFacing.HORIZONTALS)
            if (w.getTileEntity(te.getPos().offset(d)) instanceof com.dogpound.railmap.wire.TileWire tw && tw.has(c))
                WireRenderers.floorLead(d, wx, wz, com.dogpound.railmap.wire.WireNet.laneOffset(tw, c) + lane, 0.42, 0.7, col);
        if (w.getTileEntity(te.getPos().down()) instanceof com.dogpound.railmap.wire.TileWire tw && tw.has(c)) {
            double o = com.dogpound.railmap.wire.WireNet.laneOffset(tw, c) + lane;
            Prims.box(wx - 0.42, -16 + 0.7, wz - 0.42, wx + 0.42, 0, wz + 0.42, col);           // down through the floor of this block
            net.minecraft.util.math.BlockPos b = te.getPos().down();
            boolean alongX = com.dogpound.railmap.wire.WireNet.joins(w, b, EnumFacing.EAST, tw) || com.dogpound.railmap.wire.WireNet.joins(w, b, EnumFacing.WEST, tw);
            if (alongX) Prims.box(wx - 0.42, -16, Math.min(wz, 8 + o) - 0.42, wx + 0.42, -16 + 0.7, Math.max(wz, 8 + o) + 0.42, col);   // out to the run's lane
            else Prims.box(Math.min(wx, 8 + o) - 0.42, -16, wz - 0.42, Math.max(wx, 8 + o) + 0.42, -16 + 0.7, wz + 0.42, col);
        }
        Prims.end();
        GlStateManager.popMatrix();
    }

    /** scale figure: the value converted to the meter's unit */
    private static String fig(double v, String unit) {
        double k = unit.startsWith("k") ? 1e3 : unit.startsWith("M") ? 1e6 : 1;
        if (unit.equals("PF")) return String.format(Locale.ROOT, "%.1f", v);
        double s = v / k;
        return s >= 100 ? String.format(Locale.ROOT, "%.0f", s) : s >= 10 ? String.format(Locale.ROOT, "%.0f", s) : String.format(Locale.ROOT, "%.1f", s);
    }

    private static void box(double u0, double v0, double u1, double v1, double d0, double d1, int rgb) {
        Prims.box(16 - u1, v0, FACE - d1, 16 - u0, v1, FACE - d0, rgb);
    }

    /** a bar from (u, v) turned `ang` degrees clockwise from up, from radius r0 to r1 */
    private static void bar(double u, double v, double ang, double hw, double r0, double r1, double d0, double d1, int rgb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - u, v, 0);
        GlStateManager.rotate((float) ang, 0, 0, 1);
        Prims.box(-hw, r0, FACE - d1, hw, r1, FACE - d0, rgb);
        GlStateManager.popMatrix();
    }

    private static void text(FontRenderer fr, String s, double u, double v, double heightPx, double maxW, int rgb, boolean glow) {
        if (s == null || s.isEmpty()) return;
        int w = fr.getStringWidth(s);
        double h = Math.min(heightPx, maxW * 8.0 / Math.max(1, w));
        float k = (float) (h / 8.0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(16 - u, v + h / 2, FACE - 0.3);
        GlStateManager.rotate(180f, 0, 0, 1);
        GlStateManager.scale(k, k, k);
        if (glow) OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        fr.drawString(s, -w / 2, 0, rgb);
        GlStateManager.popMatrix();
    }
}
