package com.dogpound.railmap.client.render;

import com.dogpound.railmap.graph.Occupancy;
import com.dogpound.railmap.graph.RailNetwork;
import com.dogpound.railmap.graph.RailNode;
import com.dogpound.railmap.graph.SignalNode;
import com.dogpound.railmap.graph.StopNode;
import com.dogpound.railmap.graph.TrainNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * Draws a {@link RailNetwork} plus live trains into a {@code w}×{@code h} pixel rectangle whose
 * top-left is the current GL origin. Pure Tessellator quads/lines, so it works inside a GUI
 * (screen pixels) and inside a TESR (scaled onto a block face) alike. Everything is clipped
 * to the rectangle in software: a TESR has no scissor.
 * <p>
 * Screen y grows downward and world z grows south, so mapping z onto y puts north at the top.
 */
public final class MapRenderer {
    // Palette: phosphor-dark ground, cool grey track, hot colours only for state.
    public static final int COL_BG = 0xFF0E1216;
    public static final int COL_GRID = 0xFF161C22;
    public static final int COL_TRACK = 0xFFB4BCC4;
    public static final int COL_SLOPE = 0xFF8EB8E8;
    public static final int COL_SWITCH = 0xFFFFC44D;
    public static final int COL_SWITCH_INACTIVE = 0xFF7A5E1C;
    public static final int COL_CROSSING = 0xFFE07BE0;
    public static final int COL_OCCUPIED = 0xFFFF3B3B;
    public static final int COL_STOP = 0xFF4DD9E0;
    public static final int COL_STATION = 0xFF7CF29A;
    public static final int COL_ORIGIN = 0xFFFF4DA6;
    public static final int COL_TRAIN = 0xFFFFFFFF;
    public static final int COL_LOCO = 0xFFFFE066;
    public static final int COL_HOVER = 0xFFFFFFFF;
    public static final int COL_TEXT = 0xFFE8ECF0;
    public static final int COL_DIM = 0xFF8A94A0;

    /** What the cursor is over, if anything. */
    public static final class Pick {
        public RailNode node;
        public SignalNode signal;
        public StopNode stop;
        public TrainNode train;
        public String text = "";

        public boolean any() {
            return node != null || signal != null || stop != null || train != null;
        }
    }

    // Camera
    public double camX, camZ, zoom = 3;
    public int w, h;
    /** Draw station/stop labels. Off for tiny wall panels. */
    public boolean labels = true;
    public boolean grid = true;
    public BlockPos origin;

