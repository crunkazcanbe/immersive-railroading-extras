package com.dogpound.railmap.cctv.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.cctv.*;
import com.dogpound.railmap.client.gui.PrideFrame;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;

/**
 * The monitor's on-screen menu: the camera list of its network (status, recording), split 1 / 4 / 9 and which tile
 * you're setting, overlay, fisheye dewarp, and PLAYBACK of the selected tile's camera from its NVR recording: the
 * timeline with bookmarks (alarms, motion, doorbell, operator marks) - click to jump - play / pause, speed, frame step,
 * back to LIVE, and export the picture to a map.
 */
public class GuiMonitor extends CctvGui {
    private final BlockPos pos;
    private int scroll;
    private long askedRange;

    public GuiMonitor(BlockPos pos) { this.pos = pos; }

    private TileMonitor mon() { return mc.world.getTileEntity(pos) instanceof TileMonitor m ? m.master() : null; }

    private void op(String what) { RailMap.NETWORK.sendToServer(new PacketCctvOp(PacketCctvOp.MONITOR, 0, pos.toLong(), 0, what)); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float pt) {
        mx = mouseX; my = mouseY;
        startHits();
        TileMonitor m = mon();
        PrideFrame f = PrideFrame.sized(width, height, 820, 460);
        f.draw(this, "CCTV MONITOR " + (m == null ? "" : m.no), m == null ? "" : m.gearState());
        if (m == null) { super.drawScreen(mouseX, mouseY, pt); return; }
        int cam = m.ch[Math.min(m.sel, 8)];
        long now = System.currentTimeMillis();
        if (now - askedRange > 2000) { askedRange = now; CctvClient.askRange(cam); }
        // ---- picture (the selected tile; split grid shown)
        int pw = Math.min(f.cw * 55 / 100, (f.ch - 70) * 4 / 3), ph = pw * 3 / 4, px = f.cx, py = f.cy + 2;
        int s = m.split == 9 ? 3 : m.split == 4 ? 2 : 1;
        for (int k = 0; k < s * s; k++) {
            int tx = k % s, ty = k / s, x0 = px + tx * pw / s, y0 = py + ty * ph / s, w = pw / s - 1, h = ph / s - 1;
            CctvClient.Pic p = m.mode == 1 && k == m.sel ? CctvClient.playback(m.ch[k], m.pbT) : CctvClient.pic(m.ch[k], m.net());
            picture(p, x0, y0, w, h, m.dewarp);
            fontRenderer.drawStringWithShadow((m.ch[k] <= 0 ? "--" : "CAM " + m.ch[k]), x0 + 3, y0 + 3, 0xFFFFFF);
            if (k == m.sel && s > 1) { Gui.drawRect(x0, y0, x0 + w, y0 + 1, 0xFFFFE040); Gui.drawRect(x0, y0 + h - 1, x0 + w, y0 + h, 0xFFFFE040); }
            final int kk = k;
            area(x0, y0, w, h, () -> op("sel|" + kk));
        }
        int y = py + ph + 4, bw = (pw - 12) / 5;
        String[] sp = {"1", "4", "9"};
        for (int i = 0; i < 3; i++) { final String v = sp[i]; btn(px + i * (bw + 3), y, bw, 14, "SPLIT " + v, m.split == Integer.parseInt(v) ? 0xFF6A3FA0 : 0xFF2A2238, () -> op("split|" + v)); }
        btn(px + 3 * (bw + 3), y, bw, 14, m.overlay ? "OVERLAY ON" : "OVERLAY OFF", 0xFF2A2238, () -> op("overlay|"));
        btn(px + 4 * (bw + 3), y, bw, 14, m.dewarp ? "DEWARP" : "FISHEYE", 0xFF2A2238, () -> op("dewarp|"));
        // ---- playback bar
        y += 20;
        long[] r = CctvClient.RANGES.get(cam);
        int bx = px, bwid = pw;
        Gui.drawRect(bx, y, bx + bwid, y + 12, 0xFF101018);
        if (r != null && r[1] > r[0]) {
            for (String mk : CctvClient.MARKS.getOrDefault(cam, "").split("\\|")) {
                int c = mk.indexOf(':');
                if (c <= 0) continue;
                try {
                    long t = Long.parseLong(mk.substring(0, c));
                    if (t < r[0] || t > r[1]) continue;
                    int mxp = bx + (int) ((t - r[0]) * bwid / (r[1] - r[0]));
                    String txt = mk.substring(c + 1);
                    Gui.drawRect(mxp, y, mxp + 1, y + 12, txt.startsWith("MOTION") ? 0xFFFFC44D : txt.startsWith("DOOR") ? 0xFF5BCEFA : 0xFFFF5252);
                    if (mouseX >= mxp - 2 && mouseX <= mxp + 2 && mouseY >= y && mouseY < y + 12) drawHoveringText(txt, mouseX, mouseY);
                } catch (NumberFormatException ignored) { }
            }
            if (m.mode == 1) { int at = bx + (int) (Math.max(0, Math.min(r[1] - r[0], m.pbT - r[0])) * bwid / (r[1] - r[0])); Gui.drawRect(at - 1, y - 2, at + 1, y + 14, 0xFFFFFFFF); }
            final long lo = r[0], hi = r[1];
            area(bx, y, bwid, 12, () -> op("seek|" + (lo + (long) ((mx - bx) / (double) bwid * (hi - lo)))));
            fontRenderer.drawString(clock(lo), bx, y + 14, 0x8A94A0);
            fontRenderer.drawString(clock(hi), bx + bwid - fontRenderer.getStringWidth(clock(hi)), y + 14, 0x8A94A0);
        } else fontRenderer.drawString("No recording for CAM " + cam + " (needs an NVR with drives, REC on)", bx + 4, y + 2, 0x8A94A0);
        y += 26;
        int b6 = (pw - 15) / 6;
        btn(px, y, b6, 14, "LIVE", m.mode == 0 ? 0xFF2A6A3A : 0xFF2A2238, () -> op("live|"));
        btn(px + (b6 + 3), y, b6, 14, m.mode == 1 && !m.pbPause ? "PAUSE" : "PLAY", 0xFF2A2238, () -> op(m.mode == 1 && !m.pbPause ? "pause|" : "play|"));
        btn(px + 2 * (b6 + 3), y, b6, 14, "<<", 0xFF2A2238, () -> op("speed|" + (m.pbSpeed - 1)));
        btn(px + 3 * (b6 + 3), y, b6, 14, ">>", 0xFF2A2238, () -> op("speed|" + (m.pbSpeed + 1)));
        btn(px + 4 * (b6 + 3), y, b6, 14, "STEP <", 0xFF2A2238, () -> op("step|-1"));
        btn(px + 5 * (b6 + 3), y, b6, 14, "STEP >", 0xFF2A2238, () -> op("step|1"));
        y += 17;
        String spd = "x" + TileMonitor.SPEEDS[m.pbSpeed];
        fontRenderer.drawStringWithShadow(m.mode == 1 ? "PLAYBACK " + spd + (m.pbPause ? " paused" : "") + "  " + clock(m.pbT) : "LIVE", px, y + 3, m.mode == 1 ? 0xFFD040 : 0x7CF29A);
        btn(px + pw - 110, y, 110, 14, "EXPORT TO MAP", 0xFF5A4A20, () -> RailMap.NETWORK.sendToServer(new PacketCctvOp(PacketCctvOp.EXPORT, 0, pos.toLong(), 0, "")));
        // ---- camera list
        int lx = px + pw + 10, lw = f.x + f.w - 10 - lx, ly = f.cy + 2, rows = (f.cy + f.ch - ly - 14) / 12;
        fontRenderer.drawStringWithShadow("CAMERAS ON THIS NETWORK  (click = to tile " + (m.sel + 1) + ")", lx, ly, 0x5BCEFA);
        ly += 12;
        List<CctvData.Cam> cams = new java.util.ArrayList<>();
        for (CctvData.Cam c : CctvClient.DIR.values()) if (Cctv.canSee(c, m.net())) cams.add(c);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, cams.size() - rows)));
        for (int i = scroll; i < Math.min(cams.size(), scroll + rows); i++) {
            CctvData.Cam c = cams.get(i);
            boolean on = c.no == cam, over = mouseX >= lx && mouseX < lx + lw && mouseY >= ly && mouseY < ly + 12;
            Gui.drawRect(lx, ly, lx + lw, ly + 11, on ? 0xFF3A2E5C : over ? 0xFF2A2240 : (i % 2 == 0 ? 0x22FFFFFF : 0));
            String line = String.format(Locale.ROOT, "%3d  %s", c.no, c.name);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(line, lw - 90), lx + 2, ly + 2, 0xE8ECF0);
            fontRenderer.drawString(c.rec ? "REC" : "", lx + lw - 86, ly + 2, 0xFF5252);
            String st = CctvData.STATUS[c.status];
            fontRenderer.drawString(st, lx + lw - 2 - fontRenderer.getStringWidth(st), ly + 2, c.streams() ? 0x7CF29A : 0xFF5252);
            final int no = c.no, sel = m.sel;
            area(lx, ly, lw, 11, () -> op("cam|" + sel + "," + no));
            ly += 12;
        }
        if (cams.isEmpty()) fontRenderer.drawString(m.net() == 0 && CctvConfig.requireNetwork ? "Not on a video network: wire it to an NVR" : "No cameras", lx, ly, 0xFF5252);
        PrideFrame.scrollbar(lx + lw - 2, f.cy + 14, rows * 12, scroll, rows, cams.size());
        btn(f.x + f.w - 70, f.y + f.h - 22, 60, 14, "DONE", 0xFF2A6A3A, () -> mc.displayGuiScreen(null));
        super.drawScreen(mouseX, mouseY, pt);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) scroll += d > 0 ? -1 : 1;
    }

    private String clock(long t) {
        long day = (t + (mc.world.getWorldTime() - mc.world.getTotalWorldTime()) + 6000) % 24000;
        if (day < 0) day += 24000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", day / 1000, day % 1000 * 60 / 1000, day % 1000 * 3600 / 1000 % 60);
    }
}