    /**
     * Fetched per draw, never cached in a field.
     *
     * This class is constructed in preInit (it is a field of the tile renderers), and at that
     * point Minecraft has not built its FontRenderer yet — a field initialiser captured null
     * forever and the first wall panel that drew text crashed with an NPE on this.font.
     */
    /**
     * Draw lines centred on the screen, scaled down until the widest one fits.
     *
     * Text was drawn at a fixed size regardless of how big the screen was, which is fine on
     * the dispatcher board and useless on a one-block wall panel. Scale is clamped so it
     * shrinks to fit but never becomes unreadable.
     */
    private void fitLines(String[] lines, int colour) {
        FontRenderer f = font();
        int widest = 1;
        for (String line : lines) widest = Math.max(widest, f.getStringWidth(line));

        float scale = Math.min(1f, (w - 8f) / widest);
        if (scale < 0.5f) scale = 0.5f;        // below this it is just fuzz

        int lineH = f.FONT_HEIGHT + 1;
        float blockH = lines.length * lineH * scale;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx(), cy() - blockH / 2.0, 0);
        GlStateManager.scale(scale, scale, 1f);
        for (int i = 0; i < lines.length; i++) {
            f.drawString(lines[i], -f.getStringWidth(lines[i]) / 2, i * lineH, colour);
        }
        GlStateManager.popMatrix();
    }

    private FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }
    private final Tessellator tess = Tessellator.getInstance();
    private final BufferBuilder buf = tess.getBuffer();

    public void fit(RailNetwork net, BlockPos fallback, double pad) {
        if (net.isEmpty()) {
            camX = fallback.getX() + 0.5;
            camZ = fallback.getZ() + 0.5;
            zoom = 3;
            return;
        }
        double spanX = Math.max(8, net.maxX - net.minX + pad * 2);
        double spanZ = Math.max(8, net.maxZ - net.minZ + pad * 2);
        zoom = clampZoom(Math.min(w / spanX, h / spanZ));
        camX = (net.minX + net.maxX) / 2;
        camZ = (net.minZ + net.maxZ) / 2;
    }

    public static double clampZoom(double z) {
        return Math.max(0.05, Math.min(32, z));
    }

    public double cx() { return w / 2.0; }
    public double cy() { return h / 2.0; }
    public double toScreenX(double wx) { return cx() + (wx - camX) * zoom; }
    public double toScreenY(double wz) { return cy() + (wz - camZ) * zoom; }
    public double toWorldX(double sx) { return camX + (sx - cx()) / zoom; }
    public double toWorldZ(double sy) { return camZ + (sy - cy()) / zoom; }

    /**
     * Draw everything. {@code mx,my} is the cursor in local pixels (or -1,-1 for none) and the
     * returned Pick says what it is over. {@code trains} may be empty.
     */
    public Pick draw(RailNetwork net, List<TrainNode> trains, int mx, int my) {
        Pick pick = new Pick();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GlStateManager.pushMatrix();
        rect(0, 0, w, h, COL_BG);
        if (grid) drawGrid();
        // Everything else a hair toward the viewer so lines never z-fight the background on a wall screen.
        GlStateManager.translate(0, 0, 0.4);

        if (net.isEmpty()) {
            GlStateManager.enableTexture2D();
            // A single-block panel is only 64 px across and this message is about 150,
            // so written at full size it ran straight off both edges. Split it and shrink
            // it to whatever the screen actually is.
            fitLines(new String[] { "No track", "within " + net.seedRadius + " blocks" }, COL_DIM);
            GlStateManager.disableTexture2D();
        } else {
            BitSet occupied = Occupancy.compute(net, trains);
            drawTrack(net, occupied, mx, my, pick);
            drawStops(net, mx, my, pick);
            drawSignals(net, mx, my, pick);
        }
        drawTrains(net, trains, mx, my, pick);
        if (origin != null) drawOrigin();
        GlStateManager.popMatrix();

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        return pick;
    }

    // ---- layers ------------------------------------------------------------------------

    private void drawGrid() {
        // 16-block (chunk) grid when it wouldn't be denser than every 12 px.
        double step = 16;
        while (step * zoom < 12) step *= 4;
        double x0 = Math.floor(toWorldX(0) / step) * step;
        double z0 = Math.floor(toWorldZ(0) / step) * step;
        GL11.glLineWidth(1f);
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (double x = x0; toScreenX(x) <= w; x += step) {
            double sx = toScreenX(x);
            if (sx < 0) continue;
            vertex(sx, 0, COL_GRID); vertex(sx, h, COL_GRID);
        }
        for (double z = z0; toScreenY(z) <= h; z += step) {
            double sy = toScreenY(z);
            if (sy < 0) continue;
            vertex(0, sy, COL_GRID); vertex(w, sy, COL_GRID);
        }
        tess.draw();
    }

    private void drawTrack(RailNetwork net, BitSet occupied, int mx, int my, Pick pick) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        RailNode hover = null;
        double bestD = 36; // px², within 6 px
        // Three passes: plain track, switches on top so junctions read, occupied on top of all.
        for (int pass = 0; pass < 3; pass++) {
            GL11.glLineWidth(pass == 0 ? lineWidth(2f) : lineWidth(3f));
            buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (RailNode n : net.nodes) {
                boolean occ = occupied.get(n.id);
                boolean switchy = n.kind == RailNode.Kind.SWITCH || n.isSwitchLeg();
                int want = occ ? 2 : switchy ? 1 : 0;
                if (want != pass) continue;
                int col = occ ? COL_OCCUPIED : colorOf(n);
                float[] p = n.points;
                if (p.length < 6) continue;
                for (int i = 3; i < p.length; i += 3) {
                    double x0 = toScreenX(p[i - 3]), y0 = toScreenY(p[i - 1]);
                    double x1 = toScreenX(p[i]), y1 = toScreenY(p[i + 2]);
                    line(x0, y0, x1, y1, col);
                    if (mx >= 0) {
                        double d = segDistSq(mx, my, x0, y0, x1, y1);
                        if (d < bestD) { bestD = d; hover = n; }
                    }
                }
            }
            tess.draw();
        }

        // Point-only pieces (crossings, turntables), switch dots, and the hovered piece.
        GL11.glLineWidth(lineWidth(2f));
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (RailNode n : net.nodes) {
            if (n.points.length >= 6) {
                if (n.kind == RailNode.Kind.SWITCH) {
                    // Small ring at the switch's toe: the clickable point.
                    circle(toScreenX(n.points[0]), toScreenY(n.points[2]), Math.max(2.5, 0.8 * zoom),
                            occupied.get(n.id) ? COL_OCCUPIED : COL_SWITCH);
                }
                continue;
            }
            double sx = toScreenX(n.points[0]), sy = toScreenY(n.points[2]);
            if (n.kind == RailNode.Kind.TABLE) {
                circle(sx, sy, Math.max(3, n.length * zoom), occupied.get(n.id) ? COL_OCCUPIED : COL_TRACK);
            } else {
                double r = Math.max(3, 1.5 * zoom);
                line(sx - r, sy - r, sx + r, sy + r, COL_CROSSING);
                line(sx - r, sy + r, sx + r, sy - r, COL_CROSSING);
            }
            if (mx >= 0) {
                double dx = sx - mx, dy = sy - my;
                if (dx * dx + dy * dy < bestD) { bestD = dx * dx + dy * dy; hover = n; }
            }
        }
        if (hover != null && hover.points.length >= 6) {
            float[] p = hover.points;
            for (int i = 3; i < p.length; i += 3) {
                line(toScreenX(p[i - 3]), toScreenY(p[i - 1]), toScreenX(p[i]), toScreenY(p[i + 2]), COL_HOVER);
            }
        }
        tess.draw();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);

        if (hover != null) {
            pick.node = hover;
            pick.text = describe(hover, net, occupied.get(hover.id));
        }
    }

    private static int colorOf(RailNode n) {
        switch (n.kind) {
            case SWITCH:
                return n.switchState == RailNode.SWITCH_TURN ? COL_SWITCH_INACTIVE : COL_SWITCH;
            case SLOPE: return COL_SLOPE;
            case CROSSING: return COL_CROSSING;
            default:
                if (n.isSwitchLeg()) {
                    return n.switchState == RailNode.SWITCH_TURN ? COL_SWITCH : COL_SWITCH_INACTIVE;
                }
                return COL_TRACK;
        }
    }

    private void drawSignals(RailNetwork net, int mx, int my, Pick pick) {
        int r = (int) Math.max(2, Math.min(5, zoom));
        for (SignalNode s : net.signals) {
            double sx = toScreenX(s.pos.getX() + 0.5), sy = toScreenY(s.pos.getZ() + 0.5);
            if (!inside(sx, sy, r + 1)) continue;
            int col = switch (s.aspect) {
                case RED -> 0xFFFF3B3B;
                case YELLOW -> 0xFFFFD23B;
                case GREEN -> 0xFF3BFF5C;
                default -> 0xFF777777;
            };
            rect(sx - r - 1, sy - r - 1, sx + r + 1, sy + r + 1, 0xFF000000);
            rect(sx - r, sy - r, sx + r, sy + r, col);
            if (mx >= 0 && Math.abs(mx - sx) <= r + 1 && Math.abs(my - sy) <= r + 1) {
                pick.signal = s;
                pick.text = "Signal " + s.aspect + " @ " + s.pos.getX() + "," + s.pos.getY() + "," + s.pos.getZ()
                        + (s.rawState.isEmpty() ? "" : "  [" + s.rawState + "]");
            }
        }
    }

    private void drawStops(RailNetwork net, int mx, int my, Pick pick) {
        int r = (int) Math.max(3, Math.min(6, zoom));
        int hit = Math.max(6, r + 2); // the square is tiny when zoomed out; give clicks some room
        for (StopNode s : net.stops) {
            double sx = toScreenX(s.pos.getX() + 0.5), sy = toScreenY(s.pos.getZ() + 0.5);
            if (!inside(sx, sy, r + 40)) continue;
            int col = s.named ? COL_STATION : COL_STOP;
            if (s.kind == StopNode.Kind.STATION || s.named) {
                // Station: filled square with a dark border, reads as a "building".
                rect(sx - r - 1, sy - r - 1, sx + r + 1, sy + r + 1, 0xFF000000);
                rect(sx - r, sy - r, sx + r, sy + r, col);
            } else {
                rect(sx - r, sy - 1, sx + r, sy + 1, col);
                rect(sx - 1, sy - r, sx + 1, sy + r, col);
            }
            String label = s.named ? s.name : pretty(s.name);
            if (labels && zoom >= 1.5 && inside(sx, sy, 0)) text(label, sx + r + 2, sy - 4, col);
            if (mx >= 0 && Math.abs(mx - sx) <= hit && Math.abs(my - sy) <= hit) {
                pick.stop = s;
                pick.text = (s.named ? "Station: " : "Stop: ") + label
                        + (s.named ? "" : " (" + s.kind.name().toLowerCase(Locale.ROOT) + ")")
                        + " @ " + s.pos.getX() + "," + s.pos.getY() + "," + s.pos.getZ();
            }
        }
    }

    private void drawTrains(RailNetwork net, List<TrainNode> trains, int mx, int my, Pick pick) {
        if (trains.isEmpty()) return;
        double size = Math.max(3, Math.min(8, 1.2 * zoom));
        double bestD = 64;
        TrainNode hover = null;
        // Pass 1: bodies
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (TrainNode t : trains) {
            double sx = toScreenX(t.x), sy = toScreenY(t.z);
            if (!inside(sx, sy, size + 2)) continue;
            int col = t.kind.isLoco() ? COL_LOCO : COL_TRAIN;
            double a = Math.toRadians(t.yaw);
            double ca = Math.cos(a), sa = Math.sin(a);
            if (t.moving()) {
                // Arrow head pointing along the motion vector.
                tri(sx + ca * size, sy + sa * size,
                        sx - ca * size * 0.7 - sa * size * 0.6, sy - sa * size * 0.7 + ca * size * 0.6,
                        sx - ca * size * 0.7 + sa * size * 0.6, sy - sa * size * 0.7 - ca * size * 0.6, col);
            } else {
                // Stopped: a square, two triangles.
                double s = size * 0.6;
                tri(sx - s, sy - s, sx + s, sy - s, sx + s, sy + s, col);
                tri(sx - s, sy - s, sx + s, sy + s, sx - s, sy + s, col);
            }
            if (mx >= 0) {
                double dx = sx - mx, dy = sy - my;
                if (dx * dx + dy * dy < bestD) { bestD = dx * dx + dy * dy; hover = t; }
            }
        }
        tess.draw();
        // Pass 2: labels on lead units
        if (labels && zoom >= 1.5) {
            for (TrainNode t : trains) {
                if (!t.lead) continue;
                double sx = toScreenX(t.x), sy = toScreenY(t.z);
                if (!inside(sx, sy, 0)) continue;
                String l = t.displayName();
                if (t.moving()) l += " " + Math.round(Math.abs(t.speedKmh)) + "km/h";
                text(l, sx + size + 2, sy + 2, t.kind.isLoco() ? COL_LOCO : COL_TRAIN);
            }
        }
        if (hover != null) {
            pick.train = hover;
            pick.text = trainLine(hover, net);
        }
    }

    public static String trainLine(TrainNode t, RailNetwork net) {
        StringBuilder sb = new StringBuilder(t.displayName());
        if (!t.tag.isEmpty()) sb.append(" (").append(t.name).append(')');
        sb.append("  ").append(pretty(t.kind.name()));
        if (t.consist > 1) sb.append("  ").append(t.consist).append(" cars");
        sb.append("  ").append(Math.round(Math.abs(t.speedKmh))).append(" km/h");
        if (t.kind.isLoco()) {
            sb.append("  thr ").append(Math.round(t.throttle * 100)).append('%');
            sb.append("  rev ").append(t.reverser > 0.05 ? "F" : t.reverser < -0.05 ? "R" : "N");
            sb.append("  brk ").append(Math.round(t.brake * 100)).append('%');
        }
        if (t.cargoPct >= 0) sb.append("  cargo ").append(t.cargoPct).append('%');
        if (t.passengers > 0) sb.append("  pax ").append(t.passengers);
        if (!t.heading.isEmpty()) sb.append("  -> ").append(t.heading);
        else {
            StopNode at = net.nearestStop(t.x, t.y, t.z, 6, true);
            if (at != null) sb.append("  at ").append(at.name);
        }
        return sb.toString();
    }

    private void drawOrigin() {
        double sx = toScreenX(origin.getX() + 0.5), sy = toScreenY(origin.getZ() + 0.5);
        if (!inside(sx, sy, 4)) return;
        rect(sx - 4, sy - 4, sx + 4, sy + 4, 0xFF000000);
        rect(sx - 3, sy - 3, sx + 3, sy + 3, COL_ORIGIN);
        if (labels) text("YOU", sx + 6, sy - 4, COL_ORIGIN);
    }

    /** North arrow + scale bar, drawn by callers that want chrome. */
    public void drawChrome() {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 0.8);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        int nx = w - 14, ny = 8;
        rect(nx - 1, ny, nx + 1, ny + 12, 0xFFFFFFFF);
        rect(nx - 3, ny + 2, nx + 3, ny + 3, 0xFFFFFFFF);
        int[] nice = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000};
        int blocks = nice[nice.length - 1];
        for (int n : nice) if (n * zoom >= 40) { blocks = n; break; }
        int bw = (int) (blocks * zoom);
        int bx = 6, by = h - 8;
        rect(bx, by, bx + bw, by + 2, 0xFFFFFFFF);
        rect(bx, by - 3, bx + 1, by + 2, 0xFFFFFFFF);
        rect(bx + bw - 1, by - 3, bx + bw, by + 2, 0xFFFFFFFF);
        GlStateManager.enableTexture2D();
        font().drawStringWithShadow("N", nx - 3, ny - 10, 0xFFFFFFFF);
        font().drawStringWithShadow(blocks + " blocks", bx, by - 13, 0xFFFFFFFF);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static String describe(RailNode n, RailNetwork net, boolean occupied) {
        StringBuilder sb = new StringBuilder();
        sb.append(pretty(n.kind.name())).append(" @ ").append(n.pos.getX()).append(',').append(n.pos.getY()).append(',').append(n.pos.getZ());
        sb.append("  len ").append(n.length);
        sb.append("  gauge ").append(n.gaugeMm).append("mm");
        if (n.kind == RailNode.Kind.SWITCH || n.isSwitchLeg()) {
            sb.append("  switch: ").append(n.switchState == RailNode.SWITCH_TURN ? "TURN"
                    : n.switchState == RailNode.SWITCH_STRAIGHT ? "STRAIGHT" : "?");
            if (n.kind == RailNode.Kind.SWITCH) sb.append("  [click to throw]");
        }
        if (occupied) sb.append("  OCCUPIED");
        return sb.toString();
    }

    /** "ITEM_LOADER" → "Item loader". */
    public static String pretty(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "";
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- primitives, all clipped to [0,w]×[0,h] ------------------------------------------

    private boolean inside(double x, double y, double margin) {
        return x >= -margin && x <= w + margin && y >= -margin && y <= h + margin;
    }

    private float lineWidth(float px) {
        return px;
    }

    private void text(String s, double x, double y, int col) {
        int tw = font().getStringWidth(s);
        if (x < 0 || y < 0 || x + tw > w || y + 9 > h) return;
        GlStateManager.enableTexture2D();
        font().drawStringWithShadow(s, (float) x, (float) y, col);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
    }

    /** Filled rect, clipped. Begins/ends its own draw. */
    public void rect(double x0, double y0, double x1, double y1, int col) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0);
        x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        if (x1 <= x0 || y1 <= y0) return;
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        vertex(x0, y1, col); vertex(x1, y1, col); vertex(x1, y0, col); vertex(x0, y0, col);
        tess.draw();
    }

    /** Triangle inside an open GL_TRIANGLES batch; dropped whole if any corner is outside. */
    private void tri(double x0, double y0, double x1, double y1, double x2, double y2, int col) {
        if (!inside(x0, y0, 0) || !inside(x1, y1, 0) || !inside(x2, y2, 0)) return;
        vertex(x0, y0, col); vertex(x1, y1, col); vertex(x2, y2, col);
    }

    /** Line segment inside an open GL_LINES batch, Liang–Barsky clipped to the rect. */
    private void line(double x0, double y0, double x1, double y1, int col) {
        double dx = x1 - x0, dy = y1 - y0;
        double t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0, w - x0, y0, h - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return;
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0) { if (t > t1) return; if (t > t0) t0 = t; }
                else { if (t < t0) return; if (t < t1) t1 = t; }
            }
        }
        vertex(x0 + t0 * dx, y0 + t0 * dy, col);
        vertex(x0 + t1 * dx, y0 + t1 * dy, col);
    }

    private void circle(double cx, double cy, double r, int col) {
        int segs = 20;
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            line(cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, col);
        }
    }

    private void vertex(double x, double y, int argb) {
        buf.pos(x, y, 0).color((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, (argb >>> 24) & 0xff).endVertex();
    }

    public static double segDistSq(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = dx * dx + dy * dy;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / len));
        double ex = x0 + t * dx - px, ey = y0 + t * dy - py;
        return ex * ex + ey * ey;
    }
}
